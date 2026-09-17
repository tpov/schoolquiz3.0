'use strict';

// ---------------------------------------------------------------------------
// Retire a quest via the normal SYNC mechanism (NOT a hard delete).
//
// The client sync layer already removes content on its own:
//   * QuestRepositoryImpl.applyDto: a quest whose `visibleOn` is EMPTY is
//     deleted locally on sync (with version gating).
//   * Section/Theme/Lesson/Question repos: a child whose `archived == true`
//     (and version is newer) is deleted locally on sync.
//
// So to "overwrite/remove" the old course we just UPDATE it the same way the
// seeder writes changes — set the quest's visibleOn to [], bump version, emit a
// `quest` sync_change under catalogs/<catalog>/sync_changes, and bump the
// catalog. Clients then drop the quest on next sync. v2 is seeded separately
// (different id) and appears as usual.
//
// For a quest that has an author — anything published through the arena — use the
// `retirePublicQuest` callable instead: it also mirrors the assigned version into
// the author's private document, which this script cannot see. This one is for
// seeded courses, which have no private half.
//
// Default scope = quest-level only (1 quest doc). The old children become
// unreachable once the quest is gone (harmless orphans). Pass --cascade to also
// mark the whole subtree archived:true so clients/prod drop every child too.
//
// DRY-RUN by default; pass --commit (or RETIRE_COMMIT=1) to write.
//
// Usage:
//   node scripts/seed-bulk/retire-quest.js qb-courses-english-tech-c2                     # dry run
//   node scripts/seed-bulk/retire-quest.js qb-courses-english-tech-c2 --commit            # retire (quest-level)
//   node scripts/seed-bulk/retire-quest.js qb-courses-english-tech-c2 --commit --cascade  # + archive all children
// ---------------------------------------------------------------------------

const { ensureInit, writeSyncChangesWith, BatchWriter, admin } = require('./shared');

ensureInit();
const db = admin.firestore();
const FieldValue = admin.firestore.FieldValue;
const {VERSION_STEP} = require('../../functions/entity-version.js');

const now = admin.firestore.Timestamp.now();
const nowMs = now.toMillis();
// Версию здесь назначает скрипт, и это не нарушение AD-12: запрет адресован клиенту, а скрипт
// пишет документы напрямую Admin SDK — callable в этом пути нет, и сервер назначить эту версию не
// может физически. Регламент при этом тот же, что на сервере: шаг из общего модуля, значение из
// атомарного инкремента. Отметка времени, стоявшая здесь раньше, версией не была — она лишь
// случайно росла, и после первой же серверной перекладки по полкам ряд пошёл бы от неё.
const VERSION = FieldValue.increment(VERSION_STEP);
const CATALOG_ID = 'courses';

const questId = process.argv[2];
const commit = process.argv.includes('--commit') || process.env.RETIRE_COMMIT === '1';
const cascade = process.argv.includes('--cascade');

if (!questId || questId.startsWith('--')) {
  console.error('usage: node scripts/seed-bulk/retire-quest.js <questId> [--commit] [--cascade]');
  process.exit(1);
}

let changeSeq = nowMs;
const nextMs = () => changeSeq++;
let writer = null;
// Журнал — той же функцией и по тому же объявлению, что и сервер (AD-11): документ на узел, id без
// времени. Архивация и снятие с полок — изменения узла наравне с любыми другими, и запись о них
// обязательна (AD-26): без неё клиент ничего не удалит.
const journal = (node) => writeSyncChangesWith(writer, db, node, nextMs());

(async () => {
  console.log(`Target quest: ${questId}  catalog: ${CATALOG_ID}`);
  console.log(commit ? 'MODE: COMMIT' : 'MODE: DRY-RUN (no writes)');
  console.log(cascade ? 'SCOPE: quest + cascade-archive children' : 'SCOPE: quest-level only');

  const questSnap = await db.collection('quests').doc(questId).get();
  if (!questSnap.exists) {
    console.log('Quest not found — nothing to do.');
    return;
  }
  console.log('current visibleOn:', JSON.stringify(questSnap.get('visibleOn')));

  writer = new BatchWriter(db);
  let nSec = 0;
  let nTheme = 0;
  let nLesson = 0;
  let nQ = 0;

  if (cascade) {
    const sections = await db.collection('sections').where('questId', '==', questId).get();
    for (const sec of sections.docs) {
      nSec += 1;
      const themes = await db.collection('themes').where('sectionId', '==', sec.id).get();
      for (const th of themes.docs) {
        nTheme += 1;
        const lessons = await db.collection('lessons').where('themeId', '==', th.id).get();
        for (const ls of lessons.docs) {
          nLesson += 1;
          const questions = await db.collection('questions').where('lessonId', '==', ls.id).get();
          for (const q of questions.docs) {
            nQ += 1;
            if (commit) {
              await writer.set(q.ref, { archived: true, version: VERSION, lastModifiedAt: now }, { merge: true });
              await journal({ kind: 'question', id: q.id, catalogId: CATALOG_ID, lessonId: ls.id });
            }
          }
          if (commit) {
            await writer.set(ls.ref, { archived: true, version: VERSION, lastModifiedAt: now }, { merge: true });
            await journal({ kind: 'lesson', id: ls.id, catalogId: CATALOG_ID });
          }
        }
        if (commit) {
          await writer.set(th.ref, { archived: true, version: VERSION, lastModifiedAt: now }, { merge: true });
          await journal({ kind: 'theme', id: th.id, catalogId: CATALOG_ID });
        }
      }
      if (commit) {
        await writer.set(sec.ref, { archived: true, version: VERSION, lastModifiedAt: now }, { merge: true });
        await journal({ kind: 'section', id: sec.id, catalogId: CATALOG_ID });
      }
    }
    console.log(`children found: sections=${nSec} themes=${nTheme} lessons=${nLesson} questions=${nQ}`);
  }

  // Quest-level retire: empty visibleOn => client deletes the quest on sync.
  if (commit) {
    await writer.set(
      db.collection('quests').doc(questId),
      { visibleOn: [], version: VERSION, lastModifiedAt: now },
      { merge: true },
    );
    // Сидовый квест: приватной половины нет (для квеста с автором есть callable retirePublicQuest).
    await journal({ kind: 'quest', id: questId, catalogId: CATALOG_ID, ownerUid: null });
    await writer.set(
      db.doc(`catalogs/${CATALOG_ID}`),
      { version: VERSION, lastModifiedAt: now },
      { merge: true },
    );
    const ops = await writer.commit();
    console.log(`COMMITTED: ${questId} retired (visibleOn=[]). cascade children archived=${cascade ? nQ + nLesson + nTheme + nSec : 0}. ops=${ops}`);
  } else {
    console.log(
      `DRY-RUN: would set quests/${questId}.visibleOn=[], emit quest sync_change, bump catalog version` +
        (cascade ? `; plus archive ~${nQ + nLesson + nTheme + nSec} child docs (sections=${nSec} themes=${nTheme} lessons=${nLesson} questions=${nQ}).` : '.'),
    );
    console.log('Re-run with --commit to apply.');
  }
})()
  .catch((e) => {
    console.error(e);
    process.exitCode = 1;
  })
  .finally(() => admin.app().delete());
