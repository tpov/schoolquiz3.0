package com.tpov.common.presentation

import androidx.fragment.app.Fragment
import com.tpov.common.presentation.model.PathStructure

interface NavigationProvider {
    fun openQuestionActivity(pathStructure: PathStructure, typeQuestion: Boolean)
    fun navigateTo(fragment: Fragment, addToBackStack: Boolean = true, replace: Boolean = true)
}