#!/usr/bin/env bash
#
# Creates one sealed, two-leaf Arena proof: an online selected/HARD preparation followed by
# a confirmed backend cut and a local pending SETTLE.  It never sends that SETTLE afterwards.
# Delivery remains a deliberate later phase, after the operator has inspected this handoff.
set -Eeuo pipefail
umask 077

readonly RUNNER_VERSION='arena-offline-settlement-runner-v2'
readonly APP_ID='com.tpov.schoolquiz.mvp'

run_id=''
candidate_head=''
candidate_apk=''
run_context=''
selectors_file=''
output_root=''
serial=''
control_channel=''
guard=''
leaf=''
ticket_discovery=''
context_reader=''
attempt_progress_reader=''
pending_settlement_reader=''
preparation_preflight_reader=''
adb_bin="${ADB_BIN:-adb}"
maestro_bin="${MAESTRO_BIN:-maestro}"
apksigner_bin="${APKSIGNER_BIN:-apksigner}"
java_home="${JAVA_HOME:-}"
node_bin="${NODE_BIN:-node}"
flock_bin="${FLOCK_BIN:-flock}"
lock_root="${E2B_DEVICE_LOCK_ROOT:-/tools/arena-e2b-device-locks}"
pre_cut_timeout_ms=600000
offline_timeout_ms=600000
output_dir=''
input_lock=''
input_lock_sha256=''
recovery_dir=''
recovery_record=''
identity_flow=''
prepare_flow=''
start_flow=''
resume_flow=''
finish_flow=''
overall_status='INTERRUPTED'
phase_status='INTERRUPTED'
next_required='inspect the private offline-settlement artifacts before retrying'

usage() {
    cat <<'USAGE'
Usage:
  run-arena-offline-settlement.sh \
    --run-id <safe-id> --candidate-head <40-hex> --apk <candidate.apk> \
    --run-context <immutable.env> --selectors <confirmed-selectors.env> \
    --output-root <private-results> --serial <adb-serial> \
    --control-channel adb-independent \
    --guard <offline-transport-guard.js> --leaf <offline-settlement-leaf.sh> \
    --ticket-discovery <arena-e2e-ticket-discovery.js> \
    --context-reader <arena-e2e-context-reader.js> \
    --attempt-progress-reader <arena-e2e-attempt-progress-reader.js> \
    --pending-settlement-reader <arena-e2e-pending-settlement-reader.js> \
    --preparation-preflight-reader <arena-e2e-preparation-preflight-reader.js> \
    --adb <path> --maestro <path> --apksigner <path> --java-home <path> --node <path> \
    [--flock <path>] [--lock-root <private-lock-root>] \
    [--pre-cut-timeout-ms <1..3600000>] [--offline-timeout-ms <1..3600000>]

The runner creates a fresh selected/HARD ticket and performs its attempt only while the app has
no backend route.  It stops after proving a local pending SETTLE and an unchanged server ticket.
It intentionally never invokes synchronize, rate, install, clear, uninstall, cancel, or
reverse --remove-all.  A passed handoff is a matrix-row result, not overall MVP acceptance.
USAGE
}

fail() {
    overall_status='FAIL'
    phase_status='FAIL'
    next_required='inspect the private guard and leaf artifacts; do not retry preparation on an unknown ticket state'
    printf 'Arena offline-settlement runner: %s\n' "$*" >&2
    exit 2
}

not_run() {
    overall_status='NOT_RUN'
    phase_status='NOT_RUN'
    next_required=$1
    printf 'Arena offline-settlement runner: %s\n' "$1" >&2
    exit 0
}

on_error() {
    local exit_code=$?
    if [[ "$overall_status" == 'INTERRUPTED' ]]; then
        overall_status='FAIL'
        phase_status='FAIL'
    fi
    exit "$exit_code"
}

write_result() {
    local exit_code=$?
    trap - EXIT
    if [[ -n "$output_dir" && -d "$output_dir" ]]; then
        "$node_bin" - "$output_dir/result.json" "$overall_status" "$phase_status" "$run_id" "$next_required" <<'NODE' || true
const fs = require('node:fs');
const [destination, status, phaseStatus, runId, nextRequired] = process.argv.slice(2);
fs.writeFileSync(destination, JSON.stringify({
  status, phaseStatus, phase: 'offline-settle', runId, nextRequired,
  runnerVersion: 'arena-offline-settlement-runner-v2', resultArtifactVersion: 1,
}, null, 2) + '\n', {mode: 0o600});
NODE
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

require_regular_file() {
    if ! "$node_bin" - "$1" "$2" <<'NODE'
const fs = require('node:fs');
const [file, name] = process.argv.slice(2);
let stat;
try { stat = fs.lstatSync(file); } catch { process.exit(2); }
if (!stat.isFile() || stat.isSymbolicLink()) process.exit(2);
NODE
    then
        fail "$2 must be a regular non-symlink file: $1"
    fi
}

require_private_directory() {
    if ! "$node_bin" - "$1" "$2" <<'NODE'
const fs = require('node:fs');
const path = require('node:path');
const [directory] = process.argv.slice(2);
try {
  const requested = path.resolve(directory);
  const stat = fs.lstatSync(requested);
  const canonical = fs.realpathSync(requested);
  if (!stat.isDirectory() || stat.isSymbolicLink() || (stat.mode & 0o077) !== 0) process.exit(2);
  for (let current = canonical;; current = path.dirname(current)) {
    const currentStat = fs.lstatSync(current);
    if (currentStat.isSymbolicLink()) process.exit(2);
    if (path.dirname(current) === current) break;
  }
} catch { process.exit(2); }
NODE
    then
        fail "$2 must be a private non-symlink directory: $1"
    fi
}

parse_positive_timeout() {
    [[ "$1" =~ ^[1-9][0-9]*$ ]] || fail "$2 must be a positive integer"
    (( $1 <= 3600000 )) || fail "$2 exceeds the supported maximum"
}

create_input_lock() {
    local destination=$1
    shift
    if "$node_bin" - "$destination" "$@" <<'NODE'
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const [destination, ...pairs] = process.argv.slice(2);
if (pairs.length === 0 || pairs.length % 2 !== 0) process.exit(2);
const hash = (file) => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const inputs = {};
try {
  for (let i = 0; i < pairs.length; i += 2) {
    const [key, source] = [pairs[i], pairs[i + 1]];
    if (!/^[a-z][a-z0-9-]*$/.test(key) || Object.hasOwn(inputs, key)) process.exit(2);
    const stat = fs.lstatSync(source);
    if (!stat.isFile() || stat.isSymbolicLink()) process.exit(2);
    inputs[key] = {path: path.resolve(source), sha256: hash(source)};
  }
  fs.writeFileSync(destination, JSON.stringify({artifactVersion: 1, inputs}, null, 2) + '\n', {mode: 0o600, flag: 'wx'});
} catch (_) { process.exit(2); }
NODE
    then
        return 0
    fi
    fail 'could not create the sealed input lock'
}

create_recovery_record() {
    local owner_sha=$1
    if "$node_bin" - "$recovery_record" "$run_id" "$candidate_head" "$owner_sha" "$input_lock_sha256" "$output_dir" <<'NODE'
const fs = require('node:fs');
const [destination, runId, candidateHead, ownerUidSha256, inputLockSha256, outputDir] = process.argv.slice(2);
try {
  fs.writeFileSync(destination, JSON.stringify({
    artifactVersion: 1, runId, candidateHead, ownerUidSha256, inputLockSha256, outputDir,
    status: 'PREPARE_GUARDED', createdAtMs: Date.now(), updatedAtMs: Date.now(),
  }, null, 2) + '\n', {mode: 0o600, flag: 'wx'});
} catch (error) { process.exit(error?.code === 'EEXIST' ? 3 : 2); }
NODE
    then
        return 0
    else
        local status=$?
        [[ $status -eq 3 ]] && not_run 'this retained owner already has a recovery record; use that exact record instead of preparing again'
        fail 'could not create the one-time recovery record'
    fi
}

verify_input_lock_entry() {
    local key=$1 source=$2
    if ! "$node_bin" - "$input_lock" "$input_lock_sha256" "$key" "$source" <<'NODE'
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const [lockPath, expectedLockHash, key, source] = process.argv.slice(2);
try {
  const hash = (file) => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
  const lockStat = fs.lstatSync(lockPath);
  if (!lockStat.isFile() || lockStat.isSymbolicLink() || hash(lockPath) !== expectedLockHash) process.exit(2);
  const lock = JSON.parse(fs.readFileSync(lockPath, 'utf8'));
  const item = lock?.inputs?.[key];
  const sourceStat = fs.lstatSync(source);
  if (!item || path.resolve(item.path) !== path.resolve(source) || !/^[a-f0-9]{64}$/.test(item.sha256) ||
      !sourceStat.isFile() || sourceStat.isSymbolicLink() || hash(source) !== item.sha256) process.exit(2);
} catch (_) { process.exit(2); }
NODE
    then
        fail "sealed input mismatch for $key"
    fi
}

validate_and_hash_run_context() {
    "$node_bin" - "$run_context" "$run_id" "$candidate_head" <<'NODE'
const crypto = require('node:crypto');
const fs = require('node:fs');
const [file, expectedRunId, expectedHead] = process.argv.slice(2);
const required = [
  'E2B_RUN_ID', 'E2B_CANDIDATE_HEAD', 'E2B_APP_ID', 'E2B_APK_SHA256', 'E2B_APK_CERT_SHA256',
  'E2B_ACCOUNT_REF', 'E2B_OWNER_UID', 'E2B_LESSON_ID', 'E2B_LESSON_VERSION',
  'E2B_EXPECTED_FROZEN_PRICE_NOLICS', 'E2B_EXPECTED_MODE', 'E2B_EXPECTED_DIFFICULTY', 'E2B_RATING',
];
try {
  const values = new Map();
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    if (!line || line.startsWith('#')) continue;
    const match = /^([A-Z][A-Z0-9_]*)=(.*)$/.exec(line);
    if (!match || !match[1].startsWith('E2B_') || values.has(match[1]) || /[\u0000-\u001f\u007f]/.test(match[2])) process.exit(2);
    values.set(match[1], match[2]);
  }
  if (required.some((key) => !values.has(key)) || values.get('E2B_RUN_ID') !== expectedRunId ||
      values.get('E2B_CANDIDATE_HEAD') !== expectedHead || values.get('E2B_APP_ID') !== 'com.tpov.schoolquiz.mvp' ||
      !/^[a-f0-9]{64}$/.test(values.get('E2B_APK_SHA256')) || !/^[a-f0-9]{64}$/.test(values.get('E2B_APK_CERT_SHA256')) ||
      !/^[1-9][0-9]*$/.test(values.get('E2B_LESSON_VERSION')) || !/^[1-9][0-9]*$/.test(values.get('E2B_EXPECTED_FROZEN_PRICE_NOLICS')) ||
      Number(values.get('E2B_EXPECTED_FROZEN_PRICE_NOLICS')) > 100 || values.get('E2B_EXPECTED_MODE') !== 'SELECTED' ||
      values.get('E2B_EXPECTED_DIFFICULTY') !== 'HARD' || values.get('E2B_RATING') !== '2') process.exit(2);
  const owner = values.get('E2B_OWNER_UID');
  if (!owner || owner.includes('/') || owner === '.' || owner === '..' || owner.length > 256 ||
      !values.get('E2B_ACCOUNT_REF') || values.get('E2B_ACCOUNT_REF').length > 256) process.exit(2);
  process.stdout.write(crypto.createHash('sha256').update(owner).digest('hex'));
} catch (_) { process.exit(2); }
NODE
}

while (($#)); do
    case "$1" in
        --run-id) run_id=${2-}; shift 2 ;;
        --candidate-head) candidate_head=${2-}; shift 2 ;;
        --apk) candidate_apk=${2-}; shift 2 ;;
        --run-context) run_context=${2-}; shift 2 ;;
        --selectors) selectors_file=${2-}; shift 2 ;;
        --output-root) output_root=${2-}; shift 2 ;;
        --serial) serial=${2-}; shift 2 ;;
        --control-channel) control_channel=${2-}; shift 2 ;;
        --guard) guard=${2-}; shift 2 ;;
        --leaf) leaf=${2-}; shift 2 ;;
        --ticket-discovery) ticket_discovery=${2-}; shift 2 ;;
        --context-reader) context_reader=${2-}; shift 2 ;;
        --attempt-progress-reader) attempt_progress_reader=${2-}; shift 2 ;;
        --pending-settlement-reader) pending_settlement_reader=${2-}; shift 2 ;;
        --preparation-preflight-reader) preparation_preflight_reader=${2-}; shift 2 ;;
        --adb) adb_bin=${2-}; shift 2 ;;
        --maestro) maestro_bin=${2-}; shift 2 ;;
        --apksigner) apksigner_bin=${2-}; shift 2 ;;
        --java-home) java_home=${2-}; shift 2 ;;
        --node) node_bin=${2-}; shift 2 ;;
        --flock) flock_bin=${2-}; shift 2 ;;
        --lock-root) lock_root=${2-}; shift 2 ;;
        --pre-cut-timeout-ms) pre_cut_timeout_ms=${2-}; shift 2 ;;
        --offline-timeout-ms) offline_timeout_ms=${2-}; shift 2 ;;
        --help|-h) usage; exit 0 ;;
        *) fail "unknown argument: $1" ;;
    esac
done

require_value --run-id "$run_id"
require_value --candidate-head "$candidate_head"
require_value --apk "$candidate_apk"
require_value --run-context "$run_context"
require_value --selectors "$selectors_file"
require_value --output-root "$output_root"
require_value --serial "$serial"
require_value --control-channel "$control_channel"
require_value --guard "$guard"
require_value --leaf "$leaf"
require_value --ticket-discovery "$ticket_discovery"
require_value --context-reader "$context_reader"
require_value --attempt-progress-reader "$attempt_progress_reader"
require_value --pending-settlement-reader "$pending_settlement_reader"
require_value --preparation-preflight-reader "$preparation_preflight_reader"
require_value --adb "$adb_bin"
require_value --maestro "$maestro_bin"
require_value --apksigner "$apksigner_bin"
require_value --java-home "$java_home"
require_value --node "$node_bin"
require_value --flock "$flock_bin"
require_value --lock-root "$lock_root"

[[ "$run_id" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$ ]] || fail 'run ID must be a safe 3..64 character identifier'
[[ "$candidate_head" =~ ^[a-f0-9]{40}$ ]] || fail 'candidate HEAD must be lowercase 40-hex'
[[ "$serial" != *$'\n'* && "$serial" != *$'\r'* ]] || fail 'ADB serial is unsafe'
[[ "$control_channel" == 'adb-independent' ]] || fail 'an adb-independent control channel must be explicitly acknowledged'
[[ "$lock_root" = /* && "$output_root" = /* ]] || fail 'output and lock roots must be absolute'
parse_positive_timeout "$pre_cut_timeout_ms" --pre-cut-timeout-ms
parse_positive_timeout "$offline_timeout_ms" --offline-timeout-ms

for file_and_name in \
    "$candidate_apk:APK" "$run_context:run-context" "$selectors_file:selectors" "$guard:guard" "$leaf:leaf" \
    "$ticket_discovery:ticket-discovery" "$context_reader:context-reader" \
    "$attempt_progress_reader:attempt-progress-reader" "$pending_settlement_reader:pending-settlement-reader" \
    "$preparation_preflight_reader:preparation-preflight-reader"; do
    file=${file_and_name%%:*}
    name=${file_and_name#*:}
    require_regular_file "$file" "$name"
done
flow_root=$(cd -- "$(dirname -- "$leaf")/../arena-e2b/flows" 2>/dev/null && pwd -P) ||
    fail 'could not resolve the sealed Arena flow directory next to the leaf'
identity_flow="$flow_root/00-identity-preflight.yaml"
prepare_flow="$flow_root/01-selected-hard-prepare.yaml"
start_flow="$flow_root/02-start-answer-relaunch.yaml"
resume_flow="$flow_root/02-resume-saved-answer-and-finish.yaml"
finish_flow="$flow_root/03-finish.yaml"
for file_and_name in \
    "$identity_flow:identity-flow" "$prepare_flow:prepare-flow" "$start_flow:start-flow" \
    "$resume_flow:resume-flow" "$finish_flow:finish-flow"; do
    file=${file_and_name%%:*}
    name=${file_and_name#*:}
    require_regular_file "$file" "$name"
done
[[ -x "$adb_bin" && -x "$maestro_bin" && -x "$apksigner_bin" && -x "$node_bin" && -x "$flock_bin" && -x "$leaf" ]] ||
    fail 'one or more required executables are absent'
[[ -x "$java_home/bin/java" ]] || fail '--java-home must contain an executable java'

owner_uid_sha=$(validate_and_hash_run_context) || fail 'run context is not a complete immutable SELECTED/HARD candidate binding'

mkdir -p -- "$output_root" || fail 'could not create output root'
chmod 700 "$output_root" || fail 'could not secure output root'
require_private_directory "$output_root" 'output root'
recovery_dir="$output_root/offline-settle-recovery"
mkdir -m 700 -- "$recovery_dir" 2>/dev/null || true
chmod 700 "$recovery_dir" || fail 'could not secure offline-settlement recovery directory'
require_private_directory "$recovery_dir" 'offline-settlement recovery directory'
output_dir=$(mktemp -d "$output_root/${run_id}.offline-settle.$(date -u +%Y%m%dT%H%M%SZ).XXXXXX") || fail 'could not create private output directory'
chmod 700 "$output_dir"
input_lock="$output_dir/input-lock.json"
create_input_lock "$input_lock" \
    candidate-apk "$candidate_apk" run-context "$run_context" selectors "$selectors_file" guard "$guard" leaf "$leaf" \
    ticket-discovery "$ticket_discovery" context-reader "$context_reader" attempt-progress-reader "$attempt_progress_reader" \
    pending-settlement-reader "$pending_settlement_reader" preparation-preflight-reader "$preparation_preflight_reader" \
    identity-flow "$identity_flow" prepare-flow "$prepare_flow" start-flow "$start_flow" resume-flow "$resume_flow" finish-flow "$finish_flow"
input_lock_sha256=$(sha256_file "$input_lock")
recovery_record="$recovery_dir/$owner_uid_sha.json"
create_recovery_record "$owner_uid_sha"

pre_cut_args=(
    "$leaf" --phase pre-cut --run-id "$run_id" --candidate-head "$candidate_head" --apk "$candidate_apk"
    --run-context "$run_context" --selectors "$selectors_file" --output-dir "$output_dir" --serial "$serial"
    --adb "$adb_bin" --maestro "$maestro_bin" --apksigner "$apksigner_bin" --java-home "$java_home" --node "$node_bin"
    --ticket-discovery "$ticket_discovery" --context-reader "$context_reader" --preparation-preflight-reader "$preparation_preflight_reader"
    --input-lock "$input_lock" --input-lock-sha256 "$input_lock_sha256" --recovery-record "$recovery_record"
    --identity-flow "$identity_flow" --prepare-flow "$prepare_flow" --start-flow "$start_flow" --resume-flow "$resume_flow" --finish-flow "$finish_flow"
)
offline_args=(
    "$leaf" --phase offline --run-id "$run_id" --candidate-head "$candidate_head" --apk "$candidate_apk"
    --run-context "$run_context" --selectors "$selectors_file" --output-dir "$output_dir" --serial "$serial"
    --adb "$adb_bin" --maestro "$maestro_bin" --apksigner "$apksigner_bin" --java-home "$java_home" --node "$node_bin"
    --context-reader "$context_reader" --attempt-progress-reader "$attempt_progress_reader"
    --pending-settlement-reader "$pending_settlement_reader"
    --input-lock "$input_lock" --input-lock-sha256 "$input_lock_sha256" --recovery-record "$recovery_record"
    --identity-flow "$identity_flow" --prepare-flow "$prepare_flow" --start-flow "$start_flow" --resume-flow "$resume_flow" --finish-flow "$finish_flow"
)
pre_cut_json=$("$node_bin" -e 'process.stdout.write(JSON.stringify(process.argv.slice(1)))' "${pre_cut_args[@]}")
offline_json=$("$node_bin" -e 'process.stdout.write(JSON.stringify(process.argv.slice(1)))' "${offline_args[@]}")
sequence_plan="$output_dir/sequence-plan.json"
"$node_bin" - "$sequence_plan" "$pre_cut_timeout_ms" "$offline_timeout_ms" "$pre_cut_json" "$offline_json" <<'NODE'
const fs = require('node:fs');
const [destination, preCutTimeoutMs, offlineTimeoutMs, preCutJson, offlineJson] = process.argv.slice(2);
const payload = {
  artifactVersion: 1,
  preCut: {argv: JSON.parse(preCutJson), timeoutMs: Number(preCutTimeoutMs)},
  whileCut: {argv: JSON.parse(offlineJson), timeoutMs: Number(offlineTimeoutMs)},
};
fs.writeFileSync(destination, JSON.stringify(payload) + '\n', {mode: 0o600, flag: 'wx'});
NODE

{
    printf 'runnerVersion=%s\n' "$RUNNER_VERSION"
    printf 'runId=%s\n' "$run_id"
    printf 'candidateHead=%s\n' "$candidate_head"
    printf 'candidateApkSha256=%s\n' "$(sha256_file "$candidate_apk")"
    printf 'runContextSha256=%s\n' "$(sha256_file "$run_context")"
    printf 'selectorMapSha256=%s\n' "$(sha256_file "$selectors_file")"
    printf 'guardSha256=%s\n' "$(sha256_file "$guard")"
    printf 'leafSha256=%s\n' "$(sha256_file "$leaf")"
    printf 'sequencePlanSha256=%s\n' "$(sha256_file "$sequence_plan")"
    printf 'ticketDiscoverySha256=%s\n' "$(sha256_file "$ticket_discovery")"
    printf 'contextReaderSha256=%s\n' "$(sha256_file "$context_reader")"
    printf 'attemptProgressReaderSha256=%s\n' "$(sha256_file "$attempt_progress_reader")"
    printf 'pendingSettlementReaderSha256=%s\n' "$(sha256_file "$pending_settlement_reader")"
    printf 'preparationPreflightReaderSha256=%s\n' "$(sha256_file "$preparation_preflight_reader")"
    printf 'identityFlowSha256=%s\n' "$(sha256_file "$identity_flow")"
    printf 'prepareFlowSha256=%s\n' "$(sha256_file "$prepare_flow")"
    printf 'startFlowSha256=%s\n' "$(sha256_file "$start_flow")"
    printf 'resumeFlowSha256=%s\n' "$(sha256_file "$resume_flow")"
    printf 'finishFlowSha256=%s\n' "$(sha256_file "$finish_flow")"
    printf 'inputLockSha256=%s\n' "$input_lock_sha256"
    printf 'recoveryRecordSha256=%s\n' "$(sha256_file "$recovery_record")"
    printf 'serialSha256=%s\n' "$(printf '%s' "$serial" | sha256sum | awk '{print $1}')"
} > "$output_dir/run-manifest.env"
chmod 600 "$output_dir/run-manifest.env"

guard_result="$output_dir/transport-guard-result.json"
verify_input_lock_entry guard "$guard"
if "$node_bin" "$guard" \
    --serial "$serial" --adb "$adb_bin" --app-id "$APP_ID" --control-channel "$control_channel" \
    --output "$guard_result" --lock-root "$lock_root" --flock "$flock_bin" \
    --self-sha256 "$(sha256_file "$guard")" --sequence-plan "$sequence_plan"; then
    guard_exit=0
else
    guard_exit=$?
fi

[[ -f "$guard_result" ]] || fail 'transport guard did not persist a result artifact'
guard_status=$("$node_bin" - "$guard_result" <<'NODE'
const fs = require('node:fs');
const value = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const accepted = ['PASS', 'FAIL', 'NOT_RUN', 'INTERRUPTED'];
if (!accepted.includes(value.status)) process.exit(2);
process.stdout.write(value.status);
NODE
) || fail 'transport guard result is malformed'
case "$guard_status" in
    NOT_RUN) not_run 'the selected device is already owned by another guarded phase; no preparation occurred' ;;
    PASS) ;;
    INTERRUPTED) overall_status='INTERRUPTED'; phase_status='INTERRUPTED'; next_required='inspect the retained ticket and private guard artifacts before any retry'; exit 130 ;;
    *) fail "transport guard failed (exit $guard_exit); inspect its private result before retrying" ;;
esac

"$node_bin" - \
    "$guard_result" "$output_dir/pre-cut-leaf-result.json" "$output_dir/offline-leaf-result.json" \
    "$output_dir/preparation-preflight.json" "$output_dir/baseline-ticket.json" "$output_dir/prepared-ticket.json" "$output_dir/prepared-server-context.json" \
    "$output_dir/post-relaunch-room-progress.json" "$output_dir/pending-settlement-room.json" "$output_dir/server-context-under-cut.json" <<'NODE'
const fs = require('node:fs');
const [guardFile, ...files] = process.argv.slice(2);
const guard = JSON.parse(fs.readFileSync(guardFile, 'utf8'));
if (guard.status !== 'PASS' || guard.preflight?.passed !== true || guard.preCut?.requested !== true ||
    guard.preCut?.completed !== true || guard.preCutBaseline?.rechecked !== true ||
    guard.cut?.confirmed !== true || guard.offlineIntegrity?.verifiedAfterChild !== true ||
    guard.restore?.confirmed !== true || guard.deviceLock?.acquired !== true ||
    guard.deviceLock?.heldThroughChild !== true || guard.deviceLock?.released !== true ||
    guard.sequenceCapabilities?.preCutIssued !== true || guard.sequenceCapabilities?.offlineIssued !== true ||
    guard.application?.stoppedBeforePreCut !== true || guard.child?.started !== true ||
    guard.child?.exitCode !== 0 || guard.child?.processGroupClean !== true || guard.preCut?.processGroupClean !== true) process.exit(2);
for (const file of files) {
  const value = JSON.parse(fs.readFileSync(file, 'utf8'));
  if (value.status !== 'PASS' && value.status !== 'CONTEXT' && value.status !== 'BASELINE' && value.status !== 'CANDIDATE') process.exit(2);
}
NODE
[[ $? -eq 0 ]] || fail 'guard or sealed leaf evidence does not satisfy the offline-settlement contract'

"$node_bin" - "$output_dir/offline-settlement-handoff.json" "$run_id" \
    "$guard_result" "$output_dir/pre-cut-leaf-result.json" "$output_dir/offline-leaf-result.json" \
    "$output_dir/preparation-preflight.json" "$output_dir/baseline-ticket.json" "$output_dir/prepared-ticket.json" "$output_dir/prepared-server-context.json" \
    "$output_dir/post-relaunch-room-progress.json" "$output_dir/pending-settlement-room.json" "$output_dir/server-context-under-cut.json" <<'NODE'
const crypto = require('node:crypto');
const fs = require('node:fs');
const [destination, runId, ...inputs] = process.argv.slice(2);
const hash = (file) => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
fs.writeFileSync(destination, JSON.stringify({
  status: 'PASS', phase: 'offline-settle', overallStatus: 'NOT_RUN', runId,
  handoffVersion: 1,
  artifacts: Object.fromEntries(inputs.map((file) => [require('node:path').basename(file), hash(file)])),
  nextRequired: 'inspect this sealed handoff, then deliberately deliver the exact pending SETTLE after transport restoration',
}, null, 2) + '\n', {mode: 0o600, flag: 'wx'});
NODE

overall_status='PASS'
phase_status='PASS'
next_required='offline SETTLE is proven pending; inspect the sealed handoff before a separate deliberate delivery phase'
