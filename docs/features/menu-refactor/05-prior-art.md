# Prior Art: Menu Refactor

> **⚠️ PRIOR ART.** Этот документ — reference для SDK/library patterns. Некоторые ранние рекомендации **SUPERSEDED** принятыми ADR в `03-decisions.md`. Маркеры `⚠️ SUPERSEDED` указывают на актуальное решение. Для implementation — используй `06-api-contract.md` + `07-events.md` + `08-storage-model.md` как source of truth, `05-prior-art.md` — только для контекста.

Generated: 2026-04-20
Researcher: web-researcher agent

---

## SDK / Library Reference

### Coil 3.4.0 (`io.coil-kt.coil3`)

- Source: https://coil-kt.github.io/coil/getting_started/
- Changelog: https://github.com/coil-kt/coil/blob/main/CHANGELOG.md

#### Gradle coordinates

```toml
# libs.versions.toml
[versions]
coil = "3.4.0"

[libraries]
coil-compose     = { module = "io.coil-kt.coil3:coil-compose",        version.ref = "coil" }
coil-okhttp      = { module = "io.coil-kt.coil3:coil-network-okhttp", version.ref = "coil" }
# Optional extras
coil-gif         = { module = "io.coil-kt.coil3:coil-gif",            version.ref = "coil" }
coil-test        = { module = "io.coil-kt.coil3:coil-test",           version.ref = "coil" }
```

```kotlin
// build.gradle.kts (androidMain or android module)
implementation(libs.coil.compose)
implementation(libs.coil.okhttp)
```

#### Key APIs

**AsyncImage signature:**

```kotlin
AsyncImage(
    model       = "https://example.com/image.jpg", // or ImageRequest.Builder(...)
    contentDescription = "description",
    modifier    = Modifier.fillMaxWidth(),
    contentScale = ContentScale.Crop,
    placeholder = painterResource(R.drawable.placeholder),
    error       = painterResource(R.drawable.error),
)
```

**Singleton ImageLoader setup (Android Application class):**

```kotlin
class AppApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .crossfade(true)
            .components {
                add(StorageReferenceFetcher.Factory()) // custom fetcher example
            }
            .build()
}
```

**Compose Multiplatform variant** (preferred for KMP):

```kotlin
// Near app root composable
setSingletonImageLoaderFactory { context ->
    ImageLoader.Builder(context)
        .crossfade(true)
        .components { add(MyCustomFetcher.Factory()) }
        .build()
}
```

**Custom Fetcher.Factory\<StorageReference\> skeleton** (для Firebase Storage):

```kotlin
class StorageReferenceFetcher(
    private val data: StorageReference,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        // resolve download URL first (avoids Coil re-fetch issue #2551)
        val url = data.downloadUrl.await().toString()
        val request = Request.Builder().url(url).build()
        val response = OkHttpClient().newCall(request).await()
        return SourceFetchResult(
            source = ImageSource(response.body!!.source(), options.fileSystem),
            mimeType = response.header("Content-Type"),
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory : Fetcher.Factory<StorageReference> {
        override fun create(data: StorageReference, options: Options, imageLoader: ImageLoader): Fetcher? =
            StorageReferenceFetcher(data, options)
    }
}
```

**Keyer** — replaces `Fetcher.key` (breaking change vs Coil 2):

```kotlin
class StorageReferenceKeyer : Keyer<StorageReference> {
    override fun key(data: StorageReference, options: Options): String = data.path
}
// Register alongside Fetcher in components { add(StorageReferenceKeyer()) }
```

#### Known issues (relevant)

- **Issue #2551** — `AsyncImage` with custom fetcher re-fetches on recomposition if the `data` object changes identity (new instance with same logical content).
  - Source: https://github.com/coil-kt/coil/issues/2551
  - Root cause: cache key lookup fails when data class recreated during recomposition.
  - **Mitigation for project**: resolve `StorageReference.downloadUrl` in Repository layer → pass plain HTTPS URL string to `AsyncImage`, never pass `StorageReference` directly as model. This bypasses the custom fetcher entirely for cached items.
  - Alternative mitigation: implement `equals()` / `hashCode()` on the data wrapper.

- **FireCoil library** (https://github.com/thatfiredev/firecoil) — unmaintained, Coil 2 only. Do NOT use. Write own `Fetcher.Factory<StorageReference>` or pre-resolve URL.

#### Recommendation for project

> **⚠️ SUPERSEDED** by ADR-HLA-07 + ADR-L3-03: domain `Catalog` остаётся picturePath-only; HTTPS URL кешируется в `CatalogEntity.pictureUrl` (Room) и проецируется в `CatalogDisplayItem.pictureUrl` (presentation). См. `03-decisions.md` ADR-HLA-07.

~~**Preferred pattern** (historical): Repository resolves `StorageReference.downloadUrl.await()` → stores HTTPS URL in `Catalog.imageUrl: String` → UI passes plain string to `AsyncImage`. No custom fetcher needed for catalog MVP. Custom fetcher path remains as fallback if URLs become stale.~~

---

### Firebase Firestore Android SDK

- Source: https://firebase.google.com/docs/firestore/query-data/listen
- Firebase Blog coroutines guide: https://firebase.blog/posts/2022/10/using-coroutines-flows-with-firebase-on-android/

#### BoM version note

Firebase BoM **34+** removes `-ktx` suffix from artifact names. This is a **BREAKING CHANGE** for existing imports:

```kotlin
// Before BoM 34 (old, with -ktx)
implementation("com.google.firebase:firebase-firestore-ktx")

// BoM 34+ (new, without -ktx — ktx merged into base artifact)
implementation("com.google.firebase:firebase-firestore")
```

Project currently uses `firebase-firestore-ktx` — check BoM version before upgrading.

#### callbackFlow + addSnapshotListener pattern

Standard pattern (already used in project at `FirebaseUserStatsDataSource.kt:28`):

```kotlin
fun observeCatalogs(): Flow<List<Catalog>> = callbackFlow {
    val listener = catalogsRef.addSnapshotListener { snapshot, error ->
        if (error != null) { close(error); return@addSnapshotListener }
        if (snapshot != null) {
            val catalogs = snapshot.documents.mapNotNull { doc ->
                doc.toCatalog() // mapper: DocumentSnapshot → Catalog domain model
            }
            trySend(catalogs)
        }
    }
    awaitClose { listener.remove() }
}
```

**Modern KTX alternative** (`firebase-firestore-ktx` ≥ 24.3.0):

```kotlin
fun observeCatalogs(): Flow<QuerySnapshot> = catalogsRef.snapshots()
    .map { it.documents.mapNotNull { doc -> doc.toCatalog() } }
```

#### get() vs snapshot listener

| Approach | When to use |
|---|---|
| `get().await()` | One-shot read (initial load, manual refresh/SyncNow) |
| `addSnapshotListener` / `snapshots()` | Real-time reactive (userStats pattern) |

For catalog: `get().await()` on WorkManager sync + `addSnapshotListener` for live updates are both valid. Given catalog data is semi-static, `get().await()` in CoroutineWorker may be simpler.

#### Offline persistence behavior

- Firestore caches last-known data locally (client cache) — reads succeed offline.
- `metadata.hasPendingWrites` — true when write is local, not yet synced to server.
- `metadata.isFromCache` — true when data came from local cache (offline or server not yet responded).
- Write operations that use `suspend fun` can **hang indefinitely offline** (GitLiveApp issue #518). For catalog (read-only public), this is not a concern.

#### orderBy(FieldPath.documentId())

```kotlin
// Stable ordering by document ID
db.collection("catalogs")
    .orderBy(FieldPath.documentId())
    .get()
    .await()
```

---

### Firebase Storage Android

- Source: https://firebase.google.com/docs/storage/android/download-files

#### StorageReference.downloadUrl.await()

```kotlin
// Dependencies
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.x")
implementation("com.google.firebase:firebase-storage")  // BoM 34+ (no -ktx)

// Usage
import kotlinx.coroutines.tasks.await

suspend fun resolveImageUrl(path: String): String {
    val storageRef = FirebaseStorage.getInstance().reference
    return storageRef.child(path).downloadUrl.await().toString()
}

// Example for catalog: path = "catalog-pictures/${catalogId}.jpg"
```

#### Public read ACL setup

Firebase Storage Security Rules for public catalog images:

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    // catalog-pictures: public read, no write from client
    match /catalog-pictures/{imageId} {
      allow read: if true;
      allow write: if false; // write only via admin SDK / Functions
    }
    // user images: authenticated only
    match /users/{userId}/{allPaths=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

#### Path handling for catalog-pictures

Convention: `catalog-pictures/{catalogId}.jpg` or `catalog-pictures/{catalogId}/{imageName}.jpg`.

**Recommendation**: store resolved HTTPS URL (`downloadUrl`) in Firestore `catalogs/{id}.imageUrl` field at write time (admin script). Client reads URL string directly from Firestore doc — avoids runtime Storage SDK call per item.

---

### Room KMP 2.7.x

- Source (official): https://developer.android.com/kotlin/multiplatform/room
- KMP guide: https://carrion.dev/en/posts/room-in-kmp/

#### libs.versions.toml setup

```toml
[versions]
room  = "2.7.1"
ksp   = "2.1.21-2.0.1"   # must match Kotlin version

[libraries]
androidx-room-runtime  = { module = "androidx.room:room-runtime",  version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
# For SQLite bundled (required for non-Android targets):
androidx-sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version.ref = "sqlite" }

[plugins]
ksp  = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room = { id = "androidx.room",           version.ref = "room" }
```

#### build.gradle.kts for KMP module (androidTarget only)

```kotlin
plugins {
    kotlin("multiplatform")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    androidTarget()
    // jvm() if needed

    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.room.runtime)
        }
    }
}

// KSP per-target — REQUIRED (cannot use global ksp() in KMP)
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    // add("kspJvm", libs.androidx.room.compiler)  // if jvm target
}

room {
    schemaDirectory("$projectDir/schemas")
}
```

#### @ConstructedBy pattern (required for KMP)

```kotlin
// commonMain
@Database(entities = [CatalogEntity::class], version = 1)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
// Room compiler generates `actual` for each target automatically
```

#### @Entity + @Dao skeleton

```kotlin
@Entity(tableName = "catalogs")
data class CatalogEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val imageUrl: String?,
    val syncedAtMillis: Long,
)

@Dao
interface CatalogDao {
    @Query("SELECT * FROM catalogs ORDER BY title ASC")
    fun observeAll(): Flow<List<CatalogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CatalogEntity>)

    @Query("DELETE FROM catalogs")
    suspend fun deleteAll()
}
```

#### Migration strategy

```kotlin
// KMP migrations use SQLiteConnection (not SupportSQLiteDatabase)
object Migration_1_2 : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE catalogs ADD COLUMN syncedAtMillis INTEGER NOT NULL DEFAULT 0")
    }
}
```

#### In-memory testing

```kotlin
// JVM / Android instrumented test
Room.inMemoryDatabaseBuilder<AppDatabase>()
    .build()
```

#### Kotlin 1.9.x note

With Kotlin 1.9.x (project uses 1.9.22), add to `gradle.properties`:

```
kotlin.native.disableCompilerDaemon=true
```

Not needed for Kotlin 2.0+. Since project is on 1.9.22, this property is **required** if any native target is present. Android-only module is safe without it.

---

### WorkManager 2.9.1

- Source: https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work
- Testing: https://gist.github.com/pfmaggi/fe2d069fb9c9b9c6ac3582b2e0a1e646

#### Dependency

```toml
# libs.versions.toml already has work = "2.9.1"
[libraries]
androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
```

```kotlin
// android module build.gradle.kts
implementation(libs.androidx.work.runtime.ktx)
```

#### CoroutineWorker pattern

```kotlin
class CatalogSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val catalogRepository: CatalogRepository, // injected via WorkerFactory
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            catalogRepository.refreshFromRemote()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "catalog_sync"
        const val PERIODIC_WORK_NAME = "catalog_sync_periodic"
    }
}
```

#### enqueueUniquePeriodicWork (scheduled sync) — KEEP policy

```kotlin
WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    CatalogSyncWorker.PERIODIC_WORK_NAME,
    ExistingPeriodicWorkPolicy.KEEP, // do NOT replace running periodic sync
    PeriodicWorkRequestBuilder<CatalogSyncWorker>(1, TimeUnit.DAYS)
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .build()
)
```

#### enqueueUniqueWork (manual SyncNow trigger) — REPLACE policy

```kotlin
WorkManager.getInstance(context).enqueueUniqueWork(
    CatalogSyncWorker.UNIQUE_WORK_NAME,
    ExistingWorkPolicy.REPLACE, // cancel pending and re-run immediately
    OneTimeWorkRequestBuilder<CatalogSyncWorker>()
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .build()
)
```

> **Note**: `ExistingPeriodicWorkPolicy.REPLACE` is **deprecated** in WorkManager 2.8.0+ in favor of `UPDATE`. For one-time work `ExistingWorkPolicy.REPLACE` is still valid and not deprecated.

#### Testing via TestListenableWorkerBuilder

```kotlin
@Test
fun `catalog sync worker succeeds`() = runTest {
    val worker = TestListenableWorkerBuilder<CatalogSyncWorker>(context)
        .build()
    val result = worker.doWork()
    assertEquals(Result.success(), result)
}
```

#### WorkerFactory for Koin (no AssistedInject)

```kotlin
class CatalogWorkerFactory(
    private val catalogRepository: CatalogRepository,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        return if (workerClassName == CatalogSyncWorker::class.java.name)
            CatalogSyncWorker(appContext, workerParameters, catalogRepository)
        else null
    }
}
```

#### Android 14 / 15 foreground service types impact

- **Applies only to foreground (long-running) workers** that call `setForeground()`.
- Catalog sync is **background-only** (no `setForeground()`), no FGS types required.
- If future sync needs >10 min (unlikely for catalog): declare `android:foregroundServiceType="dataSync"` in manifest for `SystemForegroundService`.
- Android 15: `dataSync` FGS capped at **6 hours / 24h** — not an issue for periodic catalog sync.
- For new app: **no impact** on catalog WorkManager usage.

---

### Material3 Compose 1.4.0

- Source: https://composables.com/material3/exposeddropdownmenubox
- Android Developers reference: https://developer.android.com/reference/kotlin/androidx/compose/material/ExposedDropdownMenuBoxScope

#### ExposedDropdownMenuBox (read-only spinner)

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogSpinner(
    selected: Catalog?,
    options: List<Catalog>,
    onSelected: (Catalog) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }, // use `it`, NOT `!expanded`
    ) {
        OutlinedTextField(
            value = selected?.title ?: "",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable) // read-only spinner
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { catalog ->
                DropdownMenuItem(
                    text = { Text(catalog.title) },
                    onClick = { onSelected(catalog); expanded = false },
                )
            }
        }
    }
}
```

> **Breaking change (Material3 1.3+)**: `Modifier.menuAnchor()` without arguments is **deprecated**. Use `menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)` for read-only fields.

> **Common pitfall**: `onExpandedChange = { expanded = !expanded }` causes toggling issues in some versions. Use `onExpandedChange = { expanded = it }`.

#### LazyVerticalGrid for CatalogGrid

```kotlin
LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    contentPadding = PaddingValues(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier.fillMaxSize(),
) {
    items(catalogs, key = { it.id.value }) { catalog ->
        CatalogGridItem(catalog = catalog, onClick = { onCatalogSelected(catalog) })
    }
}
```

#### SnackbarHost + SnackbarHostState in Scaffold

Missing in current `AppShellScreen.kt:129-141`. Must add:

```kotlin
@Composable
fun AppShellScreen(rootComponent: RootComponent, ...) {
    val snackbarHostState = remember { SnackbarHostState() }

    // collect events from RootComponent
    LaunchedEffect(Unit) {
        rootComponent.events.collect { event ->
            when (event) {
                is RootEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // ...existing topBar, bottomBar
    ) { padding ->
        // content
    }
}
```

---

### Decompose 3.5.0

- Source (official): https://arkivanov.github.io/Decompose/navigation/stack/overview/
- Quick start: https://arkivanov.github.io/Decompose/getting-started/quick-start/
- Article (2025): https://jasondl.ee/2025/decompose-navigation-and-the-root-component/

#### childStack(serializer = null) — process death behavior

Project uses `serializer = null` on all `childStack` (ADR-COMP-02). Consequence:
- Process death → navigation state **not restored** → user lands on initial screen.
- This is by-design for project (decided in `app-shell-menu/03-decisions.md`).
- SyncNow + DevMode activation survive only while process is alive.

#### RootComponent events channel pattern

For side-effects that originate in `DefaultRootComponent` and need to show Snackbar in UI:

> **⚠️ SUPERSEDED** by `07-events.md`: actual RootEvent hierarchy: `SystemBack`, `DevModeActivated`, `DevModeAlreadyActive`, `SyncStarted`. Snackbar pipe — per event type, не generic `ShowSnackbar`. См. `07-events.md`.

```kotlin
// ⚠️ Historical — superseded. Canonical RootEvent hierarchy в 07-events.md.
// In RootComponent interface
interface RootComponent {
    val appShellState: StateFlow<AppShellState>
    val events: Flow<RootEvent>  // NEW for menu-refactor

    fun onSyncNow()               // NEW side-effect method (not navigation)
    fun onDestination(dest: Destination)
}

sealed interface RootEvent {
    data class ShowSnackbar(val message: String) : RootEvent
    data object SyncStarted : RootEvent
}

// In DefaultRootComponent implementation
private val _events = MutableSharedFlow<RootEvent>(extraBufferCapacity = 64)
override val events: Flow<RootEvent> = _events.asSharedFlow()

override fun onSyncNow() {
    coroutineScope.launch {
        _events.emit(RootEvent.SyncStarted)
        try {
            catalogRepository.refreshFromRemote()
            _events.emit(RootEvent.ShowSnackbar("Синхронизация завершена"))
        } catch (e: Exception) {
            _events.emit(RootEvent.ShowSnackbar("Ошибка синхронизации"))
        }
    }
}
```

> **Key decision from 1-research.md (Open Question #3, resolved)**: `onSyncNow()` is a **method on RootComponent**, not a `Destination`. It's a side-effect, not navigation. `Destination` sealed class stays clean.

#### Multiple stacks in one RootComponent

```kotlin
// Each childStack with unique key — already pattern in DefaultRootComponent
private val localStack = childStack<LocalConfig, LocalChild>(
    source = localNavigation,
    key = "LocalStack",
    serializer = null,
    initialConfiguration = LocalConfig.HomeQuestsRoot, // after home-quests rename
    handleBackButton = false,
    childFactory = ::createLocalChild,
)
```

---

### Koin 4.2.0

- Source: https://insert-koin.io/docs/support/releases/
- ABI issue: https://github.com/InsertKoinIO/koin/issues/2391
- 4.2.1 release: https://github.com/InsertKoinIO/koin/releases/tag/4.2.1

#### ABI break 4.1 → 4.2.0 — **use 4.2.1**

| Issue | Version | Status |
|---|---|---|
| `NoSuchMethodError` for `runOnKoinStarted` | 4.2.0 | Fixed in 4.2.1 |
| Stacked-params / SavedStateHandle regression | 4.2.0 | Fixed in 4.2.1 |
| `Scope._closed` concurrency (non-volatile) | 4.2.0 | Fixed in 4.2.1 |
| BOM/Annotations version conflict (Beta3) | 4.2.0-Beta3 | N/A (stable) |

**Recommendation**: if upgrading from 4.1.x, go to **4.2.1 minimum**, never land on 4.2.0.

#### Module pattern (established in project)

```kotlin
// Per-feature module convention (from appShellDataModule example)
val catalogDataModule = module {
    single<CatalogRepository> { CatalogRepositoryImpl(get(), get()) }
    single<CatalogRemoteDataSource> { FirebaseCatalogDataSource(get()) }
    single<CatalogDao> { get<AppDatabase>().catalogDao() }
}

val qualificationDataModule = module {
    single<LocalDeveloperOverrideRepository> { LocalDeveloperOverrideRepositoryImpl(get()) }
}
```

#### single { } vs factory { }

| DSL | Scope | When to use |
|---|---|---|
| `single { }` | App singleton | Repositories, DAOs, DataSources, WorkerFactory |
| `factory { }` | New per request | Use cases, RootComponent (needs fresh ComponentContext) |

#### koinInject() vs constructor injection in Compose

```kotlin
// Option A: constructor via parent component (preferred — explicit)
@Composable
fun CatalogScreen(viewState: CatalogViewState, onSync: () -> Unit) { ... }

// Option B: koinInject() in Composable (acceptable for leaf use cases)
@Composable
fun CatalogScreen() {
    val repository: CatalogRepository = koinInject()
    // ...
}
```

Project pattern uses Decompose components (no ViewModel), so dependencies are injected into `DefaultRootComponent` constructor via Koin `factory { (ctx) -> DefaultRootComponent(ctx, get(), ...) }`.

---

### kotlin.test (KMP)

- Source: https://kotlinlang.org/docs/multiplatform-run-tests.html
- KMP Testing Guide 2025: https://www.kmpship.app/blog/kotlin-multiplatform-testing-guide-2025

#### Setup in commonTest

```kotlin
// shared module build.gradle.kts
kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
```

#### Key annotations and assertions

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
```

#### Flow testing without Turbine (project rule)

```kotlin
@Test
fun `observeCatalogs emits initial list`() = runTest {
    val fakeRepo = FakeCatalogRepository()
    fakeRepo.setCatalogs(listOf(testCatalog1, testCatalog2))

    val results = fakeRepo.observeAll().take(1).toList()
    assertEquals(1, results.size)
    assertEquals(2, results.first().size)
}
```

#### commonTest vs jvmTest source set rules

- `commonTest` — all shared domain/logic tests; runs on JVM + native. **Primary location**.
- `jvmTest` — JVM-specific tests only (Room in-memory DAO, etc.). Currently contains only `.gitkeep` in project.
- `androidTest` — instrumented tests (Room migration, Compose UI). Lives in `:app/androidTest`.

**Rule**: never put `android.*` imports in `commonTest`. Use fakes for Android-bound components.

---

## Legacy References

### Legacy SyncWorker (inspiration)

- Source: `legacy/app/src/main/java/com/tpov/schoolquiz/presentation/SyncWorker.kt`

#### Pattern

```kotlin
// Legacy: Dagger AssistedInject + AppWorkerFactory
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncInteractor: SyncInteractor,
    private val profileUseCase: ProfileUseCase,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        profileUseCase.syncProfile()    // step 1
        syncSettings()                  // step 2
        syncQuizData()                  // step 3
        Result.success(outputData)
    }
}
```

Flow: `syncProfile → syncSettings → syncQuizData` sequential within `doWork()`.

Lock/unlock mechanism for concurrent sync protection:
```kotlin
// Legacy uses LockServerResult (Success / AlreadyLocked / Error) with retry loop
var retryCount = 0
while (retryCount < MAX_RETRIES) {
    lockResult = syncInteractor.lockServer(event)
    when (lockResult) {
        is LockServerResult.Success -> break
        is LockServerResult.AlreadyLocked -> { retryCount++; delay(2000) }
        is LockServerResult.Error -> continue@events
    }
}
```

#### What we take vs change

| Pattern | Legacy | New app |
|---|---|---|
| DI | Dagger `@AssistedInject` | Koin `WorkerFactory` (no assisted inject) |
| Worker type | `CoroutineWorker` | `CoroutineWorker` (same) |
| Steps | syncProfile + syncSettings + syncQuizData | refreshCatalogs (MVP; profile sync TBD) |
| Lock mechanism | Server-side lock/unlock | Not needed for catalog (read-only, no lock contention) |
| Notification | Manual `NotificationCompat` in worker | WorkManager notification not needed for catalog sync MVP |
| Registration | Dagger `AppWorkerFactory` | Koin `CatalogWorkerFactory` |

---

### AOSP BuildNumberPreferenceController (10-tap inspiration)

- Source: https://cs.android.com/android/platform/superproject/+/main:packages/apps/Settings/src/com/android/settings/deviceinfo/BuildNumberPreferenceController.java
- Analysis: https://hossainkhan.medium.com/how-to-be-an-android-developer-with-just-7-taps-deep-dive-8abebfc07061

#### AOSP implementation

| Aspect | AOSP | Project spec |
|---|---|---|
| Tap count | **7 taps** | **10 taps** (user decision) |
| Countdown toast | Starts at tap 3 (shows "N more taps") | **No toast** (spec FR #2: silent) |
| Tap timeout | **None** (no reset) | **500ms reset** (spec FR #4) |
| Lock screen verify | Yes (modern Android) | N/A (pure app-level, no system) |
| Effect | Opens Android Developer Options | Sets `developer=100` in local Room |

#### Key AOSP logic (reference)

```java
// AOSP: TAPS_TO_BE_A_DEVELOPER = 7
private static final int TAPS_TO_BE_A_DEVELOPER = 7;

// Countdown: mDevHitCountdown from 7 down to 0
if (mDevHitCountdown > 0) {
    mDevHitCountdown--;
    if (mDevHitCountdown == 0) {
        // activate developer settings
        enableDevelopmentSettings();
    } else if (mDevHitCountdown <= (TAPS_TO_BE_A_DEVELOPER - 2)) {
        showToast(mDevHitCountdown + " steps away from developer mode");
    }
}
```

#### Project implementation difference

Project implements tap FSM in pure Kotlin domain logic:
- `registerTap(progress, currentTimeMillis, TAP_THRESHOLD = 10, RESET_INTERVAL_MS = 500)` at `qualification/domain/.../logic/RegisterTap.kt:34`
- Returns `TapResult.Activated`, `TapResult.NoChange`, `TapResult.Reset`, `TapResult.AlreadyDev`
- No toast at each tap — single `TapResult.Activated` → Snackbar "Dev mode activated"
- 500ms inactivity → `TapResult.Reset` (no AOSP equivalent)

---

## Breaking Changes Summary

| Library | Version | Breaking Change | Impact on project |
|---|---|---|---|
| Coil | 3.x vs 2.x | Artifact namespace `io.coil-kt.coil3`, `Fetcher.key` → `Keyer` interface | New library — no migration needed, but cannot mix with Coil 2 |
| Coil | 3.x | `coil-network` renamed to `coil-network-ktor` / `coil-network-okhttp` | Use `coil-network-okhttp` |
| Firebase BoM | 34+ | `-ktx` suffix removed from artifact names | Check BoM version before upgrade |
| WorkManager | 2.8+ | `ExistingPeriodicWorkPolicy.REPLACE` deprecated → use `UPDATE` | Use `KEEP` for periodic (safe), `REPLACE` still valid for one-time |
| Material3 | 1.3+ | `Modifier.menuAnchor()` parameterless deprecated | Use `menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)` |
| Koin | 4.2.0 | ABI break `runOnKoinStarted` | Use 4.2.1+ |
| Room KMP | 2.7 | Migrations use `SQLiteConnection` not `SupportSQLiteDatabase` | New — no migration needed |
| Room KMP | 2.7 + Kotlin 1.9.x | `kotlin.native.disableCompilerDaemon=true` required | Add to `gradle.properties` if native targets present |

## Known Issues Summary

| Library | Issue | Mitigation |
|---|---|---|
| Coil 3 | Issue #2551 — custom fetcher re-fetches on recomposition | Pre-resolve URL in Repository; pass `String` to `AsyncImage` |
| FireCoil | Unmaintained (Coil 2 only) | Write own `Fetcher.Factory<StorageReference>` or pre-resolve URL |
| Firebase Firestore | Suspend writes hang offline | Use `callbackFlow` for reactive; `get().await()` is safe for reads |
| Koin 4.2.0 | ABI break + stacked-params regression | Upgrade to 4.2.1 |
| WorkManager | `REPLACE` periodic policy deprecated | Use `KEEP` for periodic; `REPLACE` for one-time (still valid) |
| Room + Kotlin 1.9.x | KSP processor needs daemon disabled | `kotlin.native.disableCompilerDaemon=true` in `gradle.properties` |
