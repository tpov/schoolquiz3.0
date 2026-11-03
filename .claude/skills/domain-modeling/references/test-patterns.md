# Test Patterns for Domain Layer

This reference covers JUnit 4 patterns for testing pure-functional domain code.

## Framework

Per `.claude/rules/testing.md`:
- **JUnit 4** (`junit:junit:4.13.2`)
- **No MockK** in domain tests (pure functions don't need mocks)
- **No Coroutines Test** in domain (domain is synchronous)
- **No Turbine** (no Flow in pure domain)
- **AssertJ or stdlib** for assertions

## Test File Structure

One test file per aggregate/state machine:

```
app/src/test/kotlin/<base_package>/domain/<feature_slug>/
├── CallStateTest.kt       # tests for CallState transitions
├── MuteActionTest.kt      # tests for toggleMute, canMute
└── ValueObjectsTest.kt    # tests for CallId, UserId validation
```

## Test Naming

Prefer backtick style (Kotlin convention):

```kotlin
@Test fun `cannot answer call when state is Idle`() { /* ... */ }
@Test fun `mute toggle preserves call identity`() { /* ... */ }
@Test fun `ending an already-ended call fails with InvalidStateError`() { /* ... */ }
```

Alternative camelCase (legacy):

```kotlin
@Test fun answerCall_stateIdle_returnsFailure() { /* ... */ }
```

## Mapping Domain Test Scenario → JUnit @Test

Each `Domain Test Scenario` from `0-spec.md` maps to **exactly one** `@Test`:

### Gherkin in spec

```gherkin
Scenario: Cannot answer if not Ringing
  GIVEN state = Idle
  WHEN answerCall(callId="any")
  THEN Result.failure(InvalidStateError)
```

### JUnit equivalent

```kotlin
@Test
fun `cannot answer if not Ringing`() {
    // GIVEN
    val state: CallState = CallState.Idle

    // WHEN
    val result = answerCall(state, CallId("any"), Instant.now())

    // THEN
    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is InvalidStateError)
}
```

**Rules**:
- GIVEN / WHEN / THEN structure preserved as comments (optional but helpful)
- Variable names mirror spec terms
- One assertion per logical check (avoid over-asserting)

## State Matrix → Table-Driven Tests

If `0-spec.md` has a `State Matrix`:

```
| condition A | condition B | result |
|-------------|-------------|--------|
| X           | Y           | Alpha  |
| X           | Z           | Beta   |
| ...         | ...         | ...    |
```

Option 1: **one `@Test` per row** (preferred for <10 rows, clearer failure messages):

```kotlin
@Test fun `X + Y produces Alpha`() { /* ... */ }
@Test fun `X + Z produces Beta`() { /* ... */ }
```

Option 2: **parameterized** (for >10 rows or repetitive logic):

```kotlin
@RunWith(Parameterized::class)
class TransitionTableTest(
    private val a: ConditionA,
    private val b: ConditionB,
    private val expected: Result,
) {
    @Test fun `transition matches matrix`() {
        val actual = transition(a, b)
        assertEquals(expected, actual)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} + {1} → {2}")
        fun data() = listOf(
            arrayOf(ConditionA.X, ConditionB.Y, Result.Alpha),
            arrayOf(ConditionA.X, ConditionB.Z, Result.Beta),
            // ...
        )
    }
}
```

## Primary User Journeys → Integration-Style Tests (pure)

A Primary User Journey from spec traces state changes through the feature. Test as pure function chain:

```kotlin
@Test fun `happy path_ start call, mute, unmute, end`() {
    // Journey: Idle → Ringing → Active → Active(muted) → Active → Ended

    val now = Instant.parse("2026-04-16T10:00:00Z")
    val call = Call(CallId("X"), UserId("A"), UserId("B"), now, CallKind.VOICE)

    val s1 = startCall(CallState.Idle, call, now).getOrThrow()
    assertTrue(s1 is CallState.Ringing)

    val s2 = answerCall(s1, call.id, now.plusSeconds(5)).getOrThrow()
    assertTrue(s2 is CallState.Active)
    assertFalse(s2.isMuted)

    val s3 = toggleMute(s2).getOrThrow()
    assertTrue(s3.isMuted)

    val s4 = toggleMute(s3).getOrThrow()
    assertFalse(s4.isMuted)

    val s5 = endCall(s4, EndReason.CALLER_HUNGUP, now.plusMinutes(2)).getOrThrow()
    assertTrue(s5 is CallState.Ended)
    assertEquals(EndReason.CALLER_HUNGUP, s5.reason)
}
```

**Rules**:
- No mocks, no fakes
- Deterministic time via `Instant` parameters (never `Instant.now()` inside test logic)
- Assert key state changes at each step, not just final state

## Value Object Validation Tests

For every `init { require(...) }`, add a test:

```kotlin
class UserIdTest {
    @Test fun `blank value throws`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            UserId("")
        }
        assertTrue(error.message!!.contains("blank"))
    }

    @Test fun `value over 64 chars throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            UserId("a".repeat(65))
        }
    }

    @Test fun `valid value succeeds`() {
        val id = UserId("user-123")
        assertEquals("user-123", id.value)
    }
}
```

## Pure Function Tests (no fakes, no mocks)

Pure functions test directly — input → assertion on output:

```kotlin
@Test fun `mute toggle flips isMuted flag`() {
    val call = Call(CallId("X"), UserId("A"), UserId("B"), Instant.EPOCH, CallKind.VOICE)
    val state = CallState.Active(call, Instant.EPOCH, isMuted = false)

    val result = toggleMute(state).getOrThrow()

    assertTrue(result.isMuted)
    assertEquals(call, result.call)  // identity preserved
}
```

**No setup methods** (`@Before`) for pure tests — each test constructs its own state explicitly for readability.

## Result<T> assertion helpers

Common patterns:

```kotlin
// Success + unwrap
val s = answerCall(state, id, now).getOrThrow()

// Failure + type check
val error = answerCall(state, id, now).exceptionOrNull()
assertTrue(error is InvalidStateError)

// Failure + message check
val error = answerCall(state, id, now).exceptionOrNull() as InvalidStateError
assertTrue(error.message!!.contains("Cannot answer"))

// No try/catch for expected failures — Result is explicit
```

## What NOT to do

### ❌ Don't use MockK in domain tests

```kotlin
// BAD — domain should have no dependencies to mock
val repo = mockk<CallRepository>()
every { repo.save(any()) } returns Unit
```

Pure domain has no repositories. If test needs repo mock, that logic belongs in phase-01 (integration), not spec.

### ❌ Don't test private functions

```kotlin
// BAD — change private to internal and test via public API
@Test fun `private helper returns expected`() { /* ... */ }
```

Test behavior through public API — public functions in domain.

### ❌ Don't use `now()` inside assertions

```kotlin
// BAD — non-deterministic
@Test fun `call duration is positive`() {
    val state = startCall(...)
    assertTrue(callDuration(state).toMillis() > 0)  // flaky
}

// GOOD — deterministic
@Test fun `call duration equals end minus start`() {
    val start = Instant.parse("2026-04-16T10:00:00Z")
    val end = start.plusSeconds(60)
    assertEquals(Duration.ofSeconds(60), callDuration(call, end))
}
```

### ❌ Don't write meta-tests

```kotlin
// BAD — tests the test framework, not the domain
@Test fun `assertTrue works`() { assertTrue(true) }
```

Every test must map to a Domain Test Scenario, State Matrix row, Primary User Journey step, or value object invariant.

## Coverage checklist

Before reporting complete, verify:

- [ ] Every `Domain Test Scenario` from `0-spec.md` → exactly one `@Test`
- [ ] Every row in `State Matrix` → one `@Test`
- [ ] Every `Primary User Journey` → one `@Test` walking the full flow
- [ ] Every `init { require(...) }` in value objects → one `@Test` for violation + one for valid
- [ ] Every public function has at least one "happy path" and one "rejection" test
- [ ] All `Result.failure(...)` branches covered

## Running tests

```bash
./gradlew test --tests "*<feature_slug>*" --no-configuration-cache
```

Expected output: `BUILD SUCCESSFUL`, all green, no skipped.

If red:
1. Fix skeleton if code is wrong
2. If rule is wrong in spec → STOP, escalate to lead (don't silently rewrite the rule)
