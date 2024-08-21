package com.tpov.schoolquiz.presentation.main

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.presentation.question.QuestionActivity
import com.tpov.common.presentation.question.QuestionActivity.Companion.HARD_QUESTION
import com.tpov.common.presentation.question.QuestionActivity.Companion.ID_QUIZ
import com.tpov.common.presentation.question.QuestionActivity.Companion.NAME_USER
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.databinding.FragmentTitleBinding
import com.tpov.schoolquiz.presentation.core.Logcat
import kotlinx.coroutines.InternalCoroutinesApi

@InternalCoroutinesApi
class FragmentMain : Fragment(), MainActivityAdapter.Listener {

    @OptIn(InternalCoroutinesApi::class)
    fun log(m: String) {
        Logcat.log(m, "Main", Logcat.LOG_FRAGMENT)
    }

    private lateinit var mainViewModel: MainActivityViewModel

    private var oldIdQuizEvent1 = 0

    private lateinit var adapter: MainActivityAdapter

    private lateinit var binding: FragmentTitleBinding
    private var createQuiz = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainViewModel =
            ViewModelProvider(this)[MainActivityViewModel::class.java]

        adapter = MainActivityAdapter(this, requireContext(), mainViewModel)
        binding.rcView.layoutManager = LinearLayoutManager(activity)
        binding.rcView.adapter = adapter
        binding.rcView.itemAnimator = RotateInItemAnimator()
        // Обработка нажатия на кнопку удаления
        adapter.onDeleteButtonClick = { quizEntity ->
        }

        val isMyQuiz = arguments?.getInt(ARG_IS_MY_QUIZ, 1)


        when (isMyQuiz) {
            1 -> {
                binding.fabAddItem.visibility = View.VISIBLE
                binding.fabSearch.visibility = View.GONE
                binding.fabBox.visibility = View.GONE
            }

            5 -> {
                binding.fabSearch.visibility = View.VISIBLE
                binding.fabAddItem.visibility = View.GONE
                binding.fabBox.visibility = View.GONE
            }

            8 -> {
                binding.fabBox.visibility = View.VISIBLE
                binding.fabAddItem.visibility = View.GONE
                binding.fabSearch.visibility = View.GONE
            }

            else -> {
                binding.fabBox.visibility = View.GONE
                binding.fabAddItem.visibility = View.GONE
                binding.fabSearch.visibility = View.GONE
            }
        }

        binding.fabBox.setOnClickListener {
            Toast.makeText(activity, R.string.soon___, Toast.LENGTH_SHORT).show()
        }


    }

    override fun onResume() {
        super.onResume()
        val fabAddItem = binding.fabAddItem
        fabAddItem.setOnClickListener {
            // Добавление нового элемента в список
            val fragmentManager = activity?.supportFragmentManager
            fragmentManager?.let {

            }
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTitleBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun deleteItem(id: Int) {
    }

    override fun onClick(id: Int, typeQuestion: Boolean) {
        log("onClick mainViewModel.getQuizById(id).event")

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

        questionList.forEach {
            log("kokol question: ${it.numQuestion}. ${it.nameQuestion} - ${it.language}")
        }

        log("kokol foundQuestion: $foundQuestion")
        listMap.forEach {

            try {
                if (questionList[it.key - 1].id == null) foundQuestion = false
            } catch (e: Exception) {
                log("kokol catch: $it")
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

    private fun dialogNolics(
        id: Int,
        type: Boolean,
        nolics: Int
    ) {
        if (id == -1) {
            lifecycleScope.launchWhenStarted {

            }
        } else {
            val alertDialog = AlertDialog.Builder(activity)
                .setTitle(R.string.paid_attempt_title)
                .setMessage(R.string.paid_attempt_message)
                .setPositiveButton("(-) $nolics nolics") { dialog, which ->
                    val intent = Intent(activity, QuestionActivity::class.java)
                    intent.putExtra(NAME_USER, "user")
                    intent.putExtra(ID_QUIZ, id)
                    intent.putExtra(HARD_QUESTION, type)
                    startActivityForResult(intent, REQUEST_CODE)
                }
                .setNegativeButton(R.string.cancel_button, null)
                .create()

            alertDialog.setOnShowListener { dialog ->
                val positiveButton =
                    (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
                val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

                positiveButton.setTextColor(Color.WHITE)
                negativeButton.setTextColor(Color.YELLOW)

                dialog.window?.setBackgroundDrawableResource(R.drawable.db_design3_main)
            }

            alertDialog.show()
        }

    }


    override fun editItem(id: Int) {
        log("editItem: $id")
        val fragmentManager = activity?.supportFragmentManager
        fragmentManager?.let {

        }
    }

    override fun sendItem(id: Int) {

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.let {
                val translate = it.getBooleanExtra("translate", false)
                val idQuiz = it.getIntExtra("idQuiz", 0)

            }
        }
    }

    override fun reloadData() {
        activity?.recreate()
    }

    companion object {

        const val ARG_IS_MY_QUIZ = "is_my_quiz"
        const val REQUEST_CODE = 1

        @JvmStatic
        fun newInstance(idQuiz: Int): FragmentMain {
            val args = Bundle()
            args.putInt(ARG_IS_MY_QUIZ, idQuiz)
            val fragment = FragmentMain()
            fragment.arguments = args
            return fragment
        }
    }
}