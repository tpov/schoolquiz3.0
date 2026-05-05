"use strict";

const admin = require("firebase-admin");
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {onDocumentCreated} = require("firebase-functions/v2/firestore");

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

  return {
    recordId: record.id,
    aggregate: checksToCallableMap(aggregation.checks),
    reviewerDeltas,
  };
});

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
  if (!isReadyForTranslation(task.checks) || profile.translatorLevel < QUALIFIED_LEVEL) {
    return emptyTranslationTargets();
  }
  const known = new Set(profile.knownLanguages.map(normalizeLanguage).filter(Boolean));
  const sourceLanguages = new Set(
    Array.from(task.sourceLanguages).map(normalizeLanguage).filter(Boolean),
  );
  const translated = new Set(Object.keys(task.checks.translatedLanguages || {}).map(normalizeLanguage).filter(Boolean));
  const knownSources = intersect(sourceLanguages, known);
  if (knownSources.size === 0) return emptyTranslationTargets();

  const required = requiredLanguages(task, config);
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

function privateCatalogPath(ownerUid, catalogId) {
  return `private/${ownerUid}/catalogs/${catalogId}`;
}

function privateQuestPath(ownerUid, catalogId, questId) {
  return `${privateCatalogPath(ownerUid, catalogId)}/quests/${questId}`;
}

function privateSyncChangePath(ownerUid, catalogId, questId) {
  return `private/${ownerUid}/sync_changes/${catalogId}_${questId}`;
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
    return Object.fromEntries(
      Object.entries(value)
        .filter(([, item]) => item !== undefined)
        .map(([key, item]) => [key, clean(item)]),
    );
  }
  return value;
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

function stringArray(value) {
  return Array.isArray(value)
    ? value.map((item) => stringValue(item)).filter(Boolean)
    : [];
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

function normalizeLanguage(language) {
  return stringValue(language).trim().toLowerCase();
}

function stringCompare(a, b) {
  return stringValue(a).localeCompare(stringValue(b));
}

function errorMessage(error) {
  return error && error.message ? error.message : String(error);
}
