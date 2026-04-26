# Plan Review Pass-2 — quizzes-screen

## Verdict
REJECT

## Pass 1 findings status

| Finding | Severity | Status | Evidence |
|---------|----------|--------|----------|
| F1.1 | blocker | NOT FIXED | `0-spec.md:518` makes AC#29 DI registration, but `plan/README.md:156` says `AC#29 | LessonPlaceholder...`; DI is shifted to `plan/README.md:157` as AC#30. AC#29-39 descriptions do not match canonical numbering. |
| F1.2 | high | PARTIAL | Phase-03 deps now align at `plan/README.md:34` and `phase-03/overview.md:114`, but `plan/README.md:36` says Phase-05 depends on Phase-04 while `phase-03/overview.md:176` still says Phase-04 and Phase-05 can run in parallel. |
| F1.3 | blocker | PARTIAL | Resolved/deferred entries exist at `plan/README.md:172-178`, but delegation remains: `phase-03/overview.md:175 — frontend-dev должен явно проверить`, `phase-02/frontend.md:14 — verify в текущей версии`, `phase-06/overview.md:116 — может требовать @OptIn`. |
| F1.4 | blocker | RESOLVED | `phase-05/frontend.md:32-36` routes from `QuizzesScreen` via `component::popToLevel`; all child signatures include `onSegmentClick`: lines `51`, `79`, `104`, `125`, `144`. |
| F1.5 | blocker | RESOLVED | `phase-04/overview.md:68-72` selects Option A; `phase-04/frontend.md:197-211` defines local `QuestToDisplayItemMapper.kt`. |
| F1.6 | high | PARTIAL | `phase-06/overview.md:11` says UI layer dispatch, but `phase-06/frontend.md:45` still calls `component.onShareClick(quest)` and `phase-06/frontend.md:72` leaves a frontend-dev decision. |
| F1.7 | high | PARTIAL | Dashboard marks device requirement at `plan/README.md:32-33,37`; phase overviews still mostly build APKs only, e.g. `phase-06/overview.md:102-103` uses `assembleDebugAndroidTest` with no device-run row. |
| F2.1 | blocker | RESOLVED | Required grep returned `0` matches for `06-api-contract.md §`. |
| F2.2 | blocker | RESOLVED | `phase-03/frontend.md:102` is a one-line signature; implementation is replaced by canonical pointer at `phase-03/frontend.md:107`. |
| F2.3 | blocker | RESOLVED | Assertion-code grep over `phase-*/tests.md` returned `0` matches. |
| F2.4 | high | PARTIAL | Some refs were added, but not each invariant has concrete file:line: `phase-04/frontend.md:11-15` lacks refs, and `phase-07/frontend.md:10` cites `docs/invariants.md` without a line. |

## Self-check results

| Check | Expected | Actual | Pass? |
|-------|----------|--------|-------|
| Fenced kotlin/java/groovy | 0 | 0 | Yes |
| Assertion code in tests.md | 0 | 0 | Yes |
| `§N` canonical refs | 0 | 0 | Yes |
| AC#1..AC#39 in README | 39 | 39; all AC#1..AC#39 present | Yes |

## NEW blockers

(none)

## Summary

The mechanical self-checks pass, but the plan is still not implementable as a corrected Pass 2. Two original blockers remain: canonical AC descriptions are still misnumbered from AC#29 onward, and REQUIRES items are still partly delegated to implementers.