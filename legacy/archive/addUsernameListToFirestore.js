const admin = require('firebase-admin');
const serviceAccount = require('./serviceAccountKey.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

const symbolsNot = [
  "/", "\\", "|", "<", ">", ":", "\"", "'", "`", "~", ",", ".", ";", "?", "!", "(", ")", "[", "]", "{", "}", 
  "$", "€", "£", "¥", "@", "#", "%", "^", "&", "*", "=", "+", "\n", "\t"
];

async function addUsernameList() {
  await db.collection('variable').doc('namingRules').set(
    { usernameList: symbolsNot },
    { merge: true }
  );
  console.log('usernameList успешно добавлен!');
  process.exit(0);
}

addUsernameList().catch(e => {
  console.error(e);
  process.exit(1);
}); 