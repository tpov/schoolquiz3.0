---
phase: phase-03
role: test-dev
---

# Phase-03: Test Tasks — DS Wrappers

## Scope

Compile + instrumented smoke tests для 6 wrappers + DesignCatalogScreen.

## Tests — SchoolQuizComponentsTest.kt (instrumented)

**Файл**: `android/core/designsystem/src/androidTest/kotlin/.../designsystem/SchoolQuizComponentsTest.kt`

```kotlin
@RunWith(AndroidJUnit4::class)
class SchoolQuizComponentsTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun brand_card_renders_content() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                BrandCard { Text("card content", Modifier.testTag("card_content")) }
            }
        }
        composeTestRule.onNodeWithTag("card_content").assertIsDisplayed()
    }

    @Test
    fun brand_primary_button_triggers_click() {
        var clicked = false
        composeTestRule.setContent {
            SchoolQuizTheme {
                BrandPrimaryButton(text = "Test", onClick = { clicked = true })
            }
        }
        composeTestRule.onNodeWithText("Test").performClick()
        assertTrue(clicked)
    }

    @Test
    fun brand_progress_bar_renders_for_valid_progress() {
        composeTestRule.setContent {
            SchoolQuizTheme { BrandProgressBar(progress = 0.5f, Modifier.testTag("pb")) }
        }
        composeTestRule.onNodeWithTag("pb").assertIsDisplayed()
    }

    @Test
    fun brand_progress_bar_clamps_value_above_1() {
        // Should not crash for progress > 1.0
        composeTestRule.setContent {
            SchoolQuizTheme { BrandProgressBar(progress = 2.0f) }
        }
    }

    @Test
    fun design_catalog_screen_renders_all_section_titles() {
        composeTestRule.setContent {
            SchoolQuizTheme { DesignCatalogScreen() }
        }
        composeTestRule.onNodeWithText("Design Catalog").assertIsDisplayed()
        composeTestRule.onNodeWithText("BrandCard").assertIsDisplayed()
        composeTestRule.onNodeWithText("BrandPrimaryButton").assertIsDisplayed()
    }
}
```

## Validation

```bash
./gradlew :android:core:designsystem:compileDebugKotlin --no-configuration-cache
# Instrumented (optional, requires emulator):
./gradlew :android:core:designsystem:connectedDebugAndroidTest --no-configuration-cache
```
