# TZ06D — resumable finalization of the full Arena season

## Status

**IMPLEMENTED CANDIDATE — NOT DEPLOYED — NOT ACCEPTED.**

This document describes the server candidate only. It is not evidence of a deployed scheduler,
a migrated Emulator, an APK/device run, Play verification, or owner acceptance. The source base
is authoritative server commit `ab4cf3d74b7cd31415af9afe836a067a15c72942`; the accompanying
implementation report records the final commit and fresh checks.

## Retained product rule

The product rule does not change: one UTC-quarter Arena winner needs 20 distinct eligible,
completed raters; scoring remains the existing exact weighted-score calculation; ties resolve by
rater count, earlier publication, then stable lesson ID. Frozen membership survives a later shelf
move. A season creates at most one 10-BOX award and matching currency-ledger effect.

Historical `FINALIZED` seasons remain untouched. New jobs use schema v2 and
`arena-season-full-cohort-v7`. A job with an older calculation version is not resumed by this
binary: it must be completed by its matching binary or deliberately put into review before
rollout.

## Public boundary and recovery

`finalizeArenaSeason({mutationId, quarterAtMs})` remains the authenticated entry point. A new
unfinished request returns a typed `PENDING` response with only quarter ID, finalization ID and
request time. Publication later creates the ordinary immutable `arena_mutations` receipt.

Before publication, the request is reserved in private
`arena_mutation_reservations/{hash(uid, mutationId)}`. It binds UID, operation, canonical quarter,
payload/request hashes and finalization ID. Rating, placement and season operations resolve a
receipt or reservation before writing, so a pending season cannot be overwritten by reuse of its
mutation ID.

The sealed job records its quarter, policy/calculation versions, source paths, phase, cursors,
counts, chains, random lease, revision and canonical-JSON `checkpointDigest`. Every phase is
lease/revision fenced; malformed source or checkpoint data becomes `REQUIRES_REVIEW`, never a
silent retry or a reward.

The persisted phases are:

1. `SCAN_MEMBERS` → `VERIFY_MEMBERS`: freeze and recheck members.
2. `SCAN_VOTES`: freeze eligible Arena vote/completion source rows and duplicate indexes.
3. `SELECT` → `VERIFY_SELECTION` → `COMPARE_SELECTION`: independently derive the latest vote per
   `(lessonId, uid)`, then compare working and verified maps.
4. `AGGREGATE` → `VERIFY_AGGREGATES` → `COMPARE_AGGREGATES`: independently derive per-lesson
   statistics.
5. `PICK_WINNER` → `VERIFY_CHECKPOINTS` → `PUBLISH`: replay authenticated source maps, audit all
   frozen rows and atomically write the result, receipt, award and ledger effect.

Firestore document-name pages contain at most five rows. Cursors are durable; a scheduler may
perform up to 64 trusted bounded advances per selected quarter without allowing a client to choose
worker time, cursor or batch size.

## v7 authenticated source maps

Two semantic roots, `selectionContentHash` and `aggregateContentHash`, are created during the
independent source derivations — never during mirror comparison. The prior XOR accumulator is
rejected: 257 replacement leaves admit a nonempty GF(2) zero-XOR subset without a SHA-256 break.
The v6 full 256-child vector trie is also rejected: it would create roughly 123,000 node documents
and about 1.97 GiB of selection-tree payload for a normal 4097-key cohort.

v7 replaces it with a compressed 256-way Patricia/Merkle trie in the existing private
`selection_tree` and `aggregate_tree` subcollections:

- A route is the 32-byte SHA-256 digest of the logical key, domain separated by tree kind.
- The fixed root is depth 0/prefix `""`. A non-root node is always a real split with at least two
  children; deletion is unsupported because this reducer only inserts or replaces source-derived
  rows.
- `childrenJson` is canonical, sparse and sorted by byte (0–255). A leaf edge stores only fixed
  size `keyHash` and `leafHash`; the latter binds Merkle version, tree domain, quarter,
  finalization ID, full logical key and semantic value. An unexpected same-route value fails
  closed rather than being merged.
- A branch edge stores its child depth, compressed prefix and child hash. The parent hash commits
  the exact sparse edge array; each node hash also binds version, domain, quarter, finalization,
  depth and prefix. Node envelopes bind their collection role and job identity.
- A missing authenticated edge, a different authenticated leaf, or a mismatch within a compressed
  prefix proves absence. A missing referenced node is corruption, not absence. Inserting inside a
  skipped prefix creates a new split branch at the first differing byte.

The overlay reads every route, referenced child and potential split node before its first write.
It validates the sealed root before replacing an old leaf, coalesces each dirty node to one write,
and then flushes the tree with the output rows and new sealed job head. This preserves Firestore's
read-before-write transaction rule. A five-row page remains strictly below Firestore's 500-write
cap even for adversarial 32-byte routes; the in-memory transaction double enforces that cap.

The root is replayed when a verified selection feeds aggregation, when verified aggregates feed
winner selection, and in both selection/aggregate audit stages. Thus a stale single or paired
working/verified rollback after a root has been sealed cannot change the winner or create an
award/receipt. Empty completed maps must retain their canonical empty root.

## Access boundary and rollout limits

The reservation and worker-state roots are explicitly denied by Firestore rules. Existing recursive
Arena-season denial covers job heads and all private subcollections, including the two tree
collections. `childrenJson` is excluded from indexes. The rules probe is evidence code; it does
not itself deploy or reload rules.

The hashes are consistency commitments, not secret MACs. They protect against normal client access,
accidental cross-job copies, deletion, malformed data and stale mirror rollbacks while the sealed
job head remains current. A privileged actor able to rewrite every row **and** the job root can
recompute public SHA-256 values; that is outside the Firestore-rules threat model and requires an
operator audit/KMS-anchor design if it enters scope.

No deployment occurs with this change. Before rollout, operations must deploy the rules/fence
revision, prove old synchronous revisions and in-flight old-version jobs are drained or reviewed,
then enable the v7 worker and inspect a fresh synthetic finalization. Source tests cannot establish
those operational facts.

## Evidence still required

Fresh Admin-SDK Emulator E2E, loaded-rules denial proof, actual 4096/4097 resource measurements,
restart/interruption recovery, deployment configuration inspection, Android/device/APK/Play work
and the full owner acceptance matrix remain required. None is implied by this design document.
