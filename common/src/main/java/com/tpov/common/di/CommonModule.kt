package com.tpov.common.di

import android.app.Application
import android.content.Context
import com.tpov.common.data.RepositoryExceptionImpl
import com.tpov.common.data.RepositoryQuestionDetailImpl
import com.tpov.common.data.RepositoryQuestionImpl
import com.tpov.common.domain.repository.RepositoryException
import com.tpov.common.domain.repository.RepositoryQuestion
import com.tpov.common.domain.repository.RepositoryQuestionDetail
import com.tpov.common.domain.usecase.QuestionDetailUseCase
import dagger.Module
import dagger.Provides


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
    fun provideRepositoryException(repositoryExceptionImpl: RepositoryExceptionImpl): RepositoryException {
        return repositoryExceptionImpl
    }
    @Provides
    fun provideQuestionDetailUseCase(repositoryQuestionDetail: RepositoryQuestionDetail): QuestionDetailUseCase {
        return QuestionDetailUseCase(repositoryQuestionDetail)
    }
}