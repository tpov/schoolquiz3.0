package com.tpov.common

import com.tpov.common.domain.repository.RepositoryException
import javax.inject.Inject

class Interactor @Inject constructor(
    private val repositoryException: RepositoryException
) {
    fun notFoundInputData() {

    }

    fun notFoundQuizValue() {
        TODO("Not yet implemented")
    }

    fun errorGetNumQuestion() {
        TODO("Not yet implemented")
    }

    fun notFoundNumberQuestionByTypeHardQuiz() {
        TODO("Not yet implemented")
    }

    fun notFoundInitTypeHardQuestion() {
        TODO("Not yet implemented")
    }

    fun handleQuizNotFound() {
        TODO("Not yet implemented")
    }

    fun handleInputDataNotFound() {
        TODO("Not yet implemented")
    }

    fun handleQuestionNotFound() {
        TODO("Not yet implemented")
    }

    fun sendErrorToRemote() {
        TODO("Not yet implemented")
    }

    fun notFoundQuiz() {
        TODO("Not yet implemented")
    }

    fun notFountQuestionByLanguageUser() {
        TODO("Not yet implemented")
    }


    fun initStructureDataLocal() {

        TODO("Not yet implemented")
    }
    fun initStructureDataRemote() {

        TODO("Not yet implemented")
    }

    fun syncLocalStructureData() {

        TODO("Not yet implemented")
    }

    fun syncRemoteStructureData() {

        TODO("Not yet implemented")
    }

    fun syncQuestionLocal() {

        TODO("Not yet implemented")
    }

    fun syncQuestionRemote() {

        TODO("Not yet implemented")
    }

    fun syncInfo() {

        TODO("Not yet implemented")
    }

}