package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.di

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ClosePrivateQuizLinkUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.DecideQuestProposalUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.FetchQuestProposalsUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.QuoteLessonProposalFeeUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SubmitErrorReportProposalUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.CreateQuestDraftUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.FetchQuestReviewOutcomesUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.FetchReviewDecisionsUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.GetActiveQuestDraftUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.GetArenaReadinessUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ListCuratorLifecycleUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ListInboxItemsUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ListLessonHistoryUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ListLessonRoyaltyUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ListOwnedLifecycleUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.MarkReviewDecisionUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ObserveQuestDraftConflictUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ObserveQuestDraftSummariesUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.OpenQuestDraftUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.QuoteQuestReviewFeeUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ResolveQuestDraftConflictUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SaveDraftQuestionUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SubmitLessonErrorReportUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SubmitQuestDraftToArenaUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.TransitionLessonUseCase
import org.koin.dsl.module

val questAuthoringDomainModule =
    module {
        factory { ClosePrivateQuizLinkUseCase(get()) }
        // «Сообщить об ошибке» — предложение автору (4.22, CAP-6/27): подача в общую очередь и
        // цитата платы до отправки (UX-23). Учебная поверхность — экран результата урока.
        factory { SubmitErrorReportProposalUseCase(get(), get(), get()) }
        factory { QuoteLessonProposalFeeUseCase(get()) }
        // «Предложения» (4.25, UX-22): страница прав и решение автора тем же приёмником 4.21.
        factory { FetchQuestProposalsUseCase(get()) }
        factory { DecideQuestProposalUseCase(get()) }
        factory { CreateQuestDraftUseCase(get(), get(), get()) }
        // Исход модельной проверки (4.16): сценарий добивается фабрикой здесь — MyQuests
        // резолвит его через get(), и без этой строки разрешение падало бы в рантайме.
        factory { FetchQuestReviewOutcomesUseCase(get()) }
        // «Решения» админов (4.17, UX-22): чтение страницы и отметка — прямые callable.
        factory { FetchReviewDecisionsUseCase(get()) }
        factory { MarkReviewDecisionUseCase(get()) }
        factory { GetActiveQuestDraftUseCase(get()) }
        factory { GetArenaReadinessUseCase(get()) }
        factory { ListCuratorLifecycleUseCase(get()) }
        factory { ListInboxItemsUseCase(get()) }
        factory { ListLessonHistoryUseCase(get()) }
        factory { ListLessonRoyaltyUseCase(get()) }
        factory { ListOwnedLifecycleUseCase(get()) }
        factory { ObserveQuestDraftConflictUseCase(get()) }
        factory { ObserveQuestDraftSummariesUseCase(get()) }
        factory { OpenQuestDraftUseCase(get()) }
        factory { QuoteQuestReviewFeeUseCase(get()) }
        factory { ResolveQuestDraftConflictUseCase(get()) }
        factory { SaveDraftQuestionUseCase(get(), get(), get(), get()) }
        factory { SubmitLessonErrorReportUseCase(get(), get(), get()) }
        factory { SubmitQuestDraftToArenaUseCase(get(), get(), get()) }
        factory { TransitionLessonUseCase(get()) }
    }
