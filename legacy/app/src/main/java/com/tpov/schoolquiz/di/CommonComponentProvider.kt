package com.tpov.schoolquiz.di

import com.tpov.common.di.CommonComponent

interface CommonComponentProvider {
    fun provideCommonComponent(): CommonComponent
}