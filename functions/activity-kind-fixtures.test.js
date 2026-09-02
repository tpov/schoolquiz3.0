"use strict";

/**
 * Страж общего набора фикстур для вида активности.
 *
 * Списывает сервер и выводит вид по своим документам. Клиент повторяет правило по полкам квеста,
 * которые у него уже есть, — чтобы показать цену до запуска. Разойдись зеркала, и игрок увидел бы
 * одно число, а списалось бы другое. Файл порождён этой реализацией и читается Kotlin-тестом.
 */

const assert = require("assert");
const fixtures = require("../config/activity-kind-fixtures.json");
const {activityKindForQuest, shelvesOf} = require("./activity-kind");

function testTheFixtureFileStillDescribesWhatTheServerComputes() {
  assert.ok(Array.isArray(fixtures) && fixtures.length > 0, "набор фикстур пуст");
  const stale = [];
  for (const item of fixtures) {
    const quest = item.isPrivate ? {visibleOn: []} : {visibleOn: item.visibleOn};
    const kind = activityKindForQuest(quest, item.isPrivate ? "private" : "public");
    const shelves = shelvesOf({visibleOn: item.visibleOn});
    if (kind !== item.kind || JSON.stringify(shelves) !== JSON.stringify(item.shelves)) {
      stale.push(`${item.name}: в файле ${item.kind} [${item.shelves}], сервер ${kind} [${shelves}]`);
    }
  }
  assert.deepStrictEqual(stale, [], "config/activity-kind-fixtures.json устарел:\n" + stale.join("\n"));
}

function testTheAwkwardCasesAreCovered() {
  const names = fixtures.map((item) => item.name).join("|");
  for (const wanted of ["дорогой", "снят", "приватный", "написание", "незнакомая"]) {
    assert.ok(names.includes(wanted), `в наборе нет случая «${wanted}»`);
  }
}

testTheFixtureFileStillDescribesWhatTheServerComputes();
testTheAwkwardCasesAreCovered();

console.log(`activity-kind-fixtures.test.js OK (${fixtures.length} cases)`);
