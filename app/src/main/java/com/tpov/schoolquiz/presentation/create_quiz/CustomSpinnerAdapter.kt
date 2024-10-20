package com.tpov.schoolquiz.presentation.create_quiz

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.presentation.model.QuestionShortEntity

class CustomSpinnerAdapter(
    context: Context,
    private val questions: ArrayList<QuestionShortEntity>
) : ArrayAdapter<QuestionShortEntity>(context, R.layout.simple_spinner_item, questions) {
init {

    questions.add(QuestionShortEntity(questions.size, 0, "Создать новый квест", false))
}
    @SuppressLint("SetTextI18n")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val textView = view.findViewById<TextView>(android.R.id.text1)

        Log.d("segfdf", "adapter questions: $questions")
        val question = questions[position]
        textView.text = "${question.numQuestion}. ${question.nameQuestion.take(10)}" +
                if (question.nameQuestion.length > 10) "..." else ""

        if (position == questions.size - 1) {
            textView.setTextColor(Color.BLUE)
        } else if (question.hardQuestion) {
            textView.setTextColor(Color.RED)
        } else {
            textView.setTextColor(Color.GREEN)
        }

        return view
    }

    @SuppressLint("SetTextI18n")
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent)
        val textView = view.findViewById<TextView>(android.R.id.text1)

        val question = questions[position]
        textView.text = "${if (position == questions.size - 1) "+" else question.numQuestion}. ${question.nameQuestion.take(10)}" +
                if (question.nameQuestion.length > 10) "..." else ""

        if (position == questions.size - 1) {
            textView.setTextColor(Color.BLUE)
        } else if (question.hardQuestion) {
            textView.setTextColor(Color.RED)
        } else {
            textView.setTextColor(Color.BLACK)
        }

        return view
    }
}
