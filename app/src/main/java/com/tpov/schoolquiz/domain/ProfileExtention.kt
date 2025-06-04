package com.tpov.schoolquiz.domain

import com.tpov.common.data.model.NewProfileIds
import com.tpov.common.data.model.ProfileStatus
import com.tpov.common.presentation.utils.DateUtil
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.fierbase.ProfileRemote

object ProfileExtention {
    fun ProfileEntity.createAnonymousProfile(tpovId: NewProfileIds): ProfileEntity {
        return this.copy(
            id = tpovId.tpovId,
            tpovId = tpovId.tpovId,
            nickname = "User${tpovId.tpovId}",
            status = ProfileStatus.ANONYMOUS.statusCode,
            authUid = tpovId.uniqueHash
        )
    }

    fun ProfileEntity.updateFromRemote(remoteProfile: ProfileRemote): ProfileEntity {
        return this.copy(

            nickname = remoteProfile.basic.nickname,
            addMassage = remoteProfile.addPoints.addMassage,
            addPointsGold = remoteProfile.addPoints.addGold.toInt(),
            addPointsNolics = remoteProfile.addPoints.addNolics.toInt(),
            addPointsSkill = remoteProfile.addPoints.addSkill.toInt(),
            addTrophy = remoteProfile.addPoints.addTrophy,

            admin = remoteProfile.qualification.admin.toInt(),
            developer = remoteProfile.qualification.developer.toInt(),
            sponsor = remoteProfile.qualification.sponsor.toInt(),
            tester = remoteProfile.qualification.tester.toInt(),
            moderator = remoteProfile.qualification.moderator.toInt(),
            translater = remoteProfile.qualification.translater.toInt(),

            dateBanned = remoteProfile.dates.dateBanned,
            datePremium = remoteProfile.dates.datePremium,
            dateSynch = DateUtil().getDateQuiz(),

            pointsGold = remoteProfile.points.gold.toInt(),
            trophy = remoteProfile.points.trophy,
            tpovId = remoteProfile.basic.tpovId.toInt(),
                id = remoteProfile.basic.tpovId.toInt()
        )
    }

    fun ProfileEntity.isAdmin(): Boolean {
        return admin == 1
    }

    fun ProfileEntity.isPremium(): Boolean {
        return datePremium != null && datePremium > DateUtil().getDateQuiz()
    }

    fun ProfileEntity.isBanned(): Boolean {
        return dateBanned != null && dateBanned > DateUtil().getDateQuiz()
    }

    fun ProfileEntity.updateStatus(newStatus: ProfileStatus): ProfileEntity {
        return this.copy(status = newStatus.statusCode)
    }


    fun ProfileEntity.resetAddPoints(): ProfileEntity {
        return this.copy(
            addMassage = "",
            addPointsGold = 0,
            addPointsNolics = 0,
            addPointsSkill = 0,
            addTrophy = ""
        )
    }
}
