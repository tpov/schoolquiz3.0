---
phase: phase-06
role: test-dev
---

# Phase-06: Test Tasks — Drawer Content

## 1. DrawerFooterMapperTest.kt (JVM)

**Файл**: `android/feature/app-shell/presentation/src/test/kotlin/.../DrawerFooterMapperTest.kt`

```kotlin
class DrawerFooterMapperTest {

    @Test
    fun `visibleFooterActions debug includes DesignCatalog`() {
        val actions = visibleFooterActions(isDebugBuild = true)
        assertTrue(DrawerFooterAction.DesignCatalog in actions)
    }

    @Test
    fun `visibleFooterActions release excludes DesignCatalog`() {
        val actions = visibleFooterActions(isDebugBuild = false)
        assertFalse(DrawerFooterAction.DesignCatalog in actions)
    }

    @Test
    fun `visibleFooterActions always includes About`() {
        assertTrue(DrawerFooterAction.About in visibleFooterActions(true))
        assertTrue(DrawerFooterAction.About in visibleFooterActions(false))
    }
}
```

Note: `visibleFooterActions` живёт в domain `Visibility.kt`. Если функция не существует — test-dev создаёт issue для backend-dev (domain delta — но это не spec approved delta, нужна эскалация).

## 2. DrawerHeaderTest.kt (instrumented)

```kotlin
@RunWith(AndroidJUnit4::class)
class DrawerHeaderTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun drawer_header_renders_nickname() {
        val stats = UserStats.guest().copy(nickname = "Alice")
        composeTestRule.setContent {
            SchoolQuizTheme { DrawerHeader(userStats = stats) }
        }
        composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
    }

    @Test
    fun drawer_header_renders_premium_badge_when_premium() {
        val stats = UserStats.guest().copy(hasPremium = true)
        composeTestRule.setContent {
            SchoolQuizTheme { DrawerHeader(userStats = stats) }
        }
        composeTestRule.onNodeWithText("Premium").assertIsDisplayed()
    }

    @Test
    fun drawer_header_no_premium_badge_for_guest() {
        composeTestRule.setContent {
            SchoolQuizTheme { DrawerHeader(userStats = UserStats.guest()) }
        }
        composeTestRule.onNodeWithText("Premium").assertDoesNotExist()
    }

    @Test
    fun drawer_header_streak_label_shows_streak_days() {
        val stats = UserStats.guest().copy(streakDays = 7)
        composeTestRule.setContent {
            SchoolQuizTheme { DrawerHeader(userStats = stats) }
        }
        composeTestRule.onNodeWithText("Серия: 7/10 дней").assertIsDisplayed()
    }
}
```

## Validation

```bash
./gradlew :android:feature:app-shell:presentation:test --no-configuration-cache
./gradlew :android:feature:app-shell:presentation:connectedDebugAndroidTest --no-configuration-cache
```
