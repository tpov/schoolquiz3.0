---
date: 2026-04-19
feature: menu-refactor / home-quests
type: enhancement
commit: 7c52c200
parent: 0-spec.md
---

# Sub-spec: Home Quests Rename

Переименовать `DrawerSection.LocalSection.MyCourses → HomeQuests` (+ `LocalConfig.MyCoursesRoot → HomeQuestsRoot`) и поставить `HomeQuests` первым в `Tab.LOCAL`. Минимальный refactoring без изменения функциональности navigation targets (остаются placeholder).

## Source

- "начал с экрана мои квесты, посмотри на легаси там есть разные каталоги, курсы, опросы"
- "курсы не пункт меню, убрать"
- "каталог, каталоги будут встречаться на многих экранах, на данном этапе в пункте домашние квесты и мои квесты, оба пункта в закладке локально"
- "B. Переименовать MyCourses → HomeQuests"
- "HomeQuests первый, потом MyQuests, Settings"
- "Да, переименовываем MyCoursesRoot → HomeQuestsRoot"

## Requirements

### Functional Requirements

1. **Rename sealed interface member**: `DrawerSection.LocalSection.MyCourses` → `DrawerSection.LocalSection.HomeQuests` — [USER DECIDED]
2. **Rename config**: `LocalConfig.MyCoursesRoot` → `LocalConfig.HomeQuestsRoot` — [USER DECIDED]
3. **Update `visibleSections(Tab.LOCAL, ...)` order**: `HomeQuests, MyQuests, Settings` (HomeQuests first) — [USER DECIDED]
4. **Update `rootOf(section)`**: map `DrawerSection.LocalSection.HomeQuests → LocalConfig.HomeQuestsRoot`; `MyQuests → MyQuestsRoot` (existing); `Settings → SettingsRoot` (existing) — [DELEGATED: consistent naming]
5. **Update display labels (Labels.kt)**: 
   - `DrawerSection.LocalSection.HomeQuests.displayName` = "Домашние квесты" — [USER DECIDED] "домашние квесты"
   - `LocalConfig.HomeQuestsRoot.displayName` = "Домашние квесты"
6. **Update icon**: `DrawerSection.LocalSection.HomeQuests.icon` = `Icons.Default.Home` (вместо старого `Icons.Default.Book`) — [DELEGATED: Home icon semantically соответствует "домашние квесты" + уже используется для Tab.LOCAL]
7. **Navigation root behavior**: `HomeQuestsRoot` остаётся placeholder Screen, как сейчас у `MyCoursesRoot` — [USER DECIDED] "это не относится к этой задаче"
8. **requiredRoles**: `HomeQuests.requiredRoles = emptyMap()` (всегда видим, как был MyCourses) — [DELEGATED: сохранить существующее поведение]

### Non-Functional Requirements

1. **Обратная совместимость**: не нужна. Это pre-production rename, нет persistence'а значений `MyCourses`/`MyCoursesRoot` вне кода — [DELEGATED: project pre-production; persistence пока нет serialized]
2. **No UX regression**: порядок пунктов в drawer должен быть визуально предсказуем — [DELEGATED: standard UX]
3. **Test coverage**: обновить существующие тесты (`DrawerFooterMapperTest`, `AppShellScreenTest`) если они ссылаются на `MyCourses` — [DELEGATED: test hygiene]

## Scope

### In Scope
- Rename в production коде (5 файлов):
  - `shared/feature/app-shell/domain/src/commonMain/kotlin/.../model/DrawerSection.kt:38` — `LocalSection.MyCourses` → `LocalSection.HomeQuests`
  - `shared/feature/app-shell/domain/src/commonMain/kotlin/.../model/TabConfig.kt:24` — `LocalConfig.MyCoursesRoot` → `LocalConfig.HomeQuestsRoot`
  - `shared/feature/app-shell/domain/src/commonMain/kotlin/.../logic/Visibility.kt:70,108` — обновить `visibleSections(Tab.LOCAL, ...)` (новый порядок `HomeQuests, MyQuests, Settings`) и `rootOf()` mapping
  - `shared/feature/app-shell/domain/src/commonMain/kotlin/.../logic/AppShellTransitions.kt:31` — обновить `NavStack` mapping в `onRetap`/transition logic (Codex finding #11)
  - `android/feature/app-shell/presentation/src/main/kotlin/.../ui/labels/Labels.kt:52,68,88` — обновить `displayName`, `icon` и `TabConfig.displayName`:
    - `HomeQuests → "Домашние квесты"` (было "Мои курсы")
    - `HomeQuests → Icons.Default.Home` (было `Icons.Default.Book`)
    - `HomeQuestsRoot → "Домашние квесты"`
- Rename в тестах (6 файлов, ~15+ вхождений):
  - `shared/feature/app-shell/domain/src/commonTest/.../NavStackTest.kt:26-77` (6 вхождений)
  - `shared/feature/app-shell/domain/src/commonTest/.../PrimaryUserJourneyTest.kt:132`
  - `shared/feature/app-shell/domain/src/commonTest/.../OnTabRetapUseCaseTest.kt:51,62,71-74,89` (5 вхождений)
  - `shared/feature/app-shell/domain/src/commonTest/.../AppShellTransitionsTest.kt:160,175,190` (3 вхождения)
  - `shared/feature/app-shell/domain/src/commonTest/.../VisibilityTest.kt:192-432` (6 вхождений; включая scenario 22 "visibleSections LOCAL guest" который сейчас проверяет `[MyQuests, MyCourses, Settings]` — обновить на `[HomeQuests, MyQuests, Settings]`)
  - `android/feature/app-shell/presentation/src/test/.../DefaultRootComponentTest.kt:402` (1 комментарий; обновить)
- Grep-проверка после rename: `grep -rn "MyCourses\|MyCoursesRoot" shared/ android/` → 0 matches (кроме `docs/` и git history)

### Explicitly Out of Scope
- Наполнение HomeQuests / MyQuests root screens реальным контентом — "это будет спиннер, каталоги встречаются на многих экранах, не относится к этой задаче" [USER DECIDED]
- Каталог как отдельная сущность или пункт меню — [USER DECIDED]
- Бизнес-логика "что содержится в HomeQuests vs MyQuests" — вне этой фичи; будет отдельная spec при наполнении
- Migration story для ранее закешированных значений `MyCourses` — нет, pre-production, нет persisted data для таких enum values

## Search Criteria for Research

1. **Все упоминания `MyCourses` в codebase** — для полного rename (не только в domain model):
   ```
   grep -rn "MyCourses\|MyCoursesRoot" shared/ android/
   ```
2. **Текущая структура LocalConfig** — файл с `sealed interface LocalConfig` (не нашли точный путь, нужен research; `shared/feature/app-shell/domain/src/commonMain/kotlin/.../model/LocalConfig.kt` вероятнее всего)
3. **Существующие тесты на LocalSection и LocalConfig** — какие paths, какие assertions
4. **Mapping navigation Root → Screen** — кто рендерит `MyCoursesRoot` в Compose? Где decompose component? Этот placeholder остаётся, только переименовывается
5. **Deep links** — если есть deep link схема, использующая название `my-courses` — решить: сохранять старое deep link имя или переименовывать (в master spec это не определено; research фаза спросит)

### Обязательные search directions
- Grep `MyCourses` в ВСЕХ файлах проекта (не только в app-shell)
- Grep `my_courses` / `my-courses` — возможно есть string resources или deep links
- Найти где в DI LocalConfig.MyCoursesRoot injected (Koin module)

### Completeness check
- Count of grep matches перед rename ↔ после — должен быть 0 (вне docs/features/)
- Compile check: `./gradlew assembleDebug` после rename — без ошибок

## Primary User Journeys

1. **Happy path**
   - Start: пользователь открывает приложение
   - Trigger: открывает navigation drawer, переходит в Tab.LOCAL
   - State changes: drawer показывает список LocalSection
   - Expected result: список содержит (по порядку) `"Домашние квесты"`, `"Мои квесты"`, `"Настройки"`. При клике на "Домашние квесты" — переход на `LocalConfig.HomeQuestsRoot` (placeholder screen, как сейчас у MyCoursesRoot)
   - Decision: [USER DECIDED]

2. **No interrupted path** — rename — чисто cosmetic/structural без новых flow

## Feature Domain Contract

Эта фича — **rename-рефакторинг**, не содержит бизнес-логики. `Feature Domain Contract` минимален:

### Terms / Entities / Value Constraints

- `DrawerSection.LocalSection.HomeQuests` — data object (replaces `MyCourses`)
- `LocalConfig.HomeQuestsRoot` — data object (replaces `MyCoursesRoot`)
- Порядок в `visibleSections(Tab.LOCAL, stats)`: `[HomeQuests, MyQuests, Settings].filter(isVisible)`

### Business Rules / Invariants / Guards

1. `HomeQuests.requiredRoles == emptyMap()` (всегда видим)
2. `HomeQuests.tab == Tab.LOCAL`
3. `rootOf(DrawerSection.LocalSection.HomeQuests) == LocalConfig.HomeQuestsRoot`
4. Declaration order в `visibleSections` = `HomeQuests, MyQuests, Settings` (не alphabetical)

### Domain Test Scenarios (phase-01 source of truth)

1. GIVEN `Tab.LOCAL`, `UserStats.empty()` WHEN `visibleSections()` THEN ordered list = `[HomeQuests, MyQuests, Settings]`
2. GIVEN `DrawerSection.LocalSection.HomeQuests` WHEN read `.tab` THEN == `Tab.LOCAL`
3. GIVEN `DrawerSection.LocalSection.HomeQuests` WHEN read `.requiredRoles` THEN == `emptyMap()`
4. GIVEN `DrawerSection.LocalSection.HomeQuests` WHEN `rootOf()` THEN == `LocalConfig.HomeQuestsRoot`
5. GIVEN `DrawerSection.LocalSection.HomeQuests.displayName` WHEN read THEN == `"Домашние квесты"`
6. GIVEN `LocalConfig.HomeQuestsRoot.displayName` WHEN read THEN == `"Домашние квесты"`
7. GIVEN кодовая база WHEN `grep "MyCourses"` THEN zero matches (вне docs/ и прошлых commit history)

## State Matrix

N/A — простой rename, нет ветвистой логики.

## Delegated Decisions Summary

| # | Область | Решение | Обоснование | Risk |
|---|---|---|---|---|
| 1 | Keep `requiredRoles = emptyMap()` | Consistent with MyCourses existing | Preserve behavior | low |
| 2 | `displayName = "Домашние квесты"` | Match user "домашние квесты" | User intent | low |
| 3 | Icon `Icons.Default.Home` | Semantic match | MyCourses icon был Book, Home подходит лучше | low |
| 4 | Navigation target = existing placeholder | User "не относится к задаче" | Minimal scope | low |
| 5 | No migration story | Pre-production | ADR-0004 sync values — не applicable для enum names | low |
| 6 | Update all tests referencing `MyCourses` | Test hygiene | Compile requirement | low |

## Acceptance Criteria

1. [ ] GIVEN `DrawerSection.LocalSection.HomeQuests` WHEN inspect THEN `data object` defined
2. [ ] GIVEN `DrawerSection.LocalSection.MyCourses` WHEN grep THEN zero matches (rename complete)
3. [ ] GIVEN `LocalConfig.HomeQuestsRoot` WHEN inspect THEN `data object` defined
4. [ ] GIVEN `LocalConfig.MyCoursesRoot` WHEN grep THEN zero matches
5. [ ] GIVEN `visibleSections(Tab.LOCAL, UserStats.empty())` WHEN invoked THEN returns `[HomeQuests, MyQuests, Settings]` (в этом порядке)
6. [ ] GIVEN `rootOf(DrawerSection.LocalSection.HomeQuests)` WHEN invoked THEN returns `LocalConfig.HomeQuestsRoot`
7. [ ] GIVEN `DrawerSection.LocalSection.HomeQuests.displayName` WHEN read THEN `"Домашние квесты"`
8. [ ] GIVEN `LocalConfig.HomeQuestsRoot.displayName` WHEN read THEN `"Домашние квесты"`
9. [ ] GIVEN `./gradlew assembleDebug` WHEN run THEN success (no unresolved references)
10. [ ] GIVEN `./gradlew test` (all modules) WHEN run THEN all tests pass; updated tests reference new names

## Invariant Check (from docs/invariants.md)

| Invariant | Impact | Decision |
|-----------|--------|----------|
| 1. Domain layer purity | Rename в domain layer — без новых типов | preserve |
| 6. Walking Skeleton ownership | Renaming existing domain objects — допустимо в design/spec ("Renaming классов в design phase допустимо") | preserve |

## Constraints (from PROJECT_STRUCTURE.md + ADRs)

- Compose/Material3 UI (ADR-0010) — Labels.kt обновляется, иконки из `androidx.compose.material.icons`
- Decompose navigation (ADR-0008) — `LocalConfig.HomeQuestsRoot` остаётся TabConfig sealed child
- Koin DI (ADR-0009) — если root injected в Koin module как `LocalConfig.MyCoursesRoot`, обновить reference
