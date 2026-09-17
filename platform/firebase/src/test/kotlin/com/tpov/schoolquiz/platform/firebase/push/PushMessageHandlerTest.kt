package com.tpov.schoolquiz.platform.firebase.push

import com.tpov.schoolquiz.platform.firebase.push.fake.FakeAuthRepository
import com.tpov.schoolquiz.platform.firebase.push.fake.FakeForeignTokenDetacher
import com.tpov.schoolquiz.platform.firebase.push.fake.FakePushNotifier
import com.tpov.schoolquiz.platform.firebase.push.fake.FakeSyncScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Push поднимает обычную синхронизацию и ничего не применяет сам (Story 10.4, 10.5, AD-20).
 */
class PushMessageHandlerTest {
    private val scheduler = FakeSyncScheduler()
    private val notifier = FakePushNotifier()
    private val detacher = FakeForeignTokenDetacher()
    private val auth = FakeAuthRepository(initialUid = "uid-A")
    private val handler = PushMessageHandler(scheduler, notifier, auth, detacher)

    @Test
    fun `known event for the current account raises sync once and shows one notification`() =
        runTest {
            handler.handle(message(eventType = PushEvents.QUEST_PUBLISHED.eventType))

            assertEquals(1, scheduler.manualSyncCalls)
            assertEquals(0, scheduler.manualProfileSyncCalls)
            assertEquals(listOf(PushEvents.QUEST_PUBLISHED), notifier.shown)
            assertEquals(emptyList<String>(), detacher.detached)
        }

    @Test
    fun `handler has no DAO to write payload fields into`() {
        // Ни одна таблица Room не пишется из полей push: обработчику просто некуда писать.
        val parameterTypes =
            PushMessageHandler::class.java.constructors.single().parameterTypes.map { it.name }
        val persistence = parameterTypes.filter { it.contains("Dao") || it.contains("persistence") }
        assertEquals("обработчик не должен знать о хранилище: $parameterTypes", emptyList<String>(), persistence)
    }

    @Test
    fun `unknown event still syncs once and shows nothing`() =
        runTest {
            handler.handle(message(eventType = "economy.SOMETHING_NEW"))

            assertEquals(1, scheduler.manualSyncCalls)
            assertEquals(0, notifier.shown.size)
        }

    @Test
    fun `blank event type syncs once and shows nothing`() =
        runTest {
            handler.handle(message(eventType = ""))

            assertEquals(1, scheduler.manualSyncCalls)
            assertEquals(0, notifier.shown.size)
        }

    @Test
    fun `missing event type syncs once and shows nothing`() =
        runTest {
            handler.handle(PushMessage.fromData(mapOf(PushMessage.TARGET_UID to "uid-A")))

            assertEquals(1, scheduler.manualSyncCalls)
            assertEquals(0, notifier.shown.size)
        }

    @Test
    fun `message for another account neither syncs nor shows and asks the server to detach`() =
        runTest {
            handler.handle(message(eventType = PushEvents.QUEST_PUBLISHED.eventType, targetUid = "uid-B"))

            assertEquals(0, scheduler.manualSyncCalls)
            assertEquals(0, notifier.shown.size)
            assertEquals(listOf("uid-B"), detacher.detached)
        }

    @Test
    fun `message for a guest device with a target is foreign too`() =
        runTest {
            auth.signOut()

            handler.handle(message(eventType = PushEvents.QUEST_PUBLISHED.eventType, targetUid = "uid-B"))

            assertEquals(0, scheduler.manualSyncCalls)
            assertEquals(0, notifier.shown.size)
            assertEquals(listOf("uid-B"), detacher.detached)
        }

    @Test
    fun `message without target syncs once but is not shown`() =
        runTest {
            handler.handle(message(eventType = PushEvents.QUEST_PUBLISHED.eventType, targetUid = null))

            assertEquals(1, scheduler.manualSyncCalls)
            assertEquals(0, notifier.shown.size)
            assertTrue(detacher.detached.isEmpty())
        }

    @Test
    fun `payload carries exactly three fields`() {
        val fields =
            PushMessage::class.java.declaredFields
                .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
                .map { it.name }
        assertEquals(listOf("eventType", "entityRef", "targetUid"), fields)
    }

    private fun message(
        eventType: String?,
        targetUid: String? = "uid-A",
    ) = PushMessage.fromData(
        buildMap {
            eventType?.let { put(PushMessage.EVENT_TYPE, it) }
            put(PushMessage.ENTITY_REF, "quest_authoring.QUEST:q-1")
            targetUid?.let { put(PushMessage.TARGET_UID, it) }
        },
    )
}
