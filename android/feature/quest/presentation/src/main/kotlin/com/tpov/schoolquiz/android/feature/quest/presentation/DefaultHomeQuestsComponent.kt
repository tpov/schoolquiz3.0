package com.tpov.schoolquiz.android.feature.quest.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.essenty.lifecycle.doOnResume
import com.tpov.schoolquiz.android.core.designsystem.model.toDisplayItem
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.model.QuestType
import com.tpov.schoolquiz.shared.core.catalog.domain.use_case.ObserveCatalogsUseCase
import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxFailureReason
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxOpening
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxOpeningException
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PendingGiftBoxOpening
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.ClaimPremiumDailyGiftUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.OpenGiftBoxUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.SyncBoxStreakUseCase
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.ObserveCurrentProfileUseCase
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/** A completed lesson has nothing to continue; the card only speaks about unfinished business. */
private const val PERFECT_SCORE = 100

/**
 * Default implementation of [HomeQuestsComponent].
 *
 * Observes all non-archived catalogs via ObserveCatalogsUseCase (DAO WHERE archived=0 already
 * filters at the data layer). Maps to CatalogDisplayItem for UI consumption.
 *
 * Scope lifecycle tied to ComponentContext via doOnDestroy.
 *
 * Spec: docs/features/home-and-my-quests/06-api-contract.md §6.2 DefaultHomeQuestsComponent
 * ADR-CMP-51: Decompose Component pattern.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DefaultHomeQuestsComponent(
    componentContext: ComponentContext,
    private val observeCatalogs: ObserveCatalogsUseCase,
    private val observeProfile: ObserveCurrentProfileUseCase,
    private val openGiftBoxUseCase: OpenGiftBoxUseCase,
    private val syncBoxStreak: SyncBoxStreakUseCase,
    private val claimPremiumDailyGift: ClaimPremiumDailyGiftUseCase,
    private val networkMonitor: NetworkMonitor,
    private val onCatalogDrillDown: (CatalogId, String) -> Unit,
    private val onResumeLesson: (LessonId) -> Unit,
    private val attemptRepository: LessonAttemptRepository,
    private val lessonRepository: LessonRepository,
    private val themeRepository: ThemeRepository,
    private val sectionRepository: SectionRepository,
    private val questRepository: QuestRepository,
    private val authRepository: AuthRepository,
    mainContext: CoroutineContext = Dispatchers.Main.immediate,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : HomeQuestsComponent, ComponentContext by componentContext {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + mainContext)
    private val giftBoxOpening = MutableStateFlow<HomeGiftBoxOpeningState?>(null)
    private val pendingGiftBox = MutableStateFlow<PendingGiftBoxOpening?>(null)
    private var giftBoxRequest: Job? = null
    private var premiumGiftRequest: Job? = null
    private var premiumIntentPending = false
    private var streakSync: Job? = null

    private val continueLesson = MutableStateFlow<ContinueLessonUi?>(null)

    init {
        lifecycle.doOnDestroy { componentJob.cancel() }
        scope.launch {
            authRepository.observeUid().collect { uid ->
                if (uid == null) {
                    continueLesson.value = null
                } else {
                    attemptRepository.observeAllByUser(uid).collect { attempts ->
                        continueLesson.value = resolveContinueLesson(attempts)
                    }
                }
            }
        }
    }

    private suspend fun resolveContinueLesson(attempts: List<Attempt>): ContinueLessonUi? {
        val latest = attempts.maxByOrNull { it.completedAt } ?: return null
        if (latest.percentScore.raw >= PERFECT_SCORE) return null
        val lesson = lessonRepository.getById(latest.lessonId) ?: return null
        val theme = lesson.themeId.let { themeRepository.getById(it) }
        val section = theme?.sectionId?.let { sectionRepository.getById(it) } ?: return null
        val quest = section.questId.let { questRepository.getById(it) }
        val path = listOfNotNull(quest?.title, section.title, theme?.title).joinToString(" › ")

        val sectionLessons =
            themeRepository.observeBySection(section.id).first()
                .flatMap { themeOfSection -> lessonRepository.observeByTheme(themeOfSection.id).first() }
        val completedIds =
            attempts.asSequence()
                .filter { it.percentScore.raw >= PERFECT_SCORE }
                .mapTo(mutableSetOf()) { it.lessonId }
        return ContinueLessonUi(
            lessonId = latest.lessonId,
            title = lesson.title,
            path = path,
            lessonSegments =
                sectionLessons.map { sectionLesson ->
                    LessonSegmentUi(
                        lessonId = sectionLesson.id,
                        title = sectionLesson.title,
                        completed = sectionLesson.id in completedIds,
                        isCurrent = sectionLesson.id == latest.lessonId,
                    )
                },
        )
    }

    /**
     * How many quests each catalogue holds, kept alongside the catalogues themselves.
     *
     * One query per catalogue rather than a count on the catalogue row: the number is what is on
     * the home shelf right now, and a stored total would drift every time a quest was published,
     * hidden or archived.
     */
    private val questCounts =
        observeCatalogs()
            .flatMapLatest { catalogs ->
                val listed = catalogs.filterNot { it.questType == QuestType.COURSE }
                if (listed.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(
                        listed.map { catalog ->
                            questRepository
                                .observeByCatalog(catalog.id, HOME_SHELF)
                                .map { quests -> catalog.id to quests.size }
                        },
                    ) { pairs -> pairs.toMap() }
                }
            }

    private val premiumGift =
        combine(
            observeProfile(),
            claimPremiumDailyGift.observePending(),
        ) { profile, pending ->
            premiumGiftState(profile, pending)
        }

    override val state =
        combine(
            observeCatalogs(),
            observeProfile(),
            // combine ходит парами до пяти потоков: подарочные состояния свёрнуты заранее.
            combine(
                combine(giftBoxOpening, pendingGiftBox) { opening, pending -> opening to pending },
                premiumGift,
            ) { opening, gift -> opening to gift },
            continueLesson,
            questCounts,
        ) { catalogs, profile, opening, resume, counts ->
            HomeQuestsUiState(
                catalogs =
                    catalogs
                        // Courses have their own entry point, so they are not listed here.
                        .filterNot { it.questType == QuestType.COURSE }
                        .map { catalog ->
                            catalog.toDisplayItem().copy(
                                questCountLabel = counts[catalog.id]?.let(::questCountWording),
                            )
                        },
                giftBoxCount = profile.boxCount,
                giftBoxStreakDays = profile.boxStreakDays,
                giftBoxOpening = opening.first.first,
                hasPendingGiftBoxOpening = opening.first.second != null,
                premiumGift = opening.second,
                continueLesson = resume,
            )
        }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = HomeQuestsUiState(),
            )

    init {
        lifecycle.doOnDestroy { componentJob.cancel() }
        // Входная синхронизация серии (7.4): на каждом возврате на главный экран проход
        // офлайн-заявка → визит → перечитывание начинается заново, пока предыдущий не кончился.
        lifecycle.doOnResume {
            if (streakSync?.isActive != true) {
                streakSync =
                    scope.launch {
                        // Смерть процесса между отправкой и ответом закрывает сам репозиторий:
                        // повтор уезжает тем же operationId раньше любого нового визита.
                        syncBoxStreak()
                    }
            }
            // Возврат на экран доигрывает лежащее намерение и без смени сети (таймаут, не офлайн):
            // re-arm (retro e7 F2) — иначе кнопка жила бы «забираем…» до конца сессии. Условие —
            // последнее НАБЛЮДЕННОЕ значение намерения, не презентационное состояние: Essenty
            // проигрывает doOnResume при регистрации, и застройка по state стреляла бы на
            // конструкции, в том числе без сети.
            if (premiumIntentPending) {
                rearmPremiumGift()
            }
        }
    }

    init {
        lifecycle.doOnDestroy { componentJob.cancel() }
        scope.launch {
            openGiftBoxUseCase.observePending().collectLatest { pending ->
                val previousOwner = pendingGiftBox.value?.ownerUid
                pendingGiftBox.value = pending
                if (pending == null || (previousOwner != null && previousOwner != pending.ownerUid)) {
                    giftBoxRequest?.cancel()
                    giftBoxOpening.value = null
                }
                if (pending != null && giftBoxRequest?.isActive != true &&
                    giftBoxOpening.value !is HomeGiftBoxOpeningState.Opening
                ) {
                    startGiftBox(resume = true)
                }
            }
        }
    }

    init {
        lifecycle.doOnDestroy { componentJob.cancel() }
        // Лежащее намерение премь-подарка возобновляется само: процесс мог умереть между
        // отправкой и ответом (AC процесса-смерти 7.4), а кнопка в состоянии InFlight тапа не
        // принимает — без этого прохода «забираем…» застревало навсегда. Новая заявка этим путём
        // не создаётся: invoke сперва resumePending и только при отсутствии намерения — submit.
        //
        // Re-arm (retro e7 F2): первая же неудачная попытка (офлайн/таймаут) оставляла
        // pending=true, distinctUntilChanged больше не эммитил, и повтор не происходил никогда.
        // Теперь доигрывание стреляет и на возвращении сети — до появления сети попытка не
        // начинается вовсе, бессмысленного круга нет.
        scope.launch {
            combine(
                claimPremiumDailyGift.observePending(),
                networkMonitor.observeOnline(),
            ) { pending, online -> pending to online }
                .collect { (pending, online) ->
                    premiumIntentPending = pending
                    if (pending && online) {
                        rearmPremiumGift()
                    }
                }
        }
    }

    /** Доигрывает лежащее намерение; параллельного запуска не бывает — один активный запрос. */
    private fun rearmPremiumGift() {
        if (premiumGiftRequest?.isActive == true) return
        premiumGiftRequest = scope.launch { claimPremiumDailyGift() }
    }

    override fun onContinueClick() {
        val lessonId = state.value.continueLesson?.lessonId ?: return
        onResumeLesson(lessonId)
    }

    override fun onGiftBoxFabClick() {
        val current = state.value
        if ((current.giftBoxCount <= 0 && !current.hasPendingGiftBoxOpening) || giftBoxRequest?.isActive == true) return
        startGiftBox(resume = current.hasPendingGiftBoxOpening)
    }

    private fun startGiftBox(resume: Boolean) {
        val count = state.value.giftBoxCount
        giftBoxOpening.value = HomeGiftBoxOpeningState.Opening(startedBoxCount = count)
        giftBoxRequest =
            scope.launch {
                val owner = authRepository.currentUid()
                val result = if (resume) openGiftBoxUseCase.resumePending() else openGiftBoxUseCase.execute()
                if (authRepository.currentUid() != owner) return@launch
                if (result == null) {
                    giftBoxOpening.value = null
                    return@launch
                }
                val receiptOwner =
                    result.getOrNull()?.ownerUid
                        ?: (result.exceptionOrNull() as? GiftBoxOpeningException)?.ownerUid
                if (receiptOwner != null && receiptOwner != owner) return@launch
                showGiftBoxResult(result, count)
            }
    }

    private fun showGiftBoxResult(
        result: Result<GiftBoxOpening>,
        previousCount: Int,
    ) {
        giftBoxOpening.value =
            result.fold(
                onSuccess = { opening ->
                    HomeGiftBoxOpeningState.Opened(
                        reward = opening.reward,
                        // A recovered old reward must not subtract from today's profile count.
                        remainingBoxCount = opening.remainingBoxCount ?: state.value.giftBoxCount,
                        profileSynced = opening.profileSynced,
                        openingId = opening.openingId,
                    )
                },
                onFailure = { error ->
                    HomeGiftBoxOpeningState.Failed(
                        error.toGiftBoxFailure(),
                        previousCount,
                        (error as? GiftBoxOpeningException)?.openingId,
                    )
                },
            )
    }

    override fun onGiftBoxDismiss() {
        val shown = giftBoxOpening.value
        if (shown is HomeGiftBoxOpeningState.Opening) return
        val id =
            when (shown) {
                is HomeGiftBoxOpeningState.Opened -> shown.openingId
                is HomeGiftBoxOpeningState.Failed -> shown.openingId
                else -> null
            }
        if (id == null) {
            giftBoxOpening.value = null
            return
        }
        scope.launch {
            openGiftBoxUseCase.acknowledge(id)
            if (giftBoxOpening.value == shown) giftBoxOpening.value = null
            // An uncertain/invalid receipt stays marked, but must not trap navigation behind an overlay.
        }
    }

    override fun onPremiumGiftClaim() {
        // Заявка идёт только из доступного состояния; повтор тапа уезжает тем же operationId.
        if (state.value.premiumGift !is HomePremiumGiftState.Available) return
        if (premiumGiftRequest?.isActive == true) return
        premiumGiftRequest = scope.launch { claimPremiumDailyGift() }
    }

    /** Кнопка из серверных слов: активен ли срок и свободен ли суточный слот. */
    private fun premiumGiftState(
        profile: UserProfile,
        pending: Boolean,
    ): HomePremiumGiftState =
        when {
            pending -> HomePremiumGiftState.InFlight
            !profile.premiumActiveAt(nowMs()) -> HomePremiumGiftState.Hidden
            profile.dailyGiftFreeAt(nowMs()) -> HomePremiumGiftState.Available
            else -> HomePremiumGiftState.ClaimedToday
        }

    override fun onCatalogClick(
        id: CatalogId,
        name: String,
    ) {
        // Blank names are forwarded as-is; the screen resolves them to a localized fallback.
        onCatalogDrillDown(id, name)
    }

    private fun Throwable.toGiftBoxFailure(): HomeGiftBoxFailure {
        if (this is CancellationException) throw this
        return when ((this as? GiftBoxOpeningException)?.reason) {
            GiftBoxFailureReason.NO_BOXES -> HomeGiftBoxFailure.NoBoxes
            GiftBoxFailureReason.OFFLINE -> HomeGiftBoxFailure.NetworkRequired
            GiftBoxFailureReason.PENDING -> HomeGiftBoxFailure.Pending
            GiftBoxFailureReason.RECEIPT_INVALID, GiftBoxFailureReason.REQUIRES_ATTENTION ->
                HomeGiftBoxFailure.RequiresAttention
            else -> HomeGiftBoxFailure.Unexpected(detail = null)
        }
    }
}

/** The shelf the home screen shows; quests hidden from it must not be counted into it. */
private const val HOME_SHELF = "home"

/**
 * "1 квест", "2 квеста", "5 квестов" — Russian counts three ways.
 *
 * The teens are the case naive versions get wrong: eleven takes the same form as five.
 */
private fun questCountWording(count: Int): String {
    val tail = count % 100
    val last = count % 10
    val teens = tail in 11..14
    val noun =
        when {
            teens -> "квестов"
            last == 1 -> "квест"
            last in 2..4 -> "квеста"
            else -> "квестов"
        }
    return "$count $noun"
}
