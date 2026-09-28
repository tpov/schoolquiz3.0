---
title: 'Accept current Room schema in Arena evidence readers'
type: 'bugfix'
created: '2026-09-28'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
baseline_commit: '9a4e5e1c511017704acce573d78a2f1c767b8066'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Arena preparation and pending-settlement proof readers reject the current app database: they accept only Room v14, while the installed candidate is Room v15. This prevents the planned Arena offline proof from reaching its acceptance checks.

**Approach:** Accept only the exact known `(version, identityHash)` pairs for Room v14 and v15, and report the pair actually read. Keep the one-time historical e499 recovery pinned to v14; preserve all existing ownership, quiescence, ticket, receipt, and read-only checks.

</frozen-after-approval>

## Implementation Notes

Investigation: `AppDatabase.kt` declares v15; generated schemas bind v14 to `ca5439bdc37decd3f21cb843d10bb3f4` and v15 to `e3a1b9e13aff4304afeba070b8895b6c`. `Migration14to15` adds only `quest_drafts.activeSubmissionId` and its index. The preflight reader hardcodes v14 and reports constants; the pending-settlement reader hardcodes v14 and already returns observed fields after validation. `arena-e2e-legacy-settlement.js` imports the v14 identity from the pending reader and must remain strictly v14-only. The offline leaf currently validates preflight owner/quiescence but not its Room identity; extend that gate to the same exact allowlist so unsupported evidence cannot proceed to ticket preparation.

Irreversible effects: none. This slice changes only read-only evidence tooling, its tests, and its operator documentation. It must not alter the app schema/migrations, Firebase, Room snapshots, tickets, outbox, or historical recovery data.

Footprint: centralize the exact Room schema allowlist; update preparation and pending readers, keep legacy recovery explicitly bound to v14, add positive/negative v14/v15 pair tests (including the offline composition gate), and correct the stale Arena matrix README. No APK or Gradle rebuild is required.

Implemented in working tree: pending SETTLE and preparation preflight readers now accept only exact v14/v15 schema/hash pairs and report the observed identity. Historical e499 recovery remains v14-only. The offline launcher independently checks the same pair allowlist before preparation. Tests bind the reader allowlist to generated Room schema files, exercise both pairs through real `node:sqlite`, verify legacy v15 rejection on disk, and cover launcher v14/v15 acceptance plus cross-pair/unknown rejection.

## Review Triage Log

| Finding | Verdict and evidence | Route |
|---|---|---|
| Blind-hunter round 1: offline leaf duplicates the reader's schema allowlist. | medium — the independent launcher guard could drift from the reader on a future schema change; both lists are in separate code paths. | patch — added a composition-test parity assertion against reader constants. |
| Blind-hunter and verification-gap: launcher happy path covered v15 only. | medium — existing reader tests covered v14, but no launcher-level run exercised its independent validator. | patch — parameterized composition happy path over v14 and v15. |
| Launcher unknown schema rejection lacked a case. | low — the exact pair set rejects unknown values, but only cross-pair rejection had a launcher test. | patch — added v16/unknown-hash rejection case. |
| Preparation preflight v15 had only fake-adapter coverage. | medium — fake rows bypass the actual SQLite PRAGMA and identity query. | patch — added read-only SQLite integration for both supported pairs. |
| Schema constants could drift from generated Room schemas. | medium — fixtures derived from the same constants would not detect a stale or incorrect identity. | patch — added equality check against generated schema JSON files. |
| Legacy recovery lacked a test for the exact valid v15 pair. | low — the code required v14 but the prior future-schema test paired v15 with the v14 hash. | patch — added exact v15-pair rejection regression. |
| Edge-case-hunter and blind-hunter round 2: missing quiescence counters can pass the offline launcher gate. | medium — `Object.values({}).some(...)` is false, so an incomplete `PASS` object may proceed; the production reader currently emits all five fields. | defer — pre-existing launcher contract issue recorded in `deferred-work.md`; fix before Arena acceptance. |
| Blind-hunter round 2: invalid adapter output is classified as an unsupported schema. | medium — the added early schema lookup ran before `assertQuiescent()`'s object check, changing the `null` result from `SQLITE_OUTPUT_INVALID` to `UNSUPPORTED_ROOM_SCHEMA`. | patch — validate object shape first, return the observed schema with quiescence, and assert the invalid-output code. |
| Blind-hunter round 2: verification ran on Node v26 despite README documenting Node v22. | false — the complete targeted suite was rerun under the existing Node v22.23.1 Docker image. | reject — final verification uses the documented major version. |
| Blind-hunter round 2: SQLite integration fixtures are not Room-generated or migration-backed. | false — the tests execute real SQLite queries with exact generated Room identity hashes; current migration changes only `quest_drafts`, while these readers query unchanged Arena tables. This slice does not change or claim to validate the migration. | reject — no incorrect reader outcome is demonstrated by this fixture scope. |
| Blind-hunter round 2: exact v15 legacy rejection was tested only in memory, not through `readLocalProof()`. | medium — an in-memory assessment alone would not detect a regression in the on-disk `PRAGMA user_version` gate. | patch — added a SQLite v15/version+identity pair and asserted `readLocalProof()` fails closed. |
| Blind-hunter round 2: launcher repeats the version/hash pairs instead of sharing the constant module. | false — the launcher is a separate pre-prepare trust boundary and independently validates the evidence; its composition test now asserts exact parity with the shared Node-reader constants, so drift fails verification. | reject — independent validation is intentional and synchronized by an executable parity check. |
| Blind-hunter round 2: Markdown spec was created executable. | low — file mode was 0744, which is unnecessary for a documentation artifact. | patch — changed the spec file mode to 0644. |

## Verification

- `node scripts/arena-e2e-pending-settlement-reader.test.js` — PASS (Node v22.23.1; read-only SQLite v14/v15 and generated-schema identity comparison).
- `node scripts/arena-e2e-preparation-preflight-reader.test.js` — PASS (Node v22.23.1; invalid output classification and read-only SQLite v14/v15).
- `node scripts/arena-e2e-legacy-settlement.test.js` — PASS (35 tests, Node v22.23.1; exact v15 pair rejected both in-memory and through `readLocalProof()`).
- `node .maestro/mvp/arena-e2b/run-arena-legacy-recovery.test.js` — PASS (16 tests, Node v22.23.1).
- `node .maestro/mvp/arena-matrix/offline-settlement-contract.test.js` — PASS (Node v22.23.1; launcher accepts both exact pairs and rejects cross-pair/unknown pairs before preparation).
- `bash -n .maestro/mvp/arena-matrix/run-arena-offline-settlement-leaf.sh` — PASS.
- `git diff --check` — PASS on the authoritative VPS repository.
- Runtime note: the VPS SSH shell has no Node binary, so these checks ran in its already-present `node:22-bookworm-slim` container with the authoritative worktree mounted read-only. No Gradle/APK or physical-device test is required for this tooling-only slice.
