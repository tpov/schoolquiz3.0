package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

/** Local progress of a rating whose content was frozen with an arena settlement. */
sealed interface ArenaFrozenRatingState {
    /** The retained ticket/context is absent, corrupt, or belongs to another auth epoch. */
    data object Unavailable : ArenaFrozenRatingState

    /** A confirmed settlement may be rated, but no local rating intent exists yet. */
    data object Pending : ArenaFrozenRatingState

    /** The immutable first request is durable and is still awaiting a real server response. */
    data class Queued(val rating: Int) : ArenaFrozenRatingState

    /** Only a validated arena-service APPLIED receipt reaches this state. */
    data class Accepted(val rating: Int) : ArenaFrozenRatingState

    /** A legacy/shared marker or incomplete retained intent prevents safely proposing another vote. */
    data object ExistingUnverified : ArenaFrozenRatingState
}
