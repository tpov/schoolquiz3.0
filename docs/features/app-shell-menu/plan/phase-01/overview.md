---
phase: phase-01
feature: app-shell-menu
date: 2026-04-18
---

# Phase-01: Walking Skeleton Integration Foundation

## Goal

Подключить готовый domain (229 JVM tests зелёных) к production adapter-слою: создать `UserStatsDataSource`/`UserStatsRepositoryImpl` цепочку, Koin-модули для трёх Gradle-модулей, точку старта `AppApplication`, и применить два одобренных user-approved domain delta (ADR-LEAD-02 signature + ADR-COMP-04/ADR-0011 новые interface файлы). После этой фазы `./gradlew :apps:android-next:assembleDebug` BUILD SUCCESSFUL и D1-D3 data integration tests зелёные.

## Scope

- Domain delta (2 modify + 2 new): `ObserveAppShellStateUseCase.kt` сигнатура, `ObserveAppShellStateUseCaseTest.kt` адаптация 9 тестов + stale closure test, `Navigator.kt` add, `RootComponent.kt` add
- Scaffold: `settings.gradle.kts` (add `:shared:core:stats`), convention plugin `schoolquiz.android.compose.library` + `schoolquiz.android.compose.application`, build.gradle.kts для 6 модулей
- `shared/core/stats`: новый KMP модуль — `UserStatsDataSource` interface
- `shared/feature/app-shell/data`: `UserStatsRepositoryImpl` (commonMain), `RawUserStats`, mapper, `AppShellDataModule`
- `platform/firebase`: `FirebaseUserStatsDataSource` (androidMain), `FirebaseModule`, dep на `:shared:core:stats`
- `apps/android-next`: `AppApplication.kt`, `AndroidManifest.xml` update (`android:name`)
- Все build.gradle.kts обновления для 6 Gradle-модулей (Compose BOM, Koin, Decompose, feature project refs)

## Layer

data + domain-delta + scaffold

## Role Inputs

- `backend.md` — scaffold, build files, domain delta, UserStatsRepositoryImpl, AppApplication
- `frontend.md` — none (UI не затрагивается в phase-01)
- `tests.md` — D1-D3 data integration tests, ObserveAppShellStateUseCaseTest адаптация, Koin wiring test

## Review Tags

- `concurrency-review`: `UserStatsRepositoryImpl.observeStats()` использует Flow + `.catch` (shared mutable state через Firestore snapshot listener), `ObserveAppShellStateUseCase` Flow operator chain с provider lambda (stale closure fix)

## State Matrix Coverage

Из spec `0-spec.md` State Matrix / FSM:

| FSM | Строки/ячейки | Статус |
|-----|---------------|--------|
| Cold Start FSM | R1 (initUseCase → default state) | Indirect — `UserStatsRepositoryImpl.currentStats()` вызывается внутри `InitializeAppShellUseCase`; D1-D3 тесты проверяют data flow |
| UserStats Pipeline | все строки (Firebase → RawUserStats → UserStats → AppShellState) | D1-D3 coverage |
| остальные 4 FSM (Back, RetapOutcome, DrawerGuard, SectionVisibility) | не затрагиваются | Walking Skeleton domain tests зелёные |

## Domain Contract Coverage

Walking Skeleton Variant Y — domain уже сгенерирован. Phase-01 покрывает:

| Элемент | Coverage |
|---------|----------|
| D1 — `UserStatsRepositoryImpl` round-trip (fake source → domain model) | Тест `UserStatsRepositoryImplTest.kt` |
| D2 — Koin wiring (all 3 modules resolvable) | Тест `KoinModuleWiringTest.kt` |
| D3 — Error recovery: `.catch { emit(UserStats.guest()) }` | Тест в `UserStatsRepositoryImplTest.kt` |
| ADR-LEAD-02 — stale closure отсутствует | Тест `ObserveAppShellStateUseCaseTest.kt` (stale closure test) |
| 45 pure domain scenarios | Остаются зелёными без изменений (Walking Skeleton) |
| 17 journeys | Journey 1 (cold start) частично — полная реализация в phase-04+07 |

## Traceability

| Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|--------------------------|-----------|--------------|-----------------|-------------|------------|
| Problem 1: Walking Skeleton integration — ни один consumer не подключён | backend-dev | `apps/android-next/MainActivity.kt:9`, `AndroidManifest.xml:7` | domain код не переписывается, только 2 delta + 2 новых interface | Создать `AppApplication`, Koin modules, `UserStatsRepositoryImpl`, connect dep graph | `./gradlew :apps:android-next:assembleDebug` BUILD SUCCESSFUL |
| Problem 3: kotlin-serialization plugin gap | backend-dev | `shared/feature/app-shell/domain/build.gradle.kts:1-18` | DEFERRED per ADR-LEAD-01 + ADR-COMP-05 — plugin НЕ добавляется в phase-01 | `@Serializable` и plugin отложены до future state-saving phase | Plugin отсутствует в domain build.gradle.kts → грубая проверка grep |
| Problem 4: Navigator interface missing | backend-dev | `find shared/feature/app-shell/domain -name "Navigator*"` → 0 files | domain purity: pure Kotlin interface, no Decompose | Создать `Navigator.kt` в `domain/navigation/` (3 строки) | `./gradlew :shared:feature:app-shell:domain:compileCommonMainKotlinMetadata` |
| Problem 5: apps/android-next stub (no Koin, no Compose, no feature deps) | backend-dev | `apps/android-next/build.gradle.kts:15-19`, `AndroidManifest.xml:3` | `startKoin` в AppApplication.onCreate() per OQ#3 resolution | Создать `AppApplication`, добавить deps в build.gradle.kts, update AndroidManifest | `./gradlew :apps:android-next:assembleDebug` |
| Problem 6: Feature module dependencies missing | backend-dev | `shared/feature/app-shell/data/build.gradle.kts:1-5`, `android/feature/app-shell/presentation/build.gradle.kts:9-12` | data→domain OK; presentation→domain OK; platform→core:stats OK | Добавить deps в каждый build.gradle.kts | `./gradlew :shared:feature:app-shell:data:compileKotlinJvm` |
| Problem 7: Firebase integration в KMP data module | backend-dev | `shared/feature/app-shell/data/src/androidMain/`, `platform/firebase/` | Firebase SDK только в `platform/firebase` androidMain; data/commonMain только через `UserStatsDataSource` interface (shared/core/stats) | `UserStatsDataSource` → `shared/core/stats`, `FirebaseUserStatsDataSource` → `platform/firebase`, `UserStatsRepositoryImpl` → data/commonMain | `./gradlew :shared:feature:app-shell:data:compileCommonMainKotlinMetadata` |
| Problem 2: Compose compiler gap | backend-dev | `buildSrc/AndroidApplicationConventionPlugin.kt:38-40`, `AndroidLibraryConventionPlugin.kt:38-40` | Compose только для Compose-consuming модулей (presentation, designsystem, app) — domain/data НЕ | Создать `schoolquiz.android.compose.library` plugin + `schoolquiz.android.compose.application`; apply к android-next, designsystem, navigation, presentation в будущих фазах | `./gradlew :apps:android-next:compileDebugKotlin` |

## New Files

```
buildSrc/src/main/kotlin/AndroidComposeLibraryConventionPlugin.kt
buildSrc/src/main/kotlin/AndroidComposeApplicationConventionPlugin.kt
shared/core/stats/build.gradle.kts
shared/core/stats/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/stats/UserStatsDataSource.kt
shared/core/stats/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/stats/RawUserStats.kt
shared/feature/app-shell/data/src/commonMain/kotlin/.../data/UserStatsRepositoryImpl.kt
shared/feature/app-shell/data/src/commonMain/kotlin/.../data/di/AppShellDataModule.kt
platform/firebase/src/main/kotlin/.../firebase/FirebaseUserStatsDataSource.kt
platform/firebase/src/main/kotlin/.../firebase/di/FirebaseModule.kt
apps/android-next/src/main/java/.../AppApplication.kt
shared/feature/app-shell/domain/src/commonMain/kotlin/.../domain/navigation/Navigator.kt
shared/feature/app-shell/domain/src/commonMain/kotlin/.../domain/navigation/RootComponent.kt
shared/feature/app-shell/data/src/jvmTest/kotlin/.../data/UserStatsRepositoryImplTest.kt
apps/android-next/src/test/java/.../KoinModuleWiringTest.kt
config/detekt/detekt.yml                                                    # B3 fix
```

## Modified Files

```
shared/feature/app-shell/domain/src/commonMain/kotlin/.../use_case/ObserveAppShellStateUseCase.kt  (ADR-LEAD-02: parameter rename)
shared/feature/app-shell/domain/src/commonTest/kotlin/.../use_case/ObserveAppShellStateUseCaseTest.kt  (9 test adapts + 1 new stale closure test)
shared/feature/app-shell/data/build.gradle.kts  (add deps: domain, koin-core)
platform/firebase/build.gradle.kts  (add: shared:core:stats dep)
apps/android-next/build.gradle.kts  (add: Compose, Koin, Decompose, feature project deps — Compose plugin via new convention)
apps/android-next/src/main/AndroidManifest.xml  (add android:name=".AppApplication")
settings.gradle.kts  (add: :shared:core:stats include)
buildSrc/build.gradle.kts  (add detekt + ktlint plugin classpath — B3 fix)
buildSrc/src/main/kotlin/AndroidLibraryConventionPlugin.kt  (apply detekt + ktlint — B3 fix)
buildSrc/src/main/kotlin/AndroidApplicationConventionPlugin.kt  (apply detekt + ktlint — B3 fix)
buildSrc/src/main/kotlin/KmpLibraryConventionPlugin.kt  (apply detekt + ktlint — B3 fix)
build.gradle.kts  (root — register aggregate detekt + ktlintCheck tasks — B3 fix)
gradle/libs.versions.toml  (add detekt + ktlint aliases — B3 fix)
```

## Deleted Files

none

## Dependencies

- Walking Skeleton domain (229 JVM tests green) — ALREADY EXISTS
- `libs.versions.toml` — все нужные aliases уже существуют (Compose BOM, Koin, Decompose, Firebase BOM, kotlinx-coroutines): VERIFIED

## Acceptance Criteria

1. `./gradlew :shared:feature:app-shell:domain:jvmTest` — 229+ tests зелёные (включая адаптированные ObserveAppShellStateUseCase тесты + новый stale closure test)
2. `./gradlew :shared:feature:app-shell:data:jvmTest` — D1 + D3 тесты зелёные (UserStatsRepositoryImpl round-trip + error recovery)
3. `./gradlew :apps:android-next:assembleDebug` — BUILD SUCCESSFUL
4. `./gradlew :shared:feature:app-shell:data:compileCommonMainKotlinMetadata` — компилируется без Firebase imports в commonMain
5. `Navigator.kt` и `RootComponent.kt` существуют в domain/navigation/ и компилируются
6. **Stateful field reset**: `UserStatsRepositoryImpl` не хранит mutable state между observer subscriptions (каждый `observeStats()` — новый cold flow)
7. `platform/firebase` зависит от `shared:core:stats`, НЕ от `shared:feature:app-shell:data`
8. `shared/feature/app-shell/domain/build.gradle.kts` — kotlin-serialization plugin отсутствует (AC per ADR-COMP-05)
9. Koin wiring test: `KoinModuleWiringTest` — `firebaseModule (replaced by testDataSourceModule) + appShellDataModule` резолвят без MissingPropertyException. `appShellPresentationModule` НЕ тестируется в phase-01 (создаётся в phase-04). Full-stack Koin wiring — в phase-07 (H5 fix).
10. Detekt + ktlint **реально работают** на active modules (B3 fix — Option A concrete):
    - `./gradlew tasks --all --no-configuration-cache | grep -E "^(detekt|ktlintCheck) -"` — aggregate tasks visible
    - `./gradlew detekt ktlintCheck --no-configuration-cache` — BUILD SUCCESSFUL на active modules (legacy excluded)
    - `config/detekt/detekt.yml` существует с project-wide конфигом
11. `ObserveAppShellStateUseCase` новая сигнатура `invoke(currentStateProvider: () -> AppShellState)` — stale closure test проходит

## Tests Required (TDD-style)

Параллельно с production code — test-dev пишет:

```
D1: UserStatsRepositoryImpl_observeStats_emits_mapped_domain_model:
  given FakeUserStatsDataSource emits RawUserStats(currentSkill=500)
  when observeStats() collected
  then emitted UserStats.currentSkill == 500

D2: UserStatsRepositoryImpl_currentStats_returns_domain_model:
  given FakeUserStatsDataSource.fetchRaw() returns RawUserStats(nickname="test")
  when currentStats() called
  then UserStats.nickname == "test"

D3: UserStatsRepositoryImpl_observeStats_on_error_emits_guest:
  given FakeUserStatsDataSource throws RuntimeException on observeRaw()
  when observeStats() collected
  then first emission == UserStats.guest()

D3b: UserStatsRepositoryImpl_currentStats_offline_returns_guest:
  given FakeUserStatsDataSource.fetchRaw() throws
  when currentStats() called
  then returns UserStats.guest()

stale_closure_absent:
  given ObserveAppShellStateUseCase with FakeUserStatsRepository
  when invoke { mutableStateRef.get() } where mutableStateRef changes between emissions
  then each emission reads currentStateProvider() not closure-captured initial value

koin_wiring:
  given startKoin { modules(testDataSourceModule, appShellDataModule) }
  when get<UserStatsRepository>() called
  then no MissingPropertyException, returns UserStatsRepositoryImpl
  (H5 fix: appShellPresentationModule NOT included in phase-01 — created in phase-04; full-stack in phase-07)

navigator_interface_compiles:
  given Navigator.kt exists in domain/navigation/
  when import in another module
  then compiles without unresolved reference

root_component_interface_compiles:
  given RootComponent.kt exists in domain/navigation/
  when collect appShellState: Flow<AppShellState>
  then pure coroutines Flow — no Decompose types in signature
```

## Validation

```bash
./gradlew :shared:feature:app-shell:domain:jvmTest --no-configuration-cache
# Expected: 229+ tests, 0 failures

./gradlew :shared:feature:app-shell:data:jvmTest --no-configuration-cache
# Expected: D1-D3 tests green

./gradlew :apps:android-next:assembleDebug --no-configuration-cache
# Expected: BUILD SUCCESSFUL

./gradlew :shared:feature:app-shell:data:compileCommonMainKotlinMetadata --no-configuration-cache
# Expected: no Firebase imports error

# Pattern invariant check (navigator interface absent = blocker):
find /home/Programming/Android/schoolquiz4.0/shared/feature/app-shell/domain -name "Navigator.kt"
# Expected: 1 file

# Pattern invariant check (serialization absent):
grep -r "kotlin-serialization\|plugin.serialization" /home/Programming/Android/schoolquiz4.0/shared/feature/app-shell/domain/build.gradle.kts
# Expected: empty output

# B3 fix: detekt + ktlint tasks registered И реально работают (AC 10)
./gradlew tasks --all --no-configuration-cache | grep -E "^(detekt|ktlintCheck) -"
# Expected: aggregate tasks `detekt - Run detekt across all active modules` + `ktlintCheck - Run ktlint check...`

./gradlew detekt ktlintCheck --no-configuration-cache
# Expected: BUILD SUCCESSFUL на active modules; legacy не трогается
```

## Handoff Notes

- Phase-02 (Design System) не зависит от phase-01 полного завершения, но требует что `apps/android-next/build.gradle.kts` уже содержит Compose BOM dep — это добавляется в phase-01 scaffold work. Если phase-02 стартует параллельно, backend-dev должен сначала закоммитить scaffold changes.
- `shared/core/stats` — НОВЫЙ модуль. backend-dev добавляет `include(":shared:core:stats")` в `settings.gradle.kts`. Директория `shared/core/stats/` создаётся с нуля.
- OQ-COMP-1 DEFERRED: `StackNavigation.replaceAll(vararg C)` signature — проверяется при реализации phase-04. Phase-01 не вызывает replaceAll.
- OQ-COMP-2 DEFERRED: `essentyLifecycle()` extension — проверяется при phase-07 MainActivity wiring.
- `FakeUserStatsDataSource` (для data tests) создаётся test-dev в `shared/feature/app-shell/data/src/jvmTest/` — отдельно от domain fake (`FakeUserStatsRepository` в domain/commonTest).
