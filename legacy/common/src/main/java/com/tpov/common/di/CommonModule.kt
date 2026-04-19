package com.tpov.common.di

import android.app.Application
import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.common.data.RepositoryExceptionImpl
import com.tpov.common.data.RepositoryQuestionDetailImpl
import com.tpov.common.data.RepositoryQuestionImpl
import com.tpov.common.data.RepositorySettingLocalImpl
import com.tpov.common.data.RepositorySettingServerImpl
import com.tpov.common.data.database.StructureDataDao
import com.tpov.common.data.database.StructureEditDataDao
import com.tpov.common.domain.repository.RepositoryException
import com.tpov.common.domain.repository.RepositoryQuestion
import com.tpov.common.domain.repository.RepositoryQuestionDetail
import com.tpov.common.domain.repository.RepositorySettingLocal
import com.tpov.common.domain.repository.RepositorySettingServer
import com.tpov.common.domain.usecase.QuestionDetailUseCase
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module(includes = [ViewModelModule::class])
class CommonModule {

    @Provides
    fun provideContext(application: Application): Context {
        return application.applicationContext
    }

    @Provides
    fun provideRepositoryQuestionDetail(impl: RepositoryQuestionDetailImpl): RepositoryQuestionDetail {
        return impl
    }

    @Provides
    fun provideRepositoryQuestion(repositoryQuestionImpl: RepositoryQuestionImpl): RepositoryQuestion {
        return repositoryQuestionImpl
    }

    @Provides
    fun provideQuestionDetailUseCase(repositoryQuestionDetail: RepositoryQuestionDetail): QuestionDetailUseCase {
        return QuestionDetailUseCase(repositoryQuestionDetail)
    }

    @Provides
    fun provideRepositorySettingServer(repositorySettingServerImpl: RepositorySettingServerImpl): RepositorySettingServer {
        return repositorySettingServerImpl
    }

    @Provides
    fun provideRepositoryException(
        structureDataDao: StructureDataDao,
        structureEditDataDao: StructureEditDataDao,
        firestore: FirebaseFirestore,
        context: Context
    ): RepositoryException {
        return RepositoryExceptionImpl(structureDataDao, structureEditDataDao, firestore, context)
    }

    @Provides
    fun provideRepositorySettingLocal(
        firestore: FirebaseFirestore): RepositorySettingLocal {
        return RepositorySettingLocalImpl(firestore)
    }

    @Provides
    fun provideCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
