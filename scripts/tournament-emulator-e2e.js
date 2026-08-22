"use strict";

const assert = require("assert");
const admin = require("../functions/node_modules/firebase-admin");
const callableHandlers = require("../functions/index.js");

const PROJECT_ID = process.env.GCLOUD_PROJECT || process.env.GCLOUD_PROJECT_ID || "demo-schoolquiz";
const AUTH_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST || "127.0.0.1:9099";
const FUNCTIONS_HOST = process.env.FUNCTIONS_EMULATOR_HOST || "127.0.0.1:5001";
const FIRESTORE_HOST = process.env.FIRESTORE_EMULATOR_HOST;
const USE_HTTP_CALLABLE = process.env.TOURNAMENT_E2E_HTTP === "true";

if (!FIRESTORE_HOST) {
  throw new Error("FIRESTORE_EMULATOR_HOST is required. Run through firebase emulators:exec.");
}

if (admin.apps.length === 0) {
  admin.initializeApp({projectId: PROJECT_ID});
}

const db = admin.firestore();
let localUidCounter = 0;

function userLabel(index) {
  return `P${String(index).padStart(2, "0")}`;
}

async function signUpAnonymous() {
  if (!USE_HTTP_CALLABLE) {
    localUidCounter += 1;
    const uid = `localTournamentUser${String(localUidCounter).padStart(2, "0")}`;
    return {uid, token: uid};
  }

  const url = `http://${AUTH_HOST}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key`;
  const response = await fetch(url, {
    method: "POST",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify({returnSecureToken: true}),
  });
  const json = await response.json();
  assert.strictEqual(response.ok, true, `Auth signUp failed: ${JSON.stringify(json)}`);
  assert.ok(json.localId, "Auth signUp did not return localId");
  assert.ok(json.idToken, "Auth signUp did not return idToken");
  return {uid: json.localId, token: json.idToken};
}

async function callFunction(name, token, data) {
  if (!USE_HTTP_CALLABLE) {
    const handler = callableHandlers[name];
    assert.ok(handler && typeof handler.run === "function", `Missing callable handler ${name}`);
    return handler.run({
      auth: {
        uid: token,
        token: {
          firebase: {
            sign_in_provider: "anonymous",
          },
        },
      },
      data,
    });
  }

  const url = `http://${FUNCTIONS_HOST}/${PROJECT_ID}/us-central1/${name}`;
  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({data}),
  });
  const json = await response.json();
  if (!response.ok || json.error) {
    throw new Error(`${name} failed: status=${response.status} body=${JSON.stringify(json)}`);
  }
  return json.result;
}

async function assertFunctionFails(name, token, data, code) {
  if (!USE_HTTP_CALLABLE) {
    const handler = callableHandlers[name];
    assert.ok(handler && typeof handler.run === "function", `Missing callable handler ${name}`);
    try {
      await handler.run({
        auth: token
          ? {
            uid: token,
            token: {
              firebase: {
                sign_in_provider: "anonymous",
              },
            },
          }
          : undefined,
        data,
      });
    } catch (error) {
      const actualCode = String(error && error.code || "").toUpperCase().replace(/-/g, "_");
      assert.strictEqual(actualCode, code, `${name} returned wrong error: ${error && error.stack || error}`);
      return;
    }
    throw new Error(`${name} unexpectedly succeeded`);
  }

  const url = `http://${FUNCTIONS_HOST}/${PROJECT_ID}/us-central1/${name}`;
  const headers = {"Content-Type": "application/json"};
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await fetch(url, {
    method: "POST",
    headers,
    body: JSON.stringify({data}),
  });
  const json = await response.json();
  assert.ok(json.error, `${name} unexpectedly succeeded: ${JSON.stringify(json)}`);
  assert.strictEqual(json.error.status, code, `${name} returned wrong error: ${JSON.stringify(json)}`);
}

/**
 * Builds a codeAnswer whose server-side recomputation equals `percent` exactly.
 *
 * The server now pays out only when percentScore agrees with what codeAnswer implies, so a fixture
 * that sends a bare percent stopped being a realistic client — a real one always derives one from
 * the other. A hundred digits keeps the arithmetic exact: each 9 contributes 100 and each 1
 * contributes 0, so floor(100k/100) lands on k for any whole percent.
 */
function codeAnswerForPercent(percent) {
  const whole = wholePercent(percent);
  return "9".repeat(whole) + "1".repeat(100 - whole);
}

function wholePercent(percent) {
  return Math.max(0, Math.min(100, Math.round(percent)));
}

function attempt({
  uid,
  attemptId,
  sourceShelf,
  lessonId,
  percent,
  completedAtMs,
  difficulty = "EASY",
}) {
  return {
    userId: uid,
    attemptId,
    scope: "public",
    catalogId: "codex-school",
    questId: `${sourceShelf}-quest`,
    sectionId: `${sourceShelf}-section`,
    themeId: `${sourceShelf}-theme`,
    lessonId,
    lessonVersion: 1,
    sourceShelf,
    difficulty,
    codeAnswer: codeAnswerForPercent(percent),
    percentScore: wholePercent(percent),
    completedAtMs,
    createdAtMs: completedAtMs - 1000,
  };
}

function qualifierPercent(playerIndex, round) {
  return Math.min(100, Math.max(0, 32 + playerIndex * 1.8 + round * 0.35 + (playerIndex % 3) * 0.2));
}

function finalPercent(rankIndex, round) {
  return Math.min(100, Math.max(0, 95 - rankIndex * 4.5 + round * 0.25));
}

async function createProfiles(count) {
  const users = [];
  for (let index = 0; index < count; index += 1) {
    const auth = await signUpAnonymous();
    if (USE_HTTP_CALLABLE) {
      await callFunction("ensureUserProfile", auth.token, {});
    }
    await db.collection("users").doc(auth.uid).set({nickname: userLabel(index)}, {merge: true});
    await db.collection("profiles").doc(auth.uid).set({
      uid: auth.uid,
      nickname: userLabel(index),
      developerLevel: 0,
      testerLevel: 0,
      translatorLevel: 0,
      moderatorLevel: 0,
      adminLevel: 0,
      knownLanguages: [],
    }, {merge: true});
    users.push({...auth, label: userLabel(index), index});
  }
  return users;
}

async function submitQualifier(users) {
  const base = Date.parse("2026-05-07T08:00:00.000Z");
  for (let round = 0; round < 7; round += 1) {
    const completedAtMs = base + round * 60 * 60 * 1000 + 5 * 60 * 1000;
    const lessonId = `qualifier-lesson-${round}`;
    for (const user of users) {
      const result = await callFunction("submitLessonResultEvents", user.token, {
        attempts: [
          attempt({
            uid: user.uid,
            attemptId: `qualifier-${round}-${user.uid}`,
            sourceShelf: "tournament",
            lessonId,
            percent: qualifierPercent(user.index, round),
            completedAtMs,
          }),
        ],
      });
      assert.strictEqual(result.accepted, 1, "qualifier attempt should be accepted");
      assert.ok(result.reward.skillPoints > 0, "qualifier attempt should give skill points once");
    }
  }
}

async function submitWorldFinal(finalists) {
  const base = Date.parse("2026-05-08T12:00:00.000Z");
  for (let round = 0; round < 3; round += 1) {
    const completedAtMs = base + round * 60 * 60 * 1000 + 10 * 60 * 1000;
    const lessonId = `world-hard-lesson-${round}`;
    for (const [rankIndex, user] of finalists.entries()) {
      const result = await callFunction("submitLessonResultEvents", user.token, {
        attempts: [
          attempt({
            uid: user.uid,
            attemptId: `world-${round}-${user.uid}`,
            sourceShelf: "tournamentFinal",
            lessonId,
            percent: finalPercent(rankIndex, round),
            completedAtMs,
            difficulty: "HARD",
          }),
        ],
      });
      assert.strictEqual(result.accepted, 1, "world attempt should be accepted");
      assert.ok(result.reward.skillPoints > 0, "world attempt should give skill points once");
    }
  }
}

async function assertQualifierOverview(users) {
  const overview = await callFunction("fetchTournamentOverview", users[31].token, {
    tournamentId: "tournament",
    limit: 40,
  });
  assert.strictEqual(overview.tournament.title, "Отборочный турнир");
  assert.strictEqual(overview.tournament.stageLabel, "Лёгкие вопросы");
  assert.strictEqual(overview.metadata.playersCount, 32);
  assert.strictEqual(overview.metadata.countedGroups, 7);
  assert.strictEqual(overview.metadata.isFullyConnected, true);
  assert.strictEqual(overview.leaderboard.length, 32);
  assert.strictEqual(overview.leaderboard[0].nickname, "P31");
  assert.strictEqual(overview.leaderboard[0].place, 1);
  assert.strictEqual(overview.leaderboard[31].nickname, "P00");
  assert.strictEqual(overview.currentUserEntry.nickname, "P31");
  assert.strictEqual(overview.currentUserParticipant.nickname, "P31");
  assert.strictEqual(overview.currentUserParticipant.attemptCount, 7);
  assert.ok(overview.leaderboard[0].ratingPercent > overview.leaderboard[1].ratingPercent);
  assert.ok(overview.leaderboard[0].confidence >= 0.95);
  return overview;
}

async function assertDuplicateAttemptDoesNotIncrement(user) {
  const before = await callFunction("fetchTournamentOverview", user.token, {
    tournamentId: "tournament",
    limit: 40,
  });
  const beforeParticipant = before.participants.find((item) => item.userId === user.uid);
  assert.strictEqual(beforeParticipant.attemptCount, 7);

  const completedAtMs = Date.parse("2026-05-07T08:05:00.000Z");
  const duplicate = await callFunction("submitLessonResultEvents", user.token, {
    attempts: [
      attempt({
        uid: user.uid,
        attemptId: `qualifier-0-${user.uid}`,
        sourceShelf: "tournament",
        lessonId: "qualifier-lesson-0",
        percent: 100,
        completedAtMs,
      }),
    ],
  });
  assert.strictEqual(duplicate.accepted, 1);
  assert.deepStrictEqual(duplicate.reward, {skillPoints: 0, nolics: 0});

  const after = await callFunction("fetchTournamentOverview", user.token, {
    tournamentId: "tournament",
    limit: 40,
  });
  const afterParticipant = after.participants.find((item) => item.userId === user.uid);
  assert.strictEqual(afterParticipant.attemptCount, 7);
}

async function assertArenaAttemptIsNotTournament(user) {
  const before = await callFunction("fetchTournamentOverview", user.token, {
    tournamentId: "tournament",
    limit: 40,
  });
  const completedAtMs = Date.parse("2026-05-07T20:05:00.000Z");
  await callFunction("submitLessonResultEvents", user.token, {
    attempts: [
      attempt({
        uid: user.uid,
        attemptId: `arena-${user.uid}`,
        sourceShelf: "arena",
        lessonId: "arena-lesson",
        percent: 100,
        completedAtMs,
      }),
    ],
  });
  const after = await callFunction("fetchTournamentOverview", user.token, {
    tournamentId: "tournament",
    limit: 40,
  });
  assert.strictEqual(after.metadata.countedGroups, before.metadata.countedGroups);
  assert.strictEqual(after.metadata.playersCount, before.metadata.playersCount);
}

async function assertWorldOverview(finalists) {
  const overview = await callFunction("fetchTournamentOverview", finalists[0].token, {
    tournamentId: "tournamentFinal",
    limit: 10,
  });
  assert.strictEqual(overview.tournament.title, "Чемпионат мира");
  assert.strictEqual(overview.tournament.stageLabel, "Сложные вопросы");
  assert.strictEqual(overview.metadata.playersCount, 4);
  assert.strictEqual(overview.metadata.countedGroups, 3);
  assert.strictEqual(overview.metadata.isFullyConnected, true);
  assert.deepStrictEqual(overview.leaderboard.map((item) => item.nickname), ["P31", "P30", "P29", "P28"]);
  assert.strictEqual(overview.currentUserEntry.nickname, "P31");
  assert.strictEqual(overview.currentUserParticipant.attemptCount, 3);
}

async function assertPermissions(users) {
  await assertFunctionFails("fetchTournamentOverview", null, {tournamentId: "tournament"}, "UNAUTHENTICATED");
  await assertFunctionFails("recalculateTournamentLeaderboard", users[0].token, {tournamentId: "tournament"}, "PERMISSION_DENIED");
  await db.collection("profiles").doc(users[31].uid).set({developerLevel: 101}, {merge: true});
  const result = await callFunction("recalculateTournamentLeaderboard", users[31].token, {
    tournamentId: "tournament",
  });
  assert.strictEqual(result.tournamentId, "tournament");
  assert.strictEqual(result.playersCount, 32);
}

async function assertStoredGroups() {
  const qualifierGroups = await db.collection("tournaments").doc("tournament").collection("groups").get();
  assert.strictEqual(qualifierGroups.size, 7);
  for (const group of qualifierGroups.docs) {
    const results = await group.ref.collection("results").get();
    assert.strictEqual(results.size, 32, `group ${group.id} should have 32 results`);
  }

  const finalGroups = await db.collection("tournaments").doc("tournamentFinal").collection("groups").get();
  assert.strictEqual(finalGroups.size, 3);
  for (const group of finalGroups.docs) {
    const results = await group.ref.collection("results").get();
    assert.strictEqual(results.size, 4, `final group ${group.id} should have 4 results`);
  }
}

async function main() {
  const users = await createProfiles(32);
  await submitQualifier(users);
  const qualifierOverview = await assertQualifierOverview(users);
  const finalists = qualifierOverview.leaderboard.slice(0, 4)
    .map((entry) => users.find((user) => user.uid === entry.userId));
  await assertDuplicateAttemptDoesNotIncrement(users[31]);
  await assertArenaAttemptIsNotTournament(users[0]);
  await submitWorldFinal(finalists);
  await assertWorldOverview(finalists);
  await assertPermissions(users);
  await assertStoredGroups();
  console.log("tournament emulator e2e passed");
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error && error.stack ? error.stack : error);
    process.exit(1);
  });
