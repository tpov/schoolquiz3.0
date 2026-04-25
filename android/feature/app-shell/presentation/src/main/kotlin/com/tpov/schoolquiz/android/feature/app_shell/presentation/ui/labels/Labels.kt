package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.labels

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
import androidx.compose.ui.graphics.vector.ImageVector
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerFooterAction
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerSection
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.EventsConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.InternetConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.LocalConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.ShopConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.TabConfig

// ---- Tab ----

val Tab.displayName: String
    get() =
        when (this) {
            Tab.LOCAL -> "Локальная"
            Tab.INTERNET -> "Интернет"
            Tab.EVENTS -> "События"
            Tab.SHOP -> "Магазин"
        }

val Tab.icon: ImageVector
    get() =
        when (this) {
            Tab.LOCAL -> Icons.Default.Home
            Tab.INTERNET -> Icons.Default.Language
            Tab.EVENTS -> Icons.Default.Event
            Tab.SHOP -> Icons.Default.ShoppingCart
        }

// ---- DrawerSection ----

val DrawerSection.displayName: String
    get() =
        when (this) {
            DrawerSection.LocalSection.MyQuests -> "Мои квесты"
            DrawerSection.LocalSection.HomeQuests -> "Домашние квесты"
            DrawerSection.LocalSection.Settings -> "Настройки"
            DrawerSection.InternetSection.Arena -> "Арена"
            DrawerSection.InternetSection.Catalog -> "Каталог"
            DrawerSection.InternetSection.Qualifications -> "Квалификации"
            DrawerSection.InternetSection.Profile -> "Профиль"
            DrawerSection.InternetSection.Social -> "Социальное"
            DrawerSection.InternetSection.Leaderboard -> "Таблица лидеров"
            DrawerSection.EventsSection.ActiveEvents -> "Активные события"
            DrawerSection.EventsSection.Minigames -> "Мини-игры"
        }

val DrawerSection.icon: ImageVector
    get() =
        when (this) {
            DrawerSection.LocalSection.MyQuests -> Icons.Default.Book
            DrawerSection.LocalSection.HomeQuests -> Icons.Default.Home
            DrawerSection.LocalSection.Settings -> Icons.Default.Settings
            DrawerSection.InternetSection.Arena -> Icons.Default.Stadium
            DrawerSection.InternetSection.Catalog -> Icons.Default.Book
            DrawerSection.InternetSection.Qualifications -> Icons.Default.EmojiEvents
            DrawerSection.InternetSection.Profile -> Icons.Default.AccountCircle
            DrawerSection.InternetSection.Social -> Icons.Default.People
            DrawerSection.InternetSection.Leaderboard -> Icons.Default.Leaderboard
            DrawerSection.EventsSection.ActiveEvents -> Icons.Default.Event
            DrawerSection.EventsSection.Minigames -> Icons.Default.SportsEsports
        }

// ---- TabConfig ----

val TabConfig.displayName: String
    get() =
        when (this) {
            is LocalConfig ->
                when (this) {
                    LocalConfig.MyQuestsRoot -> "Мои квесты"
                    LocalConfig.HomeQuestsRoot -> "Домашние квесты"
                    LocalConfig.SettingsRoot -> "Настройки"
                    LocalConfig.DesignCatalogRoot -> "Design Catalog"
                    LocalConfig.EmptyRoot -> "Локальная"
                    LocalConfig.QuestCreateRoot -> "Создание квеста"
                }
            is InternetConfig ->
                when (this) {
                    InternetConfig.ArenaRoot -> "Арена"
                    InternetConfig.CatalogRoot -> "Каталог"
                    InternetConfig.QualificationsRoot -> "Квалификации"
                    InternetConfig.ProfileRoot -> "Профиль"
                    InternetConfig.SocialRoot -> "Социальное"
                    InternetConfig.LeaderboardRoot -> "Таблица лидеров"
                    InternetConfig.EmptyRoot -> "Интернет"
                }
            is EventsConfig ->
                when (this) {
                    EventsConfig.ActiveEventsRoot -> "Активные события"
                    EventsConfig.MinigamesRoot -> "Мини-игры"
                    EventsConfig.EmptyRoot -> "События"
                }
            is ShopConfig -> "Магазин"
        }

// ---- DrawerFooterAction ----

val DrawerFooterAction.displayName: String
    get() =
        when (this) {
            DrawerFooterAction.DesignCatalog -> "Design Catalog"
            DrawerFooterAction.SyncNow -> "Синхронизация"
            DrawerFooterAction.About -> "О приложении"
        }

val DrawerFooterAction.icon: ImageVector
    get() =
        when (this) {
            DrawerFooterAction.DesignCatalog -> Icons.Default.Palette
            DrawerFooterAction.SyncNow -> Icons.Default.Refresh
            DrawerFooterAction.About -> Icons.Default.Info
        }
