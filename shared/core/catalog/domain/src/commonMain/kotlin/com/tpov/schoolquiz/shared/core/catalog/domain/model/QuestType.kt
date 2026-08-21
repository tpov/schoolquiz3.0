package com.tpov.schoolquiz.shared.core.catalog.domain.model

/**
 * What kind of content a quest is.
 *
 * The type lives on the catalog: the author picks a catalog as the first step of creation, so the
 * type is known before a single question is written and the editor can adapt to it. On publication
 * the server copies it onto the quest itself, so neither the server nor the runner has to look the
 * catalog up to know how a quest behaves.
 *
 * This replaces comparing `catalogId == "courses"`, which was spread across the create screen, the
 * quest list and the seed scripts — three places that had to agree by convention alone.
 *
 * Note that "home" is *not* a type: it is a shelf in `visibleOn` marking content shown on the home
 * screen for everyone.
 */
enum class QuestType {
    /** Scored quiz: arena, tournaments, ratings. The default for anything unmarked. */
    REGULAR,

    /** Course: exam mode, certificate on completion, spaced repetition; published to the archive. */
    COURSE,

    /** Survey: no right answers and no score — the result is the distribution of the answers. */
    SURVEY,

    ;

    companion object {
        /**
         * Parses a stored value. Unknown or missing values fall back to [REGULAR] so content
         * written by a newer client never disappears from an older one.
         */
        fun fromStorage(value: String?): QuestType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: REGULAR
    }
}
