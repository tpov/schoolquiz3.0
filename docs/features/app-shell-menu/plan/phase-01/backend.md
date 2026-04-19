---
phase: phase-01
role: backend-dev
---

# Phase-01: Backend Tasks

## 0. Prerequisites Check

Перед любыми изменениями прочитать:
- `shared/feature/app-shell/domain/src/commonMain/kotlin/.../use_case/ObserveAppShellStateUseCase.kt` — текущая сигнатура
- `shared/feature/app-shell/domain/src/commonTest/kotlin/.../ObserveAppShellStateUseCaseTest.kt` — 9 тестов
- `settings.gradle.kts` — список включённых модулей
- `buildSrc/src/main/kotlin/AndroidApplicationConventionPlugin.kt` — текущий plugin

## 1. Scaffold: Compose Convention Plugins

### 1a. Создать `buildSrc/src/main/kotlin/AndroidComposeLibraryConventionPlugin.kt`

```kotlin
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

/**
 * Convention plugin for Android library modules consuming Jetpack Compose.
 * Extends AndroidLibraryConventionPlugin behavior with Compose compiler setup.
 * Apply to: android/core/designsystem, android/core/navigation,
 *           android/feature/app-shell/presentation
 */
class AndroidComposeLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("schoolquiz.android.library")

            extensions.configure<LibraryExtension> {
                buildFeatures {
                    compose = true
                }
                composeOptions {
                    // Kotlin 1.9.22 → Compose compiler 1.5.10 (fixed pair per compatibility table)
                    kotlinCompilerExtensionVersion = "1.5.10"
                }
            }
        }
    }
}
```

### 1b. Создать `buildSrc/src/main/kotlin/AndroidComposeApplicationConventionPlugin.kt`

```kotlin
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention plugin for Android application modules consuming Jetpack Compose.
 * Apply to: apps/android-next
 */
class AndroidComposeApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("schoolquiz.android.application")

            extensions.configure<ApplicationExtension> {
                buildFeatures {
                    compose = true
                }
                composeOptions {
                    kotlinCompilerExtensionVersion = "1.5.10"
                }
            }
        }
    }
}
```

### 1c. Зарегистрировать в `buildSrc/build.gradle.kts`

Добавить в блок `gradlePlugin.plugins`:
```kotlin
register("androidComposeLibrary") {
    id = "schoolquiz.android.compose.library"
    implementationClass = "AndroidComposeLibraryConventionPlugin"
}
register("androidComposeApplication") {
    id = "schoolquiz.android.compose.application"
    implementationClass = "AndroidComposeApplicationConventionPlugin"
}
```

## 2. settings.gradle.kts — добавить shared:core:stats

```kotlin
include(":shared:core:stats")
```

Добавить после существующих `shared:core:*` includes. Соблюдать алфавитный порядок (после `preferences`, перед `sync`).

## 3. Создать shared/core/stats модуль

### 3a. Создать директорию и build.gradle.kts

`shared/core/stats/build.gradle.kts`:
```kotlin
plugins {
    id("schoolquiz.kmp.library")
}

android {
    namespace = "com.tpov.schoolquiz.shared.core.stats"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
```

Создать source sets:
- `shared/core/stats/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/stats/`

### 3b. `UserStatsDataSource.kt`

```kotlin
package com.tpov.schoolquiz.shared.core.stats

import kotlinx.coroutines.flow.Flow

/**
 * Platform-neutral data source interface for raw user stats.
 *
 * Lives in shared/core/stats (not shared/feature/app-shell/data) to allow
 * platform/firebase to implement it without creating feature→platform coupling.
 * Resolves OQ-COMP-5 per ADR-0011 recommendation.
 *
 * Implementation: FirebaseUserStatsDataSource (platform/firebase, androidMain)
 */
interface UserStatsDataSource {
    /**
     * Cold flow of raw stats. First emission = current persisted state.
     * Emits on every Firestore snapshot update.
     */
    fun observeRaw(): Flow<RawUserStats>

    /**
     * One-shot fetch of current stats. Used during cold start / process death recovery.
     */
    suspend fun fetchRaw(): RawUserStats
}
```

### 3c. `RawUserStats.kt`

```kotlin
package com.tpov.schoolquiz.shared.core.stats

/**
 * Platform-neutral raw representation of user stats from Firestore.
 * Mapped to UserStats (domain) by UserStatsRepositoryImpl.
 *
 * All fields correspond to Firestore document fields under users/{uid}.
 * Reference: legacy/app/src/main/java/com/tpov/schoolquiz/data/RepositoryProfileImpl.kt:18
 */
data class RawUserStats(
    val nickname: String = "",
    val avatarUrl: String? = null,
    val hasPremium: Boolean = false,
    val streakDays: Int = 0,
    val stars: Long = 0L,
    val nolics: Long = 0L,
    val standardHearts: Int = 0,
    val goldHearts: Int = 0,
    val gold: Long = 0L,
    val currentSkill: Int = 0,
    val testerLevel: Int = 0,
    val moderatorLevel: Int = 0,
    val sponsorLevel: Int = 0,
    val translatorLevel: Int = 0,
    val adminLevel: Int = 0,
    val developerLevel: Int = 0,
)
```

## 4. Domain delta — ObserveAppShellStateUseCase.kt

**Файл**: `shared/feature/app-shell/domain/src/commonMain/kotlin/.../use_case/ObserveAppShellStateUseCase.kt`

Изменить ТОЛЬКО параметр и тело `invoke`. Всё остальное — KDoc, class declaration, import — не трогать.

Было:
```kotlin
operator fun invoke(initialState: AppShellState): Flow<AppShellState> =
    userStatsRepository.observeStats()
        .map { stats -> initialState.copy(userStats = stats) }
```

Стало:
```kotlin
/**
 * Returns a cold [Flow] that emits an updated [AppShellState] for every new [UserStats]
 * emission. Navigation state is read from [currentStateProvider] at each emission
 * to prevent stale closure (ADR-LEAD-02, ADR-COMP-01).
 *
 * @param currentStateProvider Lambda returning the current navigation state at call time.
 *        Typically `{ _state.value }` from DefaultRootComponent.
 */
operator fun invoke(currentStateProvider: () -> AppShellState): Flow<AppShellState> =
    userStatsRepository.observeStats()
        .map { stats -> currentStateProvider().copy(userStats = stats) }
```

## 5. Domain delta — создать Navigator.kt

**Файл**: `shared/feature/app-shell/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/app_shell/domain/navigation/Navigator.kt`

Создать директорию `navigation/` если не существует.

```kotlin
package com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination

/**
 * Single navigation entry-point for all feature-presentation modules.
 *
 * KMP-pure: no Android, no Decompose in this interface.
 * Feature modules depend only on Navigator + Destination — never on RootComponent directly.
 *
 * Spec FR #16, NFR #3. See ADR-COMP-04, ADR-0011.
 * Implementation: NavigatorImpl in android/feature/app-shell/presentation
 */
interface Navigator {
    fun goTo(destination: Destination)
}
```

## 6. Domain delta — создать RootComponent.kt

**Файл**: `shared/feature/app-shell/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/app_shell/domain/navigation/RootComponent.kt`

```kotlin
package com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DeepLink
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RetapOutcome
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RootEvent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.AppShellState
import kotlinx.coroutines.flow.Flow

/**
 * Domain interface for the root navigation component.
 *
 * Pure Kotlin / coroutines — no Decompose types in this interface.
 * Flow<AppShellState> is kotlinx.coroutines, allowed in domain per domain-models.md.
 *
 * Implementation: DefaultRootComponent in android/feature/app-shell/presentation
 * Spec NFR #1, ADR-0011 (split interface/impl).
 */
interface RootComponent {
    /** Reactive navigation + stats state. Collect via collectAsStateWithLifecycle() in Compose. */
    val appShellState: Flow<AppShellState>

    /** Domain → UI events. Only RootEvent.SystemBack for now. Collected in MainActivity lifecycleScope. */
    val events: Flow<RootEvent>

    /** Primary navigation dispatch. Accepts all Destination variants. */
    fun onDestination(destination: Destination)

    /** Re-tap on currently active tab. Returns POP_TO_ROOT or NO_OP (UI handles scroll). */
    fun onActiveTabRetap(tab: Tab): RetapOutcome

    /** Platform-neutral deep link handler. MVP: stub (no-op). */
    fun onDeepLink(deepLink: DeepLink)
}
```

## 7. shared/feature/app-shell/data/build.gradle.kts

Полная замена:
```kotlin
plugins {
    id("schoolquiz.kmp.library")
}

android {
    namespace = "com.tpov.schoolquiz.shared.feature.app_shell.data"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:feature:app-shell:domain"))
            implementation(project(":shared:core:stats"))
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
```

## 8. UserStatsRepositoryImpl.kt

**Файл**: `shared/feature/app-shell/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/app_shell/data/UserStatsRepositoryImpl.kt`

```kotlin
package com.tpov.schoolquiz.shared.feature.app_shell.data

import com.tpov.schoolquiz.shared.core.stats.RawUserStats
import com.tpov.schoolquiz.shared.core.stats.UserStatsDataSource
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Qualification
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Production implementation of [UserStatsRepository].
 * Delegates to [UserStatsDataSource] and maps raw Firestore data to domain [UserStats].
 *
 * Spec Error Recovery #4: observeStats() always emits (never throws to UI) via .catch.
 */
class UserStatsRepositoryImpl(
    private val dataSource: UserStatsDataSource,
) : UserStatsRepository {

    override fun observeStats(): Flow<UserStats> =
        dataSource.observeRaw()
            .map { raw -> raw.toDomain() }
            .catch { emit(UserStats.guest()) }   // Spec Error Recovery #4

    override suspend fun currentStats(): UserStats =
        runCatching { dataSource.fetchRaw().toDomain() }
            .getOrDefault(UserStats.guest())
}

// Mapper — lives in data layer per domain-models.md mapper chain rule
private fun RawUserStats.toDomain(): UserStats = UserStats(
    nickname = nickname,
    avatarUrl = avatarUrl,
    hasPremium = hasPremium,
    streakDays = streakDays,
    stars = stars,
    nolics = nolics,
    standardHearts = standardHearts,
    goldHearts = goldHearts,
    gold = gold,
    currentSkill = currentSkill,
    qualification = Qualification(
        tester = testerLevel,
        moderator = moderatorLevel,
        sponsor = sponsorLevel,
        translator = translatorLevel,
        admin = adminLevel,
        developer = developerLevel,
    ),
)
```

## 9. AppShellDataModule.kt

**Файл**: `shared/feature/app-shell/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/app_shell/data/di/AppShellDataModule.kt`

```kotlin
package com.tpov.schoolquiz.shared.feature.app_shell.data.di

import com.tpov.schoolquiz.shared.feature.app_shell.data.UserStatsRepositoryImpl
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
import org.koin.dsl.module

/**
 * Koin module for app-shell data layer.
 * Requires: firebaseModule (provides UserStatsDataSource).
 *
 * ADR-0009 Rule 1: one module val per leaf module.
 */
val appShellDataModule = module {
    single<UserStatsRepository> { UserStatsRepositoryImpl(get()) }
}
```

## 10. platform/firebase — FirebaseUserStatsDataSource.kt

### 10a. platform/firebase/build.gradle.kts — добавить shared:core:stats dep

```kotlin
dependencies {
    implementation(project(":shared:core:stats"))
    // existing Firebase deps remain unchanged
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.database.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.appcheck.play.integrity)
    implementation(libs.play.services.base)
    implementation(libs.play.services.basement)
}
```

### 10b. FirebaseUserStatsDataSource.kt

**Файл**: `platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/FirebaseUserStatsDataSource.kt`

```kotlin
package com.tpov.schoolquiz.platform.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.schoolquiz.shared.core.stats.RawUserStats
import com.tpov.schoolquiz.shared.core.stats.UserStatsDataSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed implementation of [UserStatsDataSource].
 * Reads user stats from Firestore collection: users/{uid}.
 *
 * Firebase SDK types are confined to this class — they do NOT leak to data/commonMain.
 * Reference field mapping: legacy/app/src/main/java/.../data/RepositoryProfileImpl.kt:18
 */
class FirebaseUserStatsDataSource(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : UserStatsDataSource {

    private val currentUid: String?
        get() = auth.currentUser?.uid

    override fun observeRaw(): Flow<RawUserStats> = callbackFlow {
        val uid = currentUid ?: run {
            trySend(RawUserStats())   // guest/unauthenticated
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val raw = snapshot?.toRawUserStats() ?: RawUserStats()
                trySend(raw)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun fetchRaw(): RawUserStats {
        val uid = currentUid ?: return RawUserStats()
        val snapshot = firestore.collection("users").document(uid).get().await()
        return snapshot.toRawUserStats() ?: RawUserStats()
    }
}

// Extension — Firebase DocumentSnapshot → RawUserStats. Stays in platform/firebase.
private fun com.google.firebase.firestore.DocumentSnapshot.toRawUserStats(): RawUserStats? {
    if (!exists()) return null
    return RawUserStats(
        nickname = getString("nickname") ?: "",
        avatarUrl = getString("avatarUrl"),
        hasPremium = getBoolean("hasPremium") ?: false,
        streakDays = getLong("streakDays")?.toInt() ?: 0,
        stars = getLong("pointsSkill") ?: 0L,
        nolics = getLong("pointsNolics") ?: 0L,
        standardHearts = getLong("standardHearts")?.toInt() ?: 0,
        goldHearts = getLong("goldHearts")?.toInt() ?: 0,
        gold = getLong("gold") ?: 0L,
        currentSkill = getLong("pointsSkill")?.toInt() ?: 0,
        testerLevel = getLong("tester")?.toInt() ?: 0,
        moderatorLevel = getLong("moderator")?.toInt() ?: 0,
        sponsorLevel = getLong("sponsor")?.toInt() ?: 0,
        translatorLevel = getLong("translater")?.toInt() ?: 0,  // legacy field name typo preserved
        adminLevel = getLong("admin")?.toInt() ?: 0,
        developerLevel = getLong("developer")?.toInt() ?: 0,
    )
}
```

### 10c. FirebaseModule.kt

**Файл**: `platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/di/FirebaseModule.kt`

```kotlin
package com.tpov.schoolquiz.platform.firebase.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.schoolquiz.platform.firebase.FirebaseUserStatsDataSource
import com.tpov.schoolquiz.shared.core.stats.UserStatsDataSource
import org.koin.dsl.module

/**
 * Koin module for Firebase platform services.
 * ADR-0009 Rule 1: one module val per leaf module.
 */
val firebaseModule = module {
    single<UserStatsDataSource> {
        FirebaseUserStatsDataSource(
            firestore = FirebaseFirestore.getInstance(),
            auth = FirebaseAuth.getInstance(),
        )
    }
}
```

## 11. apps/android-next/build.gradle.kts

Полная замена:
```kotlin
plugins {
    id("schoolquiz.android.compose.application")  // NEW: Compose convention plugin
}

android {
    namespace = "com.tpov.schoolquiz.apps.android_next"

    defaultConfig {
        applicationId = "com.tpov.schoolquiz.next"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    // Feature modules
    implementation(project(":shared:feature:app-shell:domain"))
    implementation(project(":shared:feature:app-shell:data"))
    implementation(project(":android:feature:app-shell:presentation"))
    implementation(project(":android:core:navigation"))
    implementation(project(":android:core:designsystem"))
    implementation(project(":platform:firebase"))

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose.ui)
    implementation(libs.bundles.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)

    // Decompose
    implementation(libs.bundles.decompose)

    // Koin
    implementation(libs.bundles.koin.android)

    // Lifecycle
    implementation(libs.bundles.androidx.lifecycle)
    implementation(libs.bundles.androidx.lifecycle.compose)

    // Base
    implementation(libs.bundles.androidx.ui.base)

    // Tests
    testImplementation(libs.junit4)
    testImplementation(libs.koin.core)
    androidTestImplementation(libs.bundles.testing.instrumented)
}
```

## 12. apps/android-next — AndroidManifest.xml

Добавить `android:name=".AppApplication"` в `<application>` тег:

```xml
<application
    android:name=".AppApplication"
    android:label="@string/app_name"
    android:theme="@style/Theme.SchoolQuiz">
    <!-- existing activity declarations unchanged -->
```

## 13. AppApplication.kt

**Файл**: `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt`

```kotlin
package com.tpov.schoolquiz.apps.android_next

import android.app.Application
import com.tpov.schoolquiz.platform.firebase.di.firebaseModule
import com.tpov.schoolquiz.shared.feature.app_shell.data.di.appShellDataModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Application class — Koin initialization entry point.
 *
 * startKoin called here (not in MainActivity) to avoid double-init on configuration change
 * and to ensure Firebase/other SDK availability before first Activity starts.
 * OQ#3 resolution: Application.onCreate() per Koin 3.5.6 official docs.
 * ADR-0009 revised.
 */
class AppApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@AppApplication)
            modules(
                firebaseModule,
                appShellDataModule,
                // appShellPresentationModule added in phase-04 (Decompose integration)
            )
        }
    }
}
```

Note: `appShellPresentationModule` добавляется в phase-04 когда `DefaultRootComponent` создан. В phase-01 presentation module ещё пуст.

## 14. Detekt + ktlint setup (B3 fix — Option A — concrete working config)

AC 30 (`phase-07/overview.md:7`) требует `./gradlew detekt ktlintCheck`. В root `build.gradle.kts` эти задачи не зарегистрированы для active modules — только в `legacy/*`. Backend-dev создаёт конкретную working инфраструктуру в phase-01.

### 14a. `gradle/libs.versions.toml` — добавить plugin aliases

```toml
[versions]
# ... existing versions ...
detekt = "1.23.5"
ktlint-gradle = "12.1.1"

[libraries]
# ... existing libs ...
detekt-formatting = { module = "io.gitlab.arturbosch.detekt:detekt-formatting", version.ref = "detekt" }

[plugins]
# ... existing plugins ...
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
ktlint = { id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlint-gradle" }
```

### 14b. `buildSrc/build.gradle.kts` — добавить plugin classpath

```kotlin
dependencies {
    // existing: AGP, Kotlin Gradle plugin
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.5")
    implementation("org.jlleitschuh.gradle.ktlint:org.jlleitschuh.gradle.ktlint.gradle.plugin:12.1.1")
}
```

### 14c. Convention plugins: apply detekt + ktlint к active modules

Изменить existing convention plugins — добавить apply detekt/ktlint с project-wide config:

**`buildSrc/src/main/kotlin/AndroidLibraryConventionPlugin.kt`** (дополнить existing):
```kotlin
override fun apply(target: Project) {
    with(target) {
        // existing: android setup, kotlin, etc.

        pluginManager.apply("io.gitlab.arturbosch.detekt")
        pluginManager.apply("org.jlleitschuh.gradle.ktlint")

        extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            buildUponDefaultConfig = true
            allRules = false
            config.setFrom(rootProject.files("config/detekt/detekt.yml"))
            baseline = rootProject.file("config/detekt/baseline.xml").takeIf { it.exists() }
        }

        extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
            version.set("1.1.1")
            android.set(true)
            ignoreFailures.set(false)
            reporters {
                reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
            }
            filter {
                exclude { it.file.path.contains("/build/") }
                exclude { it.file.path.contains("/generated/") }
            }
        }
    }
}
```

Аналогичный блок — в `AndroidApplicationConventionPlugin.kt`, `AndroidComposeLibraryConventionPlugin.kt`, `AndroidComposeApplicationConventionPlugin.kt`, `KmpLibraryConventionPlugin.kt`.

### 14d. Root `build.gradle.kts` — aggregate tasks

```kotlin
plugins {
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

// Aggregate tasks — allow `./gradlew detekt` / `./gradlew ktlintCheck` от root
tasks.register("detekt") {
    description = "Run detekt across all active (non-legacy) modules"
    group = "verification"
    dependsOn(subprojects
        .filterNot { it.path.startsWith(":legacy") }
        .mapNotNull { it.tasks.findByName("detekt") })
}

tasks.register("ktlintCheck") {
    description = "Run ktlint check across all active (non-legacy) modules"
    group = "verification"
    dependsOn(subprojects
        .filterNot { it.path.startsWith(":legacy") }
        .mapNotNull { it.tasks.findByName("ktlintCheck") })
}
```

### 14e. `config/detekt/detekt.yml`

```yaml
# config/detekt/detekt.yml — project-wide detekt config
build:
  maxIssues: 10
  excludeCorrectable: false

config:
  validation: true
  warningsAsErrors: false

comments:
  excludes: ['**/test/**', '**/androidTest/**']

complexity:
  LongMethod:
    threshold: 100
  LongParameterList:
    threshold: 8
  TooManyFunctions:
    active: false

style:
  MaxLineLength:
    maxLineLength: 120   # per CLAUDE.md constraint
  MagicNumber:
    excludes: ['**/test/**', '**/androidTest/**']
```

### 14f. Phase-01 Validation AC (обновлено — конкретные команды)

```bash
# 1. Tasks visible и fully resolvable:
./gradlew tasks --all --no-configuration-cache | grep -E "^(detekt|ktlintCheck) -"
# Expected: detekt - Run detekt across all active modules; ktlintCheck - Run ktlint check...

# 2. Реальный прогон на active modules:
./gradlew detekt ktlintCheck --no-configuration-cache
# Expected: BUILD SUCCESSFUL (может быть с WARNING, но не FAIL)

# 3. Ensure legacy excluded:
./gradlew detekt --no-configuration-cache 2>&1 | grep -i legacy
# Expected: empty (legacy modules skipped)
```

Эти 3 команды — hard AC, не опциональные `grep tasks`. После phase-01 `./gradlew detekt ktlintCheck` должен быть действительно runnable (может падать на стиль — тогда backend-dev создаёт `config/detekt/baseline.xml` через `./gradlew detektBaseline`).

### Pattern Invariants

Следующие инварианты ОБЯЗАНЫ соблюдаться во всех изменениях этой фазы и в окружающем коде:

1. **Domain purity**: Файлы в `shared/feature/app-shell/domain/` — 0 imports `android.*`, `androidx.*`, Firebase, Retrofit, DI annotations. Verify: `grep -rE "^import (android|androidx|com\.google\.firebase)" shared/feature/app-shell/domain/src/commonMain/`

2. **serialization absent in domain**: `shared/feature/app-shell/domain/build.gradle.kts` — НЕ добавлять `kotlin-serialization` plugin. `TabConfig.kt` — НЕ добавлять `@Serializable`. Verify: `grep -r "kotlin-serialization\|@Serializable" shared/feature/app-shell/domain/`

3. **Firebase confinement**: Firebase SDK types (`DocumentSnapshot`, `FirebaseFirestore`, `CollectionReference`) — только в `platform/firebase/src/main/`. Граница data/commonMain получает только `RawUserStats`. Verify: `grep -rE "com\.google\.firebase" shared/feature/app-shell/data/src/commonMain/`

4. **platform/firebase → shared:core:stats, NOT shared:feature:app-shell:data**: `UserStatsDataSource` interface живёт в `shared/core/stats`. `platform/firebase/build.gradle.kts` зависит от `:shared:core:stats`, не от `:shared:feature:app-shell:data`. Verify: `grep "app-shell:data" platform/firebase/build.gradle.kts` → empty

5. **Walking Skeleton non-rewrite**: ObserveAppShellStateUseCase изменяется ТОЛЬКО в части сигнатуры `invoke` параметра. Бизнес-логика `copy(userStats = stats)` не изменяется. Все остальные domain файлы (AppShellTransitions, NavigateUseCase, etc.) — только read, не write.

6. **Koin exclusive binding**: `UserStatsRepository` биндится ОДИН РАЗ — в `appShellDataModule`. `UserStatsDataSource` биндится ОДИН РАЗ — в `firebaseModule`. Дублирование `single<>` для одного типа — blocker per `di-patterns.md`.

7. **AppApplication thread-safety**: `startKoin` вызывается единожды. Если по любой причине повторный вызов возможен — использовать `androidLogger()` + guard или проверить что Koin не инициализирован. В production лучше catch `KoinApplicationAlreadyStartedException`.
