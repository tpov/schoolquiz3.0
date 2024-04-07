package com.tpov.schoolquiz.presentation.shop

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.android.billingclient.api.*
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.databinding.ShopFragmentBinding
import com.tpov.schoolquiz.presentation.*
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesGold.COAST_LIFE_GOLD
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesNolics.COAST_LIFE1
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesNolics.COAST_LIFE2
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesNolics.COAST_LIFE3
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesNolics.COAST_LIFE4
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesNolics.COAST_LIFE5
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesNolics.COAST_SIZE_FOR_QUIZ10
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesNolics.COAST_SIZE_FOR_QUIZ2
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesNolics.COAST_SIZE_FOR_QUIZ3
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesNolics.COAST_SIZE_FOR_QUIZ4
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesNolics.COAST_SIZE_FOR_QUIZ5
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesNolics.COAST_SIZE_FOR_QUIZ6
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesNolics.COAST_SIZE_FOR_QUIZ7
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesNolics.COAST_SIZE_FOR_QUIZ8
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesNolics.COAST_SIZE_FOR_QUIZ9
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager.getCountShowAd
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory
import com.tpov.schoolquiz.presentation.fragment.BaseFragment
import com.tpov.schoolquiz.presentation.network.profile.ProfileViewModel
import com.tpov.schoolquiz.presentation.question.log
import com.tpov.schoolquiz.secure.secureCode.getAdUnitId
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Inject

class ShopFragment : BaseFragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory
    private lateinit var billingClient: BillingClient

    @OptIn(InternalCoroutinesApi::class)
    private val component by lazy {
        (requireActivity().application as MainApp).component
    }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable
    private lateinit var binding: ShopFragmentBinding

    @OptIn(InternalCoroutinesApi::class)
    private val profileViewModel: ProfileViewModel by activityViewModels { viewModelFactory }

    private lateinit var mRewardedAd: RewardedAd

    companion object {
        fun newInstance() = ShopFragment()
    }


    @OptIn(InternalCoroutinesApi::class)
    override fun onAttach(context: Context) {
        component.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ShopFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun querySkuDetails() {
        val params = SkuDetailsParams.newBuilder()
            .setSkusList(listOf("donate_1", "donate_5", "donate_20"))
            .setType(BillingClient.SkuType.INAPP)
        billingClient.querySkuDetailsAsync(params.build()) { _, skuDetailsList ->
            log("skuDetailsList $skuDetailsList")
            log("skuDetailsList ${skuDetailsList?.sortedBy { it.priceAmountMicros }}")
            if (skuDetailsList != null) setupDonateButton(skuDetailsList.sortedBy { it.priceAmountMicros })
        }
    }

    private fun setupDonateButton(skuDetailsList: List<SkuDetails>) {
        binding.bDonate.setOnClickListener {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {

                        val params = listOf(
                            "1$" to skuDetailsList[0].description,
                            "5$" to skuDetailsList[1].description,
                            "20$" to skuDetailsList[2].description
                        ).map { "${it.first} -> ${it.second}" }.toTypedArray()

                        val alertDialog = AlertDialog.Builder(requireContext())
                            .setTitle(getString(R.string.shop_fragment_donate_title))
                            .setItems(params) { dialog, which ->
                                val selectedSkuDetails = skuDetailsList[which]
                                val flowParams = BillingFlowParams.newBuilder()
                                    .setSkuDetails(selectedSkuDetails)
                                    .build()
                                billingClient.launchBillingFlow(requireActivity(), flowParams)
                            }
                            .create()

                        alertDialog.show()
                        alertDialog.window?.setBackgroundDrawableResource(R.color.contour)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    Toast.makeText(
                        context,
                        "Error donation ServiceDisconnected :(",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }

    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        var profile = profileViewModel.getProfile()
        var userBalance = profile.pointsNolics ?: 0
        var userBalanceGold = profile.pointsGold ?: 0

        context?.let { MobileAds.initialize(it) {} }

        billingClient = BillingClient.newBuilder(requireContext())
            .setListener { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    Toast.makeText(context, "Thank you!!!", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(context, "Error donation :(", Toast.LENGTH_SHORT).show()
            }
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    querySkuDetails()
                }
            }

            override fun onBillingServiceDisconnected() {
            }
        })

        binding.btnBuyAd.text = "Show ${COUNT_MAX_SHOW_AD - getCountShowAd()}"
        binding.btnBuyAd.setOnClickListener {
            if (getCountShowAd() >= COUNT_MAX_SHOW_AD) {
                Toast.makeText(context, getString(R.string.toast_ad_use_limit), Toast.LENGTH_SHORT)
                    .show()
            } else {
                // Progress dots animation
                var dotCount = 0
                runnable = Runnable {
                    val dots = StringBuilder()
                    for (i in 0..dotCount) {
                        dots.append(".")
                    }
                    binding.btnBuyAd.text = dots.toString()

                    dotCount =
                        (dotCount + 1) % 4                                                   //4 dots on button
                    handler.postDelayed(runnable, 500)
                    if (::mRewardedAd.isInitialized) showAd()
                }
                handler.post(runnable)

                binding.btnBuyAd.alpha = 0.5f
                binding.btnBuyAd.isEnabled = false
                binding.btnBuyAd.isClickable = false

                if (::mRewardedAd.isInitialized) showAd()
                else {
                    Toast.makeText(context, "Load...", Toast.LENGTH_SHORT).show()
                    loadRewardedAd()
                }
            }
        }

        var lifeCost = when (profile.countLife) {
            0 -> COAST_LIFE1
            1 -> COAST_LIFE2
            2 -> COAST_LIFE3
            3 -> COAST_LIFE4
            4 -> COAST_LIFE5
            else -> 5
        }
        binding.bBuyLife.text = "${if (lifeCost == 5) "-" else "$lifeCost nolics"} "

        binding.bBuyLife.setOnClickListener {

            if (profile.countLife!! < COUNT_MAX_LIFE && lifeCost <= userBalance) {
                showDialogWithCallbacks(
                    onConfirm = {
                        userBalance -= lifeCost

                        profile = profile.copy(
                            pointsNolics = userBalance,
                            countLife = profile.countLife?.plus(1),
                            count = profile.count?.plus(COUNT_LIFE_POINTS_IN_LIFE)
                        )

                        profileViewModel.updateProfile(
                            profile
                        )

                        lifeCost = when (profile.countLife) {
                            0 -> COAST_LIFE1
                            1 -> COAST_LIFE2
                            2 -> COAST_LIFE3
                            3 -> COAST_LIFE4
                            4 -> COAST_LIFE5
                            else -> 5
                        }
                        binding.bBuyLife.text = "${if (lifeCost == 5) "-" else "$lifeCost nolics"}"
                    },
                    onCancel = {
                    })

            } else {
                Toast.makeText(
                    context,
                    getString(R.string.toast_ad_not_enough_nolic),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val currentGoldLives = profile.countGold
        binding.bBuyLifeGold.text =
            "${if (currentGoldLives == 1) "-" else "$currentGoldLives golds"}"
        binding.bBuyLifeGold.setOnClickListener {
            if (currentGoldLives != COUNT_MAX_LIFE_GOLD && COAST_LIFE_GOLD <= userBalanceGold) {
                showDialogWithCallbacks(
                    onConfirm = {
                        userBalanceGold -= COAST_LIFE_GOLD

                        profile = profile.copy(
                            pointsGold = userBalance,
                            countGoldLife = profile.countGoldLife?.plus(1),
                            countGold = profile.countGold?.plus(COUNT_LIFE_POINTS_IN_LIFE)
                        )

                        profileViewModel.updateProfile(profile)

                        binding.bBuyLifeGold.text = "-"
                    },
                    onCancel = {
                        // Код, который будет выполнен при отмене
                    })

            } else {
                Toast.makeText(
                    context,
                    getString(R.string.toast_ad_not_enough_gold),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        var placeCost = when (profile.buyQuizPlace) {
            1 -> COAST_SIZE_FOR_QUIZ2
            2 -> COAST_SIZE_FOR_QUIZ3
            3 -> COAST_SIZE_FOR_QUIZ4
            4 -> COAST_SIZE_FOR_QUIZ5
            5 -> COAST_SIZE_FOR_QUIZ6
            6 -> COAST_SIZE_FOR_QUIZ7
            7 -> COAST_SIZE_FOR_QUIZ8
            8 -> COAST_SIZE_FOR_QUIZ9
            9 -> COAST_SIZE_FOR_QUIZ10
            else -> 10
        }

        binding.bPlaceForQuiz.text = "${if (placeCost == 10) "-" else "$placeCost nolics"} "

        binding.bPlaceForQuiz.setOnClickListener {

            if (placeCost <= userBalance && placeCost != 10) {
                showDialogWithCallbacks(
                    onConfirm = {
                        userBalance -= placeCost

                        profile = profile.copy(
                            pointsNolics = userBalance,
                            buyQuizPlace = profile.buyQuizPlace?.plus(1)
                        )
                        profileViewModel.updateProfile(profile)

                        placeCost = when (profile.buyQuizPlace) {
                            1 -> COAST_SIZE_FOR_QUIZ2
                            2 -> COAST_SIZE_FOR_QUIZ3
                            3 -> COAST_SIZE_FOR_QUIZ4
                            4 -> COAST_SIZE_FOR_QUIZ5
                            5 -> COAST_SIZE_FOR_QUIZ6
                            6 -> COAST_SIZE_FOR_QUIZ7
                            7 -> COAST_SIZE_FOR_QUIZ8
                            8 -> COAST_SIZE_FOR_QUIZ9
                            9 -> COAST_SIZE_FOR_QUIZ10
                            else -> 10
                        }

                        binding.bPlaceForQuiz.text =
                            "${if (placeCost == 10) "-" else "$placeCost nolics"} "
                    },
                    onCancel = {
                    }
                )

            } else {
                Toast.makeText(
                    context,
                    getString(R.string.toast_ad_not_enough_nolic), Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showAd() {
        mRewardedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d("AdFragment", "Ad dismissed")
                handler.removeCallbacks(runnable)

                var timeLeft = DELAY_TIMER_SHOW_AD
                runnable = Runnable {
                    binding.btnBuyAd.text = "$timeLeft s"
                    if (timeLeft == 0) {
                        handler.removeCallbacks(runnable)
                        binding.btnBuyAd.isEnabled = true
                        binding.btnBuyAd.isClickable = true
                        binding.btnBuyAd.alpha = 1.0f
                        binding.btnBuyAd.text = "Show ${COUNT_MAX_SHOW_AD - getCountShowAd()}"
                    } else {
                        timeLeft--
                        handler.postDelayed(runnable, 1000)
                    }
                }
                handler.post(runnable)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
            }

            override fun onAdShowedFullScreenContent() {
            }
        }
        mRewardedAd.show(requireActivity()) {
            SharedPreferencesManager.addCountShowAd()
            if (getCountShowAd() == COUNT_MAX_SHOW_AD) addBoxToAccount()
        }
    }

    private fun showDialogWithCallbacks(
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ) {
        val alertDialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.toast_ad_dialog_title))
            .setMessage(getString(R.string.toast_ad_dialog_massage))
            .setPositiveButton(getString(R.string.toast_ad_dialog_positive)) { dialog, _ ->
                onConfirm()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.toast_ad_dialog_negative)) { dialog, _ ->
                onCancel()
                dialog.dismiss()
            }
            .create()

        alertDialog.show()

        alertDialog.window?.setBackgroundDrawable(ColorDrawable(Color.parseColor("#505050")))

        val textView = alertDialog.findViewById<TextView>(android.R.id.message)
        textView?.setTextColor(Color.parseColor("#D1D1D1"))

    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            requireContext(),
            getAdUnitId(),
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    log("error 1 $adError")
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.toast_ad_error_load, adError.message),
                        Toast.LENGTH_SHORT
                    ).show()

                }

                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.toast_ad_loaded),
                        Toast.LENGTH_SHORT
                    ).show()
                    this@ShopFragment.mRewardedAd = rewardedAd
                    log("error 1.1 $rewardedAd")
                }
            })
    }


    @OptIn(InternalCoroutinesApi::class)
    private fun addBoxToAccount() {
        val profile = profileViewModel.getProfile()
        val countBox = profile.countBox
        profileViewModel.updateProfile(profile.copy(countBox = countBox?.plus(1)))
        Toast.makeText(context, getString(R.string.toast_ad_add_box), Toast.LENGTH_SHORT).show()
    }

}

private fun addBoxToAccount() {
    TODO("Not yet implemented")
}

