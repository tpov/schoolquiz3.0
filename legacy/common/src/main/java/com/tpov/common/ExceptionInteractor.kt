package com.tpov.common

import android.util.Log
import com.tpov.common.domain.repository.RepositoryException
import javax.inject.Inject


class ExceptionInteractor @Inject constructor(
    private val repositoryException: RepositoryException
) {
    fun notFoundInputData() {
        Log.w("ExceptionInteractor", "Input data not found")
    }

    fun notFoundQuizValue() {
        Log.w("ExceptionInteractor", "Quiz value not found")
    }

    fun errorGetNumQuestion() {
        Log.e("ExceptionInteractor", "Error getting question number")
    }

    fun notFoundNumberQuestionByTypeHardQuiz() {
        Log.w("ExceptionInteractor", "Number of questions not found by hard quiz type")
    }

    fun notFoundInitTypeHardQuestion() {
        Log.w("ExceptionInteractor", "Hard question type not initialized")
    }

    fun handleQuizNotFound() {
        Log.w("ExceptionInteractor", "Quiz not found")
    }

    fun handleInputDataNotFound() {
        Log.w("ExceptionInteractor", "Input data not found")
    }

    fun handleQuestionNotFound() {
        Log.w("ExceptionInteractor", "Question not found")
    }

    fun sendErrorToRemote() {
        Log.w("ExceptionInteractor", "Sending error to remote (not implemented)")
        // TODO: Implement remote error reporting when needed
    }

    fun notFoundQuiz() {
        Log.w("ExceptionInteractor", "Quiz not found")
    }

    fun notFountQuestionByLanguageUser() {
        Log.w("ExceptionInteractor", "Question not found for user language")
    }

    fun initStructureDataLocal() {
        Log.w("ExceptionInteractor", "Failed to initialize local structure data")
    }
    
    fun initStructureDataRemote() {
        Log.w("ExceptionInteractor", "Failed to initialize remote structure data")
    }

    fun syncLocalStructureData() {
        Log.w("ExceptionInteractor", "Failed to sync local structure data")
    }

    fun syncRemoteStructureData() {
        Log.w("ExceptionInteractor", "Failed to sync remote structure data")
    }

    fun syncQuestionLocal() {
        Log.w("ExceptionInteractor", "Failed to sync local questions")
    }

    fun syncQuestionRemote() {
        Log.w("ExceptionInteractor", "Failed to sync remote questions")
    }

    fun syncInfo() {
        Log.w("ExceptionInteractor", "Failed to sync info")
    }
}
