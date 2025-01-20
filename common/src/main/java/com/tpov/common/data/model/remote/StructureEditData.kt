package com.tpov.common.data.model.remote

import androidx.room.Entity
import androidx.room.PrimaryKey

//Єта моделька используется для редактировании категорий квеста на сервере, их удаление, копирование, перемещения и любые другие манипуляции
@Entity
data class StructureEditData(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val idEventFrom: Int,
    val idCategoryFrom: Int,
    val idSubCategoryFrom: Int,
    val idSubsubCategoryFrom: Int,
    val idQuizFrom: Int,

    val idEventTo: Int,
    val idCategoryTo: Int,
    val idSubCategoryTo: Int,
    val idSubsubCategoryTo: Int,
    val idQuizTo: Int,

    val nameEventTo: String,
    val nameCategoryTo: String,
    val nameSubCategoryTo: String,
    val nameSubsubCategoryTo: String,
    val nameQuizTo: String,

    val deleteOld: Boolean,
    val clearData: Boolean
)