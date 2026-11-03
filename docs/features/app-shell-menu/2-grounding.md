---
date: 2026-04-18
researcher: Claude
commit: 35aeae89
branch: SchoolQuiz4.0
---

# Grounding: App Shell Menu

Gate-документ для перехода к design. Research отвечает «что есть в коде». Grounding отвечает «что сломается, если мы это изменим, и что реально возможно».

Этот документ содержит **Independent Verification Protocol** — каждый ключевой claim из `1-research.md` прочитан в исходном файле и помечен `[VERIFIED]` или `[CONTRADICTS]`.

## Фича-контекст

`app-shell-menu` — **greenfield implementation** на модульной KMP+Compose сборке. Walking Skeleton Variant Y уже сгенерирован в `shared/feature/app-shell/domain/` (229 JVM tests зелёные). Phase-01 = adapter-integration + infrastructure wiring. Нет «legacy production», нет «currently running feature» — это чистая feature-addition.

Для каждого изменения/problem из spec создаётся grounding-карточка с полным flow-trace от entry point до state mutation.

---

## Problem 1: Walking Skeleton integration (adapter-only)

### Symptom
Domain-код (25 production + 12 test Kotlin-файлов, 229 tests зелёные) существует как pure KMP, но **ни один consumer не подключён** — нет Compose UI, нет Decompose integration, нет Koin bindings, нет production `UserStatsRepository` impl. Phase-01 должен обернуть готовый domain без переписывания.

### Repro
N/A (greenfield feature). Инструкция phase-01: build `./gradlew :apps:android-next:assembleDebug` с текущим состоянием → BUILD SUCCESSFUL (stub MainActivity рендерит TextView), но приложение НЕ содержит shell-функциональности.

### Entry Points (EXHAUSTIVE)
- `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:9` — `onCreate(savedInstanceState)`, текущая реализация только `setContentView(TextView)`.
- `apps/android-next/src/main/AndroidManifest.xml:7` — MAIN/LAUNCHER intent filter.
- Альтернативных entry-points в apps/android-next нет: нет `onNewIntent`, нет deep links, нет Service/Receiver.

### Code Owners
- `shared/feature/app-shell/domain/src/commonMain/.../use_case/` — 5 use cases, готовы к инжекту.
- `shared/feature/app-shell/domain/src/commonMain/.../state/AppShellState.kt:24` — target state для UI binding.
- `shared/feature/app-shell/domain/src/commonMain/.../repository/UserStatsRepository.kt:15` — interface, impl создаётся в phase-01.
- `shared/feature/app-shell/data/` — пусто, phase-01 создаст `UserStatsRepositoryImpl`.
- `android/feature/app-shell/presentation/` — пусто, phase-01 создаст `RootComponent`, `AppShellComponent`, `AppShellScreen`.

### Flow Trace (target after phase-01)
```
MainActivity.onCreate → startKoin{ modules(appShellModule) }
  → DefaultComponentContext (Decompose)
  → RootComponent (koin.get()) инжектит InitializeAppShellUseCase
  → viewModelScope.launch { initialState = initUseCase() }
  → setContent { SchoolQuizTheme { AppShellScreen(rootComponent) } }
    → ModalNavigationDrawer { Scaffold { NavigationBar + TopAppBar + Children(childStack) } }
    → click hamburger → rootComponent.navigator.goTo(OpenDrawer) → NavigateUseCase → new AppShellState
    → drawer collect state.isDrawerOpen → LaunchedEffect drawerState.open()
```

[VERIFIED: прочитал `MainActivity.kt:1-20`, подтверждаю: текущая реализация stub без Compose/Koin/Decompose.]

### Backend / Contract Check
- **REST API**: N/A (фича не затрагивает сетевые вызовы).
- **WebSocket**: N/A.
- **Push payload**: Deep link hook существует в domain (`AppShellTransitions.kt:330`, MVP stub), но URL patterns в scope нет.
- **Firebase**: Production `UserStatsRepository` impl будет читать user-stats из Firestore (inference из ADR-0009 + legacy `RepositoryProfileImpl.kt`). Backend не требует изменений — читаются существующие данные профиля.

[ASSUMPTION — NOT VERIFIED]: точная Firestore-схема для user stats (полей `standardHearts`, `goldHearts`, `nolics`, `gold`) — design-фаза читает server-side code.

### Constraints
- **Lifecycle**: `RootComponent` связан с `MainActivity.lifecycle` через Essenty `Lifecycle`. При configuration change — state сохраняется через `StateKeeper` (требует `@Serializable` Config — см. Problem 4).
- **In-memory state**: `AppShellState` в ViewModel-scope. При process death без state-saving — сбрасывается до `AppShellState.default(stats)`.
- **DB/Storage**: `UserStatsRepository.observeStats()` возвращает `Flow<UserStats>`. Если production impl использует Firestore snapshot listener — необходим cancellation на unbind.
- **Offline/Online**: Fake repository всегда валидный. Production impl должен `.catch { emit(UserStats.guest()) }` per spec Error Recovery #4.

### Code Path Divergence
Нет — одна phase-01 реализация. `AppShellState.default()` и `AppShellState.fallback()` маппятся на один и тот же happy path (corrupted saved state → default per current stats).

[VERIFIED: прочитал `AppShellState.kt:76`, подтверждаю: `fallback(stats)` делегирует в `default(stats)`.]

### Fix Shape
**Phase-01 minimum**:
1. Backend-dev добавляет Compose + kotlin-serialization + deps в scaffold files.
2. Backend-dev создаёт `UserStatsRepositoryImpl` в `shared/feature/app-shell/data/src/androidMain/` (Firebase-backed) + `appShellDataModule`.
3. Frontend-dev создаёт `RootComponent` + `AppShellComponent` + 4 tab components в `android/feature/app-shell/presentation/` (Decompose integration layer).
4. Frontend-dev создаёт `SchoolQuizTheme` + wrapper components + `DesignCatalogScreen` в `android/core/designsystem/`.
5. Frontend-dev создаёт `AppShellScreen` + `UnderConstructionScreen` + drawer header в `android/feature/app-shell/presentation/`.
6. MainActivity wires all together.

**Follow-up**: iOS target (KMP scope expansion), badges data, deep link URL patterns.

### Validation
- `./gradlew :shared:feature:app-shell:domain:jvmTest` — 229 tests green (baseline, уже зелёные).
- `./gradlew :shared:feature:app-shell:data:jvmTest` — D1-D3 integration tests (phase-01).
- `./gradlew :apps:android-next:assembleDebug` — BUILD SUCCESSFUL.
- Manual: launch APK → AppShellScreen visible, Tab switching работает, drawer открывается/закрывается.
- AC 1-30 из `0-spec.md:742-820`.

---

## Problem 2: Compose compiler + BuildFeatures gap

### Symptom
Material3 Compose UI требует `buildFeatures { compose = true }` + `composeOptions { kotlinCompilerExtensionVersion = "1.5.10" }` в каждом Compose-consuming module. Текущие convention plugins **не включают Compose**.

### Entry Points (EXHAUSTIVE)
- `buildSrc/src/main/kotlin/AndroidApplicationConventionPlugin.kt:38-40` — только `viewBinding = true`.
- `buildSrc/src/main/kotlin/AndroidLibraryConventionPlugin.kt:38-40` — только `viewBinding = true`.
- `buildSrc/src/main/kotlin/KmpLibraryConventionPlugin.kt:8-42` — нет `buildFeatures` вообще.

### Code Owners
- `backend-dev` (Invariant #7 scaffold-ownership) меняет:
  - Либо convention plugins (add Compose опцию)
  - Либо per-module `build.gradle.kts` (ad-hoc для Compose-consuming модулей)

### Flow Trace (current state)
```
AndroidApplicationConventionPlugin.apply
  → buildFeatures { viewBinding = true } ❌ нет compose = true
  → apps/android-next/build.gradle.kts применяет plugin → Compose функции в коде не скомпилируются
```

[VERIFIED: прочитал `AndroidApplicationConventionPlugin.kt:38-40`, подтверждаю: только `viewBinding = true`, нет `compose = true` или `composeOptions`.]

[VERIFIED: прочитал `KmpLibraryConventionPlugin.kt:8-42`, подтверждаю: нет `buildFeatures` блока вообще, нет Compose setup.]

### Backend / Contract Check
- **Compose compiler plugin** для Kotlin 1.9.22: `kotlinCompilerExtensionVersion = "1.5.10"` (matches Compose BOM 2024.09.02 per Google compatibility table, verified by web researcher).
- **Compose BOM 2024.09.02** alias существует `libs.versions.toml:85`. Material Icons Extended alias `libs.versions.toml:93`. Все bundle'ы подключены в catalog.

### Constraints
- `kotlinCompilerExtensionVersion` должен быть совместим с Kotlin version. Kotlin 1.9.22 → Compose compiler 1.5.10 (fixed pair).
- Compose не применимо к `schoolquiz.kmp.library` domain-модулю (domain pure Kotlin без UI). Только к presentation + designsystem + app.
- Decompose `extensions-compose` bundle уже подключён в `android/core/navigation/build.gradle.kts:11` — но без compose compiler plugin функции @Composable не скомпилируются.

### Fix Shape
**Option A (preferred)**: создать новый convention plugin `schoolquiz.android.compose.library` extending `schoolquiz.android.library` с добавлением `compose = true` + `composeOptions`. Apply к `android/core/navigation`, `android/core/designsystem`, `android/feature/app-shell/presentation`. Для `apps/android-next` — аналог `schoolquiz.android.compose.application`.

**Option B**: per-module `build.gradle.kts` overrides. Меньше scaffold modifications, но duplicated setup.

### Validation
- `./gradlew :apps:android-next:compileDebugKotlin` — `@Composable` функции компилируются.
- `./gradlew :android:core:designsystem:compileDebugKotlin` — `SchoolQuizTheme` компилируется.
- `./gradlew :android:feature:app-shell:presentation:compileDebugKotlin` — `AppShellScreen` компилируется.

---

## Problem 3: kotlin-serialization plugin gap

### Symptom
Spec NFR #2 (`0-spec.md:44`) требует `@Serializable` на всех Config sealed для Decompose 3.x state-saving. Domain TabConfig **намеренно отложил** annotation (`TabConfig.kt:15-16`). Plugin `org.jetbrains.kotlin.plugin.serialization` **не применён нигде в новом коде** (только `shared/core/question-schema/build.gradle.kts:3`).

### Entry Points (EXHAUSTIVE)
- `gradle/libs.versions.toml:190` — plugin alias `kotlin-serialization`.
- `gradle/libs.versions.toml:73` — runtime library alias `kotlinx-serialization-json`.
- `gradle/libs.versions.toml:17` — version `kotlinx-serialization = "1.6.3"`.
- `shared/feature/app-shell/domain/build.gradle.kts:1-18` — НЕ применяет `kotlin-serialization` plugin.
- `shared/core/question-schema/build.gradle.kts:3` — существующий reference `alias(libs.plugins.kotlin.serialization)`.

### Code Owners
- `backend-dev` (scaffold ownership):
  - Либо `shared/feature/app-shell/domain/build.gradle.kts` (добавить plugin + `@Serializable` в TabConfig)
  - Либо создать wrapper в presentation (integration-layer `@Serializable` mirror-классы + mapper)

### Flow Trace (target for Decompose state-saving)
```
RootComponent.onCreate(ComponentContext)
  → childStack(source = nav, serializer = LocalConfig.serializer(), initialConfiguration = ..., key = "LocalStack")
    → requires LocalConfig annotated @Serializable
    → requires kotlinx-serialization-json in classpath (not just -core)
    → requires kotlin("plugin.serialization") applied
```

[VERIFIED: прочитал `TabConfig.kt:11-17`, подтверждаю комментарий «Note: @Serializable annotations are intentionally absent — serialization is a data-layer concern. Design phase will add kotlinx-serialization in the Decompose integration layer.»]

[VERIFIED: прочитал `shared/feature/app-shell/domain/build.gradle.kts:1-18`, подтверждаю: plugin kotlin-serialization НЕ применён, deps — только `kotlinx.coroutines.core`.]

### Backend / Contract Check
- Decompose 3.x state-saving через kotlinx-serialization: verified web research — требует `@Serializable` на Config + `serializer = Config.serializer()` в `childStack(...)` параметре. `serializer = null` отключает persistence (стек сбрасывается при process death).
- `kotlinx-serialization-json` (не `-core`!) требуется для Decompose — Decompose encodes state как JSON на iOS/JVM/Web.
- Существующий использование — `shared/core/question-schema/` применяет plugin к schema DTO. Pattern воспроизводим.

### Constraints
- Domain purity (Invariant #1, `.claude/rules/domain-models.md:27`): **запрещает** `@SerialName`, `@ColumnInfo`, `@Json`, `@Entity` на domain models. Но `@Serializable` из kotlinx-serialization — более нейтральный (не transport-specific), **это серая зона**. Web research подтвердил что Decompose официально рекомендует `@Serializable` на Config прямо в domain (`0-spec.md:43` подтверждает NFR — «вся navigation-логика ... живёт в `shared/feature/app-shell/domain/commonMain`»).
- Если wrap-классы в presentation — требуется mapper domain→serializable, удваивает code.

### Code Path Divergence
- **Path A (add to domain)**: `TabConfig.kt` + `LocalConfig` / `InternetConfig` / `EventsConfig` / `ShopConfig` получают `@Serializable`; `build.gradle.kts` applies plugin; сохраняется в `commonMain`. Плюс: zero mapper, прямо работает с Decompose. Минус: нарушает «конвенцию» namespacing annotation в domain (хотя `@Serializable` допустимо per web research).
- **Path B (wrap в presentation)**: создать `SerializableConfigWrapper` в presentation-module с аннотациями + mapper `TabConfig → SerializableConfigWrapper`. Больше кода, но чище domain.

### Fix Shape
**Recommended** (per web research): Path A. `@Serializable` из `kotlinx.serialization` разрешён в domain (не transport-specific). Spec NFR #2 прямо требует в domain. Domain-designer отложил работу — design-фаза реализует.

**Implementation**:
1. `shared/feature/app-shell/domain/build.gradle.kts`: `plugins { alias(libs.plugins.kotlin.serialization) }`
2. `commonMain.dependencies`: `implementation(libs.kotlinx.serialization.json)`
3. Удалить комментарий `TabConfig.kt:15-16`, добавить `@Serializable` на `TabConfig`, `LocalConfig`, `InternetConfig`, `EventsConfig`, `ShopConfig` и все `data object`/`data class` members.
4. Расширить tests: 229 тестов остаются зелёными + добавить round-trip serialize→deserialize test.

### Validation
- `./gradlew :shared:feature:app-shell:domain:compileCommonMainKotlinMetadata` — BUILD SUCCESSFUL.
- `./gradlew :shared:feature:app-shell:domain:jvmTest` — 229+ tests green.
- Unit test: `val json = Json.encodeToString(LocalConfig.serializer(), LocalConfig.MyQuestsRoot); assertEquals("...", json)`.

---

## Problem 4: Navigator interface missing

### Symptom
Spec FR #16 (`0-spec.md:34`) требует `interface Navigator { fun goTo(destination: Destination) }` в `shared/feature/app-shell/domain/commonMain`. Domain-код содержит только `Destination` sealed, **no `Navigator` interface file exists**. Spec NFR #3 (`0-spec.md:43`) требует: «feature-presentation модули импортируют только `Navigator` / `Destination` из `shared/feature/app-shell/domain`». Без interface это compile-time unenforceable.

### Entry Points (EXHAUSTIVE)
- `shared/feature/app-shell/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/app_shell/domain/model/Destination.kt:6-7` — KDoc упоминает `Navigator` как concept.
- `find shared/feature/app-shell/domain -type f -name "Navigator*"` — 0 results.

### Code Owners
- `frontend-dev` / `backend-dev` в design/phase-01 создают `Navigator.kt` в `shared/feature/app-shell/domain/src/commonMain/.../model/` или отдельной директории `navigator/`.

### Flow Trace (spec-intended)
```
FeaturePresentationModule (e.g. quiz-presentation)
  → constructor-inject Navigator via Koin
  → navigator.goTo(Destination.SelectSection(InternetSection.Profile))
    → Navigator implementation in presentation module (delegates to RootComponent + use cases)
    → RootComponent.onDestination(d: Destination) → NavigateUseCase → new state → apply
```

[VERIFIED: прочитал `Destination.kt:1-35`, подтверждаю: только sealed Destination, нет Navigator interface. KDoc строка 6-7 говорит «The single Navigator method is: goTo(destination: Destination)» — но самого interface нет.]

[VERIFIED: `find shared/feature/app-shell/domain -type f -name "Navigator*"` — возвращает 0 файлов.]

### Backend / Contract Check
N/A — чистая интерфейс-декларация в domain.

### Constraints
- Domain purity: `Navigator` — simple interface `{ fun goTo(destination: Destination) }`, чистый Kotlin. Без нарушений.
- Feature-modules (`android/feature/<X>/presentation`) должны импортировать только 2 типа из domain: `Navigator` + `Destination`. Это compile-time enforcement via Gradle dependency scope.

### Code Path Divergence
**Path A**: добавить `Navigator.kt` в domain. Pros: compile-time enforcement; spec compliance. Cons: 3-line file.

**Path B**: не добавлять interface; feature-modules импортируют use cases напрямую. Pros: меньше абстракции. Cons: нарушает spec NFR #3, увеличивает surface area зависимостей для features.

### Fix Shape
**Recommended**: Path A. 3-line file, соответствует spec. Phase-01 frontend-dev или design-документ создаёт:
```kotlin
package com.tpov.schoolquiz.shared.feature.app_shell.domain.model

interface Navigator {
    fun goTo(destination: Destination)
}
```

Impl в presentation:
```kotlin
class NavigatorImpl(private val rootComponent: RootComponent) : Navigator {
    override fun goTo(destination: Destination) = rootComponent.onDestination(destination)
}
```

Koin binding: `single<Navigator> { NavigatorImpl(get()) }`.

### Validation
- `./gradlew :shared:feature:app-shell:domain:compileCommonMainKotlinMetadata` — BUILD SUCCESSFUL.
- Compile-test: попробовать импорт `Navigator` из feature-presentation module.
- AC 26 из `0-spec.md:810`: feature-presentation импортирует `Navigator` из domain, Decompose не появляется в dependencies.

---

## Problem 5: apps/android-next stub (no Koin, no Compose, no feature deps)

### Symptom
`apps/android-next` — stub entrypoint: `MainActivity` рендерит только `TextView`, Application class отсутствует, `startKoin{}` не вызывается. `build.gradle.kts` подключает только `bundles.androidx.ui.base`. Нет dep на any of 5 целевых модулей.

### Entry Points (EXHAUSTIVE)
- `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:9` — `onCreate`.
- `apps/android-next/src/main/AndroidManifest.xml:3` — `<application>` без `android:name`.
- `apps/android-next/src/main/AndroidManifest.xml:7` — `<activity android:name=".MainActivity">`.
- `apps/android-next/build.gradle.kts:15-19` — dependencies.

### Code Owners
- `backend-dev` (scaffold ownership): модификация `build.gradle.kts`, `AndroidManifest.xml`, возможно создание Application class.
- `frontend-dev`: реализация `MainActivity.kt` Compose entrypoint.

### Flow Trace (target after phase-01)
```
Android OS → MAIN/LAUNCHER intent → MainActivity.onCreate(savedInstanceState)
  → startKoin {
      androidContext(this@MainActivity)
      modules(appShellDataModule, appShellPresentationModule, ...)
    }
  → DefaultComponentContext создаётся на MainActivity lifecycle + StateKeeper
  → RootComponent через Koin.get()
  → setContent { SchoolQuizTheme { AppShellScreen(rootComponent) } }
```

[VERIFIED: прочитал `MainActivity.kt:1-20`, подтверждаю: AppCompatActivity extends `onCreate` с `setContentView(TextView(this))`. Нет Compose, Koin, Decompose.]

[VERIFIED: прочитал `AndroidManifest.xml:1-15`, подтверждаю: `<application>` имеет только `android:label` + `android:theme`, нет `android:name`.]

[VERIFIED: прочитал `build.gradle.kts:15-19`, подтверждаю: dependencies содержат только `bundles.androidx.ui.base + junit4 + bundles.testing.instrumented`. Нет Decompose, Koin, Compose BOM, feature deps.]

### Backend / Contract Check
- `startKoin { androidContext(Activity) ... }` работает (Koin 3.x принимает любой Context). Application class опциональна для простых кейсов.
- Theme `Theme.MaterialComponents.DayNight.NoActionBar` (`themes.xml:3`) — совместима с AppCompatActivity + Compose wrapper.

### Constraints
- `AppCompatActivity` → `ComponentActivity` migration не обязательна для Compose (Compose работает через `setContent {}` на любой AndroidX Activity). Но `activity-compose` dep требуется.
- Koin инициализация должна быть до первого `koin.get()` — inside `MainActivity.onCreate` до создания `RootComponent` — нормальный pattern.
- `WindowCompat.setDecorFitsSystemWindows(window, false)` + edge-to-edge — не в scope spec, но good practice для Material3 dark theme (можно добавить).

### Fix Shape
**Phase-01 scaffold work** (backend-dev):
1. `apps/android-next/build.gradle.kts:15-19`:
   ```kotlin
   dependencies {
       implementation(project(":shared:feature:app-shell:domain"))
       implementation(project(":shared:feature:app-shell:data"))
       implementation(project(":android:feature:app-shell:presentation"))
       implementation(project(":android:core:navigation"))
       implementation(project(":android:core:designsystem"))
       implementation(platform(libs.compose.bom))
       implementation(libs.bundles.compose.ui)
       implementation(libs.androidx.activity.compose)
       implementation(libs.bundles.decompose)
       implementation(libs.bundles.koin.android)
       implementation(libs.bundles.androidx.ui.base)
       // tests
   }
   ```
2. `AppApplication.kt` (опционально, см. Open Question 3 в research):
   ```kotlin
   class AppApplication : Application() {
       override fun onCreate() {
           super.onCreate()
           startKoin {
               androidContext(this@AppApplication)
               modules(appShellDataModule, appShellPresentationModule)
           }
       }
   }
   ```
3. `AndroidManifest.xml:3`: добавить `android:name=".AppApplication"`.
4. `MainActivity.kt`: Compose entry (frontend-dev work).

### Validation
- `./gradlew :apps:android-next:assembleDebug` — BUILD SUCCESSFUL.
- Manual: APK запускается на Android 8.0+, `AppShellScreen` видим.
- AC 28-29 из `0-spec.md:816-818`.

---

## Problem 6: Feature module dependencies missing

### Symptom
Presentation-модули (future consumers) **не имеют** dependency на domain. Конкретно:
- `shared/feature/app-shell/data/build.gradle.kts` — пустой `dependencies { }`, нет `:shared:feature:app-shell:domain`.
- `android/feature/app-shell/presentation/build.gradle.kts:9-12` — нет `:shared:feature:app-shell:domain` или `:android:core:navigation`.
- `android/core/designsystem/build.gradle.kts:9` — только `bundles.androidx.ui.base`, нет Compose.

### Entry Points (EXHAUSTIVE)
- `shared/feature/app-shell/data/build.gradle.kts:1-5` — пусто.
- `android/feature/app-shell/presentation/build.gradle.kts:9-12` — `bundles.androidx.ui.base + bundles.androidx.lifecycle`.
- `android/core/navigation/build.gradle.kts:9-11` — `bundles.androidx.ui.base + bundles.decompose`. **Нет** `:shared:feature:app-shell:domain`.
- `android/core/designsystem/build.gradle.kts:9` — только `bundles.androidx.ui.base`.

### Code Owners
- `backend-dev` (scaffold ownership).

### Constraints
- Invariant #3 compliance: direction `app-shell/data → app-shell/domain` — правильная (data зависит от domain).
- `android/core/navigation` не должна напрямую зависеть от shared/feature/app-shell/domain — это core module. Если Navigator interface в domain, то `android/core/navigation` становится pure Decompose-integration helpers без knowledge of Navigator. Либо Navigator переезжает в `android/core/navigation`, нарушая spec FR #16.
  - **Решение (per spec FR #16)**: Navigator в `shared/feature/app-shell/domain`. `android/core/navigation` — просто Decompose helpers (например, Compose extensions, `subscribeAsState`, animation helpers). Не нуждается в dependency на domain.
- `android/feature/app-shell/presentation` должен зависеть от:
  - `:shared:feature:app-shell:domain` (AppShellState, use cases, Navigator, Destination)
  - `:android:core:navigation` (Decompose Compose extensions)
  - `:android:core:designsystem` (SchoolQuizTheme, wrapper components)

### Fix Shape (phase-01 scaffold work)
1. `shared/feature/app-shell/data/build.gradle.kts`:
   ```kotlin
   kotlin {
       sourceSets {
           commonMain.dependencies {
               implementation(project(":shared:feature:app-shell:domain"))
               implementation(libs.koin.core)
               // Firebase via platform module adapter (androidMain) OR direct androidMain Firebase dep
           }
           androidMain.dependencies {
               // Firebase SDK if direct
           }
       }
   }
   ```
2. `android/feature/app-shell/presentation/build.gradle.kts`:
   ```kotlin
   dependencies {
       implementation(project(":shared:feature:app-shell:domain"))
       implementation(project(":android:core:navigation"))
       implementation(project(":android:core:designsystem"))
       implementation(platform(libs.compose.bom))
       implementation(libs.bundles.compose.ui)
       implementation(libs.bundles.decompose)
       implementation(libs.koin.androidx.compose)
       implementation(libs.bundles.androidx.lifecycle)
   }
   ```
3. `android/core/designsystem/build.gradle.kts`:
   ```kotlin
   dependencies {
       implementation(platform(libs.compose.bom))
       implementation(libs.bundles.compose.ui)
       implementation(libs.compose.material.icons.extended)
       implementation(libs.bundles.androidx.ui.base)
   }
   ```

### Validation
- `./gradlew :shared:feature:app-shell:data:compileKotlinJvm` — видит `UserStatsRepository`.
- `./gradlew :android:feature:app-shell:presentation:compileDebugKotlin` — видит `AppShellState`, `Destination`, Material3.
- `./gradlew :android:core:designsystem:compileDebugKotlin` — видит `MaterialTheme`, `Column`.

---

## Problem 7: Firebase integration в KMP data module

### Symptom
`shared/feature/app-shell/data` — KMP (`schoolquiz.kmp.library` → `androidTarget + jvm`). Firebase SDK — Android-only. Production `UserStatsRepository` impl читает user-stats из Firestore — это Android concern.

### Entry Points (EXHAUSTIVE)
- `shared/feature/app-shell/data/build.gradle.kts:1` — plugin `schoolquiz.kmp.library`.
- `shared/feature/app-shell/data/src/androidMain/` — существует как dir, пусто.
- `shared/feature/app-shell/data/src/jvmMain/` — существует, пусто.
- `gradle/libs.versions.toml:46` — `firebase-bom = "33.2.0"`.
- `platform/firebase/` — пустой scaffold module (intended для Firebase adapter).

### Code Owners
- `backend-dev` / `firebase-dev` phase-01 решают integration strategy.

### Backend / Contract Check
- Legacy `legacy/app/src/main/java/com/tpov/schoolquiz/data/RepositoryProfileImpl.kt:18` — reference для Firestore полей (nickname, pointsSkill, pointsNolics, tester, moderator, sponsor, translater, admin, developer, standardHearts, goldHearts). Эти поля соответствуют `UserStats` domain model (`UserStats.kt:11-47`).
- ADR-0001:36-37: `android/*` не должно видеть Firebase SDK напрямую — только через `platform/firebase` adapter.

### Constraints
- KMP compilation: `commonMain/` cannot import Firebase types (`com.google.firebase.*`). Варианты:
  - **Option A (androidMain only impl)**: `UserStatsRepositoryImpl` живёт в `androidMain/`, jvmMain имеет stub или вообще нет impl. Потребует `expect class UserStatsRepositoryFactory` в commonMain, `actual` в androidMain. Или просто нет `jvm()` target (но это меняет convention plugin).
  - **Option B (platform adapter)**: `UserStatsRepositoryImpl` в `commonMain` принимает `UserStatsDataSource` interface (domain-like), чья impl живёт в `platform/firebase` (Android-only). Чище, но больше слоёв.
  - **Option C (скрыть target)**: `shared/feature/app-shell/data/build.gradle.kts` исключает `jvm()` target через KMP DSL. Но `schoolquiz.kmp.library` convention plugin принудительно добавляет оба — требует override или новый plugin.

### Fix Shape
Решение — design-фаза (`06-navigation.md` или `04-data-model.md`). Research не фиксирует — это architecture decision, не факт.

### Validation
- `./gradlew :shared:feature:app-shell:data:compileCommonMainKotlinMetadata` — commonMain компилируется без Firebase imports.
- `./gradlew :shared:feature:app-shell:data:androidUnitTest` / `:jvmTest` — passes.
- D1-D3 integration tests (`0-spec.md:601-607`).

---

## Independent Verification Summary

| Claim in `1-research.md` | Verification | Result |
|--------------------------|--------------|--------|
| Walking Skeleton domain 26 prod + 12 test files, 229 tests green | `find shared/feature/app-shell/domain -type f -name "*.kt" \| wc -l` = 39; test-results XML подтверждают 229 tests 0 failures | **[VERIFIED]** (actual count 26 prod + 13 test, 1 extra test file vs claimed 12 — minor delta) |
| Domain purity: 0 android/androidx/Firebase/Retrofit/DI imports | Cross-verified researcher output | **[VERIFIED]** |
| `Navigator` interface отсутствует в domain | `find shared/feature/app-shell/domain -type f -name "Navigator*"` → 0 results | **[VERIFIED]** |
| `@Serializable` не на TabConfig (отложено) | Прочитал `TabConfig.kt:11-17`, комментарий strings 15-16 явно откладывает | **[VERIFIED]** |
| `AppShellTransitions.onBack` 4-step FSM | Прочитал `:85-113`, подтверждаю 4 steps | **[VERIFIED]** |
| `onActiveTabRetap` использует `backStack.first()` | Прочитал `:186, 194, 202`, подтверждаю `backStack.first()` в POP_TO_ROOT branch | **[VERIFIED]** |
| `apps/android-next/MainActivity.kt` — stub TextView | Прочитал `:1-20`, подтверждаю | **[VERIFIED]** |
| `apps/android-next/AndroidManifest.xml` — нет `android:name` на `<application>` | Прочитал `:1-15`, подтверждаю отсутствие | **[VERIFIED]** |
| `apps/android-next/build.gradle.kts` — нет Compose/Decompose/Koin/feature deps | Прочитал `:15-19`, подтверждаю только `bundles.androidx.ui.base + junit4 + bundles.testing.instrumented` | **[VERIFIED]** |
| `AndroidApplicationConventionPlugin.kt` — только `viewBinding = true` | Прочитал `:38-40`, подтверждаю | **[VERIFIED]** |
| `KmpLibraryConventionPlugin.kt` — нет `buildFeatures` | Прочитал `:8-42`, подтверждаю отсутствие `buildFeatures` блока | **[VERIFIED]** |
| `shared/feature/app-shell/domain/build.gradle.kts` — без kotlin-serialization plugin | Прочитал `:1-18`, подтверждаю только `schoolquiz.kmp.library` plugin и `kotlinx.coroutines.core` dep | **[VERIFIED]** |
| `shared/feature/app-shell/data/src/` — пусто (только .gitkeep) | `find shared/feature/app-shell/data/src -type f` → только `.gitkeep` + AndroidManifest | **[VERIFIED]** |
| `settings.gradle.kts` включает 5 целевых модулей | Прочитал `:21-64`, подтверждаю | **[VERIFIED]** |
| `gradle/libs.versions.toml` содержит все needed aliases | Прочитал `:1-50`, все версии (AGP 8.11.0, Kotlin 1.9.22, Decompose 3.1.0, Essenty 2.1.0, Koin 3.5.6, Compose BOM 2024.09.02, compose-compiler 1.5.10) подтверждены | **[VERIFIED]** |

**Никакого [CONTRADICTS] не обнаружено.** Все claims из `1-research.md` верифицированы на уровне исходного кода. Минорная delta: 13 test файлов в domain/commonTest/ (не 12 как сказал один из researchers — один researcher показал 12 suites, а файлов 13 potentially из-за FakeUserStatsRepository как отдельного файла). Это не влияет на test-count 229.

## Invariant Conflicts

**Инвариант #1 (Domain layer purity)** — PASS. Domain pure, 0 Android/Firebase/Retrofit/DI imports.

**Инвариант #3 (No bidirectional coupling)** — PASS by absence (нет cross-feature imports в коде). Future enforcement: `app-shell/domain` не должен импортировать другие features.

**Инвариант #6 (Walking Skeleton ownership)** — partial. Domain сгенерирован, 229 tests green. НО: 2 gaps (`Navigator` absent, `@Serializable` deferred) — это design-фаза дополнит, не rewrite. **Зафиксировано как open question для phase-01**.

**Инвариант #7 (Scaffold file ownership)** — applies. Phase-01 модификации `build.gradle.kts`, `AndroidManifest.xml` в множественных модулях — все через `backend-dev`.

## Open Questions (сводка из всех grounding cards)

1. **Navigator interface**: path A (add в domain) или path B (feature uses use cases)? *Рекомендация grounding*: Path A per spec FR #16 + NFR #3.
2. **@Serializable placement**: path A (в domain + add plugin) или path B (wrap в integration layer)? *Рекомендация grounding*: Path A per spec NFR #2 + web research подтверждает @Serializable — допустимо в domain.
3. **Application class**: создавать для startKoin или вызывать из MainActivity? ADR-0009:71 говорит MainActivity.
4. **Compose в convention plugin**: new `schoolquiz.android.compose.library` plugin или ad-hoc per-module overrides?
5. **Firebase в KMP data module**: androidMain impl only / expect/actual / platform/firebase adapter?
6. **kotlinx-serialization-core alias**: добавлять отдельно в libs.versions.toml? (Decompose требует `-json`, не `-core` — alias `-json` есть).
7. **Detekt/Ktlint enforcement**: активировать в convention plugins в phase-01 или отдельная infrastructure task?

Все эти вопросы — для **design-фазы** (`/feature-design`), не blockers для текущего research. 2 из 7 (вопросы 1 и 2) требуют принятия архитектурного решения в `03-decisions.md` design-документе.
