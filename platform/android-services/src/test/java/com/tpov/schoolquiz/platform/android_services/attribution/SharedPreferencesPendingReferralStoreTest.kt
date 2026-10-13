package com.tpov.schoolquiz.platform.android_services.attribution

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedPreferencesPendingReferralStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `code survives a fresh store over the same preferences`() = runTest {
        SharedPreferencesPendingReferralStore(context).write("inviter-uid")

        // Новый экземпляр над теми же preferences — модель смерти процесса между кликом по
        // ссылке и первым входом.
        assertEquals("inviter-uid", SharedPreferencesPendingReferralStore(context).read())
    }

    @Test
    fun `clear removes the code`() = runTest {
        val store = SharedPreferencesPendingReferralStore(context)
        store.write("inviter-uid")

        store.clear()

        assertNull(SharedPreferencesPendingReferralStore(context).read())
    }

    @Test
    fun `empty store reads null`() = runTest {
        assertNull(SharedPreferencesPendingReferralStore(context).read())
    }
}
