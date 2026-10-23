package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyLevel
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
import com.tpov.schoolquiz.shared.core.catalog.domain.model.QuestType
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.model.LessonUnlockKind
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.logic.LessonAccess
import com.tpov.schoolquiz.shared.feature.lesson.domain.logic.resolveLessonAccess
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonUnlockOutcome
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonUnlockQueue
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptStats
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Suppress("LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultLessonListComponent(
    componentContext: ComponentContext,
    config: QuizzesConfig.LessonList,
    private val lessonRepository: LessonRepository,
    private val attemptRepository: LessonAttemptRepository,
    private val authRepository: AuthRepository,
    private val economyRepository: EconomyRepository,
    /**
     * Постановка разблокировки в общую очередь — порт фичи, которой принадлежит операция.
     *
     * Обязательный параметр, а не заглушка со значением по умолчанию: молчаливый no-op здесь
     * означал бы «купил и ничего не произошло», и отличить его от исправной очереди было бы нечем.
     */
    private val queueLessonUnlock: LessonUnlockQueue,
    private val navigation: StackNavigation<QuizzesConfig>,
    /** Цена открытия каждого урока, в ноликах. По умолчанию замок остаётся без числа. */
    private val observeUnlockPrices: (List<LessonId>) -> Flow<Map<LessonId, Int>> = { flowOf(emptyMap()) },
    private val lessonContentSync: suspend (LessonId) -> Result<Unit> = { Result.success(Unit) },
    coroutineContext: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ComponentContext by componentContext, LessonListComponent {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + coroutineContext)

    private val themeId = ThemeId(config.themeId)
    override val breadcrumbs: List<BreadcrumbRoot> = config.breadcrumbs
    private val forcedLessonMode = config.forcedLessonMode

    // Only a course teaches in a fixed order. Everywhere else every lesson stays open.
    private val gatesSequentially = config.questType == QuestType.COURSE

    private val _uiState = MutableValue<LessonListUiState>(LessonListUiState.Loading)
    override val uiState: Value<LessonListUiState> = _uiState

    private val hardCheckedSet: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())

    /** Уроки, лежащие сейчас на экране: заголовок нужен, чтобы назвать урок в объяснении. */
    private val shownLessons: MutableStateFlow<Map<LessonId, String>> = MutableStateFlow(emptyMap())

    /** О каких отказах игроку уже сказали — чтобы не повторять одно и то же на каждой эмиссии. */
    private val announcedRefusals = mutableSetOf<LessonId>()

    /** Course lessons are warmed once when they first become the next playable lesson. */
    private val warmedCourseLessons = mutableSetOf<LessonId>()

    private val _messages = Channel<String>(Channel.BUFFERED)
    override val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        val statsFlow =
            authRepository.observeUid().flatMapLatest { uid ->
                if (uid == null) {
                    flowOf(emptyMap())
                } else {
                    attemptRepository.observeAllStatsByUser(uid)
                }
            }
        val unlocksFlow = economyRepository.observeBalance().map { it.lessonUnlocks }
        scope.launch {
            lessonRepository.observeByTheme(themeId).flatMapLatest { lessons ->
                // Цена нужна только там, где урок вообще может быть заперт. Вне курса подписки на
                // вопросы всех уроков темы были бы работой ради числа, которое негде показать.
                val pricesFlow =
                    if (gatesSequentially) observeUnlockPrices(lessons.map { it.id }) else flowOf(emptyMap())
                combine(
                    statsFlow,
                    hardCheckedSet,
                    unlocksFlow,
                    pricesFlow,
                ) { stats, checkedSet, unlocks, prices ->
                    mapToUi(
                        lessons = lessons,
                        stats = stats,
                        checkedSet = checkedSet,
                        unlocks = unlocks,
                        prices = prices,
                    )
                }
            }
                .catch { /* log */ }
                .collect {
                    _uiState.value = it
                    val loadedLessons = (it as? LessonListUiState.Loaded)?.items
                    shownLessons.value =
                        loadedLessons
                            ?.associate { item -> LessonId(item.id) to item.title }
                            .orEmpty()
                    if (gatesSequentially) {
                        val nextPlayableLesson =
                            loadedLessons
                                ?.firstOrNull { item -> item.access != LessonAccess.LOCKED }
                                ?.let { item -> LessonId(item.id) }
                        if (nextPlayableLesson != null && warmedCourseLessons.add(nextPlayableLesson)) {
                            scope.launch(backgroundDispatcher) {
                                try {
                                    lessonContentSync(nextPlayableLesson)
                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    // The runner's cold-cache path remains the user-visible fallback.
                                }
                            }
                        }
                    }
                }
        }
        // Покупка, ушедшая в карантин, откачена фичей lesson: нолики вернулись, урок закрылся
        // (AD-28). Молчания тут быть не должно — игрок увидел бы, как открытый урок сам закрылся.
        // Источник — карантинная строка очереди, поэтому объяснение переживает и закрытие экрана,
        // и перезапуск приложения, а не гаснет вместе с сообщением.
        scope.launch {
            authRepository.observeUid()
                .flatMapLatest { uid ->
                    if (uid == null) {
                        flowOf(emptySet())
                    } else {
                        queueLessonUnlock.observeRefusedUnlocks(uid)
                    }
                }
                .distinctUntilChanged()
                .combine(shownLessons) { refused, shown -> refused to shown }
                .catch { /* log */ }
                .collect { (refused, shown) -> announceRefusals(refused, shown) }
        }
        lifecycle.doOnDestroy {
            componentJob.cancel()
            hardCheckedSet.value = emptySet()
        }
    }

    override fun onLessonEarningsClick(lesson: LessonItemUi) {
        // «Начисления» урока (AD-30/UX-20, история 8.6): read-only проекция в стеке квизов.
        navigation.pushNew(
            QuizzesConfig.LessonEarnings(
                lessonId = lesson.id,
                title = lesson.title,
                breadcrumbs = breadcrumbs + BreadcrumbRoot.Dynamic(lesson.title),
            ),
        )
    }

    override fun onLessonClick(lesson: LessonItemUi) {
        // A shut lesson does not open by being tapped; the tap is a request to buy it.
        if (lesson.access == LessonAccess.LOCKED) {
            onUnlockClick(lesson)
            return
        }
        val mode =
            forcedLessonMode ?: if (lesson.hardUnlocked && lesson.isHardChecked) {
                Difficulty.HARD
            } else {
                Difficulty.EASY
            }
        // AC-49: each new visit defaults to unchecked — clear before pushing runner.
        hardCheckedSet.update { it - lesson.id }
        navigation.pushNew(
            QuizzesConfig.LessonRunner(
                lessonId = lesson.id,
                mode = mode,
                breadcrumbs = breadcrumbs + BreadcrumbRoot.Dynamic(lesson.title),
            ),
        )
    }

    /**
     * Разблокировка списывает нолики, открывает урок и кладёт намерение — одной транзакцией.
     *
     * Прямой вызов был отказом без сети: игрок в авиарежиме получал ошибку вместо отложенного
     * действия (AD-3, AD-6). Урок открывается сразу — решение владельца от 2026-09-03: нолики
     * мягкая валюта, и AD-25 на них не распространяется; списание и открытие ложатся в базу тем же
     * движением, что и запись очереди (AD-23). Второе нажатие замка на экране не требует: ключ
     * намерения выводится из тройки «владелец, вид, урок», и повтор возвращает ту же строку (AD-2).
     */
    override fun onUnlockClick(lesson: LessonItemUi) {
        if (lesson.access != LessonAccess.LOCKED) return
        // Цена, которой локально нет, — это урок, чьи вопросы ещё не доехали (история 5.2). Такую
        // разблокировку не откладывают: названная сервером цена разошлась бы с невиданной. Причина
        // называется своя, а не «нет связи»: связь тут ни при чём.
        val price = lesson.unlockPriceNolics
        if (price == null) {
            _messages.trySend(UNKNOWN_PRICE_MESSAGE)
            return
        }
        scope.launch {
            val uid = authRepository.currentUid()
            if (uid == null) {
                _messages.trySend(SIGNED_OUT_MESSAGE)
                return@launch
            }
            val lessonId = LessonId(lesson.id)
            val queued =
                queueLessonUnlock.queueUnlock(
                    ownerUid = uid,
                    lessonId = lessonId,
                    kindWireName = LessonUnlockKind.LESSON.wireName,
                    priceNolics = price,
                )
            // Повторное нажатие по откаченной покупке — новое намерение: карантинная строка снята
            // транзакцией, и объяснять прежний отказ больше нечего.
            announcedRefusals -= lessonId
            _messages.trySend(queued.messageFor(lesson.title))
        }
    }

    /** Что сказать игроку об исходе нажатия. Отказ по балансу называет своё число, а не «нет сети». */
    private fun Result<LessonUnlockOutcome>.messageFor(title: String): String =
        fold(
            onSuccess = { outcome ->
                when (outcome) {
                    LessonUnlockOutcome.Opened -> OPENED_MESSAGE
                    LessonUnlockOutcome.AlreadyOpen -> ALREADY_OPEN_MESSAGE
                    LessonUnlockOutcome.NoProfile -> NO_PROFILE_MESSAGE
                    is LessonUnlockOutcome.NotEnoughNolics ->
                        "Не хватает ноликов: урок «$title» стоит ${outcome.priceNolics}, " +
                            "у вас ${outcome.balanceNolics}"
                }
            },
            onFailure = { QUEUE_FAILED_MESSAGE },
        )

    /**
     * Объясняет откат покупки — по одному разу на отказ.
     *
     * Отказы приходят по всем урокам аккаунта; называются только те, что игрок видит перед собой.
     * Ушедший из набора отказ забывается: значит, строка снята, и следующий отказ по тому же уроку
     * будет новым.
     */
    private fun announceRefusals(
        refused: Set<LessonId>,
        shown: Map<LessonId, String>,
    ) {
        announcedRefusals.retainAll(refused)
        refused.forEach { lessonId ->
            val title = shown[lessonId] ?: return@forEach
            if (announcedRefusals.add(lessonId)) {
                _messages.trySend("Покупка урока «$title» не прошла: нолики возвращены, урок снова закрыт")
            }
        }
    }

    override fun onHardCheckToggled(lessonId: String) {
        val item = (_uiState.value as? LessonListUiState.Loaded)?.items?.find { it.id == lessonId }
        if (item?.hardUnlocked == true) {
            hardCheckedSet.update { current ->
                if (lessonId in current) current - lessonId else current + lessonId
            }
        }
    }

    private fun mapToUi(
        lessons: List<Lesson>,
        stats: Map<LessonId, LessonAttemptStats>,
        checkedSet: Set<String>,
        unlocks: Set<String>,
        prices: Map<LessonId, Int>,
    ): LessonListUiState {
        if (lessons.isEmpty()) return LessonListUiState.Empty(HierarchyLevel.LESSONS)
        val ordered = lessons.sortedBy { it.order }
        val purchased =
            ordered
                .map { it.id }
                .filterTo(mutableSetOf()) { LessonUnlockKind.LESSON.keyFor(it.value) in unlocks }
        val access =
            if (gatesSequentially) {
                resolveLessonAccess(
                    orderedLessonIds = ordered.map { it.id },
                    // hardUnlocked is the all-easy-correct predicate, which is what "passed" means.
                    passed = stats.filterValues { it.hardUnlocked }.keys,
                    purchased = purchased,
                )
            } else {
                emptyMap()
            }
        val items =
            ordered.map { lesson ->
                val lessonStats = stats[lesson.id]
                LessonItemUi(
                    id = lesson.id.value,
                    title = lesson.title,
                    orderLabel = "${lesson.order + 1}.",
                    averageRating = lesson.averageRating,
                    ratingCount = lesson.ratingCount,
                    bestStarsRawTenths = lessonStats?.bestStarsRawTenths ?: 0,
                    // Buying a lesson buys the whole lesson, both difficulties — it is priced as
                    // both, so it opens both. Earning it still works the usual way.
                    hardUnlocked =
                        lessonStats?.hardUnlocked == true ||
                            lesson.id in purchased ||
                            LessonUnlockKind.HARD_MODE.keyFor(lesson.id.value) in unlocks,
                    isHardChecked = lesson.id.value in checkedSet,
                    access = access[lesson.id] ?: LessonAccess.OPEN,
                    // Урок, чьи вопросы ещё не доехали, цены не получает: замок без числа честнее,
                    // чем число, которое разойдётся с тем, что спишет сервер.
                    unlockPriceNolics = prices[lesson.id],
                )
            }
        return LessonListUiState.Loaded(items)
    }
}

/** Урок, чьи вопросы ещё не доехали: цены нет, и откладывать нечего. */
private const val UNKNOWN_PRICE_MESSAGE =
    "Цена этого урока пока неизвестна: вопросы урока ещё не загружены"

/** Очередь принадлежит аккаунту (AD-8): без него класть запись некуда. */
private const val SIGNED_OUT_MESSAGE = "Войдите в аккаунт, чтобы открывать уроки"

/** Урок открыт прямо сейчас; на сервер покупка уедет сама, когда появится связь. */
private const val OPENED_MESSAGE = "Урок открыт"

/** Второе нажатие по уже открытому уроку: ни второго списания, ни второй записи (AD-2). */
private const val ALREADY_OPEN_MESSAGE = "Этот урок уже открыт"

/** Баланса на устройстве ещё нет — списывать не с чего, и это тоже не «нет сети». */
private const val NO_PROFILE_MESSAGE = "Профиль ещё не загружен: баланс ноликов пока неизвестен"

private const val QUEUE_FAILED_MESSAGE = "Не удалось поставить покупку в очередь"
