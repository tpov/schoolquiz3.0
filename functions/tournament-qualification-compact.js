"use strict";

// Qualification-only pure calculation. The adapter must supply the COMPLETE frozen, verified
// cohort, bind its digest to this checkpoint, and atomically CAS each cursor/checkpoint write.
// No Firebase, clocks, eligibility decisions, publication, or financial effects live here.
const {createHash} = require("node:crypto");
const VERSIONS = Object.freeze({algorithmFamily: "pairwise-percent-least-squares-v1",
  calculationVersion: "qualification-three-form-rational-v2", executionVersion: "compact-mask-reduction-v1",
  roundingVersion: "exact-half-positive-infinity-canonical-zero-v1"});
const MAX_PAGE_PLAYERS = 50; // Invocation bound, never an admission/cohort limit.
const MAX_FRACTION_BITS = 4096; // Arithmetic corruption/resource guard, not a player quota.
const COLLATION = Object.freeze({icu: process.versions.icu, locale: new Intl.Collator().resolvedOptions().locale});
const own = (o, k) => Object.prototype.hasOwnProperty.call(o, k);
function requireValue(ok, message) { if (!ok) throw new RangeError(message); }
function keys(o, required) {
  requireValue(o && Object.getPrototypeOf(o) === Object.prototype && Object.keys(o).length === required.length &&
    required.every(k => own(o, k)), "Unexpected calculation fields");
}
function integer(n, min = 0) { requireValue(Number.isSafeInteger(n) && n >= min, "Exact integer required"); return n; }
function add(a, b) { return integer(a + b); }
function id(value) {
  requireValue(typeof value === "string" && value.length > 0 && value.length <= 128 && !value.includes("/"), "Invalid projection identity");
  return value;
}
/** Private player document IDs can use this key so Firestore's byte order reproduces the
 * existing JS UTF-16 cursor order, including supplementary and canonically equivalent UIDs.
 */
function playerCursorKey(userId) {
  id(userId);
  return Array.from({length: userId.length}, (_, i) => userId.charCodeAt(i).toString(16).padStart(4, "0")).join("");
}
function canonical(v) {
  if (v === null || typeof v === "string" || typeof v === "boolean") return JSON.stringify(v);
  if (typeof v === "number" && Number.isSafeInteger(v)) return JSON.stringify(v);
  if (Array.isArray(v)) return `[${v.map(canonical).join(",")}]`;
  if (v && Object.getPrototypeOf(v) === Object.prototype) return `{${Object.keys(v).sort().map(k => `${JSON.stringify(k)}:${canonical(v[k])}`).join(",")}}`;
  throw new RangeError("Checkpoint requires exact JSON values");
}
const digest = value => createHash("sha256").update(canonical(value)).digest("hex");
const seal = value => ({...value, checkpointDigest: digest(value)});
function unseal(value, kind) {
  requireValue(value && value.kind === kind && value.schemaVersion === 1, "Unsupported checkpoint kind/version");
  const {checkpointDigest, ...body} = value;
  requireValue(checkpointDigest === digest(body) && digest(body.versions) === digest(VERSIONS) &&
    digest(body.collation) === digest(COLLATION), "Changed checkpoint, calculation version, or UID collation runtime");
  return structuredClone(body);
}
function big(value) {
  requireValue(typeof value === "string" && /^-?(0|[1-9][0-9]*)$/.test(value) && value.length <= 1235, "Invalid exact integer encoding");
  const result = BigInt(value);
  requireValue(abs(result).toString(2).length <= MAX_FRACTION_BITS, "Arithmetic bit budget exhausted");
  return result;
}
const abs = n => n < 0n ? -n : n;
function gcd(a, b) { a = abs(a); b = abs(b); while (b) { const t = a % b; a = b; b = t; } return a; }
function fraction(n, d = 1n) {
  requireValue(d !== 0n, "Zero rational denominator");
  if (d < 0n) { n = -n; d = -d; }
  const g = gcd(n, d); n /= g; d /= g;
  requireValue(abs(n).toString(2).length <= MAX_FRACTION_BITS && d.toString(2).length <= MAX_FRACTION_BITS,
    "Arithmetic bit budget exhausted; no partial result");
  return {n, d};
}
const zero = () => fraction(0n);
const plus = (a, b) => fraction(a.n * b.d + b.n * a.d, a.d * b.d);
const minus = (a, b) => fraction(a.n * b.d - b.n * a.d, a.d * b.d);
const times = (a, b) => fraction(a.n * b.n, a.d * b.d);
const divide = (a, b) => fraction(a.n * b.d, a.d * b.n);
const sum = values => values.reduce(plus, zero());
const pack = r => ({numerator: r.n.toString(), denominator: r.d.toString()});
function unpack(r) { keys(r, ["numerator", "denominator"]); return fraction(big(r.numerator), big(r.denominator)); }
function safeNumber(n) { const value = Number(n); requireValue(Number.isSafeInteger(value), "Projected integer overflow"); return value; }
function roundedUnits(r, scale) {
  const n = 2n * r.n * BigInt(integer(scale, 1)) + r.d; const d = 2n * r.d;
  // BigInt truncates toward zero. Correct to floor, giving Math.round's mathematical tie rule.
  return safeNumber(n / d - (n % d < 0n ? 1n : 0n));
}
function smallSolve(matrix, vector) {
  const n = vector.length;
  requireValue(n > 0 && n <= 3 && matrix.length === n && matrix.every(row => row.length === n), "System must be at most 3x3");
  const rows = matrix.map((row, i) => [...row, vector[i]]);
  for (let col = 0; col < n; col++) {
    const pivot = rows.findIndex((row, i) => i >= col && row[col].n !== 0n);
    requireValue(pivot >= 0, "Singular exact system; no fabricated zero ratings");
    [rows[col], rows[pivot]] = [rows[pivot], rows[col]];
    const p = rows[col][col];
    for (let j = col; j <= n; j++) rows[col][j] = divide(rows[col][j], p);
    for (let i = 0; i < n; i++) if (i !== col) {
      const factor = rows[i][col];
      for (let j = col; j <= n; j++) rows[i][j] = minus(rows[i][j], times(factor, rows[col][j]));
    }
  }
  return rows.map(row => row[n]);
}
function validatePlayer(player, forms) {
  keys(player, ["userId", "results"]); id(player.userId);
  requireValue(Array.isArray(player.results) && player.results.length > 0 && player.results.length <= 3, "One to three verified results required");
  const seen = new Set();
  const results = player.results.map(row => {
    keys(row, ["formId", "percent", "elapsedMs", "completedAtMs"]);
    const formIndex = forms.findIndex(form => form.formId === row.formId);
    requireValue(formIndex >= 0 && !seen.has(formIndex), "Unknown or duplicate player/form"); seen.add(formIndex);
    requireValue(integer(row.percent) <= 100, "Integer percent outside 0..100");
    requireValue(integer(row.completedAtMs) >= integer(row.elapsedMs), "Inconsistent server timing");
    return {formId: row.formId, percent: row.percent, elapsedMs: row.elapsedMs, completedAtMs: row.completedAtMs, formIndex};
  }).sort((a, b) => a.formIndex - b.formIndex);
  return {userId: player.userId, results};
}
function pageRows(rows) { requireValue(Array.isArray(rows) && rows.length > 0 && rows.length <= MAX_PAGE_PLAYERS, "Bounded nonempty player page required"); }

/** Players must be complete, unique, and strictly increasing in JS string order across pages.
 * This makes duplicate admission/player prevention resumable with one cursor, not a growing Set.
 */
function createReduction({formIds, cohortFingerprint, expectedPlayers, expectedRuns}) {
  requireValue(Array.isArray(formIds) && formIds.length === 3 && new Set(formIds.map(id)).size === 3, "Exactly three distinct form IDs required");
  requireValue(/^[a-f0-9]{64}$/.test(cohortFingerprint), "Frozen cohort fingerprint required");
  integer(expectedPlayers); integer(expectedRuns);
  requireValue(expectedRuns >= expectedPlayers && BigInt(expectedRuns) <= 3n * BigInt(expectedPlayers), "Inconsistent complete cohort counts");
  const base = {schemaVersion: 1, versions: VERSIONS, collation: COLLATION, cohortFingerprint,
    expectedPlayers, expectedRuns, formIds: [...formIds]};
  return seal({...base, kind: "REDUCE", processedPlayers: 0, processedRuns: 0, afterUserId: null,
    inputChain: digest(base), forms: formIds.map(formId => ({formId, count: 0, scoreSum: "0"})),
    bins: Array.from({length: 7}, (_, i) => ({mask: i + 1, count: 0, scoreSums: ["0", "0", "0"], minUserId: null}))});
}
function reducePage(checkpoint, page) {
  pageRows(page); const state = unseal(checkpoint, "REDUCE");
  for (const raw of page) {
    const player = validatePlayer(raw, state.forms);
    requireValue(state.afterUserId === null || state.afterUserId < player.userId, "Duplicate or nonadvancing player cursor");
    const mask = player.results.reduce((m, row) => m | (1 << row.formIndex), 0); const bin = state.bins[mask - 1];
    bin.count = add(bin.count, 1); bin.minUserId ??= player.userId;
    for (const row of player.results) {
      const form = state.forms[row.formIndex]; form.count = add(form.count, 1);
      form.scoreSum = (big(form.scoreSum) + BigInt(row.percent)).toString();
      bin.scoreSums[row.formIndex] = (big(bin.scoreSums[row.formIndex]) + BigInt(row.percent)).toString();
    }
    state.processedPlayers = add(state.processedPlayers, 1); state.processedRuns = add(state.processedRuns, player.results.length);
    requireValue(state.processedPlayers <= state.expectedPlayers && state.processedRuns <= state.expectedRuns, "Page exceeds frozen cohort");
    state.afterUserId = player.userId; state.inputChain = digest([state.inputChain, player]);
  }
  return seal(state);
}

function solve(checkpoint) {
  const state = unseal(checkpoint, "REDUCE");
  requireValue(state.processedPlayers === state.expectedPlayers && state.processedRuns === state.expectedRuns, "Incomplete cohort cannot be solved");
  const activeMask = state.forms.reduce((mask, form, i) => form.count >= 2 ? mask | (1 << i) : mask, 0);
  const bins = Array.from({length: 8}, (_, mask) => ({mask, count: 0, scoreSums: [0n, 0n, 0n], minUserId: null}));
  for (const raw of state.bins) {
    const bin = bins[raw.mask & activeMask]; bin.count = add(bin.count, raw.count);
    raw.scoreSums.forEach((n, i) => { bin.scoreSums[i] += big(n); });
    if (raw.minUserId !== null && (bin.minUserId === null || raw.minUserId < bin.minUserId)) bin.minUserId = raw.minUserId;
  }
  const counted = bins.filter(bin => bin.mask && bin.count);
  for (const bin of counted) {
    bin.D = 0n; bin.sumB = 0n;
    state.forms.forEach((form, g) => { if (bin.mask & (1 << g)) {
      bin.D += BigInt(form.count); bin.sumB += BigInt(form.count) * bin.scoreSums[g] - BigInt(bin.count) * big(form.scoreSum);
    }});
  }
  const parent = [0, 1, 2]; const root = g => { while (parent[g] !== g) g = parent[g]; return g; };
  for (const bin of counted) {
    const groups = [0, 1, 2].filter(g => bin.mask & (1 << g));
    for (const g of groups.slice(1)) parent[root(g)] = root(groups[0]);
  }
  const byRoot = new Map();
  state.forms.forEach((form, g) => { if (form.count >= 2) {
    const r = root(g); if (!byRoot.has(r)) byRoot.set(r, {groups: [], count: 0, minUserId: null});
    byRoot.get(r).groups.push(g);
  }});
  for (const bin of counted) {
    const component = byRoot.get(root([0, 1, 2].find(g => bin.mask & (1 << g))));
    component.count = add(component.count, bin.count);
    if (component.minUserId === null || bin.minUserId < component.minUserId) component.minUserId = bin.minUserId;
  }
  // Legacy discovery starts from JS-sorted UIDs. Preserve that initial stable order when two
  // different minimum UIDs compare equal under localeCompare (for example Unicode NFC/NFD).
  const components = [...byRoot.values()].sort((a, b) => b.count - a.count || a.minUserId.localeCompare(b.minUserId) ||
    (a.minUserId < b.minUserId ? -1 : a.minUserId > b.minUserId ? 1 : 0));
  const totals = [zero(), zero(), zero()];
  components.forEach((component, index) => {
    component.componentId = index + 1;
    const groups = component.groups; const included = counted.filter(bin => groups.some(g => bin.mask & (1 << g)));
    const matrix = groups.map((g, i) => groups.map((h, j) => minus(fraction(i === j ? 1n : 0n),
      sum(included.filter(bin => (bin.mask & (1 << g)) && (bin.mask & (1 << h))).map(bin => fraction(BigInt(bin.count), bin.D))))));
    const vector = groups.map(g => sum(included.filter(bin => bin.mask & (1 << g)).map(bin => fraction(bin.sumB, bin.D))));
    matrix[groups.length - 1] = groups.map(g => sum(included.filter(bin => bin.mask & (1 << g)).map(bin => fraction(BigInt(bin.count), bin.D))));
    vector[groups.length - 1] = minus(zero(), sum(included.map(bin => fraction(bin.sumB, bin.D))));
    smallSolve(matrix, vector).forEach((value, i) => { totals[groups[i]] = value; });
    const binSum = bin => divide(plus(fraction(bin.sumB), times(fraction(BigInt(bin.count)),
      sum(groups.filter(g => bin.mask & (1 << g)).map(g => totals[g])))), fraction(bin.D));
    requireValue(sum(included.map(binSum)).n === 0n, "Component gauge certificate failed");
    for (const g of groups) requireValue(minus(sum(included.filter(bin => bin.mask & (1 << g)).map(binSum)), totals[g]).n === 0n,
      "Group residual certificate failed");
  });
  return seal({schemaVersion: 1, kind: "SOLVED", versions: VERSIONS, collation: COLLATION,
    cohortFingerprint: state.cohortFingerprint, inputChain: state.inputChain, reductionDigest: checkpoint.checkpointDigest,
    expectedPlayers: state.expectedPlayers, expectedRuns: state.expectedRuns, forms: state.forms, activeMask,
    totalRanked: counted.reduce((n, bin) => add(n, bin.count), 0),
    comparisonCount: state.forms.reduce((n, form) => n + BigInt(form.count) * BigInt(form.count - 1) / 2n, 0n).toString(),
    bins: counted.map(bin => ({mask: bin.mask, count: bin.count})), components, groupTotals: totals.map(pack)});
}
function projectPlayer(solution, raw) {
  const state = unseal(solution, "SOLVED"); const player = validatePlayer(raw, state.forms);
  const rows = player.results.filter(row => state.activeMask & (1 << row.formIndex));
  if (!rows.length) return null;
  const mask = rows.reduce((m, row) => m | (1 << row.formIndex), 0);
  const component = state.components.find(c => c.groups.includes(rows[0].formIndex));
  requireValue(component && rows.every(row => component.groups.includes(row.formIndex)) && state.bins.some(bin => bin.mask === mask), "Player does not belong to solved component");
  let D = 0n; let b = 0n; let percentSum = 0n; let elapsedSum = 0n; let elapsedCount = 0; let comparisons = 0n;
  for (const row of rows) {
    const form = state.forms[row.formIndex]; D += BigInt(form.count);
    b += BigInt(form.count) * BigInt(row.percent) - big(form.scoreSum); percentSum += BigInt(row.percent);
    comparisons += BigInt(form.count - 1);
    if (row.elapsedMs > 0) { elapsedSum += BigInt(row.elapsedMs); elapsedCount++; }
  }
  const rating = divide(plus(fraction(b), sum(rows.map(row => unpack(state.groupTotals[row.formIndex])))), fraction(D));
  const ratingUnits = roundedUnits(rating, 10_000); const averagePercentUnits = roundedUnits(fraction(percentSum, BigInt(rows.length)), 10_000);
  const uniqueOpponents = state.bins.filter(bin => bin.mask & mask).reduce((n, bin) => add(n, bin.count), 0) - 1;
  return {userId: player.userId, calculationDigest: solution.checkpointDigest, exactRating: pack(rating), ratingUnits,
    ratingPoints: roundedUnits(plus(fraction(1000n), times(rating, fraction(10n))), 1), averagePercentUnits,
    averageElapsedMs: elapsedCount ? roundedUnits(fraction(elapsedSum, BigInt(elapsedCount)), 1) : null,
    groupsPlayed: rows.length, comparisons: comparisons.toString(), uniqueOpponents,
    componentId: component.componentId, componentSize: component.count};
}
function compareEntries(a, b) {
  return b.ratingUnits - a.ratingUnits || b.averagePercentUnits - a.averagePercentUnits ||
    b.groupsPlayed - a.groupsPlayed || b.uniqueOpponents - a.uniqueOpponents ||
    (a.averageElapsedMs ?? Infinity) - (b.averageElapsedMs ?? Infinity) || a.userId.localeCompare(b.userId);
}
function createTop16(solution) {
  const state = unseal(solution, "SOLVED");
  return seal({schemaVersion: 1, kind: "TOP16", versions: VERSIONS, collation: COLLATION,
    calculationDigest: solution.checkpointDigest, expectedPlayers: state.expectedPlayers, expectedRuns: state.expectedRuns,
    expectedRanked: state.totalRanked, expectedInputChain: state.inputChain,
    inputChain: digest({schemaVersion: 1, versions: VERSIONS, collation: COLLATION, cohortFingerprint: state.cohortFingerprint,
      expectedPlayers: state.expectedPlayers, expectedRuns: state.expectedRuns, formIds: state.forms.map(form => form.formId)}),
    processedPlayers: 0, processedRuns: 0, rankedPlayers: 0, afterUserId: null, entries: []});
}
/** Re-project the same complete player stream. Its independent input hash MUST equal REDUCE's
 * final hash before exposing ordinals. Persist this whole returned checkpoint atomically.
 */
function mergeTop16(checkpoint, solution, page) {
  pageRows(page); const state = unseal(checkpoint, "TOP16"); const solved = unseal(solution, "SOLVED");
  requireValue(state.calculationDigest === solution.checkpointDigest, "Different calculation checkpoint");
  for (const raw of page) {
    const player = validatePlayer(raw, solved.forms);
    requireValue(state.afterUserId === null || state.afterUserId < player.userId, "Duplicate or nonadvancing projection cursor");
    const entry = projectPlayer(solution, raw);
    state.processedPlayers = add(state.processedPlayers, 1); state.processedRuns = add(state.processedRuns, player.results.length);
    requireValue(state.processedPlayers <= state.expectedPlayers && state.processedRuns <= state.expectedRuns, "Projection exceeds frozen cohort");
    if (entry) { state.rankedPlayers = add(state.rankedPlayers, 1); state.entries = [...state.entries, entry].sort(compareEntries).slice(0, 16); }
    state.afterUserId = player.userId; state.inputChain = digest([state.inputChain, player]);
  }
  return seal(state);
}
function finishTop16(checkpoint) {
  const state = unseal(checkpoint, "TOP16");
  requireValue(state.processedPlayers === state.expectedPlayers && state.processedRuns === state.expectedRuns &&
    state.rankedPlayers === state.expectedRanked && state.inputChain === state.expectedInputChain, "Incomplete or changed projection cohort");
  return {versions: {...VERSIONS}, calculationDigest: state.calculationDigest, inputFingerprint: state.inputChain,
    verifiedRunCount: state.processedRuns, totalRanked: state.rankedPlayers,
    ranking: state.entries.map((entry, index) => ({uid: entry.userId, place: index + 1}))};
}

module.exports = {VERSIONS, MAX_PAGE_PLAYERS, playerCursorKey, createReduction, reducePage, solve, projectPlayer,
  compareEntries, createTop16, mergeTop16, finishTop16};
