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
      ref: {path},
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

  function collectionRef(path) {
    if (isDocumentPath(path)) throw new Error(`not a collection path: ${path}`);
    return {
      path,
      doc: (id) => documentRef(`${path}/${id}`),
      get: async () => {
        record("read", path);
        maybeFail("read", path);
        return querySnapshot(path, childrenOf(path));
      },
      where: (field, operator, value) => {
        if (operator !== "==") throw new Error(`stub supports only "==" queries, got ${operator}`);
        return {
          get: async () => {
            const described = `${path}?${field}==${value}`;
            record("read", described);
            maybeFail("read", described);
            return querySnapshot(
              path,
              childrenOf(path).filter((child) => store.documents.get(child)[field] === value),
            );
          },
        };
      },
    };
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
      const transaction = {
        get: async (ref) => {
          record("read", ref.path);
          maybeFail("read", ref.path);
          return snapshotFor(ref.path);
        },
        // Recorded now, applied at commit: Firestore judges read-before-write at the call, and a
        // stub that recorded at commit would let a read after a set look ordered.
        set: (ref, data, writeOptions) => {
          record("write", ref.path);
          pending.push([ref.path, data, writeOptions]);
        },
      };
      const result = await body(transaction);
      for (const [path, data, writeOptions] of pending) writeDocument(path, data, writeOptions);
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
