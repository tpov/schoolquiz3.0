# DI Patterns — Android

## General principles

- Single composition root where all dependencies are wired.
- Constructor injection preferred — dependencies are explicit, testable, discoverable.
- Interfaces for repositories and data sources — concrete implementations resolved at composition root.
- Scope lifecycle: app-scoped singletons, activity-scoped, ViewModel-scoped.

## Common approaches

| Approach | Composition Root | ViewModel Creation | Test Setup |
|----------|-----------------|-------------------|------------|
| Hilt/Dagger | `@Module` + `@Component` | `@HiltViewModel` + `by viewModels()` | `@HiltAndroidTest` + test modules |
| Manual DI | App-level factory / service locator from `PROJECT-CONTEXT.md` | Custom `ViewModelFactory` | Direct construction with fakes |
| Koin | `module { }` DSL | `by viewModel()` | `startKoin { modules(testModule) }` |

## Rules

- Consult PROJECT-CONTEXT.md for which DI approach the project uses.
- ViewModel must NOT create Repository directly — resolve through DI.
- UseCase receives Repository interface, never DAO or API directly.
- Screen/Fragment must NOT instantiate UseCase or Repository — use ViewModel.
- Don't mix DI styles in the same module.

## Manual DI pattern (when used)

```kotlin
// Factory at app level
class AppCompositionRoot(private val context: Context) {
    val database by lazy { Room.databaseBuilder(...).build() }
    val someRepository by lazy { SomeRepositoryImpl(database.someDao(), api) }
    val someViewModelFactory by lazy { SomeViewModelFactory(someRepository) }
}

// ViewModel Factory
class SomeViewModelFactory(
    private val repository: SomeRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SomeViewModel(repository) as T
    }
}
```

## Exclusive binding rule (Dagger / Hilt)

Применяется если project использует Dagger 2 или Hilt (см. `.claude/PROJECT-CONTEXT.md` для подтверждения). Для Koin это правило не применимо — там нет constructor injection, только `module { }` DSL.

Для одного класса — **либо** `@Inject constructor` **либо** `@Provides`/`@Binds` в module — **не оба одновременно**. Иначе Dagger может молча создать два "singleton" instance с разным состоянием.

**Нарушение** (пример для Hilt; аналогично для Dagger 2 manual):
```kotlin
// ❌ @Inject constructor в классе
@Singleton
class CallSessionStoreImpl @Inject constructor(
    private val dispatcher: CoroutineDispatcher,
) : CallSessionStore { /* ... */ }

// И одновременно @Provides в module
@Module
@InstallIn(SingletonComponent::class)   // Hilt-specific; для Dagger 2 manual — @Component вместо @InstallIn
object CallModule {
    @Provides
    @Singleton
    fun provideCallSessionStore(dispatcher: CoroutineDispatcher): CallSessionStore =
        CallSessionStoreImpl(dispatcher)   // ❌ может создать ВТОРОЙ instance
}
```

**Правильно — один подход:**

### Вариант 1 — constructor injection + `@Binds`

Для Hilt:
```kotlin
@Singleton
class CallSessionStoreImpl @Inject constructor(
    private val dispatcher: CoroutineDispatcher,
) : CallSessionStore { /* ... */ }

@Module
@InstallIn(SingletonComponent::class)
abstract class CallModule {
    @Binds
    abstract fun bindCallSessionStore(impl: CallSessionStoreImpl): CallSessionStore
}
```

Для Dagger 2 manual:
```kotlin
@Singleton
class CallSessionStoreImpl @Inject constructor(
    private val dispatcher: CoroutineDispatcher,
) : CallSessionStore { /* ... */ }

@Module
abstract class CallModule {
    @Binds
    abstract fun bindCallSessionStore(impl: CallSessionStoreImpl): CallSessionStore
}
// В AppComponent: @Component(modules = [CallModule::class, ...])
```

### Вариант 2 — `@Provides` без `@Inject constructor`

Когда имплементация требует специфичной construction логики (file I/O, config read):

```kotlin
class CallSessionStoreImpl(
    private val dispatcher: CoroutineDispatcher,
) : CallSessionStore { /* ... */ }   // НЕТ @Inject constructor

// Hilt:
@Module
@InstallIn(SingletonComponent::class)
object CallModule {
    @Provides
    @Singleton
    fun provideCallSessionStore(dispatcher: CoroutineDispatcher): CallSessionStore =
        CallSessionStoreImpl(dispatcher)
}

// Dagger 2 manual: тот же @Module + @Provides, без @InstallIn
```

### Koin аналог (если project использует Koin)

Koin не имеет этой проблемы, потому что нет constructor injection. Но есть своя — дублирование `single { }` для одного типа:

```kotlin
// ❌ два single для одного типа
val moduleA = module { single<CallSessionStore> { CallSessionStoreImpl(get()) } }
val moduleB = module { single<CallSessionStore> { AnotherImpl(get()) } }
// startKoin { modules(moduleA, moduleB) } — Koin бросит exception или использует последний

// ✅ один single, один модуль владеет типом
val callModule = module { single<CallSessionStore> { CallSessionStoreImpl(get()) } }
```

## Avoid

- No service locator calls scattered through business logic — keep at composition root.
- No late-init var for dependencies that should be constructor-injected.
- No singletons that hold Activity/Fragment references (memory leak).
- No God-object factory that creates everything — split by feature area.
- **No mixing `@Inject constructor` + `@Provides`/`@Binds` for the same class** — выбери один подход. Иначе молчаливое duplicate binding и два "singleton" instance.

## Review check (grep-паттерны для architect-reviewer)

```bash
# Найди все классы с @Inject constructor
grep -rnE "class\s+(\w+).*@Inject\s+constructor" <module>/ --include="*.kt"

# Для каждого найденного ClassName — проверь нет ли @Provides/@Binds возвращающего этот тип
# (ручной cross-check: для каждого ClassName из списка выше:)
grep -rnE "@(Provides|Binds).*\bClassName\b|\:\s*\bClassName\b\s*\{|\)\s*:\s*\bClassName\b" <module>/ --include="*.kt"
```

Если класс присутствует в обоих списках — blocker; предложи убрать одно из двух (обычно убирается `@Provides` если есть `@Inject constructor` + `@Binds`).
