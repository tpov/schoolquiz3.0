package com.tpov.schoolquiz.shared.feature.economy.domain.logic

import com.tpov.schoolquiz.shared.feature.economy.domain.model.ActivityKind

/**
 * Какого вида активность у квеста с такими полками.
 *
 * Зеркало `activityKindForQuest` из `functions/activity-kind.js`. Списывает по-прежнему сервер и
 * выводит вид по своим документам — здесь то же правило нужно, чтобы **показать** цену до запуска
 * и не дать начать то, на что не хватает. Ошибись зеркало — игрок увидит одно число, а спишется
 * другое, поэтому обе стороны читают общий набор фикстур.
 *
 * Квест на нескольких полках считается по самой дорогой из них: это единственное направление
 * ошибки, которое не открывает дыру. Контрольная и экзамен сюда не приходят — у них свои серверные
 * сессии, и вид известен в момент открытия сессии, а не по полке квеста.
 */
object ActivityKindRule {

    /** Полки, от дорогой к дешёвой. */
    val SHELF_PRECEDENCE: List<String> = listOf("tournamentFinal", "tournament", "arena", "home", "archive")

    private val KIND_BY_SHELF =
        mapOf(
            "tournamentFinal" to ActivityKind.TOURNAMENT,
            "tournament" to ActivityKind.TOURNAMENT,
            "arena" to ActivityKind.ARENA,
            "home" to ActivityKind.ORDINARY_LESSON,
            "archive" to ActivityKind.ORDINARY_LESSON,
        )

    /**
     * Вид по полкам квеста.
     *
     * @param visibleOn полки, как они лежат в квесте. Регистр и пробелы прощаются: эти данные
     *   писались руками и скриптами годами.
     * @param isPrivate неопубликованный квест автора — всегда обычный урок, полок у него нет.
     */
    fun of(
        visibleOn: Collection<String>,
        isPrivate: Boolean = false,
    ): ActivityKind {
        if (isPrivate) return ActivityKind.ORDINARY_LESSON
        val shelves = shelvesOf(visibleOn)
        // Снятый со всех полок квест — всё ещё урок: играть его можно, пока он на устройстве.
        return shelves.firstOrNull()?.let { KIND_BY_SHELF.getValue(it) } ?: ActivityKind.ORDINARY_LESSON
    }

    /** Полки в каноническом написании, без мусора и повторов, от дорогой к дешёвой. */
    fun shelvesOf(visibleOn: Collection<String>): List<String> {
        val seen = visibleOn.mapNotNull(::normalizeShelf).toSet()
        return SHELF_PRECEDENCE.filter { it in seen }
    }

    private fun normalizeShelf(value: String): String? {
        val lower = value.trim().lowercase()
        if (lower == "tournamentfinal") return "tournamentFinal"
        return SHELF_PRECEDENCE.firstOrNull { it == lower }
    }
}
