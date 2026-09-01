"use strict";

/**
 * Ключ идемпотентности для отложенных мутаций.
 *
 * Приложение работает офлайн-первым: действие игрока встаёт в очередь и уезжает при первой
 * возможности. Доставка при этом ненадёжна дважды — сеть может оборвать ответ уже после того, как
 * сервер всё сделал, и клиент честно повторит. Без ключа такой повтор списал бы второй раз.
 *
 * Проверка повтора сегодня держится на том, что подвернулось: у прохождений и оценок — на doc-id,
 * который выбирает клиент, у разблокировки урока — на естественной идемпотентности по набору
 * купленного. Действию без естественного уникального ключа опереться не на что.
 *
 * Правило (AD-1): сервер хранит выполненные ключи и на повтор возвращает сохранённый результат, а
 * не выполняет заново. Запись ключа и эффект операции фиксируются одной транзакцией; где эффект в
 * одну транзакцию не умещается, ключ резервируется до эффекта, и повтор по зарезервированному, но
 * не завершённому ключу ждёт, а не выполняет операцию второй раз.
 *
 * Модуль чистый — тестируется без firebase-admin.
 */

/** Ключ выдан, эффект ещё не применён. Повтор по такому ключу ждёт, а не выполняет. */
const STATE_RESERVED = "reserved";
/** Эффект применён, результат сохранён. Повтор получает сохранённое. */
const STATE_COMPLETED = "completed";

/**
 * Сколько храним выполненный ключ.
 *
 * Строго больше предельного возраста записи в клиентской очереди (AD-22): запись, пережившая свой
 * срок и всё же уехавшая, была бы для сервера новой операцией — ровно то двойное применение, ради
 * которого ключ и существует. Оба числа объявлены один раз в config/sync-params.json и читаются обеими сторонами — это
 * запас, а не совпадение, и не две копии, которые однажды разъедутся.
 */
const SYNC_PARAMS = require("../config/sync-params.json");
const MUTATION_KEY_TTL_MS = SYNC_PARAMS.mutationKeyTtlMs;

/** Предельный возраст записи в клиентской очереди — вторая половина той же пары. */
const QUEUE_RECORD_MAX_AGE_MS = SYNC_PARAMS.queueRecordMaxAgeMs;

if (!(MUTATION_KEY_TTL_MS > QUEUE_RECORD_MAX_AGE_MS)) {
  throw new Error(
    "config/sync-params.json нарушает AD-1: срок хранения ключа обязан быть строго больше " +
      "предельного возраста записи очереди",
  );
}

/**
 * Сколько ждём чужую незавершённую попытку, прежде чем считать её брошенной.
 *
 * Инстанс мог умереть между резервированием ключа и применением эффекта. Держать ключ вечно
 * значит навсегда заблокировать действие; отпускать сразу — разрешить два одновременных
 * выполнения. Минута заведомо больше любого вызова и заведомо меньше терпения игрока.
 */
const RESERVATION_TIMEOUT_MS = 60 * 1000;

/** Что делать с пришедшей мутацией. */
const DECISION_EXECUTE = "execute";
const DECISION_REPLAY = "replay";
const DECISION_WAIT = "wait";

/**
 * Решение по входящей мутации на основании того, что лежит по её ключу.
 *
 * @param {object|null} existing запись ключа, если она есть.
 * @param {number} nowMs серверное время.
 * @returns {{decision: string, result: object|null, reason: string}}
 */
function decideMutation(existing, nowMs) {
  const now = safeNumber(nowMs, 0);

  if (!existing) {
    return {decision: DECISION_EXECUTE, result: null, reason: "new key"};
  }

  if (existing.state === STATE_COMPLETED) {
    if (isExpired(existing, now)) {
      // Ключ пережил срок хранения. Выполнять заново нельзя: клиент, который держал запись
      // дольше собственного предельного возраста, нарушил договор, и повтор был бы вторым
      // списанием. Отдаём сохранённое, пока запись существует.
      return {decision: DECISION_REPLAY, result: existing.result || null, reason: "expired but known"};
    }
    return {decision: DECISION_REPLAY, result: existing.result || null, reason: "already completed"};
  }

  if (existing.state === STATE_RESERVED) {
    const age = now - safeNumber(existing.reservedAtMs, 0);
    if (age >= RESERVATION_TIMEOUT_MS) {
      // Прежняя попытка не дошла до конца — инстанс умер. Забираем ключ себе.
      return {decision: DECISION_EXECUTE, result: null, reason: "stale reservation"};
    }
    return {decision: DECISION_WAIT, result: null, reason: "another attempt in flight"};
  }

  return {decision: DECISION_EXECUTE, result: null, reason: "unknown state"};
}

/** Запись ключа в момент резервирования — до того, как эффект применён. */
function reservationRecord(mutationId, uid, operation, nowMs) {
  return {
    mutationId: stringValue(mutationId),
    uid: stringValue(uid),
    operation: stringValue(operation),
    state: STATE_RESERVED,
    reservedAtMs: safeNumber(nowMs, 0),
    expiresAtMs: safeNumber(nowMs, 0) + MUTATION_KEY_TTL_MS,
  };
}

/** Запись ключа после успешного применения: сохраняет то, что вернём на повтор. */
function completionRecord(reservation, result, nowMs) {
  return {
    ...reservation,
    state: STATE_COMPLETED,
    completedAtMs: safeNumber(nowMs, 0),
    expiresAtMs: safeNumber(nowMs, 0) + MUTATION_KEY_TTL_MS,
    result: result === undefined ? null : result,
  };
}

/**
 * Проверяет форму пришедшей мутации.
 *
 * Ключ обязателен и обязан быть тем же при повторе, поэтому его форма фиксирована: без этого
 * клиент, генерирующий ключ иначе, тихо получил бы двойное выполнение.
 */
function validateMutation(payload) {
  const mutationId = stringValue(payload && payload.mutationId);
  const operation = stringValue(payload && payload.operation);

  if (!mutationId) return {valid: false, reason: "mutationId is required"};
  if (mutationId.length > MAX_KEY_LENGTH) return {valid: false, reason: "mutationId is too long"};
  if (!/^[A-Za-z0-9_-]+$/.test(mutationId)) return {valid: false, reason: "mutationId has invalid characters"};
  if (!operation) return {valid: false, reason: "operation is required"};

  return {valid: true, mutationId, operation};
}

/** Принадлежит ли ключ этому игроку. Чужой ключ — не повтор, а попытка прочитать чужой результат. */
function belongsTo(existing, uid) {
  if (!existing) return true;
  return stringValue(existing.uid) === stringValue(uid);
}

function isExpired(existing, nowMs) {
  const expires = safeNumber(existing.expiresAtMs, 0);
  return expires > 0 && nowMs >= expires;
}

const MAX_KEY_LENGTH = 128;

function safeNumber(value, fallback) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function stringValue(value) {
  if (value === null || value === undefined) return "";
  return String(value);
}


/**
 * Есть ли обработчик под эту операцию.
 *
 * Незнакомая операция — окончательный отказ, а не повод повторять: клиент новее сервера, и ни
 * одна попытка этого не изменит. Разводится отдельно от «ещё нет», потому что решение о повторе
 * принимается по типу ошибки, а не по тексту (AD-15).
 */
function resolveOperation(registry, operation) {
  const name = stringValue(operation);
  const handler = registry && Object.prototype.hasOwnProperty.call(registry, name) ? registry[name] : null;
  if (typeof handler !== "function") {
    return {known: false, handler: null, reason: `unknown operation ${name || "(empty)"}`};
  }
  return {known: true, handler, reason: "ok"};
}

module.exports = {
  DECISION_EXECUTE,
  DECISION_REPLAY,
  DECISION_WAIT,
  MUTATION_KEY_TTL_MS,
  RESERVATION_TIMEOUT_MS,
  STATE_COMPLETED,
  STATE_RESERVED,
  belongsTo,
  completionRecord,
  decideMutation,
  reservationRecord,
  resolveOperation,
  validateMutation,
};
