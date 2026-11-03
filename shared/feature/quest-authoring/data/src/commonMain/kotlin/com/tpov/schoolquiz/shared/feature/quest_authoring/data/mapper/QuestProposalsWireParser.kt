package com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalBox
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalDecision
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalQuestionPreview
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalRow

/**
 * Строгий декодер конверта `quest-proposals-v1` (история 4.25, UX-22).
 *
 * Чужие версии конверта и нарушенные границы страницу не строят: расхождение контракта —
 * отказ чтения, а не выдуманная строка. Битая запись сервер помечает сам (`unreadable`) —
 * строка-метка проходит насквозь (AD-10).
 */
object QuestProposalsWireParser {
    const val VIEW_VERSION = "quest-proposals-v1"
    const val MAX_ROWS = 50
    const val MAX_PREVIEWS_PER_ROW = 64

    fun questProposalsPage(data: Any?): QuestProposalPage {
        val root = data as? Map<*, *> ?: error("Proposals response is not an object")
        check(root.string(FIELD_VIEW) == VIEW_VERSION) { "Unsupported proposals view version" }
        val rows = root[FIELD_ROWS] as? List<*> ?: error("Field $FIELD_ROWS is not a list")
        check(rows.size <= MAX_ROWS) { "Proposals page exceeds the row bound" }
        return QuestProposalPage(
            rows = rows.map(::questProposalRow),
            hasMore = root[FIELD_HAS_MORE] as? Boolean ?: error("Field $FIELD_HAS_MORE is not a boolean"),
            nextCursor = root.string(FIELD_NEXT_CURSOR),
        )
    }

    private fun questProposalRow(raw: Any?): QuestProposalRow {
        val row = raw as? Map<*, *> ?: error("Proposal row is not an object")
        val unreadable = row[FIELD_UNREADABLE] as? Boolean ?: false
        if (unreadable) {
            val id = row.string(FIELD_PROPOSAL_ID) ?: error("Unreadable row lost its id")
            return QuestProposalRow(
                proposalId = id, unreadable = true, kind = "", status = "", stale = false,
                targetLessonId = null, targetThemeId = null, targetCatalogId = null, targetQuestId = null,
                createdAtMs = 0, checkedAtMs = 0, decidedAtMs = 0, feeNolics = 0, changedQuestions = 0,
                decision = null, proposalText = null, proposalOwnVariant = null, lessonTitle = null,
                questionPreviews = emptyList(),
            )
        }
        val previewsRaw = row[FIELD_PREVIEWS] as? List<*> ?: error("Field $FIELD_PREVIEWS is not a list")
        check(previewsRaw.size <= MAX_PREVIEWS_PER_ROW) { "Row exceeds the preview bound" }
        val decisionRaw = row[FIELD_DECISION] as? Map<*, *>
        return QuestProposalRow(
            proposalId = row.string(FIELD_PROPOSAL_ID) ?: error("Field $FIELD_PROPOSAL_ID is missing"),
            kind = row.string(FIELD_KIND) ?: error("Field $FIELD_KIND is missing"),
            status = row.string(FIELD_STATUS) ?: error("Field $FIELD_STATUS is missing"),
            stale = row[FIELD_STALE] as? Boolean ?: error("Field $FIELD_STALE is not a boolean"),
            targetLessonId = row.string(FIELD_TARGET_LESSON),
            targetThemeId = row.string(FIELD_TARGET_THEME),
            targetCatalogId = row.string(FIELD_TARGET_CATALOG),
            targetQuestId = row.string(FIELD_TARGET_QUEST),
            createdAtMs = row.unsignedLong(FIELD_CREATED),
            checkedAtMs = row.unsignedLong(FIELD_CHECKED),
            decidedAtMs = row.unsignedLong(FIELD_DECIDED),
            feeNolics = row.unsignedInt(FIELD_FEE),
            changedQuestions = row.unsignedInt(FIELD_CHANGED),
            decision = decisionRaw?.let {
                QuestProposalDecision(
                    decision = it.string(FIELD_DECISION_VALUE) ?: error("Field $FIELD_DECISION_VALUE is missing"),
                    comment = it.string(FIELD_DECISION_COMMENT),
                    decidedAtMs = it.unsignedLong(FIELD_DECIDED),
                )
            },
            proposalText = row.string(FIELD_TEXT),
            proposalOwnVariant = row.string(FIELD_OWN_VARIANT),
            lessonTitle = row.string(FIELD_LESSON_TITLE),
            questionPreviews = previewsRaw.map { preview ->
                val entry = preview as? Map<*, *> ?: error("Question preview is not an object")
                QuestProposalQuestionPreview(
                    id = entry.string("id") ?: error("Preview id is missing"),
                    text = entry.string("text") ?: "",
                    edited = entry["edited"] as? Boolean ?: false,
                )
            },
        )
    }

    fun boxName(box: QuestProposalBox): String = when (box) {
        QuestProposalBox.INCOMING -> "INCOMING"
        QuestProposalBox.OUTGOING -> "OUTGOING"
    }

    private const val FIELD_VIEW = "view"
    private const val FIELD_ROWS = "rows"
    private const val FIELD_HAS_MORE = "hasMore"
    private const val FIELD_NEXT_CURSOR = "nextCursor"
    private const val FIELD_UNREADABLE = "unreadable"
    private const val FIELD_PROPOSAL_ID = "proposalId"
    private const val FIELD_KIND = "kind"
    private const val FIELD_STATUS = "status"
    private const val FIELD_STALE = "stale"
    private const val FIELD_TARGET_LESSON = "targetLessonId"
    private const val FIELD_TARGET_THEME = "targetThemeId"
    private const val FIELD_TARGET_CATALOG = "targetCatalogId"
    private const val FIELD_TARGET_QUEST = "targetQuestId"
    private const val FIELD_CREATED = "createdAtMs"
    private const val FIELD_CHECKED = "checkedAtMs"
    private const val FIELD_DECIDED = "decidedAtMs"
    private const val FIELD_FEE = "feeNolics"
    private const val FIELD_CHANGED = "changedQuestions"
    private const val FIELD_DECISION = "decision"
    private const val FIELD_DECISION_VALUE = "decision"
    private const val FIELD_DECISION_COMMENT = "comment"
    private const val FIELD_TEXT = "proposalText"
    private const val FIELD_OWN_VARIANT = "proposalOwnVariant"
    private const val FIELD_LESSON_TITLE = "lessonTitle"
    private const val FIELD_PREVIEWS = "questionPreviews"

    private fun Map<*, *>.string(key: String): String? = this[key] as? String

    private fun Map<*, *>.unsignedInt(key: String): Int {
        val value = this[key] as? Long ?: this[key] as? Int ?: error("Field $key is not an integer")
        check(value in 0..Int.MAX_VALUE.toLong()) { "Field $key is out of range" }
        return value.toInt()
    }

    private fun Map<*, *>.unsignedLong(key: String): Long {
        val value = this[key] as? Long ?: this[key] as? Int ?: error("Field $key is not an integer")
        check(value in 0..Long.MAX_VALUE) { "Field $key is out of range" }
        return value.toLong()
    }
}
