package com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileQualification
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileStatus
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileUiStateTest {

    private fun profile(
        status: ProfileStatus,
        nickname: String = "СтарыйНик",
    ): UserProfile =
        UserProfile(
            uid = if (status == ProfileStatus.OFFLINE) "" else "uid-1",
            nickname = nickname,
            status = status,
            avatarUrl = null,
            knownLanguages = emptyList(),
            createdAtMs = 0L,
            updatedAtMs = 0L,
            skillPoints = 0,
            gold = 0L,
            nolics = 0L,
            standardHearts = 5,
            goldHearts = 0,
            qualification = ProfileQualification(),
        )

    // ── canSaveNickname ────────────────────────────────────────────────────────────────────────

    @Test
    fun `empty input cannot be saved`() {
        val state = ProfileUiState(profile = profile(ProfileStatus.ANONYMOUS), nicknameInput = "")

        assertFalse(state.canSaveNickname)
    }

    @Test
    fun `too short input cannot be saved`() {
        val state = ProfileUiState(profile = profile(ProfileStatus.ANONYMOUS), nicknameInput = "аб")

        assertFalse(state.canSaveNickname)
    }

    @Test
    fun `input equal to current nickname cannot be saved`() {
        val current = "СтарыйНик"
        val state = ProfileUiState(profile = profile(ProfileStatus.ANONYMOUS, nickname = current), nicknameInput = current)

        assertFalse(state.canSaveNickname)
    }

    @Test
    fun `input differing only by surrounding whitespace cannot be saved`() {
        val current = "СтарыйНик"
        val state =
            ProfileUiState(profile = profile(ProfileStatus.ANONYMOUS, nickname = current), nicknameInput = "  $current ")

        assertFalse(state.canSaveNickname)
    }

    @Test
    fun `valid changed nickname can be saved`() {
        val state =
            ProfileUiState(profile = profile(ProfileStatus.ANONYMOUS), nicknameInput = "НовыйНик")

        assertTrue(state.canSaveNickname)
    }

    @Test
    fun `save is blocked while saving is in flight`() {
        val state =
            ProfileUiState(profile = profile(ProfileStatus.ANONYMOUS), nicknameInput = "НовыйНик")
                .copy(isSaving = true)

        assertFalse(state.canSaveNickname)
    }

    @Test
    fun `offline profile cannot save nickname`() {
        val state =
            ProfileUiState(profile = profile(ProfileStatus.OFFLINE), nicknameInput = "НовыйНик")

        assertFalse(state.canSaveNickname)
    }

    @Test
    fun `validated profile cannot save nickname`() {
        val state =
            ProfileUiState(profile = profile(ProfileStatus.VALIDATED), nicknameInput = "НовыйНик")

        assertFalse(state.canSaveNickname)
    }

    @Test
    fun `registered profile can save a valid changed nickname`() {
        val state =
            ProfileUiState(profile = profile(ProfileStatus.REGISTERED), nicknameInput = "НовыйНик")

        assertTrue(state.canSaveNickname)
    }

    // ── canLinkGoogle ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `anonymous account can start google link`() {
        val state = ProfileUiState(profile = profile(ProfileStatus.ANONYMOUS))

        assertTrue(state.canLinkGoogle)
    }

    @Test
    fun `google link is blocked while another link is in flight`() {
        val state =
            ProfileUiState(profile = profile(ProfileStatus.ANONYMOUS)).copy(isLinkingGoogle = true)

        assertFalse(state.canLinkGoogle)
    }

    @Test
    fun `registered account cannot link google`() {
        val state = ProfileUiState(profile = profile(ProfileStatus.REGISTERED))

        assertFalse(state.canLinkGoogle)
    }

    @Test
    fun `offline account cannot link google`() {
        val state = ProfileUiState(profile = profile(ProfileStatus.OFFLINE))

        assertFalse(state.canLinkGoogle)
    }

    // ── hasActivity ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `all-zero activity is not a chart`() {
        val state = ProfileUiState(profile = profile(ProfileStatus.ANONYMOUS), dailyActivity = List(14) { 0 })

        assertFalse(state.hasActivity)
    }

    @Test
    fun `a single non-zero day makes activity`() {
        val days = List(14) { 0 }.toMutableList()
        days[9] = 3
        val state = ProfileUiState(profile = profile(ProfileStatus.ANONYMOUS), dailyActivity = days)

        assertTrue(state.hasActivity)
    }

    @Test
    fun `empty activity list has no chart`() {
        val state = ProfileUiState(profile = profile(ProfileStatus.ANONYMOUS), dailyActivity = emptyList())

        assertFalse(state.hasActivity)
    }
}
