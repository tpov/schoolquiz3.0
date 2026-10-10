package com.tpov.schoolquiz.platform.firebase

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Callable в demo-сборке обязан доходить до эмулятора.
 *
 * `FirebaseContextProvider.getContext` склеивает auth-токен и App Check-токен в один `whenAll`:
 * без установленной фабрики App Check задача провайдера падает («No AppCheckProvider installed»)
 * и весь callable умирает до сети — «1 out of 2 underlying tasks failed», а из очереди не
 * уезжает ни арена-SETTLE, ни бутстрап профиля. На устройстве это выглядит как «синхронизация
 * сломана», хотя REST-канал здоров. Demo-ветка инициализатора ставит локальную фабрику: эмулятор
 * функции токен не проверяет, а debug-провайдер обменивает токен с живым App Check-бэкендом и
 * после переустановок упирается в его rate limit («Too many attempts») — локальная фабрика
 * делает demo-путь детерминированным.
 *
 * Половина проверки — по исходникам: demo-ветка (`useEmulators`) обязана устанавливать локальную
 * фабрику до `return`; забытой её делает не тип, а правка ветки. Вторая — по класспасу:
 * рефлексный шов debug-провайдера обязан находить его там, где он есть (не-emulator debug-ветка).
 */
class EmulatorCallableAppCheckWiringTest {

    private val initializer =
        File("src/main/kotlin/com/tpov/schoolquiz/platform/firebase/FirebaseInitializer.kt")

    @Test
    fun `given the demo branch then it installs the local app check factory before returning`() {
        assertTrue("не нашёл исходники: ${initializer.absolutePath}", initializer.isFile)

        val text = initializer.readText()
        val demoBranch = Regex("""if \(useEmulators\) \{[\s\S]*?\n    \}""").find(text)
        assertNotNull("ветка useEmulators не найдена", demoBranch)

        val body = demoBranch!!.value
        assertTrue(
            "demo-ветка не устанавливает фабрику App Check — callable не уедет:\n$body",
            body.contains("installAppCheckProviderFactory"),
        )
        assertTrue(
            "demo-ветка обязана ставить DemoLocalAppCheckProviderFactory (сеть не касается, «Too many attempts» не приносит) :\n$body",
            body.contains("DemoLocalAppCheckProviderFactory"),
        )
    }

    @Test
    fun `given the debug provider on the classpath then the reflective seam resolves it`() {
        val factory = debugAppCheckProviderFactoryOrNull()
        assertNotNull(
            "DebugAppCheckProviderFactory не найден на класспасе — в не-emulator debug-сборке callable останется сломанным",
            factory,
        )
    }
}
