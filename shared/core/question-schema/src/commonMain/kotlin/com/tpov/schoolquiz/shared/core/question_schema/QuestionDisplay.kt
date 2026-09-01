package com.tpov.schoolquiz.shared.core.question_schema

/**
 * A question as something to be *shown* — the half that is the same whether or not the answer key
 * came with it.
 *
 * [QuestionContent] and [RedactedQuestionContent] are deliberately separate hierarchies: one means
 * "a question with its answer", the other "a question whose answer was taken off", and
 * [RedactedQuestionContent]'s own note explains at length why merging them would let the authoring
 * screens overwrite a stored question with its own public half. That separation is right, and it
 * left no way to say "render this, whichever kind it is". This interface is that way, and nothing
 * more: it names the members a renderer and the timer need, and stops there.
 *
 * Three properties of the declaration are load-bearing:
 *
 * 1. **Nothing here can reveal an answer — including through an order.** There is no
 *    `correctOptionId`, no count of correct options, no `blanks`, no `info`, and deliberately **no
 *    list of the option texts**. For [QuestionContent.Ordering] the stored order of `items` *is*
 *    the answer, and for [QuestionContent.FillBlank] the authoring component writes the correct
 *    candidates first — so a texts member would hand a renderer the answer to two of the five
 *    shapes while looking harmless. What the display side actually needed from those texts was
 *    their length, so that is what this exposes: [charsCount], a number, which is not orderable and
 *    reveals nothing. A renderer that needs the labels themselves must narrow to one of the two
 *    hierarchies and take responsibility for the shuffle.
 *
 * 2. **It adds nothing to the wire format.** This interface is not `@Serializable`, and every
 *    implementation of [choiceCharsCount] is a body `val` with a getter rather than a constructor
 *    parameter — so it has no backing field, kotlinx does not encode it, and it takes part in none
 *    of the `init` invariants. The bytes of both hierarchies are what they were.
 *
 * 3. **It is not sealed.** Sealing would buy an exhaustive `when` over the two implementors, at the
 *    price of forbidding a test double in any other module. Consumers that must distinguish the two
 *    can still `when (it) { is QuestionContent -> …; is RedactedQuestionContent -> … }`; they just
 *    write the `else` branch themselves, which is the honest thing for an open type.
 *
 * Nothing consumes this yet. It is the seam the runner will move onto, landed on its own so that
 * the move is a type change rather than a design.
 */
interface QuestionDisplay {

    /**
     * Null only for a redacted payload whose source carried no id of its own — which is every
     * question in the seed corpus, where the id lives on the wrapper document. See
     * [RedactedQuestionContent.id]. A [QuestionContent] always has one, and narrows this to
     * non-null.
     */
    val id: String?

    /** The question itself, as the player reads it. Never blank for a [QuestionContent]. */
    val text: String

    /** The illustration, or null. Worth [IMAGE_CHARS] to the timer when it is a non-empty string. */
    val imageUrl: String?

    /**
     * The characters in the question's own choices — the options of a choice question, the rows of
     * an ordering one, the word pool of a fill-blank — summed, never listed.
     *
     * The one thing each implementor has to answer for itself, and the only reason each of them
     * still touches its own texts. It is a length rather than the texts because a length cannot be
     * put back in order: see point 1 above.
     *
     * `protectedTextSegments` are **not** counted. Every one of them is by construction an answer
     * spelled out in full, and the server does not count them either.
     */
    val choiceCharsCount: Int

    /**
     * What a question is worth to the timer: its own text, its choices, and a flat [IMAGE_CHARS]
     * for an illustration.
     *
     * **This number is the contract with the server.** `questionCharsCount` in
     * `functions/lesson-reward.js` computes it independently, and the reward a player is paid, the
     * price of a lesson unlock and the seconds on the clock all come from it — so the two must
     * agree to the character, for every shape, in both hierarchies. Declared once here so that
     * there is one Kotlin copy of the formula rather than one per variant; `computeCharsCount` in
     * `RunnerLogic.kt` is the second copy, and it exists only until the runner moves onto this
     * type, at which point it is a deletion rather than an edit.
     *
     * **The image test is emptiness, not nullity, and that is deliberate.** The server writes
     * `content.imageUrl ? IMAGE_CHARS : 0`, and JavaScript reads `""` as false — so an empty-string
     * `imageUrl` is worth nothing there. `RunnerLogic.computeCharsCount` currently tests
     * `imageUrl != null` and would pay a hundred characters for the same question, which is a
     * client/server disagreement about a number the server pays real rewards from. The empty string
     * is the whole of the difference: for `null` and for any real URL the two rules agree, and no
     * question the app can author has an empty one — `DefaultQuestCreateComponent` normalises a
     * blank image path to `null` before it ever reaches a [QuestionContent]. So matching the server
     * here changes nothing about any existing question and closes the gap for anything the seed
     * scripts or the emitter might yet produce.
     */
    val charsCount: Int
        get() = text.length + choiceCharsCount + if (imageUrl.isNullOrEmpty()) 0 else IMAGE_CHARS

    /**
     * The difficulty when it can be read, null when it cannot.
     *
     * The nullable form is the only one both implementors can answer. [QuestionContent.difficulty]
     * is the [Difficulty] enum and always present; [RedactedQuestionContent.difficulty] is the
     * string the server published, which may be absent, empty, or a name the enum has no case for —
     * all three of which `question-redaction.js` is written to emit, and none of which may be
     * silently substituted, because substituting one would move a question between the easy and
     * hard pools and change its reward.
     *
     * **Known divergence from the server, deliberately left standing.** `lessonAllocatedSeconds`
     * (`functions/lesson-reward.js`) reads a question's pool as
     * `String(content.difficulty || "EASY").toUpperCase()`, so on the server an absent or empty
     * difficulty is **EASY** and is priced and paid as easy, while this property reports it as
     * unknown — a question that would then belong to neither pool on the client. Reporting unknown
     * is what the E2.6 specification froze ("reads as unknown rather than failing … belongs to
     * neither pool, which is the correct outcome"), and `RedactedQuestionWireTest` pins it, so it is
     * not something this slice may quietly change. It is recorded here because whoever moves the
     * runner onto this type has to decide it on purpose.
     */
    val difficultyOrNull: Difficulty?

    companion object {
        /** An illustration is worth this many characters. `IMAGE_CHARS` in `lesson-reward.js`. */
        const val IMAGE_CHARS: Int = 100
    }
}
