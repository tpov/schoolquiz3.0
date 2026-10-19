package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.feature.economy.data.remote.GiftClaimRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimException
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimFailureReason
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimStatus
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftClaimRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftClaimRequest
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Транспорт заявок подарков: дисциплина очереди против прямого вызываемого.
 *
 * Порядок неизменен: намерение (operationId + тело) пишется в базу ДО запроса; ответ сервера —
 * типизированный исход, и любой исход разрешает намерение; отказ транспорта, нечитаемый конверт
 * или чужая операция в ответе намерение сохраняют — повтор после смерти процесса уезжает тем же
 * телом, и сервер возвращает сохранённый результат. STALE_BASE и CLAIMED перечитывают профиль:
 * первый — потому что слово сервера выиграло, второй — чтобы коробка на экране приехала сразу.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GiftClaimRepositoryImpl(
    private val remote: GiftClaimRemoteDataSource,
    private val profileRepository: ProfileRepository,
    private val intents: GiftClaimIntentStore,
    private val network: NetworkMonitor,
    private val currentUidFlow: () -> Flow<String?>,
    private val newOperationId: () -> String,
    private val clock: () -> Long,
) : GiftClaimRepository {
    override suspend fun submit(request: GiftClaimRequest): Result<GiftClaimOutcome> =
        capture {
            val uid = currentUid() ?: fail(GiftClaimFailureReason.NO_ACCOUNT, request.kind)
            if (!network.isOnline()) fail(GiftClaimFailureReason.OFFLINE, request.kind)
            val intent =
                intents.active(uid, request.kind) ?: run {
                    val operationId = newOperationId()
                    intents.reserve(uid, request.kind, operationId, payloadJson(uid, request, operationId), clock())
                }
            resolve(intent)
        }

    override suspend fun resumePending(kind: GiftClaimKind): Result<GiftClaimOutcome>? {
        val uid = currentUidFlow().first() ?: return null
        val intent = intents.active(uid, kind) ?: return null
        return capture { resolve(intent) }
    }

    override fun observePending(kind: GiftClaimKind): Flow<Boolean> =
        currentUidFlow().distinctUntilChanged().flatMapLatest { uid ->
            if (uid.isNullOrBlank()) {
                flowOf(false)
            } else {
                intents.observeActive(uid, kind).map { it != null }
            }
        }.distinctUntilChanged()

    /**
     * Отправляет сохранённое тело и разрешает намерение.
     *
     * Возвращается только исход с чужой сессией: подарок уйдёт по fence прежнему владельцу, а
     * перечитывать под новым профиль в том же проходе нельзя — счёт на экране обязан приехать от
     * его собственной синхронизации.
     */
    private suspend fun resolve(intent: GiftClaimIntent): GiftClaimOutcome {
        val outcome =
            try {
                remote.submitGiftBoxOperation(intent.payloadJson)
            } catch (error: CancellationException) {
                throw error
            } catch (error: GiftClaimException) {
                throw error
            } catch (error: Exception) {
                throw GiftClaimException(
                    GiftClaimFailureReason.TRANSPORT,
                    intent.kind,
                    intent.operationId,
                    intent.ownerUid,
                    error,
                )
            }
        if (currentUidFlow().first() != intent.ownerUid) {
            fail(GiftClaimFailureReason.NO_ACCOUNT, intent.kind, intent.operationId, intent.ownerUid)
        }
        // Любой статус сервера — терминальный: повтор вернул бы то же слово по тому же ключу.
        intents.resolve(intent)
        when (outcome.status) {
            // Слово сервера выиграло: местная заявка брошена, профиль перечитывается.
            GiftClaimStatus.STALE_BASE -> profileRepository.ensureCurrentProfile()
            // Коробка уже начислена — пусть счёт на экране поедет в этом же проходе.
            GiftClaimStatus.CLAIMED -> profileRepository.ensureCurrentProfile()
            GiftClaimStatus.NO_CHANGE, GiftClaimStatus.PREMIUM_REQUIRED, GiftClaimStatus.DAILY_LIMIT -> Unit
        }
        return outcome
    }

    /** Тело вызываемого: тройка идентичности, у OFFLINE_STREAK — ещё слово сервера и заявка. */
    private fun payloadJson(
        ownerUid: String,
        request: GiftClaimRequest,
        operationId: String,
    ): String =
        buildJsonObject {
            put("kind", request.kind.name)
            put("ownerUid", ownerUid)
            put("operationId", operationId)
            if (request.kind == GiftClaimKind.OFFLINE_STREAK) {
                put(
                    "base",
                    buildJsonObject {
                        put("boxStreakDays", checkNotNull(request.base).streakDays)
                        put("nextBoxAtMs", checkNotNull(request.base).nextBoxAtMs)
                    },
                )
                put(
                    "claimed",
                    buildJsonObject {
                        put("boxStreakDays", checkNotNull(request.claim).boxStreakDays)
                        put("boxesEarned", checkNotNull(request.claim).boxesEarned)
                    },
                )
            }
        }.toString()

    private suspend fun currentUid(): String? = currentUidFlow().first()?.takeIf { it.isNotBlank() }

    private fun fail(
        reason: GiftClaimFailureReason,
        kind: GiftClaimKind?,
        operationId: String? = null,
        ownerUid: String? = null,
    ): Nothing = throw GiftClaimException(reason, kind, operationId, ownerUid)
}

private suspend fun <T> capture(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
