package com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.AccountDataKind
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.AccountSwitchLoss

/**
 * Вопрос перед сменой аккаунта: что пропадёт, если переключиться (AD-8).
 *
 * Не сообщение, а состояние: висит, пока игрок не нажмёт одну из двух кнопок, и до этого `uid`
 * не меняется. Предупреждение и есть согласие — подтверждение удаляет перечисленное безвозвратно.
 *
 * Числа приходят разложенными и без нулей, а суммы нет вовсе: «6 действий» скрывает и конфликт,
 * ждущий решения игрока, и карантин, и ничего не говорит про историю ответов, расписание повторов
 * и неопубликованные черновики. Согласие, полученное на одну цифру, не покрывает того, что
 * удаляется (история 6.4).
 *
 * @property counts ненулевые числа по состояниям, в постоянном порядке.
 * @property namedData виды данных без серверного двойника, которые исчезнут вместе с аккаунтом.
 * @property isKnown удалось ли сосчитать. Нет — экран говорит «неизвестно сколько», а не молчит.
 */
data class AccountSwitchWarning(
    val counts: List<AccountSwitchCount>,
    val namedData: List<AccountDataKind>,
    val isKnown: Boolean,
)

/** Одно число одной строкой: своё состояние, своё слово. */
data class AccountSwitchCount(
    val kind: AccountSwitchCountKind,
    val value: Int,
)

/** Состояния, про которые предупреждение говорит отдельно. Порядок строк на экране — этот. */
enum class AccountSwitchCountKind {
    WAITING,
    WAITING_PRECONDITION,
    CONFLICTED,
    QUARANTINED,
    SYNCHRONOUS,
}

/**
 * Раскладывает потерю в строки экрана: нули отбрасываются здесь, а не в разметке.
 *
 * Так «нет суммарного числа» проверяется тестом на модели, а не глазами по разметке.
 */
fun AccountSwitchLoss.toWarning(): AccountSwitchWarning =
    AccountSwitchWarning(
        counts =
            listOf(
                AccountSwitchCount(AccountSwitchCountKind.WAITING, waiting),
                AccountSwitchCount(AccountSwitchCountKind.WAITING_PRECONDITION, waitingPrecondition),
                AccountSwitchCount(AccountSwitchCountKind.CONFLICTED, conflicted),
                AccountSwitchCount(AccountSwitchCountKind.QUARANTINED, quarantined),
                AccountSwitchCount(AccountSwitchCountKind.SYNCHRONOUS, synchronous),
            ).filter { it.value > 0 },
        namedData = namedData,
        isKnown = isKnown,
    )
