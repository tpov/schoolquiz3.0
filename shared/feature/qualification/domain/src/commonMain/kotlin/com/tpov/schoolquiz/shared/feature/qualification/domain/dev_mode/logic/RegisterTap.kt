package com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode.logic

import com.tpov.schoolquiz.shared.core.foundation.QualificationLevel
import com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode.model.TapProgress
import com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode.model.TapResult

fun registerTap(
    progress: TapProgress,
    nowMillis: Long,
    currentDeveloperLevel: Int,
    required: QualificationLevel = QualificationLevel.LEVEL_1,
    resetThresholdMillis: Long = 500L,
    targetCount: Int = 10,
): TapResult {
    val isFirstTap = progress.lastTapAtMillis == null || progress.count == 0
    val elapsed = if (isFirstTap) 0L else nowMillis - progress.lastTapAtMillis!!

    val timedOut = !isFirstTap && elapsed > resetThresholdMillis

    return when {
        timedOut -> TapResult.Reset(
            newProgress = TapProgress(count = 1, lastTapAtMillis = nowMillis),
        )

        progress.count < targetCount - 1 -> TapResult.NoChange(
            newProgress = TapProgress(count = progress.count + 1, lastTapAtMillis = nowMillis),
        )

        currentDeveloperLevel >= required.points -> TapResult.AlreadyDev(
            newProgress = TapProgress(count = 0, lastTapAtMillis = null),
        )

        else -> TapResult.Activated(
            newProgress = TapProgress(count = 0, lastTapAtMillis = null),
        )
    }
}
