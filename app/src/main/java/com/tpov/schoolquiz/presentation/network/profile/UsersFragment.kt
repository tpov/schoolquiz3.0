package com.tpov.schoolquiz.presentation.network.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.presentation.fragment.BaseFragment
import com.tpov.schoolquiz.presentation.main.MainActivityViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UsersFragment : BaseFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var profileAdapter: ProfileAdapter
    @OptIn(InternalCoroutinesApi::class)
    private lateinit var viewModel: MainActivityViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.users_fragment, container, false)
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.recycler_view)
        viewModel = ViewModelProvider(requireActivity())[MainActivityViewModel::class.java]

        MainScope().launch {
            withContext(Dispatchers.IO) {
                profileAdapter = ProfileAdapter(viewModel.getAllProfiles(), viewModel.getPlayers())
                withContext(Dispatchers.Main) {
                    recyclerView.apply {
                        layoutManager = LinearLayoutManager(requireContext())
                        adapter = profileAdapter
                    }
                }
            }
        }
    }

    companion object {
        private const val ARG_PROFILES = "profiles"

        @JvmStatic
        fun newInstance() = UsersFragment()
    }
}