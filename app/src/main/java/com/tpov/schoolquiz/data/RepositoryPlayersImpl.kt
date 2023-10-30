package com.tpov.schoolquiz.data

import android.app.Application
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.tpov.schoolquiz.data.database.PlayersDao
import com.tpov.schoolquiz.data.database.entities.PlayersEntity
import com.tpov.schoolquiz.domain.repository.RepositoryPlayers
import com.tpov.schoolquiz.presentation.core.Logcat
import com.tpov.schoolquiz.presentation.core.PATH_LIST_PLAYERS
import com.tpov.schoolquiz.presentation.core.Values
import com.tpov.schoolquiz.presentation.core.Values.context
import com.tpov.schoolquiz.presentation.core.Values.setLoadPB
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Inject

class RepositoryPlayersImpl @Inject constructor(
    private val application: Application,
    private val playersDao: PlayersDao
) : RepositoryPlayers {

    @OptIn(InternalCoroutinesApi::class)
    fun log(m: String) {
        Logcat.log(m, "RepositoryPlayerImpl", Logcat.LOG_FIREBASE)
    }

    override fun getPlayers() = playersDao.getPlayersDB()

    override fun downloadPlayers() {
        Values.loadText.postValue(context.getString(com.tpov.schoolquiz.R.string.fb_load_text_load_leaders))
        val playersListRef = FirebaseDatabase.getInstance().getReference(PATH_LIST_PLAYERS)
        playersListRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val playersList = mutableListOf<PlayersEntity>()
                log("getPlayersList snapshot: $snapshot")
                var i = 0
                for (playerSnapshot in snapshot.children) {
                    i++
                    log("getPlayersList playerSnapshot: $playerSnapshot")
                    val player = playerSnapshot.getValue(PlayersEntity::class.java)
                    if (player != null) {
                        log("getPlayersList player: $player")
                        playersList.add(
                            player.copy(id = playerSnapshot.key?.toInt())
                        )
                        setLoadPB(i, snapshot.childrenCount.toInt())
                    }
                }
                playersDao.deletePlayersList()
                playersDao.insertPlayersList(playersList)
                Values.loadText.postValue("")
            }

            override fun onCancelled(error: DatabaseError) {
                // Обработка ошибок
            }

        })
    }
}