---
id: SPEC-theme-exams
updated: '2026-09-30'
companions:
  - exam-protocol.md
  - ../../planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/ARCHITECTURE-SPINE.md
  - ../../planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md
  - ../../../docs/architecture/0003-question-schema.md
  - ../../../docs/architecture/0007-certificates.md
  - ../../planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/assessment-technical-contracts.md
sources: []
---

# Theme tests, final exam and course certificate

## Why

Give course learners a knowledge-checking goal and an annual attestation they can show outside the app. Each theme test records progress toward a separate course final; a successful final earns the certificate. Both assessments and certificate delivery belong to the current MVP alongside ordinary learning.

## Capabilities

- **CAP-1 — Theme-test admission**
  - **intent:** A player can take a theme test after completing every required EASY question in each lesson of that theme.
  - **success:** The lock identifies missing lessons and distinguishes locked access from unpassed content; bought access grants no pass. Exam start waits for confirmed relevant progress and refuses unsynced admission before charging.

- **CAP-2 — Authoritative sitting**
  - **intent:** A player receives one recoverable assessment sitting with a trustworthy result.
  - **success:** The server owns the bank, progress, clock and scoring; restart or retry cannot create another sitting, select a result or extend time. The outcome can be reconstructed from stored evidence.

- **CAP-3 — Uniform assessment draw**
  - **intent:** A player receives a random assessment across the hierarchy being tested.
  - **success:** A theme test mixes 5 EASY and 15 HARD questions; a final uses 20 HARD. Questions are distinct within the sitting, approved and available, with fixed revisions; only the current redacted question is delivered.

- **CAP-4 — Bounded answer time**
  - **intent:** Each assessment question has an enforceable answer allowance.
  - **success:** The first bounded acknowledgement fixes the server deadline and retries preserve it. Late or missing answers score as unanswered; abandonment obtains a server outcome without client cooperation. Every theme/final question gets ordinary HARD answer time, including the theme's EASY questions, with no extra exam shortening.

- **CAP-5 — Receipt without a verdict**
  - **intent:** A player knows an answer was received without learning its correctness.
  - **success:** Submissions return uniform non-correctness receipts; no exam response exposes keys, per-item verdicts, private score digits or codeAnswer. Ordering is safely shuffled; Survey is excluded.

- **CAP-6 — Result and safe study advice**
  - **intent:** A player can see the assessment result and identify lessons to revisit.
  - **success:** After closure, the client renders server percent, pass state, stars and safe advice verbatim. An open result remains pending; advice cannot identify one failed item.

- **CAP-7 — Best theme stars**
  - **intent:** A theme shows the best theme-test result rather than an average of lesson results.
  - **success:** The server maps theme percent linearly to 0..3 stars at tenths precision with half-up rounding. An ordinary worse retake cannot lower the best; confirmed-fault correction is the explicit exception. No theme average is stored or displayed, and pass/fail still uses percent, not rounded stars.

- **CAP-8 — Course certificate**
  - **intent:** A player earns or renews a course certificate by passing the separate final exam, including before the prior certificate expires.
  - **success:** Each successful terminal final atomically creates its own immutable issuance fact; retries fulfil that same fact. The profile displays the latest earned certificate even when its passing score is lower, with explicit pending signing. Earlier attestations remain immutable and independently verifiable; a failed retake issues no certificate and does not replace one. A later correction from failure to pass creates no issuance; a new successful sitting is required.

- **CAP-9 — Ordinary local progression**
  - **intent:** A player can unlock the ordinary HARD or next-lesson path through EASY completion.
  - **success:** The ordinary gate remains local and works offline without waiting for HARD scoring. No easy-first phase, second sitting or difficulty ladder is introduced inside a mixed theme test.

- **CAP-10 — Definite interruption outcome**
  - **intent:** A player can recover an interrupted assessment or see its final outcome.
  - **success:** Connectivity loss, backgrounding and process death preserve the same sitting and deadlines. Recovery cannot pause time or keep an abandoned sitting open forever; the unanswered remainder is recorded.

- **CAP-11 — Free and purchased lesson access**
  - **intent:** A player can open a course lesson through free sequential progression or buy any locked lesson with nolics.
  - **success:** Purchase grants access, not stars or completion. The accepted offline provisional unlock survives restart and resolves through the same operation; quarantine rolls it back visibly. Provisioning is separate, and prices follow the current economy contract.

- **CAP-12 — Per-assessment cooldown**
  - **intent:** A player can see when each theme test and final can next be attempted.
  - **success:** Each user/theme test waits a rolling 24 hours and each user/course final a rolling seven days (168 hours) from terminal closure, including timeout or abandonment. Keys have no difficulty rung or calendar reset; recovering an open sitting consumes no new attempt or debit.

- **CAP-13 — One charge per successful start**
  - **intent:** A player pays the displayed configured charge once when a new assessment starts.
  - **success:** Theme-test start costs 100 charge units and final start costs 200. Start and debit commit together; refusal, retry or response loss creates no second debit. These charges are separate from lesson-access prices.

- **CAP-14 — Quality review signals**
  - **intent:** The product can review question quality using answer/timing evidence and reports.
  - **success:** Statistics or a complaint alone never block, void, revoke or change a result. Confirmed faults use authorized revision, quarantine and attributable correction; no new automatic enforcement is inferred.

- **CAP-15 — Assessment rewards**
  - **intent:** A player receives nolics and XP for theme tests and final exams under confirmed payout rules.
  - **success:** Theme and final NEW-progress coefficients x3/x10 replace ordinary x2; already-paid final progress uses x0.1, with difficulty/time factors separate. Compare against the maximum result already accounted for in rewards per assessment, independently of the latest certificate. Every final attempt is eligible, including failure, with no positive minimum at zero score. Theme first-pass payout remains once per user/theme, atomic with admission and protected from repeat/correction replay. Frozen ordinary EASY/HARD question worth and exact mixed-base/currency arithmetic follow adopted C1 independently of assessment answer time. Corrections preserve any settled payout receipt and its accounted-progress history.

- **CAP-16 — Separate final exam**
  - **intent:** A player who passed all required non-archived themes can take the course final, subject to bank eligibility.
  - **success:** The final draws 20 HARD, defaults to a configured 60% pass mark and permits unlimited retakes at its own cooldown. New starts use the latest approved course; final results are course-scoped, separate from theme admission.

- **CAP-17 — Stable theme progress**
  - **intent:** A player retains one best mixed-sitting result and passed state per theme.
  - **success:** Result and admission update together. Passed themes carry by stable course/theme identity after question edits; new required themes need a pass and archived extras are ignored. Authorized correction may recompute admission in either direction.

- **CAP-18 — Immutable annual attestation**
  - **intent:** Each certificate attests its earning sitting, percent, pass mark and bank snapshot for one year.
  - **success:** Every new successful final has a distinct issuance tied to its sitting and its own fixed dates. A downward correction caused by our question error changes neither an issued identity, signed claims/score, dates, original expiry nor validity on that ground. Correction history is separate; a later certificate never rewrites prior attestations. A corrected pass on a formerly failed sitting does not create an attestation.

- **CAP-19 — Visible bank readiness**
  - **intent:** An author can see a course’s current certification readiness throughout authoring.
  - **success:** Certification needs at least 12 corresponding theme tests, each with at least 100 EASY and 100 HARD distinct approved available questions. Republishing does not inflate counts; history and current availability differ. Below-threshold courses can still publish and teach.

- **CAP-20 — Cooldown overview**
  - **intent:** A player can inspect all theme-test and final-exam availability in one overview.
  - **success:** User-scoped projections cover both assessment kinds without reading private sessions. Unavailable, pending and unknown states are distinct from an elapsed cooldown.

- **CAP-21 — Reconstructable sitting history**
  - **intent:** The product can reconstruct every sitting and explain its corrections.
  - **success:** Selected revisions and private keys, rules, answers, timings and original outcome remain separate from ordinary attempts. Corrections append versions without overwriting original evidence or an issued certificate.

## Constraints

- Theme tests and the final exam are current-MVP work. The theme-level assessment is a контрольная; экзамен means the distinct course final. Every course theme has its theme test.
- Assessments are online and server-scored. Ordinary learning and Arena retain their accepted offline, pause and recovery rules; tournament timing and termination remain independent.
- Each new sitting has exactly 20 scored questions and the configured pass mark defaults to 60%. There is no hidden pretest, unscored addition, EASY gate or per-difficulty rung inside a theme test.
- All required independent checks and approval admit a concrete question revision to the exam bank. A failed new revision leaves a valid approved old one available; confirmed faulty revisions leave new draws. Affected dependencies/translations are rechecked without discarding unrelated valid approvals.
- A theme start requires 5 available EASY and 15 HARD questions; a final additionally requires current course certification eligibility and 20 available HARD. Insufficient pools refuse before spending. Author edits cannot voluntarily break established certification eligibility; authorized quarantine can block new starts.
- New starts bind the latest approved course and applied bank/rule versions; active sittings remain frozen. Final admission compares required stable theme identities, never counts or a client-selected older version.
- Full required EASY coverage is the exam-admission predicate; rounded stars or a perfect arbitrary twenty-question subset do not prove it. Local ordinary progression remains immediate; provisional purchase/progress does not masquerade as server-confirmed exam admission.
- Public exam results expose only aggregates and safe advice. HARD keys, future questions, private session documents, codeAnswer and per-item verdicts never enter client projections or general diagnostics.
- Exam owns certificate issuance, signing and verification; profile consumes a read-only projection. Every successful final, including renewal before expiry, authorizes a distinct immutable issuance at the atomic result/issuance boundary. The latest earned certificate is displayed regardless of score; failed retakes do not issue or replace it. No theme, section or generic client-completion event authorizes issuance. Correction from a formerly failed final to passing issues no certificate; only a new successful sitting can earn one.
- Certificates attest that an account passed, not a verified person or accredited qualification. Existing administrative revocation remains separate; our content error does not revoke or rewrite the issued attestation.
- Ordinary lesson purchases preserve the accepted atomic local unlock/provisional debit/outbox path and visible rollback. The server owns confirmed financial facts; current economy tariffs and identity rules supersede old flat prices or standalone local spending.
- Gold does not directly buy exam admission, an assessment attempt or retake, or a cooldown bypass. This does not redefine the current shared premium/charge economy.
- Account/operation identity, immutable request bodies, confirmation states and compatible non-destructive delivery follow the adopted initiative architecture. A late response cannot update another account or account epoch.
- Implementation and all artifacts stay on VPS. This contract authorizes no runtime, build, service, live financial flow or deployment; acceptance requires fresh evidence for the actual candidate.
- The content of every assessment, result and certificate screen requires separate explicit confirmation against the overall product logic. Approval of appearance does not approve all copy, displayed data, actions or states; existing explicit product decisions remain binding.
- Theme-test and final-exam answer time matches ordinary HARD for every question, with no extra exam shortening; the mixed theme composition and question difficulties remain unchanged. Timer mechanics are in exam-protocol.md.

## Non-goals

- Offline or queued exams, local assessment scoring, or client-written certificates.
- Per-theme, per-section or composite multi-course certificates.
- Mandatory pretest, automatic result invalidation from statistics, or new anti-cheat enforcement.
- A shared tournament/exam state machine, a new ordinary runner, nullable correctness workarounds or new question types.
- Proctoring, personal identity verification or accreditation.
- Repricing already settled assessment rewards or inventing ranking/course-star policies.
- Standalone/offline certificate verification; revisit only if verification availability requirements change.

## Success signal

- Demonstrate ordinary EASY completion → confirmed admission → mixed theme test → all required theme passes → current-bank final → atomic issuance → signed-out verification. Lost responses, duplicate operations and account changes preserve one sitting, one debit and one immutable attestation per successful final.
- Re-earn before expiry with a lower passing score: display the latest certificate, preserve earlier attestations and their individual dates, and prevent delayed older signing from changing that selection. A failed retake issues no certificate and leaves the last earned certificate selected.
- Approve a revision without pretest; quarantine a confirmed error and correct its sitting results. New starts respect available-bank gates, passed stable themes carry across ordinary edits, new themes require a pass, and the issued certificate remains exactly as issued. The protocol defines the acceptance cases.
- Demonstrate server-returned linear theme stars with half-up tenths while pass/fail and best-theme selection stay independent. Apply rolling 24-hour theme and seven-day final cooldowns from terminal closure. Every final attempt is reward-eligible, including failure, without a positive minimum at zero score; confirmed new/repeated-progress coefficients use maximum reward-accounted progress separately from latest-certificate display, including when payout rounding yields zero. Verify the adopted C1 reward arithmetic and C2 correction fixtures, fixed settled rewards, generation-protected admission, C3 confirmed coverage and C4 recovery boundaries; concrete activation prerequisites remain explicit.
- Correct a previously failed final into a passing result: store its correction without creating an issuance or changing the current certificate. A subsequent new successful final earns a certificate normally; already-earned pending issuance remains fulfilable after a correction.
- Give every100-character theme/final question24 seconds, including theme EASY; preserve the ordinary HARD5-second minimum, first-acknowledgement deadline and retry/recovery behavior. Never apply the inherited0.75 assessment timer factor.

## Open questions

- **Q-EX-5:** Which concrete signing provider/algorithm, secret bindings, permitted target environment, numerical cadence/budgets/retention and operational proof satisfy C4 before activation? The bounded worker, Firebase Functions v2 scheduled adapter, fencing and key-rotation boundaries are adopted; no runtime or service activation is authorized.
- **Q-EX-7:** Are optional theme placement and course stars to be built, and under which product rules? Revisit only before those surfaces; no leaderboard or score-derived placement is implied.
