#!/usr/bin/env bash
# Aggregated deterministic checks for feature pipeline artifacts.
#
# Usage:
#   scripts/pipeline/check_pipeline_docs.sh docs/features/<slug>
#   scripts/pipeline/check_pipeline_docs.sh            # scans docs/features/*
#
# This script intentionally stays fast and docs-only. Gradle/build validation remains
# in ./gradlew ciCheck --no-configuration-cache.
set -u

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$REPO_ROOT" || exit 2

ISSUES=()
WARNINGS=()

add_issue() {
  ISSUES+=("$1")
}

add_warning() {
  WARNINGS+=("$1")
}

relpath() {
  python3 - <<'PY' "$1" "$REPO_ROOT"
import os
import sys

print(os.path.relpath(sys.argv[1], sys.argv[2]))
PY
}

json_payload_for_file() {
  python3 - <<'PY' "$1"
import json
import sys

print(json.dumps({"tool_input": {"file_path": sys.argv[1]}}))
PY
}

run_hook_for_file() {
  local hook="$1"
  local file="$2"
  local payload
  local output

  payload="$(json_payload_for_file "$file")"
  if ! output="$(printf '%s' "$payload" | CLAUDE_PROJECT_DIR="$REPO_ROOT" "$hook" 2>&1)"; then
    add_issue "$file failed $(basename "$hook"):
$output"
    return 1
  fi

  if [ -n "$output" ]; then
    printf '%s\n' "$output"
  fi
  return 0
}

collect_feature_dirs() {
  if [ "$#" -eq 0 ]; then
    find docs/features -mindepth 1 -maxdepth 1 -type d -print 2>/dev/null | sort
    return
  fi

  local target
  for target in "$@"; do
    if [ -d "$target" ]; then
      case "$target" in
        docs/features/*) printf '%s\n' "$target" ;;
        *) find "$target" -path '*/docs/features/*' -type d -maxdepth 0 -print 2>/dev/null ;;
      esac
    elif [ -f "$target" ]; then
      case "$target" in
        docs/features/*/*) dirname "$target" | sed -E 's|(docs/features/[^/]+).*|\1|' ;;
        *) ;;
      esac
    else
      add_issue "target does not exist: $target"
    fi
  done | sort -u
}

check_global_pipeline_templates() {
  local impl=".claude/commands/feature-implement.md"
  local comm=".claude/rules/agent-communication.md"

  if [ -f "$impl" ]; then
    local lowercase
    lowercase="$(grep -n 'Build status:' "$impl" || true)"
    if [ -n "$lowercase" ]; then
      add_issue "$impl uses lowercase 'Build status:'; reviewers require exact 'Build Status: PASSED (commit <sha-or-phase-ref>)':
$lowercase"
    fi

    if ! grep -q 'Build Status: PASSED (commit <sha-or-phase-ref>)' "$impl"; then
      add_issue "$impl does not contain exact Build Status field required by $comm"
    fi
  fi
}

check_spec_ledger() {
  local feature_dir="$1"
  local spec="$feature_dir/0-spec.md"

  [ -f "$spec" ] || return 0

  if ! grep -Eq '^- Pipeline tier:|^Pipeline tier:' "$spec"; then
    add_warning "$spec is a legacy/spec-lite artifact without 'Pipeline tier'. New specs should include Pipeline tier, Decision Ledger and Assumption Ledger."
    return 0
  fi

  if ! grep -q '^## Decision Ledger' "$spec"; then
    add_issue "$spec has Pipeline tier but no '## Decision Ledger'"
  fi

  if ! grep -q '^## Assumption Ledger' "$spec"; then
    add_issue "$spec has Pipeline tier but no '## Assumption Ledger'"
  fi

  if grep -q '\[DELEGATED' "$spec" && ! grep -q '^## Delegated Decisions Summary' "$spec"; then
    add_issue "$spec contains [DELEGATED] decisions but no Delegated Decisions Summary"
  fi
}

check_unresolved_markers() {
  local feature_dir="$1"
  local files=(
    "$feature_dir/0-spec.md"
    "$feature_dir/01-architecture.md"
    "$feature_dir/02-behavior.md"
    "$feature_dir/03-decisions.md"
    "$feature_dir/04-testing.md"
    "$feature_dir/06-api-contract.md"
  )
  local file
  local hits

  for file in "${files[@]}"; do
    [ -f "$file" ] || continue
    hits="$(grep -nE 'REQUIRES?[[:space:]]+VERIFY|UNRESOLVED|TBD\b' "$file" || true)"
    if [ -n "$hits" ]; then
      add_issue "$file contains unresolved design/spec markers:
$hits"
    fi
  done
}

check_build_status_in_feature_docs() {
  local feature_dir="$1"
  local hits

  hits="$(grep -RIn 'Build status:' "$feature_dir" --include='*.md' 2>/dev/null || true)"
  if [ -n "$hits" ]; then
    add_issue "$feature_dir contains lowercase 'Build status:'; use exact 'Build Status: PASSED (commit <sha-or-phase-ref>)':
$hits"
  fi
}

check_plan_files() {
  local feature_dir="$1"
  local file
  local rel

  [ -d "$feature_dir/plan" ] || return 0

  while IFS= read -r -d '' file; do
    rel="$(relpath "$file")"
    run_hook_for_file ".claude/hooks/check-plan-no-code.sh" "$rel" >/dev/null
    run_hook_for_file ".claude/hooks/check-plan-paths.sh" "$rel" >/dev/null
  done < <(find "$feature_dir/plan" -type f -name '*.md' -print0)
}

check_c4_vs_gradle() {
  local feature_dir="$1"
  local file="$feature_dir/01-architecture.md"
  local rel
  local output

  [ -f "$file" ] || return 0
  rel="$(relpath "$file")"
  output="$(run_hook_for_file ".claude/hooks/check-c4-vs-gradle.sh" "$rel" 2>&1 || true)"

  if echo "$output" | grep -q 'UNVERIFIED ARROWS'; then
    add_issue "$rel has C4 arrows that do not match Gradle dependencies:
$output"
  elif [ -n "$output" ]; then
    add_warning "$rel C4/Gradle check produced warnings:
$output"
  fi
}

check_api_contract_types() {
  local feature_dir="$1"
  local file="$feature_dir/06-api-contract.md"
  local rel
  local output

  [ -f "$file" ] || return 0
  rel="$(relpath "$file")"
  output="$(run_hook_for_file ".claude/hooks/check-api-contract-types.sh" "$rel" 2>&1 || true)"

  if [ -n "$output" ]; then
    if [ "${PIPELINE_STRICT_API:-0}" = "1" ]; then
      add_issue "$rel API contract type check warning promoted to failure:
$output"
    else
      add_warning "$rel API contract type check warning:
$output"
    fi
  fi
}

main() {
  local feature_dirs
  local feature_dir

  check_global_pipeline_templates

  feature_dirs="$(collect_feature_dirs "$@")"
  if [ -z "$feature_dirs" ]; then
    add_warning "no feature directories found for target(s): ${*:-docs/features}"
  fi

  while IFS= read -r feature_dir; do
    [ -n "$feature_dir" ] || continue
    check_spec_ledger "$feature_dir"
    check_unresolved_markers "$feature_dir"
    check_build_status_in_feature_docs "$feature_dir"
    check_plan_files "$feature_dir"
    check_c4_vs_gradle "$feature_dir"
    check_api_contract_types "$feature_dir"
  done <<< "$feature_dirs"

  if [ "${#WARNINGS[@]}" -gt 0 ]; then
    printf 'PIPELINE DOC WARNINGS (%s):\n' "${#WARNINGS[@]}" >&2
    printf -- '- %s\n' "${WARNINGS[@]}" >&2
    printf '\n' >&2
  fi

  if [ "${#ISSUES[@]}" -gt 0 ]; then
    printf 'PIPELINE DOC CHECK FAILED (%s issue(s)):\n' "${#ISSUES[@]}" >&2
    printf -- '- %s\n' "${ISSUES[@]}" >&2
    exit 2
  fi

  printf 'PIPELINE DOC CHECK PASSED'
  if [ "${#WARNINGS[@]}" -gt 0 ]; then
    printf ' with %s warning(s)' "${#WARNINGS[@]}"
  fi
  printf '\n'
}

main "$@"
