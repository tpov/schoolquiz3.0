'use strict';

const fs = require('fs');
const admin = require('firebase-admin');

const SERVICE_ACCOUNT_PATH =
  process.env.FIREBASE_SERVICE_ACCOUNT ||
  '/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json';

if (!fs.existsSync(SERVICE_ACCOUNT_PATH)) {
  console.error(`Service account file not found: ${SERVICE_ACCOUNT_PATH}`);
  process.exit(1);
}

admin.initializeApp({ credential: admin.credential.cert(require(SERVICE_ACCOUNT_PATH)) });

const db = admin.firestore();
const DRY_RUN = process.env.DRY_RUN === '1';
const BACKFILL_BASE_MS = Date.now();
const FILTER_CATALOGS = new Set(
  (process.env.CATALOG_IDS || '')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean),
);

const NODE_TYPES = {
  Catalog: 'catalog',
  Quest: 'quest',
  Section: 'section',
  Theme: 'theme',
  Lesson: 'lesson',
  Question: 'question',
};

function toMillis(value, fallback) {
  if (value && typeof value.toMillis === 'function') return value.toMillis();
  if (value instanceof Date) return value.getTime();
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  return fallback;
}

async function readCollection(name) {
  const snap = await db.collection(name).get();
  return new Map(snap.docs.map((doc) => [doc.id, doc.data()]));
}

function shouldIncludeCatalog(catalogId) {
  return FILTER_CATALOGS.size === 0 || FILTER_CATALOGS.has(catalogId);
}

function makeChange(catalogId, type, id, data, index) {
  return {
    catalogId,
    type,
    id,
    lastModifiedAtMs: toMillis(data.lastModifiedAt, 0),
    changedAtMs: BACKFILL_BASE_MS + index,
  };
}

function addChange(changesByCatalog, change) {
  if (!shouldIncludeCatalog(change.catalogId)) return;
  const list = changesByCatalog.get(change.catalogId) || [];
  list.push(change);
  changesByCatalog.set(change.catalogId, list);
}

function addLessonContentChange(changesByLesson, lessonId, change) {
  const list = changesByLesson.get(lessonId) || [];
  list.push(change);
  changesByLesson.set(lessonId, list);
}

function isPublicQuest(quest) {
  return Array.isArray(quest.visibleOn) && quest.visibleOn.length > 0;
}

function collectChanges(data) {
  const changesByCatalog = new Map();
  const changesByLesson = new Map();
  const publicQuestIds = new Set();
  let index = 1;

  for (const [id, catalog] of data.catalogs) {
    addChange(changesByCatalog, makeChange(id, NODE_TYPES.Catalog, id, catalog, index++));
  }

  for (const [id, quest] of data.quests) {
    const catalogId = quest.catalogId;
    if (!catalogId) {
      console.warn(`skip quests/${id}: missing catalogId`);
      continue;
    }
    if (!isPublicQuest(quest)) {
      console.warn(`skip quests/${id}: not public`);
      continue;
    }
    publicQuestIds.add(id);
    addChange(changesByCatalog, makeChange(catalogId, NODE_TYPES.Quest, id, quest, index++));
  }

  for (const [id, section] of data.sections) {
    const quest = data.quests.get(section.questId);
    if (!publicQuestIds.has(section.questId)) {
      console.warn(`skip sections/${id}: parent quest is not public`);
      continue;
    }
    if (!quest?.catalogId) {
      console.warn(`skip sections/${id}: missing parent quest ${section.questId}`);
      continue;
    }
    addChange(changesByCatalog, makeChange(quest.catalogId, NODE_TYPES.Section, id, section, index++));
  }

  for (const [id, theme] of data.themes) {
    const section = data.sections.get(theme.sectionId);
    const quest = section ? data.quests.get(section.questId) : null;
    if (!section || !publicQuestIds.has(section.questId)) {
      console.warn(`skip themes/${id}: parent quest is not public`);
      continue;
    }
    if (!quest?.catalogId) {
      console.warn(`skip themes/${id}: missing parent chain`);
      continue;
    }
    addChange(changesByCatalog, makeChange(quest.catalogId, NODE_TYPES.Theme, id, theme, index++));
  }

  for (const [id, lesson] of data.lessons) {
    const theme = data.themes.get(lesson.themeId);
    const section = theme ? data.sections.get(theme.sectionId) : null;
    const quest = section ? data.quests.get(section.questId) : null;
    if (!section || !publicQuestIds.has(section.questId)) {
      console.warn(`skip lessons/${id}: parent quest is not public`);
      continue;
    }
    if (!quest?.catalogId) {
      console.warn(`skip lessons/${id}: missing parent chain`);
      continue;
    }
    addChange(changesByCatalog, makeChange(quest.catalogId, NODE_TYPES.Lesson, id, lesson, index++));
  }

  for (const [id, question] of data.questions) {
    const lesson = data.lessons.get(question.lessonId);
    const theme = lesson ? data.themes.get(lesson.themeId) : null;
    const section = theme ? data.sections.get(theme.sectionId) : null;
    const quest = section ? data.quests.get(section.questId) : null;
    if (!section || !publicQuestIds.has(section.questId)) {
      console.warn(`skip questions/${id}: parent quest is not public`);
      continue;
    }
    if (!quest?.catalogId) {
      console.warn(`skip questions/${id}: missing parent chain`);
      continue;
    }
    const change = makeChange(quest.catalogId, NODE_TYPES.Question, id, question, index++);
    addChange(changesByCatalog, change);
    addLessonContentChange(changesByLesson, question.lessonId, {
      type: NODE_TYPES.Question,
      id,
      changedAtMs: change.changedAtMs,
    });
  }

  return { changesByCatalog, changesByLesson };
}

async function writeChanges(changesByCatalog) {
  let total = 0;
  let batch = db.batch();
  let pending = 0;

  for (const [catalogId, changes] of changesByCatalog) {
    for (const change of changes.sort((a, b) => a.changedAtMs - b.changedAtMs)) {
      total += 1;
      const ref = db.collection('catalogs')
        .doc(catalogId)
        .collection('sync_changes')
        .doc(`${change.changedAtMs}-${change.type}-${change.id}`);
      if (!DRY_RUN) {
        batch.set(ref, {
          type: change.type,
          id: change.id,
          changedAtMs: change.changedAtMs,
        });
        pending += 1;
      }
      if (pending === 450) {
        await batch.commit();
        batch = db.batch();
        pending = 0;
      }
    }
    console.log(`${catalogId}: ${changes.length} sync_changes`);
  }

  if (!DRY_RUN && pending > 0) {
    await batch.commit();
  }

  return total;
}

async function writeLessonContentChanges(changesByLesson) {
  let total = 0;
  let batch = db.batch();
  let pending = 0;

  for (const [lessonId, changes] of changesByLesson) {
    for (const change of changes.sort((a, b) => a.changedAtMs - b.changedAtMs)) {
      total += 1;
      const ref = db.collection('lesson_content')
        .doc(lessonId)
        .collection('sync_changes')
        .doc(`${change.changedAtMs}-${change.type}-${change.id}`);
      if (!DRY_RUN) {
        batch.set(ref, {
          type: change.type,
          id: change.id,
          changedAtMs: change.changedAtMs,
        });
        pending += 1;
      }
      if (pending === 450) {
        await batch.commit();
        batch = db.batch();
        pending = 0;
      }
    }
    console.log(`lesson_content/${lessonId}: ${changes.length} sync_changes`);
  }

  if (!DRY_RUN && pending > 0) {
    await batch.commit();
  }

  return total;
}

(async () => {
  const data = {
    catalogs: await readCollection('catalogs'),
    quests: await readCollection('quests'),
    sections: await readCollection('sections'),
    themes: await readCollection('themes'),
    lessons: await readCollection('lessons'),
    questions: await readCollection('questions'),
  };
  const { changesByCatalog, changesByLesson } = collectChanges(data);
  const total = await writeChanges(changesByCatalog);
  const lessonTotal = await writeLessonContentChanges(changesByLesson);

  console.log('');
  console.log(`${DRY_RUN ? 'Would write' : 'Wrote'} ${total} sync_changes.`);
  console.log(`${DRY_RUN ? 'Would write' : 'Wrote'} ${lessonTotal} lesson_content sync_changes.`);
  if (FILTER_CATALOGS.size > 0) {
    console.log(`Filtered catalogs: ${Array.from(FILTER_CATALOGS).join(', ')}`);
  }
})()
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  })
  .finally(async () => {
    await admin.app().delete();
    process.exit(process.exitCode ?? 0);
  });
