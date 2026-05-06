package com.tpov.schoolquiz.android.feature.internet.profile.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate.ProfileUiState
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.EnsureCurrentProfileUseCase
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.ObserveCurrentProfileUseCase
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
                    val keepEditing = current.nicknameInput != current.profile.nickname && current.canEditNickname
                    current.copy(
                        profile = profile,
                        nicknameInput = if (keepEditing) current.nicknameInput else profile.nickname,
                        isLoading = false,
                    )
                }
            }
        }
        onRefresh()
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
                        current.withProfile(profile).copy(isSaving = false, message = "Ник обновлён")
                    },
                    onFailure = { error ->
                        current.copy(isSaving = false, message = error.readableMessage())
                    },
                )
            }
        }
    }

    override fun onRefresh() {
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
