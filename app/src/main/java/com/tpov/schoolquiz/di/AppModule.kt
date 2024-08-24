package com.tpov.schoolquiz.di

import android.app.Application
import android.content.Context
import androidx.work.ListenableWorker
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.inject.assisted.dagger2.AssistedModule
import com.tpov.common.data.RepositoryStuctureImpl
import com.tpov.common.data.database.StructureRatingDataDao
import com.tpov.common.domain.StructureUseCase
import com.tpov.schoolquiz.presentation.AppWorkerFactory
import com.tpov.schoolquiz.presentation.ChildWorkerFactory
import com.tpov.schoolquiz.presentation.SyncWorker
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.reflect.KClass

@Module(includes = [DatabaseModule::class])
class AppModule {

    @Provides
    @Singleton
    fun provideApplicationContext(application: Application): Context = application

    @Provides
    @Singleton
    fun provideStructureUseCase(repositoryStuctureImpl: RepositoryStuctureImpl): StructureUseCase {
        return StructureUseCase(repositoryStuctureImpl)
    }

    @Provides
    @Singleton
    fun provideRepositoryStructureImpl(
        structureRatingDataDao: StructureRatingDataDao,
        firestore: FirebaseFirestore,
        context: Context
    ): RepositoryStuctureImpl {
        return RepositoryStuctureImpl(structureRatingDataDao, firestore, context)
    }

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    @Provides
    @Singleton
    fun provideWorkerFactory(
        workerFactories: Map<Class<out ListenableWorker>, @JvmSuppressWildcards Provider<ChildWorkerFactory>>
    ): AppWorkerFactory {
        return AppWorkerFactory(workerFactories)
    }
}

@Module
abstract class WorkerBindingModule {
    @Binds
    @IntoMap
    @WorkerKey(SyncWorker::class)
    abstract fun bindSyncWorker(factory: SyncWorker.Factory): ChildWorkerFactory
}

@MapKey
annotation class WorkerKey(val value: KClass<out ListenableWorker>)

@Module(includes = [AssistedInject_AssistedInjectModule::class])
@AssistedModule
interface AssistedInjectModule