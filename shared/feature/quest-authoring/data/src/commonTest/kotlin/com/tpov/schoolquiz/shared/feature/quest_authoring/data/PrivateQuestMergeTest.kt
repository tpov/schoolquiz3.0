package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Эпик 8: расхождение меряется серверными версиями, а не счётчиком правок автора (AD-12, AD-24).
 */
class PrivateQuestMergeTest {

    @Test
    fun `given the server moved past the known version then it is a conflict`() {
        assertEquals(
            PrivateQuestMerge.CONFLICT,
            privateQuestMerge(localStatus = "SAVED", knownServerRevision = 4L, incomingServerRevision = 5L),
        )
    }

    @Test
    fun `given ten local edits over the same server version then it is not a conflict`() {
        // Ровно тот ложный конфликт, ради которого история и написана: прежнее правило сравнивало
        // `localRevision` с серверной версией, и работающий автор объявлялся разошедшимся с
        // сервером, на котором никто ничего не трогал.
        assertEquals(
            PrivateQuestMerge.KEEP_LOCAL,
            privateQuestMerge(localStatus = "SAVED", knownServerRevision = 4L, incomingServerRevision = 4L),
        )
    }

    @Test
    fun `given a dirty draft and the same version then the snapshot does not overwrite it`() {
        // Приехало то же, что автор уже видел, а его несохранённая работа новее: класть снимок
        // поверх значило бы отнять её молча.
        assertEquals(
            PrivateQuestMerge.KEEP_LOCAL,
            privateQuestMerge(localStatus = "SYNC_PENDING", knownServerRevision = 4L, incomingServerRevision = 3L),
        )
    }

    @Test
    fun `given a draft that never saw a server version then nothing is declared a conflict`() {
        // Сверять не с чем: квест ещё не публиковался. Объявить расхождение здесь значит запереть
        // автора на пустом месте. Но и версию черновик не перенимает: несданные правки под
        // версией, пришедшей неизвестно откуда, — это чужая публикация с другого устройства, и
        // следующая заявка без номера получит от сервера конфликт с номером, который можно
        // разрешить честно. Переняв версию, она прошла бы сверку и затёрла чужое молча.
        assertEquals(
            PrivateQuestMerge.KEEP_LOCAL,
            privateQuestMerge(localStatus = "SAVED", knownServerRevision = null, incomingServerRevision = 9L),
        )
    }

    @Test
    fun `given a draft awaiting review then the version it just got is adopted`() {
        // Эхо собственной заявки: черновик отправлен, сервер опубликовал и назначил версию. Оставь
        // KEEP_LOCAL — и черновик не узнает её никогда: следующая заявка уйдёт без номера, сервер
        // прочтёт «сущности нет» и ответит конфликтом, которого никто не создавал.
        assertEquals(
            PrivateQuestMerge.ADOPT_SERVER_REVISION,
            privateQuestMerge(localStatus = "REVIEW_QUEUED", knownServerRevision = null, incomingServerRevision = 1L),
        )
        // И при повторной публикации тоже: своя заявка сдвинула версию дальше известной.
        assertEquals(
            PrivateQuestMerge.ADOPT_SERVER_REVISION,
            privateQuestMerge(localStatus = "REVIEW_QUEUED", knownServerRevision = 4L, incomingServerRevision = 5L),
        )
    }

    @Test
    fun `given a draft awaiting review but no server version then there is nothing to adopt`() {
        assertEquals(
            PrivateQuestMerge.KEEP_LOCAL,
            privateQuestMerge(localStatus = "REVIEW_QUEUED", knownServerRevision = null, incomingServerRevision = null),
        )
    }

    @Test
    fun `given the server never assigned a version then nothing is declared a conflict`() {
        // Пустая входящая версия — «сервер этому квесту версии не назначал». Сравнивать не с чем.
        // Прежде на её месте оказывался localRevision, число самого клиента, и сравнение клиента с
        // собой объявляло расхождение на первой же обычной публикации.
        assertEquals(
            PrivateQuestMerge.KEEP_LOCAL,
            privateQuestMerge(localStatus = "SAVED", knownServerRevision = 4L, incomingServerRevision = null),
        )
    }

    @Test
    fun `given a clean or missing draft then the snapshot is applied`() {
        assertEquals(
            PrivateQuestMerge.SAVE,
            privateQuestMerge(localStatus = null, knownServerRevision = null, incomingServerRevision = 1L),
        )
        assertEquals(
            PrivateQuestMerge.SAVE,
            privateQuestMerge(localStatus = "SYNCED", knownServerRevision = 4L, incomingServerRevision = 5L),
        )
        assertEquals(
            PrivateQuestMerge.SAVE,
            privateQuestMerge(localStatus = "SYNCED", knownServerRevision = 4L, incomingServerRevision = null),
        )
    }
}
