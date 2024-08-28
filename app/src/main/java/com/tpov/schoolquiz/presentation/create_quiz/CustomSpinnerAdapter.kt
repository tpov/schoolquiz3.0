package com.tpov.schoolquiz.presentation.create_quiz

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.schoolquiz.R

class CustomSpinnerAdapter(
    context: Context,
    private val questions: List<QuestionEntity>
) : ArrayAdapter<QuestionEntity>(context, R.layout.simple_spinner_item, questions) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val textView = view.findViewById<TextView>(android.R.id.text1)

        // Настройка текста
        val question = questions[position]
        textView.text = "${question.numQuestion} - ${question.nameQuestion}"

        // Изменение цвета текста
        if (question.hardQuestion) {
            textView.setTextColor(Color.RED)
        } else {
            textView.setTextColor(Color.BLACK)
        }

        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent)
        val textView = view.findViewById<TextView>(android.R.id.text1)

        // Настройка текста
        val question = questions[position]
        textView.text = "${question.numQuestion} - ${question.nameQuestion}"

        // Изменение цвета текста
        if (question.hardQuestion) {
            textView.setTextColor(Color.RED)
        } else {
            textView.setTextColor(Color.BLACK)
        }

        return view
    }
}
