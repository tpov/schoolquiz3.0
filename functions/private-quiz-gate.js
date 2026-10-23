"use strict";

/**
 * Старт попытки приватного квиза (AD-29) — атомарный гейт ссылки.
 *
 * Правила двери, одной транзакцией:
 *  - квест существует в дереве автора (`private/{authorUid}/catalogs/{cat}/quests/{qid}`);
 *  - ссылка включена и не истекла (`linkExpiresAtMs` — момент, ДО которого старт разрешён);
 *  - `startedCount < attemptLimit` — лимит целый, повышение допускает разницу;
 *  - решение пишется под `starts/{startId}`: повтор того же startId возвращает сохранённый
 *    старт и не расходует лимит второй раз (retry ключа — то же намерение, AD-2).
 *
 * Вызов — от участника: `uid` в авторизации, `ownerUid` в теле называет автора квеста.
 * Настройки читаются из документа квеста (`privateQuizSettings`); их отсутствие — дефолты
 * AD-29 (лимит 1, show-result скрыт, ссылка без срока), а не нулевой лимит: поле появилось
 * позже квеста, и наказывать отсутствием попыток было бы запиранием ссылки.
 *
 * Списание зарядов STANDARD/PLASMA и плата здесь не участвуют (AD-29): у приватного прохода
 * другая экономика, и её гейт живёт в своих обработчиках.
 */

const PRIVATE_ROOT = "private";
const START_ID_PATTERN = /^[A-Za-z0-9_-]{9,128}$/;

function fail(code, message) {
  const error = new Error(message);
  error.code = code;
  throw error;
}

function normalizeSettings(raw) {
  const source = raw && typeof raw === "object" && !Array.isArray(raw) ? raw : {};
  const attemptLimit = source.attemptLimit === undefined ? 1 : source.attemptLimit;
  if (!Number.isSafeInteger(attemptLimit) || attemptLimit < 1) {
    fail("INVALID_SETTINGS", "attemptLimit must be an integer >= 1");
  }
  let linkExpiresAtMs = null;
  if (source.linkExpiresAtMs !== undefined && source.linkExpiresAtMs !== null) {
    if (!Number.isSafeInteger(source.linkExpiresAtMs) || source.linkExpiresAtMs < 0) {
      fail("INVALID_SETTINGS", "linkExpiresAtMs must be a non-negative integer or null");
    }
    linkExpiresAtMs = source.linkExpiresAtMs;
  }
  const startedCount = source.startedCount === undefined ? 0 : source.startedCount;
  if (!Number.isSafeInteger(startedCount) || startedCount < 0) {
    fail("INVALID_SETTINGS", "startedCount must be a non-negative integer");
  }
  return {
    attemptLimit,
    showResult: source.showResult === true,
    linkExpiresAtMs,
    enabled: source.enabled !== false,
    startedCount,
  };
}

function requireRefId(name, value) {
  if (typeof value !== "string" || !value.trim() || value.includes("/")) {
    fail("INVALID_ARGUMENT", `${name} is required and must not contain "/"`);
  }
  return value;
}

function requireStartId(value) {
  if (typeof value !== "string" || !START_ID_PATTERN.test(value)) {
    fail("INVALID_ARGUMENT", "startId must be 9..128 chars of [A-Za-z0-9_-]");
  }
  return value;
}

/** Чистый вход гейта: db подставляет корень, uid — авторизованный участник, now — серверное время. */
function startPrivateQuizAttempt({db, uid, data, now}) {
  const body = data || {};
  const ownerUid = requireRefId("ownerUid", body.ownerUid);
  const catalogId = requireRefId("catalogId", body.catalogId);
  const questId = requireRefId("questId", body.questId);
  const startId = requireStartId(body.startId);
  if (!Number.isSafeInteger(now)) fail("INVALID_ARGUMENT", "now must be an integer");

  const questRef = db.doc(`${PRIVATE_ROOT}/${ownerUid}/catalogs/${catalogId}/quests/${questId}`);
  const startRef = questRef.collection("starts").doc(startId);

  return db.runTransaction(async (transaction) => {
    const [questSnap, startSnap] = await Promise.all([
      transaction.get(questRef),
      transaction.get(startRef),
    ]);
    // Повтор того же startId — сохранённое решение: лимит не расходуется второй раз.
    if (startSnap.exists) return startSnap.data();
    if (!questSnap.exists) fail("NOT_FOUND", "Private quiz does not exist");

    const settings = normalizeSettings(questSnap.data() && questSnap.data().privateQuizSettings);
    if (!settings.enabled) fail("LINK_DISABLED", "The private quiz link is disabled");
    if (settings.linkExpiresAtMs !== null && now >= settings.linkExpiresAtMs) {
      fail("LINK_EXPIRED", "The private quiz link expired");
    }
    if (settings.startedCount >= settings.attemptLimit) {
      fail("ATTEMPT_LIMIT_REACHED", "All attempts of this private quiz are used");
    }

    const questData = questSnap.data() || {};
    const startRecord = {
      startId,
      participantUid: uid,
      startedAtMs: now,
      attemptLimit: settings.attemptLimit,
      showResult: settings.showResult,
      // Замороженная идентичность квеста (AD-6): попытка бежит по тому содержимому, под
      // которое открыта дверь, и по срокам, посчитанным от исходного старта. Текущая
      // showResult при этом НЕ замораживается (AD-29) — поле в записи историческое, право
      // чтения результата решает настройка документа на момент чтения.
      quizSubmissionId: typeof questData.submissionId === "string" ? questData.submissionId : null,
      quizVersion: Number.isSafeInteger(questData.version) ? questData.version : null,
      // Какая по счёту попытка из лимита: повышение лимита допускает разницу, понижение
      // не обрывает начатое (AD-29) — счётчик в документе после этой транзакции равен ей.
      participantAttemptIndex: settings.startedCount + 1,
    };
    transaction.create(startRef, startRecord);
    transaction.set(
      questRef,
      {privateQuizSettings: {...settings, startedCount: settings.startedCount + 1}},
      {merge: true},
    );
    return startRecord;
  });
}

module.exports = {startPrivateQuizAttempt, normalizeSettings, PRIVATE_ROOT};
