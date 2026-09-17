package com.tpov.schoolquiz.android.feature.internet.profile.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.instancekeeper.InstanceKeeperDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate.AccountSwitchCount
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate.AccountSwitchCountKind
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate.AccountSwitchWarning
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate.ProfileMessage
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.AccountChooserHost
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.AccountDataKind
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.AccountSwitchLoss
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.GoogleLinkOutcome
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.LeagueStanding
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameAvailability
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.OwnedNickname
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileStatus
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ListingCancelSync
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ActivityRepository
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.GoogleSignInRepository
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.NicknameRepository
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.ConfirmAccountSwitchUseCase
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.DeclineAccountSwitchUseCase
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.EnsureCurrentProfileUseCase
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.GetLeagueStandingUseCase
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.LinkGoogleAccountUseCase
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.ObserveCurrentProfileUseCase
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.ObserveDailyActivityUseCase
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.UpdateProfileNicknameUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Предупреждение перед сменой аккаунта — оно же согласие (AD-8, история 6.4).
 *
 * Вопрос показывается ровно тогда, когда у прежнего аккаунта осталось неотправленное; отказ не
 * трогает ничего; подтверждение доводит переключение до конца через репозиторий, и только он
 * снимает следы. До ответа `uid` не меняется, а кнопка входа остаётся занятой.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultProfileComponentAccountSwitchTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var lifecycle: LifecycleRegistry

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        lifecycle = LifecycleRegistry()
        lifecycle.resume()
    }

    @After
    fun tearDown() {
        lifecycle.destroy()
        Dispatchers.resetMain()
    }

    private val host = object : AccountChooserHost {}

    private fun loss(
        waiting: Int = 0,
        waitingPrecondition: Int = 0,
        conflicted: Int = 0,
        quarantined: Int = 0,
        synchronous: Int = 0,
        isKnown: Boolean = true,
        namedData: List<AccountDataKind> = listOf(AccountDataKind.ANSWER_HISTORY),
    ) = AccountSwitchLoss(
        waiting = waiting,
        waitingPrecondition = waitingPrecondition,
        conflicted = conflicted,
        quarantined = quarantined,
        synchronous = synchronous,
        isKnown = isKnown,
        namedData = namedData,
    )

    private val profiles = FakeProfileRepository()

    private fun component(
        signIn: FakeGoogleSignInRepository,
        lifecycle: LifecycleRegistry = this.lifecycle,
        instanceKeeper: InstanceKeeperDispatcher = InstanceKeeperDispatcher(),
    ): DefaultProfileComponent =
        DefaultProfileComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle, instanceKeeper = instanceKeeper),
            useCases =
                ProfileUseCases(
                    observeCurrentProfile = ObserveCurrentProfileUseCase(profiles),
                    ensureCurrentProfile = EnsureCurrentProfileUseCase(profiles),
                    updateProfileNickname = UpdateProfileNicknameUseCase(profiles),
                    observeDailyActivity = ObserveDailyActivityUseCase(FakeActivityRepository()),
                    linkGoogleAccount = LinkGoogleAccountUseCase(signIn),
                    confirmAccountSwitch = ConfirmAccountSwitchUseCase(signIn),
                    declineAccountSwitch = DeclineAccountSwitchUseCase(signIn),
                    getLeagueStanding = GetLeagueStandingUseCase(profiles),
                ),
            nicknames = FakeNicknameRepository(),
        )

    @Test
    fun `given unsent work then the question is shown, nothing is confirmed, and the button stays busy`() = runTest {
        val signIn =
            FakeGoogleSignInRepository(
                GoogleLinkOutcome.SwitchAwaitsConsent(loss(waiting = 3, conflicted = 1)),
            )
        val component = component(signIn)

        component.onLinkGoogle(host)

        val state = component.state.value
        assertEquals(
            AccountSwitchWarning(
                counts =
                    listOf(
                        AccountSwitchCount(AccountSwitchCountKind.WAITING, 3),
                        AccountSwitchCount(AccountSwitchCountKind.CONFLICTED, 1),
                    ),
                namedData = listOf(AccountDataKind.ANSWER_HISTORY),
                isKnown = true,
            ),
            state.pendingAccountSwitch,
        )
        assertTrue(state.isLinkingGoogle, "до ответа переключение не закончено")
        assertNull(state.message, "это вопрос, а не одноразовое сообщение")
        assertEquals(0, signIn.confirmed)
        assertEquals(0, signIn.declined)
    }

    @Test
    fun `given the count is unknown then the question is still shown`() = runTest {
        val signIn = FakeGoogleSignInRepository(GoogleLinkOutcome.SwitchAwaitsConsent(loss(isKnown = false)))
        val component = component(signIn)

        component.onLinkGoogle(host)

        val warning = component.state.value.pendingAccountSwitch
        assertEquals(false, warning?.isKnown)
        assertTrue(warning!!.counts.isEmpty(), "нечего показывать числами, но вопрос задан")
    }

    @Test
    fun `given the link succeeded then there is no question`() = runTest {
        val component = component(FakeGoogleSignInRepository(GoogleLinkOutcome.Linked))

        component.onLinkGoogle(host)

        val state = component.state.value
        assertNull(state.pendingAccountSwitch)
        assertFalse(state.isLinkingGoogle)
        assertEquals(ProfileMessage.GoogleLinked, state.message)
    }

    @Test
    fun `given the queue was clean then the switch goes through without a question`() = runTest {
        val component = component(FakeGoogleSignInRepository(GoogleLinkOutcome.Switched))

        component.onLinkGoogle(host)

        val state = component.state.value
        assertNull(state.pendingAccountSwitch)
        assertFalse(state.isLinkingGoogle)
        assertEquals(ProfileMessage.GoogleSwitchedToExisting, state.message)
    }

    @Test
    fun `given the player declines then the repository is told and nothing else happens`() = runTest {
        val signIn = FakeGoogleSignInRepository(GoogleLinkOutcome.SwitchAwaitsConsent(loss(waiting = 2)))
        val component = component(signIn)
        component.onLinkGoogle(host)

        component.onDeclineAccountSwitch()

        val state = component.state.value
        assertNull(state.pendingAccountSwitch)
        assertFalse(state.isLinkingGoogle, "кнопка снова доступна: остались в прежнем аккаунте")
        assertNull(state.message)
        assertEquals(1, signIn.declined)
        assertEquals(0, signIn.confirmed, "отказ не подтверждает")
    }

    @Test
    fun `given the player confirms then the switch is completed through the repository`() = runTest {
        val signIn = FakeGoogleSignInRepository(GoogleLinkOutcome.SwitchAwaitsConsent(loss(waiting = 2)))
        val component = component(signIn)
        component.onLinkGoogle(host)
        val reloadsBefore = profiles.ensured

        component.onConfirmAccountSwitch()

        val state = component.state.value
        assertEquals(1, signIn.confirmed)
        assertEquals(0, signIn.declined)
        assertNull(state.pendingAccountSwitch)
        assertFalse(state.isLinkingGoogle)
        assertEquals(ProfileMessage.GoogleSwitchedToExisting, state.message)
        assertTrue(profiles.ensured > reloadsBefore, "после смены экран перечитывает аккаунт")
    }

    @Test
    fun `given the player confirmed but the sign-in failed then the screen leaves the busy state with an error`() =
        runTest {
            val signIn =
                FakeGoogleSignInRepository(
                    GoogleLinkOutcome.SwitchAwaitsConsent(loss(waiting = 2)),
                    confirmOutcome = Result.failure(IllegalStateException("no network")),
                )
            val component = component(signIn)
            component.onLinkGoogle(host)

            component.onConfirmAccountSwitch()

            val state = component.state.value
            assertNull(state.pendingAccountSwitch)
            assertFalse(state.isLinkingGoogle)
            assertEquals(ProfileMessage.Failure("no network"), state.message)
        }

    @Test
    fun `given no question is shown then confirm and decline do nothing`() = runTest {
        val signIn = FakeGoogleSignInRepository(GoogleLinkOutcome.Linked)
        val component = component(signIn)

        component.onConfirmAccountSwitch()
        component.onDeclineAccountSwitch()

        assertEquals(0, signIn.confirmed)
        assertEquals(0, signIn.declined)
    }

    @Test
    fun `given the screen is recreated then the question is still there and the button is still busy`() = runTest {
        // Поворот экрана убирал диалог вместе с состоянием компонента: переключение оставалось
        // брошенным на полпути, а в памяти висело незакрытое ожидание с чужими учётными данными.
        val keeper = InstanceKeeperDispatcher()
        val first = LifecycleRegistry().apply { resume() }
        val signIn = FakeGoogleSignInRepository(GoogleLinkOutcome.SwitchAwaitsConsent(loss(waiting = 2)))
        component(signIn, lifecycle = first, instanceKeeper = keeper).onLinkGoogle(host)
        first.destroy()

        val second = LifecycleRegistry().apply { resume() }
        val recreated = component(signIn, lifecycle = second, instanceKeeper = keeper)

        val state = recreated.state.value
        assertEquals(
            listOf(AccountSwitchCount(AccountSwitchCountKind.WAITING, 2)),
            state.pendingAccountSwitch?.counts,
        )
        assertTrue(state.isLinkingGoogle, "переключение всё ещё ждёт ответа")
        assertEquals(0, signIn.confirmed)
        assertEquals(0, signIn.declined)
        second.destroy()
    }

    @Test
    fun `given the player answered then the recreated screen has no question`() = runTest {
        val keeper = InstanceKeeperDispatcher()
        val first = LifecycleRegistry().apply { resume() }
        val signIn = FakeGoogleSignInRepository(GoogleLinkOutcome.SwitchAwaitsConsent(loss(waiting = 2)))
        val component = component(signIn, lifecycle = first, instanceKeeper = keeper)
        component.onLinkGoogle(host)
        component.onDeclineAccountSwitch()
        first.destroy()

        val second = LifecycleRegistry().apply { resume() }
        val recreated = component(signIn, lifecycle = second, instanceKeeper = keeper)

        assertNull(recreated.state.value.pendingAccountSwitch)
        assertFalse(recreated.state.value.isLinkingGoogle)
        second.destroy()
    }

    // ── Fakes ────────────────────────────────────────────────────────────────────────────────

    private class FakeGoogleSignInRepository(
        private val linkOutcome: GoogleLinkOutcome,
        private val confirmOutcome: Result<GoogleLinkOutcome> = Result.success(GoogleLinkOutcome.Switched),
    ) : GoogleSignInRepository {
        var confirmed = 0
        var declined = 0

        override suspend fun linkGoogleAccount(host: AccountChooserHost): Result<GoogleLinkOutcome> =
            Result.success(linkOutcome)

        override suspend fun confirmAccountSwitch(): Result<GoogleLinkOutcome> {
            confirmed++
            return confirmOutcome
        }

        override fun declineAccountSwitch() {
            declined++
        }
    }

    private class FakeProfileRepository : ProfileRepository {
        private val profile =
            MutableStateFlow(UserProfile.offline().copy(uid = "uid-A", status = ProfileStatus.ANONYMOUS))
        var ensured = 0

        override fun observeCurrentProfile(): Flow<UserProfile> = profile

        override suspend fun currentProfile(): UserProfile = profile.value

        override suspend fun ensureCurrentProfile(): Result<UserProfile> {
            ensured++
            return Result.success(profile.value)
        }

        override suspend fun leagueStanding(): LeagueStanding? = null

        override suspend fun updateNickname(nickname: String): Result<UserProfile> = Result.success(profile.value)
    }

    private class FakeActivityRepository : ActivityRepository {
        override fun observeDailyActivity(days: Int): Flow<List<Int>> = flowOf(List(days) { 0 })
    }

    private class FakeNicknameRepository : NicknameRepository {
        override suspend fun checkAvailability(nickname: String): NicknameAvailability =
            NicknameAvailability(nickname = nickname, available = true, reason = null)

        override suspend fun owned(): List<OwnedNickname> = emptyList()

        override suspend fun setActive(nickname: String) = Unit

        override suspend fun claim(nickname: String): Long = 0L

        override suspend fun listings(limit: Int): List<NicknameListing> = emptyList()

        override suspend fun listForSale(
            nickname: String,
            price: Long,
        ) = Unit

        override suspend fun cancelListing(nickname: String) = Unit

        override fun observeCancelSync(nickname: String): Flow<ListingCancelSync?> = flowOf(null)

        override suspend fun buy(nickname: String): Long = 0L
    }
}
