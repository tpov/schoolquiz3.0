package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState

class FakeCompleteAttemptUseCase(
    var result: RunnerState = RunnerState.Completed(
        attempt = FakeAttemptFixtures.fixtureAttempt(),
        ratingPrompt = false,
    ),
) {
    var callCount = 0

    suspend operator fun invoke(state: RunnerState.Ready): RunnerState {
        callCount++
        return result
    }
}
