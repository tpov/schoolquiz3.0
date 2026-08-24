@file:Suppress("FunctionNaming", "MagicNumber", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.local.settings.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGold
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGroup
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGroupHeader
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirIcons
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirRow
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTOff
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTheme
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.core.designsystem.noir.noirScreenGround
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileQualification
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileStatus
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile

/** Stands in for every value the account has not filled in. One wording, so a gap reads as a gap. */
private const val UNSET = "Не установлен"

/**
 * Settings, carried over from the legacy screen.
 *
 * Same four groups in the same order — personal details, game figures, sync, notifications — so
 * anyone who knew the old screen finds everything where they left it. What changed is the frame and
 * the honesty: rows with nothing behind them yet say so instead of offering a control that does
 * nothing. The design-style picker is gone; NOIR replaced both styles it chose between.
 */
@Composable
fun NoirSettingsScreen(
    profile: UserProfile,
    appVersionName: String,
    appVersionCode: Int,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().noirScreenGround()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PersonalGroup(profile) }
            item { GameStatsGroup(profile) }
            item { SyncGroup(onSyncNow) }
            item { NotificationsGroup() }
        }
        // Pinned to the bottom rather than trailing the list, and carrying no click of its own —
        // both pinned by instrumented tests (SCH-2). The build number is a fact about the screen
        // you are on, so it should not move depending on how far the list happens to scroll.
        Text(
            text = "v$appVersionName ($appVersionCode)",
            style = NoirType.kicker.copy(color = NoirTOff),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PersonalGroup(profile: UserProfile) {
    NoirGroup {
        NoirGroupHeader("Персональная информация")
        // Login and password are the same single fact — whether this account is signed in — so
        // they are one row, not two. There is nothing to change until Google sign-in is wired.
        SettingRow(
            label = "Аккаунт",
            value =
                when (profile.status) {
                    ProfileStatus.OFFLINE -> "Офлайн"
                    ProfileStatus.ANONYMOUS -> "Анонимный, без входа"
                    ProfileStatus.REGISTERED -> "Зарегистрирован"
                    ProfileStatus.VALIDATED -> "Подтверждён"
                },
            locked = true,
            note = if (profile.status == ProfileStatus.ANONYMOUS) "Вход через Google — скоро" else null,
        )
        SettingRow("Никнейм", profile.nickname)
        SettingRow("Имя", profile.realName ?: UNSET)
        SettingRow("День рождения", profile.birthday ?: UNSET)
        SettingRow("Город", profile.city ?: UNSET)
        SettingRow("Telegram", profile.telegram ?: UNSET)
        SettingRow(
            label = "Языки",
            value = profile.knownLanguages.joinToString(", ") { it.uppercase() }.ifBlank { UNSET },
            showDivider = false,
        )
    }
}

@Composable
private fun GameStatsGroup(profile: UserProfile) {
    NoirGroup {
        NoirGroupHeader("Игровая статистика")
        SettingRow("Жизни", "${profile.standardHearts}", icon = NoirIcons.Heart)
        SettingRow("Золотые жизни", "${profile.goldHearts}", icon = NoirIcons.Gem)
        SettingRow("Опыт", "${profile.skillPoints} XP")
        // Premium was a switch on the old screen. It is granted by the server, so a switch here
        // would be a control that refuses to move — the state is the whole truth.
        SettingRow(
            label = "Премиум",
            value = if (profile.premiumUntilMs > 0L) "Активен" else "Нет",
            valueTint = if (profile.premiumUntilMs > 0L) NoirGold else NoirT3,
            showDivider = false,
        )
    }
}

@Composable
private fun SyncGroup(onSyncNow: () -> Unit) {
    NoirGroup {
        NoirGroupHeader("Синхронизация")
        SettingRow("Профиль", "Каждый день")
        SettingRow("Квесты", "Каждый день")
        // The empty catalog screen tells people to sync from the menu, so the action belongs here
        // where they come looking for it.
        NoirRow(onClick = onSyncNow, showDivider = false) {
            Text("Синхронизировать сейчас", style = NoirType.rowTitle)
            Text("Забрать каталоги и профиль с сервера", style = NoirType.rowSub)
        }
    }
}

@Composable
private fun NotificationsGroup() {
    NoirGroup {
        NoirGroupHeader("Уведомления")
        SettingRow("Напоминания", "Выключены", locked = true, note = "Ждёт модуль уведомлений")
        SettingRow("Время занятий", UNSET, locked = true)
        SettingRow("Дни занятий", "Не выбраны", locked = true, showDivider = false)
    }
}

/**
 * One setting.
 *
 * A locked row keeps its place rather than disappearing: knowing a setting exists and is not ready
 * yet is worth more than a shorter list that hides it.
 */
@Composable
private fun SettingRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    locked: Boolean = false,
    note: String? = null,
    valueTint: androidx.compose.ui.graphics.Color = NoirT1,
    showDivider: Boolean = true,
) {
    NoirRow(
        showDivider = showDivider,
        leading =
            if (icon != null) {
                {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = LocalNoirAccent.current,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                null
            },
        trailing = {
            if (locked) {
                Icon(
                    NoirIcons.Lock,
                    contentDescription = "Пока недоступно",
                    tint = NoirTOff,
                    modifier = Modifier.size(15.dp),
                )
            }
        },
    ) {
        Text(label, style = NoirType.rowTitle)
        Text(
            value,
            style = NoirType.num.copy(fontSize = 12.sp, color = if (locked) NoirTOff else valueTint),
        )
        if (note != null) {
            Text(note, style = NoirType.rowSub.copy(color = NoirT3))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 412, heightDp = 892)
@Composable
@Suppress("UnusedPrivateMember")
private fun NoirSettingsPreview() {
    NoirTheme {
        NoirSettingsScreen(
            profile =
                UserProfile(
                    uid = "preview",
                    nickname = "Олег",
                    status = ProfileStatus.ANONYMOUS,
                    avatarUrl = null,
                    knownLanguages = listOf("ru", "en"),
                    createdAtMs = 0L,
                    updatedAtMs = 0L,
                    skillPoints = 1_420,
                    gold = 3L,
                    nolics = 840L,
                    standardHearts = 5,
                    goldHearts = 1,
                    qualification = ProfileQualification(),
                    city = "Киев",
                ),
            appVersionName = "0.1.0",
            appVersionCode = 1,
            onSyncNow = {},
        )
    }
}
