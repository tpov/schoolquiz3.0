package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.R
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.AuthorLifecycleUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.UiMessage
import com.tpov.schoolquiz.shared.core.network.syncErrorOrNull
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ListCuratorLifecycleUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ListLessonHistoryUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ListLessonRoyaltyUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ListOwnedLifecycleUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Автор перечитывает свои проекции при каждом открытии экрана; кэша у статусов нет (AD-25). */
class DefaultAuthorLifecycleComponent(
    componentContext: ComponentContext,
    private val authRepository: AuthRepository,
    private val listOwned: ListOwnedLifecycleUseCase,
    private val listHistory: ListLessonHistoryUseCase,
    private val listRoyalty: ListLessonRoyaltyUseCase,
    mainContext: CoroutineDispatcher = Dispatchers.Main.immediate,
) : AuthorLifecycleComponent, ComponentContext by componentContext {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + mainContext)
    private val _state = MutableStateFlow(AuthorLifecycleUiState())
    override val state: StateFlow<AuthorLifecycleUiState> = _state.asStateFlow()
    private var currentUid: String? = null

    init {
        lifecycle.doOnDestroy { componentJob.cancel() }
        scope.launch {
            authRepository.observeUid().collect { uid ->
                currentUid = uid
                if (uid.isNullOrBlank()) {
                    _state.update {
                        AuthorLifecycleUiState(
                            isLoading = false,
                            errorMessage = UiMessage.Res(R.string.qa_error_auth_not_ready),
                        )
                    }
                } else {
                    reload()
                }
            }
        }
    }

    override fun onRefreshClick() {
        reload()
    }

    override fun onLoadMoreClick() {
        val after = _state.value.entries.lastOrNull()?.lessonId ?: return
        if (_state.value.isLoadingMore) return
        loadPage(after, isMore = true)
    }

    override fun onLessonSelected(lessonId: String) {
        _state.update { it.copy(selectedLessonId = lessonId, historyIsLoading = true) }
        loadHistory(lessonId)
    }

    private fun reload() {
        if (currentUid.isNullOrBlank()) return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        loadPage(after = null, isMore = false)
        scope.launch {
            val uid = currentUid ?: return@launch
            val result = listRoyalty(uid, null, ListCuratorLifecycleUseCase.DEFAULT_PAGE_SIZE)
            _state.update { state ->
                result.fold(
                    onSuccess = { page -> state.copy(royalty = page.summary) },
                    onFailure = { error ->
                        // Роялти — довесок к статусам: его отказ не гасит основную страницу.
                        if (state.entries.isEmpty() && !state.isLoading) {
                            state.copy(errorMessage = messageOf(error))
                        } else {
                            state
                        }
                    },
                )
            }
        }
    }

    private fun loadPage(
        after: String?,
        isMore: Boolean,
    ) {
        val uid = currentUid ?: return
        scope.launch {
            _state.update { it.copy(isLoadingMore = isMore) }
            val result = listOwned(uid, after, ListCuratorLifecycleUseCase.DEFAULT_PAGE_SIZE)
            _state.update { state ->
                result.fold(
                    onSuccess = { page ->
                        state.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            errorMessage = null,
                            entries = if (isMore) state.entries + page.entries else page.entries,
                            canLoadMore = page.hasNext,
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            errorMessage = messageOf(error),
                        )
                    },
                )
            }
        }
    }

    private fun loadHistory(lessonId: String) {
        val uid = currentUid ?: return
        scope.launch {
            val result = listHistory(uid, lessonId, null, ListCuratorLifecycleUseCase.DEFAULT_PAGE_SIZE)
            _state.update { state ->
                if (state.selectedLessonId != lessonId) return@update state
                result.fold(
                    onSuccess = { page -> state.copy(historyIsLoading = false, history = page.entries) },
                    onFailure = { error -> state.copy(historyIsLoading = false, errorMessage = messageOf(error)) },
                )
            }
        }
    }

    private fun messageOf(error: Throwable): UiMessage =
        error.syncErrorOrNull()?.let { UiMessage.Raw(it.toString()) }
            ?: UiMessage.Res(R.string.qa_lifecycle_error_unknown)
}
