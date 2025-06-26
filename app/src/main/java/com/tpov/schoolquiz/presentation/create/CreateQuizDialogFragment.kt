package com.tpov.schoolquiz.presentation.create

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.tpov.common.databinding.ActivityQuizItemBinding
import com.tpov.schoolquiz.MainApp
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.databinding.FragmentDialogCreateQuizBinding
import kotlinx.coroutines.launch
import javax.inject.Inject

class CreateQuizDialogFragment: DialogFragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private var _binding: FragmentDialogCreateQuizBinding? = null
    private val binding get() = _binding!!

    private lateinit var createQuizViewModel: CreateQuizViewModel

    // Bindings для включенных компонентов
    private lateinit var categoryItemBinding: ActivityQuizItemBinding
    private lateinit var subCategoryItemBinding: ActivityQuizItemBinding
    private lateinit var subSubCategoryItemBinding: ActivityQuizItemBinding
    private lateinit var currentQuizItemBinding: ActivityQuizItemBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireActivity().application as MainApp).applicationComponent.inject(this)
        // Используем Activity как владельца, чтобы ViewModel была разделяемой
        createQuizViewModel = ViewModelProvider(requireActivity(), viewModelFactory)[CreateQuizViewModel::class.java]
        setStyle(STYLE_NORMAL, R.style.FullScreenDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDialogCreateQuizBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hideSystemUI()
        setupItemBindings()
        setupQuizItems()
        setupCustomSpinner()
        setupButtons()
        observeViewModel()
    }

    private fun setupItemBindings() {
        // Получаем binding для каждого включенного компонента
        categoryItemBinding = ActivityQuizItemBinding.bind(binding.categoryItem.root)
        subCategoryItemBinding = ActivityQuizItemBinding.bind(binding.subCategoryItem.root)
        subSubCategoryItemBinding = ActivityQuizItemBinding.bind(binding.subSubCategoryItem.root)
        currentQuizItemBinding = ActivityQuizItemBinding.bind(binding.currentQuizItem.root)
    }

    private fun setupQuizItems() {
        // Настройка элементов интерфейса для отображения категорий, субкатегорий и квизов
        // TODO: Реализовать настройку RecyclerView или других компонентов для отображения списков
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

    private fun setupCustomSpinner() {
        // Добавляем тестовые данные для демонстрации
        val testQuizzes = listOf(
            "Линейные уравнения",
            "Квадратные уравнения",
            "Системы уравнений",
            "Неравенства"
        )
        binding.spinnerQuiz.setItems(testQuizzes)

        binding.spinnerQuiz.setOnItemSelectedListener { selectedQuizName ->
            createQuizViewModel.selectQuiz(selectedQuizName)
        }
    }

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnSave.setOnClickListener {
            if (createQuizViewModel.isReadyToSave()) {
                createQuizViewModel.saveQuiz()
                // TODO: Показать сообщение об успешном сохранении
                dismiss()
            } else {
                // TODO: Показать сообщение о том, что нужно выбрать все уровни
            }
        }

        binding.bLightQuestion.setOnClickListener {
            openCreateQuestionDialog(isHardQuestion = false)
        }

        binding.bHardQuestion.setOnClickListener {
            openCreateQuestionDialog(isHardQuestion = true)
        }

    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            // Наблюдаем за категориями
            createQuizViewModel.categoriesList.collect { categories ->

            }
        }

        lifecycleScope.launch {
            // Наблюдаем за субкатегориями
            createQuizViewModel.subCategoriesList.collect { subCategories ->

            }
        }

        lifecycleScope.launch {
            // Наблюдаем за субсубкатегориями
            createQuizViewModel.subSubCategoriesList.collect { subSubCategories ->

            }
        }

        lifecycleScope.launch {
            // Наблюдаем за списком квизов
            createQuizViewModel.quizzesList.collect { quizzes ->

            }
        }

        lifecycleScope.launch {
            // Обновляем выбранные элементы
            createQuizViewModel.selectedCategory.collect { category ->

            }
        }

        lifecycleScope.launch {
            createQuizViewModel.selectedSubCategory.collect { subCategory ->

            }
        }

        lifecycleScope.launch {
            createQuizViewModel.selectedSubSubCategory.collect { subSubCategory ->

            }
        }

        lifecycleScope.launch {
            createQuizViewModel.selectedQuiz.collect { quiz ->

            }
        }
    }

    private fun openCreateQuestionDialog(isHardQuestion: Boolean) {

        val questionDialog = CreateQuestionDialogFragment.newInstance(
            hardQuestion = isHardQuestion,
        )

        questionDialog.show(parentFragmentManager, "CreateQuestionDialog")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): CreateQuizDialogFragment {
            return CreateQuizDialogFragment()
        }
    }
}
