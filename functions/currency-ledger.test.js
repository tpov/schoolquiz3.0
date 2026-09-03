"use strict";

const assert = require("assert");
const {
  CURRENCY,
  REASON,
  SWEPT_CURRENCIES,
  isReportable,
  reconciliationRecord,
  derivedBalance,
  ledgerEntries,
  ledgerEntry,
  reconcile,
} = require("./currency-ledger");

const UID = "u1";
const row = (over) => ledgerEntry({
  uid: UID, currency: CURRENCY.NOLICS, delta: 12, reason: REASON.ATTEMPT_REWARD,
  sourceId: "a1", atMs: 1000, ...over,
});

function testAMovementCarriesItsReasonAndItsAction() {
  // Движение без повода нельзя ни перепроверить, ни объяснить оператору; без идентификатора его
  // нельзя отличить от собственной повторной записи.
  const entry = row();
  assert.strictEqual(entry.uid, UID);
  assert.strictEqual(entry.delta, 12);
  assert.strictEqual(entry.reason, REASON.ATTEMPT_REWARD);
  assert.strictEqual(entry.sourceId, "a1");
  assert.strictEqual(entry.id, `${UID}_${CURRENCY.NOLICS}_${REASON.ATTEMPT_REWARD}_a1`);
}

function testTheIdIsDerivedSoAReplayIsOneMovementNotTwo() {
  // Та же причина, что и у ключа идемпотентности: переигранная транзакция — не второе движение.
  assert.strictEqual(row().id, row().id);
  assert.notStrictEqual(row().id, row({sourceId: "a2"}).id);
  assert.notStrictEqual(row().id, row({currency: CURRENCY.SKILL_POINTS}).id);
}

function testNothingIsRecordedWhenThereIsNothingToRecord() {
  assert.strictEqual(row({delta: 0}), null, "нулевое изменение — не движение");
  assert.strictEqual(row({uid: ""}), null);
  assert.strictEqual(row({sourceId: ""}), null, "без действия движение не объяснить");
}

function testAnUnknownCurrencyOrReasonIsRefusedRatherThanRecorded() {
  // Молча записать чужое слово хуже, чем не записать: сверка потом считала бы такую строку за
  // настоящую и объявила бы расхождение там, где его нет.
  assert.strictEqual(row({currency: "лайки"}), null);
  assert.strictEqual(row({reason: "потому что"}), null);
}

function testTheSignIsKept() {
  // Плата и награда — одна книга: знак и есть разница между ними.
  assert.strictEqual(row({delta: -33, reason: REASON.ATTEMPT_TOLL}).delta, -33);
  assert.strictEqual(row({delta: 1.9}).delta, 1, "дробных движений не бывает");
}

function testOneActionMovesSeveralCurrenciesAtOnce() {
  // Прохождение платит ноликами и очками навыка и берёт плату зарядами — это одно действие.
  const rows = ledgerEntries({
    uid: UID, reason: REASON.ATTEMPT_REWARD, sourceId: "a1", atMs: 1000,
    deltas: {
      [CURRENCY.NOLICS]: 12,
      [CURRENCY.SKILL_POINTS]: 3,
      [CURRENCY.STANDARD_CHARGE_POINTS]: -33,
      [CURRENCY.GOLD]: 0,
    },
  });

  assert.strictEqual(rows.length, 3, "нулевое движение в книгу не попадает");
  assert.deepStrictEqual(rows.map((entry) => entry.currency).sort(),
    [CURRENCY.NOLICS, CURRENCY.SKILL_POINTS, CURRENCY.STANDARD_CHARGE_POINTS].sort());
  assert.ok(rows.every((entry) => entry.sourceId === "a1"));
}

function testTheSweepComparesTheBalanceAgainstTheSumOfMovements() {
  const entries = [
    row({delta: 12, sourceId: "a1", atMs: 1000}),
    row({delta: 12, sourceId: "a2", atMs: 2000}),
    row({delta: -1000, reason: REASON.SLOT_PURCHASE, sourceId: "s1", atMs: 3000}),
  ];

  const honest = reconcile({uid: UID, currency: CURRENCY.NOLICS, storedBalance: -976, entries, atMs: 4000});
  assert.strictEqual(honest.derivedBalance, -976);
  assert.strictEqual(honest.gap, 0);

  const inflated = reconcile({uid: UID, currency: CURRENCY.NOLICS, storedBalance: 5000, entries, atMs: 4000});
  assert.strictEqual(inflated.gap, 5976, "разница и есть находка");
  assert.strictEqual(inflated.entryCount, 3);
}

function testTheSweepSaysHowFarBackItCanSee() {
  // До первой записи книги не было, и разница за тот период объясняется этим, а не игроком.
  const entries = [row({atMs: 7000}), row({sourceId: "a2", atMs: 9000})];
  const finding = reconcile({uid: UID, currency: CURRENCY.NOLICS, storedBalance: 24, entries, atMs: 10_000});

  assert.strictEqual(finding.coversFromMs, 7000);
  assert.strictEqual(finding.gap, 0);
}

function testMovementsOfAnotherCurrencyAreNotCountedIn() {
  const mixed = [row({delta: 12}), row({currency: CURRENCY.GOLD, delta: 100, sourceId: "g1"})];

  assert.strictEqual(derivedBalance(mixed.filter((entry) => entry.currency === CURRENCY.NOLICS)), 12);
  assert.strictEqual(reconcile({uid: UID, currency: CURRENCY.NOLICS, storedBalance: 12, entries: mixed}).gap, 0);
}

function testTheSweepTouchesOnlyForgeableBalances() {
  // Золото и плазма — деньги: их сторожат по каждой операции, а не чинят расписанием задним числом.
  assert.deepStrictEqual(
    SWEPT_CURRENCIES.slice().sort(),
    [CURRENCY.NOLICS, CURRENCY.SKILL_POINTS, CURRENCY.STANDARD_CHARGE_POINTS].sort(),
  );
  assert.ok(!SWEPT_CURRENCIES.includes(CURRENCY.GOLD));
  assert.ok(!SWEPT_CURRENCIES.includes(CURRENCY.PLASMA_CHARGE_POINTS));
}

function testAnAccountOlderThanTheBookIsNotAccused() {
  // До первой строки движения были, а записи о них нет. Разница объясняется этим, а не игроком.
  const gap = {uid: UID, currency: CURRENCY.NOLICS, gap: 500, entryCount: 3, coversFromMs: 9000};

  assert.strictEqual(isReportable(gap, {accountCreatedAtMs: 1000}), false, "аккаунт старше книги");
  assert.strictEqual(isReportable(gap, {accountCreatedAtMs: 9000}), true, "книга с ним ровесница");
  // Запас прощает книге небольшое опоздание: она началась на секунду позже аккаунта, и это ещё не
  // повод считать её неполной.
  assert.strictEqual(isReportable(gap, {accountCreatedAtMs: 8000, graceMs: 5000}), true);
  assert.strictEqual(isReportable(gap, {accountCreatedAtMs: 8000, graceMs: 0}), false, "без запаса — опоздала");
  assert.strictEqual(isReportable({...gap, gap: 0}, {accountCreatedAtMs: 9000}), false, "ноль — не находка");
  assert.strictEqual(isReportable({...gap, entryCount: 0}, {accountCreatedAtMs: 9000}), false, "нечего складывать");
  assert.strictEqual(isReportable(null), false);
}

function testTheFindingReconstructsItselfWithoutTheBook() {
  const finding = reconcile({
    uid: UID, currency: CURRENCY.NOLICS, storedBalance: 5000,
    entries: [row({delta: 12, sourceId: "a1", atMs: 1000})], atMs: 4000,
  });
  const record = reconciliationRecord(finding, 4000);

  assert.strictEqual(record.id, `${UID}_${CURRENCY.NOLICS}_4000`);
  assert.strictEqual(record.gap, 4988);
  assert.ok(record.reason.includes("разница 4988"), record.reason);
  assert.ok(record.reason.includes("движений следует 12"), record.reason);
  for (const field of ["storedBalance", "derivedBalance", "gap", "entryCount", "coversFromMs"]) {
    assert.strictEqual(record[field], finding[field], `в записи нет ${field}`);
  }
}

testTheSweepTouchesOnlyForgeableBalances();
testAnAccountOlderThanTheBookIsNotAccused();
testTheFindingReconstructsItselfWithoutTheBook();
testAMovementCarriesItsReasonAndItsAction();
testTheIdIsDerivedSoAReplayIsOneMovementNotTwo();
testNothingIsRecordedWhenThereIsNothingToRecord();
testAnUnknownCurrencyOrReasonIsRefusedRatherThanRecorded();
testTheSignIsKept();
testOneActionMovesSeveralCurrenciesAtOnce();
testTheSweepComparesTheBalanceAgainstTheSumOfMovements();
testTheSweepSaysHowFarBackItCanSee();
testMovementsOfAnotherCurrencyAreNotCountedIn();

console.log("currency-ledger.test.js OK");
