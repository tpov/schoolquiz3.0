package com.tpov.schoolquiz.shared.feature.economy.domain.use_case

import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.feature.economy.domain.logic.GiftBoxAccrual
import com.tpov.schoolquiz.shared.feature.economy.domain.logic.GiftBoxStreak
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimException
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimFailureReason
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimStatus
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftBoxLocalStreakStore
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftClaimRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftClaimRequest
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.LocalBoxStreak
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Чем кончился входной проход: два возможных исхода и два честных «не случилось». */
data class GiftStreakSyncReport(
    /** Исход офлайн-заявки; `null` — заявлять было нечего, офлайн или не решилось. */
    val offlineClaim: GiftClaimOutcome?,
    /** Исход визита; `null` — не посылался (офлайн, блокировка офлайн-заявкой) или не решилось. */
    val visit: GiftClaimOutcome?,
    /** Связи не было: визит посчитан местным счётчиком и ляжет в заявку при возвращении. */
    val offline: Boolean,
    /** Свежее слово сервера легло в профиль и пересеяло местный счёт. */
    val profileRefreshed: Boolean,
)

/**
 * Входная синхронизация серии — «подряд значит подряд» без потери честных дней.
 *
 * Порядок здесь и есть вся защита: сначала уезжает офлайн-заявка, продлевающая якорь на
 * заявленные дни, и только потом VISIT — иначе перечитанный профиль плюс визит съели бы
 * офлайн-дни как «пропущенный» разрыв, и серия оборвалась бы на сервере при живом локальном
 * счёте. Если офлайн-заявка не получила решения (обрыв, таймаут), визит не посылается вовсе:
 * намерение лежит на устройстве, и следующий вход уедет им же раньше любого визита. Сам VISIT
 * идемпотентен в своих сутках — повтор отвечает NO_CHANGE и не трогает якорь.
 *
 * Часы устройства кормят только местный счётчик — и консервативно: назад счёт не идёт, вперёд не
 * насчитывает дней больше, чем было визитов. Заявка ещё раз зажимается потолками сервера
 * ([GiftBoxAccrual]) до отправки, так что испорченные часы не превращаются в перерасход, а
 * заявка вниз (сброс серии) не отправляется никогда — серию рвёт только сервер по своим часам.
 */
class SyncBoxStreakUseCase(
    private val claims: GiftClaimRepository,
    private val profileRepository: ProfileRepository,
    private val localStreak: GiftBoxLocalStreakStore,
    private val network: NetworkMonitor,
    private val currentUidFlow: () -> Flow<String?>,
    /** Серверное слово и местный счёт живут в миллисекундах; часы ставит сборка (DI). */
    private val clock: () -> Long,
) {
    suspend operator fun invoke(): Result<GiftStreakSyncReport> =
        try {
            Result.success(sync())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }

    private suspend fun sync(): GiftStreakSyncReport {
        val uid =
            currentUidFlow().first()?.takeIf { it.isNotBlank() }
                ?: throw GiftClaimException(GiftClaimFailureReason.NO_ACCOUNT)
        // Незавершённые намерения прежде всего: смерть процесса между отправкой и ответом
        // оставила тело лежать, и повтор уезжает дословно, пока местный счёт не поехал дальше.
        val resumedOffline = claims.resumePending(GiftClaimKind.OFFLINE_STREAK)?.getOrNull()
        val resumedVisit = claims.resumePending(GiftClaimKind.VISIT)?.getOrNull()

        val now = clock()
        // Читается после возобновления: исходы выше могли перечитать профиль сами.
        val profile = profileRepository.currentProfile()
        val base = GiftBoxStreak(profile.boxCount, profile.boxStreakDays, profile.nextBoxAtMs)
        // Чужая база — сервер уже сказал новое слово (второе устройство, своя заявка): местный
        // счёт пересеивается, а не складывается с чужим.
        val local = localStreak.find(uid)?.takeIf { it.base == base } ?: LocalBoxStreak(base, base, now)
        val counted = local.copy(counted = local.counted.visited(now))
        localStreak.save(uid, counted)

        if (!network.isOnline()) {
            // Офлайн визит посчитан локально и ляжет в заявку при возвращении связи.
            return GiftStreakSyncReport(resumedOffline, resumedVisit, offline = true, profileRefreshed = false)
        }

        val claim = GiftBoxAccrual.claim(base, counted.counted, now)
        val owed = claim.boxStreakDays > base.streakDays || claim.boxesEarned > 0
        var offlineClaim: GiftClaimOutcome? = null
        var claimUndecided = false
        if (owed) {
            val result = claims.submit(GiftClaimRequest(GiftClaimKind.OFFLINE_STREAK, base, claim))
            offlineClaim = result.getOrNull()
            if (offlineClaim == null) {
                // Решения нет: визит поверх несосчитанного якоря выглядел бы для сервера
                // пропущенными сутками и оборвал серию. Намерение лежит — повтор на входе.
                claimUndecided = true
            } else if (offlineClaim.status == GiftClaimStatus.STALE_BASE) {
                // Слово сервера выиграло: профиль перечитан репозиторием, счёт пересеивается
                // ниже, заявка больше не повторяется.
            }
        }

        val visit = if (claimUndecided) null else claims.submit(GiftClaimRequest(GiftClaimKind.VISIT)).getOrNull()
        val refreshed = reseedFromFreshProfile(uid)

        return GiftStreakSyncReport(
            offlineClaim = offlineClaim ?: resumedOffline,
            visit = visit ?: resumedVisit,
            offline = false,
            profileRefreshed = refreshed,
        )
    }

    /** Свежее слово сервера становится новой базой местного счёта — накопленное сверено. */
    private suspend fun reseedFromFreshProfile(uid: String): Boolean {
        val refreshed = profileRepository.ensureCurrentProfile().getOrNull() ?: return false
        val word = GiftBoxStreak(refreshed.boxCount, refreshed.boxStreakDays, refreshed.nextBoxAtMs)
        localStreak.save(uid, LocalBoxStreak(word, word, clock()))
        return true
    }
}
