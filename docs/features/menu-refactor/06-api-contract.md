---
date: 2026-04-20
feature: menu-refactor
author: architect-component (class-level, sections 1-13) + architect-high-level (module APIs, sections 14-16)
status: CANONICAL
---

# Internal API Contract: Menu Refactor

Единственный источник истины для class-level сигнатур. Все остальные документы (01/02/03/04) ссылаются сюда — не переопределяют.

**Cross-reference rule**: если сигнатура упоминается в другом документе, она должна совпадать с этим файлом. При расхождении — этот файл авторитетен.

---

## 1. shared/core/foundation (new module)

### 1.1 QualificationLevel

**Move**: `shared/feature/qualification/domain/.../model/QualificationLevel.kt`
→ `shared/core/foundation/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/foundation/QualificationLevel.kt`

```kotlin
package com.tpov.schoolquiz.shared.core.foundation

enum class QualificationLevel(val points: Int) {
    LEVEL_1(100),
    LEVEL_2(200),
    LEVEL_3(300),
}

fun QualificationLevel.isReachedBy(points: Int): Boolean = points >= this.points
```

**Move test**: `QualificationLevelTest.kt` → `shared/core/foundation/src/commonTest/...`

---

## 2. shared/feature/qualification/domain — dev_mode updates

### 2.1 RegisterTap (param rename)

```kotlin
// File: dev_mode/logic/RegisterTap.kt
// CHANGE: currentEffectiveDeveloperLevel → currentDeveloperLevel

fun registerTap(
    progress: TapProgress,
    nowMillis: Long,
    currentDeveloperLevel: Int,                           // RENAMED
    required: QualificationLevel = QualificationLevel.LEVEL_1,
    resetThresholdMillis: Long = 500L,
    targetCount: Int = 10,
): TapResult
```

### 2.2 ActivateDevModeUseCase (full rewrite)

Replaces overlay-based implementation. Uses lambda injection to avoid cross-feature coupling
(`qualification:domain` cannot import `app-shell:domain`).

```kotlin
// File: dev_mode/use_case/ActivateDevModeUseCase.kt

class ActivateDevModeUseCase(
    private val readCurrentDeveloperLevel: () -> Int,    // provided by caller (sync, from StateFlow)
    private val onDevModeActivated: suspend () -> Unit,  // provided by caller (side effect)
) {
    suspend operator fun invoke(
        progress: TapProgress,
        nowMillis: Long,
    ): TapResult {
        val result = registerTap(progress, nowMillis, readCurrentDeveloperLevel())
        if (result is TapResult.Activated) onDevModeActivated()
        return result
    }
}
```

**Rationale**: Lambda-based injection avoids `qualification:domain → app-shell:domain` cross-feature import
(BLOCKED by clean-architecture.md). See ADR-L3-03 in 03-decisions.md.

**DELETED files** (revert codex fix #2):
- `dev_mode/model/LocalDeveloperOverride.kt`
- `dev_mode/model/DeveloperLevelStats.kt`
- `dev_mode/logic/EffectiveDeveloperLevel.kt`
- `dev_mode/repository/LocalDeveloperOverrideRepository.kt`
- `commonTest/.../fake/FakeLocalDeveloperOverrideRepository.kt`

---

## 3. shared/feature/app-shell/domain — additions

### 3.1 RootEvent (add variants)

**File**: `model/RootEvent.kt` (exists — add variants)

```kotlin
sealed interface RootEvent {
    data object SystemBack : RootEvent              // existing
    data object DevModeActivated : RootEvent        // NEW — show snackbar
    data object DevModeAlreadyActive : RootEvent    // NEW — show toast
    data object SyncStarted : RootEvent             // NEW — show snackbar
}
```

### 3.2 RootComponent (add methods)

**File**: `navigation/RootComponent.kt` (exists — add methods)

```kotlin
interface RootComponent {
    val appShellState: Flow<AppShellState>           // existing
    val events: Flow<RootEvent>                      // existing
    fun onDestination(destination: Destination)      // existing
    fun onActiveTabRetap(tab: Tab): RetapOutcome     // existing
    fun onDeepLink(deepLink: DeepLink)               // existing
    fun onVersionTap(nowMillis: Long)                // NEW — 10-tap dev mode
    fun onSyncNow()                                  // NEW — manual sync trigger
}
```

### 3.3 DrawerFooterAction (add SyncNow)

**File**: `model/DrawerFooterAction.kt` (exists — add variant)

```kotlin
sealed interface DrawerFooterAction {
    data object DesignCatalog : DrawerFooterAction  // existing
    data object SyncNow : DrawerFooterAction        // NEW
    data object About : DrawerFooterAction          // existing
}
```

### 3.4 DrawerSection.HomeQuests (rename)

**File**: `model/DrawerSection.kt:38` — RENAME `MyCourses → HomeQuests`

```kotlin
data object HomeQuests : LocalSection {
    override val tab: Tab = Tab.LOCAL
    override val requiredRoles: Map<Role, Int> = emptyMap()
}
```

### 3.5 TabConfig.HomeQuestsRoot (rename)

**File**: `model/TabConfig.kt:24` — RENAME `MyCoursesRoot → HomeQuestsRoot`

```kotlin
data object HomeQuestsRoot : LocalConfig
```

### 3.6 UserStatsRepository (add methods)

**File**: `repository/UserStatsRepository.kt` (exists — add methods)

```kotlin
interface UserStatsRepository {
    fun observeStats(): Flow<UserStats>              // existing
    suspend fun currentStats(): UserStats            // existing
    suspend fun setLocalDeveloperLevel(value: Int)   // NEW — dev mode only
    suspend fun refreshProfile(): Result<Unit>       // NEW — called by SyncWorker
}
```

**Invariant**: `setLocalDeveloperLevel` is the ONLY method through which a client can write to
`UserStats.qualification.developer` in local Room. All other fields are write-only through
`refreshProfile()`. See `docs/invariants.md` "Local-only qualification override".

---

## 4. shared/feature/app-shell/domain — Visibility changes

### 4.1 isVisible (superqualification OR-bypass)

**File**: `logic/Visibility.kt:50` — UPDATE

```kotlin
fun isVisible(section: DrawerSection, stats: UserStats): Boolean =
    stats.qualification.developer >= QualificationLevel.LEVEL_1.points ||
    section.requiredRoles.all { (role, min) -> actualLevel(role, stats) >= min }
```

Import required: `import com.tpov.schoolquiz.shared.core.foundation.QualificationLevel`

### 4.2 visibleSections LOCAL (reorder + rename)

**File**: `logic/Visibility.kt:68-72` — UPDATE

```kotlin
Tab.LOCAL -> listOf(
    DrawerSection.LocalSection.HomeQuests,   // position 1 (was MyCourses at position 2)
    DrawerSection.LocalSection.MyQuests,
    DrawerSection.LocalSection.Settings,
).filter { isVisible(it, stats) }
```

### 4.3 visibleFooterActions (new signature)

**File**: `logic/Visibility.kt:142` — FULL REPLACEMENT

```kotlin
fun visibleFooterActions(
    isDebugBuild: Boolean,
    stats: UserStats,
): List<DrawerFooterAction> {
    val devToolsVisible = isDebugBuild ||
        stats.qualification.developer >= QualificationLevel.LEVEL_1.points
    return buildList {
        if (devToolsVisible) add(DrawerFooterAction.DesignCatalog)
        if (devToolsVisible) add(DrawerFooterAction.SyncNow)
        add(DrawerFooterAction.About)
    }
}
```

**Full matrix** (from 0-spec-dev-mode.md Footer Contract):
| `isDebugBuild` | `developer` | Output |
|---|---|---|
| `true` | any | `[DesignCatalog, SyncNow, About]` |
| `false` | `>= 100` | `[DesignCatalog, SyncNow, About]` |
| `false` | `< 100` | `[About]` |

### 4.4 rootOf (rename mapping)

**File**: `logic/Visibility.kt:108` — UPDATE

```kotlin
DrawerSection.LocalSection.HomeQuests -> LocalConfig.HomeQuestsRoot   // RENAMED
```

---

## 5. shared/feature/app-shell/data — UserStatsRepositoryImpl

```kotlin
// File: UserStatsRepositoryImpl.kt — UPDATED

class UserStatsRepositoryImpl(
    private val remoteDataSource: UserStatsDataSource,    // existing
    private val userStatsDao: UserStatsDao,               // NEW
    private val currentUid: () -> String?,                // NEW — for targeted writes
) : UserStatsRepository {

    override fun observeStats(): Flow<UserStats> =
        userStatsDao.observeByUid(currentUid() ?: "")
            .map { it?.toDomain() ?: UserStats.guest() }

    override suspend fun currentStats(): UserStats =
        userStatsDao.findByUid(currentUid() ?: "")?.toDomain() ?: UserStats.guest()

    override suspend fun setLocalDeveloperLevel(value: Int) {
        val uid = currentUid() ?: return
        userStatsDao.updateDeveloperLevel(uid, value)
    }

    override suspend fun refreshProfile(): Result<Unit> = runCatching {
        val uid = currentUid() ?: return@runCatching
        val raw = remoteDataSource.fetchOnce()           // NEW method on UserStatsDataSource
        userStatsDao.upsert(raw.toEntity(uid))
    }
}
```

**REQUIRES verify before implementation**:
- `UserStatsDataSource` needs `suspend fun fetchOnce(): RawUserStats` (currently has only `observeRaw()`)
- `RawUserStats` field names for `toEntity()` mapper (see 08-storage-model.md §7)

---

## 6. android/feature/app-shell/presentation — DefaultRootComponent

```kotlin
// File: DefaultRootComponent.kt — ADDITIONS

class DefaultRootComponent(
    componentContext: ComponentContext,
    private val initUseCase: InitializeAppShellUseCase,      // existing
    private val navigateUseCase: NavigateUseCase,             // existing
    private val observeUseCase: ObserveAppShellStateUseCase, // existing
    private val retapUseCase: OnTabRetapUseCase,              // existing
    private val userStatsRepository: UserStatsRepository,     // NEW
    private val workManager: WorkManager,                     // NEW
) : RootComponent, ComponentContext by componentContext {

    // Existing: _appShellState, _events, scope, navigation stacks...

    // NEW: tap progress — lives in component (not Composable) for clean state management
    private val _tapProgress = MutableStateFlow(TapProgress.initial)

    // NEW: created with lambdas to avoid cross-feature coupling
    private val activateDevModeUseCase = ActivateDevModeUseCase(
        readCurrentDeveloperLevel = {
            _appShellState.value.userStats.qualification.developer
        },
        onDevModeActivated = {
            userStatsRepository.setLocalDeveloperLevel(QualificationLevel.LEVEL_1.points)
        },
    )

    // NEW: version text 10-tap handler
    override fun onVersionTap(nowMillis: Long) {
        scope.launch {
            val result = activateDevModeUseCase(_tapProgress.value, nowMillis)
            _tapProgress.value = result.newProgress
            when (result) {
                is TapResult.Activated -> _events.trySend(RootEvent.DevModeActivated)
                is TapResult.AlreadyDev -> _events.trySend(RootEvent.DevModeAlreadyActive)
                else -> Unit
            }
        }
    }

    // NEW: manual sync trigger
    override fun onSyncNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
        workManager.enqueueUniqueWork(
            SyncWorker.WORK_NAME_MANUAL,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        _events.trySend(RootEvent.SyncStarted)
    }
}
```

---

## 7. android/feature/app-shell/presentation — DrawerFooter

```kotlin
// File: ui/drawer/DrawerFooter.kt — UPDATED signature

@Composable
fun DrawerFooter(
    navigator: Navigator,
    isDebugBuild: Boolean,
    versionName: String,
    userStats: UserStats,                         // NEW
    onVersionTap: () -> Unit,                     // NEW (calls rootComponent.onVersionTap)
    onSyncNow: () -> Unit,                        // NEW (calls rootComponent.onSyncNow)
    modifier: Modifier = Modifier,
)
```

Version text tap target (accessibility ≥ 48dp):
```kotlin
Text(
    text = "v$versionName",
    modifier = Modifier
        .clickable(onClick = onVersionTap)
        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
)
```

---

## 8. shared/core/catalog/domain — preserved (Walking Skeleton)

Interface authoritative from Walking Skeleton (no changes):

```kotlin
interface CatalogRepository {
    fun observeAll(): Flow<List<Catalog>>
    suspend fun refreshFromRemote(): Result<Unit>
    suspend fun getById(id: CatalogId): Catalog?
}
```

---

## 9. shared/core/catalog/data — new module

### 9.1 CatalogLocalDataSource

```kotlin
interface CatalogLocalDataSource {
    fun observeAll(): Flow<List<CatalogEntity>>
    suspend fun replaceAll(entities: List<CatalogEntity>)
    suspend fun findById(id: String): CatalogEntity?
}

class CatalogLocalDataSourceImpl(
    private val dao: CatalogDao,
) : CatalogLocalDataSource {
    override fun observeAll() = dao.observeAll()
    override suspend fun replaceAll(entities: List<CatalogEntity>) = dao.replaceAll(entities)
    override suspend fun findById(id: String) = dao.findById(id)
}
```

### 9.2 CatalogRemoteDataSource

```kotlin
interface CatalogRemoteDataSource {
    suspend fun fetchAll(): List<CatalogDto>
}
```

**Canonical type locations** (clean-architecture split):
- `CatalogRemoteDataSource` interface — `shared/core/catalog/data/src/commonMain/` (pure KMP)
- `CatalogDto` pure Kotlin data class — `shared/core/catalog/data/src/commonMain/` (pure KMP — ⇄ `08-storage-model.md §7.3`)
- `CatalogDto.toEntity()` pure Kotlin mapper — `shared/core/catalog/data/src/commonMain/mapper/`
- `FirebaseCatalogRemoteDataSource` impl — `platform/firebase/` (depends on `core:catalog:data` for interface + DTO, on Firebase SDK for Firestore)
- `DocumentSnapshot.toCatalogDto()` Firebase adapter — `platform/firebase/`

`shared/core/catalog/data` **does not** depend on `platform/firebase` — only pure KMP types live in the interface contract.

### 9.3 CatalogRepositoryImpl

```kotlin
class CatalogRepositoryImpl(
    private val local: CatalogLocalDataSource,
    private val remote: CatalogRemoteDataSource,
    private val storageUrlResolver: suspend (String) -> String,   // lambda: path → HTTPS URL (ADR-HLA-07)
) : CatalogRepository {
    override fun observeAll(): Flow<List<Catalog>> =
        local.observeAll().map { list ->
            list.map { it.toDomain() }.sortedBy { it.id.value }
        }

    override suspend fun refreshFromRemote(): Result<Unit> = runCatching {
        val dtos = remote.fetchAll()
        val entitiesWithUrls = dtos.map { dto ->
            val entity = dto.toEntity()
            val pictureUrl = entity.picturePath?.let { path ->
                runCatching { storageUrlResolver(path) }.getOrNull()
            }
            entity.copy(pictureUrl = pictureUrl)
        }
        local.replaceAll(entitiesWithUrls)
    }

    override suspend fun getById(id: CatalogId): Catalog? =
        local.findById(id.value)?.toDomain()
}
```

`storageUrlResolver` — platform-specific lambda, resolved through Koin `named("storageUrlResolver")` from `firebaseCatalogModule` (see §12). `shared/core/catalog/data` stays KMP-pure.

---

## 10. platform/firebase — FirebaseCatalogRemoteDataSource

```kotlin
// File: platform/firebase/src/.../catalog/FirebaseCatalogRemoteDataSource.kt

class FirebaseCatalogRemoteDataSource(
    private val firestore: FirebaseFirestore,
) : CatalogRemoteDataSource {
    override suspend fun fetchAll(): List<CatalogDto> =
        firestore.collection("catalogs")
            .get()
            .await()
            .documents
            .mapNotNull { it.toCatalogDto() }
}
```

---

## 11. platform/android-services — SyncWorker

**Topology: Syncable list** (ADR-HLA-04, Topology B — direct repo injection отвергнут).
`SyncWorker` зависит только от `core:sync` — нет зависимости на `feature:app-shell:domain` или `core:catalog:domain` из `platform`.

```kotlin
// File: platform/android-services/src/.../sync/SyncWorker.kt

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val syncables: List<Syncable>,   // injected via Koin — см. §14
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        for (syncable in syncables) {
            syncable.sync().onFailure { return Result.retry() }
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME_PERIODIC = "periodic_sync"
        const val WORK_NAME_MANUAL = "manual_sync"
        val PERIODIC_INTERVAL_DEFAULT = 1L to TimeUnit.DAYS
    }
}
```

**Koin wiring** (в `apps:android-next` composition root):
```kotlin
val syncModule = module {
    single<WorkManager> { WorkManager.getInstance(androidContext()) }
    single<List<Syncable>> {
        listOf(
            get<UserStatsRepositoryImpl>(),   // implements Syncable via refreshProfile()
            get<CatalogRepositoryImpl>(),     // implements Syncable via refreshFromRemote()
        )
    }
}
```

⇄ ADR-HLA-04: "Worker topology: Alternatives Considered" — direct repo injection отвергнут как `platform → feature` coupling.

---

## 12. Koin module additions

All new modules registered in `AppApplication.startKoin { modules(...) }`.

```kotlin
// persistence module (new)
val persistenceModule = module {
    single<AppDatabase> {
        Room.databaseBuilder(androidContext(), AppDatabase::class.java, "schoolquiz.db")
            .addMigrations() // none for version 1
            .build()
    }
    single<UserStatsDao> { get<AppDatabase>().userStatsDao() }
    single<CatalogDao> { get<AppDatabase>().catalogDao() }
}

// catalog data module (new) — pure KMP bindings only, no Firebase types
// Location: shared/core/catalog/data/src/commonMain/kotlin/.../di/CatalogDataModule.kt
val catalogDataModule = module {
    single<CatalogLocalDataSource> { CatalogLocalDataSourceImpl(get()) }
    single<CatalogRepository> { CatalogRepositoryImpl(local = get(), remote = get(), storageUrlResolver = get(named("storageUrlResolver"))) }
}

// firebase catalog module (new) — Firebase-specific bindings
// Location: platform/firebase/src/main/kotlin/.../di/FirebaseCatalogModule.kt
val firebaseCatalogModule = module {
    single<CatalogRemoteDataSource> { FirebaseCatalogRemoteDataSource(get<FirebaseFirestore>()) }
    single<suspend (String) -> String>(named("storageUrlResolver")) {
        { path -> FirebaseStorage.getInstance().reference.child(path).downloadUrl.await().toString() }
    }
}

// catalog domain module (new)
// Location: shared/core/catalog/domain/src/commonMain/kotlin/.../di/CatalogDomainModule.kt
val catalogDomainModule = module {
    factory { ObserveCatalogsUseCase(get()) }
    factory { CreateQuestUseCase(get(), get()) }
}

// sync module (new)
val syncModule = module {
    single<WorkManager> { WorkManager.getInstance(androidContext()) }
}

// UPDATED: appShellDataModule
val appShellDataModule = module {
    single<UserStatsRepository> {
        UserStatsRepositoryImpl(
            remoteDataSource = get(),
            userStatsDao = get(),
            currentUid = { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid },
        )
    }
}

// UPDATED: appShellPresentationModule
val appShellPresentationModule = module {
    factory { (ctx: ComponentContext) ->
        DefaultRootComponent(
            componentContext = ctx,
            initUseCase = get(),
            navigateUseCase = get(),
            observeUseCase = get(),
            retapUseCase = get(),
            userStatsRepository = get(),   // NEW
            workManager = get(),           // NEW
        )
    }
    // ... existing factories unchanged
}
```

WorkManager periodic scheduling in `AppApplication.onCreate()`:
```kotlin
WorkManager.getInstance(this).enqueueUniquePeriodicWork(
    SyncWorker.WORK_NAME_PERIODIC,
    ExistingPeriodicWorkPolicy.KEEP,
    PeriodicWorkRequestBuilder<SyncWorker>(1L, TimeUnit.DAYS)
        .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
        .build(),
)
```

---

## 13. android/core/designsystem — new components

### 13.0 CatalogDisplayItem (presentation model)

**File**: `android/core/designsystem/model/CatalogDisplayItem.kt`
**Module**: `android:core:designsystem` (canonical home — ADR-L3-03)

```kotlin
data class CatalogDisplayItem(
    val id: CatalogId,
    val name: String,
    val pictureUrl: String?,   // resolved HTTPS URL (pre-computed in data layer via ADR-HLA-07)
)
```

**Mapper helper** (extension в том же модуле):
```kotlin
// File: android/core/designsystem/model/CatalogDisplayItem.kt

fun Catalog.toDisplayItem(): CatalogDisplayItem = CatalogDisplayItem(
    id = id,
    name = name,
    pictureUrl = null,   // domain Catalog не содержит pictureUrl — см. ниже
)
```

**Важно**: domain `Catalog` не содержит `pictureUrl` (Codex fix #5). Resolved URL хранится в `CatalogEntity.pictureUrl` (кэш в Room — ADR-HLA-07). Presentation layer получает `pictureUrl` из `CatalogEntity` через `CatalogRepositoryImpl`, который эмитит `Catalog` + URL через кастомный flow или отдельный `observeAllWithUrls(): Flow<List<CatalogDisplayItem>>` в repository.

**Практический подход** для implementation:
```kotlin
// В CatalogRepositoryImpl — добавить метод для presentation:
fun observeAllForDisplay(): Flow<List<CatalogDisplayItem>> =
    catalogDao.observeAll().map { entities ->
        entities.map { entity ->
            CatalogDisplayItem(
                id = CatalogId(entity.id),
                name = entity.name,
                pictureUrl = entity.pictureUrl,   // pre-resolved HTTPS URL из Room cache
            )
        }
    }
```

⇄ ADR-L3-03 (CatalogDisplayItem canonical home), ADR-HLA-07 (URL pre-resolution in data layer)

---

### 13.1 CatalogSpinner

```kotlin
// File: android/core/designsystem/components/CatalogSpinner.kt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogSpinner(
    items: List<CatalogDisplayItem>,
    selectedId: CatalogId?,           // null = "Все категории"
    onSelectionChanged: (CatalogId?) -> Unit,
    modifier: Modifier = Modifier,
)
```

Uses `ExposedDropdownMenuBox` + `menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)`.
Prepends pseudo-item "Все категории" (maps to `null` selectedId).

### 13.2 CatalogGrid

```kotlin
// File: android/core/designsystem/components/CatalogGrid.kt

@Composable
fun CatalogGrid(
    items: List<CatalogDisplayItem>,
    onCatalogClick: (CatalogId) -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
fun CatalogGridItem(
    item: CatalogDisplayItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Uses `LazyVerticalGrid(columns = GridCells.Fixed(2))`.
Image: `AsyncImage(model = item.pictureUrl, contentDescription = item.name)` via Coil 3.
`item.pictureUrl` = HTTPS URL (pre-resolved) — нет Firebase Storage custom fetcher, нет re-fetch issue.

⇄ ADR-L3-03, ADR-HLA-07

---

## 14. `Syncable` — `shared/core/sync` [architect-high-level addition]

**Модуль:** `:shared:core:sync`
**File:** `src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/sync/Syncable.kt` (NEW)

```kotlin
package com.tpov.schoolquiz.shared.core.sync

interface Syncable {
    suspend fun sync(): Result<Unit>
}
```

**Implementors (после menu-refactor):**
- `UserStatsRepositoryImpl` в `feature:app-shell:data` — оборачивает `refreshProfile()`
- `CatalogRepositoryImpl` в `core:catalog:data` — оборачивает `refreshFromRemote()`

**Consumer:** `SyncWorker` в `platform:android-services` — получает `List<Syncable>` из Koin.

---

## 15. Module Ownership Matrix [architect-high-level]

| API / Type | Module | Public (cross-module) | Owner design |
|---|---|---|---|
| `QualificationLevel` | `core:foundation` | ✓ public | architect-high-level |
| `Syncable` | `core:sync` | ✓ public | architect-high-level |
| `UserStatsRepository` interface | `feature:app-shell:domain` | ✓ public | architect-high-level |
| `UserStatsRepositoryImpl` class | `feature:app-shell:data` | internal | architect-component |
| `UserStatsEntity`, `UserStatsDao` | `core:persistence` | internal | architect-component |
| `CatalogRepository` interface | `core:catalog:domain` | ✓ public | architect-high-level (Walking Skeleton) |
| `CatalogRepositoryImpl` class | `core:catalog:data` | internal | architect-component |
| `CatalogEntity`, `CatalogDao` | `core:persistence` | internal | architect-component |
| `CatalogRemoteDataSource` interface | `core:catalog:data` (commonMain) | internal (DI boundary) | architect-component |
| `CatalogDto` + `CatalogDto.toEntity()` | `core:catalog:data` (commonMain, pure Kotlin) | internal | architect-component |
| `DocumentSnapshot.toCatalogDto()` | `platform:firebase` (Firebase adapter) | internal | architect-component |
| `FirebaseCatalogRemoteDataSource` | `platform:firebase` | internal | architect-component |
| `firebaseCatalogModule` Koin module | `platform:firebase/di/` | internal (composition) | architect-component |
| `catalogDataModule` Koin module | `core:catalog:data/di/` | internal (composition) | architect-component |
| `DrawerFooterAction` sealed set | `feature:app-shell:domain` | ✓ public | architect-high-level |
| `visibleFooterActions(isDebugBuild, stats)` | `feature:app-shell:domain` | ✓ public | architect-high-level |
| `isVisible(section, stats)` | `feature:app-shell:domain` | ✓ public | architect-high-level |
| `RootComponent` interface | `feature:app-shell:domain` | ✓ public | architect-high-level |
| `DefaultRootComponent` class | `android:feature:app-shell:presentation` | internal | architect-component |
| `SyncWorker` class | `platform:android-services` | internal | architect-component |
| `CatalogSpinner`, `CatalogGrid` | `android:core:designsystem` | ✓ public (cross-feature UI) | architect-component |

---

## 16. Resolved Debates [CLOSED]

*Все три debate point закрыты architect-component. Решения зафиксированы в ADRs.*

### Debate 1: SyncWorker topology → **RESOLVED: Syncable list (Topology B)**

**Решение**: §11 обновлён — `SyncWorker` принимает `List<Syncable>` из Koin. Direct repo injection удалён.
**Обоснование**: ADR-HLA-04 Topology B. `platform:android-services → core:sync` (только). Нет `platform → feature` coupling.

### Debate 2: ActivateDevModeUseCase → **RESOLVED: lambda injection**

**Решение**: §2.2 — lambda-based signature. `qualification:domain → app-shell:domain` cross-feature import заблокирован clean-architecture.md.
**Обоснование**: ADR-L3-01 в `03-decisions.md`.

### Debate 3: pictureUrl delivery → **RESOLVED: CatalogDisplayItem presentation model**

**Решение**: §13.0 — `CatalogDisplayItem(id, name, pictureUrl: String?)` в `android:core:designsystem`. `CatalogEntity.pictureUrl` = Room-кэш HTTPS URL (pre-resolved при `refreshFromRemote()`). `CatalogGrid`/`CatalogSpinner` принимают `List<CatalogDisplayItem>`.
**Обоснование**: ADR-L3-03 (canonical home) + ADR-HLA-07 (URL pre-resolution in data layer).
