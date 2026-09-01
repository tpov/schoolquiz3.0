package com.tpov.schoolquiz.shared.core.outbox

/**
 * Раздаёт карантин по владеющим фичам (AD-28).
 *
 * Движок не имеет права трогать таблицы фичи, а карантин терминален: запись больше не уедет, а
 * локальное изменение, сделанное вместе с ней одной транзакцией, осталось. Разбирать это должна
 * та фича, которая действие создала.
 *
 * Операция без обработчика — не «ничего не делать», а **молчаливое расхождение**, ровно то, что
 * AD-28 запрещает. Поэтому неизвестная операция уходит в [onUnhandled], и умолчания у него нет:
 * приложение обязано хотя бы узнать, что локальное состояние разошлось с сервером.
 */
class OutboxQuarantineRouter(
    private val handlers: Map<String, QuarantineListener>,
    private val onUnhandled: QuarantineListener,
) : QuarantineListener {

    override suspend fun onQuarantined(record: OutboxRecord) {
        val handler = handlers[record.operation]
        if (handler != null) handler.onQuarantined(record) else onUnhandled.onQuarantined(record)
    }
}

/**
 * Операции, у которых локальной половины нет вовсе.
 *
 * Для них карантин действительно не требует отката: серверно-защищённые поля локально не
 * меняются (AD-25), поэтому расходиться нечему. Объявляется явно, а не молчанием, — чтобы
 * «нечего откатывать» и «забыли написать обработчик» не выглядели одинаково.
 */
class NoLocalEffect(
    private val onNotified: (OutboxRecord) -> Unit = {},
) : QuarantineListener {
    override suspend fun onQuarantined(record: OutboxRecord) = onNotified(record)
}
