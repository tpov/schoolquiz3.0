package com.tpov.schoolquiz.platform.firebase.network

import com.tpov.schoolquiz.shared.core.stats.RawUserStats
import com.tpov.schoolquiz.shared.core.stats.UserStatsDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.remote.EconomyConstantsRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.remote.EconomyConstantsResponse
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.LogoRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.NicknameRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.LogoListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameAvailability
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.OwnedNickname
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileLogo
import com.tpov.schoolquiz.shared.feature.lesson.data.LessonRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson.data.dto.LessonDto
import com.tpov.schoolquiz.shared.feature.quest.data.QuestRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest.data.dto.QuestDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestSubmissionOutcome
import com.tpov.schoolquiz.shared.feature.question.data.QuestionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.question.data.dto.QuestionDto
import com.tpov.schoolquiz.shared.feature.section.data.SectionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.section.data.dto.SectionDto
import com.tpov.schoolquiz.shared.feature.theme.data.ThemeRemoteDataSource
import com.tpov.schoolquiz.shared.feature.theme.data.dto.ThemeDto
import kotlinx.coroutines.flow.Flow

// Продолжение границы из NamedReadFailures — те же читающие шаги, но на второй половине прогонов.
//
// Журналы каталога называют свою неудачу с прошлого раза, а вот то, ради чего журнал и читается,
// не называло: запись журнала говорит только «перечитай этот узел», и сам узел приезжает уже через
// `quests`, `sections`, `themes`, `lessons`, `questions`. Пока эти пять портов стояли голыми, «нет
// сети» на шаге каталога доезжало до экрана как «неизвестно» через раз — в зависимости от того, на
// каком именно чтении оборвалась связь. То же у собственных чисел аккаунта: они читаются и
// контентным прогоном, и профильным, и до сих пор ни один из них не мог назвать причину.
//
// Обёртки те же по устройству и по причине: порт целиком, а не каждое `get().await()` внутри
// источника, — и собирает пару композиционный корень.

/** Чтение квестов, называющее свою неудачу ветвью [com.tpov.schoolquiz.shared.core.network.SyncError]. */
internal fun QuestRemoteDataSource.namingFailures(): QuestRemoteDataSource = NamedFailureQuestReads(this)

/** То же для разделов. */
internal fun SectionRemoteDataSource.namingFailures(): SectionRemoteDataSource = NamedFailureSectionReads(this)

/** То же для тем. */
internal fun ThemeRemoteDataSource.namingFailures(): ThemeRemoteDataSource = NamedFailureThemeReads(this)

/** То же для уроков. */
internal fun LessonRemoteDataSource.namingFailures(): LessonRemoteDataSource = NamedFailureLessonReads(this)

/** То же для вопросов. */
internal fun QuestionRemoteDataSource.namingFailures(): QuestionRemoteDataSource = NamedFailureQuestionReads(this)

/**
 * То же для собственных чисел аккаунта.
 *
 * Шаг участвует в обоих прогонах сразу — и в контентном, и в профильном, — поэтому без имени
 * причины «нет сети» оставалось «неизвестным» в обоих следах.
 */
internal fun UserStatsDataSource.namingFailures(): UserStatsDataSource = NamedFailureUserStatsReads(this)

/**
 * Ник и логотип: единственные порты профиля, чья неудача не имела ветви вовсе.
 *
 * Без обёртки `syncErrorOrNull()` возвращал `null`, и экран магазина поднимал наверх `message`
 * исключения Firebase — английскую строку платформы. Ровно то, что запрещают AD-15 и первый эпик,
 * ради которого всё это и заводилось.
 */
internal fun NicknameRemoteDataSource.namingFailures(): NicknameRemoteDataSource = NamedFailureNicknameReads(this)

/** То же для логотипов. */
internal fun LogoRemoteDataSource.namingFailures(): LogoRemoteDataSource = NamedFailureLogoReads(this)

private class NamedFailureNicknameReads(
    private val delegate: NicknameRemoteDataSource,
) : NicknameRemoteDataSource {
    override suspend fun checkAvailability(nickname: String): NicknameAvailability =
        withNamedFailure { delegate.checkAvailability(nickname) }

    override suspend fun owned(): List<OwnedNickname> = withNamedFailure { delegate.owned() }

    override suspend fun setActive(nickname: String) = withNamedFailure { delegate.setActive(nickname) }

    override suspend fun claim(nickname: String): Long = withNamedFailure { delegate.claim(nickname) }

    override suspend fun listings(limit: Int): List<NicknameListing> = withNamedFailure { delegate.listings(limit) }

    override suspend fun listForSale(
        nickname: String,
        price: Long,
    ) = withNamedFailure { delegate.listForSale(nickname, price) }

    override suspend fun buy(nickname: String): Long = withNamedFailure { delegate.buy(nickname) }
}

private class NamedFailureLogoReads(
    private val delegate: LogoRemoteDataSource,
) : LogoRemoteDataSource {
    override suspend fun catalog(): List<ProfileLogo> = withNamedFailure { delegate.catalog() }

    override suspend fun buy(logo: String): Long = withNamedFailure { delegate.buy(logo) }

    override suspend fun wear(logo: String): String = withNamedFailure { delegate.wear(logo) }

    override suspend fun listings(limit: Int): List<LogoListing> = withNamedFailure { delegate.listings(limit) }

    override suspend fun listForSale(
        logo: String,
        price: Long,
    ) = withNamedFailure { delegate.listForSale(logo, price) }

    override suspend fun buyListed(logo: String): Long = withNamedFailure { delegate.buyListed(logo) }
}

/**
 * То же для вердиктов по заявкам автора и для чисел экономики.
 *
 * Эти два шага стоят в контентном прогоне ПЕРЕД каталогом. Пока они молчали именем, у гостя —
 * у кого шаги аккаунта не делают ничего — первой неудачей прогона становилась именно безымянная,
 * и «нет сети» от каталога пряталось за «неизвестно». Порядок шагов задаёт композиционный корень,
 * и причина не должна зависеть от того, вошёл ли игрок в аккаунт.
 */
internal fun QuestArenaSubmissionRemoteDataSource.namingFailures(): QuestArenaSubmissionRemoteDataSource =
    NamedFailureArenaOutcomeReads(this)

/** То же для чисел экономики: тот же прогон, тот же порядок, та же безымянность. */
internal fun EconomyConstantsRemoteDataSource.namingFailures(): EconomyConstantsRemoteDataSource =
    NamedFailureEconomyConstantsReads(this)

private class NamedFailureArenaOutcomeReads(
    private val delegate: QuestArenaSubmissionRemoteDataSource,
) : QuestArenaSubmissionRemoteDataSource {
    override suspend fun fetchOutcomes(ownerUid: String): List<QuestSubmissionOutcome> =
        withNamedFailure { delegate.fetchOutcomes(ownerUid) }
}

private class NamedFailureEconomyConstantsReads(
    private val delegate: EconomyConstantsRemoteDataSource,
) : EconomyConstantsRemoteDataSource {
    override suspend fun fetch(knownVersion: Long): EconomyConstantsResponse =
        withNamedFailure { delegate.fetch(knownVersion) }
}

private class NamedFailureQuestReads(
    private val delegate: QuestRemoteDataSource,
) : QuestRemoteDataSource {
    override suspend fun fetchByIds(ids: Set<String>): List<QuestDto> = withNamedFailure { delegate.fetchByIds(ids) }

    override suspend fun fetchOwnChanged(
        authorUid: String,
        catalogIds: Set<String>,
        cursor: Long,
    ): List<QuestDto> = withNamedFailure { delegate.fetchOwnChanged(authorUid, catalogIds, cursor) }

    override suspend fun fetchPublicChanged(
        shelves: Set<String>,
        cursor: Long,
    ): List<QuestDto> = withNamedFailure { delegate.fetchPublicChanged(shelves, cursor) }

    override suspend fun retirePublic(questId: String) = withNamedFailure { delegate.retirePublic(questId) }
}

private class NamedFailureSectionReads(
    private val delegate: SectionRemoteDataSource,
) : SectionRemoteDataSource {
    override suspend fun fetchByIds(ids: Set<String>): List<SectionDto> = withNamedFailure { delegate.fetchByIds(ids) }

    override suspend fun fetchChangedByParents(
        questIds: Set<String>,
        cursor: Long,
    ): List<SectionDto> = withNamedFailure { delegate.fetchChangedByParents(questIds, cursor) }
}

private class NamedFailureThemeReads(
    private val delegate: ThemeRemoteDataSource,
) : ThemeRemoteDataSource {
    override suspend fun fetchByIds(ids: Set<String>): List<ThemeDto> = withNamedFailure { delegate.fetchByIds(ids) }

    override suspend fun fetchChangedByParents(
        sectionIds: Set<String>,
        cursor: Long,
    ): List<ThemeDto> = withNamedFailure { delegate.fetchChangedByParents(sectionIds, cursor) }
}

private class NamedFailureLessonReads(
    private val delegate: LessonRemoteDataSource,
) : LessonRemoteDataSource {
    override suspend fun fetchByIds(ids: Set<String>): List<LessonDto> = withNamedFailure { delegate.fetchByIds(ids) }

    override suspend fun fetchChangedByParents(
        themeIds: Set<String>,
        cursor: Long,
    ): List<LessonDto> = withNamedFailure { delegate.fetchChangedByParents(themeIds, cursor) }
}

private class NamedFailureQuestionReads(
    private val delegate: QuestionRemoteDataSource,
) : QuestionRemoteDataSource {
    override suspend fun fetchByIds(ids: Set<String>): List<QuestionDto> = withNamedFailure { delegate.fetchByIds(ids) }

    override suspend fun fetchChangedByParents(
        lessonIds: Set<String>,
        cursor: Long,
    ): List<QuestionDto> = withNamedFailure { delegate.fetchChangedByParents(lessonIds, cursor) }
}

private class NamedFailureUserStatsReads(
    private val delegate: UserStatsDataSource,
) : UserStatsDataSource {
    /**
     * Подписка проходит как есть.
     *
     * Обрыв живого слушателя — не неудача шага синхронизации: подписка закрывается сама и
     * пересоздаётся сменой аккаунта, а исход прогона от неё не зависит. Заворачивать её здесь
     * значило бы назвать ветвью то, что ни в один след не попадает.
     */
    override fun observeRaw(): Flow<RawUserStats> = delegate.observeRaw()

    override suspend fun fetchRaw(): RawUserStats = withNamedFailure { delegate.fetchRaw() }
}
