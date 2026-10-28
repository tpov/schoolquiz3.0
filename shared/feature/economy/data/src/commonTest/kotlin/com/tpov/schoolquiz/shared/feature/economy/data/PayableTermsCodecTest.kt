package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.feature.economy.domain.model.ActivityKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PayableTerms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Запись согласия одним куском текста обязана пережить хранение целиком, а испорченная —
 * отбрасываться целиком: половина согласия хуже его отсутствия.
 */
class PayableTermsCodecTest {

    private val terms =
        PayableTerms.BOOTSTRAP.copy(
            version = 7L,
            standard = PayableTerms.BOOTSTRAP.standard.copy(maxOwned = 12, priceLadder = listOf(500L, 900L)),
            plasma = PayableTerms.BOOTSTRAP.plasma.copy(maxOwned = 4, premiumRegenDivisor = 3),
            activityPrices = PayableTerms.BOOTSTRAP.activityPrices + (ActivityKind.TOURNAMENT to 750),
        )

    @Test
    fun `given terms that were written then every field survives the record`() {
        // По полям, а не через equals: у записи есть значения по умолчанию, и сравнение целиком
        // вернуло бы true даже если бы отображение потеряло поле, чьё умолчание совпало.
        val read = requireNotNull(PayableTermsCodec.fromJson(PayableTermsCodec.toJson(terms)))

        assertEquals(7L, read.version)
        assertEquals(12, read.standard.maxOwned)
        assertEquals(listOf(500L, 900L), read.standard.priceLadder)
        assertEquals(terms.standard.currency, read.standard.currency)
        assertEquals(1, read.standard.premiumRegenDivisor)
        assertEquals(4, read.plasma.maxOwned)
        assertEquals(true, read.plasma.requiresSettledAccount)
        assertEquals(3, read.plasma.premiumRegenDivisor)
        assertEquals(750, read.priceOf(ActivityKind.TOURNAMENT))
        assertEquals(33, read.priceOf(ActivityKind.ORDINARY_LESSON))
        ActivityKind.entries.forEach { kind -> assertEquals(terms.priceOf(kind), read.priceOf(kind)) }
    }

    @Test
    fun `given the committed cross-half literal then the codec writes it back byte for byte`() {
        // Ретро эпика 2 (F16): литерал один на обе половины провода — functions/mutation-terms.test.js
        // принимает его дословно readCapturedTerms; здесь кодек обязан писать ровно его. Дрейф
        // ключа, порядка или формы на любой из половин ломает одну из двух проверок.
        val literal = """{"version":7,"standard":{"maxOwned":12,"regenMs":3600000,"priceLadder":[500,900],"currency":"NOLICS","requiresSettledAccount":false,"premiumRegenDivisor":1},"plasma":{"maxOwned":4,"regenMs":86400000,"priceLadder":[1,2,3],"currency":"GOLD","requiresSettledAccount":true,"premiumRegenDivisor":3},"activityPrices":{"ORDINARY_LESSON":33,"ARENA":50,"THEME_TEST":100,"FINAL_EXAM":200,"TOURNAMENT":750}}"""

        assertEquals(literal, PayableTermsCodec.toJson(terms))
        assertEquals(literal, PayableTermsCodec.toJson(requireNotNull(PayableTermsCodec.fromJson(literal))))
    }

    @Test
    fun `given a truncated record then it is discarded rather than half-read`() {
        assertNull(PayableTermsCodec.fromJson("""{"version":"""))
    }

    @Test
    fun `given a record without a price kind then it is discarded rather than read as free`() {
        // Вид без цены, прочитанный как ноль, — это бесплатный турнир. Запись без него не согласие.
        val body = Json.parseToJsonElement(PayableTermsCodec.toJson(terms)).jsonObject
        val prices =
            JsonObject(body.getValue("activityPrices").jsonObject.filterKeys { it != ActivityKind.ARENA.name })
        val broken = JsonObject(body + ("activityPrices" to prices)).toString()

        assertNull(PayableTermsCodec.fromJson(broken))
    }

    @Test
    fun `given a record with a negative price then it is discarded rather than read`() {
        // Отрицательная цена запрещена доменом; пропустить её мимо согласия нельзя.
        val body = Json.parseToJsonElement(PayableTermsCodec.toJson(terms)).jsonObject
        val prices =
            JsonObject(
                body.getValue("activityPrices").jsonObject +
                    (ActivityKind.ARENA.name to JsonPrimitive(-1)),
            )
        val broken = JsonObject(body + ("activityPrices" to prices)).toString()

        assertNull(PayableTermsCodec.fromJson(broken))
    }

    @Test
    fun `given a legacy record without the premium regen knob then it reads as not faster`() {
        val body = Json.parseToJsonElement(PayableTermsCodec.toJson(terms)).jsonObject
        val plasma = JsonObject(body.getValue("plasma").jsonObject.filterKeys { it != "premiumRegenDivisor" })
        val legacy = JsonObject(body + ("plasma" to plasma)).toString()

        assertEquals(1, assertNotNull(PayableTermsCodec.fromJson(legacy)).plasma.premiumRegenDivisor)
    }
}
