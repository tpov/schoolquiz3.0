#!/usr/bin/env bash
# Волна параллельных headless-сессий ZCode по темам предмета.
#   ./run-wave.sh <audit|author> <subject> [-j N] [--all | <s>-<t> ...]
# Примеры:
#   ./run-wave.sh audit chemistry -j 6 --all          # 28 тем, по 6 одновременно
#   ./run-wave.sh author history -j 4 1-1 1-2 1-3     # три темы
# Логи: scripts/seed-bulk/run/<subject>/<mode>-<s>-<t>.log ; сводка гейтов в конце.
set -u
ZCODE="${ZCODE:-/Applications/ZCode.app/Contents/Resources/glm/zcode.cjs}"
HERE="$(cd "$(dirname "$0")" && pwd)"; REPO="$(cd "$HERE/../.." && pwd)"
MODE="${1:-}"; SUBJ="${2:-}"; shift 2 2>/dev/null || { sed -n '2,7p' "$0"; exit 2; }
JOBS=4; THEMES=()
while [ $# -gt 0 ]; do case "$1" in
  -j) JOBS="$2"; shift 2;;
  --all) for s in 1 2 3 4 5 6 7; do for t in 1 2 3 4; do THEMES+=("$s-$t"); done; done; shift;;
  *) THEMES+=("$1"); shift;;
esac; done
[ "$MODE" = audit ] || [ "$MODE" = author ] || { echo "mode: audit|author"; exit 2; }
[ ${#THEMES[@]} -gt 0 ] || { echo "укажи темы или --all"; exit 2; }
LOGDIR="$HERE/run/$SUBJ"; mkdir -p "$LOGDIR"
echo "[$MODE] $SUBJ: ${#THEMES[@]} тем, параллельно $JOBS → логи $LOGDIR"

run_one() {
  theme="$1"; log="$LOGDIR/$MODE-$theme.log"
  if [ "$MODE" = audit ]; then prompt="/quest:audit $SUBJ $theme"
  else prompt="Загрузи скилл quest-author и напиши недостающие уроки темы $theme предмета $SUBJ (файлы data/school/$SUBJ/lessons/$theme-1.js … -5.js). Готовые уроки с 40 вопросами не трогать. Закончи гейтом: node gate-lesson.js $SUBJ $theme → GATE: CLEAN."; fi
  start=$(date +%s); attempt=0; gate=""
  # до 3 попыток: сессия могла упасть по сети/лимиту (429) — повторяем с паузой, только если гейт не чист
  while [ $attempt -lt "${RETRIES:-3}" ]; do
    attempt=$((attempt+1))
    node "$ZCODE" --prompt "$prompt" --cwd "$REPO" --mode yolo >> "$log" 2>&1; rc=$?
    gate=$(cd "$HERE" && node gate-lesson.js "$SUBJ" "$theme" 2>/dev/null | tail -1)
    case "$gate" in *CLEAN*) break;; esac
    echo "--- попытка $attempt: rc=$rc, гейт: $gate — пауза ${RETRY_PAUSE:-120}s ---" >> "$log"; sleep "${RETRY_PAUSE:-120}"
  done
  dur=$(( $(date +%s) - start ))
  printf "%-6s rc=%s try=%s %5ss  %s\n" "$theme" "$rc" "$attempt" "$dur" "$gate"
}
export -f run_one; export MODE SUBJ LOGDIR ZCODE REPO HERE
printf "%s\n" "${THEMES[@]}" | xargs -P "$JOBS" -I{} bash -c 'run_one "$@"' _ {} | tee "$LOGDIR/$MODE-summary.txt"
echo "--- сводка: $(grep -c 'GATE: CLEAN' "$LOGDIR/$MODE-summary.txt") чистых из ${#THEMES[@]} ---"
