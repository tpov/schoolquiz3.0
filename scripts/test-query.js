const admin = require('firebase-admin');
const serviceAccount = require('/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json');
admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
const db = admin.firestore();
(async () => {
  const snap = await db.collection('catalogs').where('lastModifiedAt', '>', 0).get();
  console.log('docs:', snap.size);
  snap.forEach(d => console.log('  ', d.id, JSON.stringify(d.data())));
})();
