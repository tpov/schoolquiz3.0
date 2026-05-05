# ADR-0005: Жизненный цикл квеста

## Status
Accepted — 2026-04-16

## Context

В legacy жизненный цикл описывался единым enum `EventQuiz` с 8 значениями (`QUIZ_BY_USER`, `QUIZ_FOR_TESTER`, `QUIZ_FOR_TRANSLATOR`, `QUIZ_FOR_ADMIN`, `QUIZ_ARENA`, `QUIZ_TOURNAMENT`, `QUIZ_TOURNAMENT_LEADER`, `QUIZ_HOME`). Этот enum смешивал две принципиально разные оси:

1. **Ревью-workflow** (на ком висит проверка): `BY_USER / FOR_TESTER / FOR_TRANSLATOR / FOR_ADMIN`.
2. **Публикационную полку** (где квест показывается): `ARENA / TOURNAMENT / TOURNAMENT_LEADER / HOME`.

Ещё одно смешение: **regular-квесты** и **курсы** шли через тот же enum, хотя у курсов нет ни арены, ни турнира — только свой архив-каталог.

Ревью было строго последовательным (`TESTER → TRANSLATOR → ADMIN`), что блокировало параллельные проверки и затягивало выход в ARENA.

## Decision

### Три разделённые оси состояния

1. **`QuestType`** — задаётся при создании, не меняется:
   - `REGULAR` — обычный квест, идёт через арену/турниры/дом
   - `COURSE` — курс, идёт в свой архив-каталог

2. **`QuestPhase`** — крупная фаза жизни:
   - `DRAFT` — автор редактирует
   - `IN_REVIEW` — проходит проверки (см. `QuestChecks`)
   - `PUBLISHED` — опубликован (на полке см. `PublicationShelf`)
   - `RETIRED` — снят с публикации

3. **`PublicationShelf`** — конкретная полка, только если `QuestPhase = PUBLISHED`:
   - `ARENA` — сезонная лента, юзеры проходят и оценивают (только REGULAR)
   - `TOURNAMENT` — отборочный раунд (только REGULAR)
   - `TOURNAMENT_LEADER` — финал для лидеров (только REGULAR)
   - `HOME` — витрина «лучшее из лучшего», видна всем при входе (только REGULAR)
   - `ARCHIVE_COURSES` — каталог курсов, свободный доступ (только COURSE)

### Ревью — 3 параллельных флажка

В фазе `IN_REVIEW` состоянием является не единое значение, а три **независимых** проверки:

```kotlin
data class QuestChecks(
    val content: CheckStatus,         // автомодерация на запрещённый контент
    val translation: TranslationStatus, // см. sealed ниже
    val logic: CheckStatus,            // ручная проверка логики (тестер/админ)
)

enum class CheckStatus { NOT_REQUIRED, PENDING, IN_PROGRESS, PASSED, FAILED }

sealed interface TranslationStatus {
    data object NotRequired : TranslationStatus               // квест на одном языке
    data object Pending : TranslationStatus                   // ждёт переводчика
    data class Translating(val translatorId: UserId) : TranslationStatus
    data class Proofreading(val reviewerId: UserId, val translatorId: UserId) : TranslationStatus
    data class Rating(val raterId: UserId, val translatorId: UserId) : TranslationStatus
    data object Passed : TranslationStatus
    data object Failed : TranslationStatus
}
```

Проверки идут **параллельно**. Переводчик/оценщик перевода, модератор контента и тестер логики работают одновременно, не блокируя друг друга. Каждая проверка внутри себя может иметь собственный state machine (у translation — явный, у content/logic — плоский).

**Оценщик может не только отклонить, но и сам поправить** (перевод или логику). Это фиксируется через `QuestRevision` — см. ниже.

### Переходы фазы

Переходы между `QuestPhase` — детерминированные и явные:

```
DRAFT                 → IN_REVIEW        : автор отправляет на ревью
IN_REVIEW             → PUBLISHED(shelf) : когда все 3 check ∈ {PASSED, NOT_REQUIRED}
                                            shelf = ARENA (REGULAR) | ARCHIVE_COURSES (COURSE)
IN_REVIEW             → DRAFT            : если хотя бы один check = FAILED; автору возвращается список что сломано
PUBLISHED(any)        → RETIRED          : админ снимает / устарело / заменено сезоном
```

### Переходы между полками — автоматические по серверным константам

Клиент **не знает** правил промо. Правила живут на сервере и применяются фоновыми задачами (`server/workers/rewards` и `server/workers/review-collisions`). Клиент только читает актуальный `PublicationShelf` квеста.

Для старта (будут конфигурироваться серверными константами):

```
ARENA → TOURNAMENT            : топ-3 по средней оценке сезона + средняя оценка ≥ порог
TOURNAMENT → TOURNAMENT_LEADER: победители отборочного турнира
TOURNAMENT_LEADER → HOME      : квесты, дошедшие до финала, попадают на витрину
любой shelf → RETIRED         : по ручному решению или условиям сезона
```

Значения `топ-3`, `порог`, критерии победы в турнире — серверный конфиг, не хардкод.

### Ревизии (editor tracking)

Храним **только последнюю** ревизию (компромисс — см. ADR-0003 логика):

```kotlin
data class QuestRevision(
    val editorId: UserId,
    val editedAt: Instant,
    val editReason: EditReason,
)

enum class EditReason { TRANSLATION, CONTENT_FIX, LOGIC_FIX, OTHER }
```

Квест содержит `lastRevision: QuestRevision?`. Этого достаточно для кейса «дать оценку последнему редактору». Полный `QuestEditHistory` — отдельная таблица, добавляется позже без ломки модели, если понадобится undo/просмотр истории.

### Режим прохождения — QuizSessionMode

Один квест может проходиться в двух **режимах сессии**, никак не связанных с QuestPhase/PublicationShelf. Режим — это параметр конкретного прохождения:

- `LEARNING` — обучающий: EASY-first, на EASY можно раскрывать правильный ответ после ответа, мягкий коэффициент таймера. Свободное перепрохождение.
- `EXAM` — экзаменационный: рандомная выборка вопросов, правильный ответ **не раскрывается даже на EASY**, более жёсткий коэффициент таймера. После EXAM квест **может сгенерировать артефакт** (см. CompletionEffect).

Матрица «что показывать» — пересечение Difficulty × Mode:

| | LEARNING | EXAM |
|---|---|---|
| EASY | правильный ответ показывается | не показывается |
| HARD | не показывается | не показывается |

Режим EXAM применяется в первую очередь для курсов (кнопка «сдать экзамен» в UI курса) и для квестов-собеседований на игровые квалификации. Обычные REGULAR-квесты на ARENA проходятся в LEARNING.

### CompletionEffect — пост-обработка прохождения

У квеста есть опциональное поле `completionEffect`, описывающее, что происходит после успешного прохождения (включая EXAM-режим, если применимо).

```kotlin
data class Quest(
    // ...
    val completionEffect: CompletionEffect = CompletionEffect.None,
)

sealed interface CompletionEffect {
    data object None : CompletionEffect
    data class IssueCourseCertificate(val courseId: QuestId) : CompletionEffect
    data class OfferQualification(
        val qualificationType: QualificationType,   // TESTER / TRANSLATOR / ... см. ADR-0006
        val notifyDevs: Boolean = false,            // отправка данных юзерам с квалификацией DEVELOPER
    ) : CompletionEffect
}
```

Обработка эффекта — **event-driven**:
1. Юзер успешно прошёл квест → `quiz:domain` выбрасывает событие `QuestCompleted(questId, userId, score, effect)`.
2. Подписчики из других модулей реагируют:
   - `IssueCourseCertificate` → `profile:domain` создаёт `Certificate` (см. ADR-0007).
   - `OfferQualification` → `qualification:domain` регистрирует оффер и показывает диалог смены квалификации (см. ADR-0006).
3. `quiz:domain` **не знает** о сертификатах и квалификациях — только о том, что у квеста есть "эффект завершения".
4. **Серверная защита:** выдача сертификата и оффер квалификации всегда проходят через сервер (клиент не может самостоятельно присвоить себе сертификат/квалификацию). Клиентское событие — это только запрос, серверные workers проверяют право и фактически создают артефакт.

### Инварианты (проверяются в домене)

1. `QuestType = COURSE` ⇒ допустимые shelves = `{ARCHIVE_COURSES}`.
2. `QuestType = REGULAR` ⇒ допустимые shelves = `{ARENA, TOURNAMENT, TOURNAMENT_LEADER, HOME}`.
3. `QuestPhase ≠ PUBLISHED` ⇒ `shelf = null`.
4. `QuestPhase = PUBLISHED` ⇒ `shelf ≠ null`.
5. Переход `DRAFT ← IN_REVIEW` возможен только через `any check = FAILED`, с прикреплённой причиной.
6. `completionEffect = IssueCourseCertificate(courseId)` ⇒ `QuestType = COURSE` и `courseId = этот квест`.
7. `completionEffect = OfferQualification` допустим только для `QuestType = REGULAR` (квест-собес).

## Consequences

### Плюсы
- Параллельность ревью — квест выходит в ARENA в момент последнего `PASSED`, а не в конце цепочки.
- Два трека (REGULAR / COURSE) изолированы, курсы не путаются с ареной/турниром.
- Промо между полками — на сервере. Клиент всегда тонкий.
- `EventQuiz` из legacy растворяется в трёх чётких осях (`QuestType + QuestPhase + PublicationShelf`). Явные инварианты вместо смешанного enum.
- `NOT_REQUIRED` у translation избавляет одноязычные квесты от фиктивных прогонов через переводчиков.

### Минусы
- Модель требует 3 поля вместо одного — чуть сложнее для сериализации и БД.
- Админ при ручном RETIRED должен указать причину (добавится отдельным полем, не описано в этом ADR, т.к. не критично).
- Если на сервере правило промо изменится — клиент это просто увидит как изменение `shelf`; но нужно покрыть тем, что несколько клиентов могут временно видеть разное состояние (типичная eventually consistent ситуация — см. будущий ADR-0004 о sync).

### Правила
1. Клиент не вычисляет правила промо между полками. Только сервер.
2. Любое изменение lifecycle проходит через domain use cases (`SubmitForReview`, `ApplyCheck`, `Retire`, `ApplyRevision`), а не через прямую правку полей.
3. Добавление новой полки / нового типа квеста — через правку этого ADR + enum + use case.
4. `CompletionEffect` никогда не обрабатывается клиентом напрямую — только как запрос серверу. Клиент не создаёт сертификат и не присваивает квалификацию сам.
5. Режим сессии (`QuizSessionMode`) — параметр запуска прохождения, не свойство самого квеста. Один квест может проходиться в обоих режимах.

## Mapping из legacy

| Legacy `EventQuiz` | Новый эквивалент |
|---|---|
| `QUIZ_BY_USER` | `QuestPhase = DRAFT` |
| `QUIZ_FOR_TESTER` | `QuestPhase = IN_REVIEW`, `logic.IN_PROGRESS` |
| `QUIZ_FOR_TRANSLATOR` | `QuestPhase = IN_REVIEW`, `translation.Translating/Proofreading/Rating` |
| `QUIZ_FOR_ADMIN` | `QuestPhase = IN_REVIEW`, `logic.IN_PROGRESS` (оценщик=админ) |
| `QUIZ_ARENA` | `PublicationShelf = ARENA` |
| `QUIZ_TOURNAMENT` | `PublicationShelf = TOURNAMENT` |
| `QUIZ_TOURNAMENT_LEADER` | `PublicationShelf = TOURNAMENT_LEADER` |
| `QUIZ_HOME` | `PublicationShelf = HOME` |
| (не было явно) | `PublicationShelf = ARCHIVE_COURSES` для курсов |

## Amendment 2026-04-21 — PublicationShelf как Set (visibleOn)

Добавлено фичей `home-and-my-quests` (см. `docs/features/home-and-my-quests/0-spec.md`).

### Изменение

Первая версия этого ADR (выше) описывала `Quest.shelf: PublicationShelf?` — квест может находиться **только на одной полке** одновременно. Переходы между полками последовательные (ARENA → TOURNAMENT → HOME).

Практика показала, что:
1. Квесты-курсы (COURSE) должны быть видны одновременно в нескольких каталогах UX (например "Мои курсы" + "Домашние курсы" + "Архив", когда юзер и автор и зритель).
2. Админ хочет показывать квест и на `HOME` и в `ARENA` одновременно для promotion campaigns.
3. Тестерский режим — один квест виден и в `TOURNAMENT` (для отборочного) и в `ARENA` (для обратной совместимости с ранее опубликованным).

Жёсткий single-shelf enum не поддерживает эти сценарии.

### Новая модель

```kotlin
// Вместо shelf: PublicationShelf?
data class Quest(
    // ...
    val visibleOn: Set<String>,  // подмножество из {"home", "arena", "tournament", "tournamentFinal", "archive"}
    // Пустой Set (emptySet()) → квест нигде не виден → локальный delete.
)
```

В Firestore хранится как `Array<String>` для `array-contains-any` filter:
```json
quests/q1 {
  "visibleOn": ["home", "arena"]
}
```

### Перенос инвариантов

| Инвариант (old) | Новая формулировка |
|-----------------|---------------------|
| `QuestPhase ≠ PUBLISHED ⇒ shelf = null` | `QuestPhase ≠ PUBLISHED ⇒ visibleOn.isEmpty()` |
| `QuestPhase = PUBLISHED ⇒ shelf ≠ null` | `QuestPhase = PUBLISHED ⇒ visibleOn.isNotEmpty()` |
| `QuestType = COURSE ⇒ shelves ⊆ {ARCHIVE_COURSES}` | `QuestType = COURSE ⇒ visibleOn ⊆ {"home", "archive"}` (курсы видны в "Домашние" + "Архив", не в "Арене"/"Турнире") |
| `QuestType = REGULAR ⇒ shelves ⊆ {ARENA, TOURNAMENT, TOURNAMENT_LEADER, HOME}` | `QuestType = REGULAR ⇒ visibleOn ⊆ {"home", "arena", "tournament", "tournamentFinal", "archive"}` |

### Миграция с enum

Server-side: admin tooling преобразует existing `shelf: String` в `visibleOn: Array<String>` (один элемент). Клиент читает новое поле. Deprecated `shelf` можно удалить после migration.

Pre-production на момент изменения — нет существующих документов, migration = zero-cost.

### Server rules автопромо

Автоматические переходы (ARENA → TOURNAMENT → HOME) теперь — **добавления** в `visibleOn`, не перемещения:

- "Топ-3 из ARENA" → `visibleOn.add("tournament")` (остаётся и в `arena`)
- "Победитель tournament" → `visibleOn.add("tournamentFinal")` (остаётся в `arena` и `tournament`)
- "Финал HOME" → `visibleOn.add("home")` (остаётся во всех предыдущих)
- Снять с публикации → `visibleOn.remove("X")`. Если empty — квест локально удаляется у клиентов (tombstone semantics per ADR-0004).

Это реалистичнее: "победитель" остаётся в арене для reference/archive.

### Impact на клиент

- Запрос для "Домашние квесты": `quests.where('visibleOn', 'array-contains', 'home')`.
- Запрос для "Арена": `quests.where('visibleOn', 'array-contains', 'arena')`.

## Amendment 2026-05-03 — Arena review as server-owned checks and scores

User-authored arena submissions are not represented as a quest enum status on the server.
The client creates a `quest_review_requests/{submissionId}` event, and the server copies the
snapshot into two server-owned trees:

- `private/{ownerUid}/catalogs/{catalogId}/quests/{questId}/...` — author's private hierarchy.
- `admin/review/lessons/{lessonId}/quests/{questId}/questions/{questionId}` — lesson-centric review queue.

The review queue uses independent fields:

```kotlin
isTested: Boolean
testingScore: Double?      // 0.0..3.0
isLogicReviewed: Boolean
logicScore: Double?        // 0.0..3.0
isTranslationReviewed: Boolean
translationScore: Int?     // 0..100
translatedLanguages: Map<String, Int> // language -> question languageLevel (1..25)
```

For arena submissions the reviewer workflow is sequential:

1. Testing is available first.
2. Logic review is available after testing.
3. Translation is available only after testing and logic review.

Routing uses the trusted server-side `profiles/{uid}` document. Clients may read their own
profile but cannot write qualification fields. A developer with `developerLevel > 100` can see
all open review stages. Testers see only testing. Admins see testing and logic review. Translators
see translation tasks only when they know one existing language and one target language, and their
`translatorLevel` is at least 100. A translated language is treated as still needing review for a
translator if `translatorLevel >= question.languageLevel + 100`.
- Sync клиента: `quests.where('visibleOn', 'array-contains-any', availableShelves)` — один запрос для всех доступных юзеру полок (до 10 значений).
- Локальный UI filter: `quest.visibleOn.contains("home")` — плоский set check.

## Notes

Модели живут в `shared/feature/quest/domain/src/commonMain/kotlin/` (новый модуль после `home-and-my-quests` фичи; старый `shared/feature/quiz/domain/` был каркасом без реальной реализации). Серверная логика промо — в `server/workers/review-collisions` и отдельном worker'е автопромо (будет добавлен). Sync с клиентом — см. ADR-0004 + amendment там же.
