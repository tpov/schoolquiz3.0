#!/usr/bin/env bash
# Safe device-only Arena matrix check: Home -> catalog -> hardware Back -> Arena -> hardware Back -> Home.
set -Eeuo pipefail
umask 077

readonly APP_ID='com.tpov.schoolquiz.mvp'
readonly LOCK_ROOT="${MATRIX_DEVICE_LOCK_ROOT:-/tools/arena-e2b-device-locks}"
readonly RUNNER_VERSION='arena-system-back-catalog-v2'

run_id=''
candidate_head=''
candidate_apk=''
candidate_apk_sha256=''
candidate_cert_sha256=''
selectors_file=''
output_root=''
serial=''
adb_bin="${ADB_BIN:-adb}"
maestro_bin="${MAESTRO_BIN:-maestro}"
apksigner_bin="${APKSIGNER_BIN:-apksigner}"
java_home="${JAVA_HOME:-}"
node_bin="${NODE_BIN:-node}"
output_dir=''
device_lock_held=false
installed_base_apk_sha256=''
overall_status='INTERRUPTED'
phase_status='INTERRUPTED'
next_required='inspect retained device state and saved evidence before retrying'

selector_seen_keys=$'\n'
readonly SELECTOR_KEYS=(
    MATRIX_SELECTOR_HOME MATRIX_SELECTOR_INTERNET MATRIX_SELECTOR_OPEN_MENU MATRIX_SELECTOR_ARENA_NAV
    MATRIX_SELECTOR_ARENA_TITLE MATRIX_SELECTOR_OPEN_CATALOG MATRIX_SELECTOR_CATALOG_PROMPT
    MATRIX_SELECTOR_LOAD_LESSONS MATRIX_SELECTOR_LOADED_CATALOG MATRIX_SELECTOR_ARENA_ROOT_PREVIEW
    MATRIX_SELECTOR_ARENA_RESERVATIONS MATRIX_SELECTOR_PREPARE MATRIX_SELECTOR_CONFIRM_PREPARATION
    MATRIX_SELECTOR_CONFIRM_CANCELLATION MATRIX_SELECTOR_TICKET_LABEL MATRIX_SELECTOR_TICKET_STATE
    MATRIX_SELECTOR_START MATRIX_SELECTOR_OPEN_SAVED MATRIX_SELECTOR_SYNCHRONIZE MATRIX_SELECTOR_RATING_PROMPT
    MATRIX_SELECTOR_RUNNER_ACTIVE MATRIX_SELECTOR_RECEIPT MATRIX_SELECTOR_RATING
)

usage() {
    cat <<'USAGE'
Usage:
  run-system-back-catalog.sh --run-id <safe-id> --candidate-head <40-hex> \
    --apk <installed.apk> --apk-sha256 <64-hex> --apk-cert-sha256 <64-hex> \
    --selectors <selectors.env> --output-root <private-results> --serial <adb-serial> \
    [--adb <path>] [--maestro <path>] [--apksigner <path>] [--java-home <path>] [--node <path>]

This command never installs, clears, uninstalls, prepares, starts, cancels, settles, or rates.
USAGE
}

fail() {
    overall_status='FAIL'
    phase_status='FAIL'
    next_required='inspect the failing phase evidence; do not send another Back until the retained state is understood'
    printf 'Arena system-Back runner: %s\n' "$*" >&2
    exit 2
}

not_run() {
    overall_status='NOT_RUN'
    phase_status='NOT_RUN'
    next_required=$1
    printf 'Arena system-Back runner: %s\n' "$1" >&2
    exit 0
}

release_device_lock() {
    if [[ "$device_lock_held" == true ]]; then
        flock -u 9 || true
        exec 9>&- || true
        device_lock_held=false
    fi
}

write_result() {
    local exit_code=$?
    trap - EXIT
    release_device_lock
    if [[ -n "$output_dir" && -d "$output_dir" ]]; then
        "$node_bin" - "$output_dir/result.json" "$overall_status" "$phase_status" "$run_id" "$next_required" <<'NODE' || true
const fs = require('node:fs');
const [destination, status, phaseStatus, runId, nextRequired] = process.argv.slice(2);
fs.writeFileSync(destination, JSON.stringify({
  status, phaseStatus, phase: 'system-back-catalog', runId, nextRequired,
  resultArtifactVersion: 1,
}, null, 2) + '\n', {mode: 0o600});
NODE
    fi
    exit "$exit_code"
}

on_error() {
    local exit_code=$?
    if [[ "$overall_status" != 'FAIL' && "$overall_status" != 'NOT_RUN' ]]; then
        overall_status='FAIL'
        phase_status='FAIL'
        next_required='inspect the failing phase evidence; do not send another Back until the retained state is understood'
    fi
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

parse_selectors() {
    local line key value line_number=0
    while IFS= read -r line || [[ -n "$line" ]]; do
        ((++line_number))
        [[ -z "$line" || "$line" == \#* ]] && continue
        [[ "$line" =~ ^([A-Z][A-Z0-9_]*)=(.*)$ ]] || fail "$selectors_file:$line_number is not KEY=value"
        key=${BASH_REMATCH[1]}
        value=${BASH_REMATCH[2]}
        [[ "$key" == MATRIX_SELECTOR_* ]] || fail "$selectors_file:$line_number has an unsupported key"
        [[ "$selector_seen_keys" != *$'\n'"$key"$'\n'* ]] || fail "$selectors_file:$line_number duplicates $key"
        [[ -n "$value" && "$value" != *$'\n'* && "$value" != *$'\r'* ]] || fail "$selectors_file:$line_number has an invalid value"
        selector_seen_keys+="$key"$'\n'
        printf -v "$key" '%s' "$value"
    done < "$selectors_file"
    local key
    for key in "${SELECTOR_KEYS[@]}"; do
        [[ "$selector_seen_keys" == *$'\n'"$key"$'\n'* ]] || fail "$key is missing from selectors"
    done
}

acquire_device_lock() {
    [[ -n "$serial" && "$serial" != *$'\n'* && "$serial" != *$'\r'* ]] || fail 'invalid ADB serial'
    [[ "$LOCK_ROOT" = /* && "$LOCK_ROOT" != *$'\n'* && "$LOCK_ROOT" != *$'\r'* ]] || fail 'invalid device lock root'
    mkdir -p -- "$LOCK_ROOT" || fail 'could not create device lock root'
    chmod 700 "$LOCK_ROOT" || fail 'could not secure device lock root'
    local serial_sha
    serial_sha=$(printf '%s' "$serial" | sha256sum | awk '{print $1}') || fail 'could not derive device lock name'
    exec 9>>"$LOCK_ROOT/$serial_sha.lock" || fail 'could not open device lock'
    chmod 600 "$LOCK_ROOT/$serial_sha.lock" || fail 'could not secure device lock'
    if ! flock -n 9; then
        exec 9>&-
        not_run 'another device phase owns this serial; wait for it to finish before retrying'
    fi
    device_lock_held=true
}

assert_candidate() {
    "$adb_bin" -s "$serial" get-state | grep -qx device || fail 'ADB target is not ready'
    "$adb_bin" -s "$serial" shell pm path "$APP_ID" | grep -q '^package:' || fail 'retained application is not installed'
    [[ "$(sha256_file "$candidate_apk")" == "$candidate_apk_sha256" ]] || fail 'APK SHA-256 differs from supplied candidate identity'
    [[ -n "$java_home" && -x "$java_home/bin/java" ]] || fail 'valid --java-home is required for certificate verification'
    local certificate
    certificate=$(JAVA_HOME="$java_home" PATH="$java_home/bin:$PATH" "$apksigner_bin" verify --print-certs "$candidate_apk") ||
        fail 'candidate APK signature is invalid'
    certificate=$(printf '%s\n' "$certificate" | awk -F': ' '/Signer #1 certificate SHA-256 digest:/ {print tolower($2); exit}')
    [[ "$certificate" == "$candidate_cert_sha256" ]] || fail 'APK certificate differs from supplied candidate identity'

    local package_paths
    package_paths=$("$adb_bin" -s "$serial" shell pm path --user 0 "$APP_ID") ||
        fail 'could not read the installed package path'
    local -a installed_apks=()
    mapfile -t installed_apks < <(printf '%s\n' "$package_paths" | tr -d '\r' | sed -n 's/^package://p')
    [[ "${#installed_apks[@]}" -eq 1 && "${installed_apks[0]}" == /data/app/*/base.apk ]] ||
        fail 'installed package is split or has an unexpected base path'
    installed_base_apk_sha256=$("$adb_bin" -s "$serial" shell sha256sum "${installed_apks[0]}" |
        tr -d '\r' | awk 'NR == 1 { print tolower($1); exit }') ||
        fail 'could not hash the installed base APK'
    [[ "$installed_base_apk_sha256" =~ ^[a-f0-9]{64}$ ]] || fail 'installed base APK hash is malformed'
    [[ "$installed_base_apk_sha256" == "$candidate_apk_sha256" ]] ||
        fail 'installed base APK differs from the supplied candidate'
}

write_manifest() {
    {
        printf 'runnerVersion=%s\n' "$RUNNER_VERSION"
        printf 'runId=%s\n' "$run_id"
        printf 'candidateHead=%s\n' "$candidate_head"
        printf 'candidateApkSha256=%s\n' "$candidate_apk_sha256"
        printf 'candidateCertSha256=%s\n' "$candidate_cert_sha256"
        printf 'installedBaseApkSha256=%s\n' "$installed_base_apk_sha256"
        printf 'installedArtifactLayout=base-only\n'
        printf 'selectorMapSha256=%s\n' "$(sha256_file "$selectors_file")"
        printf 'runnerSha256=%s\n' "$(sha256_file "$0")"
        printf 'appId=%s\n' "$APP_ID"
        printf 'serialSha256=%s\n' "$(printf '%s' "$serial" | sha256sum | awk '{print $1}')"
    } > "$output_dir/run-manifest.env"
    chmod 600 "$output_dir/run-manifest.env"
}

run_maestro() {
    local name=$1 flow=$2
    local flow_output="$output_dir/maestro-$name"
    mkdir -p "$flow_output"
    local -a args=(test -e "MATRIX_RUN_ID=$run_id")
    local key
    for key in "${SELECTOR_KEYS[@]}"; do args+=(-e "$key=${!key}"); done
    args+=(--test-output-dir "$flow_output" "$flow")
    "$maestro_bin" --device "$serial" --no-ansi "${args[@]}" 2>&1 | tee "$output_dir/maestro-$name.log"
}

capture_surface() {
    local name=$1
    "$maestro_bin" --device "$serial" hierarchy --no-reinstall-driver > "$output_dir/$name-hierarchy.json"
    "$adb_bin" -s "$serial" exec-out screencap -p > "$output_dir/$name-screen.png"
}

assess_surface() {
    local name=$1 expected=$2 hierarchy="$output_dir/$name-hierarchy.json"
    "$node_bin" - "$hierarchy" "$output_dir/$name-safety.json" "$expected" \
        "${MATRIX_SELECTOR_HOME}" "${MATRIX_SELECTOR_ARENA_NAV}" \
        "${MATRIX_SELECTOR_ARENA_TITLE}" "${MATRIX_SELECTOR_OPEN_CATALOG}" \
        "${MATRIX_SELECTOR_CATALOG_PROMPT}" "${MATRIX_SELECTOR_LOAD_LESSONS}" \
        "${MATRIX_SELECTOR_LOADED_CATALOG}" "${MATRIX_SELECTOR_ARENA_ROOT_PREVIEW}" \
        "${MATRIX_SELECTOR_ARENA_RESERVATIONS}" "${MATRIX_SELECTOR_PREPARE}" \
        "${MATRIX_SELECTOR_CONFIRM_PREPARATION}" "${MATRIX_SELECTOR_CONFIRM_CANCELLATION}" \
        "${MATRIX_SELECTOR_TICKET_LABEL}" "${MATRIX_SELECTOR_TICKET_STATE}" \
        "${MATRIX_SELECTOR_START}" "${MATRIX_SELECTOR_OPEN_SAVED}" \
        "${MATRIX_SELECTOR_SYNCHRONIZE}" "${MATRIX_SELECTOR_RATING_PROMPT}" \
        "${MATRIX_SELECTOR_RUNNER_ACTIVE}" "${MATRIX_SELECTOR_RECEIPT}" \
        "${MATRIX_SELECTOR_RATING}" <<'NODE'
const fs = require('node:fs');
const [source, destination, expected, ...values] = process.argv.slice(2);
const root = JSON.parse(fs.readFileSync(source, 'utf8'));
const selectorNames = [
  'home', 'arenaNav', 'arenaTitle', 'openCatalog', 'catalogPrompt', 'loadLessons',
  'loadedCatalog', 'arenaRootPreview', 'arenaReservations', 'prepare', 'confirmPreparation',
  'confirmCancellation', 'ticketLabel', 'ticketState', 'start', 'openSaved', 'synchronize',
  'ratingPrompt', 'runnerActive', 'receipt', 'rating',
];
if (values.length !== selectorNames.length) throw new Error('selector argument count mismatch');
const selectors = Object.fromEntries(selectorNames.map((name, index) => [name, new RegExp(values[index])]));
const observed = [];
const walk = (value) => {
  if (Array.isArray(value)) return value.forEach(walk);
  if (!value || typeof value !== 'object') return;
  const attributes = value.attributes;
  if (attributes && typeof attributes === 'object') {
    for (const field of ['text', 'accessibilityText', 'hintText']) {
      if (typeof attributes[field] === 'string' && attributes[field]) observed.push(attributes[field]);
    }
  }
  for (const child of Object.values(value)) walk(child);
};
walk(root);
const has = (name) => observed.some((value) => selectors[name].test(value));
const unsafeNames = [
  'loadedCatalog', 'prepare', 'confirmPreparation', 'confirmCancellation', 'ticketLabel', 'ticketState',
  'start', 'openSaved', 'synchronize', 'ratingPrompt', 'runnerActive', 'receipt', 'rating',
];
const unsafe = unsafeNames.filter(has);
const catalog = has('arenaTitle') && has('catalogPrompt') && has('loadLessons') && has('arenaReservations');
const arenaRoot = has('arenaTitle') && has('arenaRootPreview') && has('openCatalog') && !has('catalogPrompt') &&
  !has('loadLessons') && !has('arenaReservations');
const home = has('home') && !has('arenaTitle') && !has('catalogPrompt') && !has('arenaReservations') && !has('arenaNav');
const state = catalog ? 'catalog' : arenaRoot ? 'arena-root' : home ? 'home' : 'unknown';
const expectedState = expected === 'home' ? home : expected === 'catalog' ? catalog : expected === 'arena-root' ? arenaRoot : false;
const safe = expectedState && unsafe.length === 0;
fs.writeFileSync(destination, JSON.stringify({
  status: safe ? 'CONTEXT' : 'NOT_RUN', expectedState: expected, observedState: state,
  unsafeScreenMarkers: unsafe, observedKnownState: state !== 'unknown',
  observedTextFieldCount: observed.length, artifactVersion: 2,
}, null, 2) + '\n', {mode: 0o600});
process.exitCode = safe ? 0 : 3;
NODE
}

while (($#)); do
    case "$1" in
        --run-id) run_id=${2-}; shift 2 ;;
        --candidate-head) candidate_head=${2-}; shift 2 ;;
        --apk) candidate_apk=${2-}; shift 2 ;;
        --apk-sha256) candidate_apk_sha256=${2-}; shift 2 ;;
        --apk-cert-sha256) candidate_cert_sha256=${2-}; shift 2 ;;
        --selectors) selectors_file=${2-}; shift 2 ;;
        --output-root) output_root=${2-}; shift 2 ;;
        --serial) serial=${2-}; shift 2 ;;
        --adb) adb_bin=${2-}; shift 2 ;;
        --maestro) maestro_bin=${2-}; shift 2 ;;
        --apksigner) apksigner_bin=${2-}; shift 2 ;;
        --java-home) java_home=${2-}; shift 2 ;;
        --node) node_bin=${2-}; shift 2 ;;
        --help|-h) usage; exit 0 ;;
        *) fail "unknown argument: $1" ;;
    esac
done

require_value --run-id "$run_id"
require_value --candidate-head "$candidate_head"
require_value --apk "$candidate_apk"
require_value --apk-sha256 "$candidate_apk_sha256"
require_value --apk-cert-sha256 "$candidate_cert_sha256"
require_value --selectors "$selectors_file"
require_value --output-root "$output_root"
require_value --serial "$serial"
[[ "$run_id" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$ ]] || fail 'run ID must be a safe 3..64 character identifier'
[[ "$candidate_head" =~ ^[a-f0-9]{40}$ ]] || fail 'candidate HEAD must be lowercase 40-hex'
[[ "$candidate_apk_sha256" =~ ^[a-f0-9]{64}$ && "$candidate_cert_sha256" =~ ^[a-f0-9]{64}$ ]] || fail 'candidate hashes must be lowercase SHA-256'
[[ -f "$candidate_apk" && -f "$selectors_file" && -x "$apksigner_bin" && -x "$maestro_bin" && -x "$adb_bin" ]] || fail 'one or more required executable/input files are absent'
mkdir -p -- "$output_root" || fail 'could not create output root'
output_dir=$(mktemp -d "$output_root/${run_id}.$(date -u +%Y%m%dT%H%M%SZ).XXXXXX")
chmod 700 "$output_dir"
parse_selectors
acquire_device_lock
assert_candidate
write_manifest

run_maestro launch "$(dirname "$0")/flows/00-launch-retained.yaml"
capture_surface initial
if ! assess_surface initial home; then
    not_run 'retained application was not on a safe Home surface; no navigation or Back was sent'
fi
run_maestro reach-idle-catalog "$(dirname "$0")/flows/01-reach-idle-catalog.yaml"
capture_surface catalog-before-back
if ! assess_surface catalog-before-back catalog; then
    fail 'the navigation-only setup did not reach an unloaded safe catalog; no Back was sent'
fi
printf 'KEYCODE_BACK\n' > "$output_dir/first-system-back.action"
"$adb_bin" -s "$serial" shell input keyevent KEYCODE_BACK
run_maestro assert-arena-root "$(dirname "$0")/flows/02-assert-arena-root.yaml"
capture_surface arena-root-after-back
if ! assess_surface arena-root-after-back arena-root; then
    fail 'first system Back did not reach the verified Arena root'
fi
printf 'KEYCODE_BACK\n' > "$output_dir/second-system-back.action"
"$adb_bin" -s "$serial" shell input keyevent KEYCODE_BACK
run_maestro assert-home "$(dirname "$0")/flows/03-assert-home.yaml"
capture_surface home-after-back
if ! assess_surface home-after-back home; then
    fail 'second system Back did not reach verified Home'
fi

overall_status='PASS'
phase_status='PASS'
next_required='none'
