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
import com.tpov.schoolquiz.presentation.custom.LanguageUtils
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getTpovId
import java.lang.String
import kotlin.Int
import kotlin.Long
import kotlin.toString

class TranslationQuestionAdapter(val questions: MutableList<QuestionEntity>) :
    RecyclerView.Adapter<TranslationQuestionAdapter.QuestionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_translated_question, parent, false)
        return QuestionViewHolder(view)
    }

    override fun onBindViewHolder(holder: QuestionViewHolder, position: Int) {
        holder.bind(questions[position])
    }

    override fun getItemCount(): Int = questions.size

    fun addNewQuestion() {
        questions.add(QuestionEntity(null, 0, "", false, false, 0, "", 0, getTpovId().toString()))
        notifyItemInserted(questions.size - 1)
    }

    fun getUpdatedQuestions(): List<QuestionEntity> = questions

    inner class QuestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameQuestionEditText: EditText =
            itemView.findViewById(R.id.nameQuestionEditText)
        private val languageSpinner: Spinner = itemView.findViewById(R.id.languageSpinner)
        private val ratingTranslate: Spinner = itemView.findViewById(R.id.sp_rating)
        private val lvlTranslateTextView: AppCompatTextView =
            itemView.findViewById(R.id.lvlTranslateTextView)

        fun bind(question: QuestionEntity) {
            nameQuestionEditText.setText(question.nameQuestion)
            lvlTranslateTextView.text = question.lvlTranslate.toString()

            val languageAdapter = ArrayAdapter(
                itemView.context,
                android.R.layout.simple_spinner_item,
                LanguageUtils.languagesFullNames
            )
            languageSpinner.adapter = languageAdapter
            languageSpinner.setSelection(LanguageUtils.getPositionLang(question.language))

            languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    questions[adapterPosition].language = LanguageUtils.getLanguageShortCode(
                        languageAdapter.getItem(position).toString()
                    )
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // Do nothing
                }
            }

            val ratingAdapter = ArrayAdapter(
                itemView.context,
                android.R.layout.simple_spinner_item,
                LanguageUtils.ratingNum
            )
            ratingTranslate.adapter = ratingAdapter
            ratingTranslate.setSelection(3)

            ratingTranslate.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val rating = ratingAdapter.getItem(position).toString()
                    val infoTranslater = questions[adapterPosition].infoTranslater

// Разделение значения infoTranslater на разделитель "|"
                    val parts = infoTranslater.split("\\|").toTypedArray()

// Обновление значения rating в infoTranslater
                    parts[parts.size - 1] = rating

// Объединение значений снова с помощью разделителя "|"
                    val updatedInfoTranslater = String.join("|", *parts)

// Установка обновленного значения infoTranslater в questions
                    questions[adapterPosition].infoTranslater = updatedInfoTranslater

                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // Do nothing
                }
            }

            nameQuestionEditText.addTextChangedListener {
                questions[adapterPosition].nameQuestion = it.toString()
                ratingTranslate.setSelection(0)
            }

            lvlTranslateTextView.addTextChangedListener {
                questions[adapterPosition].lvlTranslate = it.toString().toIntOrNull() ?: 0
            }
        }
    }
}