---
phase: phase-05
role: test-dev
---

# Phase-05: Test Tasks — AppShellScreen + ScrollToTopRegistry

## 1. ScrollToTopRegistryTest.kt (JVM)

**Файл**: `android/feature/app-shell/presentation/src/test/kotlin/.../presentation/ScrollToTopRegistryTest.kt`

```kotlin
class ScrollToTopRegistryTest {

    private val registry = ScrollToTopRegistry()

    private fun fakHook() = object : ScrollToTopHook {
        override suspend fun scrollToTop() = true
    }

    @Test
    fun `register sets hook for tab`() {
        val hook = fakHook()
        registry.register(Tab.LOCAL, hook)
        assertEquals(hook, registry.current(Tab.LOCAL))
    }

    @Test
    fun `unregister with same reference removes hook`() {
        val hook = fakHook()
        registry.register(Tab.LOCAL, hook)
        registry.unregister(Tab.LOCAL, hook)
        assertNull(registry.current(Tab.LOCAL))
    }

    @Test
    fun `identity_unregister does not remove different instance`() {
        val hook1 = fakHook()
        val hook2 = fakHook()
        registry.register(Tab.LOCAL, hook1)
        registry.register(Tab.LOCAL, hook2)
        // hook1 unregisters but hook2 is registered — identity check keeps hook2
        registry.unregister(Tab.LOCAL, hook1)
        assertEquals(hook2, registry.current(Tab.LOCAL))   // ADR-COMP-06
    }

    @Test
    fun `crossfade_overlap incoming hook survives outgoing unregister`() {
        val outgoing = fakHook()
        val incoming = fakHook()
        registry.register(Tab.LOCAL, outgoing)

        // Incoming registers first (Crossfade overlap)
        registry.register(Tab.LOCAL, incoming)

        // Then outgoing disposes (identity !== incoming)
        registry.unregister(Tab.LOCAL, outgoing)

        assertEquals(incoming, registry.current(Tab.LOCAL))
    }

    @Test
    fun `current returns null for unregistered tab`() {
        assertNull(registry.current(Tab.INTERNET))
    }
}
```

## 2. AppShellScreenTest.kt (instrumented)

**Файл**: `android/feature/app-shell/presentation/src/androidTest/kotlin/.../presentation/AppShellScreenTest.kt`

Phase-05 scope: compile-level instrumented tests only. Full hamburger/drawer FSM assertion requires fully wired `DefaultRootComponent` — деferred to phase-07 manual smoke (AC 29) and integration smoke.

```kotlin
@RunWith(AndroidJUnit4::class)
class AppShellScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    // Compile-level test: UnderConstructionScreen renders without crash
    @Test
    fun under_construction_screen_renders_title_and_subtitle() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                UnderConstructionScreen(title = "Test Screen")
            }
        }
        // AC 13: both title and subtitle rendered
        composeTestRule.onNodeWithText("Test Screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Скоро здесь будет...").assertIsDisplayed()
    }

    @Test
    fun scroll_registry_provided_in_composition() {
        var registry: ScrollToTopRegistry? = null
        composeTestRule.setContent {
            SchoolQuizTheme {
                val r = remember { ScrollToTopRegistry() }
                CompositionLocalProvider(LocalScrollToTopRegistry provides r) {
                    registry = LocalScrollToTopRegistry.current
                    Box {}
                }
            }
        }
        assertNotNull(registry)
    }

    // snapshotFlow compile-level: verifies LaunchedEffect(Unit) { snapshotFlow { ... } } compiles
    @Test
    fun snapshot_flow_drawer_sync_compiles() {
        var drawerSeen = false
        composeTestRule.setContent {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            LaunchedEffect(Unit) {
                snapshotFlow { drawerState.currentValue }.collect { drawerSeen = true }
            }
            Box {}
        }
        // Verify LaunchedEffect with snapshotFlow doesn't crash at composition
        composeTestRule.waitForIdle()
        assertTrue(drawerSeen)
    }
}
```

Note: journeys 7 (edge swipe), 8 (scrim close), 9 (swipe close) full assertion coverage — phase-07 manual smoke checklist (AC 29). Phase-05 drawer sync is compile-verified; behavior verified via DefaultRootComponentTest (phase-04) + phase-07 smoke.

## Validation

```bash
./gradlew :android:feature:app-shell:presentation:test --no-configuration-cache
# Instrumented (optional):
./gradlew assembleDebugAndroidTest --no-configuration-cache
```
