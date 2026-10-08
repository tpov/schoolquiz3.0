package com.tpov.schoolquiz.platform.firebase.network

import com.google.firebase.firestore.FirebaseFirestoreException
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
 * Пометка, которой сервер называет расхождение версий, и поле, в котором он кладёт свою версию.
 *
 * Детали здесь — объект, а не строка, в отличие от [PRECONDITION_PENDING_DETAIL]: сама пометка
 * говорит только «конфликт», а разрешать его нечем без числа. Обе половины обязаны совпадать с
 * `VERSION_CONFLICT_DETAIL` и `versionConflictDetail` в `functions/mutation-queue.js`.
 */
const val VERSION_CONFLICT_DETAIL: String = "version-conflict"

/**
 * Пометка, которой сервер называет отказ по согласию с платными условиями (CAP-16, AD-5),
 * и поле объекта, в котором она едет.
 *
 * Детали здесь — объект, как у [VERSION_CONFLICT_DETAIL]: рядом с пометкой сервер кладёт
 * `change` — что именно в платных полях разошлось, — но ветви решения оно не нужно: условия и
 * расхождение клиент называет сам из своих таблиц, а показывает их согласующий экран. Значение
 * и форма обязаны совпадать с `TERMS_CONSENT_DETAIL` в `functions/mutation-terms.js`.
 */
const val TERMS_CONSENT_DETAIL: String = "terms-consent-required"
private const val DETAIL_CODE = "code"
private const val DETAIL_SERVER_VERSION = "serverVersion"

/**
 * Читает исключение как одну из пяти ветвей [SyncError].
 *
 * Отмену корутины не трогаем: это не ошибка обращения, и подменять её ошибкой значит проглотить
 * остановку.
 *
 * Firestore приходит сюда наравне с вызовами функций: коды у него те же — имена статусов gRPC, —
 * поэтому таблица решений одна на обе стороны, а не своя у отправки и своя у чтения. Деталей
 * Firestore не носит: конфликт версий и «предусловие ещё не выполнено» их требуют и потому
 * приезжают только от приёмника мутаций.
 */
fun Throwable.toSyncError(): SyncError =
    when (this) {
        is CancellationException -> throw this
        is FirebaseFunctionsException -> syncErrorForCode(code.name, message, details, cause is IOException)
        is FirebaseFirestoreException ->
            syncErrorForCode(code.name, message, details = null, causedByIo = cause is IOException)
        // Сюда попадает всё, что не доехало до сервера: нет маршрута, оборванное соединение,
        // неизвестный хост. Ответа не было, значит повторять безопасно.
        is IOException -> SyncError.NoNetwork
        else -> SyncError.Unknown(this)
    }

/**
 * Таблица решений в чистом виде: имя кода → ветвь.
 *
 * Принимает имя, а не сам `Code`, ровно для того, чтобы её можно было проверить без Firebase.
 * Имена — значения `FirebaseFunctionsException.Code` и `FirebaseFirestoreException.Code`: и то и
 * другое — статусы gRPC, поэтому таблица у отправки и у чтения общая. [causedByIo] говорит, лежит
 * ли в причине исключения обрыв ввода-вывода — без этого `INTERNAL` неразличим.
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

        // Канонический код состязания: сущность изменилась под нами. Версию, до которой сущность
        // успела уехать, сервер кладёт в детали отдельным полем — из текста сообщения её брать
        // нельзя (AD-15), а без числа разрешать конфликт нечем (AD-24).
        "ABORTED" -> SyncError.VersionConflict(serverVersion = serverVersionFrom(details))

        // Отличить «ещё нет» от «нет» может только сервер, и он говорит это в деталях: сам по
        // себе FAILED_PRECONDITION в этом проекте означает отказ («не хватает ноликов»).
        // Отказ по согласию с платными условиями — третий случай, и ветвь у него своя: не
        // карантин (откатывать нечего — попытка написана) и не вечный повтор (без согласия
        // повтор вернёт тот же отказ), а ожидание решения игрока, как конфликт версий.
        "FAILED_PRECONDITION" ->
            when {
                details == PRECONDITION_PENDING_DETAIL -> SyncError.PreconditionNotMet
                details.namesCode(TERMS_CONSENT_DETAIL) -> SyncError.TermsConsentRequired
                else -> refused(code, message)
            }

        // Сервер занят, а не отказал. Cloud Run под нагрузкой отдаёт 429, SDK превращает его в
        // RESOURCE_EXHAUSTED — и отнести это к отказу значит уводить мутацию в карантин при
        // всплеске нагрузки, а карантин по AD-28 откатывает локальное изменение. Троттлинг обязан
        // откладывать, а не уничтожать: повтор с растущей паузой и есть правильный ответ.
        //
        // Не NoNetwork: сеть есть, и говорить игроку «нужен интернет» было бы неправдой.
        "RESOURCE_EXHAUSTED" -> SyncError.Unknown()

        // Сервер посмотрел и отказал. Повтор ничего не изменит.
        "PERMISSION_DENIED",
        "UNAUTHENTICATED",
        "INVALID_ARGUMENT",
        "NOT_FOUND",
        "ALREADY_EXISTS",
        "OUT_OF_RANGE",
        "UNIMPLEMENTED",
        -> refused(code, message)

        else -> SyncError.Unknown()
    }

/**
 * Достаёт серверную версию из деталей отказа.
 *
 * Числа приезжают из JSON тем типом, каким их разобрал SDK, — `Double` для дробных, `Integer` для
 * целых, — поэтому читаются как [Number], а не как `Long`. Чужой ABORTED без наших деталей даёт
 * пустоту: ветвь остаётся конфликтом, но номера не несёт, и разрешение спросит сервер заново.
 */
private fun serverVersionFrom(details: Any?): Long? {
    val map = details as? Map<*, *> ?: return null
    if (map[DETAIL_CODE] != VERSION_CONFLICT_DETAIL) return null
    return when (val raw = map[DETAIL_SERVER_VERSION]) {
        is Number -> raw.toLong()
        is String -> raw.toLongOrNull()
        else -> null
    }
}

/** Читает ли объект деталей пометку с именем ветви — тем же чтением, что и [serverVersionFrom]. */
private fun Any?.namesCode(expected: String): Boolean =
    (this as? Map<*, *>)?.get(DETAIL_CODE) == expected

private fun refused(
    code: String,
    message: String?,
): SyncError.Refused = SyncError.Refused(reason = message.orEmpty().ifBlank { code })

/** Таймаут для вызова. Применять к каждому обращению, иначе спиннер живёт семьдесят секунд. */
fun HttpsCallableReference.withAppTimeout(): HttpsCallableReference =
    apply { setTimeout(CALLABLE_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
