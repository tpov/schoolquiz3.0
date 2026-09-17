'use strict';

/**
 * Бэкфилл формы журнала на фейке Firestore: старые append-лог записи сворачиваются в документ на
 * узел, лишние удаляются, содержательные коллекции не читаются и не пишутся вовсе.
 */

const assert = require('assert');
const {backfillSyncChangeForm} = require('./backfill-sync-change-form');
const {isSyncChangeDocument} = require('../functions/sync-changes');

const T0 = 1_756_800_000_000;

/**
 * Firestore в памяти: `collectionGroup(name).get()`, `collection(path).doc(id)`, `batch()` с
 * `set`/`delete`/`commit`. Каждое чтение и каждая запись пишутся в `touched`, чтобы тест мог
 * утверждать, что содержательные коллекции скрипт не открывал.
 */
function fakeFirestore(initial) {
  const store = new Map(Object.entries(initial));
  const touched = {reads: [], writes: [], deletes: []};
  const parentOf = (path) => path.slice(0, path.lastIndexOf('/'));
  const idOf = (path) => path.slice(path.lastIndexOf('/') + 1);
  return {
    collectionGroup(name) {
      return {
        async get() {
          const docs = [...store.entries()]
            .filter(([path]) => idOf(parentOf(path)) === name)
            .map(([path, data]) => {
              touched.reads.push(path);
              return {id: idOf(path), ref: {path, parent: {path: parentOf(path)}}, data: () => ({...data})};
            });
          return {docs};
        },
      };
    },
    collection(path) {
      return {doc: (id) => ({path: `${path}/${id}`})};
    },
    batch() {
      const staged = [];
      return {
        set(ref, data) {
          staged.push({op: 'set', path: ref.path, data});
        },
        delete(ref) {
          staged.push({op: 'delete', path: ref.path});
        },
        async commit() {
          for (const write of staged) {
            if (write.op === 'set') {
              store.set(write.path, {...write.data});
              touched.writes.push(write.path);
            } else {
              store.delete(write.path);
              touched.deletes.push(write.path);
            }
          }
        },
      };
    },
    touched,
    snapshot: () => Object.fromEntries([...store.entries()].sort(([a], [b]) => (a < b ? -1 : 1))),
    ids: (collectionPath) => [...store.keys()].filter((path) => parentOf(path) === collectionPath).map(idOf).sort(),
  };
}

const CONTENT = {
  'catalogs/courses': {title: 'Courses', version: 4},
  'quests/q-1': {title: 'Kotlin', version: 7, visibleOn: ['archive']},
  'questions/qn-1': {text: '?', payload: '{}', version: 2},
  'profiles/u-1': {nickname: 'a'},
  'private/u-1/catalogs/courses/quests/q-1': {version: 7},
  'admin/review/lessons/l-1': {assignmentId: 'sub-1_l-1', submissionId: 'sub-1'},
};

function seededDb() {
  return fakeFirestore({
    ...CONTENT,
    // Три прогона сида на один квест и один урок.
    [`catalogs/courses/sync_changes/${T0}-quest-q-1`]: {type: 'quest', id: 'q-1', changedAtMs: T0},
    [`catalogs/courses/sync_changes/${T0 + 100}-quest-q-1`]: {type: 'quest', id: 'q-1', changedAtMs: T0 + 100},
    [`catalogs/courses/sync_changes/${T0 + 200}-quest-q-1`]: {type: 'quest', id: 'q-1', changedAtMs: T0 + 200},
    [`catalogs/courses/sync_changes/${T0 + 1}-lesson-l-1`]: {type: 'lesson', id: 'l-1', changedAtMs: T0 + 1},
    // Уже в форме и позже сида: остаётся как есть.
    'catalogs/courses/sync_changes/section_s-1': {type: 'section', id: 's-1', changedAtMs: T0 + 500},
    // Журнал урока в старой форме.
    [`lesson_content/l-1/sync_changes/${T0 + 2}-question-qn-1`]: {type: 'question', id: 'qn-1', changedAtMs: T0 + 2},
    // Приватный журнал: id верный, тело с двумя лишними полями.
    'private/u-1/sync_changes/courses_q-1': {id: 'q-1', type: 'quest', catalogId: 'courses', questId: 'q-1', changedAtMs: T0 + 3},
    // Журнал ревью: тело без type.
    'admin/review/sync_changes/sub-1_l-1': {id: 'sub-1_l-1', assignmentId: 'sub-1_l-1', lessonId: 'l-1', changedAtMs: T0 + 4},
    // Нечитаемая запись из теста правил: без времени — не трогать и назвать.
    'catalogs/cat-1/sync_changes/chg-1': {questId: 'published', version: 3},
    // Одноимённая коллекция не из четырёх журналов — не трогать.
    'quests/q-1/sync_changes/x': {type: 'quest', id: 'q-1', changedAtMs: T0},
  });
}

async function testOneDocumentPerNodeWithTheLatestTimeSurvives() {
  const db = seededDb();
  const log = [];
  await backfillSyncChangeForm(db, {reviewJournalReady: true, log: (line) => log.push(line)});

  assert.deepStrictEqual(db.ids('catalogs/courses/sync_changes'), ['lesson_l-1', 'quest_q-1', 'section_s-1']);
  assert.deepStrictEqual(db.snapshot()['catalogs/courses/sync_changes/quest_q-1'], {type: 'quest', id: 'q-1', changedAtMs: T0 + 200});
  assert.deepStrictEqual(db.snapshot()['catalogs/courses/sync_changes/lesson_l-1'], {type: 'lesson', id: 'l-1', changedAtMs: T0 + 1});
  assert.deepStrictEqual(db.snapshot()['catalogs/courses/sync_changes/section_s-1'], {type: 'section', id: 's-1', changedAtMs: T0 + 500});
  assert.deepStrictEqual(db.ids('lesson_content/l-1/sync_changes'), ['qn-1']);
  assert.deepStrictEqual(db.snapshot()['private/u-1/sync_changes/courses_q-1'], {type: 'quest', id: 'q-1', changedAtMs: T0 + 3});
  assert.deepStrictEqual(db.snapshot()['admin/review/sync_changes/sub-1_l-1'], {type: 'review_assignment', id: 'sub-1_l-1', changedAtMs: T0 + 4});
  // Нечитаемое и чужое — на месте.
  assert.deepStrictEqual(db.snapshot()['catalogs/cat-1/sync_changes/chg-1'], {questId: 'published', version: 3});
  assert.deepStrictEqual(db.snapshot()['quests/q-1/sync_changes/x'], {type: 'quest', id: 'q-1', changedAtMs: T0});
  assert.ok(log.some((line) => line.includes('unreadable 1 (chg-1)')), log.join('\n'));
  assert.ok(log.some((line) => line.includes('quests/q-1/sync_changes: not one of the four journals')), log.join('\n'));
  // Всё, что осталось в четырёх журналах, — в форме.
  for (const [path, data] of Object.entries(db.snapshot())) {
    if (!path.includes('/sync_changes/') || path.startsWith('quests/') || path.includes('chg-1')) continue;
    assert.ok(isSyncChangeDocument(data), `${path}: ${JSON.stringify(data)}`);
  }
}

async function testContentCollectionsAreNeitherReadNorWritten() {
  // NFR12, AD-18: содержательные коллекции после прогона побайтово те же — и скрипт их не открывал.
  const db = seededDb();
  await backfillSyncChangeForm(db, {reviewJournalReady: true});
  const after = db.snapshot();
  for (const [path, data] of Object.entries(CONTENT)) {
    assert.deepStrictEqual(after[path], data, path);
  }
  const contentTouch = [...db.touched.reads, ...db.touched.writes, ...db.touched.deletes]
    .filter((path) => !path.includes('/sync_changes/'));
  assert.deepStrictEqual(contentTouch, [], 'ни одного чтения или записи вне журналов');
}

async function testDryRunPlansButWritesNothing() {
  const db = seededDb();
  const before = db.snapshot();
  const result = await backfillSyncChangeForm(db, {dryRun: true, reviewJournalReady: true});
  assert.deepStrictEqual(db.snapshot(), before);
  assert.deepStrictEqual(db.touched.writes, []);
  assert.deepStrictEqual(db.touched.deletes, []);
  assert.strictEqual(result.plans.get('catalogs/courses/sync_changes').deletes.length, 4);
  assert.strictEqual(result.plans.get('catalogs/courses/sync_changes').writes.length, 2);
}

async function testASecondRunFindsNothingToDo() {
  const db = seededDb();
  await backfillSyncChangeForm(db, {reviewJournalReady: true});
  const settled = db.snapshot();
  const second = await backfillSyncChangeForm(db, {reviewJournalReady: true});
  assert.deepStrictEqual(db.snapshot(), settled);
  assert.deepStrictEqual(second.applied, {writes: 0, deletes: 0});
}

async function testTheReviewJournalIsLeftAloneUntilTheDeployIsConfirmed() {
  // AD-13: прежняя версия callable брала урок задания из тела записи журнала ревью и запись без
  // `lessonId` молча отбрасывала. Приведение стирает поле, поэтому прогон раньше деплоя функций
  // ослепляет рецензентов целиком — и без единой ошибки в логе.
  const db = seededDb();
  const before = db.snapshot()['admin/review/sync_changes/sub-1_l-1'];
  const log = [];

  const result = await backfillSyncChangeForm(db, {log: (line) => log.push(line)});

  assert.deepStrictEqual(db.snapshot()['admin/review/sync_changes/sub-1_l-1'], before, 'журнал ревью не тронут');
  assert.deepStrictEqual(result.deferred, ['admin/review/sync_changes']);
  assert.ok(
    log.some((line) => line.includes('review journal deferred') && line.includes('REVIEW_JOURNAL_READY=1')),
    log.join('\n'),
  );
  // Три остальных журнала при этом приведены: пропуск точечный, а не отказ от прогона.
  assert.deepStrictEqual(db.ids('catalogs/courses/sync_changes'), ['lesson_l-1', 'quest_q-1', 'section_s-1']);
}

async function testTheReviewJournalIsConvertedOnceTheDeployIsConfirmed() {
  const db = seededDb();

  await backfillSyncChangeForm(db, {reviewJournalReady: true});

  assert.deepStrictEqual(
    db.snapshot()['admin/review/sync_changes/sub-1_l-1'],
    {type: 'review_assignment', id: 'sub-1_l-1', changedAtMs: T0 + 4},
  );
}

(async () => {
  await testTheReviewJournalIsLeftAloneUntilTheDeployIsConfirmed();
  await testTheReviewJournalIsConvertedOnceTheDeployIsConfirmed();
  await testOneDocumentPerNodeWithTheLatestTimeSurvives();
  await testContentCollectionsAreNeitherReadNorWritten();
  await testDryRunPlansButWritesNothing();
  await testASecondRunFindsNothingToDo();
  console.log('backfill-sync-change-form.test.js OK');
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
