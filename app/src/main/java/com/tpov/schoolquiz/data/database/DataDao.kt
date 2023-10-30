package com.tpov.schoolquiz.data.database

import androidx.room.*
import com.tpov.schoolquiz.data.database.entities.*

@Dao
interface DataDao {

    @Query("SELECT tpovId FROM profiles WHERE login LIKE :email")
    fun getTpovIdByEmailDB(email: String): Int


    @Query("SELECT * FROM profiles WHERE idFirebase LIKE :uid")
    fun getTpovIdByUidDB(uid: String?): ProfileEntity
}