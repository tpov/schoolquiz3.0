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
}
