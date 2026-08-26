package com.tpov.schoolquiz.android.feature.quest.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.core.designsystem.model.toDisplayItem
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.model.QuestType
import com.tpov.schoolquiz.shared.core.catalog.domain.use_case.ObserveCatalogsUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.OpenGiftBoxUseCase
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
class DefaultHomeQuestsComponent(
    componentContext: ComponentContext,
    private val observeCatalogs: ObserveCatalogsUseCase,
    private val observeProfile: ObserveCurrentProfileUseCase,
    private val openGiftBoxUseCase: OpenGiftBoxUseCase,
    private val onCatalogDrillDown: (CatalogId, String) -> Unit,
    private val onResumeLesson: (LessonId) -> Unit,
    private val attemptRepository: LessonAttemptRepository,
    private val lessonRepository: LessonRepository,
    private val themeRepository: ThemeRepository,
    private val sectionRepository: SectionRepository,
    private val questRepository: QuestRepository,
    private val authRepository: AuthRepository,
    mainContext: CoroutineContext = Dispatchers.Main.immediate,
) : HomeQuestsComponent, ComponentContext by componentContext {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + mainContext)
    private val giftBoxOpening = MutableStateFlow<HomeGiftBoxOpeningState?>(null)

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

    override val state =
        combine(
            observeCatalogs(),
            observeProfile(),
            giftBoxOpening,
            continueLesson,
        ) { catalogs, profile, opening, resume ->
            HomeQuestsUiState(
                catalogs =
                    catalogs
                        // Courses have their own entry point, so they are not listed here.
                        .filterNot { it.questType == QuestType.COURSE }
                        .map { it.toDisplayItem() },
                giftBoxCount = profile.boxCount,
                giftBoxStreakDays = profile.boxStreakDays,
                giftBoxOpening = opening,
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
    }

    override fun onContinueClick() {
        val lessonId = state.value.continueLesson?.lessonId ?: return
        onResumeLesson(lessonId)
    }

    override fun onGiftBoxFabClick() {
        val current = state.value
        if (current.giftBoxCount <= 0 || current.giftBoxOpening is HomeGiftBoxOpeningState.Opening) return

        giftBoxOpening.value = HomeGiftBoxOpeningState.Opening(startedBoxCount = current.giftBoxCount)
        scope.launch {
            val result = openGiftBoxUseCase.execute()
            giftBoxOpening.update {
                result.fold(
                    onSuccess = { opening ->
                        val fallbackRemaining = (current.giftBoxCount - 1).coerceAtLeast(0)
                        HomeGiftBoxOpeningState.Opened(
                            reward = opening.reward,
                            remainingBoxCount = opening.remainingBoxCount ?: fallbackRemaining,
                            profileSynced = opening.profileSynced,
                        )
                    },
                    onFailure = { error ->
                        HomeGiftBoxOpeningState.Failed(
                            reason = error.toGiftBoxFailure(),
                            remainingBoxCount = current.giftBoxCount,
                        )
                    },
                )
            }
        }
    }

    override fun onGiftBoxDismiss() {
        giftBoxOpening.value = null
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
        return if (message.orEmpty().contains("No gift boxes", ignoreCase = true)) {
            HomeGiftBoxFailure.NoBoxes
        } else {
            HomeGiftBoxFailure.Unexpected(detail = message?.takeIf { it.isNotBlank() })
        }
    }
}
