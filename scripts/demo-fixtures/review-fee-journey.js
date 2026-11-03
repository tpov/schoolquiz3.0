"use strict";

/**
 * Живой прогон платного цикла проверки против demo-backend (история 4.7, AD-10, CAP-4).
 *
 * Доказывает на реальном приёмнике (functions-эмулятор + Firestore/Auth demo-backend):
 *
 *   — плата тарифа при принятии заявки callable-путём (submitMutation → SUBMIT_ARENA), один раз;
 *   — расчёт фонда после обоснованного отклонения: юниты по уровням, десятые, сгоревший остаток,
 *     неизменяемый манифест, строки книги денег;
 *   — перенос carry между циклами одного проверяющего;
 *   — тариф перепроверки на повторной подаче того же квеста;
 *   — идемпотентность: повторы не меняют ни кошельков, ни манифеста;
 *   — незавершённый цикл фонд не делит и не сжигает (OQ-2 не добавлен).
 *
 * Запуск (оба эмулятора должны жить; функции-эмулятор — с env-проводкой):
 *   cd night-impl && FIRESTORE_EMULATOR_HOST=127.0.0.1:18090 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:19090 \
 *   GCLOUD_PROJECT=demo-schoolquiz-mvp node scripts/demo-fixtures/review-fee-journey.js
 *
 * Созданные документы — живые экспонаты прогона (id с меткой времени), их не вычищать.
 */

const assert = require("node:assert/strict");
const path = require("node:path");

const PROJECT = "demo-schoolquiz-mvp";
const HOSTS = {firestore: "127.0.0.1:18090", authPort: 19090, upstream: 5001};
const SUBMIT_MUTATION_PATH = `/${PROJECT}/us-central1/submitMutation`;
const SUBMIT_REVIEW_PATH = `/${PROJECT}/us-central1/submitReviewAction`;
const REJECT_PATH = `/${PROJECT}/us-central1/rejectReviewSubmission`;

assert.equal(process.env.GCLOUD_PROJECT, PROJECT, "GCLOUD_PROJECT must name the demo project");
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, HOSTS.firestore, "firestore must be the demo backend");
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, `127.0.0.1:${HOSTS.authPort}`, "auth must be the demo auth emulator");
assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined, "no credentials: emulator-only run");

const repoRoot = path.join(__dirname, "..", "..");
const admin = require(path.join(repoRoot, "functions", "node_modules", "firebase-admin"));
const {normalizeReviewFeeTariffs} = require(path.join(repoRoot, "functions", "review-fee"));
admin.initializeApp({projectId: PROJECT});
const db = admin.firestore();

const AUTHOR = "Fee4JourneyAuthor000000a1";
const TESTER = "Fee4JourneyTester000000b2";
const JUDGE = "Fee4JourneyJudge0000000c3";
const RUN = `fee-${Date.now()}`;
const QUEST = `${RUN}-quest`;
const LESSON = `${RUN}-lesson`;

async function playerIdToken(uid) {
  const customToken = await admin.auth().createCustomToken(uid);
  const response = await fetch(
    `http://127.0.0.1:${HOSTS.authPort}/identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=demo`,
    {method: "POST", headers: {"content-type": "application/json"}, body: JSON.stringify({token: customToken, returnSecureToken: true})},
  );
  const json = await response.json();
  assert.equal(response.status, 200, "auth emulator must exchange the custom token");
  return json.idToken;
}

async function callable(urlPath, payload, token) {
  const response = await fetch(`http://127.0.0.1:${HOSTS.upstream}${urlPath}`, {
    method: "POST",
    headers: {"content-type": "application/json", authorization: `Bearer ${token}`},
    body: JSON.stringify({data: payload}),
  });
  const json = await response.json();
  return {status: response.status, json};
}

async function tariffs() {
  const snapshot = await db.doc("configs/review_fee").get();
  return normalizeReviewFeeTariffs(snapshot.exists ? snapshot.data() : null);
}

async function ledgerRows(reason) {
  const snapshot = await db.collection("audit/currency_ledger/entries").where("reason", "==", reason).get();
  return snapshot.docs.map((doc) => doc.data()).filter((row) => String(row.sourceId || "").startsWith(RUN));
}

function submissionBody(submissionId) {
  return {
    submissionId,
    draftId: `${RUN}-draft`,
    ownerUid: AUTHOR,
    requestedAtMs: Date.now(),
    targetShelf: "arena",
    targetLessonIds: [LESSON],
    draftTree: {sections: [`${LESSON}-section`], themes: [`${LESSON}-theme`], lessons: [LESSON]},
    draft: {
      id: QUEST,
      catalogId: `${RUN}-catalog`,
      title: `Fee journey quest ${RUN}`,
      description: null,
      defaultLanguage: "ru",
      defaultDifficulty: "EASY",
      publicQuestId: null,
      createdAtMs: Date.now(),
      updatedAtMs: Date.now(),
    },
    sections: [{id: `${LESSON}-section`, draftId: `${RUN}-draft`, title: "Раздел", order: 0}],
    themes: [{id: `${LESSON}-theme`, draftId: `${RUN}-draft`, sectionId: `${LESSON}-section`, title: "Тема", order: 0}],
    lessons: [{id: LESSON, draftId: `${RUN}-draft`, themeId: `${LESSON}-theme`, title: "Fee lesson", order: 0}],
    questions: [{
      id: `${LESSON}-q1`,
      draftId: `${RUN}-draft`,
      lessonId: LESSON,
      type: "SINGLE_CHOICE",
      language: "ru",
      languageLevel: 1,
      difficulty: "EASY",
      order: 0,
      text: "Сколько десятых в нолике?",
      imagePath: null,
      payload: "{}",
      updatedAtMs: Date.now(),
    }],
    review: {
      isTested: false,
      testingScore: null,
      isLogicReviewed: false,
      logicScore: null,
      isTranslationReviewed: false,
      translationScore: null,
      translatedLanguages: {},
    },
  };
}

async function submit(submissionId, uid) {
  const token = await playerIdToken(uid);
  const response = await callable(SUBMIT_MUTATION_PATH, {
    mutationId: `${submissionId}-mutation`,
    operation: "quest_authoring.SUBMIT_ARENA",
    payload: JSON.stringify(submissionBody(submissionId)),
  }, token);
  assert.equal(response.status, 200, `submission must be accepted: ${JSON.stringify(response.json)}`);
  assert.equal(response.json.result.replayed, false);
  // Конверт очереди двоен: {replayed, result} — тело обработчика во внутреннем result.
  return response.json.result.result;
}

async function assignmentOf(submissionId) {
  const tasks = await db.collection("admin/review/lessons").where("submissionId", "==", submissionId).get();
  assert.equal(tasks.size, 1, `one lesson task expected for ${submissionId}`);
  const doc = tasks.docs[0];
  const questions = await doc.ref.collection("quests").doc(doc.data().questId).collection("questions").get();
  return {assignmentId: doc.data().assignmentId, lessonId: doc.data().id, questionIds: questions.docs.map((row) => row.id)};
}

async function testingReview(submissionId, score) {
  const task = await assignmentOf(submissionId);
  const token = await playerIdToken(TESTER);
  const response = await callable(SUBMIT_REVIEW_PATH, {
    assignmentId: task.assignmentId,
    lessonId: task.lessonId,
    kind: "TESTING",
    score,
    questionScores: task.questionIds.map((questionId) => ({questionId, score})),
    language: null,
  }, token);
  assert.equal(response.status, 200, `TESTING must be accepted: ${JSON.stringify(response.json)}`);
}

async function justifiedRejection(submissionId, reason) {
  const task = await assignmentOf(submissionId);
  const token = await playerIdToken(JUDGE);
  const response = await callable(REJECT_PATH, {assignmentId: task.assignmentId, reason}, token);
  assert.equal(response.status, 200, `rejection must be accepted: ${JSON.stringify(response.json)}`);
  return response.json.result;
}

async function balanceOf(uid) {
  const doc = await db.doc(`users/${uid}`).get();
  return doc.exists ? (doc.data().pointsNolics || 0) : 0;
}

async function manifestOf(submissionId) {
  const doc = await db.doc(`review_fee_manifests/${submissionId}`).get();
  return doc.exists ? doc.data() : null;
}

function assertConserved(label, manifest, feeNolics) {
  assert.equal(manifest.feeTenths, feeNolics * 10, `${label}: fee in tenths`);
  assert.equal(
    manifest.sumEntitlementTenths + manifest.burnTenths,
    manifest.feeTenths,
    `${label}: fee = rewards + burn`,
  );
  for (const wallet of manifest.wallets) {
    assert.equal(
      wallet.priorCarryTenths + wallet.entitlementTenths,
      wallet.creditedNolics * 10 + wallet.nextCarryTenths,
      `${label}: carry equation for ${wallet.uid}`,
    );
  }
  console.log(`CONSERVED ${label}: fee=${manifest.feeTenths}t rewards=${manifest.sumEntitlementTenths}t burn=${manifest.burnTenths}t`);
}

async function main() {
  const tariff = await tariffs();
  const initialFee = tariff.initialSubmissionNolics;
  const recheckFee = tariff.recheckQuestionNolics;
  console.log(`TARIFFS revision=${tariff.revision} initial=${initialFee} recheckPerQuestion=${recheckFee}`);

  // ── Участники ──
  await db.doc(`profiles/${AUTHOR}`).set({testerLevel: 0, adminLevel: 0}, {merge: true});
  await db.doc(`users/${AUTHOR}`).set({uid: AUTHOR, pointsNolics: 2000}, {merge: true});
  await db.doc(`profiles/${TESTER}`).set({testerLevel: 105, adminLevel: 0}, {merge: true});
  await db.doc(`users/${TESTER}`).set({uid: TESTER, pointsNolics: 0}, {merge: true});
  await db.doc(`profiles/${JUDGE}`).set({testerLevel: 0, adminLevel: 150}, {merge: true});
  await db.doc(`users/${JUDGE}`).set({uid: JUDGE, pointsNolics: 0}, {merge: true});
  console.log(`SEEDED author=${AUTHOR} (2000) tester=${TESTER} (level 105) judge=${JUDGE} (level 150)`);

  // ── Цикл 1: первоначальная подача, работа, обоснованное отклонение, расчёт ──
  const first = `${RUN}-sub1`;
  const accepted = await submit(first, AUTHOR);
  assert.equal(accepted.reviewFee.tariff, "initial");
  assert.equal(accepted.reviewFee.feeNolics, initialFee);
  assert.equal(await balanceOf(AUTHOR), 2000 - initialFee, "the tariff leaves the wallet at acceptance");
  const chargeRows = await ledgerRows("review_fee");
  assert.equal(chargeRows.filter((row) => row.uid === AUTHOR && row.delta === -initialFee).length, 1,
    "one charge row in the money ledger");
  console.log(`CHARGED ${first}: ${initialFee} nolics, author balance ${await balanceOf(AUTHOR)}`);

  await testingReview(first, 3);
  await justifiedRejection(first, "Justified fee-cycle rejection: paid cycle still pays its reviewers");
  const request1 = await db.collection("quest_review_requests").doc(first).get();
  assert.equal(request1.data().status, "REJECTED");
  const manifest1 = await manifestOf(first);
  assert.ok(manifest1, "the settled cycle has an immutable manifest");
  assert.deepEqual(manifest1.units.map((unit) => [unit.unitId, unit.levelTenths]).sort(), [
    [`${LESSON}/${JUDGE}`, 150],
    [`${LESSON}/${TESTER}`, 105],
  ], "rejection earns its judge at the recorded level; directions merge per reviewer");
  assertConserved("cycle1", manifest1, initialFee);
  const testerWallet1 = manifest1.wallets.find((wallet) => wallet.uid === TESTER);
  assert.equal(testerWallet1.creditedNolics, 10, "level 105 → 10 whole nolics");
  assert.equal(testerWallet1.nextCarryTenths, 5, "five tenths wait in the carry");
  assert.equal(await balanceOf(TESTER), 10);
  assert.equal(await balanceOf(JUDGE), 15);
  assert.equal(await balanceOf(AUTHOR), 2000 - initialFee, "no refund: a justified rejection keeps the fee");
  const rewardRows = await ledgerRows("review_reward");
  assert.equal(rewardRows.filter((row) => row.uid === TESTER && row.delta === 10).length, 1);
  assert.equal(rewardRows.filter((row) => row.uid === JUDGE && row.delta === 15).length, 1);
  console.log(`SETTLED ${first}: tester +10 (carry 5), judge +15, manifest ${manifest1.calculationVersion}`);

  // ── Идемпотентность: повтор отклонения и повтор ревью ничего не меняют ──
  const manifest1Bytes = JSON.stringify(manifest1);
  const beforeReplay = {
    tester: await balanceOf(TESTER),
    judge: await balanceOf(JUDGE),
    author: await balanceOf(AUTHOR),
  };
  await justifiedRejection(first, "Repeat of the same rejection must be a money no-op").catch(() => {});
  await testingReview(first, 3).catch(() => {});
  assert.equal(JSON.stringify(await manifestOf(first)), manifest1Bytes, "manifest is immutable on replays");
  assert.deepEqual({
    tester: await balanceOf(TESTER), judge: await balanceOf(JUDGE), author: await balanceOf(AUTHOR),
  }, beforeReplay, "wallets are untouched by replays");
  console.log("IDEMPOTENT replays changed nothing");

  // ── Цикл 2: перепроверка того же квеста, carry доезжает ──
  const second = `${RUN}-sub2`;
  const accepted2 = await submit(second, AUTHOR);
  assert.equal(accepted2.reviewFee.tariff, "recheck", "a resubmission of a paid quest is a recheck");
  assert.equal(accepted2.reviewFee.feeNolics, recheckFee, "one question → 100×1");
  assert.equal(await balanceOf(AUTHOR), 2000 - initialFee - recheckFee);
  await testingReview(second, 3);
  await justifiedRejection(second, "Second justified rejection for the carry roundtrip");
  const manifest2 = await manifestOf(second);
  assert.ok(manifest2);
  assertConserved("cycle2", manifest2, recheckFee);
  const testerWallet2 = manifest2.wallets.find((wallet) => wallet.uid === TESTER);
  assert.equal(testerWallet2.priorCarryTenths, 5, "the carry from cycle 1 arrives");
  assert.equal(testerWallet2.creditedNolics, 11, "5 + 105 = 110 tenths → 11 whole nolics, carry empty");
  assert.equal(await balanceOf(TESTER), 21);
  console.log(`SETTLED ${second}: carry 5+105 → tester +11 (total ${await balanceOf(TESTER)})`);

  // ── Цикл 3: незавершённый — фонд не делится и не сжигается (OQ-2 не добавлен) ──
  const third = `${RUN}-sub3`;
  await submit(third, AUTHOR);
  const cycle3 = (await db.doc(`review_fee_cycles/${third}`).get()).data();
  assert.equal(cycle3.status, "PAID");
  assert.equal(await manifestOf(third), null, "an unfinished cycle settles nothing");
  const rewardCount = (await ledgerRows("review_reward")).filter((row) => row.sourceId === third).length;
  assert.equal(rewardCount, 0);
  console.log(`UNFINISHED ${third}: fee held, no distribution, no burn (OQ-2 parked)`);

  console.log(`JOURNEY OK run=${RUN} quest=${QUEST} lesson=${LESSON}`);
}

main().catch((error) => {
  console.error("JOURNEY FAILED:", error);
  process.exitCode = 1;
});
