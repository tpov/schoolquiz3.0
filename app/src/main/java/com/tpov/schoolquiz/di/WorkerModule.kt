package com.tpov.schoolquiz.di

import androidx.work.ListenableWorker
import com.tpov.schoolquiz.presentation.ChildWorkerFactory
import com.tpov.schoolquiz.presentation.SyncWorker
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.multibindings.IntoMap
import kotlin.reflect.KClass

@Module
abstract class WorkerModule {


    @Binds
    @IntoMap
    @WorkerKey(SyncWorker::class)
    abstract fun bindSyncWorkerFactory(factory: SyncWorker.Factory): ChildWorkerFactory
}

@MustBeDocumented
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MapKey
annotation class WorkerKey(val value: KClass<out ListenableWorker>)