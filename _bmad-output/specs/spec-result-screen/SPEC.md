---
slug: result-screen
derived_from: .memlog.md
companions: []
date: 2026-08-29
---

# Result screen — the foot of it

## Why

The run ends and the screen stops. It says how it went and offers nothing to do about it:
no way into the lesson's discussion, no route onward. The three things the drawing puts
below the ranking are the whole difference between a verdict and a next step.

## Capabilities

- **CAP-1 — Discussion, one row.** A single tappable row at the foot: the word, the count
  of comments and how many are new, an action on the right. Tapping opens the discussion
  that already exists. Not an inline thread — a door.
- **CAP-2 — What to read first.** Under it, a kicker `РЕКОМЕНДУЕМ СНАЧАЛА` in the accent,
  the lesson that closes the gap this attempt exposed, and a chevron. The lesson is already
  chosen by `resultAdvice()`; nothing new has to decide it.
- **CAP-3 — Two pinned actions.** `ЕЩЁ РАЗ` left, `СЛЕДУЮЩИЙ УРОК` right, fixed to the
  bottom rather than scrolling away under the content.

## Constraints

- Blocks separate by fill step, never by a stroke. Direction 4A was chosen because a 1px
  white-alpha edge reads grey at this palette and chops the screen into boxes.
- The screen does not scroll: everything above the pinned actions fits 812dp.
- Values come out of the drawing, not the prose that accompanies it. Where they disagree,
  the drawing wins.

## Non-goals

- No inline comment thread on this screen — the row is a door, not the room.
- No new ranking, chart or figure. What is there stays.

## Success signal

A player who scored badly can reach, in one tap and without scrolling, either the lesson
that explains what they missed or the people discussing it.

## Open questions

1. **`СЛЕДУЮЩИЙ УРОК` has nowhere to go.** Nothing in the code resolves "the lesson after
   this one" — the runner knows its own lesson and no successor. Options: the next by
   `order` in the same theme; the first unfinished in the theme; or drop the action until
   there is a real destination.
2. **"3 новых" needs a last-read marker.** Counting new comments requires storing when this
   player last opened the discussion for this lesson. Nothing stores it. Options: keep it
   locally per device; store it on the profile; or show the total only and no "new".
