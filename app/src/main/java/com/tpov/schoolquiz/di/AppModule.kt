package com.tpov.schoolquiz.di

import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.common.data.RepositoryStuctureImpl
import com.tpov.common.data.database.StructureRatingDataDao
import com.tpov.common.domain.StructureUseCase
import com.tpov.schoolquiz.presentation.DaggerWorkerFactory
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module(includes = [DatabaseModule::class])
class AppModule {

    @Provides
    @Singleton
    fun provideStructureUseCase(repositoryStuctureImpl: RepositoryStuctureImpl): StructureUseCase {
        return StructureUseCase(repositoryStuctureImpl)
    }

    @Provides
    @Singleton
    fun provideRepositoryStructureImpl(
        structureRatingDataDao: StructureRatingDataDao,
        firestore: FirebaseFirestore
    ): RepositoryStuctureImpl {
        return RepositoryStuctureImpl(structureRatingDataDao, firestore)
    }

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    @Provides
    fun provideWorkerFactory(
        structureUseCase: StructureUseCase
    ): DaggerWorkerFactory {
        return DaggerWorkerFactory(structureUseCase)
    }
}