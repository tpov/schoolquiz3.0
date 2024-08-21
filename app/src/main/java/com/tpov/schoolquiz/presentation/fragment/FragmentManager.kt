package com.tpov.schoolquiz.presentation.fragment

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.tpov.schoolquiz.R

object FragmentManager {
    var currentFrag: Fragment? = null

    fun setFragment(newFrag: Fragment, activity: AppCompatActivity) {
        val transaction = activity.supportFragmentManager.beginTransaction()
        transaction.replace(R.id.title_fragment, newFrag)
        transaction.commit()
        currentFrag = newFrag
    }
}
