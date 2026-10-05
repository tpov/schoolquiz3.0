# Individual private-quiz link — reality lens, 2026-10-01

**Verdict: PASS.** No findings requiring a MAIN change.

Scope: current [AD-29, UX-O4 and UX-O4/link](../ARCHITECTURE-SPINE.md) against the owner's «и так и так» answer and subsequent «Дать закончить текущую попытку». This is a narrow document reality check, not a code/runtime audit.

- Both adopted lifetime choices are retained: no final date until manual disable, or an author-selected expiry with earlier manual disable available.
- New starts require an enabled link and either no expiry or server time strictly before expiry. The check is atomic with attempt allowance; retry returns the old receipt rather than granting a new attempt. No new client-owned authority is inferred.
- The later owner decision is reflected explicitly: disable/expiry blocks new starts only; an already server-started attempt may finish under its original identity, deadlines and AD-31. Start and access closure have one authoritative server order. No extended timer, replacement start or deferred resume is inferred. This question is no longer marked open; changes to other settings such as show-result/attempt limit remain separate.
- Default selection, automatic renewal, re-enable and rotation remain unassigned.
- The individual private quiz remains separate from the course competition. Its configurable link lifetime does not change the tournament's qualification interval or championship admission; its no-charge rule does not alter competition funds.
- A common server adapter contract remains a prerequisite. The amendment introduces no provider or technology and makes no claim that link settings, authorization or runtime acceptance already exist.

The accepted lifetime and active-attempt decisions are preserved without choosing unrelated defaults or settings policy.
