const admin = require('firebase-admin');
const path = require('path');
const sa = require('/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json');
admin.initializeApp({ credential: admin.credential.cert(sa), storageBucket: `${sa.project_id}.appspot.com` });
const bucket = admin.storage().bucket();
const ids = ['courses', 'games', 'quests', 'school', 'surveys'];
(async () => {
  for (const id of ids) {
    const src = path.resolve(__dirname, 'tmp-pics', `${id}.jpg`);
    const dst = `catalog-pictures/${id}.jpg`;
    await bucket.upload(src, { destination: dst, metadata: { contentType: 'image/jpeg' } });
    console.log('uploaded', dst);
  }
  const [files] = await bucket.getFiles({ prefix: 'catalog-pictures/' });
  console.log('now in bucket:', files.length);
})();
