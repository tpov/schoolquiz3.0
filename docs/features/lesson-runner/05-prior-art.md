---
date: 2026-04-26
researcher: web-researcher
sources: Context7 MCP + WebSearch (official docs, GitHub issues, Stack Overflow, Medium)
---

# Prior Art: SDK Best Practices for lesson-runner

## Summary

Критическое ограничение: `retainingInstance{}` property delegate недоступен в Decompose 3.1.0 / Essenty 2.1.0 — введён только в 3.2.0-alpha02. Единственный паттерн для RunnerStateHolder — `instanceKeeper.getOrCreate(key) { Holder() }`. Essenty `lifecycle.doOnStop{}` / `doOnResume{}` доступны с `isOneTime` параметром. `DialogProperties.securePolicy = SecureFlagPolicy.SecureOn` — встроенная альтернатива к `DisposableEffect + FLAG_SECURE` для диалогов. Koin function-type binding через `() -> T` ненадёжен из-за JVM type erasure — обязателен wrapper `fun interface`.

---

## SDK 1: Decompose 3.1.0 — instanceKeeper pattern

### Source
- official: https://arkivanov.github.io/Decompose/component/instance-retaining/
- issue #627: https://github.com/arkivanov/Decompose/issues/627
- InstanceKeeperExt.kt: https://github.com/arkivanov/Essenty/blob/master/instance-keeper/src/commonMain/kotlin/com/arkivanov/essenty/instancekeeper/InstanceKeeperExt.kt

### Recommended pattern (available in 3.1.0)
```kotlin
// Единственный поддерживаемый паттерн в Decompose 3.1.0:
private val holder = instanceKeeper.getOrCreate(HOLDER_KEY) {
    RunnerStateHolder(seed = seed, deadline = deadline)
}

// Holder должен реализовывать InstanceKeeper.Instance
class RunnerStateHolder(
    val seed: Long,
    val deadline: Long,
) : InstanceKeeper.Instance {
    val state = MutableStateFlow<RunnerState>(RunnerState.Loading)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onDestroy() {
        scope.cancel()
    }
}
```

### retainingInstance{} delegate — НЕ ДОСТУПЕН в 3.1.0
```kotlin
// ❌ НЕ доступно в Decompose 3.1.0 / Essenty 2.1.0:
// private val holder by retainingInstance { RunnerStateHolder() }
//
// Annotated @ExperimentalInstanceKeeperApi, введён в 3.2.0-alpha02.
// В Essenty 2.1.0 функция называется retainingInstance (не retainedInstance),
// но НЕ доступна через property delegate синтаксис в 3.1.0.
```

### pushNew — требует @OptIn
```kotlin
@OptIn(ExperimentalDecomposeApi::class)
navigation.pushNew(QuizzesConfig.LessonRunner(lessonId = id, mode = mode))
// pushNew НЕ делает ничего если конфиг уже на вершине стека (идемпотентен).
// push() — всегда добавляет, даже дублируя.
```

### ChildStack + SerializableContainer
```kotlin
// saveState / restoreState доступны в generic children() API:
children(
    source = navigation,
    saveState = { container: SerializableContainer -> ... },
    restoreState = { state: SerializableContainer -> ... },
    ...
)
// Восстановленное состояние обязано иметь то же количество дочерних конфигураций в том же порядке.
```

### Known issues / boundaries
- **Issue #627**: `InstanceKeeper` изолирован per-component начиная с 2.2.2 — InstanceKeeper родителя НЕ наследуется дочерними. Это намеренное поведение.
- `InstanceKeeper` переживает Android configuration change (rotation), НО уничтожается при pop из back stack — это ОЖИДАЕМО.
- `InstanceKeeper.Instance.onDestroy()` = аналог `ViewModel.onCleared()`.
- Для выживания при process death — комбинировать с `stateKeeper.consume`/`stateKeeper.register`.

### Application к нашей фиче
- `RunnerStateHolder : InstanceKeeper.Instance` — держит `MutableStateFlow<RunnerState>`, seed, deadline
- При pop из back stack (выход из урока) `onDestroy()` вызовется корректно → scope отменяется
- При rotation state сохраняется автоматически
- `retainingInstance{}` — **НЕДОСТУПЕН**, использовать `getOrCreate(key) { ... }`

---

## SDK 2: Essenty 2.1.0 — lifecycle extensions

### Source
- LifecycleExt.kt: https://github.com/arkivanov/Essenty/blob/master/lifecycle/src/commonMain/kotlin/com/arkivanov/essenty/lifecycle/LifecycleExt.kt
- docs: https://arkivanov.github.io/Decompose/component/lifecycle/

### Доступные extension functions
```kotlin
// Сигнатуры (из LifecycleExt.kt, Essenty 2.1.0):

// doOnCreate / doOnDestroy — БЕЗ isOneTime параметра:
inline fun Lifecycle.doOnCreate(crossinline block: () -> Unit)
inline fun Lifecycle.doOnDestroy(crossinline block: () -> Unit)

// doOnStart, doOnResume, doOnPause, doOnStop — С isOneTime параметром:
inline fun Lifecycle.doOnStart(isOneTime: Boolean = false, crossinline block: () -> Unit)
inline fun Lifecycle.doOnResume(isOneTime: Boolean = false, crossinline block: () -> Unit)
inline fun Lifecycle.doOnPause(isOneTime: Boolean = false, crossinline block: () -> Unit)
inline fun Lifecycle.doOnStop(isOneTime: Boolean = false, crossinline block: () -> Unit)

// LifecycleOwner convenience wrappers тоже существуют — делегируют к Lifecycle.doOn*()
```

### Применение в Component
```kotlin
class DefaultLessonRunnerComponent(
    componentContext: ComponentContext,
    ...
) : ComponentContext by componentContext {

    init {
        // FR#14: auto-random fill при уходе в background
        lifecycle.doOnStop {
            autoFillUnansweredQuestions()
        }

        // FR#15: показать blocking dialog при resume (если попытка ещё активна)
        lifecycle.doOnResume {
            if (shouldShowResumeDialog()) showResumeBlockingDialog()
        }
    }
}
```

### Known issues — Issue #627 анализ
- Issue #627 зарегистрирован в репозитории **Decompose**, а не Essenty
- Суть: InstanceKeeper перестал наследоваться дочерними компонентами после 2.2.2
- **Применимость к нашему кейсу onStop auto-fill + onResume dialog**: НЕ применимо. `lifecycle.doOnStop{}` / `doOnResume{}` работают на уровне самого Component, не связаны с InstanceKeeper inheritance. Паттерн безопасен.
- Lifecycle callbacks `doOnStop`/`doOnResume` — стабильный API, нет известных issues в 2.1.0

---

## SDK 3: kotlinx.serialization 1.7.3 — sealed class polymorphic parsing

### Source
- official polymorphism docs: https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md
- JsonClassDiscriminator API: https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-json/kotlinx.serialization.json/-json-class-discriminator/

### Pattern для QuestionContent (4 subtypes)
```kotlin
// Базовый sealed class с кастомным discriminator key:
@Serializable
@JsonClassDiscriminator("kind")  // кастомный ключ (default = "type")
sealed class QuestionContent

@Serializable
@SerialName("single_choice")  // значение discriminator
data class SingleChoiceContent(
    val options: List<Option>,
    val correctOptionId: String,
) : QuestionContent()

@Serializable
@SerialName("multiple_choice")
data class MultipleChoiceContent(...) : QuestionContent()

@Serializable
@SerialName("ordering")
data class OrderingContent(...) : QuestionContent()

@Serializable
@SerialName("fill_blank")
data class FillBlankContent(...) : QuestionContent()
```

### Json builder configuration
```kotlin
val json = Json {
    ignoreUnknownKeys = true   // forward compatibility
    encodeDefaults = true      // сериализовать поля с default values
    classDiscriminator = "type" // global default; @JsonClassDiscriminator переопределяет per-hierarchy
}
```

### Known issues / boundaries
- Sealed class: `SerializersModule` НЕ нужен — автоматическая регистрация подклассов
- Open/abstract class: обязателен явный `SerializersModule` с `polymorphic { subclass(...) }`
- `@JsonClassDiscriminator` — `@InheritableSerialInfo`, достаточно аннотировать базовый класс
- Нельзя задавать разные дискриминаторы в разных частях одной иерархии
- `classDiscriminator` на `Json` builder — глобальный; `@JsonClassDiscriminator` — per-hierarchy override

### Application к нашей фиче
- `QuestionContent` sealed hierarchy: annotate с `@JsonClassDiscriminator("kind")` на базовом
- Все 4 подтипа: `@SerialName` + `@Serializable`
- Парсер `KotlinxSerializationQuestionContentParser` использует этот Json instance
- Рекомендация из `1-research.md` §483: parser лучше в `shared/core/question-schema/`

---

## SDK 4: Coil 3 (3.4.0) — ImageLoader singleton

### Source
- official getting started: https://coil-kt.github.io/coil/getting_started/
- image loaders: https://coil-kt.github.io/coil/image_loaders/

### Default behavior (MVP sufficient)
```kotlin
// Для MVP: ничего делать не надо — Coil 3 создаёт singleton автоматически.
// AsyncImage { ... } использует синглтон без конфигурации.
```

### Кастомизация (если нужна для top-3 avatars + question images)
```kotlin
// Android-only app: implements SingletonImageLoader.Factory на Application
class AppApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)  // 2% disk — достаточно для avatars + question images
                    .build()
            }
            .crossfade(true)
            .build()
    }
}

// KMP / Compose Multiplatform: setSingletonImageLoaderFactory у root composable
setSingletonImageLoaderFactory { context ->
    ImageLoader.Builder(context).crossfade(true).build()
}
```

### Known issues / boundaries
- `setSingletonImageLoaderFactory` нельзя вызывать внутри `LaunchedEffect` — только до первого использования
- Каждый `ImageLoader` имеет свой memory cache + disk cache → не создавать несколько инстансов
- Disk cache default: включён, директория — `context.cacheDir/image_cache`
- Зависимости в `libs.versions.toml`: `coil3-compose`, `coil3-core`, `coil3-network-okhttp` — все уже объявлены

### Application к нашей фиче
- MVP: дополнительных изменений не требуется — синглтон создаётся автоматически
- Если нужны URL images для вопросов — убедиться что `coil3-network-okhttp` включён в модуль presentation

---

## SDK 5: Material 3 BOM 2024.09.02 — Fullscreen blocking dialog + FLAG_SECURE

### Source
- DialogProperties: https://developer.android.com/reference/kotlin/androidx/compose/ui/window/DialogProperties
- FLAG_SECURE in Compose: https://tomasrepcik.dev/blog/2023/2023-12-09-android-securing-screen/
- SecureFlagPolicy: https://developer.android.com/reference/kotlin/androidx/compose/ui/window/DialogProperties

### Fullscreen blocking dialog (onResume — FR#15)
```kotlin
@Composable
fun ResumeBlockingDialog(onContinue: () -> Unit) {
    Dialog(
        onDismissRequest = { /* заблокировано */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            // Content + "Продолжить" button → onContinue()
        }
    }
}
```

### FLAG_SECURE (HARD mode) через DisposableEffect
```kotlin
@Composable
fun SecureScreenEffect(isHardMode: Boolean) {
    if (!isHardMode) return
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

// Helper для надёжного получения Activity:
fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
```

### FLAG_SECURE через SecureFlagPolicy (для Dialog)
```kotlin
// Альтернатива для Dialog-only case — не нужен отдельный DisposableEffect:
DialogProperties(
    securePolicy = SecureFlagPolicy.SecureOn,  // встроено в DialogProperties
    dismissOnBackPress = false,
    dismissOnClickOutside = false,
    usePlatformDefaultWidth = false,
)
```

### Known issues / boundaries
- `SecureFlagPolicy.Inherit` (default) — наследует флаг от hosting window
- `SecureFlagPolicy.SecureOn` / `SecureOff` — явное управление
- `ModalBottomSheet` рендерится в отдельном window — FLAG_SECURE на Activity window НЕ применяется к нему
- Android 13+ (API 33): `setRecentsScreenshotEnabled(false)` — purpose-built альтернатива без race condition
- Race condition: `onPause()` подход ненадёжен (нет sync гарантии с системным UI)
- `DisposableEffect` гарантирует cleanup в `onDispose` → безопасно при rotation

### Application к нашей фиче
- HARD mode: `SecureScreenEffect(isHardMode = state.isHardMode)` рядом с RunnerScreen
- onResume dialog: `ResumeBlockingDialog` с `dismissOnBackPress=false, dismissOnClickOutside=false`
- Оба паттерна НЕ требуют изменений в Activity / AndroidManifest

---

## SDK 6: Compose Foundation — Timer countdown без drift

### Source
- deadline pattern: https://medium.com/@mahbooberezaee68/timer-with-launchedeffect-in-jetpack-compose-22bd2c94552b
- monotonic clock: https://www.antondanshin.com/blog/compose-timer-implementation/

### Recommended pattern (deadline + monotonic clock)
```kotlin
// В Component (не в Composable — логика в Component):
fun startTimer(totalMillis: Long) {
    val deadline = SystemClock.elapsedRealtime() + totalMillis
    componentScope.launch {
        while (isActive) {
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0L) {
                _state.update { it.copy(timeRemainingMs = 0L, isTimedOut = true) }
                break
            }
            _state.update { it.copy(timeRemainingMs = remaining) }
            delay(100L) // 100ms тики — достаточно для UI обновления
        }
    }
}

// Или в Composable (если логика в UI layer):
val deadline = remember { SystemClock.elapsedRealtime() + totalMillis }
LaunchedEffect(Unit) {
    while (isActive) {
        val remaining = deadline - SystemClock.elapsedRealtime()
        if (remaining <= 0L) { onTimedOut(); break }
        onTick(remaining)
        delay(100L)
    }
}
```

### Сравнение clock sources
| Source | Монотонный | Переживает deep sleep | Рекомендуется для |
|--------|-----------|----------------------|-------------------|
| `System.currentTimeMillis()` | ❌ (NTP прыжки) | ✅ | — |
| `SystemClock.uptimeMillis()` | ✅ | ❌ (сбрасывается при deep sleep) | quiz timer |
| `SystemClock.elapsedRealtime()` | ✅ | ✅ | quiz timer (preferred) |
| `withFrameMillis()` | ✅ | ✅ | animation-синхронизированный |

### Application к нашей фиче
- Таймер живёт в `RunnerStateHolder` (instanceKeeper), не в Composable — переживает rotation
- `deadline: Long` = `elapsedRealtime() + totalMillis` сохраняется в `stateKeeper` для process death
- При resume из background: `remaining = deadline - elapsedRealtime()` — автоматическая коррекция

---

## SDK 7: sh.calvin.reorderable 3.1.0 — Drag-and-drop Ordering

### Source
- GitHub: https://github.com/Calvin-LL/Reorderable
- README: https://github.com/Calvin-LL/Reorderable/blob/main/README.md

### Зависимость
```toml
# libs.versions.toml (добавить если используется):
[versions]
reorderable = "3.1.0"

[libraries]
reorderable = { module = "sh.calvin.reorderable:reorderable", version.ref = "reorderable" }
```

### Минимальная интеграция LazyColumn
```kotlin
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

val lazyListState = rememberLazyListState()
val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
    items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
    hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
}

LazyColumn(state = lazyListState) {
    items(items, key = { it.id }) { item ->
        ReorderableItem(reorderableState, key = item.id) { isDragging ->
            val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
            Surface(shadowElevation = elevation) {
                Row {
                    Text(item.text)
                    IconButton(
                        modifier = Modifier.draggableHandle()
                    ) {
                        Icon(Icons.Rounded.DragHandle, contentDescription = "Перетащить")
                    }
                }
            }
        }
    }
}
```

### Known issues / boundaries
- Не требует кастомного LazyColumn replacement — работает со стандартным
- Использует `Modifier.animateItem` (доступен в Compose Foundation BOM 2024.09.02+)
- Поддерживает Compose Multiplatform (Android, iOS, Desktop/JVM, Wasm, JS)
- Accessibility: явных accessibility guidelines не задокументировано — для TalkBack рассмотреть up/down IconButton как дополнение

### Application к нашей фиче (Ordering question type)
- Альтернатива: up/down `IconButton` — нулевые зависимости, лучший accessibility
- Рекомендация из `1-research.md` §328: up/down arrows для accessibility; reorderable — опционально
- Если используется reorderable: добавить в `android/feature/lesson-runner/presentation/build.gradle.kts`

---

## SDK 8: Koin 3.5.6 — Function type binding

### Source
- Koin issue #216: https://github.com/InsertKoinIO/koin/issues/216
- JVM type erasure: https://stackify.com/jvm-generics-type-erasure/

### Проблема с function type binding
```kotlin
// ❌ НЕ РАБОТАЕТ — BeanOverrideException при нескольких () -> T bindingах:
single<() -> AttemptId> { { AttemptId(UUID.randomUUID().toString()) } }
single<() -> Long> { { System.currentTimeMillis() } }
// Koin видит оба как Function0, throws BeanOverrideException
```

### Рекомендуемый паттерн: fun interface wrapper
```kotlin
// ✅ Wrapper fun interface — уникальный JVM тип:
fun interface AttemptIdProvider {
    fun next(): AttemptId
}

fun interface TimestampProvider {
    fun now(): Long
}

// В Koin module:
val lessonRunnerDataModule = module {
    single<AttemptIdProvider> { AttemptIdProvider { AttemptId(UUID.randomUUID().toString()) } }
    single<TimestampProvider> { TimestampProvider { System.currentTimeMillis() } }
    // ... остальные bindings
}

// Внедрение в constructor:
class LessonAttemptRepositoryImpl(
    private val attemptIdProvider: AttemptIdProvider,
    private val timestampProvider: TimestampProvider,
    ...
)
```

### Альтернативы
| Подход | Работает? | Недостаток |
|--------|-----------|------------|
| `single<() -> T>` | ❌ | Type erasure → BeanOverrideException |
| `typealias FooProvider = () -> Foo` | ❌ | Typealias не создаёт новый JVM тип |
| `fun interface FooProvider { fun next(): Foo }` | ✅ | Verbose, но надёжно |
| `named("attemptId")` qualifier | ✅ | Less type-safe, inject by name |
| Module parameter функция | ✅ | `lessonRunnerDataModule(idProvider: () -> AttemptId, ...)` |

### Application к нашей фиче
- Open Question #6 из `1-research.md`: Recommendation C (wrapper interface) подтверждён исследованием
- `AttemptIdProvider`, `TimestampProvider`, `RatingIdProvider` — три отдельных fun interface
- Paтtern уже используется в `appShellDataModule` (module parameter approach) — wrapper interface более explicit

---

## SDK 9: Room KMP 2.7.0 — ProvidedTypeConverter + Migration

### Source
- official KMP docs: https://developer.android.com/kotlin/multiplatform/room
- migration codelab: https://developer.android.com/codelabs/kmp-migrate-room

### @ProvidedTypeConverter pattern (обязателен для KMP)
```kotlin
// В commonMain — определить converter:
@ProvidedTypeConverter
class DifficultyTypeConverter {
    @TypeConverter
    fun fromDifficulty(difficulty: Difficulty): String = difficulty.name

    @TypeConverter
    fun toDifficulty(value: String): Difficulty = Difficulty.valueOf(value)
}

// В platform-specific builder (androidMain):
fun buildDatabase(context: Context): AppDatabase {
    return Room.databaseBuilder(context, AppDatabase::class.java, "app_db")
        .addTypeConverter(DifficultyTypeConverter())  // обязательно .addTypeConverter()
        .addMigrations(MIGRATION_3_4)
        .build()
}
```

### Migration pattern (commonMain)
```kotlin
// В commonMain — Migration определяется там же:
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Новые таблицы:
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS lesson_attempts (
                id TEXT NOT NULL PRIMARY KEY,
                lesson_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                score INTEGER NOT NULL DEFAULT 0,
                stars INTEGER NOT NULL DEFAULT 0,
                completed_at INTEGER NOT NULL
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS lesson_rating_submitted_local (
                lesson_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                rating INTEGER NOT NULL,
                PRIMARY KEY (lesson_id, user_id)
            )
        """)
        // ALTER TABLE для существующих таблиц:
        db.execSQL("ALTER TABLE lessons ADD COLUMN average_rating REAL")
        db.execSQL("ALTER TABLE lessons ADD COLUMN rating_count INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE lessons ADD COLUMN top3 TEXT NOT NULL DEFAULT '[]'")
    }
}
```

### Known issues / boundaries
- `@ProvidedTypeConverter` — обязателен в KMP; без него Room KSP не генерирует код для converter
- `.addTypeConverter(instance)` в builder — иначе RuntimeException при старте
- **Existing project pattern**: `StringSetConverter` + `PersistenceModule.kt:24` — уже используется
- DAO функции в KMP должны быть `suspend` (кроме androidMain для backward compat)
- `setQueryExecutor()` — **недоступен в commonMain** KMP; использовать `setCoroutineContext(Dispatchers.IO)`
- `BundledSQLiteDriver` — для cross-platform тестирования без Android instrumented tests

### Application к нашей фиче
- `DifficultyTypeConverter` + `ListStringConverter` (для `top3: List<String>`) — новые converters
- Migration 3→4: CREATE TABLE для `lesson_attempts`, `lesson_rating_submitted_local`; ALTER TABLE для `lessons`
- Текущее состояние: `fallbackToDestructiveMigration(dropAllTables = true)` — **данные пользователя потеряются**
- Рекомендация: написать реальную Migration(3,4) для production (Open Question #5 из research)

---

## Open Questions for Architects

- [ ] **CRITICAL**: `retainingInstance{}` delegate НЕ доступен в Decompose 3.1.0. `instanceKeeper.getOrCreate(key) { ... }` — единственный паттерн. RunnerStateHolder должен реализовывать `InstanceKeeper.Instance` с `onDestroy()`. Подтверждаете подход?
- [ ] `SystemClock.elapsedRealtime()` — Android-only API (`android.os.SystemClock`). Если таймер живёт в KMP shared module, нужен expect/actual для monotonic clock. Где живёт таймерная логика — в domain (expect/actual) или в Android presentation Component?
- [ ] Issue #627: InstanceKeeper уничтожается при pop из back stack. Это ожидаемо для RunnerStateHolder — при выходе из урока state нужно дропнуть. Подтвердите что это не конфликтует с дизайном.
- [ ] `sh.calvin.reorderable` vs up/down arrows: если используется reorderable, нужно добавить dependency в `libs.versions.toml`. Решение за архитекторами.
- [ ] Room Migration 3→4 vs продолжить с `fallbackToDestructiveMigration`: текущее состояние теряет user data. Для production нужна реальная migration. Подтвердите стратегию (Open Question #5 из `1-research.md`).
