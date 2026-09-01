package com.tpov.schoolquiz.shared.feature.question.data.fake

import com.tpov.schoolquiz.shared.core.persistence.QuestionEntity
import com.tpov.schoolquiz.shared.feature.question.data.QuestionLocalDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Fake QuestionLocalDataSource for JVM tests.
 */
class FakeQuestionLocalDataSource : QuestionLocalDataSource {

    private val store = mutableMapOf<String, QuestionEntity>()
    private val _flow = MutableStateFlow<List<QuestionEntity>>(emptyList())

    val upsertCallsFor = mutableMapOf<String, Int>()
    val deletedIds = mutableListOf<String>()

    override fun observeByLesson(lessonId: String): Flow<List<QuestionEntity>> =
        _flow.map { list -> list.filter { it.lessonId == lessonId } }

    override suspend fun upsertByIdIfNewerVersion(entity: QuestionEntity) {
        upsertCallsFor[entity.id] = (upsertCallsFor[entity.id] ?: 0) + 1
        val current = store[entity.id]
        if (current == null || current.version < entity.version) {
            store[entity.id] = entity
            _flow.value = store.values.toList()
        }
    }

    override suspend fun deleteById(id: String) {
        deletedIds.add(id)
        store.remove(id)
        _flow.value = store.values.toList()
    }

    override suspend fun findById(id: String): QuestionEntity? = store[id]

    fun seed(entities: List<QuestionEntity>) {
        store.clear()
        entities.forEach { store[it.id] = it }
        _flow.value = store.values.toList()
    }
}
