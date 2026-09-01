package com.tpov.schoolquiz.shared.core.question_schema

import kotlinx.serialization.ExperimentalSerializationApi
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
     * Dispatch on the payload's own discriminator, then decode with the matching hierarchy.
     *
     * The two hierarchies register disjoint discriminator sets — `SingleChoice` against
     * `SingleChoiceRedacted`, and no redacted `Survey` at all — so the `type` key alone says which
     * decoder can possibly read a payload. That set is read off [RedactedQuestionContent]'s own
     * serializer rather than listed here, so a fifth redacted variant is routed the day it is
     * declared instead of the day somebody remembers this file.
     *
     * Deciding first, rather than trying both and keeping whichever succeeded, is what makes a
     * *broken* redacted payload diagnosable. `{"type":"FillBlankRedacted"}` with no `candidates`
     * cannot be read by either hierarchy; reporting it through [parse]'s exception would answer
     * "FillBlankRedacted is not a registered QuestionContent" — naming a contract the payload has
     * nothing to do with, which is precisely the wrong-contract diagnosis the previous slice
     * removed from the legacy branch. A payload wearing a redacted discriminator is answered by the
     * redacted decoder, whether it succeeds or fails.
     */
    override fun parseForDisplay(
        payload: String,
        fallbackId: String,
        fallbackText: String,
        fallbackDifficulty: Difficulty,
    ): Result<QuestionDisplay> {
        if (discriminatorOf(payload) in redactedDiscriminators) return parseRedacted(payload)
        return parse(payload, fallbackId, fallbackText, fallbackDifficulty)
    }

    /** The payload as a [RedactedQuestionContent], keeping the decoder's own failure. */
    private fun parseRedacted(payload: String): Result<QuestionDisplay> {
        return try {
            Result.success(json.decodeFromString<RedactedQuestionContent>(payload))
        } catch (e: IllegalArgumentException) {
            // SerializationException extends IllegalArgumentException, so this one catch covers an
            // unreadable payload and a value outside a declared type alike.
            Result.failure(e)
        }
    }

    /** The payload's `type`, or null when there is no readable one. */
    private fun discriminatorOf(payload: String): String? =
        jsonObjectOrNull(payload)?.get("type")?.jsonPrimitive?.contentOrNull

    /**
     * The payload as a [JsonObject] when — and only when — its `type` is the one legacy format this
     * parser reads. Anything else, malformed input included, is not legacy and gets no fallback.
     *
     * Named for the single format on purpose: a second legacy shape cannot be added by widening a
     * set here, it has to be dispatched, and the name says so.
     */
    private fun legacySingleChoiceOrNull(payload: String): JsonObject? {
        val obj = jsonObjectOrNull(payload) ?: return null
        val type = obj["type"]?.jsonPrimitive?.contentOrNull
        return if (type == LEGACY_SINGLE_CHOICE) obj else null
    }

    /** The payload as a [JsonObject], or null when it is not a readable JSON object. */
    private fun jsonObjectOrNull(payload: String): JsonObject? {
        return try {
            json.decodeFromString<JsonObject>(payload)
        } catch (e: IllegalArgumentException) {
            // SerializationException extends IllegalArgumentException; one catch covers both.
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

    /**
     * The `@SerialName` of every [RedactedQuestionContent] variant, read off the sealed serializer.
     *
     * Derived rather than listed for the reason `RedactedQuestionWireTest.declaredVariants` gives:
     * a hand-kept copy of the four names is a fifth variant waiting to be added and routed nowhere.
     * A sealed serializer's descriptor is `["type": String, "value": <contextual>]`, and the
     * contextual element's element names are the subclasses' serial names.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private val redactedDiscriminators: Set<String> = run {
        val variants = RedactedQuestionContent.serializer().descriptor.getElementDescriptor(1)
        (0 until variants.elementsCount).mapTo(mutableSetOf()) { variants.getElementName(it) }
    }

    private companion object {
        /** The only pre-ADR-0003 shape still in the corpus. Same key as the discriminator. */
        const val LEGACY_SINGLE_CHOICE = "single-choice"
    }
}
