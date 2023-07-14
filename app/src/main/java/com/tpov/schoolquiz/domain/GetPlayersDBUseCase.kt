package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.data.database.entities.PlayersEntity
import com.tpov.schoolquiz.domain.repository.RepositoryDB
import javax.inject.Inject

class GetPlayersDBUseCase @Inject constructor(private val repository: RepositoryDB) {
    operator fun invoke(): List<PlayersEntity> = repository.getPlayersDB()
}