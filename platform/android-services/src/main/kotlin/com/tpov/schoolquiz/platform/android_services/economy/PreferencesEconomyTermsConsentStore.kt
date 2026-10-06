package com.tpov.schoolquiz.platform.android_services.economy

import android.content.Context
import com.tpov.schoolquiz.shared.feature.economy.data.EconomyTermsConsentStore
import com.tpov.schoolquiz.shared.feature.economy.data.PayableTermsCodec
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PayableTerms

/**
 * Последнее согласие на платные условия — в тех же настройках приложения, где и сама таблица.
 *
 * Не auth-scoped, как и таблица: цены от того, кто вошёл, не зависят. От таблицы запись согласия
 * отличается правом перезаписи: таблицу переписывает синхронизация, согласие — только игрок,
 * подтвердивший новые условия.
 *
 * Само отображение «условия ↔ строка» — это [PayableTermsCodec] в data: платформенному хранилищу
 * остаётся положить строку в настройки и достать её. Испорченная запись разбирается кодеком в
 * «согласия не было» — спросить игрока заново дешевле, чем провести платную операцию по половине
 * согласия.
 */
class PreferencesEconomyTermsConsentStore(context: Context) : EconomyTermsConsentStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(): PayableTerms? = prefs.getString(KEY_CONSENT, null)?.let(PayableTermsCodec::fromJson)

    override fun write(terms: PayableTerms) {
        prefs.edit().putString(KEY_CONSENT, PayableTermsCodec.toJson(terms)).apply()
    }

    private companion object {
        const val PREFS_NAME = "schoolquiz_economy"
        const val KEY_CONSENT = "economy_terms_consent"
    }
}
