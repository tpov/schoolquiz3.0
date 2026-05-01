# Kotlin Conventions — KMP/Android

## Type choices

- `data class` for payloads and immutable state holders.
- `sealed interface` for closed state sets (prefer over `sealed class` when no shared state).
- `object` for stateless implementations and singleton utilities.
- `enum class` for fixed, known-at-compile-time value sets.
- `operator fun invoke()` for single-action use cases.

## Naming

- Functions: verb-first (`createTask`, `validateOrder`, `observeMessages`).
- Boolean properties/functions: `is*`, `has*`, `can*`, `should*`.
- Flows: `observe*` or `*Flow` suffix.
- Suspend functions: verb without `async` prefix (use `suspend fun send()`, not `suspend fun asyncSend()`).

## Null safety

- No `!!` outside tests — use `requireNotNull()` with descriptive message.
- No nullable fields if a default value or explicit branch is clearer.
- Use `Result<T>` or `Flow<T>` on boundaries (repository returns, use case results).

## Size limits

| Metric | Green | Yellow (review) | Red (must fix) |
|--------|-------|-----------------|----------------|
| File length | <600 lines | 600-1000 | >1000 — split |
| Function length | <50 lines | 50-100 | >100 — extract |
| Nesting depth | 1-3 levels | — | >3 — flatten |
| Function params | <=5 | 6-7 | >7 — use data class |

## Coroutines

- Use structured concurrency: `coroutineScope`, `supervisorScope`.
- Catch `CancellationException` explicitly before generic `Exception`.
- Prefer `withContext(Dispatchers.IO)` over creating new scopes for IO work.
- Expose `Flow<T>` from repositories; collect in Decompose Component scope or a lifecycle-aware Android scope.

## Event streams: Channel vs SharedFlow vs StateFlow (menu-refactor retro fix)

| Use case | Type | Rationale |
|----------|------|-----------|
| One-shot event, single consumer | `Channel` + `receiveAsFlow()` | Single-consumer contract; multiple collectors → race condition (events split) |
| Event, multiple observers | `MutableSharedFlow(replay = 0..1, extraBufferCapacity = N)` | Multicast; все collectors видят все emissions |
| Current state snapshot | `MutableStateFlow(initial)` | State + distinct updates |
| Recent state history | `MutableSharedFlow(replay >= 1)` | Multicast + replay |

**Avoid**: `Channel.receiveAsFlow()` с >1 consumer = nondeterministic event split. Если несколько слоёв UI должны observe — используй `SharedFlow`.

**Source rationale**: menu-refactor retrospective Bug #3 — `DefaultRootComponent.events` Channel с двумя consumers (MainActivity + AppShellScreen). `DevModeActivated` events nondeterministically consumed по no-op TODO branch вместо snackbar handler.

### Review check

```bash
# Channel.receiveAsFlow() с >1 collect site — likely violation
rg -n "Channel.*receiveAsFlow\|consumeAsFlow" --type kt
# Then for each: count `.collect|collectLatest|collectIndexed` references on the same field name → if >1, blocker.
```

concurrency-reviewer обязан запустить эту проверку для phases meняющих event streams.

## Avoid

- No JSON parsing in Compose Screen, Decompose Component, Fragment, ViewModel, or Activity.
- No side effects hidden behind extensions that look like pure transforms.
- No `GlobalScope` — use lifecycle-aware scopes.
- No blocking calls on Main thread.
