package com.tpov.common.data.model.entity

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tpov.common.data.model.local.QuestionLocal
import com.tpov.common.data.model.remote.QuestionRemote
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.utils.LanguageUtils.Companion.toLanguageUtils
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "question_entity")
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null,

    @ColumnInfo(name = "numQuestion")
    var numQuestion: Int = 0,

    @ColumnInfo(name = "nameQuestion")
    var nameQuestion: String = "",

    @ColumnInfo(name = "infoQuestion")
    var infoQuestion: String = "",

    @ColumnInfo(name = "pictureQuestion")
    var pathPictureQuestion: String? = "",

    @ColumnInfo(name = "nameAnswers")
    var nameAnswers: String = "",

    @ColumnInfo(name = "hardQuestion")
    var hardQuestion: Boolean = false,

    @ColumnInfo(name = "eventName")
    var eventName: String,

    @ColumnInfo(name = "categoryName")
    var categoryName: String,

    @ColumnInfo(name = "subCategoryName")
    var subCategoryName: String,

    @ColumnInfo(name = "subsubCategoryName")
    var subsubCategoryName: String,

    @ColumnInfo(name = "quizName")
    var quizName: String,

    @ColumnInfo(name = "language")
    var language: String = "",

    @ColumnInfo(name = "lvlTranslate")
    var lvlTranslate: Int = 0

) : Parcelable {
    fun toQuestionRemote() = QuestionRemote(
        nameAnswers = this.nameAnswers,
        pathPictureQuestion = this.pathPictureQuestion,
        nameQuestion = this.nameQuestion,
        lvlTranslate = this.lvlTranslate,
        numQuestion = this.numQuestion,
        hardQuestion = this.hardQuestion,
        language = this.language,
        infoQuestion = this.infoQuestion,
            nameCategory = this.categoryName,
            nameSubCategory = this.subCategoryName,
            nameSubsubCategory = this.subsubCategoryName,
            nameQuiz = this.quizName
    )

    //    fun createEmptyQuestion() = QuestionEntity(
//        numQuestion = 1
//    )
    constructor() : this(
        id = null,
        numQuestion = 0,
        nameQuestion = "",
        nameAnswers = "",
        hardQuestion = false,
        eventName = "", categoryName = "", subCategoryName = "", subsubCategoryName = "", quizName = "",
        language = "",
        lvlTranslate = 0,
        pathPictureQuestion = ""
    )

    fun toQuestionLocal() = QuestionLocal(
        id, numQuestion, nameQuestion,infoQuestion, pathPictureQuestion, nameAnswers, hardQuestion, PathStructure(
            eventName, categoryName, subCategoryName, subsubCategoryName, quizName),
            language = this.language.toLanguageUtils(),
            lvlTranslate
        )
}
