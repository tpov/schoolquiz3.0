package com.tpov.common.presentation.quiz

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tpov.common.UNKNOWN_VALUE
import com.tpov.common.data.model.entity.QuestionEntity
import com.tpov.common.data.model.local.StructureDataLocal
import com.tpov.common.data.utils.RotateInItemAnimator
import com.tpov.common.databinding.FragmentQuizBinding
import com.tpov.common.di.DaggerCommonComponent
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.presentation.NavigationProvider
import com.tpov.common.presentation.model.PathStructure
import com.tpov.log_api.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject

@InternalCoroutinesApi
class QuizFragment : Fragment(), QuizActivityAdapter.Listener {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private var navigationProvider: NavigationProvider? = null

    private lateinit var quizViewModel: QuizActivityViewModel
    private lateinit var binding: FragmentQuizBinding
    private var oldIdQuizEvent1 = 0
    private lateinit var adapter: QuizActivityAdapter
    private var createQuiz = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        quizViewModel = ViewModelProvider(this, viewModelFactory)[QuizActivityViewModel::class.java]

        initGetData()
        quizViewModel.initQuestionListByIds()
        initAdapter()
        initPath()
    }

    @SuppressLint("SetTextI18n")
    private fun initPath() {
        binding.tvEventPath.text =
            quizViewModel.pathStructure?.nameEvent!! +
                    if (quizViewModel.nameCategory != "") " > " else ""
        binding.tvCatPath.text = quizViewModel.nameCategory +
                if (quizViewModel.nameSubCategory != "") " > " else ""
        binding.tvSubcatPath.text = quizViewModel.nameSubCategory
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        DaggerCommonComponent.factory()
            .create(requireActivity().application)
            .inject(this)

        if (context is NavigationProvider) {
            navigationProvider = context
        }
    }

    private fun initAdapter() {
        Log.d("ksjergfkjkseklf", "initAdapter")
        adapter = QuizActivityAdapter(this, requireContext(), quizViewModel)
        binding.rvQuizFragment.layoutManager = LinearLayoutManager(activity)
        binding.rvQuizFragment.adapter = adapter
        binding.rvQuizFragment.itemAnimator = RotateInItemAnimator()

        lifecycleScope.launch(Dispatchers.Main) {
            Log.d("ksjergfkjkseklf", "lifecycleScope")
            quizViewModel.listStructureDataLocalFlow.collect { list ->
                Log.d("ksjergfkjkseklf", "list: $list")

                adapter.submitList(list)
            }
        }

        adapter.onDeleteButtonClick = { quizEntity ->

        }
    }

    private fun initGetData() {
        quizViewModel.pathStructure?.nameEvent = arguments?.getString(KEY_ID_EVENT)!!
        quizViewModel.pathStructure?.nameCategory = arguments?.getString(KEY_ID_CATEGORY) ?: ""
        quizViewModel.pathStructure?.nameSubCategory = arguments?.getString(KEY_ID_SUB_CATEGORY) ?: ""
        quizViewModel.pathStructure?.nameSubsubCategory = arguments?.getString(KEY_ID_SUB_SUB_CATEGORY) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentQuizBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun deleteItem(pathStructure: PathStructure) {}

    override fun onClick(structureDataLocal: StructureDataLocal?, typeQuestion: Boolean) {
        if (quizViewModel.pathStructure?.nameSubCategory == UNKNOWN_VALUE && quizViewModel.pathStructure?.nameEvent == EventQuiz.QUIZ_HOME.name) {
            quizViewModel.pathStructure!!.nameSubCategory = structureDataLocal?.nameItem!!
            restartFragment(quizViewModel.pathStructure!!)
        } else if (quizViewModel.pathStructure?.nameSubsubCategory == UNKNOWN_VALUE && quizViewModel.pathStructure?.nameEvent == EventQuiz.QUIZ_HOME.name) {
            quizViewModel.pathStructure?.nameSubsubCategory = structureDataLocal?.nameItem!!
            restartFragment(quizViewModel.pathStructure!!)
        } else if (quizViewModel.pathStructure?.nameQuiz == UNKNOWN_VALUE && quizViewModel.pathStructure?.nameEvent == EventQuiz.QUIZ_HOME.name) {
            quizViewModel.pathStructure?.nameQuiz = structureDataLocal?.nameItem!!
            navigationProvider?.openQuestionActivity(quizViewModel.pathStructure!!, typeQuestion)
        } else if (quizViewModel.pathStructure?.nameEvent == EventQuiz.QUIZ_BY_USER.name) {
            quizViewModel.pathStructure = quizViewModel.getHomePath[structureDataLocal]
            navigationProvider?.openQuestionActivity(quizViewModel.pathStructure!!, typeQuestion)
        }
    }

    private fun restartFragment(pathStructure: PathStructure) {
        val fragmentManager = parentFragmentManager
        fragmentManager.beginTransaction()
            .replace(
                this.id,
                newInstance(PathStructure(
                    pathStructure.nameEvent,
                    pathStructure.nameCategory,
                    pathStructure.nameSubCategory,
                    pathStructure.nameSubsubCategory
                ))
            )
            .addToBackStack(null)
            .commit()
    }

    private fun getMap(
        listQuestionEntity: List<QuestionEntity>,
        listMap: MutableMap<Int, Boolean>
    ): MutableMap<Int, Boolean> {

        listQuestionEntity.forEach {
            listMap[it.numQuestion] = false
        }
        return listMap
    }

    override fun editItem(pathStructure: PathStructure) {
        val fragmentManager = activity?.supportFragmentManager
        fragmentManager?.let {

        }
    }

    override fun sendItem(pathStructure: PathStructure) {

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.let { intent ->

            }
        }
    }

    override fun reloadData() {
        activity?.recreate()
    }

    companion object {

        const val KEY_ID_EVENT = "key_id_event"
        const val KEY_ID_CATEGORY = "key_id_category"
        const val KEY_ID_SUB_CATEGORY = "key_id_sub_category"
        const val KEY_ID_SUB_SUB_CATEGORY = "key_id_subsub_category"

        const val REQUEST_CODE = 1

        @JvmStatic
        fun newInstance(
            pathStructure: PathStructure
        ): QuizFragment {
            val args = Bundle()
            args.putString(KEY_ID_EVENT, pathStructure.nameEvent)
            args.putString(KEY_ID_CATEGORY, pathStructure.nameCategory)
            args.putString(KEY_ID_SUB_CATEGORY, pathStructure.nameSubCategory)
            args.putString(KEY_ID_SUB_SUB_CATEGORY, pathStructure.nameSubsubCategory)
            val fragment = QuizFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
