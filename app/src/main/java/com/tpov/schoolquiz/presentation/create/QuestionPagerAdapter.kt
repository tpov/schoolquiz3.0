package com.tpov.schoolquiz.presentation.create

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tpov.common.SPLIT_BETWEEN_ANSWERS
import com.tpov.common.data.model.local.QuestionLocal
import com.tpov.common.databinding.ItemQuestionPagerBinding
import java.io.File

class QuestionPagerAdapter(
    private val onImageClickListener: (QuestionLocal, Int) -> Unit,
    private val onQuestionTextChanged: (QuestionLocal, String) -> Unit,
    private val onAnswersChanged: (QuestionLocal, List<String>) -> Unit
) : ListAdapter<QuestionLocal, QuestionPagerAdapter.QuestionViewHolder>(QuestionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestionViewHolder {
        val binding = ItemQuestionPagerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return QuestionViewHolder(binding, onImageClickListener, onQuestionTextChanged, onAnswersChanged)
    }

    override fun onBindViewHolder(holder: QuestionViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    class QuestionViewHolder(
        private val binding: ItemQuestionPagerBinding,
        private val onImageClickListener: (QuestionLocal, Int) -> Unit,
        private val onQuestionTextChanged: (QuestionLocal, String) -> Unit,
        private val onAnswersChanged: (QuestionLocal, List<String>) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentQuestion: QuestionLocal? = null
        private var isUpdating = false
        private var textWatchersSetup = false

        fun bind(question: QuestionLocal, position: Int) {
            currentQuestion = question
            isUpdating = true // Флаг для предотвращения зацикливания

            // Заполняем текст вопроса
            binding.tvQuestionText.setText(question.nameQuestion)

            // Разбиваем ответы и заполняем поля
            val answers = question.nameAnswers.split(SPLIT_BETWEEN_ANSWERS)
            binding.button1.setText(answers.getOrElse(0) { "" })
            binding.button2.setText(answers.getOrElse(1) { "" })
            binding.button3.setText(answers.getOrElse(2) { "" })
            binding.button4.setText(answers.getOrElse(3) { "" })

            isUpdating = false

            // Работа с изображением
            setupImageView(question, position)

            // Настраиваем слушатели только один раз
            if (!textWatchersSetup) {
                setupTextWatchers()
                textWatchersSetup = true
            }
        }

        private fun setupTextWatchers() {
            // TextWatcher для текста вопроса
            binding.tvQuestionText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (!isUpdating && currentQuestion != null) {
                        onQuestionTextChanged(currentQuestion!!, s.toString())
                    }
                }
            })

            // TextWatcher для ответов
            val answerTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (!isUpdating && currentQuestion != null) {
                        updateAnswers()
                    }
                }
            }

            binding.button1.addTextChangedListener(answerTextWatcher)
            binding.button2.addTextChangedListener(answerTextWatcher)
            binding.button3.addTextChangedListener(answerTextWatcher)
            binding.button4.addTextChangedListener(answerTextWatcher)
        }

        private fun updateAnswers() {
            val answers = listOf(
                binding.button1.text.toString(),
                binding.button2.text.toString(),
                binding.button3.text.toString(),
                binding.button4.text.toString()
            )
            currentQuestion?.let { question ->
                onAnswersChanged(question, answers)
            }
        }

        private fun setupImageView(question: QuestionLocal, position: Int) {
            val context = binding.root.context

            // Проверяем есть ли путь к изображению
            if (question.pathPictureQuestion?.isNotBlank() == true) {
                val imageFile = File(context.filesDir, "${question.pathPictureQuestion}")

                if (imageFile.exists()) {
                    // Показываем загруженное изображение
                    binding.imvPhotoQuestion.visibility = View.VISIBLE
                    Glide.with(context)
                        .load(imageFile)
                        .centerCrop()
                        .into(binding.imvPhotoQuestion)
                } else {
                    // Файл не существует, показываем placeholder
                    showPlaceholder()
                }
            } else {
                // Нет пути к изображению, показываем placeholder
                showPlaceholder()
            }

            // Обработчик клика для выбора изображения
            binding.imvPhotoQuestion.setOnClickListener {
                onImageClickListener(question, position)
            }
        }

        private fun showPlaceholder() {
            binding.imvPhotoQuestion.visibility = View.VISIBLE
            binding.imvPhotoQuestion.setImageResource(android.R.drawable.ic_menu_camera)
        }
    }

    private class QuestionDiffCallback : DiffUtil.ItemCallback<QuestionLocal>() {
        override fun areItemsTheSame(oldItem: QuestionLocal, newItem: QuestionLocal): Boolean {
            return oldItem.numQuestion == newItem.numQuestion
        }

        override fun areContentsTheSame(oldItem: QuestionLocal, newItem: QuestionLocal): Boolean {
            return oldItem == newItem
        }
    }
}
