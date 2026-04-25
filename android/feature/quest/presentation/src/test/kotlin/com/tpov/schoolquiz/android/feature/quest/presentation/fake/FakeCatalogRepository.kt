package com.tpov.schoolquiz.android.feature.quest.presentation.fake

import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeCatalogRepository : CatalogRepository {

    private val store = MutableStateFlow<List<Catalog>>(emptyList())

    override fun observeAll(): Flow<List<Catalog>> = store.map { list ->
        list.filter { !it.archived }
    }

    override suspend fun refreshFromRemote(): Result<Set<CatalogId>> = Result.success(emptySet())

    override suspend fun getById(id: CatalogId): Catalog? = store.value.find { it.id == id }

    fun emit(catalogs: List<Catalog>) {
        store.value = catalogs
    }
}
