package com.tpov.schoolquiz.shared.core.sync

/**
 * Место, на котором остановилось чтение журнала изменений.
 *
 * Пара, а не одно время (AD-31). Строгое сравнение по одному `changedAtMs` теряет записи,
 * записанные в одну миллисекунду: две сущности, изменённые одним пакетом, получают один
 * таймстемп, курсор встаёт на него, и вторая уже никогда не будет прочитана. Второй половиной
 * идёт id документа — он уникален, поэтому пара упорядочивает журнал строго.
 *
 * Сравнение лексикографическое: сначала время, при равенстве — id.
 */
data class SyncCursor(
    val changedAtMs: Long,
    val docId: String = "",
) : Comparable<SyncCursor> {

    init {
        require(changedAtMs >= 0) { "changedAtMs must be non-negative" }
    }

    override fun compareTo(other: SyncCursor): Int {
        val byTime = changedAtMs.compareTo(other.changedAtMs)
        return if (byTime != 0) byTime else docId.compareTo(other.docId)
    }

    companion object {
        /** Начало журнала: читать всё. Оно же — результат принудительного сброса (AD-30). */
        val BEGINNING = SyncCursor(changedAtMs = 0L, docId = "")
    }
}

/**
 * Одна страница журнала.
 *
 * Читать журнал целиком нельзя: полная перепубликация большого курса приходит одним ответом, и
 * его размер ограничен только тем, сколько успел изменить автор.
 *
 * @property changes прочитанное, в порядке курсора.
 * @property nextCursor куда встать после успешного применения. Пусто, если страница пустая.
 * @property hasMore есть ли ещё — страница пришла полной, значит стоит зайти снова.
 * @property unreadable сколько записей на этой странице читатель не понял и пропустил.
 */
data class SyncChangePage<T>(
    val changes: List<T>,
    val nextCursor: SyncCursor?,
    val hasMore: Boolean,
    /**
     * Пропущенные записи журнала.
     *
     * В журналах сосуществуют три формы документа, и читатель терпит их все, а непонятую запись
     * молча отбрасывает (AD-11). Терпимость снимается только после того, как backfill приведёт
     * записанное к одной форме, — но узнать, что он закончил, было не из чего: пропуск не
     * оставлял следа. Это число и есть след.
     *
     * Для игрока это значит, что об изменении узла ему сказали, а прочитать его не смогли: узел
     * остался прежним, и «Синхронизировать» этого не исправит.
     */
    val unreadable: Int = 0,
) {
    companion object {
        /** Журнал кончился. */
        fun <T> empty(): SyncChangePage<T> = SyncChangePage(emptyList(), null, hasMore = false)
    }
}

/** Сколько записей журнала читаем за раз. */
const val SYNC_PAGE_SIZE: Int = 200
