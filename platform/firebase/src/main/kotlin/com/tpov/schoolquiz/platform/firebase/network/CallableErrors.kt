package com.tpov.schoolquiz.platform.firebase.network

import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableReference
import com.tpov.schoolquiz.shared.core.network.SyncError
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

// Граница, на которой исключения Firebase превращаются в решение.
//
// Выше по стеку про Firebase знать нельзя (правила слоёв), а решать «повторять или нет» по тексту
// сообщения — ровно то, что AD-15 запрещает. Здесь SDK-исключение читается один раз и дальше едет
// доменным типом. Сама таблица решений вынесена в syncErrorForCode и не касается типов SDK: их
// статические инициализаторы не поднимаются в обычном JVM-тесте.

/**
 * Сколько ждём ответа, прежде чем считать, что связи нет.
 *
 * Клиент Firebase по умолчанию ждёт семьдесят секунд — за это время игрок успевает решить, что
 * приложение сломалось. Тридцати хватает любому вызову, который сегодня есть в проекте, а разницу
 * между «долго» и «никогда» игрок всё равно не различает.
 */
const val CALLABLE_TIMEOUT_SECONDS: Long = 30L

/**
 * Код, которым сервер сообщает «предусловие ещё не выполнено» — в отличие от «отказано».
 *
 * Различать эти два случая обязан сервер (AD-27): мутация, чьё предусловие удовлетворяется
 * другой, ещё летящей из той же очереди, должна повторяться, а не отвергаться. Сервер научится
 * это говорить в эпике E4; до тех пор ветвь существует, но не приходит.
 */
const val PRECONDITION_PENDING_DETAIL: String = "precondition-pending"

/**
 * Читает исключение как одну из пяти ветвей [SyncError].
 *
 * Отмену корутины не трогаем: это не ошибка обращения, и подменять её ошибкой значит проглотить
 * остановку.
 */
fun Throwable.toSyncError(): SyncError =
    when (this) {
        is CancellationException -> throw this
        is FirebaseFunctionsException -> syncErrorForCode(code.name, message, details, cause is IOException)
        // Сюда попадает всё, что не доехало до сервера: нет маршрута, оборванное соединение,
        // неизвестный хост. Ответа не было, значит повторять безопасно.
        is IOException -> SyncError.NoNetwork
        else -> SyncError.Unknown(this)
    }

/**
 * Таблица решений в чистом виде: имя кода → ветвь.
 *
 * Принимает имя, а не сам `Code`, ровно для того, чтобы её можно было проверить без Firebase.
 * Имена — значения `FirebaseFunctionsException.Code`; [causedByIo] говорит, лежит ли в причине
 * исключения обрыв ввода-вывода — без этого `INTERNAL` неразличим.
 */
internal fun syncErrorForCode(
    code: String,
    message: String?,
    details: Any?,
    causedByIo: Boolean = false,
): SyncError =
    when (code) {
        // Сервер не ответил или не успел. Запрос мог и не дойти — повторяем.
        "UNAVAILABLE", "DEADLINE_EXCEEDED" -> SyncError.NoNetwork

        // Ловушка: при обрыве связи SDK заворачивает IOException в тот же INTERNAL, что и
        // настоящая серверная ошибка, и кладёт в message литеральное "INTERNAL". Отличить
        // одно от другого можно только по причине — по коду они неразличимы.
        "INTERNAL" -> if (causedByIo) SyncError.NoNetwork else SyncError.Unknown()

        // Канонический код состязания: сущность изменилась под нами.
        "ABORTED" -> SyncError.VersionConflict()

        // Отличить «ещё нет» от «нет» может только сервер, и он говорит это в деталях: сам по
        // себе FAILED_PRECONDITION в этом проекте означает отказ («не хватает ноликов»).
        "FAILED_PRECONDITION" ->
            if (details == PRECONDITION_PENDING_DETAIL) {
                SyncError.PreconditionNotMet
            } else {
                refused(code, message)
            }

        // Сервер посмотрел и отказал. Повтор ничего не изменит.
        "PERMISSION_DENIED",
        "UNAUTHENTICATED",
        "INVALID_ARGUMENT",
        "NOT_FOUND",
        "ALREADY_EXISTS",
        "OUT_OF_RANGE",
        "RESOURCE_EXHAUSTED",
        "UNIMPLEMENTED",
        -> refused(code, message)

        else -> SyncError.Unknown()
    }

private fun refused(
    code: String,
    message: String?,
): SyncError.Refused = SyncError.Refused(reason = message.orEmpty().ifBlank { code })

/** Таймаут для вызова. Применять к каждому обращению, иначе спиннер живёт семьдесят секунд. */
fun HttpsCallableReference.withAppTimeout(): HttpsCallableReference =
    apply { setTimeout(CALLABLE_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
