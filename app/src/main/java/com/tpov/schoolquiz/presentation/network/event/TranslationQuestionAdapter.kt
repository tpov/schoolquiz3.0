package com.tpov.schoolquiz.presentation.network.event

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tpov.schoolquiz.databinding.ItemTranslatedQuestionBinding

class TranslationQuestionAdapter(
    var translatedQuestions: List<TranslatedQuestion> = listOf()
) : RecyclerView.Adapter<TranslationQuestionAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemTranslatedQuestionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTranslatedQuestionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val translatedQuestion = translatedQuestions[position]
        holder.binding.tvQuestion.text = translatedQuestion.nameQuestion

        // TODO: Обновите этот код, чтобы отображать соответствующие переводы
        // val translationsText = translatedQuestion.translations.joinToString(separator = "\n") { translation ->
        //     "${translation.language}: ${translation.text}"
        // }
        // holder.binding.tvTranslations.text = translationsText
    }

    override fun getItemCount(): Int {
        return translatedQuestions.size
    }
}
