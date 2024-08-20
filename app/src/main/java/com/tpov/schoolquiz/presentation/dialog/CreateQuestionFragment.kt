package com.tpov.schoolquiz.presentation.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.tpov.schoolquiz.databinding.FragmentCreateQuizBinding

class CreateQuestionFragment : Fragment() {

    private var _binding: FragmentCreateQuizBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentCreateQuizBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Теперь вы можете использовать элементы интерфейса через binding
        // Например:
        // binding.textView.text = "Hello, World!"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}