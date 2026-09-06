package com.tpov.schoolquiz.platform.firebase.push

/**
 * Полезная нагрузка сообщения — ровно три поля (AD-20).
 *
 * Чисел баланса, награды или уровня здесь нет и быть не может: приложение не применяет ничего из
 * этого объекта к своим таблицам, а идёт за данными обычной синхронизацией.
 */
data class PushMessage(
    val eventType: String?,
    val entityRef: String?,
    val targetUid: String?,
) {
    companion object {
        const val EVENT_TYPE = "eventType"
        const val ENTITY_REF = "entityRef"
        const val TARGET_UID = "targetUid"

        fun fromData(data: Map<String, String>): PushMessage =
            PushMessage(
                eventType = data[EVENT_TYPE],
                entityRef = data[ENTITY_REF],
                targetUid = data[TARGET_UID],
            )
    }
}
