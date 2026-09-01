package com.tpov.schoolquiz.shared.feature.theme.data.fake

import com.tpov.schoolquiz.shared.core.persistence.ThemeEntity
import com.tpov.schoolquiz.shared.feature.theme.data.ThemeLocalDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Fake ThemeLocalDataSource for JVM tests.
 * NOTE: ThemeLocalDataSource interface will be created by backend-dev in theme/data module.
 * This file compiles once ThemeLocalDataSource exists in shared/feature/theme/data.
 */
class FakeThemeLocalDataSource : ThemeLocalDataSource {

    private val store = mutableMapOf<String, ThemeEntity>()
    private val _flow = MutableStateFlow<List<ThemeEntity>>(emptyList())

    val upsertCallsFor = mutableMapOf<String, Int>()
    val deletedIds = mutableListOf<String>()

    override fun observeBySection(sectionId: String): Flow<List<ThemeEntity>> =
        _flow.map { list -> list.filter { it.sectionId == sectionId } }

    override suspend fun upsertByIdIfNewerVersion(entity: ThemeEntity) {
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

    override suspend fun findById(id: String): ThemeEntity? = store[id]

    fun seed(entities: List<ThemeEntity>) {
        store.clear()
        entities.forEach { store[it.id] = it }
        _flow.value = store.values.toList()
    }
}
