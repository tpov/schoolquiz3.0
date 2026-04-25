package com.tpov.schoolquiz.shared.core.catalog.domain.fake

import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory fake implementation of [CatalogRepository] for use case testing.
 *
 * Backed by a [MutableStateFlow] keyed by [CatalogId]. Tests can seed the
 * cache, configure server state via [seedServerCatalogs] / [simulateRemoteCatalogs],
 * simulate refresh failures via [setNextRefreshFailure], and verify that
 * [observeAll] emits the expected sorted lists.
 *
 * ## Delta-sync semantics — State Matrix 1 ordering (Fix #A — version guard FIRST)
 *
 * [refreshFromRemote] applies per-id upsert logic matching the real
 * Firestore-backed implementation contract (State Matrix 1).
 *
 * **CRITICAL ordering**: version check is the outer condition for the `present` case.
 * Delete-marker is only evaluated AFTER confirming dto.version > local.version.
 *
 *   **absent (local == null):**
 *   - `dto.archived == true` → **SKIP** (no tombstone created)
 *   - `dto.archived == false` → **INSERT**
 *
 *   **present (local != null) — version check FIRST:**
 *   - `dto.version <= local.version` → **SKIP** (stale or equal — regardless of archived)
 *   - `dto.version > local.version` + `dto.archived == true` → **DELETE** local
 *   - `dto.version > local.version` + `dto.archived == false` → **UPSERT**
 *
 * After each successful refresh the cursor is advanced to `max(dto.lastModifiedAt)`.
 *
 * This is a full fake (not a mock): it implements the repository contract
 * completely without any test framework dependencies.
 *
 * Determinism: [observeAll] emits lists sorted by `id.value` ASC, matching
 * the production repository contract.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 *   State Matrix 1, FR#14 Step 1, Domain Test Scenarios 21-23, 23b, 23c.
 */
class FakeCatalogRepository(
    initial: List<Catalog> = emptyList(),
) : CatalogRepository {

    private val cache = MutableStateFlow<Map<CatalogId, Catalog>>(
        initial.associateBy { it.id },
    )

    /**
     * The "server" state that [refreshFromRemote] will apply on the next call.
     * Set via [simulateRemoteCatalogs] (legacy: replaces whole pending list) or
     * [seedServerCatalogs] (explicit server state).
     */
    private var serverCatalogs: List<Catalog>? = null

    /** Optional failure to surface from the next [refreshFromRemote] call. */
    private var nextRefreshFailure: Throwable? = null

    /**
     * The cursor (max lastModifiedAt) advanced after each successful [refreshFromRemote].
     * Starts at 0; advances to max(dto.lastModifiedAt) of processed items.
     */
    var lastRefreshCursor: Long = 0L
        private set

    /** How many times [refreshFromRemote] was invoked. */
    var refreshCallCount: Int = 0
        private set

    // ── CatalogRepository implementation ─────────────────────────────────────

    override fun observeAll(): Flow<List<Catalog>> =
        cache.map { map -> map.values.sortedBy { it.id.value } }

    override suspend fun refreshFromRemote(): Result<Set<CatalogId>> {
        refreshCallCount++
        val failure = nextRefreshFailure
        if (failure != null) {
            nextRefreshFailure = null
            return Result.failure(failure)
        }
        val remote = serverCatalogs ?: return Result.success(emptySet())
        serverCatalogs = null

        val current = cache.value
        val mutable = current.toMutableMap()
        val changedIds = mutableSetOf<CatalogId>()
        var maxLastMod = lastRefreshCursor
        for (dto in remote) {
            maxLastMod = maxOf(maxLastMod, dto.lastModifiedAt)
            val local = mutable[dto.id]
            if (local == null) {
                if (dto.archived) continue
                mutable[dto.id] = dto
                changedIds.add(dto.id)
                continue
            }
            if (dto.version <= local.version) continue
            if (dto.archived) {
                mutable.remove(dto.id)
            } else {
                mutable[dto.id] = dto
                changedIds.add(dto.id)
            }
        }
        lastRefreshCursor = maxLastMod
        cache.value = mutable
        return Result.success(changedIds)
    }

    override suspend fun getById(id: CatalogId): Catalog? = cache.value[id]

    // ── Test helpers (not part of the repository interface) ──────────────────

    /**
     * Seeds the local cache directly, bypassing any refresh logic.
     * Useful for arranging baseline state at the start of a test.
     */
    fun seed(catalogs: List<Catalog>) {
        cache.value = catalogs.associateBy { it.id }
    }

    /**
     * Configures the server state that the next [refreshFromRemote] call will
     * apply. Equivalent to [simulateRemoteCatalogs] — preferred alias for
     * clarity in delta-sync tests.
     */
    fun seedServerCatalogs(catalogs: List<Catalog>) {
        serverCatalogs = catalogs
    }

    /**
     * Arranges the set of catalogs the *next* [refreshFromRemote] call will
     * pull from the simulated remote. Alias kept for backward compat with
     * existing tests.
     */
    fun simulateRemoteCatalogs(catalogs: List<Catalog>) {
        serverCatalogs = catalogs
    }

    /**
     * Arranges the next [refreshFromRemote] call to surface [error]
     * without touching the local cache.
     */
    fun setNextRefreshFailure(error: Throwable) {
        nextRefreshFailure = error
    }

    /** Current snapshot of the local cache sorted by id ASC. */
    fun snapshot(): List<Catalog> = cache.value.values.sortedBy { it.id.value }
}
