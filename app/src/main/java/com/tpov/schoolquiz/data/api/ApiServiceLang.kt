package com.tpov.schoolquiz.data.api

import com.tpov.schoolquiz.data.api.pojo.ResponceLang
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
/**
* "https://translated-mymemory---translation-memory.p.rapidapi.com/api/get?langpair=en%7Cru&q=Hello%20World!&mt=1&onlyprivate=0&de=a%40b.c"
**/
interface ApiServiceLang {
    @Headers(
        "X-RapidAPI-Key: 518352f3c1msh0b5972992fb28fb28fp1a9bc8jsndcffc491201d",
                "X-RapidAPI-Host: translated-mymemory---translation-memory.p.rapidapi.com"
    )
    @POST("api/")
    suspend fun translate (
        @Query(LANG) langpair: String,
        @Query(TEXT) q: String,
        @Query("d2f4e76232223ed9117b") key: String,
        @Query("1") mt: String,
        @Query("0") onlyprivate: String,
        @Query("a@b.c") de: String
    ): List<ResponceLang>

    companion object {
        const val LANG = "langpair"
        const val TEXT = "q"
    }
}
