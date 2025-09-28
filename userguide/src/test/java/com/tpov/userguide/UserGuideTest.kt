package com.tpov.userguide

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import com.tpov.userguide.presentation.Options
import com.tpov.userguide.presentation.UserGuide
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class UserGuideBuildTest {

    companion object {
        @JvmStatic
        fun provideBuildTestCases(): Stream<Arguments> {
            val mockView: View = mockk()
            val mockContext: Context = mockk()
            val mockIcon: Drawable = mockk()

            return Stream.of(
                Arguments.of(
                    UserGuide(mockContext).guideBuilder()
                        .setViews(mockView)
                        .setText("Valid Text")
                        .setOptions(Options(isInfinityCount = true)),
                    true
                ),
                Arguments.of(
                    UserGuide(mockContext).guideBuilder()
                        .setViews(null)
                        .setText("Valid Text")
                        .setOptions(Options(isInfinityCount = true)),
                    false
                ),
                Arguments.of(
                    UserGuide(mockContext).guideBuilder()
                        .setViews(mockView)
                        .setOptions(Options(isInfinityCount = true)),
                    false
                )
            )
        }
    }

    @ParameterizedTest
    @MethodSource("provideBuildTestCases")
    fun `test build conditions`(builder: UserGuide.GuideBuilder, shouldBuild: Boolean) {
        val guideItem = builder.build()
        if (shouldBuild) {
            assertNotNull(guideItem, "GuideItem should be created")
        } else {
            assertNull(guideItem, "GuideItem should not be created")
        }
    }
}

