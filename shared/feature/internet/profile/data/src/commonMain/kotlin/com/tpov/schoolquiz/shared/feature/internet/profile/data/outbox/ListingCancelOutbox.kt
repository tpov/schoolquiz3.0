package com.tpov.schoolquiz.shared.feature.internet.profile.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState

/**
 * Строки общей очереди для снятия лота с продажи.
 *
 * Снятие своего лота детерминировано по содержимому — ключ лота и ничего больше, — поэтому оно
 * откладывается (AD-3), в отличие от покупки. Уходит единственным приёмником (AD-6), а не своим
 * callable: только там проверяется ключ идемпотентности.
 *
 * Локальной половины у снятия нет: листинг — серверно-защищённая запись (AD-25), и локально с
 * ней ничего не делается ни при постановке, ни при карантине. Ссылка на сущность нужна ровно для
 * одного: чтобы строка лота могла спросить очередь «что с моим снятием» (AD-14).
 */
object ListingCancelOutbox {
    /** Пространство имён владеющей фичи — то же, что стоит в `operation` (NFR4). */
    const val NICKNAME_REF_PREFIX: String = "profile.NICKNAME_LISTING:"
    const val LOGO_REF_PREFIX: String = "profile.LOGO_LISTING:"

    fun nicknameRef(nickname: String): String = NICKNAME_REF_PREFIX + nickname

    fun logoRef(logo: String): String = LOGO_REF_PREFIX + logo

    /** Тело — ключ лота под тем же именем, что читает прямой callable `cancelNicknameListing`. */
    fun cancelNicknameRow(
        ownerUid: String,
        nickname: String,
        nowMs: Long,
    ): OutboxRecord =
        row(
            operation = OutboxOperations.CANCEL_NICKNAME_LISTING,
            key = nickname,
            ownerUid = ownerUid,
            entityRef = nicknameRef(nickname),
            payload = """{"nickname":${jsonString(nickname)}}""",
            nowMs = nowMs,
        )

    /** Тело — имя логотипа под тем же именем, что читает прямой callable `cancelLogoListing`. */
    fun cancelLogoRow(
        ownerUid: String,
        logo: String,
        nowMs: Long,
    ): OutboxRecord =
        row(
            operation = OutboxOperations.CANCEL_LOGO_LISTING,
            key = logo,
            ownerUid = ownerUid,
            entityRef = logoRef(logo),
            payload = """{"logo":${jsonString(logo)}}""",
            nowMs = nowMs,
        )

    private fun row(
        operation: String,
        key: String,
        ownerUid: String,
        entityRef: String,
        payload: String,
        nowMs: Long,
    ): OutboxRecord {
        require(key.isNotBlank()) { "listing key must not be blank" }
        return OutboxRecord(
            id = 0L,
            mutationId = OutboxOperations.mutationKey(operation, sourceId(key, nowMs)),
            ownerUid = ownerUid,
            operation = operation,
            payload = payload,
            entityRef = entityRef,
            // Лот не версионируется: снятие либо находит его, либо нет.
            expectedVersion = null,
            state = OutboxState.WAITING,
            createdAtMs = nowMs,
        )
    }

    /**
     * Идентификатор намерения, из которого выводится ключ идемпотентности (AD-2).
     *
     * Одного ключа лота мало: имя можно выставить, снять, выставить и снять снова, и второе
     * снятие — новое намерение. Под прежним ключом сервер вернул бы сохранённый ответ первого
     * (AD-1) и лот остался бы на витрине. Поэтому в идентификатор входит момент нажатия.
     *
     * Имя может содержать пробелы и любые буквы, а сервер принимает ключ только из
     * `[A-Za-z0-9_-]`: недопустимые знаки заменяются, а чтобы «a b» и «a_b» не слились, рядом
     * стоит хеш исходной строки.
     */
    private fun sourceId(
        key: String,
        nowMs: Long,
    ): String {
        val slug =
            key.map { if (it.isLetterOrDigit() && it.code < 128 || it == '_' || it == '-') it else '_' }
                .joinToString("")
        val hash = key.hashCode().toUInt().toString(16)
        return "$slug-$hash-$nowMs"
    }

    /** Строка как литерал JSON. Своя, потому что тело здесь — одно поле, и парсер ради него не нужен. */
    private fun jsonString(value: String): String =
        buildString(value.length + 2) {
            append('"')
            value.forEach { ch ->
                when {
                    ch == '"' -> append("\\\"")
                    ch == '\\' -> append("\\\\")
                    ch == '\n' -> append("\\n")
                    ch == '\r' -> append("\\r")
                    ch == '\t' -> append("\\t")
                    ch.code < 0x20 -> append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                    else -> append(ch)
                }
            }
            append('"')
        }
}
