package com.tpov.schoolquiz.apps.android_next.push

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.schoolquiz.platform.firebase.push.FirestoreDeviceTokenStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ТРЕБУЕТ ЭМУЛЯТОРА Firestore + Auth (Story 10.5): один документ у A до переключения, один у B и
 * ноль у A после.
 *
 * Запуск: `firebase emulators:start --only auth,firestore` с правилами из `firestore.rules`, затем
 * `./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.firebaseEmulatorHost=10.0.2.2`.
 * Без аргумента тест пропускается, а не падает: в обычном прогоне эмулятора нет.
 */
@RunWith(AndroidJUnit4::class)
class DeviceTokenSwitchEmulatorTest {
    private val args = InstrumentationRegistry.getArguments()

    @Test
    fun tokenMovesFromAToBOnAccountSwitch() =
        runBlocking {
            val host = args.getString(ARG_EMULATOR_HOST)
            assumeTrue("нужен эмулятор Firebase: передайте $ARG_EMULATOR_HOST", !host.isNullOrBlank())
            val auth = FirebaseAuth.getInstance().apply { useEmulator(host!!, AUTH_PORT) }
            val firestore = FirebaseFirestore.getInstance().apply { useEmulator(host!!, FIRESTORE_PORT) }
            val store = FirestoreDeviceTokenStore(firestore)
            val token = "token-${System.nanoTime()}"

            auth.signOut()
            val uidA = auth.signInAnonymously().await().user!!.uid
            store.register(uidA, token, updatedAtMs = 1L)
            store.register(uidA, token, updatedAtMs = 2L)
            assertEquals(1, devicesOf(firestore, uidA))

            // То, что делает FirebaseGoogleSignInRepository.signInAs: снять до смены аутентификации.
            store.detach(uidA, token)
            auth.signOut()
            val uidB = auth.signInAnonymously().await().user!!.uid
            store.register(uidB, token, updatedAtMs = 3L)

            assertEquals(1, devicesOf(firestore, uidB))
            assertEquals(0, devicesOf(firestore, uidA))
        }

    private suspend fun devicesOf(
        firestore: FirebaseFirestore,
        uid: String,
    ): Int =
        firestore.collection(FirestoreDeviceTokenStore.USERS).document(uid)
            .collection(FirestoreDeviceTokenStore.DEVICES).get().await().size()

    private companion object {
        const val ARG_EMULATOR_HOST = "firebaseEmulatorHost"
        const val AUTH_PORT = 9099
        const val FIRESTORE_PORT = 8080
    }
}
