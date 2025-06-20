package com.tpov.schoolquiz.di

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.storage.FirebaseStorage
import com.tpov.common.data.RepositoryQuestionImpl
import com.tpov.common.data.RepositoryStructureImpl
import com.tpov.common.di.CommonComponent
import com.tpov.common.domain.usecase.QuestionUseCase
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.schoolquiz.data.RepositoryProfileImpl
import com.tpov.schoolquiz.domain.ProfileUseCase
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton


@Module(includes = [DatabaseModule::class])
class AppModule {

    @Provides
    @Singleton
    fun provideStructureUseCase(
        repositoryStructureImpl: RepositoryStructureImpl,
    ): StructureUseCase {
        return StructureUseCase(
            repositoryStructureImpl
        )
    }
    @Provides
    @Singleton
    fun provideCommonComponentProvider(commonComponent: CommonComponent): CommonComponentProvider {
        return object : CommonComponentProvider {
            override fun provideCommonComponent(): CommonComponent {
                return commonComponent
            }
        }
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

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): com.google.firebase.functions.FirebaseFunctions {
        return com.google.firebase.functions.FirebaseFunctions.getInstance()
    }

    @Provides
    @Singleton
    fun provideCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
