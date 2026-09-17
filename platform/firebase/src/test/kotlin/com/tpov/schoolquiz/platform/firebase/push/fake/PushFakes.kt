package com.tpov.schoolquiz.platform.firebase.push.fake

import com.tpov.schoolquiz.platform.firebase.push.DeviceTokenSource
import com.tpov.schoolquiz.platform.firebase.push.DeviceTokenStore
import com.tpov.schoolquiz.platform.firebase.push.ForeignTokenDetacher
import com.tpov.schoolquiz.platform.firebase.push.PushEvent
import com.tpov.schoolquiz.platform.firebase.push.PushNotifier
import com.tpov.schoolquiz.shared.core.sync.SyncFrequency
import com.tpov.schoolquiz.shared.core.sync.SyncScheduler
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull

/** Считает вызовы: единственное, что push вправе сделать с синхронизацией, — поднять её. */
class FakeSyncScheduler : SyncScheduler {
    var manualSyncCalls = 0
    var manualProfileSyncCalls = 0
    val appliedFrequencies = mutableListOf<SyncFrequency>()

    override fun enqueueManualSync() {
        manualSyncCalls++
    }

    override fun enqueueManualProfileSync() {
        manualProfileSyncCalls++
    }

    override fun applyFrequency(frequency: SyncFrequency) {
        appliedFrequencies += frequency
    }

    override fun applyProfileFrequency(frequency: SyncFrequency) {
        appliedFrequencies += frequency
    }
}

class FakePushNotifier : PushNotifier {
    val shown = mutableListOf<PushEvent>()

    override fun notify(event: PushEvent) {
        shown += event
    }
}

class FakeForeignTokenDetacher : ForeignTokenDetacher {
    val detached = mutableListOf<String>()

    override fun detach(targetUid: String) {
        detached += targetUid
    }
}

class FakeAuthRepository(
    initialUid: String? = null,
) : AuthRepository {
    private val uid = MutableStateFlow(initialUid)

    override suspend fun currentUid(): String? = uid.value

    override fun observeUid(): Flow<String?> = uid.asStateFlow()

    fun signIn(newUid: String) {
        uid.value = newUid
    }

    fun signOut() {
        uid.value = null
    }
}

class FakeDeviceTokenSource(
    initialToken: String? = null,
) : DeviceTokenSource {
    private val latest = MutableStateFlow(initialToken)
    private val issued = MutableSharedFlow<String>(replay = 1)

    init {
        if (initialToken != null) check(issued.tryEmit(initialToken))
    }

    override fun observeToken(): Flow<String> = issued.filterNotNull()

    override suspend fun currentToken(): String? = latest.value

    /** Выдача токена SDK: та же строка трижды — три эмиссии, как и в жизни. */
    fun issue(token: String) {
        latest.value = token
        check(issued.tryEmit(token))
    }
}

/** Подколлекция `devices` в памяти: ключ — путь документа, как и в Firestore. */
class FakeDeviceTokenStore : DeviceTokenStore {
    val documents = linkedMapOf<String, Long>()
    val registerCalls = mutableListOf<Triple<String, String, Long>>()
    val detachCalls = mutableListOf<Pair<String, String>>()
    var failRegister = false
    var failDetach = false

    override suspend fun register(
        uid: String,
        token: String,
        updatedAtMs: Long,
    ) {
        registerCalls += Triple(uid, token, updatedAtMs)
        if (failRegister) error("нет сети")
        documents[path(uid, token)] = updatedAtMs
    }

    override suspend fun detach(
        uid: String,
        token: String,
    ) {
        detachCalls += uid to token
        if (failDetach) error("нет сети")
        documents.remove(path(uid, token))
    }

    fun documentsOf(uid: String): List<String> = documents.keys.filter { it.startsWith("users/$uid/devices/") }

    private fun path(
        uid: String,
        token: String,
    ) = "users/$uid/devices/$token"
}
