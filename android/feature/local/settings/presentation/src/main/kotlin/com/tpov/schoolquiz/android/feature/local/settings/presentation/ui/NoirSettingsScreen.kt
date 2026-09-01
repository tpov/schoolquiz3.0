@file:Suppress("FunctionNaming", "MagicNumber", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.local.settings.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGold
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGroup
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGroupHeader
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirHair
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirIcons
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirRow
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapeMd
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTOff
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTheme
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.core.designsystem.noir.noirScreenGround
import com.tpov.schoolquiz.android.feature.local.settings.presentation.R
import com.tpov.schoolquiz.shared.core.sync.SyncFrequency
import com.tpov.schoolquiz.shared.core.sync.SyncStatus
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileQualification
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileStatus
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile

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
    syncFrequency: SyncFrequency = SyncFrequency.DAILY,
    onSyncFrequencySelected: (SyncFrequency) -> Unit = {},
    profileSyncFrequency: SyncFrequency = SyncFrequency.DAILY,
    onProfileSyncFrequencySelected: (SyncFrequency) -> Unit = {},
    syncStatus: SyncStatus = SyncStatus(),
    onForceResync: () -> Unit = {},
) {
    Box(modifier.fillMaxSize().noirScreenGround()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PersonalGroup(profile) }
            item { GameStatsGroup(profile) }
            item {
                SyncGroup(
                    status = syncStatus,
                    frequency = syncFrequency,
                    onFrequencySelected = onSyncFrequencySelected,
                    profileFrequency = profileSyncFrequency,
                    onProfileFrequencySelected = onProfileSyncFrequencySelected,
                    onSyncNow = onSyncNow,
                    onForceResync = onForceResync,
                )
            }
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
        NoirGroupHeader(stringResource(R.string.settings_group_personal))
        // Login and password are the same single fact — whether this account is signed in — so
        // they are one row, not two. There is nothing to change until Google sign-in is wired.
        SettingRow(
            label = stringResource(R.string.settings_account),
            value =
                when (profile.status) {
                    ProfileStatus.OFFLINE -> stringResource(R.string.settings_status_offline)
                    ProfileStatus.ANONYMOUS -> stringResource(R.string.settings_status_anonymous)
                    ProfileStatus.REGISTERED -> stringResource(R.string.settings_status_registered)
                    ProfileStatus.VALIDATED -> stringResource(R.string.settings_status_validated)
                },
            locked = true,
            note =
                if (profile.status == ProfileStatus.ANONYMOUS) {
                    stringResource(R.string.settings_note_google_soon)
                } else {
                    null
                },
        )
        SettingRow(stringResource(R.string.settings_nickname), profile.nickname)
        SettingRow(
            stringResource(R.string.settings_real_name),
            profile.realName ?: stringResource(R.string.settings_value_unset),
        )
        SettingRow(
            stringResource(R.string.settings_birthday),
            profile.birthday ?: stringResource(R.string.settings_value_unset),
        )
        SettingRow(
            stringResource(R.string.settings_city),
            profile.city ?: stringResource(R.string.settings_value_unset),
        )
        SettingRow(
            stringResource(R.string.settings_telegram),
            profile.telegram ?: stringResource(R.string.settings_value_unset),
        )
        SettingRow(
            label = stringResource(R.string.settings_languages),
            value =
                profile.knownLanguages
                    .joinToString(", ") { it.uppercase() }
                    .ifBlank { stringResource(R.string.settings_value_unset) },
            showDivider = false,
        )
    }
}

@Composable
private fun GameStatsGroup(profile: UserProfile) {
    NoirGroup {
        NoirGroupHeader(stringResource(R.string.settings_group_game_stats))
        SettingRow(stringResource(R.string.settings_hearts), "${profile.standardHearts}", icon = NoirIcons.Heart)
        SettingRow(stringResource(R.string.settings_gold_hearts), "${profile.goldHearts}", icon = NoirIcons.GoldStack)
        SettingRow(stringResource(R.string.settings_skill_points), "${profile.skillPoints} XP")
        // Premium was a switch on the old screen. It is granted by the server, so a switch here
        // would be a control that refuses to move — the state is the whole truth.
        SettingRow(
            label = stringResource(R.string.settings_premium),
            value =
                if (profile.premiumUntilMs > 0L) {
                    stringResource(R.string.settings_premium_active)
                } else {
                    stringResource(R.string.settings_premium_no)
                },
            valueTint = if (profile.premiumUntilMs > 0L) NoirGold else NoirT3,
            showDivider = false,
        )
    }
}

/**
 * The sync group: how often the app reaches out on its own, plus the always-there manual action.
 *
 * Content and the profile carry separate cadences — quests play offline either way, so the
 * profile is allowed to trail behind on a rarer schedule. Each row opens the same picker; one
 * decision, five answers, no depth.
 */
@Composable
private fun SyncGroup(
    status: SyncStatus,
    frequency: SyncFrequency,
    onFrequencySelected: (SyncFrequency) -> Unit,
    profileFrequency: SyncFrequency,
    onProfileFrequencySelected: (SyncFrequency) -> Unit,
    onSyncNow: () -> Unit,
    onForceResync: () -> Unit,
) {
    val pickingContent = remember { mutableStateOf(false) }
    val confirmingResync = remember { mutableStateOf(false) }
    val pickingProfile = remember { mutableStateOf(false) }
    if (pickingContent.value) {
        FrequencyPickerDialog(
            titleRes = R.string.settings_sync_frequency,
            current = frequency,
            onSelect = { option ->
                pickingContent.value = false
                onFrequencySelected(option)
            },
            onDismiss = { pickingContent.value = false },
        )
    }
    if (pickingProfile.value) {
        FrequencyPickerDialog(
            titleRes = R.string.settings_sync_frequency_profile,
            current = profileFrequency,
            onSelect = { option ->
                pickingProfile.value = false
                onProfileFrequencySelected(option)
            },
            onDismiss = { pickingProfile.value = false },
        )
    }
    if (confirmingResync.value) {
        ResyncConfirmDialog(
            onConfirm = {
                confirmingResync.value = false
                onForceResync()
            },
            onDismiss = { confirmingResync.value = false },
        )
    }
    NoirGroup {
        NoirGroupHeader(stringResource(R.string.settings_group_sync))
        NoirRow(onClick = { pickingContent.value = true }) {
            Text(stringResource(R.string.settings_sync_frequency), style = NoirType.rowTitle)
            Text(stringResource(frequency.labelRes), style = NoirType.rowSub.copy(color = LocalNoirAccent.current))
        }
        NoirRow(onClick = { pickingProfile.value = true }) {
            Text(stringResource(R.string.settings_sync_frequency_profile), style = NoirType.rowTitle)
            Text(
                stringResource(profileFrequency.labelRes),
                style = NoirType.rowSub.copy(color = LocalNoirAccent.current),
            )
        }
        // Что с синхронизацией на самом деле (AD-14). До сих пор наружу не выходило ничего:
        // игрок не мог узнать ни что действия ждут отправки, ни что одно уже не уедет.
        SettingRow(
            label = stringResource(R.string.settings_sync_state),
            value = status.summary(),
            note = status.attentionNote(),
            valueTint = if (status.needsAttention) NoirDanger else NoirT1,
        )
        // The empty catalog screen tells people to sync from the menu, so the action belongs here
        // where they come looking for it.
        NoirRow(onClick = onSyncNow) {
            Text(stringResource(R.string.settings_sync_now), style = NoirType.rowTitle)
            Text(stringResource(R.string.settings_sync_now_subtitle), style = NoirType.rowSub)
        }
        // Последнее средство: курсоры чтения обнуляются и журналы читаются с начала. Спрашиваем
        // подтверждение, потому что это перекачка всего содержимого, а не обычная синхронизация.
        NoirRow(onClick = { confirmingResync.value = true }, showDivider = false) {
            Text(stringResource(R.string.settings_resync), style = NoirType.rowTitle)
            Text(stringResource(R.string.settings_resync_subtitle), style = NoirType.rowSub)
        }
    }
}

/**
 * Подтверждение перечитывания.
 *
 * Спрашиваем не потому, что можно что-то потерять — ресинк лечит только сторону чтения и
 * неотправленные действия не трогает (AD-30), — а потому, что это скачивание всего заново, и
 * нажать его случайно на мобильном интернете обидно.
 */
@Composable
private fun ResyncConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_resync)) },
        text = { Text(stringResource(R.string.settings_resync_confirm)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_resync_confirm_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_resync_cancel)) }
        },
    )
}

/** One decision, five answers: a whole cadence choice in a single dialog. */
@Composable
private fun FrequencyPickerDialog(
    titleRes: Int,
    current: SyncFrequency,
    onSelect: (SyncFrequency) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column {
                SyncFrequency.entries.forEachIndexed { index, option ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(NoirShapeMd)
                                .clickable { onSelect(option) }
                                .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = option == current, onClick = { onSelect(option) })
                        Text(stringResource(option.labelRes), style = NoirType.rowTitle)
                    }
                    if (index < SyncFrequency.entries.lastIndex) {
                        HorizontalDivider(color = NoirHair)
                    }
                }
            }
        },
        confirmButton = {},
    )
}

/** Label for the cadence value and its picker rows. */
private val SyncFrequency.labelRes: Int
    get() =
        when (this) {
            SyncFrequency.MANUAL -> R.string.settings_sync_freq_manual
            SyncFrequency.ON_LAUNCH -> R.string.settings_sync_freq_on_launch
            SyncFrequency.DAILY -> R.string.settings_sync_freq_daily
            SyncFrequency.EVERY_3_DAYS -> R.string.settings_sync_freq_3days
            SyncFrequency.WEEKLY -> R.string.settings_sync_freq_weekly
        }

@Composable
private fun NotificationsGroup() {
    NoirGroup {
        NoirGroupHeader(stringResource(R.string.settings_group_notifications))
        SettingRow(
            label = stringResource(R.string.settings_reminders),
            value = stringResource(R.string.settings_reminders_off),
            locked = true,
            note = stringResource(R.string.settings_note_waiting_module),
        )
        SettingRow(
            label = stringResource(R.string.settings_lesson_time),
            value = stringResource(R.string.settings_value_unset),
            locked = true,
        )
        SettingRow(
            label = stringResource(R.string.settings_lesson_days),
            value = stringResource(R.string.settings_not_selected),
            locked = true,
            showDivider = false,
        )
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
                    contentDescription = stringResource(R.string.settings_cd_locked),
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

/**
 * Одна строка про состояние синхронизации.
 *
 * Числа раздельные (AD-14), но на экране их надо свести в одну фразу: «ждёт отправки» и «застряло»
 * — разные вещи, и второе важнее, поэтому оно и говорится первым.
 */
@Composable
private fun SyncStatus.summary(): String =
    when {
        needsAttention -> stringResource(R.string.settings_sync_state_stuck, counts.stuck)
        hasPending -> stringResource(R.string.settings_sync_state_pending, counts.pending)
        hasEverSucceeded -> stringResource(R.string.settings_sync_state_clean)
        else -> stringResource(R.string.settings_sync_state_never)
    }

/**
 * Пояснение под строкой — только когда есть что пояснять.
 *
 * Непрочитанные записи журнала стоят последними: с ними игроку делать нечего, ни повтор, ни
 * «Перечитать всё» их не разберут. Но и молчать нельзя — часть содержимого осталась старой, и без
 * этой строки она выглядела бы свежей.
 */
@Composable
private fun SyncStatus.attentionNote(): String? =
    when {
        counts.conflicted > 0 && counts.quarantined > 0 ->
            stringResource(R.string.settings_sync_note_both, counts.conflicted, counts.quarantined)
        counts.conflicted > 0 -> stringResource(R.string.settings_sync_note_conflict, counts.conflicted)
        counts.quarantined > 0 -> stringResource(R.string.settings_sync_note_quarantine, counts.quarantined)
        hasUnreadableChanges -> stringResource(R.string.settings_sync_note_unreadable, unreadableChanges)
        else -> null
    }
