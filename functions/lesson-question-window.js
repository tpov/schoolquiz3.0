"use strict";

/**
 * Онлайн-переход next-question по контракту ONLINE-O2 — демонстрационный владельческий каркас
 * ordinary-онлайн (история 2.6 эпика `epic-ordinary-delivery`; контракт:
 * `_bmad-output/mvp-candidate/epic-ordinary-delivery/online-o2-contract.md`).
 *
 * Что здесь есть и чего здесь нет. Полный режимный владелец ordinary-онлайн (AD-2/AD-31) — будущий
 * потребитель этого контракта; каркас несёт ровно ту временную половину, которую доказывает
 * история 2.6 одним реальным переходом: событие завершения вопроса, 60-секундное окно загрузки с
 * захваченной конфигурацией, выдача следующего вопроса и серверная квитанция готовности (ready-ACK)
 * через единственный приёмник мутаций. Скоринга, награды и списания жизней здесь нет — это остаётся
 * за `applyLessonResultEvents`; терминальный расчёт попытки ready-ACK не подменяет (§4).
 *
 * Три правила, ради которых каркас написан именно так (ONLINE-O2 §3–§7):
 *
 * - **Выдача одна.** Повтор запроса «следующий вопрос» возвращает тот же вопрос с тем же серверным
 *   deadline — вторую выдачу повтор не создаёт (§3). Ключ окна детерминирован парой
 *   `(attemptId, completedQuestionIndex)`, поэтому повтор события завершения открывает то же окно,
 *   а не новое (§7: потерянный ACK не создаёт нового окна).
 * - **Deadline ставит сервер, и ставит один раз.** Момент ответа следующего вопроса стартует от
 *   серверной выдачи в пределах окна и НИКОГДА от клиентского запроса, reconnect или тела ACK
 *   (§5, главное правило). Отсчитанные секунды захватываются в окно при открытии вместе с
 *   константой окна и не перечитываются на выдаче — captured config (§3).
 * - **Экзамен здесь не живёт.** Правило «первый ACK закрепляет deadline» — EXAM:AD-3/AD-15,
 *   существующее доказательство экзаменационного адаптера; на этот каркас оно не обобщается (§4.1),
 *   и демонстрационный ordinary-переход его не реализует.
 *
 * Чистый модуль без `firebase-admin`: чтение документов и часы подводит `index.js`, здесь только
 * вердикты — все ветви перехода проверяются таблицей `lesson-question-window.test.js` без стора,
 * а композиция с приёмником доказывается `lesson-ready-ack-handler.test.js` поверх настоящего
 * `submitMutation`.
 */

const crypto = require("crypto");

const {questionAllocatedSeconds} = require("./lesson-reward");

/**
 * Общая серверная константа ожидания подключения и загрузки следующего вопроса (AD-31; §3).
 * Захватывается в окно при открытии и не меняется по ходу попытки.
 */
const NEXT_QUESTION_WINDOW_SECONDS = 60;

const MS_PER_SECOND = 1000;

const READY_ACK_SCHEMA_VERSION = 1;

/** Окно открыто, выдачи ещё не было. */
const WINDOW_OPEN = "OPEN";

/** Выдача состоялась: часы следующего вопроса уже идут (§5). */
const WINDOW_DELIVERED = "DELIVERED";

/** Окно истекло без выдачи: режимный владелец закрыл попытку без ответа (§7). */
const WINDOW_EXPIRED = "EXPIRED";

/**
 * Путь документа окна по identity перехода.
 *
 * Идентификатор детерминирован парой `(attemptId, completedQuestionIndex)`: повтор события
 * завершения обязан прочитать то же окно, а не открыть второе (§7). Внутри — identity попытки,
 * захват конфигурации (константа окна и секунды вопроса) и выдача, если она состоялась.
 *
 * Коллекция одна, идентификатор выводится из самой пары — как `eventBucketId`, из стабильных
 * полей, а не из клиентской даты: тот же переход обязан давать тот же документ навсегда.
 */
function windowDocumentId(attemptId, completedQuestionIndex) {
  return "qw_" + hash12(`${attemptId}\u0000${completedQuestionIndex}`);
}

/** Идентификатор окна, который уезжает клиенту и потом возвращается в ready-ACK (§4). */
function mintWindowId(attemptId, completedQuestionIndex) {
  return `win_${hash12(`${attemptId}\u0000window\u0000${completedQuestionIndex}`)}`;
}

function hash12(value) {
  return crypto.createHash("sha256").update(String(value)).digest("hex").slice(0, 12);
}

/**
 * Новое окно загрузки следующего вопроса.
 *
 * Секунды ответа следующего вопроса считаются здесь из серверного содержимого вопроса — тем же
 * правилом, что у награды (`questionAllocatedSeconds`), — и замораживаются в документе вместе с
 * константой окна: выдача потом только прикладывает захваченное, ничего не перечитывая (§3).
 * Клиент не назначает ни секунды, ни момент: тело запроса на deadline не влияет (§5).
 */
function openQuestionWindow({attemptId, lessonId, lessonVersion, completedQuestionIndex, nextQuestionId, nextQuestionContent, isHard, nowMs}) {
  const allocatedSeconds = questionAllocatedSeconds(nextQuestionContent, isHard);
  return {
    schemaVersion: 1,
    windowId: mintWindowId(attemptId, completedQuestionIndex),
    attemptId,
    lessonId,
    lessonVersion,
    completedQuestionIndex,
    nextQuestionIndex: completedQuestionIndex + 1,
    nextQuestionId,
    difficulty: isHard ? "HARD" : "EASY",
    windowSeconds: NEXT_QUESTION_WINDOW_SECONDS,
    allocatedSeconds,
    openedAtMs: nowMs,
    expiresAtMs: nowMs + NEXT_QUESTION_WINDOW_SECONDS * MS_PER_SECOND,
    state: WINDOW_OPEN,
    delivery: null,
    closedAtMs: null,
    closedReason: null,
  };
}

/**
 * Тот же ли это переход.
 *
 * Повтор события завершения с другим телом (другой урок, другой вопрос, другая сложность) под той
 * же парой — конфликт (AD-6: «другое тело под тем же ключом — конфликт»), а не второе окно.
 */
function sameWindowIntent(record, request) {
  return Boolean(record) &&
    record.attemptId === request.attemptId &&
    record.lessonId === request.lessonId &&
    record.lessonVersion === request.lessonVersion &&
    record.completedQuestionIndex === request.completedQuestionIndex &&
    record.nextQuestionId === request.nextQuestionId &&
    record.difficulty === (request.isHard ? "HARD" : "EASY");
}

/**
 * Вердикт на запрос выдачи следующего вопроса (§3).
 *
 * - `ISSUE` — первая выдача: вопрос уходит, часы стартуют от серверного «сейчас»;
 * - `DELIVERED` — повтор: тот же вопрос и тот же deadline, клиенту остаётся серверный остаток;
 * - `WINDOW_EXPIRED` — окно истекло без выдачи: попытка закрывается без ответа (§7);
 * - `NO_WINDOW` — окна с такой identity нет: расхождение клиента с сервером, окончательный отказ.
 *
 * Выдача после выдачи не пересоздаётся никогда (§5): состояние `DELIVERED` без `delivery`
 * внутренне противоречиво и трактуется как истёкшее, чтобы испорченный документ не открыл вторые
 * часы.
 */
function deliveryVerdict(record, nowMs) {
  if (!record) return {status: "NO_WINDOW"};
  const issued = record.delivery || null;
  const expired = record.state === WINDOW_EXPIRED ||
    (!issued && (record.state === WINDOW_DELIVERED || nowMs >= record.expiresAtMs));
  if (expired) {
    return {status: "WINDOW_EXPIRED", record};
  }
  if (issued) {
    return {status: "DELIVERED", record, issued, remainderMs: Math.max(0, issued.answerDeadlineMs - nowMs)};
  }
  return {
    status: "ISSUE",
    record,
    issued: {
      questionIndex: record.nextQuestionIndex,
      questionId: record.nextQuestionId,
      issuedAtMs: nowMs,
      answerDeadlineMs: nowMs + record.allocatedSeconds * MS_PER_SECOND,
    },
  };
}

/** Запись документа для состоявшейся выдачи. */
function deliveryUpdate(issued) {
  return {state: WINDOW_DELIVERED, delivery: issued};
}

/** Запись документа для закрытия по expiry окна — без платного эффекта и без победы таймауту (§7). */
function expiryUpdate(nowMs) {
  return {state: WINDOW_EXPIRED, closedAtMs: nowMs, closedReason: "WINDOW_EXPIRED"};
}

/**
 * Вердикт на ready-ACK (§4) — квитанция из серверных фактов окна.
 *
 * Тело ACK влияет только на то, найдено ли окно и совпадает ли identity; ни момент выдачи, ни
 * deadline из тела не читаются и не закрепляются заново (§4: «повтор возвращает уже закреплённый
 * deadline и ничего не закрепляет заново»). ACK без состоявшейся выдачи — окончательный отказ:
 * клиент подтверждает то, чего сервер не выдавал, и повтор этого не изменит.
 */
function readyAckVerdict(record, request, nowMs) {
  if (!record) return {ok: false, code: "invalid-argument", reason: "unknown ready-ack window"};
  if (record.attemptId !== request.attemptId || record.windowId !== request.windowId) {
    return {ok: false, code: "invalid-argument", reason: "ready-ack identity does not match the window"};
  }
  const issued = record.delivery || null;
  if (record.state !== WINDOW_DELIVERED || !issued) {
    return {ok: false, code: "invalid-argument", reason: "next question was not delivered"};
  }
  if (issued.questionIndex !== request.questionIndex) {
    return {ok: false, code: "invalid-argument", reason: "ready-ack question position does not match the delivery"};
  }
  return {
    ok: true,
    receipt: {
      schemaVersion: READY_ACK_SCHEMA_VERSION,
      status: "ACKED",
      attemptId: record.attemptId,
      questionIndex: issued.questionIndex,
      windowId: record.windowId,
      issuedAtMs: issued.issuedAtMs,
      answerDeadlineMs: issued.answerDeadlineMs,
      ackedAtMs: nowMs,
    },
  };
}

module.exports = {
  MS_PER_SECOND,
  NEXT_QUESTION_WINDOW_SECONDS,
  READY_ACK_SCHEMA_VERSION,
  WINDOW_DELIVERED,
  WINDOW_EXPIRED,
  WINDOW_OPEN,
  deliveryUpdate,
  deliveryVerdict,
  expiryUpdate,
  mintWindowId,
  openQuestionWindow,
  readyAckVerdict,
  sameWindowIntent,
  windowDocumentId,
};
