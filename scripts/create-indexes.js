// Create 6 composite indexes for cascading sync queries.
const admin = require('firebase-admin');
const sa = require('/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json');
const {google} = require('googleapis');

admin.initializeApp({ credential: admin.credential.cert(sa) });
const PROJECT = sa.project_id;

const indexes = [
  // Quest Query A — own quests
  { coll: 'quests', fields: [
    { fieldPath: 'authorUid',      order: 'ASCENDING' },
    { fieldPath: 'catalogId',      order: 'ASCENDING' },
    { fieldPath: 'lastModifiedAt', order: 'ASCENDING' },
    { fieldPath: '__name__',       order: 'ASCENDING' },
  ]},
  // Quest Query B — public quests
  { coll: 'quests', fields: [
    { fieldPath: 'visibleOn',      arrayConfig: 'CONTAINS' },
    { fieldPath: 'lastModifiedAt', order: 'ASCENDING' },
    { fieldPath: '__name__',       order: 'ASCENDING' },
  ]},
  // Section by parent quests
  { coll: 'sections', fields: [
    { fieldPath: 'questId',        order: 'ASCENDING' },
    { fieldPath: 'lastModifiedAt', order: 'ASCENDING' },
    { fieldPath: '__name__',       order: 'ASCENDING' },
  ]},
  { coll: 'themes', fields: [
    { fieldPath: 'sectionId',      order: 'ASCENDING' },
    { fieldPath: 'lastModifiedAt', order: 'ASCENDING' },
    { fieldPath: '__name__',       order: 'ASCENDING' },
  ]},
  { coll: 'lessons', fields: [
    { fieldPath: 'themeId',        order: 'ASCENDING' },
    { fieldPath: 'lastModifiedAt', order: 'ASCENDING' },
    { fieldPath: '__name__',       order: 'ASCENDING' },
  ]},
  { coll: 'questions', fields: [
    { fieldPath: 'lessonId',       order: 'ASCENDING' },
    { fieldPath: 'lastModifiedAt', order: 'ASCENDING' },
    { fieldPath: '__name__',       order: 'ASCENDING' },
  ]},
];

(async () => {
  const auth = new google.auth.GoogleAuth({
    credentials: sa,
    scopes: ['https://www.googleapis.com/auth/cloud-platform'],
  });
  const client = await auth.getClient();

  for (const idx of indexes) {
    const url = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/(default)/collectionGroups/${idx.coll}/indexes`;
    const body = { queryScope: 'COLLECTION', fields: idx.fields };
    try {
      const res = await client.request({ url, method: 'POST', data: body });
      console.log(`  ${idx.coll}: ${res.data.name}`);
    } catch (e) {
      const msg = e.response?.data?.error?.message || e.message;
      if (msg.includes('already exists')) console.log(`  ${idx.coll}: already exists`);
      else console.log(`  ${idx.coll}: ERROR ${msg}`);
    }
  }
})();
