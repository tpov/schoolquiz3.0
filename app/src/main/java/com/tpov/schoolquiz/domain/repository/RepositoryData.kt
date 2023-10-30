package com.tpov.schoolquiz.domain.repository

interface RepositoryData {
    fun getTpovIdByEmail(email: String): Int
    fun downloadTpovId()
}