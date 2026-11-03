---
date: 2026-04-19
feature: menu-refactor / qualification-levels
type: new-feature
commit: 7c52c200
parent: 0-spec.md
---

# Sub-spec: Qualification Levels

Вынести пороги квалификаций (магические числа `100`, `200`, `300`, растасканные по коду) как отдельную доменную сущность `QualificationLevel` в `shared/core/foundation/` (**[UPDATED IN RESEARCH 2026-04-20]**: перенесено из `shared/feature/qualification/domain/` чтобы избежать cross-feature import `app-shell:domain → qualification:domain`, запрещённый `clean-architecture.md`). Это prerequisite для Dev Mode и для будущих фич, зависящих от уровней квалификации.

## Source

- Пользовательский запрос:
  > "очки квалификации вынеси отдельно, например разработчик 1 уровня = 100, и так для каждой квалификации, типа сделай 3 уровня"
- Ответ на follow-up: "Одинаковые: 100 / 200 / 300 для всех 6 квалификаций"
- "Не решать сейчас, определить только пороги (100/200/300) без семантики"

## Requirements

### Functional Requirements

1. `QualificationLevel` — **enum class** с тремя значениями и числовым порогом — [USER DECIDED] основание: "типа сделай 3 уровня"
   - `LEVEL_1` = 100 points
   - `LEVEL_2` = 200 points
   - `LEVEL_3` = 300 points

   **ADR-0006 wording adjustment**: ADR-0006 ранее упоминал `value class QualificationLevel` как возможность (для валидации одного числа). Эта spec изменяет подход: `QualificationLevel` теперь — **enum с 3 closed values**. Причина: Codex cross-review finding #10 (2026-04-19) — `enum class` лучше подходит для closed set of tier values. Для валидации конкретных чисел можно добавить отдельный `value class QualificationPoints(val value: Int)` позже, если потребуется. Обновление ADR-0006 — часть scope этой spec (см. "ADR Updates" в master `0-spec.md`).
2. Enum живёт в `shared/core/foundation/src/commonMain/kotlin/<base_package>/shared/core/foundation/QualificationLevel.kt` — **[UPDATED IN RESEARCH 2026-04-20]**: перенесено из `shared/feature/qualification/domain/` в `shared/core/foundation/` — чтобы `app-shell:domain` мог импортировать enum без нарушения правила "запрещён direct import между feature-модулями" (clean-architecture.md). Walking Skeleton файлы `QualificationLevel.kt` + `QualificationLevelTest.kt` перемещаются соответственно. `shared/feature/qualification/domain/` сохраняет dev_mode package, но enum переезжает в core.
3. Enum **одинаковый** для всех 6 квалификаций (TESTER, MODERATOR, TRANSLATOR, SPONSOR, ADMIN, DEVELOPER) — [USER DECIDED] "одинаковые для всех 6"
4. Утилита проверки `fun QualificationLevel.isReachedBy(points: Int): Boolean = points >= this.points` — [DELEGATED: pure extension function для удобства сравнения. Risk: low.]
5. Все места в коде, где используется magic number `100` как threshold квалификации, **должны** использовать `QualificationLevel.LEVEL_1.points` — [DELEGATED: eliminate magic numbers. Risk: low.]

Обязательные call sites для замены:
- `shared/feature/app-shell/domain/model/DrawerSection.kt` — `EventsSection.ActiveEvents` (строки 97-104)
- Любое новое место (например, Dev Mode в sub-spec-dev-mode)

### Non-Functional Requirements

1. **Pure Kotlin** без Android/SDK/DI аннотаций — [DELEGATED: domain layer purity invariant]
2. **KMP-совместимость**: `commonMain` source set — [DELEGATED: PROJECT_STRUCTURE §4 "shared/* — KMP-модули"]
3. **Без external deps** за пределами Kotlin stdlib — [DELEGATED: core domain self-contained]

## Scope

### In Scope
- **[UPDATED 2026-04-20]** `QualificationLevel.kt` enum в `shared/core/foundation/` (move из `shared/feature/qualification/domain/`)
- **[UPDATED 2026-04-20]** Переместить `QualificationLevelTest.kt` в `shared/core/foundation/src/commonTest/`
- `isReachedBy(points: Int): Boolean` extension — в том же файле/модуле
- Заменить `100` в `DrawerSection.EventsSection.ActiveEvents.requiredRoles` на `QualificationLevel.LEVEL_1.points`
- Добавить зависимость `implementation(project(":shared:core:foundation"))` в `shared/feature/app-shell/domain/build.gradle.kts` (scaffold ownership — backend-dev)

### Explicitly Out of Scope
- Семантика уровней (что именно разблокирует LEVEL_1/2/3 для каждой квалификации) — "Не решать сейчас" [USER DECIDED]
- Серверный конфиг порогов (runtime override) — отдельная фича, упомянуто в ADR-0006 как future work
- Изменения в `UserQualifications` data class (оно уже есть) — только добавляем утилитарный enum вокруг

## Search Criteria for Research

Research по этой sub-spec должен ответить:

1. **Есть ли уже `QualificationLevel` или эквивалент?** — найти в `shared/feature/qualification/domain/` все существующие типы
2. **Где уже определены magic numbers `100` / `200` / `300` как пороги?** — поиск по всему codebase:
   ```
   grep -rn "100" shared/feature/app-shell/domain/src/commonMain/kotlin/
   grep -rn "LEVEL_" shared/feature/qualification/
   grep -rn "Role\..*to 100" shared/ android/
   ```
3. **Существует ли `UserQualifications` в shared/feature/qualification/domain/?** — если нет, нужно создать
4. **Какие тестовые паттерны используются в shared-модулях?** — `commonTest` vs `jvmTest`, JUnit 4 vs 5, существующие testing utilities
5. **Что сейчас проверяет `DrawerSection.EventsSection.ActiveEvents`?** — `shared/feature/app-shell/domain/model/DrawerSection.kt:97-104`

### Обязательные search directions
- Найти ВСЕ места в коде, где используется `100` как threshold (не только в DrawerSection)
- Найти существующий `UserQualifications` data class (упомянут в ADR-0006)
- Найти DI module для `qualification:domain` (если есть)

### Completeness check
- Для поиска `100`: grep + manual verification по каждому файлу
- Для поиска `QualificationType`: в ADR-0006 упоминается, проверить наличие в коде

## Primary User Journeys

Эта фича **не имеет пользовательских journey** (это pure refactoring). Journey — это скорее "developer journey":

1. **Developer journey: добавление нового threshold consumer**
   - Start: developer пишет новый check `if (points >= 100)`
   - Trigger: code review
   - State changes: developer заменяет на `QualificationLevel.LEVEL_1.isReachedBy(points)`
   - Expected result: чистый код без magic numbers
   - Decision: [DELEGATED — dev ergonomics, low risk]

## Feature Domain Contract

### Terms / Entities / Value Constraints

- `QualificationLevel` — enum, closed set of 3 values
- `points` — non-negative `Int` (≥ 0). Value `100`, `200`, `300` зарезервированы для LEVEL_1/2/3 соответственно
- `isReachedBy(points: Int): Boolean` — pure extension, no side effects

### Business Rules / Invariants / Guards

1. Enum `QualificationLevel` — final, нет расширений через inheritance
2. Значения `points` — immutable (в enum определены как `val`)
3. `isReachedBy(x)` истинно iff `x >= this.points`
4. `isReachedBy(-1)` → всегда `false` (LEVEL_1=100, LEVEL_2=200, LEVEL_3=300 > -1 is false)
5. `isReachedBy(Int.MAX_VALUE)` → всегда `true`

### State / Decision Rules

- Enum — closed set, не имеет "перехода" между состояниями. State — статичный.

### Error / Recovery Rules

- Нет ошибок. Все операции — pure functions, total.

### Domain Test Scenarios (phase-01 source of truth)

1. GIVEN `QualificationLevel.LEVEL_1` WHEN read `.points` THEN = 100
2. GIVEN `QualificationLevel.LEVEL_2` WHEN read `.points` THEN = 200
3. GIVEN `QualificationLevel.LEVEL_3` WHEN read `.points` THEN = 300
4. GIVEN `QualificationLevel.LEVEL_1`, value = 99 WHEN `isReachedBy(99)` THEN `false`
5. GIVEN `QualificationLevel.LEVEL_1`, value = 100 WHEN `isReachedBy(100)` THEN `true`
6. GIVEN `QualificationLevel.LEVEL_1`, value = 500 WHEN `isReachedBy(500)` THEN `true`
7. GIVEN `QualificationLevel.LEVEL_2`, value = 100 WHEN `isReachedBy(100)` THEN `false`
8. GIVEN `QualificationLevel.LEVEL_2`, value = 200 WHEN `isReachedBy(200)` THEN `true`
9. GIVEN `QualificationLevel.LEVEL_3`, value = 200 WHEN `isReachedBy(200)` THEN `false`
10. GIVEN `QualificationLevel.LEVEL_3`, value = 300 WHEN `isReachedBy(300)` THEN `true`
11. GIVEN `QualificationLevel.LEVEL_1`, value = -1 WHEN `isReachedBy(-1)` THEN `false`
12. GIVEN `QualificationLevel.LEVEL_1`, value = 0 WHEN `isReachedBy(0)` THEN `false`
13. GIVEN `QualificationLevel.values()` WHEN inspect length THEN = 3 (именно LEVEL_1, LEVEL_2, LEVEL_3; не больше и не меньше)
14. GIVEN `QualificationLevel.values()` WHEN map `.points` THEN `[100, 200, 300]` (правильный порядок declaration)

## Delegated Decisions Summary

| # | Область | Решение агента | Обоснование | Risk |
|---|---|---|---|---|
| 1 | Path `shared/feature/qualification/domain/src/commonMain/kotlin/.../model/QualificationLevel.kt` | Canonical KMP location | PROJECT_STRUCTURE.md §4 | low |
| 2 | Pure extension `isReachedBy` | API ergonomics | Standard Kotlin idiom | low |
| 3 | Eliminate magic numbers | Code hygiene | Invariant consistency | low |
| 4 | Pure Kotlin, no Android | Domain purity invariant | `.claude/rules/domain-models.md` | low |
| 5 | commonMain KMP source set | PROJECT_STRUCTURE §4 | ADR-0002 KMP strategy | low |

## Acceptance Criteria

1. [ ] GIVEN file `shared/feature/qualification/domain/.../model/QualificationLevel.kt` WHEN inspect THEN contains `enum class QualificationLevel(val points: Int) { LEVEL_1(100), LEVEL_2(200), LEVEL_3(300) }`
2. [ ] GIVEN extension `isReachedBy(points: Int)` WHEN defined THEN pure function, no side effects, returns `Boolean`
3. [ ] GIVEN all Domain Test Scenarios (14) WHEN run as `@Test` THEN все зелёные
4. [ ] GIVEN `shared/feature/app-shell/domain/model/DrawerSection.kt` WHEN inspect `EventsSection.ActiveEvents.requiredRoles` THEN использует `QualificationLevel.LEVEL_1.points` (НЕ magic number `100`)
5. [ ] GIVEN grep по всему проекту `"to 100"` или `">= 100"` в контексте role checks WHEN inspect THEN каждое найденное место либо (а) заменено на `QualificationLevel.LEVEL_1.points`, либо (б) явно задокументировано почему остаётся magic number
6. [ ] GIVEN `./gradlew :shared:feature:qualification:domain:jvmTest` (или аналог) WHEN run THEN все тесты зелёные
7. [ ] GIVEN `./gradlew :shared:feature:app-shell:domain:jvmTest` WHEN run после замены THEN все тесты зелёные (app-shell depend от qualification)

## Invariant Check (from docs/invariants.md)

| Invariant | Impact | Decision |
|-----------|--------|----------|
| 1. Domain layer purity | `QualificationLevel` — чистый Kotlin enum | preserve |
| 6. Walking Skeleton ownership | Создаётся domain в qualification:domain (если не существовал) или расширяется. Если существующий код пересекается — не перезаписывать | preserve + lock |

## Constraints (from PROJECT_STRUCTURE.md)

- KMP-модуль `shared:feature:qualification:domain` с commonMain + jvmTest
- Koin DI (ADR-0009) — если нужен provider, через `module { factory { } }`; но для pure enum DI не требуется
- Kotlin stdlib only
