---
phase: 02
role: backend-dev
---

# Phase 02 — Backend Tasks

## Pattern Invariants

- Rename атомарный — все 8 production строк меняются за один commit
- `Icons.Default.Book` в imports Labels.kt — НЕ удалять (используется в MyQuests строка и InternetSection.Catalog)
- Нет изменений в build.gradle.kts, libs.versions.toml, settings.gradle.kts в этой фазе
- `DrawerSection.LocalSection.HomeQuests.requiredRoles` = `emptyMap()` (не меняется — уже так у MyCourses)
- Порядок в `visibleSections(Tab.LOCAL)` после rename: HomeQuests первым (position 1)

---

## 1. RENAME DrawerSection.MyCourses → HomeQuests

**Файл:** `shared/feature/app-shell/domain/src/commonMain/kotlin/.../model/DrawerSection.kt`
- **Тип:** `data object` переименование
- **Сигнатура:** `data object HomeQuests : LocalSection` (было `MyCourses`)
- **Вход:** существующий `data object MyCourses : LocalSection`
- **Поведение / Выход:**
  - Имя object: `MyCourses` → `HomeQuests`
  - `override val tab: Tab = Tab.LOCAL` — без изменений
  - `override val requiredRoles: Map<Role, Int> = emptyMap()` — без изменений
  - Комментарий строки (если есть) — обновить для consistency
- **Edge cases:** убедиться что нет serialized форм (confirmed in grounding — нет)
- **Depends on:** ничего
- **Canonical reference:** `06-api-contract.md §3.4`
- **Rationale:** Grounding Problem 3 — pure rename per spec AC.

---

## 2. RENAME TabConfig.MyCoursesRoot → HomeQuestsRoot

**Файл:** `shared/feature/app-shell/domain/src/commonMain/kotlin/.../model/TabConfig.kt`
- **Тип:** `data object` переименование
- **Сигнатура:** `data object HomeQuestsRoot : LocalConfig` (было `MyCoursesRoot`)
- **Вход:** существующий `data object MyCoursesRoot : LocalConfig`
- **Поведение / Выход:**
  - Имя object: `MyCoursesRoot` → `HomeQuestsRoot`
  - Тип остаётся `LocalConfig`
- **Edge cases:** нет Room/Koin hardcoding для `MyCoursesRoot` (confirmed в grounding)
- **Depends on:** ничего
- **Canonical reference:** `06-api-contract.md §3.5`
- **Rationale:** Grounding Problem 3 — rename matching DrawerSection.

---

## 3. UPDATE Visibility.visibleSections LOCAL — reorder + rename

**Файл:** `shared/feature/app-shell/domain/src/commonMain/kotlin/.../logic/Visibility.kt`
- **Тип:** function body update
- **Сигнатура:** function `visibleSections(tab: Tab, stats: UserStats): List<DrawerSection>` — только тело меняется
- **Вход:** `Tab.LOCAL` case в когда-expression
- **Поведение / Выход:**
  - Строка ~70: `Tab.LOCAL -> listOf(DrawerSection.LocalSection.HomeQuests, DrawerSection.LocalSection.MyQuests, DrawerSection.LocalSection.Settings).filter { isVisible(it, stats) }`
  - Порядок: HomeQuests ПЕРВЫМ (было MyCourses на позиции 2), MyQuests вторым, Settings третьим
- **Edge cases:** `filter` сохраняется — секции могут быть скрыты по `requiredRoles` (у HomeQuests requiredRoles = emptyMap, всегда видна)
- **Depends on:** шаги 1, 2 (новые имена)
- **Canonical reference:** `06-api-contract.md §4.2`
- **Rationale:** Grounding Problem 3 — HQ-07 assertion: строгий порядок [HomeQuests, MyQuests, Settings].

---

## 4. UPDATE Visibility.rootOf — rename mapping

**Файл:** `shared/feature/app-shell/domain/src/commonMain/kotlin/.../logic/Visibility.kt`
- **Тип:** when-expression arm update
- **Сигнатура:** `DrawerSection.LocalSection.HomeQuests -> LocalConfig.HomeQuestsRoot` (строка ~108)
- **Вход:** существующий case `MyCourses -> LocalConfig.MyCoursesRoot`
- **Поведение / Выход:** mapping обновлён — `HomeQuests` → `HomeQuestsRoot`
- **Edge cases:** exhaustive `when` — после rename Kotlin compile-проверяет что все sealed variants покрыты
- **Depends on:** шаги 1, 2
- **Canonical reference:** `06-api-contract.md §4.4`
- **Rationale:** rootOf должен отражать новые имена.

---

## 5. UPDATE AppShellTransitions — rename

**Файл:** `android/feature/app-shell/presentation/src/main/kotlin/.../ui/AppShellTransitions.kt`
- **Тип:** when-expression arm update
- **Сигнатура:** строка ~31: `HomeQuests -> NavStack(HomeQuestsRoot)` (было `MyCourses -> NavStack(MyCoursesRoot)`)
- **Вход:** существующая строка
- **Поведение / Выход:** transition mapping обновлён
- **Edge cases:** exhaustive when — compile check
- **Depends on:** шаги 1, 2
- **Canonical reference:** internal (no api-contract entry)
- **Rationale:** AppShellTransitions должен использовать новые имена для навигации.
