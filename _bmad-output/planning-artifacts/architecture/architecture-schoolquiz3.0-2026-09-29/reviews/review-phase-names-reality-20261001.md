# Phase names and admission interval — reality lens, 2026-10-01

**Verdict: PASS.** No fixes required in the reviewed MAIN amendment.

Scope: current initiative glossary, AD-2/17/31 and UX-O4/access against the owner's latest decision. Narrow code evidence: `functions/tournament-policy.js:156–159`; no broader runtime audit or execution.

- Both course-admission paths (downloaded course OR link) require server time in `[qualification.startsAtMs, qualification.endsAtMs)`. AD-17 closes new entries at the end of qualification without turning the link into finalist authorization. Existing qualification and finalist rights remain intact.
- The observed policy checks the same inclusive-start/exclusive-end interval for a new qualification attempt. Its preceding existing-attempt branch returns the original replay receipt. The amendment distinguishes new entry from history/replay and does not contradict that ordering.
- That code establishes the existing time-boundary convention only. It does not prove the new downloaded-course/link authorization adapter; UX-O4/access correctly requires one shared server proof/API contract before implementation.
- User-facing «турнир» means qualification, «чемпионат» means the final phase including bracket/matches, and «соревнование» denotes the aggregate. Technical `tournament*`/event identities, paths and existing lifecycle owners remain unchanged. Historical HTML copy does not override the new terminology.
- The interval is not applied to the separate individual private quiz. Its link/access questions and no-charge rule remain separately scoped; tournament fund and other financial rules are preserved.
- No new technology, executed test, implemented link capability or runtime PASS is claimed.

The amendment records the adopted policy and terminology while keeping its implementation boundary explicit.
