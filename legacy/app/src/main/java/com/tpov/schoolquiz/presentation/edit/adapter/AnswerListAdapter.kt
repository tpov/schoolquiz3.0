package com.tpov.schoolquiz.presentation.edit.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.tpov.common.presentation.utils.LanguageUtils
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.presentation.edit.model.TranslateAnswer

class AnswerListAdapter(
    private val onAnswerOptionsChanged: (updatedTranslateAnswer: TranslateAnswer) -> Unit
) : RecyclerView.Adapter<AnswerListAdapter.AnswerViewHolder>() {

    private var items: List<TranslateAnswer> = emptyList()

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnswerViewHolder {
        Log.d("awdawd", "onCreateViewHolder AnswerListAdapter")
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_create_quiz_answer, parent, false)
        return AnswerViewHolder(view, onAnswerOptionsChanged)
    }

    override fun onBindViewHolder(holder: AnswerViewHolder, position: Int) {
        Log.d("awdawd", "onBindViewHolder AnswerListAdapter position: $position")
        val item = items[position]
        Log.d("awdawd", "bind answer: ${item.listAnswer}, language: ${item.language}")
        holder.bind(item)
    }

    fun submitList(list: List<TranslateAnswer>?) {
        Log.d("awdawd", "submitList AnswerListAdapter called with size: ${list?.size}")
        list?.forEachIndexed { index, item ->
            Log.d("awdawd", "submitList AnswerListAdapter item $index: answers=${item.listAnswer}, language=${item.language}")
        }

        // Всегда создаем новый список и обновляем
        items = list?.toList() ?: emptyList()
        notifyDataSetChanged()
    }

    class AnswerViewHolder(itemView: View, private val onAnswerOptionsChanged: (updatedTranslateAnswer: TranslateAnswer) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val tvAnswerLanguage: TextView = itemView.findViewById(R.id.tv_answer_language)
        private val edtAnswerOption1: EditText = itemView.findViewById(R.id.edt_answer_option_1)
        private val edtAnswerOption2: EditText = itemView.findViewById(R.id.edt_answer_option_2)
        private val edtAnswerOption3: EditText = itemView.findViewById(R.id.edt_answer_option_3)
        private val edtAnswerOption4: EditText = itemView.findViewById(R.id.edt_answer_option_4)

        private val answerEditTexts = listOf(edtAnswerOption1, edtAnswerOption2, edtAnswerOption3, edtAnswerOption4)
        private val textWatchers = mutableListOf<android.text.TextWatcher>()
        private var currentLanguage: LanguageUtils = LanguageUtils.ENGLISH

        init {
            Log.d("awdawd", "AnswerViewHolder init")
            answerEditTexts.forEachIndexed { index, editText ->
                val textWatcher = editText.doAfterTextChanged { editable ->
                    Log.d("awdawd", "answer $index changed to: ${editable.toString()}")
                    val language = currentLanguage

                    val visibleTexts = mutableListOf<String>()
                    answerEditTexts.forEach { editText ->
                        if (editText.isVisible) {
                            visibleTexts.add(editText.text.toString())
                        }
                    }

                    val updatedTranslateAnswer = TranslateAnswer(visibleTexts, language)
                    onAnswerOptionsChanged.invoke(updatedTranslateAnswer)
                }
                textWatchers.add(textWatcher)
            }
        }

        fun bind(translateAnswer: TranslateAnswer) {
            Log.d("awdawd", "bind answer: ${translateAnswer.listAnswer}, language: ${translateAnswer.language}")
            currentLanguage = translateAnswer.language
            tvAnswerLanguage.text = translateAnswer.language.fullName

            removeListeners()

            answerEditTexts.forEachIndexed { index, editText ->
                if (index < translateAnswer.listAnswer.size) {
                    val text = translateAnswer.listAnswer[index]
                    Log.d("awdawd", "setting answer $index to: $text")
                    if (editText.text.toString() != text) {
                        editText.setText(text)
                    }
                    editText.visibility = View.VISIBLE
                    editText.isEnabled = true

                    if (index == 0) {
                        editText.setTextColor(itemView.context.getColor(android.R.color.holo_green_dark))
                    } else {
                        editText.setTextColor(itemView.context.getColor(android.R.color.white))
                    }
                } else {
                    editText.visibility = View.GONE
                    editText.text.clear()
                    editText.isEnabled = false
                }
            }
            addListeners()
        }

        private fun removeListeners() {
            answerEditTexts.forEachIndexed { index, editText ->
                if (index < textWatchers.size) {
                    editText.removeTextChangedListener(textWatchers[index])
                }
            }
        }

        private fun addListeners() {
            answerEditTexts.forEachIndexed { index, editText ->
                if (index < textWatchers.size) {
                    editText.addTextChangedListener(textWatchers[index])
                }
            }
        }
    }
}
