"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const Module = require("module");
const {createFirestoreStub} = require("./_firestore-stub");
const {admin, store} = createFirestoreStub({});
const adminPath = require.resolve("firebase-admin");
const stub = new Module(adminPath, null);
stub.filename = adminPath; stub.loaded = true; stub.exports = admin;
require.cache[adminPath] = stub;
const functions = require("./index");
const {ECONOMY_CONSTANTS_DOC} = require("./economy-constants");
const uid = "box-player";
function world() {
  const now = Date.now();
  return {[`users/${uid}`]: {uid, nickname: "BoxPlayer", boxCount: 2, boxStreakDays: 2,
    nextBoxAtMs: now + 86_400_000, gold: 0, nolics: 0, pointsNolics: 0}, [`profiles/${uid}`]: {uid}};
}
function call(name, data, owner = uid) { return functions[name].run({auth: {uid: owner}, data}); }
function goldSaleWorld(gold) {
  return {
    ...world(),
    [`users/${uid}`]: {...world()[`users/${uid}`], gold},
    [ECONOMY_CONSTANTS_DOC]: {version: 7,
      premiumProducts: {premium_30d: {days: 30, goldPrice: 500, salesEnabled: true}}},
  };
}

test("real opening requires persistent owner-bound id and never trusts client reward", async () => {
  store.reset(world());
  await assert.rejects(call("openGiftBox", {}), {code: "permission-denied"});
  assert.equal(store.writes.length, 0);
  await assert.rejects(call("openGiftBox", {ownerUid: uid}), {code: "invalid-argument"});
  const intent = {ownerUid: uid, openingId: "opening-id-00000001", reward: {type: "addGold", amount: 999_999_999},
    entropy: Array(8).fill(0)};
  const opened = await call("openGiftBox", intent);
  assert.equal(opened.status, "OPENED");
  assert.equal(opened.ownerUid, uid);
  assert.equal(opened.openedBoxes, 1);
  assert.ok(opened.reward.amount < 999_999_999);
  assert.equal(store.documents.get(`users/${uid}`).boxCount, 1);
  const after = structuredClone([...store.documents]);
  // Simulate a lost response: the client keeps its original id and asks again.
  assert.deepEqual(await call("openGiftBox", intent), opened);
  assert.deepEqual(structuredClone([...store.documents]), after);
  await assert.rejects(call("openGiftBox", intent, "other-account"), {code: "permission-denied"});
  await assert.rejects(call("openGiftBox", {...intent, ownerUid: "other-account"}, "other-account"), {code: "permission-denied"});
});

test("opening failure rolls back box, reward, premium, ledger and receipt together", async () => {
  store.reset(world());
  const before = structuredClone([...store.documents]);
  const intent = {ownerUid: uid, openingId: "opening-id-00000002"};
  store.fail = (kind, path) => kind === "commit" && path.startsWith("box_operations/") ? "commit failed" : null;
  await assert.rejects(call("openGiftBox", intent), /commit failed/);
  assert.deepEqual(structuredClone([...store.documents]), before);
  store.fail = null;
  assert.equal((await call("openGiftBox", intent)).status, "OPENED");
  assert.equal(store.documents.get(`users/${uid}`).boxCount, 1);
});

test("queued opening and direct recovery share the permanent financial receipt", async () => {
  store.reset(world());
  const payload = {ownerUid: uid, openingId: "opening-id-00000003"};
  const direct = await call("openGiftBox", payload);
  const response = await call("submitMutation", {mutationId: "economy-OPEN_GIFT_BOX-opening-id-00000003",
    operation: "economy.OPEN_GIFT_BOX", payload: JSON.stringify(payload)});
  assert.deepEqual(response.result, direct);
  assert.equal(store.documents.get(`users/${uid}`).boxCount, 1);
});

test("profile refresh preserves offline anchor; daily premium is a separate once-per-day server claim", async () => {
  store.reset(world());
  const original = store.documents.get(`users/${uid}`);
  store.documents.set(`users/${uid}`, {...original, nextBoxAtMs: Date.now() - 1000,
    boxStreakDays: 9, premiumUntilMs: Date.now() + 86_400_000});
  const anchor = store.documents.get(`users/${uid}`).nextBoxAtMs;
  await call("ensureUserProfile", {});
  assert.equal(store.documents.get(`users/${uid}`).nextBoxAtMs, anchor);
  assert.equal(store.documents.get(`users/${uid}`).boxCount, 2);
  const daily = await call("submitGiftBoxOperation", {kind: "PREMIUM_DAILY", ownerUid: uid, operationId: "daily-id-0000000001"});
  assert.equal(typeof daily.replayed, "boolean");
  assert.equal(daily.replayed, false);
  assert.equal(daily.result.status, "CLAIMED");
  // Пин полного конверта (retro e7 F1a): набор ключей выведен из конструкции ответа planBoxOperation
  // (functions/box-service.js) — ...boxes даёт boxCount/boxStreakDays/nextBoxAtMs, остальное дословно:
  // schemaVersion, ownerUid, operationId, kind, status, nextBonusBoxAtMs, earnedBoxes, openedBoxes,
  // premiumUntilMs. Дроп/переименование любого ключа обязан краснить эту сюиту, а не живое устройство.
  const claimResultKeys = ["boxCount", "boxStreakDays", "earnedBoxes", "kind", "nextBonusBoxAtMs", "nextBoxAtMs",
    "openedBoxes", "operationId", "ownerUid", "premiumUntilMs", "schemaVersion", "status"];
  assert.deepEqual(Object.keys(daily.result).sort(), claimResultKeys);
  assert.equal(store.documents.get(`users/${uid}`).boxCount, 3);
  assert.equal(store.documents.get(`users/${uid}`).nextBoxAtMs, anchor);
  const again = await call("submitGiftBoxOperation", {kind: "PREMIUM_DAILY", ownerUid: uid, operationId: "daily-id-0000000002"});
  assert.equal(again.result.status, "DAILY_LIMIT");
  assert.deepEqual(Object.keys(again.result).sort(), claimResultKeys);
  const visit = await call("submitGiftBoxOperation", {kind: "VISIT", ownerUid: uid, operationId: "visit-id-0000000001"});
  assert.equal(visit.result.earnedBoxes, 1);
  assert.deepEqual(Object.keys(visit.result).sort(), claimResultKeys);
  assert.equal(store.documents.get(`users/${uid}`).boxCount, 4);
  // Повтор того же operationId читает сохранённый чек как конверт с `replayed:true` и тем же
  // телом результата — клиент обязан засчитать его тем же исходом, а не вечным ретраем.
  const replay = await call("submitGiftBoxOperation", {kind: "PREMIUM_DAILY", ownerUid: uid, operationId: "daily-id-0000000001"});
  assert.equal(replay.replayed, true);
  assert.deepEqual(replay.result, daily.result);
  assert.equal(store.documents.get(`users/${uid}`).boxCount, 4);
});

test("referral registration lands only on a new account, one level deep", async () => {
  // Новый аккаунт по живой ссылке: прямой уровень без цепочки.
  store.reset({["users/inviter-alive"]: {uid: "inviter-alive"}});
  await call("ensureUserProfile", {referredByUid: "inviter-alive"}, "newcomer-0000001");
  const newcomer = store.documents.get("users/newcomer-0000001");
  assert.equal(newcomer.referred, true);
  assert.equal(newcomer.referredBy, "inviter-alive");
  assert.equal(typeof newcomer.referredAtMs, "number");
  // Существующий аккаунт: поле не переписывается — атрибуция честна только при установке.
  store.documents.set("users/newcomer-0000001", {...store.documents.get("users/newcomer-0000001"), referredBy: "first-link"});
  await call("ensureUserProfile", {referredByUid: "inviter-alive"}, "newcomer-0000001");
  assert.equal(store.documents.get("users/newcomer-0000001").referredBy, "first-link");
  store.reset({["users/old-player"]: {uid: "old-player", nickname: "Old"}});
  await call("ensureUserProfile", {referredByUid: "inviter-alive"}, "old-player");
  assert.equal("referredBy" in store.documents.get("users/old-player"), false,
    "существующий игрок не переводит свою историю открытий пригласившему");
  // Мёртвая ссылка: аккаунт создаётся без поля — застрявший код не запирает вход.
  store.reset({});
  await call("ensureUserProfile", {referredByUid: "vanished-inviter"}, "newcomer-0000002");
  assert.equal("referredBy" in store.documents.get("users/newcomer-0000002"), false);
  // Самоприглашение и мусор — громкий именованный отказ.
  store.reset({});
  await assert.rejects(call("ensureUserProfile", {referredByUid: "self-invite-0001"}, "self-invite-0001"),
    {code: "invalid-argument"});
  await assert.rejects(call("ensureUserProfile", {referredByUid: "bad/ref"}, "newcomer-0000003"),
    {code: "invalid-argument"});
  assert.equal(store.documents.has("users/self-invite-0001"), false, "испорченное ничего не создаёт");
});

test("the offers-auction pair keeps its named wire codes and the open table stays creative-free", async () => {
  store.reset(world());
  const now = Date.UTC(2026, 10, 15, 12);
  const originalNow = Date.now;
  Date.now = () => now;
  try {
    await assert.rejects(functions.placeOfferBid.run({data: {amount: 10}}), {code: "unauthenticated"});
    await assert.rejects(call("placeOfferBid", {amount: 0, imageUrl: "i", link: "l"}), {code: "invalid-argument"});
    await assert.rejects(call("placeOfferBid", {amount: 30, imageUrl: "i", link: "l"}), {code: "failed-precondition"},
      "короткий кошелёк доезжает именованным, а не безымянной INTERNAL");
    await assert.rejects(functions.fetchOfferAuction.run({data: {}}), {code: "unauthenticated"});
    store.documents.set(`users/${uid}`, {...store.documents.get(`users/${uid}`), gold: 100});
    const placed = await call("placeOfferBid", {amount: 30, imageUrl: "i", link: "l"});
    assert.deepEqual(placed, {auctionId: "2026-12", amount: 30, raiseCount: 0, gold: 70});
    assert.equal(store.documents.get(`users/${uid}`).gold, 70);
    const view = await call("fetchOfferAuction", {});
    assert.equal(view.open, true);
    assert.deepEqual(view.bids, [{uid, amount: 30, atMs: now}]);
    for (const row of view.bids) {
      assert.equal(Object.prototype.hasOwnProperty.call(row, "imageUrl"), false);
      assert.equal(Object.prototype.hasOwnProperty.call(row, "link"), false);
    }
  } finally {
    Date.now = originalNow;
  }
});


test("the review decision names NOT_FOUND and FAILED_PRECONDITION on the wire (retro m4)", async () => {
  store.reset(world());
  const now = Date.UTC(2027, 0, 3);
  const originalNow = Date.now;
  Date.now = () => now;
  try {
    store.documents.set(`profiles/${uid}`, {...store.documents.get(`profiles/${uid}`), moderatorLevel: 100});
    // NOT_FOUND: решения по несуществующей ставке не бывает — код доезжает до клиента, а не 500.
    await assert.rejects(call("decideOfferAdReview", {bidId: "2027-01_missing", decision: "APPROVED"}),
      {code: "not-found"});
    // FAILED_PRECONDITION: повторное решение по уже решённой ставке — именованный отказ.
    store.documents.set("offer_bids/2027-01_decided", {auctionId: "2027-01", uid,
      amount: 30, createdAtMs: now, raiseCount: 0, status: "WON", reviewStatus: "REJECTED"});
    await assert.rejects(call("decideOfferAdReview", {bidId: "2027-01_decided", decision: "APPROVED"}),
      {code: "failed-precondition"});
    // Экран — только для входа: без аутентификации чтение витрины не начинается.
    await assert.rejects(functions.fetchOffersScreen.run({data: {}}), {code: "unauthenticated"});
  } finally {
    Date.now = originalNow;
  }
});

test("the offers review gate refuses the unqualified, and the screen serves only approved ads", async () => {
  store.reset(world());
  const now = Date.UTC(2027, 0, 2);
  const originalNow = Date.now;
  Date.now = () => now;
  try {
    await assert.rejects(functions.decideOfferAdReview.run({data: {bidId: "b", decision: "APPROVED"}}), {code: "unauthenticated"});
    await assert.rejects(call("decideOfferAdReview", {bidId: "b", decision: "APPROVED"}), {code: "permission-denied"},
      "без уровня модератора решения о детском экране не бывает");
    assert.deepEqual(await call("fetchOffersScreen", {}), {placement: null},
      "пустой экран без размещений — и никакого creative");
    // Кандидат ждёт проверки: экран его не показывает даже модератору через читающую поверхность.
    store.documents.set("offer_bids/2027-01_player", {auctionId: "2027-01", uid,
      amount: 30, createdAtMs: now, raiseCount: 0, status: "WON", reviewStatus: "PENDING"});
    store.documents.set("offers/pending", {auctionId: "2027-01", bidId: "2027-01_player",
      uid, imageUrl: "secret-i", link: "secret-l", reviewStatus: "PENDING", settledAtMs: now});
    assert.deepEqual(await call("fetchOffersScreen", {}), {placement: null}, "кандидат не на экране");
    // Модератор одобряет: кандидат уходит на экран. Гейт читает профиль, а не кошелёк.
    store.documents.set(`profiles/${uid}`, {...store.documents.get(`profiles/${uid}`), moderatorLevel: 100});
    const decision = await call("decideOfferAdReview", {bidId: "2027-01_player", decision: "APPROVED"});
    assert.equal(decision.promotedTo, "current");
    const view = await call("fetchOffersScreen", {});
    assert.equal(view.placement.imageUrl, "secret-i");
    assert.equal(view.placement.link, "secret-l");
    assert.equal(view.placement.uid, uid);
    assert.equal(store.documents.has("offers/pending"), false, "кандидат очищен после решения");
  } finally {
    Date.now = originalNow;
  }
});

test("the shop callable sells premium for gold and names its refusals on the wire", async () => {
  // Золотая покупка — callable-поверхность (история 2.2): списание, лот и ответ здесь, и
  // именно здесь именованные отказы доезжают до клиента. Модульные тесты premium-shop гоняют
  // копию тела; этот — сам callable, его белый список и перевод кодов.
  store.reset(goldSaleWorld(600));
  const bought = await call("applyShopPurchase", {itemId: "PREMIUM"});
  assert.equal(bought.itemId, "PREMIUM");
  assert.equal(bought.balance.gold, 100, "цена таблицы списана");
  assert.equal(bought.balance.hasPremium, true);
  assert.equal(store.documents.get(`users/${uid}`).gold, 100, "списание осело в документе");

  // Короткий кошелёк — failed-precondition, не безымянная INTERNAL.
  store.reset(goldSaleWorld(100));
  await assert.rejects(call("applyShopPurchase", {itemId: "PREMIUM"}), {code: "failed-precondition"});

  // Таблица, которая не продаёт за золото (дефолт: четыре строки — ровно одной нет), —
  // invalid-argument до всякого чтения кошелька.
  store.reset({...world(), [`users/${uid}`]: {...world()[`users/${uid}`], gold: 600}});
  await assert.rejects(call("applyShopPurchase", {itemId: "PREMIUM"}), {code: "invalid-argument"});
});
