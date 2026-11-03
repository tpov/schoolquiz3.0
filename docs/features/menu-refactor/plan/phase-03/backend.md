---
phase: 03
role: backend-dev
---

# Phase 03 — Backend Tasks

## Pattern Invariants

- `app-shell:domain` может импортировать `QualificationLevel` из `core:foundation` (dep добавлен в Phase 01)
- `app-shell:domain` НЕ импортирует `qualification:domain` — cross-feature BLOCKER
- Breaking changes (`DrawerFooterAction` sealed set, `visibleFooterActions` signature) — обновить все call sites в рамках этой фазы (presentation call sites обновляет frontend-dev в Phase 07)
- `visibleFooterActions` signature: `(isDebugBuild: Boolean, stats: UserStats)` — нет overlay параметра
- Порядок вывода `visibleFooterActions`: `[DesignCatalog?, SyncNow?, About]` — стабильный

---

## 1. ADD DrawerFooterAction.SyncNow

**Файл:** `shared/feature/app-shell/domain/src/commonMain/kotlin/.../model/DrawerFooterAction.kt`
- **Тип:** sealed interface member (добавление)
- **Сигнатура:** `data object SyncNow : DrawerFooterAction`
- **Вход:** существующий sealed set `{DesignCatalog, About}`
- **Поведение / Выход:**
  - После добавления sealed set: `{DesignCatalog, SyncNow, About}` (SyncNow между DesignCatalog и About)
  - Presentation call sites (`DrawerFooter.kt`, `Labels.kt`) получат compile stubs в задачах 9-10 этой же фазы
- **Edge cases:**
  - Порядок в sealed — имеет значение для `visibleFooterActions` ordering
  - Kotlin exhaustive `when` немедленно ломает `DrawerFooter.kt:49` и `Labels.kt:116` — поэтому задачи 9-10 в этой же фазе устраняют compile error до commit
- **Depends on:** ничего
- **Canonical reference:** `06-api-contract.md §3.3`
- **Rationale:** catalog-foundation sub-spec требует SyncNow action; Footer Contract (из 0-spec-dev-mode.md) определяет 3-member set. Стабы устраняются в Phase 07.

---

## 2. ADD RootEvent variants

**Файл:** `shared/feature/app-shell/domain/src/commonMain/kotlin/.../model/RootEvent.kt`
- **Тип:** sealed interface extensions
- **Сигнатура:** добавить `data object DevModeActivated : RootEvent`, `data object DevModeAlreadyActive : RootEvent`, `data object SyncStarted : RootEvent`
- **Вход:** существующий `sealed interface RootEvent { data object SystemBack : RootEvent }`
- **Поведение / Выход:**
  - 4-member sealed hierarchy (SystemBack existing + 3 new)
  - Domain-only types — нет Android imports
- **Edge cases:**
  - Presentation `when(event)` — может быть non-exhaustive после добавления; Phase 07 обновляет
- **Depends on:** ничего
- **Canonical reference:** `06-api-contract.md §3.1`
- **Rationale:** ADR-HLA-05 + 07-events.md — events для Snackbar feedback.

---

## 3. ADD RootComponent methods

**Файл:** `shared/feature/app-shell/domain/src/commonMain/kotlin/.../navigation/RootComponent.kt`
- **Тип:** interface method additions
- **Сигнатура:** `fun onVersionTap(nowMillis: Long)`, `fun onSyncNow()`
- **Вход:** существующий `interface RootComponent`
- **Поведение / Выход:**
  - Interface расширяется на 2 метода
  - `DefaultRootComponent` получает TODO stubs в задаче 9 этой же фазы (compile-fix)
  - `nowMillis: Long` — epoch milliseconds, используется в `ActivateDevModeUseCase`
- **Edge cases:**
  - `DefaultRootComponent` реализует `RootComponent` — compile error после добавления методов в interface; задача 9 добавляет `TODO()` stubs немедленно
  - Любые другие реализации `RootComponent` (фейки в тестах, Mock в тестах) — нужно аналогично добавить stubs
- **Depends on:** ничего
- **Canonical reference:** `06-api-contract.md §3.2`
- **Rationale:** ADR-HLA-05 — метод в RootComponent симметричен `onActiveTabRetap` паттерну. Задача 9 гарантирует compile green.

---

## 4. ADD UserStatsRepository methods

**Файл:** `shared/feature/app-shell/domain/src/commonMain/kotlin/.../repository/UserStatsRepository.kt`
- **Тип:** interface method additions
- **Сигнатура:** `suspend fun setLocalDeveloperLevel(value: Int)`, `suspend fun refreshProfile(): Result<Unit>`
- **Вход:** существующий интерфейс (с `observeStats()` + `currentStats()`)
- **Поведение / Выход:**
  - `setLocalDeveloperLevel` — только для dev mode; пишет `developer` в local Room
  - `refreshProfile` — вызывается SyncWorker; полный перезапись UserStats из Firestore
- **Edge cases:**
  - `FakeUserStatsRepository` в domain/fake/ и presentation/fake/ — нужно добавить no-op реализации обоих методов; иначе compile error
- **Depends on:** ничего (interface extension)
- **Canonical reference:** `06-api-contract.md §3.6`
- **Rationale:** ADR-HLA-02 — `setLocalDeveloperLevel` = единственный разрешённый client write path для developer field.

---

## 5. UPDATE FakeUserStatsRepository — add new methods

**Файлы:**
- `shared/feature/app-shell/domain/src/commonTest/.../fake/FakeUserStatsRepository.kt`
- `android/feature/app-shell/presentation/src/.../fake/FakeUserStatsRepository.kt`

- **Тип:** fake class update
- **Сигнатура:** добавить `var setLocalDeveloperLevelCalls: Int = 0`, `var lastSetDeveloperLevel: Int? = null` + `override suspend fun setLocalDeveloperLevel(value: Int)` + `override suspend fun refreshProfile(): Result<Unit>`
- **Вход:** существующие fake implementations
- **Поведение / Выход:**
  - `setLocalDeveloperLevel` — track calls, сохранять последнее value
  - `refreshProfile` — возвращает `Result.success(Unit)` по умолчанию; добавить `var refreshResult: Result<Unit>` для тестовой настройки
- **Edge cases:** оба fake должны быть обновлены (два экземпляра — research подтвердил дублирование)
- **Depends on:** шаг 4
- **Canonical reference:** internal (no api-contract entry)
- **Rationale:** Fakes должны компилироваться после расширения interface.

---

## 6. UPDATE Visibility.isVisible — superqualification OR-bypass

**Файл:** `shared/feature/app-shell/domain/src/commonMain/kotlin/.../logic/Visibility.kt`
- **Тип:** function body update (строка ~50)
- **Сигнатура:** `fun isVisible(section: DrawerSection, stats: UserStats): Boolean`
- **Вход:** `section: DrawerSection`, `stats: UserStats`
- **Поведение / Выход:**
  - Если `stats.qualification.developer >= QualificationLevel.LEVEL_1.points` → `true` (superqualification OR-bypass)
  - Иначе → existing `section.requiredRoles.all { (role, min) -> actualLevel(role, stats) >= min }`
  - Import: `import com.tpov.schoolquiz.shared.core.foundation.QualificationLevel`
- **Edge cases:**
  - `emptyMap()` requiredRoles → `all {}` on empty = `true` (already visible) — superqualification doesn't change this
  - `developer=0` → falls through to AND-check (existing behavior)
  - `developer=99` → < 100, не superqualification — AND-check применяется
- **Depends on:** Phase 01 (core:foundation dep добавлен в build.gradle.kts)
- **Canonical reference:** `06-api-contract.md §4.1`
- **Rationale:** Problem 2 fix — superqualification per spec + ADR-HLA-02.

---

## 7. UPDATE Visibility.visibleFooterActions — new signature + SyncNow

**Файл:** `shared/feature/app-shell/domain/src/commonMain/kotlin/.../logic/Visibility.kt`
- **Тип:** function full replacement (строка ~142)
- **Сигнатура:** `fun visibleFooterActions(isDebugBuild: Boolean, stats: UserStats): List<DrawerFooterAction>`
- **Вход:** `isDebugBuild: Boolean`, `stats: UserStats`
- **Поведение / Выход:**
  - `devToolsVisible = isDebugBuild || stats.qualification.developer >= QualificationLevel.LEVEL_1.points`
  - Если `devToolsVisible` → output `[DesignCatalog, SyncNow, About]`
  - Иначе → output `[About]`
  - Order: DesignCatalog → SyncNow → About (стабильный per spec)
- **Edge cases:**
  - `isDebugBuild=true` → всегда все 3, независимо от developer level
  - `developer=100, isDebugBuild=false` → все 3
  - `developer=0, isDebugBuild=false` → только About
- **Depends on:** шаг 1 (SyncNow в sealed), шаг 6 (QualificationLevel import)
- **Canonical reference:** `06-api-contract.md §4.3`
- **Rationale:** Problem 5 fix — breaking signature change; catalog-foundation adds SyncNow.

---

## 8. UPDATE DrawerSection.EventsSection.ActiveEvents — replace magic numbers

**Файл:** `shared/feature/app-shell/domain/src/commonMain/kotlin/.../model/DrawerSection.kt`
- **Тип:** property body update (строки 100-103)
- **Сигнатура:** изменяется только тело `requiredRoles`
- **Вход:** `Role.TESTER to 100, Role.MODERATOR to 100, Role.ADMIN to 100, Role.DEVELOPER to 100`
- **Поведение / Выход:**
  - `Role.TESTER to QualificationLevel.LEVEL_1.points`
  - `Role.MODERATOR to QualificationLevel.LEVEL_1.points`
  - `Role.ADMIN to QualificationLevel.LEVEL_1.points`
  - `Role.DEVELOPER to QualificationLevel.LEVEL_1.points`
  - Runtime value не меняется (все = 100), только убирает magic number
- **Edge cases:**
  - `VisibilityTest.kt:387-390` — assertion `assertEquals(100, roles[Role.TESTER])` остаётся корректным (runtime value = 100)
- **Depends on:** Phase 01 (core:foundation dep), шаг 6 (QualificationLevel import в файле)
- **Canonical reference:** `06-api-contract.md §3.4` (DrawerSection AC #4)
- **Rationale:** Spec AC #7 — no magic numbers. Problem 1 fix.

---

## 9. ADD compile-fix stubs in DefaultRootComponent

- **Файл:** `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/feature/appshell/presentation/component/DefaultRootComponent.kt`
- **Тип:** class update (stub overrides)
- **Сигнатура:** `override fun onVersionTap(nowMillis: Long) = TODO("Phase 07")` и `override fun onSyncNow() = TODO("Phase 07")`
- **Вход:** `DefaultRootComponent.kt` после шага 3 (RootComponent interface получил новые методы)
- **Поведение / Выход:**
  - Добавить два stub override метода — `TODO("Phase 07")` body
  - Единственная цель: устранить compile error, не содержат логики
  - Phase 07 полностью заменяет stubs реальной реализацией (tasks 4-5 в phase-07/backend.md)
- **Edge cases:**
  - Если `DefaultRootComponent` уже наследует или делегирует через by-delegate — убедиться что stubs не конфликтуют с делегатом
  - `TODO("Phase 07")` — intentional; не тихий no-op, а crasher если вызвать до Phase 07
- **Depends on:** шаг 3 (RootComponent interface extended)
- **Canonical reference:** internal (no api-contract entry — временный stub)
- **Rationale:** Compile-green invariant для Phase 03 (Variant A из review Finding #1). Stubs заменяются реальной реализацией в Phase 07 tasks 4-5.

---

## 10. ADD compile-fix stubs in DrawerFooter + Labels

- **Файлы:**
  - `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/feature/appshell/presentation/ui/drawer/DrawerFooter.kt`
  - `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/feature/appshell/presentation/ui/Labels.kt`
- **Тип:** stub branch additions в exhaustive `when` expressions
- **Сигнатура (DrawerFooter.kt):** добавить ветку `DrawerFooterAction.SyncNow -> { /* TODO Phase 07 — will call onSyncNow() */ }` в `when(action)` около `DrawerFooter.kt:49`
- **Сигнатура (Labels.kt):** добавить ветку `DrawerFooterAction.SyncNow -> "SyncNow" /* TODO Phase 07 */` (или аналогичную форму) около `Labels.kt:116`
- **Вход:** существующий exhaustive `when(action)` в обоих файлах; после шага 1 они не компилируются
- **Поведение / Выход:**
  - DrawerFooter.kt: SyncNow branch = no-op; не крашит, не делает ничего (безопасный placeholder)
  - Labels.kt: SyncNow branch возвращает placeholder строку или `Icons.Default.Refresh` placeholder
  - Phase 07 tasks 4-5 (frontend.md) заменяют оба stub на реальную логику
- **Edge cases:**
  - Если `Labels.kt` использует `when` expression (не statement) — ветка обязана вернуть правильный тип
  - DrawerFooter.kt может использовать `when(action)` как statement или expression — проверить тип при открытии файла
- **Depends on:** шаг 1 (DrawerFooterAction.SyncNow добавлен)
- **Canonical reference:** internal (no api-contract entry — временный stub)
- **Rationale:** Compile-green invariant для Phase 03 (Variant A из review Finding #1). Phase 07 frontend tasks заменяют stubs полными реализациями.
