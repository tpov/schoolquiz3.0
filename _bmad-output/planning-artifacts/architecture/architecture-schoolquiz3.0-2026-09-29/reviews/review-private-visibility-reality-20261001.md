# Private result visibility — reality lens, 2026-10-01

**Verdict: PASS.** No MAIN change requested.

Scope: [AD-29 and UX-O4/result-visibility](../ARCHITECTURE-SPINE.md) against the owner's explicit «da» confirmation that later show-result changes also apply to previous attempts. No code or runtime audit performed.

- Current server settings govern every attempt, including completed attempts; visibility is not frozen at start. Enabling reveals only each participant's own result, disabling hides it. Author statistics retain their separate authorized scope.
- The change affects the permitted projection only. Stored answers, score, completion fact and consumed attempts remain unchanged.
- Every result read/retry uses current visibility. After an authoritative newer setting, stale responses or cache cannot restore the previously visible participant projection. This is an access rule, not a claim to erase already read information or screenshots; no new offline-result capability is introduced.
- Show-result mutation is correctly removed from unresolved O4. Attempt-limit changes and timeout-result projection remain separate questions. Link/default/timing and course-competition rules are preserved.
- The adapter, permission versioning and redaction remain implementation prerequisites. No new technology, provider or implemented-runtime result is asserted.

The amendment matches the adopted rule and keeps historical scoring separate from current disclosure permission.
