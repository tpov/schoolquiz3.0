package com.tpov.schoolquiz.presentation.dialog

import android.R
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.schoolquiz.databinding.FragmentCreateQuizBinding
import com.tpov.schoolquiz.presentation.core.LanguageUtils
import com.tpov.schoolquiz.presentation.main.MainViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

class CreateQuestionFragment : Fragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var viewModel: MainViewModel

    private var idQuiz = -1

    private var _binding: FragmentCreateQuizBinding? = null
    private val binding get() = _binding!!

    private var questionsEntity: List<QuestionEntity>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            idQuiz = it.getInt(ARG_QUIZ)
            viewModel.getQuizByIdQuiz(idQuiz)
            viewModel.getQuestionByIdQuiz(idQuiz)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateQuizBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initView()
        initSetOnClickListeners()

    }

    private fun initSetOnClickListeners() {
        binding.bSave.setOnClickListener {
            // Логика для сохранения данных
            val newQuestion = QuestionEntity(
                id = questionEntity?.id, // Если редактирование, сохраняем ID
                numQuestion = questionEntity?.numQuestion
                    ?: 0, // Или получаем номер вопроса откуда-то
                nameQuestion = binding.tvQuestionText.text.toString(),
                pathPictureQuestion = questionEntity?.pathPictureQuestion,
                answer = determineCorrectAnswer(), // Ваша логика определения правильного ответа
                nameAnswers = "${binding.bAnswer1.text}|${binding.bAnswer2.text}", // Форматируем ответы
                hardQuestion = binding.checkBox2.isChecked,
                idQuiz = quizEntity!!.id!!, // Используем ID викторины
                language = questionEntity?.language ?: "default",
                lvlTranslate = questionEntity?.lvlTranslate ?: 0
            )

            // Сохранение или обновление вопроса в базе данных
            saveQuestion(newQuestion)

        }
        setCategorySpinnerListeners()
    }

    private fun initView() {
        if (idQuiz != -1) {
            lifecycleScope.launch { viewModel.quizData.collect { it.let { updateUiQuiz(it!!) } } }
            lifecycleScope.launch {
                viewModel.questionData.collect {
                    it.let {
                        questionsEntity = it!!
                        updateUiQuestion(1)
                    }
                }
            }
        }
    }

    private fun updateUiQuiz(quiz: QuizEntity) = with(binding) {
        tvQuizName.setText(quiz.nameQuiz)

        val imagePath = quiz.picture
        if (!imagePath.isNullOrEmpty()) {
            imvQuiz.setImageURI(Uri.parse(imagePath))
        }


    }

    private fun setSpinnersCategory(category: String, subCategory: String, subSubCategory: String) {
        lifecycleScope.launch {
            val structureData = viewModel.getStructureData()

            // Переменные для хранения выбранных индексов
            var selectedCategoryIndex = 0 // По умолчанию "Создать новую категорию"
            var selectedSubCategoryIndex = 0
            var selectedSubSubCategoryIndex = 0

            // Создаем списки для категорий, подкатегорий и под-субкатегорий
            val categories = mutableListOf("Создать новую категорию")
            val subcategories = mutableListOf("Создать новую подкатегорию")
            val subSubcategories = mutableListOf("Создать новую под-субкатегорию")

            // Заполняем категории и ищем нужную
            structureData?.event?.forEach { event ->
                event.category.forEachIndexed { catIndex, cat ->
                    categories.add(cat.nameQuiz)
                    if (cat.nameQuiz == category) {
                        selectedCategoryIndex =
                            catIndex + 1 // Смещение на +1 из-за добавления "Создать новую категорию"

                        // Заполняем субкатегории и ищем нужную
                        cat.subcategory.forEachIndexed { subCatIndex, subCat ->
                            subcategories.add(subCat.nameQuiz)
                            if (subCat.nameQuiz == subCategory) {
                                selectedSubCategoryIndex = subCatIndex + 1

                                // Заполняем под-субкатегории и ищем нужную
                                subCat.subSubcategory.forEachIndexed { subSubCatIndex, subSubCat ->
                                    subSubcategories.add(subSubCat.nameQuiz)
                                    if (subSubCat.nameQuiz == subSubCategory) {
                                        selectedSubSubCategoryIndex = subSubCatIndex + 1
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Устанавливаем адаптеры для спиннеров
            val categoryAdapter =
                ArrayAdapter(requireContext(), R.layout.simple_spinner_item, categories)
            categoryAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
            binding.spCategory.adapter = categoryAdapter

            val subCategoryAdapter =
                ArrayAdapter(requireContext(), R.layout.simple_spinner_item, subcategories)
            subCategoryAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
            binding.spSubCategory.adapter = subCategoryAdapter

            val subSubCategoryAdapter =
                ArrayAdapter(requireContext(), R.layout.simple_spinner_item, subSubcategories)
            subSubCategoryAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
            binding.spSubsubCategory.adapter = subSubCategoryAdapter

            // Устанавливаем выбранные элементы в спиннерах
            binding.spCategory.setSelection(selectedCategoryIndex)
            binding.spSubCategory.setSelection(selectedSubCategoryIndex)
            binding.spSubsubCategory.setSelection(selectedSubSubCategoryIndex)

        }
    }

    private fun setCategorySpinnerListeners() {
        binding.spCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selectedCategory = parent.getItemAtPosition(position) as String
                if (selectedCategory == "Создать новую категорию") {
                    createNewCategory()
                } else {
                    loadSubCategories(selectedCategory)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Слушатель для спиннера подкатегорий
        binding.spSubCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selectedSubCategory = parent.getItemAtPosition(position) as String
                if (selectedSubCategory == "Создать новую подкатегорию") {
                    createNewSubCategory()
                } else {
                    loadSubSubCategories(selectedSubCategory)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Слушатель для спиннера под-субкатегорий
        binding.spSubsubCategory.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val selectedSubSubCategory = parent.getItemAtPosition(position) as String
                    if (selectedSubSubCategory == "Создать новую под-субкатегорию") {
                        createNewSubSubCategory()
                    } else {
                        // Обработка выбора под-субкатегории
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
    }

    private fun loadSubCategories(category: String) {
        lifecycleScope.launch {
            val structureData = viewModel.getStructureData()

            val subcategories = mutableListOf("Создать новую подкатегорию")

            structureData?.event?.forEach { event ->
                event.category.firstOrNull { it.nameQuiz == category }?.subcategory?.forEach {
                    subcategories.add(it.nameQuiz)
                }
            }

            val subCategoryAdapter =
                ArrayAdapter(requireContext(), R.layout.simple_spinner_item, subcategories)
            subCategoryAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
            binding.spSubCategory.adapter = subCategoryAdapter
        }
    }

    private fun loadSubSubCategories(subCategory: String) {
        lifecycleScope.launch {
            val structureData = viewModel.getStructureData()

            val subSubcategories = mutableListOf("Создать новую под-субкатегорию")

            structureData?.event?.forEach { event ->
                event.category.forEach { category ->
                    category.subcategory.firstOrNull { it.nameQuiz == subCategory }?.subSubcategory?.forEach {
                        subSubcategories.add(it.nameQuiz)
                    }
                }
            }

            val subSubCategoryAdapter =
                ArrayAdapter(requireContext(), R.layout.simple_spinner_item, subSubcategories)
            subSubCategoryAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
            binding.spSubsubCategory.adapter = subSubCategoryAdapter
        }
    }

    private fun createNewCategory() {
        // Логика создания новой категории
    }

    private fun createNewSubCategory() {
        // Логика создания новой подкатегории
    }

    private fun createNewSubSubCategory() {
        // Логика создания новой под-субкатегории
    }

    private fun addQuestionToLayout(questionText: String? = null, language: String? = null) {
        val questionLayout = LayoutInflater.from(context).inflate(
            com.tpov.schoolquiz.R.layout.item_create_quiz_question,
            binding.llQuestions,
            false
        ) as LinearLayout

        val questionTextView: EditText =
            questionLayout.findViewById(com.tpov.schoolquiz.R.id.tv_question_text1)
        val languageSpinner: Spinner =
            questionLayout.findViewById(com.tpov.schoolquiz.R.id.sp_language_question1)

        questionTextView.setText(questionText ?: "")

        val languagesFullNames = LanguageUtils.languagesFullNames
        val languagesShortCodes = LanguageUtils.languagesShortCodes

        val languagesAdapter = ArrayAdapter(
            view?.context!!,
            R.layout.simple_spinner_item,
            languagesFullNames
        )
        languagesAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = languagesAdapter

        val selectedLanguageIndex = language?.let {
            languagesShortCodes.indexOf(it)
        } ?: -1

        if (selectedLanguageIndex != -1) {
            languageSpinner.setSelection(selectedLanguageIndex)
        }

        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                (view as? TextView)?.text = languagesShortCodes[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        questionTextView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrEmpty() && s.last() == ' ') {
                    val textBeforeSpace = s.substring(0, s.length - 1)
                    val detectedLanguage = determineLanguage(textBeforeSpace)
                    val detectedLanguageIndex = languagesShortCodes.indexOf(detectedLanguage)
                    if (detectedLanguageIndex != -1) {
                        languageSpinner.setSelection(detectedLanguageIndex)
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.llQuestions.addView(questionLayout)
    }

    private fun updateUiQuestion(numQuestion: Int) = with(binding) {
        val questionEntitiesLanguage = questionsEntity?.filter {
            it.numQuestion == numQuestion && it.hardQuestion == (numQuestion < 0)
        }

        val imagePath = questionEntitiesLanguage?.get(0)?.pathPictureQuestion
        if (!imagePath.isNullOrEmpty()) imvQuestion.setImageURI(Uri.parse(imagePath))
        else imvQuestion.setImageResource(com.tpov.schoolquiz.R.drawable.ic_upload)

        chbTypeQuestion.isChecked = numQuestion < 0

        questionEntitiesLanguage?.forEach { question ->
            addQuestionToLayout(question.nameQuestion, question.language)

            val answers = question.nameAnswers.split("|")
            val correctAnswerIndex = question.answer

            answers.forEachIndexed { index, answerText ->
                val isCorrect = index == correctAnswerIndex
                addAnswer(
                    language = question.language,
                    answerText = answerText,
                    isCorrect = isCorrect
                )
            }
        }
    }

    private fun addAnswer(language: String, answerText: String, isCorrect: Boolean = false) {
        val existingLayout = findLayoutByLanguage(language)

        if (existingLayout != null) {
            val leftAnswerEditText: EditText =
                existingLayout.findViewById(com.tpov.schoolquiz.R.id.b_answer1)
            val rightAnswerEditText: EditText =
                existingLayout.findViewById(com.tpov.schoolquiz.R.id.b_answer2)

            when {
                leftAnswerEditText.text.isEmpty() -> {
                    leftAnswerEditText.setText(answerText)
                    if (isCorrect) leftAnswerEditText.setBackgroundColor(Color.GREEN)
                }

                rightAnswerEditText.text.isEmpty() -> {
                    rightAnswerEditText.setText(answerText)
                    if (isCorrect) rightAnswerEditText.setBackgroundColor(Color.GREEN)
                }

                else -> {
                    val newAnswerEditText = EditText(context).apply {
                        layoutParams =
                            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        setText(answerText)
                        if (isCorrect) setBackgroundColor(Color.GREEN)
                        setTextColor(Color.WHITE)
                    }
                    existingLayout.addView(newAnswerEditText)
                }
            }
        } else {
            createNewAnswerLayout(language, answerText, "", isCorrect, false)
        }
    }

    private fun findLayoutByLanguage(language: String): LinearLayout? {
        for (i in 0 until binding.llAnswers.childCount) {
            val layout = binding.llAnswers.getChildAt(i) as LinearLayout
            val languageTextView: TextView =
                layout.findViewById(com.tpov.schoolquiz.R.id.tv_answer_language)
            if (languageTextView.text == LanguageUtils.getLanguageFullName(language)) {
                return layout
            }
        }
        return null
    }

    private fun createNewAnswerLayout(
        language: String,
        answerTextLeft: String?,
        answerTextRight: String?,
        isCorrectLeft: Boolean,
        isCorrectRight: Boolean
    ) {
        val newAnswerLayout = LayoutInflater.from(context).inflate(
            com.tpov.schoolquiz.R.layout.item_create_quiz_answer,
            binding.llAnswers,
            false
        ) as LinearLayout

        val answerLanguageTextView: TextView =
            newAnswerLayout.findViewById(com.tpov.schoolquiz.R.id.tv_answer_language)
        answerLanguageTextView.text = LanguageUtils.getLanguageFullName(language)

        val leftAnswerEditText: EditText =
            newAnswerLayout.findViewById(com.tpov.schoolquiz.R.id.b_answer1)
        leftAnswerEditText.setText(answerTextLeft ?: "")
        if (isCorrectLeft) {
            leftAnswerEditText.setBackgroundColor(Color.GREEN)
        }

        val rightAnswerEditText: EditText =
            newAnswerLayout.findViewById(com.tpov.schoolquiz.R.id.b_answer2)
        rightAnswerEditText.setText(answerTextRight ?: "")
        if (isCorrectRight) {
            rightAnswerEditText.setBackgroundColor(Color.GREEN)
        }

        binding.llAnswers.addView(newAnswerLayout)
    }


    private fun determineCorrectAnswer(): Int {
        // Ваша логика определения правильного ответа
        return 0
    }

    private fun saveQuestion(question: QuestionEntity) {
        // Логика сохранения или обновления вопроса в базе данных
    }

    private fun determineLanguage(textBeforeSpace: String): String {
        return "ua"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_QUIZ = "arg_quiz"
        private const val ARG_QUESTION = "arg_question"

        fun newInstance(
            idQuiz: Int
        ): CreateQuestionFragment {
            val fragment = CreateQuestionFragment()
            val args = Bundle()
            args.putInt(ARG_QUIZ, idQuiz)
            fragment.arguments = args
            return fragment
        }
    }
}