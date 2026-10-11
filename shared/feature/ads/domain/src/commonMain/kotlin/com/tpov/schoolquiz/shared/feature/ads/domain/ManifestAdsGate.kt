package com.tpov.schoolquiz.shared.feature.ads.domain

/**
 * Врата, читающие метаданные манифеста: нет идентификатора AdMob — рекламы нет всюду.
 *
 * Идентификатор — метка сборки, не экономическое число: ключ ожидается в meta-data приложения
 * ровно под этим именем. Чистая функция принимает метаданные картой — Android-обёртка
 * (Context → Map) приезжает вместе с живым SDK и не несёт решений. Пустая и пробельная строка —
 * то же «нет», что и отсутствие записи: полу-выключенной рекламы не бывает.
 */
const val ADMOB_APP_ID_METADATA: String = "com.google.android.gms.ads.APPLICATION_ID"

class ManifestAdsGate(
    private val manifestMetadata: Map<String, String>,
) : AdsGate {
    override fun availability(placement: AdPlacement): AdsAvailability = availabilityFor(manifestMetadata, placement)
}

/** Ответ врат по метаданным манифеста: идентификатор на месте — [AdsAvailability.Enabled]. */
fun availabilityFor(
    manifestMetadata: Map<String, String>,
    placement: AdPlacement,
): AdsAvailability {
    // Выключатель общий: место спрашивается, но ответ от места не зависит — выключено значит
    // выключено всюду, а не «в уроке выключено, в коробке как-нибудь».
    val appId = manifestMetadata[ADMOB_APP_ID_METADATA]?.trim().orEmpty()
    return if (appId.isEmpty()) AdsAvailability.Disabled else AdsAvailability.Enabled
}
