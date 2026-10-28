"use strict";

const assert = require("assert");
const Module = require("module");

const {createFirestoreStub} = require("./_firestore-stub");

/**
 * Серия опросов end-to-end (история 8.2 эпика `epic-social-features`, UX-O7) — поверх настоящего
 * кода. Публикация и голос идут через единственный приёмник мутаций (AD-6/AD-7) над
 * in-memory-хранилищем, как у ready-ACK: ключ рождается из тройки идентичности (§2), повтор
 * известного ключа возвращает сохранённое (§6.1), уникальность участник×вопрос серверная и
 * переживает новый ключ (§3), голос живёт только в опубликованной revision (§6.2).
 */

// ─── The store, installed before index.js is loaded ─────────────────────────────────────────────

const {admin, store} = createFirestoreStub({});
const adminPath = require.resolve("firebase-admin");
const stubModule = new Module(adminPath, null);
stubModule.filename = adminPath;
stubModule.loaded = true;
stubModule.exports = admin;
require.cache[adminPath] = stubModule;

const functions = require("./index.js");

const SUITE = [];
const test = (name, fn) => SUITE.push([name, fn]);

// ─── The world ──────────────────────────────────────────────────────────────────────────────────

const AUTHOR = "poll-author";
const VOTER = "poll-voter-1";
const NOW = 1_900_000_000_000;

const SERIES = "series-e2e-1";

const SINGLE_Q = {
  questionIndex: 0,
  text: "Favorite color?",
  mode: "SINGLE",
  options: [
    {optionId: "opt-red", text: "Red"},
    {optionId: "opt-green", text: "Green"},
    {optionId: "opt-blue", text: "Blue"},
  ],
};
const MULTI_Q = {
  questionIndex: 1,
  text: "Which fruits do you like?",
  mode: "MULTIPLE",
  options: [
    {optionId: "f-apple", text: "Apple"},
    {optionId: "f-plum", text: "Plum"},
    {optionId: "f-pear", text: "Pear"},
  ],
};

function draft(title, questions, expectedRevisionId) {
  return {seriesId: SERIES, title, expectedRevisionId, questions};
}

function publishKey(expectedRevisionId) {
  // Как у Kotlin-писателя: ключ из серии и ожидаемой следующей revision.
  return `polls-PUBLISH_SERIES-series-${SERIES}-r${expectedRevisionId + 1}`;
}

function voteKey(seriesId, revisionId, questionIndex) {
  // §2 (поправка 2026-10-18): ключ несёт участника, как его строит Kotlin-писатель
  // (PollOutboxWriters.voteSourceId): `…-q<idx>-u-<uid>`. Все голоса этой сюиты — за VOTER.
  return `polls-SUBMIT_VOTE-series-${seriesId}-r${revisionId}-q${questionIndex}-u-${VOTER}`;
}

async function withFixedClock(atMs, fn) {
  const realNow = Date.now;
  Date.now = () => atMs;
  try {
    return await fn();
  } finally {
    Date.now = realNow;
  }
}

function submit(uid, mutationId, operation, payload) {
  return functions.submitMutation.run({
    auth: {uid},
    data: {mutationId, operation, payload: JSON.stringify(payload)},
  });
}

async function publish(uid, expected, questions, title) {
  const body = draft(title || "Demo poll", questions, expected);
  return withFixedClock(NOW, () =>
    submit(uid, publishKey(expected), "polls.PUBLISH_SERIES", body));
}

function voteDocId(seriesId, revisionId, questionIndex, uid) {
  return `poll_votes/${seriesId}-r${revisionId}-q${questionIndex}-${uid}`;
}

function seriesDoc() {
  return store.documents.get(`poll_series/${SERIES}`);
}

function resetWorld() {
  store.reset({[`profiles/${AUTHOR}`]: {uid: AUTHOR}, [`profiles/${VOTER}`]: {uid: VOTER}});
}

function httpsErrorOf(promise) {
  return promise.then(
    () => null,
    (error) => error,
  );
}

// ─── Publish: единый draft становится опубликованной revision ───────────────────────────────────

test("publish creates the series with revision 1 through the real receiver", async () => {
  resetWorld();
  const result = await publish(AUTHOR, 0, [SINGLE_Q, MULTI_Q]);
  assert.strictEqual(result.replayed, false);
  assert.deepStrictEqual(result.result, {seriesId: SERIES, revisionId: 1, questionCount: 2});

  const stored = seriesDoc();
  assert.ok(stored, "the series document exists");
  assert.strictEqual(stored.authorUid, AUTHOR, "the author is the authenticated uid, not the body");
  assert.strictEqual(stored.revisionId, 1);
  assert.strictEqual(stored.revision.questions[0].mode, "SINGLE");
  assert.strictEqual(stored.revision.questions.length, 2, "no correctness/difficulty fields exist");
  assert.strictEqual(stored.revision.publishedAtMs, NOW);
});

test("the repeated publish key replays the stored revision without running the handler", async () => {
  resetWorld();
  const first = await publish(AUTHOR, 0, [SINGLE_Q, MULTI_Q]);
  const replay = await publish(AUTHOR, 0, [SINGLE_Q, MULTI_Q]);
  assert.strictEqual(replay.replayed, true, "the known key returns the stored result (AD-6)");
  assert.deepStrictEqual(replay.result, first.result);
  assert.strictEqual(seriesDoc().revisionId, 1, "no second revision");
});

test("republishing identical content is a no-op, not a reopened revision", async () => {
  resetWorld();
  await publish(AUTHOR, 0, [SINGLE_Q, MULTI_Q]);
  // Новое намерение (новый ключ: другой ожидаемый базис), но то же содержание — новая revision
  // переоткрыла бы голосование с нуля, чего §6.2 не делает.
  const repeat = await publish(AUTHOR, 1, [SINGLE_Q, MULTI_Q]);
  assert.strictEqual(repeat.replayed, false);
  assert.deepStrictEqual(repeat.result, {seriesId: SERIES, revisionId: 1, questionCount: 2});
  assert.strictEqual(seriesDoc().revisionId, 1);
  assert.strictEqual(seriesDoc().revision.publishedAtMs, NOW, "the published moment did not move");
});

test("a real content edit bumps the revision; a stale basis refuses by name", async () => {
  resetWorld();
  await publish(AUTHOR, 0, [SINGLE_Q, MULTI_Q]);
  const edited = await withFixedClock(NOW + 1000, () => publish(AUTHOR, 1, [SINGLE_Q]));
  assert.strictEqual(edited.result.revisionId, 2, "the new identity is revision 2 (§6.2)");
  assert.strictEqual(seriesDoc().revision.questions.length, 1);

  const stale = await httpsErrorOf(withFixedClock(NOW + 2000, () =>
    // Ключ-проба (не тот, что вывел бы писатель из ожидаемого базиса): гейт ключей тут молчит,
    // и семантическую сверку базиса держит сам обработчик — поименованный конфликт.
    submit(AUTHOR, `${publishKey(0)}-probe`, "polls.PUBLISH_SERIES", draft("Demo poll", [SINGLE_Q], 0))));
  assert.strictEqual(stale.code, "failed-precondition");
  assert.ok(stale.message.includes("POLL_SERIES_REVISION_CONFLICT"), stale.message);
});

// ─── Publish: владение серией (ретро эпика 8, находка 2) ────────────────────────────────────────

test("publishing someone else's series refuses by name and rewrites nothing", async () => {
  resetWorld();
  await publish(AUTHOR, 0, [SINGLE_Q, MULTI_Q]);
  const foreign = await httpsErrorOf(withFixedClock(NOW + 1000, () =>
    // Чужая рука с актуальным базисом и новым содержанием: без проверки владения ветка бампа
    // перезаписала бы вопросы и вписала authorUid атакующего.
    submit(VOTER, `${publishKey(1)}-foreign`, "polls.PUBLISH_SERIES", draft("Hijacked", [SINGLE_Q], 1))));
  assert.strictEqual(foreign.code, "permission-denied");
  assert.ok(foreign.message.includes("Poll series belongs to another account"), foreign.message);

  const stored = seriesDoc();
  assert.strictEqual(stored.authorUid, AUTHOR, "authorUid survives the foreign attempt");
  assert.strictEqual(stored.revisionId, 1, "no revision bump happened");
  assert.strictEqual(stored.title, "Demo poll", "stored content is untouched");
  assert.strictEqual(stored.revision.questions.length, 2);
});

test("a foreign no-op (identical content) is denied, not returned as success", async () => {
  resetWorld();
  await publish(AUTHOR, 0, [SINGLE_Q, MULTI_Q]);
  const foreign = await httpsErrorOf(withFixedClock(NOW + 1000, () =>
    submit(VOTER, `${publishKey(1)}-foreign-noop`, "polls.PUBLISH_SERIES",
      draft("Demo poll", [SINGLE_Q, MULTI_Q], 1))));
  assert.strictEqual(foreign.code, "permission-denied",
    "publishing is the author's right: same content does not make it the stranger's");
  assert.strictEqual(seriesDoc().revisionId, 1);
});

test("a fresh series can be created by any account, which becomes its author", async () => {
  resetWorld();
  const fresh = await withFixedClock(NOW + 500, () =>
    submit(VOTER, "polls-PUBLISH_SERIES-series-fresh-r1", "polls.PUBLISH_SERIES",
      {seriesId: "series-fresh", title: "Fresh", expectedRevisionId: 0, questions: [SINGLE_Q]}));
  assert.strictEqual(fresh.result.revisionId, 1);
  assert.strictEqual(store.documents.get("poll_series/series-fresh").authorUid, VOTER,
    "creation keeps ownership follows facts: the creator is the author");
});

// ─── Publish: предсуществующие ветки без тестов (open item 9 ретро-перевёрстки) ─────────────────

test("a repeat of the same intention returns the stored revision without a second one", async () => {
  resetWorld();
  await publish(AUTHOR, 0, [SINGLE_Q, MULTI_Q]);
  // Ветка current == expected + 1 при том же содержании: резерв ключа протух, намерение
  // повторилось под новым ключом с прежним ожидаемым базисом — обработчик возвращает то, что
  // уже стоит, без второй revision (§6.2: повтор не переоткрывает голосование).
  const repeat = await withFixedClock(NOW + 1000, () =>
    submit(AUTHOR, `${publishKey(0)}-repeat`, "polls.PUBLISH_SERIES",
      draft("Demo poll", [SINGLE_Q, MULTI_Q], 0)));
  assert.strictEqual(repeat.replayed, false);
  assert.deepStrictEqual(repeat.result, {seriesId: SERIES, revisionId: 1, questionCount: 2});
  assert.strictEqual(seriesDoc().revisionId, 1);
});

test("an expected revision onto a missing series refuses by name and creates nothing", async () => {
  resetWorld();
  const ghost = await httpsErrorOf(withFixedClock(NOW, () =>
    submit(AUTHOR, publishKey(1), "polls.PUBLISH_SERIES", draft("Ghost", [SINGLE_Q], 1))));
  assert.strictEqual(ghost.code, "failed-precondition");
  assert.ok(ghost.message.includes("POLL_SERIES_REVISION_CONFLICT"), ghost.message);
  assert.ok(ghost.message.includes("series does not exist"), ghost.message);
  assert.strictEqual(store.documents.has(`poll_series/${SERIES}`), false, "no series document appeared");
});

test("same content compares canonically: key order in the body is not an edit", async () => {
  resetWorld();
  await publish(AUTHOR, 0, [SINGLE_Q, MULTI_Q]);
  // Open item 9 (deferred посылка ревью): sameContent сверяет через JSON.stringify и посылка
  // называла чувствительность к порядку ключей. Канонизация normalizePollSeriesDraft
  // перестраивает вопрос и вариант в фиксированном порядке полей, поэтому порядок ключей в
  // присланном теле сравнение не меняет — закрепляем фактическое поведение: no-op, а не лишняя
  // revision. (Порядок вариантов в массиве — содержание, его канонизация не трогает.)
  const flipped = {
    questionIndex: 0, mode: SINGLE_Q.mode, text: SINGLE_Q.text,
    options: [{text: "Red", optionId: "opt-red"}, {text: "Green", optionId: "opt-green"}, {text: "Blue", optionId: "opt-blue"}],
  };
  const repeat = await withFixedClock(NOW + 1000, () =>
    submit(AUTHOR, `${publishKey(1)}-flipped`, "polls.PUBLISH_SERIES",
      draft("Demo poll", [flipped, MULTI_Q], 1)));
  assert.deepStrictEqual(repeat.result, {seriesId: SERIES, revisionId: 1, questionCount: 2});
  assert.strictEqual(seriesDoc().revisionId, 1, "key order alone must not reopen the vote");
});

// ─── Publish: атомарность под контеншном (defer фикс-пака — открытый пункт 8 перевёрстки) ────────

test("two publications over the same basis resolve by retry to one revision and a named conflict", async () => {
  resetWorld();
  await publish(AUTHOR, 0, [SINGLE_Q, MULTI_Q]); // r1
  // Издание A поверх базиса r1; пока оно в полёте, издание B поверх того же базиса коммитит r2.
  // Ретрай транзакции (контракт SDK: записи конфликтного прогона выбрасываются, тело
  // перечитывает свежий базис) обязан увидеть r2 и ответить поимённым конфликтом — тихой
  // перезаписи r2 содержимым A нет. Демонстрация на управляемом ретрае заглушки; строгий
  // эмулятор с реальным параллелизмом — открытый пункт владельца до боевого сезона.
  // Ретрай обязан достаться транзакции обработчика (второй runTransaction приёмника), а не
  // гейту ключей (первый): у гейта конфликтного базиса нет. Хук-арбитр: первый ретрай — гейт,
  // перезаряжаем; второй — обработчик, поверх него коммитится издание B настоящим приёмником.
  let gateRetried = false;
  const retryOnHandler = async () => {
    if (!gateRetried) {
      gateRetried = true;
      store.retryTransactionOnce = retryOnHandler; // перезарядка на следующий runTransaction
      return;
    }
    store.retryTransactionOnce = null;
    const b = await submit(AUTHOR, `${publishKey(1)}-b`, "polls.PUBLISH_SERIES",
      draft("Content B", [SINGLE_Q], 1));
    assert.strictEqual(b.result.revisionId, 2, "B lands first on the same basis");
  };
  store.retryTransactionOnce = retryOnHandler;

  const conflicted = await httpsErrorOf(withFixedClock(NOW + 1000, () =>
    submit(AUTHOR, `${publishKey(1)}-a`, "polls.PUBLISH_SERIES",
      draft("Content A", [{...MULTI_Q, questionIndex: 0}], 1))));
  assert.strictEqual(conflicted.code, "failed-precondition");
  assert.ok(conflicted.message.includes("POLL_SERIES_REVISION_CONFLICT"), conflicted.message);
  assert.ok(conflicted.message.includes("current 2"), conflicted.message);

  const stored = seriesDoc();
  assert.strictEqual(stored.revisionId, 2, "exactly one new revision");
  assert.strictEqual(stored.title, "Content B", "the retried A did not silently overwrite B");
});

// ─── Vote: один подтверждённый набор на участника на вопрос ─────────────────────────────────────

async function worldWithPublishedSeries() {
  resetWorld();
  await publish(AUTHOR, 0, [SINGLE_Q, MULTI_Q]);
}

test("a confirmed vote is accepted once through the real receiver", async () => {
  await worldWithPublishedSeries();
  const voted = await withFixedClock(NOW + 2000, () =>
    submit(VOTER, voteKey(SERIES, 1, 0), "polls.SUBMIT_VOTE",
      {seriesId: SERIES, revisionId: 1, questionIndex: 0, selectedOptionIds: ["opt-blue"], confirmedAtMs: NOW + 1500}));
  assert.strictEqual(voted.replayed, false);
  assert.deepStrictEqual(voted.result, {
    seriesId: SERIES, revisionId: 1, questionIndex: 0, selectedOptionIds: ["opt-blue"], acceptedAtMs: NOW + 2000,
  });

  const doc = store.documents.get(voteDocId(SERIES, 1, 0, VOTER));
  assert.ok(doc, "the vote document exists under the deterministic id");
  assert.strictEqual(doc.participantUid, VOTER);
  assert.deepStrictEqual(doc.selectedOptionIds, ["opt-blue"]);
  assert.strictEqual(doc.clientConfirmedAtMs, NOW + 1500, "the frozen fixation moment rides the body");
  assert.strictEqual(doc.acceptedAtMs, NOW + 2000, "the acceptance moment is the server's");
});

test("the repeated vote key replays the receipt without a second vote", async () => {
  await worldWithPublishedSeries();
  const payload = {seriesId: SERIES, revisionId: 1, questionIndex: 0, selectedOptionIds: ["opt-red"], confirmedAtMs: NOW};
  const first = await withFixedClock(NOW + 100, () =>
    submit(VOTER, voteKey(SERIES, 1, 0), "polls.SUBMIT_VOTE", payload));
  const replay = await withFixedClock(NOW + 9000, () =>
    submit(VOTER, voteKey(SERIES, 1, 0), "polls.SUBMIT_VOTE", payload));
  assert.strictEqual(replay.replayed, true);
  assert.deepStrictEqual(replay.result, first.result);
  assert.ok(store.documents.has(voteDocId(SERIES, 1, 0, VOTER)), "exactly one vote document");
});

test("a vote under a NEW key (reinstall) hits the same uniqueness and never adds a second vote", async () => {
  await worldWithPublishedSeries();
  const first = await withFixedClock(NOW + 100, () =>
    submit(VOTER, voteKey(SERIES, 1, 0), "polls.SUBMIT_VOTE",
      {seriesId: SERIES, revisionId: 1, questionIndex: 0, selectedOptionIds: ["opt-red"], confirmedAtMs: NOW}));

  // Переустановка/второе устройство: новый ключ операции, изменённый набор — уникальность §3
  // сильнее нового ключа: участник остаётся один раз, принятый набор не меняется.
  const second = await withFixedClock(NOW + 5000, () =>
    submit(VOTER, `${voteKey(SERIES, 1, 0)}-fresh`, "polls.SUBMIT_VOTE",
      {seriesId: SERIES, revisionId: 1, questionIndex: 0, selectedOptionIds: ["opt-blue"], confirmedAtMs: NOW + 4000}));
  assert.strictEqual(second.replayed, false, "a new key is a new operation run");
  assert.deepStrictEqual(second.result, first.result, "the stored set comes back, not the new one");

  const votes = [...store.documents.keys()].filter((path) => path.startsWith("poll_votes/"));
  assert.strictEqual(votes.length, 1, `exactly one vote document, got: ${votes.join(", ")}`);
});

test("a changed body under the known vote key is a conflict, not a changed vote", async () => {
  await worldWithPublishedSeries();
  await withFixedClock(NOW + 100, () =>
    submit(VOTER, voteKey(SERIES, 1, 0), "polls.SUBMIT_VOTE",
      {seriesId: SERIES, revisionId: 1, questionIndex: 0, selectedOptionIds: ["opt-red"], confirmedAtMs: NOW}));
  const conflict = await httpsErrorOf(withFixedClock(NOW + 200, () =>
    submit(VOTER, voteKey(SERIES, 1, 0), "polls.SUBMIT_VOTE",
      {seriesId: SERIES, revisionId: 1, questionIndex: 0, selectedOptionIds: ["opt-blue"], confirmedAtMs: NOW})));
  assert.strictEqual(conflict.code, "invalid-argument");
  assert.ok(conflict.message.includes("cannot be reused for another operation or payload"), conflict.message);
  assert.deepStrictEqual(
    store.documents.get(voteDocId(SERIES, 1, 0, VOTER)).selectedOptionIds, ["opt-red"]);
});

test("multi-choice accepts a set; single-choice refuses two options by contract", async () => {
  await worldWithPublishedSeries();
  const multi = await withFixedClock(NOW + 100, () =>
    submit(VOTER, voteKey(SERIES, 1, 1), "polls.SUBMIT_VOTE",
      {seriesId: SERIES, revisionId: 1, questionIndex: 1, selectedOptionIds: ["f-apple", "f-pear"], confirmedAtMs: NOW}));
  assert.deepStrictEqual(multi.result.selectedOptionIds, ["f-apple", "f-pear"]);

  const singleTwo = await httpsErrorOf(withFixedClock(NOW + 200, () =>
    submit(VOTER, voteKey(SERIES, 1, 0), "polls.SUBMIT_VOTE",
      {seriesId: SERIES, revisionId: 1, questionIndex: 0, selectedOptionIds: ["opt-red", "opt-blue"], confirmedAtMs: NOW})));
  assert.strictEqual(singleTwo.code, "invalid-argument");
  assert.ok(singleTwo.message.includes("SINGLE"), singleTwo.message);
});

test("votes outside the published identity refuse by name", async () => {
  await worldWithPublishedSeries();
  const unknownOption = await httpsErrorOf(withFixedClock(NOW + 100, () =>
    submit(VOTER, voteKey(SERIES, 1, 0), "polls.SUBMIT_VOTE",
      {seriesId: SERIES, revisionId: 1, questionIndex: 0, selectedOptionIds: ["opt-pink"], confirmedAtMs: NOW})));
  assert.strictEqual(unknownOption.code, "invalid-argument");

  const staleRevision = await httpsErrorOf(withFixedClock(NOW + 100, () =>
    submit(VOTER, voteKey(SERIES, 9, 0), "polls.SUBMIT_VOTE",
      {seriesId: SERIES, revisionId: 9, questionIndex: 0, selectedOptionIds: ["opt-red"], confirmedAtMs: NOW})));
  assert.strictEqual(staleRevision.code, "failed-precondition");
  assert.ok(staleRevision.message.includes("POLL_SERIES_REVISION_MISMATCH"), staleRevision.message);

  const unknownSeries = await httpsErrorOf(withFixedClock(NOW + 100, () =>
    submit(VOTER, voteKey("series-none", 1, 0), "polls.SUBMIT_VOTE",
      {seriesId: "series-none", revisionId: 1, questionIndex: 0, selectedOptionIds: ["opt-red"], confirmedAtMs: NOW})));
  assert.strictEqual(unknownSeries.code, "failed-precondition");
  assert.ok(unknownSeries.message.includes("POLL_SERIES_NOT_FOUND"), unknownSeries.message);
});

test("extra body fields refuse before any write (the excluded never reaches the base)", async () => {
  await worldWithPublishedSeries();
  const extra = await httpsErrorOf(withFixedClock(NOW + 100, () =>
    submit(VOTER, voteKey(SERIES, 1, 0), "polls.SUBMIT_VOTE",
      {seriesId: SERIES, revisionId: 1, questionIndex: 0, selectedOptionIds: ["opt-red"], confirmedAtMs: NOW, token: "x"})));
  assert.strictEqual(extra.code, "invalid-argument");
  assert.ok(extra.message.includes("Unsupported vote fields"), extra.message);
  assert.strictEqual(store.documents.has(voteDocId(SERIES, 1, 0, VOTER)), false);
});

test("an empty or duplicated option set refuses", async () => {
  await worldWithPublishedSeries();
  const empty = await httpsErrorOf(withFixedClock(NOW + 100, () =>
    submit(VOTER, voteKey(SERIES, 1, 1), "polls.SUBMIT_VOTE",
      {seriesId: SERIES, revisionId: 1, questionIndex: 1, selectedOptionIds: [], confirmedAtMs: NOW})));
  assert.strictEqual(empty.code, "invalid-argument");
  const dupes = await httpsErrorOf(withFixedClock(NOW + 100, () =>
    submit(VOTER, voteKey(SERIES, 1, 1), "polls.SUBMIT_VOTE",
      {seriesId: SERIES, revisionId: 1, questionIndex: 1, selectedOptionIds: ["f-apple", "f-apple"], confirmedAtMs: NOW})));
  assert.strictEqual(dupes.code, "invalid-argument");
  assert.strictEqual([...store.documents.keys()].filter((p) => p.startsWith("poll_votes/")).length, 0);
});

// ─── The suite runner (same shape as the other index.js suites) ──────────────────────────────────

(async () => {
  let failed = 0;
  for (const [name, fn] of SUITE) {
    try {
      await fn();
      console.log(`ok - ${name}`);
    } catch (error) {
      failed += 1;
      console.log(`not ok - ${name}`);
      console.log(String(error && error.stack || error));
    }
  }
  if (failed > 0) {
    console.log(`${failed} case(s) failed`);
    process.exit(1);
  }
})();
