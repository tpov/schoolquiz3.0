package com.tpov.schoolquiz.presentation.network.profile

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModelProvider
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.presentation.MainApp
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory
import com.tpov.schoolquiz.presentation.fragment.BaseFragment
import com.tpov.schoolquiz.presentation.network.event.log
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Inject

class ProfileFragment : BaseFragment() {

    companion object {
        fun newInstance() = ProfileFragment()
    }

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @OptIn(InternalCoroutinesApi::class)
    private val component by lazy {
        (requireActivity().application as MainApp).component
    }

    @OptIn(InternalCoroutinesApi::class)
    private lateinit var viewModel: ProfileViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(InternalCoroutinesApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this, viewModelFactory)[ProfileViewModel::class.java]

        viewModel.addQuestion.observe(viewLifecycleOwner) {
            Toast.makeText(activity, "added question", Toast.LENGTH_SHORT).show()
        }
        viewModel.addInfoQuestion.observe(viewLifecycleOwner) {
            Toast.makeText(activity, "added question info", Toast.LENGTH_SHORT).show()
        }
        viewModel.addQuiz.observe(viewLifecycleOwner) {
            Toast.makeText(activity, "added quiz", Toast.LENGTH_SHORT).show()
        }

        val isExecuted = BooleanArray(8) // создаем массив флагов для каждого числа
        view.findViewById<ImageButton>(R.id.imb_download).setOnClickListener {
            viewModel.getQuizzFB()
            viewModel.getTranslate()
        }
        view.findViewById<ImageButton>(R.id.imb_delete).setOnClickListener {
            viewModel.getDeleteAllQuiz()
        }
        if (SharedPreferencesManager.canSyncProfile()) {
            val osb = viewModel.synth.observe(viewLifecycleOwner) { number ->
                log("fun viewModel.getSynth.observe: $number")
                when (number) {
                    1 -> {
                        if (!isExecuted[0]) { // проверяем, выполнялось ли число 0 ранее
                            viewModel.setProfile()
                            isExecuted[0] =
                                true // устанавливаем флаг в true, чтобы пометить число 0 как выполненное
                        }
                    }

                    2 -> {
                        if (!isExecuted[1]) {
                            viewModel.getProfile()
                            isExecuted[1] = true
                        }
                    }

                    3 -> {
                        if (!isExecuted[2]) {
                            viewModel.setProfile()
                            isExecuted[2] = true
                        }
                    }

                    4 -> {
                        if (!isExecuted[3]) {
                            viewModel.setQuizFB()
                            viewModel.getPlayersList()
                            isExecuted[3] = true
                        }
                    }

                    5 -> {
                        if (!isExecuted[4]) {
                            viewModel.setQuestionsFB()
                            isExecuted[4] = true
                        }
                    }

                    6 -> {
                        if (!isExecuted[5]) {
                            viewModel.setEventQuiz()
                            isExecuted[5] = true
                        }
                    }
                }
            }
            val referenceValue = Integer.toHexString(System.identityHashCode(osb))
            log("fun viewModel.getSynth referenceValue: $referenceValue")
        }

        viewModel.getTpovId()

    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onAttach(context: Context) {
        component.inject(this)
        super.onAttach(context)
    }
}
