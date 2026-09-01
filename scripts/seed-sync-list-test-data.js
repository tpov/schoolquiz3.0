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

const sa = require(SERVICE_ACCOUNT_PATH);
const {catalogSyncChangeId} = require('./sync-change-id');

admin.initializeApp({ credential: admin.credential.cert(sa) });

const db = admin.firestore();
const T = admin.firestore.Timestamp;

const CATALOG_ID = process.env.SYNC_TEST_CATALOG_ID || 'codex-sync-test';
if (CATALOG_ID !== 'codex-sync-test' && process.env.ALLOW_NON_TEST_CATALOG !== '1') {
  console.error(
    'Refusing to seed a non-test catalog. Set ALLOW_NON_TEST_CATALOG=1 if this is intentional.',
  );
  process.exit(1);
}

const QUEST_ID = `${CATALOG_ID}-quest`;
const SECTION_ID = `${CATALOG_ID}-section`;
const THEME_ID = `${CATALOG_ID}-theme`;
const LESSON_ID = `${CATALOG_ID}-lesson`;
const QUESTION_IDS = [
  `${CATALOG_ID}-q-single`,
  `${CATALOG_ID}-q-multi`,
  `${CATALOG_ID}-q-order`,
];

const NODE_COLLECTIONS = {
  catalog: 'catalogs',
  quest: 'quests',
  section: 'sections',
  theme: 'themes',
  lesson: 'lessons',
  question: 'questions',
};

function mkPayload(obj) {
  return JSON.stringify(obj);
}

function msTimestamp(ms) {
  return T.fromMillis(ms);
}

async function deleteExistingSyncChanges() {
  const snap = await db.collection('catalogs').doc(CATALOG_ID).collection('sync_changes').get();
  if (snap.empty) return 0;

  let deleted = 0;
  let batch = db.batch();
  let pending = 0;
  for (const doc of snap.docs) {
    batch.delete(doc.ref);
    pending += 1;
    deleted += 1;
    if (pending === 450) {
      await batch.commit();
      batch = db.batch();
      pending = 0;
    }
  }
  if (pending > 0) {
    await batch.commit();
  }
  return deleted;
}

function buildDocs(baseMs) {
  const stamp = msTimestamp(baseMs);
  const version = baseMs;

  const catalog = {
    name: 'Codex Sync Test',
    picturePath: 'catalog-pictures/school.jpg',
    version,
    contentsVersion: version,
    lastModifiedAt: stamp,
    archived: false,
    iconCategoryKey: 'school',
    iconNames: ['School', 'AutoStories', 'MenuBook', 'Calculate', 'Science', 'Lightbulb'],
  };

  const quest = {
    catalogId: CATALOG_ID,
    authorUid: 'seed-author-uid',
    title: 'Sync contract smoke quest',
    picturePath: null,
    visibleOn: ['home'],
    averageRating: 2.6,
    averageRatingCount: 7,
    version,
    contentsVersion: version,
    lastModifiedAt: stamp,
    archived: false,
  };

  const section = {
    questId: QUEST_ID,
    title: 'Flat nodes',
    order: 0,
    version,
    contentsVersion: version,
    lastModifiedAt: stamp,
    archived: false,
  };

  const theme = {
    sectionId: SECTION_ID,
    title: 'Catalog sync list',
    order: 0,
    version,
    contentsVersion: version,
    lastModifiedAt: stamp,
    archived: false,
  };

  const lesson = {
    themeId: THEME_ID,
    title: 'Client fetches by id',
    order: 0,
    version,
    contentsVersion: version,
    lastModifiedAt: stamp,
    archived: false,
    averageRating: 2.4,
    ratingCount: 5,
    top3: [
      { nickname: 'Nova', avatarUrl: null, percent: 96 },
      { nickname: 'Mira', avatarUrl: null, percent: 88 },
      { nickname: 'Leo', avatarUrl: null, percent: 79 },
    ],
  };

  const questions = [
    {
      lessonId: LESSON_ID,
      text: 'Which record is stored in sync_changes?',
      payload: mkPayload({
        type: 'SingleChoice',
        id: QUESTION_IDS[0],
        difficulty: 'EASY',
        text: 'Which record is stored in sync_changes?',
        imageUrl: null,
        options: [
          { id: 'a', text: 'Only type, id, and changedAtMs' },
          { id: 'b', text: 'The whole question tree' },
          { id: 'c', text: 'Only images' },
          { id: 'd', text: 'Only local ratings' },
        ],
        correctOptionId: 'a',
        info: 'The client reads the tiny change record, then fetches the real flat document by id.',
      }),
      language: 'en',
      order: 0,
      version,
      lastModifiedAt: stamp,
      archived: false,
    },
    {
      lessonId: LESSON_ID,
      text: 'Which flat collections can be fetched by id?',
      payload: mkPayload({
        type: 'MultipleChoice',
        id: QUESTION_IDS[1],
        difficulty: 'HARD',
        text: 'Which flat collections can be fetched by id?',
        imageUrl: null,
        options: [
          { id: 'a', text: 'quests' },
          { id: 'b', text: 'sections' },
          { id: 'c', text: 'themes' },
          { id: 'd', text: 'lessons' },
          { id: 'e', text: 'questions' },
          { id: 'f', text: 'temporary UI state' },
        ],
        correctOptionIds: ['a', 'b', 'c', 'd', 'e'],
        info: 'Content nodes are flat Firestore documents. UI state stays local and is not synced as catalog content.',
      }),
      language: 'en',
      order: 1,
      version,
      lastModifiedAt: stamp,
      archived: false,
    },
    {
      lessonId: LESSON_ID,
      text: 'Put the client sync flow in order.',
      payload: mkPayload({
        type: 'Ordering',
        id: QUESTION_IDS[2],
        difficulty: 'EASY',
        text: 'Put the client sync flow in order.',
        imageUrl: null,
        items: [
          { id: 'i1', text: 'Read catalogs changed since catalog cursor' },
          { id: 'i2', text: 'Read sync_changes for each catalog' },
          { id: 'i3', text: 'Fetch flat documents by id' },
          { id: 'i4', text: 'Move the catalog sync cursor' },
        ],
        info: 'The cursor moves only after all documents from the change list are applied.',
      }),
      language: 'en',
      order: 2,
      version,
      lastModifiedAt: stamp,
      archived: false,
    },
  ];

  return {
    catalog,
    quest,
    section,
    theme,
    lesson,
    questions,
  };
}

function buildInitialChanges(baseMs) {
  const nodes = [
    ['catalog', CATALOG_ID],
    ['quest', QUEST_ID],
    ['section', SECTION_ID],
    ['theme', THEME_ID],
    ['lesson', LESSON_ID],
    ['question', QUESTION_IDS[0]],
    ['question', QUESTION_IDS[1]],
    ['question', QUESTION_IDS[2]],
  ];

  return nodes.map(([type, id], index) => ({
    type,
    id,
    changedAtMs: baseMs + index + 1,
  }));
}

async function writeSeed(baseMs) {
  const docs = buildDocs(baseMs);
  const changes = buildInitialChanges(baseMs);
  const deletedChanges = await deleteExistingSyncChanges();

  const batch = db.batch();
  batch.set(db.collection('catalogs').doc(CATALOG_ID), docs.catalog);
  batch.set(db.collection('quests').doc(QUEST_ID), docs.quest);
  batch.set(db.collection('sections').doc(SECTION_ID), docs.section);
  batch.set(db.collection('themes').doc(THEME_ID), docs.theme);
  batch.set(db.collection('lessons').doc(LESSON_ID), docs.lesson);
  docs.questions.forEach((question, index) => {
    batch.set(db.collection('questions').doc(QUESTION_IDS[index]), question);
  });
  changes.forEach((change) => {
    batch.set(
      db.collection('catalogs')
        .doc(CATALOG_ID)
        .collection('sync_changes')
        .doc(catalogSyncChangeId(change.type, change.id)),
      change,
    );
  });
  await batch.commit();

  return { changes, deletedChanges };
}

async function writeIncrementalQuestionChange(previousCursorMs) {
  const changedAtMs = previousCursorMs + 1000;
  const questionRef = db.collection('questions').doc(QUESTION_IDS[0]);
  await questionRef.update({
    text: 'Which record is stored in sync_changes? (updated)',
    payload: mkPayload({
      type: 'SingleChoice',
      id: QUESTION_IDS[0],
      difficulty: 'EASY',
      text: 'Which record is stored in sync_changes? (updated)',
      imageUrl: null,
      options: [
        { id: 'a', text: 'Only type, id, and changedAtMs' },
        { id: 'b', text: 'The whole question tree' },
        { id: 'c', text: 'Only images' },
        { id: 'd', text: 'Only local ratings' },
      ],
      correctOptionId: 'a',
      info: 'This updated question proves that a later sync_changes cursor fetches only the changed node.',
    }),
    version: changedAtMs,
    lastModifiedAt: msTimestamp(changedAtMs),
  });

  const change = {
    type: 'question',
    id: QUESTION_IDS[0],
    changedAtMs,
  };
  await db.collection('catalogs')
    .doc(CATALOG_ID)
    .collection('sync_changes')
    .doc(catalogSyncChangeId(change.type, change.id))
    .set(change);

  return change;
}

function requireField(data, path, field) {
  if (!Object.prototype.hasOwnProperty.call(data, field)) {
    throw new Error(`${path} is missing field ${field}`);
  }
}

function requireNonBlankString(data, path, field) {
  requireField(data, path, field);
  if (typeof data[field] !== 'string' || data[field].trim() === '') {
    throw new Error(`${path}.${field} must be a non-blank string`);
  }
}

function requireTimestamp(data, path, field) {
  requireField(data, path, field);
  if (!data[field] || typeof data[field].toMillis !== 'function') {
    throw new Error(`${path}.${field} must be a Firestore Timestamp`);
  }
}

async function requireDoc(collection, id, validators) {
  const path = `${collection}/${id}`;
  const snap = await db.collection(collection).doc(id).get();
  if (!snap.exists) {
    throw new Error(`Missing ${path}`);
  }
  const data = snap.data();
  validators(data, path);
  return data;
}

async function verifyCatalogQuery(baseMs) {
  const snap = await db.collection('catalogs')
    .where('lastModifiedAt', '>', msTimestamp(baseMs - 1))
    .get();
  if (!snap.docs.some((doc) => doc.id === CATALOG_ID)) {
    throw new Error(`Catalog ${CATALOG_ID} was not returned by lastModifiedAt query`);
  }
}

async function fetchSyncChanges(cursorMs) {
  const snap = await db.collection('catalogs')
    .doc(CATALOG_ID)
    .collection('sync_changes')
    .where('changedAtMs', '>', cursorMs)
    .orderBy('changedAtMs')
    .get();
  return snap.docs.map((doc) => doc.data());
}

async function verifyInitialChanges(baseMs, expectedCount) {
  const changes = await fetchSyncChanges(baseMs);
  if (changes.length !== expectedCount) {
    throw new Error(`Expected ${expectedCount} initial sync changes, got ${changes.length}`);
  }
  for (let i = 1; i < changes.length; i += 1) {
    if (changes[i - 1].changedAtMs >= changes[i].changedAtMs) {
      throw new Error('sync_changes are not ordered by changedAtMs');
    }
  }
  return changes;
}

async function verifyFlatDocs() {
  await requireDoc('catalogs', CATALOG_ID, (data, path) => {
    requireNonBlankString(data, path, 'name');
    requireTimestamp(data, path, 'lastModifiedAt');
    if (!Array.isArray(data.iconNames) || data.iconNames.length === 0) {
      throw new Error(`${path}.iconNames must be a non-empty array`);
    }
  });
  await requireDoc('quests', QUEST_ID, (data, path) => {
    requireNonBlankString(data, path, 'catalogId');
    requireNonBlankString(data, path, 'title');
    requireTimestamp(data, path, 'lastModifiedAt');
    if (data.catalogId !== CATALOG_ID) throw new Error(`${path}.catalogId mismatch`);
  });
  await requireDoc('sections', SECTION_ID, (data, path) => {
    requireNonBlankString(data, path, 'questId');
    requireNonBlankString(data, path, 'title');
    requireTimestamp(data, path, 'lastModifiedAt');
    if (data.questId !== QUEST_ID) throw new Error(`${path}.questId mismatch`);
  });
  await requireDoc('themes', THEME_ID, (data, path) => {
    requireNonBlankString(data, path, 'sectionId');
    requireNonBlankString(data, path, 'title');
    requireTimestamp(data, path, 'lastModifiedAt');
    if (data.sectionId !== SECTION_ID) throw new Error(`${path}.sectionId mismatch`);
  });
  await requireDoc('lessons', LESSON_ID, (data, path) => {
    requireNonBlankString(data, path, 'themeId');
    requireNonBlankString(data, path, 'title');
    requireTimestamp(data, path, 'lastModifiedAt');
    if (data.themeId !== THEME_ID) throw new Error(`${path}.themeId mismatch`);
  });
  for (const id of QUESTION_IDS) {
    await requireDoc('questions', id, (data, path) => {
      requireNonBlankString(data, path, 'lessonId');
      requireNonBlankString(data, path, 'text');
      requireNonBlankString(data, path, 'payload');
      requireTimestamp(data, path, 'lastModifiedAt');
      if (data.lessonId !== LESSON_ID) throw new Error(`${path}.lessonId mismatch`);
      JSON.parse(data.payload);
    });
  }
}

async function verifyChangesCanFetchFlatDocs(changes) {
  for (const change of changes) {
    const collection = NODE_COLLECTIONS[change.type];
    if (!collection) {
      throw new Error(`Unknown sync change type: ${change.type}`);
    }
    const snap = await db.collection(collection).doc(change.id).get();
    if (!snap.exists) {
      throw new Error(`Change ${change.type}/${change.id} points to a missing document`);
    }
  }
}

async function verifyIncrementalChange(previousCursorMs, expectedChange) {
  const changes = await fetchSyncChanges(previousCursorMs);
  if (changes.length !== 1) {
    throw new Error(`Expected 1 incremental sync change, got ${changes.length}`);
  }
  const [change] = changes;
  if (
    change.type !== expectedChange.type ||
    change.id !== expectedChange.id ||
    change.changedAtMs !== expectedChange.changedAtMs
  ) {
    throw new Error(`Unexpected incremental change: ${JSON.stringify(change)}`);
  }
  const question = await db.collection('questions').doc(expectedChange.id).get();
  if (!question.exists || !question.data().text.endsWith('(updated)')) {
    throw new Error('Incremental question document was not updated');
  }
}

(async () => {
  const baseMs = Date.now();

  const { changes, deletedChanges } = await writeSeed(baseMs);
  await verifyCatalogQuery(baseMs);
  await verifyFlatDocs();
  const initialChanges = await verifyInitialChanges(baseMs, changes.length);
  await verifyChangesCanFetchFlatDocs(initialChanges);

  const initialCursor = Math.max(...initialChanges.map((change) => change.changedAtMs));
  const incrementalChange = await writeIncrementalQuestionChange(initialCursor);
  await verifyIncrementalChange(initialCursor, incrementalChange);

  console.log('Seeded and verified sync-list test data.');
  console.log(`  catalog: ${CATALOG_ID}`);
  console.log(`  deleted previous test sync_changes: ${deletedChanges}`);
  console.log(`  initial sync_changes: ${changes.length}`);
  console.log(`  incremental sync_change: ${incrementalChange.type}/${incrementalChange.id}`);
  console.log(`  latest cursor: ${incrementalChange.changedAtMs}`);
  console.log(`  flat docs: ${Object.values(NODE_COLLECTIONS).join(', ')}`);
  process.exit(0);
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
