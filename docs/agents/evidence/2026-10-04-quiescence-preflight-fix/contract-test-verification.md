# Quiescence preflight fix — contract test verification (2026-10-04)

Production fix: `.maestro/mvp/arena-matrix/run-arena-offline-settlement-leaf.sh`,
`validate_preparation_preflight()` — exact-shape gate (non-null, non-array object, sorted key set
exactly the five known counters, every value `typeof "number"` and `=== 0`), else `process.exit(2)`.
Call site and exit semantics untouched. The owner's `offline-settlement-contract.test.js` (RED
oracle) was NOT modified.

Raw stdout/stderr captured in `contract-test-green.log` and
`contract-test-reverted-leaf-red.log` (local, untracked — `*.log` is gitignored by repo policy).
The committed verification record is this file plus the run record
`docs/agents/2026-10-04-epic1-rig-run.json`.

## 1. Green run — fixed leaf, owner test verbatim

```
docker run --rm --network none \
  -v /workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate:/repo:ro \
  node:22-bookworm-slim node /repo/.maestro/mvp/arena-matrix/offline-settlement-contract.test.js
```

stdout (complete):

```
offline settlement composition tests: PASS
```

exit code: 0

The test's driver exercises the 17-payload malformed matrix ({}, null, [], [0,0,0,0,0], "",
extra key, "0" string, each of the five keys deleted, each of the five keys = 1) plus the
static safety contract, both happy paths (Room v14/v15), unsupported-schema rejection,
pre-cut-failure containment, input-mutation stop and recovery-record protection.

## 2. Revert-check — ONLY the leaf fix reverted

Scratch copy at `/tmp/revert-check-20261004` (worktree tree with the leaf restored to HEAD
1e596126; owner test verbatim), same docker invocation. stderr (tail):

```
AssertionError [ERR_ASSERTION]: unsafe quiescence passed: {}
    at testInvalidQuiescenceStopsBeforePreparationAndTransportCut (/repo/.maestro/mvp/arena-matrix/offline-settlement-contract.test.js:393:12)
```

with `actual: 0, expected: 2` — exactly the RED the spec's acceptance names (line 393,
`unsafe quiescence passed: {}; 0 !== 2`). The worktree leaf was never reverted; the scratch
copy was deleted after the check.
