package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.data.database.entities.PlayersEntity
import com.tpov.schoolquiz.domain.repository.RepositoryPlayers
import javax.inject.Inject

class PlayersUseCase @Inject constructor(private val repositoryPlayers: RepositoryPlayers) {
    fun getPlayers(): List<PlayersEntity> = repositoryPlayers.getPlayers()
    fun downloadPlayers() {
        repositoryPlayers.downloadPlayers()
    }

}