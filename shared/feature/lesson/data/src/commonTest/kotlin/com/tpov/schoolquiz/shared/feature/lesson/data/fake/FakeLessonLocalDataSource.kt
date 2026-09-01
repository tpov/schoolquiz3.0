package com.tpov.schoolquiz.shared.feature.lesson.data.fake

import com.tpov.schoolquiz.shared.core.persistence.LessonEntity
import com.tpov.schoolquiz.shared.feature.lesson.data.LessonLocalDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Fake LessonLocalDataSource for JVM tests.
 * NOTE: LessonLocalDataSource will be created by backend-dev in lesson/data module.
 */
class FakeLessonLocalDataSource : LessonLocalDataSource {

    private val store = mutableMapOf<String, LessonEntity>()
    private val _flow = MutableStateFlow<List<LessonEntity>>(emptyList())

    val upsertCallsFor = mutableMapOf<String, Int>()
    val deletedIds = mutableListOf<String>()

    override fun observeByTheme(themeId: String): Flow<List<LessonEntity>> =
        _flow.map { list -> list.filter { it.themeId == themeId } }

    override suspend fun upsertByIdIfNewerVersion(entity: LessonEntity) {
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

    override suspend fun findById(id: String): LessonEntity? = store[id]

    fun seed(entities: List<LessonEntity>) {
        store.clear()
        entities.forEach { store[it.id] = it }
        _flow.value = store.values.toList()
    }
}
