package com.tpov.schoolquiz.data.api

import com.tpov.schoolquiz.data.api.pojo.ResponceQuestion
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiServiceQuestion {

//http://jservice.io/api/random1
    @GET("api/random")
    suspend fun getFullPriceList(
        @Query(QUERY_COUNT) count: String = COUNT
    ): List<ResponceQuestion>

    companion object {
        private const val COUNT = "1"
        private const val QUERY_COUNT = "count"
    }
}