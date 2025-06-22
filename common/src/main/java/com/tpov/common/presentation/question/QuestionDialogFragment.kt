package com.tpov.common.presentation.question

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import com.tpov.common.R
import com.tpov.common.databinding.ActivityQuestionBinding
import com.tpov.common.presentation.model.PathStructure

/**
 * Simple DialogFragment version of QuestionActivity
 * Just opens the same UI layout as a fullscreen dialog
 */
class QuestionDialogFragment : DialogFragment() {

    private var _binding: ActivityQuestionBinding? = null
    private val binding get() = _binding!!

    private var pathStructure: PathStructure? = null
    private var hardQuiz: Boolean = false
    private var life: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityQuestionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Setup dialog to be fullscreen
        dialog?.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        hideSystemUI()
        setupBasicUI()
        loadArguments()
    }

    private fun hideSystemUI() {
        dialog?.window?.decorView?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
    }

    private fun setupBasicUI() {
        // Show some basic info
        binding.tvQuestionText.text = "Диалог викторины открылся успешно!"
        binding.tvPercent.text = "0"
        binding.tvNumQuestion.text = "1"
        binding.tvLoad.text = "0"
        binding.tvTimer.text = "30 s"
        
        // Show 4 answer buttons
        binding.ll4Answer.visibility = View.VISIBLE
        binding.ll8Answer.visibility = View.GONE
        
        binding.button1.text = "Вариант ответа 1"
        binding.button2.text = "Вариант ответа 2"
        binding.button3.text = "Вариант ответа 3"
        binding.button4.text = "Вариант ответа 4"
        
        // Add click listeners to close dialog
        binding.button1.setOnClickListener { dismiss() }
        binding.button2.setOnClickListener { dismiss() }
        binding.button3.setOnClickListener { dismiss() }
        binding.button4.setOnClickListener { dismiss() }
        binding.bCheat.setOnClickListener { dismiss() }
    }

    private fun loadArguments() {
        arguments?.let { args ->
            pathStructure = args.getParcelable(KEY_PATH_STRUCTURE) as? PathStructure
            hardQuiz = args.getBoolean(KEY_HARD_QUESTION, false)
            life = args.getInt(KEY_LIFE, 0)
            
            // Show the data
            pathStructure?.let { path ->
                binding.tvQuestionText.text = "Викторина: ${path.nameQuiz}\nКатегория: ${path.nameCategory}"
            }
            
            if (hardQuiz) {
                binding.tvPointsGoldLife.text = life.toString()
                binding.llLifeGold.visibility = View.VISIBLE
                binding.llLife.visibility = View.GONE
            } else {
                binding.tvPointsLife.text = life.toString()
                binding.llLife.visibility = View.VISIBLE
                binding.llLifeGold.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val KEY_PATH_STRUCTURE = "path_structure"
        const val KEY_HARD_QUESTION = "hard_question"
        const val KEY_LIFE = "life"

        fun newInstance(
            pathStructure: PathStructure,
            hardQuestion: Boolean,
            life: Int
        ): QuestionDialogFragment {
            return QuestionDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(KEY_PATH_STRUCTURE, pathStructure)
                    putBoolean(KEY_HARD_QUESTION, hardQuestion)
                    putInt(KEY_LIFE, life)
                }
            }
        }
    }
} 