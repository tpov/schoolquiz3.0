package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.feature.economy.data.remote.EconomyConstantsRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.remote.EconomyConstantsResponse
import com.tpov.schoolquiz.shared.feature.economy.domain.logic.PriceChange
import com.tpov.schoolquiz.shared.feature.economy.domain.logic.TermsConsentVerdict
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ActivityKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyConstants
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PayableTerms
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyConstantsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Гейт согласия против живого репозитория таблицы: синхронизация пишет таблицу, игрок пишет
 * согласие, и отказ синхронизации не имеет права звучать как «условия изменились» (CAP-16).
 */
class EconomyTermsConsentGateImplTest {

    private class ConsentStore(private var held: PayableTerms? = null) : EconomyTermsConsentStore {
        var writes = 0
            private set

        override fun read(): PayableTerms? = held

        override fun write(terms: PayableTerms) {
            held = terms
            writes++
        }
    }

    private class Remote(
        private val answer: (Long) -> EconomyConstantsResponse,
    ) : EconomyConstantsRemoteDataSource {
        override suspend fun fetch(knownVersion: Long): EconomyConstantsResponse = answer(knownVersion)
    }

    /** Таблица с другим прейскурантом: то, что приехало бы при изменившихся платных условиях. */
    private val dearer =
        EconomyConstants.BOOTSTRAP.copy(
            version = 2L,
            activityPrices = EconomyConstants.BOOTSTRAP_PRICES + (ActivityKind.TOURNAMENT to 900),
        )

    private fun constantsRepo(remote: EconomyConstantsRemoteDataSource): EconomyConstantsRepository =
        EconomyConstantsRepositoryImpl(remote = remote, store = MemoryConstantsStore())

    @Test
    fun `given the player never consented then the verdict demands the first consent`() {
        val remote = Remote { EconomyConstantsResponse.Unchanged }
        val gate = EconomyTermsConsentGateImpl(constantsRepo(remote), ConsentStore())

        val required = assertIs<TermsConsentVerdict.Required>(gate.verdict())
        assertNull(required.change, "первого согласия не было — называть изменение нечем")
        assertEquals(PayableTerms.BOOTSTRAP, required.to)
    }

    @Test
    fun `given the player accepts then the current terms are captured and the verdict turns sufficient`() {
        val store = ConsentStore()
        val gate = EconomyTermsConsentGateImpl(constantsRepo(Remote { EconomyConstantsResponse.Unchanged }), store)

        gate.acceptCurrentTerms()

        assertEquals(1, store.writes)
        assertEquals(PayableTerms.BOOTSTRAP, store.read(), "согласие захватило снимок платных условий")
        assertEquals(TermsConsentVerdict.Sufficient, gate.verdict())
    }

    @Test
    fun `given the player consented then a table with new prices demands fresh consent naming the change`() =
        runTest {
            val store = ConsentStore()
            val repo =
                EconomyConstantsRepositoryImpl(
                    remote = Remote { EconomyConstantsResponse.Table(dearer) },
                    store = MemoryConstantsStore(),
                )
            val gate = EconomyTermsConsentGateImpl(constants = repo, store = store)
            gate.acceptCurrentTerms()
            assertEquals(TermsConsentVerdict.Sufficient, gate.verdict())

            repo.refresh()

            val required = assertIs<TermsConsentVerdict.Required>(gate.verdict())
            val change = requireNotNull(required.change)
            assertEquals(
                mapOf(ActivityKind.TOURNAMENT to PriceChange(from = 500, to = 900)),
                change.priceChanges,
            )
            assertEquals(PayableTerms.BOOTSTRAP, change.from)
            assertEquals(PayableTerms.of(dearer), change.to)
        }

    @Test
    fun `given the player consented then a failed refresh keeps the consent sufficient`() = runTest {
        // Пара половин CAP-16: отказ синхронизации оставил таблице прежний снимок — значит, гейт
        // не имеет права трактовать обрыв связи как «условия изменились» и запирать игрока.
        val store = ConsentStore()
        val repo =
            EconomyConstantsRepositoryImpl(
                remote = Remote { throw IllegalStateException("сети нет") },
                store = MemoryConstantsStore(),
            )
        val gate = EconomyTermsConsentGateImpl(constants = repo, store = store)
        gate.acceptCurrentTerms()

        assertTrue(repo.refresh().isFailure)

        assertEquals(TermsConsentVerdict.Sufficient, gate.verdict())
    }

    @Test
    fun `given the player re-consents over a failed refresh then the same known terms are captured`() =
        runTest {
            // Пересогласие после обрыва фиксирует ровно то, что и было: известный снимок, а не
            // нули и не полтаблицы.
            val store = ConsentStore()
            val repo =
                EconomyConstantsRepositoryImpl(
                    remote = Remote { throw IllegalStateException("сети нет") },
                    store = MemoryConstantsStore(),
                )
            val gate = EconomyTermsConsentGateImpl(constants = repo, store = store)
            gate.acceptCurrentTerms()
            val capturedBefore = requireNotNull(gate.capturedTerms())

            repo.refresh()
            gate.acceptCurrentTerms()

            assertEquals(capturedBefore, gate.capturedTerms())
        }

    @Test
    fun `given a discarded consent record then the verdict demands consent again`() {
        // Испорченная запись отброшена хранилищем целиком: безопасное направление — спросить заново,
        // а не вести платную операцию по непонятно каким условиям.
        val remote = Remote { EconomyConstantsResponse.Unchanged }
        val gate = EconomyTermsConsentGateImpl(constantsRepo(remote), ConsentStore())

        assertIs<TermsConsentVerdict.Required>(gate.verdict())
    }
}

/** Память вместо платформенного хранилища таблицы: гейт проверяет согласие, а не хранилище. */
private class MemoryConstantsStore : EconomyConstantsStore {
    private var held: EconomyConstants? = null

    override fun read(): EconomyConstants? = held

    override fun write(constants: EconomyConstants) {
        held = constants
    }
}
