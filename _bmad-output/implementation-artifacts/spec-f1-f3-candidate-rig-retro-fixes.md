---
title: 'Epic 1 candidate-rig retro fix package F1–F3: quiescence gate collision hole, oracle landing + wiring, ciCheck RED tracking'
type: 'bugfix'
created: '2026-10-25'
status: 'in-progress'
baseline_revision: '4e2ca56c0963289baf7ea67116b02a1cd914eebf'
route: 'full'
route_source: 'pinned'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '_bmad-output/mvp-candidate/epic-candidate-rig/RETROSPECTIVE.md'
  - '_bmad-output/implementation-artifacts/spec-1-candidate-rig.md'
warnings: ['oversized']
deferred: []
---

<intent-contract>

## Intent

**Problem:** The 2026-10-22 retrospective of epic 1 (`epic-candidate-rig`, verdict rejected) fixed three items as "чинить сейчас". F1 [HIGH]: the shipped quiescence gate `quiescenceIsQuiscent` (in `run-arena-offline-settlement-leaf.sh`) accepts a malformed payload — a one-key object whose single key is the comma-joined sorted five counter names, value 0 — because the key-set check compares joined strings without comparing counts, so a preflight without the five real counters sails through (exploit demonstrated against HEAD code in docker). The 2026-10-04 review triage falsely killed this exact finding by citing a length check that does not exist in shipped code. F2 [HIGH]: the 17-case malformed-payload oracle (`d6e89fae`, 459-line test) is not in the wave HEAD (only a 428-line happy-path test, which cannot distinguish old/new gate), and no automated leg runs the contract test at all — the regression can return silently. F3 [MED]: root ciCheck stays RED (missing owner-local debug `google-services.json`) and no wave tracking surface records the required re-run-and-re-record.

**Approach:** On the wave branch `night/e7-commerce` of the VPS checkout `/workspace/projects/schoolquiz3.0`, land four commits in order: (1) cherry-pick `d6e89fae` (the full oracle) from `continuation/mvp-candidate-20260926`; (2) in ONE commit, add the single-key collision case (18th) to the oracle matrix AND replace the gate's join-comparison with exact-count + hasOwnProperty checking; (3) apply the wiring diff from the 05.10 proposal to `scripts/package.json` (contract test becomes the last node of `test` and `lint`); (4) register draft story 1.7 in the wave `tickets.toml` and transfer the RED-baseline memo from `night/e1-followups`. Prove RED→GREEN with the contract test in docker. The retrospective re-verdict is out of scope.

## Boundaries & Constraints

**Always:**
- Work in the VPS checkout `/workspace/projects/schoolquiz3.0` on branch `night/e7-commerce` (HEAD at planning: `4e2ca56c`); tree must be clean before starting and carry only this run's changes after.
- Gate fix shape exactly: `Object.keys(candidate).length === quiescenceCounters.length` plus `hasOwnProperty` for each of the five names (join-comparison removed); values check (`typeof 'number'` and `=== 0`) unchanged; exit-2 call site untouched.
- New oracle case: `{[Object.keys(quiescent).sort().join(',')]: 0}` appended to the invalid-payload loop (computed from the fixture so it stays in sync); it must FAIL against the pre-fix gate (RED run recorded) and PASS after the fix.
- Contract test runs in `docker run --rm --network none -v /workspace/projects/schoolquiz3.0:/repo:ro node:22-bookworm-slim node /repo/.maestro/mvp/arena-matrix/offline-settlement-contract.test.js` (~2.5 min per run).
- Commit order per the 05.10 proposal and the retro lesson ("фикс и его оракул едут в одном мерже"): oracle commit first, then gate-fix+new-case as one commit, then wiring, then tracking.
- Wiring diff exactly as proposed: append ` && node ../.maestro/mvp/arena-matrix/offline-settlement-contract.test.js` to `test`, ` && node --check ../.maestro/mvp/arena-matrix/offline-settlement-contract.test.js` to `lint`; no other package.json changes.
- Story 1.7 block and its header comment transfer verbatim from the `night/e1-followups` draft (status-free draft); memo dir `docs/agents/evidence/2026-10-05-cicheck-baseline/` (README.md + cicheck-baseline.json) transfers verbatim — it carries the RED-lifting condition.
- Record evidence under `docs/agents/evidence/2026-10-25-f1-f3-retro-fix-package/` with `schemaVersion`/`capturedAtUtc` per `docs/agents/` conventions.

**Never:**
- Never modify `continuation/mvp-candidate-20260926`, `night/e1-followups`, or the `.local/worktrees/mvp-candidate` worktree — they are read-only sources (cherry-pick and `git checkout <branch> -- <path>` only).
- Never touch the producer `scripts/arena-e2e-preparation-preflight-reader.js`, its test, or anything else in `scripts/` except `package.json`'s `test`/`lint` tails.
- Never commit a debug `google-services.json`, never run ciCheck, never give 1.7 a status (it stays a draft until the owner supplies the file).
- Never edit existing tickets.toml entries (1.3/1.5 done-notes stay); only append the 1.7 block.
- No Gradle builds, no device work, no restarts of the demo backend container; docker is used only for read-only test runs.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Collision preflight (F1 hole) | `quiescence = {"orphanedRatingIntents,orphanedSubmitIntents,outboxRows,reservedArenaTickets,unresolvedArenaCommands": 0}` | Gate rejects: exit 2 before preparation/transport cut; contract test asserts status 2, `PRE_CUT_CHILD_FAILED`, `cut.attempted=false`, wifi/data intact, no Maestro entry, no `prepared-ticket.json` | On pre-fix gate this case is RED (`unsafe quiescence passed`) — that RED run is the detection proof |
| Full matrix (17 cases from d6e89fae) | `{}`, `null`, `[]`, `[0,0,0,0,0]`, `""`, extra key, string `"0"`, each of 5 counters deleted, each `=1` | Each exits 2 with the same guard signatures | Unchanged from the picked oracle |
| Valid quiescence | Five counters numeric 0 | Happy path passes (both Room v14/v15 sequences) | No error |
| Wired chain | `npm test` in `scripts/` with node 24 on PATH | Full chain green; contract test is the final node (~2.5 min); `npm run lint` passes with the new `--check` tail | If npm unavailable in shell, use `/workspace/infrastructure/zcode/3.14.3/node24/node-v24.14.0-linux-x64/bin` and record it |

</intent-contract>

## Code Map

- `.maestro/mvp/arena-matrix/run-arena-offline-settlement-leaf.sh` -- FIX SITE. `validate_preparation_preflight()` (~line 490); embedded Node heredoc: `quiescenceCounters` array and `quiescenceIsQuiscent` predicate at ~498–500 at HEAD `4e2ca56c`; the hole is `Object.keys(candidate).sort().join(',') === [...quiescenceCounters].sort().join(',')` (no count check, `.every` over a single zero passes).
- `.maestro/mvp/arena-matrix/offline-settlement-contract.test.js` -- ORACLE. At HEAD: 428 lines, blob `87800a8e` — identical to `d6e89fae^`'s blob, so the cherry-pick applies cleanly and yields the 459-line matrix (env hook `FAKE_PREFLIGHT_QUIESCENCE_JSON` + `testInvalidQuiescenceStopsBeforePreparationAndTransportCut` + 17-case loop). The invalid-payload loop (post-pick ~line 446) is where the 18th case goes. Fixtures self-fabricate fake adb/maestro and seal the real leaf — no other file needed.
- `scripts/package.json` -- wiring target (`scriptsTest` → `npm test` → `ciCheck` via `build.gradle.kts:60-66,76`). Only `test` and `lint` string tails change. Test deps are node builtins only — no `npm install` needed.
- `_bmad-output/mvp-candidate/epic-candidate-rig/tickets.toml` -- wave version diverged from e1f (1.3/1.5 carry 08–18.10 done-notes); append ONLY the 3-line 1.7 header comment and the 1.7 `[[story]]` block after the 1.6 entry.
- `night/e1-followups` @ `47903868` -- source of the 1.7 draft (tickets.toml hunk) and `docs/agents/evidence/2026-10-05-cicheck-baseline/{README.md,cicheck-baseline.json}` (README §"Условие снятия RED" is the recorded lifting condition). `docs/agents/night-2026-10-05/proposal-quiescence-contract-test-wiring.md` is the wiring recipe source (apply it; do not transfer the file — the applied result is self-documenting in package.json).
- `d6e89fae` on `continuation/mvp-candidate-20260926` -- cherry-pick source ("test(settlement): quiescence fixture env-hook + unsafe-quiescence stop test"), +32/−1 on the test file only.
- Node on VPS: not on default ssh PATH; use `/workspace/infrastructure/zcode/3.14.3/node24/node-v24.14.0-linux-x64/bin` (node v24.14.0, npm 11.9.0 — verified 05.10).
- Reuse precedent: contract-test docker invocation and PASS line `offline settlement composition tests: PASS` from `docs/agents/evidence/2026-10-04-quiescence-preflight-fix/contract-test-verification.md`.

## Tasks & Acceptance

**Execution:**
- [ ] `git` — pre-flight: confirm clean tree on `night/e7-commerce`; `git cherry-pick -x d6e89fae` (records provenance); verify `wc -l` = 459 and `git merge-base --is-ancestor d6e89fae HEAD` now true. Commit 1 (F2a).
- [ ] `.maestro/mvp/arena-matrix/offline-settlement-contract.test.js` — append the 18th case `{[Object.keys(quiescent).sort().join(',')]: 0}` to the invalid-payload loop; run the docker contract test and record the RED (`unsafe quiescence passed` at the collision case) in evidence.
- [ ] `.maestro/mvp/arena-matrix/run-arena-offline-settlement-leaf.sh` — replace the `quiescenceIsQuiscent` join-comparison with `Object.keys(candidate).length === quiescenceCounters.length && quiescenceCounters.every((name) => Object.prototype.hasOwnProperty.call(candidate, name)) && ...` (values check unchanged); re-run the docker contract test → exit 0 `offline settlement composition tests: PASS`; commit test case + leaf fix together. Commit 2 (F1).
- [ ] `scripts/package.json` — apply the wiring tails to `test` and `lint`; validate JSON; run `npm test` (node24 PATH) → full chain green with the contract test as the last node; optionally `npm run lint`. Commit 3 (F2b).
- [ ] `_bmad-output/mvp-candidate/epic-candidate-rig/tickets.toml` — append header comment + 1.7 `[[story]]` block verbatim from the e1f draft; `git checkout night/e1-followups -- docs/agents/evidence/2026-10-05-cicheck-baseline/`; verify both memo files present. Commit 4 (F3).
- [ ] `docs/agents/evidence/2026-10-25-f1-f3-retro-fix-package/` — result record (schemaVersion, capturedAtUtc, per-fix F1/F2/F3 outcomes, RED run output, GREEN run output, npm-test tail, commit hashes); final commit adds this record + the spec.

**Acceptance Criteria:**
- Given the cherry-picked oracle plus the collision case, when the contract test runs in docker against the pre-fix leaf, then it fails with `unsafe quiescence passed` naming the collision object, and against the post-fix leaf it exits 0 with `offline settlement composition tests: PASS` (18 invalid cases + happy paths).
- Given the fixed gate, when a preflight payload is a one-key object keyed by the comma-joined sorted counter names with value 0, then the leaf exits 2 before preparation and transport cut.
- Given the wired `scripts/package.json`, when `npm test` runs in `scripts/` on the VPS, then the chain passes and its final node is `../.maestro/mvp/arena-matrix/offline-settlement-contract.test.js` (and `lint` covers it with `node --check`).
- Given the wave `tickets.toml` at the final commit, when read, then story 1.7 `rerun-cicheck-record` is present as a status-free draft with the baseline pointer, and `docs/agents/evidence/2026-10-05-cicheck-baseline/` exists with both files carrying the RED-lifting condition.
- Given the finished run, when `git log night/e7-commerce` is inspected, then the four commits appear in the order oracle → fix+case → wiring → tracking, the cherry-pick line references `d6e89fae`, and the only dirty-to-clean delta is this run's files.

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Design Notes

- Why count + hasOwnProperty instead of a literal sorted string: any single-key object whose key happens to be the joined string defeats string comparison; count equality plus per-name ownership pins the exact set without relying on sort order at all.
- Why the new case rides the same commit as the gate fix: the retro lesson — a "test green" criterion arrives together with its test in the same merge; a fix committed without its detecting case repeats the F2 failure mode.
- Why test-commit-before-wiring ordering matters (proposal §3): wiring into `npm test` while the matrix is not committed would silently execute the old 428-line happy path in clean checkouts — green chain, false coverage.
- RED run before the fix is required evidence: the 04.10 revert-check showed the happy-path test cannot distinguish gates; only the collision case failing pre-fix proves the package actually detects the hole.

## Verification

**Commands:**
- `git merge-base --is-ancestor d6e89fae HEAD` (after pick) -- expected: true
- `ssh rnsrv 'cd /workspace/projects/schoolquiz3.0 && docker run --rm --network none -v /workspace/projects/schoolquiz3.0:/repo:ro node:22-bookworm-slim node /repo/.maestro/mvp/arena-matrix/offline-settlement-contract.test.js'` -- expected pre-fix: non-zero exit, assertion `unsafe quiescence passed`; post-fix: exit 0, stdout ends `offline settlement composition tests: PASS`
- `ssh rnsrv 'cd /workspace/projects/schoolquiz3.0/scripts && PATH=/workspace/infrastructure/zcode/3.14.3/node24/node-v24.14.0-linux-x64/bin:$PATH npm test'` -- expected: exit 0, chain ends with the contract test PASS
- `git -C /workspace/projects/schoolquiz3.0 status --porcelain` (after final commit) -- expected: empty
- `git -C /workspace/projects/schoolquiz3.0 log --oneline -5` -- expected: evidence/spec commit on top of wiring, tracking, fix+case, oracle commits

**Manual checks (if no CLI):**
- tickets.toml 1.7 block matches the e1f draft verbatim (id, slug, title, covers, after, hitl, both comment lines) and carries no status.
- Memo files match `night/e1-followups` blobs (`git diff night/e1-followups -- docs/agents/evidence/2026-10-05-cicheck-baseline/` is empty).
