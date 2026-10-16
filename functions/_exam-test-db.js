"use strict";

/**
 * Фейковый Firestore для экзаменационных таблиц — общий помощник exam-набора тестов.
 *
 * Ровно тот приём, которым `arena-ticket-recovery.test.js` проверяет настоящие транзакции:
 * карта «путь → данные», транзакция собирает записи и применяет их только после успешного тела,
 * чтение внутри тела видит это же состояние. Запросы поддерживают ровно то, что экзаменационные
 * модули спрашивают: `where(field, "==", value)`, `where(field, "in", [...])`, `orderBy`,
 * `limit`. Всё остальное фейк сознательно не умеет — тест обязан упасть, а не притвориться.
 */

function clone(value) {
  return value === undefined ? undefined : structuredClone(value);
}

function docIdOf(path) {
  return path.split("/").at(-1);
}

/** Подчинённые документы коллекции: нечётное число сегментов после её пути. */
function inCollection(path, collectionPath) {
  return path.startsWith(`${collectionPath}/`) &&
    path.slice(collectionPath.length + 1).split("/").length % 2 === 1;
}

function matchesFilter(row, filter) {
  if (!filter) return true;
  const value = row ? row[filter.field] : undefined;
  if (filter.op === "==") return value === filter.value;
  if (filter.op === "in") return Array.isArray(filter.value) && filter.value.includes(value);
  return false;
}

/**
 * @param options {initialRows: object|Map, nowMs: number|function}
 */
function makeFakeDb({initialRows = {}, nowMs = 0} = {}) {
  const rows = new Map(Object.entries(initialRows instanceof Map ? Object.fromEntries(initialRows) : initialRows));
  const audit = {reads: [], queries: []};
  // Часы мира: число или функция. Мутируйте `clock.now` (или подайте функцию) — модули с
  // инъекцией часов читают именно его.
  const clock = {now: typeof nowMs === "function" ? nowMs() : nowMs};

  const snapshotFor = (path) => {
    const exists = rows.has(path);
    const raw = rows.get(path);
    return {
      id: docIdOf(path),
      ref: {path, id: docIdOf(path)},
      path,
      exists,
      data: () => (exists ? clone(raw) : undefined),
    };
  };

  const querySnapshotFor = (collectionPath, options) => {
    const found = [...rows.keys()]
      .filter((path) => inCollection(path, collectionPath))
      .filter((path) => options.filters.every((filter) => matchesFilter(rows.get(path), filter)))
      .sort((a, b) => {
        if (!options.orderBy) return a < b ? -1 : 1;
        const left = (rows.get(a) || {})[options.orderBy.field];
        const right = (rows.get(b) || {})[options.orderBy.field];
        const base = left === right ? (a < b ? -1 : 1) : (left < right ? -1 : 1);
        return options.orderBy.direction === "desc" ? -base : base;
      })
      .slice(0, options.limit || 1000)
      .map((path) => snapshotFor(path));
    return {docs: found, size: found.length, empty: found.length === 0};
  };

  function query(collectionPath, options) {
    return {
      where: (field, op, value) =>
        query(collectionPath, {...options, filters: [...options.filters, {field, op, value}]}),
      orderBy: (field, direction = "asc") =>
        query(collectionPath, {...options, orderBy: {field, direction}}),
      limit: (value) => query(collectionPath, {...options, limit: value}),
      get: async () => {
        audit.queries.push({collectionPath, ...options});
        return querySnapshotFor(collectionPath, options);
      },
    };
  }

  function writerRef(path, apply) {
    return {
      path,
      id: docIdOf(path),
      get: async () => snapshotFor(path),
      create: (body) => apply(path, body, {create: true}),
      set: (body, options) => apply(path, body, {merge: options && options.merge === true}),
    };
  }

  /** Прямая запись вне транзакции: применяется сразу, как у настоящего Firestore. */
  function directWrite(path, body, {create = false, merge = false} = {}) {
    const existing = rows.get(path);
    if (create) {
      if (rows.has(path)) {
        const error = new Error(`already exists: ${path}`);
        error.code = 6;
        throw error;
      }
      rows.set(path, clone(body));
      return;
    }
    rows.set(path, merge ? {...(existing || {}), ...clone(body)} : clone(body));
  }

  const db = {
    doc: (path) => writerRef(path, (p, body, options) =>
      directWrite(p, body, {create: options.create === true, merge: options.merge === true})),
    collection: (collectionPath) => query(collectionPath, {filters: [], orderBy: null, limit: null}),
    async getAll(...refs) {
      return refs.map((ref) => snapshotFor(ref.path));
    },
    async runTransaction(body) {
      const writes = [];
      const tx = {
        get: async (refOrQuery) => {
          if (refOrQuery && refOrQuery.get) return refOrQuery.get();
          audit.reads.push(refOrQuery.path);
          return snapshotFor(refOrQuery.path);
        },
        create: (ref, bodyValue) => {
          writes.push({path: ref.path, body: clone(bodyValue), create: true});
          return writerRef(ref.path, () => {});
        },
        set: (ref, bodyValue, options) => {
          writes.push({path: ref.path, body: clone(bodyValue), merge: options && options.merge === true});
          return writerRef(ref.path, () => {});
        },
      };
      const result = await body(tx);
      for (const write of writes) {
        const existing = rows.get(write.path);
        if (write.create) {
          if (rows.has(write.path)) {
            const error = new Error(`already exists: ${write.path}`);
            error.code = 6;
            throw error;
          }
          rows.set(write.path, write.body);
        } else if (write.merge) {
          rows.set(write.path, {...(existing || {}), ...write.body});
        } else {
          rows.set(write.path, write.body);
        }
      }
      return result;
    },
    // Прямые чтения вне транзакции — для фабрик, читающих без tx (сертификаты).
    async directGet(path) {
      audit.reads.push(path);
      return snapshotFor(path);
    },
  };

  return {db, rows, audit, clock};
}

/** Минимальное дерево курса/темы/урока, которое фейковый loadLessonContext считает одобренным. */
function seedHierarchy({courseId = "course", themeId = "theme", lessonId = "lesson",
  authorUid = "author"} = {}) {
  return {
    [`quests/${courseId}`]: {id: courseId, questType: "REGULAR", catalogId: "catalog", version: 3,
      authorUid, archived: false},
    "sections/section": {id: "section", questId: courseId, archived: false},
    [`themes/${themeId}`]: {id: themeId, sectionId: "section", archived: false},
    [`lessons/${lessonId}`]: {id: lessonId, themeId, version: 5, publishedAtMs: 1,
      reviewSubmissionId: "sub-1", reviewApprovedAtMs: 2, archived: false},
  };
}

module.exports = {makeFakeDb, seedHierarchy, clone};
