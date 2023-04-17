package com.tpov.schoolquiz.presentation.mainactivity.info

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.presentation.fragment.BaseFragment
import com.tpov.schoolquiz.presentation.mainactivity.FragmentMain

class InfoFragment : BaseFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.info_fragment, container, false)
    }
    companion object {

        @JvmStatic
        fun newInstance() = InfoFragment()
    }
}
