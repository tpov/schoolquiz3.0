"use strict";

/**
 * Страж общего набора фикстур.
 *
 * Цена открытия урока считается на двух языках: здесь и в
 * `shared/core/scoring/.../UnlockPricing.kt`. Клиент обязан уметь её посчитать, иначе очередь не
 * покажет цену до отправки (AD-3), а сервер остаётся тем, кто её назначает.
 *
 * Две реализации одной формулы расходятся неизбежно, поэтому набор фикстур один: он лежит в
 * `config/unlock-price-fixtures.json`, порождён этим кодом и читается Kotlin-тестом. Этот тест
 * проверяет вторую половину договора — что файл всё ещё описывает то, что сервер считает
 * сегодня. Изменил формулу и не пересобрал файл — сборка падает здесь.
 */

const assert = require("assert");
const fixtures = require("../config/unlock-price-fixtures.json");
const {unlockPrice, UNLOCK_LESSON, UNLOCK_HARD_MODE} = require("./lesson-unlocks");

const KINDS = {[UNLOCK_LESSON]: UNLOCK_LESSON, [UNLOCK_HARD_MODE]: UNLOCK_HARD_MODE};

function testTheFixtureFileStillDescribesWhatTheServerComputes() {
  assert.ok(Array.isArray(fixtures) && fixtures.length > 0, "набор фикстур пуст");

  const stale = [];
  for (const item of fixtures) {
    assert.ok(KINDS[item.kind], `неизвестный вид разблокировки: ${item.kind}`);
    const actual = unlockPrice(item.kind, {
      easyAllocatedSeconds: item.easyAllocatedSeconds,
      hardAllocatedSeconds: item.hardAllocatedSeconds,
    });
    if (actual !== item.price) {
      stale.push(
        `${item.kind} ${item.easyAllocatedSeconds}/${item.hardAllocatedSeconds}: ` +
          `в файле ${item.price}, сервер считает ${actual}`,
      );
    }
  }

  assert.deepStrictEqual(
    stale,
    [],
    "config/unlock-price-fixtures.json устарел — пересоберите его и проверьте Kotlin-сторону:\n" + stale.join("\n"),
  );
}

function testBothKindsAreCovered() {
  // Набор без одного из видов пропустил бы ровно ту половину формулы, которая различает их.
  const kinds = new Set(fixtures.map((item) => item.kind));
  assert.ok(kinds.has(UNLOCK_LESSON), "в наборе нет покупки урока целиком");
  assert.ok(kinds.has(UNLOCK_HARD_MODE), "в наборе нет покупки сложного режима");
}

function testTheEdgeOfTheScaleIsCovered() {
  // Урок без вопросов — граница, на которой цена упирается в минимум.
  const zero = fixtures.find(
    (item) => item.easyAllocatedSeconds === 0 && item.hardAllocatedSeconds === 0,
  );
  assert.ok(zero, "в наборе нет урока нулевого размера");
  assert.strictEqual(zero.price, 1, "даром дверь не открывается");
}

testTheFixtureFileStillDescribesWhatTheServerComputes();
testBothKindsAreCovered();
testTheEdgeOfTheScaleIsCovered();

console.log(`unlock-price-fixtures.test.js OK (${fixtures.length} cases)`);
