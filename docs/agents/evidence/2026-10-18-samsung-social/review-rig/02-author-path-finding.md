# 4.10 author-path finding — local drafts have no submit route — 2026-10-19 morning

Continued from review-rig/README.md (tester role proven on the Samsung account).

The author journey got as far as:
- quest «Review_q410» created through the form (category MVP checks → new quest/section/
  theme/lesson via the «Створити» options) — the draft saves («Збережений квест: Review_q410»)
  and shows in «Мої квести» as «Чернетка»;
- the HARD question set filled (question + 2 options + correct answer; the editor validates
  «Потрібні питання, два варіанти та правильна відповідь»);
- the EASY question set filled (per `qa_error_arena_missing_questions`: submission needs
  easy AND hard in every lesson);
- «Надіслати на арену» tapped → the op `quest_authoring.SUBMIT_ARENA` was enqueued and the
  drain attempted it once → **denied server-side with
  `REVIEW_FEE_INSUFFICIENT: cycle fee 1000 nolics exceeds balance 0`** — the review cycle fee
  (1000 nolics) is real economy, not a defect. Fee fixture: `users/<author>.pointsNolics`.
- After granting 1000 nolics the creation form had reset, and re-selecting proved impossible:
  the «Квест» dropdown in the creation form lists ONLY catalog quests (Створити / MVP smoke
  course) — **a local draft cannot be re-selected to submit it**; the draft card in «Мої
  квести» opens only the question editor (no submit control, no context menu).

Findings for the fix/decision (same class as 7.4's wiring gap):
1. either the creation form must list local drafts in the «Квест» dropdown (then select
   category → quest → the saved hierarchy cascades → «Надіслати на арену»),
2. or the draft card needs a submit-for-review action.

Everything else is ready: the fee fixture recipe (pointsNolics=1000), the tester role fixture
(review-rig/01), the transport, and a full question set in the draft Review_q410 on the
Samsung account dJgxj3m64t… (UserCXPKJB).
