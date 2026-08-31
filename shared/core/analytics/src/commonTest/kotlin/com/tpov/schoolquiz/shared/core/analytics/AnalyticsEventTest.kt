package com.tpov.schoolquiz.shared.core.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Event names and parameter keys are the wire contract, so they are asserted as fully resolved
 * values rather than through the constants that produce them. A renamed event silently orphans
 * every dashboard built on it, and only a test that spells the string out catches that.
 */
class AnalyticsEventTest {

    private val allEvents: List<AnalyticsEvent> = listOf(
        AnalyticsEvent.InstallAttributed(referrer = "utm_source=telegram", installVersion = "0.1.0"),
        AnalyticsEvent.LessonStarted(lessonId = "L-1", difficulty = "EASY"),
        AnalyticsEvent.LessonFinished(lessonId = "L-1", difficulty = "EASY", percent = 80, stars = 3),
        AnalyticsEvent.LessonAbandoned(lessonId = "L-1", questionsAnswered = 4),
        AnalyticsEvent.ShopOpened,
        AnalyticsEvent.PurchaseStarted(productId = "gold_pack_small"),
        AnalyticsEvent.PurchaseCompleted("gold_pack_small", priceMicros = 49_000_000, currency = "UAH"),
        AnalyticsEvent.PurchaseFailed(productId = "gold_pack_small", reason = "user_canceled"),
        AnalyticsEvent.RewardedAdCompleted(placement = "charge_refill"),
        AnalyticsEvent.ChargesExhausted(chargeKind = "STANDARD"),
    )

    @Test
    fun `given every event when read then the wire name is exactly the published string`() {
        val names = allEvents.map { it.name }

        assertEquals(
            listOf(
                "install_attributed",
                "lesson_started",
                "lesson_finished",
                "lesson_abandoned",
                "shop_opened",
                "purchase_started",
                "purchase_completed",
                "purchase_failed",
                "rewarded_ad_completed",
                "charges_exhausted",
            ),
            names,
        )
    }

    @Test
    fun `given every event name when validated then it fits the Firebase name grammar`() {
        allEvents.forEach { event ->
            val name = event.name
            assertTrue(name.length <= 40, "$name is longer than 40 characters")
            assertTrue(name.first().isLetter(), "$name does not start with a letter")
            assertTrue(
                name.all { it.isLowerCase() || it.isDigit() || it == '_' },
                "$name contains a character outside [a-z0-9_]",
            )
        }
    }

    @Test
    fun `given a completed purchase when read then price and currency come through unchanged`() {
        val event = AnalyticsEvent.PurchaseCompleted(
            productId = "gold_pack_small",
            priceMicros = 49_000_000,
            currency = "UAH",
        )

        assertEquals(AnalyticsValue.Text("gold_pack_small"), event.params["product_id"])
        assertEquals(AnalyticsValue.Count(49_000_000), event.params["price_micros"])
        assertEquals(AnalyticsValue.Text("UAH"), event.params["currency"])
    }

    @Test
    fun `given a finished lesson when read then percent and stars are counts not text`() {
        val event = AnalyticsEvent.LessonFinished(
            lessonId = "L-9",
            difficulty = "HARD",
            percent = 62,
            stars = 2,
        )

        assertEquals(AnalyticsValue.Count(62), event.params["percent"])
        assertEquals(AnalyticsValue.Count(2), event.params["stars"])
    }

    @Test
    fun `given an event with no payload when read then params is empty rather than null`() {
        assertEquals(emptyMap(), AnalyticsEvent.ShopOpened.params)
    }

    @Test
    fun `given the user properties when read then wire names are the published strings`() {
        assertEquals("ui_language", UserProperty.UI_LANGUAGE.wireName)
        assertEquals("has_paid", UserProperty.HAS_PAID.wireName)
        assertEquals("acquisition_source", UserProperty.ACQUISITION_SOURCE.wireName)
    }

    @Test
    fun `given the recording tracker when events are tracked then they are kept in order`() {
        val tracker = RecordingAnalyticsTracker()

        tracker.track(AnalyticsEvent.ShopOpened)
        tracker.track(AnalyticsEvent.PurchaseStarted("gold_pack_small"))
        tracker.setUserProperty(UserProperty.HAS_PAID, "true")
        tracker.setUserId("uid-1")

        assertEquals(
            listOf("shop_opened", "purchase_started"),
            tracker.events.map { it.name },
        )
        assertEquals("true", tracker.userProperties[UserProperty.HAS_PAID])
        assertEquals("uid-1", tracker.userId)
    }

    @Test
    fun `given the no-op tracker when used then nothing throws`() {
        NoOpAnalyticsTracker.track(AnalyticsEvent.ShopOpened)
        NoOpAnalyticsTracker.setUserProperty(UserProperty.UI_LANGUAGE, "uk")
        NoOpAnalyticsTracker.setUserId(null)
    }
}
