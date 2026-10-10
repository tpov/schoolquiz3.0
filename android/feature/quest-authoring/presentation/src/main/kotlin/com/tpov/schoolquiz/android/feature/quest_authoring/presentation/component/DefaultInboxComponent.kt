package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.R
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.InboxUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.UiMessage
import com.tpov.schoolquiz.shared.core.network.syncErrorOrNull
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxAnchor
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ListInboxItemsUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Состояние inbox живёт сервером (AD-20): экран перечитывает view, а не кэширует его.
 */
class DefaultInboxComponent(
    componentContext: ComponentContext,
    private val authRepository: AuthRepository,
    private val listInboxItems: ListInboxItemsUseCase,
    mainContext: CoroutineDispatcher = Dispatchers.Main.immediate,
) : InboxComponent, ComponentContext by componentContext {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + mainContext)
    private val _state = MutableStateFlow(InboxUiState())
    override val state: StateFlow<InboxUiState> = _state.asStateFlow()

    private var currentUid: String? = null

    init {
        lifecycle.doOnDestroy { componentJob.cancel() }
        observeAuth()
    }

    override fun onRefreshClick() {
        reload()
    }

    override fun onLoadMoreClick() {
        val last = _state.value.entries.lastOrNull() ?: return
        if (_state.value.isLoadingMore) return
        loadPage(InboxAnchor(last.createdAtMs, last.id), isMore = true)
    }

    private fun observeAuth() {
        scope.launch {
            authRepository.observeUid().collect { uid ->
                currentUid = uid
                if (uid.isNullOrBlank()) {
                    _state.update {
                        InboxUiState(isLoading = false, errorMessage = UiMessage.Res(R.string.qa_error_auth_not_ready))
                    }
                } else {
                    reload()
                }
            }
        }
    }

    private fun reload() {
        if (currentUid.isNullOrBlank()) return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        loadPage(after = null, isMore = false)
    }

    private fun loadPage(
        after: InboxAnchor?,
        isMore: Boolean,
    ) {
        scope.launch {
            _state.update { it.copy(isLoadingMore = isMore) }
            val result = listInboxItems(pageSize = DEFAULT_PAGE_SIZE, after = after)
            _state.update { state ->
                result.fold(
                    onSuccess = { page ->
                        state.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            errorMessage = null,
                            entries = if (isMore) state.entries + page.entries else page.entries,
                            canLoadMore = page.nextAnchor != null,
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            errorMessage = messageOf(error),
                            canLoadMore = false,
                        )
                    },
                )
            }
        }
    }

    private fun messageOf(error: Throwable): UiMessage =
        error.syncErrorOrNull()?.let { UiMessage.Raw(it.toString()) }
            ?: UiMessage.Res(R.string.qa_lifecycle_error_unknown)

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
