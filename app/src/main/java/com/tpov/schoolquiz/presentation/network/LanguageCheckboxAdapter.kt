package com.tpov.schoolquiz.presentation.network

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.TextView
import com.tpov.schoolquiz.R

class LanguageCheckboxAdapter(
    private val context: Context,
    private val languages: List<String>
) : ArrayAdapter<String>(context, R.layout.item_language_checkbox, languages) {

    private val selectedLanguages = mutableSetOf<String>()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_language_checkbox, parent, false)

        val languageCheckbox = view.findViewById<CheckBox>(R.id.languageCheckbox)
        val languageNameTextView = view.findViewById<TextView>(R.id.languageNameTextView)

        val language = getItem(position)
        languageNameTextView.text = language

        languageCheckbox.isChecked = selectedLanguages.contains(language)
        languageCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedLanguages.add(language!!)
            } else {
                selectedLanguages.remove(language)
            }
            // Вы можете выполнить нужные действия при выборе/снятии выбора языка
            // Например, сохранить выбранные языки в базе данных или переменных
        }

        return view
    }
}
