package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.labels

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stadium
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.tpov.schoolquiz.android.feature.app_shell.presentation.R
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerFooterAction
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerSection
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.EventsConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.InternetConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.LocalConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.ShopConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.TabConfig

/*
 * Display names resolve through string resources so the shell follows the device language.
 * The [labelRes] mappings are pure and testable; the composables are what screens read.
 */

// ---- Tab ----

val Tab.labelRes: Int
    @StringRes get() =
        when (this) {
            Tab.LOCAL -> R.string.tab_local
            Tab.INTERNET -> R.string.tab_internet
            Tab.EVENTS -> R.string.tab_events
            Tab.SHOP -> R.string.tab_shop
        }

val Tab.displayName: String
    @Composable get() = stringResource(labelRes)

val Tab.icon: ImageVector
    get() =
        when (this) {
            Tab.LOCAL -> Icons.Default.Home
            Tab.INTERNET -> Icons.Default.Language
            Tab.EVENTS -> Icons.Default.Event
            Tab.SHOP -> Icons.Default.ShoppingCart
        }

// ---- DrawerSection ----

val DrawerSection.labelRes: Int
    @StringRes get() =
        when (this) {
            DrawerSection.LocalSection.MyQuests -> R.string.section_my_quests
            DrawerSection.LocalSection.HomeQuests -> R.string.section_home_quests
            DrawerSection.LocalSection.Archive -> R.string.section_archive
            DrawerSection.LocalSection.ReviewQueue -> R.string.section_review_queue
            DrawerSection.LocalSection.Settings -> R.string.section_settings
            DrawerSection.InternetSection.Arena -> R.string.section_arena
            DrawerSection.InternetSection.Catalog -> R.string.section_catalog
            DrawerSection.InternetSection.Qualifications -> R.string.section_qualifications
            DrawerSection.InternetSection.Profile -> R.string.section_profile
            DrawerSection.InternetSection.Social -> R.string.section_social
            DrawerSection.InternetSection.Leaderboard -> R.string.section_leaderboard
            DrawerSection.EventsSection.QualifierTournament -> R.string.section_qualifier_tournament
            DrawerSection.EventsSection.WorldChampionship -> R.string.section_world_championship
            DrawerSection.EventsSection.ActiveEvents -> R.string.section_active_events
            DrawerSection.EventsSection.Minigames -> R.string.section_minigames
        }

val DrawerSection.displayName: String
    @Composable get() = stringResource(labelRes)

val DrawerSection.icon: ImageVector
    get() =
        when (this) {
            DrawerSection.LocalSection.MyQuests -> Icons.Default.Book
            DrawerSection.LocalSection.HomeQuests -> Icons.Default.Home
            DrawerSection.LocalSection.Archive -> Icons.Default.Book
            DrawerSection.LocalSection.ReviewQueue -> Icons.Default.Book
            DrawerSection.LocalSection.Settings -> Icons.Default.Settings
            DrawerSection.InternetSection.Arena -> Icons.Default.Stadium
            DrawerSection.InternetSection.Catalog -> Icons.Default.Book
            DrawerSection.InternetSection.Qualifications -> Icons.Default.EmojiEvents
            DrawerSection.InternetSection.Profile -> Icons.Default.AccountCircle
            DrawerSection.InternetSection.Social -> Icons.Default.People
            DrawerSection.InternetSection.Leaderboard -> Icons.Default.Leaderboard
            DrawerSection.EventsSection.QualifierTournament -> Icons.Default.Event
            DrawerSection.EventsSection.WorldChampionship -> Icons.Default.EmojiEvents
            DrawerSection.EventsSection.ActiveEvents -> Icons.Default.Event
            DrawerSection.EventsSection.Minigames -> Icons.Default.SportsEsports
        }

// ---- TabConfig ----

val TabConfig.labelRes: Int
    @StringRes get() =
        when (this) {
            is LocalConfig ->
                when (this) {
                    LocalConfig.MyQuestsRoot -> R.string.section_my_quests
                    LocalConfig.HomeQuestsRoot -> R.string.section_home_quests
                    LocalConfig.ArchiveRoot -> R.string.section_archive
                    LocalConfig.ReviewQueueRoot -> R.string.section_review_queue
                    LocalConfig.SettingsRoot -> R.string.section_settings
                    LocalConfig.DesignCatalogRoot -> R.string.config_design_catalog
                    LocalConfig.EmptyRoot -> R.string.tab_local
                    LocalConfig.QuestCreateRoot -> R.string.config_quest_create
                }
            is InternetConfig ->
                when (this) {
                    InternetConfig.ArenaRoot -> R.string.section_arena
                    InternetConfig.CatalogRoot -> R.string.section_catalog
                    InternetConfig.QualificationsRoot -> R.string.section_qualifications
                    InternetConfig.ProfileRoot -> R.string.section_profile
                    InternetConfig.SocialRoot -> R.string.section_social
                    InternetConfig.LeaderboardRoot -> R.string.section_leaderboard
                    InternetConfig.EmptyRoot -> R.string.tab_internet
                }
            is EventsConfig ->
                when (this) {
                    EventsConfig.QualifierTournamentRoot -> R.string.section_qualifier_tournament
                    EventsConfig.QualifierTournamentLeaderboardRoot -> R.string.config_leaderboard
                    EventsConfig.QualifierTournamentParticipantsRoot -> R.string.config_participants
                    EventsConfig.WorldChampionshipRoot -> R.string.section_world_championship
                    EventsConfig.WorldChampionshipLeaderboardRoot -> R.string.config_leaderboard
                    EventsConfig.WorldChampionshipParticipantsRoot -> R.string.config_participants
                    EventsConfig.ActiveEventsRoot -> R.string.section_active_events
                    EventsConfig.MinigamesRoot -> R.string.section_minigames
                    EventsConfig.EmptyRoot -> R.string.tab_events
                }
            is ShopConfig -> R.string.tab_shop
        }

val TabConfig.displayName: String
    @Composable get() = stringResource(labelRes)

// ---- DrawerFooterAction ----

val DrawerFooterAction.labelRes: Int
    @StringRes get() =
        when (this) {
            DrawerFooterAction.DesignCatalog -> R.string.config_design_catalog
            DrawerFooterAction.SyncNow -> R.string.footer_sync_now
            DrawerFooterAction.About -> R.string.footer_about
        }

val DrawerFooterAction.displayName: String
    @Composable get() = stringResource(labelRes)

val DrawerFooterAction.icon: ImageVector
    get() =
        when (this) {
            DrawerFooterAction.DesignCatalog -> Icons.Default.Palette
            DrawerFooterAction.SyncNow -> Icons.Default.Refresh
            DrawerFooterAction.About -> Icons.Default.Info
        }
