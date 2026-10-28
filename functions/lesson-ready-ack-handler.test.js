"use strict";

const assert = require("assert");
const Module = require("module");

const {createFirestoreStub} = require("./_firestore-stub");

/**
 * Один реальный онлайн-переход next-question (история 2.6, ONLINE-O2) — поверх настоящего кода.
 *
 * `lesson-question-window.test.js` proves the verdicts as a table; this suite proves them
 * **wired**: window open, delivery and the ready-ACK mutation run through the real `index.js` —
 * the same two callables and the same single `submitMutation` receiver the client's outbox calls
 * (AD-7) — over an in-memory store, with nothing stubbed inside `index.js`.
 *
 * The transition itself: completion event → 60s window → delivery (the clock starts at the server
 * moment) → ready-ACK through `submitMutation` → the same receipt on the repeated key, the same
 * question and deadline on the repeated request, closure without an answer after expiry.
 */

// ─── The store, installed before index.js is loaded ─────────────────────────────────────────────

const {admin, store} = createFirestoreStub({});
const adminPath = require.resolve("firebase-admin");
const stubModule = new Module(adminPath, null);
stubModule.filename = adminPath;
stubModule.loaded = true;
stubModule.exports = admin;
require.cache[adminPath] = stubModule;

// `index.js` calls admin.initializeApp() and admin.firestore() while it loads, so this require has
// to come after the line above and nothing may require firebase-admin before it.
const functions = require("./index.js");

const SUITE = [];
const test = (name, fn) => SUITE.push([name, fn]);

// ─── The world ──────────────────────────────────────────────────────────────────────────────────

const UID = "player-online";
const ATTEMPT = "attempt-online-1";
const LESSON = "lesson-online";

/** The BEGIN moment of the one transition; every later clock reading is an offset from here. */
const NOW = 1_800_000_000_000;

/** 100 chars → 100 × 0.24 = 24 s per question at HARD, the same rule as the reward's. */
const QUESTION_TEXT = "x".repeat(100);

const POOL = ["q0", "q1", "q2", "q3", "q4"];

function world() {
  const documents = {
    [`profiles/${UID}`]: {uid: UID},
  };
  for (const id of POOL) {
    documents[`questions/${id}`] = {
      id,
      lessonId: LESSON,
      version: 1,
      difficulty: "HARD",
      archived: false,
      payload: JSON.stringify({type: "single-choice", text: QUESTION_TEXT, options: []}),
    };
  }
  return documents;
}

/** The client-visible payload triple of the ready-ACK mutation, keyed like the Kotlin codec does
 * (`ReadyAckOutboxCodec.sourceId`: attemptId goes in as is, no second prefix). */
function readyAckKey(attemptId, questionIndex, windowId) {
  return `lesson_runner-READY_ACK-${attemptId}-q${questionIndex}-window-${windowId}`;
}

function readyAckPayload(attemptId, questionIndex, windowId) {
  return JSON.stringify({attemptId, userId: UID, questionIndex, windowId});
}

/** Controls `Date.now()` inside `index.js` for the duration of `fn`.
 *
 * The helpers below await `fn` INSIDE the fixed-clock window (story 2.13 fix): every callable
 * here is `onCall(async …)`, whose body resumes on microtasks — a synchronous helper restored
 * the real clock before the transaction callbacks ran, so the server moments under test were
 * the wall clock, and the window/deadline assertions failed against it. The production clock
 * use is `Date.now()` at the transaction, exactly as the contract wants; only the harness was
 * born red (the suite shipped with 2.6 and its gate covered the JVM half only).
 */
async function withFixedClock(atMs, fn) {
  const realNow = Date.now;
  Date.now = () => atMs;
  try {
    return await fn();
  } finally {
    Date.now = realNow;
  }
}

function begin(attemptId, completedIndex, extra) {
  return functions.beginLessonQuestionWindow.run({
    auth: {uid: UID},
    data: {
      attemptId,
      lessonId: LESSON,
      lessonVersion: 1,
      completedQuestionIndex: completedIndex,
      nextQuestionId: POOL[completedIndex + 1],
      difficulty: "HARD",
      ...(extra || {}),
    },
  });
}

function fetchNext(attemptId, completedIndex) {
  return functions.fetchNextLessonQuestion.run({
    auth: {uid: UID},
    data: {attemptId, completedQuestionIndex: completedIndex},
  });
}

function submitReadyAck(mutationId, payload) {
  return functions.submitMutation.run({
    auth: {uid: UID},
    data: {mutationId, operation: "lesson_runner.READY_ACK", payload},
  });
}

function windowDoc(attemptId, completedIndex) {
  for (const [path, data] of store.documents) {
    if (path.startsWith("lesson_question_windows/") && data.attemptId === attemptId &&
      data.completedQuestionIndex === completedIndex) {
      return {path, data};
    }
  }
  return null;
}

function resetWorld() {
  store.reset(world());
}

// ─── The one real transition ─────────────────────────────────────────────────────────────────────

test("one real online transition: completion → window → delivery → ready-ACK", async () => {
  resetWorld();
  // Завершение второго вопроса открывает окно третьего: момент ставит сервер, не тело запроса.
  const opened = await withFixedClock(NOW, () => begin(ATTEMPT, 2));
  assert.strictEqual(opened.state, "OPEN");
  assert.strictEqual(opened.windowSeconds, 60);
  assert.strictEqual(opened.nextQuestionIndex, 3);
  assert.strictEqual(opened.allocatedSeconds, 24);
  const doc = windowDoc(ATTEMPT, 2);
  assert.ok(doc, "the window document exists");
  assert.strictEqual(doc.data.expiresAtMs, NOW + 60_000, "the captured window is the 60 seconds of the contract");

  // Выдача: часы стартуют от серверного «сейчас», захваченные секунды применяются как захвачены.
  const delivered = await withFixedClock(NOW + 5_000, () => fetchNext(ATTEMPT, 2));
  assert.strictEqual(delivered.status, "DELIVERED");
  assert.strictEqual(delivered.questionIndex, 3);
  assert.strictEqual(delivered.questionId, "q3");
  assert.strictEqual(delivered.issuedAtMs, NOW + 5_000);
  assert.strictEqual(delivered.answerDeadlineMs, NOW + 5_000 + 24_000);

  // Квитанция готовности — через единственный приёмник мутаций (AD-7), ключ из тройки (§6).
  const mutationId = readyAckKey(ATTEMPT, 3, delivered.windowId);
  const acked = await withFixedClock(NOW + 6_000, () => submitReadyAck(mutationId, readyAckPayload(ATTEMPT, 3, delivered.windowId)));
  assert.strictEqual(acked.replayed, false);
  assert.strictEqual(acked.result.status, "ACKED");
  assert.strictEqual(acked.result.attemptId, ATTEMPT);
  assert.strictEqual(acked.result.questionIndex, 3);
  assert.strictEqual(acked.result.windowId, delivered.windowId);
  assert.strictEqual(acked.result.issuedAtMs, NOW + 5_000, "the receipt carries the server issuance moment");
  assert.strictEqual(acked.result.answerDeadlineMs, delivered.answerDeadlineMs, "the clock start is the delivery's");
  assert.strictEqual(acked.result.ackedAtMs, NOW + 6_000);
});

test("the repeated ready-ACK key replays the same receipt without running the handler again", async () => {
  resetWorld();
  await withFixedClock(NOW, () => begin(ATTEMPT, 2));
  await withFixedClock(NOW + 5_000, () => fetchNext(ATTEMPT, 2));
  const mutationId = readyAckKey(ATTEMPT, 3, windowDoc(ATTEMPT, 2).data.windowId);
  const payload = readyAckPayload(ATTEMPT, 3, windowDoc(ATTEMPT, 2).data.windowId);

  const first = await withFixedClock(NOW + 6_000, () => submitReadyAck(mutationId, payload));
  const replay = await withFixedClock(NOW + 30_000, () => submitReadyAck(mutationId, payload));
  assert.strictEqual(replay.replayed, true, "the known key returns the stored receipt (§4)");
  assert.deepStrictEqual(replay.result, first.result, "byte-identical receipt, nothing re-fixed");
});

test("the lost-ACK branch: a repeated delivery request returns the same question and deadline", async () => {
  resetWorld();
  await withFixedClock(NOW, () => begin(ATTEMPT, 2));
  const first = await withFixedClock(NOW + 5_000, () => fetchNext(ATTEMPT, 2));
  const repeat = await withFixedClock(NOW + 9_000, () => fetchNext(ATTEMPT, 2));
  assert.strictEqual(repeat.status, "DELIVERED");
  assert.strictEqual(repeat.questionId, first.questionId, "no second issuance (§3)");
  assert.strictEqual(repeat.issuedAtMs, first.issuedAtMs);
  assert.strictEqual(repeat.answerDeadlineMs, first.answerDeadlineMs, "the deadline never restarts (§5)");
  assert.strictEqual(repeat.remainderMs, first.answerDeadlineMs - (NOW + 9_000), "the remainder is the server's");
});

test("a window expired without delivery closes the attempt without an answer", async () => {
  resetWorld();
  await withFixedClock(NOW, () => begin(ATTEMPT, 2));
  // Ни одного запроса выдачи; клиент пришёл только после окна (§7).
  const late = await withFixedClock(NOW + 61_000, () => fetchNext(ATTEMPT, 2));
  assert.strictEqual(late.status, "WINDOW_EXPIRED");
  const doc = windowDoc(ATTEMPT, 2);
  assert.strictEqual(doc.data.state, "EXPIRED");
  assert.strictEqual(doc.data.closedReason, "WINDOW_EXPIRED");
  assert.strictEqual(doc.data.delivery, null, "nothing was issued: closure without an answer");

  // И квитанции на закрытом окне не бывает: ACK после expiry — окончательный отказ.
  await assert.rejects(
    () => withFixedClock(NOW + 62_000, () => submitReadyAck(
      readyAckKey(ATTEMPT, 3, doc.data.windowId),
      readyAckPayload(ATTEMPT, 3, doc.data.windowId),
    )),
    (error) => error.code === "invalid-argument",
  );
});

test("another body under the same ready-ACK key is a conflict at the receiver", async () => {
  resetWorld();
  await withFixedClock(NOW, () => begin(ATTEMPT, 2));
  await withFixedClock(NOW + 5_000, () => fetchNext(ATTEMPT, 2));
  const windowId = windowDoc(ATTEMPT, 2).data.windowId;
  const mutationId = readyAckKey(ATTEMPT, 3, windowId);

  await withFixedClock(NOW + 6_000, () => submitReadyAck(mutationId, readyAckPayload(ATTEMPT, 3, windowId)));
  await assert.rejects(
    () => withFixedClock(NOW + 7_000, () => submitReadyAck(mutationId, readyAckPayload("attempt-other", 3, windowId))),
    (error) => error.code === "invalid-argument",
    "another body under the same key is the AD-6 conflict",
  );
});

test("a ready-ACK before any delivery is a final refusal and leaves no completed key", async () => {
  resetWorld();
  await withFixedClock(NOW, () => begin(ATTEMPT, 2));
  const windowId = windowDoc(ATTEMPT, 2).data.windowId;
  await assert.rejects(
    () => withFixedClock(NOW + 1_000, () => submitReadyAck(
      readyAckKey(ATTEMPT, 3, windowId),
      readyAckPayload(ATTEMPT, 3, windowId),
    )),
    (error) => error.code === "invalid-argument",
  );
  for (const [, data] of store.documents) {
    if (data.mutationId === readyAckKey(ATTEMPT, 3, windowId)) {
      assert.notStrictEqual(data.state, "COMPLETED", "a refused ack must not read as done");
    }
  }
});

test("no window opens after the last question of the pool", async () => {
  resetWorld();
  await assert.rejects(
    () => withFixedClock(NOW, () => begin(ATTEMPT, POOL.length - 1)),
    (error) => error.code === "invalid-argument" && /last of the pool/.test(error.message),
    "after the last question the 60s window does not start (§3)",
  );
  assert.strictEqual(windowDoc(ATTEMPT, POOL.length - 1), null);
});

test("a next question outside the lesson is refused before any window exists", async () => {
  resetWorld();
  await assert.rejects(
    () => withFixedClock(NOW, () => begin(ATTEMPT, 2, {nextQuestionId: "q-foreign"})),
    (error) => error.code === "invalid-argument",
    "the client does not appoint server facts (AD-21): the content is the server's",
  );
  assert.strictEqual(windowDoc(ATTEMPT, 2), null);
});


// ─── F10/F11 (retro, epic 2): the window has an owner and survives pool drift ───────────────────

const FOREIGN = "player-foreign";

test("the window belongs to its opener: a foreign profile neither begins, delivers nor acks it (F10)", async () => {
  resetWorld();
  store.documents.set(`profiles/${FOREIGN}`, {uid: FOREIGN});
  await withFixedClock(NOW, () => begin(ATTEMPT, 2));
  const windowId = windowDoc(ATTEMPT, 2).data.windowId;

  // Чужой begin под тем же attemptId — отказ владения (до всякой сверки identity).
  await assert.rejects(
    () => withFixedClock(NOW + 1_000, () => functions.beginLessonQuestionWindow.run({
      auth: {uid: FOREIGN},
      data: {attemptId: ATTEMPT, lessonId: LESSON, lessonVersion: 1,
        completedQuestionIndex: 2, nextQuestionId: "q3", difficulty: "HARD"},
    })),
    (error) => error.code === "permission-denied",
  );
  // Чужая выдача двигала бы серверные часы чужой попытки — тоже владение.
  await assert.rejects(
    () => withFixedClock(NOW + 2_000, () => functions.fetchNextLessonQuestion.run({
      auth: {uid: FOREIGN}, data: {attemptId: ATTEMPT, completedQuestionIndex: 2},
    })),
    (error) => error.code === "permission-denied",
  );
  // Чужой ACK читает серверные факты чужого окна: владелец в документе, не в теле (тело честное).
  await assert.rejects(
    () => withFixedClock(NOW + 3_000, () => functions.submitMutation.run({
      auth: {uid: FOREIGN},
      data: {mutationId: readyAckKey("foreign-attempt", 3, windowId),
        operation: "lesson_runner.READY_ACK",
        payload: JSON.stringify({attemptId: ATTEMPT, userId: FOREIGN, questionIndex: 3, windowId})},
    })),
    (error) => error.code === "permission-denied" && /another account/.test(error.message),
    "the record owner decides, not the declared userId",
  );
  // Свои окна чужому игроку доступны: владение — не запрет на поверхность.
  const own = await withFixedClock(NOW + 4_000, () => functions.beginLessonQuestionWindow.run({
    auth: {uid: FOREIGN},
    data: {attemptId: "attempt-foreign", lessonId: LESSON, lessonVersion: 1,
      completedQuestionIndex: 2, nextQuestionId: "q3", difficulty: "HARD"},
  }));
  assert.strictEqual(own.state, "OPEN");
  assert.strictEqual(windowDoc("attempt-foreign", 2).data.ownerUid, FOREIGN);
});

test("a repeat after the pool drifted returns the same window, not a refusal (F11)", async () => {
  resetWorld();
  await withFixedClock(NOW, () => begin(ATTEMPT, 2));
  // Дрейф сервера: пул урока уехал после открытия окна (архив/удаление вопросов).
  for (const path of [...store.documents.keys()]) {
    if (path.startsWith("questions/")) store.documents.delete(path);
  }
  // §7: потерянный ответ не создаёт второго окна и не наказывает за чужой дрейф.
  const repeat = await withFixedClock(NOW + 1_000, () => begin(ATTEMPT, 2));
  assert.strictEqual(repeat.state, "OPEN");
  assert.strictEqual(repeat.windowId, windowDoc(ATTEMPT, 2).data.windowId);
});

// ─── Run ────────────────────────────────────────────────────────────────────────────────────────

async function run() {
  let failures = 0;
  for (const [name, fn] of SUITE) {
    try {
      await fn();
    } catch (error) {
      failures += 1;
      console.error(`FAIL ${name}\n  ${error && error.stack ? error.stack : error}`);
    }
  }
  if (failures > 0) {
    console.error(`lesson-ready-ack-handler.test.js: ${failures} of ${SUITE.length} cases failed`);
    process.exitCode = 1;
  } else {
    console.log(`lesson-ready-ack-handler.test.js OK (${SUITE.length} cases)`);
  }
}

run();
