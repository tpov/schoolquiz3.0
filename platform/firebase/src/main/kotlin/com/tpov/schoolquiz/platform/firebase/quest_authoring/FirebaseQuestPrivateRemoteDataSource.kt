package com.tpov.schoolquiz.platform.firebase.quest_authoring

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaDraftDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaLessonDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaQuestionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaReviewDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaSectionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaThemeDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.PrivateQuestSnapshot
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.PrivateQuestSyncChange
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRequest
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestPrivateRemoteDataSource
import kotlinx.coroutines.tasks.await

class FirebaseQuestPrivateRemoteDataSource(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : QuestPrivateRemoteDataSource {
    override suspend fun fetchChangedSince(cursorMs: Long): List<PrivateQuestSyncChange> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        return firestore.collection(PRIVATE_COLLECTION)
            .document(uid)
            .collection(SYNC_CHANGES_COLLECTION)
            .whereGreaterThan(CHANGED_AT_MS, cursorMs)
            .orderBy(CHANGED_AT_MS)
            .get()
            .await()
            .documents
            .mapNotNull { it.toPrivateQuestSyncChange() }
    }

    override suspend fun fetchSnapshots(changes: List<PrivateQuestSyncChange>): List<PrivateQuestSnapshot> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        return changes
            .distinctBy { "${it.catalogId}/${it.questId}" }
            .mapNotNull { change ->
                firestore.collection(PRIVATE_COLLECTION)
                    .document(uid)
                    .collection(CATALOGS_COLLECTION)
                    .document(change.catalogId)
                    .collection(QUESTS_COLLECTION)
                    .document(change.questId)
                    .get()
                    .await()
                    .takeIf { it.exists() }
                    ?.toPrivateQuestSnapshot(uid, change.catalogId, change.changedAtMs)
            }
            .sortedBy { it.changedAtMs }
    }

    private suspend fun DocumentSnapshot.toPrivateQuestSnapshot(
        uid: String,
        catalogId: String,
        changedAtMsFallback: Long = 0L,
    ): PrivateQuestSnapshot {
        val questRef = reference
        val sections = questRef.collection(SECTIONS_COLLECTION).get().await().documents
        val sectionDtos = sections.map { it.toSectionDto(id) }
        val themeDtos = mutableListOf<ArenaThemeDto>()
        val lessonDtos = mutableListOf<ArenaLessonDto>()
        val questionDtos = mutableListOf<ArenaQuestionDto>()
        sections.forEach { section ->
            section.reference.collection(THEMES_COLLECTION).get().await().documents.forEach { theme ->
                themeDtos += theme.toThemeDto(id, section.id)
                theme.reference.collection(LESSONS_COLLECTION).get().await().documents.forEach { lesson ->
                    lessonDtos += lesson.toLessonDto(id, theme.id)
                    lesson.reference.collection(QUESTIONS_COLLECTION).get().await().documents.forEach { question ->
                        questionDtos += question.toQuestionDto(id, lesson.id)
                    }
                }
            }
        }
        val revision = long(LOCAL_REVISION)
        val changedAtMs = long(CHANGED_AT_MS, fallback = changedAtMsFallback.takeIf { it > 0L } ?: long(UPDATED_AT_MS))
        return PrivateQuestSnapshot(
            serverRevision = revision,
            changedAtMs = changedAtMs,
            request =
                QuestArenaSubmissionRequest(
                    submissionId = string(SUBMISSION_ID, fallback = id),
                    draftId = string(DRAFT_ID, fallback = id),
                    ownerUid = uid,
                    localRevision = revision,
                    requestedAtMs = changedAtMs,
                    draft = toDraftDto(catalogId),
                    sections = sectionDtos,
                    themes = themeDtos,
                    lessons = lessonDtos,
                    questions = questionDtos,
                    review = reviewMap().toArenaReviewDto(),
                ),
        )
    }

    private fun DocumentSnapshot.toPrivateQuestSyncChange(): PrivateQuestSyncChange? {
        val catalogId = getString(CATALOG_ID)?.takeIf { it.isNotBlank() } ?: return null
        val questId = getString(QUEST_ID)?.takeIf { it.isNotBlank() } ?: return null
        val changedAtMs = longOrNull(CHANGED_AT_MS) ?: return null
        return PrivateQuestSyncChange(
            catalogId = catalogId,
            questId = questId,
            changedAtMs = changedAtMs,
        )
    }

    private fun DocumentSnapshot.toDraftDto(catalogId: String): ArenaDraftDto =
        ArenaDraftDto(
            id = id,
            catalogId = catalogId,
            title = string(TITLE),
            description = getString(DESCRIPTION),
            defaultLanguage = string(DEFAULT_LANGUAGE),
            defaultDifficulty = string(DEFAULT_DIFFICULTY),
            publicQuestId = getString(PUBLIC_QUEST_ID),
            createdAtMs = long(CREATED_AT_MS),
            updatedAtMs = long(UPDATED_AT_MS),
        )

    private fun DocumentSnapshot.toSectionDto(draftId: String): ArenaSectionDto =
        ArenaSectionDto(
            id = id,
            draftId = draftId,
            title = string(TITLE),
            order = long(ORDER).toInt(),
        )

    private fun DocumentSnapshot.toThemeDto(
        draftId: String,
        sectionId: String,
    ): ArenaThemeDto =
        ArenaThemeDto(
            id = id,
            draftId = draftId,
            sectionId = string(SECTION_ID, fallback = sectionId),
            title = string(TITLE),
            order = long(ORDER).toInt(),
        )

    private fun DocumentSnapshot.toLessonDto(
        draftId: String,
        themeId: String,
    ): ArenaLessonDto =
        ArenaLessonDto(
            id = id,
            draftId = draftId,
            themeId = string(THEME_ID, fallback = themeId),
            title = string(TITLE),
            order = long(ORDER).toInt(),
        )

    private fun DocumentSnapshot.toQuestionDto(
        draftId: String,
        lessonId: String,
    ): ArenaQuestionDto =
        ArenaQuestionDto(
            id = id,
            draftId = draftId,
            lessonId = string(LESSON_ID, fallback = lessonId),
            type = string(TYPE),
            language = string(LANGUAGE),
            languageLevel = long(LANGUAGE_LEVEL).toInt(),
            difficulty = string(DIFFICULTY),
            order = long(ORDER).toInt(),
            text = string(TEXT),
            imagePath = getString(IMAGE_PATH),
            payload = string(PAYLOAD),
            updatedAtMs = long(UPDATED_AT_MS),
        )

    private fun Map<String, Any?>.toArenaReviewDto(): ArenaReviewDto =
        ArenaReviewDto(
            isTested = boolean(IS_TESTED),
            testingScore = doubleOrNull(TESTING_SCORE),
            isLogicReviewed = boolean(IS_LOGIC_REVIEWED),
            logicScore = doubleOrNull(LOGIC_SCORE),
            isTranslationReviewed = boolean(IS_TRANSLATION_REVIEWED),
            translationScore = longOrNull(TRANSLATION_SCORE)?.toInt(),
            translatedLanguages = languageLevels(TRANSLATED_LANGUAGES),
        )

    private fun DocumentSnapshot.string(
        field: String,
        fallback: String = "",
    ): String = getString(field)?.takeIf { it.isNotBlank() } ?: fallback

    private fun DocumentSnapshot.long(
        field: String,
        fallback: Long = 0L,
    ): Long = getLong(field) ?: fallback

    private fun DocumentSnapshot.longOrNull(field: String): Long? =
        getLong(field) ?: getTimestamp(field)?.toDate()?.time

    private fun DocumentSnapshot.reviewMap(): Map<String, Any?> =
        (get(REVIEW) as? Map<*, *>).orEmpty().mapKeys { it.key.toString() }

    private fun Map<String, Any?>.boolean(field: String): Boolean = this[field] as? Boolean ?: false

    private fun Map<String, Any?>.doubleOrNull(field: String): Double? =
        when (val value = this[field]) {
            is Double -> value
            is Float -> value.toDouble()
            is Number -> value.toDouble()
            else -> null
        }

    private fun Map<String, Any?>.longOrNull(field: String): Long? =
        when (val value = this[field]) {
            is Long -> value
            is Int -> value.toLong()
            is Number -> value.toLong()
            else -> null
        }

    private fun Map<String, Any?>.languageLevels(field: String): Map<String, Int> =
        (this[field] as? Map<*, *>).orEmpty().mapNotNull { (key, value) ->
            val language = key?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val level = (value as? Number)?.toInt() ?: return@mapNotNull null
            language to level
        }.toMap()

    private companion object {
        const val PRIVATE_COLLECTION = "private"
        const val CATALOGS_COLLECTION = "catalogs"
        const val SYNC_CHANGES_COLLECTION = "sync_changes"
        const val QUESTS_COLLECTION = "quests"
        const val SECTIONS_COLLECTION = "sections"
        const val THEMES_COLLECTION = "themes"
        const val LESSONS_COLLECTION = "lessons"
        const val QUESTIONS_COLLECTION = "questions"
        const val REVIEW = "review"
        const val SUBMISSION_ID = "submissionId"
        const val DRAFT_ID = "draftId"
        const val TITLE = "title"
        const val DESCRIPTION = "description"
        const val DEFAULT_LANGUAGE = "defaultLanguage"
        const val DEFAULT_DIFFICULTY = "defaultDifficulty"
        const val PUBLIC_QUEST_ID = "publicQuestId"
        const val CATALOG_ID = "catalogId"
        const val QUEST_ID = "questId"
        const val CREATED_AT_MS = "createdAtMs"
        const val UPDATED_AT_MS = "updatedAtMs"
        const val CHANGED_AT_MS = "changedAtMs"
        const val LOCAL_REVISION = "localRevision"
        const val SECTION_ID = "sectionId"
        const val THEME_ID = "themeId"
        const val LESSON_ID = "lessonId"
        const val TYPE = "type"
        const val LANGUAGE = "language"
        const val LANGUAGE_LEVEL = "languageLevel"
        const val DIFFICULTY = "difficulty"
        const val ORDER = "order"
        const val TEXT = "text"
        const val IMAGE_PATH = "imagePath"
        const val PAYLOAD = "payload"
        const val IS_TESTED = "isTested"
        const val TESTING_SCORE = "testingScore"
        const val IS_LOGIC_REVIEWED = "isLogicReviewed"
        const val LOGIC_SCORE = "logicScore"
        const val IS_TRANSLATION_REVIEWED = "isTranslationReviewed"
        const val TRANSLATION_SCORE = "translationScore"
        const val TRANSLATED_LANGUAGES = "translatedLanguages"
    }
}
