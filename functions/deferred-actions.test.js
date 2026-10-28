"use strict";

const assert = require("assert");
const fs = require("fs");
const path = require("path");
const {
  DECISION_EXECUTE,
  DECISION_REPLAY,
  STATE_COMPLETED,
  completionRecord,
  gateVerdict,
  resolveOperation,
} = require("./mutation-queue.js");

/**
 * Три действия, переехавшие в очередь эпиком 5: полка квеста, решение ревьюера, снятие лота.
 *
 * У всех трёх эффект серверно-защищённый (AD-25), локальной половины нет, и единственное, что
 * защищает их от повторной доставки, — ключ идемпотентности приёмника (AD-1). Поэтому здесь
 * проверяются две вещи. Первая — поведение приёмника на повторе: обработчик не вызывается, ответ
 * совпадает с сохранённым. Она гоняется через `gateVerdict` — тот самый код, что стоит в
 * `submitMutation`, — с хранилищем ключей в памяти и обработчиком-счётчиком.
 *
 * Вторая — что в `index.js` обработчик очереди и callable зовут одно и то же тело `apply*`.
 * `index.js` при загрузке поднимает firebase-admin и требует учётные данные, поэтому
 * проверяется исходник: та же техника, что в arena-request-document.test.js.
 */
const NOW = 1_800_000_000_000;
const UID = "reviewer-1";

const SUITE = [];
const test = (name, fn) => SUITE.push([name, fn]);

/**
 * Приёмник в миниатюре: хранилище ключей в памяти, порядок шагов как в `submitMutation` —
 * вердикт по ключу, резервирование, обработчик, запись результата.
 */
function receiver(registry) {
  const store = new Map();
  return async function deliver({mutationId, uid, operation, payload, now}) {
    const resolved = resolveOperation(registry, operation);
    if (!resolved.known) throw new Error(resolved.reason);
    const gate = gateVerdict(store.get(mutationId) || null, uid, mutationId, operation, now);
    if (gate.decision === DECISION_REPLAY) return {replayed: true, result: gate.result};
    if (gate.decision !== DECISION_EXECUTE) throw new Error(gate.reason);
    store.set(mutationId, gate.reservation);
    let result;
    try {
      result = await resolved.handler(uid, payload, now);
    } catch (error) {
      // Отказ обработчика: ключ освобождается, как это делает `releaseReservation`; сюда
      // приезжают только окончательные отказы, и клиент уводит запись в карантин.
      store.delete(mutationId);
      throw error;
    }
    store.set(mutationId, completionRecord(gate.reservation, result, now));
    return {replayed: false, result: result === undefined ? null : result};
  };
}

test("повторное снятие лота возвращает первый результат, а не второе not-found", async () => {
  // Модель `applyCancelNicknameListing`: после первого снятия лота в базе нет.
  const listings = new Map([["nick-1", {sellerUid: UID}]]);
  let handlerCalls = 0;
  const registry = {
    "profile.CANCEL_NICKNAME_LISTING": async (uid, payload) => {
      handlerCalls += 1;
      const listing = listings.get(payload.nickname);
      if (!listing) throw Object.assign(new Error("Listing not found"), {code: "not-found"});
      if (listing.sellerUid !== uid) throw Object.assign(new Error("Not your listing"), {code: "permission-denied"});
      listings.delete(payload.nickname);
      return {cancelled: true};
    },
  };
  const deliver = receiver(registry);
  const envelope = {
    mutationId: "cancel-nick-1",
    uid: UID,
    operation: "profile.CANCEL_NICKNAME_LISTING",
    payload: {nickname: "nick-1"},
    now: NOW,
  };

  const first = await deliver(envelope);
  const second = await deliver({...envelope, now: NOW + 1000});

  assert.deepStrictEqual(first, {replayed: false, result: {cancelled: true}});
  assert.deepStrictEqual(second, {replayed: true, result: {cancelled: true}});
  assert.strictEqual(handlerCalls, 1, "обработчик после успешного снятия не вызывается второй раз");
  assert.strictEqual(listings.has("nick-1"), false);
});

test("лот, купленный за время ожидания, — окончательный отказ, и ключ не сгорает", async () => {
  const listings = new Map();
  const registry = {
    "profile.CANCEL_LOGO_LISTING": async (uid, payload) => {
      if (!listings.has(payload.logo)) throw Object.assign(new Error("Listing not found"), {code: "not-found"});
      return {cancelled: true};
    },
  };
  const deliver = receiver(registry);

  await assert.rejects(
    deliver({mutationId: "cancel-logo-1", uid: UID, operation: "profile.CANCEL_LOGO_LISTING", payload: {logo: "fox"}, now: NOW}),
    (error) => error.code === "not-found",
  );
  // Отказ окончательный: клиент читает NOT_FOUND как Refused и уводит запись в карантин. Повтор
  // с тем же ключом не получил бы «сохранённый успех» — успеха не было.
  await assert.rejects(
    deliver({mutationId: "cancel-logo-1", uid: UID, operation: "profile.CANCEL_LOGO_LISTING", payload: {logo: "fox"}, now: NOW + 1}),
    (error) => error.code === "not-found",
  );
});

test("перенос на полку без уровня разработчика — окончательный отказ, с уровнем — один эффект", async () => {
  let writes = 0;
  const registry = {
    "quest.SET_SHELF": async (uid, payload) => {
      if (uid !== "developer") throw Object.assign(new Error("Developer level is required"), {code: "permission-denied"});
      writes += 1;
      return {questId: payload.questId, targetShelf: payload.targetShelf, version: 2};
    },
  };
  const deliver = receiver(registry);
  const payload = {questId: "q1", targetShelf: "featured"};

  await assert.rejects(
    deliver({mutationId: "shelf-q1-1", uid: "player", operation: "quest.SET_SHELF", payload, now: NOW}),
    (error) => error.code === "permission-denied",
  );

  const first = await deliver({mutationId: "shelf-q1-2", uid: "developer", operation: "quest.SET_SHELF", payload, now: NOW});
  const second = await deliver({mutationId: "shelf-q1-2", uid: "developer", operation: "quest.SET_SHELF", payload, now: NOW + 1});
  assert.strictEqual(writes, 1, "полка переписана один раз");
  assert.deepStrictEqual(second.result, first.result);
});

// --- Исходник index.js: обработчик очереди и callable зовут одно тело ---------------------------

const SOURCE = fs.readFileSync(path.join(__dirname, "index.js"), "utf8");

/** Имена операций — ровно те, что в историях 5.4–5.6, и тело у каждой одно. */
const DEFERRED_ACTIONS = [
  {operation: "quest.SET_SHELF", body: "applySetPublicQuestShelf", callable: "setPublicQuestShelf"},
  {operation: "profile.CANCEL_NICKNAME_LISTING", body: "applyCancelNicknameListing", callable: "cancelNicknameListing"},
  {operation: "profile.CANCEL_LOGO_LISTING", body: "applyCancelLogoListing", callable: "cancelLogoListing"},
];

function registryBlock() {
  const start = SOURCE.indexOf("const MUTATION_HANDLERS = {");
  assert.ok(start >= 0, "реестр MUTATION_HANDLERS должен существовать");
  const end = SOURCE.indexOf("\n};", start);
  return SOURCE.slice(start, end);
}

function callableBlock(name) {
  const start = SOURCE.indexOf(`exports.${name} = onCall(`);
  assert.ok(start >= 0, `callable ${name} должен существовать — старые вызовы не снимаются`);
  const end = SOURCE.indexOf("\n});", start);
  return SOURCE.slice(start, end);
}

for (const action of DEFERRED_ACTIONS) {
  test(`${action.operation} зарегистрирован и делит тело ${action.body} с callable ${action.callable}`, () => {
    const registry = registryBlock();
    // Лишние параметры и аргументы разрешены: обработчик ревью берёт ключ мутации (AD-1),
    // но каждая операция обязана вести в своё тело.
    const registration = new RegExp(`"${action.operation.replace(".", "\\.")}":\\s*\\(uid, payload[^)]*\\) =>\\s*${action.body}\\(uid, payload`);
    assert.ok(registration.test(registry), `${action.operation} должен вести в ${action.body}`);

    const callable = callableBlock(action.callable);
    assert.ok(
      callable.includes(`return ${action.body}(uid, request.data || {});`),
      `callable ${action.callable} обязан звать ${action.body}, а не держать вторую копию логики`,
    );
    assert.ok(
      new RegExp(`async function ${action.body}\\(uid, payload[^)]*\\)`).test(SOURCE),
      `тело ${action.body}(uid, payload) должно быть объявлено один раз`,
    );
  });
}

(async () => {
  for (const [name, fn] of SUITE) {
    try {
      await fn();
    } catch (error) {
      console.error(`FAIL: ${name}`);
      throw error;
    }
  }
  console.log(`deferred-actions.test.js: all ${SUITE.length} tests passed`);
})().catch((error) => {
  console.error(error);
  process.exit(1);
});


// Отказ тела снимает резерв ключа: приёмник в миниатюре выше делает это в catch, и настоящий
// `submitMutation` обязан делать то же — иначе транзиентный отказ держит ключ до таймаута резерва.
{
  const src = require("fs").readFileSync(__dirname + "/index.js", "utf8");
  const call = src.indexOf("result = await resolved.handler(uid, body.payload, now, mutationId, termsEconomy);");
  assert.ok(call > 0, "вызов обработчика из приёмника должен существовать");
  const catchBlock = src.slice(call, src.indexOf("await keyRef.set(completionRecord(", call));
  assert.ok(
    /catch \(error\) \{[\s\S]*releaseReservation\(keyRef, gate\.reservation\)[\s\S]*throw error;/.test(catchBlock),
    "резерв снимается при отказе тела",
  );
  console.log("receiver-releases-on-refusal: all tests passed");
}
