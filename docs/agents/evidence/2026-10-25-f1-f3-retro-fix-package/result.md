# Epic 1 candidate-rig retro fix package F1–F3 — run record (2026-10-25)

schemaVersion: 1, capturedAtUtc: 2026-10-25T10:40:36Z, дерево: `night/e7-commerce`, planning baseline `4e2ca56c0963289baf7ea67116b02a1cd914eebf`, all runs on the VPS checkout `/workspace/projects/schoolquiz3.0`.

Spec: `_bmad-output/implementation-artifacts/spec-f1-f3-candidate-rig-retro-fixes.md` (committed verbatim by the final commit of this run). Sources of the three fixes: `epic-candidate-rig` retrospective 2026-10-22 (F1, F2, F3 — «чинить сейчас»).

## Outcome per fix

| Fix | Outcome | Commit |
|---|---|---|
| F2a — land the 17-case malformed-payload oracle | DONE — `d6e89fae` cherry-picked with `-x`; 459 lines; patch-id equivalence proven | `024675ea` |
| F1 — quiescence gate exact-count fix + 18th collision case | DONE — RED recorded pre-fix, GREEN post-fix, fix and case in one commit | `fc71bfc1` |
| F2b — wire the contract test into the canonical gate | DONE — `npm test` ends with the contract test; `lint` covers it with `node --check` | `fa72bd73` |
| F3 — story 1.7 draft + RED-baseline memo transferred | DONE — verbatim transfer, byte-diff verified | `0c356339` |

Commit order on `night/e7-commerce`: oracle → fix+case → wiring → tracking → evidence (this record + the spec).

## F2a — oracle landed (`024675ea`)

- `git cherry-pick -x d6e89fae` from `continuation/mvp-candidate-20260926` (commit «test(settlement): quiescence fixture env-hook + unsafe-quiescence stop test», +32/−1, test file only); the message carries the provenance line `(cherry picked from commit d6e89faed17a64b83946a64d411aee2a100ab2f2)`.
- `wc -l .maestro/mvp/arena-matrix/offline-settlement-contract.test.js` = **459** after the pick.
- Patch equivalence: `git show d6e89fae -- <test> | git patch-id --stable` = `30ff39829224f700d9446b175223f58fffe6f2b6…` = same for `HEAD`'s pick (identical patch-ids).
- Spec deviation note: the spec's check «`git merge-base --is-ancestor d6e89fae HEAD` now true» is unsatisfiable under a cherry-pick — DAG ancestry requires a merge, and the spec mandates four linear commits with a cherry-pick line. The intent (oracle content in the wave HEAD) is proven instead by identical patch-ids and the clean pick. `--is-ancestor d6e89fae HEAD` remains false by construction.

## F1 — RED run (pre-fix gate, collision case)

Command: `docker run --rm --network none -v /workspace/projects/schoolquiz3.0:/repo:ro node:22-bookworm-slim node /repo/.maestro/mvp/arena-matrix/offline-settlement-contract.test.js` with the 18-case matrix in place and the gate still at the HEAD-`4e2ca56c` join-comparison shape. Inlined verbatim output (raw `.log` files are gitignored by repo policy):

```
node:assert:150
  throw new AssertionError(obj);
  ^

AssertionError [ERR_ASSERTION]: unsafe quiescence passed: {"orphanedRatingIntents,orphanedSubmitIntents,outboxRows,reservedArenaTickets,unresolvedArenaCommands":0}

0 !== 2

    at testInvalidQuiescenceStopsBeforePreparationAndTransportCut (/repo/.maestro/mvp/arena-matrix/offline-settlement-contract.test.js:393:12)
    at Object.<anonymous> (/repo/.maestro/mvp/arena-matrix/offline-settlement-contract.test.js:448:3)
    ...
    at node:internal/main/run_main_module:36:49 {
  generatedMessage: false,
  code: 'ERR_ASSERTION',
  actual: 0,
  expected: 2,
  operator: 'strictEqual',
  diff: 'simple'
}

Node.js v22.23.3
EXIT_CODE=1
```

This is the detection proof the 04.10 happy-path-only test could not give (retro F2: «happy-path тест не различает старый слабый гейт и новый»): the one-key collision object passed the pre-fix gate (`actual: 0` instead of exit 2).

RED reproduction from committed history (fix and detecting case share `fc71bfc1` by design): on a scratch checkout take the test file from `fc71bfc1` and the leaf from `024675ea` (equivalently, revert the single leaf hunk of `fc71bfc1`), run `docker run --rm --network none -v <checkout>:/repo:ro node:22-bookworm-slim node /repo/.maestro/mvp/arena-matrix/offline-settlement-contract.test.js` — expect exit 1 with `unsafe quiescence passed` naming the collision object.

## F1 — gate fix + 18th case, GREEN runs (`fc71bfc1`)

Predicate replaced in `validate_preparation_preflight()` (embedded Node heredoc, `.maestro/mvp/arena-matrix/run-arena-offline-settlement-leaf.sh`):

```js
const quiescenceIsQuiescent = (candidate) => typeof candidate === 'object' && candidate !== null && !Array.isArray(candidate) &&
    Object.keys(candidate).length === quiescenceCounters.length && quiescenceCounters.every((name) => Object.prototype.hasOwnProperty.call(candidate, name)) &&
    Object.values(candidate).every((count) => typeof count === 'number' && count === 0);
```

Join-comparison removed; values check unchanged; exit-2 call site untouched. The 18th case `{[Object.keys(quiescent).sort().join(',')]: 0}` is computed from the `quiescent` fixture (stays in sync if counters change) and appended to the invalid-payload loop — same commit as the fix (retro lesson: «фикс и его оракул едут в одном мерже»).

GREEN run immediately after the fix (working tree identical to `fc71bfc1`): exit 0, stdout `offline settlement composition tests: PASS`. GREEN re-run at the final committed state (`0c356339` content, 10:40Z): exit 0, same PASS line. 18 invalid cases (17 from `d6e89fae` + collision) + both Room v14/v15 happy paths + the static-safety contract all pass.

## F2b — wiring (`fa72bd73`)

`scripts/package.json`: appended ` && node ../.maestro/mvp/arena-matrix/offline-settlement-contract.test.js` to `test` (last node, after `../.maestro/mvp/arena-e2b/run-arena-legacy-recovery.test.js`) and ` && node --check ../.maestro/mvp/arena-matrix/offline-settlement-contract.test.js` to `lint`; no other keys touched (2-line diff). JSON validated (`python3 -c "import json..."` + tail assertions). Note: the wave `lint` had gained four nodes since the 05.10 proposal (b8bc23ff) — `mvp-qualification-firestore-test.js`, `course-tournament-http.test.js`, `course-tournament-private-rules.test.js`, `course-tournament-lifecycle-demo.test.js` — so the proposal's literal diff hunks no longer matched; the semantic change (appending the same two tails to the CURRENT full `test`/`lint` strings, i.e. after those four nodes in `lint`) was applied instead, per the spec's «no other package.json changes».

`npm test` run in `scripts/` with node on PATH (`/workspace/infrastructure/zcode/3.14.3/node24/node-v24.14.0-linux-x64/bin`, node v24.14.0 / npm 11.9.0 as measured 05.10): exit 0; the decisive tail (inlined):

```
ℹ tests 16
ℹ pass 16
ℹ fail 0
ℹ duration_ms 250.267312
offline settlement composition tests: PASS
EXIT_CODE=0
```

Attribution note: the `ℹ tests 16 …` block is the node:test summary of the PRECEDING chain node (`../.maestro/mvp/arena-e2b/run-arena-legacy-recovery.test.js`, 16/16), not the contract test's; the contract test prints no summary — its evidence is its own final `offline settlement composition tests: PASS` line plus the recorded exit code 0 of the whole chain.

The contract test is the final node of the chain (~2.5 min; baseline chain before it: ~2.6 s). `npm run lint` (same PATH): exit 0 with the new `--check` tail. `scriptsTest` → `npm test` → `ciCheck` picks this up automatically via `build.gradle.kts` — but note ciCheck itself stays RED (F3), so the leg is not exercised by the root gate yet.

## F3 — tracking (`0c356339`)

- `_bmad-output/mvp-candidate/epic-candidate-rig/tickets.toml`: the 3-line 1.7 header comment and the `[[story]] 1.7` block appended after the 1.6 entry, transferred mechanically from `night/e1-followups` (`git show` + `sed` extract, not retyped); byte-diff verified against the source fragments (`diff` clean for both). The block carries **no status key** (status-free draft by design). Existing 1.3/1.5/1.6 entries untouched.
- `docs/agents/evidence/2026-10-05-cicheck-baseline/` checked out from `night/e1-followups` (`git checkout night/e1-followups -- <dir>`): `README.md` + `cicheck-baseline.json`, both present; `git diff night/e1-followups -- <dir>` is empty (byte-identical). `README.md` §«Условие снятия RED» (:42) is the recorded RED-lifting condition: the owner places the debug `google-services.json` in `apps/android-next/` and re-runs `ciCheck`; story 1.7 stays a draft until then.

## Environment identity

- Contract-test legs: docker image `node:22-bookworm-slim` (digest `sha256:c3de60bf2f9dd0ac6370e6117950ff62d6e339527e7472301c9c78a017978392` at run time), `--network none`, repo mounted `:/repo:ro`, in-container node v22.23.3.
- npm legs: host node v24.14.0 / npm 11.9.0 from `/workspace/infrastructure/zcode/3.14.3/node24/node-v24.14.0-linux-x64/bin` (node is not on the default ssh PATH).
- No Gradle builds, no device work, no demo-backend container operations; ciCheck NOT run (Never per spec).

## Concurrent-activity observation (not this run's changes)

- Between commits `fc71bfc1` and `fa72bd73`, nine foreign modified files appeared in the working tree (`android/feature/economy/presentation/…`, `platform/billing/…`, `shared/feature/economy/domain/…`) — a sibling/owner session active in the same checkout. Not touched by this run; all commits here were staged and committed by explicit path.
- After `0c356339`, the git index was found holding ~8422 staged deletions spanning the whole tree (signature of a `git rm -r --cached .`-style operation by the concurrent session), while HEAD, the branch and the working tree remain intact. This run's final commit is pathspec-scoped (`git commit -- <this run's paths>`) so the foreign index state is neither committed nor discarded by this run. Flagged for the owner: if that index state is committed as-is, it would produce a whole-tree deletion commit.

## Verification checklist against the spec

- Cherry-pick provenance line references `d6e89fae`: yes (`-x`).
- 459-line oracle + 18th case; RED against pre-fix gate, GREEN post-fix: yes (outputs inlined above).
- Gate shape exactly count + `hasOwnProperty`, values check unchanged, exit-2 call site untouched: yes (diff `fc71bfc1`, 2 files, +2/−2).
- `npm test` chain green with the contract test as the final node; `lint` covers it: yes (exit codes recorded).
- tickets.toml 1.7 status-free draft with baseline pointer; memo dir present with both files, byte-identical to `night/e1-followups`: yes.
- `git log night/e7-commerce` order oracle → fix+case → wiring → tracking: yes.
- Spec's post-run `git status --porcelain` empty: **not satisfiable** — the concurrent session's foreign changes (unstaged + staged deletions) are present; this run's own files are all committed.
