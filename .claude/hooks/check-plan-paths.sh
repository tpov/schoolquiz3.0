#!/bin/bash
# PostToolUse hook: проверяет что file paths упомянутые в plan-файлах действительно существуют.
# Обоснование: menu-refactor retrospective Bug #7 — planner писал AppShellTransitions.kt в неверной директории;
# home-and-my-quests / lesson-runner — плановые paths не verified. Text rule в planner.md ignored.
# CLAUDE.md philosophy строка 3: "Deterministic enforcement > hope".
# Scope: docs/features/*/plan/**/*.md.
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

# Scope: только docs/features/*/plan/**/*.md.
case "$FILE_PATH" in
  */docs/features/*/plan/*/*.md|docs/features/*/plan/*/*.md)
    ;;
  *)
    exit 0
    ;;
esac

if [ ! -f "$FILE_PATH" ]; then
  exit 0
fi

REPO_ROOT="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"

# Extract paths из секций "New Files", "Modified Files", "Deleted Files".
# Каждая секция начинается с заголовка и заканчивается следующим заголовком или EOF.
# Внутри секций ищем строки с paths (в backticks или plain).

VIOLATIONS=""

# Use python для парсинга markdown секций — bash regex для multi-section state machine хрупкий.
RESULT=$(python3 - <<'PY' "$FILE_PATH" "$REPO_ROOT"
import os
import re
import sys

file_path = sys.argv[1]
repo_root = sys.argv[2]

try:
    with open(file_path, encoding='utf-8') as f:
        content = f.read()
except Exception as e:
    print(f"READ_ERROR:{e}")
    sys.exit(0)

# Разбиваем на секции по заголовкам ## или ###.
section_re = re.compile(r'^#{2,4}\s+(.+?)\s*$', re.MULTILINE)
matches = list(section_re.finditer(content))

# Известные project root prefixes (paths которые надо проверять).
project_prefixes = (
    'android/', 'shared/', 'apps/', 'platform/', 'docs/', '.claude/',
    'gradle/', 'scripts/', 'tests/'
)
project_files = ('build.gradle.kts', 'settings.gradle.kts', 'libs.versions.toml',
                 'gradle.properties', 'AndroidManifest.xml')

# Path regex — между backticks или standalone.
path_in_backticks = re.compile(r'`([^`\s]+\.[a-zA-Z0-9.]+(?:/[^`\s]+)*)`')
path_in_backticks_dir = re.compile(r'`([a-zA-Z][a-zA-Z0-9_./-]+/)`')
path_standalone = re.compile(
    r'(?:^|\s)([a-zA-Z][a-zA-Z0-9_-]*(?:/[a-zA-Z0-9_.-]+)+\.(?:kt|kts|md|json|sh|py|xml|toml|properties|yaml|yml|gradle))(?:[\s:,;]|$)',
    re.MULTILINE
)

def extract_paths(text):
    found = set()
    for m in path_in_backticks.finditer(text):
        found.add(m.group(1))
    for m in path_in_backticks_dir.finditer(text):
        found.add(m.group(1))
    for m in path_standalone.finditer(text):
        found.add(m.group(1))
    # Filter: только project paths.
    result = []
    for p in found:
        # Skip URLs, anchors, fragments.
        if '://' in p or p.startswith('#'):
            continue
        # Skip fenced lang markers.
        if p in ('kotlin', 'kt', 'java', 'bash', 'sh', 'markdown', 'yaml', 'json'):
            continue
        # Filter: должен начинаться с project prefix или быть project file.
        is_project = any(p.startswith(prefix) for prefix in project_prefixes)
        is_project_file = p in project_files or p.endswith(tuple('/' + f for f in project_files))
        if not (is_project or is_project_file):
            continue
        # Skip patterns с wildcards / glob / placeholders.
        if '*' in p or '?' in p or '<' in p:
            continue
        # Skip Markdown elision placeholders (e.g. "shared/.../foo.kt"). Plan authors используют
        # "..." как shorthand для namespace; реальный path with full namespace проверять
        # детерминированно нельзя без mapping. Architect-reviewer проверяет полные paths вручную.
        if '...' in p:
            continue
        result.append(p)
    return result

issues = []
for i, m in enumerate(matches):
    title = m.group(1).strip()
    title_lower = title.lower()
    # Определяем тип секции.
    is_new = 'new file' in title_lower
    is_modified = 'modified file' in title_lower or 'changed file' in title_lower or 'updated file' in title_lower
    is_deleted = 'deleted file' in title_lower or 'removed file' in title_lower
    if not (is_new or is_modified or is_deleted):
        continue
    # Body секции — от конца текущего заголовка до начала следующего.
    body_start = m.end()
    body_end = matches[i + 1].start() if i + 1 < len(matches) else len(content)
    body = content[body_start:body_end]
    paths = extract_paths(body)
    for p in paths:
        full = os.path.join(repo_root, p)
        if is_modified or is_deleted:
            # Файл должен существовать.
            if not os.path.exists(full):
                issues.append(f"{title} → '{p}' MUST exist ({full}) — file not found")
        elif is_new:
            # Parent dir должен существовать.
            parent = os.path.dirname(full)
            if not os.path.isdir(parent):
                issues.append(f"{title} → '{p}' parent dir MUST exist ({parent}) — directory not found")

if issues:
    print("VIOLATIONS")
    for i in issues:
        print(i)
else:
    print("OK")
PY
)

if echo "$RESULT" | head -n 1 | grep -q "VIOLATIONS"; then
  echo "PLAN PATHS VERIFICATION FAILED: $FILE_PATH" >&2
  echo "" >&2
  echo "Plan-файл ссылается на paths, которые не существуют на файловой системе:" >&2
  echo "" >&2
  echo "$RESULT" | tail -n +2 >&2
  echo "" >&2
  echo "Правила:" >&2
  echo "  - 'Modified Files' / 'Deleted Files': указанные пути ОБЯЗАНЫ существовать в репозитории." >&2
  echo "  - 'New Files': parent directory указанного path ОБЯЗАН существовать (новый файл создаётся внутри)." >&2
  echo "  - Если parent dir тоже новый — phase plan должен включать его создание (как часть scaffold-фазы)." >&2
  echo "" >&2
  echo "Source rationale: menu-refactor retrospective Bug #7 (\`docs/features/menu-refactor/retrospective.md\` Fix #3)." >&2
  echo "Принцип: plan не описывает несуществующую реальность; деректории/файлы — verified до fix-фазы реализации." >&2
  exit 2
fi

exit 0
