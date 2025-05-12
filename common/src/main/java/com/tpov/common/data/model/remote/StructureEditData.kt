package com.tpov.common.data.model.remote

import androidx.room.Entity
import androidx.room.PrimaryKey

//Єта моделька используется для редактировании категорий квеста на сервере, их удаление, копирование, перемещения и любые другие манипуляции
@Entity
data class StructureEditData(
    @PrimaryKey(autoGenerate = true)
    val id: Int?,
    val nameEventFrom: String,
    val nameCategoryFrom: String,
    val nameSubCategoryFrom: String,
    val nameSubsubCategoryFrom: String,
    val nameQuizFrom: String,

    val nameEventTo: String,
    val nameCategoryTo: String,
    val nameSubCategoryTo: String,
    val nameSubsubCategoryTo: String,
    val nameQuizTo: String,

    val deleteOld: Boolean,
    val clearData: Boolean
)
