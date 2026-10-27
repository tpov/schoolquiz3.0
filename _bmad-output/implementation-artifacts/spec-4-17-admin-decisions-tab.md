---
title: '4.17 admin-decisions-tab — решения админов по вердиктам модели (UX-22)'
type: 'feature'
created: '2026-10-27'
status: 'in-review'
route: 'full'
route_source: 'auto'
review: 'quick'
review_source: 'auto'
lenses_ran: []
review_loop_iteration: 0
baseline_commit: 'b543a741'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless renegotiated by the owner">

## Intent

**Problem:** The model pipeline (4.12–4.15) writes immutable per-question verdicts into `review_verdicts`, and 4.18 settled the fee world around them — but no human surface reads a verdict. The old ReviewQueue screen reads the human-assignment world (stars, translation segments), which the model pipeline never touches, so an admin auditing «admin audits every verdict» (epic goal, CAP-3/UX-22) has nowhere to stand.

**Approach:** A new Decisions surface («Решения») joins the review-queue family. Server side: a role-checked read callable `fetchReviewDecisions` (view `quest-review-decisions-v1`, strict, bounded, paged by verdict doc id) that flattens verdict records into per-question rows joined with the submission (quest/lesson titles, question payload for the preview) and with admin marks; and a write callable `markReviewDecision` that stores ✓/✗ (`VALID`/`INVALID`) with an optional bounded comment in a separate `review_decision_marks` collection — verdicts stay immutable. Client side: a Decisions drawer section with filters «Без отметки / Верно / Неверно», 64dp rows, a detail with «Логи» (rationale, sources, attempt, model, promptVersion, revision), mark states «Сохранено» / «Отправляется…», and a static runner-style question preview (no timer, no charges). Non-admins get `permission-denied` from the view and the screen falls back to the «Предложения» section only (empty placeholder until 4.22+).

## Boundaries & Constraints

**Always:**
- Role check is server-side and reuses `requireVerifier` (adminLevel ≥ 100 or developerLevel > 100): the callable is the authority, the client hides nothing on its own.
- Marks live apart from verdicts (`review_decision_marks`, callable-only access; emulator default-deny already covers the collection, same as `review_verdicts`).
- Mark identity is (verdictId, questionId); re-marking overwrites (last write wins, `adminUid`/`updatedAtMs` recorded); the mark callable refuses an unknown verdict or a question outside that verdict (`not-found` / `invalid-argument`).
- View rows are strict: a verdict record that fails strict shape yields an `unreadable: true` row (AD-10 — a trace, not silence); the page still returns.
- Runner preview is static: question text + parsed options rendered from the submission payload; no timer, no charges, no attempt machinery.

**Never:**
- No human-review surface changes (reviewer screens, stars, translation segments — 4.19 removes them).
- No proposal records or routing (4.21–4.25); the author-side «Предложения» section ships as an empty placeholder.
- No fee or verdict semantics changes; no new Firestore rules (callables ride the Admin SDK).
- No live model calls, no device journey (4.20).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Admin reads queue | verdict records exist | Rows per question verdict: quest/lesson title, stage tag, question text, model PASS/FAIL + reason, mark state; page bounded, `hasMore` + cursor | No error expected |
| Filter «Без отметки» | rows with and without marks | Only unmarked rows; paging keeps consuming verdict docs until the row bound or exhaustion | No error expected |
| Filter «Верно»/«Неверно» | marks present | Only rows marked VALID (respectively INVALID) | No error expected |
| Admin marks ✓ with comment | valid verdict+question, comment ≤ bound | Mark doc upserted; row leaves «Без отметки»; card shows «Сохранено» | No error expected |
| Re-mark | second mark on same identity | Overwrites, updates `adminUid`/`updatedAtMs`; no second doc | No error expected |
| Mark outside verdict | questionId not in verdict's questionVerdicts | `invalid-argument`, no write | No write happens |
| Mark unknown verdict | verdictId names no doc | `not-found`, no write | No write happens |
| Comment too long | > MAX bytes | `invalid-argument`, no write | No write happens |
| Non-admin reads | adminLevel/developerLevel below gate | `permission-denied` (same refusal as requireVerifier), client shows «Предложения» only | No data leaves the server |
| Non-admin marks | same | `permission-denied`, no write | No write happens |
| Corrupt verdict record | shape fails strict parse | Row `{unreadable: true, verdictId}` in place; page continues | Trace, not silence (AD-10) |
| Corrupt view response | client parser sees bad shape/bounds/version | Exception → error state, retry on next open; never a fabricated row | Fail closed |
| Runner preview | question payload from the submission | Static text + options; no timer UI, no charge wording | Parse failure shows an explicit broken-payload note |

</frozen-after-approval>

## Code Map

- `functions/review-decisions.js` (new, pure): `DECISIONS_VIEW_VERSION = "quest-review-decisions-v1"`, mark/comment bounds, `readReviewDecisions(db, data, {documentId})` — verdict page by doc-id cursor → strict parse → join submissions + marks → filter → bounded rows with `hasMore`/`nextCursor`; `writeReviewDecisionMark(db, {uid, nowMs}, data)` — strict validation + upsert into `review_decision_marks`; unreadable-row marker.
- `functions/review-decisions.test.js` (new): rows/join/filter bounds over the Firestore stub; mark validation (mark values, comment bound, unknown verdict, foreign question, overwrite semantics); unreadable verdict row; role refusals exercised through the exported callables.
- `functions/index.js` — `exports.fetchReviewDecisions` / `exports.markReviewDecision` (`requireAuthUid` + `requireVerifier`); no other surface change.
- `functions/arena-lifecycle-views-handler.test.js` — export-set pin re-pinned with the two callables (same «Re-pinned (story 4.17)» pattern as 4.11/4.16).
- Client domain (`shared/feature/quest-authoring/domain`): `ReviewDecision` + `ReviewDecisionMark` models; `ReviewDecisionsRepository` (`fetch(filter, cursor)`, `mark(...)`, `MarkDecisionAccessRefused`); `FetchReviewDecisionsUseCase`, `MarkReviewDecisionUseCase`.
- Client data (`shared/feature/quest-authoring/data`): strict wire parser + DTO (viewVersion pin, row bounds, unreadable flag), `ReviewDecisionsRemoteDataSource`, repository impl, DI.
- `platform/firebase`: `FirebaseReviewDecisionsRemoteDataSource` (two callable names as consts) + DI + `QuestReviewDecisionsCallableNamesTest` (sentinel reading `functions/index.js`).
- Presentation (`android/feature/quest-authoring/presentation`): `DecisionsUiState` (filter enum UNMARKED/VALID/INVALID, mark states SAVING/SAVED), `DecisionsComponent` + Default (QuestionContentParser for the preview payload; permission-denied → proposals-only state) + Placeholder, `DecisionsView` (64dp rows, radius-6 tags, «Логи», static preview, comment field), `DecisionsScreen`, strings ×3 locales.
- App shell: `DrawerSection.LocalSection.Decisions` (emptyMap roles — server gate, ReviewQueue precedent), `LocalConfig.DecisionsRoot`, `Visibility.rootFor` + LOCAL section order, `AppShellTransitions` NavStack, `Labels` (title/icon/config), app-shell strings ×3, `AppShellScreen` route, `DefaultRootComponent.decisionsFactory`, `AppShellPresentationModule` wiring (runCatching → placeholder, ReviewQueue precedent).
- Do not touch: `functions/mvp-isolation.test.js`, `functions/review-fee*.js`, human-review screens and callables (4.19), `scripts/generate-arena-client-fixtures.js`.

## Tasks & Acceptance

**Execution:**
- [x] Server: `review-decisions.js` + suite (rows, filters, marks, refusals, unreadable).
- [x] Server: two callables behind `requireVerifier`; export-set pin re-pinned.
- [x] Client domain: models, repository, use cases.
- [x] Client data + platform: DTO/parser, remote data source, DI, callable-name sentinel.
- [x] Presentation + shell: section, screens, view, strings, wiring.
- [x] Gates: functions `npm test` (docker), gradle suites via `sgw`, ktlint on touched sets.

**Acceptance Criteria:**
- Given verdict records and an admin, when the Decisions tab opens, then question-verdict rows appear with quest/lesson, stage tag, verdict + reason, and the «Логи» block carries rationale, sources, attempt, model, promptVersion and revision — all from the immutable record.
- Given an admin taps ✓ with a comment, when the mark saves, then it lands in `review_decision_marks` (verdicts untouched), the row leaves «Без отметки», and the card reads «Сохранено» (in-flight: «Отправляется…»).
- Given a non-admin, when the view is fetched, then the server refuses (`permission-denied`) and the screen shows only the empty «Предложения» section.
- Given a row is opened, when the preview renders, then the question shows statically with its options and neither a timer nor any charge appears.

## Implementation Notes

- Baseline: merge commit b543a741 (e5 fixpack into e7). Workflow deviation sanctioned by the run mandate (autonomous wave; spec straight to in-progress, change log is the record).
- Row unit is the question verdict (not the record): UX-22 names «the question in the runner preview», and the mark filters are per-admin-audit; the record-level metadata (model/prompt/sources/rationale) rides each of its rows and stays single-sourced in the record.
- «Логи» shows the record's `attempt` (the attempt that produced the verdict); retry errors live in the pipeline doc and are worker-private — recorded here as a conscious bound.
- Paging cursor is the verdict doc id (records carry no timestamp by design; doc ids are deterministic job identities, lexicographic order is stable).

## Spec Change Log

- 2026-10-27, implementation loopbacks: (1) the access-refused signal moved from data to domain (`ReviewDecisionsAccessRefusedException`) — the presentation module does not see the data layer, and the screen is the one that decides on refusal; the Firebase transport translates PERMISSION_DENIED into it, the repository lets it through, the use case turns it into a `Result.failure`. (2) The Firestore stub gained `FieldPath` (documentId-only) so the verdict page orders by doc id the way `index.js` asks it. (3) The pager cursor carries `afterQuestionId` — a row-bound page that stops mid-record resumes inside the same verdict document without loss or repeats. (4) 4.16 loopback: `FetchQuestReviewOutcomesUseCase` had no Koin definition (MyQuests resolves it via `get()` and would fail at runtime) — the definition added alongside this story's own use-case registrations. (5) Callable codes map to honest HTTPS codes (`invalid-argument`/`not-found`/`data-loss`) at the callable boundary.
- 2026-10-27, verification: functions `npm test` — **140/140 suites passed** (new: review-decisions 9 cases over the real callables against the stub: rows+join, filters, overwrite-in-place, mark refusals, unreadable trace + data-loss mark, orphan submission, bounded paging with lossless resume, verifier refusals, named refusals; export-set pin re-pinned with both callables). Gradle via `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew` — app-shell domain jvmTest (VisibilityTest re-pinned: Decisions joins the server-gated LOCAL sections, `rootOf` mapping), quest-authoring domain+data jvmTest (parser strictness suite), platform:firebase testDebugUnitTest (callable-name sentinel + view-version pin), quest-authoring + app-shell presentation testDebugUnitTest (component suite: load/detail/preview key, mark save+fail, refused→proposals-only, broken payload, filter reload) — all green. ktlint: module-wide `ktlint*SourceSetCheck` is red **pre-existing** under the 9.8.0 wrapper (untouched files violate: DefaultQuestCreateComponent 7, DefaultReviewQueueComponent 2, QuestCreateUiState 2, QuestCreateScreen 1, OperationHistoryScreen, FirebaseGiftClaimRemoteDataSource, QuestReviewOutcomeRepositoryImpl, QuestAuthoringMapper, PrivateQuizLinkOutboxWriter, InboxWireParser, LessonErrorReportOutboxWriter) — the same toolchain bucket as the owner-known detekt red (4.6–4.9 precedent); no violation names a file of this story after the owned-files format pass, and untouched violating files were restored byte-identical. detekt — pre-existing toolchain-wide jvm-target 27 (owner).

## Review Triage Log

## Design Notes

- Why a callable and not a Firestore view/read: `review_verdicts` is closed by rules (create-only worker world) and the join (submission + marks) crosses three collections; the review-journal precedent (AD-11) already routes rule-closed admin reads through a callable.
- Why marks are a separate collection and not a field on the verdict: verdicts are immutable by AD-32; an audit opinion that can change must not live in a document that promises it never will.
- Why the client keeps an empty «Предложения» placeholder for refused users: UX-22 fixes the information architecture now (authors see only proposals); 4.22–4.25 fill the section without reshaping the screen.

## Verification

**Commands:**
- `ssh rnsrv 'docker exec schoolquiz-functions-emu sh -c "cd /repo/functions && npm test"'` — full functions gate.
- `ssh rnsrv 'cd /workspace/projects/schoolquiz3.0 && JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :shared:feature:quest-authoring:domain:jvmTest :shared:feature:quest-authoring:data:jvmTest :platform:firebase:testDebugUnitTest :android:feature:quest-authoring:presentation:testDebugUnitTest :android:feature:app-shell:presentation:testDebugUnitTest :shared:feature:app-shell:domain:jvmTest --no-configuration-cache'` — client gates.

**Manual checks (if no CLI):**
- None beyond the suites.
