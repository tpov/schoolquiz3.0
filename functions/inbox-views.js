"use strict";

/**
 * Общий inbox (AD-20, CAP-6, история 4.9): разрешённые проекции того, что адресовано читателю.
 *
 * Два источника, две разные политики доступа — и ни одна из них вью не расширяет:
 *  - `lesson_error_reports` — приватные отчёты об ошибке урока. Читают их двое: разрешённый автор
 *    урока (адресат) и сам репортер. Роль ревьюера/куратора права читать чужой отчёт не даёт,
 *    поэтому вью отбирает строки только по `lessonAuthorUid == uid`, а авторство урока сервер
 *    закрепил в момент подачи — из документа размещения, не из тела запроса.
 *  - `lessonComments` — публичное обсуждение урока. Читается всеми и без inbox; проекция здесь —
 *    удобство автора (комментарии к его урокам в одном списке), читателей она не меняет.
 *
 * Границы формы держит сама проекция: приватный отчёт — текст про весь урок, и ни вопрос, ни
 * ответ через него не возвращаются (в документе их и нет — приёмник такие поля отвергает).
 * `reporterUid` наружу не едет: ответ автору не обещает двусторонний чат (AD-20).
 */

const VIEW_VERSION = "inbox-views-v1";
const LIMITS = Object.freeze({
  defaultPageSize: 20,
  maxPageSize: 20,
  // Чужие уроки автору в inbox не нужны, а постраничный merge двух источников требует конечного
  // множества комментариев; потолок уроков на страницу чтения размещений и есть эта граница.
  maxOwnedLessons: 25,
  commentsPerLesson: 10,
  responseBytes: 100_000,
});
const KIND_REPORT = "private_error_report";
const KIND_COMMENT = "lesson_comment";
const READERS_AUTHOR_ONLY = "author_only";
const READERS_PUBLIC = "public";
const MAX_COMMENT_TEXT_BYTES = 1000;
const MAX_COMMENT_NICKNAME_BYTES = 200;

class InboxViewError extends Error {
  constructor(code) {
    super(code);
    this.name = "InboxViewError";
    this.code = code;
  }
}

function fail(code) { throw new InboxViewError(code); }
function unavailable(condition) { if (!condition) fail("UNAVAILABLE"); }
function plain(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value) &&
    [Object.prototype, null].includes(Object.getPrototypeOf(value));
}
function identifier(value) {
  return typeof value === "string" && value.trim().length > 0 && !value.includes("/") &&
    value !== "." && value !== ".." && !/^__.*__$/.test(value) && Buffer.byteLength(value) <= 1500;
}
function unsigned(value, minimum = 0) {
  return Number.isSafeInteger(value) && value >= minimum && value <= Number.MAX_SAFE_INTEGER;
}
function only(value, fields) {
  if (!plain(value) || Reflect.ownKeys(value).some((key) => !fields.includes(key))) fail("INVALID_ARGUMENT");
}
function readRequest(input, fields) {
  only(input, ["uid", "payload"]);
  if (!identifier(input.uid)) fail("UNAUTHENTICATED");
  only(input.payload, fields);
  return input;
}
function pageSize(payload) {
  const value = payload.pageSize === undefined ? LIMITS.defaultPageSize : payload.pageSize;
  if (!unsigned(value, 1) || value > LIMITS.maxPageSize) fail("INVALID_ARGUMENT");
  return value;
}
function snapshotData(snapshot) {
  unavailable(snapshot && typeof snapshot.exists === "boolean" && typeof snapshot.data === "function");
  return snapshot.exists ? snapshot.data() : null;
}
function documentId(snapshot) {
  unavailable(snapshot && identifier(snapshot.id));
  return snapshot.id;
}
function checkResponse(value) {
  if (Buffer.byteLength(JSON.stringify(value)) > LIMITS.responseBytes) fail("SOURCE_TOO_LARGE");
  return value;
}

/** Якорь продолжения — прозрачная пара (момент, id), валидируется как всякий вход. */
function readAnchor(payload) {
  if (payload.after === undefined || payload.after === null) return null;
  only(payload.after, ["atMs", "id"]);
  if (!unsigned(payload.after.atMs) || !identifier(payload.after.id)) fail("INVALID_ARGUMENT");
  return payload.after;
}

/**
 * Приватный отчёт писался сервером, и кривой документ — серверный баг: падаем громко (UNAVAILABLE),
 * а не молча пропускаем. Проекция фиксирована: ни `reporterUid`, ни полей вопросов здесь нет.
 */
function reportEntry(snapshot) {
  const id = documentId(snapshot);
  const raw = snapshot.data();
  unavailable(plain(raw) && identifier(raw.lessonId) &&
    (raw.lessonAuthorUid === null || typeof raw.lessonAuthorUid === "string") &&
    typeof raw.text === "string" && raw.text.trim().length > 0 && unsigned(raw.createdAtMs));
  return {
    kind: KIND_REPORT,
    id,
    lessonId: raw.lessonId,
    text: raw.text,
    createdAtMs: raw.createdAtMs,
    readers: READERS_AUTHOR_ONLY,
  };
}

/**
 * Комментарий писал клиент (публичная поверхность), и одна враждебная строка не должна ронять
 * inbox автора: нарушившие форму строки пропускаются. Правила и так allowlist-ят поля, так что
 * пропуск — второй рубеж, а не замена правилам.
 */
function commentEntry(snapshot, lessonId) {
  const id = documentId(snapshot);
  const raw = snapshot.data();
  // Момент комментария на публичной поверхности называется `createdAt` (правила и клиентский
  // репозиторий пишут его под этим именем); в проекции inbox он едет как createdAtMs — как у
  // отчёта, чтобы потребитель листа не знал двух форм.
  if (!plain(raw) || raw.lessonId !== lessonId || typeof raw.text !== "string" ||
      raw.text.trim().length === 0 || Buffer.byteLength(raw.text, "utf8") > MAX_COMMENT_TEXT_BYTES ||
      !unsigned(raw.createdAt, 1)) {
    return null;
  }
  let nickname = "";
  if (raw.authorNickname !== undefined && raw.authorNickname !== null) {
    if (typeof raw.authorNickname !== "string" ||
        Buffer.byteLength(raw.authorNickname, "utf8") > MAX_COMMENT_NICKNAME_BYTES) return null;
    nickname = raw.authorNickname;
  }
  return {
    kind: KIND_COMMENT,
    id,
    lessonId,
    text: raw.text,
    authorNickname: nickname,
    createdAtMs: raw.createdAt,
    readers: READERS_PUBLIC,
  };
}

function mergeAndPage(entries, size, anchor) {
  // Порядок и фильтр продолжения согласованы: свежие выше, при равенстве момента id по убыванию —
  // иначе якорь «строго меньше» на стыке страниц молча пропускал бы строку.
  entries.sort((a, b) => (b.createdAtMs - a.createdAtMs) || (a.id < b.id ? 1 : a.id > b.id ? -1 : 0));
  const visible = anchor === null ? entries : entries.filter((entry) =>
    entry.createdAtMs < anchor.atMs || (entry.createdAtMs === anchor.atMs && entry.id < anchor.id));
  const page = visible.slice(0, size);
  const last = page.at(-1);
  return {
    entries: page,
    nextAnchor: visible.length > size && last ? {atMs: last.createdAtMs, id: last.id} : null,
  };
}

/**
 * Читатель общего inbox. Контроллер подаёт Auth uid; поля запроса власть не выдают и запросы
 * не расширяют. Чтение вне транзакции: источники независимы, каждая строка самодостаточна.
 */
function createInboxViews({db}) {
  if (!db || typeof db.collection !== "function") {
    throw new TypeError("Trusted inbox dependencies required");
  }

  async function list(input) {
    const {uid, payload} = readRequest(input, ["after", "pageSize"]);
    const size = pageSize(payload);
    const anchor = readAnchor(payload);
    const [reportRows, ownedLessons] = await Promise.all([
      db.collection("lesson_error_reports").where("lessonAuthorUid", "==", uid).get(),
      db.collection("lesson_placements").where("authorUid", "==", uid)
        .orderBy("__name__", "asc").limit(LIMITS.maxOwnedLessons).get(),
    ]);
    const entries = reportRows.docs.map(reportEntry);
    // Комментарии собираются по одному уроку за запрос: `in`-запрос потолок страниц не проходит,
    // а потолок уроков выше держит число запросов конечным.
    for (const placementDoc of ownedLessons.docs.slice(0, LIMITS.maxOwnedLessons)) {
      const lessonId = documentId(placementDoc);
      const raw = placementDoc.data();
      unavailable(plain(raw) && raw.authorUid === uid);
      const comments = await db.collection("lessonComments").where("lessonId", "==", lessonId)
        .limit(LIMITS.commentsPerLesson).get();
      for (const doc of comments.docs) {
        const entry = commentEntry(doc, lessonId);
        if (entry) entries.push(entry);
      }
    }
    return checkResponse({viewVersion: VIEW_VERSION, ...mergeAndPage(entries, size, anchor)});
  }

  return Object.freeze({list});
}

module.exports = {
  VIEW_VERSION,
  LIMITS,
  KIND_REPORT,
  KIND_COMMENT,
  READERS_AUTHOR_ONLY,
  READERS_PUBLIC,
  InboxViewError,
  createInboxViews,
};
