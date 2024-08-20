package com.tpov.network.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tpov.common.data.custom.Converters
import com.tpov.network.data.models.local.ChatEntity
import com.tpov.network.data.models.local.PlayersEntity
import kotlinx.coroutines.InternalCoroutinesApi

@Database(
    entities = [PlayersEntity::class, ChatEntity::class],
    version = 2,
    exportSchema = true
)

@TypeConverters(Converters::class)
abstract class NetworkDatabase : RoomDatabase() {
    abstract fun getChatDao(): ChatDao
    abstract fun getPlayersDao(): PlayersDao

    companion object {
        @Volatile
        private var INSTANCE: NetworkDatabase? = null

        @InternalCoroutinesApi
        fun getDatabase(context: Context): NetworkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext, NetworkDatabase::class.java, "NetworkData.db"
                ).fallbackToDestructiveMigration().allowMainThreadQueries().build()
                INSTANCE = instance
                instance
            }
        }
    }
}