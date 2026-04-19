# ADR-0003: Question schema

## Status
Accepted — 2026-04-16

## Context

Вопрос — центральная сущность продукта. От его схемы зависит БД, сетевая сериализация, UI-рендер, редактор и миграции. В legacy схема не была зафиксирована формально, из-за чего разные слои подкручивали своё представление.

Требования к новой схеме:
1. Поддержать 4 типа вопросов: single choice, multiple choice, ordering, fill-in-the-blank.
2. У вопроса есть уровень сложности (для механики EASY → HARD).
3. У каждого вопроса обязательно есть таймер (коэффициент у HARD жёстче).
4. Картинка — опциональна.
5. Схема должна сериализоваться (сеть, БД, снапшоты) и жить в KMP `commonMain`.

## Decision

### Тип вопроса

Sealed interface `Question` с четырьмя вариантами:

```kotlin
@Serializable
sealed interface Question {
    val id: QuestionId
    val difficulty: Difficulty
    val text: String
    val image: ImageRef?
    val timeLimitSec: Int          // всегда задан; null-семантики нет

    @Serializable
    data class SingleChoice(
        override val id: QuestionId,
        override val difficulty: Difficulty,
        override val text: String,
        override val image: ImageRef? = null,
        override val timeLimitSec: Int,
        val options: List<AnswerOption>,      // размер 2..8
        val correctOptionId: OptionId,
    ) : Question

    @Serializable
    data class MultipleChoice(
        override val id: QuestionId,
        override val difficulty: Difficulty,
        override val text: String,
        override val image: ImageRef? = null,
        override val timeLimitSec: Int,
        val options: List<AnswerOption>,      // размер 2..8
        val correctOptionIds: Set<OptionId>,  // ≥ 2 элементов (иначе это SingleChoice)
    ) : Question

    @Serializable
    data class Ordering(
        override val id: QuestionId,
        override val difficulty: Difficulty,
        override val text: String,
        override val image: ImageRef? = null,
        override val timeLimitSec: Int,
        val items: List<OrderItem>,           // размер 2..8; порядок в списке = правильный
    ) : Question

    @Serializable
    data class FillBlank(
        override val id: QuestionId,
        override val difficulty: Difficulty,
        override val text: String,            // содержит маркеры пропусков: "___"
        override val image: ImageRef? = null,
        override val timeLimitSec: Int,
        val blanks: List<Blank>,               // 1..3 пропусков
        val candidates: List<Candidate>,       // 5 или 10 (см. CandidateCount)
        val candidateCount: CandidateCount,
    ) : Question
}

enum class Difficulty { EASY, HARD }
enum class CandidateCount(val value: Int) { FIVE(5), TEN(10) }
```

### Вспомогательные типы

```kotlin
@JvmInline @Serializable value class QuestionId(val raw: String)
@JvmInline @Serializable value class OptionId(val raw: String)
@JvmInline @Serializable value class BlankId(val raw: String)
@JvmInline @Serializable value class CandidateId(val raw: String)

@Serializable data class AnswerOption(val id: OptionId, val text: String, val image: ImageRef? = null)
@Serializable data class OrderItem(val id: OptionId, val text: String, val image: ImageRef? = null)
@Serializable data class Blank(val id: BlankId, val correctCandidateId: CandidateId)
@Serializable data class Candidate(val id: CandidateId, val text: String)
@Serializable data class ImageRef(val url: String, val altText: String? = null)
```

### Правила инвариантов (валидируются в конструкторах/билдерах)

- `SingleChoice.options.size in 2..8` и `correctOptionId ∈ options.map { it.id }`.
- `MultipleChoice.correctOptionIds.size ≥ 2` и все ID ∈ options.
- `Ordering.items.size in 2..8`.
- `FillBlank.blanks.size in 1..3`, `candidates.size == candidateCount.value`, все `correctCandidateId ∈ candidates`, distractor'ы = `candidates - blanks.correct`.
- `timeLimitSec > 0`.

### UI-контракт для FillBlank

- `candidateCount = FIVE` → одна колонка, 1×5.
- `candidateCount = TEN` → две колонки, 2×5.
- Длина кнопки = максимальная длина текста среди кандидатов.

Выбор 5 vs 10 делает автор при создании. Обосновано в разговоре: 5 — стандарт для конвертации внешних форматов, 10 — для коротких слов когда нужно больше distractor'ов.

### Философия двух уровней сложности

**EASY = режим обучения и практики. HARD = режим истинной оценки.**

Это продуктовое решение, а не технический компромисс. Разделение позволяет одному квесту одновременно служить учебным и аттестующим инструментом без необходимости заводить «учебный» и «экзаменационный» режимы на уровне приложения.

### Правила геймплея (живут в `shared/feature/quiz/domain`, НЕ в схеме)

Схема описывает только структуру вопроса. Runtime-правила зависят от `Difficulty`:

**Общие:**
- Прохождение: сначала все EASY. 100% правильных → открываются HARD.
- Ошибка или просрочка таймера на EASY → прохождение заканчивается на EASY, максимум **2★**.
- EASY безошибочно + HARD → до **3★** (точная формула — отдельно).
- UI **не показывает список всех вопросов заранее** — только текущий (на обоих уровнях).

**EASY (обучение):**
- Можно **раскрывать правильный ответ** после ответа на вопрос (обучающий feedback).
- Таймер мягче (коэффициент задан в настройках).
- Перепрохождение доступно свободно — цель юзера учиться.

**HARD (оценка):**
- Правильный ответ **не раскрывается** — защита от подглядывания при повторе, честность оценки.
- Таймер с более жёстким коэффициентом.
- Перепрохождение возможно, но не даёт юзеру доступа к истории правильных ответов.

Runtime-слой различает эти два режима по полю `Question.difficulty` и применяет соответствующие правила.

## Consequences

### Плюсы
- Типобезопасность: нельзя создать `SingleChoice` с `correctOptionIds: Set`.
- Компилятор ловит exhaustive-handling при добавлении нового типа.
- `kotlinx.serialization` — работает в `commonMain`, гоняется по сети и в снапшотах одинаково.
- Difficulty — поле самого вопроса, не квеста. Квест — просто `List<Question>`, где есть и EASY, и HARD.

### Минусы
- `FillBlank` негибко фиксирует 5/10 — если захочется 4 или 7, нужен новый CandidateCount. Но это осознанный выбор в пользу предсказуемого UI.
- Sealed hierarchy vs JSON-Schema — для внешних языков придётся дублировать. Митигация: JSON-схему выгружаем отдельно в `docs/architecture/schemas/` когда понадобится (генератором из sealed-иерархии).

### Правила, которые следуют из решения
1. Не добавлять типы вопроса в обход sealed hierarchy.
2. Инварианты — в `require()` внутри `init {}` блоков data class'ов.
3. `timeLimitSec` хранится как базовое время вопроса; коэффициент для HARD — отдельная runtime-функция в domain.
4. Картинки по URL (`ImageRef`) — ссылка на внешний ресурс. Загрузка/кэш — зона ответственности `platform/*` и `shared/core/network`.

## Notes

Модели живут в `shared/core/question-schema/src/commonMain/kotlin/`. Плагин сериализации подключён в `build.gradle.kts` этого модуля.
