"use strict";

// Bounded, server-only reduction of one frozen Arena season.  This module never trusts a client
// cursor, time, worker token, page size, or source row.  It deliberately does not reuse the
// tournament reducer: season eligibility has a different global "latest eligible vote" rule.

const {createHash, randomBytes} = require("node:crypto");
const {canonicalJson} = require("./canonical-json");
const {
  reserveMutation,
  completeReservation,
  requestFromReservation,
  mutationDocumentId,
  hash: reservationHash,
} = require("./arena-mutation-reservations");

const SCHEMA_VERSION = 2;
// v7 anchors independently-derived maps in compressed sparse Merkle tries.  v5's XOR set
// accumulator was linear, and v6's 256-element vector at every level made a valid 4097-key
// cohort impractically large.  Older calculations must be reviewed, never resumed here.
const CALCULATION_VERSION = "arena-season-full-cohort-v7";
// A source row becomes a frozen private checkpoint. Five rows keep every transaction comfortably
// below Firestore's document/request limits even when replaying historically valid audit fields.
const PAGE_SIZE = 5;
const LEASE_MS = 90_000;
const TREE_DEPTH = 32;
const TREE_CONFIGS = Object.freeze({
  selection: Object.freeze({collection: "selection_tree", nodeKind: "SELECTION_TREE_NODE", domain: "selection"}),
  aggregate: Object.freeze({collection: "aggregate_tree", nodeKind: "AGGREGATE_TREE_NODE", domain: "aggregate"}),
});
const PHASES = Object.freeze([
  "SCAN_MEMBERS", "VERIFY_MEMBERS", "SCAN_VOTES", "SELECT", "VERIFY_SELECTION",
  "COMPARE_SELECTION", "AGGREGATE", "VERIFY_AGGREGATES", "COMPARE_AGGREGATES", "PICK_WINNER", "VERIFY_CHECKPOINTS", "PUBLISH",
  "PUBLISHED", "REQUIRES_REVIEW",
]);
const INTEGRITY_STAGES = Object.freeze(["MEMBERS", "VOTES", "SELECTED", "VERIFIED_SELECTED", "AGGREGATES", "VERIFIED_AGGREGATES"]);
const TERMINAL_PHASES = new Set(["PUBLISHED", "REQUIRES_REVIEW"]);
const TERMINAL_COMPLETIONS = new Set(["SETTLED", "PENDING", "ABORTED", "REJECTED"]);

class ArenaSeasonFinalizationError extends Error {
  constructor(code, message) { super(message); this.name = "ArenaSeasonFinalizationError"; this.code = code; }
}

function fail(code, message = code) { throw new ArenaSeasonFinalizationError(code, message); }
function policyValue(callback, message) {
  try { return callback(); }
  catch (error) {
    // Policy functions are pure validation/arithmetic. A malformed sealed value must terminate
    // as a repairable source fault, never be mistaken for a retryable storage outage.
    if (error instanceof RangeError || error instanceof TypeError) fail("INVALID_SOURCE", message);
    throw error;
  }
}
function hash(value) {
  try { return createHash("sha256").update(canonicalJson(value)).digest("hex"); }
  catch { fail("INVALID_SOURCE", "season checkpoint contains noncanonical JSON"); }
}
function data(snapshot) { return snapshot && snapshot.exists ? snapshot.data() || {} : null; }
function integer(value, name, min = 0) {
  if (!Number.isSafeInteger(value) || value < min) fail("INVALID_ARGUMENT", `${name} must be a safe integer >= ${min}`);
  return value;
}
function add(left, right, name = "counter") {
  const sum = BigInt(integer(left, name)) + BigInt(integer(right, name));
  if (sum > BigInt(Number.MAX_SAFE_INTEGER)) fail("INVALID_SOURCE", `${name} overflow`);
  return Number(sum);
}
function text(value, name) {
  if (typeof value !== "string" || !value.trim()) fail("INVALID_SOURCE", `${name} is required`);
  return value;
}
function documentId(value, name) {
  text(value, name);
  if (value.includes("/") || value === "." || value === ".." || Buffer.byteLength(value) > 1500) {
    fail("INVALID_SOURCE", `${name} is not a document ID`);
  }
  return value;
}
function object(value, name) {
  if (!value || typeof value !== "object" || Array.isArray(value)) fail("INVALID_SOURCE", `${name} must be an object`);
  return value;
}
function exactObject(value, name, fields) {
  const record = object(value, name);
  if (Object.keys(record).length !== fields.length || fields.some(field => !Object.hasOwn(record, field))) {
    fail("INVALID_SOURCE", `${name} has unsupported fields`);
  }
  return record;
}
function same(left, right) {
  try { return canonicalJson(left) === canonicalJson(right); }
  catch { fail("INVALID_SOURCE", "season checkpoint contains noncanonical JSON"); }
}
function refId(prefix, value) { return hash([prefix, value]); }
function seasonRef(db, quarterId) { return db.doc(`arena_seasons/${quarterId}`); }
function jobRef(db, quarterId, finalizationId) { return db.doc(`arena_seasons/${quarterId}/finalizations/${finalizationId}`); }
function memberJobRef(db, quarterId, finalizationId, lessonId) {
  return db.doc(`arena_seasons/${quarterId}/finalizations/${finalizationId}/members/${refId("member", lessonId)}`);
}
function voteJobRef(db, quarterId, finalizationId, sourceId) {
  return db.doc(`arena_seasons/${quarterId}/finalizations/${finalizationId}/votes/${refId("vote", sourceId)}`);
}
function ratingIndexRef(db, quarterId, finalizationId, ratingId) {
  return db.doc(`arena_seasons/${quarterId}/finalizations/${finalizationId}/rating_ids/${refId("rating", ratingId)}`);
}
function completionIndexRef(db, quarterId, finalizationId, attemptId) {
  return db.doc(`arena_seasons/${quarterId}/finalizations/${finalizationId}/completion_ids/${refId("completion", attemptId)}`);
}
function selectedRef(db, quarterId, finalizationId, lessonId, uid) {
  return db.doc(`arena_seasons/${quarterId}/finalizations/${finalizationId}/selected/${refId("selected", [lessonId, uid])}`);
}
function selectedVerifiedRef(db, quarterId, finalizationId, lessonId, uid) {
  // The verified collection has its own envelope role.  Reusing the logical row ID keeps its
  // document-name order identical to the working collection, which makes a bounded semantic
  // root reproducible without loading or sorting the full cohort in memory.
  return db.doc(`arena_seasons/${quarterId}/finalizations/${finalizationId}/selected_verified/${refId("selected", [lessonId, uid])}`);
}
function aggregateRef(db, quarterId, finalizationId, lessonId) {
  return db.doc(`arena_seasons/${quarterId}/finalizations/${finalizationId}/aggregates/${refId("aggregate", lessonId)}`);
}
function aggregateVerifiedRef(db, quarterId, finalizationId, lessonId) {
  return db.doc(`arena_seasons/${quarterId}/finalizations/${finalizationId}/aggregates_verified/${refId("aggregate", lessonId)}`);
}

function seal(value) {
  const {checkpointDigest, ...body} = value;
  return {...body, checkpointDigest: hash(body)};
}
function sealEnvelope(kind, quarterId, finalizationId, body) {
  const envelope = {kind, quarterId, finalizationId, ...body};
  return {...envelope, digest: hash(envelope)};
}
function validateEnvelope(raw, name, kind, fields, {quarterId, finalizationId}) {
  documentId(quarterId, "checkpoint quarterId"); documentId(finalizationId, "checkpoint finalizationId");
  const value = exactObject(raw, name, ["kind", "quarterId", "finalizationId", ...fields, "digest"]);
  const {digest, ...body} = value;
  if (body.kind !== kind || body.quarterId !== quarterId || body.finalizationId !== finalizationId || digest !== hash(body)) {
    fail("INVALID_SOURCE", `${name} binding changed`);
  }
  return body;
}
function chain(previous, row) { return hash([previous, row]); }
// The two source-derived maps are committed by compressed sparse 256-way Merkle tries.  A node
// persists only occupied edges; a leaf edge contains fixed-size SHA route/value commitments.
// Unary paths are compressed into one edge.  Thus an n-key tree has at most n internal nodes and
// O(n) edge payload, rather than one 256-hash vector for every one of 32 levels per key.  A
// deliberately adversarial route can still be 32 bytes deep, so one five-row page has at most
// 5 * 32 changed existing nodes plus five row writes and remains below Firestore's 500-write
// transaction limit.
const MERKLE_VERSION = "arena-season-merkle-v7";
const merkleEmptyHashes = new Map();
function validHash(value) { return typeof value === "string" && /^[a-f0-9]{64}$/.test(value); }
function merkleEmptyHash(tree, depth) {
  if (!Number.isSafeInteger(depth) || depth < 0 || depth > TREE_DEPTH) fail("INVALID_SOURCE", "Merkle depth is invalid");
  const cacheKey = `${tree.domain}:${depth}`;
  if (!merkleEmptyHashes.has(cacheKey)) {
    merkleEmptyHashes.set(cacheKey, hash([MERKLE_VERSION, tree.domain, "empty", depth]));
  }
  return merkleEmptyHashes.get(cacheKey);
}
function merkleNodeRef(db, quarterId, finalizationId, tree, depth, prefix) {
  return db.doc(`arena_seasons/${quarterId}/finalizations/${finalizationId}/${tree.collection}/` +
    refId("arena-season-merkle-node", [tree.domain, depth, prefix]));
}
function merklePath(tree, key) {
  const keyHash = hash([MERKLE_VERSION, tree.domain, "key", key]);
  return {key, keyHash};
}
function merkleChildrenJson(children) {
  if (!Array.isArray(children) || children.length > 256) fail("INVALID_SOURCE", "Merkle child vector is invalid");
  let previous = -1;
  for (const entry of children) {
    if (!Array.isArray(entry) || !Number.isSafeInteger(entry[0]) || entry[0] < 0 || entry[0] > 255 || entry[0] <= previous) {
      fail("INVALID_SOURCE", "Merkle child vector is invalid");
    }
    previous = entry[0];
    if (entry[1] === "L") {
      if (entry.length !== 4 || !validHash(entry[2]) || !validHash(entry[3])) {
        fail("INVALID_SOURCE", "Merkle leaf edge is invalid");
      }
    } else if (entry[1] === "N") {
      if (entry.length !== 5 || !Number.isSafeInteger(entry[2]) || entry[2] < 1 || entry[2] >= TREE_DEPTH ||
          typeof entry[3] !== "string" || !new RegExp(`^[a-f0-9]{${entry[2] * 2}}$`).test(entry[3]) || !validHash(entry[4])) {
        fail("INVALID_SOURCE", "Merkle node edge is invalid");
      }
    } else {
      fail("INVALID_SOURCE", "Merkle edge kind is invalid");
    }
  }
  try { return canonicalJson(children); }
  catch { fail("INVALID_SOURCE", "Merkle child vector is noncanonical"); }
}
function merkleNodeHash(tree, quarterId, finalizationId, depth, prefix, children) {
  return hash([MERKLE_VERSION, tree.domain, quarterId, finalizationId, depth, prefix, merkleChildrenJson(children)]);
}
function merkleLeafHash(tree, quarterId, finalizationId, path, value) {
  return hash([MERKLE_VERSION, tree.domain, quarterId, finalizationId, "leaf", path.keyHash, path.key, value]);
}
function sealMerkleNode(tree, quarterId, finalizationId, state) {
  return sealEnvelope(tree.nodeKind, quarterId, finalizationId, {
    depth: state.depth, prefix: state.prefix, childrenJson: merkleChildrenJson(state.children),
  });
}
function validateMerkleNode(raw, tree, {quarterId, finalizationId, depth, prefix}) {
  const node = validateEnvelope(raw, "Merkle node", tree.nodeKind, ["depth", "prefix", "childrenJson"], {quarterId, finalizationId});
  if (node.depth !== depth || node.prefix !== prefix || typeof node.childrenJson !== "string" ||
      !new RegExp(`^[a-f0-9]{${depth * 2}}$`).test(prefix)) {
    fail("INVALID_SOURCE", "Merkle node binding changed");
  }
  let children;
  try { children = JSON.parse(node.childrenJson); }
  catch { fail("INVALID_SOURCE", "Merkle child vector is malformed"); }
  // Root represents the one-key case, but every compressed non-root branch must be a real split.
  // Deletions are not part of this reducer, so a unary non-root node can only be malformed data.
  if (children.length === 0 || (depth > 0 && children.length < 2) || merkleChildrenJson(children) !== node.childrenJson) {
    fail("INVALID_SOURCE", "Merkle child vector is noncanonical");
  }
  for (const entry of children) {
    if (entry[1] === "L") {
      const leaf = validateMerkleLeafEdge(entry);
      if (!leaf.keyHash.startsWith(prefix) || merkleByte(leaf, depth) !== entry[0]) {
        fail("INVALID_SOURCE", "Merkle leaf edge route is invalid");
      }
    } else if (entry[2] <= depth || !entry[3].startsWith(prefix) ||
        Number.parseInt(entry[3].slice(depth * 2, depth * 2 + 2), 16) !== entry[0]) {
      fail("INVALID_SOURCE", "Merkle node edge route is invalid");
    }
  }
  return children;
}
function merkleByte(path, depth) {
  if (!Number.isSafeInteger(depth) || depth < 0 || depth >= TREE_DEPTH) fail("INVALID_SOURCE", "Merkle depth is invalid");
  return Number.parseInt(path.keyHash.slice(depth * 2, depth * 2 + 2), 16);
}
function merkleDivergence(left, right) {
  for (let depth = 0; depth < TREE_DEPTH; depth++) if (merkleByte(left, depth) !== merkleByte(right, depth)) return depth;
  fail("INVALID_SOURCE", "Merkle route collision requires repair");
}
function merkleStateId(depth, prefix) { return `${depth}:${prefix}`; }
function merkleFindChild(children, child) { return children.find(entry => entry[0] === child) || null; }
function merkleReplaceChild(children, next) {
  const output = children.filter(entry => entry[0] !== next[0]); output.push(next);
  output.sort((left, right) => left[0] - right[0]);
  // Validate the outgoing representation before it becomes part of a sealed hash.
  merkleChildrenJson(output);
  return output;
}
function merkleNodeDigest(tree, quarterId, finalizationId, state) {
  return merkleNodeHash(tree, quarterId, finalizationId, state.depth, state.prefix, state.children);
}
function merkleNodeEdge(child, state, digest) {
  return [child, "N", state.depth, state.prefix, digest];
}
function merkleLeafEdge(child, path, digest) { return [child, "L", path.keyHash, digest]; }
function validateMerkleLeafEdge(entry) { return {keyHash: entry[2], digest: entry[3]}; }

// Read every required node before the first write, then mutate a small in-memory overlay.  An
// edge names the next compressed prefix and commits its node hash, so the root in the sealed job
// authenticates both the route and the current leaf value.  The overlay intentionally supports
// only replacement/insertion: finalization never deletes source-derived rows.
async function loadMerkleOverlay({tx, db, quarterId, finalizationId, tree, root, keys}) {
  if (!validHash(root)) fail("INVALID_SOURCE", "Merkle root is invalid");
  const states = new Map();
  let currentRoot = root;
  async function loadState(depth, prefix, expectedDigest = undefined) {
    const id = merkleStateId(depth, prefix); const cached = states.get(id);
    if (cached) {
      if (expectedDigest !== undefined && (!cached.exists || merkleNodeDigest(tree, quarterId, finalizationId, cached) !== expectedDigest)) {
        fail("INVALID_SOURCE", "Merkle node binding changed");
      }
      return cached;
    }
    const ref = merkleNodeRef(db, quarterId, finalizationId, tree, depth, prefix);
    const stored = data(await tx.get(ref));
    const state = {depth, prefix, ref, exists: stored !== null, children: null, dirty: false, created: false};
    if (stored !== null) {
      state.children = validateMerkleNode(stored, tree, {quarterId, finalizationId, depth, prefix});
      if (expectedDigest !== undefined && merkleNodeDigest(tree, quarterId, finalizationId, state) !== expectedDigest) {
        fail("INVALID_SOURCE", "Merkle node binding changed");
      }
    } else if (expectedDigest !== undefined) {
      fail("INVALID_SOURCE", "Merkle node is absent");
    }
    states.set(id, state); return state;
  }

  // Always read the deterministic root ref.  An unreachable root document next to an empty
  // sealed root is a corruption signal, never an invitation to overwrite it later.
  const rootState = await loadState(0, "", root === merkleEmptyHash(tree, 0) ? undefined : root);
  if (root === merkleEmptyHash(tree, 0) && rootState.exists) fail("INVALID_SOURCE", "empty Merkle root has a node");

  async function loadRoute(path) {
    if (currentRoot === merkleEmptyHash(tree, 0)) return {kind: "EMPTY", path, ancestors: []};
    let state = rootState; const ancestors = [];
    for (;;) {
      if (!path.keyHash.startsWith(state.prefix) || state.depth >= TREE_DEPTH) fail("INVALID_SOURCE", "Merkle node route is invalid");
      const child = merkleByte(path, state.depth); const entry = merkleFindChild(state.children, child);
      ancestors.push({state, child, entry});
      if (!entry) return {kind: "MISSING", path, ancestors};
      if (entry[1] === "L") {
        const leaf = validateMerkleLeafEdge(entry);
        if (!leaf.keyHash.startsWith(state.prefix) || merkleByte(leaf, state.depth) !== child) {
          fail("INVALID_SOURCE", "Merkle leaf route is invalid");
        }
        return {kind: "LEAF", path, ancestors, ...leaf};
      }
      const [, , depth, prefix, digest] = entry;
      if (depth <= state.depth || !prefix.startsWith(state.prefix) ||
          Number.parseInt(prefix.slice(state.depth * 2, state.depth * 2 + 2), 16) !== child) {
        fail("INVALID_SOURCE", "Merkle edge route is invalid");
      }
      // A target can diverge in an edge's compressed prefix.  The authenticated parent edge is
      // then a valid absence proof, but its referenced child must still exist and match before
      // we split the edge.  Treating this as corruption would reject a valid insertion order.
      if (!path.keyHash.startsWith(prefix)) {
        const childState = await loadState(depth, prefix, digest);
        let divergence = state.depth + 1;
        while (divergence < depth && merkleByte(path, divergence) ===
          Number.parseInt(prefix.slice(divergence * 2, divergence * 2 + 2), 16)) divergence++;
        if (divergence >= depth) fail("INVALID_SOURCE", "Merkle compressed edge divergence is invalid");
        return {kind: "DIVERGENT_BRANCH", path, ancestors, entry, childState, divergence};
      }
      state = await loadState(depth, prefix, digest);
    }
  }

  const paths = new Map(); const routes = new Map();
  for (const key of keys) {
    const path = merklePath(tree, key); const id = canonicalJson(key);
    if (!paths.has(id)) paths.set(id, path);
  }
  for (const [id, path] of paths) routes.set(id, await loadRoute(path));

  // A later insertion in this page can split an existing leaf or another page-local route.  Read
  // every deterministic split ref now, before any write, so an unreachable/orphaned node cannot
  // be silently overwritten and no transaction performs a read after a write.
  const splits = new Map();
  function addSplit(left, right) {
    if (left.keyHash === right.keyHash) return;
    const depth = merkleDivergence(left, right); if (depth === 0) return;
    const prefix = left.keyHash.slice(0, depth * 2); splits.set(merkleStateId(depth, prefix), {depth, prefix});
  }
  for (const route of routes.values()) if (route.kind === "LEAF" && route.keyHash !== route.path.keyHash) {
    addSplit(route.path, {keyHash: route.keyHash});
  }
  for (const route of routes.values()) if (route.kind === "DIVERGENT_BRANCH") {
    const depth = route.divergence; const prefix = route.path.keyHash.slice(0, depth * 2);
    splits.set(merkleStateId(depth, prefix), {depth, prefix});
  }
  const allPaths = [...paths.values()];
  for (let left = 0; left < allPaths.length; left++) for (let right = left + 1; right < allPaths.length; right++) {
    addSplit(allPaths[left], allPaths[right]);
  }
  for (const split of splits.values()) await loadState(split.depth, split.prefix);

  function stateFor(depth, prefix) {
    const state = states.get(merkleStateId(depth, prefix));
    if (!state) fail("INVALID_SOURCE", "Merkle path was not prefetched");
    return state;
  }
  function loadedRoute(path) {
    if (currentRoot === merkleEmptyHash(tree, 0)) return {kind: "EMPTY", path, ancestors: []};
    let state = rootState; const ancestors = [];
    for (;;) {
      if (!state.exists || !path.keyHash.startsWith(state.prefix)) fail("INVALID_SOURCE", "Merkle overlay route is invalid");
      const child = merkleByte(path, state.depth); const entry = merkleFindChild(state.children, child);
      ancestors.push({state, child, entry});
      if (!entry) return {kind: "MISSING", path, ancestors};
      if (entry[1] === "L") {
        const leaf = validateMerkleLeafEdge(entry);
        if (!leaf.keyHash.startsWith(state.prefix) || merkleByte(leaf, state.depth) !== child) {
          fail("INVALID_SOURCE", "Merkle overlay leaf route is invalid");
        }
        return {kind: "LEAF", path, ancestors, ...leaf};
      }
      const [, , depth, prefix, digest] = entry; const next = stateFor(depth, prefix);
      if (!next.exists || merkleNodeDigest(tree, quarterId, finalizationId, next) !== digest ||
          depth <= state.depth || !prefix.startsWith(state.prefix) ||
          Number.parseInt(prefix.slice(state.depth * 2, state.depth * 2 + 2), 16) !== child) {
        fail("INVALID_SOURCE", "Merkle overlay edge changed");
      }
      if (!path.keyHash.startsWith(prefix)) {
        let divergence = state.depth + 1;
        while (divergence < depth && merkleByte(path, divergence) ===
          Number.parseInt(prefix.slice(divergence * 2, divergence * 2 + 2), 16)) divergence++;
        if (divergence >= depth) fail("INVALID_SOURCE", "Merkle overlay compressed edge divergence is invalid");
        return {kind: "DIVERGENT_BRANCH", path, ancestors, entry, childState: next, divergence};
      }
      state = next;
    }
  }
  function provePath(path, value) {
    const route = loadedRoute(path);
    if (route.kind === "EMPTY" || route.kind === "MISSING" || route.kind === "DIVERGENT_BRANCH" ||
        (route.kind === "LEAF" && route.keyHash !== path.keyHash)) {
      if (value !== null) fail("INVALID_SOURCE", "Merkle proof leaf is absent");
      return route;
    }
    if (value === null) fail("INVALID_SOURCE", "Merkle proof leaf unexpectedly exists");
    if (route.digest !== merkleLeafHash(tree, quarterId, finalizationId, path, value)) {
      fail("INVALID_SOURCE", "Merkle proof leaf changed");
    }
    return route;
  }
  function updateAncestors(route, changed) {
    for (let index = route.ancestors.length - 2; index >= 0; index--) {
      const parent = route.ancestors[index].state; const child = route.ancestors[index].child;
      parent.children = merkleReplaceChild(parent.children, merkleNodeEdge(child, changed, merkleNodeDigest(tree, quarterId, finalizationId, changed)));
      parent.dirty = true; changed = parent;
    }
    currentRoot = merkleNodeDigest(tree, quarterId, finalizationId, rootState);
  }
  function createBranch(path, otherLeaf) {
    const depth = merkleDivergence(path, otherLeaf);
    const prefix = path.keyHash.slice(0, depth * 2); const state = stateFor(depth, prefix);
    if (state.exists) fail("INVALID_SOURCE", "Merkle split node already exists outside the sealed route");
    const nextDigest = merkleLeafHash(tree, quarterId, finalizationId, path, path.nextValue);
    state.exists = true; state.created = true; state.dirty = true;
    state.children = [
      merkleLeafEdge(merkleByte(otherLeaf, depth), otherLeaf, otherLeaf.digest),
      merkleLeafEdge(merkleByte(path, depth), path, nextDigest),
    ].sort((left, right) => left[0] - right[0]);
    merkleChildrenJson(state.children); return state;
  }
  function createBranchAboveNode(path, oldEdge, divergence) {
    const depth = integer(divergence, "Merkle split depth", 1);
    const prefix = path.keyHash.slice(0, depth * 2); const state = stateFor(depth, prefix);
    if (state.exists) fail("INVALID_SOURCE", "Merkle split node already exists outside the sealed route");
    const oldDepth = oldEdge[2]; const oldPrefix = oldEdge[3]; const oldDigest = oldEdge[4];
    if (oldEdge[1] !== "N" || oldDepth <= depth || !oldPrefix.startsWith(prefix)) {
      fail("INVALID_SOURCE", "Merkle split branch is invalid");
    }
    const nextDigest = merkleLeafHash(tree, quarterId, finalizationId, path, path.nextValue);
    state.exists = true; state.created = true; state.dirty = true;
    state.children = [
      merkleLeafEdge(merkleByte(path, depth), path, nextDigest),
      [Number.parseInt(oldPrefix.slice(depth * 2, depth * 2 + 2), 16), "N", oldDepth, oldPrefix, oldDigest],
    ].sort((left, right) => left[0] - right[0]);
    merkleChildrenJson(state.children); return state;
  }
  function replace(key, oldValue, nextValue) {
    if (nextValue === null) fail("INVALID_ARGUMENT", "Merkle map removal is unsupported");
    const path = merklePath(tree, key); path.nextValue = nextValue;
    const route = provePath(path, oldValue); const nextDigest = merkleLeafHash(tree, quarterId, finalizationId, path, nextValue);
    if (route.kind === "EMPTY") {
      if (rootState.exists) fail("INVALID_SOURCE", "Merkle empty root changed");
      rootState.exists = true; rootState.created = true; rootState.dirty = true;
      rootState.children = [merkleLeafEdge(merkleByte(path, 0), path, nextDigest)];
      currentRoot = merkleNodeDigest(tree, quarterId, finalizationId, rootState); return;
    }
    const terminal = route.ancestors.at(-1).state; const child = route.ancestors.at(-1).child;
    if (route.kind === "MISSING") {
      terminal.children = merkleReplaceChild(terminal.children, merkleLeafEdge(child, path, nextDigest)); terminal.dirty = true;
      updateAncestors(route, terminal); return;
    }
    if (route.kind === "DIVERGENT_BRANCH") {
      const branch = createBranchAboveNode(path, route.entry, route.divergence);
      terminal.children = merkleReplaceChild(terminal.children,
        merkleNodeEdge(child, branch, merkleNodeDigest(tree, quarterId, finalizationId, branch)));
      terminal.dirty = true; updateAncestors(route, terminal); return;
    }
    if (route.keyHash === path.keyHash) {
      terminal.children = merkleReplaceChild(terminal.children, merkleLeafEdge(child, path, nextDigest)); terminal.dirty = true;
      updateAncestors(route, terminal); return;
    }
    const branch = createBranch(path, route);
    terminal.children = merkleReplaceChild(terminal.children, merkleNodeEdge(child, branch, merkleNodeDigest(tree, quarterId, finalizationId, branch)));
    terminal.dirty = true; updateAncestors(route, terminal);
  }
  function flush() {
    for (const state of states.values()) if (state.dirty) {
      if (state.created) tx.create(state.ref, sealMerkleNode(tree, quarterId, finalizationId, state));
      else tx.set(state.ref, sealMerkleNode(tree, quarterId, finalizationId, state), {merge: false});
    }
  }
  return {prove: (key, value) => provePath(merklePath(tree, key), value), replace, flush, get root() { return currentRoot; }};
}
function nonce() { return randomBytes(24).toString("hex"); }

function quarterFrom(raw, policy) {
  const quarter = object(raw, "quarter");
  const verified = policyValue(() => policy.utcQuarterWindow(integer(quarter.startMs, "quarter.startMs")),
    "season quarter is invalid");
  if (quarter.id !== verified.id || quarter.endMs !== verified.endMs || quarter.year !== verified.year || quarter.quarter !== verified.quarter) {
    fail("INVALID_SOURCE", "season quarter is inconsistent");
  }
  return verified;
}
function validateLease(lease) {
  if (lease === null) return null;
  if (!lease || !/^[a-f0-9]{48}$/.test(lease.token || "") || !Number.isSafeInteger(lease.acquiredAtMs) ||
      lease.acquiredAtMs < 0 || !Number.isSafeInteger(lease.expiresAtMs) || lease.expiresAtMs <= lease.acquiredAtMs) {
    fail("INVALID_SOURCE", "job lease requires repair");
  }
  return lease;
}
function validateJob(raw, policy) {
  if (!raw || raw.schemaVersion !== SCHEMA_VERSION || !PHASES.includes(raw.phase)) fail("INVALID_SOURCE", "season job is absent or malformed");
  const {checkpointDigest, ...body} = raw;
  if (checkpointDigest !== hash(body)) fail("INVALID_SOURCE", "season checkpoint digest changed");
  const finalizationId = documentId(raw.finalizationId, "finalizationId");
  const quarter = quarterFrom(raw.quarter, policy);
  if (raw.calculationVersion !== CALCULATION_VERSION || raw.policyVersion !== policy.POLICY_VERSION ||
      typeof raw.identityHash !== "string" || raw.identityHash !== hash(raw.identity) ||
      raw.identity?.quarterId !== quarter.id || raw.identity?.quarterStartMs !== quarter.startMs ||
      raw.identity?.quarterEndMs !== quarter.endMs || raw.identity?.calculationVersion !== CALCULATION_VERSION ||
      raw.identity?.policyVersion !== policy.POLICY_VERSION || raw.identity?.finalizationId !== finalizationId) {
    fail("INVALID_SOURCE", "season job identity changed");
  }
  if (raw.identity.memberSourcePath !== `arena_seasons/${quarter.id}/members` ||
      raw.identity.voteSourcePath !== `arena_season_votes/${quarter.id}/votes`) {
    fail("INVALID_SOURCE", "season job source provenance changed");
  }
  const requestedByUid = documentId(raw.requestedByUid, "requestedByUid");
  const requestedMutationId = text(raw.requestedMutationId, "requestedMutationId");
  const requestDocumentId = documentId(raw.requestDocumentId, "requestDocumentId");
  const payload = {quarterId: quarter.id};
  const expectedPayloadHash = reservationHash(["FINALIZE_SEASON", payload]);
  const expectedRequestDigest = reservationHash([requestedByUid, requestedMutationId, "FINALIZE_SEASON", payload]);
  if (requestDocumentId !== mutationDocumentId(requestedByUid, requestedMutationId) ||
      raw.requestedPayloadHash !== expectedPayloadHash || raw.identity.requestDigest !== expectedRequestDigest ||
      finalizationId !== hash(["arena-season-finalization", quarter.id, requestDocumentId])) {
    fail("INVALID_SOURCE", "season job request binding changed");
  }
  for (const name of [
    "revision", "memberCount", "memberVerifyCount", "voteCount", "selectionInputCount",
    "selectionCount", "verifiedSelectionInputCount", "verifiedSelectionCount", "selectionCompareCount",
    "aggregateInputCount", "aggregateCount", "verifiedAggregateInputCount", "verifiedAggregateCount",
    "aggregateCompareCount", "winnerInputCount", "eligibleLessons", "integrityCount",
  ]) {
    integer(raw[name], name);
  }
  for (const name of [
    "memberCursor", "memberVerifyCursor", "voteCursor", "selectionCursor", "selectionVerifyCursor",
    "selectionCompareCursor", "aggregateCursor", "aggregateVerifyCursor", "aggregateCompareCursor", "winnerCursor", "integrityCursor",
  ]) {
    if (raw[name] !== null) documentId(raw[name], name);
  }
  for (const name of [
    "memberChainHash", "voteChainHash", "selectionChainHash", "aggregateChainHash", "candidateChainHash",
    "verifiedAggregateChainHash", "integrityChainHash", "selectionContentHash", "aggregateContentHash",
  ]) {
    if (!/^[a-f0-9]{64}$/.test(raw[name] || "")) fail("INVALID_SOURCE", `${name} is invalid`);
  }
  if (raw.memberVerifyCount > raw.memberCount || raw.selectionInputCount > raw.voteCount ||
      raw.verifiedSelectionInputCount > raw.voteCount || raw.selectionCompareCount > raw.selectionCount ||
      raw.aggregateInputCount > raw.selectionCount || raw.verifiedAggregateInputCount > raw.selectionCount ||
      raw.aggregateCompareCount > raw.aggregateCount || raw.winnerInputCount > raw.verifiedAggregateCount) {
    fail("INVALID_SOURCE", "season checkpoint counters are inconsistent");
  }
  const phaseIndex = PHASES.indexOf(raw.phase);
  const after = phase => raw.phase !== "REQUIRES_REVIEW" && phaseIndex >= PHASES.indexOf(phase);
  // An empty map has no leaf to trigger a proof later.  Its root is therefore constrained at the
  // completed phase boundary as well, so a self-sealed nonempty head cannot hide in a no-winner
  // season merely because every pageable map is empty.
  if (after("COMPARE_SELECTION") && raw.selectionCount === 0 &&
      raw.selectionContentHash !== merkleEmptyHash(TREE_CONFIGS.selection, 0)) {
    fail("INVALID_SOURCE", "empty selection map has a noncanonical Merkle root");
  }
  if (after("COMPARE_AGGREGATES") && raw.aggregateCount === 0 &&
      raw.aggregateContentHash !== merkleEmptyHash(TREE_CONFIGS.aggregate, 0)) {
    fail("INVALID_SOURCE", "empty aggregate map has a noncanonical Merkle root");
  }
  if (after("SCAN_VOTES") && raw.memberVerifyCount !== raw.memberCount) {
    fail("INVALID_SOURCE", "frozen member checkpoint count changed");
  }
  if (after("VERIFY_SELECTION") && raw.selectionInputCount !== raw.voteCount) {
    fail("INVALID_SOURCE", "frozen vote checkpoint count changed");
  }
  if (after("COMPARE_SELECTION") &&
      (raw.verifiedSelectionInputCount !== raw.voteCount || raw.verifiedSelectionCount !== raw.selectionCount)) {
    fail("INVALID_SOURCE", "verified selection checkpoint count changed");
  }
  if (after("AGGREGATE") && raw.selectionCompareCount !== raw.selectionCount) {
    fail("INVALID_SOURCE", "selected checkpoint comparison is incomplete");
  }
  if (after("VERIFY_AGGREGATES") && raw.aggregateInputCount !== raw.selectionCount) {
    fail("INVALID_SOURCE", "aggregate input checkpoint count changed");
  }
  if (after("COMPARE_AGGREGATES") &&
      (raw.verifiedAggregateInputCount !== raw.selectionCount || raw.verifiedAggregateCount !== raw.aggregateCount)) {
    fail("INVALID_SOURCE", "verified aggregate checkpoint count changed");
  }
  if (after("PICK_WINNER") && raw.aggregateCompareCount !== raw.aggregateCount) {
    fail("INVALID_SOURCE", "aggregate checkpoint comparison is incomplete");
  }
  if (after("VERIFY_CHECKPOINTS") && raw.winnerInputCount !== raw.verifiedAggregateCount) {
    fail("INVALID_SOURCE", "aggregate checkpoint count changed");
  }
  if (raw.phase !== "REQUIRES_REVIEW") {
    if (raw.phase === "VERIFY_MEMBERS") {
      // A resumed verification keeps its cursor/count; all other phases retain only its result.
    } else if (raw.memberVerifyCursor !== null) {
      fail("INVALID_SOURCE", "member verification cursor escaped its phase");
    }
    if (raw.phase === "VERIFY_SELECTION") {
      // A resumed verification keeps its cursor/count; all other phases retain only its result.
    } else if (raw.selectionVerifyCursor !== null) {
      fail("INVALID_SOURCE", "selection verification cursor escaped its phase");
    }
    if (raw.phase === "COMPARE_SELECTION") {
      // A resumed comparison keeps its cursor/count; all other phases retain only its result.
    } else if (raw.selectionCompareCursor !== null) {
      fail("INVALID_SOURCE", "selection comparison cursor escaped its phase");
    }
    if (raw.phase === "VERIFY_AGGREGATES") {
      // A resumed verification keeps its cursor/count; all other phases retain only its result.
    } else if (raw.aggregateVerifyCursor !== null) {
      fail("INVALID_SOURCE", "aggregate verification cursor escaped its phase");
    }
    if (raw.phase === "COMPARE_AGGREGATES") {
      // A resumed comparison keeps its cursor/count; all other phases retain only its result.
    } else if (raw.aggregateCompareCursor !== null) {
      fail("INVALID_SOURCE", "aggregate comparison cursor escaped its phase");
    }
    if (raw.phase === "VERIFY_CHECKPOINTS") {
      if (!INTEGRITY_STAGES.includes(raw.integrityStage)) fail("INVALID_SOURCE", "checkpoint audit stage is invalid");
    } else if (raw.integrityStage !== null || raw.integrityCursor !== null || raw.integrityCount !== 0) {
      fail("INVALID_SOURCE", "checkpoint audit state escaped its phase");
    }
  }
  validateLease(raw.lease);
  if (raw.winner !== null) validateWinner(raw.winner, policy);
  integer(raw.createdAtMs, "createdAtMs"); integer(raw.updatedAtMs, "updatedAtMs");
  if (raw.phase === "PUBLISHED" && !/^[a-f0-9]{64}$/.test(raw.calculationManifestDigest || "")) {
    fail("INVALID_SOURCE", "published season job lacks manifest digest");
  }
  return {...raw, quarter};
}
function owns(job, token, at) { return job.lease?.token === token && job.lease.expiresAtMs > at; }
function checkpoint(job, patch, now) {
  return seal({...job, ...patch, revision: add(job.revision, 1, "revision"), updatedAtMs: now});
}

function validateMember(sourceId, raw) {
  const value = object(raw, "season member");
  const lessonId = documentId(value.lessonId, "member.lessonId");
  if (lessonId !== sourceId) fail("INVALID_SOURCE", "member document ID disagrees with lessonId");
  const result = {
    lessonId, authorUid: documentId(value.authorUid, "member.authorUid"), questType: text(value.questType, "member.questType"),
    publishedAtMs: integer(value.publishedAtMs, "member.publishedAtMs"),
    contentVersion: integer(value.contentVersion, "member.contentVersion", 1),
    capturedAtMs: integer(value.capturedAtMs, "member.capturedAtMs"),
  };
  return result;
}
function validateRating(raw) {
  const value = object(raw, "season rating");
  const rating = integer(value.rating, "rating.rating", 1);
  if (rating > 3) fail("INVALID_SOURCE", "rating must be in 1..3");
  return {
    ratingId: documentId(value.ratingId, "rating.ratingId"), uid: documentId(value.uid, "rating.uid"),
    lessonId: documentId(value.lessonId, "rating.lessonId"), attemptId: documentId(value.attemptId, "rating.attemptId"),
    rating, acceptedAtMs: integer(value.acceptedAtMs, "rating.acceptedAtMs"),
    acceptedSequence: integer(value.acceptedSequence === undefined ? 0 : value.acceptedSequence, "rating.acceptedSequence"),
  };
}
function validateCompletion(raw) {
  const value = object(raw, "season completion");
  const status = text(value.status, "completion.status");
  if (!TERMINAL_COMPLETIONS.has(status)) fail("INVALID_SOURCE", "unsupported completion status");
  if (typeof value.completed !== "boolean") fail("INVALID_SOURCE", "completion.completed must be boolean");
  return {
    attemptId: documentId(value.attemptId, "completion.attemptId"), uid: documentId(value.uid, "completion.uid"),
    lessonId: documentId(value.lessonId, "completion.lessonId"), status, completed: value.completed,
    settledAtMs: status === "SETTLED" ? integer(value.settledAtMs, "completion.settledAtMs") : null,
  };
}
function validateVote(sourceId, raw) {
  const value = object(raw, "season vote");
  if (value.activityKind !== "ARENA") fail("INVALID_SOURCE", "season vote has non-Arena provenance");
  // This source may predate the new write cap. Keep it addressable for historical finalization,
  // but omit the audit-only value from every frozen worker row below.
  text(value.mutationId, "vote.mutationId");
  const result = frozenVoteRow(sourceId, value.vote, value.completion, value.authorUidAtAcceptance);
  // mutationId remains only in the immutable source receipt. It is neither ranking input nor a
  // required audit datum for a frozen candidate, so do not multiply it across worker chunks.
  return result;
}
function frozenVoteRow(sourceId, rating, completion, authorUidAtAcceptance) {
  const result = {
    sourceId: documentId(sourceId, "vote sourceId"), rating: validateRating(rating), completion: validateCompletion(completion),
    authorUidAtAcceptance: documentId(authorUidAtAcceptance, "vote.authorUidAtAcceptance"),
  };
  return {...result, sourceDigest: hash(result)};
}
function validateFrozenVote(raw) {
  const value = exactObject(raw, "frozen season vote", ["sourceId", "rating", "completion", "authorUidAtAcceptance", "sourceDigest"]);
  const row = frozenVoteRow(value.sourceId, value.rating, value.completion, value.authorUidAtAcceptance);
  if (value.sourceDigest !== row.sourceDigest) fail("INVALID_SOURCE", "stored vote source digest changed");
  return row;
}
function validateFrozenVoteCheckpoint(raw, {quarterId, finalizationId, expectedSourceId = null, checkpointId = null} = {}) {
  const value = validateEnvelope(raw, "frozen vote checkpoint", "FROZEN_VOTE", ["sourceId", "row"], {quarterId, finalizationId});
  const row = validateFrozenVote(value.row);
  if (value.sourceId !== row.sourceId || (expectedSourceId !== null && row.sourceId !== expectedSourceId) ||
      (checkpointId !== null && checkpointId !== refId("vote", row.sourceId))) {
    fail("INVALID_SOURCE", "frozen vote checkpoint binding changed");
  }
  return row;
}
function validateFrozenMember(raw, {quarterId, finalizationId, expectedLessonId = null, checkpointId = null} = {}) {
  const value = validateEnvelope(raw, "frozen member checkpoint", "FROZEN_MEMBER", ["sourceId", "member"], {quarterId, finalizationId});
  const member = validateMember(value.sourceId, value.member);
  if ((expectedLessonId !== null && member.lessonId !== expectedLessonId) ||
      (checkpointId !== null && checkpointId !== refId("member", member.lessonId))) {
    fail("INVALID_SOURCE", "frozen member checkpoint binding changed");
  }
  return member;
}
function validateRatingIndex(raw, {quarterId, finalizationId, expectedRatingId} = {}) {
  if (!raw) return null;
  const value = validateEnvelope(raw, "frozen rating index", "FROZEN_RATING_INDEX", ["id", "rating"], {quarterId, finalizationId});
  const rating = validateRating(value.rating);
  if (value.id !== expectedRatingId || rating.ratingId !== expectedRatingId) {
    fail("INVALID_SOURCE", "frozen rating index binding changed");
  }
  return {id: value.id, digest: hash(rating), rating};
}
function validateCompletionIndex(raw, {quarterId, finalizationId, expectedAttemptId} = {}) {
  if (!raw) return null;
  const value = validateEnvelope(raw, "frozen completion index", "FROZEN_COMPLETION_INDEX", ["id", "completion"], {quarterId, finalizationId});
  const completion = validateCompletion(value.completion);
  if (value.id !== expectedAttemptId || completion.attemptId !== expectedAttemptId) {
    fail("INVALID_SOURCE", "frozen completion index binding changed");
  }
  return {id: value.id, digest: hash(completion), completion};
}
function voteDigest(row) {
  return validateFrozenVote(row).sourceDigest;
}
function validateSelectedCandidate(raw) {
  const value = exactObject(raw, "selected season candidate", ["sourceId", "rating", "completion", "authorUidAtAcceptance", "member", "sourceDigest"]);
  const vote = validateFrozenVote({sourceId: value.sourceId, rating: value.rating, completion: value.completion,
    authorUidAtAcceptance: value.authorUidAtAcceptance, sourceDigest: value.sourceDigest});
  const member = validateMember(value.member?.lessonId, value.member);
  if (member.lessonId !== vote.rating.lessonId) fail("INVALID_SOURCE", "selected candidate member changed");
  return {...vote, member};
}
function validateSelectedCheckpoint(raw, {quarterId, finalizationId, kind = "SELECTED_WORKING", expectedLessonId = null,
  expectedUid = null, checkpointId = null} = {}) {
  if (kind !== "SELECTED_WORKING" && kind !== "SELECTED_VERIFIED") fail("INVALID_SOURCE", "selected checkpoint kind is invalid");
  const value = validateEnvelope(raw, "selected checkpoint", kind, ["candidate"], {quarterId, finalizationId});
  const candidate = validateSelectedCandidate(value.candidate);
  if ((expectedLessonId !== null && candidate.rating.lessonId !== expectedLessonId) ||
      (expectedUid !== null && candidate.rating.uid !== expectedUid) ||
      (checkpointId !== null && checkpointId !== refId("selected", [candidate.rating.lessonId, candidate.rating.uid]))) {
    fail("INVALID_SOURCE", "selected checkpoint binding changed");
  }
  return candidate;
}
function validateAggregate(raw, policy, {quarterId, finalizationId, kind = "AGGREGATE_WORKING", expectedLessonId = null,
  checkpointId = null} = {}) {
  if (kind !== "AGGREGATE_WORKING" && kind !== "AGGREGATE_VERIFIED") fail("INVALID_SOURCE", "aggregate checkpoint kind is invalid");
  const value = exactObject(raw, "stored aggregate", ["kind", "quarterId", "finalizationId", "lessonId", "authorUid", "publishedAtMs",
    "ratingSum", "ratingCount", "contributorChainHash", "weightedScore", "aggregateDigest"]);
  const {aggregateDigest, ...body} = value;
  if (body.kind !== kind || body.quarterId !== quarterId || body.finalizationId !== finalizationId ||
      !/^[a-f0-9]{64}$/.test(aggregateDigest || "") || aggregateDigest !== hash(body)) {
    fail("INVALID_SOURCE", "aggregate checkpoint changed");
  }
  const aggregate = {lessonId: documentId(value.lessonId, "aggregate.lessonId"), authorUid: documentId(value.authorUid, "aggregate.authorUid"),
    publishedAtMs: integer(value.publishedAtMs, "aggregate.publishedAtMs"), ratingSum: integer(value.ratingSum, "aggregate.ratingSum"),
    ratingCount: integer(value.ratingCount, "aggregate.ratingCount"), contributorChainHash: value.contributorChainHash,
    weightedScore: policyValue(() => policy.weightedScore(value), "aggregate score is invalid")};
  if (!/^[a-f0-9]{64}$/.test(aggregate.contributorChainHash || "") || !same(aggregate.weightedScore, value.weightedScore) ||
      (expectedLessonId !== null && aggregate.lessonId !== expectedLessonId) ||
      (checkpointId !== null && checkpointId !== refId("aggregate", aggregate.lessonId))) {
    fail("INVALID_SOURCE", "aggregate checkpoint binding changed");
  }
  return aggregate;
}
function compareTextBytes(left, right) {
  return Buffer.compare(Buffer.from(left), Buffer.from(right));
}
function validateIndex(previous, id, digest, field) {
  if (!previous) return;
  if (previous.id !== id || previous.digest !== digest) fail("INVALID_SOURCE", `conflicting duplicate ${field}`);
}
function later(left, right) {
  if (!right) return true;
  if (left.acceptedAtMs !== right.rating.acceptedAtMs) return left.acceptedAtMs > right.rating.acceptedAtMs;
  if (left.acceptedSequence !== right.rating.acceptedSequence) return left.acceptedSequence > right.rating.acceptedSequence;
  if (left.ratingId !== right.rating.ratingId) return left.ratingId < right.rating.ratingId;
  return left.sourceId < right.sourceId;
}
function eligible(member, rating, completion, quarter) {
  if (!member || member.questType !== "REGULAR" || member.publishedAtMs >= quarter.endMs || rating.lessonId !== member.lessonId ||
      rating.uid === member.authorUid || rating.acceptedAtMs < Math.max(quarter.startMs, member.publishedAtMs) ||
      rating.acceptedAtMs >= quarter.endMs || !completion || completion.status !== "SETTLED" || completion.completed !== true ||
      completion.uid !== rating.uid || completion.lessonId !== rating.lessonId || completion.settledAtMs > rating.acceptedAtMs) return false;
  return true;
}
function validateWinner(raw, policy) {
  const value = object(raw, "winner");
  const winner = {
    lessonId: documentId(value.lessonId, "winner.lessonId"), authorUid: documentId(value.authorUid, "winner.authorUid"),
    publishedAtMs: integer(value.publishedAtMs, "winner.publishedAtMs"), ratingSum: integer(value.ratingSum, "winner.ratingSum"),
    ratingCount: integer(value.ratingCount, "winner.ratingCount", policy.MIN_SEASON_RATERS || 20),
    weightedScore: policyValue(() => policy.weightedScore(value), "winner score is invalid"),
  };
  if (!same(winner.weightedScore, value.weightedScore)) fail("INVALID_SOURCE", "winner score is not exact");
  return winner;
}
function candidateBeats(left, right, policy) {
  if (!right) return true;
  const score = policyValue(() => policy.compareScores(left.weightedScore, right.weightedScore), "candidate score is invalid");
  if (score !== 0) return score > 0;
  if (left.ratingCount !== right.ratingCount) return left.ratingCount > right.ratingCount;
  if (left.publishedAtMs !== right.publishedAtMs) return left.publishedAtMs < right.publishedAtMs;
  return left.lessonId < right.lessonId;
}
function summary(row) {
  return row ? {lessonId: row.lessonId, authorUid: row.authorUid, publishedAtMs: row.publishedAtMs,
    ratingSum: row.ratingSum, ratingCount: row.ratingCount, weightedScore: row.weightedScore} : null;
}
function manifest(job) {
  return {
    calculationVersion: job.calculationVersion, policyVersion: job.policyVersion, quarter: job.quarter,
    identityHash: job.identityHash, memberCount: job.memberCount, memberChainHash: job.memberChainHash,
    memberVerifyCount: job.memberVerifyCount, voteCount: job.voteCount, voteChainHash: job.voteChainHash,
    selectionInputCount: job.selectionInputCount, selectionCount: job.selectionCount,
    verifiedSelectionInputCount: job.verifiedSelectionInputCount, verifiedSelectionCount: job.verifiedSelectionCount,
    selectionCompareCount: job.selectionCompareCount, selectionChainHash: job.selectionChainHash,
    selectionContentHash: job.selectionContentHash,
    aggregateInputCount: job.aggregateInputCount, aggregateCount: job.aggregateCount, aggregateChainHash: job.aggregateChainHash,
    verifiedAggregateInputCount: job.verifiedAggregateInputCount, verifiedAggregateCount: job.verifiedAggregateCount,
    aggregateCompareCount: job.aggregateCompareCount, verifiedAggregateChainHash: job.verifiedAggregateChainHash,
    aggregateContentHash: job.aggregateContentHash,
    winnerInputCount: job.winnerInputCount, eligibleLessons: job.eligibleLessons, candidateChainHash: job.candidateChainHash,
    integrityChainHash: job.integrityChainHash, winner: summary(job.winner),
  };
}

function queryFor(db, path, cursor, limit) {
  let query = db.collection(path);
  if (typeof query.orderBy !== "function" || typeof query.limit !== "function" ||
      (cursor !== null && typeof query.startAfter !== "function")) {
    fail("UNAVAILABLE", "season paging adapter is unavailable");
  }
  // Firestore owns document-name collation. Never sort a QuerySnapshot with JavaScript locale
  // rules: their UTF-16 order differs from Firestore's UTF-8 document-name ordering.
  query = query.orderBy("__name__");
  if (cursor !== null) query = query.startAfter(cursor);
  query = query.limit(limit + 1);
  return query;
}
async function page(tx, db, path, cursor, limit = PAGE_SIZE) {
  const snapshot = await tx.get(queryFor(db, path, cursor, limit));
  if (!snapshot || !Array.isArray(snapshot.docs)) fail("UNAVAILABLE", "season source page is unavailable");
  const docs = snapshot.docs.map(doc => {
    documentId(doc.id, "page document ID");
    return doc;
  });
  if (docs.length > limit + 1) fail("UNAVAILABLE", "season source page exceeded its bound");
  const picked = docs.slice(0, limit);
  return {docs: picked, hasMore: docs.length > limit, nextCursor: picked.length ? picked.at(-1).id : cursor};
}

function initialJob({quarter, finalizationId, request, now, policy}) {
  const identity = {quarterId: quarter.id, quarterStartMs: quarter.startMs, quarterEndMs: quarter.endMs,
    finalizationId, calculationVersion: CALCULATION_VERSION, policyVersion: policy.POLICY_VERSION,
    memberSourcePath: `arena_seasons/${quarter.id}/members`, voteSourcePath: `arena_season_votes/${quarter.id}/votes`,
    requestDigest: request.requestDigest};
  const seed = hash([]);
  const selectionRoot = merkleEmptyHash(TREE_CONFIGS.selection, 0);
  const aggregateRoot = merkleEmptyHash(TREE_CONFIGS.aggregate, 0);
  return seal({schemaVersion: SCHEMA_VERSION, finalizationId, quarter, calculationVersion: CALCULATION_VERSION,
    policyVersion: policy.POLICY_VERSION, identity, identityHash: hash(identity), phase: "SCAN_MEMBERS", revision: 0,
    requestedByUid: request.uid, requestedMutationId: request.mutationId, requestedPayloadHash: request.payloadHash,
    requestDocumentId: request.documentId, memberCursor: null, memberCount: 0, memberChainHash: seed,
    memberVerifyCursor: null, memberVerifyCount: 0,
    voteCursor: null, voteCount: 0, voteChainHash: seed,
    selectionCursor: null, selectionInputCount: 0, selectionCount: 0, selectionChainHash: seed,
    selectionVerifyCursor: null, verifiedSelectionInputCount: 0, verifiedSelectionCount: 0,
    selectionCompareCursor: null, selectionCompareCount: 0, selectionContentHash: selectionRoot,
    aggregateCursor: null, aggregateInputCount: 0, aggregateCount: 0, aggregateChainHash: seed,
    aggregateVerifyCursor: null, verifiedAggregateInputCount: 0, verifiedAggregateCount: 0, verifiedAggregateChainHash: seed,
    aggregateCompareCursor: null, aggregateCompareCount: 0, aggregateContentHash: aggregateRoot,
    winnerCursor: null, winnerInputCount: 0, eligibleLessons: 0, candidateChainHash: seed,
    integrityStage: null, integrityCursor: null, integrityCount: 0, integrityChainHash: seed,
    winner: null,
    calculationManifestDigest: null, lease: null, createdAtMs: now, updatedAtMs: now});
}
function pendingResponse(quarterId, finalizationId, requestedAtMs, status = "PENDING") {
  return {schemaVersion: 2, operation: "FINALIZE_SEASON", status, quarterId, finalizationId, requestedAtMs};
}

function createArenaSeasonFinalizer({db, policy, nowMs = Date.now, prepareAward, applyAward, createDecision}) {
  if (!db || typeof db.runTransaction !== "function" || !policy || typeof policy.utcQuarterWindow !== "function" ||
      typeof nowMs !== "function" || typeof prepareAward !== "function" || typeof applyAward !== "function" ||
      typeof createDecision !== "function") throw new TypeError("Trusted season finalizer dependencies required");

  const now = () => integer(nowMs(), "server now");
  const job = (quarterId, finalizationId) => jobRef(db, quarterId, finalizationId);
  function finalizationIdFor(request, quarter) { return hash(["arena-season-finalization", quarter.id, request.documentId]); }

  function begin(tx, {request, quarter, now: at, oldSeason}) {
    const finalizationId = finalizationIdFor(request, quarter);
    const reservation = reserveMutation(tx, request, {quarterId: quarter.id, finalizationId, now: at});
    const record = initialJob({quarter, finalizationId, request, now: at, policy});
    tx.create(job(quarter.id, finalizationId), record);
    tx.set(seasonRef(db, quarter.id), {quarter, status: "FINALIZING", finalization: {
      schemaVersion: 2, finalizationId, calculationVersion: CALCULATION_VERSION,
      requestedByUid: request.uid, requestedMutationId: request.mutationId, requestDigest: request.requestDigest,
      reservationDocumentId: request.documentId, startedAtMs: at,
    }, ...(oldSeason?.createdAtMs ? {createdAtMs: oldSeason.createdAtMs} : {createdAtMs: at})}, {merge: false});
    return {reservation, response: pendingResponse(quarter.id, finalizationId, at)};
  }

  function validateSeason(season, quarter, finalizationId) {
    if (!season || season.status !== "FINALIZING" || !same(season.quarter, quarter) ||
        season.finalization?.schemaVersion !== 2 || season.finalization?.finalizationId !== finalizationId ||
        season.finalization?.calculationVersion !== CALCULATION_VERSION) {
      fail("INVALID_SOURCE", "season finalization fence changed");
    }
  }
  function validateTerminalSeason(season, current, finalizationId) {
    const expected = current.phase === "PUBLISHED" ? "FINALIZED" : "REQUIRES_REVIEW";
    if (!season || season.status !== expected || !same(season.quarter, current.quarter) ||
        season.finalization?.schemaVersion !== 2 || season.finalization?.finalizationId !== finalizationId ||
        season.finalization?.calculationVersion !== CALCULATION_VERSION) {
      fail("INVALID_SOURCE", "terminal season finalization fence changed");
    }
  }
  async function acquire(quarterId, finalizationId) {
    return db.runTransaction(async tx => {
      const at = now(); const current = validateJob(data(await tx.get(job(quarterId, finalizationId))), policy);
      const season = data(await tx.get(seasonRef(db, quarterId)));
      if (TERMINAL_PHASES.has(current.phase)) {
        validateTerminalSeason(season, current, finalizationId);
        return {kind: current.phase, job: current};
      }
      validateSeason(season, current.quarter, finalizationId);
      if (current.lease && current.lease.expiresAtMs > at) return {kind: "BUSY", retryAtMs: current.lease.expiresAtMs};
      const token = nonce(); const next = checkpoint(current, {lease: {token, acquiredAtMs: at, expiresAtMs: add(at, LEASE_MS, "lease")}}, at);
      tx.set(job(quarterId, finalizationId), next, {merge: false});
      return {kind: "ACQUIRED", token, job: next};
    });
  }
  async function release(quarterId, finalizationId, token) {
    return db.runTransaction(async tx => {
      const at = now(); const current = validateJob(data(await tx.get(job(quarterId, finalizationId))), policy);
      if (!owns(current, token, at) || TERMINAL_PHASES.has(current.phase)) return false;
      tx.set(job(quarterId, finalizationId), checkpoint(current, {lease: null}, at), {merge: false});
      return true;
    });
  }
  async function markReview(quarterId, finalizationId, token, code) {
    return db.runTransaction(async tx => {
      const at = now(); const current = validateJob(data(await tx.get(job(quarterId, finalizationId))), policy);
      if (!owns(current, token, at)) return false;
      validateSeason(data(await tx.get(seasonRef(db, quarterId))), current.quarter, finalizationId);
      const next = checkpoint(current, {phase: "REQUIRES_REVIEW", lease: null, reviewCode: /^[A-Z_]+$/.test(code) ? code : "INVALID_SOURCE"}, at);
      tx.set(job(quarterId, finalizationId), next, {merge: false});
      tx.set(seasonRef(db, quarterId), {status: "REQUIRES_REVIEW", reviewCode: next.reviewCode, reviewAtMs: at}, {merge: true});
      return true;
    });
  }
  async function update(quarterId, finalizationId, token, expectedPhase, builder) {
    return db.runTransaction(async tx => {
      const at = now(); const current = validateJob(data(await tx.get(job(quarterId, finalizationId))), policy);
      if (!owns(current, token, at)) return {kind: "LEASE_LOST"};
      if (current.phase !== expectedPhase) return {kind: current.phase};
      validateSeason(data(await tx.get(seasonRef(db, quarterId))), current.quarter, finalizationId);
      const next = await builder(tx, current, at);
      tx.set(job(quarterId, finalizationId), seal(next), {merge: false});
      return {kind: next.phase, phase: next.phase};
    });
  }
  function next(jobValue, patch, at) { return checkpoint(jobValue, patch, at); }

  async function scanMembers(quarterId, finalizationId, token) {
    return update(quarterId, finalizationId, token, "SCAN_MEMBERS", async (tx, current, at) => {
      const source = await page(tx, db, `arena_seasons/${quarterId}/members`, current.memberCursor);
      const prepared = source.docs.map(doc => ({sourceId: doc.id, member: validateMember(doc.id, doc.data())}));
      const existing = [];
      for (const item of prepared) existing.push(data(await tx.get(memberJobRef(db, quarterId, finalizationId, item.member.lessonId))));
      for (let index = 0; index < prepared.length; index++) {
        const old = existing[index]; const row = prepared[index];
        if (old) {
          const frozen = validateFrozenMember(old, {quarterId, finalizationId, expectedLessonId: row.member.lessonId,
            checkpointId: refId("member", row.member.lessonId)});
          if (!same(frozen, row.member) || old.sourceId !== row.sourceId) fail("INVALID_SOURCE", "member checkpoint changed");
        }
      }
      let count = current.memberCount; let digest = current.memberChainHash;
      for (let index = 0; index < prepared.length; index++) {
        const row = prepared[index];
        if (!existing[index]) {
          count = add(count, 1, "memberCount"); digest = chain(digest, {sourceId: row.sourceId, member: row.member});
          tx.create(memberJobRef(db, quarterId, finalizationId, row.member.lessonId),
            sealEnvelope("FROZEN_MEMBER", quarterId, finalizationId, {sourceId: row.sourceId, member: row.member}));
        }
      }
      return next(current, {memberCursor: source.nextCursor, memberCount: count, memberChainHash: digest,
        phase: source.hasMore ? "SCAN_MEMBERS" : "VERIFY_MEMBERS"}, at);
    });
  }

  async function verifyMembers(quarterId, finalizationId, token) {
    return update(quarterId, finalizationId, token, "VERIFY_MEMBERS", async (tx, current, at) => {
      const rows = await page(tx, db, `arena_seasons/${quarterId}/finalizations/${finalizationId}/members`, current.memberVerifyCursor);
      for (const doc of rows.docs) {
        const member = validateFrozenMember(data(doc), {quarterId, finalizationId, checkpointId: doc.id});
        const source = validateMember(member.lessonId,
          data(await tx.get(db.doc(`arena_seasons/${quarterId}/members/${member.lessonId}`))));
        if (!same(source, member)) fail("INVALID_SOURCE", "frozen member no longer matches its source");
      }
      const memberVerifyCount = add(current.memberVerifyCount, rows.docs.length, "memberVerifyCount");
      if (!rows.hasMore && memberVerifyCount !== current.memberCount) {
        fail("INVALID_SOURCE", "frozen member checkpoint count changed");
      }
      return next(current, {memberVerifyCursor: rows.hasMore ? rows.nextCursor : null, memberVerifyCount,
        phase: rows.hasMore ? "VERIFY_MEMBERS" : "SCAN_VOTES"}, at);
    });
  }

  async function scanVotes(quarterId, finalizationId, token) {
    return update(quarterId, finalizationId, token, "SCAN_VOTES", async (tx, current, at) => {
      const source = await page(tx, db, `arena_season_votes/${quarterId}/votes`, current.voteCursor);
      const prepared = source.docs.map(doc => validateVote(doc.id, doc.data()));
      const ratingRows = new Map(); const completionRows = new Map();
      for (const row of prepared) {
        const ratingDigest = hash(row.rating); const completionDigest = hash(row.completion);
        const priorRating = ratingRows.get(row.rating.ratingId);
        const priorCompletion = completionRows.get(row.completion.attemptId);
        if (priorRating && priorRating.digest !== ratingDigest) fail("INVALID_SOURCE", "conflicting duplicate ratingId");
        if (priorCompletion && priorCompletion.digest !== completionDigest) fail("INVALID_SOURCE", "conflicting duplicate attemptId");
        ratingRows.set(row.rating.ratingId, {row: row.rating, digest: ratingDigest});
        completionRows.set(row.completion.attemptId, {row: row.completion, digest: completionDigest});
      }
      const reads = [];
      for (const row of prepared) {
        reads.push({vote: data(await tx.get(voteJobRef(db, quarterId, finalizationId, row.sourceId)))});
      }
      const ratingIndexes = new Map(); const completionIndexes = new Map();
      for (const ratingId of ratingRows.keys()) ratingIndexes.set(ratingId,
        validateRatingIndex(data(await tx.get(ratingIndexRef(db, quarterId, finalizationId, ratingId))),
          {quarterId, finalizationId, expectedRatingId: ratingId}));
      for (const attemptId of completionRows.keys()) completionIndexes.set(attemptId,
        validateCompletionIndex(data(await tx.get(completionIndexRef(db, quarterId, finalizationId, attemptId))),
          {quarterId, finalizationId, expectedAttemptId: attemptId}));
      let count = current.voteCount; let digest = current.voteChainHash;
      for (let index = 0; index < prepared.length; index++) {
        const row = prepared[index]; const old = reads[index];
        if (old.vote) {
          const frozen = validateFrozenVoteCheckpoint(old.vote, {quarterId, finalizationId, expectedSourceId: row.sourceId,
            checkpointId: refId("vote", row.sourceId)});
          if (!same(frozen, row)) fail("INVALID_SOURCE", "vote checkpoint changed");
        }
        validateIndex(ratingIndexes.get(row.rating.ratingId), row.rating.ratingId, ratingRows.get(row.rating.ratingId).digest, "ratingId");
        validateIndex(completionIndexes.get(row.completion.attemptId), row.completion.attemptId,
          completionRows.get(row.completion.attemptId).digest, "attemptId");
        if (!old.vote) {
          count = add(count, 1, "voteCount"); digest = chain(digest, {sourceId: row.sourceId, digest: row.sourceDigest});
          tx.create(voteJobRef(db, quarterId, finalizationId, row.sourceId),
            sealEnvelope("FROZEN_VOTE", quarterId, finalizationId, {sourceId: row.sourceId, row}));
        }
      }
      for (const [ratingId, item] of ratingRows) if (!ratingIndexes.get(ratingId)) {
        tx.create(ratingIndexRef(db, quarterId, finalizationId, ratingId),
          sealEnvelope("FROZEN_RATING_INDEX", quarterId, finalizationId, {id: ratingId, rating: item.row}));
      }
      for (const [attemptId, item] of completionRows) if (!completionIndexes.get(attemptId)) {
        tx.create(completionIndexRef(db, quarterId, finalizationId, attemptId),
          sealEnvelope("FROZEN_COMPLETION_INDEX", quarterId, finalizationId, {id: attemptId, completion: item.row}));
      }
      return next(current, {voteCursor: source.nextCursor, voteCount: count, voteChainHash: digest,
        phase: source.hasMore ? "SCAN_VOTES" : "SELECT"}, at);
    });
  }

  async function selectVotes(quarterId, finalizationId, token) {
    return update(quarterId, finalizationId, token, "SELECT", async (tx, current, at) => {
      const rows = await page(tx, db, `arena_seasons/${quarterId}/finalizations/${finalizationId}/votes`, current.selectionCursor);
      const prepared = rows.docs.map(doc => validateFrozenVoteCheckpoint(data(doc), {quarterId, finalizationId, checkpointId: doc.id}));
      const selectionRefs = new Map();
      for (const row of prepared) {
        const key = canonicalJson([row.rating.lessonId, row.rating.uid]);
        selectionRefs.set(key, {lessonId: row.rating.lessonId, uid: row.rating.uid,
          ref: selectedRef(db, quarterId, finalizationId, row.rating.lessonId, row.rating.uid)});
      }
      const reads = [];
      for (const row of prepared) {
        reads.push({member: data(await tx.get(memberJobRef(db, quarterId, finalizationId, row.rating.lessonId)))});
      }
      const selectedRows = new Map();
      for (const [key, item] of selectionRefs) {
        const selected = data(await tx.get(item.ref));
        const candidate = selected ? validateSelectedCheckpoint(selected, {quarterId, finalizationId,
          expectedLessonId: item.lessonId, expectedUid: item.uid}) : null;
        selectedRows.set(key, {item, candidate, changed: false});
      }
      let count = current.selectionCount; let digest = current.selectionChainHash;
      for (let index = 0; index < prepared.length; index++) {
        const row = prepared[index]; const read = reads[index];
        // Canonical arena-policy ignores a vote whose lesson was never in the frozen membership.
        // Treat it as ineligible rather than making a valid historical source impossible to close.
        if (!read.member) continue;
        const member = validateFrozenMember(read.member, {quarterId, finalizationId, expectedLessonId: row.rating.lessonId,
          checkpointId: refId("member", row.rating.lessonId)});
        // `authorUidAtAcceptance` is audit provenance. The established policy deliberately
        // ranks against the frozen member author, so it must not introduce a new exclusion rule.
        // The completion inside the sealed vote is the source-of-truth for eligibility. The
        // duplicate-detection index is intentionally not an input to the winner, so a stale
        // index cannot quietly turn a valid vote into an ineligible one.
        const completion = row.completion;
        if (!eligible(member, row.rating, completion, current.quarter)) continue;
        const candidate = {sourceId: row.sourceId, rating: row.rating, completion, authorUidAtAcceptance: row.authorUidAtAcceptance,
          member, sourceDigest: row.sourceDigest};
        const key = canonicalJson([row.rating.lessonId, row.rating.uid]); const selected = selectedRows.get(key);
        // Every eligible frozen vote contributes to the manifest chain, including a vote that is
        // superseded later in the scan. The selected row still remains exactly one latest vote
        // per (lesson, uid), matching arena-policy's global rule.
        digest = chain(digest, {key: [row.rating.lessonId, row.rating.uid], candidate});
        if (later(row.rating, selected.candidate)) {
          if (!selected.candidate) count = add(count, 1, "selectionCount");
          selected.candidate = candidate; selected.changed = true;
        }
      }
      for (const selected of selectedRows.values()) if (selected.changed) {
        tx.set(selected.item.ref, sealEnvelope("SELECTED_WORKING", quarterId, finalizationId,
          {candidate: selected.candidate}), {merge: false});
      }
      const selectionInputCount = add(current.selectionInputCount, prepared.length, "selectionInputCount");
      if (!rows.hasMore && selectionInputCount !== current.voteCount) {
        fail("INVALID_SOURCE", "frozen vote checkpoint count changed");
      }
      return next(current, {selectionCursor: rows.nextCursor, selectionInputCount, selectionCount: count, selectionChainHash: digest,
        phase: rows.hasMore ? "SELECT" : "VERIFY_SELECTION"}, at);
    });
  }

  // A second independent pass derives the authoritative selected rows from the frozen source.
  // The first SELECT pass is compared below, but AGGREGATE consumes only this verified output.
  // This prevents a stale, still-well-formed selected candidate from rolling a player back to an
  // earlier vote after SELECT has finished.
  async function verifySelection(quarterId, finalizationId, token) {
    return update(quarterId, finalizationId, token, "VERIFY_SELECTION", async (tx, current, at) => {
      const rows = await page(tx, db, `arena_seasons/${quarterId}/finalizations/${finalizationId}/votes`, current.selectionVerifyCursor);
      const prepared = rows.docs.map(doc => validateFrozenVoteCheckpoint(data(doc), {quarterId, finalizationId, checkpointId: doc.id}));
      const selectionRefs = new Map(); const reads = [];
      for (const row of prepared) {
        const key = canonicalJson([row.rating.lessonId, row.rating.uid]);
        selectionRefs.set(key, {lessonId: row.rating.lessonId, uid: row.rating.uid,
          ref: selectedVerifiedRef(db, quarterId, finalizationId, row.rating.lessonId, row.rating.uid)});
        const source = validateVote(row.sourceId,
          data(await tx.get(db.doc(`arena_season_votes/${quarterId}/votes/${row.sourceId}`))));
        if (!same(source, row)) fail("INVALID_SOURCE", "frozen vote no longer matches its source");
        reads.push({member: data(await tx.get(memberJobRef(db, quarterId, finalizationId, row.rating.lessonId)))});
      }
      const selectedRows = new Map();
      for (const [key, item] of selectionRefs) {
        const selected = data(await tx.get(item.ref));
        const candidate = selected ? validateSelectedCheckpoint(selected, {quarterId, finalizationId, kind: "SELECTED_VERIFIED",
          expectedLessonId: item.lessonId, expectedUid: item.uid}) : null;
        selectedRows.set(key, {item, candidate, oldCandidate: candidate, changed: false});
      }
      const selectionTree = await loadMerkleOverlay({tx, db, quarterId, finalizationId, tree: TREE_CONFIGS.selection,
        root: current.selectionContentHash, keys: [...selectionRefs.values()].map(item => [item.lessonId, item.uid])});
      let count = current.verifiedSelectionCount;
      for (let index = 0; index < prepared.length; index++) {
        const row = prepared[index]; const read = reads[index];
        if (!read.member) continue;
        const member = validateFrozenMember(read.member, {quarterId, finalizationId, expectedLessonId: row.rating.lessonId,
          checkpointId: refId("member", row.rating.lessonId)});
        const candidate = {sourceId: row.sourceId, rating: row.rating, completion: row.completion,
          authorUidAtAcceptance: row.authorUidAtAcceptance, member, sourceDigest: row.sourceDigest};
        if (!eligible(member, row.rating, row.completion, current.quarter)) continue;
        const selected = selectedRows.get(canonicalJson([row.rating.lessonId, row.rating.uid]));
        if (later(row.rating, selected.candidate)) {
          if (!selected.candidate) count = add(count, 1, "verifiedSelectionCount");
          selected.candidate = candidate; selected.changed = true;
        }
      }
      for (const selected of selectedRows.values()) if (selected.changed) {
        selectionTree.replace([selected.item.lessonId, selected.item.uid], selected.oldCandidate, selected.candidate);
      }
      const verifiedSelectionInputCount = add(current.verifiedSelectionInputCount, prepared.length, "verifiedSelectionInputCount");
      if (!rows.hasMore && (verifiedSelectionInputCount !== current.voteCount || count !== current.selectionCount)) {
        fail("INVALID_SOURCE", "verified selected checkpoint count changed");
      }
      selectionTree.flush();
      for (const selected of selectedRows.values()) if (selected.changed) {
        tx.set(selected.item.ref, sealEnvelope("SELECTED_VERIFIED", quarterId, finalizationId,
          {candidate: selected.candidate}), {merge: false});
      }
      return next(current, {selectionVerifyCursor: rows.hasMore ? rows.nextCursor : null,
        verifiedSelectionInputCount, verifiedSelectionCount: count, selectionContentHash: selectionTree.root,
        phase: rows.hasMore ? "VERIFY_SELECTION" : "COMPARE_SELECTION"}, at);
    });
  }

  async function compareSelection(quarterId, finalizationId, token) {
    return update(quarterId, finalizationId, token, "COMPARE_SELECTION", async (tx, current, at) => {
      const rows = await page(tx, db,
        `arena_seasons/${quarterId}/finalizations/${finalizationId}/selected_verified`, current.selectionCompareCursor);
      const verified = rows.docs.map(doc => ({checkpointId: doc.id, candidate: validateSelectedCheckpoint(data(doc), {
        quarterId, finalizationId, kind: "SELECTED_VERIFIED", checkpointId: doc.id})}));
      const working = [];
      for (const {candidate} of verified) {
        const stored = data(await tx.get(selectedRef(db, quarterId, finalizationId, candidate.rating.lessonId, candidate.rating.uid)));
        if (!stored) fail("INVALID_SOURCE", "selected checkpoint is absent");
        working.push(validateSelectedCheckpoint(stored, {quarterId, finalizationId,
          expectedLessonId: candidate.rating.lessonId, expectedUid: candidate.rating.uid,
          checkpointId: refId("selected", [candidate.rating.lessonId, candidate.rating.uid])}));
      }
      const selectionTree = await loadMerkleOverlay({tx, db, quarterId, finalizationId, tree: TREE_CONFIGS.selection,
        root: current.selectionContentHash, keys: verified.map(({candidate}) => [candidate.rating.lessonId, candidate.rating.uid])});
      for (let index = 0; index < verified.length; index++) {
        if (!same(verified[index].candidate, working[index])) fail("INVALID_SOURCE", "selected checkpoint is not the verified latest vote");
        selectionTree.prove([verified[index].candidate.rating.lessonId, verified[index].candidate.rating.uid], verified[index].candidate);
      }
      const selectionCompareCount = add(current.selectionCompareCount, verified.length, "selectionCompareCount");
      if (!rows.hasMore && (selectionCompareCount !== current.verifiedSelectionCount ||
          selectionCompareCount !== current.selectionCount)) {
        fail("INVALID_SOURCE", "selected checkpoint comparison count changed");
      }
      return next(current, {selectionCompareCursor: rows.hasMore ? rows.nextCursor : null, selectionCompareCount,
        phase: rows.hasMore ? "COMPARE_SELECTION" : "AGGREGATE"}, at);
    });
  }

  async function aggregateSelection(quarterId, finalizationId, token, {
    phase, aggregateFor, aggregateKind, aggregatePrefix,
    cursorField, inputCountField, countField, chainField, sealsAggregateRoot = false, nextPhase,
  }) {
    return update(quarterId, finalizationId, token, phase, async (tx, current, at) => {
      const selected = await page(tx, db, `arena_seasons/${quarterId}/finalizations/${finalizationId}/selected_verified`, current[cursorField]);
      const candidates = selected.docs.map(doc => ({checkpointId: doc.id, candidate: validateSelectedCheckpoint(data(doc), {
        quarterId, finalizationId, kind: "SELECTED_VERIFIED", checkpointId: doc.id})}));
      const byLesson = new Map();
      for (const {candidate} of candidates) {
        if (!byLesson.has(candidate.rating.lessonId)) byLesson.set(candidate.rating.lessonId, []);
        byLesson.get(candidate.rating.lessonId).push(candidate);
      }
      const reads = new Map();
      for (const [lessonId, group] of byLesson) {
        reads.set(lessonId, {aggregate: data(await tx.get(aggregateFor(lessonId))),
          member: data(await tx.get(memberJobRef(db, quarterId, finalizationId, lessonId))), rows: []});
        for (const candidate of group) {
          reads.get(lessonId).rows.push({candidate,
            stored: data(await tx.get(voteJobRef(db, quarterId, finalizationId, candidate.sourceId)))});
        }
      }
      const selectionTree = await loadMerkleOverlay({tx, db, quarterId, finalizationId, tree: TREE_CONFIGS.selection,
        root: current.selectionContentHash, keys: candidates.map(({candidate}) => [candidate.rating.lessonId, candidate.rating.uid])});
      const aggregateTree = sealsAggregateRoot ? await loadMerkleOverlay({tx, db, quarterId, finalizationId,
        tree: TREE_CONFIGS.aggregate, root: current.aggregateContentHash, keys: [...byLesson.keys()]}) : null;
      let aggregateCount = current[countField]; let digest = current[chainField];
      const outputs = [];
      for (const [lessonId, group] of byLesson) {
        const read = reads.get(lessonId);
        if (!read.member) fail("INVALID_SOURCE", "selected member is absent");
        const frozenMember = validateFrozenMember(read.member, {quarterId, finalizationId, expectedLessonId: lessonId,
          checkpointId: refId("member", lessonId)});
        let sum = 0; const contributorRows = [];
        for (const item of read.rows) {
          selectionTree.prove([item.candidate.rating.lessonId, item.candidate.rating.uid], item.candidate);
          const stored = validateFrozenVoteCheckpoint(item.stored, {quarterId, finalizationId,
            expectedSourceId: item.candidate.sourceId, checkpointId: refId("vote", item.candidate.sourceId)});
          if (stored.sourceDigest !== item.candidate.sourceDigest || !same(stored.rating, item.candidate.rating) ||
              !same(stored.completion, item.candidate.completion) || stored.authorUidAtAcceptance !== item.candidate.authorUidAtAcceptance ||
              !same(item.candidate.member, frozenMember)) {
            fail("INVALID_SOURCE", "selected frozen vote changed");
          }
          if (item.candidate.member.lessonId !== lessonId ||
              !eligible(frozenMember, item.candidate.rating, item.candidate.completion, current.quarter)) {
            fail("INVALID_SOURCE", "selected candidate is no longer eligible");
          }
          sum = add(sum, item.candidate.rating.rating, "ratingSum");
          contributorRows.push({uid: item.candidate.rating.uid, ratingId: item.candidate.rating.ratingId, sourceId: item.candidate.sourceId});
        }
        contributorRows.sort((a, b) => compareTextBytes(a.uid, b.uid) || compareTextBytes(a.ratingId, b.ratingId) ||
          compareTextBytes(a.sourceId, b.sourceId));
        const old = read.aggregate;
        const oldAggregate = old ? validateAggregate(old, policy, {quarterId, finalizationId, kind: aggregateKind,
          expectedLessonId: lessonId, checkpointId: refId(aggregatePrefix, lessonId)}) : null;
        const baseSum = oldAggregate ? oldAggregate.ratingSum : 0;
        const baseCount = oldAggregate ? oldAggregate.ratingCount : 0;
        const baseChain = oldAggregate ? oldAggregate.contributorChainHash : hash([]);
        if (!/^[a-f0-9]{64}$/.test(baseChain || "")) fail("INVALID_SOURCE", "aggregate chain is invalid");
        const nextAggregate = {kind: aggregateKind, quarterId, finalizationId,
          lessonId, authorUid: frozenMember.authorUid, publishedAtMs: frozenMember.publishedAtMs,
          ratingSum: add(baseSum, sum, "ratingSum"), ratingCount: add(baseCount, group.length, "ratingCount"),
          contributorChainHash: chain(baseChain, contributorRows)};
        nextAggregate.weightedScore = policyValue(() => policy.weightedScore(nextAggregate), "aggregate score is invalid");
        nextAggregate.aggregateDigest = hash(nextAggregate);
        const nextSemanticAggregate = validateAggregate(nextAggregate, policy, {quarterId, finalizationId, kind: aggregateKind,
          expectedLessonId: lessonId, checkpointId: refId(aggregatePrefix, lessonId)});
        if (aggregateTree) aggregateTree.replace(lessonId, oldAggregate, nextSemanticAggregate);
        if (!old) {
          aggregateCount = add(aggregateCount, 1, countField);
          digest = chain(digest, {lessonId, authorUid: frozenMember.authorUid, publishedAtMs: frozenMember.publishedAtMs});
        }
        outputs.push({ref: aggregateFor(lessonId), aggregate: nextAggregate});
      }
      const aggregateInputCount = add(current[inputCountField], candidates.length, inputCountField);
      if (!selected.hasMore && aggregateInputCount !== current.selectionCount) {
        fail("INVALID_SOURCE", "verified selected checkpoint does not match its sealed content root");
      }
      if (aggregateTree) aggregateTree.flush();
      for (const output of outputs) tx.set(output.ref, output.aggregate, {merge: false});
      return next(current, {[cursorField]: selected.hasMore ? selected.nextCursor :
        (phase === "VERIFY_AGGREGATES" ? null : selected.nextCursor), [inputCountField]: aggregateInputCount,
        [countField]: aggregateCount, [chainField]: digest,
        ...(aggregateTree ? {aggregateContentHash: aggregateTree.root} : {}),
        phase: selected.hasMore ? phase : nextPhase}, at);
    });
  }

  async function aggregateVotes(quarterId, finalizationId, token) {
    return aggregateSelection(quarterId, finalizationId, token, {
      phase: "AGGREGATE",
      aggregateFor: lessonId => aggregateRef(db, quarterId, finalizationId, lessonId),
      aggregateKind: "AGGREGATE_WORKING", aggregatePrefix: "aggregate",
      cursorField: "aggregateCursor", inputCountField: "aggregateInputCount", countField: "aggregateCount",
      chainField: "aggregateChainHash", nextPhase: "VERIFY_AGGREGATES",
    });
  }

  async function verifyAggregates(quarterId, finalizationId, token) {
    return aggregateSelection(quarterId, finalizationId, token, {
      phase: "VERIFY_AGGREGATES",
      aggregateFor: lessonId => aggregateVerifiedRef(db, quarterId, finalizationId, lessonId),
      aggregateKind: "AGGREGATE_VERIFIED", aggregatePrefix: "aggregate",
      cursorField: "aggregateVerifyCursor", inputCountField: "verifiedAggregateInputCount", countField: "verifiedAggregateCount",
      chainField: "verifiedAggregateChainHash", sealsAggregateRoot: true,
      nextPhase: "COMPARE_AGGREGATES",
    });
  }

  async function compareAggregates(quarterId, finalizationId, token) {
    return update(quarterId, finalizationId, token, "COMPARE_AGGREGATES", async (tx, current, at) => {
      const rows = await page(tx, db,
        `arena_seasons/${quarterId}/finalizations/${finalizationId}/aggregates_verified`, current.aggregateCompareCursor);
      const verified = rows.docs.map(doc => ({checkpointId: doc.id, aggregate: validateAggregate(data(doc), policy, {
        quarterId, finalizationId, kind: "AGGREGATE_VERIFIED", checkpointId: doc.id})}));
      const working = [];
      for (const {aggregate} of verified) {
        const stored = data(await tx.get(aggregateRef(db, quarterId, finalizationId, aggregate.lessonId)));
        if (!stored) fail("INVALID_SOURCE", "aggregate checkpoint is absent");
        working.push(validateAggregate(stored, policy, {quarterId, finalizationId, expectedLessonId: aggregate.lessonId,
          checkpointId: refId("aggregate", aggregate.lessonId)}));
      }
      const aggregateTree = await loadMerkleOverlay({tx, db, quarterId, finalizationId, tree: TREE_CONFIGS.aggregate,
        root: current.aggregateContentHash, keys: verified.map(({aggregate}) => aggregate.lessonId)});
      for (let index = 0; index < verified.length; index++) {
        if (!same(verified[index].aggregate, working[index])) fail("INVALID_SOURCE", "aggregate checkpoint is not independently verified");
        aggregateTree.prove(verified[index].aggregate.lessonId, verified[index].aggregate);
      }
      const aggregateCompareCount = add(current.aggregateCompareCount, verified.length, "aggregateCompareCount");
      if (!rows.hasMore && (aggregateCompareCount !== current.aggregateCount ||
          aggregateCompareCount !== current.verifiedAggregateCount)) {
        fail("INVALID_SOURCE", "aggregate checkpoint comparison count changed");
      }
      return next(current, {aggregateCompareCursor: rows.hasMore ? rows.nextCursor : null, aggregateCompareCount,
        phase: rows.hasMore ? "COMPARE_AGGREGATES" : "PICK_WINNER"}, at);
    });
  }

  async function pickWinner(quarterId, finalizationId, token) {
    return update(quarterId, finalizationId, token, "PICK_WINNER", async (tx, current, at) => {
      const aggregates = await page(tx, db, `arena_seasons/${quarterId}/finalizations/${finalizationId}/aggregates_verified`, current.winnerCursor);
      const rows = aggregates.docs.map(doc => ({checkpointId: doc.id, aggregate: validateAggregate(data(doc), policy, {
        quarterId, finalizationId, kind: "AGGREGATE_VERIFIED", checkpointId: doc.id})}));
      const aggregateTree = await loadMerkleOverlay({tx, db, quarterId, finalizationId, tree: TREE_CONFIGS.aggregate,
        root: current.aggregateContentHash, keys: rows.map(({aggregate}) => aggregate.lessonId)});
      let winner = current.winner; let eligibleLessons = current.eligibleLessons; let digest = current.candidateChainHash;
      for (const {aggregate} of rows) {
        const candidate = {lessonId: aggregate.lessonId, authorUid: aggregate.authorUid, publishedAtMs: aggregate.publishedAtMs,
          ratingSum: aggregate.ratingSum, ratingCount: aggregate.ratingCount, weightedScore: aggregate.weightedScore};
        aggregateTree.prove(aggregate.lessonId, aggregate);
        if (candidate.ratingCount < policy.MIN_SEASON_RATERS) continue;
        eligibleLessons = add(eligibleLessons, 1, "eligibleLessons"); digest = chain(digest, summary(candidate));
        if (candidateBeats(candidate, winner, policy)) winner = candidate;
      }
      const done = !aggregates.hasMore;
      const winnerInputCount = add(current.winnerInputCount, rows.length, "winnerInputCount");
      if (done && winnerInputCount !== current.verifiedAggregateCount) {
        fail("INVALID_SOURCE", "winner input does not match sealed aggregate content");
      }
      return next(current, {winnerCursor: aggregates.nextCursor, winnerInputCount, eligibleLessons, candidateChainHash: digest, winner,
        integrityStage: done ? "MEMBERS" : null, integrityCursor: null, integrityCount: 0,
        phase: done ? "VERIFY_CHECKPOINTS" : "PICK_WINNER"}, at);
    });
  }

  function nextIntegrityStage(stage) {
    const index = INTEGRITY_STAGES.indexOf(stage);
    if (index < 0) fail("INVALID_SOURCE", "checkpoint audit stage is invalid");
    return INTEGRITY_STAGES[index + 1] || null;
  }

  async function auditCheckpoint(tx, current, quarterId, finalizationId, stage, doc, tree = null) {
    const raw = data(doc);
    if (stage === "MEMBERS") {
      const member = validateFrozenMember(raw, {quarterId, finalizationId, checkpointId: doc.id});
      const source = validateMember(member.lessonId,
        data(await tx.get(db.doc(`arena_seasons/${quarterId}/members/${member.lessonId}`))));
      if (!same(source, member)) fail("INVALID_SOURCE", "frozen member no longer matches its source");
    } else if (stage === "VOTES") {
      const vote = validateFrozenVoteCheckpoint(raw, {quarterId, finalizationId, checkpointId: doc.id});
      const source = validateVote(vote.sourceId,
        data(await tx.get(db.doc(`arena_season_votes/${quarterId}/votes/${vote.sourceId}`))));
      if (!same(source, vote)) fail("INVALID_SOURCE", "frozen vote no longer matches its source");
    } else if (stage === "SELECTED" || stage === "VERIFIED_SELECTED") {
      const kind = stage === "SELECTED" ? "SELECTED_WORKING" : "SELECTED_VERIFIED";
      const candidate = validateSelectedCheckpoint(raw, {quarterId, finalizationId, kind, checkpointId: doc.id});
      const otherRef = stage === "SELECTED" ?
        selectedVerifiedRef(db, quarterId, finalizationId, candidate.rating.lessonId, candidate.rating.uid) :
        selectedRef(db, quarterId, finalizationId, candidate.rating.lessonId, candidate.rating.uid);
      const other = data(await tx.get(otherRef));
      if (!other) fail("INVALID_SOURCE", "paired selected checkpoint is absent");
      const otherCandidate = validateSelectedCheckpoint(other, {quarterId, finalizationId,
        kind: stage === "SELECTED" ? "SELECTED_VERIFIED" : "SELECTED_WORKING",
        expectedLessonId: candidate.rating.lessonId, expectedUid: candidate.rating.uid,
        checkpointId: refId("selected", [candidate.rating.lessonId, candidate.rating.uid])});
      if (!same(candidate, otherCandidate)) fail("INVALID_SOURCE", "selected checkpoint no longer matches verified selection");
      if (!tree) fail("INVALID_SOURCE", "selected checkpoint audit lacks Merkle proof");
      tree.prove([candidate.rating.lessonId, candidate.rating.uid], candidate);
    } else if (stage === "AGGREGATES" || stage === "VERIFIED_AGGREGATES") {
      const kind = stage === "AGGREGATES" ? "AGGREGATE_WORKING" : "AGGREGATE_VERIFIED";
      const aggregate = validateAggregate(raw, policy, {quarterId, finalizationId, kind, checkpointId: doc.id});
      const otherRef = stage === "AGGREGATES" ?
        aggregateVerifiedRef(db, quarterId, finalizationId, aggregate.lessonId) :
        aggregateRef(db, quarterId, finalizationId, aggregate.lessonId);
      const other = data(await tx.get(otherRef));
      if (!other) fail("INVALID_SOURCE", "paired aggregate checkpoint is absent");
      const otherAggregate = validateAggregate(other, policy, {quarterId, finalizationId,
        kind: stage === "AGGREGATES" ? "AGGREGATE_VERIFIED" : "AGGREGATE_WORKING",
        expectedLessonId: aggregate.lessonId,
        checkpointId: refId("aggregate", aggregate.lessonId)});
      if (!same(aggregate, otherAggregate)) fail("INVALID_SOURCE", "aggregate checkpoint no longer matches verified aggregate");
      if (!tree) fail("INVALID_SOURCE", "aggregate checkpoint audit lacks Merkle proof");
      tree.prove(aggregate.lessonId, aggregate);
    } else {
      fail("INVALID_SOURCE", "checkpoint audit stage is invalid");
    }
    return {digest: hash(raw)};
  }

  async function verifyCheckpoints(quarterId, finalizationId, token) {
    return update(quarterId, finalizationId, token, "VERIFY_CHECKPOINTS", async (tx, current, at) => {
      const audit = {
        MEMBERS: {path: `arena_seasons/${quarterId}/finalizations/${finalizationId}/members`, expected: current.memberCount},
        VOTES: {path: `arena_seasons/${quarterId}/finalizations/${finalizationId}/votes`, expected: current.voteCount},
        SELECTED: {path: `arena_seasons/${quarterId}/finalizations/${finalizationId}/selected`, expected: current.selectionCount},
        VERIFIED_SELECTED: {path: `arena_seasons/${quarterId}/finalizations/${finalizationId}/selected_verified`, expected: current.verifiedSelectionCount},
        AGGREGATES: {path: `arena_seasons/${quarterId}/finalizations/${finalizationId}/aggregates`, expected: current.aggregateCount},
        VERIFIED_AGGREGATES: {path: `arena_seasons/${quarterId}/finalizations/${finalizationId}/aggregates_verified`, expected: current.verifiedAggregateCount},
      }[current.integrityStage];
      if (!audit) fail("INVALID_SOURCE", "checkpoint audit stage is invalid");
      const rows = await page(tx, db, audit.path, current.integrityCursor);
      let auditTree = null;
      if (current.integrityStage === "SELECTED" || current.integrityStage === "VERIFIED_SELECTED") {
        const kind = current.integrityStage === "SELECTED" ? "SELECTED_WORKING" : "SELECTED_VERIFIED";
        const keys = rows.docs.map(doc => {
          const candidate = validateSelectedCheckpoint(data(doc), {quarterId, finalizationId, kind, checkpointId: doc.id});
          return [candidate.rating.lessonId, candidate.rating.uid];
        });
        auditTree = await loadMerkleOverlay({tx, db, quarterId, finalizationId, tree: TREE_CONFIGS.selection,
          root: current.selectionContentHash, keys});
      } else if (current.integrityStage === "AGGREGATES" || current.integrityStage === "VERIFIED_AGGREGATES") {
        const kind = current.integrityStage === "AGGREGATES" ? "AGGREGATE_WORKING" : "AGGREGATE_VERIFIED";
        const keys = rows.docs.map(doc => validateAggregate(data(doc), policy, {quarterId, finalizationId, kind, checkpointId: doc.id}).lessonId);
        auditTree = await loadMerkleOverlay({tx, db, quarterId, finalizationId, tree: TREE_CONFIGS.aggregate,
          root: current.aggregateContentHash, keys});
      }
      let integrityChainHash = current.integrityChainHash;
      for (const doc of rows.docs) {
        const checked = await auditCheckpoint(tx, current, quarterId, finalizationId, current.integrityStage, doc, auditTree);
        integrityChainHash = chain(integrityChainHash, {stage: current.integrityStage, checkpointId: doc.id, digest: checked.digest});
      }
      const integrityCount = add(current.integrityCount, rows.docs.length, "integrityCount");
      if (rows.hasMore) {
        return next(current, {integrityCursor: rows.nextCursor, integrityCount, integrityChainHash}, at);
      }
      if (integrityCount !== audit.expected) fail("INVALID_SOURCE", "checkpoint audit count changed");
      const stage = nextIntegrityStage(current.integrityStage);
      if (stage) {
        return next(current, {integrityStage: stage, integrityCursor: null, integrityCount: 0, integrityChainHash,
        }, at);
      }
      const draft = next(current, {integrityStage: null, integrityCursor: null, integrityCount: 0, integrityChainHash,
        phase: "PUBLISH"}, at);
      return {...draft, calculationManifestDigest: hash(manifest(draft))};
    });
  }

  async function publish(quarterId, finalizationId, token) {
    return db.runTransaction(async tx => {
      const at = now(); const current = validateJob(data(await tx.get(job(quarterId, finalizationId))), policy);
      if (!owns(current, token, at)) return {kind: "LEASE_LOST"};
      if (current.phase !== "PUBLISH") return {kind: current.phase};
      validateSeason(data(await tx.get(seasonRef(db, quarterId))), current.quarter, finalizationId);
      if (current.calculationManifestDigest !== hash(manifest(current))) fail("INVALID_SOURCE", "calculation manifest changed");
      const reservation = data(await tx.get(db.doc(`arena_mutation_reservations/${current.requestDocumentId}`)));
      let request;
      try { request = requestFromReservation({db, reservation}); }
      catch { fail("INVALID_SOURCE", "publication reservation binding changed"); }
      if (request.uid !== current.requestedByUid || request.mutationId !== current.requestedMutationId ||
          request.payloadHash !== current.requestedPayloadHash || reservation.finalizationId !== finalizationId ||
          reservation.quarterId !== quarterId) fail("INVALID_SOURCE", "publication request binding changed");
      if (data(await tx.get(request.ref))) fail("INVALID_SOURCE", "immutable final receipt exists before publication");
      const season = {quarter: current.quarter, winner: current.winner};
      let reward; let prepared;
      try {
        reward = policy.planSeasonAward({season});
        prepared = await prepareAward(tx, reward);
      } catch (error) {
        // These are deterministic faults in our private checkpoint/award ledger, not a reason
        // to spin forever at PUBLISH. Transport/SDK failures remain retryable and keep the lease
        // release path below.
        if (error instanceof RangeError || error instanceof TypeError || error?.name === "ArenaServiceError" ||
            error?.name === "ArenaMutationReservationError") {
          fail("INVALID_SOURCE", "season publication inputs require repair");
        }
        throw error;
      }
      const result = {quarter: current.quarter, winner: summary(current.winner), eligibleLessons: current.eligibleLessons,
        calculationManifestDigest: current.calculationManifestDigest, calculationVersion: current.calculationVersion,
        policyVersion: current.policyVersion, awarded: prepared && !prepared.alreadyAwarded ? reward : null,
        alreadyAwarded: Boolean(prepared && prepared.alreadyAwarded)};
      applyAward(tx, prepared, at);
      createDecision(tx, request, at, "APPLIED", result);
      completeReservation(tx, request, reservation, at);
      tx.set(seasonRef(db, quarterId), {quarter: current.quarter, status: "FINALIZED", finalizedAtMs: at,
        finalizedByUid: request.uid, finalizedByMutationId: request.mutationId, policyVersion: current.policyVersion,
        calculationVersion: current.calculationVersion, calculationManifestDigest: current.calculationManifestDigest, result}, {merge: true});
      tx.set(job(quarterId, finalizationId), checkpoint(current, {phase: "PUBLISHED", lease: null, publishedAtMs: at}, at), {merge: false});
      return {kind: "PUBLISHED", result};
    });
  }

  async function advance({quarterId, finalizationId, deadlineAtMs = Number.MAX_SAFE_INTEGER}) {
    documentId(quarterId, "quarterId"); documentId(finalizationId, "finalizationId"); integer(deadlineAtMs, "deadlineAtMs");
    if (now() >= deadlineAtMs) return {status: "TIME_BUDGET", quarterId, finalizationId};
    let acquired;
    try { acquired = await acquire(quarterId, finalizationId); }
    catch (error) { if (error instanceof ArenaSeasonFinalizationError) throw error; fail("UNAVAILABLE", "season worker storage unavailable"); }
    if (acquired.kind === "BUSY") return {status: "BUSY", quarterId, finalizationId, retryAtMs: acquired.retryAtMs};
    if (acquired.kind === "PUBLISHED") return {status: "PUBLISHED", quarterId, finalizationId};
    if (acquired.kind === "REQUIRES_REVIEW") return {status: "REQUIRES_REVIEW", quarterId, finalizationId};
    const token = acquired.token; let result; let error = null; let terminal = false;
    try {
      if (now() >= deadlineAtMs) result = {kind: "TIME_BUDGET"};
      else if (acquired.job.phase === "SCAN_MEMBERS") result = await scanMembers(quarterId, finalizationId, token);
      else if (acquired.job.phase === "VERIFY_MEMBERS") result = await verifyMembers(quarterId, finalizationId, token);
      else if (acquired.job.phase === "SCAN_VOTES") result = await scanVotes(quarterId, finalizationId, token);
      else if (acquired.job.phase === "SELECT") result = await selectVotes(quarterId, finalizationId, token);
      else if (acquired.job.phase === "VERIFY_SELECTION") result = await verifySelection(quarterId, finalizationId, token);
      else if (acquired.job.phase === "COMPARE_SELECTION") result = await compareSelection(quarterId, finalizationId, token);
      else if (acquired.job.phase === "AGGREGATE") result = await aggregateVotes(quarterId, finalizationId, token);
      else if (acquired.job.phase === "VERIFY_AGGREGATES") result = await verifyAggregates(quarterId, finalizationId, token);
      else if (acquired.job.phase === "COMPARE_AGGREGATES") result = await compareAggregates(quarterId, finalizationId, token);
      else if (acquired.job.phase === "PICK_WINNER") result = await pickWinner(quarterId, finalizationId, token);
      else if (acquired.job.phase === "VERIFY_CHECKPOINTS") result = await verifyCheckpoints(quarterId, finalizationId, token);
      else if (acquired.job.phase === "PUBLISH") result = await publish(quarterId, finalizationId, token);
      else result = {kind: acquired.job.phase};
      terminal = ["PUBLISHED", "REQUIRES_REVIEW"].includes(result.kind);
    } catch (caught) {
      error = caught;
      if (caught instanceof ArenaSeasonFinalizationError && ["INVALID_SOURCE", "INVALID_ARGUMENT"].includes(caught.code)) {
        const marked = await markReview(quarterId, finalizationId, token, caught.code);
        return {status: marked ? "REQUIRES_REVIEW" : "LEASE_LOST", quarterId, finalizationId};
      }
    } finally {
      if (!terminal && !(error instanceof ArenaSeasonFinalizationError && ["INVALID_SOURCE", "INVALID_ARGUMENT"].includes(error.code))) {
        try { await release(quarterId, finalizationId, token); } catch { /* expiry safely fences stale worker */ }
      }
    }
    if (error) throw error;
    return {status: result.kind === "LEASE_LOST" ? "LEASE_LOST" : result.kind, quarterId, finalizationId,
      ...(result.result ? {result: result.result} : {})};
  }

  return {begin, advance, pendingResponse, finalizationIdFor, jobRef: job};
}

module.exports = {
  ArenaSeasonFinalizationError, SCHEMA_VERSION, CALCULATION_VERSION, PAGE_SIZE, LEASE_MS, PHASES,
  createArenaSeasonFinalizer, validateMember, validateVote, validateCompletion, validateRating,
  eligible, candidateBeats, summary, manifest, seal, hash, pendingResponse,
};
