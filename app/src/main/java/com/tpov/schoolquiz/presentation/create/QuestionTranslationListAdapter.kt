package com.tpov.schoolquiz.presentation.create

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tpov.common.SPLIT_BETWEEN_LANGUAGES
import com.tpov.common.presentation.utils.LanguageUtils.languagesFullNames
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
        private val spLanguage: CustomSpinner = itemView.findViewById(R.id.sp_language_question)
        private val edtQuestionText: EditText = itemView.findViewById(R.id.tv_question_text)

        private var currentLanguage: List<String> = listOf()

        init {
            val languages = languagesFullNames.toList()
            spLanguage.setItems(languages)
            edtQuestionText.doAfterTextChanged { text ->
                val updatedQuestion = TranslateQuestion(
                    language = currentLanguage.joinToString(SPLIT_BETWEEN_LANGUAGES),
                    question = text.toString()
                )
                onQuestionTextChanged(updatedQuestion)
            }
        }

        fun bind(translateQuestion: TranslateQuestion) {
            currentLanguage = translateQuestion.language.split(SPLIT_BETWEEN_LANGUAGES)

            spLanguage.setItems(currentLanguage)
            val index = currentLanguage.indexOf(translateQuestion.language)
            if (index >= 0) {
                spLanguage.setSelection(index)
            }

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
