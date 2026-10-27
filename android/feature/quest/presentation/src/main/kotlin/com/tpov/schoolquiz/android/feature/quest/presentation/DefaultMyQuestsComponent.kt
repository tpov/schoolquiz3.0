package com.tpov.schoolquiz.android.feature.quest.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.core.designsystem.model.toDisplayItem
import com.tpov.schoolquiz.android.feature.quest.presentation.mapper.toDisplayItem
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.use_case.ObserveCatalogsUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.Navigator
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.ObserveMyQuestsUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftSummary
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ObserveQuestDraftSummariesUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.OpenQuestDraftUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.QuoteQuestReviewFeeUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SubmitQuestDraftToArenaUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Default implementation of [MyQuestsComponent].
 *
 * Uses flatMapLatest on authRepo.observeUid() — safe for mid-session login/logout:
 *   uid=null  → flowOf(emptyList()), isGuest=true, observeMyQuests NOT called (AC#45)
 *   uid!=null → observeMyQuests(uid, catalogFilter)
 *
 * selectedCatalog is backed by InstanceKeeper (ADR-CMP-51) — survives config changes (rotation).
 * Scope is tied to ComponentContext lifecycle via doOnDestroy — cancelled on destroy.
 * mainContext is a constructor param for testability (default: Dispatchers.Main.immediate).
 *
 * Spec: docs/features/home-and-my-quests/06-api-contract.md §6.1 DefaultMyQuestsComponent
 * ADR-CMP-51: Decompose Component pattern + instanceKeeper retention.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList")
class DefaultMyQuestsComponent(
    componentContext: ComponentContext,
    private val authRepo: AuthRepository,
    private val observeMyQuests: ObserveMyQuestsUseCase,
    private val observeDraftSummaries: ObserveQuestDraftSummariesUseCase,
    private val openQuestDraft: OpenQuestDraftUseCase,
    private val observeCatalogs: ObserveCatalogsUseCase,
    private val quoteReviewFee: QuoteQuestReviewFeeUseCase,
    private val submitQuestDraftToArena: SubmitQuestDraftToArenaUseCase,
    private val navigator: Navigator,
    private val onQuestDrillDown: (QuestDisplayItem) -> Unit = {},
    mainContext: CoroutineContext = Dispatchers.Main.immediate,
) : MyQuestsComponent, ComponentContext by componentContext {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + mainContext)

    // InstanceKeeper retains selectedCatalog across config changes (rotation).
    // ADR-CMP-51: stateful UI state backed by instanceKeeper, not plain MutableStateFlow.
    private val selectedCatalogHolder: SelectedCatalogHolder =
        instanceKeeper.getOrCreate { SelectedCatalogHolder() }
    private val selectedCatalog: MutableStateFlow<CatalogId?> = selectedCatalogHolder.flow

    private val uidFlow = authRepo.observeUid()

    private val questsFlow =
        uidFlow.flatMapLatest { uid ->
            if (uid == null) {
                selectedCatalog.value = null
                flowOf(emptyList())
            } else {
                selectedCatalog.flatMapLatest { catalogId ->
                    observeMyQuests(uid, catalogId)
                }
            }
        }

    private val draftSummariesFlow =
        uidFlow.flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                combine(
                    observeDraftSummaries(uid),
                    selectedCatalog,
                ) { drafts, selectedId ->
                    drafts.filter { selectedId == null || it.catalogId == selectedId }
                }
            }
        }

    private val isGuestFlow = uidFlow.map { it == null }

    // Catalogs are public data; no uid gating required.
    private val catalogsFlow = observeCatalogs()

    // Диалог подачи с карточки (4.11) — отдельный поток состояния: главный combine и так
    // собран из пяти источников, а диалогу не нужно пересобираться вместе со списком.
    private val _submitState = MutableStateFlow<DraftSubmitState?>(null)
    override val submitState: StateFlow<DraftSubmitState?> = _submitState.asStateFlow()

    override val state =
        combine(
            questsFlow,
            draftSummariesFlow,
            catalogsFlow,
            selectedCatalog,
            isGuestFlow,
        ) { quests, drafts, catalogs, selectedId, isGuest ->
            MyQuestsUiState(
                quests = quests.map { it.toDisplayItem() },
                drafts = drafts.map { it.toDisplayItem() },
                catalogs = catalogs.map { it.toDisplayItem() },
                selectedCatalogId = selectedId,
                isGuest = isGuest,
                isLoading = false,
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = MyQuestsUiState(),
        )

    init {
        lifecycle.doOnDestroy { componentJob.cancel() }
    }

    override fun onCatalogSelected(id: CatalogId?) {
        selectedCatalog.value = id
    }

    // AC#46 / Journey 11: guest may tap FAB — navigates to OpenQuestCreate → UnderConstructionScreen.
    // No auth check here — UnderConstructionScreen is placeholder, no actual quest creation.
    // Future create-quest feature MUST re-validate auth before allowing quest submission.
    override fun onCreateQuestClick() {
        navigator.goTo(Destination.OpenQuestCreate)
    }

    override fun onQuestClick(quest: QuestDisplayItem) {
        onQuestDrillDown(quest)
    }

    /**
     * Открыть выбранный черновик.
     *
     * Раньше отсюда уходил только переход на экран создания, а сам `draft` не использовался — и
     * редактор открывал тот черновик, который случайно числился активным. С двумя черновиками
     * автор попадал не туда, куда нажал, и ничем не мог это заметить.
     *
     * Переход делается после того, как выбор записан: экран читает активный черновик при
     * открытии, и уйти на него раньше значит снова показать прежний.
     */
    override fun onDraftClick(draft: DraftQuestDisplayItem) {
        scope.launch {
            val uid = authRepo.currentUid()
            if (uid != null) {
                openQuestDraft(ownerUid = uid, draftId = draft.id)
            }
            navigator.goTo(Destination.OpenQuestCreate)
        }
    }

    /**
     * Подача с карточки (история 4.11): сначала цитата платы, диалог — только с ценой на руках.
     *
     * Повторное нажатие по той же карточке перезапрашивает цитату: тариф мог измениться между
     * отказом транспорта и повтором, а угадывать цену нельзя.
     */
    override fun onDraftSubmitClick(draft: DraftQuestDisplayItem) {
        scope.launch {
            val uid = authRepo.currentUid()
            if (uid == null) {
                // У гостя черновиков не бывает (список пуст при uid=null) — защитный no-op.
                return@launch
            }
            _submitState.value = DraftSubmitState(draft = draft, isQuoteLoading = true)
            val result = quoteReviewFee(draft.id, draft.questionCount)
            val current = _submitState.value
            // Явное чтение-запись вместо MutableStateFlow.update: диалог мог закрыться, пока
            // цитата летела, и тогда апдейтить нечего — update с null-результатом крутился бы
            // вечно.
            if (current != null && current.draft.id == draft.id) {
                _submitState.value =
                    current.copy(
                        quote = result.getOrNull(),
                        isQuoteLoading = false,
                        isQuoteFailed = result.isFailure,
                    )
            }
        }
    }

    override fun onDraftSubmitDismiss() {
        _submitState.value = null
    }

    /**
     * Подтверждённая подача: тот же use case, что и «Отправить на арену» в форме, — весь черновик
     * целиком на аренную полку (D13). Успех закрывает диалог: черновик уходит из списка
     * активных сам, когда очередь подтвердит перевод статуса.
     */
    override fun onDraftSubmitConfirm() {
        val current = _submitState.value ?: return
        if (current.quote == null || current.isSubmitting) return
        scope.launch {
            val uid = authRepo.currentUid()
            if (uid == null) {
                _submitState.value = null
                return@launch
            }
            _submitState.value = current.copy(isSubmitting = true, isSubmitFailed = false)
            val result = submitQuestDraftToArena(current.draft.id)
            if (result.isSuccess) {
                _submitState.value = null
            } else {
                val state = _submitState.value
                if (state != null && state.draft.id == current.draft.id) {
                    _submitState.value = state.copy(isSubmitting = false, isSubmitFailed = true)
                }
            }
        }
    }

    private class SelectedCatalogHolder : InstanceKeeper.Instance {
        val flow = MutableStateFlow<CatalogId?>(null)

        override fun onDestroy() = Unit
    }
}

private fun QuestDraftSummary.toDisplayItem(): DraftQuestDisplayItem =
    DraftQuestDisplayItem(
        id = id,
        catalogId = catalogId,
        title = title,
        status = status,
        questionCount = questionCount,
        isActive = isActive,
        rejectionReason = rejectionReason,
    )
