---
date: 2026-04-26
feature: lesson-runner
type: new-feature
commit: f2492d29
---

# Feature Specification: Lesson Runner — экран прохождения урока

## Source

- Описание фичи: «будем делать экран прохождения урока, экран вопросов»
- Type: `new-feature` — заменяет существующий `LessonPlaceholderComponent` из фичи `quizzes-screen` на полноценный gameplay-loop викторины. Это центральный геймплей-экран приложения: пользователь видит вопрос, отвечает, переходит к следующему, в конце получает результат с процентами/звёздами/топ-3 и опционально оценивает урок.

Иерархия навигации: Catalog → Quest → Section → Theme → Lesson **→ LessonRunner (эта фича) → QuestionScreen** → ResultScreen.

## Requirements

### Functional Requirements

#### Точка входа и lifecycle

1. **Тап на Lesson** в `LessonListComponent` (фича `quizzes-screen`) — заменяем существующий push `LessonPlaceholderComponent` на push `LessonRunnerRootComponent(lessonId, mode)`, где `mode` определяется состоянием HARD checkbox в `LessonListComponent` (default = EASY если не отмечен). Без отдельной обложки урока: тап → сразу первый вопрос. — [USER DECIDED]

2. **HARD режим** запускается через checkbox **на карточке урока** в `LessonListComponent`. Checkbox видим **только если** у пользователя есть своя завершённая EASY-попытка с `allShownAnswersAre9 == true` (= все показанные digits в codeAnswer = `'9'`) для этого урока — `hardUnlocked == true`. Тогда тап запускает прохождение по hard-pool. Иначе — easy-pool. — [USER DECIDED]

3. **Pool size = 20 константа**. Рандомное подмножество из вопросов нужного difficulty: `subset = randomSubset(eligibleQuestions, min(20, eligibleQuestions.size))`. Подмножество фиксируется на старте попытки и не меняется. — [USER DECIDED]

4. **Difficulty filter**: EASY-попытка → только `Question.difficulty=EASY`; HARD-попытка → только `=HARD`. Смешения нет. — [USER DECIDED]

5. **Empty pool** (для выбранного difficulty `eligibleQuestions.isEmpty()`) → empty state «В уроке пока нет вопросов» с кнопкой «Назад». Прохождение не запускается. — [DELEGATED]

#### Экран вопроса

6. **Один вопрос на экран**. Сверху — индикатор прогресса (текущий вопрос / total в подмножестве, например `3 / 20`) и таймер. Слева вверху — крестик «Выйти». В EASY mode таймер белый/нейтральный фон; в HARD mode визуально другой фон (например `MaterialTheme.colorScheme.errorContainer` или акцентный) — чтобы пользователь видел, что режим жёсткий. — [USER DECIDED]

7. **Картинка вопроса** (опционально, поле в payload или domain — research уточнит): отображается сверху над текстом вопроса, если URL есть и Coil смог загрузить. Loading placeholder/error placeholder — стандартные. — [DELEGATED]

8. **Текст вопроса** — `MaterialTheme.typography.titleMedium` или `bodyLarge`, по центру, ниже картинки. — [DELEGATED]

9. **Варианты ответа по типу**:
   - **SingleChoice** (`options.size in 2..8`): кнопки. ≤5 → один ряд; 6-8 → два ряда. Тап на кнопку = немедленная фиксация ответа.
   - **MultipleChoice** (`options.size in 2..8`, `correctOptionIds.size ≥ 2`): чекбоксы. ≤5 → один ряд; 6-8 → два ряда. Снизу кнопка «Ответить».
   - **Ordering** (`items.size in 2..8`, правильный порядок = порядок в `items`): drag-and-drop элементы. Снизу кнопка «Готово».
   - **FillBlank** (`blanks.size in 1..3`, `candidates.size = 5 или 10`): текст с маркерами `___`, candidates снизу (5 → 1 ряд 5; 10 → 2 ряда по 5). Тап на candidate подставляет в ближайший blank, тап на blank — очищает. Снизу кнопка «Готово».
   — [USER DECIDED] layout `8/10 для двух рядов или 5 для одного` совпадает с ADR-0003 Question schema.

10. **Без feedback-экрана** (правильно/неправильно): после фиксации ответа — мгновенный переход к следующему вопросу. — [USER DECIDED] «после ответа сразу следующий вопрос». Это применяется на обоих режимах (EASY и HARD); ADR-0003 говорит «можно раскрывать правильный ответ на EASY», но юзер явно сказал не делать — это решение пользователя, переопределяющее ADR.

11. **Таймер per вопрос**: формула:
    ```
    charsCount = chars(text) + sum(chars(option/item/candidate text)) + (if hasImage then 100 else 0)
    seconds = max(5, round(charsCount × k))
    ```
    EASY: `k_easy ≈ 0.18` (≈30 сек на 165 знаков). HARD: `k_hard ≈ k_easy / 1.5 ≈ 0.12` (≈20 сек). Min floor 5 секунд (защита от тривиальных коротких вопросов). Картинка в вопросе добавляет 100 знаков-эквивалентов (учёт времени на просмотр). Точные коэффициенты — config константы (tuning в design phase). Когда таймер истекает — auto-random ответ → score фиксируется → переход дальше. — [USER DECIDED] формула из user request, +100 за фото, +5 sec floor.

12. **Auto-answer на timeout**: текущий вопрос «отвечается» рандомным выбором (рандомная option / рандомный subset / рандомная permutation / рандомный candidate per blank — по типу). Score фиксируется по обычным правилам (Matrix 1). Защищает от выгодного выжидания таймера. — [USER DECIDED]

13. **Запрет скриншотов в HARD mode** (`FLAG_SECURE`): включается на старте HARD-попытки, снимается на выходе. EASY mode — без FLAG_SECURE. — [USER DECIDED] «только при сложных вопросов».

#### Защита от подглядывания через task-switcher

14. **При сворачивании (`onStop`)** — текущий вопрос auto-random fill, score фиксируется в state, таймер останавливается. — [USER DECIDED]

15. **При возврате (`onResume`)** — блокирующий **fullscreen диалог «Продолжить прохождение?»** с двумя кнопками: «Продолжить» (закрывает диалог → следующий вопрос с новым таймером, предыдущий не показывается) и «Выйти» (вызов `AbortAttemptUseCase` → unanswered subset positions = `'1'`, out-of-subset = `'0'` → возврат к списку уроков). — [USER DECIDED]

16. **Process kill** (swipe из task-switcher / OOM): попытка теряется полностью, ничего в Room не пишется. — [USER DECIDED] «не нужно сохранять состояние, это другая задача».

17. **Configuration change (rotation)**: Decompose `instanceKeeper` сохраняет component-state. Таймер не сбрасывается; текущий выбор (для MultipleChoice/Ordering/FillBlank) остаётся. — [DELEGATED] standard Decompose pattern.

18. **Крестик во время прохождения** → диалог «Уверены? Прогресс попытки потеряется» → подтвердил → save attempt → возврат на список уроков; отмена → продолжаем. — [DELEGATED]

#### Запись попытки и rating

19. **Сохранение попытки только в Room** через `LessonAttemptRepository.save(attempt)`. **Один write per attempt** в момент: (а) полное прохождение (последний вопрос отвечен), (б) exit-via-dialog в `onResume`, (в) подтвержденный exit через крестик. **Без incremental save** во время прохождения — state только in-memory. — [USER DECIDED]

20. **Cascade sync** (отдельная инфраструктура, не наша фича) синхронизирует Room ↔ Firebase: новые attempts уходят на сервер через WorkManager + `lastModifiedAt` cursor. Та же модель, что для каталогов/квестов/уроков/вопросов в `home-and-my-quests`. Наша фича в Firebase напрямую не пишет. — [USER DECIDED]

21. **Поля `Attempt`** (domain):
   - `id: AttemptId` (UUID)
   - `userId: String` (Firebase Auth UID, snapshot на старте попытки в `RunnerState.Ready.userId`)
   - `lessonId: LessonId`
   - `lessonVersion: Long` (= `Lesson.version` на момент start попытки)
   - `mode: Difficulty` (EASY / HARD)
   - `completedAt: Long` (Unix millis; domain timestamps as Long)
   - `codeAnswer: CodeAnswer` (value object; длина = `eligibleQuestions(mode).size` после canonical pipeline)
   - `percentScore: PercentScore` (value object; 0..100, derived integer math из codeAnswer)
   — [USER DECIDED] минимум полей; lessonVersion добавлен по уточнению.

22. **codeAnswer индексация**: позиция `i` в строке = индекс `i`-го вопроса в `eligibleQuestions(mode).sortedBy { (it.order, it.sourceId) }`, где `eligibleQuestions(mode)` строится pipeline:
    ```
    questions = questionRepository.observeByLesson(lessonId).first()
    valids = questions
        .filter { !it.archived }
        .mapNotNull { q ->
            parser.parse(q.payload).getOrNull()?.let { content ->
                RunnerQuestion.Valid(sourceId = q.id, order = q.order, codeAnswerIndex = -1, content = content)
            }
        }
    eligible = valids.filter { it.content.difficulty == mode }
    sorted = eligible.sortedWith(compareBy({ it.order }, { it.sourceId.raw }))
    indexed = sorted.mapIndexed { idx, rq -> rq.copy(codeAnswerIndex = idx) }
    ```
    Не попавшие в subset (но входящие в eligible) → `'0'` в codeAnswer. — [USER DECIDED]

23. **Score scale (Matrix 1)**:
   - `'0'` = вопрос не показан в этой попытке
   - `'1'` = показан, ответил с 0% правильности (или auto-random попал мимо)
   - `'2'`..`'8'` = частичная правильность по формуле `digit = round(correct_share × 8) + 1`
   - `'9'` = 100% правильно
   - `correct_share ∈ [0, 1]`
   — [USER DECIDED] совпадает с legacy `CODE_MIN_SCORE_ANSWER='1'` / `CODE_MAX_SCORE_ANSWER='9'` / `COUNT_VARIATION_CODE_ANSWER=8`; формула в `legacy/.../QuestionViewModel.kt:188`.

24. **Score per type**:
   - **SingleChoice**: `correct_share = 1.0 если selected == correctOptionId else 0.0`
   - **MultipleChoice (Jaccard)**: `correct_share = correct_picked / (correct_picked + wrong_picked + missed)`, где `correct_picked = |picked ∩ correctOptionIds|`, `wrong_picked = |picked − correctOptionIds|`, `missed = |correctOptionIds − picked|`. Если все три = 0 (не выбрано ничего) → `correct_share = 0.0`.
   - **Ordering**: `correct_share = (matched_positions) / total_positions`, где `matched_positions = count(i: userOrder[i].id == correctOrder[i].id)`.
   - **FillBlank**: `correct_share = (correctly_filled_blanks) / total_blanks`.
   — [USER DECIDED] derived from ADR-0003 + score scale.

#### Звёзды и прогресс на карточке

25. **Звёзды** — domain value class `Stars(rawTenths: Int 0..30)`, что соответствует UI отображению 3 звёзд × 10 частей каждая. UI рендер через `StarRating` компонент (значение в Float = `rawTenths / 10f`) в `android/core/designsystem/components/`. Integer math в domain — нет Float precision issues. — [USER DECIDED]

26. **Stars per attempt formula (Matrix 2)** — integer math:
   - EASY mode: `rawTenths = (percentScore × 20 + 50) / 100`, диапазон [0..20]. Эквивалент `stars = (percentScore / 100) × 2.0` с round half up.
   - HARD mode: `rawTenths = 20 + (percentScore × 10 + 50) / 100`, диапазон [20..30]. Эквивалент `stars = 2.0 + (percentScore / 100) × 1.0`.
   - `percentScore` = integer 0..100 (см. Business Rule 10 в Domain Contract).
   — [USER DECIDED] линейная формула, утверждена.

27. **bestStars per lesson** = `Stars(max(rawTenths) for each attempt)`. Integer max по всем своим попыткам этого урока. — [USER DECIDED]

28. **HARD unlock** = `∃ EASY-попытка с allShownAnswersAre9 == true` (string-based проверка codeAnswer, не percent). Это derived state, не отдельный флаг в БД. — [USER DECIDED]

29. **Карточка урока** в `LessonListComponent` (фича `quizzes-screen`) расширяется:
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
   - `lessonId: String`
   - `lessonVersion: Long`
   - `hardQuestion: Boolean` (true=HARD, false=EASY)
   - `completedAt: serverTimestamp`
   - `codeAnswer: String`
   - `percentScore: Int`
   - `lastModifiedAt: serverTimestamp` (для cascade sync cursor)
   - `version: Long = 1` (immutable после создания)
   — [USER DECIDED] поле `hardQuestion` (не `hardQuiz`) — по уточнению.

33. **Новая Firestore коллекция `lesson_ratings/{ratingId}`** (write-only с клиента; сервер агрегирует — отдельная задача):
   - `userId: String`
   - `lessonId: String`
   - `lessonVersion: Long`
   - `rating: Int` (1, 2 или 3)
   - `ratedAt: serverTimestamp`
   - `lastModifiedAt: serverTimestamp`
   - `version: Long = 1`
   — [USER DECIDED]

34. **Расширение `Lesson` (Firestore + domain)**:
   - `averageRating: Float?` — средняя оценка (1..3) по всем `lesson_ratings`. Сервер агрегирует.
   - `ratingCount: Int?` — количество оценок.
   - `top3: List<TopParticipant>` (size ≤ 3) — `{ nickname, avatarUrl, percent }`. Сервер агрегирует из `lesson_attempts` для текущей `lessonVersion`.
   — [USER DECIDED] поле в Lesson, агрегация серверная (отдельная задача).

   > **Amendment 2026-04-26 (design phase, user-approved)**: `Lesson.ratingCount` изменён с `Int?` на `Int = 0` (non-nullable, default 0) — align с Quest.averageRatingCount pattern (`shared/feature/quest/domain/.../model/Quest.kt:69`). Семантика: missing field = 0 (нет оценок), null более не используется. Resolution applied per `docs/features/lesson-runner/03-decisions.md ADR-LR-15`.

#### DI и module structure

35. **Новые модули**:
   - `shared/feature/lesson-runner/domain/` — pure Kotlin commonMain: `model/`, `state/`, `logic/`, `repository/`, `use_case/`, `di/`. Walking Skeleton генерируется в Phase 3.8.
   - `shared/feature/lesson-runner/data/` — Room adapters (`LessonAttemptDao`, `LessonAttemptEntity`, mappers), `LessonAttemptRepositoryImpl`, `LessonRatingRepositoryImpl`. Реализуется в phase-01 implementation.
   - `android/feature/lesson-runner/presentation/` — Decompose `LessonRunnerRootComponent` + sub-components per вопрос-type, Compose UI screens, DI Koin module.
   — [DELEGATED]

36. **Koin регистрация** — `LessonRunnerPresentationModule`, `LessonRunnerDataModule` добавляются в `apps/android-next/.../AppApplication.kt` startKoin список. — [DELEGATED]

37. **Подключение к `quizzes-screen`** — заменить configuration `LessonPlaceholder` на `LessonRunner(lessonId, mode)` в `QuizzesConfig` sealed class фичи `quizzes-screen`. Это touchpoint в существующей навигации; design phase решит — заменить полностью или оставить placeholder как fallback. — [DELEGATED]

### Non-Functional Requirements

1. **Domain layer purity** — никакого Android/SDK импорта в `shared/feature/lesson-runner/domain/`. Никаких DI аннотаций (нет Hilt в проекте). — [USER DECIDED] по invariant 1.

2. **Walking Skeleton (Variant Y)** — на spec-фазе генерируется полный domain (model, state, logic, repository, use_case + in-memory fakes + JVM tests). Phase-01 в implement интегрирует через Room + sync (не переписывает domain). — [USER DECIDED] Phase 3.8 обязательна.

3. **Тестирование** — JVM unit tests параллельно с Walking Skeleton (Phase 3.8b: test-dev пишет тесты по Domain Test Scenarios). — [DELEGATED]

4. **Compose UI tests** — instrumented tests для key UI: per-type question rendering, timer behavior, FLAG_SECURE applied in HARD mode, dialog onResume. — [DELEGATED]

5. **Brand consistency** — все цвета через `MaterialTheme.colorScheme`. Без hardcoded `Color(0xFF...)`. Соответствует `BrandComponentsInvariantsTest`. — [USER DECIDED]

6. **Decompose Components only** — никакой `AndroidViewModel`/`ViewModel` (см. invariant 2). — [USER DECIDED]

7. **Performance** — typing/answering responsive (<100ms между tap и UI change). Timer tick = 100ms (для плавной анимации). — [DELEGATED]

## Scope

### In Scope

- **Наполнение существующего пустого модуля `shared/core/question-schema/`** — sealed `QuestionContent` (SingleChoice/MultipleChoice/Ordering/FillBlank) согласно ADR-0003 в `commonMain`, plus `QuestionContentParser` interface + `KotlinxSerializationQuestionContentParser` impl. Module already имеет `kotlinx.serialization` dependency в `commonMain` (`shared/core/question-schema/build.gradle.kts:12-15`). Domain purity rule (`.claude/rules/domain-models.md:35-39`) применяется к **feature domain** layers (`shared/feature/*/domain/`), а `shared/core/question-schema` — это shared core module со своими разрешениями. Lesson-runner domain импортирует **только sealed types и parser interface**, не serialization annotations напрямую.
- Новый KMP module `shared/feature/lesson-runner/domain` + Walking Skeleton сгенерирован в Phase 3.8
- Новый KMP module `shared/feature/lesson-runner/data` (Room + sync hooks) — реализация в phase-01
- Новый Android module `android/feature/lesson-runner/presentation` (Decompose Components + Compose UI)
- Новые Room таблицы: `lesson_attempts`, `lesson_ratings_submitted_local` (compound PK `(userId, lessonId)`)
- Расширение Firestore mapping: новые collections `lesson_attempts`, `lesson_ratings` + новые поля в `lessons` (`averageRating`, `ratingCount`, `top3`)
- Расширение `Lesson` domain + entity + dto + Room migration script + Firestore default values для backward compat
- Подключение к `quizzes-screen` ChildStack — замена `LessonPlaceholder` на `LessonRunner`
- Расширение `LessonListComponent` (фича `quizzes-screen`): отображение `bestStars` + checkbox HARD при `hardUnlocked`
- DI module registration в `AppApplication.kt`
- JVM unit tests для domain (Walking Skeleton)
- Compose UI tests для presentation

### Explicitly Out of Scope

- **Cascading sync infrastructure для `lesson_attempts`/`lesson_ratings`** — расширение `home-and-my-quests` cascade sync orchestrator под новые коллекции. Контракт фиксируется здесь, реализация — отдельная phase / задача. Без этого attempts/ratings будут жить только локально.
- **Серверная агрегация** `Lesson.averageRating`, `Lesson.ratingCount`, `Lesson.top3` — Cloud Functions. Контракт фиксируется здесь, server work — отдельная задача.
- **Серверная агрегация аватарок пользователей** в Firestore — отдельная инфраструктурная задача (sync subset of `users/{uid}` documents с avatarUrl).
- **Сохранение состояния прохождения между app sessions** — process kill = lost. «Это другая задача» (user). Decompose `StateKeeper` для runner-state не используется.
- **Лидерборд экран** (full leaderboard urok-а — больше top-3) — отдельная фича.
- **Достижения / streaks / счётчик дней подряд** — отдельная фича.
- **Шеринг результата** — отдельная фича.
- **Время прохождения как метрика** — не сохраняется, не показывается. Только `completedAt` timestamp.
- **Repetition mechanism** (повторение вопросов на которые плохо отвечал) — отдельная фича в будущем.
- **Локализация / language filter** — payload содержит вопрос на одном языке (`Question.language`), MVP не фильтрует.
- **Подсказки / 50-50 / skip** — нет, прохождение «честное».
- **Lesson cover screen перед прохождением** — нет, тап на урок = сразу первый вопрос.
- **Feedback экран после ответа** — нет, сразу следующий вопрос (на обоих режимах).
- **Logout cleanup для local lesson_attempts/ratings** — относится к общей logout-cleanup задаче (отдельной); фича лишь фиксирует требование чистить.
- **Edit / delete своих attempts** — attempts immutable.
- **Несколько одновременных попыток** — UI singleton, невозможно физически.

## User Decisions

| # | Question | Answer | Impact on Design |
|---|----------|--------|-----------------|
| 1 | Slug | `lesson-runner` | название модулей и feature directory |
| 2 | Обложка перед прохождением | Нет, сразу первый вопрос | один экран на entry, без промежуточного |
| 3 | EASY/HARD режимы | Из ADR-0003: сначала EASY, после `allShownAnswersAre9` (perfect EASY) unlocks HARD via checkbox | runtime separation, hardUnlocked derive |
| 4 | Pool size | 20 константа | random subset из eligibleQuestions |
| 5 | Difficulty filter | EASY → only easy, HARD → only hard, без смешения | `eligibleQuestions(mode)` |
| 6 | Score scale | 0-9 (legacy `QuestionDetailLocal`) | `digit = round(share × 8) + 1`; `'0'` = не показан |
| 7 | codeAnswer length | = `eligibleQuestions(mode).size` | разные длины для EASY и HARD попыток |
| 8 | После ответа | Сразу следующий вопрос (на обоих режимах) | без feedback screen, переопределяет ADR-0003 «можно раскрывать на EASY» |
| 9 | Timeout behavior | auto-random выбор → score | защита от выжидания таймера |
| 10 | Timer formula | `(chars × k)`, k_easy ≈ 0.18, k_hard ≈ 0.12 | per-question dynamic |
| 11 | FLAG_SECURE | Только в HARD mode | toggle на старте/выходе HARD-попытки |
| 12 | Сворачивание | onStop auto-random; onResume диалог Продолжить/Выйти | блокирующий overlay, защита task-switcher |
| 13 | Process kill | попытка теряется | no StateKeeper для runner-state |
| 14 | Save attempt | только в Room, один write в конце | через `LessonAttemptRepository`; sync — отдельно |
| 15 | Не сохраняем состояние сессии | подтверждено | runner-state in-memory only |
| 16 | Stars шкала | `Stars(rawTenths: Int 0..30)` value class (3 звезды × 10 частей; integer math) | UI делит на 10 для `StarRating(rating = rawTenths/10f)` |
| 17 | Stars formula (integer math) | EASY: `rawTenths = (percentScore × 20 + 50) / 100`; HARD: `rawTenths = 20 + (percentScore × 10 + 50) / 100` | линейная, integer round half up |
| 18 | bestStars source | max по своим попыткам | derived из Room `lesson_attempts` |
| 19 | HARD unlock condition | ∃ EASY-попытка с `codeAnswer.allShownAnswersAre9 == true` | derived (string-based, не percent) |
| 20 | hardQuestion поле | `hardQuestion: Boolean` (не hardQuiz) | naming согласно user уточнению |
| 21 | lessonVersion | в attempt и в rating | фиксируется на старте/submit |
| 22 | Финальный экран | %, подпись, звёзды, статистика, опрос rating, top3, Завершить | конкретный layout per user request |
| 23 | Rating prompt | После первого `codeAnswer.allShownAnswersAre9 == true` + ¬оценивал | один раз per (userId, lessonId) |
| 24 | Lesson rating storage | `lesson_ratings/{ratingId}` collection + локальный флаг | сервер агрегирует |
| 25 | Top-N | Top-3 в `Lesson.top3` field | агрегация на сервере |
| 26 | Top entry поля | nickname, avatarUrl, percent | аватарки sync через общую инфру |
| 27 | Аватарки fallback | placeholder если не закеширована | как фото вопросов/каталогов |
| 28 | Sync `lesson_attempts` | стандартный cascade (cursor `lastModifiedAt`, фильтр userId==uid) | без кастомного count-comparison |
| 29 | Logout cleanup | local clear (часть отдельной задачи) | фича просто фиксирует требование |

## Server-Side Context

**N/A для прямых writes** — наша фича пишет только в Room через repository interfaces. Cascade sync infrastructure (вне scope) синхронизирует Room ↔ Firebase.

### Server-Side Issues / Required Server Work (контракт, реализация — отдельные задачи)

| Issue | Why Can't Fix on Android | Recommended Server Change | Impact |
|-------|--------------------------|---------------------------|--------|
| Агрегация `Lesson.averageRating`, `ratingCount` | Нужен trigger на write `lesson_ratings` для пересчёта полей в `lessons/{lessonId}` | Cloud Function `onCreate(lesson_ratings)` пересчитывает avg + count по текущей `lessonVersion` | Без CF: rating оценки только локально пишутся, общественное avg не обновляется |
| Агрегация `Lesson.top3` | Нужен trigger на write `lesson_attempts` для пересчёта top-3 для `lessonVersion` | Cloud Function `onCreate(lesson_attempts)` если новая percentScore входит в top-3 — обновляет field | Без CF: top3 всегда пуст, секция на финальном экране скрыта |
| Sync subset аватарок пользователей | Нужны отдельные `users/{uid}` document с `avatarUrl` + способ select-fetch только тех, кто в top3 | Cloud Function или клиентский subset-fetch на основе nickname/uid из top3 entries | Без этого: nickname показывается, аватарки = placeholder |
| Расширение cascade sync под `lesson_attempts` и `lesson_ratings` | Существующий orchestrator в `home-and-my-quests` работает по 6 уровням иерархии; новые коллекции — orthogonal к иерархии (per-user, не per-content-tree) | Дополнительный sync flow: `where userId == uid` + cursor `lastModifiedAt` | Без этого: attempts/ratings локальны, не sync между устройствами |
| Firestore security rules для новых коллекций | Read-own + write-own + immutable после создания | Rules: `lesson_attempts.userId == request.auth.uid` для read и create; no update/delete | Без rules: cross-user data leak возможен |

## Search Criteria for Research

Эту секцию читает `/feature-research`. Что именно research должен найти:

### Existing infrastructure (re-use mapping)

1. **Где сейчас живёт `Question.difficulty`** — в `Question.payload` JSON или нужно добавить как domain field? Текущий `shared/feature/question/domain/.../model/Question.kt` поле `difficulty` НЕ имеет. ADR-0003 говорит «`difficulty` — поле самого вопроса». Research должен (а) проверить есть ли модуль `shared/core/question-schema/` с parsed sealed Question, (б) если есть — задокументировать его API; если нет — это важное открытое решение для design phase: добавить domain field или парсить из payload.

2. **Где живёт `Question.image: ImageRef?`** — в payload или domain? Текущий `Question` domain поля `image` НЕ имеет. Research должен проверить аналогично п.1.

3. **`StarRating` компонент** — `android/core/designsystem/.../components/StarRating.kt`. Задокументировать API: поддерживает ли fractional value (0.0..3.0 шаг 0.1) или только integer? Если только integer — design phase решит расширение или замену.

4. **Decompose `LessonPlaceholderComponent` интеграция в `quizzes-screen`** — `android/feature/quizzes-screen/presentation/`. Найти `QuizzesConfig` sealed class и место push `LessonPlaceholder(lessonId)`. Документировать сигнатуру push, как breadcrumb передаётся, как ChildStack замена работает.

5. **`LessonListComponent`** — найти текущую реализацию в `android/feature/quizzes-screen/presentation/`. Документировать:
   - state model для отображения списка lesson cards
   - какой `HierarchyItemCard` используется и его API (уже есть `subtitleCount` опциональный)
   - как добавить `bestStars` отображение и `Checkbox` для HARD mode

6. **`QuestionRepository.observeByLesson(lessonId)`** — `shared/feature/question/domain/.../repository/QuestionRepository.kt:24`. Документировать полную сигнатуру + sort order (ожидается `order ASC`).

7. **Coil setup в проекте** — найти существующий ImageLoader конфиг. Документировать disk cache settings (для проверки что картинки вопросов и аватарки кешируются).

8. **Cascade sync orchestrator** — `home-and-my-quests` фича. Как добавить новую коллекцию (`lesson_attempts`) в sync flow. Документировать существующий cursor pattern (`lastModifiedAt`).

9. **Decompose `instanceKeeper`** — найти существующие примеры использования. Документировать API для restore in-memory state на rotation.

10. **`AuthRepository.observeUid()` и `currentUid()`** — `shared/feature/app-shell/domain/.../AuthRepository.kt:31` и `:43` (упомянуты в `quizzes-screen` research). Документировать сигнатуру для использования в `LessonAttemptRepository` (write attempt с актуальным `userId`).

11. **FLAG_SECURE pattern в Compose** — найти существующие места `WindowManager.LayoutParams.FLAG_SECURE` или Compose-эквивалент. Если нет — это новый паттерн для проекта; документировать рекомендуемый подход (`LocalView.current.window` или DisposableEffect).

12. **Block-on-resume pattern** — найти существующие диалоги, которые блокируют UI после `onResume`. Если нет — задокументировать требование к новому компоненту.

### Legacy reference

13. **Legacy `QuestionActivity`** — `legacy/common/src/main/java/com/tpov/common/presentation/question/QuestionActivity.kt`. Документировать:
    - Layout кнопок 4 vs 8 (`buttons4`, `buttons8`) — соответствие с ADR-0003 5/10 candidates для FillBlank и 2..8 options для других типов
    - `setupTextViewForDrop()` — drag-and-drop для FillBlank (`activity_quiz_item.xml`)
    - Spring animation между вопросами (`springAnim`)
    - Timer countdown (`startTimer`, `anim321` countdown indicator 3-2-1)
    - `hideSystemUI()` — fullscreen pattern (immersive mode)

14. **Legacy `QuestionViewModel`** — той же директории. Документировать:
    - `checkAnswer(selectedTags, is4Button)` — формула scoring (line 225)
    - `setCodeInCodeAnswer(score)` — кодирование в строку (line 264)
    - `calculateResultByCodeAnswer(codeAnswer)` — derive percent (line 188)
    - `calculatePercentByCodeAnswer()` — current attempt percent (line 192)
    - `setNextQuestion()`, `setPrefQuestion()` — навигация по вопросам (line 254, 259)
    - **NB**: legacy `checkAnswer` имеет inverted логику (correct → MIN, wrong → MAX) — это баг. Наша новая фича должна делать наоборот: correct → 9, wrong → 1. Зафиксировать в Domain Contract test scenarios.

15. **Legacy `QuestionDetailLocal`** — `legacy/.../data/model/local/QuestionDetailLocal.kt`. Документировать:
    - Поля: `data`, `codeAnswer`, `hardQuiz`, `pathStructure`
    - `create(pathStructure, numQuestions)` — initial state для попытки (codeAnswer = "0".repeat(numQuestions))

16. **Legacy `QuizFragment.initPath()`** — `legacy/.../presentation/quiz/QuizFragment.kt:55`. Только для контекста breadcrumb формата (этим занимается `quizzes-screen`, не наша фича).

### Architecture invariants

17. **Domain purity** — новый модуль `shared/feature/lesson-runner/domain/src/commonMain/` НЕ должен импортировать `android.*`, `androidx.*`, Firebase, Room, kotlinx.serialization (на уровне domain). Проверить рекурсивно после Walking Skeleton generation.

18. **Cross-feature coupling** — новый presentation module импортирует `shared/feature/lesson-runner/domain`, `shared/feature/lesson/domain`, `shared/feature/question/domain`, `shared/core/*`. Проверить что не импортирует другие `android/feature/*` (только через `android/core/designsystem`).

19. **Bidirectional coupling check** — `quizzes-screen` будет импортировать lesson-runner config (для push) или lesson-runner будет импортировать что-то из quizzes-screen? Согласовать: `quizzes-screen` импортирует lesson-runner (push нового component), lesson-runner НЕ импортирует quizzes-screen (только pop через NavStack).

20. **Koin binding uniqueness** — для каждого нового exposed type один production binding. Composition root остаётся `apps/android-next/.../AppApplication.kt`.

### Domain model / data contract

21. **`Lesson` model полная сигнатура** — `shared/feature/lesson/domain/.../model/Lesson.kt`. Документировать существующие поля (`title`, `order`, `version`, `contentsVersion`, `lastModifiedAt`, `archived`). Новые поля `averageRating`, `ratingCount`, `top3` — это расширение domain (impacts: data layer mapper, Firestore mapping, sync). Research должен подтвердить что это безопасно (no breaking changes для existing observers).

22. **`Question` model полная сигнатура** — текущий поля (`text`, `payload`, `language`, `order`, `version`, `lastModifiedAt`, `archived`). Если `difficulty` и `image` отсутствуют — research отмечает это как gap, design решит (extend domain vs parse from payload).

23. **Sort order** для `eligibleQuestions(mode)` — research подтверждает что filter по `Question.difficulty` + sort `Question.order ASC` дают стабильный порядок (для consistent codeAnswer индексации).

24. **Random subset stability** — для одной попытки subset должен быть стабилен (не меняется на rotation). Это значит seed для random выбирается на старте попытки и сохраняется в `RunnerState` (Decompose `instanceKeeper`).

### Completeness check

- Для каждого entry point (тап на Lesson из `LessonListComponent`) задокументировать **полную цепочку** UI → Component → Use Case / Repository.
- Для каждой `LessonAttemptRepository` / `LessonRatingRepository` метод задокументировать ожидаемую сигнатуру согласно Walking Skeleton (Phase 3.8 output).
- Для каждого типа вопроса (SingleChoice/MultipleChoice/Ordering/FillBlank) задокументировать конкретный existing payload-format (если есть) или схему ADR-0003.
- Для FLAG_SECURE проверить что HARD-mode toggle не «протекает» при rotation / process kill restart.
- grep + manual verification:
  - `Question.difficulty` references — есть ли в `data/`, `presentation/`, `quiz-creation/`?
  - `lesson_attempts` Firestore mention — упоминается ли где-то существующее sync infrastructure?
  - `FLAG_SECURE` mention в проекте — есть ли legacy паттерн?
  - `top3`/`leaderboard`/`top_participants` — есть ли существующие модели или это полностью новая концепция?

## Primary User Journeys

1. **Happy path EASY first time**
   - Start: `LessonListComponent` (фича `quizzes-screen`), пользователь видит карточку урока с 0★, без HARD checkbox.
   - Trigger: тап на урок.
   - State changes: push `LessonRunnerRootComponent(lessonId, mode=EASY)` → загружает `Question`s урока, фильтрует EASY, выбирает random subset 20 → state `Loading → Ready` → отображение первого вопроса.
   - User отвечает (тап / drag / fill) → `SubmitAnswerUseCase` фиксирует score → next question.
   - На последнем вопросе → `CompleteAttemptUseCase` → save attempt в Room → переход на result screen.
   - Если `codeAnswer.allShownAnswersAre9 == true` (perfect EASY) → подпись «Сложные доступны» + опрос «Оцените урок».
   - User оценивает 1/2/3 целых звезды → submit rating → set local флаг.
   - User тапает «Завершить» → pop ChildStack → возврат в `LessonListComponent` → карточка теперь показывает 2.0★ + HARD checkbox.
   - Decision: [USER DECIDED]

2. **Happy path EASY с ошибками → 1.5★ (UI display)**
   - Start: `LessonListComponent`, тап на урок без stars.
   - User проходит 20 вопросов, на нескольких ошибается / timeouts.
   - `percentScore` = 75 → `Stars(rawTenths = 15)` (= 1.5 для UI).
   - Result: процент 75, звёзды 1.5, нейтральная подпись «Урок завершён», без опроса rating (`allShownAnswersAre9 == false`).
   - User тапает «Завершить» → возврат, карточка показывает Stars(rawTenths=15), HARD checkbox **скрыт** (нет EASY с allShownAnswersAre9).
   - Decision: [USER DECIDED]

3. **HARD attempt после unlock**
   - Start: `LessonListComponent`, карточка урока bestStars=Stars(20) (UI 2.0) + HARD checkbox visible (`hardUnlocked=true`).
   - Trigger: пользователь включает HARD checkbox + тапает на урок.
   - State changes: push `LessonRunnerRootComponent(lessonId, mode=HARD)`. FLAG_SECURE включается. Random subset из HARD pool, разные коэффициенты таймера.
   - User проходит, `percentScore=80` → `Stars(rawTenths=28)` (UI 2.8).
   - Result: процент 80, звёзды 2.8 (UI), нейтральная подпись.
   - bestStars урока теперь = `Stars(max(20, 28)) = Stars(28)`.
   - Без опроса rating (`allShownAnswersAre9 == false`).
   - User тапает «Завершить» → FLAG_SECURE снимается → возврат.
   - Decision: [USER DECIDED]

4. **Perfect HARD → 3.0 + первый rating**
   - Start: уже есть Stars(20) от EASY perfect (allShownAnswersAre9), не оценивал урок.
   - User проходит HARD perfect (`codeAnswer.allShownAnswersAre9 == true`, `percentScore=100`) → `Stars(rawTenths=30)`.
   - Result: 100, подпись «Сложные пройдены полностью», звёзды 3.0 (UI), опрос rating (первый perfect, не оценивал).
   - User оценивает → submit rating с `lessonVersion` = текущая.
   - bestStars урока теперь Stars(30).
   - Decision: [USER DECIDED]

5. **Сворачивание + возврат**
   - Start: пользователь на 5-м вопросе.
   - Trigger: нажал Home / переключил приложение.
   - State changes: `onStop` → текущий вопрос auto-random fill → score фиксируется в state. Таймер останавливается.
   - User возвращается → `onResume` → fullscreen диалог «Продолжить прохождение?».
   - Sub-Journey 5a: тап «Продолжить» → диалог закрыт → 6-й вопрос с новым таймером (5-й не показывается).
   - Sub-Journey 5b: тап «Выйти» → save attempt в Room (codeAnswer: 1-4 ответы реальные, 5-20 = '1' для непоказанных) → возврат в `LessonListComponent`.
   - Decision: [USER DECIDED]

6. **Process kill во время прохождения**
   - Start: пользователь на 10-м вопросе.
   - Trigger: swipe из task-switcher / OOM kill.
   - State changes: процесс убит. Никакого save в Room. Никакого Firebase write.
   - User возвращается → запуск приложения с нуля → возможно tab Home / список уроков → карточка урока показывает state ДО этой попытки.
   - Decision: [USER DECIDED]

7. **Configuration change (rotation) во время прохождения**
   - Start: пользователь на 7-м вопросе с введёнными blanks (FillBlank).
   - Trigger: rotation portrait → landscape.
   - State changes: Decompose `instanceKeeper` сохраняет `RunnerState`. Component не пересоздаётся. Таймер не сбрасывается. Введённые blanks остаются.
   - Expected result: UI перерисовывается, scroll position и input state preserved.
   - Decision: [DELEGATED]

8. **Empty pool для урока**
   - Start: пользователь тапает урок, у которого все вопросы archived или нет вопросов выбранного difficulty (например выбрал HARD, но в уроке нет HARD-вопросов — но это противоречит unlock condition; альтернатива: cascade sync ещё не подтянул).
   - State changes: push `LessonRunnerRootComponent` → state `Loading → InitFailed(EmptyPool)`.
   - Expected result: empty state «В уроке пока нет вопросов» + кнопка «Назад» (= pop).
   - Decision: [DELEGATED]

9. **Невалидный payload вопроса**
   - Invalid payload **исключается на этапе init** в `StartLessonAttemptUseCase` (см. canonical pipeline шаг 3 в Requirement 22). До прохождения никогда не доходит.
   - Если у урока **все** Question имеют invalid payload → `InitFailed(NoValidQuestions)` → empty state «В уроке нет валидных вопросов».
   - Если хотя бы один валидный есть → invalid просто отбрасываются, eligibleQuestions содержит только Valid; user проходит без них.
   - Decision: [DELEGATED]

10. **Offline во время прохождения**
    - Start: пользователь без интернета.
    - State changes: вопросы и Lesson уже в Room (cascade sync ранее), картинки в Coil disk-cache (если были).
    - User проходит, в конце save attempt в Room. Cascade sync неактивен (offline) — attempt пишется на Firestore позже когда онлайн вернётся (Firestore offline persistence через cascade sync).
    - Top-3 на финальном экране — закешированный snapshot из `Lesson.top3`. Аватарки — placeholder если не закеширована.
    - Expected result: full attempt работает offline; результат корректен; top3 может быть outdated.
    - Decision: [DELEGATED]

11. **Logout / login (account switch)**
    - Start: пользователь user1 имеет 2.0★ для урока X (одна perfect EASY попытка).
    - Trigger: logout → новый anonymous uid (часть logout-cleanup задачи: clears local lesson_attempts).
    - State changes: lesson_attempts Room таблица очищается → bestStars для всех уроков 0★, hardCheckbox скрыт.
    - User проходит снова — это новая попытка нового uid.
    - Decision: [USER DECIDED] cleanup — отдельная задача, наша фича лишь требует чистить наши таблицы.

12. **Параллельный sync во время прохождения**
    - Start: пользователь на 8-м вопросе.
    - Trigger: WorkManager runs cascade sync, обновляет `Lesson.top3` и `Lesson.averageRating` в Room.
    - State changes: in-memory `RunnerState` snapshot вопросов фиксирован на старте, не меняется. Финальный экран result читает обновлённый `Lesson.top3` (свежий).
    - Expected result: прохождение не прерывается; top-3 актуальный на result screen.
    - Decision: [DELEGATED]

13. **Fresh install — нет вопросов в Room**
    - Start: только установил приложение, тап на урок.
    - Trigger: cascade sync не успел подтянуть Question-ы.
    - State changes: `observeByLesson(lessonId)` отдаёт empty list → state `InitFailed(EmptyPool)`.
    - Expected result: empty state «В уроке пока нет вопросов». User ждёт sync. (Issue infrastructure-level — не наша фича.)
    - Decision: [USER DECIDED] N/A для нас, ограничение sync infrastructure.

14. **Rating prompt уже заполнен раньше**
    - Start: user уже оценивал этот урок (есть local флаг). Прошёл perfect EASY повторно.
    - State changes: на финальном экране опрос rating НЕ показывается (даже несмотря на perfect).
    - Expected result: подпись/звёзды/статистика/top3/Завершить — без rating prompt.
    - Decision: [USER DECIDED]

## Feature Domain Contract

### Terms / Entities / Value Constraints

- **AttemptId** — `value class AttemptId(val raw: String)`. UUID String (auto-generated при создании Attempt). Validated `raw.isNotBlank()`.
- **RatingId** — `value class RatingId(val raw: String)`. Deterministic `sha256("$userId:$lessonId")`. Validated `raw.isNotBlank()`.
- **InitFailureReason** — sealed: `EmptyPool | NoValidQuestions | LessonNotFound | AuthRequired`.
- **SaveError** — sealed: `IoFailure(throwable) | UnknownError(throwable)`. (НЕ содержит AuthRequired — userId snapshot фиксируется в `RunnerState.Ready.userId` на старте, save use cases не делают повторный auth read.)
- **Difficulty** — enum `EASY | HARD`.
- **Score** — Int 0..9 (validated в init); `0` = special "не показан в этой попытке"; `1`..`9` = показан, разный share правильности.
- **CodeAnswer** — String value object: длина ≥ 1, каждый char ∈ `'0'..'9'`. Validated в init.
- **PercentScore** — Int 0..100 (validated `0 <= raw <= 100`). Derived из CodeAnswer integer math:
  - `nonZeroDigits = codeAnswer.filter { it != '0' }`
  - Если `nonZeroDigits.isEmpty()` → 0
  - Иначе: `sum = nonZeroDigits.sumOf { (it.digitToInt() - 1) * 100 / 8 }`; `percentScore = sum / nonZeroDigits.size` (integer division — детерминирует)
- **Stars** — value class `Stars(val rawTenths: Int)` где `rawTenths ∈ [0..30]` (validated в init). UI делит на 10 для Float отображения. Integer math для всех операций — нет Float precision issues. Это **derived UI value**, НЕ хранится в Attempt.
- **TimerDuration** — Int seconds, derived from question content + mode coefficient. Validated `>= 5` (min floor).
- **Attempt** — immutable data class: `(id: AttemptId, userId: String, lessonId: LessonId, lessonVersion: Long, mode: Difficulty, completedAt: Long, codeAnswer: CodeAnswer, percentScore: PercentScore)`. `userId.isNotBlank()`, `lessonVersion >= 1`, `completedAt >= 0`.
- **LessonRating** — immutable: `(id: RatingId, userId: String, lessonId: LessonId, lessonVersion: Long, rating: Int (1..3), ratedAt: Long)`. Validated.
- **TopParticipant** — `(nickname: String, avatarUrl: String?, percent: Int)`. `nickname.isNotBlank()`, `percent ∈ 0..100`.
- **QuestionContent** — sealed (parsed by `shared/core/question-schema/QuestionContentParser`): см. ADR-0003. Импортируется из question-schema как уже типизированная sealed Question.
- **RunnerState** — sealed:
  - `Loading` — стартовое состояние пока не загружены вопросы
  - `InitFailed(reason: InitFailureReason)` — sealed: `EmptyPool | NoValidQuestions | LessonNotFound | AuthRequired`
  - `Ready(userId: String, lessonId: LessonId, lessonVersion: Long, mode: Difficulty, playOrder: List<RunnerQuestion.Valid>, eligibleSize: Int, indexInPool: Int, codeAnswer: CodeAnswer, deadlineMs: Long, seed: Long, currentDraftAnswer: UserAnswerDraft?, isPaused: Boolean)` — активное прохождение
    - `userId` — snapshot Firebase Auth UID на старте попытки (читается из `AuthRepository.currentUid()` в `StartLessonAttemptUseCase`). Save use cases используют этот snapshot, не делают повторный auth read.
    - `playOrder: List<RunnerQuestion.Valid>` — отсортированный subset в порядке показа (sortedBy `Question.order` ASC, ties broken by `Question.id`). Только `Valid`: invalid payloads уже отфильтрованы при init. (Sealed `RunnerQuestion` объявлен для будущих edge cases, но в playOrder только `.Valid`.)
    - `eligibleSize` — total eligibleQuestions(mode) после parse + filter difficulty (= длина codeAnswer)
    - `indexInPool` — позиция текущего вопроса в `playOrder`. Invariant: `0 <= indexInPool <= playOrder.size`. Значение `playOrder.size` — sentinel «complete»; этот state кратковременный, сразу после `submitAnswer` на последнем вопросе → component вызывает `CompleteAttemptUseCase` → переход в `Completed`/`SaveFailed`.
    - `seed` — для детерминизма subset selection при rotation; фиксируется на старте
    - `currentDraftAnswer` — partial input для MultipleChoice/Ordering/FillBlank (не submitted ещё)
    - `isPaused` — флаг что показан onResume диалог «Продолжить?»

  Текущий `codeAnswerIndex` для записи score берётся из `playOrder[indexInPool].codeAnswerIndex`.
  - `Completed(attempt: Attempt, ratingPrompt: Boolean)` — финал, attempt сохранён
  - `Aborted(attempt: Attempt)` — exit-via-dialog или confirmed cross, attempt сохранён
  - `SaveFailed(attempt: Attempt, error: SaveError)` — Room write throw; user видит result с warning «Не удалось сохранить, попробуйте позже»; в MVP no auto-retry, см. P24
- **QuestionContent** — sealed (импорт из `shared/core/question-schema/src/commonMain/`): `SingleChoice/MultipleChoice/Ordering/FillBlank` согласно ADR-0003. Invariants enforced в init того модуля. `QuestionContent` имеет `id`, `difficulty`, `text`, `image?`, и type-specific поля. **НЕ имеет** `Question.order`, `Question.archived` (это поля domain `Question`, не parsed schema).
- **RunnerQuestion** — domain wrapper, sealed для разделения valid/invalid:
  ```kotlin
  sealed interface RunnerQuestion {
      val sourceId: QuestionId
      val order: Int
      val codeAnswerIndex: Int

      data class Valid(
          override val sourceId: QuestionId,
          override val order: Int,
          override val codeAnswerIndex: Int,
          val content: QuestionContent,  // parsed sealed Question
      ) : RunnerQuestion

      data class Invalid(
          override val sourceId: QuestionId,
          override val order: Int,
          override val codeAnswerIndex: Int,
          val parseError: String,  // describing reason
      ) : RunnerQuestion
  }
  ```
  Используется в `RunnerState.Ready.playOrder: List<RunnerQuestion>`. `StartLessonAttemptUseCase` строит:
  1. Загружает `List<Question>` через `QuestionRepository.observeByLesson(lessonId).first()`.
  2. Фильтрует `!archived`.
  3. Для каждого `Question`: `parser.parse(question.payload)` → если success → `RunnerQuestion.Valid` с `content`; если failure → `RunnerQuestion.Invalid`.
  4. Фильтрует по `mode`: `Valid` где `content.difficulty == mode`. **`Invalid` исключаются из subset на этапе фильтра** (нельзя определить difficulty без content). Они просто не попадают в pool.
  5. Сортирует по `(order, sourceId)` ASC.
  6. Применяет `selectSubset(eligible, 20, seed)`.

  Order of failures (init priority — соответствует фактической реализации Walking Skeleton):
  1. `authRepository.currentUid() == null` → `InitFailed(AuthRequired)` — guard в самом начале (не имеет смысла продолжать без uid).
  2. `lessonRepository.getById(lessonId) == null` → `InitFailed(LessonNotFound)` — урок не существует/архивирован.
  3. `eligibleQuestions(mode).isEmpty()` после parse + filter → `InitFailed(EmptyPool)`.
  4. `pool.all { invalid }` (все active questions имели invalid payloads) → `InitFailed(NoValidQuestions)`.
  
  В реальности EmptyPool и NoValidQuestions могут оказаться эквивалентны (если все invalid → eligibleSize=0 → EmptyPool). Walking Skeleton делает их различимыми через подсчёт parsed/invalid count.

  `Invalid` фактически **не существует в playOrder** в нормальном flow (фильтр их выкидывает). Sealed type оставлен на случай будущих edge cases (hot-reload во время прохождения и т.д. — для design phase).
- **UserAnswer** — sealed:
  - `SingleChoiceAnswer(selected: OptionId?)` — null = timeout без действия
  - `MultipleChoiceAnswer(selected: Set<OptionId>)`
  - `OrderingAnswer(order: List<OptionId>)`
  - `FillBlankAnswer(filled: Map<BlankId, CandidateId?>)` — value=null значит blank пуст
- **UserAnswerDraft** — параллельная структура для in-progress input, который пользователь ещё не submit-нул. Сериализуется в `instanceKeeper` для rotation.

### Business Rules / Invariants / Guards

1. **Pool size = 20 константа**. `subset = seededRandomSubset(eligibleQuestions, min(20, eligibleQuestions.size), seed)`. Если eligibleQuestions empty → `InitFailed(EmptyPool)`.
2. **Difficulty filter (active eligible questions snapshot at start)**: см. canonical pipeline в Requirement 22. Pseudo-summary:
    1. `questionRepository.observeByLesson(lessonId).first()` — snapshot list `Question`.
    2. `.filter { !it.archived }` — drop archived.
    3. `.mapNotNull { parser.parse(it.payload).getOrNull()?.let { content -> RunnerQuestion.Valid(...) } }` — drop invalid payloads.
    4. `.filter { it.content.difficulty == mode }` — keep only mode questions.
    5. `.sortedWith(compareBy({ order }, { sourceId.raw }))` — стабильный порядок.
    6. `.mapIndexed { idx, rq -> rq.copy(codeAnswerIndex = idx) }` — присвоить позиции в codeAnswer.

    EASY-попытка → only EASY; HARD → only HARD. Snapshot фиксируется на старте, изменения cascade sync во время прохождения игнорируются. Invalid payloads исключаются на шаге 3 — до прохождения они НЕ доходят (если все invalid → `InitFailed(NoValidQuestions)`).
3. **Subset selection determinism**: `seed = System.currentTimeMillis()` (или provided clock) фиксируется на старте, сохраняется в `RunnerState.seed` для restore через `instanceKeeper` на rotation.
4. **playOrder ordering**: `playOrder = subset.sortedBy(it.order then it.id)` (НЕ shuffled). Стабильный порядок показа. Дубликаты `Question.order` устраняются через secondary sort key `id`.
5. **codeAnswer длина** = `eligibleQuestions(mode).size` (НЕ total questions урока, НЕ subset size).
6. **codeAnswer индекс** = позиция `Question` в `eligibleQuestions(mode)` (отсортированном по `(order, id)`). Не входящие в subset (out-of-subset) → `'0'`. Входящие в subset, но валидные но непрогресированные (timeout / abandoned mid-subset) → `'1'`. Входящие в subset с invalid payload → `'1'` (показан, но не отвечен корректно).
7. **Score scale**:
   - `'0'` = вопрос НЕ был показан в этой попытке (out-of-subset)
   - `'1'`..`'9'` для показанных или попытавшихся показаться:
     - `'1'` = 0% правильности (full miss / timeout без угадывания / invalid payload)
     - `'5'` = 50%
     - `'9'` = 100%
   - Формула: `digit = round(correct_share × 8) + 1`, где `correct_share ∈ [0, 1]`.
8. **Score formula per type**:
   - SingleChoice: `correct_share = if (selected == correctOptionId) 1.0 else 0.0`. Guard: `selected ∈ options.map { it.id }` или null. Если selected ∉ options → trait как null (timeout).
   - MultipleChoice (Jaccard): `correct_share = correct_picked / (correct_picked + wrong_picked + missed)` — эквивалентно `|picked ∩ correctOptionIds| / |picked ∪ correctOptionIds|`. Если denominator == 0 (=пустой union: невозможно поскольку correctOptionIds.size ≥ 2 по ADR) → 0.0. Guard: `picked ⊆ options.map { it.id }`. Любые foreign IDs игнорируются (treat as wrong по умолчанию через filter).
   - Ordering: `correct_share = matched_positions / total_positions`, `matched_positions = items.indices.count { i -> userOrder.getOrNull(i) == correctOrder[i].id }`. Guard: `userOrder.size == items.size` и `userOrder.toSet() == items.map{it.id}.toSet()` (perm check); если invalid → score 0% (`'1'`).
   - FillBlank: `correct_share = filled_correct / total_blanks`. `filled_correct = blanks.count { filled[it.id] == it.correctCandidateId }`. Guard: `filled.values.filterNotNull() ⊆ candidates.map { it.id }`. null/foreign → wrong.
9. **Auto-answer на timeout**:
   - SingleChoice: `selected = options.random(seedFor(question))`
   - MultipleChoice: `selected = correctOptionIds.size случайных options` (детерминированно через seed)
   - Ordering: `userOrder = items.shuffled(seedFor(question))`
   - FillBlank: каждый blank → `candidates.random(seedFor(question))` (с возвратом — same candidate может попасть в несколько blanks; design phase решит UI блокировку, но domain-level fine)
   - Score фиксируется по тем же правилам.
10. **percentScore (Int)**: `nonZero = codeAnswer.count { it != '0' }`. Если `nonZero == 0` → 0. Иначе: `sum = codeAnswer.filter { it != '0' }.sumOf { (it.digitToInt() - 1) * 100 / 8 }`; `percentScore = sum / nonZero` (integer division — детерминирует).
11. **Perfect attempt detection** (для unlock и rating prompt): `attempt.allShownAnswersAre9 = codeAnswer.all { it == '0' || it == '9' }` AND `codeAnswer.any { it == '9' }`. Это **string-based**, НЕ percent-based — нет Float precision issues.
12. **Stars per attempt formula** (derived value class `Stars(rawTenths: Int 0..30)`):
    - EASY: `rawTenths = (percentScore * 20 + 50) / 100` → `[0..20]`. Эквивалент `stars = (percentScore / 100) × 2.0` с round half up.
    - HARD: `rawTenths = 20 + round(percentScore * 1 / 10)` → `[20..30]`. Точно: `tenths = 20 + (percentScore * 10 + 50) / 100`.
    - Все математика integer. Float только при UI rendering.
13. **bestStars per lesson** = `max(rawTenths per attempt)`. Integer max по всем своим попыткам этого `lessonId`. Если нет попыток — `Stars(0)`.
14. **HARD unlock condition** = `attempts.any { it.mode == EASY && it.codeAnswer.allShownAnswersAre9 }`. String-based, не percent. **НЕ** через bestStars (HARD attempt с percentScore=0 даёт `Stars(rawTenths=20)` floor, но НЕ unlocks).
15. **Checkbox visibility on lesson card**: `hardUnlocked == true`. **НЕ** через bestStars (HARD attempt floor `Stars(rawTenths=20)` не unlocks).
16. **Rating prompt visibility** = `attempt.allShownAnswersAre9 && !ratingsRepo.hasSubmitted(userId, lessonId)`. **String-based**, не percent-based.
17. **lessonVersion fixation**: `attempt.lessonVersion = lesson.version` на момент `StartLessonAttemptUseCase` invocation (snapshot когда state ещё `Loading`). Если `Lesson` Flow emit-ит обновление между UseCase invocation и завершением — игнорируется. `rating.lessonVersion = lesson.version` на момент `submit` (свежий read).
18. **Attempt save = только в конце**: один `LessonAttemptRepository.save(attempt)` в:
    - `CompleteAttemptUseCase` (последний вопрос отвечен) → `RunnerState.Completed`
    - `AbortAttemptUseCase` (exit-via-dialog или confirmed cross) → `RunnerState.Aborted`
    - **НЕ при process kill** (lost).
    - При Room exception → `RunnerState.SaveFailed(attempt, error)`. UI показывает result с warning. No auto-retry в MVP.
19. **No incremental save** — runner state живёт только in-memory + Decompose `instanceKeeper` для rotation.
20. **Subset stability**: subset через seed фиксируется на старте попытки.
21. **Timer formula**:
    ```
    charsCount = chars(text) + sum(chars(option_or_item_or_candidate text)) + (if hasImage then 100 else 0)
    seconds = max(5, round(charsCount × k))
    ```
    `k_easy ≈ 0.18`, `k_hard ≈ k_easy / 1.5 ≈ 0.12`. Min floor 5 секунд. Точные коэффициенты и +100-bonus — config константы в `lesson-runner/domain/.../config/TimerCoefficients.kt`.
22. **FLAG_SECURE**: enabled только в HARD-mode runner (toggle on `RunnerState.Ready` enter с mode=HARD; untoggle on `Completed/Aborted/SaveFailed/InitFailed` exit).
23. **Attempt immutability** — после save Attempt в Room не редактируется (только создание новых).
24. **Rating uniqueness — lifetime per (userId, lessonId)** (упрощено для consistency):
    - Local Room PK: compound `(userId, lessonId)` в таблице `lesson_rating_submitted_local`. Один раз поставил → больше не предлагается.
    - Remote ID: deterministic `sha256("$userId:$lessonId")` (БЕЗ lessonVersion в ключе). Cloud Function dedupe через document-id collision (existing → ignore new).
    - `lessonVersion` сохраняется в payload документа `lesson_ratings` для server-side analytics (понимать какая версия оценивалась), но НЕ участвует в uniqueness key.
    - Если автор кардинально поменяет урок и захочет «обнулить» все оценки — это server-side policy (отдельная задача, не наша фича).
25. **lessonVersion в `LessonRating` payload** — analytics поле, не uniqueness key. Фиксируется на момент submit.

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

## Delegated Decisions Summary

| # | Область | Решение агента | Обоснование | Risk |
|---|---------|---------------|-------------|------|
| 1 | Empty state text | «В уроке пока нет вопросов» | Соответствует existing pattern в `MyQuestsScreen` / `quizzes-screen` | Low |
| 2 | Loading indicator | `CircularProgressIndicator` по центру | Existing pattern | Low |
| 3 | Скрытие feedback после ответа на EASY | Применяем требование пользователя на оба режима, переопределяя ADR-0003 | User explicit «после ответа сразу следующий», без условий per mode | Medium — переопределяем ADR; должно быть зафиксировано в design phase ADR amendment |
| 4 | Image loading | Coil из URL + placeholder при offline | Соответствует existing для каталогов/квестов | Low |
| 5 | Spring/animation между вопросами | Не реализуем в MVP (legacy `springAnim`) | Усложнение без чёткого UX value | Low — добавим в polish phase |
| 6 | Fullscreen immersive mode | Не делаем (legacy `hideSystemUI`) | В Compose это неприоритет; FLAG_SECURE достаточно | Low |
| 7 | 3-2-1 countdown indicator | Не реализуем в MVP (legacy `anim321`) | Усложнение | Low |
| 8 | Timer min floor | Если расчёт < 5 sec → принудительно 5 sec | Защита от undue strict timer на коротких вопросах | Low |
| 9 | Подпись на result screen для нейтрального исхода | «Урок завершён» | Простая, нейтральная | Low |
| 10 | Error retry для save attempt | Не делаем в MVP (просто log + lost) | Persist queue — overengineering для MVP | Medium — данные могут теряться при IO error |
| 11 | Compose UI tests scope | Critical paths only (per-type rendering, timer, FLAG_SECURE toggle, dialog) | Полный coverage — отдельная задача | Low |
| 12 | Ordering UI (drag) | drag-and-drop через `Modifier.draggable` или up/down arrows | drag — natural но сложнее в Compose; arrows — accessible. Design phase решит | Low |
| 13 | FillBlank UI candidate placement | tap candidate → подставляется в "ближайший" пустой blank по text position; tap blank → очищается | Соответствует legacy paradigm | Low |
| 14 | Per-question type sub-components | Каждый тип — отдельный Composable + соответствующий sub-state | Structural clarity, отдельные тесты | Low |
| 15 | DI module location | `android/feature/lesson-runner/presentation/.../di/LessonRunnerPresentationModule.kt` + `shared/feature/lesson-runner/data/.../di/LessonRunnerDataModule.kt` | Соответствует existing pattern | Low |
| 16 | Local флаг «уже оценил» | Room таблица `lesson_rating_submitted_local` с compound PK `(userId, lessonId)`, поле `ratedAt: Long` | Compound PK защищает от double-rating одного user, но позволяет другому user оценить тот же lesson | Low |
| 17 | Default avatar placeholder | `Icons.Filled.Person` или material default avatar | Простой fallback | Low |
| 18 | Top3 секция при пустом списке | Скрыта (no «Топ пуст» текста) | Чище UX | Low |
| 19 | Configuration change recovery | Decompose `instanceKeeper` для всего RunnerState | Standard Decompose pattern | Low |
| 20 | Walking Skeleton structure | `model/`, `state/`, `logic/`, `repository/`, `use_case/` + tests + fakes | Соответствует skill `domain-modeling` | Low |

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

## Acceptance Criteria

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

## Invariant Check (from docs/invariants.md)

| Invariant | Impact | Decision |
|-----------|--------|----------|
| 1. Domain layer purity | Новый `shared/feature/lesson-runner/domain/` | preserve — Walking Skeleton содержит только pure Kotlin commonMain. Никаких Android/SDK/DI annotations. Verify grep после Phase 3.8 |
| 2. Presentation does not bypass domain | `android/feature/lesson-runner/presentation` через Decompose Components → Use Cases → Repository | preserve — Compose screens получают state/callbacks; никаких Koin lookups в Composables; никаких DAO direct |
| 3. No bidirectional coupling between feature modules | `quizzes-screen` → импорт lesson-runner config (для push); lesson-runner НЕ импортирует quizzes-screen | preserve — однонаправленный import; pop через ChildStack без знания о quizzes-screen |
| 4. onDestroy is not for business cleanup | Decompose `doOnDestroy` для cancel scope, не для save attempt | preserve — save attempt в use case, вызывается из CompleteAttemptUseCase / AbortAttemptUseCase, не в lifecycle hook |
| 5. Koin binding uniqueness | `LessonAttemptRepository`, `LessonRatingRepository`, etc. — один production binding per type | preserve — Koin manual DI |
| 6. Walking Skeleton ownership | Phase 3.8 генерирует domain | preserve — `domain-designer` агент в Phase 3.8a/3.8b. Не переписывается в downstream |
| 7. Scaffold file ownership | Новые `build.gradle.kts` для 3 модулей + entries в `settings.gradle.kts` + Manifest entries | preserve — backend-dev владеет; другие teammates запрашивают через lead |

## Constraints (from PROJECT-CONTEXT.md)

- **KMP** — `shared/feature/lesson-runner/{domain,data}/src/commonMain` для cross-platform code; Android-specific живёт в `android/feature/lesson-runner/presentation`. Walking Skeleton тесты в `commonTest` (или `jvmTest` если используется JUnit4).
- **Koin manual DI** — composition root `apps/android-next/.../AppApplication.kt` startKoin. Новые модули добавляются в список.
- **Decompose Components** — pattern из `home-and-my-quests/03-decisions.md` ADR-CMP-51. `LessonRunnerRootComponent` + child components per вопрос-type.
- **Compose** — view functions, no direct Koin resolution. State/callbacks через Component public API.
- **Build commands** — `./gradlew ciCheck --no-configuration-cache` для full quality gate; `./gradlew :apps:android-next:assembleDebug --no-configuration-cache` для app build; `./gradlew test --no-configuration-cache` (Android/app JVM) и `./gradlew allTests --no-configuration-cache` (KMP JVM) — оба нужны для full coverage.
- **Testing** — JUnit 4 + MockK + coroutines-test; fakes convention; no Turbine; Flow tests через `.take()`/`.toList()`/`StateFlow.value`/`StandardTestDispatcher`.
- **Naming** — package `com.tpov.schoolquiz.shared.feature.lesson_runner.domain` / `com.tpov.schoolquiz.android.feature.lesson_runner.presentation` (или `lesson-runner` / `lessonRunner` — design phase).
- **Brand** — `MaterialTheme.colorScheme.*`, no hardcoded colors. `BrandComponentsInvariantsTest` validation.
- **Cascade sync infrastructure** — `home-and-my-quests` orchestrator pattern; новые collections (`lesson_attempts`, `lesson_ratings`) расширяются orthogonally к 6-уровневой иерархии (per-user, не per-tree). Это infrastructure work — отдельная задача.

## ADR Amendments Required

### ADR-0003 (Question schema) — четыре правки нужны для согласования

**Amendment A**: Runtime rule «Ошибка или просрочка таймера на EASY → прохождение заканчивается на EASY, максимум 2★» (`docs/architecture/0003-question-schema.md:122-125`) **перекрывается** decision этой фичи: прохождение продолжается до конца независимо от ошибок. Звёзды считаются по итоговому codeAnswer. Это user-explicit decision: «после ответа сразу следующий вопрос». Amendment текст для добавления в ADR:

> **Amendment 2026-04-26 (lesson-runner spec)**: Прохождение EASY НЕ прерывается на ошибке или таймауте — продолжается до конца pool. Звёзды считаются по итоговому `percentScore` через integer formula `rawTenths = (percentScore * 20 + 50) / 100` для EASY (см. lesson-runner `0-spec.md` Business Rule 12). Прерывание упомянутое в первоначальном тексте — отменено.

**Amendment B**: Runtime rule «Можно раскрывать правильный ответ после ответа на вопрос» для EASY (`docs/architecture/0003-question-schema.md:128`) **перекрывается**: после ответа сразу следующий вопрос, без feedback-экрана, на обоих режимах. Amendment:

> **Amendment 2026-04-26 (lesson-runner spec)**: На EASY и HARD — после ответа сразу переход к следующему вопросу, без раскрытия правильного ответа. Обучающий feedback — отдельная фича (out of scope).

**Amendment C**: Runtime rule `timeLimitSec` поле всегда задано в schema (`docs/architecture/0003-question-schema.md:30, 102`). lesson-runner вычисляет таймер по формуле `(charsCount × k)`, а не из `timeLimitSec`. Amendment:

> **Amendment 2026-04-26 (lesson-runner spec)**: `timeLimitSec` в Question schema **может остаться в payload** для backward compatibility (legacy data), но **runtime игнорирует его** в пользу формулы `seconds = max(5, round(charsCount × k))` где `charsCount = chars(text) + sum(chars(option/item/candidate)) + (if hasImage then 100 else 0)`. EASY k≈0.18, HARD k≈0.12 (1.5× жёстче). Точные коэффициенты — runtime config в `lesson-runner/domain/.../config/TimerCoefficients.kt`.

**Amendment D**: ADR упоминает «Runtime-правила живут в `shared/feature/quiz/domain`» (`docs/architecture/0003-question-schema.md:118`). Этот feature module не существует в текущем коде. Runtime gameplay — `shared/feature/lesson-runner/domain` (создаётся этой фичей). Amendment:

> **Amendment 2026-04-26 (lesson-runner spec)**: Reference на `shared/feature/quiz/domain` в ADR-0003 line 118 заменяется на `shared/feature/lesson-runner/domain` — фактический module для runtime gameplay rules.

Эти amendments **обязательны** для применения в design phase (формальный edit `docs/architecture/0003-question-schema.md`) перед началом implementation, иначе явное противоречие с architecturally locked schema.

## Resolved Decisions (закрыты в spec dialogue)

| # | Question | Resolution |
|---|----------|------------|
| 1 | Где живёт `Question.difficulty`/`image` | **Resolved**: parsed schema (sealed Question with difficulty + image) живёт в `shared/core/question-schema/` (существующий пустой module). Парсер реализуется в data source set этого модуля (kotlinx.serialization). lesson-runner/domain импортирует sealed types as-is. |
| 2 | Stars precision (Float vs Int) | **Resolved**: `Stars(rawTenths: Int 0..30)` value class. Integer math, нет Float precision issues. UI делит на 10 для отображения. |
| 3 | Perfect detection (Float==100 vs string) | **Resolved**: `allShownAnswersAre9 = codeAnswer.all { it == '0' || it == '9' } && codeAnswer.any { it == '9' }` — string-based. Не percent-based. |
| 4 | Checkbox visibility logic | **Resolved**: `hardUnlocked` (string-based: ∃ EASY-attempt с `allShownAnswersAre9`), НЕ `bestStars.rawTenths >= 20`. HARD attempt floor `Stars(rawTenths=20)` не unlocks HARD mode. |
| 5 | Invalid payload encoding в codeAnswer | **Resolved**: invalid payloads исключаются на этапе init (canonical pipeline шаг 3 в Business Rule 2 → mapNotNull). До прохождения не доходят. Если все invalid → `InitFailed(NoValidQuestions)`. Если хоть один valid — invalid просто отбрасываются. Никакой digit для invalid не пишется. (Старая семантика «invalid → '1'» отменена.) |
| 6 | Subset порядок | **Resolved**: `playOrder = subset.sortedBy(order, id)`. Стабильный, не shuffled. |
| 7 | Random subset stability на rotation | **Resolved**: `seed: Long` фиксируется в `RunnerState.Ready` на старте, restore через `instanceKeeper`. |
| 8 | Min timer floor | **Resolved**: `max(5, round(...))`. Min 5 секунд. |
| 9 | Image bonus в timer formula | **Resolved**: +100 знаков-эквивалентов если `hasImage`. |
| 10 | HARD unlock на short pool | **Resolved**: any size OK. 3/3 EASY правильно → unlocks. |
| 11 | Save failure handling | **Resolved**: `RunnerState.SaveFailed(attempt, error)` + UI warning. No retry в MVP. |
| 12 | Top3 cached note | **Resolved**: AC #45 явно говорит что top3 — server snapshot, текущая попытка не там. |
| 13 | Rating uniqueness | **Resolved**: lifetime per `(userId, lessonId)`. Local Room PK compound `(userId, lessonId)`; remote ID deterministic `sha256("$userId:$lessonId")` (БЕЗ lessonVersion в ключе). lessonVersion сохраняется в payload как analytics, не uniqueness key. |
| 14 | ADR-0003 amendments | **Resolved**: 4 amendments к ADR (A: EASY error continues, B: no feedback, C: timer formula override, D: module path lesson-runner). |
| 15 | EASY error continuation | **Resolved**: продолжается до конца. ADR Amendment A. |

## Open Items for Phase-01 (Implementation)

Эти items обнаружены в Codex review Walking Skeleton кода, **не блокеры** для spec но требуются для production-готовой реализации:

1. **`KotlinxSerializationQuestionContentParser` impl** — сейчас в `shared/core/question-schema/` есть только interface. Phase-01 backend-dev создаёт production реализацию через `kotlinx.serialization.json.Json` (схема согласно ADR-0003). Living в `commonMain` если purity rules позволяют (`shared/core/question-schema` это core module — kotlinx.serialization уже разрешена в его build.gradle.kts).

2. **DI lambda bindings** в `LessonRunnerDomainModule.kt` — `attemptIdProvider: () -> AttemptId`, `ratingIdProvider: (userId, lessonId) -> RatingId`, `currentTimeMillisProvider: () -> Long`. Koin не поддерживает function types через обычный `get()` без named qualifier. Phase-01 backend-dev решает strategy: либо `single<AttemptIdProvider> { ... }` wrapper interface, либо `parametersOf` injection. Сейчас в Stage A skeleton оставлено как TODO.

3. **AutoAnswerTest scope refinement** — текущие тесты #44-47 проверяют advance/index/digit, но не сам сгенерированный `UserAnswer` (форма ответа). Phase-01 test-dev может вынести `generateAutoAnswer(content, seed): UserAnswer` в `internal pure function` и протестировать форму напрямую. Текущая реализация работает корректно, тесты просто проверяют через эффекты.

4. **`SaveAttemptTest.kt:95` cosmetic warning** — "No cast needed" compiler warning. Phase-01 test-dev убирает.

## Open Questions for Research (open для research phase)

- [ ] Где конкретно реализованы текущие `Question.payload` JSON структуры — какой именно формат сейчас пишут quiz-creation feature и legacy. Research должен проверить совпадает ли с ADR-0003 schema или нужна migration логика (`payload v1` → `payload v2`).
- [ ] `StarRating.kt` API — поддерживает ли fractional value (0.0..3.0 шаг 0.1)? Если нет — расширить (design phase task).
- [ ] Compose FLAG_SECURE pattern — найти existing usage или зафиксировать рекомендацию (`LocalView.current.window.addFlags(FLAG_SECURE)` через `DisposableEffect`).
- [ ] Coil ImageLoader конфиг — disk cache settings (для offline картинок и аватарок).
- [ ] Cascade sync orchestrator API — как добавить новые коллекции `lesson_attempts` и `lesson_ratings` (для extension в phase реализации; не блокер для этой spec).
- [ ] Lesson Room migration plan — добавление полей `averageRating: Float?`, `ratingCount: Int?`, `top3: List<TopParticipant>` без breaking existing observers. Default values + Firestore backfill needed?
- [ ] Существующая backfill процедура в проекте (`scripts/backfill-catalogs.js` упомянут в PROJECT-CONTEXT) — паттерн для нового Lesson backfill?
