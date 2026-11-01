package com.tpov.schoolquiz.android.feature.quest.presentation.fake

import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeCatalogRepository : CatalogRepository {

    private val store = MutableStateFlow<List<Catalog>>(emptyList())

    /** Сколько раз список каталогов перечитывали с сервера (джорни 4.26 — возврат на полку). */
    var refreshCalls: Int = 0
        private set

    /** Крюк на перечитывание: тест подсаживает каталог, «приехавший» с сервера, прямо здесь. */
    var onRefresh: (() -> Unit)? = null

    override fun observeAll(): Flow<List<Catalog>> = store.map { list ->
        list.filter { !it.archived }
    }

    override suspend fun refreshFromRemote(): Result<Set<CatalogId>> {
        refreshCalls++
        onRefresh?.invoke()
        return Result.success(emptySet())
    }

    override suspend fun getById(id: CatalogId): Catalog? = store.value.find { it.id == id }

    fun emit(catalogs: List<Catalog>) {
        store.value = catalogs
    }
}
