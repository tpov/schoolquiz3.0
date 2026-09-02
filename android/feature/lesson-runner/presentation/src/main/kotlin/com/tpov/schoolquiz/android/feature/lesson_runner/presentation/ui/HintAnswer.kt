package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart
import com.tpov.schoolquiz.shared.core.question_schema.BlankId
import com.tpov.schoolquiz.shared.core.question_schema.CandidateId
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft

/**
 * Whether the hint is live for [qState] right now, given the question's difficulty
 * ([isHard]), the [charges] left, and whether the verdict is already on screen.
 *
 * The one decision, kept out of the Composable so a JVM test can pin it: the screen must not
 * re-derive any part of it. A hint exists only when [buildHintDraft] can produce a complete,
 * playable answer, so a charge is never spent on a question with nothing to reveal.
 *
 * **Easy only.** `spec-charges/SPEC.md:29,47` names the sinks: "A **standard** charge is spent on
 * exactly two things — the toll for playing an activity, and a hint on an EASY question", and
 * "a standard charge never touches a hard question". Hard answers gate stars, the hard unlock and
 * certification, so a bought answer there is not a hint but a forged result. [isHard] has no
 * default on purpose: the rule must not be defaulted away by a call site that forgets it.
 * `RunnerUiState.Question.revealCorrect` being false on hard is not this rule — it only suppresses
 * the verdict banner while the answer is still submitted.
 *
 * Note on wording: when this is false the button is still drawn — dimmed and unclickable, exactly
 * as it looks with no charges left. "Not offered" throughout this feature means disabled, not
 * absent.
 */
internal fun isHintAvailable(
    qState: QuestionUiState,
    isHard: Boolean,
    charges: Int?,
    feedbackShown: Boolean,
): Boolean = !isHard && (charges ?: 0) > 0 && !feedbackShown && buildHintDraft(qState) != null

/**
 * The answer the hint plays: the complete correct draft for [qState], or null when there is
 * nothing playable to reveal.
 *
 * Prefer the typed per-type builders below at a call site that already knows the type — they
 * return the exact draft subtype, so a handler cannot be wired to the wrong one and still
 * compile. This umbrella overload exists for the type-agnostic availability decision.
 *
 * **Which cases are live.** Only the FillBlank guard is reachable in shipped content:
 * `QuestionContent`'s init blocks (`QuestionContent.kt:79-142`) already require a `correctOptionId`
 * present in `options`, `correctOptionIds.size >= 2` all present in `options`, and
 * `items.size in 2..8`, and `StartLessonAttemptUseCase` drops content that fails them — so the
 * SingleChoice, MultipleChoice and Ordering guards are defence against a future mapper change,
 * not against today's data. FillBlank is the one that fires in production: see
 * [QuestionUiState.FillBlank.hintDraft].
 */
internal fun buildHintDraft(qState: QuestionUiState): UserAnswerDraft? =
    when (qState) {
        // An opinion has no right version to reveal.
        is QuestionUiState.Survey -> null
        is QuestionUiState.SingleChoice -> qState.hintDraft()
        is QuestionUiState.MultipleChoice -> qState.hintDraft()
        is QuestionUiState.Ordering -> qState.hintDraft()
        is QuestionUiState.FillBlank -> qState.hintDraft()
    }

/** Null unless the correct option is known and is one the player can actually see. */
@Suppress("ReturnCount")
internal fun QuestionUiState.SingleChoice.hintDraft(): UserAnswerDraft.SingleChoiceDraft? {
    val correctId = correctOptionId ?: return null
    if (options.none { it.id == correctId }) return null
    return UserAnswerDraft.SingleChoiceDraft(OptionId(correctId))
}

/** Null unless there is a correct set and every id in it is on screen. */
@Suppress("ReturnCount")
internal fun QuestionUiState.MultipleChoice.hintDraft(): UserAnswerDraft.MultipleChoiceDraft? {
    if (correctIds.isEmpty()) return null
    val onScreen = options.map { it.id }.toSet()
    if (correctIds.any { it !in onScreen }) return null
    return UserAnswerDraft.MultipleChoiceDraft(correctIds.map { OptionId(it) }.toSet())
}

/**
 * Null unless the correct order is a rearrangement of the items on screen.
 *
 * Non-empty is not enough: `OrderingContent` falls back to `qState.items` unless the order it is
 * handed has exactly the same size, so a mismatched order would take the charge and leave the
 * list sitting where it was.
 */
@Suppress("ReturnCount")
internal fun QuestionUiState.Ordering.hintDraft(): UserAnswerDraft.OrderingDraft? {
    if (correctOrderIds.isEmpty()) return null
    if (correctOrderIds.size != items.size) return null
    if (correctOrderIds.toSet() != items.map { it.id }.toSet()) return null
    return UserAnswerDraft.OrderingDraft(correctOrderIds.map { OptionId(it) })
}

/**
 * Null unless the hint can fill every blank the question is graded on.
 *
 * This is the live guard. `RunnerStateMapper` builds `correctCandidateIdsByBlankIndex` over every
 * declared blank (`RunnerStateMapper.kt:144-145`) but `parseTemplateParts` (`:191`) emits a
 * [TemplatePart.Blank] only while `idx < segments.size - 1`, so a question whose text carries
 * fewer `___` markers than it declares blanks exposes only some of them. The domain still grades
 * the answer over all of them, so a draft covering only the reachable ones is not a correct
 * answer — it would take the charge and come back marked wrong. Both directions therefore have to
 * line up: every template blank resolves to a candidate the player can see, and the answer key
 * holds nothing beyond those blanks.
 */
@Suppress("ReturnCount")
internal fun QuestionUiState.FillBlank.hintDraft(): UserAnswerDraft.FillBlankDraft? {
    val blanks = templateParts.filterIsInstance<TemplatePart.Blank>()
    if (blanks.isEmpty()) return null
    if (blanks.size != correctCandidateIdsByBlankIndex.size) return null
    val candidateIds = candidates.map { it.id }.toSet()
    val filled =
        blanks.associate { blank ->
            val candidateId = correctCandidateIdsByBlankIndex[blank.index]?.takeIf { it in candidateIds }
            BlankId(blank.blankId) to candidateId?.let { CandidateId(it) }
        }
    if (filled.size != blanks.size) return null
    if (filled.values.any { it == null }) return null
    return UserAnswerDraft.FillBlankDraft(filled)
}

/**
 * What the hint draft fills, keyed the way [AnswerFeedback.FillBlank] wants it — by template
 * index rather than by blank id.
 *
 * The verdict has to be computed from the draft that is actually submitted. Passing the answer
 * key in both slots of [AnswerFeedback.FillBlank] makes `revealDigit()` compare the key with
 * itself, which reads a perfect 9 no matter what the player was handed.
 */
internal fun UserAnswerDraft.FillBlankDraft.filledByBlankIndex(
    blankParts: List<TemplatePart.Blank>,
): Map<Int, String> {
    val indexByBlankId = blankParts.associateBy({ it.blankId }) { it.index }
    return filled.mapNotNull { (blankId, candidateId) ->
        val index = indexByBlankId[blankId.raw] ?: return@mapNotNull null
        val candidate = candidateId ?: return@mapNotNull null
        index to candidate.raw
    }.toMap()
}

/**
 * The hint's two decisions for one question, held together so the four type branches cannot
 * disagree: whether the button is live, and the spend, which succeeds at most once per question.
 */
internal class HintControl(
    val enabled: Boolean,
    val spend: () -> Boolean,
)
