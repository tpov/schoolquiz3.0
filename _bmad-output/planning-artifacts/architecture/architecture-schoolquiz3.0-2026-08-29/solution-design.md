# Solution design — theme tests, final exam and certificate

Reconciled 2026-09-30. The stable AD contract is [ARCHITECTURE-SPINE.md](ARCHITECTURE-SPINE.md); its prior finalized review and the scoped renewal amendment have separate evidence. This companion explains the accepted design and does not claim implementation or runtime validation. [EXAM-TECH-1](assessment-technical-contracts.md) C1, C2 arithmetic, C3 and C4 are adopted with [fixture oracles](assessment-contract-fixtures.json); C2 settled-award economics is confirmed as preservation without adjustment; concrete execution activation remains gated.

## Learning, assessment and tournament boundaries

Ordinary lessons and Arena retain local progress, offline play and ordinary pause/recovery rules. EASY questions can score locally. HARD keys stay on the server, so an offline HARD result waits for server confirmation.

Theme tests and the final exam have their own online server-owned lifecycle. The server controls the draw, clock, answer commitment and result. Existing tournaments also have server-owned sessions, but they retain their own continuous clocks and foreground/worker settlement.

The owner chose common basic contracts with separate lifecycle management. Scoring arithmetic, answer representation, redaction and operation identity can be reused through narrow adapters. An exam acknowledgement timer does not become a tournament timer, and building exams does not require migrating tournaments to a new engine.

## A sitting from start to result

A theme test opens after confirmed completion of all required EASY questions in its lessons. Buying lesson access does not count as completion. C3 defines publication's immutable required-EASY manifest of exact canonical question/revision tokens and learning ingestion's validated successful-evidence union across attempts. A lesson requiring more than twenty tokens is completed across multiple attempts; counts, stars, forged digits and an upload ACK are insufficient.

The existing local attempt/outbox transaction captures manifest/revision and raw answer provenance without adding an offline ticket. Local ordinary progression stays immediate. Before charging, a new exam start checks the current required manifest and confirmed projection generation/receipt barrier atomically. Incomplete, not-yet-confirmed and unavailable authority have distinct refusals. Unchanged exact tokens carry across manifests, revised/new tokens need proof, while already-passed stable theme carry for final admission remains unchanged. Legacy evidence is accepted only when its immutable provenance is conclusive.

A new start uses the current published course version and checks eligibility, cooldown, available approved questions and charge. A theme test selects five EASY and fifteen HARD questions in mixed order and costs 100 units. A final selects twenty HARD questions and costs 200. Both default to a 60% pass mark. These rules are server configuration captured for the sitting.

Start and debit are one idempotent operation. A refused draw takes nothing; a lost response recovers the same open sitting without another charge. Selected question revisions, private keys and applied rules remain immutable for that sitting.

Question image is prepared before text. The first acknowledgement that the text rendered fixes the answer deadline; subsequent acknowledgements return it. Waiting for acknowledgement has a bounded gap beginning at creation or the preceding answer receipt. That gap does not truncate an already acknowledged question's full allowance. Every ThemeTest and FinalExam question uses the ordinary HARD answer-time rule, including the five EASY theme positions: seconds=max(5,roundHalfUp(chars*0.24)), with the ordinary character-count convention and image equivalent100. Do not multiply by examFactor0.75. Difficulty labels, scoring and 5-EASY/15-HARD composition remain unchanged; capture the timer rule with the sitting.

Answers receive receipts without correctness feedback. The first answer for a question wins; retries cannot replace it or advance another question. The exam sweeper alone writes the terminal outcome, including abandonment and early finish requests. The unanswered remainder scores as unanswered. Terminal closure, including timeout or abandonment, starts a rolling 24-hour cooldown for that user/theme or a rolling 168-hour (7-day) cooldown for that user/course final. Recovery of the same open sitting changes neither charge nor cooldown anchor; no calendar reset is implied.

The server returns aggregate percent, pass/fail, stars and safe lesson advice. It never returns codeAnswer, per-item correctness or keys. Client presentation renders the result without recalculating it. ThemeTest stars are linear from 0 to 3 using the overall percent: server starsTenths=roundHalfUp(30*p/100). The tenths/half-up convention gives 60%=1.8, 95%=2.9 and 100%=3.0. Pass/fail still uses percent and its applied threshold; theme best-result policy does not change.

## Progress across course updates

There is one admission projection per user/theme, holding one best mixed-sitting result and passed state. The terminal transaction records the result and projection together. Ordinary retakes can improve the projection; a confirmed error correction is the separate authorized lowering path.

Passed themes carry by stable courseId/themeId when their question content changes. Only a newly added required theme requires another theme test. Extra archived theme ids do not disqualify a player. A new final exam uses the latest approved course version; an already started exam keeps its frozen bank. Content-update carry does not exempt an actually erroneous result from the correction process.

The final exam is a distinct course-level sitting and has separate result/cooldown records. Passing the last theme or completing a section opens eligibility; it does not issue a certificate.

## Approval, question errors and available banks

Every concrete question revision becomes eligible after its required independent checks and approval. This is the same rule for the first bank and later additions. There are twenty scored questions and no mandatory hidden pretest questions.

Statistics and reports identify content that needs another review. They do not prove an error and do not automatically remove a question. A confirmed error is removed from new draws by the authorized content process; the stored revision remains available for audit and frozen active banks.

C2 corrections preserve the original twenty slots and append an attributable result version. Map cumulative authorized exclusions to distinct original slots, reproduce retained digits from frozen evidence/scoringVersion, then calculate percent=floor(sum(floor(100*(digit-1)/8))/retainedN). Excluded slots leave numerator and denominator; unanswered retained slots still count as digit1. Compare with the captured pass mark, apply the settled star mapping and safe-advice rules, and CAS the correction head. Commit that head and recomputed theme admission/best atomically, or atomically mark a durable correction-generation barrier pending with the head. A materializer writes only the matching current result/correction version and generation. New final start checks current/applied correction generation and readiness in its start/charge transaction, so pending recomputation or unavailable state refuses before charging and stale passed data cannot admit a player. Another valid pass can still suffice; an already started sitting remains frozen.

All excluded yields UNSCORABLE_ALL_EXCLUDED with no numeric result; missing/corrupt evidence yields CORRECTION_PENDING_EVIDENCE. Neither can preserve a known-invalid current admission proof or fabricate a score. No automatic refund or cooldown reset is added. An already settled award keeps its original nolics/XP receipt and reward-accounted maximum exactly; no top-up, recoupment, reset or advance follows corrected score. A first-ever corrected theme pass can issue its existing first-pass reward once if the receipt is absent, atomically with admission from the original frozen basis and b0.

Certification still requires twelve theme tests, each with at least 100 approved available EASY and 100 HARD questions. Counts use distinct question identities, so publishing edits never inflates them. Historical existence and current usable availability are shown separately.

An author cannot publish an update that voluntarily breaks established certification eligibility. A curator must still be able to remove a confirmed bad revision. If that reduces the current bank below certification thresholds, new final exams wait for a repaired approved bank. Theme tests retain their own smaller 5-EASY/15-HARD draw requirement. Started sittings and issued certificates are preserved.

## Assessment rewards

For both nolics and XP, the confirmed NEW-progress coefficient is ordinary2, ThemeTest3 or FinalExam10; the assessment value replaces ordinary NEW2 rather than multiplying the whole reward. Already-accounted/repeated final progress uses0.1. Keep the ordinary weighted marginal score bands and currency conversion, with separate difficulty tariff and allocated-time factor. A HARD final therefore uses effective NEW30 on its new bands and REPEATED0.3 on previously accounted bands, never a uniform ×30 over the sitting.

The server keeps a durable reward-accounted maximum per user/assessment, independent of theme best/admission and the latest certificate. Compute the new/accounted split from this high-water mark. A lower displayed certificate, another sitting, expiry or renewal does not reset it. Commit the payout receipt and updated accounted maximum atomically, including when rounding makes the amount zero, so retries cannot repay NEW progress.

Theme reward remains first-pass-only with a stable user/theme identity and atomic admission update. Earlier failed unpaid theme attempts leave its initial reward-accounted progress at zero. Every terminal final attempt, including failure and retakes, is eligible under the formula without a positive minimum; each sitting settles idempotently. Certificate issuance still requires an originally successful final. C1 now fixes the mixed reward basis and exact arithmetic; its fixture oracle must be integrated into later Kotlin/JS tests. C2 preserves already-settled reward receipts and accounted maximum without adjustment.

C1 freezes the approved canonical pool's ordinary worth source and per-difficulty time totals at start, independent of locale, the random draw, current elapsed time or later content edits. Ordinary EASY/HARD worth uses its own 0.36/0.24 seconds-per-character rules, both with the ordinary floor/half-up convention; every assessment answer can still use the separate HARD timer. Theme reward-time means cap to5 EASY and15 HARD, final to20 HARD: B=(TE+3*TH)/720. It is not a forty-question reward basis.

From integer aggregate p and accounted b, use P=c*max(W(p)-W(b),0)+r*W(min(p,b)), then R=P*B. Round XP from R and nolics from R/10 independently, once each; do not round bands or derive one resource from the other. C1 specifies checked integer/rational arithmetic and failure for invalid/unknown basis before spending.

## The certificate remains what was issued

Only a successful final-exam terminal result authorizes the server to issue a signed certificate for the course. It includes the earning sitting, percent, date, pass mark and bank snapshot, with its original one-year validity. The public verification endpoint is available without signing in. Every successful final, including a renewal, commits terminal success and the immutable logical issuance fact atomically with durable signing/publication work. Signing happens outside the transaction; pending is explicit and retries keep issuance identity, earning result version, claims, key identity and dates, regardless of a later correction.

The owner explicitly decided that a later downward correction caused by our question error preserves the issued certificate exactly. Its signed score, claims, identity, dates, expiry and validity on this ground do not change. The corrected result has its own history; it neither rewrites the certificate nor extends its term.

A correction that changes a formerly failed final to passing does not authorize a new certificate for that sitting; only a new successful final sitting can earn one. Issuance already earned at original successful terminal closure, including pending signing, remains unchanged.

The public attestation keeps its own immutable data rather than reading mutable profile/course fields. Verification binds a key identity; rotating keys must preserve the material needed to verify older signatures.

A new final exam can renew the certificate before or after expiry under the existing admission, charge and cooldown rules. Every new successful sitting gets a distinct certificate and its own one-year dates frozen from that earning outcome; replay fulfills the same issuance. Default display uses the latest authoritative earning outcome even if its passing score is lower, while older attestations remain immutable in history. Failure creates no issuance and leaves the current certificate in place. The server fixes total earning order with the terminal result; signature completion order cannot select or replace the latest projection, and the latest pending issuance is explicit. Theme best-result/admission and other existing administrative revocation grounds remain separate.

The certificate attests an account's successful sitting. No proctoring, personal identity verification or accreditation is implied.

## Existing code and remaining work

Shared Kotlin scoring, the server assessment scorer, redaction and tournament lifecycle code already exist. The implementation should reuse their current contracts rather than repeat the August extraction tasks as new work. Assessment still needs its own sitting/admission/draw/certificate integration and complete acceptance.

C4 fixes the single fenced/CAS worker capability, bounded durable sweep/wrap, and recovery from crashes, overlapping work, expired fences and poison records. An isolated controlled driver invokes the capability for tests/demo; production uses the existing Firebase Functions v2 onSchedule wake-up adapter. Scheduler delays do not extend deadlines. Signing verifies an artifact generated outside the transaction and CAS-publishes the first valid artifact under issuance/digest/fence. Key rotation retains verification history and signing capability for pending facts bound to old keys.

All planning and artifacts remain on VPS. Nothing in this design starts services, builds an app, runs financial operations or activates Scheduler/PubSub. Before activation, choose the concrete signing provider/algorithm, secret/environment bindings, cadence/budgets/retention and prove the C4 interruption/recovery runbook.

Core arithmetic, coverage, reward basis/rounding and recovery contracts are adopted. Remaining prerequisites are concrete Q-EX-5 activation details and implementation parity/runtime evidence. C2 arithmetic and settled-award economics are closed. Optional theme placement and course stars remain deferred; screen contents still need their separate confirmation.

Final admission projection acceptance: a new valid theme sitting commits while a correction materializer is pending; the older materializer cannot erase that new pass. Terminal and correction writes coordinate the same full aggregate generation, and stale publication retries.
