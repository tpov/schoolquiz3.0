---
date: 2026-04-18
researcher: web-researcher (Claude)
feature: app-shell-menu
---

# Prior Art: App Shell Menu — SDK Research

Web research findings для 5 SDK, используемых в фиче. Каждый факт со ссылкой на источник.

---

## 1. Decompose 3.1.0

### Verified Facts

| Факт | Источник |
|------|----------|
| `childStack(source, serializer, initialConfiguration, handleBackButton, key, childFactory)` — полная сигнатура | [arkivanov.github.io/Decompose/navigation/stack/overview](https://arkivanov.github.io/Decompose/navigation/stack/overview/) |
| `serializer = null` → state-saving отключён, стек сбрасывается при process death | [Child Stack overview](https://arkivanov.github.io/Decompose/navigation/stack/overview/) |
| Несколько `childStack` в одном компоненте → каждый требует уникального `key`; ключи уникальны только в пределах родителя | [Child Stack overview](https://arkivanov.github.io/Decompose/navigation/stack/overview/) |
| `bringToFront(config)` — рекомендуемый паттерн для tab navigation (не `push`), сохраняет состояние уже существующего экрана | [Navigation overview](https://arkivanov.github.io/Decompose/navigation/overview/) + [Decompose Navigation — Speednet](https://speednetsoftware.com/decompose-navigation/) |
| Конфигурации в одном стеке должны быть уникальны по `equals`; экспериментальный `DecomposeExperimentFlags.duplicateConfigurationsEnabled` для обхода | [Child Stack overview](https://arkivanov.github.io/Decompose/navigation/stack/overview/) |
| `Child#key` — тип `String`; используется как `key` в Compose для предотвращения краша "Key XYZ was used multiple times" | [Decompose Releases](https://github.com/arkivanov/Decompose/releases) |
| `Children(stack, animation, content)` из `decompose-extensions-compose` — рендер активного child; `subscribeAsState()` конвертирует `Value<ChildStack>` → `State<ChildStack>` | [Extensions for Compose](https://arkivanov.github.io/Decompose/extensions/compose/) |
| Миграция state-saving с `kotlin-parcelize` на `kotlinx-serialization` — issue #470 в Decompose (причина: parcelize dropped in K2) | [GitHub Issue #470](https://github.com/arkivanov/Decompose/issues/470) |
| Decompose 3.x использует JSON-формат для state-saving: `Serialize as JSON → encode via NSCoder (iOS), file system (JVM), localStorage (Web)` | [State Preservation docs](https://arkivanov.github.io/Decompose/component/state-preservation/) |

### Essenty BackHandler vs Jetpack BackHandler

| Аспект | Essenty (Decompose) | Jetpack (activity-compose) |
|--------|--------------------|-----------------------------|
| Платформа | KMP (multiplatform) | Android only |
| Слой | Business logic (ComponentContext) | UI (Composable) |
| Область действия | Иерархическая (component tree) | Глобальная (root OnBackPressedDispatcher) |
| Проблема в child | Нет — корректно scope | **BLOCKER**: Jetpack handler в child перехватывает back независимо от иерархии, игнорирует parent state |
| Тестируемость | JVM-тесты без Android runtime | Требует Compose environment |
| Predictive back | Все платформы через `BackDispatcher` | Android U+ only |

**Вывод**: Essenty `BackHandler` (из `ComponentContext`) — обязательный выбор для root-level FSM. Использование Jetpack `BackHandler` в child components ломает иерархию обработки back. Источник: [Decompose Back Button docs](https://arkivanov.github.io/Decompose/component/back-button/)

### Known Issues

- `DecomposeExperimentFlags.duplicateConfigurationsEnabled = true` — экспериментальный флаг, нестабилен; не использовать в production для 4 tab стеков. Каждый tab должен иметь уникальный `data object` конфигурацию.
- `Child#key as String` fix важен для Compose key stability — убедиться что `decompose-extensions-compose` из bundle версии 3.1.0.

### Dependency Notes

- `kotlinx-serialization-json` (не только `-core`) требуется потому что Decompose использует JSON-формат для encoding state. Alias `-json` уже есть в catalog (`libs.versions.toml:73`). Алиас `-core` не нужен отдельно — он входит в `-json` как транзитивная зависимость.
- Для `childStack(serializer = Config.serializer())` нужен `kotlin("plugin.serialization")` в модуле, где определены Config-классы.

---

## 2. Koin 3.5.6 + koin-androidx-compose 1.1.5

### Verified Facts

| Факт | Источник |
|------|----------|
| **Рекомендуемое место `startKoin`**: `Application.onCreate()`, не `MainActivity` | [Koin Android Start docs](https://insert-koin.io/docs/reference/koin-android/start/) |
| В `MainActivity`: риск двойного вызова при rotation, недоступность для Service/BroadcastReceiver до первого открытия Activity | [Koin Medium — Simplifying or Adding Complexity](https://medium.com/@actiwerks/koin-and-context-management-in-android-simplifying-or-adding-complexity-568b58bf2296) |
| `androidContext(this@Application)` — Application context, не Activity context (memory leak) | [Koin docs](https://insert-koin.io/docs/reference/koin-android/start/) |
| `KoinStartup` + AndroidX App Startup — инициализация через ContentProvider до `Application.onCreate()`; полезно только если уже используется AndroidX App Startup | [Koin Startup Extension — Medium](https://medium.com/@santimattius/koin-startup-extension-configuring-koin-using-app-startup-in-android-e0facd2fd943) |
| `factory { params -> MyComponent(componentContext = params.get(), repo = get()) }` — паттерн для Decompose компонентов с `ComponentContext` как inject parameter | [Koin Injection Parameters docs](https://insert-koin.io/docs/reference/koin-core/injection-parameters/) |
| `singleOf(::Impl) bind Interface::class` — idiomatic Koin 3.5.6 (autowire DSL 3.2+); эквивалент `single<Interface> { Impl(get()) }` | [Koin Autowire DSL docs](https://insert-koin.io/docs/reference/koin-core/dsl-update/) |
| `koinViewModel()` — предпочтительный API в Compose; `getViewModel()` устарел (deprecated) | [Koin for Compose docs](https://insert-koin.io/docs/reference/koin-compose/compose/) |
| `koinViewModel<VM> { parametersOf(param) }` — runtime параметры; НО: смена параметра не создаёт новый VM instance (known issue) | [Koin GitHub Issue #1477](https://github.com/InsertKoinIO/koin/issues/1477) |
| `koin-androidx-compose` = удобная обёртка, включает `koin-compose` + `koin-compose-viewmodel` | [Koin Compose docs](https://insert-koin.io/docs/reference/koin-compose/compose/) |

### Known Issues

| Issue | Versions | Status |
|-------|----------|--------|
| `ClosedScopeException: Scope '_root_' is closed` при создании ViewModel | 3.5.3, 3.5.4 | **Исправлено в 3.5.6** ✅ |
| `getViewModel()` с новыми параметрами возвращает старый instance (не пересоздаёт) | все версии | Open — рекомендация: использовать `remember { }` с параметром как ключом |

### [DISCREPANCY] ADR-0009 vs Official Koin Docs

- `ADR-0009:71` (`docs/architecture/0009-dependency-injection.md:71`): `startKoin { androidContext(this@MainActivity); modules(...) }` в `MainActivity`
- **Official Koin docs**: настоятельно рекомендуют `Application.onCreate()` — риск двойного вызова в MainActivity при rotation, недоступность для Services

**Рекомендация**: создать `AppApplication : Application`, перенести `startKoin` туда. ADR-0009 устарел в этой части. Источник: [Koin Android Start](https://insert-koin.io/docs/reference/koin-android/start/)

---

## 3. Compose Material3 (BOM 2024.09.02)

### Verified Facts

| Факт | Источник |
|------|----------|
| **`ModalNavigationDrawer` оборачивает `Scaffold`** — не наоборот. Scaffold в M3 не имеет `drawerContent` slot (в отличие от M2) | [Android Developers — Drawer](https://developer.android.com/develop/ui/compose/components/drawer) |
| `ModalNavigationDrawer(drawerContent, drawerState, gesturesEnabled, scrimColor, content)` — полная сигнатура | [Composables.com M3 docs](https://composables.com/material3/modalnavigationdrawer) |
| `drawerState.open()` / `drawerState.close()` — **suspend functions**; обязательно `coroutineScope.launch { drawerState.open() }` | [Android Developers — Drawer](https://developer.android.com/develop/ui/compose/components/drawer) |
| `rememberDrawerState(DrawerValue.Closed)` — для создания state | [Composables.com M3](https://composables.com/material3/modalnavigationdrawer) |
| `ModalDrawerSheet` — обёртка для `drawerContent`, применяет M3 styling | [Android Developers — Drawer](https://developer.android.com/develop/ui/compose/components/drawer) |
| **Badge API**: `BadgedBox(badge: @Composable BoxScope.() -> Unit, content: @Composable BoxScope.() -> Unit)` | [Android Developers — Badges](https://developer.android.com/develop/ui/compose/components/badges) |
| `NavigationBarItem` использует `BadgedBox` для badge slot | [Composables.com BadgedBox](https://composables.com/material3/badgedbox) |
| **BOM 1.4.0-alpha01 breaking**: `material3` больше не добавляет `material-icons-core` как зависимость — надо добавить явно | [Compose Material3 Release Notes](https://developer.android.com/jetpack/androidx/releases/compose-material3) |
| `Crossfade(targetState, animationSpec = tween(durationMillis = 300))` — стандартный паттерн для section/tab switch; default `tween()` уже 300ms | [Compose Animation — Android Developers](https://developer.android.com/develop/ui/compose/animation/composables-modifiers) |
| `TopAppBar(title, navigationIcon, actions, scrollBehavior)` — confirmed API | [Android Developers](https://developer.android.com) |

### Badge Usage Pattern

```kotlin
NavigationBarItem(
    icon = {
        BadgedBox(
            badge = {
                if (count > 0) Badge { Text("$count") }
            }
        ) {
            Icon(icon, contentDescription)
        }
    },
    // ...
)
```

### ModalNavigationDrawer + Scaffold Pattern

```kotlin
ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
        ModalDrawerSheet {
            // drawer items
        }
    }
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        coroutineScope.launch { drawerState.open() }
                    }) { Icon(Icons.Default.Menu, null) }
                }
            )
        },
        bottomBar = { NavigationBar { /* tabs */ } }
    ) { padding ->
        // content
    }
}
```

### Known Issues / Warnings

- **[WARNING]** BOM 2024.09.02 соответствует Material3 1.3.x. С 1.4.0-alpha01 `material-icons-core` убран из transitive deps — если обновляться выше BOM 2024.09.02, добавить `implementation(libs.compose.material.icons.extended)` явно (в catalog уже есть alias).
- `NavigationBarItem` active label color изменился в 1.4.0-alpha05 (from `onSurface` → `secondary`) — не влияет на BOM 2024.09.02.

---

## 4. kotlinx-serialization 1.6.3 в KMP domain

### Verified Facts

| Факт | Источник |
|------|----------|
| `@Serializable` на `sealed interface` поддерживается с kotlinx-serialization 1.3+ | [GitHub Issue #1576](https://github.com/Kotlin/kotlinx.serialization/issues/1576) |
| Все subclass'ы sealed hierarchy обязаны также быть `@Serializable` | [Kotlin Serialization docs](https://kotlinlang.org/docs/serialization.html) |
| Компилятор генерирует `serializer()` extension на companion; требует `kotlin("plugin.serialization")` в модуле где аннотация | [kotlinx.serialization README](https://github.com/Kotlin/kotlinx.serialization) |
| `@Serializable` добавляет type discriminator `"type"` в JSON; имя можно изменить через `@SerialName` | [Polymorphism docs](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md) |
| `kotlinx-serialization-core` — format-agnostic: только `KSerializer` interface, аннотации, базовые API. **Без JSON encoder.** | [Baeldung — Intro to kotlinx-serialization](https://www.baeldung.com/kotlin/kotlinx-serialization-project) |
| `kotlinx-serialization-json` включает `-core` как транзитивную зависимость; для конечного приложения добавляй `-json` | [Maven Repo + kotlinx.serialization README](https://github.com/Kotlin/kotlinx.serialization) |
| Для `childStack(serializer = Config.serializer())` — `.serializer()` из `-core`; но Decompose внутренне использует JSON → нужен `-json` в classpath | [Decompose Issue #470](https://github.com/arkivanov/Decompose/issues/470) |

### @Serializable в Domain Layer — "Серая Зона" разрешена

Из `2-grounding.md:178` — domain-model rule запрещает `@SerialName`, `@ColumnInfo`, `@Json`, `@Entity` (transport-specific). Но `@Serializable` из `kotlinx.serialization` — **не transport-specific**:

- Он не привязан к формату (JSON/Protobuf/CBOR/custom)
- Он нужен для навигационного state-saving (infrastructure, не transport)
- Spec NFR #2 прямо требует его в domain
- Decompose официально рекомендует `@Serializable` на Config classes в domain

**Вывод**: `@Serializable` разрешён в domain. Нарушения domain purity нет. Запрещены только transport-specific аннотации (`@SerialName` если добавляется только для HTTP, `@Json`, `@ColumnInfo`). Источник: [Kotlin Serialization docs](https://kotlinlang.org/docs/serialization.html)

### Dependency Matrix для Decompose state-saving

| Сценарий | Нужная зависимость |
|----------|-------------------|
| Только `@Serializable` annotation + `.serializer()` | `kotlinx-serialization-core` достаточно |
| Decompose 3.x state-saving (JSON encoding internally) | `kotlinx-serialization-json` (включает core транзитивно) |
| End-to-end: Decompose + domain + data в KMP app | **`kotlinx-serialization-json`** в module с Config + plugin в `build.gradle.kts` |

Alias `kotlinx-serialization-json` уже есть в catalog (`libs.versions.toml:73`). Отдельный alias `-core` не нужен.

---

## 5. Firebase KMP Integration

### Verified Facts

| Факт | Источник |
|------|----------|
| `com.google.firebase:firebase-bom:33.2.0` — Android-only SDK, нет multiplatform поддержки | [Firebase official] |
| **gitlive firebase-kotlin-sdk** — Kotlin-first KMP wrapper; API аналогичен Firebase Android SDK Extensions | [GitHub GitLiveApp/firebase-kotlin-sdk](https://github.com/GitLiveApp/firebase-kotlin-sdk) |
| gitlive current version: **`dev.gitlive:firebase-firestore:2.4.0`** (не 1.x — устарел) | [Maven Central](https://central.sonatype.com/artifact/dev.gitlive/firebase-firestore) |
| gitlive Flows — **cold flows**; listener автоматически отменяется при cancellation coroutine scope | [GitHub GitLiveApp](https://github.com/GitLiveApp/firebase-kotlin-sdk) |
| Паттерн `expect/actual` с gitlive: `expect object FirebaseInitializer` в commonMain, `actual` в androidMain | [KMP Firebase Guide — Medium](https://medium.com/advanced-kotlin-multiplatform-kmp/add-firebase-to-kotlin-multiplatform-compose-multiplatform-3663c6a8f19c) |
| Один из подходов: реализовать Firebase в `androidApp` и инжектить в commonMain — "менее гибок" но нет community wrapper dependency | [Kotlin Slack thread](https://slack-chats.kotlinlang.org/t/18845148) |

### 3 паттерна для KMP data module (grounding Problem 7)

| Вариант | Механизм | Плюсы | Минусы |
|---------|----------|-------|--------|
| **A) androidMain только** | `UserStatsRepositoryImpl` в `androidMain/`; `jvmMain` без impl / не компилируется | Простой; нет extra deps; нет wrapper | JVM target не работает; convention plugin надо изменить |
| **B) Common interface + platform adapter** | `UserStatsDataSource` interface в commonMain; Firebase impl в `androidMain/`; `platform/firebase` adapter | Чистая архитектура; соответствует ADR-0001:36-37 | Больше слоёв; сложнее wiring |
| **C) gitlive-firebase в commonMain** | `dev.gitlive:firebase-firestore:2.4.0` в `commonMain`; `expect/actual` только для init | KMP-нативен; поддерживает iOS | Community wrapper; 2.4.0 vs наш BOM 33.2.0 (разные версии underlying SDK) |

**Рекомендация для design-фазы**: Вариант A (androidMain only) если JVM target не нужен в production. Вариант C (gitlive) если iOS цель есть в roadmap. ADR-0001:36-37 уже предусматривает `platform/firebase` adapter — это точное описание Варианта B.

### Flow Cancellation Pattern (Firestore)

```kotlin
// В UserStatsRepositoryImpl (androidMain)
override fun observeStats(): Flow<UserStats> =
    firestore.collection("profiles").document(uid)
        .snapshots()  // gitlive cold Flow
        .map { snapshot -> snapshot.toObject<UserStatsDto>().toDomain() }
        .catch { emit(UserStats.guest()) }  // spec Error Recovery #4
// Flow listener автоматически removed при cancel scope — утечек нет
```

---

## Open Questions (не найдено / не подтверждено)

| Вопрос | Статус |
|--------|--------|
| Exact `@Serializable` support на `sealed interface` в kotlinx-serialization 1.6.3 (именно interface, не class) — есть ли ограничения vs sealed class? | Поддержка подтверждена с 1.3+, но [Issue #2572](https://github.com/Kotlin/kotlinx.serialization/issues/2572) — implicit propagation не реализована: каждый subclass должен быть явно аннотирован |
| Точная версия Material3 в BOM 2024.09.02 | `1.3.0-beta05` (по матрице BOM→library, не проверено прямым fetch) |
| `stackAnimation` из decompose-extensions-compose — точный API для Crossfade интеграции | Документация неполна; рекомендую использовать `Children(stack, animation = null)` + manual Crossfade внутри content lambda для explicit control |
| gitlive `2.4.0` совместимость с `firebase-bom:33.2.0` | [NOT FOUND] — gitlive 2.x wraps Firebase Android SDK; точные версии underlying SDK надо сверить в gitlive pom |

---

## Сводная таблица рекомендаций по 7 Open Questions из grounding

| # | Вопрос | Рекомендация (web research) | Источник |
|---|--------|----------------------------|----------|
| 1 | Navigator interface location | **Path A**: добавить в domain — 3-line interface, pure Kotlin, compile-time enforcement | spec NFR #3 + grounding Problem 4 |
| 2 | `@Serializable` placement | **Path A**: в domain + plugin в `domain/build.gradle.kts` — `@Serializable` разрешён, не transport-specific | [Kotlin Serialization docs](https://kotlinlang.org/docs/serialization.html) + Decompose official recommendation |
| 3 | Koin `startKoin` location | **Application class** — не MainActivity. ADR-0009:71 устарел. Rotation risk + Services need. | [Koin docs](https://insert-koin.io/docs/reference/koin-android/start/) |
| 4 | Compose в convention plugins | **Новый plugin** `schoolquiz.android.compose.library` — не ad-hoc; иначе дублирование в N build.gradle | best practice: convention-plugins per concern |
| 5 | Firebase в KMP data module | **Вариант A** (androidMain) если iOS не в scope; **Вариант C** (gitlive 2.4.0) если iOS roadmap | [GitLive GitHub](https://github.com/GitLiveApp/firebase-kotlin-sdk) |
| 6 | `-core` alias | **Не добавлять** — `-json` включает `-core` транзитивно; alias `-json` уже в catalog | [kotlinx.serialization README](https://github.com/Kotlin/kotlinx.serialization) |
| 7 | Detekt/Ktlint enforcement | Отдельная infrastructure task — не блокирует phase-01 функциональность | architecture decision |
