package com.tpov.schoolquiz.presentation.main

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tpov.common.EVENT_QUIZ_HOME
import com.tpov.common.data.model.local.CategoryData
import com.tpov.common.di.CommonComponent
import com.tpov.common.presentation.quiz.QuizFragment
import com.tpov.log_api.logger.Logger
import com.tpov.schoolquiz.MainApp
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.di.DaggerApplicationComponent
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Inject

@Logger
class MainFragment : Fragment(R.layout.fragment_main), OnItemClickListener {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var viewModel: MainViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MainAdapter
    lateinit var commonComponent: CommonComponent

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


        Log.d("MainFragment", "onViewCreated()")
        viewModel.categoryData.observe(viewLifecycleOwner) { categoryDataList ->
            Log.d("MainFragment", "categoryDataList: $categoryDataList")
            adapter = MainAdapter(categoryDataList, this)
            recyclerView.adapter = adapter
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onItemClick(category: CategoryData) {
        val fragment = QuizFragment.newInstance(EVENT_QUIZ_HOME, category.id, -1, -1)

        Log.d("варпроп", "category: $category")
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.title_fragment, fragment)
            .addToBackStack(null)
            .commit()
    }

}
