---
id: SPEC-theme-exams
companions:
  - exam-protocol.md
  - brownfield.md
  - ../../../docs/architecture/0005-quest-lifecycle.md
  - ../../../docs/architecture/0007-certificates.md
  - ../../../docs/architecture/0003-question-schema.md
sources: []
---

> **Canonical contract.** This SPEC and the files in `companions:` are the complete, preservation-validated contract for what to build, test, and validate.

# Theme tests and the final exam

## Why

A theme in a home quest is currently just a folder: the player grinds its lessons, each lesson keeps its own three stars, and the theme above them shows nothing. Two assessments are introduced and they are deliberately different things — a **theme test** (контрольная) closes a theme, and a single **final exam** (экзамен) closes the course and is the only issuer of its certificate. There is no moment where the theme is *finished* and nothing that says so. Exams supply that — a single sitting drawn from the whole theme, producing the theme's stars — and, chained upward, the one certificate a course awards at its end. It is a vision to realize, not a gap to patch: the pieces were reserved for it and never built (`SessionMode.EXAM`, `CompletionEffect.IssueCourseCertificate`, ADR-0007).

The exam is deliberately **online and server-scored**, against the app's offline-first grain. An assessment the client grades is an assessment the client can forge, and a certificate a third party is meant to trust cannot rest on a number the phone computed.

## Capabilities

- **CAP-1 — Exam gate on the theme.**
  - **intent:** A player who has answered every easy question of every lesson in a theme correctly — two stars on each, as the player sees it — can start that theme's test; before then the entry point is visible but locked and names which lessons are short.
  - **success:** With one lesson of a five-lesson theme short of two stars, the exam entry is locked and names that lesson; clearing its easy questions unlocks the entry without a restart. A lesson opened with nolics and never played leaves the exam locked.

- **CAP-2 — Server-held exam session.**
  - **intent:** Starting an exam opens a session on the server, which owns the question order, the clock and the score for its whole life.
  - **success:** A client patched to lie about its answers, its timing or its score cannot change the returned result; the result is reproducible from server state alone.

- **CAP-3 — Random draw across the theme, filtered by difficulty.**
  - **intent:** The server serves one question at a time, drawn at random from the questions of every lesson in the theme, restricted to the exam's difficulty.
  - **success:** Two exams of the same theme by the same player draw different question sets; an EASY exam contains no HARD question and vice versa; questions from every lesson of the theme are reachable.

- **CAP-4 — Server-enforced deadline.**
  - **intent:** Each question carries a deadline the server holds; when it lapses, the question is scored as unanswered rather than left open.
  - **success:** A client that goes silent past a question deadline and then submits gets that question scored as a miss, and the exam continues or ends by server decision, not the client's.

- **CAP-5 — Answers accepted, never marked.**
  - **intent:** The player submits an answer and is told only that it was received; nothing reveals correctness, and nothing reveals the correct answer.
  - **success:** No response on the exam path — question, submit, or session state — contains the correct option, the correct ordering, or a per-question verdict.

- **CAP-6 — Result at the end, from the server.**
  - **intent:** When the last question is answered or the exam ends, the server computes the score and returns the finished result; the client renders it and stores nothing it computed itself.
  - **success:** The result screen's score, stars and pass/fail are byte-identical to the server response; deleting local app data and re-reading the exam yields the same result.

- **CAP-7 — Theme stars from the best exam.**
  - **intent:** A theme shows the stars of its best exam result; lesson stars never feed it.
  - **success:** A theme whose lessons are all three-star still shows zero theme stars until an exam is sat; a later worse exam does not lower what the theme shows.

- **CAP-8 — One certificate, earned at the final exam.**
  - **intent:** Passing the course's final exam issues the player its single certificate, which they own and a third party can verify.
  - **success:** No certificate exists while the final exam is unpassed, however many sections are complete; on passing it, exactly one certificate appears in the player's list on a fresh install, and its verification link resolves for a signed-out visitor.

- **CAP-16 — The final exam.**
  - **intent:** A course ends on its own exam — a separate sitting above every theme test, drawing on the course as a whole, and the only thing that issues the certificate. It admits a player who has passed every theme test of every section, and it can be re-sat without limit, once per 23 hours like any other exam.
  - **success:** The final exam is a distinct entry point from any lesson and any theme test; one unpassed theme test anywhere in the course keeps it shut; a failed sitting costs a cooldown and nothing else, and a player may return to it indefinitely at one sitting a day.

- **CAP-9 — Difficulty ladder, between lessons.**
  - **intent:** Hard lesson play unlocks behind a lesson's easy questions being answered correctly, and that gate resolves on the device without waiting for the server.
  - **success:** Clearing a lesson's easy questions opens its hard mode and the next lesson immediately, offline, with no round trip; a theme test contains no such gate, its difficulties being mixed in one pass.

- **CAP-10 — Interruption is a server outcome.**
  - **intent:** Losing connectivity, backgrounding, or killing the app during an exam resolves to a definite server-side outcome, and the player is told which.
  - **success:** An exam abandoned mid-way reaches a terminal state on the server without client cooperation, and reopening the app reports that outcome rather than a blank or a resumable-forever session.

- **CAP-11 — Sequential lessons in a course, or paid out of order.**
  - **intent:** Inside a course catalog free progression is sequential — the next lesson opens when the previous is passed — while nolics open any locked lesson in the course outright.
  - **success:** Lesson 3 cannot be started while lesson 2 is unpassed; clearing lesson 2's easy questions opens lesson 3, on the device and at once. Buying opens whichever lesson was bought, lesson 5 as readily as lesson 3, and leaves every unplayed lesson at zero stars. Buying downloads nothing — the content is already there, brought down in portions by its own provisioning feature.

- **CAP-12 — One attempt per exam per 23 hours.**
  - **intent:** The cooldown belongs to an exam, not to the player: each exam can be sat once per rolling 23 hours, and a player may sit every exam available to them on the same day.
  - **success:** A second start of the *same* exam inside 23 hours is refused with the time it returns, including after a sitting the server ended by timeout; starting a *different* theme's test the same day is never refused; a player sitting an exam at the same hour each day is never turned away.

- **CAP-13 — An exam costs charge, deducted by the server.**
  - **intent:** Sitting an exam spends a whole charge, taken in the same call that opens the session so the price never flickers.
  - **success:** Starting an exam deducts the charge server-side and the player is told how long until it refills; the balance shown after any profile sync is the one that was charged, and an exam that fails to open charges nothing.

- **CAP-17 — Admission is one monotonic record.**
  - **intent:** Each theme a player sits keeps their best easy percent, their best hard percent and whether the theme is passed, written when a session reaches its terminal state; admission to the final exam reads those records and nothing else.
  - **success:** The record only ever improves, so a replayed or out-of-order result cannot lower it; the theme's stars and the final exam's admission are computed from the same record.

- **CAP-18 — What the certificate says.**
  - **intent:** The certificate carries the sitting that earned it — session, percent and date — together with a snapshot of what it attested: the size of the question bank and the pass mark in force at issue. It is valid for one year, after which the final exam is sat again. The public page shows all of it.
  - **success:** A signed-out visitor sees the percent and date of the passing sitting and the bank it was drawn from; a course edited afterwards does not change what an already-issued certificate claims, so the page never states something that was true and stopped being true.

- **CAP-19 — The author knows where they stand.**
  - **intent:** A course carries a certification line from its first minute showing the distance to eligibility, not a verdict at publish time, with one sentence explaining why the number exists.
  - **success:** An author sees "147 of 200" throughout authoring and reaches 200 as a finish they walked to; nobody is told after six months of work that their course does not certify.

- **CAP-21 — Every sitting is kept.**
  - **intent:** Exam sittings are persisted separately from lesson attempts, so how the population fares against the pass mark is measurable from the first day rather than inferred later.
  - **success:** On launch day the server holds, per sitting, who sat which exam, when, at what percent and whether they passed; the pass rate against the 60% bar is a query, not a migration.

- **CAP-20 — Every cooldown in one place.**
  - **intent:** A player carries one cooldown per exam, so they can see all of them at once rather than discovering which lock opened by trying them.
  - **success:** After an evening of ten exams, the next day opens on a single view of what is available now and what returns when.

- **CAP-15 — A theme pays out.**
  - **intent:** A finished theme test awards the player rewards and, where it applies, a placement among other players; it never awards a certificate.
  - **success:** A passed theme test credits rewards and shows the player's standing; no certificate is created until the course's last section closes.

- **CAP-14 — Anti-cheat telemetry.**
  - **intent:** The server records, for every exam, how long each question took and how the player answered it, so patterns can be filtered later.
  - **success:** After an exam, its per-question timings and answers are queryable server-side; no result is blocked, voided or altered by this record.

## Constraints

- **The exam requires connectivity; there is no offline path.** This breaks the app's offline-first default on purpose, and it is why the exam is a new server-owned flow rather than a `sessionMode` flag on the existing local runner.
- **The client is never the scorer.** It does not receive correct answers on the exam path, does not compute the exam's `percentScore`, and does not write its own certificate — ADR-0005 rule 4 and ADR-0007 rule 1.
- **One certificate per course, server-issued and server-signed.** ADR-0007 stands unamended; a theme test is a step toward it, never an issuer.
- **One predicate for "lesson passed", used everywhere.** Every easy question of the lesson answered correctly. `computeHardUnlocked` is close but not identical — it tests all-9 over the 20-question play subset, so for a lesson with more than 20 questions it is the weaker bar and must be tightened rather than reused as-is. "Two stars" is how the predicate is shown to the player, not a second rule; do not re-derive the gate from a star value.
- **The lesson gate resolves on the device, immediately.** Easy questions are scored locally, so two stars opens the next lesson without waiting for the server to return hard results. Only the theme test's gate needs the server.
- **The difficulty ladder lives between lessons, not inside a sitting.** A theme test has no easy phase to clear before its hard questions appear; the two difficulties are mixed in one pass.
- **Draw scope is the hierarchy node.** A theme test draws from everything under its theme, the final exam from everything under its course. One rule, two scopes.
- **A course certifies only with 12 theme tests, each backed by 100 easy and 100 hard questions.** That is 2400 questions in a certifiable course — five times the draw on every sitting, reachable by a determined author rather than only by a team. A course below the bar still teaches, still has theme tests, and publishes normally as a full course; it just awards no certificate.
- **Every theme has a test.** It is not optional per theme.
- **A certifiable course cannot be de-certified by its own author.** The server refuses an update that would drop it below the bar, so no player is ever stranded mid-course by an edit.
- **The course version governs what is being earned.** A player working through a course that updates earns the certificate against the updated course.
- **Certification eligibility is computed, never stamped.** It is a cumulative counter kept in the publication pass — total and hard separately — because nothing in the system ever sees a whole course at once: publication walks one submission at a time and merges. Course creation is where the author *sees* the certification line, not where it is computed.
- **Count by set of question ids, never by increment.** Questions are re-published on every edit through the same merge, so an incrementing counter would credit a 60-question course with 200 after four edits.
- **The course threshold does not belong in submission validation.** `QuestAuthoringValidation`'s `hasEasy && hasHard` validates one submission; a 200- or 100-question rule there would reject every course's first submission. Submission property and course property are different checks.
- **The minimum is split by difficulty, which is what makes it bite.** Each sitting draws 20 from its own bank of 100, so neither the easy nor the hard rung can serve its whole pool — the failure mode where a theme reaches its total on easy questions alone cannot arise.
- **The minimum protects the exam's meaning, not its difficulty.** Pool size does not affect the chance of passing — that is Binomial(20, p) against a 12-of-20 bar and the pool never enters it. What a large pool prevents is the sitting degenerating into recall of a fixed set.
- **The draw index is split by difficulty** so a hard draw does not read the easy half of the course.
- **The certificate reads `passed` and `percentScore`, never stars,** because `computeStars` floors HARD at two stars even for a blank attempt.
- **Admission is a set, not a count.** It compares the ids of passed themes against the course's structure and its structure version; an author adding a theme honestly re-closes admission instead of letting through whoever slipped in first.
- **The draw needs an index built at publication, not at exam start.** Question documents carry only `lessonId`, so a collection-group query cannot narrow to a theme or a course; without an index a theme draw walks every lesson and a course draw walks the whole tree on every start, per player, under a capped instance budget. Publication already traverses the whole tree in one pass with the ancestry in hand — that is where the index is built.
- **Nolics live on the server.** A lesson purchase is a server call, not a local deduction; the balance is in `users/{uid}` and profile sync overwrites the local copy wholesale. Same reason the exam charge is server-side.
- **Redaction is per question type, and two types resist it.** `Ordering` carries its answer as the order of its own items, so the server must shuffle and keep the permutation rather than strip a field. `Survey` scores full marks for any answer, so surveys are excluded from the exam draw entirely — not filtered, excluded.
- **Redaction gets its own wire type.** `QuestionContent`'s invariants forbid a missing correct answer; making the field nullable would throw at construction and silently score ordinary lessons as wrong. The exam feature carries its own redacted type.
- **Every sitting draws 20 questions.** A theme test is 5 easy and 15 hard, mixed and indistinguishable in order; the final exam is 20 hard. The composition is what protects the theme test: easy answers are on the device for offline lessons, so easy marks are readable, and 5 of 20 caps that free share at 25%.
- **Both pass at 60%, held in server config rather than a code constant** and intended to become per-course, set by the author or by an algorithm. Moving it must not cost a release.
- **CAP-5 is load-bearing for the pass mark, not only for feel.** The 60% bar holds because the player never learns which questions they failed; reveal a per-question verdict and retakes become search with feedback, and the bar collapses. Any pixel that changes after submit now costs a certificate.
- **Passing a theme means 60% of one mixed sitting.** There is no separate easy requirement and no easy rung to clear first — that shape was tried and dropped, because one missed easy question would have ended the sitting before a single hard question appeared, at the cost of a charge and a day.
- **Star arithmetic reuses `computeStars`.** An EASY exam reaches at most 2 stars, only a HARD exam reaches 3. No second star formula enters the codebase.
- **The anti-cheat only records.** It does not block, void, flag-to-the-player, or alter a result in this iteration.
- **Buying opens, it never passes.** Nolics unlock access to a lesson — any locked lesson, not only the next — but earn no stars and move no gate. The locked card carries a price and a padlock and no further explanation; the consequence is recorded as a known risk below.
- **The exam cannot be bought at any price.** Nolics buy lessons; the exam has one key and it is earned. This holds because a bought lesson stays at zero stars, so buying a whole theme open still leaves the exam shut.
- **The 23-hour cooldown is server-side and is the real gate.** Charge is device-side motivation and gates nothing the server relies on; it is resettable by clearing app data and that is accepted.
- **Gold never touches the path to the certificate.** Gold buys premium boxes and NFTs and drops from them; it does not interact with progression. No attempt, retake, unlock or cooldown is purchasable with it.
- **Only the final exam issues the certificate.** A theme test pays results, rewards and placement. Completing the last section unlocks the final exam and awards nothing by itself. There is exactly one certificate per course and one way to get it.
- **The existing lesson layer is untouched in behaviour.** Per-lesson stars, `Lesson.averageRating`, and the LEARNING runner keep working exactly as they do; exams and sequential unlock add axes above them.
- **Exams live on themes of home-shelf quests.** Per the ADR-0005 amendment, `QuestType = COURSE` quests are the ones on `home`/`archive`; exams and sequential unlock belong to that track, not to arena or tournament play.

## Non-goals

- No revealing of correct answers, during or after the exam — not even on EASY, where practice reveals them.
- No offline exam, no queued exam, no exam replay from the outbox.
- No per-theme and no per-section certificate; the course awards one, at its end.
- No composite certificate spanning several courses; ADR-0007 defers that to a future `CertificatePath`.
- No section-level exam sitting; the course-level final exam is CAP-16 and is in scope.
- No anti-cheat enforcement rule, threshold, or appeal flow in this iteration.
- No change to how lessons are scored or rated, beyond the sequential unlock gate.
- No new question types and no change to the ADR-0003 payload schema.
- No proctoring, camera, or identity checks beyond the existing Firebase Auth session.
- No rename of life points to charge across shop, profile and legacy price list — see open question 3.

## Success signal

A player finishes the last lesson of a theme, the theme's test unlocks, they spend a charge, sit questions they have not seen in that order, get no hint of how they are doing, and come out with a server-computed score and stars now showing on the theme itself. Sections later, the last one closes and a single certificate appears whose link an employer can open without an account.

## Known risks

- **A player can buy a whole theme open and still find the exam locked.** Nolics grant access, stars come only from playing, and the exam gate reads stars. The card that took the nolics showed a price and a padlock and said nothing about this. Accepted deliberately, and it stays a disappointment rather than a refund problem because nolics are the offline motivational currency — gold is the one bought with real money, and it buys no lessons here.

- **The certificate attests an account, not a person.** With proctoring in non-goals, a verifier learns that this account passed the course. Stated here so the claim made to an employer stays honest.

- **The cooldown, not the charge, is the only limiter on unlimited retakes.** With the cooldown owned by the exam, five charges buy five *different* exams rather than five attempts at one, so the charge limits only the sixth exam in a day. It is a motivational cost, as intended. What actually stands between a partial knower and the certificate is sittings per day: a player who knows 40% of the material passes with 82% probability over 30 sittings, and one who knows 50% with 94% over 10.

- **With unlimited retakes the certificate attests persistence as much as knowledge.** A holder passed once; nothing says they would pass again. Accepted deliberately — the alternative was inventing a way to permanently refuse someone who completed the whole course — but it should be stated rather than discovered by a verifier. It also defuses the opposite risk: a dropped connection costs a cooldown, not months of work.

- **Device-side spending is not implementable as written**, for charge or for nolics. `lifePoints` and `pointsNolics` both live in `users/{uid}` and `ProfileRepositoryImpl` overwrites the local profile with the remote one wholesale, so a local deduction is erased by the next profile refresh — not on a data wipe, on the next sync. The exam charge and the lesson purchase are both server calls; making the wider economy device-side is a separate decision this feature does not need.

## Assumptions

- "Домашние кости" in the transcript is "домашние квесты" — the home shelf, where `QuestType = COURSE` quests already live per ADR-0005.
- Completion chains upward: a theme is done when its exam is passed, a section when all its themes are done, the course when all its sections are done — and that last event unlocks the final exam rather than issuing anything.
- Exam length mirrors the lesson pool (~20 questions) rather than the whole theme, since a theme can hold hundreds of questions.
- The final exam behaves like a theme test one level up — same online session, same redaction, same no-feedback rule, same cooldown — until decided otherwise.
- Its question mix is unsettled; the recommendation on file is hard questions only (open question 1).
- The cooldown is per exam — each (theme, rung) and the final exam each carry their own — and runs from the start of the previous sitting.
- "Every lesson at two stars" means each lesson individually, not an average of two across the theme.
- One charge means 100 points, the value of a heart slot, against 33 for a lesson attempt.

## Open Questions

1. **Does the course carry stars of its own** from the final exam, the way a theme does from its exam? And does a better re-sit change anything once the certificate exists?
2. **Which sync direction governs the balance?** Three directions exist: results bottom-up through the outbox (the anti-cheat feed, exactly as intended), content top-down by delta, and the profile top-down wholesale via `ProfileSyncWorker`. Charge and nolics live in the third, which is why device-side spending does not survive. Confirm spending stays server-side, or change that sync.
3. **What replaces `lifeCharged` as the economic throttle on lesson attempts?** It gates four things, not one — skill points and nolics, the radar activity ratings, and the tournament attempt write. Removing it silently changes tournament standings too. Proposed replacement: `attemptId` idempotency plus a daily cap on paid attempts per lesson. (Replay itself is already stopped by `isNew` and `scoreVerified`, not by this flag.)
4. **Ship order:** theme test first and the certificate later, along the seam this spec already draws — or the inversion, certificate and public verification first, cheapest honest exam second?
5. **Is the theme placement a real leaderboard**, and if so who ranks against whom — the theme, the course, everyone? Or is it just the player's own best shown back to them?
6. **Does the life-points-to-charge rename ride along with exams or ship separately?** It touches the shop, the profile, the server economy and the legacy price list.
7. **Does the EASY exam have a pass mark of its own,** or is it only meaningful as the 100% prerequisite inside the theme pass rule?
8. **The client already caches every question payload locally, correct answers included.** Serving redacted questions online does not stop a player reading the answer out of their own Room database mid-exam. Do exam themes ship as on-demand content (the `Quest.archived` path), is local lookup accepted for now, or does the anti-cheat telemetry cover it?
