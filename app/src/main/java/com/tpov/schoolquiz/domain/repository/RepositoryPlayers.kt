package com.tpov.schoolquiz.domain.repository

import com.tpov.schoolquiz.data.database.entities.PlayersEntity

interface RepositoryPlayers {
    fun getPlayers(): List<PlayersEntity>

    fun downloadPlayers()
}