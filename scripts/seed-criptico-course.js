'use strict';

// Seed script: creates an archived on-demand course in the Courses catalog.
// Usage: node scripts/seed-criptico-course.js
const admin = require('firebase-admin');
const sa = require('/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json');

admin.initializeApp({ credential: admin.credential.cert(sa) });

const db = admin.firestore();
const now = admin.firestore.Timestamp.now();
const baseMs = now.toMillis();
const INC1 = admin.firestore.FieldValue.increment(1);
const VERSION = baseMs;
const AUTHOR = 'seed-author-uid';

const CATALOG_ID = 'courses';
const QUEST_ID = 'quest-criptico';
const SECTION_ID = 'section-criptico-basics';
const THEME_ID = 'theme-criptico-blockchain';
const LESSON_ID = 'lesson-criptico-intro';

const mkPayload = (obj) => JSON.stringify(obj);
const syncChange = (type, id, offset) => ({
  type,
  id,
  changedAtMs: baseMs + offset,
});

const quest = {
  id: QUEST_ID,
  catalogId: CATALOG_ID,
  authorUid: AUTHOR,
  title: 'Criptico',
  picturePath: null,
  visibleOn: ['archive'],
  averageRating: null,
  averageRatingCount: 0,
  version: VERSION,
  contentsVersion: VERSION,
  lastModifiedAt: now,
  archived: true,
};

const section = {
  id: SECTION_ID,
  questId: QUEST_ID,
  title: 'Криптовалюта без мифов',
  order: 0,
  version: VERSION,
  contentsVersion: VERSION,
  lastModifiedAt: now,
  archived: false,
};

const theme = {
  id: THEME_ID,
  sectionId: SECTION_ID,
  title: 'Блокчейн, кошельки и безопасность',
  order: 0,
  version: VERSION,
  contentsVersion: VERSION,
  lastModifiedAt: now,
  archived: false,
};

const lesson = {
  id: LESSON_ID,
  themeId: THEME_ID,
  title: 'Основы крипты',
  order: 0,
  version: VERSION,
  contentsVersion: VERSION,
  lastModifiedAt: now,
  archived: false,
  averageRating: 4.6,
  ratingCount: 12,
  top3: [
    { nickname: 'SatoshiKid', avatarUrl: null, percent: 98 },
    { nickname: 'BlockNova', avatarUrl: null, percent: 91 },
    { nickname: 'HashPilot', avatarUrl: null, percent: 84 },
  ],
};

const questions = [
  {
    id: 'criptico-q-sc-easy-blockchain',
    lessonId: LESSON_ID,
    text: 'Что такое блокчейн?',
    payload: mkPayload({
      type: 'SingleChoice',
      id: 'criptico-q-sc-easy-blockchain',
      difficulty: 'EASY',
      text: 'Что такое блокчейн?',
      imageUrl: null,
      options: [
        { id: 'a', text: 'Обычная таблица на одном сервере' },
        { id: 'b', text: 'Цепочка блоков с записями, которую проверяет сеть' },
        { id: 'c', text: 'Пароль от криптокошелька' },
        { id: 'd', text: 'Название криптобиржи' },
      ],
      correctOptionId: 'b',
      info: 'Блокчейн хранит данные блоками. Каждый новый блок связан с предыдущим, а участники сети проверяют корректность записей.',
    }),
    language: 'ru',
    order: 0,
    version: VERSION,
    lastModifiedAt: now,
    archived: false,
  },
  {
    id: 'criptico-q-sc-hard-private-key',
    lessonId: LESSON_ID,
    text: 'Зачем нужен приватный ключ?',
    payload: mkPayload({
      type: 'SingleChoice',
      id: 'criptico-q-sc-hard-private-key',
      difficulty: 'HARD',
      text: 'Зачем нужен приватный ключ?',
      imageUrl: null,
      options: [
        { id: 'a', text: 'Чтобы подписывать транзакции и подтверждать владение средствами' },
        { id: 'b', text: 'Чтобы узнать цену монеты' },
        { id: 'c', text: 'Чтобы ускорить интернет' },
        { id: 'd', text: 'Чтобы удалить блокчейн' },
      ],
      correctOptionId: 'a',
      info: 'Приватный ключ доказывает право распоряжаться средствами. Его нельзя показывать другим людям: кто знает ключ, тот может подписать перевод.',
    }),
    language: 'ru',
    order: 1,
    version: VERSION,
    lastModifiedAt: now,
    archived: false,
  },
  {
    id: 'criptico-q-mc-easy-wallet',
    lessonId: LESSON_ID,
    text: 'Что связано с криптокошельком? Выберите все верные варианты.',
    payload: mkPayload({
      type: 'MultipleChoice',
      id: 'criptico-q-mc-easy-wallet',
      difficulty: 'EASY',
      text: 'Что связано с криптокошельком? Выберите все верные варианты.',
      imageUrl: null,
      options: [
        { id: 'a', text: 'Адрес кошелька' },
        { id: 'b', text: 'Seed-фраза' },
        { id: 'c', text: 'Приватный ключ' },
        { id: 'd', text: 'Номер паспорта' },
        { id: 'e', text: 'Пароль от Wi-Fi' },
      ],
      correctOptionIds: ['a', 'b', 'c'],
      info: 'У кошелька есть адрес для получения средств, а seed-фраза и приватные ключи дают доступ к управлению активами.',
    }),
    language: 'ru',
    order: 2,
    version: VERSION,
    lastModifiedAt: now,
    archived: false,
  },
  {
    id: 'criptico-q-mc-hard-security',
    lessonId: LESSON_ID,
    text: 'Что помогает безопаснее хранить криптовалюту?',
    payload: mkPayload({
      type: 'MultipleChoice',
      id: 'criptico-q-mc-hard-security',
      difficulty: 'HARD',
      text: 'Что помогает безопаснее хранить криптовалюту?',
      imageUrl: null,
      options: [
        { id: 'a', text: 'Аппаратный кошелёк' },
        { id: 'b', text: 'Резервная копия seed-фразы офлайн' },
        { id: 'c', text: 'Публикация seed-фразы в чате' },
        { id: 'd', text: 'Проверка адреса перед переводом' },
        { id: 'e', text: 'Один пароль для всех сервисов' },
      ],
      correctOptionIds: ['a', 'b', 'd'],
      info: 'Безопаснее хранить ключи офлайн, проверять адреса и использовать отдельные защищённые устройства. Seed-фразу нельзя отправлять в чаты.',
    }),
    language: 'ru',
    order: 3,
    version: VERSION,
    lastModifiedAt: now,
    archived: false,
  },
  {
    id: 'criptico-q-ord-easy-transaction',
    lessonId: LESSON_ID,
    text: 'Расставь шаги простой криптотранзакции по порядку.',
    payload: mkPayload({
      type: 'Ordering',
      id: 'criptico-q-ord-easy-transaction',
      difficulty: 'EASY',
      text: 'Расставь шаги простой криптотранзакции по порядку.',
      imageUrl: null,
      items: [
        { id: 'i1', text: 'Создать транзакцию' },
        { id: 'i2', text: 'Подписать её приватным ключом' },
        { id: 'i3', text: 'Отправить в сеть' },
        { id: 'i4', text: 'Дождаться подтверждения' },
      ],
      info: 'Сначала транзакцию создают, затем подписывают, отправляют в сеть и ждут подтверждения в блокчейне.',
    }),
    language: 'ru',
    order: 4,
    version: VERSION,
    lastModifiedAt: now,
    archived: false,
  },
  {
    id: 'criptico-q-ord-hard-block',
    lessonId: LESSON_ID,
    text: 'Расставь события вокруг подтверждения транзакции.',
    payload: mkPayload({
      type: 'Ordering',
      id: 'criptico-q-ord-hard-block',
      difficulty: 'HARD',
      text: 'Расставь события вокруг подтверждения транзакции.',
      imageUrl: null,
      items: [
        { id: 'i1', text: 'Транзакция попадает в очередь сети' },
        { id: 'i2', text: 'Валидатор или майнер включает её в блок' },
        { id: 'i3', text: 'Сеть проверяет новый блок' },
        { id: 'i4', text: 'Блок добавляется в цепочку' },
        { id: 'i5', text: 'Количество подтверждений растёт' },
      ],
      info: 'Транзакция сначала ожидает включения в блок. После проверки блок становится частью цепочки, а новые блоки сверху увеличивают число подтверждений.',
    }),
    language: 'ru',
    order: 5,
    version: VERSION,
    lastModifiedAt: now,
    archived: false,
  },
  {
    id: 'criptico-q-fb-easy-btc',
    lessonId: LESSON_ID,
    text: 'Биткоин появился в ___ году, его тикер — ___.',
    payload: mkPayload({
      type: 'FillBlank',
      id: 'criptico-q-fb-easy-btc',
      difficulty: 'EASY',
      text: 'Биткоин появился в ___ году, его тикер — ___.',
      imageUrl: null,
      blanks: [
        { id: 'b1', correctCandidateId: 'c1' },
        { id: 'b2', correctCandidateId: 'c2' },
      ],
      candidates: [
        { id: 'c1', text: '2009' },
        { id: 'c2', text: 'BTC' },
        { id: 'c3', text: '2019' },
        { id: 'c4', text: 'ETH' },
        { id: 'c5', text: 'USD' },
      ],
      info: 'Сеть Bitcoin запущена в 2009 году. Самый распространённый тикер биткоина — BTC.',
    }),
    language: 'ru',
    order: 6,
    version: VERSION,
    lastModifiedAt: now,
    archived: false,
  },
  {
    id: 'criptico-q-fb-hard-ethereum',
    lessonId: LESSON_ID,
    text: 'Смарт-контракты чаще связывают с ___, а комиссии в Ethereum называют ___.',
    payload: mkPayload({
      type: 'FillBlank',
      id: 'criptico-q-fb-hard-ethereum',
      difficulty: 'HARD',
      text: 'Смарт-контракты чаще связывают с ___, а комиссии в Ethereum называют ___.',
      imageUrl: null,
      blanks: [
        { id: 'b1', correctCandidateId: 'c1' },
        { id: 'b2', correctCandidateId: 'c2' },
      ],
      candidates: [
        { id: 'c1', text: 'Ethereum' },
        { id: 'c2', text: 'gas' },
        { id: 'c3', text: 'Bitcoin' },
        { id: 'c4', text: 'hash' },
        { id: 'c5', text: 'seed' },
        { id: 'c6', text: 'mining' },
      ],
      info: 'Ethereum стал одной из главных платформ для смарт-контрактов. Комиссии за операции в этой сети обычно называют gas.',
    }),
    language: 'ru',
    order: 7,
    version: VERSION,
    lastModifiedAt: now,
    archived: false,
  },
];

(async () => {
  const batch = db.batch();

  batch.set(db.doc(`quests/${QUEST_ID}`), quest, { merge: true });
  batch.set(db.doc(`sections/${SECTION_ID}`), section, { merge: true });
  batch.set(db.doc(`themes/${THEME_ID}`), theme, { merge: true });
  batch.set(db.doc(`lessons/${LESSON_ID}`), lesson, { merge: true });

  for (const question of questions) {
    batch.set(db.doc(`questions/${question.id}`), question, { merge: true });
  }

  [
    syncChange('catalog', CATALOG_ID, 1),
    syncChange('quest', QUEST_ID, 2),
    syncChange('section', SECTION_ID, 3),
    syncChange('theme', THEME_ID, 4),
    syncChange('lesson', LESSON_ID, 5),
  ].forEach((change) => {
    batch.set(
      db.doc(`catalogs/${CATALOG_ID}/sync_changes/${change.changedAtMs}-${change.type}-${change.id}`),
      change,
    );
  });

  questions
    .map((question, index) => syncChange('question', question.id, 6 + index))
    .forEach((change) => {
      batch.set(
        db.doc(`lesson_content/${LESSON_ID}/sync_changes/${change.changedAtMs}-${change.type}-${change.id}`),
        change,
      );
    });

  batch.set(
    db.doc(`catalogs/${CATALOG_ID}`),
    {
      contentsVersion: INC1,
      lastModifiedAt: now,
    },
    { merge: true },
  );

  await batch.commit();

  console.log('Seeded Criptico course:');
  console.log(`  catalog:  ${CATALOG_ID} / archive`);
  console.log(`  quest:    ${QUEST_ID} — "${quest.title}"`);
  console.log(`  section:  ${SECTION_ID}`);
  console.log(`  theme:    ${THEME_ID}`);
  console.log(`  lesson:   ${LESSON_ID} — "${lesson.title}"`);
  console.log(`  sync:     5 catalog sync_changes`);
  console.log(`  content:  ${questions.length} lesson_content sync_changes`);
  console.log(`  version:  ${VERSION}`);
})()
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  })
  .finally(() => admin.app().delete());
