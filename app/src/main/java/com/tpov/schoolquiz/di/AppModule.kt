package com.tpov.schoolquiz.di

import android.app.Application
import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.storage.FirebaseStorage
import com.tpov.common.data.RepositoryQuestionImpl
import com.tpov.common.data.RepositoryQuizImpl
import com.tpov.common.data.RepositoryStuctureImpl
import com.tpov.common.data.database.StructureCategoryDataDao
import com.tpov.common.data.database.StructureRatingDataDao
import com.tpov.common.domain.QuestionUseCase
import com.tpov.common.domain.QuizUseCase
import com.tpov.common.domain.StructureUseCase
import com.tpov.schoolquiz.data.RepositoryProfileImpl
import com.tpov.schoolquiz.domain.ProfileUseCase
import dagger.Module
import dagger.Provides
import javax.inject.Singleton


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
    fun provideQuizUseCase(repositoryQuizImpl: RepositoryQuizImpl): QuizUseCase {
        return QuizUseCase(repositoryQuizImpl)
    }

    @Provides
    @Singleton
    fun provideQuestionUseCase(repositoryQuestionImpl: RepositoryQuestionImpl): QuestionUseCase {
        return QuestionUseCase(repositoryQuestionImpl)
    }

    @Provides
    @Singleton
    fun provideProfileUseCase(repositoryProfileImpl: RepositoryProfileImpl): ProfileUseCase {
        return ProfileUseCase(repositoryProfileImpl)
    }

    @Provides
    @Singleton
    fun provideRepositoryStructureImpl(
        structureRatingDataDao: StructureRatingDataDao,
        structureCategoryDataDao: StructureCategoryDataDao,
        firestore: FirebaseFirestore,
        context: Context
    ): RepositoryStuctureImpl {
        return RepositoryStuctureImpl(structureRatingDataDao,structureCategoryDataDao, firestore, context)
    }

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {

        val firestore = FirebaseFirestore.getInstance()
        firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        return firestore
    }

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage {
        return FirebaseStorage.getInstance()
    }
}
