package com.tpov.schoolquiz.android.feature.internet.profile.presentation.di

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.component.DefaultProfileComponent
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.component.ProfileComponent
import org.koin.dsl.module

val profilePresentationModule =
    module {
        factory<ProfileComponent> { (ctx: ComponentContext) ->
            DefaultProfileComponent(
                componentContext = ctx,
                observeCurrentProfile = get(),
                ensureCurrentProfile = get(),
                updateProfileNickname = get(),
            )
        }
    }
