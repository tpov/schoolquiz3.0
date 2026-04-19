# Kotlin Conventions — Android

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
- Expose `Flow<T>` from repositories, collect in ViewModel lifecycle scope.

## Avoid

- No JSON parsing in Fragment, ViewModel, or Activity.
- No side effects hidden behind extensions that look like pure transforms.
- No `GlobalScope` — use lifecycle-aware scopes.
- No blocking calls on Main thread.
