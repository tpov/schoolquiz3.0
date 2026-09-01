package com.tpov.schoolquiz.shared.core.network

/**
 * Носитель [SyncError] там, где по контракту едет `Throwable`.
 *
 * Проект возвращает с границ `Result<T>`, а `Result.failure` принимает только `Throwable`. Делать
 * ради этого сам [SyncError] исключением нельзя: он живёт в общем коде, его сравнивают и
 * разбирают по ветвям, а исключение тащит за собой стек и семантику броска. Поэтому тип остаётся
 * данными, а до `Result` доезжает в этой обёртке.
 *
 * Бросается на границе с платформой — там, где SDK-исключение уже прочитано, — и разбирается
 * в презентации через [syncErrorOrNull].
 */
class SyncFailure(
    val error: SyncError,
    cause: Throwable? = null,
) : Exception(error.toString(), cause)

/**
 * Ошибка обращения к серверу, если неудача была именно ею.
 *
 * Возвращает `null` для всего остального: провала валидации, отсутствия авторизации и прочего,
 * что до сервера не доходило и решения о повторе не требует.
 */
fun Throwable?.syncErrorOrNull(): SyncError? = (this as? SyncFailure)?.error

/** То же для результата: короткая форма самой частой проверки в презентации. */
fun Result<*>.syncErrorOrNull(): SyncError? = exceptionOrNull().syncErrorOrNull()
