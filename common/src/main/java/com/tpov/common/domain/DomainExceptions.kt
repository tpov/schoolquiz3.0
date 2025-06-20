package com.tpov.common.domain

import com.tpov.common.ExceptionHandler
import com.tpov.common.ExceptionInteractor

open class DomainExceptions(
    beforeException: (String) -> Unit,
    afterException: () -> Unit,
    interactor: ExceptionInteractor,
) : ExceptionHandler(
    beforeException,
    afterException,
    interactor
) {

    inline fun <reified T> exceptionInitStructureLocalData(): T =
        handleException("exception Init Structure Local Data") {
            interactor.initStructureDataLocal()
        }

    inline fun <reified T> exceptionInitStructureRemoteData(messege: String = "exception Init Structure Remote Data"): T =
        handleException(messege) {
            interactor.initStructureDataRemote()
        }

    inline fun <reified T> exceptionSyncLocalStructureData(messege: String = "exceptionSyncLocalStructureData"): T =
        handleException(messege) {
            interactor.syncLocalStructureData()
        }

    inline fun <reified T> exceptionSyncRemoteStructureData(): T =
        handleException("exceptionSyncRemoteStructureData") {
            interactor.syncRemoteStructureData()
        }

    inline fun <reified T> exceptionSyncQuestionLocal(): T =
        handleException("exceptionSyncQuestionLocal") {
            interactor.syncQuestionLocal()
        }

    inline fun <reified T> exceptionSyncQuestionRemote(message: String = "exceptionSyncQuestionRemote"): T =
        handleException(message) {
            interactor.syncQuestionRemote()
        }

    inline fun <reified T> exceptionSyncInfo(message: String = "exceptionSyncInfo"): T =
        handleException(message) {
            interactor.syncInfo()
        }
}
