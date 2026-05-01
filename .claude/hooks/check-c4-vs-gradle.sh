#!/bin/bash
# PostToolUse hook: warn если C4 module graph в 01-architecture.md содержит arrows,
# которые могут не соответствовать реальным Gradle dependencies.
# Обоснование: lesson-runner Bug #2 (C4 показывает quizzes-screen → lesson-runner,
# Gradle build.gradle.kts:11-17 не имеет dep). Manual SSoT drift.
#
# Hook не блокирует (exit 0), но печатает список arrows для lead-а — чтобы он
# verified соответствие против build.gradle.kts. Полный mechanical check требует
# mapping diagram-node → gradle-path, что специфично для проекта; warning делает
# проблему видимой, не invisible.
#
# Scope: docs/features/*/01-architecture.md.
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
  */docs/features/*/01-architecture.md|docs/features/*/01-architecture.md)
    ;;
  *)
    exit 0
    ;;
esac

if [ ! -f "$FILE_PATH" ]; then
  exit 0
fi

REPO_ROOT="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"

# Extract module graph arrows из mermaid блоков. Поддерживает синтаксис:
#   A --> B
#   A -.-> B
#   A -- "label" --> B
#   A == B (component diagram)

RESULT=$(python3 - <<'PY' "$FILE_PATH" "$REPO_ROOT"
import os
import re
import sys
import subprocess

file_path = sys.argv[1]
repo_root = sys.argv[2]

try:
    with open(file_path, encoding='utf-8') as f:
        content = f.read()
except Exception as e:
    print(f"READ_ERROR:{e}")
    sys.exit(0)

# Find mermaid blocks.
mermaid_blocks = re.findall(r'```mermaid\n(.*?)```', content, re.DOTALL)
if not mermaid_blocks:
    print("NO_MERMAID")
    sys.exit(0)

# Arrow patterns: A --> B, A -.-> B, A -- "label" --> B.
arrow_re = re.compile(
    r'^\s*([\w-]+)\s*(?:\["[^"]*"\])?\s*-{1,2}\.?-{1,2}>\s*(?:\|[^|]+\|\s*)?([\w-]+)\s*(?:\["[^"]*"\])?\s*$',
    re.MULTILINE
)

arrows = set()

def is_module_node(name):
    """Module nodes — kebab-case (lesson-runner) or simple (auth, sync). Class/type nodes
    are PascalCase (DefaultComponent) or contain underscores (RunnerUiState_Question).
    Skip them — C4 L3 may include class-level nodes which не check against Gradle."""
    if not name:
        return False
    # PascalCase: starts with uppercase letter, contains lowercase letter inside.
    if name[0].isupper() and any(c.islower() for c in name):
        return False
    # Contains underscore — likely class/type name in C4 L3 (RunnerUiState_Question).
    if '_' in name:
        return False
    # All-caps short labels (like LD, LRD, QSP) — abbreviations for diagram, not modules.
    if name.isupper() and len(name) <= 4:
        return False
    return True

for block in mermaid_blocks:
    for m in arrow_re.finditer(block):
        a = m.group(1)
        b = m.group(2)
        # Skip generic Mermaid keywords.
        if a.lower() in ('graph', 'flowchart', 'tb', 'lr', 'rl', 'bt', 'subgraph', 'end', 'classdef', 'class', 'click'):
            continue
        if b.lower() in ('graph', 'flowchart', 'tb', 'lr', 'rl', 'bt', 'subgraph', 'end', 'classdef', 'class', 'click'):
            continue
        # Skip class-level nodes (PascalCase, abbreviations) — only module-level arrows checked.
        if not is_module_node(a) or not is_module_node(b):
            continue
        arrows.add((a, b))

if not arrows:
    print("NO_ARROWS")
    sys.exit(0)

# Heuristic: name → path candidates.
def name_to_paths(name):
    # Try common project layout patterns.
    candidates = []
    n = name.lower().replace('_', '-')
    # android/feature/<name>/presentation
    candidates.append(f"android/feature/{n}/presentation")
    candidates.append(f"android/feature/{n}")
    # shared/feature/<name>/{domain,data}
    candidates.append(f"shared/feature/{n}/domain")
    candidates.append(f"shared/feature/{n}/data")
    candidates.append(f"shared/feature/{n}")
    # shared/core/<name>
    candidates.append(f"shared/core/{n}")
    candidates.append(f"android/core/{n}")
    # platform/<name>
    candidates.append(f"platform/{n}")
    # apps/<name>
    candidates.append(f"apps/{n}")
    return candidates

# Поиск build.gradle.kts для каждого узла.
def find_module_path(name):
    for candidate in name_to_paths(name):
        full = os.path.join(repo_root, candidate, 'build.gradle.kts')
        if os.path.isfile(full):
            return candidate
    return None

unverified = []
verified = []
unknown_nodes = set()

for a, b in arrows:
    a_path = find_module_path(a)
    b_path = find_module_path(b)
    if not a_path:
        unknown_nodes.add(a)
        continue
    if not b_path:
        unknown_nodes.add(b)
        continue
    # Check that A's build.gradle.kts mentions B's path.
    a_gradle = os.path.join(repo_root, a_path, 'build.gradle.kts')
    try:
        with open(a_gradle, encoding='utf-8') as f:
            gradle_content = f.read()
    except Exception:
        unverified.append(f"{a} → {b}: cannot read {a_gradle}")
        continue
    # Build Gradle project notation: project(":path:to:module") — path uses ':'.
    b_gradle_ref = ':' + b_path.replace('/', ':')
    if b_gradle_ref in gradle_content or b_path in gradle_content:
        verified.append(f"{a} → {b}: OK (matches {a_path}/build.gradle.kts)")
    else:
        unverified.append(f"{a} → {b}: arrow declared, but '{b_gradle_ref}' not found in {a_path}/build.gradle.kts")

# Output report.
if unverified or unknown_nodes:
    print("WARNINGS")
    if unverified:
        print("UNVERIFIED ARROWS (likely C4 vs Gradle drift):")
        for u in unverified:
            print(f"  {u}")
    if unknown_nodes:
        print("UNKNOWN NODES (cannot map to module path):")
        for n in sorted(unknown_nodes):
            print(f"  {n}")
else:
    print(f"OK ({len(verified)} arrows verified)")
PY
)

# Hook не блокирует — только warns (exit 0). Lead должен ответить на warning manually.
if echo "$RESULT" | head -n 1 | grep -q "WARNINGS"; then
  echo "C4 ARCHITECTURE DIAGRAM ↔ GRADLE DEPENDENCIES MISMATCH (WARNING): $FILE_PATH" >&2
  echo "" >&2
  echo "$RESULT" | tail -n +2 >&2
  echo "" >&2
  echo "Action: Verify each unverified arrow:" >&2
  echo "  1. Open источника модуля's build.gradle.kts" >&2
  echo "  2. Confirm dependency declared (e.g. 'implementation(project(\":shared:feature:foo:domain\"))')" >&2
  echo "  3. If arrow is intentional but not yet implemented — note in ADR." >&2
  echo "  4. If diagram drift — fix diagram or fix Gradle." >&2
  echo "" >&2
  echo "Source rationale: lesson-runner retrospective Bug #2 (\`docs/features/lesson-runner/retrospective.md\`)." >&2
  echo "" >&2
  echo "(Note: hook не блокирует save — это warning. Полный grep audit делает design-phase reviewer.)" >&2
fi

exit 0
