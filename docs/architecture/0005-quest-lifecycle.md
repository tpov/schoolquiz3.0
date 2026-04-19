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

## Notes

Модели живут в `shared/feature/quiz/domain/src/commonMain/kotlin/`. Серверная логика промо — в `server/workers/review-collisions` и отдельном worker'е автопромо (будет добавлен). Sync с клиентом — см. ADR-0004 (ещё не написан).
