package com.tpov.schoolquiz.presentation.shop

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.tpov.schoolquiz.databinding.ShopFragmentBinding
import com.tpov.schoolquiz.presentation.fragment.BaseFragment

class ShopFragment : BaseFragment() {

    private lateinit var binding: ShopFragmentBinding

    companion object {
        fun newInstance() = ShopFragment()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = ShopFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    private var rewardedAd: RewardedAd? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Инициализация рекламы
        MobileAds.initialize(requireContext()) {}

        loadRewardedAd()

        binding.btnWatchAd.setOnClickListener {
            showRewardedAd()
        }
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(requireContext(), "YOUR_ADMOB_AD_UNIT_ID", adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d("AdFragment", "Ad failed to load: ${adError.message}")
                rewardedAd = null
            }

            override fun onAdLoaded(rewardedAd: RewardedAd) {
                Log.d("AdFragment", "Ad loaded")
                this@ShopFragment.rewardedAd = rewardedAd
            }
        })
    }

    private fun showRewardedAd() {
        rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d("AdFragment", "Ad dismissed")
                loadRewardedAd() // Загрузите новую рекламу после закрытия текущей
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.d("AdFragment", "Ad failed to show: ${adError.message}")
            }

            override fun onAdShowedFullScreenContent() {
                Log.d("AdFragment", "Ad showed")
                rewardedAd = null
            }
        }

        rewardedAd?.show(requireActivity()) { rewardItem ->
            Log.d("AdFragment", "User earned reward: ${rewardItem.amount}")
            // Обновите оставшиеся просмотры или выполните другие действия после получения вознаграждения
        }
    }

}
