---
paths:
  - "app/src/main/java/**/presentation/**/*.kt"
  - "app/src/main/java/**/ui/**/*.kt"
---

# Navigation — Hybrid Compose + Fragment

## Project setup

- Single Activity (`MainActivity` extends `AppCompatActivity`) + Compose NavGraph.
- Navigation host path and route-definition file must come from `PROJECT-CONTEXT.md`.
- Main Compose entry point must come from `PROJECT-CONTEXT.md`.
- Bottom-tab container details must come from `PROJECT-CONTEXT.md`.
- **Hybrid boundary**: Fragment/BottomSheet interop locations must come from `PROJECT-CONTEXT.md`. Full-screen screens stay in Compose.
- Manual DI — no Hilt. ViewModels создаются через `ViewModelFactory` + `lifecycleViewModel()`.
- No deep links. No drawer navigation. BottomNavigationBar вместо drawer.

## ViewModel creation patterns

```kotlin
// Pattern 1: Factory + lifecycleViewModel (основной)
val chatFactory = ChatViewModelFactory(
    initializeChatUseCase = application.initializeChatUseCase,
    sendMessageUseCase = application.sendMessageUseCase, ...
)
val chatViewModel: ChatViewModel = lifecycleViewModel(factory = chatFactory)

// Pattern 2: remember { Factory } + lifecycleViewModel (lazy per tab)
val contactsFactory = remember { ContactsViewModelFactory(usersRepository, webSocketService) }
val contactsViewModel: ContactsViewModel = lifecycleViewModel(factory = contactsFactory)

// Pattern 3: lifecycleViewModel() без factory (default creation)
val viewModel: SplashViewModel = lifecycleViewModel()
```

## Route patterns

```kotlin
// NavRoutes.kt — centralized routes
object NavRoutes {
    const val splash = "splash"
    const val auth = "auth"
    const val chats = "chats"
    const val chat = "chat/{chatId}?userName={userName}&openConversationInfo={openConversationInfo}"

    // Builder functions with URI encoding
    fun chat(chatId: String, userName: String, openConversationInfo: Boolean = false): String =
        "chat/$chatId?userName=${Uri.encode(userName)}&openConversationInfo=$openConversationInfo"

    // Tab normalization
    fun normalizeMainTab(tab: String?): String = tab?.takeIf { it in swipeTabSet } ?: chats
    fun mainTabToPage(tab: String?): Int = swipeTabOrder.indexOf(normalizeMainTab(tab))
}
```

## Navigation coordinators

- `ConversationNavigationCoordinator` — `openOrCreateConversationAndNavigate()` для создания conversation + навигации
- `CallNavigationCoordinator` — `handleCallClick()`, `handleContactCallInitiation()`, `startOrJoinGroupCall()` для call flow

## Back stack management

```kotlin
// Clear backstack при переходе с Splash
navController.navigate(NavRoutes.mainTabs()) { popUpTo(NavRoutes.splash) { inclusive = true }; launchSingleTop = true }

// Redirect routes для legacy compatibility
composable(NavRoutes.chats) { /* redirect → mainTabs?tab=chats */ }
```

## Rules

- Define all routes in `NavRoutes.kt` as string constants with builder functions.
- Navigate via `NavController`, not FragmentManager.
- ViewModel creation: `ViewModelFactory` + `lifecycleViewModel()` from `androidx.lifecycle.viewmodel.compose`.
- Lazy ViewModel creation per tab: create only when page is visible in HorizontalPager.
- Fragment interop: only for BottomSheet/Dialog components. No new Fragment screens.

## Avoid

- No `nav_graph.xml` — project uses Compose Navigation (если project использует Compose; см. PROJECT-CONTEXT.md).
- No Safe Args — use string routes with arguments via `backStackEntry.arguments` (если Compose Nav).
- No new Fragment-based screens (если project на Compose; для Fragment-based проектов это правило не применимо).
- No hardcoded route strings — centralize в `NavRoutes.kt` или аналогичном.
- No deep links (если не поддерживаются проектом).
- No Drawer navigation (если project использует BottomNavigationBar).

**DI integration**: следуй подходу из `.claude/PROJECT-CONTEXT.md` ("DI approach" секция). Примеры:
- Hilt: `@AndroidEntryPoint` на Activity + `@HiltViewModel` на ViewModel + `by viewModels()`
- Dagger 2 (manual): `AppComponent.inject(this)` + `ViewModelFactory` + `ViewModelProvider(this, factory)`
- Koin: `by viewModel()` extension из `org.koin.androidx.viewmodel.ext.android`

## Project-specific notes

Navigation стек проекта (Compose Nav vs Fragments vs Activities-only) — в `PROJECT-CONTEXT.md`. Это файл описывает специфичные для проекта правила. Правила выше — generic для Android. При конфликте: PROJECT-CONTEXT.md имеет приоритет.
