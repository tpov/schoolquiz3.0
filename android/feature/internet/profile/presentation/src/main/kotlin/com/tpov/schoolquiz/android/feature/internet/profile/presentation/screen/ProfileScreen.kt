@file:Suppress("FunctionNaming", "MagicNumber", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.internet.profile.presentation.screen

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTheme
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.component.ProfileComponent
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate.ProfileUiState
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.OwnedNickname
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.PlatformAccountChooserHost
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileQualification
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileStatus
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val MESSAGE_AUTO_DISMISS_MS = 3_000L

/**
 * Everything the profile screen can do, in one place.
 *
 * Bundled because the list only grows — renaming, wearing a name, signing in — and a composable
 * that takes nine callbacks stops being readable at the call site long before the compiler minds.
 */
data class ProfileActions(
    val onNicknameChange: (String) -> Unit = {},
    val onStartRename: () -> Unit = {},
    val onCancelRename: () -> Unit = {},
    val onSaveNickname: () -> Unit = {},
    val onSelectNickname: (String) -> Unit = {},
    val onLinkGoogle: () -> Unit = {},
)

@Composable
fun ProfileScreen(
    component: ProfileComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ProfileView(
        state = state,
        actions =
            ProfileActions(
                onNicknameChange = component::onNicknameChange,
                onStartRename = component::onStartRename,
                onCancelRename = component::onCancelRename,
                onSaveNickname = component::onSaveNickname,
                onSelectNickname = component::onSelectNickname,
                onLinkGoogle = {
                    // The account sheet is a system dialog and needs the Activity it appears over.
                    (context as? Activity)?.let { component.onLinkGoogle(PlatformAccountChooserHost(it)) }
                },
            ),
        modifier = modifier,
    )
    // Fires every time the tab is opened, because the tab's content leaves composition when
    // another tab is shown — which is exactly when the names may have changed under it.
    LaunchedEffect(Unit) { component.onScreenShown() }
    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(MESSAGE_AUTO_DISMISS_MS)
            component.onMessageShown()
        }
    }
}

/**
 * The default screen of the Internet tab.
 *
 * Reads top to bottom as an answer to one question — how is this account doing: who you are, which
 * league that puts you in, what the last fortnight looked like, what you are trusted with, what you
 * have collected. The counts nobody came for are last, as rows rather than panels.
 *
 * Scrolls rather than filling the height. The radar needs a fixed size to stay a hexagon, and a
 * layout that divides slack between panels turns it into an oval on a short screen.
 */
@Composable
fun ProfileView(
    state: ProfileUiState,
    actions: ProfileActions,
    modifier: Modifier = Modifier,
) {
    val metrics = remember(state.profile) { state.profile.dashboardMetrics() }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = 16.dp, vertical = 4.dp)),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        ProfileIdentityRow(
            state = state,
            onNicknameChange = actions.onNicknameChange,
            onStartRename = actions.onStartRename,
            onCancelRename = actions.onCancelRename,
            onSaveNickname = actions.onSaveNickname,
        )

        ProfileLeagueBand(
            leagueName = metrics.leagueName,
            nextLeagueName = metrics.nextLeagueName,
            nextMilestoneDelta = metrics.nextMilestoneDelta,
            skillPoints = state.profile.skillPoints,
            progress = metrics.leagueProgress,
            activity = state.dailyActivity,
        )

        ProfileQualificationCard(
            values = metrics.qualificationValues,
            averagePercent = metrics.qualificationPercent,
            rolesHeld = metrics.rolesHeld,
            topRole = metrics.topRole,
        )

        ProfileNicknameShelf(state = state, onSelect = actions.onSelectNickname)

        if (state.canLinkGoogle || state.isLinkingGoogle) {
            ProfileGoogleUpgrade(busy = state.isLinkingGoogle, onClick = actions.onLinkGoogle)
        }

        ProfileTrophyShelf(profile = state.profile)

        ProfileFooterRows(profile = state.profile)

        state.message?.let { ProfileToast(message = it) }
    }
}

private data class ProfileDashboardMetrics(
    val qualificationValues: List<Float>,
    val qualificationPercent: Int,
    val rolesHeld: Int,
    val topRole: String?,
    val leagueName: String,
    val nextLeagueName: String?,
    val leagueProgress: Int,
    val nextMilestoneDelta: Int,
)

/** The role names, in the order [ProfileQualification] declares them. */
private val ROLE_NAMES = listOf("Спонсор", "Тестер", "Переводчик", "Модератор", "Админ", "Разработчик")

private fun UserProfile.dashboardMetrics(): ProfileDashboardMetrics {
    val levels =
        listOf(
            qualification.sponsorLevel,
            qualification.testerLevel,
            qualification.translatorLevel,
            qualification.moderatorLevel,
            qualification.adminLevel,
            qualification.developerLevel,
        )
    val values = levels.map { (it / 100f).coerceIn(0f, 1f) }
    val league = leagueForSkill(skillPoints)
    return ProfileDashboardMetrics(
        qualificationValues = values,
        qualificationPercent = (values.average() * 100).roundToInt().coerceIn(0, 100),
        rolesHeld = levels.count { it > 0 },
        // The role the account is furthest along in; ties go to the earlier one, which is the
        // order the qualifications are granted in anyway.
        topRole = levels.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.let { ROLE_NAMES[it.index] },
        leagueName = league.name,
        nextLeagueName = league.nextName,
        leagueProgress = league.progress,
        nextMilestoneDelta = league.nextMilestoneDelta,
    )
}

private data class ExperienceLeague(
    val name: String,
    val nextName: String?,
    val progress: Int,
    val nextMilestoneDelta: Int,
)

private fun leagueForSkill(skillPoints: Int): ExperienceLeague {
    val milestones = listOf(0, 100, 500, 1_500, 5_000, 15_000, 50_000)
    val names = listOf("Старт", "Ученик", "Знаток", "Эксперт", "Мастер", "Легенда")
    val lowerIndex = milestones.indexOfLast { skillPoints >= it }.coerceIn(0, names.lastIndex)
    val lower = milestones[lowerIndex]
    val upper = milestones.getOrElse(lowerIndex + 1) { milestones.last() }
    val progress =
        if (upper == lower) {
            100
        } else {
            (((skillPoints - lower).coerceAtLeast(0)) / (upper - lower).toFloat() * 100f)
                .roundToInt()
                .coerceIn(0, 100)
        }
    return ExperienceLeague(
        name = names[lowerIndex],
        nextName = names.getOrNull(lowerIndex + 1),
        progress = progress,
        nextMilestoneDelta = (upper - skillPoints).coerceAtLeast(0),
    )
}

internal val ProfileStatus.displayName: String
    get() =
        when (this) {
            ProfileStatus.OFFLINE -> "Офлайн"
            ProfileStatus.ANONYMOUS -> "Анонимный"
            ProfileStatus.REGISTERED -> "Зарегистрирован"
            ProfileStatus.VALIDATED -> "Валидирован"
        }

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 412, heightDp = 892)
@Composable
@Suppress("UnusedPrivateMember")
private fun ProfileViewPreview() {
    NoirTheme {
        ProfileView(
            state =
                ProfileUiState(
                    profile =
                        UserProfile(
                            uid = "preview",
                            nickname = "Олег",
                            status = ProfileStatus.REGISTERED,
                            avatarUrl = null,
                            knownLanguages = listOf("ru", "en"),
                            createdAtMs = 0L,
                            updatedAtMs = 0L,
                            skillPoints = 1_420,
                            gold = 3L,
                            nolics = 840L,
                            standardHearts = 5,
                            goldHearts = 1,
                            qualification =
                                ProfileQualification(
                                    sponsorLevel = 20,
                                    testerLevel = 100,
                                    translatorLevel = 45,
                                    developerLevel = 100,
                                ),
                            boxCount = 2,
                            boxStreakDays = 6,
                            trophies = setOf("first_steps", "night_owl", "quick_wit", "collector"),
                            ownedLogos = listOf("a", "b", "c"),
                        ),
                    dailyActivity = listOf(0, 2, 4, 0, 1, 5, 0, 3, 6, 2, 4, 7, 3, 5),
                    ownedNicknames =
                        listOf(
                            OwnedNickname("Олег", active = true, generated = false),
                            OwnedNickname("tpov", active = false, generated = false, listedPrice = 25L),
                            OwnedNickname("UserHN42E2", active = false, generated = true),
                        ),
                ),
            actions = ProfileActions(),
        )
    }
}
