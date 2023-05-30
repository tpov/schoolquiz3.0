package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.domain.repository.RepositoryDB
import javax.inject.Inject

class DeleteChatUseCase @Inject constructor(private val repositoryDB: RepositoryDB) {
    operator fun invoke(time: String) =
        repositoryDB.deleteChat(time)
}