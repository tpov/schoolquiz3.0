const admin = require('firebase-admin');
const sa = require('/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json');
admin.initializeApp({ credential: admin.credential.cert(sa) });
(async () => {
  // try both common bucket names
  for (const name of [`${sa.project_id}.appspot.com`, `${sa.project_id}.firebasestorage.app`]) {
    try {
      const b = admin.storage().bucket(name);
      const [files] = await b.getFiles({ maxResults: 30 });
      console.log(`bucket: ${name} — ${files.length} files`);
      files.slice(0, 15).forEach(f => console.log('  ', f.name));
    } catch (e) {
      console.log(`bucket: ${name} — ERROR: ${e.message.split('\n')[0]}`);
    }
  }
})();
