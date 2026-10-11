package com.tpov.schoolquiz.android.feature.internet.social.presentation.di

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.feature.internet.social.presentation.component.DefaultPollsComponent
import com.tpov.schoolquiz.android.feature.internet.social.presentation.component.PollsComponent
import com.tpov.schoolquiz.shared.feature.internet.social.domain.provider.PollsIdProvider
import com.tpov.schoolquiz.shared.feature.internet.social.domain.use_case.GetPollQuestionStateUseCase
import com.tpov.schoolquiz.shared.feature.internet.social.domain.use_case.ListPollSeriesUseCase
import com.tpov.schoolquiz.shared.feature.internet.social.domain.use_case.PublishPollSeriesUseCase
import com.tpov.schoolquiz.shared.feature.internet.social.domain.use_case.SubmitPollVoteUseCase
import org.koin.dsl.module

val socialPresentationModule =
    module {
        factory<PollsComponent> { (ctx: ComponentContext) ->
            DefaultPollsComponent(
                componentContext = ctx,
                authRepository = get(),
                listSeries = get<ListPollSeriesUseCase>(),
                questionState = get<GetPollQuestionStateUseCase>(),
                submitVote = get<SubmitPollVoteUseCase>(),
                publishSeries = get<PublishPollSeriesUseCase>(),
                idProvider = get<PollsIdProvider>(),
            )
        }
    }
