package com.tpov.schoolquiz.platform.firebase

import com.google.firebase.FirebaseApp
import io.mockk.mockk
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Локальный demo-токен обязан выдаваться сразу и без сети: упавший App Check-task валит callable
 * до эмулятора (whenAll с auth-токеном), а сеть вне эмулятора demo-сборке не нужна.
 */
class DemoLocalAppCheckProviderFactoryTest {

    @Test
    fun `given the demo factory then a token resolves immediately with future expiry`() {
        val provider = DemoLocalAppCheckProviderFactory.create(mockk<FirebaseApp>())

        val task = provider.getToken()

        assertTrue(task.isComplete)
        assertTrue(task.isSuccessful)
        val token = task.result
        assertTrue(token.token.isNotEmpty())
        assertTrue("токен обязан жить дольше момента выдачи", token.expireTimeMillis > System.currentTimeMillis())
    }

    @Test
    fun `given repeated token requests then each carries a fresh expiry`() {
        val provider = DemoLocalAppCheckProviderFactory.create(mockk<FirebaseApp>())

        val first = provider.getToken().result
        val second = provider.getToken().result

        assertNotEquals("просроченный кеш не должен реанимировать callable", 0L, first.expireTimeMillis)
        assertTrue(second.expireTimeMillis >= first.expireTimeMillis)
    }

    @Test
    fun `given the demo token then the SDK can parse its claims`() {
        val provider = DemoLocalAppCheckProviderFactory.create(mockk<FirebaseApp>())

        val token = provider.getToken().result.token

        val sections = token.split(".")
        assertTrue("SDK разбирает токен как JWT и бракует строку без секций", sections.size >= 3)
        val padding = "==".take((4 - sections[1].length % 4) % 4)
        val json = String(java.util.Base64.getUrlDecoder().decode(sections[1] + padding))
        // Читатель кеша приводит exp к Integer напрямую: Long в claim роняет процесс на старте.
        assertTrue("exp обязан быть секундами внутри Integer: $json", json.contains("\"exp\":2147483000"))
    }
}
