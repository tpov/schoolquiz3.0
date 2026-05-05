package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.di

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.CreateQuestDraftUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.GetActiveQuestDraftUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.GetArenaReadinessUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ObserveQuestDraftSummariesUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ObserveReviewAssignmentsUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SaveDraftQuestionUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SubmitReviewActionUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SubmitQuestDraftToArenaUseCase
import org.koin.dsl.module

val questAuthoringDomainModule = module {
    factory { CreateQuestDraftUseCase(get(), get(), get()) }
    factory { GetActiveQuestDraftUseCase(get()) }
    factory { GetArenaReadinessUseCase(get()) }
    factory { ObserveQuestDraftSummariesUseCase(get()) }
    factory { ObserveReviewAssignmentsUseCase(get()) }
    factory { SaveDraftQuestionUseCase(get(), get(), get(), get()) }
    factory { SubmitReviewActionUseCase(get()) }
    factory { SubmitQuestDraftToArenaUseCase(get(), get(), get()) }
}
