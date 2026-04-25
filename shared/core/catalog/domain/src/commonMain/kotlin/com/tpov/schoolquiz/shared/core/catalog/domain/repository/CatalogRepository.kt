package com.tpov.schoolquiz.shared.core.catalog.domain.repository

import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import kotlinx.coroutines.flow.Flow

/**
 * Domain boundary for reading and syncing [Catalog]s.
 *
 * Production implementation (Firestore + Room) lives in the data layer and is
 * introduced in phase-01. Use case tests drive this boundary through an
 * in-memory fake (`FakeCatalogRepository`) located in the test source set.
 *
 * Determinism contract (Codex fix #6): [observeAll] emits lists sorted by
 * `id.value` ASC. Firestore order is non-deterministic, so the client
 * applies a stable sort before emitting. This avoids flaky tests and
 * guarantees consistent UI ordering across screens.
 *
 * Domain rules:
 *  - signatures reference only domain types (no DTO, Entity, HTTP codes);
 *  - no DI annotations — wiring is phase-01 concern;
 *  - `suspend fun` and `Flow<T>` are allowed here because this is the
 *    boundary between the pure core and the imperative shell.
 *
 * Spec: docs/features/catalog-foundation/0-spec.md — Feature Domain Contract.
 */
interface CatalogRepository {

    /**
     * Observes the locally cached list of catalogs sorted by `id.value` ASC.
     *
     * First emission reflects the current persisted state (may be empty on a
     * cold start before [refreshFromRemote] succeeds). Subsequent emissions
     * occur whenever the local cache changes (remote sync, write-through, …).
     */
    fun observeAll(): Flow<List<Catalog>>

    /**
     * Pulls the latest catalog set from Firestore and persists it into the
     * local cache.
     *
     * Result contract:
     *  - success on any completed pull (including empty server response);
     *  - failure on network/permission errors — cached list continues to
     *    emit unchanged through [observeAll].
     */
    suspend fun refreshFromRemote(): Result<Set<CatalogId>>

    /**
     * Returns a single catalog by id from the local cache, or `null` if
     * the id is not present.
     *
     * Intended for cross-aggregate validation (e.g. [Quest] create-time
     * check that the referenced catalog exists). Does not trigger remote
     * fetch — callers that need fresh data should [refreshFromRemote] first.
     */
    suspend fun getById(id: CatalogId): Catalog?
}
