package com.tpov.schoolquiz.presentation.main

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tpov.common.di.CommonComponent
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.quiz.QuizFragment
import com.tpov.log_api.logger.Logger
import com.tpov.schoolquiz.MainApp
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.di.DaggerApplicationComponent
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject

class MainFragment : Fragment(R.layout.fragment_main), OnItemClickListener {

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
                    adapter = MainAdapter(categoryDataList, this@MainFragment)
                    recyclerView.adapter = adapter
            }
        }
        initGetData()
        viewModel.initStructureData(event ?: EventQuiz.QUIZ_HOME)
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
}
