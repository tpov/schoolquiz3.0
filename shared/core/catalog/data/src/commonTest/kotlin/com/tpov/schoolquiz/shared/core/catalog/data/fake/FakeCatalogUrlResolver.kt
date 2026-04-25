package com.tpov.schoolquiz.shared.core.catalog.data.fake

import com.tpov.schoolquiz.shared.core.catalog.data.StorageUrlResolver

class FakeCatalogUrlResolver {
    var callCount: Int = 0
    var shouldThrow: Boolean = false

    val resolver: StorageUrlResolver = StorageUrlResolver { path ->
        callCount++
        if (shouldThrow) throw RuntimeException("fake resolver error")
        else "https://fake.example.com/$path"
    }
}
