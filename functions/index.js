"use strict";

const admin = require("firebase-admin");
const crypto = require("crypto");
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {onDocumentCreated} = require("firebase-functions/v2/firestore");
const {onSchedule} = require("firebase-functions/v2/scheduler");
const {trophyList, pickGiftBoxTrophy, hasTrophy, TROPHY_VERIFIED} = require("./trophies.js");
const {
  VerificationError,
  DECISION_APPROVED,
  normalizeVerificationDetails,
  canSubmitRequest,
  normalizeDecision,
} = require("./verification.js");
const {
  calculateTournamentLeaderboard,
  tournamentGroupForAttempt,
  tournamentResultForAttempt,
} = require("./tournament-ranking");
const {
  recomputePercentScore,
  isWellFormedCodeAnswer,
} = require("./result-verification");
const {
  LESSON_ATTEMPT_LIFE_COST,
  maxLifePoints,
  regenerateLifePoints,
  spendLifePoints,
} = require("./life-points");

admin.initializeApp();
const db = admin.firestore();
db.settings({ignoreUndefinedProperties: true});

const REGION = "us-central1";
const FUNCTION_OPTIONS = {
  region: REGION,
  labels: {"schoolquiz-runtime": "node22"},
};
const QUALIFIED_LEVEL = 100;
const DEVELOPER_ALL_ACCESS_LEVEL = 100;
const TRANSLATION_REVIEW_LEVEL_GAP = 100;
const ACTIVE_LEVEL_WINDOW = 100;
const ACCEPTED_TRANSLATION_SEGMENT_POINTS = 5;
const REJECTED_TRANSLATION_SEGMENT_POINTS = -1;
const REVIEW_PASS_LEVEL = 100;
const DAY_MS = 24 * 60 * 60 * 1000;
const PUBLIC_SCOPE = "public";
const PRIVATE_SCOPE = "private";
const MAX_RESULT_EVENTS_PER_CALL = 50;
const DIRTY_RATING_READ_BATCH = 400;
const DAILY_DIRTY_RATING_LIMIT = 5000;
const HARD_RESULT_REWARD_MULTIPLIER = 2;
const NOLICS_PERCENT_STEP = 10;
const QUEST_RATING_QUALIFICATION_USER_FIELD = "developer";
const QUEST_RATING_QUALIFICATION_PROFILE_FIELD = "developerLevel";
const QUEST_RATING_QUALIFICATION_POINTS_PER_STAR = 10;
const SHOP_ITEM_STANDARD_HEART_SLOT = "STANDARD_HEART_SLOT";
const SHOP_ITEM_GOLD_HEART = "GOLD_HEART";
const STANDARD_HEART_SLOT_COSTS = [1000, 2000, 5000, 10000, 20000];
const MAX_STANDARD_HEARTS = 5;
const MAX_GOLD_HEARTS = 1;
const GOLD_HEART_COST = 10;
const GIFT_BOX_STREAK_TARGET_DAYS = 10;
const MAX_TOURNAMENT_GROUPS_PER_RECALC = 1000;
const MAX_TOURNAMENT_RESULTS_PER_GROUP = 1000;
const TOURNAMENT_LEADERBOARD_LIMIT = 100;
const TOURNAMENT_PARTICIPANT_LIMIT = 500;
const GIFT_BOX_LOGO_NAMES = [
  "Golden Crown Logo",
  "Diamond Star Logo",
  "Phoenix Wings Logo",
  "Dragon Scale Logo",
  "Crystal Orb Logo",
  "Thunder Bolt Logo",
  "Mystic Eye Logo",
  "Royal Shield Logo",
];
const NICKNAME_CLAIMS_COLLECTION = "nickname_claims";
const NICKNAME_POLICY_DOC = "configs/nickname_policy";
const NICKNAME_GENERATION_ATTEMPTS = 20;
const PUBLIC_QUEST_SHELVES = new Set(["home", "arena", "tournament", "tournamentFinal"]);

exports.processQuestReviewRequest = onDocumentCreated(
  {...FUNCTION_OPTIONS, document: "quest_review_requests/{submissionId}"},
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;
    const request = snapshot.data() || {};
    if (request.processed === true) return;

    try {
      await processArenaRequest(normalizeRequest(request, snapshot.id));
    } catch (error) {
      await snapshot.ref.set(
        {
          processed: false,
          lastError: errorMessage(error),
          failedAtMs: Date.now(),
        },
        {merge: true},
      );
      throw error;
    }
  },
);

exports.processPendingArenaRequests = onCall(FUNCTION_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const profile = await requireProfile(uid);
  if (profile.developerLevel <= DEVELOPER_ALL_ACCESS_LEVEL) {
    throw new HttpsError("permission-denied", "Developer level is required");
  }
  const limit = Math.max(1, Math.min(numberValue(request.data && request.data.limit, 20), 100));
  const pending = await db
    .collection("quest_review_requests")
    .where("processed", "==", false)
    .limit(limit)
    .get();
  const results = [];
  for (const doc of pending.docs) {
    results.push(await processArenaRequest(normalizeRequest(doc.data(), doc.id)));
  }
  return {processed: results};
});

exports.fetchReviewAssignmentChanges = onCall(FUNCTION_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  await requireProfile(uid);
  const cursorMs = numberValue(request.data && request.data.cursorMs, 0);
  const snapshot = await db
    .collection("admin/review/sync_changes")
    .where("changedAtMs", ">", cursorMs)
    .get();
  const changes = snapshot.docs
    .map((doc) => {
      const data = doc.data() || {};
      const assignmentId = stringValue(data.assignmentId || data.id || doc.id);
      const lessonId = stringValue(data.lessonId);
      if (!assignmentId || !lessonId) return null;
      return {
        id: assignmentId,
        changedAtMs: numberValue(data.changedAtMs, 0),
      };
    })
    .filter(Boolean)
    .sort((a, b) => a.changedAtMs - b.changedAtMs);
  return {changes};
});

exports.fetchReviewAssignments = onCall(FUNCTION_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const profile = await requireProfile(uid);
  const ids = stringArray(request.data && request.data.ids);
  if (ids.length === 0) return {assignments: []};

  const config = await readArenaReviewConfig();
  const tasks = await readAdminReviewLessonTasksByIds(new Set(ids));
  const assignments = [];
  for (const task of tasks) {
    const assignment = toAssignmentDto(task, profile, config);
    if (assignment) assignments.push(assignment);
  }
  return {assignments};
});

exports.submitReviewAction = onCall(FUNCTION_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const action = normalizeSubmitAction(request.data || {});
  const profile = await requireProfile(uid);
  const task = await readAdminReviewLessonTask(action.assignmentId);
  if (!task) {
    throw new HttpsError("not-found", `Review assignment ${action.assignmentId} not found`);
  }
  if (task.lessonId !== action.lessonId) {
    throw new HttpsError(
      "invalid-argument",
      `Action lessonId ${action.lessonId} does not match assignment lessonId ${task.lessonId}`,
    );
  }

  const config = await readArenaReviewConfig();
  const existingRecords = await readReviewRecords(action.lessonId);
  if (!canSubmit(profile, task, action.kind, config, existingRecords)) {
    throw new HttpsError(
      "permission-denied",
      `Reviewer ${profile.uid} is not allowed to submit ${action.kind} for ${action.assignmentId}`,
    );
  }

  const now = Date.now();
  const record = actionToRecord(action, profile, task, config, existingRecords, now);
  const taskWithTranslatedQuestions =
    record.kind === "TRANSLATION"
      ? {...task, questions: mergeQuestions(task.questions, record.translatedQuestions)}
      : task;
  const aggregation = rebuildAggregate(taskWithTranslatedQuestions, existingRecords.concat(record), config);
  const previousAggregation = rebuildAggregate(task, existingRecords, config);
  const updatedTask = {
    ...taskWithTranslatedQuestions,
    checks: aggregation.checks,
    status: lessonReviewStatus(aggregation.checks, taskWithTranslatedQuestions, config),
    changedAtMs: now,
  };
  const reviewerDeltas = newReviewerDeltas(
    previousAggregation.reviewerDeltas,
    aggregation.reviewerDeltas,
  ).concat(translationReviewerDeltas(record, existingRecords));

  const batch = db.batch();
  batch.set(
    db.doc(adminReviewRecordPath(record.lessonId, record.id)),
    reviewRecordToDocument(record),
    {merge: true},
  );
  writeAdminReviewLessonTasksToBatch(batch, [updatedTask]);
  await batch.commit();

  for (const delta of reviewerDeltas) {
    if (delta.points !== 0) await addReviewerReputation(delta.reviewerUid, delta.points);
  }
  const published = await publishSubmissionIfReady(updatedTask.submissionId, config, now);

  return {
    recordId: record.id,
    aggregate: checksToCallableMap(aggregation.checks),
    reviewerDeltas,
    published,
  };
});

exports.reconcileQuestReviewDaily = onSchedule(
  {...FUNCTION_OPTIONS, schedule: "every day 03:00", timeZone: "UTC"},
  async () => {
    const now = Date.now();
    await reconcileChangedReviewLessons(now - DAY_MS, now);
  },
);

exports.submitLessonResultEvents = onCall(FUNCTION_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  await requireProfile(uid);
  const attempts = listMaps(request.data && request.data.attempts);
  if (attempts.length > MAX_RESULT_EVENTS_PER_CALL) {
    throw new HttpsError("invalid-argument", `At most ${MAX_RESULT_EVENTS_PER_CALL} attempts are accepted per call`);
  }
  if (attempts.length === 0) return {accepted: 0};

  const now = Date.now();
  const events = attempts.map((item) => {
    const event = normalizeLessonResultAttemptEvent(item, uid);
    const content = contentKeysForEvent(event);
    const ref = db
      .collection(scopedCollection("result_events", event.scope))
      .doc(eventBucketId(event.attemptId))
      .collection("events")
      .doc(safeDocId(event.attemptId));
    return {event, content, ref};
  });
  let reward = {skillPoints: 0, nolics: 0};
  let remainingLifePoints = 0;
  const touchedTournamentIds = new Set();
  await db.runTransaction(async (transaction) => {
    // Firestore requires every read before the first write.
    const userRef = db.collection("users").doc(uid);
    const userSnapshot = await transaction.get(userRef);
    const existingSnapshots = [];
    for (const item of events) {
      existingSnapshots.push(await transaction.get(item.ref));
    }

    // Life points gate how much activity is paid for. Regeneration is derived from the elapsed
    // time here, so nothing has to run while the user is away. Accounts created before this
    // field existed start from a full tank rather than from zero.
    const userData = userSnapshot.exists ? userSnapshot.data() || {} : {};
    const lifeCeiling = maxLifePoints(
      Math.min(MAX_STANDARD_HEARTS, nonNegativeInteger(userData.standardHearts, MAX_STANDARD_HEARTS)),
    );
    let life = regenerateLifePoints(
      numberValue(userData.lifePoints, lifeCeiling),
      numberValue(userData.lifePointsUpdatedAtMs, now),
      now,
      lifeCeiling,
    );

    const delta = {skillPoints: 0, nolics: 0};
    events.forEach((item, index) => {
      // An attempt is paid for only when it is new, its percentScore follows from its own
      // codeAnswer, and the player still has the life points it costs. The game is offline-first,
      // so a legitimate attempt can arrive with an empty tank — it is stored either way, and the
      // flag records whether it was charged.
      const isNew = !existingSnapshots[index].exists;
      let lifeCharged = false;
      if (isNew && item.event.scoreVerified) {
        const charged = spendLifePoints(life.points, LESSON_ATTEMPT_LIFE_COST, lifeCeiling);
        if (charged.affordable) {
          life = {points: charged.points, updatedAtMs: life.updatedAtMs};
          lifeCharged = true;
        }
      }

      transaction.set(
        item.ref,
        clean({
          ...item.event,
          ...item.content,
          receivedAtMs: now,
          schemaVersion: 1,
          lifeCharged,
        }),
        {merge: true},
      );

      if (lifeCharged) {
        const itemReward = lessonResultReward(item.event);
        delta.skillPoints += itemReward.skillPoints;
        delta.nolics += itemReward.nolics;
        const tournamentId = writeTournamentAttemptToTransaction(transaction, item.event, item.content, now);
        if (tournamentId) touchedTournamentIds.add(tournamentId);
      }
    });

    if (delta.skillPoints > 0 || delta.nolics > 0) {
      writeUserProgressDelta(transaction, uid, delta, now);
    }
    transaction.set(
      userRef,
      {uid, lifePoints: life.points, lifePointsUpdatedAtMs: life.updatedAtMs, updatedAtMs: now},
      {merge: true},
    );
    reward = delta;
    remainingLifePoints = life.points;
  });
  for (const tournamentId of touchedTournamentIds) {
    await recalculateTournamentLeaderboard(tournamentId, now);
  }
  return {accepted: attempts.length, reward, lifePoints: remainingLifePoints};
});

exports.submitQuestRatingEvents = onCall(FUNCTION_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  await requireProfile(uid);
  const ratings = listMaps(request.data && request.data.ratings);
  if (ratings.length > MAX_RESULT_EVENTS_PER_CALL) {
    throw new HttpsError("invalid-argument", `At most ${MAX_RESULT_EVENTS_PER_CALL} ratings are accepted per call`);
  }
  if (ratings.length === 0) return {accepted: 0};

  const now = Date.now();
  const batch = db.batch();
  for (const item of ratings) {
    const event = normalizeQuestRatingEvent(item, uid);
    const content = contentKeysForEvent(event);
    const bucketId = eventBucketId(event.ratingId);
    const eventRef = db
      .collection(scopedCollection("rating_events", event.scope))
      .doc(bucketId)
      .collection("events")
      .doc(safeDocId(event.ratingId));
    const submissionRef = db
      .collection(scopedCollection("quest_rating_submissions", event.scope))
      .doc(content.questContentKey)
      .collection("ratings")
      .doc(hashHex(uid));
    const dirtyRef = db
      .collection(scopedCollection("quest_rating_dirty", event.scope))
      .doc(content.questContentKey);
    const eventData = clean({
      ...event,
      ...content,
      receivedAtMs: now,
      schemaVersion: 1,
    });
    batch.set(eventRef, eventData, {merge: true});
    batch.set(
      submissionRef,
      clean({
        ...eventData,
        uidHash: hashHex(uid),
        updatedAtMs: now,
      }),
      {merge: true},
    );
    batch.set(
      dirtyRef,
      clean({
        contentKey: content.questContentKey,
        scope: event.scope,
        ownerUid: event.ownerUid,
        catalogId: event.catalogId,
        questId: event.questId,
        sourceShelf: event.sourceShelf,
        changedAtMs: now,
      }),
      {merge: true},
    );
  }
  await batch.commit();
  return {accepted: ratings.length};
});

exports.aggregateQuestRatingsDaily = onSchedule(
  {...FUNCTION_OPTIONS, schedule: "every day 04:00", timeZone: "UTC"},
  async () => {
    await aggregateDirtyQuestRatings(Date.now(), DAILY_DIRTY_RATING_LIMIT);
  },
);

exports.aggregateQuestRatingsNow = onCall(FUNCTION_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const profile = await requireProfile(uid);
  if (profile.developerLevel <= DEVELOPER_ALL_ACCESS_LEVEL) {
    throw new HttpsError("permission-denied", "Developer level is required");
  }
  const limit = Math.max(1, Math.min(numberValue(request.data && request.data.limit, 200), DAILY_DIRTY_RATING_LIMIT));
  return aggregateDirtyQuestRatings(Date.now(), limit);
});

exports.ensureUserProfile = onCall(FUNCTION_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const result = await upsertUserProfile(uid, {
    nickname: generatedNickname(uid),
    knownLanguages: normalizeLanguages(request.data && request.data.knownLanguages),
    now: Date.now(),
    authStatus: authProfileStatus(request),
    allowNicknameUpgrade: false,
  });
  return profileResponse(result);
});

exports.updateUserNickname = onCall(FUNCTION_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const nickname = sanitizeNickname(stringValue(request.data && request.data.nickname), null);
  if (!nickname) {
    throw new HttpsError("invalid-argument", "Nickname must contain 3..24 supported characters");
  }
  const result = await upsertUserProfile(uid, {
    nickname,
    knownLanguages: normalizeLanguages(request.data && request.data.knownLanguages),
    now: Date.now(),
    authStatus: authProfileStatus(request),
    allowNicknameUpgrade: true,
  });
  return profileResponse(result);
});

/**
 * Verification is a human decision, not a computed one: an admin or developer reads what somebody
 * says about themselves, reaches them on telegram, and decides. These three calls carry that
 * conversation — filing, listing, deciding — and nothing about it is derivable from play.
 */
exports.submitVerificationRequest = onCall(FUNCTION_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  // An anonymous account is a container for statistics, not a person anybody could verify. The
  // ladder runs anonymous -> registered -> validated, and this is the step that needs a real login.
  if (authProfileStatus(request) !== "REGISTERED") {
    throw new HttpsError("failed-precondition", "Sign in before requesting verification");
  }
  const now = Date.now();
  const details = translateVerificationError(() => normalizeVerificationDetails(request.data, now));

  const requestRef = db.collection("verification_requests").doc(uid);
  const userRef = db.collection("users").doc(uid);
  await db.runTransaction(async (transaction) => {
    const existingRequest = await transaction.get(requestRef);
    const userSnapshot = await transaction.get(userRef);
    const user = userSnapshot.exists ? userSnapshot.data() || {} : {};
    const gate = canSubmitRequest(
      existingRequest.exists ? existingRequest.data() : null,
      hasTrophy(user.trophies, TROPHY_VERIFIED),
    );
    if (!gate.allowed) throw new HttpsError("failed-precondition", gate.reason);

    // Kept on the user document too, so the owner can see and correct what they submitted. Only
    // they can read it there; the copy in the request is what reviewers see.
    transaction.set(userRef, {uid, ...details, updatedAtMs: now}, {merge: true});
    transaction.set(requestRef, {
      ownerUid: uid,
      ...details,
      status: "PENDING",
      processed: false,
      submittedAtMs: now,
      decidedAtMs: null,
      decidedByUid: null,
      rejectionReason: null,
    });
  });
  return {status: "PENDING", submittedAtMs: now};
});

exports.fetchVerificationRequests = onCall(FUNCTION_OPTIONS, async (request) => {
  await requireVerifier(requireAuthUid(request));
  const limit = Math.max(1, Math.min(numberValue(request.data && request.data.limit, 20), 100));
  const snapshot = await db
    .collection("verification_requests")
    .where("processed", "==", false)
    .limit(limit)
    .get();
  return {
    requests: snapshot.docs.map((doc) => {
      const data = doc.data() || {};
      return {
        uid: doc.id,
        realName: stringValue(data.realName),
        birthday: stringValue(data.birthday),
        city: stringValue(data.city),
        telegram: stringValue(data.telegram),
        submittedAtMs: numberValue(data.submittedAtMs, 0),
      };
    }),
  };
});

exports.decideVerification = onCall(FUNCTION_OPTIONS, async (request) => {
  const reviewerUid = requireAuthUid(request);
  await requireVerifier(reviewerUid);
  const targetUid = stringValue(request.data && request.data.uid);
  if (!targetUid) throw new HttpsError("invalid-argument", "uid is required");
  // Being trusted to check other people is not being trusted to wave yourself through.
  if (targetUid === reviewerUid) {
    throw new HttpsError("permission-denied", "Cannot decide your own verification");
  }
  const decision = translateVerificationError(() =>
    normalizeDecision(request.data && request.data.decision));
  const reason = nullableString(request.data && request.data.reason);
  const now = Date.now();

  const requestRef = db.collection("verification_requests").doc(targetUid);
  const userRef = db.collection("users").doc(targetUid);
  const profileRef = db.collection("profiles").doc(targetUid);
  await db.runTransaction(async (transaction) => {
    const existingRequest = await transaction.get(requestRef);
    if (!existingRequest.exists) {
      throw new HttpsError("not-found", `No verification request for ${targetUid}`);
    }
    const data = existingRequest.data() || {};
    if (data.processed === true) {
      throw new HttpsError("failed-precondition", "Verification request is already decided");
    }

    transaction.set(requestRef, {
      status: decision,
      processed: true,
      decidedAtMs: now,
      decidedByUid: reviewerUid,
      rejectionReason: decision === DECISION_APPROVED ? null : reason,
    }, {merge: true});

    if (decision === DECISION_APPROVED) {
      transaction.set(userRef, {
        uid: targetUid,
        trophies: admin.firestore.FieldValue.arrayUnion(TROPHY_VERIFIED),
        verifiedAtMs: now,
        verifiedByUid: reviewerUid,
        status: "VALIDATED",
        updatedAtMs: now,
      }, {merge: true});
      transaction.set(profileRef, {uid: targetUid, status: "VALIDATED"}, {merge: true});
    }
  });
  return {uid: targetUid, decision, decidedAtMs: now};
});

exports.applyShopPurchase = onCall(FUNCTION_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const itemId = stringValue(request.data && request.data.itemId);
  if (![SHOP_ITEM_STANDARD_HEART_SLOT, SHOP_ITEM_GOLD_HEART].includes(itemId)) {
    throw new HttpsError("invalid-argument", `Unsupported shop item ${itemId}`);
  }

  const userRef = db.collection("users").doc(uid);
  const now = Date.now();
  let response = null;
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(userRef);
    const current = readEconomyBalance(snapshot.exists ? snapshot.data() || {} : {});
    const balance = applyShopPurchaseToBalance(current, itemId);
    transaction.set(
      userRef,
      clean({
        uid,
        updatedAtMs: now,
        hasPremium: balance.hasPremium,
        streakDays: balance.streakDays,
        stars: balance.stars,
        pointsNolics: balance.nolics,
        nolics: balance.nolics,
        standardHearts: balance.standardHearts,
        goldHearts: balance.goldHearts,
        gold: balance.gold,
      }),
      {merge: true},
    );
    response = {itemId, balance};
  });
  return response;
});

exports.openGiftBox = onCall(FUNCTION_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const now = Date.now();
  await upsertUserProfile(uid, {
    nickname: generatedNickname(uid),
    knownLanguages: normalizeLanguages(request.data && request.data.knownLanguages),
    now,
    authStatus: authProfileStatus(request),
    allowNicknameUpgrade: false,
  });

  let reward = null;
  await db.runTransaction(async (transaction) => {
    const userRef = db.collection("users").doc(uid);
    const userSnapshot = await transaction.get(userRef);
    const existingUser = userSnapshot.exists ? userSnapshot.data() || {} : {};
    const boxState = advanceGiftBoxState(existingUser, now);
    if (boxState.boxCount <= 0) {
      throw new HttpsError("failed-precondition", "No gift boxes available");
    }
    // Chosen here rather than before the transaction: only inside it do we know which badges the
    // player already holds, and a duplicate would be a reward worth nothing.
    reward = generateGiftBoxReward(existingUser.trophies);
    transaction.set(
      userRef,
      clean({
        uid,
        updatedAtMs: now,
        ...boxState,
        boxCount: boxState.boxCount - 1,
        ...giftBoxRewardUpdate(existingUser, reward, now),
      }),
      {merge: true},
    );
  });
  return reward;
});

exports.setPublicQuestShelf = onCall(FUNCTION_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const profile = await requireProfile(uid);
  if (profile.developerLevel <= DEVELOPER_ALL_ACCESS_LEVEL) {
    throw new HttpsError("permission-denied", "Developer level is required");
  }

  const questId = stringValue(request.data && request.data.questId);
  if (!questId) throw new HttpsError("invalid-argument", "questId is required");
  const targetShelf = publicQuestShelfValue(request.data && request.data.targetShelf);
  const now = Date.now();
  const questRef = db.collection("quests").doc(questId);

  return db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(questRef);
    if (!snapshot.exists) {
      throw new HttpsError("not-found", `Quest ${questId} not found`);
    }
    const quest = snapshot.data() || {};
    const catalogId = stringValue(quest.catalogId);
    if (!catalogId) {
      throw new HttpsError("failed-precondition", `Quest ${questId} has no catalogId`);
    }
    const nextVersion = numberValue(quest.version, 0) + 1;
    const lastModifiedAt = admin.firestore.Timestamp.fromMillis(now);
    transaction.set(
      questRef,
      {
        visibleOn: [targetShelf],
        version: nextVersion,
        lastModifiedAt,
      },
      {merge: true},
    );
    transaction.set(
      db.doc(publicCatalogSyncChangePath(catalogId, "quest", questId)),
      syncChangeDocument("quest", questId, now),
      {merge: true},
    );
    return {
      questId,
      catalogId,
      targetShelf,
      version: nextVersion,
      changedAtMs: now,
    };
  });
});

exports.recalculateTournamentLeaderboard = onCall(FUNCTION_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const profile = await requireProfile(uid);
  if (profile.developerLevel <= DEVELOPER_ALL_ACCESS_LEVEL) {
    throw new HttpsError("permission-denied", "Developer level is required");
  }

  const tournamentId = firestoreDocumentId(request.data && request.data.tournamentId, "tournamentId");
  return recalculateTournamentLeaderboard(tournamentId, Date.now());
});

exports.fetchTournamentOverview = onCall(FUNCTION_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  await requireProfile(uid);
  const tournamentId = tournamentIdValue(request.data && request.data.tournamentId);
  const limit = Math.max(1, Math.min(numberValue(request.data && request.data.limit, 50), TOURNAMENT_LEADERBOARD_LIMIT));
  const tournamentRef = db.collection("tournaments").doc(tournamentId);
  const tournamentSnapshot = await tournamentRef.get();
  const metadataSnapshot = await tournamentRef.collection("leaderboard_meta").doc("current").get();
  const leaderboardSnapshot = await tournamentRef.collection("leaderboard")
    .orderBy("place")
    .limit(limit)
    .get();
  const participantsSnapshot = await tournamentRef.collection("participants")
    .orderBy("lastAttemptAtMs", "desc")
    .limit(TOURNAMENT_PARTICIPANT_LIMIT)
    .get();

  const leaderboard = leaderboardSnapshot.docs.map((doc) => tournamentLeaderboardEntry(doc.data() || {}, doc.id));
  const participants = participantsSnapshot.docs.map((doc) => tournamentParticipantEntry(doc.data() || {}, doc.id));
  const userIds = Array.from(new Set(
    leaderboard.map((entry) => entry.userId)
      .concat(participants.map((entry) => entry.userId))
      .concat(uid)
      .filter(Boolean),
  ));
  const profiles = await readPublicUserProfiles(userIds);
  const enrichedLeaderboard = leaderboard.map((entry) => enrichTournamentLeaderboardEntry(entry, profiles));
  const enrichedParticipants = participants.map((entry) => enrichTournamentParticipantEntry(entry, profiles));
  const currentUserEntry = enrichedLeaderboard.find((entry) => entry.userId === uid) || null;
  const currentUserParticipant = enrichedParticipants.find((entry) => entry.userId === uid) || null;

  return {
    tournament: tournamentSummary(tournamentId, tournamentSnapshot.exists ? tournamentSnapshot.data() || {} : {}),
    metadata: metadataSnapshot.exists ? tournamentMetadata(metadataSnapshot.data() || {}) : null,
    leaderboard: enrichedLeaderboard,
    participants: enrichedParticipants,
    currentUserEntry,
    currentUserParticipant,
  };
});

async function recalculateTournamentLeaderboard(tournamentId, now) {
  const tournamentRef = db.collection("tournaments").doc(tournamentId);
  const tournamentSnapshot = await tournamentRef.get();
  if (!tournamentSnapshot.exists) {
    throw new HttpsError("not-found", `Tournament ${tournamentId} not found`);
  }
  const groups = await readTournamentRankingGroups(tournamentRef);
  const ranking = calculateTournamentLeaderboard(groups, {minGroupsForStableRank: 7});
  await writeTournamentLeaderboard(tournamentRef, ranking, now);
  return {
    tournamentId,
    updatedAtMs: now,
    ...ranking.metadata,
  };
}

async function readTournamentRankingGroups(tournamentRef) {
  const groupsSnapshot = await tournamentRef.collection("groups")
    .limit(MAX_TOURNAMENT_GROUPS_PER_RECALC)
    .get();
  const groups = [];
  for (const groupDoc of groupsSnapshot.docs) {
    const groupData = groupDoc.data() || {};
    const inlineResults = listMaps(groupData.results)
      .map((item) => tournamentResultData(item, stringValue(item.userId || item.uid || item.playerId)))
      .filter(Boolean);
    const resultDocs = await groupDoc.ref.collection("results")
      .limit(MAX_TOURNAMENT_RESULTS_PER_GROUP)
      .get();
    const storedResults = resultDocs.docs
      .map((doc) => tournamentResultData(doc.data() || {}, doc.id))
      .filter(Boolean);
    groups.push({
      groupId: groupDoc.id,
      weight: numberValue(groupData.weight, 1),
      results: inlineResults.concat(storedResults),
    });
  }
  return groups;
}

function tournamentResultData(data, fallbackUserId) {
  const userId = stringValue(data.userId || data.uid || data.playerId, fallbackUserId);
  if (!userId) return null;
  return clean({
    userId,
    percent: nullableNumber(firstDefined(data.percent, data.percentScore, data.completionPercent, data.scorePercent)),
    correctCount: nullableNumber(firstDefined(data.correctCount, data.correctAnswers)),
    questionCount: nullableNumber(firstDefined(data.questionCount, data.totalQuestions, data.answeredCount)),
    weight: nullableNumber(data.weight),
    completedAtMs: nullableNumber(firstDefined(data.completedAtMs, data.finishedAtMs, data.updatedAtMs)),
  });
}

function writeTournamentAttemptToTransaction(transaction, event, content, now) {
  const group = tournamentGroupForAttempt(event);
  const result = tournamentResultForAttempt(event);
  if (!group || !result) return null;

  const tournamentRef = db.collection("tournaments").doc(group.tournamentId);
  const groupRef = tournamentRef.collection("groups").doc(group.groupId);
  const resultRef = groupRef.collection("results").doc(safeDocId(result.userId));
  const participantRef = tournamentRef.collection("participants").doc(safeDocId(result.userId));

  transaction.set(
    tournamentRef,
    clean({
      id: group.tournamentId,
      sourceShelf: group.sourceShelf,
      title: tournamentTitleForId(group.tournamentId),
      stageLabel: tournamentStageLabelForId(group.tournamentId),
      updatedAtMs: now,
      groupWindowMs: group.groupWindowMs,
      schemaVersion: 1,
    }),
    {merge: true},
  );
  transaction.set(
    groupRef,
    clean({
      id: group.groupId,
      tournamentId: group.tournamentId,
      sourceShelf: group.sourceShelf,
      catalogId: event.catalogId,
      questId: event.questId,
      lessonId: group.lessonId,
      difficulty: group.difficulty,
      windowStartMs: group.windowStartMs,
      windowEndMs: group.windowEndMs,
      weight: 1,
      updatedAtMs: now,
      schemaVersion: 1,
    }),
    {merge: true},
  );
  transaction.set(
    resultRef,
    clean({
      ...result,
      ...content,
      sourceShelf: group.sourceShelf,
      updatedAtMs: now,
      schemaVersion: 1,
    }),
    {merge: true},
  );
  transaction.set(
    participantRef,
    clean({
      userId: result.userId,
      sourceShelf: group.sourceShelf,
      lastAttemptAtMs: result.completedAtMs,
      lastPercent: result.percent,
      lastGroupId: group.groupId,
      attemptCount: admin.firestore.FieldValue.increment(1),
      updatedAtMs: now,
      schemaVersion: 1,
    }),
    {merge: true},
  );
  return group.tournamentId;
}

function tournamentSummary(tournamentId, data) {
  return {
    id: tournamentId,
    sourceShelf: stringValue(data.sourceShelf, tournamentId),
    title: stringValue(data.title, tournamentTitleForId(tournamentId)),
    stageLabel: stringValue(data.stageLabel, tournamentStageLabelForId(tournamentId)),
    updatedAtMs: numberValue(data.updatedAtMs, 0),
    leaderboardUpdatedAtMs: numberValue(data.leaderboardUpdatedAtMs, 0),
  };
}

function tournamentMetadata(data) {
  return {
    algorithmVersion: stringValue(data.algorithmVersion),
    playersCount: numberValue(data.playersCount, 0),
    groupsCount: numberValue(data.groupsCount, 0),
    countedGroups: numberValue(data.countedGroups, 0),
    comparisonCount: numberValue(data.comparisonCount, 0),
    isFullyConnected: Boolean(data.isFullyConnected),
    updatedAtMs: numberValue(data.updatedAtMs, 0),
  };
}

function tournamentLeaderboardEntry(data, fallbackId) {
  return {
    userId: stringValue(data.userId, fallbackId),
    place: numberValue(data.place, 0),
    ratingPercent: numberValue(data.ratingPercent, 0),
    ratingPoints: numberValue(data.ratingPoints, 0),
    averagePercent: numberValue(data.averagePercent, 0),
    groupsPlayed: numberValue(data.groupsPlayed, 0),
    comparisons: numberValue(data.comparisons, 0),
    uniqueOpponents: numberValue(data.uniqueOpponents, 0),
    componentId: numberValue(data.componentId, 0),
    componentSize: numberValue(data.componentSize, 0),
    confidence: numberValue(data.confidence, 0),
    updatedAtMs: numberValue(data.updatedAtMs, 0),
  };
}

function tournamentParticipantEntry(data, fallbackId) {
  return {
    userId: stringValue(data.userId, fallbackId),
    attemptCount: numberValue(data.attemptCount, 0),
    lastAttemptAtMs: numberValue(data.lastAttemptAtMs, 0),
    lastPercent: numberValue(data.lastPercent, 0),
    status: stringValue(data.status, "active"),
    updatedAtMs: numberValue(data.updatedAtMs, 0),
  };
}

async function readPublicUserProfiles(userIds) {
  const result = new Map();
  for (const userId of userIds) {
    const snapshot = await db.collection("users").doc(userId).get();
    const data = snapshot.exists ? snapshot.data() || {} : {};
    result.set(userId, {
      userId,
      nickname: stringValue(data.nickname, fallbackNickname(userId)),
      avatarUrl: nullableString(data.avatarUrl),
    });
  }
  return result;
}

function enrichTournamentLeaderboardEntry(entry, profiles) {
  const profile = profiles.get(entry.userId) || {};
  return {
    ...entry,
    nickname: stringValue(profile.nickname, fallbackNickname(entry.userId)),
    avatarUrl: nullableString(profile.avatarUrl),
  };
}

function enrichTournamentParticipantEntry(entry, profiles) {
  const profile = profiles.get(entry.userId) || {};
  return {
    ...entry,
    nickname: stringValue(profile.nickname, fallbackNickname(entry.userId)),
    avatarUrl: nullableString(profile.avatarUrl),
  };
}

function fallbackNickname(userId) {
  return `User${hashHex(userId).slice(0, 6).toUpperCase()}`;
}

function tournamentTitleForId(tournamentId) {
  return tournamentId === "tournamentFinal" ? "Чемпионат мира" : "Отборочный турнир";
}

function tournamentStageLabelForId(tournamentId) {
  return tournamentId === "tournamentFinal" ? "Сложные вопросы" : "Лёгкие вопросы";
}

async function writeTournamentLeaderboard(tournamentRef, ranking, now) {
  const existing = await tournamentRef.collection("leaderboard").get();
  const nextIds = new Set(ranking.leaderboard.map((entry) => safeDocId(entry.userId)));
  const operations = [];
  operations.push({
    type: "set",
    ref: tournamentRef.collection("leaderboard_meta").doc("current"),
    data: clean({
      ...ranking.metadata,
      updatedAtMs: now,
    }),
  });
  operations.push({
    type: "set",
    ref: tournamentRef,
    data: clean({
      leaderboardUpdatedAtMs: now,
      leaderboardAlgorithmVersion: ranking.metadata.algorithmVersion,
      leaderboardPlayersCount: ranking.metadata.playersCount,
      leaderboardIsFullyConnected: ranking.metadata.isFullyConnected,
    }),
  });
  for (const entry of ranking.leaderboard) {
    operations.push({
      type: "set",
      ref: tournamentRef.collection("leaderboard").doc(safeDocId(entry.userId)),
      data: clean({
        ...entry,
        updatedAtMs: now,
        algorithmVersion: ranking.metadata.algorithmVersion,
      }),
    });
  }
  for (const doc of existing.docs) {
    if (!nextIds.has(doc.id)) {
      operations.push({type: "delete", ref: doc.ref});
    }
  }
  await commitOperations(operations);
}

async function commitOperations(operations) {
  let batch = db.batch();
  let count = 0;
  for (const operation of operations) {
    if (operation.type === "delete") {
      batch.delete(operation.ref);
    } else {
      batch.set(operation.ref, operation.data, {merge: true});
    }
    count += 1;
    if (count >= 450) {
      await batch.commit();
      batch = db.batch();
      count = 0;
    }
  }
  if (count > 0) await batch.commit();
}

async function processArenaRequest(request) {
  if (!request.ownerUid) throw new Error("request.ownerUid must not be blank");
  if (!request.questions || request.questions.length === 0) {
    throw new Error("request.questions must not be empty");
  }
  await requireProfile(request.ownerUid);

  const now = Date.now();
  const adminTasks = toAdminLessonTasks(request, now);
  const batch = db.batch();
  writePrivateHierarchyToBatch(batch, request);
  writeAdminReviewLessonTasksToBatch(batch, adminTasks);
  batch.set(
    db.collection("quest_review_requests").doc(request.submissionId),
    {
      processed: true,
      processedAtMs: now,
      lastError: null,
      status: "UNDER_REVIEW",
      targetShelf: request.targetShelf,
      targetLessonIds: request.targetLessonIds,
    },
    {merge: true},
  );
  await batch.commit();
  return {
    submissionId: request.submissionId,
    privateQuestPath: privateQuestPath(request.ownerUid, request.draft.catalogId, request.draft.id),
    adminLessonTaskPaths: adminTasks.map((task) => adminLessonPath(task.lessonId)),
  };
}

function writePrivateHierarchyToBatch(batch, request) {
  for (const [path, data] of Object.entries(privateDocuments(request))) {
    batch.set(db.doc(path), clean(data), {merge: true});
  }
}

function writeAdminReviewLessonTasksToBatch(batch, tasks) {
  for (const [path, data] of Object.entries(adminDocuments(tasks))) {
    batch.set(db.doc(path), clean(data), {merge: true});
  }
}

async function reconcileChangedReviewLessons(sinceMs, now) {
  const changes = await db.collection("admin/review/sync_changes")
    .where("changedAtMs", ">", sinceMs)
    .get();
  const assignmentIds = new Set(
    changes.docs
      .map((doc) => stringValue((doc.data() || {}).assignmentId, doc.id))
      .filter(Boolean),
  );
  if (assignmentIds.size === 0) return {checked: 0, published: 0};

  const config = await readArenaReviewConfig();
  const tasks = await readAdminReviewLessonTasksByIds(assignmentIds);
  const touchedSubmissionIds = new Set();
  const batch = db.batch();

  for (const task of tasks) {
    const records = await readReviewRecords(task.lessonId);
    const aggregation = rebuildAggregate(task, records, config);
    const updatedTask = {
      ...task,
      checks: aggregation.checks,
      status: lessonReviewStatus(aggregation.checks, task, config),
      changedAtMs: now,
    };
    writeAdminReviewLessonTasksToBatch(batch, [updatedTask]);
    touchedSubmissionIds.add(task.submissionId);
  }
  await batch.commit();

  let published = 0;
  for (const submissionId of touchedSubmissionIds) {
    if (await publishSubmissionIfReady(submissionId, config, now)) published += 1;
  }
  return {checked: tasks.length, published};
}

async function aggregateDirtyQuestRatings(now, maxDirtyDocs) {
  const publicResult = await aggregateDirtyQuestRatingsForScope(PUBLIC_SCOPE, now, maxDirtyDocs);
  const remaining = Math.max(0, maxDirtyDocs - publicResult.processed - publicResult.skipped);
  const privateResult = await aggregateDirtyQuestRatingsForScope(PRIVATE_SCOPE, now, remaining);
  return {public: publicResult, private: privateResult};
}

async function aggregateDirtyQuestRatingsForScope(scope, now, maxDirtyDocs) {
  if (maxDirtyDocs <= 0) return {processed: 0, skipped: 0, qualificationDelta: 0};
  const root = db.collection(scopedCollection("quest_rating_dirty", scope));
  let processed = 0;
  let skipped = 0;
  let qualificationDelta = 0;
  while (processed + skipped < maxDirtyDocs) {
    const limit = Math.min(DIRTY_RATING_READ_BATCH, maxDirtyDocs - processed - skipped);
    const snapshot = await root.limit(limit).get();
    if (snapshot.empty) break;
    for (const doc of snapshot.docs) {
      const result = await aggregateDirtyQuestRatingDoc(scope, doc, now);
      processed += result.processed;
      skipped += result.skipped;
      qualificationDelta += numberValue(result.qualificationDelta, 0);
    }
    if (snapshot.size < limit) break;
  }
  return {processed, skipped, qualificationDelta};
}

async function aggregateDirtyQuestRatingDoc(scope, dirtyDoc, now) {
  const dirty = normalizeDirtyQuestRating(dirtyDoc.data() || {}, dirtyDoc.id, scope);
  if (!dirty.contentKey || !dirty.catalogId || !dirty.questId || (dirty.scope === PRIVATE_SCOPE && !dirty.ownerUid)) {
    await dirtyDoc.ref.delete();
    return {processed: 0, skipped: 1};
  }

  const targetUid = await ratingQualificationTargetUid(dirty);
  const ratingsSnapshot = await db
    .collection(scopedCollection("quest_rating_submissions", dirty.scope))
    .doc(dirty.contentKey)
    .collection("ratings")
    .get();
  let count = 0;
  let sum = 0;
  for (const doc of ratingsSnapshot.docs) {
    const rating = numberValue((doc.data() || {}).rating, null);
    if (rating >= 1 && rating <= 3) {
      count += 1;
      sum += rating;
    }
  }
  const averageRating = count > 0 ? Math.round((sum / count) * 10) / 10 : null;
  const qualificationScore = questRatingQualificationScore(ratingsSnapshot.docs, targetUid);
  const aggregateRef = db.collection(scopedCollection("quest_rating_aggregates", dirty.scope)).doc(dirty.contentKey);
  const result = {processed: 1, skipped: 0, qualificationDelta: 0};
  await db.runTransaction(async (transaction) => {
    const dirtySnapshot = await transaction.get(dirtyDoc.ref);
    if (!dirtySnapshot.exists) {
      result.processed = 0;
      result.skipped = 1;
      return;
    }
    const aggregateSnapshot = await transaction.get(aggregateRef);
    const previousAggregate = aggregateSnapshot.exists ? aggregateSnapshot.data() || {} : {};
    const previousTargetUid = nullableString(previousAggregate.qualificationTargetUid);
    const previousScore = numberValue(previousAggregate.qualificationScore, 0);
    const previousAppliedScore = numberValue(previousAggregate.qualificationAppliedScore, previousScore);
    const targetSnapshots = await readQualificationTargets(
      transaction,
      [previousTargetUid, targetUid].filter(Boolean),
    );
    const qualificationApplication = writeRatingQualificationDelta(
      transaction,
      targetSnapshots,
      {
        previousTargetUid,
        previousAppliedScore,
        targetUid,
        qualificationScore,
        now,
      },
    );
    result.qualificationDelta = qualificationApplication.delta;

    transaction.set(
      aggregateRef,
      clean({
        contentKey: dirty.contentKey,
        scope: dirty.scope,
        ownerUid: dirty.ownerUid,
        catalogId: dirty.catalogId,
        questId: dirty.questId,
        averageRating,
        averageRatingCount: count,
        qualificationTargetUid: targetUid,
        qualificationField: QUEST_RATING_QUALIFICATION_USER_FIELD,
        qualificationScore,
        qualificationAppliedScore: qualificationApplication.appliedScore,
        updatedAtMs: now,
      }),
      {merge: true},
    );
    writeQuestRatingAggregateToWriter(transaction, dirty, averageRating, count, now);
    transaction.delete(dirtyDoc.ref);
  });
  return result;
}

function writeQuestRatingAggregateToWriter(writer, dirty, averageRating, averageRatingCount, now) {
  const ratingPatch = clean({
    averageRating,
    averageRatingCount,
    ratingUpdatedAtMs: now,
    lastModifiedAt: admin.firestore.Timestamp.fromMillis(now),
    version: admin.firestore.FieldValue.increment(1),
  });
  if (dirty.scope === PRIVATE_SCOPE) {
    writer.set(
      db.doc(privateQuestPath(dirty.ownerUid, dirty.catalogId, dirty.questId)),
      {...ratingPatch, changedAtMs: now},
      {merge: true},
    );
    writer.set(
      db.doc(privateSyncChangePath(dirty.ownerUid, dirty.catalogId, dirty.questId)),
      {
        id: dirty.questId,
        type: "quest",
        catalogId: dirty.catalogId,
        questId: dirty.questId,
        changedAtMs: now,
      },
      {merge: true},
    );
    return;
  }
  writer.set(db.collection("quests").doc(dirty.questId), ratingPatch, {merge: true});
  writer.set(
    db.doc(publicCatalogSyncChangePath(dirty.catalogId, "quest", dirty.questId)),
    syncChangeDocument("quest", dirty.questId, now),
    {merge: true},
  );
}

async function ratingQualificationTargetUid(dirty) {
  if (dirty.scope === PRIVATE_SCOPE) return dirty.ownerUid;
  const snapshot = await db.collection("quests").doc(dirty.questId).get();
  if (!snapshot.exists) return null;
  const data = snapshot.data() || {};
  return nullableString(data.authorUid || data.ownerUid);
}

function questRatingQualificationScore(ratingDocs, targetUid) {
  if (!targetUid) return 0;
  let score = 0;
  for (const doc of ratingDocs) {
    const data = doc.data() || {};
    const rating = numberValue(data.rating, null);
    if (rating < 1 || rating > 3) continue;
    const raterUid = nullableString(data.userId);
    if (raterUid && raterUid === targetUid) continue;
    score += questRatingQualificationDelta(rating);
  }
  return score;
}

function questRatingQualificationDelta(rating) {
  return (rating - 2) * QUEST_RATING_QUALIFICATION_POINTS_PER_STAR;
}

async function readQualificationTargets(transaction, uids) {
  const result = new Map();
  for (const uid of Array.from(new Set(uids.filter(Boolean)))) {
    const userRef = db.collection("users").doc(uid);
    const profileRef = db.collection("profiles").doc(uid);
    const userSnapshot = await transaction.get(userRef);
    const profileSnapshot = await transaction.get(profileRef);
    result.set(uid, {
      userRef,
      profileRef,
      userData: userSnapshot.exists ? userSnapshot.data() || {} : {},
      profileData: profileSnapshot.exists ? profileSnapshot.data() || {} : {},
    });
  }
  return result;
}

function writeRatingQualificationDelta(transaction, targetSnapshots, options) {
  let appliedDelta = 0;
  let appliedScore = 0;
  if (
    options.previousTargetUid &&
    options.previousTargetUid !== options.targetUid &&
    options.previousAppliedScore !== 0
  ) {
    appliedDelta += writeUserQualificationLevelDelta(
      transaction,
      targetSnapshots,
      options.previousTargetUid,
      -options.previousAppliedScore,
      options.now,
    );
  }
  if (options.targetUid) {
    const previousTargetAppliedScore =
      options.previousTargetUid === options.targetUid ? options.previousAppliedScore : 0;
    const targetDelta = writeUserQualificationLevelDelta(
      transaction,
      targetSnapshots,
      options.targetUid,
      options.qualificationScore - previousTargetAppliedScore,
      options.now,
    );
    appliedDelta += targetDelta;
    appliedScore = previousTargetAppliedScore + targetDelta;
  }
  return {delta: appliedDelta, appliedScore};
}

function writeUserQualificationLevelDelta(transaction, targetSnapshots, uid, delta, now) {
  if (!uid || delta === 0) return 0;
  const target = targetSnapshots.get(uid);
  if (!target) return 0;
  const current = currentRatingQualificationLevel(target.userData, target.profileData);
  const next = Math.max(0, current + delta);
  const appliedDelta = next - current;
  if (appliedDelta === 0) return 0;
  transaction.set(
    target.userRef,
    clean({
      uid,
      updatedAtMs: now,
      [QUEST_RATING_QUALIFICATION_USER_FIELD]: next,
      qualifications: {
        [QUEST_RATING_QUALIFICATION_USER_FIELD]: next,
      },
      qualification: {
        [QUEST_RATING_QUALIFICATION_USER_FIELD]: next,
        [QUEST_RATING_QUALIFICATION_PROFILE_FIELD]: next,
      },
    }),
    {merge: true},
  );
  transaction.set(
    target.profileRef,
    clean({
      uid,
      updatedAtMs: now,
      [QUEST_RATING_QUALIFICATION_PROFILE_FIELD]: next,
    }),
    {merge: true},
  );
  return appliedDelta;
}

function currentRatingQualificationLevel(userData, profileData) {
  const userQualifications = userData.qualifications || {};
  const userQualification = userData.qualification || {};
  return Math.max(
    nonNegativeNumber(profileData[QUEST_RATING_QUALIFICATION_PROFILE_FIELD], 0),
    nonNegativeNumber(userData[QUEST_RATING_QUALIFICATION_USER_FIELD], 0),
    nonNegativeNumber(userQualifications[QUEST_RATING_QUALIFICATION_USER_FIELD], 0),
    nonNegativeNumber(userQualification[QUEST_RATING_QUALIFICATION_USER_FIELD], 0),
    nonNegativeNumber(userQualification[QUEST_RATING_QUALIFICATION_PROFILE_FIELD], 0),
  );
}

async function publishSubmissionIfReady(submissionId, config, now) {
  const requestSnapshot = await db.collection("quest_review_requests").doc(submissionId).get();
  if (!requestSnapshot.exists) return false;
  const request = normalizeRequest(requestSnapshot.data() || {}, submissionId);
  if (request.status === "PUBLISHED") return false;

  const taskSnapshot = await db.collection("admin/review/lessons")
    .where("submissionId", "==", submissionId)
    .get();
  const tasks = [];
  for (const doc of taskSnapshot.docs) {
    tasks.push(await adminLessonSnapshotToTask(doc));
  }
  const requestedLessonIds =
    request.targetLessonIds.length > 0
      ? new Set(request.targetLessonIds)
      : new Set(request.lessons.map((lesson) => lesson.id));
  const targetTasks = tasks.filter((task) => requestedLessonIds.has(task.lessonId));
  if (targetTasks.length === 0 || targetTasks.length < requestedLessonIds.size) {
    await requestSnapshot.ref.set({status: "UNDER_REVIEW", updatedAtMs: now}, {merge: true});
    return false;
  }
  const allReady = targetTasks.every((task) => lessonPassed(task.checks, task, config));
  if (!allReady) {
    await requestSnapshot.ref.set({status: "UNDER_REVIEW", updatedAtMs: now}, {merge: true});
    return false;
  }

  const batch = db.batch();
  writePublicHierarchyToBatch(batch, requestWithPublishedQuestions(request, targetTasks), now);
  for (const task of targetTasks) {
    writeAdminReviewLessonTasksToBatch(batch, [{...task, status: "PUBLISHED", changedAtMs: now}]);
  }
  batch.set(
    requestSnapshot.ref,
    {
      status: "PUBLISHED",
      publishedAtMs: now,
      updatedAtMs: now,
      publicQuestId: request.draft.id,
    },
    {merge: true},
  );
  await batch.commit();
  return true;
}

function requestWithPublishedQuestions(request, targetTasks) {
  const questionsByLesson = new Map(
    targetTasks.map((task) => [task.lessonId, task.questions]),
  );
  const writtenLessons = new Set();
  const questions = [];
  for (const question of request.questions) {
    const publishedQuestions = questionsByLesson.get(question.lessonId);
    if (!publishedQuestions) {
      questions.push(question);
    } else if (!writtenLessons.has(question.lessonId)) {
      questions.push(...publishedQuestions);
      writtenLessons.add(question.lessonId);
    }
  }
  for (const [lessonId, publishedQuestions] of questionsByLesson.entries()) {
    if (!writtenLessons.has(lessonId)) {
      questions.push(...publishedQuestions);
    }
  }
  return {...request, questions};
}

function privateDocuments(request) {
  const documents = {};
  const ownerUid = request.ownerUid;
  const catalogId = request.draft.catalogId;
  const questId = request.draft.id;
  const sectionsById = indexBy(request.sections, "id");
  const themesById = indexBy(request.themes, "id");
  const lessonsById = indexBy(request.lessons, "id");

  documents[privateCatalogPath(ownerUid, catalogId)] = {
    id: catalogId,
    ownerUid,
    updatedAtMs: request.draft.updatedAtMs,
    changedAtMs: request.requestedAtMs,
  };
  documents[privateQuestPath(ownerUid, catalogId, questId)] = {
    id: questId,
    draftId: request.draftId,
    submissionId: request.submissionId,
    ownerUid,
    catalogId,
    title: request.draft.title,
    description: request.draft.description,
    defaultLanguage: request.draft.defaultLanguage,
    defaultDifficulty: request.draft.defaultDifficulty,
    publicQuestId: request.draft.publicQuestId,
    targetShelf: request.targetShelf,
    targetLessonIds: request.targetLessonIds,
    createdAtMs: request.draft.createdAtMs,
    localRevision: request.localRevision,
    updatedAtMs: request.draft.updatedAtMs,
    changedAtMs: request.requestedAtMs,
    review: checksToCallableMap(request.review),
  };
  documents[privateSyncChangePath(ownerUid, catalogId, questId)] = {
    id: questId,
    type: "quest",
    catalogId,
    questId,
    changedAtMs: request.requestedAtMs,
  };

  for (const section of request.sections) {
    documents[privateSectionPath(ownerUid, catalogId, questId, section.id)] = {
      id: section.id,
      draftId: section.draftId,
      title: section.title,
      order: section.order,
    };
  }
  for (const theme of request.themes) {
    documents[privateThemePath(ownerUid, catalogId, questId, theme.sectionId, theme.id)] = {
      id: theme.id,
      draftId: theme.draftId,
      sectionId: theme.sectionId,
      title: theme.title,
      order: theme.order,
    };
  }
  for (const lesson of request.lessons) {
    const theme = themesById[lesson.themeId];
    if (!theme) throw new Error(`Theme ${lesson.themeId} not found`);
    if (!sectionsById[theme.sectionId]) throw new Error(`Section ${theme.sectionId} not found`);
    documents[privateLessonPath(ownerUid, catalogId, questId, theme.sectionId, theme.id, lesson.id)] = {
      id: lesson.id,
      draftId: lesson.draftId,
      themeId: lesson.themeId,
      title: lesson.title,
      order: lesson.order,
    };
  }
  for (const question of request.questions) {
    const lesson = lessonsById[question.lessonId];
    if (!lesson) throw new Error(`Lesson ${question.lessonId} not found`);
    const theme = themesById[lesson.themeId];
    if (!theme) throw new Error(`Theme ${lesson.themeId} not found`);
    documents[
      privateQuestionPath(ownerUid, catalogId, questId, theme.sectionId, theme.id, lesson.id, question.id)
    ] = questionToDocument(question);
  }
  return documents;
}

function adminDocuments(tasks) {
  const documents = {};
  for (const task of tasks) {
    documents[adminLessonPath(task.lessonId)] = {
      id: task.lessonId,
      submissionId: task.submissionId,
      ownerUid: task.ownerUid,
      catalogId: task.catalogId,
      draftId: task.draftId,
      questId: task.questId,
      title: task.title,
      createdAtMs: task.createdAtMs,
      changedAtMs: task.changedAtMs,
      status: task.status || lessonReviewStatus(task.checks, task, null),
      targetShelf: task.targetShelf,
      availableLanguages: Array.from(availableLanguages(task)).sort(),
      sourceLanguages: Array.from(task.sourceLanguages).sort(),
      testingScore: task.checks.testingScore,
      logicScore: task.checks.logicScore,
      translationScore: task.checks.translationScore,
      translatedLanguages: task.checks.translatedLanguages,
      isTested: task.checks.isTested,
      isLogicReviewed: task.checks.isLogicReviewed,
      isTranslationReviewed: task.checks.isTranslationReviewed,
      checks: checksToCallableMap(task.checks),
      questionCount: task.questions.length,
    };
    documents[adminQuestPath(task.lessonId, task.questId)] = {
      id: task.questId,
      lessonId: task.lessonId,
      ownerUid: task.ownerUid,
      title: task.title,
      checks: checksToCallableMap(task.checks),
    };
    for (const question of task.questions) {
      documents[adminQuestionPath(task.lessonId, task.questId, question.id)] = questionToDocument(question);
    }
    documents[adminSyncChangePath(task.id)] = {
      id: task.id,
      assignmentId: task.id,
      lessonId: task.lessonId,
      changedAtMs: task.changedAtMs,
    };
  }
  return documents;
}

function writePublicHierarchyToBatch(batch, request, now) {
  for (const [path, data] of Object.entries(publicDocuments(request, now))) {
    batch.set(db.doc(path), clean(data), {merge: true});
  }
}

function publicDocuments(request, now) {
  const documents = {};
  const catalogId = request.draft.catalogId;
  const questId = request.draft.id;
  const archivedRoot = request.targetShelf === "archive";
  documents[`quests/${questId}`] = {
    id: questId,
    catalogId,
    authorUid: request.ownerUid,
    title: request.draft.title,
    picturePath: null,
    visibleOn: [request.targetShelf],
    averageRating: null,
    averageRatingCount: 0,
    version: request.localRevision,
    contentsVersion: request.localRevision,
    lastModifiedAt: admin.firestore.Timestamp.fromMillis(now),
    archived: archivedRoot,
  };
  documents[publicCatalogSyncChangePath(catalogId, "quest", questId)] = syncChangeDocument("quest", questId, now);
  for (const section of request.sections) {
    documents[`sections/${section.id}`] = {
      id: section.id,
      questId,
      title: section.title,
      order: section.order,
      version: request.localRevision,
      contentsVersion: request.localRevision,
      lastModifiedAt: admin.firestore.Timestamp.fromMillis(now),
      archived: false,
    };
    documents[publicCatalogSyncChangePath(catalogId, "section", section.id)] =
      syncChangeDocument("section", section.id, now);
  }
  for (const theme of request.themes) {
    documents[`themes/${theme.id}`] = {
      id: theme.id,
      sectionId: theme.sectionId,
      title: theme.title,
      order: theme.order,
      version: request.localRevision,
      contentsVersion: request.localRevision,
      lastModifiedAt: admin.firestore.Timestamp.fromMillis(now),
      archived: false,
    };
    documents[publicCatalogSyncChangePath(catalogId, "theme", theme.id)] = syncChangeDocument("theme", theme.id, now);
  }
  for (const lesson of request.lessons) {
    documents[`lessons/${lesson.id}`] = {
      id: lesson.id,
      themeId: lesson.themeId,
      title: lesson.title,
      order: lesson.order,
      version: request.localRevision,
      contentsVersion: request.localRevision,
      lastModifiedAt: admin.firestore.Timestamp.fromMillis(now),
      archived: false,
    };
    documents[publicCatalogSyncChangePath(catalogId, "lesson", lesson.id)] =
      syncChangeDocument("lesson", lesson.id, now);
  }
  for (const question of request.questions) {
    documents[`questions/${question.id}`] = {
      id: question.id,
      lessonId: question.lessonId,
      text: question.text,
      payload: question.payload,
      language: question.language,
      languageLevel: question.languageLevel,
      order: question.order,
      version: request.localRevision,
      lastModifiedAt: admin.firestore.Timestamp.fromMillis(now),
      archived: false,
    };
    documents[publicCatalogSyncChangePath(catalogId, "question", question.id)] =
      syncChangeDocument("question", question.id, now);
    documents[lessonContentSyncChangePath(question.lessonId, question.id)] =
      syncChangeDocument("question", question.id, now);
  }
  return documents;
}

function syncChangeDocument(type, id, changedAtMs) {
  return {type, id, changedAtMs};
}

function toAdminLessonTasks(request, createdAtMs) {
  return request.lessons.map((lesson) => {
    const lessonQuestions = request.questions.filter((question) => question.lessonId === lesson.id);
    return {
      id: `${request.submissionId}_${lesson.id}`,
      submissionId: request.submissionId,
      ownerUid: request.ownerUid,
      catalogId: request.draft.catalogId,
      draftId: request.draftId,
      questId: request.draft.id,
      lessonId: lesson.id,
      title: lesson.title,
      targetShelf: request.targetShelf,
      createdAtMs,
      changedAtMs: createdAtMs,
      checks: reviewToChecks(request.review, questionLanguages(lessonQuestions)),
      questions: lessonQuestions,
      sourceLanguages: new Set(lessonQuestions.map((question) => normalizeLanguage(question.language))),
    };
  });
}

function toAssignmentDto(task, profile, config) {
  const taskKinds = availableTasks(profile, task, config);
  if (taskKinds.size === 0) return null;
  const targets = translationTargets(profile, task, config);
  return {
    id: task.id,
    submissionId: task.submissionId,
    ownerUid: task.ownerUid,
    catalogId: task.catalogId,
    draftId: task.draftId,
    questId: task.questId,
    lessonId: task.lessonId,
    title: task.title,
    createdAtMs: task.createdAtMs,
    taskKinds: Array.from(taskKinds).sort(),
    sourceLanguages: Array.from(targets.sourceLanguages).sort(),
    newTranslationLanguages: Array.from(targets.newTranslationLanguages).sort(),
    reviewLanguages: Array.from(targets.reviewLanguages).sort(),
    checks: checksToCallableMap(task.checks),
    questions: task.questions.map(questionToDocument),
  };
}

function availableTasks(profile, task, config) {
  const openTasks =
    profile.developerLevel > DEVELOPER_ALL_ACCESS_LEVEL
      ? openTasksForDeveloper(task, config)
      : openTasksFor(profile, task, config);
  if (profile.developerLevel > DEVELOPER_ALL_ACCESS_LEVEL) return openTasks;

  const result = new Set();
  if (profile.testerLevel >= QUALIFIED_LEVEL && openTasks.has("TESTING")) result.add("TESTING");
  if (profile.adminLevel >= QUALIFIED_LEVEL) {
    if (openTasks.has("TESTING")) result.add("TESTING");
    if (openTasks.has("LOGIC")) result.add("LOGIC");
  }
  if (profile.translatorLevel >= QUALIFIED_LEVEL) {
    const targets = translationTargets(profile, task, config);
    if (targets.newTranslationLanguages.size > 0) result.add("TRANSLATION");
    if (targets.reviewLanguages.size > 0) result.add("TRANSLATION_REVIEW");
  }
  return result;
}

function canSubmit(profile, task, kind, config, existingRecords) {
  if (profile.developerLevel > DEVELOPER_ALL_ACCESS_LEVEL) {
    return isStageOpenForSubmit(task, kind, existingRecords, config);
  }
  switch (kind) {
    case "TESTING":
      return (
        hasTestingQualification(profile) &&
        isStageOpenForSubmit(task, "TESTING", existingRecords, config)
      );
    case "LOGIC":
      return (
        hasLogicQualification(profile) &&
        isStageOpenForSubmit(task, "LOGIC", existingRecords, config)
      );
    case "TRANSLATION":
      return (
        profile.translatorLevel >= QUALIFIED_LEVEL &&
        translationTargets(profile, task, config).newTranslationLanguages.size > 0
      );
    case "TRANSLATION_REVIEW":
      return (
        profile.translatorLevel >= QUALIFIED_LEVEL &&
        translationTargets(profile, task, config).reviewLanguages.size > 0
      );
    default:
      return false;
  }
}

function translationTargets(profile, task, config) {
  const hasDeveloperOverride = profile.developerLevel > DEVELOPER_ALL_ACCESS_LEVEL;
  if (!isReadyForTranslation(task.checks) || (!hasDeveloperOverride && profile.translatorLevel < QUALIFIED_LEVEL)) {
    return emptyTranslationTargets();
  }
  const sourceLanguages = new Set(
    Array.from(task.sourceLanguages).map(normalizeLanguage).filter(Boolean),
  );
  const required = requiredLanguages(task, config);
  const known = new Set(
    (hasDeveloperOverride
      ? [...profile.knownLanguages, ...sourceLanguages, ...required]
      : profile.knownLanguages
    ).map(normalizeLanguage).filter(Boolean),
  );
  const translated = new Set(Object.keys(task.checks.translatedLanguages || {}).map(normalizeLanguage).filter(Boolean));
  const knownSources = hasDeveloperOverride ? sourceLanguages : intersect(sourceLanguages, known);
  if (knownSources.size === 0) return emptyTranslationTargets();

  const newTargets = intersect(difference(required, translated), known);
  const reviewThreshold = profile.translatorLevel - TRANSLATION_REVIEW_LEVEL_GAP;
  let reviewTargets = new Set();
  if (newTargets.size === 0) {
    reviewTargets = new Set(
      Object.entries(task.checks.translatedLanguages || {})
        .filter(([language]) => known.has(normalizeLanguage(language)))
        .filter(([language]) => !sourceLanguages.has(normalizeLanguage(language)))
        .filter(([, level]) => numberValue(level, 0) <= reviewThreshold)
        .map(([language]) => normalizeLanguage(language)),
    );
  }
  return {
    sourceLanguages: knownSources,
    newTranslationLanguages: newTargets,
    reviewLanguages: reviewTargets,
  };
}

function openTasksFor(profile, task, config) {
  const result = new Set();
  if (!hasTestingResult(task.checks)) {
    result.add("TESTING");
  } else if (!hasLogicResult(task.checks)) {
    result.add("LOGIC");
  } else {
    const targets = translationTargets(profile, task, config);
    if (targets.newTranslationLanguages.size > 0) result.add("TRANSLATION");
    if (targets.reviewLanguages.size > 0) result.add("TRANSLATION_REVIEW");
  }
  return result;
}

function openTasksForDeveloper(task, config) {
  if (!hasTestingResult(task.checks)) return new Set(["TESTING"]);
  if (!hasLogicResult(task.checks)) return new Set(["LOGIC"]);
  const translated = new Set(Object.keys(task.checks.translatedLanguages || {}).map(normalizeLanguage));
  return Array.from(requiredLanguages(task, config)).some((language) => !translated.has(language))
    ? new Set(["TRANSLATION"])
    : new Set();
}

function isStageOpenForSubmit(task, kind, existingRecords, config) {
  switch (kind) {
    case "TESTING":
      return !hasLogicResult(task.checks) && !existingRecords.some((record) => record.acceptedByServer && record.kind === "LOGIC");
    case "LOGIC":
      return hasTestingResult(task.checks) && !existingRecords.some((record) => record.acceptedByServer && (record.kind === "TRANSLATION" || record.kind === "TRANSLATION_REVIEW"));
    case "TRANSLATION": {
      const translated = new Set(Object.keys(task.checks.translatedLanguages || {}).map(normalizeLanguage));
      return (
        isReadyForTranslation(task.checks) &&
        Array.from(requiredLanguages(task, config)).some((language) => !translated.has(language))
      );
    }
    case "TRANSLATION_REVIEW":
      return isReadyForTranslation(task.checks);
    default:
      return false;
  }
}

function rebuildAggregate(task, records, config) {
  const accepted = records.filter((record) => record.acceptedByServer);
  const testing = scoreAggregate(accepted, "TESTING");
  const logic = scoreAggregate(accepted, "LOGIC");
  const translated = translatedLanguages(task, accepted);
  const required = requiredLanguages(task, config);
  const reviewedSegments = accepted
    .filter((record) => record.kind === "TRANSLATION_REVIEW")
    .flatMap((record) => record.segmentResults || []);
  const translationScore =
    reviewedSegments.length > 0
      ? Math.trunc((reviewedSegments.filter((segment) => segment.accepted).length * 100) / reviewedSegments.length)
      : null;
  return {
    checks: {
      isTested: testing.score !== null,
      testingScore: testing.score,
      isLogicReviewed: logic.score !== null,
      logicScore: logic.score,
      isTranslationReviewed: Array.from(required).every((language) => Object.prototype.hasOwnProperty.call(translated, language)),
      translationScore,
      translatedLanguages: translated,
    },
    reviewerDeltas: testing.reviewerDeltas.concat(logic.reviewerDeltas),
  };
}

function lessonReviewStatus(checks, task, config) {
  return lessonPassed(checks, task, config) ? "READY_FOR_PUBLICATION" : "UNDER_REVIEW";
}

function lessonPassed(checks, task, config) {
  const required = requiredLanguages(task, config);
  const translated = checks.translatedLanguages || {};
  const sourceLanguages = new Set(
    Array.from(task.sourceLanguages || []).map(normalizeLanguage).filter(Boolean),
  );
  return (
    hasTestingResult(checks) &&
    hasLogicResult(checks) &&
    Array.from(required).every((language) =>
      sourceLanguages.has(normalizeLanguage(language)) ||
      numberValue(translated[language], 0) >= REVIEW_PASS_LEVEL
    )
  );
}

function scoreAggregate(records, kind) {
  const scored = records.filter((record) => record.kind === kind && record.score !== null && record.score !== undefined);
  if (scored.length === 0) return {score: null, reviewerDeltas: []};

  const maxReviewerLevel = Math.max(...scored.map((record) => record.reviewerLevelAtSubmit));
  const activeThreshold = maxReviewerLevel - ACTIVE_LEVEL_WINDOW;
  const active = scored.filter((record) => record.reviewerLevelAtSubmit >= activeThreshold);
  const activeScore = active.reduce((sum, record) => sum + record.score, 0) / active.length;
  const activeStars = Math.min(3, Math.max(1, Math.round(activeScore)));
  const activeIds = new Set(active.map((record) => record.id));
  const reviewerDeltas = scored
    .filter((record) => !activeIds.has(record.id))
    .map((record) => {
      const diff = Math.abs(record.score - activeStars);
      const points = diff === 0 ? 3 : diff === 1 ? 0 : -3;
      return {reviewerUid: record.reviewerUid, points};
    });
  return {score: activeScore, reviewerDeltas};
}

function translatedLanguages(task, records) {
  const sourceQuestionCount = getSourceQuestionCount(task);
  const result = {};
  for (const [language, level] of Object.entries(task.checks.translatedLanguages || {}).sort()) {
    const normalized = normalizeLanguage(language);
    if (normalized) result[normalized] = numberValue(level, 0);
  }

  const byLanguage = new Map();
  for (const record of records) {
    if (record.kind !== "TRANSLATION" || !record.language) continue;
    const language = normalizeLanguage(record.language);
    if (!byLanguage.has(language)) byLanguage.set(language, []);
    byLanguage.get(language).push(record);
  }
  for (const [language, languageRecords] of byLanguage.entries()) {
    const completed = languageRecords
      .filter((record) => (record.translatedQuestions || []).length >= sourceQuestionCount)
      .sort((a, b) => b.reviewerLevelAtSubmit - a.reviewerLevelAtSubmit)[0];
    if (completed) result[language] = completed.reviewerLevelAtSubmit;
  }
  return result;
}

function requiredLanguages(task, config) {
  const configured = new Set(
    ((config && config.requiredLanguages) || []).map(normalizeLanguage).filter(Boolean),
  );
  return configured.size > 0 ? configured : new Set(Array.from(task.sourceLanguages).map(normalizeLanguage).filter(Boolean));
}

function actionToRecord(action, profile, task, config, existingRecords, now) {
  const reviewerLevel = levelFor(profile, action.kind);
  const language = action.language ? normalizeLanguage(action.language) : null;
  const reviewId = `${action.assignmentId}_${action.kind.toLowerCase()}_${now}_${profile.uid}`
    .replace(/[^A-Za-z0-9_.-]/g, "_");

  if (action.kind === "TESTING" || action.kind === "LOGIC") {
    if (action.score === null || action.score === undefined) {
      throw new HttpsError("invalid-argument", `${action.kind} requires score`);
    }
    return {
      id: reviewId,
      lessonId: action.lessonId,
      kind: action.kind,
      reviewerUid: profile.uid,
      reviewerLevelAtSubmit: reviewerLevel,
      score: action.score,
      createdAtMs: now,
      acceptedByServer: true,
      segmentResults: [],
      translatedQuestions: [],
    };
  }

  if (action.kind === "TRANSLATION") {
    if (!language) throw new HttpsError("invalid-argument", "TRANSLATION requires language");
    const targets = translationTargets(profile, task, config);
    if (!targets.newTranslationLanguages.has(language)) {
      throw new HttpsError("failed-precondition", `Language ${language} is not open for translation`);
    }
    if (!action.translatedQuestions || action.translatedQuestions.length === 0) {
      throw new HttpsError("invalid-argument", "TRANSLATION requires translatedQuestions");
    }
    const translatedQuestions = action.translatedQuestions.map((question) => ({
      ...question,
      id: translatedQuestionId(question.id, language),
      language,
      languageLevel: reviewerLevel,
    }));
    return {
      id: reviewId,
      lessonId: action.lessonId,
      kind: action.kind,
      reviewerUid: profile.uid,
      reviewerLevelAtSubmit: reviewerLevel,
      language,
      createdAtMs: now,
      acceptedByServer: true,
      segmentResults: [],
      translatedQuestions,
    };
  }

  if (action.kind === "TRANSLATION_REVIEW") {
    if (!language) throw new HttpsError("invalid-argument", "TRANSLATION_REVIEW requires language");
    if (!action.segmentResults || action.segmentResults.length === 0) {
      throw new HttpsError("invalid-argument", "TRANSLATION_REVIEW requires segmentResults");
    }
    const targets = translationTargets(profile, task, config);
    if (!targets.reviewLanguages.has(language)) {
      throw new HttpsError("failed-precondition", `Language ${language} is not open for translation review`);
    }
    const targetRecord =
      (action.targetReviewId && existingRecords.find((record) => record.id === action.targetReviewId)) ||
      existingRecords
        .filter((record) => record.kind === "TRANSLATION" && record.language === language)
        .sort((a, b) => b.createdAtMs - a.createdAtMs)[0];
    if (!targetRecord) throw new HttpsError("not-found", `Translation record for ${language} not found`);
    if (profile.translatorLevel < targetRecord.reviewerLevelAtSubmit + TRANSLATION_REVIEW_LEVEL_GAP) {
      throw new HttpsError(
        "permission-denied",
        `Reviewer level must be at least ${TRANSLATION_REVIEW_LEVEL_GAP} above translation level`,
      );
    }
    return {
      id: reviewId,
      lessonId: action.lessonId,
      kind: action.kind,
      reviewerUid: profile.uid,
      reviewerLevelAtSubmit: reviewerLevel,
      language,
      targetReviewId: targetRecord.id,
      createdAtMs: now,
      acceptedByServer: true,
      segmentResults: action.segmentResults,
      translatedQuestions: [],
    };
  }

  throw new HttpsError("invalid-argument", `Unknown review kind ${action.kind}`);
}

function translationReviewerDeltas(record, existingRecords) {
  if (record.kind !== "TRANSLATION_REVIEW") return [];
  const targetRecord = existingRecords.find((candidate) => candidate.id === record.targetReviewId);
  if (!targetRecord) return [];
  return (record.segmentResults || []).map((result) => ({
    reviewerUid: targetRecord.reviewerUid,
    points: result.accepted ? ACCEPTED_TRANSLATION_SEGMENT_POINTS : REJECTED_TRANSLATION_SEGMENT_POINTS,
  }));
}

function newReviewerDeltas(previous, current) {
  const remainingPrevious = new Map();
  for (const delta of previous) {
    const key = deltaKey(delta);
    remainingPrevious.set(key, (remainingPrevious.get(key) || 0) + 1);
  }
  return current.filter((delta) => {
    const key = deltaKey(delta);
    const count = remainingPrevious.get(key) || 0;
    if (count > 0) {
      if (count === 1) remainingPrevious.delete(key);
      else remainingPrevious.set(key, count - 1);
      return false;
    }
    return true;
  });
}

function deltaKey(delta) {
  return `${delta.reviewerUid}\u0000${delta.points}`;
}

async function readAdminReviewLessonTasksByIds(ids) {
  if (!ids || ids.size === 0) return [];
  const tasks = [];
  for (const assignmentId of ids) {
    const change = await db.doc(adminSyncChangePath(assignmentId)).get();
    const lessonId = change.exists ? stringValue((change.data() || {}).lessonId) : null;
    if (!lessonId) continue;
    const lesson = await db.doc(adminLessonPath(lessonId)).get();
    if (lesson.exists) tasks.push(await adminLessonSnapshotToTask(lesson));
  }
  return tasks;
}

async function readAdminReviewLessonTask(assignmentId) {
  const tasks = await readAdminReviewLessonTasksByIds(new Set([assignmentId]));
  return tasks[0] || null;
}

async function adminLessonSnapshotToTask(snapshot) {
  const data = snapshot.data() || {};
  const questId = stringValue(data.questId);
  const questionsSnapshot = await snapshot.ref
    .collection("quests")
    .doc(questId)
    .collection("questions")
    .get();
  const questions = questionsSnapshot.docs.map((doc) => normalizeQuestion(doc.data() || {}, doc.id));
  const checks = reviewToChecks(data.checks || {
    isTested: data.isTested,
    testingScore: data.testingScore,
    isLogicReviewed: data.isLogicReviewed,
    logicScore: data.logicScore,
    isTranslationReviewed: data.isTranslationReviewed,
    translationScore: data.translationScore,
    translatedLanguages: data.translatedLanguages,
  });
  return {
    id: `${stringValue(data.submissionId)}_${snapshot.id}`,
    submissionId: stringValue(data.submissionId),
    ownerUid: stringValue(data.ownerUid),
    catalogId: stringValue(data.catalogId),
    draftId: stringValue(data.draftId),
    questId,
    lessonId: snapshot.id,
    title: stringValue(data.title),
    targetShelf: targetShelfValue(data.targetShelf),
    status: stringValue(data.status, "UNDER_REVIEW"),
    createdAtMs: numberValue(data.createdAtMs, 0),
    changedAtMs: numberValue(data.changedAtMs, numberValue(data.createdAtMs, 0)),
    checks,
    questions,
    sourceLanguages: new Set(
      stringArray(data.sourceLanguages).length > 0
        ? stringArray(data.sourceLanguages).map(normalizeLanguage)
        : questions.map((question) => normalizeLanguage(question.language)),
    ),
  };
}

async function readReviewRecords(lessonId) {
  const snapshot = await db.doc(adminLessonPath(lessonId)).collection("reviews").get();
  return snapshot.docs.map((doc) => normalizeReviewRecord(doc.data() || {}, doc.id, lessonId));
}

async function readArenaReviewConfig() {
  const snapshot = await db.doc("configs/arena_review").get();
  if (!snapshot.exists) return null;
  const data = snapshot.data() || {};
  return {
    requiredLanguages: stringArray(data.requiredLanguages),
    updatedAtMs: numberValue(data.updatedAtMs, 0),
  };
}

async function requireProfile(uid) {
  const snapshot = await db.collection("profiles").doc(uid).get();
  if (!snapshot.exists) throw new HttpsError("not-found", `Trusted profile ${uid} not found`);
  const data = snapshot.data() || {};
  return {
    uid,
    testerLevel: numberValue(data.testerLevel, 0),
    adminLevel: numberValue(data.adminLevel, 0),
    translatorLevel: numberValue(data.translatorLevel, 0),
    developerLevel: numberValue(data.developerLevel, 0),
    knownLanguages: stringArray(data.knownLanguages),
  };
}

/** Verification is decided by admins, and by developers through their blanket access. */
async function requireVerifier(uid) {
  const profile = await requireProfile(uid);
  if (
    profile.adminLevel < QUALIFIED_LEVEL &&
    profile.developerLevel <= DEVELOPER_ALL_ACCESS_LEVEL
  ) {
    throw new HttpsError("permission-denied", "Admin or developer level is required");
  }
  return profile;
}

function translateVerificationError(body) {
  try {
    return body();
  } catch (error) {
    if (error instanceof VerificationError) throw new HttpsError("invalid-argument", error.message);
    throw error;
  }
}

async function addReviewerReputation(uid, points) {
  if (points === 0) return;
  const ref = db.collection("profiles").doc(uid);
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    const current = numberValue(snapshot.exists ? (snapshot.data() || {}).reviewReputation : 0, 0);
    transaction.set(ref, {reviewReputation: current + points}, {merge: true});
  });
}

function normalizeRequest(data, fallbackSubmissionId) {
  return {
    submissionId: stringValue(data.submissionId, fallbackSubmissionId),
    draftId: stringValue(data.draftId),
    ownerUid: stringValue(data.ownerUid),
    localRevision: numberValue(data.localRevision, 0),
    requestedAtMs: numberValue(data.requestedAtMs, 0),
    targetShelf: targetShelfValue(data.targetShelf),
    targetLessonIds: stringArray(data.targetLessonIds),
    status: stringValue(data.status),
    draft: {
      id: stringValue(data.draft && data.draft.id),
      catalogId: stringValue(data.draft && data.draft.catalogId),
      title: stringValue(data.draft && data.draft.title),
      description: nullableString(data.draft && data.draft.description),
      defaultLanguage: stringValue(data.draft && data.draft.defaultLanguage),
      defaultDifficulty: stringValue(data.draft && data.draft.defaultDifficulty),
      publicQuestId: nullableString(data.draft && data.draft.publicQuestId),
      createdAtMs: numberValue(data.draft && data.draft.createdAtMs, 0),
      updatedAtMs: numberValue(data.draft && data.draft.updatedAtMs, 0),
    },
    sections: listMaps(data.sections).map((item) => ({
      id: stringValue(item.id),
      draftId: stringValue(item.draftId),
      title: stringValue(item.title),
      order: numberValue(item.order, 0),
    })),
    themes: listMaps(data.themes).map((item) => ({
      id: stringValue(item.id),
      draftId: stringValue(item.draftId),
      sectionId: stringValue(item.sectionId),
      title: stringValue(item.title),
      order: numberValue(item.order, 0),
    })),
    lessons: listMaps(data.lessons).map((item) => ({
      id: stringValue(item.id),
      draftId: stringValue(item.draftId),
      themeId: stringValue(item.themeId),
      title: stringValue(item.title),
      order: numberValue(item.order, 0),
    })),
    questions: listMaps(data.questions).map((item) => normalizeQuestion(item)),
    review: reviewToChecks(data.review || {}),
  };
}

/**
 * Per-question answers that came with an attempt.
 *
 * Kept permissive on purpose: a malformed row is dropped rather than failing the whole attempt,
 * because these answers feed statistics, not rewards — losing one is far better than rejecting a
 * legitimately played lesson.
 */
function normalizeLessonAnswers(value) {
  return listMaps(value)
    .map((item) => ({
      questionId: stringValue(item.questionId),
      codeAnswerIndex: Math.max(0, numberValue(item.codeAnswerIndex, 0)),
      score: Math.max(0, Math.min(9, numberValue(item.score, 0))),
      answerPayload: stringValue(item.answerPayload),
      answeredAtMs: nonNegativeEventTime(item.answeredAtMs),
      durationMs: Math.max(0, numberValue(item.durationMs, 0)),
      wasTimeout: Boolean(item.wasTimeout),
    }))
    .filter((item) => item.questionId !== "");
}

function normalizeLessonResultAttemptEvent(data, authUid) {
  const userId = stringValue(data.userId, authUid);
  if (userId !== authUid) {
    throw new HttpsError("permission-denied", "Attempt userId must match authenticated uid");
  }
  const event = normalizeContentEvent(data, authUid);
  const attemptId = stringValue(data.attemptId);
  if (!attemptId) throw new HttpsError("invalid-argument", "attemptId is required");
  const percentScore = numberValue(data.percentScore, null);
  if (percentScore === null || percentScore < 0 || percentScore > 100) {
    throw new HttpsError("invalid-argument", "percentScore must be in 0..100");
  }
  const codeAnswer = stringValue(data.codeAnswer);
  if (!isWellFormedCodeAnswer(codeAnswer)) {
    throw new HttpsError("invalid-argument", "codeAnswer must contain digits only");
  }
  const difficulty = stringValue(data.difficulty, "EASY").toUpperCase();
  if (difficulty !== "EASY" && difficulty !== "HARD") {
    throw new HttpsError("invalid-argument", "difficulty must be EASY or HARD");
  }
  // The client always derives percentScore from codeAnswer (CompleteAttemptUseCase and
  // AbortAttemptUseCase are the only two paths), so an honest attempt always matches.
  // A mismatch means the payload was crafted: keep the event for analysis, pay nothing.
  const expectedPercentScore = recomputePercentScore(codeAnswer);
  const answers = normalizeLessonAnswers(data.answers);
  return {
    answers,
    ...event,
    attemptId,
    difficulty,
    codeAnswer,
    percentScore,
    expectedPercentScore,
    scoreVerified: expectedPercentScore === percentScore,
    completedAtMs: nonNegativeEventTime(data.completedAtMs),
    createdAtMs: nonNegativeEventTime(data.createdAtMs),
  };
}

function normalizeQuestRatingEvent(data, authUid) {
  const userId = stringValue(data.userId, authUid);
  if (userId !== authUid) {
    throw new HttpsError("permission-denied", "Rating userId must match authenticated uid");
  }
  const event = normalizeContentEvent(data, authUid);
  const ratingId = stringValue(data.ratingId);
  if (!ratingId) throw new HttpsError("invalid-argument", "ratingId is required");
  const rating = numberValue(data.rating, null);
  if (rating === null || rating < 1 || rating > 3) {
    throw new HttpsError("invalid-argument", "rating must be in 1..3");
  }
  return {
    ...event,
    ratingId,
    rating,
    ratedAtMs: nonNegativeEventTime(data.ratedAtMs),
    createdAtMs: nonNegativeEventTime(data.createdAtMs),
  };
}

function lessonResultReward(event) {
  const percent = Math.max(0, Math.min(numberValue(event.percentScore, 0), 100));
  const multiplier = stringValue(event.difficulty).toUpperCase() === "HARD" ? HARD_RESULT_REWARD_MULTIPLIER : 1;
  return {
    skillPoints: percent * multiplier,
    nolics: Math.floor(percent / NOLICS_PERCENT_STEP) * multiplier,
  };
}

function writeUserProgressDelta(transaction, uid, delta, now) {
  const userRef = db.collection("users").doc(uid);
  transaction.set(
    userRef,
    clean({
      uid,
      updatedAtMs: now,
      pointsSkill: admin.firestore.FieldValue.increment(delta.skillPoints),
      skillPoints: admin.firestore.FieldValue.increment(delta.skillPoints),
      pointsNolics: admin.firestore.FieldValue.increment(delta.nolics),
      nolics: admin.firestore.FieldValue.increment(delta.nolics),
    }),
    {merge: true},
  );
}

function generateGiftBoxReward(ownedTrophies) {
  const firstRoll = Math.random();
  const secondRoll = Math.random();

  if (firstRoll > 0.05) {
    return {
      type: "addNolics",
      amount: randomIntInclusive(500, 2000),
    };
  }

  if (secondRoll <= 0.95) {
    switch (randomIntInclusive(0, 2)) {
      case 0:
        return {
          type: "addNolics",
          amount: randomIntInclusive(7000, 13000),
        };
      case 1: {
        const goldAmount = randomIntInclusive(0, 1);
        if (goldAmount === 0) {
          return {
            type: "addNolics",
            amount: randomIntInclusive(7000, 13000),
          };
        }
        return {
          type: "addGold",
          amount: goldAmount,
        };
      }
      case 2:
        return {
          type: "datePremium",
          amount: 86400,
        };
      default:
        break;
    }
  }

  switch (randomIntInclusive(0, 4)) {
    case 0:
      return {
        type: "logo",
        amount: 1,
        itemName: GIFT_BOX_LOGO_NAMES[randomIntInclusive(0, GIFT_BOX_LOGO_NAMES.length - 1)],
      };
    case 1: {
      const trophyName = randomIntInclusive(0, 1) === 0 ? null : pickGiftBoxTrophy(ownedTrophies);
      // Either the roll went the other way or the collection is already complete. A box that
      // grants a badge somebody holds grants nothing, so it pays in gold instead.
      if (!trophyName) {
        return {
          type: "addGold",
          amount: randomIntInclusive(10, 20),
        };
      }
      return {
        type: "trophy",
        amount: 1,
        itemName: trophyName,
      };
    }
    case 2:
      return {
        type: "addGold",
        amount: randomIntInclusive(10, 20),
      };
    case 3:
      return {
        type: "datePremium",
        amount: 86400 * 30,
      };
    case 4:
      return {
        type: "addNolics",
        amount: randomIntInclusive(50000, 200000),
      };
    default:
      return {
        type: "addNolics",
        amount: 1000,
      };
  }
}

function giftBoxRewardUpdate(existingUser, reward, now) {
  const amount = Math.max(0, numberValue(reward.amount, 0));
  switch (reward.type) {
    case "addNolics":
      return {
        pointsNolics: admin.firestore.FieldValue.increment(amount),
        nolics: admin.firestore.FieldValue.increment(amount),
      };
    case "addGold":
      return {
        gold: admin.firestore.FieldValue.increment(amount),
      };
    case "datePremium": {
      const premiumDeltaMs = amount * 1000;
      const premiumBaseMs = Math.max(now, numberValue(existingUser.premiumUntilMs, 0));
      const premiumUntilMs = premiumBaseMs + premiumDeltaMs;
      return {
        hasPremium: premiumUntilMs > now,
        premiumUntilMs,
      };
    }
    case "logo": {
      const itemName = stringValue(reward.itemName);
      return itemName ? {ownedLogos: admin.firestore.FieldValue.arrayUnion(itemName)} : {};
    }
    case "trophy": {
      const trophyName = stringValue(reward.itemName);
      return trophyName ? {trophies: admin.firestore.FieldValue.arrayUnion(trophyName)} : {};
    }
    default:
      return {};
  }
}

function advanceGiftBoxState(existingUser, now) {
  const currentCount = nonNegativeNumber(existingUser.boxCount, existingUser.countBox);
  const currentStreak = nonNegativeNumber(existingUser.boxStreakDays, existingUser.countDayBox);
  const nextBoxAtMs = nonNegativeNumber(existingUser.nextBoxAtMs, existingUser.timeLastOpenBox);
  if (nextBoxAtMs === 0) {
    return {
      boxCount: currentCount,
      boxStreakDays: currentStreak,
      nextBoxAtMs: now + DAY_MS,
    };
  }
  if (nextBoxAtMs > now) {
    return {
      boxCount: currentCount,
      boxStreakDays: currentStreak,
      nextBoxAtMs,
    };
  }

  const nextStreak = currentStreak + 1;
  if (nextStreak >= GIFT_BOX_STREAK_TARGET_DAYS) {
    return {
      boxCount: currentCount + 1,
      boxStreakDays: nextStreak,
      nextBoxAtMs: now + DAY_MS,
    };
  }
  return {
    boxCount: currentCount,
    boxStreakDays: nextStreak,
    nextBoxAtMs: now + DAY_MS,
  };
}

function randomIntInclusive(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function normalizeContentEvent(data, authUid) {
  const scope = normalizeScope(data.scope);
  const ownerUid = nullableString(data.ownerUid);
  if (scope === PRIVATE_SCOPE && ownerUid !== authUid) {
    throw new HttpsError("permission-denied", "Private event ownerUid must match authenticated uid");
  }
  const catalogId = stringValue(data.catalogId);
  const questId = stringValue(data.questId);
  const sectionId = stringValue(data.sectionId);
  const themeId = stringValue(data.themeId);
  const lessonId = stringValue(data.lessonId);
  if (!catalogId || !questId || !sectionId || !themeId || !lessonId) {
    throw new HttpsError("invalid-argument", "catalogId, questId, sectionId, themeId, and lessonId are required");
  }
  return {
    userId: authUid,
    scope,
    ownerUid: scope === PRIVATE_SCOPE ? ownerUid : null,
    catalogId,
    questId,
    sectionId,
    themeId,
    lessonId,
    lessonVersion: Math.max(1, numberValue(data.lessonVersion, 1)),
    sourceShelf: stringValue(data.sourceShelf, scope === PRIVATE_SCOPE ? PRIVATE_SCOPE : "arena"),
  };
}

function normalizeDirtyQuestRating(data, fallbackContentKey, scope) {
  return {
    contentKey: stringValue(data.contentKey, fallbackContentKey),
    scope: normalizeScope(scope),
    ownerUid: nullableString(data.ownerUid),
    catalogId: stringValue(data.catalogId),
    questId: stringValue(data.questId),
    sourceShelf: stringValue(data.sourceShelf),
    changedAtMs: numberValue(data.changedAtMs, 0),
  };
}

function normalizeSubmitAction(data) {
  const score = data.score === null || data.score === undefined ? null : numberValue(data.score, null);
  if (score !== null && (score < 1 || score > 3)) {
    throw new HttpsError("invalid-argument", "score must be in 1..3");
  }
  return {
    assignmentId: stringValue(data.assignmentId),
    lessonId: stringValue(data.lessonId),
    kind: stringValue(data.kind),
    score,
    language: nullableString(data.language),
    targetReviewId: nullableString(data.targetReviewId),
    translatedQuestions: listMaps(data.translatedQuestions).map((item) => normalizeQuestion(item)),
    segmentResults: listMaps(data.segmentResults).map((item) => ({
      questionId: stringValue(item.questionId),
      segmentKey: stringValue(item.segmentKey),
      accepted: Boolean(item.accepted),
    })),
  };
}

function normalizeQuestion(data, fallbackId) {
  return {
    id: stringValue(data.id, fallbackId),
    draftId: stringValue(data.draftId),
    lessonId: stringValue(data.lessonId),
    type: stringValue(data.type),
    language: stringValue(data.language),
    languageLevel: numberValue(data.languageLevel, 0),
    difficulty: stringValue(data.difficulty),
    order: numberValue(data.order, 0),
    text: stringValue(data.text),
    imagePath: nullableString(data.imagePath),
    payload: stringValue(data.payload),
    updatedAtMs: numberValue(data.updatedAtMs, 0),
  };
}

function normalizeReviewRecord(data, fallbackId, fallbackLessonId) {
  return {
    id: stringValue(data.id, fallbackId),
    lessonId: stringValue(data.lessonId, fallbackLessonId),
    kind: stringValue(data.kind),
    reviewerUid: stringValue(data.reviewerUid),
    reviewerLevelAtSubmit: numberValue(data.reviewerLevelAtSubmit, 0),
    score: data.score === null || data.score === undefined ? null : numberValue(data.score, null),
    language: nullableString(data.language),
    targetReviewId: nullableString(data.targetReviewId),
    createdAtMs: numberValue(data.createdAtMs, 0),
    acceptedByServer: Boolean(data.acceptedByServer),
    segmentResults: listMaps(data.segmentResults).map((item) => ({
      questionId: stringValue(item.questionId),
      segmentKey: stringValue(item.segmentKey),
      accepted: Boolean(item.accepted),
    })),
    translatedQuestions: listMaps(data.translatedQuestions).map((item) => normalizeQuestion(item)),
  };
}

function reviewToChecks(review, fallbackLanguages) {
  const translated = languageLevels(review && review.translatedLanguages);
  return {
    isTested: Boolean(review && review.isTested),
    testingScore: nullableNumber(review && review.testingScore),
    isLogicReviewed: Boolean(review && review.isLogicReviewed),
    logicScore: nullableNumber(review && review.logicScore),
    isTranslationReviewed: Boolean(review && review.isTranslationReviewed),
    translationScore:
      review && review.translationScore !== null && review.translationScore !== undefined
        ? numberValue(review.translationScore, null)
        : null,
    translatedLanguages:
      Object.keys(translated).length > 0 ? translated : (fallbackLanguages || {}),
  };
}

function checksToCallableMap(checks) {
  return {
    isTested: Boolean(checks.isTested),
    testingScore: checks.testingScore === undefined ? null : checks.testingScore,
    isLogicReviewed: Boolean(checks.isLogicReviewed),
    logicScore: checks.logicScore === undefined ? null : checks.logicScore,
    isTranslationReviewed: Boolean(checks.isTranslationReviewed),
    translationScore: checks.translationScore === undefined ? null : checks.translationScore,
    translatedLanguages: checks.translatedLanguages || {},
  };
}

function questionToDocument(question) {
  return {
    id: question.id,
    draftId: question.draftId,
    lessonId: question.lessonId,
    type: question.type,
    language: question.language,
    languageLevel: question.languageLevel,
    difficulty: question.difficulty,
    order: question.order,
    text: question.text,
    imagePath: question.imagePath,
    payload: question.payload,
    updatedAtMs: question.updatedAtMs,
  };
}

function reviewRecordToDocument(record) {
  return clean({
    id: record.id,
    lessonId: record.lessonId,
    kind: record.kind,
    reviewerUid: record.reviewerUid,
    reviewerLevelAtSubmit: record.reviewerLevelAtSubmit,
    score: record.score,
    language: record.language,
    targetReviewId: record.targetReviewId,
    createdAtMs: record.createdAtMs,
    acceptedByServer: record.acceptedByServer,
    segmentResults: (record.segmentResults || []).map((item) => ({
      questionId: item.questionId,
      segmentKey: item.segmentKey,
      accepted: item.accepted,
    })),
    translatedQuestions: (record.translatedQuestions || []).map(questionToDocument),
  });
}

function mergeQuestions(current, translated) {
  const translatedIds = new Set(translated.map((question) => question.id));
  return current
    .filter((question) => !translatedIds.has(question.id))
    .concat(translated)
    .sort((a, b) =>
      stringCompare(a.lessonId, b.lessonId) ||
      stringCompare(a.language, b.language) ||
      numberValue(a.order, 0) - numberValue(b.order, 0),
    );
}

function translatedQuestionId(sourceQuestionId, language) {
  const suffix = `__${language}`;
  return sourceQuestionId.endsWith(suffix) ? sourceQuestionId : `${sourceQuestionId}${suffix}`;
}

function levelFor(profile, kind) {
  switch (kind) {
    case "TESTING":
      return Math.max(profile.testerLevel, profile.adminLevel, profile.developerLevel);
    case "LOGIC":
      return Math.max(profile.adminLevel, profile.developerLevel);
    case "TRANSLATION":
    case "TRANSLATION_REVIEW":
      return Math.max(profile.translatorLevel, profile.developerLevel);
    default:
      return 0;
  }
}

function questionLanguages(questions) {
  const levels = {};
  for (const question of questions) {
    const language = question.language;
    levels[language] = Math.max(levels[language] || 0, numberValue(question.languageLevel, 0));
  }
  return levels;
}

function getSourceQuestionCount(task) {
  const groups = {};
  for (const question of task.questions) {
    const language = normalizeLanguage(question.language);
    groups[language] = (groups[language] || 0) + 1;
  }
  const counts = Object.values(groups);
  return counts.length > 0 ? Math.max(...counts) : task.questions.length;
}

function availableLanguages(task) {
  const translatedLanguages = Object.keys(task.checks.translatedLanguages || {});
  if (translatedLanguages.length > 0) return new Set(translatedLanguages);
  return new Set(task.questions.map((question) => question.language));
}

function hasTestingResult(checks) {
  return Boolean(checks.isTested) || checks.testingScore !== null && checks.testingScore !== undefined;
}

function hasLogicResult(checks) {
  return Boolean(checks.isLogicReviewed) || checks.logicScore !== null && checks.logicScore !== undefined;
}

function isReadyForTranslation(checks) {
  return hasTestingResult(checks) && hasLogicResult(checks);
}

function hasTestingQualification(profile) {
  return profile.testerLevel >= QUALIFIED_LEVEL || profile.adminLevel >= QUALIFIED_LEVEL;
}

function hasLogicQualification(profile) {
  return profile.adminLevel >= QUALIFIED_LEVEL;
}

function emptyTranslationTargets() {
  return {
    sourceLanguages: new Set(),
    newTranslationLanguages: new Set(),
    reviewLanguages: new Set(),
  };
}

function requireAuthUid(request) {
  const uid = request.auth && request.auth.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Authenticated uid is required");
  return uid;
}

function firestoreDocumentId(value, fieldName) {
  const id = stringValue(value).trim();
  if (!id || id.includes("/")) {
    throw new HttpsError("invalid-argument", `${fieldName} must be a Firestore document id`);
  }
  return id;
}

function authProfileStatus(request) {
  const provider = request.auth &&
    request.auth.token &&
    request.auth.token.firebase &&
    request.auth.token.firebase.sign_in_provider;
  return provider && provider !== "anonymous" ? "REGISTERED" : "ANONYMOUS";
}

async function upsertUserProfile(uid, options) {
  const nicknamePolicy = await readNicknamePolicy();
  const userRef = db.collection("users").doc(uid);
  const profileRef = db.collection("profiles").doc(uid);
  return db.runTransaction(async (transaction) => {
    const userSnapshot = await transaction.get(userRef);
    const profileSnapshot = await transaction.get(profileRef);
    const existingUser = userSnapshot.exists ? userSnapshot.data() || {} : {};
    const existingProfile = profileSnapshot.exists ? profileSnapshot.data() || {} : {};
    const qualification = readQualification(existingProfile, existingUser);
    const status = profileStatus(options.authStatus, hasTrophy(existingUser.trophies, TROPHY_VERIFIED));
    const shouldReplaceNickname =
      options.allowNicknameUpgrade ||
      !userSnapshot.exists ||
      !stringValue(existingUser.nickname);
    const requestedNickname = shouldReplaceNickname ? options.nickname : stringValue(existingUser.nickname, options.nickname);
    const nicknameClaim = await resolveNicknameClaim(transaction, {
      uid,
      requestedNickname,
      currentNickname: stringValue(existingUser.nickname || existingProfile.nickname),
      allowGeneratedFallback: !options.allowNicknameUpgrade,
      policy: nicknamePolicy,
      now: options.now,
    });
    const nickname = nicknameClaim.nickname;
    const knownLanguages =
      options.knownLanguages.length > 0
        ? options.knownLanguages
        : stringArray(existingProfile.knownLanguages).concat(stringArray(existingUser.knownLanguages));
    const languages = normalizeLanguages(knownLanguages);
    const createdAtMs = numberValue(existingUser.createdAtMs || existingProfile.createdAtMs, options.now);
    const updatedAtMs = options.now;
    const boxState = advanceGiftBoxState(existingUser, options.now);
    const premiumUntilMs = nonNegativeNumber(existingUser.premiumUntilMs, 0);
    const publicProfile = {
      uid,
      nickname,
      avatarUrl: nullableString(existingUser.avatarUrl),
      status,
      knownLanguages: languages,
      createdAtMs,
      updatedAtMs,
      streakDays: numberValue(existingUser.streakDays, 0),
      stars: numberValue(existingUser.stars, 0),
      pointsNolics: numberValue(existingUser.pointsNolics, 0),
      standardHearts: numberValue(existingUser.standardHearts, 5),
      goldHearts: numberValue(existingUser.goldHearts, 0),
      gold: numberValue(existingUser.gold, 0),
      pointsSkill: numberValue(existingUser.pointsSkill, 0),
      boxCount: boxState.boxCount,
      boxStreakDays: boxState.boxStreakDays,
      nextBoxAtMs: boxState.nextBoxAtMs,
      premiumUntilMs,
      hasPremium: premiumUntilMs > options.now || Boolean(existingUser.hasPremium),
      trophies: trophyList(existingUser.trophies),
      ownedLogos: stringArray(existingUser.ownedLogos),
      sponsor: qualification.sponsorLevel,
      tester: qualification.testerLevel,
      translater: qualification.translatorLevel,
      moderator: qualification.moderatorLevel,
      admin: qualification.adminLevel,
      developer: qualification.developerLevel,
      qualifications: {
        sponsor: qualification.sponsorLevel,
        tester: qualification.testerLevel,
        translater: qualification.translatorLevel,
        moderator: qualification.moderatorLevel,
        admin: qualification.adminLevel,
        developer: qualification.developerLevel,
      },
    };
    const trustedProfile = {
      uid,
      nickname,
      status,
      knownLanguages: languages,
      createdAtMs,
      updatedAtMs,
      reviewReputation: numberValue(existingProfile.reviewReputation, 0),
      sponsorLevel: qualification.sponsorLevel,
      testerLevel: qualification.testerLevel,
      translatorLevel: qualification.translatorLevel,
      moderatorLevel: qualification.moderatorLevel,
      adminLevel: qualification.adminLevel,
      developerLevel: qualification.developerLevel,
    };
    if (nicknameClaim.releaseRef) {
      transaction.delete(nicknameClaim.releaseRef);
    }
    transaction.set(
      nicknameClaim.ref,
      {
        uid,
        nickname,
        canonical: nicknameClaim.canonical,
        createdAtMs: numberValue((nicknameClaim.snapshot.data() || {}).createdAtMs, updatedAtMs),
        updatedAtMs,
      },
      {merge: true},
    );
    transaction.set(userRef, publicProfile, {merge: true});
    transaction.set(profileRef, trustedProfile, {merge: true});
    return {uid, publicProfile, trustedProfile};
  });
}

async function readNicknamePolicy() {
  const snapshot = await db.doc(NICKNAME_POLICY_DOC).get();
  if (!snapshot.exists) {
    return {blockedWords: [], blockedSymbols: []};
  }
  const data = snapshot.data() || {};
  return {
    blockedWords: stringArray(data.blockedWords)
      .map(canonicalNickname)
      .filter(Boolean),
    blockedSymbols: stringArray(data.blockedSymbols)
      .map((symbol) => stringValue(symbol).normalize("NFKC"))
      .filter(Boolean),
  };
}

async function resolveNicknameClaim(transaction, options) {
  const candidates = options.allowGeneratedFallback
    ? generatedNicknameCandidates(options.uid, options.requestedNickname)
    : [options.requestedNickname];
  let lastPolicyError = null;

  for (const candidate of candidates) {
    let validated;
    try {
      validated = validateNicknameOrThrow(candidate, options.policy);
    } catch (error) {
      if (!options.allowGeneratedFallback) throw error;
      lastPolicyError = error;
      continue;
    }

    const ref = db.collection(NICKNAME_CLAIMS_COLLECTION).doc(nicknameClaimId(validated.canonical));
    const snapshot = await transaction.get(ref);
    const ownerUid = snapshot.exists ? stringValue((snapshot.data() || {}).uid) : "";
    if (!snapshot.exists || ownerUid === options.uid) {
      const releaseRef = await nicknameReleaseRef(transaction, {
        uid: options.uid,
        currentNickname: options.currentNickname,
        nextCanonical: validated.canonical,
      });
      return {
        ...validated,
        ref,
        snapshot,
        releaseRef,
      };
    }

    if (!options.allowGeneratedFallback) {
      throw new HttpsError("already-exists", "Nickname is already taken");
    }
  }

  if (lastPolicyError) throw lastPolicyError;
  throw new HttpsError("already-exists", "Could not allocate a unique nickname");
}

async function nicknameReleaseRef(transaction, options) {
  const currentCanonical = canonicalNickname(options.currentNickname);
  if (!currentCanonical || currentCanonical === options.nextCanonical) return null;
  const ref = db.collection(NICKNAME_CLAIMS_COLLECTION).doc(nicknameClaimId(currentCanonical));
  const snapshot = await transaction.get(ref);
  const ownerUid = snapshot.exists ? stringValue((snapshot.data() || {}).uid) : "";
  return ownerUid === options.uid ? ref : null;
}

function validateNicknameOrThrow(value, policy) {
  const nickname = sanitizeNickname(value, null);
  if (!nickname) {
    throw new HttpsError("invalid-argument", "Nickname must contain 3..24 supported characters");
  }
  const canonical = canonicalNickname(nickname);
  const normalizedNickname = nickname.normalize("NFKC");
  const blockedSymbol = policy.blockedSymbols.find((symbol) => symbol && normalizedNickname.includes(symbol));
  if (blockedSymbol) {
    throw new HttpsError("invalid-argument", "Nickname contains a blocked symbol");
  }

  const compactCanonical = canonical.replace(/\s+/g, "");
  const blockedWord = policy.blockedWords.find((word) => {
    const compactWord = word.replace(/\s+/g, "");
    return word && (canonical.includes(word) || (compactWord && compactCanonical.includes(compactWord)));
  });
  if (blockedWord) {
    throw new HttpsError("invalid-argument", "Nickname contains a blocked word");
  }
  return {
    nickname,
    canonical,
  };
}

function readQualification(profile, user) {
  return {
    sponsorLevel: nonNegativeNumber(profile.sponsorLevel, user.sponsor),
    testerLevel: nonNegativeNumber(profile.testerLevel, user.tester),
    translatorLevel: nonNegativeNumber(profile.translatorLevel, user.translater),
    moderatorLevel: nonNegativeNumber(profile.moderatorLevel, user.moderator),
    adminLevel: nonNegativeNumber(profile.adminLevel, user.admin),
    developerLevel: nonNegativeNumber(profile.developerLevel, user.developer),
  };
}

function nonNegativeNumber(primary, fallback) {
  return Math.max(0, numberValue(primary, numberValue(fallback, 0)));
}

/**
 * VALIDATED means a person was checked by another person — nothing else.
 *
 * It used to be granted to anyone holding a qualification, so a tester counted as verified without
 * anyone having looked at them, while somebody who passed a real check had no way to be marked. The
 * status is also no longer sticky: deriving it from the tick every time means it cannot outlive the
 * fact it stands for. Accounts that were validated by qualification alone lose the label and can
 * apply like everybody else.
 */
function profileStatus(authStatus, isVerified) {
  if (isVerified) return "VALIDATED";
  return authStatus === "REGISTERED" ? "REGISTERED" : "ANONYMOUS";
}

function profileResponse(result) {
  const user = result.publicProfile;
  const profile = result.trustedProfile;
  return {
    uid: result.uid,
    nickname: stringValue(user.nickname),
    status: stringValue(profile.status || user.status, "ANONYMOUS"),
    avatarUrl: nullableString(user.avatarUrl),
    knownLanguages: stringArray(profile.knownLanguages).length > 0
      ? stringArray(profile.knownLanguages)
      : stringArray(user.knownLanguages),
    createdAtMs: numberValue(user.createdAtMs || profile.createdAtMs, 0),
    updatedAtMs: numberValue(user.updatedAtMs || profile.updatedAtMs, 0),
    skillPoints: numberValue(user.pointsSkill, 0),
    gold: numberValue(user.gold, 0),
    nolics: numberValue(user.pointsNolics, 0),
    standardHearts: numberValue(user.standardHearts, 5),
    goldHearts: numberValue(user.goldHearts, 0),
    // Life points are reported already regenerated, so the client can show the real balance
    // without repeating the clock arithmetic. maxLifePoints lets it render the gauge.
    ...lifePointsSnapshot(user, Date.now()),
    boxCount: numberValue(user.boxCount, 0),
    boxStreakDays: numberValue(user.boxStreakDays, 0),
    nextBoxAtMs: numberValue(user.nextBoxAtMs, 0),
    premiumUntilMs: numberValue(user.premiumUntilMs, 0),
    trophies: trophyList(user.trophies),
    ownedLogos: stringArray(user.ownedLogos),
    qualification: {
      sponsorLevel: numberValue(profile.sponsorLevel, 0),
      testerLevel: numberValue(profile.testerLevel, 0),
      translatorLevel: numberValue(profile.translatorLevel, 0),
      moderatorLevel: numberValue(profile.moderatorLevel, 0),
      adminLevel: numberValue(profile.adminLevel, 0),
      developerLevel: numberValue(profile.developerLevel, 0),
    },
  };
}

/**
 * Current life balance for a stored user document, brought up to date at `nowMs`.
 * Read-only: it never writes back, so the value is regenerated again on the next read.
 */
function lifePointsSnapshot(user, nowMs) {
  const ceiling = maxLifePoints(
    Math.min(MAX_STANDARD_HEARTS, nonNegativeInteger(user.standardHearts, MAX_STANDARD_HEARTS)),
  );
  const life = regenerateLifePoints(
    numberValue(user.lifePoints, ceiling),
    numberValue(user.lifePointsUpdatedAtMs, nowMs),
    nowMs,
    ceiling,
  );
  return {
    lifePoints: life.points,
    maxLifePoints: ceiling,
    lifePointsUpdatedAtMs: life.updatedAtMs,
    lessonAttemptLifeCost: LESSON_ATTEMPT_LIFE_COST,
  };
}

function readEconomyBalance(user) {
  return {
    hasPremium: Boolean(user.hasPremium),
    streakDays: nonNegativeInteger(user.streakDays, 0),
    stars: nonNegativeInteger(user.stars, 0),
    nolics: nonNegativeInteger(user.pointsNolics, numberValue(user.nolics, 0)),
    standardHearts: Math.min(MAX_STANDARD_HEARTS, nonNegativeInteger(user.standardHearts, MAX_STANDARD_HEARTS)),
    goldHearts: Math.min(MAX_GOLD_HEARTS, nonNegativeInteger(user.goldHearts, 0)),
    gold: nonNegativeInteger(user.gold, 0),
  };
}

function applyShopPurchaseToBalance(balance, itemId) {
  switch (itemId) {
    case SHOP_ITEM_STANDARD_HEART_SLOT:
      return buyStandardHeart(balance);
    case SHOP_ITEM_GOLD_HEART:
      return buyGoldHeart(balance);
    default:
      throw new HttpsError("invalid-argument", `Unsupported shop item ${itemId}`);
  }
}

function buyStandardHeart(balance) {
  if (balance.standardHearts >= MAX_STANDARD_HEARTS) {
    throw new HttpsError("failed-precondition", "Standard hearts are already full");
  }
  const price = standardHeartSlotCost(balance.standardHearts);
  if (balance.nolics < price) {
    throw new HttpsError("failed-precondition", "Not enough nolics");
  }
  return {
    ...balance,
    nolics: balance.nolics - price,
    standardHearts: balance.standardHearts + 1,
  };
}

function buyGoldHeart(balance) {
  if (balance.goldHearts >= MAX_GOLD_HEARTS) {
    throw new HttpsError("failed-precondition", "Gold hearts are already full");
  }
  if (balance.gold < GOLD_HEART_COST) {
    throw new HttpsError("failed-precondition", "Not enough gold");
  }
  return {
    ...balance,
    gold: balance.gold - GOLD_HEART_COST,
    goldHearts: balance.goldHearts + 1,
  };
}

function standardHeartSlotCost(currentHearts) {
  const index = Math.min(Math.max(0, currentHearts), STANDARD_HEART_SLOT_COSTS.length - 1);
  return STANDARD_HEART_SLOT_COSTS[index];
}

function nonNegativeInteger(value, fallback) {
  return Math.trunc(Math.max(0, numberValue(value, fallback)));
}

function normalizeLanguages(value) {
  const languages = stringArray(value)
    .map(normalizeLanguage)
    .filter(Boolean);
  const unique = Array.from(new Set(languages)).slice(0, 8);
  return unique.length > 0 ? unique : ["ru"];
}

function generatedNicknameCandidates(uid, requestedNickname) {
  const candidates = [requestedNickname, generatedNickname(uid)];
  for (let attempt = 1; attempt <= NICKNAME_GENERATION_ATTEMPTS; attempt++) {
    candidates.push(generatedNickname(uid, attempt));
  }
  return Array.from(new Set(candidates.filter(Boolean)));
}

function generatedNickname(uid, attempt = 0) {
  const suffix = stringValue(uid)
    .replace(/[^a-zA-Z0-9]/g, "")
    .slice(-6)
    .toUpperCase();
  if (attempt <= 0) return `User${suffix || "000000"}`;
  const attemptSuffix = hashHex(`${uid}:${attempt}`).slice(0, 4).toUpperCase();
  return `User${suffix || "000000"}${attemptSuffix}`;
}

function sanitizeNickname(value, fallback) {
  const text = stringValue(value, fallback || "")
    .trim()
    .replace(/\s+/g, " ");
  if (text.length < 3 || text.length > 24) return fallback;
  if (/[\u0000-\u001F/\\]/.test(text)) return fallback;
  return text;
}

function canonicalNickname(value) {
  return stringValue(value)
    .normalize("NFKC")
    .trim()
    .replace(/\s+/g, " ")
    .toLowerCase();
}

function nicknameClaimId(canonicalNicknameValue) {
  return hashHex(canonicalNicknameValue);
}

function privateCatalogPath(ownerUid, catalogId) {
  return `private/${ownerUid}/catalogs/${catalogId}`;
}

function privateQuestPath(ownerUid, catalogId, questId) {
  return `${privateCatalogPath(ownerUid, catalogId)}/quests/${questId}`;
}

function privateSyncChangePath(ownerUid, catalogId, questId) {
  return `private/${ownerUid}/sync_changes/${catalogId}_${questId}`;
}

function publicCatalogSyncChangePath(catalogId, type, id) {
  return `catalogs/${catalogId}/sync_changes/${type}_${id}`;
}

function lessonContentSyncChangePath(lessonId, questionId) {
  return `lesson_content/${lessonId}/sync_changes/${questionId}`;
}

function privateSectionPath(ownerUid, catalogId, questId, sectionId) {
  return `${privateQuestPath(ownerUid, catalogId, questId)}/sections/${sectionId}`;
}

function privateThemePath(ownerUid, catalogId, questId, sectionId, themeId) {
  return `${privateSectionPath(ownerUid, catalogId, questId, sectionId)}/themes/${themeId}`;
}

function privateLessonPath(ownerUid, catalogId, questId, sectionId, themeId, lessonId) {
  return `${privateThemePath(ownerUid, catalogId, questId, sectionId, themeId)}/lessons/${lessonId}`;
}

function privateQuestionPath(ownerUid, catalogId, questId, sectionId, themeId, lessonId, questionId) {
  return `${privateLessonPath(ownerUid, catalogId, questId, sectionId, themeId, lessonId)}/questions/${questionId}`;
}

function adminLessonPath(lessonId) {
  return `admin/review/lessons/${lessonId}`;
}

function adminQuestPath(lessonId, questId) {
  return `${adminLessonPath(lessonId)}/quests/${questId}`;
}

function adminQuestionPath(lessonId, questId, questionId) {
  return `${adminQuestPath(lessonId, questId)}/questions/${questionId}`;
}

function adminReviewRecordPath(lessonId, reviewId) {
  return `${adminLessonPath(lessonId)}/reviews/${reviewId}`;
}

function adminSyncChangePath(changeId) {
  return `admin/review/sync_changes/${changeId}`;
}

function clean(value) {
  if (Array.isArray(value)) return value.map(clean);
  if (value && typeof value === "object") {
    if (isFirestoreNativeValue(value)) return value;
    return Object.fromEntries(
      Object.entries(value)
        .filter(([, item]) => item !== undefined)
        .map(([key, item]) => [key, clean(item)]),
    );
  }
  return value;
}

function isFirestoreNativeValue(value) {
  const firestore = admin.firestore;
  if (isInstanceOf(value, Date)) return true;
  if (isInstanceOf(value, firestore.Timestamp)) return true;
  if (isInstanceOf(value, firestore.GeoPoint)) return true;
  if (isInstanceOf(value, firestore.Bytes)) return true;
  const constructorName = value.constructor && value.constructor.name;
  return typeof value.isEqual === "function" &&
    typeof constructorName === "string" &&
    constructorName.endsWith("Transform");
}

function isInstanceOf(value, constructor) {
  return typeof constructor === "function" && value instanceof constructor;
}

function indexBy(items, field) {
  return Object.fromEntries((items || []).map((item) => [item[field], item]));
}

function intersect(a, b) {
  return new Set(Array.from(a).filter((item) => b.has(item)));
}

function difference(a, b) {
  return new Set(Array.from(a).filter((item) => !b.has(item)));
}

function scopedCollection(base, scope) {
  return `${base}_${normalizeScope(scope)}`;
}

function normalizeScope(value) {
  return stringValue(value, PUBLIC_SCOPE) === PRIVATE_SCOPE ? PRIVATE_SCOPE : PUBLIC_SCOPE;
}

function contentKeysForEvent(event) {
  return {
    questContentKey: questContentKey(event.scope, event.ownerUid, event.catalogId, event.questId),
    lessonContentKey: lessonContentKey(event.scope, event.ownerUid, event.catalogId, event.questId, event.lessonId),
  };
}

function questContentKey(scope, ownerUid, catalogId, questId) {
  return hashHex([normalizeScope(scope), ownerUid || "", catalogId, questId].join("\u0000"));
}

function lessonContentKey(scope, ownerUid, catalogId, questId, lessonId) {
  return hashHex([normalizeScope(scope), ownerUid || "", catalogId, questId, lessonId].join("\u0000"));
}

/**
 * Shard key for event buckets. It must depend on the stable id ONLY: it used to include the
 * client-supplied date, so the same attemptId replayed with another completedAtMs landed in a
 * different bucket, looked new, and was rewarded again. These collections are write-only —
 * nothing reads them back by date — so hashing the id alone is safe and keeps dedup global.
 */
function eventBucketId(stableId) {
  return hashHex(stableId).slice(0, 2);
}

function hashHex(value) {
  return crypto.createHash("sha256").update(stringValue(value)).digest("hex");
}

function safeDocId(value) {
  return hashHex(stringValue(value)).slice(0, 12) + "_" + stringValue(value)
    .replace(/[^A-Za-z0-9_.-]/g, "_")
    .slice(0, 80);
}

function nonNegativeEventTime(value) {
  return Math.max(0, numberValue(value, Date.now()));
}

function stringArray(value) {
  return Array.isArray(value)
    ? value.map((item) => stringValue(item)).filter(Boolean)
    : [];
}

function targetShelfValue(value) {
  const shelf = stringValue(value, "arena");
  return shelf === "archive" ? "archive" : "arena";
}

function publicQuestShelfValue(value) {
  const rawShelf = stringValue(value).trim();
  const normalizedShelf = rawShelf.toLowerCase();
  const shelf = normalizedShelf === "tournamentfinal" ? "tournamentFinal" : normalizedShelf;
  if (!PUBLIC_QUEST_SHELVES.has(shelf)) {
    throw new HttpsError("invalid-argument", `Unsupported targetShelf ${shelf || "<empty>"}`);
  }
  return shelf;
}

function tournamentIdValue(value) {
  const raw = stringValue(value).trim();
  const normalized = raw.toLowerCase();
  const tournamentId = normalized === "tournamentfinal" || normalized === "world" || normalized === "worldchampionship"
    ? "tournamentFinal"
    : normalized === "qualifier" || normalized === "qualification"
      ? "tournament"
      : publicQuestShelfValue(raw);
  if (tournamentId !== "tournament" && tournamentId !== "tournamentFinal") {
    throw new HttpsError("invalid-argument", `Unsupported tournamentId ${raw || "<empty>"}`);
  }
  return tournamentId;
}

function listMaps(value) {
  return Array.isArray(value)
    ? value.filter((item) => item && typeof item === "object")
    : [];
}

function languageLevels(value) {
  if (!value || typeof value !== "object") return {};
  return Object.fromEntries(
    Object.entries(value)
      .map(([language, level]) => [stringValue(language), numberValue(level, null)])
      .filter(([language, level]) => language && level !== null),
  );
}

function stringValue(value, fallback = "") {
  if (value === null || value === undefined) return fallback;
  const text = String(value);
  return text.length > 0 ? text : fallback;
}

function nullableString(value) {
  if (value === null || value === undefined) return null;
  const text = String(value);
  return text.length > 0 ? text : null;
}

function numberValue(value, fallback) {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (value && typeof value.toNumber === "function") return value.toNumber();
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function nullableNumber(value) {
  if (value === null || value === undefined) return null;
  return numberValue(value, null);
}

function firstDefined(...values) {
  return values.find((value) => value !== undefined && value !== null);
}

function normalizeLanguage(language) {
  return stringValue(language).trim().toLowerCase();
}

function stringCompare(a, b) {
  return stringValue(a).localeCompare(stringValue(b));
}

function errorMessage(error) {
  return error && error.message ? error.message : String(error);
}
