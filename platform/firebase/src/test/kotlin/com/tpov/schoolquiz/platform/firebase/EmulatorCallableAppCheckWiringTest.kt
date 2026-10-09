package com.tpov.schoolquiz.platform.firebase

import java.io.File
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
 * сломана», хотя REST-канал здоров. Demo-ветка инициализатора ставит debug-фабрику: она
 * отвечает локально и live-сервис не касается.
 *
 * Половина проверки — по исходникам: demo-ветка (`useEmulators`) обязана устанавливать фабрику
 * до `return`; забытой её делает не тип, а правка ветки. Вторая половина — по класспасу:
 * рефлексный шов обязан находить debug-провайдера там, где он есть.
 */
class EmulatorCallableAppCheckWiringTest {

    private val initializer =
        File("src/main/kotlin/com/tpov/schoolquiz/platform/firebase/FirebaseInitializer.kt")

    @Test
    fun `given the demo branch then it installs an app check factory before returning`() {
        assertTrue("не нашёл исходники: ${initializer.absolutePath}", initializer.isFile)

        val text = initializer.readText()
        val demoBranch = Regex("""if \(useEmulators\) \{[\s\S]*?\n    \}""").find(text)
        assertNotNull("ветка useEmulators не найдена", demoBranch)

        val body = demoBranch!!.value
        assertTrue(
            "demo-ветка не устанавливает фабрику App Check — callable не уедет:\n$body",
            body.contains("installAppCheckProviderFactory"),
        )
    }

    @Test
    fun `given the debug provider on the classpath then the reflective seam resolves it`() {
        val factory = debugAppCheckProviderFactoryOrNull()
        assertNotNull(
            "DebugAppCheckProviderFactory не найден на класспасе — в mvp-сборке callable останется сломанным",
            factory,
        )
    }
}
