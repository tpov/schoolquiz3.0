'use strict';

/**
 * Сид списковой синхронизации пишет журнал общей функцией — значит, во все журналы, откуда узел
 * читают (AD-11, инвариант 2).
 *
 * Раньше сид клал записи одним своим `batch.set` и только в каталожный журнал. Вопрос читают два
 * журнала, и второй — `lesson_content/{lessonId}/sync_changes` — оставался пустым: открытый урок
 * приходил без вопросов, а симптом был неотличим от настоящей поломки синхронизации.
 */

const assert = require('assert');
const {isSyncChangeDocument} = require('../functions/sync-changes');
const {
  CATALOG_ID,
  LESSON_ID,
  QUESTION_IDS,
  QUEST_ID,
  buildInitialChanges,
  useFirestore,
  writeIncrementalQuestionChange,
  writeSeed,
} = require('./seed-sync-list-test-data');

const T0 = 1_756_800_000_000;

/** Метки времени, какими их видит сид: только `fromMillis`, только `toMillis` обратно. */
const TIMESTAMPS = {fromMillis: (ms) => ({toMillis: () => ms})};

/** Firestore в памяти: `doc(path)`, `collection(path).doc(id)[.collection(name)]`, `batch()`. */
function fakeFirestore() {
  const store = new Map();
  const parentOf = (path) => path.slice(0, path.lastIndexOf('/'));
  const idOf = (path) => path.slice(path.lastIndexOf('/') + 1);

  function docRef(path) {
    return {
      path,
      collection: (name) => collectionRef(`${path}/${name}`),
      async get() {
        return {exists: store.has(path), id: idOf(path), ref: docRef(path), data: () => store.get(path)};
      },
      async update(patch) {
        store.set(path, {...(store.get(path) || {}), ...patch});
      },
    };
  }

  function collectionRef(path) {
    return {
      path,
      doc: (id) => docRef(`${path}/${id}`),
      async get() {
        const docs = [...store.keys()]
          .filter((key) => parentOf(key) === path)
          .map((key) => ({id: idOf(key), ref: docRef(key), data: () => store.get(key)}));
        return {docs, empty: docs.length === 0, size: docs.length};
      },
    };
  }

  return {
    doc: docRef,
    collection: collectionRef,
    batch() {
      const staged = [];
      return {
        set: (ref, data) => staged.push({op: 'set', path: ref.path, data}),
        delete: (ref) => staged.push({op: 'delete', path: ref.path}),
        async commit() {
          for (const write of staged) {
            if (write.op === 'set') store.set(write.path, {...write.data});
            else store.delete(write.path);
          }
        },
      };
    },
    ids: (collectionPath) =>
      [...store.keys()].filter((key) => parentOf(key) === collectionPath).map(idOf).sort(),
    snapshot: () => Object.fromEntries([...store.entries()]),
  };
}

async function testTheQuestionReachesBothOfItsJournals() {
  const db = fakeFirestore();
  useFirestore(db, TIMESTAMPS);

  await writeSeed(T0);

  // Журнал урока — тот, который читает открытый урок. Прежде он оставался пустым.
  assert.deepStrictEqual(db.ids(`lesson_content/${LESSON_ID}/sync_changes`), [...QUESTION_IDS].sort());
  for (const questionId of QUESTION_IDS) {
    const body = db.snapshot()[`lesson_content/${LESSON_ID}/sync_changes/${questionId}`];
    assert.deepStrictEqual(
      {type: body.type, id: body.id},
      {type: 'question', id: questionId},
      JSON.stringify(body),
    );
  }
}

async function testTheCatalogJournalKeepsOneDocumentPerNodeInTheOneForm() {
  const db = fakeFirestore();
  useFirestore(db, TIMESTAMPS);

  const {changes} = await writeSeed(T0);

  const ids = db.ids(`catalogs/${CATALOG_ID}/sync_changes`);
  assert.strictEqual(ids.length, changes.length);
  assert.ok(ids.includes(`quest_${QUEST_ID}`), ids.join(', '));
  assert.ok(ids.includes(`question_${QUESTION_IDS[0]}`), ids.join(', '));
  for (const id of ids) {
    const body = db.snapshot()[`catalogs/${CATALOG_ID}/sync_changes/${id}`];
    assert.ok(isSyncChangeDocument(body), `${id}: ${JSON.stringify(body)}`);
    assert.ok(!/\d{10,}/.test(id), `во времени в id документа и был весь дефект: ${id}`);
  }
}

async function testTheIncrementalChangeGoesToBothJournalsToo() {
  const db = fakeFirestore();
  useFirestore(db, TIMESTAMPS);
  await writeSeed(T0);

  const change = await writeIncrementalQuestionChange(T0 + 100);

  const inCatalog = db.snapshot()[`catalogs/${CATALOG_ID}/sync_changes/question_${QUESTION_IDS[0]}`];
  const inLesson = db.snapshot()[`lesson_content/${LESSON_ID}/sync_changes/${QUESTION_IDS[0]}`];
  assert.strictEqual(inCatalog.changedAtMs, change.changedAtMs);
  assert.strictEqual(inLesson.changedAtMs, change.changedAtMs);
  assert.ok(isSyncChangeDocument(inCatalog));
  assert.ok(isSyncChangeDocument(inLesson));
}

function testEveryNodeNamesTheJournalsItBelongsTo() {
  // Вопрос обязан назвать урок, квест — сказать, что приватной половины у него нет. Забытое поле
  // молча оставило бы один из журналов без записи, и `writeSyncChanges` за это ругается.
  for (const node of buildInitialChanges(T0)) {
    if (node.kind === 'question') assert.ok(node.lessonId, JSON.stringify(node));
    if (node.kind === 'quest') {
      assert.ok(Object.prototype.hasOwnProperty.call(node, 'ownerUid'), JSON.stringify(node));
    }
  }
}

(async () => {
  await testTheQuestionReachesBothOfItsJournals();
  await testTheCatalogJournalKeepsOneDocumentPerNodeInTheOneForm();
  await testTheIncrementalChangeGoesToBothJournalsToo();
  testEveryNodeNamesTheJournalsItBelongsTo();
  console.log('seed-sync-list-test-data.test.js OK');
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
