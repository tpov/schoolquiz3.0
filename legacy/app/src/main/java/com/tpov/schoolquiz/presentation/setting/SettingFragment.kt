package com.tpov.schoolquiz.presentation.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.fragment.app.Fragment
import com.tpov.schoolquiz.R

class SettingsFragment : Fragment() {

    private lateinit var appSettings: AppSettings

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.setting_fragment, container, false)

        appSettings = AppSettings(requireContext())

        val backgroundSpinner: Spinner = view.findViewById(R.id.background_spinner)
        val musicSpinner: Spinner = view.findViewById(R.id.music_spinner)
        val nickColorSpinner: Spinner = view.findViewById(R.id.nick_color_spinner)
        val languagesSpinner: Spinner = view.findViewById(R.id.languages_spinner)

        val nickColorAdapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.nick_colors,
            android.R.layout.simple_spinner_item
        )
        nickColorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        nickColorSpinner.adapter = nickColorAdapter
        nickColorSpinner.setSelection(nickColorAdapter.getPosition(appSettings.nickColor))



        nickColorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                appSettings.nickColor = nickColorAdapter.getItem(position).toString()
            }
        }

        return view
    }

    companion object {

        @JvmStatic
        fun newInstance() = SettingsFragment()
    }

}
