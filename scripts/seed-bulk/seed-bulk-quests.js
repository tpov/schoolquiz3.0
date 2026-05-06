'use strict';

// Bulk seed runner.
// Reads scripts/seed-bulk/data/<catalogId>.js for each target catalog,
// writes the entire quest -> section -> theme -> lesson -> question hierarchy
// into Firestore via the Admin SDK, then bumps catalog contentsVersion and
// emits matching sync_change documents.
//
// Usage: node scripts/seed-bulk/seed-bulk-quests.js
// Optional: NODE_OPTIONS=--max-old-space-size=4096 node ...

const path = require('path');
const {
  ensureInit,
  AUTHOR_UID,
  VISIBLE_ON,
  LANGUAGE,
  mkPayload,
  syncChange,
  BatchWriter,
  admin,
} = require('./shared');

ensureInit();

const db = admin.firestore();
const FieldValue = admin.firestore.FieldValue;
const now = admin.firestore.Timestamp.now();
const baseMs = now.toMillis();
const VERSION = baseMs;
const INC1 = FieldValue.increment(1);

const DEFAULT_TARGET_CATALOGS = ['school', 'courses', 'surveys', 'games', 'quests'];
const COURSES_CATALOG_ID = 'courses';
const COURSES_VISIBLE_ON = ['archive'];

function parseCsvEnv(name, fallback) {
  const raw = process.env[name];
  if (!raw) return fallback;
  const values = raw
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);
  return values.length > 0 ? values : fallback;
}

const TARGET_CATALOGS = parseCsvEnv('SEED_CATALOGS', DEFAULT_TARGET_CATALOGS);
const TARGET_QUEST_IDS = new Set(parseCsvEnv('SEED_QUEST_IDS', []));

function* timestampGenerator(start) {
  let next = start;
  while (true) {
    yield next++;
  }
}

async function seedCatalog(catalogId) {
  const data = require(`./data/${catalogId}`);
  if (data.catalogId !== catalogId) {
    throw new Error(`data/${catalogId}.js exports catalogId=${data.catalogId}, expected ${catalogId}`);
  }
  if (!Array.isArray(data.quests) || data.quests.length === 0) {
    throw new Error(`data/${catalogId}.js has empty quests`);
  }
  const quests =
    TARGET_QUEST_IDS.size === 0
      ? data.quests
      : data.quests.filter((quest) => TARGET_QUEST_IDS.has(quest.id));

  if (quests.length === 0) {
    console.log(`[${catalogId}] skipped: no quests match SEED_QUEST_IDS`);
    return { ops: 0, questCount: 0, sectionCount: 0, themeCount: 0, lessonCount: 0, questionCount: 0 };
  }

  const writer = new BatchWriter(db);
  const ts = timestampGenerator(baseMs);

  let questCount = 0;
  let sectionCount = 0;
  let themeCount = 0;
  let lessonCount = 0;
  let questionCount = 0;

  console.log(`[${catalogId}] start: ${quests.length} quests`);

  for (const quest of quests) {
    const questVisibleOn = visibleOnForQuest(catalogId, quest);
    const questArchived = archivedForQuest(catalogId, quest);
    const questDoc = {
      id: quest.id,
      catalogId,
      authorUid: AUTHOR_UID,
      title: quest.title,
      picturePath: quest.picturePath || null,
      visibleOn: questVisibleOn,
      averageRating: null,
      averageRatingCount: 0,
      version: VERSION,
      contentsVersion: VERSION,
      lastModifiedAt: now,
      archived: questArchived,
    };
    await writer.set(db.doc(`quests/${quest.id}`), questDoc, { merge: true });
    const questChange = syncChange('quest', quest.id, ts.next().value);
    await writer.set(
      db.doc(
        `catalogs/${catalogId}/sync_changes/${questChange.changedAtMs}-${questChange.type}-${questChange.id}`,
      ),
      questChange,
    );
    questCount += 1;

    for (let s = 0; s < quest.sections.length; s++) {
      const section = quest.sections[s];
      const sectionDoc = {
        id: section.id,
        questId: quest.id,
        title: section.title,
        order: s,
        version: VERSION,
        contentsVersion: VERSION,
        lastModifiedAt: now,
        archived: false,
      };
      await writer.set(db.doc(`sections/${section.id}`), sectionDoc, { merge: true });
      const sectionChange = syncChange('section', section.id, ts.next().value);
      await writer.set(
        db.doc(
          `catalogs/${catalogId}/sync_changes/${sectionChange.changedAtMs}-${sectionChange.type}-${sectionChange.id}`,
        ),
        sectionChange,
      );
      sectionCount += 1;

      for (let t = 0; t < section.themes.length; t++) {
        const theme = section.themes[t];
        const themeDoc = {
          id: theme.id,
          sectionId: section.id,
          title: theme.title,
          order: t,
          version: VERSION,
          contentsVersion: VERSION,
          lastModifiedAt: now,
          archived: false,
        };
        await writer.set(db.doc(`themes/${theme.id}`), themeDoc, { merge: true });
        const themeChange = syncChange('theme', theme.id, ts.next().value);
        await writer.set(
          db.doc(
            `catalogs/${catalogId}/sync_changes/${themeChange.changedAtMs}-${themeChange.type}-${themeChange.id}`,
          ),
          themeChange,
        );
        themeCount += 1;

        for (let l = 0; l < theme.lessons.length; l++) {
          const lesson = theme.lessons[l];
          const lessonDoc = {
            id: lesson.id,
            themeId: theme.id,
            title: lesson.title,
            order: l,
            version: VERSION,
            contentsVersion: VERSION,
            lastModifiedAt: now,
            archived: false,
            averageRating: null,
            ratingCount: 0,
            top3: [],
          };
          await writer.set(db.doc(`lessons/${lesson.id}`), lessonDoc, { merge: true });
          const lessonChange = syncChange('lesson', lesson.id, ts.next().value);
          await writer.set(
            db.doc(
              `catalogs/${catalogId}/sync_changes/${lessonChange.changedAtMs}-${lessonChange.type}-${lessonChange.id}`,
            ),
            lessonChange,
          );
          lessonCount += 1;

          for (let q = 0; q < lesson.questions.length; q++) {
            const question = lesson.questions[q];
            const payloadObj = {
              ...question.payload,
              id: question.id,
            };
            const questionDoc = {
              id: question.id,
              lessonId: lesson.id,
              text: question.text || payloadObj.text,
              payload: mkPayload(payloadObj),
              language: LANGUAGE,
              order: q,
              version: VERSION,
              lastModifiedAt: now,
              archived: false,
            };
            await writer.set(db.doc(`questions/${question.id}`), questionDoc, { merge: true });
            const questionChange = syncChange('question', question.id, ts.next().value);
            await writer.set(
              db.doc(
                `lesson_content/${lesson.id}/sync_changes/${questionChange.changedAtMs}-${questionChange.type}-${questionChange.id}`,
              ),
              questionChange,
            );
            questionCount += 1;
          }
        }
      }
    }
  }

  // Catalog cascade signal.
  const catalogChange = syncChange('catalog', catalogId, ts.next().value);
  await writer.set(
    db.doc(
      `catalogs/${catalogId}/sync_changes/${catalogChange.changedAtMs}-${catalogChange.type}-${catalogChange.id}`,
    ),
    catalogChange,
  );
  await writer.set(
    db.doc(`catalogs/${catalogId}`),
    {
      contentsVersion: INC1,
      lastModifiedAt: now,
    },
    { merge: true },
  );

  const ops = await writer.commit();
  console.log(
    `[${catalogId}] done: ${ops} ops | quests=${questCount} sections=${sectionCount} themes=${themeCount} lessons=${lessonCount} questions=${questionCount}`,
  );
  return { ops, questCount, sectionCount, themeCount, lessonCount, questionCount };
}

function visibleOnForQuest(catalogId, quest) {
  if (Array.isArray(quest.visibleOn) && quest.visibleOn.length > 0) return quest.visibleOn;
  return catalogId === COURSES_CATALOG_ID ? COURSES_VISIBLE_ON : VISIBLE_ON;
}

function archivedForQuest(catalogId, quest) {
  if (typeof quest.archived === 'boolean') return quest.archived;
  return catalogId === COURSES_CATALOG_ID;
}

(async () => {
  const summary = {};
  for (const catalogId of TARGET_CATALOGS) {
    summary[catalogId] = await seedCatalog(catalogId);
  }

  console.log('\n=== Bulk seed summary ===');
  let totalOps = 0;
  let totalQuestions = 0;
  for (const [catalogId, s] of Object.entries(summary)) {
    console.log(
      `  ${catalogId.padEnd(8)} quests=${s.questCount} sections=${s.sectionCount} themes=${s.themeCount} lessons=${s.lessonCount} questions=${s.questionCount} ops=${s.ops}`,
    );
    totalOps += s.ops;
    totalQuestions += s.questionCount;
  }
  console.log(`  TOTAL    ops=${totalOps}  questions=${totalQuestions}`);
  console.log(`  baseMs=${baseMs}  version=${VERSION}`);
})()
  .catch((e) => {
    console.error(e);
    process.exitCode = 1;
  })
  .finally(() => admin.app().delete());
