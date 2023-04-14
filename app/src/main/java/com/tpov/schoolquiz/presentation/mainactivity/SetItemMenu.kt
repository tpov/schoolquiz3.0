package com.tpov.schoolquiz.presentation.mainactivity

import android.content.Context
import android.util.Log
import android.view.Menu
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.databinding.ActivityMainBinding
import com.tpov.schoolquiz.presentation.custom.Logcat
import kotlinx.coroutines.InternalCoroutinesApi

object SetItemMenu {
    fun setHomeMenu(binding: ActivityMainBinding, fr2: Int, context: Context) {
        val menu = binding.navigationView.menu
        menu.clear() // Очистите текущее меню

        val menuItemsToAdd = when (fr2) {
            1 -> listOf(
                Pair(R.string.nav_my_quiz, R.drawable.nav_my_quiz),
                Pair(R.string.nav_settings, R.drawable.ic_settings),
                Pair(R.string.nav_downloads, R.drawable.baseline_download_24)
            )
            2 -> listOf(
                Pair(R.string.nav_home, R.drawable.ic_home),
                Pair(R.string.nav_settings, R.drawable.ic_settings),
                Pair(R.string.nav_downloads, R.drawable.baseline_download_24)
            )
            3 -> listOf(
                Pair(R.string.nav_home, R.drawable.ic_home),
                Pair(R.string.nav_my_quiz, R.drawable.nav_my_quiz),
                Pair(R.string.nav_downloads, R.drawable.baseline_download_24)
            )
            4 -> listOf(
                Pair(R.string.nav_home, R.drawable.ic_home),
                Pair(R.string.nav_my_quiz, R.drawable.nav_my_quiz),
                Pair(R.string.nav_settings, R.drawable.ic_settings)
            )
            else -> emptyList()
        }

        for (item in menuItemsToAdd) {
            val menuItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, item.first)
            menuItem.icon = ContextCompat.getDrawable(context, item.second)
        }
    }


    fun setNetworkMenu(binding: ActivityMainBinding, fr2: Int, context: Context) {
        log("fun setNetworkMenu()")
        val menu = binding.navigationView.menu
        menu.clear() // Очистите текущее меню

        val menuItemsToAdd = if (FirebaseAuth.getInstance().currentUser?.uid == null) {
            log("setNetworkMenu() пользователь не зарегестрирован")
            when (fr2) {
                1 -> listOf(
                    Pair(R.string.nav_enter, R.drawable.nav_add_acc)
                )
                2 -> emptyList()
                else -> emptyList()
            }
        } else {
            when (fr2) {
                1 -> {
                    listOf(
                        Pair(R.string.nav_massages, R.drawable.nav_massage),
                        Pair(R.string.nav_chat, R.drawable.nav_chat),
                        Pair(R.string.nav_task, R.drawable.nav_task),
                        Pair(R.string.nav_leaders, R.drawable.nav_leader),
                        Pair(R.string.nav_players, R.drawable.nav_user),
                        Pair(R.string.nav_news, R.drawable.ic_new),
                        Pair(R.string.nav_global, R.drawable.baseline_public_24),
                        Pair(R.string.nav_friends, R.drawable.ic_baseline_drive_folder_upload_24),
                        Pair(R.string.nav_enter, R.drawable.nav_add_acc),
                        Pair(R.string.nav_reports, R.drawable.nav_report),
                        Pair(R.string.nav_exit, R.drawable.nav_exit)
                    )
                }
                2 -> listOf(
                    Pair(R.string.nav_profile, R.drawable.nav_profile),
                    Pair(R.string.nav_chat, R.drawable.nav_chat),
                    Pair(R.string.nav_task, R.drawable.nav_task),
                    Pair(R.string.nav_leaders, R.drawable.nav_leader),
                    Pair(R.string.nav_players, R.drawable.nav_user),
                    Pair(R.string.nav_news, R.drawable.ic_new),
                    Pair(R.string.nav_global, R.drawable.baseline_public_24),
                    Pair(R.string.nav_friends, R.drawable.ic_baseline_drive_folder_upload_24),
                    Pair(R.string.nav_enter, R.drawable.nav_add_acc),
                    Pair(R.string.nav_reports, R.drawable.nav_report),
                    Pair(R.string.nav_exit, R.drawable.nav_exit)
                )
                3 -> listOf(
                    Pair(R.string.nav_profile, R.drawable.nav_profile),
                    Pair(R.string.nav_massages, R.drawable.nav_massage),
                    Pair(R.string.nav_chat, R.drawable.nav_chat),
                    Pair(R.string.nav_leaders, R.drawable.nav_leader),
                    Pair(R.string.nav_players, R.drawable.nav_user),
                    Pair(R.string.nav_news, R.drawable.ic_new),
                    Pair(R.string.nav_global, R.drawable.baseline_public_24),
                    Pair(R.string.nav_friends, R.drawable.ic_baseline_drive_folder_upload_24),
                    Pair(R.string.nav_enter, R.drawable.nav_add_acc),
                    Pair(R.string.nav_reports, R.drawable.nav_report),
                    Pair(R.string.nav_exit, R.drawable.nav_exit)
                )
                4 -> listOf(
                    Pair(R.string.nav_profile, R.drawable. nav_profile),
                    Pair(R.string.nav_massages, R.drawable. nav_massage),
                    Pair(R.string.nav_chat, R.drawable. nav_chat),
                    Pair(R.string.nav_leaders, R.drawable. nav_leader),
                    Pair(R.string.nav_players, R.drawable.nav_user),
                    Pair(R.string.nav_news, R.drawable. ic_new),
                    Pair(R.string.nav_global, R.drawable. baseline_public_24),
                    Pair(R.string.nav_friends, R.drawable. ic_baseline_drive_folder_upload_24),
                    Pair(R.string.nav_enter, R.drawable. nav_add_acc),
                    Pair(R.string.nav_reports, R.drawable.nav_report),
                    Pair(R.string.nav_exit, R.drawable. nav_exit)
                )
                5 -> listOf(
                    Pair(R.string.nav_profile, R.drawable. nav_profile),
                    Pair(R.string.nav_massages, R.drawable. nav_massage),
                    Pair(R.string.nav_chat, R.drawable. nav_chat),
                    Pair(R.string.nav_task, R.drawable. nav_task),
                    Pair(R.string.nav_players, R.drawable.nav_user),
                    Pair(R.string.nav_news, R.drawable. ic_new),
                    Pair(R.string.nav_global, R.drawable. baseline_public_24),
                    Pair(R.string.nav_friends, R.drawable. ic_baseline_drive_folder_upload_24),
                    Pair(R.string.nav_enter, R.drawable. nav_add_acc),
                    Pair(R.string.nav_reports, R.drawable.nav_report),
                    Pair(R.string.nav_exit, R.drawable. nav_exit)
                )
                6 -> listOf(
                    Pair(R.string.nav_profile, R.drawable.nav_profile),
                    Pair(R.string.nav_massages, R.drawable.nav_massage),
                    Pair(R.string.nav_chat, R.drawable.nav_chat),
                    Pair(R.string.nav_task, R.drawable.nav_task),
                    Pair(R.string.nav_leaders, R.drawable.nav_leader),
                    Pair(R.string.nav_news, R.drawable.ic_new),
                    Pair(R.string.nav_global, R.drawable.baseline_public_24),
                    Pair(R.string.nav_friends, R.drawable.ic_baseline_drive_folder_upload_24),
                    Pair(R.string.nav_enter, R.drawable.nav_add_acc),
                    Pair(R.string.nav_reports, R.drawable.nav_report),
                    Pair(R.string.nav_exit, R.drawable.nav_exit)
                )
                7 -> listOf(
                    Pair(R.string.nav_profile, R.drawable.nav_profile),
                    Pair(R.string.nav_massages, R.drawable.nav_massage),
                    Pair(R.string.nav_chat, R.drawable.nav_chat),
                    Pair(R.string.nav_task, R.drawable.nav_task),
                    Pair(R.string.nav_leaders, R.drawable.nav_leader),
                    Pair(R.string.nav_players, R.drawable.nav_user),
                    Pair(R.string.nav_global, R.drawable.baseline_public_24),
                    Pair(R.string.nav_friends, R.drawable.ic_baseline_drive_folder_upload_24),
                    Pair(R.string.nav_enter, R.drawable.nav_add_acc),
                    Pair(R.string.nav_reports, R.drawable.nav_report),
                    Pair(R.string.nav_exit, R.drawable.nav_exit)
                )
                8 -> listOf(
                    Pair(R.string.nav_profile, R.drawable.nav_profile),
                    Pair(R.string.nav_massages, R.drawable.nav_massage),
                    Pair(R.string.nav_chat, R.drawable.nav_chat),
                    Pair(R.string.nav_task, R.drawable.nav_task),
                    Pair(R.string.nav_leaders, R.drawable.nav_leader),
                    Pair(R.string.nav_players, R.drawable.nav_user),
                    Pair(R.string.nav_news, R.drawable.ic_new),
                    Pair(R.string.nav_global, R.drawable.baseline_public_24),
                    Pair(R.string.nav_enter, R.drawable.nav_add_acc),
                    Pair(R.string.nav_reports, R.drawable.nav_report),
                    Pair(R.string.nav_exit, R.drawable.nav_exit)
                )
                9 -> listOf(
                    Pair(R.string.nav_profile, R.drawable.nav_profile),
                    Pair(R.string.nav_massages, R.drawable.nav_massage),
                    Pair(R.string.nav_chat, R.drawable.nav_chat),
                    Pair(R.string.nav_task, R.drawable.nav_task),
                    Pair(R.string.nav_leaders, R.drawable.nav_leader),
                    Pair(R.string.nav_players, R.drawable.nav_user),
                    Pair(R.string.nav_news, R.drawable.ic_new),
                    Pair(R.string.nav_global, R.drawable.baseline_public_24),
                    Pair(R.string.nav_enter, R.drawable.nav_add_acc),
                    Pair(R.string.nav_reports, R.drawable.nav_report),
                    Pair(R.string.nav_exit, R.drawable.nav_exit)
                )
                10 -> listOf(
                    Pair(R.string.nav_profile, R.drawable.nav_profile),
                    Pair(R.string.nav_massages, R.drawable.nav_massage),
                    Pair(R.string.nav_chat, R.drawable.nav_chat),
                    Pair(R.string.nav_task, R.drawable.nav_task),
                    Pair(R.string.nav_leaders, R.drawable.nav_leader),
                    Pair(R.string.nav_players, R.drawable.nav_user),
                    Pair(R.string.nav_news, R.drawable.ic_new),
                    Pair(R.string.nav_global, R.drawable.baseline_public_24),
                    Pair(R.string.nav_friends, R.drawable.ic_baseline_drive_folder_upload_24),
                    Pair(R.string.nav_reports, R.drawable.nav_report),
                    Pair(R.string.nav_exit, R.drawable.nav_exit)
                )
                11 -> listOf(
                    Pair(R.string.nav_profile, R.drawable.nav_profile),
                    Pair(R.string.nav_massages, R.drawable.nav_massage),
                    Pair(R.string.nav_chat, R.drawable.nav_chat),
                    Pair(R.string.nav_task, R.drawable.nav_task),
                    Pair(R.string.nav_leaders, R.drawable.nav_leader),
                    Pair(R.string.nav_players, R.drawable.nav_user),
                    Pair(R.string.nav_news, R.drawable.ic_new),
                    Pair(R.string.nav_global, R.drawable.baseline_public_24),
                    Pair(R.string.nav_friends, R.drawable.ic_baseline_drive_folder_upload_24),
                    Pair(R.string.nav_enter, R.drawable.nav_add_acc),
                    Pair(R.string.nav_exit, R.drawable.nav_exit)
                )
                12 -> listOf(
                    Pair(R.string.nav_profile, R.drawable.nav_profile),
                    Pair(R.string.nav_massages, R.drawable.nav_massage),
                    Pair(R.string.nav_chat, R.drawable.nav_chat),
                    Pair(R.string.nav_task, R.drawable.nav_task),
                    Pair(R.string.nav_leaders, R.drawable.nav_leader),
                    Pair(R.string.nav_players, R.drawable.nav_user),
                    Pair(R.string.nav_news, R.drawable.ic_new),
                    Pair(R.string.nav_global, R.drawable.baseline_public_24),
                    Pair(R.string.nav_friends, R.drawable.ic_baseline_drive_folder_upload_24),
                    Pair(R.string.nav_enter, R.drawable.nav_add_acc),
                    Pair(R.string.nav_reports, R.drawable.nav_report)
                )

                else -> emptyList()
            }
        }

        for (item in menuItemsToAdd) {
            val menuItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, item.first)
            menuItem.icon = ContextCompat.getDrawable(context, item.second)
        }
    }


    @OptIn(InternalCoroutinesApi::class)
    fun log(massage: String) { Logcat.log(massage, "SetItemMenu", Logcat.LOG_ACTIVITY) }
}