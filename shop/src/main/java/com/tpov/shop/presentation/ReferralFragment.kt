package com.tpov.shop.presentation

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tpov.shop.R
import com.tpov.shop.domain.ReferralUser
import java.util.UUID

class ReferralFragment : Fragment() {

    private lateinit var tvPovId: TextView
    private lateinit var btnCopyLink: ImageButton
    private lateinit var btnShareLink: ImageButton
    private lateinit var rvReferredUsers: RecyclerView
    private lateinit var tvRewardText: TextView
    private lateinit var ivRewardIcon: ImageView
    private lateinit var tvGettingGetNewBoxCount: TextView

    private lateinit var referralAdapter: ReferralAdapter
    private var userTpovId: String = ""
    private var username: String = "User177" // Default username

    // Updated sample data
    private var sampleReferralUsers: List<ReferralUser> = listOf(
        ReferralUser(UUID.randomUUID().toString(), "TPOV", 150, 70, "tpov1"), // allOpenBox >= 100, bonus from season = 0
        ReferralUser(UUID.randomUUID().toString(), "ArtemON_GO", 75, 200, "tpov2"),   // allOpenBox < 100 (progress 75%), bonus from season = 2
        ReferralUser(UUID.randomUUID().toString(), "test", 20, 50, "tpov3"),    // progress 20%, bonus from season = 0
        ReferralUser(UUID.randomUUID().toString(), "Alina", 100, 0, "tpov4"),   // progress 100%, bonus from season = 0
        ReferralUser(UUID.randomUUID().toString(), "CalcOriginal", 5, 10, "tpov5"),     // progress 5%, bonus from season = 0
        // Add more or fewer to test placeholder logic and reward text
        // For example, to test < 6 users:
        // ReferralUser(UUID.randomUUID().toString(), "UserSeven", 50, 50, "tpov7")
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_referral, container, false)
        initializeViews(view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateUserInfo()
        setupRecyclerView()
        setupClickListeners()
        loadReferralData()
    }

    private fun initializeViews(view: View) {
        tvPovId = view.findViewById(R.id.tv_tpov_id)
        btnCopyLink = view.findViewById(R.id.imv_b_copy_referral_link)
        btnShareLink = view.findViewById(R.id.imv_b_share_referral_link)
        rvReferredUsers = view.findViewById(R.id.rv_referred_users)
        tvRewardText = view.findViewById(R.id.tv_reward_text)
        ivRewardIcon = view.findViewById(R.id.iv_reward_icon)
        tvGettingGetNewBoxCount = view.findViewById(R.id.tv_getting_new_box_count)
    }

    @SuppressLint("SetTextI18n")
    private fun updateUserInfo() {
        // Fetch TPOV ID from SettingConfigObject in the common module
        userTpovId = com.tpov.common.domain.usecase.SettingConfigObject.settingsConfig.tpovId.toString()
        
        // Get username if available - could be from user settings or another source
        username = getUsernameFromSettings() ?: "User$userTpovId"
        
        // Update UI
        tvPovId.text = "tpovId: $userTpovId"
    }
    
    private fun getUsernameFromSettings(): String? {
        // Implementation depending on your app's architecture
        // For now, we'll use a placeholder
        return null
    }

    private fun setupRecyclerView() {
        referralAdapter = ReferralAdapter(requireContext())
        rvReferredUsers.apply {
            adapter = referralAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        }
    }

    private fun setupClickListeners() {
        btnCopyLink.setOnClickListener {
            copyToClipboard(generateReferralLink(userTpovId))
        }

        btnShareLink.setOnClickListener {
            shareReferralLink(generateReferralLink(userTpovId))
        }
        
        // Add back button click listener
        view?.findViewById<ImageButton>(R.id.btn_back)?.setOnClickListener {
            activity?.finish()
        }
    }

    private fun generateReferralLink(tpovId: String): String {
        return "https://yourapp.example.com/referral?id=$tpovId"
    }

    private fun copyToClipboard(text: String) {
        val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("ReferralLink", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Referral link copied!", Toast.LENGTH_SHORT).show()
    }

    private fun shareReferralLink(link: String) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Join me using my referral link: $link")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }

    private fun loadReferralData() {
        if (::referralAdapter.isInitialized) {
            referralAdapter.submitList(sampleReferralUsers)
        }
        updateRewardStatus(sampleReferralUsers)
        val sum = sampleReferralUsers
            .map { it.seasonBoxCount / 100 }
            .sum()

        tvGettingGetNewBoxCount.text = sum.toString()
    }

    private fun updateRewardStatus(realUsers: List<ReferralUser>) {
        if (realUsers.filter { it.allOpenBox >= 100 }.size >= 6) {
            tvRewardText.text = getString(R.string.referral_reward_received_text)
            ivRewardIcon.setImageResource(R.drawable.ic_save)
        } else {
            tvRewardText.text = getString(R.string.referral_reward_default_text)
            ivRewardIcon.setImageResource(R.drawable.ic_box)
        }
    }

    companion object {
        fun newInstance() = ReferralFragment()
    }
}
