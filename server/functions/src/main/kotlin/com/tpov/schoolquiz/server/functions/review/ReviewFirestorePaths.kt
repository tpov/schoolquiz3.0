package com.tpov.schoolquiz.server.functions.review

object ReviewFirestorePaths {
    const val REVIEW_REQUESTS_COLLECTION = "quest_review_requests"
    const val PROFILES_COLLECTION = "profiles"
    const val ARENA_REVIEW_CONFIG = "configs/arena_review"

    fun privateCatalog(
        ownerUid: String,
        catalogId: String,
    ): String = "private/$ownerUid/catalogs/$catalogId"

    fun privateQuest(
        ownerUid: String,
        catalogId: String,
        questId: String,
    ): String = "${privateCatalog(ownerUid, catalogId)}/quests/$questId"

    fun privateSyncChange(
        ownerUid: String,
        catalogId: String,
        questId: String,
    ): String = "private/$ownerUid/sync_changes/${catalogId}_$questId"

    fun privateSection(
        ownerUid: String,
        catalogId: String,
        questId: String,
        sectionId: String,
    ): String = "${privateQuest(ownerUid, catalogId, questId)}/sections/$sectionId"

    fun privateTheme(
        ownerUid: String,
        catalogId: String,
        questId: String,
        sectionId: String,
        themeId: String,
    ): String = "${privateSection(ownerUid, catalogId, questId, sectionId)}/themes/$themeId"

    fun privateLesson(
        ownerUid: String,
        catalogId: String,
        questId: String,
        sectionId: String,
        themeId: String,
        lessonId: String,
    ): String = "${privateTheme(ownerUid, catalogId, questId, sectionId, themeId)}/lessons/$lessonId"

    fun privateQuestion(
        ownerUid: String,
        catalogId: String,
        questId: String,
        sectionId: String,
        themeId: String,
        lessonId: String,
        questionId: String,
    ): String = "${privateLesson(ownerUid, catalogId, questId, sectionId, themeId, lessonId)}/questions/$questionId"

    fun adminLesson(lessonId: String): String = "admin/review/lessons/$lessonId"

    fun adminQuest(
        lessonId: String,
        questId: String,
    ): String = "${adminLesson(lessonId)}/quests/$questId"

    fun adminQuestion(
        lessonId: String,
        questId: String,
        questionId: String,
    ): String = "${adminQuest(lessonId, questId)}/questions/$questionId"

    fun adminReviewRecord(
        lessonId: String,
        reviewId: String,
    ): String = "${adminLesson(lessonId)}/reviews/$reviewId"

    fun adminSyncChange(changeId: String): String = "admin/review/sync_changes/$changeId"
}
