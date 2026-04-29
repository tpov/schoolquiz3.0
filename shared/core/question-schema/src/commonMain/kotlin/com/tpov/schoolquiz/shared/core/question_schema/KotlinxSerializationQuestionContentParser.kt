package com.tpov.schoolquiz.shared.core.question_schema

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class KotlinxSerializationQuestionContentParser : QuestionContentParser {

    private val json = Json { ignoreUnknownKeys = true }

    override fun parse(payload: String): Result<QuestionContent> {
        return try {
            Result.success(json.decodeFromString<QuestionContent>(payload))
        } catch (e: SerializationException) {
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }

    override fun parse(
        payload: String,
        fallbackId: String,
        fallbackText: String,
        fallbackDifficulty: Difficulty,
    ): Result<QuestionContent> {
        val newFormat = parse(payload)
        if (newFormat.isSuccess) return newFormat
        return parseLegacy(payload, fallbackId, fallbackText, fallbackDifficulty)
    }

    private fun parseLegacy(
        payload: String,
        fallbackId: String,
        fallbackText: String,
        fallbackDifficulty: Difficulty,
    ): Result<QuestionContent> {
        return try {
            val obj = json.decodeFromString<JsonObject>(payload)
            val type = obj["type"]?.jsonPrimitive?.content
                ?: return Result.failure(SerializationException("Legacy payload missing 'type'"))
            when (type) {
                "single-choice" -> parseLegacySingleChoice(obj, fallbackId, fallbackText, fallbackDifficulty)
                else -> Result.failure(SerializationException("Unsupported legacy question type: $type"))
            }
        } catch (e: SerializationException) {
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }

    private fun parseLegacySingleChoice(
        obj: JsonObject,
        fallbackId: String,
        fallbackText: String,
        fallbackDifficulty: Difficulty,
    ): Result<QuestionContent> {
        return try {
            val optionTexts = obj["options"]?.jsonArray?.map { it.jsonPrimitive.content }
                ?: return Result.failure(SerializationException("Legacy single-choice missing 'options'"))
            val correctIndex = obj["correctIndex"]?.jsonPrimitive?.int
                ?: return Result.failure(SerializationException("Legacy single-choice missing 'correctIndex'"))

            if (optionTexts.size !in 2..8) {
                return Result.failure(
                    IllegalArgumentException("Legacy single-choice options count must be 2..8, got ${optionTexts.size}")
                )
            }
            if (correctIndex !in optionTexts.indices) {
                return Result.failure(
                    IllegalArgumentException(
                        "Legacy single-choice correctIndex=$correctIndex out of [0, ${optionTexts.size})"
                    )
                )
            }

            val options = optionTexts.mapIndexed { idx, text ->
                QuestionContent.Option(id = OptionId("opt-$idx"), text = text)
            }

            Result.success(
                QuestionContent.SingleChoice(
                    id = fallbackId.ifBlank { "legacy-unknown" },
                    difficulty = fallbackDifficulty,
                    text = fallbackText.ifBlank { "?" },
                    imageUrl = null,
                    options = options,
                    correctOptionId = OptionId("opt-$correctIndex"),
                )
            )
        } catch (e: SerializationException) {
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }
}
