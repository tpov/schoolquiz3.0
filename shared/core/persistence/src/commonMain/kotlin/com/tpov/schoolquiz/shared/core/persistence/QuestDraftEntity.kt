package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quest_drafts",
    indices = [
        Index(value = ["ownerUid"]),
        Index(value = ["catalogId"]),
        Index(value = ["updatedAtMs"]),
        Index(value = ["isActive"]),
        Index(value = ["ownerUid", "status", "activeSubmissionId"]),
    ],
)
data class QuestDraftEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val catalogId: String,
    val title: String,
    val description: String?,
    val defaultLanguage: String,
    val defaultDifficulty: String,
    val status: String,
    val localRevision: Long,
    val serverRevision: Long?,
    val publicQuestId: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val isActive: Boolean,
    /**
     * Why a reviewer sent this draft back, verbatim. Null unless the last submission was
     * rejected; cleared when the author resubmits.
     */
    val rejectionReason: String? = null,
    /**
     * The one server submission that is allowed to settle this awaiting draft.
     *
     * It is deliberately local bookkeeping rather than a user-visible draft field: dropping it
     * after the outbox row is delivered would let an old terminal outcome settle a later submit
     * with the same draft ID.
     */
    val activeSubmissionId: String? = null,
    /**
     * Настройки приватной ссылки (AD-29) — три авторских поля, по столбцу на каждое.
     *
     * Все три `null` одновременно: квест неприватный, настроек у него нет. Загрузка
     * существующего приватного квиза восстанавливает сохранённое, а не дефолты заново.
     * Рантайм (`enabled`/`startedCount`) здесь не живёт: это серверные слова.
     */
    val privateQuizAttemptLimit: Int? = null,
    val privateQuizShowResult: Boolean? = null,
    val privateQuizLinkExpiresAtMs: Long? = null,
) {
    /** Настройки приватной ссылки целиком; `null` — квест неприватный. */
    val privateQuiz: PrivateQuizSettingsColumns?
        get() =
            if (privateQuizAttemptLimit == null && privateQuizShowResult == null && privateQuizLinkExpiresAtMs == null) {
                null
            } else {
                PrivateQuizSettingsColumns(
                    attemptLimit = privateQuizAttemptLimit,
                    showResult = privateQuizShowResult,
                    linkExpiresAtMs = privateQuizLinkExpiresAtMs,
                )
            }
}

/**
 * Срез настроек приватной ссылки над тремя nullable-колонками.
 *
 * Частично заполненная строка (один столбец задан, других нет) читается как есть: округлять её
 * до дефолтов значило бы выдумывать факт. Домен отвергнет такой набор своим init'ом — громко,
 * а не подстановкой.
 */
data class PrivateQuizSettingsColumns(
    val attemptLimit: Int?,
    val showResult: Boolean?,
    val linkExpiresAtMs: Long?,
)
