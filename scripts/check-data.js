const admin = require('firebase-admin');
const sa = require('/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json');
admin.initializeApp({ credential: admin.credential.cert(sa) });
const db = admin.firestore();
(async () => {
  const all = await db.collection('catalogs').get();
  console.log('Total:', all.size);
  all.forEach(d => {
    const data = d.data();
    const lmType = data.lastModifiedAt?.constructor?.name || typeof data.lastModifiedAt;
    console.log(' ', d.id, 'lmType=', lmType, 'value=', JSON.stringify(data.lastModifiedAt));
  });
  console.log('---query: whereGreaterThan(lastModifiedAt, Timestamp(0,0))---');
  const q = await db.collection('catalogs').where('lastModifiedAt', '>', admin.firestore.Timestamp.fromMillis(0)).get();
  console.log('Query size:', q.size);
})();
