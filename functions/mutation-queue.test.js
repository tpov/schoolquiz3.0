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

// Вердикт приёмника — то же решение, что принимала транзакция `submitMutation`, но чистое.
{
  const {DECISION_DENIED, gateVerdict} = require("./mutation-queue");

  const fresh = gateVerdict(null, UID, "m-1", "quest.SET_SHELF", NOW);
  assert.strictEqual(fresh.decision, DECISION_EXECUTE);
  assert.strictEqual(fresh.reservation.state, STATE_RESERVED, "выполнение начинается с резервирования ключа");
  assert.strictEqual(fresh.reservation.mutationId, "m-1");
  assert.strictEqual(fresh.reservation.operation, "quest.SET_SHELF");

  const completed = completionRecord(fresh.reservation, {cancelled: true}, NOW);
  const replay = gateVerdict(completed, UID, "m-1", "quest.SET_SHELF", NOW + 1);
  assert.strictEqual(replay.decision, DECISION_REPLAY);
  assert.deepStrictEqual(replay.result, {cancelled: true});
  assert.strictEqual(replay.reservation, null, "повтор ничего не резервирует");

  const inFlight = gateVerdict(fresh.reservation, UID, "m-1", "quest.SET_SHELF", NOW + 1);
  assert.strictEqual(inFlight.decision, DECISION_WAIT);

  // Владелец проверяется раньше состояния: чужой выполненный ключ не отдаёт чужой результат.
  const foreign = gateVerdict(completed, "someone-else", "m-1", "quest.SET_SHELF", NOW + 1);
  assert.strictEqual(foreign.decision, DECISION_DENIED);
  assert.strictEqual(foreign.result, null);
}

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

// Тело приезжает строкой: очередь хранит его как есть и в него не смотрит (AD-5, AD-7).
{
  const {parseMutationPayload} = require("./mutation-queue.js");

  const fromString = parseMutationPayload('{"lessonId":"lesson-1","kind":"UNLOCK_LESSON"}');
  assert.strictEqual(fromString.valid, true, "строка с корректным JSON обязана разбираться");
  assert.strictEqual(fromString.payload.lessonId, "lesson-1");

  // Синхронный вызов того же обработчика тело строкой не заворачивает.
  const fromObject = parseMutationPayload({lessonId: "lesson-2"});
  assert.strictEqual(fromObject.valid, true);
  assert.strictEqual(fromObject.payload.lessonId, "lesson-2");

  const missing = parseMutationPayload(undefined);
  assert.strictEqual(missing.valid, true, "пустое тело — не ошибка формы");
  assert.deepStrictEqual(missing.payload, {});

  // Сломанное тело обязано отличаться от пустого: иначе обработчик получит {} и молча
  // выполнит операцию не над тем, что игрок имел в виду.
  const broken = parseMutationPayload("{not json");
  assert.strictEqual(broken.valid, false, "неразбираемое тело — отказ, а не пустой объект");
  assert.strictEqual(broken.payload, null);

  const notAnObject = parseMutationPayload("[1,2,3]");
  assert.strictEqual(notAnObject.valid, false, "массив телом мутации не является");

  const scalar = parseMutationPayload("42");
  assert.strictEqual(scalar.valid, false, "число телом мутации не является");

  const tooLong = parseMutationPayload('{"a":"' + "x".repeat(1024 * 1024) + '"}');
  assert.strictEqual(tooLong.valid, false, "тело сверх потолка не разбирается вовсе");

  console.log("parseMutationPayload: all tests passed");
}

// ─── Сверка expected_version (история 8.3) ────────────────────────────────────────────────────
{
  const {
    STATE_COMPLETED: COMPLETED,
    STATE_RESERVED: RESERVED,
    VERSION_CONFLICT_DETAIL,
    decideMutation: decide,
    readExpectedVersion,
    reservationRecord: reserve,
    completionRecord: complete,
    versionConflictDetail,
    versionVerdict,
  } = require("./mutation-queue.js");

  // Поля нет. По форме это законно, и разбирает случай приёмник: у операции без версионируемой
  // сущности он сверку не зовёт вовсе, у версионируемой — зовёт, и молчание значит «сущности ещё
  // нет». Молчание, означающее «не сверяй», выключало бы всю сверку тем, что клиент промолчал.
  const absent = readExpectedVersion({mutationId: "m-1", operation: "UNLOCK_LESSON"});
  assert.strictEqual(absent.valid, true);
  assert.strictEqual(absent.expectedVersion, null);
  assert.strictEqual(versionVerdict(absent.expectedVersion, 0).conflict, false, "сущности нет — создание проходит");
  assert.strictEqual(
    versionVerdict(absent.expectedVersion, 6).conflict,
    true,
    "сущность уже есть, а клиент её версии не знает — это конфликт, а не разрешение писать поверх",
  );
  assert.strictEqual(versionVerdict(absent.expectedVersion, 6).serverVersion, 6, "клиенту нужно чем разрешать");

  // Ноль на проводе означает ровно то же, что молчание. Ни один клиент его не шлёт — его модель
  // требует serverRevision либо отсутствующим, либо >= 1, — но принять его и сверить одинаково
  // дешевле, чем держать две несовпадающие ветки для одного утверждения «сущности ещё нет».
  const zero = readExpectedVersion({expectedVersion: 0});
  assert.strictEqual(zero.expectedVersion, 0);
  assert.strictEqual(versionVerdict(zero.expectedVersion, 0).checked, true);
  assert.strictEqual(versionVerdict(zero.expectedVersion, 0).conflict, false);
  assert.strictEqual(versionVerdict(zero.expectedVersion, 1).conflict, true, "документ уже создан кем-то");
  assert.deepStrictEqual(
    versionVerdict(null, 1),
    versionVerdict(0, 1),
    "молчание и явный ноль обязаны решаться одинаково",
  );

  // Оба написания принимаются, но противоречить друг другу не имеют права.
  assert.strictEqual(readExpectedVersion({expected_version: 5}).expectedVersion, 5);
  assert.strictEqual(readExpectedVersion({expectedVersion: 5, expected_version: 5}).expectedVersion, 5);
  assert.strictEqual(readExpectedVersion({expectedVersion: 5, expected_version: 6}).valid, false);

  // Кривое значение — отказ формы, а не молчаливая мутация мимо сверки.
  assert.strictEqual(readExpectedVersion({expectedVersion: "five"}).valid, false);
  assert.strictEqual(readExpectedVersion({expectedVersion: 1.5}).valid, false);
  assert.strictEqual(readExpectedVersion({expectedVersion: -1}).valid, false);
  assert.strictEqual(readExpectedVersion({expectedVersion: true}).valid, false);

  // Версия совпала — эффект применяется.
  const fresh = versionVerdict(5, 5);
  assert.strictEqual(fresh.conflict, false);
  assert.strictEqual(fresh.serverVersion, 5);

  // Версия устарела — конфликт, и сервер обязан сказать, куда он ушёл.
  const stale = versionVerdict(5, 6);
  assert.strictEqual(stale.conflict, true);
  assert.strictEqual(stale.serverVersion, 6, "клиенту нужно чем разрешать конфликт");
  const detail = versionConflictDetail(stale.serverVersion);
  assert.strictEqual(detail.code, VERSION_CONFLICT_DETAIL);
  assert.strictEqual(detail.serverVersion, 6, "версия — отдельным полем, а не в тексте сообщения");

  // Конфликт без номера: сервер версии не знает — поля нет вовсе, а не ноль. Ноль уговорил бы
  // клиента показать «отправить поверх версии 0», и та отправка вернулась бы тем же конфликтом.
  const numberless = versionConflictDetail(0);
  assert.strictEqual(numberless.code, VERSION_CONFLICT_DETAIL);
  assert.strictEqual(Object.prototype.hasOwnProperty.call(numberless, "serverVersion"), false, "нет номера — нет поля");
  const junk = versionConflictDetail("not a number");
  assert.strictEqual(Object.prototype.hasOwnProperty.call(junk, "serverVersion"), false, "мусор — тоже не номер");
  assert.notStrictEqual(VERSION_CONFLICT_DETAIL, "precondition-pending", "пометки обязаны различаться");

  // Клиент, ушедший вперёд сервера, — тоже расхождение, а не успех.
  assert.strictEqual(versionVerdict(7, 6).conflict, true);

  // Повтор отклонённой мутации: ключ не остаётся ни выполненным, ни зарезервированным, поэтому
  // вторая попытка снова доходит до сверки и снова получает конфликт — и хранилище выполненных
  // ключей по-прежнему её не содержит.
  const store = new Map();
  const now = 1_800_000_000_000;
  function receive(mutationId, expectedVersion, serverVersion, at) {
    const verdict = decide(store.get(mutationId) || null, at);
    if (verdict.decision !== DECISION_EXECUTE) return {outcome: verdict.decision};
    const reservation = reserve(mutationId, UID, "quest_authoring.SUBMIT_ARENA", at);
    store.set(mutationId, reservation);
    const check = versionVerdict(expectedVersion, serverVersion);
    if (check.conflict) {
      store.delete(mutationId);
      return {outcome: "conflict", serverVersion: check.serverVersion};
    }
    store.set(mutationId, complete(reservation, {published: true}, at));
    return {outcome: "applied"};
  }

  const firstAttempt = receive("m-conflict", 5, 6, now);
  assert.strictEqual(firstAttempt.outcome, "conflict");
  assert.strictEqual(store.has("m-conflict"), false, "отклонённый ключ не остаётся зарезервированным");

  const secondAttempt = receive("m-conflict", 5, 6, now + 1000);
  assert.strictEqual(secondAttempt.outcome, "conflict", "повтор устаревшей мутации снова конфликт");
  const completedKeys = [...store.entries()].filter(([, record]) => record.state === COMPLETED);
  assert.deepStrictEqual(completedKeys, [], "хранилище выполненных ключей отклонённой мутации не знает");
  assert.strictEqual([...store.values()].some((record) => record.state === RESERVED), false);

  // А та же мутация, пересобранная поверх серверной версии, проходит и записывается выполненной.
  const resolvedAttempt = receive("m-resolved", secondAttempt.serverVersion, 6, now + 2000);
  assert.strictEqual(resolvedAttempt.outcome, "applied");
  assert.strictEqual(store.get("m-resolved").state, COMPLETED);

  console.log("expected_version: all tests passed");
}
