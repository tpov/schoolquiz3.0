"use strict";

/**
 * Страж общего набора фикстур накопления коробок.
 *
 * Серию считают на двух сторонах: здесь (`gift-boxes.js`) и на устройстве, которое копит её без
 * связи. Набор порождён этой реализацией; Kotlin-зеркало читает его, когда появится. Изменил
 * правило и не пересобрал файл — сборка падает здесь.
 */

const assert = require("assert");
const fixtures = require("../config/gift-box-fixtures.json");
const {advanceStreak, boxAccrualVerdict} = require("./gift-boxes");

function testTheFixtureFileStillDescribesWhatTheServerComputes() {
  assert.ok(fixtures.advances.length > 0 && fixtures.claims.length > 0, "набор фикстур пуст");
  const stale = [];
  for (const item of fixtures.advances) {
    if (JSON.stringify(advanceStreak(item.stored, item.nowMs)) !== JSON.stringify(item.expected)) stale.push(item.name);
  }
  for (const item of fixtures.claims) {
    const verdict = boxAccrualVerdict({stored: item.stored, claimed: item.claimed, nowMs: item.nowMs});
    if (JSON.stringify(verdict) !== JSON.stringify(item.expected)) stale.push(item.name);
  }
  assert.deepStrictEqual(stale, [], "config/gift-box-fixtures.json устарел: " + stale.join(", "));
}

function testTheAwkwardCasesAreCovered() {
  const names = fixtures.advances.concat(fixtures.claims).map((item) => item.name).join("|");
  for (const wanted of ["пропущенный", "пять дней", "десятый", "сорок", "без слова"]) {
    assert.ok(names.includes(wanted), `в наборе нет случая «${wanted}»`);
  }
}

testTheFixtureFileStillDescribesWhatTheServerComputes();
testTheAwkwardCasesAreCovered();

console.log(`gift-box-fixtures.test.js OK (${fixtures.advances.length + fixtures.claims.length} cases)`);
