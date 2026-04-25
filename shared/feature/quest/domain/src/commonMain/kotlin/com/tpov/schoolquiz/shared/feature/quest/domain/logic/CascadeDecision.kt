package com.tpov.schoolquiz.shared.feature.quest.domain.logic

/**
 * Pure predicate for the cascade recurse decision (State Matrix 3).
 *
 * Determines whether children of a parent entity should be fetched from remote
 * after the parent is processed in Matrix 1/2. This is the "early-exit" guard:
 * if the parent's content has not changed, there is no reason to recurse into
 * its children (sections for a quest, themes for a section, etc.).
 *
 * ## Spec — State Matrix 3 rows:
 *
 *   | Condition                                         | Action        |
 *   |---------------------------------------------------|---------------|
 *   | parent absent (just inserted) + dto.cv > 0        | RECURSE       |
 *   | parent present + dto.cv > local.cv                | RECURSE       |
 *   | parent present + dto.cv == local.cv               | STOP          |
 *   | parent present + dto.cv < local.cv                | STOP (stale)  |
 *
 * ## Usage (Scenario 45 — quest → section early-exit):
 * ```kotlin
 * val localQuestCv: Long? = localQuest?.contentsVersion
 * if (shouldRecurseIntoChildren(dto.contentsVersion, localQuestCv)) {
 *     sectionRepository.refreshByParents(setOf(dto.id), cursor)
 * }
 * ```
 *
 * ## Usage (Scenario 45 — catalog → quest early-exit):
 * ```kotlin
 * val localCatalogCv: Long? = localCatalog?.contentsVersion
 * if (shouldRecurseIntoChildren(dto.contentsVersion, localCatalogCv)) {
 *     questRepository.refreshFromRemote(...)
 * }
 * ```
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 *   State Matrix Matrix 3 (cascade recurse predicate)
 *   Business Rule #7: "клиент идёт на уровень N+1 только если dto.cv > local.cv"
 *   Domain Test Scenarios 28-31, 45
 *
 * @param dtoContentsVersion    the `contentsVersion` from the incoming server DTO.
 * @param localContentsVersion  the `contentsVersion` from the local cache, or `null`
 *                              if the entity was just inserted (absent before sync).
 * @return `true` if children should be fetched; `false` to stop the cascade.
 */
fun shouldRecurseIntoChildren(
    dtoContentsVersion: Long,
    localContentsVersion: Long?,
): Boolean {
    if (localContentsVersion == null) {
        // Parent was just inserted — recurse only if it actually has content
        return dtoContentsVersion > 0L
    }
    return dtoContentsVersion > localContentsVersion
}
