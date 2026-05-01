'use strict';
// Seed script: appends 8 lesson-runner questions to existing lesson-sample ("val и var").
// Does NOT recreate catalog/quest/section/theme/lesson.
// Does NOT delete existing question-sample (var/val, order=0).
// Usage: node scripts/seed-lesson-runner-extra.js
const admin = require('firebase-admin');
const sa = require('/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json');
admin.initializeApp({ credential: admin.credential.cert(sa) });
const db = admin.firestore();
const now = admin.firestore.Timestamp.now();
const baseMs = now.toMillis();
const INC1 = admin.firestore.FieldValue.increment(1);

const LESSON_ID  = 'lesson-sample';
const CATALOG_ID = 'courses';

const mkPayload = (obj) => JSON.stringify(obj);
const syncChange = (type, id, offset) => ({
  type,
  id,
  changedAtMs: baseMs + offset,
});

// ── 8 Questions (order 1..8, keeping order=0 for question-sample) ─────────────

const questions = [
  // 1. SingleChoice EASY
  {
    id: 'q-sc-easy',
    lessonId: LESSON_ID,
    text: 'Сколько будет 7 × 8?',
    payload: mkPayload({
      type: 'SingleChoice',
      id: 'q-sc-easy',
      difficulty: 'EASY',
      text: 'Сколько будет 7 × 8?',
      imageUrl: null,
      options: [
        { id: 'a', text: '54' },
        { id: 'b', text: '56' },
        { id: 'c', text: '58' },
        { id: 'd', text: '64' },
      ],
      correctOptionId: 'b',
    }),
    language: 'ru',
    order: 1,
    version: 1,
    lastModifiedAt: now,
    archived: false,
  },

  // 2. SingleChoice HARD
  {
    id: 'q-sc-hard',
    lessonId: LESSON_ID,
    text: 'Чему равен квадратный корень из 196?',
    payload: mkPayload({
      type: 'SingleChoice',
      id: 'q-sc-hard',
      difficulty: 'HARD',
      text: 'Чему равен квадратный корень из 196?',
      imageUrl: null,
      options: [
        { id: 'a', text: '12' },
        { id: 'b', text: '13' },
        { id: 'c', text: '14' },
        { id: 'd', text: '16' },
      ],
      correctOptionId: 'c',
    }),
    language: 'ru',
    order: 2,
    version: 1,
    lastModifiedAt: now,
    archived: false,
  },

  // 3. MultipleChoice EASY
  {
    id: 'q-mc-easy',
    lessonId: LESSON_ID,
    text: 'Какие животные относятся к млекопитающим? (выберите все верные)',
    payload: mkPayload({
      type: 'MultipleChoice',
      id: 'q-mc-easy',
      difficulty: 'EASY',
      text: 'Какие животные относятся к млекопитающим? (выберите все верные)',
      imageUrl: null,
      options: [
        { id: 'a', text: 'Кит' },
        { id: 'b', text: 'Орёл' },
        { id: 'c', text: 'Собака' },
        { id: 'd', text: 'Щука' },
        { id: 'e', text: 'Кошка' },
        { id: 'f', text: 'Лягушка' },
      ],
      correctOptionIds: ['a', 'c', 'e'],
    }),
    language: 'ru',
    order: 3,
    version: 1,
    lastModifiedAt: now,
    archived: false,
  },

  // 4. MultipleChoice HARD
  {
    id: 'q-mc-hard',
    lessonId: LESSON_ID,
    text: 'Какие из утверждений о простых числах верны? (выберите все верные)',
    payload: mkPayload({
      type: 'MultipleChoice',
      id: 'q-mc-hard',
      difficulty: 'HARD',
      text: 'Какие из утверждений о простых числах верны? (выберите все верные)',
      imageUrl: null,
      options: [
        { id: 'a', text: '1 — простое число' },
        { id: 'b', text: '2 — единственное чётное простое число' },
        { id: 'c', text: '17 — простое число' },
        { id: 'd', text: '9 — простое число' },
        { id: 'e', text: '29 — простое число' },
        { id: 'f', text: '4 — простое число' },
      ],
      correctOptionIds: ['b', 'c', 'e'],
    }),
    language: 'ru',
    order: 4,
    version: 1,
    lastModifiedAt: now,
    archived: false,
  },

  // 5. Ordering EASY
  {
    id: 'q-ord-easy',
    lessonId: LESSON_ID,
    text: 'Расставь месяцы в правильном порядке (январь → декабрь)',
    payload: mkPayload({
      type: 'Ordering',
      id: 'q-ord-easy',
      difficulty: 'EASY',
      text: 'Расставь месяцы в правильном порядке (январь → декабрь)',
      imageUrl: null,
      items: [
        { id: 'i1', text: 'Январь' },
        { id: 'i2', text: 'Март' },
        { id: 'i3', text: 'Июль' },
        { id: 'i4', text: 'Декабрь' },
      ],
    }),
    language: 'ru',
    order: 5,
    version: 1,
    lastModifiedAt: now,
    archived: false,
  },

  // 6. Ordering HARD
  {
    id: 'q-ord-hard',
    lessonId: LESSON_ID,
    text: 'Расставь события Великой Отечественной войны в хронологическом порядке',
    payload: mkPayload({
      type: 'Ordering',
      id: 'q-ord-hard',
      difficulty: 'HARD',
      text: 'Расставь события Великой Отечественной войны в хронологическом порядке',
      imageUrl: null,
      items: [
        { id: 'i1', text: 'Начало войны (1941)' },
        { id: 'i2', text: 'Битва под Москвой (1941–1942)' },
        { id: 'i3', text: 'Сталинградская битва (1942–1943)' },
        { id: 'i4', text: 'Курская битва (1943)' },
        { id: 'i5', text: 'Победа (1945)' },
      ],
    }),
    language: 'ru',
    order: 6,
    version: 1,
    lastModifiedAt: now,
    archived: false,
  },

  // 7. FillBlank EASY
  // Blank markers are "___" (three underscores) per parseTemplateParts in RunnerStateMapper.kt.
  {
    id: 'q-fb-easy',
    lessonId: LESSON_ID,
    text: 'Столица России — ___, а главная река Москвы — ___.',
    payload: mkPayload({
      type: 'FillBlank',
      id: 'q-fb-easy',
      difficulty: 'EASY',
      text: 'Столица России — ___, а главная река Москвы — ___.',
      imageUrl: null,
      blanks: [
        { id: 'b1', correctCandidateId: 'c1' },
        { id: 'b2', correctCandidateId: 'c2' },
      ],
      candidates: [
        { id: 'c1', text: 'Москва' },
        { id: 'c2', text: 'Москва-река' },
        { id: 'c3', text: 'Волга' },
        { id: 'c4', text: 'Санкт-Петербург' },
        { id: 'c5', text: 'Нева' },
      ],
    }),
    language: 'ru',
    order: 7,
    version: 1,
    lastModifiedAt: now,
    archived: false,
  },

  // 8. FillBlank HARD
  {
    id: 'q-fb-hard',
    lessonId: LESSON_ID,
    text: 'В русском языке ___ падежей, а в английском существительные имеют ___ форм числа.',
    payload: mkPayload({
      type: 'FillBlank',
      id: 'q-fb-hard',
      difficulty: 'HARD',
      text: 'В русском языке ___ падежей, а в английском существительные имеют ___ форм числа.',
      imageUrl: null,
      blanks: [
        { id: 'b1', correctCandidateId: 'c2' },
        { id: 'b2', correctCandidateId: 'c5' },
      ],
      candidates: [
        { id: 'c1', text: '5' },
        { id: 'c2', text: '6' },
        { id: 'c3', text: '7' },
        { id: 'c4', text: '1' },
        { id: 'c5', text: '2' },
        { id: 'c6', text: '3' },
      ],
    }),
    language: 'ru',
    order: 8,
    version: 1,
    lastModifiedAt: now,
    archived: false,
  },
];

// ── Lesson update (merge — keeps existing title/themeId/order) ────────────────

const lessonUpdate = {
  contentsVersion: 1 + questions.length, // 1 existing + 8 new = 9
  lastModifiedAt: now,
  averageRating: 2.5,
  ratingCount: 4,
  top3: [
    { nickname: 'Alice',   avatarUrl: null, percent: 95 },
    { nickname: 'Bob',     avatarUrl: null, percent: 87 },
    { nickname: 'Charlie', avatarUrl: null, percent: 72 },
  ],
};

// ── Write ─────────────────────────────────────────────────────────────────────

(async () => {
  // 8 questions + 1 lesson update + 1 catalog bump = 10 ops.
  const batch = db.batch();

  batch.set(db.doc(`lessons/${LESSON_ID}`), lessonUpdate, { merge: true });

  for (const q of questions) {
    batch.set(db.doc(`questions/${q.id}`), q, { merge: true });
  }

  [
    syncChange('catalog', CATALOG_ID, 1),
    syncChange('lesson', LESSON_ID, 2),
  ].forEach((change) => {
    batch.set(
      db.doc(`catalogs/${CATALOG_ID}/sync_changes/${change.changedAtMs}-${change.type}-${change.id}`),
      change,
    );
  });

  questions
    .map((q, index) => syncChange('question', q.id, 3 + index))
    .forEach((change) => {
      batch.set(
        db.doc(`lesson_content/${LESSON_ID}/sync_changes/${change.changedAtMs}-${change.type}-${change.id}`),
        change,
      );
    });

  // Bump catalog so delta-sync picks up the updated lesson subtree.
  batch.update(db.doc(`catalogs/${CATALOG_ID}`), {
    contentsVersion: INC1,
    lastModifiedAt: now,
  });

  await batch.commit();

  console.log('Appended extra questions to lesson-sample:');
  console.log(`  lesson:   ${LESSON_ID} — "val и var" (contentsVersion → ${lessonUpdate.contentsVersion})`);
  console.log(`  top3:     Alice 95%, Bob 87%, Charlie 72%`);
  console.log(`  rating:   avg=${lessonUpdate.averageRating} count=${lessonUpdate.ratingCount}`);
  console.log('  sync:     2 catalog sync_changes');
  console.log(`  content:  ${questions.length} lesson_content sync_changes`);
  console.log(`  questions (${questions.length} new, 1 existing preserved):`);
  for (const q of questions) {
    const p = JSON.parse(q.payload);
    console.log(`    ${q.id}: type=${p.type}, difficulty=${p.difficulty}, order=${q.order}`);
  }
  console.log('');
  console.log('Next step: sync app → открой урок "val и var" → проверь 8 вопросов (4 типа, EASY зелёный / HARD красный).');
})()
  .catch((e) => {
    console.error(e);
    process.exitCode = 1;
  })
  .finally(() => admin.app().delete());
