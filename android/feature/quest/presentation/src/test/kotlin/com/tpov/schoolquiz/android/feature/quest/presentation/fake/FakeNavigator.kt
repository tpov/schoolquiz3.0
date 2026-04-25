package com.tpov.schoolquiz.android.feature.quest.presentation.fake

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.Navigator

class FakeNavigator : Navigator {
    val destinations = mutableListOf<Destination>()

    override fun goTo(destination: Destination) {
        destinations.add(destination)
    }

    fun lastDestination(): Destination? = destinations.lastOrNull()
}
