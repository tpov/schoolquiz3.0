# Use Cases — KMP Domain

## Pattern

```kotlin
class DoSomethingUseCase(
    private val someRepository: SomeRepository,
) {
    suspend operator fun invoke(param: ParamType): Result<ReturnType> {
        return someRepository.doSomething(param)
    }
}
```

## Rules

- Current project convention is `operator fun invoke(...)` for use cases.
- Constructor dependencies are domain repository interfaces or other use cases, not concrete data implementations.
- Return domain models, `Result<T>`, or `Flow<T>`; never transport DTOs, Room entities, Firebase snapshots, or Android framework types.
- Keep each use case focused on one business scenario.
- Connection-aware refresh, cascading sync decisions, validation, and multi-repository orchestration belong in use cases or explicitly named orchestration modules.
- DI wiring depends on Koin modules; use cases themselves do not know about Koin.

## When to create a use case

- Business logic coordinates multiple repositories.
- The behavior is reused by multiple Decompose components.
- The behavior has validation, retries, sync decisions, or state transition rules.
- If a Decompose component just delegates to one repository method without logic, a direct domain repository interface may be simpler.

## Presentation boundary

Decompose Components may call:
- UseCase methods
- Domain repository interface methods
- Navigator/domain navigation contracts

Compose Screens should call only:
- Component callbacks
- Local UI helpers/design-system components

Compose Screens should not call:
- UseCase methods
- Repository methods
- DAO/API/Firebase/Room/data-source methods
- Koin resolution (`getKoin`, `koinInject`, `inject`)

## Avoid

- No orchestration chains in Compose Screens.
- No Android framework classes in use case parameters or return types.
- No direct UI state mutation from use cases.
- No data-layer types in use case signatures.

## Review check

```bash
# Compose screens resolving dependencies or calling infrastructure directly
rg -n "getKoin\(|koinInject\(|inject<|UseCase|Repository|Dao|DataSource" android -g "**/presentation/src/main/**/ui/**/*.kt"

# Data-layer types in domain use cases
rg -n "^import .*\.(data|persistence|room|firebase)" shared -g "**/domain/src/commonMain/**/use_case/**/*.kt"
```

Matches in changed files are findings. Existing matches outside phase scope should be reported as existing debt.
