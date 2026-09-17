package com.tpov.schoolquiz.shared.core.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Один проход чтения за раз.
 *
 * Курсоры монотонны: `setCursor` берёт максимум из старого и нового. Это защищает от отката при
 * двух одновременных проходах — и оно же отменяет сброс. Полный ресинк обнуляет курсоры, а проход,
 * начавшийся до сброса, дописывает своё старое значение уже после — и ресинк молча не делает
 * ничего, хотя игрок его попросил.
 *
 * Отменить монотонность нельзя: она нужна ровно для того, ради чего написана. Поэтому сброс и
 * чтение просто не пересекаются во времени.
 *
 * Ворота одни на всё приложение и живут в composition root: два экземпляра означали бы, что
 * пересечение снова возможно.
 *
 * **Проход реентерабелен.** `kotlinx.coroutines.sync.Mutex` владельца не знает, и вложенный
 * `withPass` на тех же воротах вешал бы вызывающего навсегда. Ровно это и делает полный ресинк:
 * он обнуляет курсоры внутри ворот и тут же зовёт читающую сторону, у которой `sync()` — тоже
 * `withPass`. Замок висел бы уже ПОСЛЕ удаления курсоров и не отпускался бы никогда, унося с
 * собой и плановую синхронизацию. Поэтому проход помечает себя в контексте корутины, и вложенный
 * `withPass` тех же ворот идёт насквозь: снаружи это по-прежнему один неделимый проход.
 */
class SyncGate {
    private val mutex = Mutex()

    /** Метка «эта корутина уже внутри прохода этих ворот». */
    private val pass = Pass(this)

    /**
     * Пропускает через ворота.
     *
     * Не `tryLock`: проход, пришедший вторым, обязан дождаться, а не молча отмениться. Иначе
     * плановая синхронизация, совпавшая с ресинком, просто не случилась бы — и никто бы не узнал.
     */
    suspend fun <T> withPass(block: suspend () -> T): T =
        if (coroutineContext[Pass]?.gate === this) {
            // Уже внутри прохода этих же ворот: повторный lock не взять — Mutex нереентерабелен.
            block()
        } else {
            mutex.withLock { withContext(pass) { block() } }
        }

    /**
     * Признак «мы внутри прохода». Хранится в контексте корутины, а не полем: поле принадлежало бы
     * воротам, а признак принадлежит вызывающему — иначе чужая корутина проскочила бы замок.
     */
    private class Pass(val gate: SyncGate) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Pass>
    }
}
