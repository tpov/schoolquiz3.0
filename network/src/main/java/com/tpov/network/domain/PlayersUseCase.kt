package com.tpov.network.domain

import com.tpov.network.data.models.local.PlayersEntity
import com.tpov.network.domain.repository.RepositoryPlayers
import javax.inject.Inject

class PlayersUseCase @Inject constructor(private val repositoryPlayers: RepositoryPlayers) {
    fun getPlayers(): List<PlayersEntity> = repositoryPlayers.getPlayers()
    fun downloadPlayers() {
        repositoryPlayers.downloadPlayers()
    }

}