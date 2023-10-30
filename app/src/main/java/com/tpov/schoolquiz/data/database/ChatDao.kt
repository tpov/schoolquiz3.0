package com.tpov.schoolquiz.data.database

import androidx.room.*
import com.tpov.schoolquiz.data.database.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChatDB(chatEntity: ChatEntity)

    fun insertChat(chatEntity: ChatEntity) {
        insertChatDB(chatEntity)
    }

    fun getChat(): Flow<List<ChatEntity>> {
        return getChatDB()
    }

    @Query("SELECT * FROM chat_data ORDER BY id ASC")
    fun getChatDB(): Flow<List<ChatEntity>>

    @Query("DELETE FROM chat_data WHERE time LIKE :time")
    fun deleteChat(time: String)

    @Query("SELECT COUNT(*) FROM chat_data")
    fun getChatCount(): Int

}