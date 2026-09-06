"use strict";

// Transactional test double: committed state, query bounds, read-before-write and atomic rollback.
// It intentionally does not emulate Firestore networking, automatic retries or composite indexes.
const assert = require("assert/strict");
const clone = (value) => value === undefined ? undefined : structuredClone(value);

function premiumTestDb(initial = {}) {
  const store = new Map(Object.entries(clone(initial)));
  const commits = [];
  const queries = [];
  let failNext = false;
  let beforeNext = null;
  const snap = (path) => ({id: path.split("/").at(-1), exists: store.has(path), data: () => clone(store.get(path))});
  const apply = (writes) => {
    if (failNext) { failNext = false; throw new Error("injected atomic commit failure"); }
    const next = new Map(store);
    for (const write of writes) {
      if (write.op === "create" && next.has(write.path)) throw new Error("ALREADY_EXISTS");
      next.set(write.path, write.merge ? {...next.get(write.path), ...clone(write.data)} : clone(write.data));
    }
    store.clear();
    for (const [path, value] of next) store.set(path, value);
    commits.push(clone(writes));
  };
  const doc = (path) => ({
    path, get: async () => snap(path), collection: (name) => collection(`${path}/${name}`),
    set: async (data, options) => apply([{op: "set", path, data, merge: !!options?.merge}]),
  });
  const collection = (path, filters = [], sort = null, maximum = null) => ({
    path, filters, sort, maximum, doc: (id) => doc(`${path}/${id}`),
    where: (field, op, value) => collection(path, [...filters, {field, op, value}], sort, maximum),
    orderBy: (field) => collection(path, filters, field, maximum),
    limit: (n) => collection(path, filters, sort, n),
  });
  return {
    store, commits, queries, doc, collection,
    failNextCommit() { failNext = true; },
    beforeNextTransaction(fn) { beforeNext = fn; },
    async runTransaction(body) {
      if (beforeNext) { const fn = beforeNext; beforeNext = null; fn(store); }
      const pending = [];
      const read = () => assert.equal(pending.length, 0, "Firestore transaction read after write");
      const tx = {
        async getAll(...refs) { read(); return refs.map((ref) => snap(ref.path)); },
        async get(ref) {
          read();
          if (!ref.filters) return snap(ref.path);
          assert.ok(Number.isSafeInteger(ref.maximum), "query must be explicitly bounded");
          queries.push({path: ref.path, maximum: ref.maximum});
          const depth = ref.path.split("/").length + 1;
          const selected = [...store.keys()].filter((path) => path.startsWith(`${ref.path}/`) && path.split("/").length === depth)
            .filter((path) => ref.filters.every(({field, op, value}) => {
              assert.equal(op, "=="); return store.get(path)[field] === value;
            })).sort((a, b) => store.get(a)[ref.sort] - store.get(b)[ref.sort]);
          return {docs: selected.slice(0, ref.maximum).map(snap)};
        },
        set(ref, data, options) { pending.push({op: "set", path: ref.path, data: clone(data), merge: !!options?.merge}); },
        create(ref, data) { pending.push({op: "create", path: ref.path, data: clone(data), merge: false}); },
      };
      const result = await body(tx);
      apply(pending);
      return result;
    },
  };
}

module.exports = {premiumTestDb};
