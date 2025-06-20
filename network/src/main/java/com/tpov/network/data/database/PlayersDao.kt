package com.tpov.network.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tpov.network.data.models.local.PlayersEntity

@Dao
interface PlayersDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPlayersList(playersList: List<PlayersEntity>)

    @Query("DELETE FROM table_players")
    fun deletePlayersList()

    @Query("SELECT * FROM table_players")
    fun getPlayersDB(): List<PlayersEntity>

    @Query("SELECT * FROM table_players WHERE id LIKE :tpovId")
    fun getPlayersDB(tpovId: Int): PlayersEntity
}