"use strict";

// Живое доказательство 3.8 arena-price-immutability-and-races (эпик epic-arena-completion) через
// реальные callables demo-backend: хостовой functions-эмулятор (5001) против реальных
// Firestore/Auth контейнера (18090/19090) — та же поверхность scripted client, что у 3.2/3.7.
// Settle идёт через submitMutation (lesson_runner.SUBMIT_ATTEMPT + capturedTerms — реальный
// транспорт приложения), cancel/prepare — через свои callables, reconciling-чтение — через
// fetchArenaEntryTicketStatus (тот, по которому клиентский journal резолвит retained SETTLE).
//
// Четыре обязательства заголовка 3.8:
//   A. Цена заморожена ПОСЛЕ prepare и сквозь settle (quote не переоценивается): prepare по
//      цене 1, затем окно мутации рейтинга поднимает котировку банка до 100, settle закрывает
//      попытку по замороженной 1 (consumedNolics=1, роялти-расщепление одной нолики, замороженные
//      life), повтор тела — байт-в-байт тот же результат. Рейтинг восстановлен байт-в-байт.
//   B. Гонка cancel/settle даёт ровно один финальный эффект, неразрешённое намерение сохраняется
//      (retained SETTLE остаётся сверяемым): обе детерминированные стороны (B1 settle-первый,
//      B2 cancel-первый) плюс истинная параллельная гонка B3; проигравшая операция в каждом
//      случае имеет документированный исход без второго эффекта, а повтор отказавшего тела —
//      тот же поименованный отказ (TICKET_CANCELLED), ключ мутации освобождён, завершённой
//      записи нет — намерение остаётся припаркованным и сверяемым, не сфабрикованным в успех.
//   C. Позднее завершение отменённого тикета не платит дважды (часть B2: два разных тела
//      позднего завершения — ноль записей, ноль денег, ноль наград).
//   D. Неизвестный HARD-скор никогда не ноль: ноль бывает только заработанным (D1: завершение
//      без ответов — codeAnswer из «1» «показан без ответа», без единого «0», цена выплачена,
//      percent = computePercentScore(codeAnswer)); неразрешённая попытка не получает никакого
//      числа (D2: ABORTED-завершение отказано EXPLICIT_CANCELLATION_REQUIRED, event-документа
//      нет, терминала нет — неизвестность не записана нулём), единственный выход без ответа —
//      явная отмена с замороженным возвратом.
//
// Деньги: только 1-ноликовый bank-low; риг-плеер ck3D — тот же, кого читает device-половина.
// Консервация сверяется помаршрутно (зеркало attemptReward из того же модуля functions) и
// итогово. Рейтинг bank-low на время окна мутации подменяется легальным максимумом шкалы {3,1}
// и восстанавливается в finally байт-в-байт (окно ловит любой сбой, включая assert).

const assert = require("node:assert/strict");
const {createHash} = require("node:crypto");
const {readFileSync, writeFileSync} = require("node:fs");
const path = require("node:path");

const PROJECT = "demo-schoolquiz-mvp";
const HOSTS = {firestore: "127.0.0.1:18090", authPort: 19090, upstream: 5001};
const PREPARE_PATH = `/${PROJECT}/us-central1/prepareArenaEntry`;
const CANCEL_PATH = `/${PROJECT}/us-central1/cancelArenaEntry`;
const CATALOG_PATH = `/${PROJECT}/us-central1/listArenaLessons`;
const SUBMIT_PATH = `/${PROJECT}/us-central1/submitMutation`;
const STATUS_PATH = `/${PROJECT}/us-central1/fetchArenaEntryTicketStatus`;
const CALL_TIMEOUT_MS = 30000;

assert.equal(process.env.GCLOUD_PROJECT, PROJECT, "GCLOUD_PROJECT must name the demo project");
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, HOSTS.firestore, "firestore must be the demo backend");
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, `127.0.0.1:${HOSTS.authPort}`, "auth must be the demo auth emulator");
assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined, "no credentials: emulator-only run");

const repoRoot = path.join(__dirname, "..", "..");
const admin = require(path.join(repoRoot, "functions", "node_modules", "firebase-admin"));
const {readEconomyConstants, ECONOMY_CONSTANTS_DOC} = require(path.join(repoRoot, "functions", "economy-constants"));
const {payableTermsProjection} = require(path.join(repoRoot, "functions", "mutation-terms"));
const {restoreContent} = require(path.join(repoRoot, "functions", "question-redaction"));
const {attemptReward, questionAllocatedSeconds} = require(path.join(repoRoot, "functions", "lesson-reward"));
const {computePercentScore} = require(path.join(repoRoot, "functions", "assessment-scoring"));
admin.initializeApp({projectId: PROJECT});
const db = admin.firestore();

const RIG_PLAYER = "ck3DaiqEd7WGcuVikiw22TyF9fS9";
// Префикс requestId-ов прогона: тикеты живут по requestId, повторный прогон обязан не
// переигрывать чужие документы; при перезапуске после частичного прогона TAG сдвигается env-ом.
const TAG = process.env.AR38_TAG || "ar38";
const manifest = JSON.parse(readFileSync(path.join(__dirname, "manifest.json"), "utf8"));
const BANK_LOW = manifest.banks.find((entry) => entry.id === "arena-bank-low");
assert.ok(BANK_LOW, "manifest must carry bank-low");
const LESSON_ID = BANK_LOW.lessonId;

const canonical = (value) => {
  if (value === null || typeof value !== "object" || Array.isArray(value)) return value;
  return Object.fromEntries(Object.keys(value).sort().map((key) => [key, canonical(value[key])]));
};
const digest = (value) => createHash("sha256").update(JSON.stringify(canonical(value))).digest("hex");
const ticketDocId = (uid, requestId) => digest(["arena-ticket", uid, requestId]);
const attemptDocId = (ticketId) => digest(["arena-attempt", ticketId]);
const eventPath = (attemptId) => {
  const hash = createHash("sha256").update(attemptId).digest("hex");
  const safe = `${hash.slice(0, 12)}_${attemptId.replace(/[^A-Za-z0-9_.-]/g, "_").slice(0, 80)}`;
  return `result_events_public/${hash.slice(0, 2)}/events/${safe}`;
};

async function callable(path, body, idToken) {
  const response = await fetch(`http://127.0.0.1:${HOSTS.upstream}${path}`, {
    method: "POST",
    headers: {"content-type": "application/json", authorization: `Bearer ${idToken}`},
    body: JSON.stringify({data: body}),
    signal: AbortSignal.timeout(CALL_TIMEOUT_MS),
  });
  const json = await response.json().catch(() => ({}));
  const error = json.error || null;
  const details = error && error.details;
  const reason = details ? (typeof details === "object" && !Array.isArray(details) ? details.reason :
    Array.isArray(details) ? details.find((entry) => entry && entry.reason)?.reason ?? null : null) : null;
  return {status: response.status, ok: response.ok, json, reason};
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

async function userDoc(uid) {
  return (await db.doc(`users/${uid}`).get()).data() || null;
}
async function ticketDoc(uid, requestId) {
  const doc = await db.doc(`arena_entry_tickets/${ticketDocId(uid, requestId)}`).get();
  return doc.exists ? doc.data() : null;
}
async function eventDoc(attemptId) {
  const doc = await db.doc(eventPath(attemptId)).get();
  return doc.exists ? doc.data() : null;
}
async function ledgerBySource(sourceId) {
  const rows = await db.collection("audit/currency_ledger/entries").where("sourceId", "==", sourceId).get();
  return rows.docs.map((doc) => doc.data());
}
async function rigKeyCount() {
  const rows = await db.collection("mutation_keys").where("uid", "==", RIG_PLAYER).get();
  return rows.size;
}
const wallet = (user) => ({nolics: user.pointsNolics ?? user.nolics, lifePoints: user.lifePoints,
  reservedNolics: user.reservedNolics ?? 0, reservedLifePoints: user.reservedLifePoints ?? 0,
  arenaSelectedHeldCount: user.arenaSelectedHeldCount ?? 0,
  skillPoints: user.pointsSkill ?? user.skillPoints ?? 0});
const sumLedger = (rows, ...reasons) => rows.filter((row) => reasons.includes(row.reason))
  .reduce((sum, row) => sum + row.delta, 0);

// Плейсхолдеры ответов одного вопроса: {type: "single-choice", selected} — форма ANSWER_TYPE
// клиентского кодека; option-идентификаторы choice-вопросов не переиздаются, так что ответ
// живёт в исходном пространстве (question-redaction.translateSubmittedAnswer).
const answerFor = (question, optionId, atMs) => ({questionId: question.questionId,
  codeAnswerIndex: question.codeAnswerIndex, answerPayload: {type: "single-choice", selected: optionId},
  answeredAtMs: atMs});

// Раскладка ответов по замороженному тикету: серверный грейдинг восстанавливает контент ключом
// из тикета (тот же restoreContent, что в scoreAttempt), поэтому «правильный» вариант — это
// key.correctOptionId, а неправильный — любой другой из предложенных.
function answerLayout(ticketData, atMs) {
  const layout = ticketData.questions.map((question, codeAnswerIndex) => {
    const publicPayload = question.publicQuestion.payload;
    const key = question.gradingKey;
    assert.ok(key && key.correctOptionId, "a HARD fixture question must carry its correctOptionId in the ticket key");
    const restored = restoreContent(publicPayload, key);
    assert.ok(restored && restored.correctOptionId === key.correctOptionId,
      `the frozen key must restore its question ${question.questionId}`);
    const wrong = restored.options.map((option) => option.id).find((id) => id !== restored.correctOptionId);
    assert.ok(wrong, "a choice question must offer a wrong option");
    return {questionId: question.questionId, codeAnswerIndex, correct: restored.correctOptionId, wrong};
  });
  const answers = (pick) => layout.map((question, index) => answerFor(question, pick(question, index), atMs));
  return {layout, allCorrect: () => answers((question) => question.correct),
    firstWrong: () => answers((question, index) => index === 0 ? question.wrong : question.correct),
    none: () => []};
}

// Зеркало награды settle-ветки (тот же чистый модуль functions) — для помаршрутной консервации.
function expectedReward(userBefore, ticketData, percent) {
  const bestKey = `${ticketData.lessonId}:${ticketData.difficulty}`;
  const previousBestPercent = Number(userBefore.lessonBest?.[bestKey] ?? 0);
  const allocatedSeconds = ticketData.questions.reduce((sum, question) =>
    sum + questionAllocatedSeconds(JSON.parse(question.publicQuestion.payload), ticketData.difficulty === "HARD"), 0);
  return {previousBestPercent, reward: attemptReward({previousBestPercent, percent,
    isHard: ticketData.difficulty === "HARD", allocatedSeconds})};
}

function settleBody(ticket, answers) {
  return {
    mutationId: `lesson_runner-SUBMIT_ATTEMPT-${ticket.attemptId}`,
    operation: "lesson_runner.SUBMIT_ATTEMPT",
    payload: {
      attemptId: ticket.attemptId, ticketId: ticket.ticketId, ownerUid: RIG_PLAYER, userId: RIG_PLAYER,
      lessonId: ticket.lessonId, lessonVersion: ticket.lessonVersion, difficulty: ticket.difficulty,
      completionKind: "COMPLETED", answers,
    },
    capturedTerms: null, // заполняется после чтения живой таблицы в main
  };
}

async function prepare(idToken, body) {
  return callable(PREPARE_PATH, body, idToken);
}
async function settle(idToken, body) {
  return callable(SUBMIT_PATH, body, idToken);
}
async function cancelTicket(idToken, body) {
  return callable(CANCEL_PATH, body, idToken);
}
async function ticketStatus(idToken, ticketId) {
  return callable(STATUS_PATH, {ticketId}, idToken);
}
function catalogPrice(response, lessonId) {
  const result = response.json.result;
  const entries = Array.isArray(result) ? result : result.entries || result.lessons || result.items || [];
  const row = entries.find((entry) => entry && (entry.lessonId === lessonId || entry.id === lessonId));
  assert.ok(row, `the catalog must contain ${lessonId}: ${JSON.stringify(result).slice(0, 400)}`);
  return row.priceNolics ?? row.price ?? null;
}

async function main() {
  const output = {story: "3.8 arena-price-immutability-and-races", startedAt: new Date().toISOString(),
    requestTag: TAG, rigPlayer: RIG_PLAYER, parts: {}, notes: []};

  // ── Подготовка: живая таблица экономики -> capturedTerms (та же проекция, что сверяет сервер)
  const constantsSnapshot = await db.doc(ECONOMY_CONSTANTS_DOC).get();
  const writtenAtMs = constantsSnapshot.exists && constantsSnapshot.updateTime ? constantsSnapshot.updateTime.toMillis() : 0;
  const constants = readEconomyConstants(constantsSnapshot.exists ? constantsSnapshot.data() : null, writtenAtMs);
  const currentTerms = payableTermsProjection(constants);
  const CAPTURED_TERMS = currentTerms; // полный объект согласия: версия, прейскурант, правила зарядов

  const token = await playerIdToken(RIG_PLAYER);
  const userRaw = await userDoc(RIG_PLAYER);
  // Валютный запас риг-плеера тает с каждым прогоном: settle ПОТРЕБЛЯЕТ замороженные 1 нолик и
  // 50 жизни (в отличие от cancel, возвращающего их), полный прогон стоит до 4 ноликов и 250
  // жизни; улучшений больше нет (lessonBest уже 100), так что награды не возвращают потраченное.
  // Регенерация жизни медленная, догоняем обе валюты до рабочих минимумов админ-записью тех же
  // полей, какими 3.7 бутстрапила экономику синтетического fixture-игрока; факт пишется в
  // доказательство и в README. Курсор регенерации (lifePointsUpdatedAtMs) не трогается.
  const MIN_RUN_LIFE = 300;
  const MIN_RUN_NOLICS = 8;
  output.topUp = null;
  const topUp = {};
  if ((userRaw.lifePoints ?? 0) < MIN_RUN_LIFE) topUp.lifePoints = MIN_RUN_LIFE;
  if ((userRaw.pointsNolics ?? userRaw.nolics ?? 0) < MIN_RUN_NOLICS) topUp.pointsNolics = MIN_RUN_NOLICS;
  if (Object.keys(topUp).length > 0) {
    await db.doc(`users/${RIG_PLAYER}`).set(topUp, {merge: true});
    output.topUp = {from: {lifePoints: userRaw.lifePoints ?? 0, pointsNolics: userRaw.pointsNolics ?? userRaw.nolics ?? 0}, to: topUp};
    output.notes.push(`economy topped up ${JSON.stringify(output.topUp.from)} -> ${JSON.stringify(topUp)} ` +
      "(settled arena attempts consume their frozen price and life; the rig player's regen and exhausted " +
      "lessonBest no longer offset a rerun)");
    console.log("economy top-up:", JSON.stringify(output.topUp));
  }
  const userBefore = await userDoc(RIG_PLAYER);
  const walletBefore = wallet(userBefore);
  assert.equal(walletBefore.reservedNolics, 0, "the rig player starts with nothing reserved");
  assert.equal(walletBefore.arenaSelectedHeldCount, 0, "the rig player starts with zero helds");
  const keysBefore = await rigKeyCount();
  const ratingRef = db.doc(`lesson_ratings/${LESSON_ID}`);
  const ratingOriginal = (await ratingRef.get()).data();
  assert.ok(ratingOriginal, "bank-low must carry a rating document");
  output.baseline = {wallet: walletBefore, keys: keysBefore,
    rating: {ratingSum: ratingOriginal.ratingSum, ratingCount: ratingOriginal.ratingCount,
      ratingVersion: ratingOriginal.ratingVersion}};
  console.log("baseline:", JSON.stringify(output.baseline));

  // ── A. Заморозка цены сквозь settle (quote не переоценивается) ──────────────────────────────
  // Окно мутации ловит любой сбой: finally восстанавливает документ рейтинга до выхода.
  let ticketA = null;
  try {
    const issued = await prepare(token, {requestId: `${TAG}-a1`, mode: "SELECTED",
      lessonId: LESSON_ID, difficulty: "HARD"});
    assert.equal(issued.status, 200, `prepare A must pass: ${JSON.stringify(issued.json)}`);
    ticketA = issued.json.result.ticket;
    assert.equal(ticketA.priceNolics, 1, "bank-low is the price-1 fixture");
    assert.equal(ticketA.status, "RESERVED");
    const frozenDoc = await ticketDoc(RIG_PLAYER, `${TAG}-a1`);
    const layout = answerLayout(frozenDoc, Date.now());
    const answersA = layout.allCorrect();

    // Котировка банка поднимается до 100 ПОСЛЕ prepare: легальный максимум шкалы 1..3 при одном
    // голосе — ratingSum 3, ratingCount 1 (взвешенная ничья с bank-high на верхнем уровне).
    await ratingRef.set({...ratingOriginal, ratingSum: 3, ratingCount: 1});
    const catalogMutated = await callable(CATALOG_PATH, {pageSize: 50}, token);
    assert.equal(catalogMutated.status, 200, `mutated catalog must list: ${JSON.stringify(catalogMutated.json).slice(0, 200)}`);
    const quotedPrice = catalogPrice(catalogMutated, LESSON_ID);
    assert.equal(quotedPrice, 100, "the moved rating must move the quote for new preparations");
    console.log("A. quote moved after prepare: 1 ->", quotedPrice);

    const userAfterPrepare = await userDoc(RIG_PLAYER);
    const mirror = expectedReward(userAfterPrepare, frozenDoc, 100);
    const bodyA = {...settleBody(ticketA, answersA), capturedTerms: CAPTURED_TERMS};
    const settledA = await settle(token, bodyA);
    assert.equal(settledA.status, 200, `settle A must pass: ${JSON.stringify(settledA.json)}`);
    const resultA = settledA.json.result.result;
    const receiptA = resultA.receipts[0];
    assert.equal(resultA.accepted, 1);
    assert.equal(receiptA.status, "SETTLED");
    assert.equal(receiptA.percentScore, 100);
    assert.equal(receiptA.codeAnswer, "99", "two correct HARD answers are two 9s");
    assert.equal(computePercentScore(receiptA.codeAnswer), receiptA.percentScore, "the percent is the honest arithmetic of the code");
    assert.deepEqual(receiptA.reward, mirror.reward, "the reward matches the attemptReward mirror");
    const docA = await ticketDoc(RIG_PLAYER, `${TAG}-a1`);
    assert.equal(docA.status, "SETTLED");
    assert.equal(docA.terminal.consumedNolics, 1, "the FROZEN price settles, not the moved 100 quote");
    assert.equal(docA.terminal.consumedLifePoints, frozenDoc.lifeCost, "the frozen life cost settles");
    const royaltyA = await ledgerBySource(ticketA.ticketId);
    const royaltySum = sumLedger(royaltyA, "arena_author_royalty", "arena_royalty_sink", "arena_royalty_carry");
    assert.equal(royaltySum, 1, `the royalty split conserves exactly the frozen nolic: ${royaltySum}`);
    const userAfterA = await userDoc(RIG_PLAYER);
    // Цена ушла с кошелька ещё на prepare (резерв); settle добавляет только награду.
    const expectedNolicsA = (userAfterPrepare.pointsNolics ?? userAfterPrepare.nolics) + mirror.reward.nolics;
    assert.equal(userAfterA.pointsNolics ?? userAfterA.nolics, expectedNolicsA, "the payer's wallet moved by exactly the frozen price minus the mirrored reward");

    // Повтор тела: тот же mutationId — байт-в-байт тот же результат, ни одной новой записи.
    const replayA = await settle(token, bodyA);
    assert.equal(replayA.status, 200);
    assert.equal(replayA.json.result.replayed, true, "the repeated body is a replay");
    assert.deepEqual(replayA.json.result.result, resultA, "the replay returns the identical settlement");
    assert.equal(await rigKeyCount(), keysBefore + 1, "exactly one new mutation key after settle + replay");
    assert.deepEqual((await ledgerBySource(ticketA.ticketId)).length, royaltyA.length, "the replay wrote no second royalty row");

    output.parts.frozenSettle = {ticketId: ticketA.ticketId, attemptId: ticketA.attemptId,
      quoteDuringSettle: quotedPrice, consumedNolics: docA.terminal.consumedNolics,
      percentScore: receiptA.percentScore, royaltySum, replayIdentical: true};
    console.log("A. frozen settle: quote", quotedPrice, "but consumedNolics", docA.terminal.consumedNolics,
      "royaltySum", royaltySum, "replay identical");
  } finally {
    await ratingRef.set(ratingOriginal);
    const restored = await ratingRef.get();
    assert.deepEqual(restored.data(), ratingOriginal, "the rating doc must be restored byte-for-byte");
    const catalogRestored = await callable(CATALOG_PATH, {pageSize: 50}, token);
    assert.equal(catalogPrice(catalogRestored, LESSON_ID), 1, "the quote returns to 1");
    output.parts.quoteRestored = true;
  }

  // ── B1. Гонка, сторона settle-первый: cancel по SETTLED — no-op, намерение сверяемо ─────────
  {
    const issued = await prepare(token, {requestId: `${TAG}-b1`, mode: "SELECTED",
      lessonId: LESSON_ID, difficulty: "HARD"});
    assert.equal(issued.status, 200, `prepare B1 must pass: ${JSON.stringify(issued.json)}`);
    const ticketB1 = issued.json.result.ticket;
    const frozenDoc = await ticketDoc(RIG_PLAYER, `${TAG}-b1`);
    const layout = answerLayout(frozenDoc, Date.now());
    const mirror = expectedReward(await userDoc(RIG_PLAYER), frozenDoc, 50);
    const settledB1 = await settle(token, {...settleBody(ticketB1, layout.firstWrong()), capturedTerms: CAPTURED_TERMS});
    assert.equal(settledB1.status, 200, `settle B1 must pass: ${JSON.stringify(settledB1.json)}`);
    const resultB1 = settledB1.json.result.result;
    assert.equal(resultB1.receipts[0].percentScore, 50);
    assert.equal(resultB1.receipts[0].codeAnswer, "19", "one wrong answer is a 1, one correct is a 9");
    assert.deepEqual(resultB1.receipts[0].reward, mirror.reward);
    const userAfterSettleB1 = await userDoc(RIG_PLAYER);

    // Поздняя отмена по SETTLED: возвращает статус с квитанцией сеттлмента и ничего не отпускает.
    const cancelledB1 = await cancelTicket(token, {ticketId: ticketB1.ticketId, requestId: `${TAG}-x-b1`});
    assert.equal(cancelledB1.status, 200, `cancel of a SETTLED ticket must be a no-op: ${JSON.stringify(cancelledB1.json)}`);
    assert.equal(cancelledB1.json.result.status, "SETTLED", "the cancel reports the winner's terminal state");
    assert.deepEqual(cancelledB1.json.result.terminal, (await ticketDoc(RIG_PLAYER, `${TAG}-b1`)).terminal,
      "the reported terminal is the settlement's, not a cancellation's");
    const userAfterCancelB1 = await userDoc(RIG_PLAYER);
    assert.equal(userAfterCancelB1.pointsNolics ?? userAfterCancelB1.nolics,
      userAfterSettleB1.pointsNolics ?? userAfterSettleB1.nolics, "the no-op cancel moved no money");
    const cancelRowsB1 = (await ledgerBySource(ticketB1.ticketId)).filter((row) => row.reason === "arena_ticket_cancel");
    assert.equal(cancelRowsB1.length, 0, "the no-op cancel wrote no release rows");

    const replayB1 = await cancelTicket(token, {ticketId: ticketB1.ticketId, requestId: `${TAG}-x-b1`});
    assert.deepEqual(replayB1.json.result, cancelledB1.json.result, "the repeated cancel requestId replays its saved result");
    const statusB1 = await ticketStatus(token, ticketB1.ticketId);
    assert.equal(statusB1.status, 200, `the reconciling read must answer: ${JSON.stringify(statusB1.json)}`);
    assert.equal(statusB1.json.result.ticket.status, "SETTLED");
    // Поверхность статуса отдаёт терминал проекцией без приватного scoreDigest — сверяем её поля.
    const rawTerminalB1 = (await ticketDoc(RIG_PLAYER, `${TAG}-b1`)).terminal;
    assert.ok(rawTerminalB1.scoreDigest, "the raw terminal keeps its scoreDigest");
    const {scoreDigest: _digest, ...publicTerminalB1} = rawTerminalB1;
    assert.deepEqual(statusB1.json.result.ticket.terminal, publicTerminalB1);
    output.parts.settleFirst = {ticketId: ticketB1.ticketId, cancelResult: cancelledB1.json.result.status,
      noRelease: true, replayIdentical: true, statusCallable: statusB1.json.result.ticket.status};
    console.log("B1. settle-first: late cancel is a SETTLED no-op, replay identical, status callable concurs");
  }

  // ── B2/C. Гонка, сторона cancel-первый: позднее завершение не платит, намерение остаётся ────
  {
    const issued = await prepare(token, {requestId: `${TAG}-b2`, mode: "SELECTED",
      lessonId: LESSON_ID, difficulty: "HARD"});
    assert.equal(issued.status, 200, `prepare B2 must pass: ${JSON.stringify(issued.json)}`);
    const ticketB2 = issued.json.result.ticket;
    const frozenDoc = await ticketDoc(RIG_PLAYER, `${TAG}-b2`);
    const layout = answerLayout(frozenDoc, Date.now());

    const cancelledB2 = await cancelTicket(token, {ticketId: ticketB2.ticketId, requestId: `${TAG}-x-b2`});
    assert.equal(cancelledB2.status, 200, `cancel B2 must pass: ${JSON.stringify(cancelledB2.json)}`);
    assert.equal(cancelledB2.json.result.status, "CANCELLED");
    assert.equal(cancelledB2.json.result.terminal.releasedNolics, 1, "the cancel releases the frozen price");
    const userAfterCancelB2 = await userDoc(RIG_PLAYER);

    // Позднее завершение №1: отказ поименованный, ключ освобождён, ноль записей.
    const late1 = await settle(token, {...settleBody(ticketB2, layout.allCorrect()), capturedTerms: CAPTURED_TERMS});
    assert.equal(late1.status, 400, `a late completion of a CANCELLED ticket must be refused: ${JSON.stringify(late1.json)}`);
    assert.equal(late1.reason, "TICKET_CANCELLED", "the refusal must name the race's loser");
    assert.equal(await eventDoc(ticketB2.attemptId), null, "the refused completion wrote no event");
    assert.equal((await ticketDoc(RIG_PLAYER, `${TAG}-b2`)).terminal.receiptId,
      cancelledB2.json.result.terminal.receiptId, "the cancel's terminal is the only terminal");
    const royaltyRowsB2 = (await ledgerBySource(ticketB2.ticketId)).filter((row) =>
      ["arena_author_royalty", "arena_royalty_sink", "arena_royalty_carry"].includes(row.reason));
    assert.equal(royaltyRowsB2.length, 0, "the refused completion paid no royalty");
    const rewardRowsB2 = (await ledgerBySource(ticketB2.attemptId)).filter((row) => row.reason === "attempt_reward");
    assert.equal(rewardRowsB2.length, 0, "the refused completion granted no reward");

    // Позднее завершение №2 (другое тело, тот же тикет): снова отказ — не платит ДВАЖДЫ.
    const otherLayout = answerLayout(frozenDoc, Date.now() + 1);
    const late2 = await settle(token, {...settleBody(ticketB2, otherLayout.firstWrong()), capturedTerms: CAPTURED_TERMS});
    assert.equal(late2.status, 400, `a second distinct late completion must also be refused: ${JSON.stringify(late2.json)}`);
    assert.equal(late2.reason, "TICKET_CANCELLED");
    const userAfterLate2 = await userDoc(RIG_PLAYER);
    assert.equal(userAfterLate2.pointsNolics ?? userAfterLate2.nolics,
      userAfterCancelB2.pointsNolics ?? userAfterCancelB2.nolics, "two refused completions moved no money");
    assert.equal(userAfterLate2.pointsSkill ?? userAfterLate2.skillPoints,
      userAfterCancelB2.pointsSkill ?? userAfterCancelB2.skillPoints, "two refused completions granted no skill");
    assert.equal(await eventDoc(ticketB2.attemptId), null, "still no event document");
    assert.equal(await rigKeyCount(), keysBefore + 2, "refused bodies release their keys: no completed records");

    // Сверяемое чтение: серверная правда, по которой клиентский retained SETTLE резолвится.
    const statusB2 = await ticketStatus(token, ticketB2.ticketId);
    assert.equal(statusB2.status, 200);
    assert.equal(statusB2.json.result.ticket.status, "CANCELLED");
    assert.deepEqual(statusB2.json.result.ticket.terminal, cancelledB2.json.result.terminal);
    output.parts.cancelFirst = {ticketId: ticketB2.ticketId, refusal: late1.reason, secondRefusal: late2.reason,
      noEvent: true, noRoyalty: true, noReward: true, keysUnchanged: true,
      statusCallable: statusB2.json.result.ticket.status};
    console.log("B2/C. cancel-first: late completions refused TICKET_CANCELLED twice, zero writes, retained intent still reconcilable");
  }

  // ── B3. Истинная параллельная гонка: ровно один финальный эффект ────────────────────────────
  {
    const issued = await prepare(token, {requestId: `${TAG}-b3`, mode: "SELECTED",
      lessonId: LESSON_ID, difficulty: "HARD"});
    assert.equal(issued.status, 200, `prepare B3 must pass: ${JSON.stringify(issued.json)}`);
    const ticketB3 = issued.json.result.ticket;
    const frozenDoc = await ticketDoc(RIG_PLAYER, `${TAG}-b3`);
    const layout = answerLayout(frozenDoc, Date.now());
    const userBeforeRace = await userDoc(RIG_PLAYER);
    const keysBeforeRace = await rigKeyCount();

    const race = await Promise.allSettled([
      cancelTicket(token, {ticketId: ticketB3.ticketId, requestId: `${TAG}-x-b3`}),
      settle(token, {...settleBody(ticketB3, layout.allCorrect()), capturedTerms: CAPTURED_TERMS}),
    ]);
    assert.ok(race.every((entry) => entry.status === "fulfilled"), `both racers must answer: ${JSON.stringify(race.map((e) => e.reason))}`);
    const [cancelOutcome, settleOutcome] = race.map((entry) => entry.value);
    const settleWon = settleOutcome.status === 200;
    const raceDoc = await ticketDoc(RIG_PLAYER, `${TAG}-b3`);
    const ledgerB3 = await ledgerBySource(ticketB3.ticketId);
    const cancelReasons = ledgerB3.filter((row) => row.reason === "arena_ticket_cancel");
    const royaltyReasons = ledgerB3.filter((row) =>
      ["arena_author_royalty", "arena_royalty_sink", "arena_royalty_carry"].includes(row.reason));
    const userAfterRace = await userDoc(RIG_PLAYER);
    const raceKeys = await rigKeyCount();
    const raceRecord = {ticketId: ticketB3.ticketId, cancelStatus: cancelOutcome.status,
      settleStatus: settleOutcome.status, settleRefusal: settleOutcome.reason || null,
      terminal: raceDoc.status};

    if (settleWon) {
      assert.equal(cancelOutcome.json.result.status, "SETTLED", "the losing cancel reports the winner's terminal");
      assert.equal(raceDoc.status, "SETTLED");
      assert.equal(raceDoc.terminal.percentScore, 100);
      assert.equal(raceDoc.terminal.consumedNolics, 1);
      assert.equal(cancelReasons.length, 0, "no release rows on the settle-winner path");
      assert.ok(royaltyReasons.length > 0, "royalty rows exist exactly once");
      // userBeforeRace читается ПОСЛЕ prepare — цена уже в резерве; награда единственный дельта.
      const mirror = expectedReward(userBeforeRace, frozenDoc, 100);
      const nolicsDelta = (userAfterRace.pointsNolics ?? userAfterRace.nolics) -
        (userBeforeRace.pointsNolics ?? userBeforeRace.nolics);
      assert.equal(nolicsDelta, mirror.reward.nolics, "exactly one financial effect: the settlement's reward");
      assert.equal(raceKeys, keysBeforeRace + 1, "one completed mutation key");
      raceRecord.effect = "SETTLED once";
    } else {
      assert.equal(settleOutcome.status, 400);
      assert.equal(settleOutcome.reason, "TICKET_CANCELLED", "the losing settle is refused by name");
      assert.equal(cancelOutcome.json.result.status, "CANCELLED");
      assert.equal(cancelOutcome.json.result.terminal.releasedNolics, 1);
      assert.equal(raceDoc.status, "CANCELLED");
      assert.equal(royaltyReasons.length, 0, "no royalty rows on the cancel-winner path");
      assert.equal(cancelReasons.length, 2, "the release rows exist exactly once (nolics + life)");
      const nolicsDelta = (userAfterRace.pointsNolics ?? userAfterRace.nolics) -
        (userBeforeRace.pointsNolics ?? userBeforeRace.nolics);
      assert.equal(nolicsDelta, 1, "exactly one financial effect: the frozen release");
      assert.equal(raceKeys, keysBeforeRace, "no completed mutation key");
      raceRecord.effect = "CANCELLED once";
    }
    assert.equal(userAfterRace.arenaSelectedHeldCount, 0, "the race leaves no helds");
    assert.equal(userAfterRace.reservedNolics, 0, "the race leaves nothing reserved");
    output.parts.concurrentRace = raceRecord;
    console.log("B3. concurrent race:", raceRecord.effect, "| cancel", cancelOutcome.status,
      "| settle", settleOutcome.status, settleOutcome.reason || "");
  }

  // ── D. Неизвестный HARD-скор никогда не ноль ────────────────────────────────────────────────
  {
    // D1. Ноль бывает только заработанным: завершение без единого ответа.
    const issued = await prepare(token, {requestId: `${TAG}-d1`, mode: "SELECTED",
      lessonId: LESSON_ID, difficulty: "HARD"});
    assert.equal(issued.status, 200, `prepare D1 must pass: ${JSON.stringify(issued.json)}`);
    const ticketD1 = issued.json.result.ticket;
    const frozenDoc = await ticketDoc(RIG_PLAYER, `${TAG}-d1`);
    const layout = answerLayout(frozenDoc, Date.now());
    const mirror = expectedReward(await userDoc(RIG_PLAYER), frozenDoc, 0);
    const settledD1 = await settle(token, {...settleBody(ticketD1, layout.none()), capturedTerms: CAPTURED_TERMS});
    assert.equal(settledD1.status, 200, `the answer-less completion must settle honestly: ${JSON.stringify(settledD1.json)}`);
    const receiptD1 = settledD1.json.result.result.receipts[0];
    assert.equal(receiptD1.status, "SETTLED");
    assert.equal(receiptD1.percentScore, 0);
    assert.equal(receiptD1.codeAnswer, "11", "every served question is a 1 (shown, no valid answer)");
    assert.ok(!receiptD1.codeAnswer.includes("0"), "a served question is never a 0 (not shown)");
    assert.equal(computePercentScore(receiptD1.codeAnswer), 0, "the zero is the honest arithmetic of the code");
    assert.deepEqual(receiptD1.reward, mirror.reward);
    const docD1 = await ticketDoc(RIG_PLAYER, `${TAG}-d1`);
    assert.equal(docD1.terminal.consumedNolics, 1, "the earned zero still pays the frozen price");
    const royaltyD1 = await ledgerBySource(ticketD1.ticketId);
    assert.equal(sumLedger(royaltyD1, "arena_author_royalty", "arena_royalty_sink", "arena_royalty_carry"), 1);
    output.parts.unknownNeverZero = {ticketId: ticketD1.ticketId, codeAnswer: receiptD1.codeAnswer,
      percentScore: receiptD1.percentScore, consumedNolics: docD1.terminal.consumedNolics,
      noNotShownDigits: true};
    console.log("D1. answer-less completion: code", receiptD1.codeAnswer, "percent", receiptD1.percentScore,
      "- an earned zero, price paid, no 0 digits");

    // D2. Неразрешённая попытка не получает никакого числа: ABORTED не сеттлится молча.
    const issuedD2 = await prepare(token, {requestId: `${TAG}-d2`, mode: "SELECTED",
      lessonId: LESSON_ID, difficulty: "HARD"});
    assert.equal(issuedD2.status, 200, `prepare D2 must pass: ${JSON.stringify(issuedD2.json)}`);
    const ticketD2 = issuedD2.json.result.ticket;
    const frozenDocD2 = await ticketDoc(RIG_PLAYER, `${TAG}-d2`);
    const layoutD2 = answerLayout(frozenDocD2, Date.now());
    const baseD2 = settleBody(ticketD2, layoutD2.allCorrect());
    const bodyD2 = {...baseD2, capturedTerms: CAPTURED_TERMS,
      payload: {...baseD2.payload, completionKind: "ABORTED"}};
    const aborted = await settle(token, bodyD2);
    assert.equal(aborted.status, 400, `an ABORTED body must not settle: ${JSON.stringify(aborted.json)}`);
    assert.equal(aborted.reason, "EXPLICIT_CANCELLATION_REQUIRED", "the exit without answers must be named");
    const unresolvedDoc = await ticketDoc(RIG_PLAYER, `${TAG}-d2`);
    assert.equal(unresolvedDoc.status, "RESERVED", "the attempt stays unresolved");
    assert.equal(unresolvedDoc.terminal, null, "no terminal, no number");
    assert.equal(await eventDoc(ticketD2.attemptId), null, "no event document carries a score for it");
    // Единственный выход без ответа — явная отмена с замороженным возвратом.
    const cancelledD2 = await cancelTicket(token, {ticketId: ticketD2.ticketId, requestId: `${TAG}-x-d2`});
    assert.equal(cancelledD2.status, 200);
    assert.equal(cancelledD2.json.result.terminal.releasedNolics, 1, "the frozen price returns");
    assert.equal(cancelledD2.json.result.terminal.releasedLifePoints, frozenDocD2.lifeCost, "the frozen life returns");
    output.parts.unknownStaysUnknown = {ticketId: ticketD2.ticketId, refusal: aborted.reason,
      terminalWhileUnresolved: null, noEvent: true, cancelRelease: cancelledD2.json.result.terminal};
    console.log("D2. unresolved attempt: ABORTED refused", aborted.reason, "- no number written, frozen cancel returns it");
  }

  // ── Финальная консервация ────────────────────────────────────────────────────────────────────
  const userFinal = await userDoc(RIG_PLAYER);
  const walletFinal = wallet(userFinal);
  assert.equal(walletFinal.reservedNolics, 0, "nothing stays reserved");
  // Стоячие reservedLifePoints риг-плеера (150) — задокументированный предсуществующий артефакт
  // прошлых риг-прогонов (README 3.7: «life 350/150 — известный дрейф регенерации, вне мутаций»);
  // предмет этого прогона — вернуть резервы ровно к базлайну, ничего не добавив и не потеряв.
  assert.equal(walletFinal.reservedLifePoints, walletBefore.reservedLifePoints,
    "life reservations return exactly to the baseline");
  assert.equal(walletFinal.arenaSelectedHeldCount, 0, "no helds remain");
  const ratingFinal = await ratingRef.get();
  assert.deepEqual(ratingFinal.data(), ratingOriginal, "the rating doc is byte-identical to the baseline");
  const settlesWon = [output.parts.frozenSettle, output.parts.settleFirst,
    output.parts.concurrentRace.effect === "SETTLED once" ? output.parts.concurrentRace : null,
    output.parts.unknownNeverZero].filter(Boolean).length;
  assert.equal(await rigKeyCount(), keysBefore + settlesWon,
    `exactly ${settlesWon} completed mutation keys were added by the whole run`);
  output.final = {wallet: walletFinal, walletBefore, keysBefore, keysAfter: await rigKeyCount(),
    settlesWon};
  const nolicsDelta = walletFinal.nolics - walletBefore.nolics;
  output.final.nolicsDelta = nolicsDelta;
  console.log("final conservation: nolics delta", nolicsDelta, "| keys", keysBefore, "->", output.final.keysAfter,
    "| reserved 0, helds 0, rating restored");

  const evidenceDir = path.join(__dirname, "..", "..", "docs", "agents", "evidence", "2026-10-10-arena-races");
  writeFileSync(path.join(evidenceDir, "live-run.json"), `${JSON.stringify(output, null, 1)}\n`);
  console.log("PASS 3.8 arena-price-immutability-and-races live run");
}

main().catch((error) => { console.error(error); process.exitCode = 1; });
