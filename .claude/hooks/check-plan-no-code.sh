#!/bin/bash
# PostToolUse hook: блокирует сохранение plan-файлов с ```kotlin блоками.
# Обоснование: `feature-plan.md:188` = "Только phase files, никакого кода".
# Text rules LLM рационализирует (см. CLAUDE.md philosophy строка 3: "Deterministic enforcement > hope").
# Plan = ТЗ (Signature Card формат per planner.md), domain/data/*.kt = код.
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

# Scope: только docs/features/*/plan/**/*.md. Остальные .md (spec, design, research) — не трогаем.
case "$FILE_PATH" in
  */docs/features/*/plan/*/*.md|docs/features/*/plan/*/*.md)
    ;;
  *)
    exit 0
    ;;
esac

# Exempt filenames: overview.md, README.md — допустимы ```bash (validation commands) и ```markdown (templates).
# Но ```kotlin/java/kt/groovy fenced code — blocker в ЛЮБОМ plan-файле.

if [ ! -f "$FILE_PATH" ]; then
  exit 0
fi

# Ищем fenced code blocks языков-нарушителей. Signature в inline backticks (`class Foo`) разрешена.
VIOLATIONS=$(grep -nE '^[[:space:]]*```(kotlin|kt|java|groovy)\b' "$FILE_PATH" || true)

if [ -n "$VIOLATIONS" ]; then
  echo "PLAN CONTAINS READY-TO-COPY CODE: $FILE_PATH" >&2
  echo "" >&2
  echo "Nарушено правило feature-plan.md:188 (\"Только phase files, никакого кода\") и planner.md (\"Signature Card формат, не полные классы\")." >&2
  echo "" >&2
  echo "Fenced code blocks найдены в строках:" >&2
  echo "$VIOLATIONS" >&2
  echo "" >&2
  echo "Правила:" >&2
  echo "  - Plan = ТЗ (Signature Card: файл + сигнатура в inline backticks + поведение + edge cases + canonical reference)." >&2
  echo "  - Готовый Kotlin/Java/Groovy код живёт в domain/data/*.kt, не в plan/*.md." >&2
  echo "  - Для описания сигнатуры используй inline backticks: \`class Foo : Bar\` (одна строка)." >&2
  echo "  - Для convention plugin / internal helper допустима краткая inline-сигнатура в Signature Card, но НЕ полный класс." >&2
  echo "  - Canonical signatures живут в 06-api-contract.md — plan ссылается, не копирует." >&2
  echo "" >&2
  echo "См. .claude/agents/planner.md (Signature Card template) и .claude/skills/adversarial-review/references/plan-review-lens.md." >&2
  exit 2
fi

exit 0
