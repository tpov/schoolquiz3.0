package com.tpov.schoolquiz.shared.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * AD-15 задаёт не только ветви, но и что с каждой делать: повтор назначается для
 * НетСети, ПредусловиеНеВыполнено и Неизвестно; Отказ уводит в карантин без повторов;
 * КонфликтВерсии обрабатывается отдельно по AD-24; ОжиданиеСогласия — та же дверь решения,
 * но решает его согласие игрока, а не новая версия (CAP-16).
 *
 * Это правило и проверяется здесь — оно единственное, ради чего тип существует.
 */
class SyncErrorTest {

    // ── Что повторяем ─────────────────────────────────────────────────────────

    @Test
    fun `given no network then retry`() {
        assertEquals(SyncError.Disposition.RETRY, SyncError.NoNetwork.disposition)
        assertTrue(SyncError.NoNetwork.isRetryable)
    }

    @Test
    fun `given precondition not met then retry`() {
        // «Ещё нет», а не «нет»: мутация, от которой эта зависит, всё ещё летит (AD-27).
        assertEquals(SyncError.Disposition.RETRY, SyncError.PreconditionNotMet.disposition)
        assertTrue(SyncError.PreconditionNotMet.isRetryable)
    }

    @Test
    fun `given unknown failure then retry`() {
        assertEquals(SyncError.Disposition.RETRY, SyncError.Unknown().disposition)
        assertTrue(SyncError.Unknown(IllegalStateException("boom")).isRetryable)
    }

    // ── Что не повторяем ──────────────────────────────────────────────────────

    @Test
    fun `given server refusal then quarantine and never retry`() {
        val refused = SyncError.Refused(reason = "Not enough nolics")
        assertEquals(SyncError.Disposition.QUARANTINE, refused.disposition)
        assertFalse(refused.isRetryable)
    }

    @Test
    fun `given version conflict then conflict and never retry`() {
        val conflict = SyncError.VersionConflict(serverVersion = 7L)
        assertEquals(SyncError.Disposition.CONFLICT, conflict.disposition)
        assertFalse(conflict.isRetryable)
    }

    @Test
    fun `given terms consent required then conflict and never retry`() {
        // Не отказ и не повтор: без свежего согласия повтор вернёт тот же отказ, а карантин
        // откатил бы написанную попытку. Запись ждёт решения игрока, как конфликт версий.
        assertEquals(SyncError.Disposition.CONFLICT, SyncError.TermsConsentRequired.disposition)
        assertFalse(SyncError.TermsConsentRequired.isRetryable)
    }

    // ── Что ветви несут ───────────────────────────────────────────────────────

    @Test
    fun `given refusal then the server reason survives`() {
        // Причина показывается игроку и попадает в журнал, поэтому не теряется по дороге.
        assertEquals("Not enough nolics", SyncError.Refused("Not enough nolics").reason)
    }

    @Test
    fun `given conflict before the server sends versions then version is absent, not zero`() {
        // E8 научит сервер присылать версию. До него ветвь существует, но номера не несёт,
        // и ноль здесь означал бы «версия 0», а не «неизвестно».
        assertEquals(null, SyncError.VersionConflict().serverVersion)
    }

    @Test
    fun `given every branch then a disposition is declared`() {
        // Ветвь без решения о повторе — это ровно тот разбор по строке, который AD-15 запрещает.
        val all = listOf(
            SyncError.NoNetwork,
            SyncError.PreconditionNotMet,
            SyncError.Refused("any"),
            SyncError.VersionConflict(1L),
            SyncError.TermsConsentRequired,
            SyncError.Unknown(),
        )
        assertEquals(6, all.size, "AD-15 закрывает набор шестью ветвями")
        assertEquals(6, all.map { it.disposition }.size)
        assertEquals(3, all.count { it.isRetryable }, "повторяются ровно три ветви")
    }
}
