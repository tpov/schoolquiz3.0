package com.tpov.schoolquiz.presentation.create

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.tpov.common.SPLIT_BETWEEN_ANSWERS
import com.tpov.common.data.model.local.QuestionLocal
import com.tpov.common.databinding.ActivityQuestionBinding
import com.tpov.common.presentation.model.PathStructure
import com.tpov.schoolquiz.MainApp
import com.tpov.schoolquiz.R
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Simple DialogFragment version of QuestionActivity
 * Just opens the same UI layout as a fullscreen dialog
 */
class CreateQuestionDialogFragment : DialogFragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var binding: ActivityQuestionBinding
    private lateinit var createQuizViewModel: CreateQuizViewModel
    private lateinit var questionPagerAdapter: QuestionPagerAdapter
    private lateinit var imagePickerHelper: ImagePickerHelper
    private var pathStructure: PathStructure? = null
    private var hardQuiz: Boolean = false
    private var life: Int = 0
    private var currentSelectedQuestion: QuestionLocal? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireActivity().application as MainApp).applicationComponent.inject(this)
        // Используем Activity как владельца, чтобы ViewModel была разделяемой
        createQuizViewModel = ViewModelProvider(requireActivity(), viewModelFactory)[CreateQuizViewModel::class.java]
        setStyle(STYLE_NORMAL, R.style.FullScreenDialog)

        setupImagePicker()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = ActivityQuestionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setOnClickListener()
        setupDialog()
        setupViewPager()
        loadArguments()
        addMultipleTestQuestions()
        observeViewModel()
    }

    private fun setOnClickListener() {
        binding.tvTimer.text = "Save"
        binding.tvTimer.setOnClickListener {
            dismiss()
        }
    }

    private fun setupDialog() {
        // Setup dialog to be fullscreen
        dialog?.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun setupViewPager() {
        questionPagerAdapter = QuestionPagerAdapter(
            onImageClickListener = { question, position ->
                // Обработка клика по изображению
                currentSelectedQuestion = question
                imagePickerHelper.pickImage()
            },
            onQuestionTextChanged = { question, newText ->
                // Обновляем текст вопроса в ViewModel
                createQuizViewModel.updateQuestionText(
                    question.numQuestion,
                    question.hardQuestion,
                    question.language,
                    newText
                )
            },
            onAnswersChanged = { question, answers ->
                val answersString = answers.joinToString(SPLIT_BETWEEN_ANSWERS)
                createQuizViewModel.updateQuestionAnswers(
                    question.numQuestion,
                    question.hardQuestion,
                    question.language,
                    answersString
                )
            }
        )
        binding.viewPagerQuestions.adapter = questionPagerAdapter

        binding.viewPagerQuestions.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                // Проверяем, дошел ли пользователь до последнего элемента
                val currentList = createQuizViewModel.showQuestionList.value
                if (position == currentList.size - 1 && currentList.isNotEmpty()) {
                    createQuizViewModel.createEmptyQuestion(hardQuiz)
                }
            }
        })
    }

    private fun setupImagePicker() {
        imagePickerHelper = ImagePickerHelper(this) { imagePath ->
            // Когда изображение выбрано и сохранено
            currentSelectedQuestion?.let { question ->
                // Обновляем вопрос с новым путем к изображению
                createQuizViewModel.updateQuestionImage(question.numQuestion, question.hardQuestion, imagePath)

                // Сжимаем изображение в фоновом потоке (опционально)
                // compressImageAsync(imagePath)
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            createQuizViewModel.showQuestionList.collect { filteredQuestionList ->
                if (filteredQuestionList.isNotEmpty()) {
                    questionPagerAdapter.submitList(filteredQuestionList)

                    binding.viewPagerQuestions.visibility = View.VISIBLE
                    binding.tvNumQuestion.text = filteredQuestionList.size.toString()

                }
            }
        }
    }

    private fun hideSystemUI() {
        dialog?.window?.decorView?.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    private fun addMultipleTestQuestions() {

    }

    private fun loadArguments() {
        arguments?.let { args ->
            pathStructure = args.getParcelable(KEY_PATH_STRUCTURE) as? PathStructure
            hardQuiz = args.getBoolean(KEY_HARD_QUESTION, false)

            createQuizViewModel.showQuestions(hardQuiz, pathStructure ?: PathStructure())
        }
    }


    companion object {
        const val KEY_PATH_STRUCTURE = "path_structure"
        const val KEY_HARD_QUESTION = "hard_question"

        fun newInstance(
            hardQuestion: Boolean,
        ): CreateQuestionDialogFragment {
            return CreateQuestionDialogFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(KEY_HARD_QUESTION, hardQuestion)
                }
            }
        }
    }
}
