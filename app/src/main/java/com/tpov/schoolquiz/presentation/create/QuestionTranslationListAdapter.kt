package com.tpov.schoolquiz.presentation.create

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
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
            .inflate(R.layout.item_create_quiz__translate_question, parent, false)
        return TranslationViewHolder(view, onQuestionTextChanged)
    }

    override fun onBindViewHolder(holder: TranslationViewHolder, position: Int) {
        val translateQuestion = getItem(position)
        holder.bind(translateQuestion)
    }

    class TranslationViewHolder(itemView: View, private val onQuestionTextChanged: (updatedTranslateQuestion: TranslateQuestion) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val spLanguage: Spinner = itemView.findViewById(R.id.sp_language_question)
        private val edtQuestionText: EditText = itemView.findViewById(R.id.tv_question_text)

        private var currentLanguage: String = ""

        init {
            edtQuestionText.doAfterTextChanged { editable ->
                val language = currentLanguage
                val updatedQuestion = TranslateQuestion(language = language, question = editable.toString())
                onQuestionTextChanged.invoke(updatedQuestion)
            }
        }

        fun bind(translateQuestion: TranslateQuestion) {
            currentLanguage = translateQuestion.language
            
            // Создаем адаптер для Spinner с одним элементом - текущим языком
            val adapter = ArrayAdapter(
                itemView.context,
                android.R.layout.simple_spinner_item,
                listOf(translateQuestion.language)
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            spLanguage.adapter = adapter
            
            if (edtQuestionText.text.toString() != translateQuestion.question) {
                edtQuestionText.setText(translateQuestion.question)
            }
        }
    }

    private class TranslationDiffCallback : DiffUtil.ItemCallback<TranslateQuestion>() {
        override fun areItemsTheSame(oldItem: TranslateQuestion, newItem: TranslateQuestion): Boolean {
            return oldItem.language == newItem.language
        }

        override fun areContentsTheSame(oldItem: TranslateQuestion, newItem: TranslateQuestion): Boolean {
            return oldItem == newItem
        }
    }
}
