---
phase: 05
role: backend-dev
---

# Phase-05 Backend Tasks

Scaffold для нового модуля `android/feature/quest/presentation`: `build.gradle.kts`, `settings.gradle.kts` entry, `AppApplication.kt` регистрация `questPresentationModule`.

---

## Pattern Invariants

- `android/feature/quest/presentation` ДОЛЖЕН объявлять плагин `schoolquiz.android.compose.library` — аналогично `android/feature/app-shell/presentation/build.gradle.kts`
- `decompose-testutils` ДОЛЖЕН быть в `testImplementation` (JVM тесты), НЕ в `androidTestImplementation`
- `settings.gradle.kts` — include строка добавляется в алфавитном порядке среди `:android:feature:*` entries
- `AppApplication.kt` — `questPresentationModule` добавляется в `modules(...)` список; импорт добавляется в секцию импортов

---

## 1. Add :android:feature:quest:presentation to settings.gradle.kts

- **Файл:** `settings.gradle.kts` (root)
- **Тип:** configuration — update
- **Сигнатура:** добавить `include(":android:feature:quest:presentation")` в список `include` строк
- **Вход:** существующий `settings.gradle.kts`
- **Поведение / Выход:**
  - Найти блок с `include(":android:feature:app-shell:presentation")` (или ближайшую `:android:feature:*` строку)
  - Добавить `include(":android:feature:quest:presentation")` рядом (алфавитный порядок: quest после app-shell)
  - Gradle sync после добавления
- **Edge cases:**
  - Если `include(":android:feature:quest:presentation")` уже есть → NO CHANGE (verify)
- **Depends on:** существующие feature entries в `settings.gradle.kts`
- **Canonical reference:** internal (no api-contract entry)
- **Rationale:** без этой строки Gradle не видит модуль; добавляется первым чтобы разблокировать compile для остальных задач фазы

---

## 2. Create build.gradle.kts for android/feature/quest/presentation

- **Файл:** `android/feature/quest/presentation/build.gradle.kts`
- **Тип:** Gradle build script — new file
- **Сигнатура:** `plugins { id("schoolquiz.android.compose.library") }`
- **Вход:** N/A (new file)
- **Поведение / Выход:**
  - `namespace = "com.tpov.schoolquiz.android.feature.quest.presentation"`
  - `implementation(project(":shared:feature:quest:domain"))` — для QuestId, QuestRepository, ObserveMyQuestsUseCase
  - `implementation(project(":shared:core:catalog:domain"))` — для ObserveCatalogsUseCase, CatalogId
  - `implementation(project(":android:core:designsystem"))` — для QuestCard, StarRating, QuestDisplayItem, CatalogSpinner
  - `implementation(project(":android:core:navigation"))` — для `Navigator` interface (navigation abstraction layer)
  - `implementation(project(":shared:feature:app-shell:domain"))` — для `Navigator`, `Destination`, `LocalConfig` (Navigator interface и Destination sealed types живут в app-shell:domain, не в android:core:navigation; проверить транзитивность — если подтягивается через другой dep, строку можно убрать)
  - `implementation(libs.bundles.decompose)` — Decompose + Essenty
  - `implementation(libs.bundles.koin.android)` — Koin
  - `implementation(libs.bundles.compose.ui)` — Compose + BOM
  - `implementation(platform(libs.compose.bom))`
  - `implementation(libs.bundles.androidx.lifecycle.compose)`
  - `testImplementation(libs.junit4)`
  - `testImplementation(libs.kotlin.test.junit)`
  - `testImplementation(libs.bundles.testing.unit)` — kotlinx-coroutines-test, MockK
  - `testImplementation(libs.decompose.testutils)` — OQ-TEST-1 (см. Handoff Notes)
  - `androidTestImplementation(platform(libs.compose.bom))`
  - `androidTestImplementation(libs.compose.ui.test.junit4)`
  - `androidTestImplementation(libs.androidx.test.ext.junit)`
  - `debugImplementation(libs.compose.ui.test.manifest)`
- **Edge cases:**
  - `decompose.testutils` — если `libs.decompose.testutils` нет в `libs.versions.toml` → добавить алиас `decompose-testutils = { module = "com.arkivanov.decompose:decompose-testutils", version.ref = "decompose" }` (корректный artifact — см. task #3 и Handoff Notes)
  - Не добавлять `implementation(libs.bundles.decompose)` дважды
- **Depends on:** task #1 (settings.gradle.kts), `libs.versions.toml` entries
- **Canonical reference:** internal (no api-contract entry)
- **Rationale:** отдельный модуль изолирует quest/presentation от app-shell/presentation; следует той же конвенции что `app-shell/presentation/build.gradle.kts`

---

## 3. Add decompose-testutils to libs.versions.toml (if missing)

- **Файл:** `gradle/libs.versions.toml`
- **Тип:** version catalog — conditional update
- **Сигнатура:** добавить alias `decompose-testutils` в `[libraries]` секцию
- **Вход:** существующий `libs.versions.toml`
- **Поведение / Выход:**
  - Проверить: если `decompose-testutils = { module = "com.arkivanov.decompose:decompose-testutils", version.ref = "decompose" }` уже есть → NO CHANGE
  - Если отсутствует → добавить после строки с `decompose-extensions-compose` (строки 115-116 в текущем файле):
    ```
    decompose-testutils = { module = "com.arkivanov.decompose:decompose-testutils", version.ref = "decompose" }
    ```
  - `decompose = "3.1.0"` уже существует (строка 35) — версия берётся оттуда
- **Edge cases:**
  - Если Decompose 3.1.0 не публикует `decompose-testutils` artifact → проверить на Maven Central; если отсутствует → использовать `decompose` artifact напрямую для тестов (override в `build.gradle.kts`)
  - Для Decompose ComponentContext в тестах достаточно `DefaultComponentContext(LifecycleRegistry())` без testutils — уточнить в `04-testing.md`
- **Depends on:** существующий `decompose = "3.1.0"` entry
- **Canonical reference:** internal (no api-contract entry)
- **Rationale:** OQ-TEST-1 из `phase-05/overview.md` — test-dev требует testutils для `TestComponentContext`; backend-dev добавляет dependency

---

## 4. Register questPresentationModule in AppApplication.kt

- **Файл:** `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt`
- **Тип:** class — update `startKoin { modules(...) }` block
- **Сигнатура:** добавить `questPresentationModule` в `modules(...)` список внутри `startKoin { ... }`
- **Вход:** существующий `AppApplication.kt` (читается перед правкой)
- **Поведение / Выход:**
  - Добавить import: `import com.tpov.schoolquiz.android.feature.quest.presentation.di.questPresentationModule`
  - Добавить `questPresentationModule` в `modules(...)` после `appShellPresentationModule` (строка ~75 в текущем файле):
    ```
    appShellPresentationModule,
    questPresentationModule,
    ```
  - Порядок: quest зависит от `catalogDomainModule` и `questDomainModule` (если добавлен в фазе-02) — убедиться что они в списке до `questPresentationModule`
- **Edge cases:**
  - Если `questDomainModule` ещё не в `modules(...)` (добавляется в phase-02) → это blocker для runtime DI; phase-05 добавляет только `questPresentationModule`, предполагая что `questDomainModule` уже там (phase-02 precondition)
  - Duplicate module в `startKoin` — Koin бросит exception. Проверить перед добавлением
- **Depends on:** task #1 (settings.gradle.kts), task #2 (build.gradle.kts), `QuestPresentationModule.kt` (frontend-dev task #11)
- **Canonical reference:** `06-api-contract.md` §12 QuestPresentationModule
- **Rationale:** без регистрации в `startKoin` Koin граф не знает о `QuestPresentationModule` и любой `get<MyQuestsComponent>()` падает с DefinitionNotFoundException

---

## Handoff Notes

**OQ-TEST-1 (decompose-testutils):**
Arkivanov Decompose публикует `decompose-testutils` как отдельный artifact в той же группе: `com.arkivanov.decompose:decompose-testutils:3.1.0`. Проверить на Maven Central: https://mvnrepository.com/artifact/com.arkivanov.decompose.
Если artifact не найден — test-dev может использовать `DefaultComponentContext(LifecycleRegistry())` из основного `decompose` artifact без testutils. В `build.gradle.kts` тогда нужна только `testImplementation(libs.decompose)` (или ничего дополнительного — если `decompose` уже в `implementation`).

**Порядок модулей в modules(...):**
Текущий порядок в `AppApplication.kt` (строки 71-79): `persistenceModule`, `firebaseModule`, `firebaseCatalogModule`, `appShellDataModule`, `appShellPresentationModule`, `catalogDataModule`, `catalogDomainModule`, `syncModule`.
После phase-02 добавляются: `questDataModule`, `questDomainModule`, `firebaseQuestModule` (и аналоги для section/theme/lesson/question).
`questPresentationModule` добавляется ПОСЛЕ всех domain/data модулей которые он потребляет.

**Scaffold file ownership:**
Backend-dev владеет всеми `.kts` и `settings.gradle.kts` изменениями в этой фазе. Frontend-dev создаёт `QuestPresentationModule.kt` с Koin DSL; backend-dev только регистрирует его в `AppApplication.kt`.
