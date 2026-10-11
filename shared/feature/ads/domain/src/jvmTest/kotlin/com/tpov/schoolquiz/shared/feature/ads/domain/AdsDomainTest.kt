package com.tpov.schoolquiz.shared.feature.ads.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Матрица врат истории 3.4: нет идентификатора — выключено всюду, есть — включено. */
class AdsDomainTest {

    @Test
    fun `a build without the AdMob identifier answers disabled for every placement`() {
        val gate = ManifestAdsGate(emptyMap())
        for (placement in AdPlacement.entries) {
            assertEquals(AdsAvailability.Disabled, gate.availability(placement), placement.name)
        }
    }

    @Test
    fun `an empty or blank identifier is the same no as its absence`() {
        for (metadata in listOf(
            mapOf(ADMOB_APP_ID_METADATA to ""),
            mapOf(ADMOB_APP_ID_METADATA to "   "),
            mapOf("some.other.key" to "ca-app-pub-3940256099942544~3347511713"),
        )) {
            val gate = ManifestAdsGate(metadata)
            for (placement in AdPlacement.entries) {
                assertEquals(AdsAvailability.Disabled, gate.availability(placement), "$metadata / $placement")
            }
        }
    }

    @Test
    fun `a present identifier answers enabled for every placement`() {
        val gate = ManifestAdsGate(mapOf(ADMOB_APP_ID_METADATA to "ca-app-pub-3940256099942544~3347511713"))
        for (placement in AdPlacement.entries) {
            assertEquals(AdsAvailability.Enabled, gate.availability(placement), placement.name)
        }
    }

    @Test
    fun `the placement list is closed and names exactly the two owner-chosen homes`() {
        assertEquals(listOf(AdPlacement.BOX_UNLOCK, AdPlacement.OFFERS_SCREEN), AdPlacement.entries.toList())
        assertTrue(AdPlacement.entries.none { it.name.contains("LESSON") }, "урока в перечне нет")
    }

    @Test
    fun `the pure function and the gate answer alike`() {
        val metadata = mapOf(ADMOB_APP_ID_METADATA to "id")
        for (placement in AdPlacement.entries) {
            assertEquals(ManifestAdsGate(metadata).availability(placement), availabilityFor(metadata, placement))
        }
    }
}
