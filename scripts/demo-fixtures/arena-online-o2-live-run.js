"use strict";

// Живой ONLINE-O2 переход арены (3.2, эпик epic-arena-completion) через реальный приёмник
// demo-backend: хостовой functions-эмулятор (5001) против реальных Firestore/Auth контейнера
// (18090/19090) — та же поверхность, что у 2.6/2.7 и fault-прогона 3.3b. Приложение и его outbox
// остаются device-половиной: в сборке ещё нет онлайн-драйвера арен-переходов (граница записана
// в 2.7), поэтому device-повтор — приёмниковый прогон ниже, у rig-плеера и его же арен-попытки.
//
// Что доказывает прогон (по обязательствам заголовка 3.2):
//   1. событие завершения на РЕАЛЬНОЙ арен-попытке открывает окно с захваченной конфигурацией
//      (60 с, allocatedSeconds из серверного содержимого);
//   2. выдача стартует часы серверным моментом; повтор запроса возвращает тот же вопрос и тот же
//      исходный deadline (потерянный ACK не пересоздаёт выдачу, §5/§7), документ окна не меняется;
//   3. ready-ACK тем же ключом, что строит клиентский кодек, получает квитанцию с фактами
//      ВЫДАЧИ; повтор ключа — байт-в-байт тот же результат с replayed:true, одна запись в
//      mutation_keys (§6.1/AD-6);
//   4. чужое окно в ready-ACK — окончательный отказ без записи (identity прежде фактов);
//   5. после последнего вопроса попытки/пула окно не начинается (§3);
//   6. окно, оставшееся без выдачи, истекает и остаётся стабильно истёкшим; ready-ACK мимо
//      выдачи отвергается (§7, без победы таймауту).
//
// Идентичность: попытки и владелец — реальные арены 3.1 риг-плеера (см.
// docs/agents/evidence/2026-10-09-hitl-rig-matrix/31-arena-first-pass/). Попытки SETTLED:
// приёмник окна ключуется (attemptId, index) и состояния попытки не читает — это граница
// демо-каркаса, записана честно. Пишутся только новые документы lesson_question_windows и
// новые ключи READY_ACK в mutation_keys; пинованные фикстуры не переписываются.

const assert = require("node:assert/strict");
const {createHash} = require("node:crypto");
const {readFileSync} = require("node:fs");
const path = require("node:path");

const PROJECT = "demo-schoolquiz-mvp";
const HOSTS = {firestore: "127.0.0.1:18090", authPort: 19090, upstream: 5001};
const SUBMIT_PATH = `/${PROJECT}/us-central1/submitMutation`;
const BEGIN_PATH = `/${PROJECT}/us-central1/beginLessonQuestionWindow`;
const FETCH_PATH = `/${PROJECT}/us-central1/fetchNextLessonQuestion`;
const CALL_TIMEOUT_MS = 30000;

assert.equal(process.env.GCLOUD_PROJECT, PROJECT, "GCLOUD_PROJECT must name the demo project");
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, HOSTS.firestore, "firestore must be the demo backend");
assert.equal(
  process.env.FIREBASE_AUTH_EMULATOR_HOST,
  `127.0.0.1:${HOSTS.authPort}`,
  "auth must be the demo auth emulator",
);
assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined, "no credentials: emulator-only run");

const repoRoot = path.join(__dirname, "..", "..");
const admin = require(path.join(repoRoot, "functions", "node_modules", "firebase-admin"));
admin.initializeApp({projectId: PROJECT});
const db = admin.firestore();

// Риг-плеер и его две арены-попытки 3.1 (один фикстурный урок, цена 1, HARD, 2 вопроса).
const RIG_PLAYER = "ck3DaiqEd7WGcuVikiw22TyF9fS9";
const ATTEMPT_MAIN = "cf575a94463d638eedc742391001560ff21d7b9bf2b5e1b25647f529d3b29079";
const ATTEMPT_EXPIRY = "3da6a7c31b4dd99d51846a5814ac117b72f3ebc1b";
const manifest = JSON.parse(readFileSync(path.join(__dirname, "manifest.json"), "utf8"));
const bank = manifest.banks.find((entry) => entry.id === "arena-bank-low");
assert.ok(bank, "manifest must carry the bank-low fixture");
const LESSON_ID = bank.lessonId;
const LESSON_VERSION = bank.chain.lessonVersion;

const sha256 = (value) => createHash("sha256").update(value).digest("hex");
const windowDocId = (attemptId, completedQuestionIndex) =>
  "qw_" + sha256(`${attemptId}\u0000${completedQuestionIndex}`).slice(0, 12);
const keyDocId = (mutationId) =>
  `${sha256(mutationId).slice(0, 12)}_${mutationId.replace(/[^A-Za-z0-9_.-]/g, "_").slice(0, 80)}`;
// Тот же ключ, что строит ReadyAckOutboxCodec: operation точка -> дефис + sourceId тройки.
const readyAckKey = (attemptId, questionIndex, windowId) =>
  `lesson_runner-READY_ACK-${attemptId}-q${questionIndex}-window-${windowId}`;

async function callable(path, body, idToken) {
  const response = await fetch(`http://127.0.0.1:${HOSTS.upstream}${path}`, {
    method: "POST",
    headers: {"content-type": "application/json", authorization: `Bearer ${idToken}`},
    body: JSON.stringify({data: body}),
    signal: AbortSignal.timeout(CALL_TIMEOUT_MS),
  });
  const json = await response.json().catch(() => ({}));
  return {status: response.status, ok: response.ok, json};
}

async function playerIdToken(uid) {
  const customToken = await admin.auth().createCustomToken(uid);
  const response = await fetch(
    `http://127.0.0.1:${HOSTS.authPort}/identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=demo`,
    {method: "POST", headers: {"content-type": "application/json"}, body: JSON.stringify({token: customToken, returnSecureToken: true})},
  );
  assert.equal(response.status, 200, "auth emulator must exchange the custom token");
  const json = await response.json();
  assert.equal(typeof json.idToken, "string", "auth emulator returned no idToken");
  return json.idToken;
}

async function mutationRecord(mutationId) {
  const doc = await db.collection("mutation_keys").doc(keyDocId(mutationId)).get();
  return doc.exists ? doc.data() : null;
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function main() {
  const output = {story: "3.2 arena-online-o2", attempt: ATTEMPT_MAIN, lessonId: LESSON_ID, steps: {}};

  // Вопросы урока читает сервер; ids берём из хранилища, чтобы назвать настоящий следующий.
  const lessonQuestions = await db.collection("questions").where("lessonId", "==", LESSON_ID).get();
  const questionIds = lessonQuestions.docs.map((doc) => doc.id).sort();
  assert.ok(questionIds.length >= 2, "the fixture lesson must hold at least two questions");
  output.lessonQuestions = {pool: questionIds.length, ids: questionIds};
  // HARD-пул той же формулы, что у приёмника: ровно вопросы урока для этой фикстуры.
  assert.equal(questionIds.length, 2, "the demo bank fixture is a two-question lesson");

  const idToken = await playerIdToken(RIG_PLAYER);

  // ── 1. Событие завершения: окно с захваченной конфигурацией ────────────────────────────────
  const begin = await callable(BEGIN_PATH, {
    attemptId: ATTEMPT_MAIN,
    lessonId: LESSON_ID,
    lessonVersion: LESSON_VERSION,
    completedQuestionIndex: 0,
    nextQuestionId: questionIds[1],
    difficulty: "HARD",
  }, idToken);
  assert.equal(begin.status, 200, `begin must open the window: ${JSON.stringify(begin.json)}`);
  const view = begin.json.result || begin.json;
  assert.equal(view.windowSeconds, 60, "the captured window constant is 60s");
  assert.ok(view.allocatedSeconds > 0, "the next question's seconds are captured from server content");
  assert.equal(view.attemptId, ATTEMPT_MAIN);
  assert.equal(view.nextQuestionIndex, 1);
  output.steps.begin = view;
  console.log("1. begin: window", view.windowId, "state", view.state, "windowSeconds", view.windowSeconds, "allocatedSeconds", view.allocatedSeconds);

  // ── 2. Выдача: часы стартуют серверным моментом ────────────────────────────────────────────
  const fetchBody = {attemptId: ATTEMPT_MAIN, completedQuestionIndex: 0};
  const first = await callable(FETCH_PATH, fetchBody, idToken);
  assert.equal(first.status, 200, `fetch must deliver: ${JSON.stringify(first.json)}`);
  const delivery = first.json.result || first.json;
  assert.equal(delivery.status, "DELIVERED");
  assert.equal(delivery.questionIndex, 1);
  assert.equal(delivery.questionId, questionIds[1]);
  assert.equal(delivery.answerDeadlineMs - delivery.issuedAtMs, view.allocatedSeconds * 1000, "deadline = issuance + captured seconds");
  const windowDoc = await db.collection("lesson_question_windows").doc(windowDocId(ATTEMPT_MAIN, 0)).get();
  const issued = windowDoc.data().delivery;
  assert.deepEqual(
    {issuedAtMs: issued.issuedAtMs, answerDeadlineMs: issued.answerDeadlineMs},
    {issuedAtMs: delivery.issuedAtMs, answerDeadlineMs: delivery.answerDeadlineMs},
  );
  output.steps.firstDelivery = delivery;
  console.log("2. first delivery: q", delivery.questionId, "deadline", delivery.answerDeadlineMs, "remainder", delivery.remainderMs);

  // ── 3. Потерянный ACK: повтор возвращает тот же вопрос и тот же deadline ───────────────────
  const repeat = await callable(FETCH_PATH, fetchBody, idToken);
  assert.equal(repeat.status, 200);
  const redelivery = repeat.json.result || repeat.json;
  assert.equal(redelivery.status, "DELIVERED");
  assert.equal(redelivery.questionId, delivery.questionId, "no second question is issued");
  assert.equal(redelivery.answerDeadlineMs, delivery.answerDeadlineMs, "the original deadline continues");
  assert.ok(
    redelivery.remainderMs <= delivery.remainderMs,
    "the remainder never grows back with the server clock (0 on an expired answer clock)",
  );
  const windowDocAfter = await db.collection("lesson_question_windows").doc(windowDocId(ATTEMPT_MAIN, 0)).get();
  assert.deepEqual(windowDocAfter.data().delivery, issued, "the window document keeps one issuance");
  output.steps.redelivery = redelivery;
  console.log("3. redelivery: same deadline", redelivery.answerDeadlineMs, "remainder", redelivery.remainderMs);

  // ── 4. Ready-ACK ключом клиентского кодека: квитанция с фактами выдачи ────────────────────
  const ackKey = readyAckKey(ATTEMPT_MAIN, 1, view.windowId);
  const ackBody = {
    mutationId: ackKey,
    operation: "lesson_runner.READY_ACK",
    payload: {attemptId: ATTEMPT_MAIN, userId: RIG_PLAYER, questionIndex: 1, windowId: view.windowId},
  };
  const ackRecordBefore = await mutationRecord(ackKey);
  const ack = await callable(SUBMIT_PATH, ackBody, idToken);
  assert.equal(ack.status, 200, `ready-ack must be accepted: ${JSON.stringify(ack.json)}`);
  const ackEnvelope = ack.json.result;
  if (ackRecordBefore) {
    assert.equal(ackEnvelope.replayed, true, "an existing key replays its saved result");
  } else {
    assert.equal(ackEnvelope.replayed, false, "the first call is not a replay");
  }
  const receipt = ackEnvelope.result;
  assert.equal(receipt.status, "ACKED");
  assert.deepEqual(
    {issuedAtMs: receipt.issuedAtMs, answerDeadlineMs: receipt.answerDeadlineMs},
    {issuedAtMs: delivery.issuedAtMs, answerDeadlineMs: delivery.answerDeadlineMs},
    "the receipt carries the delivery's facts, not the ack body's",
  );
  const ackRecord = await mutationRecord(ackKey);
  assert.ok(ackRecord && ackRecord.state === "completed", "exactly one completed key record");
  output.steps.readyAck = receipt;
  console.log(
    "4. ready-ack:",
    ackRecordBefore ? "replayed a previous run's receipt" : "fresh ACKED",
    "deadline", receipt.answerDeadlineMs, "ackedAt", receipt.ackedAtMs,
  );

  // ── 5. Повтор ключа: байт-в-байт тот же результат, replayed:true, без второй записи ────────
  const ackRepeat = await callable(SUBMIT_PATH, ackBody, idToken);
  assert.equal(ackRepeat.status, 200);
  const repeatEnvelope = ackRepeat.json.result;
  assert.equal(repeatEnvelope.replayed, true, "any repeat of the key is a replay");
  assert.deepEqual(repeatEnvelope.result, receipt, "the replay returns the same receipt");
  const ackRecordAfter = await mutationRecord(ackKey);
  assert.deepEqual(ackRecordAfter, ackRecord, "the record is unchanged by the replay");
  output.steps.readyAckReplay = {replayed: true, sameResult: true};
  console.log("5. ready-ack replay: same receipt, one key record");

  // ── 6. Чужое окно в ready-ACK: окончательный отказ, ключ не пишется ────────────────────────
  const foreignKey = readyAckKey(ATTEMPT_MAIN, 1, "win_foreign00");
  const foreign = await callable(SUBMIT_PATH, {
    mutationId: foreignKey,
    operation: "lesson_runner.READY_ACK",
    payload: {attemptId: ATTEMPT_MAIN, userId: RIG_PLAYER, questionIndex: 1, windowId: "win_foreign00"},
  }, idToken);
  assert.equal(foreign.status, 400, "a foreign window must be refused");
  assert.equal((await mutationRecord(foreignKey)), null, "the refused ack writes no key");
  output.steps.foreignAck = {status: foreign.status};
  console.log("6. foreign window ack:", foreign.status, JSON.stringify(foreign.json.error && foreign.json.error.message));

  // ── 7. После последнего вопроса окно не начинается ─────────────────────────────────────────
  const last = await callable(BEGIN_PATH, {
    attemptId: ATTEMPT_MAIN,
    lessonId: LESSON_ID,
    lessonVersion: LESSON_VERSION,
    completedQuestionIndex: 1,
    nextQuestionId: questionIds[0],
    difficulty: "HARD",
  }, idToken);
  assert.equal(last.status, 400, "the last question opens no window");
  assert.match(String(last.json.error && last.json.error.message), /no next question/);
  output.steps.lastQuestion = {status: last.status};
  console.log("7. window after the last question:", last.status, JSON.stringify(last.json.error && last.json.error.message));

  // ── 8. Окно без выдачи истекает и остаётся стабильно истёкшим; ACK мимо выдачи отвергнут ───
  const beginB = await callable(BEGIN_PATH, {
    attemptId: ATTEMPT_EXPIRY,
    lessonId: LESSON_ID,
    lessonVersion: LESSON_VERSION,
    completedQuestionIndex: 0,
    nextQuestionId: questionIds[1],
    difficulty: "HARD",
  }, idToken);
  assert.equal(beginB.status, 200, "the second attempt's window opens");
  const viewB = beginB.json.result || beginB.json;
  const expiryDocBefore = await db.collection("lesson_question_windows").doc(windowDocId(ATTEMPT_EXPIRY, 0)).get();
  if (expiryDocBefore.exists && expiryDocBefore.data().state === "EXPIRED") {
    console.log("8. expiry window already EXPIRED from a previous run - re-proving stability");
  } else {
    console.log("8. expiry window opened", viewB.windowId, "- waiting out the 60s...");
    await sleep(61_000);
  }
  const expiredFetch = await callable(FETCH_PATH, {attemptId: ATTEMPT_EXPIRY, completedQuestionIndex: 0}, idToken);
  assert.equal(expiredFetch.status, 200);
  const expired = expiredFetch.json.result || expiredFetch.json;
  assert.equal(expired.status, "WINDOW_EXPIRED");
  const expiredAgain = await callable(FETCH_PATH, {attemptId: ATTEMPT_EXPIRY, completedQuestionIndex: 0}, idToken);
  const expiredTwice = expiredAgain.json.result || expiredAgain.json;
  assert.equal(expiredTwice.status, "WINDOW_EXPIRED", "the expiry is stable, no unbounded gap");
  const lateAck = await callable(SUBMIT_PATH, {
    mutationId: readyAckKey(ATTEMPT_EXPIRY, 1, viewB.windowId),
    operation: "lesson_runner.READY_ACK",
    payload: {attemptId: ATTEMPT_EXPIRY, userId: RIG_PLAYER, questionIndex: 1, windowId: viewB.windowId},
  }, idToken);
  assert.equal(lateAck.status, 400, "an ack without a delivery is refused");
  const expiryDoc = await db.collection("lesson_question_windows").doc(windowDocId(ATTEMPT_EXPIRY, 0)).get();
  assert.equal(expiryDoc.data().state, "EXPIRED");
  assert.equal(expiryDoc.data().closedReason, "WINDOW_EXPIRED");
  output.steps.expiry = {first: expired.status, second: expiredTwice.status, lateAck: lateAck.status, state: expiryDoc.data().state};
  console.log("8. expired:", expired.status, "->", expiredTwice.status, "; late ack:", lateAck.status, "; doc state EXPIRED");

  // ── Итог ──────────────────────────────────────────────────────────────────────────────────
  output.ok = true;
  console.log("ARENA-O2 LIVE RUN: PASS");
  return output;
}

main()
  .then((output) => {
    process.stdout.write(`\n${JSON.stringify(output, null, 1)}\n`);
  })
  .catch((error) => {
    console.error("ARENA-O2 LIVE RUN: FAIL");
    console.error(error);
    process.exitCode = 1;
  });
