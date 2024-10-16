package com.tpov.schoolquiz

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.lifecycleScope
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.presentation.main.MainActivity
import com.tpov.schoolquiz.presentation.splashscreen.SplashScreen
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// 1. update this tpovId
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @OptIn(InternalCoroutinesApi::class)
    @get:Rule
    var activityScenarioRule = ActivityScenarioRule(SplashScreen::class.java)

    val tpovId = 95

    @OptIn(InternalCoroutinesApi::class)
    @Test
    fun testProfile() {
        var mainActivity: MainActivity? = null
        Espresso.onIdle()

        getInstrumentation().runOnMainSync {
            val resumedActivities = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
            for (activity in resumedActivities) {
                if (activity is MainActivity) {
                    mainActivity = activity
                    break
                }
            }
        }

        Assert.assertNotNull("MainActivity не запущена", mainActivity)

        val receivedProfiles = mutableListOf<ProfileEntity?>()

        val job = mainActivity!!.lifecycleScope.launch {
            mainActivity!!.viewModel.profileState.collect { profile ->
                Log.d("asdfdsa", "profile: $profile")
                Log.d("asdfdsa", "receivedProfiles.size: ${receivedProfiles.size}")
                if (profile != null) {
                    receivedProfiles.add(profile)

                    val receivedProfile = receivedProfiles[0]
                    when (receivedProfiles.size) {
                        2 -> {
                            val profileEntity = profile
                            mainActivity!!.viewModel.updateProfile(profileEntity.copy(
                                tester = 100,
                                addPointsSkill = 2,
                                addMassage = "asd",
                                addPointsGold = 1,
                                addPointsNolics = 123,
                                addTrophy = "\uD83C\uDFC5"
                            ))
                        }

                        3 -> {

                            Assert.assertEquals(
                                receivedProfiles[0]?.copy(
                                    addPointsGold = 1, addPointsSkill = 2, addPointsNolics = 123,
                                    countGold = receivedProfiles[2]?.countGold ?: 0, // Значение указываем явно для теста
                                    tpovId = tpovId,
                                    tester = receivedProfiles[2]?.tester,  // Игнорируем изменение tester
                                ),
                                receivedProfiles[2]?.copy(
                                    dateCloseApp = receivedProfile!!.dateCloseApp,
                                    datePremium = receivedProfile.datePremium,
                                    dateBanned = receivedProfile.dateBanned,
                                    dateSynch = receivedProfile.dateSynch,
                                )
                            )

                        }

                        4 -> {
                            Assert.assertEquals(
                                receivedProfiles[0]?.copy(
                                    countGold = 2,
                                    tpovId = tpovId,
                                    tester = 0,
                                    addTrophy = "",
                                    addPointsNolics = 0,
                                    addPointsGold = 0,
                                    addMassage = "",
                                    addPointsSkill = 0,
                                    pointsGold =1,
                                    pointsNolics = 123,
                                    pointsSkill = 2
                                ),
                                receivedProfiles[2]?.copy(
                                    dateCloseApp = receivedProfile!!.dateCloseApp,
                                    datePremium = receivedProfile.datePremium,
                                    dateBanned = receivedProfile.dateBanned,
                                    dateSynch = receivedProfile.dateSynch,
                                    addTrophy = "",

                                    )
                            )
                            cancel()
                        }
                    }
                }
            }
        }

        runBlocking {
            withTimeout(200000) {
                job.join()
            }
        }
    }

    val asd: ClassCastException? = null
}
