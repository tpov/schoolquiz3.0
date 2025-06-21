package com.tpov.schoolquiz.presentation.question

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.databinding.FragmentQuestionEditBinding
import com.tpov.schoolquiz.presentation.create.QuestionItem
import com.tpov.schoolquiz.presentation.create.SimpleCreateQuizViewModel

class QuestionEditFragment : Fragment() {

    private var _binding: FragmentQuestionEditBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SimpleCreateQuizViewModel // Shared ViewModel with Activity
    private var currentQuestionItem: QuestionItem? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri: Uri? = result.data?.data
            imageUri?.let {
                Glide.with(this)
                    .load(it)
                    .into(binding.imageViewQuestion)
                // Convert Uri to BitmapDrawable and update the questionItem
                try {
                    val bitmap = MediaStore.Images.Media.getBitmap(requireActivity().contentResolver, it)
                    currentQuestionItem?.image = BitmapDrawable(resources, bitmap)
                    updateViewModel()
                } catch (e: Exception) {
                    // Handle exception
                }
            }
        }
    }

    companion object {
        private const val ARG_QUESTION_ID = "question_id"

        fun newInstance(questionId: Int): QuestionEditFragment {
            val fragment = QuestionEditFragment()
            val args = Bundle()
            args.putInt(ARG_QUESTION_ID, questionId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQuestionEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[SimpleCreateQuizViewModel::class.java]

        val questionId = arguments?.getInt(ARG_QUESTION_ID)
        if (questionId != null) {
            viewModel.questions.observe(viewLifecycleOwner) { questions ->
                currentQuestionItem = questions.firstOrNull { it.id == questionId }
                currentQuestionItem?.let { populateUi(it) }
            }
        }

        setupListeners()
    }

    private fun populateUi(question: QuestionItem) {
        binding.editTextQuestion.setText(question.questionText)
        question.image?.let {
            binding.imageViewQuestion.setImageDrawable(it)
        } ?: run {
            binding.imageViewQuestion.setImageResource(R.drawable.ic_add_photo) // Placeholder
        }

        binding.linearLayoutAnswers.removeAllViews()
        question.answers.forEach { answer ->
            addAnswerView(answer)
        }
    }

    private fun setupListeners() {
        binding.editTextQuestion.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                currentQuestionItem?.questionText = binding.editTextQuestion.text.toString()
                updateViewModel()
            }
        }

        binding.buttonAddAnswer.setOnClickListener {
            currentQuestionItem?.answers?.add(QuestionItem.AnswerItem("", false))
            currentQuestionItem?.let { addAnswerView(it.answers.last()) }
            updateViewModel()
        }

        binding.imageViewQuestion.setOnClickListener {
            openGalleryForImage()
        }

        // TODO: Add listeners for other fields like points, isHard, typeQuestion if they are added to the layout
    }

    private fun addAnswerView(answer: QuestionItem.AnswerItem) {
        val answerView = LayoutInflater.from(context).inflate(R.layout.item_answer_edit, binding.linearLayoutAnswers, false)
        val editTextAnswer = answerView.findViewById<EditText>(R.id.editTextAnswer)
        val checkBoxCorrect = answerView.findViewById<CheckBox>(R.id.checkBoxCorrect)
        val buttonRemoveAnswer = answerView.findViewById<Button>(R.id.buttonRemoveAnswer)

        editTextAnswer.setText(answer.text)
        checkBoxCorrect.isChecked = answer.isCorrect

        editTextAnswer.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                answer.text = editTextAnswer.text.toString()
                updateViewModel()
            }
        }

        checkBoxCorrect.setOnCheckedChangeListener { _, isChecked ->
            // Ensure only one answer is correct for single-choice questions
            if (isChecked && currentQuestionItem?.typeQuestion == 0) { // Assuming 0 is single choice
                currentQuestionItem?.answers?.forEach { it.isCorrect = false }
            }
            answer.isCorrect = isChecked
            updateViewModel() // Update all answers in case of single choice
            // Re-render answers to reflect single choice logic
            currentQuestionItem?.let { populateUi(it) }
        }

        buttonRemoveAnswer.setOnClickListener {
            currentQuestionItem?.answers?.remove(answer)
            binding.linearLayoutAnswers.removeView(answerView)
            updateViewModel()
        }

        binding.linearLayoutAnswers.addView(answerView)
    }

    private fun openGalleryForImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun updateViewModel() {
        currentQuestionItem?.let {
            viewModel.updateQuestion(it)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // final update before fragment is destroyed
        currentQuestionItem?.questionText = binding.editTextQuestion.text.toString()
        // Collect answers from dynamically added views one last time
        // This is a bit tricky as views are recycled. Better to update model on each interaction.
        updateViewModel()
        _binding = null
    }
}
