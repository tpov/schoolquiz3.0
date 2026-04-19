# ADR-0008: Навигация

## Status
Accepted — 2026-04-16

## Context

В legacy приложение было построено на Activity + Fragment + Jetpack Navigation с XML-графом. Все XML-based.

В новой версии (см. ADR-0002 KMP, ADR-0010 Designsystem) мы переходим на **Jetpack Compose**. Plus, архитектура ориентирована на **iOS в будущем** — значит навигация должна уметь работать как на Android, так и на iOS через Compose Multiplatform.

Требования:

1. Single-Activity — всё приложение внутри одной Activity, все экраны — Composable.
2. 4 bottom-вкладки (см. ADR про app-shell ниже):
   - Локальная (с drawer)
   - Интернет (с drawer)
   - События (с drawer, для minigame-фичи)
   - Магазин (без drawer, внутри горизонтальные tabs)
3. Каждая вкладка — независимый navigation stack, своя история, back работает внутри вкладки.
4. Drawer переключает «раздел» внутри вкладки (например, в «Интернет» drawer переключает между Арена / Курсы / Профиль / Социалка / Лидерборды).
5. Навигация должна быть KMP-совместимой, чтобы iOS взял тот же код.
6. Deep links — для FCM push-уведомлений (ADR-0004) и ссылок извне.

## Decision

### Библиотека: Decompose

Используем **Decompose** (https://github.com/arkivanov/Decompose) — Kotlin Multiplatform навигация, поверх которой рендерится Compose (`decompose-extensions-compose`).

Почему Decompose, а не Jetpack Navigation-Compose:

- **KMP-native.** Jetpack Navigation-Compose — только Android. Decompose работает на iOS, Desktop, Web с теми же компонентами.
- **Явная модель компонентов.** Каждый экран — `Component` с собственным жизненным циклом (`ComponentContext`), независимым от Composable-дерева. Упрощает unit-тесты и миграции между экранами.
- **Type-safe навигация.** Роуты — sealed class'ы с аргументами, без строковых маршрутов.
- **State persistence из коробки.** Decompose автоматически сохраняет state через `StateKeeper` при смене конфигурации.

Минус: порог входа выше, чем у Navigation-Compose. Компенсируется тем, что команда пишет KMP с первого дня.

### Архитектура навигации

```
RootComponent                                 // top-level, в MainActivity
├── AppShellComponent                         // основной scaffold с bottom-tabs
│   ├── LocalTabComponent                     // вкладка «Локальная»
│   │   ├── drawer: LocalDrawerItems          // Мои квесты / Мои курсы / Настройки
│   │   └── stack: StackNavigation<Config>    // стек экранов внутри вкладки
│   │        ├── MyQuestsScreenComponent
│   │        ├── MyCoursesScreenComponent
│   │        └── LocalSettingsScreenComponent
│   ├── InternetTabComponent                  // вкладка «Интернет»
│   │   ├── drawer: InternetDrawerItems       // Арена / Каталог / Квалификации / Профиль / ...
│   │   └── stack: StackNavigation<Config>
│   │        ├── ArenaScreenComponent
│   │        ├── CourseCatalogScreenComponent
│   │        ├── QualificationsScreenComponent
│   │        ├── ProfileScreenComponent
│   │        ├── SocialScreenComponent
│   │        └── LeaderboardScreenComponent
│   ├── EventsTabComponent                    // вкладка «События» (minigame)
│   │   ├── drawer: EventsDrawerItems
│   │   └── stack: StackNavigation<Config>
│   │        ├── ActiveEventsScreenComponent
│   │        └── MinigameScreenComponent
│   └── ShopTabComponent                      // вкладка «Магазин» (без drawer)
│       └── pager: ShopPager                  // горизонтальные tabs: Shop / Referrals / Donate
│            ├── ShopScreenComponent
│            ├── ReferralsScreenComponent
│            └── DonateScreenComponent
└── AuthFlowComponent                         // отдельный flow для логина, поверх AppShell
    ├── LoginScreenComponent
    └── SignUpScreenComponent
```

### Переключение вкладок vs переходы внутри вкладки

- Переключение **между вкладками** — пользователь тапает bottom-bar. Состояние каждой вкладки сохраняется (вернувшись во вкладку, юзер видит тот же экран, что и покидал).
- Переход **внутри вкладки** — push/pop на `StackNavigation`. Назад кнопка убирает верхний экран из стека.
- Drawer переключает **root конфигурацию вкладки** (например, в «Интернет» drawer = Арена → выкидываем стек, показываем ArenaScreenComponent).

### Роль модуля `android/core/navigation`

В этом модуле живут:
- Общие типы конфигураций (sealed `Config`) и их сериализация (через `kotlin-parcelize` для Android и `kotlinx-serialization` для KMP).
- Интерфейсы `NavigationCoordinator` / `Navigator`, через которые фича-модули получают возможность навигации, не зная конкретной реализации.
- Helper'ы для deep links.

Фича-presentation модули **не зависят** напрямую от Decompose — они принимают `Navigator`-интерфейс и вызывают `navigator.goTo(Config.X)`. Это изолирует фичи от смены библиотеки навигации.

### Где живёт RootComponent и AppShellComponent

- **`shared/feature/app-shell/domain`** — `RootComponent`, `AppShellComponent`, их интерфейсы конфигураций. KMP.
- **`android/feature/app-shell/presentation`** — Compose-UI: `Scaffold`, `NavigationBar`, `ModalNavigationDrawer`, рендерит текущий Composable из `AppShellComponent.activeTab`.
- **`apps/android-next`** — `MainActivity` создаёт `RootComponent` через `DefaultComponentContext(backHandler = …)` и передаёт в Compose.

### Deep links

`Config`-sealed-classes сериализуются в ссылки вида `schoolquiz://tab/internet/arena?questId=123`. Decompose предоставляет хуки `onDeepLink(intent)` → резолвит в цепочку `RootComponent.navigateTo(Config)` → переключает вкладку + очищает/строит стек.

FCM push-уведомления (ADR-0004) содержат `deeplink` в payload, клиент при клике на notification вызывает `onDeepLink`.

### Navigation из фичи в фичу

Фича НЕ знает про существование других фич. Она знает только про `Navigator`-интерфейс с методами уровня приложения:

```
interface Navigator {
    fun navigateTo(destination: Destination)
    fun goBack()
}

sealed interface Destination {
    data class Quest(val questId: QuestId) : Destination
    data class Profile(val userId: UserId) : Destination
    data class Certificate(val certificateId: CertificateId) : Destination
    // ...
}
```

Реализация `Navigator` — в `shared/feature/app-shell/domain` (знает про все вкладки и может переключать).

## Consequences

### Плюсы
- Код навигации переиспользуется на iOS/desktop без изменений.
- Single-Activity + Decompose = один общий back stack, deep links работают одинаково из push/ссылок/кнопок.
- Type-safe `Config` вместо строковых routes — компилятор ловит ошибки навигации.
- State persistence «бесплатно» — поворот экрана или смена конфигурации не теряет состояние экрана.

### Минусы
- **Порог входа выше.** Разработчику сначала нужно понять `ComponentContext`, `ChildStack`, `StateKeeper`.
- **Less integration with Jetpack** — например, `SavedStateHandle` заменяется на `StateKeeper` от Essenty.
- **Не Google-supported** — библиотека поддерживается одним maintainer'ом (Arkadii Ivanov). Риск частично митигируется активным сообществом и стабильной 3.x веткой.

### Правила
1. Фича-presentation ничего не знает про Decompose. Зависит только от `Navigator` и `Destination` из `android/core/navigation`.
2. Все конфигурации (`Config`) — sealed class'ы с `@Parcelize` для Android-совместимости.
3. Переход между вкладками — только через `AppShellComponent`, не через прямые вызовы.
4. Deep links живут в `android/core/navigation` в одном месте, каждая фича регистрирует свои паттерны.

## Notes

Библиотеки — `libs.decompose` + `libs.essenty` + `kotlin-parcelize` plugin (см. `libs.versions.toml`). Compose-интеграция — `libs.decompose.extensions.compose`.
