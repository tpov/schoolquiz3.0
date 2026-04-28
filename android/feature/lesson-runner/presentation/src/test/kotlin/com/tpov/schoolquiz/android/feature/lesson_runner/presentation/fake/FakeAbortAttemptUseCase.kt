package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState

class FakeAbortAttemptUseCase(
    var result: RunnerState = RunnerState.Aborted(
        attempt = FakeAttemptFixtures.fixtureAttempt(),
    ),
) {
    var callCount = 0

    suspend operator fun invoke(state: RunnerState.Ready): RunnerState {
        callCount++
        return result
    }
}
