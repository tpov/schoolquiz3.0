package com.tpov.schoolquiz.shared.core.catalog.data.fake

import com.tpov.schoolquiz.shared.core.persistence.CatalogDao
import com.tpov.schoolquiz.shared.core.persistence.CatalogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeCatalogDao : CatalogDao {
    private val _flow = MutableStateFlow<List<CatalogEntity>>(emptyList())
    var replaceAllCalls: Int = 0

    override fun observeAll(): Flow<List<CatalogEntity>> =
        _flow.map { list -> list.filter { !it.archived }.sortedBy { it.id } }

    override suspend fun findById(id: String): CatalogEntity? = _flow.value.find { it.id == id }

    override suspend fun insertOrReplace(entity: CatalogEntity) {
        _flow.value = _flow.value.filter { it.id != entity.id } + entity
    }

    override suspend fun deleteById(id: String) {
        _flow.value = _flow.value.filter { it.id != id }
    }

    override suspend fun insertAll(entities: List<CatalogEntity>) {
        _flow.value = entities
    }

    override suspend fun deleteAll() {
        _flow.value = emptyList()
    }

    override suspend fun replaceAll(entities: List<CatalogEntity>) {
        replaceAllCalls++
        _flow.value = entities
    }

    fun emit(entities: List<CatalogEntity>) {
        _flow.value = entities
    }
}
