#!/usr/bin/env bash
# Download artifacts of a finished Device Farm run into run/device-farm/<name>/
# Usage: bash run/device-farm/df-collect.sh <run-arn> <name>
set -euo pipefail
RUN_ARN="$1"; NAME="$2"
REGION=us-west-2
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT="$ROOT/run/device-farm/$NAME"
mkdir -p "$OUT"

aws devicefarm get-run --arn "$RUN_ARN" --region "$REGION" \
  --query "run.{status:status,result:result,started:started,stopped:stopped,deviceMinutes:deviceMinutes}" \
  --output json | tee "$OUT/run-summary.json"

JOBS=$(aws devicefarm list-jobs --arn "$RUN_ARN" --region "$REGION" --query "jobs[].arn" --output text)
for JOB in $JOBS; do
  SUITES=$(aws devicefarm list-suites --arn "$JOB" --region "$REGION" --query "suites[].arn" --output text)
  for SUITE in $SUITES; do
    TESTS=$(aws devicefarm list-tests --arn "$SUITE" --region "$REGION" --query "tests[].arn" --output text)
    for TEST in $TESTS; do
      # list-artifacts требует явный --type (31.10: без него ParamValidation,
      # артефакты молча терялись через || true)
      for TYPE in LOG FILE SCREENSHOT; do
        aws devicefarm list-artifacts --arn "$TEST" --type "$TYPE" --region "$REGION" --query "artifacts[].{name:name,type:type,url:url}" --output json >> "$OUT/artifacts.json" || true
        echo >> "$OUT/artifacts.json"
      done
    done
  done
done

python3 - "$OUT" <<'PYEOF'
import json, subprocess, sys, os
out = sys.argv[1]
items = []
raw = open(os.path.join(out, "artifacts.json")).read().strip()
for chunk in raw.split("\n\n"):
    chunk = chunk.strip()
    if not chunk:
        continue
    items.extend(json.loads(chunk))
seen = set()
for item in items:
    url = item.get("url")
    if not url or url in seen:
        continue
    seen.add(url)
    kind = item.get("type", "FILE").lower()
    name = item.get("name", "artifact").replace("/", "_").replace(" ", "_")
    target = os.path.join(out, f"{kind}-{name}")
    subprocess.run(["curl", "-sS", "--path-as-is", "-o", target, url], check=False)
    print("saved", target)
PYEOF
ls -la "$OUT"
