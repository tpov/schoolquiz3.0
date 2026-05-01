'use strict';

const fs = require('fs');
const admin = require('firebase-admin');

const SERVICE_ACCOUNT_PATH =
  process.env.FIREBASE_SERVICE_ACCOUNT ||
  '/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json';
const RULES_PATH = process.env.FIRESTORE_RULES_PATH || 'firestore.rules';

if (!fs.existsSync(SERVICE_ACCOUNT_PATH)) {
  console.error(`Service account file not found: ${SERVICE_ACCOUNT_PATH}`);
  process.exit(1);
}

if (!fs.existsSync(RULES_PATH)) {
  console.error(`Firestore rules file not found: ${RULES_PATH}`);
  process.exit(1);
}

admin.initializeApp({ credential: admin.credential.cert(require(SERVICE_ACCOUNT_PATH)) });

(async () => {
  const source = fs.readFileSync(RULES_PATH, 'utf8');
  await admin.securityRules().releaseFirestoreRulesetFromSource(source);
  console.log(`Released Firestore rules from ${RULES_PATH}`);
})()
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  })
  .finally(() => admin.app().delete());
