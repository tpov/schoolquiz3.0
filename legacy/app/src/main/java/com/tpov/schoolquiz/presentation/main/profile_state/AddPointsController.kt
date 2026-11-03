package com.tpov.schoolquiz.presentation.main.profile_state

import android.content.Context
import com.tpov.schoolquiz.data.fierbase.AddPoints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


class AddPointsController(private val context: Context) {

    private val _pointsState = MutableStateFlow(AddPoints())
    val addPointsState = _pointsState.asStateFlow()

    fun setCoins(
        addCoins: AddPoints
    ) {
        if (addCoins.addGold.toInt() != 0 ||
            addCoins.addSkill.toInt() != 0 ||
            addCoins.addNolics.toInt() != 0 ||
            addCoins.addTrophy.isNotEmpty() ||
            addCoins.addMassage.isNotEmpty()
        ) {
            _pointsState.value = addCoins
        }
    }

}
