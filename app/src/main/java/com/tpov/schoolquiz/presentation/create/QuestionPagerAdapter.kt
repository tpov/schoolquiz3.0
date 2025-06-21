package com.tpov.schoolquiz.presentation.create

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.tpov.schoolquiz.presentation.question.QuestionEditFragment // Changed import

class QuestionPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private var questions: List<QuestionItem> = emptyList()

    fun submitList(newQuestions: List<QuestionItem>) {
        questions = newQuestions
        notifyDataSetChanged()
    }

    fun getQuestions(): List<QuestionItem> {
        // This method might need to collect data from fragments if they hold the state
        return questions
    }

    override fun getItemCount(): Int = questions.size

    override fun createFragment(position: Int): Fragment {
        // Use the newly created QuestionEditFragment
        return QuestionEditFragment.newInstance(questions[position].id)
    }

    // We need a way to update the QuestionItem when the user edits it in the fragment
    // This might involve callbacks from the fragment to the adapter/activity/viewModel
    fun updateQuestionItem(position: Int, updatedQuestion: QuestionItem) {
        if (position >= 0 && position < questions.size) {
            val mutableQuestions = questions.toMutableList()
            mutableQuestions[position] = updatedQuestion
            questions = mutableQuestions
            // notifyItemChanged(position) // If you want to be more specific
            // The viewModel should probably handle the state of the questions.
        }
    }
}
