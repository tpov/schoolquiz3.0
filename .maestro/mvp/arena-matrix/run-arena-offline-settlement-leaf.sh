#!/usr/bin/env bash
# The only children accepted by run-arena-offline-settlement.sh's sequence plan.
# This file deliberately has no lock, transport, install, clear, cancel, synchronize, or rating code.
set -Eeuo pipefail
umask 077

readonly LEAF_VERSION='arena-offline-settlement-leaf-v2'
readonly APP_ID='com.tpov.schoolquiz.mvp'
readonly EVIDENCE_PROJECT='demo-schoolquiz-mvp'
readonly EVIDENCE_FIRESTORE_HOST='schoolquiz-mvp-firebase:8080'
readonly ALLOWED_REVERSE_PORTS=(9099 8080 5001)
readonly AUTH_STORE_PREFIX='com.google.firebase.auth.api.Store.'

phase=''
run_id=''
candidate_head=''
candidate_apk=''
run_context=''
selectors_file=''
output_dir=''
serial=''
adb_bin=''
maestro_bin=''
apksigner_bin=''
java_home=''
node_bin=''
ticket_discovery=''
context_reader=''
attempt_progress_reader=''
pending_settlement_reader=''
preparation_preflight_reader=''
input_lock=''
input_lock_sha256=''
guard_capability=''
guard_nonce=''
recovery_record=''
identity_flow=''
prepare_flow=''
start_flow=''
resume_flow=''
finish_flow=''
overall_status='INTERRUPTED'
next_required='inspect the retained ticket and private leaf evidence before retrying'
result_path=''
raw_snapshot_dir=''

run_seen_keys=$'\n'
selector_seen_keys=$'\n'
readonly SELECTOR_KEYS=(
    E2B_SELECTOR_INTERNET E2B_SELECTOR_ARENA_TITLE E2B_SELECTOR_REFRESH_BALANCE
    E2B_SELECTOR_NOLICS_BALANCE E2B_SELECTOR_LIFE_BALANCE E2B_SELECTOR_LOAD_LESSONS
    E2B_SELECTOR_CATALOG_LOADED E2B_SELECTOR_TARGET_LESSON E2B_SELECTOR_FIXTURE_AUTHOR
    E2B_SELECTOR_FROZEN_PRICE E2B_SELECTOR_SELECTED_PREPARE E2B_SELECTOR_PREPARE_DIALOG
    E2B_SELECTOR_DIFFICULTY_HARD E2B_SELECTOR_HARD_SELECTED E2B_SELECTOR_PREPARE_CONFIRM
    E2B_SELECTOR_PREPARED_TICKET E2B_SELECTOR_SHOW_LOCAL_TICKETS E2B_SELECTOR_TICKET_LABEL_TEMPLATE
    E2B_SELECTOR_TICKET_START E2B_SELECTOR_TICKET_OPEN E2B_SELECTOR_QUESTION E2B_SELECTOR_FIRST_ANSWER
    E2B_SELECTOR_SAVED_ANSWER E2B_SELECTOR_RESUME_DIALOG E2B_SELECTOR_RESUME_OR_SAVED
    E2B_SELECTOR_RESUME_CONTINUE E2B_SELECTOR_SECOND_QUESTION E2B_SELECTOR_SECOND_ANSWER
    E2B_SELECTOR_CONTINUE_LESSON E2B_SELECTOR_SYNCHRONIZE E2B_SELECTOR_SETTLE_PENDING
)

usage() {
    cat <<'USAGE'
Usage:
  run-arena-offline-settlement-leaf.sh --phase pre-cut|offline --run-id <id> --candidate-head <40-hex>
    --apk <candidate.apk> --run-context <immutable.env> --selectors <confirmed.env>
    --output-dir <private-run-dir> --serial <adb-serial> --adb <path> --maestro <path>
    --apksigner <path> --java-home <path> --node <path> --context-reader <reader.js>
    --input-lock <private-lock.json> --input-lock-sha256 <sha256> \\
    --guard-capability <guard-issued.json> --guard-nonce <hex> --recovery-record <private.json>
    [--ticket-discovery <reader.js>] [--attempt-progress-reader <reader.js>]
    [--pending-settlement-reader <reader.js>] [--preparation-preflight-reader <reader.js>]
    --identity-flow <00-identity.yaml> --prepare-flow <01-prepare.yaml> \\
    --start-flow <02-start.yaml> --resume-flow <02-resume.yaml> --finish-flow <03-finish.yaml>

This is a private sequence-plan leaf, not a standalone runner.  A guard-issued, single-use
capability and a sealed input lock are mandatory before it can do anything.
USAGE
}

fail() {
    overall_status='FAIL'
    next_required='inspect the private leaf log and retained ticket state; do not repeat preparation blindly'
    printf 'Arena offline-settlement leaf: %s\n' "$*" >&2
    exit 2
}

cleanup_raw_snapshot() {
    [[ -n "$raw_snapshot_dir" ]] || return 0
    rm -f -- "$raw_snapshot_dir/schoolquiz.db" "$raw_snapshot_dir/schoolquiz.db-wal" \
        "$raw_snapshot_dir/schoolquiz.db-shm" "$raw_snapshot_dir/firebase-auth.xml" || true
    rmdir -- "$raw_snapshot_dir" 2>/dev/null || true
    raw_snapshot_dir=''
}

write_result() {
    local exit_code=$?
    trap - EXIT
    cleanup_raw_snapshot
    if [[ -n "$result_path" ]]; then
        "$node_bin" - "$result_path" "$overall_status" "$phase" "$run_id" "$next_required" <<'NODE' || true
const fs = require('node:fs');
const [destination, status, phase, runId, nextRequired] = process.argv.slice(2);
fs.writeFileSync(destination, JSON.stringify({
  status, phase, runId, nextRequired, leafVersion: 'arena-offline-settlement-leaf-v2',
  resultArtifactVersion: 1,
}, null, 2) + '\n', {mode: 0o600});
NODE
    fi
    exit "$exit_code"
}

on_error() {
    local exit_code=$?
    if [[ "$overall_status" == 'INTERRUPTED' ]]; then overall_status='FAIL'; fi
    exit "$exit_code"
}

trap write_result EXIT
trap on_error ERR

require_value() {
    [[ -n "$2" ]] || fail "$1 is required"
}

sha256_file() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    else
        fail 'SHA-256 tool is unavailable'
    fi
}

verify_locked_input() {
    local key=$1 candidate=$2
    if ! "$node_bin" - "$input_lock" "$input_lock_sha256" "$key" "$candidate" <<'NODE'
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const [lockPath, expectedLockSha, key, candidate] = process.argv.slice(2);
const hash = (file) => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
try {
  const lockStat = fs.lstatSync(lockPath);
  if (!lockStat.isFile() || lockStat.isSymbolicLink() || (lockStat.mode & 0o077) !== 0 || hash(lockPath) !== expectedLockSha) process.exit(2);
  const lock = JSON.parse(fs.readFileSync(lockPath, 'utf8'));
  if (!lock || lock.artifactVersion !== 1 || !lock.inputs || typeof lock.inputs !== 'object' || Array.isArray(lock.inputs)) process.exit(2);
  const item = lock.inputs[key];
  if (!item || Object.keys(item).sort().join(',') !== 'path,sha256' || typeof item.path !== 'string' ||
      typeof item.sha256 !== 'string' || !/^[a-f0-9]{64}$/.test(item.sha256) || path.resolve(item.path) !== path.resolve(candidate)) process.exit(2);
  const stat = fs.lstatSync(candidate);
  if (!stat.isFile() || stat.isSymbolicLink() || hash(candidate) !== item.sha256) process.exit(2);
} catch (_) { process.exit(2); }
NODE
    then
        fail "sealed input mismatch for $key"
    fi
}

verify_guard_capability() {
    if ! "$node_bin" - "$guard_capability" "$guard_nonce" "$phase" "$serial" "$APP_ID" "$output_dir" <<'NODE'
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const [file, nonce, phase, serial, appId, outputDir] = process.argv.slice(2);
try {
  if (!/^[a-f0-9]{64}$/.test(nonce)) process.exit(2);
  const requestedOutputDir = path.resolve(outputDir);
  const canonicalOutputDir = fs.realpathSync(requestedOutputDir);
  if (fs.realpathSync(path.dirname(path.resolve(file))) !== canonicalOutputDir) process.exit(2);
  const stat = fs.lstatSync(file);
  if (!stat.isFile() || stat.isSymbolicLink() || (stat.mode & 0o077) !== 0) process.exit(2);
  const plan = path.join(requestedOutputDir, 'sequence-plan.json');
  const planStat = fs.lstatSync(plan);
  if (!planStat.isFile() || planStat.isSymbolicLink() || (planStat.mode & 0o077) !== 0) process.exit(2);
  const value = JSON.parse(fs.readFileSync(file, 'utf8'));
  const expected = ['appId', 'artifactVersion', 'guardPid', 'guardVersion', 'nonce', 'phase', 'sequencePlanSha256', 'serialSha256'].sort().join(',');
  if (!value || Object.keys(value).sort().join(',') !== expected || value.artifactVersion !== 1 || value.phase !== phase ||
      value.nonce !== nonce || value.appId !== appId || typeof value.guardPid !== 'number' || value.guardPid < 1 ||
      !/^[a-f0-9]{64}$/.test(value.sequencePlanSha256) ||
      value.sequencePlanSha256 !== crypto.createHash('sha256').update(fs.readFileSync(plan)).digest('hex') ||
      value.serialSha256 !== crypto.createHash('sha256').update(serial).digest('hex')) process.exit(2);
  process.kill(value.guardPid, 0);
  const consumed = path.join(path.dirname(file), path.basename(file) + '.consumed');
  const descriptor = fs.openSync(consumed, 'wx', 0o600);
  fs.writeFileSync(descriptor, JSON.stringify({phase, nonceSha256: crypto.createHash('sha256').update(nonce).digest('hex')}) + '\n');
  fs.fsyncSync(descriptor); fs.closeSync(descriptor);
} catch (_) { process.exit(2); }
NODE
    then
        fail 'guard capability is absent, stale, malformed, bound to another plan, or already consumed'
    fi
}

verify_recovery_record() {
    if ! E2B_RECOVERY_OWNER_UID="$E2B_OWNER_UID" "$node_bin" - "$recovery_record" "$output_dir" "$phase" "$input_lock_sha256" "$run_id" "$candidate_head" <<'NODE'
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const [file, outputDir, phase, inputLockSha256, runId, candidateHead] = process.argv.slice(2);
const regularPrivate = (candidate) => {
  const stat = fs.lstatSync(candidate);
  return stat.isFile() && !stat.isSymbolicLink() && (stat.mode & 0o077) === 0;
};
try {
  const parent = path.dirname(path.resolve(file));
  const parentStat = fs.lstatSync(parent);
  if (!parentStat.isDirectory() || parentStat.isSymbolicLink() || (parentStat.mode & 0o077) !== 0 || !regularPrivate(file)) process.exit(2);
  const value = JSON.parse(fs.readFileSync(file, 'utf8'));
  const baseKeys = ['artifactVersion', 'candidateHead', 'createdAtMs', 'inputLockSha256', 'outputDir', 'ownerUidSha256', 'runId', 'status', 'updatedAtMs'];
  const ticketKeys = [...baseKeys, 'attemptId', 'preparedContextSha256', 'ticketId'];
  const hasTicket = ['TICKET_DISCOVERED', 'PREPARED', 'OFFLINE_PENDING'].includes(value?.status);
  const expectedKeys = (hasTicket ? ticketKeys : baseKeys).sort().join(',');
  if (!value || Object.keys(value).sort().join(',') !== expectedKeys || value.artifactVersion !== 1 ||
      !['PREPARE_GUARDED', 'PREPARE_STARTED', 'TICKET_DISCOVERED', 'PREPARED', 'OFFLINE_PENDING'].includes(value.status) ||
      value.ownerUidSha256 !== crypto.createHash('sha256').update(process.env.E2B_RECOVERY_OWNER_UID || '').digest('hex') ||
      path.resolve(value.outputDir) !== path.resolve(outputDir) ||
      !Number.isSafeInteger(value.createdAtMs) || !Number.isSafeInteger(value.updatedAtMs) || value.updatedAtMs < value.createdAtMs ||
      value.inputLockSha256 !== inputLockSha256 || value.runId !== runId || value.candidateHead !== candidateHead ||
      !/^[a-f0-9]{64}$/.test(value.inputLockSha256)) process.exit(2);
  if (hasTicket && (!/^[a-f0-9]{64}$/.test(value.ticketId) || !/^[a-f0-9]{64}$/.test(value.attemptId) ||
      !/^[a-f0-9]{64}$/.test(value.preparedContextSha256))) process.exit(2);
  if ((phase === 'pre-cut' && value.status !== 'PREPARE_GUARDED') || (phase === 'offline' && value.status !== 'PREPARED')) process.exit(2);
} catch (_) { process.exit(2); }
NODE
    then
        fail 'recovery record is malformed, belongs to another owner, or does not permit this leaf'
    fi
}

require_private_directory() {
    if ! "$node_bin" - "$1" <<'NODE'
const fs = require('node:fs');
const path = require('node:path');
try {
  const requested = path.resolve(process.argv[2]);
  const stat = fs.lstatSync(requested);
  const canonical = fs.realpathSync(requested);
  if (!stat.isDirectory() || stat.isSymbolicLink() || (stat.mode & 0o077) !== 0) process.exit(2);
  for (let current = canonical;; current = path.dirname(current)) {
    if (fs.lstatSync(current).isSymbolicLink()) process.exit(2);
    if (path.dirname(current) === current) break;
  }
} catch { process.exit(2); }
NODE
    then
        fail "output directory must be a private non-symlink directory: $1"
    fi
}

parse_key_value_file() {
    local file=$1 kind=$2 line line_number=0 key value
    [[ -f "$file" ]] || fail "input file does not exist: $file"
    while IFS= read -r line || [[ -n "$line" ]]; do
        ((++line_number))
        [[ -z "$line" || "$line" == \#* ]] && continue
        [[ "$line" =~ ^([A-Z][A-Z0-9_]*)=(.*)$ ]] || fail "$file:$line_number is not KEY=value data"
        key=${BASH_REMATCH[1]}
        value=${BASH_REMATCH[2]}
        [[ "$key" == E2B_* ]] || fail "$file:$line_number has a non-E2B key"
        [[ "$value" != *$'\n'* && "$value" != *$'\r'* ]] || fail "$file:$line_number contains a control character"
        case "$kind" in
            run)
                [[ "$run_seen_keys" != *$'\n'"$key"$'\n'* ]] || fail "$file:$line_number duplicates $key"
                run_seen_keys+="$key"$'\n' ;;
            selectors)
                [[ "$selector_seen_keys" != *$'\n'"$key"$'\n'* ]] || fail "$file:$line_number duplicates $key"
                selector_seen_keys+="$key"$'\n' ;;
            *) fail 'unknown input kind' ;;
        esac
        printf -v "$key" '%s' "$value"
    done < "$file"
}

require_file_key() {
    local kind=$1 key=$2 seen value
    case "$kind" in
        run) seen=$run_seen_keys ;;
        selectors) seen=$selector_seen_keys ;;
        *) fail 'unknown input kind' ;;
    esac
    [[ "$seen" == *$'\n'"$key"$'\n'* ]] || fail "$key is missing from $kind input"
    value=${!key}
    [[ -n "$value" ]] || fail "$key is empty"
}

validate_run_context() {
    local key
    for key in E2B_RUN_ID E2B_CANDIDATE_HEAD E2B_APP_ID E2B_APK_SHA256 E2B_APK_CERT_SHA256 \
        E2B_ACCOUNT_REF E2B_OWNER_UID E2B_LESSON_ID E2B_LESSON_VERSION \
        E2B_EXPECTED_FROZEN_PRICE_NOLICS E2B_EXPECTED_MODE E2B_EXPECTED_DIFFICULTY E2B_RATING; do
        require_file_key run "$key"
    done
    [[ "$E2B_RUN_ID" == "$run_id" && "$E2B_CANDIDATE_HEAD" == "$candidate_head" && "$E2B_APP_ID" == "$APP_ID" ]] ||
        fail 'immutable run context does not bind this candidate'
    [[ "$E2B_APK_SHA256" =~ ^[a-f0-9]{64}$ && "$E2B_APK_CERT_SHA256" =~ ^[a-f0-9]{64}$ ]] || fail 'run context has invalid APK hashes'
    [[ "$E2B_EXPECTED_MODE" == SELECTED && "$E2B_EXPECTED_DIFFICULTY" == HARD && "$E2B_RATING" == 2 ]] ||
        fail 'this runner accepts only the agreed SELECTED/HARD context with rating 2'
    [[ "$E2B_LESSON_VERSION" =~ ^[1-9][0-9]*$ && "$E2B_EXPECTED_FROZEN_PRICE_NOLICS" =~ ^[1-9][0-9]*$ ]] ||
        fail 'run context has invalid lesson version or selected price'
    (( E2B_EXPECTED_FROZEN_PRICE_NOLICS <= 100 )) || fail 'selected price is outside the product range'
}

required_selector_keys() {
    case "$phase" in
        pre-cut)
            printf '%s\n' \
                E2B_SELECTOR_INTERNET E2B_SELECTOR_ARENA_TITLE E2B_SELECTOR_REFRESH_BALANCE \
                E2B_SELECTOR_NOLICS_BALANCE E2B_SELECTOR_LIFE_BALANCE E2B_SELECTOR_LOAD_LESSONS \
                E2B_SELECTOR_CATALOG_LOADED E2B_SELECTOR_TARGET_LESSON E2B_SELECTOR_FIXTURE_AUTHOR \
                E2B_SELECTOR_FROZEN_PRICE E2B_SELECTOR_SELECTED_PREPARE E2B_SELECTOR_PREPARE_DIALOG \
                E2B_SELECTOR_DIFFICULTY_HARD E2B_SELECTOR_HARD_SELECTED E2B_SELECTOR_PREPARE_CONFIRM \
                E2B_SELECTOR_SHOW_LOCAL_TICKETS E2B_SELECTOR_PREPARED_TICKET ;;
        offline)
            printf '%s\n' \
                E2B_SELECTOR_INTERNET E2B_SELECTOR_ARENA_TITLE E2B_SELECTOR_SHOW_LOCAL_TICKETS \
                E2B_SELECTOR_TICKET_LABEL_TEMPLATE E2B_SELECTOR_TICKET_START E2B_SELECTOR_TICKET_OPEN \
                E2B_SELECTOR_QUESTION E2B_SELECTOR_FIRST_ANSWER E2B_SELECTOR_SAVED_ANSWER \
                E2B_SELECTOR_RESUME_DIALOG E2B_SELECTOR_RESUME_OR_SAVED E2B_SELECTOR_RESUME_CONTINUE \
                E2B_SELECTOR_SECOND_QUESTION E2B_SELECTOR_SECOND_ANSWER E2B_SELECTOR_CONTINUE_LESSON \
                E2B_SELECTOR_SYNCHRONIZE E2B_SELECTOR_SETTLE_PENDING ;;
        *) fail 'unknown leaf phase' ;;
    esac
}

validate_selectors() {
    local key
    while IFS= read -r key; do
        [[ -z "$key" ]] || require_file_key selectors "$key"
    done < <(required_selector_keys)
    if [[ "$phase" == offline ]]; then
        [[ "$E2B_SELECTOR_TICKET_LABEL_TEMPLATE" == *'{{ticketId}}'* ]] || fail 'ticket label selector must contain {{ticketId}}'
        [[ "${E2B_SELECTOR_TICKET_LABEL_TEMPLATE#*'{{ticketId}}'}" != *'{{ticketId}}'* ]] ||
            fail 'ticket label selector must contain {{ticketId}} exactly once'
    fi
}

json_value() {
    "$node_bin" - "$1" "$2" <<'NODE'
const fs = require('node:fs');
const [file, key] = process.argv.slice(2);
const value = JSON.parse(fs.readFileSync(file, 'utf8'))[key];
if (typeof value !== 'string' && typeof value !== 'number') process.exit(2);
process.stdout.write(String(value));
NODE
}

assert_candidate() {
    verify_locked_input candidate-apk "$candidate_apk"
    "$adb_bin" -s "$serial" get-state | grep -qx device || fail 'ADB target is not ready'
    "$adb_bin" -s "$serial" shell pm path "$APP_ID" | grep -q '^package:' || fail 'retained application is not installed'
    [[ "$(sha256_file "$candidate_apk")" == "$E2B_APK_SHA256" ]] || fail 'candidate APK SHA-256 differs from run context'
    local certificate
    certificate=$(JAVA_HOME="$java_home" PATH="$java_home/bin:$PATH" "$apksigner_bin" verify --print-certs "$candidate_apk") ||
        fail 'candidate APK signature is invalid'
    certificate=$(printf '%s\n' "$certificate" | awk -F': ' '/Signer #1 certificate SHA-256 digest:/ {print tolower($2); exit}')
    [[ "$certificate" == "$E2B_APK_CERT_SHA256" ]] || fail 'candidate APK certificate differs from run context'
    local package_paths installed_base
    package_paths=$("$adb_bin" -s "$serial" shell pm path --user 0 "$APP_ID") || fail 'could not read installed package path'
    installed_base=$(printf '%s\n' "$package_paths" | tr -d '\r' | sed -n 's/^package://p' | head -n 1)
    [[ "$installed_base" == /data/app/*/base.apk ]] || fail 'installed package is split or has an unexpected base path'
    [[ $(printf '%s\n' "$package_paths" | tr -d '\r' | sed -n 's/^package://p' | wc -l | tr -d ' ') == 1 ]] ||
        fail 'installed package has more than one APK path'
    local installed_sha
    installed_sha=$("$adb_bin" -s "$serial" shell sha256sum "$installed_base" | tr -d '\r' | awk 'NR == 1 {print tolower($1); exit}') ||
        fail 'could not hash installed base APK'
    [[ "$installed_sha" =~ ^[a-f0-9]{64}$ && "$installed_sha" == "$E2B_APK_SHA256" ]] ||
        fail 'installed base APK differs from the candidate'
}

run_maestro() {
    local name=$1 key=$2 flow=$3
    verify_locked_input "$key" "$flow"
    local flow_output="$output_dir/maestro-$phase-$name"
    mkdir -m 700 "$flow_output"
    local -a args=(test -e "E2B_RUN_ID=$run_id" -e "E2B_APP_ID=$APP_ID" -e "E2B_RATING=$E2B_RATING")
    local key
    for key in "${SELECTOR_KEYS[@]}"; do
        if [[ "$selector_seen_keys" == *$'\n'"$key"$'\n'* ]]; then args+=(-e "$key=${!key}"); fi
    done
    [[ -z ${E2B_SELECTOR_TICKET_LABEL-} ]] || args+=(-e "E2B_SELECTOR_TICKET_LABEL=$E2B_SELECTOR_TICKET_LABEL")
    args+=(--test-output-dir "$flow_output" "$flow")
    "$maestro_bin" --device "$serial" --no-ansi "${args[@]}" > "$output_dir/maestro-$phase-$name.log" 2>&1 ||
        fail "Maestro flow failed: $name"
}

capture_evidence_json() {
    local destination=$1 key=$2
    shift 2
    verify_locked_input "$key" "$2"
    local temporary="${destination}.tmp.$$"
    [[ ! -e "$destination" && ! -e "$temporary" ]] || fail "evidence destination already exists: $destination"
    if ! env -u GOOGLE_APPLICATION_CREDENTIALS -u FIREBASE_CONFIG \
        GCLOUD_PROJECT="$EVIDENCE_PROJECT" FIRESTORE_EMULATOR_HOST="$EVIDENCE_FIRESTORE_HOST" \
        "$@" > "$temporary"; then
        rm -f -- "$temporary"
        fail "read-only evidence tool failed for $(basename "$destination")"
    fi
    chmod 600 "$temporary"
    mv -- "$temporary" "$destination"
}

assert_cut_transport() {
    "$adb_bin" -s "$serial" get-state | grep -qx device || fail 'ADB control channel is not ready under the cut'
    [[ $("$adb_bin" -s "$serial" shell settings get global wifi_on | tr -d '\r\n') == 0 ]] || fail 'Wi-Fi is enabled during offline leaf'
    [[ $("$adb_bin" -s "$serial" shell settings get global mobile_data | tr -d '\r\n') == 0 ]] || fail 'mobile data is enabled during offline leaf'
    [[ -z $("$adb_bin" -s "$serial" reverse --list) ]] || fail 'an adb reverse mapping exists during offline leaf'
}

copy_room_snapshot() {
    local label=$1 reader_key=$2 reader=$3 destination=$4 database suffix temporary
    verify_locked_input "$reader_key" "$reader"
    raw_snapshot_dir=$(mktemp -d "$output_dir/raw-room-$label.XXXXXX") || fail 'could not create private Room snapshot directory'
    chmod 700 "$raw_snapshot_dir"
    database="$raw_snapshot_dir/schoolquiz.db"
    temporary="${destination}.tmp.$$"
    if ! "$adb_bin" -s "$serial" exec-out run-as "$APP_ID" cat databases/schoolquiz.db > "$database"; then
        cleanup_raw_snapshot
        fail 'could not copy Room database from the stopped retained app'
    fi
    chmod 600 "$database"
    for suffix in -wal -shm; do
        if "$adb_bin" -s "$serial" shell run-as "$APP_ID" ls "databases/schoolquiz.db${suffix}" >/dev/null 2>&1; then
            if ! "$adb_bin" -s "$serial" exec-out run-as "$APP_ID" cat "databases/schoolquiz.db${suffix}" > "${database}${suffix}"; then
                cleanup_raw_snapshot
                fail 'could not copy a Room WAL/SHM sidecar'
            fi
            chmod 600 "${database}${suffix}"
        fi
    done
    if ! "$node_bin" "$reader" --db "$database" --run-id "$run_id" --owner-uid "$E2B_OWNER_UID" \
        --ticket-id "$ticket_id" --attempt-id "$attempt_id" > "$temporary"; then
        rm -f -- "$temporary"
        cleanup_raw_snapshot
        fail "Room reader failed for $label"
    fi
    chmod 600 "$temporary"
    cleanup_raw_snapshot
    mv -- "$temporary" "$destination"
}

capture_preparation_preflight() {
    local destination=$1 database auth_prefs temporary auth_name names=() suffix
    verify_locked_input preparation-preflight-reader "$preparation_preflight_reader"
    raw_snapshot_dir=$(mktemp -d "$output_dir/raw-room-preparation.XXXXXX") || fail 'could not create private preparation snapshot directory'
    chmod 700 "$raw_snapshot_dir"
    database="$raw_snapshot_dir/schoolquiz.db"
    auth_prefs="$raw_snapshot_dir/firebase-auth.xml"
    temporary="${destination}.tmp.$$"
    if ! "$adb_bin" -s "$serial" exec-out run-as "$APP_ID" cat databases/schoolquiz.db > "$database"; then
        cleanup_raw_snapshot
        fail 'could not copy Room database from the stopped retained app for preparation preflight'
    fi
    chmod 600 "$database"
    for suffix in -wal -shm; do
        if "$adb_bin" -s "$serial" shell run-as "$APP_ID" ls "databases/schoolquiz.db${suffix}" >/dev/null 2>&1; then
            if ! "$adb_bin" -s "$serial" exec-out run-as "$APP_ID" cat "databases/schoolquiz.db${suffix}" > "${database}${suffix}"; then
                cleanup_raw_snapshot
                fail 'could not copy a Room sidecar for preparation preflight'
            fi
            chmod 600 "${database}${suffix}"
        fi
    done
    while IFS= read -r auth_name; do
        auth_name=${auth_name//$'\r'/}
        # Firebase Auth encodes the app key in a base64-like preference filename.  Keep the
        # remote `adb shell` argument restricted to that literal filename grammar; the previous
        # bracket-heavy ERE was not portable across Bash implementations.
        [[ "$auth_name" =~ ^com\.google\.firebase\.auth\.api\.Store\.[A-Za-z0-9._+=-]+\.xml$ ]] || continue
        names+=("$auth_name")
    done < <("$adb_bin" -s "$serial" shell run-as "$APP_ID" ls shared_prefs 2>/dev/null)
    (( ${#names[@]} == 1 )) || { cleanup_raw_snapshot; fail 'stopped app does not expose exactly one Firebase Auth Store preference'; }
    if ! "$adb_bin" -s "$serial" exec-out run-as "$APP_ID" cat "shared_prefs/${names[0]}" > "$auth_prefs"; then
        cleanup_raw_snapshot
        fail 'could not copy the stopped Firebase Auth preference'
    fi
    chmod 600 "$auth_prefs"
    if ! "$node_bin" "$preparation_preflight_reader" --db "$database" --auth-prefs "$auth_prefs" \
        --run-id "$run_id" --owner-uid "$E2B_OWNER_UID" --account-ref "$E2B_ACCOUNT_REF" > "$temporary"; then
        rm -f -- "$temporary"
        cleanup_raw_snapshot
        fail 'read-only preparation preflight rejected the retained app state'
    fi
    chmod 600 "$temporary"
    cleanup_raw_snapshot
    mv -- "$temporary" "$destination"
}

validate_preparation_preflight() {
    if ! "$node_bin" - "$1" "$run_id" "$E2B_OWNER_UID" "$E2B_ACCOUNT_REF" <<'NODE'
const crypto = require('node:crypto');
const fs = require('node:fs');
const [file, runId, ownerUid, accountRef] = process.argv.slice(2);
const value = JSON.parse(fs.readFileSync(file, 'utf8'));
const hash = (text) => crypto.createHash('sha256').update(text).digest('hex');
const q = value.quiescence;
const quiescenceCounters = ['outboxRows', 'unresolvedArenaCommands', 'reservedArenaTickets', 'orphanedSubmitIntents', 'orphanedRatingIntents'];
const quiescenceIsQuiescent = (candidate) => typeof candidate === 'object' && candidate !== null && !Array.isArray(candidate) &&
    Object.keys(candidate).sort().join(',') === [...quiescenceCounters].sort().join(',') &&
    Object.values(candidate).every((count) => typeof count === 'number' && count === 0);
const supportedRoomSchemas = new Set([
  JSON.stringify([14, 'ca5439bdc37decd3f21cb843d10bb3f4']),
  JSON.stringify([15, 'e3a1b9e13aff4304afeba070b8895b6c']),
]);
const room = value.room;
const roomSchemaSupported = room && supportedRoomSchemas.has(JSON.stringify([room.schemaVersion, room.schemaIdentityHash]));
if (value.status !== 'PASS' || value.readerVersion !== 'arena-e2e-preparation-preflight-reader-v1' || value.runId !== runId ||
    value.ownerUidSha256 !== hash(ownerUid) || value.accountRefSha256 !== hash(accountRef) ||
    value.auth?.cachedUser !== true || value.auth?.accessTokenBound !== true || !roomSchemaSupported ||
    !quiescenceIsQuiescent(q) || value.documentWrites !== 0) process.exit(2);
NODE
    then
        fail 'preparation preflight does not prove the retained owner and a quiescent Room state'
    fi
}

recovery_transition() {
    local expected_status=$1 next_status=$2 ticket=${3-} attempt=${4-} context_sha=${5-}
    if ! E2B_RECOVERY_RUN_ID="$run_id" E2B_RECOVERY_CANDIDATE_HEAD="$candidate_head" \
        E2B_RECOVERY_INPUT_LOCK_SHA="$input_lock_sha256" E2B_RECOVERY_OWNER_UID="$E2B_OWNER_UID" "$node_bin" - \
        "$recovery_record" "$output_dir" "$expected_status" "$next_status" "$ticket" "$attempt" "$context_sha" <<'NODE'
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const [file, outputDir, expectedStatus, nextStatus, ticketId, attemptId, contextSha] = process.argv.slice(2);
const transitions = new Map([
  ['PREPARE_GUARDED', 'PREPARE_STARTED'],
  ['PREPARE_STARTED', 'TICKET_DISCOVERED'],
  ['TICKET_DISCOVERED', 'PREPARED'],
  ['PREPARED', 'OFFLINE_PENDING'],
]);
const baseKeys = ['artifactVersion', 'candidateHead', 'createdAtMs', 'inputLockSha256', 'outputDir', 'ownerUidSha256', 'runId', 'status', 'updatedAtMs'];
const ticketKeys = [...baseKeys, 'attemptId', 'preparedContextSha256', 'ticketId'];
const expectedOwnerSha = crypto.createHash('sha256').update(process.env.E2B_RECOVERY_OWNER_UID || '').digest('hex');
const isPrivateFile = (candidate) => {
  const stat = fs.lstatSync(candidate);
  return stat.isFile() && !stat.isSymbolicLink() && (stat.mode & 0o077) === 0;
};
const isTicket = (value) => typeof value === 'string' && /^[a-f0-9]{64}$/.test(value);
try {
  if (transitions.get(expectedStatus) !== nextStatus) process.exit(2);
  const parent = path.dirname(path.resolve(file));
  const parentStat = fs.lstatSync(parent);
  if (!parentStat.isDirectory() || parentStat.isSymbolicLink() || (parentStat.mode & 0o077) !== 0 || !isPrivateFile(file)) process.exit(2);
  const lockPath = file + '.transition.lock';
  let lockDescriptor;
  try {
    lockDescriptor = fs.openSync(lockPath, fs.constants.O_CREAT | fs.constants.O_EXCL | fs.constants.O_WRONLY | fs.constants.O_NOFOLLOW, 0o600);
    fs.writeFileSync(lockDescriptor, String(process.pid) + '\n');
    fs.fsyncSync(lockDescriptor);
    fs.closeSync(lockDescriptor);
    lockDescriptor = undefined;
  } catch (_) { process.exit(2); }
  try {
  const stat = fs.lstatSync(file);
  if (!stat.isFile() || stat.isSymbolicLink() || (stat.mode & 0o077) !== 0) process.exit(2);
  const value = JSON.parse(fs.readFileSync(file, 'utf8'));
  if (!value || value.status !== expectedStatus || value.runId !== process.env.E2B_RECOVERY_RUN_ID ||
      value.candidateHead !== process.env.E2B_RECOVERY_CANDIDATE_HEAD || value.inputLockSha256 !== process.env.E2B_RECOVERY_INPUT_LOCK_SHA ||
      value.ownerUidSha256 !== expectedOwnerSha || path.resolve(value.outputDir) !== path.resolve(outputDir) ||
      Object.keys(value).sort().join(',') !== (['TICKET_DISCOVERED', 'PREPARED', 'OFFLINE_PENDING'].includes(expectedStatus) ? ticketKeys : baseKeys).sort().join(',')) process.exit(2);
  if (!Number.isSafeInteger(value.createdAtMs) || !Number.isSafeInteger(value.updatedAtMs) || value.updatedAtMs < value.createdAtMs) process.exit(2);
  const requiresTicket = nextStatus === 'TICKET_DISCOVERED' || expectedStatus === 'TICKET_DISCOVERED' || expectedStatus === 'PREPARED';
  if (requiresTicket && (!isTicket(ticketId) || !isTicket(attemptId) || !isTicket(contextSha))) process.exit(2);
  if (['TICKET_DISCOVERED', 'PREPARED'].includes(expectedStatus) &&
      (value.ticketId !== ticketId || value.attemptId !== attemptId)) process.exit(2);
  if (!requiresTicket && (ticketId || attemptId || contextSha)) process.exit(2);
  const next = {...value, status: nextStatus, updatedAtMs: Date.now()};
  if (requiresTicket) {
    next.ticketId = ticketId; next.attemptId = attemptId; next.preparedContextSha256 = contextSha;
  }
  const temporary = file + '.tmp.' + process.pid + '.' + crypto.randomBytes(16).toString('hex');
  fs.writeFileSync(temporary, JSON.stringify(next, null, 2) + '\n', {mode: 0o600, flag: 'wx'});
  fs.renameSync(temporary, file);
  fs.chmodSync(file, 0o600);
  } finally {
    if (lockDescriptor !== undefined) try { fs.closeSync(lockDescriptor); } catch (_) { /* best effort */ }
    try { fs.unlinkSync(lockPath); } catch (_) { /* retained lock fails future unsafe retries */ }
  }
} catch (_) { process.exit(2); }
NODE
    then
        fail 'could not persist the exact recovery state for this run'
    fi
}

validate_prepared_context() {
    if ! "$node_bin" - "$1" "$run_id" "$E2B_OWNER_UID" "$E2B_LESSON_ID" "$E2B_LESSON_VERSION" "$E2B_EXPECTED_FROZEN_PRICE_NOLICS" <<'NODE'
const fs = require('node:fs');
const [file, runId, ownerUid, lessonId, lessonVersion, price] = process.argv.slice(2);
const value = JSON.parse(fs.readFileSync(file, 'utf8'));
if (value.status !== 'CONTEXT' || value.runId !== runId || value.ownerUid !== ownerUid || value.lessonId !== lessonId ||
    String(value.lessonVersion) !== lessonVersion || String(value.frozenPriceNolics) !== price ||
    value.mode !== 'SELECTED' || value.difficulty !== 'HARD' || value.ticketStatus !== 'RESERVED' ||
    value.oracleVersion !== 'arena-e2e-context-reader-v2' || value.settlementReceipt !== null || value.event !== null ||
    value.resultEventDocumentExists !== false || !/^[a-f0-9]{64}$/.test(value.ticketId) ||
    !/^[a-f0-9]{64}$/.test(value.attemptId)) process.exit(2);
NODE
    then
        fail 'prepared server context is not the exact fresh unconfirmed ticket'
    fi
}

validate_offline_contexts() {
    if ! "$node_bin" - "$1" "$2" "$3" "$run_id" "$ticket_id" "$attempt_id" <<'NODE'
const fs = require('node:fs');
const [progressFile, pendingFile, serverFile, runId, ticketId, attemptId] = process.argv.slice(2);
const progress = JSON.parse(fs.readFileSync(progressFile, 'utf8'));
const pending = JSON.parse(fs.readFileSync(pendingFile, 'utf8'));
const server = JSON.parse(fs.readFileSync(serverFile, 'utf8'));
const same = (value) => value.status === 'CONTEXT' && value.runId === runId && value.ticketId === ticketId && value.attemptId === attemptId;
if (!same(progress) || !same(pending) || !same(server) ||
    progress.ticketState?.serverStatus !== 'RESERVED' || progress.ticketState?.localUse !== 'LAUNCHED' ||
    progress.ticketState?.decision !== 'NONE' || pending.ticketState?.serverStatus !== 'RESERVED' ||
    pending.ticketState?.localUse !== 'LAUNCHED' || pending.ticketState?.decision !== 'SETTLE' ||
    pending.pendingSettlement?.commandState !== 'PENDING' || pending.pendingSettlement?.firstIntentPresent !== true ||
    pending.pendingSettlement?.localReceiptCount !== 0 || pending.pendingSettlement?.localScoredAttemptRows !== 0 ||
    pending.pendingSettlement?.localScoredAnswerRows !== 0 || server.ticketStatus !== 'RESERVED' ||
    server.oracleVersion !== 'arena-e2e-context-reader-v2' || server.settlementReceipt !== null || server.event !== null ||
    server.resultEventDocumentExists !== false ||
    progress.ticketState?.snapshotSha256 !== pending.ticketState?.snapshotSha256) process.exit(2);
NODE
    then
        fail 'Room/server offline evidence does not bind one exact pending SETTLE'
    fi
}

write_leaf_manifest() {
    verify_locked_input candidate-apk "$candidate_apk"
    verify_locked_input run-context "$run_context"
    verify_locked_input selectors "$selectors_file"
    verify_locked_input context-reader "$context_reader"
    verify_locked_input identity-flow "$identity_flow"
    verify_locked_input prepare-flow "$prepare_flow"
    verify_locked_input start-flow "$start_flow"
    verify_locked_input resume-flow "$resume_flow"
    verify_locked_input finish-flow "$finish_flow"
    {
        printf 'leafVersion=%s\n' "$LEAF_VERSION"
        printf 'phase=%s\n' "$phase"
        printf 'runId=%s\n' "$run_id"
        printf 'candidateHead=%s\n' "$candidate_head"
        printf 'candidateApkSha256=%s\n' "$(sha256_file "$candidate_apk")"
        printf 'runContextSha256=%s\n' "$(sha256_file "$run_context")"
        printf 'selectorMapSha256=%s\n' "$(sha256_file "$selectors_file")"
        printf 'contextReaderSha256=%s\n' "$(sha256_file "$context_reader")"
        printf 'inputLockSha256=%s\n' "$input_lock_sha256"
        printf 'recoveryRecordSha256=%s\n' "$(sha256_file "$recovery_record")"
        printf 'identityFlowSha256=%s\n' "$(sha256_file "$identity_flow")"
        printf 'prepareFlowSha256=%s\n' "$(sha256_file "$prepare_flow")"
        printf 'startFlowSha256=%s\n' "$(sha256_file "$start_flow")"
        printf 'resumeFlowSha256=%s\n' "$(sha256_file "$resume_flow")"
        printf 'finishFlowSha256=%s\n' "$(sha256_file "$finish_flow")"
        [[ -z "$ticket_discovery" ]] || printf 'ticketDiscoverySha256=%s\n' "$(sha256_file "$ticket_discovery")"
        [[ -z "$attempt_progress_reader" ]] || printf 'attemptProgressReaderSha256=%s\n' "$(sha256_file "$attempt_progress_reader")"
        [[ -z "$pending_settlement_reader" ]] || printf 'pendingSettlementReaderSha256=%s\n' "$(sha256_file "$pending_settlement_reader")"
        [[ -z "$preparation_preflight_reader" ]] || printf 'preparationPreflightReaderSha256=%s\n' "$(sha256_file "$preparation_preflight_reader")"
    } > "$output_dir/$phase-leaf-manifest.env"
    chmod 600 "$output_dir/$phase-leaf-manifest.env"
}

while (($#)); do
    case "$1" in
        --phase) phase=${2-}; shift 2 ;;
        --run-id) run_id=${2-}; shift 2 ;;
        --candidate-head) candidate_head=${2-}; shift 2 ;;
        --apk) candidate_apk=${2-}; shift 2 ;;
        --run-context) run_context=${2-}; shift 2 ;;
        --selectors) selectors_file=${2-}; shift 2 ;;
        --output-dir) output_dir=${2-}; shift 2 ;;
        --serial) serial=${2-}; shift 2 ;;
        --adb) adb_bin=${2-}; shift 2 ;;
        --maestro) maestro_bin=${2-}; shift 2 ;;
        --apksigner) apksigner_bin=${2-}; shift 2 ;;
        --java-home) java_home=${2-}; shift 2 ;;
        --node) node_bin=${2-}; shift 2 ;;
        --ticket-discovery) ticket_discovery=${2-}; shift 2 ;;
        --context-reader) context_reader=${2-}; shift 2 ;;
        --attempt-progress-reader) attempt_progress_reader=${2-}; shift 2 ;;
        --pending-settlement-reader) pending_settlement_reader=${2-}; shift 2 ;;
        --preparation-preflight-reader) preparation_preflight_reader=${2-}; shift 2 ;;
        --input-lock) input_lock=${2-}; shift 2 ;;
        --input-lock-sha256) input_lock_sha256=${2-}; shift 2 ;;
        --guard-capability) guard_capability=${2-}; shift 2 ;;
        --guard-nonce) guard_nonce=${2-}; shift 2 ;;
        --recovery-record) recovery_record=${2-}; shift 2 ;;
        --identity-flow) identity_flow=${2-}; shift 2 ;;
        --prepare-flow) prepare_flow=${2-}; shift 2 ;;
        --start-flow) start_flow=${2-}; shift 2 ;;
        --resume-flow) resume_flow=${2-}; shift 2 ;;
        --finish-flow) finish_flow=${2-}; shift 2 ;;
        --help|-h) usage; exit 0 ;;
        *) fail "unknown argument: $1" ;;
    esac
done

for required in phase run_id candidate_head candidate_apk run_context selectors_file output_dir serial adb_bin maestro_bin apksigner_bin java_home node_bin context_reader input_lock input_lock_sha256 guard_capability guard_nonce recovery_record identity_flow prepare_flow start_flow resume_flow finish_flow; do
    require_value "--${required//_/-}" "${!required}"
done
[[ "$phase" == pre-cut || "$phase" == offline ]] || fail '--phase must be pre-cut or offline'
[[ "$run_id" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$ && "$candidate_head" =~ ^[a-f0-9]{40}$ ]] || fail 'invalid run ID or candidate HEAD'
[[ "$input_lock_sha256" =~ ^[a-f0-9]{64}$ ]] || fail 'input lock SHA-256 is invalid'
[[ "$serial" != *$'\n'* && "$serial" != *$'\r'* ]] || fail 'unsafe ADB serial'
[[ -x "$adb_bin" && -x "$maestro_bin" && -x "$apksigner_bin" && -x "$node_bin" && -x "$java_home/bin/java" ]] ||
    fail 'one or more required runtime tools are absent'
[[ -f "$candidate_apk" && -f "$run_context" && -f "$selectors_file" && -f "$context_reader" && -f "$input_lock" && -f "$recovery_record" ]] || fail 'one or more input files are absent'
case "$phase" in
    pre-cut) [[ -f "$ticket_discovery" && -f "$preparation_preflight_reader" ]] || fail '--ticket-discovery and --preparation-preflight-reader are required for pre-cut' ;;
    offline) [[ -f "$attempt_progress_reader" && -f "$pending_settlement_reader" ]] ||
        fail '--attempt-progress-reader and --pending-settlement-reader are required for offline' ;;
esac
require_private_directory "$output_dir"
result_path="$output_dir/$phase-leaf-result.json"
[[ ! -e "$result_path" ]] || fail "leaf result already exists: $result_path"
verify_locked_input leaf "$0"
verify_locked_input candidate-apk "$candidate_apk"
verify_locked_input run-context "$run_context"
verify_locked_input selectors "$selectors_file"
verify_locked_input context-reader "$context_reader"
verify_locked_input identity-flow "$identity_flow"
verify_locked_input prepare-flow "$prepare_flow"
verify_locked_input start-flow "$start_flow"
verify_locked_input resume-flow "$resume_flow"
verify_locked_input finish-flow "$finish_flow"
case "$phase" in
    pre-cut)
        verify_locked_input ticket-discovery "$ticket_discovery"
        verify_locked_input preparation-preflight-reader "$preparation_preflight_reader" ;;
    offline)
        verify_locked_input attempt-progress-reader "$attempt_progress_reader"
        verify_locked_input pending-settlement-reader "$pending_settlement_reader" ;;
esac
parse_key_value_file "$run_context" run
parse_key_value_file "$selectors_file" selectors
validate_run_context
validate_selectors
verify_recovery_record
verify_guard_capability
write_leaf_manifest

case "$phase" in
    pre-cut)
        assert_candidate
        capture_preparation_preflight "$output_dir/preparation-preflight.json"
        validate_preparation_preflight "$output_dir/preparation-preflight.json"
        run_maestro identity-preflight identity-flow "$identity_flow"
        # Capture immediately before the operation that creates the ticket.  A catalog refresh is
        # intentionally outside this baseline-to-prepare window, so an old or concurrent ticket
        # cannot be relabelled as the ticket created by this run.
        capture_evidence_json "$output_dir/baseline-ticket.json" ticket-discovery "$node_bin" "$ticket_discovery" baseline \
            --run-id "$run_id" --owner-uid "$E2B_OWNER_UID" --lesson-id "$E2B_LESSON_ID" \
            --lesson-version "$E2B_LESSON_VERSION" --mode SELECTED --difficulty HARD \
            --frozen-price-nolics "$E2B_EXPECTED_FROZEN_PRICE_NOLICS"
        recovery_transition PREPARE_GUARDED PREPARE_STARTED
        run_maestro selected-hard-prepare prepare-flow "$prepare_flow"
        capture_evidence_json "$output_dir/prepared-ticket.json" ticket-discovery "$node_bin" "$ticket_discovery" discover \
            --baseline-file "$output_dir/baseline-ticket.json"
        ticket_id=$(json_value "$output_dir/prepared-ticket.json" ticketId) || fail 'could not read fresh ticket ID'
        attempt_id=$(json_value "$output_dir/prepared-ticket.json" attemptId) || fail 'could not read fresh attempt ID'
        recovery_transition PREPARE_STARTED TICKET_DISCOVERED "$ticket_id" "$attempt_id" "$(sha256_file "$output_dir/prepared-ticket.json")"
        capture_evidence_json "$output_dir/prepared-server-context.json" context-reader "$node_bin" "$context_reader" \
            --run-id "$run_id" --owner-uid "$E2B_OWNER_UID" --ticket-id "$ticket_id" --attempt-id "$attempt_id"
        validate_prepared_context "$output_dir/prepared-server-context.json"
        recovery_transition TICKET_DISCOVERED PREPARED "$ticket_id" "$attempt_id" "$(sha256_file "$output_dir/prepared-server-context.json")"
        overall_status='PASS'
        next_required='guard will now stop the app, cut transport, and run the exact prepared ticket locally'
        ;;
    offline)
        assert_cut_transport
        validate_prepared_context "$output_dir/prepared-server-context.json"
        ticket_id=$(json_value "$output_dir/prepared-server-context.json" ticketId) || fail 'could not read prepared ticket ID'
        attempt_id=$(json_value "$output_dir/prepared-server-context.json" attemptId) || fail 'could not read prepared attempt ID'
        E2B_SELECTOR_TICKET_LABEL=${E2B_SELECTOR_TICKET_LABEL_TEMPLATE/'{{ticketId}}'/$ticket_id}
        run_maestro start-answer-relaunch start-flow "$start_flow"
        "$adb_bin" -s "$serial" shell am force-stop "$APP_ID"
        assert_cut_transport
        copy_room_snapshot post-relaunch attempt-progress-reader "$attempt_progress_reader" "$output_dir/post-relaunch-room-progress.json"
        run_maestro resume-saved-answer-and-finish resume-flow "$resume_flow"
        run_maestro finish finish-flow "$finish_flow"
        "$adb_bin" -s "$serial" shell am force-stop "$APP_ID"
        assert_cut_transport
        copy_room_snapshot pending-settlement pending-settlement-reader "$pending_settlement_reader" "$output_dir/pending-settlement-room.json"
        capture_evidence_json "$output_dir/server-context-under-cut.json" context-reader "$node_bin" "$context_reader" \
            --run-id "$run_id" --owner-uid "$E2B_OWNER_UID" --ticket-id "$ticket_id" --attempt-id "$attempt_id"
        validate_offline_contexts "$output_dir/post-relaunch-room-progress.json" "$output_dir/pending-settlement-room.json" \
            "$output_dir/server-context-under-cut.json"
        assert_cut_transport
        recovery_transition PREPARED OFFLINE_PENDING "$ticket_id" "$attempt_id" "$(sha256_file "$output_dir/server-context-under-cut.json")"
        overall_status='PASS'
        next_required='transport was restored by the guard; inspect sealed pending SETTLE evidence before a separate delivery'
        ;;
esac
