# Real course: open first question on Samsung

- Result: PASS, 1/1 Maestro scenario, 67 seconds end to end.
- Date: 2026-09-28.
- Device: Samsung SM-A145F (Samsung A14), Android 15, serial `RF8W80MBR3Z`.
- App: `com.tpov.schoolquiz.mvp`, version `0.1.0-isolated`, installed APK last updated 2026-09-27 23:02 local time.
- Code under test: commit `51da9d5b78dc4a4219cad74b13dec849a76a3e84` (`fix(sync): bound cold lesson content sync`).
- Scenario: synchronize a demo course, navigate Home → Archive → course → section → theme → first lesson, then open the first question. It does not answer or submit the question.
- Course fixture: `Математика: полный школьный курс`; the first lesson has 40 seeded questions, and the runner selects/shuffles 20. The UI screenshot therefore need not show a fixed question ID.
- Evidence: `report.xml`, `maestro.log`, and `real-course-first-question-uk.png` in this directory.

## What this confirms

The ordinary lesson start reads the question list from Room: `StartLessonAttemptUseCase.kt:64-68` calls `QuestionRepository.observeByLesson(...).first()`, and `QuestionRepositoryImpl.kt:17-18` delegates to the local data source. The network path is not used on this successful local-cache start. A cold-content recovery path is separate and only runs after local initialization fails.

## Timing caveat

The flow emitted `COURSE_FIRST_QUESTION_MS=5487`, measured from immediately before Maestro's tap command until its accessibility hierarchy reported the runner screen. This includes Maestro command and hierarchy-detection overhead; it is not an app-internal latency measurement. The log shows the actual tap at 01:03:16.377 and hierarchy-change detection at 01:03:19.835, so it still does not isolate Room, parsing, navigation, and rendering. No production performance defect or fix is claimed from this run. A device-side trace or app-internal spans are still needed for an honest breakdown.

The app remained connected to the isolated demo backend for catalog setup. This run demonstrates the successful local Room start path, but it is not a full airplane-mode/offline acceptance test.
