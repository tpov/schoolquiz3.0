"use strict";

const assert = require("assert");

const {
  NEXT_QUESTION_WINDOW_SECONDS,
  WINDOW_DELIVERED,
  WINDOW_EXPIRED,
  WINDOW_OPEN,
  deliveryUpdate,
  deliveryVerdict,
  foreignWindowOwner,
  expiryUpdate,
  mintWindowId,
  openQuestionWindow,
  readyAckVerdict,
  sameWindowIntent,
  windowDocumentId,
} = require("./lesson-question-window");

/**
 * Ветви онлайн-перехода next-question по ONLINE-O2 (история 2.6), без стора.
 *
 * Контракт живёт в трёх правилах, и каждое здесь — таблицей случаев: выдача одна и повтор
 * возвращает тот же вопрос с тем же серверным deadline (§3); deadline ставит серверная выдача и
 * никогда не тело ACK (§5); окно, истекшее без выдачи, закрывает попытку без ответа (§7).
 *
 * Composition этих вердиктов с документами и приёмником мутаций — `lesson-ready-ack-handler.test.js`
 * поверх настоящего `submitMutation`.
 */

const NOW = 1_800_000_000_000;
const ATTEMPT = "attempt-7";
const LESSON = "lesson-online";
const QUESTION = "q-next";
const HARD = true;

/** 100 символов текста, без картинки и списков: 100 × 0.24 = 24 секунды на HARD. */
const CONTENT = {text: "x".repeat(100)};

function open(overrides) {
  return openQuestionWindow({
    attemptId: ATTEMPT,
    lessonId: LESSON,
    lessonVersion: 3,
    completedQuestionIndex: 2,
    nextQuestionId: QUESTION,
    nextQuestionContent: CONTENT,
    isHard: HARD,
    nowMs: NOW,
    ...(overrides || {}),
  });
}

function deliver(record, atMs) {
  const verdict = deliveryVerdict(record, atMs);
  assert.strictEqual(verdict.status, "ISSUE", `expected a first issue, got ${verdict.status}`);
  return {...record, ...deliveryUpdate(verdict.issued)};
}

function testTheWindowCapturesTheConfiguredConstantsAtOpen() {
  const record = open();
  assert.strictEqual(record.schemaVersion, 1);
  assert.strictEqual(record.windowSeconds, NEXT_QUESTION_WINDOW_SECONDS);
  assert.strictEqual(NEXT_QUESTION_WINDOW_SECONDS, 60, "the contract window is the captured 60 seconds");
  assert.strictEqual(record.expiresAtMs, NOW + 60_000);
  assert.strictEqual(record.state, WINDOW_OPEN);
  assert.strictEqual(record.delivery, null);
  assert.strictEqual(record.nextQuestionIndex, 3, "the next question is the completed one plus one");
  assert.strictEqual(record.difficulty, "HARD");
  // Секунды ответа считаются из серверного содержимого тем же правилом, что у награды, — не из тела.
  assert.strictEqual(record.allocatedSeconds, 24);
}

function testTheWindowIdentityIsDeterministicPerTransition() {
  // Повтор события завершения обязан прочитать то же окно, а не открыть второе (§7).
  assert.strictEqual(windowDocumentId(ATTEMPT, 2), windowDocumentId(ATTEMPT, 2));
  assert.strictEqual(mintWindowId(ATTEMPT, 2), mintWindowId(ATTEMPT, 2));
  assert.strictEqual(open().windowId, open().windowId);
  // Другой переход той же попытки — другое окно.
  assert.notStrictEqual(windowDocumentId(ATTEMPT, 2), windowDocumentId(ATTEMPT, 3));
  assert.notStrictEqual(mintWindowId(ATTEMPT, 2), mintWindowId(ATTEMPT, 3));
}

function testARepeatedCompletionWithAnotherBodyIsAConflictNotASecondWindow() {
  const record = open();
  assert.strictEqual(sameWindowIntent(record, {
    attemptId: ATTEMPT,
    lessonId: LESSON,
    lessonVersion: 3,
    completedQuestionIndex: 2,
    nextQuestionId: QUESTION,
    isHard: HARD,
  }), true);
  const others = [
    {attemptId: "attempt-8"},
    {lessonId: "lesson-other"},
    {lessonVersion: 4},
    {completedQuestionIndex: 3},
    {nextQuestionId: "q-other"},
    {isHard: false},
  ];
  for (const other of others) {
    assert.strictEqual(
      sameWindowIntent(record, {
        attemptId: ATTEMPT,
        lessonId: LESSON,
        lessonVersion: 3,
        completedQuestionIndex: 2,
        nextQuestionId: QUESTION,
        isHard: HARD,
        ...other,
      }),
      false,
      `a changed ${Object.keys(other)[0]} must read as another transition`,
    );
  }
}

function testTheFirstDeliveryStartsTheClockAtTheServerMoment() {
  const record = open();
  const at = NOW + 5_000;
  const verdict = deliveryVerdict(record, at);
  assert.strictEqual(verdict.status, "ISSUE");
  assert.strictEqual(verdict.issued.questionIndex, 3);
  assert.strictEqual(verdict.issued.questionId, QUESTION);
  assert.strictEqual(verdict.issued.issuedAtMs, at, "the clock starts at the server moment of delivery");
  assert.strictEqual(verdict.issued.answerDeadlineMs, at + 24_000, "the captured seconds apply as captured");
}

function testARepeatedDeliveryReturnsTheSameQuestionAndTheSameDeadline() {
  const delivered = deliver(open(), NOW + 5_000);
  const later = NOW + 9_000;
  const verdict = deliveryVerdict(delivered, later);
  assert.strictEqual(verdict.status, "DELIVERED");
  assert.strictEqual(verdict.issued.questionId, QUESTION, "the repeat names the same question");
  assert.strictEqual(
    verdict.issued.answerDeadlineMs,
    delivered.delivery.answerDeadlineMs,
    "the repeat returns the already fixed deadline, never a fresh one (§5)",
  );
  assert.strictEqual(verdict.remainderMs, delivered.delivery.answerDeadlineMs - later);
}

function testAWindowExpiredWithoutDeliveryClosesWithoutAnAnswer() {
  const record = open();
  const late = record.expiresAtMs + 1;
  const verdict = deliveryVerdict(record, late);
  assert.strictEqual(verdict.status, "WINDOW_EXPIRED", "an expired window issues nothing (§7)");
  const closed = {...record, ...expiryUpdate(late)};
  assert.strictEqual(closed.state, WINDOW_EXPIRED);
  assert.strictEqual(closed.closedReason, "WINDOW_EXPIRED");
  // Закрытое окно не переоткрывается: следующий запрос видит то же закрытие.
  assert.strictEqual(deliveryVerdict(closed, late + 1_000).status, "WINDOW_EXPIRED");
}


function testACorruptedCapturedClockClosesInsteadOfIssuingNaNHours() {
  // F12: not-finite captured seconds in the ISSUE branch would mint a NaN deadline that the
  // no-reissue rule (5) would then fix forever. A corrupted window closes as expired (7).
  const broken = {...open(), allocatedSeconds: "24"};
  assert.strictEqual(deliveryVerdict(broken, NOW + 1000).status, "WINDOW_EXPIRED");

  // A delivered window with a non-finite deadline shows no NaN remainder: it expired too.
  const delivered = deliver(open(), NOW + 1000);
  const corruptDeadline = {...delivered, delivery: {...delivered.delivery, answerDeadlineMs: "soon"}};
  const verdict = deliveryVerdict(corruptDeadline, NOW + 2000);
  assert.strictEqual(verdict.status, "WINDOW_EXPIRED");
}

function testTheWindowRecordsItsOwner() {
  // F10: the window belongs to whoever opened it; a foreign profile neither begins, nor
  // delivers, nor closes it. Pre-field documents (no ownerUid) behave as before.
  const record = open({ownerUid: "player-a"});
  assert.strictEqual(record.ownerUid, "player-a");
  assert.strictEqual(foreignWindowOwner(record, "player-a"), false);
  assert.strictEqual(foreignWindowOwner(record, "player-b"), true);
  assert.strictEqual(foreignWindowOwner(open(), "player-b"), false);
  assert.strictEqual(foreignWindowOwner(null, "player-a"), false);
}

function testAContradictoryDeliveredStateWithoutADeliveryIsNotASecondIssue() {
  // Испорченный документ не открывает вторые часы: противоречие трактуется как истёкшее.
  const broken = {...open(), state: WINDOW_DELIVERED, delivery: null};
  assert.strictEqual(deliveryVerdict(broken, NOW + 1).status, "WINDOW_EXPIRED");
}

function testDeliveryWithoutAWindowIsAFinalRefusal() {
  assert.strictEqual(deliveryVerdict(null, NOW).status, "NO_WINDOW");
}

function testReadyAckReturnsTheServerFactsAndNeverReadsDeadlinesFromBody() {
  const delivered = deliver(open(), NOW + 5_000);
  const request = {attemptId: ATTEMPT, questionIndex: 3, windowId: delivered.windowId};
  const verdict = readyAckVerdict(delivered, request, NOW + 6_000);
  assert.strictEqual(verdict.ok, true);
  const receipt = verdict.receipt;
  assert.strictEqual(receipt.schemaVersion, 1);
  assert.strictEqual(receipt.status, "ACKED");
  assert.strictEqual(receipt.attemptId, ATTEMPT);
  assert.strictEqual(receipt.questionIndex, 3);
  assert.strictEqual(receipt.windowId, delivered.windowId);
  assert.strictEqual(receipt.issuedAtMs, NOW + 5_000, "the receipt echoes the server issuance");
  assert.strictEqual(receipt.answerDeadlineMs, NOW + 5_000 + 24_000, "the deadline is the delivery's, not the ack's");
  assert.strictEqual(receipt.ackedAtMs, NOW + 6_000);
  // Повтор ACK в другой момент возвращает ту же квитанцию кроме метки приёма: ничего не закрепляется заново (§4).
  const repeat = readyAckVerdict(delivered, request, NOW + 60_000);
  assert.deepStrictEqual(
    {...repeat.receipt, ackedAtMs: 0},
    {...receipt, ackedAtMs: 0},
    "the repeated ack re-echoes the same fixed facts",
  );
}

function testReadyAckRejectsUnknownOrMismatchedIdentity() {
  const delivered = deliver(open(), NOW + 5_000);
  const good = {attemptId: ATTEMPT, questionIndex: 3, windowId: delivered.windowId};
  assert.strictEqual(readyAckVerdict(null, good, NOW).ok, false, "no window — no receipt");
  for (const bad of [
    {attemptId: "attempt-8"},
    {questionIndex: 4},
    {windowId: "win_other"},
  ]) {
    const verdict = readyAckVerdict(delivered, {...good, ...bad}, NOW + 6_000);
    assert.strictEqual(verdict.ok, false, `a mismatched ${Object.keys(bad)[0]} must not ack`);
    assert.strictEqual(verdict.code, "invalid-argument");
  }
}

function testReadyAckBeforeDeliveryIsAFinalRefusal() {
  const opened = open();
  assert.strictEqual(
    readyAckVerdict(opened, {attemptId: ATTEMPT, questionIndex: 3, windowId: opened.windowId}, NOW + 1).ok,
    false,
    "an ack for an undelivered question is a contract violation, not a retry case",
  );
}

// ─── Run ────────────────────────────────────────────────────────────────────────────────────────

const SUITE = [];
const test = (name, fn) => SUITE.push([name, fn]);

test("the window captures the configured constants at open", testTheWindowCapturesTheConfiguredConstantsAtOpen);
test("the window identity is deterministic per transition", testTheWindowIdentityIsDeterministicPerTransition);
test("a repeated completion with another body is a conflict, not a second window", testARepeatedCompletionWithAnotherBodyIsAConflictNotASecondWindow);
test("the first delivery starts the clock at the server moment", testTheFirstDeliveryStartsTheClockAtTheServerMoment);
test("a repeated delivery returns the same question and the same deadline", testARepeatedDeliveryReturnsTheSameQuestionAndTheSameDeadline);
test("a window expired without delivery closes without an answer", testAWindowExpiredWithoutDeliveryClosesWithoutAnAnswer);
test("a contradictory delivered state without a delivery is not a second issue", testAContradictoryDeliveredStateWithoutADeliveryIsNotASecondIssue);
test("a corrupted captured clock closes instead of issuing NaN hours", testACorruptedCapturedClockClosesInsteadOfIssuingNaNHours);
test("the window records its owner", testTheWindowRecordsItsOwner);
test("delivery without a window is a refusal", testDeliveryWithoutAWindowIsAFinalRefusal);
test("ready-ack returns the server facts and never reads deadlines from the body", testReadyAckReturnsTheServerFactsAndNeverReadsDeadlinesFromBody);
test("ready-ack rejects unknown or mismatched identity", testReadyAckRejectsUnknownOrMismatchedIdentity);
test("ready-ack before delivery is a final refusal", testReadyAckBeforeDeliveryIsAFinalRefusal);

let failures = 0;
for (const [name, fn] of SUITE) {
  try {
    fn();
  } catch (error) {
    failures += 1;
    console.error(`FAIL ${name}\n  ${error && error.stack ? error.stack : error}`);
  }
}
if (failures > 0) {
  console.error(`lesson-question-window.test.js: ${failures} of ${SUITE.length} cases failed`);
  process.exitCode = 1;
} else {
  console.log(`lesson-question-window.test.js OK (${SUITE.length} cases)`);
}
