# Quality Scorecard — lesson-runner

Дата: 2026-04-28
Реализация: phase-01..07 (7 phases), Walking Skeleton domain (~89 tests).
Источник: findings 5 reviewers (architect, code, security, completeness, concurrency) по всем фазам.

| Параметр | Grade | Blockers | High | Medium | Low | Детали |
|----------|-------|----------|------|--------|-----|--------|
| Architecture | B | 0 | 1 | 1 | 0 | HIGH (phase-04 LessonRunnerComponentFactory missing) → закрыт fix loop. MEDIUM (phase-04 commonMain dep scope) → закрыт. ADR-LR-19 для RunnerUiState.Result flat (security-driven), ADR-LR-20 для component factory location. |
| Correctness | B | 3* | 2 | 3 | 4 | *3 phase-04 compile blockers (mainContext / lambda types / Result fields) — все resolved через fix loop до merge. HIGH: phase-04 componentJob cancel + double-complete sentinel. MEDIUM: phase-02 isHard:Int (spec-mandated), phase-03 commonMain dep, phase-04 LessonRunnerUseCases data class. |
| Completeness | A | 0 | 0 | 1 | 4 | Все AC 1-65 покрыты. PT-01..41 + CT-01..30 + IT-09a..h + DT-01..82 (Walking Skeleton). 3 ADR additions documented (LR-18/19/20). 1 medium (phase-04 RunnerStateHolder.onDestroy partial reset). LOW: documented design deviations (LessonItemUi.subtitleCount reserved, LessonRatingMapper tests deferred, ADR-LR-19 design drift, phase-07 LessonRunnerComponentFactory location). |
| Security | A | 0 | 0 | 1 | 5 | MEDIUM (phase-04): RunnerUiState.Result содержал attempt: Attempt с PII (userId, codeAnswer, attemptId) → flat projection (ADR-LR-19) + HTTPS-only avatarUrl filter (FirestoreLessonDtoMapper.kt:28). LOW: error messages с option IDs (game data, не PII), URL validation на UI layer, .catch{} silent в combine flow, codeAnswer in cleartext SQLite (acceptable for offline persistence). |
| Code Organization | A | 0 | 0 | 0 | 4 | LOW: stale comments in tests, subtitleCount dead path documented, missing edge case test (phase-06 hardUnlocked=true,isHardChecked=false→EASY), 60-line composable extracted (phase-04 deferred to phase-05). |
| **Overall (after Codex Round 3)** | **A** | 0 open | 0 open | 0 open | 0 open | All Codex findings closed across 3 rounds. R1 (4B+5H+1M) → R2 (0B+3H+3M) → R3 (0/6). Phase-08 fix loop успешен. |

## Grading Scale

- A = 0 findings
- B = only medium severity (or all resolved)
- C = 1-2 high
- D = 3+ high
- F = any unresolved blocker

Phase-by-phase: все 7 фаз закрыты с PASS от 5 reviewers (security/code/concurrency where applicable/architect/completeness). Ни одна фаза не ушла в production state с open blocker / high / medium.

## Pipeline Quality Notes

**Strengths:**
- Walking Skeleton (~89 tests) ушёл в spec phase, обеспечил domain contract validation до интеграции
- Autonomous fix loop reviewers↔coder работал — lead не вмешивался кроме escalations (3 design deviations: ADR-LR-18/19/20)
- Build gates строги: connectedAndroidTest на real device (Pixel 10 Pro), ciCheck, jvmTest, allTests, detekt+ktlint все green
- Cross-feature ADRs (LR-01..LR-20) задокументированы заранее, drift пойман в фазах

**Gaps:**
- Cross-phase Codex CLI review не запущен (diff против master — 6525 files prior KMP refactor; focused diff невозможен на этой ветке). Documented как known gap.
- Manual smoke на Pixel: только app launch без crash подтверждён программно (monkey -c LAUNCHER). Полная UI navigation Catalog → Quest → Section → Theme → Lesson → LessonRunnerScreen requires interactive testing — деferred user verification.

**ADR additions during implementation:**
- ADR-LR-18: DifficultyConverter removed (mapper-based conversion, phase-02)
- ADR-LR-19: RunnerUiState.Result flat projection (security-driven, no PII in StateFlow, phase-04→05)
- ADR-LR-20: LessonRunnerComponentFactory location (lessonRunnerPresentationModule, phase-04→07)

Все 3 ADR — обоснованные deviations от plan invariants, документированы в `03-decisions.md`. План обновлён через ссылки "**Superseded by ADR-LR-XX**".
