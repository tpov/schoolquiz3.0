package com.tpov.schoolquiz.data.api.pojo

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class Category (
    @SerializedName("id")
    @Expose
    var id: Int,

    @SerializedName("title")
    @Expose
    var title: String? = null,

    @SerializedName("created_at")
    @Expose
    var createdAt: String? = null,

    @SerializedName("updated_at")
    @Expose
    var updatedAt: String? = null,

    @SerializedName("clues_count")
    @Expose
    var cluesCount: Int = 0
)