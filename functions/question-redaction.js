"use strict";

const {randomInt} = require("node:crypto");

/**
 * Splits a published question payload into a public half and an answer key, and puts the two back
 * together again.
 *
 * Published questions are world-readable (`firestore.rules:127`, `allow read: if true`) and today
 * carry the whole answer key inside `payload`. This module is the pure half of taking the key off
 * that document: it decides what may be published and what must be kept, and it owns the reverse
 * operation too, so the write path and the scorer do not each reinvent it.
 *
 * Nothing calls it yet. Wiring it into publication is a separate slice, so this file — like
 * `lesson-reward.js` and `assessment-scoring.js` — stays free of `firebase-admin` and testable with
 * plain `node`. `node:crypto` is a Node builtin and costs none of that.
 *
 * Four decisions carry the whole design:
 *
 * 1. **The public half is a different type, not a stripped one.** It carries its own discriminator
 *    (`SingleChoiceRedacted`, …). A `redacted: true` flag beside `"type":"SingleChoice"` would be
 *    swallowed by `Json { ignoreUnknownKeys = true }`
 *    (`KotlinxSerializationQuestionContentParser.kt:12`), and worse, a stripped `Ordering` still
 *    reads as an `Ordering` to `scoreOrdering` (`assessment-scoring.js:139-152`), which takes the
 *    correct order from the array order of `content.items` — so the shuffled sequence on screen
 *    would score 9 and the true answer 5. A distinct discriminator makes that structurally
 *    impossible: `evaluateAnswer` falls through to its floor for a type it does not know, and the
 *    Kotlin parser cannot decode the shape at all.
 *
 * 2. **The public half is built from an allow-list.** Fields are copied in by name, never deleted
 *    by name. A field nobody here has reasoned about is not proven safe, so it is not published.
 *    `info` — the author's prose, which routinely names the answer — is therefore absent from every
 *    public half this module *produces*. It is emphatically still present in every payload this
 *    module hands back untouched: a Survey, a legacy dialect, an unknown discriminator, a refusal.
 *    Those are passed through by design, which is exactly why the caller must read `status` rather
 *    than assume a returned payload has been through anything.
 *
 * 3. **Redaction is scheme-agnostic and whole-or-nothing.** Ids are re-issued, never parsed: the
 *    authoring component mints `ord-0…` in the correct order and `cand-0..cand-(n-1)` for the
 *    correct candidates (`DefaultQuestCreateComponent.kt:1147,1176-1182`), but thousands of seeded
 *    questions use `a`/`b`/`c`, `i1..i4`, `c1..c5` instead. Any rule that reads an ordinal out of an
 *    id is wrong for one corpus or the other. And a payload this module cannot split completely is
 *    refused — returned untouched, with a `status` and a `reason` saying so, so the caller can
 *    decline to publish rather than publish an answer key.
 *
 * 4. **The result is a discriminated outcome, not a nullable key.** `status: "redacted"` and
 *    `status: "refused"` both used to arrive as `key: null` with the payload unchanged, and one of
 *    them ships a hard question's answer to the world. `already-redacted` is called out separately
 *    because a caller that stores a non-null key and clears a null one would otherwise wipe a
 *    stored key every time a question is republished.
 *
 * Ids are re-issued **along the shuffled order**, so a public list reads `ri-0, ri-1, …` ascending.
 * Numbering along the original order would move the leak into the id sequence instead of removing
 * it.
 */

/** Source discriminators, as they appear in `QuestionContent.kt`. */
const CONTENT_TYPE = {
  SINGLE_CHOICE: "SingleChoice",
  MULTIPLE_CHOICE: "MultipleChoice",
  ORDERING: "Ordering",
  FILL_BLANK: "FillBlank",
  SURVEY: "Survey",
};

/**
 * Public discriminators. Deliberately not a suffix computed at runtime: these strings are a wire
 * contract with a client that does not exist yet, and grepping for them must find them.
 */
const REDACTED_TYPE = {
  [CONTENT_TYPE.SINGLE_CHOICE]: "SingleChoiceRedacted",
  [CONTENT_TYPE.MULTIPLE_CHOICE]: "MultipleChoiceRedacted",
  [CONTENT_TYPE.ORDERING]: "OrderingRedacted",
  [CONTENT_TYPE.FILL_BLANK]: "FillBlankRedacted",
};

/** The inverse, for recognising our own output — see STATUS.ALREADY_REDACTED. */
const SOURCE_TYPE_OF = new Map(Object.entries(REDACTED_TYPE).map(([source, public_]) => [public_, source]));

const STATUS = {
  /** Split; `key` is non-null and `publicPayload` is safe to publish. */
  REDACTED: "redacted",
  /** Recognised, but has no answer to take — a Survey. Publish the payload as it stands. */
  NOT_APPLICABLE: "not-applicable",
  /** Already one of ours. Publish as it stands and leave any stored key alone. */
  ALREADY_REDACTED: "already-redacted",
  /** Could not be split completely. The payload still carries its answer; do not publish it. */
  REFUSED: "refused",
};

/**
 * Stamped into every key.
 *
 * The `ri-`/`rc-` prefixes and the direction of `idMap` are a contract between a row written today
 * and a scorer running months from now. Changing either without this field would misread every key
 * already in the collection, silently and in the direction that scores wrong answers correct.
 */
const KEY_VERSION = 1;

/** Prefixes for re-issued ids. They are meaningless by design — the mapping lives in the key. */
const ORDER_ITEM_PREFIX = "ri-";
const CANDIDATE_PREFIX = "rc-";

/** `QuestionContent` requires options and items in 2..8; a one-row list is all answer. */
const MIN_ROWS = 2;

/** How many times the shuffle may redraw before it stops asking politely. */
const SHUFFLE_ATTEMPTS = 8;

/**
 * Builds a map whose keys are untrusted strings.
 *
 * Plain assignment cannot be used: `map["__proto__"] = "c1"` sets no own property at all, so a
 * blank named `__proto__` would vanish from the key and its answer would be unrecoverable — the
 * exact failure the whole-or-nothing rule exists to prevent. `defineProperty` stores it as data.
 * The result is a plain object, so it survives `JSON.stringify` and compares with `deepStrictEqual`.
 */
function toPlainMap(entries) {
  const map = {};
  for (const [key, value] of entries) {
    Object.defineProperty(map, key, {value, enumerable: true, writable: true, configurable: true});
  }
  return map;
}

function lookup(map, id) {
  if (!map || typeof map !== "object") return null;
  if (typeof id !== "string") return null;
  return Object.prototype.hasOwnProperty.call(map, id) ? map[id] : null;
}

/**
 * The default source of shuffle indices.
 *
 * `Math.random` is V8's xorshift128+, whose internal state is recoverable from a short run of
 * observed outputs — and every output of this module's shuffle is published, as the item order of an
 * `OrderingRedacted` and the candidate order of a `FillBlankRedacted`. Enough public questions and
 * the generator is predictable, which makes the next question's order predictable with it.
 * `crypto.randomInt` is drawn from the CSPRNG and is uniform over the bound, with no modulo skew.
 */
function secureIndex(bound) {
  return bound <= 1 ? 0 : randomInt(bound);
}

/**
 * Adapts an injected `Math.random`-shaped source to the index-drawing contract.
 *
 * The index is clamped rather than trusted. `random` is a parameter, and a caller handing back 1 or
 * a negative would otherwise swap with `undefined` and silently drop a row from a published
 * question. Nothing in this module throws, including on its own arguments.
 */
function indexSourceFrom(random) {
  return function nextIndex(bound) {
    if (bound <= 1) return 0;
    const drawn = Math.floor(random() * bound);
    if (!Number.isFinite(drawn)) return 0;
    return Math.min(bound - 1, Math.max(0, drawn));
  };
}

/** Fisher-Yates over a copy. */
function fisherYates(rows, nextIndex) {
  const out = rows.slice();
  for (let i = out.length - 1; i > 0; i -= 1) {
    const j = nextIndex(i + 1);
    const held = out[i];
    out[i] = out[j];
    out[j] = held;
  }
  return out;
}

function isCanonical(shuffled, rows) {
  return shuffled.every((row, index) => row.id === rows[index].id);
}

/**
 * Shuffles, and refuses to hand back the order it was given.
 *
 * A fair shuffle of two rows is the identity half the time, and `items.size in 2..8` makes a
 * two-item Ordering legal — so one published question in two would show its answer in the order it
 * was drawn in. "Random" and "hides the answer" are different properties, and this module owes the
 * second one. The redraw is bounded, because a `random` that always returns 1 clamps to the
 * identity every time; after that a rotation is forced, which is deterministic but never canonical.
 */
function shuffle(rows, nextIndex) {
  if (rows.length < 2) return rows.slice();
  for (let attempt = 0; attempt < SHUFFLE_ATTEMPTS; attempt += 1) {
    const candidate = fisherYates(rows, nextIndex);
    if (!isCanonical(candidate, rows)) return candidate;
  }
  return rows.slice(1).concat(rows[0]);
}

/**
 * Reads a list of `{id, text}` rows, or null if the list is unusable.
 *
 * Both fields must be strings. `text` is not optional here even though it is only ever read for its
 * length: `questionCharsCount` (`lesson-reward.js:135`) sums these texts and the reward, the unlock
 * price and the client timer are all computed from that sum. A row whose text this module cannot
 * copy through verbatim is a row whose question would be worth a different amount after
 * publication, so it refuses instead.
 *
 * Fewer than two rows is refused for a different reason: there is no shuffle of one row, and a
 * single option, item or candidate simply *is* the answer. `QuestionContent` requires 2..8 anyway.
 * The upper bound and the odder invariants (`candidates.size == 5 || 10`, `blanks.size in 1..3`)
 * are deliberately not mirrored — refusing a real corpus question is worse than publishing one the
 * schema would have grumbled about, and the answer still leaves either way.
 */
function readRows(rows) {
  if (!Array.isArray(rows) || rows.length < MIN_ROWS) return null;
  const out = [];
  for (const row of rows) {
    if (!row || typeof row !== "object" || Array.isArray(row)) return null;
    if (typeof row.id !== "string" || row.id === "") return null;
    if (typeof row.text !== "string") return null;
    out.push({id: row.id, text: row.text});
  }
  return out;
}

function hasUniqueIds(rows) {
  return new Set(rows.map((row) => row.id)).size === rows.length;
}

/**
 * The difficulty the public half will carry.
 *
 * A `difficulty` the payload actually has is copied verbatim — including `""`. That empty string is
 * not a missing value: `parseQuestionPayload` (`index.js:1318-1325`) merges `{...fallback, ...parsed}`
 * so the payload's `""` already wins over the document field today, and `lessonAllocatedSeconds`
 * (`lesson-reward.js:180`) reads `"" || "EASY"` as EASY. Substituting the document's `"HARD"` there
 * would move the question from the easy pool to the hard one and change the reward, the unlock
 * price and the client timer — which is the one thing redaction must not do.
 *
 * The argument fills only a genuinely absent field, where losing it would default a hard question
 * to EASY and zero its reward. A `difficulty` that is present but not a string is refusal, not
 * fallback: substituting a value there would change what the question is worth rather than preserve
 * it.
 */
const DIFFICULTY_ABSENT = null;
const DIFFICULTY_REFUSED = false;

function resolveDifficulty(source, fallback) {
  if (source.difficulty !== undefined && source.difficulty !== null) {
    return typeof source.difficulty === "string" ? source.difficulty : DIFFICULTY_REFUSED;
  }
  if (typeof fallback === "string" && fallback !== "") return fallback;
  return DIFFICULTY_ABSENT;
}

/**
 * The fields every public half carries, whatever its type. Returns a reason string on refusal.
 *
 * `id`, `text` and `imageUrl` are copied verbatim; `imageUrl` is worth a flat hundred characters to
 * `questionCharsCount` whenever it is truthy, so a non-string there is refused rather than
 * normalised to null.
 */
function publicBase(source, type, difficulty) {
  if (typeof source.text !== "string") return "missing-text";
  if (source.imageUrl !== undefined && source.imageUrl !== null && typeof source.imageUrl !== "string") {
    return "invalid-image-url";
  }
  const level = resolveDifficulty(source, difficulty);
  if (level === DIFFICULTY_REFUSED) return "invalid-difficulty";

  const base = {type};
  if (typeof source.id === "string" && source.id !== "") base.id = source.id;
  if (level !== DIFFICULTY_ABSENT) base.difficulty = level;
  base.text = source.text;
  base.imageUrl = typeof source.imageUrl === "string" ? source.imageUrl : null;
  return {base};
}

/**
 * SingleChoice: every option stays exactly as it was; only the pointer to one of them leaves.
 *
 * Each split function returns `{fields, key}` or a reason string.
 */
function splitSingleChoice(source) {
  const options = readRows(source.options);
  if (!options) return "invalid-options";
  const correctOptionId = source.correctOptionId;
  if (typeof correctOptionId !== "string" || correctOptionId === "") return "missing-correct-option";
  // A key naming an option the question does not offer cannot be checked against the public half
  // later, and a question published without a usable key is a question nobody can score.
  if (!options.some((option) => option.id === correctOptionId)) return "dangling-correct-option";
  return {
    fields: {options},
    key: {type: CONTENT_TYPE.SINGLE_CHOICE, correctOptionId},
  };
}

/**
 * MultipleChoice: same public shape as SingleChoice, which is the point.
 *
 * Dropping `correctOptionIds` whole is what hides **how many** answers are correct. A public half
 * that kept the count — as a length, a `correctCount`, or a padded list — would cut the search
 * space enormously: two of five is ten guesses rather than thirty-one.
 */
function splitMultipleChoice(source) {
  const options = readRows(source.options);
  if (!options) return "invalid-options";
  const correctOptionIds = source.correctOptionIds;
  if (!Array.isArray(correctOptionIds)) return "missing-correct-options";
  // An empty correct set is not a question with no answer, it is a question whose answer was lost
  // before it got here. Refused whole rather than published with an unanswerable key.
  if (correctOptionIds.length === 0) return "empty-correct-options";
  const optionIds = new Set(options.map((option) => option.id));
  for (const id of correctOptionIds) {
    if (typeof id !== "string" || !optionIds.has(id)) return "dangling-correct-option";
  }
  return {
    fields: {options},
    key: {type: CONTENT_TYPE.MULTIPLE_CHOICE, correctOptionIds: correctOptionIds.slice()},
  };
}

/**
 * Ordering: the answer *is* the array order, so the array order is what has to go.
 *
 * Shuffling alone is not enough — the ids travel with their rows, and `ord-0…` sorted
 * lexicographically hands the answer straight back. So the shuffled rows are re-issued `ri-0…`
 * along their new order and the mapping back is kept in the key.
 *
 * Item ids must be unique here even though `QuestionContent.Ordering` does not require it: the key
 * records the canonical order as a list of ids, and a repeated id makes "which row goes where"
 * unanswerable on the way back.
 */
function splitOrdering(source, nextIndex) {
  const items = readRows(source.items);
  if (!items) return "invalid-items";
  if (!hasUniqueIds(items)) return "duplicate-item-ids";

  const shuffled = shuffle(items, nextIndex);
  const publicItems = shuffled.map((item, index) => ({id: `${ORDER_ITEM_PREFIX}${index}`, text: item.text}));
  const idMap = toPlainMap(shuffled.map((item, index) => [`${ORDER_ITEM_PREFIX}${index}`, item.id]));

  return {
    fields: {items: publicItems},
    key: {
      type: CONTENT_TYPE.ORDERING,
      order: items.map((item) => item.id),
      idMap,
    },
  };
}

/**
 * Whether a protected segment gives an answer away.
 *
 * Containment, not equality. The segments are lifted out of the author's own prose
 * (`FillBlankAuthoring.kt:142-152`), so an answer arrives inside them punctuated and in context —
 * `"Kotlin,"`, `"the Kotlin language"` — and an equality test lets every one of those through to be
 * published beside a shuffled candidate list. Trimmed and case-folded, since
 * `extractProtectedTextSegments` already deduplicates by lowercase.
 *
 * An empty answer text is skipped: every string contains it, and a candidate with no text would
 * otherwise silently delete the whole list.
 */
function isAnswerText(segment, answerTexts) {
  const folded = segment.trim().toLowerCase();
  for (const answer of answerTexts) {
    if (answer !== "" && folded.includes(answer)) return true;
  }
  return false;
}

/**
 * FillBlank: the blanks keep their order and lose their answers; the candidates keep their texts and
 * lose their identity.
 *
 * `blanks` becomes a list of bare ids in the original order — the order is how the client lines the
 * blanks up with the markers in `text`, and it says nothing about which candidate fills which.
 * Candidates are shuffled and re-issued `rc-0…` because the authoring component puts the correct
 * ones first as `cand-0..cand-(n-1)`.
 *
 * `protectedTextSegments` is kept but filtered. It exists so the authoring editor can restore the
 * `***word***` markup, and every entry in it is by construction an answer spelled out in full — a
 * live fixture pairs `c1 → "Kotlin"` with `protectedTextSegments: ["Kotlin"]`
 * (`QuestionContentParserTest.kt:76`). Republishing that beside a shuffled candidate list would
 * undo the shuffle in one reading. In practice the filter empties the list; it is written as a
 * filter rather than a deletion so a segment that is genuinely not an answer survives.
 */
function splitFillBlank(source, nextIndex) {
  const candidates = readRows(source.candidates);
  if (!candidates) return "invalid-candidates";
  if (!hasUniqueIds(candidates)) return "duplicate-candidate-ids";

  const blanks = source.blanks;
  if (!Array.isArray(blanks) || blanks.length === 0) return "invalid-blanks";
  const candidateIds = new Set(candidates.map((candidate) => candidate.id));
  const blankIds = [];
  const pairs = [];
  for (const blank of blanks) {
    if (!blank || typeof blank !== "object" || Array.isArray(blank)) return "invalid-blanks";
    if (typeof blank.id !== "string" || blank.id === "") return "invalid-blanks";
    if (blankIds.includes(blank.id)) return "duplicate-blank-ids";
    const correctCandidateId = blank.correctCandidateId;
    if (typeof correctCandidateId !== "string" || !candidateIds.has(correctCandidateId)) {
      return "dangling-correct-candidate";
    }
    blankIds.push(blank.id);
    pairs.push([blank.id, correctCandidateId]);
  }

  const textById = new Map(candidates.map((candidate) => [candidate.id, candidate.text]));
  const answerTexts = new Set(pairs.map(([, id]) => String(textById.get(id)).trim().toLowerCase()));

  const fields = {blanks: blankIds};

  const shuffled = shuffle(candidates, nextIndex);
  fields.candidates = shuffled.map((candidate, index) => ({
    id: `${CANDIDATE_PREFIX}${index}`,
    text: candidate.text,
  }));
  const idMap = toPlainMap(shuffled.map((candidate, index) => [`${CANDIDATE_PREFIX}${index}`, candidate.id]));

  if (source.protectedTextSegments !== undefined && source.protectedTextSegments !== null) {
    const segments = source.protectedTextSegments;
    if (!Array.isArray(segments)) return "invalid-protected-segments";
    for (const segment of segments) {
      if (typeof segment !== "string") return "invalid-protected-segments";
    }
    fields.protectedTextSegments = segments.filter((segment) => !isAnswerText(segment, answerTexts));
  }

  return {
    fields,
    key: {
      type: CONTENT_TYPE.FILL_BLANK,
      blankToCandidate: toPlainMap(pairs),
      idMap,
    },
  };
}

function outcome(status, publicPayload, key, reason) {
  return {status, publicPayload, key: key || null, reason: reason || null};
}

/**
 * Splits one payload into what may be published and what must not be.
 *
 * @param payloadJson the question's `payload` field, as stored — a JSON string
 * @param difficulty the document's difficulty, used only when the payload carries none at all
 * @param options `{random, questionId}` — `random` is a `Math.random`-shaped source, injected so
 *   tests are deterministic; left out, the shuffle draws from `crypto.randomInt`. `questionId` is
 *   the document id, stamped into the key so a key cannot be applied to another question's half;
 *   the payload's own `id` is the fallback, and the seed corpus has none (`_helpers.js:26-36`).
 * @returns `{status, publicPayload, key, reason}`.
 *   - `redacted` — `publicPayload` is the safe half, `key` holds the answer.
 *   - `not-applicable` — a Survey. No answer exists to take (`QuestionContent.kt:46-60`); the
 *     payload comes back unchanged and is safe to publish as it is.
 *   - `already-redacted` — one of this module's own shapes. Unchanged, safe, and any key already
 *     stored for this question must be left where it is rather than cleared.
 *   - `refused` — the payload could not be split completely and **still contains its answer**.
 *     `reason` names why. Publishing it is what this status exists to prevent.
 *
 * Nothing throws. This runs inside a publish batch, and one unreadable payload must not be able to
 * fail the publication of every other question beside it.
 */
function redact(payloadJson, difficulty, options) {
  if (typeof payloadJson !== "string") {
    return outcome(STATUS.REFUSED, payloadJson, null, "not-a-string");
  }

  let source;
  try {
    source = JSON.parse(payloadJson);
  } catch (error) {
    return outcome(STATUS.REFUSED, payloadJson, null, "malformed-json");
  }
  if (!source || typeof source !== "object" || Array.isArray(source)) {
    return outcome(STATUS.REFUSED, payloadJson, null, "not-an-object");
  }

  if (source.type === CONTENT_TYPE.SURVEY) {
    return outcome(STATUS.NOT_APPLICABLE, payloadJson, null, null);
  }
  if (SOURCE_TYPE_OF.has(source.type)) {
    return outcome(STATUS.ALREADY_REDACTED, payloadJson, null, null);
  }

  const nextIndex = options && typeof options.random === "function"
    ? indexSourceFrom(options.random)
    : secureIndex;

  let split;
  if (source.type === CONTENT_TYPE.SINGLE_CHOICE) split = splitSingleChoice(source);
  else if (source.type === CONTENT_TYPE.MULTIPLE_CHOICE) split = splitMultipleChoice(source);
  else if (source.type === CONTENT_TYPE.ORDERING) split = splitOrdering(source, nextIndex);
  else if (source.type === CONTENT_TYPE.FILL_BLANK) split = splitFillBlank(source, nextIndex);
  else return outcome(STATUS.REFUSED, payloadJson, null, "unknown-type");

  if (typeof split === "string") return outcome(STATUS.REFUSED, payloadJson, null, split);

  const based = publicBase(source, REDACTED_TYPE[source.type], difficulty);
  if (typeof based === "string") return outcome(STATUS.REFUSED, payloadJson, null, based);

  const questionId = options && typeof options.questionId === "string" && options.questionId !== ""
    ? options.questionId
    : (typeof source.id === "string" && source.id !== "" ? source.id : null);

  return outcome(
    STATUS.REDACTED,
    JSON.stringify({...based.base, ...split.fields}),
    {version: KEY_VERSION, questionId, ...split.key},
    null,
  );
}

/**
 * Reconstructs the original answer from the key alone.
 *
 * This exists to be executable proof that a key is complete: if a key needed the public half to
 * spell its own answer out, that would show up here as a missing argument rather than as a scoring
 * bug months later. Each type returns the answer in its own shape — an id, a set of ids, an order,
 * a blank-to-candidate mapping — all in the question's original ids, never the re-issued ones.
 *
 * Copies are defensive: a caller mutating the returned value must not reach into the key.
 */
function restoreAnswer(key) {
  if (!key || typeof key !== "object") return null;
  if (key.type === CONTENT_TYPE.SINGLE_CHOICE) {
    if (typeof key.correctOptionId !== "string") return null;
    return {type: CONTENT_TYPE.SINGLE_CHOICE, correctOptionId: key.correctOptionId};
  }
  if (key.type === CONTENT_TYPE.MULTIPLE_CHOICE) {
    if (!Array.isArray(key.correctOptionIds)) return null;
    return {type: CONTENT_TYPE.MULTIPLE_CHOICE, correctOptionIds: key.correctOptionIds.slice()};
  }
  if (key.type === CONTENT_TYPE.ORDERING) {
    if (!Array.isArray(key.order)) return null;
    return {type: CONTENT_TYPE.ORDERING, order: key.order.slice()};
  }
  if (key.type === CONTENT_TYPE.FILL_BLANK) {
    const mapping = key.blankToCandidate;
    if (!mapping || typeof mapping !== "object") return null;
    return {
      type: CONTENT_TYPE.FILL_BLANK,
      blankToCandidate: toPlainMap(Object.keys(mapping).map((id) => [id, mapping[id]])),
    };
  }
  return null;
}

/** Whether this key and this public half describe the same question, as far as either can tell. */
function isPairable(pub, key) {
  if (key.version !== KEY_VERSION) return false;
  const half = typeof pub.id === "string" && pub.id !== "" ? pub.id : null;
  const keyed = typeof key.questionId === "string" && key.questionId !== "" ? key.questionId : null;
  // Neither side is required to carry an id — the seed corpus payloads have none — so this catches a
  // crossed pair whenever it can and says nothing when it cannot.
  return half === null || keyed === null || half === keyed;
}

function restoredBase(pub, type) {
  const base = {type};
  if (pub.id !== undefined) base.id = pub.id;
  if (pub.difficulty !== undefined) base.difficulty = pub.difficulty;
  base.text = pub.text;
  base.imageUrl = pub.imageUrl === undefined ? null : pub.imageUrl;
  return base;
}

/**
 * Puts the two halves back together into something `evaluateAnswer` can score.
 *
 * This is the operation the write path and the scorer would otherwise each write for themselves,
 * and getting it wrong is quiet: for Ordering the items must be **physically reordered** by
 * `key.order`, because `scoreOrdering` reads the correct order out of the array order — a merge
 * that only swapped the discriminator back would leave them shuffled and score the true answer 5
 * against its own key. It lives here, once, with tests on it.
 *
 * `info` and any filtered `protectedTextSegments` are gone for good; neither is part of an answer
 * and neither is needed to score one.
 *
 * @param publicPayload the stored public half, as a JSON string or an already-parsed object
 * @returns the original-shaped content, or null if the two do not belong together
 */
function restoreContent(publicPayload, key) {
  if (!key || typeof key !== "object") return null;
  let pub = publicPayload;
  if (typeof pub === "string") {
    try {
      pub = JSON.parse(pub);
    } catch (error) {
      return null;
    }
  }
  if (!pub || typeof pub !== "object" || Array.isArray(pub)) return null;
  if (pub.type !== REDACTED_TYPE[key.type]) return null;
  if (!isPairable(pub, key)) return null;

  if (key.type === CONTENT_TYPE.SINGLE_CHOICE) {
    if (typeof key.correctOptionId !== "string" || !Array.isArray(pub.options)) return null;
    return {...restoredBase(pub, key.type), options: pub.options, correctOptionId: key.correctOptionId};
  }

  if (key.type === CONTENT_TYPE.MULTIPLE_CHOICE) {
    if (!Array.isArray(key.correctOptionIds) || !Array.isArray(pub.options)) return null;
    return {
      ...restoredBase(pub, key.type),
      options: pub.options,
      correctOptionIds: key.correctOptionIds.slice(),
    };
  }

  if (key.type === CONTENT_TYPE.ORDERING) {
    if (!Array.isArray(pub.items) || !Array.isArray(key.order)) return null;
    const textByOriginalId = new Map();
    for (const item of pub.items) {
      if (!item || typeof item !== "object") return null;
      const originalId = lookup(key.idMap, item.id);
      if (originalId === null) return null;
      textByOriginalId.set(originalId, item.text);
    }
    if (textByOriginalId.size !== key.order.length) return null;
    const items = [];
    for (const id of key.order) {
      if (!textByOriginalId.has(id)) return null;
      items.push({id, text: textByOriginalId.get(id)});
    }
    return {...restoredBase(pub, key.type), items};
  }

  if (key.type === CONTENT_TYPE.FILL_BLANK) {
    if (!Array.isArray(pub.candidates) || !Array.isArray(pub.blanks)) return null;
    const candidates = [];
    for (const row of pub.candidates) {
      if (!row || typeof row !== "object") return null;
      const originalId = lookup(key.idMap, row.id);
      if (originalId === null) return null;
      candidates.push({id: originalId, text: row.text});
    }
    const blanks = [];
    for (const blankId of pub.blanks) {
      const candidateId = lookup(key.blankToCandidate, blankId);
      if (candidateId === null) return null;
      blanks.push({id: blankId, correctCandidateId: candidateId});
    }
    return {...restoredBase(pub, key.type), blanks, candidates};
  }

  return null;
}

/**
 * Rewrites an answer submitted against the public half into the question's original ids.
 *
 * A client answering an `OrderingRedacted` sends `ri-` ids and one answering a `FillBlankRedacted`
 * sends `rc-` ids, while `idMap` is stored the other way round and `restoreAnswer` deliberately
 * speaks only in original ids. Without this, every call site does that translation by hand against
 * a private field of the key.
 *
 * An id the map does not know becomes null rather than being passed through: null cannot collide
 * with a real id, so a crafted or stale submission scores the floor instead of accidentally
 * matching. Choice types are returned as they came — their option ids were never re-issued.
 */
function translateSubmittedAnswer(answer, key) {
  if (!answer || typeof answer !== "object" || Array.isArray(answer)) return null;
  if (!key || typeof key !== "object") return null;

  if (key.type === CONTENT_TYPE.SINGLE_CHOICE || key.type === CONTENT_TYPE.MULTIPLE_CHOICE) {
    return {...answer};
  }
  if (key.type === CONTENT_TYPE.ORDERING) {
    const order = Array.isArray(answer.order) ? answer.order : [];
    return {...answer, order: order.map((id) => lookup(key.idMap, id))};
  }
  if (key.type === CONTENT_TYPE.FILL_BLANK) {
    const filled = answer.filled && typeof answer.filled === "object" ? answer.filled : {};
    return {
      ...answer,
      filled: toPlainMap(Object.keys(filled).map((blankId) => [blankId, lookup(key.idMap, filled[blankId])])),
    };
  }
  return null;
}

module.exports = {
  CONTENT_TYPE,
  REDACTED_TYPE,
  STATUS,
  KEY_VERSION,
  redact,
  restoreAnswer,
  restoreContent,
  translateSubmittedAnswer,
};
