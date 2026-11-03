---
phase: phase-07
role: test-dev
---

# Phase-07: Test Tasks — Full Stack Wiring Validation

## 1. KoinFullStackWiringTest.kt

Расширить `KoinModuleWiringTest.kt` из phase-01 новыми тестами:

**Файл**: `apps/android-next/src/test/java/.../KoinModuleWiringTest.kt` (extend existing)

```kotlin
@Test
fun `full stack wiring DefaultRootComponent resolvable with parametersOf`() {
    val lifecycle = LifecycleRegistry(); lifecycle.resume()
    val testCtx = DefaultComponentContext(lifecycle)

    val testDataSourceModule = module {
        single<UserStatsDataSource> {
            object : UserStatsDataSource {
                override fun observeRaw() = kotlinx.coroutines.flow.emptyFlow()
                override suspend fun fetchRaw() = RawUserStats()
            }
        }
    }

    startKoin {
        modules(testDataSourceModule, appShellDataModule, appShellPresentationModule)
    }

    val component = get<DefaultRootComponent> { parametersOf(testCtx) }
    assertNotNull(component)
}

@Test
fun `system back event emitted at LOCAL root`() = runTest {
    val fakeRepo = FakeUserStatsRepository()
    val lifecycle = LifecycleRegistry(); lifecycle.resume()
    val testCtx = DefaultComponentContext(lifecycle)

    val component = DefaultRootComponent(
        componentContext = testCtx,
        initUseCase = InitializeAppShellUseCase(fakeRepo),
        navigateUseCase = NavigateUseCase(),
        observeUseCase = ObserveAppShellStateUseCase(fakeRepo),
        retapUseCase = OnTabRetapUseCase(),
    )

    val events = mutableListOf<RootEvent>()
    val job = launch(UnconfinedTestDispatcher(testScheduler)) {
        component.events.toList(events)
    }

    // FSM step 1: drawer closed already; step 3: already LOCAL; step 4: emit SystemBack
    component.onDestination(Destination.Back)

    job.cancel()
    assertTrue(events.any { it == RootEvent.SystemBack }, "Expected SystemBack in $events")
}
```

## 2. Manual Smoke Test Checklist

После `./gradlew :apps:android-next:assembleDebug` и установки APK:

```bash
# L2 fix: правильный путь к APK и имя activity
adb install apps/android-next/build/outputs/apk/debug/android-next-debug.apk
adb shell am start -n com.tpov.schoolquiz.next/.MainActivity
```

- [ ] AC 28: APK запускается, `AppShellScreen` видим (не crash, не blank)
- [ ] AC 29: 4 вкладки в NavigationBar
- [ ] AC 29: hamburger виден на LOCAL/INTERNET/EVENTS, скрыт на SHOP
- [ ] AC 29: tap hamburger → drawer открывается (Journey 2)
- [ ] AC 29 Journey 5: edge swipe от левого края на LOCAL tab → drawer открывается
- [ ] AC 29 Journey 7: tap на scrim (область вне drawer) → drawer закрывается
- [ ] AC 29 Journey 8: swipe drawer влево → drawer закрывается
- [ ] AC 29: Back button в drawer → drawer закрывается
- [ ] AC 29: Back button на LOCAL root → exit (moveTaskToBack)
- [ ] AC 29: Tap на INTERNET tab → переключение
- [ ] AC 29: Drawer header показывает nickname / streak / stats (guest values OK)
- [ ] AC 29: Design Catalog в debug drawer footer → tap → DesignCatalogScreen показан
- [ ] H3/AC 26: «О приложении» в footer → AlertDialog с версией (не навигация в Settings)
- [ ] AC 13: UnderConstructionScreen показывает title + "Скоро здесь будет..."
- [ ] AC 30: `./gradlew detekt ktlintCheck` — no new violations

## Validation

```bash
./gradlew :apps:android-next:assembleDebug --no-configuration-cache
./gradlew test --no-configuration-cache
./gradlew detekt ktlintCheck --no-configuration-cache
```
