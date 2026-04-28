package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider

interface RandomSeedProvider {
    fun next(): Long
}
