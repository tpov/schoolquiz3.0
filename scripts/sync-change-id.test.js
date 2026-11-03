'use strict';

/**
 * Страж формы журнала изменений (AD-11).
 *
 * Идентификатор документа не содержит времени, поэтому повторная запись того же узла переписывает
 * тот же документ. Со временем в идентификаторе — а именно так сиды писали годами — каждый прогон
 * создавал новые документы, коллекция росла неограниченно, и клиент вычитывал одно и то же
 * изменение столько раз, сколько раз запускали сид.
 *
 * Формы обязаны совпадать с серверными `publicCatalogSyncChangePath` и
 * `lessonContentSyncChangePath` из `functions/index.js`; эти строки повторены здесь, потому что
 * весь смысл теста — поймать расхождение, а импорт из `functions` сделал бы его тавтологией.
 */

const assert = require('assert');
const {
  catalogSyncChangeId,
  catalogSyncChangePath,
  lessonContentSyncChangeId,
  lessonContentSyncChangePath,
} = require('./sync-change-id');

function testTheIdCarriesNoTimeSoARerunOverwritesRatherThanAppends() {
  const first = catalogSyncChangeId('quest', 'q-1');
  const second = catalogSyncChangeId('quest', 'q-1');

  assert.strictEqual(first, second, 'один узел — один документ, сколько раз ни запускай');
  assert.ok(!/\d{10,}/.test(first), 'во времени в идентификаторе и был весь дефект');
}

function testDifferentNodesStillGetDifferentDocuments() {
  assert.notStrictEqual(catalogSyncChangeId('quest', 'q-1'), catalogSyncChangeId('quest', 'q-2'));
  assert.notStrictEqual(catalogSyncChangeId('quest', 'x'), catalogSyncChangeId('lesson', 'x'));
}

function testThePathsMatchWhatTheServerWrites() {
  // Дословно из functions/index.js: publicCatalogSyncChangePath и lessonContentSyncChangePath.
  assert.strictEqual(catalogSyncChangePath('cat-1', 'quest', 'q-1'), 'catalogs/cat-1/sync_changes/quest_q-1');
  assert.strictEqual(lessonContentSyncChangePath('l-1', 'qn-1'), 'lesson_content/l-1/sync_changes/qn-1');
}

function testTheLessonJournalKeepsOnlyTheQuestionId() {
  // В этом журнале других типов не бывает, и сервер тип в идентификатор не кладёт.
  assert.strictEqual(lessonContentSyncChangeId('qn-1'), 'qn-1');
  assert.strictEqual(lessonContentSyncChangeId(42), '42');
}

testTheIdCarriesNoTimeSoARerunOverwritesRatherThanAppends();
testDifferentNodesStillGetDifferentDocuments();
testThePathsMatchWhatTheServerWrites();
testTheLessonJournalKeepsOnlyTheQuestionId();

console.log('sync-change-id.test.js OK');
