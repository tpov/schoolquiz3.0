package com.tpov.schoolquiz.shared.feature.ads.domain

/**
 * Врата рекламы: единственный вопрос «показывать ли рекламу в этом месте».
 *
 * Синхронный по построению — источник ответа (метаданные манифеста) не меняется за жизнь
 * процесса. Потребители — поверхности из [AdPlacement]; урок в перечне отсутствует и врата
 * не спрашивает.
 */
interface AdsGate {
    fun availability(placement: AdPlacement): AdsAvailability
}
