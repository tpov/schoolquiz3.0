// TEMPORARY: relax read rules for sync test (allow read: if true on quests + nested).
// After test, redeploy original firestore.rules via Firebase Console or CLI.
const admin = require('firebase-admin');
const sa = require('/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json');
admin.initializeApp({ credential: admin.credential.cert(sa) });

const RULES = `rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow read: if request.auth != null && request.auth.uid == userId;
      allow write: if request.auth != null && request.auth.uid == userId
        && (!request.resource.data.diff(resource.data).affectedKeys()
            .hasAny(['admin','developer','moderator','tester','sponsor','translater','qualifications']));
    }
    match /catalogs/{catalogId}    { allow read: if true; allow write: if false; }
    match /quests/{questId}        { allow read: if true; allow write: if false; }
    match /sections/{sectionId}    { allow read: if true; allow write: if false; }
    match /themes/{themeId}        { allow read: if true; allow write: if false; }
    match /lessons/{lessonId}      { allow read: if true; allow write: if false; }
    match /questions/{questionId}  { allow read: if true; allow write: if false; }
  }
}`;

(async () => {
  const sr = admin.securityRules();
  await sr.releaseFirestoreRulesetFromSource(RULES);
  console.log('Released firestore ruleset (relaxed for tests)');
})().catch(e => { console.error(e); process.exit(1); });
