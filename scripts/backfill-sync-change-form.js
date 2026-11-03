'use strict';

/**
 * Приводит уже записанные журналы изменений к одной форме (AD-11).
 *
 * Сиды годами писали `catalogs/{c}/sync_changes/{changedAtMs}-{type}-{id}` и такой же append-лог
 * под `lesson_content/{lessonId}/sync_changes`: каждый прогон добавлял документы, и один узел лежал
 * в журнале столько раз, сколько раз запускали сид. Приватный журнал нёс в теле два лишних поля,
 * журнал ревью — тело без `type`. Читатель, который терпит все эти формы, не может снять защитный
 * fallback никогда; отсюда этот скрипт: после него на каждый узел остаётся ровно один документ под
 * каноничным id с телом ровно из трёх полей `{type, id, changedAtMs}`, а лишние удалены.
 *
 * Скрипт читает ТОЛЬКО журналы и пишет ТОЛЬКО в них. Содержательные коллекции — квесты, вопросы,
 * каталоги как узлы, профили — он не открывает вовсе (AD-18, NFR12): журнал производный, и
 * приведение его к инвариантам вправе удалять лишние документы, содержимого это не касается.
 *
 * Что именно делать с коллекцией, решает чистая `planJournalBackfill` из `functions/sync-changes.js`
 * — та же, которой покрыт тест. Здесь — только обход и применение.
 *
 * ПОРЯДОК ВЫКАТА (AD-13). Журнал ревью приводится ПОСЛЕ деплоя функций, а не до.
 *
 * Прежняя версия callable `fetchReviewAssignmentChanges` требовала `lessonId` в теле записи журнала
 * ревью и запись без него молча отбрасывала; урок задания она же брала оттуда. Приведение стирает
 * это поле — тело становится тремя полями, — поэтому прогон раньше деплоя ослепляет рецензентов
 * целиком: заданий не приходит ни одного, и симптом выглядит как «изменений нет», без ошибки.
 * Ошибиться порядком нельзя молча: журнал ревью пропускается, пока прогон явно не подтвердит, что
 * функции уже развёрнуты.
 *
 * Использование:
 *   DRY_RUN=1 node scripts/backfill-sync-change-form.js   # только план, ничего не пишет
 *   node scripts/backfill-sync-change-form.js             # три журнала, ревью пропущен
 *   REVIEW_JOURNAL_READY=1 node scripts/...               # и журнал ревью — после деплоя функций
 *   FIREBASE_SERVICE_ACCOUNT=/path/to/sa.json ...         # иной ключ
 */

const {
  JOURNAL_REVIEW,
  SYNC_CHANGES_COLLECTION,
  journalOfCollectionPath,
  planJournalBackfill,
} = require('../functions/sync-changes');

/** Предел операций в одном батче Firestore — 500; запас на всякий случай. */
const BATCH_LIMIT = 450;

/**
 * Все документы всех четырёх журналов, сгруппированные по коллекции: путь коллекции -> записи.
 *
 * Одна `collectionGroup`-выборка по имени `sync_changes` покрывает четыре журнала разом; коллекция
 * с тем же именем, но не из четырёх известных, откладывается в `skipped` и не трогается.
 */
async function readJournals(db) {
  const snapshot = await db.collectionGroup(SYNC_CHANGES_COLLECTION).get();
  const journals = new Map();
  const skipped = new Set();
  for (const doc of snapshot.docs) {
    const collectionPath = doc.ref.parent.path;
    const journal = journalOfCollectionPath(collectionPath);
    if (!journal) {
      skipped.add(collectionPath);
      continue;
    }
    const entry = journals.get(collectionPath) || {journal, entries: []};
    entry.entries.push({id: doc.id, data: doc.data() || {}});
    journals.set(collectionPath, entry);
  }
  return {journals, skipped: [...skipped].sort()};
}

/**
 * План на все журналы: путь коллекции -> `{journal, writes, deletes, unreadable}`.
 *
 * Журнал ревью в план не попадает, пока прогон не подтвердил, что функции уже развёрнуты: до
 * деплоя приведение его тела ослепляет рецензентов, и ошибка эта беззвучная (AD-13).
 */
function planAll(journals, {reviewJournalReady = false} = {}) {
  const plans = new Map();
  const deferred = [];
  for (const [collectionPath, {journal, entries}] of journals) {
    if (journal === JOURNAL_REVIEW && !reviewJournalReady) {
      deferred.push(collectionPath);
      continue;
    }
    plans.set(collectionPath, {journal, ...planJournalBackfill(journal, entries)});
  }
  return {plans, deferred};
}

/** Применяет планы батчами: запись победителя и удаление лишних — рядом, в одном батче. */
async function applyPlans(db, plans) {
  let batch = db.batch();
  let pending = 0;
  let writes = 0;
  let deletes = 0;
  const flush = async () => {
    if (pending === 0) return;
    await batch.commit();
    batch = db.batch();
    pending = 0;
  };
  for (const [collectionPath, plan] of plans) {
    for (const write of plan.writes) {
      batch.set(db.collection(collectionPath).doc(write.id), write.data);
      writes += 1;
      pending += 1;
      if (pending >= BATCH_LIMIT) await flush();
    }
    for (const id of plan.deletes) {
      batch.delete(db.collection(collectionPath).doc(id));
      deletes += 1;
      pending += 1;
      if (pending >= BATCH_LIMIT) await flush();
    }
  }
  await flush();
  return {writes, deletes};
}

/** Сводка плана в строках лога — по коллекции и итого. */
function describePlans(plans, skipped, deferred = []) {
  const lines = [];
  let writes = 0;
  let deletes = 0;
  let unreadable = 0;
  for (const [collectionPath, plan] of plans) {
    if (plan.writes.length === 0 && plan.deletes.length === 0 && plan.unreadable.length === 0) continue;
    lines.push(
      `${collectionPath} [${plan.journal}]: write ${plan.writes.length}, delete ${plan.deletes.length}` +
        (plan.unreadable.length > 0 ? `, unreadable ${plan.unreadable.length} (${plan.unreadable.join(', ')})` : ''),
    );
    writes += plan.writes.length;
    deletes += plan.deletes.length;
    unreadable += plan.unreadable.length;
  }
  for (const collectionPath of skipped) {
    lines.push(`${collectionPath}: not one of the four journals, skipped`);
  }
  for (const collectionPath of deferred) {
    lines.push(
      `${collectionPath}: review journal deferred — deploy functions first, then rerun with REVIEW_JOURNAL_READY=1`,
    );
  }
  lines.push(`total: write ${writes}, delete ${deletes}, unreadable ${unreadable}, collections ${plans.size}`);
  return lines;
}

/**
 * Полный прогон над данным Firestore: прочитать, спланировать, применить (если не dry-run).
 * Возвращает план и итоги, чтобы тест мог проверить всё это на фейке без сети.
 */
async function backfillSyncChangeForm(db, {dryRun = false, reviewJournalReady = false, log = () => {}} = {}) {
  const {journals, skipped} = await readJournals(db);
  const {plans, deferred} = planAll(journals, {reviewJournalReady});
  for (const line of describePlans(plans, skipped, deferred)) log(line);
  const applied = dryRun ? {writes: 0, deletes: 0} : await applyPlans(db, plans);
  log(dryRun ? 'DRY_RUN: nothing written' : `applied: write ${applied.writes}, delete ${applied.deletes}`);
  return {plans, skipped, deferred, applied};
}

module.exports = {BATCH_LIMIT, backfillSyncChangeForm, describePlans, planAll, readJournals, applyPlans};

if (require.main === module) {
  const fs = require('fs');
  const admin = require('firebase-admin');
  const SERVICE_ACCOUNT_PATH =
    process.env.FIREBASE_SERVICE_ACCOUNT ||
    '/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json';
  if (!fs.existsSync(SERVICE_ACCOUNT_PATH)) {
    console.error(`Service account file not found: ${SERVICE_ACCOUNT_PATH}`);
    process.exit(1);
  }
  admin.initializeApp({credential: admin.credential.cert(require(SERVICE_ACCOUNT_PATH))});
  backfillSyncChangeForm(admin.firestore(), {
    dryRun: process.env.DRY_RUN === '1',
    reviewJournalReady: process.env.REVIEW_JOURNAL_READY === '1',
    log: console.log,
  })
    .catch((error) => {
      console.error(error);
      process.exitCode = 1;
    })
    .finally(async () => {
      await admin.app().delete();
      process.exit(process.exitCode ?? 0);
    });
}
