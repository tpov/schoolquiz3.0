package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.di

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.CreateQuestDraftUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.GetActiveQuestDraftUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.GetArenaReadinessUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ObserveQuestDraftConflictUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ObserveQuestDraftSummariesUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ObserveReviewAssignmentSyncUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ObserveReviewAssignmentsUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.OpenQuestDraftUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ResolveQuestDraftConflictUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SaveDraftQuestionUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SubmitQuestDraftToArenaUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SubmitReviewActionUseCase
import org.koin.dsl.module

val questAuthoringDomainModule =
    module {
        factory { CreateQuestDraftUseCase(get(), get(), get()) }
        factory { GetActiveQuestDraftUseCase(get()) }
        factory { GetArenaReadinessUseCase(get()) }
        factory { ObserveQuestDraftConflictUseCase(get()) }
        factory { ObserveQuestDraftSummariesUseCase(get()) }
        factory { ObserveReviewAssignmentsUseCase(get()) }
        factory { OpenQuestDraftUseCase(get()) }
        factory { ObserveReviewAssignmentSyncUseCase(get()) }
        factory { ResolveQuestDraftConflictUseCase(get()) }
        factory { SaveDraftQuestionUseCase(get(), get(), get(), get()) }
        factory { SubmitReviewActionUseCase(get(), get(), get()) }
        factory { SubmitQuestDraftToArenaUseCase(get(), get(), get()) }
    }
