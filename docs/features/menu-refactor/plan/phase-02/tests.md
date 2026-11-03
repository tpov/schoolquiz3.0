---
phase: 02
role: test-dev
---

# Phase 02 — Test Tasks

## Pattern Invariants

- Тест-dev обновляет тесты параллельно с production rename
- 26 строк в 6 файлах — исчерпывающий список из `1-research.md §3.Home-quests`
- Нет новых test файлов — только обновление существующих
- Framework: `kotlin.test` для domain тестов

---

## Файлы для обновления (26 строк)

### 1. NavStackTest.kt (9 строк)

**Файл:** `shared/feature/app-shell/domain/src/commonTest/.../NavStackTest.kt`

Строки 26, 27, 34, 47, 55, 65, 68, 74, 77 — заменить `MyCourses` → `HomeQuests`, `MyCoursesRoot` → `HomeQuestsRoot`.

**Сценарии HQ в этом файле:** тесты проверяют NavStack behaviour с HomeQuests section.

---

### 2. PrimaryUserJourneyTest.kt (1 строка)

**Файл:** `shared/feature/app-shell/domain/src/commonTest/.../PrimaryUserJourneyTest.kt`

Строка 132 — заменить `MyCourses` → `HomeQuests`.

---

### 3. OnTabRetapUseCaseTest.kt (5 строк)

**Файл:** `shared/feature/app-shell/domain/src/commonTest/.../OnTabRetapUseCaseTest.kt`

Строки 51, 62, 74, 89 — заменить `MyCourses` → `HomeQuests`, `MyCoursesRoot` → `HomeQuestsRoot`.
Строка 71 — комментарий, обновить для consistency.

---

### 4. AppShellTransitionsTest.kt (3 строки)

**Файл:** `android/feature/app-shell/presentation/src/test/.../AppShellTransitionsTest.kt`

Строки 160, 175, 190 — заменить `MyCourses` → `HomeQuests`, `MyCoursesRoot` → `HomeQuestsRoot`.

---

### 5. VisibilityTest.kt (7 строк)

**Файл:** `shared/feature/app-shell/domain/src/commonTest/.../VisibilityTest.kt`

- Строка ~192 — backtick test name: обновить `MyCourses` → `HomeQuests` в имени теста
- Строка ~198 — assertion: список `[HomeQuests, MyQuests, Settings]` (был `[MyQuests, MyCourses, Settings]`) — **BEHAVIOR CHANGE** (reorder + rename)
- Строки ~326, ~327 — update test names + assertions
- Строка ~336 — backtick name update
- Строка ~341 — assertion update
- Строка ~432 — update reference

**Ключевое изменение:** строка ~198 — assertion `result[0] is HomeQuests` (первый) — это HQ-01/HQ-07 verification.

---

### 6. DefaultRootComponentTest.kt (1 строка)

**Файл:** `android/feature/app-shell/presentation/src/test/.../DefaultRootComponentTest.kt`

Строка ~402 — закомментированная строка — обновить для hygiene consistency.

---

## Сценарии (HQ-01..HQ-07)

Все 7 сценариев покрываются обновлением существующих тестов:

- HQ-01: `visibleSections(LOCAL)[0] is HomeQuests` — VisibilityTest.kt строка ~198
- HQ-02: `HomeQuests in visibleSections(LOCAL)` — VisibilityTest.kt
- HQ-03: `MyCourses !in visibleSections(LOCAL)` — VisibilityTest.kt (имплицитно — если только HomeQuests, MyQuests, Settings, то MyCourses не присутствует)
- HQ-04: `rootOf(HomeQuests) == HomeQuestsRoot` — NavStackTest.kt or VisibilityTest.kt
- HQ-05: `HomeQuests.requiredRoles == emptyMap()` — проверить в NavStackTest.kt или VisibilityTest.kt
- HQ-06: `HomeQuests is LocalSection` — type check, implicit в compile
- HQ-07: порядок строго `[HomeQuests, MyQuests, Settings]` — VisibilityTest.kt строка ~198

---

## Validation

| Команда | Ожидаемый результат |
|---------|---------------------|
| `./gradlew :shared:feature:app-shell:domain:jvmTest --no-configuration-cache` | GREEN — HQ-01..07 зелёные |
| `./gradlew :android:feature:app-shell:presentation:test --no-configuration-cache` | GREEN — transitions + component tests |
| `grep -rn "MyCourses\|MyCoursesRoot" shared/ android/ --include="*.kt"` | 0 matches (вне docs/) |
