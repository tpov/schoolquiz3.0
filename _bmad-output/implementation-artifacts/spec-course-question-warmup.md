---
title: 'Warm course questions before lesson launch'
type: 'bugfix'
created: '2026-09-27'
status: 'done'
route: 'oneshot'
review_loop_iteration: 2
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Opening a normal lesson in a course can wait for a network fetch because the lesson runner starts from Room and only downloads question content after finding that the local cache is empty. Choosing a course quest currently navigates into its sections without starting that download.

**Approach:** When the user opens a quest from the ordinary Home list of a course catalog, start the existing quest-content sync in the quest-list component's lifecycle scope before navigating, without awaiting it. This warms Room while the user browses; keep the runner's existing cold-cache retry as a fallback.

</frozen-after-approval>

## Implementation Notes

- Confirmed the existing lesson-content sync path writes question content into the local repositories; the runner's cold-cache retry remains the fallback.
- The first attempt to warm the whole selected course was rejected after review because the sync path shares an app-wide gate with runner downloads and could delay the lesson the user actually chose.
- Instead, the visible course lesson list warms only its first currently playable lesson. Later lessons can be warmed when they become playable; unrelated lessons and courses remain on demand.
- The Home course entry does use `QuestType.COURSE` in `QuestListMode.Archive`, but prefetching belongs to the lesson-list screen, where access order is known.
- Dispatch sync to the existing background dispatcher after publishing the loaded UI state. Preserve lesson navigation on sync failure/cancellation; the runner's cold-start fallback remains authoritative.
- Targeted presentation unit tests pass with separate UI/background test schedulers; the new test confirms that a locked next lesson is not warmed and navigation is immediate.
- `:apps:android-next:assembleMvp` succeeds on the VPS with the isolated debug Android user home. Candidate SHA-256: `80c747c82e6e0116e9e381331b9293d42992c3de11d288c5c939b419df211c6f`; signing certificate matches the installed app.
- Installed the candidate with `adb install -r` on Samsung Galaxy A14 `RF8W80MBR3Z`; pulled installed `base.apk` has the same SHA-256, confirming the installed artifact.
- Maestro flow reaches and screenshots the first question. This is the two-question MVP smoke fixture with already cached content, not a cold first-use measurement of the owner's target course; evidence is in `docs/agents/evidence/2026-09-27-course-question-warmup/`.
