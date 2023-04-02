package com.tpov.schoolquiz.presentation.fragment

import androidx.appcompat.app.AppCompatActivity
import com.tpov.schoolquiz.R

object FragmentManager {
    var currentFrag: BaseFragment? = null

    fun setFragment(newFrag: BaseFragment, activity: AppCompatActivity) {
        val transaction = activity.supportFragmentManager.beginTransaction()
        transaction.replace(R.id.title_fragment, newFrag)                       //Заменяем пустой фрагмент
        transaction.commit()
        currentFrag = newFrag
    }
}
