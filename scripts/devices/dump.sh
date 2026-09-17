#!/usr/bin/env bash
# Безопасный дамп дерева элементов: жёсткий предел по времени, чтобы зависший
# uiautomator на устройстве не подвешивал прогон (инцидент на планшете T775H).
# Использование: dump.sh <serial> [секунд]  -> XML в stdout, код 124 при таймауте.
set -uo pipefail
ADB=/opt/homebrew/bin/adb
SERIAL="${1:?нужен serial}"
LIMIT="${2:-20}"
OUT=$(mktemp)
( "$ADB" -s "$SERIAL" exec-out uiautomator dump /dev/tty 2>/dev/null > "$OUT" ) &
PID=$!
for _ in $(seq 1 "$LIMIT"); do
  kill -0 "$PID" 2>/dev/null || break
  sleep 1
done
if kill -0 "$PID" 2>/dev/null; then
  kill -9 "$PID" 2>/dev/null
  "$ADB" -s "$SERIAL" shell pkill -9 uiautomator 2>/dev/null
  rm -f "$OUT"
  echo "ТАЙМАУТ: uiautomator не ответил за ${LIMIT}с на $SERIAL" >&2
  exit 124
fi
wait "$PID" 2>/dev/null
cat "$OUT"; rm -f "$OUT"
