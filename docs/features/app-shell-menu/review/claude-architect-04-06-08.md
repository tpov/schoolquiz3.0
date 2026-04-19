Все документы прочитаны. Компилирую выводы.

---

# Summary

**Verdict: REJECT**

Пять BLOCKERов — тест-документ (`04-testing.md`) имеет синтаксически неверный конструктор теста, API контракт (`06-api-contract.md`) содержит три несовместимых утверждения о DI-вайринге, storage-документ (`08-storage-model.md`) ссылается на устаревший Decompose-тип вместо coroutines-типа, и сигнатура `ScrollToTopRegistry` разделилась между тремя несовместимыми версиями. Любой из этих пяти пунктов приведёт к compile failure или runtime Koin-ошибке в phase-01.

---

# Findings

## BLOCKER

---

### BLOCKER-1 — `04-testing.md:createComponent()` — неверная сигнатура конструктора

**Category**: ModelMismatch  
**Location**: `04-testing.md:119–129`

**Evidence:**

Тест-хелпер передаёт `handleBackUseCase = HandleBackUseCase()` — параметра с таким именем нет в каноническом конструкторе `DefaultRootComponent`. Требуемый параметр `observeUseCase: ObserveAppShellStateUseCase` полностью отсутствует.

Канонический конструктор (per `01-architecture.md` L3 + ADR-LEAD-02):
```kotlin
class DefaultRootComponent(
    componentContext: ComponentContext,
    private val initUseCase: InitializeAppShellUseCase,
    private val navigateUseCase: NavigateUseCase,
    private val observeUseCase: ObserveAppShellStateUseCase,  // ← обязателен
    private val retapUseCase: OnTabRetapUseCase,
    private val userStatsRepository: UserStatsRepository,
    // handleBackUseCase — отсутствует (ADR-COMP-07: back via NavigateUseCase)
)
```

Тест-хелпер `04-testing.md:119–129`:
```kotlin
return DefaultRootComponent(
    initializeUseCase = InitializeAppShellUseCase(fakeRepo),
    navigateUseCase = NavigateUseCase(),
    handleBackUseCase = HandleBackUseCase(),  // ← не является параметром конструктора
    onTabRetapUseCase = OnTabRetapUseCase(),
    userStatsRepository = fakeRepo,
    // observeUseCase — отсутствует, compile failure
)
```

Ссылки: `01-architecture.md` DefaultRootComponent constructor; `ADR-COMP-07` (`03-decisions.md`): "handleBackUseCase: NOT injected — back via NavigateUseCase"; `ADR-LEAD-02` (`01-architecture.md:865-908`): ObserveAppShellStateUseCase обязателен.

**Impact:** Весь блок `DefaultRootComponentTest` не скомпилируется в phase-01. Тест на ADR-COMP-01 stale-closure fix (`when observeStats emits then state userStats updated without navigation change`) физически невозможен без `observeUseCase` в компоненте.

---

### BLOCKER-2 — `06-api-contract.md:267` — `single<RootComponent>` вместо `factory`

**Category**: ModelMismatch / Consistency  
**Location**: `06-api-contract.md:267`

**Evidence:**

```kotlin
// 06-api-contract.md:267
single<RootComponent> { (context: ComponentContext) ->
    DefaultRootComponent(...)
}
```

ADR-COMP-07 (`03-decisions.md:189-218`) явно требует `factory`:
> «`ComponentContext` привязан к lifecycle Activity. Если зарегистрировать как `single` — один instance будет shared между Activities при configuration change. Activity-scope ComponentContext в singleton = **memory leak** при смене конфигурации».
> Accepted decision: `factory { (ctx: ComponentContext) -> DefaultRootComponent(...) }`

Также: `NavigatorImpl` binding в том же модуле вызывает `get<RootComponent>()` без `parametersOf(ctx)`. При `factory` с параметром `ComponentContext` этот вызов провалится с Koin runtime error. Документ содержит два взаимоисключающих утверждения в пяти строках.

**Impact:** В release-сборке при rotation activity Koin создаёт новый `ComponentContext`, но `single` возвращает старый instance с dead lifecycle — `BackHandler` не работает. Либо runtime Koin error при попытке `get<RootComponent>()` без параметра.

---

### BLOCKER-3 — `06-api-contract.md:271–284` — `handleBackUseCase` внедрён, `userStatsRepository` пропущен

**Category**: ModelMismatch  
**Location**: `06-api-contract.md:271–284`

**Evidence:**

```kotlin
// 06-api-contract.md (appShellPresentationModule):
single<RootComponent> { (context: ComponentContext) ->
    DefaultRootComponent(
        componentContext = context,
        initializeUseCase = get(),
        navigateUseCase = get(),
        handleBackUseCase = get(),   // ← NOT in constructor (ADR-COMP-07)
        onTabRetapUseCase = get(),
        observeStateUseCase = get(), // ← param name mismatch: canonical = observeUseCase
        // userStatsRepository — отсутствует, но обязателен
    )
}
```

ADR-COMP-07 (`03-decisions.md:198-208`) явно: `// handleBackUseCase: NOT injected — back via NavigateUseCase`.  
Canonical constructor (`01-architecture.md:366-374`): требует `userStatsRepository: UserStatsRepository`.  
`02-behavior.md:89`: `NavigateUseCase handles ALL Destinations incl. Back` — подтверждает что отдельный HandleBackUseCase не нужен.

**Impact:** Compile failure. Koin binding ссылается на несуществующий параметр и не передаёт обязательный — `DefaultRootComponent` не будет создан.

---

### BLOCKER-4 — `ScrollToTopRegistry` — три несовместимых API между документами

**Category**: SpecContradiction  
**Location**: `04-testing.md:147–158`; `06-api-contract.md:332–335`; `03-decisions.md:ADR-COMP-06`

**Evidence:**

Три версии API в одном design pack:

**Версия A** (canonical) — `0-spec.md:82`, `01-architecture.md:336–339`, `06-api-contract.md:332`:
```kotlin
fun register(tab: Tab, hook: ScrollToTopHook)
fun unregister(tab: Tab, hook: ScrollToTopHook)  // per-tab map
fun current(tab: Tab): ScrollToTopHook?
```

**Версия B** — `03-decisions.md:ADR-COMP-06:170–175`:
```kotlin
fun unregister(hook: ScrollToTopHook) {   // no Tab parameter!
    if (_currentHook === hook) _currentHook = null  // single _currentHook
}
```

**Версия C** — `04-testing.md:147–158` (тест-код):
```kotlin
registry.register(hook1)             // no Tab parameter
registry.register(hook2)             // no Tab parameter
registry.unregister(hook1)           // no Tab parameter
assertSame(hook2, registry.currentHook)  // .currentHook, not .current(tab)
```

Версии B и C (single-hook без tab) несовместимы с Версией A (per-tab map). Тест-код не компилируется против spec API. Реализация по Версии B не может обслуживать несколько вкладок одновременно.

**Impact:** BLOCKER для phase-01 frontend-dev: реализует одну версию — тесты другой версии не компилируются. `unregister` с tab-параметром vs без tap-параметра — разные методы.

---

### BLOCKER-5 — `06-api-contract.md:257–261` — дублирующий `UserStatsDataSource` binding

**Category**: ModelMismatch / Consistency  
**Location**: `06-api-contract.md:257–261` vs `06-api-contract.md:288–290`

**Evidence:**

`appShellDataModule` (`06-api-contract.md:257-261`):
```kotlin
val appShellDataModule = module {
    single<UserStatsDataSource> { FirebaseUserStatsDataSource(get()) }  // binding #1
    single<UserStatsRepository> { UserStatsRepositoryImpl(get()) }
}
```

`firebasePlatformModule` (`06-api-contract.md:288-290`):
```kotlin
val firebasePlatformModule = module {
    single<UserStatsDataSource> { FirebaseUserStatsDataSource(Firebase.firestore) }  // binding #2
}
```

Оба модуля регистрируются в `startKoin { modules(firebasePlatformModule, appShellDataModule, ...) }`. Koin 3.5.6 с `allowOverride = false` (по умолчанию) бросает `DefinitionOverrideException` при дублирующем binding.

Кроме того, `shared/feature/app-shell/data/` не должен ссылаться на `platform/firebase` классы — это нарушает модульную границу. Per `01-architecture.md L3`: `appShellDataModule` должен содержать только `UserStatsRepository → UserStatsRepositoryImpl(get<UserStatsDataSource>())`, DataSource binding принадлежит `firebasePlatformModule`.

**Impact:** Runtime `KoinDefinitionOverrideException` при старте приложения. Неправильная модульная граница (shared/data → platform/firebase).

---

## HIGH

---

### HIGH-1 — `06-api-contract.md:441` — leftover `kotlinx-serialization` в domain

**Category**: SpecContradiction  
**Location**: `06-api-contract.md:441` (External SDK Consumption Map)

**Evidence:**

```
| kotlinx-serialization-json 1.6.3 | ... | shared/feature/app-shell/domain
  (для @Serializable Config — phase-01, OQ#2) | ...
```

Два ADR явно запрещают это:
- ADR-COMP-05 (`03-decisions.md:143-148`): «НЕ добавлять `@Serializable` на `TabConfig`... `kotlin-serialization` plugin в `build.gradle.kts` не добавляется на этом этапе».
- ADR-LEAD-01 (`01-architecture.md:923-924`): «Phase-01 не добавляет kotlinx-serialization в domain build.gradle.kts».

OQ#2 ("@Serializable Config") закрыт: ADR-COMP-05 + ADR-LEAD-01 — решение принято.

**Impact:** backend-dev читает 06-api-contract.md и добавляет serialization plugin в domain build.gradle.kts — нарушает ADR-LEAD-01 и Walking Skeleton contract.

---

### HIGH-2 — `08-storage-model.md:17` — `MutableValue<AppShellState>` (Decompose) вместо `MutableStateFlow`

**Category**: ModelMismatch  
**Location**: `08-storage-model.md:17`

**Evidence:**

```markdown
// 08-storage-model.md:17
| AppShellState | `MutableValue<AppShellState>` (Decompose) | Activity lifecycle | DefaultRootComponent._state |
```

Канонический тип (по ADR-0011 и `01-architecture.md:376-378`):
```kotlin
| `_state` | `MutableStateFlow<AppShellState>` | Pure coroutines;
  `override val appShellState = _state.asStateFlow()` |
```

`02-behavior.md:97` подтверждает: `STATE_UPDATE["_state.update { newState }\nMutableStateFlow<AppShellState>"]`.

`MutableValue<AppShellState>` — Decompose-тип. Его использование в domain state (если backend-dev следует 08-storage-model.md) нарушает Invariant #1 (domain purity) и ADR-0011, принятый именно для устранения Decompose из state container.

**Impact:** backend-dev может реализовать DefaultRootComponent с `MutableValue<AppShellState>` — Decompose тип в state container, `appShellState` станет `Value<AppShellState>` вместо `StateFlow<AppShellState>`, что ломает Android lifecycle-safe collection (`collectAsStateWithLifecycle`).

---

### HIGH-3 — `01-architecture.md:ADR-LEAD-02` — type error в `.catch` блоке

**Category**: ModelMismatch  
**Location**: `01-architecture.md:887-889`

**Evidence:**

```kotlin
// ADR-LEAD-02 (01-architecture.md:887-889):
scope.launch {
    observeUseCase { _state.value }
        .catch { emit(UserStats.guest()) }    // ← TYPE ERROR
        .collect { newState -> _state.update { newState } }
}
```

`observeUseCase` возвращает `Flow<AppShellState>` (use case maps stats → `currentStateProvider().copy(userStats=stats)`). `.catch { emit(X) }` должен получать X типа `AppShellState`, но `UserStats.guest()` — это `UserStats`.

Корректная версия — в `ADR-COMP-01` (`03-decisions.md:36-38`):
```kotlin
.catch { emit(AppShellState.fallback(UserStats.guest())) }  // ← правильный тип
```

**Impact:** Compile failure в DefaultRootComponent.kt если backend-dev копирует сниппет из ADR-LEAD-02 в 01-architecture.md.

---

### HIGH-4 — `04-testing.md` — нет явной таблицы AC→тест для всех 37 AC

**Category**: MissingTrace  
**Location**: `04-testing.md` (отсутствует раздел)

**Evidence:**

04-testing.md содержит категории тестов с примерами, но нет сквозной таблицы `AC# → test method`. Следующие ACs не имеют назначенного теста:

| AC | Описание | Статус в 04-testing.md |
|----|----------|----------------------|
| #9 | re-tap, scrollOffset=0, NO_OP, UI не делает ничего | Не упомянут даже в EdgeCases |
| #13 | placeholder visual rendering (icon+title+subtitle) | Нет теста |
| #14 | SchoolQuizTheme color/shape values | Нет теста |
| #16 | release build: DesignCatalog не виден | Нет теста |
| #17 | Crossfade 300ms виден | Нет теста |
| #18 | Drawer slide animation (не crossfade) | Нет теста |
| #20 | badges: nullable BadgeContent? = null | Нет теста |
| D2 | Koin singleton scope для UserStatsRepository | Не именован явно |

UI ACs (#13, #14, #16-18) отмечены как instrumented «если scope позволяет» — без конкретного plan или условий когда scope позволяет. Это открывает риск что phase-01 завершится без покрытия ~20% AC.

**Impact:** Plan stage не сможет верифицировать AC-completeness без ручного сопоставления. При phase review непонятно — AC#13 покрыт или нет.

---

## MEDIUM

---

### MEDIUM-1 — `RootComponent.appShellState`: `Flow` vs `StateFlow` расхождение

**Category**: Consistency  
**Location**: `06-api-contract.md:182`; `01-architecture.md` class diagram

**Evidence:**
- ADR-0011 + `06-api-contract.md:182`: `val appShellState: Flow<AppShellState>`
- `01-architecture.md` class diagram: `appShellState: StateFlow~AppShellState~`

`StateFlow` — подтип `Flow`, но существенно отличается: имеет `.value` (текущее), всегда имеет элемент, replay=1. Если интерфейс объявляет `Flow<AppShellState>` — UI не может использовать `.value` без downcast; если `StateFlow<AppShellState>` — может. Важно для `collectAsStateWithLifecycle` initial value.

**Impact:** Может вызвать `ClassCastException` в UI или потребовать дополнительного downcast.

---

### MEDIUM-2 — `OQ-COMP-4` остаётся OPEN в `03-decisions.md`

**Category**: MissingTrace  
**Location**: `03-decisions.md:227`

**Evidence:**
```
| OQ-COMP-4 | Koin параметризованный factory: `get<DefaultRootComponent>(parametersOf(ctx))` 
             в MainActivity — spec требования к DI entry point? | **OPEN** |
```

Это блокирует конкретную реализацию `MainActivity.kt` — как именно вызывать Koin для получения `DefaultRootComponent` с `ComponentContext` параметром. `01-architecture.md:636-638` показывает `get { parametersOf(componentContext) }` но это не подтверждено как ADR.

---

### MEDIUM-3 — `UserStatsRepositoryImpl.currentStats()` через `observeStats().first()` не использует `fetchRaw()`

**Category**: Consistency  
**Location**: `06-api-contract.md:224-228`

**Evidence:**
```kotlin
override suspend fun currentStats(): UserStats =
    runCatching { observeStats().first() }
        .getOrDefault(UserStats.guest())
```

`01-architecture.md` data layer class diagram показывает `UserStatsDataSource` с двумя методами: `observeRaw(): Flow<RawUserStats>` И `fetchRaw(): suspend RawUserStats`. `currentStats()` явно должен использовать `fetchRaw()` (single-shot) а не открывать новый snapshot listener через `observeStats()` только для `.first()`.

**Impact:** Каждый вызов `currentStats()` открывает Firestore snapshot listener и немедленно отписывается — wasteful but functional. При offline может не получить кэшированного значения.

---

### MEDIUM-4 — `04-testing.md` не содержит явного маппинга 17 Primary User Journeys → тесты

**Category**: MissingTrace  
**Location**: `04-testing.md` (отсутствует раздел)

**Evidence:** Spec описывает 17 Primary User Journeys (`0-spec.md:188-324`). `04-testing.md` не содержит раздел "Journey Coverage". Journeys 14b и 14c (guest → Events/Internet с прогрессивным unlock) особенно не тривиальны и требуют явного coverage.

---

## LOW

---

### LOW-1 — `TestComponentContext` — открытый вопрос без resolution

**Category**: MissingTrace  
**Location**: `04-testing.md:131-133`

**Evidence:**
```
REQUIRES: `TestComponentContext` — JVM stub for Decompose ComponentContext. 
Verify availability in research or create inline fake.
```

Нет подтверждения что такой stub существует в Decompose 3.1.0 test artifacts или в проекте. Если не существует — все `DefaultRootComponentTest` тесты требуют ручного написания полного fake перед запуском.

---

### LOW-2 — `04-testing.md:207` — AC#9 отсутствует в Edge Cases таблице

**Category**: MissingTrace  
**Location**: `04-testing.md:195-208`

**Evidence:** Edge Cases таблица содержит `onActiveTabRetap with backStack.first()` (POP_TO_ROOT) и "Re-tap on non-active tab → N/A". Но AC#9 (`scrollOffset=0, scrollToTop returns false, UI does nothing`) — явно не упомянут ни в Edge Cases, ни в DefaultRootComponentTest списке.

---

# Positive Notes

1. **FakeUserStatsRepository и FakeNavigator** (`04-testing.md:29-54`) — чистый pure Kotlin, без Android/SDK типов, соответствует Invariant #1 и `testing.md` fakes convention. `currentStatsCallCount` tracking — правильный паттерн.

2. **Koin test setup pattern** (`04-testing.md:182-191`) — `startKoin / stopKoin` с isolated test modules — корректный подход без Hilt, соответствует `testing.md`.

3. **Flow без Turbine** (`04-testing.md:214-219`) — явно задокументировано: `.take(1).toList()`, `UnconfinedTestDispatcher`, `StandardTestDispatcher + advanceUntilIdle()` — точное соответствие `testing.md` rules.

4. **ScrollToTopRegistry crossfade overlap test** (`04-testing.md:147-158`) — правильно описывает бизнес-кейс (incoming регистрируется до dispose outgoing). Identity-check логика корректна — проблема только в API signature (см. BLOCKER-4).

5. **DeepLink boundary** (`06-api-contract.md:153-163`) — правильно локализует Android `Intent` → `DeepLink` маппинг в `apps/android-next/MainActivity.kt`, domain остаётся platform-neutral. Соответствует ADR-0008 + NFR #1.

6. **`appShellPresentationModule` includes `HandleBackUseCase` как factory** (`06-api-contract.md:282`) — регистрация для прямого использования в тестах правильная (domain tests only, не в конструкторе DefaultRootComponent). Проблема только в том, что тот же документ инжектирует его в DefaultRootComponent.

7. **ADR-COMP-06 identity-check logic** (`03-decisions.md:169-175`) — `===` (reference equality) для unregister — технически правильное решение crossfade overlap. Нужно только выбрать canonical API (с Tab или без).

8. **08-storage-model.md** явно документирует "No Room Entities" — чёткая декларация scope без Room, что согласованно с Firebase-only design и ADR-LEAD-01.
