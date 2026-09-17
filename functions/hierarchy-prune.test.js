"use strict";

/**
 * Удаление ниже квеста: узел, выпавший из дерева между публикациями, снимается и попадает в журнал.
 *
 * До этой правки `archived` у секции, темы, урока и вопроса был литералом `false`, публикация писала
 * только `{merge: true}` и ничего не удаляла, а записи журнала складывались лишь по узлам заявки.
 * Из трёх способов удаления (AD-26) ниже квеста не срабатывал ни один: автор удалял вопрос, а
 * игрокам его продолжали задавать.
 *
 * Второе, что здесь закреплено, — граница осторожности. Заявка на арену несёт порцию, а не весь
 * квест, поэтому снимать по одному лишь отсутствию в заявке можно только вопрос целевого урока.
 */

const assert = require("assert");
const {planHierarchyPrune, planPrivateHierarchyPrune, writePrivateHierarchyPruneToBatch} = require("./hierarchy-prune");
const {NODE_JOURNALS, syncChangeWrites} = require("./sync-changes");

const NOW = 1_756_800_000_000;
const CATALOG = "courses";

function request(parts) {
  return {
    draft: {id: "q-1", catalogId: CATALOG},
    ownerUid: "author-1",
    sections: [],
    themes: [],
    lessons: [],
    questions: [],
    ...parts,
  };
}

const SUBMITTED_TREE = {
  sections: [{id: "s-1"}],
  themes: [{id: "t-1"}],
  lessons: [{id: "l-1"}],
  questions: [{id: "qn-1", lessonId: "l-1"}, {id: "qn-2", lessonId: "l-1"}],
};

const PUBLISHED = {
  sections: [{id: "s-1", currentVersion: 3, archived: false}],
  themes: [{id: "t-1", currentVersion: 3, archived: false}],
  lessons: [{id: "l-1", currentVersion: 3, archived: false}],
  questions: [
    {id: "qn-1", currentVersion: 3, archived: false, lessonId: "l-1"},
    {id: "qn-2", currentVersion: 3, archived: false, lessonId: "l-1"},
  ],
};

function testAQuestionDroppedFromTheDraftIsArchivedAndJournalled() {
  const plan = planHierarchyPrune({
    request: request({...SUBMITTED_TREE, questions: [{id: "qn-1", lessonId: "l-1"}]}),
    existing: PUBLISHED,
    now: NOW,
  });

  assert.deepStrictEqual(plan.documents, {
    "questions/qn-2": {archived: true, currentVersion: 3, lastModifiedAtMs: NOW},
  });
  assert.deepStrictEqual(plan.nodes, [{kind: "question", id: "qn-2", catalogId: CATALOG, lessonId: "l-1"}]);
  // Вопрос читают два журнала; какие именно — решает объявление, не это место.
  const journals = syncChangeWrites(plan.nodes[0], NOW).map((write) => write.journal);
  assert.deepStrictEqual(journals, [...NODE_JOURNALS.question]);
}

function testAQuestionOfALessonThatIsNotInThisSubmissionIsLeftAlone() {
  // Публикация одного урока не вправе судить о составе вопросов соседнего: этой заявкой он не
  // описан вовсе. Снимать его значило бы стирать курс по частям при каждой частичной публикации.
  const plan = planHierarchyPrune({
    request: request(SUBMITTED_TREE),
    existing: {
      ...PUBLISHED,
      questions: PUBLISHED.questions.concat([{id: "qn-9", currentVersion: 1, archived: false, lessonId: "l-2"}]),
    },
    now: NOW,
  });

  assert.deepStrictEqual(plan.documents, {});
  assert.deepStrictEqual(plan.nodes, []);
}

function testALessonMissingFromTheSubmissionIsNotArchivedButIsNamed() {
  const plan = planHierarchyPrune({
    request: request(SUBMITTED_TREE),
    existing: {...PUBLISHED, lessons: PUBLISHED.lessons.concat([{id: "l-2", currentVersion: 1, archived: false}])},
    now: NOW,
  });

  assert.deepStrictEqual(plan.documents, {});
  assert.deepStrictEqual(plan.undecidable, {lessons: ["l-2"]});
}

function testALessonIsArchivedOnceTheSubmissionNamesTheWholeDraft() {
  const plan = planHierarchyPrune({
    request: request({
      ...SUBMITTED_TREE,
      draftTree: {sections: ["s-1"], themes: ["t-1"], lessons: ["l-1"]},
    }),
    existing: {
      sections: PUBLISHED.sections.concat([{id: "s-9", currentVersion: 2, archived: false}]),
      themes: PUBLISHED.themes.concat([{id: "t-9", currentVersion: 2, archived: false}]),
      lessons: PUBLISHED.lessons.concat([{id: "l-2", currentVersion: 1, archived: false}]),
      questions: PUBLISHED.questions,
    },
    now: NOW,
  });

  assert.deepStrictEqual(Object.keys(plan.documents).sort(), ["lessons/l-2", "sections/s-9", "themes/t-9"]);
  assert.deepStrictEqual(
    plan.nodes.map((node) => `${node.kind}/${node.id}`).sort(),
    ["lesson/l-2", "section/s-9", "theme/t-9"],
  );
  assert.deepStrictEqual(plan.undecidable, {});
  for (const node of plan.nodes) {
    assert.deepStrictEqual(syncChangeWrites(node, NOW).map((write) => write.journal), [...NODE_JOURNALS[node.kind]]);
  }
}

function testNothingIsWrittenWhenTheTreeIsUnchanged() {
  const plan = planHierarchyPrune({request: request(SUBMITTED_TREE), existing: PUBLISHED, now: NOW});

  assert.deepStrictEqual(plan.documents, {});
  assert.deepStrictEqual(plan.nodes, []);
}

function testAnAlreadyArchivedNodeIsNotArchivedTwice() {
  // Иначе публикация соседнего урока двигала бы версию снятого и клала запись в журнал каждый раз.
  const plan = planHierarchyPrune({
    request: request({...SUBMITTED_TREE, questions: [{id: "qn-1", lessonId: "l-1"}]}),
    existing: {
      ...PUBLISHED,
      questions: [
        {id: "qn-1", currentVersion: 3, archived: false, lessonId: "l-1"},
        {id: "qn-2", currentVersion: 9, archived: true, lessonId: "l-1"},
      ],
    },
    now: NOW,
  });

  assert.deepStrictEqual(plan.documents, {});
  assert.deepStrictEqual(plan.nodes, []);
}

function testASubmissionWithoutLessonsNeverArchivesAnything() {
  // Заявка без уроков — испорченная или недочитанная запись, а не «автор удалил всё».
  const plan = planHierarchyPrune({request: request({}), existing: PUBLISHED, now: NOW});

  assert.deepStrictEqual(plan.documents, {});
  assert.deepStrictEqual(plan.nodes, []);
  assert.strictEqual(plan.skipped, "request names no lesson");
}

function testAQuestionWithoutAKnownLessonIsNotArchivedSilently() {
  // Без урока вторая запись журнала не адресуется, а одна из двух — половина читателей вслепую.
  const plan = planHierarchyPrune({
    request: request({...SUBMITTED_TREE, questions: []}),
    existing: {...PUBLISHED, questions: [{id: "qn-2", currentVersion: 1, archived: false}]},
    now: NOW,
  });

  assert.deepStrictEqual(plan.nodes, []);
  assert.deepStrictEqual(plan.documents, {});
}

function testTheVersionIsHandedOverAsItIsForTheModuleToStep() {
  const plan = planHierarchyPrune({
    request: request({lessons: [{id: "l-1"}], questions: []}),
    existing: {sections: [], themes: [], lessons: [], questions: [{id: "qn-9", archived: false, lessonId: "l-1"}]},
    now: NOW,
  });

  // Ноль означает «версии ещё нет»: следующую посчитает entity-version.js, а не этот план.
  assert.deepStrictEqual(plan.documents, {"questions/qn-9": {archived: true, currentVersion: 0, lastModifiedAtMs: NOW}});
}

// ─── приватная половина: то же решение, выраженное отсутствием ────────────────────────────────

/**
 * Приватная копия автора читается снимком целиком (`FirebaseQuestPrivateRemoteDataSource`):
 * подколлекции перечисляются как есть, и поля `archived` этот читатель не знает ни у секции, ни у
 * темы, ни у урока, ни у вопроса. Поэтому здесь снятие — удаление документа: узел, не вернувшийся в
 * снимке, уходит с устройства (первый из трёх способов AD-26), а `archived: true` был бы для этого
 * читателя невидим и оставил бы удалённый урок у автора навсегда.
 */

const OWNER = "author-1";
const QUEST_ROOT = `private/${OWNER}/catalogs/${CATALOG}/quests/q-1`;
const SECTION_1 = `${QUEST_ROOT}/sections/s-1`;
const THEME_1 = `${SECTION_1}/themes/t-1`;
const LESSON_1 = `${THEME_1}/lessons/l-1`;

const PRIVATE = {
  sections: [{id: "s-1", path: SECTION_1}],
  themes: [{id: "t-1", path: THEME_1}],
  lessons: [{id: "l-1", path: LESSON_1}],
  questions: [
    {id: "qn-1", path: `${LESSON_1}/questions/qn-1`, lessonId: "l-1"},
    {id: "qn-2", path: `${LESSON_1}/questions/qn-2`, lessonId: "l-1"},
  ],
};

/** Firestore в памяти и батч «всё или ничего» — тот же фейк, что в `sync-changes.test.js`. */
function fakeFirestore(initial = {}) {
  const store = new Map(Object.entries(initial));
  return {
    doc(path) {
      return {path};
    },
    batch() {
      const staged = [];
      return {
        set(ref, data, options) {
          staged.push({type: "set", path: ref.path, data, options});
        },
        delete(ref) {
          staged.push({type: "delete", path: ref.path});
        },
        async commit() {
          for (const write of staged) {
            if (write.type === "delete") {
              store.delete(write.path);
              continue;
            }
            const previous = write.options && write.options.merge ? store.get(write.path) || {} : {};
            store.set(write.path, {...previous, ...write.data});
          }
        },
      };
    },
    read: (path) => store.get(path),
    paths: () => [...store.keys()].sort(),
  };
}

const PRIVATE_JOURNAL = `private/${OWNER}/sync_changes/${CATALOG}_q-1`;

function testAQuestionDroppedFromTheDraftIsDeletedFromThePrivateTree() {
  const plan = planPrivateHierarchyPrune({
    request: request({...SUBMITTED_TREE, questions: [{id: "qn-1", lessonId: "l-1"}]}),
    existing: PRIVATE,
  });

  assert.deepStrictEqual(plan.deletes, [`${LESSON_1}/questions/qn-2`]);
  // Приватный документ читают из одного журнала — приватного; какого именно, решает объявление.
  assert.deepStrictEqual(plan.nodes, [{kind: "private_quest", id: "q-1", catalogId: CATALOG, ownerUid: OWNER}]);
  assert.deepStrictEqual(
    syncChangeWrites(plan.nodes[0], NOW).map((write) => write.journal),
    [...NODE_JOURNALS.private_quest],
  );
}

function testThePrivateTreeIsNotJudgedBeyondTheSubmission() {
  // Вопрос чужого урока и урок, которого заявка не называет: ни того, ни другого эта заявка не
  // описывает, и работа автора от публикации соседнего урока не исчезает.
  const plan = planPrivateHierarchyPrune({
    request: request(SUBMITTED_TREE),
    existing: {
      ...PRIVATE,
      lessons: PRIVATE.lessons.concat([{id: "l-2", path: `${THEME_1}/lessons/l-2`}]),
      questions: PRIVATE.questions.concat([
        {id: "qn-9", path: `${THEME_1}/lessons/l-2/questions/qn-9`, lessonId: "l-2"},
      ]),
    },
  });

  assert.deepStrictEqual(plan.deletes, []);
  assert.deepStrictEqual(plan.nodes, []);
  // Старый клиент без `draftTree`: урок не снимается, но и не пропадает молча.
  assert.deepStrictEqual(plan.undecidable, {lessons: ["l-2"]});
}

function testAPrivateLessonIsDeletedOnceTheSubmissionNamesTheWholeDraft() {
  const plan = planPrivateHierarchyPrune({
    request: request({
      ...SUBMITTED_TREE,
      draftTree: {sections: ["s-1"], themes: ["t-1"], lessons: ["l-1"]},
    }),
    existing: {
      sections: PRIVATE.sections.concat([{id: "s-9", path: `${QUEST_ROOT}/sections/s-9`}]),
      themes: PRIVATE.themes.concat([{id: "t-9", path: `${SECTION_1}/themes/t-9`}]),
      lessons: PRIVATE.lessons.concat([{id: "l-2", path: `${THEME_1}/lessons/l-2`}]),
      questions: PRIVATE.questions,
    },
  });

  assert.deepStrictEqual(plan.deletes.slice().sort(), [
    `${THEME_1}/lessons/l-2`,
    `${SECTION_1}/themes/t-9`,
    `${QUEST_ROOT}/sections/s-9`,
  ].sort());
  assert.deepStrictEqual(plan.undecidable, {});
}

function testAnAlreadyDeletedPrivateNodeIsNotDeletedTwice() {
  // Снятый узел в приватном дереве отсутствует, а значит и в снимке его нет: снимать нечего, и
  // пустой записи в журнал быть не должно — иначе автор перечитывал бы квест на каждую публикацию.
  const plan = planPrivateHierarchyPrune({
    request: request({...SUBMITTED_TREE, questions: [{id: "qn-1", lessonId: "l-1"}]}),
    existing: {...PRIVATE, questions: [PRIVATE.questions[0]]},
  });

  assert.deepStrictEqual(plan.deletes, []);
  assert.deepStrictEqual(plan.nodes, []);
}

function testAPrivateSubmissionWithoutLessonsNeverDeletesAnything() {
  const plan = planPrivateHierarchyPrune({request: request({}), existing: PRIVATE});

  assert.deepStrictEqual(plan.deletes, []);
  assert.deepStrictEqual(plan.nodes, []);
  assert.strictEqual(plan.skipped, "request names no lesson");
}

function testAPrivateTreeWithoutAnOwnerIsNeverTouched() {
  // Без автора приватного пути нет вовсе, и запись в приватный журнал не адресуется.
  const plan = planPrivateHierarchyPrune({
    request: {...request(SUBMITTED_TREE), ownerUid: ""},
    existing: PRIVATE,
  });

  assert.deepStrictEqual(plan.deletes, []);
  assert.strictEqual(plan.skipped, "no ownerUid");
}

function testTheDeletedNodeAndItsJournalEntryGoInOneBatch() {
  const questionPath = `${LESSON_1}/questions/qn-2`;
  const db = fakeFirestore({[questionPath]: {id: "qn-2"}, [LESSON_1]: {id: "l-1"}});
  const plan = planPrivateHierarchyPrune({
    request: request({...SUBMITTED_TREE, questions: [{id: "qn-1", lessonId: "l-1"}]}),
    existing: PRIVATE,
  });
  const batch = db.batch();
  writePrivateHierarchyPruneToBatch(batch, db, plan, NOW);

  return batch.commit().then(() => {
    assert.strictEqual(db.read(questionPath), undefined, "снятый вопрос уходит из приватного дерева");
    assert.deepStrictEqual(db.read(LESSON_1), {id: "l-1"}, "урок заявки не тронут");
    // Тело записи — те же три поля, что у всякого журнала (AD-11), и тип у приватной копии `quest`.
    assert.deepStrictEqual(db.read(PRIVATE_JOURNAL), {type: "quest", id: "q-1", changedAtMs: NOW});
  });
}

function testAnEmptyPrivatePlanWritesNothingAtAll() {
  const db = fakeFirestore();
  const plan = planPrivateHierarchyPrune({request: request(SUBMITTED_TREE), existing: PRIVATE});
  const batch = db.batch();
  writePrivateHierarchyPruneToBatch(batch, db, plan, NOW);

  return batch.commit().then(() => {
    assert.deepStrictEqual(db.paths(), []);
  });
}

(async () => {
  testAQuestionDroppedFromTheDraftIsArchivedAndJournalled();
  testAQuestionOfALessonThatIsNotInThisSubmissionIsLeftAlone();
  testALessonMissingFromTheSubmissionIsNotArchivedButIsNamed();
  testALessonIsArchivedOnceTheSubmissionNamesTheWholeDraft();
  testNothingIsWrittenWhenTheTreeIsUnchanged();
  testAnAlreadyArchivedNodeIsNotArchivedTwice();
  testASubmissionWithoutLessonsNeverArchivesAnything();
  testAQuestionWithoutAKnownLessonIsNotArchivedSilently();
  testTheVersionIsHandedOverAsItIsForTheModuleToStep();
  testAQuestionDroppedFromTheDraftIsDeletedFromThePrivateTree();
  testThePrivateTreeIsNotJudgedBeyondTheSubmission();
  testAPrivateLessonIsDeletedOnceTheSubmissionNamesTheWholeDraft();
  testAnAlreadyDeletedPrivateNodeIsNotDeletedTwice();
  testAPrivateSubmissionWithoutLessonsNeverDeletesAnything();
  testAPrivateTreeWithoutAnOwnerIsNeverTouched();
  await testTheDeletedNodeAndItsJournalEntryGoInOneBatch();
  await testAnEmptyPrivatePlanWritesNothingAtAll();
  console.log("hierarchy-prune.test.js OK");
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
