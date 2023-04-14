package com.tpov.schoolquiz.presentation.network.event

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.databinding.TitleFragmentBinding
import com.tpov.schoolquiz.databinding.TitleFragmentBinding.inflate
import com.tpov.schoolquiz.presentation.MainApp
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory
import com.tpov.schoolquiz.presentation.fragment.BaseFragment
import com.tpov.schoolquiz.presentation.mainactivity.MainActivity
import com.tpov.schoolquiz.presentation.mainactivity.MainActivityAdapter
import com.tpov.schoolquiz.presentation.mainactivity.MainActivityViewModel
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Inject


@InternalCoroutinesApi
class ArenaFragment : BaseFragment(), MainActivityAdapter.Listener {


    @OptIn(InternalCoroutinesApi::class)
    fun log(m: String) {
        Logcat.log(m, "Main", Logcat.LOG_FRAGMENT)
    }

    private lateinit var viewModel: MainActivityViewModel

    @Inject
    lateinit var viewModelFactory: ViewModelFactory
    private val component by lazy {
        (requireActivity().application as MainApp).component
    }

    private lateinit var adapter: MainActivityAdapter

    private lateinit var binding: TitleFragmentBinding
    private var createQuiz = false

    override fun onAttach(context: Context) {
        component.inject(this)
        super.onAttach(context)
    }

    override fun onClickNew(name: String, stars: Int) {

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this, viewModelFactory)[MainActivityViewModel::class.java]

    }

    override fun onResume() {
        super.onResume()


    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = inflate(inflater, container, false)
        // binding.swipeRefreshLayout.setOnRefreshListener { reloadData() }
        return binding.root
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.let {
                val translate = it.getBooleanExtra("translate", false)
                val idQuiz = it.getIntExtra("idQuiz",0)

                if (translate) (requireActivity() as MainActivity).replaceFragment(
                    TranslateQuestionFragment.newInstance(idQuiz, null))
            }
        }
    }

    override fun deleteItem(id: Int) {

    }

    override fun onClick(id: Int, stars: Int) {
    }

    override fun shareItem(id: Int, stars: Int) {
    }

    override fun sendItem(quizEntity: QuizEntity) {
    }

    override fun reloadData() {
        activity?.recreate()
    }

    fun onDeleteButtonClick() {
        // Код, который будет выполнен при нажатии на кнопку "Удалить"
    }

    fun onEditButtonClick() {
        Log.d("ffsefsf", "deleteItem = $id")
    }

    companion object {
        @JvmStatic
        fun newInstance() = ArenaFragment()

        const val CREATE_QUIZ = ""
        const val DELETE_QUIZ = "deleteQuiz"
        const val SHARE_QUIZ = "shareQuiz"
        const val REQUEST_CODE = 1
    }
}