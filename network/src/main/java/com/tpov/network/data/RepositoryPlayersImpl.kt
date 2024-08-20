package com.tpov.network.data

import android.app.Application
import com.tpov.network.data.database.PlayersDao
import com.tpov.network.domain.repository.RepositoryPlayers
import javax.inject.Inject

class RepositoryPlayersImpl @Inject constructor(
    private val application: Application,
    private val playersDao: PlayersDao
) : RepositoryPlayers {


    override fun getPlayers() = playersDao.getPlayersDB()

    override fun downloadPlayers() {

    }
}