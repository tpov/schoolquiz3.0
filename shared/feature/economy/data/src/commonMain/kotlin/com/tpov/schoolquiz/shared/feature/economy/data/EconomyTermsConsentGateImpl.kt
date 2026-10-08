package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.core.outbox.OutboxConsentRequeue
import com.tpov.schoolquiz.shared.feature.economy.domain.logic.TermsConsentGate
import com.tpov.schoolquiz.shared.feature.economy.domain.logic.TermsConsentVerdict
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PayableTerms
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyConstantsRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyTermsConsentGate

/**
 * Гейт согласия: захваченные платные условия против текущей таблицы.
 *
 * Обе половины CAP-16 встречаются здесь. Отказ синхронизации оставляет таблице последний
 * известный снимок — значит, и вердикт согласия не меняется от того, что сервер был недоступен:
 * игрок не виноват в обрыве связи, и переспрашивать согласие по прежним ценам из-за него нельзя.
 * Требовать свежего согласия гейт вправе только когда таблица действительно стала другой.
 *
 * Принятие согласия здесь же снимает ожидание очереди: серверная половина проверки (2.8)
 * отвергает платную мутацию с пометкой `terms-consent-required`, пока игрок не согласится
 * заново, и единственный момент, когда это ожидание разрешается, — [acceptCurrentTerms]. После
 * записи согласия ждущие записи возвращаются в отправку под теми же ключами и уезжают уже
 * со свежим снимком.
 */
class EconomyTermsConsentGateImpl(
    private val constants: EconomyConstantsRepository,
    private val store: EconomyTermsConsentStore,
    private val requeue: OutboxConsentRequeue,
) : EconomyTermsConsentGate {
    override fun capturedTerms(): PayableTerms? = store.read()

    override fun verdict(): TermsConsentVerdict = TermsConsentGate.evaluate(store.read(), constants.current())

    override suspend fun acceptCurrentTerms() {
        store.write(PayableTerms.of(constants.current()))
        requeue.requeueConflictedTerms()
    }
}
