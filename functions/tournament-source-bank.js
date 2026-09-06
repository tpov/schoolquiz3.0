"use strict";

const {randomBytes, createHmac} = require("node:crypto");
const {redact, restoreContent, REDACTED_TYPE, STATUS} = require("./question-redaction");
const {canonicalQuestionId, questionAllocatedSeconds} = require("./lesson-reward");
const {canonicalTournamentJson: canonical, tournamentDigest: digest, normalizeTournamentBank} = require("./tournament-bank");

const SOURCE_BANKS = "course_tournament_source_banks";
const PREPARATIONS = "course_tournament_bank_preparations";
const TIMING_VERSION = "lesson-reward-questionAllocatedSeconds-v1";
const PROOF_VERSION = "tournament-source-proof-v1";
const DEFAULT_LIMITS = Object.freeze({hierarchyRows: 300, questionRows: 2000, selectedQuestions: 1000,
  readDocuments: 6000, queryDocuments: 2000, snapshotBytes: 900000});
const SOURCE_TYPES = new Set(Object.keys(REDACTED_TYPE));
const REVERSE_TYPES = new Map(Object.entries(REDACTED_TYPE).map(([source, redacted]) => [redacted, source]));
const compare = (a, b) => a < b ? -1 : a > b ? 1 : 0;
function fail(code, message = code) { const error = new Error(message); error.code = code; throw error; }
function exact(n, min = 0) { if (!Number.isSafeInteger(n) || n < min) fail("INVALID_SOURCE", "Exact non-negative integer required"); return n; }
function id(value) {
  if (typeof value !== "string" || !value.trim() || value.includes("/") || [".", ".."].includes(value) ||
      Buffer.byteLength(value) > 1500 || /^__.*__$/.test(value)) fail("INVALID_ARGUMENT", "Single document ID required");
  return value;
}
function fields(value, names) {
  if (!value || Object.getPrototypeOf(value) !== Object.prototype || Object.keys(value).some((key) => !names.includes(key))) fail("INVALID_ARGUMENT");
}
function sourceRef(value, snapshot = false) {
  // Existing event.source also carries its historical policy attestations. They are ignored:
  // current eligibility comes from collect(), never these booleans supplied to a trusted load.
  fields(value, snapshot ? ["kind", "id", "snapshotId", "reviewed", "published", "admitted"] : ["kind", "id"]);
  if (!["COURSE", "ARENA_LESSON"].includes(value.kind)) fail("INVALID_ARGUMENT");
  const result = {kind: value.kind, id: id(value.id)};
  if (snapshot) {
    if (typeof value.snapshotId !== "string" || !/^bank_[0-9a-f]{64}$/.test(value.snapshotId)) fail("INVALID_ARGUMENT");
    result.snapshotId = value.snapshotId;
  }
  return result;
}
const data = (snapshot) => snapshot.exists ? snapshot.data() || {} : null;
function parse(value) { try { return JSON.parse(value); } catch { fail("INVALID_SOURCE", "Malformed source JSON"); } }
function material(row, names) { return Object.fromEntries(names.filter((key) => row[key] !== undefined).map((key) => [key, row[key]])); }
function randomFor(entropy, questionId) {
  let position = 0;
  return () => createHmac("sha256", entropy).update(canonical([questionId, position++])).digest().readUIntBE(0, 6) / 281474976710656;
}

/** Root supplies ONLY trusted functions: the real hierarchy/same-submission independent-review
 * loader, and a profile-backed curator check. Their reads are bounded here, never cached by tx.
 * No client may call loadBank directly or read either private collection.
 */
function createTournamentSourceBank({db, loadLessonContext, isCurator, nowMs = Date.now, limits: overrides = {}}) {
  for (const fn of [loadLessonContext, isCurator, nowMs]) if (typeof fn !== "function") throw new TypeError("Trusted loader functions required");
  if (!db || typeof db.runTransaction !== "function") throw new TypeError("Firestore db required");
  fields(overrides, Object.keys(DEFAULT_LIMITS));
  const limits = {...DEFAULT_LIMITS, ...overrides};
  Object.values(limits).forEach((n) => exact(n, 1));
  // This is a storage safety ceiling, not a hidden content-selection rule.
  if (limits.snapshotBytes > DEFAULT_LIMITS.snapshotBytes) throw new RangeError("snapshotBytes cannot exceed 900000");

  function reader(transaction) {
    let consumed = 0;
    const proof = new Map();
    async function read(ref, maximum) {
      const query = typeof ref.limit === "function";
      const bound = Math.min(maximum ?? limits.queryDocuments, limits.queryDocuments, limits.readDocuments - consumed);
      if (consumed >= limits.readDocuments || (query && bound < 1)) fail("SOURCE_TOO_LARGE", "Read budget exhausted; no snapshot was prepared");
      // The injected root loader currently has an unbounded reviews query. Apply a +1 lookahead
      // here and reject overflow BEFORE returning anything to it, never hand it a partial review.
      const result = await transaction.get(query ? ref.limit(bound + 1) : ref);
      consumed += query ? Math.max(1, result.docs.length) : 1;
      if (consumed > limits.readDocuments || (query && result.docs.length > bound)) fail("SOURCE_TOO_LARGE", "Complete source exceeds explicit read/query bound");
      return result;
    }
    const trustedTransaction = {get: (ref) => read(ref), getAll: (...refs) => Promise.all(refs.map((ref) => read(ref)))};
    const remember = (path, row) => { proof.set(path, digest(row)); };
    async function get(path) { return data(await read(db.doc(path))); }
    async function rows(collection, field, value, maximum) {
      return (await read(db.collection(collection).where(field, "==", value), maximum)).docs
        .map((doc) => ({id: doc.id, row: doc.data() || {}})).sort((a, b) => compare(a.id, b.id));
    }
    return {get, rows, remember, trustedTransaction, proof};
  }

  async function collect(transaction, uid, source) {
    const r = reader(transaction);
    let hierarchyRows = 0; let questionRows = 0;
    async function children(collection, field, parent) {
      const rows = await r.rows(collection, field, parent, limits.hierarchyRows - hierarchyRows);
      hierarchyRows += rows.length;
      if (hierarchyRows > limits.hierarchyRows) fail("SOURCE_TOO_LARGE", "Hierarchy bound exceeded");
      for (const item of rows) r.remember(`${collection}/${item.id}`, material(item.row,
        [field, "version", "archived", "reviewSubmissionId", "reviewApprovedAtMs", "publishedAtMs"]));
      return rows;
    }
    const lessons = [];
    let course = null;
    if (source.kind === "COURSE") {
      course = await r.get(`quests/${source.id}`);
      if (!course || (course.authorUid || course.ownerUid) !== uid || !Array.isArray(course.visibleOn) ||
          !course.visibleOn.includes("archive")) fail("SOURCE_NOT_ELIGIBLE", "Own published COURSE archive required");
      id(course.catalogId); exact(course.version, 1);
      const catalog = await r.get(`catalogs/${course.catalogId}`);
      if (!catalog || catalog.archived === true || String(catalog.questType || course.questType || "REGULAR").toUpperCase() !== "COURSE") fail("SOURCE_NOT_ELIGIBLE");
      r.remember(`quests/${source.id}`, material(course, ["catalogId", "authorUid", "ownerUid", "questType", "version", "visibleOn", "archived"]));
      r.remember(`catalogs/${course.catalogId}`, material(catalog, ["questType", "version", "archived"]));
      for (const section of await children("sections", "questId", source.id)) {
        if (section.row.archived === true) continue;
        exact(section.row.version, 1);
        for (const theme of await children("themes", "sectionId", section.id)) {
          if (theme.row.archived === true) continue;
          exact(theme.row.version, 1);
          for (const lesson of await children("lessons", "themeId", theme.id)) {
            if (lesson.row.archived === true) continue;
            const ctx = await loadLessonContext(r.trustedTransaction, lesson.id);
            if (!ctx || ctx.reviewApproved !== true) continue;
            if (ctx.lessonId !== lesson.id || ctx.questId !== source.id || ctx.catalogId !== course.catalogId ||
                ctx.sectionId !== section.id || ctx.themeId !== theme.id || ctx.authorUid !== uid || ctx.questType !== "COURSE" ||
                ctx.contentVersion !== lesson.row.version) fail("INVALID_SOURCE", "Trusted hierarchy/version mismatch");
            // Archived root on visibleOn=archive is a publication marker, not retirement. The
            // concrete descendant rows above are unarchived. Older root loaders report false here.
            exact(ctx.contentVersion, 1);
            lessons.push({id: lesson.id, row: lesson.row, ctx});
          }
        }
      }
    } else {
      const ctx = await loadLessonContext(r.trustedTransaction, source.id);
      if (!ctx || ctx.lessonId !== source.id || ctx.questType !== "REGULAR" || ctx.published !== true || ctx.reviewApproved !== true) fail("SOURCE_NOT_ELIGIBLE");
      id(ctx.authorUid); exact(ctx.contentVersion, 1);
      if (ctx.authorUid !== uid && await isCurator(r.trustedTransaction, uid) !== true) fail("PERMISSION_DENIED");
      const placement = await r.get(`lesson_placements/${source.id}`);
      if (!placement || placement.shelf !== "arena" || placement.contentVersion !== ctx.contentVersion ||
          placement.authorUid !== ctx.authorUid) fail("SOURCE_NOT_ELIGIBLE", "Current admitted Arena lesson required");
      const lesson = await r.get(`lessons/${source.id}`);
      if (!lesson || lesson.archived === true || lesson.version !== ctx.contentVersion || lesson.themeId !== ctx.themeId) fail("INVALID_SOURCE");
      for (const [collection, documentId, parentField, parentId] of [
        ["themes", ctx.themeId, "sectionId", ctx.sectionId], ["sections", ctx.sectionId, "questId", ctx.questId],
        ["quests", ctx.questId, "catalogId", ctx.catalogId], ["catalogs", ctx.catalogId, null, null],
      ]) {
        const ancestor = await r.get(`${collection}/${id(documentId)}`);
        if (!ancestor || (parentField && ancestor[parentField] !== parentId) ||
            (collection !== "quests" && ancestor.archived === true)) fail("INVALID_SOURCE", "Source hierarchy is no longer eligible");
        r.remember(`${collection}/${documentId}`, material(ancestor,
          ["version", "archived", "sectionId", "questId", "catalogId", "questType", "authorUid", "ownerUid", "visibleOn"]));
      }
      lessons.push({id: source.id, row: lesson, ctx});
      r.remember(`lesson_placements/${source.id}`, material(placement, ["lessonId", "authorUid", "shelf", "contentVersion", "placementVersion"]));
    }

    const candidates = [];
    for (const lesson of lessons.sort((a, b) => compare(a.id, b.id))) {
      r.remember(`eligible-lessons/${lesson.id}`, {context: material(lesson.ctx,
        ["lessonId", "authorUid", "catalogId", "questId", "sectionId", "themeId", "questType", "contentVersion", "reviewApproved"]),
      publication: material(lesson.row, ["version", "archived", "themeId", "reviewSubmissionId", "reviewApprovedAtMs", "publishedAtMs"])});
      const rows = await r.rows("questions", "lessonId", lesson.id, limits.questionRows - questionRows);
      questionRows += rows.length;
      if (questionRows > limits.questionRows) fail("SOURCE_TOO_LARGE", "Question-row bound exceeded");
      let keyDocument;
      for (const {id: questionId, row} of rows) {
        id(questionId);
        r.remember(`questions/${questionId}`, material(row, ["lessonId", "version", "archived", "difficulty", "language", "order", "payload"]));
        if (row.archived === true) continue;
        let content = parse(row.payload);
        if (!content || typeof content !== "object" || Array.isArray(content)) fail("INVALID_SOURCE");
        if (content.type === "Survey") continue;
        const type = REVERSE_TYPES.get(content.type) || content.type;
        if (!SOURCE_TYPES.has(type)) fail("INVALID_SOURCE", "Unsupported assessment question");
        const difficulty = String(row.difficulty || content.difficulty || "EASY").toUpperCase();
        if (!["EASY", "HARD"].includes(difficulty) || (content.difficulty && String(content.difficulty).toUpperCase() !== difficulty)) fail("INVALID_SOURCE", "Conflicting question difficulty");
        exact(row.version, 1);
        if (REVERSE_TYPES.has(content.type)) {
          if (keyDocument === undefined) keyDocument = await r.get(`question_keys/${lesson.id}`);
          const matching = keyDocument?.keys?.filter((entry) => entry.questionId === questionId) || [];
          if (keyDocument?.version !== 1 || keyDocument.lessonId !== lesson.id || keyDocument.publicHalfRedacted !== true ||
              matching.length !== 1 || matching[0].key?.questionId !== questionId) fail("INVALID_SOURCE", "Missing current private key generation");
          r.remember(`selected-source-keys/${questionId}`, matching[0].key);
          content = restoreContent(content, matching[0].key);
          if (!content) fail("INVALID_SOURCE", "Invalid current private key");
        }
        // A content-embedded id is not the Firestore document id. Bind BOTH newly generated
        // halves to the actual document that was read; never accept a payload's identity claim.
        candidates.push({questionId, canonicalId: canonicalQuestionId(questionId), lessonId: lesson.id,
          contentVersion: row.version, type, difficulty,
          maxDurationMs: exact(questionAllocatedSeconds(content, difficulty === "HARD") * 1000, 1),
          content: {...content, id: questionId, difficulty}});
      }
    }
    candidates.sort((a, b) => compare(a.questionId, b.questionId));
    const unique = new Map();
    for (const row of candidates) {
      const previous = unique.get(row.canonicalId);
      if (previous && (previous.difficulty !== row.difficulty || previous.type !== row.type || previous.lessonId !== row.lessonId)) fail("INVALID_SOURCE", "Conflicting translated variants");
      if (!previous) unique.set(row.canonicalId, row);
    }
    const selected = [...unique.values()];
    if (selected.length > limits.selectedQuestions) fail("SOURCE_TOO_LARGE", "Unique question bound exceeded; no truncation allowed");
    const counts = {easy: selected.filter((q) => q.difficulty === "EASY").length, hard: selected.filter((q) => q.difficulty === "HARD").length, total: selected.length};
    if (counts.easy < 60 || counts.hard < 20) fail("INSUFFICIENT_QUESTIONS", "60 distinct EASY and 20 HARD questions required");
    const proof = {version: PROOF_VERSION, source, timingVersion: TIMING_VERSION,
      rows: [...r.proof].sort(([a], [b]) => compare(a, b)).map(([path, hash]) => ({path, hash})),
      selected: selected.map((q) => ({questionId: q.questionId, lessonId: q.lessonId, contentVersion: q.contentVersion, maxDurationMs: q.maxDurationMs}))};
    return {selected, counts, proof};
  }

  async function prepare({uid, payload}) {
    id(uid); fields(payload, ["requestId", "source"]);
    if (typeof payload.requestId !== "string" || !/^[A-Za-z0-9_-]{16,128}$/.test(payload.requestId)) fail("INVALID_ARGUMENT");
    const source = sourceRef(payload.source);
    const fingerprint = digest({uid, requestId: payload.requestId, source});
    const operationId = digest(payload.requestId);
    const snapshotId = `bank_${operationId}`;
    const entropy = randomBytes(32); // Fixed per invocation, stable on optimistic transaction retries.
    const now = exact(nowMs());
    return db.runTransaction(async (transaction) => {
      const receiptRef = db.doc(`${PREPARATIONS}/${operationId}`);
      const old = data(await transaction.get(receiptRef));
      if (old) {
        if (old.uid !== uid || old.requestFingerprint !== fingerprint) fail("PREPARATION_REUSE");
        return structuredClone(old.response); // Lost reply is returned before any live eligibility check.
      }
      const ref = db.doc(`${SOURCE_BANKS}/${snapshotId}`);
      if (data(await transaction.get(ref))) fail("INCONSISTENT_PREPARATION");
      const current = await collect(transaction, uid, source);
      const bankSource = {...source, snapshotId, reviewed: true, published: true,
        ...(source.kind === "ARENA_LESSON" ? {admitted: true} : {})};
      const questions = current.selected.map((q) => {
        const pair = redact(canonical(q.content), q.difficulty, {questionId: q.questionId, random: randomFor(entropy, q.questionId)});
        if (pair.status !== STATUS.REDACTED) fail("INVALID_SOURCE", "Question cannot be safely redacted");
        if (questionAllocatedSeconds(JSON.parse(pair.publicPayload), q.difficulty === "HARD") * 1000 !== q.maxDurationMs) fail("INVALID_SOURCE", "Redaction changed allocated time");
        return {questionId: q.questionId, type: q.type, difficulty: q.difficulty, sourceId: source.id,
          reviewed: true, published: true, contentVersion: q.contentVersion, keyContentVersion: q.contentVersion,
          publicHalfRedacted: true, maxDurationMs: q.maxDurationMs,
          publicPayload: pair.publicPayload, answerKey: pair.key};
      });
      const bank = {authorizedUid: uid, immutable: true, timingVersion: TIMING_VERSION, source: bankSource, questions};
      normalizeTournamentBank({bank, requesterUid: uid, sourceRef: {...source, snapshotId}});
      const record = {schemaVersion: 1, snapshotId, ownerUid: uid, source, requestFingerprint: fingerprint,
        createdAtMs: now, timingVersion: TIMING_VERSION, proofVersion: PROOF_VERSION, counts: current.counts,
        proofJson: canonical(current.proof), proofHash: digest(current.proof), bankJson: canonical(bank), bankHash: digest(bank)};
      if (Buffer.byteLength(canonical(record)) > limits.snapshotBytes) fail("SOURCE_TOO_LARGE", "Immutable bank exceeds private document byte bound");
      const response = {snapshotId, counts: current.counts, timingVersion: TIMING_VERSION};
      transaction.create(ref, record);
      transaction.create(receiptRef, {schemaVersion: 1, uid, requestId: payload.requestId, requestFingerprint: fingerprint, response, createdAtMs: now});
      return response;
    });
  }

  async function loadBank({transaction, requesterUid, sourceRef: rawSource, purpose}) {
    id(requesterUid);
    const source = sourceRef(rawSource, true);
    if (!["CREATE", "CLOSE_QUALIFICATION", "RECOVERY"].includes(purpose)) fail("INVALID_ARGUMENT", "Explicit trusted lifecycle purpose required");
    const record = data(await transaction.get(db.doc(`${SOURCE_BANKS}/${source.snapshotId}`)));
    if (!record || record.ownerUid !== requesterUid || record.snapshotId !== source.snapshotId || record.schemaVersion !== 1 ||
        record.source?.kind !== source.kind || record.source?.id !== source.id) fail("SOURCE_NOT_ELIGIBLE", "Owned immutable snapshot required");
    const bank = parse(record.bankJson); const proof = parse(record.proofJson);
    if (digest(bank) !== record.bankHash || digest(proof) !== record.proofHash || bank.timingVersion !== record.timingVersion ||
        proof.version !== record.proofVersion) fail("INVALID_SOURCE", "Immutable bank integrity failure");
    normalizeTournamentBank({bank, requesterUid, sourceRef: source});
    if (purpose === "CREATE") {
      if (record.timingVersion !== TIMING_VERSION || record.proofVersion !== PROOF_VERSION) fail("STALE_PREPARATION", "Prepare with current proof/timing version");
      const current = await collect(transaction, requesterUid, {kind: source.kind, id: source.id});
      if (digest(current.proof) !== record.proofHash) fail("STALE_PREPARATION", "Source changed; prepare a new snapshot/request ID");
    }
    // CLOSE/RECOVERY intentionally read no mutable source, reviews, roles or timer configuration.
    // Existing event's saved source and organizer must be supplied by the trusted lifecycle only.
    return bank;
  }
  return {prepare, loadBank};
}

module.exports = {createTournamentSourceBank, SOURCE_BANKS, PREPARATIONS, TIMING_VERSION, PROOF_VERSION, DEFAULT_LIMITS};
