package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

/** A read request, never consent to spend. Owner and continuation proof stay local, not in the body. */
data class ArenaLessonCatalogRequest(
    val ownerUid: String,
    val pageSize: Int = ArenaLessonCatalogBounds.DEFAULT_PAGE_SIZE,
    val cursor: ArenaLessonCatalogCursor? = null,
) {
    init {
        requireCatalogId(ownerUid)
        require(pageSize in 1..ArenaLessonCatalogBounds.MAX_PAGE_SIZE)
        require(cursor == null || cursor.ownerUid == ownerUid)
    }
}

/** Public metadata only. PREPARE_REQUIRED does not promise a bank, launch permission or reserved price. */
data class ArenaLessonCatalogPage(
    val ownerUid: String,
    val snapshotFingerprint: String,
    val serverNowMs: Long,
    val totalEligibleLessons: Int,
    val lessons: List<ArenaLessonCatalogCard>,
    val nextCursor: ArenaLessonCatalogCursor?,
) {
    val catalogVersion: String get() = ArenaLessonCatalogBounds.VERSION
    val entryAvailability: String get() = ArenaLessonCatalogBounds.PREPARE_REQUIRED
}

data class ArenaLessonCatalogCard(
    val lessonId: String,
    val contentVersion: Long,
    val title: String?,
    val authorUid: String,
    val rating: ArenaLessonCatalogRating,
    val priceNolics: Int,
    val placement: ArenaLessonCatalogPlacement,
)

data class ArenaLessonCatalogRating(
    val sum: Long,
    val count: Long,
    val weightedScore: ArenaLessonCatalogScore,
    val version: Long,
)

/** JS-safe nonnegative numerator / positive denominator; compare without Double or overflowing products. */
data class ArenaLessonCatalogScore(val numerator: Long, val denominator: Long) : Comparable<ArenaLessonCatalogScore> {
    init {
        require(numerator in 0..ArenaLessonCatalogBounds.MAX_SAFE_INTEGER)
        require(denominator in 1..ArenaLessonCatalogBounds.MAX_SAFE_INTEGER)
    }

    override fun compareTo(other: ArenaLessonCatalogScore): Int {
        var leftNumerator = numerator
        var leftDenominator = denominator
        var rightNumerator = other.numerator
        var rightDenominator = other.denominator
        var direction = 1
        var result: Int? = null
        while (result == null) {
            val wholeComparison = (leftNumerator / leftDenominator).compareTo(rightNumerator / rightDenominator)
            val leftRemainder = leftNumerator % leftDenominator
            val rightRemainder = rightNumerator % rightDenominator
            when {
                wholeComparison != 0 -> result = direction * wholeComparison
                leftRemainder == 0L || rightRemainder == 0L ->
                    result = direction * leftRemainder.compareTo(rightRemainder)
                else -> {
                    // Removing equal whole parts then reciprocating reverses the comparison.
                    // Denominators strictly decrease; all arithmetic stays inside positive Long.
                    leftNumerator = leftDenominator
                    leftDenominator = leftRemainder
                    rightNumerator = rightDenominator
                    rightDenominator = rightRemainder
                    direction = -direction
                }
            }
        }
        return result
    }
}

data class ArenaLessonCatalogPlacement(
    val previousShelf: ArenaLessonCatalogShelf?,
    val version: Long?,
    val updatedAtMs: Long?,
) {
    val shelf: ArenaLessonCatalogShelf get() = ArenaLessonCatalogShelf.ARENA
}

enum class ArenaLessonCatalogShelf { REVIEW, ARENA, TOURNAMENT, HOME, RETIRED, REJECTED }

/** Derived continuation proof. Only [token] goes to the server; none of this is purchase authority. */
data class ArenaLessonCatalogCursor(
    val token: String,
    val ownerUid: String,
    val snapshotFingerprint: String,
    val after: ArenaLessonCatalogCard,
    val totalEligibleLessons: Int,
    val seenLessonIds: Set<String>,
    val ratedPriceAtBoundary: Int?,
) {
    init {
        requireCatalogId(ownerUid)
        require(token.length in 1..ArenaLessonCatalogBounds.MAX_CURSOR_BYTES)
        require(token.all { it.isLetterOrDigit() && it.code < ASCII_BOUND || it == '-' || it == '_' })
        require(snapshotFingerprint.length == SHA256_HEX_LENGTH)
        require(snapshotFingerprint.all { it in '0'..'9' || it in 'a'..'f' })
        require(totalEligibleLessons in 1..ArenaLessonCatalogBounds.MAX_LESSONS)
        require(seenLessonIds.size in 1 until totalEligibleLessons && after.lessonId in seenLessonIds)
        seenLessonIds.forEach(::requireCatalogId)
        require(ratedPriceAtBoundary == null || ratedPriceAtBoundary in 1..ArenaLessonCatalogBounds.MAX_PRICE)
    }
}

/** Closed reasons, not message-text matching and not an instruction to create an anonymous account. */
enum class ArenaLessonCatalogFailureKind {
    ACCOUNT_UNAVAILABLE,
    ACCOUNT_CHANGED,
    INVALID_REQUEST,
    MALFORMED_RESPONSE,
    CATALOG_CHANGED,
    CATALOG_TOO_LARGE,
    CATALOG_INVALID,
    SERVER_FAILURE,
}

class ArenaLessonCatalogFailure(val kind: ArenaLessonCatalogFailureKind) : Exception(kind.name)

object ArenaLessonCatalogBounds {
    const val VERSION = "arena-lesson-catalog-v1"
    const val PREPARE_REQUIRED = "PREPARE_REQUIRED"
    const val DEFAULT_PAGE_SIZE = 20
    const val MAX_PAGE_SIZE = 50
    const val MAX_LESSONS = 1000
    const val MAX_PRICE = 100
    const val MAX_RESPONSE_BYTES = 900000
    const val MAX_CURSOR_BYTES = 4096
    const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L
    const val PRIOR_SUM = 40L
    const val PRIOR_COUNT = 20L
}

private fun requireCatalogId(value: String) {
    require(value.isNotBlank() && '/' !in value && value != "." && value != "..")
    require(!(value.startsWith("__") && value.endsWith("__")))
    require(value.encodeToByteArray().size <= MAX_DOCUMENT_ID_BYTES)
}

private const val MAX_DOCUMENT_ID_BYTES = 1500
private const val SHA256_HEX_LENGTH = 64
private const val ASCII_BOUND = 128
