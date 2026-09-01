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

    // ── Конфликт версий ───────────────────────────────────────────────────────

    @Test
    fun `given aborted then version conflict`() {
        val error = forCode("ABORTED")

        assertEquals(SyncError.VersionConflict(), error)
        assertEquals(SyncError.Disposition.CONFLICT, error.disposition)
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
