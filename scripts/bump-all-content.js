const admin = require('firebase-admin');
const sa = require('/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json');
admin.initializeApp({ credential: admin.credential.cert(sa) });
const db = admin.firestore();

const collections = ['quests', 'sections', 'themes', 'lessons', 'questions'];

async function bumpCollection(name) {
  const snap = await db.collection(name).get();
  if (snap.empty) {
    console.log(`${name}: empty, skipped`);
    return 0;
  }
  const now = admin.firestore.Timestamp.now();
  const docs = snap.docs;
  let total = 0;
  // Firestore batches are limited to 500 ops. Chunk for safety.
  for (let i = 0; i < docs.length; i += 400) {
    const chunk = docs.slice(i, i + 400);
    const batch = db.batch();
    chunk.forEach((d) => {
      const data = d.data();
      const newVersion = (typeof data.version === 'number' ? data.version : 0) + 1;
      batch.update(d.ref, { version: newVersion, lastModifiedAt: now });
    });
    await batch.commit();
    total += chunk.length;
  }
  console.log(`${name}: bumped ${total}`);
  return total;
}

(async () => {
  for (const c of collections) {
    await bumpCollection(c);
  }
  process.exit(0);
})();
