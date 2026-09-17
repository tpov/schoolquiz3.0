package com.tpov.schoolquiz.shared.core.catalog.data

import com.tpov.schoolquiz.shared.core.sync.SYNC_PAGE_SIZE
import com.tpov.schoolquiz.shared.core.sync.SyncCursor

interface CatalogRemoteDataSource {
    /**
     * Страница списка каталогов, начиная с [cursor] (AD-31).
     *
     * Курсор — пара `(lastModifiedAt, id)`, как во всех четырёх журналах, а не одно число. Строгое
     * сравнение по одному времени теряло каталог, чей `lastModifiedAt` совпал с миллисекундой уже
     * стоящего курсора: он не попадал в список, а свой журнал `catalogs/{id}/sync_changes` у него
     * не читается, пока каталога в списке нет, — то есть новый курс не появлялся у игрока вовсе,
     * пока не изменится какой-нибудь другой.
     *
     * Пустой [SyncCursor.docId] означает «в этой миллисекунде ещё ничего не прочитано»: читается
     * вся миллисекунда целиком. Повтор безвреден — применение идёт через версионный upsert.
     *
     * Выборка ограничена страницей: прежде её не было вовсе, и на первом запуске и после каждого
     * ресинка вычитывалась вся коллекция `catalogs` одним ответом.
     */
    suspend fun fetchChangedSince(
        cursor: SyncCursor,
        limit: Int = SYNC_PAGE_SIZE,
    ): List<CatalogDto>

    suspend fun fetchByIds(ids: Set<String>): List<CatalogDto> = emptyList()
}
