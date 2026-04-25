package com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode.use_case

import com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode.logic.registerTap
import com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode.model.TapProgress
import com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode.model.TapResult

class ActivateDevModeUseCase(
    private val readCurrentDeveloperLevel: () -> Int,
    private val onDevModeActivated: suspend () -> Unit,
) {
    suspend operator fun invoke(progress: TapProgress, nowMillis: Long): TapResult {
        val result = registerTap(
            progress = progress,
            nowMillis = nowMillis,
            currentDeveloperLevel = readCurrentDeveloperLevel(),
        )
        if (result is TapResult.Activated) {
            onDevModeActivated()
        }
        return result
    }
}
