package com.tpov.common.presentation.utils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.TextView
import com.tpov.common.R
import com.tpov.common.presentation.model.LanguageEntity

class LanguageAdapter(private val languages: List<LanguageEntity>) : BaseAdapter() {

    override fun getCount(): Int {
        return languages.size
    }

    override fun getItem(position: Int): Any {
        return languages[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(parent?.context)
            .inflate(R.layout.item_language_checkbox, parent, false)

        val language = languages[position]
        val checkBox = view.findViewById<CheckBox>(R.id.checkBox)
        val textViewLanguage = view.findViewById<TextView>(R.id.textViewLanguage)

        checkBox.isChecked = language.isSelected
        textViewLanguage.text = language.name

        checkBox.setOnCheckedChangeListener { _, isChecked ->
            language.isSelected = isChecked
        }

        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
        return getView(position, convertView, parent)
    }
}
