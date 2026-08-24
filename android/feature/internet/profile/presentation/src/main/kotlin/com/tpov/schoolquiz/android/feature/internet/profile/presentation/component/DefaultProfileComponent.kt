package com.tpov.schoolquiz.android.feature.internet.profile.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate.ProfileUiState
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.AccountChooserHost
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.GoogleLinkOutcome
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.NicknameRepository
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.EnsureCurrentProfileUseCase
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.LinkGoogleAccountUseCase
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.ObserveCurrentProfileUseCase
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.ObserveDailyActivityUseCase
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.UpdateProfileNicknameUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DefaultProfileComponent(
    componentContext: ComponentContext,
    private val observeCurrentProfile: ObserveCurrentProfileUseCase,
    private val ensureCurrentProfile: EnsureCurrentProfileUseCase,
    private val updateProfileNickname: UpdateProfileNicknameUseCase,
    private val observeDailyActivity: ObserveDailyActivityUseCase,
    private val nicknames: NicknameRepository,
    private val linkGoogleAccount: LinkGoogleAccountUseCase,
) : ProfileComponent, ComponentContext by componentContext {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(ProfileUiState(isLoading = true))
    override val state: StateFlow<ProfileUiState> = _state

    init {
        lifecycle.doOnDestroy { componentJob.cancel() }
        scope.launch {
            observeCurrentProfile().collect { profile ->
                _state.update { current ->
                    val keepEditing = current.isEditingNickname && current.canEditNickname
                    current.copy(
                        profile = profile,
                        nicknameInput = if (keepEditing) current.nicknameInput else profile.nickname,
                        isLoading = false,
                    )
                }
            }
        }
        scope.launch {
            observeDailyActivity().collect { activity ->
                _state.update { it.copy(dailyActivity = activity) }
            }
        }
        onRefresh()
        refreshNicknames()
    }

    override fun onSelectNickname(nickname: String) {
        if (_state.value.switchingNickname != null) return
        scope.launch {
            _state.update { it.copy(switchingNickname = nickname, message = null) }
            val outcome = runCatching { nicknames.setActive(nickname) }
            _state.update {
                it.copy(
                    switchingNickname = null,
                    message =
                        outcome.fold(
                            onSuccess = { "Активное имя — $nickname" },
                            onFailure = { error -> error.readableMessage() },
                        ),
                )
            }
            // The worn name lives on the profile document, so the account has to be re-read for
            // the header to agree with the shelf below it.
            if (outcome.isSuccess) ensureCurrentProfile()
            refreshNicknames()
        }
    }

    /**
     * Reloads the shelf of owned names.
     *
     * A failure is recorded separately from an empty result: an account really can own nothing,
     * and showing that same blank shelf when the request never landed would be a lie.
     */
    private fun refreshNicknames() {
        scope.launch {
            _state.update { it.copy(isLoadingNicknames = true) }
            val result = runCatching { nicknames.owned() }
            _state.update { current ->
                current.copy(
                    ownedNicknames = result.getOrDefault(current.ownedNicknames),
                    isLoadingNicknames = false,
                    nicknamesUnreachable = result.isFailure,
                )
            }
        }
    }

    override fun onStartRename() {
        _state.update { current ->
            if (!current.canEditNickname) current else current.copy(isEditingNickname = true)
        }
    }

    override fun onCancelRename() {
        _state.update { it.copy(isEditingNickname = false, nicknameInput = it.profile.nickname) }
    }

    override fun onNicknameChange(value: String) {
        _state.update { it.copy(nicknameInput = value) }
    }

    override fun onSaveNickname() {
        val nickname = _state.value.nicknameInput
        scope.launch {
            _state.update { it.copy(isSaving = true, message = null) }
            val result = updateProfileNickname(nickname)
            _state.update { current ->
                result.fold(
                    onSuccess = { profile ->
                        current.withProfile(profile)
                            .copy(isSaving = false, isEditingNickname = false, message = "Ник обновлён")
                    },
                    onFailure = { error ->
                        current.copy(isSaving = false, message = error.readableMessage())
                    },
                )
            }
        }
    }

    override fun onLinkGoogle(host: AccountChooserHost) {
        if (_state.value.isLinkingGoogle) return
        scope.launch {
            _state.update { it.copy(isLinkingGoogle = true, message = null) }
            val result = linkGoogleAccount(host)
            _state.update { current ->
                current.copy(
                    isLinkingGoogle = false,
                    message =
                        result.fold(
                            onSuccess = { outcome ->
                                when (outcome) {
                                    GoogleLinkOutcome.LINKED -> "Аккаунт привязан, прогресс сохранён"
                                    // Said plainly. The alternative is somebody discovering on
                                    // their own that everything they had is no longer here.
                                    GoogleLinkOutcome.SWITCHED ->
                                        "Вход выполнен в существующий аккаунт — прогресс гостя остался на нём"
                                }
                            },
                            onFailure = { error -> error.readableMessage() },
                        ),
                )
            }
            // Whichever way it went, the uid may have changed and everything on screen is about
            // the account behind it.
            onScreenShown()
        }
    }

    override fun onScreenShown() {
        refreshNicknames()
        scope.launch {
            // Same pull as onRefresh, without the banner: only a failure is worth interrupting for.
            ensureCurrentProfile().onSuccess { profile ->
                _state.update { it.withProfile(profile) }
            }
        }
    }

    override fun onRefresh() {
        refreshNicknames()
        scope.launch {
            _state.update { it.copy(isLoading = true, message = null) }
            val result = ensureCurrentProfile()
            _state.update { current ->
                result.fold(
                    onSuccess = { profile ->
                        current.withProfile(profile).copy(isLoading = false, message = "Профиль синхронизирован")
                    },
                    onFailure = { error ->
                        current.copy(isLoading = false, message = error.readableMessage())
                    },
                )
            }
        }
    }

    override fun onMessageShown() {
        _state.update { it.copy(message = null) }
    }

    private fun ProfileUiState.withProfile(profile: UserProfile): ProfileUiState =
        copy(profile = profile, nicknameInput = profile.nickname)

    private fun Throwable.readableMessage(): String {
        if (this is CancellationException) throw this
        return message?.takeIf { it.isNotBlank() } ?: "Не удалось синхронизировать профиль"
    }
}
