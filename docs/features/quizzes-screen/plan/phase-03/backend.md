---
phase: 03
role: backend-dev
---

# Phase-03 Backend Tasks: Gradle Scaffold

### Pattern Invariants

- Scaffold ownership (CLAUDE.md): `build.gradle.kts`, `settings.gradle.kts`, `AppApplication.kt` — только backend-dev.
- `kotlinx-serialization` plugin ОБЯЗАТЕЛЕН в новом module build.gradle.kts — иначе `@Serializable` не компилируется.
- Зависимости: `android/feature/quizzes-screen/presentation` импортирует только `android/core/designsystem` и `shared/feature/*/domain` + `shared/core/catalog/domain`. НИКАКИХ `android/feature/quest/presentation` или `android/feature/app-shell/presentation` в dependency list.

---

## Create android/feature/quizzes-screen/presentation/build.gradle.kts

- **Файл:** `android/feature/quizzes-screen/presentation/build.gradle.kts`
- **Тип:** Gradle build script (new file)
- **Сигнатура:** `plugins { id("...android.library"); kotlin("android"); id("org.jetbrains.kotlin.plugin.serialization") }`
- **Вход:** зависимости из `01-architecture.md` Module Dependency Graph
- **Поведение / Выход:**
  - `plugins` block: Android library plugin + kotlin + `org.jetbrains.kotlin.plugin.serialization`
  - `dependencies` block:
    - `implementation(project(":android:core:designsystem"))` — QuestCard, HierarchyItemCard, BreadcrumbBar
    - `implementation(project(":shared:feature:quest:domain"))` — QuestRepository, Quest, QuestId
    - `implementation(project(":shared:feature:section:domain"))` — SectionRepository, Section, SectionId
    - `implementation(project(":shared:feature:theme:domain"))` — ThemeRepository, Theme, ThemeId
    - `implementation(project(":shared:feature:lesson:domain"))` — LessonRepository, Lesson, LessonId
    - `implementation(project(":shared:core:catalog:domain"))` — CatalogId
    - Decompose + Essenty dependencies (версии из `libs.versions.toml`)
    - `kotlinx-serialization-json` (версия из `libs.versions.toml`)
    - `koin-android` (версия из `libs.versions.toml`)
  - `testImplementation` block: JUnit 4, coroutines-test, Decompose testing, MockK
  - **ЗАПРЕЩЕНЫ** в dependencies: `":android:feature:quest:presentation"`, `":android:feature:app-shell:presentation"` (Invariant 3)
- **Edge cases:**
  - Если `org.jetbrains.kotlin.plugin.serialization` version catalog alias отличается от `"kotlin.serialization"` — verify в `libs.versions.toml`
  - Verify что Decompose + Essenty aliases существуют в `libs.versions.toml`
- **Depends on:** `libs.versions.toml` (existing), other project module paths
- **Canonical reference:** `01-architecture.md §Module Dependency Graph`, ADR-QS-02
- **Rationale:** kotlinx-serialization plugin обязателен для `@Serializable sealed class QuizzesConfig`. Отсутствие plugin → compile error при annotation processing.

---

## Add module entry to settings.gradle.kts

- **Файл:** `settings.gradle.kts`
- **Тип:** Gradle settings script (modification)
- **Сигнатура:** `include(":android:feature:quizzes-screen:presentation")`
- **Вход:** N/A
- **Поведение / Выход:**
  - Добавить include entry для нового модуля
  - projectDir mapping если нужно (verify conventions существующих modules)
- **Edge cases:**
  - Verify что path `:android:feature:quizzes-screen:presentation` соответствует директории `android/feature/quizzes-screen/presentation`
  - Verify naming convention — существующие modules: `:android:feature:quest:presentation` и т.д.
- **Depends on:** `settings.gradle.kts` existing structure
- **Canonical reference:** `01-architecture.md`
- **Rationale:** Без include entry новый module не видит Gradle build system.

---

## Create AndroidManifest.xml for new module

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/AndroidManifest.xml`
- **Тип:** Android library manifest (new file)
- **Сигнатура:** minimal library manifest (package declaration only, no activities)
- **Вход:** package = `com.tpov.schoolquiz.android.feature.quizzes_screen.presentation` (verify naming convention с существующими modules)
- **Поведение / Выход:**
  - Минимальный manifest для Android library module
  - Нет activities, нет permissions
- **Edge cases:**
  - Package naming: `quizzes_screen` с underscore vs `quizzesscreen` — verify по существующим module manifests
- **Depends on:** N/A
- **Canonical reference:** existing module manifests (e.g. `android/feature/quest/presentation/src/main/AndroidManifest.xml`)
- **Rationale:** Required для Android library module.

---

## Update AppApplication.kt — add quizzesPresentationModule

- **Файл:** `apps/android-next/src/main/kotlin/.../AppApplication.kt`
- **Тип:** Application class (modification)
- **Сигнатура:** existing `startKoin { modules(...) }` call
- **Вход:** `QuizzesPresentationModule` из нового module
- **Поведение / Выход:**
  - Добавить `quizzesPresentationModule` в список модулей в `startKoin`
  - Import `com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.di.quizzesPresentationModule`
- **Edge cases:**
  - Порядок модулей: `quizzesPresentationModule` должен следовать ПОСЛЕ modules которые предоставляют Repository singletons (QuestRepository, SectionRepository и т.д.) — они должны быть уже зарегистрированы
  - Verify порядок существующих modules в AppApplication.kt
- **Depends on:** `QuizzesPresentationModule.kt` (frontend-dev task в Phase-03)
- **Canonical reference:** `06-api-contract.md:742`, ADR-QS-11 (Koin DI)
- **Rationale:** Scaffold ownership rule — только backend-dev модифицирует AppApplication.kt.
