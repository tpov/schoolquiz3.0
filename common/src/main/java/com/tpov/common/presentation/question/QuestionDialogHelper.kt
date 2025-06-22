package com.tpov.common.presentation.question

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.tpov.common.presentation.model.PathStructure

/**
 * Helper class for showing QuestionDialogFragment
 */
object QuestionDialogHelper {
    
    /**
     * Shows QuestionDialogFragment with the same parameters as QuestionActivity.newIntent()
     */
    fun showQuestionDialog(
        fragmentManager: FragmentManager,
        pathStructure: PathStructure,
        hardQuestion: Boolean,
        life: Int,
        tag: String = "QuestionDialog"
    ) {
        val dialog = QuestionDialogFragment.newInstance(
            pathStructure = pathStructure,
            hardQuestion = hardQuestion,
            life = life
        )
        dialog.show(fragmentManager, tag)
    }
}

/**
 * Extension function for Fragment to easily show QuestionDialog
 */
fun Fragment.showQuestionDialog(
    pathStructure: PathStructure,
    hardQuestion: Boolean,
    life: Int
) {
    QuestionDialogHelper.showQuestionDialog(
        fragmentManager = parentFragmentManager,
        pathStructure = pathStructure,
        hardQuestion = hardQuestion,
        life = life
    )
} 