---
name: domain-modeling
description: Generate complete domain layer in Kotlin following Functional Core / Imperative Shell boundaries. Used by domain-designer agent on spec phase to produce full Walking Skeleton — pure core + repository interfaces + use cases + in-memory fakes + JVM tests that validate Feature Domain Contract before design/plan/implement phases.
---

# Domain Modeling (Full Walking Skeleton — Variant Y)

## Scope extension (spec = full domain)

Этот skill производит **полный domain layer** на spec-этапе, а не только pure core. Mission:
- Pure Functional Core: value objects, entities, states, pure transitions
- **Repository interfaces** (pure abstractions — чистые `interface`, без реализаций)
- **Use cases** (`operator fun invoke(...)`, constructor injection repository interface, `suspend` + `Flow` разрешены здесь)
- **In-memory fake repositories** в test source set (используются ТОЛЬКО для тестирования use cases; production-реализации идут в phase-01)
- JUnit тесты: pure core напрямую, use cases через fakes

Phase-01 в implement после этого **НЕ пишет domain code** — только wire up repositories to real data sources, DAO mappers, DI bindings.

## When to invoke this skill

Invoke this skill when:
- Generating full domain slice for a new feature on spec phase (`domain-designer` agent)
- Validating domain rules from `Feature Domain Contract` через executable tests including use case orchestration
- Refactoring existing domain layer and need to clarify Functional Core / Imperative Shell boundary

Do NOT invoke this skill when:
- Implementing real repositories (backed by Room/Retrofit/Firebase), DAO mappers — that's phase-01 in implement
- Designing UI/ViewModels — that's presentation/ui, frontend-dev
- Choosing framework stack (Decompose/Koin/Room/Compose) — that's design phase

## Foundational principles

### 1. Functional Core, Imperative Shell (Gary Bernhardt)

Domain layer = **pure functions over immutable data**. No side effects. No I/O. No framework dependencies. Side effects (HTTP, DB, UI, logging) live in the imperative shell (data/presentation layers), which *calls* the core.

**Test consequence**: pure functions test directly without mocks. Given input → assert output. Deterministic. Fast.

### 2. Walking Skeleton (Alistair Cockburn)

Domain code is **production-quality from day one**, not throw-away prototype. Phase-01 in implement wraps this code with repositories/DI/adapters — it does NOT rewrite. Renames are allowed in design phase, but business rules and function signatures survive.

### 3. Specification by Example (Gojko Adzic)

Each `Domain Test Scenario` (GIVEN/WHEN/THEN) from `0-spec.md` maps to **exactly one** JUnit `@Test`. Tests are living documentation — if spec changes, tests must reflect it. If tests diverge from spec, spec was ambiguous → re-spec.

### 4. Make Illegal States Unrepresentable (Yaron Minsky)

Use Kotlin's type system to eliminate invalid states at compile time. Prefer `sealed interface` over nullable pairs. Prefer `@JvmInline value class` over raw primitives. Prefer `enum class` over magic strings.

## Mandatory reading order

Load references lazily, only when needed for current work:

1. **`references/kotlin-patterns.md`** — value objects, data classes, sealed interfaces, pure functions (read FIRST)
2. **`references/test-patterns.md`** — JUnit 4 + Gherkin mapping, State Matrix → table-driven tests
3. **`references/anti-patterns.md`** — что категорически запрещено в domain слое (читать перед любым commit)

## Quick reference (cheat sheet)

### Value Object (with validation)

```kotlin
@JvmInline
value class UserId(val value: String) {
    init {
        require(value.isNotBlank()) { "UserId must not be blank" }
        require(value.length <= 64) { "UserId too long" }
    }
}
```

### Entity (immutable data class)

```kotlin
data class Call(
    val id: CallId,
    val caller: UserId,
    val recipient: UserId,
    val startedAt: Instant,
) {
    init {
        require(caller != recipient) { "Caller cannot call themselves" }
    }
}
```

### State Machine (sealed interface)

```kotlin
sealed interface CallState {
    data object Idle : CallState
    data class Ringing(val call: Call) : CallState
    data class Active(val call: Call, val isMuted: Boolean) : CallState
    data class Ended(val callId: CallId, val reason: EndReason) : CallState
}

enum class EndReason { CALLER_HUNGUP, RECEIVER_HUNGUP, NETWORK_LOST, TIMEOUT, BUSY }
```

### Pure Function (Result<T>, no throw)

```kotlin
fun toggleMute(state: CallState): Result<CallState.Active> =
    when (state) {
        is CallState.Active -> Result.success(state.copy(isMuted = !state.isMuted))
        else -> Result.failure(InvalidStateError("Cannot mute in $state"))
    }
```

### Error type (sealed, not exceptions)

```kotlin
sealed class DomainError(message: String) : Exception(message)
class InvalidStateError(message: String) : DomainError(message)
class ConstraintViolation(message: String) : DomainError(message)
```

### Test — pure core (direct assertion, no mocks)

```kotlin
class CallStateTest {
    @Test fun `cannot mute when Idle`() {
        val result = toggleMute(CallState.Idle)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is InvalidStateError)
    }

    @Test fun `mute toggle preserves call identity`() {
        val call = Call(CallId("X"), UserId("A"), UserId("B"), Instant.now())
        val state = CallState.Active(call, isMuted = false)
        val result = toggleMute(state).getOrThrow()
        assertEquals(call, result.call)
        assertTrue(result.isMuted)
    }
}
```

### Repository Interface (pure abstraction — layer boundary)

Repository interface **живёт в domain**, реализация (Room/Retrofit/Firebase) — в phase-01 data layer. Interface описывает domain-level операции, не transport-specific:

```kotlin
// domain/<slug>/repository/CallRepository.kt
interface CallRepository {
    suspend fun fetchActiveCall(userId: UserId): Result<CallState.Active?>
    fun observeCalls(userId: UserId): Flow<List<Call>>
    suspend fun save(call: Call): Result<Unit>
}
```

Правила:
- Параметры и return — только **domain types** (`UserId`, `Call`, `Result<T>`, `Flow<T>`). Никаких DTO, Entity, JSON, HTTP-кодов
- `suspend fun` и `Flow<T>` разрешены (boundary между pure core и imperative shell)
- `Result<T>` для операций которые могут fail. `suspend fun` без Result → строго happy-path операции
- Без аннотаций (`@Inject`, `@Singleton`, `@Module`) — DI решается в phase-01
- Один repository = один aggregate (не god-interface с 20 методами)

### Use Case (constructor injection repository, single business scenario)

```kotlin
// domain/<slug>/use_case/MuteCallUseCase.kt
class MuteCallUseCase(
    private val calls: CallRepository,
) {
    suspend operator fun invoke(callId: CallId): Result<CallState.Active> {
        val active = calls.fetchActiveCall(callId.owner).getOrNull()
            ?: return Result.failure(CallNotFoundError(callId))
        val muted = toggleMute(active).getOrElse { return Result.failure(it) }
        calls.save(muted.call).getOrElse { return Result.failure(it) }
        return Result.success(muted)
    }
}
```

Правила:
- `operator fun invoke(...)` — single business scenario, один вызов = одна операция
- Constructor — только **repository interfaces** (или другие use cases для composition; sparingly)
- Никаких concrete classes, framework-типов, Android API, DI annotations
- Логика — orchestration: fetch → apply pure domain function → persist. Сами бизнес-правила живут в pure core, use case их *вызывает*

### In-Memory Fake (test double for use case tests)

Fake — это **полноценная in-memory реализация interface**, не mock с заглушками. Живёт в test source set (commonTest/jvmTest), используется для тестирования use cases без I/O:

```kotlin
// test/.../domain/<slug>/fake/FakeCallRepository.kt
class FakeCallRepository : CallRepository {
    private val state = MutableStateFlow<List<Call>>(emptyList())

    override suspend fun fetchActiveCall(userId: UserId): Result<CallState.Active?> =
        Result.success(state.value.firstOrNull { it.caller == userId }?.let { CallState.Active(it, isMuted = false) })

    override fun observeCalls(userId: UserId): Flow<List<Call>> =
        state.map { list -> list.filter { it.caller == userId } }

    override suspend fun save(call: Call): Result<Unit> {
        state.update { it + call }
        return Result.success(Unit)
    }

    // Test helpers (не часть interface):
    fun seed(calls: List<Call>) = state.update { calls }
    fun stored(): List<Call> = state.value
}
```

### Test — use case through fake

```kotlin
class MuteCallUseCaseTest {
    @Test fun `mute active call transitions to muted state`() = runTest {
        val fake = FakeCallRepository()
        val call = Call(CallId("X"), UserId("A"), UserId("B"), Instant.parse("2026-01-01T00:00:00Z"))
        fake.seed(listOf(call))
        val useCase = MuteCallUseCase(fake)

        val result = useCase(CallId("X"))

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isMuted)
    }

    @Test fun `mute when no active call returns failure`() = runTest {
        val fake = FakeCallRepository()
        val useCase = MuteCallUseCase(fake)

        val result = useCase(CallId("MISSING"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CallNotFoundError)
    }
}
```

## Directory structure (spec phase output)

```
domain/<feature_slug>/
├── model/         — value objects, entities, enums (pure data)
├── state/         — sealed interface state machines, state containers
├── logic/         — pure functions (transitions, predicates, policies)
├── repository/    — repository interfaces (suspend/Flow allowed, pure abstractions)
└── use_case/      — use case classes (constructor-inject repository interfaces)

test/.../domain/<feature_slug>/
├── <CoreTypeTest>.kt    — pure core tests (no mocks, no fakes)
├── <UseCaseTest>.kt     — use case tests via fakes
└── fake/
    └── Fake<Repo>.kt    — in-memory fake implementations of repository interfaces
```

Tests file organization: один test file на aggregate/use case, fakes — в подпакете `fake/`. Pure core тесты не используют fakes; use case тесты — используют.

## Workflow

### Phase A: Pure Functional Core (model/state/logic)

1. **Read** `0-spec.md` sections: `Feature Domain Contract`, `State Matrix`, `Primary User Journeys`, `Domain Test Scenarios`
2. **Identify aggregates**: each `Term / Entity` from Domain Contract → 1 file in `model/`
3. **Model terms**: value objects for constrained primitives (IDs, emails, phone numbers) → `model/`
4. **Model entities**: data classes for domain objects (Call, Message, User) → `model/`
5. **Model states**: sealed interface + data classes for lifecycle states → `state/`
6. **Model transitions**: top-level functions returning `Result<T>` (no methods on sealed interface if logic differs per state — use exhaustive `when`) → `logic/`
7. **Model errors**: sealed error hierarchy (not generic Exception) → `model/` or `logic/`
8. **Write pure core tests**: one `@Test` per Domain Test Scenario + one per State Matrix row + one per Primary User Journey. **NO mocks/fakes.** Direct input → assert output
9. **Run tests**: `./gradlew test --tests "*<feature_slug>*" --no-configuration-cache` — Phase A green

### Phase B: Repository Interfaces (repository/)

10. **Infer repository boundaries** from `0-spec.md` — ищи фразы «persisted», «fetched», «observed», «synced», «loaded from». Каждый persistence boundary → один `<Aggregate>Repository` interface
11. **Define interface signatures** в domain terms. Разрешено: `suspend fun`, `Flow<T>`, `Result<T>`. Запрещено: DTO, Entity, HTTP coding, any framework type
12. **No implementations** в `repository/` (main source) — только `interface`. Реализации будут в phase-01 (data layer)

### Phase C: Use Cases (use_case/)

13. **Identify user-facing operations** из `0-spec.md` Primary User Journeys и AC. Каждое **single business scenario** → один use case class
14. **Constructor inject** repository interfaces. Без конкретных классов, без framework
15. **Write use case body** — orchestration: fetch (through repo) → apply pure function (from `logic/`) → persist (through repo) → return Result
16. **Keep use cases thin** — вся business logic в pure core; use case только координирует

### Phase D: In-Memory Fakes + Use Case Tests (test/.../fake/)

17. **Create fake repositories** в test source set. Полноценные in-memory реализации интерфейсов (не mock-заглушки). Каждый repository → один fake
18. **Write use case tests**: для каждого Primary User Journey и key AC → `@Test` через fake. Seed fake, invoke use case, assert на result + fake state
19. **Run tests**: gradle task снова — Phase D green

### Phase E: Coverage check + Report

20. **Verify coverage**: каждый Domain Test Scenario покрыт пробой из pure core **или** use case теста; каждый State Matrix row; каждый Primary User Journey хотя бы одним use case тестом
21. **Report to lead**: файлы, test count, coverage summary (pure core / use case splits), open questions (if any)

Если Domain Contract в 0-spec.md не содержит описания persistence или side effects — Phases B-D могут оказаться пустыми. Это валидно — пометь в отчёте «no repositories needed: Domain Contract pure, no persistence described».

## Escalation signals

Stop и спроси lead-а (через SendMessage, секция Open Questions), если:

- **Mock instead of fake** — если по какой-то причине не получается написать полноценный in-memory fake (например repository должен вернуть что-то невоспроизводимое на fake) → это сигнал что interface design кривой. Эскалируй, переделай signature
- **Framework import needed** — signature repository interface или use case требует Android/SDK тип → место в data layer (phase-01). Оборачивай тип в domain-специфичный, эскалируй если нельзя
- **Spec contradiction** — два rules в `Feature Domain Contract` не могут одновременно выполняться → эскалируй, не выбирай один молча
- **Ambiguity** — `Domain Test Scenario` допускает два толкования → эскалируй, не принимай решение за пользователя
- **Persistence ambiguity** — `0-spec.md` не даёт чёткого сигнала что именно должно быть в repository (fetch by id? observe? batch?). Зафиксируй как Open Question, предложи минимальный interface
- **Scope feels wrong** — если для корректного моделирования нужно подозрительно много aggregates (например фича про "mute" внезапно требует 8+ классов) → это сигнал что spec может содержать несколько independent concerns. Спроси lead-а: "объёмно, не рассматривали разбиение в Phase 2.5?" Но если фича реально цельная и требует всё это — пиши всё. Качество > искусственных лимитов.

**Нет hard limits на размер.** Если для правильного моделирования нужно 7 aggregates, 20 функций и 30 тестов — пиши всё. Преждевременное упрощение хуже избыточности.

## Output format

When complete, send lead this report via SendMessage:

```
=== DOMAIN SKELETON READY ===
Feature: <slug>
Package: <base_package>.domain.<feature_slug>

Files created:
  domain/<slug>/CallState.kt         (sealed interface + 4 states)
  domain/<slug>/CallId.kt            (value object)
  domain/<slug>/MuteAction.kt        (pure functions)
  domain/<slug>/DomainError.kt       (sealed error hierarchy)

Tests created:
  test/.../domain/<slug>/CallStateTest.kt    (6 scenarios)
  test/.../domain/<slug>/MuteActionTest.kt   (4 scenarios)
  Total: 10 tests

Validation:
  ./gradlew test --tests "*<slug>*" — PASSED (10 run, 0 failed, 0 skipped)

Coverage:
  Feature Domain Contract rules: 7/7 covered
  State Matrix rows: 4/4 covered
  Primary User Journeys: 3/3 covered
  Domain Test Scenarios: 10/10 covered

Open Questions: none
```

## Rules

1. **=pure core is pure=** — `model/`, `state/`, `logic/` — no side effects, no throw, no mutation, no `suspend`, no `Flow`, no framework types. Tested directly without mocks/fakes
2. **=total functions via Result=** — pure core functions return `Result<T>` if input domain is not exhaustive
3. **=make illegal states unrepresentable=** — lean on types, not runtime checks
4. **=repository interfaces pure=** — `repository/` contains only `interface` with domain types in signatures (no DTO, no Entity, no HTTP, no DI annotations). `suspend fun` and `Flow<T>` allowed — it's the boundary to imperative shell
5. **=use cases thin=** — `use_case/` классы только координируют repository + pure core. Без business rules внутри use case body (они в `logic/`). Constructor injects repository interfaces, ничего framework-specific
6. **=fakes not mocks=** — `test/.../fake/` содержит полноценные in-memory реализации repository interfaces. Use case тесты идут через fakes. Pure core тесты никогда не используют fakes
7. **=tests are spec mirrors=** — каждый Domain Test Scenario → ровно один `@Test` (pure core или use case). State Matrix rows и Primary User Journeys покрыты
8. **=no framework decisions=** — не выбирай framework stack (Decompose/Koin/Room/Compose) — это design phase. Твоя задача — abstract interfaces + use cases + tests
9. **=green before report=** — never claim complete if tests not green
10. **=escalate, don't improvise=** — ambiguity → Open Question, not best guess

## References

- Bernhardt, Gary. "Boundaries" talk. Functional Core, Imperative Shell.
- Cockburn, Alistair. "Crystal Clear: A Human-Powered Methodology for Small Teams." Walking Skeleton.
- Hunt, Andrew & Thomas, David. "The Pragmatic Programmer." Tracer Bullets.
- Adzic, Gojko. "Specification by Example." Living documentation via tests.
- Minsky, Yaron. "Effective ML." Make illegal states unrepresentable.
- Scott Wlaschin. "Domain Modeling Made Functional." (F# but principles apply to Kotlin ADTs.)
