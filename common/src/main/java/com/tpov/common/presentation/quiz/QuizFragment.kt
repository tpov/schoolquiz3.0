package com.tpov.common.presentation.quiz

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.utils.RotateInItemAnimator
import com.tpov.common.databinding.FragmentQuizBinding
import com.tpov.common.di.DaggerCommonComponent
import com.tpov.common.presentation.NavigationProvider
import com.tpov.log_api.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject

@Logger
@InternalCoroutinesApi
class QuizFragment : Fragment(), QuizActivityAdapter.Listener {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var mainViewModel: QuizActivityViewModel
    private var navigationProvider: NavigationProvider? = null

    private lateinit var binding: FragmentQuizBinding
    private lateinit var adapter: QuizActivityAdapter

    private var createQuiz = false

    private var idEvent = -1
    var idCategory = -1
    var idSubCategory = -1
    var idSubsubCategory = -1


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainViewModel = ViewModelProvider(this, viewModelFactory)[QuizActivityViewModel::class.java]
        initGetData()
        mainViewModel.initQuestionListByIds(idEvent, idCategory, idSubCategory)
        initAdapter()
        Log.d("jfersdklfgjskledf", "idEvent: $idEvent idCategory: $idCategory idSubCategory: $idSubCategory idSubsubCategory: $idSubsubCategory ")
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
        adapter = QuizActivityAdapter(this, requireContext(), mainViewModel)
        binding.rvQuizFragment.layoutManager = LinearLayoutManager(activity)
        binding.rvQuizFragment.adapter = adapter
        binding.rvQuizFragment.itemAnimator = RotateInItemAnimator()

        lifecycleScope.launch(Dispatchers.IO) {
            mainViewModel.listFlattenedQuizDataFlow.collect { list ->
                Log.d("dawdasf", list.toString())
                adapter.submitList(list)
            }
        }

        adapter.onDeleteButtonClick = { quizEntity ->

        }
    }

    private fun initGetData() {

        idEvent = arguments?.getInt(KEY_ID_EVENT, -1) ?: -1
        idCategory = arguments?.getInt(KEY_ID_CATEGORY, -1) ?: -1
        idSubCategory = arguments?.getInt(KEY_ID_SUB_CATEGORY, -1) ?: -1
        idSubsubCategory = arguments?.getInt(KEY_ID_SUB_SUB_CATEGORY, -1) ?: -1

    }

    override fun onResume() {
        super.onResume()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentQuizBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun deleteItem(id: Int) {
    }

    override fun onClick(id: Int, typeQuestion: Boolean) {
        Log.d("jfersdklfgjskledf", "idEvent: $idEvent idCategory: $idCategory idSubCategory: $idSubCategory idSubsubCategory: $idSubsubCategory ")
         if (idSubCategory == -1) {
             Log.d("jfersdklfgjskledf", "1")
            idSubCategory = id
            mainViewModel.initQuestionListByIds(idEvent, idCategory, idSubCategory)
             initAdapter()
        } else if (idSubsubCategory == -1) {
             Log.d("jfersdklfgjskledf", "2")
            idSubsubCategory = id
            mainViewModel.initQuestionListByIds(idEvent, idCategory, idSubCategory)
             initAdapter()
        } else navigationProvider?.openQuestionActivity(id, typeQuestion)
        Log.d("jfersdklfgjskledf", "idEvent: $idEvent idCategory: $idCategory idSubCategory: $idSubCategory idSubsubCategory: $idSubsubCategory ")

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

    private fun getUserLocalization(context: Context): String {
        val config: Configuration = context.resources.configuration
        return config.locale.language
    }

    private fun getListQuestionByProfileLang(
        questionThisListAll: List<QuestionEntity>,
        listMap: MutableMap<Int, Boolean>
    ): ArrayList<QuestionEntity> {
        val userLocalization: String = getUserLocalization(requireContext())

        val questionList = ArrayList<QuestionEntity>()

        listMap.forEach { map ->
            var filteredList = questionThisListAll
                .filter { it.numQuestion == map.key }
                .filter { it.language == userLocalization }

            if (filteredList.isNotEmpty()) {
                questionList.add(filteredList[0])
            } else {
                filteredList = questionThisListAll
                    .filter { it.numQuestion == map.key }

                if (filteredList.isNotEmpty()) {
                    questionList.add(filteredList[0])
                }
            }
        }
        return questionList
    }

    private fun didFoundAllQuestion(
        questionList: List<QuestionEntity>,
        listMap: MutableMap<Int, Boolean>
    ): Boolean {
        var foundQuestion = listMap.isNotEmpty()

        listMap.forEach {

            try {
                if (questionList[it.key - 1].id == null) foundQuestion = false
            } catch (e: Exception) {

                foundQuestion = false
            }
        }

        return foundQuestion
    }

    private fun getListQuestionListByLocal(
        listMap: MutableMap<Int, Boolean>,
        questionThisListAll: List<QuestionEntity>
    ): ArrayList<QuestionEntity> {
        val userLocalization: String = getUserLocalization(requireContext())

        val questionList = ArrayList<QuestionEntity>()
        listMap.forEach { map ->
            val filteredList = questionThisListAll
                .filter { it.numQuestion == map.key }
                .filter { it.language == userLocalization }

            if (filteredList.isNotEmpty()) questionList.add(filteredList[0])
        }

        return questionList
    }

    override fun editItem(id: Int) {
        val fragmentManager = activity?.supportFragmentManager
        fragmentManager?.let {

        }
    }

    override fun sendItem(id: Int) {

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
        fun newInstance(idEvent: Int, idCategory: Int, idSubCategory: Int, idSubsubCategory: Int): QuizFragment {
            val args = Bundle()
            args.putInt(KEY_ID_EVENT, idEvent)
            args.putInt(KEY_ID_CATEGORY, idCategory)
            args.putInt(KEY_ID_SUB_CATEGORY, idSubCategory)
            args.putInt(KEY_ID_SUB_SUB_CATEGORY, idSubsubCategory)
            val fragment = QuizFragment()
            fragment.arguments = args
            return fragment
        }
    }
}