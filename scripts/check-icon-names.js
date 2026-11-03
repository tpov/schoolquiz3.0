const admin = require('firebase-admin');
const sa = require('/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json');
admin.initializeApp({ credential: admin.credential.cert(sa) });
const db = admin.firestore();

(async () => {
  const snap = await db.collection('catalogs').get();
  snap.forEach(d => {
    const data = d.data();
    const fields = Object.keys(data).sort();
    console.log(`${d.id}:`);
    console.log(`  fields: ${fields.join(', ')}`);
    console.log(`  iconNames: ${JSON.stringify(data.iconNames)}`);
    console.log(`  version: ${data.version}`);
    console.log(`  lastModifiedAt: ${data.lastModifiedAt?.toDate()?.toISOString()}`);
  });
  process.exit(0);
})();
