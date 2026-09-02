package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.core.persistence.UserStatsEntity
import com.tpov.schoolquiz.shared.feature.economy.data.mapper.mergeWithBalance
import com.tpov.schoolquiz.shared.feature.economy.data.mapper.toBalance
import com.tpov.schoolquiz.shared.feature.economy.data.mapper.toNewUserStatsEntity
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Понижение потолка не конфискует — и на клиенте тоже.
 *
 * Маппер зажимал сердца потолком, зашитым в сборку: аккаунт с двенадцатью слотами — а потолок
 * теперь живёт в серверной таблице и может быть любым — показывался бы с пятью. Не обкрадывал
 * (сервер помнит своё), но врал; а после `mergeWithBalance` и записывал ложь в локальную строку,
 * откуда её читала витрина и предлагала купить слот, которого не продаст.
 *
 * По полям, а не через `equals`: у обеих моделей есть значения по умолчанию, и сравнение целиком
 * не заметило бы потерянное поле, чьё умолчание совпало с записанным.
 */
class HeartCeilingIsNotConfiscationTest {

    private fun entity(standard: Int, gold: Int) =
        UserStatsEntity(
            uid = "u1",
            nickname = "player",
            avatarUrl = null,
            hasPremium = false,
            streakDays = 0,
            stars = 0L,
            nolics = 0L,
            standardHearts = standard,
            goldHearts = gold,
            gold = 0L,
            currentSkill = 0,
            testerLevel = 0,
            moderatorLevel = 0,
            sponsorLevel = 0,
            translatorLevel = 0,
            adminLevel = 0,
            developerLevel = 0,
            lessonUnlocks = emptySet(),
        )

    @Test
    fun aBalanceAboveTheBuildsCeilingIsShownAsItIs() {
        val balance = entity(standard = 12, gold = 4).toBalance()

        assertEquals(12, balance.standardHearts)
        assertEquals(4, balance.goldHearts)
    }

    @Test
    fun aServerAnswerAboveTheBuildsCeilingIsStoredAsItIs() {
        val answer = EconomyResourceBalance(standardHearts = 12, goldHearts = 4)

        val merged = entity(standard = 1, gold = 0).mergeWithBalance(answer)
        val fresh = answer.toNewUserStatsEntity("u1")

        assertEquals(12, merged.standardHearts)
        assertEquals(4, merged.goldHearts)
        assertEquals(12, fresh.standardHearts)
        assertEquals(4, fresh.goldHearts)
    }

    @Test
    fun aNegativeCountStillFloorsAtZero() {
        // Снят потолок, не пол: отрицательных сердец не бывает.
        assertEquals(0, entity(standard = -3, gold = -1).toBalance().standardHearts)
        assertEquals(0, entity(standard = -3, gold = -1).toBalance().goldHearts)
    }
}
