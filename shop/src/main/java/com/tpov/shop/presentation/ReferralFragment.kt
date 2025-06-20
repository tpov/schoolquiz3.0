package com.tpov.shop.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tpov.shop.R
import com.tpov.shop.domain.ReferralUser
import java.util.UUID

class ReferralFragment : Fragment() {

    private lateinit var tvPovId: TextView
    private lateinit var btnCopyLink: Button
    private lateinit var btnShareLink: Button
    private lateinit var rvReferredUsers: RecyclerView
    private lateinit var tvRewardText: TextView
    private lateinit var ivRewardIcon: ImageView

    private lateinit var referralAdapter: ReferralAdapter
    private var userTpovId: String = ""

    // Placeholder data
    private val sampleReferralUsers = listOf(
        ReferralUser(UUID.randomUUID().toString(), "Nickname1", 0, 0, 0, "tpov1"),
        ReferralUser(UUID.randomUUID().toString(), "PlayerX", 10, 10, 1, "tpov2"),
        ReferralUser(UUID.randomUUID().toString(), "TopRefer", 150, 100, 15, "tpov3"),
        ReferralUser(UUID.randomUUID().toString(), "UserABC", 50, 50, 5, "tpov4"),
        ReferralUser(UUID.randomUUID().toString(), "Newbie", 0, 0, 0, "tpov5"),
        ReferralUser(UUID.randomUUID().toString(), "ProGamer", 200, 75, 20, "tpov6"),
        ReferralUser(UUID.randomUUID().toString(), "CasualJoe", 25, 25, 2, "tpov7"),
        ReferralUser(UUID.randomUUID().toString(), "LoyalFan", 900, 90, 90, "tpov8")
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_referral, container, false)
        initializeViews(view)
        updateUserPovId() // Get TPOV ID first
        setupRecyclerView()
        setupClickListeners()
        loadReferralData() // Load initial data
        return view
    }

    private fun initializeViews(view: View) {
        tvPovId = view.findViewById(R.id.tv_tpov_id)
        btnCopyLink = view.findViewById(R.id.btn_copy_referral_link)
        btnShareLink = view.findViewById(R.id.btn_share_referral_link)
        rvReferredUsers = view.findViewById(R.id.rv_referred_users)
        tvRewardText = view.findViewById(R.id.tv_reward_text)
        ivRewardIcon = view.findViewById(R.id.iv_reward_icon)
    }

    private fun updateUserPovId() {
        // Fetch TPOV ID from SettingConfigObject in the common module
        userTpovId = com.tpov.common.domain.usecase.SettingConfigObject.settingsConfig.tpovId.toString()
        tvPovId.text = userTpovId
    }

    private fun setupRecyclerView() {
        referralAdapter = ReferralAdapter(requireContext())
        rvReferredUsers.apply {
            adapter = referralAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        }
    }

    private fun setupClickListeners() {
        btnCopyLink.setOnClickListener {
            copyToClipboard(generateReferralLink(userTpovId))
        }

        btnShareLink.setOnClickListener {
            shareReferralLink(generateReferralLink(userTpovId))
        }
    }

    private fun generateReferralLink(tpovId: String): String {
        // Replace with your actual referral link structure
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
        // TODO: Replace with actual data fetching logic
        referralAdapter.submitList(sampleReferralUsers)
        updateRewardStatus(sampleReferralUsers)
    }

    private fun updateRewardStatus(realUsers: List<ReferralUser>) {
        // The condition is simply having 6 referred users.
        // The adapter handles displaying placeholders if there are fewer than 6 real users.
        // So, we check the count of actual referred users provided to the fragment.
        if (realUsers.size >= 6) {
            tvRewardText.text = "Reward received"
            ivRewardIcon.setImageResource(R.drawable.ic_baseline_check_circle_24)
        } else {
            // Default text is now set in XML, but can be reaffirmed here if complex logic arises
            tvRewardText.text = getString(R.string.referral_reward_default_text)
            ivRewardIcon.setImageResource(R.drawable.ic_box)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Any additional setup after view is created
    }

    // It's good practice to have a companion object for fragment instantiation if needed
    companion object {
        fun newInstance() = ReferralFragment()
    }
}
