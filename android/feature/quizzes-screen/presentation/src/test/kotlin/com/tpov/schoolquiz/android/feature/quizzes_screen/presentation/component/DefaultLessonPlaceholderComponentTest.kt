package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals

/**
 * JVM unit tests for [DefaultLessonPlaceholderComponent].
 *
 * Spec: docs/features/quizzes-screen/04-testing.md §7
 * Phase: 04 (TDD)
 *
 * Coverage: LP-U-01, LP-U-02
 * No coroutines: LessonPlaceholderComponent is stateless (config-derived only).
 */
class DefaultLessonPlaceholderComponentTest {

    private lateinit var lifecycle: LifecycleRegistry

    @After
    fun tearDown() {
        if (::lifecycle.isInitialized) {
            lifecycle.stop()
            lifecycle.destroy()
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun buildComponent(
        lessonId: String = "l-1",
        lessonTitle: String = "Lesson Title",
        titles: List<String> = listOf("Math", "Quest", "Section", "Theme", "Lesson Title"),
    ): DefaultLessonPlaceholderComponent {
        lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val ctx = DefaultComponentContext(lifecycle = lifecycle)
        return DefaultLessonPlaceholderComponent(
            componentContext = ctx,
            config = QuizzesConfig.LessonPlaceholder(
                lessonId = lessonId,
                lessonTitle = lessonTitle,
                titles = titles,
            ),
        )
    }

    // ── LP-U-01 ──────────────────────────────────────────────────────────────

    /**
     * Spec: LP-U-01 — uiState.lessonTitle equals config.lessonTitle.
     * Component directly exposes config.lessonTitle without transformation.
     */
    @Test
    fun `uiState lessonTitle equals config lessonTitle`() {
        val component = buildComponent(lessonTitle = "Lesson Title")
        assertEquals("Lesson Title", component.uiState.lessonTitle)
    }

    // ── LP-U-02 ──────────────────────────────────────────────────────────────

    /**
     * Spec: LP-U-02 — uiState.titles equals config.titles (frozen breadcrumb).
     * The titles list is passed through verbatim; no modification.
     */
    @Test
    fun `uiState titles equals config titles (frozen breadcrumb)`() {
        val configTitles = listOf("Math", "Quest", "Section", "Theme", "Lesson Title")
        val component = buildComponent(titles = configTitles)
        assertEquals(configTitles, component.uiState.titles)
    }

    // ── LP-U-edge-01 ─────────────────────────────────────────────────────────

    /**
     * Spec: LP-U-01 edge case — lessonTitle must NOT be derived from titles.last().
     * Even when titles has a different last element, lessonTitle reflects config.lessonTitle.
     */
    @Test
    fun `lessonTitle is not derived from titles last element`() {
        val component = buildComponent(
            lessonTitle = "Special",
            titles = listOf("A", "B"),
        )
        assertEquals(
            "Special",
            component.uiState.lessonTitle,
            "lessonTitle must reflect config.lessonTitle, not titles.last()",
        )
    }
}
