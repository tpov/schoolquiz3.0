package com.tpov.schoolquiz.data.database

import androidx.room.*
import com.tpov.schoolquiz.data.database.entities.*

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