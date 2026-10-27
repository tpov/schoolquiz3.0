package com.tpov.schoolquiz.platform.firebase.economy

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Фиксжурнал SHA-пина источника конверта подарочных заявок (retro e7 F1b). Прецедент —
 * арена-пин `FirebaseArenaLessonCatalogRemoteDataSourceTest.generatedPublicContractAndCallableNameRemainBoundToActualProducer`.
 *
 * `functions/box-service.js` строит тело `result` конверта `{replayed, result}`, который клиентский
 * строгий читатель (`FirebaseGiftClaimRemoteDataSource`, формы — `GiftClaimEnvelopeMappingTest`) обязан
 * разбирать дословно. Правка серверного источника без перечитывания клиентом — тот самый шов, чей дрейф
 * уже стоил одной device-смены (19–20.10, evidence `2026-10-20-premium-wire-repair` §3: обе стороны были
 * зелёными против противоположных контрактов). Красный тест = «перечитай конверт и перезапинь», не «отломи».
 */
class BoxServiceSourcePinTest {
    private val repo =
        generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun boxServiceSourceShaStaysPinnedToTheEnvelopeTheClientReaderParses() {
        // Журнал перезапинов:
        // 2026-10-27 (e7 retro fix-pack, базаль 44fc0f83): первичный пин — box-service.js с конвертом
        //   {replayed, result} (серверная правка c2c1a414 от 19.10; ответ собирает planBoxOperation,
        //   functions/box-service.js:205-208). Набор ключей result запинен в functions/economy-handler.test.js (F1a).
        val expected = "2222aabc26c9021c5fbdf4048153c722fa862100e4a3c9451f70165f4779c4d0"
        val bytes = MessageDigest.getInstance("SHA-256").digest(File(repo, "functions/box-service.js").readBytes())
        val actual = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        assertEquals(expected, actual)
    }
}
