package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

/**
 * Намерение ревьюера, поставленное в очередь.
 *
 * [id] рождается вместе с нажатием и не меняется ни при одной попытке (AD-2): из него выводится
 * ключ идемпотентности, и повторная доставка той же записи начислит репутацию ровно один раз.
 * Содержимое ([command]) после этого неизменно — другое решение по тому же заданию будет другим
 * намерением с другим ключом.
 *
 * Локальной половины у действия нет: запись ревью и репутацию пишет только сервер (AD-25), поэтому
 * строка задания на устройстве не меняется, пока журнал ревью не принесёт исчезновение задания.
 */
data class ReviewActionSubmission(
    val id: String,
    val ownerUid: String,
    val requestedAtMs: Long,
    val command: SubmitReviewActionCommand,
) {
    init {
        require(id.isNotBlank()) { "ReviewActionSubmission.id must not be blank" }
        require(ownerUid.isNotBlank()) { "ReviewActionSubmission.ownerUid must not be blank" }
        require(requestedAtMs >= 0L) { "ReviewActionSubmission.requestedAtMs must be >= 0" }
    }
}
