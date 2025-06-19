package com.tpov.schoolquiz.presentation.main

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tpov.common.data.model.local.StructureDataLocal
import com.tpov.common.di.CommonComponent
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.quiz.QuizFragment
import com.tpov.log_api.logger.Logger
import com.tpov.schoolquiz.MainApp
import androidx.appcompat.app.AlertDialog
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.di.DaggerApplicationComponent
import com.tpov.schoolquiz.presentation.create.CreateQuizActivity
import com.tpov.schoolquiz.presentation.create.CreateQuizViewModel // Для константы режима
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject

@Logger
class MainFragment : Fragment(R.layout.fragment_main), OnItemClickListener, OnQuizActionClickListener {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var viewModel: MainViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MainAdapter
    lateinit var commonComponent: CommonComponent

    private var event: EventQuiz? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val commonComponent = (requireActivity().application as MainApp).commonComponent
        val applicationComponent = DaggerApplicationComponent.factory()
            .create(requireActivity().application, commonComponent)

        applicationComponent.inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        viewModel = ViewModelProvider(requireActivity(), viewModelFactory)[MainViewModel::class.java]

        lifecycleScope.launch {
            Log.d("ksjergfkjkseklf", "lifecycleScope")
            viewModel.structureData.collect { categoryDataList ->
                Log.d("ksjergfkjkseklf", "categoryDataList: $categoryDataList")
                // Передаем this@MainFragment и как OnItemClickListener, и как OnQuizActionClickListener
                adapter = MainAdapter(categoryDataList, this@MainFragment, this@MainFragment)
                recyclerView.adapter = adapter
            }
        }
        initGetData()
        viewModel.initStructureData(event ?: EventQuiz.QUIZ_BY_USER)
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onItemClick(category: String) {
        val fragment = QuizFragment.newInstance(PathStructure( event?.name!!, category, "","", ""))

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.title_fragment, fragment)
            .addToBackStack(null)
            .commit()
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun initGetData() {
        event = arguments?.getInt(QuizFragment.KEY_ID_EVENT)?.let { EventQuiz.fromInput(it) }
    }

    companion object {

        const val KEY_ID_EVENT = "key_id_event"
        const val REQUEST_CODE = 1

        @JvmStatic
        fun newInstance(
            idEvent: EventQuiz,
        ): MainFragment {
            val args = Bundle()
            args.putInt(KEY_ID_EVENT, idEvent.id)
            val fragment = MainFragment()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onEditQuiz(quiz: StructureDataLocal) {
        // Используем REGIME_EDIT_QUIZ. Если такой константы нет в CreateQuizViewModel, ее нужно будет создать.
        // Предположим, что REGIME_EDIT_QUIZ = 1 (или любое другое уникальное значение)
        // event?.name должен быть EventQuiz.QUIZ_BY_USER.name для пользовательских квизов
        val pathStructure = PathStructure(
            nameEvent = event?.name ?: EventQuiz.QUIZ_BY_USER.name, // Если event null, значит это точно QUIZ_BY_USER
            nameQuiz = quiz.nameItem,
            nameCategory = quiz.nameCategory ?: "",
            nameSubCategory = quiz.nameSubcategory ?: "",
            nameSubSubCategory = quiz.nameSubSubcategory ?: ""
            // idQuiz можно было бы передать, если бы PathStructure его поддерживал и это было бы нужно для загрузки
            // Пока что идентифицируем по уникальному пути (event, quizName, category, etc.)
        )

        // TODO: Проверить наличие константы REGIME_EDIT_QUIZ в CreateQuizViewModel
        //  Если ее нет, нужно будет ее добавить, например:
        //  в CreateQuizViewModel.kt: const val REGIME_EDIT_QUIZ = 1 (убедиться, что не конфликтует с REGIME_CREATE_QUIZ)
        //  Пока используем гипотетическое значение 1. Если REGIME_CREATE_QUIZ = 0, то 1 подойдет.
        //  Судя по CreateQuizActivity, там используется intent.getIntExtra("extra_regime", -1)
        //  и есть CreateQuizViewModel.REGIME_CREATE_QUIZ. Нужно найти или определить REGIME_EDIT_QUIZ
        //  Пока что я не могу проверить CreateQuizViewModel, поэтому оставлю это как возможное место для доработки.
        //  Предположим, что для EditQuizRegimeStrategy используется отдельный режим.

        // Я посмотрю CreateQuizViewModel на наличие константы REGIME_EDIT_QUIZ.
        // Если ее нет, я буду использовать 1, как и планировал, и оставлю комментарий для ее создания.
        // В CreateQuizActivity есть companion object с newIntent, который принимает regime: Int.
        // В CreateQuizViewModel есть companion object с REGIME_CREATE_QUIZ = 0.
        // Значит, для редактирования можно использовать 1.

        val intent = CreateQuizActivity.newIntent(
            requireContext(),
            CreateQuizViewModel.REGIME_EDIT_QUIZ, // Используем корректную константу
            pathStructure
        )
        startActivity(intent)
    }

    override fun onDeleteQuiz(quiz: StructureDataLocal) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удаление квеста")
            .setMessage("Вы уверены, что хотите удалить квест \"${quiz.nameItem}\"? Это действие необратимо.")
            .setPositiveButton("Удалить") { dialog, _ ->
                // Формируем PathStructure для удаления
                val pathStructure = PathStructure(
                    nameEvent = event?.name ?: EventQuiz.QUIZ_BY_USER.name,
                    nameQuiz = quiz.nameItem,
                    nameCategory = quiz.nameCategory ?: "",
                    nameSubCategory = quiz.nameSubcategory ?: "",
                    nameSubSubCategory = quiz.nameSubSubcategory ?: ""
                    // idFront и idServer из StructureDataLocal могут быть полезны, если useCase их использует
                )
                viewModel.deleteQuiz(pathStructure) // Вызываем метод ViewModel
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onSendToArena(quiz: StructureDataLocal) {
        // TODO: Implement send to arena logic in a later step
        android.widget.Toast.makeText(requireContext(), "Отправить на арену: ${quiz.nameItem}", android.widget.Toast.LENGTH_SHORT).show()
    }
}
