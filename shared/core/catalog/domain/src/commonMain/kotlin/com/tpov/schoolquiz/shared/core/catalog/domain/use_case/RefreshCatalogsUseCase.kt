package com.tpov.schoolquiz.shared.core.catalog.domain.use_case

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository

/**
 * Честное перечитывание списка каталогов с сервера (джорни 4.26).
 *
 * Полка читает локальный кэш; приезжает каталог только с синком, а синк — по расписанию
 * очереди. Экран, который остаётся открытым, обязан уметь перечитать сам: возврат на полку
 * вызывает [CatalogRepository.refreshFromRemote], и каталог, опубликованный после прошлого
 * прохода, появляется без ожидания планировщика. Отказ чтения полку не ломает — список
 * остаётся как был.
 */
class RefreshCatalogsUseCase(
    private val catalogs: CatalogRepository,
) {
    suspend operator fun invoke(): Result<Set<CatalogId>> = catalogs.refreshFromRemote()
}
