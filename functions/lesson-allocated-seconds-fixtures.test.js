"use strict";

/**
 * Страж общего набора фикстур для отведённого времени урока.
 *
 * Пара к `unlock-price-fixtures.test.js`. Тот набор пиннит вторую половину цены — перевод секунд
 * в нолики; этот пиннит первую — сами секунды, которые считаются из вопросов урока. Обе половины
 * живут на двух языках: здесь и в `shared/core/scoring/.../LessonAllocatedSeconds.kt`, потому что
 * замок в списке уроков обязан показать число до покупки (AD-3), а списывает по-прежнему сервер.
 *
 * Файл `config/lesson-allocated-seconds-fixtures.json` порождён из этой реализации и читается
 * Kotlin-тестом. Изменил формулу и не пересобрал файл — сборка падает здесь.
 */

const assert = require("assert");
const fixtures = require("../config/lesson-allocated-seconds-fixtures.json");
const {lessonAllocatedSeconds} = require("./lesson-reward");

/** Разворачивает фикстуру обратно в форму, которую читает сервер. */
function questionsOf(item) {
  return item.questions.map((question) => ({
    id: question.id,
    archived: question.archived,
    content: {
      // Объём восстанавливается текстом: формуле важно только его количество.
      text: "x".repeat(question.charsCount),
      difficulty: question.difficulty,
    },
  }));
}

function testTheFixtureFileStillDescribesWhatTheServerComputes() {
  assert.ok(Array.isArray(fixtures) && fixtures.length > 0, "набор фикстур пуст");

  const stale = [];
  for (const item of fixtures) {
    const questions = questionsOf(item);
    const easy = lessonAllocatedSeconds(questions, false);
    const hard = lessonAllocatedSeconds(questions, true);
    if (easy !== item.easyAllocatedSeconds || hard !== item.hardAllocatedSeconds) {
      stale.push(
        `${item.name}: в файле ${item.easyAllocatedSeconds}/${item.hardAllocatedSeconds}, ` +
          `сервер считает ${easy}/${hard}`,
      );
    }
  }

  assert.deepStrictEqual(
    stale,
    [],
    "config/lesson-allocated-seconds-fixtures.json устарел — пересоберите его и проверьте " +
      "Kotlin-сторону:\n" + stale.join("\n"),
  );
}

function testTheAwkwardCasesAreCovered() {
  // Набор без этих случаев проверял бы только сумму — а расходятся реализации на них.
  const names = fixtures.map((item) => item.name).join("|");
  for (const wanted of ["архивный", "переводы", "минимум", "среднее"]) {
    assert.ok(names.includes(wanted), `в наборе нет случая «${wanted}»`);
  }
  const beyondPool = fixtures.find((item) => item.questions.length > 20);
  assert.ok(beyondPool, "в наборе нет урока длиннее набора, который тянет раннер");
}

testTheFixtureFileStillDescribesWhatTheServerComputes();
testTheAwkwardCasesAreCovered();

console.log(`lesson-allocated-seconds-fixtures.test.js OK (${fixtures.length} cases)`);
