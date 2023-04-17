package com.tpov.schoolquiz.presentation.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.presentation.fragment.BaseFragment
import com.tpov.schoolquiz.presentation.network.profile.UsersFragment

class SettingsFragment : BaseFragment() {

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

        val backgroundAdapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.languages,
            android.R.layout.simple_spinner_item
        )
        backgroundAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        backgroundSpinner.adapter = backgroundAdapter
        backgroundSpinner.setSelection(backgroundAdapter.getPosition(appSettings.background))

        val musicAdapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.languages,
            android.R.layout.simple_spinner_item
        )
        musicAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        musicSpinner.adapter = musicAdapter
        musicSpinner.setSelection(musicAdapter.getPosition(appSettings.music))

        val nickColorAdapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.nick_colors,
            android.R.layout.simple_spinner_item
        )
        nickColorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        nickColorSpinner.adapter = nickColorAdapter
        nickColorSpinner.setSelection(nickColorAdapter.getPosition(appSettings.nickColor))

        val languagesAdapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.languages,
            android.R.layout.simple_spinner_item
        )
        languagesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languagesSpinner.adapter = languagesAdapter
        languagesSpinner.setSelection(languagesAdapter.getPosition(appSettings.languages))

        backgroundSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                appSettings.background = backgroundAdapter.getItem(position).toString()
            }
        }

        musicSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                appSettings.music = musicAdapter.getItem(position).toString()
            }
        }

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

        languagesSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                appSettings.languages = languagesAdapter.getItem(position).toString()
            }
        }

        return view
    }

    companion object {

        @JvmStatic
        fun newInstance() = SettingsFragment()
    }

}
