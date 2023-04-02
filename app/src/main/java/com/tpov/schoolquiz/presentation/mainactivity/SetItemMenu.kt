package com.tpov.schoolquiz.presentation.mainactivity

import android.util.Log
import android.view.Menu
import com.google.firebase.auth.FirebaseAuth
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.databinding.ActivityMainBinding
import kotlinx.coroutines.InternalCoroutinesApi

object SetItemMenu {

    fun setHomeMenu(binding: ActivityMainBinding, fr2: Int) {
        log(" fun setHomeMenu()")
        binding.navigationView.menu.removeItem(R.id.nav_home)
        binding.navigationView.menu.removeItem(R.id.nav_enter)
        binding.navigationView.menu.removeItem(R.id.nav_exit)
        binding.navigationView.menu.removeItem(R.id.nav_task)
        binding.navigationView.menu.removeItem(R.id.nav_profile)
        binding.navigationView.menu.removeItem(R.id.nav_chat)
        binding.navigationView.menu.removeItem(R.id.nav_players)
        binding.navigationView.menu.removeItem(R.id.nav_massages)
        binding.navigationView.menu.removeItem(R.id.nav_friends)
        binding.navigationView.menu.removeItem(R.id.nav_leaders)
        binding.navigationView.menu.removeItem(R.id.nav_news)
        binding.navigationView.menu.removeItem(R.id.nav_global)
        binding.navigationView.menu.removeItem(R.id.nav_my_quiz)
        binding.navigationView.menu.removeItem(R.id.nav_settings)
        binding.navigationView.menu.removeItem(R.id.nav_downloads)
        binding.navigationView.menu.removeItem(R.id.nav_reports)

        when (fr2) {
            1 -> {
                log("setHomeMenu() номер: $fr2")
                binding.navigationView.menu.add(Menu.NONE, R.id.nav_my_quiz, Menu.NONE, R.string.nav_my_quiz).setIcon(
                    R.drawable. nav_my_quiz)
                binding.navigationView.menu.add(Menu.NONE, R.id.nav_settings, Menu.NONE, R.string.nav_settings).setIcon(
                    R.drawable. ic_settings)
                binding.navigationView.menu.add(Menu.NONE, R.id.nav_downloads, Menu.NONE, R.string.nav_downloads).setIcon(
                    R.drawable. baseline_download_24)
            }
            2 -> {
                log("setHomeMenu() номер: $fr2")
                binding.navigationView.menu.add(Menu.NONE, R.id.nav_home, Menu.NONE, R.string.nav_home).setIcon(
                    R.drawable. ic_home)
                binding.navigationView.menu.add(Menu.NONE, R.id.nav_settings, Menu.NONE, R.string.nav_settings).setIcon(
                    R.drawable. ic_settings)
                binding.navigationView.menu.add(Menu.NONE, R.id.nav_downloads, Menu.NONE, R.string.nav_downloads).setIcon(
                    R.drawable. baseline_download_24)
            }
            3 -> {
                log("setHomeMenu() номер: $fr2")
                binding.navigationView.menu.add(Menu.NONE, R.id.nav_home, Menu.NONE, R.string.nav_home).setIcon(
                    R.drawable. ic_home)
                binding.navigationView.menu.add(Menu.NONE, R.id.nav_my_quiz, Menu.NONE, R.string.nav_my_quiz).setIcon(
                    R.drawable. nav_my_quiz)
                binding.navigationView.menu.add(Menu.NONE, R.id.nav_downloads, Menu.NONE, R.string.nav_downloads).setIcon(
                    R.drawable. baseline_download_24)
            }
            4 -> {
                log("setHomeMenu() номер: $fr2")
                binding.navigationView.menu.add(Menu.NONE, R.id.nav_home, Menu.NONE, R.string.nav_home).setIcon(
                    R.drawable. ic_home)
                binding.navigationView.menu.add(Menu.NONE, R.id.nav_my_quiz, Menu.NONE, R.string.nav_my_quiz).setIcon(
                    R.drawable. nav_my_quiz)
                binding.navigationView.menu.add(Menu.NONE, R.id.nav_settings, Menu.NONE, R.string.nav_settings).setIcon(
                    R.drawable. ic_settings)
            }
        }
    }

    fun setNetworkMenu(binding: ActivityMainBinding, fr2: Int) {
        log("fun setNetworkMenu()")
        binding.navigationView.menu.removeItem(R.id.nav_home)
        binding.navigationView.menu.removeItem(R.id.nav_enter)
        binding.navigationView.menu.removeItem(R.id.nav_exit)
        binding.navigationView.menu.removeItem(R.id.nav_task)
        binding.navigationView.menu.removeItem(R.id.nav_profile)
        binding.navigationView.menu.removeItem(R.id.nav_chat)
        binding.navigationView.menu.removeItem(R.id.nav_players)
        binding.navigationView.menu.removeItem(R.id.nav_massages)
        binding.navigationView.menu.removeItem(R.id.nav_friends)
        binding.navigationView.menu.removeItem(R.id.nav_leaders)
        binding.navigationView.menu.removeItem(R.id.nav_news)
        binding.navigationView.menu.removeItem(R.id.nav_global)
        binding.navigationView.menu.removeItem(R.id.nav_my_quiz)
        binding.navigationView.menu.removeItem(R.id.nav_settings)
        binding.navigationView.menu.removeItem(R.id.nav_downloads)
        if (FirebaseAuth.getInstance().currentUser?.uid == null) {
            log("setNetworkMenu() пользователь не зарегестрирован")
            when (fr2) {
                1 -> {
                    log("setNetworkMenu() номер: $fr2")
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_enter, Menu.NONE, R.string.nav_enter).setIcon(
                        R.drawable. nav_add_acc)
                }

                2 -> {
                    log("setNetworkMenu() номер: $fr2")

                }
            }
        } else {
            log("setNetworkMenu() пользователь зарегестрирован")
            when (fr2) {
                1 -> {
                    log("setNetworkMenu() номер: $fr2")
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_massages, Menu.NONE, R.string.nav_massages).setIcon(
                        R.drawable. nav_massage)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_chat, Menu.NONE, R.string.nav_chat).setIcon(
                        R.drawable. nav_chat)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_task, Menu.NONE, R.string.nav_task).setIcon(
                        R.drawable. nav_task)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_leaders, Menu.NONE, R.string.nav_leaders).setIcon(
                        R.drawable. nav_leader)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_players, Menu.NONE, R.string.nav_players).setIcon(
                        R.drawable.nav_user)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_news, Menu.NONE, R.string.nav_news).setIcon(
                        R.drawable. ic_new)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_global, Menu.NONE, R.string.nav_global).setIcon(
                        R.drawable. baseline_public_24)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_friends, Menu.NONE, R.string.nav_friends).setIcon(
                        R.drawable. ic_baseline_drive_folder_upload_24)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_enter, Menu.NONE, R.string.nav_enter).setIcon(
                        R.drawable. nav_add_acc)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_reports, Menu.NONE, R.string.nav_reports).setIcon(
                        R.drawable.nav_report)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_exit, Menu.NONE, R.string.nav_exit).setIcon(
                        R.drawable. nav_exit)
                }

                2 -> {
                    log("setNetworkMenu() номер: $fr2")
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_profile, Menu.NONE, R.string.nav_profile).setIcon(
                        R.drawable. nav_profile)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_chat, Menu.NONE, R.string.nav_chat).setIcon(
                        R.drawable. nav_chat)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_task, Menu.NONE, R.string.nav_task).setIcon(
                        R.drawable. nav_task)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_leaders, Menu.NONE, R.string.nav_leaders).setIcon(
                        R.drawable. nav_leader)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_players, Menu.NONE, R.string.nav_players).setIcon(
                        R.drawable.nav_user)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_news, Menu.NONE, R.string.nav_news).setIcon(
                        R.drawable. ic_new)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_global, Menu.NONE, R.string.nav_global).setIcon(
                        R.drawable. baseline_public_24)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_friends, Menu.NONE, R.string.nav_friends).setIcon(
                        R.drawable. ic_baseline_drive_folder_upload_24)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_enter, Menu.NONE, R.string.nav_enter).setIcon(
                        R.drawable. nav_add_acc)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_reports, Menu.NONE, R.string.nav_reports).setIcon(
                        R.drawable.nav_report)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_exit, Menu.NONE, R.string.nav_exit).setIcon(
                        R.drawable. nav_exit)
                }
                3 -> {
                    log("setNetworkMenu() номер: $fr2")
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_profile, Menu.NONE, R.string.nav_profile).setIcon(
                        R.drawable. nav_profile)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_massages, Menu.NONE, R.string.nav_massages).setIcon(
                        R.drawable. nav_massage)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_task, Menu.NONE, R.string.nav_task).setIcon(
                        R.drawable. nav_task)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_leaders, Menu.NONE, R.string.nav_leaders).setIcon(
                        R.drawable. nav_leader)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_players, Menu.NONE, R.string.nav_players).setIcon(
                        R.drawable.nav_user)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_news, Menu.NONE, R.string.nav_news).setIcon(
                        R.drawable. ic_new)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_global, Menu.NONE, R.string.nav_global).setIcon(
                        R.drawable. baseline_public_24)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_friends, Menu.NONE, R.string.nav_friends).setIcon(
                        R.drawable. ic_baseline_drive_folder_upload_24)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_enter, Menu.NONE, R.string.nav_enter).setIcon(
                        R.drawable. nav_add_acc)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_reports, Menu.NONE, R.string.nav_reports).setIcon(
                        R.drawable.nav_report)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_exit, Menu.NONE, R.string.nav_exit).setIcon(
                        R.drawable. nav_exit)
                }
                4 -> {
                    log("setNetworkMenu() номер: $fr2")
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_profile, Menu.NONE, R.string.nav_profile).setIcon(
                        R.drawable. nav_profile)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_massages, Menu.NONE, R.string.nav_massages).setIcon(
                        R.drawable. nav_massage)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_chat, Menu.NONE, R.string.nav_chat).setIcon(
                        R.drawable. nav_chat)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_leaders, Menu.NONE, R.string.nav_leaders).setIcon(
                        R.drawable. nav_leader)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_players, Menu.NONE, R.string.nav_players).setIcon(
                        R.drawable.nav_user)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_news, Menu.NONE, R.string.nav_news).setIcon(
                        R.drawable. ic_new)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_global, Menu.NONE, R.string.nav_global).setIcon(
                        R.drawable. baseline_public_24)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_friends, Menu.NONE, R.string.nav_friends).setIcon(
                        R.drawable. ic_baseline_drive_folder_upload_24)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_enter, Menu.NONE, R.string.nav_enter).setIcon(
                        R.drawable. nav_add_acc)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_reports, Menu.NONE, R.string.nav_reports).setIcon(
                        R.drawable.nav_report)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_exit, Menu.NONE, R.string.nav_exit).setIcon(
                        R.drawable. nav_exit)
                }
                5 -> {
                    log("setNetworkMenu() номер: $fr2")
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_profile, Menu.NONE, R.string.nav_profile).setIcon(
                        R.drawable. nav_profile)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_massages, Menu.NONE, R.string.nav_massages).setIcon(
                        R.drawable. nav_massage)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_chat, Menu.NONE, R.string.nav_chat).setIcon(
                        R.drawable. nav_chat)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_task, Menu.NONE, R.string.nav_task).setIcon(
                        R.drawable. nav_task)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_players, Menu.NONE, R.string.nav_players).setIcon(
                        R.drawable.nav_user)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_news, Menu.NONE, R.string.nav_news).setIcon(
                        R.drawable. ic_new)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_global, Menu.NONE, R.string.nav_global).setIcon(
                        R.drawable. baseline_public_24)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_friends, Menu.NONE, R.string.nav_friends).setIcon(
                        R.drawable. ic_baseline_drive_folder_upload_24)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_enter, Menu.NONE, R.string.nav_enter).setIcon(
                        R.drawable. nav_add_acc)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_reports, Menu.NONE, R.string.nav_reports).setIcon(
                        R.drawable.nav_report)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_exit, Menu.NONE, R.string.nav_exit).setIcon(
                        R.drawable. nav_exit)
                }
                6 -> {
                    log("setNetworkMenu() номер: $fr2")
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_profile, Menu.NONE, R.string.nav_profile).setIcon(
                        R.drawable. nav_profile)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_massages, Menu.NONE, R.string.nav_massages).setIcon(
                        R.drawable. nav_massage)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_chat, Menu.NONE, R.string.nav_chat).setIcon(
                        R.drawable. nav_chat)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_task, Menu.NONE, R.string.nav_task).setIcon(
                        R.drawable. nav_task)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_leaders, Menu.NONE, R.string.nav_leaders).setIcon(
                        R.drawable. nav_leader)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_news, Menu.NONE, R.string.nav_news).setIcon(
                        R.drawable. ic_new)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_global, Menu.NONE, R.string.nav_global).setIcon(
                        R.drawable. baseline_public_24)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_friends, Menu.NONE, R.string.nav_friends).setIcon(
                        R.drawable. ic_baseline_drive_folder_upload_24)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_enter, Menu.NONE, R.string.nav_enter).setIcon(
                        R.drawable. nav_add_acc)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_reports, Menu.NONE, R.string.nav_reports).setIcon(
                        R.drawable.nav_report)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_exit, Menu.NONE, R.string.nav_exit).setIcon(
                        R.drawable. nav_exit)
                }
                7 -> {
                    log("setNetworkMenu() номер: $fr2")
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_profile, Menu.NONE, R.string.nav_profile).setIcon(
                        R.drawable. nav_profile)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_massages, Menu.NONE, R.string.nav_massages).setIcon(
                        R.drawable. nav_massage)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_chat, Menu.NONE, R.string.nav_chat).setIcon(
                        R.drawable. nav_chat)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_task, Menu.NONE, R.string.nav_task).setIcon(
                        R.drawable. nav_task)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_leaders, Menu.NONE, R.string.nav_leaders).setIcon(
                        R.drawable. nav_leader)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_players, Menu.NONE, R.string.nav_players).setIcon(
                        R.drawable.nav_user)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_global, Menu.NONE, R.string.nav_global).setIcon(
                        R.drawable. baseline_public_24)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_friends, Menu.NONE, R.string.nav_friends).setIcon(
                        R.drawable. ic_baseline_drive_folder_upload_24)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_enter, Menu.NONE, R.string.nav_enter).setIcon(
                        R.drawable. nav_add_acc)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_reports, Menu.NONE, R.string.nav_reports).setIcon(
                        R.drawable.nav_report)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_exit, Menu.NONE, R.string.nav_exit).setIcon(
                        R.drawable. nav_exit)
                }
                8 -> {
                    log("setNetworkMenu() номер: $fr2")
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_profile, Menu.NONE, R.string.nav_profile).setIcon(
                        R.drawable. nav_profile)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_massages, Menu.NONE, R.string.nav_massages).setIcon(
                        R.drawable. nav_massage)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_chat, Menu.NONE, R.string.nav_chat).setIcon(
                        R.drawable. nav_chat)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_task, Menu.NONE, R.string.nav_task).setIcon(
                        R.drawable. nav_task)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_leaders, Menu.NONE, R.string.nav_leaders).setIcon(
                        R.drawable. nav_leader)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_players, Menu.NONE, R.string.nav_players).setIcon(
                        R.drawable.nav_user)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_news, Menu.NONE, R.string.nav_news).setIcon(
                        R.drawable. ic_new)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_friends, Menu.NONE, R.string.nav_friends).setIcon(
                        R.drawable. ic_baseline_drive_folder_upload_24)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_enter, Menu.NONE, R.string.nav_enter).setIcon(
                        R.drawable. nav_add_acc)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_reports, Menu.NONE, R.string.nav_reports).setIcon(
                        R.drawable.nav_report)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_exit, Menu.NONE, R.string.nav_exit).setIcon(
                        R.drawable. nav_exit)
                }
                9 -> {
                    log("setNetworkMenu() номер: $fr2")
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_profile, Menu.NONE, R.string.nav_profile).setIcon(
                        R.drawable. nav_profile)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_massages, Menu.NONE, R.string.nav_massages).setIcon(
                        R.drawable. nav_massage)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_chat, Menu.NONE, R.string.nav_chat).setIcon(
                        R.drawable. nav_chat)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_task, Menu.NONE, R.string.nav_task).setIcon(
                        R.drawable. nav_task)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_leaders, Menu.NONE, R.string.nav_leaders).setIcon(
                        R.drawable. nav_leader)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_players, Menu.NONE, R.string.nav_players).setIcon(
                        R.drawable.nav_user)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_news, Menu.NONE, R.string.nav_news).setIcon(
                        R.drawable. ic_new)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_global, Menu.NONE, R.string.nav_global).setIcon(
                        R.drawable. baseline_public_24)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_enter, Menu.NONE, R.string.nav_enter).setIcon(
                        R.drawable. nav_add_acc)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_reports, Menu.NONE, R.string.nav_reports).setIcon(
                        R.drawable.nav_report)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_exit, Menu.NONE, R.string.nav_exit).setIcon(
                        R.drawable. nav_exit)
                }
                10 -> {
                    log("setNetworkMenu() номер: $fr2")
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_profile, Menu.NONE, R.string.nav_profile).setIcon(
                        R.drawable. nav_profile)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_massages, Menu.NONE, R.string.nav_massages).setIcon(
                        R.drawable. nav_massage)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_chat, Menu.NONE, R.string.nav_chat).setIcon(
                        R.drawable. nav_chat)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_task, Menu.NONE, R.string.nav_task).setIcon(
                        R.drawable. nav_task)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_leaders, Menu.NONE, R.string.nav_leaders).setIcon(
                        R.drawable. nav_leader)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_players, Menu.NONE, R.string.nav_players).setIcon(
                        R.drawable.nav_user)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_news, Menu.NONE, R.string.nav_news).setIcon(
                        R.drawable. ic_new)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_global, Menu.NONE, R.string.nav_global).setIcon(
                        R.drawable. baseline_public_24)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_friends, Menu.NONE, R.string.nav_friends).setIcon(
                        R.drawable. ic_baseline_drive_folder_upload_24)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_reports, Menu.NONE, R.string.nav_reports).setIcon(
                        R.drawable.nav_report)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_exit, Menu.NONE, R.string.nav_exit).setIcon(
                        R.drawable. nav_exit)
                }
                11 -> {
                    log("setNetworkMenu() номер: $fr2")
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_profile, Menu.NONE, R.string.nav_profile).setIcon(
                        R.drawable. nav_profile)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_massages, Menu.NONE, R.string.nav_massages).setIcon(
                        R.drawable. nav_massage)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_chat, Menu.NONE, R.string.nav_chat).setIcon(
                        R.drawable. nav_chat)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_task, Menu.NONE, R.string.nav_task).setIcon(
                        R.drawable. nav_task)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_leaders, Menu.NONE, R.string.nav_leaders).setIcon(
                        R.drawable. nav_leader)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_players, Menu.NONE, R.string.nav_players).setIcon(
                        R.drawable.nav_user)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_news, Menu.NONE, R.string.nav_news).setIcon(
                        R.drawable. ic_new)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_global, Menu.NONE, R.string.nav_global).setIcon(
                        R.drawable. baseline_public_24)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_friends, Menu.NONE, R.string.nav_friends).setIcon(
                        R.drawable. ic_baseline_drive_folder_upload_24)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_enter, Menu.NONE, R.string.nav_enter).setIcon(
                        R.drawable. nav_add_acc)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_exit, Menu.NONE, R.string.nav_exit).setIcon(
                        R.drawable. nav_exit)
                }
                12 -> {
                    log("setNetworkMenu() номер: $fr2")
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_profile, Menu.NONE, R.string.nav_profile).setIcon(
                        R.drawable. nav_profile)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_massages, Menu.NONE, R.string.nav_massages).setIcon(
                        R.drawable. nav_massage)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_chat, Menu.NONE, R.string.nav_chat).setIcon(
                        R.drawable. nav_chat)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_task, Menu.NONE, R.string.nav_task).setIcon(
                        R.drawable. nav_task)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_leaders, Menu.NONE, R.string.nav_leaders).setIcon(
                        R.drawable. nav_leader)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_players, Menu.NONE, R.string.nav_players).setIcon(
                        R.drawable.nav_user)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_news, Menu.NONE, R.string.nav_news).setIcon(
                        R.drawable. ic_new)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_global, Menu.NONE, R.string.nav_global).setIcon(
                        R.drawable. baseline_public_24)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_friends, Menu.NONE, R.string.nav_friends).setIcon(
                        R.drawable. ic_baseline_drive_folder_upload_24)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_enter, Menu.NONE, R.string.nav_enter).setIcon(
                        R.drawable. nav_add_acc)
                    binding.navigationView.menu.add(Menu.NONE, R.id.nav_reports, Menu.NONE, R.string.nav_reports).setIcon(
                        R.drawable.nav_report)

                }
            }
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    fun log(massage: String) { MainActivity.log(massage, "SetItemMenu", MainActivity.LOG_ACTIVITY) }
}