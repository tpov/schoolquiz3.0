# ADR-0009: Dependency Injection

## Status
Accepted — 2026-04-16

## Context

В legacy использовался Dagger 2 (`com.google.dagger:dagger:2.49`) с ручными компонентами. Hilt как надстройка над Dagger там отсутствовал. Это Android-only решение с compile-time KAPT генерацией.

В новой архитектуре:

1. **KMP-first** (см. ADR-0002). Dagger и Hilt не поддерживают KMP — работают только на Android.
2. **Server-модули** на чистом JVM (не Android) — им нужен тот же DI, что и клиенту, чтобы переиспользовать domain-сервисы (`shared/feature/*/domain`).
3. **Decompose** (ADR-0008) требует фабрик для `Component`, принимающих `ComponentContext`. DI-библиотека должна уметь это делать.

## Decision

### Библиотека: Koin

**Koin** (https://insert-koin.io) — выбран для всех модулей.

Почему Koin, а не Dagger 2 / Hilt / kotlin-inject:

- **KMP-native.** Koin работает на Android, JVM, iOS, JS, Native — одним и тем же API.
- **Runtime-based, без code generation.** Нет KAPT/KSP-шага — быстрее компиляция, проще отладка. Ценой — ошибки регистрации вылезают в runtime, не в compile time. Для проекта нашего размера это приемлемо.
- **Простой DSL.** `module { single { … } }`, без @Inject / @Provides / компонентов.
- **Интеграция с Decompose.** Есть `factory` для создания Component'ов с переданным `ComponentContext`.
- **Server-поддержка.** Koin-core работает на JVM, не требует Android-контекста.

### Структура DI-модулей

**Каждая фича владеет своим DI-модулем.** Модули собираются в приложении при старте.

```
shared/feature/quiz/data/src/commonMain/kotlin/di/
    QuizDataModule.kt          // репозитории + data sources
shared/feature/quiz/domain/src/commonMain/kotlin/di/
    QuizDomainModule.kt        // use cases + доменные сервисы
android/feature/quiz/presentation/src/main/kotlin/di/
    QuizPresentationModule.kt  // Component factories, ViewModels
```

Пример содержимого `QuizDataModule.kt`:

```kotlin
val quizDataModule = module {
    single<QuestRepository> { DefaultQuestRepository(get(), get()) }
    single<QuestionRepository> { DefaultQuestionRepository(get()) }
    single<OutgoingMutationQueue> { RoomOutgoingMutationQueue(get()) }
    factory<QuestSyncStrategy> { DefaultQuestSyncStrategy(get(), get()) }
}
```

Пример `QuizPresentationModule.kt` с Decompose:

```kotlin
val quizPresentationModule = module {
    factory { (componentContext: ComponentContext, questId: QuestId) ->
        DefaultQuestScreenComponent(
            componentContext = componentContext,
            questId = questId,
            loadQuest = get<LoadQuestUseCase>(),
            // ...
        )
    }
}
```

### Собирание всех модулей в `apps/android-next`

`MainActivity` инициализирует Koin с перечислением всех модулей:

```kotlin
startKoin {
    androidContext(application)
    modules(
        // core
        coreSyncModule, coreNetworkModule, corePersistenceModule,
        // platform
        firebasePlatformModule, androidServicesPlatformModule,
        // features — data
        authDataModule, profileDataModule, quizDataModule, qualificationDataModule,
        economyDataModule, minigameDataModule, /* ... */
        // features — domain
        authDomainModule, profileDomainModule, quizDomainModule, /* ... */
        // features — presentation
        appShellPresentationModule, quizPresentationModule, /* ... */
    )
}
```

Этот список растёт только в `apps/android-next` и (потом) в `apps/ios-next`.

### Серверные модули

`server/functions` и `server/workers/*` используют **тот же Koin**, подключают только те модули, которые им нужны (обычно `shared/core/*` и нужные domain-модули). Presentation-модули они не видят.

Пример `server/workers/sync/Main.kt`:

```kotlin
fun main() {
    startKoin {
        modules(coreSyncModule, quizDomainModule, syncWorkerModule)
    }
    // ...
}
```

### Тестирование

- **Unit-тесты** на domain/data — классы создаются **без Koin**, напрямую (конструктор-инъекция). Koin нужен только в runtime-сборке приложения.
- **Интеграционные тесты** — могут использовать `koinApplication { modules(testModules) }` для подмены реальных реализаций на test doubles.

## Consequences

### Плюсы
- Один DI-механизм для клиента (Android + iOS в будущем) и сервера.
- Никакого code generation — быстрые сборки.
- Простой DSL, быстрый onboarding.
- Интеграция с Decompose через factory с параметрами.
- Тесты domain/data не зависят от DI — чистые конструкторы.

### Минусы
- **Runtime ошибки вместо compile-time.** Забыл зарегистрировать зависимость → краш при старте приложения. Митигация: автоматические smoke-тесты в CI (`verifyModules()`), которые запускают Koin со всеми модулями и проверяют, что все resolve'ятся.
- **Меньше статической верификации** по сравнению с Dagger 2. Для больших проектов это минус; для нашего — приемлемо.
- Обучающий материал Koin менее развит, чем Dagger Hilt; встречаются устаревшие гайды.

### Правила
1. Каждый leaf-модуль Gradle (`shared/feature/X/data`, `android/feature/X/presentation`, etc.) имеет **ровно один** Koin-module, экспортируется как top-level `val xxxModule`.
2. Фича-модуль **не** пишет глобальные singleton'ы уровня приложения. Только свои классы.
3. Регистрация всех модулей — **только в `apps/*` и `server/*` entry point'ах**.
4. Зависимости между фичами — через интерфейсы в domain. Data-реализации регистрируются в своей фиче и подставляются Koin'ом.
5. CI включает smoke-тест «все модули резолвятся».

## Mapping из legacy

| Legacy (Dagger 2) | Новый (Koin) |
|---|---|
| `@Component interface AppComponent` | `startKoin { modules(...) }` в `MainActivity` |
| `@Module class NetworkModule` | `val networkModule = module { ... }` в соответствующем Gradle-модуле |
| `@Inject constructor(...)` | Явная регистрация `single { ... }` или `factory { ... }` |
| `@Provides fun provideX()` | `single<X> { ... }` |
| Скоупы `@Singleton`, `@ActivityScoped` | `single` (app-wide), `factory` (каждый раз новый), `scoped` (для Decompose ComponentContext) |

## Notes

Библиотеки — `libs.koin.core`, `libs.koin.android`, `libs.koin.androidx.compose` (см. `libs.versions.toml`). Для server-модулей — только `koin-core`. Интеграция с Decompose — через `factory { (context: ComponentContext) -> ... }`.
