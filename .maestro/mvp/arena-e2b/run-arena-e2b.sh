#!/usr/bin/env bash
#
# E2B runner draft.  It is safe by construction for a retained MVP installation:
# no clear/uninstall fallback, no reverse --remove-all, and no hidden output location.
set -Eeuo pipefail

readonly APP_ID='com.tpov.schoolquiz.mvp'
readonly ALLOWED_REVERSE_PORTS=(9099 8080 5001)

phase=''
run_id=''
candidate_head=''
candidate_apk=''
run_context=''
selectors_file=''
receipt_context=''
rating_id_context=''
context_reader=''
receipt_oracle=''
output_root=''
serial=''
install_candidate=false
adb_bin="${ADB_BIN:-adb}"
maestro_bin="${MAESTRO_BIN:-maestro}"
apksigner_bin="${APKSIGNER_BIN:-apksigner}"
java_home="${JAVA_HOME:-}"
node_bin="${NODE_BIN:-node}"
output_dir=''
overall_status='INTERRUPTED'
phase_status='NOT_RUN'
next_required='runner did not reach a terminal phase'

run_seen_keys=$'\n'
selector_seen_keys=$'\n'
readonly SELECTOR_KEYS=(
    E2B_SELECTOR_INTERNET
    E2B_SELECTOR_ARENA_TITLE
    E2B_SELECTOR_REFRESH_BALANCE
    E2B_SELECTOR_NOLICS_BALANCE
    E2B_SELECTOR_LIFE_BALANCE
    E2B_SELECTOR_LOAD_LESSONS
    E2B_SELECTOR_CATALOG_LOADED
    E2B_SELECTOR_TARGET_LESSON
    E2B_SELECTOR_FIXTURE_AUTHOR
    E2B_SELECTOR_FROZEN_PRICE
    E2B_SELECTOR_SELECTED_PREPARE
    E2B_SELECTOR_PREPARE_DIALOG
    E2B_SELECTOR_DIFFICULTY_HARD
    E2B_SELECTOR_HARD_SELECTED
    E2B_SELECTOR_PREPARE_CONFIRM
    E2B_SELECTOR_PREPARED_TICKET
    E2B_SELECTOR_SHOW_LOCAL_TICKETS
    E2B_SELECTOR_TICKET_LABEL_TEMPLATE
    E2B_SELECTOR_TICKET_START
    E2B_SELECTOR_TICKET_OPEN
    E2B_SELECTOR_QUESTION
    E2B_SELECTOR_FIRST_ANSWER
    E2B_SELECTOR_SAVED_ANSWER
    E2B_SELECTOR_RESUME_DIALOG
    E2B_SELECTOR_RESUME_OR_SAVED
    E2B_SELECTOR_RESUME_CONTINUE
    E2B_SELECTOR_SECOND_QUESTION
    E2B_SELECTOR_SECOND_ANSWER
    E2B_SELECTOR_CONTINUE_LESSON
    E2B_SELECTOR_SYNCHRONIZE
    E2B_SELECTOR_SETTLE_PENDING
    E2B_SELECTOR_SETTLE_CONFIRMED
    E2B_SELECTOR_RATING_PROMPT
    E2B_SELECTOR_RATE_TWO
    E2B_SELECTOR_RATING_QUEUED
    E2B_SELECTOR_SEND_QUEUED_RATING
    E2B_SELECTOR_RATING_ACCEPTED
)

usage() {
    cat <<'USAGE'
Usage:
  run-arena-e2b.sh --phase preflight|prepare|settle|resume-settle|deliver|rate|deliver-rating|verify \
    --run-id <id> --candidate-head <40-hex> --apk <candidate.apk> \
    --run-context <immutable.env> --selectors <confirmed-selectors.env> \
    --output-root <results-dir> [--serial <adb-serial>] \
    [--receipt-context <reader.json>] [--context-reader <reader.js>] \
    [--rating-id-context <room-reader.json>] \
    [--receipt-oracle <oracle.js>] [--adb <path>] [--maestro <path>] \
    [--apksigner <path>] [--java-home <dir>] [--node <path>] [--install-candidate]

Only verify may emit overall PASS.  Device phases require --serial.  Settle and deliver require
--receipt-context and --context-reader; rate requires --receipt-context; deliver-rating requires
--receipt-context and --rating-id-context; verify requires
--receipt-context, --rating-id-context, --context-reader, and --receipt-oracle.

--install-candidate performs one retained-data update through adb install -r. Omitting it
only verifies the already installed package, so later phases cannot restart the app by reinstalling it.
USAGE
}

die() {
    overall_status='FAIL'
    phase_status='FAIL'
    next_required='inspect runner stderr and retained state before retrying'
    printf 'E2B runner: %s\n' "$*" >&2
    exit 2
}

on_interrupt() {
    overall_status='INTERRUPTED'
    phase_status='INTERRUPTED'
    next_required='inspect the saved device state and output evidence before retrying'
    exit 130
}

on_error() {
    local exit_code=$?
    # A failed reader, validator, or oracle is evidence of a failed phase, not an interruption.
    # Keep a more specific failure state set by run_maestro() or die().
    if [[ "$overall_status" != 'FAIL' ]]; then
        overall_status='FAIL'
        phase_status='FAIL'
        next_required='inspect the failed read-only evidence or runner stderr before retrying'
    fi
    exit "$exit_code"
}

write_result() {
    local exit_code=$?
    trap - EXIT
    if [[ -n "$output_dir" && -d "$output_dir" ]]; then
        "$node_bin" - "$output_dir/result.json" "$overall_status" "$phase_status" "$phase" "$run_id" "$next_required" <<'NODE' || true
const fs = require('fs');
const [resultPath, status, phaseStatus, phase, runId, nextRequired] = process.argv.slice(2);
fs.writeFileSync(
  resultPath,
  JSON.stringify({ status, phaseStatus, phase, runId, nextRequired, resultArtifactVersion: 1 }, null, 2) + '\n',
  { mode: 0o600 },
);
NODE
    fi
    exit "$exit_code"
}

trap write_result EXIT
trap on_interrupt INT TERM
trap on_error ERR

require_value() {
    local option_name=$1
    local option_value=$2
    [[ -n "$option_value" ]] || die "$option_name is required"
}

is_safe_value() {
    # Bash strings cannot contain NUL; test only the control bytes that can be represented here.
    [[ $1 != *$'\n'* && $1 != *$'\r'* ]]
}

parse_key_value_file() {
    local file=$1
    local kind=$2
    local line line_number=0 key value
    [[ -f "$file" ]] || die "input file does not exist: $file"
    while IFS= read -r line || [[ -n "$line" ]]; do
        ((++line_number))
        [[ -z "$line" || "$line" == \#* ]] && continue
        [[ "$line" =~ ^([A-Z][A-Z0-9_]*)=(.*)$ ]] || die "$file:$line_number is not KEY=value data"
        key=${BASH_REMATCH[1]}
        value=${BASH_REMATCH[2]}
        [[ "$key" == E2B_* ]] || die "$file:$line_number has a non-E2B key"
        case "$kind" in
            run)
                [[ "$run_seen_keys" != *$'\n'"$key"$'\n'* ]] || die "$file:$line_number duplicates $key"
                run_seen_keys+="$key"$'\n'
                ;;
            selectors)
                [[ "$selector_seen_keys" != *$'\n'"$key"$'\n'* ]] || die "$file:$line_number duplicates $key"
                selector_seen_keys+="$key"$'\n'
                ;;
            *) die "unknown key/value input kind: $kind" ;;
        esac
        is_safe_value "$value" || die "$file:$line_number contains a control character"
        # Key syntax is constrained above, so this assignment cannot evaluate input as code.
        printf -v "$key" '%s' "$value"
    done < "$file"
}

require_file_key() {
    local kind=$1
    local key=$2
    local seen_keys value
    case "$kind" in
        run) seen_keys=$run_seen_keys ;;
        selectors) seen_keys=$selector_seen_keys ;;
        *) die "unknown key/value input kind: $kind" ;;
    esac
    [[ "$seen_keys" == *$'\n'"$key"$'\n'* ]] || die "$key is missing from its input file"
    value=${!key}
    [[ -n "$value" ]] || die "$key must be populated in its input file"
}

sha256_file() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    else
        die 'SHA-256 tool is unavailable'
    fi
}

validate_sha256() {
    [[ $1 =~ ^[a-f0-9]{64}$ ]] || die "$2 must be lowercase SHA-256"
}

validate_run_context() {
    local key
    for key in \
        E2B_RUN_ID E2B_CANDIDATE_HEAD E2B_APP_ID E2B_APK_SHA256 E2B_APK_CERT_SHA256 \
        E2B_ACCOUNT_REF E2B_OWNER_UID E2B_LESSON_ID E2B_LESSON_VERSION \
        E2B_EXPECTED_FROZEN_PRICE_NOLICS E2B_EXPECTED_MODE E2B_EXPECTED_DIFFICULTY E2B_RATING; do
        require_file_key run "$key"
    done
    [[ "$E2B_RUN_ID" == "$run_id" ]] || die 'run ID differs from immutable context'
    [[ "$E2B_CANDIDATE_HEAD" == "$candidate_head" ]] || die 'candidate HEAD differs from immutable context'
    [[ "$E2B_APP_ID" == "$APP_ID" ]] || die 'unexpected application ID'
    validate_sha256 "$E2B_APK_SHA256" E2B_APK_SHA256
    validate_sha256 "$E2B_APK_CERT_SHA256" E2B_APK_CERT_SHA256
    [[ "$E2B_EXPECTED_MODE" == SELECTED ]] || die 'E2B must use SELECTED mode'
    [[ "$E2B_EXPECTED_DIFFICULTY" == HARD ]] || die 'E2B must use HARD difficulty'
    [[ "$E2B_RATING" == 2 ]] || die 'the first E2B proof requires rating 2'
    [[ "$E2B_LESSON_VERSION" =~ ^[1-9][0-9]*$ ]] || die 'lesson version must be a positive integer'
    [[ "$E2B_EXPECTED_FROZEN_PRICE_NOLICS" =~ ^[0-9]+$ ]] || die 'frozen price must be a non-negative integer'
}

required_selector_keys() {
    case "$phase" in
        preflight)
            printf '%s\n' \
                E2B_SELECTOR_INTERNET E2B_SELECTOR_ARENA_TITLE E2B_SELECTOR_REFRESH_BALANCE \
                E2B_SELECTOR_NOLICS_BALANCE E2B_SELECTOR_LIFE_BALANCE E2B_SELECTOR_LOAD_LESSONS \
                E2B_SELECTOR_CATALOG_LOADED E2B_SELECTOR_TARGET_LESSON E2B_SELECTOR_FIXTURE_AUTHOR \
                E2B_SELECTOR_FROZEN_PRICE
            ;;
        prepare)
            printf '%s\n' \
                E2B_SELECTOR_INTERNET E2B_SELECTOR_ARENA_TITLE E2B_SELECTOR_REFRESH_BALANCE \
                E2B_SELECTOR_NOLICS_BALANCE E2B_SELECTOR_LIFE_BALANCE E2B_SELECTOR_LOAD_LESSONS \
                E2B_SELECTOR_CATALOG_LOADED E2B_SELECTOR_TARGET_LESSON E2B_SELECTOR_FIXTURE_AUTHOR \
                E2B_SELECTOR_FROZEN_PRICE \
                E2B_SELECTOR_SELECTED_PREPARE E2B_SELECTOR_PREPARE_DIALOG \
                E2B_SELECTOR_DIFFICULTY_HARD E2B_SELECTOR_HARD_SELECTED \
                E2B_SELECTOR_PREPARE_CONFIRM E2B_SELECTOR_SHOW_LOCAL_TICKETS \
                E2B_SELECTOR_PREPARED_TICKET
            ;;
        settle)
            printf '%s\n' \
                E2B_SELECTOR_INTERNET E2B_SELECTOR_ARENA_TITLE E2B_SELECTOR_SHOW_LOCAL_TICKETS \
                E2B_SELECTOR_TICKET_LABEL_TEMPLATE E2B_SELECTOR_TICKET_START E2B_SELECTOR_TICKET_OPEN \
                E2B_SELECTOR_QUESTION E2B_SELECTOR_FIRST_ANSWER E2B_SELECTOR_SAVED_ANSWER \
                E2B_SELECTOR_RESUME_DIALOG E2B_SELECTOR_RESUME_OR_SAVED E2B_SELECTOR_RESUME_CONTINUE \
                E2B_SELECTOR_SECOND_QUESTION E2B_SELECTOR_SECOND_ANSWER E2B_SELECTOR_CONTINUE_LESSON \
                E2B_SELECTOR_SYNCHRONIZE E2B_SELECTOR_SETTLE_PENDING
            ;;
        resume-settle)
            printf '%s\n' \
                E2B_SELECTOR_INTERNET E2B_SELECTOR_ARENA_TITLE E2B_SELECTOR_SHOW_LOCAL_TICKETS \
                E2B_SELECTOR_TICKET_LABEL_TEMPLATE E2B_SELECTOR_TICKET_OPEN \
                E2B_SELECTOR_SAVED_ANSWER E2B_SELECTOR_RESUME_DIALOG E2B_SELECTOR_RESUME_OR_SAVED \
                E2B_SELECTOR_RESUME_CONTINUE E2B_SELECTOR_SECOND_QUESTION E2B_SELECTOR_SECOND_ANSWER \
                E2B_SELECTOR_CONTINUE_LESSON E2B_SELECTOR_SYNCHRONIZE E2B_SELECTOR_SETTLE_PENDING
            ;;
        deliver)
            printf '%s\n' \
                E2B_SELECTOR_INTERNET E2B_SELECTOR_ARENA_TITLE E2B_SELECTOR_SHOW_LOCAL_TICKETS \
                E2B_SELECTOR_TICKET_LABEL_TEMPLATE E2B_SELECTOR_TICKET_OPEN \
                E2B_SELECTOR_SYNCHRONIZE E2B_SELECTOR_SETTLE_CONFIRMED
            ;;
        rate)
            printf '%s\n' \
                E2B_SELECTOR_INTERNET E2B_SELECTOR_ARENA_TITLE E2B_SELECTOR_SHOW_LOCAL_TICKETS \
                E2B_SELECTOR_TICKET_LABEL_TEMPLATE E2B_SELECTOR_TICKET_OPEN \
                E2B_SELECTOR_SETTLE_CONFIRMED E2B_SELECTOR_RATING_PROMPT E2B_SELECTOR_RATE_TWO \
                E2B_SELECTOR_RATING_QUEUED
            ;;
        deliver-rating)
            printf '%s\n' \
                E2B_SELECTOR_INTERNET E2B_SELECTOR_ARENA_TITLE E2B_SELECTOR_SHOW_LOCAL_TICKETS \
                E2B_SELECTOR_TICKET_LABEL_TEMPLATE E2B_SELECTOR_TICKET_OPEN \
                E2B_SELECTOR_SETTLE_CONFIRMED E2B_SELECTOR_RATING_QUEUED \
                E2B_SELECTOR_SEND_QUEUED_RATING E2B_SELECTOR_RATING_ACCEPTED
            ;;
        verify)
            ;;
        *) die "unknown phase: $phase" ;;
    esac
}

validate_selectors() {
    local key
    while IFS= read -r key; do
        [[ -n "$key" ]] || continue
        require_file_key selectors "$key"
    done < <(required_selector_keys)
    if [[ "$phase" == settle || "$phase" == deliver || "$phase" == rate || "$phase" == deliver-rating ]]; then
        local template=$E2B_SELECTOR_TICKET_LABEL_TEMPLATE
        [[ "$template" == *'{{ticketId}}'* ]] || die 'ticket label selector must contain {{ticketId}}'
        [[ "${template#*'{{ticketId}}'}" != *'{{ticketId}}'* ]] || die 'ticket label selector must contain {{ticketId}} exactly once'
    fi
}

json_value() {
    local file=$1
    local key=$2
    "$node_bin" -e '
const fs = require("fs");
const [file, key] = process.argv.slice(1);
const value = JSON.parse(fs.readFileSync(file, "utf8"))[key];
if (typeof value !== "string" && typeof value !== "number") process.exit(2);
process.stdout.write(String(value));
' "$file" "$key"
}

validate_receipt_context() {
    local context_file=$1
    local need_rating_id=$2
    [[ -f "$context_file" ]] || die "receipt context does not exist: $context_file"
    "$node_bin" - "$context_file" "$run_id" "$E2B_OWNER_UID" \
        "$E2B_LESSON_ID" "$E2B_LESSON_VERSION" \
        "$E2B_EXPECTED_FROZEN_PRICE_NOLICS" "$need_rating_id" <<'NODE'
const fs = require('fs');
const [file, runId, ownerUid, lessonId, lessonVersion, frozenPrice, needsRatingId] = process.argv.slice(2);
let value;
try {
  value = JSON.parse(fs.readFileSync(file, 'utf8'));
} catch (error) {
  process.stderr.write(`invalid receipt context: ${error.message}\n`);
  process.exit(2);
}
const required = ['status', 'runId', 'ownerUid', 'ticketId', 'attemptId', 'lessonId', 'lessonVersion', 'frozenPriceNolics', 'mode', 'difficulty'];
for (const key of required) {
  if (value[key] === undefined || value[key] === null || value[key] === '') {
    process.stderr.write(`receipt context lacks ${key}\n`);
    process.exit(2);
  }
}
const equal = [
  ['status', 'CONTEXT'], ['runId', runId], ['ownerUid', ownerUid], ['lessonId', lessonId],
  ['lessonVersion', lessonVersion], ['frozenPriceNolics', frozenPrice], ['mode', 'SELECTED'], ['difficulty', 'HARD'],
];
for (const [key, expected] of equal) {
  if (String(value[key]) !== String(expected)) {
    process.stderr.write(`receipt context ${key} does not match immutable run context\n`);
    process.exit(2);
  }
}
if (!/^[a-f0-9]{64}$/.test(String(value.ticketId)) || !/^[a-f0-9]{64}$/.test(String(value.attemptId))) {
  process.stderr.write('receipt context has an invalid ticketId or attemptId\n');
  process.exit(2);
}
if (needsRatingId === 'true' && (typeof value.ratingId !== 'string' || value.ratingId.length === 0)) {
  process.stderr.write('receipt context lacks ratingId after rating submission\n');
  process.exit(2);
}
NODE
}

validate_confirmed_settlement() {
    local context_file=$1
    "$node_bin" - "$context_file" <<'NODE'
const fs = require('fs');
const [file] = process.argv.slice(2);
const value = JSON.parse(fs.readFileSync(file, 'utf8'));
if (value.settlementReceipt === null || typeof value.settlementReceipt !== 'object' || Array.isArray(value.settlementReceipt)) {
  process.stderr.write('receipt context does not contain a confirmed settlement receipt\n');
  process.exit(2);
}
NODE
}

validate_unsettled_server_context() {
    local context_file=$1
    "$node_bin" - "$context_file" <<'NODE'
const fs = require('fs');
const [file] = process.argv.slice(2);
const value = JSON.parse(fs.readFileSync(file, 'utf8'));
if (value.ticketStatus !== 'RESERVED' || value.settlementReceipt !== null || value.event !== null) {
  process.stderr.write('receipt context is not the exact unconfirmed server state required for this phase\n');
  process.exit(2);
}
NODE
}

validate_rating_id_context() {
    local context_file=$1
    local expected_ticket_id=$2
    local expected_attempt_id=$3
    [[ -f "$context_file" ]] || die "rating ID context does not exist: $context_file"
    "$node_bin" - "$context_file" "$run_id" "$E2B_OWNER_UID" \
        "$E2B_LESSON_ID" "$E2B_RATING" "$expected_ticket_id" "$expected_attempt_id" <<'NODE'
const fs = require('fs');
const [file, runId, ownerUid, lessonId, rating, ticketId, attemptId] = process.argv.slice(2);
let value;
try {
  value = JSON.parse(fs.readFileSync(file, 'utf8'));
} catch (error) {
  process.stderr.write(`invalid rating ID context: ${error.message}\n`);
  process.exit(2);
}
const required = ['status', 'runId', 'ownerUid', 'ticketId', 'attemptId', 'lessonId', 'ratingId', 'rating', 'mutationId', 'entityRef'];
for (const key of required) {
  if (value[key] === undefined || value[key] === null || value[key] === '') {
    process.stderr.write(`rating ID context lacks ${key}\n`);
    process.exit(2);
  }
}
for (const [key, expected] of [['status', 'CONTEXT'], ['runId', runId], ['ownerUid', ownerUid], ['lessonId', lessonId], ['rating', Number(rating)], ['ticketId', ticketId], ['attemptId', attemptId]]) {
  if (String(value[key]) !== String(expected)) {
    process.stderr.write(`rating ID context ${key} does not match immutable run context\n`);
    process.exit(2);
  }
}
if (!/^[a-f0-9]{64}$/.test(String(value.ticketId)) || !/^[a-f0-9]{64}$/.test(String(value.attemptId)) ||
    !/^[a-f0-9]{64}$/.test(String(value.ratingId))) {
  process.stderr.write('rating ID context has invalid ticket, attempt, or rating identity\n');
  process.exit(2);
}
if (value.mutationId !== `lesson_runner-SUBMIT_RATING-${value.ratingId}` ||
    value.entityRef !== `lesson_runner:rating:${value.ratingId}`) {
  process.stderr.write('rating ID context does not preserve the local mutation binding\n');
  process.exit(2);
}
if (value.documentWrites !== 0 || value.networkCalls !== 0 || value.deviceCalls !== 0 || value.storage !== 'Room') {
  process.stderr.write('rating ID context is not a read-only Room result\n');
  process.exit(2);
}
NODE
}

prepare_ticket_selectors() {
    local ticket_id
    ticket_id=$(json_value "$receipt_context" ticketId) || die 'could not read ticketId from receipt context'
    E2B_SELECTOR_TICKET_LABEL=${E2B_SELECTOR_TICKET_LABEL_TEMPLATE/'{{ticketId}}'/$ticket_id}
}

assert_device_and_candidate() {
    [[ "$serial" != *$'\n'* && "$serial" != *$'\r'* ]] || die 'invalid ADB serial'
    "$adb_bin" -s "$serial" get-state | grep -qx device || die 'ADB target is not a ready device'
    "$adb_bin" -s "$serial" shell pm path "$APP_ID" | grep -q '^package:' || die 'retained application is not installed'

    local actual_apk_sha cert_output actual_cert
    actual_apk_sha=$(sha256_file "$candidate_apk")
    [[ "$actual_apk_sha" == "$E2B_APK_SHA256" ]] || die 'candidate APK SHA-256 differs from immutable context'
    if [[ -n "$java_home" ]]; then
        [[ -x "$java_home/bin/java" ]] || die "Java executable is absent under --java-home: $java_home"
        cert_output=$(JAVA_HOME="$java_home" PATH="$java_home/bin:$PATH" "$apksigner_bin" verify --print-certs "$candidate_apk") ||
            die 'candidate APK signature is invalid'
    else
        cert_output=$("$apksigner_bin" verify --print-certs "$candidate_apk") || die 'candidate APK signature is invalid'
    fi
    actual_cert=$(printf '%s\n' "$cert_output" | awk -F': ' '/Signer #1 certificate SHA-256 digest:/ {print tolower($2); exit}')
    [[ "$actual_cert" == "$E2B_APK_CERT_SHA256" ]] || die 'candidate APK certificate differs from immutable context'

    if [[ "$install_candidate" == true ]]; then
        # -r preserves package data. There is intentionally no clear, uninstall, downgrade, or fallback.
        "$adb_bin" -s "$serial" install --no-incremental -r "$candidate_apk" > "$output_dir/adb-install.log"
    else
        printf 'SKIPPED: candidate APK installation was not requested; existing retained package was verified present.\n' \
            > "$output_dir/adb-install.log"
    fi
}

ensure_reverse_mappings() {
    local mapping local_port
    local mappings
    mappings=$("$adb_bin" -s "$serial" reverse --list)
    while IFS= read -r mapping; do
        [[ -z "$mapping" ]] && continue
        parse_reverse_mapping "$mapping"
        case "$REVERSE_LOCAL:$REVERSE_REMOTE" in
            tcp:9099:tcp:9099|tcp:8080:tcp:8080|tcp:5001:tcp:5001) ;;
            *) die 'ADB target already has a reverse mapping outside the E2B allowlist' ;;
        esac
    done <<< "$mappings"
    for local_port in "${ALLOWED_REVERSE_PORTS[@]}"; do
        "$adb_bin" -s "$serial" reverse "tcp:$local_port" "tcp:$local_port"
    done
    mappings=$("$adb_bin" -s "$serial" reverse --list)
    for local_port in "${ALLOWED_REVERSE_PORTS[@]}"; do
        local found=false
        while IFS= read -r mapping; do
            [[ -z "$mapping" ]] && continue
            parse_reverse_mapping "$mapping"
            if [[ "$REVERSE_LOCAL" == "tcp:$local_port" && "$REVERSE_REMOTE" == "tcp:$local_port" ]]; then
                found=true
            fi
        done <<< "$mappings"
        [[ "$found" == true ]] || die "required reverse port $local_port is absent"
    done
}

parse_reverse_mapping() {
    local mapping=$1 first second third fourth
    read -r first second third fourth <<< "$mapping"
    if [[ "$first" == tcp:* ]]; then
        REVERSE_LOCAL=$first
        REVERSE_REMOTE=$second
        [[ -z "$third" && -z "$fourth" ]] || die 'ADB reverse list contains an unexpected mapping shape'
    else
        # Platform-tools 36 prefixes `reverse --list` rows with an internal device name.
        [[ -n "$first" && "$second" == tcp:* && "$third" == tcp:* && -z "$fourth" ]] ||
            die 'ADB reverse list contains an unexpected mapping shape'
        REVERSE_LOCAL=$second
        REVERSE_REMOTE=$third
    fi
}

write_manifest() {
    local apk_sha context_sha selector_sha runner_sha
    apk_sha=$(sha256_file "$candidate_apk")
    context_sha=$(sha256_file "$run_context")
    selector_sha=$(sha256_file "$selectors_file")
    runner_sha=$(sha256_file "$0")
    {
        printf 'runId=%s\n' "$run_id"
        printf 'phase=%s\n' "$phase"
        printf 'candidateHead=%s\n' "$candidate_head"
        printf 'candidateApkSha256=%s\n' "$apk_sha"
        printf 'runContextSha256=%s\n' "$context_sha"
        printf 'selectorMapSha256=%s\n' "$selector_sha"
        printf 'runnerScriptSha256=%s\n' "$runner_sha"
        printf 'appId=%s\n' "$APP_ID"
        [[ -n "$receipt_context" ]] && printf 'receiptContextSha256=%s\n' "$(sha256_file "$receipt_context")"
    } > "$output_dir/run-manifest.env"
    chmod 600 "$output_dir/run-manifest.env"
}

run_maestro() {
    local flow=$1
    local flow_name=${flow##*/} flow_sha
    flow_name=${flow_name%.yaml}
    local flow_output="$output_dir/maestro-${phase}-${flow_name}"
    mkdir -p "$flow_output"
    flow_sha=$(sha256_file "$flow")
    printf 'maestroFlow.%s.%s.sha256=%s\n' "$phase" "$flow_name" "$flow_sha" >> "$output_dir/run-manifest.env"
    local -a args=(test -e "E2B_RUN_ID=$run_id" -e "E2B_APP_ID=$APP_ID")
    local key value
    for key in "${SELECTOR_KEYS[@]}"; do
        if [[ "$selector_seen_keys" == *$'\n'"$key"$'\n'* ]]; then
            value=${!key}
            args+=(-e "$key=$value")
        fi
    done
    if [[ -n ${E2B_SELECTOR_TICKET_LABEL-} ]]; then
        args+=(-e "E2B_SELECTOR_TICKET_LABEL=$E2B_SELECTOR_TICKET_LABEL")
    fi
    if [[ -n "$receipt_context" ]]; then
        args+=(-e "E2B_TICKET_ID=$(json_value "$receipt_context" ticketId)")
        args+=(-e "E2B_ATTEMPT_ID=$(json_value "$receipt_context" attemptId)")
    fi
    args+=(--test-output-dir "$flow_output" -e "E2B_RATING=$E2B_RATING" "$flow")
    if ! "$maestro_bin" --device "$serial" --no-ansi "${args[@]}" 2>&1 | tee "$output_dir/maestro-${phase}-${flow_name}.log"; then
        overall_status='FAIL'
        phase_status='FAIL'
        next_required='inspect the saved Maestro hierarchy and retained ticket state before retrying'
        return 1
    fi
}

capture_reader_context() {
    local destination=$1
    [[ -f "$context_reader" ]] || die "context reader does not exist: $context_reader"
    "$node_bin" "$context_reader" \
        --run-id "$run_id" \
        --owner-uid "$E2B_OWNER_UID" \
        --ticket-id "$(json_value "$receipt_context" ticketId)" \
        --attempt-id "$(json_value "$receipt_context" attemptId)" \
        > "$destination"
    chmod 600 "$destination"
}

capture_reader_context_after_rating() {
    local destination=$1
    local rating_id
    rating_id=$(json_value "$rating_id_context" ratingId) || die 'could not read ratingId from Room context'
    [[ -f "$context_reader" ]] || die "context reader does not exist: $context_reader"
    "$node_bin" "$context_reader" \
        --run-id "$run_id" \
        --owner-uid "$E2B_OWNER_UID" \
        --ticket-id "$(json_value "$receipt_context" ticketId)" \
        --attempt-id "$(json_value "$receipt_context" attemptId)" \
        --rating-id "$rating_id" \
        --rating "$E2B_RATING" \
        > "$destination"
    chmod 600 "$destination"
}

run_full_oracle() {
    local rating_id
    rating_id=$(json_value "$rating_id_context" ratingId) || die 'could not read ratingId from Room context'
    [[ -f "$receipt_oracle" ]] || die "receipt oracle does not exist: $receipt_oracle"
    "$node_bin" "$receipt_oracle" \
        --run-id "$run_id" \
        --owner-uid "$E2B_OWNER_UID" \
        --ticket-id "$(json_value "$receipt_context" ticketId)" \
        --attempt-id "$(json_value "$receipt_context" attemptId)" \
        --rating-id "$rating_id" \
        --rating "$E2B_RATING" \
        > "$output_dir/two-receipt-oracle.json"
    chmod 600 "$output_dir/two-receipt-oracle.json"
}

while (($#)); do
    case "$1" in
        --phase) [[ $# -ge 2 ]] || die '--phase requires a value'; phase=$2; shift 2 ;;
        --run-id) [[ $# -ge 2 ]] || die '--run-id requires a value'; run_id=$2; shift 2 ;;
        --candidate-head) [[ $# -ge 2 ]] || die '--candidate-head requires a value'; candidate_head=$2; shift 2 ;;
        --apk) [[ $# -ge 2 ]] || die '--apk requires a value'; candidate_apk=$2; shift 2 ;;
        --run-context) [[ $# -ge 2 ]] || die '--run-context requires a value'; run_context=$2; shift 2 ;;
        --selectors) [[ $# -ge 2 ]] || die '--selectors requires a value'; selectors_file=$2; shift 2 ;;
        --receipt-context) [[ $# -ge 2 ]] || die '--receipt-context requires a value'; receipt_context=$2; shift 2 ;;
        --rating-id-context) [[ $# -ge 2 ]] || die '--rating-id-context requires a value'; rating_id_context=$2; shift 2 ;;
        --context-reader) [[ $# -ge 2 ]] || die '--context-reader requires a value'; context_reader=$2; shift 2 ;;
        --receipt-oracle) [[ $# -ge 2 ]] || die '--receipt-oracle requires a value'; receipt_oracle=$2; shift 2 ;;
        --output-root) [[ $# -ge 2 ]] || die '--output-root requires a value'; output_root=$2; shift 2 ;;
        --serial) [[ $# -ge 2 ]] || die '--serial requires a value'; serial=$2; shift 2 ;;
        --adb) [[ $# -ge 2 ]] || die '--adb requires a value'; adb_bin=$2; shift 2 ;;
        --maestro) [[ $# -ge 2 ]] || die '--maestro requires a value'; maestro_bin=$2; shift 2 ;;
        --apksigner) [[ $# -ge 2 ]] || die '--apksigner requires a value'; apksigner_bin=$2; shift 2 ;;
        --java-home) [[ $# -ge 2 ]] || die '--java-home requires a value'; java_home=$2; shift 2 ;;
        --node) [[ $# -ge 2 ]] || die '--node requires a value'; node_bin=$2; shift 2 ;;
        --install-candidate) install_candidate=true; shift ;;
        --help|-h) usage; exit 0 ;;
        *) die "unknown argument: $1" ;;
    esac
done

require_value --phase "$phase"
require_value --run-id "$run_id"
require_value --candidate-head "$candidate_head"
require_value --apk "$candidate_apk"
require_value --run-context "$run_context"
require_value --selectors "$selectors_file"
require_value --output-root "$output_root"
[[ "$run_id" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$ ]] || die 'run ID must be 3..64 safe characters'
[[ "$candidate_head" =~ ^[a-f0-9]{40}$ ]] || die 'candidate HEAD must be lowercase 40-hex'
[[ -f "$candidate_apk" ]] || die "candidate APK does not exist: $candidate_apk"
if [[ "$install_candidate" == true && "$phase" != preflight && "$phase" != prepare ]]; then
    die '--install-candidate is allowed only before the retained ticket is prepared'
fi

parse_key_value_file "$run_context" run
parse_key_value_file "$selectors_file" selectors
validate_run_context
validate_selectors

# Validate all immutable and read-only inputs before the runner updates the retained APK or changes
# its three allowed reverse mappings.
case "$phase" in
    settle|resume-settle)
        require_value --receipt-context "$receipt_context"
        require_value --context-reader "$context_reader"
        [[ -f "$context_reader" ]] || die "context reader does not exist: $context_reader"
        validate_receipt_context "$receipt_context" false
        validate_unsettled_server_context "$receipt_context"
        prepare_ticket_selectors
        ;;
    deliver)
        require_value --receipt-context "$receipt_context"
        require_value --context-reader "$context_reader"
        [[ -f "$context_reader" ]] || die "context reader does not exist: $context_reader"
        validate_receipt_context "$receipt_context" false
        validate_unsettled_server_context "$receipt_context"
        prepare_ticket_selectors
        ;;
    rate)
        require_value --receipt-context "$receipt_context"
        validate_receipt_context "$receipt_context" false
        validate_confirmed_settlement "$receipt_context"
        prepare_ticket_selectors
        ;;
    deliver-rating)
        require_value --receipt-context "$receipt_context"
        require_value --rating-id-context "$rating_id_context"
        validate_receipt_context "$receipt_context" false
        validate_confirmed_settlement "$receipt_context"
        validate_rating_id_context "$rating_id_context" \
            "$(json_value "$receipt_context" ticketId)" \
            "$(json_value "$receipt_context" attemptId)"
        prepare_ticket_selectors
        ;;
    verify)
        require_value --receipt-context "$receipt_context"
        require_value --rating-id-context "$rating_id_context"
        require_value --context-reader "$context_reader"
        require_value --receipt-oracle "$receipt_oracle"
        [[ -f "$context_reader" ]] || die "context reader does not exist: $context_reader"
        [[ -f "$receipt_oracle" ]] || die "receipt oracle does not exist: $receipt_oracle"
        validate_receipt_context "$receipt_context" false
        validate_confirmed_settlement "$receipt_context"
        validate_rating_id_context "$rating_id_context" \
            "$(json_value "$receipt_context" ticketId)" \
            "$(json_value "$receipt_context" attemptId)"
        ;;
esac

umask 077
mkdir -p -- "$output_root"
[[ -d "$output_root" ]] || die 'output root is not a directory'
output_dir=$(mktemp -d "$output_root/${run_id}.$(date -u +%Y%m%dT%H%M%SZ).XXXXXX")
chmod 700 "$output_dir"
write_manifest

case "$phase" in
    preflight|prepare|settle|resume-settle|deliver|rate|deliver-rating)
        require_value --serial "$serial"
        assert_device_and_candidate
        ensure_reverse_mappings
        ;;
    verify)
        ;;
esac

case "$phase" in
    preflight)
        run_maestro "$(dirname "$0")/flows/00-identity-preflight.yaml"
        overall_status='NOT_RUN'
        phase_status='PASS'
        next_required='capture confirmed selectors and run the retained-account preparation phase'
        ;;
    prepare)
        run_maestro "$(dirname "$0")/flows/00-identity-preflight.yaml"
        run_maestro "$(dirname "$0")/flows/01-selected-hard-prepare.yaml"
        overall_status='NOT_RUN'
        phase_status='PASS'
        next_required='capture a fresh read-only receipt context with the prepared ticket and attempt IDs'
        ;;
    settle)
        run_maestro "$(dirname "$0")/flows/02-start-answer-relaunch.yaml"
        run_maestro "$(dirname "$0")/flows/03-finish.yaml"
        capture_reader_context "$output_dir/pending-settlement-context.json"
        validate_receipt_context "$output_dir/pending-settlement-context.json" false
        validate_unsettled_server_context "$output_dir/pending-settlement-context.json"
        printf 'pendingSettlementContextSha256=%s\n' "$(sha256_file "$output_dir/pending-settlement-context.json")" >> "$output_dir/run-manifest.env"
        overall_status='NOT_RUN'
        phase_status='PASS'
        next_required='use the retained exact ticket to synchronize its saved settlement request'
        ;;
    resume-settle)
        run_maestro "$(dirname "$0")/flows/02-resume-saved-answer-and-finish.yaml"
        run_maestro "$(dirname "$0")/flows/03-finish.yaml"
        capture_reader_context "$output_dir/pending-settlement-context.json"
        validate_receipt_context "$output_dir/pending-settlement-context.json" false
        validate_unsettled_server_context "$output_dir/pending-settlement-context.json"
        printf 'pendingSettlementContextSha256=%s\n' "$(sha256_file "$output_dir/pending-settlement-context.json")" >> "$output_dir/run-manifest.env"
        overall_status='NOT_RUN'
        phase_status='PASS'
        next_required='use the retained exact ticket to synchronize its saved settlement request'
        ;;
    deliver)
        run_maestro "$(dirname "$0")/flows/03-sync-pending-settlement.yaml"
        capture_reader_context "$output_dir/confirmed-settlement-context.json"
        validate_receipt_context "$output_dir/confirmed-settlement-context.json" false
        validate_confirmed_settlement "$output_dir/confirmed-settlement-context.json"
        printf 'confirmedSettlementContextSha256=%s\n' "$(sha256_file "$output_dir/confirmed-settlement-context.json")" >> "$output_dir/run-manifest.env"
        overall_status='NOT_RUN'
        phase_status='PASS'
        next_required='inspect the settlement context, then submit rating 2 from the confirmed result screen'
        ;;
    rate)
        run_maestro "$(dirname "$0")/flows/04-rate-two.yaml"
        overall_status='NOT_RUN'
        phase_status='PASS'
        next_required='capture a read-only Room rating-ID context, then deliver the saved rating intent'
        ;;
    deliver-rating)
        run_maestro "$(dirname "$0")/flows/04-send-queued-rating.yaml"
        overall_status='NOT_RUN'
        phase_status='PASS'
        next_required='run verify with the retained Room rating-ID context and the two-receipt oracle'
        ;;
    verify)
        capture_reader_context_after_rating "$output_dir/receipt-context-after-rating.json"
        receipt_context="$output_dir/receipt-context-after-rating.json"
        validate_receipt_context "$receipt_context" true
        validate_confirmed_settlement "$receipt_context"
        printf 'ratingReceiptContextSha256=%s\n' "$(sha256_file "$receipt_context")" >> "$output_dir/run-manifest.env"
        printf 'ratingIdContextSha256=%s\n' "$(sha256_file "$rating_id_context")" >> "$output_dir/run-manifest.env"
        run_full_oracle
        overall_status='PASS'
        phase_status='PASS'
        next_required='none'
        ;;
    *) die "unknown phase: $phase" ;;
esac
