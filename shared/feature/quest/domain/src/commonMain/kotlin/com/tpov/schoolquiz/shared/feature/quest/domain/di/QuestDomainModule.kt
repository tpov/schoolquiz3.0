package com.tpov.schoolquiz.shared.feature.quest.domain.di

import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.ObserveMyQuestsUseCase
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.RetirePublicQuestUseCase
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.SetPublicQuestShelfUseCase
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.SyncQuestsUseCase
import org.koin.dsl.module

val questDomainModule = module {
    factory { ObserveMyQuestsUseCase(get()) }
    factory { SetPublicQuestShelfUseCase(get()) }
    factory { RetirePublicQuestUseCase(get()) }
    factory { SyncQuestsUseCase(get()) }
}
