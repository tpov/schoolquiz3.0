package com.tpov.network.presentation

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.tpov.network.R
import com.tpov.network.databinding.FragmentLoginBinding
import kotlinx.coroutines.InternalCoroutinesApi

class AutorisationFragment: Fragment(){
    val startDelay = 0
    val stepDelay = 75
    val duration = 400L
    var sumDelay = startDelay

    private val binding get() = _binding!!
    private var _binding: FragmentLoginBinding? = null
    companion object {
        fun newInstance() = AutorisationFragment()
    }

    private lateinit var viewModel: AutorisationViewModel
    private fun setupTextWatchers() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                // Do nothing
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                // Do nothing
            }

            override fun afterTextChanged(s: Editable) {
                updateButtonsState()
            }
        }

        binding.username.addTextChangedListener(textWatcher)
        binding.password.addTextChangedListener(textWatcher)
        binding.loginName.addTextChangedListener(textWatcher)
        binding.nickname.addTextChangedListener(textWatcher)
    }

    private fun updateButtonsState() = with(binding) {

    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ) = FragmentLoginBinding.inflate(inflater, container, false).root


    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    private fun getDelay(): Long {
        val delay = sumDelay + stepDelay
        sumDelay = delay
        return delay.toLong()
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        binding.modeRadioGroup.setOnCheckedChangeListener { _, checkedId ->

            when (checkedId) {
                R.id.login_mode -> {
                    sumDelay = startDelay

                }

                R.id.registration_mode -> {
                    sumDelay = startDelay

                }
            }
        }
    }

    private fun updateButtonTextColor(button: Button, active: Boolean) {
        val textColor = if (active) {
            resources.getColor(R.color.green, null)
        } else {
            resources.getColor(R.color.white, null)
        }
        button.setTextColor(textColor)
    }


    @OptIn(InternalCoroutinesApi::class)
    override fun onAttach(context: Context) {
        super.onAttach(context)
    }

}