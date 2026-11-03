#!/usr/bin/env bash
# Показать правки уроков в читаемом виде «было → стало» (для ревью работы ZCode).
#   ./review-diff.sh [<subject>] [<git-range>]     по умолчанию: все предметы, рабочее дерево vs HEAD
set -u
SUBJ="${1:-}"; RANGE="${2:-}"
cd "$(dirname "$0")"
paths="data/school/${SUBJ:-}"; [ -z "$SUBJ" ] && paths="data/school"
git diff --stat $RANGE -- "$paths" | tail -1
git diff -U0 $RANGE -- "$paths" | awk '
  /^diff --git/ { f=$3; sub("a/scripts/seed-bulk/data/school/","",f); print "\n=== " f " ===" ; next }
  /^@@/ { match($0,/\+[0-9]+/); print "--- строка " substr($0,RSTART+1,RLENGTH-1) " ---"; next }
  /^-[^-]/ { print "  БЫЛО:  " substr($0,2,260); next }
  /^\+[^+]/ { print "  СТАЛО: " substr($0,2,260); next }
'
