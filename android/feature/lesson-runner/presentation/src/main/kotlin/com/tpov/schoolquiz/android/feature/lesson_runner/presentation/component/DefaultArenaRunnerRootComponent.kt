package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ArenaRunnerRequest
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import kotlin.coroutines.CoroutineContext

/** Context belongs to this navigation child; only the context-free instance survives rotation. */
internal class DefaultArenaRunnerRootComponent private constructor(
    private val instance: ArenaRunnerInstance,
) : ArenaRunnerRootComponent, LessonRunnerRootComponent by instance {
    constructor(
        context: ComponentContext,
        request: ArenaRunnerRequest,
        services: ArenaRunServices,
        clock: Clock,
        mainContext: CoroutineContext = Dispatchers.Main.immediate,
    ) : this(
        context.instanceKeeper.getOrCreate("arena_runner_instance") {
            ArenaRunnerInstance(request, services, clock, mainContext)
        }.also { it.requireRoute(request) },
    )

    override val arenaView get() = instance.host.view

    override fun onArenaAction(
        token: ArenaRunToken,
        action: ArenaRunAction,
    ) = instance.host.action(token, action)

    override fun onArenaReload() = instance.host.reload()

    override fun onArenaForeground() = instance.host.foreground()

    override fun onArenaBackground(changingConfigurations: Boolean) = instance.host.background(changingConfigurations)

    override fun onArenaRefreshWallet() = instance.host.refreshWallet()
}
