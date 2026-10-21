package com.tpov.schoolquiz.platform.firebase.network

import com.tpov.schoolquiz.shared.core.network.SyncError
import java.io.IOException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Граница, ради которой всё затевалось: пока ошибка не прочитана здесь, выше по стеку остаётся
 * только текст сообщения — а по нему нельзя решить, повторять или нет.
 *
 * Главная проверка в этом файле — что «нет сети» и «сервер отказал» расходятся на разные ветви.
 * Сегодня они доходят до экрана одинаково.
 *
 * Таблица решений проверяется через [syncErrorForCode] по именам кодов: типы Firebase в JVM-тесте
 * не поднимаются — их статический инициализатор падает, — а имена кодов и есть весь вход решения.
 */
class CallableErrorsTest {

    private fun forCode(
        code: String,
        message: String? = null,
        details: Any? = null,
        causedByIo: Boolean = false,
    ): SyncError = syncErrorForCode(code, message, details, causedByIo)

    // ── Реплей арена-settle: уже-зафиксированный результат ─────────────────────

    private fun forOperation(
        operation: String,
        code: String,
        details: Any? = null,
    ): SyncError = mutationErrorForCode(operation, code, message = null, details = details)

    @Test
    fun `settlement reuse replay maps to already-recorded and the engine can ack it`() {
        val mapped = forOperation(
            "lesson_runner.SUBMIT_ATTEMPT",
            "ALREADY_EXISTS",
            details = mapOf("reason" to "SETTLEMENT_REUSE"),
        )
        assertEquals(SyncError.SettlementAlreadyRecorded::class, mapped::class)
    }

    @Test
    fun `foreign conflicting replay is not silently acked`() {
        val mapped = forOperation(
            "lesson_runner.SUBMIT_ATTEMPT",
            "ALREADY_EXISTS",
            details = mapOf("reason" to "CONFLICTING_REPLAY"),
        )
        assertEquals(false, mapped is SyncError.SettlementAlreadyRecorded)
    }

    @Test
    fun `other permanent operations never get the settlement-reuse ack`() {
        val mapped = forOperation(
            "lesson_runner.OPEN_GIFT_BOX",
            "ALREADY_EXISTS",
            details = mapOf("reason" to "SETTLEMENT_REUSE"),
        )
        // SETTLEMENT_REUSE — слово арена-settle: чужая already-exists не ACK-ается по нему.
        assertEquals(false, mapped is SyncError.SettlementAlreadyRecorded)
    }

    // ── Нет связи ─────────────────────────────────────────────────────────────

    @Test
    fun `given no route to host then no network`() {
        assertEquals(SyncError.NoNetwork, UnknownHostException("nope").toSyncError())
    }

    @Test
    fun `given io failure then no network`() {
        // Запрос не доехал, ответа не было — повторять безопасно.
        assertEquals(SyncError.NoNetwork, IOException("socket closed").toSyncError())
    }

    @Test
    fun `given server unavailable or timed out then no network`() {
        assertEquals(SyncError.NoNetwork, forCode("UNAVAILABLE"))
        assertEquals(SyncError.NoNetwork, forCode("DEADLINE_EXCEEDED"))
    }

    @Test
    fun `given internal caused by a broken connection then no network`() {
        // Ловушка SDK: обрыв связи приезжает как INTERNAL с сообщением "INTERNAL", а не как
        // UNAVAILABLE. Единственный различитель — причина.
        assertEquals(SyncError.NoNetwork, forCode("INTERNAL", "INTERNAL", causedByIo = true))
    }

    @Test
    fun `given internal from the server itself then unknown, not no network`() {
        // Тот же код, но причина не в связи: назвать это отсутствием сети значит соврать игроку.
        assertEquals(SyncError.Unknown(), forCode("INTERNAL", "INTERNAL", causedByIo = false))
    }

    // ── Отказ ─────────────────────────────────────────────────────────────────

    @Test
    fun `given not enough currency then refused, not no network`() {
        // Ровно тот случай, который сегодня неотличим от отсутствия сети.
        val error = forCode("FAILED_PRECONDITION", "Not enough nolics")

        assertEquals(SyncError.Refused("Not enough nolics"), error)
        assertEquals(SyncError.Disposition.QUARANTINE, error.disposition)
    }

    @Test
    fun `given the server is throttling then retry, not quarantine`() {
        // Cloud Run под нагрузкой отдаёт 429, SDK превращает его в RESOURCE_EXHAUSTED. Отнести
        // это к отказу значит уводить мутацию в карантин при всплеске нагрузки — а карантин по
        // AD-28 откатывает локальное изменение. Троттлинг обязан откладывать, а не уничтожать.
        val error = forCode("RESOURCE_EXHAUSTED", "quota exceeded")

        assertEquals(SyncError.Disposition.RETRY, error.disposition)
        assertEquals("и не NoNetwork: сеть есть, врать игроку незачем", SyncError.Unknown(), error)
    }

    @Test
    fun `given permission denied then refused and never retried`() {
        assertEquals(SyncError.Disposition.QUARANTINE, forCode("PERMISSION_DENIED", "nope").disposition)
    }

    @Test
    fun `given refusal without a message then the code stands in as the reason`() {
        // Пустая причина в карантинной записи бесполезна тому, кто будет её разбирать.
        assertEquals(SyncError.Refused("NOT_FOUND"), forCode("NOT_FOUND", message = null))
        assertEquals(SyncError.Refused("ALREADY_EXISTS"), forCode("ALREADY_EXISTS", message = "   "))
    }

    // ── Ещё не выполнено, а не отказано ───────────────────────────────────────

    @Test
    fun `given precondition marked pending then retryable, not quarantine`() {
        // AD-27: мутация, чьё предусловие ещё летит из той же очереди, обязана повториться.
        val error =
            forCode(
                "FAILED_PRECONDITION",
                message = "lesson not unlocked yet",
                details = PRECONDITION_PENDING_DETAIL,
            )

        assertEquals(SyncError.PreconditionNotMet, error)
        assertEquals(SyncError.Disposition.RETRY, error.disposition)
    }

    @Test
    fun `given the same code without the marker then refused`() {
        // Пара к предыдущему: без пометки сервера это обычный отказ, и он не повторяется.
        assertEquals(
            SyncError.Disposition.QUARANTINE,
            forCode("FAILED_PRECONDITION", "Not enough nolics").disposition,
        )
    }

    // ── Согласие с платными условиями: ожидание решения, не отказ (CAP-16) ────

    @Test
    fun `given the terms refusal detail then the consent branch, not quarantine`() {
        // Ровно то, что кладёт приёмник 2.8 в details: {code: terms-consent-required}. Карантин
        // здесь откатил бы написанную попытку, повтор без согласия вернул бы тот же отказ —
        // запись ждёт решения игрока, как конфликт версий.
        val error =
            forCode(
                "FAILED_PRECONDITION",
                message = "operation lesson_runner.SUBMIT_ATTEMPT requires captured payable terms",
                details = mapOf("code" to TERMS_CONSENT_DETAIL),
            )

        assertEquals(SyncError.TermsConsentRequired, error)
        assertEquals(SyncError.Disposition.CONFLICT, error.disposition)
    }

    @Test
    fun `given the terms refusal with the named change then the branch is still the consent one`() {
        // `change` — материал согласующего экрана, а не ветви решения; она обязана не зависеть
        // от того, назвал ли сервер изменение.
        val error =
            forCode(
                "FAILED_PRECONDITION",
                details = mapOf("code" to TERMS_CONSENT_DETAIL, "change" to mapOf("field" to "activityPrices.TOURNAMENT")),
            )

        assertEquals(SyncError.TermsConsentRequired, error)
    }

    @Test
    fun `given a foreign detail object on failed precondition then refused, not consent`() {
        // Чужой FAILED_PRECONDITION с объектом деталей — обычный отказ: ветвь согласия читает
        // только свою пометку.
        assertEquals(
            SyncError.Refused("Broken profile"),
            forCode("FAILED_PRECONDITION", "Broken profile", details = mapOf("code" to "something-else")),
        )
        assertEquals(
            SyncError.Refused("Broken profile"),
            forCode("FAILED_PRECONDITION", "Broken profile", details = "terms-consent-required"),
        )
    }

    // ── Конфликт версий ───────────────────────────────────────────────────────

    @Test
    fun `given aborted then version conflict`() {
        val error = forCode("ABORTED")

        assertEquals(SyncError.VersionConflict(), error)
        assertEquals(SyncError.Disposition.CONFLICT, error.disposition)
    }

    @Test
    fun `given aborted with the documented detail then the server version comes through`() {
        // Ровно то, что кладёт `versionConflictDetail` в functions/mutation-queue.js. Число едет
        // отдельным полем, потому что разбирать его из текста сообщения запрещено (AD-15), а без
        // него конфликт неразрешим (AD-24).
        val error =
            forCode(
                code = "ABORTED",
                message = "Entity moved to version 7",
                details = mapOf(VERSION_CONFLICT_DETAIL_KEY to VERSION_CONFLICT_DETAIL, "serverVersion" to 7),
            )

        assertEquals(SyncError.VersionConflict(7L), error)
    }

    @Test
    fun `given aborted with a fractional number then it is still read as a version`() {
        // JSON не различает целые и дробные: SDK отдаёт то Integer, то Double, и приведение к
        // Long обязано пережить оба.
        val error =
            forCode(
                code = "ABORTED",
                details = mapOf(VERSION_CONFLICT_DETAIL_KEY to VERSION_CONFLICT_DETAIL, "serverVersion" to 7.0),
            )

        assertEquals(SyncError.VersionConflict(7L), error)
    }

    @Test
    fun `given aborted from somewhere else then the branch survives without a number`() {
        // Чужой ABORTED — состязание транзакций Firestore, а не наша пометка. Ветвь остаётся
        // конфликтом, но версии не несёт: выдумывать её нельзя.
        assertEquals(SyncError.VersionConflict(), forCode("ABORTED", details = mapOf("code" to "something-else")))
        assertEquals(SyncError.VersionConflict(), forCode("ABORTED", details = "version-conflict"))
        assertEquals(
            SyncError.VersionConflict(),
            forCode("ABORTED", details = mapOf(VERSION_CONFLICT_DETAIL_KEY to VERSION_CONFLICT_DETAIL)),
        )
    }

    private companion object {
        const val VERSION_CONFLICT_DETAIL_KEY = "code"
    }

    // ── Всё остальное ─────────────────────────────────────────────────────────

    @Test
    fun `given an unrecognised code then unknown`() {
        assertEquals(SyncError.Unknown(), forCode("SOMETHING_NEW"))
    }

    @Test
    fun `given an unrecognised throwable then unknown, and it keeps the cause`() {
        val cause = IllegalStateException("boom")

        assertEquals(SyncError.Unknown(cause), cause.toSyncError())
    }

    @Test
    fun `given cancellation then it is rethrown, not swallowed as an error`() {
        // Отмена корутины — не отказ сервера. Проглотить её значит проглотить остановку.
        var propagated: String? = null
        try {
            CancellationException("stopped").toSyncError()
        } catch (e: CancellationException) {
            propagated = e.message
        }
        assertEquals("та же отмена, а не подменённая ошибка", "stopped", propagated)
    }
}
