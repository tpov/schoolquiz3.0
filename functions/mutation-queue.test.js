"use strict";

const assert = require("assert");
const {
  DECISION_EXECUTE,
  DECISION_REPLAY,
  DECISION_WAIT,
  MUTATION_KEY_TTL_MS,
  RESERVATION_TIMEOUT_MS,
  STATE_COMPLETED,
  STATE_RESERVED,
  belongsTo,
  completionRecord,
  decideMutation,
  reservationRecord,
  validateMutation,
} = require("./mutation-queue");

const NOW = 1_800_000_000_000;
const UID = "user-1";

function testAnUnseenKeyIsExecuted() {
  const {decision} = decideMutation(null, NOW);
  assert.strictEqual(decision, DECISION_EXECUTE);
}

function testARepeatOfACompletedMutationReturnsTheSavedResultInsteadOfRunningAgain() {
  // Ради этого случая ключ и существует: сеть оборвала ответ, клиент честно повторил, а сервер
  // всё уже сделал. Второе выполнение было бы вторым списанием.
  const reservation = reservationRecord("m-1", UID, "UNLOCK_LESSON", NOW);
  const completed = completionRecord(reservation, {charged: 500}, NOW);

  const {decision, result} = decideMutation(completed, NOW + 1000);

  assert.strictEqual(decision, DECISION_REPLAY);
  assert.deepStrictEqual(result, {charged: 500});
}

function testAKeyHeldByALiveAttemptMakesTheRepeatWaitRatherThanRun() {
  // Инстанс ещё работает: выполнить сейчас значит выполнить дважды одновременно.
  const reserved = reservationRecord("m-1", UID, "UNLOCK_LESSON", NOW);

  const {decision} = decideMutation(reserved, NOW + RESERVATION_TIMEOUT_MS - 1);

  assert.strictEqual(decision, DECISION_WAIT);
}

function testAnAbandonedReservationIsTakenOver() {
  // Инстанс умер между резервированием и эффектом. Держать ключ вечно значит навсегда
  // заблокировать действие игрока.
  const reserved = reservationRecord("m-1", UID, "UNLOCK_LESSON", NOW);

  const {decision} = decideMutation(reserved, NOW + RESERVATION_TIMEOUT_MS);

  assert.strictEqual(decision, DECISION_EXECUTE);
}

function testAnExpiredButKnownKeyStillReplaysRatherThanRuns() {
  // Клиент нарушил договор, продержав запись дольше её предельного возраста. Выполнять заново
  // всё равно нельзя: пока запись ключа существует, она и есть доказательство, что уже сделано.
  const reservation = reservationRecord("m-1", UID, "BUY", NOW);
  const completed = completionRecord(reservation, {ok: true}, NOW);

  const {decision, result} = decideMutation(completed, NOW + MUTATION_KEY_TTL_MS + 1);

  assert.strictEqual(decision, DECISION_REPLAY);
  assert.deepStrictEqual(result, {ok: true});
}

function testTheKeyOutlivesTheClientsOwnDeadline() {
  // Инвариант AD-1 и AD-22: срок хранения ключа строго больше предельного возраста записи в
  // очереди клиента (14 суток). Иначе пережившая срок запись стала бы новой операцией.
  const clientMaxAgeMs = 14 * 24 * 60 * 60 * 1000;
  assert.ok(
    MUTATION_KEY_TTL_MS > clientMaxAgeMs,
    "срок хранения ключа обязан быть больше предельного возраста записи очереди",
  );
}

function testACompletedRecordKeepsWhatToReturn() {
  const reservation = reservationRecord("m-1", UID, "BUY", NOW);
  const completed = completionRecord(reservation, {balance: 7}, NOW + 5);

  assert.strictEqual(completed.state, STATE_COMPLETED);
  assert.strictEqual(completed.mutationId, "m-1");
  assert.strictEqual(completed.uid, UID);
  assert.deepStrictEqual(completed.result, {balance: 7});
  assert.strictEqual(reservation.state, STATE_RESERVED);
}

function testAnUndefinedResultIsStoredAsNullSoAReplayIsStillDistinguishable() {
  const reservation = reservationRecord("m-1", UID, "BUY", NOW);
  const completed = completionRecord(reservation, undefined, NOW);

  assert.strictEqual(completed.result, null);
}

function testAMutationWithoutAKeyIsRejected() {
  assert.strictEqual(validateMutation({operation: "BUY"}).valid, false);
  assert.strictEqual(validateMutation({mutationId: "m-1"}).valid, false);
  assert.strictEqual(validateMutation(null).valid, false);
}

function testAKeyWithOddCharactersOrLengthIsRejected() {
  // Ключ попадает в id документа: путь с косой чертой создал бы подколлекцию вместо записи.
  assert.strictEqual(validateMutation({mutationId: "a/b", operation: "BUY"}).valid, false);
  assert.strictEqual(validateMutation({mutationId: "a".repeat(129), operation: "BUY"}).valid, false);
  assert.strictEqual(validateMutation({mutationId: "a-b_C9", operation: "BUY"}).valid, true);
}

function testAKeyBelongsToTheAccountThatCreatedIt() {
  // Чужой ключ — не повтор, а попытка прочитать чужой результат.
  const reservation = reservationRecord("m-1", UID, "BUY", NOW);

  assert.strictEqual(belongsTo(reservation, UID), true);
  assert.strictEqual(belongsTo(reservation, "someone-else"), false);
  assert.strictEqual(belongsTo(null, UID), true);
}

testAnUnseenKeyIsExecuted();
testARepeatOfACompletedMutationReturnsTheSavedResultInsteadOfRunningAgain();
testAKeyHeldByALiveAttemptMakesTheRepeatWaitRatherThanRun();
testAnAbandonedReservationIsTakenOver();
testAnExpiredButKnownKeyStillReplaysRatherThanRuns();
testTheKeyOutlivesTheClientsOwnDeadline();
testACompletedRecordKeepsWhatToReturn();
testAnUndefinedResultIsStoredAsNullSoAReplayIsStillDistinguishable();
testAMutationWithoutAKeyIsRejected();
testAKeyWithOddCharactersOrLengthIsRejected();
testAKeyBelongsToTheAccountThatCreatedIt();

console.log("mutation-queue.test.js: all tests passed");

// Незнакомая операция — окончательный отказ, а не повод повторять.
{
  const {resolveOperation} = require("./mutation-queue.js");
  const registry = {"lesson.UNLOCK": async () => ({ok: true})};

  const known = resolveOperation(registry, "lesson.UNLOCK");
  assert.strictEqual(known.known, true, "зарегистрированная операция обязана находиться");
  assert.strictEqual(typeof known.handler, "function");

  const unknown = resolveOperation(registry, "lesson.MINT_GOLD");
  assert.strictEqual(unknown.known, false, "незарегистрированная операция не должна выполняться");
  assert.strictEqual(unknown.handler, null);

  const empty = resolveOperation(registry, "");
  assert.strictEqual(empty.known, false, "пустое имя операции — не операция");

  // Прототипные ключи не должны выдаваться за обработчики.
  const injected = resolveOperation(registry, "constructor");
  assert.strictEqual(injected.known, false, "унаследованное свойство не обработчик");

  console.log("resolveOperation: all tests passed");
}
