package com.tpov.schoolquiz.shared.feature.internet.profile.data

import com.tpov.schoolquiz.shared.core.persistence.UserProfileEntity
import com.tpov.schoolquiz.shared.feature.internet.profile.data.mapper.toDomain
import com.tpov.schoolquiz.shared.feature.internet.profile.data.mapper.toEntity
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Круговой переход профиля через границу Room — слова сервера не теряются и не переставляются.
 *
 * Гейты PremiumSurfaceState и «Получить подарок» читают свои часы из `nextBonusBoxAtMs` после
 * каждой перезаписи профиля; потеря поля в маппере сделала бы слот вечно свободным, а
 * «забрано сегодня» — недостижимым между сессиями, и ни один тест компонента этого бы не увидел.
 */
class ProfileMapperTest {

    @Test
    fun `entity to domain to entity keeps every server word including the bonus clock`() {
        val entity = entityWithAllWords()

        assertEquals(entity, entity.toDomain().toEntity())
    }

    @Test
    fun `bonus clock survives both directions on its own`() {
        val domain = entityWithAllWords().toDomain()

        assertEquals(BONUS_CLOCK, domain.nextBonusBoxAtMs)
        assertEquals(BONUS_CLOCK, domain.toEntity().nextBonusBoxAtMs)
    }

    @Test
    fun `a profile without the bonus word maps to the zero the server never sent`() {
        val domain = UserProfile.offline().copy(uid = "uid-1", nickname = "Игрок", premiumUntilMs = 5L)

        assertEquals(0L, domain.toEntity().nextBonusBoxAtMs)
        assertEquals(0L, domain.toEntity().toDomain().nextBonusBoxAtMs)
    }

    private fun entityWithAllWords() =
        UserProfileEntity(
            uid = "uid-1",
            nickname = "Игрок",
            status = "ANONYMOUS",
            avatarUrl = null,
            knownLanguages = listOf("ru"),
            createdAtMs = 1L,
            updatedAtMs = 2L,
            skillPoints = 3,
            gold = 4L,
            nolics = 5L,
            standardHearts = 6,
            goldHearts = 7,
            sponsorLevel = 8,
            testerLevel = 9,
            translatorLevel = 10,
            moderatorLevel = 11,
            adminLevel = 12,
            developerLevel = 13,
            boxCount = 2,
            boxStreakDays = 9,
            nextBoxAtMs = 1_000L,
            premiumUntilMs = 2_000L,
            trophies = listOf("TROPHY_VERIFIED"),
            ownedLogos = listOf("logo-1"),
            lifePoints = 30,
            lifePointsUpdatedAtMs = 14L,
            realName = "Имя",
            birthday = "2012-01-01",
            city = "Киев",
            telegram = "@player",
            verifiedAtMs = 15L,
            nextBonusBoxAtMs = BONUS_CLOCK,
        )

    private companion object {
        const val BONUS_CLOCK = 1_701_036_800_000L
    }
}
