const admin = require('firebase-admin');
const sa = require('/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json');
const {catalogSyncChangePath, lessonContentSyncChangePath} = require('./sync-change-id');
const {buildCatalogIndex, syncTypeOf} = require('./content-catalog-index');
admin.initializeApp({ credential: admin.credential.cert(sa) });
const db = admin.firestore();

const collections = ['quests', 'sections', 'themes', 'lessons', 'questions'];

/**
 * Поднимает версию у всего содержимого — и сообщает об этом клиентам.
 *
 * Раньше скрипт правил только сами узлы. Клиент узнаёт об изменениях единственным способом — из
 * журнала `sync_changes` (AD-11), поэтому подъём версии без записи в журнал был невидимым: у
 * всех, кто уже синхронизировался, содержимое оставалось прежним навсегда, а курсор не двигался,
 * потому что двигать было нечего.
 */
async function bumpCollection(name, catalogIdOf, missing) {
  const snap = await db.collection(name).get();
  if (snap.empty) {
    console.log(`${name}: empty, skipped`);
    return 0;
  }
  const now = admin.firestore.Timestamp.now();
  const nowMs = now.toMillis();
  const type = syncTypeOf(name);
  const docs = snap.docs;
  let total = 0;
  // Firestore batches are limited to 500 ops. Each node now costs two writes — the node and its
  // journal entry, and a question costs three — so the chunk is smaller than it was.
  for (let i = 0; i < docs.length; i += 150) {
    const chunk = docs.slice(i, i + 150);
    const batch = db.batch();
    chunk.forEach((d) => {
      const data = d.data();
      const newVersion = (typeof data.version === 'number' ? data.version : 0) + 1;
      batch.update(d.ref, { version: newVersion, lastModifiedAt: now });

      const catalogId = catalogIdOf(name, d.id, data);
      if (!catalogId) {
        // Оборванная цепочка вверх: родителя нет. Записать изменение некуда, и приписать узел
        // чужому каталогу хуже, чем пропустить и сказать об этом.
        missing.push(`${name}/${d.id}`);
        return;
      }
      batch.set(
        db.doc(catalogSyncChangePath(catalogId, type, d.id)),
        { type, id: d.id, changedAtMs: nowMs },
        { merge: true },
      );
      if (name === 'questions') {
        // Вопрос читается двумя журналами: каталожным и журналом своего урока.
        const lessonId = String(data.lessonId || '');
        if (lessonId) {
          batch.set(
            db.doc(lessonContentSyncChangePath(lessonId, d.id)),
            { type, id: d.id, changedAtMs: nowMs },
            { merge: true },
          );
        }
      }
    });
    await batch.commit();
    total += chunk.length;
  }
  console.log(`${name}: bumped ${total}`);
  return total;
}

(async () => {
  const catalogIdOf = await buildCatalogIndex(db);
  const missing = [];
  for (const c of collections) {
    await bumpCollection(c, catalogIdOf, missing);
  }
  if (missing.length > 0) {
    // Громко, а не в тишину: эти узлы изменены, но ни один клиент об этом не узнает.
    console.warn(`\n${missing.length} nodes have no reachable catalog and got no journal entry:`);
    missing.slice(0, 20).forEach((item) => console.warn(`  ${item}`));
    if (missing.length > 20) console.warn(`  ... and ${missing.length - 20} more`);
  }
  process.exit(0);
})();
