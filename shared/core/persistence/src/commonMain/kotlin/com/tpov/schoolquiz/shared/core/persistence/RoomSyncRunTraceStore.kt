package com.tpov.schoolquiz.shared.core.persistence

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.sync.SyncRunKind
import com.tpov.schoolquiz.shared.core.sync.SyncRunTrace
import com.tpov.schoolquiz.shared.core.sync.SyncRunTraceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * След прогона на Room (истории 4.2 и 4.5).
 *
 * Единственная причина, по которой он лежит в базе: процесс приложения умирает чаще, чем проходит
 * синхронизация. Пока след жил в памяти, каждый холодный старт говорил игроку «синхронизации ещё
 * не было» — то же молчание, что и до AD-14, только с датой.
 *
 * Отсутствующая строка читается как нули, а не как пустой поток (инвариант 8, NFR8): у владельца
 * без следа и у гостя ответ один и тот же — «прогона не было», и он приходит значением. То же и с
 * видом прогона, которого в таблице нет: он читается нулями, а не отсутствием ключа.
 */
class RoomSyncRunTraceStore(
    private val dao: SyncRunTraceDao,
) : SyncRunTraceStore {
    override fun observeTraces(ownerUid: String): Flow<Map<SyncRunKind, SyncRunTrace>> =
        dao.observe(ownerUid).map { rows ->
            rows.mapNotNull { row -> row.kindOrNull()?.let { it to row.toTrace() } }.toMap()
        }

    override suspend fun recordSuccess(
        ownerUid: String,
        kind: SyncRunKind,
        atMs: Long,
    ) {
        if (ownerUid.isBlank()) return
        dao.recordSuccess(ownerUid, kind.column(), atMs)
    }

    override suspend fun recordFailure(
        ownerUid: String,
        kind: SyncRunKind,
        error: SyncError,
    ) {
        if (ownerUid.isBlank()) return
        dao.recordFailure(
            ownerUid = ownerUid,
            kind = kind.column(),
            branch = error.branchName(),
            reason = (error as? SyncError.Refused)?.reason,
            serverVersion = (error as? SyncError.VersionConflict)?.serverVersion,
        )
    }
}

/**
 * Имена видов прогона в схеме.
 *
 * Имена свои, а не `name` перечисления: переименование или перестановка ветви в коде не должна
 * менять то, что уже записано на устройстве, — иначе след контентного прогона после безобидного
 * рефакторинга станет следом неизвестного вида и пропадёт с экрана.
 */
internal object SyncRunKindColumn {
    const val CONTENT: String = "CONTENT"
    const val PROFILE: String = "PROFILE"
    const val RESYNC: String = "RESYNC"
}

/** Вид именем — исчерпывающим `when`, поэтому четвёртое расписание не соберётся молча. */
internal fun SyncRunKind.column(): String =
    when (this) {
        SyncRunKind.CONTENT -> SyncRunKindColumn.CONTENT
        SyncRunKind.PROFILE -> SyncRunKindColumn.PROFILE
        SyncRunKind.RESYNC -> SyncRunKindColumn.RESYNC
    }

/**
 * Обратно в вид — по колонке.
 *
 * Строка с неизвестным именем вида написана версией приложения, которой здесь ещё не было: её
 * след пропускается целиком, а не приписывается наугад контентному прогону. Приписать — значило бы
 * показать игроку время чужого расписания как своё, то есть вернуть ровно тот дефект, ради
 * которого вид попал в ключ.
 */
private fun SyncRunTraceEntity.kindOrNull(): SyncRunKind? =
    when (kind) {
        SyncRunKindColumn.CONTENT -> SyncRunKind.CONTENT
        SyncRunKindColumn.PROFILE -> SyncRunKind.PROFILE
        SyncRunKindColumn.RESYNC -> SyncRunKind.RESYNC
        else -> null
    }

/**
 * Имена ветвей [SyncError] в схеме.
 *
 * Имена свои, а не `toString()` класса: перенос или переименование типа не должно менять то, что
 * уже записано на устройстве, — иначе прочитанное станет «неизвестной ветвью» после безобидного
 * рефакторинга.
 */
internal object SyncErrorBranch {
    const val NO_NETWORK = "NO_NETWORK"
    const val PRECONDITION_NOT_MET = "PRECONDITION_NOT_MET"
    const val REFUSED = "REFUSED"
    const val VERSION_CONFLICT = "VERSION_CONFLICT"
    const val TERMS_CONSENT_REQUIRED = "TERMS_CONSENT_REQUIRED"
    const val UNKNOWN = "UNKNOWN"
}

/**
 * Ветвь именем — исчерпывающим `when`, поэтому новая ветвь не соберётся, а не запишется как
 * `UNKNOWN` молча (AD-15).
 */
internal fun SyncError.branchName(): String =
    when (this) {
        SyncError.NoNetwork -> SyncErrorBranch.NO_NETWORK
        SyncError.PreconditionNotMet -> SyncErrorBranch.PRECONDITION_NOT_MET
        is SyncError.Refused -> SyncErrorBranch.REFUSED
        is SyncError.VersionConflict -> SyncErrorBranch.VERSION_CONFLICT
        SyncError.TermsConsentRequired -> SyncErrorBranch.TERMS_CONSENT_REQUIRED
        is SyncError.Unknown -> SyncErrorBranch.UNKNOWN
    }

/**
 * Обратно в тип — по колонке ветви, а не разбором текста (AD-15).
 *
 * Исходное исключение ветви `Unknown` не хранится: оно нужно журналу в момент падения, а на
 * устройстве от него остаётся только имя класса, которое AD-15 наружу и не пускает.
 */
private fun SyncRunTraceEntity.toTrace(): SyncRunTrace {
    val error =
        when (lastErrorBranch) {
            null -> null
            SyncErrorBranch.NO_NETWORK -> SyncError.NoNetwork
            SyncErrorBranch.PRECONDITION_NOT_MET -> SyncError.PreconditionNotMet
            SyncErrorBranch.REFUSED -> SyncError.Refused(lastErrorReason.orEmpty())
            SyncErrorBranch.VERSION_CONFLICT -> SyncError.VersionConflict(lastErrorServerVersion)
            SyncErrorBranch.TERMS_CONSENT_REQUIRED -> SyncError.TermsConsentRequired
            // Строка, записанная версией приложения, которой здесь ещё не было: ветвь неизвестна,
            // но неудача была — молчать о ней хуже, чем назвать её неизвестной.
            else -> SyncError.Unknown()
        }
    return SyncRunTrace(lastSuccessAtMs = lastSuccessAtMs, lastError = error)
}
