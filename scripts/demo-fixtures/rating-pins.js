"use strict";

// Прамбула рейтинговых пинов живых прогонов (ретро item 4b, L8): оба банка манифеста несут
// seeded-пин рейтинга, и дрейф демо-бэкенда чинится ДО старта прогона громким логом, а не
// ломает молча цены — finally-восстановления драйверов возвращают то, что прочитали на
// старте, и протухший baseline они бы законсервировали. Пишутся только три пиновых поля
// (merge): authorUid и любые другие поля документа, как и чужие данные, не трогаются.
// Вызов — в начале main() каждого live-run драйвера; repair-pin.js — та же проверка
// отдельной командой для ручного ремонта.

const assert = require("node:assert/strict");

async function assertOrRepairRatingPins(manifest, db, log = console.log) {
  const repairs = [];
  for (const bank of manifest.banks) {
    assert.equal(bank.rating.seeded, true,
      `bank ${bank.id} rating must be fixture-owned (seeded:true) for the pin preamble`);
    const docPath = `lesson_ratings/${bank.lessonId}`;
    const snapshot = await db.doc(docPath).get();
    const actual = snapshot.exists ? snapshot.data() : null;
    const expected = {
      ratingSum: bank.rating.ratingSum,
      ratingCount: bank.rating.ratingCount,
      ratingVersion: bank.rating.ratingVersion,
    };
    const drifted = !actual ||
      actual.ratingSum !== expected.ratingSum ||
      actual.ratingCount !== expected.ratingCount ||
      actual.ratingVersion !== expected.ratingVersion;
    if (!drifted) continue;
    await db.doc(docPath).set(expected, {merge: true});
    repairs.push({
      doc: docPath,
      from: actual
        ? {ratingSum: actual.ratingSum, ratingCount: actual.ratingCount, ratingVersion: actual.ratingVersion}
        : null,
      to: expected,
    });
  }
  if (repairs.length > 0) {
    log("RATING PIN DRIFT REPAIRED to manifest pins before the run:", JSON.stringify(repairs));
  } else {
    log("rating pins match the manifest: no drift");
  }
  return repairs;
}

module.exports = {assertOrRepairRatingPins};
