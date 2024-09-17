package com.tpov.schoolquiz

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.tpov.common.data.RepositoryQuestionImpl
import com.tpov.common.data.RepositoryQuizImpl
import com.tpov.common.data.RepositoryStuctureImpl
import com.tpov.common.data.database.CommonDatabase
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.local.StructureCategoryDataEntity
import com.tpov.common.domain.QuestionUseCase
import com.tpov.common.domain.QuizUseCase
import com.tpov.common.domain.StructureUseCase
import com.tpov.common.domain.repository.RepositoryQuestion
import com.tpov.common.domain.repository.RepositoryQuiz
import com.tpov.schoolquiz.data.RepositoryProfileImpl
import com.tpov.schoolquiz.data.database.MainDatabase
import com.tpov.schoolquiz.domain.ProfileUseCase
import com.tpov.schoolquiz.domain.repository.RepositoryProfile
import com.tpov.schoolquiz.presentation.main.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreateQuizIntegrationTest {

    private lateinit var viewModel: MainViewModel
    private lateinit var repositoryQuiz: RepositoryQuiz
    private lateinit var repositoryQuestion: RepositoryQuestion
    private lateinit var repositoryProfile: RepositoryProfile
    private lateinit var structureUseCase: StructureUseCase
    private lateinit var repositoryStuctureImpl: RepositoryStuctureImpl
    private lateinit var quizUseCase: QuizUseCase
    private lateinit var profileUseCase: ProfileUseCase
    private lateinit var questionUseCase: QuestionUseCase

    val quizEntity = QuizEntity(
        nameQuiz = "Столицы Европы",
        numQ = 2,
        numHQ = 2,
        languages = "ru|ua|en"
    )

    val questionsEntity1 = QuestionEntity(
        nameQuestion = "Как называется столица Франции?",
        answer = 1,
        nameAnswers = "Париж|Лондон|Берлин|Мадрид",
        hardQuestion = false,
        language = "ru", // Русский язык
        numQuestion = 1,
    )

    val questionsEntity1Ua = QuestionEntity(
        nameQuestion = "Як називається столиця Франції?",
        answer = 1,
        nameAnswers = "Париж|Лондон|Берлін|Мадрид",
        hardQuestion = false,
        language = "ua", // Украинский язык
        numQuestion = 1,
    )

    val questionsEntity1En = QuestionEntity(
        nameQuestion = "What is the capital of France?", // Английский
        answer = 1, // Париж — правильный ответ на первом месте
        nameAnswers = "Paris|London|Berlin|Madrid",
        hardQuestion = false,
        language = "en", // Английский язык
        numQuestion = 1,
    )

    val questionsEntity2 = QuestionEntity(
        nameQuestion = "Как называется столица Германии?", // Русский
        answer = 1, // Берлин — правильный ответ на первом месте
        nameAnswers = "Берлин|Париж|Лондон|Мадрид",
        hardQuestion = false,
        language = "ru", // Русский язык
        numQuestion = 2,
    )

    val questionsEntity2Ua = QuestionEntity(
        nameQuestion = "Як називається столиця Німеччини?", // Украинский
        answer = 1, // Берлин — правильный ответ на первом месте
        nameAnswers = "Берлін|Париж|Лондон|Мадрид",
        hardQuestion = false,
        language = "ua", // Украинский язык
        numQuestion = 2,
    )

    val questionsEntity2En = QuestionEntity(
        nameQuestion = "What is the capital of Germany?", // Английский
        answer = 1, // Берлин — правильный ответ на первом месте
        nameAnswers = "Berlin|Paris|London|Madrid",
        hardQuestion = false,
        language = "en", // Английский язык
        numQuestion = 2,
    )

    val questionsEntity3 = QuestionEntity(
        nameQuestion = "Как называется столица Испании?", // Русский
        answer = 1, // Мадрид — правильный ответ на первом месте
        nameAnswers = "Мадрид|Париж|Лондон|Берлин",
        hardQuestion = true,
        language = "ru", // Русский язык
        numQuestion = 3,
    )

    val questionsEntity3Ua = QuestionEntity(
        nameQuestion = "Як називається столиця Іспанії?", // Украинский
        answer = 1, // Мадрид — правильный ответ на первом месте
        nameAnswers = "Мадрид|Париж|Лондон|Берлін",
        hardQuestion = true,
        language = "ua", // Украинский язык
        numQuestion = 3,
    )

    val questionsEntity3En = QuestionEntity(
        nameQuestion = "What is the capital of Spain?", // Английский
        answer = 1, // Мадрид — правильный ответ на первом месте
        nameAnswers = "Madrid|Paris|London|Berlin",
        hardQuestion = true,
        language = "en", // Английский язык
        numQuestion = 3,
    )

    val questionsEntity4 = QuestionEntity(
        nameQuestion = "Как называется столица Великобритании?", // Русский
        answer = 1, // Лондон — правильный ответ на первом месте
        nameAnswers = "Лондон|Мадрид|Берлин|Париж",
        hardQuestion = true,
        language = "ru", // Русский язык
        numQuestion = 4,
    )

    val questionsEntity4Ua = QuestionEntity(
        nameQuestion = "Як називається столиця Великобританії?", // Украинский
        answer = 1, // Лондон — правильный ответ на первом месте
        nameAnswers = "Лондон|Мадрид|Берлін|Париж",
        hardQuestion = true,
        language = "ua", // Украинский язык
        numQuestion = 4,
    )

    val questionsEntity4En = QuestionEntity(
        nameQuestion = "What is the capital of the United Kingdom?", // Английский
        answer = 1,
        nameAnswers = "London|Madrid|Berlin|Paris",
        hardQuestion = true,
        language = "en",
        numQuestion = 4
    )

    // Добавляем все вопросы в список
    val questionsEntity = arrayListOf(
        questionsEntity1, questionsEntity1Ua, questionsEntity1En,
        questionsEntity2, questionsEntity2Ua, questionsEntity2En,
        questionsEntity3, questionsEntity3Ua, questionsEntity3En,
        questionsEntity4, questionsEntity4Ua, questionsEntity4En
    )

    val structureCategoryDataEntity = StructureCategoryDataEntity(
        newEventId = 1,
        newCategoryName = "География",
        newSubCategoryName = "Европа",
        newSubsubCategoryName = "Столицы",
        newQuizName = "Столицы Европы",
    )

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Инициализация Firebase и базы данных
        val firestore = FirebaseFirestore.getInstance()
        val quizDao = Room.inMemoryDatabaseBuilder(
            context,
            MainDatabase::class.java
        ).build().getQuizDao()
        val profileDao = Room.inMemoryDatabaseBuilder(
            context,
            MainDatabase::class.java
        ).build().getProfileDao()
        val questionDao = Room.inMemoryDatabaseBuilder(
            context,
            MainDatabase::class.java
        ).build().getQuestionDao()
        val structureCategoryDataDao = Room.inMemoryDatabaseBuilder(
            context,
            CommonDatabase::class.java
        ).build().getStructureCategoryDataDao()
        val structureRatingDataDao = Room.inMemoryDatabaseBuilder(
            context,
            CommonDatabase::class.java
        ).build().getStructureRatingDataDao()

        val storage = FirebaseStorage.getInstance()
        // Инициализация репозиториев
        repositoryQuiz = RepositoryQuizImpl(quizDao, firestore, storage)
        repositoryStuctureImpl = RepositoryStuctureImpl(structureRatingDataDao, structureCategoryDataDao, firestore, context)
        repositoryQuestion = RepositoryQuestionImpl(questionDao, firestore, storage)
        // Инициализация use case
        structureUseCase = StructureUseCase(repositoryStuctureImpl)
        quizUseCase = QuizUseCase(repositoryQuiz)
        questionUseCase = QuestionUseCase(repositoryQuestion)
        repositoryProfile = RepositoryProfileImpl(profileDao, quizDao)
        // Инициализация profileUseCase
        profileUseCase = ProfileUseCase(repositoryProfile)

        // Инициализация ViewModel
        viewModel = MainViewModel(structureUseCase, quizUseCase, questionUseCase, profileUseCase, context)
    }



    @Test
    fun testPushAndFetchQuiz() = runBlocking {
        viewModel.pushTheQuiz(structureCategoryDataEntity, quizEntity, questionsEntity)

        kotlinx.coroutines.delay(5000)

        structureUseCase.fetchQuizzes()

        kotlinx.coroutines.delay(5000)

        // Проверяем, что квиз сохранен правильно
        val savedQuiz = quizUseCase.getQuizById(101)
        assertNotNull("Квиз не найден в локальной базе данных", savedQuiz)
        assertEquals(quizEntity.copy(id = 101), savedQuiz)

        val savedQuestions = questionUseCase.getQuestionByIdQuiz(savedQuiz?.id ?: 0)
        assertTrue("Вопросы не найдены или не соответствуют ожиданиям", questionsEntity == savedQuestions)
    }
}