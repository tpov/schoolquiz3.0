'use strict';

/**
 * К какому каталогу относится узел содержимого.
 *
 * Журнал изменений живёт под каталогом (`catalogs/{catalogId}/sync_changes/...`), а сами узлы
 * каталог не хранят: квест знает свой `catalogId`, а секция знает только квест, тема — секцию,
 * урок — тему, вопрос — урок. Чтобы записать изменение узла в журнал, цепочку надо пройти вверх.
 *
 * Строится один раз на весь прогон скрипта: пять запросов вместо обхода по одному узлу.
 */

/** Порядок соответствует пути вверх: вопрос → урок → тема → секция → квест → каталог. */
const PARENT_FIELD = {
  sections: 'questId',
  themes: 'sectionId',
  lessons: 'themeId',
  questions: 'lessonId',
};

/**
 * Читает иерархию и возвращает функцию `catalogIdOf(collection, docId)`.
 *
 * Узел, чью цепочку не удалось пройти до конца — оборванная ссылка или удалённый родитель, —
 * даёт `null`. Это не повод падать: такие узлы в базе есть, и молча приписать их чужому каталогу
 * было бы хуже, чем честно пропустить и сказать сколько.
 */
async function buildCatalogIndex(db) {
  const [quests, sections, themes, lessons] = await Promise.all([
    db.collection('quests').get(),
    db.collection('sections').get(),
    db.collection('themes').get(),
    db.collection('lessons').get(),
  ]);

  const questCatalog = new Map();
  quests.docs.forEach((doc) => {
    const catalogId = String((doc.data() || {}).catalogId || '');
    if (catalogId) questCatalog.set(doc.id, catalogId);
  });

  const parentOf = {
    sections: mapParents(sections, PARENT_FIELD.sections),
    themes: mapParents(themes, PARENT_FIELD.themes),
    lessons: mapParents(lessons, PARENT_FIELD.lessons),
  };

  function catalogIdOf(collection, docId, data) {
    if (collection === 'quests') return questCatalog.get(docId) || null;
    if (collection === 'sections') return questCatalog.get(parentOf.sections.get(docId)) || null;
    if (collection === 'themes') {
      const sectionId = parentOf.themes.get(docId);
      return catalogIdOf('sections', sectionId) || null;
    }
    if (collection === 'lessons') {
      const themeId = parentOf.lessons.get(docId);
      return catalogIdOf('themes', themeId) || null;
    }
    if (collection === 'questions') {
      // Вопросы не читаются целиком заранее: их больше всего, а родитель приходит вместе с
      // самим документом, когда скрипт по ним и так идёт.
      const lessonId = String((data || {})[PARENT_FIELD.questions] || '');
      return lessonId ? catalogIdOf('lessons', lessonId) : null;
    }
    return null;
  }

  return catalogIdOf;
}

function mapParents(snapshot, field) {
  const map = new Map();
  snapshot.docs.forEach((doc) => {
    const parent = String((doc.data() || {})[field] || '');
    if (parent) map.set(doc.id, parent);
  });
  return map;
}

/** Тип узла в журнале — единственное число от имени коллекции. */
function syncTypeOf(collection) {
  return collection.replace(/s$/, '');
}

module.exports = {buildCatalogIndex, syncTypeOf};
