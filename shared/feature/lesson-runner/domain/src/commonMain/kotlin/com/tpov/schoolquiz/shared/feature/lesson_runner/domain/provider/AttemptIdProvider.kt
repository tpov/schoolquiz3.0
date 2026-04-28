package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId

interface AttemptIdProvider {
    fun next(): AttemptId
}
