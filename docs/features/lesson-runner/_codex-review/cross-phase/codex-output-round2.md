Reading prompt from stdin...
OpenAI Codex v0.125.0 (research preview)
--------
workdir: /home/Programming/Android/schoolquiz4.0
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, $TMPDIR, /home/tpov/.codex/memories]
reasoning effort: xhigh
reasoning summaries: none
session id: 019dd15c-2051-7e03-914f-573a035e5944
--------
user
You are an adversarial code reviewer for the lesson-runner feature.

Apply 3 lenses in sequence:
1. Realist — does the code do what spec says? Are AC 1-65 from 0-spec.md actually covered?
2. Skeptic — what edge cases, race conditions, error paths are missed? What assumptions are wrong?
3. Architect — are layer boundaries respected? Are cross-feature ADRs (LR-01..LR-20) enforced? Is DI wiring correct?

Inputs:
- Diff: /home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch (6638 lines, lesson-runner-only changes)
- Spec: docs/features/lesson-runner/0-spec.md (1234 lines, contains Feature Domain Contract + AC 1-65)
- Implementation summary: docs/features/lesson-runner/implementation.md
- ADRs: docs/features/lesson-runner/03-decisions.md (ADR-LR-01..LR-20)

Focus on cross-phase integration issues — not per-phase code style. Specifically check:
- DI chain: questionSchemaModule → lessonRunnerDataModule → lessonRunnerDomainKoinAdapter → lessonRunnerPresentationModule, no duplicate bindings
- Walking Skeleton domain (~89 tests) NOT modified beyond rename
- ADR-LR-09 C1: lessonRunnerDomainKoinAdapter in data/androidMain
- ADR-LR-16: LessonRunnerRootComponent interface in lesson-runner/presentation, NOT core/navigation
- ADR-LR-17: quizzes-screen → lesson-runner one-way; reverse blocker
- ADR-LR-18: DifficultyConverter removed (mapper-based)
- ADR-LR-19: RunnerUiState.Result flat projection (no PII)
- ADR-LR-20: LessonRunnerComponentFactory in presentation module, not duplicated

Severity: BLOCKER / HIGH / MEDIUM / LOW. Always cite file:line. Output structured findings list.

deprecated: `[features].collab` is deprecated. Use `[features].multi_agent` instead.
Enable it with `--enable multi_agent` or `[features].multi_agent` in config.toml. See https://developers.openai.com/codex/config-basic#feature-flags for details.
hook: SessionStart
hook: SessionStart Completed
hook: UserPromptSubmit
hook: UserPromptSubmit Completed
codex
I’ll use the `kotlin-diagnostics` skill here since this is explicitly a cross-phase Kotlin/KMP defect hunt. I’m going to anchor the review in the spec, ADRs, and patch first, then verify suspicious points against the actual files so findings have concrete `file:line` citations.
exec
/bin/bash -lc "sed -n '1,220p' /home/tpov/.codex/skills/kotlin-diagnostics/SKILL.md" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
---
name: kotlin-diagnostics
description: Агент-поисковик багов для Kotlin / Compose Multiplatform / Android / Spring / KMP / Backend. Может запускать системные команды, инструментировать проект, анализировать логи, но не исправляет код без прямого подтверждения.
---


Ты — интеллектуальный агент-диагност, способный выявлять ошибки в проектах на Kotlin / Compose Multiplatform / Android / JVM / Spring / Backend.  
Твоя цель — максимально точно и быстро локализовать проблему, не внося изменения в код до явного разрешения пользователя.

================================================================================
# 0. EXECUTION CONFIDENCE RULES (NO PER-STEP CONFIRMATION)

Ты НЕ обязан спрашивать разрешения для промежуточных действий.  
Ты выполняешь все диагностические шаги автоматически, **без вопросов**, включая:

- запуск системных команд (`gradle`, `docker`, `adb`, `logs`)
- пересборки
- перезапуск контейнеров
- чтение/анализ логов
- временную инструментировку (добавление debug-логов, таймеров, трассировки)
- сканирование файлов
- выполнение тестов
- выполнение скриптов
- просмотр состояния системы

Эти действия выполняются **автоматически, без запросов**, если они не меняют код проекта.

## Но:

Ты обязан ОСТАНОВИТЬСЯ перед финальным внесением правок в проект:

- перед изменением файлов кода,
- перед удалением файлов,
- перед изменением конфигураций,
- перед совершением любых необратимых операций.

И спросить разрешение в форме:

> “Готов применить фикс. Скажи OK / Fix / Done / Исправь — чтобы я внёс изменения.”

================================================================================
# 1. DIAGNOSIS WORKFLOW (STRICT LOGIC)

Когда пользователь говорит “найди баг”, ты выполняешь последовательные фазы.

## Фаза 1 — Анализ проекта (statical scanning)
Проанализируй:

### Kotlin / KMP / Compose ошибки:
- неправильное использование remember
- бесконечные recompositions
- неправильная инициализация state
- корутинные ошибки (race, cancellation, deadlock)
- утечки ComponentContext в Decompose
- неправильные dispatchers
- platform types → NPE risks
- неправильные sealed branch matchings
- suspend вызовы не в корутине
- ошибки в DI (Kodein/Inject)
- shared flow без replay
- мутабельность состояния в UI
- неправильное управление жизненным циклом

### Backend / Spring:
- транзакции
- lazy-loading ошибки
- JPA mapping проблемы
- циклические Spring DI зависимости
- дублирующиеся beans
- неправильные nullable поля
- неверная конфигурация Docker сервисов

### Multiplatform:
- общая логика KMP
- несоответствия между JVM/Native/JS
- IO и concurrency ошибки

### Common:
- NPE / !! misuse
- unreachable code
- dead code
- логические ошибки
- неверные зависимости
- неправильные импорты


## Фаза 2 — Автоматический запуск системных команд
Ты МОЖЕШЬ запускать команды **без подтверждения**, если они не разрушают проект.

### Build
- `./gradlew build --stacktrace`
- `./gradlew test`
- `./gradlew assembleDebug`
- `./gradlew :app:build`

### Logs
- `docker compose logs`
- `docker logs <container>`
- `adb logcat`

### Docker
- `docker compose up -d`
- `docker compose restart`
- `docker ps`

### System
- `ls`, `cat`, `ps`, `kill`
- `./scripts/*`
- `df -h`, `free -m`

Эти команды используются только для диагностики.


## Фаза 3 — Интеллектуальная инструментировка (TEMP ONLY)
Ты можешь автоматически:

- вставлять временные debug-логи
- добавлять println
- временно логировать входы/выходы use-cases
- логировать состояние Component
- добавлять таймеры
- трассировать навигацию Decompose
- логировать exceptions через try/catch
- включать debug-флаги в конфигурации

НО:

### ❗ ВСЕ изменения — временные
### ❗ Код НЕ модифицируется окончательно
### ❗ Ты НЕ правишь бизнес-логику


## Фаза 4 — Runtime анализ
После запуска:

- анализируй stacktrace
- связывай его с местами в коде
- ищи root cause:
    - NPE
    - IllegalState
    - Uninitialized
    - coroutine cancellation
    - lifecycle misuse
    - неправильные параметры
    - конфликт версий библиотек
    - нехватка токенов/конфигураций
    - ошибки в последовательности вызовов


## Фаза 5 — Финальный вывод (Bug Localization)

В конце дай строго:

### 1) Problem summary
короткое описание, что сломано.

### 2) Root cause (конкретная строка/модуль/компонент)
объяснение корневой причины.

### 3) Why it happens
цепочка событий → ошибка.

### 4) Minimal Reproduction
если возможно — путь к повторению.

### 5) Proposed fix (PATCH, DIFF)
предложи конкретный diff:

```diff
--- old.kt
+++ new.kt
@@
  <fix>
```

Но НЕ применяй его автоматически.

### 6) Instruction
Напиши:

> “Скажи OK / Done / Fix / Исправь / Примени — чтобы я применил изменения”.

================================================================================
# 2. FIX APPLY RULES (MULTILANGUAGE & FLEXIBLE)

Ты применяешь изменения **ТОЛЬКО**, если пользователь говорит фразу, значение которой = *«да, применяй фикс»*.

РАЗРЕШЁННЫЕ ТРИГГЕРЫ:

### English
- ok
- okay
- yes
- yes apply
- fix
- fix it
- apply
- apply patch
- done
- do it
- go ahead
- make it
- confirm

### Russian
- ок
- да
- давай
- хорошо
- пофикси
- исправь
- примени
- примени патч
- сделай
- фиксируй
- запускай
- готово

### Semantic approval
Любая фраза со смыслом:

I’ll keep this as a review-only pass: evidence gathering, contract tracing, then findings. No code edits.
  → Read spec, ADRs, implementation summary, and patch shape
  • Trace DI/navigation/state contracts in actual files
  • Check AC coverage and integration gaps
  • Produce severity-ranked findings with file lines
exec
/bin/bash -lc 'wc -l docs/features/lesson-runner/0-spec.md docs/features/lesson-runner/03-decisions.md docs/features/lesson-runner/implementation.md docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
  1235 docs/features/lesson-runner/0-spec.md
   773 docs/features/lesson-runner/03-decisions.md
   131 docs/features/lesson-runner/implementation.md
  6638 docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch
  8777 total

exec
/bin/bash -lc 'rg -n "''^##? |''^### |''^AC |Acceptance|ADR-LR|C1|MUST|must|LessonRunnerRootComponent|DifficultyConverter|RunnerUiState|questionSchemaModule|lessonRunnerDataModule|lessonRunnerDomainKoinAdapter|lessonRunnerPresentationModule|LessonRunnerComponentFactory" docs/features/lesson-runner/0-spec.md docs/features/lesson-runner/03-decisions.md docs/features/lesson-runner/implementation.md' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
docs/features/lesson-runner/implementation.md:1:# Implementation — lesson-runner
docs/features/lesson-runner/implementation.md:7:## Summary
docs/features/lesson-runner/implementation.md:11:## Phases Completed
docs/features/lesson-runner/implementation.md:16:| Phase-02 | Persistence — Migration v3→v4 | PASS (5/5 reviewers) | ADR-LR-18: DifficultyConverter removed |
docs/features/lesson-runner/implementation.md:19:| Phase-05 | Compose UI (LessonRunnerScreen + 12 composables) | PASS (5/5 reviewers) | ADR-LR-19: RunnerUiState.Result flat projection (security-driven) |
docs/features/lesson-runner/implementation.md:21:| Phase-07 | Composition Root + Smoke | PASS (4/5 reviewers; concurrency N/A) | ADR-LR-20: LessonRunnerComponentFactory in presentation module |
docs/features/lesson-runner/implementation.md:23:## Review Verdicts
docs/features/lesson-runner/implementation.md:33:Все findings закрыты автономно через reviewer↔coder loop. Lead вмешался только для 3 design escalations (ADR-LR-18/19/20).
docs/features/lesson-runner/implementation.md:35:## Changed Files
docs/features/lesson-runner/implementation.md:37:### NEW Modules (3 Gradle modules)
docs/features/lesson-runner/implementation.md:43:### NEW Files in existing modules (~12)
docs/features/lesson-runner/implementation.md:50:### Modified Files (~20)
docs/features/lesson-runner/implementation.md:58:### Deleted Files
docs/features/lesson-runner/implementation.md:63:Phase-02 deletion: DifficultyConverter.kt + MT-05 tests (ADR-LR-18 supersedes).
docs/features/lesson-runner/implementation.md:65:## Test Coverage
docs/features/lesson-runner/implementation.md:78:## Build Validation
docs/features/lesson-runner/implementation.md:89:## Smoke
docs/features/lesson-runner/implementation.md:95:## ADR Additions During Implementation
docs/features/lesson-runner/implementation.md:99:| LR-18 | 02 | DifficultyConverter removed; mapper-based Difficulty↔Int conversion in data layer | Room KMP 2.7+ rejects unused converters; entity uses Int isHard, mapper handles conversion. Plan invariant overview.md:129 superseded. |
docs/features/lesson-runner/implementation.md:100:| LR-19 | 04→05 | RunnerUiState.Result uses flat projection instead of attempt: Attempt aggregate | Security: attempt contained PII (userId, codeAnswer, attemptId) — minimal exposure principle in public StateFlow. 06-api-contract.md:408 + frontend.md Pattern Invariant superseded. |
docs/features/lesson-runner/implementation.md:101:| LR-20 | 04→07 | single<LessonRunnerComponentFactory> in lessonRunnerPresentationModule, NOT in AppApplication | Phase-04 already established single<> binding; phase-07 plan duplication would cause Koin override warning. Per 06-api-contract.md:374, factory binding stays in feature presentation module. |
docs/features/lesson-runner/implementation.md:105:## Remaining Issues / Known Gaps
docs/features/lesson-runner/implementation.md:121:## Quality Scorecard
docs/features/lesson-runner/implementation.md:125:## Pipeline Statistics
docs/features/lesson-runner/03-decisions.md:3:authors: architect-high-level (ADR-LR-01..LR-07, ADR-LR-16), architect-component (ADR-LR-08..15)
docs/features/lesson-runner/03-decisions.md:7:# Architecture Decisions: Lesson Runner
docs/features/lesson-runner/03-decisions.md:9:<!-- HL_SECTION_START: ADR-LR-01..LR-07 (architect-high-level writes here) -->
docs/features/lesson-runner/03-decisions.md:11:## ADR-LR-01: lesson-runner/domain → lesson:domain (cross-feature import)
docs/features/lesson-runner/03-decisions.md:15:### Context
docs/features/lesson-runner/03-decisions.md:25:### Decision
docs/features/lesson-runner/03-decisions.md:31:### Alternatives Considered
docs/features/lesson-runner/03-decisions.md:42:### Consequences
docs/features/lesson-runner/03-decisions.md:48:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:54:## ADR-LR-02: lesson-runner/domain → question:domain (cross-feature import)
docs/features/lesson-runner/03-decisions.md:58:### Context
docs/features/lesson-runner/03-decisions.md:67:### Decision
docs/features/lesson-runner/03-decisions.md:73:### Alternatives Considered
docs/features/lesson-runner/03-decisions.md:79:### Consequences
docs/features/lesson-runner/03-decisions.md:85:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:91:## ADR-LR-03: lesson-runner/domain → app-shell:domain (cross-feature import)
docs/features/lesson-runner/03-decisions.md:95:### Context
docs/features/lesson-runner/03-decisions.md:101:### Decision
docs/features/lesson-runner/03-decisions.md:107:### Alternatives Considered
docs/features/lesson-runner/03-decisions.md:113:### Consequences
docs/features/lesson-runner/03-decisions.md:118:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:124:## ADR-LR-04: lesson-runner/domain → shared/core/question-schema (core import)
docs/features/lesson-runner/03-decisions.md:128:### Context
docs/features/lesson-runner/03-decisions.md:137:### Decision
docs/features/lesson-runner/03-decisions.md:145:### Alternatives Considered
docs/features/lesson-runner/03-decisions.md:151:### Consequences
docs/features/lesson-runner/03-decisions.md:154:- `Difficulty` требует `@Serializable` для `QuizzesConfig.LessonRunner` (см. ADR-LR-06)
docs/features/lesson-runner/03-decisions.md:156:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:162:## ADR-LR-05: TopParticipant location — устранение bidirectional coupling (BLOCKER #2)
docs/features/lesson-runner/03-decisions.md:166:### Context
docs/features/lesson-runner/03-decisions.md:174:`lesson-runner:domain` уже импортирует `lesson:domain` (ADR-LR-01). Если `lesson:domain` добавит `Lesson.top3: List<TopParticipant>` — возникнет обратный import `lesson:domain → lesson-runner:domain`. Это **bidirectional coupling** — blocker по Invariant 3 (`docs/invariants.md:25`).
docs/features/lesson-runner/03-decisions.md:178:### Decision
docs/features/lesson-runner/03-decisions.md:197:### Alternatives Considered
docs/features/lesson-runner/03-decisions.md:209:### Consequences
docs/features/lesson-runner/03-decisions.md:217:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:223:## ADR-LR-06: Difficulty @Serializable — QuizzesConfig.LessonRunner сериализация (BLOCKER #5)
docs/features/lesson-runner/03-decisions.md:227:### Context
docs/features/lesson-runner/03-decisions.md:239:### Decision
docs/features/lesson-runner/03-decisions.md:251:### Alternatives Considered
docs/features/lesson-runner/03-decisions.md:258:### Consequences
docs/features/lesson-runner/03-decisions.md:265:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:271:## ADR-LR-07: LessonPlaceholder replacement strategy — full atomic replacement
docs/features/lesson-runner/03-decisions.md:275:### Context
docs/features/lesson-runner/03-decisions.md:289:### Decision
docs/features/lesson-runner/03-decisions.md:303:### Alternatives Considered
docs/features/lesson-runner/03-decisions.md:311:### Consequences
docs/features/lesson-runner/03-decisions.md:321:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:323:Если lesson-runner реализация задерживается или отменяется — нет fallback: тап на урок не работает. Reversal = восстановить `LessonPlaceholder*` файлы из git history + откатить ADR-LR-07. Стоимость reversal: средняя, но git history доступен. Атомарная замена правильная ставка для новой фичи без production saved state.
docs/features/lesson-runner/03-decisions.md:325:### Producer/Consumer boundary — SUPERSEDED by ADR-LR-16
docs/features/lesson-runner/03-decisions.md:327:> ⚠️ Этот раздел описывал первоначальный подход через `android/core/navigation/`. Codex CLI plan-round-1 выявил, что этот подход создаёт circular Gradle dependency: `core/navigation → lesson-runner/presentation` (для RunnerUiState/RunnerEvent типов) + `lesson-runner/presentation → core/navigation` (для interface implementation). Cycle = build failure. Актуальное решение — **ADR-LR-16**: interface и factory живут в `lesson-runner/presentation`, прямой импорт из `quizzes-screen/presentation`.
docs/features/lesson-runner/03-decisions.md:331:## ADR-LR-16: LessonRunnerRootComponent interface location — lesson-runner/presentation
docs/features/lesson-runner/03-decisions.md:335:### Context
docs/features/lesson-runner/03-decisions.md:337:`plan/phase-04/backend.md:18-44` содержал задачу поместить `LessonRunnerRootComponent` interface в `android/core/navigation/`. При анализе типов выявлена circular Gradle dependency:
docs/features/lesson-runner/03-decisions.md:340:core/navigation → lesson-runner/presentation   (RunnerUiState, RunnerEvent типы для interface)
docs/features/lesson-runner/03-decisions.md:346:`LessonRunnerComponentFactory` fun interface с тем же планом в `core/navigation` имела идентичную проблему: return type `LessonRunnerRootComponent` обязывает `core/navigation → lesson-runner/presentation`.
docs/features/lesson-runner/03-decisions.md:348:### Decision
docs/features/lesson-runner/03-decisions.md:354:| `LessonRunnerRootComponent` interface | `android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt` |
docs/features/lesson-runner/03-decisions.md:355:| `LessonRunnerComponentFactory` fun interface | `android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt` |
docs/features/lesson-runner/03-decisions.md:363:### Alternatives Considered
docs/features/lesson-runner/03-decisions.md:365:**Вариант A — core/navigation (изначальный план, ADR-LR-07 original section)**:
docs/features/lesson-runner/03-decisions.md:374:- (-) `LessonRunnerScreen` composable всё равно ожидает typed `LessonRunnerRootComponent` — presenter слой не упрощается
docs/features/lesson-runner/03-decisions.md:377:**Вариант C — RunnerUiState/RunnerEvent в shared/core**:
docs/features/lesson-runner/03-decisions.md:382:### Consequences
docs/features/lesson-runner/03-decisions.md:385:- `DefaultQuizzesComponent.createChild` для `QuizzesConfig.LessonRunner` вызывает `factory.create(ctx, lessonId, mode)` → возвращает typed `LessonRunnerRootComponent`.
docs/features/lesson-runner/03-decisions.md:386:- Koin: `LessonRunnerComponentFactory` single binding в `apps/android-next/AppApplication.kt` — canonical: `06-api-contract.md §LR-9a`.
docs/features/lesson-runner/03-decisions.md:388:- Validation grep после phase-04 implementation: `rg "interface LessonRunnerRootComponent" android/feature/lesson-runner/presentation/src` — ожидается 1 match.
docs/features/lesson-runner/03-decisions.md:390:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:398:<!-- CMP_SECTION_START: ADR-LR-08..15 (architect-component writes here) -->
docs/features/lesson-runner/03-decisions.md:400:## ADR-LR-08 — KotlinxSerializationQuestionContentParser location
docs/features/lesson-runner/03-decisions.md:412:- Binding: `single<QuestionContentParser> { KotlinxSerializationQuestionContentParser() }` в новом `questionSchemaModule`.
docs/features/lesson-runner/03-decisions.md:418:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:420:Если parser нужно переместить в `lesson-runner/data` (например специфическая логика только для этой фичи) — перемещение требует обновить Koin module (`questionSchemaModule` → `lessonRunnerDataModule`) и все tests. Умеренная стоимость. Если `Question` schema добавит 5-й тип — parser нужно обновить в `shared/core/question-schema/` с exhaustive `when`; compile-time catch гарантирован.
docs/features/lesson-runner/03-decisions.md:422:### Migration Plan (Phase-01 task)
docs/features/lesson-runner/03-decisions.md:432:## ADR-LR-09 — Koin lambda binding strategy для providers
docs/features/lesson-runner/03-decisions.md:440:**Decision**: **Option C** — wrapper interfaces в `shared/feature/lesson-runner/domain/src/commonMain/…/provider/` (C1 fix: domain — единственный потребитель; interfaces принадлежат слою который их использует):
docs/features/lesson-runner/03-decisions.md:446:Koin adapter (`lessonRunnerDomainKoinAdapter`) — `shared/feature/lesson-runner/data/src/androidMain/…/di/` (C1 fix: adapter в data, не в domain/androidMain):
docs/features/lesson-runner/03-decisions.md:449:// lessonRunnerDataModule (data/androidMain)
docs/features/lesson-runner/03-decisions.md:454:// lessonRunnerDomainKoinAdapter (data/androidMain) — adapts interfaces to function types
docs/features/lesson-runner/03-decisions.md:466:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:472:## ADR-LR-10 — AppDatabase migration strategy v3 → v4
docs/features/lesson-runner/03-decisions.md:481:**TypeConverters**: `DifficultyConverter` и `TopParticipantListConverter` оба `@ProvidedTypeConverter` + `.addTypeConverter()` в Room builder (паттерн из `StringSetConverter` / `PersistenceModule.kt:24`).
docs/features/lesson-runner/03-decisions.md:483:**Risk Mitigations** (C14: удалён claim "нулевой риск"):
docs/features/lesson-runner/03-decisions.md:491:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:497:## ADR-LR-11 — Компонент для lesson card с StarRating + HARD checkbox
docs/features/lesson-runner/03-decisions.md:517:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:523:## ADR-LR-12 — Унификация поля `value` vs `raw` в value classes
docs/features/lesson-runner/03-decisions.md:534:**Compile gate** (C13): `./gradlew :shared:feature:lesson-runner:domain:jvmTest` должен быть зелёным после rename. Если тесты ломаются — rename не завершён (не "почти готово").
docs/features/lesson-runner/03-decisions.md:536:**Test churn specifics** (C13): файлы, которые потребуют изменений:
docs/features/lesson-runner/03-decisions.md:547:**Spec typo** (C13): `0-spec.md:100` пишет `sourceId.raw` — это spec-опечатка; после rename фактически будет `sourceId.value`. Spec не обновляется (minor typo), implementation следует renamed field.
docs/features/lesson-runner/03-decisions.md:552:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:558:## ADR-LR-13 — `lastModifiedAt` в Attempt для Firestore sync
docs/features/lesson-runner/03-decisions.md:572:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:578:## ADR-LR-14 — Формализация ADR-0003 Amendments A-D
docs/features/lesson-runner/03-decisions.md:599:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:605:## ADR-LR-15 — Lesson rating fields naming и типы
docs/features/lesson-runner/03-decisions.md:622:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:628:## ADR-LR-17 — Compose composition exception: quizzes-screen рендерит LessonRunnerScreen через ChildStack
docs/features/lesson-runner/03-decisions.md:632:**Context**: `QuizzesScreen.kt` содержит exhaustive `when(active)` dispatch block для `ChildStack<QuizzesConfig, QuizzesChild>`. При добавлении `QuizzesChild.LessonRunner` (ADR-LR-07) нужно вызвать `LessonRunnerScreen(child.component)` — `@Composable` функция из `android/feature/lesson-runner/presentation`. Это создаёт прямой import `android/feature/quizzes-screen/presentation → android/feature/lesson-runner/presentation`.
docs/features/lesson-runner/03-decisions.md:638:**Связь с ADR-LR-16**: `LessonRunnerRootComponent` interface живёт в `lesson-runner/presentation` (не в `core/navigation` — отменено из-за cycle, per ADR-LR-16). `QuizzesScreen.kt` получает `child.component: LessonRunnerRootComponent` из ChildStack и передаёт его в `LessonRunnerScreen`. Таким образом ADR-LR-17 расширяет разрешение ADR-LR-16: тот же модуль `lesson-runner/presentation`, дополнительный artifact — `@Composable` screen function.
docs/features/lesson-runner/03-decisions.md:642:- `LessonRunnerRootComponent` interface — тип параметра `LessonRunnerScreen` (уже разрешён ADR-LR-16, упомянут здесь для полноты)
docs/features/lesson-runner/03-decisions.md:655:- Импортируется **только** `@Composable` screen function + `LessonRunnerRootComponent` — NOT use cases, NOT repositories, NOT internal sealed interfaces, NOT component implementation classes
docs/features/lesson-runner/03-decisions.md:660:  # Допустимые совпадения: только LessonRunnerScreen и LessonRunnerRootComponent
docs/features/lesson-runner/03-decisions.md:669:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:677:## ADR-LR-18 — DifficultyConverter удалён: mapper-based conversion вместо TypeConverter
docs/features/lesson-runner/03-decisions.md:681:### Context
docs/features/lesson-runner/03-decisions.md:683:Phase-02 добавил `DifficultyConverter` (`@ProvidedTypeConverter`) для конвертации `Difficulty ↔ String` в Room. После реализации выяснилось, что ни одна Entity не хранит поле типа `Difficulty` напрямую — `LessonAttemptEntity.isHard: Int` (spec-mandated primitive). Room KMP 2.7+ `validateTypeConverters()` отклоняет любой `@ProvidedTypeConverter`, тип которого не используется ни одной Entity, выбрасывая `IllegalArgumentException: Unexpected type converter` при открытии БД.
docs/features/lesson-runner/03-decisions.md:687:### Decision
docs/features/lesson-runner/03-decisions.md:689:**Удалить `DifficultyConverter.kt` полностью.** Конвертация `Difficulty ↔ Int` выполняется в mapper слое:
docs/features/lesson-runner/03-decisions.md:698:`DifficultyConverter` не регистрируется ни в `@TypeConverters`, ни в Room builder. `TypeConvertersPhase02Test.kt` MT-05 тесты удалены.
docs/features/lesson-runner/03-decisions.md:700:Plan invariant `overview.md §Pattern Invariants` строка про `DifficultyConverter` — superseded этим ADR.
docs/features/lesson-runner/03-decisions.md:702:### Alternatives Considered
docs/features/lesson-runner/03-decisions.md:704:- **Оставить DifficultyConverter + изменить Entity на `Difficulty` тип** — нарушает spec AC#52 ("isHard: Int для safer migration validation"). Отклонено: spec mandates Int.
docs/features/lesson-runner/03-decisions.md:707:### Consequences
docs/features/lesson-runner/03-decisions.md:709:- `DifficultyConverter.kt` удалён
docs/features/lesson-runner/03-decisions.md:715:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:719:## ADR-LR-19 — Phase-04: RunnerUiState.Result flat projection (security)
docs/features/lesson-runner/03-decisions.md:724:### Decision
docs/features/lesson-runner/03-decisions.md:726:`RunnerUiState.Result` использует flat-поля (`percentScore`, `mode`, `completedAt`, `hardUnlocked`, `bestStarsRawTenths`) вместо `attempt: Attempt` aggregate из `06-api-contract.md:408`.
docs/features/lesson-runner/03-decisions.md:728:### Rationale
docs/features/lesson-runner/03-decisions.md:730:Phase-04 security review (task #23) обнаружил что `attempt: Attempt` в публичном `StateFlow<RunnerUiState>` содержит PII: `userId`, `codeAnswer`, `attemptId`. Flat projection — security best practice (minimal exposure): UI получает только поля, необходимые для рендеринга, без доступа к идентификаторам пользователя и raw code answers.
docs/features/lesson-runner/03-decisions.md:734:### Supersedes
docs/features/lesson-runner/03-decisions.md:739:### Consequences
docs/features/lesson-runner/03-decisions.md:741:- `RunnerUiState.Result` имеет flat-поля; Phase-05 UI обращается к `state.mode`, `state.percentScore.raw` напрямую
docs/features/lesson-runner/03-decisions.md:742:- Phase-06 и последующие фазы используют flat-поля `RunnerUiState.Result` — не `attempt: Attempt`
docs/features/lesson-runner/03-decisions.md:745:### Alternatives Considered
docs/features/lesson-runner/03-decisions.md:750:## ADR-LR-20 — LessonRunnerComponentFactory binding location: lessonRunnerPresentationModule (Phase-04 accepted deviation)
docs/features/lesson-runner/03-decisions.md:755:### Decision
docs/features/lesson-runner/03-decisions.md:757:`single<LessonRunnerComponentFactory>` binding живёт в `lessonRunnerPresentationModule` (`android/feature/lesson-runner/presentation/src/main/.../di/LessonRunnerPresentationModule.kt:37`), а **не** inline в `AppApplication.kt startKoin` как предписывалось `06-api-contract.md:374`.
docs/features/lesson-runner/03-decisions.md:759:### Rationale
docs/features/lesson-runner/03-decisions.md:761:Phase-04 HIGH finding (task #21): binding добавлен в `lessonRunnerPresentationModule` в рамках fix loop. Binding корректен: при регистрации `lessonRunnerPresentationModule` в composition root factory автоматически доступна через Koin graph. Поведение идентично spec — `NoBeanDefinitionFoundException` при навигации на LessonRunner не возникает.
docs/features/lesson-runner/03-decisions.md:765:### Consequences
docs/features/lesson-runner/03-decisions.md:768:- `AppApplication.kt` регистрирует `lessonRunnerPresentationModule` → factory доступна транзитивно
docs/features/lesson-runner/03-decisions.md:769:- Будущие разработчики **не должны** добавлять `single<LessonRunnerComponentFactory>` в `AppApplication.kt` — это создаст Koin duplicate binding exception
docs/features/lesson-runner/03-decisions.md:771:### Supersedes
docs/features/lesson-runner/0-spec.md:8:# Feature Specification: Lesson Runner — экран прохождения урока
docs/features/lesson-runner/0-spec.md:10:## Source
docs/features/lesson-runner/0-spec.md:17:## Requirements
docs/features/lesson-runner/0-spec.md:19:### Functional Requirements
docs/features/lesson-runner/0-spec.md:23:1. **Тап на Lesson** в `LessonListComponent` (фича `quizzes-screen`) — заменяем существующий push `LessonPlaceholderComponent` на push `LessonRunnerRootComponent(lessonId, mode)`, где `mode` определяется состоянием HARD checkbox в `LessonListComponent` (default = EASY если не отмечен). Без отдельной обложки урока: тап → сразу первый вопрос. — [USER DECIDED]
docs/features/lesson-runner/0-spec.md:187:   > **Amendment 2026-04-26 (design phase, user-approved)**: `Lesson.ratingCount` изменён с `Int?` на `Int = 0` (non-nullable, default 0) — align с Quest.averageRatingCount pattern (`shared/feature/quest/domain/.../model/Quest.kt:69`). Семантика: missing field = 0 (нет оценок), null более не используется. Resolution applied per `docs/features/lesson-runner/03-decisions.md ADR-LR-15`.
docs/features/lesson-runner/0-spec.md:194:   - `android/feature/lesson-runner/presentation/` — Decompose `LessonRunnerRootComponent` + sub-components per вопрос-type, Compose UI screens, DI Koin module.
docs/features/lesson-runner/0-spec.md:201:### Non-Functional Requirements
docs/features/lesson-runner/0-spec.md:217:## Scope
docs/features/lesson-runner/0-spec.md:219:### In Scope
docs/features/lesson-runner/0-spec.md:234:### Explicitly Out of Scope
docs/features/lesson-runner/0-spec.md:253:## User Decisions
docs/features/lesson-runner/0-spec.md:287:## Server-Side Context
docs/features/lesson-runner/0-spec.md:291:### Server-Side Issues / Required Server Work (контракт, реализация — отдельные задачи)
docs/features/lesson-runner/0-spec.md:301:## Search Criteria for Research
docs/features/lesson-runner/0-spec.md:305:### Existing infrastructure (re-use mapping)
docs/features/lesson-runner/0-spec.md:334:### Legacy reference
docs/features/lesson-runner/0-spec.md:357:### Architecture invariants
docs/features/lesson-runner/0-spec.md:367:### Domain model / data contract
docs/features/lesson-runner/0-spec.md:377:### Completeness check
docs/features/lesson-runner/0-spec.md:389:## Primary User Journeys
docs/features/lesson-runner/0-spec.md:394:   - State changes: push `LessonRunnerRootComponent(lessonId, mode=EASY)` → загружает `Question`s урока, фильтрует EASY, выбирает random subset 20 → state `Loading → Ready` → отображение первого вопроса.
docs/features/lesson-runner/0-spec.md:413:   - State changes: push `LessonRunnerRootComponent(lessonId, mode=HARD)`. FLAG_SECURE включается. Random subset из HARD pool, разные коэффициенты таймера.
docs/features/lesson-runner/0-spec.md:454:   - State changes: push `LessonRunnerRootComponent` → state `Loading → InitFailed(EmptyPool)`.
docs/features/lesson-runner/0-spec.md:499:## Feature Domain Contract
docs/features/lesson-runner/0-spec.md:501:### Terms / Entities / Value Constraints
docs/features/lesson-runner/0-spec.md:583:### Business Rules / Invariants / Guards
docs/features/lesson-runner/0-spec.md:650:### Repository Interfaces (фиксируется явно для Phase 3.8)
docs/features/lesson-runner/0-spec.md:683:### Use Cases (фиксируется явно)
docs/features/lesson-runner/0-spec.md:762:### State / Decision Rules
docs/features/lesson-runner/0-spec.md:766:### Error / Recovery Rules
docs/features/lesson-runner/0-spec.md:778:### Domain Test Scenarios (для Walking Skeleton, Phase 3.8)
docs/features/lesson-runner/0-spec.md:921:## Delegated Decisions Summary
docs/features/lesson-runner/0-spec.md:946:## State Matrix
docs/features/lesson-runner/0-spec.md:948:### Matrix 1: Score 0-9 за один ответ по типу
docs/features/lesson-runner/0-spec.md:959:### Matrix 2: Stars per attempt formula
docs/features/lesson-runner/0-spec.md:974:### Matrix 3: bestStars per lesson (max по своим попыткам)
docs/features/lesson-runner/0-spec.md:986:### Matrix 4: Когда писать attempt в Room
docs/features/lesson-runner/0-spec.md:999:### Matrix 5: Rating prompt visibility
docs/features/lesson-runner/0-spec.md:1007:### Matrix 6: Sworn-fold / onResume / abort
docs/features/lesson-runner/0-spec.md:1017:### Matrix 7: Timer per вопрос
docs/features/lesson-runner/0-spec.md:1024:### Matrix 8: Pool selection
docs/features/lesson-runner/0-spec.md:1034:## Acceptance Criteria
docs/features/lesson-runner/0-spec.md:1036:### Navigation flow
docs/features/lesson-runner/0-spec.md:1038:1. [ ] GIVEN пользователь на `LessonListComponent` тапает урок (без HARD checkbox или checkbox=false) THEN push `LessonRunnerRootComponent(lessonId, mode=EASY)`; FLAG_SECURE НЕ включается; первый вопрос отображается
docs/features/lesson-runner/0-spec.md:1039:2. [ ] GIVEN пользователь с `hardUnlocked=true` тапает урок с включенным HARD checkbox THEN push `LessonRunnerRootComponent(lessonId, mode=HARD)`; FLAG_SECURE включается; первый вопрос отображается с HARD-стилизацией фона
docs/features/lesson-runner/0-spec.md:1044:### Score / codeAnswer correctness
docs/features/lesson-runner/0-spec.md:1058:### Stars and progress
docs/features/lesson-runner/0-spec.md:1068:### Timer
docs/features/lesson-runner/0-spec.md:1075:### Lifecycle / FLAG_SECURE / dialogs
docs/features/lesson-runner/0-spec.md:1087:### Attempt save / Room
docs/features/lesson-runner/0-spec.md:1094:### Result screen
docs/features/lesson-runner/0-spec.md:1103:### HARD unlock checkbox
docs/features/lesson-runner/0-spec.md:1109:### Empty / error states
docs/features/lesson-runner/0-spec.md:1117:### Code / DI / invariants
docs/features/lesson-runner/0-spec.md:1149:## Invariant Check (from docs/invariants.md)
docs/features/lesson-runner/0-spec.md:1161:## Constraints (from PROJECT-CONTEXT.md)
docs/features/lesson-runner/0-spec.md:1165:- **Decompose Components** — pattern из `home-and-my-quests/03-decisions.md` ADR-CMP-51. `LessonRunnerRootComponent` + child components per вопрос-type.
docs/features/lesson-runner/0-spec.md:1173:## ADR Amendments Required
docs/features/lesson-runner/0-spec.md:1175:### ADR-0003 (Question schema) — четыре правки нужны для согласования
docs/features/lesson-runner/0-spec.md:1195:## Resolved Decisions (закрыты в spec dialogue)
docs/features/lesson-runner/0-spec.md:1215:## Open Items for Phase-01 (Implementation)
docs/features/lesson-runner/0-spec.md:1227:## Open Questions for Research (open для research phase)

exec
/bin/bash -lc 'rg -n "''^diff --git|''^'"\\+\\+\\+ b/|"'^--- a/|DifficultyConverter|LessonRunnerRootComponent|RunnerUiState|lessonRunnerDomainKoinAdapter|lessonRunnerPresentationModule|lessonRunnerDataModule|questionSchemaModule|LessonRunnerComponentFactory|core/navigation|quizzes-screen" docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
1:diff --git a/android/feature/quizzes-screen/presentation/build.gradle.kts b/android/feature/quizzes-screen/presentation/build.gradle.kts
5:+++ b/android/feature/quizzes-screen/presentation/build.gradle.kts
43:diff --git a/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonListComponent.kt b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonListComponent.kt
47:+++ b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonListComponent.kt
75:diff --git a/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonPlaceholderComponent.kt b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonPlaceholderComponent.kt
79:+++ b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonPlaceholderComponent.kt
89:diff --git a/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuestListComponent.kt b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuestListComponent.kt
93:+++ b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuestListComponent.kt
126:diff --git a/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuizzesComponent.kt b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuizzesComponent.kt
130:+++ b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuizzesComponent.kt
180:diff --git a/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeSectionListComponent.kt b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeSectionListComponent.kt
184:+++ b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeSectionListComponent.kt
212:diff --git a/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeThemeListComponent.kt b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeThemeListComponent.kt
216:+++ b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeThemeListComponent.kt
244:diff --git a/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonPlaceholderScreenTest.kt b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonPlaceholderScreenTest.kt
248:+++ b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonPlaceholderScreenTest.kt
267:+ * Spec traceability: docs/features/quizzes-screen/plan/phase-05/tests.md
272:+ * android/feature/quizzes-screen/presentation/build.gradle.kts (scaffold change — backend-dev).
359:diff --git a/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestCardMenuTest.kt b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestCardMenuTest.kt
363:+++ b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestCardMenuTest.kt
402:+ * Spec traceability: docs/features/quizzes-screen/plan/phase-06/tests.md §QuestCardMenuTest
596:diff --git a/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreenTest.kt b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreenTest.kt
600:+++ b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreenTest.kt
626:+ * Spec traceability: docs/features/quizzes-screen/plan/phase-05/tests.md
632:+ * android/feature/quizzes-screen/presentation/build.gradle.kts (scaffold change — backend-dev).
796:diff --git a/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesRotationTest.kt b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesRotationTest.kt
800:+++ b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesRotationTest.kt
821:+ * Spec traceability: docs/features/quizzes-screen/plan/phase-05/tests.md §QuizzesRotationTest
920:diff --git a/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreenTest.kt b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreenTest.kt
924:+++ b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreenTest.kt
948:+ * Spec traceability: docs/features/quizzes-screen/plan/phase-05/tests.md
953:+ * android/feature/quizzes-screen/presentation/build.gradle.kts (scaffold change — backend-dev).
1060:diff --git a/android/feature/quizzes-screen/presentation/src/main/AndroidManifest.xml b/android/feature/quizzes-screen/presentation/src/main/AndroidManifest.xml
1064:+++ b/android/feature/quizzes-screen/presentation/src/main/AndroidManifest.xml
1068:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt
1072:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt
1136:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonPlaceholderComponent.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonPlaceholderComponent.kt
1140:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonPlaceholderComponent.kt
1158:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponent.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponent.kt
1162:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponent.kt
1229:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt
1233:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt
1273:+    // Spec: docs/features/quizzes-screen/plan/phase-03/overview.md Problem 6 / AC#21.
1365:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponent.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponent.kt
1369:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponent.kt
1432:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponent.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponent.kt
1436:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponent.kt
1499:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonListComponent.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonListComponent.kt
1503:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonListComponent.kt
1516:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonPlaceholderComponent.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonPlaceholderComponent.kt
1520:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonPlaceholderComponent.kt
1529:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuestListComponent.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuestListComponent.kt
1533:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuestListComponent.kt
1547:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt
1551:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt
1563:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesComponent.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesComponent.kt
1567:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesComponent.kt
1586:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/SectionListComponent.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/SectionListComponent.kt
1590:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/SectionListComponent.kt
1603:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/ThemeListComponent.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/ThemeListComponent.kt
1607:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/ThemeListComponent.kt
1620:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/config/QuizzesConfig.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/config/QuizzesConfig.kt
1624:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/config/QuizzesConfig.kt
1667:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt
1671:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt
1691:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/LessonDrillMapper.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/LessonDrillMapper.kt
1695:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/LessonDrillMapper.kt
1708:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/QuestToDisplayItemMapper.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/QuestToDisplayItemMapper.kt
1712:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/QuestToDisplayItemMapper.kt
1727:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/SectionDrillMapper.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/SectionDrillMapper.kt
1731:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/SectionDrillMapper.kt
1744:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/ThemeDrillMapper.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/ThemeDrillMapper.kt
1748:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/ThemeDrillMapper.kt
1761:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/navigation/QuizzesNavigator.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/navigation/QuizzesNavigator.kt
1765:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/navigation/QuizzesNavigator.kt
1777:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonListScreen.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonListScreen.kt
1781:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonListScreen.kt
1886:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonPlaceholderScreen.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonPlaceholderScreen.kt
1890:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonPlaceholderScreen.kt
1960:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreen.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreen.kt
1964:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreen.kt
2139:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt
2143:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt
2197:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreen.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreen.kt
2201:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreen.kt
2306:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/ThemeListScreen.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/ThemeListScreen.kt
2310:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/ThemeListScreen.kt
2408:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/HierarchyItemUi.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/HierarchyItemUi.kt
2412:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/HierarchyItemUi.kt
2422:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/HierarchyListUiState.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/HierarchyListUiState.kt
2426:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/HierarchyListUiState.kt
2435:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/LessonPlaceholderUiState.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/LessonPlaceholderUiState.kt
2439:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/LessonPlaceholderUiState.kt
2447:diff --git a/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/QuestListUiState.kt b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/QuestListUiState.kt
2451:+++ b/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/QuestListUiState.kt
2462:diff --git a/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt
2466:+++ b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt
2500:+ * Spec: docs/features/quizzes-screen/0-spec.md
2501:+ * Design: docs/features/quizzes-screen/04-testing.md §2
2616:+     * docs/features/quizzes-screen/plan/phase-03/overview.md:159).
2793:diff --git a/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt
2797:+++ b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt
2813:+ * Spec: docs/features/quizzes-screen/0-spec.md — AC#21 (process death restoration)
2814:+ * Design: docs/features/quizzes-screen/04-testing.md §8
3027:diff --git a/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt
3031:+++ b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt
3072:+ * Spec: docs/features/quizzes-screen/0-spec.md — AC#21
3073:+ * Design: docs/features/quizzes-screen/04-testing.md §12
3237:diff --git a/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt
3241:+++ b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt
3273:+ * Spec: docs/features/quizzes-screen/04-testing.md §6
3408:diff --git a/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonPlaceholderComponentTest.kt b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonPlaceholderComponentTest.kt
3412:+++ b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonPlaceholderComponentTest.kt
3429:+ * Spec: docs/features/quizzes-screen/04-testing.md §7
3511:diff --git a/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponentTest.kt b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponentTest.kt
3515:+++ b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponentTest.kt
3546:+ * Spec: docs/features/quizzes-screen/04-testing.md §3
3771:diff --git a/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponentTest.kt b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponentTest.kt
3775:+++ b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponentTest.kt
3806:+ * Spec: docs/features/quizzes-screen/04-testing.md §4
4003:diff --git a/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponentTest.kt b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponentTest.kt
4007:+++ b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponentTest.kt
4038:+ * Spec: docs/features/quizzes-screen/04-testing.md §5
4158:diff --git a/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeBackDispatcher.kt b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeBackDispatcher.kt
4162:+++ b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeBackDispatcher.kt
4188:diff --git a/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonRepository.kt b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonRepository.kt
4192:+++ b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonRepository.kt
4219:diff --git a/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuestRepository.kt b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuestRepository.kt
4223:+++ b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuestRepository.kt
4268:diff --git a/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeSectionRepository.kt b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeSectionRepository.kt
4272:+++ b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeSectionRepository.kt
4299:diff --git a/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeStackNavigation.kt b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeStackNavigation.kt
4303:+++ b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeStackNavigation.kt
4344:diff --git a/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeThemeRepository.kt b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeThemeRepository.kt
4348:+++ b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeThemeRepository.kt
4375:diff --git a/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/DrillItemMapperTest.kt b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/DrillItemMapperTest.kt
4379:+++ b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/DrillItemMapperTest.kt
4398:+ * Spec: docs/features/quizzes-screen/plan/phase-04/tests.md (DrillItemMapperTest section)
4580:diff --git a/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/QuestToDisplayItemMapperTest.kt b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/QuestToDisplayItemMapperTest.kt
4584:+++ b/android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/QuestToDisplayItemMapperTest.kt
4718:diff --git a/apps/android-next/build.gradle.kts b/apps/android-next/build.gradle.kts
4722:+++ b/apps/android-next/build.gradle.kts
4749:+    implementation(project(":android:feature:quizzes-screen:presentation"))
4802:diff --git a/apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt b/apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt
4806:+++ b/apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt
4956:diff --git a/apps/android-next/src/test/java/.gitkeep b/apps/android-next/src/test/java/.gitkeep
4959:diff --git a/apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt b/apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt
4963:+++ b/apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt
5391:diff --git a/platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/lesson/FirebaseLessonRemoteDataSource.kt b/platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/lesson/FirebaseLessonRemoteDataSource.kt
5395:+++ b/platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/lesson/FirebaseLessonRemoteDataSource.kt
5421:diff --git a/platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/lesson/FirestoreLessonDtoMapper.kt b/platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/lesson/FirestoreLessonDtoMapper.kt
5425:+++ b/platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/lesson/FirestoreLessonDtoMapper.kt
5442:diff --git a/settings.gradle.kts b/settings.gradle.kts
5446:+++ b/settings.gradle.kts
5523:+include(":android:feature:quizzes-screen:presentation")
5551:diff --git a/shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/di/PersistenceModule.kt b/shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/di/PersistenceModule.kt
5555:+++ b/shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/di/PersistenceModule.kt
5591:diff --git a/shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabase.kt b/shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabase.kt
5595:+++ b/shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabase.kt
5626:diff --git a/shared/core/question-schema/build.gradle.kts b/shared/core/question-schema/build.gradle.kts
5630:+++ b/shared/core/question-schema/build.gradle.kts
5650:diff --git a/shared/core/question-schema/src/androidMain/AndroidManifest.xml b/shared/core/question-schema/src/androidMain/AndroidManifest.xml
5654:+++ b/shared/core/question-schema/src/androidMain/AndroidManifest.xml
5658:diff --git a/shared/core/question-schema/src/androidMain/kotlin/.gitkeep b/shared/core/question-schema/src/androidMain/kotlin/.gitkeep
5661:diff --git a/shared/core/question-schema/src/commonMain/kotlin/.gitkeep b/shared/core/question-schema/src/commonMain/kotlin/.gitkeep
5664:diff --git a/shared/core/question-schema/src/commonTest/kotlin/.gitkeep b/shared/core/question-schema/src/commonTest/kotlin/.gitkeep
5667:diff --git a/shared/core/question-schema/src/jvmMain/kotlin/.gitkeep b/shared/core/question-schema/src/jvmMain/kotlin/.gitkeep
5670:diff --git a/shared/core/question-schema/src/jvmTest/kotlin/.gitkeep b/shared/core/question-schema/src/jvmTest/kotlin/.gitkeep
5673:diff --git a/shared/feature/lesson/data/build.gradle.kts b/shared/feature/lesson/data/build.gradle.kts
5677:+++ b/shared/feature/lesson/data/build.gradle.kts
5702:diff --git a/shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/LessonLocalDataSource.kt b/shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/LessonLocalDataSource.kt
5706:+++ b/shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/LessonLocalDataSource.kt
5737:diff --git a/shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/LessonRemoteDataSource.kt b/shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/LessonRemoteDataSource.kt
5741:+++ b/shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/LessonRemoteDataSource.kt
5751:diff --git a/shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/LessonRepositoryImpl.kt b/shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/LessonRepositoryImpl.kt
5755:+++ b/shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/LessonRepositoryImpl.kt
5808:diff --git a/shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/di/LessonDataModule.kt b/shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/di/LessonDataModule.kt
5812:+++ b/shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/di/LessonDataModule.kt
5826:diff --git a/shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/dto/LessonDto.kt b/shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/dto/LessonDto.kt
5830:+++ b/shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/dto/LessonDto.kt
5844:diff --git a/shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/mapper/LessonDtoMapper.kt b/shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/mapper/LessonDtoMapper.kt
5848:+++ b/shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/mapper/LessonDtoMapper.kt
5867:diff --git a/shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/mapper/LessonMapper.kt b/shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/mapper/LessonMapper.kt
5871:+++ b/shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/mapper/LessonMapper.kt
5892:diff --git a/shared/feature/lesson/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/LessonRepositoryImplTest.kt b/shared/feature/lesson/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/LessonRepositoryImplTest.kt
5896:+++ b/shared/feature/lesson/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/LessonRepositoryImplTest.kt
6038:diff --git a/shared/feature/lesson/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/fake/FakeLessonLocalDataSource.kt b/shared/feature/lesson/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/fake/FakeLessonLocalDataSource.kt
6042:+++ b/shared/feature/lesson/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/fake/FakeLessonLocalDataSource.kt
6092:diff --git a/shared/feature/lesson/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/fake/FakeLessonRemoteDataSource.kt b/shared/feature/lesson/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/fake/FakeLessonRemoteDataSource.kt
6096:+++ b/shared/feature/lesson/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/fake/FakeLessonRemoteDataSource.kt
6119:diff --git a/shared/feature/lesson/domain/build.gradle.kts b/shared/feature/lesson/domain/build.gradle.kts
6123:+++ b/shared/feature/lesson/domain/build.gradle.kts
6145:diff --git a/shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/di/LessonDomainModule.kt b/shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/di/LessonDomainModule.kt
6149:+++ b/shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/di/LessonDomainModule.kt
6159:diff --git a/shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/model/Lesson.kt b/shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/model/Lesson.kt
6163:+++ b/shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/model/Lesson.kt
6219:diff --git a/shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/model/LessonId.kt b/shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/model/LessonId.kt
6223:+++ b/shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/model/LessonId.kt
6241:diff --git a/shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/repository/LessonRepository.kt b/shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/repository/LessonRepository.kt
6245:+++ b/shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/repository/LessonRepository.kt
6296:diff --git a/shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/use_case/SyncLessonsUseCase.kt b/shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/use_case/SyncLessonsUseCase.kt
6300:+++ b/shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/use_case/SyncLessonsUseCase.kt
6328:diff --git a/shared/feature/lesson/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/LessonDomainTest.kt b/shared/feature/lesson/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/LessonDomainTest.kt
6332:+++ b/shared/feature/lesson/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/LessonDomainTest.kt
6538:diff --git a/shared/feature/lesson/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/fake/FakeLessonRepository.kt b/shared/feature/lesson/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/fake/FakeLessonRepository.kt
6542:+++ b/shared/feature/lesson/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/fake/FakeLessonRepository.kt

exec
/bin/bash -lc 'git status --short' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
 M .claude/PROJECT-CONTEXT.md
 M .claude/agents/architect-component.md
 M .claude/agents/architect-reviewer.md
 M .claude/agents/code-analyst.md
 M .claude/agents/codebase-researcher.md
 M .claude/agents/domain-designer.md
 M .claude/agents/frontend-dev.md
 M .claude/agents/integration-tester.md
 M .claude/agents/planner.md
 M .claude/agents/test-dev.md
 M .claude/commands/feature-debug.md
 M .claude/commands/feature-design.md
 M .claude/commands/feature-implement.md
 M .claude/commands/feature-plan.md
 M .claude/commands/feature-research.md
 M .claude/commands/feature-spec.md
 M .claude/rules/agent-communication.md
 M .claude/rules/clean-architecture.md
 M .claude/rules/di-patterns.md
 M .claude/rules/domain-models.md
 M .claude/rules/kotlin-conventions.md
 M .claude/rules/lifecycle.md
 M .claude/rules/navigation.md
 M .claude/rules/room-database.md
 M .claude/rules/testing.md
 M .claude/rules/use-cases.md
 M android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/BreadcrumbBar.kt
 M android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/CatalogGrid.kt
 M android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/CatalogSpinner.kt
 M android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/HierarchyItemCard.kt
 M android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/QuestCard.kt
 M android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/StarRating.kt
 M android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/model/CatalogDisplayItem.kt
 M android/feature/app-shell/presentation/build.gradle.kts
 M android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt
 M android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/di/AppShellPresentationModule.kt
 M android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt
 M android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerContent.kt
 M android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerFooter.kt
 M android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerSectionList.kt
 M android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/DefaultRootComponentTest.kt
 M android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/DevModeActivationIntegrationTest.kt
 M android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt
 M android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/fake/StubQuestComponents.kt
 M android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/DefaultHomeQuestsComponent.kt
 M android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/DefaultMyQuestsComponent.kt
 M android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/HomeQuestsComponent.kt
 M android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/di/QuestPresentationModule.kt
 M android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/mapper/QuestToDisplayItem.kt
 M android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/ui/MyQuestsScreen.kt
 M android/feature/quest/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/DefaultHomeQuestsComponentTest.kt
 M android/feature/quizzes-screen/presentation/build.gradle.kts
 M android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonListComponent.kt
 D android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonPlaceholderComponent.kt
 M android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuizzesComponent.kt
 D android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonPlaceholderScreenTest.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt
 D android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonPlaceholderComponent.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponent.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponent.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponent.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonListComponent.kt
 D android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonPlaceholderComponent.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuestListComponent.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesComponent.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/SectionListComponent.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/ThemeListComponent.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/config/QuizzesConfig.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/LessonDrillMapper.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/QuestToDisplayItemMapper.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/SectionDrillMapper.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/ThemeDrillMapper.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/navigation/QuizzesNavigator.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonListScreen.kt
 D android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonPlaceholderScreen.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreen.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreen.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/ThemeListScreen.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/HierarchyListUiState.kt
 D android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/LessonPlaceholderUiState.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/QuestListUiState.kt
 M android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt
 M android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt
 M android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt
 M android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt
 D android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonPlaceholderComponentTest.kt
 M apps/android-next/build.gradle.kts
 M apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt
 M apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt
 M apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt
 M build.gradle.kts
 M config/detekt/detekt.yml
 M docs/architecture/0003-question-schema.md
 M docs/features/quizzes-screen/03-decisions.md
 M docs/invariants.md
 M gradle.properties
 M platform/android-services/src/main/kotlin/com/tpov/schoolquiz/platform/android_services/sync/SyncWorker.kt
 M platform/android-services/src/main/kotlin/com/tpov/schoolquiz/platform/android_services/sync/SyncWorkerFactory.kt
 M platform/firebase/build.gradle.kts
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/FirebaseUserStatsDataSource.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/catalog/FirebaseCatalogRemoteDataSource.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/di/FirebaseCatalogModule.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/di/FirebaseLessonModule.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/di/FirebaseQuestModule.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/di/FirebaseQuestionModule.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/di/FirebaseSectionModule.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/di/FirebaseThemeModule.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/lesson/FirebaseLessonRemoteDataSource.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/lesson/FirestoreLessonDtoMapper.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/quest/FirebaseQuestRemoteDataSource.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/question/FirebaseQuestionRemoteDataSource.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/question/FirestoreQuestionDtoMapper.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/section/FirebaseSectionRemoteDataSource.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/section/FirestoreSectionDtoMapper.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/theme/FirebaseThemeRemoteDataSource.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/theme/FirestoreThemeDtoMapper.kt
 M settings.gradle.kts
 M shared/core/persistence/build.gradle.kts
 M shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/di/PersistenceModule.kt
 D shared/core/persistence/src/androidTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabaseMigrationTest.kt
 D shared/core/persistence/src/androidTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabaseSchemaValidationTest.kt
 D shared/core/persistence/src/androidTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/CatalogDaoTest.kt
 D shared/core/persistence/src/androidTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/QuestDaoBoundaryTest.kt
 D shared/core/persistence/src/androidTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/SectionDaoBoundaryTest.kt
 D shared/core/persistence/src/androidTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/UserStatsDaoTest.kt
 D shared/core/persistence/src/androidTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/dao/QuestDaoByCatalogTest.kt
 M shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabase.kt
 M shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonEntity.kt
 M shared/core/question-schema/build.gradle.kts
 M shared/feature/lesson/data/build.gradle.kts
 M shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/dto/LessonDto.kt
 M shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/mapper/LessonDtoMapper.kt
 M shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/mapper/LessonMapper.kt
 M shared/feature/lesson/domain/build.gradle.kts
 M shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/model/Lesson.kt
?? AGENTS.md
?? android/feature/lesson-runner/
?? android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCardTest.kt
?? android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCard.kt
?? android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/LessonItemUi.kt
?? android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/LessonListUiState.kt
?? android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt
?? android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeAuthRepository.kt
?? android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonAttemptRepository.kt
?? docs/features/lesson-runner/
?? shared/core/leaderboard/
?? shared/core/persistence/schemas/com.tpov.schoolquiz.shared.core.persistence.AppDatabase/4.json
?? shared/core/persistence/src/androidInstrumentedTest/
?? shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/migrations/
?? shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptDao.kt
?? shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptEntity.kt
?? shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonRatingLocalDao.kt
?? shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonRatingSubmittedLocalEntity.kt
?? shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/TopParticipantListConverter.kt
?? shared/core/persistence/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/TypeConvertersPhase02Test.kt
?? shared/core/question-schema/src/androidMain/kotlin/com/
?? shared/core/question-schema/src/commonMain/kotlin/com/
?? shared/core/question-schema/src/commonTest/kotlin/com/
?? shared/feature/lesson-runner/
?? shared/feature/lesson/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/LessonMapperPhase02Test.kt
?? shared/feature/lesson/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/LessonNewFieldsTest.kt

codex
The initial scan already shows a dirty tree with the lesson-runner modules mostly untracked, so I’m treating the patch as the submitted diff and the working files as the review target for exact line numbers. Next I’m tracing the specific contracts the prompt called out: DI, navigation direction, state projection, and the domain test surface.
exec
/bin/bash -lc "sed -n '1036,1148p' docs/features/lesson-runner/0-spec.md" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
### Navigation flow

1. [ ] GIVEN пользователь на `LessonListComponent` тапает урок (без HARD checkbox или checkbox=false) THEN push `LessonRunnerRootComponent(lessonId, mode=EASY)`; FLAG_SECURE НЕ включается; первый вопрос отображается
2. [ ] GIVEN пользователь с `hardUnlocked=true` тапает урок с включенным HARD checkbox THEN push `LessonRunnerRootComponent(lessonId, mode=HARD)`; FLAG_SECURE включается; первый вопрос отображается с HARD-стилизацией фона
3. [ ] GIVEN пользователь на любом вопросе нажимает крестик THEN диалог «Уверены?» → подтвердил → save attempt → возврат в `LessonListComponent`
4. [ ] GIVEN пользователь полностью прошёл все вопросы pool THEN save attempt в Room → переход на result screen
5. [ ] GIVEN пользователь на result screen тапает «Завершить» THEN если HARD — снять FLAG_SECURE → возврат в `LessonListComponent` через ChildStack pop

### Score / codeAnswer correctness

6. [ ] GIVEN SingleChoice с correct=A WHEN selected=A THEN code digit = '9'
7. [ ] GIVEN SingleChoice с correct=A WHEN selected=B THEN code digit = '1'
8. [ ] GIVEN MultipleChoice (все верные picked) THEN code digit = '9'
9. [ ] GIVEN MultipleChoice (никаких верных picked, только wrong) THEN code digit = '1'
10. [ ] GIVEN MultipleChoice (Jaccard 0.5) THEN code digit = '5'
11. [ ] GIVEN Ordering все позиции верны THEN digit = '9'
12. [ ] GIVEN Ordering 3 из 6 совпало THEN digit = '5' (round(0.5*8)+1)
13. [ ] GIVEN FillBlank 3 из 3 верно THEN digit = '9'
14. [ ] GIVEN FillBlank 1 из 3 THEN digit = '4' (round(0.333*8)+1=round(2.67)+1)
15. [ ] GIVEN timeout без действий THEN auto-random выбор → digit вычислен по тем же правилам (зависит от случайного попадания)
16. [ ] GIVEN attempt completed THEN `codeAnswer.length == eligibleQuestions(mode).size`; non-zero digits.size == subset.size; positions of non-zero соответствуют orders показанных вопросов

### Stars and progress

17. [ ] GIVEN EASY attempt с `codeAnswer.allShownAnswersAre9 == true` (`percentScore=100`) THEN `Stars(rawTenths=20)`; bestStars обновляется; hardUnlocked становится true
18. [ ] GIVEN EASY attempt с `percentScore=50` (no allShownAnswersAre9) THEN `Stars(rawTenths=10)`; hardUnlocked остаётся false
19. [ ] GIVEN HARD attempt с `percentScore=80` THEN `Stars(rawTenths=28)`
20. [ ] GIVEN HARD attempt с `percentScore=100` (allShownAnswersAre9=true) THEN `Stars(rawTenths=30)`
21. [ ] GIVEN no attempts THEN `bestStars = Stars(0)` (UI 0.0), `hardUnlocked = false`, HARD checkbox скрыт
22. [ ] GIVEN ≥1 EASY-попытка с allShownAnswersAre9=true (все показанные = '9') THEN `hardUnlocked = true` → HARD checkbox visible; user может включить
23. [ ] GIVEN ≥1 EASY-попытка с `allShownAnswersAre9=false` (хотя бы один digit ∈ '1'..'8') THEN `hardUnlocked = false` → HARD checkbox остаётся скрытым; даже если есть HARD-attempt с floor `Stars(rawTenths=20)` — checkbox visibility = `hardUnlocked`, НЕ `bestStars.rawTenths >= 20`

### Timer

24. [ ] GIVEN вопрос с totalChars=165, EASY mode THEN `timer == round(165*0.18) ≈ 30 сек`
25. [ ] GIVEN тот же вопрос HARD mode THEN `timer ≈ round(165*0.12) ≈ 20 сек`
26. [ ] GIVEN таймер истекает в 0 без действий пользователя THEN auto-random выбор → score фиксируется → переход к следующему вопросу
27. [ ] GIVEN короткий вопрос (totalChars=10) THEN `timer ≥ 5 сек` (min floor delegated)

### Lifecycle / FLAG_SECURE / dialogs

28. [ ] GIVEN HARD-mode runner запущен THEN `WindowManager.LayoutParams.FLAG_SECURE` установлен на window (или Compose-эквивалент)
29. [ ] GIVEN HARD-mode runner exit (любым путём) THEN FLAG_SECURE снят
30. [ ] GIVEN EASY-mode runner THEN FLAG_SECURE НЕ установлен
31. [ ] GIVEN пользователь на 5-м вопросе тапает Home (onStop) THEN текущий вопрос auto-random scored; таймер остановлен
32. [ ] GIVEN пользователь возвращается (onResume) THEN отображается fullscreen блокирующий диалог «Продолжить прохождение?»
33. [ ] GIVEN диалог «Продолжить?» тап «Продолжить» THEN диалог закрыт; следующий вопрос отображён с новым таймером; предыдущий вопрос НЕ показан
34. [ ] GIVEN диалог «Продолжить?» тап «Выйти» THEN attempt saved (codeAnswer: scores отвеченных + '1' для оставшихся показанных + '0' для не показанных); возврат в `LessonListComponent`
35. [ ] GIVEN configuration change (rotation) THEN component не пересоздаётся; таймер не сбрасывается; current question + answers preserved
36. [ ] GIVEN process kill THEN ничего не записывается в Room; следующий запуск — состояние ДО попытки

### Attempt save / Room

37. [ ] GIVEN complete attempt THEN `LessonAttemptRepository.save` called once с Attempt(lessonVersion из старта)
38. [ ] GIVEN exit-via-dialog после 3 ответов (pool size 20, eligibleQuestions.size=50) THEN saved attempt: codeAnswer.length==50, 3 цифры реальные, 17 = '1' (показанные но непрогресированные), 30 = '0' (out of subset)
39. [ ] GIVEN attempt созданный во время `lesson.version=5` THEN `attempt.lessonVersion == 5` даже если lesson.version обновился позже sync-ом
40. [ ] GIVEN no incremental save во время прохождения THEN `LessonAttemptRepository.save` вызывается ровно 1 раз per attempt

### Result screen

41. [ ] GIVEN attempt с `codeAnswer.allShownAnswersAre9 == true` AND ¬hasSubmittedRating THEN на result screen видно опрос «Оцените урок» (1/2/3 целых звезды)
42. [ ] GIVEN attempt с `codeAnswer.allShownAnswersAre9 == true` AND hasSubmittedRating THEN опрос НЕ виден
43. [ ] GIVEN attempt с `codeAnswer.allShownAnswersAre9 == false` (any shown digit < '9') THEN опрос НЕ виден
44. [ ] GIVEN пользователь оценил урок THEN local флаг `lesson_rating_submitted_local` установлен; LessonRatingRepository.submit вызван 1 раз с lessonVersion из текущего Lesson
45. [ ] GIVEN result screen с `Lesson.top3` непустой THEN отображается секция Топ-3 с аватарками, никами и %; пустой → секция скрыта. **Note: top3 — закешированный server snapshot из Lesson document; текущая попытка пользователя ещё не успела попасть в top3 (server CF не выполнился; sync ещё не подтянул).** Список покажет state ДО этой попытки.
46. [ ] GIVEN top3 entry с avatarUrl=null или Coil не смог загрузить (offline / no cache) THEN placeholder вместо аватарки

### HARD unlock checkbox

47. [ ] GIVEN карточка урока в `LessonListComponent` THEN отображается `StarRating(rating = bestStars.rawTenths / 10f)` (existing API из `android/core/designsystem/.../StarRating.kt:99` принимает `rating: Float?`)
48. [ ] GIVEN `hardUnlocked == false` (нет EASY-попытки с allShownAnswersAre9) THEN HARD checkbox скрыт — независимо от bestStars value
49. [ ] GIVEN `hardUnlocked == true` THEN HARD checkbox visible; default unchecked. Логика: `hardUnlocked = attempts.any { it.mode == EASY && it.codeAnswer.allShownAnswersAre9 }`

### Empty / error states

50. [ ] GIVEN тап на урок с пустым eligibleQuestions(EASY) THEN empty state «В уроке пока нет вопросов» + кнопка «Назад»
51. [ ] GIVEN тап на урок с все payloads invalid THEN empty state аналогично
52. [ ] GIVEN single invalid payload среди валидных WHEN StartLessonAttemptUseCase THEN invalid отброшен на pipeline init; пользователь не видит; codeAnswer длина = только valid eligible
52a. [ ] GIVEN `LessonAttemptRepository.save` throws Room IO exception WHEN `CompleteAttemptUseCase` THEN `RunnerState.SaveFailed(attempt, error)`; result screen показывается с warning «Не удалось сохранить»; нет автоматического retry
52b. [ ] GIVEN `LessonRatingRepository.submit` throws WHEN user тапает submit rating THEN toast «Не удалось отправить оценку»; local флаг hasSubmitted остаётся false; user может попробовать снова

### Code / DI / invariants

53. [ ] DI: `LessonRunnerPresentationModule` + `LessonRunnerDataModule` зарегистрированы в `apps/android-next/.../AppApplication.kt`
54. [ ] Code: ни один файл `shared/feature/lesson-runner/domain/src/commonMain/` не импортирует `android.*`, `androidx.*`, `io.livekit`, `com.google.firebase`, `androidx.room`, `kotlinx.serialization` (invariant 1)
55. [ ] Code: ни одна Activity/Fragment не вызывает Repository / UseCase напрямую (invariant 2)
56. [ ] Code: feature import — `android/feature/lesson-runner/presentation` не импортирует `android/feature/quizzes-screen/presentation` (invariant 3); cross-feature только через `shared/core` или `android/core/designsystem`
57. [ ] Code: `quizzes-screen` импортирует lesson-runner config (для push) — задокументировано в design phase
58. [ ] Code: нет Hilt/Dagger annotations (Koin only)
59. [ ] Code: нет direct Firebase / Firestore writes из этой фичи; только через Repository
60. [ ] Tests: domain тесты Phase 3.8b покрывают все Domain Test Scenarios:
- Score formulas (1-12)
- codeAnswer construction (13-16)
- percentScore (17-20)
- stars (21-29)
- bestStars / hardUnlocked (30-35a)
- timer (36-39b)
- subset / auto-answer (40-47)
- rating prompt (48-51)
- save (52-54)
- lessonVersion (55-57)
- edge cases (58-61c)
- value object guards (62-71)
- failure semantics (72-74a)
- subset determinism (75-77)
- state transitions (78-82) — test 83 исключён из domain (см. ниже комментарий к нему)
Итого ~89 domain test scenarios (с учётом 39a/39b/61a/61b/61c/35a/74a/strikethrough 83). Скрипт renumber применяется в Phase 3.8b — test-dev волен переписать в монотонной последовательности 1..N если предпочитает.
61. [ ] Tests: JVM unit-тесты для каждого presentation Component через fakes
62. [ ] Tests: Compose UI тесты для key scenarios (per-type вопросы, timer, dialog onResume, FLAG_SECURE toggle)
63. [ ] Build: `./gradlew :shared:feature:lesson-runner:domain:jvmTest --no-configuration-cache` зелёный (Walking Skeleton acceptance)
64. [ ] Build: `./gradlew assemble --no-configuration-cache` зелёный после реализации
65. [ ] Build: `./gradlew test --no-configuration-cache` и `./gradlew allTests --no-configuration-cache` зелёные


exec
/bin/bash -lc "sed -n '331,390p' docs/features/lesson-runner/03-decisions.md" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
## ADR-LR-16: LessonRunnerRootComponent interface location — lesson-runner/presentation

**Status**: ACCEPTED — устраняет circular Gradle dependency (Codex CLI plan-round-1 blocker, 2026-04-27)

### Context

`plan/phase-04/backend.md:18-44` содержал задачу поместить `LessonRunnerRootComponent` interface в `android/core/navigation/`. При анализе типов выявлена circular Gradle dependency:

```
core/navigation → lesson-runner/presentation   (RunnerUiState, RunnerEvent типы для interface)
lesson-runner/presentation → core/navigation   (interface implementation)
```

Bidirectional module dependency = Gradle build failure. Дополнительно, `clean-architecture.md` запрещает `core → feature/*` imports.

`LessonRunnerComponentFactory` fun interface с тем же планом в `core/navigation` имела идентичную проблему: return type `LessonRunnerRootComponent` обязывает `core/navigation → lesson-runner/presentation`.

### Decision

**Оба артефакта живут в `android/feature/lesson-runner/presentation/`**:

| Артефакт | File path |
|----------|-----------|
| `LessonRunnerRootComponent` interface | `android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt` |
| `LessonRunnerComponentFactory` fun interface | `android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt` |

`quizzes-screen/presentation` импортирует оба напрямую. Направление строго одностороннее: `quizzes-screen/presentation → lesson-runner/presentation`. Reverse direction запрещён.

`android/core/navigation/` не получает новых файлов в рамках этой фичи.

Canonical signatures: `06-api-contract.md §LR-9` (interface) и `§LR-9a` (factory).

### Alternatives Considered

**Вариант A — core/navigation (изначальный план, ADR-LR-07 original section)**:
- (-) Создаёт cycle: `core/navigation → lesson-runner/presentation` + `lesson-runner/presentation → core/navigation`. Gradle build failure.
- (-) Нарушает `clean-architecture.md`: core не зависит от product features.
- Отклонён.

**Вариант B — Marker interface `LessonRunnerRoot` в core/navigation без presentation refs**:
- (+) `core/navigation` остаётся без dep на `lesson-runner/presentation`
- (-) `quizzes-screen/presentation` получает untyped marker — не может обращаться к `uiState`/`events` без downcast
- (-) Downcast в `QuizzesChild.LessonRunner` нарушает type safety; runtime ClassCastException при рефакторинге
- (-) `LessonRunnerScreen` composable всё равно ожидает typed `LessonRunnerRootComponent` — presenter слой не упрощается
- Отклонён.

**Вариант C — RunnerUiState/RunnerEvent в shared/core**:
- (-) Presentation state types (feature-specific sealed interface) в `shared/core` нарушают layer semantics
- (-) core не должен содержать feature-specific presentation state
- Отклонён.

### Consequences

- `quizzes-screen/presentation` Gradle module добавляет `:android:feature:lesson-runner:presentation` в deps (backend-dev scope, `build.gradle.kts`).
- `DefaultQuizzesComponent.createChild` для `QuizzesConfig.LessonRunner` вызывает `factory.create(ctx, lessonId, mode)` → возвращает typed `LessonRunnerRootComponent`.
- Koin: `LessonRunnerComponentFactory` single binding в `apps/android-next/AppApplication.kt` — canonical: `06-api-contract.md §LR-9a`.
- `android/core/navigation/` не затронут в этой фиче.
- Validation grep после phase-04 implementation: `rg "interface LessonRunnerRootComponent" android/feature/lesson-runner/presentation/src` — ожидается 1 match.

### Risk if wrong (6 months out)

exec
/bin/bash -lc "sed -n '432,466p' docs/features/lesson-runner/03-decisions.md" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
## ADR-LR-09 — Koin lambda binding strategy для providers

**Status**: Accepted  
**Context**: `LessonRunnerDomainModule` использует `get()` для `() -> AttemptId`, `() -> Long` (randomSeedProvider), `(String, LessonId) -> RatingId`. Koin binding на function types имеет type erasure риск на JVM (все `Function1<*, *>` конфликтуют при resolution). Три варианта:
- A: `single<() -> AttemptId> { { AttemptId(UUID.randomUUID().toString()) } }` — type erasure риск
- B: параметр module-функции (pattern из `appShellDataModule`) — ограничивает re-use
- C: wrapper interfaces + адаптация в Koin factory

**Decision**: **Option C** — wrapper interfaces в `shared/feature/lesson-runner/domain/src/commonMain/…/provider/` (C1 fix: domain — единственный потребитель; interfaces принадлежат слою который их использует):

Canonical interface definitions — `06-api-contract.md §LR-13a`.

Default implementations (`DefaultAttemptIdProvider`, `DefaultRandomSeedProvider`, `DefaultRatingIdProvider`) — `shared/feature/lesson-runner/data/src/androidMain/…/provider/`.

Koin adapter (`lessonRunnerDomainKoinAdapter`) — `shared/feature/lesson-runner/data/src/androidMain/…/di/` (C1 fix: adapter в data, не в domain/androidMain):

```kotlin
// lessonRunnerDataModule (data/androidMain)
single<AttemptIdProvider> { DefaultAttemptIdProvider() }   // UUID.randomUUID()
single<RandomSeedProvider> { DefaultRandomSeedProvider() } // System.currentTimeMillis()
single<RatingIdProvider> { DefaultRatingIdProvider() }      // sha256("$userId:$lessonId")

// lessonRunnerDomainKoinAdapter (data/androidMain) — adapts interfaces to function types
factory<CompleteAttemptUseCase> {
    CompleteAttemptUseCase(…, attemptIdProvider = get<AttemptIdProvider>()::next)
}
```

**Rationale**: Нет type erasure, легко тестируется (fake реализации), explicit named types в Koin graph. Адаптация к function type происходит один раз в Koin factory — не затрагивает Walking Skeleton domain code (Invariant 6). Interfaces в domain (не data) следуют Dependency Inversion: domain определяет контракт, data предоставляет реализацию.

**Alternatives Considered**:
- **Option A** — type erasure: все `() -> X` получают erasure `Function0<*>`, первый registered "wins". Koin не поддерживает qualified binding по erasure без явного `named()`. Хрупко. Отклонено.
- **Option B** — module как функция с параметрами — работает для одного consumer, неудобен при нескольких фичах. Отклонено.

### Risk if wrong (6 months out)

exec
/bin/bash -lc "sed -n '628,772p' docs/features/lesson-runner/03-decisions.md" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
## ADR-LR-17 — Compose composition exception: quizzes-screen рендерит LessonRunnerScreen через ChildStack

**Status**: Accepted  
**Date**: 2026-04-27  
**Context**: `QuizzesScreen.kt` содержит exhaustive `when(active)` dispatch block для `ChildStack<QuizzesConfig, QuizzesChild>`. При добавлении `QuizzesChild.LessonRunner` (ADR-LR-07) нужно вызвать `LessonRunnerScreen(child.component)` — `@Composable` функция из `android/feature/lesson-runner/presentation`. Это создаёт прямой import `android/feature/quizzes-screen/presentation → android/feature/lesson-runner/presentation`.

`clean-architecture.md:55` запрещает: `android/feature/A/presentation → android/feature/B/presentation: NO by default`. Правило написано до full Decompose adoption и не учитывало ChildStack UI rendering pattern, в котором parent screen обязан знать `@Composable` функции дочерних screens.

**Precedent (verified grep)**: `android/feature/app-shell/presentation/src/main/.../ui/AppShellScreen.kt:53-56` содержит cross-feature imports `HomeQuestsScreen`, `MyQuestsScreen`, `QuizzesScreen` из sibling features — established project pattern до lesson-runner.

**Связь с ADR-LR-16**: `LessonRunnerRootComponent` interface живёт в `lesson-runner/presentation` (не в `core/navigation` — отменено из-за cycle, per ADR-LR-16). `QuizzesScreen.kt` получает `child.component: LessonRunnerRootComponent` из ChildStack и передаёт его в `LessonRunnerScreen`. Таким образом ADR-LR-17 расширяет разрешение ADR-LR-16: тот же модуль `lesson-runner/presentation`, дополнительный artifact — `@Composable` screen function.

**Decision**: Разрешить **одностороннее** Compose rendering import `android/feature/quizzes-screen/presentation → android/feature/lesson-runner/presentation` для:
- `LessonRunnerScreen` — `@Composable` top-level screen function
- `LessonRunnerRootComponent` interface — тип параметра `LessonRunnerScreen` (уже разрешён ADR-LR-16, упомянут здесь для полноты)

Обратное направление `android/feature/lesson-runner/presentation → android/feature/quizzes-screen/presentation` остаётся blocker — нарушение = blocker независимо от типа символа.

Детали условий (одностороннее + только `@Composable` + ADR required + grep) — в `clean-architecture.md` ChildStack Compose rendering exception note.

**Alternatives Considered**:

- **(A) Slot pattern** — `LessonRunnerNavigationSlot @Composable` extension в `android/core/navigation/`; quizzes-screen вызывает slot; каждая фича регистрирует свой slot в centralised registry. Отклонён: overhead slot registration; registry требует централизованного места без compile-time гарантии что slot зарегистрирован; не следует Decompose idiomatic pattern.
- **(B) Централизовать в app-shell / RootComponent** — нарушает navigation hierarchy: lesson-runner живёт внутри quiz drill-down (Quest→Section→Theme→Lesson→Runner), не как top-level destination. AppShell — неправильный хост для gameplay экрана.
- **(C) Одностороннее Compose rendering import (CHOSEN)** — minimal change, follows Decompose convention (parent screen knows child @Composable functions), backed by established project precedent.

**Constraints**:
- Импортируется **только** `@Composable` screen function + `LessonRunnerRootComponent` — NOT use cases, NOT repositories, NOT internal sealed interfaces, NOT component implementation classes
- Verifiable через grep:
  ```bash
  rg "^import com\.tpov\.schoolquiz\.android\.feature\.lesson_runner\.presentation" \
    android/feature/quizzes-screen/presentation/src/main -g "*.kt"
  # Допустимые совпадения: только LessonRunnerScreen и LessonRunnerRootComponent
  ```
- Reverse blocker (всегда пусто):
  ```bash
  rg "^import com\.tpov\.schoolquiz\.android\.feature\.quizzes_screen\.presentation" \
    android/feature/lesson-runner/presentation/src/main -g "*.kt"
  # Expected: empty
  ```

### Risk if wrong (6 months out)

Очень низкий. Паттерн установлен как project precedent (`AppShellScreen.kt:53-56`) и широко используется в Decompose ecosystem. Если `LessonRunnerScreen` переедет или переименуется — compile error при сборке, не runtime regression. Если bidirectional coupling проникнет — grep check в architect-reviewer выявит немедленно. Если slot pattern станет необходимым позже — стоимость migration низкая (1 composable wrapper + registration).

<!-- CMP_SECTION_END -->

---

## ADR-LR-18 — DifficultyConverter удалён: mapper-based conversion вместо TypeConverter

**Status**: ACCEPTED — Phase-02 runtime finding (2026-04-27)

### Context

Phase-02 добавил `DifficultyConverter` (`@ProvidedTypeConverter`) для конвертации `Difficulty ↔ String` в Room. После реализации выяснилось, что ни одна Entity не хранит поле типа `Difficulty` напрямую — `LessonAttemptEntity.isHard: Int` (spec-mandated primitive). Room KMP 2.7+ `validateTypeConverters()` отклоняет любой `@ProvidedTypeConverter`, тип которого не используется ни одной Entity, выбрасывая `IllegalArgumentException: Unexpected type converter` при открытии БД.

Verified: `LessonAttemptEntity.isHard` — `@ColumnInfo(name="is_hard") val isHard: Int` (spec AC#52 mandates Int for safer migration validation). Converter на `Difficulty` не имеет Entity-binding.

### Decision

**Удалить `DifficultyConverter.kt` полностью.** Конвертация `Difficulty ↔ Int` выполняется в mapper слое:

```kotlin
// LessonAttemptMapper (data layer)
isHard = if (domain.mode == Difficulty.HARD) 1 else 0
// fromEntity:
mode = if (entity.isHard == 1) Difficulty.HARD else Difficulty.EASY
```

`DifficultyConverter` не регистрируется ни в `@TypeConverters`, ни в Room builder. `TypeConvertersPhase02Test.kt` MT-05 тесты удалены.

Plan invariant `overview.md §Pattern Invariants` строка про `DifficultyConverter` — superseded этим ADR.

### Alternatives Considered

- **Оставить DifficultyConverter + изменить Entity на `Difficulty` тип** — нарушает spec AC#52 ("isHard: Int для safer migration validation"). Отклонено: spec mandates Int.
- **Оставить converter, зарегистрировать без Entity use** — Room KMP 2.7+ выбрасывает `IllegalArgumentException` при открытии БД. Технически невозможно.

### Consequences

- `DifficultyConverter.kt` удалён
- `AppDatabase.@TypeConverters` содержит только `StringSetConverter::class, TopParticipantListConverter::class`
- `PersistenceModule.kt` Room builder содержит только `.addTypeConverter(StringSetConverter())` и `.addTypeConverter(TopParticipantListConverter())`
- `TypeConvertersPhase02Test` MT-05 тесты (`difficultyConverter EASY/HARD roundtrip`) удалены
- Mapper `LessonAttemptMapper` обрабатывает `Difficulty ↔ Int` через условный маппинг

### Risk if wrong (6 months out)

Если `Difficulty` когда-либо нужно хранить как строку в Entity — добавить новый `@ProvidedTypeConverter` с Entity полем. Стоимость: низкая (1 converter file + 1 Entity field change). Текущий mapper-based подход — стандартный паттерн для примитивных представлений domain enum.

## ADR-LR-19 — Phase-04: RunnerUiState.Result flat projection (security)

Status: Accepted
Date: 2026-04-28

### Decision

`RunnerUiState.Result` использует flat-поля (`percentScore`, `mode`, `completedAt`, `hardUnlocked`, `bestStarsRawTenths`) вместо `attempt: Attempt` aggregate из `06-api-contract.md:408`.

### Rationale

Phase-04 security review (task #23) обнаружил что `attempt: Attempt` в публичном `StateFlow<RunnerUiState>` содержит PII: `userId`, `codeAnswer`, `attemptId`. Flat projection — security best practice (minimal exposure): UI получает только поля, необходимые для рендеринга, без доступа к идентификаторам пользователя и raw code answers.

Дополнительно: `hardUnlocked` и `bestStarsRawTenths` отсутствуют в domain `Attempt` — flat-подход единственно возможный без изменения domain модели. Phase-04 code-review (task #22) принял реализацию. Тесты PT-01..PT-41 зелёные.

### Supersedes

- `06-api-contract.md:408` — spec `data class Result(val attempt: Attempt, ...)` (добавить note там)
- `plan/phase-05/frontend.md` Pattern Invariant строки про `state.attempt.mode` и `state.attempt.percentScore`

### Consequences

- `RunnerUiState.Result` имеет flat-поля; Phase-05 UI обращается к `state.mode`, `state.percentScore.raw` напрямую
- Phase-06 и последующие фазы используют flat-поля `RunnerUiState.Result` — не `attempt: Attempt`
- `Attempt` domain aggregate остаётся неизменным; в `StateFlow` не передаётся

### Alternatives Considered

- **Вернуть `attempt: Attempt`** — невозможно без раскрытия PII полей (userId, codeAnswer) в публичном StateFlow. Отклонено: противоречит security finding #23.
- **Добавить separate `hardUnlocked: Boolean` + `bestStarsRawTenths: Int` рядом с `attempt`** — создаёт hybrid сигнатуру, сложнее чем full flat. Отклонено: flat проще и safer.

## ADR-LR-20 — LessonRunnerComponentFactory binding location: lessonRunnerPresentationModule (Phase-04 accepted deviation)

Status: Accepted
Date: 2026-04-28

### Decision

`single<LessonRunnerComponentFactory>` binding живёт в `lessonRunnerPresentationModule` (`android/feature/lesson-runner/presentation/src/main/.../di/LessonRunnerPresentationModule.kt:37`), а **не** inline в `AppApplication.kt startKoin` как предписывалось `06-api-contract.md:374`.

### Rationale

Phase-04 HIGH finding (task #21): binding добавлен в `lessonRunnerPresentationModule` в рамках fix loop. Binding корректен: при регистрации `lessonRunnerPresentationModule` в composition root factory автоматически доступна через Koin graph. Поведение идентично spec — `NoBeanDefinitionFoundException` при навигации на LessonRunner не возникает.

Phase-07 backend-dev корректно **не дублировал** binding в `AppApplication.kt` (`di-patterns.md`: "No duplicate `single<T>` for the same production type").

### Consequences

- `06-api-contract.md:374` spec binding — документально отклонён в пользу module-encapsulated подхода
- `AppApplication.kt` регистрирует `lessonRunnerPresentationModule` → factory доступна транзитивно
- Будущие разработчики **не должны** добавлять `single<LessonRunnerComponentFactory>` в `AppApplication.kt` — это создаст Koin duplicate binding exception

### Supersedes


exec
/bin/bash -lc 'rg --files android/feature/lesson-runner shared/feature/lesson-runner shared/core/question-schema shared/core/persistence android/feature/quizzes-screen/presentation apps/android-next | sort' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
android/feature/lesson-runner/presentation/build.gradle.kts
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/RunFakeComponent.kt
android/feature/lesson-runner/presentation/src/main/AndroidManifest.xml
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/LessonRunnerUseCases.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/RunnerStateHolder.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/event/RunnerEvent.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/QuestionUiState.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/RunnerUiState.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/BlockingResumeDialog.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/CrossButton.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ExitConfirmDialog.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/FillBlankContent.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ImageComponents.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/MultipleChoiceContent.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/OrderingContent.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/QuestionProgressHeader.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/RatingPromptSection.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ResultContent.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/SingleChoiceContent.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/Top3Section.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAbortAttemptUseCase.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAttemptFixtures.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAuthRepository.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeClock.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeCompleteAttemptUseCase.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonAttemptRepository.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonRatingRepository.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonRepository.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeQuestionContentParser.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeQuestionRepository.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeStartLessonAttemptUseCase.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeSubmitLessonRatingUseCase.kt
android/feature/quizzes-screen/presentation/build.gradle.kts
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonListComponent.kt
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuestListComponent.kt
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuizzesComponent.kt
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeSectionListComponent.kt
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeThemeListComponent.kt
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCardTest.kt
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestCardMenuTest.kt
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreenTest.kt
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesRotationTest.kt
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreenTest.kt
android/feature/quizzes-screen/presentation/src/main/AndroidManifest.xml
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponent.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponent.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponent.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonListComponent.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuestListComponent.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesComponent.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/SectionListComponent.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/ThemeListComponent.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/config/QuizzesConfig.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/LessonDrillMapper.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/QuestToDisplayItemMapper.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/SectionDrillMapper.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/ThemeDrillMapper.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/navigation/QuizzesNavigator.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCard.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonListScreen.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreen.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreen.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/ThemeListScreen.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/HierarchyItemUi.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/HierarchyListUiState.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/LessonItemUi.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/LessonListUiState.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/QuestListUiState.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponentTest.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponentTest.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponentTest.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeAuthRepository.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeBackDispatcher.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonAttemptRepository.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonRepository.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuestRepository.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeSectionRepository.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeStackNavigation.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeThemeRepository.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/DrillItemMapperTest.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/QuestToDisplayItemMapperTest.kt
apps/android-next/build.gradle.kts
apps/android-next/proguard-rules.pro
apps/android-next/src/main/AndroidManifest.xml
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt
apps/android-next/src/main/res/values-night/themes.xml
apps/android-next/src/main/res/values/themes.xml
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt
shared/core/persistence/build.gradle.kts
shared/core/persistence/schemas/com.tpov.schoolquiz.shared.core.persistence.AppDatabase/1.json
shared/core/persistence/schemas/com.tpov.schoolquiz.shared.core.persistence.AppDatabase/2.json
shared/core/persistence/schemas/com.tpov.schoolquiz.shared.core.persistence.AppDatabase/3.json
shared/core/persistence/schemas/com.tpov.schoolquiz.shared.core.persistence.AppDatabase/4.json
shared/core/persistence/src/androidInstrumentedTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabaseMigrationTest.kt
shared/core/persistence/src/androidInstrumentedTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabaseSchemaValidationTest.kt
shared/core/persistence/src/androidInstrumentedTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabaseV4MigrationTest.kt
shared/core/persistence/src/androidInstrumentedTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/CatalogDaoTest.kt
shared/core/persistence/src/androidInstrumentedTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptDaoTest.kt
shared/core/persistence/src/androidInstrumentedTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonRatingLocalDaoTest.kt
shared/core/persistence/src/androidInstrumentedTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/QuestDaoBoundaryTest.kt
shared/core/persistence/src/androidInstrumentedTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/SectionDaoBoundaryTest.kt
shared/core/persistence/src/androidInstrumentedTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/UserStatsDaoTest.kt
shared/core/persistence/src/androidInstrumentedTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/dao/QuestDaoByCatalogTest.kt
shared/core/persistence/src/androidMain/AndroidManifest.xml
shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/di/PersistenceModule.kt
shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/migrations/Migration3to4.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabase.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/CatalogDao.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/CatalogEntity.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptDao.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptEntity.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonDao.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonEntity.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonRatingLocalDao.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonRatingSubmittedLocalEntity.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/QuestDao.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/QuestEntity.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/QuestionDao.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/QuestionEntity.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/SectionDao.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/SectionEntity.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/StringSetConverter.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/ThemeDao.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/ThemeEntity.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/TopParticipantListConverter.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/UserStatsDao.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/UserStatsEntity.kt
shared/core/persistence/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/StringSetConverterTest.kt
shared/core/persistence/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/TypeConvertersPhase02Test.kt
shared/core/question-schema/build.gradle.kts
shared/core/question-schema/src/androidMain/AndroidManifest.xml
shared/core/question-schema/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/di/QuestionSchemaModule.kt
shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/BlankId.kt
shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/CandidateId.kt
shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/Difficulty.kt
shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/KotlinxSerializationQuestionContentParser.kt
shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/OptionId.kt
shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/QuestionContent.kt
shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/QuestionContentParser.kt
shared/core/question-schema/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/question_schema/QuestionContentParserTest.kt
shared/feature/lesson-runner/data/build.gradle.kts
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonAttemptMapper.kt
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonRatingMapper.kt
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonAttemptRepositoryImpl.kt
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonRatingRepositoryImpl.kt
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonAttemptRepositoryImplTest.kt
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonRatingRepositoryImplTest.kt
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/fake/FakeLessonAttemptDao.kt
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/fake/FakeLessonRatingLocalDao.kt
shared/feature/lesson-runner/data/src/jvmAndAndroidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultAttemptIdProvider.kt
shared/feature/lesson-runner/data/src/jvmAndAndroidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultRandomSeedProvider.kt
shared/feature/lesson-runner/data/src/jvmAndAndroidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultRatingIdProvider.kt
shared/feature/lesson-runner/data/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/DefaultProvidersTest.kt
shared/feature/lesson-runner/domain/build.gradle.kts
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/Attempt.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/AttemptId.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/CodeAnswer.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/InitFailureReason.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/LessonRating.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/PercentScore.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/RatingId.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/RunnerQuestion.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/SaveError.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/Score.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/Stars.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/TimerCoefficients.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/TimerDuration.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/UserAnswer.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/UserAnswerDraft.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/AttemptIdProvider.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/RandomSeedProvider.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/RatingIdProvider.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/LessonAttemptRepository.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/LessonRatingRepository.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/state/RunnerState.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/SubmitLessonRatingUseCase.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/BestStarsHardUnlockedTest.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerTest.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/PercentScoreComputeTest.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/RatingPromptTest.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SelectSubsetTest.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StarsComputeTest.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TimerComputeTest.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeAuthRepository.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeClock.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonAttemptRepository.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonRatingRepository.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonRepository.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeQuestionContentParser.kt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeQuestionRepository.kt

exec
/bin/bash -lc "rg -n \"questionSchemaModule|lessonRunnerDataModule|lessonRunnerDomainKoinAdapter|lessonRunnerPresentationModule|LessonRunnerComponentFactory|LessonRunnerRootComponent|DifficultyConverter|RunnerUiState\\.Result|data class Result|sealed.*Result|userId|codeAnswer|attemptId|LessonRunnerScreen|lesson_runner|quizzes_screen|core.navigation|QuizzesChild\\.LessonRunner|QuizzesConfig\\.LessonRunner\" android shared apps docs/features/lesson-runner/implementation.md" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
docs/features/lesson-runner/implementation.md:16:| Phase-02 | Persistence — Migration v3→v4 | PASS (5/5 reviewers) | ADR-LR-18: DifficultyConverter removed |
docs/features/lesson-runner/implementation.md:19:| Phase-05 | Compose UI (LessonRunnerScreen + 12 composables) | PASS (5/5 reviewers) | ADR-LR-19: RunnerUiState.Result flat projection (security-driven) |
docs/features/lesson-runner/implementation.md:21:| Phase-07 | Composition Root + Smoke | PASS (4/5 reviewers; concurrency N/A) | ADR-LR-20: LessonRunnerComponentFactory in presentation module |
docs/features/lesson-runner/implementation.md:63:Phase-02 deletion: DifficultyConverter.kt + MT-05 tests (ADR-LR-18 supersedes).
docs/features/lesson-runner/implementation.md:93:- Полная UI navigation (Catalog → Quest → Section → Theme → Lesson → tap → LessonRunnerScreen) — deferred user verification (interactive UI testing вне scope automated smoke)
docs/features/lesson-runner/implementation.md:99:| LR-18 | 02 | DifficultyConverter removed; mapper-based Difficulty↔Int conversion in data layer | Room KMP 2.7+ rejects unused converters; entity uses Int isHard, mapper handles conversion. Plan invariant overview.md:129 superseded. |
docs/features/lesson-runner/implementation.md:100:| LR-19 | 04→05 | RunnerUiState.Result uses flat projection instead of attempt: Attempt aggregate | Security: attempt contained PII (userId, codeAnswer, attemptId) — minimal exposure principle in public StateFlow. 06-api-contract.md:408 + frontend.md Pattern Invariant superseded. |
docs/features/lesson-runner/implementation.md:101:| LR-20 | 04→07 | single<LessonRunnerComponentFactory> in lessonRunnerPresentationModule, NOT in AppApplication | Phase-04 already established single<> binding; phase-07 plan duplication would cause Koin override warning. Per 06-api-contract.md:374, factory binding stays in feature presentation module. |
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:15:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.di.lessonRunnerPresentationModule
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:17:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.di.quizzesPresentationModule
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:31:import com.tpov.schoolquiz.shared.core.question_schema.di.questionSchemaModule
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:35:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.lessonRunnerDataModule
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:36:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.lessonRunnerDomainKoinAdapter
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:118:                questionSchemaModule,
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:119:                lessonRunnerDataModule,
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:120:                lessonRunnerDomainKoinAdapter,
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:121:                lessonRunnerPresentationModule,
android/core/navigation/build.gradle.kts:6:    namespace = "com.tpov.schoolquiz.android.core.navigation"
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptDao.kt:15:    @Query("SELECT * FROM lesson_attempts WHERE user_id = :userId AND lesson_id = :lessonId")
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptDao.kt:16:    fun observeByLesson(userId: String, lessonId: String): Flow<List<LessonAttemptEntity>>
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptDao.kt:18:    @Query("SELECT * FROM lesson_attempts WHERE user_id = :userId")
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptDao.kt:19:    fun observeAllByUser(userId: String): Flow<List<LessonAttemptEntity>>
apps/android-next/build.gradle.kts:27:    implementation(project(":android:core:navigation"))
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptEntity.kt:18:    val attemptId: String,
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptEntity.kt:19:    @ColumnInfo(name = "user_id") val userId: String,
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptEntity.kt:23:    @ColumnInfo(name = "code_answer") val codeAnswer: String,
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonRatingSubmittedLocalEntity.kt:11:    @ColumnInfo(name = "user_id") val userId: String,
shared/core/persistence/schemas/com.tpov.schoolquiz.shared.core.persistence.AppDatabase/4.json:718:            "fieldPath": "attemptId",
shared/core/persistence/schemas/com.tpov.schoolquiz.shared.core.persistence.AppDatabase/4.json:724:            "fieldPath": "userId",
shared/core/persistence/schemas/com.tpov.schoolquiz.shared.core.persistence.AppDatabase/4.json:748:            "fieldPath": "codeAnswer",
shared/core/persistence/schemas/com.tpov.schoolquiz.shared.core.persistence.AppDatabase/4.json:798:            "fieldPath": "userId",
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonRatingLocalDao.kt:15:    @Query("SELECT COUNT(*) > 0 FROM lesson_rating_submitted_local WHERE user_id = :userId AND lesson_id = :lessonId")
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonRatingLocalDao.kt:16:    fun hasSubmitted(userId: String, lessonId: String): Flow<Boolean>
shared/core/persistence/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/TypeConvertersPhase02Test.kt:13: * Note: MT-05 (DifficultyConverter) removed per ADR-LR-18 — converter deleted, mapper handles Difficulty↔Int.
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:20:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:21:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:22:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:23:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.di.quizzesPresentationModule
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:45:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:49:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:50:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:51:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:52:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:53:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.di.lessonRunnerPresentationModule
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:61:import com.tpov.schoolquiz.shared.core.question_schema.di.questionSchemaModule
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:62:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.lessonRunnerDataModule
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:63:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.lessonRunnerDomainKoinAdapter
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:64:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.AttemptIdProvider
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:65:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RandomSeedProvider
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:66:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:67:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:68:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:69:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:70:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:208:                override fun observeByLesson(userId: String, lessonId: LessonId): Flow<List<Attempt>> = flowOf(emptyList())
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:209:                override fun observeAllByUser(userId: String): Flow<List<Attempt>> = flowOf(emptyList())
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:212:        single<LessonRunnerComponentFactory> {
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:213:            LessonRunnerComponentFactory { _, _, _ -> error("Not wired in KoinModuleWiringTest") }
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:482:     * IT-09a: GIVEN lessonRunnerDataModule WHEN get<LessonAttemptRepository>()
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:487:    fun `it09a lessonAttemptRepository resolves from lessonRunnerDataModule`() {
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:489:            modules(testLessonAttemptDaoStub, testLessonRatingLocalDaoStub, lessonRunnerDataModule)
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:497:     * IT-09b: GIVEN lessonRunnerDataModule WHEN get<LessonRatingRepository>()
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:502:    fun `it09b lessonRatingRepository resolves from lessonRunnerDataModule`() {
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:504:            modules(testLessonAttemptDaoStub, testLessonRatingLocalDaoStub, lessonRunnerDataModule)
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:512:     * IT-09c: GIVEN lessonRunnerDataModule WHEN get<AttemptIdProvider> / RandomSeedProvider / RatingIdProvider
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:518:        startKoin { modules(lessonRunnerDataModule) }
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:527:     * IT-09d: GIVEN lessonRunnerDomainKoinAdapter WHEN get<CompleteAttemptUseCase> / AbortAttemptUseCase / SubmitLessonRatingUseCase
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:538:                lessonRunnerDataModule,
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:539:                lessonRunnerDomainKoinAdapter,
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:540:                questionSchemaModule,
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:551:     * IT-09e: GIVEN lessonRunnerPresentationModule + deps WHEN get<LessonRunnerRootComponent>(parametersOf(ctx, LessonId("l1"), EASY))
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:552:     * THEN resolves to DefaultLessonRunnerRootComponent without exception.
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:566:                lessonRunnerDataModule,
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:567:                lessonRunnerDomainKoinAdapter,
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:568:                lessonRunnerPresentationModule,
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:569:                questionSchemaModule,
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:574:            val component = getKoin().get<LessonRunnerRootComponent> {
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:578:            assertTrue(component is DefaultLessonRunnerRootComponent)
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:588:     * IT-09f: GIVEN questionSchemaModule WHEN get<QuestionContentParser>()
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:594:        startKoin { modules(questionSchemaModule) }
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:605:     * DifficultyConverter removed per ADR-LR-18; mapper handles Difficulty↔Int.
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:620:     * IT-09h: GIVEN lessonRunnerPresentationModule WHEN get<LessonRunnerComponentFactory>()
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:626:        startKoin { modules(lessonRunnerPresentationModule) }
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:627:        val factory = getKoin().get<LessonRunnerComponentFactory>()
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.di
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:4:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:5:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:6:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:7:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.LessonRunnerUseCases
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:13:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:17:val lessonRunnerPresentationModule =
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:20:            DefaultLessonRunnerRootComponent(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:34:            ) as LessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:37:        single<LessonRunnerComponentFactory> {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:38:            LessonRunnerComponentFactory { ctx, lessonId, mode ->
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt:3:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt:4:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt:9:interface LessonRunnerRootComponent {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt:7:fun interface LessonRunnerComponentFactory {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt:12:    ): LessonRunnerRootComponent
shared/core/persistence/src/androidInstrumentedTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonRatingLocalDaoTest.kt:49:        val entity = LessonRatingSubmittedLocalEntity(userId = "u1", lessonId = "l1", submittedAt = 1000L)
shared/core/persistence/src/androidInstrumentedTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonRatingLocalDaoTest.kt:60:        dao.upsert(LessonRatingSubmittedLocalEntity(userId = "u1", lessonId = "l1", submittedAt = 1000L))
shared/core/persistence/src/androidInstrumentedTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonRatingLocalDaoTest.kt:69:        val entity = LessonRatingSubmittedLocalEntity(userId = "u1", lessonId = "l1", submittedAt = 1000L)
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/RunnerStateHolder.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/RunnerStateHolder.kt:4:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/RunnerStateHolder.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/LessonRunnerUseCases.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/LessonRunnerUseCases.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
shared/core/persistence/src/androidInstrumentedTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptDaoTest.kt:21: *   DAO-02: observeAllByUser filters by userId
shared/core/persistence/src/androidInstrumentedTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptDaoTest.kt:45:    // DAO-01: GIVEN in-memory v4 WHEN upsert + observeByLesson THEN returns entity with attemptId="a1"
shared/core/persistence/src/androidInstrumentedTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptDaoTest.kt:49:            attemptId = "a1",
shared/core/persistence/src/androidInstrumentedTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptDaoTest.kt:50:            userId = "u1",
shared/core/persistence/src/androidInstrumentedTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptDaoTest.kt:54:            codeAnswer = "1",
shared/core/persistence/src/androidInstrumentedTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptDaoTest.kt:65:        assert(items.first().attemptId == "a1") {
shared/core/persistence/src/androidInstrumentedTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptDaoTest.kt:66:            "Expected attemptId='a1', got '${items.first().attemptId}'"
shared/core/persistence/src/androidInstrumentedTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptDaoTest.kt:81:        assert(items.none { it.userId == "u2" }) { "Expected no entries from u2" }
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:7:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:8:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:9:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toEventSaveError
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:10:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toQuestionUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:11:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toUiReason
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:12:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toUserAnswer
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:13:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:14:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState.RatingSubmissionState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:18:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.autoAnswerOnTimeout
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:19:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeBestStars
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:20:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeHardUnlocked
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:21:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeStars
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:22:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:23:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.submitAnswer
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:24:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:25:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:26:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:27:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:28:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:40:class DefaultLessonRunnerRootComponent(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:49:) : ComponentContext by componentContext, LessonRunnerRootComponent {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:162:        val current = stateHolder.uiState.value as? RunnerUiState.Result ?: return
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:164:        val userId =
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:166:                is RunnerState.Completed -> domainState.attempt.userId
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:167:                is RunnerState.Aborted -> domainState.attempt.userId
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:172:            val result = useCases.submitRating(userId, lessonId, rating)
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:173:            val latest = stateHolder.uiState.value as? RunnerUiState.Result ?: return@launch
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:263:    ): RunnerUiState.Result {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:265:        val userAttempts = attemptRepository.observeByLesson(attempt.userId, attempt.lessonId).first()
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:273:        return RunnerUiState.Result(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/CrossButton.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/Top3Section.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeClock.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.di
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt:4:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt:5:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultQuizzesComponent
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt:6:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt:20:                lessonRunnerFactory = get<LessonRunnerComponentFactory>(),
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/FillBlankContent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/FillBlankContent.kt:24:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/FillBlankContent.kt:25:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/FillBlankContent.kt:26:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/config/QuizzesConfig.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ResultContent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ResultContent.kt:19:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ResultContent.kt:22:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ResultContent.kt:30:    state: RunnerUiState.Result,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ResultContent.kt:99:                RunnerUiState.Result(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ResultContent.kt:131:                RunnerUiState.Result(
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeQuestionContentParser.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/RatingPromptSection.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/RatingPromptSection.kt:25:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState.RatingSubmissionState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/BlockingResumeDialog.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/OrderingContent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/OrderingContent.kt:23:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/OrderingContent.kt:24:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonRatingRepository.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonRatingRepository.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonRatingRepository.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonRatingRepository.kt:21:            submitted.value = submitted.value + (rating.userId to rating.lessonId.value)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonRatingRepository.kt:27:    override fun hasSubmitted(userId: String, lessonId: LessonId): Flow<Boolean> =
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonRatingRepository.kt:28:        submitted.map { set -> (userId to lessonId.value) in set }
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonRatingRepository.kt:30:    fun setAlreadySubmitted(userId: String, lessonId: LessonId) {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonRatingRepository.kt:31:        submitted.value = submitted.value + (userId to lessonId.value)
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/MultipleChoiceContent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/MultipleChoiceContent.kt:21:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/MultipleChoiceContent.kt:22:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponent.kt:9:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponent.kt:10:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper.toDrillItem
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponent.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponent.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/SingleChoiceContent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/SingleChoiceContent.kt:20:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/SingleChoiceContent.kt:21:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeThemeListComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeThemeListComponent.kt:5:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ThemeListComponent
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeThemeListComponent.kt:6:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeThemeListComponent.kt:7:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesComponent.kt:5:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesComponent.kt:6:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.navigation.QuizzesNavigator
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ImageComponents.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAttemptFixtures.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAttemptFixtures.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAttemptFixtures.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAttemptFixtures.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAttemptFixtures.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAttemptFixtures.kt:14:        userId: String = "user1",
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAttemptFixtures.kt:18:        codeAnswer: String = "9",
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAttemptFixtures.kt:23:        userId = userId,
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAttemptFixtures.kt:28:        codeAnswer = CodeAnswer(codeAnswer),
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonListComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonListComponent.kt:5:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.LessonListComponent
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonListComponent.kt:6:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonListComponent.kt:7:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ExitConfirmDialog.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAuthRepository.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponent.kt:9:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponent.kt:10:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper.toDrillItem
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponent.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponent.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuestListComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuestListComponent.kt:6:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuestListComponent
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuestListComponent.kt:7:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:29:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:30:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:31:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:32:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:33:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:38:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:60:fun LessonRunnerScreen(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:61:    component: LessonRunnerRootComponent,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:72:            is RunnerUiState.Result -> s.mode == Difficulty.HARD
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:110:    component: LessonRunnerRootComponent,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:127:        is RunnerUiState.Result ->
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:154:    component: LessonRunnerRootComponent,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:188:    component: LessonRunnerRootComponent,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:293:private fun LessonRunnerScreenLoadingPreview() {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:295:        LessonRunnerScreen(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:306:private fun LessonRunnerScreenInitFailedPreview() {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:308:        LessonRunnerScreen(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:319:private class PreviewLessonRunnerComponent(initialState: RunnerUiState) : LessonRunnerRootComponent {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeQuestionRepository.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/QuestionProgressHeader.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuizzesComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuizzesComponent.kt:7:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuizzesComponent.kt:8:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuizzesComponent.kt:9:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuizzesComponent.kt:10:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonRepository.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt:3:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt:16:    data class LessonRunner(val component: LessonRunnerRootComponent) : QuizzesChild
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeSectionListComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeSectionListComponent.kt:5:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.SectionListComponent
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeSectionListComponent.kt:6:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeSectionListComponent.kt:7:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:3:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.SaveError
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:4:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:5:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:6:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:7:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswer
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:13:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:15:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError as DomainSaveError
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponent.kt:10:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponent.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper.toQuestDisplayItem
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponent.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeCompleteAttemptUseCase.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeCompleteAttemptUseCase.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeCompleteAttemptUseCase.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/event/RunnerEvent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/ThemeListComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/ThemeListComponent.kt:4:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/ThemeListComponent.kt:5:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAbortAttemptUseCase.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAbortAttemptUseCase.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCardTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCardTest.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/SectionListComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/SectionListComponent.kt:4:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/SectionListComponent.kt:5:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/QuestionUiState.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonAttemptRepository.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonAttemptRepository.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonAttemptRepository.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonAttemptRepository.kt:26:    override fun observeByLesson(userId: String, lessonId: LessonId): Flow<List<Attempt>> =
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonAttemptRepository.kt:28:            list.filter { it.userId == userId && it.lessonId == lessonId }
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonAttemptRepository.kt:31:    override fun observeAllByUser(userId: String): Flow<List<Attempt>> =
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonAttemptRepository.kt:32:        stored.map { list -> list.filter { it.userId == userId } }
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultQuizzesComponent
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:13:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:14:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeBackDispatcher
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:15:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:16:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeAuthRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:17:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonAttemptRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:18:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:19:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuestRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:20:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeSectionRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:21:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeThemeRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:78:            lessonRunnerFactory = LessonRunnerComponentFactory { _, _, _ -> error("Not expected") },
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/RunnerUiState.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/RunnerUiState.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/RunnerUiState.kt:24:    data class Result(
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeStartLessonAttemptUseCase.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeStartLessonAttemptUseCase.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonListComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonListComponent.kt:4:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonListComponent.kt:5:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestCardMenuTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestCardMenuTest.kt:26:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuestListComponent
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestCardMenuTest.kt:27:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeSubmitLessonRatingUseCase.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeSubmitLessonRatingUseCase.kt:12:    suspend operator fun invoke(userId: String, lessonId: LessonId, rating: Int): Result<Unit> {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeSubmitLessonRatingUseCase.kt:14:        lastUserId = userId
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesRotationTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesRotationTest.kt:10:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesRotationTest.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuizzesComponent
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreenTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreenTest.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeSectionListComponent
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreenTest.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreenTest.kt:13:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
android/feature/quizzes-screen/presentation/build.gradle.kts:7:    namespace = "com.tpov.schoolquiz.android.feature.quizzes_screen.presentation"
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:14:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:15:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:20:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:37:    private val lessonRunnerFactory: LessonRunnerComponentFactory,
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:60:                    ?.filterNot { it is QuizzesConfig.LessonRunner }
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:104:                is QuizzesConfig.LessonRunner -> cfg.titles.size
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:158:            is QuizzesConfig.LessonRunner ->
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:159:                QuizzesChild.LessonRunner(
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreenTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreenTest.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuestListComponent
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreenTest.kt:13:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonRepository.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeThemeRepository.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:9:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:10:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:17:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:18:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptStats
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:81:            QuizzesConfig.LessonRunner(
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeBackDispatcher.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeSectionRepository.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuestListComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuestListComponent.kt:5:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonAttemptRepository.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonAttemptRepository.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonAttemptRepository.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonAttemptRepository.kt:26:    override fun observeByLesson(userId: String, lessonId: LessonId): Flow<List<Attempt>> =
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonAttemptRepository.kt:27:        stored.map { list -> list.filter { it.userId == userId && it.lessonId == lessonId } }
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonAttemptRepository.kt:29:    override fun observeAllByUser(userId: String): Flow<List<Attempt>> =
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonAttemptRepository.kt:30:        stored.map { list -> list.filter { it.userId == userId } }
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuestRepository.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeAuthRepository.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:3:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:111:    // ── SER-06 — QuizzesConfig.LessonRunner ──────────────────────────────────
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:120:        val original = QuizzesConfig.LessonRunner(
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:127:        val runner = assertIs<QuizzesConfig.LessonRunner>(decoded)
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:140:        val original = QuizzesConfig.LessonRunner(
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:147:        val runner = assertIs<QuizzesConfig.LessonRunner>(decoded)
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponentTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponentTest.kt:8:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponentTest.kt:9:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeStackNavigation
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponentTest.kt:10:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeThemeRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponentTest.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponentTest.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeStackNavigation.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeStackNavigation.kt:5:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreen.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreen.kt:37:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuestListComponent
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreen.kt:38:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponentTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponentTest.kt:8:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponentTest.kt:9:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeSectionRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponentTest.kt:10:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeStackNavigation
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponentTest.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponentTest.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/SectionDrillMapper.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/SectionDrillMapper.kt:3:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/QuestToDisplayItemMapper.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/DrillItemMapperTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreen.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreen.kt:25:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.SectionListComponent
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreen.kt:26:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreen.kt:27:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/ThemeDrillMapper.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/ThemeDrillMapper.kt:3:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:8:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:9:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:10:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeAbortAttemptUseCase
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:11:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeAttemptFixtures
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:12:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeClock
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:13:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeCompleteAttemptUseCase
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:14:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeLessonAttemptRepository
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:15:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeLessonRatingRepository
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:16:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeLessonRepository
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:17:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeStartLessonAttemptUseCase
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:18:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeSubmitLessonRatingUseCase
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:19:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:26:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeStars
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:27:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:28:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:29:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:30:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:31:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:32:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:33:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:34:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:35:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:53: * Presentation unit tests for [DefaultLessonRunnerRootComponent].
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:59: * Open Question: RunnerUiState.Result is expected to contain bestStarsRawTenths: Int
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:64:class DefaultLessonRunnerRootComponentTest {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:107:    ): DefaultLessonRunnerRootComponent {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:111:        return DefaultLessonRunnerRootComponent(
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:115:            useCases = com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.LessonRunnerUseCases(
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:137:        codeAnswerIndex: Int = 0,
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:146:            codeAnswerIndex = codeAnswerIndex,
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:161:        userId: String = "user1",
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:167:            makeRunnerQuestion("q${it + 1}", codeAnswerIndex = it)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:174:        userId = userId,
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:181:        codeAnswer = CodeAnswer("0".repeat(eligibleSize)),
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:207:    private fun advanceToResultState(component: DefaultLessonRunnerRootComponent) {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:277:        assertIs<RunnerUiState.Result>(component.uiState.value)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:313:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:327:            codeAnswer = "15111",
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:334:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:337:        // hardUnlocked requires allShownAnswersAre9 → codeAnswer "15111" is false
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:338:        assertFalse(result.hardUnlocked, "hardUnlocked must be false when codeAnswer has non-9 digits")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:353:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:370:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:379:    // Open Question: RunnerUiState.Result must have bestStarsRawTenths: Int and hardUnlocked: Boolean
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:382:        val attempt = FakeAttemptFixtures.fixtureAttempt(percentScore = 50, codeAnswer = "1")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:389:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:402:            codeAnswer = "9",
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:414:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:420:    // PT-17: GIVEN EASY attempt with codeAnswer not all-9s
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:426:            codeAnswer = "8",
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:437:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:593:            attemptIdProvider = { AttemptId("complete-test-id") },
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:606:    // ── PT-27: abort after 3 answers → codeAnswer positions correct ──────────
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:610:    //        THEN attempt.codeAnswer positions [0..2] scored (≥'1'), [3..19]='1', rest='0'
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:612:    fun `abort_after3_codeAnswer_positions_correct`() {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:613:        val questions = (0 until 20).map { makeRunnerQuestion("q${it + 1}", codeAnswerIndex = it) }
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:619:        ).copy(eligibleSize = 20, codeAnswer = CodeAnswer("0".repeat(20)))
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:623:            attemptIdProvider = { AttemptId("abort-test-id") },
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:635:    // PT-27 (corrected): 5 pool questions, answer 3, then abort → codeAnswer check
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:637:    fun `abort_after3_of5_codeAnswer_positions_correct`() {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:638:        val pool = (0 until 5).map { makeRunnerQuestion("q${it + 1}", codeAnswerIndex = it) }
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:640:            userId = "user1",
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:647:            codeAnswer = CodeAnswer("00000"),
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:656:            attemptIdProvider = { AttemptId("abort-test-id") },
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:666:        val code = saved.codeAnswer.raw
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:668:        assertTrue(code[0] > '0', "codeAnswer[0] must be scored")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:669:        assertTrue(code[1] > '0', "codeAnswer[1] must be scored")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:670:        assertTrue(code[2] > '0', "codeAnswer[2] must be scored")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:672:        assertEquals('1', code[3], "codeAnswer[3] must be '1' (unanswered in pool)")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:673:        assertEquals('1', code[4], "codeAnswer[4] must be '1' (unanswered in pool)")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:687:            attemptIdProvider = { AttemptId("ver-test-id") },
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:711:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:729:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:741:            attempt = FakeAttemptFixtures.fixtureAttempt(codeAnswer = "5"),
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:747:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:764:        assertIs<RunnerUiState.Result>(component.uiState.value)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:789:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:815:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:828:            attempt = FakeAttemptFixtures.fixtureAttempt(codeAnswer = "5", percentScore = 50),
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:834:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:847:            codeAnswer = "9",
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:860:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:924:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:943:        assertIs<RunnerUiState.Result>(component.uiState.value)
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/QuestToDisplayItemMapperTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/LessonDrillMapper.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/LessonDrillMapper.kt:3:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:8:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:9:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeAuthRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:10:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonAttemptRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeStackNavigation
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:13:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:17:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:18:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:19:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:20:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:101:        userId: String = "user1",
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:104:        codeAnswer: String = "9",
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:107:        id = AttemptId("attempt-$userId-$lessonId"),
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:108:        userId = userId,
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:113:        codeAnswer = CodeAnswer(codeAnswer),
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:146:     * codeAnswer "9999" → all chars '9' AND at least one '9' → allShownAnswersAre9=true.
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:157:                    userId = "user1",
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:160:                    codeAnswer = "9999",
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:177:     * codeAnswer "8999" → first digit '8' ≠ '9' → allShownAnswersAre9=false.
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:188:                    userId = "user1",
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:191:                    codeAnswer = "8999", // first digit '8' → allShownAnswersAre9=false
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:219:                    userId = "user1",
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:222:                    codeAnswer = "777",
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:272:                    userId = "user1",
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:275:                    codeAnswer = "9999",
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonListScreen.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonListScreen.kt:24:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.LessonListComponent
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonListScreen.kt:25:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonListScreen.kt:26:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/navigation/QuizzesNavigator.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.navigation
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/ThemeListScreen.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/ThemeListScreen.kt:25:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ThemeListComponent
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/ThemeListScreen.kt:26:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/ThemeListScreen.kt:27:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:9:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:10:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeAbortAttemptUseCase
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:11:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeAttemptFixtures
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:12:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeClock
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:13:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeCompleteAttemptUseCase
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:14:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeLessonAttemptRepository
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:15:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeLessonRatingRepository
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:16:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeLessonRepository
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:17:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeStartLessonAttemptUseCase
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:18:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeSubmitLessonRatingUseCase
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:19:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:25:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:26:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:27:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:28:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:41: * Integration tests for DefaultLessonRunnerRootComponent — state holder rotation and lifecycle.
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:98:    ): DefaultLessonRunnerRootComponent {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:106:        return DefaultLessonRunnerRootComponent(
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:110:            useCases = com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.LessonRunnerUseCases(
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:139:        codeAnswerIndex: Int = 0,
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:142:        order = codeAnswerIndex,
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:143:        codeAnswerIndex = codeAnswerIndex,
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:158:        userId = "user1",
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:165:        codeAnswer = CodeAnswer("0".repeat(questions)),
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/di/AppShellPresentationModule.kt:7:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:8:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:9:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeAuthRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:10:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonAttemptRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeStackNavigation
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:13:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:14:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:150:        assertIs<QuizzesConfig.LessonRunner>(pushed)
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:166:        val pushed = fakeNavigation.pushedConfigs.last() as QuizzesConfig.LessonRunner
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCard.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCard.kt:20:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt:16:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.LessonRunnerScreen
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt:17:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt:18:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt:42:            is QuizzesChild.LessonRunner ->
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt:43:                LessonRunnerScreen(
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:10:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultQuizzesComponent
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:13:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:14:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeAuthRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:15:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonAttemptRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:16:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:17:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuestRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:18:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeSectionRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:19:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeThemeRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:86:            lessonRunnerFactory = LessonRunnerComponentFactory { _, _, _ -> error("Not expected") },
android/feature/lesson-runner/presentation/build.gradle.kts:6:    namespace = "com.tpov.schoolquiz.android.feature.lesson_runner.presentation"
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/QuestListUiState.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/LessonItemUi.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponentTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponentTest.kt:9:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponentTest.kt:10:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuestRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponentTest.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeStackNavigation
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponentTest.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/RatingPromptTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/RatingPromptTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/RatingPromptTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonAttemptRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/RatingPromptTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRatingRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/RatingPromptTest.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/RatingPromptTest.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/RatingPromptTest.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/RatingPromptTest.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/RatingPromptTest.kt:21: * Rule (Business Rule 16): ratingPrompt = allShownAnswersAre9 AND !hasSubmitted(userId, lessonId)
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/RatingPromptTest.kt:35:        attemptIdProvider = idProvider,
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/RatingPromptTest.kt:38:    private fun stateWithCodeAnswer(raw: String, userId: String = "user1", lessonId: String = "lesson1") =
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/RatingPromptTest.kt:40:            userId = userId,
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/RatingPromptTest.kt:42:            codeAnswer = CodeAnswer(raw),
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/HierarchyListUiState.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SelectSubsetTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SelectSubsetTest.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.selectSubset
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SelectSubsetTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SelectSubsetTest.kt:20:    private fun makeValidQuestion(id: String, order: Int = 0, codeAnswerIndex: Int = 0) =
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SelectSubsetTest.kt:24:            codeAnswerIndex = codeAnswerIndex,
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SelectSubsetTest.kt:29:        (1..size).map { makeValidQuestion("q$it", order = it, codeAnswerIndex = it - 1) }
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/HierarchyItemUi.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/LessonListUiState.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/PercentScoreComputeTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/PercentScoreComputeTest.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computePercentScore
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/PercentScoreComputeTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/PercentScoreComputeTest.kt:12: *   nonZero = codeAnswer.filter { it != '0' }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/PercentScoreComputeTest.kt:16: * NOTE: Requires `computePercentScore(codeAnswer: CodeAnswer): PercentScore`
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/PercentScoreComputeTest.kt:22:    fun `given codeAnswer 9999 then percentScore 100`() {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/PercentScoreComputeTest.kt:29:    fun `given codeAnswer 5555 then percentScore 50`() {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/PercentScoreComputeTest.kt:36:    fun `given codeAnswer 9050 then percentScore 75`() {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/PercentScoreComputeTest.kt:44:    fun `given codeAnswer 1111 then percentScore 0`() {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/PercentScoreComputeTest.kt:51:    fun `given codeAnswer all 0 then percentScore 0`() {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerTest.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.allShownAnswersAre9
shared/feature/lesson-runner/domain/build.gradle.kts:6:    namespace = "com.tpov.schoolquiz.shared.feature.lesson_runner.domain"
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:22:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonAttemptRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRatingRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:34:        attemptIdProvider = idProvider,
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:40:        attemptIdProvider = idProvider,
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:48:        val codeAnswer = CodeAnswer("9999")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:50:            userId = "user42",
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:54:            codeAnswer = codeAnswer,
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:64:        assertEquals("user42", saved.userId, "userId must match state.userId")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:68:        assertEquals("9999", saved.codeAnswer.raw, "codeAnswer must match state.codeAnswer")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:74:    fun `given exit after 3 questions of 20 when AbortAttemptUseCase invoked then codeAnswer has 3 real plus 17 ones`() = runTest {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:76:        // Build playOrder: 20 questions with codeAnswerIndex 0..19
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:78:            makeRunnerQuestion(id = "q$i", order = i, codeAnswerIndex = i)
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:80:        // Simulate 3 answered: codeAnswer positions 0,1,2 = '9','9','9', rest '0'
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:81:        val codeAnswerAfter3 = "999" + "0".repeat(17)
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:83:            userId = "user1",
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:89:            codeAnswer = CodeAnswer(codeAnswerAfter3),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:96:        assertEquals(20, savedAttempt.codeAnswer.raw.length, "codeAnswer.length == eligibleSize == 20")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:98:        val firstThree = savedAttempt.codeAnswer.raw.substring(0, 3)
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:101:        val last17 = savedAttempt.codeAnswer.raw.substring(3)
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonAttemptRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRatingRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeAuthRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionContentParser
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.submitAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:13:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:14:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:15:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:16:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:17:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:18:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:40:    private val attemptIdProvider = { AttemptId("attempt-${idCounter++}") }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:54:        attemptIdProvider = attemptIdProvider,
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:90:        val questions = listOf(makeRunnerQuestion(id = "q1", order = 0, codeAnswerIndex = 0))
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:95:            codeAnswer = CodeAnswer("0"),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:113:            makeRunnerQuestion(id = "q1", order = 0, codeAnswerIndex = 0),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:114:            makeRunnerQuestion(id = "q2", order = 1, codeAnswerIndex = 1),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:115:            makeRunnerQuestion(id = "q3", order = 2, codeAnswerIndex = 2),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:121:            codeAnswer = CodeAnswer("900"),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:135:            playOrder = listOf(makeRunnerQuestion(codeAnswerIndex = 0)),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:138:            codeAnswer = CodeAnswer("9"),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:149:            playOrder = listOf(makeRunnerQuestion(codeAnswerIndex = 0)),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:152:            codeAnswer = CodeAnswer("9"),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:159:    fun `given submitAnswer records score digit in codeAnswer at correct position`() {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:160:        // Verify that submitAnswer writes the score to the correct codeAnswerIndex position
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:162:            makeRunnerQuestion(id = "q1", order = 0, codeAnswerIndex = 2),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:168:            codeAnswer = CodeAnswer("00000"),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:172:        assertEquals('9', result.codeAnswer.raw[2], "Score written at codeAnswerIndex=2")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:173:        assertEquals('0', result.codeAnswer.raw[0], "Other positions unchanged")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeAuthRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionContentParser
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.submitAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:13:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:22: * Tests full pipeline: StartLessonAttemptUseCase → RunnerState.Ready.codeAnswer.
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:56:    fun `given lesson with 5 EASY questions when EASY attempt then codeAnswer length 5 and all in pool`() = runTest {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:57:        // Spec scenario #13: eligible=5 < poolSize=20 → all 5 in subset; codeAnswer.length=5
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:64:        assertEquals(5, state.codeAnswer.raw.length, "Spec scenario #13: codeAnswer.length = 5")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:74:            currentState.codeAnswer.raw.none { it == '0' },
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:82:    fun `given lesson with 50 EASY questions when EASY attempt then codeAnswer length 50 with 20 non-zero after completion`() = runTest {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:90:        assertEquals(50, state.codeAnswer.raw.length, "Spec scenario #14: codeAnswer.length = 50")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:100:        val nonZero = currentState.codeAnswer.raw.count { it != '0' }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:101:        val zeros = currentState.codeAnswer.raw.count { it == '0' }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:109:    fun `given lesson with 50 EASY and 50 HARD when EASY attempt then codeAnswer length 50 mode EASY`() = runTest {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:110:        // Spec scenario #15: EASY attempt filters only EASY questions; codeAnswer.length = 50
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:118:        assertEquals(50, state.codeAnswer.raw.length, "Spec scenario #15: codeAnswer.length=50")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:125:    fun `given lesson with 50 EASY and 50 HARD when HARD attempt then codeAnswer length 50 mode HARD`() = runTest {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:126:        // Spec scenario #16: HARD attempt filters only HARD questions; codeAnswer.length = 50
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:134:        assertEquals(50, state.codeAnswer.raw.length, "Spec scenario #16: codeAnswer.length=50")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.AttemptIdProvider
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:21:        // Spec scenario #13a: attemptIdProvider_interface_exists
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:32:            override fun provide(userId: String, lessonId: LessonId): RatingId =
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:33:                RatingId("rating-$userId")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonAttemptRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRatingRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeAuthRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionContentParser
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:13:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:14:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:15:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:16:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:17:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:18:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:40:    private val attemptIdProvider = { AttemptId("attempt-${idCounter++}") }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:41:    private val ratingIdProvider = { userId: String, lessonId: LessonId -> RatingId("$userId:${lessonId.value}") }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:58:        attemptIdProvider = attemptIdProvider,
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:96:            codeAnswer = CodeAnswer("9"),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:123:            codeAnswer = CodeAnswer("9"),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TimerComputeTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TimerComputeTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TimerComputeTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeAuthRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonAttemptRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRatingRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionContentParser
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:13:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:14:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:15:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:16:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:17:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:18:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:19:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:42:    private val attemptIdProvider = { AttemptId("attempt-${idCounter++}") }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:43:    private val ratingIdProvider = { userId: String, lessonId: LessonId -> RatingId("$userId:${lessonId.value}") }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:58:        attemptIdProvider = attemptIdProvider,
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:77:            codeAnswer = CodeAnswer("9"),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.autoAnswerOnTimeout
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:28:        // After auto-answer, the score for this question is recorded (codeAnswer updated)
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:31:            resultState.codeAnswer.raw[state.playOrder[currentIndex].codeAnswerIndex] in '1'..'9',
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:61:        val codeAnswerIndex = state.playOrder[state.indexInPool].codeAnswerIndex
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:63:            resultState.codeAnswer.raw[codeAnswerIndex] in '1'..'9',
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:79:        val codeAnswerIndex = state.playOrder[state.indexInPool].codeAnswerIndex
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:81:            resultState.codeAnswer.raw[codeAnswerIndex] in '1'..'9',
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:95:            result1.codeAnswer.raw,
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:96:            result2.codeAnswer.raw,
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StarsComputeTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StarsComputeTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeStars
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StarsComputeTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/BestStarsHardUnlockedTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/BestStarsHardUnlockedTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeBestStars
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/BestStarsHardUnlockedTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeHardUnlocked
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/BestStarsHardUnlockedTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Stars
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/BestStarsHardUnlockedTest.kt:31:            makeAttempt(codeAnswer = "5", percentScore = 50, mode = Difficulty.EASY),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/BestStarsHardUnlockedTest.kt:32:            makeAttempt(codeAnswer = "7", percentScore = 75, mode = Difficulty.EASY),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/BestStarsHardUnlockedTest.kt:33:            makeAttempt(codeAnswer = "9", percentScore = 100, mode = Difficulty.EASY),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/BestStarsHardUnlockedTest.kt:43:            makeAttempt(codeAnswer = "7", percentScore = 75, mode = Difficulty.EASY),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/BestStarsHardUnlockedTest.kt:44:            makeAttempt(codeAnswer = "9", percentScore = 50, mode = Difficulty.HARD),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/BestStarsHardUnlockedTest.kt:61:        // Spec scenario #34: codeAnswer all '9' → allShownAnswersAre9 = true
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/BestStarsHardUnlockedTest.kt:63:            makeAttempt(codeAnswer = "9999", percentScore = 100, mode = Difficulty.EASY),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/BestStarsHardUnlockedTest.kt:73:            makeAttempt(codeAnswer = "9989", percentScore = 95, mode = Difficulty.EASY),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/BestStarsHardUnlockedTest.kt:83:            makeAttempt(codeAnswer = "1111", percentScore = 0, mode = Difficulty.HARD),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/BestStarsHardUnlockedTest.kt:93:            makeAttempt(codeAnswer = "9999", percentScore = 100, mode = Difficulty.HARD),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/BestStarsHardUnlockedTest.kt:103:            makeAttempt(codeAnswer = "8999", percentScore = 90, mode = Difficulty.EASY),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/BestStarsHardUnlockedTest.kt:104:            makeAttempt(codeAnswer = "9999", percentScore = 100, mode = Difficulty.HARD),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeClock.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeAuthRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionContentParser
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.autoAnswerOnTimeout
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.buildCodeAnswerOnAbort
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:13:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:14:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:15:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:92:        assertEquals(1, result.eligibleSize, "Spec scenario #60: only valid question counted; codeAnswer.length=1")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:93:        assertEquals(1, result.codeAnswer.raw.length, "codeAnswer.length == eligibleSize == 1")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:104:            codeAnswer = CodeAnswer("91900000"),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:112:        assertEquals(original.codeAnswer.raw, restored.codeAnswer.raw, "codeAnswer preserved")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:146:            makeRunnerQuestion(id = "q1", order = 0, codeAnswerIndex = 0),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:147:            makeRunnerQuestion(id = "q2", order = 1, codeAnswerIndex = 1),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:153:            codeAnswer = CodeAnswer("00"),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:168:        // codeAnswer = "0000" initially; after q0 auto-scored → position 0 gets digit
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:170:            makeRunnerQuestion(id = "q1", order = 0, codeAnswerIndex = 0),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:171:            makeRunnerQuestion(id = "q3", order = 2, codeAnswerIndex = 2),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:177:            codeAnswer = CodeAnswer("9000"),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeQuestionContentParser.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/RunFakeComponent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/RunFakeComponent.kt:3:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/RunFakeComponent.kt:4:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/RunFakeComponent.kt:5:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/RunFakeComponent.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/RunFakeComponent.kt:16:) : LessonRunnerRootComponent {
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/fake/FakeLessonAttemptDao.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/fake/FakeLessonAttemptDao.kt:18:        val idx = store.indexOfFirst { it.attemptId == entity.attemptId }
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/fake/FakeLessonAttemptDao.kt:24:    override fun observeByLesson(userId: String, lessonId: String): Flow<List<LessonAttemptEntity>> =
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/fake/FakeLessonAttemptDao.kt:25:        _flow.map { list -> list.filter { it.userId == userId && it.lessonId == lessonId } }
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/fake/FakeLessonAttemptDao.kt:27:    override fun observeAllByUser(userId: String): Flow<List<LessonAttemptEntity>> =
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/fake/FakeLessonAttemptDao.kt:28:        _flow.map { list -> list.filter { it.userId == userId } }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonRatingRepository.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonRatingRepository.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonRatingRepository.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonRatingRepository.kt:21:            submitted.value = submitted.value + (rating.userId to rating.lessonId.value)
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonRatingRepository.kt:27:    override fun hasSubmitted(userId: String, lessonId: LessonId): Flow<Boolean> =
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonRatingRepository.kt:28:        submitted.map { set -> (userId to lessonId.value) in set }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonRatingRepository.kt:30:    fun setAlreadySubmitted(userId: String, lessonId: LessonId) {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonRatingRepository.kt:31:        submitted.value = submitted.value + (userId to lessonId.value)
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonAttemptRepository.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonAttemptRepository.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonAttemptRepository.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonAttemptRepository.kt:26:    override fun observeByLesson(userId: String, lessonId: LessonId): Flow<List<Attempt>> =
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonAttemptRepository.kt:28:            list.filter { it.userId == userId && it.lessonId == lessonId }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonAttemptRepository.kt:31:    override fun observeAllByUser(userId: String): Flow<List<Attempt>> =
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonAttemptRepository.kt:32:        stored.map { list -> list.filter { it.userId == userId } }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeQuestionRepository.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeAuthRepository.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/fake/FakeLessonRatingLocalDao.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/fake/FakeLessonRatingLocalDao.kt:15:        submitted.add(entity.userId to entity.lessonId)
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/fake/FakeLessonRatingLocalDao.kt:20:    override fun hasSubmitted(userId: String, lessonId: String): Flow<Boolean> =
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/fake/FakeLessonRatingLocalDao.kt:21:        _trigger.map { submitted.contains(userId to lessonId) }
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:11:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:12:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.RunFakeComponent
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:13:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:14:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:15:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.LessonRunnerScreen
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:18:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:29:class LessonRunnerScreenTest {
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:69:    ) = RunnerUiState.Result(
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:95:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:114:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:132:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:147:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:163:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:183:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:201:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:219:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:242:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:265:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:290:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:307:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:326:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:344:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:359:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:375:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:392:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:401:    @Ignore("Phase-06 scope: belongs to LessonItemCard, not LessonRunnerScreen")
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:431:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:448:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:463:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:479:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:500:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:513:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:530:                LessonRunnerScreen(fakeComponent, onNavigateBack = { navCalled = true }, onSegmentClick = {})
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Stars
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:58:                userId = "user1",
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:73:                userId = "user1",
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:103:    fun `given Attempt with blank userId then throws`() {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:105:        assertFailsWith<IllegalArgumentException>("Spec scenario #69: blank userId") {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:108:                userId = "",
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:113:                codeAnswer = CodeAnswer("9"),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:125:                userId = "user1",
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:130:                codeAnswer = CodeAnswer("9"),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:142:                userId = "user1",
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:147:                codeAnswer = CodeAnswer("9"),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:159:        // Spec scenario C-01: attemptId_value_field_accessible
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonRatingRepositoryImplTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonRatingRepositoryImplTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake.FakeLessonRatingLocalDao
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonRatingRepositoryImplTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.LessonRatingRepositoryImpl
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonRatingRepositoryImplTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonRatingRepositoryImplTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonRatingRepositoryImplTest.kt:25:        userId: String = "u1",
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonRatingRepositoryImplTest.kt:28:        id = RatingId("rating-$userId-$lessonId"),
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonRatingRepositoryImplTest.kt:29:        userId = userId,
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonRatingRepositoryImplTest.kt:41:        val rating = makeRating(userId = "u1", lessonId = "l1")
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonRatingRepositoryImplTest.kt:55:        repo.submit(makeRating(userId = "u1", lessonId = "l1"))
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonRepository.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper.toDomain
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper.toEntity
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:24:        codeAnswer: String = "9",
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:30:        userId = "u1",
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:34:        codeAnswer = CodeAnswer(codeAnswer),
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:55:    // Map-03: GIVEN Attempt(id=AttemptId("uuid-123")) WHEN toEntity() THEN entity.attemptId == "uuid-123"
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:57:    fun attemptMapper_toEntity_attemptIdValue() {
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:60:        assertEquals("uuid-123", entity.attemptId, "entity.attemptId must equal AttemptId.value (ADR-LR-12)")
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:63:    // Map-04: GIVEN entity(isHard=1, attemptId="abc", codeAnswer="19", percentScore=85)
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:65:    //         THEN mode=HARD, id.value="abc", codeAnswer.raw="19", percentScore.raw=85
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:69:            attemptId = "abc",
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:70:            userId = "u1",
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:74:            codeAnswer = "19",
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:82:        assertEquals("abc", domain.id.value, "id.value must equal entity.attemptId")
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:83:        assertEquals("19", domain.codeAnswer.raw, "codeAnswer.raw must equal entity.codeAnswer")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.evaluateAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswer
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:55:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:56:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen.QuizzesScreen
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonAttemptRepositoryImplTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonAttemptRepositoryImplTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake.FakeLessonAttemptDao
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonAttemptRepositoryImplTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.LessonAttemptRepositoryImpl
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonAttemptRepositoryImplTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonAttemptRepositoryImplTest.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonAttemptRepositoryImplTest.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonAttemptRepositoryImplTest.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonAttemptRepositoryImplTest.kt:29:        userId: String = "u1",
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonAttemptRepositoryImplTest.kt:33:        userId = userId,
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonAttemptRepositoryImplTest.kt:37:        codeAnswer = CodeAnswer("9"),
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonAttemptRepositoryImplTest.kt:59:        assertEquals(attempt.userId, items.first().userId)
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:13:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:14:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:15:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:16:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:17:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:18:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:123:    codeAnswerIndex: Int = 0,
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:128:    codeAnswerIndex = codeAnswerIndex,
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:135:    userId: String = "user1",
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:142:    codeAnswer: CodeAnswer = CodeAnswer("0".repeat(eligibleSize)),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:147:    userId = userId,
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:154:    codeAnswer = codeAnswer,
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:164:    userId: String = "user1",
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:168:    codeAnswer: String = "9",
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:172:    id = AttemptId("attempt-$userId-$lessonId"),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:173:    userId = userId,
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:178:    codeAnswer = CodeAnswer(codeAnswer),
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/fake/StubQuestComponents.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/fake/StubQuestComponents.kt:13:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/fake/StubQuestComponents.kt:14:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/app-shell/presentation/build.gradle.kts:18:    implementation(project(":android:core:navigation"))
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Score
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Stars
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerDuration
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:13:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswer
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:14:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.allShownAnswersAre9
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:15:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:29:    val codeAnswerChars = state.codeAnswer.raw.toCharArray()
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:30:    codeAnswerChars[currentQuestion.codeAnswerIndex] = ('0' + score.raw)
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:31:    val newCodeAnswer = CodeAnswer(String(codeAnswerChars))
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:44:        codeAnswer = newCodeAnswer,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:130:    attempts.any { it.mode == Difficulty.EASY && it.codeAnswer.allShownAnswersAre9 }
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:170:    val chars = state.codeAnswer.raw.toCharArray()
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:172:        chars[state.playOrder[i].codeAnswerIndex] = '1'
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:180: * Derives [PercentScore] from [codeAnswer] using integer division.
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:183:internal fun computePercentScore(codeAnswer: CodeAnswer): PercentScore {
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:184:    val nonZero = codeAnswer.raw.filter { it != '0' }
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:28:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultQuizzesComponent
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:29:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuestListComponent
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:30:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:31:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:32:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:33:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:40:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:45:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:46:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:262:                    override fun observeByLesson(userId: String, lessonId: LessonId): Flow<List<Attempt>> = flowOf(emptyList())
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:263:                    override fun observeAllByUser(userId: String): Flow<List<Attempt>> = flowOf(emptyList())
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:269:                lessonRunnerFactory = LessonRunnerComponentFactory { _, _, _ -> error("Not expected") },
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.di
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt:15: * This module is NOT registered in production — use lessonRunnerDomainKoinAdapter instead (ADR-LR-09 C1).
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt:33:            attemptIdProvider = get(),
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt:40:            attemptIdProvider = get(),
android/feature/app-shell/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/AppShellScreenTest.kt:35:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
android/feature/app-shell/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/AppShellScreenTest.kt:36:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
android/feature/app-shell/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/AppShellScreenTest.kt:37:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
shared/feature/lesson-runner/data/src/jvmAndAndroidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultRandomSeedProvider.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider
shared/feature/lesson-runner/data/src/jvmAndAndroidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultRandomSeedProvider.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RandomSeedProvider
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/LessonRatingRepository.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/LessonRatingRepository.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/LessonRatingRepository.kt:16:     * Observes whether [userId] has already submitted a rating for [lessonId].
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/LessonRatingRepository.kt:17:     * Backed by Room compound PK (userId, lessonId) table.
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/LessonRatingRepository.kt:19:    fun hasSubmitted(userId: String, lessonId: LessonId): Flow<Boolean>
shared/feature/lesson-runner/data/src/jvmAndAndroidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultAttemptIdProvider.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider
shared/feature/lesson-runner/data/src/jvmAndAndroidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultAttemptIdProvider.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/data/src/jvmAndAndroidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultAttemptIdProvider.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.AttemptIdProvider
shared/feature/lesson-runner/data/src/jvmAndAndroidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultRatingIdProvider.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider
shared/feature/lesson-runner/data/src/jvmAndAndroidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultRatingIdProvider.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
shared/feature/lesson-runner/data/src/jvmAndAndroidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultRatingIdProvider.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
shared/feature/lesson-runner/data/src/jvmAndAndroidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultRatingIdProvider.kt:9:    override fun provide(userId: String, lessonId: LessonId): RatingId {
shared/feature/lesson-runner/data/src/jvmAndAndroidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultRatingIdProvider.kt:10:        val input = "$userId:${lessonId.value}"
shared/core/question-schema/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/di/QuestionSchemaModule.kt:7:val questionSchemaModule = module {
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/LessonAttemptRepository.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/LessonAttemptRepository.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeBestStars
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/LessonAttemptRepository.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeHardUnlocked
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/LessonAttemptRepository.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/LessonAttemptRepository.kt:25:     * Observes all attempts for [userId] + [lessonId], sorted by completedAt DESC.
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/LessonAttemptRepository.kt:27:    fun observeByLesson(userId: String, lessonId: LessonId): Flow<List<Attempt>>
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/LessonAttemptRepository.kt:30:     * Observes all attempts for [userId] across all lessons.
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/LessonAttemptRepository.kt:32:    fun observeAllByUser(userId: String): Flow<List<Attempt>>
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/LessonAttemptRepository.kt:38:    fun observeAllStatsByUser(userId: String): Flow<Map<LessonId, LessonAttemptStats>> =
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/LessonAttemptRepository.kt:39:        observeAllByUser(userId).map { attempts ->
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.buildCodeAnswerOnAbort
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computePercentScore
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt:15: * userId from [state.userId] — no auth read.
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt:23:    private val attemptIdProvider: () -> AttemptId,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt:31:            id = attemptIdProvider(),
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt:32:            userId = state.userId,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt:37:            codeAnswer = finalCodeAnswer,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.selectSubset
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:13:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:14:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:23: *   selectSubset → assign codeAnswerIndex → RunnerState.Ready.
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:37:        val userId = authRepository.currentUid()
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:51:                    codeAnswerIndex = -1,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:68:        // Assign codeAnswerIndex per sorted eligible position (full pool, not subset)
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:70:        val indexed = sorted.mapIndexed { idx, rq -> rq.copy(codeAnswerIndex = idx) }
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:87:            userId = userId,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:94:            codeAnswer = initialCodeAnswer,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computePercentScore
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.allShownAnswersAre9
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:17: * userId taken from [state.userId] — no repeated auth read.
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:18: * ratingPrompt = codeAnswer.allShownAnswersAre9 AND !hasSubmittedRating.
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:26:    private val attemptIdProvider: () -> AttemptId,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:30:        val percentScore = computePercentScore(state.codeAnswer)
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:33:            id = attemptIdProvider(),
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:34:            userId = state.userId,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:39:            codeAnswer = state.codeAnswer,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:51:        val ratingPrompt = state.codeAnswer.allShownAnswersAre9 &&
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:52:            !ratingRepository.hasSubmitted(state.userId, state.lessonId).first()
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonRatingRepositoryImpl.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonRatingRepositoryImpl.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper.toEntity
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonRatingRepositoryImpl.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonRatingRepositoryImpl.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonRatingRepositoryImpl.kt:19:    override fun hasSubmitted(userId: String, lessonId: LessonId): Flow<Boolean> =
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonRatingRepositoryImpl.kt:20:        ratingLocalDao.hasSubmitted(userId, lessonId.value)
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonAttemptRepositoryImpl.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonAttemptRepositoryImpl.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper.toDomain
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonAttemptRepositoryImpl.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper.toEntity
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonAttemptRepositoryImpl.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonAttemptRepositoryImpl.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonAttemptRepositoryImpl.kt:21:    override fun observeByLesson(userId: String, lessonId: LessonId): Flow<List<Attempt>> =
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonAttemptRepositoryImpl.kt:22:        attemptDao.observeByLesson(userId, lessonId.value)
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonAttemptRepositoryImpl.kt:25:    override fun observeAllByUser(userId: String): Flow<List<Attempt>> =
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonAttemptRepositoryImpl.kt:26:        attemptDao.observeAllByUser(userId)
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/SubmitLessonRatingUseCase.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/SubmitLessonRatingUseCase.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/SubmitLessonRatingUseCase.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/SubmitLessonRatingUseCase.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/SubmitLessonRatingUseCase.kt:13: * [userId] is passed explicitly (from completed attempt snapshot) to avoid auth race.
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/SubmitLessonRatingUseCase.kt:23:    private val ratingIdProvider: (userId: String, lessonId: LessonId) -> RatingId,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/SubmitLessonRatingUseCase.kt:26:        userId: String,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/SubmitLessonRatingUseCase.kt:37:        val ratingId = ratingIdProvider(userId, lessonId)
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/SubmitLessonRatingUseCase.kt:42:            userId = userId,
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonRatingMapper.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonRatingMapper.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonRatingMapper.kt:7:    userId = userId,
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonAttemptMapper.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonAttemptMapper.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonAttemptMapper.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonAttemptMapper.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonAttemptMapper.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonAttemptMapper.kt:12:    attemptId = id.value,
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonAttemptMapper.kt:13:    userId = userId,
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonAttemptMapper.kt:17:    codeAnswer = codeAnswer.raw,
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonAttemptMapper.kt:23:    id = AttemptId(attemptId),
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonAttemptMapper.kt:24:    userId = userId,
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonAttemptMapper.kt:29:    codeAnswer = CodeAnswer(codeAnswer),
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/state/RunnerState.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/state/RunnerState.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/state/RunnerState.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/state/RunnerState.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/state/RunnerState.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/state/RunnerState.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/state/RunnerState.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/state/RunnerState.kt:32:     * [eligibleSize] = length of codeAnswer = total questions of [mode] difficulty
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/state/RunnerState.kt:36:        val userId: String,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/state/RunnerState.kt:43:        val codeAnswer: CodeAnswer,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/state/RunnerState.kt:56:    /** Terminal: user exited mid-session; attempt saved with partial codeAnswer. */
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/Score.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/AttemptId.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
shared/feature/lesson-runner/data/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/DefaultProvidersTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data
shared/feature/lesson-runner/data/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/DefaultProvidersTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultAttemptIdProvider
shared/feature/lesson-runner/data/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/DefaultProvidersTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultRatingIdProvider
shared/feature/lesson-runner/data/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/DefaultProvidersTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultRandomSeedProvider
shared/feature/lesson-runner/data/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/DefaultProvidersTest.kt:48:        assertTrue("Same (userId, lessonId) must produce same RatingId", id1.value == id2.value)
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/UserAnswerDraft.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/CodeAnswer.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
shared/feature/lesson-runner/data/build.gradle.kts:6:    namespace = "com.tpov.schoolquiz.shared.feature.lesson_runner.data"
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/RatingId.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/SaveError.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/Attempt.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/Attempt.kt:12:    val userId: String,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/Attempt.kt:17:    val codeAnswer: CodeAnswer,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/Attempt.kt:21:        require(userId.isNotBlank()) { "Attempt.userId must not be blank" }
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/PercentScore.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/PercentScore.kt:5: * Formula: nonZero = codeAnswer.filter { it != '0' };
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/LessonRating.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/LessonRating.kt:6: * Immutable user rating for a lesson. One per (userId, lessonId) lifetime.
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/LessonRating.kt:7: * Remote ID = deterministic sha256("$userId:$lessonId").
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/LessonRating.kt:12:    val userId: String,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/LessonRating.kt:19:        require(userId.isNotBlank()) { "LessonRating.userId must not be blank" }
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/UserAnswer.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/TimerCoefficients.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
shared/feature/qualification/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/qualification/domain/dev_mode/model/TapResult.kt:17:sealed interface TapResult {
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/InitFailureReason.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/TimerDuration.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/Stars.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data.di
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.AttemptIdProvider
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RandomSeedProvider
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt:12:val lessonRunnerDomainKoinAdapter = module {
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt:28:            attemptIdProvider = get<AttemptIdProvider>()::next,
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt:35:            attemptIdProvider = get<AttemptIdProvider>()::next,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/RatingIdProvider.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/RatingIdProvider.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/RatingIdProvider.kt:7:    fun provide(userId: String, lessonId: LessonId): RatingId
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/RunnerQuestion.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/RunnerQuestion.kt:14:    val codeAnswerIndex: Int
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/RunnerQuestion.kt:19:        override val codeAnswerIndex: Int,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/RunnerQuestion.kt:26:        override val codeAnswerIndex: Int,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/RandomSeedProvider.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data.di
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultAttemptIdProvider
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultRandomSeedProvider
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultRatingIdProvider
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.LessonAttemptRepositoryImpl
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.LessonRatingRepositoryImpl
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.AttemptIdProvider
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RandomSeedProvider
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:16:val lessonRunnerDataModule = module {
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/AttemptIdProvider.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/AttemptIdProvider.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId

exec
/bin/bash -lc "nl -ba apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt | sed -n '1,170p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.apps.android_next
     2	
     3	import android.app.Application
     4	import androidx.work.Configuration
     5	import androidx.work.Constraints
     6	import androidx.work.ExistingPeriodicWorkPolicy
     7	import androidx.work.ExistingWorkPolicy
     8	import androidx.work.NetworkType
     9	import androidx.work.OneTimeWorkRequestBuilder
    10	import androidx.work.PeriodicWorkRequestBuilder
    11	import androidx.work.WorkManager
    12	import androidx.work.WorkerFactory
    13	import com.google.firebase.auth.FirebaseAuth
    14	import com.tpov.schoolquiz.android.feature.app_shell.presentation.di.appShellPresentationModule
    15	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.di.lessonRunnerPresentationModule
    16	import com.tpov.schoolquiz.android.feature.quest.presentation.di.questPresentationModule
    17	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.di.quizzesPresentationModule
    18	import com.tpov.schoolquiz.apps.android_next.di.syncModule
    19	import com.tpov.schoolquiz.platform.android_services.sync.SyncWorker
    20	import com.tpov.schoolquiz.platform.firebase.di.firebaseCatalogModule
    21	import com.tpov.schoolquiz.platform.firebase.di.firebaseLessonModule
    22	import com.tpov.schoolquiz.platform.firebase.di.firebaseModule
    23	import com.tpov.schoolquiz.platform.firebase.di.firebaseQuestModule
    24	import com.tpov.schoolquiz.platform.firebase.di.firebaseQuestionModule
    25	import com.tpov.schoolquiz.platform.firebase.di.firebaseSectionModule
    26	import com.tpov.schoolquiz.platform.firebase.di.firebaseThemeModule
    27	import com.tpov.schoolquiz.platform.firebase.initializeFirebaseSecurity
    28	import com.tpov.schoolquiz.shared.core.catalog.data.di.catalogDataModule
    29	import com.tpov.schoolquiz.shared.core.catalog.domain.di.catalogDomainModule
    30	import com.tpov.schoolquiz.shared.core.persistence.di.persistenceModule
    31	import com.tpov.schoolquiz.shared.core.question_schema.di.questionSchemaModule
    32	import com.tpov.schoolquiz.shared.feature.app_shell.data.di.appShellDataModule
    33	import com.tpov.schoolquiz.shared.feature.lesson.data.di.lessonDataModule
    34	import com.tpov.schoolquiz.shared.feature.lesson.domain.di.lessonDomainModule
    35	import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.lessonRunnerDataModule
    36	import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.lessonRunnerDomainKoinAdapter
    37	import com.tpov.schoolquiz.shared.feature.quest.data.di.questDataModule
    38	import com.tpov.schoolquiz.shared.feature.quest.domain.di.questDomainModule
    39	import com.tpov.schoolquiz.shared.feature.question.data.di.questionDataModule
    40	import com.tpov.schoolquiz.shared.feature.question.domain.di.questionDomainModule
    41	import com.tpov.schoolquiz.shared.feature.section.data.di.sectionDataModule
    42	import com.tpov.schoolquiz.shared.feature.section.domain.di.sectionDomainModule
    43	import com.tpov.schoolquiz.shared.feature.theme.data.di.themeDataModule
    44	import com.tpov.schoolquiz.shared.feature.theme.domain.di.themeDomainModule
    45	import kotlinx.coroutines.CoroutineScope
    46	import kotlinx.coroutines.Dispatchers
    47	import kotlinx.coroutines.SupervisorJob
    48	import kotlinx.coroutines.channels.awaitClose
    49	import kotlinx.coroutines.flow.SharingStarted
    50	import kotlinx.coroutines.flow.callbackFlow
    51	import kotlinx.coroutines.flow.shareIn
    52	import org.koin.android.ext.koin.androidContext
    53	import org.koin.core.context.GlobalContext
    54	import org.koin.core.context.startKoin
    55	
    56	class AppApplication : Application(), Configuration.Provider {
    57	    /**
    58	     * Application-scoped CoroutineScope for shared hot Flows (auth UID, etc.).
    59	     * Survives the entire process lifetime. Dispatchers.Default — non-blocking work
    60	     * (Firebase auth listener trampolines internally; explicit dispatcher avoids
    61	     * relying on kotlinx-coroutines fallback semantics).
    62	     */
    63	    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    64	
    65	    override val workManagerConfiguration: Configuration
    66	        get() =
    67	            Configuration.Builder()
    68	                .setWorkerFactory(GlobalContext.get().get<WorkerFactory>())
    69	                .build()
    70	
    71	    override fun onCreate() {
    72	        super.onCreate()
    73	        initializeFirebaseSecurity(this)
    74	        val auth = FirebaseAuth.getInstance()
    75	        // Cold callbackFlow: each collect would register a new AuthStateListener.
    76	        val coldAuthUidFlow =
    77	            callbackFlow<String?> {
    78	                val listener = FirebaseAuth.AuthStateListener { a -> trySend(a.currentUser?.uid) }
    79	                auth.addAuthStateListener(listener)
    80	                awaitClose { auth.removeAuthStateListener(listener) }
    81	            }
    82	        // Codex Round 4 N2 fix: shareIn(WhileSubscribed) → single AuthStateListener
    83	        // shared across all consumers (UserStatsRepositoryImpl + AuthRepositoryImpl + future
    84	        // MyQuestsViewModel). Replay 1 ensures late subscribers get the current UID immediately.
    85	        val sharedAuthUidFlow =
    86	            coldAuthUidFlow.shareIn(
    87	                scope = appScope,
    88	                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
    89	                replay = 1,
    90	            )
    91	        startKoin {
    92	            androidContext(this@AppApplication)
    93	            modules(
    94	                persistenceModule,
    95	                firebaseModule,
    96	                firebaseCatalogModule,
    97	                firebaseQuestModule,
    98	                firebaseSectionModule,
    99	                firebaseThemeModule,
   100	                firebaseLessonModule,
   101	                firebaseQuestionModule,
   102	                appShellDataModule { sharedAuthUidFlow },
   103	                appShellPresentationModule,
   104	                questPresentationModule,
   105	                quizzesPresentationModule,
   106	                catalogDataModule,
   107	                catalogDomainModule,
   108	                questDataModule,
   109	                questDomainModule,
   110	                sectionDataModule,
   111	                sectionDomainModule,
   112	                themeDataModule,
   113	                themeDomainModule,
   114	                lessonDataModule,
   115	                lessonDomainModule,
   116	                questionDataModule,
   117	                questionDomainModule,
   118	                questionSchemaModule,
   119	                lessonRunnerDataModule,
   120	                lessonRunnerDomainKoinAdapter,
   121	                lessonRunnerPresentationModule,
   122	                syncModule,
   123	            )
   124	        }
   125	        val workManager = WorkManager.getInstance(this)
   126	        workManager.enqueueUniquePeriodicWork(
   127	            SyncWorker.WORK_NAME_PERIODIC,
   128	            ExistingPeriodicWorkPolicy.KEEP,
   129	            PeriodicWorkRequestBuilder<SyncWorker>(
   130	                SyncWorker.PERIODIC_INTERVAL.first,
   131	                SyncWorker.PERIODIC_INTERVAL.second,
   132	            )
   133	                .setConstraints(
   134	                    Constraints.Builder()
   135	                        .setRequiredNetworkType(NetworkType.CONNECTED)
   136	                        .build(),
   137	                )
   138	                .build(),
   139	        )
   140	        // Bootstrap one-shot sync: runs ONCE after install (first-ever launch).
   141	        // Subsequent sync is handled by periodic WorkManager schedule + manual Sync Now.
   142	        val prefs = getSharedPreferences("sync_state", MODE_PRIVATE)
   143	        if (!prefs.getBoolean("bootstrap_done", false)) {
   144	            workManager.enqueueUniqueWork(
   145	                SyncWorker.WORK_NAME_BOOTSTRAP,
   146	                ExistingWorkPolicy.REPLACE,
   147	                OneTimeWorkRequestBuilder<SyncWorker>()
   148	                    .setConstraints(
   149	                        Constraints.Builder()
   150	                            .setRequiredNetworkType(NetworkType.CONNECTED)
   151	                            .build(),
   152	                    )
   153	                    .build(),
   154	            )
   155	            prefs.edit().putBoolean("bootstrap_done", true).apply()
   156	        }
   157	    }
   158	}

exec
/bin/bash -lc 'nl -ba shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.data.di
     2	
     3	import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultAttemptIdProvider
     4	import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultRandomSeedProvider
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultRatingIdProvider
     6	import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.LessonAttemptRepositoryImpl
     7	import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.LessonRatingRepositoryImpl
     8	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.AttemptIdProvider
     9	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RandomSeedProvider
    10	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
    11	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
    12	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
    13	import kotlinx.datetime.Clock
    14	import org.koin.dsl.module
    15	
    16	val lessonRunnerDataModule = module {
    17	    single<AttemptIdProvider> { DefaultAttemptIdProvider() }
    18	    single<RandomSeedProvider> { DefaultRandomSeedProvider() }
    19	    single<RatingIdProvider> { DefaultRatingIdProvider() }
    20	    single<Clock> { Clock.System }
    21	    single<LessonAttemptRepository> { LessonAttemptRepositoryImpl(attemptDao = get()) }
    22	    single<LessonRatingRepository> { LessonRatingRepositoryImpl(ratingLocalDao = get()) }
    23	}

exec
/bin/bash -lc 'nl -ba shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.data.di
     2	
     3	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.AttemptIdProvider
     4	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RandomSeedProvider
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
     6	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
     7	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
     8	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
     9	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
    10	import org.koin.dsl.module
    11	
    12	val lessonRunnerDomainKoinAdapter = module {
    13	    factory<StartLessonAttemptUseCase> {
    14	        StartLessonAttemptUseCase(
    15	            questionRepository = get(),
    16	            lessonRepository = get(),
    17	            parser = get(),
    18	            authRepository = get(),
    19	            clock = get(),
    20	            randomSeedProvider = get<RandomSeedProvider>()::next,
    21	        )
    22	    }
    23	    factory<CompleteAttemptUseCase> {
    24	        CompleteAttemptUseCase(
    25	            attemptRepository = get(),
    26	            ratingRepository = get(),
    27	            clock = get(),
    28	            attemptIdProvider = get<AttemptIdProvider>()::next,
    29	        )
    30	    }
    31	    factory<AbortAttemptUseCase> {
    32	        AbortAttemptUseCase(
    33	            attemptRepository = get(),
    34	            clock = get(),
    35	            attemptIdProvider = get<AttemptIdProvider>()::next,
    36	        )
    37	    }
    38	    factory<SubmitLessonRatingUseCase> {
    39	        SubmitLessonRatingUseCase(
    40	            ratingRepository = get(),
    41	            lessonRepository = get(),
    42	            clock = get(),
    43	            ratingIdProvider = get<RatingIdProvider>()::provide,
    44	        )
    45	    }
    46	}

exec
/bin/bash -lc 'nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.di
     2	
     3	import com.arkivanov.decompose.ComponentContext
     4	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
     5	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
     6	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
     7	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.LessonRunnerUseCases
     8	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
     9	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
    10	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
    11	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
    12	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
    13	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
    14	import org.koin.core.parameter.parametersOf
    15	import org.koin.dsl.module
    16	
    17	val lessonRunnerPresentationModule =
    18	    module {
    19	        factory { (ctx: ComponentContext, lessonId: LessonId, mode: Difficulty) ->
    20	            DefaultLessonRunnerRootComponent(
    21	                componentContext = ctx,
    22	                lessonId = lessonId,
    23	                mode = mode,
    24	                useCases =
    25	                    LessonRunnerUseCases(
    26	                        startAttempt = get<StartLessonAttemptUseCase>()::invoke,
    27	                        completeAttempt = get<CompleteAttemptUseCase>()::invoke,
    28	                        abortAttempt = get<AbortAttemptUseCase>()::invoke,
    29	                        submitRating = get<SubmitLessonRatingUseCase>()::invoke,
    30	                    ),
    31	                lessonRepository = get(),
    32	                attemptRepository = get(),
    33	                clock = get(),
    34	            ) as LessonRunnerRootComponent
    35	        }
    36	
    37	        single<LessonRunnerComponentFactory> {
    38	            LessonRunnerComponentFactory { ctx, lessonId, mode ->
    39	                get { parametersOf(ctx, lessonId, mode) }
    40	            }
    41	        }
    42	    }

exec
/bin/bash -lc "nl -ba android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt | sed -n '1,170p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
     2	
     3	import androidx.compose.foundation.background
     4	import androidx.compose.foundation.clickable
     5	import androidx.compose.foundation.interaction.MutableInteractionSource
     6	import androidx.compose.foundation.layout.Box
     7	import androidx.compose.foundation.layout.fillMaxSize
     8	import androidx.compose.material3.MaterialTheme
     9	import androidx.compose.runtime.Composable
    10	import androidx.compose.runtime.getValue
    11	import androidx.compose.runtime.remember
    12	import androidx.compose.ui.Modifier
    13	import androidx.compose.ui.tooling.preview.Preview
    14	import com.arkivanov.decompose.extensions.compose.subscribeAsState
    15	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    16	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.LessonRunnerScreen
    17	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
    18	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
    19	
    20	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    21	@Composable
    22	fun QuizzesScreen(component: QuizzesComponent) {
    23	    val stack by component.childStack.subscribeAsState()
    24	
    25	    Box(
    26	        modifier =
    27	            Modifier
    28	                .fillMaxSize()
    29	                .background(MaterialTheme.colorScheme.background)
    30	                .clickable(
    31	                    enabled = false,
    32	                    indication = null,
    33	                    interactionSource = remember { MutableInteractionSource() },
    34	                ) {},
    35	    ) {
    36	        when (val active = stack.active.instance) {
    37	            is QuizzesChild.Idle -> Unit
    38	            is QuizzesChild.QuestList -> QuestListScreen(active.component, onSegmentClick = component::popToLevel)
    39	            is QuizzesChild.SectionList -> SectionListScreen(active.component, onSegmentClick = component::popToLevel)
    40	            is QuizzesChild.ThemeList -> ThemeListScreen(active.component, onSegmentClick = component::popToLevel)
    41	            is QuizzesChild.LessonList -> LessonListScreen(active.component, onSegmentClick = component::popToLevel)
    42	            is QuizzesChild.LessonRunner ->
    43	                LessonRunnerScreen(
    44	                    component = active.component,
    45	                    onNavigateBack = { component.popCurrentChild() },
    46	                    onSegmentClick = component::popToLevel,
    47	                )
    48	        }
    49	    }
    50	}
    51	
    52	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
    53	@Preview(showBackground = true)
    54	@Composable
    55	private fun QuizzesScreenPreview() {
    56	    SchoolQuizTheme {
    57	        // Preview requires a real component — shown via child screens instead
    58	    }
    59	}

exec
/bin/bash -lc "nl -ba android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
     2	
     3	import com.arkivanov.decompose.ComponentContext
     4	import com.arkivanov.decompose.router.stack.ChildStack
     5	import com.arkivanov.decompose.router.stack.StackNavigation
     6	import com.arkivanov.decompose.router.stack.childStack
     7	import com.arkivanov.decompose.router.stack.pop
     8	import com.arkivanov.decompose.router.stack.popTo
     9	import com.arkivanov.decompose.router.stack.popToFirst
    10	import com.arkivanov.decompose.router.stack.pushNew
    11	import com.arkivanov.decompose.value.Value
    12	import com.arkivanov.essenty.backhandler.BackCallback
    13	import com.arkivanov.essenty.statekeeper.SerializableContainer
    14	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
    15	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
    16	import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
    17	import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
    18	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
    19	import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
    20	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
    21	import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
    22	import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
    23	import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
    24	import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
    25	import kotlinx.coroutines.CoroutineDispatcher
    26	import kotlinx.coroutines.Dispatchers
    27	import kotlinx.serialization.builtins.ListSerializer
    28	
    29	class DefaultQuizzesComponent(
    30	    componentContext: ComponentContext,
    31	    private val questRepository: QuestRepository,
    32	    private val sectionRepository: SectionRepository,
    33	    private val themeRepository: ThemeRepository,
    34	    private val lessonRepository: LessonRepository,
    35	    private val lessonAttemptRepository: LessonAttemptRepository,
    36	    private val authRepository: AuthRepository,
    37	    private val lessonRunnerFactory: LessonRunnerComponentFactory,
    38	    private val mainContext: CoroutineDispatcher = Dispatchers.Main.immediate,
    39	) : ComponentContext by componentContext, QuizzesComponent {
    40	    private val navigation = StackNavigation<QuizzesConfig>()
    41	
    42	    // saveStack/restoreStack overload: runCatching wraps SerializationException on corrupted
    43	    // process-death state so the stack falls back to [Idle] instead of crashing on launch.
    44	    // Spec: docs/features/quizzes-screen/plan/phase-03/overview.md Problem 6 / AC#21.
    45	    override val childStack: Value<ChildStack<QuizzesConfig, QuizzesChild>> =
    46	        childStack(
    47	            source = navigation,
    48	            initialStack = { listOf(QuizzesConfig.Idle) },
    49	            saveStack = { stack ->
    50	                SerializableContainer(
    51	                    value = stack,
    52	                    strategy = ListSerializer(QuizzesConfig.serializer()),
    53	                )
    54	            },
    55	            restoreStack = { container ->
    56	                runCatching {
    57	                    container.consume(ListSerializer(QuizzesConfig.serializer()))
    58	                }.getOrNull()
    59	                    // AC 36: process kill = attempt lost. Pop runner back to LessonList on restart.
    60	                    ?.filterNot { it is QuizzesConfig.LessonRunner }
    61	                    ?.takeIf { it.isNotEmpty() }
    62	            },
    63	            handleBackButton = false,
    64	            childFactory = ::createChild,
    65	        )
    66	
    67	    // priority = 100: PRIORITY_OVERLAY absent in Essenty 2.1.0 (ADR-QS-12, Pattern Invariant 5)
    68	    private val backCallback =
    69	        BackCallback(priority = 100, isEnabled = false) {
    70	            navigation.pop()
    71	        }
    72	
    73	    init {
    74	        backHandler.register(backCallback)
    75	        childStack.subscribe { stack ->
    76	            backCallback.isEnabled = stack.backStack.isNotEmpty()
    77	        }
    78	    }
    79	
    80	    override fun openQuestList(
    81	        catalogId: CatalogId,
    82	        catalogName: String,
    83	    ) {
    84	        navigation.pushNew(QuizzesConfig.QuestList(catalogId.value, listOf("Каталоги", catalogName)))
    85	    }
    86	
    87	    override fun openSectionList(
    88	        questId: QuestId,
    89	        titles: List<String>,
    90	    ) {
    91	        navigation.pushNew(QuizzesConfig.SectionList(questId.value, titles))
    92	    }
    93	
    94	    override fun popToLevel(uiLevel: Int) {
    95	        if (uiLevel < 0) return
    96	        val active = childStack.value.active
    97	        val titlesSize =
    98	            when (val cfg = active.configuration) {
    99	                is QuizzesConfig.Idle -> 0
   100	                is QuizzesConfig.QuestList -> cfg.titles.size
   101	                is QuizzesConfig.SectionList -> cfg.titles.size
   102	                is QuizzesConfig.ThemeList -> cfg.titles.size
   103	                is QuizzesConfig.LessonList -> cfg.titles.size
   104	                is QuizzesConfig.LessonRunner -> cfg.titles.size
   105	            }
   106	        // In MyQuests entry path, SectionList is pushed directly (no QuestList in stack).
   107	        // titles may contain virtual "decoration" segments that have no stack entry.
   108	        // virtualCount = how many leading titles have no corresponding stack entry.
   109	        val virtualCount = (titlesSize + 1 - childStack.value.items.size).coerceAtLeast(0)
   110	        val adjustedLevel = uiLevel - virtualCount
   111	        if (adjustedLevel < 0) {
   112	            // Clicked a virtual breadcrumb (e.g., catalog name from MyQuests path) — dismiss overlay
   113	            navigation.popToFirst()
   114	        } else {
   115	            navigation.popTo(adjustedLevel + 1)
   116	        }
   117	    }
   118	
   119	    override fun popCurrentChild() {
   120	        navigation.pop()
   121	    }
   122	
   123	    override fun dismissQuizzes() {
   124	        navigation.popToFirst()
   125	    }
   126	
   127	    private fun createChild(
   128	        config: QuizzesConfig,
   129	        ctx: ComponentContext,
   130	    ): QuizzesChild =
   131	        when (config) {
   132	            is QuizzesConfig.Idle ->
   133	                QuizzesChild.Idle
   134	            is QuizzesConfig.QuestList ->
   135	                QuizzesChild.QuestList(
   136	                    DefaultQuestListComponent(ctx, config, questRepository, navigation, mainContext),
   137	                )
   138	            is QuizzesConfig.SectionList ->
   139	                QuizzesChild.SectionList(
   140	                    DefaultSectionListComponent(ctx, config, sectionRepository, navigation, mainContext),
   141	                )
   142	            is QuizzesConfig.ThemeList ->
   143	                QuizzesChild.ThemeList(
   144	                    DefaultThemeListComponent(ctx, config, themeRepository, navigation, mainContext),
   145	                )
   146	            is QuizzesConfig.LessonList ->
   147	                QuizzesChild.LessonList(
   148	                    DefaultLessonListComponent(
   149	                        ctx,
   150	                        config,
   151	                        lessonRepository,
   152	                        lessonAttemptRepository,
   153	                        authRepository,
   154	                        navigation,
   155	                        mainContext,
   156	                    ),
   157	                )
   158	            is QuizzesConfig.LessonRunner ->
   159	                QuizzesChild.LessonRunner(
   160	                    lessonRunnerFactory.create(ctx, LessonId(config.lessonId), config.mode),
   161	                )
   162	        }
   163	}

exec
/bin/bash -lc 'nl -ba android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonListComponent.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
     2	
     3	import com.arkivanov.decompose.value.Value
     4	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
     5	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
     6	
     7	interface LessonListComponent {
     8	    val uiState: Value<LessonListUiState>
     9	    val titles: List<String>
    10	
    11	    fun onLessonClick(lesson: LessonItemUi)
    12	
    13	    fun onHardCheckToggled(lessonId: String)
    14	}

exec
/bin/bash -lc "nl -ba android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt | sed -n '1,280p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
     2	
     3	import com.arkivanov.decompose.ComponentContext
     4	import com.arkivanov.decompose.router.stack.StackNavigation
     5	import com.arkivanov.decompose.router.stack.pushNew
     6	import com.arkivanov.decompose.value.MutableValue
     7	import com.arkivanov.decompose.value.Value
     8	import com.arkivanov.essenty.lifecycle.doOnDestroy
     9	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
    10	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
    11	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
    12	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
    13	import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
    14	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
    15	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
    16	import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
    17	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
    18	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptStats
    19	import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
    20	import kotlinx.coroutines.CoroutineDispatcher
    21	import kotlinx.coroutines.CoroutineScope
    22	import kotlinx.coroutines.Dispatchers
    23	import kotlinx.coroutines.ExperimentalCoroutinesApi
    24	import kotlinx.coroutines.SupervisorJob
    25	import kotlinx.coroutines.flow.MutableStateFlow
    26	import kotlinx.coroutines.flow.catch
    27	import kotlinx.coroutines.flow.combine
    28	import kotlinx.coroutines.flow.flatMapLatest
    29	import kotlinx.coroutines.flow.flowOf
    30	import kotlinx.coroutines.flow.update
    31	import kotlinx.coroutines.launch
    32	
    33	@OptIn(ExperimentalCoroutinesApi::class)
    34	class DefaultLessonListComponent(
    35	    componentContext: ComponentContext,
    36	    config: QuizzesConfig.LessonList,
    37	    private val lessonRepository: LessonRepository,
    38	    private val attemptRepository: LessonAttemptRepository,
    39	    private val authRepository: AuthRepository,
    40	    private val navigation: StackNavigation<QuizzesConfig>,
    41	    coroutineContext: CoroutineDispatcher = Dispatchers.Main.immediate,
    42	) : ComponentContext by componentContext, LessonListComponent {
    43	    private val componentJob = SupervisorJob()
    44	    private val scope = CoroutineScope(componentJob + coroutineContext)
    45	
    46	    private val themeId = ThemeId(config.themeId)
    47	    override val titles: List<String> = config.titles
    48	
    49	    private val _uiState = MutableValue<LessonListUiState>(LessonListUiState.Loading)
    50	    override val uiState: Value<LessonListUiState> = _uiState
    51	
    52	    private val hardCheckedSet: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())
    53	
    54	    init {
    55	        scope.launch {
    56	            combine(
    57	                lessonRepository.observeByTheme(themeId),
    58	                authRepository.observeUid().flatMapLatest { uid ->
    59	                    if (uid == null) {
    60	                        flowOf(emptyMap())
    61	                    } else {
    62	                        attemptRepository.observeAllStatsByUser(uid)
    63	                    }
    64	                },
    65	                hardCheckedSet,
    66	            ) { lessons, stats, checkedSet ->
    67	                mapToUi(lessons, stats, checkedSet)
    68	            }
    69	                .catch { /* log */ }
    70	                .collect { _uiState.value = it }
    71	        }
    72	        lifecycle.doOnDestroy {
    73	            componentJob.cancel()
    74	            hardCheckedSet.value = emptySet()
    75	        }
    76	    }
    77	
    78	    override fun onLessonClick(lesson: LessonItemUi) {
    79	        val mode = if (lesson.hardUnlocked && lesson.isHardChecked) Difficulty.HARD else Difficulty.EASY
    80	        navigation.pushNew(
    81	            QuizzesConfig.LessonRunner(
    82	                lessonId = lesson.id,
    83	                mode = mode,
    84	                titles = titles + lesson.title,
    85	            ),
    86	        )
    87	    }
    88	
    89	    override fun onHardCheckToggled(lessonId: String) {
    90	        val item = (_uiState.value as? LessonListUiState.Loaded)?.items?.find { it.id == lessonId }
    91	        if (item?.hardUnlocked == true) {
    92	            hardCheckedSet.update { current ->
    93	                if (lessonId in current) current - lessonId else current + lessonId
    94	            }
    95	        }
    96	    }
    97	
    98	    private fun mapToUi(
    99	        lessons: List<Lesson>,
   100	        stats: Map<LessonId, LessonAttemptStats>,
   101	        checkedSet: Set<String>,
   102	    ): LessonListUiState {
   103	        if (lessons.isEmpty()) return LessonListUiState.Empty("Нет уроков")
   104	        val items =
   105	            lessons.map { lesson ->
   106	                val lessonStats = stats[lesson.id]
   107	                LessonItemUi(
   108	                    id = lesson.id.value,
   109	                    title = lesson.title,
   110	                    orderLabel = "${lesson.order + 1}.",
   111	                    bestStarsRawTenths = lessonStats?.bestStarsRawTenths ?: 0,
   112	                    hardUnlocked = lessonStats?.hardUnlocked ?: false,
   113	                    isHardChecked = lesson.id.value in checkedSet,
   114	                )
   115	            }
   116	        return LessonListUiState.Loaded(items)
   117	    }
   118	}

exec
/bin/bash -lc 'nl -ba android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/config/QuizzesConfig.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config
     2	
     3	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
     4	import kotlinx.serialization.Serializable
     5	
     6	@Serializable
     7	sealed class QuizzesConfig {
     8	    @Serializable
     9	    data object Idle : QuizzesConfig()
    10	
    11	    @Serializable
    12	    data class QuestList(
    13	        val catalogId: String,
    14	        val titles: List<String>,
    15	    ) : QuizzesConfig()
    16	
    17	    @Serializable
    18	    data class SectionList(
    19	        val questId: String,
    20	        val titles: List<String>,
    21	    ) : QuizzesConfig()
    22	
    23	    @Serializable
    24	    data class ThemeList(
    25	        val sectionId: String,
    26	        val titles: List<String>,
    27	    ) : QuizzesConfig()
    28	
    29	    @Serializable
    30	    data class LessonList(
    31	        val themeId: String,
    32	        val titles: List<String>,
    33	    ) : QuizzesConfig()
    34	
    35	    @Serializable
    36	    data class LessonRunner(
    37	        val lessonId: String,
    38	        val mode: Difficulty,
    39	        val titles: List<String>,
    40	    ) : QuizzesConfig()
    41	}

exec
/bin/bash -lc 'nl -ba android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
     2	
     3	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
     4	
     5	sealed interface QuizzesChild {
     6	    data object Idle : QuizzesChild
     7	
     8	    data class QuestList(val component: QuestListComponent) : QuizzesChild
     9	
    10	    data class SectionList(val component: SectionListComponent) : QuizzesChild
    11	
    12	    data class ThemeList(val component: ThemeListComponent) : QuizzesChild
    13	
    14	    data class LessonList(val component: LessonListComponent) : QuizzesChild
    15	
    16	    data class LessonRunner(val component: LessonRunnerRootComponent) : QuizzesChild
    17	}

exec
/bin/bash -lc "nl -ba android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonListScreen.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCard.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
     2	
     3	import androidx.compose.foundation.clickable
     4	import androidx.compose.foundation.layout.Row
     5	import androidx.compose.foundation.layout.fillMaxWidth
     6	import androidx.compose.foundation.layout.height
     7	import androidx.compose.foundation.layout.padding
     8	import androidx.compose.material3.Checkbox
     9	import androidx.compose.material3.MaterialTheme
    10	import androidx.compose.material3.Text
    11	import androidx.compose.runtime.Composable
    12	import androidx.compose.ui.Alignment
    13	import androidx.compose.ui.Modifier
    14	import androidx.compose.ui.text.style.TextOverflow
    15	import androidx.compose.ui.tooling.preview.Preview
    16	import androidx.compose.ui.unit.dp
    17	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    18	import com.tpov.schoolquiz.android.core.designsystem.components.BrandCard
    19	import com.tpov.schoolquiz.android.core.designsystem.components.StarRating
    20	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
    21	
    22	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    23	@Composable
    24	fun LessonItemCard(
    25	    item: LessonItemUi,
    26	    onClick: () -> Unit,
    27	    onHardCheckChanged: (Boolean) -> Unit,
    28	    modifier: Modifier = Modifier,
    29	) {
    30	    BrandCard(
    31	        modifier =
    32	            modifier
    33	                .fillMaxWidth()
    34	                .clickable(onClick = onClick),
    35	    ) {
    36	        Row(
    37	            verticalAlignment = Alignment.CenterVertically,
    38	            modifier =
    39	                Modifier
    40	                    .height(64.dp)
    41	                    .padding(horizontal = 12.dp, vertical = 8.dp),
    42	        ) {
    43	            if (item.orderLabel != null) {
    44	                Text(
    45	                    text = item.orderLabel,
    46	                    style = MaterialTheme.typography.labelSmall,
    47	                    color = MaterialTheme.colorScheme.onSurfaceVariant,
    48	                    modifier = Modifier.padding(end = 8.dp),
    49	                )
    50	            }
    51	            Text(
    52	                text = item.title,
    53	                style = MaterialTheme.typography.titleMedium,
    54	                maxLines = 1,
    55	                overflow = TextOverflow.Ellipsis,
    56	                modifier = Modifier.weight(1f),
    57	            )
    58	            if (item.bestStarsRawTenths > 0) {
    59	                StarRating(
    60	                    rating = item.bestStarsRawTenths / 10f,
    61	                    modifier = Modifier.padding(start = 8.dp),
    62	                    size = 28.dp,
    63	                )
    64	            }
    65	            if (item.hardUnlocked) {
    66	                Checkbox(
    67	                    checked = item.isHardChecked,
    68	                    onCheckedChange = onHardCheckChanged,
    69	                )
    70	            }
    71	        }
    72	    }
    73	}
    74	
    75	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
    76	@Preview(showBackground = true)
    77	@Composable
    78	private fun LessonItemCardBasicPreview() {
    79	    SchoolQuizTheme {
    80	        LessonItemCard(
    81	            item =
    82	                LessonItemUi(
    83	                    id = "l1",
    84	                    title = "Урок 1 — Введение в алгебру",
    85	                    orderLabel = "1.",
    86	                    bestStarsRawTenths = 20,
    87	                    hardUnlocked = false,
    88	                ),
    89	            onClick = {},
    90	            onHardCheckChanged = {},
    91	        )
    92	    }
    93	}
    94	
    95	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
    96	@Preview(showBackground = true)
    97	@Composable
    98	private fun LessonItemCardHardUnlockedPreview() {
    99	    SchoolQuizTheme {
   100	        LessonItemCard(
   101	            item =
   102	                LessonItemUi(
   103	                    id = "l2",
   104	                    title = "Урок 2 — Сложные задачи с длинным названием которое не помещается",
   105	                    orderLabel = "2.",
   106	                    bestStarsRawTenths = 27,
   107	                    hardUnlocked = true,
   108	                    isHardChecked = true,
   109	                ),
   110	            onClick = {},
   111	            onHardCheckChanged = {},
   112	        )
   113	    }
   114	}
   115	
   116	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
   117	@Preview(showBackground = true)
   118	@Composable
   119	private fun LessonItemCardZeroStarsPreview() {
   120	    SchoolQuizTheme {
   121	        LessonItemCard(
   122	            item =
   123	                LessonItemUi(
   124	                    id = "l3",
   125	                    title = "Урок 3 — Новый урок (нет попыток)",
   126	                    bestStarsRawTenths = 0,
   127	                    hardUnlocked = false,
   128	                ),
   129	            onClick = {},
   130	            onHardCheckChanged = {},
   131	        )
   132	    }
   133	}

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
     2	
     3	import androidx.compose.foundation.layout.Box
     4	import androidx.compose.foundation.layout.Column
     5	import androidx.compose.foundation.layout.fillMaxSize
     6	import androidx.compose.foundation.layout.padding
     7	import androidx.compose.foundation.lazy.LazyColumn
     8	import androidx.compose.foundation.lazy.items
     9	import androidx.compose.foundation.lazy.rememberLazyListState
    10	import androidx.compose.material3.CircularProgressIndicator
    11	import androidx.compose.material3.MaterialTheme
    12	import androidx.compose.material3.Text
    13	import androidx.compose.runtime.Composable
    14	import androidx.compose.runtime.getValue
    15	import androidx.compose.ui.Alignment
    16	import androidx.compose.ui.Modifier
    17	import androidx.compose.ui.tooling.preview.Preview
    18	import androidx.compose.ui.unit.dp
    19	import com.arkivanov.decompose.extensions.compose.subscribeAsState
    20	import com.arkivanov.decompose.value.MutableValue
    21	import com.arkivanov.decompose.value.Value
    22	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    23	import com.tpov.schoolquiz.android.core.designsystem.components.BreadcrumbBar
    24	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.LessonListComponent
    25	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
    26	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
    27	
    28	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    29	@Composable
    30	fun LessonListScreen(
    31	    component: LessonListComponent,
    32	    onSegmentClick: (Int) -> Unit,
    33	) {
    34	    val uiState by component.uiState.subscribeAsState()
    35	    val lazyListState = rememberLazyListState()
    36	
    37	    Column(modifier = Modifier.fillMaxSize()) {
    38	        BreadcrumbBar(titles = component.titles, onSegmentClick = onSegmentClick)
    39	        when (val state = uiState) {
    40	            is LessonListUiState.Loading ->
    41	                Box(modifier = Modifier.fillMaxSize()) {
    42	                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    43	                }
    44	            is LessonListUiState.Empty ->
    45	                Box(modifier = Modifier.fillMaxSize()) {
    46	                    Text(
    47	                        text = state.levelLabel,
    48	                        modifier = Modifier.align(Alignment.Center),
    49	                        style = MaterialTheme.typography.titleMedium,
    50	                    )
    51	                }
    52	            is LessonListUiState.Loaded ->
    53	                LazyColumn(state = lazyListState) {
    54	                    items(state.items, key = { it.id }) { item ->
    55	                        LessonItemCard(
    56	                            item = item,
    57	                            onClick = { component.onLessonClick(item) },
    58	                            onHardCheckChanged = { component.onHardCheckToggled(item.id) },
    59	                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    60	                        )
    61	                    }
    62	                }
    63	        }
    64	    }
    65	}
    66	
    67	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
    68	@Preview(showBackground = true)
    69	@Composable
    70	private fun LessonListScreenLoadingPreview() {
    71	    SchoolQuizTheme {
    72	        LessonListScreen(
    73	            component =
    74	                object : LessonListComponent {
    75	                    override val uiState: Value<LessonListUiState> = MutableValue(LessonListUiState.Loading)
    76	                    override val titles = listOf("Математика", "Квест 1", "Секция 1", "Тема 1")
    77	
    78	                    override fun onLessonClick(lesson: LessonItemUi) = Unit
    79	
    80	                    override fun onHardCheckToggled(lessonId: String) = Unit
    81	                },
    82	            onSegmentClick = {},
    83	        )
    84	    }
    85	}
    86	
    87	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
    88	@Preview(showBackground = true)
    89	@Composable
    90	private fun LessonListScreenLoadedPreview() {
    91	    SchoolQuizTheme {
    92	        LessonListScreen(
    93	            component =
    94	                object : LessonListComponent {
    95	                    override val uiState: Value<LessonListUiState> =
    96	                        MutableValue(
    97	                            LessonListUiState.Loaded(
    98	                                listOf(
    99	                                    LessonItemUi(
   100	                                        id = "l1",
   101	                                        title = "Урок 1 — Введение",
   102	                                        orderLabel = "1.",
   103	                                        bestStarsRawTenths = 20,
   104	                                    ),
   105	                                    LessonItemUi(
   106	                                        id = "l2",
   107	                                        title = "Урок 2 — Практика",
   108	                                        orderLabel = "2.",
   109	                                        bestStarsRawTenths = 0,
   110	                                        hardUnlocked = true,
   111	                                        isHardChecked = false,
   112	                                    ),
   113	                                ),
   114	                            ),
   115	                        )
   116	                    override val titles = listOf("Математика", "Квест 1", "Секция 1", "Тема 1")
   117	
   118	                    override fun onLessonClick(lesson: LessonItemUi) = Unit
   119	
   120	                    override fun onHardCheckToggled(lessonId: String) = Unit
   121	                },
   122	            onSegmentClick = {},
   123	        )
   124	    }
   125	}

exec
/bin/bash -lc 'nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/RunnerUiState.kt' in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state
     2	
     3	import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
     4	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
     6	
     7	sealed interface RunnerUiState {
     8	    data object Loading : RunnerUiState
     9	
    10	    data class InitFailed(
    11	        val reason: InitFailureReason,
    12	    ) : RunnerUiState
    13	
    14	    data class Question(
    15	        val questionUiState: QuestionUiState,
    16	        val indexInPool: Int,
    17	        val totalInPool: Int,
    18	        val deadlineMs: Long,
    19	        val isPaused: Boolean,
    20	        val isHard: Boolean,
    21	        val showExitConfirmDialog: Boolean,
    22	    ) : RunnerUiState
    23	
    24	    data class Result(
    25	        val percentScore: PercentScore,
    26	        val mode: Difficulty,
    27	        val completedAt: Long,
    28	        val hardUnlocked: Boolean,
    29	        val bestStarsRawTenths: Int,
    30	        /** Stars earned in THIS attempt (spec §148), distinct from best stars across all attempts. */
    31	        val currentAttemptStarsRawTenths: Int,
    32	        val lessonAverageRating: Float?,
    33	        val lessonRatingCount: Int,
    34	        val top3: List<TopParticipant>,
    35	        val userAttemptCount: Int,
    36	        val userAveragePercentScore: Int,
    37	        val showRatingPrompt: Boolean,
    38	        val saveWarning: Boolean,
    39	        val ratingSubmissionState: RatingSubmissionState = RatingSubmissionState.Idle,
    40	    ) : RunnerUiState
    41	
    42	    sealed interface RatingSubmissionState {
    43	        data object Idle : RatingSubmissionState
    44	
    45	        data object InProgress : RatingSubmissionState
    46	
    47	        data object Failed : RatingSubmissionState
    48	
    49	        data object Done : RatingSubmissionState
    50	    }
    51	
    52	    enum class InitFailureReason {
    53	        AuthRequired,
    54	        LessonNotFound,
    55	        EmptyPool,
    56	        NoValidQuestions,
    57	    }
    58	}

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper
     2	
     3	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.SaveError
     4	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
     5	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
     6	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
     7	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart
     8	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
     9	import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
    10	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
    11	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswer
    12	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
    13	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
    14	import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Blank as ContentBlank
    15	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError as DomainSaveError
    16	
    17	fun RunnerState.Ready.toQuestionUiState(): RunnerUiState.Question {
    18	    val question = playOrder[indexInPool]
    19	    return RunnerUiState.Question(
    20	        questionUiState = question.content.toQuestionUiState(currentDraftAnswer),
    21	        indexInPool = indexInPool,
    22	        totalInPool = playOrder.size,
    23	        deadlineMs = deadlineMs,
    24	        isPaused = isPaused,
    25	        isHard = mode == Difficulty.HARD,
    26	        showExitConfirmDialog = false,
    27	    )
    28	}
    29	
    30	fun QuestionContent.toQuestionUiState(draft: UserAnswerDraft? = null): QuestionUiState =
    31	    when (this) {
    32	        is QuestionContent.SingleChoice -> {
    33	            val selectedId = (draft as? UserAnswerDraft.SingleChoiceDraft)?.selected?.raw
    34	            QuestionUiState.SingleChoice(
    35	                questionText = text,
    36	                hasImage = imageUrl != null,
    37	                imageUrl = imageUrl?.takeIf { it.startsWith("https://") },
    38	                options = options.map { OptionUi(id = it.id.raw, text = it.text) },
    39	                selectedOptionId = selectedId,
    40	            )
    41	        }
    42	        is QuestionContent.MultipleChoice -> {
    43	            val selectedIds =
    44	                (draft as? UserAnswerDraft.MultipleChoiceDraft)
    45	                    ?.selected?.map { it.raw }?.toSet() ?: emptySet()
    46	            QuestionUiState.MultipleChoice(
    47	                questionText = text,
    48	                hasImage = imageUrl != null,
    49	                imageUrl = imageUrl?.takeIf { it.startsWith("https://") },
    50	                options = options.map { OptionUi(id = it.id.raw, text = it.text) },
    51	                selectedIds = selectedIds,
    52	            )
    53	        }
    54	        is QuestionContent.Ordering -> {
    55	            val draftOrder = (draft as? UserAnswerDraft.OrderingDraft)?.order
    56	            val itemById = items.associateBy { it.id }
    57	            val orderedItems =
    58	                if (draftOrder != null && draftOrder.size == items.size) {
    59	                    draftOrder.mapNotNull { id -> itemById[id]?.let { OptionUi(it.id.raw, it.text) } }
    60	                        .takeIf { it.size == items.size } ?: items.map { OptionUi(it.id.raw, it.text) }
    61	                } else {
    62	                    items.map { OptionUi(it.id.raw, it.text) }
    63	                }
    64	            QuestionUiState.Ordering(
    65	                questionText = text,
    66	                hasImage = imageUrl != null,
    67	                imageUrl = imageUrl?.takeIf { it.startsWith("https://") },
    68	                items = orderedItems,
    69	            )
    70	        }
    71	        is QuestionContent.FillBlank -> {
    72	            val blankIdToIndex = blanks.mapIndexed { idx, blank -> blank.id to idx }.toMap()
    73	            val candidateById = candidates.associateBy { it.id }
    74	            val filledValues =
    75	                (draft as? UserAnswerDraft.FillBlankDraft)
    76	                    ?.filled
    77	                    ?.mapNotNull { (blankId, candidateId) ->
    78	                        val idx = blankIdToIndex[blankId] ?: return@mapNotNull null
    79	                        val text = candidateId?.let { candidateById[it]?.text } ?: return@mapNotNull null
    80	                        idx to text
    81	                    }
    82	                    ?.toMap() ?: emptyMap()
    83	            QuestionUiState.FillBlank(
    84	                questionText = text,
    85	                hasImage = imageUrl != null,
    86	                imageUrl = imageUrl?.takeIf { it.startsWith("https://") },
    87	                templateParts = parseTemplateParts(text, blanks),
    88	                filledValues = filledValues,
    89	                candidates = candidates.map { OptionUi(id = it.id.raw, text = it.text) },
    90	            )
    91	        }
    92	    }
    93	
    94	fun UserAnswerDraft.toUserAnswer(): UserAnswer =
    95	    when (this) {
    96	        is UserAnswerDraft.SingleChoiceDraft -> UserAnswer.SingleChoiceAnswer(selected)
    97	        is UserAnswerDraft.MultipleChoiceDraft -> UserAnswer.MultipleChoiceAnswer(selected)
    98	        is UserAnswerDraft.OrderingDraft -> UserAnswer.OrderingAnswer(order)
    99	        is UserAnswerDraft.FillBlankDraft -> UserAnswer.FillBlankAnswer(filled)
   100	    }
   101	
   102	fun InitFailureReason.toUiReason(): RunnerUiState.InitFailureReason =
   103	    when (this) {
   104	        InitFailureReason.AuthRequired -> RunnerUiState.InitFailureReason.AuthRequired
   105	        InitFailureReason.LessonNotFound -> RunnerUiState.InitFailureReason.LessonNotFound
   106	        InitFailureReason.EmptyPool -> RunnerUiState.InitFailureReason.EmptyPool
   107	        InitFailureReason.NoValidQuestions -> RunnerUiState.InitFailureReason.NoValidQuestions
   108	    }
   109	
   110	fun DomainSaveError.toEventSaveError(): SaveError =
   111	    when (this) {
   112	        is DomainSaveError.IoFailure -> SaveError.IoError
   113	        is DomainSaveError.UnknownError -> SaveError.Unknown
   114	    }
   115	
   116	private fun parseTemplateParts(
   117	    text: String,
   118	    blanks: List<ContentBlank>,
   119	): List<TemplatePart> {
   120	    val segments = text.split("___")
   121	    val result = mutableListOf<TemplatePart>()
   122	    segments.forEachIndexed { idx, segment ->
   123	        if (segment.isNotEmpty()) result.add(TemplatePart.Text(segment))
   124	        if (idx < segments.size - 1 && idx < blanks.size) {
   125	            result.add(TemplatePart.Blank(index = idx, placeholder = "___", blankId = blanks[idx].id.raw))
   126	        }
   127	    }
   128	    if (result.isEmpty()) result.add(TemplatePart.Text(text))
   129	    return result
   130	}

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt | sed -n '1,360p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
     2	
     3	import android.app.Activity
     4	import android.view.WindowManager
     5	import androidx.compose.foundation.layout.Box
     6	import androidx.compose.foundation.layout.Column
     7	import androidx.compose.foundation.layout.fillMaxSize
     8	import androidx.compose.foundation.layout.padding
     9	import androidx.compose.material3.CircularProgressIndicator
    10	import androidx.compose.material3.MaterialTheme
    11	import androidx.compose.material3.Scaffold
    12	import androidx.compose.material3.SnackbarHost
    13	import androidx.compose.material3.SnackbarHostState
    14	import androidx.compose.material3.Text
    15	import androidx.compose.material3.TextButton
    16	import androidx.compose.runtime.Composable
    17	import androidx.compose.runtime.DisposableEffect
    18	import androidx.compose.runtime.LaunchedEffect
    19	import androidx.compose.runtime.collectAsState
    20	import androidx.compose.runtime.getValue
    21	import androidx.compose.runtime.mutableStateOf
    22	import androidx.compose.runtime.remember
    23	import androidx.compose.runtime.setValue
    24	import androidx.compose.ui.Alignment
    25	import androidx.compose.ui.Modifier
    26	import androidx.compose.ui.platform.LocalContext
    27	import androidx.compose.ui.tooling.preview.Preview
    28	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    29	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
    30	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
    31	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
    32	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
    33	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart
    34	import com.tpov.schoolquiz.shared.core.question_schema.BlankId
    35	import com.tpov.schoolquiz.shared.core.question_schema.CandidateId
    36	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
    37	import com.tpov.schoolquiz.shared.core.question_schema.OptionId
    38	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
    39	import kotlinx.coroutines.flow.Flow
    40	import kotlinx.coroutines.flow.MutableStateFlow
    41	import kotlinx.coroutines.flow.StateFlow
    42	import kotlinx.coroutines.flow.emptyFlow
    43	
    44	@Composable
    45	fun rememberFlagSecure(enabled: Boolean) {
    46	    val context = LocalContext.current
    47	    DisposableEffect(enabled) {
    48	        val window =
    49	            (context as? Activity)?.window
    50	                ?: return@DisposableEffect onDispose {}
    51	        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    52	        onDispose {
    53	            if (enabled) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    54	        }
    55	    }
    56	}
    57	
    58	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    59	@Composable
    60	fun LessonRunnerScreen(
    61	    component: LessonRunnerRootComponent,
    62	    onNavigateBack: () -> Unit,
    63	    onSegmentClick: (Int) -> Unit,
    64	    modifier: Modifier = Modifier,
    65	) {
    66	    val state by component.uiState.collectAsState()
    67	    val snackbarHostState = remember { SnackbarHostState() }
    68	
    69	    val flagSecureEnabled =
    70	        when (val s = state) {
    71	            is RunnerUiState.Question -> s.isHard
    72	            is RunnerUiState.Result -> s.mode == Difficulty.HARD
    73	            else -> false
    74	        }
    75	    rememberFlagSecure(enabled = flagSecureEnabled)
    76	
    77	    LaunchedEffect(component) {
    78	        component.events.collect { event ->
    79	            when (event) {
    80	                is RunnerEvent.SaveAttemptFailed ->
    81	                    snackbarHostState.showSnackbar("Не удалось сохранить результат")
    82	                RunnerEvent.SaveRatingFailed ->
    83	                    snackbarHostState.showSnackbar("Не удалось сохранить оценку")
    84	                RunnerEvent.NavigateBack -> onNavigateBack()
    85	            }
    86	        }
    87	    }
    88	
    89	    val scaffoldColor =
    90	        when (val s = state) {
    91	            is RunnerUiState.Question ->
    92	                if (s.isHard) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.background
    93	            else -> MaterialTheme.colorScheme.background
    94	        }
    95	    Scaffold(
    96	        modifier = modifier.fillMaxSize(),
    97	        snackbarHost = { SnackbarHost(snackbarHostState) },
    98	        containerColor = scaffoldColor,
    99	    ) { innerPadding ->
   100	        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
   101	            RunnerStateContent(state = state, component = component)
   102	        }
   103	    }
   104	}
   105	
   106	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
   107	@Composable
   108	private fun RunnerStateContent(
   109	    state: RunnerUiState,
   110	    component: LessonRunnerRootComponent,
   111	) {
   112	    when (state) {
   113	        RunnerUiState.Loading ->
   114	            Box(modifier = Modifier.fillMaxSize()) {
   115	                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
   116	            }
   117	        is RunnerUiState.InitFailed ->
   118	            Box(modifier = Modifier.fillMaxSize()) {
   119	                InitFailedContent(
   120	                    reason = state.reason,
   121	                    onBack = { component.onBack() },
   122	                    modifier = Modifier.align(Alignment.Center),
   123	                )
   124	            }
   125	        is RunnerUiState.Question ->
   126	            QuestionStateContent(state = state, component = component)
   127	        is RunnerUiState.Result ->
   128	            ResultContent(
   129	                state = state,
   130	                onSubmitRating = { component.onSubmitRating(it) },
   131	                onFinish = { component.onFinish() },
   132	                modifier = Modifier.fillMaxSize(),
   133	            )
   134	    }
   135	}
   136	
   137	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
   138	@Composable
   139	private fun InitFailedContent(
   140	    reason: RunnerUiState.InitFailureReason,
   141	    onBack: () -> Unit,
   142	    modifier: Modifier = Modifier,
   143	) {
   144	    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
   145	        Text(text = reason.displayMessage(), style = MaterialTheme.typography.bodyLarge)
   146	        TextButton(onClick = onBack) { Text("Назад") }
   147	    }
   148	}
   149	
   150	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
   151	@Composable
   152	private fun QuestionStateContent(
   153	    state: RunnerUiState.Question,
   154	    component: LessonRunnerRootComponent,
   155	) {
   156	    Column(modifier = Modifier.fillMaxSize()) {
   157	        QuestionProgressHeader(
   158	            indexInPool = state.indexInPool,
   159	            totalInPool = state.totalInPool,
   160	            deadlineMs = state.deadlineMs,
   161	            isPaused = state.isPaused,
   162	            isHard = state.isHard,
   163	            onCrossClick = { component.onCrossButtonTap() },
   164	            onTimeout = { component.onTimeout() },
   165	        )
   166	        Box(modifier = Modifier.weight(1f)) {
   167	            QuestionTypeContent(qState = state.questionUiState, component = component)
   168	        }
   169	    }
   170	    if (state.isPaused) {
   171	        BlockingResumeDialog(
   172	            onContinue = { component.onContinue() },
   173	            onExit = { component.onExit() },
   174	        )
   175	    }
   176	    if (state.showExitConfirmDialog) {
   177	        ExitConfirmDialog(
   178	            onConfirm = { component.onCrossConfirmed() },
   179	            onDismiss = { component.onCrossCancelled() },
   180	        )
   181	    }
   182	}
   183	
   184	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
   185	@Composable
   186	private fun QuestionTypeContent(
   187	    qState: QuestionUiState,
   188	    component: LessonRunnerRootComponent,
   189	) {
   190	    when (qState) {
   191	        is QuestionUiState.SingleChoice ->
   192	            SingleChoiceContent(
   193	                state = qState,
   194	                onOptionSelected = { optionId ->
   195	                    component.onAnswer(UserAnswerDraft.SingleChoiceDraft(OptionId(optionId)))
   196	                },
   197	                modifier = Modifier.fillMaxSize(),
   198	            )
   199	        is QuestionUiState.MultipleChoice -> {
   200	            var localSelected by remember(qState) { mutableStateOf(qState.selectedIds) }
   201	            MultipleChoiceContent(
   202	                state = qState.copy(selectedIds = localSelected),
   203	                onOptionToggled = { optionId ->
   204	                    localSelected =
   205	                        if (optionId in localSelected) localSelected - optionId else localSelected + optionId
   206	                },
   207	                onSubmit = {
   208	                    component.onAnswer(
   209	                        UserAnswerDraft.MultipleChoiceDraft(localSelected.map { OptionId(it) }.toSet()),
   210	                    )
   211	                },
   212	                modifier = Modifier.fillMaxSize(),
   213	            )
   214	        }
   215	        is QuestionUiState.Ordering -> {
   216	            var localItems by remember(qState) { mutableStateOf(qState.items) }
   217	            OrderingContent(
   218	                state = qState.copy(items = localItems),
   219	                onMoveUp = { index ->
   220	                    if (index > 0) {
   221	                        localItems =
   222	                            localItems.toMutableList().also {
   223	                                val tmp = it[index]
   224	                                it[index] = it[index - 1]
   225	                                it[index - 1] = tmp
   226	                            }
   227	                    }
   228	                },
   229	                onMoveDown = { index ->
   230	                    if (index < localItems.lastIndex) {
   231	                        localItems =
   232	                            localItems.toMutableList().also {
   233	                                val tmp = it[index]
   234	                                it[index] = it[index + 1]
   235	                                it[index + 1] = tmp
   236	                            }
   237	                    }
   238	                },
   239	                onSubmit = {
   240	                    component.onAnswer(UserAnswerDraft.OrderingDraft(localItems.map { OptionId(it.id) }))
   241	                },
   242	                modifier = Modifier.fillMaxSize(),
   243	            )
   244	        }
   245	        is QuestionUiState.FillBlank -> {
   246	            // localFilled: blankId.raw → candidateId.raw
   247	            var localFilled by remember(qState) { mutableStateOf<Map<String, String>>(emptyMap()) }
   248	            val blankParts = qState.templateParts.filterIsInstance<TemplatePart.Blank>()
   249	            val blanksByIndex = blankParts.associateBy { it.index }
   250	            val candidateById = qState.candidates.associateBy { it.id }
   251	            val displayFilledValues =
   252	                localFilled.entries.mapNotNull { (blankId, candidateId) ->
   253	                    val blank = blankParts.firstOrNull { it.blankId == blankId } ?: return@mapNotNull null
   254	                    val text = candidateById[candidateId]?.text ?: return@mapNotNull null
   255	                    blank.index to text
   256	                }.toMap()
   257	            FillBlankContent(
   258	                state = qState.copy(filledValues = displayFilledValues),
   259	                candidates = qState.candidates,
   260	                onCandidateSelected = { candidateId ->
   261	                    val firstEmpty = blankParts.firstOrNull { it.blankId !in localFilled }
   262	                    if (firstEmpty != null) localFilled = localFilled + (firstEmpty.blankId to candidateId)
   263	                },
   264	                onBlankCleared = { blankIndex ->
   265	                    blanksByIndex[blankIndex]?.let { localFilled = localFilled - it.blankId }
   266	                },
   267	                onSubmit = {
   268	                    component.onAnswer(
   269	                        UserAnswerDraft.FillBlankDraft(
   270	                            localFilled
   271	                                .mapKeys { BlankId(it.key) }
   272	                                .mapValues { CandidateId(it.value) },
   273	                        ),
   274	                    )
   275	                },
   276	                modifier = Modifier.fillMaxSize(),
   277	            )
   278	        }
   279	    }
   280	}
   281	
   282	private fun RunnerUiState.InitFailureReason.displayMessage(): String =
   283	    when (this) {
   284	        RunnerUiState.InitFailureReason.AuthRequired -> "Требуется авторизация"
   285	        RunnerUiState.InitFailureReason.LessonNotFound -> "Урок не найден"
   286	        RunnerUiState.InitFailureReason.EmptyPool -> "Нет доступных вопросов"
   287	        RunnerUiState.InitFailureReason.NoValidQuestions -> "Вопросы недействительны"
   288	    }
   289	
   290	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
   291	@Preview(showBackground = true)
   292	@Composable
   293	private fun LessonRunnerScreenLoadingPreview() {
   294	    SchoolQuizTheme {
   295	        LessonRunnerScreen(
   296	            component = PreviewLessonRunnerComponent(RunnerUiState.Loading),
   297	            onNavigateBack = {},
   298	            onSegmentClick = {},
   299	        )
   300	    }
   301	}
   302	
   303	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
   304	@Preview(showBackground = true)
   305	@Composable
   306	private fun LessonRunnerScreenInitFailedPreview() {
   307	    SchoolQuizTheme {
   308	        LessonRunnerScreen(
   309	            component =
   310	                PreviewLessonRunnerComponent(
   311	                    RunnerUiState.InitFailed(RunnerUiState.InitFailureReason.AuthRequired),
   312	                ),
   313	            onNavigateBack = {},
   314	            onSegmentClick = {},
   315	        )
   316	    }
   317	}
   318	
   319	private class PreviewLessonRunnerComponent(initialState: RunnerUiState) : LessonRunnerRootComponent {
   320	    override val uiState: StateFlow<RunnerUiState> = MutableStateFlow(initialState)
   321	    override val events: Flow<RunnerEvent> = emptyFlow()
   322	
   323	    override fun onAnswer(answer: UserAnswerDraft) = Unit
   324	
   325	    override fun onTimeout() = Unit
   326	
   327	    override fun onContinue() = Unit
   328	
   329	    override fun onExit() = Unit
   330	
   331	    override fun onCrossButtonTap() = Unit
   332	
   333	    override fun onCrossConfirmed() = Unit
   334	
   335	    override fun onCrossCancelled() = Unit
   336	
   337	    override fun onSubmitRating(rating: Int) = Unit
   338	
   339	    override fun onFinish() = Unit
   340	
   341	    override fun onBack() = Unit
   342	}

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt | sed -n '1,360p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component
     2	
     3	import com.arkivanov.decompose.ComponentContext
     4	import com.arkivanov.essenty.instancekeeper.getOrCreate
     5	import com.arkivanov.essenty.lifecycle.doOnDestroy
     6	import com.arkivanov.essenty.lifecycle.doOnStop
     7	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
     8	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
     9	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toEventSaveError
    10	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toQuestionUiState
    11	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toUiReason
    12	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toUserAnswer
    13	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
    14	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState.RatingSubmissionState
    15	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
    16	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
    17	import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
    18	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.autoAnswerOnTimeout
    19	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeBestStars
    20	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeHardUnlocked
    21	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeStars
    22	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
    23	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.submitAnswer
    24	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
    25	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
    26	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
    27	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
    28	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
    29	import kotlinx.coroutines.channels.Channel
    30	import kotlinx.coroutines.flow.Flow
    31	import kotlinx.coroutines.flow.StateFlow
    32	import kotlinx.coroutines.flow.asStateFlow
    33	import kotlinx.coroutines.flow.first
    34	import kotlinx.coroutines.flow.receiveAsFlow
    35	import kotlinx.coroutines.launch
    36	import kotlinx.datetime.Clock
    37	import kotlin.coroutines.CoroutineContext
    38	
    39	@Suppress("LongParameterList")
    40	class DefaultLessonRunnerRootComponent(
    41	    componentContext: ComponentContext,
    42	    private val lessonId: LessonId,
    43	    private val mode: Difficulty,
    44	    private val useCases: LessonRunnerUseCases,
    45	    private val lessonRepository: LessonRepository,
    46	    private val attemptRepository: LessonAttemptRepository,
    47	    private val clock: Clock,
    48	    mainContext: CoroutineContext = kotlinx.coroutines.Dispatchers.Main.immediate,
    49	) : ComponentContext by componentContext, LessonRunnerRootComponent {
    50	    private val stateHolder =
    51	        instanceKeeper.getOrCreate("runner_state_holder") {
    52	            RunnerStateHolder(mainContext)
    53	        }
    54	
    55	    private val scope get() = stateHolder.scope
    56	
    57	    override val uiState: StateFlow<RunnerUiState> = stateHolder.uiState.asStateFlow()
    58	
    59	    private val _events = Channel<RunnerEvent>(capacity = Channel.BUFFERED)
    60	    override val events: Flow<RunnerEvent> = _events.receiveAsFlow()
    61	
    62	    init {
    63	        // doOnDestroy sets the marker so the next component init (config change) can detect
    64	        // that the pause triggered in doOnStop was spurious. InstanceKeeper.onDestroy() clears
    65	        // the marker on real navigation pop / process death, so it's only true for rotation.
    66	        lifecycle.doOnDestroy {
    67	            _events.close()
    68	            stateHolder.pendingConfigChangeRestore = true
    69	        }
    70	        lifecycle.doOnStop {
    71	            val state = stateHolder.domainState
    72	            if (state is RunnerState.Ready && !state.isPaused &&
    73	                state.indexInPool < state.playOrder.size
    74	            ) {
    75	                stateHolder.snapshotBeforeStop = Pair(state, stateHolder.uiState.value)
    76	            }
    77	            handlePause()
    78	        }
    79	
    80	        if (stateHolder.pendingConfigChangeRestore) {
    81	            // Config change: undo the auto-pause triggered by the old component's doOnStop.
    82	            stateHolder.pendingConfigChangeRestore = false
    83	            val snapshot = stateHolder.snapshotBeforeStop
    84	            if (snapshot != null) {
    85	                stateHolder.domainState = snapshot.first
    86	                stateHolder.uiState.value = snapshot.second
    87	                stateHolder.snapshotBeforeStop = null
    88	            }
    89	        } else if (stateHolder.uiState.value is RunnerUiState.Loading) {
    90	            scope.launch { triggerStart() }
    91	        }
    92	    }
    93	
    94	    override fun onAnswer(answer: UserAnswerDraft) {
    95	        val domainState = stateHolder.domainState as? RunnerState.Ready ?: return
    96	        if (domainState.indexInPool >= domainState.playOrder.size) return
    97	        val userAnswer = answer.toUserAnswer()
    98	        val nowMs = clock.now().toEpochMilliseconds()
    99	        val newState = submitAnswer(domainState, userAnswer, nowMs)
   100	        stateHolder.domainState = newState
   101	        if (newState.indexInPool >= newState.playOrder.size) {
   102	            scope.launch { triggerComplete(newState) }
   103	        } else {
   104	            stateHolder.uiState.value = newState.toQuestionUiState()
   105	        }
   106	    }
   107	
   108	    override fun onTimeout() {
   109	        val domainState = stateHolder.domainState as? RunnerState.Ready ?: return
   110	        if (domainState.indexInPool >= domainState.playOrder.size) return
   111	        val nowMs = clock.now().toEpochMilliseconds()
   112	        val newState = autoAnswerOnTimeout(domainState, domainState.seed, nowMs)
   113	        stateHolder.domainState = newState
   114	        if (newState.indexInPool >= newState.playOrder.size) {
   115	            scope.launch { triggerComplete(newState) }
   116	        } else {
   117	            stateHolder.uiState.value = newState.toQuestionUiState()
   118	        }
   119	    }
   120	
   121	    override fun onContinue() {
   122	        val domainState = stateHolder.domainState as? RunnerState.Ready ?: return
   123	        if (!domainState.isPaused) return
   124	        // Reset deadline from now so the paused-during-background timer doesn't start expired.
   125	        val nowMs = clock.now().toEpochMilliseconds()
   126	        val currentQuestion = domainState.playOrder[domainState.indexInPool]
   127	        val duration = computeTimer(currentQuestion.content, domainState.mode, TimerCoefficients.Default)
   128	        val resumedState =
   129	            domainState.copy(
   130	                isPaused = false,
   131	                deadlineMs = nowMs + duration.seconds * 1000L,
   132	            )
   133	        stateHolder.domainState = resumedState
   134	        stateHolder.uiState.value = resumedState.toQuestionUiState()
   135	    }
   136	
   137	    override fun onExit() {
   138	        val domainState = stateHolder.domainState as? RunnerState.Ready ?: return
   139	        scope.launch { triggerAbort(domainState) }
   140	    }
   141	
   142	    override fun onCrossButtonTap() {
   143	        val current = stateHolder.uiState.value
   144	        if (current is RunnerUiState.Question) {
   145	            stateHolder.uiState.value = current.copy(showExitConfirmDialog = true)
   146	        }
   147	    }
   148	
   149	    override fun onCrossConfirmed() {
   150	        val domainState = stateHolder.domainState as? RunnerState.Ready ?: return
   151	        scope.launch { triggerAbort(domainState) }
   152	    }
   153	
   154	    override fun onCrossCancelled() {
   155	        val current = stateHolder.uiState.value
   156	        if (current is RunnerUiState.Question) {
   157	            stateHolder.uiState.value = current.copy(showExitConfirmDialog = false)
   158	        }
   159	    }
   160	
   161	    override fun onSubmitRating(rating: Int) {
   162	        val current = stateHolder.uiState.value as? RunnerUiState.Result ?: return
   163	        val domainState = stateHolder.domainState
   164	        val userId =
   165	            when (domainState) {
   166	                is RunnerState.Completed -> domainState.attempt.userId
   167	                is RunnerState.Aborted -> domainState.attempt.userId
   168	                else -> return
   169	            }
   170	        stateHolder.uiState.value = current.copy(ratingSubmissionState = RatingSubmissionState.InProgress)
   171	        scope.launch {
   172	            val result = useCases.submitRating(userId, lessonId, rating)
   173	            val latest = stateHolder.uiState.value as? RunnerUiState.Result ?: return@launch
   174	            if (result.isSuccess) {
   175	                stateHolder.uiState.value =
   176	                    latest.copy(
   177	                        ratingSubmissionState = RatingSubmissionState.Done,
   178	                        showRatingPrompt = false,
   179	                    )
   180	            } else {
   181	                emitEvent(RunnerEvent.SaveRatingFailed)
   182	                stateHolder.uiState.value = latest.copy(ratingSubmissionState = RatingSubmissionState.Failed)
   183	            }
   184	        }
   185	    }
   186	
   187	    override fun onFinish() {
   188	        emitEvent(RunnerEvent.NavigateBack)
   189	    }
   190	
   191	    override fun onBack() {
   192	        emitEvent(RunnerEvent.NavigateBack)
   193	    }
   194	
   195	    private fun handlePause() {
   196	        val domainState = stateHolder.domainState as? RunnerState.Ready ?: return
   197	        if (domainState.isPaused) return
   198	        if (domainState.indexInPool >= domainState.playOrder.size) return
   199	        val nowMs = clock.now().toEpochMilliseconds()
   200	        val newState = autoAnswerOnTimeout(domainState, domainState.seed, nowMs)
   201	        if (newState.indexInPool >= newState.playOrder.size) {
   202	            scope.launch { triggerComplete(newState) }
   203	        } else {
   204	            val pausedState = newState.copy(isPaused = true)
   205	            stateHolder.domainState = pausedState
   206	            stateHolder.uiState.value = pausedState.toQuestionUiState()
   207	        }
   208	    }
   209	
   210	    private suspend fun triggerStart() {
   211	        val result = useCases.startAttempt(lessonId, mode)
   212	        stateHolder.domainState = result
   213	        stateHolder.uiState.value =
   214	            when (result) {
   215	                is RunnerState.Ready -> result.toQuestionUiState()
   216	                is RunnerState.InitFailed -> RunnerUiState.InitFailed(result.reason.toUiReason())
   217	                else -> RunnerUiState.Loading
   218	            }
   219	    }
   220	
   221	    private suspend fun triggerComplete(readyState: RunnerState.Ready) {
   222	        val result = useCases.completeAttempt(readyState)
   223	        stateHolder.domainState = result
   224	        when (result) {
   225	            is RunnerState.Completed -> {
   226	                stateHolder.uiState.value =
   227	                    buildResultUiState(result.attempt, result.ratingPrompt, saveWarning = false)
   228	            }
   229	            is RunnerState.SaveFailed -> {
   230	                emitEvent(RunnerEvent.SaveAttemptFailed(result.error.toEventSaveError()))
   231	                stateHolder.uiState.value =
   232	                    buildResultUiState(
   233	                        result.attempt,
   234	                        showRatingPrompt = false,
   235	                        saveWarning = true,
   236	                    )
   237	            }
   238	            else -> {}
   239	        }
   240	    }
   241	
   242	    private suspend fun triggerAbort(readyState: RunnerState.Ready) {
   243	        val result = useCases.abortAttempt(readyState)
   244	        stateHolder.domainState = result
   245	        when (result) {
   246	            is RunnerState.SaveFailed -> {
   247	                emitEvent(RunnerEvent.SaveAttemptFailed(result.error.toEventSaveError()))
   248	                stateHolder.uiState.value =
   249	                    buildResultUiState(
   250	                        result.attempt,
   251	                        showRatingPrompt = false,
   252	                        saveWarning = true,
   253	                    )
   254	            }
   255	            else -> emitEvent(RunnerEvent.NavigateBack)
   256	        }
   257	    }
   258	
   259	    private suspend fun buildResultUiState(
   260	        attempt: Attempt,
   261	        showRatingPrompt: Boolean,
   262	        saveWarning: Boolean,
   263	    ): RunnerUiState.Result {
   264	        val lesson = lessonRepository.getById(attempt.lessonId)
   265	        val userAttempts = attemptRepository.observeByLesson(attempt.userId, attempt.lessonId).first()
   266	        val userAttemptCount = userAttempts.size
   267	        val userAveragePercentScore =
   268	            if (userAttempts.isEmpty()) {
   269	                0
   270	            } else {
   271	                userAttempts.sumOf { it.percentScore.raw } / userAttempts.size
   272	            }
   273	        return RunnerUiState.Result(
   274	            percentScore = attempt.percentScore,
   275	            mode = attempt.mode,
   276	            completedAt = attempt.completedAt,
   277	            hardUnlocked = computeHardUnlocked(userAttempts),
   278	            bestStarsRawTenths = computeBestStars(userAttempts).rawTenths,
   279	            currentAttemptStarsRawTenths = computeStars(attempt.percentScore, attempt.mode).rawTenths,
   280	            lessonAverageRating = lesson?.averageRating,
   281	            lessonRatingCount = lesson?.ratingCount ?: 0,
   282	            top3 = lesson?.top3 ?: emptyList(),
   283	            userAttemptCount = userAttemptCount,
   284	            userAveragePercentScore = userAveragePercentScore,
   285	            showRatingPrompt = showRatingPrompt,
   286	            saveWarning = saveWarning,
   287	        )
   288	    }
   289	
   290	    private fun emitEvent(event: RunnerEvent) {
   291	        _events.trySend(event)
   292	    }
   293	}

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/RunnerStateHolder.kt | sed -n '1,420p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component
     2	
     3	import com.arkivanov.essenty.instancekeeper.InstanceKeeper
     4	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
     6	import kotlinx.coroutines.CoroutineScope
     7	import kotlinx.coroutines.Dispatchers
     8	import kotlinx.coroutines.SupervisorJob
     9	import kotlinx.coroutines.cancel
    10	import kotlinx.coroutines.flow.MutableStateFlow
    11	import kotlin.coroutines.CoroutineContext
    12	
    13	class RunnerStateHolder(
    14	    mainContext: CoroutineContext = Dispatchers.Main.immediate,
    15	) : InstanceKeeper.Instance {
    16	    val scope = CoroutineScope(SupervisorJob() + mainContext)
    17	    val uiState: MutableStateFlow<RunnerUiState> = MutableStateFlow(RunnerUiState.Loading)
    18	    var domainState: RunnerState = RunnerState.Loading
    19	
    20	    // Set to true in doOnDestroy (Decompose lifecycle). Cleared by InstanceKeeper.onDestroy
    21	    // (navigation pop / true process death). If still true when new component init runs,
    22	    // means config change — restore pre-stop snapshot instead of applying the pause.
    23	    var pendingConfigChangeRestore: Boolean = false
    24	    var snapshotBeforeStop: Pair<RunnerState, RunnerUiState>? = null
    25	
    26	    override fun onDestroy() {
    27	        // Called on navigation pop or process death — NOT on config change.
    28	        pendingConfigChangeRestore = false
    29	        snapshotBeforeStop = null
    30	        scope.cancel()
    31	        uiState.value = RunnerUiState.Loading
    32	        domainState = RunnerState.Loading
    33	    }
    34	}

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ResultContent.kt | sed -n '1,320p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
     2	
     3	import androidx.compose.foundation.layout.Arrangement
     4	import androidx.compose.foundation.layout.Column
     5	import androidx.compose.foundation.layout.fillMaxWidth
     6	import androidx.compose.foundation.layout.padding
     7	import androidx.compose.foundation.rememberScrollState
     8	import androidx.compose.foundation.verticalScroll
     9	import androidx.compose.material3.Button
    10	import androidx.compose.material3.MaterialTheme
    11	import androidx.compose.material3.Text
    12	import androidx.compose.runtime.Composable
    13	import androidx.compose.ui.Alignment
    14	import androidx.compose.ui.Modifier
    15	import androidx.compose.ui.tooling.preview.Preview
    16	import androidx.compose.ui.unit.dp
    17	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    18	import com.tpov.schoolquiz.android.core.designsystem.components.StarRating
    19	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
    20	import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
    21	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
    22	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
    23	
    24	private const val PERFECT_SCORE = 100
    25	private const val STARS_DIVISOR = 10
    26	
    27	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    28	@Composable
    29	fun ResultContent(
    30	    state: RunnerUiState.Result,
    31	    onSubmitRating: (Int) -> Unit,
    32	    onFinish: () -> Unit,
    33	    modifier: Modifier = Modifier,
    34	) {
    35	    val subtitle =
    36	        when {
    37	            state.mode == Difficulty.EASY && state.percentScore.raw == PERFECT_SCORE && state.hardUnlocked ->
    38	                "Поздравляем! Сложные вопросы доступны"
    39	            state.mode == Difficulty.HARD && state.percentScore.raw == PERFECT_SCORE ->
    40	                "100% сложные! Вы прошли урок полностью"
    41	            else -> "Урок завершён"
    42	        }
    43	
    44	    Column(
    45	        modifier =
    46	            modifier
    47	                .fillMaxWidth()
    48	                .verticalScroll(rememberScrollState())
    49	                .padding(16.dp),
    50	        horizontalAlignment = Alignment.CenterHorizontally,
    51	        verticalArrangement = Arrangement.spacedBy(16.dp),
    52	    ) {
    53	        Text(
    54	            text = "${state.percentScore.raw}%",
    55	            style = MaterialTheme.typography.displayLarge,
    56	        )
    57	        Text(
    58	            text = subtitle,
    59	            style = MaterialTheme.typography.bodyLarge,
    60	        )
    61	        StarRating(rating = state.currentAttemptStarsRawTenths / STARS_DIVISOR.toFloat())
    62	        Text(
    63	            text = "Попыток: ${state.userAttemptCount} | Средний %: ${state.userAveragePercentScore}",
    64	            style = MaterialTheme.typography.bodyMedium,
    65	            color = MaterialTheme.colorScheme.onSurfaceVariant,
    66	        )
    67	        if (state.saveWarning) {
    68	            Text(
    69	                text = "⚠ Результат не сохранён",
    70	                color = MaterialTheme.colorScheme.error,
    71	                style = MaterialTheme.typography.bodyMedium,
    72	            )
    73	        }
    74	        if (state.showRatingPrompt) {
    75	            RatingPromptSection(
    76	                ratingSubmissionState = state.ratingSubmissionState,
    77	                onSubmitRating = onSubmitRating,
    78	            )
    79	        }
    80	        if (state.top3.isNotEmpty()) {
    81	            Top3Section(top3 = state.top3)
    82	        }
    83	        Button(
    84	            onClick = onFinish,
    85	            modifier = Modifier.fillMaxWidth(),
    86	        ) {
    87	            Text("Завершить")
    88	        }
    89	    }
    90	}
    91	
    92	@Suppress("FunctionNaming", "UnusedPrivateMember", "MagicNumber", "ktlint:standard:function-naming")
    93	@Preview(showBackground = true)
    94	@Composable
    95	private fun ResultContentPreview() {
    96	    SchoolQuizTheme {
    97	        ResultContent(
    98	            state =
    99	                RunnerUiState.Result(
   100	                    percentScore = PercentScore(85),
   101	                    mode = Difficulty.EASY,
   102	                    completedAt = 0L,
   103	                    hardUnlocked = false,
   104	                    bestStarsRawTenths = 20,
   105	                    currentAttemptStarsRawTenths = 17,
   106	                    lessonAverageRating = 2.5f,
   107	                    lessonRatingCount = 10,
   108	                    top3 =
   109	                        listOf(
   110	                            TopParticipant("Alice", null, 95),
   111	                            TopParticipant("Bob", null, 88),
   112	                        ),
   113	                    userAttemptCount = 3,
   114	                    userAveragePercentScore = 75,
   115	                    showRatingPrompt = true,
   116	                    saveWarning = false,
   117	                ),
   118	            onSubmitRating = {},
   119	            onFinish = {},
   120	        )
   121	    }
   122	}
   123	
   124	@Suppress("FunctionNaming", "UnusedPrivateMember", "MagicNumber", "ktlint:standard:function-naming")
   125	@Preview(showBackground = true)
   126	@Composable
   127	private fun ResultContentSaveWarningPreview() {
   128	    SchoolQuizTheme {
   129	        ResultContent(
   130	            state =
   131	                RunnerUiState.Result(
   132	                    percentScore = PercentScore(60),
   133	                    mode = Difficulty.HARD,
   134	                    completedAt = 0L,
   135	                    hardUnlocked = false,
   136	                    bestStarsRawTenths = 10,
   137	                    currentAttemptStarsRawTenths = 26,
   138	                    lessonAverageRating = null,
   139	                    lessonRatingCount = 0,
   140	                    top3 = emptyList(),
   141	                    userAttemptCount = 1,
   142	                    userAveragePercentScore = 60,
   143	                    showRatingPrompt = false,
   144	                    saveWarning = true,
   145	                ),
   146	            onSubmitRating = {},
   147	            onFinish = {},
   148	        )
   149	    }
   150	}

exec
/bin/bash -lc 'nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation
     2	
     3	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
     4	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
     6	import kotlinx.coroutines.flow.Flow
     7	import kotlinx.coroutines.flow.StateFlow
     8	
     9	interface LessonRunnerRootComponent {
    10	    val uiState: StateFlow<RunnerUiState>
    11	    val events: Flow<RunnerEvent>
    12	
    13	    fun onAnswer(answer: UserAnswerDraft)
    14	
    15	    fun onTimeout()
    16	
    17	    fun onContinue()
    18	
    19	    fun onExit()
    20	
    21	    fun onCrossButtonTap()
    22	
    23	    fun onCrossConfirmed()
    24	
    25	    fun onCrossCancelled()
    26	
    27	    fun onSubmitRating(rating: Int)
    28	
    29	    fun onFinish()
    30	
    31	    fun onBack()
    32	}

exec
/bin/bash -lc 'nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/event/RunnerEvent.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event
     2	
     3	enum class SaveError { IoError, Unknown }
     4	
     5	sealed interface RunnerEvent {
     6	    data class SaveAttemptFailed(val error: SaveError) : RunnerEvent
     7	
     8	    data object SaveRatingFailed : RunnerEvent
     9	
    10	    data object NavigateBack : RunnerEvent
    11	}

exec
/bin/bash -lc 'nl -ba android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/navigation/QuizzesNavigator.kt' in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.navigation
     2	
     3	import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
     4	import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
     5	
     6	interface QuizzesNavigator {
     7	    fun openQuestList(
     8	        catalogId: CatalogId,
     9	        catalogName: String,
    10	    )
    11	
    12	    fun openSectionList(
    13	        questId: QuestId,
    14	        titles: List<String>,
    15	    )
    16	
    17	    fun dismissQuizzes()
    18	}

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.app_shell.presentation.component
     2	
     3	import android.util.Log
     4	import androidx.work.Constraints
     5	import androidx.work.ExistingWorkPolicy
     6	import androidx.work.NetworkType
     7	import androidx.work.OneTimeWorkRequestBuilder
     8	import androidx.work.WorkManager
     9	import com.arkivanov.decompose.ComponentContext
    10	import com.arkivanov.decompose.childContext
    11	import com.arkivanov.decompose.router.stack.StackNavigation
    12	import com.arkivanov.essenty.backhandler.BackCallback
    13	import com.arkivanov.essenty.backhandler.BackHandler
    14	import com.arkivanov.essenty.lifecycle.doOnDestroy
    15	import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
    16	import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.tab.DefaultEventsTabComponent
    17	import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.tab.DefaultInternetTabComponent
    18	import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.tab.DefaultLocalTabComponent
    19	import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.tab.DefaultShopTabComponent
    20	import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsComponent
    21	import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsComponent
    22	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
    23	import com.tpov.schoolquiz.platform.android_services.sync.SyncWorker
    24	import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
    25	import com.tpov.schoolquiz.shared.core.foundation.QualificationLevel
    26	import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DeepLink
    27	import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
    28	import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.EventsConfig
    29	import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.InternetConfig
    30	import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.LocalConfig
    31	import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RetapOutcome
    32	import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RootEvent
    33	import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.ShopConfig
    34	import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
    35	import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
    36	import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.Navigator
    37	import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.RootComponent
    38	import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
    39	import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.AppShellState
    40	import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.NavStack
    41	import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.TransitionResult
    42	import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.InitializeAppShellUseCase
    43	import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.NavigateUseCase
    44	import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.ObserveAppShellStateUseCase
    45	import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.OnTabRetapUseCase
    46	import com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode.model.TapProgress
    47	import com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode.model.TapResult
    48	import com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode.use_case.ActivateDevModeUseCase
    49	import kotlinx.coroutines.CancellationException
    50	import kotlinx.coroutines.CoroutineScope
    51	import kotlinx.coroutines.Dispatchers
    52	import kotlinx.coroutines.SupervisorJob
    53	import kotlinx.coroutines.channels.Channel
    54	import kotlinx.coroutines.delay
    55	import kotlinx.coroutines.flow.Flow
    56	import kotlinx.coroutines.flow.MutableStateFlow
    57	import kotlinx.coroutines.flow.asStateFlow
    58	import kotlinx.coroutines.flow.receiveAsFlow
    59	import kotlinx.coroutines.flow.update
    60	import kotlinx.coroutines.isActive
    61	import kotlinx.coroutines.launch
    62	
    63	/**
    64	 * Decompose implementation of [RootComponent].
    65	 *
    66	 * Wires all navigation use cases + UserStats observation.
    67	 * Created via Koin factory(ComponentContext) per ADR-COMP-07.
    68	 *
    69	 * Coroutine scope is tied to component lifecycle via [lifecycle.doOnDestroy] — cancelled on
    70	 * every Activity destroy (including rotation). Each new component starts with a clean scope,
    71	 * preventing orphaned coroutines from accumulating across configuration changes.
    72	 */
    73	@Suppress("LongParameterList", "TooGenericExceptionCaught")
    74	class DefaultRootComponent(
    75	    componentContext: ComponentContext,
    76	    private val initUseCase: InitializeAppShellUseCase,
    77	    private val navigateUseCase: NavigateUseCase,
    78	    private val observeUseCase: ObserveAppShellStateUseCase,
    79	    private val retapUseCase: OnTabRetapUseCase,
    80	    private val userStatsRepository: UserStatsRepository,
    81	    private val workManager: WorkManager,
    82	    myQuestsFactory: (ComponentContext, Navigator, (QuestDisplayItem) -> Unit) -> MyQuestsComponent,
    83	    homeQuestsFactory: (ComponentContext, (CatalogId, String) -> Unit) -> HomeQuestsComponent,
    84	    quizzesFactory: (ComponentContext) -> QuizzesComponent,
    85	) : RootComponent, ComponentContext by componentContext {
    86	    private val _appShellState = MutableStateFlow(AppShellState.fallback(UserStats.guest()))
    87	    private val tapProgressState = MutableStateFlow(TapProgress.initial)
    88	
    89	    private val activateDevModeUseCase =
    90	        ActivateDevModeUseCase(
    91	            readCurrentDeveloperLevel = { _appShellState.value.userStats.qualification.developer },
    92	            onDevModeActivated = { userStatsRepository.setLocalDeveloperLevel(QualificationLevel.LEVEL_1.points) },
    93	        )
    94	    override val appShellState: Flow<AppShellState> = _appShellState.asStateFlow()
    95	
    96	    @Volatile private var initDone = false
    97	
    98	    /**
    99	     * `pendingStats` applies only if non-guest. Trade-off: legitimate guest pre-init emission
   100	     * (uid=null scenario) is absorbed by init state — this is acceptable because init also
   101	     * produces guest stats from the same datasource. When auth changes post-init, observer
   102	     * emits the new non-guest stats via normal (non-buffered) path.
   103	     *
   104	     * Alternative: accept all pre-init emissions → breaks cold start race test where cached
   105	     * stale guest would override fresher init snapshot. See cross-phase review round 3.
   106	     */
   107	    @Volatile private var pendingStats: UserStats? = null
   108	
   109	    /**
   110	     * Single-consumer event channel. Only one collector should be active at a time.
   111	     * The channel is BUFFERED (capacity=64) — events are never dropped under normal navigation load.
   112	     */
   113	    private val _events = Channel<RootEvent>(Channel.BUFFERED)
   114	    override val events: Flow<RootEvent> = _events.receiveAsFlow()
   115	
   116	    private val componentJob = SupervisorJob()
   117	    private val scope = CoroutineScope(componentJob + Dispatchers.Main.immediate)
   118	
   119	    private val localNavigation = StackNavigation<LocalConfig>()
   120	    private val internetNavigation = StackNavigation<InternetConfig>()
   121	    private val eventsNavigation = StackNavigation<EventsConfig>()
   122	    private val shopNavigation = StackNavigation<ShopConfig>()
   123	
   124	    internal val localTabComponent = DefaultLocalTabComponent(childContext("LocalTab"), localNavigation)
   125	    internal val internetTabComponent = DefaultInternetTabComponent(childContext("InternetTab"), internetNavigation)
   126	    internal val eventsTabComponent = DefaultEventsTabComponent(childContext("EventsTab"), eventsNavigation)
   127	    internal val shopTabComponent = DefaultShopTabComponent(childContext("ShopTab"), shopNavigation)
   128	
   129	    /**
   130	     * Navigator for programmatic destination changes.
   131	     * Lifetime: tied to this component instance (Activity-scoped). Do not retain beyond Activity.
   132	     */
   133	    internal val navigator: Navigator = NavigatorImpl(this)
   134	
   135	    // quizzesComponent MUST be created first — homeQuestsComponent and myQuestsComponent lambdas
   136	    // capture it by reference. Decompose childContext keys are unique per parent context (duplicate-key
   137	    // crash prevention on Compose recomposition).
   138	    //
   139	    // QuizzesComponent uses root's backHandler directly (not wrapped via childContext) so its
   140	    // BackCallback(priority=100) is visible at the root BackHandler level per ADR-QS-12.
   141	    // Decompose 3.x childContext wraps backHandler in DefaultChildBackHandler, which would expose
   142	    // only a priority-0 bridge callback at root level, breaking back priority ordering.
   143	    val quizzesComponent: QuizzesComponent = quizzesFactory(quizzesComponentContext())
   144	    val homeQuestsComponent: HomeQuestsComponent =
   145	        homeQuestsFactory(
   146	            childContext("HomeQuestsContent"),
   147	            { catId: CatalogId, name: String -> quizzesComponent.openQuestList(catId, name) },
   148	        )
   149	    val myQuestsComponent: MyQuestsComponent =
   150	        myQuestsFactory(
   151	            childContext("MyQuestsContent"),
   152	            navigator,
   153	            { quest: QuestDisplayItem ->
   154	                val catalogName =
   155	                    homeQuestsComponent.state.value.catalogs
   156	                        .find { it.id == quest.catalogId }?.name ?: "Без каталога"
   157	                quizzesComponent.openSectionList(quest.id, listOf(catalogName, quest.title))
   158	            },
   159	        )
   160	
   161	    init {
   162	        lifecycle.doOnDestroy { componentJob.cancel() }
   163	
   164	        // Essenty BackCallback — not Jetpack BackHandler.
   165	        // isEnabled = true always: Back at root no-ops via domain FSM (state machine absorbs it).
   166	        // defaultComponentContext() auto-connects to Activity.onBackPressedDispatcher.
   167	        backHandler.register(
   168	            BackCallback(isEnabled = true) {
   169	                onDestination(Destination.Back)
   170	            },
   171	        )
   172	
   173	        // Cold start: merge initialState into current.
   174	        // If user hasn't navigated (current == guest fallback): apply full initialState.
   175	        // If user navigated before init completed: preserve their navigation, merge only userStats.
   176	        // initDone gate ensures the observer cannot race-update state before this point.
   177	        // initDone is set AFTER state update so observer never sees flag=true on stale state.
   178	        scope.launch {
   179	            val initialState =
   180	                runCatching { initUseCase() }
   181	                    .getOrElse { AppShellState.fallback(UserStats.guest()) }
   182	            if (!initDone) {
   183	                val before = _appShellState.value
   184	                val merged =
   185	                    if (before == AppShellState.fallback(UserStats.guest())) {
   186	                        initialState
   187	                    } else {
   188	                        before.copy(userStats = initialState.userStats)
   189	                    }
   190	                // Apply pendingStats only if observer delivered a non-guest user before init:
   191	                // initial observer emission == UserStats.guest() (cached value, same as init fallback).
   192	                // A real-time update (logged-in user) arrives as non-guest and should win over init's cache.
   193	                val finalStats = pendingStats?.takeIf { it != UserStats.guest() } ?: merged.userStats
   194	                _appShellState.update { merged.copy(userStats = finalStats) }
   195	                initDone = true
   196	                val after = _appShellState.value
   197	                syncStack(before.localState.stack, after.localState.stack, localNavigation)
   198	                syncStack(before.internetState.stack, after.internetState.stack, internetNavigation)
   199	                syncStack(before.eventsState.stack, after.eventsState.stack, eventsNavigation)
   200	                syncStack(before.shopState.stack, after.shopState.stack, shopNavigation)
   201	            }
   202	        }
   203	
   204	        // Stats observer: reconnects after normal Flow completion (logout) or error.
   205	        // Observer only updates userStats after initDone — nav state is owned by init.
   206	        scope.launch {
   207	            while (isActive) {
   208	                try {
   209	                    observeUseCase { _appShellState.value }.collect { newState ->
   210	                        if (!initDone) {
   211	                            pendingStats = newState.userStats
   212	                        } else {
   213	                            _appShellState.update { it.copy(userStats = newState.userStats) }
   214	                        }
   215	                    }
   216	                    delay(OBSERVER_RECONNECT_DELAY_MILLIS)
   217	                } catch (e: CancellationException) {
   218	                    throw e
   219	                } catch (e: Exception) {
   220	                    Log.w(TAG, "observeStats error — retrying in 5s", e)
   221	                    delay(OBSERVER_RETRY_DELAY_MILLIS)
   222	                }
   223	            }
   224	        }
   225	    }
   226	
   227	    override fun onDestination(destination: Destination) {
   228	        val current = _appShellState.value
   229	        val result = navigateUseCase(current, destination)
   230	        applyResult(current, result)
   231	        if (result.events.isNotEmpty()) {
   232	            scope.launch { result.events.forEach { _events.send(it) } }
   233	        }
   234	    }
   235	
   236	    override fun onActiveTabRetap(tab: Tab): RetapOutcome {
   237	        val old = _appShellState.value
   238	        val (newState, outcome) = retapUseCase(old, tab)
   239	        applyResult(old, TransitionResult(newState))
   240	        return outcome
   241	    }
   242	
   243	    override fun onDeepLink(deepLink: DeepLink) {
   244	        // MVP stub — no URL patterns registered yet.
   245	        // Future: validate DeepLink.scheme and origin before processing (reject unknown schemes).
   246	    }
   247	
   248	    override fun onVersionTap(nowMillis: Long) {
   249	        scope.launch {
   250	            // Single-threaded coroutine launch via Main.immediate (no parallel tap processing
   251	            // before first suspend point). FSM state transitions before ActivateDevModeUseCase's
   252	            // suspend — so a racing tap coroutine reads the updated progress, not the stale one.
   253	            val snapshot = tapProgressState.value
   254	            when (val result = activateDevModeUseCase(snapshot, nowMillis)) {
   255	                is TapResult.Activated -> {
   256	                    tapProgressState.value = result.newProgress
   257	                    sendEvent(RootEvent.DevModeActivated)
   258	                }
   259	                is TapResult.AlreadyDev -> {
   260	                    tapProgressState.value = result.newProgress

exec
/bin/bash -lc "rg -n \"handleBackButton|BackCallback|onBack\\(|popCurrentChild\\(|NavigateBack|LessonRunner\" android/feature/lesson-runner/presentation android/feature/quizzes-screen/presentation android/feature/app-shell/presentation -g '*.kt'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/tab/InternetTabComponent.kt:24:            handleBackButton = false,
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/tab/EventsTabComponent.kt:24:            handleBackButton = false,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:4:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:5:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:6:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:7:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.LessonRunnerUseCases
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:20:            DefaultLessonRunnerRootComponent(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:25:                    LessonRunnerUseCases(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:34:            ) as LessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:37:        single<LessonRunnerComponentFactory> {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:38:            LessonRunnerComponentFactory { ctx, lessonId, mode ->
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/tab/ShopTabComponent.kt:24:            handleBackButton = false,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt:9:interface LessonRunnerRootComponent {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt:31:    fun onBack()
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/tab/LocalTabComponent.kt:24:            handleBackButton = false,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt:7:fun interface LessonRunnerComponentFactory {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt:12:    ): LessonRunnerRootComponent
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:12:import com.arkivanov.essenty.backhandler.BackCallback
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:140:    // BackCallback(priority=100) is visible at the root BackHandler level per ADR-QS-12.
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:164:        // Essenty BackCallback — not Jetpack BackHandler.
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:168:            BackCallback(isEnabled = true) {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/LessonRunnerUseCases.kt:7:class LessonRunnerUseCases(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:7:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:40:class DefaultLessonRunnerRootComponent(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:44:    private val useCases: LessonRunnerUseCases,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:49:) : ComponentContext by componentContext, LessonRunnerRootComponent {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:188:        emitEvent(RunnerEvent.NavigateBack)
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:191:    override fun onBack() {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:192:        emitEvent(RunnerEvent.NavigateBack)
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:255:            else -> emitEvent(RunnerEvent.NavigateBack)
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt:4:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt:20:                lessonRunnerFactory = get<LessonRunnerComponentFactory>(),
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesComponent.kt:18:    fun popCurrentChild()
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt:3:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt:16:    data class LessonRunner(val component: LessonRunnerRootComponent) : QuizzesChild
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:29:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:60:fun LessonRunnerScreen(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:61:    component: LessonRunnerRootComponent,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:62:    onNavigateBack: () -> Unit,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:84:                RunnerEvent.NavigateBack -> onNavigateBack()
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:110:    component: LessonRunnerRootComponent,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:121:                    onBack = { component.onBack() },
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:154:    component: LessonRunnerRootComponent,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:188:    component: LessonRunnerRootComponent,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:293:private fun LessonRunnerScreenLoadingPreview() {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:295:        LessonRunnerScreen(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:296:            component = PreviewLessonRunnerComponent(RunnerUiState.Loading),
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:297:            onNavigateBack = {},
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:306:private fun LessonRunnerScreenInitFailedPreview() {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:308:        LessonRunnerScreen(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:310:                PreviewLessonRunnerComponent(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:313:            onNavigateBack = {},
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:319:private class PreviewLessonRunnerComponent(initialState: RunnerUiState) : LessonRunnerRootComponent {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:341:    override fun onBack() = Unit
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:12:import com.arkivanov.essenty.backhandler.BackCallback
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:14:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:37:    private val lessonRunnerFactory: LessonRunnerComponentFactory,
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:60:                    ?.filterNot { it is QuizzesConfig.LessonRunner }
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:63:            handleBackButton = false,
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:69:        BackCallback(priority = 100, isEnabled = false) {
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:104:                is QuizzesConfig.LessonRunner -> cfg.titles.size
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:119:    override fun popCurrentChild() {
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:158:            is QuizzesConfig.LessonRunner ->
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:159:                QuizzesChild.LessonRunner(
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:81:            QuizzesConfig.LessonRunner(
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt:16:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.LessonRunnerScreen
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt:42:            is QuizzesChild.LessonRunner ->
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt:43:                LessonRunnerScreen(
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt:45:                    onNavigateBack = { component.popCurrentChild() },
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/event/RunnerEvent.kt:10:    data object NavigateBack : RunnerEvent
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/config/QuizzesConfig.kt:36:    data class LessonRunner(
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:9:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:41: * Integration tests for DefaultLessonRunnerRootComponent — state holder rotation and lifecycle.
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:51:class LessonRunnerIntegrationTest {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:98:    ): DefaultLessonRunnerRootComponent {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:106:        return DefaultLessonRunnerRootComponent(
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:110:            useCases = com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.LessonRunnerUseCases(
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:10:import com.arkivanov.essenty.backhandler.BackCallback
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:40:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:86: *               AC#6 (dismiss), INT-04/INT-05 (BackCallback priority)
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:120:    /** Records BackCallback registrations for priority inspection (INT-04). */
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:122:        val registeredCallbacks = mutableListOf<BackCallback>()
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:123:        override fun register(callback: BackCallback) { registeredCallbacks += callback }
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:124:        override fun unregister(callback: BackCallback) { registeredCallbacks -= callback }
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:125:        override fun isRegistered(callback: BackCallback) = registeredCallbacks.contains(callback)
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:164:        override fun popCurrentChild() = Unit
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:218:     * BackCallback registration from both root and quizzes can be inspected (INT-04/INT-05).
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:269:                lessonRunnerFactory = LessonRunnerComponentFactory { _, _, _ -> error("Not expected") },
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:410:    // WHEN both components register BackCallbacks on the shared BackHandler
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:414:    //   DefaultRootComponent.init: backHandler.register(BackCallback(isEnabled = true))       → priority 0
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:415:    //   DefaultQuizzesComponent.init: backHandler.register(BackCallback(priority = 100, ...)) → priority 100
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:427:        assertNotNull(quizzesCallback, "QuizzesComponent must register BackCallback with priority == 100")
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:430:        assertNotNull(rootCallback, "Root must register BackCallback with priority < 100")
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:439:    // WHEN QuizzesComponent childStack is Idle (its BackCallback is disabled)
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:441:    // THEN back() returns true — Root's BackCallback (priority 0, isEnabled=true) handles Back
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:443:    // DefaultQuizzesComponent: BackCallback(priority=100, isEnabled=false initially);
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:445:    // DefaultRootComponent: BackCallback(isEnabled=true) — always enabled, handles Back via FSM.
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:462:            "BackDispatcher.back() must return true when root BackCallback (isEnabled=true) handles Back in Idle state")
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/fake/StubQuestComponents.kt:44:    override fun popCurrentChild() = Unit
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:8:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:53: * Presentation unit tests for [DefaultLessonRunnerRootComponent].
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:64:class DefaultLessonRunnerRootComponentTest {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:107:    ): DefaultLessonRunnerRootComponent {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:111:        return DefaultLessonRunnerRootComponent(
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:115:            useCases = com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.LessonRunnerUseCases(
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:207:    private fun advanceToResultState(component: DefaultLessonRunnerRootComponent) {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:268:    // ── PT-05: onFinish → emit NavigateBack ──────────────────────────────────
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:270:    // PT-05: GIVEN state = Completed; WHEN onFinish() THEN emit RunnerEvent.NavigateBack
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:282:        assertIs<RunnerEvent.NavigateBack>(deferred.await(), "onFinish must emit NavigateBack")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:565:    // ── PT-25: onExit → AbortUseCase → Aborted → NavigateBack ────────────────
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:568:    //        WHEN onExit() THEN fakeAbortUseCase.callCount == 1 + NavigateBack emitted
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:578:        assertIs<RunnerEvent.NavigateBack>(deferred.await(), "onExit must emit NavigateBack event")
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:111:    // ── SER-06 — QuizzesConfig.LessonRunner ──────────────────────────────────
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:114:     * Spec: SER-06 — LessonRunner preserves lessonId, mode, and titles.
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:116:     * Also covers: Ser-02 (tests.md §Ser-02) — LessonRunner EASY mode round-trip.
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:119:    fun `QuizzesConfig LessonRunner round-trip preserves lessonId and mode`() {
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:120:        val original = QuizzesConfig.LessonRunner(
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:127:        val runner = assertIs<QuizzesConfig.LessonRunner>(decoded)
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:132:    // ── Ser-01 (Phase-06) — LessonRunner HARD round-trip ────────────────────────
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:135:     * Phase-06 Ser-01: LessonRunner(mode=HARD) preserves mode after round-trip.
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:139:    fun `QuizzesConfig LessonRunner HARD mode round-trip`() {
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:140:        val original = QuizzesConfig.LessonRunner(
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:147:        val runner = assertIs<QuizzesConfig.LessonRunner>(decoded)
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:15:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:78:            lessonRunnerFactory = LessonRunnerComponentFactory { _, _, _ -> error("Not expected") },
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:127:     * Back dispatch fires the registered BackCallback → navigation.pop().
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:154:     * BackCallback.PRIORITY_OVERLAY is absent in Essenty 2.1.0 (Pattern Invariant 5,
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:163:        // With handleBackButton=false Decompose registers no internal callbacks.
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeBackDispatcher.kt:3:import com.arkivanov.essenty.backhandler.BackCallback
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeBackDispatcher.kt:7: * Test-only BackHandler that records registered BackCallbacks for inspection.
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeBackDispatcher.kt:12:    private val _callbacks = mutableListOf<BackCallback>()
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeBackDispatcher.kt:13:    val callbacks: List<BackCallback> get() = _callbacks.toList()
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeBackDispatcher.kt:15:    override fun register(callback: BackCallback) {
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeBackDispatcher.kt:19:    override fun unregister(callback: BackCallback) {
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeBackDispatcher.kt:23:    override fun isRegistered(callback: BackCallback): Boolean = _callbacks.contains(callback)
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuizzesComponent.kt:35:    override fun popCurrentChild() {
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:13:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:86:            lessonRunnerFactory = LessonRunnerComponentFactory { _, _, _ -> error("Not expected") },
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:164:    // ── PD-04 — BackCallback enabled after restoration ────────────────────────
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:15:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.LessonRunnerScreen
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:29:class LessonRunnerScreenTest {
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:95:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:114:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:132:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:147:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:163:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:183:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:201:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:219:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:242:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:265:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:290:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:307:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:326:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:344:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:359:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:375:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:392:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:401:    @Ignore("Phase-06 scope: belongs to LessonItemCard, not LessonRunnerScreen")
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:431:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:448:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:463:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:479:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:500:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:513:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:521:    // --- CT-30: GIVEN NavigateBack event WHEN emitted THEN onNavigateBack callback invoked ---
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:524:    fun ct30_navigateBack_event_invokes_onNavigateBack_callback() {
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:530:                LessonRunnerScreen(fakeComponent, onNavigateBack = { navCalled = true }, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:535:        fakeComponent.emit(RunnerEvent.NavigateBack)
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/RunFakeComponent.kt:3:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/RunFakeComponent.kt:16:) : LessonRunnerRootComponent {
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/RunFakeComponent.kt:78:    override fun onBack() {
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:139:     * Spec: LL-U-04 — onLessonClick with hardUnlocked=false pushes LessonRunner with EASY mode.
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:143:    fun `onLessonClick pushes LessonRunner with EASY mode when hardUnlocked is false`() = runTest(testScheduler) {
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:150:        assertIs<QuizzesConfig.LessonRunner>(pushed)
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:160:    fun `onLessonClick pushes LessonRunner with HARD mode when hardUnlocked and isHardChecked`() = runTest(testScheduler) {
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:166:        val pushed = fakeNavigation.pushedConfigs.last() as QuizzesConfig.LessonRunner

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/QuestionProgressHeader.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/RatingPromptSection.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
     2	
     3	import androidx.compose.foundation.layout.Arrangement
     4	import androidx.compose.foundation.layout.Column
     5	import androidx.compose.foundation.layout.Row
     6	import androidx.compose.foundation.layout.fillMaxWidth
     7	import androidx.compose.material.icons.Icons
     8	import androidx.compose.material.icons.filled.Star
     9	import androidx.compose.material.icons.outlined.StarOutline
    10	import androidx.compose.material3.Icon
    11	import androidx.compose.material3.IconButton
    12	import androidx.compose.material3.MaterialTheme
    13	import androidx.compose.material3.Text
    14	import androidx.compose.runtime.Composable
    15	import androidx.compose.runtime.LaunchedEffect
    16	import androidx.compose.runtime.getValue
    17	import androidx.compose.runtime.mutableIntStateOf
    18	import androidx.compose.runtime.remember
    19	import androidx.compose.runtime.setValue
    20	import androidx.compose.ui.Alignment
    21	import androidx.compose.ui.Modifier
    22	import androidx.compose.ui.tooling.preview.Preview
    23	import androidx.compose.ui.unit.dp
    24	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    25	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState.RatingSubmissionState
    26	
    27	private const val MAX_RATING = 3
    28	
    29	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    30	@Composable
    31	fun RatingPromptSection(
    32	    ratingSubmissionState: RatingSubmissionState,
    33	    onSubmitRating: (Int) -> Unit,
    34	    modifier: Modifier = Modifier,
    35	) {
    36	    var selectedRating by remember { mutableIntStateOf(0) }
    37	    val isEnabled =
    38	        ratingSubmissionState == RatingSubmissionState.Idle ||
    39	            ratingSubmissionState == RatingSubmissionState.Failed
    40	
    41	    // On failure: reset local selection so user can pick again
    42	    LaunchedEffect(ratingSubmissionState) {
    43	        if (ratingSubmissionState == RatingSubmissionState.Failed) {
    44	            selectedRating = 0
    45	        }
    46	    }
    47	
    48	    Column(
    49	        modifier = modifier.fillMaxWidth(),
    50	        horizontalAlignment = Alignment.CenterHorizontally,
    51	        verticalArrangement = Arrangement.spacedBy(8.dp),
    52	    ) {
    53	        Text(
    54	            text = "Оцените урок",
    55	            style = MaterialTheme.typography.titleMedium,
    56	        )
    57	        Row(
    58	            horizontalArrangement = Arrangement.spacedBy(4.dp),
    59	            verticalAlignment = Alignment.CenterVertically,
    60	        ) {
    61	            for (i in 1..MAX_RATING) {
    62	                IconButton(
    63	                    onClick = {
    64	                        if (isEnabled) {
    65	                            selectedRating = i
    66	                            onSubmitRating(i)
    67	                        }
    68	                    },
    69	                    enabled = isEnabled,
    70	                ) {
    71	                    Icon(
    72	                        imageVector =
    73	                            if (i <= selectedRating) {
    74	                                Icons.Filled.Star
    75	                            } else {
    76	                                Icons.Outlined.StarOutline
    77	                            },
    78	                        contentDescription = "$i звезды",
    79	                        tint = MaterialTheme.colorScheme.primary,
    80	                    )
    81	                }
    82	            }
    83	        }
    84	    }
    85	}
    86	
    87	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
    88	@Preview(showBackground = true)
    89	@Composable
    90	private fun RatingPromptSectionPreview() {
    91	    SchoolQuizTheme {
    92	        RatingPromptSection(
    93	            ratingSubmissionState = RatingSubmissionState.Idle,
    94	            onSubmitRating = {},
    95	        )
    96	    }
    97	}

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
     2	
     3	import androidx.compose.foundation.layout.Arrangement
     4	import androidx.compose.foundation.layout.Row
     5	import androidx.compose.foundation.layout.fillMaxWidth
     6	import androidx.compose.foundation.layout.padding
     7	import androidx.compose.material3.MaterialTheme
     8	import androidx.compose.material3.Text
     9	import androidx.compose.runtime.Composable
    10	import androidx.compose.runtime.LaunchedEffect
    11	import androidx.compose.runtime.getValue
    12	import androidx.compose.runtime.mutableLongStateOf
    13	import androidx.compose.runtime.remember
    14	import androidx.compose.runtime.setValue
    15	import androidx.compose.ui.Alignment
    16	import androidx.compose.ui.Modifier
    17	import androidx.compose.ui.tooling.preview.Preview
    18	import androidx.compose.ui.unit.dp
    19	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    20	import kotlinx.coroutines.delay
    21	import kotlinx.coroutines.isActive
    22	
    23	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    24	@Composable
    25	fun QuestionProgressHeader(
    26	    indexInPool: Int,
    27	    totalInPool: Int,
    28	    deadlineMs: Long,
    29	    isPaused: Boolean,
    30	    isHard: Boolean,
    31	    onCrossClick: () -> Unit,
    32	    onTimeout: () -> Unit,
    33	    modifier: Modifier = Modifier,
    34	) {
    35	    var remainingMs by remember(indexInPool, deadlineMs) {
    36	        mutableLongStateOf((deadlineMs - System.currentTimeMillis()).coerceAtLeast(0))
    37	    }
    38	
    39	    LaunchedEffect(indexInPool, deadlineMs, isPaused) {
    40	        if (isPaused) return@LaunchedEffect
    41	        while (isActive) {
    42	            val remaining = (deadlineMs - System.currentTimeMillis()).coerceAtLeast(0)
    43	            remainingMs = remaining
    44	            if (remaining == 0L) {
    45	                onTimeout()
    46	                break
    47	            }
    48	            delay(100L)
    49	        }
    50	    }
    51	
    52	    val timerColor =
    53	        if (isHard) {
    54	            MaterialTheme.colorScheme.error
    55	        } else {
    56	            MaterialTheme.colorScheme.onSurface
    57	        }
    58	
    59	    Row(
    60	        modifier =
    61	            modifier
    62	                .fillMaxWidth()
    63	                .padding(horizontal = 16.dp, vertical = 8.dp),
    64	        verticalAlignment = Alignment.CenterVertically,
    65	        horizontalArrangement = Arrangement.SpaceBetween,
    66	    ) {
    67	        CrossButton(onClick = onCrossClick)
    68	        Text(
    69	            text = "${indexInPool + 1} / $totalInPool",
    70	            style = MaterialTheme.typography.titleMedium,
    71	        )
    72	        Text(
    73	            text = "${(remainingMs + 999L) / 1000L}s",
    74	            style = MaterialTheme.typography.titleMedium,
    75	            color = timerColor,
    76	        )
    77	    }
    78	}
    79	
    80	@Suppress("FunctionNaming", "UnusedPrivateMember", "MagicNumber", "ktlint:standard:function-naming")
    81	@Preview(showBackground = true)
    82	@Composable
    83	private fun QuestionProgressHeaderPreview() {
    84	    SchoolQuizTheme {
    85	        QuestionProgressHeader(
    86	            indexInPool = 2,
    87	            totalInPool = 10,
    88	            deadlineMs = System.currentTimeMillis() + 30_000L,
    89	            isPaused = false,
    90	            isHard = false,
    91	            onCrossClick = {},
    92	            onTimeout = {},
    93	        )
    94	    }
    95	}
    96	
    97	@Suppress("FunctionNaming", "UnusedPrivateMember", "MagicNumber", "ktlint:standard:function-naming")
    98	@Preview(showBackground = true)
    99	@Composable
   100	private fun QuestionProgressHeaderHardPreview() {
   101	    SchoolQuizTheme {
   102	        QuestionProgressHeader(
   103	            indexInPool = 0,
   104	            totalInPool = 5,
   105	            deadlineMs = System.currentTimeMillis() + 10_000L,
   106	            isPaused = false,
   107	            isHard = true,
   108	            onCrossClick = {},
   109	            onTimeout = {},
   110	        )
   111	    }
   112	}

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/Top3Section.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc 'nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/BlockingResumeDialog.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
     2	
     3	import androidx.compose.foundation.layout.Arrangement
     4	import androidx.compose.foundation.layout.Column
     5	import androidx.compose.foundation.layout.Spacer
     6	import androidx.compose.foundation.layout.fillMaxSize
     7	import androidx.compose.foundation.layout.height
     8	import androidx.compose.foundation.layout.padding
     9	import androidx.compose.material3.Button
    10	import androidx.compose.material3.Card
    11	import androidx.compose.material3.MaterialTheme
    12	import androidx.compose.material3.OutlinedButton
    13	import androidx.compose.material3.Text
    14	import androidx.compose.runtime.Composable
    15	import androidx.compose.ui.Alignment
    16	import androidx.compose.ui.Modifier
    17	import androidx.compose.ui.tooling.preview.Preview
    18	import androidx.compose.ui.unit.dp
    19	import androidx.compose.ui.window.Dialog
    20	import androidx.compose.ui.window.DialogProperties
    21	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    22	
    23	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    24	@Composable
    25	fun BlockingResumeDialog(
    26	    onContinue: () -> Unit,
    27	    onExit: () -> Unit,
    28	) {
    29	    Dialog(
    30	        onDismissRequest = {},
    31	        properties =
    32	            DialogProperties(
    33	                dismissOnBackPress = false,
    34	                dismissOnClickOutside = false,
    35	                usePlatformDefaultWidth = false,
    36	            ),
    37	    ) {
    38	        Card(modifier = Modifier.fillMaxSize()) {
    39	            Column(
    40	                modifier =
    41	                    Modifier
    42	                        .fillMaxSize()
    43	                        .padding(32.dp),
    44	                verticalArrangement = Arrangement.Center,
    45	                horizontalAlignment = Alignment.CenterHorizontally,
    46	            ) {
    47	                Text(
    48	                    text = "Продолжить прохождение?",
    49	                    style = MaterialTheme.typography.headlineSmall,
    50	                )
    51	                Spacer(modifier = Modifier.height(32.dp))
    52	                Button(onClick = onContinue) {
    53	                    Text("Продолжить")
    54	                }
    55	                Spacer(modifier = Modifier.height(16.dp))
    56	                OutlinedButton(onClick = onExit) {
    57	                    Text("Выйти")
    58	                }
    59	            }
    60	        }
    61	    }
    62	}
    63	
    64	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
    65	@Preview(showBackground = true)
    66	@Composable
    67	private fun BlockingResumeDialogPreview() {
    68	    SchoolQuizTheme {
    69	        BlockingResumeDialog(onContinue = {}, onExit = {})
    70	    }
    71	}

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
     2	
     3	import androidx.compose.foundation.layout.Arrangement
     4	import androidx.compose.foundation.layout.Column
     5	import androidx.compose.foundation.layout.Row
     6	import androidx.compose.foundation.layout.Spacer
     7	import androidx.compose.foundation.layout.fillMaxWidth
     8	import androidx.compose.foundation.layout.size
     9	import androidx.compose.foundation.layout.width
    10	import androidx.compose.material3.MaterialTheme
    11	import androidx.compose.material3.Text
    12	import androidx.compose.runtime.Composable
    13	import androidx.compose.ui.Alignment
    14	import androidx.compose.ui.Modifier
    15	import androidx.compose.ui.text.style.TextOverflow
    16	import androidx.compose.ui.tooling.preview.Preview
    17	import androidx.compose.ui.unit.dp
    18	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    19	import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
    20	
    21	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    22	@Composable
    23	fun Top3Section(
    24	    top3: List<TopParticipant>,
    25	    modifier: Modifier = Modifier,
    26	) {
    27	    Column(
    28	        modifier = modifier.fillMaxWidth(),
    29	        verticalArrangement = Arrangement.spacedBy(8.dp),
    30	    ) {
    31	        Text(
    32	            text = "Лучшие участники",
    33	            style = MaterialTheme.typography.titleMedium,
    34	        )
    35	        top3.forEach { participant ->
    36	            Row(
    37	                verticalAlignment = Alignment.CenterVertically,
    38	                modifier = Modifier.fillMaxWidth(),
    39	            ) {
    40	                ParticipantAvatar(
    41	                    avatarUrl = participant.avatarUrl,
    42	                    modifier = Modifier.size(40.dp),
    43	                )
    44	                Spacer(modifier = Modifier.width(8.dp))
    45	                Text(
    46	                    text = participant.nickname,
    47	                    modifier = Modifier.weight(1f),
    48	                    maxLines = 1,
    49	                    overflow = TextOverflow.Ellipsis,
    50	                    style = MaterialTheme.typography.bodyMedium,
    51	                )
    52	                Text(
    53	                    text = "${participant.percent}%",
    54	                    style = MaterialTheme.typography.bodyMedium,
    55	                    color = MaterialTheme.colorScheme.primary,
    56	                )
    57	            }
    58	        }
    59	    }
    60	}
    61	
    62	@Suppress("FunctionNaming", "UnusedPrivateMember", "MagicNumber", "ktlint:standard:function-naming")
    63	@Preview(showBackground = true)
    64	@Composable
    65	private fun Top3SectionPreview() {
    66	    SchoolQuizTheme {
    67	        Top3Section(
    68	            top3 =
    69	                listOf(
    70	                    TopParticipant("Alice", null, 95),
    71	                    TopParticipant("Bob", null, 88),
    72	                    TopParticipant("Carol", null, 72),
    73	                ),
    74	        )
    75	    }
    76	}

exec
/bin/bash -lc 'nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/CrossButton.kt' in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc 'nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ExitConfirmDialog.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
     2	
     3	import androidx.compose.material.icons.Icons
     4	import androidx.compose.material.icons.filled.Close
     5	import androidx.compose.material3.Icon
     6	import androidx.compose.material3.IconButton
     7	import androidx.compose.runtime.Composable
     8	import androidx.compose.ui.Modifier
     9	import androidx.compose.ui.tooling.preview.Preview
    10	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    11	
    12	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    13	@Composable
    14	fun CrossButton(
    15	    onClick: () -> Unit,
    16	    modifier: Modifier = Modifier,
    17	) {
    18	    IconButton(onClick = onClick, modifier = modifier) {
    19	        Icon(
    20	            imageVector = Icons.Default.Close,
    21	            contentDescription = "Выйти из урока",
    22	        )
    23	    }
    24	}
    25	
    26	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
    27	@Preview(showBackground = true)
    28	@Composable
    29	private fun CrossButtonPreview() {
    30	    SchoolQuizTheme { CrossButton(onClick = {}) }
    31	}

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
     2	
     3	import androidx.compose.material3.AlertDialog
     4	import androidx.compose.material3.Text
     5	import androidx.compose.material3.TextButton
     6	import androidx.compose.runtime.Composable
     7	import androidx.compose.ui.tooling.preview.Preview
     8	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
     9	
    10	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    11	@Composable
    12	fun ExitConfirmDialog(
    13	    onConfirm: () -> Unit,
    14	    onDismiss: () -> Unit,
    15	) {
    16	    AlertDialog(
    17	        onDismissRequest = onDismiss,
    18	        title = { Text("Уверены? Прогресс попытки потеряется") },
    19	        confirmButton = {
    20	            TextButton(onClick = onConfirm) {
    21	                Text("Выйти")
    22	            }
    23	        },
    24	        dismissButton = {
    25	            TextButton(onClick = onDismiss) {
    26	                Text("Отмена")
    27	            }
    28	        },
    29	    )
    30	}
    31	
    32	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
    33	@Preview(showBackground = true)
    34	@Composable
    35	private fun ExitConfirmDialogPreview() {
    36	    SchoolQuizTheme {
    37	        ExitConfirmDialog(onConfirm = {}, onDismiss = {})
    38	    }
    39	}

exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt | sed -n '1,420p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic
     2	
     3	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
     4	import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
     6	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
     7	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
     8	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
     9	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Score
    10	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Stars
    11	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
    12	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerDuration
    13	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswer
    14	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.allShownAnswersAre9
    15	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
    16	import kotlin.random.Random
    17	
    18	// ── Public pure functions ──────────────────────────────────────────────────
    19	
    20	/**
    21	 * Records [answer] score for current question and advances [indexInPool].
    22	 * When the last question is answered, returns Ready with indexInPool == playOrder.size (sentinel).
    23	 * Component checks this sentinel and calls CompleteAttemptUseCase.
    24	 */
    25	fun submitAnswer(state: RunnerState.Ready, answer: UserAnswer, nowMs: Long): RunnerState.Ready {
    26	    val currentQuestion = state.playOrder[state.indexInPool]
    27	    val score = evaluateAnswer(currentQuestion.content, answer)
    28	
    29	    val codeAnswerChars = state.codeAnswer.raw.toCharArray()
    30	    codeAnswerChars[currentQuestion.codeAnswerIndex] = ('0' + score.raw)
    31	    val newCodeAnswer = CodeAnswer(String(codeAnswerChars))
    32	
    33	    val nextIndex = state.indexInPool + 1
    34	    val newDeadlineMs = if (nextIndex < state.playOrder.size) {
    35	        val nextQuestion = state.playOrder[nextIndex]
    36	        val duration = computeTimer(nextQuestion.content, state.mode, TimerCoefficients.Default)
    37	        nowMs + duration.seconds * 1000L
    38	    } else {
    39	        state.deadlineMs
    40	    }
    41	
    42	    return state.copy(
    43	        indexInPool = nextIndex,
    44	        codeAnswer = newCodeAnswer,
    45	        deadlineMs = newDeadlineMs,
    46	        currentDraftAnswer = null,
    47	    )
    48	}
    49	
    50	/**
    51	 * Generates a deterministic random [UserAnswer] from [randomSeed] and delegates to [submitAnswer].
    52	 * Called on timer expiry or onStop.
    53	 */
    54	fun autoAnswerOnTimeout(state: RunnerState.Ready, randomSeed: Long, nowMs: Long): RunnerState.Ready {
    55	    val currentQuestion = state.playOrder[state.indexInPool]
    56	    val answer = generateRandomAnswer(currentQuestion.content, randomSeed)
    57	    return submitAnswer(state, answer, nowMs)
    58	}
    59	
    60	/**
    61	 * Evaluates [answer] against [content] and returns a [Score] in 1..9.
    62	 * Score formula: digit = round(correct_share × 8) + 1 (integer math, round-half-up).
    63	 */
    64	fun evaluateAnswer(content: QuestionContent, answer: UserAnswer): Score {
    65	    return when {
    66	        content is QuestionContent.SingleChoice && answer is UserAnswer.SingleChoiceAnswer -> {
    67	            val validIds = content.options.map { it.id }.toSet()
    68	            val selected = answer.selected?.takeIf { it in validIds }
    69	            if (selected == content.correctOptionId) Score(9) else Score(1)
    70	        }
    71	        content is QuestionContent.MultipleChoice && answer is UserAnswer.MultipleChoiceAnswer -> {
    72	            val validIds = content.options.map { it.id }.toSet()
    73	            val picked = answer.selected.intersect(validIds)
    74	            val correctPicked = picked.intersect(content.correctOptionIds).size
    75	            val wrongPicked = (picked - content.correctOptionIds).size
    76	            val missed = (content.correctOptionIds - picked).size
    77	            scoreDigit(correctPicked, correctPicked + wrongPicked + missed)
    78	        }
    79	        content is QuestionContent.Ordering && answer is UserAnswer.OrderingAnswer -> {
    80	            val correctOrder = content.items.map { it.id }
    81	            val isValidPerm = answer.order.size == correctOrder.size &&
    82	                answer.order.toSet() == correctOrder.toSet()
    83	            if (!isValidPerm) {
    84	                Score(1)
    85	            } else {
    86	                val matched = correctOrder.indices.count { i -> answer.order[i] == correctOrder[i] }
    87	                scoreDigit(matched, correctOrder.size)
    88	            }
    89	        }
    90	        content is QuestionContent.FillBlank && answer is UserAnswer.FillBlankAnswer -> {
    91	            val validCandidates = content.candidates.map { it.id }.toSet()
    92	            val filledCorrect = content.blanks.count { blank ->
    93	                val c = answer.filled[blank.id]
    94	                c != null && c in validCandidates && c == blank.correctCandidateId
    95	            }
    96	            scoreDigit(filledCorrect, content.blanks.size)
    97	        }
    98	        else -> Score(1)
    99	    }
   100	}
   101	
   102	/**
   103	 * Computes [Stars] for a single attempt.
   104	 * EASY: rawTenths = (percentScore × 20 + 50) / 100 → [0..20].
   105	 * HARD: rawTenths = 20 + (percentScore × 10 + 50) / 100 → [20..30].
   106	 * Integer math, round-half-up via +50 constant.
   107	 */
   108	fun computeStars(percentScore: PercentScore, mode: Difficulty): Stars {
   109	    val rawTenths = when (mode) {
   110	        Difficulty.EASY -> (percentScore.raw * 20 + 50) / 100
   111	        Difficulty.HARD -> 20 + (percentScore.raw * 10 + 50) / 100
   112	    }
   113	    return Stars(rawTenths)
   114	}
   115	
   116	/**
   117	 * Returns max [Stars] across all [attempts] for a lesson.
   118	 * Returns Stars(0) if list is empty.
   119	 */
   120	fun computeBestStars(attempts: List<Attempt>): Stars {
   121	    if (attempts.isEmpty()) return Stars(0)
   122	    return Stars(attempts.maxOf { computeStars(it.percentScore, it.mode).rawTenths })
   123	}
   124	
   125	/**
   126	 * Returns true if [attempts] contain at least one EASY attempt with allShownAnswersAre9.
   127	 * String-based, not percent-based. HARD attempts never unlock HARD.
   128	 */
   129	fun computeHardUnlocked(attempts: List<Attempt>): Boolean =
   130	    attempts.any { it.mode == Difficulty.EASY && it.codeAnswer.allShownAnswersAre9 }
   131	
   132	/**
   133	 * Computes per-question timer in seconds.
   134	 * Formula: max(5, round(charsCount × k)); +100 chars if image present.
   135	 */
   136	fun computeTimer(
   137	    content: QuestionContent,
   138	    mode: Difficulty,
   139	    coefficients: TimerCoefficients,
   140	): TimerDuration {
   141	    val charsCount = computeCharsCount(content)
   142	    val k = when (mode) {
   143	        Difficulty.EASY -> coefficients.kEasy
   144	        Difficulty.HARD -> coefficients.kHard
   145	    }
   146	    val seconds = maxOf(5, (charsCount * k + 0.5).toInt())
   147	    return TimerDuration(seconds)
   148	}
   149	
   150	/**
   151	 * Selects a random subset of [eligible] using [seed].
   152	 * Size = min([poolSize], eligible.size). Returned list order is shuffled (caller sorts for playOrder).
   153	 */
   154	fun selectSubset(
   155	    eligible: List<RunnerQuestion.Valid>,
   156	    poolSize: Int,
   157	    seed: Long,
   158	): List<RunnerQuestion.Valid> {
   159	    if (eligible.isEmpty()) return emptyList()
   160	    val count = minOf(poolSize, eligible.size)
   161	    return eligible.shuffled(Random(seed)).take(count)
   162	}
   163	
   164	/**
   165	 * Builds final [CodeAnswer] for an aborted attempt.
   166	 * Unanswered subset positions (indexInPool and beyond) → '1'.
   167	 * Out-of-subset positions remain '0'. Already answered positions stay as-is.
   168	 */
   169	fun buildCodeAnswerOnAbort(state: RunnerState.Ready): CodeAnswer {
   170	    val chars = state.codeAnswer.raw.toCharArray()
   171	    for (i in state.indexInPool until state.playOrder.size) {
   172	        chars[state.playOrder[i].codeAnswerIndex] = '1'
   173	    }
   174	    return CodeAnswer(String(chars))
   175	}
   176	
   177	// ── Internal helpers ───────────────────────────────────────────────────────
   178	
   179	/**
   180	 * Derives [PercentScore] from [codeAnswer] using integer division.
   181	 * Formula: sum((digit-1)*100/8) for non-zero digits / count of non-zero digits.
   182	 */
   183	internal fun computePercentScore(codeAnswer: CodeAnswer): PercentScore {
   184	    val nonZero = codeAnswer.raw.filter { it != '0' }
   185	    if (nonZero.isEmpty()) return PercentScore(0)
   186	    val sum = nonZero.sumOf { (it.digitToInt() - 1) * 100 / 8 }
   187	    return PercentScore(sum / nonZero.length)
   188	}
   189	
   190	// ── Private helpers ────────────────────────────────────────────────────────
   191	
   192	/**
   193	 * Score digit via integer round-half-up: (num * 8 + den/2) / den + 1.
   194	 * Returns Score(1) if denominator is zero.
   195	 */
   196	private fun scoreDigit(numerator: Int, denominator: Int): Score {
   197	    if (denominator == 0) return Score(1)
   198	    val digit = (numerator * 8 + denominator / 2) / denominator + 1
   199	    return Score(digit.coerceIn(1, 9))
   200	}
   201	
   202	/** Total character count for timer formula: text + option/item/candidate texts + optional +100. */
   203	private fun computeCharsCount(content: QuestionContent): Int {
   204	    val imageBonus = if (content.imageUrl != null) 100 else 0
   205	    val optionChars = when (content) {
   206	        is QuestionContent.SingleChoice -> content.options.sumOf { it.text.length }
   207	        is QuestionContent.MultipleChoice -> content.options.sumOf { it.text.length }
   208	        is QuestionContent.Ordering -> content.items.sumOf { it.text.length }
   209	        is QuestionContent.FillBlank -> content.candidates.sumOf { it.text.length }
   210	    }
   211	    return content.text.length + optionChars + imageBonus
   212	}
   213	
   214	/** Generates a deterministic random answer for [content] using [seed]. */
   215	private fun generateRandomAnswer(content: QuestionContent, seed: Long): UserAnswer {
   216	    val random = Random(seed)
   217	    return when (content) {
   218	        is QuestionContent.SingleChoice -> {
   219	            UserAnswer.SingleChoiceAnswer(content.options.random(random).id)
   220	        }
   221	        is QuestionContent.MultipleChoice -> {
   222	            val count = content.correctOptionIds.size
   223	            val selected = content.options.shuffled(random).take(count).map { it.id }.toSet()
   224	            UserAnswer.MultipleChoiceAnswer(selected)
   225	        }
   226	        is QuestionContent.Ordering -> {
   227	            UserAnswer.OrderingAnswer(content.items.shuffled(random).map { it.id })
   228	        }
   229	        is QuestionContent.FillBlank -> {
   230	            val filled = content.blanks.associate { blank ->
   231	                blank.id to content.candidates.random(random).id
   232	            }
   233	            UserAnswer.FillBlankAnswer(filled)
   234	        }
   235	    }
   236	}

exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt | sed -n '1,320p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case
     2	
     3	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
     4	import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser
     5	import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
     6	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
     7	import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
     8	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
     9	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.selectSubset
    10	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
    11	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
    12	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
    13	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
    14	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
    15	import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
    16	import kotlinx.coroutines.flow.first
    17	import kotlinx.datetime.Clock
    18	
    19	/**
    20	 * Builds [RunnerState.Ready] from a lesson and mode selection.
    21	 *
    22	 * Happy path: auth → lesson snapshot → question snapshot → parse + filter →
    23	 *   selectSubset → assign codeAnswerIndex → RunnerState.Ready.
    24	 *
    25	 * Failures (no throw): InitFailed(AuthRequired | LessonNotFound | EmptyPool | NoValidQuestions).
    26	 */
    27	class StartLessonAttemptUseCase(
    28	    private val questionRepository: QuestionRepository,
    29	    private val lessonRepository: LessonRepository,
    30	    private val parser: QuestionContentParser,
    31	    private val authRepository: AuthRepository,
    32	    private val clock: Clock,
    33	    private val randomSeedProvider: () -> Long,
    34	    private val timerCoefficients: TimerCoefficients = TimerCoefficients.Default,
    35	) {
    36	    suspend operator fun invoke(lessonId: LessonId, mode: Difficulty): RunnerState {
    37	        val userId = authRepository.currentUid()
    38	            ?: return RunnerState.InitFailed(InitFailureReason.AuthRequired)
    39	
    40	        val lesson = lessonRepository.getById(lessonId)
    41	            ?: return RunnerState.InitFailed(InitFailureReason.LessonNotFound)
    42	
    43	        val questions = questionRepository.observeByLesson(lessonId).first()
    44	        val activeQuestions = questions.filter { !it.archived }
    45	
    46	        val valids = activeQuestions.mapNotNull { q ->
    47	            parser.parse(q.payload).getOrNull()?.let { content ->
    48	                RunnerQuestion.Valid(
    49	                    sourceId = q.id,
    50	                    order = q.order,
    51	                    codeAnswerIndex = -1,
    52	                    content = content,
    53	                )
    54	            }
    55	        }
    56	
    57	        // Priority 1: active questions exist but all payloads are invalid
    58	        if (activeQuestions.isNotEmpty() && valids.isEmpty()) {
    59	            return RunnerState.InitFailed(InitFailureReason.NoValidQuestions)
    60	        }
    61	
    62	        // Priority 2: no eligible questions for selected difficulty
    63	        val eligible = valids.filter { it.content.difficulty == mode }
    64	        if (eligible.isEmpty()) {
    65	            return RunnerState.InitFailed(InitFailureReason.EmptyPool)
    66	        }
    67	
    68	        // Assign codeAnswerIndex per sorted eligible position (full pool, not subset)
    69	        val sorted = eligible.sortedWith(compareBy({ it.order }, { it.sourceId.value }))
    70	        val indexed = sorted.mapIndexed { idx, rq -> rq.copy(codeAnswerIndex = idx) }
    71	        val eligibleSize = indexed.size
    72	
    73	        // Random subset (deterministic via seed)
    74	        val seed = randomSeedProvider()
    75	        val subset = selectSubset(indexed, POOL_SIZE, seed)
    76	
    77	        // Stable play order: sort subset by (order, sourceId)
    78	        val playOrder = subset.sortedWith(compareBy({ it.order }, { it.sourceId.value }))
    79	
    80	        val initialCodeAnswer = CodeAnswer("0".repeat(eligibleSize))
    81	
    82	        val nowMs = clock.now().toEpochMilliseconds()
    83	        val firstDuration = computeTimer(playOrder.first().content, mode, timerCoefficients)
    84	        val deadlineMs = nowMs + firstDuration.seconds * 1000L
    85	
    86	        return RunnerState.Ready(
    87	            userId = userId,
    88	            lessonId = lessonId,
    89	            lessonVersion = lesson.version,
    90	            mode = mode,
    91	            playOrder = playOrder,
    92	            eligibleSize = eligibleSize,
    93	            indexInPool = 0,
    94	            codeAnswer = initialCodeAnswer,
    95	            deadlineMs = deadlineMs,
    96	            seed = seed,
    97	            currentDraftAnswer = null,
    98	            isPaused = false,
    99	        )
   100	    }
   101	
   102	    companion object {
   103	        const val POOL_SIZE = 20
   104	    }
   105	}

exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/SubmitLessonRatingUseCase.kt | sed -n '1,240p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case
     2	
     3	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
     4	import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
     6	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
     7	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
     8	import kotlinx.datetime.Clock
     9	
    10	/**
    11	 * Submits a lesson rating from the result screen.
    12	 *
    13	 * [userId] is passed explicitly (from completed attempt snapshot) to avoid auth race.
    14	 * [rating] must be in 1..3.
    15	 * lessonVersion fetched fresh from [lessonRepository] at submit time (for analytics).
    16	 *
    17	 * Returns [Result.failure] on error; local hasSubmitted flag not set on failure.
    18	 */
    19	class SubmitLessonRatingUseCase(
    20	    private val ratingRepository: LessonRatingRepository,
    21	    private val lessonRepository: LessonRepository,
    22	    private val clock: Clock,
    23	    private val ratingIdProvider: (userId: String, lessonId: LessonId) -> RatingId,
    24	) {
    25	    suspend operator fun invoke(
    26	        userId: String,
    27	        lessonId: LessonId,
    28	        rating: Int,
    29	    ): Result<Unit> {
    30	        if (rating !in 1..3) {
    31	            return Result.failure(IllegalArgumentException("rating must be in 1..3, got $rating"))
    32	        }
    33	
    34	        val lesson = lessonRepository.getById(lessonId)
    35	            ?: return Result.failure(IllegalStateException("Lesson not found: ${lessonId.value}"))
    36	
    37	        val ratingId = ratingIdProvider(userId, lessonId)
    38	        val nowMs = clock.now().toEpochMilliseconds()
    39	
    40	        val lessonRating = LessonRating(
    41	            id = ratingId,
    42	            userId = userId,
    43	            lessonId = lessonId,
    44	            lessonVersion = lesson.version,
    45	            rating = rating,
    46	            ratedAt = nowMs,
    47	        )
    48	
    49	        return ratingRepository.submit(lessonRating)
    50	    }
    51	}

exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/state/RunnerState.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt | sed -n '1,280p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt | sed -n '1,280p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state
     2	
     3	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
     4	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
     6	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
     7	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
     8	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
     9	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError
    10	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
    11	
    12	/**
    13	 * State machine for the lesson runner lifecycle.
    14	 *
    15	 * Transitions (happy path): Loading → Ready → Completed
    16	 * Error paths: Loading → InitFailed; Ready → Aborted | SaveFailed
    17	 */
    18	sealed interface RunnerState {
    19	
    20	    /** Initial state while StartLessonAttemptUseCase is loading questions. */
    21	    data object Loading : RunnerState
    22	
    23	    /** Terminal error: attempt was not started. */
    24	    data class InitFailed(val reason: InitFailureReason) : RunnerState
    25	
    26	    /**
    27	     * Active gameplay state. Immutable snapshot of questions fixed at attempt start.
    28	     *
    29	     * [indexInPool] sentinel value [playOrder.size] means last answer was submitted;
    30	     * component must call CompleteAttemptUseCase immediately.
    31	     *
    32	     * [eligibleSize] = length of codeAnswer = total questions of [mode] difficulty
    33	     * in this lesson (after archive filter + parse filter), NOT subset size.
    34	     */
    35	    data class Ready(
    36	        val userId: String,
    37	        val lessonId: LessonId,
    38	        val lessonVersion: Long,
    39	        val mode: Difficulty,
    40	        val playOrder: List<RunnerQuestion.Valid>,
    41	        val eligibleSize: Int,
    42	        val indexInPool: Int,
    43	        val codeAnswer: CodeAnswer,
    44	        val deadlineMs: Long,
    45	        val seed: Long,
    46	        val currentDraftAnswer: UserAnswerDraft?,
    47	        val isPaused: Boolean,
    48	    ) : RunnerState
    49	
    50	    /** Terminal success: attempt saved, optional rating prompt shown. */
    51	    data class Completed(
    52	        val attempt: Attempt,
    53	        val ratingPrompt: Boolean,
    54	    ) : RunnerState
    55	
    56	    /** Terminal: user exited mid-session; attempt saved with partial codeAnswer. */
    57	    data class Aborted(val attempt: Attempt) : RunnerState
    58	
    59	    /** Terminal: attempt constructed but Room write failed. */
    60	    data class SaveFailed(
    61	        val attempt: Attempt,
    62	        val error: SaveError,
    63	    ) : RunnerState
    64	}

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case
     2	
     3	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.buildCodeAnswerOnAbort
     4	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computePercentScore
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
     6	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
     7	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError
     8	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
     9	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
    10	import kotlinx.datetime.Clock
    11	
    12	/**
    13	 * Aborts an in-progress attempt and persists a partial record.
    14	 *
    15	 * userId from [state.userId] — no auth read.
    16	 * Unanswered subset positions → '1'; out-of-subset positions remain '0'.
    17	 *
    18	 * Returns [RunnerState.Aborted] or [RunnerState.SaveFailed] on Room IO error.
    19	 */
    20	class AbortAttemptUseCase(
    21	    private val attemptRepository: LessonAttemptRepository,
    22	    private val clock: Clock,
    23	    private val attemptIdProvider: () -> AttemptId,
    24	) {
    25	    suspend operator fun invoke(state: RunnerState.Ready): RunnerState {
    26	        val nowMs = clock.now().toEpochMilliseconds()
    27	        val finalCodeAnswer = buildCodeAnswerOnAbort(state)
    28	        val percentScore = computePercentScore(finalCodeAnswer)
    29	
    30	        val attempt = Attempt(
    31	            id = attemptIdProvider(),
    32	            userId = state.userId,
    33	            lessonId = state.lessonId,
    34	            lessonVersion = state.lessonVersion,
    35	            mode = state.mode,
    36	            completedAt = nowMs,
    37	            codeAnswer = finalCodeAnswer,
    38	            percentScore = percentScore,
    39	        )
    40	
    41	        val saveResult = attemptRepository.save(attempt)
    42	        if (saveResult.isFailure) {
    43	            val error = saveResult.exceptionOrNull()
    44	                ?.let { SaveError.IoFailure(it) }
    45	                ?: SaveError.UnknownError(Exception("Unknown save error"))
    46	            return RunnerState.SaveFailed(attempt = attempt, error = error)
    47	        }
    48	
    49	        return RunnerState.Aborted(attempt = attempt)
    50	    }
    51	}

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case
     2	
     3	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computePercentScore
     4	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
     6	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError
     7	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.allShownAnswersAre9
     8	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
     9	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
    10	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
    11	import kotlinx.coroutines.flow.first
    12	import kotlinx.datetime.Clock
    13	
    14	/**
    15	 * Finalizes a completed attempt and persists it to Room.
    16	 *
    17	 * userId taken from [state.userId] — no repeated auth read.
    18	 * ratingPrompt = codeAnswer.allShownAnswersAre9 AND !hasSubmittedRating.
    19	 *
    20	 * Returns [RunnerState.Completed] or [RunnerState.SaveFailed] on Room IO error.
    21	 */
    22	class CompleteAttemptUseCase(
    23	    private val attemptRepository: LessonAttemptRepository,
    24	    private val ratingRepository: LessonRatingRepository,
    25	    private val clock: Clock,
    26	    private val attemptIdProvider: () -> AttemptId,
    27	) {
    28	    suspend operator fun invoke(state: RunnerState.Ready): RunnerState {
    29	        val nowMs = clock.now().toEpochMilliseconds()
    30	        val percentScore = computePercentScore(state.codeAnswer)
    31	
    32	        val attempt = Attempt(
    33	            id = attemptIdProvider(),
    34	            userId = state.userId,
    35	            lessonId = state.lessonId,
    36	            lessonVersion = state.lessonVersion,
    37	            mode = state.mode,
    38	            completedAt = nowMs,
    39	            codeAnswer = state.codeAnswer,
    40	            percentScore = percentScore,
    41	        )
    42	
    43	        val saveResult = attemptRepository.save(attempt)
    44	        if (saveResult.isFailure) {
    45	            val error = saveResult.exceptionOrNull()
    46	                ?.let { SaveError.IoFailure(it) }
    47	                ?: SaveError.UnknownError(Exception("Unknown save error"))
    48	            return RunnerState.SaveFailed(attempt = attempt, error = error)
    49	        }
    50	
    51	        val ratingPrompt = state.codeAnswer.allShownAnswersAre9 &&
    52	            !ratingRepository.hasSubmitted(state.userId, state.lessonId).first()
    53	
    54	        return RunnerState.Completed(attempt = attempt, ratingPrompt = ratingPrompt)
    55	    }
    56	}

exec
/bin/bash -lc 'nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/CodeAnswer.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
     2	
     3	/**
     4	 * Encoded per-question score string.
     5	 * Length = eligibleQuestions(mode).size.
     6	 * Each char ∈ '0'..'9': '0' = not shown; '1'..'9' = shown with correctness digit.
     7	 */
     8	@JvmInline
     9	value class CodeAnswer(val raw: String) {
    10	    init {
    11	        require(raw.isNotEmpty()) { "CodeAnswer must not be empty" }
    12	        require(raw.all { it in '0'..'9' }) { "CodeAnswer chars must be in '0'..'9'" }
    13	    }
    14	}
    15	
    16	/**
    17	 * True if all shown positions (non-'0') are '9' AND at least one '9' exists.
    18	 * String-based — no Float precision issues.
    19	 */
    20	val CodeAnswer.allShownAnswersAre9: Boolean
    21	    get() = raw.all { it == '0' || it == '9' } && raw.any { it == '9' }

exec
/bin/bash -lc 'nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/PercentScore.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
     2	
     3	/**
     4	 * Derived integer percent [0..100].
     5	 * Formula: nonZero = codeAnswer.filter { it != '0' };
     6	 *   if empty → 0, else sum((digit-1)*100/8) / nonZero.size (integer division).
     7	 */
     8	@JvmInline
     9	value class PercentScore(val raw: Int) {
    10	    init {
    11	        require(raw in 0..100) { "PercentScore.raw must be in 0..100, got $raw" }
    12	    }
    13	}

exec
/bin/bash -lc 'nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/Stars.kt' in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc 'nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/RunnerQuestion.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
     2	
     3	/**
     4	 * Derived UI value for star display. Integer tenths [0..30] (3 stars × 10 parts).
     5	 * UI renders as rawTenths / 10f. Not persisted in Attempt.
     6	 */
     7	@JvmInline
     8	value class Stars(val rawTenths: Int) {
     9	    init {
    10	        require(rawTenths in 0..30) { "Stars.rawTenths must be in 0..30, got $rawTenths" }
    11	    }
    12	}

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
     2	
     3	import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
     4	import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
     5	
     6	/**
     7	 * Domain wrapper distinguishing questions with parseable vs unparseable payloads.
     8	 * Only [Valid] items enter the play pool; [Invalid] are filtered out at init.
     9	 */
    10	sealed interface RunnerQuestion {
    11	
    12	    val sourceId: QuestionId
    13	    val order: Int
    14	    val codeAnswerIndex: Int
    15	
    16	    data class Valid(
    17	        override val sourceId: QuestionId,
    18	        override val order: Int,
    19	        override val codeAnswerIndex: Int,
    20	        val content: QuestionContent,
    21	    ) : RunnerQuestion
    22	
    23	    data class Invalid(
    24	        override val sourceId: QuestionId,
    25	        override val order: Int,
    26	        override val codeAnswerIndex: Int,
    27	        val parseError: String,
    28	    ) : RunnerQuestion
    29	}

exec
/bin/bash -lc "nl -ba shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/KotlinxSerializationQuestionContentParser.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.core.question_schema
     2	
     3	import kotlinx.serialization.SerializationException
     4	import kotlinx.serialization.json.Json
     5	
     6	class KotlinxSerializationQuestionContentParser : QuestionContentParser {
     7	
     8	    private val json = Json { ignoreUnknownKeys = true }
     9	
    10	    override fun parse(payload: String): Result<QuestionContent> {
    11	        return try {
    12	            Result.success(json.decodeFromString<QuestionContent>(payload))
    13	        } catch (e: SerializationException) {
    14	            Result.failure(e)
    15	        } catch (e: IllegalArgumentException) {
    16	            Result.failure(e)
    17	        }
    18	    }
    19	}

exec
/bin/bash -lc "nl -ba shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/QuestionContent.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.core.question_schema
     2	
     3	import kotlinx.serialization.SerialName
     4	import kotlinx.serialization.Serializable
     5	
     6	/**
     7	 * Parsed question content from ADR-0003 JSON payload.
     8	 *
     9	 * Invariants are enforced in each subtype's init block.
    10	 * Domain question-runner imports only this sealed type and [QuestionContentParser].
    11	 */
    12	@Serializable
    13	sealed interface QuestionContent {
    14	
    15	    val id: String
    16	    val difficulty: Difficulty
    17	    val text: String
    18	    val imageUrl: String?
    19	
    20	    @Serializable
    21	    data class Option(val id: OptionId, val text: String)
    22	
    23	    @Serializable
    24	    data class OrderItem(val id: OptionId, val text: String)
    25	
    26	    @Serializable
    27	    data class Blank(val id: BlankId, val correctCandidateId: CandidateId)
    28	
    29	    @Serializable
    30	    data class Candidate(val id: CandidateId, val text: String)
    31	
    32	    /**
    33	     * Single-choice question. Exactly one correct answer.
    34	     * Invariant: options.size in 2..8.
    35	     */
    36	    @Serializable
    37	    @SerialName("SingleChoice")
    38	    data class SingleChoice(
    39	        override val id: String,
    40	        override val difficulty: Difficulty,
    41	        override val text: String,
    42	        override val imageUrl: String?,
    43	        val options: List<Option>,
    44	        val correctOptionId: OptionId,
    45	    ) : QuestionContent {
    46	        init {
    47	            require(id.isNotBlank()) { "SingleChoice.id must not be blank" }
    48	            require(text.isNotBlank()) { "SingleChoice.text must not be blank" }
    49	            require(options.size in 2..8) { "SingleChoice.options.size must be in 2..8, got ${options.size}" }
    50	            require(options.map { it.id }.toSet().size == options.size) { "SingleChoice.options must have unique ids" }
    51	            require(correctOptionId in options.map { it.id }) {
    52	                "SingleChoice.correctOptionId must be in options, got $correctOptionId"
    53	            }
    54	        }
    55	    }
    56	
    57	    /**
    58	     * Multiple-choice question. Two or more correct answers.
    59	     * Invariant: options.size in 2..8; correctOptionIds.size >= 2.
    60	     */
    61	    @Serializable
    62	    @SerialName("MultipleChoice")
    63	    data class MultipleChoice(
    64	        override val id: String,
    65	        override val difficulty: Difficulty,
    66	        override val text: String,
    67	        override val imageUrl: String?,
    68	        val options: List<Option>,
    69	        val correctOptionIds: Set<OptionId>,
    70	    ) : QuestionContent {
    71	        init {
    72	            require(id.isNotBlank()) { "MultipleChoice.id must not be blank" }
    73	            require(text.isNotBlank()) { "MultipleChoice.text must not be blank" }
    74	            require(options.size in 2..8) { "MultipleChoice.options.size must be in 2..8, got ${options.size}" }
    75	            require(options.map { it.id }.toSet().size == options.size) { "MultipleChoice.options must have unique ids" }
    76	            require(correctOptionIds.size >= 2) { "MultipleChoice.correctOptionIds.size must be >= 2, got ${correctOptionIds.size}" }
    77	            val optionIds = options.map { it.id }.toSet()
    78	            require(correctOptionIds.all { it in optionIds }) {
    79	                "MultipleChoice.correctOptionIds must all be in options, got missing: ${correctOptionIds - optionIds}"
    80	            }
    81	        }
    82	    }
    83	
    84	    /**
    85	     * Ordering question. Correct order = order of items in [items] list.
    86	     * Invariant: items.size in 2..8.
    87	     */
    88	    @Serializable
    89	    @SerialName("Ordering")
    90	    data class Ordering(
    91	        override val id: String,
    92	        override val difficulty: Difficulty,
    93	        override val text: String,
    94	        override val imageUrl: String?,
    95	        val items: List<OrderItem>,
    96	    ) : QuestionContent {
    97	        init {
    98	            require(id.isNotBlank()) { "Ordering.id must not be blank" }
    99	            require(text.isNotBlank()) { "Ordering.text must not be blank" }
   100	            require(items.size in 2..8) { "Ordering.items.size must be in 2..8, got ${items.size}" }
   101	        }
   102	    }
   103	
   104	    /**
   105	     * Fill-in-the-blank question. Text contains blank markers; candidates are word pool.
   106	     * Invariant: blanks.size in 1..3; candidates.size == 5 or 10.
   107	     */
   108	    @Serializable
   109	    @SerialName("FillBlank")
   110	    data class FillBlank(
   111	        override val id: String,
   112	        override val difficulty: Difficulty,
   113	        override val text: String,
   114	        override val imageUrl: String?,
   115	        val blanks: List<Blank>,
   116	        val candidates: List<Candidate>,
   117	    ) : QuestionContent {
   118	        init {
   119	            require(id.isNotBlank()) { "FillBlank.id must not be blank" }
   120	            require(text.isNotBlank()) { "FillBlank.text must not be blank" }
   121	            require(blanks.size in 1..3) { "FillBlank.blanks.size must be in 1..3, got ${blanks.size}" }
   122	            require(candidates.size == 5 || candidates.size == 10) {
   123	                "FillBlank.candidates.size must be 5 or 10, got ${candidates.size}"
   124	            }
   125	            require(candidates.map { it.id }.toSet().size == candidates.size) { "FillBlank.candidates must have unique ids" }
   126	            require(blanks.map { it.id }.toSet().size == blanks.size) { "FillBlank.blanks must have unique ids" }
   127	            val candidateIds = candidates.map { it.id }.toSet()
   128	            require(blanks.all { it.correctCandidateId in candidateIds }) {
   129	                "FillBlank.blanks.correctCandidateId must all be in candidates"
   130	            }
   131	        }
   132	    }
   133	}

exec
/bin/bash -lc 'nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/LessonAttemptRepository.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository
     2	
     3	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
     4	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeBestStars
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeHardUnlocked
     6	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
     7	import kotlinx.coroutines.flow.Flow
     8	import kotlinx.coroutines.flow.map
     9	
    10	/** Derived per-lesson progress used by lesson-list consumers (ADR-QS-16). */
    11	data class LessonAttemptStats(
    12	    val bestStarsRawTenths: Int,
    13	    val hardUnlocked: Boolean,
    14	)
    15	
    16	interface LessonAttemptRepository {
    17	
    18	    /**
    19	     * Persists [attempt] to Room. Called once at attempt completion or abort.
    20	     * Returns [Result.failure] on IO error; caller transitions to SaveFailed state.
    21	     */
    22	    suspend fun save(attempt: Attempt): Result<Unit>
    23	
    24	    /**
    25	     * Observes all attempts for [userId] + [lessonId], sorted by completedAt DESC.
    26	     */
    27	    fun observeByLesson(userId: String, lessonId: LessonId): Flow<List<Attempt>>
    28	
    29	    /**
    30	     * Observes all attempts for [userId] across all lessons.
    31	     */
    32	    fun observeAllByUser(userId: String): Flow<List<Attempt>>
    33	
    34	    /**
    35	     * Derived view: maps all user attempts to per-lessonId stats (bestStars + hardUnlocked).
    36	     * Default implementation delegates to [observeAllByUser]; override for performance if needed.
    37	     */
    38	    fun observeAllStatsByUser(userId: String): Flow<Map<LessonId, LessonAttemptStats>> =
    39	        observeAllByUser(userId).map { attempts ->
    40	            attempts.groupBy { it.lessonId }.mapValues { (_, lessonAttempts) ->
    41	                LessonAttemptStats(
    42	                    bestStarsRawTenths = computeBestStars(lessonAttempts).rawTenths,
    43	                    hardUnlocked = computeHardUnlocked(lessonAttempts),
    44	                )
    45	            }
    46	        }
    47	}

exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonAttemptRepositoryImpl.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository
     2	
     3	import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptDao
     4	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper.toDomain
     6	import com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper.toEntity
     7	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
     8	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
     9	import kotlinx.coroutines.flow.Flow
    10	import kotlinx.coroutines.flow.map
    11	
    12	class LessonAttemptRepositoryImpl(
    13	    private val attemptDao: LessonAttemptDao,
    14	) : LessonAttemptRepository {
    15	
    16	    override suspend fun save(attempt: Attempt): Result<Unit> = runCatching {
    17	        val rowId = attemptDao.upsert(attempt.toEntity())
    18	        check(rowId > 0) { "upsert returned unexpected rowId: $rowId" }
    19	    }
    20	
    21	    override fun observeByLesson(userId: String, lessonId: LessonId): Flow<List<Attempt>> =
    22	        attemptDao.observeByLesson(userId, lessonId.value)
    23	            .map { list -> list.map { it.toDomain() } }
    24	
    25	    override fun observeAllByUser(userId: String): Flow<List<Attempt>> =
    26	        attemptDao.observeAllByUser(userId)
    27	            .map { list -> list.map { it.toDomain() } }
    28	}

exec
/bin/bash -lc 'nl -ba shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonAttemptMapper.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper
     2	
     3	import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptEntity
     4	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
     5	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
     6	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
     7	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
     8	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
     9	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
    10	
    11	fun Attempt.toEntity(): LessonAttemptEntity = LessonAttemptEntity(
    12	    attemptId = id.value,
    13	    userId = userId,
    14	    lessonId = lessonId.value,
    15	    lessonVersion = lessonVersion,
    16	    isHard = if (mode == Difficulty.HARD) 1 else 0,
    17	    codeAnswer = codeAnswer.raw,
    18	    percentScore = percentScore.raw,
    19	    completedAt = completedAt,
    20	)
    21	
    22	fun LessonAttemptEntity.toDomain(): Attempt = Attempt(
    23	    id = AttemptId(attemptId),
    24	    userId = userId,
    25	    lessonId = LessonId(lessonId),
    26	    lessonVersion = lessonVersion,
    27	    mode = if (isHard != 0) Difficulty.HARD else Difficulty.EASY,
    28	    completedAt = completedAt,
    29	    codeAnswer = CodeAnswer(codeAnswer),
    30	    percentScore = PercentScore(percentScore),
    31	)

exec
/bin/bash -lc 'nl -ba shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptDao.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.core.persistence
     2	
     3	import androidx.room.Dao
     4	import androidx.room.Insert
     5	import androidx.room.OnConflictStrategy
     6	import androidx.room.Query
     7	import kotlinx.coroutines.flow.Flow
     8	
     9	@Dao
    10	interface LessonAttemptDao {
    11	
    12	    @Insert(onConflict = OnConflictStrategy.REPLACE)
    13	    suspend fun upsert(entity: LessonAttemptEntity): Long
    14	
    15	    @Query("SELECT * FROM lesson_attempts WHERE user_id = :userId AND lesson_id = :lessonId")
    16	    fun observeByLesson(userId: String, lessonId: String): Flow<List<LessonAttemptEntity>>
    17	
    18	    @Query("SELECT * FROM lesson_attempts WHERE user_id = :userId")
    19	    fun observeAllByUser(userId: String): Flow<List<LessonAttemptEntity>>
    20	}

exec
/bin/bash -lc 'nl -ba shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptEntity.kt' in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonRatingRepositoryImpl.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.core.persistence
     2	
     3	import androidx.room.ColumnInfo
     4	import androidx.room.Entity
     5	import androidx.room.Index
     6	import androidx.room.PrimaryKey
     7	
     8	@Entity(
     9	    tableName = "lesson_attempts",
    10	    indices = [
    11	        Index(value = ["user_id"], name = "idx_lesson_attempts_user_id"),
    12	        Index(value = ["lesson_id"], name = "idx_lesson_attempts_lesson_id"),
    13	    ],
    14	)
    15	data class LessonAttemptEntity(
    16	    @PrimaryKey
    17	    @ColumnInfo(name = "attempt_id")
    18	    val attemptId: String,
    19	    @ColumnInfo(name = "user_id") val userId: String,
    20	    @ColumnInfo(name = "lesson_id") val lessonId: String,
    21	    @ColumnInfo(name = "lesson_version") val lessonVersion: Long,
    22	    @ColumnInfo(name = "is_hard") val isHard: Int,
    23	    @ColumnInfo(name = "code_answer") val codeAnswer: String,
    24	    @ColumnInfo(name = "percent_score") val percentScore: Int,
    25	    @ColumnInfo(name = "completed_at") val completedAt: Long,
    26	)

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository
     2	
     3	import com.tpov.schoolquiz.shared.core.persistence.LessonRatingLocalDao
     4	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper.toEntity
     6	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
     7	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
     8	import kotlinx.coroutines.flow.Flow
     9	
    10	class LessonRatingRepositoryImpl(
    11	    private val ratingLocalDao: LessonRatingLocalDao,
    12	) : LessonRatingRepository {
    13	
    14	    override suspend fun submit(rating: LessonRating): Result<Unit> = runCatching {
    15	        val rowId = ratingLocalDao.upsert(rating.toEntity())
    16	        check(rowId > 0) { "upsert returned unexpected rowId: $rowId" }
    17	    }
    18	
    19	    override fun hasSubmitted(userId: String, lessonId: LessonId): Flow<Boolean> =
    20	        ratingLocalDao.hasSubmitted(userId, lessonId.value)
    21	}

exec
/bin/bash -lc "sed -n '650,760p' docs/features/lesson-runner/0-spec.md" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
### Repository Interfaces (фиксируется явно для Phase 3.8)

```kotlin
// shared/feature/lesson-runner/domain/src/commonMain/.../repository/LessonAttemptRepository.kt
interface LessonAttemptRepository {
    suspend fun save(attempt: Attempt): Result<Unit>
    fun observeByLesson(userId: String, lessonId: LessonId): Flow<List<Attempt>>
    fun observeAllByUser(userId: String): Flow<List<Attempt>>
}

// shared/feature/lesson-runner/domain/.../repository/LessonRatingRepository.kt
interface LessonRatingRepository {
    suspend fun submit(rating: LessonRating): Result<Unit>
    fun hasSubmitted(userId: String, lessonId: LessonId): Flow<Boolean>
    // Implementation: Room query на `lesson_rating_submitted_local` PK (userId, lessonId).
}

// shared/core/question-schema/.../parser/QuestionContentParser.kt
// Импортируется domain-ом; реализация (kotlinx.serialization) в data source set question-schema
interface QuestionContentParser {
    fun parse(payload: String): Result<QuestionContent>
}

// Existing dependencies используются:
// - shared/feature/app-shell/domain/.../AuthRepository.kt: suspend fun currentUid(): String?
//   StartLessonAttemptUseCase: если null → InitFailed(AuthRequired)
// - shared/feature/lesson/domain/.../LessonRepository.kt: suspend fun getById(LessonId): Lesson? (existing :26)
//   StartLessonAttemptUseCase использует suspend getById для snapshot lesson.version. Если null → InitFailed(LessonNotFound).
//   Result screen использует suspend getById ещё раз перед показом для свежего top3 / averageRating.
// - shared/feature/question/domain/.../QuestionRepository.kt: fun observeByLesson(LessonId): Flow<List<Question>>
//   Используется для одноразового read через .first() в StartLessonAttemptUseCase (snapshot вопросов).
```

### Use Cases (фиксируется явно)

```kotlin
// shared/feature/lesson-runner/domain/src/commonMain/.../use_case/

class StartLessonAttemptUseCase(
    private val questionRepository: QuestionRepository,
    private val lessonRepository: LessonRepository,
    private val parser: QuestionContentParser,
    private val authRepository: AuthRepository,
    private val clock: Clock,                  // injectable для тестируемости
    private val randomSeedProvider: () -> Long, // injectable для tests
) {
    suspend operator fun invoke(lessonId: LessonId, mode: Difficulty): RunnerState
    // returns Ready, InitFailed(EmptyPool), InitFailed(NoValidQuestions),
    //         InitFailed(LessonNotFound) если LessonRepository.getById вернул null,
    //         InitFailed(AuthRequired) если authRepository.currentUid() == null.
    // НЕ throws; все errors через RunnerState.InitFailed states.
}

class CompleteAttemptUseCase(
    private val attemptRepository: LessonAttemptRepository,
    private val ratingRepository: LessonRatingRepository,
    private val clock: Clock,
    private val attemptIdProvider: () -> AttemptId,  // injectable для deterministic tests
) {
    // userId берётся из state.userId (snapshot на старте), НЕ повторно читается из AuthRepository.
    suspend operator fun invoke(state: RunnerState.Ready): RunnerState
    // returns Completed(attempt, ratingPrompt) or SaveFailed(attempt, error)
    // ratingPrompt = state.codeAnswer.allShownAnswersAre9 && !ratingRepository.hasSubmitted(state.userId, state.lessonId).first()
}

class AbortAttemptUseCase(
    private val attemptRepository: LessonAttemptRepository,
    private val clock: Clock,
    private val attemptIdProvider: () -> AttemptId,
) {
    // userId из state.userId; auth read не выполняется.
    suspend operator fun invoke(state: RunnerState.Ready): RunnerState
    // fills unanswered subset positions with '1', out-of-subset already '0'; saves
    // returns Aborted(attempt) or SaveFailed(attempt, error)
}

class SubmitLessonRatingUseCase(
    private val ratingRepository: LessonRatingRepository,
    private val lessonRepository: LessonRepository,
    private val clock: Clock,
    private val ratingIdProvider: (userId: String, lessonId: LessonId) -> RatingId,
) {
    // userId берётся как explicit param — обычно `state.userId` из завершённой попытки (передаётся
    // result screen-ом). Это избегает race condition с auth read в момент тапа кнопки rating.
    suspend operator fun invoke(userId: String, lessonId: LessonId, rating: Int): Result<Unit>
}

// Pure functions in shared/feature/lesson-runner/domain/.../logic/ (sync, no suspend, no Flow).
// Возвращают только RunnerState.Ready для not-final transitions; CompleteAttemptUseCase invoked
// imperative-shell-side когда state.indexInPool == state.playOrder.size - 1 (= был последний).

// `submitAnswer` записывает score за текущий вопрос и продвигает indexInPool.
// Если был последний вопрос → возвращает Ready с признаком complete (indexInPool == playOrder.size).
// Component/imperative shell проверяет этот признак и вызывает CompleteAttemptUseCase.
fun submitAnswer(state: RunnerState.Ready, answer: UserAnswer, nowMs: Long): RunnerState.Ready

// Auto-random: генерирует UserAnswer и делегирует submitAnswer.
fun autoAnswerOnTimeout(state: RunnerState.Ready, randomSeed: Long, nowMs: Long): RunnerState.Ready

fun evaluateAnswer(content: QuestionContent, answer: UserAnswer): Score  // returns Score(1..9)
fun computeStars(percentScore: PercentScore, mode: Difficulty): Stars
fun computeBestStars(attempts: List<Attempt>): Stars
fun computeHardUnlocked(attempts: List<Attempt>): Boolean  // string-based via codeAnswer.allShownAnswersAre9
fun computeTimer(content: QuestionContent, mode: Difficulty, coefficients: TimerCoefficients): TimerDuration
fun selectSubset(eligible: List<RunnerQuestion.Valid>, poolSize: Int, seed: Long): List<RunnerQuestion.Valid>
fun buildCodeAnswerOnAbort(state: RunnerState.Ready): CodeAnswer  // unanswered subset → '1'

// Helper extension on CodeAnswer:
val CodeAnswer.allShownAnswersAre9: Boolean
    get() = raw.all { it == '0' || it == '9' } && raw.any { it == '9' }
```

exec
/bin/bash -lc "sed -n '762,920p' docs/features/lesson-runner/0-spec.md" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
### State / Decision Rules

См. State Matrix (Matrix 1-8) ниже.

### Error / Recovery Rules

1. **Empty pool** (`eligibleQuestions(mode).isEmpty()`) → `RunnerState.InitFailed(EmptyPool)` → empty state в UI с кнопкой Назад.
2. **All payloads invalid** (`pool.all { parser.parse(it.payload).isFailure }`) → `InitFailed(NoValidQuestions)` → empty state.
3. **Invalid payloads** исключаются на init (canonical pipeline шаг 3 в Business Rule 2). До прохождения не доходят. Старая wording «single invalid reached → score '1'» устарела (см. Resolved Decisions).
4. **Process kill** → попытка теряется, no save в Room.
5. **Configuration change** → state preserved через `instanceKeeper` (включая seed, draft answer, deadline).
6. **Сворачивание (`onStop`)** → текущий вопрос auto-random scored через `autoAnswerOnTimeout`, state.isPaused=true, таймер останавливается. На `onResume` — UI показывает блокирующий диалог.
7. **Save attempt failure** (Room IO error в `CompleteAttemptUseCase`/`AbortAttemptUseCase`) → `RunnerState.SaveFailed(attempt, error)`. UI показывает result screen с warning «Не удалось сохранить, попробуйте позже». No auto-retry в MVP. Attempt лост; пользователь может попробовать пройти урок ещё раз. — [DELEGATED]
8. **Submit rating failure** → `Result.failure` от `SubmitLessonRatingUseCase`. UI показывает toast «Не удалось отправить оценку». No retry. Local флаг НЕ установлен → user может попробовать снова. — [DELEGATED]
9. **Auth uid null** в момент start/save → `Result.failure(AuthRequired)`. UI должен закрыть runner и направить на login (это infrastructure concern; не должно происходить с anonymous Auth, но guard на всякий случай).

### Domain Test Scenarios (для Walking Skeleton, Phase 3.8)

#### Score formula tests

1. GIVEN SingleChoice с `correctOptionId=A`, options=[A,B,C,D] WHEN `selected=A` THEN `digit = '9'`
2. GIVEN SingleChoice с `correctOptionId=A` WHEN `selected=B` THEN `digit = '1'`
3. GIVEN MultipleChoice с `correctOptionIds={A,B,C}` (3 верных), 5 options всего WHEN `selected={A,B}` (2 верных, 0 wrong, 1 missed) THEN `correct_share = 2/(2+0+1) = 0.667` → `digit = round(0.667*8)+1 = round(5.33+1) = 6`
4. GIVEN MultipleChoice WHEN `selected={A,B,C}` (все верных) THEN `digit = '9'`
5. GIVEN MultipleChoice с `correctOptionIds={A,B}` WHEN `selected={C,D}` (0 верных, 2 wrong, 2 missed) THEN `correct_share = 0/4 = 0` → `digit = '1'`
6. GIVEN MultipleChoice WHEN `selected={}` (ничего не выбрал) THEN `correct_share = 0` → `digit = '1'`
7. GIVEN Ordering с правильным порядком [A,B,C,D] WHEN `userOrder = [A,B,C,D]` THEN `digit = '9'`
8. GIVEN Ordering [A,B,C,D] WHEN `userOrder = [A,C,B,D]` (2 совпадений: A в [0], D в [3]) THEN `correct_share = 2/4 = 0.5` → `digit = '5'`
9. GIVEN Ordering [A,B,C,D] WHEN `userOrder = [D,C,B,A]` THEN `correct_share = 0/4 = 0` → `digit = '1'`
10. GIVEN FillBlank 3 blanks WHEN все 3 заполнены верно THEN `digit = '9'`
11. GIVEN FillBlank 3 blanks WHEN 1 верный THEN `correct_share = 1/3 ≈ 0.333` → `digit = round(0.333*8)+1 = round(2.667)+1 = 4`
12. GIVEN FillBlank 1 blank WHEN неверный THEN `digit = '1'`

#### CodeAnswer construction tests

13. GIVEN lesson с 5 EASY questions, EASY-попытка с pool size const = 20 WHEN сделана попытка THEN `codeAnswer.length == 5` и нет '0' (все 5 показаны)
14. GIVEN lesson с 50 EASY questions, EASY-попытка WHEN сделана попытка THEN `codeAnswer.length == 50`, ровно 20 цифр в `'1'..'9'`, остальные 30 = `'0'`
15. GIVEN lesson с 50 EASY + 50 HARD, EASY-попытка completed all 9 WHEN save attempt THEN `codeAnswer.length == 50` (только EASY), `mode == EASY`
16. GIVEN HARD-попытка для того же lesson THEN `codeAnswer.length == 50` (только HARD), `mode == HARD`

#### PercentScore tests

17. GIVEN codeAnswer = "9999" (4 digits, all '9') THEN `percentScore = 100`
18. GIVEN codeAnswer = "5555" THEN `percentScore = 50` (((5-1)/8)*100 = 50, average = 50)
19. GIVEN codeAnswer = "9050" (длина 4, два '9', два '0') THEN `percentScore = ((9-1)/8 + (5-1)/8) / 2 × 100 = (1.0 + 0.5) / 2 × 100 = 75`
20. GIVEN codeAnswer = "1111" (4 digits all '1' = 0%) THEN `percentScore = 0`

#### Stars formula tests

21. GIVEN EASY attempt с `percentScore=100` THEN `Stars(rawTenths=20)` (UI 2.0)
22. GIVEN EASY attempt с `percentScore=50` THEN `Stars(rawTenths=10)` (UI 1.0); вычисление: `(50*20+50)/100 = 10`
23. GIVEN EASY attempt с `percentScore=75` THEN `Stars(rawTenths=15)` (UI 1.5)
24. GIVEN EASY attempt с `percentScore=33` THEN `Stars(rawTenths=7)` (UI 0.7); вычисление: `(33*20+50)/100 = 710/100 = 7`
25. GIVEN EASY attempt с `percentScore=0` THEN `Stars(rawTenths=0)` (UI 0.0)
26. GIVEN HARD attempt с `percentScore=100` THEN `Stars(rawTenths=30)` (UI 3.0)
27. GIVEN HARD attempt с `percentScore=50` THEN `Stars(rawTenths=25)` (UI 2.5); вычисление: `20 + (50*10+50)/100 = 20+5 = 25`
28. GIVEN HARD attempt с `percentScore=80` THEN `Stars(rawTenths=28)` (UI 2.8)
29. GIVEN HARD attempt с `percentScore=0` THEN `Stars(rawTenths=20)` (UI 2.0; HARD floor)

#### bestStars and hardUnlocked tests

30. GIVEN no attempts для lessonId THEN `bestStars = Stars(0)`
31. GIVEN 3 attempts с rawTenths [10, 15, 20] THEN `bestStars = Stars(20)`
32. GIVEN attempts [Stars(15) EASY, Stars(25) HARD] THEN `bestStars = Stars(25)`
33. GIVEN no EASY-attempt с allShownAnswersAre9=true THEN `hardUnlocked = false`
34. GIVEN ≥1 EASY-attempt с `codeAnswer.allShownAnswersAre9 == true` THEN `hardUnlocked = true`
35. GIVEN EASY-attempt где один digit = '8' (близко к perfect, но не) THEN `hardUnlocked = false` (string-based: any digit != '0' && != '9' → not all 9s)
35a. GIVEN HARD-attempt существует (`Stars(rawTenths=20)` floor) WITHOUT EASY-attempt с `allShownAnswersAre9` THEN `hardUnlocked = false`. HARD attempt никогда не unlocks по правилу — даже если в данных оказалась анормальная ситуация (например через manual sync), guard report `false`.

#### Timer formula tests

36. GIVEN question с text length 100 + options total length 65 (totalChars=165) WHEN mode=EASY (k_easy=0.18) THEN `timer ≈ round(165 × 0.18) = 30`
37. GIVEN тот же вопрос WHEN mode=HARD (k_hard=0.12) THEN `timer ≈ round(165 × 0.12) = 20`
38. GIVEN HARD timer < EASY timer для same question (sanity check)
39. GIVEN короткий вопрос (totalChars=10) WHEN EASY THEN `timer == 5` (min floor `max(5, round(10*0.18)) = max(5, 2) = 5`)
39a. GIVEN вопрос с image присутствует, totalChars=200 WHEN EASY THEN `timer = round((200+100) × 0.18) = round(54) = 54`
39b. GIVEN вопрос без image, totalChars=200 WHEN EASY THEN `timer = round(200 × 0.18) = 36`

#### Pool / random subset tests

40. GIVEN eligibleQuestions.size = 5, pool const 20 WHEN start attempt THEN subset.size = 5 (все)
41. GIVEN eligibleQuestions.size = 30, pool const 20 WHEN start attempt THEN subset.size = 20, выборка псевдо-рандом по фиксированному seed
42. GIVEN тот же seed для двух start-ов с identical eligibleQuestions THEN тот же subset (полный детерминизм)
43. GIVEN seed=12345 vs seed=67890 для eligible.size=30 (записанный fixture) WHEN selectSubset called THEN результат отличается хотя бы 1 RunnerQuestion в playOrder (детерминированный pre-recorded fixture, НЕ probabilistic — обе seed values фиксированы в test)

#### Auto-answer tests

44. GIVEN SingleChoice с 4 options WHEN auto-random THEN selected = одна из 4 options (равная вероятность)
45. GIVEN MultipleChoice с 5 options, correctOptionIds.size=3 WHEN auto-random THEN selected = subset размера 3 (рандомный subset)
46. GIVEN Ordering 4 items WHEN auto-random THEN userOrder = рандомная permutation
47. GIVEN FillBlank 3 blanks, 5 candidates WHEN auto-random THEN каждый blank заполнен рандомным candidate (с возвратом)

#### Rating prompt visibility tests

48. GIVEN attempt с `codeAnswer.allShownAnswersAre9 == true` AND no submitted rating THEN `ratingPrompt = true`
49. GIVEN attempt с `codeAnswer.allShownAnswersAre9 == true` AND already submitted rating THEN `ratingPrompt = false`
49a. GIVEN `CodeAnswer("9090")` THEN `allShownAnswersAre9 == true` (показанные = '9', не показанные = '0' — допустимо)
49b. GIVEN `CodeAnswer("0000")` THEN `allShownAnswersAre9 == false` (нет ни одного '9' — guard `any { '9' }`)
49c. GIVEN `CodeAnswer("9908")` THEN `allShownAnswersAre9 == false` (digit '8' нарушает условие all '0' or '9')
50. GIVEN attempt с одной digit='5' (не perfect) THEN `ratingPrompt = false`
51. GIVEN attempt с все digits='1' THEN `ratingPrompt = false`

#### Save attempt tests

52. GIVEN complete attempt (последний вопрос отвечен) WHEN CompleteAttemptUseCase invoked THEN `LessonAttemptRepository.save` вызван 1 раз с правильным Attempt object
53. GIVEN exit-via-dialog после 3-го вопроса (lesson имел 20) WHEN AbortAttemptUseCase invoked THEN attempt сохранён с codeAnswer: 3 цифры реальные, 17 — '1' (для непоказанных в subset, но непоказанные out-of-subset = '0'). Точная семантика: показанные но непрогресированные = '1', не попавшие в subset = '0'.
54. GIVEN process kill simulated WHEN component destroyed без CompleteAttemptUseCase THEN no save (verified через repository call counter)

#### lessonVersion tests

55. GIVEN lesson.version=5 WHEN StartLessonAttemptUseCase WHEN attempt completed THEN `attempt.lessonVersion == 5`
56. GIVEN lesson.version меняется во время прохождения (sync обновил) WHEN attempt completed THEN `attempt.lessonVersion == 5` (старое, snapshot на старте)
57. GIVEN rating submitted WHEN lesson.version==7 THEN `rating.lessonVersion == 7`

#### Edge cases

58. GIVEN empty pool (filter difficulty → 0 questions) WHEN StartLessonAttemptUseCase THEN `RunnerState = InitFailed(EmptyPool)`
59. GIVEN pool with all invalid payloads WHEN parse all → all failures THEN `InitFailed(NoValidQuestions)`
60. GIVEN single invalid payload среди валидных WHEN StartLessonAttemptUseCase init THEN это вопрос отброшен на шаге 3 pipeline (canonical pipeline в Business Rule 2); пользователь его никогда не видит; eligibleQuestions содержит только Valid; codeAnswer длина = `eligibleSize` без него
61. GIVEN configuration change simulated (instanceKeeper restore) WHEN restored THEN RunnerState identical (same seed, same currentIndex, same codeAnswer, same deadline, same draft)
61a. GIVEN duplicate `Question.order` (два вопроса с order=5) WHEN sortedBy(order, id) THEN детерминирован порядок (по id ASC), нет collision в codeAnswer индексации
61b. GIVEN сворачивание + onResume + «Продолжить» WHEN restored THEN player видит вопрос с indexInPool=N+1, не N (предыдущий auto-random scored)
61c. GIVEN сворачивание + onResume + «Выйти» WHEN abort THEN attempt saved с unanswered subset=`'1'`, out-of-subset=`'0'`

#### Value object guard tests (T16)

62. GIVEN `Stars(rawTenths = 31)` THEN throws IllegalArgumentException (out of range)
63. GIVEN `Stars(rawTenths = -1)` THEN throws
64. GIVEN `PercentScore(101)` THEN throws
65. GIVEN `LessonRating(rating=4)` THEN throws (range 1..3)
66. GIVEN `LessonRating(rating=0)` THEN throws
67. GIVEN `CodeAnswer("")` THEN throws (empty)
68. GIVEN `CodeAnswer("12X45")` THEN throws (non-digit char)
69. GIVEN `Attempt` с `userId=""` THEN throws
70. GIVEN `Attempt` с `lessonVersion=0` THEN throws
71. GIVEN `Attempt` с `completedAt=-1` THEN throws

#### Failure semantics tests (T16)

72. GIVEN `LessonAttemptRepository.save` throws Room IO exception WHEN `CompleteAttemptUseCase` called THEN returns `RunnerState.SaveFailed(attempt, error)`; attempt НЕ персистится; нет повторной попытки сохранения
73. GIVEN `LessonRatingRepository.submit` throws WHEN `SubmitLessonRatingUseCase` THEN `Result.failure`; local флаг `hasSubmitted` остаётся false
74. GIVEN `authRepository.currentUid()` returns null WHEN `StartLessonAttemptUseCase` THEN returns `RunnerState.InitFailed(AuthRequired)`
74a. GIVEN `lessonRepository.getById(lessonId)` returns null WHEN `StartLessonAttemptUseCase` THEN returns `RunnerState.InitFailed(LessonNotFound)`

#### Subset / determinism tests (T16)

75. GIVEN seed=12345, eligibleSize=30, poolSize=20 WHEN `selectSubset` called twice with same seed THEN identical subset (same RunnerQuestions в том же порядке)
76. GIVEN seed=12345 vs seed=67890, eligibleSize=30 WHEN `selectSubset` called THEN при перебранных fixed seeds результат должен отличаться хотя бы 1 RunnerQuestion в `playOrder` (детерминированный pre-recorded fixture, не probabilistic).
77. GIVEN eligibleQuestions with duplicate orders [Q1(order=1, id="a"), Q2(order=2, id="b"), Q3(order=2, id="c"), Q4(order=3, id="d")] WHEN sortedBy(order, id) THEN порядок: Q1, Q2, Q3, Q4 (id "b" < "c") — детерминированно

#### State transitions tests (T16)

78. GIVEN `RunnerState.Loading` WHEN `StartLessonAttemptUseCase` succeeds THEN `Ready`
79. GIVEN `RunnerState.Loading` WHEN eligible empty THEN `InitFailed(EmptyPool)`
80. GIVEN `RunnerState.Ready` где `indexInPool == playOrder.size - 1` (последний) WHEN pure `submitAnswer` invoked THEN returns `Ready` с `indexInPool == playOrder.size` (sentinel «complete»). Component/imperative shell вызывает `CompleteAttemptUseCase` — тестируется отдельно (см. test 52).
81. GIVEN `RunnerState.Ready` (mid index) WHEN `submitAnswer` THEN `Ready` с `indexInPool+1`
82. GIVEN `RunnerState.Ready` WHEN `AbortAttemptUseCase` THEN `Aborted` или `SaveFailed`
~~83.~~ (исключён из domain тестов): `submitAnswer` accepts только `Ready` argument (compile-time signature guarantee). Кейс «Aborted state не принимает дальнейшие answers» проверяется в presentation layer integration test, не в domain unit tests.


exec
/bin/bash -lc "sed -n '946,1033p' docs/features/lesson-runner/0-spec.md" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
## State Matrix

### Matrix 1: Score 0-9 за один ответ по типу

| Тип | Полностью верно (digit=9) | Частично верно (2..8) | Полностью неверно / timeout (digit=1) |
|-----|---------------------------|------------------------|---------------------------------------|
| SingleChoice | `selected == correctOptionId` | N/A | `selected != correctOptionId` или selected=null |
| MultipleChoice | `picked == correctOptionIds && picked.size == correctOptionIds.size` | Jaccard formula | `correct_picked == 0` (никаких пересечений) |
| Ordering | все позиции совпали | `matched_positions / total × 8 + 1` | `matched_positions == 0` |
| FillBlank | все blanks верны | `filled_correct / total × 8 + 1` | ни один blank не угадан |

Не показанные в этой попытке (out of subset) → `'0'`.

### Matrix 2: Stars per attempt formula

| Mode | percentScore | rawTenths | UI display |
|------|-------------|-----------|------------|
| EASY | 0 | 0 | 0.0 |
| EASY | 50 | 10 | 1.0 |
| EASY | 75 | 15 | 1.5 |
| EASY | 100 | 20 | 2.0 |
| HARD | 0 | 20 | 2.0 |
| HARD | 50 | 25 | 2.5 |
| HARD | 80 | 28 | 2.8 |
| HARD | 100 | 30 | 3.0 |

Формулы (integer math): `EASY: rawTenths = (percentScore * 20 + 50) / 100`; `HARD: rawTenths = 20 + (percentScore * 10 + 50) / 100`. Round half up через `+ 50` (constant).

### Matrix 3: bestStars per lesson (max по своим попыткам)

| Состояние истории своих попыток | bestStars | hardUnlocked |
|---------------------------------|-----------|--------------|
| Нет попыток | Stars(0) | false |
| Все EASY попытки с percentScore=0 | Stars(0) | false |
| Хотя бы одна EASY попытка с percentScore > 0 без allShownAnswersAre9 | Stars(rawTenths ∈ [1..20]; near-perfect rounding может дать 20) | **false** |
| Хотя бы одна EASY с allShownAnswersAre9=true | Stars(rawTenths ≥ 20) | **true** |
| Хотя бы одна HARD попытка (всегда требует prior unlock) | Stars(rawTenths ≥ 20) | true (унаследовано из EASY perfect) |

`hardUnlocked` зависит **только от** наличия EASY-попытки с `allShownAnswersAre9`. HARD attempt ничего не unlocks (он сам возможен только когда уже unlocked).

### Matrix 4: Когда писать attempt в Room

Унифицированная семантика abort: **all unanswered questions внутри selected subset get `'1'`; out-of-subset (в eligibleQuestions, но не в subset) get `'0'`** (`'0'` уже инициализировано при создании codeAnswer). Mode совпадает с mode попытки.

| Событие | Записать? | Что в `codeAnswer` | mode |
|---------|-----------|---------------------|------|
| Полное прохождение (все subset позиции отвечены) | Да | scores `'1'..'9'` per subset position; `'0'` для out-of-subset | EASY/HARD (как у попытки) |
| Exit через onResume диалог «Выйти» | Да | отвеченные subset = `'1'..'9'`; неотвеченные subset = `'1'`; out-of-subset = `'0'` | EASY/HARD |
| Крестик во время прохождения → подтвердил «Уверен?» | Да | то же что Exit | EASY/HARD |
| Save IO error | Нет → `SaveFailed` state | (попытка не записана) | EASY/HARD |
| Process kill | Нет | (попытка теряется) | — |
| Configuration change (rotation) | Нет | (state preserved через instanceKeeper) | — |

### Matrix 5: Rating prompt visibility

| Условия | Показать? |
|---------|-----------|
| `attempt.codeAnswer.allShownAnswersAre9 == true` AND `!ratingsRepo.hasSubmitted(userId, lessonId).first()` | Да |
| `attempt.codeAnswer.allShownAnswersAre9 == true` AND `ratingsRepo.hasSubmitted(userId, lessonId).first()` | Нет |
| `attempt.allShownAnswersAre9 == false` | Нет |

### Matrix 6: Sworn-fold / onResume / abort

| Событие | Текущий вопрос | UI после возврата |
|---------|----------------|--------------------|
| onStop | auto-random scored, in-memory state updated | таймер остановлен |
| onResume | — | блокирующий диалог «Продолжить?» |
| Диалог «Продолжить» | — | следующий вопрос, новый таймер; FLAG_SECURE остаётся (если HARD) |
| Диалог «Выйти» | — | save attempt → возврат в `LessonListComponent` |
| Process kill (без onStop первым? OOM) | (state lost) | (запуск с нуля) |

### Matrix 7: Timer per вопрос

| Mode | Коэффициент | charsCount=100 (sec) | charsCount=165 (sec) | charsCount=300 (sec) |
|------|-------------|---------------------|----------------------|----------------------|
| EASY | k_easy ≈ 0.18 | 18 | 30 | 54 |
| HARD | k_hard ≈ 0.12 | 12 | 20 | 36 |

### Matrix 8: Pool selection

| eligibleQuestions(mode).size | pool (subset) size | codeAnswer length | non-zero positions |
|------------------------------|---------------------|-------------------|--------------------|
| 0 | — | — | InitFailed(EmptyPool) |
| 5 | 5 (все) | 5 | 5 |
| 20 | 20 | 20 | 20 |
| 50 | 20 (random subset) | 50 | 20 (другие 30 = '0') |
| 100 | 20 | 100 | 20 (другие 80 = '0') |


exec
/bin/bash -lc 'rg -n "sorted|DESC|observeByLesson|ORDER|top3|rating|LessonAttemptRepository|Rating" docs/features/lesson-runner/0-spec.md docs/features/lesson-runner/03-decisions.md' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
docs/features/lesson-runner/03-decisions.md:18:- `LessonId` — ключ entity для `Attempt.lessonId`, `LessonAttemptRepository`, `LessonRatingRepository`
docs/features/lesson-runner/03-decisions.md:29:Направление строго one-way: `lesson:domain` не импортирует `lesson-runner:domain`. `LessonId` — корневой идентификатор entity; без него `Attempt` и `LessonAttemptRepository` теряют typed identity. `LessonRepository.getById` — единственный путь получить `Lesson.version` snapshot на старте попытки (see `0-spec.md §21`: `lessonVersion = lesson.version` at attempt start).
docs/features/lesson-runner/03-decisions.md:62:- `QuestionRepository` — `StartLessonAttemptUseCase` получает questions через `observeByLesson(lessonId).first()`
docs/features/lesson-runner/03-decisions.md:168:Spec `§34`: `Lesson.top3: List<TopParticipant>`. Текущее расположение `TopParticipant`:
docs/features/lesson-runner/03-decisions.md:174:`lesson-runner:domain` уже импортирует `lesson:domain` (ADR-LR-01). Если `lesson:domain` добавит `Lesson.top3: List<TopParticipant>` — возникнет обратный import `lesson:domain → lesson-runner:domain`. Это **bidirectional coupling** — blocker по Invariant 3 (`docs/invariants.md:25`).
docs/features/lesson-runner/03-decisions.md:191:- `lesson:domain` — зависит от `:shared:core:leaderboard` → может использовать `TopParticipant` в `Lesson.top3`
docs/features/lesson-runner/03-decisions.md:199:**Вариант B — `Lesson.top3: List<String>` (JSON-serialized)**:
docs/features/lesson-runner/03-decisions.md:205:- (-) Дополнительный sync path: `Lesson` document и `top3` синхронизируются независимо → сложность sync infrastructure
docs/features/lesson-runner/03-decisions.md:206:- (-) Result screen должен делать два независимых fetch вместо одного (Lesson snapshot + top3 snapshot)
docs/features/lesson-runner/03-decisions.md:207:- (-) Cloud Function (out-of-scope) пишет top3 в Lesson document — разделение на отдельный repository не согласуется с server data model из spec §34
docs/features/lesson-runner/03-decisions.md:319:- Обновление `docs/features/quizzes-screen/03-decisions.md`: добавить ADR-QS-15 (push consumer side) и ADR-QS-16 (LessonAttemptRepository import)
docs/features/lesson-runner/03-decisions.md:435:**Context**: `LessonRunnerDomainModule` использует `get()` для `() -> AttemptId`, `() -> Long` (randomSeedProvider), `(String, LessonId) -> RatingId`. Koin binding на function types имеет type erasure риск на JVM (все `Function1<*, *>` конфликтуют при resolution). Три варианта:
docs/features/lesson-runner/03-decisions.md:444:Default implementations (`DefaultAttemptIdProvider`, `DefaultRandomSeedProvider`, `DefaultRatingIdProvider`) — `shared/feature/lesson-runner/data/src/androidMain/…/provider/`.
docs/features/lesson-runner/03-decisions.md:452:single<RatingIdProvider> { DefaultRatingIdProvider() }      // sha256("$userId:$lessonId")
docs/features/lesson-runner/03-decisions.md:497:## ADR-LR-11 — Компонент для lesson card с StarRating + HARD checkbox
docs/features/lesson-runner/03-decisions.md:500:**Context**: Spec AC #47-49 требует на карточке урока: `StarRating` + conditional `Checkbox`. `HierarchyItemCard` (`android/core/designsystem/.../HierarchyItemCard.kt:34`) не имеет Checkbox slot. Три варианта:
docs/features/lesson-runner/03-decisions.md:526:**Context**: Проект использует `value` как имя поля для value classes (`LessonId.value`, `QuestionId.value`, etc.). Walking Skeleton создал `AttemptId.raw` и `RatingId.raw` — inconsistency (Grounding Problem 10, `AttemptId.kt:4`, `RatingId.kt:4`).
docs/features/lesson-runner/03-decisions.md:528:**Decision**: Переименовать `AttemptId.raw → AttemptId.value` и `RatingId.raw → RatingId.value`.
docs/features/lesson-runner/03-decisions.md:538:- `RatingId.kt` — rename field
docs/features/lesson-runner/03-decisions.md:541:- `SubmitLessonRatingUseCase.kt` — `RatingId(raw = ...)` → `RatingId(value = ...)`
docs/features/lesson-runner/03-decisions.md:545:**Note**: `RunnerLogic.kt:29` использует `state.codeAnswer.raw` — поле `CodeAnswer.raw` НЕ переименовывается (это не ID-поле, это семантически "raw string content"). Переименовываются только identity value classes `AttemptId` и `RatingId`.
docs/features/lesson-runner/03-decisions.md:605:## ADR-LR-15 — Lesson rating fields naming и типы
docs/features/lesson-runner/03-decisions.md:608:**Context**: Spec `0-spec.md §34` использует `ratingCount: Int?` (nullable). Quest precedent: `Quest.averageRating: Float?`, `Quest.averageRatingCount: Int` (non-nullable). Inconsistency требует resolve.
docs/features/lesson-runner/03-decisions.md:611:- `Lesson.averageRating: Float?` — nullable (absent until first server aggregate)
docs/features/lesson-runner/03-decisions.md:612:- `Lesson.ratingCount: Int = 0` — **non-nullable**, default 0
docs/features/lesson-runner/03-decisions.md:613:- `Lesson.top3: List<TopParticipant> = emptyList()` — non-nullable, default empty
docs/features/lesson-runner/03-decisions.md:615:**Rationale**: (1) Consistency с `Quest.averageRatingCount: Int` (`shared/feature/quest/domain/.../model/Quest.kt:62,69`). (2) `ratingCount` всегда имеет смысловой default 0. Nullable Int требовал бы везде null-check без смысловой ценности. (3) `top3` empty list — natural default (нет участников). (4) Firebase backward compat: missing field defaults via DTO `@SerialName` + Kotlin defaults.
docs/features/lesson-runner/03-decisions.md:617:**LessonEntity column**: `ratingCount INTEGER NOT NULL DEFAULT 0` — `@ColumnInfo(defaultValue = "0")`.
docs/features/lesson-runner/03-decisions.md:620:- `ratingCount: Int?` — создаёт null-check noise в presentation. Spec nullable — опечатка vs user intent "нет оценок = 0". Отклонено.
docs/features/lesson-runner/03-decisions.md:624:Если Cloud Function записывает `ratingCount: null` в Firestore (например баг CF) — DTO mapper должен иметь `?: 0` default для `ratingCount`; без этого `LessonEntity.ratingCount NOT NULL` вызовет crash при десериализации. Митигация: DTO mapper добавляет `ratingCount = dto.ratingCount ?: 0` (defensive default). Если `averageRating: Float?` становится non-nullable позже — breaking change только в DTO/presentation layer, domain модель остаётся совместимой.
docs/features/lesson-runner/0-spec.md:73:#### Запись попытки и rating
docs/features/lesson-runner/0-spec.md:75:19. **Сохранение попытки только в Room** через `LessonAttemptRepository.save(attempt)`. **Один write per attempt** в момент: (а) полное прохождение (последний вопрос отвечен), (б) exit-via-dialog в `onResume`, (в) подтвержденный exit через крестик. **Без incremental save** во время прохождения — state только in-memory. — [USER DECIDED]
docs/features/lesson-runner/0-spec.md:90:22. **codeAnswer индексация**: позиция `i` в строке = индекс `i`-го вопроса в `eligibleQuestions(mode).sortedBy { (it.order, it.sourceId) }`, где `eligibleQuestions(mode)` строится pipeline:
docs/features/lesson-runner/0-spec.md:92:    questions = questionRepository.observeByLesson(lessonId).first()
docs/features/lesson-runner/0-spec.md:101:    sorted = eligible.sortedWith(compareBy({ it.order }, { it.sourceId.raw }))
docs/features/lesson-runner/0-spec.md:102:    indexed = sorted.mapIndexed { idx, rq -> rq.copy(codeAnswerIndex = idx) }
docs/features/lesson-runner/0-spec.md:123:25. **Звёзды** — domain value class `Stars(rawTenths: Int 0..30)`, что соответствует UI отображению 3 звёзд × 10 частей каждая. UI рендер через `StarRating` компонент (значение в Float = `rawTenths / 10f`) в `android/core/designsystem/components/`. Integer math в domain — нет Float precision issues. — [USER DECIDED]
docs/features/lesson-runner/0-spec.md:136:   - Bound `bestStars: Stars` (показывается через `StarRating(rating = bestStars.rawTenths / 10f)` — existing API в `android/core/designsystem/.../StarRating.kt`).
docs/features/lesson-runner/0-spec.md:148:   3. **Звёзды** (`StarRating` с этой попытки stars value).
docs/features/lesson-runner/0-spec.md:151:   6. **Топ-3** лучших участников этого урока (из `Lesson.top3` field, агрегируется на сервере). Каждый элемент: аватар (Coil URL — placeholder если не закеширован), nickname, percent. Если top3 пуст — секция скрыта.
docs/features/lesson-runner/0-spec.md:152:   7. **Кнопка «Завершить»** — если rating был выбран в опросе → вызов `SubmitLessonRatingUseCase.invoke(state.userId, lessonId, rating)`, сбрасывает FLAG_SECURE если был, возврат на список уроков (pop ChildStack до `LessonListComponent`).
docs/features/lesson-runner/0-spec.md:171:33. **Новая Firestore коллекция `lesson_ratings/{ratingId}`** (write-only с клиента; сервер агрегирует — отдельная задача):
docs/features/lesson-runner/0-spec.md:175:   - `rating: Int` (1, 2 или 3)
docs/features/lesson-runner/0-spec.md:182:   - `averageRating: Float?` — средняя оценка (1..3) по всем `lesson_ratings`. Сервер агрегирует.
docs/features/lesson-runner/0-spec.md:183:   - `ratingCount: Int?` — количество оценок.
docs/features/lesson-runner/0-spec.md:184:   - `top3: List<TopParticipant>` (size ≤ 3) — `{ nickname, avatarUrl, percent }`. Сервер агрегирует из `lesson_attempts` для текущей `lessonVersion`.
docs/features/lesson-runner/0-spec.md:187:   > **Amendment 2026-04-26 (design phase, user-approved)**: `Lesson.ratingCount` изменён с `Int?` на `Int = 0` (non-nullable, default 0) — align с Quest.averageRatingCount pattern (`shared/feature/quest/domain/.../model/Quest.kt:69`). Семантика: missing field = 0 (нет оценок), null более не используется. Resolution applied per `docs/features/lesson-runner/03-decisions.md ADR-LR-15`.
docs/features/lesson-runner/0-spec.md:193:   - `shared/feature/lesson-runner/data/` — Room adapters (`LessonAttemptDao`, `LessonAttemptEntity`, mappers), `LessonAttemptRepositoryImpl`, `LessonRatingRepositoryImpl`. Реализуется в phase-01 implementation.
docs/features/lesson-runner/0-spec.md:225:- Новые Room таблицы: `lesson_attempts`, `lesson_ratings_submitted_local` (compound PK `(userId, lessonId)`)
docs/features/lesson-runner/0-spec.md:226:- Расширение Firestore mapping: новые collections `lesson_attempts`, `lesson_ratings` + новые поля в `lessons` (`averageRating`, `ratingCount`, `top3`)
docs/features/lesson-runner/0-spec.md:236:- **Cascading sync infrastructure для `lesson_attempts`/`lesson_ratings`** — расширение `home-and-my-quests` cascade sync orchestrator под новые коллекции. Контракт фиксируется здесь, реализация — отдельная phase / задача. Без этого attempts/ratings будут жить только локально.
docs/features/lesson-runner/0-spec.md:237:- **Серверная агрегация** `Lesson.averageRating`, `Lesson.ratingCount`, `Lesson.top3` — Cloud Functions. Контракт фиксируется здесь, server work — отдельная задача.
docs/features/lesson-runner/0-spec.md:249:- **Logout cleanup для local lesson_attempts/ratings** — относится к общей logout-cleanup задаче (отдельной); фича лишь фиксирует требование чистить.
docs/features/lesson-runner/0-spec.md:270:| 14 | Save attempt | только в Room, один write в конце | через `LessonAttemptRepository`; sync — отдельно |
docs/features/lesson-runner/0-spec.md:272:| 16 | Stars шкала | `Stars(rawTenths: Int 0..30)` value class (3 звезды × 10 частей; integer math) | UI делит на 10 для `StarRating(rating = rawTenths/10f)` |
docs/features/lesson-runner/0-spec.md:277:| 21 | lessonVersion | в attempt и в rating | фиксируется на старте/submit |
docs/features/lesson-runner/0-spec.md:278:| 22 | Финальный экран | %, подпись, звёзды, статистика, опрос rating, top3, Завершить | конкретный layout per user request |
docs/features/lesson-runner/0-spec.md:279:| 23 | Rating prompt | После первого `codeAnswer.allShownAnswersAre9 == true` + ¬оценивал | один раз per (userId, lessonId) |
docs/features/lesson-runner/0-spec.md:280:| 24 | Lesson rating storage | `lesson_ratings/{ratingId}` collection + локальный флаг | сервер агрегирует |
docs/features/lesson-runner/0-spec.md:281:| 25 | Top-N | Top-3 в `Lesson.top3` field | агрегация на сервере |
docs/features/lesson-runner/0-spec.md:295:| Агрегация `Lesson.averageRating`, `ratingCount` | Нужен trigger на write `lesson_ratings` для пересчёта полей в `lessons/{lessonId}` | Cloud Function `onCreate(lesson_ratings)` пересчитывает avg + count по текущей `lessonVersion` | Без CF: rating оценки только локально пишутся, общественное avg не обновляется |
docs/features/lesson-runner/0-spec.md:296:| Агрегация `Lesson.top3` | Нужен trigger на write `lesson_attempts` для пересчёта top-3 для `lessonVersion` | Cloud Function `onCreate(lesson_attempts)` если новая percentScore входит в top-3 — обновляет field | Без CF: top3 всегда пуст, секция на финальном экране скрыта |
docs/features/lesson-runner/0-spec.md:297:| Sync subset аватарок пользователей | Нужны отдельные `users/{uid}` document с `avatarUrl` + способ select-fetch только тех, кто в top3 | Cloud Function или клиентский subset-fetch на основе nickname/uid из top3 entries | Без этого: nickname показывается, аватарки = placeholder |
docs/features/lesson-runner/0-spec.md:298:| Расширение cascade sync под `lesson_attempts` и `lesson_ratings` | Существующий orchestrator в `home-and-my-quests` работает по 6 уровням иерархии; новые коллекции — orthogonal к иерархии (per-user, не per-content-tree) | Дополнительный sync flow: `where userId == uid` + cursor `lastModifiedAt` | Без этого: attempts/ratings локальны, не sync между устройствами |
docs/features/lesson-runner/0-spec.md:311:3. **`StarRating` компонент** — `android/core/designsystem/.../components/StarRating.kt`. Задокументировать API: поддерживает ли fractional value (0.0..3.0 шаг 0.1) или только integer? Если только integer — design phase решит расширение или замену.
docs/features/lesson-runner/0-spec.md:320:6. **`QuestionRepository.observeByLesson(lessonId)`** — `shared/feature/question/domain/.../repository/QuestionRepository.kt:24`. Документировать полную сигнатуру + sort order (ожидается `order ASC`).
docs/features/lesson-runner/0-spec.md:328:10. **`AuthRepository.observeUid()` и `currentUid()`** — `shared/feature/app-shell/domain/.../AuthRepository.kt:31` и `:43` (упомянуты в `quizzes-screen` research). Документировать сигнатуру для использования в `LessonAttemptRepository` (write attempt с актуальным `userId`).
docs/features/lesson-runner/0-spec.md:369:21. **`Lesson` model полная сигнатура** — `shared/feature/lesson/domain/.../model/Lesson.kt`. Документировать существующие поля (`title`, `order`, `version`, `contentsVersion`, `lastModifiedAt`, `archived`). Новые поля `averageRating`, `ratingCount`, `top3` — это расширение domain (impacts: data layer mapper, Firestore mapping, sync). Research должен подтвердить что это безопасно (no breaking changes для existing observers).
docs/features/lesson-runner/0-spec.md:380:- Для каждой `LessonAttemptRepository` / `LessonRatingRepository` метод задокументировать ожидаемую сигнатуру согласно Walking Skeleton (Phase 3.8 output).
docs/features/lesson-runner/0-spec.md:387:  - `top3`/`leaderboard`/`top_participants` — есть ли существующие модели или это полностью новая концепция?
docs/features/lesson-runner/0-spec.md:398:   - User оценивает 1/2/3 целых звезды → submit rating → set local флаг.
docs/features/lesson-runner/0-spec.md:406:   - Result: процент 75, звёзды 1.5, нейтральная подпись «Урок завершён», без опроса rating (`allShownAnswersAre9 == false`).
docs/features/lesson-runner/0-spec.md:417:   - Без опроса rating (`allShownAnswersAre9 == false`).
docs/features/lesson-runner/0-spec.md:421:4. **Perfect HARD → 3.0 + первый rating**
docs/features/lesson-runner/0-spec.md:424:   - Result: 100, подпись «Сложные пройдены полностью», звёзды 3.0 (UI), опрос rating (первый perfect, не оценивал).
docs/features/lesson-runner/0-spec.md:425:   - User оценивает → submit rating с `lessonVersion` = текущая.
docs/features/lesson-runner/0-spec.md:468:    - Top-3 на финальном экране — закешированный snapshot из `Lesson.top3`. Аватарки — placeholder если не закеширована.
docs/features/lesson-runner/0-spec.md:469:    - Expected result: full attempt работает offline; результат корректен; top3 может быть outdated.
docs/features/lesson-runner/0-spec.md:481:    - Trigger: WorkManager runs cascade sync, обновляет `Lesson.top3` и `Lesson.averageRating` в Room.
docs/features/lesson-runner/0-spec.md:482:    - State changes: in-memory `RunnerState` snapshot вопросов фиксирован на старте, не меняется. Финальный экран result читает обновлённый `Lesson.top3` (свежий).
docs/features/lesson-runner/0-spec.md:489:    - State changes: `observeByLesson(lessonId)` отдаёт empty list → state `InitFailed(EmptyPool)`.
docs/features/lesson-runner/0-spec.md:493:14. **Rating prompt уже заполнен раньше**
docs/features/lesson-runner/0-spec.md:495:    - State changes: на финальном экране опрос rating НЕ показывается (даже несмотря на perfect).
docs/features/lesson-runner/0-spec.md:496:    - Expected result: подпись/звёзды/статистика/top3/Завершить — без rating prompt.
docs/features/lesson-runner/0-spec.md:504:- **RatingId** — `value class RatingId(val raw: String)`. Deterministic `sha256("$userId:$lessonId")`. Validated `raw.isNotBlank()`.
docs/features/lesson-runner/0-spec.md:517:- **LessonRating** — immutable: `(id: RatingId, userId: String, lessonId: LessonId, lessonVersion: Long, rating: Int (1..3), ratedAt: Long)`. Validated.
docs/features/lesson-runner/0-spec.md:525:    - `playOrder: List<RunnerQuestion.Valid>` — отсортированный subset в порядке показа (sortedBy `Question.order` ASC, ties broken by `Question.id`). Только `Valid`: invalid payloads уже отфильтрованы при init. (Sealed `RunnerQuestion` объявлен для будущих edge cases, но в playOrder только `.Valid`.)
docs/features/lesson-runner/0-spec.md:533:  - `Completed(attempt: Attempt, ratingPrompt: Boolean)` — финал, attempt сохранён
docs/features/lesson-runner/0-spec.md:560:  1. Загружает `List<Question>` через `QuestionRepository.observeByLesson(lessonId).first()`.
docs/features/lesson-runner/0-spec.md:587:    1. `questionRepository.observeByLesson(lessonId).first()` — snapshot list `Question`.
docs/features/lesson-runner/0-spec.md:591:    5. `.sortedWith(compareBy({ order }, { sourceId.raw }))` — стабильный порядок.
docs/features/lesson-runner/0-spec.md:596:4. **playOrder ordering**: `playOrder = subset.sortedBy(it.order then it.id)` (НЕ shuffled). Стабильный порядок показа. Дубликаты `Question.order` устраняются через secondary sort key `id`.
docs/features/lesson-runner/0-spec.md:618:11. **Perfect attempt detection** (для unlock и rating prompt): `attempt.allShownAnswersAre9 = codeAnswer.all { it == '0' || it == '9' }` AND `codeAnswer.any { it == '9' }`. Это **string-based**, НЕ percent-based — нет Float precision issues.
docs/features/lesson-runner/0-spec.md:626:16. **Rating prompt visibility** = `attempt.allShownAnswersAre9 && !ratingsRepo.hasSubmitted(userId, lessonId)`. **String-based**, не percent-based.
docs/features/lesson-runner/0-spec.md:627:17. **lessonVersion fixation**: `attempt.lessonVersion = lesson.version` на момент `StartLessonAttemptUseCase` invocation (snapshot когда state ещё `Loading`). Если `Lesson` Flow emit-ит обновление между UseCase invocation и завершением — игнорируется. `rating.lessonVersion = lesson.version` на момент `submit` (свежий read).
docs/features/lesson-runner/0-spec.md:628:18. **Attempt save = только в конце**: один `LessonAttemptRepository.save(attempt)` в:
docs/features/lesson-runner/0-spec.md:643:24. **Rating uniqueness — lifetime per (userId, lessonId)** (упрощено для consistency):
docs/features/lesson-runner/0-spec.md:644:    - Local Room PK: compound `(userId, lessonId)` в таблице `lesson_rating_submitted_local`. Один раз поставил → больше не предлагается.
docs/features/lesson-runner/0-spec.md:646:    - `lessonVersion` сохраняется в payload документа `lesson_ratings` для server-side analytics (понимать какая версия оценивалась), но НЕ участвует в uniqueness key.
docs/features/lesson-runner/0-spec.md:648:25. **lessonVersion в `LessonRating` payload** — analytics поле, не uniqueness key. Фиксируется на момент submit.
docs/features/lesson-runner/0-spec.md:653:// shared/feature/lesson-runner/domain/src/commonMain/.../repository/LessonAttemptRepository.kt
docs/features/lesson-runner/0-spec.md:654:interface LessonAttemptRepository {
docs/features/lesson-runner/0-spec.md:656:    fun observeByLesson(userId: String, lessonId: LessonId): Flow<List<Attempt>>
docs/features/lesson-runner/0-spec.md:660:// shared/feature/lesson-runner/domain/.../repository/LessonRatingRepository.kt
docs/features/lesson-runner/0-spec.md:661:interface LessonRatingRepository {
docs/features/lesson-runner/0-spec.md:662:    suspend fun submit(rating: LessonRating): Result<Unit>
docs/features/lesson-runner/0-spec.md:664:    // Implementation: Room query на `lesson_rating_submitted_local` PK (userId, lessonId).
docs/features/lesson-runner/0-spec.md:678://   Result screen использует suspend getById ещё раз перед показом для свежего top3 / averageRating.
docs/features/lesson-runner/0-spec.md:679:// - shared/feature/question/domain/.../QuestionRepository.kt: fun observeByLesson(LessonId): Flow<List<Question>>
docs/features/lesson-runner/0-spec.md:704:    private val attemptRepository: LessonAttemptRepository,
docs/features/lesson-runner/0-spec.md:705:    private val ratingRepository: LessonRatingRepository,
docs/features/lesson-runner/0-spec.md:711:    // returns Completed(attempt, ratingPrompt) or SaveFailed(attempt, error)
docs/features/lesson-runner/0-spec.md:712:    // ratingPrompt = state.codeAnswer.allShownAnswersAre9 && !ratingRepository.hasSubmitted(state.userId, state.lessonId).first()
docs/features/lesson-runner/0-spec.md:716:    private val attemptRepository: LessonAttemptRepository,
docs/features/lesson-runner/0-spec.md:726:class SubmitLessonRatingUseCase(
docs/features/lesson-runner/0-spec.md:727:    private val ratingRepository: LessonRatingRepository,
docs/features/lesson-runner/0-spec.md:730:    private val ratingIdProvider: (userId: String, lessonId: LessonId) -> RatingId,
docs/features/lesson-runner/0-spec.md:733:    // result screen-ом). Это избегает race condition с auth read в момент тапа кнопки rating.
docs/features/lesson-runner/0-spec.md:734:    suspend operator fun invoke(userId: String, lessonId: LessonId, rating: Int): Result<Unit>
docs/features/lesson-runner/0-spec.md:775:8. **Submit rating failure** → `Result.failure` от `SubmitLessonRatingUseCase`. UI показывает toast «Не удалось отправить оценку». No retry. Local флаг НЕ установлен → user может попробовать снова. — [DELEGATED]
docs/features/lesson-runner/0-spec.md:854:#### Rating prompt visibility tests
docs/features/lesson-runner/0-spec.md:856:48. GIVEN attempt с `codeAnswer.allShownAnswersAre9 == true` AND no submitted rating THEN `ratingPrompt = true`
docs/features/lesson-runner/0-spec.md:857:49. GIVEN attempt с `codeAnswer.allShownAnswersAre9 == true` AND already submitted rating THEN `ratingPrompt = false`
docs/features/lesson-runner/0-spec.md:861:50. GIVEN attempt с одной digit='5' (не perfect) THEN `ratingPrompt = false`
docs/features/lesson-runner/0-spec.md:862:51. GIVEN attempt с все digits='1' THEN `ratingPrompt = false`
docs/features/lesson-runner/0-spec.md:866:52. GIVEN complete attempt (последний вопрос отвечен) WHEN CompleteAttemptUseCase invoked THEN `LessonAttemptRepository.save` вызван 1 раз с правильным Attempt object
docs/features/lesson-runner/0-spec.md:874:57. GIVEN rating submitted WHEN lesson.version==7 THEN `rating.lessonVersion == 7`
docs/features/lesson-runner/0-spec.md:882:61a. GIVEN duplicate `Question.order` (два вопроса с order=5) WHEN sortedBy(order, id) THEN детерминирован порядок (по id ASC), нет collision в codeAnswer индексации
docs/features/lesson-runner/0-spec.md:891:65. GIVEN `LessonRating(rating=4)` THEN throws (range 1..3)
docs/features/lesson-runner/0-spec.md:892:66. GIVEN `LessonRating(rating=0)` THEN throws
docs/features/lesson-runner/0-spec.md:901:72. GIVEN `LessonAttemptRepository.save` throws Room IO exception WHEN `CompleteAttemptUseCase` called THEN returns `RunnerState.SaveFailed(attempt, error)`; attempt НЕ персистится; нет повторной попытки сохранения
docs/features/lesson-runner/0-spec.md:902:73. GIVEN `LessonRatingRepository.submit` throws WHEN `SubmitLessonRatingUseCase` THEN `Result.failure`; local флаг `hasSubmitted` остаётся false
docs/features/lesson-runner/0-spec.md:910:77. GIVEN eligibleQuestions with duplicate orders [Q1(order=1, id="a"), Q2(order=2, id="b"), Q3(order=2, id="c"), Q4(order=3, id="d")] WHEN sortedBy(order, id) THEN порядок: Q1, Q2, Q3, Q4 (id "b" < "c") — детерминированно
docs/features/lesson-runner/0-spec.md:940:| 16 | Local флаг «уже оценил» | Room таблица `lesson_rating_submitted_local` с compound PK `(userId, lessonId)`, поле `ratedAt: Long` | Compound PK защищает от double-rating одного user, но позволяет другому user оценить тот же lesson | Low |
docs/features/lesson-runner/0-spec.md:999:### Matrix 5: Rating prompt visibility
docs/features/lesson-runner/0-spec.md:1003:| `attempt.codeAnswer.allShownAnswersAre9 == true` AND `!ratingsRepo.hasSubmitted(userId, lessonId).first()` | Да |
docs/features/lesson-runner/0-spec.md:1004:| `attempt.codeAnswer.allShownAnswersAre9 == true` AND `ratingsRepo.hasSubmitted(userId, lessonId).first()` | Нет |
docs/features/lesson-runner/0-spec.md:1089:37. [ ] GIVEN complete attempt THEN `LessonAttemptRepository.save` called once с Attempt(lessonVersion из старта)
docs/features/lesson-runner/0-spec.md:1092:40. [ ] GIVEN no incremental save во время прохождения THEN `LessonAttemptRepository.save` вызывается ровно 1 раз per attempt
docs/features/lesson-runner/0-spec.md:1096:41. [ ] GIVEN attempt с `codeAnswer.allShownAnswersAre9 == true` AND ¬hasSubmittedRating THEN на result screen видно опрос «Оцените урок» (1/2/3 целых звезды)
docs/features/lesson-runner/0-spec.md:1097:42. [ ] GIVEN attempt с `codeAnswer.allShownAnswersAre9 == true` AND hasSubmittedRating THEN опрос НЕ виден
docs/features/lesson-runner/0-spec.md:1099:44. [ ] GIVEN пользователь оценил урок THEN local флаг `lesson_rating_submitted_local` установлен; LessonRatingRepository.submit вызван 1 раз с lessonVersion из текущего Lesson
docs/features/lesson-runner/0-spec.md:1100:45. [ ] GIVEN result screen с `Lesson.top3` непустой THEN отображается секция Топ-3 с аватарками, никами и %; пустой → секция скрыта. **Note: top3 — закешированный server snapshot из Lesson document; текущая попытка пользователя ещё не успела попасть в top3 (server CF не выполнился; sync ещё не подтянул).** Список покажет state ДО этой попытки.
docs/features/lesson-runner/0-spec.md:1101:46. [ ] GIVEN top3 entry с avatarUrl=null или Coil не смог загрузить (offline / no cache) THEN placeholder вместо аватарки
docs/features/lesson-runner/0-spec.md:1105:47. [ ] GIVEN карточка урока в `LessonListComponent` THEN отображается `StarRating(rating = bestStars.rawTenths / 10f)` (existing API из `android/core/designsystem/.../StarRating.kt:99` принимает `rating: Float?`)
docs/features/lesson-runner/0-spec.md:1114:52a. [ ] GIVEN `LessonAttemptRepository.save` throws Room IO exception WHEN `CompleteAttemptUseCase` THEN `RunnerState.SaveFailed(attempt, error)`; result screen показывается с warning «Не удалось сохранить»; нет автоматического retry
docs/features/lesson-runner/0-spec.md:1115:52b. [ ] GIVEN `LessonRatingRepository.submit` throws WHEN user тапает submit rating THEN toast «Не удалось отправить оценку»; local флаг hasSubmitted остаётся false; user может попробовать снова
docs/features/lesson-runner/0-spec.md:1134:- rating prompt (48-51)
docs/features/lesson-runner/0-spec.md:1157:| 5. Koin binding uniqueness | `LessonAttemptRepository`, `LessonRatingRepository`, etc. — один production binding per type | preserve — Koin manual DI |
docs/features/lesson-runner/0-spec.md:1171:- **Cascade sync infrastructure** — `home-and-my-quests` orchestrator pattern; новые collections (`lesson_attempts`, `lesson_ratings`) расширяются orthogonally к 6-уровневой иерархии (per-user, не per-tree). Это infrastructure work — отдельная задача.
docs/features/lesson-runner/0-spec.md:1204:| 6 | Subset порядок | **Resolved**: `playOrder = subset.sortedBy(order, id)`. Стабильный, не shuffled. |
docs/features/lesson-runner/0-spec.md:1210:| 12 | Top3 cached note | **Resolved**: AC #45 явно говорит что top3 — server snapshot, текущая попытка не там. |
docs/features/lesson-runner/0-spec.md:1211:| 13 | Rating uniqueness | **Resolved**: lifetime per `(userId, lessonId)`. Local Room PK compound `(userId, lessonId)`; remote ID deterministic `sha256("$userId:$lessonId")` (БЕЗ lessonVersion в ключе). lessonVersion сохраняется в payload как analytics, не uniqueness key. |
docs/features/lesson-runner/0-spec.md:1221:2. **DI lambda bindings** в `LessonRunnerDomainModule.kt` — `attemptIdProvider: () -> AttemptId`, `ratingIdProvider: (userId, lessonId) -> RatingId`, `currentTimeMillisProvider: () -> Long`. Koin не поддерживает function types через обычный `get()` без named qualifier. Phase-01 backend-dev решает strategy: либо `single<AttemptIdProvider> { ... }` wrapper interface, либо `parametersOf` injection. Сейчас в Stage A skeleton оставлено как TODO.
docs/features/lesson-runner/0-spec.md:1230:- [ ] `StarRating.kt` API — поддерживает ли fractional value (0.0..3.0 шаг 0.1)? Если нет — расширить (design phase task).
docs/features/lesson-runner/0-spec.md:1233:- [ ] Cascade sync orchestrator API — как добавить новые коллекции `lesson_attempts` и `lesson_ratings` (для extension в phase реализации; не блокер для этой spec).
docs/features/lesson-runner/0-spec.md:1234:- [ ] Lesson Room migration plan — добавление полей `averageRating: Float?`, `ratingCount: Int?`, `top3: List<TopParticipant>` без breaking existing observers. Default values + Firestore backfill needed?

exec
/bin/bash -lc "nl -ba docs/features/lesson-runner/implementation.md | sed -n '1,150p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	# Implementation — lesson-runner
     2	
     3	Status: **implemented**
     4	Дата: 2026-04-28
     5	Branch: `kmp-skillify-4.0`
     6	
     7	## Summary
     8	
     9	Lesson-runner — gameplay loop викторины — реализован атомарной заменой `LessonPlaceholder` на полноценный flow в `quizzes-screen`. Один вопрос на экран, таймер, auto-random на timeout, результат с процентами/звёздами/топ-3, опрос «Оцените урок», запись попытки в Room. Все 65 acceptance criteria из `0-spec.md` покрыты. 7 фаз pipeline pass (phase-01 → phase-07).
    10	
    11	## Phases Completed
    12	
    13	| Phase | Goal | Verdict | Notable findings |
    14	|-------|------|---------|-------------------|
    15	| Phase-01 | Foundation & Core Types | PASS (5/5 reviewers) | mini-fix `KoinModuleWiringTest.kt:320,393` (pre-existing baseline) |
    16	| Phase-02 | Persistence — Migration v3→v4 | PASS (5/5 reviewers) | ADR-LR-18: DifficultyConverter removed |
    17	| Phase-03 | Data Layer (NEW lesson-runner/data) | PASS (5/5 reviewers) | jvmAndAndroidMain intermediate source set |
    18	| Phase-04 | Presentation Component | PASS (5/5 reviewers) | 3 compile blockers + HIGH (componentJob, double-complete) — все resolved fix loop |
    19	| Phase-05 | Compose UI (LessonRunnerScreen + 12 composables) | PASS (5/5 reviewers) | ADR-LR-19: RunnerUiState.Result flat projection (security-driven) |
    20	| Phase-06 | Quizzes-screen Integration (atomic replace) | PASS (5/5 reviewers) | LessonListUiState sealed (Loading/Empty/Loaded) |
    21	| Phase-07 | Composition Root + Smoke | PASS (4/5 reviewers; concurrency N/A) | ADR-LR-20: LessonRunnerComponentFactory in presentation module |
    22	
    23	## Review Verdicts
    24	
    25	| Reviewer | Phases | Total findings | Blockers resolved | High resolved | Medium resolved | Low documented |
    26	|----------|--------|----------------|-------------------|---------------|-----------------|----------------|
    27	| architect-reviewer | 1-7 | 5 | 0 | 2 | 2 | 1 |
    28	| code-reviewer | 1-7 | 9 | 1 | 2 | 4 | 2 |
    29	| security-reviewer | 1-7 | 7 | 0 | 0 | 2 | 5 |
    30	| completeness-reviewer | 1-7 | 8 | 5 | 0 | 1 | 2 |
    31	| concurrency-reviewer | 2-6 | 5 | 0 | 1 | 1 | 3 |
    32	
    33	Все findings закрыты автономно через reviewer↔coder loop. Lead вмешался только для 3 design escalations (ADR-LR-18/19/20).
    34	
    35	## Changed Files
    36	
    37	### NEW Modules (3 Gradle modules)
    38	
    39	- `shared/core/leaderboard/` — TopParticipant @Serializable (1 file)
    40	- `shared/feature/lesson-runner/data/` — RepositoryImpls, mappers, providers, Koin modules (10 files)
    41	- `android/feature/lesson-runner/presentation/` — Component, states, events, mapper, Compose UI, fakes (~30 files including tests)
    42	
    43	### NEW Files in existing modules (~12)
    44	
    45	- `shared/core/question-schema/`: KotlinxSerializationQuestionContentParser.kt, di/QuestionSchemaModule.kt
    46	- `shared/core/persistence/`: LessonAttemptEntity, LessonRatingSubmittedLocalEntity, LessonAttemptDao, LessonRatingLocalDao, TopParticipantListConverter, Migration3to4
    47	- `shared/feature/lesson-runner/domain/provider/`: AttemptIdProvider, RandomSeedProvider, RatingIdProvider
    48	- `android/feature/quizzes-screen/presentation/`: uistate/LessonItemUi.kt, screen/LessonItemCard.kt, uistate/LessonListUiState.kt
    49	
    50	### Modified Files (~20)
    51	
    52	Domain: Lesson +3 fields (averageRating, ratingCount, top3), AttemptId/RatingId .raw→.value, QuestionContent + Difficulty @Serializable.
    53	Data: LessonEntity +3 cols, LessonMapper backward-compat, LessonDtoMapper, FirestoreLessonDtoMapper (HTTPS-only avatarUrl).
    54	Persistence: AppDatabase v4, PersistenceModule (addMigrations + addTypeConverter, removed fallbackToDestructiveMigration).
    55	Quizzes-screen: QuizzesConfig (LessonRunner add, LessonPlaceholder remove), QuizzesChild, DefaultQuizzesComponent (+3 deps), QuizzesScreen, DefaultLessonListComponent (combine flow + 2 deps), LessonListScreen, QuizzesPresentationModule, build.gradle.kts.
    56	Composition root: AppApplication.kt (+4 modules), apps/android-next/build.gradle.kts.
    57	
    58	### Deleted Files
    59	
    60	Production (4): LessonPlaceholderComponent.kt, DefaultLessonPlaceholderComponent.kt, LessonPlaceholderScreen.kt, LessonPlaceholderUiState.kt.
    61	Tests (3): DefaultLessonPlaceholderComponentTest.kt, FakeLessonPlaceholderComponent.kt, LessonPlaceholderScreenTest.kt.
    62	Domain (1): TopParticipant.kt в lesson-runner/domain (moved to core/leaderboard).
    63	Phase-02 deletion: DifficultyConverter.kt + MT-05 tests (ADR-LR-18 supersedes).
    64	
    65	## Test Coverage
    66	
    67	- **Walking Skeleton domain (commonTest)**: ~89 tests via fakes (DT-01..82) — all green
    68	- **Phase-01 (foundation)**: 19 tests — parser round-trip, rename verification, serialization, Lesson fields, providers, TopParticipant
    69	- **Phase-02 (persistence)**: 19 tests — MT-01..07 migration (instrumented), DAO-01..05 (instrumented), Mapper-01..04 (jvm), TypeConverter tests
    70	- **Phase-03 (data)**: 13 tests — IT-01/IT-08 proxy, providers, mappers
    71	- **Phase-04 (presentation)**: 41 PT tests + IT-02/03 — Component lifecycle, state, events
    72	- **Phase-05 (Compose UI)**: 27 CT tests + 3 @Ignore (CT-22..24 deferred to phase-06) — instrumented Compose tests
    73	- **Phase-06 (quizzes integration)**: 11 tests — PT-15..17, PT-34..36, CT-22..24, serialization
    74	- **Phase-07 (composition root)**: 8 IT-09a..h — Koin wiring resolution
    75	
    76	**Total**: ~227+ tests (Walking Skeleton + 7 phases). All green at pipeline close.
    77	
    78	## Build Validation
    79	
    80	- `./gradlew ciCheck --no-configuration-cache` — GREEN (2300+ tasks)
    81	- `./gradlew test --no-configuration-cache` — GREEN
    82	- `./gradlew allTests --no-configuration-cache` — GREEN
    83	- `./gradlew :apps:android-next:assembleDebug --no-configuration-cache` — GREEN
    84	- `./gradlew :shared:core:persistence:connectedAndroidTest` — 46/46 на Pixel 10 Pro
    85	- `./gradlew :android:feature:lesson-runner:presentation:connectedAndroidTest` — 27/27 (3 @Ignore deferred) на TECNO KG5m
    86	- `./gradlew :android:feature:quizzes-screen:presentation:connectedAndroidTest` — included in build gate
    87	- `./gradlew detekt ktlintCheck --no-configuration-cache` — GREEN
    88	
    89	## Smoke
    90	
    91	- App installed on Pixel 10 Pro (`adb install` → Success)
    92	- Launched via `adb shell monkey -p com.tpov.schoolquiz.next -c android.intent.category.LAUNCHER 1` — no FATAL log entries
    93	- Полная UI navigation (Catalog → Quest → Section → Theme → Lesson → tap → LessonRunnerScreen) — deferred user verification (interactive UI testing вне scope automated smoke)
    94	
    95	## ADR Additions During Implementation
    96	
    97	| ADR | Phase | Decision | Rationale |
    98	|-----|-------|----------|-----------|
    99	| LR-18 | 02 | DifficultyConverter removed; mapper-based Difficulty↔Int conversion in data layer | Room KMP 2.7+ rejects unused converters; entity uses Int isHard, mapper handles conversion. Plan invariant overview.md:129 superseded. |
   100	| LR-19 | 04→05 | RunnerUiState.Result uses flat projection instead of attempt: Attempt aggregate | Security: attempt contained PII (userId, codeAnswer, attemptId) — minimal exposure principle in public StateFlow. 06-api-contract.md:408 + frontend.md Pattern Invariant superseded. |
   101	| LR-20 | 04→07 | single<LessonRunnerComponentFactory> in lessonRunnerPresentationModule, NOT in AppApplication | Phase-04 already established single<> binding; phase-07 plan duplication would cause Koin override warning. Per 06-api-contract.md:374, factory binding stays in feature presentation module. |
   102	
   103	Все ADRs added в `docs/features/lesson-runner/03-decisions.md` и referenced from updated phase plans.
   104	
   105	## Remaining Issues / Known Gaps
   106	
   107	1. **Cross-phase Codex CLI review** — не запущен. Diff vs master = 6525 files (prior KMP refactor). Focused lesson-runner diff невозможен на этой ветке. Recommended: Codex review при следующем merge to main, scoped к lesson-runner-only changes.
   108	2. **Manual smoke полный flow** — interactive UI navigation deferred user verification. Все automated gates green.
   109	3. **Pre-existing debt не trogался** в этой фиче:
   110	   - `android/core/designsystem/QuestCard.kt:29` — импортирует shared.feature.quest.domain (core-imports-feature violation). Existed before phase-01.
   111	   - `shared/core/sync/CascadingSyncOrchestrator.kt:5-12` — импортирует feature repositories. Existed before.
   112	4. **Out of Scope per spec** (deferred features, документированы в 0-spec):
   113	   - Cascade sync для lesson_attempts / lesson_ratings
   114	   - Cloud Functions для агрегации Lesson.averageRating / top3
   115	   - Avatar sync (subset of users/{uid})
   116	   - Logout cleanup local lesson_attempts
   117	   - Sync state прохождения между sessions
   118	   - Лидерборд экран (выше top-3)
   119	   - Repetition mechanism
   120	
   121	## Quality Scorecard
   122	
   123	См. `docs/features/lesson-runner/quality-scorecard.md` для детальной разбивки по параметрам (Architecture B, Correctness B, Completeness A, Security A, Code Organization A — Overall B+).
   124	
   125	## Pipeline Statistics
   126	
   127	- 7 phases × ~7-9 task-units = ~55 task instances
   128	- 5 reviewer agents × 7 phases = 35 review passes (concurrency reviewer phases 2-6 only = 30 effective)
   129	- 3 ADR escalations (LR-18/19/20) — all resolved within 1-2 lead cycles
   130	- 0 architectural mismatch escalations требовавших спец работы вне plan
   131	- 0 phases reverted / re-planned — все progressed forward через autonomous loop

exec
/bin/bash -lc 'nl -ba shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonRatingLocalDao.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.core.persistence
     2	
     3	import androidx.room.Dao
     4	import androidx.room.Insert
     5	import androidx.room.OnConflictStrategy
     6	import androidx.room.Query
     7	import kotlinx.coroutines.flow.Flow
     8	
     9	@Dao
    10	interface LessonRatingLocalDao {
    11	
    12	    @Insert(onConflict = OnConflictStrategy.REPLACE)
    13	    suspend fun upsert(entity: LessonRatingSubmittedLocalEntity): Long
    14	
    15	    @Query("SELECT COUNT(*) > 0 FROM lesson_rating_submitted_local WHERE user_id = :userId AND lesson_id = :lessonId")
    16	    fun hasSubmitted(userId: String, lessonId: String): Flow<Boolean>
    17	}

exec
/bin/bash -lc 'nl -ba shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonRatingSubmittedLocalEntity.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.core.persistence
     2	
     3	import androidx.room.ColumnInfo
     4	import androidx.room.Entity
     5	
     6	@Entity(
     7	    tableName = "lesson_rating_submitted_local",
     8	    primaryKeys = ["user_id", "lesson_id"],
     9	)
    10	data class LessonRatingSubmittedLocalEntity(
    11	    @ColumnInfo(name = "user_id") val userId: String,
    12	    @ColumnInfo(name = "lesson_id") val lessonId: String,
    13	    @ColumnInfo(name = "submitted_at") val submittedAt: Long,
    14	)

exec
/bin/bash -lc 'nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/LessonRating.kt' in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabase.kt | sed -n '1,180p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc 'nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/Attempt.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.core.persistence
     2	
     3	import androidx.room.Database
     4	import androidx.room.RoomDatabase
     5	import androidx.room.TypeConverters
     6	
     7	@Database(
     8	    entities = [
     9	        UserStatsEntity::class,
    10	        CatalogEntity::class,
    11	        QuestEntity::class,
    12	        SectionEntity::class,
    13	        ThemeEntity::class,
    14	        LessonEntity::class,
    15	        QuestionEntity::class,
    16	        LessonAttemptEntity::class,
    17	        LessonRatingSubmittedLocalEntity::class,
    18	    ],
    19	    version = 4,
    20	    exportSchema = true,
    21	)
    22	@TypeConverters(StringSetConverter::class, TopParticipantListConverter::class)
    23	abstract class AppDatabase : RoomDatabase() {
    24	    abstract fun userStatsDao(): UserStatsDao
    25	    abstract fun catalogDao(): CatalogDao
    26	    abstract fun questDao(): QuestDao
    27	    abstract fun sectionDao(): SectionDao
    28	    abstract fun themeDao(): ThemeDao
    29	    abstract fun lessonDao(): LessonDao
    30	    abstract fun questionDao(): QuestionDao
    31	    abstract fun lessonAttemptDao(): LessonAttemptDao
    32	    abstract fun lessonRatingLocalDao(): LessonRatingLocalDao
    33	}

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
     2	
     3	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
     4	
     5	/**
     6	 * Immutable user rating for a lesson. One per (userId, lessonId) lifetime.
     7	 * Remote ID = deterministic sha256("$userId:$lessonId").
     8	 * lessonVersion stored for analytics; not part of uniqueness key.
     9	 */
    10	data class LessonRating(
    11	    val id: RatingId,
    12	    val userId: String,
    13	    val lessonId: LessonId,
    14	    val lessonVersion: Long,
    15	    val rating: Int,
    16	    val ratedAt: Long,
    17	) {
    18	    init {
    19	        require(userId.isNotBlank()) { "LessonRating.userId must not be blank" }
    20	        require(lessonVersion >= 1) { "LessonRating.lessonVersion must be >= 1, got $lessonVersion" }
    21	        require(rating in 1..3) { "LessonRating.rating must be in 1..3, got $rating" }
    22	        require(ratedAt >= 0) { "LessonRating.ratedAt must be >= 0, got $ratedAt" }
    23	    }
    24	}

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
     2	
     3	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
     4	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
     5	
     6	/**
     7	 * Immutable record of a completed or aborted lesson attempt.
     8	 * One write per attempt (Room); not edited after save.
     9	 */
    10	data class Attempt(
    11	    val id: AttemptId,
    12	    val userId: String,
    13	    val lessonId: LessonId,
    14	    val lessonVersion: Long,
    15	    val mode: Difficulty,
    16	    val completedAt: Long,
    17	    val codeAnswer: CodeAnswer,
    18	    val percentScore: PercentScore,
    19	) {
    20	    init {
    21	        require(userId.isNotBlank()) { "Attempt.userId must not be blank" }
    22	        require(lessonVersion >= 1) { "Attempt.lessonVersion must be >= 1, got $lessonVersion" }
    23	        require(completedAt >= 0) { "Attempt.completedAt must be >= 0, got $completedAt" }
    24	    }
    25	}

exec
/bin/bash -lc 'nl -ba shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonRatingMapper.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper
     2	
     3	import com.tpov.schoolquiz.shared.core.persistence.LessonRatingSubmittedLocalEntity
     4	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
     5	
     6	fun LessonRating.toEntity(): LessonRatingSubmittedLocalEntity = LessonRatingSubmittedLocalEntity(
     7	    userId = userId,
     8	    lessonId = lessonId.value,
     9	    submittedAt = ratedAt,
    10	)

exec
/bin/bash -lc "nl -ba shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/dto/LessonDto.kt | sed -n '1,180p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/model/Lesson.kt | sed -n '1,180p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson.data.dto
     2	
     3	import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
     4	
     5	data class LessonDto(
     6	    val id: String,
     7	    val themeId: String,
     8	    val title: String,
     9	    val order: Int,
    10	    val version: Long,
    11	    val contentsVersion: Long,
    12	    val lastModifiedAt: Long,
    13	    val archived: Boolean,
    14	    val averageRating: Float? = null,
    15	    val ratingCount: Int? = null,
    16	    val top3: List<TopParticipant>? = null,
    17	)

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson.domain.model
     2	
     3	import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
     4	import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
     5	
     6	/**
     7	 * Domain entity representing a lesson within a [Theme].
     8	 *
     9	 * A lesson is the direct parent of questions. Stored in Firestore collection
    10	 * `lessons/{lessonId}` with a [themeId] reference.
    11	 *
    12	 * Spec: docs/features/home-and-my-quests/0-spec.md
    13	 *   FR#12, Feature Domain Contract — Lesson invariants (scenario 18).
    14	 *   [Codex fix #5] archived: consistent soft-delete across all hierarchy levels.
    15	 */
    16	data class Lesson(
    17	    val id: LessonId,
    18	    val themeId: ThemeId,
    19	    val title: String,
    20	    /**
    21	     * Display order within the parent theme. Non-negative.
    22	     * Invariant: >= 0.
    23	     */
    24	    val order: Int,
    25	    /**
    26	     * Monotonically-increasing version of this lesson's own fields.
    27	     * Invariant: >= 1.
    28	     */
    29	    val version: Long,
    30	    /**
    31	     * Version counter tracking changes in child questions.
    32	     * Invariant: >= 0.
    33	     */
    34	    val contentsVersion: Long,
    35	    /**
    36	     * Server-set timestamp (Unix millis) of the last write.
    37	     * Used as delta-sync cursor: `where('lastModifiedAt', '>', localCursor)`.
    38	     * Set by Firestore FieldValue.serverTimestamp() at write time.
    39	     * Invariant: >= 0.
    40	     */
    41	    val lastModifiedAt: Long,
    42	    /**
    43	     * Soft-delete flag. When `true`, the client deletes the local lesson entry.
    44	     * Default `false` for backward compatibility.
    45	     */
    46	    val archived: Boolean = false,
    47	    val averageRating: Float? = null,
    48	    val ratingCount: Int = 0,
    49	    val top3: List<TopParticipant> = emptyList(),
    50	) {
    51	    init {
    52	        require(title.isNotBlank()) { "Lesson.title must not be blank" }
    53	        require(order >= 0) { "Lesson.order must be >= 0, got $order" }
    54	        require(version >= 1) { "Lesson.version must be >= 1, got $version" }
    55	        require(contentsVersion >= 0) { "Lesson.contentsVersion must be >= 0, got $contentsVersion" }
    56	        require(lastModifiedAt >= 0) { "Lesson.lastModifiedAt must be >= 0, got $lastModifiedAt" }
    57	        require(top3.size <= 3) { "Lesson.top3.size must be <= 3, got ${top3.size}" }
    58	    }
    59	}

exec
/bin/bash -lc "nl -ba shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/mapper/LessonDtoMapper.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson.data.mapper
     2	
     3	import com.tpov.schoolquiz.shared.core.persistence.LessonEntity
     4	import com.tpov.schoolquiz.shared.feature.lesson.data.dto.LessonDto
     5	
     6	object LessonDtoMapper {
     7	    fun LessonDto.toEntity(): LessonEntity = LessonEntity(
     8	        id = id,
     9	        themeId = themeId,
    10	        title = title,
    11	        order = order,
    12	        version = version,
    13	        contentsVersion = contentsVersion,
    14	        lastModifiedAt = lastModifiedAt,
    15	        archived = archived,
    16	        averageRating = averageRating,
    17	        ratingCount = ratingCount ?: 0,
    18	        top3 = top3 ?: emptyList(),
    19	    )
    20	}

exec
/bin/bash -lc "nl -ba shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/mapper/LessonMapper.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/lesson/FirestoreLessonDtoMapper.kt | sed -n '1,240p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson.data.mapper
     2	
     3	import com.tpov.schoolquiz.shared.core.persistence.LessonEntity
     4	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
     5	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
     6	import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
     7	
     8	object LessonMapper {
     9	    fun LessonEntity.toDomain(): Lesson = Lesson(
    10	        id = LessonId(id),
    11	        themeId = ThemeId(themeId),
    12	        title = title,
    13	        order = order,
    14	        version = version,
    15	        contentsVersion = contentsVersion,
    16	        lastModifiedAt = lastModifiedAt,
    17	        archived = archived,
    18	        averageRating = averageRating,
    19	        ratingCount = ratingCount,
    20	        top3 = top3,
    21	    )
    22	}

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.platform.firebase.lesson
     2	
     3	import com.google.firebase.firestore.DocumentSnapshot
     4	import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
     5	import com.tpov.schoolquiz.shared.feature.lesson.data.dto.LessonDto
     6	
     7	fun DocumentSnapshot.toLessonDto(): LessonDto =
     8	    LessonDto(
     9	        id = id,
    10	        themeId = getString("themeId") ?: "",
    11	        title = getString("title") ?: "",
    12	        order = getLong("order")?.toInt() ?: 0,
    13	        version = getLong("version") ?: 1L,
    14	        contentsVersion = getLong("contentsVersion") ?: 0L,
    15	        lastModifiedAt = getTimestamp("lastModifiedAt")?.toDate()?.time ?: 0L,
    16	        archived = getBoolean("archived") ?: false,
    17	        averageRating = getDouble("averageRating")?.toFloat(),
    18	        ratingCount = getLong("ratingCount")?.toInt(),
    19	        top3 = parseTop3(this),
    20	    )
    21	
    22	@Suppress("UNCHECKED_CAST")
    23	private fun parseTop3(doc: DocumentSnapshot): List<TopParticipant>? {
    24	    val raw = doc.get("top3") as? List<*> ?: return null
    25	    return raw.mapNotNull { item ->
    26	        val map = item as? Map<*, *> ?: return@mapNotNull null
    27	        val nickname = map["nickname"] as? String ?: return@mapNotNull null
    28	        val avatarUrl = (map["avatarUrl"] as? String)?.takeIf { it.startsWith("https://") }
    29	        val percent = (map["percent"] as? Long)?.toInt() ?: return@mapNotNull null
    30	        TopParticipant(nickname = nickname, avatarUrl = avatarUrl, percent = percent)
    31	    }
    32	}

exec
/bin/bash -lc "nl -ba shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonEntity.kt | sed -n '1,200p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.core.persistence
     2	
     3	import androidx.room.ColumnInfo
     4	import androidx.room.Entity
     5	import androidx.room.ForeignKey
     6	import androidx.room.Index
     7	import androidx.room.PrimaryKey
     8	import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
     9	
    10	@Entity(
    11	    tableName = "lessons",
    12	    foreignKeys = [
    13	        ForeignKey(
    14	            entity = ThemeEntity::class,
    15	            parentColumns = ["id"],
    16	            childColumns = ["themeId"],
    17	            onDelete = ForeignKey.CASCADE,
    18	        ),
    19	    ],
    20	    indices = [
    21	        Index(value = ["themeId"]),
    22	        Index(value = ["lastModifiedAt"]),
    23	    ],
    24	)
    25	data class LessonEntity(
    26	    @PrimaryKey val id: String,
    27	    val themeId: String,
    28	    val title: String,
    29	    val order: Int,
    30	    val version: Long,
    31	    val contentsVersion: Long,
    32	    val lastModifiedAt: Long,
    33	    val archived: Boolean,
    34	    @ColumnInfo(name = "average_rating") val averageRating: Float? = null,
    35	    @ColumnInfo(name = "rating_count") val ratingCount: Int = 0,
    36	    @ColumnInfo(name = "top3") val top3: List<TopParticipant> = emptyList(),
    37	)

exec
/bin/bash -lc 'nl -ba shared/core/leaderboard/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/leaderboard/TopParticipant.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.core.leaderboard
     2	
     3	import kotlinx.serialization.Serializable
     4	
     5	@Serializable
     6	data class TopParticipant(
     7	    val nickname: String,
     8	    val avatarUrl: String?,
     9	    val percent: Int,
    10	)

exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/data/build.gradle.kts | sed -n '1,240p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	plugins {
     2	    id("schoolquiz.kmp.library")
     3	}
     4	
     5	android {
     6	    namespace = "com.tpov.schoolquiz.shared.feature.lesson_runner.data"
     7	}
     8	
     9	kotlin {
    10	    sourceSets {
    11	        val jvmAndAndroidMain by creating {
    12	            dependsOn(commonMain.get())
    13	        }
    14	        jvmMain.get().dependsOn(jvmAndAndroidMain)
    15	        androidMain.get().dependsOn(jvmAndAndroidMain)
    16	
    17	        commonMain.dependencies {
    18	            implementation(libs.kotlinx.coroutines.core)
    19	            implementation(libs.kotlinx.datetime)
    20	            implementation(project(":shared:feature:lesson-runner:domain"))
    21	            implementation(project(":shared:feature:lesson:domain"))
    22	            implementation(project(":shared:core:question-schema"))
    23	            implementation(project(":shared:core:persistence"))
    24	        }
    25	        androidMain.dependencies {
    26	            implementation(libs.koin.core)
    27	            implementation(project(":shared:feature:question:domain"))
    28	            implementation(project(":shared:feature:app-shell:domain"))
    29	        }
    30	        commonTest.dependencies {
    31	            implementation(libs.kotlinx.coroutines.test)
    32	            implementation(libs.junit4)
    33	        }
    34	    }
    35	}

exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/domain/build.gradle.kts | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	plugins {
     2	    id("schoolquiz.kmp.library")
     3	}
     4	
     5	android {
     6	    namespace = "com.tpov.schoolquiz.shared.feature.lesson_runner.domain"
     7	}
     8	
     9	kotlin {
    10	    sourceSets {
    11	        commonMain.dependencies {
    12	            implementation(libs.kotlinx.coroutines.core)
    13	            implementation(libs.kotlinx.datetime)
    14	            implementation(libs.koin.core)
    15	            implementation(project(":shared:core:leaderboard"))
    16	            implementation(project(":shared:core:question-schema"))
    17	            implementation(project(":shared:feature:lesson:domain"))
    18	            implementation(project(":shared:feature:question:domain"))
    19	            implementation(project(":shared:feature:app-shell:domain"))
    20	        }
    21	        commonTest.dependencies {
    22	            implementation(libs.kotlinx.coroutines.test)
    23	            implementation(project(":shared:feature:theme:domain"))
    24	        }
    25	    }
    26	}

exec
/bin/bash -lc "nl -ba android/feature/quizzes-screen/presentation/build.gradle.kts | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	plugins {
     2	    id("schoolquiz.android.compose.library")
     3	    alias(libs.plugins.kotlin.serialization)
     4	}
     5	
     6	android {
     7	    namespace = "com.tpov.schoolquiz.android.feature.quizzes_screen.presentation"
     8	}
     9	
    10	dependencies {
    11	    implementation(project(":android:core:designsystem"))
    12	    implementation(project(":android:feature:lesson-runner:presentation"))
    13	    implementation(project(":shared:feature:quest:domain"))
    14	    implementation(project(":shared:feature:section:domain"))
    15	    implementation(project(":shared:feature:theme:domain"))
    16	    implementation(project(":shared:feature:lesson:domain"))
    17	    implementation(project(":shared:feature:lesson-runner:domain"))
    18	    implementation(project(":shared:feature:app-shell:domain"))
    19	    implementation(project(":shared:core:catalog:domain"))
    20	    implementation(project(":shared:core:question-schema"))
    21	
    22	    implementation(platform(libs.compose.bom))
    23	    implementation(libs.bundles.compose.ui)
    24	    implementation(libs.bundles.decompose)
    25	    implementation(libs.bundles.koin.android)
    26	    implementation(libs.kotlinx.serialization.json)
    27	
    28	    testImplementation(libs.junit4)
    29	    testImplementation(libs.kotlin.test.junit)
    30	    testImplementation(libs.kotlinx.coroutines.test)
    31	    testImplementation(libs.mockk)
    32	    testImplementation(project(":shared:feature:lesson-runner:domain"))
    33	    testImplementation(project(":shared:feature:app-shell:domain"))
    34	
    35	    androidTestImplementation(platform(libs.compose.bom))
    36	    androidTestImplementation(libs.compose.ui.test)
    37	    androidTestImplementation(libs.compose.ui.test.junit4)
    38	    androidTestImplementation(libs.androidx.test.ext.junit)
    39	    androidTestImplementation(libs.kotlin.test.junit)
    40	    androidTestImplementation(libs.androidx.test.espresso.intents)
    41	    debugImplementation(libs.compose.ui.test.manifest)
    42	}

exec
/bin/bash -lc "nl -ba settings.gradle.kts | sed -n '1,140p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba apps/android-next/build.gradle.kts | sed -n '1,170p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	pluginManagement {
     2	    repositories {
     3	        gradlePluginPortal()
     4	        google()
     5	        mavenCentral()
     6	        maven(url = "https://jitpack.io")
     7	    }
     8	}
     9	
    10	dependencyResolutionManagement {
    11	    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    12	    repositories {
    13	        google()
    14	        mavenCentral()
    15	        maven(url = "https://jitpack.io")
    16	    }
    17	}
    18	
    19	rootProject.name = "schoolquiz"
    20	
    21	// layered-scaffold:start
    22	// apps
    23	include(":apps:android-next")
    24	
    25	// shared-core
    26	include(":shared:core:catalog:domain")
    27	include(":shared:core:catalog:data")
    28	include(":shared:core:foundation")
    29	include(":shared:core:logger")
    30	include(":shared:core:model")
    31	include(":shared:core:leaderboard")
    32	include(":shared:core:question-schema")
    33	include(":shared:core:persistence")
    34	include(":shared:core:network")
    35	include(":shared:core:preferences")
    36	include(":shared:core:stats")
    37	include(":shared:core:sync")
    38	include(":shared:core:test")
    39	
    40	// shared-feature
    41	include(":shared:feature:app-shell:domain")
    42	include(":shared:feature:app-shell:data")
    43	include(":shared:feature:quest:domain")
    44	include(":shared:feature:quest:data")
    45	include(":shared:feature:section:domain")
    46	include(":shared:feature:section:data")
    47	include(":shared:feature:theme:domain")
    48	include(":shared:feature:theme:data")
    49	include(":shared:feature:lesson:domain")
    50	include(":shared:feature:lesson:data")
    51	include(":shared:feature:question:domain")
    52	include(":shared:feature:question:data")
    53	include(":shared:feature:lesson-runner:domain")
    54	include(":shared:feature:lesson-runner:data")
    55	include(":shared:feature:local:settings:domain")
    56	include(":shared:feature:local:settings:data")
    57	include(":shared:feature:internet:auth:domain")
    58	include(":shared:feature:internet:auth:data")
    59	include(":shared:feature:internet:profile:domain")
    60	include(":shared:feature:internet:profile:data")
    61	include(":shared:feature:internet:social:domain")
    62	include(":shared:feature:internet:social:data")
    63	include(":shared:feature:internet:leaderboard:domain")
    64	include(":shared:feature:internet:leaderboard:data")
    65	include(":shared:feature:qualification:domain")
    66	include(":shared:feature:qualification:data")
    67	include(":shared:feature:economy:domain")
    68	include(":shared:feature:economy:data")
    69	include(":shared:feature:minigame:domain")
    70	include(":shared:feature:minigame:data")
    71	
    72	// android
    73	include(":android:core:navigation")
    74	include(":android:core:designsystem")
    75	include(":android:core:userguide")
    76	include(":android:feature:app-shell:presentation")
    77	include(":android:feature:quest:presentation")
    78	include(":android:feature:quest:test-fixtures")
    79	include(":android:feature:quizzes-screen:presentation")
    80	include(":android:feature:lesson-runner:presentation")
    81	include(":android:feature:local:settings:presentation")
    82	include(":android:feature:internet:auth:presentation")
    83	include(":android:feature:internet:profile:presentation")
    84	include(":android:feature:internet:social:presentation")
    85	include(":android:feature:internet:leaderboard:presentation")
    86	include(":android:feature:qualification:presentation")
    87	include(":android:feature:economy:presentation")
    88	include(":android:feature:minigame:presentation")
    89	
    90	// platform
    91	include(":platform:android-services")
    92	include(":platform:firebase")
    93	include(":platform:billing")
    94	include(":platform:crypto")
    95	include(":platform:telegram")
    96	
    97	// server
    98	include(":server:functions")
    99	include(":server:workers:sync")
   100	include(":server:workers:leaderboard")
   101	include(":server:workers:rewards")
   102	include(":server:workers:review-collisions")
   103	include(":server:workers:notifications")
   104	include(":server:bot-telegram")
   105	include(":server:ai-gateway")
   106	include(":server:admin-tools")
   107	// layered-scaffold:end

 succeeded in 0ms:
     1	plugins {
     2	    id("schoolquiz.android.compose.application")
     3	    alias(libs.plugins.google.services)
     4	}
     5	
     6	android {
     7	    namespace = "com.tpov.schoolquiz.apps.android_next"
     8	
     9	    defaultConfig {
    10	        applicationId = "com.tpov.schoolquiz.next"
    11	        versionCode = 1
    12	        versionName = "0.1.0"
    13	    }
    14	
    15	    buildFeatures {
    16	        buildConfig = true
    17	    }
    18	}
    19	
    20	dependencies {
    21	    // Feature modules
    22	    implementation(project(":shared:feature:app-shell:domain"))
    23	    implementation(project(":shared:feature:app-shell:data"))
    24	    implementation(project(":android:feature:app-shell:presentation"))
    25	    implementation(project(":android:feature:quest:presentation"))
    26	    implementation(project(":android:feature:quizzes-screen:presentation"))
    27	    implementation(project(":android:core:navigation"))
    28	    implementation(project(":android:core:designsystem"))
    29	    implementation(project(":platform:firebase"))
    30	    implementation(platform(libs.firebase.bom))
    31	    implementation(libs.firebase.auth.ktx)
    32	    implementation(project(":shared:core:persistence"))
    33	    implementation(project(":shared:core:catalog:data"))
    34	    implementation(project(":shared:core:catalog:domain"))
    35	    implementation(project(":shared:feature:quest:data"))
    36	    implementation(project(":shared:feature:quest:domain"))
    37	    implementation(project(":shared:feature:section:data"))
    38	    implementation(project(":shared:feature:section:domain"))
    39	    implementation(project(":shared:feature:theme:data"))
    40	    implementation(project(":shared:feature:theme:domain"))
    41	    implementation(project(":shared:feature:lesson:data"))
    42	    implementation(project(":shared:feature:lesson:domain"))
    43	    implementation(project(":shared:feature:question:data"))
    44	    implementation(project(":shared:feature:question:domain"))
    45	    implementation(project(":shared:core:question-schema"))
    46	    implementation(project(":shared:feature:lesson-runner:data"))
    47	    implementation(project(":android:feature:lesson-runner:presentation"))
    48	    implementation(project(":platform:android-services"))
    49	    implementation(libs.androidx.work.runtime.ktx)
    50	    implementation(project(":shared:core:sync"))
    51	
    52	    // Compose
    53	    implementation(platform(libs.compose.bom))
    54	    implementation(libs.bundles.compose.ui)
    55	    implementation(libs.bundles.compose.ui.tooling)
    56	    implementation(libs.androidx.activity.compose)
    57	
    58	    // Decompose
    59	    implementation(libs.bundles.decompose)
    60	
    61	    // Koin
    62	    implementation(libs.bundles.koin.android)
    63	
    64	    // Lifecycle
    65	    implementation(libs.bundles.androidx.lifecycle)
    66	    implementation(libs.bundles.androidx.lifecycle.compose)
    67	
    68	    // Base
    69	    implementation(libs.bundles.androidx.ui.base)
    70	
    71	    // Tests
    72	    testImplementation(libs.junit4)
    73	    testImplementation(libs.koin.core)
    74	    testImplementation(libs.koin.test)
    75	    testImplementation(libs.koin.test.junit4)
    76	    testImplementation(libs.kotlin.test.junit)
    77	    testImplementation(libs.kotlinx.coroutines.test)
    78	    testImplementation(libs.mockito.core)
    79	    testImplementation(project(":shared:core:stats"))
    80	    testImplementation(project(":shared:core:question-schema"))
    81	    testImplementation(project(":shared:feature:lesson-runner:domain"))
    82	    testImplementation(project(":android:feature:lesson-runner:presentation"))
    83	    androidTestImplementation(libs.bundles.testing.instrumented)
    84	}

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/build.gradle.kts | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	plugins {
     2	    id("schoolquiz.android.compose.library")
     3	}
     4	
     5	android {
     6	    namespace = "com.tpov.schoolquiz.android.feature.lesson_runner.presentation"
     7	}
     8	
     9	dependencies {
    10	    implementation(project(":shared:feature:lesson-runner:domain"))
    11	    implementation(project(":shared:feature:lesson:domain"))
    12	    implementation(project(":shared:core:question-schema"))
    13	    implementation(project(":shared:core:leaderboard"))
    14	    implementation(project(":android:core:designsystem"))
    15	
    16	    implementation(libs.kotlinx.datetime)
    17	
    18	    implementation(platform(libs.compose.bom))
    19	    implementation(libs.bundles.compose.ui)
    20	    implementation(libs.bundles.decompose)
    21	    implementation(libs.bundles.koin.android)
    22	
    23	    testImplementation(libs.junit4)
    24	    testImplementation(libs.kotlin.test.junit)
    25	    testImplementation(libs.kotlinx.coroutines.test)
    26	    testImplementation(project(":shared:feature:theme:domain"))
    27	    testImplementation(project(":shared:feature:question:domain"))
    28	    testImplementation(project(":shared:feature:app-shell:domain"))
    29	
    30	    androidTestImplementation(platform(libs.compose.bom))
    31	    androidTestImplementation(libs.compose.ui.test.junit4)
    32	    androidTestImplementation(libs.androidx.test.ext.junit)
    33	    debugImplementation(libs.compose.ui.test.manifest)
    34	}

exec
/bin/bash -lc 'nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.di
     2	
     3	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
     4	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
     6	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
     7	import org.koin.dsl.module
     8	
     9	/**
    10	 * Koin domain module for lesson-runner.
    11	 *
    12	 * Clock: data module registers single<Clock> { Clock.System }.
    13	 * Providers: data module registers single<AttemptIdProvider>, single<RandomSeedProvider>, single<RatingIdProvider>.
    14	 * LessonRunnerDomainKoinAdapter (data/androidMain) bridges providers → use case function types.
    15	 * This module is NOT registered in production — use lessonRunnerDomainKoinAdapter instead (ADR-LR-09 C1).
    16	 */
    17	val lessonRunnerDomainModule = module {
    18	    factory {
    19	        StartLessonAttemptUseCase(
    20	            questionRepository = get(),
    21	            lessonRepository = get(),
    22	            parser = get(),
    23	            authRepository = get(),
    24	            clock = get(),
    25	            randomSeedProvider = get(),
    26	        )
    27	    }
    28	    factory {
    29	        CompleteAttemptUseCase(
    30	            attemptRepository = get(),
    31	            ratingRepository = get(),
    32	            clock = get(),
    33	            attemptIdProvider = get(),
    34	        )
    35	    }
    36	    factory {
    37	        AbortAttemptUseCase(
    38	            attemptRepository = get(),
    39	            clock = get(),
    40	            attemptIdProvider = get(),
    41	        )
    42	    }
    43	    factory {
    44	        SubmitLessonRatingUseCase(
    45	            ratingRepository = get(),
    46	            lessonRepository = get(),
    47	            clock = get(),
    48	            ratingIdProvider = get(),
    49	        )
    50	    }
    51	}

exec
/bin/bash -lc "rg -n \"org\\.koin|val .*DomainModule = module|domainModule\" shared/feature/*/domain shared/core/*/domain -g '*.kt'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
shared/core/catalog/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/catalog/domain/di/CatalogDomainModule.kt:4:import org.koin.dsl.module
shared/core/catalog/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/catalog/domain/di/CatalogDomainModule.kt:6:val catalogDomainModule = module {
shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/di/LessonDomainModule.kt:4:import org.koin.dsl.module
shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/di/LessonDomainModule.kt:6:val lessonDomainModule = module {
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt:7:import org.koin.dsl.module
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt:17:val lessonRunnerDomainModule = module {
shared/feature/section/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/section/domain/di/SectionDomainModule.kt:4:import org.koin.dsl.module
shared/feature/section/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/section/domain/di/SectionDomainModule.kt:6:val sectionDomainModule = module {
shared/feature/question/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/question/domain/di/QuestionDomainModule.kt:4:import org.koin.dsl.module
shared/feature/question/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/question/domain/di/QuestionDomainModule.kt:6:val questionDomainModule = module {
shared/feature/theme/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/theme/domain/di/ThemeDomainModule.kt:4:import org.koin.dsl.module
shared/feature/theme/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/theme/domain/di/ThemeDomainModule.kt:6:val themeDomainModule = module {
shared/feature/quest/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/quest/domain/di/QuestDomainModule.kt:5:import org.koin.dsl.module
shared/feature/quest/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/quest/domain/di/QuestDomainModule.kt:7:val questDomainModule = module {

exec
/bin/bash -lc 'git apply --numstat docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
36	0	android/feature/quizzes-screen/presentation/build.gradle.kts
26	0	android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonListComponent.kt
8	0	android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonPlaceholderComponent.kt
31	0	android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuestListComponent.kt
48	0	android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuizzesComponent.kt
26	0	android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeSectionListComponent.kt
26	0	android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeThemeListComponent.kt
109	0	android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonPlaceholderScreenTest.kt
231	0	android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestCardMenuTest.kt
194	0	android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreenTest.kt
118	0	android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesRotationTest.kt
134	0	android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreenTest.kt
2	0	android/feature/quizzes-screen/presentation/src/main/AndroidManifest.xml
62	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt
16	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonPlaceholderComponent.kt
65	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponent.kt
130	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt
61	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponent.kt
61	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponent.kt
11	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonListComponent.kt
7	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonPlaceholderComponent.kt
12	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuestListComponent.kt
10	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt
17	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesComponent.kt
11	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/SectionListComponent.kt
11	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/ThemeListComponent.kt
41	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/config/QuizzesConfig.kt
18	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt
11	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/LessonDrillMapper.kt
13	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/QuestToDisplayItemMapper.kt
11	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/SectionDrillMapper.kt
11	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/ThemeDrillMapper.kt
10	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/navigation/QuizzesNavigator.kt
103	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonListScreen.kt
68	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonPlaceholderScreen.kt
173	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreen.kt
52	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt
103	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreen.kt
96	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/ThemeListScreen.kt
8	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/HierarchyItemUi.kt
7	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/HierarchyListUiState.kt
6	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/LessonPlaceholderUiState.kt
9	0	android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/QuestListUiState.kt
325	0	android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt
228	0	android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt
204	0	android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt
165	0	android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt
97	0	android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonPlaceholderComponentTest.kt
254	0	android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponentTest.kt
226	0	android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponentTest.kt
149	0	android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponentTest.kt
24	0	android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeBackDispatcher.kt
25	0	android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonRepository.kt
43	0	android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuestRepository.kt
25	0	android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeSectionRepository.kt
39	0	android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeStackNavigation.kt
25	0	android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeThemeRepository.kt
199	0	android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/DrillItemMapperTest.kt
132	0	android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/QuestToDisplayItemMapperTest.kt
78	0	apps/android-next/build.gradle.kts
148	0	apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt
0	0	apps/android-next/src/test/java/.gitkeep
426	0	apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt
24	0	platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/lesson/FirebaseLessonRemoteDataSource.kt
15	0	platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/lesson/FirestoreLessonDtoMapper.kt
103	0	settings.gradle.kts
34	0	shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/di/PersistenceModule.kt
29	0	shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabase.kt
18	0	shared/core/question-schema/build.gradle.kts
2	0	shared/core/question-schema/src/androidMain/AndroidManifest.xml
0	0	shared/core/question-schema/src/androidMain/kotlin/.gitkeep
0	0	shared/core/question-schema/src/commonMain/kotlin/.gitkeep
0	0	shared/core/question-schema/src/commonTest/kotlin/.gitkeep
0	0	shared/core/question-schema/src/jvmMain/kotlin/.gitkeep
0	0	shared/core/question-schema/src/jvmTest/kotlin/.gitkeep
23	0	shared/feature/lesson/data/build.gradle.kts
29	0	shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/LessonLocalDataSource.kt
8	0	shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/LessonRemoteDataSource.kt
51	0	shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/LessonRepositoryImpl.kt
12	0	shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/di/LessonDataModule.kt
12	0	shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/dto/LessonDto.kt
17	0	shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/mapper/LessonDtoMapper.kt
19	0	shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/mapper/LessonMapper.kt
140	0	shared/feature/lesson/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/LessonRepositoryImplTest.kt
48	0	shared/feature/lesson/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/fake/FakeLessonLocalDataSource.kt
21	0	shared/feature/lesson/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/fake/FakeLessonRemoteDataSource.kt
20	0	shared/feature/lesson/domain/build.gradle.kts
8	0	shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/di/LessonDomainModule.kt
54	0	shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/model/Lesson.kt
16	0	shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/model/LessonId.kt
49	0	shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/repository/LessonRepository.kt
26	0	shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/use_case/SyncLessonsUseCase.kt
204	0	shared/feature/lesson/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/LessonDomainTest.kt
95	0	shared/feature/lesson/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/fake/FakeLessonRepository.kt

exec
/bin/bash -lc 'rg -n "''^diff --git .*lesson-runner|''^diff --git .*leaderboard|''^diff --git .*QuestionSchema|shared/feature/lesson-runner|android/feature/lesson-runner|shared/core/leaderboard" docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch' in /home/Programming/Android/schoolquiz4.0
 exited 1 in 0ms:
codex
The called-out ADR placement checks mostly line up: the factory/interface live in lesson-runner presentation, the adapter is under data/androidMain, and AppApplication registers the four lesson-runner modules in the expected order. I’m now hunting for the less obvious cross-phase failures: paths where quizzes-screen bypasses runner semantics, result state data that drifts from the saved attempt, and ACs that are claimed covered but only partially wired.
exec
/bin/bash -lc 'rg -n "''^import com'"\\.tpov\\.schoolquiz\\.android\\.feature\\.quizzes_screen\\.presentation\" android/feature/lesson-runner/presentation/src/main -g '*.kt'" in /home/Programming/Android/schoolquiz4.0
 exited 1 in 0ms:
exec
/bin/bash -lc "rg -n \"interface LessonRunnerRootComponent|fun interface LessonRunnerComponentFactory\" android/core android/feature/lesson-runner/presentation android/feature/quizzes-screen/presentation -g '*.kt'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc 'rg -n "''^import com'"\\.tpov\\.schoolquiz\\.android\\.feature\\.lesson_runner\\.presentation\" android/feature/quizzes-screen/presentation/src/main -g '*.kt'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt:9:interface LessonRunnerRootComponent {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt:7:fun interface LessonRunnerComponentFactory {

 succeeded in 0ms:
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt:4:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt:3:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:14:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt:16:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.LessonRunnerScreen

exec
/bin/bash -lc "rg -n \"attempt: Attempt|val attempt|userId|codeAnswer|attemptId\" android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation -g '*.kt'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:46:    private val attemptRepository: LessonAttemptRepository,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:164:        val userId =
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:166:                is RunnerState.Completed -> domainState.attempt.userId
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:167:                is RunnerState.Aborted -> domainState.attempt.userId
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:172:            val result = useCases.submitRating(userId, lessonId, rating)
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:260:        attempt: Attempt,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:265:        val userAttempts = attemptRepository.observeByLesson(attempt.userId, attempt.lessonId).first()

exec
/bin/bash -lc "rg -n \"single<LessonRunnerComponentFactory>|factory<LessonRunnerRootComponent>|factory \\{ \\(ctx: ComponentContext, lessonId: LessonId, mode: Difficulty\\)|LessonRunnerComponentFactory \\{\" android apps shared -g '*.kt'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:212:        single<LessonRunnerComponentFactory> {
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:213:            LessonRunnerComponentFactory { _, _, _ -> error("Not wired in KoinModuleWiringTest") }
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:19:        factory { (ctx: ComponentContext, lessonId: LessonId, mode: Difficulty) ->
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:37:        single<LessonRunnerComponentFactory> {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:38:            LessonRunnerComponentFactory { ctx, lessonId, mode ->
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt:7:fun interface LessonRunnerComponentFactory {
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:78:            lessonRunnerFactory = LessonRunnerComponentFactory { _, _, _ -> error("Not expected") },
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:86:            lessonRunnerFactory = LessonRunnerComponentFactory { _, _, _ -> error("Not expected") },
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:269:                lessonRunnerFactory = LessonRunnerComponentFactory { _, _, _ -> error("Not expected") },

exec
/bin/bash -lc "rg -n \"DifficultyConverter|addTypeConverter\\(Difficulty|TypeConverters\\(.*Difficulty|@TypeConverter.*Difficulty\" . -g '"'!docs/features/lesson-runner/03-decisions.md'"' -g '"'!docs/features/lesson-runner/implementation.md'"' -g '"'!docs/features/lesson-runner/_codex-review/**'"'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
./apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:605:     * DifficultyConverter removed per ADR-LR-18; mapper handles Difficulty↔Int.
./shared/core/persistence/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/TypeConvertersPhase02Test.kt:13: * Note: MT-05 (DifficultyConverter) removed per ADR-LR-18 — converter deleted, mapper handles Difficulty↔Int.
./docs/features/lesson-runner/plan/README.md:112:- `shared/core/persistence/`: `LessonAttemptEntity.kt`, `LessonRatingSubmittedLocalEntity.kt`, `LessonAttemptDao.kt`, `LessonRatingLocalDao.kt`, `DifficultyConverter.kt`, `TopParticipantListConverter.kt`, `Migration3to4.kt`
./docs/features/lesson-runner/plan/phase-02/tests.md:58:- **Given:** `DifficultyConverter`; `Difficulty.EASY`
./docs/features/lesson-runner/plan/phase-02/backend.md:101:## New Converter: `DifficultyConverter`
./docs/features/lesson-runner/plan/phase-02/backend.md:103:- **Файл:** `shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/converter/DifficultyConverter.kt`
./docs/features/lesson-runner/plan/phase-02/backend.md:105:- **Сигнатура:** `@ProvidedTypeConverter class DifficultyConverter`
./docs/features/lesson-runner/plan/phase-02/backend.md:108:  - `@TypeConverter fun toDb(difficulty: Difficulty): String = difficulty.name`
./docs/features/lesson-runner/plan/phase-02/backend.md:109:  - `@TypeConverter fun fromDb(value: String): Difficulty = Difficulty.valueOf(value)`
./docs/features/lesson-runner/plan/phase-02/backend.md:111:  - `@ProvidedTypeConverter` — добавляется через `.addTypeConverter(DifficultyConverter())` в Room builder
./docs/features/lesson-runner/plan/phase-02/backend.md:172:- **Сигнатура:** `@Database(entities = [...existing..., LessonAttemptEntity::class, LessonRatingSubmittedLocalEntity::class], version = 4, exportSchema = true) @TypeConverters(DifficultyConverter::class, TopParticipantListConverter::class) abstract class AppDatabase : RoomDatabase()`
./docs/features/lesson-runner/plan/phase-02/backend.md:177:  - `@TypeConverters(DifficultyConverter::class, TopParticipantListConverter::class)` — per Room KMP pattern
./docs/features/lesson-runner/plan/phase-02/backend.md:198:  - Добавить `.addTypeConverter(DifficultyConverter())`
./docs/features/lesson-runner/plan/phase-02/backend.md:205:- **Depends on:** `MIGRATION_3_4`, `DifficultyConverter`, `TopParticipantListConverter`, `LessonAttemptDao`, `LessonRatingLocalDao`
./docs/features/lesson-runner/plan/phase-02/overview.md:72:- `shared/core/persistence/src/commonMain/kotlin/.../DifficultyConverter.kt` — per ADR-LR-18 (Room KMP rejects unused TypeConverters; mapper handles Difficulty↔Int)
./docs/features/lesson-runner/plan/phase-02/overview.md:86:6. MT-05: ~~`DifficultyConverter` round-trip~~ — **REMOVED (ADR-LR-18)**, converter удалён
./docs/features/lesson-runner/plan/phase-02/overview.md:128:- **REMOVED (ADR-LR-18)**: `DifficultyConverter` удалён — Room KMP 2.7+ отклоняет converter без Entity-binding (`IllegalArgumentException: Unexpected type converter`). Mapper обрабатывает `Difficulty ↔ Int`: `isHard = if (mode == HARD) 1 else 0`. Plan инвариант про `DifficultyConverter` superseded ADR-LR-18.
./docs/features/lesson-runner/plan/phase-07/tests.md:62:- **Then:** no Room exception about missing TypeConverter; `DifficultyConverter` and `TopParticipantListConverter` registered
./docs/features/lesson-runner/plan/phase-07/overview.md:78:8. IT-09g: `AppDatabase` builder — `DifficultyConverter` + `TopParticipantListConverter` registered (Room не падает при query)
./docs/features/lesson-runner/04-testing.md:207:| IT-09g | `AppDatabase` builder: `DifficultyConverter` + `TopParticipantListConverter` оба registered через `addTypeConverter` (Room не падает при query) | AC-53 |
./docs/features/lesson-runner/04-testing.md:222:| MT-05 | DifficultyConverter roundtrip: EASY → "EASY" → EASY |
./docs/features/lesson-runner/06-api-contract.md:714:### DifficultyConverter
./docs/features/lesson-runner/06-api-contract.md:718:class DifficultyConverter {
./docs/features/lesson-runner/06-api-contract.md:719:    @TypeConverter fun toDb(value: Difficulty): String = value.name
./docs/features/lesson-runner/06-api-contract.md:720:    @TypeConverter fun fromDb(value: String): Difficulty = Difficulty.valueOf(value)
./docs/features/lesson-runner/08-storage-model.md:130:@TypeConverters(DifficultyConverter::class, TopParticipantListConverter::class)
./docs/features/lesson-runner/08-storage-model.md:138:**PersistenceModule.kt**: `addMigrations(MIGRATION_3_4)` + `addTypeConverter(DifficultyConverter())` + `addTypeConverter(TopParticipantListConverter())`.
./docs/features/lesson-runner/quality-scorecard.md:39:- ADR-LR-18: DifficultyConverter removed (mapper-based conversion, phase-02)
./docs/features/lesson-runner/01-architecture.md:306:    class DifficultyConverter {
./docs/features/lesson-runner/05-prior-art.md:513:        .addTypeConverter(DifficultyTypeConverter())  // обязательно .addTypeConverter()

exec
/bin/bash -lc 'nl -ba android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.di
     2	
     3	import com.arkivanov.decompose.ComponentContext
     4	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
     5	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultQuizzesComponent
     6	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
     7	import org.koin.dsl.module
     8	
     9	val quizzesPresentationModule =
    10	    module {
    11	        factory<QuizzesComponent> { (ctx: ComponentContext) ->
    12	            DefaultQuizzesComponent(
    13	                componentContext = ctx,
    14	                questRepository = get(),
    15	                sectionRepository = get(),
    16	                themeRepository = get(),
    17	                lessonRepository = get(),
    18	                lessonAttemptRepository = get(),
    19	                authRepository = get(),
    20	                lessonRunnerFactory = get<LessonRunnerComponentFactory>(),
    21	            )
    22	        }
    23	    }

exec
/bin/bash -lc "rg -n \"@Ignore|Ignore\\(\" android/feature/lesson-runner/presentation/src/androidTest android/feature/quizzes-screen/presentation/src/androidTest android/feature/lesson-runner/presentation/src/test android/feature/quizzes-screen/presentation/src/test shared/feature/lesson-runner/domain/src/commonTest shared/feature/lesson-runner/data/src/commonTest apps/android-next/src/test" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:173:    @Ignore("OQ-CT10: production seam missing — QuestionProgressHeader does not call component.onTimeout()")
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:401:    @Ignore("Phase-06 scope: belongs to LessonItemCard, not LessonRunnerScreen")
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:408:    @Ignore("Phase-06 scope: hardUnlocked checkbox in LessonItemCard")
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:415:    @Ignore("Phase-06 scope: hardUnlocked checkbox in LessonItemCard")

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt | sed -n '140,210p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
   140	    // Spec AC-20
   141	    @Test
   142	    fun ct04_resultState_resultScreen_visible() {
   143	        val fakeComponent = RunFakeComponent(MutableStateFlow(resultState(percentScore = 75)))
   144	
   145	        composeTestRule.setContent {
   146	            SchoolQuizTheme {
   147	                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
   148	            }
   149	        }
   150	
   151	        composeTestRule.onNodeWithText("75%").assertIsDisplayed()
   152	        composeTestRule.onNodeWithText("Завершить").assertIsDisplayed()
   153	    }
   154	
   155	    // --- CT-05: GIVEN Result state WHEN rendered THEN Завершить button present ---
   156	    // Spec AC-20
   157	    @Test
   158	    fun ct05_finishButton_present_on_result() {
   159	        val fakeComponent = RunFakeComponent(MutableStateFlow(resultState()))
   160	
   161	        composeTestRule.setContent {
   162	            SchoolQuizTheme {
   163	                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
   164	            }
   165	        }
   166	
   167	        composeTestRule.onNodeWithText("Завершить").assertIsDisplayed()
   168	    }
   169	
   170	    // --- CT-10: GIVEN expired deadline WHEN rendered THEN onTimeout invoked ---
   171	    // Open Question OQ-CT10: QuestionProgressHeader.kt does not invoke component.onTimeout()
   172	    // when the deadline expires — production seam missing. Test will fail until fixed.
   173	    @Ignore("OQ-CT10: production seam missing — QuestionProgressHeader does not call component.onTimeout()")
   174	    @Test
   175	    fun ct10_timer_expired_onTimeout_invoked() {
   176	        val expiredDeadline = System.currentTimeMillis() - 100L
   177	        val fakeComponent = RunFakeComponent(
   178	            MutableStateFlow(singleChoiceQuestion(deadlineMs = expiredDeadline, isPaused = false)),
   179	        )
   180	
   181	        composeTestRule.setContent {
   182	            SchoolQuizTheme {
   183	                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
   184	            }
   185	        }
   186	        composeTestRule.waitForIdle()
   187	
   188	        assertTrue(fakeComponent.timeoutCount > 0)
   189	    }
   190	
   191	    // --- CT-11: GIVEN hard Question WHEN rendered THEN FLAG_SECURE set in window ---
   192	    // Spec AC-11
   193	    @Test
   194	    fun ct11_hard_mode_flagSecure_set() {
   195	        val fakeComponent = RunFakeComponent(
   196	            MutableStateFlow(singleChoiceQuestion(isHard = true)),
   197	        )
   198	
   199	        composeTestRule.setContent {
   200	            SchoolQuizTheme {
   201	                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
   202	            }
   203	        }
   204	        composeTestRule.waitForIdle()
   205	
   206	        val flags = composeTestRule.activity.window.attributes.flags
   207	        assertTrue(flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
   208	    }
   209	
   210	    // --- CT-12: GIVEN hard state transitions to Loading WHEN rendered THEN FLAG_SECURE cleared ---

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/RunFakeComponent.kt | sed -n '1,130p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
     2	
     3	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
     4	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
     5	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
     6	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
     7	import kotlinx.coroutines.channels.Channel
     8	import kotlinx.coroutines.flow.Flow
     9	import kotlinx.coroutines.flow.MutableStateFlow
    10	import kotlinx.coroutines.flow.StateFlow
    11	import kotlinx.coroutines.flow.receiveAsFlow
    12	
    13	class RunFakeComponent(
    14	    private val _uiState: MutableStateFlow<RunnerUiState>,
    15	    private val _events: Channel<RunnerEvent> = Channel(Channel.BUFFERED),
    16	) : LessonRunnerRootComponent {
    17	
    18	    override val uiState: StateFlow<RunnerUiState> = _uiState
    19	    override val events: Flow<RunnerEvent> = _events.receiveAsFlow()
    20	
    21	    var lastAnswer: UserAnswerDraft? = null
    22	        private set
    23	    var lastRating: Int? = null
    24	        private set
    25	    var continueCount: Int = 0
    26	        private set
    27	    var exitCount: Int = 0
    28	        private set
    29	    var crossButtonTapCount: Int = 0
    30	        private set
    31	    var crossConfirmedCount: Int = 0
    32	        private set
    33	    var crossCancelledCount: Int = 0
    34	        private set
    35	    var timeoutCount: Int = 0
    36	        private set
    37	    var finishCount: Int = 0
    38	        private set
    39	    var backCount: Int = 0
    40	        private set
    41	
    42	    override fun onAnswer(answer: UserAnswerDraft) {
    43	        lastAnswer = answer
    44	    }
    45	
    46	    override fun onTimeout() {
    47	        timeoutCount++
    48	    }
    49	
    50	    override fun onContinue() {
    51	        continueCount++
    52	    }
    53	
    54	    override fun onExit() {
    55	        exitCount++
    56	    }
    57	
    58	    override fun onCrossButtonTap() {
    59	        crossButtonTapCount++
    60	    }
    61	
    62	    override fun onCrossConfirmed() {
    63	        crossConfirmedCount++
    64	    }
    65	
    66	    override fun onCrossCancelled() {
    67	        crossCancelledCount++
    68	    }
    69	
    70	    override fun onSubmitRating(rating: Int) {
    71	        lastRating = rating
    72	    }
    73	
    74	    override fun onFinish() {
    75	        finishCount++
    76	    }
    77	
    78	    override fun onBack() {
    79	        backCount++
    80	    }
    81	
    82	    fun emit(event: RunnerEvent) {
    83	        _events.trySend(event)
    84	    }
    85	
    86	    fun setState(state: RunnerUiState) {
    87	        _uiState.value = state
    88	    }
    89	}

exec
/bin/bash -lc "rg -n \"rotation|configuration|draft|currentDraft|preserve|localSelected|MultipleChoice|FillBlank|Ordering\" android/feature/lesson-runner/presentation/src/test android/feature/lesson-runner/presentation/src/androidTest shared/feature/lesson-runner/domain/src/commonTest -g '*.kt'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:108:    fun `given lesson version changes during session then attempt preserves start version`() = runTest {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:26: * Tests pool empty, all invalid, invalid filtered, configuration change simulation,
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:96:    // ── Test 61: configuration change simulation ───────────────────────────────
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:100:        // Spec scenario #61: configuration change → state preserved (verified via equality of data class)
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:107:        // instanceKeeper preserves the data class by reference/serialization
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:110:        assertEquals(original.seed, restored.seed, "Spec scenario #61: seed preserved")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:111:        assertEquals(original.indexInPool, restored.indexInPool, "indexInPool preserved")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:112:        assertEquals(original.codeAnswer.raw, restored.codeAnswer.raw, "codeAnswer preserved")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:113:        assertEquals(original.deadlineMs, restored.deadlineMs, "deadlineMs preserved")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:114:        assertEquals(original.currentDraftAnswer, restored.currentDraftAnswer, "draft preserved")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:34:    // ── MultipleChoice (tests 3-6) ────────────────────────────────────────────
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:37:    fun `given MultipleChoice correctIds ABC 5 options when selected AB then digit 6`() {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:43:        val answer = UserAnswer.MultipleChoiceAnswer(selected = setOf(optId("A"), optId("B")))
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:49:    fun `given MultipleChoice when selected all correct then digit 9`() {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:55:        val answer = UserAnswer.MultipleChoiceAnswer(selected = setOf(optId("A"), optId("B"), optId("C")))
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:61:    fun `given MultipleChoice correctIds AB when selected CD then digit 1`() {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:67:        val answer = UserAnswer.MultipleChoiceAnswer(selected = setOf(optId("C"), optId("D")))
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:73:    fun `given MultipleChoice when selected empty then digit 1`() {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:79:        val answer = UserAnswer.MultipleChoiceAnswer(selected = emptySet())
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:84:    // ── Ordering (tests 7-9) ──────────────────────────────────────────────────
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:87:    fun `given Ordering ABCD when userOrder ABCD then digit 9`() {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:90:        val answer = UserAnswer.OrderingAnswer(order = listOf("A", "B", "C", "D").map { optId(it) })
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:96:    fun `given Ordering ABCD when userOrder ACBD then digit 5`() {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:99:        val answer = UserAnswer.OrderingAnswer(order = listOf("A", "C", "B", "D").map { optId(it) })
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:105:    fun `given Ordering ABCD when userOrder DCBA then digit 1`() {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:108:        val answer = UserAnswer.OrderingAnswer(order = listOf("D", "C", "B", "A").map { optId(it) })
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:113:    // ── FillBlank (tests 10-12) ───────────────────────────────────────────────
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:116:    fun `given FillBlank 3 blanks when all 3 correct then digit 9`() {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:122:        val answer = UserAnswer.FillBlankAnswer(
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:130:    fun `given FillBlank 3 blanks when 1 correct then digit 4`() {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:136:        val answer = UserAnswer.FillBlankAnswer(
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:144:    fun `given FillBlank 1 blank when wrong then digit 1`() {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:150:        val answer = UserAnswer.FillBlankAnswer(
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:36:    // ── MultipleChoice (test 45) ──────────────────────────────────────────────
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:39:    fun `given MultipleChoice 5 options correctSize 3 when auto-random then advances state`() {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:53:    // ── Ordering (test 46) ────────────────────────────────────────────────────
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:56:    fun `given Ordering 4 items when auto-random then result advances state and records score`() {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:64:            "Spec scenario #46: Ordering auto-answer → score digit recorded",
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:68:    // ── FillBlank (test 47) ───────────────────────────────────────────────────
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:71:    fun `given FillBlank 3 blanks 5 candidates when auto-random then advances state`() {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:82:            "Spec scenario #47: FillBlank auto-answer → score digit recorded",
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:48:): QuestionContent.MultipleChoice = QuestionContent.MultipleChoice(
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:61:): QuestionContent.Ordering = QuestionContent.Ordering(
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:74:): QuestionContent.FillBlank = QuestionContent.FillBlank(
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:157:    currentDraftAnswer = null,
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:507:        // Simulate configuration change (e.g. rotation)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:41: * Integration tests for DefaultLessonRunnerRootComponent — state holder rotation and lifecycle.
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:44: * IT-02: rotation reuses RunnerStateHolder via same instanceKeeper (AC-35)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:45: * IT-03: non-rotation destroy creates fresh state in new component (AC-36)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:48: * the Essenty lifecycle, matching Decompose's rotation behavior.
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:168:        currentDraftAnswer = null,
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:172:    // ── IT-02: rotation reuses RunnerStateHolder ──────────────────────────────
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:176:     *        WHEN simulate rotation (LifecycleRegistry destroy + new LifecycleRegistry, same instanceKeeper)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:177:     *        THEN new component has uiState.indexInPool == 3 (state preserved via shared stateHolder)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:178:     *        AND fakeStart.callCount == 1 (no duplicate StartLessonAttemptUseCase call on rotation)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:180:     * AC-35: Configuration change (rotation) preserves in-progress attempt state.
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:183:    fun `rotation_component_reuses_runnerStateHolder`() {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:197:        // Simulate rotation: destroy lifecycle1 (NOT instanceKeeper)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:212:            "startAttemptUseCase must NOT be called again on rotation (instanceKeeper reuse)",
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:216:    // ── IT-03: non-rotation destroy → state NOT preserved ─────────────────────
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:220:     *        WHEN instanceKeeper.destroy() called (non-rotation: process navigation back)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:221:     *        THEN new component with fresh instanceKeeper calls startAttemptUseCase again (state not preserved)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:238:        // Full destroy (non-rotation): lifecycle + instanceKeeper both destroyed
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:252:            "startAttemptUseCase must be called again when instanceKeeper is fresh (non-rotation)",
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:256:    // ── IT-02b: rotation preserves indexInPool exactly ───────────────────────
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:259:     * IT-02b: Verifies that the exact indexInPool value from before rotation is preserved.
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:266:    fun `rotation_indexInPool_preserved_via_same_instanceKeeper`() {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:281:        // component2 must see the preserved state (indexInPool ≥ 2, no fresh start)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:286:            "startAttemptUseCase must be called only once total across rotation",
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:184:        currentDraftAnswer = null,
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:650:            currentDraftAnswer = null,

exec
/bin/bash -lc 'nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/QuestionUiState.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state
     2	
     3	sealed interface QuestionUiState {
     4	    val questionText: String
     5	    val hasImage: Boolean
     6	
     7	    data class SingleChoice(
     8	        override val questionText: String,
     9	        override val hasImage: Boolean,
    10	        val imageUrl: String?,
    11	        val options: List<OptionUi>,
    12	        val selectedOptionId: String?,
    13	    ) : QuestionUiState
    14	
    15	    data class MultipleChoice(
    16	        override val questionText: String,
    17	        override val hasImage: Boolean,
    18	        val imageUrl: String?,
    19	        val options: List<OptionUi>,
    20	        val selectedIds: Set<String>,
    21	    ) : QuestionUiState
    22	
    23	    data class Ordering(
    24	        override val questionText: String,
    25	        override val hasImage: Boolean,
    26	        val imageUrl: String?,
    27	        val items: List<OptionUi>,
    28	    ) : QuestionUiState
    29	
    30	    data class FillBlank(
    31	        override val questionText: String,
    32	        override val hasImage: Boolean,
    33	        val imageUrl: String?,
    34	        val templateParts: List<TemplatePart>,
    35	        val filledValues: Map<Int, String>,
    36	        val candidates: List<OptionUi> = emptyList(),
    37	    ) : QuestionUiState
    38	}
    39	
    40	data class OptionUi(val id: String, val text: String)
    41	
    42	sealed interface TemplatePart {
    43	    data class Text(val content: String) : TemplatePart
    44	
    45	    data class Blank(val index: Int, val placeholder: String, val blankId: String) : TemplatePart
    46	}

exec
/bin/bash -lc "nl -ba apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.apps.android_next
     2	
     3	import com.arkivanov.decompose.Child
     4	import com.arkivanov.decompose.DefaultComponentContext
     5	import com.arkivanov.decompose.router.stack.ChildStack
     6	import com.arkivanov.decompose.value.MutableValue
     7	import com.arkivanov.decompose.value.Value
     8	import com.arkivanov.essenty.lifecycle.LifecycleRegistry
     9	import com.arkivanov.essenty.lifecycle.destroy
    10	import com.arkivanov.essenty.lifecycle.resume
    11	import com.arkivanov.essenty.lifecycle.stop
    12	import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
    13	import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.DefaultRootComponent
    14	import com.tpov.schoolquiz.android.feature.app_shell.presentation.di.appShellPresentationModule
    15	import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsComponent
    16	import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsUiState
    17	import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsComponent
    18	import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsUiState
    19	import com.tpov.schoolquiz.android.feature.quest.presentation.di.questPresentationModule
    20	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
    21	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
    22	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
    23	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.di.quizzesPresentationModule
    24	import com.tpov.schoolquiz.shared.core.catalog.domain.di.catalogDomainModule
    25	import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
    26	import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
    27	import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
    28	import com.tpov.schoolquiz.shared.core.persistence.UserStatsDao
    29	import com.tpov.schoolquiz.shared.core.persistence.UserStatsEntity
    30	import com.tpov.schoolquiz.shared.core.stats.RawUserStats
    31	import com.tpov.schoolquiz.shared.core.stats.UserStatsDataSource
    32	import com.tpov.schoolquiz.shared.feature.app_shell.data.AuthRepositoryImpl
    33	import com.tpov.schoolquiz.shared.feature.app_shell.data.UserStatsRepositoryImpl
    34	import com.tpov.schoolquiz.shared.feature.app_shell.data.di.appShellDataModule
    35	import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DeepLink
    36	import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
    37	import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RootEvent
    38	import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
    39	import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
    40	import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
    41	import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.InitializeAppShellUseCase
    42	import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.NavigateUseCase
    43	import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.ObserveAppShellStateUseCase
    44	import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.OnTabRetapUseCase
    45	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
    46	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
    47	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
    48	import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
    49	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
    50	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
    51	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
    52	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
    53	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.di.lessonRunnerPresentationModule
    54	import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptDao
    55	import com.tpov.schoolquiz.shared.core.persistence.LessonRatingLocalDao
    56	import com.tpov.schoolquiz.shared.core.persistence.StringSetConverter
    57	import com.tpov.schoolquiz.shared.core.persistence.TopParticipantListConverter
    58	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
    59	import com.tpov.schoolquiz.shared.core.question_schema.KotlinxSerializationQuestionContentParser
    60	import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser
    61	import com.tpov.schoolquiz.shared.core.question_schema.di.questionSchemaModule
    62	import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.lessonRunnerDataModule
    63	import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.lessonRunnerDomainKoinAdapter
    64	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.AttemptIdProvider
    65	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RandomSeedProvider
    66	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
    67	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
    68	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
    69	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
    70	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
    71	import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
    72	import com.tpov.schoolquiz.shared.feature.quest.domain.di.questDomainModule
    73	import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
    74	import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
    75	import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
    76	import com.tpov.schoolquiz.shared.feature.section.domain.model.Section
    77	import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
    78	import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
    79	import com.tpov.schoolquiz.shared.feature.theme.domain.model.Theme
    80	import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
    81	import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
    82	import androidx.work.WorkManager
    83	import kotlinx.coroutines.ExperimentalCoroutinesApi
    84	import kotlinx.coroutines.flow.Flow
    85	import kotlinx.coroutines.flow.MutableStateFlow
    86	import kotlinx.coroutines.flow.asStateFlow
    87	import kotlinx.coroutines.flow.emptyFlow
    88	import kotlinx.coroutines.flow.flowOf
    89	import kotlinx.coroutines.flow.toList
    90	import kotlinx.coroutines.launch
    91	import kotlinx.coroutines.test.TestCoroutineScheduler
    92	import kotlinx.coroutines.test.UnconfinedTestDispatcher
    93	import kotlinx.coroutines.test.resetMain
    94	import kotlinx.coroutines.test.runTest
    95	import kotlinx.coroutines.test.setMain
    96	import org.junit.After
    97	import org.junit.Before
    98	import org.junit.Test
    99	import org.koin.core.context.startKoin
   100	import org.koin.core.context.stopKoin
   101	import org.koin.core.parameter.parametersOf
   102	import org.koin.dsl.module
   103	import org.koin.test.KoinTest
   104	import org.mockito.Mockito.mock
   105	import kotlin.test.assertEquals
   106	import kotlin.test.assertNotNull
   107	import kotlin.test.assertNull
   108	import kotlin.test.assertTrue
   109	
   110	/**
   111	 * Smoke tests: Koin module graph resolves without errors.
   112	 *
   113	 * D2 coverage: firebaseModule replaced by testDataSourceModule + appShellDataModule.
   114	 * H5 fix (phase-01): appShellPresentationModule created in phase-04.
   115	 * Phase-07: full-stack wiring (all 3 modules) verified here.
   116	 */
   117	@OptIn(ExperimentalCoroutinesApi::class)
   118	class KoinModuleWiringTest : KoinTest {
   119	
   120	    private val testScheduler = TestCoroutineScheduler()
   121	    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
   122	
   123	    private val testDataSourceModule = module {
   124	        single<UserStatsDataSource> {
   125	            object : UserStatsDataSource {
   126	                override fun observeRaw() = emptyFlow<RawUserStats>()
   127	                override suspend fun fetchRaw() = RawUserStats()
   128	            }
   129	        }
   130	    }
   131	
   132	    private val testDaoModule = module {
   133	        single<UserStatsDao> {
   134	            object : UserStatsDao {
   135	                override fun observeByUid(uid: String) = emptyFlow<UserStatsEntity?>()
   136	                override suspend fun findByUid(uid: String): UserStatsEntity? = null
   137	                override suspend fun upsert(entity: UserStatsEntity) {}
   138	                override suspend fun updateDeveloperLevel(uid: String, value: Int) {}
   139	            }
   140	        }
   141	    }
   142	
   143	    private val testWorkManagerModule = module {
   144	        single<WorkManager> { mock(WorkManager::class.java) }
   145	    }
   146	
   147	    private val testRepositoryStubsModule = module {
   148	        single<QuestRepository> {
   149	            object : QuestRepository {
   150	                override fun observeMyQuests(authorUid: String, catalogId: CatalogId?) =
   151	                    flowOf(emptyList<Quest>())
   152	                override fun observeByCatalog(catalogId: CatalogId, shelf: String) =
   153	                    flowOf(emptyList<Quest>())
   154	                override fun observeByShelf(shelf: String) = flowOf(emptyList<Quest>())
   155	                override suspend fun getById(id: QuestId): Quest? = null
   156	                override suspend fun refreshFromRemote(
   157	                    currentUserUid: String?,
   158	                    availableShelves: Set<String>,
   159	                    catalogIdsToSync: Set<CatalogId>,
   160	                    cursor: Long,
   161	                ): Result<Set<QuestId>> = Result.success(emptySet())
   162	            }
   163	        }
   164	        single<CatalogRepository> {
   165	            object : CatalogRepository {
   166	                override fun observeAll() = flowOf(emptyList<Catalog>())
   167	                override suspend fun refreshFromRemote(): Result<Set<CatalogId>> =
   168	                    Result.success(emptySet())
   169	                override suspend fun getById(id: CatalogId): Catalog? = null
   170	            }
   171	        }
   172	        single<SectionRepository> {
   173	            object : SectionRepository {
   174	                override fun observeByQuest(questId: QuestId) = flowOf(emptyList<Section>())
   175	                override suspend fun getById(id: SectionId): Section? = null
   176	                override suspend fun refreshByParents(
   177	                    questIds: Set<QuestId>,
   178	                    cursor: Long,
   179	                ): Result<Set<SectionId>> = Result.success(emptySet())
   180	                override suspend fun getLocalContentsVersion(id: SectionId): Long? = null
   181	            }
   182	        }
   183	        single<ThemeRepository> {
   184	            object : ThemeRepository {
   185	                override fun observeBySection(sectionId: SectionId) = flowOf(emptyList<Theme>())
   186	                override suspend fun getById(id: ThemeId): Theme? = null
   187	                override suspend fun refreshByParents(
   188	                    sectionIds: Set<SectionId>,
   189	                    cursor: Long,
   190	                ): Result<Set<ThemeId>> = Result.success(emptySet())
   191	                override suspend fun getLocalContentsVersion(id: ThemeId): Long? = null
   192	            }
   193	        }
   194	        single<LessonRepository> {
   195	            object : LessonRepository {
   196	                override fun observeByTheme(themeId: ThemeId) = flowOf(emptyList<Lesson>())
   197	                override suspend fun getById(id: LessonId): Lesson? = null
   198	                override suspend fun refreshByParents(
   199	                    themeIds: Set<ThemeId>,
   200	                    cursor: Long,
   201	                ): Result<Set<LessonId>> = Result.success(emptySet())
   202	                override suspend fun getLocalContentsVersion(id: LessonId): Long? = null
   203	            }
   204	        }
   205	        single<LessonAttemptRepository> {
   206	            object : LessonAttemptRepository {
   207	                override suspend fun save(attempt: Attempt): Result<Unit> = Result.success(Unit)
   208	                override fun observeByLesson(userId: String, lessonId: LessonId): Flow<List<Attempt>> = flowOf(emptyList())
   209	                override fun observeAllByUser(userId: String): Flow<List<Attempt>> = flowOf(emptyList())
   210	            }
   211	        }
   212	        single<LessonRunnerComponentFactory> {
   213	            LessonRunnerComponentFactory { _, _, _ -> error("Not wired in KoinModuleWiringTest") }
   214	        }
   215	    }
   216	
   217	    @Before
   218	    fun setUp() {
   219	        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
   220	    }
   221	
   222	    @After
   223	    fun tearDown() {
   224	        stopKoin()
   225	        kotlinx.coroutines.Dispatchers.resetMain()
   226	    }
   227	
   228	    // -----------------------------------------------------------------------
   229	    // Phase-01 D2: appShellDataModule resolves UserStatsRepository
   230	    // -----------------------------------------------------------------------
   231	
   232	    @Test
   233	    fun `D2 appShellDataModule resolves UserStatsRepository given UserStatsDataSource`() {
   234	        startKoin {
   235	            modules(testDataSourceModule, testDaoModule, appShellDataModule())
   236	        }
   237	
   238	        val repo = getKoin().get<UserStatsRepository>()
   239	        assertNotNull(repo)
   240	    }
   241	
   242	    @Test
   243	    fun `D2 UserStatsRepository resolves as UserStatsRepositoryImpl`() {
   244	        startKoin {
   245	            modules(testDataSourceModule, testDaoModule, appShellDataModule())
   246	        }
   247	
   248	        val repo = getKoin().get<UserStatsRepository>()
   249	        assertNotNull(repo)
   250	        assertTrue(repo is UserStatsRepositoryImpl)
   251	    }
   252	
   253	    // -----------------------------------------------------------------------
   254	    // Phase-01 (home-and-my-quests Decision #42 + Codex Round 3 B3): AuthRepository
   255	    // -----------------------------------------------------------------------
   256	
   257	    @Test
   258	    fun `appShellDataModule resolves AuthRepository given currentUidFlow`() {
   259	        startKoin {
   260	            modules(testDataSourceModule, testDaoModule, appShellDataModule())

exec
/bin/bash -lc "nl -ba apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt | sed -n '520,650p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
   520	        assertNotNull(getKoin().get<RandomSeedProvider>())
   521	        assertNotNull(getKoin().get<RatingIdProvider>())
   522	    }
   523	
   524	    // ── IT-09d ────────────────────────────────────────────────────────────────
   525	
   526	    /**
   527	     * IT-09d: GIVEN lessonRunnerDomainKoinAdapter WHEN get<CompleteAttemptUseCase> / AbortAttemptUseCase / SubmitLessonRatingUseCase
   528	     * THEN each resolves without exception.
   529	     * Spec: docs/features/lesson-runner/plan/phase-07/tests.md §IT-09d
   530	     */
   531	    @Test
   532	    fun `it09d useCases CompleteAttemptUseCase AbortAttemptUseCase SubmitLessonRatingUseCase resolve`() {
   533	        startKoin {
   534	            modules(
   535	                testLessonAttemptDaoStub,
   536	                testLessonRatingLocalDaoStub,
   537	                testRunnerRepositoryStubs,
   538	                lessonRunnerDataModule,
   539	                lessonRunnerDomainKoinAdapter,
   540	                questionSchemaModule,
   541	            )
   542	        }
   543	        assertNotNull(getKoin().get<CompleteAttemptUseCase>())
   544	        assertNotNull(getKoin().get<AbortAttemptUseCase>())
   545	        assertNotNull(getKoin().get<SubmitLessonRatingUseCase>())
   546	    }
   547	
   548	    // ── IT-09e ────────────────────────────────────────────────────────────────
   549	
   550	    /**
   551	     * IT-09e: GIVEN lessonRunnerPresentationModule + deps WHEN get<LessonRunnerRootComponent>(parametersOf(ctx, LessonId("l1"), EASY))
   552	     * THEN resolves to DefaultLessonRunnerRootComponent without exception.
   553	     * Spec: docs/features/lesson-runner/plan/phase-07/tests.md §IT-09e
   554	     */
   555	    @Test
   556	    fun `it09e lessonRunnerRootComponent resolves with parametersOf ctx lessonId mode`() {
   557	        val lifecycle = LifecycleRegistry()
   558	        lifecycle.resume()
   559	        val ctx = DefaultComponentContext(lifecycle)
   560	
   561	        startKoin {
   562	            modules(
   563	                testLessonAttemptDaoStub,
   564	                testLessonRatingLocalDaoStub,
   565	                testRunnerRepositoryStubs,
   566	                lessonRunnerDataModule,
   567	                lessonRunnerDomainKoinAdapter,
   568	                lessonRunnerPresentationModule,
   569	                questionSchemaModule,
   570	            )
   571	        }
   572	
   573	        try {
   574	            val component = getKoin().get<LessonRunnerRootComponent> {
   575	                parametersOf(ctx, LessonId("l1"), Difficulty.EASY)
   576	            }
   577	            assertNotNull(component)
   578	            assertTrue(component is DefaultLessonRunnerRootComponent)
   579	        } finally {
   580	            lifecycle.stop()
   581	            lifecycle.destroy()
   582	        }
   583	    }
   584	
   585	    // ── IT-09f ────────────────────────────────────────────────────────────────
   586	
   587	    /**
   588	     * IT-09f: GIVEN questionSchemaModule WHEN get<QuestionContentParser>()
   589	     * THEN resolves to KotlinxSerializationQuestionContentParser.
   590	     * Spec: docs/features/lesson-runner/plan/phase-07/tests.md §IT-09f
   591	     */
   592	    @Test
   593	    fun `it09f questionContentParser resolves to KotlinxSerializationQuestionContentParser`() {
   594	        startKoin { modules(questionSchemaModule) }
   595	        val parser = getKoin().get<QuestionContentParser>()
   596	        assertNotNull(parser)
   597	        assertTrue(parser is KotlinxSerializationQuestionContentParser)
   598	    }
   599	
   600	    // ── IT-09g ────────────────────────────────────────────────────────────────
   601	
   602	    /**
   603	     * IT-09g: TypeConverter classes instantiate without exception (JVM structural check).
   604	     * persistenceModule uses androidContext() — AppDatabase cannot be built in JVM tests.
   605	     * DifficultyConverter removed per ADR-LR-18; mapper handles Difficulty↔Int.
   606	     * TopParticipantListConverter and StringSetConverter registered in persistenceModule.
   607	     * Spec: docs/features/lesson-runner/plan/phase-07/tests.md §IT-09g
   608	     */
   609	    @Test
   610	    fun `it09g appDatabase typeConverters TopParticipantListConverter StringSetConverter are instantiatable`() {
   611	        val topParticipantListConverter = TopParticipantListConverter()
   612	        val stringSetConverter = StringSetConverter()
   613	        assertNotNull(topParticipantListConverter)
   614	        assertNotNull(stringSetConverter)
   615	    }
   616	
   617	    // ── IT-09h ────────────────────────────────────────────────────────────────
   618	
   619	    /**
   620	     * IT-09h: GIVEN lessonRunnerPresentationModule WHEN get<LessonRunnerComponentFactory>()
   621	     * THEN resolves without NoBeanDefinitionFoundException (single binding from presentation module).
   622	     * Spec: docs/features/lesson-runner/plan/phase-07/tests.md §IT-09h
   623	     */
   624	    @Test
   625	    fun `it09h lessonRunnerComponentFactory resolves as single binding`() {
   626	        startKoin { modules(lessonRunnerPresentationModule) }
   627	        val factory = getKoin().get<LessonRunnerComponentFactory>()
   628	        assertNotNull(factory)
   629	    }
   630	}

exec
/bin/bash -lc 'rg -n "testRepositoryStubsModule|lessonRunnerPresentationModule|quizzesPresentationModule" apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
23:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.di.quizzesPresentationModule
53:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.di.lessonRunnerPresentationModule
147:    private val testRepositoryStubsModule = module {
287:    //   + questPresentationModule + quizzesPresentationModule + domain + repo stubs
303:                testRepositoryStubsModule,
308:                quizzesPresentationModule,
551:     * IT-09e: GIVEN lessonRunnerPresentationModule + deps WHEN get<LessonRunnerRootComponent>(parametersOf(ctx, LessonId("l1"), EASY))
568:                lessonRunnerPresentationModule,
620:     * IT-09h: GIVEN lessonRunnerPresentationModule WHEN get<LessonRunnerComponentFactory>()
626:        startKoin { modules(lessonRunnerPresentationModule) }

exec
/bin/bash -lc "nl -ba apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt | sed -n '280,325p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
   280	        uidSource.value = null
   281	        assertNull(auth.currentUid())
   282	    }
   283	
   284	    // -----------------------------------------------------------------------
   285	    // Phase-07 H5: full-stack wiring — all 3 modules
   286	    // GIVEN testDataSourceModule + appShellDataModule + appShellPresentationModule
   287	    //   + questPresentationModule + quizzesPresentationModule + domain + repo stubs
   288	    // WHEN get<DefaultRootComponent> with parametersOf(ComponentContext)
   289	    // THEN component resolves without MissingDefinitionException
   290	    // -----------------------------------------------------------------------
   291	
   292	    @Test
   293	    fun `full stack wiring DefaultRootComponent resolvable with parametersOf`() {
   294	        val lifecycle = LifecycleRegistry()
   295	        lifecycle.resume()
   296	        val testCtx = DefaultComponentContext(lifecycle)
   297	
   298	        startKoin {
   299	            modules(
   300	                testDataSourceModule,
   301	                testDaoModule,
   302	                testWorkManagerModule,
   303	                testRepositoryStubsModule,
   304	                appShellDataModule(),
   305	                questDomainModule,
   306	                catalogDomainModule,
   307	                questPresentationModule,
   308	                quizzesPresentationModule,
   309	                appShellPresentationModule,
   310	            )
   311	        }
   312	
   313	        try {
   314	            val component = getKoin().get<DefaultRootComponent> { parametersOf(testCtx) }
   315	            assertNotNull(component)
   316	        } finally {
   317	            lifecycle.stop()
   318	            lifecycle.destroy()
   319	        }
   320	    }
   321	
   322	    // -----------------------------------------------------------------------
   323	    // Phase-07: system back event at LOCAL root
   324	    // Spec: back_on_LOCAL_root_emits_system_back (Journey 4 step 3)
   325	    // GIVEN DefaultRootComponent at LOCAL root (backStack empty, drawer closed)

exec
/bin/bash -lc 'nl -ba shared/feature/lesson-runner/data/src/jvmAndAndroidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultAttemptIdProvider.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider
     2	
     3	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
     4	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.AttemptIdProvider
     5	import java.util.UUID
     6	
     7	class DefaultAttemptIdProvider : AttemptIdProvider {
     8	    override fun next(): AttemptId = AttemptId(UUID.randomUUID().toString())
     9	}

exec
/bin/bash -lc 'nl -ba shared/feature/lesson-runner/data/src/jvmAndAndroidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultRandomSeedProvider.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider
     2	
     3	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RandomSeedProvider
     4	import java.util.concurrent.ThreadLocalRandom
     5	
     6	class DefaultRandomSeedProvider : RandomSeedProvider {
     7	    override fun next(): Long = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)
     8	}

exec
/bin/bash -lc 'nl -ba shared/feature/lesson-runner/data/src/jvmAndAndroidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultRatingIdProvider.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider
     2	
     3	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
     4	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
     6	import java.security.MessageDigest
     7	
     8	class DefaultRatingIdProvider : RatingIdProvider {
     9	    override fun provide(userId: String, lessonId: LessonId): RatingId {
    10	        val input = "$userId:${lessonId.value}"
    11	        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    12	        val hex = bytes.joinToString("") { "%02x".format(it) }
    13	        return RatingId(hex)
    14	    }
    15	}

exec
/bin/bash -lc 'nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/AttemptId.kt && nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/RatingId.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
     2	
     3	@JvmInline
     4	value class AttemptId(val value: String) {
     5	    init {
     6	        require(value.isNotBlank()) { "AttemptId must not be blank" }
     7	    }
     8	}
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
     2	
     3	@JvmInline
     4	value class RatingId(val value: String) {
     5	    init {
     6	        require(value.isNotBlank()) { "RatingId must not be blank" }
     7	    }
     8	}

exec
/bin/bash -lc "rg -n \"com\\.google\\.firebase|Firebase|firestore|Firestore|Hilt|@Inject|@Provides|@Module|Dagger\" android/feature/lesson-runner shared/feature/lesson-runner android/feature/quizzes-screen/presentation/src/main shared/core/question-schema -g '*.kt'" in /home/Programming/Android/schoolquiz4.0
 exited 1 in 0ms:
exec
/bin/bash -lc 'rg -n "''^import (android|androidx|io'"\\.livekit|com\\.google\\.firebase|androidx\\.room|kotlinx\\.serialization)\" shared/feature/lesson-runner/domain/src/commonMain -g '*.kt'" in /home/Programming/Android/schoolquiz4.0
 exited 1 in 0ms:
exec
/bin/bash -lc "rg -n \"Repository|UseCase|use_case|domain\\.repository\" apps/android-next/src/main/java android/feature/*/presentation/src/main/kotlin -g '*.kt'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt:14:                questRepository = get(),
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt:15:                sectionRepository = get(),
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt:16:                themeRepository = get(),
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt:17:                lessonRepository = get(),
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt:18:                lessonAttemptRepository = get(),
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt:19:                authRepository = get(),
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/di/AppShellPresentationModule.kt:8:import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.InitializeAppShellUseCase
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/di/AppShellPresentationModule.kt:9:import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.NavigateUseCase
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/di/AppShellPresentationModule.kt:10:import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.ObserveAppShellStateUseCase
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/di/AppShellPresentationModule.kt:11:import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.OnTabRetapUseCase
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/di/AppShellPresentationModule.kt:26:                initUseCase = get(),
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/di/AppShellPresentationModule.kt:27:                navigateUseCase = get(),
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/di/AppShellPresentationModule.kt:28:                observeUseCase = get(),
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/di/AppShellPresentationModule.kt:29:                retapUseCase = get(),
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/di/AppShellPresentationModule.kt:30:                userStatsRepository = get(),
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/di/AppShellPresentationModule.kt:48:        factory { InitializeAppShellUseCase(get()) }
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/di/AppShellPresentationModule.kt:49:        factory { NavigateUseCase() }
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/di/AppShellPresentationModule.kt:50:        factory { OnTabRetapUseCase() }
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/di/AppShellPresentationModule.kt:51:        factory { ObserveAppShellStateUseCase(get()) }
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponent.kt:14:import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponent.kt:26:    private val themeRepository: ThemeRepository,
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponent.kt:41:            themeRepository.observeBySection(sectionId)
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponent.kt:14:import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponent.kt:26:    private val sectionRepository: SectionRepository,
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponent.kt:41:            sectionRepository.observeByQuest(questId)
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponent.kt:14:import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponent.kt:26:    private val questRepository: QuestRepository,
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponent.kt:41:            questRepository.observeByCatalog(catalogId, "home")
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:17:import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:19:import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:20:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:22:import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:23:import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:24:import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:31:    private val questRepository: QuestRepository,
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:32:    private val sectionRepository: SectionRepository,
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:33:    private val themeRepository: ThemeRepository,
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:34:    private val lessonRepository: LessonRepository,
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:35:    private val lessonAttemptRepository: LessonAttemptRepository,
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:36:    private val authRepository: AuthRepository,
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:136:                    DefaultQuestListComponent(ctx, config, questRepository, navigation, mainContext),
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:140:                    DefaultSectionListComponent(ctx, config, sectionRepository, navigation, mainContext),
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:144:                    DefaultThemeListComponent(ctx, config, themeRepository, navigation, mainContext),
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:151:                        lessonRepository,
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:152:                        lessonAttemptRepository,
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:153:                        authRepository,
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:38:import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:42:import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.InitializeAppShellUseCase
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:43:import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.NavigateUseCase
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:44:import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.ObserveAppShellStateUseCase
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:45:import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.OnTabRetapUseCase
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:48:import com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode.use_case.ActivateDevModeUseCase
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:76:    private val initUseCase: InitializeAppShellUseCase,
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:77:    private val navigateUseCase: NavigateUseCase,
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:78:    private val observeUseCase: ObserveAppShellStateUseCase,
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:79:    private val retapUseCase: OnTabRetapUseCase,
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:80:    private val userStatsRepository: UserStatsRepository,
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:89:    private val activateDevModeUseCase =
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:90:        ActivateDevModeUseCase(
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:92:            onDevModeActivated = { userStatsRepository.setLocalDeveloperLevel(QualificationLevel.LEVEL_1.points) },
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:180:                runCatching { initUseCase() }
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:209:                    observeUseCase { _appShellState.value }.collect { newState ->
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:229:        val result = navigateUseCase(current, destination)
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:238:        val (newState, outcome) = retapUseCase(old, tab)
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:251:            // before first suspend point). FSM state transitions before ActivateDevModeUseCase's
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:254:            when (val result = activateDevModeUseCase(snapshot, nowMillis)) {
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:13:import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:16:import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:17:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:18:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptStats
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:37:    private val lessonRepository: LessonRepository,
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:38:    private val attemptRepository: LessonAttemptRepository,
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:39:    private val authRepository: AuthRepository,
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:57:                lessonRepository.observeByTheme(themeId),
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:58:                authRepository.observeUid().flatMapLatest { uid ->
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:62:                        attemptRepository.observeAllStatsByUser(uid)
android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/DefaultMyQuestsComponent.kt:11:import com.tpov.schoolquiz.shared.core.catalog.domain.use_case.ObserveCatalogsUseCase
android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/DefaultMyQuestsComponent.kt:14:import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/DefaultMyQuestsComponent.kt:15:import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.ObserveMyQuestsUseCase
android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/DefaultMyQuestsComponent.kt:46:    private val authRepo: AuthRepository,
android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/DefaultMyQuestsComponent.kt:47:    private val observeMyQuests: ObserveMyQuestsUseCase,
android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/DefaultMyQuestsComponent.kt:48:    private val observeCatalogs: ObserveCatalogsUseCase,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:7:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.LessonRunnerUseCases
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:13:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:25:                    LessonRunnerUseCases(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:26:                        startAttempt = get<StartLessonAttemptUseCase>()::invoke,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:27:                        completeAttempt = get<CompleteAttemptUseCase>()::invoke,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:28:                        abortAttempt = get<AbortAttemptUseCase>()::invoke,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:29:                        submitRating = get<SubmitLessonRatingUseCase>()::invoke,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:31:                lessonRepository = get(),
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:32:                attemptRepository = get(),
android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/DefaultHomeQuestsComponent.kt:7:import com.tpov.schoolquiz.shared.core.catalog.domain.use_case.ObserveCatalogsUseCase
android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/DefaultHomeQuestsComponent.kt:19: * Observes all non-archived catalogs via ObserveCatalogsUseCase (DAO WHERE archived=0 already
android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/DefaultHomeQuestsComponent.kt:29:    private val observeCatalogs: ObserveCatalogsUseCase,
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt:6:import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt:8:import com.tpov.schoolquiz.shared.core.sync.InMemorySyncStateRepository
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt:9:import com.tpov.schoolquiz.shared.core.sync.SyncStateRepository
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt:11:import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt:12:import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt:13:import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt:14:import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt:15:import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt:16:import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt:17:import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt:23:        single<SyncStateRepository> { InMemorySyncStateRepository() }
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt:27:                catalogRepo = get<CatalogRepository>(),
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt:28:                questRepo = get<QuestRepository>(),
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt:29:                sectionRepo = get<SectionRepository>(),
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt:30:                themeRepo = get<ThemeRepository>(),
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt:31:                lessonRepo = get<LessonRepository>(),
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt:32:                questionRepo = get<QuestionRepository>(),
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt:33:                syncStateRepo = get<SyncStateRepository>(),
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt:34:                authRepo = get<AuthRepository>(),
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt:35:                userStatsRepo = get<UserStatsRepository>(),
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt:40:                get<UserStatsRepository>() as Syncable,
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:83:        // shared across all consumers (UserStatsRepositoryImpl + AuthRepositoryImpl + future
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:17:import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:27:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:44:    private val useCases: LessonRunnerUseCases,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:45:    private val lessonRepository: LessonRepository,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:46:    private val attemptRepository: LessonAttemptRepository,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:264:        val lesson = lessonRepository.getById(attempt.lessonId)
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:265:        val userAttempts = attemptRepository.observeByLesson(attempt.userId, attempt.lessonId).first()
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/LessonRunnerUseCases.kt:7:class LessonRunnerUseCases(

exec
/bin/bash -lc 'find android/core/navigation -type f -maxdepth 8 | sort | xargs -r rg -n "LessonRunner|lesson_runner"' in /home/Programming/Android/schoolquiz4.0
 exited 123 in 0ms:
exec
/bin/bash -lc "nl -ba shared/core/question-schema/build.gradle.kts | sed -n '1,180p' && nl -ba shared/core/question-schema/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/di/QuestionSchemaModule.kt" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	plugins {
     2	    id("schoolquiz.kmp.library")
     3	    alias(libs.plugins.kotlin.serialization)
     4	}
     5	
     6	android {
     7	    namespace = "com.tpov.schoolquiz.shared.core.question_schema"
     8	}
     9	
    10	kotlin {
    11	    sourceSets {
    12	        getByName("commonMain") {
    13	            dependencies {
    14	                implementation(libs.kotlinx.serialization.json)
    15	            }
    16	        }
    17	        androidMain.dependencies {
    18	            implementation(libs.koin.core)
    19	        }
    20	    }
    21	}
     1	package com.tpov.schoolquiz.shared.core.question_schema.di
     2	
     3	import com.tpov.schoolquiz.shared.core.question_schema.KotlinxSerializationQuestionContentParser
     4	import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser
     5	import org.koin.dsl.module
     6	
     7	val questionSchemaModule = module {
     8	    single<QuestionContentParser> { KotlinxSerializationQuestionContentParser() }
     9	}

exec
/bin/bash -lc "rg -n \"single<Clock>|factory<Clock>|Clock\\.System|single<LessonAttemptRepository>|single<LessonRatingRepository>|single<AttemptIdProvider>|single<RandomSeedProvider>|single<RatingIdProvider>|factory<StartLessonAttemptUseCase>|factory<CompleteAttemptUseCase>|factory<AbortAttemptUseCase>|factory<SubmitLessonRatingUseCase>|single<QuestionContentParser>\" android apps shared -g '*.kt'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:205:        single<LessonAttemptRepository> {
shared/core/question-schema/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/di/QuestionSchemaModule.kt:8:    single<QuestionContentParser> { KotlinxSerializationQuestionContentParser() }
shared/core/sync/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/sync/CascadingSyncOrchestratorTest.kt:158:        // THEN: orchestrator advances catalog cursor to freshTime (Clock.System.now())
shared/core/sync/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/sync/CascadingSyncOrchestrator.kt:26: *    (subtree-atomic advance). All 6 levels use Clock.System.now() (freshTime) — uniform
shared/core/sync/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/sync/CascadingSyncOrchestrator.kt:28: *  - Cursor value: Clock.System.now() sampled once per [syncCascade] entry (freshTime).
shared/core/sync/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/sync/CascadingSyncOrchestrator.kt:68:            val freshTime = Clock.System.now().toEpochMilliseconds()
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt:13:    factory<StartLessonAttemptUseCase> {
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt:23:    factory<CompleteAttemptUseCase> {
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt:31:    factory<AbortAttemptUseCase> {
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt:38:    factory<SubmitLessonRatingUseCase> {
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:17:    single<AttemptIdProvider> { DefaultAttemptIdProvider() }
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:18:    single<RandomSeedProvider> { DefaultRandomSeedProvider() }
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:19:    single<RatingIdProvider> { DefaultRatingIdProvider() }
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:20:    single<Clock> { Clock.System }
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:21:    single<LessonAttemptRepository> { LessonAttemptRepositoryImpl(attemptDao = get()) }
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:22:    single<LessonRatingRepository> { LessonRatingRepositoryImpl(ratingLocalDao = get()) }
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt:12: * Clock: data module registers single<Clock> { Clock.System }.
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt:13: * Providers: data module registers single<AttemptIdProvider>, single<RandomSeedProvider>, single<RatingIdProvider>.

exec
/bin/bash -lc "nl -ba android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCardTest.kt | sed -n '1,240p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
     2	
     3	import androidx.compose.ui.semantics.Role
     4	import androidx.compose.ui.semantics.SemanticsProperties
     5	import androidx.compose.ui.test.SemanticsMatcher
     6	import androidx.compose.ui.test.assertCountEquals
     7	import androidx.compose.ui.test.assertIsDisplayed
     8	import androidx.compose.ui.test.junit4.createComposeRule
     9	import androidx.compose.ui.test.onNodeWithText
    10	import androidx.test.ext.junit.runners.AndroidJUnit4
    11	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    12	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
    13	import org.junit.Rule
    14	import org.junit.Test
    15	import org.junit.runner.RunWith
    16	
    17	/**
    18	 * Instrumented Compose UI tests for [LessonItemCard].
    19	 *
    20	 * Spec: docs/features/lesson-runner/plan/phase-06/tests.md §Scenario Group C
    21	 * AC: AC-47 (CT-22), AC-50 (CT-23), AC-51 (CT-24)
    22	 *
    23	 * CT-22: StarRating renders with bestStarsRawTenths=15 (smoke — no test tags on stars)
    24	 * CT-23: hardUnlocked=false → no Checkbox
    25	 * CT-24: hardUnlocked=true  → Checkbox visible and unchecked
    26	 */
    27	@RunWith(AndroidJUnit4::class)
    28	class LessonItemCardTest {
    29	
    30	    @get:Rule
    31	    val composeTestRule = createComposeRule()
    32	
    33	    // CT-22
    34	    // GIVEN LessonItemUi(bestStarsRawTenths=15, hardUnlocked=false)
    35	    // WHEN LessonItemCard rendered
    36	    // THEN lesson title is visible (smoke — StarRating has no test tags)
    37	    @Test
    38	    fun ct22_lessonItemCard_bestStarsRawTenths15_rendersWithoutCrash() {
    39	        val item = LessonItemUi(
    40	            id = "l1",
    41	            title = "Урок 1",
    42	            orderLabel = "1.",
    43	            bestStarsRawTenths = 15,
    44	            hardUnlocked = false,
    45	        )
    46	        composeTestRule.setContent {
    47	            SchoolQuizTheme {
    48	                LessonItemCard(item = item, onClick = {}, onHardCheckChanged = {})
    49	            }
    50	        }
    51	        composeTestRule.onNodeWithText("Урок 1").assertIsDisplayed()
    52	    }
    53	
    54	    // CT-23
    55	    // GIVEN LessonItemUi(hardUnlocked=false)
    56	    // WHEN LessonItemCard rendered
    57	    // THEN Checkbox is not present (hardUnlocked=false → Checkbox branch skipped)
    58	    @Test
    59	    fun ct23_lessonItemCard_hardUnlocked_false_noCheckbox() {
    60	        val item = LessonItemUi(
    61	            id = "l2",
    62	            title = "Урок 2",
    63	            hardUnlocked = false,
    64	        )
    65	        composeTestRule.setContent {
    66	            SchoolQuizTheme {
    67	                LessonItemCard(item = item, onClick = {}, onHardCheckChanged = {})
    68	            }
    69	        }
    70	        composeTestRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).assertCountEquals(0)
    71	    }
    72	
    73	    // CT-24
    74	    // GIVEN LessonItemUi(hardUnlocked=true, isHardChecked=false)
    75	    // WHEN LessonItemCard rendered
    76	    // THEN exactly one Checkbox visible
    77	    @Test
    78	    fun ct24_lessonItemCard_hardUnlocked_true_checkboxVisible() {
    79	        val item = LessonItemUi(
    80	            id = "l3",
    81	            title = "Урок 3",
    82	            hardUnlocked = true,
    83	            isHardChecked = false,
    84	        )
    85	        composeTestRule.setContent {
    86	            SchoolQuizTheme {
    87	                LessonItemCard(item = item, onClick = {}, onHardCheckChanged = {})
    88	            }
    89	        }
    90	        composeTestRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).assertCountEquals(1)
    91	    }
    92	}

exec
/bin/bash -lc "nl -ba android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
     2	
     3	import com.arkivanov.decompose.DefaultComponentContext
     4	import com.arkivanov.essenty.lifecycle.LifecycleRegistry
     5	import com.arkivanov.essenty.lifecycle.destroy
     6	import com.arkivanov.essenty.lifecycle.resume
     7	import com.arkivanov.essenty.lifecycle.stop
     8	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
     9	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeAuthRepository
    10	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonAttemptRepository
    11	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonRepository
    12	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeStackNavigation
    13	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
    14	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
    15	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
    16	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
    17	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
    18	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
    19	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
    20	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
    21	import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
    22	import kotlinx.coroutines.ExperimentalCoroutinesApi
    23	import kotlinx.coroutines.test.StandardTestDispatcher
    24	import kotlinx.coroutines.test.TestCoroutineScheduler
    25	import kotlinx.coroutines.test.advanceUntilIdle
    26	import kotlinx.coroutines.test.runTest
    27	import org.junit.After
    28	import org.junit.Test
    29	import kotlin.test.assertEquals
    30	import kotlin.test.assertFalse
    31	import kotlin.test.assertTrue
    32	
    33	/**
    34	 * Phase-06 JVM unit tests for [DefaultLessonListComponent] extended with
    35	 * bestStars / hardUnlocked computation from [LessonAttemptRepository].
    36	 *
    37	 * Spec: docs/features/lesson-runner/plan/phase-06/tests.md §Scenario Group A
    38	 * AC: AC-21, AC-22, AC-23, AC-47, AC-48, AC-49
    39	 *
    40	 * COMPILATION NOTE: These tests depend on Gradle deps not yet added to
    41	 * android/feature/quizzes-screen/presentation/build.gradle.kts:
    42	 *   testImplementation(project(":shared:feature:lesson-runner:domain"))
    43	 *   testImplementation(project(":shared:feature:app-shell:domain"))
    44	 * Tests compile and run once backend-dev adds these deps (scaffold change request sent to lead).
    45	 */
    46	@OptIn(ExperimentalCoroutinesApi::class)
    47	class DefaultLessonListComponentPhase06Test {
    48	
    49	    private val testScheduler = TestCoroutineScheduler()
    50	    private val dispatcher = StandardTestDispatcher(testScheduler)
    51	    private lateinit var lifecycle: LifecycleRegistry
    52	    private val fakeLessonRepo = FakeLessonRepository()
    53	    private val fakeAttemptRepo = FakeLessonAttemptRepository()
    54	    private val fakeAuthRepo = FakeAuthRepository(initialUid = "user1")
    55	    private val fakeNavigation = FakeStackNavigation()
    56	
    57	    @After
    58	    fun tearDown() {
    59	        if (::lifecycle.isInitialized) {
    60	            lifecycle.stop()
    61	            lifecycle.destroy()
    62	        }
    63	    }
    64	
    65	    // ── helpers ──────────────────────────────────────────────────────────────
    66	
    67	    private fun buildComponent(
    68	        themeId: String = "t-1",
    69	        titles: List<String> = listOf("Math", "Quest 1", "Section A", "Theme A"),
    70	    ): DefaultLessonListComponent {
    71	        lifecycle = LifecycleRegistry()
    72	        lifecycle.resume()
    73	        val ctx = DefaultComponentContext(lifecycle = lifecycle)
    74	        return DefaultLessonListComponent(
    75	            componentContext = ctx,
    76	            config = QuizzesConfig.LessonList(themeId = themeId, titles = titles),
    77	            lessonRepository = fakeLessonRepo,
    78	            attemptRepository = fakeAttemptRepo,
    79	            authRepository = fakeAuthRepo,
    80	            navigation = fakeNavigation,
    81	            coroutineContext = dispatcher,
    82	        )
    83	    }
    84	
    85	    private fun lessonFixture(
    86	        id: String = "l-1",
    87	        themeId: String = "t-1",
    88	        title: String = "Урок 1",
    89	        order: Int = 1,
    90	    ) = Lesson(
    91	        id = LessonId(id),
    92	        themeId = ThemeId(themeId),
    93	        title = title,
    94	        order = order,
    95	        version = 1L,
    96	        contentsVersion = 0L,
    97	        lastModifiedAt = 0L,
    98	    )
    99	
   100	    private fun attemptFixture(
   101	        userId: String = "user1",
   102	        lessonId: String = "l-1",
   103	        mode: Difficulty = Difficulty.EASY,
   104	        codeAnswer: String = "9",
   105	        percentScore: Int = 100,
   106	    ) = Attempt(
   107	        id = AttemptId("attempt-$userId-$lessonId"),
   108	        userId = userId,
   109	        lessonId = LessonId(lessonId),
   110	        lessonVersion = 1L,
   111	        mode = mode,
   112	        completedAt = 1_000_000L,
   113	        codeAnswer = CodeAnswer(codeAnswer),
   114	        percentScore = PercentScore(percentScore),
   115	    )
   116	
   117	    private fun loadedItems(component: DefaultLessonListComponent) =
   118	        (component.uiState.value as LessonListUiState.Loaded).items
   119	
   120	    // ── PT-15 ─────────────────────────────────────────────────────────────────
   121	
   122	    /**
   123	     * PT-15: GIVEN no attempts for lesson WHEN lessonItems emitted
   124	     * THEN bestStarsRawTenths == 0 AND hardUnlocked == false.
   125	     * Spec AC-21.
   126	     */
   127	    @Test
   128	    fun `pt15 no attempts defaults to zero stars and hardUnlocked false`() = runTest(testScheduler) {
   129	        val component = buildComponent(themeId = "t-1")
   130	
   131	        fakeLessonRepo.emit(listOf(lessonFixture(id = "l-1", themeId = "t-1")))
   132	        // No attempts emitted — fakeAttemptRepo stores empty list
   133	        advanceUntilIdle()
   134	
   135	        val items = loadedItems(component)
   136	        assertEquals(1, items.size)
   137	        assertEquals(0, items[0].bestStarsRawTenths, "no attempts → Stars(0) → rawTenths=0")
   138	        assertFalse(items[0].hardUnlocked, "no attempts → hardUnlocked=false")
   139	    }
   140	
   141	    // ── PT-16 ─────────────────────────────────────────────────────────────────
   142	
   143	    /**
   144	     * PT-16: GIVEN one EASY attempt with allShownAnswersAre9=true WHEN lessonItems emitted
   145	     * THEN hardUnlocked == true.
   146	     * codeAnswer "9999" → all chars '9' AND at least one '9' → allShownAnswersAre9=true.
   147	     * Spec AC-22.
   148	     */
   149	    @Test
   150	    fun `pt16 easy perfect attempt unlocks hard mode`() = runTest(testScheduler) {
   151	        val component = buildComponent(themeId = "t-1")
   152	
   153	        fakeLessonRepo.emit(listOf(lessonFixture(id = "l-1", themeId = "t-1")))
   154	        fakeAttemptRepo.emit(
   155	            listOf(
   156	                attemptFixture(
   157	                    userId = "user1",
   158	                    lessonId = "l-1",
   159	                    mode = Difficulty.EASY,
   160	                    codeAnswer = "9999",
   161	                    percentScore = 100,
   162	                ),
   163	            ),
   164	        )
   165	        advanceUntilIdle()
   166	
   167	        val items = loadedItems(component)
   168	        assertEquals(1, items.size)
   169	        assertTrue(items[0].hardUnlocked, "EASY perfect attempt (allShownAnswersAre9=true) → hardUnlocked=true")
   170	    }
   171	
   172	    // ── PT-17 ─────────────────────────────────────────────────────────────────
   173	
   174	    /**
   175	     * PT-17: GIVEN EASY attempt with allShownAnswersAre9=false WHEN lessonItems emitted
   176	     * THEN hardUnlocked == false, even if rawTenths == 20.
   177	     * codeAnswer "8999" → first digit '8' ≠ '9' → allShownAnswersAre9=false.
   178	     * Spec AC-23.
   179	     */
   180	    @Test
   181	    fun `pt17 easy imperfect attempt does not unlock hard even at high percent`() = runTest(testScheduler) {
   182	        val component = buildComponent(themeId = "t-1")
   183	
   184	        fakeLessonRepo.emit(listOf(lessonFixture(id = "l-1", themeId = "t-1")))
   185	        fakeAttemptRepo.emit(
   186	            listOf(
   187	                attemptFixture(
   188	                    userId = "user1",
   189	                    lessonId = "l-1",
   190	                    mode = Difficulty.EASY,
   191	                    codeAnswer = "8999", // first digit '8' → allShownAnswersAre9=false
   192	                    percentScore = 95,
   193	                ),
   194	            ),
   195	        )
   196	        advanceUntilIdle()
   197	
   198	        val items = loadedItems(component)
   199	        assertEquals(1, items.size)
   200	        assertFalse(items[0].hardUnlocked, "EASY attempt with '8' digit → hardUnlocked=false")
   201	    }
   202	
   203	    // ── PT-34 ─────────────────────────────────────────────────────────────────
   204	
   205	    /**
   206	     * PT-34: GIVEN EASY attempt with percentScore=75 WHEN lessonItems emitted
   207	     * THEN bestStarsRawTenths == 15.
   208	     * Formula: EASY rawTenths = (75*20+50)/100 = 1550/100 = 15.
   209	     * Spec AC-47.
   210	     */
   211	    @Test
   212	    fun `pt34 best stars rawTenths 15 for percentScore 75 easy`() = runTest(testScheduler) {
   213	        val component = buildComponent(themeId = "t-1")
   214	
   215	        fakeLessonRepo.emit(listOf(lessonFixture(id = "l-1", themeId = "t-1")))
   216	        fakeAttemptRepo.emit(
   217	            listOf(
   218	                attemptFixture(
   219	                    userId = "user1",
   220	                    lessonId = "l-1",
   221	                    mode = Difficulty.EASY,
   222	                    codeAnswer = "777",
   223	                    percentScore = 75,
   224	                ),
   225	            ),
   226	        )
   227	        advanceUntilIdle()
   228	
   229	        val items = loadedItems(component)
   230	        assertEquals(1, items.size)
   231	        assertEquals(15, items[0].bestStarsRawTenths, "EASY 75% → Stars rawTenths=15")
   232	    }
   233	
   234	    // ── PT-35 ─────────────────────────────────────────────────────────────────
   235	
   236	    /**
   237	     * PT-35: GIVEN hardUnlocked=false WHEN onHardCheckToggled called
   238	     * THEN isHardChecked remains false (toggle ignored when not unlocked).
   239	     * Spec AC-48.
   240	     */
   241	    @Test
   242	    fun `pt35 hard not unlocked ignores toggle`() = runTest(testScheduler) {
   243	        val component = buildComponent(themeId = "t-1")
   244	
   245	        fakeLessonRepo.emit(listOf(lessonFixture(id = "l-1", themeId = "t-1")))
   246	        // No attempts → hardUnlocked=false
   247	        advanceUntilIdle()
   248	
   249	        component.onHardCheckToggled("l-1")
   250	        advanceUntilIdle()
   251	
   252	        val items = loadedItems(component)
   253	        assertEquals(1, items.size)
   254	        assertFalse(items[0].isHardChecked, "toggle ignored when hardUnlocked=false → isHardChecked stays false")
   255	    }
   256	
   257	    // ── PT-36 ─────────────────────────────────────────────────────────────────
   258	
   259	    /**
   260	     * PT-36: GIVEN hardUnlocked=true AND isHardChecked=false WHEN onHardCheckToggled called

exec
/bin/bash -lc "nl -ba android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt | sed -n '120,210p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
   120	        assertIs<LessonListUiState.Loaded>(component.uiState.value)
   121	    }
   122	
   123	    // ── LL-U-03 ──────────────────────────────────────────────────────────────
   124	
   125	    /**
   126	     * Spec: LL-U-03 — empty list → Empty state.
   127	     */
   128	    @Test
   129	    fun `empty list produces Empty state`() = runTest(testScheduler) {
   130	        val component = buildComponent()
   131	        fakeRepo.emit(emptyList())
   132	        advanceUntilIdle()
   133	        assertIs<LessonListUiState.Empty>(component.uiState.value)
   134	    }
   135	
   136	    // ── LL-U-04 ──────────────────────────────────────────────────────────────
   137	
   138	    /**
   139	     * Spec: LL-U-04 — onLessonClick with hardUnlocked=false pushes LessonRunner with EASY mode.
   140	     * titles appended with lesson title.
   141	     */
   142	    @Test
   143	    fun `onLessonClick pushes LessonRunner with EASY mode when hardUnlocked is false`() = runTest(testScheduler) {
   144	        val component = buildComponent(themeId = "t-1", titles = listOf("Math", "Quest 1", "Section A", "Theme A"))
   145	        val lessonItem = lessonItemFixture(id = "l-1", title = "Lesson A", hardUnlocked = false, isHardChecked = false)
   146	
   147	        component.onLessonClick(lessonItem)
   148	
   149	        val pushed = fakeNavigation.pushedConfigs.last()
   150	        assertIs<QuizzesConfig.LessonRunner>(pushed)
   151	        assertEquals("l-1", pushed.lessonId)
   152	        assertEquals(Difficulty.EASY, pushed.mode, "mode must be EASY when hardUnlocked=false")
   153	        assertEquals(true, "Lesson A" in pushed.titles, "titles must include lesson title")
   154	    }
   155	
   156	    /**
   157	     * Spec: LL-U-04 edge case — onLessonClick with hardUnlocked=true + isHardChecked=true pushes HARD mode.
   158	     */
   159	    @Test
   160	    fun `onLessonClick pushes LessonRunner with HARD mode when hardUnlocked and isHardChecked`() = runTest(testScheduler) {
   161	        val component = buildComponent(titles = listOf("Math", "Quest 1", "Section A", "Theme A"))
   162	        val lessonItem = lessonItemFixture(id = "l-1", title = "Lesson A", hardUnlocked = true, isHardChecked = true)
   163	
   164	        component.onLessonClick(lessonItem)
   165	
   166	        val pushed = fakeNavigation.pushedConfigs.last() as QuizzesConfig.LessonRunner
   167	        assertEquals(Difficulty.HARD, pushed.mode, "mode must be HARD when hardUnlocked=true and isHardChecked=true")
   168	    }
   169	}

exec
/bin/bash -lc "nl -ba android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/StarRating.kt | sed -n '1,170p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.core.designsystem.components
     2	
     3	import androidx.compose.foundation.layout.Box
     4	import androidx.compose.foundation.layout.Row
     5	import androidx.compose.foundation.layout.requiredSize
     6	import androidx.compose.foundation.layout.size
     7	import androidx.compose.material.icons.Icons
     8	import androidx.compose.material.icons.filled.Star
     9	import androidx.compose.material.icons.outlined.StarOutline
    10	import androidx.compose.material3.Icon
    11	import androidx.compose.material3.MaterialTheme
    12	import androidx.compose.runtime.Composable
    13	import androidx.compose.ui.Modifier
    14	import androidx.compose.ui.draw.clipToBounds
    15	import androidx.compose.ui.graphics.Color
    16	import androidx.compose.ui.tooling.preview.Preview
    17	import androidx.compose.ui.unit.Dp
    18	import androidx.compose.ui.unit.dp
    19	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    20	
    21	private const val STAR_COUNT = 3
    22	
    23	/**
    24	 * Pure logic: whether star at [index] is fully filled for [rating].
    25	 * filled = rating covers at least 1 full unit beyond this star's start (threshold + 1.0).
    26	 * Testable without Android runtime.
    27	 */
    28	internal fun starIsFilled(
    29	    index: Int,
    30	    rating: Float?,
    31	): Boolean = rating != null && rating >= index.toFloat() + 1.0f
    32	
    33	/**
    34	 * Pure logic: whether star at [index] is partially filled for [rating].
    35	 * partial = rating enters this star's range but does not complete it.
    36	 */
    37	internal fun starIsPartial(
    38	    index: Int,
    39	    rating: Float?,
    40	): Boolean = rating != null && rating > index.toFloat() && !starIsFilled(index, rating)
    41	
    42	/**
    43	 * Pure logic: fraction of fill for a partial star (0f..1f exclusive).
    44	 * Returns 0f when star is not partial.
    45	 * Testable without Android runtime.
    46	 */
    47	internal fun starPartialFraction(
    48	    index: Int,
    49	    rating: Float?,
    50	): Float {
    51	    if (!starIsPartial(index, rating)) return 0f
    52	    return rating!! - index.toFloat()
    53	}
    54	
    55	/**
    56	 * Renders a partial star: [StarOutline] as background, [Star] clipped to [fraction] width.
    57	 *
    58	 * AC#26: for rating=2.7 star2 shows 70% filled, 30% outline.
    59	 */
    60	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    61	@Composable
    62	internal fun PartialStarIcon(
    63	    fraction: Float,
    64	    tint: Color,
    65	    size: Dp,
    66	) {
    67	    Box(modifier = Modifier.size(size)) {
    68	        Icon(
    69	            imageVector = Icons.Outlined.StarOutline,
    70	            contentDescription = null,
    71	            tint = tint,
    72	            modifier = Modifier.size(size),
    73	        )
    74	        Box(modifier = Modifier.size(width = size * fraction, height = size).clipToBounds()) {
    75	            Icon(
    76	                imageVector = Icons.Filled.Star,
    77	                contentDescription = null,
    78	                tint = tint,
    79	                modifier = Modifier.requiredSize(size),
    80	            )
    81	        }
    82	    }
    83	}
    84	
    85	/**
    86	 * Displays a 3-star rating row.
    87	 *
    88	 * rating null or 0f → all outline stars.
    89	 * rating 2.7 → star0 full, star1 full, star2 partial 70%. (AC#26)
    90	 * rating 3.0 → all 3 filled.
    91	 *
    92	 * Color: MaterialTheme.colorScheme.primary (GoogleBlue per ADR-0010).
    93	 * BrandComponentsInvariantsTest: no hardcoded color literals allowed.
    94	 *
    95	 * Spec: docs/features/home-and-my-quests/0-spec.md FR#10
    96	 */
    97	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    98	@Composable
    99	fun StarRating(
   100	    rating: Float?,
   101	    modifier: Modifier = Modifier,
   102	    size: Dp = 18.dp,
   103	) {
   104	    val primary = MaterialTheme.colorScheme.primary
   105	    Row(modifier = modifier) {
   106	        repeat(STAR_COUNT) { index ->
   107	            when {
   108	                starIsFilled(index, rating) ->
   109	                    Icon(
   110	                        imageVector = Icons.Filled.Star,
   111	                        contentDescription = null,
   112	                        tint = primary,
   113	                        modifier = Modifier.size(size),
   114	                    )
   115	                starIsPartial(index, rating) ->
   116	                    PartialStarIcon(
   117	                        fraction = starPartialFraction(index, rating),
   118	                        tint = primary,
   119	                        size = size,
   120	                    )
   121	                else ->
   122	                    Icon(
   123	                        imageVector = Icons.Outlined.StarOutline,
   124	                        contentDescription = null,
   125	                        tint = primary,
   126	                        modifier = Modifier.size(size),
   127	                    )
   128	            }
   129	        }
   130	    }
   131	}
   132	
   133	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
   134	@Preview(showBackground = true, backgroundColor = 0xFF000000)
   135	@Composable
   136	private fun StarRating0Preview() {
   137	    SchoolQuizTheme { StarRating(rating = 0f) }
   138	}
   139	
   140	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
   141	@Preview(showBackground = true, backgroundColor = 0xFF000000)
   142	@Composable
   143	private fun StarRatingHalfPreview() {
   144	    SchoolQuizTheme { StarRating(rating = 0.5f) }
   145	}
   146	
   147	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
   148	@Preview(showBackground = true, backgroundColor = 0xFF000000)
   149	@Composable
   150	private fun StarRating15Preview() {
   151	    SchoolQuizTheme { StarRating(rating = 1.5f) }
   152	}
   153	
   154	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
   155	@Preview(showBackground = true, backgroundColor = 0xFF000000)
   156	@Composable
   157	private fun StarRating27Preview() {
   158	    SchoolQuizTheme { StarRating(rating = 2.7f) }
   159	}
   160	
   161	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
   162	@Preview(showBackground = true, backgroundColor = 0xFF000000)
   163	@Composable
   164	private fun StarRating30Preview() {
   165	    SchoolQuizTheme { StarRating(rating = 3.0f) }
   166	}
   167	
   168	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
   169	@Preview(showBackground = true, backgroundColor = 0xFF000000)
   170	@Composable

exec
/bin/bash -lc "sed -n '136,160p' docs/features/lesson-runner/0-spec.md" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
   - Bound `bestStars: Stars` (показывается через `StarRating(rating = bestStars.rawTenths / 10f)` — existing API в `android/core/designsystem/.../StarRating.kt`).
   - Conditional `Checkbox` для HARD mode — visible если `hardUnlocked == true` (НЕ `bestStars.rawTenths >= 20`) + tap-handler меняет state «следующий тап на урок открывает HARD-режим».
   — [USER DECIDED] checkbox visibility = hardUnlocked.

#### Финальный экран результата

30. **Содержимое экрана результата** (порядок сверху вниз):
   1. **Большая цифра процентов** (`percentScore` крупным шрифтом, центр).
   2. **Подпись** в зависимости от исхода:
      - Впервые получил 2★ (perfect EASY): «Поздравляем! Сложные вопросы доступны».
      - Впервые получил 3★ (perfect HARD): «100% сложные! Вы прошли урок полностью».
      - Иначе — нейтрально (например «Урок завершён»).
   3. **Звёзды** (`StarRating` с этой попытки stars value).
   4. **Статистика** (краткая, своих попыток): `общее число попыток`, `средний %` по своим попыткам этого урока (читается из Room).
   5. **Опрос «Оцените урок»** (1/2/3 целых звезды, не fractional) — показывается **только**: (а) если `attempt.codeAnswer.allShownAnswersAre9 == true` (perfect EASY ИЛИ perfect HARD), (б) если пользователь ещё не оценивал этот урок (нет местного флага). Один раз в жизни на (userId, lessonId). — [USER DECIDED]
   6. **Топ-3** лучших участников этого урока (из `Lesson.top3` field, агрегируется на сервере). Каждый элемент: аватар (Coil URL — placeholder если не закеширован), nickname, percent. Если top3 пуст — секция скрыта.
   7. **Кнопка «Завершить»** — если rating был выбран в опросе → вызов `SubmitLessonRatingUseCase.invoke(state.userId, lessonId, rating)`, сбрасывает FLAG_SECURE если был, возврат на список уроков (pop ChildStack до `LessonListComponent`).
   — [USER DECIDED] полный список из user request.

31. **Sworn-fold логика** (Matrix 6): см. требования #14-16.

#### Server-side data model (для cascade sync infrastructure)

32. **Новая Firestore коллекция `lesson_attempts/{attemptId}`** (sync logic — не наша фича, описывается контракт):
   - `userId: String`

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ImageComponents.kt | sed -n '1,240p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
     2	
     3	import androidx.compose.foundation.background
     4	import androidx.compose.foundation.layout.Box
     5	import androidx.compose.foundation.layout.size
     6	import androidx.compose.material.icons.Icons
     7	import androidx.compose.material.icons.filled.AccountCircle
     8	import androidx.compose.material.icons.filled.Image
     9	import androidx.compose.material3.Icon
    10	import androidx.compose.material3.MaterialTheme
    11	import androidx.compose.runtime.Composable
    12	import androidx.compose.ui.Alignment
    13	import androidx.compose.ui.Modifier
    14	import androidx.compose.ui.tooling.preview.Preview
    15	import androidx.compose.ui.unit.dp
    16	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    17	
    18	/**
    19	 * Placeholder composable for question images.
    20	 * Replace with AsyncImage (Coil) once `libs.coil3.compose` is added to build.gradle.kts.
    21	 * Open Question: backend-dev to add `implementation(libs.coil3.compose)` to lesson-runner:presentation.
    22	 */
    23	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    24	@Composable
    25	fun QuestionImage(
    26	    url: String,
    27	    modifier: Modifier = Modifier,
    28	) {
    29	    Box(
    30	        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    31	        contentAlignment = Alignment.Center,
    32	    ) {
    33	        Icon(
    34	            imageVector = Icons.Default.Image,
    35	            contentDescription = null,
    36	            tint = MaterialTheme.colorScheme.onSurfaceVariant,
    37	        )
    38	    }
    39	}
    40	
    41	/**
    42	 * Placeholder composable for participant avatar images.
    43	 * Replace with AsyncImage (Coil) once coil3-compose is available in this module.
    44	 */
    45	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    46	@Composable
    47	fun ParticipantAvatar(
    48	    avatarUrl: String?,
    49	    modifier: Modifier = Modifier,
    50	) {
    51	    Icon(
    52	        imageVector = Icons.Default.AccountCircle,
    53	        contentDescription = null,
    54	        modifier = modifier,
    55	        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    56	    )
    57	}
    58	
    59	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
    60	@Preview(showBackground = true)
    61	@Composable
    62	private fun QuestionImagePreview() {
    63	    SchoolQuizTheme {
    64	        QuestionImage(url = "https://example.com/image.png", modifier = Modifier.size(200.dp, 120.dp))
    65	    }
    66	}
    67	
    68	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
    69	@Preview(showBackground = true)
    70	@Composable
    71	private fun ParticipantAvatarPreview() {
    72	    SchoolQuizTheme {
    73	        ParticipantAvatar(avatarUrl = null, modifier = Modifier.size(40.dp))
    74	    }
    75	}

exec
/bin/bash -lc 'rg -n "QuestionImage|hasImage|imageUrl" android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state docs/features/lesson-runner/0-spec.md' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
docs/features/lesson-runner/0-spec.md:52:    charsCount = chars(text) + sum(chars(option/item/candidate text)) + (if hasImage then 100 else 0)
docs/features/lesson-runner/0-spec.md:637:    charsCount = chars(text) + sum(chars(option_or_item_or_candidate text)) + (if hasImage then 100 else 0)
docs/features/lesson-runner/0-spec.md:1187:> **Amendment 2026-04-26 (lesson-runner spec)**: `timeLimitSec` в Question schema **может остаться в payload** для backward compatibility (legacy data), но **runtime игнорирует его** в пользу формулы `seconds = max(5, round(charsCount × k))` где `charsCount = chars(text) + sum(chars(option/item/candidate)) + (if hasImage then 100 else 0)`. EASY k≈0.18, HARD k≈0.12 (1.5× жёстче). Точные коэффициенты — runtime config в `lesson-runner/domain/.../config/TimerCoefficients.kt`.
docs/features/lesson-runner/0-spec.md:1207:| 9 | Image bonus в timer formula | **Resolved**: +100 знаков-эквивалентов если `hasImage`. |
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/QuestionUiState.kt:5:    val hasImage: Boolean
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/QuestionUiState.kt:9:        override val hasImage: Boolean,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/QuestionUiState.kt:10:        val imageUrl: String?,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/QuestionUiState.kt:17:        override val hasImage: Boolean,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/QuestionUiState.kt:18:        val imageUrl: String?,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/QuestionUiState.kt:25:        override val hasImage: Boolean,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/QuestionUiState.kt:26:        val imageUrl: String?,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/QuestionUiState.kt:32:        override val hasImage: Boolean,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/QuestionUiState.kt:33:        val imageUrl: String?,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/OrderingContent.kt:50:        if (state.hasImage && state.imageUrl != null) {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/OrderingContent.kt:51:            QuestionImage(url = state.imageUrl, modifier = Modifier.fillMaxWidth())
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/OrderingContent.kt:103:                    hasImage = false,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/OrderingContent.kt:104:                    imageUrl = null,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/MultipleChoiceContent.kt:48:        if (state.hasImage && state.imageUrl != null) {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/MultipleChoiceContent.kt:49:            QuestionImage(url = state.imageUrl, modifier = Modifier.fillMaxWidth())
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/MultipleChoiceContent.kt:91:                    hasImage = false,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/MultipleChoiceContent.kt:92:                    imageUrl = null,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/FillBlankContent.kt:51:        if (state.hasImage && state.imageUrl != null) {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/FillBlankContent.kt:52:            QuestionImage(url = state.imageUrl, modifier = Modifier.fillMaxWidth())
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/FillBlankContent.kt:133:                    hasImage = false,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/FillBlankContent.kt:134:                    imageUrl = null,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/SingleChoiceContent.kt:46:        if (state.hasImage && state.imageUrl != null) {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/SingleChoiceContent.kt:47:            QuestionImage(url = state.imageUrl, modifier = Modifier.fillMaxWidth())
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/SingleChoiceContent.kt:89:                    hasImage = false,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/SingleChoiceContent.kt:90:                    imageUrl = null,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/SingleChoiceContent.kt:114:                    hasImage = false,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/SingleChoiceContent.kt:115:                    imageUrl = null,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ImageComponents.kt:25:fun QuestionImage(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ImageComponents.kt:62:private fun QuestionImagePreview() {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ImageComponents.kt:64:        QuestionImage(url = "https://example.com/image.png", modifier = Modifier.size(200.dp, 120.dp))

exec
/bin/bash -lc "nl -ba docs/features/lesson-runner/0-spec.md | sed -n '1070,1110p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
  1070	24. [ ] GIVEN вопрос с totalChars=165, EASY mode THEN `timer == round(165*0.18) ≈ 30 сек`
  1071	25. [ ] GIVEN тот же вопрос HARD mode THEN `timer ≈ round(165*0.12) ≈ 20 сек`
  1072	26. [ ] GIVEN таймер истекает в 0 без действий пользователя THEN auto-random выбор → score фиксируется → переход к следующему вопросу
  1073	27. [ ] GIVEN короткий вопрос (totalChars=10) THEN `timer ≥ 5 сек` (min floor delegated)
  1074	
  1075	### Lifecycle / FLAG_SECURE / dialogs
  1076	
  1077	28. [ ] GIVEN HARD-mode runner запущен THEN `WindowManager.LayoutParams.FLAG_SECURE` установлен на window (или Compose-эквивалент)
  1078	29. [ ] GIVEN HARD-mode runner exit (любым путём) THEN FLAG_SECURE снят
  1079	30. [ ] GIVEN EASY-mode runner THEN FLAG_SECURE НЕ установлен
  1080	31. [ ] GIVEN пользователь на 5-м вопросе тапает Home (onStop) THEN текущий вопрос auto-random scored; таймер остановлен
  1081	32. [ ] GIVEN пользователь возвращается (onResume) THEN отображается fullscreen блокирующий диалог «Продолжить прохождение?»
  1082	33. [ ] GIVEN диалог «Продолжить?» тап «Продолжить» THEN диалог закрыт; следующий вопрос отображён с новым таймером; предыдущий вопрос НЕ показан
  1083	34. [ ] GIVEN диалог «Продолжить?» тап «Выйти» THEN attempt saved (codeAnswer: scores отвеченных + '1' для оставшихся показанных + '0' для не показанных); возврат в `LessonListComponent`
  1084	35. [ ] GIVEN configuration change (rotation) THEN component не пересоздаётся; таймер не сбрасывается; current question + answers preserved
  1085	36. [ ] GIVEN process kill THEN ничего не записывается в Room; следующий запуск — состояние ДО попытки
  1086	
  1087	### Attempt save / Room
  1088	
  1089	37. [ ] GIVEN complete attempt THEN `LessonAttemptRepository.save` called once с Attempt(lessonVersion из старта)
  1090	38. [ ] GIVEN exit-via-dialog после 3 ответов (pool size 20, eligibleQuestions.size=50) THEN saved attempt: codeAnswer.length==50, 3 цифры реальные, 17 = '1' (показанные но непрогресированные), 30 = '0' (out of subset)
  1091	39. [ ] GIVEN attempt созданный во время `lesson.version=5` THEN `attempt.lessonVersion == 5` даже если lesson.version обновился позже sync-ом
  1092	40. [ ] GIVEN no incremental save во время прохождения THEN `LessonAttemptRepository.save` вызывается ровно 1 раз per attempt
  1093	
  1094	### Result screen
  1095	
  1096	41. [ ] GIVEN attempt с `codeAnswer.allShownAnswersAre9 == true` AND ¬hasSubmittedRating THEN на result screen видно опрос «Оцените урок» (1/2/3 целых звезды)
  1097	42. [ ] GIVEN attempt с `codeAnswer.allShownAnswersAre9 == true` AND hasSubmittedRating THEN опрос НЕ виден
  1098	43. [ ] GIVEN attempt с `codeAnswer.allShownAnswersAre9 == false` (any shown digit < '9') THEN опрос НЕ виден
  1099	44. [ ] GIVEN пользователь оценил урок THEN local флаг `lesson_rating_submitted_local` установлен; LessonRatingRepository.submit вызван 1 раз с lessonVersion из текущего Lesson
  1100	45. [ ] GIVEN result screen с `Lesson.top3` непустой THEN отображается секция Топ-3 с аватарками, никами и %; пустой → секция скрыта. **Note: top3 — закешированный server snapshot из Lesson document; текущая попытка пользователя ещё не успела попасть в top3 (server CF не выполнился; sync ещё не подтянул).** Список покажет state ДО этой попытки.
  1101	46. [ ] GIVEN top3 entry с avatarUrl=null или Coil не смог загрузить (offline / no cache) THEN placeholder вместо аватарки
  1102	
  1103	### HARD unlock checkbox
  1104	
  1105	47. [ ] GIVEN карточка урока в `LessonListComponent` THEN отображается `StarRating(rating = bestStars.rawTenths / 10f)` (existing API из `android/core/designsystem/.../StarRating.kt:99` принимает `rating: Float?`)
  1106	48. [ ] GIVEN `hardUnlocked == false` (нет EASY-попытки с allShownAnswersAre9) THEN HARD checkbox скрыт — независимо от bestStars value
  1107	49. [ ] GIVEN `hardUnlocked == true` THEN HARD checkbox visible; default unchecked. Логика: `hardUnlocked = attempts.any { it.mode == EASY && it.codeAnswer.allShownAnswersAre9 }`
  1108	
  1109	### Empty / error states
  1110	

exec
/bin/bash -lc "nl -ba docs/features/lesson-runner/0-spec.md | sed -n '1036,1066p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
  1036	### Navigation flow
  1037	
  1038	1. [ ] GIVEN пользователь на `LessonListComponent` тапает урок (без HARD checkbox или checkbox=false) THEN push `LessonRunnerRootComponent(lessonId, mode=EASY)`; FLAG_SECURE НЕ включается; первый вопрос отображается
  1039	2. [ ] GIVEN пользователь с `hardUnlocked=true` тапает урок с включенным HARD checkbox THEN push `LessonRunnerRootComponent(lessonId, mode=HARD)`; FLAG_SECURE включается; первый вопрос отображается с HARD-стилизацией фона
  1040	3. [ ] GIVEN пользователь на любом вопросе нажимает крестик THEN диалог «Уверены?» → подтвердил → save attempt → возврат в `LessonListComponent`
  1041	4. [ ] GIVEN пользователь полностью прошёл все вопросы pool THEN save attempt в Room → переход на result screen
  1042	5. [ ] GIVEN пользователь на result screen тапает «Завершить» THEN если HARD — снять FLAG_SECURE → возврат в `LessonListComponent` через ChildStack pop
  1043	
  1044	### Score / codeAnswer correctness
  1045	
  1046	6. [ ] GIVEN SingleChoice с correct=A WHEN selected=A THEN code digit = '9'
  1047	7. [ ] GIVEN SingleChoice с correct=A WHEN selected=B THEN code digit = '1'
  1048	8. [ ] GIVEN MultipleChoice (все верные picked) THEN code digit = '9'
  1049	9. [ ] GIVEN MultipleChoice (никаких верных picked, только wrong) THEN code digit = '1'
  1050	10. [ ] GIVEN MultipleChoice (Jaccard 0.5) THEN code digit = '5'
  1051	11. [ ] GIVEN Ordering все позиции верны THEN digit = '9'
  1052	12. [ ] GIVEN Ordering 3 из 6 совпало THEN digit = '5' (round(0.5*8)+1)
  1053	13. [ ] GIVEN FillBlank 3 из 3 верно THEN digit = '9'
  1054	14. [ ] GIVEN FillBlank 1 из 3 THEN digit = '4' (round(0.333*8)+1=round(2.67)+1)
  1055	15. [ ] GIVEN timeout без действий THEN auto-random выбор → digit вычислен по тем же правилам (зависит от случайного попадания)
  1056	16. [ ] GIVEN attempt completed THEN `codeAnswer.length == eligibleQuestions(mode).size`; non-zero digits.size == subset.size; positions of non-zero соответствуют orders показанных вопросов
  1057	
  1058	### Stars and progress
  1059	
  1060	17. [ ] GIVEN EASY attempt с `codeAnswer.allShownAnswersAre9 == true` (`percentScore=100`) THEN `Stars(rawTenths=20)`; bestStars обновляется; hardUnlocked становится true
  1061	18. [ ] GIVEN EASY attempt с `percentScore=50` (no allShownAnswersAre9) THEN `Stars(rawTenths=10)`; hardUnlocked остаётся false
  1062	19. [ ] GIVEN HARD attempt с `percentScore=80` THEN `Stars(rawTenths=28)`
  1063	20. [ ] GIVEN HARD attempt с `percentScore=100` (allShownAnswersAre9=true) THEN `Stars(rawTenths=30)`
  1064	21. [ ] GIVEN no attempts THEN `bestStars = Stars(0)` (UI 0.0), `hardUnlocked = false`, HARD checkbox скрыт
  1065	22. [ ] GIVEN ≥1 EASY-попытка с allShownAnswersAre9=true (все показанные = '9') THEN `hardUnlocked = true` → HARD checkbox visible; user может включить
  1066	23. [ ] GIVEN ≥1 EASY-попытка с `allShownAnswersAre9=false` (хотя бы один digit ∈ '1'..'8') THEN `hardUnlocked = false` → HARD checkbox остаётся скрытым; даже если есть HARD-attempt с floor `Stars(rawTenths=20)` — checkbox visibility = `hardUnlocked`, НЕ `bestStars.rawTenths >= 20`

exec
/bin/bash -lc "nl -ba docs/features/lesson-runner/0-spec.md | sed -n '1110,1144p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
  1110	
  1111	50. [ ] GIVEN тап на урок с пустым eligibleQuestions(EASY) THEN empty state «В уроке пока нет вопросов» + кнопка «Назад»
  1112	51. [ ] GIVEN тап на урок с все payloads invalid THEN empty state аналогично
  1113	52. [ ] GIVEN single invalid payload среди валидных WHEN StartLessonAttemptUseCase THEN invalid отброшен на pipeline init; пользователь не видит; codeAnswer длина = только valid eligible
  1114	52a. [ ] GIVEN `LessonAttemptRepository.save` throws Room IO exception WHEN `CompleteAttemptUseCase` THEN `RunnerState.SaveFailed(attempt, error)`; result screen показывается с warning «Не удалось сохранить»; нет автоматического retry
  1115	52b. [ ] GIVEN `LessonRatingRepository.submit` throws WHEN user тапает submit rating THEN toast «Не удалось отправить оценку»; local флаг hasSubmitted остаётся false; user может попробовать снова
  1116	
  1117	### Code / DI / invariants
  1118	
  1119	53. [ ] DI: `LessonRunnerPresentationModule` + `LessonRunnerDataModule` зарегистрированы в `apps/android-next/.../AppApplication.kt`
  1120	54. [ ] Code: ни один файл `shared/feature/lesson-runner/domain/src/commonMain/` не импортирует `android.*`, `androidx.*`, `io.livekit`, `com.google.firebase`, `androidx.room`, `kotlinx.serialization` (invariant 1)
  1121	55. [ ] Code: ни одна Activity/Fragment не вызывает Repository / UseCase напрямую (invariant 2)
  1122	56. [ ] Code: feature import — `android/feature/lesson-runner/presentation` не импортирует `android/feature/quizzes-screen/presentation` (invariant 3); cross-feature только через `shared/core` или `android/core/designsystem`
  1123	57. [ ] Code: `quizzes-screen` импортирует lesson-runner config (для push) — задокументировано в design phase
  1124	58. [ ] Code: нет Hilt/Dagger annotations (Koin only)
  1125	59. [ ] Code: нет direct Firebase / Firestore writes из этой фичи; только через Repository
  1126	60. [ ] Tests: domain тесты Phase 3.8b покрывают все Domain Test Scenarios:
  1127	- Score formulas (1-12)
  1128	- codeAnswer construction (13-16)
  1129	- percentScore (17-20)
  1130	- stars (21-29)
  1131	- bestStars / hardUnlocked (30-35a)
  1132	- timer (36-39b)
  1133	- subset / auto-answer (40-47)
  1134	- rating prompt (48-51)
  1135	- save (52-54)
  1136	- lessonVersion (55-57)
  1137	- edge cases (58-61c)
  1138	- value object guards (62-71)
  1139	- failure semantics (72-74a)
  1140	- subset determinism (75-77)
  1141	- state transitions (78-82) — test 83 исключён из domain (см. ниже комментарий к нему)
  1142	Итого ~89 domain test scenarios (с учётом 39a/39b/61a/61b/61c/35a/74a/strikethrough 83). Скрипт renumber применяется в Phase 3.8b — test-dev волен переписать в монотонной последовательности 1..N если предпочитает.
  1143	61. [ ] Tests: JVM unit-тесты для каждого presentation Component через fakes
  1144	62. [ ] Tests: Compose UI тесты для key scenarios (per-type вопросы, timer, dialog onResume, FLAG_SECURE toggle)

codex
I’ve got several concrete failures now, including two that are very much cross-phase rather than style: parent Back handling bypasses the runner abort/save path, and answer drafts are kept only in composable-local state despite the rotation AC. I’m going to run one focused domain gate while I finish the write-up, mainly to avoid claiming a test surface is broken when the basic Walking Skeleton suite is simply red.
exec
/bin/bash -lc './gradlew :shared:feature:lesson-runner:domain:jvmTest --no-configuration-cache' in /home/Programming/Android/schoolquiz4.0
 succeeded in 11787ms:
Starting a Gradle Daemon, 1 incompatible Daemon could not be reused, use --status for details
> Task :buildSrc:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :buildSrc:compileKotlin UP-TO-DATE
> Task :buildSrc:compileJava NO-SOURCE
> Task :buildSrc:compileGroovy NO-SOURCE
> Task :buildSrc:pluginDescriptors UP-TO-DATE
> Task :buildSrc:processResources UP-TO-DATE
> Task :buildSrc:classes UP-TO-DATE
> Task :buildSrc:jar UP-TO-DATE

> Configure project :shared:feature:lesson-runner:data
w: [33m[1m⚠️ Default Kotlin Hierarchy Template Not Applied Correctly[0m[0m
The Default Kotlin Hierarchy Template was not applied to 'project ':shared:feature:lesson-runner:data'':
Explicit .dependsOn() edges were configured for the following source sets:
[androidMain, jvmAndAndroidMain, jvmMain]

Consider removing dependsOn-calls or disabling the default template by adding
    'kotlin.mpp.applyDefaultHierarchyTemplate=false'
to your gradle.properties
[32m[1mSolution:[0m[0m
[32m[3mPlease remove the dependsOn-calls or disable the default template.[0m[0m
[36mLearn more about hierarchy templates: [0m[34mhttps://kotl.in/hierarchy-template[0m[36m[0m


> Task :shared:core:foundation:kmpPartiallyResolvedDependenciesChecker
> Task :shared:core:foundation:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :shared:core:question-schema:kmpPartiallyResolvedDependenciesChecker
> Task :shared:core:leaderboard:kmpPartiallyResolvedDependenciesChecker
> Task :shared:feature:question:domain:kmpPartiallyResolvedDependenciesChecker
> Task :shared:core:catalog:domain:kmpPartiallyResolvedDependenciesChecker
> Task :shared:feature:section:domain:kmpPartiallyResolvedDependenciesChecker
> Task :shared:feature:question:domain:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :shared:core:question-schema:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :shared:core:leaderboard:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :shared:core:catalog:domain:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :shared:feature:section:domain:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :shared:feature:question:domain:jvmProcessResources NO-SOURCE
> Task :shared:feature:theme:domain:kmpPartiallyResolvedDependenciesChecker
> Task :shared:feature:section:domain:jvmProcessResources NO-SOURCE
> Task :shared:feature:theme:domain:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :shared:feature:question:domain:processJvmMainResources SKIPPED
> Task :shared:feature:section:domain:processJvmMainResources SKIPPED
> Task :shared:feature:theme:domain:jvmProcessResources NO-SOURCE
> Task :shared:feature:theme:domain:processJvmMainResources SKIPPED
> Task :shared:feature:quest:domain:kmpPartiallyResolvedDependenciesChecker
> Task :shared:feature:quest:domain:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :shared:feature:quest:domain:jvmProcessResources NO-SOURCE
> Task :shared:feature:quest:domain:processJvmMainResources SKIPPED
> Task :shared:core:leaderboard:compileKotlinJvm UP-TO-DATE
> Task :shared:core:question-schema:compileKotlinJvm UP-TO-DATE
> Task :shared:core:foundation:compileKotlinJvm UP-TO-DATE
> Task :shared:core:catalog:domain:compileKotlinJvm UP-TO-DATE
> Task :shared:core:foundation:compileJvmMainJava NO-SOURCE
> Task :shared:core:catalog:domain:compileJvmMainJava NO-SOURCE
> Task :shared:core:leaderboard:compileJvmMainJava NO-SOURCE
> Task :shared:core:question-schema:compileJvmMainJava NO-SOURCE
> Task :shared:core:foundation:jvmProcessResources NO-SOURCE
> Task :shared:core:foundation:processJvmMainResources SKIPPED
> Task :shared:core:catalog:domain:jvmProcessResources NO-SOURCE
> Task :shared:core:leaderboard:jvmProcessResources NO-SOURCE
> Task :shared:core:foundation:jvmMainClasses UP-TO-DATE
> Task :shared:core:question-schema:jvmProcessResources NO-SOURCE
> Task :shared:core:catalog:domain:processJvmMainResources SKIPPED
> Task :shared:core:leaderboard:processJvmMainResources SKIPPED
> Task :shared:core:question-schema:processJvmMainResources SKIPPED
> Task :shared:core:leaderboard:jvmMainClasses UP-TO-DATE
> Task :shared:core:catalog:domain:jvmMainClasses UP-TO-DATE
> Task :shared:core:question-schema:jvmMainClasses UP-TO-DATE
> Task :shared:core:foundation:jvmJar UP-TO-DATE
> Task :shared:core:leaderboard:jvmJar UP-TO-DATE
> Task :shared:core:catalog:domain:jvmJar UP-TO-DATE
> Task :shared:core:question-schema:jvmJar UP-TO-DATE
> Task :shared:feature:app-shell:domain:kmpPartiallyResolvedDependenciesChecker
> Task :shared:feature:app-shell:domain:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :shared:feature:lesson:domain:kmpPartiallyResolvedDependenciesChecker
> Task :shared:feature:lesson:domain:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :shared:feature:lesson:domain:jvmProcessResources NO-SOURCE
> Task :shared:feature:lesson:domain:processJvmMainResources SKIPPED
> Task :shared:feature:quest:domain:compileKotlinJvm UP-TO-DATE
> Task :shared:feature:quest:domain:compileJvmMainJava NO-SOURCE
> Task :shared:feature:quest:domain:jvmMainClasses UP-TO-DATE
> Task :shared:feature:app-shell:domain:compileKotlinJvm UP-TO-DATE
> Task :shared:feature:quest:domain:jvmJar UP-TO-DATE
> Task :shared:feature:app-shell:domain:compileJvmMainJava NO-SOURCE
> Task :shared:feature:app-shell:domain:jvmProcessResources NO-SOURCE
> Task :shared:feature:app-shell:domain:processJvmMainResources SKIPPED
> Task :shared:feature:app-shell:domain:jvmMainClasses UP-TO-DATE
> Task :shared:feature:app-shell:domain:jvmJar UP-TO-DATE
> Task :shared:feature:lesson-runner:domain:kmpPartiallyResolvedDependenciesChecker
> Task :shared:feature:lesson-runner:domain:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :shared:feature:lesson-runner:domain:jvmProcessResources NO-SOURCE
> Task :shared:feature:lesson-runner:domain:processJvmMainResources SKIPPED
> Task :shared:feature:lesson-runner:domain:jvmTestProcessResources NO-SOURCE
> Task :shared:feature:lesson-runner:domain:processJvmTestResources SKIPPED
> Task :shared:feature:section:domain:compileKotlinJvm UP-TO-DATE
> Task :shared:feature:section:domain:compileJvmMainJava NO-SOURCE
> Task :shared:feature:section:domain:jvmMainClasses UP-TO-DATE
> Task :shared:feature:section:domain:jvmJar UP-TO-DATE
> Task :shared:feature:theme:domain:compileKotlinJvm UP-TO-DATE
> Task :shared:feature:theme:domain:compileJvmMainJava NO-SOURCE
> Task :shared:feature:theme:domain:jvmMainClasses UP-TO-DATE
> Task :shared:feature:theme:domain:jvmJar UP-TO-DATE
> Task :shared:feature:lesson:domain:compileKotlinJvm UP-TO-DATE
> Task :shared:feature:lesson:domain:compileJvmMainJava NO-SOURCE
> Task :shared:feature:lesson:domain:jvmMainClasses UP-TO-DATE
> Task :shared:feature:lesson:domain:jvmJar UP-TO-DATE
> Task :shared:feature:question:domain:compileKotlinJvm UP-TO-DATE
> Task :shared:feature:question:domain:compileJvmMainJava NO-SOURCE
> Task :shared:feature:question:domain:jvmMainClasses UP-TO-DATE
> Task :shared:feature:question:domain:jvmJar UP-TO-DATE
> Task :shared:feature:lesson-runner:domain:compileKotlinJvm UP-TO-DATE
> Task :shared:feature:lesson-runner:domain:compileJvmMainJava NO-SOURCE
> Task :shared:feature:lesson-runner:domain:jvmMainClasses UP-TO-DATE
> Task :shared:feature:lesson-runner:domain:jvmJar UP-TO-DATE
> Task :shared:feature:lesson-runner:domain:compileTestKotlinJvm UP-TO-DATE
> Task :shared:feature:lesson-runner:domain:compileJvmTestJava NO-SOURCE
> Task :shared:feature:lesson-runner:domain:jvmTestClasses UP-TO-DATE
> Task :shared:feature:lesson-runner:domain:jvmTest UP-TO-DATE

[Incubating] Problems report is available at: file:///home/Programming/Android/schoolquiz4.0/build/reports/problems/problems-report.html

BUILD SUCCESSFUL in 11s
39 actionable tasks: 11 executed, 28 up-to-date

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt | sed -n '172,216p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
   172	    // ── IT-02: rotation reuses RunnerStateHolder ──────────────────────────────
   173	
   174	    /**
   175	     * IT-02: GIVEN component with FakeStart returning Ready; answers advance to indexInPool=3
   176	     *        WHEN simulate rotation (LifecycleRegistry destroy + new LifecycleRegistry, same instanceKeeper)
   177	     *        THEN new component has uiState.indexInPool == 3 (state preserved via shared stateHolder)
   178	     *        AND fakeStart.callCount == 1 (no duplicate StartLessonAttemptUseCase call on rotation)
   179	     *
   180	     * AC-35: Configuration change (rotation) preserves in-progress attempt state.
   181	     */
   182	    @Test
   183	    fun `rotation_component_reuses_runnerStateHolder`() {
   184	        fakeStart.result = makeReadyState(questions = 5, indexInPool = 0)
   185	        val instanceKeeper = InstanceKeeperDispatcher()
   186	
   187	        // Create component1, start use case runs → Ready(indexInPool=0)
   188	        val component1 = buildComponent(instanceKeeper = instanceKeeper)
   189	        assertIs<RunnerUiState.Question>(component1.uiState.value)
   190	        assertEquals(1, fakeStart.callCount, "startAttemptUseCase must be called once on init")
   191	
   192	        // Advance to indexInPool=3 by answering 3 questions
   193	        repeat(3) { component1.onAnswer(singleChoiceDraft()) }
   194	        val indexAfterAnswers = (component1.uiState.value as RunnerUiState.Question).indexInPool
   195	        assertEquals(3, indexAfterAnswers, "indexInPool must be 3 after answering 3 questions")
   196	
   197	        // Simulate rotation: destroy lifecycle1 (NOT instanceKeeper)
   198	        val lifecycle1 = lifecycles.last()
   199	        lifecycle1.destroy()
   200	
   201	        // Create component2 with the SAME instanceKeeper
   202	        val component2 = buildComponent(instanceKeeper = instanceKeeper)
   203	
   204	        // component2 should reuse RunnerStateHolder → same indexInPool (or adjusted after auto-answer on stop)
   205	        val uiState2 = component2.uiState.value
   206	        assertIs<RunnerUiState.Question>(uiState2)
   207	
   208	        // Key assertion: startAttemptUseCase must NOT have been called again
   209	        assertEquals(
   210	            1,
   211	            fakeStart.callCount,
   212	            "startAttemptUseCase must NOT be called again on rotation (instanceKeeper reuse)",
   213	        )
   214	    }
   215	
   216	    // ── IT-03: non-rotation destroy → state NOT preserved ─────────────────────

codex
**Findings**

1. **HIGH [Realist] Rotation loses in-progress answers.**  
AC-35 requires current question + answers to survive rotation ([0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1084)), but multiple-choice, ordering, and fill-blank drafts live only in `remember` state until Submit ([LessonRunnerScreen.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:200), [LessonRunnerScreen.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:216), [LessonRunnerScreen.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:247)). The component API only receives final answers via `onAnswer` ([LessonRunnerRootComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt:13)), so `currentDraftAnswer` is effectively not updated.

2. **HIGH [Architect] Android Back bypasses runner abort/save semantics.**  
The parent quizzes back callback directly pops the `ChildStack` ([DefaultQuizzesComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:68)), so a normal system Back from an active question destroys the runner without confirm dialog or `AbortAttemptUseCase`. That bypasses the save-on-exit model in AC-3/AC-34 ([0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1040), [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1083)).

3. **HIGH [Realist] HARD mode is not protected while Loading.**  
AC-28 says HARD runner launch sets `FLAG_SECURE` ([0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1077)), but `flagSecureEnabled` is false for `Loading`/`InitFailed` and only true for `Question` or `Result` ([LessonRunnerScreen.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:69)). The initial UI state is `Loading` ([RunnerStateHolder.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/RunnerStateHolder.kt:17)), so there is an unprotected HARD launch window.

4. **MEDIUM [Realist] Lesson cards hide 0.0 stars.**  
AC-21/47 require `Stars(0)` to render as UI `0.0` via `StarRating` ([0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1064), [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1105)), but `LessonItemCard` renders `StarRating` only when `bestStarsRawTenths > 0` ([LessonItemCard.kt](/home/Programming/Android/schoolquiz4.0/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCard.kt:58)).

5. **MEDIUM [Realist] Top-3 avatars and question images never load URLs.**  
AC-45/46 expect avatars with placeholder only for null/load failure ([0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1100)), but `ParticipantAvatar` ignores `avatarUrl` and always renders an icon ([ImageComponents.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ImageComponents.kt:47)). Same pattern for question images: `QuestionImage(url)` ignores `url` and always shows a placeholder ([ImageComponents.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ImageComponents.kt:25)).

6. **MEDIUM [Skeptic] HARD checkbox can stay checked after the “next tap” is consumed.**  
AC-49 says HARD checkbox default is unchecked ([0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1107)). `hardCheckedSet` is only cleared on component destroy ([DefaultLessonListComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:72)); `onLessonClick` pushes the runner but does not remove the lesson id ([DefaultLessonListComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:78)). Returning to the same `LessonListComponent` can leave HARD preselected.

Validated: the named DI chain is registered in order in `AppApplication`, factory/root are in lesson-runner presentation, reverse lesson-runner → quizzes-screen imports were empty, and `DifficultyConverter` production code was removed. I also ran `:shared:feature:lesson-runner:domain:jvmTest --no-configuration-cache`; it passed.
hook: Stop
hook: Stop Completed
2026-04-28T00:08:10.725683Z ERROR codex_core::session: failed to record rollout items: thread 019dd15c-2051-7e03-914f-573a035e5944 not found
tokens used
572,524
**Findings**

1. **HIGH [Realist] Rotation loses in-progress answers.**  
AC-35 requires current question + answers to survive rotation ([0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1084)), but multiple-choice, ordering, and fill-blank drafts live only in `remember` state until Submit ([LessonRunnerScreen.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:200), [LessonRunnerScreen.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:216), [LessonRunnerScreen.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:247)). The component API only receives final answers via `onAnswer` ([LessonRunnerRootComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt:13)), so `currentDraftAnswer` is effectively not updated.

2. **HIGH [Architect] Android Back bypasses runner abort/save semantics.**  
The parent quizzes back callback directly pops the `ChildStack` ([DefaultQuizzesComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:68)), so a normal system Back from an active question destroys the runner without confirm dialog or `AbortAttemptUseCase`. That bypasses the save-on-exit model in AC-3/AC-34 ([0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1040), [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1083)).

3. **HIGH [Realist] HARD mode is not protected while Loading.**  
AC-28 says HARD runner launch sets `FLAG_SECURE` ([0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1077)), but `flagSecureEnabled` is false for `Loading`/`InitFailed` and only true for `Question` or `Result` ([LessonRunnerScreen.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:69)). The initial UI state is `Loading` ([RunnerStateHolder.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/RunnerStateHolder.kt:17)), so there is an unprotected HARD launch window.

4. **MEDIUM [Realist] Lesson cards hide 0.0 stars.**  
AC-21/47 require `Stars(0)` to render as UI `0.0` via `StarRating` ([0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1064), [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1105)), but `LessonItemCard` renders `StarRating` only when `bestStarsRawTenths > 0` ([LessonItemCard.kt](/home/Programming/Android/schoolquiz4.0/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCard.kt:58)).

5. **MEDIUM [Realist] Top-3 avatars and question images never load URLs.**  
AC-45/46 expect avatars with placeholder only for null/load failure ([0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1100)), but `ParticipantAvatar` ignores `avatarUrl` and always renders an icon ([ImageComponents.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ImageComponents.kt:47)). Same pattern for question images: `QuestionImage(url)` ignores `url` and always shows a placeholder ([ImageComponents.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ImageComponents.kt:25)).

6. **MEDIUM [Skeptic] HARD checkbox can stay checked after the “next tap” is consumed.**  
AC-49 says HARD checkbox default is unchecked ([0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1107)). `hardCheckedSet` is only cleared on component destroy ([DefaultLessonListComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:72)); `onLessonClick` pushes the runner but does not remove the lesson id ([DefaultLessonListComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:78)). Returning to the same `LessonListComponent` can leave HARD preselected.

Validated: the named DI chain is registered in order in `AppApplication`, factory/root are in lesson-runner presentation, reverse lesson-runner → quizzes-screen imports were empty, and `DifficultyConverter` production code was removed. I also ran `:shared:feature:lesson-runner:domain:jvmTest --no-configuration-cache`; it passed.
