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
import com.tpov.shop.domain.ReferralUser // Will be created in a later step
import java.util.UUID
import android.util.Log // Added for logging

class ReferralFragment : Fragment() {

    private lateinit var tvPovId: TextView
    private lateinit var btnCopyLink: Button
    private lateinit var btnShareLink: Button
    private lateinit var rvReferredUsers: RecyclerView
    private lateinit var tvRewardText: TextView
    private lateinit var ivRewardIcon: ImageView

    private lateinit var referralAdapter: ReferralAdapter // Will be created/updated
    private var userTpovId: String = ""

    // Updated sample data
    private var sampleReferralUsers: List<ReferralUser> = listOf(
        ReferralUser(UUID.randomUUID().toString(), "UserAlpha", 150, 70, "tpov1"), // allOpenBox >= 100, bonus from season = 0
        ReferralUser(UUID.randomUUID().toString(), "BetaMax", 75, 200, "tpov2"),   // allOpenBox < 100 (progress 75%), bonus from season = 2
        ReferralUser(UUID.randomUUID().toString(), "GammaRay", 20, 50, "tpov3"),    // progress 20%, bonus from season = 0
        ReferralUser(UUID.randomUUID().toString(), "DeltaForce", 100, 0, "tpov4"),   // progress 100%, bonus from season = 0
        ReferralUser(UUID.randomUUID().toString(), "Epsilon", 5, 10, "tpov5"),     // progress 5%, bonus from season = 0
        ReferralUser(UUID.randomUUID().toString(), "ZetaOne", 230, 350, "tpov6")    // progress 100% (from 230%100=30), bonus from season = 3
        // Add more or fewer to test placeholder logic and reward text
        // For example, to test < 6 users:
        // ReferralUser(UUID.randomUUID().toString(), "UserSeven", 50, 50, "tpov7")
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_referral, container, false) // Assumes fragment_referral.xml exists
        initializeViews(view) // Initialize views that are part of the fragment's direct view structure
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Operations that require the view to be fully created and accessible
        updateUserPovId()
        setupRecyclerView()
        setupClickListeners()
        loadReferralData()
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
        referralAdapter = ReferralAdapter(requireContext()) // Instantiate the adapter
        Log.d("ReferralFragment", "rvReferredUsers is null before setting adapter: ${!this::rvReferredUsers.isInitialized || rvReferredUsers == null}")
        // Check if rvReferredUsers is initialized AND not null, which it should be if initializeViews worked.
        if (this::rvReferredUsers.isInitialized && rvReferredUsers != null) {
            rvReferredUsers.apply {
                adapter = referralAdapter
                Log.d("ReferralFragment", "Adapter set on rvReferredUsers")
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            }
        } else {
            Log.e("ReferralFragment", "rvReferredUsers was NOT properly initialized before setting adapter!")
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
        // This will be updated to use new sample data and adapter logic
        if (::referralAdapter.isInitialized) {
            referralAdapter.submitList(sampleReferralUsers)
        }
        updateRewardStatus(sampleReferralUsers)
    }

    private fun updateRewardStatus(realUsers: List<ReferralUser>) {
        // Condition: 6 actual referred users.
        if (realUsers.size >= 6) {
            tvRewardText.text = getString(R.string.referral_reward_received_text) // New string for "Reward received"
            ivRewardIcon.setImageResource(R.drawable.ic_baseline_check_circle_24) // Will be restored
        } else {
            tvRewardText.text = getString(R.string.referral_reward_default_text)
            ivRewardIcon.setImageResource(R.drawable.ic_box) // Will be restored
        }
    }

    companion object {
        fun newInstance() = ReferralFragment()
    }
}
