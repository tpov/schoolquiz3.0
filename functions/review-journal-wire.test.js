"use strict";

/**
 * Страж проводки: `index.js` действительно пользуется разобранными модулями, а не своей копией.
 *
 * Проверка исходником, а не поведением: `index.js` при загрузке поднимает firebase-admin и требует
 * учётные данные, поэтому вызвать эти функции из обычного node-теста нельзя (тот же довод, что и в
 * `arena-request-document.test.js`). Здесь ловятся ровно те три места, где расхождение молчит:
 *
 *   — журнал ревью, прочитанный целиком, без `limit` и без пары курсора (AD-31);
 *   — задание, которое ищется единственным запросом по полю, которого у прежних документов нет;
 *   — публикация, которая пишет только узлы заявки и не снимает выпавшие из дерева (AD-26).
 */

const assert = require("assert");
const fs = require("fs");
const path = require("path");

const SOURCE = fs.readFileSync(path.join(__dirname, "index.js"), "utf8");

/** Тело функции по её имени — от первой `{` после заголовка до парной ей. */
function bodyOf(name) {
  const header = new RegExp(`(?:function|exports\\.)\\s*${name}\\b`).exec(SOURCE);
  assert.ok(header, `функция ${name} не найдена в index.js`);
  const start = SOURCE.indexOf("{", header.index);
  assert.ok(start > 0, `у ${name} нет тела`);
  let depth = 0;
  for (let index = start; index < SOURCE.length; index += 1) {
    const character = SOURCE[index];
    if (character === "{") depth += 1;
    else if (character === "}") {
      depth -= 1;
      if (depth === 0) return SOURCE.slice(start, index + 1);
    }
  }
  throw new assert.AssertionError({message: `тело ${name} не закрыто`});
}

function testTheReviewJournalIsReadAsAPageAndNotWholesale() {
  const body = bodyOf("fetchReviewAssignmentChanges");

  assert.ok(body.includes("readReviewAssignmentChanges("), "страницу журнала считает review-journal.js");
  assert.ok(
    !/\.where\(\s*"changedAtMs"/.test(body) && !/\.get\(\)/.test(body),
    "своего запроса к журналу здесь быть не должно: он уходил без limit и без пары курсора",
  );
}

function testTheAssignmentLookupKeepsTheCompatibilityChain() {
  const body = bodyOf("readAdminReviewLessonTasksByIds");

  assert.ok(body.includes("resolveReviewAssignments("), "поиск задания — цепочкой из review-journal.js");
  assert.ok(
    !/where\(\s*"assignmentId"\s*,\s*"=="/.test(body),
    "единственный запрос по assignmentId терял задания, записанные прежней версией сервера",
  );
  assert.ok(body.includes("stampAssignmentIds("), "найденное окольным путём задание доклеивает себе поле");
}

function testPublicationRemovesWhatTheAuthorRemoved() {
  const body = bodyOf("publishSubmissionIfReady");

  assert.ok(body.includes("planHierarchyPrune("), "публикация сравнивает заявку с опубликованным деревом");
  assert.ok(body.includes("readPublishedSubtree("), "поддерево читается до батча — батч читать не умеет");
  assert.ok(body.includes("writeHierarchyPruneToBatch("), "снятие идёт тем же батчем, что и публикация");
}

function testPublicationRemovesItFromTheAuthorsOwnCopyToo() {
  // Публичная половина снятия ничего не говорит приватной: автор читает своё дерево из
  // `private/{uid}/…`, и урок, снятый только в каталоге, оставался у него навсегда (AD-26).
  const body = bodyOf("publishSubmissionIfReady");

  assert.ok(body.includes("readPrivateSubtree("), "приватное дерево читается до батча");
  assert.ok(body.includes("planPrivateHierarchyPrune("), "решение — то же самое, из hierarchy-prune.js");
  assert.ok(body.includes("writePrivateHierarchyPruneToBatch("), "снятие идёт тем же батчем, что и публикация");
}

function testTheArchivedNodeAndItsJournalEntryGoTogether() {
  const body = bodyOf("writeHierarchyPruneToBatch");

  assert.ok(body.includes("writeSyncChanges("), "снятие без записи в журнал клиент не увидит никогда (AD-11)");
  assert.ok(body.includes("archived"), "способ снятия — archived, третий из трёх (AD-26)");
}

testTheReviewJournalIsReadAsAPageAndNotWholesale();
testTheAssignmentLookupKeepsTheCompatibilityChain();
testPublicationRemovesWhatTheAuthorRemoved();
testPublicationRemovesItFromTheAuthorsOwnCopyToo();
testTheArchivedNodeAndItsJournalEntryGoTogether();

console.log("review-journal-wire.test.js OK");
