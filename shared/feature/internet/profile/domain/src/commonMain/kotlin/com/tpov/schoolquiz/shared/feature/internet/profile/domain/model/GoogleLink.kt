package com.tpov.schoolquiz.shared.feature.internet.profile.domain.model

/**
 * Whatever the platform needs in order to put an account chooser on screen.
 *
 * Opaque on purpose. Android needs a live Activity to show the sheet, and naming that type here
 * would drag the framework into the domain; the implementation unwraps its own kind and refuses
 * anything else.
 */
interface AccountChooserHost

/**
 * The default carrier: whatever screen object the platform needs, held as [platformHost].
 *
 * Typed as Any so the domain never names Activity. The implementation unwraps the kind it knows
 * and refuses anything else, which keeps the mistake at runtime in one place instead of spreading
 * the framework through every layer that passes this along.
 */
class PlatformAccountChooserHost(val platformHost: Any) : AccountChooserHost

/**
 * What happened when somebody signed in with Google.
 *
 * The distinction matters and is not cosmetic. [Linked] keeps the anonymous account and everything
 * earned on it. [Switched] means the Google account already belonged to another player, so this
 * device is now signed in as them and whatever the anonymous account held is no longer in reach —
 * which is worth telling somebody, not swallowing.
 */
sealed interface GoogleLinkOutcome {
    data object Linked : GoogleLinkOutcome

    /** Вошли как другой игрок; следы прежнего аккаунта на этом устройстве сняты (AD-8). */
    data object Switched : GoogleLinkOutcome

    /**
     * Переключение остановлено перед самым входом: у прежнего аккаунта остались неотправленные
     * действия, и прежде чем их потерять, надо спросить (AD-8: предупреждение и есть согласие).
     *
     * Сам вход ещё не случился — `uid` прежний, очередь цела. Дальше два пути и ни одного по
     * умолчанию: [GoogleSignInRepository.confirmAccountSwitch] или
     * [GoogleSignInRepository.declineAccountSwitch].
     *
     * @property loss числа по каждому состоянию и имена данных, которые исчезнут.
     */
    data class SwitchAwaitsConsent(
        val loss: AccountSwitchLoss,
    ) : GoogleLinkOutcome
}

/**
 * Что именно потеряется при смене аккаунта — числами по состояниям и именами данных.
 *
 * Одного суммарного числа мало и оно вводит в заблуждение: «шесть действий» не говорит, что среди
 * них конфликт, ждущий решения игрока, и ничего не говорит про историю ответов, расписание повторов
 * и неопубликованные черновики — их сервер не хранит, и после переключения они не вернутся.
 * Согласие, полученное на «шесть действий», не покрывает того, что удаляется (AD-8, история 6.4).
 *
 * @property waiting ждут отправки.
 * @property waitingPrecondition ждут, пока доедет предусловие.
 * @property conflicted разошлись с сервером и ждут решения игрока (AD-24).
 * @property quarantined повторять бессмысленно, последствие разбирает фича (AD-28).
 * @property synchronous хранят ключ синхронного действия, чтобы спросить сервер о его судьбе.
 * @property isKnown удалось ли сосчитать. Незнание — тоже повод спросить, а не молчать; при нём
 *   числа нулевые, и экран говорит «неизвестно сколько».
 * @property namedData виды данных без серверного двойника, которые исчезнут вместе с аккаунтом.
 */
data class AccountSwitchLoss(
    val waiting: Int,
    val waitingPrecondition: Int,
    val conflicted: Int,
    val quarantined: Int,
    val synchronous: Int,
    val isKnown: Boolean,
    val namedData: List<AccountDataKind>,
) {
    /** Сколько строк очереди исчезнет. Не показывается одним числом — только для проверок. */
    val total: Int get() = waiting + waitingPrecondition + conflicted + quarantined + synchronous
}

/**
 * Вид данных, у которого нет серверного двойника, — названный игроку словом, а не таблицей.
 *
 * Набор закрыт и приходит из перечня истории 6.2: SQL и имена таблиц наружу не выходят (NFR11).
 */
enum class AccountDataKind {
    /** История ответов на вопросы. */
    ANSWER_HISTORY,

    /** Расписание повторов. */
    REPETITION_SCHEDULE,

    /** Неопубликованные черновики квестов. */
    UNPUBLISHED_QUEST_DRAFTS,
}
