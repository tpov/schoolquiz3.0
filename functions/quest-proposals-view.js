"use strict";

/**
 * «Предложения» для автора и предложившего (AD-33, история 4.25, UX-22).
 *
 * Одна запись `lesson_proposals` читается двумя сторонами с разными правами: владелец цели
 * видит только то, что до него доехало (CHECK_PASSED и решения — проваленные и задержанные
 * проверки до автора не доезжают никогда), предложивший видит свою запись целиком, включая
 * исход проверки. Право решает выборка по `targetOwnerUid`/`proposerUid`; чужой идентификатор
 * неотличим от пустой страницы.
 *
 * Правила честности:
 *   — запись, не прошедшая строгий разбор, не исчезает молча (AD-10): её место занимает
 *     строка-метка unreadable, страница продолжается;
 *   — STALE вычисляет сервер: baseRevision записи против текущей версии цели в момент чтения
 *     (единое правило AD-33; отказ применения всё равно делает решение 4.21);
 *   — догон платы: у терминальной записи манифест `review_proposal_fee_manifests` создаётся
 *     ровно один раз — вознаграждений нет, весь фонд сгорает (модель 4.18/AD-10); запись, не
 *     дошедшая до терминала, фонд не делит.
 */

const PROPOSALS_COLLECTION = "lesson_proposals";
const PROPOSAL_FEE_MANIFESTS_COLLECTION = "review_proposal_fee_manifests";
const LESSONS_COLLECTION = "lessons";
const THEMES_COLLECTION = "themes";

const PROPOSALS_VIEW_VERSION = "quest-proposals-v1";

/** Полки чтения: входящие (владельцу цели) и исходящие (предложившему). */
const PROPOSAL_BOXES = Object.freeze(["INCOMING", "OUTGOING"]);

/** Статусы, видимые владельцу цели: до него доезжает только проверенное и решённое. */
const OWNER_VISIBLE_STATUSES = Object.freeze(["CHECK_PASSED", "APPROVED", "REJECTED"]);

const DEFAULT_ROW_LIMIT = 20;
const MAX_ROW_LIMIT = 50;
/** Границы предпросмотра строки: деталь не превращается в выгрузку. */
const MAX_PREVIEW_QUESTION_TEXT_CHARS = 500;
const MAX_DECISION_COMMENT_BYTES = 2000;
const MAX_QUESTIONS_PER_PROPOSAL = 64;

const TERMINAL_STATUSES = Object.freeze(["CHECK_FAILED", "CHECK_DELAYED", "APPROVED", "REJECTED"]);

function fail(code, message = code) {
  const error = new Error(message);
  error.code = code;
  throw error;
}

function nonBlank(value) {
  return typeof value === "string" && value.trim() !== "" ? value : null;
}

function nonNegativeInteger(value) {
  const parsed = typeof value === "number" ? value : Number(value);
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : null;
}

function clipped(value, limit) {
  if (typeof value !== "string") return null;
  return value.length > limit ? value.slice(0, limit) : value;
}

/** Тело запроса: полка, границы страницы, курсор — имя документа предложения. */
function parseQuestProposalsRequest(data) {
  const body = data && typeof data === "object" && !Array.isArray(data) ? data : {};
  const box = nonBlank(body.box) || "INCOMING";
  if (!PROPOSAL_BOXES.includes(box)) fail("INVALID_ARGUMENT", "Unknown proposals box");
  const asked = body.limit === undefined ? DEFAULT_ROW_LIMIT : nonNegativeInteger(body.limit);
  if (asked === null) fail("INVALID_ARGUMENT", "limit must be a whole number");
  if (asked < 1) fail("INVALID_ARGUMENT", "limit must be positive");
  const cursor = nonBlank(body.cursor);
  if (cursor !== null && cursor.includes("/")) fail("INVALID_ARGUMENT", "cursor must be a proposal id");
  return {box, limit: Math.min(asked, MAX_ROW_LIMIT), cursor};
}

/** Текущая версия цели чтением документа цели; неопубликованная цель — null (не STALE-детектор). */
async function readCurrentRevision(db, proposal) {
  const target = proposal && typeof proposal.target === "object" ? proposal.target : {};
  if (nonBlank(target.lessonId)) {
    const snapshot = await db.doc(`${LESSONS_COLLECTION}/${target.lessonId}`).get();
    return snapshot && snapshot.exists ? nonNegativeInteger(snapshot.data().version) : null;
  }
  if (nonBlank(target.themeId)) {
    const snapshot = await db.doc(`${THEMES_COLLECTION}/${target.themeId}`).get();
    return snapshot && snapshot.exists ? nonNegativeInteger(snapshot.data().version) : null;
  }
  return null;
}

/**
 * Строка вью из записи предложения. viewerRole — "owner"|"proposer": owner видит тело отчёта
 * и вопросы правки (оба читателя записи — стороны предложения, тайны между ними нет), но
 * статусы до проверки — не его мир.
 */
function rowForProposal(docId, raw, viewerRole, currentRevision) {
  if (!raw || typeof raw !== "object" || raw.schemaVersion !== 1 ||
    nonBlank(raw.proposalId) === null || nonBlank(raw.kind) === null ||
    nonBlank(raw.status) === null || nonBlank(raw.proposerUid) === null ||
    nonBlank(raw.targetOwnerUid) === null) {
    return {unreadable: true, proposalId: docId};
  }
  const status = raw.status;
  if (viewerRole === "owner" && !OWNER_VISIBLE_STATUSES.includes(status)) return null;
  const target = typeof raw.target === "object" && raw.target !== null ? raw.target : {};
  const payload = typeof raw.payload === "object" && raw.payload !== null ? raw.payload : {};
  const baseRevision = nonNegativeInteger(raw.baseRevision);
  const stale = baseRevision !== null && currentRevision !== null && baseRevision !== currentRevision;
  const decisionRaw = typeof raw.decision === "object" && raw.decision !== null ? raw.decision : null;
  const feeRaw = typeof raw.fee === "object" && raw.fee !== null ? raw.fee : {};
  const row = {
    unreadable: false,
    proposalId: docId,
    kind: raw.kind,
    status,
    stale,
    targetLessonId: nonBlank(target.lessonId),
    targetThemeId: nonBlank(target.themeId),
    targetCatalogId: nonBlank(target.catalogId),
    targetQuestId: nonBlank(target.questId),
    createdAtMs: nonNegativeInteger(raw.createdAtMs) || 0,
    checkedAtMs: nonNegativeInteger(raw.checkedAtMs) || 0,
    decidedAtMs: nonNegativeInteger(raw.decidedAtMs) || 0,
    feeNolics: nonNegativeInteger(feeRaw.nolics) || 0,
    changedQuestions: nonNegativeInteger(feeRaw.changedQuestions) || 0,
    decision: null,
    proposalText: null,
    proposalOwnVariant: null,
    lessonTitle: clipped(payload.lessonTitle, 200) || (payload.lesson && clipped(payload.lesson.title, 200)) || null,
    questionPreviews: [],
  };
  if (decisionRaw) {
    row.decision = {
      decision: nonBlank(decisionRaw.decision),
      comment: clipped(decisionRaw.comment, MAX_DECISION_COMMENT_BYTES),
      decidedAtMs: nonNegativeInteger(decisionRaw.decidedAtMs) || 0,
    };
  }
  if (raw.kind === "REPORT") {
    row.proposalText = clipped(payload.text, 2000);
    row.proposalOwnVariant = clipped(payload.ownVariant, 2000);
  }
  const questions = Array.isArray(payload.questions) ? payload.questions : [];
  for (const question of questions.slice(0, MAX_QUESTIONS_PER_PROPOSAL)) {
    const id = question && nonBlank(question.id);
    if (!id) continue;
    row.questionPreviews.push({
      id,
      text: clipped(question && question.text, MAX_PREVIEW_QUESTION_TEXT_CHARS) || "",
      edited: raw.kind === "COPY_EDIT" ? Array.isArray(payload.editedQuestionIds) &&
        payload.editedQuestionIds.includes(id) : true,
    });
  }
  return row;
}

/** Страница: строгие строки обеих проверок, сортировка по времени — в памяти, курсор по id. */
function buildProposalsPage({box, viewerRole, docs, revisions, limit}) {
  const rows = [];
  for (const doc of docs || []) {
    if (!doc || !doc.id) continue;
    const currentRevision = revisions && revisions[doc.id] !== undefined ? revisions[doc.id] : null;
    const row = rowForProposal(doc.id, doc.data, viewerRole, currentRevision);
    if (row === null) continue;
    rows.push(row);
  }
  rows.sort((a, b) => (b.createdAtMs !== a.createdAtMs ? b.createdAtMs - a.createdAtMs :
    (a.proposalId < b.proposalId ? -1 : 1)));
  const page = rows.slice(0, limit);
  const last = page[page.length - 1];
  return {
    view: PROPOSALS_VIEW_VERSION,
    box,
    rows: page,
    hasMore: rows.length > limit,
    nextCursor: rows.length > limit && last ? last.proposalId : null,
  };
}

/**
 * Догон расчёта платы предложения (AD-10, модель 4.18): манифест создаётся create-однократно,
 * вознаграждений нет — весь фонд сгорает. Не-терминальная запись фонд не делит.
 */
async function settleProposalFeeCycle(db, proposalId, proposal, now) {
  const status = proposal && nonBlank(proposal.status);
  if (!status || !TERMINAL_STATUSES.includes(status)) return {settled: false, reason: "not-terminal"};
  const feeNolics = nonNegativeInteger(proposal && proposal.fee && proposal.fee.nolics) || 0;
  const manifest = {
    schemaVersion: 1,
    calculationVersion: 1,
    proposalId,
    kind: nonBlank(proposal.kind),
    outcome: status,
    feeNolics,
    feeTenths: feeNolics * 10,
    units: [],
    sumEntitlementTenths: 0,
    burnTenths: feeNolics * 10,
    settledAtMs: now,
  };
  try {
    await db.doc(`${PROPOSAL_FEE_MANIFESTS_COLLECTION}/${proposalId}`).create(manifest);
    return {settled: true, replayed: false, burnTenths: manifest.burnTenths};
  } catch (error) {
    if (error && (error.code === 6 || error.code === "ALREADY_EXISTS" || error.code === "already-exists")) {
      return {settled: true, replayed: true};
    }
    throw error;
  }
}

/**
 * Сборка страницы из хранилища: одна ограниченная выборка на полку, сортировка в памяти,
 * версии целей и догон манифестов — на терминальных строках страницы. Право — сама выборка:
 * чужие записи в неё не попадают.
 */
async function readQuestProposalsPage(db, {uid, box, limit, cursor}) {
  const field = box === "OUTGOING" ? "proposerUid" : "targetOwnerUid";
  const viewerRole = box === "OUTGOING" ? "proposer" : "owner";
  const snapshot = await db.collection(PROPOSALS_COLLECTION).where(field, "==", uid).get();
  const candidates = (snapshot && Array.isArray(snapshot.docs) ? snapshot.docs : [])
    .filter((doc) => doc && doc.id && (!cursor || doc.id > cursor))
    .map((doc) => ({id: doc.id, data: doc.data ? doc.data() : {}}));
  // Догон платы: терминальные записи страницы закрывают манифестом до показа (idempotent).
  const revisions = {};
  for (const candidate of candidates) {
    const status = candidate.data && candidate.data.status;
    if (status && TERMINAL_STATUSES.includes(status)) {
      try {
        await settleProposalFeeCycle(db, candidate.id, candidate.data, Date.now());
      } catch (error) {
        // Догон не должен ломать чтение: манифест догонится следующим чтением.
      }
    }
    if (revisionNeeded(candidate.data)) {
      revisions[candidate.id] = await readCurrentRevision(db, candidate.data);
    }
  }
  return buildProposalsPage({box, viewerRole, docs: candidates, revisions, limit});
}

/** Версию цели читает только строка, где STALE правомочен: ждущее решения предложение. */
function revisionNeeded(raw) {
  return Boolean(raw && typeof raw === "object" && raw.schemaVersion === 1 &&
    raw.status === "CHECK_PASSED");
}

module.exports = {
  readQuestProposalsPage,
  DEFAULT_ROW_LIMIT,
  MAX_ROW_LIMIT,
  OWNER_VISIBLE_STATUSES,
  PROPOSALS_VIEW_VERSION,
  TERMINAL_STATUSES,
  buildProposalsPage,
  parseQuestProposalsRequest,
  readCurrentRevision,
  rowForProposal,
  settleProposalFeeCycle,
};
