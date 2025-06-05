package com.tpov.schoolquiz.presentation.create

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.presentation.create.model.TranslateAnswer

class AnswerListAdapter(
    private val onAnswerOptionsChanged: (updatedTranslateAnswer: TranslateAnswer) -> Unit
) : ListAdapter<TranslateAnswer, AnswerListAdapter.AnswerViewHolder>(
    AnswerDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnswerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_create_quiz_answer, parent, false)
        return AnswerViewHolder(view, onAnswerOptionsChanged)
    }

    override fun onBindViewHolder(holder: AnswerViewHolder, position: Int) {
        val translateAnswer = getItem(position)
        holder.bind(translateAnswer)
    }

    class AnswerViewHolder(itemView: View, private val onAnswerOptionsChanged: (updatedTranslateAnswer: TranslateAnswer) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val tvAnswerLanguage: TextView = itemView.findViewById(R.id.tv_answer_language)
        private val edtAnswerOption1: EditText = itemView.findViewById(R.id.edt_answer_option_1)
        private val edtAnswerOption2: EditText = itemView.findViewById(R.id.edt_answer_option_2)
        private val edtAnswerOption3: EditText = itemView.findViewById(R.id.edt_answer_option_3)
        private val edtAnswerOption4: EditText = itemView.findViewById(R.id.edt_answer_option_4)

        private val answerEditTexts = listOf(edtAnswerOption1, edtAnswerOption2, edtAnswerOption3, edtAnswerOption4)

        // Keep track of TextWatchers to avoid adding duplicates
        private val textWatchers = mutableListOf<android.text.TextWatcher>()

        private var currentLanguage: String = "" // Keep track of the currently bound language

        init {
            // Add TextWatchers to EditTexts
            answerEditTexts.forEachIndexed { index, editText ->
                val textWatcher = editText.doAfterTextChanged { editable ->
                    val language = currentLanguage

                    // Collect all visible fields, including empty ones
                    val visibleTexts = mutableListOf<String>()
                    answerEditTexts.forEach { editText ->
                        if (editText.isVisible) {
                            visibleTexts.add(editText.text.toString())
                        }
                    }

                    // Create the updated TranslateAnswer object with visible texts
                    val updatedTranslateAnswer = TranslateAnswer(visibleTexts, language)

                    // Call the callback function with the updated object
                    onAnswerOptionsChanged.invoke(updatedTranslateAnswer)
                }
                // Store textWatcher for potential removal/re-adding
                textWatchers.add(textWatcher)
            }
        }

        fun bind(translateAnswer: TranslateAnswer) {
            currentLanguage = translateAnswer.language // Store the language
            tvAnswerLanguage.text = translateAnswer.language

            // Remove existing listeners before binding new data
            removeListeners()

            // Bind text and manage visibility/enablement of EditTexts
            answerEditTexts.forEachIndexed { index, editText ->
                if (index < translateAnswer.listAnswer.size) {
                    val text = translateAnswer.listAnswer[index]
                    // Only update text if it's different to avoid unnecessary triggering of listeners
                    if (editText.text.toString() != text) {
                        editText.setText(text)
                    }
                    editText.visibility = View.VISIBLE
                    editText.isEnabled = true

                    // Set green text color for the first EditText
                    if (index == 0) {
                        editText.setTextColor(itemView.context.getColor(android.R.color.holo_green_dark))
                    } else {
                        editText.setTextColor(itemView.context.getColor(android.R.color.white))
                    }

                } else {
                    // Hide or clear EditText if there's no corresponding answer option
                    editText.visibility = View.GONE
                    editText.text.clear() // Clear text when hiding
                    editText.isEnabled = false
                }
            }
            // Add listeners back after binding new data
            addListeners()
        }

        // Helper function to remove TextWatchers
        private fun removeListeners() {
            answerEditTexts.forEachIndexed { index, editText ->
                if (index < textWatchers.size) {
                    editText.removeTextChangedListener(textWatchers[index])
                }
            }
        }

        // Helper function to add TextWatchers
        private fun addListeners() {
            answerEditTexts.forEachIndexed { index, editText ->
                if (index < textWatchers.size) {
                    editText.addTextChangedListener(textWatchers[index])
                }
            }
        }
    }

    private class AnswerDiffCallback : DiffUtil.ItemCallback<TranslateAnswer>() {
        override fun areItemsTheSame(oldItem: TranslateAnswer, newItem: TranslateAnswer): Boolean {
            // Assuming language is a unique identifier for TranslateAnswer items
            return oldItem.language == newItem.language
        }

        override fun areContentsTheSame(oldItem: TranslateAnswer, newItem: TranslateAnswer): Boolean {
            // Check if the contents of the items are the same
            return oldItem == newItem
        }
    }
}
