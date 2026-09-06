"use strict";

const {writeSyncChanges} = require("./sync-changes");

/**
 * Что публикация обязана убрать: узлы, которые в опубликованном дереве есть, а в заявке — уже нет.
 *
 * Публикация пишет узлы заявки с `{merge: true}` и не удаляет ничего, а `archived` у секции, темы,
 * урока и вопроса был литералом `false`. Из трёх способов удаления (AD-26) ниже квеста не срабатывал
 * ни один: автор удалял вопрос, документ `questions/{id}` оставался с `archived: false`, запись
 * журнала от прошлой публикации никуда не девалась, `refreshByIds` вопрос возвращал — и игроку его
 * продолжали задавать. Единственным работающим путём был каскад от строки квеста, то есть только
 * исчезновение квеста целиком.
 *
 * Способ здесь третий из трёх — `archived: true`, — и по AD-11 удаление на сервере обязано
 * сопровождаться записью в КАЖДЫЙ журнал, откуда узел читают: иначе клиент об этом не узнает вовсе.
 *
 * ЧТО СЕРВЕР ВПРАВЕ СЧИТАТЬ УДАЛЁННЫМ. Заявка на арену — не снимок всего квеста, а порция: автор
 * выбирает уроки, и в заявку попадают только они, их темы, их разделы и их вопросы. Поэтому
 * отсутствие узла в заявке само по себе не означает удаления:
 *
 *   — ВОПРОС решается сегодня и без новых полей. Заявка не отправляется вовсе, пока хоть один
 *     вопрос целевого урока не `SAVED` (`SubmitQuestDraftToArenaUseCase.validateTargetLessons`),
 *     значит вопросы целевого урока приходят все до одного. Опубликованный вопрос, чей урок в
 *     заявке есть, а его самого нет, — удалён автором, и другого объяснения у этого нет;
 *
 *   — УРОК, ТЕМА и РАЗДЕЛ так не решаются: «урока нет в заявке» и «урок не в этой порции» — одно и
 *     то же на проводе. Снимать их по отсутствию значило бы архивировать курс при публикации одного
 *     урока. Они снимаются только когда заявка прямо называет полный состав черновика (`draftTree`);
 *     пока клиент его не шлёт, эти три вида не снимаются, и это сказано вслух, а не подразумевается.
 *
 * Модуль чистый: ни firebase-admin, ни `Timestamp`. Время отдаётся числом (`lastModifiedAtMs`), и
 * тот, кто пишет, превращает его в `Timestamp` — иначе план нельзя было бы сравнить в тесте.
 * Версию план тоже не считает: новое значение `version` рождается в одном месте (AD-12).
 */

/** Виды узлов ниже квеста. Корень квеста сюда не входит — он снимается полкой (`visibleOn`). */
const PRUNABLE_KINDS = Object.freeze(["section", "theme", "lesson", "question"]);

/** Коллекция по виду узла — те же плоские коллекции, которые читает `refreshByIds`. */
const COLLECTION_OF_KIND = Object.freeze({
  section: "sections",
  theme: "themes",
  lesson: "lessons",
  question: "questions",
});

/** Вид узла по имени коллекции. */
const KIND_OF_COLLECTION = Object.freeze({
  sections: "section",
  themes: "theme",
  lessons: "lesson",
  questions: "question",
});

function idsOf(nodes) {
  const ids = new Set();
  for (const node of nodes || []) {
    const id = node && node.id !== undefined && node.id !== null ? String(node.id) : "";
    if (id) ids.add(id);
  }
  return ids;
}

function declaredIds(draftTree, collection) {
  if (!draftTree || typeof draftTree !== "object") return null;
  const declared = draftTree[collection];
  if (!Array.isArray(declared)) return null;
  return new Set(declared.map((id) => String(id)).filter(Boolean));
}

const KEEP = "keep";
const PRUNE = "prune";
const UNDECIDABLE = "undecidable";

/**
 * Одно решение на оба дерева: что заявка говорит про уже записанный узел.
 *
 * Публичное дерево и приватная копия автора расходятся только в том, ЧЕМ выражено снятие, — но не в
 * том, ЧТО снимается. Второе место, где это решалось бы заново, немедленно стало бы четвёртым
 * способом удаления (AD-26): достаточно одной правки в одной из копий, чтобы у игрока и у автора
 * пропадали разные узлы.
 *
 * @param {object} request публикуемая заявка.
 * @returns {{submittedLessons: Set<string>, verdict: function(string, object): string,
 *   skipped: string}} `verdict(kind, node)` — [KEEP], [PRUNE] или [UNDECIDABLE].
 */
function pruneVerdicts(request) {
  const submittedLessons = idsOf(request.lessons);
  const submitted = {
    section: idsOf(request.sections),
    theme: idsOf(request.themes),
    lesson: submittedLessons,
    question: idsOf(request.questions),
  };
  // Полный состав черновика объявляется только для секции, темы и урока; состав вопросов заявка
  // описывает сама, и `draftTree` про них не спрашивают.
  const declared = {
    section: declaredIds(request.draftTree, "sections"),
    theme: declaredIds(request.draftTree, "themes"),
    lesson: declaredIds(request.draftTree, "lessons"),
    question: null,
  };
  return {
    submittedLessons,
    skipped: submittedLessons.size === 0 ? "request names no lesson" : "",
    verdict(kind, node) {
      const id = String(node.id);
      if (submitted[kind].has(id)) return KEEP;
      if (kind === "question") {
        // Судить можно только о вопросах тех уроков, которые в заявке есть: у остальных состав
        // вопросов эта заявка не описывает.
        const lessonId = node.lessonId ? String(node.lessonId) : null;
        return lessonId && submittedLessons.has(lessonId) ? PRUNE : KEEP;
      }
      // Полного состава черновика заявка не называет — отличить «удалён» от «не в этой порции»
      // не по чему.
      if (!declared[kind]) return UNDECIDABLE;
      return declared[kind].has(id) ? KEEP : PRUNE;
    },
  };
}

/**
 * План снятия узлов, выпавших из дерева.
 *
 * @param {object} params
 * @param {object} params.request публикуемая заявка: `{draft: {id, catalogId}, sections, themes,
 *   lessons, questions, draftTree?}`. `draftTree` — необязательное объявление полного состава
 *   черновика `{sections: [id], themes: [id], lessons: [id]}`; без него урок, тема и раздел не
 *   снимаются, потому что отличить «удалён» от «не в этой порции» не по чему.
 * @param {object} params.existing опубликованное поддерево: `{sections, themes, lessons, questions}`,
 *   каждый элемент — `{id, currentVersion, archived, lessonId?}`.
 * @param {number} params.now время публикации, миллисекунды эпохи.
 * @returns {{documents: object, nodes: Array<object>, undecidable: object, questId: string,
 *   skipped: string}} `documents` — путь -> заплата; `nodes` — узлы для `writeSyncChanges`;
 *   `undecidable` — что заявка судить не вправе; `skipped` — почему план пуст не по существу.
 */
function planHierarchyPrune({request, existing, now}) {
  const documents = {};
  const nodes = [];
  const undecidable = {};
  const catalogId = request && request.draft ? String(request.draft.catalogId || "") : "";
  const questId = request && request.draft ? String(request.draft.id || "") : "";
  const empty = {documents, nodes, undecidable, questId};
  if (!catalogId) return {...empty, skipped: "no catalogId"};

  // Заявка, не описывающая дерева, не означает «автор удалил всё». Такая заявка — испорченная или
  // недочитанная запись, и снимать по ней опубликованный курс нельзя ни при каких обстоятельствах.
  const decision = pruneVerdicts(request);
  if (decision.skipped) return {...empty, skipped: decision.skipped};

  for (const kind of PRUNABLE_KINDS) {
    const collection = COLLECTION_OF_KIND[kind];
    for (const node of (existing && existing[collection]) || []) {
      const id = node && node.id ? String(node.id) : null;
      if (!id) continue;
      // Уже снятый узел не снимается второй раз: повторная запись двигала бы версию и клала запись
      // в журнал при каждой публикации соседнего урока.
      if (node.archived === true) continue;

      const verdict = decision.verdict(kind, node);
      if (verdict === KEEP) continue;
      if (verdict === UNDECIDABLE) {
        // Молчать об этом нельзя: узел остаётся у игроков, и это единственный след.
        (undecidable[collection] = undecidable[collection] || []).push(id);
        continue;
      }
      documents[`${collection}/${id}`] = archivePatch(node, now);
      // Вопрос читают два журнала — каталожный и журнал своего урока (AD-11); какие именно, решает
      // объявление в `sync-changes.js`, а не это место.
      nodes.push(kind === "question" ? {kind, id, catalogId, lessonId: String(node.lessonId)} : {kind, id, catalogId});
    }
  }
  return {documents, nodes, undecidable, questId, skipped: ""};
}

/**
 * То же снятие в приватной копии автора — и выраженное отсутствием документа, а не флагом.
 *
 * ПОЧЕМУ НЕ `archived`. Приватную половину читает `FirebaseQuestPrivateRemoteDataSource`: он берёт
 * документ квеста и перечисляет подколлекции целиком — `sections`, под каждой `themes`, под каждой
 * `lessons`, под каждым `questions`, — и превращает КАЖДЫЙ полученный документ в DTO. Поля
 * `archived` этот читатель не знает ни у одного из четырёх видов: его нет ни среди констант, ни в
 * `toSectionDto`/`toThemeDto`/`toLessonDto`/`toQuestionDto`, ни в фильтре — фильтра там нет вовсе.
 * Значит `archived: true` в приватном дереве не был бы снятием: урок вернулся бы автору обычным
 * узлом, и это ровно тот дефект, ради которого правка делается. Отсутствующий документ в
 * перечислении не возвращается — это первый из трёх способов AD-26 («id, не вернувшийся в ответе,
 * удаляется локально»), и для снимка целого квеста он и есть штатный.
 *
 * Четвёртого способа здесь не заводится: решение о том, ЧТО снять, — общее ([pruneVerdicts]), и
 * отличается только запись.
 *
 * NFR12 и AD-18 («серверные данные не удаляются») этим не нарушены: запрет защищает содержательные
 * коллекции, существующие не только у владельца, — курсы, вопросы, профили, — и запрещает менять
 * форму данных удалением. Здесь удаляется приватная копия автора, у которой один читатель — сам
 * автор, и удаляется ровно потому, что автор удалил узел у себя; публичный документ того же узла
 * остаётся на месте под `archived: true`.
 *
 * @param {object} params
 * @param {object} params.request та же заявка, что и у [planHierarchyPrune].
 * @param {object} params.existing приватное поддерево: `{sections, themes, lessons, questions}`, где
 *   элемент — `{id, path, lessonId?}`; `path` — полный путь документа в `private/{uid}/…`.
 * @returns {{deletes: string[], nodes: Array<object>, undecidable: object, questId: string,
 *   skipped: string}} `nodes` — одна запись `private_quest`, и только когда снимать есть что.
 */
function planPrivateHierarchyPrune({request, existing}) {
  const deletes = [];
  const nodes = [];
  const undecidable = {};
  const catalogId = request && request.draft ? String(request.draft.catalogId || "") : "";
  const questId = request && request.draft ? String(request.draft.id || "") : "";
  const ownerUid = request ? String(request.ownerUid || "") : "";
  const empty = {deletes, nodes, undecidable, questId};
  if (!catalogId) return {...empty, skipped: "no catalogId"};
  // Без автора приватного пути не существует, и запись в приватный журнал не адресуется (AD-11).
  if (!ownerUid) return {...empty, skipped: "no ownerUid"};

  const decision = pruneVerdicts(request);
  if (decision.skipped) return {...empty, skipped: decision.skipped};

  for (const kind of PRUNABLE_KINDS) {
    const collection = COLLECTION_OF_KIND[kind];
    for (const node of (existing && existing[collection]) || []) {
      const id = node && node.id ? String(node.id) : null;
      const path = node && node.path ? String(node.path) : null;
      // Узел без пути адресовать нечем; снятый узел в приватном дереве отсутствует вовсе, и второй
      // раз снимать нечего — «уже снято» здесь выражается тем же отсутствием.
      if (!id || !path) continue;

      const verdict = decision.verdict(kind, node);
      if (verdict === KEEP) continue;
      if (verdict === UNDECIDABLE) {
        (undecidable[collection] = undecidable[collection] || []).push(id);
        continue;
      }
      deletes.push(path);
    }
  }
  // Приватный журнал знает один узел — квест целиком (`NODE_JOURNALS.private_quest`), и автор
  // перечитывает по нему весь снимок. Пустой записи здесь быть не должно: она гоняла бы автора за
  // неизменившимся деревом на каждую публикацию соседнего урока.
  if (deletes.length > 0) nodes.push({kind: "private_quest", id: questId, catalogId, ownerUid});
  return {deletes, nodes, undecidable, questId, skipped: ""};
}

/**
 * Снятие в приватном дереве — тем же батчем, что и публикация, и вместе с записью в журнал.
 *
 * Живёт здесь, а не в `index.js`, по одной причине: писать нечего, кроме `delete` и записи журнала,
 * — ни `Timestamp`, ни версии, ни firebase-admin, — и потому эту проводку можно проверить на фейке
 * Firestore целиком, а не по исходнику. Публичный сосед (`writeHierarchyPruneToBatch`) остаётся в
 * `index.js`: ему нужны `admin.firestore.Timestamp` и `setVersioned`.
 *
 * @param {*} writer батч или транзакция: `delete(ref)` и `set(ref, data)`.
 * @param {*} db Firestore — ради `doc(path)`.
 * @param {object} plan результат [planPrivateHierarchyPrune].
 * @param {number} now время публикации, миллисекунды эпохи.
 * @returns {number} сколько документов снято.
 */
function writePrivateHierarchyPruneToBatch(writer, db, plan, now) {
  if (!writer || typeof writer.delete !== "function") {
    throw new TypeError("writePrivateHierarchyPruneToBatch requires a batch or a transaction with delete(ref)");
  }
  if (!db || typeof db.doc !== "function") {
    throw new TypeError("writePrivateHierarchyPruneToBatch requires a Firestore with doc(path)");
  }
  for (const path of plan.deletes) writer.delete(db.doc(path));
  // Документ и запись журнала уходят одним коммитом: снятый узел без записи автор не увидит
  // никогда, а запись без снятия отправила бы его перечитывать неизменившееся дерево (AD-11).
  for (const node of plan.nodes) writeSyncChanges(writer, db, node, now);
  return plan.deletes.length;
}

function archivePatch(node, now) {
  return {
    archived: true,
    // Не следующая версия, а текущая: новое значение version рождается в одном месте
    // (`entity-version.js`, AD-12), и план его не считает — он лишь передаёт, от чего считать.
    currentVersion: Number.isSafeInteger(node.currentVersion) && node.currentVersion > 0 ? node.currentVersion : 0,
    lastModifiedAtMs: now,
  };
}

module.exports = {
  COLLECTION_OF_KIND,
  KIND_OF_COLLECTION,
  PRUNABLE_KINDS,
  planHierarchyPrune,
  planPrivateHierarchyPrune,
  writePrivateHierarchyPruneToBatch,
};
