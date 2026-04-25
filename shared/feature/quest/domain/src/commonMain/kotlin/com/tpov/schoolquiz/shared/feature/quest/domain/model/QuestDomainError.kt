package com.tpov.schoolquiz.shared.feature.quest.domain.model

/**
 * Domain error hierarchy for the quest feature.
 *
 * All domain errors extend [QuestDomainError] so callers can distinguish
 * quest-specific errors from generic exceptions via `is QuestDomainError`.
 *
 * Following Functional Core pattern: pure functions return [Result.failure]
 * with a [QuestDomainError] subtype, never throw.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md — Error/Recovery Rules.
 */
sealed class QuestDomainError(message: String) : Exception(message)

/**
 * Thrown when a requested quest is not found in the local cache.
 */
class QuestNotFoundError(val questId: QuestId) :
    QuestDomainError("Quest not found: ${questId.value}")
