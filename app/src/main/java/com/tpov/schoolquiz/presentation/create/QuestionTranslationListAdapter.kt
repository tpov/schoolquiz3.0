package com.tpov.schoolquiz.presentation.create

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.presentation.create.model.TranslateQuestion

class QuestionTranslationListAdapter(
    private val onQuestionTextChanged: (updatedTranslateQuestion: TranslateQuestion) -> Unit
) : ListAdapter<TranslateQuestion, QuestionTranslationListAdapter.TranslationViewHolder>(
    TranslationDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TranslationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_create_quiz__translate_question, parent, false) // Assuming a layout for question translations
        return TranslationViewHolder(view, onQuestionTextChanged)
    }

    override fun onBindViewHolder(holder: TranslationViewHolder, position: Int) {
        val translateQuestion = getItem(position)
        holder.bind(translateQuestion)
    }

    class TranslationViewHolder(itemView: View, private val onQuestionTextChanged: (updatedTranslateQuestion: TranslateQuestion) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val tvLanguage: TextView = itemView.findViewById(R.id.sp_language_question) // Assuming ID for language TextView
        private val edtQuestionText: EditText = itemView.findViewById(R.id.tv_question_text) // Assuming ID for question text EditText

        private var currentLanguage: String = "" // Keep track of the currently bound language

        init {
            edtQuestionText.doAfterTextChanged { editable ->
                // Get the language from the bound item
                val language = currentLanguage
                val updatedQuestion = TranslateQuestion(language = language, question = editable.toString())
                onQuestionTextChanged.invoke(updatedQuestion)
            }
        }

        fun bind(translateQuestion: TranslateQuestion) {
            currentLanguage = translateQuestion.language // Store the language
            tvLanguage.text = translateQuestion.language
            if (edtQuestionText.text.toString() != translateQuestion.question) {
                edtQuestionText.setText(translateQuestion.question)
            }
        }
    }

    private class TranslationDiffCallback : DiffUtil.ItemCallback<TranslateQuestion>() {
        override fun areItemsTheSame(oldItem: TranslateQuestion, newItem: TranslateQuestion): Boolean {
            return oldItem.language == newItem.language // Assuming language is the unique identifier
        }

        override fun areContentsTheSame(oldItem: TranslateQuestion, newItem: TranslateQuestion): Boolean {
            return oldItem == newItem
        }
    }
}
