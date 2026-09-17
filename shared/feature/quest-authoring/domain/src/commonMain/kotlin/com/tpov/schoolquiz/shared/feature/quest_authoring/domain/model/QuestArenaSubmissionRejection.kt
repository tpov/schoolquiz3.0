package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

/**
 * Почему отправка на арену не состоялась.
 *
 * Причина названа типом, а не текстом сообщения. Прежде сценарий отклонял отправку четырьмя
 * разными `require`, а экран сваливал любой `IllegalArgumentException` в одну подпись «нужны
 * сохранённые лёгкие и сложные вопросы». Автору с готовым квестом, отклонённым по состоянию
 * черновика, предлагалось чинить исправные вопросы — то есть сообщение уводило от настоящей
 * причины.
 *
 * Наследование от [IllegalArgumentException] сохранено намеренно: вызывающий код и прежние
 * тесты ловят именно его, и смена базового типа сломала бы их молча.
 */
sealed class QuestArenaSubmissionRejection(
    message: String,
) : IllegalArgumentException(message) {
    /** Черновика с таким идентификатором в базе нет. */
    class DraftNotFound(
        val draftId: QuestDraftId,
    ) : QuestArenaSubmissionRejection("Draft ${draftId.value} not found")

    /**
     * Состояние черновика не допускает отправку.
     *
     * Допустимы [SUBMITTABLE_DRAFT_STATUSES]. Исключены те, где отправка уже идёт или закончена
     * (`REVIEW_QUEUED`, `REVIEW_SENT`, `PUBLISHED`), и `CONFLICT` — у расхождения свой путь
     * разрешения, отправлять поверх него нельзя.
     */
    class DraftNotSubmittable(
        val draftId: QuestDraftId,
        val status: QuestDraftStatus,
    ) : QuestArenaSubmissionRejection(
            "Draft ${draftId.value} is not submittable in status $status",
        )

    /** Полка назначения не арена и не архив. */
    class UnsupportedTargetShelf(
        val targetShelf: String,
    ) : QuestArenaSubmissionRejection("Target shelf must be arena or archive, got $targetShelf")

    /** Не выбрано ни одного урока. */
    class NoLessonsChosen :
        QuestArenaSubmissionRejection("Choose at least one lesson for arena submission")

    /** Среди выбранных есть урок из другого черновика. */
    class LessonOutsideDraft(
        val draftId: QuestDraftId,
        val lessonIds: Set<DraftLessonId>,
    ) : QuestArenaSubmissionRejection(
            "Selected lessons must belong to draft ${draftId.value}",
        )

    /** В уроке не хватает сохранённых вопросов нужных сложностей. */
    class LessonNotReady(
        val lessonId: DraftLessonId,
    ) : QuestArenaSubmissionRejection(
            "Lesson ${lessonId.value} is not ready for arena submission",
        )
}

/**
 * Состояния черновика, из которых отправка разрешена.
 *
 * `DRAFT` входит сюда наравне с остальными, и это важно. Отказ рецензента и карантин заявки
 * возвращают черновик именно в `DRAFT` — чтобы автор поправил и отправил заново
 * (см. `QuestArenaQuarantineRollback`). Пока `DRAFT` отсутствовал в наборе, обе половины
 * противоречили друг другу: черновик разблокировали для правки, а путь отправки его не принимал,
 * и поднять состояние обратно было нечем — `SAVED` присваивается только при создании черновика.
 * Любой отклонённый квест становился неотправляемым навсегда.
 *
 * Готовность к отправке проверяется отдельно, по урокам и вопросам, поэтому допуск `DRAFT` не
 * пропускает недоделанную работу — он лишь перестаёт запирать доделанную.
 */
val SUBMITTABLE_DRAFT_STATUSES: Set<QuestDraftStatus> =
    setOf(
        QuestDraftStatus.DRAFT,
        QuestDraftStatus.SAVED,
        QuestDraftStatus.SYNC_PENDING,
        QuestDraftStatus.SYNCED,
    )
