package com.tpov.schoolquiz.data.database.entities

import android.content.res.Resources
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tpov.common.data.model.NewProfileIds
import com.tpov.common.data.model.ProfileStatus
import com.tpov.common.presentation.utils.DateUtil

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    val id: Int? = null,
    val tpovId: Int? = null,
    val login: String? = null,
    val name: String = "",
    val nickname: String? = null,
    val birthday: String = "",
    val datePremium: String = "",
    val dateBanned: String = "",
    val trophy: String = "",
    val friends: String = "",
    val city: String = "",
    val logo: String = "",
    val commander: Int = 0,

    val timeInGamesInQuiz: Int = 0,
    val timeInGamesInChat: Int = 0,
    val smsCount: Int = 0,
    val countAnswer: Int = 0,
    val countTrueAnswer: Int = 0,
    val timeInQuizRating: Int = 0,

    val pointsGold: Int = 0,
    val pointsSkill: Int = 0,
    val pointsNolics: Int = 0,
    val buyQuizPlace: Int = 0,
    val buyTheme: String = "",
    val buyMusic: String = "",
    val buyLogo: String = "",
    val addPointsGold: Int = 0,
    val addPointsSkill: Int = 0,
    val addPointsNolics: Int = 0,
    val addTrophy: String = "",
    val addMassage: String = "",

    val dataCreateAcc: String? = null,
    val dateSynch: String = "",
    val dateCloseApp: String = "",
    val languages: String? = null,

    val status: Int = 0,
    val sponsor: Int? = 0,
    val tester: Int? = 0,
    val translater: Int = 0,
    val moderator: Int = 0,
    val admin: Int = 0,
    val developer: Int = 0,

    val countBox: Int = 0,
    val timeLastOpenBox: String? = null,// Why this?
    val countDayBox: Int = 0,
    val launchCount: Int = 0,

    val standardLife: Int = 0,
    val standardHearts: Int = 1,
    val goldLife: Int = 0,
    val goldHearts: Int = 0,
    val authUid: String? = null
) {
    fun create(tpovId: NewProfileIds?) = ProfileEntity(
        tpovId?.tpovId, tpovId?.tpovId, nickname = if (tpovId != null) "User${tpovId.tpovId}" else null, dataCreateAcc = DateUtil().getDateQuiz(),
        dateCloseApp = DateUtil().getDateQuiz(), languages = Resources.getSystem().configuration.locales[0].language,
        standardLife = 300,
        status = if (tpovId == null) ProfileStatus.OFFLINE.statusCode else ProfileStatus.ANONYMOUS.statusCode,
        authUid = tpovId?.uniqueHash ?: ""
    )
}

