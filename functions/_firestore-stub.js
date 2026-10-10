"use strict";

/**
 * A `firebase-admin` stand-in, in memory, big enough to run `index.js` end to end.
 *
 * `index.js` calls `admin.initializeApp()` and `admin.firestore()` while it loads, so nothing in it
 * can be driven without one — and the parts most worth driving are the ones no pure module can
 * reach: the order the handler reads and writes in, what it hands `transaction.set`, and what it
 * spends. Cutting functions out of the source text proves each of them in isolation and proves
 * nothing about the composition; deleting the call that scores every attempt leaves every such
 * case green.
 *
 * So this is a real store rather than a set of expectations: documents go in, the handler runs
 * against them, and the assertions are made on what came out. Three things it records that a real
 * Firestore would only tell you by failing in production:
 *
 * - **every operation in order**, so "no read after the first write" is a fact about a run rather
 *   than about where a line sits in a file;
 * - **every write with its merge option**, so a document can be asserted whole;
 * - **transaction sets at the moment they are made**, not at commit, because that is when
 *   Firestore's read-before-write rule judges them.
 *
 * Deliberately not a Firestore emulator. Queries handle one `where` on equality over the immediate
 * children of a collection path, which is every query `index.js` makes on this path, and anything
 * else throws rather than quietly returning the wrong rows.
 */

/** Path segments alternate collection/document, so a document path has an even segment count. */
function isDocumentPath(path) {
  return path.split("/").length % 2 === 0;
}

/**
 * Надгробие отложенного удаления в транзакции: запись `pending` с ним вместо данных к коммиту
 * убирает документ. Реальная транзакция Firestore умеет `delete`, и код приёмника им пользуется
 * (`releaseReservation`), поэтому заглушка без него превращала бы честную ветку освобождения
 * ключа в TypeError ещё до проверки сути.
 */
const DELETE = Symbol("delete");

/** A value written through `FieldValue.increment`. Named so `clean()` in index.js passes it through. */
class IncrementTransform {
  constructor(by) {
    this.by = by;
  }
  isEqual(other) {
    return other instanceof IncrementTransform && other.by === this.by;
  }
}

/** `FieldValue.arrayUnion` / `arrayRemove`, under the same rule. */
class ArrayTransform {
  constructor(mode, values) {
    this.mode = mode;
    this.values = values;
  }
  isEqual(other) {
    return other instanceof ArrayTransform && other.mode === this.mode &&
      JSON.stringify(other.values) === JSON.stringify(this.values);
  }
}

class Timestamp {
  constructor(ms) {
    this.ms = ms;
  }
  toMillis() {
    return this.ms;
  }
  isEqual(other) {
    return other instanceof Timestamp && other.ms === this.ms;
  }
  static fromMillis(ms) {
    return new Timestamp(ms);
  }
}

class GeoPoint {}
class Bytes {}

function applyTransform(previous, value) {
  if (value instanceof IncrementTransform) {
    return (typeof previous === "number" ? previous : 0) + value.by;
  }
  if (value instanceof ArrayTransform) {
    const current = Array.isArray(previous) ? previous.slice() : [];
    if (value.mode === "remove") {
      return current.filter((entry) => !value.values.includes(entry));
    }
    for (const entry of value.values) if (!current.includes(entry)) current.push(entry);
    return current;
  }
  return value;
}

/** A merge write, field by field, so a transform sees what was there before it. */
function mergeInto(previous, data) {
  const merged = previous === undefined ? {} : {...previous};
  for (const [key, value] of Object.entries(data)) merged[key] = applyTransform(merged[key], value);
  return merged;
}

/**
 * @param seed `{path: data}` written before the run, as documents that already exist.
 * @param options `{fail}` — `{path: message}` or a predicate `(kind, path) => message|null`, so a
 *   read that must fail can be made to fail rather than described in a comment.
 */
function createFirestoreStub(seed, options) {
  const store = {
    documents: new Map(Object.entries(seed || {})),
    operations: [],
    writes: [],
    fail: (options && options.fail) || null,
    retryTransactionOnce: (options && options.retryTransactionOnce) || null,
  };

  function maybeFail(kind, path) {
    if (store.fail === null) return;
    const message = typeof store.fail === "function" ? store.fail(kind, path) : store.fail[path];
    if (message) throw new Error(message);
  }

  function record(kind, path) {
    store.operations.push({kind, path});
  }

  function snapshotFor(path) {
    const data = store.documents.get(path);
    return {
      id: path.slice(path.lastIndexOf("/") + 1),
      exists: data !== undefined,
      ref: {path, collection: (name) => collectionRef(`${path}/${name}`)},
      data: () => (data === undefined ? undefined : {...data}),
      updateTime: data === undefined ? undefined : Timestamp.fromMillis(0),
    };
  }

  function writeDocument(path, data, writeOptions) {
    const merge = Boolean(writeOptions && writeOptions.merge);
    const previous = merge ? store.documents.get(path) : undefined;
    store.documents.set(path, mergeInto(previous, data));
    store.writes.push({path, data, merge});
  }

  function documentRef(path) {
    if (!isDocumentPath(path)) throw new Error(`not a document path: ${path}`);
    return {
      path,
      id: path.slice(path.lastIndexOf("/") + 1),
      collection: (name) => collectionRef(`${path}/${name}`),
      get: async () => {
        record("read", path);
        maybeFail("read", path);
        return snapshotFor(path);
      },
      set: async (data, writeOptions) => {
        record("write", path);
        maybeFail("write", path);
        writeDocument(path, data, writeOptions);
      },
    };
  }

  /** The documents directly under a collection path — not those in its sub-collections. */
  function childrenOf(path) {
    const prefix = `${path}/`;
    return [...store.documents.keys()].filter(
      (candidate) => candidate.startsWith(prefix) && !candidate.slice(prefix.length).includes("/"),
    );
  }

  function querySnapshot(path, matches) {
    return {docs: matches.map(snapshotFor), empty: matches.length === 0, size: matches.length};
  }

  function collectionRef(path, filters = [], ordering = [], after = null, maximum = Infinity) {
    if (isDocumentPath(path)) throw new Error(`not a collection path: ${path}`);
    const described = path + (filters.length ? `?${filters.map(([field, value]) => `${field}==${value}`).join("&")}` : "");
    const valueOf = (child, field) => field === "__name__" ?
      child.slice(child.lastIndexOf("/") + 1) : store.documents.get(child)[field];
    const compared = (left, right) => left < right ? -1 : left > right ? 1 : 0;
    const ref = {
      path,
      querySnapshot: () => {
        let matches = childrenOf(path).filter((child) => filters.every(([field, value]) =>
          valueOf(child, field) === value));
        if (ordering.length > 0) {
          matches = matches.filter((child) => ordering.every(([field]) => valueOf(child, field) !== undefined));
          matches.sort((a, b) => {
            for (const [field, direction] of ordering) {
              const result = compared(valueOf(a, field), valueOf(b, field));
              if (result !== 0) return direction === "desc" ? -result : result;
            }
            return 0;
          });
        }
        if (after !== null) {
          if (ordering.length === 0 || after.length !== ordering.length) throw new Error("Unsupported fake cursor");
          matches = matches.filter((child) => {
            for (let index = 0; index < ordering.length; index += 1) {
              const [field, direction] = ordering[index];
              const result = compared(valueOf(child, field), after[index]);
              if (result === 0) continue;
              return direction === "desc" ? result < 0 : result > 0;
            }
            return false;
          });
        }
        return querySnapshot(path, matches.slice(0, maximum));
      },
      doc: (id) => documentRef(`${path}/${id}`),
      get: async () => {
        record("read", described);
        maybeFail("read", described);
        return ref.querySnapshot();
      },
      where: (field, operator, value) => {
        if (operator !== "==") throw new Error(`stub supports only "==" queries, got ${operator}`);
        return collectionRef(path, [...filters, [field, value]], ordering, after, maximum);
      },
      orderBy: (field, direction = "asc") => {
        if (!["asc", "desc"].includes(direction) || ordering.some(([old]) => old === field)) throw new Error("Unsupported fake ordering");
        return collectionRef(path, filters, [...ordering, [field, direction]], after, maximum);
      },
      startAfter: (...values) => collectionRef(path, filters, ordering, values, maximum),
      limit: (count) => collectionRef(path, filters, ordering, after, count),
    };
    return ref;
  }

  const db = {
    settings: () => {},
    collection: (name) => collectionRef(name),
    doc: (path) => documentRef(path),
    batch: () => {
      const pending = [];
      return {
        set: (ref, data, writeOptions) => pending.push([ref.path, data, writeOptions]),
        commit: async () => {
          for (const [path, data, writeOptions] of pending) {
            record("write", path);
            writeDocument(path, data, writeOptions);
          }
        },
      };
    },
    runTransaction: async (body) => {
      const pending = [];
      const created = new Set();
      const transaction = {
        get: async (ref) => {
          if (pending.length > 0) throw new Error("Firestore transaction read after write");
          record("read", ref.path);
          maybeFail("read", ref.path);
          return ref.querySnapshot ? ref.querySnapshot() : snapshotFor(ref.path);
        },
        getAll: async (...refs) => Promise.all(refs.map((ref) => transaction.get(ref))),
        create: (ref, data) => {
          if (created.has(ref.path)) throw new Error("ALREADY_EXISTS");
          created.add(ref.path);
          record("write", ref.path);
          pending.push([ref.path, data, undefined]);
        },
        // Recorded now, applied at commit: Firestore judges read-before-write at the call, and a
        // stub that recorded at commit would let a read after a set look ordered.
        set: (ref, data, writeOptions) => {
          record("write", ref.path);
          pending.push([ref.path, data, writeOptions]);
        },
        delete: (ref) => {
          record("write", ref.path);
          pending.push([ref.path, DELETE, undefined]);
        },
      };
      let result = await body(transaction);
      if (store.retryTransactionOnce) {
        // The real SDK retries the callback with the SAME Transaction instance, but throws
        // away writes from the conflicted run. Let tests change committed external state.
        const beforeRetry = store.retryTransactionOnce;
        store.retryTransactionOnce = null;
        pending.length = 0;
        created.clear();
        await beforeRetry(store);
        result = await body(transaction);
      }
      // Validate every simulated commit failure before applying any of its writes: no partial
      // fake commit may make a broken money transaction look correct.
      for (const [path] of pending) maybeFail("commit", path);
      for (const path of created) if (store.documents.has(path)) throw new Error("ALREADY_EXISTS");
      for (const [path, data, writeOptions] of pending) {
        if (data === DELETE) {
          store.documents.delete(path);
          store.writes.push({path, data: undefined, merge: false, deleted: true});
        } else {
          writeDocument(path, data, writeOptions);
        }
      }
      return result;
    },
  };

  const firestore = () => db;
  firestore.FieldValue = {
    increment: (by) => new IncrementTransform(by),
    arrayUnion: (...values) => new ArrayTransform("union", values),
    arrayRemove: (...values) => new ArrayTransform("remove", values),
  };
  firestore.Timestamp = Timestamp;
  firestore.GeoPoint = GeoPoint;
  firestore.Bytes = Bytes;

  store.db = db;
  store.reset = (nextSeed, nextOptions) => {
    store.documents = new Map(Object.entries(nextSeed || {}));
    store.operations = [];
    store.writes = [];
    store.fail = (nextOptions && nextOptions.fail) || null;
    store.retryTransactionOnce = (nextOptions && nextOptions.retryTransactionOnce) || null;
  };
  /** The path of the first write in the run, and every read that happened after it. */
  store.readsAfterFirstWrite = () => {
    const firstWrite = store.operations.findIndex((operation) => operation.kind === "write");
    if (firstWrite === -1) return [];
    return store.operations.slice(firstWrite).filter((operation) => operation.kind === "read");
  };
  store.writesTo = (prefix) => store.writes.filter((write) => write.path.startsWith(prefix));

  return {admin: {initializeApp: () => {}, firestore}, store};
}

module.exports = {createFirestoreStub, IncrementTransform, ArrayTransform, Timestamp};
