package com.tpov.schoolquiz.shared.core.outbox

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Клиент и приёмник называют операции одинаково.
 *
 * Имя операции — часть контракта (AD-6): незнакомую приёмник отвергает окончательно, и такая
 * запись уходит в карантин навсегда, а локальное изменение остаётся расходиться с сервером. Ловить
 * это в бою нечем — отказ выглядит как обычный отказ сервера.
 *
 * Ловушка тут не гипотетическая. В `functions/index.js` идентификатор `UNLOCK_LESSON` уже занят:
 * он импортирован из `lesson-unlocks.js` и означает вид покупки, строку `"lesson"`. Пока ключ
 * реестра писался голым именем — литералом `UNLOCK_LESSON:`, — всякая естественная «уборка» вида
 * `[UNLOCK_LESSON]: handler` молча зарегистрировала бы обработчик под `"lesson"`, и каждая
 * отложенная разблокировка стала бы неизвестной операцией. Имя приведено к правилу NFR4 и теперь
 * пишется в кавычках (`"lesson.UNLOCK"`), но проверка на вычисленный ключ остаётся: она стережёт
 * весь реестр, а не одну строку.
 *
 * Поэтому реестр читается текстом. JVM-тест, а не общий: файл с диска общий код не видит.
 */
class OutboxOperationsRegistryTest {
    private val source: String by lazy {
        val file = File("../../../functions/index.js")
        assertTrue(file.exists(), "нет приёмника мутаций: ${file.absolutePath}")
        file.readText()
    }

    /** Ключи объектного литерала `MUTATION_HANDLERS`, в кавычках и без. */
    private val registered: Set<String> by lazy {
        val body =
            Regex("""const MUTATION_HANDLERS = \{(.*?)\n};""", RegexOption.DOT_MATCHES_ALL)
                .find(source)
                ?.groupValues
                ?.get(1)
        assertTrue(body != null, "не нашёл реестр MUTATION_HANDLERS в functions/index.js")
        Regex("""^\s{2}"?([A-Za-z0-9_.]+)"?:""", RegexOption.MULTILINE)
            .findAll(body!!)
            .map { it.groupValues[1] }
            .toSet()
    }

    /** Всё, что клиент умеет ставить в очередь. */
    private val declared =
        setOf(
            OutboxOperations.UNLOCK_LESSON,
            OutboxOperations.SUBMIT_ATTEMPT,
            OutboxOperations.OPEN_GIFT_BOX,
            OutboxOperations.READY_ACK,
            OutboxOperations.SUBMIT_RATING,
            OutboxOperations.SUBMIT_ARENA,
            OutboxOperations.SET_SHELF,
            OutboxOperations.SUBMIT_LESSON_ERROR,
            OutboxOperations.SUBMIT_POLL_VOTE,
            OutboxOperations.PUBLISH_POLL_SERIES,
            OutboxOperations.CANCEL_NICKNAME_LISTING,
            OutboxOperations.CANCEL_LOGO_LISTING,
            OutboxOperations.TOURNAMENT_SESSION_OP,
            OutboxOperations.CLOSE_PRIVATE_QUIZ_LINK,
            OutboxOperations.SUBMIT_PRIVATE_QUIZ_ATTEMPT,
            // Предложение автору (AD-33, 4.21): один приёмник для трёх входов (report/copy/new).
            OutboxOperations.SUBMIT_PROPOSAL,
        )

    @Test
    fun `given every operation the client can queue then the server has a handler for it`() {
        val orphans = declared - registered

        assertEquals(
            emptySet(),
            orphans,
            "эти операции клиент отправит, а приёмник отвергнет окончательно: $orphans\n" +
                "реестр знает: $registered",
        )
    }

    @Test
    fun `given every handler on the server then the client declares its name`() {
        // Обработчик, чьего имени клиент не знает, — либо забытое переименование, либо операция,
        // которую некому поставить в очередь. Оба случая стоит увидеть до боя.
        val unreachable = registered - declared

        assertEquals(emptySet(), unreachable, "обработчики, до которых клиенту не дотянуться: $unreachable")
    }

    @Test
    fun `given the registry then no key is spelled as a bare identifier the file redefines`() {
        // `UNLOCK_LESSON:` — литерал, и работает; `[UNLOCK_LESSON]:` — импортированная константа
        // со значением "lesson", и не работает. Разница в двух скобках.
        val computed =
            Regex("""^\s{2}\[[A-Za-z0-9_.]+]:""", RegexOption.MULTILINE)
                .findAll(source.substringAfter("const MUTATION_HANDLERS = {").substringBefore("\n};"))
                .map { it.value.trim() }
                .toList()

        assertEquals(emptyList(), computed, "ключ реестра, посчитанный из переменной: $computed")
    }

    /** Реестр операций, чья мутация сверяется с согласием игрока, — текстом из приёмника. */
    private val serverTermsOperations: Set<String> by lazy {
        val body =
            Regex("""const MUTATION_TERMS_OPERATIONS = new Set\(\[(.*?)\]\);""", RegexOption.DOT_MATCHES_ALL)
                .find(source)
                ?.groupValues
                ?.get(1)
        assertTrue(body != null, "не нашёл реестр MUTATION_TERMS_OPERATIONS в functions/index.js")
        Regex(""""([A-Za-z0-9_.]+)"""")
            .findAll(body!!)
            .map { it.groupValues[1] }
            .toSet()
    }

    @Test
    fun `given the client terms registry then the server checks consent for exactly those operations`() {
        // Операция согласия без серверной сверки: клиент возит согласие, которое никто не читает.
        val unchecked = OutboxOperations.termsConsentOperations - serverTermsOperations
        assertEquals(
            emptySet(),
            unchecked,
            "клиент прикладывает согласие, которое приёмник не сверяет: $unchecked",
        )
    }

    @Test
    fun `given the server terms registry then the client attaches consent to exactly those operations`() {
        // Серверная сверка без клиентского согласия: платная мутация уедет без capturedTerms и
        // будет отвергнута окончательно — тот стык, который закрыла история 2.12.
        val unattached = serverTermsOperations - OutboxOperations.termsConsentOperations
        assertEquals(
            emptySet(),
            unattached,
            "приёмник сверяет согласие, которого клиент не прикладывает: $unattached",
        )
    }

    @Test
    fun `given the private quiz operations then none of them asks for payable terms consent`() {
        // AD-29/8.5: приватный квиз бесплатен целиком — вход, подача попытки, закрытие ссылки.
        // Согласие с платными условиями у приватных операций появилось бы ровно в одном случае:
        // если кто-то начал брать за приватное деньги — и это должно упасть здесь, по имени.
        val privateOperations =
            setOf(
                OutboxOperations.CLOSE_PRIVATE_QUIZ_LINK,
                OutboxOperations.SUBMIT_PRIVATE_QUIZ_ATTEMPT,
            )
        val askingForConsent = privateOperations intersect OutboxOperations.termsConsentOperations
        assertEquals(
            emptySet(),
            askingForConsent,
            "приватная операция попала в реестр согласий с платными условиями: $askingForConsent",
        )
        // И двусторонне: сервер не сверяет с приватными — клиент не прикладывает.
        val serverAsking = privateOperations intersect serverTermsOperations
        assertEquals(
            emptySet(),
            serverAsking,
            "приёмник требует согласия за приватную операцию: $serverAsking",
        )
    }
}
