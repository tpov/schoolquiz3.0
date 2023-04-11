package com.tpov.schoolquiz.presentation.network

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.presentation.MainApp
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory
import com.tpov.schoolquiz.presentation.fragment.BaseFragment
import com.tpov.schoolquiz.presentation.mainactivity.MainActivity
import com.tpov.schoolquiz.presentation.network.profile.ProfileFragment
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Inject

class AutorisationFragment : BaseFragment() {
    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var nameEditText: EditText
    private lateinit var dateEditText: EditText
    private lateinit var loginCity: EditText
    private lateinit var loginButton: Button
    private lateinit var registrationButton: Button
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var nickname: TextView
    private lateinit var loginMode: RadioButton
    private lateinit var modeRadioGroup: RadioGroup
    private lateinit var registrationMode: RadioButton

    companion object {
        fun newInstance() = AutorisationFragment()

    }

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @OptIn(InternalCoroutinesApi::class)
    private val component by lazy {
        (requireActivity().application as MainApp).component
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
        usernameEditText.addTextChangedListener(textWatcher)
        passwordEditText.addTextChangedListener(textWatcher)
        nameEditText.addTextChangedListener(textWatcher)
        nameEditText.addTextChangedListener(textWatcher)
        nickname.addTextChangedListener(textWatcher)
        dateEditText.addTextChangedListener(textWatcher)
    }

    private fun updateButtonsState() {
        val isLoginMode = loginMode.isChecked
        val isRegistrationMode = registrationMode.isChecked

        val loginValid = usernameEditText.text.isNotEmpty() && passwordEditText.text.isNotEmpty()
        val registrationValid =
            nickname.text.isNotEmpty() && nameEditText.text.isNotEmpty() && dateEditText.text.isNotEmpty() && usernameEditText.text.isNotEmpty() && passwordEditText.text.isNotEmpty()

        loginButton.isEnabled = isLoginMode && loginValid
        registrationButton.isEnabled = isRegistrationMode && registrationValid
        loginButton.isClickable = isLoginMode && loginValid
        registrationButton.isClickable = isRegistrationMode && registrationValid

        updateButtonTextColor(loginButton, isLoginMode && loginValid)
        updateButtonTextColor(registrationButton, isRegistrationMode && registrationValid)
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        log("fun onViewCreated()")
        viewModel = ViewModelProvider(this, viewModelFactory)[AutorisationViewModel::class.java]
        usernameEditText = view.findViewById(R.id.username)
        passwordEditText = view.findViewById(R.id.password)
        nameEditText = view.findViewById(R.id.loginName)
        dateEditText = view.findViewById(R.id.years)
        loginButton = view.findViewById(R.id.login)
        loadingProgressBar = view.findViewById(R.id.loading)
        registrationButton = view.findViewById(R.id.registration)
        loginCity = view.findViewById(R.id.city)
        nickname = view.findViewById(R.id.nickname)
        loginMode = view.findViewById(R.id.login_mode)
        registrationMode = view.findViewById(R.id.registration_mode)
        modeRadioGroup = view.findViewById(R.id.mode_radio_group)

        registrationButton.isEnabled = false
        registrationButton.isClickable = false
        loginButton.isEnabled = false
        loginButton.isClickable = false

        setupTextWatchers()
        loginMode.isChecked = true
        modeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.login_mode -> {
                    nameEditText.visibility = View.GONE
                    dateEditText.visibility = View.GONE
                    nickname.visibility = View.GONE
                    loginCity.visibility = View.GONE
                    registrationButton.visibility = View.GONE
                    loginButton.visibility = View.VISIBLE
                }

                R.id.registration_mode -> {
                    nameEditText.visibility = View.VISIBLE
                    dateEditText.visibility = View.VISIBLE
                    nickname.visibility = View.VISIBLE
                    loginCity.visibility = View.VISIBLE
                    registrationButton.visibility = View.VISIBLE
                    loginButton.visibility = View.GONE
                }
            }
        }

        loginButton.setOnClickListener {
            viewModel.loginAcc(
                usernameEditText.text.toString(), passwordEditText.text.toString(), requireContext()
            )
        }

        registrationButton.setOnClickListener {
            viewModel.createAcc(
                usernameEditText.text.toString(),
                passwordEditText.text.toString(),
                requireContext(),
                nameEditText.text.toString(),
                nickname.text.toString(),
                dateEditText.text.toString(),
                loginCity.text.toString(),
                ""
            )
        }

        var obs = false
        viewModel.someData.observe(viewLifecycleOwner) {
            log("onViewCreated someData.observe()")

            if (it > 0) {
                if (!obs) {
                    log("onViewCreated someData.observe() соблюдение условий для запуска ProfileFragment")
                    obs = true
                    val fragmentTransaction = fragmentManager?.beginTransaction()
                    fragmentTransaction?.remove(this)
                    fragmentTransaction?.replace(R.id.title_fragment, ProfileFragment.newInstance())
                    fragmentTransaction?.commit()
                }
            }
        }
    }

    private fun updateButtonTextColor(button: Button, active: Boolean) {
        val textColor = if (active) {
            resources.getColor(R.color.num_chack_norice_green, null)
        } else {
            resources.getColor(R.color.white, null)
        }
        button.setTextColor(textColor)
    }


    @OptIn(InternalCoroutinesApi::class)
    override fun onAttach(context: Context) {
        component.inject(this)
        super.onAttach(context)
    }

    @OptIn(InternalCoroutinesApi::class)
    fun log(msg: String) {
        Logcat.log(msg, "AutorisationFragment", Logcat.LOG_FRAGMENT)
    }
}