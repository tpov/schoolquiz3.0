package com.tpov.userguide

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

internal class InfoFragment(private val guideItems: List<GuideItem>) : Fragment() {

    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_info, container, false)

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        guideItems?.let {
            val adapter = InfoAdapter(it)
            recyclerView.adapter = adapter
        }

        return view
    }

    companion object {
        fun newInstance(guideItems: List<GuideItem>): InfoFragment {
            return InfoFragment(guideItems)
        }
    }

}
