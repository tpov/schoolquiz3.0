package com.tpov.schoolquiz.shared.feature.internet.profile.domain.di

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.EnsureCurrentProfileUseCase
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.LinkGoogleAccountUseCase
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.ObserveCurrentProfileUseCase
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.ObserveDailyActivityUseCase
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.UpdateProfileNicknameUseCase
import org.koin.dsl.module

val profileDomainModule = module {
    factory { ObserveCurrentProfileUseCase(get()) }
    factory { EnsureCurrentProfileUseCase(get()) }
    factory { ObserveDailyActivityUseCase(get()) }
    factory { LinkGoogleAccountUseCase(get()) }
    factory { UpdateProfileNicknameUseCase(get()) }
}
