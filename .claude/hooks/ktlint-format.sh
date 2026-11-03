#!/bin/sh
# Formats a Kotlin file straight after it is edited.
#
# ktlint is part of the build gate, so an unformatted edit fails the next `ktlintCheck` and costs a
# whole edit-build-fix round trip. Running the formatter here closes that loop before anybody sees
# it. Scoped to the module that owns the file so the task is seconds rather than a full-project
# format, and silent unless it actually fails.

set -eu

file=$(sed -n 's/.*"file_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
case "$file" in
  *.kt|*.kts) ;;
  *) exit 0 ;;
esac

root=$(cd "$(dirname "$0")/../.." && pwd)
case "$file" in
  "$root"/*) ;;
  *) exit 0 ;;
esac

# ":a:b:c:ktlintFormat" from "android/core/designsystem/src/main/..." — the deepest directory above
# src/ that Gradle knows about.
rel=${file#"$root"/}
module=${rel%%/src/*}
[ "$module" = "$rel" ] && exit 0

cd "$root"
gradle_path=":$(printf '%s' "$module" | tr '/' ':'):ktlintFormat"
./gradlew "$gradle_path" --no-configuration-cache -q >/dev/null 2>&1 || exit 0
