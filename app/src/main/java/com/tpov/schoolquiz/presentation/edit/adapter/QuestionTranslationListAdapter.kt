package com.tpov.schoolquiz.presentation.edit.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.tpov.common.presentation.utils.LanguageUtils
import com.tpov.common.presentation.utils.LanguageUtils.Companion.toLanguageUtils
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.presentation.custom.CustomSpinner
import com.tpov.schoolquiz.presentation.edit.model.TranslateQuestion

class QuestionTranslationListAdapter(
    private val onQuestionTextChanged: (updatedTranslateQuestion: TranslateQuestion) -> Unit,
    private val onLanguageChanged: (oldLanguage: LanguageUtils, newLanguage: LanguageUtils) -> Unit
) : RecyclerView.Adapter<QuestionTranslationListAdapter.TranslationViewHolder>() {

    private val items = mutableListOf<TranslateQuestion>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TranslationViewHolder {
        Log.d("awdawd", "onCreateViewHolder")
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_create_quiz__translate_question, parent, false)
        return TranslationViewHolder(view, onQuestionTextChanged) { oldLang, newLang ->
            onLanguageChanged(oldLang, newLang)
        }
    }

    override fun onBindViewHolder(holder: TranslationViewHolder, position: Int) {
        Log.d("awdawd", "onBindViewHolder position: $position")
        val item = items[position]
        Log.d("awdawd", "bind question: ${item.question}, language: ${item.language}")
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<TranslateQuestion>?) {
        Log.d("awdawd", "submitList called with size: ${newItems?.size}")
        newItems?.forEachIndexed { index, item ->
            Log.d("awdawd", "submitList item $index: question=${item.question}, language=${item.language}")
        }

        items.clear()
        if (newItems != null) {
            items.addAll(newItems)
        }
        notifyDataSetChanged()
    }

    class TranslationViewHolder(
        itemView: View,
        private val onQuestionTextChanged: (updatedTranslateQuestion: TranslateQuestion) -> Unit,
        private val onLanguageSelected: (oldLanguage: LanguageUtils, newLanguage: LanguageUtils) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val spLanguage: CustomSpinner = itemView.findViewById(R.id.sp_language_question)
        private val edtQuestionText: EditText = itemView.findViewById(R.id.tv_question_text)

        private var currentLanguage: LanguageUtils = LanguageUtils.ENGLISH

        init {
            Log.d("awdawd", "TranslationViewHolder init")
            val languages = LanguageUtils.entries.map { it.fullName }
            spLanguage.setItems(languages)

            spLanguage.setOnItemSelectedListener { position ->
                val newLanguage = position.toLanguageUtils()
                if (newLanguage != currentLanguage) {
                    val oldLanguage = currentLanguage
                    Log.d("awdawd", "Language changed from $oldLanguage to $newLanguage")
                    onLanguageSelected(oldLanguage, newLanguage)
                    currentLanguage = newLanguage
                }
                true
            }

            edtQuestionText.doAfterTextChanged { text ->
                Log.d("awdawd", "text changed to: ${text.toString()}")
                val updatedQuestion = TranslateQuestion(
                    question = text.toString(),
                    language = currentLanguage,
                )
                onQuestionTextChanged(updatedQuestion)
            }
        }

        fun bind(translateQuestion: TranslateQuestion) {
            Log.d("awdawd", "bind question: ${translateQuestion.question}, language: ${translateQuestion.language}")
            currentLanguage = translateQuestion.language

            val languages = LanguageUtils.entries.map { it.fullName }
            spLanguage.setItems(languages)
            val index = LanguageUtils.entries.indexOf(translateQuestion.language)
            if (index >= 0) {
                spLanguage.setSelection(index, false)
            }

            if (edtQuestionText.text.toString() != translateQuestion.question) {
                Log.d("awdawd", "setting text to: ${translateQuestion.question}")
                edtQuestionText.setText(translateQuestion.question)
            }
        }
    }
}
