package com.tpov.schoolquiz.platform.firebase.network

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Каждый вызов Cloud Function обязан иметь таймаут.
 *
 * Клиент Firebase по умолчанию ждёт семьдесят секунд — за это время игрок решает, что приложение
 * сломалось. AD-15 требует, чтобы «долго» и «никогда» не выглядели одинаково, и таймаут — половина
 * этого требования; вторая половина, различение ветвей ошибки, без него бесполезна: до неё просто
 * не доходит.
 *
 * Проверяется текстом по исходникам, а не типами: обёртка — расширение над
 * `HttpsCallableReference`, и забыть её нельзя никак, кроме как забыть. Новый вызов без таймаута
 * уронит эту проверку в тот же день, когда появится.
 */
class CallableTimeoutCoverageTest {

    private val sourceRoot = File("src/main/kotlin")

    @Test
    fun `given every callable in this module then each one carries a timeout`() {
        assertTrue("не нашёл исходники: ${sourceRoot.absolutePath}", sourceRoot.isDirectory)

        val offenders =
            sourceRoot.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .mapNotNull { file ->
                    val text = file.readText()
                    val calls = COUNT_CALLABLE.findAll(text).count()
                    if (calls == 0) return@mapNotNull null
                    // Импорт обёртки не считается её применением.
                    val timeouts = text.lineSequence().count { it.contains(".withAppTimeout()") }
                    if (calls == timeouts) null else "${file.name}: вызовов $calls, таймаутов $timeouts"
                }
                .toList()

        assertEquals("вызовы без таймаута:\n" + offenders.joinToString("\n"), emptyList<String>(), offenders)
    }

    private companion object {
        val COUNT_CALLABLE = Regex("""\.getHttpsCallable\(""")
    }
}
