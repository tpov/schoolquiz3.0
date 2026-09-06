package com.tpov.schoolquiz.shared.feature.quest.data

import com.tpov.schoolquiz.shared.core.catalog.data.StorageUrlResolver
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.outbox.OutboxEntitySync
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.feature.quest.data.mapper.QuestDtoMapper.toEntity
import com.tpov.schoolquiz.shared.feature.quest.data.mapper.QuestMapper.toDomain
import com.tpov.schoolquiz.shared.feature.quest.data.outbox.QuestShelfOutbox
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestShelfChange
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestShelfSyncState
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

class QuestRepositoryImpl(
    private val local: QuestLocalDataSource,
    private val remote: QuestRemoteDataSource,
    private val storageUrlResolver: StorageUrlResolver,
    private val outbox: OutboxStore,
    private val clock: Clock = Clock.System,
) : QuestRepository {
    /** «Найти старое → положить новое» — два шага; замок не даёт двум нажатиям положить две записи. */
    private val shelfQueueLock = Mutex()

    override fun observeMyQuests(
        authorUid: String,
        catalogId: CatalogId?,
    ): Flow<List<Quest>> =
        local.observeMyQuests(authorUid, catalogId?.value).map { list ->
            list.map { it.toDomain() }
        }

    override fun observeByShelf(shelf: String): Flow<List<Quest>> =
        local.observeByShelf(shelf).map { list -> list.map { it.toDomain() } }

    override fun observeByCatalog(
        catalogId: CatalogId,
        shelf: String,
    ): Flow<List<Quest>> = local.observeByCatalog(catalogId.value, shelf).map { list -> list.map { it.toDomain() } }

    override fun observeDownloadedArchivedByCatalog(
        catalogId: CatalogId,
        shelf: String,
    ): Flow<List<Quest>> =
        local.observeDownloadedArchivedByCatalog(catalogId.value, shelf).map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun getById(id: QuestId): Quest? = local.findById(id.value)?.toDomain()

    override suspend fun refreshByIds(ids: Set<QuestId>): Result<Unit> {
        if (ids.isEmpty()) return Result.success(Unit)
        return try {
            val requested = ids.map { it.value }.toSet()
            val dtos = remote.fetchByIds(requested)
            val returned = dtos.map { it.id }.toSet()
            for (missingId in requested - returned) {
                local.deleteById(missingId)
            }
            for (dto in dtos) {
                applyDto(dto, fromSyncList = true)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Намерение — в очередь, строка квеста — нетронутой (AD-25).
     *
     * Всё, что лежало по этому квесту, снимается перед новым намерением. Неотправленное — потому
     * что очередь порядка не обещает (AD-27), а изменённое содержимое — новая мутация (AD-2).
     * Карантинное — потому что оно терминально и держится только как пометка на карточке (AD-28):
     * новое нажатие по той же карточке эту пометку и снимает. Пока карантинная запись оставалась
     * лежать, она возвращалась на карточку, стоило новому переносу доехать и удалиться (AD-4):
     * самой свежей записью по ссылке снова оказывался старый отказ.
     *
     * Под замком, потому что «найти старое → положить новое» — два шага, и два быстрых нажатия
     * без него давали две записи, и обе ехали. Транзакция базы не нужна: локальной половины нет,
     * и худшее при смерти процесса между шагами — старое намерение снято, нового нет, и карточка
     * честно покажет «неотправленного нет».
     */
    override suspend fun queueShelfChange(change: QuestShelfChange): Result<Unit> =
        try {
            shelfQueueLock.withLock {
                val entityRef = QuestShelfOutbox.entityRef(change.questId)
                withdrawAll(change.ownerUid, entityRef)
                outbox.enqueue(QuestShelfOutbox.record(change, createdAtMs = clock.now().toEpochMilliseconds()))
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    /** Снимает записи по ссылке одну за другой: выборка отдаёт по одной, а лежать может не одна. */
    private suspend fun withdrawAll(
        ownerUid: String,
        entityRef: String,
    ) {
        var stale = outbox.findByEntityRef(ownerUid, entityRef)
        while (stale != null) {
            outbox.remove(stale.id)
            stale = outbox.findByEntityRef(ownerUid, entityRef)
        }
    }

    override fun observeShelfChangeState(
        ownerUid: String,
        questId: QuestId,
    ): Flow<QuestShelfSyncState> =
        outbox.observeEntity(ownerUid, QuestShelfOutbox.entityRef(questId))
            .map { sync ->
                when (sync) {
                    OutboxEntitySync.None -> QuestShelfSyncState.NONE
                    is OutboxEntitySync.Queued ->
                        when (sync.state) {
                            OutboxState.SYNCHRONOUS -> QuestShelfSyncState.NONE
                            OutboxState.WAITING, OutboxState.WAITING_PRECONDITION -> QuestShelfSyncState.PENDING
                            // Перенос не версионируется, конфликта по нему не бывает; если запись
                            // всё же в нём оказалась, она вне выборки и ждёт — это ожидание, не
                            // карантин.
                            OutboxState.CONFLICT -> QuestShelfSyncState.PENDING
                            OutboxState.QUARANTINED -> QuestShelfSyncState.QUARANTINED
                        }
                }
            }
            .distinctUntilChanged()

    override suspend fun retirePublic(questId: QuestId): Result<Unit> =
        try {
            remote.retirePublic(questId.value)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    override suspend fun refreshFromRemote(
        currentUserUid: String?,
        availableShelves: Set<String>,
        catalogIdsToSync: Set<CatalogId>,
        cursor: Long,
    ): Result<Set<QuestId>> {
        if (catalogIdsToSync.isEmpty()) return Result.success(emptySet())
        return try {
            val catalogIdStrings = catalogIdsToSync.map { it.value }.toSet()
            val dtos =
                coroutineScope {
                    // Query A skipped when currentUserUid is null (guest mode — ADR-CMP-49 #2)
                    val queryA =
                        async {
                            if (currentUserUid == null) {
                                emptyList()
                            } else {
                                remote.fetchOwnChanged(currentUserUid, catalogIdStrings, cursor)
                            }
                        }
                    val queryB =
                        async {
                            remote.fetchPublicChanged(availableShelves, cursor)
                                .filter { it.catalogId in catalogIdStrings }
                        }
                    // A wins on id conflict: own quests take priority over public results
                    (queryA.await() + queryB.await()).distinctBy { it.id }
                }
            val changedIds = mutableSetOf<QuestId>()
            for (dto in dtos) {
                if (applyDto(dto, fromSyncList = false)) {
                    changedIds.add(QuestId(dto.id))
                }
            }
            Result.success(changedIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun applyDto(
        dto: com.tpov.schoolquiz.shared.feature.quest.data.dto.QuestDto,
        fromSyncList: Boolean,
    ): Boolean {
        val localEntity = local.findById(dto.id)
        val path = dto.picturePath
        val retainedUrl =
            localEntity?.pictureUrl?.takeIf {
                localEntity.picturePath == path
            }
        val resolvedUrl =
            when {
                path == null -> null
                retainedUrl != null -> retainedUrl
                else ->
                    runCatching { storageUrlResolver(path) }
                        .onFailure { if (it is CancellationException) throw it }
                        .getOrNull()
            }
        val entity = dto.toEntity(resolvedUrl)
        return if (dto.visibleOn.isEmpty()) {
            val localVersion = localEntity?.version ?: 0L
            if (fromSyncList || dto.version > localVersion) local.deleteById(dto.id)
            false
        } else {
            if (fromSyncList) local.upsertFromSyncList(entity) else local.upsertByIdIfNewerVersion(entity)
            true
        }
    }
}
