package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic

internal const val FILL_BLANK_RUNTIME_MARKER = "___"

internal data class FillBlankAnswerSpec(
    val text: String,
    val isProtected: Boolean = false,
)

internal data class FillBlankVisualSegment(
    val text: String,
    val isBlank: Boolean,
    val isProtected: Boolean,
)

internal fun extractFillBlankAnswers(text: String): List<FillBlankAnswerSpec> =
    parseAuthorMarkup(text)
        .segments
        .filter { it.isBlank }
        .map { segment ->
            FillBlankAnswerSpec(
                text = segment.text.trim(),
                isProtected = segment.isProtected,
            )
        }
        .filter { it.text.isNotBlank() }

internal fun fillBlankCandidateCountIsValid(
    answerCount: Int,
    distractorCount: Int,
): Boolean {
    val totalCount = answerCount + distractorCount
    return answerCount in FILL_BLANK_MIN_ANSWERS..FILL_BLANK_MAX_ANSWERS &&
        (totalCount == FILL_BLANK_MIN_CANDIDATES || totalCount == FILL_BLANK_MAX_CANDIDATES)
}

internal fun containsAllFillBlankAnswers(
    text: String,
    answers: List<String>,
): Boolean = findFillBlankMatches(text, answers.toSpecs()) != null

@Suppress("ReturnCount")
internal fun buildFillBlankRuntimeText(
    text: String,
    answers: List<FillBlankAnswerSpec>,
): String? {
    val authorMarkup = parseAuthorMarkup(text)
    if (authorMarkup.hasMarkup) {
        val runtimeText =
            authorMarkup.segments
                .joinToString(separator = "") { segment ->
                    if (segment.isBlank) FILL_BLANK_RUNTIME_MARKER else segment.text
                }
                .trim()
        return runtimeText.takeIf { it.isNotBlank() && authorMarkup.segments.any { segment -> segment.isBlank } }
    }

    val matches = findFillBlankMatches(text, answers) ?: return null
    return matches
        .asReversed()
        .fold(text.trim()) { current, match ->
            current.replaceRange(
                startIndex = match.start,
                endIndex = match.end,
                replacement = FILL_BLANK_RUNTIME_MARKER,
            )
        }
}

@Suppress("ReturnCount")
internal fun buildFillBlankVisualSegments(
    text: String,
    answers: List<FillBlankAnswerSpec>,
): List<FillBlankVisualSegment> {
    val authorMarkup = parseAuthorMarkup(text)
    if (authorMarkup.hasMarkup) return authorMarkup.segments

    val normalizedText = text.trim()
    val matches =
        findFillBlankMatches(normalizedText, answers)
            ?: return listOf(
                FillBlankVisualSegment(
                    text = normalizedText,
                    isBlank = false,
                    isProtected = false,
                ),
            )
    val result = mutableListOf<FillBlankVisualSegment>()
    var cursor = 0
    matches.forEach { match ->
        if (match.start > cursor) {
            result +=
                FillBlankVisualSegment(
                    text = normalizedText.substring(cursor, match.start),
                    isBlank = false,
                    isProtected = false,
                )
        }
        result +=
            FillBlankVisualSegment(
                text = normalizedText.substring(match.start, match.end),
                isBlank = true,
                isProtected = match.answer.isProtected,
            )
        cursor = match.end
    }
    if (cursor < normalizedText.length) {
        result +=
            FillBlankVisualSegment(
                text = normalizedText.substring(cursor),
                isBlank = false,
                isProtected = false,
            )
    }
    return result
}

internal fun restoreFillBlankAuthorText(
    runtimeText: String,
    answers: List<String>,
    protectedTextSegments: List<String> = emptyList(),
): String {
    val withAnswers =
        answers.fold(runtimeText) { current, answer ->
            val marker =
                if (protectedTextSegments.any { it.equals(answer, ignoreCase = true) }) {
                    "***$answer***"
                } else {
                    "**$answer**"
                }
            current.replaceFirst(FILL_BLANK_RUNTIME_MARKER, marker)
        }
    return protectedTextSegments
        .filterNot { protectedSegment ->
            answers.any { answer -> answer.equals(protectedSegment, ignoreCase = true) }
        }
        .fold(withAnswers) { current, protectedSegment ->
            current.replaceFirst(protectedSegment, "*$protectedSegment*")
        }
}

internal fun extractProtectedTextSegments(
    text: String,
    answers: List<FillBlankAnswerSpec> = emptyList(),
): List<String> =
    buildFillBlankVisualSegments(text = text, answers = answers)
        .asSequence()
        .filter { it.isProtected }
        .map { it.text.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .toList()

internal fun orderFillBlankAnswersByText(
    text: String,
    answers: List<FillBlankAnswerSpec>,
): List<FillBlankAnswerSpec>? {
    val authorAnswers = extractFillBlankAnswers(text)
    if (authorAnswers.isNotEmpty()) return authorAnswers
    return findFillBlankMatches(text, answers)?.map { it.answer }
}

private const val FILL_BLANK_MIN_ANSWERS = 1
private const val FILL_BLANK_MAX_ANSWERS = 3
private const val FILL_BLANK_MIN_CANDIDATES = 5
private const val FILL_BLANK_MAX_CANDIDATES = 10

private data class FillBlankMatch(
    val answer: FillBlankAnswerSpec,
    val start: Int,
    val end: Int,
)

private data class FillBlankMarkup(
    val segments: List<FillBlankVisualSegment>,
    val hasMarkup: Boolean,
)

@Suppress("LoopWithTooManyJumpStatements")
private fun parseAuthorMarkup(text: String): FillBlankMarkup {
    val source = text.trim()
    if (source.isBlank()) {
        return FillBlankMarkup(
            segments =
                listOf(
                    FillBlankVisualSegment(
                        text = "",
                        isBlank = false,
                        isProtected = false,
                    ),
                ),
            hasMarkup = false,
        )
    }

    val segments = mutableListOf<FillBlankVisualSegment>()
    val plain = StringBuilder()
    var index = 0
    var hasMarkup = false

    fun flushPlain() {
        if (plain.isNotEmpty()) {
            segments +=
                FillBlankVisualSegment(
                    text = plain.toString(),
                    isBlank = false,
                    isProtected = false,
                )
            plain.clear()
        }
    }
    while (index < source.length) {
        val token =
            when {
                source.startsWith("***", index) -> "***"
                source.startsWith("**", index) -> "**"
                source.startsWith("*", index) -> "*"
                else -> null
            }
        if (token == null) {
            plain.append(source[index])
            index += 1
            continue
        }

        val contentStart = index + token.length
        val contentEnd = source.indexOf(token, startIndex = contentStart)
        if (contentEnd <= contentStart) {
            plain.append(token)
            index += token.length
            continue
        }

        val content = source.substring(contentStart, contentEnd).trim()
        if (content.isBlank()) {
            plain.append(source.substring(index, contentEnd + token.length))
            index = contentEnd + token.length
            continue
        }

        flushPlain()
        hasMarkup = true
        segments +=
            FillBlankVisualSegment(
                text = content,
                isBlank = token.length >= 2,
                isProtected = token.length == 1 || token.length == 3,
            )
        index = contentEnd + token.length
    }
    flushPlain()
    return FillBlankMarkup(
        segments =
            segments.ifEmpty {
                listOf(
                    FillBlankVisualSegment(
                        text = source,
                        isBlank = false,
                        isProtected = false,
                    ),
                )
            },
        hasMarkup = hasMarkup,
    )
}

@Suppress("ReturnCount")
private fun findFillBlankMatches(
    text: String,
    answers: List<FillBlankAnswerSpec>,
): List<FillBlankMatch>? {
    val normalizedText = text.trim().takeIf { it.isNotBlank() } ?: return null
    val normalizedAnswers =
        answers
            .map { it.copy(text = it.text.trim()) }
            .filter { it.text.isNotBlank() }
    if (normalizedAnswers.isEmpty()) return null

    val claimedRanges = mutableListOf<IntRange>()
    val matches = mutableListOf<FillBlankMatch>()
    normalizedAnswers.forEach { answer ->
        var searchStart = 0
        var answerStart =
            normalizedText.indexOf(
                string = answer.text,
                startIndex = searchStart,
                ignoreCase = true,
            )
        while (
            answerStart >= 0 &&
            claimedRanges.any { it.overlaps(answerStart, answerStart + answer.text.length) }
        ) {
            searchStart = answerStart + 1
            answerStart =
                normalizedText.indexOf(
                    string = answer.text,
                    startIndex = searchStart,
                    ignoreCase = true,
                )
        }
        if (answerStart < 0) return null
        val answerEnd = answerStart + answer.text.length
        claimedRanges += answerStart until answerEnd
        matches += FillBlankMatch(answer = answer, start = answerStart, end = answerEnd)
    }
    return matches.sortedBy { it.start }
}

private fun List<String>.toSpecs(): List<FillBlankAnswerSpec> = map { FillBlankAnswerSpec(text = it) }

private fun IntRange.overlaps(
    start: Int,
    end: Int,
): Boolean = first < end && start < last + 1
