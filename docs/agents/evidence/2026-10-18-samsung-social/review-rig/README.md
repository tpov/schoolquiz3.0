# 4.10 review rig — tester role proven, queue renders — 2026-10-18 night (Samsung)

Story: epic-authoring-review 4.10 `review-resume-and-timed-question` (device proof of
disconnect/resume). Device: Samsung A14, fresh account UserCXPKJB
(uid dJgxj3m64tRmKIXzOUEoPQCxPkjb) — created after the pm clear for the 8.4 rerun.

Done here (01-review-queue-tester.png):
- tester qualification granted by an admin write:
  `users/<uid>.qualifications.tester = 1` (merge; the doc already carries the qualifications
  map with all six roles zeroed);
- after a relaunch the «Перевірка» screen renders FOR THE TESTER: the filter chip «Усі»,
  counter «0 завдань», empty state «Немає завдань» — the queue machinery talks to
  `fetchReviewAssignmentChanges` fine through the socat transport.

What remains for the 4.10 run (next shift, ~an hour):
1. An author submission in the queue: author a quest through the form on this device
   (the «Створення квесту» form drives fine — see private-quiz-authoring/02) — including
   section/theme/lesson from the MVP smoke course catalog and at least one СКЛАДНЕ (HARD)
   question — then trigger the submit-for-review transition (the curator-transition string
   `qa_curator_transition_review` («На перевірку») is in the quest-authoring resources; if the
   author path lacks the transition, that is the same class of wiring gap as 7.4 and needs a
   decision).
2. The assignment then appears in «Перевірка» for this tester account (the role fixture is
   already live).
3. Run the acceptance: saved review work resumes from confirmed progress; an active timed
   TESTING question follows AD-31 across a force-stop (original deadline continues) and across
   a network drop (svc wifi/data disable) — evidence per the story's naming
   (docs/agents/evidence/<date>-review-resume/).
