# Private-quiz defaults — reality lens, 2026-10-01

**Verdict: PASS.** No MAIN change requested.

Reviewed only [AD-29 and UX-O4](../ARCHITECTURE-SPINE.md) against the owner's confirmation «ну да» of one attempt, hidden participant result and a link without expiry.

- The three defaults apply to creation of a new individual private quiz: `maxAttempts=1`, `show-result=false`, no expiry. Author selection of another integer limit ≥1, a visible own result or a link deadline remains possible.
- Loading an existing quiz/attempt preserves its saved settings. The amendment does not authorize a migration, reset or retroactive replacement with the new defaults.
- Changing show-result/attempt limit during an active attempt remains a separate unresolved policy. The default decision does not alter accepted link closure, live-attempt completion, timers or competition rules.
- No technology choice, application mutation, test execution or implemented-runtime claim was added.

This is a documentation consistency check, not application acceptance.
