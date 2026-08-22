"use strict";

/**
 * Security-rules tests.
 *
 * These are deliberately NOT written like scripts/tournament-emulator-e2e.js. That harness talks to
 * Firestore through the admin SDK, which ignores security rules entirely — it can prove a callable
 * computes the right number, but it can never prove that the wrong person is kept out. Rules are
 * the real access boundary, so they need contexts that are actually subject to them.
 *
 * Run through the emulator:
 *   firebase emulators:exec --only auth,firestore,functions "node scripts/rules-emulator-test.js"
 */

const fs = require("fs");
const path = require("path");
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");

const PROJECT_ID = process.env.GCLOUD_PROJECT || "demo-schoolquiz";
const RULES_PATH = path.join(__dirname, "..", "firestore.rules");

const QUALIFIED = 100;

/**
 * Every actor the app can present to Firestore, from a guest with no account up to a developer.
 * `profile` is the trusted profiles/{uid} mirror the rules read for privilege checks; actors
 * without one stand for users who have never been granted anything.
 */
const ACTORS = {
  player: {profile: {}},
  author: {profile: {}},
  outsider: {profile: {}},
  tester: {profile: {testerLevel: QUALIFIED}},
  translatorA: {profile: {translatorLevel: QUALIFIED}},
  translatorB: {profile: {translatorLevel: 200}},
  moderator: {profile: {moderatorLevel: QUALIFIED}},
  admin: {profile: {adminLevel: QUALIFIED}},
  developer: {profile: {developerLevel: 101}},
  // Developer at exactly the all-access level. isReviewer() uses `> 100`, not `>= 100`, so this
  // account must NOT be a reviewer. Pins the boundary against a stray >= creeping in.
  developerEdge: {profile: {developerLevel: QUALIFIED}},
  // Signed in anonymously: request.auth is non-null, but no trusted profile exists.
  anon: {profile: null, anonymous: true},
};

let testEnv;
const results = [];

function ctx(name) {
  if (name === "guest") return testEnv.unauthenticatedContext();
  const actor = ACTORS[name];
  if (!actor) throw new Error(`Unknown actor ${name}`);
  const token = actor.anonymous ? {firebase: {sign_in_provider: "anonymous"}} : {};
  return testEnv.authenticatedContext(name, token);
}

function db(name) {
  return ctx(name).firestore();
}

async function check(label, expectation, operation) {
  try {
    await (expectation === "allow" ? assertSucceeds(operation()) : assertFails(operation()));
    results.push({label, ok: true});
  } catch (error) {
    results.push({label, ok: false, error: error && error.message ? error.message : String(error)});
  }
}

const allow = (label, operation) => check(label, "allow", operation);
const deny = (label, operation) => check(label, "deny", operation);

async function seed() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const store = context.firestore();

    for (const [uid, actor] of Object.entries(ACTORS)) {
      await store.doc(`users/${uid}`).set({
        uid,
        nickname: uid,
        gold: 10,
        nolics: 0,
        skillPoints: 0,
        lifePoints: 100,
        lifePointsUpdatedAtMs: 0,
        standardHearts: 5,
        qualifications: {admin: 0, developer: 0, moderator: 0, tester: 0, sponsor: 0, translater: 0},
      });
      if (actor.profile) {
        await store.doc(`profiles/${uid}`).set({
          uid,
          testerLevel: 0,
          translatorLevel: 0,
          moderatorLevel: 0,
          adminLevel: 0,
          developerLevel: 0,
          knownLanguages: ["ru"],
          ...actor.profile,
        });
      }
    }

    await store.doc("quests/published").set({
      id: "published",
      catalogId: "cat-1",
      authorUid: "author",
      title: "Опубликованный",
      description: "",
      picturePath: "",
      visibleOn: ["ru"],
      archived: false,
      version: 3,
      contentsVersion: 2,
      lastModifiedAt: 1,
    });
    await store.doc("quests/draft").set({
      id: "draft",
      catalogId: "cat-1",
      authorUid: "author",
      title: "Черновик",
      description: "",
      picturePath: "",
      visibleOn: [],
      archived: false,
      version: 1,
      contentsVersion: 0,
      lastModifiedAt: 1,
    });

    await store.doc("catalogs/cat-1").set({id: "cat-1", title: "Каталог"});
    await store.doc("catalogs/cat-1/sync_changes/chg-1").set({questId: "published", version: 3});
    await store.doc("sections/sec-1").set({id: "sec-1", questId: "published"});
    await store.doc("themes/thm-1").set({id: "thm-1", sectionId: "sec-1"});
    await store.doc("lessons/les-1").set({id: "les-1", themeId: "thm-1"});
    await store.doc("questions/qst-1").set({id: "qst-1", lessonId: "les-1"});

    await store.doc("admin/review/lessons/les-1").set({id: "les-1"});
    await store.doc("private/author/catalogs/cat-1").set({id: "cat-1"});
    await store.doc("quest_review_requests/req-1").set({ownerUid: "author", processed: false});
    await store.doc("tournaments/tournament").set({id: "tournament"});
    await store.doc("tournaments/tournament/groups/g-1").set({id: "g-1"});
    await store.doc("nickname_claims/claim-1").set({uid: "author"});
    await store.doc("configs/nickname_policy").set({minLength: 3});
  });
}

/** users/{uid} — the balance sheet. Server-owned; the client only ever reads it. */
async function testUsers() {
  await allow("users: владелец читает свой документ", () => db("player").doc("users/player").get());
  await deny("users: игрок читает чужой документ", () => db("player").doc("users/author").get());
  await deny("users: гость читает чужой документ", () => db("guest").doc("users/author").get());
  await deny("users: аноним читает чужой документ", () => db("anon").doc("users/author").get());

  await deny("users: владелец правит qualifications", () =>
    db("player").doc("users/player").update({qualifications: {admin: 100}}));
  await deny("users: владелец правит admin", () =>
    db("player").doc("users/player").update({admin: 100}));
  await deny("users: владелец правит nickname", () =>
    db("player").doc("users/player").update({nickname: "новый"}));

  // Currency and the activity budget are settled by Cloud Functions. If the client can write them
  // directly, the whole server-side accounting — score verification included — is decorative.
  await deny("users: владелец правит lifePoints", () =>
    db("player").doc("users/player").update({lifePoints: 999999}));
  await deny("users: владелец правит gold", () =>
    db("player").doc("users/player").update({gold: 999999}));
  await deny("users: владелец правит skillPoints", () =>
    db("player").doc("users/player").update({skillPoints: 999999}));
  await deny("users: владелец правит standardHearts", () =>
    db("player").doc("users/player").update({standardHearts: 99}));
  await deny("users: владелец заводит произвольное поле", () =>
    db("player").doc("users/player").update({trophies: 1000}));
}

/** profiles/{uid} — the trusted privilege mirror used for review routing. */
async function testProfiles() {
  await allow("profiles: владелец читает свой", () => db("player").doc("profiles/player").get());
  await deny("profiles: игрок читает чужой", () => db("player").doc("profiles/author").get());
  await allow("profiles: тестер читает чужой", () => db("tester").doc("profiles/author").get());
  await allow("profiles: админ читает чужой", () => db("admin").doc("profiles/author").get());
  await deny("profiles: модератор читает чужой", () => db("moderator").doc("profiles/author").get());
  await deny("profiles: владелец пишет свой", () =>
    db("player").doc("profiles/player").update({testerLevel: 100}));
  await deny("profiles: админ пишет чужой", () =>
    db("admin").doc("profiles/author").update({testerLevel: 100}));
}

const NEW_QUEST = {
  id: "fresh",
  catalogId: "cat-1",
  authorUid: "author",
  title: "Новый",
  description: "",
  picturePath: "",
  visibleOn: [],
  archived: false,
  version: 1,
  contentsVersion: 0,
  lastModifiedAt: 1,
};

async function testQuests() {
  await allow("quests: гость читает опубликованный", () => db("guest").doc("quests/published").get());
  await allow("quests: аноним читает опубликованный", () => db("anon").doc("quests/published").get());
  await allow("quests: автор читает свой черновик", () => db("author").doc("quests/draft").get());
  await deny("quests: чужой читает черновик", () => db("outsider").doc("quests/draft").get());

  await allow("quests: автор создаёт свой квест", () =>
    db("author").doc("quests/fresh").set(NEW_QUEST));
  await deny("quests: создание от чужого имени", () =>
    db("outsider").doc("quests/forged").set(NEW_QUEST));
  await deny("quests: создание с рейтингом", () =>
    db("author").doc("quests/rated").set({...NEW_QUEST, id: "rated", averageRating: 5}));
  await deny("quests: создание с version != 1", () =>
    db("author").doc("quests/v9").set({...NEW_QUEST, id: "v9", version: 9}));
  await deny("quests: создание с посторонним полем", () =>
    db("author").doc("quests/extra").set({...NEW_QUEST, id: "extra", isFeatured: true}));

  await allow("quests: автор правит заголовок", () =>
    db("author").doc("quests/published").update({title: "Другой"}));
  await deny("quests: чужой правит квест", () =>
    db("outsider").doc("quests/published").update({title: "Захвачено"}));
  await deny("quests: автор подменяет authorUid", () =>
    db("author").doc("quests/published").update({authorUid: "outsider"}));
  await deny("quests: автор правит version", () =>
    db("author").doc("quests/published").update({version: 99}));
  await deny("quests: автор правит contentsVersion", () =>
    db("author").doc("quests/published").update({contentsVersion: 99}));
  await deny("quests: автор правит рейтинг", () =>
    db("author").doc("quests/published").update({averageRating: 5}));

  await deny("quests: автор удаляет свой квест", () => db("author").doc("quests/draft").delete());
  await allow("quests: админ удаляет квест", () => db("admin").doc("quests/draft").delete());
}

/**
 * Nested content is world-readable on purpose (Option C in docs/03-decisions.md): guarding it would
 * cost a parent get() on every read. These assertions record that as a decision, so a later reader
 * does not mistake it for an oversight and "fix" it without measuring the cost.
 */
async function testNestedContent() {
  for (const [collection, id] of [
    ["sections", "sec-1"], ["themes", "thm-1"], ["lessons", "les-1"], ["questions", "qst-1"],
  ]) {
    await allow(`${collection}: чтение открыто всем (осознанный компромисс MVP)`, () =>
      db("guest").doc(`${collection}/${id}`).get());
    await deny(`${collection}: игрок не пишет`, () =>
      db("player").doc(`${collection}/${id}`).update({hacked: true}));
    await allow(`${collection}: админ пишет`, () =>
      db("admin").doc(`${collection}/${id}`).update({touched: true}));
  }
}

async function testReviewHierarchy() {
  await allow("admin/**: тестер читает", () => db("tester").doc("admin/review/lessons/les-1").get());
  await allow("admin/**: переводчик читает", () =>
    db("translatorA").doc("admin/review/lessons/les-1").get());
  await allow("admin/**: разработчик 101 читает", () =>
    db("developer").doc("admin/review/lessons/les-1").get());
  await deny("admin/**: разработчик ровно 100 не читает", () =>
    db("developerEdge").doc("admin/review/lessons/les-1").get());
  await deny("admin/**: модератор не читает", () =>
    db("moderator").doc("admin/review/lessons/les-1").get());
  await deny("admin/**: игрок не читает", () => db("player").doc("admin/review/lessons/les-1").get());
  await deny("admin/**: аноним не читает", () => db("anon").doc("admin/review/lessons/les-1").get());
  await deny("admin/**: админ не пишет", () =>
    db("admin").doc("admin/review/lessons/les-1").update({hacked: true}));

  await allow("private/**: владелец читает своё", () =>
    db("author").doc("private/author/catalogs/cat-1").get());
  await allow("private/**: ревьюер читает чужое", () =>
    db("tester").doc("private/author/catalogs/cat-1").get());
  await deny("private/**: чужой не читает", () =>
    db("outsider").doc("private/author/catalogs/cat-1").get());
  await deny("private/**: владелец не пишет", () =>
    db("author").doc("private/author/catalogs/cat-1").update({hacked: true}));
}

async function testReviewRequests() {
  await allow("review_requests: автор создаёт свою заявку", () =>
    db("author").doc("quest_review_requests/req-mine").set({ownerUid: "author", processed: false}));
  await deny("review_requests: заявка от чужого имени", () =>
    db("outsider").doc("quest_review_requests/req-forged").set({ownerUid: "author", processed: false}));
  await deny("review_requests: заявка сразу обработанной", () =>
    db("author").doc("quest_review_requests/req-done").set({ownerUid: "author", processed: true}));

  await allow("review_requests: автор читает свою", () =>
    db("author").doc("quest_review_requests/req-1").get());
  await allow("review_requests: ревьюер читает чужую", () =>
    db("tester").doc("quest_review_requests/req-1").get());
  await deny("review_requests: чужой не читает", () =>
    db("outsider").doc("quest_review_requests/req-1").get());
  await deny("review_requests: автор не правит свою", () =>
    db("author").doc("quest_review_requests/req-1").update({processed: true}));
  await deny("review_requests: автор не удаляет свою", () =>
    db("author").doc("quest_review_requests/req-1").delete());
}

async function testCatalogsAndTournaments() {
  await allow("catalogs: читает даже гость", () => db("guest").doc("catalogs/cat-1").get());
  await deny("catalogs: игрок не пишет", () =>
    db("player").doc("catalogs/cat-1").update({title: "Захвачено"}));
  await allow("catalogs: админ пишет", () =>
    db("admin").doc("catalogs/cat-1").update({title: "Обновлён"}));
  await allow("catalogs: sync_changes читает гость", () =>
    db("guest").doc("catalogs/cat-1/sync_changes/chg-1").get());
  await deny("catalogs: sync_changes игрок не пишет", () =>
    db("player").doc("catalogs/cat-1/sync_changes/chg-2").set({questId: "x", version: 1}));

  await allow("tournaments: вошедший читает", () => db("player").doc("tournaments/tournament").get());
  await allow("tournaments: вложенное читает вошедший", () =>
    db("player").doc("tournaments/tournament/groups/g-1").get());
  await deny("tournaments: гость не читает", () => db("guest").doc("tournaments/tournament").get());
  await deny("tournaments: игрок не пишет", () =>
    db("player").doc("tournaments/tournament").update({hacked: true}));
}

async function testServerOnlyCollections() {
  await deny("nickname_claims: админ не читает", () => db("admin").doc("nickname_claims/claim-1").get());
  await deny("nickname_claims: игрок не пишет", () =>
    db("player").doc("nickname_claims/claim-2").set({uid: "player"}));
  await deny("configs: админ не читает", () => db("admin").doc("configs/nickname_policy").get());
  await deny("configs: разработчик не пишет", () =>
    db("developer").doc("configs/nickname_policy").update({minLength: 1}));
}

async function main() {
  const host = process.env.FIRESTORE_EMULATOR_HOST;
  if (!host) {
    throw new Error("FIRESTORE_EMULATOR_HOST is required. Run through firebase emulators:exec.");
  }
  const [hostname, port] = host.split(":");

  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {rules: fs.readFileSync(RULES_PATH, "utf8"), host: hostname, port: Number(port)},
  });

  await testEnv.clearFirestore();
  await seed();

  await testUsers();
  await testProfiles();
  await testQuests();
  await testNestedContent();
  await testReviewHierarchy();
  await testReviewRequests();
  await testCatalogsAndTournaments();
  await testServerOnlyCollections();

  await testEnv.cleanup();

  const failed = results.filter((entry) => !entry.ok);
  for (const entry of failed) {
    console.error(`  ✗ ${entry.label}`);
  }
  console.log(`rules: ${results.length - failed.length}/${results.length} проверок прошло`);
  if (failed.length > 0) {
    throw new Error(`${failed.length} проверок правил не прошло`);
  }
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error && error.stack ? error.stack : error);
    process.exit(1);
  });
