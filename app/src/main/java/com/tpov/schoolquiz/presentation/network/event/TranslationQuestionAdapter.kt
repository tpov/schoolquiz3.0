package com.tpov.schoolquiz.presentation.network.event

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.RecyclerView
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.QuestionEntity

class TranslationQuestionAdapter(val questions: MutableList<QuestionEntity>, private val languages: List<String>) :
    RecyclerView.Adapter<TranslationQuestionAdapter.QuestionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_translated_question, parent, false)
        return QuestionViewHolder(view)
    }

    override fun onBindViewHolder(holder: QuestionViewHolder, position: Int) {
        holder.bind(questions[position])
    }

    override fun getItemCount(): Int = questions.size

    fun addNewQuestion() {
        questions.add(QuestionEntity(null, 0, "", false, false, 0, "", 0))
        notifyItemInserted(questions.size - 1)
    }

    fun getUpdatedQuestions(): List<QuestionEntity> = questions

    inner class QuestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameQuestionEditText: EditText = itemView.findViewById(R.id.nameQuestionEditText)
        private val languageSpinner: Spinner = itemView.findViewById(R.id.languageSpinner)
        private val lvlTranslateTextView: AppCompatTextView = itemView.findViewById(R.id.lvlTranslateTextView)

        fun bind(question: QuestionEntity) {
            nameQuestionEditText.setText(question.nameQuestion)
            lvlTranslateTextView.text = question.lvlTranslate.toString()

            val languageAdapter = ArrayAdapter(itemView.context, android.R.layout.simple_spinner_item, languages)
            languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            languageSpinner.adapter = languageAdapter

            if (question.language.isNotEmpty()) {
                val languagePosition = languages.indexOf(question.language)
                if (languagePosition != -1) {
                    languageSpinner.setSelection(languagePosition)
                }
            }

            languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    questions[adapterPosition].language = languages[position]
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // Do nothing
                }
            }

            nameQuestionEditText.addTextChangedListener {
                questions[adapterPosition].nameQuestion = it.toString()
            }

            lvlTranslateTextView.addTextChangedListener {
                questions[adapterPosition].lvlTranslate = it.toString().toIntOrNull() ?: 0
            }
        }
    }
}