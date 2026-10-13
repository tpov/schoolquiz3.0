package com.tpov.schoolquiz.platform.android_services.attribution

import android.content.Context
import com.tpov.schoolquiz.shared.feature.internet.profile.data.PendingReferralStore

/**
 * Код приглашающего из deep-link лежит в plain preferences рядом с остальной атрибуцией:
 * ссылку могут открыть задолго до первого входа, и процесс между этими событиями умирает.
 */
class SharedPreferencesPendingReferralStore(
    context: Context,
) : PendingReferralStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun read(): String? = prefs.getString(KEY_CODE, null)

    override suspend fun write(code: String) {
        prefs.edit().putString(KEY_CODE, code).apply()
    }

    override suspend fun clear() {
        prefs.edit().remove(KEY_CODE).apply()
    }

    private companion object {
        const val PREFS = "attribution_state"
        const val KEY_CODE = "pending_referrer_uid"
    }
}
