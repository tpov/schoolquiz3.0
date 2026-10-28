package com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonEditCopy

/**
 * Транспорт редактируемой копии урока (UX-23, история 4.23): один читающий callable.
 *
 * Владелец выводится сервером из Firebase Auth — в полезной нагрузке его нет. Ответ приводится
 * к доменной проекции строгим парсером; расхождение конверта — отказ чтения.
 */
interface LessonEditCopyRemoteDataSource {
    suspend fun fetchLessonEditCopy(lessonId: String): LessonEditCopy
}
