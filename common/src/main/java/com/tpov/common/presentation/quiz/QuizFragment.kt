package com.tpov.common.presentation.quiz

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tpov.common.UNKNOWN_VALUE
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.utils.RotateInItemAnimator
import com.tpov.common.databinding.FragmentQuizBinding
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
    private var navigationProvider: NavigationProvider? = null

    private lateinit var quizViewModel: QuizActivityViewModel
    private lateinit var binding: FragmentQuizBinding
    private var oldIdQuizEvent1 = 0
    private lateinit var adapter: QuizActivityAdapter
    private var createQuiz = false

    private var idEvent = UNKNOWN_VALUE
    var idCategory = UNKNOWN_VALUE
    var idSubCategory = UNKNOWN_VALUE
    var idSubsubCategory = UNKNOWN_VALUE
    private var nameCategory = ""
    private var nameSubCategory = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        quizViewModel = ViewModelProvider(this, viewModelFactory)[QuizActivityViewModel::class.java]

        initGetData()
        quizViewModel.initQuestionListByIds(idEvent, idCategory, idSubCategory)
        initAdapter()
        initPath()
    }

    private fun initPath() {
        binding.tvEventPath.text = quizViewModel.getNamePathEvent(idEvent)
        binding.tvCatPath.text = nameCategory
        binding.tvSubcatPath.text = nameSubCategory
    }

    private fun initAdapter() {
        adapter = QuizActivityAdapter(this, requireContext(), quizViewModel)
        binding.rvQuizFragment.layoutManager = LinearLayoutManager(activity)
        binding.rvQuizFragment.adapter = adapter
        binding.rvQuizFragment.itemAnimator = RotateInItemAnimator()

        lifecycleScope.launch(Dispatchers.Main) {
            quizViewModel.listFlattenedQuizDataFlow.collect { list ->
                adapter.submitList(list)
            }
        }

        adapter.onDeleteButtonClick = { quizEntity ->

        }
    }

    private fun initGetData() {
        idEvent = arguments?.getInt(KEY_ID_EVENT, UNKNOWN_VALUE) ?: UNKNOWN_VALUE
        idCategory = arguments?.getInt(KEY_ID_CATEGORY, UNKNOWN_VALUE) ?: UNKNOWN_VALUE
        idSubCategory = arguments?.getInt(KEY_ID_SUB_CATEGORY, UNKNOWN_VALUE) ?: UNKNOWN_VALUE
        idSubsubCategory = arguments?.getInt(KEY_ID_SUB_SUB_CATEGORY, UNKNOWN_VALUE) ?: UNKNOWN_VALUE
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
         if (idSubCategory == UNKNOWN_VALUE) {
            idSubCategory = id
             restartFragment()
        } else if (idSubsubCategory == UNKNOWN_VALUE) {
             navigationProvider?.openQuestionActivity(id, typeQuestion)
        } else {
            navigationProvider?.openQuestionActivity(id, typeQuestion)
         }

    }
    private fun restartFragment() {
        val fragmentManager = parentFragmentManager
        fragmentManager.beginTransaction()
            .replace(this.id, newInstance(idEvent, idCategory, idSubCategory, idSubsubCategory))
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