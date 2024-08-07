package com.tpov.shoppinglist.utils

import android.content.Intent
import android.util.Log
import com.tpov.common.data.model.local.QuestionEntity

object ShareHelper {
    fun shareShopList(nameQuiz: String, id: List<QuestionEntity>, nameAnswerQuestion: Boolean): Intent {
        var intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plane"
        intent.apply {
            putExtra(Intent.EXTRA_TEXT, makeShareText(nameQuiz, id, nameAnswerQuestion))
        }
        return intent
    }

    private fun makeShareText(nameQuiz: String, id: List<QuestionEntity>, nameAnswerQuestion: Boolean): String {
        val sBuilder = StringBuilder()
        sBuilder.append("<<$nameQuiz>>")
        sBuilder.append("\n")
        var i = 0
        var itemInfo: String?
        var typeQuestion: String

        id.forEach {
            itemInfo = if (nameAnswerQuestion) {
                if (it.idQuiz > 10) "___"
                else it.answerQuestion.toString()
            } else " "

            typeQuestion = if (it.hardQuestion) "* "
            else " "

            Log.d("ShareHalper", "it.answerQuestion ${it.answerQuestion}")
            Log.d("ShareHalper", "it.answerQuestion.toString() $itemInfo")

            if (it.nameQuestion == nameQuiz) {
                sBuilder.append("${++i} - ${it.nameQuestion}$typeQuestion (${itemInfo})")
                sBuilder.append("\n")
            }
        }
        return sBuilder.toString()
    }
}