package com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RandomSeedProvider
import java.util.concurrent.ThreadLocalRandom

class DefaultRandomSeedProvider : RandomSeedProvider {
    override fun next(): Long = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)
}
