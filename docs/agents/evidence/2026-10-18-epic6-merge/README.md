# Merge: night/e6-exams → night/e5-inception (epic 6 exams/certificates)

Date: 2026-10-18. Executed by the night session after epic 5.12 demo-e2e closed on the live rig.

## What was merged

- Into: `night/e5-inception` at `7f84599f` (ticket 5.12 done, live-rig evidence)
- From: `night/e6-exams` at `4cb73eae` (epic-6 retrospective accepted-with-open-items; local == origin at merge time)
- Merge base: `dc9beb20`; the merge brings the full exam vertical (spec
  `_bmad-output/implementation-artifacts/spec-epic-6-exams-certificates.md`, frontmatter
  `status: done`): sittings, certificates, corrections, client exam run, EXAM-TECH-1 fixtures,
  plus the epic-6 retro. 26 commits ahead of the inception line.
- Merge command: `git merge --no-ff --no-commit night/e6-exams`, conflicts resolved, then a single
  merge commit. This file and the epic-card update ride in that merge commit.

Pre-merge checks: working tree clean, branch == origin/night/e5-inception, HEAD matched the
briefed `7f84599f` (no new commits to reconcile).

## Conflicts (dry run predicted 2, actual 2 — exactly as briefed)

### 1. `functions/arena-lifecycle-views-handler.test.js` — the pinned callable-surface hash

Both lines re-pinned `ACCEPTED_NON_LIFECYCLE_EXPORT_SET_SHA256` over different surfaces:
HEAD (story 5.3) added `fetchCourseTournamentEvents` + `recordCourseTournamentDownload`;
night/e6-exams added the exam callables (`startThemeTest`…`reconcileExamSittings`,
`materializeExamCorrection`). Resolution: **union of both sides, one pin recomputed over the
merged `index.js`** — not either side's stale value.

- Method check: the recomputation procedure was first run against each side's own tree
  (`git archive` extracts, same loading path as the test) and reproduced both original pins
  byte-for-byte (e5: 82 names → `2d453d6d…`, e6: 92 names → `5caaa14c…`), which validates it.
- Merged surface: 94 non-lifecycle exports (92 from e6 + exactly the two 5.3 names).
- New pin: `c432ab6484d60f07c75ddad05c761f936afbb25727eb39d6e65088bb16c3dece`.
- The comment above the pin now narrates both re-pins (5.3 and the exam vertical) plus the
  merge re-pin of 2026-10-18.

### 2. `platform/firebase/.../di/FirebaseModule.kt` — parallel DI registrations

Both sides appended a data-source registration after `QuestionWindowRemoteDataSource`:
HEAD the tournament block (story 5.9: `FirebaseTournamentRemoteDataSource` + 4 interface
bindings), e6 the exam blocks (CAP-21/22: `FirebaseExamSittingRemoteDataSource` +
`FirebaseExamCertificateRemoteDataSource` + interface bindings). Resolution: **both blocks kept,
tournament first, then exams** — DI bindings of epic 6 and additions of epic 5 all preserved;
imports had auto-merged and reference real classes/interfaces (verified by grep before staging).

## Gates

| Gate | Result |
|---|---|
| functions (Node, `run-tests.js`) | **PASS — 124 of 124 suites**, including the re-pinned surface test (`arena-lifecycle-views-handler.test.js`) and both exam + tournament suites |
| Kotlin `:apps:android-next:compileDebugKotlin` (sgw) | see below |
| Kotlin `:platform:firebase:test` (sgw) | **311 of 313 pass; 2 failures — both pre-existing at the merge base, not introduced by the merge** (analysis below) |

### The 2 Kotlin failures are pre-existing drift, not merge damage

Failing tests (both hash guards binding fixture pins to actual producer sources):

1. `FirebaseArenaLessonCatalogRemoteDataSourceTest.generatedPublicContractAndCallableNameRemainBoundToActualProducer`
   — pin `3ef0ef38…` vs actual `ab1bd875…` for `functions/arena-lesson-catalog.js`.
2. `FirebaseArenaTicketRemoteDataSourceTest.fixtureHashesAndCallableNamesAreBoundToActualServerSources`
   — pin `9aec302a…` vs actual `0d76d7f5…` for `functions/arena-tickets.js`.

Proof the merge is not the cause — the inputs of both guards are byte-identical across all three
refs, so the outcome is identical everywhere:

| input | merge base `dc9beb20` | e5 `7f84599f` | e6 `4cb73eae` | merged tree |
|---|---|---|---|---|
| `functions/arena-lesson-catalog.js` | `ab1bd875…` | `ab1bd875…` | `ab1bd875…` | `ab1bd875…` |
| `functions/arena-tickets.js` | `0d76d7f5…` | `0d76d7f5…` | `0d76d7f5…` | `0d76d7f5…` |
| catalog fixture pin | `3ef0ef38…` | `3ef0ef38…` | `3ef0ef38…` | `3ef0ef38…` |
| ticket fixture pin | `9aec302a…` | `9aec302a…` | `9aec302a…` | `9aec302a…` |

Root cause: `34fe664b` («fix(arena): share eligible lesson universe», an ancestor of the merge
base) rewrote both producer files but did not regenerate the `sourceSha256` pins in
`arena-catalog-client-fixtures.json` / `arena-ticket-client-fixtures.json`; the later
`827b582f` («regenerate producer fixtures») refreshed payload fixtures but not these pins.
Neither branch modified the two guard test classes since the base. Payload-fixture hashes
(`fixture.getValue("hashes")`) all pass — only the generation-time source pins are stale.

Left out of this merge on purpose: re-pinning means regenerating producer fixtures (a substantive
epic-5-arena change inside an epic-6 merge). Filed as an open item for an arena follow-up session.

## Notes

- Node binary: `/root/.zcode/server/node` (v22.16.0); system `node` is absent on this VPS.
- The merge commit's parents are the two hashes above; the commit itself is the only merge commit
  of `night/e6-exams` into `night/e5-inception` dated 2026-10-18.
- Open item for the arena line: regenerate `arena-catalog-client-fixtures.json` /
  `arena-ticket-client-fixtures.json` `sourceSha256` pins (drift since `34fe664b`) so the two
  arena guard tests go green again on every branch, not just on this merge.
