package com.tpov.schoolquiz.shared.core.scoring

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Отведённое время урока — одна формула на двух языках.
 *
 * Пара к [UnlockPricingParityTest]. Тот набор пиннит перевод секунд в нолики, этот — сами секунды.
 * Обе половины нужны, чтобы замок в списке уроков показывал ровно то число, которое сервер потом
 * спишет: считает клиент, а списывает сервер, и разойтись им нельзя.
 *
 * JVM-тест, а не общий: файл с диска общий код не видит.
 */
class LessonAllocatedSecondsParityTest {

    @Serializable
    private data class FixtureQuestion(
        val id: String,
        val archived: Boolean,
        val difficulty: String,
        val charsCount: Int,
    )

    @Serializable
    private data class Case(
        val name: String,
        val questions: List<FixtureQuestion>,
        val easyAllocatedSeconds: Long,
        val hardAllocatedSeconds: Long,
    )

    private val fixtures: List<Case> by lazy {
        val file = File("../../../config/lesson-allocated-seconds-fixtures.json")
        assertTrue(file.exists(), "нет общего набора фикстур: ${file.absolutePath}")
        Json { ignoreUnknownKeys = true }.decodeFromString<List<Case>>(file.readText())
    }

    /** Пустая строка — та самая сложность, которую сервер читает как лёгкую, а клиент не читает вовсе. */
    private fun difficultyOf(name: String): Difficulty? =
        Difficulty.entries.firstOrNull { it.name == name.uppercase() }

    private fun questionsOf(case: Case) =
        case.questions.map {
            LessonAllocatedSeconds.Question(
                id = it.id,
                charsCount = it.charsCount,
                difficulty = difficultyOf(it.difficulty),
                archived = it.archived,
            )
        }

    @Test
    fun `given the shared fixtures then every lesson is worth what the server says`() {
        assertTrue(fixtures.isNotEmpty(), "набор фикстур пуст — значит проверять нечего")

        val mismatches =
            fixtures.mapNotNull { case ->
                val questions = questionsOf(case)
                val easy = LessonAllocatedSeconds.of(questions, hard = false)
                val hard = LessonAllocatedSeconds.of(questions, hard = true)
                if (easy == case.easyAllocatedSeconds && hard == case.hardAllocatedSeconds) {
                    null
                } else {
                    "${case.name}: сервер ${case.easyAllocatedSeconds}/${case.hardAllocatedSeconds}, " +
                        "клиент $easy/$hard"
                }
            }

        assertEquals(emptyList(), mismatches, "клиент и сервер считают урок по-разному:\n" + mismatches.joinToString("\n"))
    }

    @Test
    fun `given a lesson longer than the pool then its worth stops growing with the count`() {
        // Иначе урок из ста одинаковых вопросов стоил бы впятеро против урока из двадцати, хотя
        // игроку в обоих покажут двадцать.
        val short = List(20) { question("q$it", chars = 120) }
        val long = List(100) { question("q$it", chars = 120) }

        assertEquals(LessonAllocatedSeconds.of(short, hard = false), LessonAllocatedSeconds.of(long, hard = false))
    }

    @Test
    fun `given a lesson translated three ways then it is worth one lesson`() {
        val monolingual = listOf(question("q1", chars = 120))
        val translated = listOf(question("q1", 120), question("q1__ru", 120), question("q1__en", 120))

        assertEquals(
            LessonAllocatedSeconds.of(monolingual, hard = false),
            LessonAllocatedSeconds.of(translated, hard = false),
        )
    }

    @Test
    fun `given a difficulty nobody could read then it is counted as easy, as the server counts it`() {
        // Расхождение, названное в QuestionDisplay.difficultyOrNull: сервер читает отсутствующую
        // сложность как лёгкую и по ней списывает. Цена обязана совпасть со списанием.
        val unreadable = listOf(question("q1", chars = 120, difficulty = null))

        assertEquals(
            LessonAllocatedSeconds.of(listOf(question("q1", 120)), hard = false),
            LessonAllocatedSeconds.of(unreadable, hard = false),
        )
        assertEquals(0L, LessonAllocatedSeconds.of(unreadable, hard = true))
    }

    private fun question(
        id: String,
        chars: Int,
        difficulty: Difficulty? = Difficulty.EASY,
        archived: Boolean = false,
    ) = LessonAllocatedSeconds.Question(id = id, charsCount = chars, difficulty = difficulty, archived = archived)
}
