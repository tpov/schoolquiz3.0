package com.tpov.schoolquiz.shared.feature.internet.profile.data

/**
 * Код приглашающего, пойманный из deep-link, — дожидается первого входа (история 5.3).
 *
 * Единственный честный момент атрибуции — создание аккаунта, поэтому код живёт ровно до
 * первого успешного bootstrap: уходит с [ProfileBootstrapRequest.referredByUid] и стирается.
 * Ссылку могут открыть задолго до регистрации, так что хранение обязано переживать процесс —
 * какая постоянная реализация ни выбрана, в памяти процесса живёт только умолчание для тестов.
 */
interface PendingReferralStore {
    suspend fun read(): String?

    suspend fun write(code: String)

    suspend fun clear()
}

/** Память процесса: умолчание для JVM-тестов и заглушек; боевой проводке полагается постоянная. */
class InMemoryPendingReferralStore : PendingReferralStore {
    @Volatile
    private var code: String? = null

    override suspend fun read(): String? = code

    override suspend fun write(code: String) {
        this.code = code
    }

    override suspend fun clear() {
        this.code = null
    }
}
