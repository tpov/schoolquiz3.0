package com.tpov.schoolquiz.shared.core.sync

/**
 * Проход по одному журналу: страницами, от сохранённого курсора, с продвижением после применения.
 *
 * Один цикл на четыре журнала. Правила у них общие и не должны расходиться:
 * - страница читается сразу после курсора-пары (AD-31);
 * - курсор двигается только после успешного применения страницы — обрыв на середине оставляет
 *   его на месте, и следующий проход перечитает ту же страницу целиком;
 * - полная страница — повод зайти снова, но не бесконечно: журнал пополняется во время чтения,
 *   и без потолка один проход мог бы не закончиться никогда. Остаток доедет следующим заходом;
 * - непонятая запись не пропадает молча: до приведения журналов она посчитана и курсор её
 *   проходит, после — останавливает проход отказом, и курсор остаётся на месте (AD-11).
 *
 * @param cursorId ключ курсора в [SyncStateRepository].
 * @param startAt откуда читать вместо сохранённого курсора; по умолчанию — сохранённый.
 * @param policy строгость разбора: [JournalReadPolicy.STRICT] превращает непонятую запись в отказ.
 * @param isReadable записи, не прошедшие проверку, считаются непонятыми и в [apply] не попадают.
 * @param fetch страница журнала после курсора.
 * @param apply применение страницы; отказ прерывает проход, курсор остаётся до этой страницы.
 * @return сколько записей журнала проход не понял, либо первая неудача применения.
 */
suspend fun <T> SyncStateRepository.syncJournal(
    cursorId: String,
    startAt: SyncCursor? = null,
    maxPages: Int = MAX_PAGES_PER_RUN,
    policy: JournalReadPolicy = JournalReadPolicy.TOLERANT,
    isReadable: (T) -> Boolean = { true },
    fetch: suspend (SyncCursor) -> SyncChangePage<T>,
    apply: suspend (List<T>) -> Result<Unit>,
): Result<Int> {
    var at = startAt ?: getCursor(cursorId)
    var pages = 0
    var unreadable = 0
    while (pages < maxPages) {
        val page = fetch(at)
        val changes = page.changes.filter(isReadable)
        val pageUnreadable = page.unreadable + (page.changes.size - changes.size)
        unreadable += pageUnreadable
        // После приведения журналов непонятой записи взяться неоткуда, и встреченная означает, что
        // клиент чего-то не знает. Курсор через неё не проходит: следующий проход перечитает ту же
        // страницу, и изменение не пропадёт (AD-11). До приведения — ровно наоборот, иначе первая
        // же старая запись остановила бы журнал навсегда.
        if (policy.strict && pageUnreadable > 0) {
            return Result.failure(UnreadableJournalEntriesException(cursorId, pageUnreadable))
        }
        // Выход по пустой странице, а не по «нет распознанных изменений»: страница, целиком
        // состоящая из битых записей, иначе останавливала бы журнал навсегда — курсор не двигался,
        // и следующий проход читал бы её же.
        val next = page.nextCursor ?: break

        if (changes.isNotEmpty()) {
            apply(changes).onFailure { return Result.failure(it) }
        }
        at = next
        setCursor(cursorId, next)
        pages++
        if (!page.hasMore) break
    }
    return Result.success(unreadable)
}

/**
 * Сколько страниц журнала берём за один проход.
 *
 * Не бесконечно: журнал пополняется во время чтения, и без потолка один проход мог бы не
 * закончиться никогда. Остаток доедет следующим заходом — курсор уже сдвинут.
 */
const val MAX_PAGES_PER_RUN: Int = 50
