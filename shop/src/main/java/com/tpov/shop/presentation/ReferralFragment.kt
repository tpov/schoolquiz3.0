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
    private var userTpovId: String = "testPovId123" // Placeholder

    // Placeholder data
    private val sampleReferralUsers = listOf(
        ReferralUser(UUID.randomUUID().toString(), "User 1", 0, 0, "tpov1"),
        ReferralUser(UUID.randomUUID().toString(), "User 2", 10, 1, "tpov2"),
        ReferralUser(UUID.randomUUID().toString(), "User 3", 100, 10, "tpov3"),
        ReferralUser(UUID.randomUUID().toString(), "User 4", 50, 5, "tpov4"),
        ReferralUser(UUID.randomUUID().toString(), "User 5", 0, 0, "tpov5"),
        ReferralUser(UUID.randomUUID().toString(), "User 6", 75, 7, "tpov6"),
        ReferralUser(UUID.randomUUID().toString(), "User 7", 25, 2, "tpov7"),
        ReferralUser(UUID.randomUUID().toString(), "User 8", 90, 9, "tpov8")
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_referral, container, false)
        initializeViews(view)
        setupRecyclerView()
        setupClickListeners()
        loadReferralData() // Load initial data
        updateUserPovId()
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
        // In a real app, this would be fetched from user data
        userTpovId = "user" + UUID.randomUUID().toString().substring(0, 8)
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

    private fun updateRewardStatus(users: List<ReferralUser>) {
        val qualifiedRecruiters = users.count { it.allOpenBox > 0 }

        if (qualifiedRecruiters >= 6) {
            tvRewardText.text = "Reward received"
            ivRewardIcon.setImageResource(R.drawable.ic_baseline_check_circle_24) // Assuming you have a check icon
            // You might want to change text color or style as well
        } else {
            tvRewardText.text = "Reward for 6 recruiters x30" // Default text from layout
            ivRewardIcon.setImageResource(R.drawable.ic_box) // Default icon (already set in XML but good to be explicit)
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
