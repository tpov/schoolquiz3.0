"use strict";

/**
 * Страж общего набора фикстур расчёта заявок.
 *
 * Расчёт живёт на двух языках: здесь (`charge-claims.js`) и на клиенте
 * (`shared/core/scoring/.../ChargeClaimMask.kt`) — клиент показывает игроку, за что взяли, а
 * списывает сервер, и разойтись им нельзя. Файл `config/charge-claim-fixtures.json` порождён этой
 * реализацией и читается Kotlin-тестом. Изменил правило и не пересобрал файл — сборка падает здесь.
 */

const assert = require("assert");
const fixtures = require("../config/charge-claim-fixtures.json");
const {settleClaims, validateClaimMask} = require("./charge-claims");

function testTheFixtureFileStillDescribesWhatTheServerComputes() {
  assert.ok(Array.isArray(fixtures) && fixtures.length > 0, "набор фикстур пуст");
  const stale = [];
  for (const item of fixtures) {
    const fault = validateClaimMask(item.mask, item.codeAnswer, item.difficulty);
    const settled = fault ? null : settleClaims(item.mask, item.codeAnswer, item.standard, item.plasma, item.order || undefined);
    if (JSON.stringify({fault, settled}) !== JSON.stringify({fault: item.fault, settled: item.settled})) {
      stale.push(item.name);
    }
  }
  assert.deepStrictEqual(stale, [], "config/charge-claim-fixtures.json устарел: " + stale.join(", "));
}

function testTheAwkwardCasesAreCovered() {
  const names = fixtures.map((item) => item.name).join("|");
  for (const wanted of ["порядок", "без плазмы", "подсказка", "девятка", "длина"]) {
    assert.ok(names.includes(wanted), `в наборе нет случая «${wanted}»`);
  }
  assert.ok(fixtures.some((item) => item.fault), "в наборе нет ни одной испорченной маски");
}

testTheFixtureFileStillDescribesWhatTheServerComputes();
testTheAwkwardCasesAreCovered();

console.log(`charge-claim-fixtures.test.js OK (${fixtures.length} cases)`);
