const admin = require('firebase-admin');
const sa = require('/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json');
admin.initializeApp({ credential: admin.credential.cert(sa) });
const db = admin.firestore();
(async () => {
  const snap = await db.collection('catalogs').get();
  const batch = db.batch();
  const now = admin.firestore.Timestamp.now();
  snap.forEach(d => batch.update(d.ref, { lastModifiedAt: now }));
  await batch.commit();
  console.log('bumped', snap.size, 'catalogs');
})();
