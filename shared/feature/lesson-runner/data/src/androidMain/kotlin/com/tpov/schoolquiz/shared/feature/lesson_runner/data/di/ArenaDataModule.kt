package com.tpov.schoolquiz.shared.feature.lesson_runner.data.di

import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaJournalCheckpointCodec
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaJournalCodec
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaJournalStorage
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaOwnerGate
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaRunnerCheckpointCodec
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaTicketJournal
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.OutboxArenaOwnerGate
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.RoomArenaJournalStorage
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

/** Lazy wiring only: resolution never starts a purchase, recovery pass, launch or settlement. */
val arenaDataModule =
    module {
        includes(arenaCheckpointCodecModule)
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
    }

@Suppress("MagicNumber") // Unsigned-byte mask and hexadecimal radix, not economy constants.
private fun journalSha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }
