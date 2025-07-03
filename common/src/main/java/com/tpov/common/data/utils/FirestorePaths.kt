package com.tpov.common.data.utils

import android.util.Pair
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.domain.model.EventQuiz.Companion.fromInput
import com.tpov.common.domain.usecase.SettingConfigObject.settingsConfig
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.utils.LanguageUtils
import com.tpov.common.presentation.utils.DateUtil
import kotlinx.coroutines.tasks.await

object FirestorePaths {
    // Base paths
    const val STRUCTURES = "structures"

    // Quiz types
    const val QUIZ_HOME = "QUIZ_HOME"
    const val QUIZ_ARENA = "QUIZ_ARENA"
    const val QUIZ_USER = "QUIZ_USER"
    const val QUIZ_TOURNAMENT = "QUIZ_TOURNAMENT"

    // Questions
    const val QUESTIONS = "questions"
    const val QUESTIONS_DETAIL = "questionDetail"
    const val STRUCTURE_INFO_LOCAL = "$STRUCTURES/structureInfo"
    const val STRUCTURE_DATA = "$STRUCTURES/structureData"

    const val REFERAL_USER = "referals"

    const val NAMING_RULES = "variable/namingRules"
    const val SERVER_CONFIG = "variable/serverConfig"
    const val TRANSLATE_CONFIG = "variable/translateConfig"

    //Storage
    const val QUESTIONS_PHOTO = "questionPhoto"
    const val MUSICS = "music"
    const val QUIZES_PHOTO = "quizPhoto"
    const val ICONS_PROFILE = "profileIcons"

    // ==== Realtime Chat Paths ====
    fun chatByDate() = "chat/$date"
    const val PLAYER_ONLINE_LIST = "playerOnlineList"
    const val TIME_FOR_MASSAGE = "timeForMassage"
    const val CHAT_VALUES = "values"
    const val IS_OPEN_CHAT = "values/openChat"
    fun timeForMassageRole() = "timeForMassage"

    fun getStructureDataCategoryList(path: PathStructure) = "$STRUCTURE_DATA/${path.nameEvent}"
    fun getStructureDataCategoryListByUser() =
        "$STRUCTURE_DATA/${EventQuiz.QUIZ_BY_USER}/tpovIdList/${settingsConfig.tpovId}"

    fun getQuestionPath(path: PathStructure, language: LanguageUtils) =
        "$QUESTIONS/${fromInput(path.nameEvent)}/$language"

    fun getQuestionByUserPath(path: PathStructure) = "$QUESTIONS/${fromInput(path.nameEvent)}/${settingsConfig.tpovId}"

    fun getQuestionsDetailPath(path: PathStructure) =
        "${QUESTIONS_DETAIL}/${fromInput(path.nameEvent)}/${path.toPath()}"

    fun getQuestionsDetailByUserPath(path: PathStructure) =
        "${QUESTIONS_DETAIL}/${fromInput(path.nameEvent)}/${settingsConfig.tpovId}"

    fun getPathStructureInfoLocalListByQuiz(path: PathStructure) =
        "$STRUCTURE_INFO_LOCAL/${path.nameEvent}/infoLocalListByQuiz/${path.toPath()}"

    fun getPathStructureInfoLocalListByTpovId(path: PathStructure) =
        "$STRUCTURE_INFO_LOCAL/${path.nameEvent}/infoLocalListByTpovId/${settingsConfig.tpovId}"

    fun getPathStructureRatingUserListByQuiz(path: PathStructure) =
        "$STRUCTURE_INFO_LOCAL/${path.nameEvent}/ratingList/${path.toPath()}" //получает список где имена доументов - место


    fun getReferalList() =
        "$REFERAL_USER/${settingsConfig.tpovId}/referalList" // получает список где название доумента - айди приглашенного

    fun getNamesUserReg() = "$NAMING_RULES/usernameReg"
    fun getNamesQuizReg() = "$NAMING_RULES/quiznameReg"

    fun getUsernameList() = "$NAMING_RULES/usernameList"
    fun getLastTpovId() = "variable/lastId/tpovId"
    fun getEventLock(path: PathStructure) = "$SERVER_CONFIG/structureLocks/${path.nameEvent}"
    fun getTranslateConfig() = "$TRANSLATE_CONFIG"

    fun chatByUnixDay(): String {
        val unixDay = DateUtil().getUnixDay()
        return "chat/$unixDay"
    }
}

/**
 * 🔗 Builder класс для одного запроса к одному языку
 *
 * Структура: questions/{event}/{language}/{auto-hash} -> QuestionEntity
 *
 * Использование:
 * ```
 *
 */
class PathBuilder(
    private val collectionPath: String
) {
    private val filters = mutableListOf<Pair<String, Any?>>()
    private var limitValue: Int? = null

    fun queryFilter(field: String, value: Any?): PathBuilder {
        filters.add(Pair(field, value))
        return this
    }

    fun limit(count: Int): PathBuilder {
        this.limitValue = count
        return this
    }

    fun buildQuery(): Query {
        var query: Query = FirebaseFirestore.getInstance().collection(collectionPath)
        for (i in filters.indices) {
            val field = filters[i].first
            val value = filters[i].second
            query = query.whereEqualTo(field, value)
        }
        limitValue?.let { query = query.limit(it.toLong()) }
        return query
    }

    suspend fun <T : Any> buildItems(clazz: Class<T>): List<T> {
        val query = buildQuery()
        val documents = query.get().await().documents
        return documents.mapNotNull { it.toObject(clazz) }
    }

    suspend fun getCount(): Long {
        val query = buildQuery()
        val documents = query.get().await().documents
        return documents.size.toLong()
    }

    suspend fun setDocument(data: Any, documentId: String? = null) {
        val collection = FirebaseFirestore.getInstance().collection(collectionPath)
        if (documentId == null) collection.add(data).await()
        else collection.document(documentId).set(data).await()
    }
}

