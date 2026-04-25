const admin = require('firebase-admin');
const serviceAccount = require('/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json');

admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
const db = admin.firestore();
const Timestamp = admin.firestore.Timestamp;
const now = Timestamp.now();

async function backfillCatalogs() {
  const snap = await db.collection('catalogs').get();
  const batch = db.batch();
  let count = 0;
  snap.forEach(doc => {
    const d = doc.data();
    const update = {};
    // client expects Firestore Timestamp, not Number
    if (!(d.lastModifiedAt instanceof Timestamp)) update.lastModifiedAt = now;
    if (d.version === undefined) update.version = 1;
    if (d.contentsVersion === undefined) update.contentsVersion = 0;
    if (d.archived === undefined) update.archived = false;
    if (Object.keys(update).length > 0) {
      batch.update(doc.ref, update);
      count++;
      console.log(`  ${doc.id}: updating ${Object.keys(update).join(', ')}`);
    } else {
      console.log(`  ${doc.id}: OK`);
    }
  });
  if (count > 0) { await batch.commit(); console.log(`\nUpdated ${count} documents.`); }
  else console.log('\nAll catalogs OK.');
}
backfillCatalogs().catch(e => { console.error(e); process.exit(1); });
