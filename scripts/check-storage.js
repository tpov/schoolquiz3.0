const admin = require('firebase-admin');
const sa = require('/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json');
admin.initializeApp({ credential: admin.credential.cert(sa), storageBucket: `${sa.project_id}.appspot.com` });
const bucket = admin.storage().bucket();
(async () => {
  const [files] = await bucket.getFiles({ prefix: 'catalog-pictures/' });
  console.log(`bucket: ${bucket.name}, files under catalog-pictures/:`, files.length);
  files.forEach(f => console.log('  ', f.name, 'size:', f.metadata?.size || '?'));
  if (files.length === 0) {
    console.log('\n--- listing bucket root ---');
    const [all] = await bucket.getFiles({ maxResults: 20 });
    all.forEach(f => console.log('  ', f.name));
  }
})();
