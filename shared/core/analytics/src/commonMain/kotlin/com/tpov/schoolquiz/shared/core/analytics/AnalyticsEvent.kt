package com.tpov.schoolquiz.shared.core.analytics

/**
 * The funnel, as events.
 *
 * Every event here answers a question the product currently cannot answer at all: how many people
 * opened a lesson, how many finished one, how many reached the shop, how many paid. The set is
 * deliberately small — an event nobody reads is a liability, not data.
 *
 * [name] is the wire name. It is snake_case, at most 40 characters, and starts with a letter,
 * because that is what the Firebase event-name grammar accepts. Changing a name breaks the
 * history behind it, so treat these as a published contract.
 */
sealed interface AnalyticsEvent {
    val name: String
    val params: Map<String, AnalyticsValue>
        get() = emptyMap()

    /** First launch after install. Carries the install referrer once it is known. */
    data class InstallAttributed(
        val referrer: String,
        val installVersion: String,
    ) : AnalyticsEvent {
        override val name: String = "install_attributed"
        override val params: Map<String, AnalyticsValue>
            get() = mapOf(
                "referrer" to AnalyticsValue.Text(referrer),
                "install_version" to AnalyticsValue.Text(installVersion),
            )
    }

    /** A lesson was opened for play. The top of the engagement funnel. */
    data class LessonStarted(
        val lessonId: String,
        val difficulty: String,
    ) : AnalyticsEvent {
        override val name: String = "lesson_started"
        override val params: Map<String, AnalyticsValue>
            get() = mapOf(
                "lesson_id" to AnalyticsValue.Text(lessonId),
                "difficulty" to AnalyticsValue.Text(difficulty),
            )
    }

    /** A lesson reached its result screen. [percent] is 0..100. */
    data class LessonFinished(
        val lessonId: String,
        val difficulty: String,
        val percent: Int,
        val stars: Int,
    ) : AnalyticsEvent {
        override val name: String = "lesson_finished"
        override val params: Map<String, AnalyticsValue>
            get() = mapOf(
                "lesson_id" to AnalyticsValue.Text(lessonId),
                "difficulty" to AnalyticsValue.Text(difficulty),
                "percent" to AnalyticsValue.Count(percent.toLong()),
                "stars" to AnalyticsValue.Count(stars.toLong()),
            )
    }

    /** A lesson was abandoned before the result screen. The other half of [LessonFinished]. */
    data class LessonAbandoned(
        val lessonId: String,
        val questionsAnswered: Int,
    ) : AnalyticsEvent {
        override val name: String = "lesson_abandoned"
        override val params: Map<String, AnalyticsValue>
            get() = mapOf(
                "lesson_id" to AnalyticsValue.Text(lessonId),
                "questions_answered" to AnalyticsValue.Count(questionsAnswered.toLong()),
            )
    }

    /** The shop was opened. Without this the purchase rate has no denominator. */
    data object ShopOpened : AnalyticsEvent {
        override val name: String = "shop_opened"
    }

    /** A real-money purchase flow was launched. Pairs with [PurchaseCompleted] or [PurchaseFailed]. */
    data class PurchaseStarted(
        val productId: String,
    ) : AnalyticsEvent {
        override val name: String = "purchase_started"
        override val params: Map<String, AnalyticsValue>
            get() = mapOf("product_id" to AnalyticsValue.Text(productId))
    }

    /**
     * A real-money purchase was acknowledged.
     *
     * [priceMicros] and [currency] come from the store, never from the client's own price list —
     * regional pricing means the two disagree, and the store is the one that took the money.
     */
    data class PurchaseCompleted(
        val productId: String,
        val priceMicros: Long,
        val currency: String,
    ) : AnalyticsEvent {
        override val name: String = "purchase_completed"
        override val params: Map<String, AnalyticsValue>
            get() = mapOf(
                "product_id" to AnalyticsValue.Text(productId),
                "price_micros" to AnalyticsValue.Count(priceMicros),
                "currency" to AnalyticsValue.Text(currency),
            )
    }

    /** A purchase flow ended without a purchase. [reason] is a stable code, not a user message. */
    data class PurchaseFailed(
        val productId: String,
        val reason: String,
    ) : AnalyticsEvent {
        override val name: String = "purchase_failed"
        override val params: Map<String, AnalyticsValue>
            get() = mapOf(
                "product_id" to AnalyticsValue.Text(productId),
                "reason" to AnalyticsValue.Text(reason),
            )
    }

    /** A rewarded ad finished and the reward was granted. The ad-revenue denominator. */
    data class RewardedAdCompleted(
        val placement: String,
    ) : AnalyticsEvent {
        override val name: String = "rewarded_ad_completed"
        override val params: Map<String, AnalyticsValue>
            get() = mapOf("placement" to AnalyticsValue.Text(placement))
    }

    /** A charge ran out and blocked play. The retention risk the shop economy creates. */
    data class ChargesExhausted(
        val chargeKind: String,
    ) : AnalyticsEvent {
        override val name: String = "charges_exhausted"
        override val params: Map<String, AnalyticsValue>
            get() = mapOf("charge_kind" to AnalyticsValue.Text(chargeKind))
    }
}

/**
 * The value types an analytics backend actually accepts. Deliberately narrow: a backend that
 * takes `Any` turns every typo into a silent data-quality bug discovered months later.
 */
sealed interface AnalyticsValue {
    data class Text(val value: String) : AnalyticsValue
    data class Count(val value: Long) : AnalyticsValue
    data class Amount(val value: Double) : AnalyticsValue
    data class Flag(val value: Boolean) : AnalyticsValue
}
