"use strict";

const assert = require("assert");
const {
  ORDINARY_LESSON,
  ARENA,
  TOURNAMENT,
  THEME_TEST,
  FINAL_EXAM,
  activityKindForQuest,
  shelvesOf,
  sourceShelfForQuest,
} = require("./activity-kind");
const {ACTIVITY_KINDS, activityPrice, readEconomyConstants} = require("./economy-constants");

function testTheKindComesFromTheQuestNotFromTheClient() {
  // Клиент может объявить что угодно — сюда его объявление не попадает вовсе.
  assert.strictEqual(activityKindForQuest({visibleOn: ["home"]}, "public"), ORDINARY_LESSON);
  assert.strictEqual(activityKindForQuest({visibleOn: ["arena"]}, "public"), ARENA);
  assert.strictEqual(activityKindForQuest({visibleOn: ["tournament"]}, "public"), TOURNAMENT);
  assert.strictEqual(activityKindForQuest({visibleOn: ["tournamentFinal"]}, "public"), TOURNAMENT);
  assert.strictEqual(activityKindForQuest({visibleOn: ["archive"]}, "public"), ORDINARY_LESSON);
}

function testAQuestOnSeveralShelvesIsChargedAsTheDearest() {
  // Единственное направление ошибки, которое не открывает дыру.
  assert.strictEqual(activityKindForQuest({visibleOn: ["home", "tournament"]}, "public"), TOURNAMENT);
  assert.strictEqual(activityKindForQuest({visibleOn: ["archive", "arena"]}, "public"), ARENA);
  assert.strictEqual(sourceShelfForQuest({visibleOn: ["tournament", "tournamentFinal"]}, "public"), "tournamentFinal");
}

function testAPrivateQuestIsAnOrdinaryLessonAndStaysPrivateDownstream() {
  // Приватный квест полок не имеет, и документ у него в другом месте; вид ясен без чтения.
  assert.strictEqual(activityKindForQuest(null, "private"), ORDINARY_LESSON);
  assert.strictEqual(sourceShelfForQuest(null, "private"), "private");
}

function testADelistedQuestIsAnOrdinaryLessonWithNoShelfToRouteBy() {
  assert.strictEqual(activityKindForQuest({visibleOn: []}, "public"), ORDINARY_LESSON);
  assert.strictEqual(sourceShelfForQuest({visibleOn: []}, "public"), "");
}

function testAMissingQuestIsUnknownAndUnknownIsChargedAtTheDearestRate() {
  // Выдуманный questId при настоящем lessonId получал бы награду за урок по непроверенной цене.
  const kind = activityKindForQuest(null, "public");
  assert.strictEqual(kind, null);
  const constants = readEconomyConstants(null);
  assert.strictEqual(activityPrice(constants, kind), constants.activityPrices.TOURNAMENT);
}

function testShelfSpellingIsForgiven() {
  // Данные писались руками и скриптами годами: регистр и пробелы — не повод потерять турнир.
  assert.deepStrictEqual(shelvesOf({visibleOn: [" Tournament ", "HOME", "tournamentfinal", 7, null]}),
    ["tournamentFinal", "tournament", "home"]);
  assert.deepStrictEqual(shelvesOf({visibleOn: "home"}), [], "строка вместо списка — это не полка");
}

function testEveryKindTheModuleCanNameHasAPrice() {
  // Два словаря, которые обязаны быть одним: вид, который нельзя оценить, — бесплатная попытка.
  for (const kind of [ORDINARY_LESSON, ARENA, TOURNAMENT, THEME_TEST, FINAL_EXAM]) {
    assert.ok(ACTIVITY_KINDS.includes(kind), `у прейскуранта нет строки ${kind}`);
  }
}

testTheKindComesFromTheQuestNotFromTheClient();
testAQuestOnSeveralShelvesIsChargedAsTheDearest();
testAPrivateQuestIsAnOrdinaryLessonAndStaysPrivateDownstream();
testADelistedQuestIsAnOrdinaryLessonWithNoShelfToRouteBy();
testAMissingQuestIsUnknownAndUnknownIsChargedAtTheDearestRate();
testShelfSpellingIsForgiven();
testEveryKindTheModuleCanNameHasAPrice();

console.log("activity-kind.test.js OK");
