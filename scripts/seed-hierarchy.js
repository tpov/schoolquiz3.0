const admin = require('firebase-admin');
const sa = require('/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json');
admin.initializeApp({ credential: admin.credential.cert(sa) });
const db = admin.firestore();
const T = admin.firestore.Timestamp;
const now = T.now();
const baseMs = now.toMillis();
const AUTHOR = 'seed-author-uid';

function syncChange(type, id, offset) {
  return {
    type,
    id,
    changedAtMs: baseMs + offset,
  };
}

(async () => {
  const quest = {
    id: 'quest-sample',
    catalogId: 'courses',
    authorUid: AUTHOR,
    title: 'Основы Kotlin',
    picturePath: null,
    visibleOn: ['archive'],
    averageRating: null,
    averageRatingCount: 0,
    version: 1,
    contentsVersion: 1,
    lastModifiedAt: now,
    archived: true,
  };
  const section = {
    id: 'section-sample',
    questId: quest.id,
    title: 'Синтаксис',
    order: 0,
    version: 1,
    contentsVersion: 1,
    lastModifiedAt: now,
    archived: false,
  };
  const theme = {
    id: 'theme-sample',
    sectionId: section.id,
    title: 'Переменные и типы',
    order: 0,
    version: 1,
    contentsVersion: 1,
    lastModifiedAt: now,
    archived: false,
  };
  const lesson = {
    id: 'lesson-sample',
    themeId: theme.id,
    title: 'val и var',
    order: 0,
    version: 1,
    contentsVersion: 1,
    lastModifiedAt: now,
    archived: false,
  };
  const question = {
    id: 'question-sample',
    lessonId: lesson.id,
    text: 'В чём разница между val и var?',
    payload: JSON.stringify({
      type: 'single-choice',
      options: ['val — изменяемая', 'var — неизменяемая', 'val — read-only, var — mutable', 'Никакой'],
      correctIndex: 2,
    }),
    language: 'ru',
    order: 0,
    version: 1,
    lastModifiedAt: now,
    archived: false,
  };

  // Bump parent contentsVersion + lastModifiedAt for cascade recurse predicate
  const catalogUpd = {
    contentsVersion: admin.firestore.FieldValue.increment(1),
    lastModifiedAt: now,
  };

  const batch = db.batch();
  batch.set(db.doc('quests/' + quest.id), quest);
  batch.set(db.doc('sections/' + section.id), section);
  batch.set(db.doc('themes/' + theme.id), theme);
  batch.set(db.doc('lessons/' + lesson.id), lesson);
  batch.set(db.doc('questions/' + question.id), question);
  [
    syncChange('catalog', quest.catalogId, 1),
    syncChange('quest', quest.id, 2),
    syncChange('section', section.id, 3),
    syncChange('theme', theme.id, 4),
    syncChange('lesson', lesson.id, 5),
  ].forEach((change) => {
    batch.set(
      db.doc(
        `catalogs/${quest.catalogId}/sync_changes/${change.changedAtMs}-${change.type}-${change.id}`,
      ),
      change,
    );
  });
  const questionChange = syncChange('question', question.id, 6);
  batch.set(
    db.doc(
      `lesson_content/${lesson.id}/sync_changes/${questionChange.changedAtMs}-${questionChange.type}-${questionChange.id}`,
    ),
    questionChange,
  );
  batch.update(db.doc('catalogs/' + quest.catalogId), catalogUpd);
  await batch.commit();

  console.log('Seeded hierarchy:');
  console.log('  catalog:  courses (contentsVersion bumped)');
  console.log('  quest:    ' + quest.id + ' [authorUid=' + quest.authorUid + ', visibleOn=' + quest.visibleOn.join(',') + ']');
  console.log('  section:  ' + section.id + ' (questId=' + section.questId + ')');
  console.log('  theme:    ' + theme.id + ' (sectionId=' + theme.sectionId + ')');
  console.log('  lesson:   ' + lesson.id + ' (themeId=' + lesson.themeId + ')');
  console.log('  question: ' + question.id + ' (lessonId=' + question.lessonId + ')');
})().catch(e => { console.error(e); process.exit(1); });
