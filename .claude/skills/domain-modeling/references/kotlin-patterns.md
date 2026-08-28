# Kotlin Patterns for Domain Layer

This reference covers concrete Kotlin patterns for writing pure-functional domain code (Walking Skeleton style).

## Value Objects

Value objects wrap primitive types (`String`, `Int`, `Long`) to prevent "primitive obsession" and encode invariants.

### Use `@JvmInline value class` for single-field wrappers

```kotlin
@JvmInline
value class UserId(val value: String) {
    init {
        require(value.isNotBlank()) { "UserId must not be blank" }
        require(value.length in 1..64) { "UserId length out of range: ${value.length}" }
    }
}
```

**Benefits**:
- Zero runtime overhead (compiled to underlying primitive in most cases)
- Type safety: `fun findCall(id: CallId)` cannot accept `UserId`
- Validation at construction — illegal values impossible

### Use `data class` for multi-field value objects

```kotlin
data class DateRange(val start: Instant, val end: Instant) {
    init {
        require(start <= end) { "DateRange start must be ≤ end" }
    }

    val duration: Duration get() = Duration.between(start, end)
}
```

### Factory methods for fallible construction

```kotlin
@JvmInline
value class Email private constructor(val value: String) {
    companion object {
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")

        fun parse(raw: String): Result<Email> =
            if (EMAIL_REGEX.matches(raw)) Result.success(Email(raw))
            else Result.failure(InvalidEmailError(raw))
    }
}
```

## Entities

Entities are objects with identity. Use `data class` with immutable `val` fields.

```kotlin
data class Call(
    val id: CallId,
    val caller: UserId,
    val recipient: UserId,
    val startedAt: Instant,
    val kind: CallKind,
) {
    init {
        require(caller != recipient) { "Caller cannot call themselves" }
    }
}

enum class CallKind { VOICE, VIDEO }
```

**Rules**:
- All fields `val` (immutable)
- `init { require(...) }` for invariants
- No methods that cause side effects (no `save()`, no `send()`)
- Pure derived properties via `get()` are OK (`val duration: Duration get() = ...`)

## State Machines via sealed interface

State machines are modeled as `sealed interface` with `data class` / `data object` for each state.

```kotlin
sealed interface CallState {
    data object Idle : CallState

    data class Ringing(
        val call: Call,
        val ringingSince: Instant,
    ) : CallState

    data class Active(
        val call: Call,
        val startedAt: Instant,
        val isMuted: Boolean = false,
        val isSpeakerOn: Boolean = false,
    ) : CallState

    data class Ended(
        val callId: CallId,
        val reason: EndReason,
        val endedAt: Instant,
    ) : CallState
}

enum class EndReason { CALLER_HUNGUP, RECEIVER_HUNGUP, NETWORK_LOST, TIMEOUT, BUSY }
```

**Benefits**:
- Exhaustive `when` — compiler catches missing states
- Illegal transitions impossible (no "Active without call" — state carries its context)
- Pattern matching via `when (state) { is ... -> ... }`

### `sealed interface` vs `sealed class`

Prefer `sealed interface` unless you need shared state fields across all subtypes. Interfaces are more flexible — a class can implement multiple sealed interfaces.

## Pure Transition Functions

Functions that transition between states. **Always** return `Result<T>` — never throw.

```kotlin
fun answerCall(state: CallState, callId: CallId, now: Instant): Result<CallState.Active> =
    when (state) {
        is CallState.Ringing ->
            if (state.call.id == callId)
                Result.success(CallState.Active(state.call, now))
            else
                Result.failure(InvalidStateError("Call ID mismatch: expected ${state.call.id}, got $callId"))

        CallState.Idle,
        is CallState.Active,
        is CallState.Ended ->
            Result.failure(InvalidStateError("Cannot answer in state $state"))
    }
```

**Rules**:
- Top-level `fun`, not method on sealed interface
- Input: current state + parameters (never mutable references)
- Output: `Result<NewState>` or `Result<Unit>` if event-sourced
- Exhaustive `when` — no `else ->` that hides missing cases
- No throw — only `Result.failure(...)`

### Why top-level functions, not methods?

Methods on sealed interface force every state to implement them (or have default). Top-level functions allow clear per-state logic via `when`, and compile-time detection of newly added states via exhaustive check.

## Error Types

Use sealed hierarchy, not generic `Exception` or `IllegalArgumentException`.

```kotlin
sealed class DomainError(message: String) : Exception(message)

class InvalidStateError(message: String) : DomainError(message)
class ConstraintViolation(message: String, val field: String) : DomainError(message)
class NotFoundError(message: String, val entityType: String, val id: String) : DomainError(message)
```

**Benefits**:
- Callers can handle specific error types via `when (error is DomainError)`
- Encodes domain meaning (not just "something went wrong")
- Still extends `Exception` for interop with `Result` and standard Kotlin

## Enums for Closed Value Sets

Use `enum class` for fixed, known-at-compile-time values.

```kotlin
enum class EndReason { CALLER_HUNGUP, RECEIVER_HUNGUP, NETWORK_LOST, TIMEOUT, BUSY }
enum class CallKind { VOICE, VIDEO }
enum class UserRole { ADMIN, MEMBER, GUEST }
```

**Rules**:
- No companion methods that do I/O
- No nullable `fromString()` — prefer `Result<T>` factory

## Collections

Use `List<T>`, `Set<T>`, `Map<K, V>` from stdlib (immutable by default in domain).

```kotlin
data class CallHistory(val calls: List<Call>) {
    fun add(call: Call): CallHistory = copy(calls = calls + call)
    fun byCaller(id: UserId): List<Call> = calls.filter { it.caller == id }
}
```

**Never** use:
- `MutableList`, `MutableSet`, `MutableMap` in domain (mutation = side effect)
- `Array<T>` (JVM-specific, reference equality issues)

## Derived / computed properties

OK, as long as computation is pure:

```kotlin
data class Call(/* ... */) {
    val duration: Duration
        get() = Duration.between(startedAt, endedAt ?: Instant.now())  // ❌ side effect (now())
}
```

Fix: pass `now` as parameter to pure function, don't compute internally:

```kotlin
fun callDuration(call: Call, now: Instant): Duration =
    Duration.between(call.startedAt, call.endedAt ?: now)
```

## Null handling

Avoid `null` in domain. Use `sealed interface` or `Result<T>`.

```kotlin
// ❌ nullable field hides meaning
data class Call(val endedAt: Instant? = null)

// ✅ state captures "ended or not"
sealed interface CallState {
    data class Active(val startedAt: Instant) : CallState
    data class Ended(val startedAt: Instant, val endedAt: Instant) : CallState
}
```

Exception: genuinely optional fields (e.g., `middleName: String? = null`) are OK as long as semantics are clear.

## Immutability shortcuts via `copy()`

Data classes provide `copy()` — use it for state transitions:

```kotlin
fun toggleMute(state: CallState.Active): CallState.Active =
    state.copy(isMuted = !state.isMuted)
```

## Package structure

Single flat package per feature: `app/src/main/kotlin/<base_package>/domain/<feature_slug>/`

**Do NOT** create sub-packages on spec phase:
- ❌ `domain/call_mute/model/`, `domain/call_mute/usecase/`, `domain/call_mute/policy/`
- ✅ `domain/call_mute/CallState.kt`, `domain/call_mute/MuteAction.kt`, ...

Sub-packages are architectural decisions — they belong to design phase, not spec.

## Size guidelines (sanity checks, не hard limits)

File-size conventions из `.claude/rules/kotlin-conventions.md` остаются ориентиром (≤600 lines комфортно, >1000 — повод подумать о разбиении). Для domain skeleton это **sanity checks**, не hard targets:

- Один aggregate / state machine как правило ложится в один файл. Если aggregate естественно разбивается на 2-3 связанных файла (state + actions + errors) — OK, не сливайте насильно.
- Количество функций и тестов определяется Feature Domain Contract. Пишите столько, сколько нужно для полного покрытия rules + State Matrix + Domain Test Scenarios + Primary User Journeys.
- Если файл разрастается >600 lines — это **сигнал подумать**, не обязанность разбить. Иногда aggregate с rich state machine натурально занимает больше.

**Не жертвуйте покрытием ради компактности.** Преждевременное упрощение домена = баги в проде.
