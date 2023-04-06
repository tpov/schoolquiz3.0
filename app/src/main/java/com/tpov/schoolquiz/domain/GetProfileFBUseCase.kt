package com.tpov.schoolquiz.domain

import android.content.Context
import com.tpov.schoolquiz.domain.repository.RepositoryFB
import javax.inject.Inject

class GetProfileFBUseCase @Inject constructor(private val repository: RepositoryFB) {
    operator fun invoke() = repository.getProfile()
}