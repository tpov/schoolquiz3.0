package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.feature.economy.data.remote.EconomyConstantsRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.remote.EconomyConstantsResponse
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ActivityKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * Таблица настроек на устройстве: что показываем до первой синхронизации, что после, и что —
 * когда сервер недоступен.
 */
class EconomyConstantsRepositoryImplTest {

    private class Store(private var held: EconomyConstants? = null) : EconomyConstantsStore {
        var writes = 0
            private set

        override fun read(): EconomyConstants? = held

        override fun write(constants: EconomyConstants) {
            held = constants
            writes++
        }
    }

    private class Remote(
        private val answer: (Long) -> EconomyConstantsResponse,
    ) : EconomyConstantsRemoteDataSource {
        var askedWith: Long? = null
            private set

        override suspend fun fetch(knownVersion: Long): EconomyConstantsResponse {
            askedWith = knownVersion
            return answer(knownVersion)
        }
    }

    private val newer = EconomyConstants.BOOTSTRAP.copy(version = 5L, clockSkewToleranceMs = 1_000L)

    @Test
    fun `given nothing has ever arrived then the bootstrap copy is what the device runs on`() = runTest {
        // Устройство, которое ещё ни разу не синхронизировалось, обязано запуститься.
        val repo = EconomyConstantsRepositoryImpl(Remote { EconomyConstantsResponse.Unchanged }, Store())

        assertEquals(EconomyConstants.BOOTSTRAP, repo.observe().first())
    }

    @Test
    fun `given a table arrives then it is stored and observed`() = runTest {
        val store = Store()
        val repo = EconomyConstantsRepositoryImpl(Remote { EconomyConstantsResponse.Table(newer) }, store)

        assertTrue(repo.refresh().isSuccess)

        assertEquals(newer, repo.observe().first())
        assertEquals(newer, store.read(), "иначе таблица не переживёт перезапуск")
    }

    @Test
    fun `given the server says unchanged then nothing is written`() = runTest {
        // Таблица меняется редко, а синхронизация идёт часто: переписывать её каждый раз — работа
        // ради того же самого.
        val store = Store(newer)
        val repo = EconomyConstantsRepositoryImpl(Remote { EconomyConstantsResponse.Unchanged }, store)

        repo.refresh()

        assertEquals(0, store.writes)
        assertEquals(newer, repo.observe().first())
    }

    @Test
    fun `given a stored table then the server is asked with its version, not with zero`() = runTest {
        // Иначе сервер не может ответить «то же самое» и гоняет таблицу целиком каждый проход.
        val remote = Remote { EconomyConstantsResponse.Unchanged }
        EconomyConstantsRepositoryImpl(remote, Store(newer)).refresh()

        assertEquals(5L, remote.askedWith)
    }

    @Test
    fun `given the server is unreachable then the table already known stays in force`() = runTest {
        // Играть по прошлой таблице честнее, чем по нулям: нулевой потолок запер бы аккаунт.
        val repo =
            EconomyConstantsRepositoryImpl(
                Remote { throw IllegalStateException("сети нет") },
                Store(newer),
            )

        assertTrue(repo.refresh().isFailure)
        assertEquals(newer, repo.observe().first())
    }

    @Test
    fun `given the server is unreachable then not a single known price turns into zero`() = runTest {
        // CAP-16: отказ сохраняет известные значения. Ноль в прейскуранте — не «бесплатно», а
        // витрина, обещающая попытку даром, которой сервер потом не подтвердит.
        val dearer =
            EconomyConstants.BOOTSTRAP.copy(
                version = 2L,
                activityPrices = EconomyConstants.BOOTSTRAP_PRICES + (ActivityKind.TOURNAMENT to 900),
                standard = EconomyConstants.BOOTSTRAP.standard.copy(maxOwned = 7),
            )
        val repo =
            EconomyConstantsRepositoryImpl(
                Remote { throw IllegalStateException("сети нет") },
                Store(dearer),
            )

        assertTrue(repo.refresh().isFailure)

        val held = repo.observe().first()
        ActivityKind.entries.forEach { kind ->
            assertEquals(dearer.priceOf(kind), held.priceOf(kind), "цена $kind")
            assertTrue(held.priceOf(kind) >= 0)
        }
        assertEquals(7, held.standard.maxOwned)
    }

    @Test
    fun `given the server is unreachable on a device that never synced then the bootstrap pricelist stays in force`() =
        runTest {
            // Устройство, ни разу не синхронизировавшееся, при обрыве не остаётся без таблицы:
            // загрузочная копия — не нули.
            val repo =
                EconomyConstantsRepositoryImpl(
                    Remote { throw IllegalStateException("сети нет") },
                    Store(),
                )

            assertTrue(repo.refresh().isFailure)

            val held = repo.observe().first()
            assertEquals(EconomyConstants.BOOTSTRAP, held)
            assertTrue(ActivityKind.entries.all { held.priceOf(it) > 0 })
        }

    @Test
    fun `given the server is unreachable then the store is not touched by the failure`() = runTest {
        // Отказ — не приезд новой таблицы: перезаписывать хранилище нечем, и попытка «сохранить
        // отказ» стёрла бы последнюю хорошую копию.
        val store = Store(newer)
        val repo =
            EconomyConstantsRepositoryImpl(
                Remote { throw IllegalStateException("сети нет") },
                store,
            )

        assertTrue(repo.refresh().isFailure)

        assertEquals(0, store.writes)
        assertEquals(newer, store.read())
    }

    @Test
    fun `given the store rejects the new table then the running table is not swapped halfway`() =
        runTest {
            // Кэш принимает целый согласованный снимок (AD-5): упавшая запись оставляет работать
            // прежнюю таблицу целиком, а не её половину.
            val store = RejectingWriteStore(newer)
            val next = newer.copy(version = 6L)
            val repo =
                EconomyConstantsRepositoryImpl(Remote { EconomyConstantsResponse.Table(next) }, store)

            assertTrue(repo.refresh().isFailure)
            assertEquals(newer, repo.observe().first())
        }

    @Test
    fun `given cancellation while fetching then refresh rethrows instead of reporting a failure`() = runTest {
        // Отмена — не сетевой отказ: проглотив её в Result.failure, синхронизация продолжила бы
        // работу, которую у неё отозвали.
        val repo =
            EconomyConstantsRepositoryImpl(
                Remote { throw CancellationException("scope закрыт") },
                Store(newer),
            )

        assertFailsWith<CancellationException> { repo.refresh() }
        assertEquals(newer, repo.observe().first())
    }

    @Test
    fun `given a price changed on the server then the device prices by the new table`() = runTest {
        val dearer =
            EconomyConstants.BOOTSTRAP.copy(
                version = 2L,
                activityPrices = EconomyConstants.BOOTSTRAP_PRICES + (ActivityKind.TOURNAMENT to 900),
            )
        val repo = EconomyConstantsRepositoryImpl(Remote { EconomyConstantsResponse.Table(dearer) }, Store())

        repo.refresh()

        assertEquals(900, repo.observe().first().priceOf(ActivityKind.TOURNAMENT))
    }

    /** Хранилище, отказывающее в записи: эмуляция сбоя платформенного хранилища в момент приезда таблицы. */
    private class RejectingWriteStore(held: EconomyConstants) : EconomyConstantsStore {
        private var backing: EconomyConstants? = held
        var writeAttempts = 0
            private set

        override fun read(): EconomyConstants? = backing

        override fun write(constants: EconomyConstants) {
            writeAttempts++
            throw IllegalStateException("хранилище недоступно")
        }
    }
}
