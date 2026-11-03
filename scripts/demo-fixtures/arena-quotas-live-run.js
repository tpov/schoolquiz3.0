"use strict";

// Живое доказательство 3.7 arena-quotas-concurrency (эпик epic-arena-completion) через реальные
// callables demo-backend: хостовой functions-эмулятор (5001) против реальных Firestore/Auth
// контейнера (18090/19090) — та же поверхность scripted client, что у 2.7/3.2/3.3b.
//
// Два актора, два уровня честности:
//   A. demoarena_player_a (синтетический fixture-игрок манифеста, purpose: «repeated price-1
//      rows») — серверная правда квот: 20 random-выдач за UTC-день и отказ 21-й
//      (RANDOM_DAILY_LIMIT), отмена не возвращает квоту; кап пяти одновременно подготовленных
//      SELECTED-тикетов и отказ шестого (SELECTED_LIMIT); гонка за последний слот — ровно один
//      победитель; конкурентный повтор того же requestId — один тикет. Квота player_a остаётся
//      исчерпанной до смены UTC-дня — это задокументированное состояние fixture-игрока, риг-плеер
//      не затрагивается.
//   B. риг-плеер ck3D… — заморозка terms: EASY и HARD тикеты несут собственные замороженные
//      формы (price/lifeCost/bundleDigest); повтор prepare того же requestId после изменения
//      цены возвращает байт-в-байт ту же замороженную форму; изменение рейтинга банка (единственный
//      вход цены) двигает цену для НОВЫХ подготовок (ceiling-запрос получает PRICE_CHANGED, ничего
//      не тратя), при этом документ выданного тикета и его деньги не меняются: cancel возвращает
//      ровно замороженную цену. Рейтинг восстанавливается байт-в-байт той же транзакцией скрипта.
// Деньги входят только через 1-ноликовые банки (random-выдача бесплатна); баланс ck3D после
// прогона сходится в ноль разницы. Пиннированный рейтинг возвращается в исходное состояние, за чем
// следит verify-demo-fixtures отдельным шагом.

const assert = require("node:assert/strict");
const {createHash} = require("node:crypto");
const {readFileSync, writeFileSync} = require("node:fs");
const path = require("node:path");

const PROJECT = "demo-schoolquiz-mvp";
const HOSTS = {firestore: "127.0.0.1:18090", authPort: 19090, upstream: 5001};
const PREPARE_PATH = `/${PROJECT}/us-central1/prepareArenaEntry`;
const CANCEL_PATH = `/${PROJECT}/us-central1/cancelArenaEntry`;
const CATALOG_PATH = `/${PROJECT}/us-central1/listArenaLessons`;
const CALL_TIMEOUT_MS = 30000;

assert.equal(process.env.GCLOUD_PROJECT, PROJECT, "GCLOUD_PROJECT must name the demo project");
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, HOSTS.firestore, "firestore must be the demo backend");
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, `127.0.0.1:${HOSTS.authPort}`, "auth must be the demo auth emulator");
assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined, "no credentials: emulator-only run");

const admin = require(path.join(__dirname, "..", "..", "functions", "node_modules", "firebase-admin"));
admin.initializeApp({projectId: PROJECT});
const db = admin.firestore();

const RIG_PLAYER = "ck3DaiqEd7WGcuVikiw22TyF9fS9";
const PLAYER_A = "demoarena_player_a";
// Префикс requestId-ов прогона: квота и тикеты живут по requestId, повторный прогон в тот же
// UTC-день обязан не переигрывать чужие документы. Квота игрока на день одна: если её уже
// исчерпал прежний прогон этой истории, цикл выдач пропускается с пометкой, а отказ 21-й всё
// равно доказывается.
const TAG = process.env.AQ_TAG || "aq37";
const RUN_REUSED_QUOTA = process.env.AQ_SKIP_LOOP === "1";
const manifest = JSON.parse(readFileSync(path.join(__dirname, "manifest.json"), "utf8"));
const {assertOrRepairRatingPins} = require(path.join(__dirname, "rating-pins.js"));
const BANK_LOW = manifest.banks.find((entry) => entry.id === "arena-bank-low");
const EASY_BANK = manifest.easyBank;
assert.ok(BANK_LOW && EASY_BANK, "manifest must carry bank-low and the easy bank");

const PLAYER_A_ECONOMY = {pointsNolics: 200, nolics: 200, lifePoints: 1000, lifePointsUpdatedAtMs: Date.now(),
  reservedNolics: 0, reservedLifePoints: 0, arenaSelectedHeldCount: 0};

const canonical = (value) => {
  if (value === null || typeof value !== "object" || Array.isArray(value)) return value;
  return Object.fromEntries(Object.keys(value).sort().map((key) => [key, canonical(value[key])]));
};
const sha256 = (value) => createHash("sha256").update(value).digest("hex");
const digest = (value) => sha256(JSON.stringify(canonical(value)));
const ticketDocId = (uid, requestId) => digest(["arena-ticket", uid, requestId]);
const dayId = (now) => new Date(now).toISOString().slice(0, 10);
const quotaDocId = (uid, now) => digest([uid, dayId(now)]);

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

async function prepare(idToken, body) {
  return callable(PREPARE_PATH, body, idToken);
}
async function cancelTicket(idToken, body) {
  return callable(CANCEL_PATH, body, idToken);
}
async function userDoc(uid) {
  return (await db.doc(`users/${uid}`).get()).data() || null;
}
async function ticketDoc(uid, requestId) {
  const doc = await db.doc(`arena_entry_tickets/${ticketDocId(uid, requestId)}`).get();
  return doc.exists ? doc.data() : null;
}
const wallet = (user) => ({pointsNolics: user.pointsNolics ?? user.nolics, lifePoints: user.lifePoints,
  reservedNolics: user.reservedNolics ?? 0, reservedLifePoints: user.reservedLifePoints ?? 0,
  arenaSelectedHeldCount: user.arenaSelectedHeldCount ?? 0});
const WALLET_NEUTRAL = {pointsNolics: PLAYER_A_ECONOMY.pointsNolics, lifePoints: PLAYER_A_ECONOMY.lifePoints,
  reservedNolics: 0, reservedLifePoints: 0, arenaSelectedHeldCount: 0};

async function main() {
  const output = {story: "3.7 arena-quotas-concurrency", startedAt: new Date().toISOString(),
    utcDay: dayId(Date.now()), requestTag: TAG, players: {}, notes: []};

  // Ретро item 4b (L8): у этого драйвера то же хрупкое окно мутации рейтинга, что у races —
  // пины сверяются с манифестом до старта, дрейф чинится громким логом (merge трёх полей),
  // иначе baseline окна и finally-восстановление законсервируют протухший документ.
  output.ratingPinRepairs = await assertOrRepairRatingPins(manifest, db);

  // ── Подготовка акторов ─────────────────────────────────────────────────────────────────────
  // player_a: документ экономики дозаписывается merge-ом ТОЛЬКО когда экономических полей ещё
  // нет (manifest объявляет gold-баланс, а арена читает pointsNolics/lifePoints; fixture-игрок
  // синтетический, запись — часть прогона). Жизненный курсор существующего дока не трогается:
  // он связан с курсором premium_accounts/{uid} (asOfMs), расхождение даёт CURSOR_MISMATCH на
  // следующей регенерации.
  const existingA = await userDoc(PLAYER_A);
  const economyAbsent = !existingA || ((existingA.pointsNolics ?? existingA.nolics) == null && existingA.lifePoints == null);
  if (economyAbsent) await db.doc(`users/${PLAYER_A}`).set(PLAYER_A_ECONOMY, {merge: true});
  const tokenA = await playerIdToken(PLAYER_A);
  const tokenRig = await playerIdToken(RIG_PLAYER);
  output.players.playerA = {uid: PLAYER_A, economy: wallet(await userDoc(PLAYER_A))};
  output.players.rigPlayer = {uid: RIG_PLAYER, economy: wallet(await userDoc(RIG_PLAYER))};
  console.log("actors:", JSON.stringify(output.players));

  // ── A. Квоты и конкурентность (fixture-игрок) ──────────────────────────────────────────────
  const quotaBefore = await db.doc(`arena_ticket_quotas/${quotaDocId(PLAYER_A, Date.now())}`).get();
  assert.equal(output.players.playerA.economy.arenaSelectedHeldCount, 0, "player_a starts with zero helds");
  const loopAlreadyDone = quotaBefore.exists && quotaBefore.data().randomIssued === 20;

  // A1. Двадцать random-выдач за день; каждая отмена возвращает деньги, но не квоту.
  const randomTickets = [];
  output.randomQuota = {issued: 0, ticketIds: randomTickets};
  if (RUN_REUSED_QUOTA || loopAlreadyDone) {
    output.randomQuota.preExhausted = "this UTC day's quota doc already holds randomIssued=20 from a " +
      "previous attempt of this story (attempt 1); the refusal below is still proven live";
    output.randomQuota.issued = 20;
    output.notes.push(`A1 loop skipped for tag ${TAG}: quota pre-exhausted by attempt 1 (its backend state: ` +
      "quota doc randomIssued=20, twenty CANCELLED aq37-r-* tickets); A1's own facts are in attempt 1's log");
    console.log("A1. skipped: quota doc already at 20 from the previous attempt (see its console log)");
  } else {
    assert.equal(quotaBefore.exists, false, "player_a must start the UTC day without a quota doc");
    for (let index = 0; index < 20; index++) {
      const requestId = `${TAG}-r-${index}`;
      const issued = await prepare(tokenA, {requestId, mode: "RANDOM", lessonId: null, difficulty: "HARD"});
      assert.equal(issued.status, 200, `random issue ${index} must pass: ${JSON.stringify(issued.json)}`);
      const ticket = issued.json.result.ticket;
      assert.equal(ticket.priceNolics, 0, "random entry is free");
      const cancelled = await cancelTicket(tokenA, {ticketId: ticket.ticketId, requestId: `${TAG}-c-${index}`});
      assert.equal(cancelled.status, 200, `random cancel ${index} must pass: ${JSON.stringify(cancelled.json)}`);
      assert.equal(cancelled.json.result.terminal.releasedNolics, 0, "nothing was reserved");
      randomTickets.push(ticket.ticketId);
    }
    const quotaDoc = await db.doc(`arena_ticket_quotas/${quotaDocId(PLAYER_A, Date.now())}`).get();
    assert.equal(quotaDoc.data().randomIssued, 20, "exactly twenty random issuances counted");
    const userAfterLoop = await userDoc(PLAYER_A);
    assert.deepEqual(wallet(userAfterLoop), WALLET_NEUTRAL, "the loop is life- and money-neutral");
    output.randomQuota = {issued: 20, ticketIds: randomTickets};
    console.log("A1. 20 random issues + cancels: quota doc = 20, wallet unchanged");
  }

  // A2. Двадцать первая выдача отказывает поименованным кодом, тикета не создаёт.
  const refused = await prepare(tokenA, {requestId: `${TAG}-r-20`, mode: "RANDOM", lessonId: null, difficulty: "HARD"});
  assert.equal(refused.status, 400, "the 21st random issue must be refused");
  assert.equal(refused.reason, "RANDOM_DAILY_LIMIT", `refusal must name the quota: ${JSON.stringify(refused.json)}`);
  assert.equal(await ticketDoc(PLAYER_A, `${TAG}-r-20`), null, "a refused issue creates no ticket");
  // A3. Отмена не возвращает квоту.
  const stillTwenty = await db.doc(`arena_ticket_quotas/${quotaDocId(PLAYER_A, Date.now())}`).get();
  assert.equal(stillTwenty.data().randomIssued, 20, "cancellations never return the quota");
  output.randomQuota.refusal = {status: refused.status, reason: refused.reason};
  console.log("A2/A3. 21st random refused RANDOM_DAILY_LIMIT; cancels did not return the quota");

  // A4. Четыре SELECTED-подготовки price-1 банка занимают четыре слота.
  for (let index = 0; index < 4; index++) {
    const issued = await prepare(tokenA, {requestId: `${TAG}-s-${index}`, mode: "SELECTED",
      lessonId: BANK_LOW.lessonId, difficulty: "HARD"});
    assert.equal(issued.status, 200, `selected issue ${index} must pass: ${JSON.stringify(issued.json)}`);
    assert.equal(issued.json.result.ticket.priceNolics, 1, "bank-low is the price-1 fixture");
  }
  assert.equal((await userDoc(PLAYER_A)).arenaSelectedHeldCount, 4, "four selected helds");

  // A5. Гонка за пятый слот: ровно один из двух параллельных prepare проходит.
  const race = await Promise.allSettled([
    prepare(tokenA, {requestId: `${TAG}-s-race-a`, mode: "SELECTED", lessonId: BANK_LOW.lessonId, difficulty: "HARD"}),
    prepare(tokenA, {requestId: `${TAG}-s-race-b`, mode: "SELECTED", lessonId: BANK_LOW.lessonId, difficulty: "HARD"}),
  ]);
  const fulfilled = race.filter((entry) => entry.status === "fulfilled" && entry.value.status === 200);
  const rejected = race.filter((entry) => entry.status === "fulfilled" && entry.value.status !== 200);
  assert.equal(fulfilled.length, 1, `exactly one race winner: ${JSON.stringify(race.map((e) => e.value?.status))}`);
  assert.equal(rejected.length, 1, "exactly one race loser");
  assert.equal((await userDoc(PLAYER_A)).arenaSelectedHeldCount, 5, "five selected helds after the race");

  // A6. Шестая подготовка отказывает SELECTED_LIMIT прежде денег (код сервера, не догадка).
  const sixth = await prepare(tokenA, {requestId: `${TAG}-s-5`, mode: "SELECTED",
    lessonId: BANK_LOW.lessonId, difficulty: "HARD"});
  assert.equal(sixth.status, 400, "the 6th selected prepare must be refused");
  assert.equal(sixth.reason, "SELECTED_LIMIT", `refusal must name the cap: ${JSON.stringify(sixth.json)}`);
  output.selectedCap = {raceWinner: fulfilled[0].value.json.result.ticket.ticketId,
    raceLoserReason: rejected[0].value.reason, sixthRefusal: sixth.reason};
  console.log("A4-A6. selected cap: race winner exactly one, held 5, 6th refused SELECTED_LIMIT");

  // A7. Конкурентный повтор того же requestId — один тикет, байт-в-байт одинаковый ответ.
  const replayPair = await Promise.all([
    prepare(tokenA, {requestId: `${TAG}-s-race-a`, mode: "SELECTED", lessonId: BANK_LOW.lessonId, difficulty: "HARD"}),
    prepare(tokenA, {requestId: `${TAG}-s-race-a`, mode: "SELECTED", lessonId: BANK_LOW.lessonId, difficulty: "HARD"}),
  ]);
  assert.deepEqual(replayPair[0].json.result, replayPair[1].json.result, "concurrent replay returns one ticket");
  assert.equal((await userDoc(PLAYER_A)).arenaSelectedHeldCount, 5, "the replay created no second held");
  output.selectedCap.concurrentReplayOneTicket = true;
  console.log("A7. concurrent same-requestId replay: identical ticket, no second hold");

  // A8. Уборка: отмена всех пяти освобождает холды и деньги; тикеты остаются CANCELLED.
  for (const requestId of [`${TAG}-s-0`, `${TAG}-s-1`, `${TAG}-s-2`, `${TAG}-s-3`,
    requestIdOf(fulfilled[0].value.json.result.ticket)]) {
    const cancelled = await cancelTicket(tokenA, {ticketId: (await ticketDoc(PLAYER_A, requestId)).ticketId, requestId: `${TAG}-x-${requestId}`});
    assert.equal(cancelled.status, 200, `cleanup cancel of ${requestId} must pass`);
  }
  const userCleaned = await userDoc(PLAYER_A);
  assert.deepEqual(wallet(userCleaned), WALLET_NEUTRAL, "cleanup restores the wallet exactly");
  output.selectedCap.cleanup = {heldAfter: userCleaned.arenaSelectedHeldCount, cancelled: 5};
  console.log("A8. cleanup: five cancels restore the wallet exactly");

  // ── B. Заморозка terms и неизменность выданных тикетов (риг-плеер) ─────────────────────────
  const rigBefore = await userDoc(RIG_PLAYER);
  assert.equal(rigBefore.arenaSelectedHeldCount, 0, "rig player starts with zero selected helds");
  const rigNolicsBefore = rigBefore.pointsNolics ?? rigBefore.nolics;
  output.rigFrozen = {nolicsBefore: rigNolicsBefore};

  // B1. Две замороженные формы одной цены, разные сложности: EASY и HARD.
  const easyIssue = await prepare(tokenRig, {requestId: `${TAG}-fz-easy`, mode: "SELECTED",
    lessonId: EASY_BANK.lessonId, difficulty: "EASY"});
  assert.equal(easyIssue.status, 200, `EASY prepare must pass: ${JSON.stringify(easyIssue.json)}`);
  const hardIssue = await prepare(tokenRig, {requestId: `${TAG}-fz-hard`, mode: "SELECTED",
    lessonId: BANK_LOW.lessonId, difficulty: "HARD"});
  assert.equal(hardIssue.status, 200, `HARD prepare must pass: ${JSON.stringify(hardIssue.json)}`);
  const easyTicket = easyIssue.json.result.ticket;
  const hardTicket = hardIssue.json.result.ticket;
  assert.equal(easyTicket.difficulty, "EASY");
  assert.equal(hardTicket.difficulty, "HARD");
  assert.equal(easyTicket.priceNolics, 1);
  assert.equal(hardTicket.priceNolics, 1);
  assert.notDeepEqual(easyTicket.questions, hardTicket.questions, "public frozen question forms differ per difficulty");
  // bundleDigest — приватный отпечаток, callable его сознательно не отдаёт; разницу замороженных
  // форм читаем из доков тикетов (admin-чтение той же базы).
  const easyDoc = await ticketDoc(RIG_PLAYER, `${TAG}-fz-easy`);
  const hardDoc = await ticketDoc(RIG_PLAYER, `${TAG}-fz-hard`);
  assert.notEqual(easyDoc.bundleDigest, hardDoc.bundleDigest, "per-difficulty frozen bundles differ");
  output.rigFrozen.easy = {ticketId: easyTicket.ticketId, bundleDigest: easyDoc.bundleDigest};
  output.rigFrozen.hard = {ticketId: hardTicket.ticketId, bundleDigest: hardDoc.bundleDigest};
  console.log("B1. frozen forms: EASY", easyDoc.bundleDigest.slice(0, 12), "HARD", hardDoc.bundleDigest.slice(0, 12));

  // B2. Повтор того же requestId (потерянный ответ) возвращает ту же замороженную форму.
  const replay = await prepare(tokenRig, {requestId: `${TAG}-fz-hard`, mode: "SELECTED",
    lessonId: BANK_LOW.lessonId, difficulty: "HARD"});
  assert.equal(replay.status, 200);
  assert.deepEqual(replay.json.result.ticket, hardTicket, "re-prepare returns the identical frozen ticket");
  output.rigFrozen.replayIdentical = true;
  console.log("B2. replay of the same requestId: byte-identical frozen ticket");

  // B3. Окно мутации: рейтинг банка (единственный вход цены) двигается на максимум легальной
  // шкалы. Шкала оценок 1..3 (arena-policy: sum > 3*count отказывает), поэтому «50/1» и «5/1»
  // из первых попыток валили ВЕСЬ юниверс в UNIVERSE_INVALID (наблюдено живьём, см. README);
  // легальный максимум при одном голосе — 3/1: weighted 43/21, ничья с bank-high на верхнем
  // уровне, ничьи делят одну цену — bank-low становится 100. Выданные тикеты и их деньги не
  // меняются, новые подготовки видят новую цену. Окно ловит ЛЮБОЙ сбой: finally восстанавливает
  // исходный документ рейтинга до выхода, даже по assert.
  const ratingRef = db.doc(`lesson_ratings/${BANK_LOW.lessonId}`);
  const ratingBefore = await ratingRef.get();
  const ratingOriginal = ratingBefore.data();
  output.ratingMutation = {before: {ratingSum: ratingOriginal.ratingSum, ratingCount: ratingOriginal.ratingCount,
    ratingVersion: ratingOriginal.ratingVersion}};
  let priceBefore = null; let priceMutated = null; let windowChecks = null;
  try {
    const catalogBefore = await callable(CATALOG_PATH, {pageSize: 50}, tokenRig);
    assert.equal(catalogBefore.status, 200, `catalog must list: ${JSON.stringify(catalogBefore.json).slice(0, 200)}`);
    priceBefore = catalogPrice(catalogBefore, BANK_LOW.lessonId);
    assert.equal(priceBefore, 1, "bank-low must start at price 1");
    await ratingRef.set({...ratingOriginal, ratingSum: 3, ratingCount: 1});
    const catalogMutated = await callable(CATALOG_PATH, {pageSize: 50}, tokenRig);
    assert.equal(catalogMutated.status, 200, `mutated catalog must list: ${JSON.stringify(catalogMutated.json).slice(0, 200)}`);
    priceMutated = catalogPrice(catalogMutated, BANK_LOW.lessonId);
    assert.equal(priceMutated, 100, "the moved rating must move the price for new preparations");
    windowChecks = await insideMutationWindow();
  } finally {
    await ratingRef.set(ratingOriginal);
    const restored = await ratingRef.get();
    assert.deepEqual(restored.data(), ratingOriginal, "the rating doc must be restored byte-for-byte");
    output.ratingMutation.restored = true;
  }
  const catalogRestored = await callable(CATALOG_PATH, {pageSize: 50}, tokenRig);
  assert.equal(catalogRestored.status, 200, `restored catalog must list: ${JSON.stringify(catalogRestored.json).slice(0, 200)}`);
  assert.equal(catalogPrice(catalogRestored, BANK_LOW.lessonId), 1, "the price returns to 1");
  output.rigFrozen.duringMutation = windowChecks;
  console.log("B3/B6. rating mutated in-window: catalog price", priceBefore, "->", priceMutated,
    ", window checks:", windowChecks.summary, ", restored byte-for-byte, price back to 1");

  async function insideMutationWindow() {
    // Документ выданного тикета не изменился.
    const hardDocDuring = await ticketDoc(RIG_PLAYER, `${TAG}-fz-hard`);
    assert.equal(hardDocDuring.priceNolics, 1, "the issued ticket keeps its frozen price");
    // Новый prepare с прежним ceiling — PRICE_CHANGED, ничего не тратит.
    const ceiling = await prepare(tokenRig, {requestId: `${TAG}-fz-hard2`, mode: "SELECTED",
      lessonId: BANK_LOW.lessonId, difficulty: "HARD", maxPriceNolics: 1});
    assert.equal(ceiling.status, 400, "a stale ceiling must be refused at the new price");
    assert.equal(ceiling.reason, "PRICE_CHANGED", `refusal must name the price change: ${JSON.stringify(ceiling.json)}`);
    assert.equal(await ticketDoc(RIG_PLAYER, `${TAG}-fz-hard2`), null, "PRICE_CHANGED spends nothing");
    // Повтор замороженного requestId игнорирует смену юниверса.
    const replayDuring = await prepare(tokenRig, {requestId: `${TAG}-fz-hard`, mode: "SELECTED",
      lessonId: BANK_LOW.lessonId, difficulty: "HARD"});
    assert.deepEqual(replayDuring.json.result.ticket, hardTicket, "the frozen ticket ignores the universe change");
    return {ticketPrice: hardDocDuring.priceNolics, ceilingRefusal: ceiling.reason, replayIdentical: true,
      summary: "ticket frozen, PRICE_CHANGED on stale ceiling, replay unchanged"};
  }

  // B5. Деньги следуют замороженной форме: cancel возвращает ровно замороженную цену.
  const hardCancel = await cancelTicket(tokenRig, {ticketId: hardTicket.ticketId, requestId: `${TAG}-x-hard`});
  assert.equal(hardCancel.status, 200);
  assert.equal(hardCancel.json.result.terminal.releasedNolics, 1, "cancel returns the frozen price");
  const easyCancel = await cancelTicket(tokenRig, {ticketId: easyTicket.ticketId, requestId: `${TAG}-x-easy`});
  assert.equal(easyCancel.status, 200);
  assert.equal(easyCancel.json.result.terminal.releasedNolics, 1);
  const rigAfter = await userDoc(RIG_PLAYER);
  assert.equal(rigAfter.pointsNolics ?? rigAfter.nolics, rigNolicsBefore, "the frozen cancels restore the wallet exactly");
  assert.equal(rigAfter.arenaSelectedHeldCount, 0, "no helds remain");
  output.rigFrozen.cancelRestored = {hard: 1, easy: 1, nolicsAfter: rigAfter.pointsNolics ?? rigAfter.nolics};
  console.log("B5. cancels returned frozen 1+1 nolics; wallet back to", output.rigFrozen.cancelRestored.nolicsAfter);

  writeFileSync(path.join(__dirname, "..", "..", "docs", "agents", "evidence", "2026-10-10-arena-quotas", "live-run.json"),
    `${JSON.stringify(output, null, 1)}\n`);
  console.log("PASS 3.7 arena-quotas-concurrency live run");
}

function requestIdOf(ticket) {
  assert.ok(ticket.requestId, "the race winner ticket must carry its requestId");
  return ticket.requestId;
}

function catalogPrice(response, lessonId) {
  const result = response.json.result;
  const entries = Array.isArray(result) ? result : result.entries || result.lessons || result.items || [];
  const row = entries.find((entry) => entry && (entry.lessonId === lessonId || entry.id === lessonId));
  assert.ok(row, `the catalog must contain ${lessonId}: ${JSON.stringify(result).slice(0, 400)}`);
  return row.priceNolics ?? row.price ?? null;
}

main().catch((error) => { console.error(error); process.exitCode = 1; });
