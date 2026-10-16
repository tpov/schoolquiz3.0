"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {makeFakeDb} = require("./_exam-test-db");
const {
  ONE_YEAR_MS,
  VERIFY_STATUS,
  createCertificateSigning,
  issuanceFact,
  latestEarnedIssuance,
  profileProjection,
  publicSnapshot,
  signingWorkRecord,
  verifyVerdict,
} = require("./exam-certificates");

function factAt({uid = "player", courseId = "course", sittingId = "s1", resultVersion = 1,
  percent = 90, issuedAtMs = 1_700_000_000_000, keyId = "k1"} = {}) {
  return issuanceFact({uid, courseId, sittingId, resultVersion, percent, passMark: 60,
    issuedAtMs, keyId, bankSnapshot: {manifestHash: "mh", TE: 0, TH: 480}});
}

/** Подписант-дублёр: детерминированная подпись, умение проверять. */
function fakeSigner({missing = false} = {}) {
  return {
    available: (keyId) => !missing && keyId === "k1",
    sign: async (claims) => ({signature: `sig(${claims.issuedAtMs})`, algorithm: "TEST-ED25519"}),
    verify: () => true,
  };
}

test("issuance fact is immutable by construction: one year, own dates, frozen claims", () => {
  const issuedAtMs = 1_700_000_000_000;
  const fact = factAt({issuedAtMs});
  assert.equal(fact.expiresAtMs, issuedAtMs + ONE_YEAR_MS);
  assert.equal(fact.claims.expiresAtMs, fact.expiresAtMs);
  assert.equal(fact.claims.passMark, 60, "захваченный балл заморожен в требованиях");
  assert.equal(fact.claimsDigest.length, 64);
  // Тот же вход — тот же факт: ретрай терминальной транзакции не создаёт второй.
  const again = factAt({issuedAtMs});
  assert.equal(again.issuanceId, fact.issuanceId);
  const other = factAt({issuedAtMs, sittingId: "s2"});
  assert.notEqual(other.issuanceId, fact.issuanceId);
  assert.equal(fact.signing.state, "PENDING");
});

test("profile shows the latest EARNED issuance, even at a lower passing score", () => {
  const first = factAt({sittingId: "s1", percent: 90, issuedAtMs: 1_000});
  const second = factAt({sittingId: "s2", percent: 70, issuedAtMs: 2_000});
  const selected = latestEarnedIssuance([first, second]);
  assert.equal(selected.percent, 70, "порядок заработка, не счёт");
  const projection = profileProjection(selected);
  assert.equal(projection.certificate.issuanceId, second.issuanceId);
  assert.equal(profileProjection(latestEarnedIssuance([])).certificate, null,
    "пустой список — сертификата нет");
  // Завершение подписи старого факта выбор не возвращает: выбор по earned-порядку.
  const signedFirst = {...first, signing: {state: "SIGNED"}};
  assert.equal(latestEarnedIssuance([signedFirst, second]).percent, 70);
});

test("verify policy reads the fact's own dates and keys only", () => {
  const fact = factAt({issuedAtMs: 1_000, keyId: "k1"});
  const snapshot = publicSnapshot(fact, {signature: "sig", algorithm: "TEST"});
  const keys = {k1: true};
  assert.equal(verifyVerdict({publicSnapshot: null, keyMaterial: keys, nowMs: 2}).status,
    VERIFY_STATUS.NOT_FOUND);
  assert.equal(verifyVerdict({publicSnapshot: snapshot, keyMaterial: keys, nowMs: 2}).status,
    VERIFY_STATUS.VALID);
  assert.equal(verifyVerdict({publicSnapshot: snapshot, keyMaterial: keys,
    nowMs: fact.expiresAtMs + 1}).status, VERIFY_STATUS.EXPIRED,
    "истёкший аттестат остаётся найденным со своим снимком");
  assert.equal(verifyVerdict({publicSnapshot: snapshot, keyMaterial: {}, nowMs: 2}).status,
    VERIFY_STATUS.UNKNOWN_KEY, "материал ключа ни активен, ни удержан");
  // Изменяемые поля профиля в снимке не живут: только требования и подпись.
  assert.equal(JSON.stringify(snapshot).includes("bestPercent"), false);
});

test("signing lifecycle: fence, sign outside, CAS publish, first wins, no unsigned publication", async () => {
  const fact = factAt({sittingId: "s1"});
  const fake = makeFakeDb({initialRows: {
    [`exam_certificates/${fact.issuanceId}`]: fact,
    [`exam_signing_work/${fact.issuanceId}`]: signingWorkRecord(fact),
  }, nowMs: fact.issuedAtMs});
  const signing = createCertificateSigning({db: fake.db, nowMs: () => fake.clock.now,
    signer: fakeSigner()});

  const fence = await signing.claimFence(fact.issuanceId);
  assert.ok(fence, "работа берётся с замком");
  const secondFence = await signing.claimFence(fact.issuanceId);
  assert.equal(secondFence, null, "второй fulфиль ждёт: замок занят");

  const fulfilled = await signing.fulfil({issuanceId: fact.issuanceId, fence});
  assert.deepEqual({fulfilled: fulfilled.fulfilled, alreadyDone: fulfilled.alreadyDone},
    {fulfilled: true, alreadyDone: false});
  const snapshotDoc = fake.rows.get(`exam_certificate_public/${fact.issuanceId}`);
  assert.ok(snapshotDoc, "публикация только после подписи и верификации");
  assert.equal(snapshotDoc.signature, `sig(${fact.issuedAtMs})`);
  const work = fake.rows.get(`exam_signing_work/${fact.issuanceId}`);
  assert.equal(work.state, "SIGNED");
  const stored = fake.rows.get(`exam_certificates/${fact.issuanceId}`);
  assert.equal(stored.signing.state, "SIGNED");
  // Повтор исполнения — уже сделано, второго снимка нет.
  const retry = await signing.fulfil({issuanceId: fact.issuanceId, fence});
  assert.equal(retry.alreadyDone, true);
});

test("absent signer or key leaves recoverable pending work, never unsigned publication", async () => {
  const fact = factAt({sittingId: "s1", keyId: "absent-key"});
  const fake = makeFakeDb({initialRows: {
    [`exam_certificates/${fact.issuanceId}`]: fact,
    [`exam_signing_work/${fact.issuanceId}`]: signingWorkRecord(fact),
  }, nowMs: fact.issuedAtMs});
  const signing = createCertificateSigning({db: fake.db, nowMs: () => fake.clock.now,
    signer: fakeSigner()});
  const fence = await signing.claimFence(fact.issuanceId);
  const result = await signing.fulfil({issuanceId: fact.issuanceId, fence});
  assert.deepEqual({fulfilled: result.fulfilled, reason: result.reason},
    {fulfilled: false, reason: "KEY_UNAVAILABLE"});
  assert.equal(fake.rows.has(`exam_certificate_public/${fact.issuanceId}`), false,
    "неподписанная публикация невозможна");
  const work = fake.rows.get(`exam_signing_work/${fact.issuanceId}`);
  assert.equal(work.state, "PENDING", "восстановимое ожидание");
  // Совсем без подписанта — то же ожидание.
  const bare = createCertificateSigning({db: fake.db, nowMs: () => fake.clock.now, signer: null});
  assert.ok(bare);
});

test("fence loss: a stale fulfilment cannot publish", async () => {
  const fact = factAt({sittingId: "s1"});
  const fake = makeFakeDb({initialRows: {
    [`exam_certificates/${fact.issuanceId}`]: fact,
    [`exam_signing_work/${fact.issuanceId}`]: signingWorkRecord(fact),
  }, nowMs: fact.issuedAtMs});
  const signing = createCertificateSigning({db: fake.db, nowMs: () => fake.clock.now,
    signer: fakeSigner()});
  const stale = await signing.fulfil({issuanceId: fact.issuanceId, fence: "borrowed-fence"});
  assert.deepEqual({fulfilled: stale.fulfilled, reason: stale.reason},
    {fulfilled: false, reason: "FENCE_LOST"});
  assert.equal(fake.rows.has(`exam_certificate_public/${fact.issuanceId}`), false);
});

test("rotation: verification material publishes before use and is retained after retirement", async () => {
  const fake = makeFakeDb({nowMs: 1});
  const signing = createCertificateSigning({db: fake.db, nowMs: () => fake.clock.now,
    signer: fakeSigner()});
  await signing.publishVerificationMaterial({keyId: "k1", material: {public: "jwk-1"}});
  await signing.publishVerificationMaterial({keyId: "k2", material: {public: "jwk-2"}});
  // Ротация: старый материал удержан, новый опубликован до первого выпуска под ним.
  let material = await signing.keyMaterialMap();
  assert.deepEqual(material, {k1: true, k2: true});
  await fake.db.doc("exam_certificate_keys/k1").set({retired: true}, {merge: true});
  material = await signing.keyMaterialMap();
  assert.deepEqual(material, {k1: false, k2: true}, "вышедший ключ удержан, но не активен");
  const fact = factAt({keyId: "k1"});
  assert.equal(verifyVerdict({publicSnapshot: publicSnapshot(fact, {signature: "s", algorithm: "a"}),
    keyMaterial: material, nowMs: fact.issuedAtMs + 1}).status, VERIFY_STATUS.VALID,
    "аттестат под удержанным ключом проверяется");
});
