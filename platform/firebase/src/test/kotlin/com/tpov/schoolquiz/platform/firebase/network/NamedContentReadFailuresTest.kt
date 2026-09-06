package com.tpov.schoolquiz.platform.firebase.network

import com.google.firebase.firestore.FirebaseFirestoreException
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.stats.RawUserStats
import com.tpov.schoolquiz.shared.core.stats.UserStatsDataSource
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
import com.tpov.schoolquiz.shared.feature.question.data.QuestionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.question.data.dto.QuestionDto
import com.tpov.schoolquiz.shared.feature.section.data.SectionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.section.data.dto.SectionDto
import com.tpov.schoolquiz.shared.feature.theme.data.ThemeRemoteDataSource
import com.tpov.schoolquiz.shared.feature.theme.data.dto.ThemeDto
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Узлы содержимого и собственные числа аккаунта называют свою неудачу так же, как журналы.
 *
 * Журнал каталога говорит только «перечитай этот узел» — сам узел приезжает через `quests`,
 * `sections`, `themes`, `lessons`, `questions`. Пока эти пять портов стояли голыми, «нет сети» на
 * шаге каталога уезжало наверх как «неизвестно» ровно через раз: смотря на каком чтении оборвалась
 * связь. То же у чисел аккаунта, которые читают оба прогона.
 *
 * Проверка идёт от брошенного исключения к ветви — готовую ветвь здесь никто не подсовывает.
 */
class NamedContentReadFailuresTest {

    private fun firestoreOffline(): FirebaseFirestoreException =
        FirebaseFirestoreException(
            "Failed to get documents from server",
            FirebaseFirestoreException.Code.UNAVAILABLE,
        )

    @Test
    fun `given the network is gone when a nickname is bought then the branch is no network, not a platform string`() =
        runTest {
            // Прежде эта ветвь не существовала вовсе: syncErrorOrNull давал null, и наверх уходило
            // английское message исключения Firebase — то самое, что запрещает AD-15.
            val nicknames = FailingNicknameReads { UnknownHostException("firebaseapp.com") }.namingFailures()

            val failure = caughtFailure { nicknames.buy("Имя") }

            assertEquals(SyncError.NoNetwork, failure.error)
        }

    @Test
    fun `given the server refuses when a logo is worn then the branch names the reason`() =
        runTest {
            val refusal =
                FirebaseFirestoreException(
                    "Logo is not owned",
                    FirebaseFirestoreException.Code.PERMISSION_DENIED,
                )
            val logos = FailingLogoReads { refusal }.namingFailures()

            val failure = caughtFailure { logos.wear("fox") }

            assertEquals(SyncError.Refused("Logo is not owned"), failure.error)
        }

    @Test
    fun `given firestore says offline when quests are read by id then the branch is no network`() =
        runTest {
            val quests = FailingQuestReads { firestoreOffline() }.namingFailures()

            val failure = caughtFailure { quests.fetchByIds(setOf("q-1")) }

            assertEquals(SyncError.NoNetwork, failure.error)
        }

    @Test
    fun `given firestore refuses when sections are read by id then the branch is a refusal that names the reason`() =
        runTest {
            val refusal =
                FirebaseFirestoreException(
                    "Missing or insufficient permissions",
                    FirebaseFirestoreException.Code.PERMISSION_DENIED,
                )
            val sections = FailingSectionReads { refusal }.namingFailures()

            val failure = caughtFailure { sections.fetchByIds(setOf("s-1")) }

            assertEquals(SyncError.Refused("Missing or insufficient permissions"), failure.error)
        }

    @Test
    fun `given firestore says offline when themes are read by parent then the branch is no network`() =
        runTest {
            val themes = FailingThemeReads { firestoreOffline() }.namingFailures()

            val failure = caughtFailure { themes.fetchChangedByParents(setOf("s-1"), cursor = 0L) }

            assertEquals(SyncError.NoNetwork, failure.error)
        }

    @Test
    fun `given the connection breaks when lessons are read by id then the branch is no network`() =
        runTest {
            val lessons = FailingLessonReads { UnknownHostException("firestore") }.namingFailures()

            val failure = caughtFailure { lessons.fetchByIds(setOf("l-1")) }

            assertEquals(SyncError.NoNetwork, failure.error)
        }

    @Test
    fun `given firestore says offline when questions are read by id then the branch is no network`() =
        runTest {
            val questions = FailingQuestionReads { firestoreOffline() }.namingFailures()

            val failure = caughtFailure { questions.fetchByIds(setOf("qn-1")) }

            assertEquals(SyncError.NoNetwork, failure.error)
        }

    @Test
    fun `given firestore says offline when the account numbers are read then the branch is no network`() =
        runTest {
            val offline = firestoreOffline()
            val numbers = FailingUserStatsReads { offline }.namingFailures()

            val failure = caughtFailure { numbers.fetchRaw() }

            assertEquals(SyncError.NoNetwork, failure.error)
            // Исходное исключение остаётся причиной: журналу оно нужно, экрану — нет (AD-15).
            assertSame(offline, failure.cause)
        }

    @Test
    fun `given a content read is cancelled then cancellation passes through and does not become a branch`() =
        runTest {
            val cancelled = CancellationException("scope closed")
            val questions = FailingQuestionReads { cancelled }.namingFailures()

            val thrown = thrownBy<CancellationException> { questions.fetchByIds(setOf("qn-1")) }

            assertSame(cancelled, thrown)
        }
}

private suspend fun caughtFailure(block: suspend () -> Unit): SyncFailure = thrownBy(block)

/**
 * То же, что `assertFailsWith`, но без `kotlin-test`: модуль собирается на JUnit 4.
 */
private suspend inline fun <reified T : Throwable> thrownBy(block: suspend () -> Unit): T {
    try {
        block()
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Throwable,
    ) {
        if (e is T) return e
        throw AssertionError("ждали ${T::class.simpleName}, а прилетело ${e::class.simpleName}", e)
    }
    throw AssertionError("ждали ${T::class.simpleName}, а не прилетело ничего")
}

// ── Источники, которые не отвечают ───────────────────────────────────────────

private class FailingQuestReads(
    private val failure: () -> Throwable,
) : QuestRemoteDataSource {
    override suspend fun fetchByIds(ids: Set<String>): List<QuestDto> = throw failure()

    override suspend fun fetchOwnChanged(
        authorUid: String,
        catalogIds: Set<String>,
        cursor: Long,
    ): List<QuestDto> = throw failure()

    override suspend fun fetchPublicChanged(
        shelves: Set<String>,
        cursor: Long,
    ): List<QuestDto> = throw failure()
}

private class FailingSectionReads(
    private val failure: () -> Throwable,
) : SectionRemoteDataSource {
    override suspend fun fetchByIds(ids: Set<String>): List<SectionDto> = throw failure()

    override suspend fun fetchChangedByParents(
        questIds: Set<String>,
        cursor: Long,
    ): List<SectionDto> = throw failure()
}

private class FailingThemeReads(
    private val failure: () -> Throwable,
) : ThemeRemoteDataSource {
    override suspend fun fetchByIds(ids: Set<String>): List<ThemeDto> = throw failure()

    override suspend fun fetchChangedByParents(
        sectionIds: Set<String>,
        cursor: Long,
    ): List<ThemeDto> = throw failure()
}

private class FailingLessonReads(
    private val failure: () -> Throwable,
) : LessonRemoteDataSource {
    override suspend fun fetchByIds(ids: Set<String>): List<LessonDto> = throw failure()

    override suspend fun fetchChangedByParents(
        themeIds: Set<String>,
        cursor: Long,
    ): List<LessonDto> = throw failure()
}

private class FailingQuestionReads(
    private val failure: () -> Throwable,
) : QuestionRemoteDataSource {
    override suspend fun fetchByIds(ids: Set<String>): List<QuestionDto> = throw failure()

    override suspend fun fetchChangedByParents(
        lessonIds: Set<String>,
        cursor: Long,
    ): List<QuestionDto> = throw failure()
}

private class FailingNicknameReads(
    private val failure: () -> Throwable,
) : NicknameRemoteDataSource {
    override suspend fun checkAvailability(nickname: String): NicknameAvailability = throw failure()

    override suspend fun owned(): List<OwnedNickname> = throw failure()

    override suspend fun setActive(nickname: String) = throw failure()

    override suspend fun claim(nickname: String): Long = throw failure()

    override suspend fun listings(limit: Int): List<NicknameListing> = throw failure()

    override suspend fun listForSale(
        nickname: String,
        price: Long,
    ) = throw failure()

    override suspend fun buy(nickname: String): Long = throw failure()
}

private class FailingLogoReads(
    private val failure: () -> Throwable,
) : LogoRemoteDataSource {
    override suspend fun catalog(): List<ProfileLogo> = throw failure()

    override suspend fun buy(logo: String): Long = throw failure()

    override suspend fun wear(logo: String): String = throw failure()

    override suspend fun listings(limit: Int): List<LogoListing> = throw failure()

    override suspend fun listForSale(
        logo: String,
        price: Long,
    ) = throw failure()

    override suspend fun buyListed(logo: String): Long = throw failure()
}

private class FailingUserStatsReads(
    private val failure: () -> Throwable,
) : UserStatsDataSource {
    override fun observeRaw(): Flow<RawUserStats> = flowOf(RawUserStats())

    override suspend fun fetchRaw(): RawUserStats = throw failure()
}
