package com.tpov.schoolquiz.shared.core.catalog.data

fun interface StorageUrlResolver {
    suspend operator fun invoke(path: String): String
}
