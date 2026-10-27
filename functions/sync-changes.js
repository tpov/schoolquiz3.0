"use strict";

/**
 * Журнал изменений: одна форма записи на четыре журнала (AD-11).
 *
 * Клиент узнаёт об изменениях единственным способом — из журнала `sync_changes` (CAP-8). Журналов
 * четыре, и каждый читается своим читателем:
 *
 *   catalogs/{catalogId}/sync_changes   — публичное дерево каталога (все игроки)
 *   lesson_content/{lessonId}/sync_changes — вопросы одного урока (тот, кто открыл урок)
 *   private/{uid}/sync_changes           — квесты автора, видимые только ему
 *   admin/review/sync_changes            — задания на ревью (рецензенты)
 *
 * Три инварианта, общие для всех четырёх:
 *
 *   (1) один документ на узел, и id документа не содержит времени. Повторная запись того же узла
 *       переписывает тот же документ. Сиды годами писали `{changedAtMs}-{type}-{id}`, и каждый
 *       прогон растил коллекцию; клиент вычитывал одно и то же изменение столько раз, сколько раз
 *       запускали сид;
 *   (2) всякое изменение узла на сервере сопровождается записью в КАЖДЫЙ журнал, откуда этот узел
 *       читают. Принадлежность объявлена в [NODE_JOURNALS] и не выводится на месте вызова;
 *   (3) тело записи — ровно `{type, id, changedAtMs}`, где `id` — ключ, по которому читатель зовёт
 *       `fetchByIds`, а не id документа журнала. Схема id документа остаётся за журналом.
 *
 * Модуль чистый: ни firebase-admin, ни сети. Батч, транзакция и `db` приезжают аргументами, и
 * поэтому одной и той же функцией пишут `index.js`, сиды и бэкфилл.
 */

// ─── журналы ──────────────────────────────────────────────────────────────────────────────────

const JOURNAL_CATALOG = "catalog";
const JOURNAL_LESSON_CONTENT = "lesson_content";
const JOURNAL_PRIVATE = "private";

const JOURNALS = Object.freeze([JOURNAL_CATALOG, JOURNAL_LESSON_CONTENT, JOURNAL_PRIVATE]);

/** Имя подколлекции журнала — одно на все четыре пути. */
const SYNC_CHANGES_COLLECTION = "sync_changes";

// ─── узлы и их журналы ────────────────────────────────────────────────────────────────────────

/**
 * Какой узел в какие журналы пишется. Единственное место, где это решено.
 *
 * Узел — документ, который читатель забирает по `id` из тела записи. Публичный квест и его
 * приватная копия у автора — два документа с одним id, и у каждого свой читатель, поэтому здесь
 * это два разных вида узла:
 *
 *   `quest` — публичный документ `quests/{id}`. Он читается из каталожного журнала всеми и из
 *   приватного журнала автором: перекладка по полкам, снятие с полок и публикация меняют версию в
 *   обоих документах (`writeQuestVersionMirror`), и автор обязан узнать об этом из своего журнала,
 *   иначе `expected_version` у него отстанет. У квеста без автора (сиды пишут публичное дерево
 *   напрямую) приватной половины нет — это говорится явно, `ownerUid: null`, а не умалчивается;
 *
 *   `private_quest` — документ `private/{uid}/catalogs/{c}/quests/{id}` сам по себе: заявка на
 *   арену и рейтинг приватного квеста трогают только его, и публичным читателям сообщать не о чем.
 *   В теле записи он всё равно `quest`: приватный журнал знает один тип, и две формы в одной
 *   коллекции — ровно то, что инвариант (3) запрещает;
 *
 *   `question` читается двумя журналами — каталожным и журналом своего урока. Запись только в один
 *   означает, что половина читателей об изменении не узнает;
 *
 */
const NODE_JOURNALS = Object.freeze({
  catalog: Object.freeze([JOURNAL_CATALOG]),
  quest: Object.freeze([JOURNAL_CATALOG, JOURNAL_PRIVATE]),
  section: Object.freeze([JOURNAL_CATALOG]),
  theme: Object.freeze([JOURNAL_CATALOG]),
  lesson: Object.freeze([JOURNAL_CATALOG]),
  question: Object.freeze([JOURNAL_CATALOG, JOURNAL_LESSON_CONTENT]),
  private_quest: Object.freeze([JOURNAL_PRIVATE]),
});

const NODE_KINDS = Object.freeze(Object.keys(NODE_JOURNALS));

/** Тип в теле записи по виду узла. Совпадает с видом везде, кроме приватной копии квеста. */
function syncChangeType(kind) {
  return kind === "private_quest" ? "quest" : kind;
}

// ─── пути ─────────────────────────────────────────────────────────────────────────────────────

/** Документ каталожного журнала: `{type}_{id}` — один на узел. */
function catalogSyncChangeId(type, id) {
  return `${type}_${id}`;
}

/**
 * Документ журнала содержимого урока: только id вопроса, без типа — в этом журнале других типов
 * не бывает.
 */
function lessonContentSyncChangeId(questionId) {
  return String(questionId);
}

/** Документ приватного журнала: `{catalogId}_{questId}`. */
function privateSyncChangeId(catalogId, questId) {
  return `${catalogId}_${questId}`;
}

function publicCatalogSyncChangePath(catalogId, type, id) {
  return `catalogs/${catalogId}/${SYNC_CHANGES_COLLECTION}/${catalogSyncChangeId(type, id)}`;
}

function lessonContentSyncChangePath(lessonId, questionId) {
  return `lesson_content/${lessonId}/${SYNC_CHANGES_COLLECTION}/${lessonContentSyncChangeId(questionId)}`;
}

function privateSyncChangePath(ownerUid, catalogId, questId) {
  return `private/${ownerUid}/${SYNC_CHANGES_COLLECTION}/${privateSyncChangeId(catalogId, questId)}`;
}

// ─── тело записи ──────────────────────────────────────────────────────────────────────────────

function nonBlankString(value, name) {
  if (typeof value !== "string" || value.trim() === "") {
    throw new TypeError(`sync change: ${name} must be a non-empty string, got ${JSON.stringify(value)}`);
  }
  return value;
}

function epochMillis(value, name) {
  if (!Number.isSafeInteger(value) || value <= 0) {
    throw new TypeError(`sync change: ${name} must be a positive integer of epoch milliseconds, got ${JSON.stringify(value)}`);
  }
  return value;
}

/**
 * Тело записи журнала — ровно три поля, и ни одно не может отсутствовать.
 *
 * Строгость намеренная: журнал ревью писал тело без `type`, приватный — с двумя лишними полями, и
 * читатель, который терпит обе формы, не может убрать защитный fallback никогда (AD-11).
 */
function syncChangeDocument(type, id, changedAtMs) {
  return {
    type: nonBlankString(type, "type"),
    id: nonBlankString(id, "id"),
    changedAtMs: epochMillis(changedAtMs, "changedAtMs"),
  };
}

const SYNC_CHANGE_FIELDS = Object.freeze(["type", "id", "changedAtMs"]);

/** Тело записи в ожидаемой форме: три поля, каждое своего типа, и ничего сверх. */
function isSyncChangeDocument(data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) return false;
  const keys = Object.keys(data);
  if (keys.length !== SYNC_CHANGE_FIELDS.length) return false;
  if (!SYNC_CHANGE_FIELDS.every((field) => keys.includes(field))) return false;
  return (
    typeof data.type === "string" && data.type.trim() !== "" &&
    typeof data.id === "string" && data.id.trim() !== "" &&
    Number.isSafeInteger(data.changedAtMs) && data.changedAtMs > 0
  );
}

// ─── записи по узлу ───────────────────────────────────────────────────────────────────────────

function requireAddress(node, field, kind, journal) {
  const value = node[field];
  if (typeof value !== "string" || value.trim() === "") {
    throw new TypeError(`sync change: ${kind} needs ${field} to address the ${journal} journal`);
  }
  return value;
}

function journalPath(journal, node, type) {
  const kind = node.kind;
  switch (journal) {
    case JOURNAL_CATALOG:
      return publicCatalogSyncChangePath(requireAddress(node, "catalogId", kind, journal), type, node.id);
    case JOURNAL_LESSON_CONTENT:
      return lessonContentSyncChangePath(requireAddress(node, "lessonId", kind, journal), node.id);
    case JOURNAL_PRIVATE:
      return privateSyncChangePath(
        requireAddress(node, "ownerUid", kind, journal),
        requireAddress(node, "catalogId", kind, journal),
        node.id,
      );
    default:
      throw new TypeError(`sync change: unknown journal ${journal}`);
  }
}

/**
 * Приватный журнал у публичного квеста есть только тогда, когда у квеста есть автор с приватной
 * половиной. Отсутствие автора — сведение, а не умолчание: место вызова обязано сказать
 * `ownerUid: null`, иначе забытое поле молча оставило бы автора без записи.
 */
function skipsPrivateJournal(node) {
  if (node.kind !== "quest") return false;
  if (!Object.prototype.hasOwnProperty.call(node, "ownerUid")) {
    throw new TypeError("sync change: quest needs ownerUid — a string, or null when the quest has no private half");
  }
  return node.ownerUid === null;
}

/**
 * Записи журналов для одного изменения узла: `[{journal, path, data}]`.
 *
 * @param {object} node `{kind, id, catalogId?, lessonId?, ownerUid?}` — вид узла из [NODE_JOURNALS]
 *   и адресные части журналов, которым он принадлежит.
 * @param {number} changedAtMs время изменения, миллисекунды эпохи.
 */
function syncChangeWrites(node, changedAtMs) {
  if (!node || typeof node !== "object") throw new TypeError("sync change: node is required");
  const journals = Object.prototype.hasOwnProperty.call(NODE_JOURNALS, node.kind) ? NODE_JOURNALS[node.kind] : null;
  if (!journals) {
    throw new TypeError(`sync change: unknown node kind ${JSON.stringify(node.kind)}; declared kinds: ${NODE_KINDS.join(", ")}`);
  }
  const data = syncChangeDocument(syncChangeType(node.kind), node.id, changedAtMs);
  const skipPrivate = skipsPrivateJournal(node);
  const writes = [];
  for (const journal of journals) {
    if (journal === JOURNAL_PRIVATE && skipPrivate) continue;
    writes.push({journal, path: journalPath(journal, node, data.type), data: {...data}});
  }
  return writes;
}

/**
 * Пишет записи журналов для одного изменения узла тем же батчем или транзакцией, что и сам узел.
 *
 * Без `merge`: документ — на узел, тело — три поля, и лишнее поле из прошлой формы не должно
 * пережить перезапись. Всё-или-ничего гарантирует писатель: узел и его записи идут одним коммитом.
 *
 * @param {*} writer батч или транзакция — всё, что умеет `set(ref, data)`.
 * @param {*} db Firestore — нужен только ради `doc(path)`.
 * @returns {Array<object>} записи, которые ушли писателю.
 */
function writeSyncChanges(writer, db, node, changedAtMs) {
  if (!writer || typeof writer.set !== "function") {
    throw new TypeError("writeSyncChanges requires a batch or a transaction");
  }
  if (!db || typeof db.doc !== "function") {
    throw new TypeError("writeSyncChanges requires a Firestore with doc(path)");
  }
  const writes = syncChangeWrites(node, changedAtMs);
  for (const write of writes) {
    writer.set(db.doc(write.path), write.data);
  }
  return writes;
}

// ─── приведение уже записанного к форме ──────────────────────────────────────────────────────

/** Старый append-лог сидов: `{changedAtMs}-{type}-{id}`. Время впереди — и был весь дефект. */
const LEGACY_TIMED_ID = /^(\d{10,})-([a-z_]+)-(.+)$/;

function numberOrNull(value) {
  return Number.isSafeInteger(value) && value > 0 ? value : null;
}

function stringOrNull(value) {
  return typeof value === "string" && value.trim() !== "" ? value : null;
}

/**
 * Что документ журнала означает — узел и время — независимо от того, в какой форме он записан.
 *
 * Возвращает `{docId: каноничный id документа, type, id, changedAtMs}` или null, когда документ
 * прочитать нельзя (нет времени, не разобрать узел): такой документ бэкфилл не трогает и называет.
 */
function interpretJournalEntry(journal, docId, data) {
  const body = data && typeof data === "object" ? data : {};
  const changedAtMs = numberOrNull(body.changedAtMs);
  if (changedAtMs === null) return null;
  const legacy = LEGACY_TIMED_ID.exec(docId);

  switch (journal) {
    case JOURNAL_CATALOG: {
      let type = stringOrNull(body.type);
      let id = stringOrNull(body.id);
      if (legacy) {
        type = type || legacy[2];
        id = id || legacy[3];
      } else if (!type || !id) {
        const separator = docId.indexOf("_");
        if (separator > 0) {
          type = type || docId.slice(0, separator);
          id = id || docId.slice(separator + 1);
        }
      }
      if (!type || !id) return null;
      return {docId: catalogSyncChangeId(type, id), type, id, changedAtMs};
    }
    case JOURNAL_LESSON_CONTENT: {
      const id = stringOrNull(body.id) || (legacy ? legacy[3] : docId);
      return {docId: lessonContentSyncChangeId(id), type: "question", id, changedAtMs};
    }
    case JOURNAL_PRIVATE: {
      // Документ всегда назывался `{catalogId}_{questId}`; лишними были поля тела, не id.
      const separator = docId.indexOf("_");
      const id = stringOrNull(body.questId) || stringOrNull(body.id) || (separator > 0 ? docId.slice(separator + 1) : null);
      if (!id) return null;
      return {docId, type: "quest", id, changedAtMs};
    }
    default:
      throw new TypeError(`sync change: unknown journal ${journal}`);
  }
}

/**
 * План приведения одной коллекции журнала к инвариантам AD-11.
 *
 * На каждый узел остаётся ровно один документ — с наибольшим `changedAtMs` — под каноничным id и
 * с телом ровно из трёх полей; остальные документы того же узла удаляются. Содержательные
 * коллекции план не упоминает вовсе: журнал производный, и приводить его можно, не читая узлы
 * (AD-18, NFR12).
 *
 * @param {string} journal один из [JOURNALS].
 * @param {Array<{id: string, data: object}>} entries все документы коллекции.
 * @returns {{writes: Array<{id: string, data: object}>, deletes: string[], unreadable: string[]}}
 */
function planJournalBackfill(journal, entries) {
  if (!JOURNALS.includes(journal)) throw new TypeError(`sync change: unknown journal ${journal}`);
  const byNode = new Map();
  const unreadable = [];
  for (const entry of entries || []) {
    const meaning = interpretJournalEntry(journal, entry.id, entry.data);
    if (!meaning) {
      unreadable.push(entry.id);
      continue;
    }
    const group = byNode.get(meaning.docId) || [];
    group.push({sourceId: entry.id, data: entry.data, ...meaning});
    byNode.set(meaning.docId, group);
  }

  const writes = [];
  const deletes = [];
  for (const [docId, group] of byNode) {
    // Побеждает самое позднее изменение; при равном времени — документ, уже стоящий под каноничным
    // id, чтобы план был устойчив к повторному прогону.
    const winner = group.reduce((best, candidate) => {
      if (!best) return candidate;
      if (candidate.changedAtMs !== best.changedAtMs) return candidate.changedAtMs > best.changedAtMs ? candidate : best;
      if (candidate.sourceId === docId && best.sourceId !== docId) return candidate;
      return best;
    }, null);
    const body = syncChangeDocument(winner.type, winner.id, winner.changedAtMs);
    const alreadyInForm = group.some(
      (member) => member.sourceId === docId && isSyncChangeDocument(member.data) &&
        member.data.type === body.type && member.data.id === body.id && member.data.changedAtMs === body.changedAtMs,
    );
    if (!alreadyInForm) writes.push({id: docId, data: body});
    for (const member of group) {
      if (member.sourceId !== docId) deletes.push(member.sourceId);
    }
  }
  writes.sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  deletes.sort();
  unreadable.sort();
  return {writes, deletes, unreadable};
}

/**
 * Какой это журнал — по пути коллекции `…/sync_changes`. Null для коллекции с таким именем, но
 * не из четырёх известных: её бэкфилл не трогает.
 */
function journalOfCollectionPath(collectionPath) {
  const path = String(collectionPath || "");
  const match = /^([a-z_]+)\/[^/]+\/sync_changes$/.exec(path);
  if (!match) return null;
  switch (match[1]) {
    case "catalogs":
      return JOURNAL_CATALOG;
    case "lesson_content":
      return JOURNAL_LESSON_CONTENT;
    case "private":
      return JOURNAL_PRIVATE;
    default:
      return null;
  }
}

module.exports = {
  JOURNALS,
  JOURNAL_CATALOG,
  JOURNAL_LESSON_CONTENT,
  JOURNAL_PRIVATE,
  NODE_JOURNALS,
  NODE_KINDS,
  SYNC_CHANGES_COLLECTION,
  SYNC_CHANGE_FIELDS,
  catalogSyncChangeId,
  interpretJournalEntry,
  isSyncChangeDocument,
  journalOfCollectionPath,
  lessonContentSyncChangeId,
  lessonContentSyncChangePath,
  planJournalBackfill,
  privateSyncChangeId,
  privateSyncChangePath,
  publicCatalogSyncChangePath,
  syncChangeDocument,
  syncChangeType,
  syncChangeWrites,
  writeSyncChanges,
};
