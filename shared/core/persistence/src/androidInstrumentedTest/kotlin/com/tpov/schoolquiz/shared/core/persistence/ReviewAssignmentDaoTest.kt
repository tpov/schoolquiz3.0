package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewAssignmentDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ReviewAssignmentDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()
        dao = db.reviewAssignmentDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun sameAssignmentId_isIsolatedByOwnerUid() = runTest {
        dao.insertAll(
            listOf(
                assignment(ownerUid = "reviewer-a", title = "Assignment A"),
                assignment(ownerUid = "reviewer-b", title = "Assignment B"),
            ),
        )
        dao.insertQuestions(
            listOf(
                question(ownerUid = "reviewer-a", text = "Question A"),
                question(ownerUid = "reviewer-b", text = "Question B"),
            ),
        )

        val reviewerA = dao.findDetailsByOwner("reviewer-a").single()
        val reviewerB = dao.findDetailsByOwner("reviewer-b").single()

        assertEquals("Assignment A", reviewerA.assignment.title)
        assertEquals(listOf("Question A"), reviewerA.questions.map { it.text })
        assertEquals("Assignment B", reviewerB.assignment.title)
        assertEquals(listOf("Question B"), reviewerB.questions.map { it.text })
    }

    @Test
    fun observeDetailsByOwner_keepsSameAssignmentIdPerOwner() = runTest {
        dao.insertAll(
            listOf(
                assignment(ownerUid = "reviewer-a", title = "Assignment A"),
                assignment(ownerUid = "reviewer-b", title = "Assignment B"),
            ),
        )
        dao.insertQuestions(
            listOf(
                question(ownerUid = "reviewer-a", text = "Question A"),
                question(ownerUid = "reviewer-b", text = "Question B"),
            ),
        )

        val observed = dao.observeDetailsByOwner("reviewer-a").first()

        assertEquals(listOf("Assignment A"), observed.map { it.assignment.title })
        assertEquals(listOf("Question A"), observed.single().questions.map { it.text })
    }

    private fun assignment(
        ownerUid: String,
        title: String,
        id: String = "assignment-1",
    ): ReviewAssignmentEntity =
        ReviewAssignmentEntity(
            id = id,
            ownerUid = ownerUid,
            submissionId = "submission-1",
            catalogId = "catalog-1",
            draftId = "draft-1",
            questId = "quest-1",
            lessonId = "lesson-1",
            title = title,
            createdAtMs = 1L,
            taskKinds = listOf("TESTING"),
            sourceLanguages = listOf("ru"),
            newTranslationLanguages = emptyList(),
            reviewLanguages = emptyList(),
            isTested = false,
            testingScore = null,
            isLogicReviewed = false,
            logicScore = null,
            isTranslationReviewed = false,
            translationScore = null,
            translatedLanguages = listOf("ru=125"),
        )

    private fun question(
        ownerUid: String,
        text: String,
        assignmentId: String = "assignment-1",
        questionId: String = "question-1",
    ): ReviewAssignmentQuestionEntity =
        ReviewAssignmentQuestionEntity(
            ownerUid = ownerUid,
            assignmentId = assignmentId,
            questionId = questionId,
            draftId = "draft-1",
            lessonId = "lesson-1",
            type = "SINGLE_CHOICE",
            language = "ru",
            languageLevel = 125,
            difficulty = "EASY",
            order = 0,
            text = text,
            imagePath = null,
            payload = "{}",
            updatedAtMs = 1L,
        )
}
