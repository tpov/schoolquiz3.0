"use strict";

const assert = require("assert");
const {
  ADMIN_REVIEW_SYNC_CHANGES,
  JOURNALS,
  JOURNAL_CATALOG,
  JOURNAL_LESSON_CONTENT,
  JOURNAL_PRIVATE,
  JOURNAL_REVIEW,
  NODE_JOURNALS,
  NODE_KINDS,
  SYNC_CHANGE_FIELDS,
  adminSyncChangePath,
  isSyncChangeDocument,
  journalOfCollectionPath,
  lessonContentSyncChangePath,
  planJournalBackfill,
  privateSyncChangePath,
  publicCatalogSyncChangePath,
  syncChangeDocument,
  syncChangeWrites,
  writeSyncChanges,
} = require("./sync-changes");

const NOW = 1_756_800_000_000;

// ─── фейки: Firestore в памяти, батч «всё или ничего» ─────────────────────────────────────────

/**
 * Хранилище путь -> данные и батч, который применяет всё разом или ничего: настоящий батч
 * Firestore атомарен, и здесь важно ровно это свойство. `refuse` — путь, запись в который
 * «падает», как упавшая бы запись в один из журналов.
 */
function fakeFirestore(initial = {}) {
  const store = new Map(Object.entries(initial));
  const db = {
    doc(path) {
      return {path};
    },
    batch(refuse = null) {
      const staged = [];
      return {
        set(ref, data, options) {
          staged.push({path: ref.path, data, options});
        },
        async commit() {
          if (refuse && staged.some((write) => write.path === refuse)) {
            throw new Error(`refused: ${refuse}`);
          }
          for (const write of staged) {
            const previous = write.options && write.options.merge ? store.get(write.path) || {} : {};
            store.set(write.path, {...previous, ...write.data});
          }
        },
      };
    },
    read: (path) => store.get(path),
    paths: () => [...store.keys()].sort(),
  };
  return db;
}

/** Полный набор адресных частей: всё, что любому узлу могло бы понадобиться. */
function everyAddress(kind, id) {
  return {kind, id, catalogId: "cat-1", lessonId: "lesson-1", ownerUid: "author-1"};
}

function journalOfPath(path) {
  return journalOfCollectionPath(path.slice(0, path.lastIndexOf("/")));
}

// ─── 7.1: тело и id ───────────────────────────────────────────────────────────────────────────

function testTheBodyIsExactlyThreeFieldsInEveryJournal() {
  // По одному свежезаписанному документу на каждый из четырёх путей.
  const db = fakeFirestore();
  const batch = db.batch();
  writeSyncChanges(batch, db, {kind: "quest", id: "q-1", catalogId: "cat-1", ownerUid: "author-1"}, NOW);
  writeSyncChanges(batch, db, {kind: "question", id: "qn-1", catalogId: "cat-1", lessonId: "lesson-1"}, NOW);
  writeSyncChanges(batch, db, {kind: "review_assignment", id: "sub-1_lesson-1"}, NOW);
  return batch.commit().then(() => {
    const paths = db.paths();
    const journalsSeen = new Set(paths.map(journalOfPath));
    assert.deepStrictEqual([...journalsSeen].sort(), [...JOURNALS].sort(), "все четыре журнала получили запись");
    for (const path of paths) {
      const body = db.read(path);
      assert.deepStrictEqual(
        Object.keys(body).sort(),
        [...SYNC_CHANGE_FIELDS].sort(),
        `${path}: тело — ровно type, id, changedAtMs`,
      );
      assert.strictEqual(typeof body.type, "string");
      assert.strictEqual(typeof body.id, "string");
      assert.strictEqual(body.changedAtMs, NOW);
    }
    // `id` — ключ для fetchByIds, а не id документа журнала.
    assert.strictEqual(db.read(privateSyncChangePath("author-1", "cat-1", "q-1")).id, "q-1");
    assert.strictEqual(db.read(publicCatalogSyncChangePath("cat-1", "quest", "q-1")).id, "q-1");
    assert.strictEqual(db.read(adminSyncChangePath("sub-1_lesson-1")).id, "sub-1_lesson-1");
    // Журнал ревью раньше писал тело без `type` — теперь оно есть.
    assert.strictEqual(db.read(adminSyncChangePath("sub-1_lesson-1")).type, "review_assignment");
  });
}

function testAMissingFieldOrAnExtraOneIsRefused() {
  assert.deepStrictEqual(syncChangeDocument("quest", "q-1", NOW), {type: "quest", id: "q-1", changedAtMs: NOW});
  assert.throws(() => syncChangeDocument(undefined, "q-1", NOW), TypeError);
  assert.throws(() => syncChangeDocument("quest", "", NOW), TypeError);
  assert.throws(() => syncChangeDocument("quest", "q-1", undefined), TypeError);
  assert.throws(() => syncChangeDocument("quest", "q-1", "1756800000000"), TypeError, "время — число, не строка");
  assert.throws(() => syncChangeDocument("quest", "q-1", 0), TypeError);
  assert.throws(() => syncChangeDocument("quest", "q-1", 1.5), TypeError);
  // Проверка формы, которой пользуется бэкфилл: четвёртое поле или пропуск любого из трёх — не форма.
  assert.ok(isSyncChangeDocument({type: "quest", id: "q-1", changedAtMs: NOW}));
  assert.ok(!isSyncChangeDocument({type: "quest", id: "q-1", changedAtMs: NOW, lessonId: "l"}), "четвёртое поле");
  assert.ok(!isSyncChangeDocument({id: "q-1", changedAtMs: NOW}), "без type — как писал журнал ревью");
  assert.ok(!isSyncChangeDocument({type: "quest", changedAtMs: NOW}));
  assert.ok(!isSyncChangeDocument({type: "quest", id: "q-1"}));
}

function testDocumentIdsCarryNoTime() {
  // Дословно те строки, что пишет сервер и по которым клиент читает.
  assert.strictEqual(publicCatalogSyncChangePath("cat-1", "quest", "q-1"), "catalogs/cat-1/sync_changes/quest_q-1");
  assert.strictEqual(lessonContentSyncChangePath("l-1", "qn-1"), "lesson_content/l-1/sync_changes/qn-1");
  assert.strictEqual(privateSyncChangePath("u-1", "cat-1", "q-1"), "private/u-1/sync_changes/cat-1_q-1");
  assert.strictEqual(adminSyncChangePath("sub-1_l-1"), "admin/review/sync_changes/sub-1_l-1");
  for (const kind of NODE_KINDS) {
    for (const write of syncChangeWrites(everyAddress(kind, "node-1"), NOW)) {
      const docId = write.path.slice(write.path.lastIndexOf("/") + 1);
      assert.ok(!/\d{10,}/.test(docId), `${write.path}: во времени в id и был весь дефект`);
      assert.ok(!docId.includes("changedAtMs"), write.path);
      assert.ok(!docId.includes(String(NOW)), write.path);
    }
  }
}

function testARerunOverwritesTheSameDocumentInsteadOfAppending() {
  // CAP-8: повторный прогон сида не растит коллекцию. Один узел — один документ, сколько раз ни пиши.
  const db = fakeFirestore();
  const node = {kind: "question", id: "qn-1", catalogId: "cat-1", lessonId: "lesson-1"};
  const first = db.batch();
  writeSyncChanges(first, db, node, NOW);
  const second = db.batch();
  writeSyncChanges(second, db, node, NOW + 5_000);
  return first.commit().then(() => second.commit()).then(() => {
    assert.strictEqual(db.paths().length, 2, "два журнала — два документа, а не четыре");
    assert.strictEqual(db.read(lessonContentSyncChangePath("lesson-1", "qn-1")).changedAtMs, NOW + 5_000);
    assert.strictEqual(db.read(publicCatalogSyncChangePath("cat-1", "question", "qn-1")).changedAtMs, NOW + 5_000);
  });
}

// ─── 7.2: принадлежность узла журналам ────────────────────────────────────────────────────────

function testEveryDeclaredKindWritesExactlyItsJournalsAndNothingElse() {
  // Перебор всех видов узла из объявления: с полным набором адресов (то есть при любом месте
  // вызова) записи уходят ровно в объявленные журналы.
  for (const kind of NODE_KINDS) {
    const writes = syncChangeWrites(everyAddress(kind, `${kind}-1`), NOW);
    const journals = writes.map((write) => write.journal).sort();
    assert.deepStrictEqual(journals, [...NODE_JOURNALS[kind]].sort(), `${kind}: состав журналов`);
    // Путь каждой записи действительно лежит в том журнале, которым назван.
    for (const write of writes) {
      assert.strictEqual(journalOfPath(write.path), write.journal, `${write.path} принадлежит ${write.journal}`);
      assert.ok(isSyncChangeDocument(write.data), write.path);
    }
    assert.strictEqual(new Set(writes.map((write) => write.path)).size, writes.length, `${kind}: путь на журнал один`);
  }
  // Объявление закрыто: не объявленный вид не пишется никуда, а не «в каталог по умолчанию».
  assert.throws(() => syncChangeWrites(everyAddress("comment", "c-1"), NOW), /unknown node kind/);
  assert.throws(() => syncChangeWrites(everyAddress("constructor", "c-1"), NOW), /unknown node kind/);
}

function testAQuestIsReadFromTwoJournalsAndWrittenToBoth() {
  // AC: квест читается и из private/{uid}, и из catalogs/{catalogId} — запись в оба, с одним временем.
  const writes = syncChangeWrites({kind: "quest", id: "q-1", catalogId: "cat-1", ownerUid: "author-1"}, NOW);
  assert.deepStrictEqual(
    writes.map((write) => write.path).sort(),
    [publicCatalogSyncChangePath("cat-1", "quest", "q-1"), privateSyncChangePath("author-1", "cat-1", "q-1")].sort(),
  );
  assert.ok(writes.every((write) => write.data.changedAtMs === NOW));
  assert.ok(writes.every((write) => write.data.type === "quest" && write.data.id === "q-1"));
}

function testAQuestWithoutAPrivateHalfSaysSoAndSkipsThePrivateJournal() {
  // Сиды пишут публичное дерево напрямую; приватной половины у такого квеста нет — и это говорится.
  const writes = syncChangeWrites({kind: "quest", id: "q-1", catalogId: "cat-1", ownerUid: null}, NOW);
  assert.deepStrictEqual(writes.map((write) => write.journal), [JOURNAL_CATALOG]);
  // Забытое поле — не «нет автора», а ошибка: иначе автор молча остался бы без записи.
  assert.throws(() => syncChangeWrites({kind: "quest", id: "q-1", catalogId: "cat-1"}, NOW), /ownerUid/);
  assert.throws(() => syncChangeWrites({kind: "quest", id: "q-1", catalogId: "cat-1", ownerUid: ""}, NOW), /ownerUid/);
}

function testThePrivateCopyAloneStaysOutOfThePublicJournal() {
  // Заявка на арену и рейтинг приватного квеста трогают только приватный документ: публичному
  // читателю сообщать не о чем, а в теле приватный журнал знает один тип — quest.
  const writes = syncChangeWrites({kind: "private_quest", id: "q-1", catalogId: "cat-1", ownerUid: "author-1"}, NOW);
  assert.deepStrictEqual(writes.map((write) => write.journal), [JOURNAL_PRIVATE]);
  assert.deepStrictEqual(writes[0].data, {type: "quest", id: "q-1", changedAtMs: NOW});
  assert.strictEqual(writes[0].path, privateSyncChangePath("author-1", "cat-1", "q-1"));
}

function testAMissingAddressPartIsAnErrorNotASilentSkip() {
  assert.throws(() => syncChangeWrites({kind: "question", id: "qn-1", catalogId: "cat-1"}, NOW), /lessonId/);
  assert.throws(() => syncChangeWrites({kind: "question", id: "qn-1", lessonId: "l-1"}, NOW), /catalogId/);
  assert.throws(() => syncChangeWrites({kind: "section", id: "s-1"}, NOW), /catalogId/);
  assert.throws(() => syncChangeWrites({kind: "private_quest", id: "q-1", catalogId: "cat-1"}, NOW), /ownerUid/);
  assert.throws(() => syncChangeWrites({kind: "quest", id: "q-1", ownerUid: "author-1"}, NOW), /catalogId/);
}

function testTheNodeAndItsJournalsLandTogetherOrNotAtAll() {
  // AC: отказ записи в один из двух журналов — узел остался прежним, и во втором журнале записи
  // нет. Узел и его записи идут одним батчем; батч атомарен.
  const questPath = "quests/q-1";
  const db = fakeFirestore({[questPath]: {visibleOn: ["home"], version: 3}});
  const batch = db.batch(privateSyncChangePath("author-1", "cat-1", "q-1"));
  batch.set(db.doc(questPath), {visibleOn: ["archive"], version: 4}, {merge: true});
  const writes = writeSyncChanges(batch, db, {kind: "quest", id: "q-1", catalogId: "cat-1", ownerUid: "author-1"}, NOW);
  assert.strictEqual(writes.length, 2);
  return batch.commit().then(
    () => assert.fail("коммит обязан упасть"),
    () => {
      assert.deepStrictEqual(db.read(questPath), {visibleOn: ["home"], version: 3}, "узел в прежнем виде");
      assert.strictEqual(db.read(publicCatalogSyncChangePath("cat-1", "quest", "q-1")), undefined, "и второй журнал пуст");
      assert.deepStrictEqual(db.paths(), [questPath]);
    },
  );
}

function testWriteSyncChangesNeedsAWriterAndADb() {
  assert.throws(() => writeSyncChanges(null, fakeFirestore(), everyAddress("lesson", "l-1"), NOW), TypeError);
  assert.throws(() => writeSyncChanges(fakeFirestore().batch(), {}, everyAddress("lesson", "l-1"), NOW), TypeError);
}

// ─── бэкфилл: приведение уже записанного ──────────────────────────────────────────────────────

function testLegacyTimedEntriesFoldIntoOneDocumentPerNode() {
  // Три прогона сида оставили три документа на один узел; остаётся один — с наибольшим временем.
  const plan = planJournalBackfill(JOURNAL_CATALOG, [
    {id: `${NOW}-quest-q-1`, data: {type: "quest", id: "q-1", changedAtMs: NOW}},
    {id: `${NOW + 20}-quest-q-1`, data: {type: "quest", id: "q-1", changedAtMs: NOW + 20}},
    {id: `${NOW + 10}-quest-q-1`, data: {type: "quest", id: "q-1", changedAtMs: NOW + 10}},
    {id: `${NOW + 1}-lesson-l-1`, data: {type: "lesson", id: "l-1", changedAtMs: NOW + 1}},
  ]);
  assert.deepStrictEqual(plan.writes, [
    {id: "lesson_l-1", data: {type: "lesson", id: "l-1", changedAtMs: NOW + 1}},
    {id: "quest_q-1", data: {type: "quest", id: "q-1", changedAtMs: NOW + 20}},
  ]);
  assert.deepStrictEqual(plan.deletes, [
    `${NOW}-quest-q-1`, `${NOW + 1}-lesson-l-1`, `${NOW + 10}-quest-q-1`, `${NOW + 20}-quest-q-1`,
  ].sort());
  assert.deepStrictEqual(plan.unreadable, []);
}

function testALegacyEntryLosesToANewerDocumentAlreadyInForm() {
  // Сервер уже писал `quest_q-1` позже сида: сидовский документ удаляется, серверный не трогается.
  const plan = planJournalBackfill(JOURNAL_CATALOG, [
    {id: `${NOW}-quest-q-1`, data: {type: "quest", id: "q-1", changedAtMs: NOW}},
    {id: "quest_q-1", data: {type: "quest", id: "q-1", changedAtMs: NOW + 500}},
  ]);
  assert.deepStrictEqual(plan.writes, [], "документ в форме и самый поздний — писать нечего");
  assert.deepStrictEqual(plan.deletes, [`${NOW}-quest-q-1`]);
}

function testTheLessonJournalKeepsOnlyTheQuestionId() {
  const plan = planJournalBackfill(JOURNAL_LESSON_CONTENT, [
    {id: `${NOW}-question-qn-1`, data: {type: "question", id: "qn-1", changedAtMs: NOW}},
    {id: "qn-2", data: {type: "question", id: "qn-2", changedAtMs: NOW}},
  ]);
  assert.deepStrictEqual(plan.writes, [{id: "qn-1", data: {type: "question", id: "qn-1", changedAtMs: NOW}}]);
  assert.deepStrictEqual(plan.deletes, [`${NOW}-question-qn-1`]);
}

function testExtraFieldsAreStrippedAndAMissingTypeIsRestored() {
  // Приватный журнал нёс catalogId и questId сверх формы; журнал ревью — тело без type.
  const privatePlan = planJournalBackfill(JOURNAL_PRIVATE, [
    {id: "cat-1_q-1", data: {id: "q-1", type: "quest", catalogId: "cat-1", questId: "q-1", changedAtMs: NOW}},
  ]);
  assert.deepStrictEqual(privatePlan.writes, [{id: "cat-1_q-1", data: {type: "quest", id: "q-1", changedAtMs: NOW}}]);
  assert.deepStrictEqual(privatePlan.deletes, []);

  const reviewPlan = planJournalBackfill(JOURNAL_REVIEW, [
    {id: "sub-1_l-1", data: {id: "sub-1_l-1", assignmentId: "sub-1_l-1", lessonId: "l-1", changedAtMs: NOW}},
  ]);
  assert.deepStrictEqual(reviewPlan.writes, [
    {id: "sub-1_l-1", data: {type: "review_assignment", id: "sub-1_l-1", changedAtMs: NOW}},
  ]);
  assert.deepStrictEqual(reviewPlan.deletes, []);
}

function testAnUnreadableEntryIsLeftAloneAndNamed() {
  // Без времени документ не сравнить ни с кем: план его не трогает и называет, а не удаляет молча.
  const plan = planJournalBackfill(JOURNAL_CATALOG, [
    {id: "chg-1", data: {questId: "published", version: 3}},
    {id: "quest_q-1", data: {type: "quest", id: "q-1", changedAtMs: NOW}},
  ]);
  assert.deepStrictEqual(plan.writes, []);
  assert.deepStrictEqual(plan.deletes, []);
  assert.deepStrictEqual(plan.unreadable, ["chg-1"]);
}

function testTheBackfillIsIdempotent() {
  const before = [
    {id: `${NOW}-theme-t-1`, data: {type: "theme", id: "t-1", changedAtMs: NOW}},
    {id: `${NOW + 3}-theme-t-1`, data: {type: "theme", id: "t-1", changedAtMs: NOW + 3}},
    {id: "section_s-1", data: {type: "section", id: "s-1", changedAtMs: NOW, extra: true}},
  ];
  const first = planJournalBackfill(JOURNAL_CATALOG, before);
  const after = before
    .filter((entry) => !first.deletes.includes(entry.id))
    .map((entry) => first.writes.find((write) => write.id === entry.id) || entry)
    .concat(first.writes.filter((write) => !before.some((entry) => entry.id === write.id)));
  const second = planJournalBackfill(JOURNAL_CATALOG, after);
  assert.deepStrictEqual(second, {writes: [], deletes: [], unreadable: []});
  assert.strictEqual(after.length, 2, "по документу на узел");
  assert.ok(after.every((entry) => isSyncChangeDocument(entry.data)));
}

function testJournalsAreRecognisedByCollectionPath() {
  assert.strictEqual(journalOfCollectionPath("catalogs/cat-1/sync_changes"), JOURNAL_CATALOG);
  assert.strictEqual(journalOfCollectionPath("lesson_content/l-1/sync_changes"), JOURNAL_LESSON_CONTENT);
  assert.strictEqual(journalOfCollectionPath("private/u-1/sync_changes"), JOURNAL_PRIVATE);
  assert.strictEqual(journalOfCollectionPath(ADMIN_REVIEW_SYNC_CHANGES), JOURNAL_REVIEW);
  // Одноимённая коллекция где-то ещё — не журнал, и бэкфилл её не трогает.
  assert.strictEqual(journalOfCollectionPath("quests/q-1/sync_changes"), null);
  assert.strictEqual(journalOfCollectionPath("catalogs/cat-1/nodes/n-1/sync_changes"), null);
  assert.strictEqual(journalOfCollectionPath("admin/other/sync_changes"), null);
}

(async () => {
  await testTheBodyIsExactlyThreeFieldsInEveryJournal();
  testAMissingFieldOrAnExtraOneIsRefused();
  testDocumentIdsCarryNoTime();
  await testARerunOverwritesTheSameDocumentInsteadOfAppending();
  testEveryDeclaredKindWritesExactlyItsJournalsAndNothingElse();
  testAQuestIsReadFromTwoJournalsAndWrittenToBoth();
  testAQuestWithoutAPrivateHalfSaysSoAndSkipsThePrivateJournal();
  testThePrivateCopyAloneStaysOutOfThePublicJournal();
  testAMissingAddressPartIsAnErrorNotASilentSkip();
  await testTheNodeAndItsJournalsLandTogetherOrNotAtAll();
  testWriteSyncChangesNeedsAWriterAndADb();
  testLegacyTimedEntriesFoldIntoOneDocumentPerNode();
  testALegacyEntryLosesToANewerDocumentAlreadyInForm();
  testTheLessonJournalKeepsOnlyTheQuestionId();
  testExtraFieldsAreStrippedAndAMissingTypeIsRestored();
  testAnUnreadableEntryIsLeftAloneAndNamed();
  testTheBackfillIsIdempotent();
  testJournalsAreRecognisedByCollectionPath();
  console.log("sync-changes.test.js OK");
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
