package com.tpov.schoolquiz.platform.firebase.di

import com.google.firebase.storage.FirebaseStorage
import com.tpov.schoolquiz.platform.firebase.catalog.FirebaseCatalogRemoteDataSource
import com.tpov.schoolquiz.shared.core.catalog.data.CatalogRemoteDataSource
import com.tpov.schoolquiz.shared.core.catalog.data.StorageUrlResolver
import kotlinx.coroutines.tasks.await
import org.koin.core.qualifier.named
import org.koin.dsl.module

val firebaseCatalogModule =
    module {
        single<CatalogRemoteDataSource> {
            FirebaseCatalogRemoteDataSource(get())
        }
        single<StorageUrlResolver>(named("storageUrlResolver")) {
            StorageUrlResolver { path ->
                FirebaseStorage.getInstance().reference.child(path).downloadUrl.await().toString()
            }
        }
    }
