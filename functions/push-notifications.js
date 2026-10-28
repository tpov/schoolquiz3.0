"use strict";

/**
 * Push по критическим событиям.
 *
 * Единственный канал, по которому серверное событие доходит до устройства при закрытом
 * приложении. Сообщение несёт повод, а не данные (AD-20): ровно три поля — `eventType`,
 * `entityRef`, `targetUid`. Ни баланса, ни награды, ни тела изменившегося документа: получив
 * push, приложение синхронизируется обычным путём, и полезная нагрузка источником истины не
 * становится.
 *
 * Адреса доставки лежат в `users/{uid}/devices/{token}` — технический реестр, который пишет
 * клиент и восстанавливает клиент при следующем запуске. Поэтому удаление мёртвого токена
 * сервером потерей данных не является — единственное разрешённое удаление под `users` (NFR12).
 *
 * Имена событий живут в пространстве владеющей фичи (NFR4): `<фича>.<СОБЫТИЕ>`, а ссылка на
 * сущность — `<та же фича>.<СУЩНОСТЬ>:<id>`, как `entity_ref` очереди отложенных действий (AD-5).
 *
 * Модуль принимает `db` и `messaging` параметрами, поэтому тестируется без firebase-admin и без
 * сети: index.js отдаёт ему `admin.firestore()` и `admin.messaging()`.
 */

/** Подколлекция адресов доставки под документом пользователя. */
const DEVICES_SUBCOLLECTION = "devices";

/**
 * Пространства имён фич — те же, что у `operation` и `entity_ref` очереди (NFR4).
 *
 * Список закрыт: событие с префиксом вне него — не новая фича, а опечатка, и реестр обязан
 * упасть на ней в тесте, а не молча отправить сообщение, которое клиент не разберёт.
 */
const FEATURE_NAMESPACES = Object.freeze([
  "app_shell",
  "auth",
  "economy",
  "leaderboard",
  "lesson",
  "lesson_runner",
  "local",
  "minigame",
  "profile",
  "qualification",
  "quest",
  "quest_authoring",
  "question",
  "section",
  "social",
  "theme",
]);

/**
 * Реестр событий, по которым сервер шлёт push.
 *
 * Три события спайна (CAP-10). Каждое — `eventType` и функция, собирающая `entityRef` из
 * идентификатора сущности; префиксы обоих обязаны совпадать. Состав событий спекой не закрыт
 * (Open Questions эпика 10): «сертификат» в коде отсутствует, ближайший существующий аналог —
 * одобренная верификация.
 */
const PUSH_EVENTS = Object.freeze({
  /** Квест автора продвинулся: заявка на ревью опубликована. */
  QUEST_PUBLISHED: Object.freeze({
    eventType: "quest_authoring.QUEST_PUBLISHED",
    entityRef: (questId) => `quest_authoring.QUEST:${questId}`,
  }),
  /** Предложение прошло проверку и доехало до автора цели (AD-33, история 4.25). */
  QUEST_PROPOSAL_RECEIVED: Object.freeze({
    eventType: "quest_authoring.QUEST_PROPOSAL_RECEIVED",
    entityRef: (proposalId) => `quest_authoring.PROPOSAL:${proposalId}`,
  }),
  /** Автор цели решил предложение; исход видит предложивший (история 4.25). */
  QUEST_PROPOSAL_DECIDED: Object.freeze({
    eventType: "quest_authoring.QUEST_PROPOSAL_DECIDED",
    entityRef: (proposalId) => `quest_authoring.PROPOSAL:${proposalId}`,
  }),
  /** Уровень квалификации автора (рейтинг от звёзд игроков) изменился. */
  AUTHOR_RATING_CHANGED: Object.freeze({
    eventType: "qualification.AUTHOR_RATING_CHANGED",
    entityRef: (uid) => `qualification.AUTHOR_RATING:${uid}`,
  }),
  /** Заявка на верификацию одобрена. */
  VERIFICATION_APPROVED: Object.freeze({
    eventType: "profile.VERIFICATION_APPROVED",
    entityRef: (uid) => `profile.VERIFICATION:${uid}`,
  }),
});

/**
 * Коды ответа FCM, после которых токен мёртв: адрес больше никому не принадлежит, и держать его
 * значит слать в пустоту при каждом событии. Любой другой код — временный, документ остаётся,
 * повтор внутри запроса не запускается: следующая попытка происходит при следующем событии.
 */
const DEAD_TOKEN_CODES = Object.freeze([
  "messaging/registration-token-not-registered",
  "messaging/invalid-argument",
]);

/** Ошибка модуля с кодом в терминах HttpsError; index.js переводит её в HttpsError. */
class PushError extends Error {
  constructor(code, message) {
    super(message);
    this.name = "PushError";
    this.code = code;
  }
}

/** Пространство имён — всё до первой точки; без точки имени нет. */
function namespaceOf(name) {
  if (typeof name !== "string") return null;
  const dot = name.indexOf(".");
  return dot > 0 ? name.slice(0, dot) : null;
}

/**
 * Полезная нагрузка сообщения — ровно три поля, все строками (FCM другого не принимает).
 *
 * Здесь и только здесь задаётся форма; тест фиксирует состав ключей, чтобы число баланса или
 * награды не просочилось в уведомление в обход AD-20.
 */
function buildPushData(event, entityId, targetUid) {
  if (!event || typeof event.eventType !== "string" || typeof event.entityRef !== "function") {
    throw new PushError("internal", "push event is not from the registry");
  }
  const id = String(entityId === null || entityId === undefined ? "" : entityId).trim();
  const uid = String(targetUid === null || targetUid === undefined ? "" : targetUid).trim();
  if (!id) throw new PushError("internal", "push entity id is required");
  if (!uid) throw new PushError("internal", "push target uid is required");
  return {
    eventType: event.eventType,
    entityRef: event.entityRef(id),
    targetUid: uid,
  };
}

/** Сообщение FCM на один токен. Данные — ровно то, что собрал `buildPushData`. */
function buildPushMessage(token, data) {
  return {
    token,
    data,
    android: {priority: "high"},
  };
}

/** Мёртв ли токен по ответу FCM. */
function isDeadTokenError(error) {
  const code = error && (error.code || (error.errorInfo && error.errorInfo.code));
  return typeof code === "string" && DEAD_TOKEN_CODES.includes(code);
}

function devicesCollection(db, uid) {
  return db.collection("users").doc(uid).collection(DEVICES_SUBCOLLECTION);
}

/**
 * Шлёт событие на каждый токен аккаунта.
 *
 * Отказ по одному токену не отменяет остальных: отправки идут независимо, итог собирается по
 * каждой. Мёртвый токен убирается из реестра; временный сбой оставляет документ на месте.
 * Наружу ничего не бросает — вызывающая операция уже зафиксирована, и её результат от доставки
 * уведомления не зависит.
 *
 * @returns {Promise<{sent: number, failed: number, removed: number}>}
 */
async function sendPushToUser(deps, event, entityId, targetUid, log) {
  const outcome = {sent: 0, failed: 0, removed: 0};
  const logger = log || {warn() {}};
  let data;
  try {
    data = buildPushData(event, entityId, targetUid);
  } catch (error) {
    logger.warn("push: refusing to build message", {message: error.message});
    return outcome;
  }
  let tokenDocs;
  try {
    const snapshot = await devicesCollection(deps.db, data.targetUid).get();
    tokenDocs = snapshot.docs || [];
  } catch (error) {
    logger.warn("push: devices unreadable", {targetUid: data.targetUid, message: error.message});
    return outcome;
  }
  if (tokenDocs.length === 0) return outcome;

  const results = await Promise.allSettled(
    tokenDocs.map((doc) => deps.messaging.send(buildPushMessage(doc.id, data))),
  );
  for (let index = 0; index < results.length; index += 1) {
    const result = results[index];
    if (result.status === "fulfilled") {
      outcome.sent += 1;
      continue;
    }
    outcome.failed += 1;
    const error = result.reason;
    const code = error && error.code;
    if (!isDeadTokenError(error)) {
      logger.warn("push: send failed", {eventType: data.eventType, code, message: error && error.message});
      continue;
    }
    try {
      await tokenDocs[index].ref.delete();
      outcome.removed += 1;
    } catch (deleteError) {
      logger.warn("push: dead token not removed", {code, message: deleteError.message});
    }
  }
  return outcome;
}

/** Идентификатор документа: непустой, без разделителя пути. */
function documentId(value, fieldName) {
  const id = String(value === null || value === undefined ? "" : value).trim();
  if (!id || id.includes("/")) {
    throw new PushError("invalid-argument", `${fieldName} is required`);
  }
  return id;
}

/**
 * Снимает токен с чужого аккаунта.
 *
 * Клиент, получивший сообщение с чужим `targetUid`, сам удалить документ прежнего владельца не
 * может — правило владельца после смены аутентификации ему это запрещает. Сервер под правами
 * администратора удаляет ровно один документ `users/<targetUid>/devices/<token>` и ничего больше.
 *
 * Собственный `uid` отклоняется: для своего токена у клиента есть правило владельца, и обходной
 * путь через администратора ему не нужен.
 */
async function detachDeviceToken(db, {callerUid, token, targetUid}) {
  if (!callerUid) throw new PushError("unauthenticated", "Authenticated uid is required");
  const tokenId = documentId(token, "token");
  const uid = documentId(targetUid, "targetUid");
  if (uid === callerUid) {
    throw new PushError("permission-denied", "Use the owner rule to detach your own token");
  }
  await devicesCollection(db, uid).doc(tokenId).delete();
  return {targetUid: uid, detached: true};
}

module.exports = {
  DEVICES_SUBCOLLECTION,
  DEAD_TOKEN_CODES,
  FEATURE_NAMESPACES,
  PUSH_EVENTS,
  PushError,
  buildPushData,
  buildPushMessage,
  detachDeviceToken,
  isDeadTokenError,
  namespaceOf,
  sendPushToUser,
};
