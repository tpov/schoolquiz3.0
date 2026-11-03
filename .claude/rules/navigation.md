---
paths:
  - "android/**/presentation/src/main/**/*.kt"
  - "shared/feature/app-shell/domain/src/commonMain/**/*.kt"
---

# Navigation — Decompose + Compose

## Project setup

- Current Android app entry point: `apps/android-next`.
- Presentation modules live under `android/feature/<slug>/presentation`.
- New screen state holders are Decompose `Component`s, usually `Default*Component`.
- Compose `Screen` functions render immutable state and emit callbacks; they do not own navigation, Koin resolution, repository calls, or sync orchestration.
- Navigation state/contracts live in `shared/feature/app-shell/domain`; Android wiring lives in `android/feature/app-shell/presentation`.
- DI is Koin. Composition root is `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt`.
- AndroidX `ViewModel`, Compose Navigation, Fragment screens, or direct `koinInject()` in screens require an explicit phase/ADR because they are not the current default architecture.

## Component patterns

```kotlin
interface ExampleComponent {
    val state: StateFlow<ExampleState>
    fun onPrimaryAction()
}

class DefaultExampleComponent(
    componentContext: ComponentContext,
    private val observeExample: ObserveExampleUseCase,
    private val navigator: Navigator,
) : ExampleComponent,
    ComponentContext by componentContext {
    // component scope is tied to Decompose lifecycle
}
```

## Rules

- Define navigation destinations and transitions in the app-shell domain/navigation contracts first.
- Components call use cases/domain repository interfaces; Compose screens call component callbacks.
- Koin factories create components and pass runtime parameters with `parametersOf` when needed.
- Use `ComponentContext` / Decompose lifecycle for component-scoped coroutines and cleanup.
- Keep screen functions free of Koin, Room, Firebase, DAO, Repository, and UseCase imports.
- If adding a new destination variant, update destination model, transition handler, labels, Koin wiring, and app-shell tests atomically.

## Avoid

- No new AndroidX `ViewModel` for feature presentation unless the phase explicitly says this feature is an exception.
- No direct `getKoin()`, `koinInject()`, or `inject<...>()` inside Compose screens.
- No Fragment/Compose Navigation route strings for full-screen feature navigation unless an ADR changes the navigation stack.
- No business cleanup in Activity `onDestroy`; see `.claude/rules/lifecycle.md`.

## Review check

```bash
# Koin access inside Compose ui package
rg -n "getKoin\(|koinInject\(|inject<" android -g "**/presentation/src/main/**/ui/**/*.kt"

# Compose screens importing data/domain infrastructure directly
rg -n "^import .*\\.(Dao|Entity|DataSource|Repository|UseCase|Firebase|Room)" android -g "**/presentation/src/main/**/ui/**/*.kt"

# New AndroidX ViewModel usage in presentation
rg -n "androidx\\.lifecycle\\.(ViewModel|viewModelScope)|: ViewModel\\(" android -g "**/presentation/src/main/**/*.kt"
```

Matches in changed files are findings. Existing matches outside the phase scope should be reported as existing debt, not silently fixed.
