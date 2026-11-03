---
phase: 02
role: frontend-dev
---

# Phase 02 — Frontend Tasks

## Pattern Invariants

- `Icons.Default.Book` в imports Labels.kt — НЕ удалять (используется в MyQuests строка ~67 и InternetSection.Catalog строка ~71)
- `Icons.Default.Home` уже есть в imports Labels.kt:8 — не добавлять дублирующий import
- Строки Labels.kt — atomic update вместе с backend rename

---

## 1. UPDATE Labels.kt — displayName + icon + root display name

**Файл:** `android/feature/app-shell/presentation/src/main/kotlin/.../ui/Labels.kt`
- **Тип:** function body update (when-expression arms)
- **Сигнатура:** `fun DrawerSection.displayName(): String` + `fun DrawerSection.icon(): ImageVector` + `fun TabConfig.displayName(): String`
- **Вход:** существующие arms для `MyCourses` / `MyCoursesRoot`
- **Поведение / Выход:**
  - Строка ~52: `DrawerSection.LocalSection.HomeQuests -> "Домашние квесты"` (было `MyCourses -> "Мои курсы"`)
  - Строка ~68: `DrawerSection.LocalSection.HomeQuests -> Icons.Default.Home` (было `MyCourses -> Icons.Default.Book`)
  - Строка ~88: `LocalConfig.HomeQuestsRoot -> "Домашние квесты"` (было `MyCoursesRoot -> "Мои курсы"`)
- **Edge cases:**
  - После rename `HomeQuests` — exhaustive when-expression компилятор проверяет полноту
  - `Icons.Default.Home` уже импортирован — не добавлять дублирующий import
  - `Icons.Default.Book` НЕ удалять из imports — используется в других строках
- **Depends on:** backend Phase 02 steps 1, 2 (новые имена существуют)
- **Canonical reference:** internal (no api-contract entry)
- **Rationale:** Spec AC #6 — displayName "Домашние квесты" для HomeQuests; icon обновлён с Book на Home для смысловой точности.
