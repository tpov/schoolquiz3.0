#!/bin/bash
# PostToolUse hook: проверяет syntax + sourcing для всего scripts/qa_lib/.
# Нужен потому что scripts/qa собирается из source-цепочки и одна битая lib ломает весь entrypoint.
set -u

INPUT="$(cat /dev/stdin)"
FILE_PATH="$(
  python3 - <<'PY' "$INPUT"
import json
import sys

raw = sys.argv[1]
try:
    payload = json.loads(raw) if raw.strip() else {}
except Exception:
    payload = {}

tool_input = payload.get("tool_input", {}) if isinstance(payload, dict) else {}
if not isinstance(tool_input, dict):
    tool_input = {}

path = tool_input.get("file_path") or tool_input.get("filePath") or ""
print(path)
PY
)"

if [ -z "$FILE_PATH" ]; then
  exit 0
fi

case "$FILE_PATH" in
  */scripts/qa_lib/*.sh|scripts/qa_lib/*.sh)
    ;;
  *)
    exit 0
    ;;
esac

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"
cd "$PROJECT_DIR" || exit 0

TMPOUT=$(mktemp)
if ! bash -n "$FILE_PATH" > "$TMPOUT" 2>&1; then
  echo "SYNTAX CHECK FAILED after editing $FILE_PATH" >&2
  cat "$TMPOUT" >&2
  echo "Fix the error before continuing." >&2
  rm -f "$TMPOUT"
  exit 2
fi

bash -lc '
  set -euo pipefail
  source scripts/qa_lib/common.sh
  source scripts/qa_lib/tui.sh
  source scripts/qa_lib/profile.sh
  source scripts/qa_lib/matrix.sh
  source scripts/qa_lib/reporting.sh
  source scripts/qa_lib/accounts.sh
  source scripts/qa_lib/auth.sh
  source scripts/qa_lib/adb.sh
  source scripts/qa_lib/backend.sh
  source scripts/qa_lib/gradle.sh
  source scripts/qa_lib/pipeline.sh
  echo SOURCING_OK
' > "$TMPOUT" 2>&1
EXIT_CODE=$?

# Проверяем: exit code != 0 ИЛИ в output есть syntax error ИЛИ нет SOURCING_OK
if [ $EXIT_CODE -ne 0 ] || grep -qi "syntax error\|unexpected token\|unbound variable\|command not found" "$TMPOUT" || ! grep -q "SOURCING_OK" "$TMPOUT"; then
  echo "SOURCING FAILED after editing $FILE_PATH" >&2
  cat "$TMPOUT" >&2
  echo "Fix the error before continuing." >&2
  rm -f "$TMPOUT"
  exit 2
fi

rm -f "$TMPOUT"
exit 0
