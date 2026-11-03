# Testing — schoolquiz4.0

## Test infrastructure

- KMP JVM tests use `commonTest`/`jvmTest` with Kotlin test, JUnit 4 where configured, coroutines-test, and fakes.
- Android/app JVM tests use `src/test` and run through Gradle `test`.
- Instrumented tests use `src/androidTest` or KMP `src/androidInstrumentedTest` when migrated.
- DI is Koin; tests should prefer direct construction with fakes or explicit test modules. No Hilt/Dagger test infrastructure.
- Project convention: no Turbine; Flow tests use `.take()`, `.toList()`, direct `StateFlow.value`, or controlled coroutine dispatchers.

## Canonical commands

- Full local quality gate: `./gradlew ciCheck --no-configuration-cache`
- App debug build: `./gradlew :apps:android-next:assembleDebug --no-configuration-cache`
- Android/app JVM unit tests: `./gradlew test --no-configuration-cache`
- KMP JVM tests: `./gradlew allTests --no-configuration-cache`
- Static analysis and formatting: `./gradlew detekt ktlintCheck --no-configuration-cache`
- Instrumented test APK build: `./gradlew assembleDebugAndroidTest --no-configuration-cache`
- Connected instrumented tests: `./gradlew connectedAndroidTest` with a connected device.

`test` and `allTests` cover different task families in this project; do not claim the full gate passed after running only one of them.

## Test locations

| Area | Main path | Test path |
|------|-----------|-----------|
| KMP feature domain | `shared/feature/<slug>/domain/src/commonMain/` | `shared/feature/<slug>/domain/src/commonTest/` |
| KMP feature data | `shared/feature/<slug>/data/src/commonMain/` | `shared/feature/<slug>/data/src/commonTest/` |
| KMP core | `shared/core/**/src/commonMain/` | `shared/core/**/src/commonTest/` |
| Android presentation | `android/feature/<slug>/presentation/src/main/` | `android/feature/<slug>/presentation/src/test/` |
| Android app | `apps/android-next/src/main/` | `apps/android-next/src/test/` |
| Instrumented | module-specific `src/androidTest/` or `src/androidInstrumentedTest/` | same module |

## Fakes convention

- Use existing fakes first, especially under `src/commonTest/.../fake` or feature-local `src/test/.../fake`.
- Repository fake: in-memory backing state with call tracking where behavior matters.
- DAO fake: in-memory collection plus `MutableStateFlow` when observation is part of the contract.
- API/remote fake: enum/flag-driven responses, request counters, and last payload capture.
- Do not create duplicate fakes if a canonical fake already exists in the module/test-fixtures.

## Coroutines test patterns

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
@Test
fun `given pending sync when retry succeeds then state is updated`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    // arrange with fakes
    // act
    // advanceUntilIdle()
    // assert
}
```

## Rules

- Start with deterministic JVM tests for policies, state transitions, mappers, repositories, and component behavior.
- For Decompose components, construct the component with `DefaultComponentContext(LifecycleRegistry())` or existing test fixture helpers.
- For Android framework-bound behavior, either use instrumented tests or extract pure logic behind a testable boundary.
- `test-dev` adds tests but does not modify production code.
- Never delete test files unless listed in the phase `overview.md` section "Deleted Files".
- TDD-style work means tests are written in parallel with production code, not after the feature is complete.
- When code composes paths, channels, config, or generated IDs, include at least one test for the fully resolved value.
- **Mapper round-trip field-level assertions** (menu-refactor retro fix): Mapper round-trip tests (Entity ↔ Domain, Domain ↔ DTO) MUST verify each field explicitly, не только `equals(original)`. Используй field-by-field assertions или property-based testing с field introspection. `equals()` on data class с дефолтными значениями (`val pictureUrl: String? = null`) скрывает field-drop bugs — `original.pictureUrl == null` AND `mapped.pictureUrl == null` returns true даже если mapper случайно dropped non-null value. **Source rationale**: menu-refactor Bug #5 — `pictureUrl` dropped в `CatalogMapper.kt` chain Entity→Domain→UI; round-trip tests passed because `equals()` returned true on default-null values.

## Тесты по исходникам: только отрицательные утверждения

Тест, читающий текст исходного файла вместо поведения, доказывает мало и легко закрепляет неверную реализацию. Разрешён он в двух случаях:

- **Отрицательное утверждение о всём дереве**, которое поведением не проверить: «поля с таким именем нет нигде», «второй копии этой логики не появилось», «прямой вызов снят». Такой тест обязан считать просмотренные файлы и падать, если насчитал подозрительно мало: пустой результат должен значить «не нашли», а не «не искали».
- **Граница, которую на JVM не поднять**: серверный `index.js` требует firebase-admin и учётных данных, поэтому «обработчик очереди и callable зовут одно тело» проверяется чтением исходника. Прецеденты: `arena-request-document.test.js`, `deferred-actions.test.js`.

Во всех остальных случаях проверяется поведение. Признак нарушения: тест ищет подстроку в файле там, где то же самое утверждение можно получить, вызвав код с фейком. **Source rationale**: разбор эпика 7 — тест «третий способ удаления реализован» искал слово `archived` в файле репозитория; способ был реализован ровно наоборот критерию, и тест оставался зелёным.

## Общие имена клиента и сервера — со сторожем

Любое имя, которое обе стороны обязаны писать одинаково (операция очереди, тип события push, префикс `entity_ref`, поле тела мутации, ключ ответа callable), заводится **вместе со сторожем**, который читает исходник другой стороны и падает на расхождении. Образцы: `OutboxOperationsRegistryTest` (читает реестр `functions/index.js`), `PushEventsRegistryTest` (читает `functions/push-notifications.js`).

Без сторожа каждая сторона проверяет только себя, и расхождение живёт до боя. **Source rationale**: эпик 10 — сервер писал `qualification.AUTHOR_RATING_CHANGED`, клиент ждал `profile.QUALIFICATION_CHANGED`; уведомление о квалификации не показалось бы никогда, а оба набора тестов были зелёными.

Отдельно для серверной части: каждый файл `*.test.js` в `functions/` и `scripts/` обязан быть подключён в строку `test` своего `package.json`. Тест, лежащий в дереве и не запускаемый, — это не покрытие. **Source rationale**: эпик 5 — параллельная сессия переписала `package.json`, и новый тест выпал из прогона незамеченным.

## Avoid

- No live network or production credentials in unit tests.
- No testing private methods; test behavior through public API.
- No claiming quality gate passed if a command could not run.
- No deleting tests to achieve green build.
- No Hilt test modules or `@HiltAndroidTest`.
- No Turbine unless a separate dependency/ADR adds it.
