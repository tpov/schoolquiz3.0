package com.tpov.schoolquiz.shared.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

/**
 * Ворота существуют ради одного случая: полный ресинк обнуляет курсоры, а проход, начавшийся до
 * сброса, дописывает своё старое значение уже после. Курсоры монотонны — `setCursor` берёт
 * максимум, — поэтому такой дописанный курсор отменяет ресинк молча, и игрок, нажавший
 * «перечитать всё», не получает ничего.
 */
class SyncGateTest {

    @Test
    fun `given two passes then they do not overlap`() = runTest {
        val gate = SyncGate()
        var inside = 0
        var maxInside = 0

        coroutineScope {
            val jobs =
                List(4) {
                    async {
                        gate.withPass {
                            inside++
                            maxInside = maxOf(maxInside, inside)
                            yield()
                            inside--
                        }
                    }
                }
            jobs.forEach { it.await() }
        }

        assertEquals(1, maxInside, "второй проход обязан ждать, а не идти рядом")
    }

    @Test
    fun `given a resync then a pass waiting on it starts only after the reset`() = runTest {
        // Ровно тот случай, ради которого ворота: сброс и чтение — одно неделимое действие.
        val gate = SyncGate()
        val order = mutableListOf<String>()

        coroutineScope {
            val resync =
                async {
                    gate.withPass {
                        order += "сброс"
                        yield()
                        order += "чтение"
                    }
                }
            yield()
            val pass = async { gate.withPass { order += "плановый проход" } }
            resync.await()
            pass.await()
        }

        assertEquals(listOf("сброс", "чтение", "плановый проход"), order)
    }

    @Test
    fun `given a pass that throws then the gate does not stay shut`() = runTest {
        // Незакрытые ворота остановили бы синхронизацию навсегда — хуже исходного дефекта.
        val gate = SyncGate()

        runCatching { gate.withPass { error("boom") } }
        var passed = false
        gate.withPass { passed = true }

        assertTrue(passed, "после неудачного прохода ворота обязаны открыться")
    }

    @Test
    fun `given a pass nested in a pass of the same gate then it goes through`() = runTest {
        // Полный ресинк делает ровно это: обнуляет курсоры внутри ворот и тут же зовёт читающую
        // сторону, у которой `sync()` — тоже `withPass`. Нереентерабельный замок висел бы здесь
        // вечно, уже после удаления курсоров.
        val gate = SyncGate()
        var reached = false

        val outcome =
            withTimeoutOrNull(5_000L) {
                gate.withPass {
                    gate.withPass { reached = true }
                }
            }

        assertNotNull(outcome, "вложенный проход повис на собственных воротах")
        assertTrue(reached)
    }

    @Test
    fun `given a nested pass finished then the gate is open for the next pass`() = runTest {
        // Реентерабельность не должна отпускать замок раньше времени: внешний проход ещё внутри.
        val gate = SyncGate()
        val order = mutableListOf<String>()

        coroutineScope {
            val outer =
                async {
                    gate.withPass {
                        gate.withPass { order += "вложенный" }
                        yield()
                        order += "внешний закончил"
                    }
                }
            yield()
            val other = async { gate.withPass { order += "следующий проход" } }
            outer.await()
            other.await()
        }

        assertEquals(listOf("вложенный", "внешний закончил", "следующий проход"), order)
    }

    @Test
    fun `given two different gates then one does not let the other through`() = runTest {
        // Метка прохода принадлежит конкретным воротам: иначе чужой проход проскакивал бы замок.
        val outer = SyncGate()
        val inner = SyncGate()
        var maxInside = 0
        var inside = 0

        coroutineScope {
            val held =
                async {
                    inner.withPass {
                        inside++
                        maxInside = maxOf(maxInside, inside)
                        yield()
                        yield()
                        inside--
                    }
                }
            yield()
            val nested =
                async {
                    outer.withPass {
                        inner.withPass {
                            inside++
                            maxInside = maxOf(maxInside, inside)
                            inside--
                        }
                    }
                }
            held.await()
            nested.await()
        }

        assertEquals(1, maxInside, "чужие ворота обязаны держать проход")
    }
}
