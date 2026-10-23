package com.tpov.schoolquiz.shared.feature.lesson_runner.data.di

import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaCancelledSettlementProof
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaCancelledSettlementResolver
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaEntryLocalMapper
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaJournalCheckpointCodec
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaJournalCodec
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaJournalStorage
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaOwnerGate
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaRunnerCheckpointCodec
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaTicketJournal
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.OutboxArenaOwnerGate
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.RoomArenaJournalStorage
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketWireParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.ArenaCatalogRepositoryImpl
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.ArenaEntryRepositoryImpl
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.PrivateQuizRunRepositoryImpl
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.PrivateQuizRunRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.ArenaFrozenRatingDependencies
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.ArenaFrozenRatingRepositoryImpl
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.ArenaRunRepositoryImpl
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.ArenaWalletRepositoryImpl
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.OutboxArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaCatalogRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaEntryRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaFrozenRatingRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaWalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import org.koin.dsl.module
import java.security.MessageDigest

/** Required strict checkpoint binding, kept separate so its absence can be tested fail-closed. */
val arenaCheckpointCodecModule =
    module {
        single { ArenaRunnerCheckpointCodec() }
        single<ArenaJournalCheckpointCodec> { get<ArenaRunnerCheckpointCodec>() }
    }

/** Required strict public parser/proof. Kept separate to test their absence, not to provide a fallback. */
val arenaEntryProofModule =
    module {
        single { ArenaTicketWireParser(sha256 = ::journalSha256) }
        single { ArenaCancelledSettlementProof(codec = get(), sha256 = ::journalSha256) }
    }

/** Lazy wiring only: resolution never starts a purchase, recovery pass, launch or settlement. */
val arenaDataModule =
    module {
        includes(arenaCheckpointCodecModule, arenaEntryProofModule)
        single<ArenaCatalogRepository> { ArenaCatalogRepositoryImpl(remote = get()) }
        single<ArenaSessionProvider> { OutboxArenaSessionProvider(sessions = get()) }
        single<ArenaRunRepository> {
            ArenaRunRepositoryImpl(
                journal = get(),
                checkpoints = get<ArenaRunnerCheckpointCodec>(),
                sessions = get(),
                dispatcher = Dispatchers.IO,
                clock = get<Clock>(),
            )
        }
        single<ArenaFrozenRatingRepository> {
            ArenaFrozenRatingRepositoryImpl(
                ArenaFrozenRatingDependencies(
                    dao = get(),
                    receipts = get(),
                    settlements = get(),
                    sessions = get(),
                    gate = get(),
                    ids = get(),
                    clock = get(),
                    codec = get(),
                ),
            )
        }
        single<ArenaWalletRepository> {
            ArenaWalletRepositoryImpl(remote = get(), sessions = get(), dispatcher = Dispatchers.IO)
        }
        single<ArenaJournalStorage> { RoomArenaJournalStorage(dao = get()) }
        single<ArenaOwnerGate> { OutboxArenaOwnerGate(sessions = get(), engine = get()) }
        single { ArenaJournalCodec(sha256 = ::journalSha256) }
        single {
            val clock = get<Clock>()
            ArenaTicketJournal(
                storage = get(),
                remote = get(),
                gate = get(),
                network = get(),
                codec = get(),
                checkpoints = get<ArenaJournalCheckpointCodec>(),
                nowMs = { clock.now().toEpochMilliseconds() },
                dispatcher = Dispatchers.IO,
            )
        }
        single {
            val clock = get<Clock>()
            ArenaCancelledSettlementResolver(
                storage = get(),
                remote = get(),
                gate = get(),
                proof = get(),
                dispatcher = Dispatchers.IO,
                nowMs = { clock.now().toEpochMilliseconds() },
            )
        }
        single { ArenaEntryLocalMapper(codec = get(), parser = get()) }
        single<ArenaEntryRepository> {
            ArenaEntryRepositoryImpl(
                storage = get(),
                journal = get(),
                resolver = get(),
                mapper = get(),
                gate = get(),
                sessions = get(),
                dispatcher = Dispatchers.IO,
            )
        }
        // Приватный квиз (AD-29, история 8.4): дверь + замороженный прогон + свой результат.
        // Подача едет общей очередью (AD-6/AD-7), поэтому репозиторию нужен OutboxStore.
        single<PrivateQuizRunRepository> {
            val clock = get<Clock>()
            PrivateQuizRunRepositoryImpl(
                remote = get(),
                outbox = get(),
                nowMs = { clock.now().toEpochMilliseconds() },
            )
        }
    }

@Suppress("MagicNumber") // Unsigned-byte mask and hexadecimal radix, not economy constants.
private fun journalSha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }
