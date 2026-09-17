package com.tpov.schoolquiz.shared.feature.internet.profile.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.QuarantineListener

/**
 * Что делает профиль, когда снятие лота ушло в карантин (AD-28).
 *
 * Откатывать нечего: локальной половины у снятия нет (AD-25) — листинг живёт на сервере, и в
 * очередь легло только намерение. Последствие карантина здесь одно, и оно уже записано ядром:
 * запись стоит в карантине, строка лота читает это по ссылке на сущность и рисует «уже продан»,
 * а счётчик карантинных растёт. Ни одной таблицы фичи менять не надо.
 *
 * Обработчик всё равно объявлен и зарегистрирован — за фичей, чьё имя стоит в `operation`
 * (NFR4): «нечего откатывать» обязано отличаться от «забыли написать обработчик».
 */
class ListingCancelQuarantine : QuarantineListener {
    override suspend fun onQuarantined(record: OutboxRecord) = Unit
}
