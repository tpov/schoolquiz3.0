package com.tpov.schoolquiz.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.common.data.manager.FirebaseRequestInterceptor
import com.tpov.schoolquiz.data.database.ProfileDao
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.fierbase.ProfileRemote
import com.tpov.schoolquiz.data.fierbase.fromHashMap
import com.tpov.schoolquiz.domain.repository.RepositoryProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class RepositoryProfileImpl @Inject constructor(
    private val profileDao: ProfileDao,
    private val firestore: FirebaseFirestore,
) : RepositoryProfile {
    override suspend fun getProfileFlow(): Flow<ProfileEntity?>? {
        return profileDao.getProfileFlow()
    }

    override suspend fun fetchProfile(tpovId: Int): ProfileRemote? {
        val profilesRef = firestore.collection("profiles")
Log.d("FirebaseRequestInterceptor", "fetchProfile $tpovId")
        return try {
            val task = FirebaseRequestInterceptor.executeWithChecksSingleTask {
                profilesRef.document(tpovId.toString()).get()
            }.await()

            if (task.exists()) {
                val profileData = task.data
                fromHashMap(profileData!!)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w("Firestore", "Error fetchProfile", e)
            null
        }
    }


    override suspend fun pushProfile(profileRemote: ProfileRemote) {
        val profilesRef = firestore.collection("profiles")

        Log.d("FirebaseRequestInterceptor", "pushProfile")
        try {
            // Создаем HashMap для Firestore
            val data = HashMap<String, Any>()
            
            // Basic
            val basicMap = HashMap<String, Any>()
            basicMap["tpovId"] = profileRemote.basic.tpovId
            basicMap["login"] = profileRemote.basic.login
            basicMap["name"] = profileRemote.basic.name
            basicMap["nickname"] = profileRemote.basic.nickname
            basicMap["birthday"] = profileRemote.basic.birthday
            basicMap["city"] = profileRemote.basic.city
            basicMap["languages"] = profileRemote.basic.languages
            basicMap["comander"] = profileRemote.basic.comander
            basicMap["logo"] = profileRemote.basic.logo
            data["basic"] = basicMap
            
            // Points
            val pointsMap = HashMap<String, Any>()
            pointsMap["gold"] = profileRemote.points.gold
            pointsMap["skill"] = profileRemote.points.skill
            pointsMap["nolics"] = profileRemote.points.nolics
            pointsMap["trophy"] = profileRemote.points.trophy
            pointsMap["friends"] = profileRemote.points.friends
            data["points"] = pointsMap
            
            // Buy
            val buyMap = HashMap<String, Any>()
            buyMap["quizPlace"] = profileRemote.buy.quizPlace
            buyMap["theme"] = profileRemote.buy.theme
            buyMap["music"] = profileRemote.buy.music
            buyMap["logo"] = profileRemote.buy.logo
            data["buy"] = buyMap
            
            // TimeInGames
            val timeInGamesMap = HashMap<String, Any>()
            timeInGamesMap["timeInQuiz"] = profileRemote.timeInGames.timeInQuiz
            timeInGamesMap["timeInChat"] = profileRemote.timeInGames.timeInChat
            timeInGamesMap["smsPoints"] = profileRemote.timeInGames.smsPoints
            timeInGamesMap["countQuestions"] = profileRemote.timeInGames.countQuestions ?: 0L
            timeInGamesMap["countTrueQuestion"] = profileRemote.timeInGames.countTrueQuestion
            timeInGamesMap["quizRating"] = profileRemote.timeInGames.quizRating
            data["timeInGames"] = timeInGamesMap
            
            // AddPoints
            val addPointsMap = HashMap<String, Any>()
            addPointsMap["addGold"] = profileRemote.addPoints.addGold
            addPointsMap["addSkill"] = profileRemote.addPoints.addSkill
            addPointsMap["addNolics"] = profileRemote.addPoints.addNolics
            addPointsMap["addTrophy"] = profileRemote.addPoints.addTrophy
            addPointsMap["addMassage"] = profileRemote.addPoints.addMassage
            data["addPoints"] = addPointsMap
            
            // Dates
            val datesMap = HashMap<String, Any>()
            datesMap["dataCreateAcc"] = profileRemote.dates.dataCreateAcc
            datesMap["dateSynch"] = profileRemote.dates.dateSynch
            datesMap["datePremium"] = profileRemote.dates.datePremium
            datesMap["dateBanned"] = profileRemote.dates.dateBanned
            data["dates"] = datesMap
            
            // Qualification
            val qualificationMap = HashMap<String, Any>()
            qualificationMap["sponsor"] = profileRemote.qualification.sponsor
            qualificationMap["tester"] = profileRemote.qualification.tester
            qualificationMap["translater"] = profileRemote.qualification.translater
            qualificationMap["moderator"] = profileRemote.qualification.moderator
            qualificationMap["admin"] = profileRemote.qualification.admin
            qualificationMap["developer"] = profileRemote.qualification.developer
            data["qualification"] = qualificationMap
            
            // Life
            val lifeMap = HashMap<String, Any>()
            lifeMap["countLife"] = profileRemote.life.countLife
            lifeMap["countGoldLife"] = profileRemote.life.countGoldLife
            data["life"] = lifeMap
            
            // Box
            val boxMap = HashMap<String, Any>()
            boxMap["countBox"] = profileRemote.box.countBox
            boxMap["timeLastOpenBox"] = profileRemote.box.timeLastOpenBox
            boxMap["countDayBox"] = profileRemote.box.countDayBox
            data["box"] = boxMap
            
            FirebaseRequestInterceptor.executeWithChecksSingleTask {
                profilesRef.document(profileRemote.basic.tpovId.toString()).set(data)
            }.await()
        } catch (e: Exception) {
            Log.w("Firestore", "Error pushProfile", e)
        }
    }


    override suspend fun getProfile(): ProfileEntity {
        return profileDao.getProfile()
    }

    override suspend fun insertProfile(profile: ProfileEntity) {
        profileDao.insertProfile(profile)
    }

    override suspend fun updateProfile(profile: ProfileEntity) {
        profileDao.updateProfiles(profile)
    }
}
