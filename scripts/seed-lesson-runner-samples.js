'use strict';
// Seed script: creates a complete hierarchy with 8 lesson-runner sample questions.
// Usage: node scripts/seed-lesson-runner-samples.js
// Requires: firebase-admin npm package in scripts/node_modules (already present).
const admin = require('firebase-admin');
const sa = require('/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json');
const {catalogSyncChangePath, lessonContentSyncChangePath} = require('./sync-change-id');
admin.initializeApp({ credential: admin.credential.cert(sa) });
const db = admin.firestore();
const now = admin.firestore.Timestamp.now();
const baseMs = now.toMillis();
const INC1 = admin.firestore.FieldValue.increment(1);
const AUTHOR = 'seed-author-uid';
const VERSION = 4;

const CATALOG_ID = 'school';
const QUEST_ID   = 'quest-lr-samples';
const SECTION_ID = 'section-lr-samples';
const THEME_ID   = 'theme-lr-samples';
const LESSON_ID  = 'lesson-lr-types';

// Inline payload helper — stores QuestionContent JSON as a string field per ADR-0003.
const mkPayload = (obj) => JSON.stringify(obj);
const syncChange = (type, id, offset) => ({
  type,
  id,
  changedAtMs: baseMs + offset,
});

// ── 8 Questions ───────────────────────────────────────────────────────────────

const questions = [
  // 1. SingleChoice EASY
  {
    id: 'lr-q-sc-easy',
    lessonId: LESSON_ID,
    text: 'Сколько будет 3 + 3?',
    payload: mkPayload({
      type: 'SingleChoice',
      id: 'lr-q-sc-easy',
      difficulty: 'EASY',
      text: 'Сколько будет 3 + 3?',
      imageUrl: null,
      options: [
        { id: 'a', text: '3' },
        { id: 'b', text: '4' },
        { id: 'c', text: '5' },
        { id: 'd', text: '6' },
      ],
      correctOptionId: 'd',
      info: 'Складываем три и три: 3 + 3 = 6. Поэтому верный вариант — 6.',
    }),
    language: 'ru',
    order: 0,
    version: VERSION,
    lastModifiedAt: now,
    archived: false,
  },

  // 2. SingleChoice HARD
  {
    id: 'lr-q-sc-hard',
    lessonId: LESSON_ID,
    text: 'Чему равно число π с точностью до двух знаков после запятой?',
    payload: mkPayload({
      type: 'SingleChoice',
      id: 'lr-q-sc-hard',
      difficulty: 'HARD',
      text: 'Чему равно число π с точностью до двух знаков после запятой?',
      imageUrl: null,
      options: [
        { id: 'a', text: '3.12' },
        { id: 'b', text: '3.14' },
        { id: 'c', text: '3.16' },
        { id: 'd', text: '3.41' },
      ],
      correctOptionId: 'b',
      info: 'Число π начинается как 3.14159..., а при округлении до двух знаков после запятой получается 3.14.',
    }),
    language: 'ru',
    order: 1,
    version: VERSION,
    lastModifiedAt: now,
    archived: false,
  },

  // 3. MultipleChoice EASY
  {
    id: 'lr-q-mc-easy',
    lessonId: LESSON_ID,
    text: 'Какие из чисел чётные? (выберите все верные)',
    payload: mkPayload({
      type: 'MultipleChoice',
      id: 'lr-q-mc-easy',
      difficulty: 'EASY',
      text: 'Какие из чисел чётные? (выберите все верные)',
      imageUrl: null,
      options: [
        { id: 'a', text: '2' },
        { id: 'b', text: '3' },
        { id: 'c', text: '4' },
        { id: 'd', text: '7' },
        { id: 'e', text: '8' },
      ],
      correctOptionIds: ['a', 'c', 'e'],
      info: 'Чётные числа делятся на 2 без остатка. 2, 4 и 8 подходят; 3 и 7 — нет.',
    }),
    language: 'ru',
    order: 2,
    version: VERSION,
    lastModifiedAt: now,
    archived: false,
  },

  // 4. MultipleChoice HARD
  {
    id: 'lr-q-mc-hard',
    lessonId: LESSON_ID,
    text: 'Какие слова являются именами существительными? (выберите все верные)',
    payload: mkPayload({
      type: 'MultipleChoice',
      id: 'lr-q-mc-hard',
      difficulty: 'HARD',
      text: 'Какие слова являются именами существительными? (выберите все верные)',
      imageUrl: null,
      options: [
        { id: 'a', text: 'бежать' },
        { id: 'b', text: 'книга' },
        { id: 'c', text: 'синий' },
        { id: 'd', text: 'школа' },
        { id: 'e', text: 'быстро' },
        { id: 'f', text: 'ученик' },
      ],
      correctOptionIds: ['b', 'd', 'f'],
      info: 'Существительные называют предметы или людей: книга, школа, ученик. Остальные варианты отвечают на другие вопросы.',
    }),
    language: 'ru',
    order: 3,
    version: VERSION,
    lastModifiedAt: now,
    archived: false,
  },

  // 5. Ordering EASY
  {
    id: 'lr-q-ord-easy',
    lessonId: LESSON_ID,
    text: 'Расставь числа по возрастанию',
    payload: mkPayload({
      type: 'Ordering',
      id: 'lr-q-ord-easy',
      difficulty: 'EASY',
      text: 'Расставь числа по возрастанию',
      imageUrl: null,
      items: [
        { id: 'i1', text: '1' },
        { id: 'i2', text: '5' },
        { id: 'i3', text: '9' },
        { id: 'i4', text: '15' },
      ],
      info: 'Возрастание означает от меньшего числа к большему: 1, 5, 9, 15.',
    }),
    language: 'ru',
    order: 4,
    version: VERSION,
    lastModifiedAt: now,
    archived: false,
  },

  // 6. Ordering HARD
  {
    id: 'lr-q-ord-hard',
    lessonId: LESSON_ID,
    text: 'Расставь планеты от ближайшей к Солнцу до дальней',
    payload: mkPayload({
      type: 'Ordering',
      id: 'lr-q-ord-hard',
      difficulty: 'HARD',
      text: 'Расставь планеты от ближайшей к Солнцу до дальней',
      imageUrl: null,
      items: [
        { id: 'i1', text: 'Меркурий' },
        { id: 'i2', text: 'Венера' },
        { id: 'i3', text: 'Земля' },
        { id: 'i4', text: 'Марс' },
        { id: 'i5', text: 'Юпитер' },
      ],
      info: 'Порядок от Солнца начинается так: Меркурий, Венера, Земля, Марс, Юпитер.',
    }),
    language: 'ru',
    order: 5,
    version: VERSION,
    lastModifiedAt: now,
    archived: false,
  },

  // 7. FillBlank EASY
  // Blank markers are "___" (three underscores) per parseTemplateParts in RunnerStateMapper.kt.
  // Blank order in `blanks` matches the left-to-right order of "___" occurrences in text.
  {
    id: 'lr-q-fb-easy',
    lessonId: LESSON_ID,
    text: 'Столица России — ___, а столица Франции — ___.',
    payload: mkPayload({
      type: 'FillBlank',
      id: 'lr-q-fb-easy',
      difficulty: 'EASY',
      text: 'Столица России — ___, а столица Франции — ___.',
      imageUrl: null,
      blanks: [
        { id: 'b1', correctCandidateId: 'c1' },
        { id: 'b2', correctCandidateId: 'c2' },
      ],
      candidates: [
        { id: 'c1', text: 'Москва' },
        { id: 'c2', text: 'Париж' },
        { id: 'c3', text: 'Берлин' },
        { id: 'c4', text: 'Лондон' },
        { id: 'c5', text: 'Рим' },
      ],
      info: 'В первой пропущенной части нужна Москва, во второй — Париж.',
    }),
    language: 'ru',
    order: 6,
    version: VERSION,
    lastModifiedAt: now,
    archived: false,
  },

  // 8. FillBlank HARD
  {
    id: 'lr-q-fb-hard',
    lessonId: LESSON_ID,
    text: 'Формула воды — ___, а формула поваренной соли — ___.',
    payload: mkPayload({
      type: 'FillBlank',
      id: 'lr-q-fb-hard',
      difficulty: 'HARD',
      text: 'Формула воды — ___, а формула поваренной соли — ___.',
      imageUrl: null,
      blanks: [
        { id: 'b1', correctCandidateId: 'c3' },
        { id: 'b2', correctCandidateId: 'c7' },
      ],
      candidates: [
        { id: 'c1',  text: 'CO2'   },
        { id: 'c2',  text: 'O2'    },
        { id: 'c3',  text: 'H2O'   },
        { id: 'c4',  text: 'N2'    },
        { id: 'c5',  text: 'CH4'   },
        { id: 'c6',  text: 'HCl'   },
        { id: 'c7',  text: 'NaCl'  },
        { id: 'c8',  text: 'KCl'   },
        { id: 'c9',  text: 'CaCO3' },
        { id: 'c10', text: 'MgO'   },
      ],
      info: 'Вода записывается как H2O, а поваренная соль — как NaCl.',
    }),
    language: 'ru',
    order: 7,
    version: VERSION,
    lastModifiedAt: now,
    archived: false,
  },
];

// ── Lesson ────────────────────────────────────────────────────────────────────

const lesson = {
  id: LESSON_ID,
  themeId: THEME_ID,
  title: 'Тест: типы вопросов',
  order: 0,
  version: VERSION,
  contentsVersion: questions.length,
  lastModifiedAt: now,
  archived: false,
  averageRating: 2.5,
  ratingCount: 4,
  top3: [
    { nickname: 'Alice',   avatarUrl: null, percent: 95 },
    { nickname: 'Bob',     avatarUrl: null, percent: 87 },
    { nickname: 'Charlie', avatarUrl: null, percent: 72 },
  ],
};

// ── Theme / Section / Quest ───────────────────────────────────────────────────

const theme = {
  id: THEME_ID,
  sectionId: SECTION_ID,
  title: 'Типы вопросов',
  order: 0,
  version: VERSION,
  contentsVersion: 1,
  lastModifiedAt: now,
  archived: false,
};

const section = {
  id: SECTION_ID,
  questId: QUEST_ID,
  title: 'Примеры вопросов',
  order: 0,
  version: VERSION,
  contentsVersion: 1,
  lastModifiedAt: now,
  archived: false,
};

const quest = {
  id: QUEST_ID,
  catalogId: CATALOG_ID,
  authorUid: AUTHOR,
  title: 'Демо: Lesson Runner',
  picturePath: null,
  visibleOn: ['home', 'arena'],
  averageRating: null,
  averageRatingCount: 0,
  version: VERSION,
  contentsVersion: 1,
  lastModifiedAt: now,
  archived: false,
};

// ── Write ─────────────────────────────────────────────────────────────────────

(async () => {
  // Batched writes — 8 questions + 4 parent docs + 1 catalog update = 13 ops (well within 500 limit).
  const batch = db.batch();

  batch.set(db.doc(`quests/${quest.id}`),     quest,   { merge: true });
  batch.set(db.doc(`sections/${section.id}`), section, { merge: true });
  batch.set(db.doc(`themes/${theme.id}`),     theme,   { merge: true });
  batch.set(db.doc(`lessons/${lesson.id}`),   lesson,  { merge: true });

  for (const q of questions) {
    batch.set(db.doc(`questions/${q.id}`), q, { merge: true });
  }

  [
    syncChange('catalog', CATALOG_ID, 1),
    syncChange('quest', QUEST_ID, 2),
    syncChange('section', SECTION_ID, 3),
    syncChange('theme', THEME_ID, 4),
    syncChange('lesson', LESSON_ID, 5),
  ].forEach((change) => {
    batch.set(
      db.doc(catalogSyncChangePath(CATALOG_ID, change.type, change.id)),
      change,
    );
  });
  questions
    .map((q, index) => syncChange('question', q.id, 6 + index))
    .forEach((change) => {
      batch.set(
        db.doc(lessonContentSyncChangePath(LESSON_ID, change.id)),
        change,
      );
    });

  // Bump catalog contentsVersion so delta-sync picks up the new quest subtree.
  batch.update(db.doc(`catalogs/${CATALOG_ID}`), {
    contentsVersion: INC1,
    lastModifiedAt: now,
  });

  await batch.commit();

  console.log('Seeded lesson-runner samples:');
  console.log(`  catalog:  ${CATALOG_ID} (contentsVersion bumped)`);
  console.log(`  quest:    ${quest.id}`);
  console.log(`  section:  ${section.id}`);
  console.log(`  theme:    ${theme.id}`);
  console.log(`  lesson:   ${lesson.id} — "${lesson.title}"`);
  console.log(`  top3:     Alice 95%, Bob 87%, Charlie 72%`);
  console.log(`  rating:   avg=${lesson.averageRating} count=${lesson.ratingCount}`);
  console.log('  sync:     5 catalog sync_changes');
  console.log(`  content:  ${questions.length} lesson_content sync_changes`);
  console.log(`  questions (${questions.length}):`);
  for (const q of questions) {
    const p = JSON.parse(q.payload);
    console.log(`    ${q.id}: type=${p.type}, difficulty=${p.difficulty}`);
  }
  console.log('');
  console.log('Next step: sync app → find lesson "Тест: типы вопросов" → verify all 4 types in EASY and HARD mode.');
})()
  .catch((e) => {
    console.error(e);
    process.exitCode = 1;
  })
  .finally(() => admin.app().delete());
