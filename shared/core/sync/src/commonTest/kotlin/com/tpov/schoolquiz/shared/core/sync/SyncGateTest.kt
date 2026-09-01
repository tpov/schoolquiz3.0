package com.tpov.schoolquiz.shared.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
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
}
