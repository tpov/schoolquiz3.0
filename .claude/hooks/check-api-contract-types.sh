#!/bin/bash
# PostToolUse hook: проверяет что types упомянутые в 06-api-contract.md существуют в Walking Skeleton domain.
# Обоснование: lesson-runner Bug #4 — canonical API snippets `06-api-contract.md:447-450` ссылаются
# на param `lessonAttemptRepository`, real `CompleteAttemptUseCase.kt:22-26` имеет `attemptRepository`.
# Manual SSoT drift; Codex поймал post-impl, должно ловиться deterministically.
#
# Scope: docs/features/*/06-api-contract.md.
# Стратегия: extract class/interface/data class names из ```kotlin блоков 06-api-contract.md,
# проверить что они существуют в shared/feature/<slug>/domain/ или android/feature/<slug>/.
#
# Ограничения hook:
# - Не делает full kotlinc compile (требует Gradle setup, медленно).
# - Проверяет только class-level existence: если 06-api-contract.md объявляет `class FooUseCase`,
#   ищет `class FooUseCase` в source. Param-name drift не ловится hook'ом — это работа Codex/architect-reviewer.
# - Игнорирует types из standard lib (Result, Flow, StateFlow, etc.).
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
  */docs/features/*/06-api-contract.md|docs/features/*/06-api-contract.md)
    ;;
  *)
    exit 0
    ;;
esac

if [ ! -f "$FILE_PATH" ]; then
  exit 0
fi

REPO_ROOT="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"

# Extract feature slug из path.
SLUG=$(echo "$FILE_PATH" | sed -nE 's|.*/docs/features/([^/]+)/06-api-contract\.md|\1|p')
if [ -z "$SLUG" ]; then
  exit 0
fi

RESULT=$(python3 - <<'PY' "$FILE_PATH" "$REPO_ROOT" "$SLUG"
import os
import re
import sys
import subprocess

file_path = sys.argv[1]
repo_root = sys.argv[2]
slug = sys.argv[3]

try:
    with open(file_path, encoding='utf-8') as f:
        content = f.read()
except Exception as e:
    print(f"READ_ERROR:{e}")
    sys.exit(0)

# Extract Kotlin code blocks.
code_blocks = re.findall(r'```(?:kotlin|kt)\n(.*?)```', content, re.DOTALL)
if not code_blocks:
    print("NO_KOTLIN_BLOCKS")
    sys.exit(0)

# Extract class/interface/data class/object/sealed declarations.
decl_re = re.compile(
    r'^\s*(?:public\s+|internal\s+|private\s+)?(?:abstract\s+|sealed\s+|data\s+|enum\s+|open\s+|annotation\s+|value\s+|inline\s+)?(class|interface|object)\s+([A-Z]\w+)',
    re.MULTILINE
)

# Standard library / common types — ignore.
STDLIB_TYPES = {
    'Result', 'Flow', 'StateFlow', 'SharedFlow', 'List', 'Set', 'Map', 'String',
    'Int', 'Long', 'Boolean', 'Float', 'Double', 'Unit', 'Any', 'Nothing',
    'Throwable', 'Exception', 'Result', 'Optional', 'Pair', 'Triple', 'Channel',
    'CoroutineScope', 'Job', 'Deferred', 'Mutex', 'Continuation',
    'Instant', 'Duration', 'LocalDate', 'LocalDateTime', 'LocalTime', 'TimeZone',
    'Clock', 'ComponentContext', 'Lifecycle', 'BackHandler', 'Value', 'StackNavigation',
}

declared_types = []
for block in code_blocks:
    for m in decl_re.finditer(block):
        kind = m.group(1)
        name = m.group(2)
        if name in STDLIB_TYPES:
            continue
        declared_types.append((kind, name))

if not declared_types:
    print("NO_DECLARATIONS")
    sys.exit(0)

# Search для каждого declared type в источниках.
# Цель: ловить SSoT drift — type декларирован в contract но не существует в коде.
# Cross-feature search OK: контракт может описывать типы из других feature-модулей,
# которые предоставляют эту фичу (e.g. lesson-runner contract → quizzes-screen Component).
# Если type не существует НИГДЕ в проекте — это drift.
search_paths = [
    f"shared/feature/{slug}",
    f"android/feature/{slug}",
    "shared/feature",
    "android/feature",
    "shared/core",
    "android/core",
    "platform",
    "apps/android-next",
]

# Filter: только existing dirs.
existing_search_paths = [p for p in search_paths if os.path.isdir(os.path.join(repo_root, p))]

if not existing_search_paths:
    print(f"WARN: no search paths exist for slug '{slug}'")
    sys.exit(0)

missing = []
for kind, name in declared_types:
    # Build search command.
    pattern = f'(class|interface|object)\\s+{re.escape(name)}\\b'
    found = False
    for sp in existing_search_paths:
        full_sp = os.path.join(repo_root, sp)
        try:
            result = subprocess.run(
                ['grep', '-rqE', pattern, '--include=*.kt', full_sp],
                capture_output=True, timeout=5
            )
            if result.returncode == 0:
                found = True
                break
        except (subprocess.TimeoutExpired, FileNotFoundError):
            continue
    if not found:
        missing.append((kind, name))

if missing:
    print("MISSING")
    for kind, name in missing:
        print(f"  {kind} {name} — declared in 06-api-contract.md but not found in source")
else:
    print(f"OK ({len(declared_types)} types verified)")
PY
)

# Hook warns если types declared в contract но не существуют в source.
# Не блокирует: некоторые types могут быть planned (forthcoming phase), не yet existing.
# Lead/architect-reviewer должен решить blocker vs planned.
if echo "$RESULT" | head -n 1 | grep -q "MISSING"; then
  echo "API CONTRACT TYPE NOT FOUND IN SOURCE (WARNING): $FILE_PATH" >&2
  echo "" >&2
  echo "Following types declared in 06-api-contract.md but не found в source code:" >&2
  echo "" >&2
  echo "$RESULT" | tail -n +2 >&2
  echo "" >&2
  echo "Action options:" >&2
  echo "  - Если type ещё не реализован (planned для upcoming phase) — verified, OK." >&2
  echo "  - Если type должен существовать (Walking Skeleton либо already-implemented phase) — это SSoT drift." >&2
  echo "    Source code = ground truth; contract должен match. Fix contract либо implement type." >&2
  echo "" >&2
  echo "Source rationale: lesson-runner retrospective Bug #4 (\`docs/features/lesson-runner/retrospective.md\`)." >&2
  echo "" >&2
  echo "(Note: hook не делает full compile-check — он catches class-level existence только." >&2
  echo "Param-name drift, signature semantics, etc. — work для design-phase architect-reviewer и Codex review.)" >&2
fi

exit 0
