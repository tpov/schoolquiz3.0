'use strict';

// Production-grade verification for a seeded quest.
//
// It validates Firestore against the local seed source, not just aggregate counts:
// - every expected quest/section/theme/lesson/question document exists;
// - document fields match the current app data contract;
// - no unexpected documents with the course ID prefixes exist;
// - every payload JSON matches the expected question payload;
// - sync_changes documents exist for catalog hierarchy and lesson questions;
// - accidental lesson_materials documents are absent.

const {
  AUTHOR_UID,
  LANGUAGE,
  VISIBLE_ON,
  ensureInit,
  admin,
} = require('./shared');

const catalogId = process.env.VERIFY_CATALOG || 'courses';
const questId = process.env.VERIFY_QUEST_ID || 'qb-courses-english-tech-c2';
const verifySyncChanges = process.env.VERIFY_SYNC_CHANGES !== '0';
const chunkSize = Number(process.env.VERIFY_CHUNK_SIZE || 250);
const COURSES_CATALOG_ID = 'courses';
const COURSES_VISIBLE_ON = ['archive'];

ensureInit();

const db = admin.firestore();
const FieldPath = admin.firestore.FieldPath;

function fail(message) {
  throw new Error(message);
}

function assert(condition, message) {
  if (!condition) fail(message);
}

function chunk(items, size) {
  const result = [];
  for (let i = 0; i < items.length; i += size) {
    result.push(items.slice(i, i + size));
  }
  return result;
}

function stable(value) {
  if (Array.isArray(value)) return value.map(stable);
  if (value && typeof value === 'object') {
    return Object.keys(value)
      .sort()
      .reduce((acc, key) => {
        acc[key] = stable(value[key]);
        return acc;
      }, {});
  }
  return value;
}

function sameJson(a, b) {
  return JSON.stringify(stable(a)) === JSON.stringify(stable(b));
}

function millis(value) {
  if (value && typeof value.toMillis === 'function') return value.toMillis();
  return value;
}

function exactKeys(data, allowed, path) {
  const keys = Object.keys(data).sort();
  const expected = [...allowed].sort();
  assert(
    JSON.stringify(keys) === JSON.stringify(expected),
    `${path}: field keys mismatch\nexpected=${expected.join(',')}\nactual=${keys.join(',')}`,
  );
}

function docPath(collection, id) {
  return `${collection}/${id}`;
}

async function getAllDocs(collection, ids) {
  const refs = ids.map((id) => db.doc(docPath(collection, id)));
  const docs = [];
  for (const group of chunk(refs, chunkSize)) {
    docs.push(...await db.getAll(...group));
  }
  return docs;
}

async function assertCountByPrefix(collection, prefix, expected) {
  const snap = await db.collection(collection)
    .where(FieldPath.documentId(), '>=', prefix)
    .where(FieldPath.documentId(), '<', `${prefix}~`)
    .count()
    .get();
  const actual = snap.data().count;
  assert(actual === expected, `${collection} prefix ${prefix}: expected ${expected}, got ${actual}`);
}

async function assertCollectionDocs(collection, expectedById, verifier) {
  const ids = Object.keys(expectedById);
  const docs = await getAllDocs(collection, ids);
  const seen = new Set();
  docs.forEach((doc) => {
    assert(doc.exists, `${collection}/${doc.id}: missing`);
    seen.add(doc.id);
    verifier(doc, expectedById[doc.id]);
  });
  const missing = ids.filter((id) => !seen.has(id));
  assert(missing.length === 0, `${collection}: missing docs ${missing.slice(0, 20).join(', ')}`);
}

function loadQuest() {
  const data = require(`./data/${catalogId}`);
  const quest = data.quests.find((item) => item.id === questId);
  if (!quest) fail(`Quest ${questId} not found in scripts/seed-bulk/data/${catalogId}.js`);
  return quest;
}

function flattenQuest(quest) {
  const sections = [];
  const themes = [];
  const lessons = [];
  const questions = [];

  quest.sections.forEach((section, sectionOrder) => {
    sections.push({ ...section, order: sectionOrder, questId: quest.id });
    section.themes.forEach((theme, themeOrder) => {
      themes.push({ ...theme, order: themeOrder, sectionId: section.id });
      theme.lessons.forEach((lesson, lessonOrder) => {
        lessons.push({ ...lesson, order: lessonOrder, themeId: theme.id });
        lesson.questions.forEach((question, questionOrder) => {
          questions.push({
            ...question,
            order: questionOrder,
            lessonId: lesson.id,
            payloadObj: {
              ...question.payload,
              id: question.id,
            },
          });
        });
      });
    });
  });

  return { sections, themes, lessons, questions };
}

function indexById(items) {
  return Object.fromEntries(items.map((item) => [item.id, item]));
}

function assertVersioned(data, version, path, hasContentsVersion, expectedArchived = false) {
  assert(data.version === version, `${path}: version expected ${version}, got ${data.version}`);
  if (hasContentsVersion) {
    assert(data.contentsVersion === version, `${path}: contentsVersion expected ${version}, got ${data.contentsVersion}`);
  }
  assert(millis(data.lastModifiedAt) === version, `${path}: lastModifiedAt expected ${version}, got ${millis(data.lastModifiedAt)}`);
  assert(data.archived === expectedArchived, `${path}: archived expected ${expectedArchived}, got ${data.archived}`);
}

function verifyQuestDoc(doc, expected, version) {
  const path = docPath('quests', doc.id);
  const data = doc.data();
  exactKeys(
    data,
    [
      'id',
      'catalogId',
      'authorUid',
      'title',
      'picturePath',
      'visibleOn',
      'averageRating',
      'averageRatingCount',
      'version',
      'contentsVersion',
      'lastModifiedAt',
      'archived',
    ],
    path,
  );
  assert(data.id === expected.id, `${path}: id mismatch`);
  assert(data.catalogId === catalogId, `${path}: catalogId mismatch`);
  assert(data.authorUid === AUTHOR_UID, `${path}: authorUid mismatch`);
  assert(data.title === expected.title, `${path}: title mismatch`);
  assert(data.picturePath === null, `${path}: picturePath must be null`);
  assert(sameJson(data.visibleOn, visibleOnForQuest(expected)), `${path}: visibleOn mismatch`);
  assert(data.averageRating === null, `${path}: averageRating must be null`);
  assert(data.averageRatingCount === 0, `${path}: averageRatingCount must be 0`);
  assertVersioned(data, version, path, true, archivedForQuest(expected));
}

function visibleOnForQuest(quest) {
  if (Array.isArray(quest.visibleOn) && quest.visibleOn.length > 0) return quest.visibleOn;
  return catalogId === COURSES_CATALOG_ID ? COURSES_VISIBLE_ON : VISIBLE_ON;
}

function archivedForQuest(quest) {
  if (typeof quest.archived === 'boolean') return quest.archived;
  return catalogId === COURSES_CATALOG_ID;
}

function verifySectionDoc(doc, expected, version) {
  const path = docPath('sections', doc.id);
  const data = doc.data();
  exactKeys(data, ['id', 'questId', 'title', 'order', 'version', 'contentsVersion', 'lastModifiedAt', 'archived'], path);
  assert(data.id === expected.id, `${path}: id mismatch`);
  assert(data.questId === expected.questId, `${path}: questId mismatch`);
  assert(data.title === expected.title, `${path}: title mismatch`);
  assert(data.order === expected.order, `${path}: order mismatch`);
  assertVersioned(data, version, path, true);
}

function verifyThemeDoc(doc, expected, version) {
  const path = docPath('themes', doc.id);
  const data = doc.data();
  exactKeys(data, ['id', 'sectionId', 'title', 'order', 'version', 'contentsVersion', 'lastModifiedAt', 'archived'], path);
  assert(data.id === expected.id, `${path}: id mismatch`);
  assert(data.sectionId === expected.sectionId, `${path}: sectionId mismatch`);
  assert(data.title === expected.title, `${path}: title mismatch`);
  assert(data.order === expected.order, `${path}: order mismatch`);
  assertVersioned(data, version, path, true);
}

function verifyLessonDoc(doc, expected, version) {
  const path = docPath('lessons', doc.id);
  const data = doc.data();
  exactKeys(
    data,
    [
      'id',
      'themeId',
      'title',
      'order',
      'version',
      'contentsVersion',
      'lastModifiedAt',
      'archived',
      'averageRating',
      'ratingCount',
      'top3',
    ],
    path,
  );
  assert(data.id === expected.id, `${path}: id mismatch`);
  assert(data.themeId === expected.themeId, `${path}: themeId mismatch`);
  assert(data.title === expected.title, `${path}: title mismatch`);
  assert(data.order === expected.order, `${path}: order mismatch`);
  assert(data.averageRating === null, `${path}: averageRating must be null`);
  assert(data.ratingCount === 0, `${path}: ratingCount must be 0`);
  assert(Array.isArray(data.top3) && data.top3.length === 0, `${path}: top3 must be empty array`);
  assertVersioned(data, version, path, true);
}

function verifyQuestionDoc(doc, expected, version) {
  const path = docPath('questions', doc.id);
  const data = doc.data();
  exactKeys(data, ['id', 'lessonId', 'text', 'payload', 'language', 'order', 'version', 'lastModifiedAt', 'archived'], path);
  assert(data.id === expected.id, `${path}: id mismatch`);
  assert(data.lessonId === expected.lessonId, `${path}: lessonId mismatch`);
  assert(data.text === (expected.text || expected.payloadObj.text), `${path}: text mismatch`);
  assert(data.language === LANGUAGE, `${path}: language mismatch`);
  assert(data.order === expected.order, `${path}: order mismatch`);
  assertVersioned(data, version, path, false);
  let payload;
  try {
    payload = JSON.parse(data.payload);
  } catch (error) {
    fail(`${path}: payload is not valid JSON: ${error.message}`);
  }
  assert(sameJson(payload, expected.payloadObj), `${path}: payload mismatch`);
}

function makeExpectedSyncChanges(flat, version) {
  let changedAtMs = version;
  const catalogChanges = [];
  const questionChangesByLesson = new Map();

  catalogChanges.push({ type: 'quest', id: questId, changedAtMs: changedAtMs++ });
  flat.sections.forEach((section) => {
    catalogChanges.push({ type: 'section', id: section.id, changedAtMs: changedAtMs++ });
    section.themes.forEach((theme) => {
      catalogChanges.push({ type: 'theme', id: theme.id, changedAtMs: changedAtMs++ });
      theme.lessons.forEach((lesson) => {
        catalogChanges.push({ type: 'lesson', id: lesson.id, changedAtMs: changedAtMs++ });
        lesson.questions.forEach((question) => {
          const change = { type: 'question', id: question.id, changedAtMs: changedAtMs++ };
          if (!questionChangesByLesson.has(lesson.id)) questionChangesByLesson.set(lesson.id, []);
          questionChangesByLesson.get(lesson.id).push(change);
        });
      });
    });
  });
  catalogChanges.push({ type: 'catalog', id: catalogId, changedAtMs: changedAtMs++ });

  return { catalogChanges, questionChangesByLesson };
}

function verifyChangeDoc(doc, expected) {
  const data = doc.data();
  exactKeys(data, ['type', 'id', 'changedAtMs'], doc.ref.path);
  assert(data.type === expected.type, `${doc.ref.path}: type mismatch`);
  assert(data.id === expected.id, `${doc.ref.path}: id mismatch`);
  assert(data.changedAtMs === expected.changedAtMs, `${doc.ref.path}: changedAtMs mismatch`);
}

async function verifySync(flat, version) {
  const { catalogChanges, questionChangesByLesson } = makeExpectedSyncChanges(flat, version);
  const catalogRefs = catalogChanges.map((change) => (
    db.doc(`catalogs/${catalogId}/sync_changes/${change.changedAtMs}-${change.type}-${change.id}`)
  ));
  const catalogDocs = [];
  for (const group of chunk(catalogRefs, chunkSize)) catalogDocs.push(...await db.getAll(...group));
  catalogDocs.forEach((doc, index) => {
    assert(doc.exists, `${doc.ref.path}: missing`);
    verifyChangeDoc(doc, catalogChanges[index]);
  });

  const questionSync = [];
  for (const [lessonId, changes] of questionChangesByLesson.entries()) {
    changes.forEach((change) => {
      questionSync.push({
        expected: change,
        ref: db.doc(`lesson_content/${lessonId}/sync_changes/${change.changedAtMs}-${change.type}-${change.id}`),
      });
    });
  }
  for (const group of chunk(questionSync, chunkSize)) {
    const docs = await db.getAll(...group.map((item) => item.ref));
    docs.forEach((doc, index) => {
      assert(doc.exists, `${doc.ref.path}: missing`);
      verifyChangeDoc(doc, group[index].expected);
    });
  }
}

async function main() {
  const quest = loadQuest();
  const flat = flattenQuest(quest);
  const questDoc = await db.doc(`quests/${quest.id}`).get();
  assert(questDoc.exists, `quests/${quest.id}: missing`);
  const version = questDoc.get('version');
  assert(Number.isSafeInteger(version) && version > 0, `quests/${quest.id}: invalid version ${version}`);

  await assertCountByPrefix('sections', 'sb-courses-english-tech-', flat.sections.length);
  await assertCountByPrefix('themes', 'tb-courses-english-tech-', flat.themes.length);
  await assertCountByPrefix('lessons', 'lb-courses-english-tech-', flat.lessons.length);
  await assertCountByPrefix('questions', 'qsb-courses-english-tech-', flat.questions.length);
  await assertCountByPrefix('lesson_materials', 'lb-courses-english-tech-', 0);

  verifyQuestDoc(questDoc, quest, version);
  await assertCollectionDocs('sections', indexById(flat.sections), (doc, expected) => verifySectionDoc(doc, expected, version));
  await assertCollectionDocs('themes', indexById(flat.themes), (doc, expected) => verifyThemeDoc(doc, expected, version));
  await assertCollectionDocs('lessons', indexById(flat.lessons), (doc, expected) => verifyLessonDoc(doc, expected, version));
  await assertCollectionDocs('questions', indexById(flat.questions), (doc, expected) => verifyQuestionDoc(doc, expected, version));

  if (verifySyncChanges) {
    await verifySync(flat, version);
  }

  console.log(JSON.stringify({
    status: 'OK',
    catalogId,
    questId,
    version,
    sections: flat.sections.length,
    themes: flat.themes.length,
    lessons: flat.lessons.length,
    questions: flat.questions.length,
    hoursAt20x5Min: flat.lessons.length * 20 * 5 / 60,
    syncChangesVerified: verifySyncChanges,
  }, null, 2));
}

main()
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  })
  .finally(async () => {
    await admin.app().delete();
    process.exit(process.exitCode || 0);
  });
