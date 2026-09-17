package com.tpov.schoolquiz.platform.firebase.quest_authoring

import com.google.firebase.firestore.DocumentSnapshot
import com.tpov.schoolquiz.platform.firebase.util.longField

/** Поле, которое назначает и пишет только сервер (AD-12). */
private const val VERSION_FIELD = "version"

/** Первая версия существующего документа. Всё, что меньше, версией не является. */
private const val FIRST_VERSION = 1L

/**
 * Версия приватного квеста, назначенная сервером, — или её отсутствие.
 *
 * Отсутствие выражается пустотой и ничем другим. Прежде на это место при отсутствующем поле
 * подставлялся `localRevision` — счётчик правок, который присылает сам клиент, — и приёмник
 * сравнивал клиента с клиентом же: своё число против своего, выданного за серверное. Отсюда
 * бралось «расхождение» у автора, который просто работал.
 *
 * Значение меньше [FIRST_VERSION] — не версия, а след прежнего порядка (или мусор): сервер
 * начинает ряд с единицы, а ноль у него означает «документа не было». Читать такое как известную
 * версию значит сравнивать с числом, которого сервер никому не назначал.
 */
internal fun DocumentSnapshot.serverAssignedVersion(): Long? = longField(VERSION_FIELD)?.takeIf { it >= FIRST_VERSION }
