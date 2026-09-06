package com.tpov.schoolquiz.platform.android_services.push

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationPermissionPromptTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `asks once on Android 13 and never again after any answer`() {
        val prompt = NotificationPermissionPrompt(context)

        assertTrue(prompt.shouldAsk(sdkInt = 33))
        prompt.markAsked()

        assertFalse(prompt.shouldAsk(sdkInt = 33))
        val fresh = NotificationPermissionPrompt(context)
        assertFalse("флаг живёт в настройках, а не в объекте", fresh.shouldAsk(sdkInt = 34))
    }

    @Test
    fun `nothing to ask below Android 13`() {
        assertFalse(NotificationPermissionPrompt(context).shouldAsk(sdkInt = 32))
    }
}
