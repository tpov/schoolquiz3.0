"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const Module = require("module");
const {createFirestoreStub} = require("./_firestore-stub");

/**
 * Санитайз квалификации в клиентском пуше профиля (джорни 5.13 v3, 31.10).
 *
 * Прогон на Device Farm доказал: второй SYNC NOW устройства затирал админ-роли
 * (tester/moderator/admin/developer=100 из setup-513) нулями в users-документе —
 * через ~7 с после CREATE турнира, из-за чего пункт шторки «Active events»
 * (Visibility требует TESTER+MODERATOR+ADMIN+DEVELOPER ≥ 100) не появлялся.
 *
 * Механика была такая: ensureUserProfile/updateUserNickname (единственный клиентский
 * транспорт к users-документу — правилами Firestore прямой записи нет) выводили уровни
 * из profiles-документа и перезаливали их merge-ом в users. Бутстрап клал в profiles
 * нули, админ потом поднимал роли в users, и каждый следующий пуш возвращал нули.
 *
 * Эти кейсы держат фикс: пуш квалификацию не пишет вовсе (ни в users, ни в profiles),
 * админ-значения переживают любые пуши, а ответ клиенту показывает фактическое
 * состояние (profiles-уровни с fallback-ом на users-зеркало).
 */

const {admin, store} = createFirestoreStub({});
const adminPath = require.resolve("firebase-admin");
const stub = new Module(adminPath, null);
stub.filename = adminPath; stub.loaded = true; stub.exports = admin;
require.cache[adminPath] = stub;
const functions = require("./index");

const uid = "qual-player";

/** Поля ролей в users-зеркале — то, что читает клиент (FirebaseUserStatsDataSource). */
const USER_ROLE_FIELDS = ["sponsor", "tester", "translater", "moderator", "admin", "developer", "qualifications"];
/** Уровни в доверенном profiles-документе — то, что читают серверные гейты (requireProfile). */
const PROFILE_LEVEL_FIELDS = ["sponsorLevel", "testerLevel", "translatorLevel", "moderatorLevel", "adminLevel", "developerLevel"];

function call(name, data, owner = uid) {
  return functions[name].run({auth: {uid: owner}, data});
}

/** Админ-merge в users-документ — как его делает prep-драйвер (setup-513) через Admin SDK. */
function adminMergeRoles(levels) {
  const user = store.documents.get(`users/${uid}`) || {};
  store.documents.set(`users/${uid}`, {...user, ...levels});
}

test("bootstrap push stamps no qualification zeros into users or profiles", async () => {
  store.reset({});
  const pushed = await call("ensureUserProfile", {});
  assert.equal(pushed.qualification.testerLevel, 0);
  const user = store.documents.get(`users/${uid}`);
  const profile = store.documents.get(`profiles/${uid}`);
  assert.ok(user, "users document must exist after bootstrap");
  assert.ok(profile, "profiles document must exist after bootstrap");
  for (const field of USER_ROLE_FIELDS) {
    assert.ok(!(field in user), `users must not carry ${field} after a client push`);
  }
  for (const field of PROFILE_LEVEL_FIELDS) {
    assert.ok(!(field in profile), `profiles must not carry ${field} after a client push`);
  }
});

test("admin-merged roles in users survive every later client push (5.13 v3 wipe regression)", async () => {
  store.reset({});
  await call("ensureUserProfile", {});
  adminMergeRoles({tester: 100, moderator: 100, admin: 100, developer: 100});

  // Второй синк устройства — тот самый шаг, который раньше затирал роли нулями.
  const synced = await call("ensureUserProfile", {});
  assert.equal(synced.qualification.testerLevel, 100, "response must report the admin-set role");
  assert.equal(synced.qualification.moderatorLevel, 100);
  assert.equal(synced.qualification.adminLevel, 100);
  assert.equal(synced.qualification.developerLevel, 100);
  let user = store.documents.get(`users/${uid}`);
  assert.equal(user.tester, 100, "client push must not wipe the admin tester role");
  assert.equal(user.moderator, 100);
  assert.equal(user.admin, 100);
  assert.equal(user.developer, 100);

  // Смена ника идёт через тот же апсерт — и тоже не должна трогать роли.
  await call("updateUserNickname", {nickname: "QualPlayer2", knownLanguages: ["en"]});
  user = store.documents.get(`users/${uid}`);
  assert.equal(user.tester, 100, "nickname update must keep the admin tester role");
  assert.equal(user.developer, 100);
  const profile = store.documents.get(`profiles/${uid}`);
  for (const field of PROFILE_LEVEL_FIELDS) {
    assert.ok(!(field in profile), `profiles must stay free of ${field} — the users fallback depends on it`);
  }
});

test("admin-set levels in the trusted profile are preserved and reported", async () => {
  const now = Date.now();
  store.reset({
    [`users/${uid}`]: {uid, nickname: "TrustedOne", createdAtMs: now},
    [`profiles/${uid}`]: {uid, testerLevel: 250, adminLevel: 250},
  });
  const pushed = await call("ensureUserProfile", {});
  assert.equal(pushed.qualification.testerLevel, 250);
  assert.equal(pushed.qualification.adminLevel, 250);
  const profile = store.documents.get(`profiles/${uid}`);
  assert.equal(profile.testerLevel, 250, "client push must not touch trusted levels");
  assert.equal(profile.adminLevel, 250);
  const user = store.documents.get(`users/${uid}`);
  for (const field of USER_ROLE_FIELDS) {
    assert.ok(!(field in user), `users must not gain ${field} from a client push`);
  }
});

test("a push carrying role-looking payload fields changes nothing", async () => {
  store.reset({});
  await call("ensureUserProfile", {});
  adminMergeRoles({tester: 100});
  // Клиент прислал в конверте свои «роли» — callable их не читает, но и подорваться не должен.
  const pushed = await call("ensureUserProfile", {
    tester: 999, admin: 999, developerLevel: 999,
    qualifications: {tester: 999, admin: 999},
    knownLanguages: ["en"],
  });
  const user = store.documents.get(`users/${uid}`);
  assert.equal(user.tester, 100, "payload role fields must be ignored, admin value kept");
  assert.ok(!("admin" in user));
  assert.ok(!("qualifications" in user));
  assert.equal(pushed.qualification.testerLevel, 100);
});
