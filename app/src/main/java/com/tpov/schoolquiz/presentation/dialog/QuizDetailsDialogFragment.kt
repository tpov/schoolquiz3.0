package com.tpov.schoolquiz.presentation.dialog

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.databinding.FragmentQuizDetailsDialogBinding
import com.tpov.schoolquiz.presentation.create.SimpleCreateQuizViewModel

class QuizDetailsDialogFragment : DialogFragment() {

    private var _binding: FragmentQuizDetailsDialogBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SimpleCreateQuizViewModel // Shared with SimpleCreateQuizActivity
    private var quizImageDrawable: BitmapDrawable? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri: Uri? = result.data?.data
            imageUri?.let {
                Glide.with(this)
                    .load(it)
                    .into(binding.imageViewQuizCover)
                try {
                    val bitmap = MediaStore.Images.Media.getBitmap(requireActivity().contentResolver, it)
                    quizImageDrawable = BitmapDrawable(resources, bitmap)
                } catch (e: Exception) {
                    // Handle exception converting URI to BitmapDrawable
                    e.printStackTrace()
                }
            }
        }
    }

    companion object {
        fun newInstance(): QuizDetailsDialogFragment {
            return QuizDetailsDialogFragment()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = FragmentQuizDetailsDialogBinding.inflate(LayoutInflater.from(context))
        viewModel = ViewModelProvider(requireActivity())[SimpleCreateQuizViewModel::class.java]

        binding.imageViewQuizCover.setOnClickListener {
            openGalleryForImage()
        }

        val dialog = AlertDialog.Builder(requireActivity())
            .setTitle("Детали квиза")
            .setView(binding.root)
            .setPositiveButton("Сохранить") { _, _ ->
                val quizName = binding.editTextQuizName.text.toString()
                // Pass data back to the activity/viewModel
                // For simplicity, directly calling ViewModel here, but could use callback/listener
                viewModel.saveQuiz(quizName, quizImageDrawable, viewModel.questions.value ?: emptyList())
                viewModel.resetQuizDetailsDialogTrigger()
            }
            .setNegativeButton("Отмена") { _, _ ->
                viewModel.resetQuizDetailsDialogTrigger()
                dismiss()
            }
            .create()

        return dialog
    }

    private fun openGalleryForImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Avoid memory leaks
    }
}
