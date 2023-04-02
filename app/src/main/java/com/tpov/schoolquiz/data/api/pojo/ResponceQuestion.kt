package com.tpov.schoolquiz.data.api.pojo

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class ResponceQuestion (

    @SerializedName("id")
    @Expose
    private var id: Int = 0,

    @SerializedName("answer")
    @Expose
    private var answer: String? = null,

    @SerializedName("question")
    @Expose
    private var question: String? = null,

    @SerializedName("value")
    @Expose
    private var value: Int = 0,

    @SerializedName("airdate")
    @Expose
    private var airdate: String? = null,

    @SerializedName("created_at")
    @Expose
    private var createdAt: String? = null,

    @SerializedName("updated_at")
    @Expose
    private var updatedAt: String? = null,

    @SerializedName("category_id")
    @Expose
    private var categoryId: Int = 0,

    @SerializedName("game_id")
    @Expose
    private var gameId: Int = 0,

    @SerializedName("invalid_count")
    @Expose
    private var invalidCount: Any? = null,

    @SerializedName("category")
    @Expose
    private var category: Category? = null,
) {

    fun getId(): Int {
        return id
    }

    fun setId(id: Int) {
        this.id = id
    }

    fun getAnswer(): String? {
        return answer
    }

    fun setAnswer(answer: String?) {
        this.answer = answer
    }

    fun getQuestion(): String? {
        return question
    }

    fun setQuestion(question: String?) {
        this.question = question
    }

    fun getValue(): Int {
        return value
    }

    fun setValue(value: Int) {
        this.value = value
    }

    fun getAirdate(): String? {
        return airdate
    }

    fun setAirdate(airdate: String?) {
        this.airdate = airdate
    }

    fun getCreatedAt(): String? {
        return createdAt
    }

    fun setCreatedAt(createdAt: String?) {
        this.createdAt = createdAt
    }

    fun getUpdatedAt(): String? {
        return updatedAt
    }

    fun setUpdatedAt(updatedAt: String?) {
        this.updatedAt = updatedAt
    }

    fun getCategoryId(): Int {
        return categoryId
    }

    fun setCategoryId(categoryId: Int) {
        this.categoryId = categoryId
    }

    fun getGameId(): Int {
        return gameId
    }

    fun setGameId(gameId: Int) {
        this.gameId = gameId
    }

    fun getInvalidCount(): Any? {
        return invalidCount
    }

    fun setInvalidCount(invalidCount: Any?) {
        this.invalidCount = invalidCount
    }

    fun getCategory(): Category? {
        return category
    }

    fun setCategory(category: Category?) {
        this.category = category
    }
}