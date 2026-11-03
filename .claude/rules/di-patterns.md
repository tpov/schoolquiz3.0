# DI Patterns — Koin

## Current project

- DI framework: Koin.
- Composition root: `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt`.
- Feature modules expose Koin `module { ... }` declarations near their data/presentation layer.
- Project does not use Hilt/Dagger; do not introduce Hilt/Dagger annotations without an explicit ADR.

## Rules

- Components/use cases receive dependencies through constructors.
- Koin modules wire implementations to domain interfaces.
- Compose screens do not call `getKoin()`, `koinInject()`, or `inject<...>()`; dependencies are passed through Decompose Components.
- Runtime parameters for Components use Koin `parametersOf` or explicit parent construction.
- One production binding per exposed type unless a named/qualified binding is documented.
- App composition root must include every production module needed by `apps/android-next`.

## Common patterns

```kotlin
val questPresentationModule = module {
    factory<MyQuestsComponent> { (componentContext: ComponentContext) ->
        DefaultMyQuestsComponent(
            componentContext = componentContext,
            observeMyQuestsUseCase = get(),
            navigator = get(),
        )
    }
}
```

```kotlin
val questDataModule = module {
    single<QuestRepository> {
        QuestRepositoryImpl(
            localDataSource = get(),
            remoteDataSource = get(),
        )
    }
}
```

## Avoid

- No service locator calls scattered through business logic or Compose screens.
- No duplicate `single<T>` / `factory<T>` for the same production type unless qualified.
- No singleton holding `Activity`, `Fragment`, `Context` with shorter lifecycle, or `ComponentContext`.
- No Hilt/Dagger annotations: `@Inject`, `@Module`, `@Provides`, `@Binds`, `@AndroidEntryPoint`, `@HiltViewModel`.
- No God-object module that wires unrelated feature areas.

## Review check

```bash
# Koin bindings touched by a phase
rg -n "(single|factory)<|module \{" apps android shared platform -g "*.kt"

# Direct Koin access from Compose screens
rg -n "getKoin\(|koinInject\(|inject<" android -g "**/presentation/src/main/**/ui/**/*.kt"

# Hilt/Dagger annotations should not appear
rg -n "@(Inject|Provides|Binds|Module|HiltAndroidApp|AndroidEntryPoint|HiltViewModel)" apps android shared platform -g "*.kt"
```

Matches in changed files are findings. Duplicate production bindings or missing app module registration are blockers.
