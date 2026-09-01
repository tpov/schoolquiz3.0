package com.tpov.schoolquiz.shared.core.question_schema

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
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
        // Only a payload whose "type" actually names a legacy format goes down the legacy branch.
        //
        // The legacy `type` key and the polymorphic discriminator are the same key, so every
        // payload that failed above still has one — and answering "Unsupported legacy question
        // type: SingleChoiceRedacted" for a redacted payload names the wrong contract entirely and
        // buries the accurate diagnosis. The exception from the polymorphic decoder already names
        // the unregistered discriminator, so when the payload is not legacy it is the one kept.
        val legacy = legacySingleChoiceOrNull(payload) ?: return newFormat
        return parseLegacySingleChoice(legacy, fallbackId, fallbackText, fallbackDifficulty)
    }

    /**
     * The payload as a [JsonObject] when — and only when — its `type` is the one legacy format this
     * parser reads. Anything else, malformed input included, is not legacy and gets no fallback.
     *
     * Named for the single format on purpose: a second legacy shape cannot be added by widening a
     * set here, it has to be dispatched, and the name says so.
     */
    private fun legacySingleChoiceOrNull(payload: String): JsonObject? {
        return try {
            val obj = json.decodeFromString<JsonObject>(payload)
            val type = obj["type"]?.jsonPrimitive?.contentOrNull
            if (type == LEGACY_SINGLE_CHOICE) obj else null
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
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

    private companion object {
        /** The only pre-ADR-0003 shape still in the corpus. Same key as the discriminator. */
        const val LEGACY_SINGLE_CHOICE = "single-choice"
    }
}
