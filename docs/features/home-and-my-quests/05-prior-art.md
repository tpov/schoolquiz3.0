---
date: 2026-04-22
researcher: web-researcher
commit: 7c52c200
branch: kmp-skillify-4.0
---

# Prior Art: Home Quests & My Quests + Cascading Catalog Sync

SDK-исследование для feature `home-and-my-quests`. Каждый факт снабжён ссылкой на источник.

---

## 1. Firebase Firestore

### Official Docs
- https://firebase.google.com/docs/firestore/query-data/queries
- https://firebase.google.com/docs/firestore/query-data/indexing
- https://firebase.google.com/docs/firestore/security/rules-conditions
- https://firebase.google.com/docs/reference/kotlin/com/google/firebase/Timestamp

### Verified Facts

#### Delta Query Pattern (`where('lastModifiedAt', '>', cursor)`)
- Поле `lastModifiedAt` типа `Long` (Unix millis) нативно поддерживается Firestore как number — range фильтр `where("lastModifiedAt", ">", cursor)` работает как single-field index, регистрация не требуется.
- Для composite query с дополнительным equality filter (`where("authorUid", "==", uid)`) требуется composite index.
- Canonial Timestamp → Long конвертация: `timestamp.seconds * 1000L + timestamp.nanoseconds / 1_000_000L` — используй `1000L` (Long), чтобы избежать 32-bit overflow до умножения.
  - Source: https://firebase.google.com/docs/reference/kotlin/com/google/firebase/Timestamp
  - Source: https://medium.com/firebase-developers/the-secrets-of-firestore-fieldvalue-servertimestamp-revealed-29dd7a38a82b

#### Composite Indexes (7 индексов для фичи)
Spec требует 7 composite indexes (`0-spec.md:222-238`). Регистрация — Firebase Console → Firestore → Indexes → Composite → Create Index. Или через Firebase CLI: `firebase.indexes.json` + `firebase deploy --only firestore`.

Когда composite index отсутствует, Firestore логирует ошибку с прямой URL для создания — удобно при разработке. В production индексы должны быть задеплоены заранее.
- Source: https://firebase.google.com/docs/firestore/query-data/indexing
- Source: https://oneuptime.com/blog/post/2026-02-17-how-to-create-and-manage-composite-indexes-in-firestore/view

Для запроса `quests.where('visibleOn', 'array-contains-any', shelves).where('lastModifiedAt', '>', cursor)`:
```
Collection: quests
Fields:
  visibleOn    Arrays (array-contains-any)
  lastModifiedAt  Ascending
```

#### `array-contains-any` + `where-in` в одном запросе — ЗАПРЕЩЕНО
- Официальная документация: нельзя комбинировать `array-contains-any` и `in`/`not-in` в одном query. Попытка бросит исключение.
- Начиная с JS SDK v9.17.2 некоторые комбинации проходят без ошибки, но это баг, не фича.
- Max elements: `in` и `array-contains-any` — **30 значений** (с введением OR queries в Firebase; ранее было 10).
- **Вывод для spec**: два независимых Firebase-запроса (Query A + Query B) — корректный и единственный поддерживаемый workaround для `authorUid == uid` + `visibleOn array-contains-any`.
  - Source: https://github.com/firebase/firebase-js-sdk/issues/7147
  - Source: https://github.com/firebase/flutterfire/issues/11085

#### `FieldValue.serverTimestamp()` + Timestamp→Long
```kotlin
// WRITE
val data = hashMapOf("lastModifiedAt" to FieldValue.serverTimestamp())
db.collection("quests").add(data)

// READ — canonical Kotlin conversion
val firestoreTimestamp = document.getTimestamp("lastModifiedAt")
val lastModifiedAt: Long = firestoreTimestamp?.let {
    it.seconds * 1000L + it.nanoseconds / 1_000_000L
} ?: 0L
```
Важно: НЕ использовать `ServerValue.TIMESTAMP` — это Realtime Database API.
- Source: https://firebase.google.com/docs/reference/kotlin/com/google/firebase/firestore/ServerTimestamp
- Source: https://code.luasoftware.com/tutorials/google-cloud-firestore/firestore-server-timestamp/

#### Firestore Security Rules — Owner OR Public OR Admin
Паттерн для `quests` collection (owner or public read):
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /quests/{questId} {
      allow read: if request.auth != null &&
                    (resource.data.authorUid == request.auth.uid ||
                     resource.data.visibleOn.hasAny(['home', 'arena', 'tournament']));
      allow write: if request.auth != null &&
                     request.auth.uid == resource.data.authorUid;
    }
  }
}
```
Важно: **Security rules не являются фильтрами** — если query потенциально может вернуть документы, к которым у клиента нет доступа, весь запрос упадёт. Поэтому Query A (authorUid == uid) и Query B (visibleOn) запрашиваются раздельно — каждый соответствует своей rule.
- Source: https://firebase.google.com/docs/firestore/security/rules-query
- Source: https://firebase.google.com/docs/firestore/security/rules-conditions

#### Firestore Cost Control при Cascading Sync
- Cascade pattern создаёт N read-запросов за один sync. При 6 уровнях и 30 parentId per batch — в худшем случае много чтений.
- `contentsVersion` early-exit критичен: без него каждый sync делает reads для всех 6 уровней независимо от изменений.
- Firebase не заряжает за document reads которые не происходят (early-exit = экономия).
- Рекомендация: cursor-based delta (`lastModifiedAt > cursor`) минимизирует reads до только изменённых документов.
- Source: https://firebase.google.com/docs/firestore/query-data/queries

### Known Issues
- `array-contains-any` + `in` одновременно: баг в JS SDK v9.17.2+ (не задокументирован, не надёжно). Не использовать.
  - Source: https://github.com/firebase/firebase-js-sdk/issues/7147
- Composite index требует явной регистрации — без него runtime exception при первом query в production. Деплой через Firebase CLI предпочтительнее Console (версионируется в VCS).

### Best Practices
- Использовать `FieldValue.serverTimestamp()` для `lastModifiedAt` при write — server-authoritative, избегает clock skew.
- Composite indexes для всех multi-field queries регистрировать через `firestore.indexes.json` в VCS.
- Разделять Query A (author) и Query B (visibility) — оба совместимы со своими security rules.
- Batch limit 30 для `in` фильтров — chunks при > 30 parentId.

---

## 2. Coil 3.4.0

### Official Docs
- https://coil-kt.github.io/coil/changelog/
- https://coil-kt.github.io/coil/upgrading_to_coil3/
- https://coil-kt.github.io/coil/image_loaders/

### Verified Facts

#### Breaking Changes 3.1.0 → 3.4.0
1. **Java 11 bytecode requirement** (3.4.0): `coil-compose` и `coil-compose-core` требуют Java 11 из-за Compose 1.8.0.
   ```kotlin
   android {
       compileOptions {
           sourceCompatibility = JavaVersion.VERSION_11
           targetCompatibility = JavaVersion.VERSION_11
       }
   }
   ```
   Если проект уже настроен на Java 11 (стандарт для KMP) — изменений не требуется.

2. **`AsyncImagePainter.state` → `StateFlow`** (изменение из 3.0.0-alpha07): требует `collectAsState()` для observation. Проект использует `coil3.compose.AsyncImage` напрямую (не `AsyncImagePainter`) — изменение не затрагивает текущий `CatalogGrid.kt:71`.

3. **`modelEqualityDelegate` / `EqualityDelegate` removal** (3.0.0-rc02): параметр убран из `AsyncImage`. Управление через `LocalAsyncImageModelEqualityDelegate`. Текущий `CatalogGrid.kt:71` использует `AsyncImage(model = url)` без `modelEqualityDelegate` — **не задет**.

4. **`AsyncImagePreviewHandler` behavior** (3.4.0): функциональный конструктор теперь возвращает `State.Success` вместо `State.Loading` в preview. Влияет только на Compose Preview отображение, не на runtime.

5. **Binary incompatible changes** в `coil-network-cache-control` module — если не используется (проект не использует), изменение не затрагивает.

6. **Kotlin 2.2.0 + Compose 1.8.2** в 3.4.0 — потенциальное несовместимость если проект использует старый Kotlin. Надо проверить при bump.
   - Source: https://coil-kt.github.io/coil/changelog/
   - Source: https://coil-kt.github.io/coil/upgrading_to_coil3/

**Вывод**: Bump 3.1.0 → 3.4.0 для `CatalogGrid.kt` использования безопасен. Breaking changes не задевают `AsyncImage(model = url)` паттерн.

#### URL-Based Cache Invalidation (`?v={version}`)
- Coil 3 использует **полный URL string** (включая query params) как disk cache key по умолчанию.
- `https://example.com/img.jpg?v=1` и `?v=2` — разные cache entries → cache miss → network fetch.
- В 3.4.0 поведение не изменилось.
- Опциональный explicit control:
  ```kotlin
  ImageRequest.Builder(context)
      .data("https://storage.googleapis.com/quest-pictures/q-uuid.jpg?v=42")
      .diskCacheKey("https://storage.googleapis.com/quest-pictures/q-uuid.jpg?v=42")
      .build()
  ```
- Spec `0-spec.md` Decision #50: `pictureUrl = resolved + "?v=$version"` в `*RepositoryImpl` — корректный паттерн.
  - Source: https://coil-kt.github.io/coil/image_loaders/
  - Source: https://github.com/coil-kt/coil/discussions/1175

#### KMP Support (Android-only vs commonMain)
- Coil 3 — KMP library: поддерживает Android, JVM, iOS, macOS, JS, WASM.
- Зависимость `coil3-compose` может быть в `commonMain` для Compose Multiplatform проектов.
- Для Android-only Compose: `androidMain` dependency достаточна.
- Текущий проект использует `coil3.compose.AsyncImage` в `android/core/designsystem` (Android module) — остаётся в `androidMain`.
  - Source: https://coil-kt.github.io/coil/

#### Dependency (после bump)
```kotlin
implementation("io.coil-kt.coil3:coil-compose:3.4.0")
implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")
```

### Known Issues
- **Cache Control headers** не respектируются по умолчанию в Coil 3. Отдельный модуль `coil-network-cache-control` для этого. Проект использует `?v=` pattern — не затронуто.
- **File last write timestamp** больше не добавляется в cache key в Coil 3 — только если используешь `memoryCacheKeyExtra`. Для URL-based resources не имеет значения.

### Best Practices
- `?v={entity.version}` в URL — идиоматичный способ cache invalidation в Coil 3.
- Не использовать `AsyncImagePainter` напрямую без `collectAsState()` — state теперь `StateFlow`.
- `@Preview` обязателен для всех Composable в `android/core/designsystem/components/` (проверяет `BrandComponentsInvariantsTest`).

---

## 3. Decompose 3.x

### Official Docs
- https://arkivanov.github.io/Decompose/
- https://arkivanov.github.io/Decompose/component/scopes/
- https://arkivanov.github.io/Decompose/samples/

### Verified Facts

#### `ComponentContext` — основной контракт
- Каждый Component получает `ComponentContext` и делегирует ему через `ComponentContext by componentContext`.
- `ComponentContext` реализует: `Lifecycle`, `StateKeeper`, `InstanceKeeper`, `BackHandler`.
  - Source: https://medium.com/codandotv/kotlin-decompose-component-context-capabilities-in-depth-c70f25b33750

#### `instanceKeeper` — ViewModel-аналог
- `instanceKeeper.getOrCreate(key) { Handler(initialState) }` сохраняет инстанс через config changes (поворот экрана).
- НЕ выживает после process death — только config changes.
- `Instance` interface с методом `onDestroy()` для cleanup.
```kotlin
class DefaultMyQuestsComponent(
    componentContext: ComponentContext,
    private val authRepository: AuthRepository,
    private val observeMyQuests: ObserveMyQuestsUseCase,
) : MyQuestsComponent, ComponentContext by componentContext {

    private val handler = instanceKeeper.getOrCreate(HANDLER_KEY) {
        Handler(stateKeeper.consume(STATE_KEY, UiState.serializer()) ?: UiState.Loading)
    }

    init {
        stateKeeper.register(STATE_KEY, UiState.serializer()) { handler.state.value }
    }

    companion object {
        private const val HANDLER_KEY = "my_quests_handler"
        private const val STATE_KEY = "my_quests_state"
    }
}
```
  - Source: https://medium.com/@yeldar.nurpeissov/master-kotlin-multiplatform-with-decompose-part-3-restoring-state-with-instancekeeper-and-692df1615955

#### `coroutineScope` — lifecycle-aware (через Essenty)
- Decompose использует Essenty library для lifecycle utilities.
- `coroutineScope(mainContext + SupervisorJob())` — Extension function из `essenty:lifecycle-coroutines`.
- Scope **автоматически отменяется** при уничтожении компонента.
```kotlin
// Dependency:
// com.arkivanov.essenty:lifecycle-coroutines:2.0.0+

class DefaultMyQuestsComponent(
    componentContext: ComponentContext,
    mainContext: CoroutineContext = Dispatchers.Main,
) : MyQuestsComponent, ComponentContext by componentContext {

    private val scope = coroutineScope(mainContext + SupervisorJob())

    init {
        scope.launch {
            // lifecycle-aware collection
        }
    }
}
```
  - Source: https://arkivanov.github.io/Decompose/component/scopes/
  - Source: https://gist.github.com/aartikov/a56cc94bb306e05b7b7927353910da08

#### Koin DI Integration — Factory Pattern
- Бизнес-зависимости (repositories, use cases) — из Koin.
- `ComponentContext` и navigation-специфичные params — передаются явно из parent component.
- Паттерн: Component.Factory interface, Koin предоставляет Factory через `factory { }`.
```kotlin
class DefaultMyQuestsComponent(
    componentContext: ComponentContext,
    private val authRepository: AuthRepository,       // from Koin
    private val observeMyQuests: ObserveMyQuestsUseCase, // from Koin
    private val onCreateQuestClick: () -> Unit,       // from parent
) : MyQuestsComponent, ComponentContext by componentContext {

    // Component.Factory для Koin:
    class Factory(
        private val authRepository: AuthRepository,
        private val observeMyQuests: ObserveMyQuestsUseCase,
    ) {
        fun create(
            componentContext: ComponentContext,
            onCreateQuestClick: () -> Unit,
        ): MyQuestsComponent = DefaultMyQuestsComponent(
            componentContext, authRepository, observeMyQuests, onCreateQuestClick
        )
    }
}

// Koin module:
val myQuestsPresentationModule = module {
    factory { MyQuestsComponent.Factory(get(), get()) }
}
```
  - Source: https://medium.com/@yeldar.nurpeissov/master-kotlin-multiplatform-navigation-with-decompose-add-di-with-kodein-and-koin-405462b2691b

#### `StackNavigation` — push/pop внутри вкладки
- `StackNavigation<LocalConfig>` через `DefaultLocalTabComponent` уже используется в проекте.
- `navigation.push(LocalConfig.QuestCreateRoot)` добавляет в стек.
- Back (`AppShellScreen.kt:127` `BackCallback`) → `navigation.pop()`.

### Known Issues
- `Dispatchers.Main` может быть недоступен на JVM Desktop по умолчанию. Для JVM тестов `Dispatchers.Default` или `UnconfinedTestDispatcher`.
- `InstanceKeeper` НЕ выживает process death — только `StateKeeper` (через `onSaveInstanceState` аналог).
- Koin Wasm alpha: runtime errors на WASM targets (не применимо к Android-only проекту).

### Best Practices
- Инжектировать `mainContext: CoroutineContext` вместо хардкода `Dispatchers.Main` — testability.
- Для state persistence через process death: `stateKeeper.register` + `stateKeeper.consume` пара.
- Component пары (Component + Composable Screen) — standard pattern, соответствует existing `HomeQuestsComponent` + `HomeQuestsScreen`.

---

## 4. Room KMP + KSP

### Official Docs
- https://developer.android.com/kotlin/multiplatform/room
- https://developer.android.com/training/data-storage/room/migrating-db-versions
- https://developer.android.com/jetpack/androidx/releases/room

### Verified Facts

#### `kspJvm` — обязательна при наличии JVM target
- Если в `kotlin { jvm() }` target объявлен JVM, то KSP config ДОЛЖЕН включать `kspJvm`:
  ```kotlin
  dependencies {
      add("kspAndroid", "androidx.room:room-compiler:$roomVersion")
      add("kspJvm", "androidx.room:room-compiler:$roomVersion") // required for JVM target!
  }
  ```
- Без `kspJvm` — `Configuration with name 'kspJvm' not found` error при sync.
- Проект `shared/core/persistence/build.gradle.kts:39` имеет только `add("kspAndroid", ...)` — если `jvm()` target присутствует, нужно добавить `kspJvm`. [DISCREPANCY — подтвердить с grounding]
  - Source: https://developer.android.com/kotlin/multiplatform/room
  - Source: https://medium.com/@hgarcia.alberto/implementing-room-database-in-kotlin-multiplatform-ksp2-koin-aac564da2d4f

#### `@TypeConverter` для `Set<String>` (`Quest.visibleOn`) в KMP
- Рекомендуемый подход: `@ProvidedTypeConverter` + `kotlinx.serialization`.
- `@ProvidedTypeConverter` означает что Koin/DI предоставляет инстанс конвертера (не Room создаёт через reflection).
```kotlin
@ProvidedTypeConverter
class StringSetConverter {
    @TypeConverter
    fun fromStringSet(value: Set<String>): String = Json.encodeToString(value.toList())

    @TypeConverter
    fun toStringSet(value: String): Set<String> = Json.decodeFromString<List<String>>(value).toSet()
}

// Регистрация в AppDatabase:
@Database(entities = [...], version = 2)
@TypeConverters(StringSetConverter::class)
abstract class AppDatabase : RoomDatabase() { ... }

// Koin — передать в builder:
Room.databaseBuilder(...).addTypeConverter(StringSetConverter()).build()
```
- Новый nullable-aware TypeConverter analyzer в Room 2.7+: рекомендует non-null converters, Room оборачивает null check автоматически.
  - Source: https://developer.android.com/kotlin/multiplatform/room
  - Source: https://www.nmvasani.com/post/using-room-database-in-compose-multiplatform-kmp-cmp-a-step-by-step-guide-by-nimesh-vasani

#### `fallbackToDestructiveMigration()` для pre-production schema bump 1→2
- Механизм: при несовпадении версии schema Room бросает `IllegalStateException`. `fallbackToDestructiveMigration()` перехватывает — удаляет DB, создаёт заново.
- Паттерн для pre-production (нет ценных пользовательских данных):
  ```kotlin
  Room.databaseBuilder(context, AppDatabase::class.java, "schoolquiz.db")
      .fallbackToDestructiveMigration()
      .build()
  ```
- Bump version: `@Database(..., version = 2)` — достаточно.
- Варианты: `fallbackToDestructiveMigrationFrom(1)` — деструктивно только при миграции с версии 1.
  - Source: https://developer.android.com/training/data-storage/room/migrating-db-versions
  - Source: https://carrion.dev/en/posts/room-in-kmp/

#### Schema Bump 1→2 Pattern для этой фичи
```kotlin
// AppDatabase.kt:
@Database(
    entities = [
        UserStatsEntity::class,
        CatalogEntity::class,     // extended: +4 fields
        QuestEntity::class,       // new
        SectionEntity::class,     // new
        ThemeEntity::class,       // new
        LessonEntity::class,      // new
        QuestionEntity::class,    // new
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(StringSetConverter::class)
abstract class AppDatabase : RoomDatabase() { ... }
```

#### KSP2 + Kotlin 2.0
- Room 2.7+ совместима с KSP2 (рекомендована для Kotlin 2.0+).
- Плагин: `id("com.google.devtools.ksp") version "2.x.y-1.x.z"`.
  - Source: https://developer.android.com/jetpack/androidx/releases/room

### Known Issues
- Room 3.0 (март 2026): новый major версия, новые maven координаты (`androidx.room3:...`), только Kotlin codegen. Не затрагивает этот проект (использует Room 2.7.x).
  - Source: https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html
- `BundledSQLiteDriver` увеличивает binary size на Android и iOS — использовать только если нужен iOS support. Проект Android-only → `AndroidSQLiteDriver` (стандартный).

### Best Practices
- Всегда добавлять `kspJvm` при наличии JVM target в KMP module.
- `@ProvidedTypeConverter` + kotlinx-serialization для коллекций — стандарт для KMP (нет reflection issues).
- `exportSchema = true` + `schemas/` директория для Room migration history — уже в проекте.
- `fallbackToDestructiveMigration()` для pre-production; для production — писать явные `Migration` объекты.

---

## 5. WorkManager — Cascading Orchestration

### Official Docs
- https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- https://developer.android.com/topic/libraries/architecture/workmanager

### Verified Facts

#### Sequential Steps (6 уровней) — CoroutineWorker + internal orchestration
- WorkManager chains (`WorkContinuation`) создают DAG из отдельных Workers. Для cascading sync это избыточно — лучше один `CoroutineWorker` с 6 sequential steps внутри `doWork()`.
- `CoroutineWorker.doWork()` — `suspend fun`, supports structured concurrency.
- Данные между шагами через in-memory state (или `SyncStateRepository`) — не через WorkManager `Data` (ограничение 10KB).
  - Source: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work

```kotlin
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val orchestrator: CascadingSyncOrchestrator,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            orchestrator.run()
            Result.success()
        } catch (e: NetworkException) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val MAX_RETRIES = 3
    }
}
```

#### `Result.retry()` + Exponential Backoff
- По умолчанию WorkManager exponential backoff: 30s initial, удваивается.
- Явная настройка через `setBackoffCriteria(BackoffPolicy.EXPONENTIAL, minimumBackoffMillis, TimeUnit.MILLISECONDS)`.
- **WorkManager НЕ ограничивает количество retry** — нужно проверять `runAttemptCount` вручную.
```kotlin
val request = OneTimeWorkRequestBuilder<SyncWorker>()
    .setConstraints(Constraints(NetworkType.CONNECTED))
    .setBackoffCriteria(
        BackoffPolicy.EXPONENTIAL,
        WorkRequest.MIN_BACKOFF_MILLIS,
        TimeUnit.MILLISECONDS
    )
    .build()
```
  - Source: https://proandroiddev.com/android-workmanager-a-complete-technical-deep-dive-f037c768d87b
  - Source: https://github.com/googlecodelabs/android-workmanager/issues/63

#### In-Memory Cursor State — Process Death Behavior
- `InMemorySyncStateRepository` использует `MutableStateFlow<Map<String, Long>>` для курсоров.
- При process death WorkManager убивает процесс → все in-memory state теряется.
- Следующий запуск WorkManager-а (при восстановлении) → cursor = 0 → full re-sync.
- Upsert-by-id идемпотентен → нет дубликатов. Spec `0-spec.md:57` явно принимает это поведение для phase-01.
- Fail-fast (любой шаг с ошибкой → `Result.retry()`) vs partial retry: для cascading sync fail-fast корректнее, иначе orphan state (quest без section).
  - Source: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work

#### Idempotency Guarantee при Re-Sync
- При cursor=0 → `fetchChangedSince(0)` → возвращает все документы.
- Room upsert (`INSERT OR REPLACE` / `OnConflictStrategy.REPLACE`) по id → безопасный повтор.
- Cursor обновляется **только при успешном завершении шага** — partial success не advance cursor.

### Known Issues
- `runAttemptCount` не сбрасывается между WorkManager enqueueing если использовать `KEEP` policy — нужно `REPLACE` для manual sync или `APPEND_OR_REPLACE`.
- Periodic WorkRequest с `setBackoffCriteria` — backoff применяется только если Worker возвращает `Result.retry()`, не при network constraint failures (WorkManager ждёт constraint satisfaction автоматически).

### Best Practices
- Один `CoroutineWorker` с внутренними sequential steps предпочтительнее chain из 6 Workers для cascading sync — меньше overhead, cursor можно держать in-memory.
- `runAttemptCount < MAX_RETRIES` guard в `doWork()` — предотвращает infinite retry loop.
- Enqueue с `ExistingWorkPolicy.REPLACE` для manual sync (кнопка SyncNow) — отменяет предыдущий pending request.
- Periodic sync с `ExistingPeriodicWorkPolicy.KEEP` — не дублирует если уже запланирован.
- `setBackoffCriteria(EXPONENTIAL, MIN_BACKOFF_MILLIS, ...)` — явно задаёт backoff, не полагается на default.

---

## Open Questions

1. `kspJvm` в `shared/core/persistence/build.gradle.kts` — подтвердить наличие или отсутствие. Research показывает что НУЖЕН если `jvm()` target объявлен. Grounding отмечает это как `uncertain` (`1-research.md:377`).

2. Coil 3.4.0 требует Kotlin 2.2.0 — подтвердить что проект использует Kotlin 2.x (вероятно уже, т.к. KMP + KSP2). Если нет — либо остаться на 3.1.0 либо bump Kotlin.

3. Firestore `array-contains-any` лимит 30 (с OR queries SDK) — нужно проверить версию Firebase Android SDK в проекте. Если SDK < версии с OR queries support → лимит остаётся 10.

4. Room 3.0 (новые maven координаты `androidx.room3:...`) — не применимо сейчас, но при upgrade monitoring нужен.

---

## Reference Implementations

| SDK | Reference | URL |
|-----|-----------|-----|
| Firestore delta sync | Firebase documentation | https://firebase.google.com/docs/firestore/query-data/queries |
| Coil 3 cache key | Official docs | https://coil-kt.github.io/coil/image_loaders/ |
| Decompose + Koin | Medium article (Part 2) | https://medium.com/@yeldar.nurpeissov/master-kotlin-multiplatform-navigation-with-decompose-add-di-with-kodein-and-koin-405462b2691b |
| Decompose instanceKeeper | Medium article (Part 3) | https://medium.com/@yeldar.nurpeissov/master-kotlin-multiplatform-with-decompose-part-3-restoring-state-with-instancekeeper-and-692df1615955 |
| Room KMP + KSP | Android Developers | https://developer.android.com/kotlin/multiplatform/room |
| WorkManager backoff | Android Developers | https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work |
| Decompose coroutineScope | Essenty gist | https://gist.github.com/aartikov/a56cc94bb306e05b7b7927353910da08 |
| Firestore Security Rules | Firebase docs | https://firebase.google.com/docs/firestore/security/rules-conditions |
