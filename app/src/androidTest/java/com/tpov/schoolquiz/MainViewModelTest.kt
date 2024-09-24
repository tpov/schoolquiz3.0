package com.tpov.schoolquiz

import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.tpov.common.data.RepositoryQuestionImpl
import com.tpov.common.data.RepositoryQuizImpl
import com.tpov.common.data.RepositoryStuctureImpl
import com.tpov.common.data.database.CommonDatabase
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs


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


    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Инициализация Firebase и базы данных
        val firestore = FirebaseFirestore.getInstance()
        val quizDao = Room.inMemoryDatabaseBuilder(
            context,
            MainDatabase::class.java
        ).build().getQuizDao()
        val questionDao = Room.inMemoryDatabaseBuilder(
            context,
            MainDatabase::class.java
        ).build().getQuestionDao()
        val structureRatingDataDao = Room.inMemoryDatabaseBuilder(
            context,
            CommonDatabase::class.java
        ).build().getStructureRatingDataDao()
        val structureCategoryDataDao = Room.inMemoryDatabaseBuilder(
            context,
            CommonDatabase::class.java
        ).build().getStructureCategoryDataDao()
        val profileDao = Room.inMemoryDatabaseBuilder(
            context,
            MainDatabase::class.java
        ).build().getProfileDao()

        val storage = FirebaseStorage.getInstance()

        // Инициализация всех репозиториев
        repositoryQuiz = RepositoryQuizImpl(quizDao, firestore, storage)

        // Предположим, что RepositoryQuestion и RepositoryStructureImpl требуют подобных данных
        repositoryQuestion = RepositoryQuestionImpl(questionDao, firestore, storage)
        repositoryStuctureImpl = RepositoryStuctureImpl(
            structureRatingDataDao,
            structureCategoryDataDao,
            firestore,
            context
        )

        repositoryProfile = RepositoryProfileImpl(profileDao, quizDao)
        // Инициализация use case
        structureUseCase = StructureUseCase(repositoryStuctureImpl)
        quizUseCase = QuizUseCase(repositoryQuiz)
        questionUseCase = QuestionUseCase(repositoryQuestion)

        // Инициализация profileUseCase (предполагается, что есть отдельный репозиторий для профилей)
        profileUseCase = ProfileUseCase(repositoryProfile)

        // Инициализация ViewModel с передачей всех зависимостей
        viewModel =
            MainViewModel(structureUseCase, quizUseCase, questionUseCase, profileUseCase, context)
    }

    @Test
    fun testPushAndFetchQuiz() = runBlocking<Unit> {

        kotlinx.coroutines.delay(30000) // для получения анонимного токена
        structureUseCase.logger(0)
        viewModel.pushTheQuiz(
            Quiz1.structureCategoryDataEntity,
            Quiz1.quizEntity1,
            Quiz1.questionsEntity
        )
        structureUseCase.logger(1)
        viewModel.pushTheQuiz(
            Quiz3.structureCategoryDataEntityAfrica,
            Quiz3.quizEntity3,
            Quiz3.questionsEntityAfrica
        )
        structureUseCase.logger(2)
        viewModel.pushTheQuiz(
            Quiz4.structureCategoryDataEntityNorthAmerica,
            Quiz4.quizEntity4,
            Quiz4.questionsEntityNorthAmerica
        )
        kotlinx.coroutines.delay(30000)

        structureUseCase.logger(1)
        val listQuiz = viewModel.getNewStructureDataANDQuizzes()
        Log.d("testPushAndFetchQuiz", "listQuiz: $listQuiz")
        assertEquals(3, listQuiz.size)
        structureUseCase.logger(2)
        kotlinx.coroutines.delay(5000)

        val savedQuiz1 = quizUseCase.getQuizById(101)
        val savedQuiz3 = quizUseCase.getQuizById(102)
        val savedQuiz4 = quizUseCase.getQuizById(103)

        assertNotNull("Квиз не найден в локальной базе данных", quizUseCase.getQuizById(101))
        assertNotNull("Квиз не найден в локальной базе данных", quizUseCase.getQuizById(102))
        assertNotNull("Квиз не найден в локальной базе данных", quizUseCase.getQuizById(103))

        val currentTime = System.currentTimeMillis() / 1000
        val savedQuiz1DataUpdate = savedQuiz1?.dataUpdate?.toLongOrNull() ?: 0L
        val savedQuiz3DataUpdate = savedQuiz3?.dataUpdate?.toLongOrNull() ?: 0L
        val savedQuiz4DataUpdate = savedQuiz4?.dataUpdate?.toLongOrNull() ?: 0L

        val quizEntity1DataUpdate = Quiz1.quizEntity1.dataUpdate.toLongOrNull() ?: 0L
        val quizEntity3DataUpdate = Quiz3.quizEntity3.dataUpdate.toLongOrNull() ?: 0L
        val quizEntity4DataUpdate = Quiz4.quizEntity4.dataUpdate.toLongOrNull() ?: 0L

        Log.e("testPushAndFetchQuiz", "3 ${questionUseCase.getQuestionByIdQuiz(101).size}")
        assertTrue(
            "Временные метки отличаются более чем на 200 секунд",
            abs(currentTime - savedQuiz1DataUpdate) <= 200
        )
        assertTrue(
            "Временные метки отличаются более чем на 200 секунд",
            abs(currentTime - savedQuiz3DataUpdate) <= 200
        )
        assertTrue(
            "Временные метки отличаются более чем на 200 секунд",
            abs(currentTime - savedQuiz4DataUpdate) <= 200
        )

        assertEquals(
            Quiz1.quizEntity1.copy(id = 101, dataUpdate = savedQuiz1?.dataUpdate ?: ""),
            savedQuiz1
        )
        assertEquals(
            Quiz3.quizEntity3.copy(id = 102, dataUpdate = savedQuiz3?.dataUpdate ?: ""),
            savedQuiz3
        )
        assertEquals(
            Quiz4.quizEntity4.copy(id = 103, dataUpdate = savedQuiz4?.dataUpdate ?: ""),
            savedQuiz4
        )

        structureUseCase.syncStructureDataANDquizzes()
        kotlinx.coroutines.delay(10000)

        assertThat(StructureData1.structureData)
            .usingRecursiveComparison()
            .ignoringFieldsMatchingRegexes(".*\\.id$", ".*\\.idQuiz$", ".*\\.dataUpdate$")
            .isEqualTo(structureUseCase.getStructureData())

        val savedQuestions1 = questionUseCase.getQuestionByIdQuiz(101)
        val savedQuestions3 = questionUseCase.getQuestionByIdQuiz(102)
        val savedQuestions4 = questionUseCase.getQuestionByIdQuiz(103)

        assertEquals(
            "Размеры списков не совпадают",
            savedQuestions1.size,
            Quiz1.questionsEntity.size
        )
        assertEquals(
            "Размеры списков не совпадают",
            savedQuestions3.size,
            Quiz3.questionsEntityAfrica.size
        )
        assertEquals(
            "Размеры списков не совпадают",
            savedQuestions4.size,
            Quiz4.questionsEntityNorthAmerica.size
        )

        for (expected in savedQuestions1) {
            val matchingQuestion1 = savedQuestions1.find { saved ->
                saved.numQuestion == expected.numQuestion &&
                        saved.language == expected.language &&
                        saved.hardQuestion == expected.hardQuestion
            }
            assertNotNull("Не найдено совпадение для: $expected", matchingQuestion1)
            matchingQuestion1?.let { saved ->
                assertThat(saved)
                    .usingRecursiveComparison()
                    .ignoringFields("id", "idQuiz")
                    .isEqualTo(expected)
            }
        }

        for (expected in savedQuestions3) {
            val matchingQuestion3 = savedQuestions3.find { saved ->
                saved.numQuestion == expected.numQuestion &&
                        saved.language == expected.language &&
                        saved.hardQuestion == expected.hardQuestion
            }
            assertNotNull("Не найдено совпадение для: $expected", matchingQuestion3)
            matchingQuestion3?.let { saved ->
                assertThat(saved)
                    .usingRecursiveComparison()
                    .ignoringFields("id", "idQuiz")
                    .isEqualTo(expected)
            }
        }

        for (expected in savedQuestions4) {
            val matchingQuestion4 = savedQuestions4.find { saved ->
                saved.numQuestion == expected.numQuestion &&
                        saved.language == expected.language &&
                        saved.hardQuestion == expected.hardQuestion
            }
            assertNotNull("Не найдено совпадение для: $expected", matchingQuestion4)
            matchingQuestion4?.let { saved ->
                assertThat(saved)
                    .usingRecursiveComparison()
                    .ignoringFields("id", "idQuiz")
                    .isEqualTo(expected)
            }
        }

    }
}