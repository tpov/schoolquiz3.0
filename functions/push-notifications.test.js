"use strict";

const assert = require("assert");
const {
  DEAD_TOKEN_CODES,
  FEATURE_NAMESPACES,
  PUSH_EVENTS,
  PushError,
  buildPushData,
  buildPushMessage,
  detachDeviceToken,
  isDeadTokenError,
  namespaceOf,
  sendPushToUser,
} = require("./push-notifications");

// ─── фейки: Firestore и messaging, без сети ───────────────────────────────────────────────────

/**
 * Firestore в памяти: только то, что читает модуль — `collection().doc().collection().get()`,
 * `doc().delete()`, `ref.delete()`. Пути хранятся строкой, чтобы тест мог утверждать, что
 * тронут ровно один документ.
 */
function fakeFirestore(initialPaths = []) {
  const docs = new Set(initialPaths);
  const deleted = [];
  function docRef(path) {
    return {
      id: path.slice(path.lastIndexOf("/") + 1),
      path,
      async delete() {
        deleted.push(path);
        docs.delete(path);
      },
      collection(name) {
        return collectionRef(`${path}/${name}`);
      },
    };
  }
  function collectionRef(path) {
    return {
      doc(id) {
        return docRef(`${path}/${id}`);
      },
      async get() {
        const prefix = `${path}/`;
        const matching = [...docs]
          .filter((docPath) => docPath.startsWith(prefix) && !docPath.slice(prefix.length).includes("/"))
          .sort();
        return {docs: matching.map((docPath) => ({id: docPath.slice(prefix.length), ref: docRef(docPath)}))};
      },
    };
  }
  return {
    collection(name) {
      return collectionRef(name);
    },
    deleted,
    has: (path) => docs.has(path),
    paths: () => [...docs].sort(),
  };
}

/** messaging, который отвечает по таблице `failures`: токен → ошибка. */
function fakeMessaging(failures = {}) {
  const sent = [];
  return {
    sent,
    async send(message) {
      sent.push(message);
      const failure = failures[message.token];
      if (failure) throw failure;
      return `projects/x/messages/${sent.length}`;
    },
  };
}

function fcmError(code) {
  const error = new Error(code);
  error.code = code;
  return error;
}

const silentLog = {warn() {}};
const UID = "user-a";
const devicePath = (uid, token) => `users/${uid}/devices/${token}`;

// ─── сборка сообщения ─────────────────────────────────────────────────────────────────────────

function testMessageCarriesExactlyThreeFields() {
  const data = buildPushData(PUSH_EVENTS.QUEST_PUBLISHED, "quest-7", UID);
  // Ровно три ключа: ни баланса, ни награды, ни тела документа (AD-20).
  assert.deepStrictEqual(Object.keys(data).sort(), ["entityRef", "eventType", "targetUid"]);
  assert.deepStrictEqual(data, {
    eventType: "quest_authoring.QUEST_PUBLISHED",
    entityRef: "quest_authoring.QUEST:quest-7",
    targetUid: UID,
  });
  // FCM принимает в data только строки.
  for (const value of Object.values(data)) assert.strictEqual(typeof value, "string");

  const message = buildPushMessage("tok-1", data);
  assert.strictEqual(message.token, "tok-1");
  assert.deepStrictEqual(message.data, data);
  // Полезная нагрузка — только `data`; `notification` отсутствует, текст собирает клиент по ключу.
  assert.strictEqual(message.notification, undefined);
}

function testMessageRefusesEmptyIdentity() {
  assert.throws(() => buildPushData(PUSH_EVENTS.QUEST_PUBLISHED, "", UID), PushError);
  assert.throws(() => buildPushData(PUSH_EVENTS.QUEST_PUBLISHED, "quest-7", ""), PushError);
  assert.throws(() => buildPushData({eventType: "x"}, "quest-7", UID), PushError);
}

// ─── реестр событий ───────────────────────────────────────────────────────────────────────────

function testRegistryKeepsEventAndEntityInOneFeatureNamespace() {
  const names = Object.keys(PUSH_EVENTS);
  assert.ok(names.length >= 3, "три события спайна");
  for (const name of names) {
    const event = PUSH_EVENTS[name];
    const eventNamespace = namespaceOf(event.eventType);
    const entityNamespace = namespaceOf(event.entityRef("id-1"));
    assert.ok(eventNamespace, `${name}: eventType без пространства имён`);
    assert.strictEqual(entityNamespace, eventNamespace, `${name}: префиксы eventType и entityRef расходятся`);
    assert.ok(FEATURE_NAMESPACES.includes(eventNamespace), `${name}: ${eventNamespace} — не фича`);
    // `<фича>.<СОБЫТИЕ>` и `<фича>.<СУЩНОСТЬ>:<id>` — та же форма, что у entity_ref очереди.
    assert.match(event.eventType, /^[a-z_]+\.[A-Z_]+$/);
    assert.match(event.entityRef("id-1"), /^[a-z_]+\.[A-Z_]+:id-1$/);
  }
  // Один eventType — одно событие.
  const types = names.map((name) => PUSH_EVENTS[name].eventType);
  assert.strictEqual(new Set(types).size, types.length);
  assert.strictEqual(namespaceOf("NO_DOT"), null);
  assert.strictEqual(namespaceOf(".LEADING"), null);
}

// ─── отправка на каждый токен ─────────────────────────────────────────────────────────────────

async function testOneFailingTokenDoesNotCancelTheOthers() {
  const db = fakeFirestore(["tok-1", "tok-2", "tok-3"].map((token) => devicePath(UID, token)));
  const messaging = fakeMessaging({"tok-2": fcmError("messaging/internal-error")});

  const outcome = await sendPushToUser({db, messaging}, PUSH_EVENTS.QUEST_PUBLISHED, "quest-7", UID, silentLog);

  assert.deepStrictEqual(outcome, {sent: 2, failed: 1, removed: 0});
  assert.deepStrictEqual(messaging.sent.map((message) => message.token).sort(), ["tok-1", "tok-2", "tok-3"]);
  for (const message of messaging.sent) {
    assert.deepStrictEqual(Object.keys(message.data).sort(), ["entityRef", "eventType", "targetUid"]);
  }
  // Временный сбой документ не удаляет — следующая попытка при следующем событии.
  assert.deepStrictEqual(db.deleted, []);
}

async function testDeadTokenDocumentIsRemovedAndOnlyIt() {
  const db = fakeFirestore([
    devicePath(UID, "tok-live"),
    devicePath(UID, "tok-dead"),
    devicePath(UID, "tok-bad"),
    devicePath("user-b", "tok-dead"),
  ]);
  const messaging = fakeMessaging({
    "tok-dead": fcmError("messaging/registration-token-not-registered"),
    "tok-bad": fcmError("messaging/invalid-argument"),
  });

  const outcome = await sendPushToUser({db, messaging}, PUSH_EVENTS.VERIFICATION_APPROVED, UID, UID, silentLog);

  assert.deepStrictEqual(outcome, {sent: 1, failed: 2, removed: 2});
  assert.deepStrictEqual(db.deleted.sort(), [devicePath(UID, "tok-bad"), devicePath(UID, "tok-dead")]);
  // Живой токен и тот же токен под другим аккаунтом не тронуты.
  assert.ok(db.has(devicePath(UID, "tok-live")));
  assert.ok(db.has(devicePath("user-b", "tok-dead")));
}

function testOnlyTheTwoDeadCodesCountAsDead() {
  for (const code of DEAD_TOKEN_CODES) assert.ok(isDeadTokenError(fcmError(code)), code);
  assert.ok(!isDeadTokenError(fcmError("messaging/server-unavailable")));
  assert.ok(!isDeadTokenError(fcmError("messaging/internal-error")));
  assert.ok(!isDeadTokenError(new Error("ECONNRESET")));
  assert.ok(!isDeadTokenError(null));
  // firebase-admin кладёт код и в errorInfo.
  assert.ok(isDeadTokenError({errorInfo: {code: "messaging/invalid-argument"}}));
}

async function testSendNeverThrowsIntoTheCallingOperation() {
  // Нет токенов — молча ноль.
  const empty = fakeFirestore([]);
  assert.deepStrictEqual(
    await sendPushToUser({db: empty, messaging: fakeMessaging()}, PUSH_EVENTS.QUEST_PUBLISHED, "q", UID, silentLog),
    {sent: 0, failed: 0, removed: 0},
  );
  // Firestore недоступен — тоже не наверх.
  const broken = {collection() { throw new Error("firestore down"); }};
  assert.deepStrictEqual(
    await sendPushToUser({db: broken, messaging: fakeMessaging()}, PUSH_EVENTS.QUEST_PUBLISHED, "q", UID, silentLog),
    {sent: 0, failed: 0, removed: 0},
  );
  // Кривой вызов (нет id) — сообщение не собирается, но операция не падает.
  assert.deepStrictEqual(
    await sendPushToUser({db: empty, messaging: fakeMessaging()}, PUSH_EVENTS.QUEST_PUBLISHED, "", UID, silentLog),
    {sent: 0, failed: 0, removed: 0},
  );
}

// ─── detachDeviceToken ────────────────────────────────────────────────────────────────────────

async function testDetachRemovesExactlyOneDocument() {
  const db = fakeFirestore([
    devicePath("user-old", "tok-1"),
    devicePath("user-old", "tok-2"),
    devicePath("user-new", "tok-1"),
  ]);

  const result = await detachDeviceToken(db, {callerUid: "user-new", token: "tok-1", targetUid: "user-old"});

  assert.deepStrictEqual(result, {targetUid: "user-old", detached: true});
  assert.deepStrictEqual(db.deleted, [devicePath("user-old", "tok-1")]);
  assert.deepStrictEqual(db.paths(), [devicePath("user-new", "tok-1"), devicePath("user-old", "tok-2")]);

  // Наблюдаемое следствие (Story 10.5): второе сообщение прежнего аккаунта на этот токен не уходит.
  const messaging = fakeMessaging();
  await sendPushToUser({db, messaging}, PUSH_EVENTS.QUEST_PUBLISHED, "q", "user-old", silentLog);
  assert.deepStrictEqual(messaging.sent.map((message) => message.token), ["tok-2"]);
}

async function testDetachRefusesOwnUidAndBadInput() {
  const db = fakeFirestore([devicePath(UID, "tok-1")]);
  await assert.rejects(
    detachDeviceToken(db, {callerUid: UID, token: "tok-1", targetUid: UID}),
    (error) => error instanceof PushError && error.code === "permission-denied",
  );
  await assert.rejects(
    detachDeviceToken(db, {callerUid: null, token: "tok-1", targetUid: "user-old"}),
    (error) => error instanceof PushError && error.code === "unauthenticated",
  );
  await assert.rejects(
    detachDeviceToken(db, {callerUid: UID, token: "", targetUid: "user-old"}),
    (error) => error instanceof PushError && error.code === "invalid-argument",
  );
  await assert.rejects(
    detachDeviceToken(db, {callerUid: UID, token: "tok-1", targetUid: "user-old/devices"}),
    (error) => error instanceof PushError && error.code === "invalid-argument",
  );
  // Ни один отказ ничего не удалил.
  assert.deepStrictEqual(db.deleted, []);
  assert.ok(db.has(devicePath(UID, "tok-1")));
}

// AC 10.3 на уровне index.js: `index.js` без firebase-admin не поднять, поэтому свойство «сбой
// messaging не роняет операцию и не откатывает её запись» проверяется по исходнику: обёртка
// notifyUser глотает любой сбой, а каждая из трёх точек вызова стоит ПОСЛЕ коммита своей
// операции внутри той же функции. Порядок и есть гарантия: запись верификации / публикации /
// агрегата уже лежит, когда push только собирается уходить.
function testNotifyRunsAfterCommitAndSwallowsFailures() {
  const src = require("fs").readFileSync(__dirname + "/index.js", "utf8");
  const wrapper = src.slice(src.indexOf("async function notifyUser("), src.indexOf("\n}\n", src.indexOf("async function notifyUser(")));
  assert.ok(/try \{[\s\S]*sendPushToUser\([\s\S]*\} catch \(error\) \{[\s\S]*logger\.warn/.test(wrapper), "notifyUser глотает сбой messaging, а не бросает в операцию");
  const sites = [
    {head: "exports.decideVerification = onCall(", event: "VERIFICATION_APPROVED", commit: "runTransaction("},
    {head: "async function publishModelReviewedSubmission(", event: "QUEST_PUBLISHED", commit: ".commit()"},
    {head: "async function aggregateDirtyQuestRatingDoc(", event: "AUTHOR_RATING_CHANGED", commit: "runTransaction("},
  ];
  for (const site of sites) {
    const fnIdx = src.indexOf(site.head);
    assert.ok(fnIdx >= 0, `${site.head} должна существовать`);
    const fnEnd = src.indexOf(site.head.startsWith("exports.") ? "\n});\n" : "\n}\n", fnIdx);
    const callIdx = src.indexOf(`notifyUser(PUSH_EVENTS.${site.event}`, fnIdx);
    assert.ok(callIdx > fnIdx && callIdx < fnEnd, `${site.head} обязана звать notifyUser(${site.event})`);
    const commitIdx = src.lastIndexOf(site.commit, callIdx);
    assert.ok(commitIdx > fnIdx, `${site.head}: push уходит ПОСЛЕ ${site.commit}, а не до него`);
  }
}

// Пуш о рейтинге автора: только нынешнему автору и только когда число выросло. Проверяется по
// исходнику — `index.js` без firebase-admin не поднять. Прежний владелец при переносе рейтинга
// получал «ваша квалификация изменилась» о том, что у него отобрали; автор получал то же самое,
// когда его число падало. Уровня квалификации по этому числу в проекте нет (ADR-0006).
function testTheRatingPushGoesOnlyToTheAuthorAndOnlyOnAGain() {
  const src = require("fs").readFileSync(__dirname + "/index.js", "utf8");
  const start = src.indexOf("function writeRatingQualificationDelta(");
  assert.ok(start > 0, "функция начисления рейтинга должна существовать");
  const body = src.slice(start, src.indexOf("\nfunction ", start + 10));
  const pushes = body.match(/changedUids\.push\([^)]*\)/g) || [];
  assert.deepStrictEqual(pushes, ["changedUids.push(options.targetUid)"], "адресат уведомления один — нынешний автор");
  assert.ok(
    /if \(targetDelta > 0\) changedUids\.push\(options\.targetUid\)/.test(body),
    "уведомление уходит только когда рейтинг вырос, а не при любой ненулевой дельте",
  );
  assert.ok(
    !/previousDelta !== 0\) changedUids\.push/.test(body),
    "прежнему владельцу при переносе рейтинга уведомление не уходит",
  );
  console.log("rating-push-recipients: ok");
}

(async () => {
  testMessageCarriesExactlyThreeFields();
  testMessageRefusesEmptyIdentity();
  testRegistryKeepsEventAndEntityInOneFeatureNamespace();
  await testOneFailingTokenDoesNotCancelTheOthers();
  await testDeadTokenDocumentIsRemovedAndOnlyIt();
  testOnlyTheTwoDeadCodesCountAsDead();
  await testSendNeverThrowsIntoTheCallingOperation();
  await testDetachRemovesExactlyOneDocument();
  await testDetachRefusesOwnUidAndBadInput();
  testNotifyRunsAfterCommitAndSwallowsFailures();
  testTheRatingPushGoesOnlyToTheAuthorAndOnlyOnAGain();
  console.log("push-notifications.test.js: ok");
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
