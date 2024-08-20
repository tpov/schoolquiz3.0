package com.tpov.network.domain.repository

import com.tpov.network.data.models.local.PlayersEntity

interface RepositoryPlayers {
    fun getPlayers(): List<PlayersEntity>

    fun downloadPlayers()
}