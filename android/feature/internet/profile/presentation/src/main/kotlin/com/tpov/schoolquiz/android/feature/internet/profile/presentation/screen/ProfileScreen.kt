@file:Suppress("FunctionNaming", "MagicNumber", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.internet.profile.presentation.screen

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTheme
import com.tpov.schoolquiz.android.core.designsystem.noir.noirScreenWash
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.R
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.component.ProfileComponent
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate.ProfileMessage
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate.ProfileUiState
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.OwnedNickname
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.PlatformAccountChooserHost
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileQualification
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileStatus
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Midpoint of the profile's wash, from the drawing: near-black with the accent's blue in it. */
private val NoirProfileWash = androidx.compose.ui.graphics.Color(0xFF0C1C28)

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
    val onRefresh: () -> Unit = {},
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
                onRefresh = {
                    // One sync at a time: the in-flight flag also drives the icon's spin.
                    if (!state.isLoading) component.onRefresh()
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
    val leagueName = stringResource(LEAGUE_NAME_RES[metrics.leagueNameIndex])
    val nextLeagueName = metrics.nextLeagueIndex?.let { stringResource(LEAGUE_NAME_RES[it]) }
    val topRole = metrics.topRoleIndex?.let { stringResource(ROLE_NAME_RES[it]) }
    Column(
        modifier
            .noirScreenWash(NoirProfileWash)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = 16.dp, vertical = 4.dp)),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        // The canvas keeps refresh on the bar's trailing edge; the shell owns that bar, so the
        // control lives on the screen's first line instead.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ProfileRefreshButton(
                isSyncing = state.isLoading,
                contentDescription = stringResource(R.string.profile_cd_refresh),
                onClick = actions.onRefresh,
            )
        }

        ProfileIdentityRow(
            state = state,
            onNicknameChange = actions.onNicknameChange,
            onStartRename = actions.onStartRename,
            onCancelRename = actions.onCancelRename,
            onSaveNickname = actions.onSaveNickname,
        )

        ProfileLeagueBand(
            leagueName = leagueName,
            nextLeagueName = nextLeagueName,
            nextMilestoneDelta = metrics.nextMilestoneDelta,
            skillPoints = state.profile.skillPoints,
            progress = metrics.leagueProgress,
            activity = state.dailyActivity,
        )

        ProfileQualificationCard(
            standing = state.standing,
            values = metrics.qualificationValues,
            activity = metrics.qualificationActivity,
            averagePercent = metrics.qualificationPercent,
            rolesHeld = metrics.rolesHeld,
            topRole = topRole,
        )

        if (state.canLinkGoogle || state.isLinkingGoogle) {
            ProfileGoogleUpgrade(busy = state.isLinkingGoogle, onClick = actions.onLinkGoogle)
        }

        ProfileTrophyShelf(profile = state.profile)

        ProfileFooterRows(profile = state.profile)

        state.message?.let { ProfileToast(message = it.resolvedText()) }
    }
}

private data class ProfileDashboardMetrics(
    val qualificationValues: List<Float>,
    /** The six activity ratings, on the same axes as the levels. */
    val qualificationActivity: List<Float>,
    val qualificationPercent: Int,
    val rolesHeld: Int,
    /** Index into [ROLE_NAME_RES]; null when no qualification is held at all. */
    val topRoleIndex: Int?,
    val leagueNameIndex: Int,
    val nextLeagueIndex: Int?,
    val leagueProgress: Int,
    val nextMilestoneDelta: Int,
)

/** The role names, in the order [ProfileQualification] declares them. */
private val ROLE_NAME_RES =
    listOf(
        R.string.profile_role_sponsor,
        R.string.profile_role_tester,
        R.string.profile_role_translator,
        R.string.profile_role_moderator,
        R.string.profile_role_admin,
        R.string.profile_role_developer,
    )

private val LEAGUE_NAME_RES =
    listOf(
        R.string.profile_league_start,
        R.string.profile_league_student,
        R.string.profile_league_connoisseur,
        R.string.profile_league_expert,
        R.string.profile_league_master,
        R.string.profile_league_legend,
    )

@Composable
private fun ProfileMessage.resolvedText(): String =
    when (this) {
        is ProfileMessage.NicknameActivated -> stringResource(R.string.profile_message_nickname_active, nickname)
        ProfileMessage.ProfileSynced -> stringResource(R.string.profile_message_synced)
        ProfileMessage.NicknameUpdated -> stringResource(R.string.profile_message_nickname_updated)
        ProfileMessage.GoogleLinked -> stringResource(R.string.profile_message_google_linked)
        ProfileMessage.GoogleSwitchedToExisting -> stringResource(R.string.profile_message_google_switched)
        is ProfileMessage.Failure -> detail ?: stringResource(R.string.profile_message_sync_failed)
    }

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
        qualificationActivity = activityRatings.axes,
        qualificationPercent = (values.average() * 100).roundToInt().coerceIn(0, 100),
        rolesHeld = levels.count { it > 0 },
        // The role the account is furthest along in; ties go to the earlier one, which is the
        // order the qualifications are granted in anyway.
        topRoleIndex = levels.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.index,
        leagueNameIndex = league.nameIndex,
        nextLeagueIndex = league.nextIndex,
        leagueProgress = league.progress,
        nextMilestoneDelta = league.nextMilestoneDelta,
    )
}

private data class ExperienceLeague(
    val nameIndex: Int,
    val nextIndex: Int?,
    val progress: Int,
    val nextMilestoneDelta: Int,
)

private fun leagueForSkill(skillPoints: Int): ExperienceLeague {
    val milestones = listOf(0, 100, 500, 1_500, 5_000, 15_000, 50_000)
    val lowerIndex = milestones.indexOfLast { skillPoints >= it }.coerceIn(0, LEAGUE_NAME_RES.lastIndex)
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
        nameIndex = lowerIndex,
        nextIndex = (lowerIndex + 1).takeIf { it <= LEAGUE_NAME_RES.lastIndex },
        progress = progress,
        nextMilestoneDelta = (upper - skillPoints).coerceAtLeast(0),
    )
}

val ProfileStatus.labelRes: Int
    @StringRes get() =
        when (this) {
            ProfileStatus.OFFLINE -> R.string.profile_status_offline
            ProfileStatus.ANONYMOUS -> R.string.profile_status_anonymous
            ProfileStatus.REGISTERED -> R.string.profile_status_registered
            ProfileStatus.VALIDATED -> R.string.profile_status_validated
        }

val ProfileStatus.displayName: String
    @Composable get() = stringResource(labelRes)

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
