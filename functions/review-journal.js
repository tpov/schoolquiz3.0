"use strict";

/**
 * Журнал ревью со стороны сервера: страница изменений и поиск задания по его id.
 *
 * Журналов четыре (AD-11), и этот — единственный, который клиент читает не из Firestore напрямую, а
 * через callable: коллекция `admin/review/sync_changes` закрыта правилами. Отсюда два требования,
 * которые в трёх остальных журналах выполняет сам Firestore, а здесь обязан выполнить сервер:
 *
 *   — страница ограничена и продолжаема, курсор — пара `(changedAtMs, docId)` (AD-31). Скалярный
 *     `cursorMs` остаётся запасным входом: клиент прежней версии другого не шлёт;
 *   — запись, которую прочитать не удалось, не исчезает молча (AD-10): она уходит в ответ без
 *     `id`, и клиент считает её в `unreadable`. Молчаливый `filter(Boolean)` двигал бы курсор мимо
 *     неё, и след пропажи не оставался бы нигде.
 *
 * Второе — адресация задания. Тело записи журнала по AD-11 — ровно `{type, id, changedAtMs}`, и
 * `lessonId` в нём не живёт; урок задания сервер обязан узнать из содержательной коллекции, а не из
 * производного журнала. Задание — документ `admin/review/lessons/{lessonId}` с полем `assignmentId`;
 * у документов, записанных прежней версией сервера, этого поля нет, поэтому поиск идёт цепочкой из
 * трёх шагов и найденное задание доклеивает себе поле (AD-13: сервер работает и до, и после
 * приведения данных, а не только после).
 *
 * Модуль чистый: ни firebase-admin, ни сети. `db` приезжает аргументом, поэтому та же функция
 * работает в проде и на фейке в тесте.
 */

const {adminSyncChangePath, ADMIN_REVIEW_SYNC_CHANGES} = require("./sync-changes");

/** Коллекция заданий на ревью: документ на урок, id документа — id урока. */
const ADMIN_REVIEW_LESSONS = "admin/review/lessons";

/** Поле задания, по которому оно ищется. Пишется публикацией заявки, доклеивается при чтении. */
const ASSIGNMENT_ID_FIELD = "assignmentId";

/** Размер страницы по умолчанию — тот же, что у клиента (SYNC_PAGE_SIZE). */
const DEFAULT_PAGE_SIZE = 200;

/** Потолок страницы: клиент вправе просить меньше, но не больше. */
const MAX_PAGE_SIZE = 500;

/**
 * Сколько раз id задания разрезается по `_` в поисках урока.
 *
 * Id задания — `{submissionId}_{lessonId}`, и обе половины могут содержать `_`, поэтому позиция
 * разреза угадывается перебором. Перебор ограничен, иначе одно испорченное имя стоило бы стольких
 * чтений, сколько в нём символов.
 */
const MAX_ASSIGNMENT_ID_SPLITS = 8;

function nonBlankString(value) {
  if (typeof value === "string") return value.trim() === "" ? null : value;
  if (value === null || value === undefined) return null;
  const text = String(value);
  return text.trim() === "" ? null : text;
}

function positiveInteger(value) {
  const parsed = typeof value === "number" ? value : Number(value);
  return Number.isFinite(parsed) ? Math.floor(parsed) : null;
}

/**
 * Курсор запроса в том виде, в каком он пришёл.
 *
 * Пара побеждает скаляр: клиент шлёт оба, и скаляр у него намеренно стоит на миллисекунду раньше
 * пары — сервер, умеющий только скаляр, отдаёт всю миллисекунду курсора. Пара с пустым `docId` —
 * «в этой миллисекунде ещё ничего не прочитано»; такой курсор адресуется скаляром, потому что
 * `startAfter` пустым id документа не выражается.
 */
function readCursor(data) {
  const body = data && typeof data === "object" ? data : {};
  const pair = body.cursor && typeof body.cursor === "object" ? body.cursor : null;
  const pairMs = pair ? positiveInteger(pair.changedAtMs) : null;
  const pairDocId = pair ? nonBlankString(pair.docId) : null;
  if (pairMs !== null && pairMs >= 0 && pairDocId !== null) {
    return {changedAtMs: pairMs, docId: pairDocId};
  }
  if (pairMs !== null && pairMs >= 0) {
    // Пара без id документа: читать с начала этой миллисекунды, то есть строго после предыдущей.
    return {changedAtMs: pairMs - 1, docId: null};
  }
  const cursorMs = positiveInteger(body.cursorMs);
  return {changedAtMs: cursorMs === null ? 0 : cursorMs, docId: null};
}

/** Размер страницы: то, что попросили, в границах `[1, MAX_PAGE_SIZE]`. */
function readLimit(data) {
  const body = data && typeof data === "object" ? data : {};
  const asked = positiveInteger(body.limit);
  if (asked === null) return DEFAULT_PAGE_SIZE;
  return Math.max(1, Math.min(asked, MAX_PAGE_SIZE));
}

/**
 * Запись журнала в том виде, в каком её ждёт клиент: `{id, docId, changedAtMs}`.
 *
 * `id` отсутствует, когда прочитать его не из чего, — и это не то же самое, что отсутствие записи:
 * клиент считает такую запись непрочитанной и двигает курсор через неё, оставляя след (AD-10).
 * Терпимость к прежним формам тела здесь и живёт: `id` — новая форма, `assignmentId` — форма
 * журнала ревью до приведения, id документа — последняя опора.
 */
function reviewChangeEntry(docId, data) {
  const body = data && typeof data === "object" ? data : {};
  const id = nonBlankString(body.id) || nonBlankString(body[ASSIGNMENT_ID_FIELD]) || nonBlankString(docId);
  const changedAtMs = positiveInteger(body.changedAtMs);
  const entry = {docId: String(docId), changedAtMs: changedAtMs === null ? 0 : changedAtMs};
  if (id) entry.id = id;
  return entry;
}

/** Лексикографический порядок пары — тот же, что у клиента. */
function compareEntries(left, right) {
  if (left.changedAtMs !== right.changedAtMs) return left.changedAtMs - right.changedAtMs;
  return left.docId < right.docId ? -1 : left.docId > right.docId ? 1 : 0;
}

/**
 * Страница журнала ревью.
 *
 * @param {*} db Firestore.
 * @param {object} data тело вызова: `{cursor?: {changedAtMs, docId}, cursorMs?, limit?}`.
 * @param {object} options `{documentId}` — `FieldPath.documentId()` того же SDK; без него вторая
 *   половина пары не выражается запросом, и порядок остался бы только по времени.
 * @returns {Promise<{changes: Array, hasMore: boolean}>}
 */
async function readReviewAssignmentChanges(db, data, options) {
  const cursor = readCursor(data);
  const limit = readLimit(data);
  const documentId = options && options.documentId;
  let query = db.collection(ADMIN_REVIEW_SYNC_CHANGES).orderBy("changedAtMs");
  if (documentId) query = query.orderBy(documentId);
  query = cursor.docId && documentId
    ? query.startAfter(cursor.changedAtMs, cursor.docId)
    : query.where("changedAtMs", ">", cursor.changedAtMs);
  const snapshot = await query.limit(limit).get();
  const docs = snapshot.docs || [];
  const changes = docs
    .map((doc) => reviewChangeEntry(doc.id, doc.data ? doc.data() : {}))
    .sort(compareEntries);
  return {changes, hasMore: docs.length >= limit};
}

/**
 * Урок задания и, если задание нашлось окольным путём, запись, которой оно доклеивает себе поле.
 *
 * Цепочка ровно из трёх шагов, и порядок в ней — от самого дешёвого к самому терпеливому:
 *
 *   1. `admin/review/lessons where assignmentId == id` — задание, записанное этой версией сервера
 *      или уже доклеившее себе поле;
 *   2. `lessonId` в теле записи журнала — форма журнала ревью до приведения к AD-11. После
 *      приведения поля там нет, поэтому шаг не последний;
 *   3. разрез id задания: `{submissionId}_{lessonId}`. Позиция разреза перебирается, а найденное
 *      проверяется по `submissionId` самого документа урока — совпадение по обеим половинам, а не
 *      по одной.
 *
 * Шаги 2 и 3 возвращают `heal`: путь документа урока и поле, которого ему не хватает. Писать здесь
 * нечего — чтение остаётся чтением, — но вызывающий обязан этот долг закрыть, иначе окольный путь
 * будет проходиться на каждое чтение.
 */
async function resolveReviewAssignment(db, assignmentId) {
  const id = nonBlankString(assignmentId);
  if (!id) return null;

  const byField = await db.collection(ADMIN_REVIEW_LESSONS).where(ASSIGNMENT_ID_FIELD, "==", id).limit(1).get();
  const found = (byField.docs || [])[0];
  if (found) return {assignmentId: id, lessonId: found.id, lesson: found, source: ASSIGNMENT_ID_FIELD, heal: null};

  const change = await db.doc(adminSyncChangePath(id)).get();
  const journalLessonId = change && change.exists ? nonBlankString((change.data() || {}).lessonId) : null;
  if (journalLessonId) {
    return {
      assignmentId: id,
      lessonId: journalLessonId,
      lesson: null,
      source: "journal",
      heal: {path: `${ADMIN_REVIEW_LESSONS}/${journalLessonId}`, data: {[ASSIGNMENT_ID_FIELD]: id}},
    };
  }

  for (const {submissionId, lessonId} of assignmentIdSplits(id)) {
    const lesson = await db.doc(`${ADMIN_REVIEW_LESSONS}/${lessonId}`).get();
    if (!lesson || !lesson.exists) continue;
    if (nonBlankString((lesson.data() || {}).submissionId) !== submissionId) continue;
    return {
      assignmentId: id,
      lessonId,
      lesson,
      source: "split",
      heal: {path: `${ADMIN_REVIEW_LESSONS}/${lessonId}`, data: {[ASSIGNMENT_ID_FIELD]: id}},
    };
  }
  return null;
}

/** Разрезы `{submissionId}_{lessonId}` по каждому `_`, слева направо, но не больше предела. */
function assignmentIdSplits(assignmentId) {
  const splits = [];
  let from = 0;
  while (splits.length < MAX_ASSIGNMENT_ID_SPLITS) {
    const separator = assignmentId.indexOf("_", from);
    if (separator <= 0 || separator === assignmentId.length - 1) break;
    splits.push({
      submissionId: assignmentId.slice(0, separator),
      lessonId: assignmentId.slice(separator + 1),
    });
    from = separator + 1;
  }
  return splits;
}

/**
 * Уроки заданий пачкой: `{resolved: [{assignmentId, lessonId, lesson, source}], heals: [{path, data}]}`.
 *
 * `lesson` — снимок документа, если он уже прочитан по дороге; `null` означает «читай сам», а не
 * «документа нет»: путь через журнал знает только id урока.
 */
async function resolveReviewAssignments(db, assignmentIds) {
  const resolved = [];
  const heals = [];
  for (const assignmentId of assignmentIds || []) {
    const hit = await resolveReviewAssignment(db, assignmentId);
    if (!hit) continue;
    resolved.push({assignmentId: hit.assignmentId, lessonId: hit.lessonId, lesson: hit.lesson, source: hit.source});
    if (hit.heal) heals.push(hit.heal);
  }
  return {resolved, heals};
}

module.exports = {
  ADMIN_REVIEW_LESSONS,
  ASSIGNMENT_ID_FIELD,
  DEFAULT_PAGE_SIZE,
  MAX_ASSIGNMENT_ID_SPLITS,
  MAX_PAGE_SIZE,
  assignmentIdSplits,
  readCursor,
  readLimit,
  readReviewAssignmentChanges,
  resolveReviewAssignment,
  resolveReviewAssignments,
  reviewChangeEntry,
};
