# Course tournament access — reality lens, 2026-10-01

**Verdict: PASS.** No findings requiring a change.

Scope: only the [initiative spine](../ARCHITECTURE-SPINE.md) delta against `/tmp/schoolquiz-course-access-20261001/ARCHITECTURE-SPINE.md`: AD-17, UX-O4 and the new UX-O4/access implementation prerequisite. No broad code or runtime audit performed.

- The owner answer «все кто скачал курс + по ссылке» is represented as two alternative paths: users who downloaded the corresponding course, or a valid tournament link. No organizer whitelist, password, guest mode or tariff was added.
- AD-17 preserves tournament registration/qualification, deadlines, private-form protection and existing financial rules. Access is not represented as bypassing those requirements.
- UX-O4 explicitly scopes the no-charge rule to the individual private quiz. Tournament access appears separately, and UX-O4/access preserves tournament financial rules; no free tournament entitlement is inferred.
- The proof/API boundary is honestly unresolved: server confirmation of course download/link, account/course/tournament binding and one common contract are required before the admission adapter. The text does not claim a working download-attestation mechanism, completed implementation or runtime acceptance.
- No technology or provider was introduced. Existing AD IDs and the remaining private-quiz questions remain intact; only the course-tournament audience choice is closed.

The amendment is consistent with the owner's decision and distinguishes the accepted policy from its pending implementation.
