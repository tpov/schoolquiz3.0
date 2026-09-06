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

// Ноль — «сущности нет» — приезжает из того же модуля, что назначает версии: два определения одного
// нуля разошлись бы ровно в тот день, когда первая версия перестала бы быть единицей.
const {NO_VERSION} = require("./entity-version.js");

/** Ключ выдан, эффект ещё не применён. Повтор по такому ключу ждёт, а не выполняет. */
const STATE_RESERVED = "reserved";
/** Эффект применён, результат сохранён. Повтор получает сохранённое. */
const STATE_COMPLETED = "completed";

/**
 * Сколько храним выполненный ключ.
 *
 * Строго больше предельного возраста записи в клиентской очереди (AD-22): запись, пережившая свой
 * срок и всё же уехавшая, была бы для сервера новой операцией — ровно то двойное применение, ради
 * которого ключ и существует. Оба числа объявлены один раз в functions/config/sync-params.json и
 * читаются обеими сторонами — это запас, а не совпадение, и не две копии, которые однажды разъедутся.
 *
 * Путь ведёт внутрь каталога, который Firebase упаковывает при деплое (`firebase.json`,
 * `source: "functions"`), и это часть контракта, а не деталь. Объявление жило в `config/` в корне
 * репозитория, и путь наверх находил файл локально, но не в облаке: каталога выше там уже нет, и
 * `index.js` не загружался целиком — вместе с ним падал весь приёмник
 * мутаций, единственный транспорт отложенных операций (AD-6). Стережёт это `deploy-package.test.js`,
 * который грузит модуль из временной копии одного лишь каталога `functions`.
 */
const SYNC_PARAMS = require("./config/sync-params.json");
const MUTATION_KEY_TTL_MS = SYNC_PARAMS.mutationKeyTtlMs;

/** Предельный возраст записи в клиентской очереди — вторая половина той же пары. */
const QUEUE_RECORD_MAX_AGE_MS = SYNC_PARAMS.queueRecordMaxAgeMs;

if (!(MUTATION_KEY_TTL_MS > QUEUE_RECORD_MAX_AGE_MS)) {
  throw new Error(
    "functions/config/sync-params.json нарушает AD-1: срок хранения ключа обязан быть строго больше " +
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
/** Ключ принадлежит другому аккаунту: не повтор, а попытка прочитать чужой результат. */
const DECISION_DENIED = "denied";

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

/**
 * Вердикт приёмника по ключу — то, что раньше было телом транзакции в `submitMutation`.
 *
 * Вынесено в чистую функцию, чтобы «повтор не выполняет обработчик второй раз» проверялось без
 * Firestore ровно тем кодом, который работает в приёмнике, а не его пересказом в тесте. Порядок
 * проверок неизменен: сначала владелец ключа, потом состояние. Резервирование возвращается
 * готовой записью: приёмник кладёт её в транзакцию, если решение — выполнять.
 *
 * @returns {{decision: string, result: object|null, reason: string, reservation: object|null}}
 */
function gateVerdict(existing, uid, mutationId, operation, nowMs) {
  if (!belongsTo(existing, uid)) {
    return {decision: DECISION_DENIED, result: null, reason: "mutationId belongs to another account", reservation: null};
  }
  const verdict = decideMutation(existing, nowMs);
  if (verdict.decision !== DECISION_EXECUTE) {
    return {decision: verdict.decision, result: verdict.result, reason: verdict.reason, reservation: null};
  }
  return {
    decision: DECISION_EXECUTE,
    result: null,
    reason: verdict.reason,
    reservation: reservationRecord(mutationId, uid, operation, nowMs),
  };
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
 * Читает тело мутации.
 *
 * На проводе оно приезжает строкой: очередь на клиенте хранит его как есть и в него не смотрит —
 * одна таблица на все типы действий (AD-5) тем и держится. Разбор — здесь, на входе, один раз для
 * всех обработчиков; иначе каждый разбирал бы строку по-своему, и первый же, кто забыл, получил бы
 * пустое тело вместо отказа.
 *
 * Объект принимается тоже: синхронный вызов того же обработчика тело строкой не заворачивает.
 *
 * @returns {{valid: boolean, payload: object|null, reason: string}}
 */
function parseMutationPayload(value) {
  if (value === null || value === undefined) return {valid: true, payload: {}, reason: "empty"};
  if (typeof value === "object" && !Array.isArray(value)) return {valid: true, payload: value, reason: "object"};
  if (typeof value !== "string") return {valid: false, payload: null, reason: "payload must be an object or a JSON string"};
  if (value.length > MAX_PAYLOAD_LENGTH) return {valid: false, payload: null, reason: "payload is too long"};

  let parsed;
  try {
    parsed = JSON.parse(value);
  } catch (error) {
    return {valid: false, payload: null, reason: "payload is not valid JSON"};
  }
  if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
    return {valid: false, payload: null, reason: "payload must decode to an object"};
  }
  return {valid: true, payload: parsed, reason: "parsed"};
}

/**
 * Потолок размера тела.
 *
 * Заявка на арену везёт весь черновик, поэтому запас большой; но бесконечным он быть не может —
 * тело приезжает из очереди, которая пережила офлайн, и разбирать мегабайты на каждой попытке
 * значит платить за них временем каждого вызова.
 */
const MAX_PAYLOAD_LENGTH = 1024 * 1024;

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

/**
 * Пометка, по которой клиент узнаёт именно конфликт версии.
 *
 * Код ответа — ABORTED: канонический код состязания, и клиент уже читает его как ветвь
 * «КонфликтВерсии» (`syncErrorForCode` в platform/firebase/.../network/CallableErrors.kt). Новую
 * пометку поверх FAILED_PRECONDITION заводить было бы хуже вдвойне: FAILED_PRECONDITION в этом
 * проекте по умолчанию означает отказ, то есть карантин и откат локального изменения (AD-28), а
 * ветвь «предусловие ещё не доехало» уже занимает там единственную пометку — вторая рядом с ней
 * различалась бы только значением строки, и ошибка в одном символе увела бы конфликт в отказ.
 *
 * Сама пометка всё равно нужна: ABORTED говорит «состязание», но не говорит, какая версия сейчас на
 * сервере. Поэтому детали ответа — объект, а не строка: {@link VERSION_CONFLICT_DETAIL} в поле
 * `code` и текущая серверная версия в `serverVersion`, читаемая без разбора текста сообщения
 * (AD-15).
 */
const VERSION_CONFLICT_DETAIL = "version-conflict";

/**
 * Читает `expected_version` из конверта мутации.
 *
 * Поле необязательное, и отсутствие его значит разное в зависимости от операции. У операции, за
 * которой не закреплена версионируемая сущность, сверять просто нечего. У версионируемой операции
 * отсутствие — это утверждение «сущности ещё нет»: клиент шлёт `expected_version` ровно тогда,
 * когда прочитал серверную версию, а её нет только у ненапечатанной сущности. Разбирает эти два
 * случая приёмник, который один знает, версионируема ли операция; здесь только форма.
 *
 * Если поле пришло — оно обязано быть целым неотрицательным числом: дробь или строка означают, что
 * клиент собрал конверт неверно, и молча пропустить такую мутацию значит применить её мимо всякой
 * сверки. Ноль допускается и означает то же, что отсутствие, — но ни один клиент его не шлёт:
 * модель требует `serverRevision` либо отсутствующим, либо >= 1.
 *
 * Имя принимается в обоих написаниях: на проводе конверт камельный (`mutationId`, `operation`), а
 * колонка очереди на клиенте — `expected_version`. Расхождение написаний между двумя сторонами
 * стоило бы тихо пропущенной сверки, поэтому оба принимаются, но противоречить друг другу не
 * имеют права.
 *
 * @returns {{valid: boolean, expectedVersion: number|null, reason: string}}
 */
function readExpectedVersion(data) {
  const envelope = data || {};
  const camel = envelope.expectedVersion;
  const snake = envelope.expected_version;
  const camelGiven = camel !== null && camel !== undefined;
  const snakeGiven = snake !== null && snake !== undefined;

  if (!camelGiven && !snakeGiven) {
    return {valid: true, expectedVersion: null, reason: "absent"};
  }
  if (camelGiven && snakeGiven && String(camel) !== String(snake)) {
    return {valid: false, expectedVersion: null, reason: "expectedVersion contradicts expected_version"};
  }

  const raw = camelGiven ? camel : snake;
  if (typeof raw === "boolean") {
    return {valid: false, expectedVersion: null, reason: "expectedVersion must be a number"};
  }
  const parsed = Number(raw);
  if (!Number.isFinite(parsed) || !Number.isInteger(parsed) || parsed < 0) {
    return {valid: false, expectedVersion: null, reason: "expectedVersion must be a non-negative integer"};
  }
  return {valid: true, expectedVersion: parsed, reason: "present"};
}

/**
 * Сверка версии: применять эффект или это конфликт.
 *
 * Конфликт — не отказ (AD-24). Мутация не применяется, но и не уничтожается: клиент получает
 * текущую серверную версию и порождает новую мутацию с новым ключом поверх неё. Поэтому здесь нет
 * ветви «отклонить навсегда» — есть только «применять» и «сервер ушёл вперёд, вот куда».
 *
 * Сущность, чью версию сюда подставили, выбирает обработчик операции по своей полезной нагрузке:
 * ядро очереди `payload` не разбирает и о сущностях не знает (AD-5).
 *
 * @param {number|null} expectedVersion из конверта; null — клиент утверждает, что сущности ещё нет.
 * @param {number} serverVersion текущая версия сущности; 0 — сущности ещё нет.
 */
function versionVerdict(expectedVersion, serverVersion) {
  const current = safeNumber(serverVersion, 0);
  // Отсутствующее ожидание — не «сверки нет», а «сущности ещё нет».
  //
  // Приёмник зовёт эту функцию только для версионируемых операций, а у версионируемой операции
  // клиент опускает поле ровно в одном случае: серверной версии он не прочитал, потому что её нет.
  // Пропускать такую мутацию без сверки значило бы, что вся сверка выключается тем, что клиент
  // промолчал, — а промолчать он может и потому, что читал версию не оттуда. Поэтому молчание
  // приравнивается к [NO_VERSION] и проверяется наравне со всем остальным: сущность уже есть —
  // конфликт, и клиент узнаёт её текущую версию.
  const expected = expectedVersion === null || expectedVersion === undefined
    ? NO_VERSION
    : safeNumber(expectedVersion, -1);
  if (expected !== current) {
    return {checked: true, conflict: true, serverVersion: current, reason: "stale expected_version"};
  }
  return {checked: true, conflict: false, serverVersion: current, reason: "up to date"};
}

/**
 * Детали ответа на конфликт.
 *
 * Отдельным полем, а не в тексте: решение о том, что делать дальше, принимается по типу ошибки и по
 * числу, а не по разбору сообщения (AD-15).
 */
/**
 * Детали отказа по конфликту версий.
 *
 * Номер кладётся только когда он есть. NO_VERSION означает «сервер версии не знает» — если
 * документ её потерял, — и подставлять вместо него ноль значило бы показать игроку «отправить
 * поверх версии 0» и уговорить его на отправку, которую сервер прочтёт как «сущности нет».
 * Отсутствие поля клиент читает как «конфликт без номера» и кнопку не показывает.
 */
function versionConflictDetail(serverVersion) {
  const known = safeNumber(serverVersion, NO_VERSION);
  if (known === NO_VERSION) return {code: VERSION_CONFLICT_DETAIL};
  return {code: VERSION_CONFLICT_DETAIL, serverVersion: known};
}

module.exports = {
  DECISION_DENIED,
  DECISION_EXECUTE,
  DECISION_REPLAY,
  DECISION_WAIT,
  MUTATION_KEY_TTL_MS,
  QUEUE_RECORD_MAX_AGE_MS,
  RESERVATION_TIMEOUT_MS,
  STATE_COMPLETED,
  STATE_RESERVED,
  VERSION_CONFLICT_DETAIL,
  belongsTo,
  completionRecord,
  decideMutation,
  gateVerdict,
  parseMutationPayload,
  readExpectedVersion,
  reservationRecord,
  resolveOperation,
  validateMutation,
  versionConflictDetail,
  versionVerdict,
};
