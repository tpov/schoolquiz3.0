const admin = require('firebase-admin');
const sa = require('/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json');
admin.initializeApp({ credential: admin.credential.cert(sa) });
const db = admin.firestore();

// Names must exist in the client whitelist registry
// (android/core/designsystem/.../MaterialIconRegistry.kt).
const seeds = {
  games: [
    'VideogameAsset', 'SportsEsports', 'Casino', 'Extension',
    'EmojiEvents', 'Star', 'Diamond', 'LocalFireDepartment',
  ],
  quests: [
    'Map', 'Place', 'Explore', 'Flag',
    'EmojiEvents', 'Star', 'Diamond', 'WorkspacePremium',
  ],
  school: [
    'AutoStories', 'MenuBook', 'Book', 'Edit', 'EditNote', 'Create',
    'Calculate', 'Functions', 'Science', 'Biotech',
    'Psychology', 'School', 'Backpack', 'Article',
    'Translate', 'Language', 'History', 'Lightbulb', 'Star',
  ],
  surveys: [
    'Poll', 'BarChart', 'PieChart', 'Insights',
    'ChecklistRtl', 'FactCheck', 'Quiz', 'QuestionAnswer',
    'Forum', 'CommentBank', 'ListAlt', 'ThumbUp', 'Reviews',
  ],
  courses: [
    'School', 'MenuBook', 'AutoStories', 'OndemandVideo',
    'CastForEducation', 'Lightbulb', 'WorkspacePremium',
    'EditNote', 'Article', 'Backpack',
  ],
};

(async () => {
  const snap = await db.collection('catalogs').get();
  const batch = db.batch();
  const now = admin.firestore.Timestamp.now();
  let updated = 0;
  snap.forEach(d => {
    const icons = seeds[d.id];
    if (!icons) {
      console.log(`  ${d.id}: no seed defined, skipped`);
      return;
    }
    const data = d.data();
    const newVersion = (typeof data.version === 'number' ? data.version : 0) + 1;
    batch.update(d.ref, {
      iconNames: icons,
      version: newVersion,
      lastModifiedAt: now,
    });
    console.log(`  ${d.id}: iconNames=${icons.length} items, version ${data.version} -> ${newVersion}`);
    updated += 1;
  });
  await batch.commit();
  console.log(`updated ${updated} catalogs`);
  process.exit(0);
})();
