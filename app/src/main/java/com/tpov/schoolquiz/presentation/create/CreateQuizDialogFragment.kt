package com.tpov.schoolquiz.presentation.create

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.tpov.common.databinding.ActivityQuizItemBinding
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.presentation.model.PathStructure
import com.tpov.schoolquiz.MainApp
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.databinding.FragmentDialogCreateQuizBinding
import kotlinx.coroutines.launch
import javax.inject.Inject

class CreateQuizDialogFragment : DialogFragment() {

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
        setupItemBindings()
        setupQuizItems()
        setupButtons()
        observeViewModel()
    }

    private fun setupItemBindings() {
        categoryItemBinding = ActivityQuizItemBinding.bind(binding.categoryItem.root)
        subCategoryItemBinding = ActivityQuizItemBinding.bind(binding.subCategoryItem.root)
        subSubCategoryItemBinding = ActivityQuizItemBinding.bind(binding.subSubCategoryItem.root)
        currentQuizItemBinding = ActivityQuizItemBinding.bind(binding.currentQuizItem.root)
    }

    private fun setupQuizItems() {
        // Обработчики нажатий только на фото
        categoryItemBinding.imageView.setOnClickListener {
            // Выбор изображения для категории
            openImagePicker { imagePath ->
                categoryItemBinding.imageView.setImageResource(getImageResource(imagePath))
            }
        }
        
        subCategoryItemBinding.imageView.setOnClickListener {
            // Выбор изображения для субкатегории
            openImagePicker { imagePath ->
                subCategoryItemBinding.imageView.setImageResource(getImageResource(imagePath))
            }
        }
        
        subSubCategoryItemBinding.imageView.setOnClickListener {
            // Выбор изображения для субсубкатегории
            openImagePicker { imagePath ->
                subSubCategoryItemBinding.imageView.setImageResource(getImageResource(imagePath))
            }
        }
        
        currentQuizItemBinding.imageView.setOnClickListener {
            // Выбор изображения для текущего квиза
            openImagePicker { imagePath ->
                currentQuizItemBinding.imageView.setImageResource(getImageResource(imagePath))
            }
        }
    }
    
    private fun openImagePicker(onImageSelected: (String) -> Unit) {
        // TODO: Реализовать выбор изображения
    }
    
    private fun getImageResource(imagePath: String): Int {
        return resources.getIdentifier(imagePath, "drawable", requireContext().packageName)
            ?: com.tpov.schoolquiz.R.drawable.ic_baseline_quiz
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

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnSave.setOnClickListener {
            // Получаем изображения из 4 элементов
            val categoryImage = getImageFromQuizItem(categoryItemBinding)
            val subCategoryImage = getImageFromQuizItem(subCategoryItemBinding)
            val subSubCategoryImage = getImageFromQuizItem(subSubCategoryItemBinding)
            val currentQuizImage = getImageFromQuizItem(currentQuizItemBinding)

            createQuizViewModel.saveQuiz(
                PathStructure(
                    EventQuiz.QUIZ_BY_USER.name,
                    binding.tvCreateCategory.text.toString(),
                    binding.tvCreateSubCategory.text.toString(),
                    binding.tvCreateSubsubCategory.text.toString(),
                    binding.tvCreateQuest.text.toString()
                ),
                createQuizViewModel.questionList,
                categoryImage,
                subCategoryImage,
                subSubCategoryImage,
                currentQuizImage
            )
            dismiss()
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
            createQuizViewModel.selectedCategory.collect { category ->
                category?.let {
                    binding.tvCreateCategory.setText(it.nameItem)
                    categoryItemBinding.imageView.setImageResource(
                        it.picture?.let { path ->
                            resources.getIdentifier(path, "drawable", requireContext().packageName)
                        } ?: com.tpov.schoolquiz.R.drawable.ic_baseline_quiz
                    )
                    categoryItemBinding.mainTitleButton.text = it.nameItem
                }
            }
        }

        lifecycleScope.launch {
            createQuizViewModel.selectedSubCategory.collect { subCategory ->
                subCategory?.let {
                    binding.tvCreateSubCategory.setText(it.nameItem)
                    subCategoryItemBinding.imageView.setImageResource(
                        it.picture?.let { path ->
                            resources.getIdentifier(path, "drawable", requireContext().packageName)
                        } ?: com.tpov.schoolquiz.R.drawable.ic_baseline_quiz
                    )
                    subCategoryItemBinding.mainTitleButton.text = it.nameItem
                }
            }
        }

        lifecycleScope.launch {
            createQuizViewModel.selectedSubSubCategory.collect { subSubCategory ->
                subSubCategory?.let {
                    binding.tvCreateSubsubCategory.setText(it.nameItem)
                    subSubCategoryItemBinding.imageView.setImageResource(
                        it.picture?.let { path ->
                            resources.getIdentifier(path, "drawable", requireContext().packageName)
                        } ?: com.tpov.schoolquiz.R.drawable.ic_baseline_quiz
                    )
                    subSubCategoryItemBinding.mainTitleButton.text = it.nameItem
                }
            }
        }

        lifecycleScope.launch {
            createQuizViewModel.selectedQuiz.collect { quiz ->
                quiz?.let {
                    binding.tvCreateQuest.setText(it)
                    currentQuizItemBinding.mainTitleButton.text = it
                }
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

    private fun getImageFromQuizItem(binding: ActivityQuizItemBinding): String {
        // Получаем drawable resource из ImageView и возвращаем его путь/имя
        val drawable = binding.imageView.drawable
        return when {
            drawable != null -> {
                // Пытаемся получить имя ресурса
                val resourceId = binding.imageView.tag as? Int ?: 0
                if (resourceId != 0) {
                    resources.getResourceEntryName(resourceId)
                } else {
                    "default_quiz_image"
                }
            }
            else -> "default_quiz_image"
        }
    }

    companion object {
        fun newInstance(): CreateQuizDialogFragment {
            return CreateQuizDialogFragment()
        }
    }
}
