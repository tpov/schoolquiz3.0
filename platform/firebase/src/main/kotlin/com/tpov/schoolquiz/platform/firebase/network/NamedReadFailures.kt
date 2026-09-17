package com.tpov.schoolquiz.platform.firebase.network

import com.tpov.schoolquiz.shared.core.catalog.data.CatalogDto
import com.tpov.schoolquiz.shared.core.catalog.data.CatalogRemoteDataSource
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncChange
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncChangeRemoteDataSource
import com.tpov.schoolquiz.shared.core.sync.LessonContentSyncChange
import com.tpov.schoolquiz.shared.core.sync.LessonContentSyncChangeRemoteDataSource
import com.tpov.schoolquiz.shared.core.sync.SyncChangePage
import com.tpov.schoolquiz.shared.core.sync.SyncCursor
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.ProfileBootstrapRequest
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.ProfileRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.LeagueStanding
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.PrivateQuestSnapshot
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.PrivateQuestSyncChange
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestPrivateRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewAssignmentChangeDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewAssignmentDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewAssignmentRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.SubmitReviewActionDto
import kotlin.coroutines.cancellation.CancellationException

// Вторая половина границы из CallableErrors.
//
// Отправка отложенной мутации давно проходит через toSyncError и уезжает наверх доменной ветвью.
// Чтения — каталога, приватных квестов, заданий на ревью, профиля — этой границы не имели вовсе:
// исключение SDK летело сквозь них как есть, и наверху от «нет сети» оставался только текст. По
// AD-15 наружу обязана выходить ветвь, а критерий 4.5 обещает игроку названную причину — значит
// читающая сторона должна проходить ровно через тот же разбор, а не заводить свой.
//
// Место выбрано на границе модуля, а не внутри каждого источника: в источниках чтение размазано
// по десяткам `get().await()`, и обернуть их поштучно значит однажды забыть. Здесь оборачивается
// сам порт целиком — что бы источник ни бросил, наверх выйдет SyncFailure. Реализации остаются
// как есть, а собирает пару композиционный корень: в graph уходит уже обёрнутый порт.

/**
 * Пропускает шаг обращения к серверу через разбор ошибок.
 *
 * Отмена корутины проходит насквозь: это не неудача обращения, и подменять её ветвью значит
 * проглотить остановку. Ловится она первой намеренно — на JVM `CancellationException` наследует
 * `Exception`, и общий `catch` иначе забрал бы её себе.
 *
 * Уже названная неудача повторно не заворачивается: ветвь у неё есть, и второй слой обёртки
 * только удлинил бы причину.
 */
internal suspend fun <T> withNamedFailure(step: suspend () -> T): T =
    try {
        step()
    } catch (e: CancellationException) {
        throw e
    } catch (e: SyncFailure) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        throw SyncFailure(e.toSyncError(), e)
    }

/** Чтение каталога, называющее свою неудачу ветвью [com.tpov.schoolquiz.shared.core.network.SyncError]. */
internal fun CatalogRemoteDataSource.namingFailures(): CatalogRemoteDataSource = NamedFailureCatalogReads(this)

/**
 * То же для журналов, из которых читается содержимое каталога.
 *
 * Они — та же «неудача чтения каталога» из критерия 4.2: сам список каталогов и его журналы
 * читаются одним проходом, и назвать причину только у половины прохода значит оставить игроку
 * то же «неизвестно» через раз.
 */
internal fun CatalogSyncChangeRemoteDataSource.namingFailures(): CatalogSyncChangeRemoteDataSource =
    NamedFailureCatalogJournalReads(this)

/** То же для журнала содержимого урока. */
internal fun LessonContentSyncChangeRemoteDataSource.namingFailures(): LessonContentSyncChangeRemoteDataSource =
    NamedFailureLessonContentJournalReads(this)

/** То же для журнала приватных квестов. */
internal fun QuestPrivateRemoteDataSource.namingFailures(): QuestPrivateRemoteDataSource =
    NamedFailureQuestPrivateReads(this)

/** То же для журнала заданий на ревью. */
internal fun ReviewAssignmentRemoteDataSource.namingFailures(): ReviewAssignmentRemoteDataSource =
    NamedFailureReviewAssignmentReads(this)

/** То же для профиля. */
internal fun ProfileRemoteDataSource.namingFailures(): ProfileRemoteDataSource = NamedFailureProfileReads(this)

private class NamedFailureCatalogReads(
    private val delegate: CatalogRemoteDataSource,
) : CatalogRemoteDataSource {
    override suspend fun fetchChangedSince(
        cursor: SyncCursor,
        limit: Int,
    ): List<CatalogDto> = withNamedFailure { delegate.fetchChangedSince(cursor, limit) }

    override suspend fun fetchByIds(ids: Set<String>): List<CatalogDto> = withNamedFailure { delegate.fetchByIds(ids) }
}

private class NamedFailureCatalogJournalReads(
    private val delegate: CatalogSyncChangeRemoteDataSource,
) : CatalogSyncChangeRemoteDataSource {
    override suspend fun fetchPage(
        catalogId: CatalogId,
        cursor: SyncCursor,
        limit: Int,
    ): SyncChangePage<CatalogSyncChange> = withNamedFailure { delegate.fetchPage(catalogId, cursor, limit) }
}

private class NamedFailureLessonContentJournalReads(
    private val delegate: LessonContentSyncChangeRemoteDataSource,
) : LessonContentSyncChangeRemoteDataSource {
    override suspend fun fetchPage(
        lessonId: LessonId,
        cursor: SyncCursor,
        limit: Int,
    ): SyncChangePage<LessonContentSyncChange> = withNamedFailure { delegate.fetchPage(lessonId, cursor, limit) }
}

private class NamedFailureQuestPrivateReads(
    private val delegate: QuestPrivateRemoteDataSource,
) : QuestPrivateRemoteDataSource {
    override suspend fun fetchPage(
        cursor: SyncCursor,
        limit: Int,
    ): SyncChangePage<PrivateQuestSyncChange> = withNamedFailure { delegate.fetchPage(cursor, limit) }

    override suspend fun fetchSnapshots(changes: List<PrivateQuestSyncChange>): List<PrivateQuestSnapshot> =
        withNamedFailure { delegate.fetchSnapshots(changes) }
}

private class NamedFailureReviewAssignmentReads(
    private val delegate: ReviewAssignmentRemoteDataSource,
) : ReviewAssignmentRemoteDataSource {
    override suspend fun fetchAssignmentChangePage(
        cursor: SyncCursor,
        limit: Int,
    ): SyncChangePage<ReviewAssignmentChangeDto> =
        withNamedFailure {
            delegate.fetchAssignmentChangePage(cursor, limit)
        }

    override suspend fun fetchByIds(ids: Set<String>): List<ReviewAssignmentDto> =
        withNamedFailure { delegate.fetchByIds(ids) }

    override suspend fun submitReviewAction(action: SubmitReviewActionDto) =
        withNamedFailure { delegate.submitReviewAction(action) }
}

private class NamedFailureProfileReads(
    private val delegate: ProfileRemoteDataSource,
) : ProfileRemoteDataSource {
    override suspend fun ensureProfile(request: ProfileBootstrapRequest): UserProfile =
        withNamedFailure { delegate.ensureProfile(request) }

    override suspend fun updateNickname(
        nickname: String,
        knownLanguages: List<String>,
    ): UserProfile = withNamedFailure { delegate.updateNickname(nickname, knownLanguages) }

    override suspend fun leagueStanding(): LeagueStanding = withNamedFailure { delegate.leagueStanding() }
}
