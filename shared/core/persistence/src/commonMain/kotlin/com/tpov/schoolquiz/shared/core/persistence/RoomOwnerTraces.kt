package com.tpov.schoolquiz.shared.core.persistence

import com.tpov.schoolquiz.shared.core.outbox.OwnerDataKind
import com.tpov.schoolquiz.shared.core.outbox.OwnerForgetReason
import com.tpov.schoolquiz.shared.core.outbox.OwnerTraces

/**
 * Личные следы прежнего владельца вне очереди — на Room (AD-8).
 *
 * Две половины одной операции, и обе обязательны. Ключ личного курсора (`private_quests:{uid}`)
 * складывает фича, которая им пользуется, и этот слой его формы не знает. Локальные строки
 * владельца перечислены в [OwnerScopedTables] и режутся [OwnerRowsDao] — пока снимался только
 * курсор, черновики прежнего владельца оставались на устройстве навсегда, а читающий проход шёл
 * по ним и требовал у сервера чужие документы, получая отказ правила на каждом прогоне.
 *
 * Курсоры общего контента в перечень не попадают, потому что контент от аккаунта не зависит.
 *
 * Снятие курсора — удаление строки, а не запись нуля: `setCursor` монотонен, и ноль пережил бы смысл.
 */
class RoomOwnerTraces(
    private val ownerRows: OwnerRowsDao,
    private val privateCursorIds: (uid: String) -> List<String>,
) : OwnerTraces {
    /** Перечень имён закрыт и лежит рядом с перечнем таблиц: два списка разойтись не могут. */
    override val namedLosses: List<OwnerDataKind> = OwnerScopedTables.NAMED_TO_PLAYER

    override suspend fun forget(uid: String) {
        forget(uid, OwnerForgetReason.ERASE_LOCAL_DATA)
    }

    override suspend fun forget(
        uid: String,
        reason: OwnerForgetReason,
    ) {
        if (uid.isBlank()) return
        ownerRows.forgetOwner(uid, privateCursorIds(uid), reason)
    }

    /**
     * Кто оставил личные строки — по всему перечню, а не по очереди.
     *
     * Пустая очередь не значит «чисто»: запись уехала и исчезла, а черновик остался лежать. Пока
     * этот вопрос задавался одной очереди, прежний владелец жил на устройстве вечно.
     */
    override suspend fun ownersWithTraces(): List<String> =
        ownerRows.ownersWithTraces().filter { it.isNotBlank() }.distinct()

    /**
     * Что этот владелец действительно потеряет.
     *
     * `when` исчерпывающий намеренно: новый вид в [OwnerDataKind] обязан получить свой признак в
     * запросе, иначе он молча выпал бы из вопроса — а вопрос и есть согласие.
     */
    override suspend fun lossesOf(uid: String): List<OwnerDataKind> {
        if (uid.isBlank()) return emptyList()
        val row = ownerRows.namedTraces(uid)
        return OwnerScopedTables.NAMED_TO_PLAYER.filter { kind ->
            when (kind) {
                OwnerDataKind.ANSWER_HISTORY -> row.hasAnswerHistory
                OwnerDataKind.REPETITION_SCHEDULE -> row.hasRepetitionSchedule
                OwnerDataKind.UNPUBLISHED_QUEST_DRAFTS -> row.hasUnpublishedDrafts
            }
        }
    }
}
