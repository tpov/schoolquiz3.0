# Anti-Patterns: What NOT to do in Domain Layer

This reference lists violations that break domain purity. Read before every commit.

## 1. Framework Imports

**Forbidden in domain files:**

```kotlin
// ❌ Android framework
import android.content.Context
import android.os.Bundle
import android.net.Uri
import androidx.lifecycle.ViewModel

// ❌ Third-party SDKs
import io.livekit.android.room.track.Track
import com.google.firebase.auth.FirebaseUser
import retrofit2.Response
import okhttp3.OkHttpClient
import androidx.room.Entity
import com.squareup.moshi.JsonClass
```

**Why**: Domain must compile and run on pure JVM (no Android emulator, no network, no DB). If a type is platform-specific, it belongs to the data layer.

**Fix**: data layer wraps SDK type, maps to/from domain type:

```kotlin
// data layer
class LiveKitCallAdapter(private val track: Track) {
    fun toDomain(): CallVideoTrack = CallVideoTrack(
        id = track.sid.value,
        isEnabled = track.enabled,
    )
}

// domain
data class CallVideoTrack(val id: String, val isEnabled: Boolean)
```

## 2. DI Annotations

**Forbidden:**

```kotlin
// ❌ Hilt / Dagger
@Inject constructor(...)
@Provides fun provideCall(): Call
@Module class DomainModule
@HiltAndroidApp
@Singleton

// ❌ Koin
val module = module { single { Call(...) } }
```

**Why**: DI is a composition-root concern (who creates what). Domain models should be creatable by `data class` constructor without any DI framework.

**Fix**: no annotations on domain types. DI wiring happens in data/presentation layer, phase-01.

## 3. Side Effects

**Forbidden in pure functions:**

```kotlin
// ❌ Logging
fun toggleMute(state: CallState): CallState.Active {
    Log.d("Call", "Toggling mute")  // side effect
    return state.copy(isMuted = !state.isMuted)
}

// ❌ println
fun calculateFee(call: Call): Money {
    println("Calculating for ${call.id}")  // side effect
    return Money(100)
}

// ❌ Mutable global state
var lastCallId: CallId? = null  // mutable top-level
fun recordCall(call: Call) { lastCallId = call.id }

// ❌ I/O
fun saveCall(call: Call) { File("calls.txt").appendText(call.toString()) }

// ❌ Time access inside pure function
fun isRecent(call: Call): Boolean =
    call.startedAt > Instant.now().minusSeconds(60)  // now() = side effect
```

**Fix**: pass `now` as parameter; move logging/persistence to data layer:

```kotlin
fun isRecent(call: Call, now: Instant): Boolean =
    call.startedAt > now.minusSeconds(60)
```

## 4. Throwing Exceptions

**Forbidden:**

```kotlin
// ❌ Throw in pure function
fun answerCall(state: CallState, callId: CallId): CallState.Active {
    if (state !is CallState.Ringing) throw IllegalStateException("Cannot answer")
    return CallState.Active(state.call)
}
```

**Fix**: return `Result<T>`:

```kotlin
fun answerCall(state: CallState, callId: CallId, now: Instant): Result<CallState.Active> =
    when (state) {
        is CallState.Ringing -> Result.success(CallState.Active(state.call, now))
        else -> Result.failure(InvalidStateError("Cannot answer in state $state"))
    }
```

**Exception to rule**: `init { require(...) }` in value objects/data classes MAY throw `IllegalArgumentException`. This is construction-time validation, not transition logic.

## 5. Repository Interfaces in Domain

**Forbidden:**

```kotlin
// ❌ Repository interface in domain skeleton
interface CallRepository {
    suspend fun save(call: Call)
    fun observeCalls(): Flow<List<Call>>
}
```

**Why**: Repository interfaces are an architectural choice (Hexagonal / Clean Arch). They belong in design phase, implemented in data layer during phase-01. Spec-phase domain is pure — no I/O abstractions.

**Fix**: add `CallRepository` in design phase (`01-architecture.md`), implement in `data/`, phase-01.

## 6. UseCase Classes with Repository Dependencies

**Forbidden:**

```kotlin
// ❌ UseCase in spec domain
class MuteCallUseCase(private val repo: CallRepository) {
    suspend operator fun invoke(callId: CallId): Result<Unit> { /* ... */ }
}
```

**Why**: UseCase that takes a repository is orchestration — needs I/O boundary, needs DI, needs coroutines. All phase-01 concerns.

**Fix**: spec contains only pure transition functions (`fun toggleMute(state): Result<State>`). Phase-01 wraps them in UseCase class with repository injection.

## 7. Suspend Functions / Coroutines

**Forbidden:**

```kotlin
// ❌ suspend in domain
suspend fun fetchCall(id: CallId): Call = api.getCall(id)
suspend fun observeCalls(): Flow<List<Call>>
```

**Why**: `suspend` implies asynchronous boundary — typically a network/DB call. Domain is synchronous pure computation.

**Fix**: remove `suspend`. Async logic belongs to phase-01 repository/UseCase.

## 8. Mutable Collections

**Forbidden:**

```kotlin
// ❌ Mutable return types
data class Call(val tags: MutableList<String>)

fun addTag(call: Call, tag: String) { call.tags.add(tag) }  // mutation
```

**Fix**: immutable collections + `copy()`:

```kotlin
data class Call(val tags: List<String>)

fun addTag(call: Call, tag: String): Call = call.copy(tags = call.tags + tag)
```

## 9. Deep Inheritance / Abstract Base Classes

**Forbidden:**

```kotlin
// ❌ Abstract base class with inheritance
abstract class Entity {
    abstract val id: String
    fun isValid(): Boolean = id.isNotBlank()
}

class Call(override val id: String, /* ... */) : Entity()
```

**Why**: OOP inheritance fights Functional Core principles. Prefer composition + interfaces.

**Fix**: use `sealed interface` (closed hierarchy) or `interface` with default methods (open):

```kotlin
interface Identified { val id: String }
fun <T : Identified> T.isValid(): Boolean = id.isNotBlank()

data class Call(override val id: String, /* ... */) : Identified
```

## 10. Mocks / Fakes in Domain Tests

**Forbidden:**

```kotlin
// ❌ MockK in domain test
@Test fun `answer call saves state`() {
    val repo = mockk<CallRepository>()
    every { repo.save(any()) } returns Unit
    val useCase = MuteCallUseCase(repo)
    useCase(CallId("X"))
    verify { repo.save(any()) }
}
```

**Why**: If you need a mock, the test isn't testing pure domain — it's testing orchestration (phase-01).

**Fix**: test pure functions directly without mocks. If orchestration test needed, move to phase-01.

## 11. Sub-Packages on Spec Phase

**Forbidden on spec phase:**

```
domain/call_mute/
├── model/
│   └── CallState.kt
├── usecase/
│   └── MuteUseCase.kt
├── policy/
│   └── MutePolicy.kt
└── error/
    └── MuteError.kt
```

**Why**: Sub-package structure is an architectural decision (Clean Arch? Hexagonal? Onion?). It belongs to design phase, not spec.

**Fix**: flat package for skeleton:

```
domain/call_mute/
├── CallState.kt
├── MuteAction.kt
└── DomainError.kt
```

Design phase may reorganize.

## 12. Android Types as Parameters

**Forbidden:**

```kotlin
// ❌ Context as parameter
fun formatCallMessage(context: Context, call: Call): String = context.getString(R.string.call, call.id)

// ❌ Uri, Bundle, Intent
fun parseCallUri(uri: Uri): Call
```

**Why**: Adding Android types anywhere in domain (return, parameter, field) makes the entire layer Android-dependent — breaks JVM testability.

**Fix**: parse/format happens outside domain. Domain operates on pure strings/structures:

```kotlin
// domain
fun callSummary(call: Call): String = "Call ${call.id.value}"

// presentation layer maps to resource string
context.getString(R.string.call_format, callSummary(call))
```

## 13. JSON / Serialization Annotations

**Forbidden:**

```kotlin
// ❌ Moshi / Gson / Kotlinx.Serialization in domain
@JsonClass(generateAdapter = true)
data class Call(
    @Json(name = "call_id") val id: String,
)

@Serializable
data class User(@SerialName("user_id") val id: String)
```

**Why**: Serialization is transport concern (data layer). Domain types shouldn't know about JSON field names.

**Fix**: DTO in data layer carries annotations; mapper converts to/from domain:

```kotlin
// data/dto/CallDto.kt
@JsonClass(generateAdapter = true)
data class CallDto(@Json(name = "call_id") val id: String, /* ... */) {
    fun toDomain(): Call = Call(CallId(id), /* ... */)
}

// domain/call/Call.kt
data class Call(val id: CallId, /* ... */)
```

## 14. Room / Database Annotations

**Forbidden:**

```kotlin
// ❌ Room in domain
@Entity(tableName = "calls")
data class Call(@PrimaryKey val id: String)
```

**Fix**: Entity in data layer; domain model separate:

```kotlin
// data/db/CallEntity.kt
@Entity(tableName = "calls")
data class CallEntity(@PrimaryKey val id: String, /* ... */) {
    fun toDomain(): Call = Call(CallId(id), /* ... */)
}

// domain/call/Call.kt
data class Call(val id: CallId, /* ... */)
```

## 15. Logging / Observability

**Forbidden:**

```kotlin
// ❌ Timber, Log, etc. in domain
import timber.log.Timber
fun validate(call: Call): Result<Unit> {
    Timber.d("Validating ${call.id}")
    return /* ... */
}
```

**Fix**: return rich `Result` with context; caller logs at boundary:

```kotlin
// domain
fun validate(call: Call): Result<Unit> =
    if (call.caller == call.recipient)
        Result.failure(ValidationError("caller equals recipient", field = "caller"))
    else Result.success(Unit)

// phase-01 orchestration
validate(call).onFailure { Timber.d("Validation failed: $it") }
```

## Pre-commit checklist for domain-designer agent

Before reporting `DOMAIN SKELETON READY`, verify NO domain file contains:

- [ ] `import android.*` or `import androidx.*`
- [ ] Third-party SDK imports (grep: `io.livekit`, `com.google.firebase`, `retrofit2`, `okhttp3`, `androidx.room`, `com.squareup.moshi`, `kotlinx.serialization`)
- [ ] DI annotations (`@Inject`, `@Provides`, `@Module`, `@Singleton`)
- [ ] `suspend` keyword on any function
- [ ] `throw` statement in function body (except `init { require }`)
- [ ] `var` at top-level or as field (mutable state)
- [ ] `Log.*`, `println`, `System.out`, `Timber`
- [ ] `mockk`, `every`, `verify`, `mockkObject` in test files
- [ ] Sub-packages under `domain/<slug>/`
- [ ] `Context`, `Uri`, `Bundle`, `Intent`, `View` references
- [ ] `@Entity`, `@PrimaryKey`, `@JsonClass`, `@SerialName`, `@Serializable`

If any violation found → fix before reporting complete.
