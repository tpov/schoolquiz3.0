package com.tpov.schoolquiz.presentation.main

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tpov.common.data.model.local.CategoryData
import com.tpov.common.presentation.quiz.QuizFragment
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.di.DaggerApplicationComponent
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Inject

class MainFragment : Fragment(R.layout.fragment_main), OnItemClickListener {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var viewModel: MainViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MainAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val applicationComponent = DaggerApplicationComponent.factory()
            .create(requireActivity().application)

        applicationComponent.inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)

        viewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]

        viewModel.categoryData.observe(viewLifecycleOwner) { categoryDataList ->
            adapter = MainAdapter(categoryDataList, this)
            recyclerView.adapter = adapter
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onItemClick(category: CategoryData) {
        // Создаем новый фрагмент, который нужно показать
        val newFragment = QuizFragment()

        // Создаем Bundle для передачи данных, если необходимо
        val args = Bundle()
        args.putString("key", category.nameQuiz)
        newFragment.arguments = args

        // Выполняем транзакцию замены фрагмента
        requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.title_fragment, newFragment)
                .addToBackStack(null)
                .commit()
    }
}
