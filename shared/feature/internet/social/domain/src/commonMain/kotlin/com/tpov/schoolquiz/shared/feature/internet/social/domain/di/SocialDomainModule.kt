package com.tpov.schoolquiz.shared.feature.internet.social.domain.di

import com.tpov.schoolquiz.shared.feature.internet.social.domain.use_case.GetPollQuestionStateUseCase
import com.tpov.schoolquiz.shared.feature.internet.social.domain.use_case.ListPollSeriesUseCase
import com.tpov.schoolquiz.shared.feature.internet.social.domain.use_case.PublishPollSeriesUseCase
import com.tpov.schoolquiz.shared.feature.internet.social.domain.use_case.SubmitPollVoteUseCase
import org.koin.dsl.module

val socialDomainModule =
    module {
        factory { ListPollSeriesUseCase(get()) }
        factory { GetPollQuestionStateUseCase(get()) }
        factory { SubmitPollVoteUseCase(get(), get(), get()) }
        factory { PublishPollSeriesUseCase(get(), get(), get()) }
    }
