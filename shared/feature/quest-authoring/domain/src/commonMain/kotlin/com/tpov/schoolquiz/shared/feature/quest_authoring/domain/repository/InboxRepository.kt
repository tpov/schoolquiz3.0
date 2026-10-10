package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxAnchor
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonErrorReportSubmission

/**
 * Общий inbox автора (AD-20): чтение разрешённых проекций и подача приватного отчёта.
 *
 * Чтение идёт серверной вью — право проверяет она; подача кладётся в общую очередь (AD-6),
 * единственный транспорт отложенной мутации.
 */
interface InboxRepository {
    suspend fun listInboxItems(
        pageSize: Int,
        after: InboxAnchor?,
    ): Result<InboxPage>

    suspend fun queueErrorReport(submission: LessonErrorReportSubmission): Result<Unit>
}
