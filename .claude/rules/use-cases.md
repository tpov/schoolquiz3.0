# Use Cases — Android

## Pattern

```kotlin
class DoSomethingUseCase(
    private val someRepository: SomeRepository
) {
    suspend operator fun invoke(param: ParamType): Result<ReturnType> {
        // single business scenario
        return Result.success(someRepository.doSomething(param))
    }
}
```

## Rules

- Single-action use cases: `operator fun invoke(...)`.
- Constructor injection of repository interfaces only — never concrete implementations.
- Return domain models, `Result<T>`, or `Flow<T>` — never transport details.
- Keep each use case to one business scenario.
- Connection-aware refresh, orchestration of multiple repos — in use cases, not ViewModels.
- DI wiring depends on project setup — consult PROJECT-CONTEXT.md.

## When to create a use case

- Business logic that coordinates multiple repositories.
- Logic reused by multiple ViewModels.
- Complex validation or transformation chains.
- If ViewModel just delegates to one repository method without logic — skip the use case.

## Avoid

- No orchestration chains in ViewModel if they are reusable business behavior.
- No Activities, Fragments in use case parameters.
- No direct UI state mutation from use case.
- No Android framework classes in use case.

## Activity/Fragment discipline

Activity/Fragment **не вызывает напрямую**:
- Repository / Repository interface методы
- Provider / Manager / Service / Store методы (`CallProvider`, `CallSessionStore`, `LiveKitManager`, etc.)
- UseCase instance методы (use cases инжектятся в ViewModel, не в Activity)
- DAO / API / Retrofit service

Activity/Fragment вызывает **только**:
- ViewModel публичный API (методы, StateFlow, SharedFlow, события)
- Navigation компоненты (NavController, NavHost, Fragment transactions)
- Android system компоненты (startActivity, sendBroadcast, system services) — строго для platform dispatch
- View binding / view setup

Если Activity нужна бизнес-логика → перенеси её в ViewModel. Если Activity хочет вызвать `provider.endCall()` или `store.updateActiveCallId()` — **это сигнал что здесь должен быть ViewModel** (или existing ViewModel должен получить этот метод).

### Пример нарушения

```kotlin
// ❌ Activity координирует бизнес-логику напрямую
class VoipActiveCallActivity : AppCompatActivity() {

    @Inject lateinit var provider: CallProvider
    @Inject lateinit var store: CallSessionStore

    override fun onDestroy() {
        super.onDestroy()
        provider.endCall()              // direct business call
        store.updateActiveCallId(null)  // direct state mutation
    }
}
```

```kotlin
// ✅ Activity делегирует ViewModel
class VoipActiveCallActivity : AppCompatActivity() {

    private val viewModel: VoipCallViewModel by viewModels { factory }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.onActivityDestroyed(isFinishing, isChangingConfigurations)
    }
}

class VoipCallViewModel(
    private val endCallUseCase: EndCallUseCase,
    private val callSessionStore: CallSessionStore,
) : ViewModel() {

    fun onActivityDestroyed(isFinishing: Boolean, isChangingConfigurations: Boolean) {
        // ViewModel применяет lifecycle логику — см. .claude/rules/lifecycle.md
        if (isFinishing && !isChangingConfigurations) {
            endCallUseCase()
        }
    }
}
```

## Review check (grep-паттерны для architect-reviewer)

```bash
# Нарушение: Activity/Fragment вызывает Provider/Store/Manager/Service методы
grep -rnE "(provider|store|manager|service)\.(start|end|update|send|cancel|kill|dispose|disconnect|hang|answer)" \
    <ui_path>/ --include="*Activity.kt" --include="*Fragment.kt"

# Нарушение: Activity/Fragment использует Repository как поле
grep -rnE "(private\s+)?(val|var)\s+\w*[Rr]epository\b" \
    <ui_path>/ --include="*Activity.kt" --include="*Fragment.kt"

# Нарушение: Activity/Fragment инжектит UseCase напрямую
grep -rnE "@Inject\s+(lateinit\s+)?var\s+\w*UseCase" \
    <ui_path>/ --include="*Activity.kt" --include="*Fragment.kt"
```

Matches = blocker; предложи перенос вызовов в ViewModel.
