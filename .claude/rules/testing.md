# Testing — Android App

## Test infrastructure

- Manual DI — no Hilt, no `HiltTestRunner`. Dependencies injected via constructors or MockK.
- `isReturnDefaultValues = true` в testOptions — unmocked Android framework calls возвращают defaults.
- Room in-memory databases: `Room.inMemoryDatabaseBuilder().allowMainThreadQueries()` для DAO tests.
- Instrumentation runner: `androidx.test.runner.AndroidJUnitRunner` (стандартный, без кастомизации).

## Test locations

| Module | Path | Type | Count |
|--------|------|------|-------|
| `:app` | `app/src/test/java/` | JVM unit tests | ~69 files |
| `:app` | `app/src/androidTest/java/` | Instrumented (migration, DAO) | 3 files |
| `:pushkit` | `pushkit/src/test/java/` | Push protocol JVM tests | 3 files |

## Build commands

- JVM tests: `./gradlew test --no-configuration-cache`
- Instrumented test APK build: `./gradlew assembleDebugAndroidTest --no-configuration-cache`
- Instrumented tests on connected device: `./gradlew connectedDebugAndroidTest`
- Canonical app build command: use `canonical_app_build_command` from `PROJECT-CONTEXT.md`
- Canonical device/backend scenario command: use `canonical_device_scenario_command` from `PROJECT-CONTEXT.md`
- Environment variables required for canonical builds: use the values documented in `PROJECT-CONTEXT.md`
- Bare `assembleDebug` is not a valid final app-build proof unless `PROJECT-CONTEXT.md` explicitly says so.

## Test dependencies

| Library | Usage |
|---------|-------|
| JUnit 4 (`junit:junit:4.13.2`) | Test framework |
| MockK (`io.mockk:mockk:1.13.10`) | Mocking: `mockk()`, `every {}`, `coEvery {}`, `verify {}` |
| Coroutines Test (`kotlinx-coroutines-test:1.7.3`) | `runTest`, `StandardTestDispatcher`, `UnconfinedTestDispatcher`, `advanceTimeBy()` |
| OkHttp MockWebServer (`mockwebserver:4.12.0`) | HTTP mocking |
| Room Testing (`room-testing`) | `MigrationTestHelper` for schema migrations |
| Compose UI Test (`compose-ui-test-junit4`) | Compose testing (instrumented) |

**NOT used**: Turbine — Flow testing uses `.toList()`, `.take()`, `.value` inspection.

## Fakes convention

Project uses **fakes** for repositories and DAOs — established convention. Canonical fake locations and preferred fake names must come from `PROJECT-CONTEXT.md`.

| Fake | Backing store | Call tracking |
|------|--------------|---------------|
| DAO fake | In-memory collection + `MutableStateFlow` | Explicit call counters for write methods |
| Repository fake | Mutable backing state | Track mutations and last requested IDs |
| API fake | Enum/flag-driven responses | Count requests and expose last payload |

**Inline fakes** for Android-bound classes that can't be instantiated in JVM:
- Functional replicas: extract testable logic into pure functions, test the logic without Android runtime
- Examples: ring timeout FSM, re-entry guard (AtomicBoolean), banner state mapping

## Test categories

| Category | Scope | Framework | Example |
|----------|-------|-----------|---------|
| API/Model parsing | Response decoding, payload format | JUnit 4 | `ApiResponseDecodingTest` |
| Domain logic | State machines, timeout, banner | JUnit 4 | `ConnectivityStateLogicTest` |
| Data mappers | Entity <-> Domain round-trip | JUnit 4 | `EntityMapperRoundTripTest` |
| Repository (with fakes) | Data flow, cache sync, queue | JUnit 4 + Fakes | `RepositoryQueueFlowTest` |
| WebSocket | Connection, events, health | JUnit 4 + MockK | `RealtimeEventParsingTest` |
| Push handling | TTL, dedup, routing FSM | JUnit 4 | `PushHandlerDedupTest` |
| Dedup & concurrency | Race conditions, idempotency | JUnit 4 + coroutines-test | `RepositorySyncConcurrencyTest` |
| Authentication | Cleanup chain, re-entry guard | JUnit 4 + MockK | `AuthenticationCleanupTest` |
| Migration (instrumented) | Room schema upgrade path | AndroidJUnit4 + MigrationTestHelper | `AppDatabaseMigrationTest` |
| DAO (instrumented) | Query correctness, boundary | AndroidJUnit4 + Room in-memory | `EntityDaoBoundaryTest` |

## Naming conventions

**Preferred (Kotlin backtick style)**:
```kotlin
@Test fun `when resetStateForLogout called then visibleConversationId is null`() { ... }
@Test fun `reEntryGuard when cleanupInProgress second call is skipped`() { ... }
```

**Legacy (camelCase)**:
```kotlin
@Test fun handleIncomingCall_registerReturnsFalse_notificationSkipped() { ... }
@Test fun getPendingMessages_includesQueued_Sending_Failed() { ... }
```

## Coroutines test patterns

```kotlin
// Modern virtual-time testing (preferred)
@OptIn(ExperimentalCoroutinesApi::class)
@Test fun `timeout fires after 60 seconds`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    // ... setup
    advanceTimeBy(60_001)
    // ... assertions
}

// Immediate execution (for simple Flow tests)
val dispatcher = UnconfinedTestDispatcher()

// Async coordination (for background coroutines)
val latch = CountDownLatch(1)
// ... launch background work
latch.await(2, TimeUnit.SECONDS)
```

## Rules

- Start with deterministic JVM tests for policies, queue logic, mappers, validators.
- Use fakes for repositories and DAOs — project convention. MockK as fallback for complex dependencies.
- Check edge cases: empty lists, null fields, boundary values, **negative IDs**, offline state.
- `test-dev` agent adds tests but NEVER modifies production code.
- NEVER delete test files unless listed in phase `overview.md` section "Deleted Files".
- TDD-style: tests are written PARALLEL with production code, not in separate "testing" phases after.
- For Android-bound classes: extract testable logic into functional replicas, test without Android runtime.
- When code references composed resources (API path = base URL + endpoint, channel = prefix + name, config = default + override), write at least 1 test verifying the full resolved value. Catches prefix duplication, format conflicts, missing segments.

## Avoid

- No live network or production credentials in unit tests.
- No testing private methods — test behavior through public API.
- No claiming quality gate passed if tests couldn't run.
- No deleting tests to achieve green build.
- No Hilt test modules or `@HiltAndroidTest` — manual DI only.
- No Turbine (not in dependencies) — use `.toList()`, `.take()`, `.value` for Flow testing.
