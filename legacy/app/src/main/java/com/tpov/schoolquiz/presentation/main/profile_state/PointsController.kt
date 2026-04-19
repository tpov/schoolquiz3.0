package com.tpov.schoolquiz.presentation.main.profile_state

import android.content.Context
import com.tpov.schoolquiz.data.fierbase.Points
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


class PointsController(private val context: Context) {

    private val _pointsState = MutableStateFlow(Points())
    val pointsState = _pointsState.asStateFlow()

    fun setCoins(
       addCoins: Points
    ) {
        _pointsState.value = addCoins
    }

}
