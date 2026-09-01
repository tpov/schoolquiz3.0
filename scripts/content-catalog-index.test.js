'use strict';

/**
 * Цепочка вверх до каталога.
 *
 * Журнал изменений живёт под каталогом, а узлы содержимого каталог не хранят: квест знает свой,
 * а всё ниже знает только родителя. Ошибка здесь означает изменение, записанное в чужой журнал
 * или не записанное никуда, — и то и другое клиент увидит как «ничего не поменялось».
 */

const assert = require('assert');
const {buildCatalogIndex, syncTypeOf} = require('./content-catalog-index');

/** Хватает того, что читает индекс: коллекция документов с id и data(). */
function fakeDb(byCollection) {
  return {
    collection(name) {
      return {
        async get() {
          const docs = Object.entries(byCollection[name] || {}).map(([id, data]) => ({
            id,
            data: () => data,
          }));
          return {docs};
        },
      };
    },
  };
}

const DB = fakeDb({
  quests: {'q-1': {catalogId: 'courses'}, 'q-2': {catalogId: 'home'}, 'q-orphan': {}},
  sections: {'s-1': {questId: 'q-1'}, 's-2': {questId: 'q-2'}, 's-lost': {questId: 'q-gone'}},
  themes: {'t-1': {sectionId: 's-1'}, 't-lost': {sectionId: 's-lost'}},
  lessons: {'l-1': {themeId: 't-1'}, 'l-lost': {themeId: 't-lost'}},
});

async function testEveryLevelResolvesToItsCatalog() {
  const catalogIdOf = await buildCatalogIndex(DB);

  assert.strictEqual(catalogIdOf('quests', 'q-1'), 'courses');
  assert.strictEqual(catalogIdOf('sections', 's-1'), 'courses');
  assert.strictEqual(catalogIdOf('themes', 't-1'), 'courses');
  assert.strictEqual(catalogIdOf('lessons', 'l-1'), 'courses');
  assert.strictEqual(catalogIdOf('questions', 'qn-1', {lessonId: 'l-1'}), 'courses');
}

async function testNodesUnderDifferentQuestsDoNotBleedIntoOneCatalog() {
  const catalogIdOf = await buildCatalogIndex(DB);

  assert.strictEqual(catalogIdOf('sections', 's-2'), 'home');
  assert.notStrictEqual(catalogIdOf('sections', 's-1'), catalogIdOf('sections', 's-2'));
}

async function testABrokenChainYieldsNothingRatherThanTheWrongCatalog() {
  // Приписать узел чужому каталогу хуже, чем честно вернуть пусто: в первом случае изменение
  // уедет не тем клиентам, во втором — вызывающий скажет об этом вслух.
  const catalogIdOf = await buildCatalogIndex(DB);

  assert.strictEqual(catalogIdOf('sections', 's-lost'), null);
  assert.strictEqual(catalogIdOf('themes', 't-lost'), null);
  assert.strictEqual(catalogIdOf('lessons', 'l-lost'), null);
  assert.strictEqual(catalogIdOf('quests', 'q-orphan'), null);
  assert.strictEqual(catalogIdOf('questions', 'qn-x', {lessonId: 'l-lost'}), null);
  assert.strictEqual(catalogIdOf('questions', 'qn-x', {}), null);
}

function testTheJournalTypeIsTheSingularOfTheCollection() {
  assert.strictEqual(syncTypeOf('quests'), 'quest');
  assert.strictEqual(syncTypeOf('sections'), 'section');
  assert.strictEqual(syncTypeOf('themes'), 'theme');
  assert.strictEqual(syncTypeOf('lessons'), 'lesson');
  assert.strictEqual(syncTypeOf('questions'), 'question');
}

(async () => {
  await testEveryLevelResolvesToItsCatalog();
  await testNodesUnderDifferentQuestsDoNotBleedIntoOneCatalog();
  await testABrokenChainYieldsNothingRatherThanTheWrongCatalog();
  testTheJournalTypeIsTheSingularOfTheCollection();
  console.log('content-catalog-index.test.js OK');
})();
