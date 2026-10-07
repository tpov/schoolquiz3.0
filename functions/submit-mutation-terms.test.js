"use strict";

/**
 * Контрактные тесты приёмника: сверка согласия с платными условиями в `submitMutation`
 * (CAP-16, серверная половина AD-5, история 2.8).
 *
 * Сюита едет против живого эмулятора Firestore, а не против подмены: контракт живёт на
 * пересечении приёмника, реестра ключей и чтения `configs/economy` — подмена любого из трёх
 * проверяла бы пересказ, а не контракт. Запуск против demo-backend:
 *
 *   FIRESTORE_EMULATOR_HOST=127.0.0.1:18090 GCLOUD_PROJECT=demo-schoolquiz-mvp node submit-mutation-terms.test.js
 *
 * Без эмулятора сюита пропускается: `npm test` остаётся зелёным там, где бэкенда нет, а
 * широкий гейт гоняет её отдельно (история 2.8). Тестовые документы носят префикс `it_terms_`
 * и удаляются в конце; `configs/economy` сюита не пишет — только читает.
 *
 * Позитивные ветви (согласие достаточно) доказываются чужой ошибкой обработчика: тело без
 * обязательных полей доходит до интейка и падает `invalid-argument` уже ПОСЛЕ сверки условий —
 * значит, гейт пропустил. Полная успешная проводка попытки потребовала бы сеять урок целиком;
 * это доказывает 2.6 и приёмка, а здесь контракт — про гейт.
 */

if (!process.env.FIRESTORE_EMULATOR_HOST) {
  console.log("submit-mutation-terms.test.js SKIP (нет FIRESTORE_EMULATOR_HOST — приёмниковая сюита едет против эмулятора)");
} else {
  const assert = require("assert");
  const admin = require("firebase-admin");
  const crypto = require("crypto");
  const {ECONOMY_CONSTANTS_DOC, readEconomyConstants} = require("./economy-constants");
  const {TERMS_CONSENT_DETAIL} = require("./mutation-terms");

  // Приложение инициализируется до загрузки index.js: его страж видит существующее приложение
  // и не переинициализируется. Проект — тот же, под которым живёт demo-backend.
  admin.initializeApp({projectId: process.env.GCLOUD_PROJECT || "demo-schoolquiz-mvp"});
  const {submitMutation} = require("./index.js");

  const db = admin.firestore();
  const TEST_UID = "it_terms_contract_probe";

  function termsForOperation(table) {
    // Глубокая копия обязана: кейсы ниже правят цену/правила в своей копии и не должны
    // отравлять таблицу для соседних кейсов.
    return JSON.parse(JSON.stringify({version: table.version, activityPrices: table.activityPrices, standard: table.standard, plasma: table.plasma}));
  }

  function envelope(operation, mutationId, extra) {
    return {mutationId, operation, payload: {}, ...extra};
  }

  async function keyDoc(mutationId) {
    // Зеркало safeDocId из index.js: ключи в mutation_keys живут под хэш-префиксованным
    // идентификатором, и проверять освобождение надо именно его. Изменение вывода там громко
    // уронит эту сюиту — молчаливого расхождения не будет.
    const id = safeKeyId(mutationId);
    const snapshot = await db.collection("mutation_keys").doc(id).get();
    return snapshot.exists ? snapshot.data() : null;
  }

  /** Зеркало safeDocId: sha256-префикс + очищенный id мутации. */
  function safeKeyId(mutationId) {
    return crypto.createHash("sha256").update(mutationId).digest("hex").slice(0, 12) + "_" +
      mutationId.replace(/[^A-Za-z0-9_.-]/g, "_").slice(0, 80);
  }

  async function expectTermsRefusal(call, withChange) {
    let refused = null;
    try {
      await call();
    } catch (error) {
      refused = error;
    }
    assert.ok(refused, "платная мутация без согласия обязана быть отклонена");
    assert.strictEqual(refused.code, "failed-precondition", `код отказа: ${refused.code}, ${refused.message}`);
    assert.strictEqual(refused.details && refused.details.code, TERMS_CONSENT_DETAIL);
    if (withChange) {
      assert.ok(refused.details.change, "изменившиеся условия обязаны быть названы");
    } else {
      assert.strictEqual(refused.details.change, undefined, "первое согласие не называет изменения");
    }
  }

  /** Чужая ошибка обработчика: гейт пройден, до интейка дошло, ключ освобождён. */
  async function expectGatePassed(call, mutationId) {
    let thrown = null;
    try {
      await call();
    } catch (error) {
      thrown = error;
    }
    assert.ok(thrown, "пустое тело обязано дойти до обработчика и упасть там");
    assert.strictEqual(thrown.code, "invalid-argument", `ошибка обработчика: ${thrown.code}, ${thrown.message}`);
    assert.notStrictEqual(thrown.details && thrown.details.code, TERMS_CONSENT_DETAIL, "это не отказ по согласию");
    assert.strictEqual(await keyDoc(mutationId), null, "ключ освобождён отказом тела");
  }

  async function seedCompletedKey(mutationId, operation) {
    const now = Date.now();
    await db.collection("mutation_keys").doc(safeKeyId(mutationId)).set({
      mutationId,
      uid: TEST_UID,
      operation,
      state: "completed",
      reservedAtMs: now,
      completedAtMs: now,
      expiresAtMs: now + 24 * 60 * 60 * 1000,
      result: {accepted: 1},
      requestFingerprint: null,
    });
  }

  async function cleanup() {
    await db.collection("profiles").doc(TEST_UID).delete();
    // Ключи ищутся по владельцу, а не по запомненным id: и сид, и приёмник могут оставить
    // документы, сборка по запросу не оставляет щели.
    const keys = await db.collection("mutation_keys").where("uid", "==", TEST_UID).get();
    for (const doc of keys.docs) {
      await doc.ref.delete();
    }
  }

  async function run() {
    await db.collection("profiles").doc(TEST_UID).set({
      testerLevel: 0,
      adminLevel: 0,
      translatorLevel: 0,
      moderatorLevel: 0,
      developerLevel: 0,
      knownLanguages: [],
    });

    const snapshot = await db.doc(ECONOMY_CONSTANTS_DOC).get();
    const table = readEconomyConstants(snapshot.exists ? snapshot.data() : null, 0);
    const ATTEMPT = "lesson_runner.SUBMIT_ATTEMPT";

    // 1. Платная операция без согласия — окончательный отказ; ключ освобождён, а не оставлен.
    const absentId = "it_terms_absent_consent";
    await expectTermsRefusal(
      () => submitMutation.run({auth: {uid: TEST_UID}, data: envelope(ATTEMPT, absentId)}),
      false,
    );
    assert.strictEqual(await keyDoc(absentId), null, "ключ за отказом без согласия не резервируется");

    // 2. Кривое согласие — ошибка конверта, а не согласия.
    let malformed = null;
    try {
      await submitMutation.run({auth: {uid: TEST_UID}, data: envelope(ATTEMPT, "it_terms_malformed", {capturedTerms: 42})});
    } catch (error) {
      malformed = error;
    }
    assert.strictEqual(malformed && malformed.code, "invalid-argument", "не объект — кривой конверт");
    assert.strictEqual(await keyDoc("it_terms_malformed"), null);

    // 3. Устаревшая цена — отказ с поименованным изменением; ключ освобождён.
    const stalePrice = termsForOperation(table);
    stalePrice.activityPrices.ORDINARY_LESSON += 1;
    const stalePriceId = "it_terms_stale_price";
    await expectTermsRefusal(
      () => submitMutation.run({auth: {uid: TEST_UID}, data: envelope(ATTEMPT, stalePriceId, {capturedTerms: stalePrice})}),
      true,
    );
    assert.strictEqual(await keyDoc(stalePriceId), null, "ключ освобождён отказом согласия");

    // 4. Устаревшее правило заряда — тот же отказ.
    const staleRules = termsForOperation(table);
    staleRules.plasma.maxOwned += 1;
    const staleRulesId = "it_terms_stale_rules";
    await expectTermsRefusal(
      () => submitMutation.run({auth: {uid: TEST_UID}, data: envelope(ATTEMPT, staleRulesId, {capturedTerms: staleRules})}),
      true,
    );
    assert.strictEqual(await keyDoc(staleRulesId), null);

    // 5. Поднятая версия без платных правок — не изменение: гейт пройден.
    const bumpedVersion = termsForOperation(table);
    bumpedVersion.version += 1;
    await expectGatePassed(
      () => submitMutation.run({auth: {uid: TEST_UID}, data: envelope(ATTEMPT, "it_terms_version_bump", {capturedTerms: bumpedVersion})}),
      "it_terms_version_bump",
    );

    // 6. Согласие с текущими условиями — гейт пройден, тело падает уже в обработчике.
    await expectGatePassed(
      () => submitMutation.run({auth: {uid: TEST_UID}, data: envelope(ATTEMPT, "it_terms_fresh_consent", {capturedTerms: termsForOperation(table)})}),
      "it_terms_fresh_consent",
    );

    // 7. Неплатная операция согласия не требует вовсе.
    await expectGatePassed(
      () => submitMutation.run({auth: {uid: TEST_UID}, data: envelope("lesson_runner.SUBMIT_RATING", "it_terms_unpaid_op")}),
      "it_terms_unpaid_op",
    );

    // 8. Повтор известного ключа возвращает сохранённое и согласие повторно не сверяет:
    // повтор — не новая операция (AD-6), переспрашивать согласие по нему нечему.
    const replayId = "it_terms_replay_stale_terms";
    await seedCompletedKey(replayId, ATTEMPT);
    const replay = await submitMutation.run({
      auth: {uid: TEST_UID},
      data: envelope(ATTEMPT, replayId, {capturedTerms: stalePrice}),
    });
    assert.deepStrictEqual(replay, {replayed: true, result: {accepted: 1}}, "повтор возвращает сохранённое мимо сверки условий");
  }

  run()
    .then(async () => {
      await cleanup();
      console.log("submit-mutation-terms.test.js OK");
    })
    .catch(async (error) => {
      console.error(error && error.stack ? error.stack : error);
      try {
        await cleanup();
      } catch (cleanupError) {
        console.error(`cleanup failed: ${cleanupError}`);
      }
      process.exitCode = 1;
    });
}
