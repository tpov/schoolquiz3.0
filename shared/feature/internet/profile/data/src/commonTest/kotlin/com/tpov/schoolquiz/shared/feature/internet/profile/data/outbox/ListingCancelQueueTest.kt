package com.tpov.schoolquiz.shared.feature.internet.profile.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.feature.internet.profile.data.LogoRepositoryImpl
import com.tpov.schoolquiz.shared.feature.internet.profile.data.NicknameRepositoryImpl
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.LogoRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.NicknameRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ListingCancelSync
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.LogoListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameAvailability
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.OwnedNickname
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileLogo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Снятие лота с продажи уезжает очередью, а не callable (история 5.6).
 *
 * Локальной половины у него нет (AD-25): в очередь ложится намерение с ключом лота, сервер не
 * зовётся, ни одна локальная строка не меняется. Что с записью дальше, строка лота читает из
 * очереди по ссылке на сущность (AD-14); карантин — «лот уже продан».
 */
class ListingCancelQueueTest {

    private val outbox = FakeOutboxStore()
    private val remoteNames = FakeNicknameRemote()
    private val remoteLogos = FakeLogoRemote()
    private var now = 1_000L
    private val uid = MutableStateFlow<String?>("uid-1")

    // Одна очередь на оба репозитория, как в Koin: замок в ней общий, и гонку двух нажатий
    // можно столкнуть только через один экземпляр.
    private val queue = ListingCancelQueue(outbox = outbox, currentUidFlow = { uid }, nowMs = { now })

    private fun names() = NicknameRepositoryImpl(remote = remoteNames, cancelQueue = queue)

    private fun logos() = LogoRepositoryImpl(remote = remoteLogos, cancelQueue = queue)

    @Test
    fun `given airplane mode when a name is taken down then only the intent is queued`() = runTest {
        names().cancelListing("Old Name")

        val record = outbox.all.single()
        assertEquals("profile.CANCEL_NICKNAME_LISTING", record.operation)
        assertEquals("profile.NICKNAME_LISTING:Old Name", record.entityRef)
        assertEquals("""{"nickname":"Old Name"}""", record.payload)
        assertEquals(OutboxState.WAITING, record.state)
        assertEquals("uid-1", record.ownerUid)
        assertNull(record.expectedVersion, "лот не версионируется")
        assertEquals(0, remoteNames.calls, "callable не зовётся: сервера в авиарежиме нет, и он не нужен")
    }

    @Test
    fun `given airplane mode when a logo is taken down then only the intent is queued`() = runTest {
        logos().cancelListing("phoenix")

        val record = outbox.all.single()
        assertEquals("profile.CANCEL_LOGO_LISTING", record.operation)
        assertEquals("profile.LOGO_LISTING:phoenix", record.entityRef)
        assertEquals("""{"logo":"phoenix"}""", record.payload)
        assertEquals(OutboxState.WAITING, record.state)
        assertEquals(0, remoteLogos.calls)
    }

    @Test
    fun `given the queued record then the row sees it as queued`() = runTest {
        names().cancelListing("Old Name")

        assertEquals(ListingCancelSync.QUEUED, names().observeCancelSync("Old Name").first())
        assertNull(names().observeCancelSync("Other").first(), "чужой лот очередь не касается")
    }

    @Test
    fun `given the lot was bought meanwhile and the record is quarantined then the row reads already sold`() =
        runTest {
            logos().cancelListing("phoenix")
            val record = outbox.all.single()

            // Движок получил окончательный отказ (история 5.1) и увёл запись в карантин (AD-22).
            outbox.setState(record.id, OutboxState.QUARANTINED, lastError = "Listing not found")

            assertEquals(ListingCancelSync.REFUSED, logos().observeCancelSync("phoenix").first())
            assertEquals(1, outbox.observeCounts("uid-1").first().quarantined, "счётчик карантинных вырос")
            // Откатывать нечего: у снятия нет локальной половины (AD-25).
            ListingCancelQuarantine().onQuarantined(record.copy(state = OutboxState.QUARANTINED))
            assertEquals(1, outbox.all.size)
        }

    @Test
    fun `given the record is still queued when the lot is taken down again then no second record appears`() =
        runTest {
            names().cancelListing("Old Name")
            now += 10
            names().cancelListing("Old Name")

            assertEquals(1, outbox.all.size, "то же намерение, а не второе (AD-2)")
        }

    @Test
    fun `given the name was relisted after a delivered removal then the new removal gets a new key`() = runTest {
        names().cancelListing("Old Name")
        val first = outbox.all.single()
        // Доехала и удалена (AD-4); имя выставлено снова; игрок снимает ещё раз.
        outbox.remove(first.id)
        now += 60_000
        names().cancelListing("Old Name")
        val second = outbox.all.single()

        assertNotEquals(first.mutationId, second.mutationId, "иначе сервер вернул бы сохранённый ответ первого снятия")
        assertEquals(first.entityRef, second.entityRef)
    }

    @Test
    fun `given a refused removal when the lot is taken down again then the refusal is withdrawn with the new intent`() =
        runTest {
            names().cancelListing("Old Name")
            outbox.setState(outbox.all.single().id, OutboxState.QUARANTINED)
            now += 10

            names().cancelListing("Old Name")

            val row = outbox.all.single()
            assertEquals(OutboxState.WAITING, row.state, "карантинная запись снята, а не оставлена рядом (AD-28)")
            assertEquals(ListingCancelSync.QUEUED, names().observeCancelSync("Old Name").first())
            assertEquals(0, outbox.observeCounts("uid-1").first().quarantined, "счётчик карантинных вернулся к нулю")
        }

    @Test
    fun `given a refused removal and a delivered newer one then the old refusal does not come back`() = runTest {
        names().cancelListing("Old Name")
        outbox.setState(outbox.all.single().id, OutboxState.QUARANTINED)
        now += 10
        names().cancelListing("Old Name")

        // Новое снятие доехало и удалено (AD-4). Пока карантинная запись оставалась, самой свежей
        // по ссылке снова становилась она — и строка показывала «уже продан» на живом лоте.
        outbox.remove(outbox.all.single().id)

        assertNull(names().observeCancelSync("Old Name").first())
    }

    @Test
    fun `given a refused removal when the name is listed again then the refusal is forgotten`() = runTest {
        names().cancelListing("Old Name")
        outbox.setState(outbox.all.single().id, OutboxState.QUARANTINED, lastError = "Listing not found")

        names().listForSale("Old Name", price = 10)

        assertEquals(1, remoteNames.listed, "выставление — синхронный вызов, он ушёл")
        assertTrue(outbox.all.isEmpty(), "отказ снятия говорил «лота нет»; выставленный заново лот есть")
        assertNull(names().observeCancelSync("Old Name").first())
    }

    @Test
    fun `given a pending removal when the logo is listed again then the pending removal stays`() = runTest {
        logos().cancelListing("phoenix")

        logos().listForSale("phoenix", price = 10)

        assertEquals(ListingCancelSync.QUEUED, logos().observeCancelSync("phoenix").first(), "живое намерение не трогается")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `given two taps racing on the same lot then only one record is queued`() = runTest {
        outbox.yieldBeforeFind = true

        launch { names().cancelListing("Old Name") }
        launch { names().cancelListing("Old Name") }
        advanceUntilIdle()

        assertEquals(1, outbox.all.size, "два быстрых нажатия — одна запись, не две едущие")
    }

    @Test
    fun `given a key the server would not accept then the mutation id is still well formed`() = runTest {
        names().cancelListing("Имя с \"кавычками\" и пробелом")

        val record = outbox.all.single()
        assertTrue(Regex("[A-Za-z0-9_-]+").matches(record.mutationId), record.mutationId)
        assertTrue(record.mutationId.startsWith(OutboxOperations.CANCEL_NICKNAME_LISTING.replace('.', '-')))
        assertEquals("""{"nickname":"Имя с \"кавычками\" и пробелом"}""", record.payload)
    }

    @Test
    fun `given no signed-in account then nothing is queued and the caller hears about it`() = runTest {
        uid.value = null

        assertFails { names().cancelListing("Old Name") }

        assertTrue(outbox.all.isEmpty())
        assertNull(names().observeCancelSync("Old Name").first())
    }

    /**
     * Умеет считать вызовы: снятия среди них быть не должно, и метода для него больше нет.
     * Выставление — единственное, что здесь отвечает: оно синхронное и живёт рядом со снятием.
     */
    private class FakeNicknameRemote : NicknameRemoteDataSource {
        var calls = 0
        var listed = 0

        override suspend fun checkAvailability(nickname: String): NicknameAvailability = unused()

        override suspend fun owned(): List<OwnedNickname> = unused()

        override suspend fun setActive(nickname: String) = unused()

        override suspend fun claim(nickname: String): Long = unused()

        override suspend fun listings(limit: Int): List<NicknameListing> = unused()

        override suspend fun listForSale(nickname: String, price: Long) {
            listed++
        }

        override suspend fun buy(nickname: String): Long = unused()

        private fun unused(): Nothing {
            calls++
            error("сервер в этом тесте не зовётся")
        }
    }

    private class FakeLogoRemote : LogoRemoteDataSource {
        var calls = 0

        override suspend fun catalog(): List<ProfileLogo> = unused()

        override suspend fun buy(logo: String): Long = unused()

        override suspend fun wear(logo: String): String = unused()

        override suspend fun listings(limit: Int): List<LogoListing> = unused()

        override suspend fun listForSale(logo: String, price: Long) = Unit

        override suspend fun buyListed(logo: String): Long = unused()

        private fun unused(): Nothing {
            calls++
            error("сервер в этом тесте не зовётся")
        }
    }
}

