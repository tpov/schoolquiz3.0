package com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.AttemptIdProvider
import java.util.UUID

class DefaultAttemptIdProvider : AttemptIdProvider {
    override fun next(): AttemptId = AttemptId(UUID.randomUUID().toString())
}
