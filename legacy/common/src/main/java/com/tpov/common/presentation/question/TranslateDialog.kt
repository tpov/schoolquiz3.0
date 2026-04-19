package com.tpov.common.presentation.question

import android.R
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.tpov.common.SPLIT_BETWEEN_LANGUAGES
import com.tpov.common.SPLIT_BETWEEN_LVL_TRANSLATE_AND_LANG
import com.tpov.common.databinding.DialogTranslateBinding
import com.tpov.common.domain.usecase.SettingConfigObject.settingsConfig
import com.tpov.common.presentation.utils.LanguageUtils
import com.tpov.common.presentation.utils.LanguageUtils.Companion.toLanguageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import java.util.Locale

class TranslateDialog : DialogFragment() {

    private var _binding: DialogTranslateBinding? = null
    private val binding get() = _binding!!

    @OptIn(InternalCoroutinesApi::class)
    private val viewModel: QuestionViewModel by activityViewModels()

    private var newLanguageCode = LanguageUtils.ENGLISH
    private var onTranslationComplete: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogTranslateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeState()
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun setupUI() {
        Log.d("wadasdaw", "setupUI")
        var firstQuestion = false
        viewLifecycleOwner.lifecycleScope.launch {
            Log.d("wadasdaw", "lifecycleScope")
            viewModel.quiz.collect { quiz ->
                    Log.d("wadasdaw", "collect")
                    quiz?.languages?.let { languagesString ->
                        Log.d("wadasdaw", "languagesString: $languagesString")


                        val languages = languagesString
                            .split(SPLIT_BETWEEN_LANGUAGES)
                            .map { it.split(SPLIT_BETWEEN_LVL_TRANSLATE_AND_LANG)[0] }

                        initSpinner(languages, quiz.nameItem, languages[0])
                        initButtons(quiz.nameItem, languages[0])
                    }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            Log.d("wadasdaw", "lifecycleScope: $lifecycleScope")
            viewModel.questionList.collect { questionList ->
                Log.d("wadasdaw", "questionList firstQuestion: $firstQuestion")
                if (firstQuestion) {
                    questionList?.forEach { question ->
                        Log.d("wadasdaw", "question: $question")
                        val findQuestionByThisNewLanguage =
                            viewModel.pathStructure?.let {
                                viewModel.questionUseCase.getQuestionByPath(it).find {
                                    it.language.code ==
                                            newLanguageCode.code
                                            && it.numQuestion == question.numQuestion
                                            && it.hardQuestion == question.hardQuestion
                                }
                            }
                        if (findQuestionByThisNewLanguage == null) {
                            viewModel.translateANDAddQuestion(question, newLanguageCode)
                        }
                    }
                    viewModel.getQuestionList(listOf( newLanguageCode))
                    dismiss()
                } else firstQuestion = true
            }
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun initButtons(nameItem: String, mainLangQuiz: String) {
        val selectLang = binding.spLanguages.selectedItem.toString()
        val translateLang = settingsConfig.languages.firstOrNull()?.toString()?.toLanguageUtils() ?: Locale.getDefault().language.toLanguageUtils()
        binding.bLoad.text = selectLang
        binding.bTranslate.text = translateLang.fullName
        binding.bTranslate.setOnClickListener {
            newLanguageCode = translateLang
            viewModel.getQuestionList(listOf( mainLangQuiz.toLanguageUtils()))
        }

        binding.bCancel.setOnClickListener {
            dismiss()
        }

        binding.bLoad.setOnClickListener {
            newLanguageCode = binding.bLoad.text.toString().toLanguageUtils()
            viewModel.getQuestionList(listOf( newLanguageCode))
        }
    }

    private fun initSpinner(languages: List<String>, nameItem: String, mainLanguageQuiz: String) {
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.simple_spinner_item,
            languages
        ).apply {
            setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        }
        binding.spLanguages.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                initButtons(nameItem, mainLanguageQuiz)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.spLanguages.adapter = adapter
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.translateState.collect { state ->
                when (state) {
                    is TranslateState.Initial -> {
                    }
                    is TranslateState.Loading -> {
                    }
                    is TranslateState.Success -> {
                        onTranslationComplete?.invoke(state.translatedText)
                    }
                    is TranslateState.Error -> {
                        Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
