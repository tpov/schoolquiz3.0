"use strict";

const admin = require("firebase-admin");
// The Emulator Suite may bind the namespaced accessor, losing its static classes. Resolve
// primitives independently in that environment; injected test namespaces retain their types.
const firestoreTypes = admin.firestore.Timestamp ? admin.firestore : require("firebase-admin/firestore");
const crypto = require("crypto");
const https = require("https");
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {onDocumentCreated} = require("firebase-functions/v2/firestore");
const {onMessagePublished} = require("firebase-functions/v2/pubsub");
const {onSchedule} = require("firebase-functions/v2/scheduler");
const {onRequest} = require("firebase-functions/v2/https");
const {defineSecret} = require("firebase-functions/params");
const {trophyList, hasTrophy, TROPHY_VERIFIED} = require("./trophies.js");
const {logoImageUrl, isLogoName, logoImageBytes} = require("./logos.js");
const {
  describeNickname,
  sanitizeNickname,
  canonicalNickname,
  MIN_LISTING_PRICE,
  nicknameAskingPrice,
  nicknameTier,
  nicknamePricing,
  splitSalePrice,
} = require("./nicknames.js");
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
  attemptActivityCounts,
} = require("./result-verification");
const {
  UNLOCK_LESSON,
  UNLOCK_HARD_MODE,
  unlockPrice,
  spendNolics,
  unlockDocId,
  readUnlocks,
  withUnlock,
} = require("./lesson-unlocks");
const {
  ECONOMY_CONSTANTS_DOC,
  POINTS_PER_CHARGE,
  activityPrice,
  clientEconomyConstants,
  readEconomyConstants,
  regenMsFor,
  slotPrice,
} = require("./economy-constants");
const {
  SETTLEMENT_OUTCOME_REFUSE,
  SETTLEMENT_OUTCOME_UNAVAILABLE,
  settlePurchase,
  settleSubscriptionRenewal,
} = require("./purchase-settlement");
const {clawBackPurchase} = require("./purchase-clawback");
const {decodeNotification, readSubscriptionRenewal} = require("./purchase-notifications");
const {applyBoxOperation, BOX_KIND} = require("./box-service");
const {readBoxState} = require("./gift-boxes");
const {GIFT_BOX_LOGO_NAMES} = require("./gift-box-rewards");
const {ADMOB_VERIFIER_KEYS_URL, adRewardQueryFromUrl} = require("./ad-reward-verification");
const {applyAdReward, AD_REWARD_OUTCOME_ACKNOWLEDGED} = require("./ad-reward-progress");
const {
  readPremiumEconomy, planPremiumEconomy, writePremiumEconomy,
  PREMIUM_CATCHUP, PREMIUM_CATCHUP_PASSES,
} = require("./premium-economy");
const {purchasePremiumWithGold} = require("./premium-shop");
const {placeOfferBid, offerAuctionView} = require("./offer-auction");
const {settleDueOfferAuctions} = require("./offer-auction-settlement");
const {sweepEconomySeasons} = require("./economy-season");
const {referralFields, referralRegistration} = require("./referral-registration");
const {decideOfferAdReview, offersScreenView} = require("./offer-ad-review");
const {createPlayDeveloperApi} = require("./play-developer-api");
const {
  PUSH_EVENTS,
  PushError,
  detachDeviceToken,
  sendPushToUser,
} = require("./push-notifications");
const {activityKindForQuest, sourceShelfForQuest} = require("./activity-kind");
const {createArenaService} = require("./arena-service");
const {createArenaSeasonWorker, FUNCTION_TIMEOUT_MS: ARENA_SEASON_FUNCTION_TIMEOUT_MS} = require("./arena-season-worker");
const {createArenaEntryRuntime} = require("./arena-entry-runtime");
const {createArenaTicketRecovery} = require("./arena-ticket-recovery");
const {createArenaLessonCatalog} = require("./arena-lesson-catalog");
const {
  createArenaLifecycleViews, ArenaLifecycleViewError, MAX_AUTHOR_REJECTION_REASON_BYTES,
} = require("./arena-lifecycle-views");
const {createInboxViews, InboxViewError} = require("./inbox-views");
const {createPollViews, PollViewError} = require("./poll-views");
const {createCourseTournamentController} = require("./course-tournament-controller");
const {createTournamentWorker, FUNCTION_TIMEOUT_MS} = require("./tournament-worker");
const {
  CURRENCY,
  LEDGER_PATH,
  RECONCILIATION_PATH,
  REASON,
  SWEPT_CURRENCIES,
  isReportable,
  ledgerEntries,
  reconcile,
  reconciliationRecord,
} = require("./currency-ledger");
const {
  OVERSPEND_AUDIT_PATH,
  askedOrder,
  countClaims,
  noClaims,
  overspendRecord,
  overspendVerdict,
  settleClaims,
} = require("./charge-claims");
const {
  TENTHS_PER_NOLIC,
  buildReviewUnits,
  buildSettlementManifest,
  normalizeReviewFeeTariffs,
  reviewFeeNolics,
  settleReviewFeeTenths,
  walletCreditForEntitlement,
} = require("./review-fee");
const {parseReviewFeeQuoteRequest, quoteReviewFee} = require("./review-fee-quote");
const {ADMIN_REVIEW_SYNC_CHANGES, writeSyncChanges} = require("./sync-changes");
const {readReviewAssignmentChanges, resolveReviewAssignments} = require("./review-journal");
const {readReviewDecisions, writeReviewDecisionMark} = require("./review-decisions");
const {
  planHierarchyPrune,
  planPrivateHierarchyPrune,
  writePrivateHierarchyPruneToBatch,
} = require("./hierarchy-prune");
const logger = require("firebase-functions/logger");
const {isStorableDocumentId, keyDocumentPath, questionKeyDocuments} = require("./question-key-store");
// Приём прохождений: чтение тела, сборка пула, оценка и правило оплаты. Все четыре — чистые,
// без `firebase-admin`; всё, что читает Firestore, живёт здесь (`readLessonScoringSources`).
const {
  PAYMENT_RULE,
  SCORING_AUTHORITY,
  readSubmittedAttempt,
  withServerScore,
} = require("./attempt-intake");
const {buildScoringPool} = require("./scoring-pool");
const {FAULT, scoreAttempt} = require("./attempt-scoring");
const {STATUS: ATTEMPT_STATUS, attemptFingerprint, attemptReceipt, replayFault} = require("./attempt-receipt");
const {lessonAllocatedSeconds, lessonPoolSize, attemptReward} = require("./lesson-reward");
const {normalizeSettings, startPrivateQuizAttempt} = require("./private-quiz-gate");
const {
  ATTEMPT_STATUS: PRIVATE_RUN_STATUS,
  attemptDeadlineMs,
  finishAttempt,
  projectAuthorStatistics,
  projectOwnResult,
  readSubmission,
} = require("./private-quiz-run");
const {redact, translateSubmittedAnswer} = require("./question-redaction");
// Онлайн-переход next-question по ONLINE-O2 (история 2.6): чистые вердикты окна; Firestore и часы
// подводят обёртки ниже.
const {
  WINDOW_OPEN,
  WINDOW_EXPIRED,
  deliveryUpdate,
  deliveryVerdict,
  expiryUpdate,
  openQuestionWindow,
  readyAckVerdict,
  sameWindowIntent,
  windowDocumentId,
} = require("./lesson-question-window");
const {
  maxLifePoints,
  spendLifePoints,
} = require("./life-points");

// Контрактные тесты приёмника инициализируют приложение сами — с projectId эмулятора и до
// загрузки модуля; повторная инициализация здесь упала бы на уже существующем приложении.
// Сюиты с инъекцией подставляют admin-заглушку без поля `apps` — ей нужен безусловный вызов,
// поэтому пустота apps проверяется только там, где о ней есть что читать.
if (!admin.apps || admin.apps.length === 0) admin.initializeApp();
const db = admin.firestore();
db.settings({ignoreUndefinedProperties: true});
const REGION = "us-central1";
/**
 * Every function is capped at a handful of instances.
 *
 * Not a performance choice — a quota one. Cloud Run bills a project against the sum of
 * maxInstances x cpu across every service in a region, and the default cap of 100 instances times
 * 28 functions asks for far more CPU than a project is granted. The result is not a slow app but a
 * dead one: deploys fail, and the functions that do deploy answer 429 "no available instance".
 *
 * Одного инстанса на функцию оказалось мало: квота считает не инстансы, а процессор, и по
 * целому ядру на функцию проект упёрся в потолок. Замеряно в консоли: предел в `us-central1` —
 * 20 000 милли-vCPU, занято 95,5 %, а функций 49. Отсюда отказы выкатки «Quota exceeded for total
 * allowable CPU per project per region»: восемь вызываемых функций так и остались недостижимыми,
 * включая приёмник очереди и настройки экономики.
 *
 * `cpu: "gcf_gen1"` возвращает тот объём процессора, что выделялся функциям первого поколения, —
 * для 256 МБ это примерно шестая часть ядра. Аппетит падает впятеро, и все 49 функций помещаются
 * в ту же квоту без обращения в поддержку.
 *
 * Цена: при доле ядра инстанс обслуживает **один** запрос за раз (одновременность больше единицы
 * при `cpu < 1` запрещена). При нынешней нагрузке — 221 вызов за неделю по данным консоли — это
 * незаметно. Когда нагрузка вырастет, правильный ход — просить увеличение квоты и возвращать
 * целое ядро, а не молча упираться в очередь.
 */
const FUNCTION_OPTIONS = {
  region: REGION,
  maxInstances: 1,
  cpu: "gcf_gen1",
  labels: {"schoolquiz-runtime": "node22"},
};
/**
 * Functions that move money get their own cap.
 *
 * `maxInstances` is per function, and one 2nd-gen instance serves up to 80 concurrent requests, so
 * the global 1 is not a bottleneck for anything this app sees today. It is still one instance:
 * when it is busy, cold-starting a second is not an option, and a purchase is the one call where
 * the player is watching the spinner while Play and Firestore take their turns. Two is headroom
 * for that call alone, and small enough to fit the region's CPU quota alongside everything else.
 */
const MONETARY_FUNCTION_OPTIONS = {...FUNCTION_OPTIONS, maxInstances: 2};
/**
 * Настройки для всего, что зовёт клиент.
 *
 * `invoker: "public"` здесь не про доверие вызывающему, а про достижимость. Право на вызов
 * Cloud Run выдаётся при СОЗДАНИИ службы; у функций, созданных недавно, оно по умолчанию не
 * выдаётся, а последующие выкатки идут как обновление и его не добавляют. Такая функция числится
 * в списке, но отвечает 403 HTML-страницей раньше, чем запрос доходит до кода, — а клиентский SDK
 * переводит это в `UNAUTHENTICATED`, и выглядит оно как беда со входом, хотя вход ни при чём.
 *
 * Так на боевом молча оказались недостижимы восемь вызываемых функций: приёмник очереди
 * `submitMutation`, из-за которого ни одна отложенная операция клиента никогда не доходила до
 * сервера, и `getEconomyConstants`, который валил синхронизацию целиком, среди них.
 *
 * Проверку личности это не ослабляет: каждая функция по-прежнему начинается с `requireAuthUid`,
 * а денежные — ещё и с `requireProfile`. Открыт только сам вход, ровно как у остальных
 * девятнадцати функций проекта, которые всё это время работали.
 *
 * Расписаниям и триггерам (`onSchedule`, `onDocumentCreated`) эти настройки НЕ передаются: их
 * вызывает не клиент, а Google под своей служебной личностью, и открывать их наружу было бы
 * настоящей дырой. Они остаются на [FUNCTION_OPTIONS].
 */
const CALLABLE_OPTIONS = {...FUNCTION_OPTIONS, invoker: "public"};
// Профиль сертификатов: последние выпуски по порядку заработка. Отбор «последнего
// заработанного» и список на экране укладываются в этот предел; история остаётся в базе.
const CERTIFICATE_PROFILE_READ_LIMIT = 25;
const MONETARY_CALLABLE_OPTIONS = {...MONETARY_FUNCTION_OPTIONS, invoker: "public"};
// Pagination anchors reference closed receipts. The token key is bound only to lifecycle readers.
const ARENA_LIFECYCLE_CURSOR_SECRET = defineSecret("ARENA_LIFECYCLE_CURSOR_SECRET");
const LIFECYCLE_CALLABLE_OPTIONS = {...CALLABLE_OPTIONS, secrets: [ARENA_LIFECYCLE_CURSOR_SECRET]};
/**
 * The Pub/Sub topic Play publishes real-time developer notifications to.
 *
 * A plain module constant, not a secret and not an environment variable: the name is half of a
 * deploy-time binding — the topic has to exist in this project under exactly this name, and Play
 * Console has to be pointed at it — so writing it anywhere but here would only hide which name the
 * function is waiting on. The topic and its subscription are created by the owner in the console;
 * nothing about it is confidential.
 */
const PLAY_RTDN_TOPIC = "play-rtdn";
const QUALIFIED_LEVEL = 100;
const DEVELOPER_ALL_ACCESS_LEVEL = 100;
const TRANSLATION_REVIEW_LEVEL_GAP = 100;
const ACTIVE_LEVEL_WINDOW = 100;
const ACCEPTED_TRANSLATION_SEGMENT_POINTS = 5;
const REJECTED_TRANSLATION_SEGMENT_POINTS = -1;
const REVIEW_PASS_LEVEL = 100;
// Review scores run 1..3; the pass mark lives in configs/arena_review.testingPassScore
// (default 2) so the owner can raise the bar without a deploy. One is a refusal, not a quiet
// approval.
const DAY_MS = 24 * 60 * 60 * 1000;
const PUBLIC_SCOPE = "public";
const PRIVATE_SCOPE = "private";
const MAX_RESULT_EVENTS_PER_CALL = 50;
const DIRTY_RATING_READ_BATCH = 400;
const DAILY_DIRTY_RATING_LIMIT = 5000;
// What a quest author earns from the stars players give them. It is a reputation, not a rank:
// it opens nothing, and no gate anywhere reads it.
//
// This used to be `developer` / `developerLevel` — the same field every privilege check in the
// product reads at `> 100`. At ten points per three-star rating, eleven ratings from eleven
// accounts made an author a developer, and anonymous sign-in makes those accounts free. The
// arithmetic below is unchanged and still exactly reversible; only its destination moved.
const AUTHOR_RATING_SCORE_FIELD = "authorRatingScore";
const QUEST_RATING_QUALIFICATION_POINTS_PER_STAR = 10;
const SHOP_ITEM_STANDARD_HEART_SLOT = "STANDARD_HEART_SLOT";
const SHOP_ITEM_GOLD_HEART = "GOLD_HEART";
const SHOP_ITEM_PREMIUM = "PREMIUM";
const MAX_STANDARD_HEARTS = 5;
const MAX_GOLD_HEARTS = 1;
const MAX_TOURNAMENT_GROUPS_PER_RECALC = 1000;
const MAX_TOURNAMENT_RESULTS_PER_GROUP = 1000;
const TOURNAMENT_LEADERBOARD_LIMIT = 100;
const TOURNAMENT_PARTICIPANT_LIMIT = 500;
const NICKNAME_CLAIMS_COLLECTION = "nickname_claims";
const NICKNAME_LISTINGS_COLLECTION = "nickname_listings";
const NICKNAME_POLICY_DOC = "configs/nickname_policy";
const LOGO_LISTINGS_COLLECTION = "logo_listings";
const NICKNAME_GENERATION_ATTEMPTS = 20;
// "archive" is a legitimate destination, not only a publication target: without it a course moved
// off the archive could never be put back, and the first mistaken move would be permanent.
const PUBLIC_QUEST_SHELVES = new Set(["home", "arena", "tournament", "tournamentFinal", "archive"]);

const arenaService = createArenaService({
  db,
  nowMs: () => Date.now(),
  loadLessonContext: loadArenaLessonContext,
  loadCompletion: async (transaction, {uid, lessonId, attemptId}) => {
    const ref = db.collection(scopedCollection("result_events", PUBLIC_SCOPE))
      .doc(eventBucketId(attemptId)).collection("events").doc(safeDocId(attemptId));
    const snapshot = await transaction.get(ref);
    const event = snapshot.exists ? snapshot.data() || {} : {};
    if (event.userId !== uid || event.lessonId !== lessonId || !event.settlementReceipt) return null;
    return {...event.settlementReceipt, activityKind: event.activityKind};
  },
  isCurator: isArenaCurator,
  writeLessonChange: (transaction, context, fields, now) => {
    const lessonChangeAtMs = Math.max(now, context.nextChangeAtMs);
    transaction.set(db.doc(`lessons/${context.lessonId}`),
      {...fields, lastModifiedAt: firestoreTypes.Timestamp.fromMillis(lessonChangeAtMs)}, {merge: true});
    writeSyncChanges(transaction, db, {kind: "lesson", id: context.lessonId, catalogId: context.catalogId}, lessonChangeAtMs);
  },
});

const arenaSeasonWorker = createArenaSeasonWorker({
  db, advanceFinalization: (execution) => arenaService.advanceSeasonFinalizationForQuarter(execution),
  log: (record) => logger.warn("Arena season sweep", record),
});

// A scheduler owns progress; the callable below can only reserve a season and read its small
// redacted status projection. It cannot choose a job ID, page, lease, cursor, or server clock.
exports.reconcileArenaSeasonFinalizations = onSchedule({...FUNCTION_OPTIONS,
  schedule: "every 1 minutes", timeZone: "UTC", timeoutSeconds: ARENA_SEASON_FUNCTION_TIMEOUT_MS / 1000,
  memory: "512MiB", maxInstances: 1, retryCount: 0,
}, async () => {
  const result = await arenaSeasonWorker.sweep();
  logger.info("Arena season sweep completed", {
    status: result.status, attempted: result.attempted, advanceCalls: result.advanceCalls, failed: result.failed,
    terminal: result.terminal, queryCount: result.queryCount, wrapped: result.wrapped,
    leaseReleased: result.leaseReleased,
  });
});

async function isArenaCurator(transaction, uid) {
  const snapshot = await transaction.get(db.doc(`profiles/${uid}`));
  const profile = snapshot.exists ? snapshot.data() || {} : {};
  return numberValue(profile.adminLevel, 0) >= QUALIFIED_LEVEL ||
    numberValue(profile.developerLevel, 0) > DEVELOPER_ALL_ACCESS_LEVEL;
}

const courseTournaments = createCourseTournamentController({
  db, loadLessonContext: loadArenaLessonContext, isCurator: isArenaCurator,
});
const tournamentWorker = createTournamentWorker({
  db, reconcileEvent: courseTournaments.reconcileEvent,
  log: (record) => logger.warn("Course tournament sweep", record),
});
// Server scheduler only; never reuse callable/App Check options or expose client-selected work.
exports.reconcileCourseTournaments = onSchedule({
  schedule: "every 1 minutes", timeZone: "UTC", timeoutSeconds: FUNCTION_TIMEOUT_MS / 1000,
  memory: "512MiB", maxInstances: 1, retryCount: 0,
}, async () => {
  const result = await tournamentWorker.sweep();
  logger.info("Course tournament sweep completed", {
    status: result.status, attempted: result.attempted, failed: result.failed,
    completedEvents: result.completedEvents, queryCount: result.queryCount,
    wrapped: result.wrapped, leaseReleased: result.leaseReleased,
  });
});
// Explicit capabilities only: no callable can select a controller method or invoke its worker.
exports.prepareCourseTournamentBank = onCall(CALLABLE_OPTIONS, (request) =>
  callCourseTournament(request, (uid, payload) => courseTournaments.prepare(uid, payload)));
exports.submitCourseTournamentOperation = onCall(MONETARY_CALLABLE_OPTIONS, (request) =>
  callCourseTournament(request, (uid, payload) => courseTournaments.operate(uid, payload)));
exports.submitCourseTournamentSession = onCall(CALLABLE_OPTIONS, (request) =>
  callCourseTournament(request, (uid, payload) => courseTournaments.session(uid, payload)));
exports.fetchCourseTournamentEvent = onCall(CALLABLE_OPTIONS, (request) =>
  callCourseTournament(request, (uid, payload) => courseTournaments.readEvent(uid, payload)));
exports.fetchCourseTournamentSession = onCall(CALLABLE_OPTIONS, (request) =>
  callCourseTournament(request, (uid, payload) => courseTournaments.readSession(uid, payload)));
exports.fetchCourseTournamentMeeting = onCall(CALLABLE_OPTIONS, (request) =>
  callCourseTournament(request, (uid, payload) => courseTournaments.readMeeting(uid, payload)));
// Authorized discovery and the server-attested download record (story 5.3, UX-O4/access):
// both are ordinary allowlisted capabilities on the same controller — no dispatch surface.
exports.fetchCourseTournamentEvents = onCall(CALLABLE_OPTIONS, (request) =>
  callCourseTournament(request, (uid, payload) => courseTournaments.listEvents(uid, payload)));
exports.recordCourseTournamentDownload = onCall(CALLABLE_OPTIONS, (request) =>
  callCourseTournament(request, (uid, payload) => courseTournaments.recordDownload(uid, payload)));

async function callCourseTournament(request, action) {
  const uid = requireAuthUid(request);
  try {
    return await action(uid, request.data);
  } catch (error) {
    throw courseTournamentFailure(error);
  }
}

/** Отказ контроллера соревнования -> HttpsError со структурной причиной в деталях.
 * Клиент читает из деталей только `reason` (никогда текст сообщения), поэтому таблица
 * причина->код обязана оставаться одной: и прямой callable, и приёмник очереди отвечают ею.
 */
function courseTournamentFailure(error) {
  // Do not expose server documents, grading material or SDK internals through callable errors.
  const reason = typeof error.code === "string" && /^[A-Z][A-Z_]{0,63}$/.test(error.code) ? error.code : "SERVER_FAILURE";
  const code = reason === "INVALID_ARGUMENT" ? "invalid-argument" :
    reason === "UNAUTHENTICATED" ? "unauthenticated" : reason === "PERMISSION_DENIED" ? "permission-denied" :
    reason === "NOT_FOUND" ? "not-found" :
    ["ALREADY_EXISTS", "CONFLICTING_REPLAY", "PREPARATION_REUSE"].includes(reason) ? "already-exists" :
    reason === "SOURCE_TOO_LARGE" ? "resource-exhausted" :
    ["UNAVAILABLE", "SCORING_UNAVAILABLE"].includes(reason) || reason.startsWith("INCONSISTENT_") ? "unavailable" :
    reason === "SERVER_FAILURE" ? "internal" : "failed-precondition";
  if (code === "internal" || code === "unavailable") logger.warn("Course tournament operation needs retry or repair", {reason});
  return new HttpsError(code, "Tournament operation could not be completed", {reason});
}

const arenaEntries = createArenaEntryRuntime({db, loadLessonContext: loadArenaLessonContext});
const arenaLessonCatalog = createArenaLessonCatalog({db, loadLessonContext: loadArenaLessonContext});
const arenaLifecycleViews = createArenaLifecycleViews({
  db, loadLessonContext: loadArenaLifecycleContext, isCurator: isArenaCurator,
  readCursorKey: () => ARENA_LIFECYCLE_CURSOR_SECRET.value(),
});
const inboxViews = createInboxViews({db});
// Опросы (UX-O7, история 8.2): список серий и состояние вопроса — читающие view-коллблы;
// записи (публикация, голос) идут единственным приёмником мутаций (AD-6/AD-7).
const pollViews = createPollViews({db});
exports.listPollSeries = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  try {
    return await pollViews.list({uid, payload: request.data});
  } catch (error) {
    const reason = !request || !request.auth || !request.auth.uid ? "UNAUTHENTICATED" :
      error instanceof PollViewError ? error.code : "UNAVAILABLE";
    const code = reason === "UNAUTHENTICATED" ? "unauthenticated" :
      reason === "INVALID_ARGUMENT" ? "invalid-argument" :
        reason === "NOT_FOUND" ? "not-found" :
          reason === "SOURCE_TOO_LARGE" ? "resource-exhausted" : "unavailable";
    throw new HttpsError(code, "Poll series could not be loaded", {reason});
  }
});
exports.getPollQuestionState = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  try {
    return await pollViews.questionState({uid, payload: request.data});
  } catch (error) {
    const reason = !request || !request.auth || !request.auth.uid ? "UNAUTHENTICATED" :
      error instanceof PollViewError ? error.code : "UNAVAILABLE";
    const code = reason === "UNAUTHENTICATED" ? "unauthenticated" :
      reason === "INVALID_ARGUMENT" ? "invalid-argument" :
        reason === "NOT_FOUND" ? "not-found" :
          reason === "SOURCE_TOO_LARGE" ? "resource-exhausted" : "unavailable";
    throw new HttpsError(code, "Poll question state could not be loaded", {reason});
  }
});
exports.listArenaLessons = onCall(CALLABLE_OPTIONS, async (request) => {
  try {
    const uid = requireAuthUid(request);
    // Pass the strict payload intact; a body uid can never replace authenticated identity.
    return await arenaLessonCatalog.list({uid, payload: request.data});
  } catch (error) {
    const codes = {
      UNAUTHENTICATED: "unauthenticated", INVALID_ARGUMENT: "invalid-argument",
      CATALOG_CHANGED: "failed-precondition", CATALOG_TOO_LARGE: "resource-exhausted",
      CATALOG_INVALID: "unavailable",
    };
    const reason = !request?.auth?.uid ? "UNAUTHENTICATED" :
      Object.hasOwn(codes, error?.code) ? error.code : "SERVER_FAILURE";
    // Only fixed reasons cross this boundary, never SDK messages, paths or private data.
    throw new HttpsError(codes[reason] || "internal", "Arena lessons could not be loaded", {reason});
  }
});
exports.listOwnedLessonLifecycle = onCall(LIFECYCLE_CALLABLE_OPTIONS, (request) =>
  callArenaLifecycleView("listOwned", request));
exports.listCuratorLessonLifecycle = onCall(LIFECYCLE_CALLABLE_OPTIONS, (request) =>
  callArenaLifecycleView("listCurator", request));
exports.getLessonLifecycle = onCall(LIFECYCLE_CALLABLE_OPTIONS, (request) =>
  callArenaLifecycleView("get", request));
exports.listLessonLifecycleHistory = onCall(LIFECYCLE_CALLABLE_OPTIONS, (request) =>
  callArenaLifecycleView("listHistory", request));
exports.listMyArenaRoyalty = onCall(LIFECYCLE_CALLABLE_OPTIONS, (request) =>
  callArenaLifecycleView("listMyRoyalty", request));
// Собственная история операций и «Начисления» авторского урока (AD-30/UX-20, история 8.6):
// та же авторизованная view-семья TZ06B, тот же шифрованный курсор. Чтение не создаёт
// финансовых эффектов и не пишет в книгу (контракт ux-o10-own-ledger-projection-contract.md).
exports.listMyOperationHistory = onCall(LIFECYCLE_CALLABLE_OPTIONS, (request) =>
  callArenaLifecycleView("listMyHistory", request));
exports.listMyLessonEarnings = onCall(LIFECYCLE_CALLABLE_OPTIONS, (request) =>
  callArenaLifecycleView("listMyLessonEarnings", request));
exports.listMyQuestReviewSubmissions = onCall(LIFECYCLE_CALLABLE_OPTIONS, (request) =>
  callArenaLifecycleView("listMyQuestReviewSubmissions", request));
async function callArenaLifecycleView(method, request) {
  try {
    const uid = requireAuthUid(request);
    return await arenaLifecycleViews[method]({uid, payload: request.data});
  } catch (error) {
    const reason = !request || !request.auth || !request.auth.uid ? "UNAUTHENTICATED" :
      error instanceof ArenaLifecycleViewError ? error.code : "UNAVAILABLE";
    const code = reason === "UNAUTHENTICATED" ? "unauthenticated" :
      reason === "INVALID_ARGUMENT" ? "invalid-argument" :
        reason === "PERMISSION_DENIED" ? "permission-denied" :
          reason === "NOT_FOUND" ? "not-found" :
            reason === "SOURCE_TOO_LARGE" ? "resource-exhausted" : "unavailable";
    throw new HttpsError(code, "Lesson lifecycle could not be loaded", {reason});
  }
}
// Общий inbox (AD-20): автор читает адресованные ему приватные отчёты и комментарии к своим
// урокам. Проверка права живёт во вью (отбор по lessonAuthorUid/authorUid), ошибка — без деталей
// хранилища: наружу едет только поименованная причина.
exports.listInboxItems = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  try {
    return await inboxViews.list({uid, payload: request.data});
  } catch (error) {
    const reason = !request || !request.auth || !request.auth.uid ? "UNAUTHENTICATED" :
      error instanceof InboxViewError ? error.code : "UNAVAILABLE";
    const code = reason === "UNAUTHENTICATED" ? "unauthenticated" :
      reason === "INVALID_ARGUMENT" ? "invalid-argument" :
        reason === "SOURCE_TOO_LARGE" ? "resource-exhausted" : "unavailable";
    throw new HttpsError(code, "Inbox could not be loaded", {reason});
  }
});
const arenaTicketRecovery = createArenaTicketRecovery({db});
exports.listArenaEntryTickets = onCall(CALLABLE_OPTIONS, (request) => callArenaTicketRecovery("list", request));
exports.fetchArenaEntryTicket = onCall(CALLABLE_OPTIONS, (request) => callArenaTicketRecovery("get", request));
exports.fetchArenaEntryTicketStatus = onCall(CALLABLE_OPTIONS, (request) => callArenaTicketRecovery("status", request));
async function callArenaTicketRecovery(method, request) {
  const uid = requireAuthUid(request);
  try {
    return await arenaTicketRecovery[method]({uid, payload: request.data});
  } catch (error) {
    const reason = error.name === "ArenaTicketError" ? error.code : "UNAVAILABLE";
    const code = reason === "INVALID_ARGUMENT" ? "invalid-argument" :
      reason === "NOT_FOUND" ? "not-found" : "unavailable";
    throw new HttpsError(code, "Arena ticket recovery could not be completed", {reason});
  }
}
exports.prepareArenaEntry = onCall(CALLABLE_OPTIONS, (request) => callArenaEntry("issue", request));
exports.cancelArenaEntry = onCall(CALLABLE_OPTIONS, (request) => callArenaEntry("cancel", request));

// Старт попытки приватного квиза (AD-29): атомарная дверь ссылки. Отказы модуля — исходы двери
// (ссылка выключена/истекла/лимит) — участнику failed-precondition'ом, не «неизвестной».
exports.startPrivateQuizAttempt = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  await requireProfile(uid);
  const codes = {
    INVALID_ARGUMENT: "invalid-argument",
    NOT_FOUND: "not-found",
    LINK_DISABLED: "failed-precondition",
    LINK_EXPIRED: "failed-precondition",
    ATTEMPT_LIMIT_REACHED: "failed-precondition",
    INVALID_SETTINGS: "internal",
  };
  try {
    return await startPrivateQuizAttempt({db, uid, data: request.data || {}, now: Date.now()});
  } catch (error) {
    if (codes[error.code]) {
      // Машиночитаемый код двери едет в деталях: текст участнику, код — типизированному
      // состоянию входа на клиенте (AD-21). Разбор человеческого текста не контракт.
      throw new HttpsError(codes[error.code], error.message, {reason: error.code});
    }
    throw error;
  }
});

/*
 * Приватный квиз после двери (AD-29/AD-31, история 8.4).
 *
 * Одна дверь (`startPrivateQuizAttempt` выше), один прогон (ONLINE-O2 на клиенте), один
 * приём подачи (`private_quiz.SUBMIT_ATTEMPT` в реестре мутаций) и три чтения: замороженный
 * набор, собственный результат — по ТЕКУЩЕЙ настройке showResult, авторская статистика —
 * всегда. Никаких списаний и согласий: приватный проход бесплатен (8.5), и тайм-аут здесь —
 * терминальный результат, а не отложенное продолжение.
 */

/** Адрес попытки в теле читаемого вызова — только named-поля, владелец из авторизации не виден. */
function readRunAddress(body) {
  const ownerUid = stringValue(body.ownerUid);
  const catalogId = stringValue(body.catalogId);
  const questId = stringValue(body.questId);
  const startId = stringValue(body.startId);
  if (!ownerUid || !catalogId || !questId || !startId) {
    throw new HttpsError("invalid-argument", "ownerUid, catalogId, questId and startId are required");
  }
  return {ownerUid, catalogId, questId, startId};
}

/** Читает запись старта и проверяет, что читает её тот, кто стартовал. */
async function readOwnStart(uid, address) {
  const snapshot = await db.doc(startPath(address)).get();
  if (!snapshot.exists) throw new HttpsError("not-found", "Attempt start does not exist");
  const record = snapshot.data() || {};
  if (record.participantUid !== uid) {
    // Чужая попытка не отличима от несуществующей: подменённый в теле адресат чужой uid
    // не открывает (AD-20: серверная авторизация, а не прятанье в UI).
    throw new HttpsError("not-found", "Attempt start does not exist");
  }
  return record;
}

function startPath({ownerUid, catalogId, questId, startId}) {
  return privateQuestPath(ownerUid, catalogId, questId) + "/starts/" + startId;
}

/** Совпадает ли живой квест с тем, под которым открыт старт (замороженная идентичность, AD-6). */
function quizIdentityMatches(record, questData) {
  const version = Number.isSafeInteger(questData.version) ? questData.version : null;
  const submissionId = typeof questData.submissionId === "string" ? questData.submissionId : null;
  return record.quizVersion === version && record.quizSubmissionId === submissionId;
}

/** Порядок порции — (order, id), как его собирает клиентский прогон. */
function frozenOrder(left, right) {
  return numberValue(left.order, 0) - numberValue(right.order, 0) ||
    String(left.id).localeCompare(String(right.id));
}

/**
 * Вопрос замороженного набора в форме для прогона: ТОЛЬКО публичная половина payload'а.
 *
 * Вопросы приватного дерева несут свои ключи — участнику они не едут ни при каком флаге
 * (AD-29: ни UI hiding, ни «участник свой» не заменяют серверную фильтрацию). Ключ вынимает
 * редактор `question-redaction.js` — тот же, что красактирует публичные уроки.
 */
function redactPrivateRunQuestion(question) {
  const verdict = redact(
    typeof question.payload === "string" ? question.payload : JSON.stringify(question.payload === undefined ? null : question.payload),
    "HARD",
  );
  if (verdict.status === "redacted") {
    return {id: question.id, order: numberValue(question.order, 0), difficulty: "HARD", payload: verdict.publicPayload};
  }
  if (verdict.status === "already-redacted" || verdict.status === "not-applicable") {
    return {id: question.id, order: numberValue(question.order, 0), difficulty: "HARD", payload: question.payload};
  }
  // Нечитаемый вопрос замороженного набора — наша дыра: молча отдать его с ключом хуже
  // громкого отказа прогона.
  throw new HttpsError("internal", "RUN_QUESTION_UNREADABLE: a frozen question could not be redacted");
}

/** Вопросы замороженного набора, обычным чтением — для выдачи прогона. */
async function collectFrozenQuestions(ownerUid, catalogId, questId) {
  const frozen = [];
  const sections = await db.collection(privateQuestPath(ownerUid, catalogId, questId) + "/sections").get();
  for (const section of sections.docs || []) {
    const themes = await section.ref.collection("themes").get();
    for (const theme of themes.docs || []) {
      const lessons = await theme.ref.collection("lessons").get();
      for (const lesson of lessons.docs || []) {
        const questionSnaps = await lesson.ref.collection("questions").get();
        for (const question of questionSnaps.docs || []) {
          frozen.push({id: question.id, ...question.data()});
        }
      }
    }
  }
  return frozen.sort(frozenOrder);
}

/** То же внутри транзакции — для решений, которые пишут (ленивый терминал, приём подачи). */
async function collectFrozenQuestionsInTransaction(transaction, ownerUid, catalogId, questId) {
  const frozen = [];
  const sections = await transaction.get(
    db.collection(privateQuestPath(ownerUid, catalogId, questId) + "/sections"),
  );
  for (const section of sections.docs || []) {
    const themes = await transaction.get(section.ref.collection("themes"));
    for (const theme of themes.docs || []) {
      const lessons = await transaction.get(theme.ref.collection("lessons"));
      for (const lesson of lessons.docs || []) {
        const questionSnaps = await transaction.get(lesson.ref.collection("questions"));
        for (const question of questionSnaps.docs || []) {
          frozen.push({id: question.id, ...question.data()});
        }
      }
    }
  }
  return frozen.sort(frozenOrder);
}

/**
 * Граница читаемых вызовов прогона: наружу едет одно фиксированное сообщение и машиночитаемый
 * reason в деталях — тот же забор, что у всей семьи TZ06B (AD-20/AD-15: внутренние тексты и
 * формы через границу вызываемых не путешествуют).
 */
function runBoundaryFailure(error) {
  if (error instanceof HttpsError) {
    const reason =
      (error.details && typeof error.details === "object" && error.details.reason) ||
      String(error.code || "unavailable").toUpperCase().replaceAll("-", "_");
    throw new HttpsError(error.code, PRIVATE_RUN_BOUNDARY_MESSAGE, {reason});
  }
  runFailure(error);
}

const PRIVATE_RUN_BOUNDARY_MESSAGE = "Private quiz run could not be loaded";

function runFailure(error) {
  const codes = {
    INVALID_ARGUMENT: "invalid-argument",
    NOT_FOUND: "not-found",
    RUN_EMPTY: "failed-precondition",
    RUN_UNSCORABLE: "internal",
    RUN_QUESTION_UNREADABLE: "internal",
  };
  if (codes[error.code]) throw new HttpsError(codes[error.code], error.message);
  throw error;
}

/**
 * Замороженный набор прогона — за дверью: старт существует, принадлежит читателю и не
 * завершён; содержимое совпадает с тем, под которым дверь открывалась (AD-6). showResult
 * сознательно не едет: видимость результата решается на чтении результата текущим значением
 * (AD-29), а не копией со старта.
 */
exports.fetchPrivateQuizRun = onCall(CALLABLE_OPTIONS, async (request) => {
  try {
    const uid = requireAuthUid(request);
    await requireProfile(uid);
    const address = readRunAddress(request.data || {});
    // Выдача — транзакция: первый же сервис замораживает прогон (публичные половины И ключи
    // перевода) под стартом. Второе устройство и повтор после обрыва читают ТО ЖЕ, что увидел
    // первый: ключи перевода описывают свою перестановку, и прогон, пересобранный заново,
    // ответы бы не перевёл (AD-6: исходное намерение зафиксировано до разноса).
    return await db.runTransaction(async (transaction) => {
      const startRef = db.doc(startPath(address));
      const startSnap = await transaction.get(startRef);
      if (!startSnap.exists) throw new HttpsError("not-found", "Attempt start does not exist");
      const record = startSnap.data() || {};
      if (record.participantUid !== uid) {
        throw new HttpsError("not-found", "Attempt start does not exist");
      }
      if (record.attempt && record.attempt.status === PRIVATE_RUN_STATUS.TERMINAL) {
        throw new HttpsError("failed-precondition", "PRIVATE_ATTEMPT_TERMINAL: this attempt is already finished", {reason: "PRIVATE_ATTEMPT_TERMINAL"});
      }
      const questSnap = await transaction.get(
        db.doc(privateQuestPath(address.ownerUid, address.catalogId, address.questId)),
      );
      if (!questSnap.exists) throw new HttpsError("not-found", "Private quiz does not exist");
      const questData = questSnap.data() || {};
      if (!quizIdentityMatches(record, questData)) {
        // Квест уехал с момента старта: попытка обязана бежать по замороженному набору (AD-6),
        // а другого у неё нет. Отказ именованный — участник стартует заново, если дверь пустит.
        throw new HttpsError("failed-precondition", "RUN_CONTENT_CHANGED: the quiz changed after this attempt started", {reason: "RUN_CONTENT_CHANGED"});
      }
      let run = record.run && typeof record.run === "object" ? record.run : null;
      if (!run || !Array.isArray(run.questions) || run.questions.length === 0) {
        const questions = await collectFrozenQuestionsInTransaction(
          transaction,
          address.ownerUid,
          address.catalogId,
          address.questId,
        );
        run = freezeRun(questions);
        transaction.set(startRef, {run}, {merge: true});
      }
      return {
        startId: address.startId,
        startedAtMs: record.startedAtMs,
        // Бюджет попытки считается из ЗАМОРОЖЕННЫХ публичных половин: ровно их видел и
        // засекал участник, и приём подачи считает по тому же основанию (AD-6).
        deadlineMs: attemptDeadlineMs({startedAtMs: record.startedAtMs, questions: run.questions}),
        questions: run.questions,
      };
    });
  } catch (error) {
    runBoundaryFailure(error);
  }
});

/**
 * Заморозка прогона: публичные половины для участника и ключи перевода для приёмника.
 *
 * Ключи (`run.keys`) на клиент не едут никогда — они серверное слово приёмника подачи, без
 * которого ответ в перемешанных id Ordering/FillBlank не читается (choices ключа не требуют:
 * их id красакция не переписывает).
 */
function freezeRun(questions) {
  const frozen = [];
  const keys = {};
  for (const question of questions) {
    const verdict = redact(
      typeof question.payload === "string" ? question.payload : JSON.stringify(question.payload === undefined ? null : question.payload),
      "HARD",
    );
    if (verdict.status === "redacted") {
      frozen.push({id: question.id, order: numberValue(question.order, 0), difficulty: "HARD", payload: verdict.publicPayload});
      if (verdict.key) keys[question.id] = verdict.key;
    } else if (verdict.status === "already-redacted" || verdict.status === "not-applicable") {
      frozen.push({id: question.id, order: numberValue(question.order, 0), difficulty: "HARD", payload: question.payload});
    } else {
      // Нечитаемый вопрос замороженного набора — наша дыра: молча отдать его с ключом хуже
      // громкого отказа прогона.
      const unreadable = new Error("RUN_QUESTION_UNREADABLE: a frozen question could not be redacted");
      unreadable.code = "RUN_QUESTION_UNREADABLE";
      throw unreadable;
    }
  }
  return {questions: frozen, keys};
}



/** Собственный результат попытки — по ТЕКУЩЕЙ настройке showResult, при каждом чтении (AD-29). */
exports.readPrivateQuizResult = onCall(CALLABLE_OPTIONS, async (request) => {
  try {
    const uid = requireAuthUid(request);
    await requireProfile(uid);
    const address = readRunAddress(request.data || {});
    // Чтение тоже может закрывать попытку: окно восстановления кончилось, подачи не было —
    // ленивый терминал с причиной timeout (exam-terminal CLOSE_REASON.TIMEOUT прецедент).
    return await db.runTransaction(async (transaction) => {
      const startRef = db.doc(startPath(address));
      const startSnap = await transaction.get(startRef);
      if (!startSnap.exists) throw new HttpsError("not-found", "Attempt start does not exist");
      const record = startSnap.data() || {};
      if (record.participantUid !== uid) {
        throw new HttpsError("not-found", "Attempt start does not exist");
      }
      const questSnap = await transaction.get(db.doc(privateQuestPath(address.ownerUid, address.catalogId, address.questId)));
      if (!questSnap.exists) throw new HttpsError("not-found", "Private quiz does not exist");
      const questData = questSnap.data() || {};
      const settings = normalizeSettings(questData.privateQuizSettings);
      if (record.attempt && record.attempt.status === PRIVATE_RUN_STATUS.TERMINAL) {
        // Текущая настройка применяется заново при каждом чтении и повторе (AD-29).
        return projectOwnResult({attempt: record.attempt, showResult: settings.showResult});
      }
      if (!quizIdentityMatches(record, questData)) {
        throw new HttpsError("failed-precondition", "RUN_CONTENT_CHANGED: the quiz changed after this attempt started", {reason: "RUN_CONTENT_CHANGED"});
      }
      const frozen = await collectFrozenQuestionsInTransaction(
        transaction,
        address.ownerUid,
        address.catalogId,
        address.questId,
      );
      const deadlineBasis =
        record.run && Array.isArray(record.run.questions) && record.run.questions.length > 0 ?
          record.run.questions :
          frozen;
      const deadlineMs = attemptDeadlineMs({startedAtMs: record.startedAtMs, questions: deadlineBasis});
      if (Date.now() > deadlineMs) {
        // Пустая подача: всё, что сервер принял вовремя, — ничего. Весь квиз без ответа.
        const attempt = finishAttempt({
          settings,
          questions: frozen,
          submission: {...address, served: [], answers: []},
          now: Date.now(),
          deadlineMs,
        });
        transaction.set(startRef, {attempt}, {merge: true});
        return projectOwnResult({attempt, showResult: settings.showResult});
      }
      return projectOwnResult({attempt: null, showResult: settings.showResult});
    });
  } catch (error) {
    runBoundaryFailure(error);
  }
});

/**
 * Перевод ответов подачи из выданных (перемешанных) id в исходные — ключами заморозки прогона.
 *
 * Ответ без ключа доезжает как есть: у choices ключа и нет. Непереводимый ответ не выбрасывается
 * и не чинится — скоринг честно положит его на пол (evaluateAnswer floor), как и мусорный ответ.
 */
function translateSubmissionAnswers(submission, run) {
  const keys = run && typeof run === "object" && run.keys && typeof run.keys === "object" ? run.keys : null;
  if (!keys) return submission;
  const translate = (answer) => {
    if (!answer || typeof answer !== "object") return answer;
    const key = keys[answer.questionId];
    if (!key) return answer;
    const payload = typeof answer.answerPayload === "string" ? safeParse(answer.answerPayload) : answer.answerPayload;
    if (!payload || typeof payload !== "object") return answer;
    const translated = translateSubmittedAnswer(payload, key);
    if (!translated) return answer;
    return {...answer, answerPayload: JSON.stringify(translated)};
  };
  return {...submission, answers: (submission.answers || []).map(translate)};
}

function safeParse(text) {
  try {
    return JSON.parse(text);
  } catch (_) {
    return null;
  }
}

/** Приём подачи попытки: повтор ключа отвечает сохранённым терминалом, лимит не тратится дважды. */
async function applyPrivateQuizAttemptSubmission(uid, payload) {
  const submission = readSubmission(payload);
  const now = Date.now();
  return db.runTransaction(async (transaction) => {
    const startRef = db.doc(startPath(submission));
    const startSnap = await transaction.get(startRef);
    if (!startSnap.exists) throw new HttpsError("not-found", "Attempt start does not exist");
    const record = startSnap.data() || {};
    if (record.participantUid !== uid) {
      throw new HttpsError("not-found", "Attempt start does not exist");
    }
    const questSnap = await transaction.get(
      db.doc(privateQuestPath(submission.ownerUid, submission.catalogId, submission.questId)),
    );
    if (!questSnap.exists) throw new HttpsError("not-found", "Private quiz does not exist");
    const questData = questSnap.data() || {};
    const settings = normalizeSettings(questData.privateQuizSettings);
    // Повтор подачи того же старта — сохранённый терминал: результат не пересчитывается,
    // ответы не дописываются (AD-6: повтор намерения — то же намерение). Видимость — ТЕКУЩАЯ
    // настройка, при каждом чтении и повторе тоже (AD-29), а не «показать всё повтором».
    if (record.attempt && record.attempt.status === PRIVATE_RUN_STATUS.TERMINAL) {
      return projectOwnResult({attempt: record.attempt, showResult: settings.showResult});
    }
    // Закрытая или истёкшая ссылка рвёт только НОВЫЕ старты: начатая попытка право закончить
    // по исходным срокам сохранила (AD-29) — enabled/expiry здесь намеренно не читаются.
    const frozen = await collectFrozenQuestionsInTransaction(
      transaction,
      submission.ownerUid,
      submission.catalogId,
      submission.questId,
    );
    // Основание срока — замороженный прогон, когда он выдавался (участник засекал его),
    // иначе исходные вопросы дерева: основа та, по которой шёл вопрос (AD-6).
    const deadlineBasis =
      record.run && Array.isArray(record.run.questions) && record.run.questions.length > 0 ?
        record.run.questions :
        frozen;
    const deadlineMs = attemptDeadlineMs({startedAtMs: record.startedAtMs, questions: deadlineBasis});
    // Ответы в перемешанных id Ordering/FillBlank переводятся ключами заморозки; choices ключа
    // не требуют. Ключ делает ровно одно: возвращает ответ к исходному набору — и ничего больше.
    const translatedSubmission = translateSubmissionAnswers(submission, record.run);
    const attempt = finishAttempt({settings, questions: frozen, submission: translatedSubmission, now, deadlineMs});
    transaction.set(startRef, {attempt}, {merge: true});
    // Результат подающего отдаётся по текущей настройке; повтор чтения применит её заново.
    return projectOwnResult({attempt, showResult: settings.showResult});
  });
}

/** Авторская статистика — отдельный authorized scope, читается при любом showResult (AD-29). */
exports.readPrivateQuizStatistics = onCall(CALLABLE_OPTIONS, async (request) => {
  try {
    const uid = requireAuthUid(request);
    await requireProfile(uid);
    const body = request.data || {};
    const catalogId = stringValue(body.catalogId);
    const questId = stringValue(body.questId);
    if (!catalogId || !questId) {
      throw new HttpsError("invalid-argument", "catalogId and questId are required", {reason: "INVALID_ARGUMENT"});
    }
    const questSnap = await db.doc(privateQuestPath(uid, catalogId, questId)).get();
    if (!questSnap.exists) {
      throw new HttpsError("not-found", "Private quiz does not exist", {reason: "NOT_FOUND"});
    }
    const starts = await db.collection(privateQuestPath(uid, catalogId, questId) + "/starts").get();
    const attempts = (starts.docs || []).map((doc) => (doc.data() || {}).attempt).filter(Boolean);
    return {
      settings: normalizeSettings(questSnap.data() && questSnap.data().privateQuizSettings),
      ...projectAuthorStatistics({attempts}),
    };
  } catch (error) {
    runBoundaryFailure(error);
  }
});

/*
 * Экзамен (CAP-21/22/23): сидинги, терминальный писатель, сертификаты, коррекции.
 *
 * Экзамен — собственный жизненный цикл, не обычная попытка и не билет арены; здесь только
 * обвязка вызовов, вся логика — в модулях `exam-*`. Ничего из этого не активировано и не
 * развёрнуто: расписание ниже — только экспорт-заготовка, конкретный подписант сертификатов,
 * секреты и окружение остаются решением владельца (Q-EX-5).
 */
const {
  createExamSittings,
  SITTING_STATE,
} = require("./exam-sitting");
const {createExamTerminal} = require("./exam-terminal");
const {createCertificateSigning, verifyVerdict, VERIFY_STATUS,
  CERTIFICATE_PUBLIC_COLLECTION, KEY_MATERIAL_COLLECTION} = require("./exam-certificates");
const {createExamCorrections} = require("./exam-corrections");

const EXAM_CONFIG_DOC = "configs/exam";

/** Серверные правила экзамена: ручки владельца с безопасными значениями по умолчанию. */
function readExamConfig(document) {
  const source = document && typeof document === "object" ? document : {};
  const intOr = (value, fallback, min, max) => {
    const parsed = Math.floor(Number(value));
    return Number.isFinite(parsed) && parsed >= min && parsed <= max ? parsed : fallback;
  };
  return {
    // Явная недоступность сервера: экзамен открывается решением, а не деплоем.
    examEnabled: source.examEnabled === true,
    passMarkPercent: intOr(source.passMarkPercent, 60, 0, 100),
    // Общее серверное окно следующего вопроса (AD-31): тот же источник, что у обычного стека.
    nextQuestionWindowSeconds: intOr(source.nextQuestionWindowSeconds, 60, 5, 600),
    abandonmentGraceMs: intOr(source.abandonmentGraceMs, 5 * 60 * 1000, 0, 24 * 60 * 60 * 1000),
    paidTermsRevision: intOr(source.paidTermsRevision, 1, 0, 1_000_000),
    // Идентичность ключа подписи сертификатов — не секрет; сам материал — Q-EX-5.
    certificateSigningKeyId: typeof source.certificateSigningKeyId === "string" ?
      source.certificateSigningKeyId : null,
  };
}

async function loadExamConfig(tx) {
  const snapshot = await tx.get(db.doc(EXAM_CONFIG_DOC));
  return readExamConfig(snapshot.exists ? snapshot.data() : null);
}

const examSittings = createExamSittings({db, nowMs: () => Date.now(),
  loadLessonContext: loadArenaLessonContext, loadExamConfig});
const examTerminal = createExamTerminal({db, nowMs: () => Date.now(), loadExamConfig,
  log: (record) => logger.warn("Exam terminal step", record)});
// Подписант не связан (Q-EX-5): фабрика существует, ключей нет — выпуски остаются в явном
// ожидании подписи, неподписанная публикация невозможна.
const certificateSigning = createCertificateSigning({db, nowMs: () => Date.now(), signer: null});
const {latestEarnedIssuance, profileProjection} = require("./exam-certificates");
const examCorrections = createExamCorrections({
  db, nowMs: () => Date.now(),
  authorizeExclusion: (record) => Boolean(record && record.authorizedByCuratorUid),
});

/** Кураторская проверка вне транзакции — тот же порог, что и у арены. */
async function isCuratorOutsideTransaction(uid) {
  const snapshot = await db.doc(`profiles/${uid}`).get();
  const profile = snapshot.exists ? snapshot.data() || {} : {};
  return numberValue(profile.adminLevel, 0) >= QUALIFIED_LEVEL ||
    numberValue(profile.developerLevel, 0) > DEVELOPER_ALL_ACCESS_LEVEL;
}

exports.startThemeTest = onCall(MONETARY_CALLABLE_OPTIONS, (request) => callExamSitting("startThemeTest", request));
exports.startFinalExam = onCall(MONETARY_CALLABLE_OPTIONS, (request) => callExamSitting("startFinalExam", request));
exports.acknowledgeQuestion = onCall(CALLABLE_OPTIONS, (request) => callExamSitting("acknowledgeQuestion", request));
exports.submitAnswer = onCall(CALLABLE_OPTIONS, (request) => callExamSitting("submitAnswer", request));
exports.finishSitting = onCall(CALLABLE_OPTIONS, (request) => callExamSitting("finishSitting", request));
exports.fetchSittingResult = onCall(CALLABLE_OPTIONS, (request) => callExamSitting("fetchSittingResult", request));

async function callExamSitting(name, request) {
  const uid = requireAuthUid(request);
  const mode = name === "startFinalExam" ? "FinalExam" : "ThemeTest";
  try {
    if (name === "startThemeTest" || name === "startFinalExam") {
      return await examSittings.start({uid, payload: request.data, mode});
    }
    return await examSittings[name]({uid, payload: request.data});
  } catch (error) {
    if (error.name !== "Error" || !/^[A-Z][A-Z_]{2,63}$/.test(error.code || "")) throw error;
    const code = error.code === "INVALID_ARGUMENT" ? "invalid-argument" :
      error.code === "PERMISSION_DENIED" ? "permission-denied" :
      error.code === "NOT_FOUND" ? "not-found" :
      error.code === "USER_NOT_FOUND" ? "failed-precondition" :
      ["REQUEST_REUSE", "ANSWER_CONFLICT", "CORRECTION_REUSE"].includes(error.code) ? "already-exists" :
      ["REGEN_RETRY"].includes(error.code) ? "unavailable" : "failed-precondition";
    if (code === "unavailable") logger.warn("Exam operation needs retry", {reason: error.code});
    throw new HttpsError(code, "Exam operation could not be completed", {reason: error.code});
  }
}

/**
 * Публичная верификация сертификата — чтение БЕЗ входа.
 *
 * Отдельный вызов, не ветвь сидинговых: immutable публичный снимок и статус по собственным
 * датам и ключам факта. Изменяемые поля профиля, курса и скорректированный счёт не читаются.
 */
exports.publicVerifyCertificate = onCall(CALLABLE_OPTIONS, async (request) => {
  const issuanceId = typeof request.data === "object" && request.data !== null &&
    typeof request.data.issuanceId === "string" && request.data.issuanceId.trim() &&
    !request.data.issuanceId.includes("/") ?
    request.data.issuanceId.trim() : (() => {
      throw new HttpsError("invalid-argument", "issuanceId is required");
    })();
  const [publicSnapshotSnapshot, keysSnapshot] = await Promise.all([
    db.doc(`${CERTIFICATE_PUBLIC_COLLECTION}/${issuanceId}`).get(),
    db.collection(KEY_MATERIAL_COLLECTION).get(),
  ]);
  const keyMaterial = {};
  for (const doc of keysSnapshot.docs) keyMaterial[doc.id] = true;
  const verdict = verifyVerdict({
    publicSnapshot: publicSnapshotSnapshot.exists ? publicSnapshotSnapshot.data() : null,
    keyMaterial,
    nowMs: Date.now(),
  });
  if (!verdict.found) {
    // Нет снимка: либо выпуск ещё подписывается (явное ожидание), либо выпуска не существует.
    // Форма одна у всех ветвей: found, status, issuanceId, snapshot (null без снимка).
    const pending = await db.doc(`exam_certificates/${issuanceId}`).get();
    if (pending.exists) {
      return {found: true, status: VERIFY_STATUS.PENDING, issuanceId, snapshot: null};
    }
    return {found: false, status: VERIFY_STATUS.NOT_FOUND, issuanceId, snapshot: null};
  }
  return {found: true, status: verdict.status, issuanceId, snapshot: verdict.snapshot};
});

/**
 * Терминальный писатель экзамена: адаптер расписания СУЩЕСТВУЕТ, но НИЧЕГО не активирует и не
 * разворачивает. Деплой, расписание и окружение — решение владельца (Q-EX-5); этот экспорт —
 * только готовность кода, подтверждаемая тестами с подставными часами.
 */
exports.reconcileExamSittings = onSchedule({
  schedule: "every 1 minutes", timeZone: "UTC",
  timeoutSeconds: 540, memory: "512MiB", maxInstances: 1, retryCount: 0,
  region: REGION,
}, async () => {
  const result = await examTerminal.sweep();
  logger.info("Exam sitting sweep completed", {
    status: result.status, attempted: result.attempted, terminalized: result.terminalized,
    failed: result.failed,
  });
});

/** Исполнение работы подписывания владеет оператор (Q-EX-5); клиентского пути к ней нет. */
exports.fulfilCertificateSigning = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  if (!(await isCuratorOutsideTransaction(uid))) {
    throw new HttpsError("permission-denied", "Curator is required", {reason: "CURATOR_REQUIRED"});
  }
  const issuanceId = typeof request.data === "object" && request.data !== null ?
    request.data.issuanceId : null;
  const fence = typeof request.data === "object" && request.data !== null ?
    request.data.fence : null;
  if (typeof issuanceId !== "string" || !issuanceId || typeof fence !== "string" || !fence) {
    throw new HttpsError("invalid-argument", "issuanceId and fence are required");
  }
  // issuanceId становится путём документа выпуска: document-safe или типизированный отказ,
  // а не Firestore-ошибка посреди вызова (тот же класс, что correctionId у коррекций).
  if (issuanceId.includes("/") || issuanceId.length > 128) {
    throw new HttpsError("invalid-argument", "issuanceId must be a document-safe string",
      {reason: "INVALID_ARGUMENT"});
  }
  return certificateSigning.fulfil({issuanceId, fence});
});

/** Список сертификатов профиля: последний заработанный — по порядку заработка. */
exports.fetchMyCertificates = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  // Профиль читает последние выпуски по порядку заработка, а не всю историю аккаунта:
  // чтение ограничено, отбор «последнего заработанного» от него не меняется.
  const snapshot = await db.collection("exam_certificates").where("uid", "==", uid)
    .orderBy("issuedAtMs", "desc").limit(CERTIFICATE_PROFILE_READ_LIMIT).get();
  return profileProjection(latestEarnedIssuance(snapshot.docs.map((doc) => doc.data())));
});

/**
 * Материализация коррекции (C2) — терминальный шаг за куратором: пересчёт из исходных слотов
 * читается ВНУТРИ транзакции, поколение темы продвигается ровно один раз (устаревший пересчёт
 * отклоняется проверкой поколения), и исправленная проекция в ТОЙ ЖЕ транзакции ложится в
 * состояние допуска пользователя: passedAtGeneration = опубликованное поколение.
 */
exports.appendExamCorrection = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  if (!(await isCuratorOutsideTransaction(uid))) {
    throw new HttpsError("permission-denied", "Curator is required", {reason: "CURATOR_REQUIRED"});
  }
  const body = request.data || {};
  // Валидация до записи: неизвестный режим публикует голову без барьера поколения, пустой
  // владелец ломает идентичность состояния допуска. correctionId становится путём документа
  // головы: document-safe или типизированный отказ, а не Firestore-ошибка посреди вызова.
  if (typeof body.correctionId !== "string" || !body.correctionId.trim() ||
      body.correctionId.includes("/") || body.correctionId.length > 128) {
    throw new HttpsError("invalid-argument",
      "correctionId must be a nonempty document-safe string", {reason: "INVALID_ARGUMENT"});
  }
  if (!["ThemeTest", "FinalExam"].includes(body.mode)) {
    throw new HttpsError("invalid-argument", "mode must be ThemeTest or FinalExam",
      {reason: "INVALID_ARGUMENT"});
  }
  if (typeof body.ownerUid !== "string" || !body.ownerUid.trim()) {
    throw new HttpsError("invalid-argument", "ownerUid is required", {reason: "INVALID_ARGUMENT"});
  }
  if (typeof body.assessmentId !== "string" || !body.assessmentId.trim()) {
    throw new HttpsError("invalid-argument", "assessmentId is required", {reason: "INVALID_ARGUMENT"});
  }
  if (typeof body.sittingId !== "string" || !body.sittingId.trim()) {
    throw new HttpsError("invalid-argument", "sittingId is required", {reason: "INVALID_ARGUMENT"});
  }
  if (body.expectedHeadVersion !== undefined && body.expectedHeadVersion !== null &&
      (!Number.isSafeInteger(body.expectedHeadVersion) || body.expectedHeadVersion < 0)) {
    throw new HttpsError("invalid-argument",
      "expectedHeadVersion must be a non-negative integer when present", {reason: "INVALID_ARGUMENT"});
  }
  try {
    return await examCorrections.appendExclusion({
      record: {
        correctionId: body.correctionId,
        uid: body.ownerUid,
        mode: body.mode,
        assessmentId: body.assessmentId,
        sittingId: body.sittingId,
        slotIndex: body.slotIndex,
        actorUid: uid,
        reason: body.reason,
        reviewedAtMs: Date.now(),
        originalDigest: body.originalDigest || null,
        authorizedByCuratorUid: uid,
      },
      expectedHeadVersion: body.expectedHeadVersion,
    });
  } catch (error) {
    if (error.name === "Error" && /^[A-Z][A-Z_]{2,63}$/.test(error.code || "")) {
      const code = error.code === "INVALID_ARGUMENT" ? "invalid-argument" :
        ["CORRECTION_REUSE", "HEAD_CONFLICT"].includes(error.code) ? "already-exists" :
        error.code === "NOT_AUTHORIZED" ? "permission-denied" : "failed-precondition";
      throw new HttpsError(code, "Exam correction could not be applied", {reason: error.code});
    }
    throw error;
  }
});

/**
 * Терминальный шаг коррекции: довести опубликованную голову до проекции. Тот же кураторский
 * затвор, что у дописывания исключения. Устаревший пересчёт (поколение ушло вперёд) отклоняется
 * проверкой поколения и не трогает ни состояние, ни барьер.
 */
exports.materializeExamCorrection = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  if (!(await isCuratorOutsideTransaction(uid))) {
    throw new HttpsError("permission-denied", "Curator is required", {reason: "CURATOR_REQUIRED"});
  }
  const body = request.data || {};
  if (!["ThemeTest", "FinalExam"].includes(body.mode)) {
    throw new HttpsError("invalid-argument", "mode must be ThemeTest or FinalExam",
      {reason: "INVALID_ARGUMENT"});
  }
  if (typeof body.ownerUid !== "string" || !body.ownerUid.trim()) {
    throw new HttpsError("invalid-argument", "ownerUid is required", {reason: "INVALID_ARGUMENT"});
  }
  if (typeof body.assessmentId !== "string" || !body.assessmentId.trim()) {
    throw new HttpsError("invalid-argument", "assessmentId is required", {reason: "INVALID_ARGUMENT"});
  }
  try {
    return await examCorrections.materialize({
      uid: body.ownerUid,
      mode: body.mode,
      assessmentId: body.assessmentId,
    });
  } catch (error) {
    if (error.name === "Error" && /^[A-Z][A-Z_]{2,63}$/.test(error.code || "")) {
      throw new HttpsError("failed-precondition", "Exam correction could not be materialized",
        {reason: error.code});
    }
    throw error;
  }
});

async function callArenaEntry(method, request) {
  const uid = requireAuthUid(request);
  // A direct SDK call may acquire its Auth token after the user has switched accounts. Bind new
  // paid prepare/cancel calls to the owner confirmed by the UI before entering any transaction.
  // Legacy clients omit this redundant binding; server Auth remains the only authority.
  if (method !== "settle" && request.data && Object.hasOwn(request.data, "expectedOwnerUid")) {
    if (typeof request.data.expectedOwnerUid !== "string" || !isSingleDocumentId(request.data.expectedOwnerUid)) {
      throw new HttpsError("invalid-argument", "Invalid expected Arena owner", {reason: "INVALID_ARGUMENT"});
    }
    if (request.data.expectedOwnerUid !== uid) {
      throw new HttpsError("permission-denied", "The Arena account changed", {reason: "ACCOUNT_CHANGED"});
    }
  }
  try {
    return await arenaEntries[method](method === "settle" ? {uid, payload: request.data} : {...request.data, uid});
  } catch (error) {
    if (error.name !== "ArenaTicketError") throw error;
    const code = error.code === "INVALID_ARGUMENT" ? "invalid-argument" :
      error.code === "TICKET_OWNER_MISMATCH" ? "permission-denied" :
      ["REQUEST_REUSE", "SETTLEMENT_REUSE", "TICKET_ATTEMPT_MISMATCH"].includes(error.code) ? "already-exists" :
      ["RETRY_REGEN", "UNAVAILABLE", "UNSCORABLE_BANK", "INCONSISTENT_SETTLEMENT", "ARENA_INDEX_REBUILD_REQUIRED"].includes(error.code) ? "unavailable" :
      "failed-precondition";
    throw new HttpsError(code, error.message, {reason: error.code});
  }
}

// These are separate lesson decisions, not the legacy whole-quest shelf/rating operations.
exports.setLessonPlacement = onCall(CALLABLE_OPTIONS, (request) => callArenaService("transitionLesson", request));
exports.rateArenaLesson = onCall(CALLABLE_OPTIONS, (request) => callArenaService("submitLessonRating", request));
exports.finalizeArenaSeason = onCall(CALLABLE_OPTIONS, (request) => callArenaService("finalizeSeason", request));
exports.getArenaSeasonFinalizationStatus = onCall(CALLABLE_OPTIONS, (request) =>
  callArenaService("getSeasonFinalizationStatus", request));

async function callArenaService(method, request) {
  const uid = requireAuthUid(request);
  try {
    return await arenaService[method]({...request.data, uid});
  } catch (error) {
    const reason = typeof error?.code === "string" && /^[A-Z][A-Z_]{0,63}$/.test(error.code) ? error.code : "SERVER_FAILURE";
    const code = reason === "INVALID_ARGUMENT" ? "invalid-argument" :
      reason === "CURATOR_REQUIRED" ? "permission-denied" :
      reason === "MUTATION_REUSE" ? "already-exists" :
      reason === "SETTLEMENT_PENDING" || reason === "UNAVAILABLE" ? "unavailable" :
      reason === "SEASON_NOT_FOUND" ? "not-found" :
      reason === "SERVER_FAILURE" ? "internal" : "failed-precondition";
    if (code === "internal" || code === "unavailable") logger.warn("Arena operation needs retry or repair", {reason});
    throw new HttpsError(code, "Arena operation could not be completed", {reason});
  }
}

async function loadArenaLessonContext(transaction, lessonId) {
  if (!isSingleDocumentId(lessonId)) return null;
  const lessonSnapshot = await transaction.get(db.doc(`lessons/${lessonId}`));
  if (!lessonSnapshot.exists) return null;
  const lesson = lessonSnapshot.data() || {};
  if (!isSingleDocumentId(lesson.themeId)) return null;
  const themeSnapshot = await transaction.get(db.doc(`themes/${lesson.themeId}`));
  const theme = themeSnapshot.exists ? themeSnapshot.data() || {} : {};
  if (!isSingleDocumentId(theme.sectionId)) return null;
  const sectionSnapshot = await transaction.get(db.doc(`sections/${theme.sectionId}`));
  const section = sectionSnapshot.exists ? sectionSnapshot.data() || {} : {};
  if (!isSingleDocumentId(section.questId)) return null;
  const questSnapshot = await transaction.get(db.doc(`quests/${section.questId}`));
  const quest = questSnapshot.exists ? questSnapshot.data() || {} : {};
  if (!isSingleDocumentId(quest.catalogId)) return null;
  const catalogSnapshot = await transaction.get(db.doc(`catalogs/${quest.catalogId}`));
  if (!catalogSnapshot.exists) return null;
  const catalog = catalogSnapshot.data() || {};
  const authorUid = stringValue(quest.authorUid || quest.ownerUid);
  if (!isSingleDocumentId(authorUid) || !Number.isSafeInteger(lesson.version) || lesson.version < 1) return null;
  const [reviewSnapshot, recordSnapshots, configSnapshot, changeSnapshot] = await Promise.all([
    transaction.get(db.doc(adminLessonPath(lessonId))),
    transaction.get(db.collection(`${adminLessonPath(lessonId)}/reviews`)),
    transaction.get(db.doc("configs/arena_review")),
    transaction.get(db.doc(`catalogs/${quest.catalogId}/sync_changes/lesson_${lessonId}`)),
  ]);
  const review = reviewSnapshot.exists ? reviewSnapshot.data() || {} : {};
  const config = configSnapshot.exists ? configSnapshot.data() || {} : {};
  const task = {lessonId, ownerUid: authorUid, submissionId: review.submissionId,
    sourceLanguages: new Set(stringArray(review.sourceLanguages)), questions: [], checks: reviewToChecks({})};
  const records = recordSnapshots.docs.map((doc) => normalizeReviewRecord(doc.data() || {}, doc.id, lessonId));
  const checks = rebuildAggregate(task, records, config).checks;
  const reviewApproved = review.status === "PUBLISHED" && !review.rejectedAtMs &&
    review.ownerUid === authorUid && review.questId === section.questId &&
    isSingleDocumentId(lesson.reviewSubmissionId) && lesson.reviewSubmissionId === review.submissionId &&
    Number.isSafeInteger(lesson.reviewApprovedAtMs) && lessonPassed(checks, task, config);
  const change = changeSnapshot.exists ? changeSnapshot.data() || {} : {};
  return {lessonId, authorUid, catalogId: quest.catalogId, questId: section.questId,
    sectionId: theme.sectionId, themeId: lesson.themeId,
    questType: stringValue(catalog.questType || quest.questType, "REGULAR").toUpperCase(),
    contentVersion: lesson.version, publishedAtMs: nonNegativeInteger(lesson.publishedAtMs, 0),
    // At the course root archived=true is also the existing on-demand library marker.
    // A retired course has no archive shelf; archived descendants remain unavailable.
    published: !lesson.archived && !theme.archived && !section.archived &&
      (!quest.archived || stringArray(quest.visibleOn).includes("archive")),
    reviewApproved, nextChangeAtMs: nonNegativeInteger(change.changedAtMs, 0) + 1};
}

/**
 * Bounded context for the pageable lifecycle readers. State transitions and the public Arena
 * catalog intentionally recompute independent-review checks from every review record. A reader
 * must not do that: one old lesson with a large review journal would turn a page of twenty rows
 * into an unbounded transaction. This uses the server-written review outcome and six named
 * documents only; it never reads the review subcollection.
 */
async function loadArenaLifecycleContext(transaction, lessonId) {
  if (!isSingleDocumentId(lessonId)) return null;
  const lessonSnapshot = await transaction.get(db.doc(`lessons/${lessonId}`));
  if (!lessonSnapshot.exists) return null;
  const lesson = lessonSnapshot.data() || {};
  if (!isSingleDocumentId(lesson.themeId)) return null;
  const themeSnapshot = await transaction.get(db.doc(`themes/${lesson.themeId}`));
  const theme = themeSnapshot.exists ? themeSnapshot.data() || {} : {};
  if (!isSingleDocumentId(theme.sectionId)) return null;
  const sectionSnapshot = await transaction.get(db.doc(`sections/${theme.sectionId}`));
  const section = sectionSnapshot.exists ? sectionSnapshot.data() || {} : {};
  if (!isSingleDocumentId(section.questId)) return null;
  const questSnapshot = await transaction.get(db.doc(`quests/${section.questId}`));
  const quest = questSnapshot.exists ? questSnapshot.data() || {} : {};
  if (!isSingleDocumentId(quest.catalogId)) return null;
  const catalogSnapshot = await transaction.get(db.doc(`catalogs/${quest.catalogId}`));
  if (!catalogSnapshot.exists) return null;
  const catalog = catalogSnapshot.data() || {};
  const authorUid = stringValue(quest.authorUid || quest.ownerUid);
  if (!isSingleDocumentId(authorUid) || !Number.isSafeInteger(lesson.version) || lesson.version < 1) return null;
  const reviewSnapshot = await transaction.get(db.doc(adminLessonPath(lessonId)));
  const review = reviewSnapshot.exists ? reviewSnapshot.data() || {} : {};
  const reviewApproved = review.status === "PUBLISHED" && !review.rejectedAtMs &&
    review.ownerUid === authorUid && review.questId === section.questId &&
    isSingleDocumentId(lesson.reviewSubmissionId) && lesson.reviewSubmissionId === review.submissionId &&
    Number.isSafeInteger(lesson.reviewApprovedAtMs);
  return {lessonId, authorUid,
    questType: stringValue(catalog.questType || quest.questType, "REGULAR").toUpperCase(),
    contentVersion: lesson.version,
    published: !lesson.archived && !theme.archived && !section.archived &&
      (!quest.archived || stringArray(quest.visibleOn).includes("archive")),
    reviewApproved};
}

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

/*
 * Дев-вызова `processPendingArenaRequests` здесь больше нет — намеренно.
 *
 * Он выбирал заявки с `processed == false` и прогонял через `processArenaRequest` их тело как есть:
 * мимо ключа идемпотентности (ключа у него не было вовсе) и мимо сверки версии (сверка живёт в
 * `submitMutation`, а он звал обработчик напрямую). То есть рядом с единственным проверяемым входом
 * стоял второй, непроверяемый, и открывался он одним developerLevel.
 *
 * Заменять нечем и не нужно. Заявка приезжает единственным транспортом (AD-6) — `submitMutation` с
 * операцией `quest_authoring.SUBMIT_ARENA`; упавшая попытка освобождает ключ, и повтор делает сам
 * клиент, проходя и ключ, и сверку. Документ, застрявший в `processed == false`, — это либо строка,
 * оставшаяся от снятой прямой записи, либо запись админского скрипта; догонять их вторым входом в
 * рабочий код значит держать этот вход открытым постоянно ради ситуации, которой в проде нет.
 */

/**
 * Страница журнала ревью.
 *
 * Единственный из четырёх журналов, который клиент читает не из Firestore напрямую: коллекция
 * `admin/review/sync_changes` закрыта правилами. Поэтому ограничение и продолжаемость выборки
 * (AD-31) здесь обязан выполнить сервер, а не запрос клиента: курсор — пара `(changedAtMs, docId)`,
 * страница ограничена `limit`, и ответ говорит, есть ли ещё. Скалярный `cursorMs` остаётся
 * запасным входом для клиента прежней версии. Разбор и запрос живут в `review-journal.js` — там же
 * их тест на фейке Firestore.
 */
exports.fetchReviewAssignmentChanges = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  await requireProfile(uid);
  return readReviewAssignmentChanges(db, request.data || {}, {
    documentId: firestoreTypes.FieldPath.documentId(),
  });
});

exports.fetchReviewAssignments = onCall(CALLABLE_OPTIONS, async (request) => {
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

/**
 * Sends a quest back to its author with a reason.
 *
 * Scoring a lesson low leaves it UNDER_REVIEW forever, which tells the author nothing and blocks
 * the queue. Rejection is the other half: the submission stops being reviewable, the reason
 * travels back to the author's draft, and nothing of it reaches a public shelf. Resubmission is
 * how it comes back — the author edits and sends again, minting a new submission.
 */
exports.rejectReviewSubmission = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const profile = await requireProfile(uid);
  const assignmentId = stringValue(request.data && request.data.assignmentId);
  if (!assignmentId) throw new HttpsError("invalid-argument", "assignmentId is required");
  const reason = stringValue(request.data && request.data.reason).trim();
  // A rejection without a reason is indistinguishable from silence, and the author cannot act
  // on silence.
  if (!reason) throw new HttpsError("invalid-argument", "reason is required");
  if (Buffer.byteLength(reason, "utf8") > MAX_AUTHOR_REJECTION_REASON_BYTES) {
    throw new HttpsError("invalid-argument", "reason is too long");
  }

  const task = await readAdminReviewLessonTask(assignmentId);
  if (!task) throw new HttpsError("not-found", `Review assignment ${assignmentId} not found`);

  const config = await readArenaReviewConfig();
  const existingRecords = await readReviewRecords(task.lessonId, task.submissionId);
  // Whoever may score this lesson may refuse it: same gate, both directions. The author-exclusion
  // and the developer exemption inside canSubmit apply here unchanged.
  const mayJudge =
    canSubmit(profile, task, "TESTING", config, existingRecords) ||
    canSubmit(profile, task, "LOGIC", config, existingRecords);
  if (!mayJudge) {
    throw new HttpsError("permission-denied", `Reviewer ${uid} cannot judge ${assignmentId}`);
  }

  const now = Date.now();
  const rejectedTask = {
    ...task,
    rejectedAtMs: now,
    rejectedByUid: uid,
    // Уровень судьи на момент отклонения: расчёт цикла платит за обоснованное отклонение как за
    // завершённую работу (AD-10), а уровня у отклонения без этого поля нет.
    rejectedReviewerLevelAtSubmit: Math.max(levelFor(profile, "TESTING"), levelFor(profile, "LOGIC")),
    rejectionReason: reason,
    status: "REJECTED",
    changedAtMs: now,
  };
  const batch = db.batch();
  writeAdminReviewLessonTasksToBatch(batch, [rejectedTask]);
  batch.set(
    db.collection("quest_review_requests").doc(task.submissionId),
    {
      status: "REJECTED",
      rejectedAtMs: now,
      rejectedByUid: uid,
      rejectionReason: reason,
      updatedAtMs: now,
    },
    {merge: true},
  );
  await batch.commit();
  // Отклонение — тоже полный цикл (CAP-4): фонд делится, остаток сгорает, возврата платы нет.
  await settleReviewFeeCycle(task.submissionId, "REJECTED", now);
  return {assignmentId, submissionId: task.submissionId, rejectedAtMs: now, reason};
});

exports.submitReviewAction = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  return applySubmitReviewAction(uid, request.data || {});
});

/**
 * Решение ревьюера — тело, общее для прямого вызова и для очереди.
 *
 * Тело — сериализованный `SubmitReviewActionCommand`, в той же форме, что и прямой вызов.
 * Задание, к моменту доставки переназначенное или закрытое, отвечает окончательным отказом
 * (`not-found` / `permission-denied`), и запись очереди уходит в карантин — локальной половины у
 * этого действия нет, откатывать нечего (AD-25, AD-28).
 *
 * Начисление репутации ниже идёт вне транзакции. От двойного начисления при повторной доставке
 * защищает ключ идемпотентности приёмника (AD-1): повтор по выполненному ключу получает
 * сохранённый ответ и сюда не попадает. Здесь этот ключ не сверяется и не может — у тела нет
 * доступа к конверту, и второй проверки рядом с единственной быть не должно.
 */
async function applySubmitReviewAction(uid, payload, options = {}) {
  const action = normalizeSubmitAction(payload || {});
  const pinnedReviewId = stringValue(options.reviewId) || null;
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
  const existingRecords = await readReviewRecords(action.lessonId, task.submissionId);
  // Эффект уже лёг — инстанс умер после batch.commit(), но до того, как ключ был закрыт.
  // Отвечаем эквивалентом сохранённого результата и не трогаем ни записи, ни репутацию:
  // проверка раньше canSubmit, иначе «уже отрецензировано» ушло бы отказом в карантин, а
  // карантин откатил бы ревью, которое на самом деле состоялось.
  if (pinnedReviewId && existingRecords.some((record) => record.id === pinnedReviewId)) {
    const settled = rebuildAggregate(task, existingRecords, config);
    return {
      recordId: pinnedReviewId,
      aggregate: checksToCallableMap(settled.checks),
      reviewerDeltas: [],
      published: await publishSubmissionIfReady(task.submissionId, config, Date.now()),
      replayed: true,
    };
  }
  if (!canSubmit(profile, task, action.kind, config, existingRecords)) {
    throw new HttpsError(
      "permission-denied",
      `Reviewer ${profile.uid} is not allowed to submit ${action.kind} for ${action.assignmentId}`,
    );
  }

  const now = Date.now();
  const record = {...actionToRecord(action, profile, task, config, existingRecords, now, pinnedReviewId),
    submissionId: task.submissionId};
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
}

/** Сколько аккаунтов сверяем за один прогон. Остальные дождутся следующего: сверка не срочная. */
const MAX_RECONCILED_ACCOUNTS_PER_RUN = 200;

/**
 * Сверка подделываемых балансов с книгой движений (CAP-11).
 *
 * Раз в сутки, ночью, и не блокируя игру: читает движения аккаунта, складывает их по валюте и
 * сравнивает с записанным балансом. Расхождение записывается находкой — не приговором. Баланс не
 * трогается: чинить его задним числом значило бы отобрать у честного игрока то, что ему начислила
 * ошибка сервера, а находка для того и нужна, чтобы сначала посмотреть.
 *
 * Золото и плазма не проверяются: их сторожат по каждой операции, и неправильное число там —
 * неправильные деньги, а не расхождение в учёте.
 *
 * Аккаунт, начавшийся раньше книги, не обвиняется: до первой строки движения были, а записи о них
 * нет. Запас в сутки прощает книге опоздание в пределах одного прогона этой же функции.
 */
exports.reconcileCurrencyBalancesDaily = onSchedule(
  {...FUNCTION_OPTIONS, schedule: "every day 04:00", timeZone: "UTC"},
  async () => {
    const now = Date.now();
    // Порциями и по одному аккаунту: сверка не должна ни блокировать игру, ни читать всю книгу
    // разом. Кого проверять — те, у кого за сутки было хоть одно движение: у остальных баланс не
    // менялся, и сверять нечего.
    const movedUids = new Set();
    const recent = await db.collection(LEDGER_PATH)
      .where("atMs", ">", now - DAY_MS)
      .limit(MAX_RECONCILED_ACCOUNTS_PER_RUN * 8)
      .get();
    for (const doc of recent.docs) {
      const uid = stringValue((doc.data() || {}).uid);
      if (uid) movedUids.add(uid);
      if (movedUids.size >= MAX_RECONCILED_ACCOUNTS_PER_RUN) break;
    }

    for (const uid of movedUids) {
      await reconcileOneAccount(uid, now);
    }
  },
);

async function reconcileOneAccount(uid, now) {
  const [userSnapshot, entrySnapshot] = await Promise.all([
    db.collection("users").doc(uid).get(),
    db.collection(LEDGER_PATH).where("uid", "==", uid).get(),
  ]);
  if (!userSnapshot.exists) return;
  const user = userSnapshot.data() || {};
  const entries = entrySnapshot.docs.map((doc) => doc.data() || {});
  const accountCreatedAtMs = numberValue(user.createdAtMs, 0);

  for (const currency of SWEPT_CURRENCIES) {
    const finding = reconcile({
      uid,
      currency,
      storedBalance: storedBalanceOf(user, currency),
      entries,
      atMs: now,
    });
    if (!isReportable(finding, {accountCreatedAtMs, graceMs: DAY_MS})) continue;
    const record = reconciliationRecord(finding, now);
    await db.doc(`${RECONCILIATION_PATH}/${record.id}`).set(record, {merge: true});
  }
}

/** Где лежит баланс каждой сверяемой валюты. Одно место на валюту, чтобы сверка не гадала. */
function storedBalanceOf(user, currency) {
  if (currency === CURRENCY.NOLICS) return nonNegativeInteger(user.pointsNolics, numberValue(user.nolics, 0));
  if (currency === CURRENCY.SKILL_POINTS) return nonNegativeInteger(user.pointsSkill, 0);
  if (currency === CURRENCY.STANDARD_CHARGE_POINTS) return nonNegativeInteger(user.lifePoints, 0);
  return 0;
}

exports.reconcileQuestReviewDaily = onSchedule(
  {...FUNCTION_OPTIONS, schedule: "every day 03:00", timeZone: "UTC"},
  async () => {
    const now = Date.now();
    await reconcileChangedReviewLessons(now - DAY_MS, now);
  },
);

/** Key for one lesson at one difficulty, so easy and hard keep separate bests. */
function lessonBestKey(lessonId, difficulty) {
  return `${lessonId}:${stringValue(difficulty).toUpperCase() === "HARD" ? "HARD" : "EASY"}`;
}

/**
 * A lesson's published questions, whole, for every distinct lesson named.
 *
 * One query per lesson serving two readers: the allocated-time sum a batch is rewarded by, and the
 * pool a server-scored attempt is judged against. They want the same documents in two shapes, and
 * ran the same query twice, five lines apart, until this took the query off both of them.
 *
 * Strict on purpose. A question query that fails takes the whole call down, exactly as it did when
 * only the reward read it: the reward is computed from these documents, and a lesson that came back
 * empty because Firestore was unavailable would pay a smaller reward silently where today the call
 * fails and the client retries. The reads this file added on top — the answer keys and a private
 * lesson's own questions — are caught per lesson instead, because there a failure has a safe
 * answer: the attempt is not scored, and so is neither paid nor charged.
 */
async function readLessonQuestions(lessonIds) {
  const distinct = [...new Set(lessonIds.filter(Boolean).map(String))];
  const entries = await Promise.all(
    distinct.map(async (lessonId) => {
      const snapshot = await db.collection("questions").where("lessonId", "==", lessonId).get();
      return [lessonId, snapshot.docs.map(questionDocumentFor)];
    }),
  );
  return new Map(entries);
}

/** One stored question as a plain document, id included — the shape both readers narrow from. */
function questionDocumentFor(doc) {
  return {...(doc.data() || {}), id: doc.id};
}

/**
 * Allocated seconds per lesson, both difficulties, from the questions already read.
 *
 * Derived from the lesson's own questions rather than taken from the client: a caller that could
 * name its lesson's worth could name any reward it liked.
 */
function allocatedSecondsFrom(questionsByLesson) {
  const allocated = {};
  for (const [lessonId, documents] of questionsByLesson) {
    const contents = documents.map(questionRowFrom);
    allocated[lessonId] = {
      easy: lessonAllocatedSeconds(contents, false),
      hard: lessonAllocatedSeconds(contents, true),
      easyPoolSize: lessonPoolSize(contents, false),
      hardPoolSize: lessonPoolSize(contents, true),
    };
  }
  return allocated;
}

/**
 * How many records one attempt names in a log line before the count speaks for the rest. The same
 * bound, and the same reason, as the publication path's own sample: a log entry over Cloud
 * Logging's size limit is dropped whole, which is worse than a sample beside a count.
 */
const LOGGED_SCORING_SAMPLE = 20;

/**
 * Why a lesson was not read, when it was not.
 *
 * A state of its own, and never `documents: []`. An empty lesson and an unread one score
 * identically — every served position a filler, every digit `'1'` — and only one of the two is the
 * player's business. Collapsing them charged full price for a zero the server had caused.
 */
const LESSON_UNREAD = {
  /** The read itself failed: permission, a deadline, an index that is not there. */
  READ_FAILED: "lesson-read-failed",
  /** The lesson's own path could not be assembled from the ids the attempt carries. */
  UNUSABLE_PATH: "unusable-lesson-path",
  /** Nothing was read for this lesson at all — reachable only through a bug in this file. */
  NOT_READ: "lesson-not-read",
};

/** A lesson whose keys and questions could not be read. Same keys as a read one. */
function unreadLesson(failure) {
  return {keyDocument: null, documents: [], failure};
}

/**
 * The lesson documents the server needs to score the attempts that asked it to.
 *
 * Read here, beside the questions the reward is priced from, and for the same reason: Firestore
 * wants every read before the first write, and these are reads. One entry per **distinct lesson**,
 * so fifty attempts on one lesson cost one key document and — for a private lesson — one question
 * query. A lesson no attempt asked the server to score is not read at all.
 *
 * Keyed by `lessonContextKey`, the same scope-qualified key `readLessonContexts` uses, and not by
 * the bare `lessonId`: that id comes off the device, and a public and a private lesson may both be
 * called `lesson-1`. Keyed by the id alone, whichever was read first would have answered for both,
 * and one player's questions would have scored another player's attempt.
 *
 * @returns a `Map` from `lessonContextKey(event)` to `{keyDocument, documents, failure}`.
 */
async function readLessonScoringSources(events, questionsByLesson) {
  const pending = new Map();
  for (const item of events) {
    if (item.event.paymentRule !== PAYMENT_RULE.SERVER_SCORED) continue;
    const key = lessonContextKey(item.event);
    if (!pending.has(key)) pending.set(key, readLessonScoringSource(item.event, questionsByLesson));
  }
  const entries = await Promise.all([...pending].map(async ([key, promise]) => [key, await promise]));
  return new Map(entries);
}

/**
 * One lesson's keys and questions, in its own scope, with a failure kept as a failure.
 *
 * Caught per lesson rather than left to the `Promise.all` above: a permission error or a deadline
 * on one lesson's key document would otherwise reject out of the handler and take the other
 * forty-nine attempts in the batch — device-scored ones included — down with it. Here it costs its
 * own attempt, which is then neither paid for nor charged.
 */
async function readLessonScoringSource(event, questionsByLesson) {
  try {
    const [keyDocument, documents] = await Promise.all([
      readLessonKeyDocument(event.lessonId),
      readScopedLessonQuestions(event, questionsByLesson),
    ]);
    if (documents === null) return unreadLesson(LESSON_UNREAD.UNUSABLE_PATH);
    return {keyDocument, documents, failure: null};
  } catch (error) {
    logger.error("attempt-scoring: a lesson could not be read", {
      lessonId: event.lessonId,
      scope: normalizeScope(event.scope),
      message: error && error.message ? String(error.message).slice(0, 500) : String(error),
    });
    return unreadLesson(LESSON_UNREAD.READ_FAILED);
  }
}

/** The lesson's answer keys, or null for a lesson that has none. Only publication writes these. */
async function readLessonKeyDocument(lessonId) {
  // `db.doc` throws on an id Firestore will not take, and an id the key store could never have
  // written under is simply a lesson with no keys — which the scorer already has an answer for.
  if (!isStorableDocumentId(lessonId)) return null;
  const snapshot = await db.doc(keyDocumentPath(lessonId)).get();
  return snapshot.exists ? snapshot.data() || null : null;
}

/**
 * A lesson's questions, from wherever that lesson keeps them.
 *
 * A public lesson's are in the top-level `questions` collection and have already been read for the
 * whole batch. A private one's are not there at all: only the public publish path writes that copy,
 * and a private quest's questions live under its own owner's tree. Reading a private lesson from
 * the public collection returned nothing, every served question was missing, and the player was
 * charged full price for a zero — so scope decides the path, exactly as `resolveLessonContext`
 * already has it decide.
 *
 * @returns the documents, or `null` when this lesson was not read at all — never `[]` for that.
 */
async function readScopedLessonQuestions(event, questionsByLesson) {
  if (normalizeScope(event.scope) !== PRIVATE_SCOPE) {
    const documents = questionsByLesson instanceof Map
      ? questionsByLesson.get(event.lessonId)
      : undefined;
    return documents === undefined ? null : documents;
  }
  const ids = [event.ownerUid, event.catalogId, event.questId, event.sectionId, event.themeId, event.lessonId];
  if (!ids.every(isSingleDocumentId)) return null;
  const snapshot = await db.collection(`${privateLessonPath(...ids)}/questions`).get();
  return snapshot.docs.map(questionDocumentFor);
}

/**
 * The score already on file for every server-scored attempt in this batch that has one.
 *
 * A submitted attempt arrives twice whenever the offline queue redelivers it, and the second
 * delivery is scored against **today's** questions and keys. A question republished in between
 * moves the digits, so a replay could lower a percent the player had already been paid for, while
 * `lessonBest` and the tournament row kept the original — one attempt with two scores and no way
 * to tell which was charged for. So a replay is not re-scored at all: the stored numbers stand.
 *
 * Advisory, and only ever in the safe direction. A document that is here is certainly a replay —
 * these are written, never deleted — so skipping the scoring is sound. A document that is not may
 * still turn out to exist inside the transaction, and the write guards that case separately.
 *
 * @returns a `Map` keyed by the batch item, so nothing is matched up by an id the client chose.
 */
async function readStoredAttemptScores(events) {
  const replays = new Map();
  const pending = events.filter((item) => item.event.paymentRule === PAYMENT_RULE.SERVER_SCORED);
  await Promise.all(pending.map(async (item) => {
    try {
      const snapshot = await item.ref.get();
      const stored = storedScoreFields(snapshot);
      if (stored.codeAnswer !== undefined) replays.set(item, stored);
    } catch (error) {
      // Not knowing means scoring it, which is what happened before this read existed. The
      // transaction still refuses to charge twice, and the write still keeps the stored score.
      logger.warn("attempt-scoring: could not check whether an attempt was already stored", {
        attemptId: item.event.attemptId,
        message: error && error.message ? String(error.message).slice(0, 500) : String(error),
      });
    }
  }));
  return replays;
}

/**
 * The score a stored attempt already carries, or nothing when it carries none.
 *
 * `{}` rather than defaults on purpose: it is spread over a document being written, and a
 * `codeAnswer: ""` invented here would erase the one that is actually on file.
 */
function storedScoreFields(snapshot) {
  const data = snapshot && snapshot.exists ? snapshot.data() || {} : null;
  if (data === null || typeof data.codeAnswer !== "string") return {};
  return {codeAnswer: data.codeAnswer, percentScore: numberValue(data.percentScore, 0)};
}

/**
 * Scores one attempt against what its lesson holds, or answers with nothing for an attempt the
 * device scored itself.
 *
 * Pure: the lesson's keys and questions arrive in `sources`, already read.
 *
 * @returns `null` for a device-scored attempt — there is nothing here to decide about one.
 *   Otherwise `{unread, pool, scoring}`. `unread` names why the lesson was never read, and then
 *   both others are null; `scoring` is null for a lesson that was read but could not be made into
 *   a pool. Every one of those is an attempt nobody can score, which `withServerScore` and
 *   `isPayable` read as "pay nothing, charge nothing".
 */
function serverScoringFor(event, sources) {
  if (event.paymentRule !== PAYMENT_RULE.SERVER_SCORED) return null;
  const source = sources instanceof Map ? sources.get(lessonContextKey(event)) : undefined;
  if (source === undefined) return {unread: LESSON_UNREAD.NOT_READ, pool: null, scoring: null};
  if (source.failure !== null) return {unread: source.failure, pool: null, scoring: null};
  const pool = buildScoringPool({
    lessonId: event.lessonId,
    lessonVersion: event.lessonVersion,
    served: event.served,
    documents: source.documents,
  });
  if (!pool.built) return {unread: null, pool, scoring: null};
  return {
    unread: null,
    pool,
    scoring: scoreAttempt({
      questions: pool.questions,
      served: event.served,
      keyDocument: source.keyDocument,
      answers: event.answers,
    }),
  };
}

/**
 * Fills in the server's own digits for every attempt that asked for them, and settles what each
 * attempt in the batch may be paid for.
 *
 * Reads nothing and writes nothing: the lesson documents arrive in `sources` and the outcome lands
 * on the items. Every attempt is decided **on its own** — an attempt the server could not score
 * leaves the other forty-nine exactly as they were, which is the whole of the batch's contract.
 *
 * A server-scored attempt also leaves here with a charge-claim mask of its own. The mask has to be
 * the length of the digits, and on this path the digits are the server's — the intake refuses a
 * mask sent against them by name — so an empty mask of the right length is the only one there is.
 * Without it `settleClaims` throws on the length check, inside the transaction, taking the batch.
 */
function applyServerScoring(events, sources, replays) {
  for (const item of events) {
    const replayed = replays instanceof Map ? replays.get(item) : undefined;
    if (replayed !== undefined) {
      // Already stored, already decided. `isNew` refuses the charge either way; this refuses the
      // re-score, so the numbers a player was paid on cannot move underneath them.
      item.payable = false;
      item.event = {
        ...item.event,
        codeAnswer: replayed.codeAnswer,
        percentScore: replayed.percentScore,
        chargeClaims: noClaims(replayed.codeAnswer.length),
      };
      continue;
    }
    const outcome = serverScoringFor(item.event, sources);
    const scoring = outcome === null ? null : outcome.scoring;
    const {payable, ...event} = withServerScore(item.event, scoring);
    item.payable = payable === true;
    item.event = event;
    if (outcome === null) {
      reportDeviceScoredAttempt(item);
      continue;
    }
    item.event.chargeClaims = noClaims(item.event.codeAnswer.length);
    reportServerScoredAttempt(item, outcome, scoring);
  }
}

/**
 * What a server-scored attempt cost, when it cost anything.
 *
 * Logged on the payable path too, and that is the point of it. A lesson whose questions were
 * accidentally emptied of all but one charges every player who touches it, hands them a percent
 * built from `'1'`s, and is a completely ordinary paid attempt from the transaction's side.
 * `pool.missing` is the only place that loss is visible at all.
 *
 * The reasons are filtered to the server's own, because those are the ones that make an attempt
 * unpayable and the client's are ordinary play. Counts travel beside every sample: the records are
 * capped, and `scoring.omitted` says how many did not fit — two hundred client faults evict every
 * server fault there was, and the line would otherwise report no reasons at all.
 */
function reportServerScoredAttempt(item, outcome, scoring) {
  const missing = outcome.pool === null ? [] : outcome.pool.missing;
  const records = scoring === null ? [] : scoring.unscorable;
  const omitted = scoring === null ? 0 : scoring.omitted;
  if (item.payable && missing.length === 0 && records.length === 0 && omitted === 0) return;
  const serverFaults = records.filter((record) => record && record.fault === FAULT.SERVER);
  logger.warn("attempt-scoring: a hard attempt the server scored did not come out clean", {
    attemptId: item.event.attemptId,
    lessonId: item.event.lessonId,
    payable: item.payable,
    unread: outcome.unread,
    poolReason: outcome.pool === null ? null : outcome.pool.reason,
    poolDetail: outcome.pool === null ? null : outcome.pool.detail,
    versionDrift: outcome.pool === null ? null : outcome.pool.versionDrift,
    missingCount: missing.length,
    missingQuestions: missing.slice(0, LOGGED_SCORING_SAMPLE),
    unscorableCount: records.length,
    // Records past `MAX_RECORDS` were dropped by the scorer, so the reasons below are a sample of
    // a sample whenever this is not zero.
    omitted,
    serverFaultCount: serverFaults.length,
    serverFaultReasons: [...new Set(serverFaults.map((record) => record.reason))]
      .slice(0, LOGGED_SCORING_SAMPLE),
  });
}

/**
 * The one device-scored outcome worth a line: an attempt whose percent follows from its own digits
 * and whose served list does not.
 *
 * `servedVerified` became a payment condition the moment this handler started reading bodies
 * through the intake, and the client has been sending `served` since E2.10. A client bug that
 * misbuilds the list would otherwise stop paying every player who has it, silently and with nothing
 * anywhere to say why. Nothing else about a device-scored attempt is logged: a crafted percent is
 * today's rule, refused today's way, and it was not logged before either.
 */
function reportDeviceScoredAttempt(item) {
  if (item.payable) return;
  if (item.event.scoreVerified !== true || item.event.servedVerified !== false) return;
  logger.warn("attempt-intake: a device-scored attempt was refused payment on its served list", {
    attemptId: item.event.attemptId,
    lessonId: item.event.lessonId,
    codeAnswerLength: stringValue(item.event.codeAnswer).length,
    servedCount: Array.isArray(item.event.served) ? item.event.served.length : null,
    answerCount: Array.isArray(item.event.answers) ? item.event.answers.length : null,
  });
}

/**
 * The fields `result_events` holds, in the order a document carries them.
 *
 * An allowlist, not a denylist. The intake and this handler both hang their own working state on
 * the event — the payment rule, whether it was evaluated, what the lesson resolved to — and a
 * denylist stores every field either of them grows next, by default, forever. Named here, a new
 * field reaches storage only when somebody decides it should.
 *
 * `served` is on the list deliberately: it is the sole authority on which question sat at which
 * position, and without it a score the server charged real money for cannot be re-derived or
 * disputed. `scoringAuthority` is on it for the same reason — otherwise the only mark of a
 * server-scored attempt is the *absence* of `scoreVerified`, which is indistinguishable from a row
 * written before that field existed.
 */
const STORED_ATTEMPT_FIELDS = [
  // The intake's, device-scored and server-scored alike.
  "answers",
  "userId",
  "scope",
  "ownerUid",
  "catalogId",
  "questId",
  "sectionId",
  "themeId",
  "lessonId",
  "lessonVersion",
  "sourceShelf",
  "attemptId",
  "difficulty",
  "codeAnswer",
  "chargeClaims",
  "percentScore",
  "expectedPercentScore",
  "scoreVerified",
  "completedAtMs",
  "createdAtMs",
  "served",
  "scoringAuthority",
  // What this handler resolved before the transaction opened.
  "declaredSourceShelf",
  "resolvedQuestId",
  "activityKind",
  "lifeCost",
  // What settlement decided inside it.
  "settledCodeAnswer",
  "settledPercentScore",
  "standardChargesPaid",
  "plasmaChargesPaid",
  "chargeClaimsUnpaid",
];

/** The attempt as `result_events` stores it: the fields above, and nothing this file decides by. */
function storedAttemptFields(event) {
  const stored = {};
  for (const field of STORED_ATTEMPT_FIELDS) {
    if (event[field] !== undefined) stored[field] = event[field];
  }
  return stored;
}

/**
 * Назвала ли попытка столько показанных вопросов, сколько урок ставит.
 *
 * Список показанного приезжает от устройства и сверяется приёмником только с цифрами — этого мало:
 * `codeAnswer: "9"` со списком на один вопрос согласован сам с собой. Награда же считается по
 * уроку целиком, поэтому короткая попытка платила бы как полная.
 *
 * Список, которого нет вовсе, сверку не проходит: без него подделка из одной цифры остаётся
 * оплачиваемой, а нынешний клиент список шлёт всегда. Попытка при этом сохраняется — не платится
 * только награда.
 */
function servedCountMatchesLesson(event, allocatedByLesson) {
  // Списка нет вовсе — не платим. Приёмник называет такую попытку клиентом, который список ещё не
  // шлёт, и от сверки с цифрами её освобождает; для оплаты этого мало, потому что без списка
  // подделка из одной цифры проходит нетронутой. Живых установок нет (AD-16, подтверждено
  // владельцем), а нынешний клиент список шлёт всегда, так что отказывать нечему.
  if (!Array.isArray(event.served)) return false;
  const lesson = allocatedByLesson[event.lessonId];
  if (!lesson) return false;
  const isHard = stringValue(event.difficulty).toUpperCase() === "HARD";
  const expected = isHard ? lesson.hardPoolSize : lesson.easyPoolSize;
  return expected > 0 && event.served.length === expected;
}

/**
 * Квест, к которому на самом деле принадлежит каждый урок из пакета, — прочитанный один раз
 * перед транзакцией.
 *
 * Цена берётся по квесту, награда — по уроку, и раньше эти двое были двумя независимыми полями
 * payload: клиент называл `questId` любого домашнего квеста (или объявлял область приватной) и
 * `lessonId` самого большого турнирного урока — и получал полную награду за него по цене обычного
 * урока. Поэтому квест здесь выводится **из урока**: опубликованный урок знает свою тему, тема —
 * раздел, раздел — квест, и объявленный `questId` в цене не участвует вовсе.
 *
 * Опубликованный урок побеждает объявленную область: если `lessons/{lessonId}` существует, попытка
 * оценивается по его квесту, что бы клиент ни написал в `scope`. Приватный урок ищется по своему
 * пути и подтверждает приватную область только если действительно там лежит. Ни того ни другого —
 * вид неизвестен, и цена берётся по самой дорогой известной ставке.
 */
/**
 * Пишет записи о перерасходе — по одной на вид заряда, у которого он есть.
 *
 * Обоснование внутри записи, а не выводимое из неё: оператор читает её, не перечитывая попытки.
 */
async function writeOverspendRecords(uid, inputs, now) {
  if (!inputs || !inputs.attemptIds.length) return;
  if (inputs.economy.auditEnabled === false) return;
  const kinds = [["STANDARD", inputs.standard], ["PLASMA", inputs.plasma]];
  for (const [kind, side] of kinds) {
    if (side.claimed <= 0) continue;
    const verdict = overspendVerdict({
      claimed: side.claimed,
      storedPoints: side.storedPoints,
      storedUpdatedAtMs: side.storedUpdatedAtMs,
      windowEndMs: now,
      rules: side.rules,
      ownedSlots: side.ownedSlots,
      regenMs: regenMsFor(side.rules, inputs.hasPremium),
      clockSkewToleranceMs: inputs.economy.clockSkewToleranceMs,
      pointsPerCharge: POINTS_PER_CHARGE,
    });
    if (verdict.surplus <= 0) continue;
    const record = overspendRecord({
      uid,
      chargeKind: kind,
      windowStartMs: inputs.windowStartMs,
      windowEndMs: now,
      storedPoints: side.storedPoints,
      storedUpdatedAtMs: side.storedUpdatedAtMs,
      constantsVersion: inputs.economy.version,
      verdict,
      attemptIds: inputs.attemptIds,
      recordedAtMs: now,
    });
    await db.doc(`${OVERSPEND_AUDIT_PATH}/${record.id}`).set(record, {merge: true});
  }
}

async function readLessonContexts(events) {
  const pending = new Map();
  for (const event of events) {
    const key = lessonContextKey(event);
    if (!pending.has(key)) pending.set(key, resolveLessonContext(event));
  }
  const entries = await Promise.all([...pending].map(async ([key, promise]) => [key, await promise]));
  return Object.fromEntries(entries);
}

async function resolveLessonContext(event) {
  const unknown = {scope: normalizeScope(event.scope), questId: "", quest: null, resolved: false};
  if (isSingleDocumentId(event.lessonId)) {
    const lesson = await db.doc(`lessons/${event.lessonId}`).get();
    if (lesson.exists) {
      const themeId = stringValue((lesson.data() || {}).themeId);
      const theme = isSingleDocumentId(themeId) ? await db.doc(`themes/${themeId}`).get() : null;
      const sectionId = theme && theme.exists ? stringValue((theme.data() || {}).sectionId) : "";
      const section = isSingleDocumentId(sectionId) ? await db.doc(`sections/${sectionId}`).get() : null;
      const questId = section && section.exists ? stringValue((section.data() || {}).questId) : "";
      const quest = isSingleDocumentId(questId) ? await db.collection("quests").doc(questId).get() : null;
      return {
        scope: PUBLIC_SCOPE,
        questId,
        quest: quest && quest.exists ? (quest.data() || {}) : null,
        resolved: true,
      };
    }
  }
  const ids = [event.ownerUid, event.catalogId, event.questId, event.sectionId, event.themeId, event.lessonId];
  if (normalizeScope(event.scope) === PRIVATE_SCOPE && ids.every(isSingleDocumentId)) {
    const lesson = await db.doc(privateLessonPath(...ids)).get();
    if (lesson.exists) {
      const quest = await db.doc(privateQuestPath(event.ownerUid, event.catalogId, event.questId)).get();
      return {scope: PRIVATE_SCOPE, questId: event.questId, quest: quest.exists ? (quest.data() || {}) : null, resolved: true};
    }
  }
  return unknown;
}

function lessonContextKey(event) {
  return [normalizeScope(event.scope), event.ownerUid || "", event.catalogId, event.questId,
    event.sectionId, event.themeId, event.lessonId].join("\u0000");
}

/** Годится ли строка в имя одного документа Firestore. */
function isSingleDocumentId(value) {
  const id = stringValue(value);
  return id.length > 0 && id.length <= 1500 && !id.includes("/") && id !== "." && id !== "..";
}

const {
  DECISION_DENIED,
  DECISION_EXECUTE,
  DECISION_REPLAY,
  DECISION_WAIT,
  STATE_RESERVED,
  completionRecord,
  gateVerdict,
  parseMutationPayload,
  readExpectedVersion,
  resolveOperation,
  validateMutation,
  versionConflictDetail,
  versionVerdict,
} = require("./mutation-queue.js");

const {
  TERMS_CONSENT_DETAIL,
  payableTermsProjection,
  readCapturedTerms,
  termsVerdict,
} = require("./mutation-terms.js");

// Единственное место, где рождается новое значение version (AD-12). Ни один узел не получает
// число, присланное клиентом, и ни одно место не считает следующую версию своим способом.
const {
  currentVersion: currentEntityVersion,
  nextVersionAtPath,
  setVersioned,
  versionsByPath,
} = require("./entity-version.js");

/**
 * Что клиент говорит в деталях, чтобы отличить «ещё нет» от «нет».
 *
 * Сам по себе FAILED_PRECONDITION в этом проекте означает отказ («не хватает ноликов»), поэтому
 * повторяемое ожидание помечается отдельно. Значение обязано совпадать с
 * PRECONDITION_PENDING_DETAIL в platform/firebase/.../network/CallableErrors.kt.
 */
const PRECONDITION_PENDING_DETAIL = "precondition-pending";

/**
 * Обработчики отложенных мутаций: имя операции -> async (uid, payload, nowMs) => result.
 *
 * Пуст намеренно. Единственный приёмник существует раньше, чем в него переезжает первое действие:
 * жизненный цикл ключа, резервирование и повтор отлаживаются здесь, на пустом реестре, а не под
 * первой же денежной операцией. Регистрация настоящих операций — эпик 5.
 */
/**
 * Что умеет очередь.
 *
 * Пусто по умолчанию и наполняется по одной операции: незнакомая операция — окончательный отказ,
 * а не повод повторять, поэтому добавление сюда и есть решение «это действие можно отложить».
 * Критерий отложимости — AD-3: результат выводится из синхронизированного контента, а не из
 * состояния, которого у клиента нет.
 *
 * Разблокировка урока подходит: цену считает сервер из времени самого урока, повтор безопасен по
 * набору купленного, и без сети игрок всё равно не увидит цену раньше отправки.
 */
/**
 * Идентификатор записи ревью для мутации из очереди.
 *
 * Ключ уже проверен приёмником (`[A-Za-z0-9_-]`, до 128 символов), поэтому годится в id
 * документа как есть. Префикс отличает его от записей, созданных прямым callable по часам.
 */
function reviewIdForMutation(mutationId) {
  return `m_${stringValue(mutationId)}`;
}

const MUTATION_HANDLERS = {
  // Разблокировка урока. Имя в пространстве владеющей фичи (NFR4): раньше ключ был голым
  // `UNLOCK_LESSON`, и это единственное имя реестра, у которого фичи не было. Клиентская половина
  // — `OutboxOperations.UNLOCK_LESSON`, и совпадение этих двух строк сторожит
  // `OutboxOperationsRegistryTest`.
  "lesson.UNLOCK": (uid, payload) => applyLessonUnlock(uid, payload),
  // Прохождение урока. Приёмник у него теперь один: очередь несёт по одному прохождению за
  // запись, а пакетный callable остаётся для синхронной отправки — тело у обоих общее.
  "lesson_runner.SUBMIT_ATTEMPT": (uid, payload) => applyLessonResultEvents(uid, [payload || {}]),
  // Оценка квеста.
  "lesson_runner.SUBMIT_RATING": (uid, payload) => applyQuestRatingEvents(uid, [payload || {}]),
  // Квитанция готовности онлайн-перехода next-question (ONLINE-O2 §4, история 2.6). Пер-вопросная
  // квитанция перехода, не терминальный расчёт: deadline возвращается из документа окна — тот,
  // что закрепила серверная выдача, — и никогда не читается из тела ACK. Repeat ключа отвечает
  // сохранённой квитанцией силами самого приёмника, до обработчика не доходя.
  "lesson_runner.READY_ACK": (uid, payload, now) => applyLessonReadyAck(uid, payload || {}, now),
  // The opening itself owns a permanent receipt; queue leases may expire without rerolling it.
  "economy.OPEN_GIFT_BOX": async (uid, payload) =>
    (await callBoxOperation({auth: {uid}, data: payload}, BOX_KIND.OPEN)).response,
  // Заявка черновика на арену. Раньше она писалась в Firestore напрямую и проверку ключа
  // обходила в принципе — теперь идёт тем же путём, что и всё остальное (AD-6).
  "quest_authoring.SUBMIT_ARENA": (uid, payload) => applyArenaSubmission(uid, payload),
  // Ручное закрытие приватной ссылки (AD-29/UX-16, история 8.3). Откладывается по AD-3: эффект —
  // одно поле документа, тело не ведёт денег и не читает платных таблиц; повтор по ключу возвращает
  // тот же результат, а закрытие идемпотентно и по сути. Re-enable решением не назначен: ссылка
  // закрывается навсегда, и перезаявка её не открывает — рантайм настроек перезаявкой не пишется.
  "quest_authoring.CLOSE_PRIVATE_QUIZ_LINK": (uid, payload) => applyClosePrivateQuizLink(uid, payload),
  // Решение ревьюера по заданию. Начисление репутации в теле идёт вне транзакции, и защищает его
  // от повтора только ключ: вторая доставка получает сохранённый ответ и до обработчика не доходит.
  "quest_authoring.SUBMIT_REVIEW": (uid, payload, _now, mutationId) =>
    applySubmitReviewAction(uid, payload, {reviewId: reviewIdForMutation(mutationId)}),
  // Перенос публичного квеста на полку. Полка серверно-защищена (AD-25): клиент кладёт в очередь
  // намерение, а новое значение приезжает к нему журналом изменений.
  "quest.SET_SHELF": (uid, payload) => applySetPublicQuestShelf(uid, payload),
  // Снятие своего лота с продажи. Детерминировано по содержимому и потому откладывается (AD-3),
  // в отличие от покупки. Лот, купленный за время ожидания, — окончательный отказ.
  "profile.CANCEL_NICKNAME_LISTING": (uid, payload) => applyCancelNicknameListing(uid, payload),
  "profile.CANCEL_LOGO_LISTING": (uid, payload) => applyCancelLogoListing(uid, payload),
  // Приватный отчёт об ошибке урока — общий inbox (AD-20). Откладывается по AD-3: текст про весь
  // урок и его id уже лежат в теле, адресата (разрешённого автора) сервер выводит сам из
  // документа размещения, а повтор по ключу возвращает ту же запись.
  "lesson_report.SUBMIT_ERROR": (uid, payload) => applyLessonErrorReport(uid, payload),
  // Публикация серии опросов единым draft (UX-O7 §6.2, история 8.2). Откладывается по AD-3: всё
  // содержание серии едет в теле, эффекта вне его нет. Повтор того же намерения (тот же ключ)
  // возвращает сохранённую revision; повторная публикация того же содержания — no-op без
  // новой revision: переоткрывать голосование переизданием нельзя.
  "polls.PUBLISH_SERIES": (uid, payload) => applyPublishPollSeries(uid, payload),
  // Голос опроса (UX-O7 §2/§3, история 8.2). Тело — один замороженный набор (AD-6/AD-28);
  // уникальность (участник, серия, revision, вопрос) — серверная, документом с
  // детерминированным id: голос под новым ключом (переустановка, второе устройство) упирается
  // в ту же уникальность и не создаёт второй голос (§3).
  "polls.SUBMIT_VOTE": (uid, payload) => applyPollVote(uid, payload),
  // Подача попытки приватного квиза (AD-29/AD-31, история 8.4). Единственная дверь — гейт
  // старта: попытка бежит по замороженной им записи `starts/{startId}`, и ключ идемпотентности
  // подачи — сам startId: повтор возвращает сохранённый терминал, лимит второй раз не тратится.
  // Бесплатно и без согласия с платными условиями (8.5): ни одной строки в книге движений.
  "private_quiz.SUBMIT_ATTEMPT": (uid, payload) => applyPrivateQuizAttemptSubmission(uid, payload),
  // Мутации сессии соревнования — ANSWER/ADVANCE/READY_ACK (история 5.9). Владелец берётся из
  // авторизации, а не из тела; uid + payload проходят в контроллер без изменений — форму тела
  // и владение сессией сторожит сам контроллер, идемпотентность по operationId уже серверная.
  // Отказ контроллера уходит в очередь HttpsError со структурной причиной (`details.reason`),
  // поэтому и из очереди клиент читает те же причины, что и из прямого вызова.
  "tournament.SESSION_OP": (uid, payload) =>
    courseTournaments.session(uid, payload || {}).catch((error) => {
      throw courseTournamentFailure(error);
    }),
};

/**
 * Чья версия сверяется с `expected_version` — по одной записи на версионируемую операцию.
 *
 * Ядро очереди в тело мутации не смотрит и о сущностях не знает (AD-5): какую именно сущность
 * версионирует операция, отвечает только сама операция, и ответ живёт здесь, рядом с её
 * обработчиком, а не внутри приёмника.
 *
 * Операции в реестре нет — значит, она неверсионируемая: `expected_version` от неё не ждут, а если
 * он всё же пришёл, это несовпадение клиента с сервером, а не конфликт данных.
 *
 * Заявка на арену сверяется с приватным документом квеста — ровно с тем, из которого клиент читает
 * `serverRevision` (`FirebaseQuestPrivateRemoteDataSource`). Сверять с публичным `quests/{id}`
 * нельзя: клиент прислал бы число, прочитанное из одного документа, а сервер сравнил бы его с
 * числом из другого. Расходиться этим двум не даёт `writeQuestVersionMirror` — публикация,
 * перекладка по полкам и снятие с полок кладут одну и ту же назначенную версию в оба документа.
 *
 * Набор версий всех изменённых узлов дерева авторитетным быть не может: автор правит черновик
 * целиком, и «моя редакция выросла из версии N» — утверждение про квест, а не про каждый урок
 * отдельно.
 */
const MUTATION_VERSION_SOURCES = {
  "quest_authoring.SUBMIT_ARENA": (uid, payload) =>
    readPrivateQuestVersion(uid, payload && payload.draft && payload.draft.catalogId, payload && payload.draft && payload.draft.id),
};

/**
 * Чьи мутации обязаны назвать согласие с платными условиями (CAP-16, серверная половина AD-5).
 *
 * Критерий один: вердикт операции читает согласованную таблицу `configs/economy`. Сегодня это
 * прохождение урока — цена попытки (`activityPrice`) и потолок зарядов при списании берутся
 * из таблицы в момент применения; выполнение по ценам, с которыми игрок не соглашался, и есть
 * скрытая переоценка. Добавление платной операции — добавление её сюда рядом с обработчиком.
 *
 * Кого здесь нет и почему. Разблокировка урока цену берёт не из таблицы, а из времени самого
 * урока (`unlockPrice` по allocated seconds) — сверять согласие с таблицей не с чем. Оценка,
 * квитанция READY_ACK, заявка на арену, решение ревьюера, полка и снятие лотов денег не читают
 * вовсе. Открытие бокса — начисление, а не списание.
 */
const MUTATION_TERMS_OPERATIONS = new Set(["lesson_runner.SUBMIT_ATTEMPT"]);

/**
 * Текущая версия квеста по приватному документу автора.
 *
 * Владелец — из авторизации, а не из тела: путь к приватному документу и есть проверка права, и
 * подставленный в тело чужой `ownerUid` увёл бы сверку в чужое поддерево.
 *
 * Поля `version` в документе нет — ноль: квест ещё ни разу не публиковался, и его версии не
 * существует. Ноль сходится с отсутствующим `expected_version` (`versionVerdict`), то есть первая
 * публикация проходит, а вторая — уже нет.
 */
async function readPrivateQuestVersion(uid, catalogId, questId) {
  const catalog = stringValue(catalogId);
  const id = stringValue(questId);
  if (!catalog || !id) {
    throw new HttpsError("invalid-argument", "draft.catalogId and draft.id are required for a versioned submission");
  }
  const snapshot = await db.doc(privateQuestPath(uid, catalog, id)).get();
  return currentEntityVersion(snapshot);
}

/**
 * Настройки приватной ссылки из документа квеста, нормированные гейтом; null — квеста нет.
 *
 * Нормирование здесь не украшение: документ мог написать и старый клиент, и отсутствующие или
 * испорченные поля гейт уже обслуживает дефолтами AD-29 и громким отказом соответственно —
 * писатель настроек обязан видеть ровно ту форму, которую потом читает дверь.
 */
async function readPrivateQuizSettings(ownerUid, catalogId, questId) {
  const snapshot = await db.doc(privateQuestPath(ownerUid, stringValue(catalogId), stringValue(questId))).get();
  if (!snapshot.exists) return null;
  return normalizeSettings(snapshot.data() && snapshot.data().privateQuizSettings);
}

/**
 * Заявка на арену из очереди.
 *
 * Владельца берём из авторизации, а не из тела: подписать чужой заявкой свой черновик — ровно то,
 * что проверка обязана не пустить. Дальше работает тот же обработчик, что и у документа,
 * созданного триггером, — второй копии этой логики не существует.
 */
/**
 * Приватный отчёт об ошибке урока — тело, общее для очереди и (в будущем) прямого вызова.
 *
 * Форма намеренно узкая: `reportId`, `lessonId`, текст про весь урок. Полей вопросов здесь нет по
 * построению (AD-20) — отчёт не может переоткрыть ни вопрос, ни ответ, потому что таких полей
 * приёмник не принимает вовсе. Адресат выводится сервером из документа размещения: тело не
 * называет получателя, подставленный в него чужой uid адресацию не меняет.
 *
 * Отказ «урока нет на размещении» окончательный: у записи, которая его получил, не появится
 * адресат от повтора — локальную половину уводит в карантин владеющая фича.
 */
async function applyLessonErrorReport(uid, payload) {
  const data = payload || {};
  // Тело — только три named-поля: лишний ключ (токен, payload вопроса, будущая форма) —
  // несовпадение контракта, а не повод что-то записать (AD-20: исключённое не доезжает до базы).
  const unknown = Object.keys(data).filter((key) => !["reportId", "lessonId", "text"].includes(key));
  if (unknown.length > 0) {
    throw new HttpsError("invalid-argument", `Unsupported report fields: ${unknown.sort().join(", ")}`);
  }
  const reportId = stringValue(data.reportId);
  if (!/^[A-Za-z0-9_-]{1,128}$/.test(reportId)) {
    throw new HttpsError("invalid-argument", "reportId is required");
  }
  const lessonId = stringValue(data.lessonId);
  if (!/^[A-Za-z0-9_-]{1,128}$/.test(lessonId)) {
    throw new HttpsError("invalid-argument", "lessonId is required");
  }
  const text = stringValue(data.text).trim();
  if (!text) throw new HttpsError("invalid-argument", "text is required");
  if (text.length > 1000) throw new HttpsError("invalid-argument", "text is too long");

  const reportRef = db.collection("lesson_error_reports").doc(reportId);
  // Ручей защищает ключ приёмника (AD-1), но обработчик может умереть после записи, до неё —
  // резерв протухнет и повтор придёт заново. Читаем документ: повтор возвращает то, что уже
  // записано, а не вторую запись с новым моментом.
  const existing = await reportRef.get();
  if (existing.exists) {
    const stored = existing.data() || {};
    return {reportId, lessonId: stringValue(stored.lessonId), createdAtMs: numberValue(stored.createdAtMs, 0)};
  }

  const placementSnapshot = await db.collection("lesson_placements").doc(lessonId).get();
  const lessonAuthorUid = nullableString(
    placementSnapshot.exists ? placementSnapshot.data().authorUid : null,
  );
  if (!lessonAuthorUid) {
    throw new HttpsError("failed-precondition", `Lesson ${lessonId} is not placed`);
  }

  const now = Date.now();
  await reportRef.set(
    clean({
      reportId,
      lessonId,
      lessonAuthorUid,
      reporterUid: uid,
      text,
      schemaVersion: 1,
      createdAtMs: now,
    }),
  );
  return {reportId, lessonId, createdAtMs: now};
}

/** Годится ли строка в идентификатор серии/варианта опроса (короткий charset — и в id документа). */
function isPollId(value) {
  return /^[A-Za-z0-9_-]{1,64}$/.test(stringValue(value));
}

/**
 * Нормализует содержание серии из тела публикации: канонические вопросы с опциями или бросок.
 *
 * Правила UX-O7 §2 и AD-28: варианты 2–8, single/multiple, без correctness/сложности и уроковой
 * иерархии; вопрос идентичен `(revisionId, questionIndex)`, поэтому индексы канонизируются
 * позициями массива. Тексты тримируются: пустая строка после трима — отказ.
 */
function normalizePollSeriesDraft(data) {
  const questions = data.questions.map((question, index) => {
    if (!isPlainObject(question)) {
      throw new HttpsError("invalid-argument", `Question ${index} is not an object`);
    }
    const unknown = Object.keys(question).filter((key) =>
      !["questionIndex", "text", "mode", "options"].includes(key));
    if (unknown.length > 0) {
      throw new HttpsError("invalid-argument", `Unsupported question fields: ${unknown.sort().join(", ")}`);
    }
    if (numberValue(question.questionIndex, -1) !== index) {
      throw new HttpsError("invalid-argument", "questionIndex must equal the array position");
    }
    const text = stringValue(question.text).trim();
    if (!text || text.length > 500) {
      throw new HttpsError("invalid-argument", `Question ${index} text is required (<= 500 chars)`);
    }
    const mode = stringValue(question.mode);
    if (mode !== "SINGLE" && mode !== "MULTIPLE") {
      throw new HttpsError("invalid-argument", `Question ${index} mode must be SINGLE or MULTIPLE`);
    }
    if (!Array.isArray(question.options) || question.options.length < 2 || question.options.length > 8) {
      throw new HttpsError("invalid-argument", `Question ${index} needs 2..8 options`);
    }
    const seenOptions = new Set();
    const options = question.options.map((option) => {
      if (!isPlainObject(option)) {
        throw new HttpsError("invalid-argument", `Question ${index} option is not an object`);
      }
      const optionUnknown = Object.keys(option).filter((key) => !["optionId", "text"].includes(key));
      if (optionUnknown.length > 0) {
        throw new HttpsError("invalid-argument", `Unsupported option fields: ${optionUnknown.sort().join(", ")}`);
      }
      const optionId = stringValue(option.optionId);
      if (!isPollId(optionId)) {
        throw new HttpsError("invalid-argument", `Question ${index} optionId is required`);
      }
      if (seenOptions.has(optionId)) {
        throw new HttpsError("invalid-argument", `Question ${index} duplicates optionId ${optionId}`);
      }
      seenOptions.add(optionId);
      const optionText = stringValue(option.text).trim();
      if (!optionText || optionText.length > 200) {
        throw new HttpsError("invalid-argument", `Question ${index} option text is required (<= 200 chars)`);
      }
      return {optionId, text: optionText};
    });
    return {questionIndex: index, text, mode, options};
  });
  return questions;
}

function isPlainObject(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

/**
 * Публикация серии опросов (UX-O7 §6.2, история 8.2): единый draft становится опубликованной
 * revision. Серия, за которой автор редактировал устаревшую revision, отказывается поименованным
 * конфликтом — клиент перечитывает список и решает сам; сервер не решает за автора.
 *
 * Идемпотентность двух слоёв: ключ мутации защищает повтор намерения (тот же ключ — тот же
 * ответ), а одинаковое содержание поверх текущей revision — no-op без новой revision (§6.2:
 * переиздание не переписывает и не «переоткрывает» принятые голоса).
 */
async function applyPublishPollSeries(uid, payload) {
  const data = payload || {};
  const unknown = Object.keys(data).filter((key) =>
    !["seriesId", "title", "expectedRevisionId", "questions"].includes(key));
  if (unknown.length > 0) {
    throw new HttpsError("invalid-argument", `Unsupported series fields: ${unknown.sort().join(", ")}`);
  }
  const seriesId = stringValue(data.seriesId);
  if (!isPollId(seriesId)) throw new HttpsError("invalid-argument", "seriesId is required");
  const title = stringValue(data.title).trim();
  if (!title || title.length > 200) {
    throw new HttpsError("invalid-argument", "title is required (<= 200 chars)");
  }
  if (!Number.isSafeInteger(data.expectedRevisionId) || data.expectedRevisionId < 0) {
    throw new HttpsError("invalid-argument", "expectedRevisionId is required");
  }
  if (!Array.isArray(data.questions) || data.questions.length < 1 || data.questions.length > 50) {
    throw new HttpsError("invalid-argument", "series needs 1..50 questions");
  }
  const questions = normalizePollSeriesDraft(data);

  const seriesRef = db.collection("poll_series").doc(seriesId);
  const existing = await seriesRef.get();
  const now = Date.now();

  if (existing.exists) {
    const stored = existing.data() || {};
    const currentRevisionId = numberValue(stored.revisionId, 0);
    const sameContent = stored.revision &&
      JSON.stringify(stored.revision.questions) === JSON.stringify(questions) &&
      stringValue(stored.title) === title;
    // Повтор намерения после протухшего резерва ключа: тот же ожидаемый базис уже опубликован
    // (current = expected + 1) и содержание то же — возвращаем то, что уже стоит, без второй
    // revision.
    if (currentRevisionId === data.expectedRevisionId + 1 && sameContent) {
      return {seriesId, revisionId: currentRevisionId, questionCount: questions.length};
    }
    if (currentRevisionId === data.expectedRevisionId) {
      if (sameContent) {
        // Переиздание того же содержания — no-op: новая revision переоткрыла бы голосование
        // с нуля, а §6.2 переизданием принятые голоса не переписывает и не «переоткрывает».
        return {seriesId, revisionId: currentRevisionId, questionCount: questions.length};
      }
      const revisionId = currentRevisionId + 1;
      await seriesRef.set(clean({
        seriesId,
        authorUid: uid,
        title,
        revisionId,
        revision: {revisionId, publishedAtMs: now, questions},
        schemaVersion: 1,
        updatedAtMs: now,
        createdAtMs: numberValue(stored.createdAtMs, now),
      }), {merge: false});
      return {seriesId, revisionId, questionCount: questions.length};
    }
    throw new HttpsError(
      "failed-precondition",
      `POLL_SERIES_REVISION_CONFLICT: expected ${data.expectedRevisionId}, current ${currentRevisionId}`,
    );
  }

  if (data.expectedRevisionId !== 0) {
    throw new HttpsError(
      "failed-precondition",
      `POLL_SERIES_REVISION_CONFLICT: expected ${data.expectedRevisionId}, current 0 (series does not exist)`,
    );
  }
  await seriesRef.set(clean({
    seriesId,
    authorUid: uid,
    title,
    revisionId: 1,
    revision: {revisionId: 1, publishedAtMs: now, questions},
    schemaVersion: 1,
    updatedAtMs: now,
    createdAtMs: now,
  }), {merge: false});
  return {seriesId, revisionId: 1, questionCount: questions.length};
}

/**
 * Голос опроса (UX-O7 §2/§3, история 8.2): один подтверждённый непустой набор участника на вопрос
 * тройки идентичности. Уникальность `(participant, seriesId, revisionId, questionIndex)`
 * держит документ с детерминированным id — она серверная и переживает переустановку клиента:
 * повтор под новым ключом операции читает тот же документ и возвращает принятый набор,
 * не создавая второй голос.
 */
async function applyPollVote(uid, payload) {
  const data = payload || {};
  const unknown = Object.keys(data).filter((key) =>
    !["seriesId", "revisionId", "questionIndex", "selectedOptionIds", "confirmedAtMs"].includes(key));
  if (unknown.length > 0) {
    throw new HttpsError("invalid-argument", `Unsupported vote fields: ${unknown.sort().join(", ")}`);
  }
  const seriesId = stringValue(data.seriesId);
  if (!isPollId(seriesId)) throw new HttpsError("invalid-argument", "seriesId is required");
  if (!Number.isSafeInteger(data.revisionId) || data.revisionId < 1) {
    throw new HttpsError("invalid-argument", "revisionId is required");
  }
  if (!Number.isSafeInteger(data.questionIndex) || data.questionIndex < 0) {
    throw new HttpsError("invalid-argument", "questionIndex is required");
  }
  const selected = Array.isArray(data.selectedOptionIds) ? data.selectedOptionIds.map(stringValue) : [];
  if (selected.length < 1 || selected.length > 8 || new Set(selected).size !== selected.length ||
    selected.some((optionId) => !isPollId(optionId))) {
    throw new HttpsError("invalid-argument", "selectedOptionIds must be 1..8 distinct option ids");
  }
  if (!Number.isSafeInteger(data.confirmedAtMs) || data.confirmedAtMs < 0) {
    throw new HttpsError("invalid-argument", "confirmedAtMs is required");
  }

  const seriesSnapshot = await db.collection("poll_series").doc(seriesId).get();
  if (!seriesSnapshot.exists) {
    throw new HttpsError("failed-precondition", "POLL_SERIES_NOT_FOUND");
  }
  const series = seriesSnapshot.data() || {};
  const currentRevisionId = numberValue(series.revisionId, 0);
  if (data.revisionId !== currentRevisionId) {
    // Голос существует только для опубликованной revision (UX-O7 §6.2): устаревшая —
    // окончательный отказ, новую revision клиент увидит при перечитывании.
    throw new HttpsError(
      "failed-precondition",
      `POLL_SERIES_REVISION_MISMATCH: voted ${data.revisionId}, published ${currentRevisionId}`,
    );
  }
  const questions = Array.isArray((series.revision || {}).questions) ? series.revision.questions : [];
  const question = questions[data.questionIndex];
  if (!isPlainObject(question) || numberValue(question.questionIndex, -1) !== data.questionIndex) {
    throw new HttpsError("invalid-argument", "questionIndex is out of range");
  }
  const knownOptions = new Set((question.options || []).map((option) => stringValue(option.optionId)));
  const unknownOptions = selected.filter((optionId) => !knownOptions.has(optionId));
  if (unknownOptions.length > 0) {
    throw new HttpsError("invalid-argument", `Unknown option ids: ${unknownOptions.sort().join(", ")}`);
  }
  // Кардинальность режима — часть определения серии: SINGLE-вопрос с двумя отметками —
  // тело, собранное вопреки контракту, а не другой голос.
  if (question.mode === "SINGLE" && selected.length !== 1) {
    throw new HttpsError("invalid-argument", "SINGLE question accepts exactly one option");
  }

  const voteId = `${seriesId}-r${data.revisionId}-q${data.questionIndex}-${uid}`;
  const voteRef = db.collection("poll_votes").doc(voteId);

  // Ручей защищает ключ приёмника, но повтор по протухшему резерву и голос под новым ключом
  // (переустановка, второе устройство) приходят в обработчик заново: уникальность — здесь.
  const stored = await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(voteRef);
    if (snapshot.exists) return snapshot.data() || {};
    const acceptedAtMs = Date.now();
    const vote = clean({
      voteId,
      seriesId,
      revisionId: data.revisionId,
      questionIndex: data.questionIndex,
      participantUid: uid,
      selectedOptionIds: selected,
      clientConfirmedAtMs: data.confirmedAtMs,
      acceptedAtMs,
      schemaVersion: 1,
    });
    transaction.create(voteRef, vote);
    return vote;
  });
  return {
    seriesId,
    revisionId: data.revisionId,
    questionIndex: data.questionIndex,
    selectedOptionIds: stringArray(stored.selectedOptionIds),
    acceptedAtMs: numberValue(stored.acceptedAtMs, 0),
  };
}

async function applyArenaSubmission(uid, payload) {
  const data = payload || {};
  const submissionId = stringValue(data.submissionId);
  if (!submissionId) throw new HttpsError("invalid-argument", "submissionId is required");
  if (stringValue(data.ownerUid) && stringValue(data.ownerUid) !== uid) {
    throw new HttpsError("permission-denied", "Submission ownerUid must match authenticated uid");
  }
  const request = normalizeRequest({...data, ownerUid: uid}, submissionId);
  if (request.questions.length === 0) {
    // Заявка без вопросов существовать не может: либо тело собрано неверно, либо это строка,
    // перенесённая миграцией 5 → 6 из очереди, которая содержимого черновика не хранила.
    // Повтор этого не изменит — отказ окончательный, и локальную половину откатит владеющая
    // фича по карантину (AD-28), вернув черновик автору в редактируемое состояние.
    throw new HttpsError("invalid-argument", "Submission carries no questions");
  }
  // Срок приватной ссылки на сохранении может быть только в будущем или отсутствовать (AD-29).
  // Истёкший к моменту сохранения — отказ до всякой записи: ссылка, рождающаяся уже закрытой,
  // была бы дверью, которую нельзя открыть. Отказ окончательный — время назад не идёт.
  if (request.draft.privateQuiz && request.draft.privateQuiz.linkExpiresAtMs !== null &&
      request.draft.privateQuiz.linkExpiresAtMs <= Date.now()) {
    throw new HttpsError("failed-precondition", "PRIVATE_QUIZ_EXPIRY_IN_PAST: link expiry must be in the future");
  }
  await assertLessonTitlesUnchanged(request);
  return processArenaRequest(request);
}

/**
 * Ручное закрытие приватной ссылки — решение автора (AD-29), единственное назначенное изменение
 * рантайма настроек.
 *
 * Закрытие не рвёт начатую попытку (AD-29): отказывают только НОВЫЕ старты, и потому решение
 * обязано быть упорядочено со стартом — транзакция. Тело — только адрес; владелец берётся из
 * авторизации, как во всей приватной половине. Запись журнала (`writeSyncChanges`) приносит
 * закрытие и на другие устройства автора: иначе выключенная на одном ссылка оставалась бы
 * включённой на экране другого до первого снимка.
 *
 * Re-enable (как и автопродление/перевыпуск) решением не назначен (UX-16): закрытое остаётся
 * закрытым, и повтор этой же мутации честно возвращает тот же результат.
 */
async function applyClosePrivateQuizLink(uid, payload) {
  const data = payload || {};
  const unknown = Object.keys(data).filter((key) => !["catalogId", "questId"].includes(key));
  if (unknown.length > 0) {
    throw new HttpsError("invalid-argument", `Unsupported close-link fields: ${unknown.sort().join(", ")}`);
  }
  const catalogId = stringValue(data.catalogId);
  const questId = stringValue(data.questId);
  if (!catalogId || !questId) {
    throw new HttpsError("invalid-argument", "catalogId and questId are required");
  }
  const questRef = db.doc(privateQuestPath(uid, catalogId, questId));
  // Часы журнала — серверные, как во всех четырёх журналах (AD-11): время берётся один раз
  // здесь и тем же числом датирует запись, которую прочитает курсор автора.
  const now = Date.now();
  return db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(questRef);
    if (!snapshot.exists) {
      throw new HttpsError("not-found", "Private quiz does not exist");
    }
    // Форма настроек — гейтова: закрытие не выдумывает поля, которых дверь не читает.
    const settings = normalizeSettings(snapshot.data() && snapshot.data().privateQuizSettings);
    transaction.set(questRef, {privateQuizSettings: {...settings, enabled: false}}, {merge: true});
    writeSyncChanges(
      transaction,
      db,
      {kind: "private_quest", id: questId, catalogId, ownerUid: uid},
      now,
    );
    return {closed: true};
  });
}

/**
 * Заголовок уже существующего урока неизменен (SPEC Constraints, история 4.5).
 *
 * Публикация перезаписывает документы заявки с `{merge: true}`, и заголовок с провода затёр бы
 * публичный молча. Клиентского пути переименовать существующий урок нет — заголовок заполняется
 * один раз при создании урока и едет по проводу без изменений, — поэтому расхождение здесь
 * значит ровно одно: тело заявки собрано вопреки этому правилу. Отказ до всякой записи: заявка
 * не создаётся, приватное дерево не пишется, ключ мутации освобождается обработчиком.
 */
async function assertLessonTitlesUnchanged(request) {
  for (const lesson of request.lessons) {
    const snapshot = await db.doc(`lessons/${lesson.id}`).get();
    if (!snapshot.exists) continue;
    const existingTitle = stringValue(snapshot.data() && snapshot.data().title);
    if (existingTitle !== lesson.title) {
      throw new HttpsError(
        "failed-precondition",
        `LESSON_TITLE_IMMUTABLE: lesson ${lesson.id} is already published with a different title`,
      );
    }
  }
}

/**
 * Единственный транспорт отложенной мутации (AD-6).
 *
 * Прямая запись в Firestore из очереди запрещена: проверить ключ идемпотентности можно только там,
 * где выполняется код. Здесь этот код и живёт.
 *
 * Ключ резервируется до эффекта и дописывается после (AD-1): уместить чужой обработчик в одну
 * транзакцию с записью ключа нельзя, а повтор по зарезервированному, но не завершённому ключу
 * никогда не считается выполненным — он ждёт, а не выполняет второй раз.
 */
exports.submitMutation = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  await requireProfile(uid);

  const shape = validateMutation(request.data);
  if (!shape.valid) throw new HttpsError("invalid-argument", shape.reason);

  // Форма `expected_version` проверяется до резервирования ключа: криво собранный конверт не должен
  // сжигать ключ, который клиенту потом нечем будет повторить.
  const expected = readExpectedVersion(request.data);
  if (!expected.valid) throw new HttpsError("invalid-argument", expected.reason);

  const {mutationId, operation} = shape;
  const resolved = resolveOperation(MUTATION_HANDLERS, operation);
  if (!resolved.known) {
    // Клиент новее сервера. Повтор этого не изменит, поэтому отказ окончательный.
    throw new HttpsError("invalid-argument", resolved.reason);
  }

  const body = parseMutationPayload(request.data.payload);
  if (!body.valid) throw new HttpsError("invalid-argument", body.reason);

  // Форма согласия проверяется до резервирования ключа по той же причине, что и форма
  // `expected_version`: криво собранный конверт не должен сжигать ключ, который клиенту потом
  // нечем будет повторить. Само отсутствие согласия — не ошибка формы: повтор известного ключа
  // возвращает сохранённое без всякой сверки, и его отсутствие обязательным не бывает.
  const captured = readCapturedTerms(request.data);
  if (!captured.valid) throw new HttpsError("invalid-argument", captured.reason);
  const requestFingerprint = attemptFingerprint(uid, {operation, payload: body.payload, expectedVersion: expected.expectedVersion});

  const now = Date.now();
  const keyRef = db.collection("mutation_keys").doc(safeDocId(mutationId));

  const gate = await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(keyRef);
    const existing = snapshot.exists ? snapshot.data() : null;
    if (existing && existing.uid === uid && (existing.operation !== operation ||
        (existing.requestFingerprint && existing.requestFingerprint !== requestFingerprint))) {
      throw new HttpsError("invalid-argument", "mutationId cannot be reused for another operation or payload");
    }
    // Решение — чистое (`gateVerdict`); транзакции остаётся только записать резервирование.
    const verdict = gateVerdict(existing, uid, mutationId, operation, now);
    if (verdict.decision === DECISION_EXECUTE) {
      verdict.reservation.requestFingerprint = requestFingerprint;
      // Принятые условия фиксируются в операции (AD-5): по записи ключа видно, под какой
      // редакцией таблицы эффект был применён. У операций без релевантных условий поля нет.
      if (captured.captured) verdict.reservation.termsVersion = captured.captured.version;
      transaction.set(keyRef, verdict.reservation);
    }
    return verdict;
  });

  if (gate.decision === DECISION_DENIED) {
    throw new HttpsError("permission-denied", "mutationId belongs to another account");
  }
  if (gate.decision === DECISION_REPLAY) {
    return {replayed: true, result: gate.result};
  }
  if (gate.decision === DECISION_WAIT) {
    // Повторяемое ожидание, а не отказ: другая попытка по тому же ключу ещё летит.
    throw new HttpsError("failed-precondition", gate.reason, PRECONDITION_PENDING_DETAIL);
  }

  const versionSource = MUTATION_VERSION_SOURCES[operation];
  if (expected.expectedVersion !== null && typeof versionSource !== "function") {
    // Клиент считает операцию версионируемой, а сервер — нет. Применить её мимо сверки значит
    // сделать вид, что сверка была: это отказ, а не конфликт.
    await releaseReservation(keyRef, gate.reservation);
    throw new HttpsError("invalid-argument", `operation ${operation} carries no version`);
  }
  if (typeof versionSource === "function") {
    // Сверка идёт и тогда, когда поля в конверте нет.
    //
    // Отсутствие `expected_version` у версионируемой операции — это утверждение «сущности ещё
    // нет», а не разрешение пропустить проверку. Пока отсутствие означало «не сверяем», сверку
    // выключало молчание клиента: приватный документ без поля `version` давал `serverRevision`
    // ноль, клиент опускал поле, и мутация проходила без единой проверки — то есть не проверялось
    // вообще ничего и никогда.
    const verdict = versionVerdict(expected.expectedVersion, await versionSource(uid, body.payload));
    if (verdict.conflict) {
      // Конфликт — не отказ (AD-24): эффект не применён, версия не сдвинулась, а ключ обязан
      // освободиться целиком. Останься он выполненным — повтор вернул бы пустой сохранённый
      // результат как успех; останься зарезервированным — новая мутация поверх серверной версии
      // ждала бы минуту таймаута.
      await releaseReservation(keyRef, gate.reservation);
      throw new HttpsError(
        "aborted",
        `Entity moved to version ${verdict.serverVersion}`,
        versionConflictDetail(verdict.serverVersion),
      );
    }
  }

  // Сверка согласия с текущей таблицей — после резервирования ключа и до обработчика, как сверка
  // версии: расхождение условий — не применение эффекта (AD-24), эффект по ним не считается, и
  // ключ обязан освободиться целиком. Повтор известного ключа сюда не доходит (он вернул
  // сохранённое ещё в гейте) — согласие повторно не переспрашивает. Согласовавший старые цены
  // игрок получит свежую таблицу при следующей синхронизации, согласится заново, и повтор той же
  // мутации пройдёт уже по новым.
  if (MUTATION_TERMS_OPERATIONS.has(operation)) {
    if (!captured.captured) {
      // Платная операция без согласия не выполняется: игроку сначала показывают текущие
      // условия, согласие фиксируется, и только тогда операция едет. Повтор этого не изменит,
      // поэтому отказ окончательный до нового согласия.
      await releaseReservation(keyRef, gate.reservation);
      throw new HttpsError("failed-precondition", `operation ${operation} requires captured payable terms`, {code: TERMS_CONSENT_DETAIL});
    }
    const terms = termsVerdict(captured.captured, payableTermsProjection(await readEconomyConstantsDocument()));
    if (!terms.sufficient) {
      await releaseReservation(keyRef, gate.reservation);
      throw new HttpsError("failed-precondition", "Payable terms changed since consent", {code: TERMS_CONSENT_DETAIL, change: terms.change});
    }
  }

  // Ключ едет в обработчик: телу, чей эффект не умещается в транзакцию с ключом, нужен свой
  // детерминированный идентификатор, чтобы повтор по протухшему резерву не применил его дважды (AD-1).
  let result;
  try {
    result = await resolved.handler(uid, body.payload, now, mutationId);
  } catch (error) {
    // Отказ тела — не результат: ключ освобождается, как при конфликте версии. Останься он
    // зарезервированным, честный повтор транзиентной ошибки ждал бы минуту таймаута, а
    // окончательный отказ держал бы в `mutation_keys` резерв, которому уже нечего защищать.
    await releaseReservation(keyRef, gate.reservation);
    throw error;
  }
  // A slow worker may have lost its lease. It must not overwrite a newer reservation/result.
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(keyRef);
    const current = snapshot.exists ? snapshot.data() || {} : {};
    if (current.state !== STATE_RESERVED || current.reservedAtMs !== gate.reservation.reservedAtMs ||
        current.requestFingerprint !== requestFingerprint) return;
    transaction.set(keyRef, completionRecord(gate.reservation, result, Date.now()));
  });
  return {replayed: false, result: result === undefined ? null : result};
});

/**
 * Снимает собственное резервирование ключа.
 *
 * Сравнение с `reservedAtMs` обязательно: между резервированием и этим вызовом ключ мог перейти к
 * другой попытке по таймауту брошенного резервирования, и удалить чужое значит разрешить два
 * одновременных выполнения — ровно то, ради чего ключ и существует (AD-1).
 */
async function releaseReservation(keyRef, reservation) {
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(keyRef);
    if (!snapshot.exists) return;
    const existing = snapshot.data() || {};
    if (existing.state !== STATE_RESERVED) return;
    if (numberValue(existing.reservedAtMs, -1) !== numberValue(reservation.reservedAtMs, -2)) return;
    transaction.delete(keyRef);
  });
}

// ─── Онлайн-переход next-question (ONLINE-O2, история 2.6) ──────────────────────────────────────

/**
 * Окна загрузки следующего вопроса, по документу на переход.
 *
 * Один переход = одна пара `(attemptId, completedQuestionIndex)`; идентификатор документа
 * детерминирован ею, поэтому повтор события завершения читает то же окно, а не открывает второе
 * (§7: потерянный ACK не создаёт нового окна).
 */
const LESSON_QUESTION_WINDOW_COLLECTION = "lesson_question_windows";

function lessonWindowRef(attemptId, completedQuestionIndex) {
  return db
    .collection(LESSON_QUESTION_WINDOW_COLLECTION)
    .doc(windowDocumentId(attemptId, completedQuestionIndex));
}

/** Обязательная строка тела: отсутствие — расхождение клиента с контрактом, а не пустота. */
function requiredWindowString(value, name) {
  const text = stringValue(value);
  if (!text) throw new HttpsError("invalid-argument", `${name} is required`);
  return text;
}

function wholeIndex(value, name) {
  const parsed = numberValue(value, -1);
  if (!Number.isInteger(parsed) || parsed < 0) {
    throw new HttpsError("invalid-argument", `${name} must be a non-negative integer`);
  }
  return parsed;
}

/** Что клиент видит об окне: identity перехода и захваченная конфигурация, без выдачи. */
function windowView(record) {
  return {
    schemaVersion: 1,
    windowId: record.windowId,
    attemptId: record.attemptId,
    completedQuestionIndex: record.completedQuestionIndex,
    nextQuestionIndex: record.nextQuestionIndex,
    state: record.state,
    windowSeconds: record.windowSeconds,
    allocatedSeconds: record.allocatedSeconds,
    openedAtMs: record.openedAtMs,
    expiresAtMs: record.expiresAtMs,
  };
}

/** Пул урока нужной сложности по серверному содержимому — то же правило, что у цены урока. */
async function lessonQuestionPoolSize(lessonId, isHard) {
  const snapshot = await db.collection("questions").where("lessonId", "==", lessonId).get();
  return lessonPoolSize(snapshot.docs.map(questionDocumentFor), isHard);
}

/**
 * Событие завершения вопроса и открытие окна загрузки (ONLINE-O2 §2–§3).
 *
 * Демо-поверхность перехода истории 2.6: момент завершения ставит сервер (свои часы, не тело
 * запроса), константа окна и секунды ответа следующего вопроса захватываются в документ при
 * открытии. Содержимое следующего вопроса сервер читает сам и отказ окончательно, если вопрос
 * чужой, архивный или его не существует: клиент не назначает серверные факты (AD-21). Повтор
 * события с тем же телом возвращает то же окно; с другим — конфликт (AD-6). После последнего
 * вопроса окно не начинается (§3) — пул считает сервер по содержимому урока.
 *
 * Тип завершения (принятый своевременный ответ или исходный expiry) здесь не разбирается: оба
 * события открывают одно и то же окно (§2), а их различение — дело расчёта попытки, который идёт
 * по `lesson_runner.SUBMIT_ATTEMPT` и ready-ACK не подменяется.
 */
exports.beginLessonQuestionWindow = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  await requireProfile(uid);
  const data = request.data || {};
  const attemptId = requiredWindowString(data.attemptId, "attemptId");
  const lessonId = requiredWindowString(data.lessonId, "lessonId");
  const lessonVersion = Math.max(0, Math.trunc(numberValue(data.lessonVersion, 0)));
  const completedQuestionIndex = wholeIndex(data.completedQuestionIndex, "completedQuestionIndex");
  const isHard = stringValue(data.difficulty, "HARD").toUpperCase() !== "EASY";

  // После последнего вопроса окно не начинается (§3): пул знает сервер, до любых полей выдачи.
  const poolSize = await lessonQuestionPoolSize(lessonId, isHard);
  if (completedQuestionIndex + 1 >= poolSize) {
    throw new HttpsError("invalid-argument", "no next question: the completed question is the last of the pool");
  }

  const nextQuestionId = requiredWindowString(data.nextQuestionId, "nextQuestionId");

  const intent = {attemptId, lessonId, lessonVersion, completedQuestionIndex, nextQuestionId, isHard};
  const docRef = lessonWindowRef(attemptId, completedQuestionIndex);
  const record = await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(docRef);
    const existing = snapshot.exists ? snapshot.data() : null;
    if (existing) {
      if (!sameWindowIntent(existing, intent)) {
        throw new HttpsError("invalid-argument", "window already opened for a different transition");
      }
      return existing;
    }
    // Содержимое вопроса читает сервер: секунды ответа считаются здесь и замораживаются в окне
    // (§3, captured config) — тело запроса на deadline не влияет (§5).
    const questionDoc = await transaction.get(db.collection("questions").doc(nextQuestionId));
    const question = questionDoc.exists ? questionDocumentFor(questionDoc) : null;
    if (!question || question.lessonId !== lessonId || question.archived === true) {
      throw new HttpsError("invalid-argument", `next question ${nextQuestionId} is not part of lesson ${lessonId}`);
    }
    const created = openQuestionWindow({
      ...intent,
      nextQuestionContent: questionRowFrom(question).content,
      nowMs: Date.now(),
    });
    transaction.set(docRef, created);
    return created;
  });
  return windowView(record);
});

/**
 * Запрос следующего вопроса — выдача внутри окна (ONLINE-O2 §3).
 *
 * Первая выдача стартует часы ответа серверным «сейчас» плюс захваченные секунды; повтор
 * возвращает тот же вопрос с тем же deadline и серверным остатком — вторую выдачу повтор не
 * создаёт (§3, §5). Окно, истекшее без выдачи, закрывается без ответа: попытке не объявляется
 * победа таймауту и не выдаётся платная попытка (§7).
 */
exports.fetchNextLessonQuestion = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  await requireProfile(uid);
  const data = request.data || {};
  const attemptId = requiredWindowString(data.attemptId, "attemptId");
  const completedQuestionIndex = wholeIndex(data.completedQuestionIndex, "completedQuestionIndex");
  const nowMs = Date.now();

  const docRef = lessonWindowRef(attemptId, completedQuestionIndex);
  const verdict = await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(docRef);
    const record = snapshot.exists ? snapshot.data() : null;
    const outcome = deliveryVerdict(record, nowMs);
    if (outcome.status === "ISSUE") {
      transaction.set(docRef, {...record, ...deliveryUpdate(outcome.issued)});
    }
    if (outcome.status === "WINDOW_EXPIRED" && record && record.state !== WINDOW_EXPIRED) {
      transaction.set(docRef, {...record, ...expiryUpdate(nowMs)});
    }
    return outcome;
  });

  if (verdict.status === "NO_WINDOW") {
    throw new HttpsError("invalid-argument", "unknown next-question window");
  }
  if (verdict.status === "WINDOW_EXPIRED") {
    return {
      status: "WINDOW_EXPIRED",
      windowId: verdict.record ? verdict.record.windowId : null,
      closedAtMs: nowMs,
      serverNowMs: nowMs,
    };
  }
  const issued = verdict.issued;
  return {
    status: "DELIVERED",
    windowId: verdict.record.windowId,
    questionIndex: issued.questionIndex,
    questionId: issued.questionId,
    issuedAtMs: issued.issuedAtMs,
    answerDeadlineMs: issued.answerDeadlineMs,
    remainderMs: verdict.status === "DELIVERED" ? verdict.remainderMs : Math.max(0, issued.answerDeadlineMs - nowMs),
    serverNowMs: nowMs,
  };
});

/**
 * Приёмник ready-ACK — серверная половина квитанции готовности (ONLINE-O2 §4, §6.2).
 *
 * Один приёмник со всеми мутациями (AD-7); дедуп и «повтор ключа возвращает сохранённый результат»
 * делает сам `submitMutation` до обработчика. Здесь только связь с окном: identity сверяется с
 * документом, а квитанция собирается из серверных фактов выдачи — ни moment, ни deadline из тела
 * не читаются (§5: замещающих часов нет; повтор возвращает уже закреплённый deadline).
 */
async function applyLessonReadyAck(uid, payload, now) {
  const attemptId = requiredWindowString(payload.attemptId, "attemptId");
  const windowId = requiredWindowString(payload.windowId, "windowId");
  const questionIndex = wholeIndex(payload.questionIndex, "questionIndex");
  if (stringValue(payload.userId) && stringValue(payload.userId) !== uid) {
    throw new HttpsError("permission-denied", "Ready-ack ownerUid must match authenticated uid");
  }
  if (questionIndex < 1) {
    throw new HttpsError("invalid-argument", "ready-ack names no delivered question");
  }
  const snapshot = await lessonWindowRef(attemptId, questionIndex - 1).get();
  const verdict = readyAckVerdict(snapshot.exists ? snapshot.data() : null, {attemptId, questionIndex, windowId}, now);
  if (!verdict.ok) throw new HttpsError(verdict.code, verdict.reason);
  return verdict.receipt;
}

exports.submitLessonResultEvents = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  await requireProfile(uid);
  return applyLessonResultEvents(uid, listMaps(request.data && request.data.attempts));
});

/**
 * Приём прохождений — тело, общее для прямого вызова и для очереди.
 *
 * Одно и то же действие приезжает двумя дорогами: синхронно, пока игрок смотрит на экран, и
 * отложенно из общей очереди, если сети не было. Логика обязана быть одна: жизни, награда и
 * турнирный зачёт, посчитанные двумя копиями, однажды разойдутся.
 */
async function applyLessonResultEvents(uid, attempts) {
  if (attempts.length > MAX_RESULT_EVENTS_PER_CALL) {
    throw new HttpsError("invalid-argument", `At most ${MAX_RESULT_EVENTS_PER_CALL} attempts are accepted per call`);
  }
  if (attempts.length === 0) return {accepted: 0};

  // A ticket owns its reservation and royalty carry. Each ticket therefore settles in its own
  // transaction, including its E2 result, rather than sharing a stale payer/author batch readset.
  if (attempts.some((attempt) => attempt.ticketId != null)) {
    const combined = {accepted: 0, reward: {skillPoints: 0, nolics: 0}, receipts: []};
    for (const attempt of attempts) {
      const result = attempt.ticketId != null ? await callArenaEntry("settle", {auth: {uid}, data: attempt}) :
        await applyLessonResultEvents(uid, [attempt]);
      combined.accepted += result.accepted;
      combined.reward.skillPoints += result.reward.skillPoints;
      combined.reward.nolics += result.reward.nolics;
      combined.receipts.push(...result.receipts);
      combined.lifePoints = result.lifePoints;
    }
    return combined;
  }

  const now = Date.now();
  const fingerprints = new Map();
  const events = attempts.map((item) => {
    // Тело читает `attempt-intake.js`: для устройства, посчитавшего себя само, — сегодняшний путь
    // проверка в проверку и сообщение в сообщение; для сложной попытки без цифр — заявка на то,
    // чтобы посчитал сервер. Кто именно считает, говорит `paymentRule`.
    const event = readSubmittedAttempt(item, uid);
    const fingerprint = attemptFingerprint(uid, item);
    if (fingerprints.has(event.attemptId) && fingerprints.get(event.attemptId) !== fingerprint) {
      throw new HttpsError("invalid-argument", "Conflicting payloads for one attemptId");
    }
    fingerprints.set(event.attemptId, fingerprint);
    if (!Number.isSafeInteger(event.lessonVersion)) throw new HttpsError("invalid-argument", "lessonVersion must be an integer");
    // Legacy queued records cannot prove completion rather than an abort. The new client must
    // explicitly name the terminal outcome; this field alone never bypasses scoring/count checks.
    if (item.completionKind != null && !["COMPLETED", "ABORTED"].includes(item.completionKind)) {
      throw new HttpsError("invalid-argument", "Unknown completionKind");
    }
    event.completed = item.completionKind === "COMPLETED";
    event.aborted = item.completionKind === "ABORTED";
    const content = contentKeysForEvent(event);
    const ref = db
      .collection(scopedCollection("result_events", event.scope))
      .doc(eventBucketId(event.attemptId))
      .collection("events")
      .doc(safeDocId(event.attemptId));
    return {event, content, ref, fingerprint};
  });
  // Everything this batch has to read, read before the transaction opens: Firestore wants every
  // read before the first write. Together rather than one after another — none of the four depends
  // on another's answer, and serialised they were four round trips where one would do.
  const [questionsByLesson, contexts, economy, replays] = await Promise.all([
    // What each lesson in this batch is worth, and what a server-scored attempt is judged against.
    readLessonQuestions(events.map((item) => item.event.lessonId)),
    // Вид попытки — а значит, её цену — решает сервер по квесту, который попытка называет
    // (CAP-16). До сих пор единственным именем сыгранного был `sourceShelf`, и его вычисляло
    // устройство: под прейскурантом клиент, объявивший `home` для турнира, платил бы 33 очка
    // вместо 500. Объявление остаётся в событии как диагностика; списание от него не зависит.
    readLessonContexts(events.map((item) => item.event)),
    readEconomyConstantsDocument(),
    // Какие из серверных попыток уже лежат: повтор не пересчитывается заново.
    readStoredAttemptScores(events),
  ]);
  const allocatedByLesson = allocatedSecondsFrom(questionsByLesson);
  // Попытки, которые сервер считает сам, считаются здесь — в том же слоте и по той же причине:
  // ключи урока и вопросы приватного урока это чтения. Ни одно из них не выполняется, пока в
  // пакете нет ни одной такой попытки, поэтому сегодняшний пакет стоит ровно столько же.
  applyServerScoring(events, await readLessonScoringSources(events, questionsByLesson), replays);
  for (const item of events) {
    const context = contexts[lessonContextKey(item.event)];
    item.event.declaredSourceShelf = item.event.sourceShelf;
    // Квест — тот, к которому урок принадлежит на самом деле; объявленный остаётся в записи.
    item.event.resolvedQuestId = context.questId;
    item.event.activityKind = activityKindForQuest(context.quest, context.scope);
    // Всё, что дальше читает полку — турнирная группа, грязные оценки, — читает выведенную.
    item.event.sourceShelf = sourceShelfForQuest(context.quest, context.scope);
    item.event.lifeCost = activityPrice(economy, item.event.activityKind);
  }

  let reward = {skillPoints: 0, nolics: 0};
  let remainingLifePoints = 0;
  let receipts = [];
  const ledgerRows = [];
  let overspendInputs = null;
  const touchedTournamentIds = new Set();
  await runEconomyTransaction(async (transaction) => {
    // Firestore may rerun this callback after a competing device commits. Claim settlement
    // rewrites scoring fields, so each run starts from the same immutable intake, not the last
    // callback's partially settled digits.
    const settlementEvents = events.map((item) => ({...item, event: structuredClone(item.event)}));
    touchedTournamentIds.clear();
    // Firestore requires every read before the first write.
    const userRef = db.collection("users").doc(uid);
    const userSnapshot = await transaction.get(userRef);
    const existingSnapshots = [];
    for (const item of settlementEvents) {
      existingSnapshots.push(await transaction.get(item.ref));
    }
    const placements = [];
    for (const item of settlementEvents) {
      placements.push(item.event.scope === PUBLIC_SCOPE ?
        await transaction.get(db.doc(`lesson_placements/${item.event.lessonId}`)) : null);
    }

    // Life points gate how much activity is paid for. Regeneration is derived from the elapsed
    // time here, so nothing has to run while the user is away. Accounts created before this
    // field existed start from a full tank rather than from zero.
    const userData = userSnapshot.exists ? userSnapshot.data() || {} : {};
    const premiumPlan = await advancePremiumInTransaction(transaction, uid, userData, economy, now);
    if (!premiumPlan.caughtUp) return writePremiumCheckpoint(transaction, uid, premiumPlan);
    // The player's best on each lesson, kept beside the balance so paying for improvement needs
    // no aggregate of its own and no second read.
    const lessonBest = {...(userData.lessonBest || {})};
    // Бак — купленные слоты, не выше потолка из таблицы; темп восстановления — тоже из неё.
    const ownedSlots = nonNegativeInteger(userData.standardHearts, MAX_STANDARD_HEARTS);
    const lifeCeiling = maxLifePoints(Math.min(ownedSlots, economy.standard.maxOwned));
    // Премиум восстанавливается быстрее — если так говорит таблица; ручка стоит рядом с периодом,
    // потому что спека запрещает периоду быть голой константой.
    const hasPremium = premiumPlan.userPatch.hasPremium;
    const storedLifePoints = numberValue(userData.lifePoints, lifeCeiling);
    const storedLifeUpdatedAtMs = numberValue(userData.lifePointsUpdatedAtMs, now);
    let life = {points: premiumPlan.userPatch.lifePoints, updatedAtMs: premiumPlan.userPatch.lifePointsUpdatedAtMs};

    // Плазма — такой же бак, как обычный заряд: слоты в `goldHearts`, накопленное в очках.
    const plasmaRules = economy.plasma;
    const plasmaCeiling = Math.min(nonNegativeInteger(userData.goldHearts, 0), plasmaRules.maxOwned) * POINTS_PER_CHARGE;
    const storedPlasmaPoints = numberValue(userData.plasmaPoints, plasmaCeiling);
    const storedPlasmaUpdatedAtMs = numberValue(userData.plasmaPointsUpdatedAtMs, now);
    let plasma = {points: premiumPlan.userPatch.plasmaPoints, updatedAtMs: premiumPlan.userPatch.plasmaPointsUpdatedAtMs};
    const receiptsByAttempt = new Map();
    // Строки книги собираются внутри транзакции и пишутся ею же: движение и его запись обязаны
    // либо случиться вместе, либо не случиться вовсе.
    ledgerRows.length = 0;
    let standardClaimed = 0;
    let plasmaClaimed = 0;
    const claimedAttemptIds = [];

    const delta = {skillPoints: 0, nolics: 0};
    // The activity ratings the profile radar draws. Counted from the same attempts that pay, so a
    // replayed or unpaid submission cannot inflate them either.
    const ratings = {questions: 0, correct: 0, quizzes: 0};
    settlementEvents.forEach((item, index) => {
      // An attempt is paid for only when it is new, the intake's own payment rule holds for it,
      // and the player still has the life points it costs. The game is offline-first, so a
      // legitimate attempt can arrive with an empty tank — it is stored either way, and the flag
      // records whether it was charged.
      //
      // `payable` is that rule already evaluated (`isPayable`, through `withServerScore`): for a
      // device-scored attempt it is today's `scoreVerified` and the served list agreeing with the
      // digits; for a server-scored one it is "the server scored it, with no gap of its own".
      //
      // Один и тот же `attemptId` дважды в одном пакете — это одна попытка, а не две: снимки
      // читались до первой записи, поэтому обе выглядели бы новыми, и плата, расчёт и награда
      // прошли бы по каждой.
      if (receiptsByAttempt.has(item.event.attemptId)) return;
      const existing = existingSnapshots[index].exists ? existingSnapshots[index].data() || {} : null;
      if (existing) {
        const fault = replayFault(existing, item.event, item.fingerprint);
        if (fault) throw new HttpsError(fault === "OWNER_MISMATCH" ? "permission-denied" : "invalid-argument",
          "Attempt identity cannot be reused for another submission");
        const receipt = existing.settlementReceipt || attemptReceipt({
          event: existing, status: ATTEMPT_STATUS.LEGACY,
          settledAtMs: nonNegativeInteger(existing.receivedAtMs, 0),
        });
        receiptsByAttempt.set(item.event.attemptId, receipt);
        // A response lost after the economic commit must not lose the leaderboard refresh too.
        const group = existing.lifeCharged ? tournamentGroupForAttempt(existing) : null;
        if (group) touchedTournamentIds.add(group.tournamentId);
        return;
      }
      const placement = placements[index]?.exists ? placements[index].data() : null;
      const legacyWithoutTicket = placement?.shelf === "arena" && !item.event.completed && !item.event.aborted;
      if (placement?.shelf === "arena" && !legacyWithoutTicket) {
        throw new HttpsError("failed-precondition", "A new Arena attempt requires its prepared ticket",
          {reason: "ARENA_TICKET_REQUIRED"});
      }
      const isNew = true;
      let lifeCharged = false;
      // Preserve the independent full-lesson guard from the sync branch, in addition to E2's
      // server scoring decision. A self-consistent one-question payload is not a full lesson.
      const servedCountVerified = servedCountMatchesLesson(item.event, allocatedByLesson);
      if (isNew && item.payable && servedCountVerified && !item.event.aborted && !legacyWithoutTicket) {
        const charged = spendLifePoints(life.points, item.event.lifeCost, lifeCeiling);
        if (charged.affordable) {
          life = {points: charged.points, updatedAtMs: life.updatedAtMs};
          lifeCharged = true;
        }
      }

      // Заявки на заряды. Платится тем, что есть, в порядке, в котором вопросы задавались, и только
      // оплаченный пропуск засчитывается верным — неоплаченный оставляет `0`, то есть вопрос
      // считается неотвеченным, чем он и был. Повтор той же попытки не платит второй раз.
      const settled = isNew && lifeCharged ?
        settleClaims(
          item.event.chargeClaims,
          item.event.codeAnswer,
          Math.floor(life.points / POINTS_PER_CHARGE),
          Math.floor(plasma.points / POINTS_PER_CHARGE),
          askedOrder(item.event.answers),
        ) :
        null;
      if (settled) {
        life = {points: life.points - settled.standardChargesPaid * POINTS_PER_CHARGE, updatedAtMs: life.updatedAtMs};
        plasma = {points: plasma.points - settled.plasmaChargesPaid * POINTS_PER_CHARGE, updatedAtMs: plasma.updatedAtMs};
        standardClaimed += countClaims(item.event.chargeClaims).standard;
        plasmaClaimed += countClaims(item.event.chargeClaims).plasma;
        claimedAttemptIds.push(item.event.attemptId);
        // Строка и процент, сохранённые на попытке, — послерасчётные: оплаченный пропуск и есть
        // верный ответ. Присланные клиентом остаются рядом, чтобы расхождение было видно.
        item.event.settledCodeAnswer = settled.codeAnswer;
        item.event.settledPercentScore = recomputePercentScore(settled.codeAnswer);
        item.event.standardChargesPaid = settled.standardChargesPaid;
        item.event.plasmaChargesPaid = settled.plasmaChargesPaid;
        item.event.chargeClaimsUnpaid = settled.unpaid.length;
        // Присланные значения остаются рядом под своими именами, а рабочие — замещаются: иначе
        // оплаченный пропуск поднимал бы только сохранённую строку, а награда, лучший результат,
        // рейтинги активности и турнирная запись считались бы по неподнятой. Платят и ранжируют
        // по тому, за что взяли деньги.
        item.event.submittedCodeAnswer = item.event.codeAnswer;
        item.event.submittedPercentScore = item.event.percentScore;
        item.event.codeAnswer = settled.codeAnswer;
        item.event.percentScore = item.event.settledPercentScore;
      }

      // Что это прохождение сдвинуло в балансах — строкой на каждую валюту. Без книги баланс
      // меняется приращением, и после записи «плюс двенадцать» неоткуда вывести заново: сверка
      // (CAP-11) тогда не может проверить нолики и очки навыка, ей не с чем сравнивать.
      if (isNew) {
        ledgerRows.push(...ledgerEntries({
          uid,
          reason: REASON.ATTEMPT_TOLL,
          sourceId: item.event.attemptId,
          atMs: now,
          deltas: {[CURRENCY.STANDARD_CHARGE_POINTS]: lifeCharged ? -item.event.lifeCost : 0},
          details: {lessonId: item.event.lessonId, activityKind: item.event.activityKind},
        }));
        if (settled) {
          ledgerRows.push(...ledgerEntries({
            uid,
            reason: REASON.CHARGE_CLAIM,
            sourceId: item.event.attemptId,
            atMs: now,
            deltas: {
              [CURRENCY.STANDARD_CHARGE_POINTS]: -settled.standardChargesPaid * POINTS_PER_CHARGE,
              [CURRENCY.PLASMA_CHARGE_POINTS]: -settled.plasmaChargesPaid * POINTS_PER_CHARGE,
            },
            details: {unpaid: settled.unpaid.length},
          }));
        }
      }

      let itemReward = {skillPoints: 0, nolics: 0};
      if (lifeCharged) {
        // Paid on improvement: percent taken for the first time counts double, percent already
        // earned counts a tenth. Replaying a lesson you know is not a way to earn.
        const bestKey = lessonBestKey(item.event.lessonId, item.event.difficulty);
        const previousBestPercent = numberValue(lessonBest[bestKey], 0);
        const isHard = stringValue(item.event.difficulty).toUpperCase() === "HARD";
        const percent = numberValue(item.event.percentScore, 0);
        itemReward = attemptReward({
          previousBestPercent,
          percent,
          isHard,
          allocatedSeconds: numberValue(
            (allocatedByLesson[item.event.lessonId] || {})[isHard ? "hard" : "easy"],
            0,
          ),
        });
        if (percent > previousBestPercent) lessonBest[bestKey] = percent;
        delta.skillPoints += itemReward.skillPoints;
        delta.nolics += itemReward.nolics;
        ledgerRows.push(...ledgerEntries({
          uid,
          reason: REASON.ATTEMPT_REWARD,
          sourceId: item.event.attemptId,
          atMs: now,
          deltas: {
            [CURRENCY.NOLICS]: itemReward.nolics,
            [CURRENCY.SKILL_POINTS]: itemReward.skillPoints,
          },
          details: {lessonId: item.event.lessonId, percent, previousBestPercent},
        }));
        const counts = attemptActivityCounts(item.event.codeAnswer);
        ratings.questions += counts.questions;
        ratings.correct += counts.correct;
        ratings.quizzes += 1;
        const tournamentId = writeTournamentAttemptToTransaction(transaction, item.event, item.content, now);
        if (tournamentId) touchedTournamentIds.add(tournamentId);
      }
      const status = legacyWithoutTicket ? ATTEMPT_STATUS.LEGACY : lifeCharged ? ATTEMPT_STATUS.SETTLED :
        item.event.paymentRule === PAYMENT_RULE.SERVER_SCORED && !item.payable ?
          ATTEMPT_STATUS.UNSCORABLE : ATTEMPT_STATUS.REJECTED;
      const receipt = attemptReceipt({
        event: item.event, status, settledAtMs: now,
        lifePointsSpent: lifeCharged ? item.event.lifeCost + (settled ? settled.standardChargesPaid * POINTS_PER_CHARGE : 0) : 0,
        plasmaPointsSpent: settled ? settled.plasmaChargesPaid * POINTS_PER_CHARGE : 0,
        reward: itemReward,
      });
      receiptsByAttempt.set(item.event.attemptId, receipt);
      transaction.set(item.ref, clean({
        ...storedAttemptFields(item.event),
        ...item.content,
        ...(status === ATTEMPT_STATUS.UNSCORABLE ? {codeAnswer: null, percentScore: null} : {}),
        completed: receipt.completed,
        aborted: item.event.aborted,
        receivedAtMs: now,
        schemaVersion: 1,
        lifeCharged,
        requestFingerprint: item.fingerprint,
        settlementReceipt: receipt,
      }), {merge: true});
    });

    if (delta.skillPoints > 0 || delta.nolics > 0) {
      writeUserProgressDelta(transaction, uid, delta, now);
    }
    if (ratings.quizzes > 0) {
      writeActivityRatingsDelta(transaction, uid, ratings, now);
    }
    for (const entry of ledgerRows) {
      transaction.set(db.doc(`${LEDGER_PATH}/${entry.id}`), entry, {merge: true});
    }
    writePremiumEconomy({db, transaction, uid, plan: premiumPlan});
    transaction.set(
      userRef,
      {
        ...premiumPlan.userPatch,
        uid,
        lifePoints: life.points,
        lifePointsUpdatedAtMs: life.updatedAtMs,
        plasmaPoints: plasma.points,
        plasmaPointsUpdatedAtMs: plasma.updatedAtMs,
        updatedAtMs: now,
        lessonBest,
      },
      {merge: true},
    );
    reward = delta;
    receipts = settlementEvents.map((item) => receiptsByAttempt.get(item.event.attemptId));
    remainingLifePoints = life.points;
    overspendInputs = {
      economy,
      windowStartMs: settlementEvents.reduce(
        (earliest, item) => Math.min(earliest, nonNegativeEventTime(item.event.completedAtMs) || now),
        now,
      ),
      attemptIds: claimedAttemptIds,
      hasPremium,
      standard: {
        claimed: standardClaimed,
        storedPoints: storedLifePoints,
        storedUpdatedAtMs: storedLifeUpdatedAtMs,
        rules: economy.standard,
        ownedSlots,
      },
      plasma: {
        claimed: plasmaClaimed,
        storedPoints: storedPlasmaPoints,
        storedUpdatedAtMs: storedPlasmaUpdatedAtMs,
        rules: economy.plasma,
        ownedSlots: nonNegativeInteger(userData.goldHearts, 0),
      },
    };
  });
  // Перерасход. Излишек и так не оплачен — запись для оператора, а не второе наказание, и
  // медленная синхронизация не подделка: окно длинное, и потолок растёт вместе с ним.
  await writeOverspendRecords(uid, overspendInputs, now);
  for (const tournamentId of touchedTournamentIds) {
    await recalculateTournamentLeaderboard(tournamentId, now);
  }
  return {accepted: attempts.length, reward, lifePoints: remainingLifePoints, receipts};
}

exports.submitQuestRatingEvents = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  await requireProfile(uid);
  return applyQuestRatingEvents(uid, listMaps(request.data && request.data.ratings));
});

/** Приём оценок квеста — то же тело для прямого вызова и для очереди. */
async function applyQuestRatingEvents(uid, ratings) {
  if (ratings.length > MAX_RESULT_EVENTS_PER_CALL) {
    throw new HttpsError("invalid-argument", `At most ${MAX_RESULT_EVENTS_PER_CALL} ratings are accepted per call`);
  }
  if (ratings.length === 0) return {accepted: 0};

  const now = Date.now();
  const receipts = [];
  for (const item of ratings) {
    const event = normalizeQuestRatingEvent(item, uid);
    if (typeof item.attemptId === "string" && item.attemptId && event.scope === PUBLIC_SCOPE) {
      receipts.push(await callArenaService("submitLessonRating", {auth: {uid}, data: {
        mutationId: event.ratingId, lessonId: event.lessonId, attemptId: item.attemptId, rating: event.rating,
      }}));
      continue;
    }
    // Preserve already queued legacy/private feedback as history only. It cannot prove a paid,
    // completed public lesson and must not become a course-wide or season rating during upgrade.
    const bucketId = eventBucketId(event.ratingId);
    const eventRef = db
      .collection(scopedCollection("rating_events", event.scope))
      .doc(bucketId)
      .collection("events")
      .doc(safeDocId(event.ratingId));
    const receipt = {status: "LEGACY_RECORDED", ratingId: event.ratingId, eligible: false};
    await db.runTransaction(async (transaction) => {
      const existing = await transaction.get(eventRef);
      if (existing.exists) {
        if ((existing.data() || {}).userId !== uid) throw new HttpsError("permission-denied", "Rating belongs to another account");
        return;
      }
      transaction.create(eventRef, clean({...event, ...contentKeysForEvent(event), receivedAtMs: now,
        schemaVersion: 1, settlementReceipt: receipt}));
    });
    receipts.push(receipt);
  }
  return {accepted: ratings.length, receipts};
}

exports.aggregateQuestRatingsDaily = onSchedule(
  {...FUNCTION_OPTIONS, schedule: "every day 04:00", timeZone: "UTC"},
  async () => {
    await aggregateDirtyQuestRatings(Date.now(), DAILY_DIRTY_RATING_LIMIT);
  },
);

exports.aggregateQuestRatingsNow = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const profile = await requireProfile(uid);
  if (profile.developerLevel <= DEVELOPER_ALL_ACCESS_LEVEL) {
    throw new HttpsError("permission-denied", "Developer level is required");
  }
  const limit = Math.max(1, Math.min(numberValue(request.data && request.data.limit, 200), DAILY_DIRTY_RATING_LIMIT));
  return aggregateDirtyQuestRatings(Date.now(), limit);
});

/**
 * Таблица настроек экономики зарядов — на устройство.
 *
 * Коллекция `configs/` закрыта правилами: клиент читать её не может и не должен, иначе таблицу
 * можно было бы подменить локально и торговаться с сервером о ценах. Поэтому она приезжает
 * вызовом, который клиент делает в обычной синхронизации.
 *
 * Клиент присылает версию, которая у него уже есть. Совпала — не отдаём ничего, кроме этого факта:
 * таблица меняется редко, а синхронизация идёт часто.
 *
 * Отсутствующий или испорченный документ вырождается в начальные значения (`economy-constants.js`),
 * а не в нулевые потолки: нулевой потолок запер бы каждый аккаунт разом.
 */
/**
 * Таблица настроек, как её читает сервер: отсутствующий или испорченный документ вырождается в
 * начальные значения, а документ без своего номера версии получает время последней записи —
 * иначе две правки подряд были бы одной версией, и вторая до устройств не доехала бы.
 */
async function readEconomyConstantsDocument() {
  const snapshot = await db.doc(ECONOMY_CONSTANTS_DOC).get();
  const writtenAtMs = snapshot.exists && snapshot.updateTime ? snapshot.updateTime.toMillis() : 0;
  return readEconomyConstants(snapshot.exists ? snapshot.data() : null, writtenAtMs);
}

// Long entitlement histories advance in bounded, durable checkpoints. No operation is
// acknowledged until its own transaction completed; retries never lose elapsed premium time.
async function runEconomyTransaction(body) {
  for (let pass = 0; pass < PREMIUM_CATCHUP_PASSES; pass += 1) {
    const result = await db.runTransaction(body);
    if (result !== PREMIUM_CATCHUP) return result;
  }
  throw new HttpsError("unavailable", "Premium history is catching up; retry the same operation");
}

async function advancePremiumInTransaction(transaction, uid, userData, constants, now) {
  const operation = {kind: "ADVANCE"};
  const snapshot = await readPremiumEconomy({db, transaction, uid, operation});
  return planPremiumEconomy({snapshot, userData, constants, now, operation});
}

function writePremiumCheckpoint(transaction, uid, plan) {
  writePremiumEconomy({db, transaction, uid, plan});
  transaction.set(db.doc(`users/${uid}`), plan.userPatch, {merge: true});
  return PREMIUM_CATCHUP;
}

exports.getEconomyConstants = onCall(CALLABLE_OPTIONS, async (request) => {
  requireAuthUid(request);
  const constants = await readEconomyConstantsDocument();
  const known = Math.floor(Number(request.data && request.data.knownVersion));
  if (Number.isFinite(known) && known === constants.version) {
    return {unchanged: true, version: constants.version};
  }
  // Белым списком: размеры паков золота (`goldPacks`) остаются на сервере.
  return {unchanged: false, ...clientEconomyConstants(constants)};
});

exports.ensureUserProfile = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  let result;
  try {
    result = await upsertUserProfile(uid, {
      nickname: generatedNickname(uid),
      knownLanguages: normalizeLanguages(request.data && request.data.knownLanguages),
      now: Date.now(),
      authStatus: authProfileStatus(request),
      allowNicknameUpgrade: false,
      // Регистрация по ссылке (история 5.3): поле читается только при создании аккаунта.
      referredByUid: request.data && request.data.referredByUid,
    });
  } catch (error) {
    if (error && error.code === "INVALID_ARGUMENT") {
      throw new HttpsError("invalid-argument", error.message);
    }
    throw error;
  }
  return profileResponse(result, await readEconomyConstantsDocument());
});

exports.updateUserNickname = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const nickname = sanitizeNickname(stringValue(request.data && request.data.nickname), null);
  if (!nickname) {
    throw new HttpsError("invalid-argument", "Nickname must contain 3..24 supported characters");
  }
  // Wearing a name you own is free; taking a new one may cost gold. This call never charges — it
  // refuses and points at claimNickname, so money only ever moves on a call that says "buy".
  await refuseIfPurchaseRequired(uid, nickname);
  const result = await upsertUserProfile(uid, {
    nickname,
    knownLanguages: normalizeLanguages(request.data && request.data.knownLanguages),
    now: Date.now(),
    authStatus: authProfileStatus(request),
    allowNicknameUpgrade: true,
  });
  return profileResponse(result, await readEconomyConstantsDocument());
});

/**
 * Verification is a human decision, not a computed one: an admin or developer reads what somebody
 * says about themselves, reaches them on telegram, and decides. These three calls carry that
 * conversation — filing, listing, deciding — and nothing about it is derivable from play.
 */
exports.submitVerificationRequest = onCall(CALLABLE_OPTIONS, async (request) => {
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

exports.fetchVerificationRequests = onCall(CALLABLE_OPTIONS, async (request) => {
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

exports.decideVerification = onCall(CALLABLE_OPTIONS, async (request) => {
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
        trophies: firestoreTypes.FieldValue.arrayUnion(TROPHY_VERIFIED),
        verifiedAtMs: now,
        verifiedByUid: reviewerUid,
        status: "VALIDATED",
        updatedAtMs: now,
      }, {merge: true});
      transaction.set(profileRef, {uid: targetUid, status: "VALIDATED"}, {merge: true});
    }
  });
  // После коммита, не внутри: транзакция может переиграться, а уведомление — нет. Сбой доставки
  // операцию не роняет — решение уже записано (AD-20).
  if (decision === DECISION_APPROVED) {
    await notifyUser(PUSH_EVENTS.VERIFICATION_APPROVED, targetUid, targetUid);
  }
  return {uid: targetUid, decision, decidedAtMs: now};
});

/**
 * Push по критическому событию — повод, а не данные (AD-20).
 *
 * Зовётся после коммита операции. Никогда не бросает: уведомление — побочный канал, и его
 * сбой не должен превращаться в HttpsError у операции, которая уже состоялась.
 */
async function notifyUser(event, entityId, targetUid) {
  try {
    await sendPushToUser({db, messaging: admin.messaging()}, event, entityId, targetUid, logger);
  } catch (error) {
    logger.warn("push: unexpected failure", {eventType: event && event.eventType, message: error.message});
  }
}

/**
 * Снимает токен устройства с чужого аккаунта — единственная точка, откуда это делает клиент,
 * получивший сообщение с чужим `targetUid` после смены аккаунта (Story 10.5).
 */
exports.detachDeviceToken = onCall(CALLABLE_OPTIONS, async (request) => {
  const callerUid = requireAuthUid(request);
  try {
    return await detachDeviceToken(db, {
      callerUid,
      token: request.data && request.data.token,
      targetUid: request.data && request.data.targetUid,
    });
  } catch (error) {
    if (error instanceof PushError) throw new HttpsError(error.code, error.message);
    throw error;
  }
});

/**
 * Tells somebody whether a name is free while they are still typing it.
 *
 * Deliberately says nothing about who holds a taken name. Returning the owner would turn this into
 * a directory: run a dictionary through it and you have a map of every player. Sign-in is required
 * for the same reason — an open endpoint would be that directory for anybody at all.
 *
 * The answer is a hint, never a reservation. Somebody else can claim the name between this call and
 * the save, so the save still decides.
 */
exports.checkNicknameAvailability = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const policy = await readNicknamePolicy();
  const described = describeNickname(stringValue(request.data && request.data.nickname), policy);
  if (!described.ok) {
    return {nickname: "", available: false, reason: described.reason, price: 0, tier: null, holder: null};
  }

  const claim = await db
    .collection(NICKNAME_CLAIMS_COLLECTION)
    .doc(nicknameClaimId(described.canonical))
    .get();
  // What this name would actually cost, answered here rather than guessed on the phone: the ladder
  // lives on the server, and a client that derives it shows the wrong number the moment it moves.
  const price = nicknameAskingPrice(described.nickname, await hasChosenNickname(uid));
  const tier = nicknameTier(described.nickname);
  if (!claim.exists) {
    return {nickname: described.nickname, available: true, reason: null, price, tier, holder: null};
  }
  // Your own name must not read as taken, or changing anything else on the form would look blocked.
  const claimData = claim.data() || {};
  const holderUid = stringValue(claimData.uid);
  const isOwn = holderUid === uid;
  // Who holds it, by the name they wear — a taken name stays on screen with its owner's tag rather
  // than vanishing, so the market reads as inhabited instead of empty.
  let holder = null;
  if (!isOwn && holderUid) {
    const holderSnapshot = await db.collection("users").doc(holderUid).get();
    holder = nullableString((holderSnapshot.data() || {}).nickname);
  }
  return {
    nickname: described.nickname,
    available: isOwn,
    reason: isOwn ? "yours" : "taken",
    price: 0,
    tier,
    holder,
  };
});

/**
 * Names are held, not rented. An account can own several and exactly one of them is active; the
 * registry document is the title deed, and `users/{uid}.nickname` merely points at whichever is
 * being worn. The first self-chosen name is free, every further one costs gold, and names can
 * change hands through the shop.
 */

exports.fetchOwnedNicknames = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const [claims, userSnapshot, listings] = await Promise.all([
    db.collection(NICKNAME_CLAIMS_COLLECTION).where("uid", "==", uid).get(),
    db.collection("users").doc(uid).get(),
    db.collection(NICKNAME_LISTINGS_COLLECTION).where("sellerUid", "==", uid).get(),
  ]);
  const activeCanonical = canonicalNickname((userSnapshot.data() || {}).nickname);
  const listedByCanonical = new Map(
    listings.docs.map((doc) => [stringValue((doc.data() || {}).canonical), doc.data() || {}]),
  );
  return {
    nicknames: claims.docs.map((doc) => {
      const data = doc.data() || {};
      const canonical = stringValue(data.canonical);
      const listing = listedByCanonical.get(canonical);
      return {
        nickname: stringValue(data.nickname),
        active: canonical === activeCanonical,
        generated: Boolean(data.generated),
        listedPrice: listing ? numberValue(listing.price, 0) : null,
      };
    }),
  };
});

exports.claimNickname = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const policy = await readNicknamePolicy();
  const described = describeNickname(stringValue(request.data && request.data.nickname), policy);
  if (!described.ok) {
    throw new HttpsError("invalid-argument", `Nickname rejected: ${described.reason}`);
  }

  const claimRef = db.collection(NICKNAME_CLAIMS_COLLECTION).doc(nicknameClaimId(described.canonical));
  const userRef = db.collection("users").doc(uid);
  const profileRef = db.collection("profiles").doc(uid);
  const now = Date.now();
  let charged = 0;

  await db.runTransaction(async (transaction) => {
    const claim = await transaction.get(claimRef);
    const userSnapshot = await transaction.get(userRef);
    if (claim.exists && stringValue((claim.data() || {}).uid) !== uid) {
      throw new HttpsError("already-exists", "Nickname is already taken");
    }
    const user = userSnapshot.exists ? userSnapshot.data() || {} : {};

    // Short names always cost; a first long one is free. The name handed out at registration is
    // free either way — that one is given, not chosen — and does not spend the allowance.
    const price = nicknameAskingPrice(described.nickname, await hasChosenNickname(uid));
    const gold = numberValue(user.gold, 0);
    if (gold < price) throw new HttpsError("failed-precondition", "Not enough gold");
    charged = price;

    transaction.set(claimRef, {
      uid,
      nickname: described.nickname,
      canonical: described.canonical,
      generated: false,
      createdAtMs: numberValue((claim.data() || {}).createdAtMs, now),
      updatedAtMs: now,
    }, {merge: true});
    transaction.set(userRef, {
      uid, nickname: described.nickname, gold: gold - price, updatedAtMs: now,
    }, {merge: true});
    transaction.set(profileRef, {uid, nickname: described.nickname}, {merge: true});
  });
  return {nickname: described.nickname, charged};
});

/**
 * The eight logos, and what each costs.
 *
 * Priced in three steps rather than eight, because the difference a player cares about is how rare
 * a logo looks next to the others, not a unique number per glyph. Boxes hand these out for nothing;
 * this is the way to get one you were not given.
 */
const LOGO_PRICES = {
  "Golden Crown Logo": 1500,
  "Diamond Star Logo": 1500,
  "Phoenix Wings Logo": 3000,
  "Dragon Scale Logo": 3000,
  "Crystal Orb Logo": 400,
  "Thunder Bolt Logo": 600,
  "Mystic Eye Logo": 600,
  "Royal Shield Logo": 400,
};

exports.fetchLogoCatalog = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const snapshot = await db.collection("users").doc(uid).get();
  const owned = new Set(stringArray((snapshot.data() || {}).ownedLogos));
  return {
    logos: GIFT_BOX_LOGO_NAMES.map((name) => ({
      name,
      price: numberValue(LOGO_PRICES[name], 0),
      owned: owned.has(name),
      imageUrl: logoImageUrl(name),
    })),
  };
});

exports.buyLogo = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const name = stringValue(request.data && request.data.logo);
  const price = LOGO_PRICES[name];
  if (!price) throw new HttpsError("invalid-argument", "Unknown logo");

  const userRef = db.collection("users").doc(uid);
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(userRef);
    const user = snapshot.exists ? snapshot.data() || {} : {};
    // Paying twice for a logo already owned is the one outcome worth a hard refusal: the gold is
    // gone and nothing changes on screen, which reads as the purchase having failed.
    if (stringArray(user.ownedLogos).includes(name)) {
      throw new HttpsError("already-exists", "Logo already owned");
    }
    const gold = numberValue(user.gold, 0);
    if (gold < price) throw new HttpsError("failed-precondition", "Not enough gold");
    transaction.set(
      userRef,
      {
        uid,
        gold: gold - price,
        ownedLogos: firestoreTypes.FieldValue.arrayUnion(name),
        updatedAtMs: Date.now(),
      },
      {merge: true},
    );
  });
  return {logo: name, charged: price};
});

exports.wearLogo = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const name = stringValue(request.data && request.data.logo);
  if (!isLogoName(name)) throw new HttpsError("invalid-argument", "Unknown logo");

  const userRef = db.collection("users").doc(uid);
  const user = (await userRef.get()).data() || {};
  if (!stringArray(user.ownedLogos).includes(name)) {
    throw new HttpsError("permission-denied", "You do not own this logo");
  }
  const avatarUrl = logoImageUrl(name);
  await userRef.set({uid, avatarUrl, updatedAtMs: Date.now()}, {merge: true});
  return {logo: name, avatarUrl};
});

/** The avatar image itself. Small, immutable, cached for a day; 404 for anything else. */
exports.logoImage = onRequest(
  // Картинки логотипов отдаются в браузер и на устройство без токена — вход тут открыт по смыслу,
  // но объявить его всё равно нужно: иначе служба недостижима (см. [CALLABLE_OPTIONS]).
  {region: "us-central1", maxInstances: 1, invoker: "public"},
  async (request, response) => {
    const name = String(request.query.logo || "");
    const bytes = isLogoName(name) ? logoImageBytes(name) : null;
    if (!bytes) {
      response.status(404).send("Unknown logo");
      return;
    }
    response
      .set("Content-Type", "image/png")
      .set("Cache-Control", "public, max-age=86400, immutable")
      .send(bytes);
  },
);

exports.fetchLogoListings = onCall(CALLABLE_OPTIONS, async (request) => {
  requireAuthUid(request);
  const limit = Math.max(1, Math.min(numberValue(request.data && request.data.limit, 50), 100));
  const snapshot = await db.collection(LOGO_LISTINGS_COLLECTION).limit(limit).get();
  return {
    listings: snapshot.docs.map((doc) => {
      const data = doc.data() || {};
      return {
        logo: stringValue(data.logo),
        imageUrl: stringValue(data.imageUrl),
        price: numberValue(data.price, 0),
        sellerNickname: stringValue(data.sellerNickname),
        listedAtMs: numberValue(data.listedAtMs, 0),
      };
    }),
  };
});

exports.listLogoForSale = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const name = stringValue(request.data && request.data.logo);
  if (!isLogoName(name)) throw new HttpsError("invalid-argument", "Unknown logo");
  const price = Math.floor(numberValue(request.data && request.data.price, 0));
  if (price < MIN_LISTING_PRICE) {
    throw new HttpsError("invalid-argument", `price must be at least ${MIN_LISTING_PRICE}`);
  }

  const listingRef = db.collection(LOGO_LISTINGS_COLLECTION).doc(name);
  const userRef = db.collection("users").doc(uid);
  const now = Date.now();
  let sellerNickname = null;

  await db.runTransaction(async (transaction) => {
    const userSnapshot = await transaction.get(userRef);
    const user = userSnapshot.exists ? userSnapshot.data() || {} : {};
    if (!stringArray(user.ownedLogos).includes(name)) {
      throw new HttpsError("permission-denied", "You do not own this logo");
    }
    // Selling the avatar you wear would take the picture off your own drawer the moment it sells.
    if (stringValue(user.avatarUrl) === logoImageUrl(name)) {
      throw new HttpsError("failed-precondition", "Switch to another avatar before selling this one");
    }
    sellerNickname = stringValue(user.nickname);
    transaction.set(listingRef, {
      logo: name,
      imageUrl: logoImageUrl(name),
      price,
      sellerUid: uid,
      sellerNickname,
      listedAtMs: now,
    });
  });
  return {logo: name, price};
});

exports.cancelLogoListing = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  return applyCancelLogoListing(uid, request.data || {});
});

/**
 * Снятие логотипа с продажи — тело, общее для прямого вызова и для очереди.
 *
 * Ключ лота — `logo`, как и у прямого вызова. Лот, которого уже нет, — `not-found`: за время
 * ожидания его купили, и повтор этого не изменит, поэтому отказ окончательный и запись очереди
 * уходит в карантин. Повтор после успешного снятия сюда не доходит: приёмник отдаёт сохранённый
 * `{cancelled: true}` по ключу, и второго `not-found` клиент не видит.
 */
async function applyCancelLogoListing(uid, payload) {
  const name = stringValue(payload && payload.logo);
  if (!isLogoName(name)) throw new HttpsError("invalid-argument", "Unknown logo");
  const listingRef = db.collection(LOGO_LISTINGS_COLLECTION).doc(name);
  await db.runTransaction(async (transaction) => {
    const listing = await transaction.get(listingRef);
    if (!listing.exists) throw new HttpsError("not-found", "Listing not found");
    if (stringValue((listing.data() || {}).sellerUid) !== uid) {
      throw new HttpsError("permission-denied", "Not your listing");
    }
    transaction.delete(listingRef);
  });
  return {cancelled: true};
}

exports.buyListedLogo = onCall(CALLABLE_OPTIONS, async (request) => {
  const buyerUid = requireAuthUid(request);
  const name = stringValue(request.data && request.data.logo);
  if (!isLogoName(name)) throw new HttpsError("invalid-argument", "Unknown logo");

  const policy = await readNicknamePolicy();
  const listingRef = db.collection(LOGO_LISTINGS_COLLECTION).doc(name);
  const buyerRef = db.collection("users").doc(buyerUid);
  const now = Date.now();
  let receipt = null;

  await db.runTransaction(async (transaction) => {
    const listing = await transaction.get(listingRef);
    if (!listing.exists) throw new HttpsError("not-found", "Listing not found");
    const data = listing.data() || {};
    const sellerUid = stringValue(data.sellerUid);
    if (sellerUid === buyerUid) throw new HttpsError("failed-precondition", "This is your own listing");

    const sellerRef = db.collection("users").doc(sellerUid);
    const buyerSnapshot = await transaction.get(buyerRef);
    const sellerSnapshot = await transaction.get(sellerRef);
    // The seller's registry is the source of truth; a listing that outlives the ownership (for
    // example after a rollback) transfers nothing.
    if (!stringArray((sellerSnapshot.data() || {}).ownedLogos).includes(name)) {
      throw new HttpsError("failed-precondition", "Seller no longer owns the logo");
    }

    const split = splitSalePrice(numberValue(data.price, 0), policy.saleCommissionPercent);
    const buyerGold = numberValue((buyerSnapshot.data() || {}).gold, 0);
    if (buyerGold < split.total) throw new HttpsError("failed-precondition", "Not enough gold");
    const sellerGold = numberValue((sellerSnapshot.data() || {}).gold, 0);

    // Picture, payment and the listing all move together: a partial trade would either take the
    // gold without handing over the avatar, or hand it over unpaid.
    transaction.set(sellerRef, {
      gold: sellerGold + split.sellerGets,
      ownedLogos: firestoreTypes.FieldValue.arrayRemove(name),
      updatedAtMs: now,
    }, {merge: true});
    transaction.set(buyerRef, {
      gold: buyerGold - split.total,
      ownedLogos: firestoreTypes.FieldValue.arrayUnion(name),
      updatedAtMs: now,
    }, {merge: true});
    transaction.delete(listingRef);
    receipt = {
      logo: stringValue(data.logo),
      paid: split.total,
      commission: split.commission,
    };
  });
  return receipt;
});

/**
 * Where this player stands among everybody, by experience.
 *
 * Counted rather than listed: the answer is two numbers, and fetching every profile to work them
 * out would grow with the playerbase for no benefit. Firestore's aggregation counts do it in one
 * round trip each, and the count is approximate only in the sense that somebody may pass you
 * between the two — which is true of any ranking that is not frozen.
 */
exports.fetchLeagueStanding = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const snapshot = await db.collection("users").doc(uid).get();
  const mine = numberValue((snapshot.data() || {}).skillPoints, 0);

  const [aheadCount, totalCount] = await Promise.all([
    db.collection("users").where("skillPoints", ">", mine).count().get(),
    db.collection("users").count().get(),
  ]);
  const ahead = aheadCount.data().count;
  const total = Math.max(1, totalCount.data().count);
  const place = ahead + 1;
  // Rounded up, so being 1st of 128 reads as the top 1% rather than the top 0%.
  const topPercent = Math.max(1, Math.min(100, Math.ceil((place / total) * 100)));
  return {place, total, topPercent, skillPoints: mine};
});

exports.setActiveNickname = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const canonical = canonicalNickname(stringValue(request.data && request.data.nickname));
  if (!canonical) throw new HttpsError("invalid-argument", "nickname is required");

  const claimRef = db.collection(NICKNAME_CLAIMS_COLLECTION).doc(nicknameClaimId(canonical));
  const claim = await claimRef.get();
  if (!claim.exists || stringValue((claim.data() || {}).uid) !== uid) {
    throw new HttpsError("permission-denied", "You do not own this nickname");
  }
  const nickname = stringValue((claim.data() || {}).nickname);
  const now = Date.now();
  const batch = db.batch();
  batch.set(db.collection("users").doc(uid), {uid, nickname, updatedAtMs: now}, {merge: true});
  batch.set(db.collection("profiles").doc(uid), {uid, nickname}, {merge: true});
  // Wearing a name takes it off the shelf.
  //
  // Listing already refuses the name you wear, but nothing stopped you listing a spare and then
  // putting it on — leaving it worn and for sale at once. The next buyer would take the name off
  // somebody's face, and the account would be left pointing at a name it no longer owns.
  batch.delete(db.collection(NICKNAME_LISTINGS_COLLECTION).doc(nicknameClaimId(canonical)));
  await batch.commit();
  return {nickname};
});

exports.fetchNicknameListings = onCall(CALLABLE_OPTIONS, async (request) => {
  requireAuthUid(request);
  const limit = Math.max(1, Math.min(numberValue(request.data && request.data.limit, 50), 100));
  const snapshot = await db.collection(NICKNAME_LISTINGS_COLLECTION).limit(limit).get();
  return {
    listings: snapshot.docs.map((doc) => {
      const data = doc.data() || {};
      // The seller is shown by the name they wear, never by uid.
      return {
        nickname: stringValue(data.nickname),
        price: numberValue(data.price, 0),
        sellerNickname: stringValue(data.sellerNickname),
        listedAtMs: numberValue(data.listedAtMs, 0),
      };
    }),
  };
});

/**
 * A name changes hands only between accounts that belong to somebody.
 *
 * The shop is the one place where a name leaves the account that earned it, and an unverified
 * account is not yet a person as far as the app is concerned — letting one trade would make the
 * market a laundry for names. Withdrawing your own lot is deliberately not gated: that returns a
 * name rather than moving it, and an account that loses the tick must still be able to take its
 * own listing down.
 */
function requireVerifiedForNicknameMarket(user, action) {
  if (!hasTrophy((user || {}).trophies, TROPHY_VERIFIED)) {
    throw new HttpsError("permission-denied", `Only verified accounts may ${action} nicknames`);
  }
}

exports.listNicknameForSale = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const canonical = canonicalNickname(stringValue(request.data && request.data.nickname));
  if (!canonical) throw new HttpsError("invalid-argument", "nickname is required");
  const price = Math.floor(numberValue(request.data && request.data.price, 0));
  if (price < MIN_LISTING_PRICE) {
    throw new HttpsError("invalid-argument", `price must be at least ${MIN_LISTING_PRICE}`);
  }

  const claimRef = db.collection(NICKNAME_CLAIMS_COLLECTION).doc(nicknameClaimId(canonical));
  const listingRef = db.collection(NICKNAME_LISTINGS_COLLECTION).doc(nicknameClaimId(canonical));
  const userRef = db.collection("users").doc(uid);
  const now = Date.now();

  await db.runTransaction(async (transaction) => {
    const claim = await transaction.get(claimRef);
    const userSnapshot = await transaction.get(userRef);
    if (!claim.exists || stringValue((claim.data() || {}).uid) !== uid) {
      throw new HttpsError("permission-denied", "You do not own this nickname");
    }
    const user = userSnapshot.exists ? userSnapshot.data() || {} : {};
    requireVerifiedForNicknameMarket(user, "sell");
    // Selling the name you are wearing would leave you nameless the moment somebody bought it.
    if (canonicalNickname(user.nickname) === canonical) {
      throw new HttpsError("failed-precondition", "Switch to another nickname before selling this one");
    }
    transaction.set(listingRef, {
      canonical,
      nickname: stringValue((claim.data() || {}).nickname),
      price,
      sellerUid: uid,
      sellerNickname: stringValue(user.nickname),
      listedAtMs: now,
    });
  });
  return {nickname: stringValue((await claimRef.get()).data().nickname), price};
});

exports.cancelNicknameListing = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  return applyCancelNicknameListing(uid, request.data || {});
});

/**
 * Снятие ника с продажи — тело, общее для прямого вызова и для очереди.
 *
 * Ключ лота — `nickname`, как и у прямого вызова. Семантика отказов и повтора та же, что у
 * {@link applyCancelLogoListing}: купленный лот — окончательный `not-found`, повтор после
 * успешного снятия получает сохранённый ответ от приёмника.
 */
async function applyCancelNicknameListing(uid, payload) {
  const canonical = canonicalNickname(stringValue(payload && payload.nickname));
  if (!canonical) throw new HttpsError("invalid-argument", "nickname is required");
  const listingRef = db.collection(NICKNAME_LISTINGS_COLLECTION).doc(nicknameClaimId(canonical));
  await db.runTransaction(async (transaction) => {
    const listing = await transaction.get(listingRef);
    if (!listing.exists) throw new HttpsError("not-found", "Listing not found");
    if (stringValue((listing.data() || {}).sellerUid) !== uid) {
      throw new HttpsError("permission-denied", "Not your listing");
    }
    transaction.delete(listingRef);
  });
  return {cancelled: true};
}

exports.buyListedNickname = onCall(CALLABLE_OPTIONS, async (request) => {
  const buyerUid = requireAuthUid(request);
  const canonical = canonicalNickname(stringValue(request.data && request.data.nickname));
  if (!canonical) throw new HttpsError("invalid-argument", "nickname is required");

  const policy = await readNicknamePolicy();
  const listingRef = db.collection(NICKNAME_LISTINGS_COLLECTION).doc(nicknameClaimId(canonical));
  const claimRef = db.collection(NICKNAME_CLAIMS_COLLECTION).doc(nicknameClaimId(canonical));
  const buyerRef = db.collection("users").doc(buyerUid);
  const now = Date.now();
  let receipt = null;

  await db.runTransaction(async (transaction) => {
    const listing = await transaction.get(listingRef);
    if (!listing.exists) throw new HttpsError("not-found", "Listing not found");
    const data = listing.data() || {};
    const sellerUid = stringValue(data.sellerUid);
    if (sellerUid === buyerUid) throw new HttpsError("failed-precondition", "This is your own listing");

    const sellerRef = db.collection("users").doc(sellerUid);
    const claim = await transaction.get(claimRef);
    const buyerSnapshot = await transaction.get(buyerRef);
    const sellerSnapshot = await transaction.get(sellerRef);
    requireVerifiedForNicknameMarket(buyerSnapshot.data(), "buy");
    // The listing is a promise about the registry; if they disagree, the registry wins.
    if (!claim.exists || stringValue((claim.data() || {}).uid) !== sellerUid) {
      throw new HttpsError("failed-precondition", "Listing no longer matches the registry");
    }

    const split = splitSalePrice(numberValue(data.price, 0), policy.saleCommissionPercent);
    const buyerGold = numberValue((buyerSnapshot.data() || {}).gold, 0);
    if (buyerGold < split.total) throw new HttpsError("failed-precondition", "Not enough gold");
    const sellerGold = numberValue((sellerSnapshot.data() || {}).gold, 0);

    // Title, payment and the listing all move together: a partial trade would either take the gold
    // without handing over the name, or hand it over unpaid.
    transaction.set(claimRef, {uid: buyerUid, updatedAtMs: now}, {merge: true});
    transaction.set(buyerRef, {gold: buyerGold - split.total, updatedAtMs: now}, {merge: true});
    // The commission is credited to nobody. It leaves circulation, which is the point of it.
    transaction.set(sellerRef, {gold: sellerGold + split.sellerGets, updatedAtMs: now}, {merge: true});
    transaction.delete(listingRef);
    receipt = {
      nickname: stringValue(data.nickname),
      paid: split.total,
      commission: split.commission,
    };
  });
  return receipt;
});

exports.applyShopPurchase = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const itemId = stringValue(request.data && request.data.itemId);
  if (![SHOP_ITEM_STANDARD_HEART_SLOT, SHOP_ITEM_GOLD_HEART, SHOP_ITEM_PREMIUM].includes(itemId)) {
    throw new HttpsError("invalid-argument", `Unsupported shop item ${itemId}`);
  }

  const userRef = db.collection("users").doc(uid);
  const now = Date.now();
  // Цена назначается по таблице, действующей в момент, когда сервер принимает покупку, — а не по
  // той, по которой клиент нарисовал витрину (economy-constants.md).
  const economy = await readEconomyConstantsDocument();
  let response = null;
  try {
    await runEconomyTransaction(async (transaction) => {
      const snapshot = await transaction.get(userRef);
      const storedUser = snapshot.exists ? snapshot.data() || {} : {};
      let premiumPlan;
      let balance;
      if (itemId === SHOP_ITEM_PREMIUM) {
        // Премиум за золото (история 2.2): цену и сам факт «продаётся» называет таблица, золото и
        // срок идут одной транзакцией на общем движке премиума. Запись лота и проводки — внутри.
        const purchased =
          await purchasePremiumWithGold({db, transaction, uid, storedUser, economy, now, readBalance: readEconomyBalance});
        if (!purchased.caughtUp) return writePremiumCheckpoint(transaction, uid, purchased.plan);
        premiumPlan = purchased.plan;
        balance = purchased.balance;
      } else {
        premiumPlan = await advancePremiumInTransaction(transaction, uid, storedUser, economy, now);
        if (!premiumPlan.caughtUp) return writePremiumCheckpoint(transaction, uid, premiumPlan);
        const current = readEconomyBalance({...storedUser, ...premiumPlan.userPatch});
        balance = applyShopPurchaseToBalance(current, itemId, economy);
        // Покупка слота — движение сразу двух валют: платы и того, что за неё дали. Идентификатор
        // выводится из момента и предмета: та же покупка, переигранная транзакцией, — одна строка.
        for (const entry of ledgerEntries({
          uid,
          reason: REASON.SLOT_PURCHASE,
          sourceId: `${itemId}_${now}`,
          atMs: now,
          deltas: {
            [CURRENCY.NOLICS]: balance.nolics - current.nolics,
            [CURRENCY.GOLD]: balance.gold - current.gold,
            [CURRENCY.PLASMA_CHARGE_POINTS]: balance.plasmaPoints - current.plasmaPoints,
          },
          details: {itemId, standardHearts: balance.standardHearts, goldHearts: balance.goldHearts},
        })) {
          transaction.set(db.doc(`${LEDGER_PATH}/${entry.id}`), entry, {merge: true});
        }
      }
      writePremiumEconomy({db, transaction, uid, plan: premiumPlan});
      transaction.set(
        userRef,
        clean({
          ...premiumPlan.userPatch,
          uid,
          updatedAtMs: now,
          hasPremium: balance.hasPremium,
          streakDays: balance.streakDays,
          stars: balance.stars,
          pointsNolics: balance.nolics,
          nolics: balance.nolics,
          standardHearts: balance.standardHearts,
          goldHearts: balance.goldHearts,
          plasmaPoints: balance.plasmaPoints,
          gold: balance.gold,
        }),
        {merge: true},
      );
      response = {itemId, balance};
    });
  } catch (error) {
    // Отказы покупки идут именованными кодами: премиум за золото называет FAILED_PRECONDITION
    // (кошелёк короток) и INVALID_ARGUMENT (таблица не продаёт) из premium-shop.js, слот —
    // HttpsError напрямую. Конфликт повторного выдачи (CONFLICTING_REPLAY, SOURCE_ALREADY_GRANTED
    // из premium-grants.js) — тоже отказ по состоянию, не авария. Клиент ветвится по коду
    // провода, поэтому подделка обязана доехать именованной, а не безымянной INTERNAL; немые
    // ошибки уходят как были.
    const codes = {
      INVALID_ARGUMENT: "invalid-argument",
      FAILED_PRECONDITION: "failed-precondition",
      CONFLICTING_REPLAY: "failed-precondition",
      SOURCE_ALREADY_GRANTED: "failed-precondition",
      INTERNAL: "internal",
    };
    if (codes[error.code]) throw new HttpsError(codes[error.code], error.message);
    throw error;
  }
  return response;
});

/**
 * Ставка на аукционе предложений (история 4.1).
 *
 * Золото и запись ставки идут одной транзакцией внутри offer-auction.js; повышение — возврат
 * прежней суммы и списание новой той же транзакцией. Отказы приходят именованными кодами
 * (FAILED_PRECONDITION — короткий кошелёк или закрытый аукцион, INVALID_ARGUMENT — испорченная
 * ставка), и до клиента они доезжают именованными, а не безымянной INTERNAL: подделка обязана
 * получить отказ провода, по которому клиент ветвится.
 *
 * Открытый стол ставок — соседний read-only вызов: кто, сколько и когда; creative объявлений на
 * столе нет — непроверенная картинка не публичная поверхность (показ после проверки — 4.3).
 */
exports.placeOfferBid = onCall(MONETARY_CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const payload = request.data || {};
  try {
    return await placeOfferBid({
      db,
      uid,
      amount: payload.amount,
      imageUrl: payload.imageUrl,
      link: payload.link,
      now: Date.now(),
    });
  } catch (error) {
    const codes = {INVALID_ARGUMENT: "invalid-argument", FAILED_PRECONDITION: "failed-precondition", INTERNAL: "internal"};
    if (codes[error.code]) throw new HttpsError(codes[error.code], error.message);
    throw error;
  }
});

exports.fetchOfferAuction = onCall(CALLABLE_OPTIONS, async (request) => {
  requireAuthUid(request);
  try {
    return await offerAuctionView({db, now: Date.now()});
  } catch (error) {
    if (error.code === "INVALID_ARGUMENT") throw new HttpsError("invalid-argument", error.message);
    throw error;
  }
});

/**
 * Переворот месяца аукциона предложений (история 4.2).
 *
 * Только серверное расписание, никаких callable-ручек: работа сама находит созревшие аукционы
 * и недовозвращённые ставки, ключи повтора — статусы документов (offer-auction-settlement.js),
 * поэтому повторный прогон ничего не меняет. maxInstances: 1 — два параллельных прогона не
 * нужны ни одному из них.
 */
exports.rotateOfferAuctions = onSchedule({...FUNCTION_OPTIONS,
  schedule: "every 10 minutes", timeZone: "UTC", timeoutSeconds: FUNCTION_TIMEOUT_MS / 1000,
  memory: "512MiB", maxInstances: 1, retryCount: 0,
}, async () => {
  const result = await settleDueOfferAuctions({db, now: Date.now()});
  logger.info("Offer auction rollover completed", result);
});

/**
 * Закрытие сезонов экономики (история 5.1).
 *
 * Только серверное расписание, никаких callable-ручек: работа сама находит созревший квартал,
 * закрывает его одним снимком (топ-100 и дайджест) и порциями снимает накопленные счётчики
 * открытий приглашённых; ключи повтора — существование документа сезона и курсор обхода
 * (economy-season.js), поэтому повторный прогон ничего не меняет. maxInstances: 1 — два
 * параллельных прогона не нужны ни одному из них.
 */
exports.reconcileEconomySeasons = onSchedule({...FUNCTION_OPTIONS,
  schedule: "every 10 minutes", timeZone: "UTC", timeoutSeconds: FUNCTION_TIMEOUT_MS / 1000,
  memory: "512MiB", maxInstances: 1, retryCount: 0,
}, async () => {
  const result = await sweepEconomySeasons({db, now: Date.now(), constants: await readEconomyConstantsDocument()});
  logger.info("Economy season sweep completed", result);
});

/**
 * Решение по объявлению — проверка перед показом (история 4.3).
 *
 * Серверная половина операторской поверхности: очередь проверки в консоли — defer владельца.
 * Гейт — уровень модератора или разработчика (образец isModerator): решение о том, что увидят
 * дети, не принимает ни аккаунт без квалификации, ни сама витрина. Отказы приходят именованными
 * кодами (NOT_FOUND — нет ставки, FAILED_PRECONDITION — решение уже принято, INVALID_ARGUMENT —
 * испорченный запрос).
 */
exports.decideOfferAdReview = onCall(MONETARY_CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const profile = await requireProfile(uid);
  if (profile.moderatorLevel < QUALIFIED_LEVEL && profile.developerLevel <= DEVELOPER_ALL_ACCESS_LEVEL) {
    throw new HttpsError("permission-denied", "Moderator level is required");
  }
  const payload = request.data || {};
  try {
    return await decideOfferAdReview({
      db,
      bidId: payload.bidId,
      decision: payload.decision,
      now: Date.now(),
    });
  } catch (error) {
    const codes = {
      INVALID_ARGUMENT: "invalid-argument",
      FAILED_PRECONDITION: "failed-precondition",
      NOT_FOUND: "not-found",
    };
    if (codes[error.code]) throw new HttpsError(codes[error.code], error.message);
    throw error;
  }
});

/**
 * Экран предложений (история 4.3): только проверенное объявление, кандидатов здесь нет.
 *
 * Читающая половина показа: непроверенный creative не покидает сервер ни при каком состоянии
 * документов — offer-ad-review.js отвечает пустотой на непроверенное и испорченное.
 */
exports.fetchOffersScreen = onCall(CALLABLE_OPTIONS, async (request) => {
  requireAuthUid(request);
  return offersScreenView({db});
});

/**
 * Verifies a Play receipt and credits the gold pack it paid for — exactly once.
 *
 * The client's purchase is a claim; Play's answer is the fact. So the order is fixed: ask Play,
 * credit, confirm, and only then does the client consume. Anything credited on the device would be
 * forgeable; anything consumed before crediting would lose the player's money, because Play never
 * returns a consumed token.
 *
 * This is the wiring and nothing else: who is calling, what they depend on, how an outcome becomes
 * an answer. The decision is pure (purchase-verification.js) and the money path — the Play call
 * and the one transaction that writes gold, settlement, receipt and audit together — runs without
 * firebase-admin (purchase-settlement.js), so both are tested without it. A refusal carries a
 * machine-readable `reasonCode` in its details; the client branches on the code, people and
 * analytics on the reason.
 */
exports.verifyPurchase = onCall(MONETARY_CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const outcome = await settlePurchase({
    db,
    playApi: playDeveloperApi(),
    now: Date.now(),
    uid,
    payload: request.data || {},
    readGold: (data) => readEconomyBalance(data).gold,
    log: logger,
  });
  if (outcome.kind === SETTLEMENT_OUTCOME_REFUSE) {
    throw new HttpsError(outcome.code, outcome.reason, {reasonCode: outcome.reasonCode});
  }
  if (outcome.kind === SETTLEMENT_OUTCOME_UNAVAILABLE) {
    throw new HttpsError("unavailable", outcome.reason);
  }
  return outcome.response;
});

/**
 * Takes back the gold a refunded purchase paid for — exactly once, and never below zero.
 *
 * Google refunds a purchase without asking the app, and refunds by itself anything the player left
 * unclaimed for three days. Until this handler existed, every one of those left the gold on the
 * account, which made a refund the cheapest way to be paid in gold for nothing.
 *
 * A notification is a reason to ask, not a fact: the money moves only when the Play Developer API
 * confirms the purchase is no longer in the purchased state, or no longer knows the token at all.
 * The amount is what the settlement record says was granted, never what the message claims.
 *
 * Wiring only. The reading of the message and the decision are pure (purchase-notifications.js),
 * and the one transaction that writes balance, settlement, receipt, audit and ledger row together
 * runs without firebase-admin (purchase-clawback.js). `retry: true` because a message this handler
 * throws on is one it wrote nothing for: an unreachable Play or an uncommitted transaction must
 * come back, while anything unreadable is recorded and acknowledged so it cannot pile up.
 */
exports.playPurchaseNotifications = onMessagePublished(
  {...MONETARY_FUNCTION_OPTIONS, topic: PLAY_RTDN_TOPIC, retry: true},
  async (event) => {
    const message = event.data && event.data.message;
    // Продление (история 2.3) идёт первым: его токен — повод запустить тот же расчёт, что и чек
    // с устройства, а снять нечего. Семейства уведомлений не пересекаются, поэтому всё прочее —
    // по-прежнему возвраты.
    const renewalToken = readSubscriptionRenewal(decodeNotification(message));
    if (renewalToken) {
      const outcome = await settleSubscriptionRenewal({
        db,
        playApi: playDeveloperApi(),
        now: Date.now(),
        purchaseToken: renewalToken,
        readGold: (data) => readEconomyBalance(data).gold,
        log: logger,
      });
      if (outcome.kind === SETTLEMENT_OUTCOME_UNAVAILABLE) {
        // Вердикта нет — ничего не записано; повторная доставка попробует снова.
        throw new Error("Play Developer API unavailable; redeliver this renewal notification");
      }
      if (outcome.kind === SETTLEMENT_OUTCOME_REFUSE) {
        logger.warn("playPurchaseNotifications: renewal refused", {reason: outcome.reason, reasonCode: outcome.reasonCode});
      }
      return;
    }
    await clawBackPurchase({
      db,
      playApi: playDeveloperApi(),
      now: Date.now(),
      message,
      readGold: (data) => readEconomyBalance(data).gold,
      log: logger,
    });
  },
);

/**
 * The Play client is built on first use, not at load: every function in this file shares the
 * module, and most of them never talk to Play.
 */
let playDeveloperApiClient = null;
function playDeveloperApi() {
  if (!playDeveloperApiClient) playDeveloperApiClient = createPlayDeveloperApi();
  return playDeveloperApiClient;
}

/**
 * Buys a lesson open, or buys its hard mode.
 *
 * Server-side because the balance lives in users/{uid} and the client's copy is overwritten
 * wholesale by the next profile sync — a local deduction would simply evaporate.
 *
 * Buying grants access and nothing else: no stars are written, so the lesson stays unpassed and
 * the sequential chain stops where it was. Repeating a purchase is a no-op rather than a charge.
 */
/**
 * The stored question payload as an object, with the fields the timer needs filled in.
 *
 * A question whose payload will not parse is worth nothing rather than breaking the purchase: an
 * unreadable question is one the runner would have filtered out anyway.
 */
/**
 * One question as the allocated-time sum needs it: its document id, whether it is archived, and the
 * parsed payload. The id carries the `__lang` suffix that marks a translated variant, and the
 * payload does not — so the row, not the content, is what can be deduplicated.
 */
function questionRowFor(doc) {
  return questionRowFrom(questionDocumentFor(doc));
}

/** The same row from a document already read, so one query can serve the reward and the scorer. */
function questionRowFrom(document) {
  return {
    id: document.id,
    archived: document.archived === true,
    content: parseQuestionPayload(document),
  };
}

function parseQuestionPayload(data) {
  const fallback = {text: stringValue(data && data.text), difficulty: stringValue(data && data.difficulty)};
  try {
    const parsed = JSON.parse(stringValue(data && data.payload) || "{}");
    return {...fallback, ...parsed};
  } catch (error) {
    return fallback;
  }
}

exports.unlockLesson = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  return applyLessonUnlock(uid, request.data);
});

/**
 * Разблокировка урока — тело, общее для прямого вызова и для очереди.
 *
 * Одно и то же действие приезжает двумя дорогами: синхронно, пока игрок смотрит на экран, и
 * отложенно из очереди, если сети не было. Логика обязана быть одна, иначе цена, посчитанная
 * этими дорогами, однажды разойдётся.
 */
async function applyLessonUnlock(uid, payload) {
  const lessonId = stringValue(payload && payload.lessonId);
  const kind = stringValue(payload && payload.kind) || UNLOCK_LESSON;
  if (!lessonId) {
    throw new HttpsError("invalid-argument", "lessonId must not be blank");
  }
  // Priced from the lesson's own allocated time, read from its questions rather than taken from
  // the client — a caller that could name its own price could name zero.
  const questionDocs = await db.collection("questions").where("lessonId", "==", lessonId).get();
  const contents = questionDocs.docs.map((doc) => questionRowFor(doc));
  const price = unlockPrice(kind, {
    easyAllocatedSeconds: lessonAllocatedSeconds(contents, false),
    hardAllocatedSeconds: lessonAllocatedSeconds(contents, true),
  });
  if (price === null) {
    throw new HttpsError("invalid-argument", `Unsupported unlock kind ${kind}`);
  }

  const userRef = db.collection("users").doc(uid);
  const key = unlockDocId(kind, lessonId);
  const now = Date.now();
  let response = null;

  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(userRef);
    const data = snapshot.exists ? snapshot.data() || {} : {};
    const balance = readEconomyBalance(data);
    // Unlocks live as a field on the user document rather than a subcollection, so they ride the
    // profile sync the client already runs and a bought lesson stays bought offline.
    const owned = balance.lessonUnlocks;

    // Already bought. Charging twice for the same door is the one thing a retry must not do.
    if (owned.includes(key)) {
      response = {lessonId, kind, charged: 0, balance};
      return;
    }

    const spent = spendNolics(balance.nolics, price);
    if (!spent.affordable) {
      throw new HttpsError("failed-precondition", "Not enough nolics");
    }

    transaction.set(
      userRef,
      clean({
        uid,
        updatedAtMs: now,
        // Both fields: FirebaseUserStatsDataSource reads pointsNolics and the economy data source
        // reads nolics, so writing one leaves half the app showing a balance never charged.
        pointsNolics: spent.nolics,
        nolics: spent.nolics,
        lessonUnlocks: firestoreTypes.FieldValue.arrayUnion(key),
      }),
      {merge: true},
    );
    response = {
      lessonId,
      kind,
      charged: price,
      balance: {...balance, nolics: spent.nolics, lessonUnlocks: withUnlock(owned, key)},
    };
  });

  return response;
}

exports.openGiftBox = onCall(CALLABLE_OPTIONS, async (request) => {
  return (await callBoxOperation(request, BOX_KIND.OPEN)).response;
});

// Заявки подарков читают стандартный конверт мутаций `{replayed, result}` (спек 7: «gift-claim
// envelopes {replayed, result}», ретрай тем же operationId обязан вернуть сохранённый исход как
// исход, а не отказ). Плоское тело ответа здесь — форма планировщика, а не провода.
exports.submitGiftBoxOperation = onCall(CALLABLE_OPTIONS, async (request) => {
  const plan = await callBoxOperation(request);
  return {replayed: plan.replayed, result: plan.response};
});

async function callBoxOperation(request, forcedKind = null) {
  const uid = requireAuthUid(request);
  const payload = {...request.data, ...(forcedKind ? {kind: forcedKind} : {})};
  try {
    return await applyBoxOperation({db, uid, payload, now: Date.now()});
  } catch (error) {
    const codes = {INVALID_ARGUMENT: "invalid-argument", PERMISSION_DENIED: "permission-denied",
      CONFLICTING_REPLAY: "already-exists", UNAVAILABLE: "unavailable"};
    if (codes[error.code]) throw new HttpsError(codes[error.code], error.message);
    throw error;
  }
}

exports.setPublicQuestShelf = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  return applySetPublicQuestShelf(uid, request.data || {});
});

/** Кэш публичных ключей подписи SSV. Ротация объявляет себя незнакомым key_id, кэш живёт сутки. */
const AD_REWARD_KEYS_CACHE_MS = 24 * 60 * 60 * 1000;
let adRewardVerifierKeysCache = null;

/** Документ ключей по адресу; отказ сети, статуса или разбора — исключение (исход UNAVAILABLE). */
function fetchVerifierKeysDocument(url) {
  return new Promise((resolve, reject) => {
    const request = https.get(url, (response) => {
      if (response.statusCode !== 200) {
        response.resume();
        reject(new Error(`verifier keys HTTP ${response.statusCode}`));
        return;
      }
      const chunks = [];
      response.on("data", (chunk) => chunks.push(chunk));
      response.on("end", () => {
        try {
          resolve(JSON.parse(Buffer.concat(chunks).toString("utf8")));
        } catch (error) {
          reject(new Error(`verifier keys unreadable: ${error.message}`));
        }
      });
    });
    request.on("error", reject);
    request.setTimeout(5000, () => request.destroy(new Error("verifier keys timed out")));
  });
}

/** Ключи для транзакции подтверждения: кэш, а по требованию — свежий забор без кэша. */
async function adRewardVerifierKeys(force) {
  if (!force && adRewardVerifierKeysCache && Date.now() - adRewardVerifierKeysCache.fetchedAtMs < AD_REWARD_KEYS_CACHE_MS) {
    return adRewardVerifierKeysCache.document;
  }
  const document = await fetchVerifierKeysDocument(ADMOB_VERIFIER_KEYS_URL);
  adRewardVerifierKeysCache = {fetchedAtMs: Date.now(), document};
  return document;
}

/**
 * SSV-подтверждение награды за рекламу (история 3.1).
 *
 * Вызывает Google, поэтому вход открыт по смыслу ([CALLABLE_OPTIONS]) — но решает подпись, а не
 * происхождение: без честной подписи сеть не подтверждает ни строки. Всё содержательное — чистая
 * верификация (ad-reward-verification.js) и одна транзакция подтверждения с внедрённым Firestore
 * (ad-reward-progress.js); здесь только запрос, ключи с кэшем и ответ. 200 на всё решённое —
 * включая подделки и повторы: Google повторяет не-200, а повторы мусора не нужны. 500 — единственный
 * исход без вердикта (ключи недоступны): ничего не записано, повтор придёт свежим списком ключей.
 *
 * URL этого endpoint'а регистрируется в консоли AdMob при настройке SSV — действие владельца,
 * пока отложенное закрытым аккаунтом сети.
 */
exports.adMobRewardConfirmed = onRequest(
  {region: "us-central1", maxInstances: 2, invoker: "public"},
  async (request, response) => {
    const {outcome} = await applyAdReward({
      db,
      rawQuery: adRewardQueryFromUrl(request.url),
      fetchKeys: adRewardVerifierKeys,
      now: Date.now(),
      log: logger,
    });
    response.status(outcome === AD_REWARD_OUTCOME_ACKNOWLEDGED ? 200 : 500).send("OK");
  },
);

/**
 * Перенос публичного квеста на полку — тело, общее для прямого вызова и для очереди.
 *
 * Тело: `questId`, `targetShelf`, необязательный `reason`. Уровень разработчика проверяется
 * первым, и отказ по нему окончательный (`permission-denied`): аккаунт без уровня не получит его
 * от повтора, и запись очереди уходит в карантин. Локальной полки это не касается — `visibleOn`
 * серверно-защищён (AD-25) и приезжает на клиент журналом изменений после применения.
 */
async function applySetPublicQuestShelf(uid, payload) {
  const data = payload || {};
  const profile = await requireProfile(uid);
  if (profile.developerLevel <= DEVELOPER_ALL_ACCESS_LEVEL) {
    throw new HttpsError("permission-denied", "Developer level is required");
  }

  const questId = stringValue(data.questId);
  if (!questId) throw new HttpsError("invalid-argument", "questId is required");
  const targetShelf = publicQuestShelfValue(data.targetShelf);
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
    const lastModifiedAt = firestoreTypes.Timestamp.fromMillis(now);
    const previousVisibleOn = Array.isArray(quest.visibleOn)
      ? quest.visibleOn.filter((shelf) => typeof shelf === "string")
      : [];
    const assignedVersion = setVersioned(
      transaction,
      questRef,
      {
        visibleOn: [targetShelf],
        // The flag follows the placement: a course returned to the archive reads as archived
        // again, and a quest pulled onto a live shelf stops being filtered out as archived.
        archived: targetShelf === "archive",
        lastModifiedAt,
      },
      quest.version,
      {merge: true},
    );
    const ownerUid = nullableString(quest.authorUid || quest.ownerUid);
    writeQuestVersionMirror(transaction, {ownerUid, catalogId, questId, assignedVersion, now});
    writeSyncChanges(transaction, db, {kind: "quest", id: questId, catalogId, ownerUid}, now);
    writeShelfMoveAudit(transaction, {
      questId,
      catalogId,
      actorUid: uid,
      action: "move",
      previousVisibleOn,
      visibleOn: [targetShelf],
      reason: nullableString(data.reason),
      now,
    });
    return {
      questId,
      catalogId,
      targetShelf,
      version: assignedVersion,
      changedAtMs: now,
    };
  });
}

/**
 * Takes a published quest off every shelf. The client half of removal already works — an empty
 * visibleOn makes the sync delete the local row — so the server half is all that was missing:
 * until this callable, the only takedown was a laptop script hardcoded to the courses catalog.
 */
exports.retirePublicQuest = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const profile = await requireProfile(uid);
  if (profile.developerLevel <= DEVELOPER_ALL_ACCESS_LEVEL) {
    throw new HttpsError("permission-denied", "Developer level is required");
  }

  const questId = stringValue(request.data && request.data.questId);
  if (!questId) throw new HttpsError("invalid-argument", "questId is required");
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
    const previousVisibleOn = Array.isArray(quest.visibleOn)
      ? quest.visibleOn.filter((shelf) => typeof shelf === "string")
      : [];
    const assignedVersion = setVersioned(
      transaction,
      questRef,
      {
        visibleOn: [],
        lastModifiedAt: firestoreTypes.Timestamp.fromMillis(now),
      },
      quest.version,
      {merge: true},
    );
    // Снятие с полок — тоже изменение узла, и в журнал оно попадает наравне с любым другим:
    // пустой `visibleOn` и есть удаление для клиента (AD-26), и без записи он о нём не узнает.
    const ownerUid = nullableString(quest.authorUid || quest.ownerUid);
    writeQuestVersionMirror(transaction, {ownerUid, catalogId, questId, assignedVersion, now});
    writeSyncChanges(transaction, db, {kind: "quest", id: questId, catalogId, ownerUid}, now);
    writeShelfMoveAudit(transaction, {
      questId,
      catalogId,
      actorUid: uid,
      action: "retire",
      previousVisibleOn,
      visibleOn: [],
      reason: nullableString(request.data && request.data.reason),
      now,
    });
    return {questId, catalogId, version: assignedVersion, changedAtMs: now};
  });
});

// ─── Reports, verdicts and bans ────────────────────────────────────────────────────────────────

const REPORT_TARGET_TYPES = new Set(["COMMENT", "QUEST", "USER", "MESSAGE"]);
// Upholding a report pays the reporter; a rejected one costs them the same. Symmetric on purpose:
// an asymmetry either invites spam or makes reporting too expensive to bother with.
const REPORT_UPHELD_POINTS = 3;
const REPORT_REJECTED_POINTS = -3;
// How far above a moderator someone must sit to ban them. The same distance the review consensus
// uses to decide whose opinion is the senior one.
const MODERATOR_BAN_LEVEL_GAP = 100;
const BAN_TERM_WEEK_MS = 7 * DAY_MS;
const BAN_TERM_MONTH_MS = 30 * DAY_MS;

/**
 * How long a ban lasts, decided by the rank of whoever issues it rather than chosen per case.
 * A junior moderator can only ban briefly; permanence is reserved for the most senior.
 * Returns null for a ban that does not expire.
 */
function banDurationMsFor(profile) {
  if (profile.developerLevel > DEVELOPER_ALL_ACCESS_LEVEL) return null;
  if (profile.moderatorLevel > 300) return null;
  if (profile.moderatorLevel >= 200) return BAN_TERM_MONTH_MS;
  return BAN_TERM_WEEK_MS;
}

/** Anyone signed in can report anything. The verdict is where judgement happens, not the filing. */
exports.submitReport = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  await requireProfile(uid);
  const targetType = stringValue(request.data && request.data.targetType).toUpperCase();
  if (!REPORT_TARGET_TYPES.has(targetType)) {
    throw new HttpsError("invalid-argument", `Unsupported targetType ${targetType || "<empty>"}`);
  }
  const targetId = stringValue(request.data && request.data.targetId);
  if (!targetId) throw new HttpsError("invalid-argument", "targetId is required");
  const reason = stringValue(request.data && request.data.reason).trim();
  if (!reason) throw new HttpsError("invalid-argument", "reason is required");
  if (targetType === "USER" && targetId === uid) {
    throw new HttpsError("invalid-argument", "Cannot report yourself");
  }

  const now = Date.now();
  const reportId = `${targetType.toLowerCase()}_${targetId}_${uid}`;
  const reportRef = db.collection("reports").doc(reportId);
  return db.runTransaction(async (transaction) => {
    const existing = await transaction.get(reportRef);
    // One open report per person per thing: re-filing is not a second voice, and a decided report
    // must not be quietly reopened by the same reporter.
    if (existing.exists && stringValue((existing.data() || {}).status) === "OPEN") {
      throw new HttpsError("already-exists", "You have already reported this");
    }
    transaction.set(
      reportRef,
      clean({
        id: reportId,
        targetType,
        targetId,
        reporterUid: uid,
        reason: reason.slice(0, 1000),
        status: "OPEN",
        createdAtMs: now,
        decidedAtMs: null,
        decidedByUid: null,
        decision: null,
      }),
      {merge: true},
    );
    return {reportId, status: "OPEN"};
  });
});

/**
 * A moderator rules on a report. Three outcomes, by the owner's rule: uphold, reject, or uphold
 * and ban the author. The reporter is paid for being right and charged for being wrong, which is
 * what stops reporting from being free.
 */
exports.decideReport = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const profile = await requireProfile(uid);
  const isModerator = profile.moderatorLevel >= QUALIFIED_LEVEL;
  const isDeveloper = profile.developerLevel > DEVELOPER_ALL_ACCESS_LEVEL;
  if (!isModerator && !isDeveloper) {
    throw new HttpsError("permission-denied", "Moderator level is required");
  }
  const reportId = stringValue(request.data && request.data.reportId);
  if (!reportId) throw new HttpsError("invalid-argument", "reportId is required");
  const decision = stringValue(request.data && request.data.decision).toUpperCase();
  if (!["UPHOLD", "REJECT", "UPHOLD_AND_BAN"].includes(decision)) {
    throw new HttpsError("invalid-argument", `Unsupported decision ${decision || "<empty>"}`);
  }
  const note = nullableString(request.data && request.data.note);
  const now = Date.now();

  const reportSnapshot = await db.collection("reports").doc(reportId).get();
  if (!reportSnapshot.exists) throw new HttpsError("not-found", `Report ${reportId} not found`);
  const report = reportSnapshot.data() || {};
  if (stringValue(report.status) !== "OPEN") {
    throw new HttpsError("failed-precondition", "Report is already decided");
  }
  const reporterUid = stringValue(report.reporterUid);
  if (reporterUid === uid) {
    throw new HttpsError("permission-denied", "Cannot decide your own report");
  }

  let bannedUid = null;
  let banUntilMs;
  if (decision === "UPHOLD_AND_BAN") {
    bannedUid = await reportTargetOwnerUid(report);
    if (!bannedUid) {
      throw new HttpsError("failed-precondition", "Report target has no owner to ban");
    }
    if (bannedUid === uid) {
      throw new HttpsError("permission-denied", "Cannot ban yourself");
    }
    const targetProfile = await requireProfile(bannedUid);
    // Banning a moderator takes seniority: a peer cannot silence a peer.
    if (targetProfile.moderatorLevel >= QUALIFIED_LEVEL && !isDeveloper) {
      if (profile.moderatorLevel < targetProfile.moderatorLevel + MODERATOR_BAN_LEVEL_GAP) {
        throw new HttpsError(
          "permission-denied",
          "Banning a moderator needs a level at least 100 above theirs, or a developer",
        );
      }
    }
    const durationMs = banDurationMsFor(profile);
    banUntilMs = durationMs === null ? null : now + durationMs;
    await db.collection("users").doc(bannedUid).set(
      clean({
        bannedUntilMs: banUntilMs,
        bannedByUid: uid,
        bannedAtMs: now,
        banReason: note || stringValue(report.reason),
      }),
      {merge: true},
    );
  }

  const upheld = decision !== "REJECT";
  await db.collection("reports").doc(reportId).set(
    clean({
      status: upheld ? "UPHELD" : "REJECTED",
      decision,
      decidedAtMs: now,
      decidedByUid: uid,
      deciderModeratorLevel: profile.moderatorLevel,
      note,
      bannedUid,
      banUntilMs: bannedUid ? banUntilMs : null,
    }),
    {merge: true},
  );
  await addReviewerReputation(
    reporterUid,
    upheld ? REPORT_UPHELD_POINTS : REPORT_REJECTED_POINTS,
  );
  return {reportId, status: upheld ? "UPHELD" : "REJECTED", bannedUid, banUntilMs};
});

/**
 * A senior moderator grades a decided report. Agreeing pays the original decider, overturning
 * costs them — the same +3 / -3 the review consensus uses, and the same reputation number.
 * Judgement about judgement is how a moderation corps stays honest without a second hierarchy.
 */
exports.reviewReportDecision = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const profile = await requireProfile(uid);
  const isDeveloper = profile.developerLevel > DEVELOPER_ALL_ACCESS_LEVEL;
  const reportId = stringValue(request.data && request.data.reportId);
  if (!reportId) throw new HttpsError("invalid-argument", "reportId is required");
  const agrees = Boolean(request.data && request.data.agrees);

  const snapshot = await db.collection("reports").doc(reportId).get();
  if (!snapshot.exists) throw new HttpsError("not-found", `Report ${reportId} not found`);
  const report = snapshot.data() || {};
  const deciderUid = stringValue(report.decidedByUid);
  if (!deciderUid) throw new HttpsError("failed-precondition", "Report is not decided yet");
  if (deciderUid === uid) {
    throw new HttpsError("permission-denied", "Cannot grade your own decision");
  }
  const deciderLevel = numberValue(report.deciderModeratorLevel, 0);
  if (!isDeveloper && profile.moderatorLevel < deciderLevel + MODERATOR_BAN_LEVEL_GAP) {
    throw new HttpsError(
      "permission-denied",
      "Grading a decision needs a level at least 100 above the decider's",
    );
  }

  const now = Date.now();
  await db.collection("reports").doc(reportId).set(
    clean({
      gradedByUid: uid,
      gradedAtMs: now,
      gradeAgrees: agrees,
    }),
    {merge: true},
  );
  await addReviewerReputation(deciderUid, agrees ? REPORT_UPHELD_POINTS : REPORT_REJECTED_POINTS);
  return {reportId, deciderUid, agrees};
});

/** Who owns the thing a report points at, so a ban has somebody to land on. */
async function reportTargetOwnerUid(report) {
  const targetType = stringValue(report.targetType);
  const targetId = stringValue(report.targetId);
  if (targetType === "USER") return targetId;
  if (targetType === "COMMENT") {
    const snapshot = await db.collection("lessonComments").doc(targetId).get();
    return snapshot.exists ? nullableString((snapshot.data() || {}).authorUid) : null;
  }
  if (targetType === "QUEST") {
    const snapshot = await db.collection("quests").doc(targetId).get();
    return snapshot.exists ? nullableString((snapshot.data() || {}).authorUid) : null;
  }
  // MESSAGE is reserved for the chat that does not exist yet — see chat-contract.md.
  return null;
}

/**
 * Removes one lesson comment. The comment's own rules are `update, delete: if false` for every
 * client, so this callable — running with admin credentials — is the only takedown there is.
 * Moderators are the audience; a developer's blanket access covers it too. The removed text is
 * preserved in the audit record: the evidence must survive the removal, or every dispute becomes
 * one person's word against another's.
 */
exports.removeLessonComment = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const profile = await requireProfile(uid);
  const isModerator = profile.moderatorLevel >= QUALIFIED_LEVEL;
  const isDeveloper = profile.developerLevel > DEVELOPER_ALL_ACCESS_LEVEL;
  if (!isModerator && !isDeveloper) {
    throw new HttpsError("permission-denied", "Moderator level is required");
  }
  const commentId = stringValue(request.data && request.data.commentId);
  if (!commentId) throw new HttpsError("invalid-argument", "commentId is required");
  const reason = nullableString(request.data && request.data.reason);
  const now = Date.now();
  const commentRef = db.collection("lessonComments").doc(commentId);

  return db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(commentRef);
    if (!snapshot.exists) {
      throw new HttpsError("not-found", `Comment ${commentId} not found`);
    }
    const comment = snapshot.data() || {};
    transaction.set(
      db.collection("admin").doc("moderation").collection("comment_removals").doc(`${commentId}_${now}`),
      clean({
        commentId,
        lessonId: nullableString(comment.lessonId),
        authorUid: nullableString(comment.authorUid),
        authorNickname: nullableString(comment.authorNickname),
        text: nullableString(comment.text),
        actorUid: uid,
        reason,
        removedAtMs: now,
      }),
      {merge: true},
    );
    transaction.delete(commentRef);
    return {commentId, removedAtMs: now};
  });
});

/**
 * One record per placement change, under admin/ where clients cannot write and reviewers can
 * read. This is how "who put this on the home screen, and when?" gets an answer — including the
 * author-triggered republish reset, the one demotion that used to happen silently.
 */
function writeShelfMoveAudit(transaction, options) {
  transaction.set(
    db.collection("admin").doc("shelf_moves").collection("moves").doc(`${options.questId}_${options.now}`),
    clean({
      questId: options.questId,
      catalogId: options.catalogId,
      actorUid: options.actorUid,
      action: options.action,
      previousVisibleOn: options.previousVisibleOn,
      visibleOn: options.visibleOn,
      reason: options.reason,
      changedAtMs: options.now,
    }),
    {merge: true},
  );
}

exports.recalculateTournamentLeaderboard = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  const profile = await requireProfile(uid);
  if (profile.developerLevel <= DEVELOPER_ALL_ACCESS_LEVEL) {
    throw new HttpsError("permission-denied", "Developer level is required");
  }

  const tournamentId = firestoreDocumentId(request.data && request.data.tournamentId, "tournamentId");
  return recalculateTournamentLeaderboard(tournamentId, Date.now());
});

exports.fetchTournamentOverview = onCall(CALLABLE_OPTIONS, async (request) => {
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
    // Время отвечания — разрыв ничьей по проценту (CAP-10). Читается обратно, иначе пересчёт
    // таблицы видел бы его только у тех результатов, что лежат в самой группе, а не в документах.
    elapsedMs: nullableNumber(firstDefined(data.elapsedMs, data.durationMs)),
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
      attemptCount: firestoreTypes.FieldValue.increment(1),
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
  // Плата и принятие заявки — одна транзакция (CAP-4): подача без денег не принимается, принятая
  // подача без платы не существует. Записи прежнего батча здесь не теряют ни поля.
  //
  // Исключение — приватный квиз (AD-29/UX-16): он не входит в публичный цикл проверки
  // (AD-26: приватный не проходит публичную доработку), плата проверки с него не берётся, и в
  // книге движений от приватной подачи не появляется ни строки (8.5). Тарифы и циклы это не
  // меняет: цикл платы приватной заявке просто не открывается.
  const isPrivateSubmission = request.draft.privateQuiz !== null;
  return db.runTransaction(async (transaction) => {
    // Рантайм приватной ссылки читается ВНУТРИ транзакции: старт попытки, влетевший между
    // чтением и записью, поднял бы startedCount — и перезаявка его затёрла бы (AD-29). Гейт
    // пишет счётчик merge-ом без версии квеста, сверку это не ловит, ловит только порядок.
    const previousPrivateQuizSettings = request.draft.privateQuiz ?
      await readPrivateQuizSettings(request.ownerUid, request.draft.catalogId, request.draft.id) :
      null;
    const charge = isPrivateSubmission ? null : await planReviewFeeCharge(transaction, request);
    writePrivateHierarchyToBatch(transaction, request, now, previousPrivateQuizSettings);
    writeAdminReviewLessonTasksToBatch(transaction, adminTasks);
    transaction.set(
      db.collection("quest_review_requests").doc(request.submissionId),
      clean({
        // Тело заявки целиком, а не одна отметка об обработке.
        //
        // Раньше документ создавал клиент прямой записью, а триггер лишь дописывал `processed`.
        // Прямая запись снята (AD-6), и писать сюда стало некому: без `ownerUid`, `draftId`,
        // `draft`, `sections`, `themes`, `lessons` и `questions` публикация строит квест по пустому
        // идентификатору (`quests/`), а автор не находит свой вердикт — выборка вердиктов идёт
        // по `ownerUid` и требует `draftId`, и правило чтения смотрит на тот же `ownerUid`.
        //
        // Состав полей — ровно тот, что читает `normalizeRequest`: документ обязан пережить
        // перезапуск и остаться единственным источником для `publishSubmissionIfReady`, которая
        // перечитывает его спустя дни после подачи.
        ...requestToDocument(request),
        processed: true,
        processedAtMs: now,
        lastError: null,
        status: "UNDER_REVIEW",
        // Свежая заявка не прошла ещё ничего: стадии называются сразу, а не молчанием,
        // по-вопросных исходов TESTING пока нет — пустой список, а не отсутствие поля.
        failedStages: initialFailedStages(request),
        questionOutcomes: [],
      }),
      {merge: true},
    );
    const reviewFee = charge
      ? writeReviewFeeChargeToTransaction(transaction, request, charge, now)
      : null;
    return {
      submissionId: request.submissionId,
      privateQuestPath: privateQuestPath(request.ownerUid, request.draft.catalogId, request.draft.id),
      adminLessonTaskPaths: adminTasks.map((task) => adminLessonPath(task.lessonId)),
      reviewFee,
    };
  });
}

// ─── Платный цикл проверки: плата и расчёт (AD-10, история 4.7) ─────────────────────────────────

/**
 * Цитата платы для диалога подачи с карточки черновика (история 4.11, D13).
 *
 * Только чтение: владелец выводится из Auth, тело — адрес квеста и число вопросов. `priorCycle` —
 * серверная правда (оплаченный цикл или публикация уже были), поэтому клиент её не угадывает.
 * Цитата называет цену заранее; платой станет расчёт приёмки из тела реальной заявки — цитата
 * властью не обладает.
 */
exports.quoteQuestReviewFee = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  await requireProfile(uid);
  const parsed = parseReviewFeeQuoteRequest(request.data);
  if (!parsed.ok) throw new HttpsError("invalid-argument", parsed.reason);
  const [tariffs, questSnap, publicSnap] = await Promise.all([
    readReviewFeeTariffs(),
    db.doc(`${REVIEW_FEE_QUESTS}/${parsed.questId}`).get(),
    db.doc(`quests/${parsed.questId}`).get(),
  ]);
  const priorCycle = questSnap.exists || publicSnap.exists;
  return quoteReviewFee({tariffs, priorCycle, questionCount: parsed.questionCount});
});

/** Где живут документы платного цикла. Коллекции закрыты правилами от клиентов. */
const REVIEW_FEE_TARIFFS_DOC = "configs/review_fee";
const REVIEW_FEE_CYCLES = "review_fee_cycles";
const REVIEW_FEE_QUESTS = "review_fee_quests";
const REVIEW_FEE_MANIFESTS = "review_fee_manifests";

/** Поле кошелька с дробным остатком вознаграждений проверки; отдельный carry от доли Арены (AD-9). */
const REVIEW_FEE_CARRY_FIELD = "reviewFeeCarryTenths";

/**
 * Тарифы платы из `configs/review_fee` — или принятые владельцем по умолчанию.
 *
 * Испорченный тариф отказывает громко всем платным подачам: тихое возвращение к умолчанию
 * выглядело бы как цена, которой владельцем не принято.
 */
async function readReviewFeeTariffs() {
  const snapshot = await db.doc(REVIEW_FEE_TARIFFS_DOC).get();
  try {
    return normalizeReviewFeeTariffs(snapshot.exists ? snapshot.data() : null);
  } catch (error) {
    throw new HttpsError("failed-precondition", `${REVIEW_FEE_TARIFFS_DOC}: ${error.message}`);
  }
}

/**
 * Решение о плате за принимаемую заявку: тариф, проверка денег, ключи для записи.
 *
 * Идемпотентность платы — документ цикла: повтор приёмки той же заявки (упавший инстанс,
 * догоняющий обработчик) находит его и плату не повторяет. Квест с оплаченной подачей или
 * публичной версией платит тариф перепроверки, первая подача — первоначальный.
 *
 * @returns {object|null} null, когда цикл уже оплачен.
 */
async function planReviewFeeCharge(transaction, request) {
  const cycleRef = db.doc(`${REVIEW_FEE_CYCLES}/${request.submissionId}`);
  if ((await transaction.get(cycleRef)).exists) return null;

  const questRef = db.doc(`${REVIEW_FEE_QUESTS}/${request.draft.id}`);
  const [questSnap, publicSnap, tariffs] = await Promise.all([
    transaction.get(questRef),
    transaction.get(db.doc(`quests/${request.draft.id}`)),
    readReviewFeeTariffs(),
  ]);
  const priorCycle = questSnap.exists || publicSnap.exists;
  // Ретро 2026-10-25 L4/п.2 (история 4.18): тариф решают СВОИ документы квеста. Чужой id в
  // заявке — именованный отказ без единой записи под чужим именем: и cheaper-recheck, и счётчик
  // циклов на чужом документе закрыты этой сверкой. Документ без владельца (легаси до введения
  // платы) чужим не считается — проверяется только известный и другой владелец.
  if (questSnap.exists) {
    const owner = stringValue((questSnap.data() || {}).ownerUid);
    if (owner && owner !== request.ownerUid) {
      throw new HttpsError("permission-denied", "REVIEW_FEE_DRAFT_NOT_OWNED");
    }
  }
  if (publicSnap.exists) {
    const author = stringValue((publicSnap.data() || {}).authorUid);
    if (author && author !== request.ownerUid) {
      throw new HttpsError("permission-denied", "REVIEW_FEE_DRAFT_NOT_OWNED");
    }
  }
  const feeNolics = reviewFeeNolics({tariffs, priorCycle, questionCount: request.questions.length});

  const userRef = db.doc(`users/${request.ownerUid}`);
  const userSnap = await transaction.get(userRef);
  const userData = userSnap.exists ? userSnap.data() || {} : {};
  const balance = nonNegativeInteger(userData.pointsNolics, numberValue(userData.nolics, 0));
  if (balance < feeNolics) {
    // Окончательный отказ: повтор заявки с тем же кошельком изменит нечего — денег по-прежнему нет.
    throw new HttpsError(
      "failed-precondition",
      `REVIEW_FEE_INSUFFICIENT: cycle fee ${feeNolics} nolics exceeds balance ${balance}`,
    );
  }
  return {
    feeNolics,
    tariff: priorCycle ? "recheck" : "initial",
    tariffRevision: tariffs.revision,
    questionCount: request.questions.length,
    balance,
    userRef,
    questRef,
    questSnap,
    cycleRef,
  };
}

/** Само списание: кошелёк, документ цикла, счётчик циклов квеста и строка книги денег. */
function writeReviewFeeChargeToTransaction(transaction, request, charge, now) {
  transaction.set(
    charge.userRef,
    clean({
      updatedAtMs: now,
      // Both fields: FirebaseUserStatsDataSource reads pointsNolics and the economy data source
      // reads nolics — ретро 2026-10-25 L4/п.2 (4.18): зеркало списания двустороннее, как во
      // всех денежных путях.
      pointsNolics: charge.balance - charge.feeNolics,
      nolics: charge.balance - charge.feeNolics,
    }),
    {merge: true},
  );
  transaction.create(charge.cycleRef, clean({
    submissionId: request.submissionId,
    ownerUid: request.ownerUid,
    questId: request.draft.id,
    catalogId: request.draft.catalogId,
    status: "PAID",
    feeNolics: charge.feeNolics,
    feeTenths: charge.feeNolics * TENTHS_PER_NOLIC,
    tariff: charge.tariff,
    tariffRevision: charge.tariffRevision,
    questionCount: charge.questionCount,
    chargedAtMs: now,
  }));
  transaction.set(
    charge.questRef,
    clean({
      questId: request.draft.id,
      ownerUid: request.ownerUid,
      catalogId: request.draft.catalogId,
      lastSubmissionId: request.submissionId,
      lastChargedAtMs: now,
      cycles: numberValue(charge.questSnap.exists ? (charge.questSnap.data() || {}).cycles : 0, 0) + 1,
    }),
    {merge: true},
  );
  for (const entry of ledgerEntries({
    uid: request.ownerUid,
    reason: REASON.REVIEW_FEE,
    sourceId: request.submissionId,
    atMs: now,
    deltas: {[CURRENCY.NOLICS]: -charge.feeNolics},
    details: {tariff: charge.tariff, questionCount: charge.questionCount, tariffRevision: charge.tariffRevision},
  })) {
    transaction.set(db.doc(`${LEDGER_PATH}/${entry.id}`), entry, {merge: true});
  }
  return {
    feeNolics: charge.feeNolics,
    tariff: charge.tariff,
    tariffRevision: charge.tariffRevision,
    questionCount: charge.questionCount,
  };
}

/**
 * Расчёт платного цикла: фонд делится между проверяющими, остаток сгорает (AD-10).
 *
 * Вызывается на терминальном переходе (PUBLISHED — включая возврат заявки автору с отказом, — и
 * REJECTED) и повторно при каждом обращении к уже терминальной заявке: расчёт идемпотентен
 * статусом цикла, так что упавший между публикацией и расчётом инстанс догоняется следующим же
 * действием, а не теряет деньги проверяющих. Заявка, не дошедшая до терминала, фонд не делит и
 * не сжигает (OQ-2 не добавлен); заявка без оплаченного цикла (бывшие до введения платы) не
 * рассчитывается вовсе.
 */
async function settleReviewFeeCycle(submissionId, outcome, now) {
  const requestSnapshot = await db.collection("quest_review_requests").doc(submissionId).get();
  if (!requestSnapshot.exists) return {settled: false, reason: "no-request"};
  const status = stringValue((requestSnapshot.data() || {}).status);
  if (status !== "PUBLISHED" && status !== "REJECTED") return {settled: false, reason: "not-terminal"};

  const cycleRef = db.doc(`${REVIEW_FEE_CYCLES}/${submissionId}`);
  const cycleSnap = await cycleRef.get();
  if (!cycleSnap.exists) return {settled: false, reason: "unpaid"};
  const cycle = cycleSnap.data() || {};
  if (cycle.status === "SETTLED") return {settled: true, replayed: true};

  const request = normalizeRequest(requestSnapshot.data(), submissionId);
  // Модельный мир (AD-32, история 4.18): проверяющих у конвейера нет — entitlements ровно нули,
  // весь фонд сгорает; записи ревьюеров и задания человеческого мира не читаются вовсе.
  const modelOutcome = request.reviewOutcome && typeof request.reviewOutcome === "object";
  const lessonIds =
    request.targetLessonIds.length > 0
      ? request.targetLessonIds
      : request.lessons.map((lesson) => lesson.id);
  const taskByLessonId = {};
  const recordsByLessonId = {};
  if (!modelOutcome) {
    for (const lessonId of lessonIds) {
      const taskSnapshot = await db.doc(adminLessonPath(lessonId)).get();
      if (taskSnapshot.exists) taskByLessonId[lessonId] = await adminLessonSnapshotToTask(taskSnapshot);
      recordsByLessonId[lessonId] = await readReviewRecords(lessonId, submissionId);
    }
  }
  const units = modelOutcome ? [] : buildReviewUnits({
    submissionId,
    lessonIds,
    taskByLessonId,
    recordsByLessonId,
    ownerUid: request.ownerUid,
  });
  const plan = settleReviewFeeTenths({feeTenths: numberValue(cycle.feeTenths, 0), units});
  const entitlementByReviewer = new Map();
  for (const allocation of plan.allocations) {
    const unit = units.find((item) => item.unitId === allocation.unitId);
    entitlementByReviewer.set(
      unit.reviewerUid,
      (entitlementByReviewer.get(unit.reviewerUid) || 0) + allocation.entitlementTenths,
    );
  }

  return db.runTransaction(async (transaction) => {
    const current = await transaction.get(cycleRef);
    if (!current.exists || (current.data() || {}).status === "SETTLED") {
      return {settled: true, replayed: true};
    }
    const wallets = [];
    for (const [uid, entitlementTenths] of Array.from(entitlementByReviewer.entries()).sort()) {
      const userSnap = await transaction.get(db.doc(`users/${uid}`));
      const userData = userSnap.exists ? userSnap.data() || {} : {};
      const credit = walletCreditForEntitlement({
        priorCarryTenths: nonNegativeInteger(userData[REVIEW_FEE_CARRY_FIELD], 0),
        entitlementTenths,
      });
      wallets.push({
        uid,
        entitlementTenths,
        priorCarryTenths: nonNegativeInteger(userData[REVIEW_FEE_CARRY_FIELD], 0),
        creditedNolics: credit.creditedNolics,
        nextCarryTenths: credit.nextCarryTenths,
        balance: nonNegativeInteger(userData.pointsNolics, numberValue(userData.nolics, 0)),
      });
    }
    const manifest = buildSettlementManifest({
      submissionId,
      ownerUid: request.ownerUid,
      questId: request.draft.id,
      outcome,
      settledAtMs: now,
      feeNolics: numberValue(cycle.feeNolics, 0),
      tariff: cycle.tariff || null,
      tariffRevision: numberValue(cycle.tariffRevision, 1),
      units,
      allocations: plan.allocations,
      wallets,
    });
    // Неизменяемость манифеста — create: второй расчёт того же цикла не существует.
    transaction.create(db.doc(`${REVIEW_FEE_MANIFESTS}/${submissionId}`), clean(manifest));
    // Кошельки пишутся из исходного массива: в манифесте баланса нет — он состояние кошелька,
    // а не факт расчёта. К этому месту buildSettlementManifest уже сверил арифметику carry.
    // Зеркало двустороннее (ретро L4/п.2, 4.18): pointsNolics и nolics вместе, как в списании.
    for (const wallet of wallets) {
      transaction.set(
        db.doc(`users/${wallet.uid}`),
        clean({
          pointsNolics: wallet.balance + wallet.creditedNolics,
          nolics: wallet.balance + wallet.creditedNolics,
          [REVIEW_FEE_CARRY_FIELD]: wallet.nextCarryTenths,
          updatedAtMs: now,
        }),
        {merge: true},
      );
      for (const entry of ledgerEntries({
        uid: wallet.uid,
        reason: REASON.REVIEW_REWARD,
        sourceId: submissionId,
        atMs: now,
        deltas: {[CURRENCY.NOLICS]: wallet.creditedNolics},
        details: {
          entitlementTenths: wallet.entitlementTenths,
          priorCarryTenths: wallet.priorCarryTenths,
          newCarryTenths: wallet.nextCarryTenths,
        },
      })) {
        transaction.set(db.doc(`${LEDGER_PATH}/${entry.id}`), entry, {merge: true});
      }
    }
    transaction.set(
      cycleRef,
      clean({
        status: "SETTLED",
        outcome,
        settledAtMs: now,
        manifestPath: `${REVIEW_FEE_MANIFESTS}/${submissionId}`,
        sumEntitlementTenths: manifest.sumEntitlementTenths,
        burnTenths: manifest.burnTenths,
      }),
      {merge: true},
    );
    return {settled: true, replayed: false, sumEntitlementTenths: manifest.sumEntitlementTenths,
      burnTenths: manifest.burnTenths};
  });
}

/**
 * Заявка как документ — обратная сторона `normalizeRequest`.
 *
 * Читатель и писатель заявки живут в одном файле и обязаны знать одни и те же поля: `normalizeRequest`
 * достаёт из документа ровно этот состав, `publishSubmissionIfReady` перечитывает документ спустя дни
 * после подачи и строит по нему публичный квест, а выборка вердиктов автору ищет по `ownerUid` и
 * `draftId`. Расхождение здесь не падает и не логируется — оно тихо публикует квест по пустому пути.
 * Парность двух списков закреплена `arena-request-document.test.js`.
 *
 * `status` в состав не входит: его ставит тот, кто пишет (обработка — `UNDER_REVIEW`, публикация —
 * `PUBLISHED`, отказ рецензента — `REJECTED`), и тело заявки в этом не участвует. Перезапись
 * телом вернула бы уже отклонённую заявку в проверку.
 */
function requestToDocument(request) {
  return {
    submissionId: request.submissionId,
    draftId: request.draftId,
    ownerUid: request.ownerUid,
    localRevision: request.localRevision,
    requestedAtMs: request.requestedAtMs,
    targetShelf: request.targetShelf,
    targetLessonIds: request.targetLessonIds,
    draftTree: request.draftTree,
    draft: request.draft,
    sections: request.sections,
    themes: request.themes,
    lessons: request.lessons,
    questions: request.questions.map(questionToDocument),
    review: checksToCallableMap(request.review),
    // Исход модельной проверки (4.16): при подаче его ещё нет — поле существует и пусто,
    // как failedStages выше; writer/reader симметрию стережёт arena-request-document.test.
    reviewOutcome: null,
  };
}

/**
 * Приватное дерево автора и его журнал одним батчем — по серверным часам.
 *
 * `now` приходит аргументом и обязан приходить: время журнала — это курсор читателя, а не свойство
 * заявки. Раньше запись датировалась `request.requestedAtMs` — числом, которое ставит устройство
 * автора (`SubmitQuestDraftToArenaUseCase` → тело очереди → `numberValue(data.requestedAtMs, 0)`).
 * У автора с убежавшими вперёд часами курсор `private_quests:{uid}` уезжал в будущее, и всё, что
 * сервер записывал потом — назначенная при публикации версия (`writeQuestVersionMirror`),
 * перекладка по полкам, рейтинг, — оказывалось ниже курсора и не читалось никогда: `serverRevision`
 * в черновик не приходил, следующая публикация уходила со стухшим `expected_version` и получала
 * вечный конфликт. Курсор монотонен и назад не возвращается, документ журнала на квест один и
 * пишется без merge, поэтому клиентское время не добавлялось к серверному, а замещало его, и
 * лечилось это только «Перечитать всё».
 *
 * Отсюда правило, общее для всех четырёх журналов (AD-11, AD-31): `changedAtMs` ставит тот, чьи
 * часы задают порядок чтения, — сервер. Время устройства остаётся в полях про содержание
 * (`updatedAtMs`, `createdAtMs`, `requestedAtMs` в теле заявки), где оно и означает «когда автор
 * это сделал». Стережёт `sync-change-clock.test.js`.
 */
function writePrivateHierarchyToBatch(batch, request, now, previousPrivateQuizSettings = null) {
  for (const [path, data] of Object.entries(privateDocuments(request, now, previousPrivateQuizSettings))) {
    batch.set(db.doc(path), clean(data), {merge: true});
  }
  // Заявка трогает только приватную копию квеста: публичного узла у черновика ещё нет.
  writeSyncChanges(
    batch,
    db,
    {kind: "private_quest", id: request.draft.id, catalogId: request.draft.catalogId, ownerUid: request.ownerUid},
    now,
  );
}

/**
 * Копия назначенной версии квеста в приватный документ автора.
 *
 * Клиент читает `serverRevision` из `private/{uid}/catalogs/{c}/quests/{q}`
 * (`FirebaseQuestPrivateRemoteDataSource`), а сверяется `expected_version` с версией той же самой
 * сущности. Пока публикация писала версию только в `quests/{id}`, приватный документ её не имел
 * вовсе: `serverRevision` выходил нулевым, поле в конверт не попадало, и сверка не выполнялась ни
 * разу. Единственная запись, которая туда её всё-таки клала, была запись рейтинга — то есть автор
 * сверялся со счётчиком чужих звёзд.
 *
 * Отсюда правило: где назначается версия квеста, там же она и зеркалится. Два документа несут одно
 * число, и «моя редакция выросла из версии N» — утверждение про квест, а не про то, из какого
 * документа его прочитали.
 *
 * Отметка `changedAtMs` обязательна рядом: приватная синхронизация ходит по ней. Запись в
 * приватный журнал здесь не делается — её делает тот же вызов `writeSyncChanges` для узла `quest`,
 * который пишет и каталожный журнал: квест принадлежит обоим (AD-11), и запись в них одна.
 *
 * @param {*} writer батч или транзакция.
 */
function writeQuestVersionMirror(writer, options) {
  const ownerUid = stringValue(options.ownerUid);
  const catalogId = stringValue(options.catalogId);
  const questId = stringValue(options.questId);
  // Квест без автора зеркалить некуда. Такие документы остались от сидов, которые пишут публичное
  // дерево напрямую и приватной половины не имеют; терять из-за них перекладку по полкам нельзя.
  if (!ownerUid || !catalogId || !questId) return;
  writer.set(
    db.doc(privateQuestPath(ownerUid, catalogId, questId)),
    {
      version: options.assignedVersion,
      changedAtMs: options.now,
    },
    {merge: true},
  );
}

/**
 * Задания на ревью — документы и их журнал одним батчем.
 *
 * Задание переписывается целиком при создании, оценке, отклонении и публикации, и каждый раз в
 * `admin/review/sync_changes/{assignmentId}` ложится запись с новым `changedAtMs` — иначе рецензент,
 * читающий журнал, о смене статуса не узнает.
 */
function writeAdminReviewLessonTasksToBatch(batch, tasks) {
  for (const [path, data] of Object.entries(adminDocuments(tasks))) {
    batch.set(db.doc(path), clean(data), {merge: true});
  }
  for (const task of tasks) {
    writeSyncChanges(batch, db, {kind: "review_assignment", id: task.id}, task.changedAtMs);
  }
}

async function reconcileChangedReviewLessons(sinceMs, now) {
  const changes = await db.collection(ADMIN_REVIEW_SYNC_CHANGES)
    .where("changedAtMs", ">", sinceMs)
    .get();
  const assignmentIds = new Set(
    changes.docs
      .map((doc) => stringValue((doc.data() || {}).id, doc.id))
      .filter(Boolean),
  );
  if (assignmentIds.size === 0) return {checked: 0, published: 0};

  const config = await readArenaReviewConfig();
  const tasks = await readAdminReviewLessonTasksByIds(assignmentIds);
  const touchedSubmissionIds = new Set();
  const batch = db.batch();

  for (const task of tasks) {
    const records = await readReviewRecords(task.lessonId, task.submissionId);
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
  // Последний прогон транзакции — тот, что закоммитился; его адресаты и получают push.
  let changedUids = [];
  await db.runTransaction(async (transaction) => {
    changedUids = [];
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
    changedUids = qualificationApplication.changedUids;

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
        qualificationField: AUTHOR_RATING_SCORE_FIELD,
        qualificationScore,
        qualificationAppliedScore: qualificationApplication.appliedScore,
        updatedAtMs: now,
      }),
      {merge: true},
    );
    writeQuestRatingAggregateToWriter(transaction, dirty, averageRating, count, now, targetUid);
    transaction.delete(dirtyDoc.ref);
  });
  // После коммита: уровень квалификации изменился — автору уходит повод, не число (AD-20).
  for (const uid of changedUids) {
    await notifyUser(PUSH_EVENTS.AUTHOR_RATING_CHANGED, uid, uid);
  }
  return result;
}

function writeQuestRatingAggregateToWriter(writer, dirty, averageRating, averageRatingCount, now, ownerUid) {
  const ratingPatch = clean({
    averageRating,
    averageRatingCount,
    ratingUpdatedAtMs: now,
    lastModifiedAt: firestoreTypes.Timestamp.fromMillis(now),
    // Рейтинг двигает СВОЙ счётчик и не трогает `version` — ни здесь, ни в приватном документе ниже.
    //
    // `version` означает ровно одно: «редакция содержимого сущности», и с ней сверяется
    // `expected_version` автора. Начисление рейтинга содержимое квеста не меняет — его меняет
    // чужой человек, поставивший звёзды. Пока рейтинг двигал `version`, автор получал конфликт за
    // то, что его квест оценили, а приватный документ (тот самый, из которого клиент читает
    // `serverRevision`) уезжал вперёд публичного, и приёмник сверял два несвязанных счётчика.
    //
    // Отдельный счётчик, а не только `lastModifiedAt`, потому что клиентский приёмник снимков
    // сравнивает целые числа (`QuestDao.upsertByIdIfNewerVersion`), а не время: время приезжает из
    // разных мест, и «новее» по нему — вопрос часов, а не факта. `ratingUpdatedAtMs` рядом остаётся
    // отметкой «когда пересчитали», а не признаком новизны.
    ratingVersion: firestoreTypes.FieldValue.increment(1),
  });
  if (dirty.scope === PRIVATE_SCOPE) {
    // Рейтинг приватного квеста меняет только приватный документ — узел `private_quest`.
    writer.set(
      db.doc(privateQuestPath(dirty.ownerUid, dirty.catalogId, dirty.questId)),
      {...ratingPatch, changedAtMs: now},
      {merge: true},
    );
    writeSyncChanges(
      writer,
      db,
      {kind: "private_quest", id: dirty.questId, catalogId: dirty.catalogId, ownerUid: dirty.ownerUid},
      now,
    );
    return;
  }
  writer.set(db.collection("quests").doc(dirty.questId), ratingPatch, {merge: true});
  // Рейтинг публичного квеста — изменение публичного узла, и автор узнаёт о нём из своего журнала
  // так же, как о перекладке по полкам: `ownerUid` здесь — автор, которому начисляется квалификация.
  writeSyncChanges(
    writer,
    db,
    {kind: "quest", id: dirty.questId, catalogId: dirty.catalogId, ownerUid: nullableString(ownerUid)},
    now,
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
  // Кому уйдёт push после коммита. Сама отправка здесь жить не может: транзакция переигрывается,
  // а уведомление повторяться не должно.
  //
  // Уведомляется только нынешний автор и только когда его рейтинг ВЫРОС. Прежний владелец при
  // переносе рейтинга получал «ваша квалификация изменилась» о том, что у него отобрали, — а
  // автор получал то же самое, когда оценка опустила его число. Уровня квалификации по этому
  // числу в проекте нет (ADR-0006: рейтинг автора — репутация, а не ранг), поэтому единственное
  // событие, о котором игроку есть смысл сказать, — «стало лучше». Чем оно должно стать, когда
  // появится оффер квалификации, записано в deferred-work.md.
  const changedUids = [];
  if (
    options.previousTargetUid &&
    options.previousTargetUid !== options.targetUid &&
    options.previousAppliedScore !== 0
  ) {
    const previousDelta = writeAuthorRatingScoreDelta(
      transaction,
      targetSnapshots,
      options.previousTargetUid,
      -options.previousAppliedScore,
      options.now,
    );
    appliedDelta += previousDelta;
    // Прежнему владельцу не пишем: у него отняли, это не повод для уведомления.
  }
  if (options.targetUid) {
    const previousTargetAppliedScore =
      options.previousTargetUid === options.targetUid ? options.previousAppliedScore : 0;
    const targetDelta = writeAuthorRatingScoreDelta(
      transaction,
      targetSnapshots,
      options.targetUid,
      options.qualificationScore - previousTargetAppliedScore,
      options.now,
    );
    appliedDelta += targetDelta;
    appliedScore = previousTargetAppliedScore + targetDelta;
    if (targetDelta > 0) changedUids.push(options.targetUid);
  }
  return {delta: appliedDelta, appliedScore, changedUids};
}

function writeAuthorRatingScoreDelta(transaction, targetSnapshots, uid, delta, now) {
  if (!uid || delta === 0) return 0;
  const target = targetSnapshots.get(uid);
  if (!target) return 0;
  const current = currentAuthorRatingScore(target.userData, target.profileData);
  const next = Math.max(0, current + delta);
  const appliedDelta = next - current;
  if (appliedDelta === 0) return 0;
  // One field, at the top level of both documents. The old write also fanned the number into the
  // `qualifications` and `qualification` maps, which is exactly what made a reputation look like a
  // rank to every reader downstream.
  transaction.set(
    target.userRef,
    clean({
      uid,
      updatedAtMs: now,
      [AUTHOR_RATING_SCORE_FIELD]: next,
    }),
    {merge: true},
  );
  transaction.set(
    target.profileRef,
    clean({
      uid,
      updatedAtMs: now,
      [AUTHOR_RATING_SCORE_FIELD]: next,
    }),
    {merge: true},
  );
  return appliedDelta;
}

function currentAuthorRatingScore(userData, profileData) {
  return Math.max(
    nonNegativeNumber(profileData[AUTHOR_RATING_SCORE_FIELD], 0),
    nonNegativeNumber(userData[AUTHOR_RATING_SCORE_FIELD], 0),
  );
}

/**
 * Строгий гейт исхода модельной проверки (4.16): документ пишет воркер, но читать его публикуя
 * можно только в форме, которую этот код понимает. Всё прочее — громкий отказ на ремонте, а не
 * молчаливая публикация чего-то непонятного.
 */
function normalizeReviewOutcome(raw) {
  const outcome = raw;
  if (!outcome || typeof outcome !== "object" || Array.isArray(outcome)) {
    throw new HttpsError("data-loss", "review outcome must be an object");
  }
  if (outcome.pipelineStatus !== "QUEST_REVIEWED" && outcome.pipelineStatus !== "REVIEW_DELAYED") {
    throw new HttpsError("data-loss", "review outcome names an unknown pipeline status");
  }
  if (!Array.isArray(outcome.passingLessonIds)) {
    throw new HttpsError("data-loss", "review outcome must name passing lessons");
  }
  if (outcome.passingLessonIds.some((id) => typeof id !== "string" || !id)) {
    throw new HttpsError("data-loss", "review outcome passing lessons must be identifiers");
  }
  if (outcome.detectedLanguage !== null && outcome.detectedLanguage !== undefined &&
    !["ru", "uk", "en"].includes(outcome.detectedLanguage)) {
    throw new HttpsError("data-loss", "review outcome names an unknown detected language");
  }
  return outcome;
}

/**
 * Публикация модельной заявки (AD-32, 4.16, UX-6).
 *
 * Исход воркера решает: REVIEW_DELAYED — заявка получает «Проверка задерживается» и фонд не
 * делит (незавершённый цикл, OQ-2 parked); QUEST_REVIEWED без прошедших уроков — отказ с
 * «Не прошёл» и списком заваленных вопросов; иначе публикуются прошедшие уроки независимо от
 * соседей: заявка сужается до прошедшего состава, переводы (review_translations) едут в дерево
 * вариантами `__lang` рядом с оригиналом. Оркестрация повторяет человеческую половину
 * публикации — общие помощники те же, ветка своя, чтобы мир ревьюеров не тянуть через флаги
 * (4.19 удалит его целиком).
 */
async function publishModelReviewedSubmission(submissionId, request, requestSnapshot, config, now) {
  const outcome = normalizeReviewOutcome(request.reviewOutcome);
  if (outcome.pipelineStatus === "REVIEW_DELAYED") {
    await requestSnapshot.ref.set({status: "REVIEW_DELAYED", updatedAtMs: now}, {merge: true});
    return false;
  }
  const passing = await resolvePassingLessonsWithTranslations(submissionId, request, outcome);
  if (passing.lessonIds.length === 0) {
    await requestSnapshot.ref.set({
      status: "REJECTED",
      rejectedAtMs: now,
      rejectionReason: "REVIEW_FAILED_QUESTIONS",
      updatedAtMs: now,
    }, {merge: true});
    await settleReviewFeeCycle(submissionId, "REJECTED", now);
    return false;
  }

  const publishable = {
    ...request,
    lessons: passing.lessons,
    questions: mergeQuestions(
      request.questions.filter((question) => passing.ids.has(question.lessonId)),
      passing.translatedQuestions,
    ),
  };
  const batch = db.batch();
  // Версии узлов читаются до батча: батч писать умеет, а читать нет (AD-12).
  const hierarchySnapshots = await readPublicHierarchySnapshots(publishable);
  const currentVersions = versionsByPath(hierarchySnapshots);
  // Снимается только то, что эта публикация описывает: прошедший состав и есть весь состав.
  const pruned = planHierarchyPrune({
    request: publishable,
    existing: await readPublishedSubtree(publishable),
    now,
  });
  const privatePruned = planPrivateHierarchyPrune({
    request: publishable,
    existing: await readPrivateSubtree(publishable),
  });
  const existingQuest = hierarchySnapshots[0];
  if (existingQuest.exists) {
    const existing = existingQuest.data() || {};
    const previousVisibleOn = Array.isArray(existing.visibleOn)
      ? existing.visibleOn.filter((shelf) => typeof shelf === "string")
      : [];
    const nextShelf = request.targetShelf;
    if (previousVisibleOn.length !== 1 || previousVisibleOn[0] !== nextShelf) {
      writeShelfMoveAudit(batch, {
        questId: request.draft.id,
        catalogId: request.draft.catalogId,
        actorUid: request.ownerUid,
        action: "republish",
        previousVisibleOn,
        visibleOn: [nextShelf],
        reason: null,
        now,
      });
    }
  }
  const assignedVersion = nextVersionAtPath(currentVersions, `quests/${request.draft.id}`);
  writePublicHierarchyToBatch(batch, publishable, now, currentVersions, assignedVersion);
  writeHierarchyPruneToBatch(batch, pruned, now);
  writePrivateHierarchyPruneToBatch(batch, db, privatePruned, now);
  logPrivatePruneOutcome(privatePruned);
  writeQuestVersionMirror(batch, {
    ownerUid: request.ownerUid,
    catalogId: request.draft.catalogId,
    questId: request.draft.id,
    assignedVersion,
    now,
  });
  for (const lesson of publishable.lessons) {
    const lessonPath = `lessons/${lesson.id}`;
    const previous = hierarchySnapshots.find((snapshot) => snapshot.ref.path === lessonPath);
    const previousData = previous && previous.exists ? previous.data() || {} : {};
    batch.set(db.doc(lessonPath), {
      authorUid: request.ownerUid,
      reviewSubmissionId: submissionId,
      reviewApprovedAtMs: now,
      publishedAtMs: nonNegativeInteger(previousData.publishedAtMs, now),
    }, {merge: true});
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
  // Публикация зафиксирована — автору уходит повод синхронизироваться, не данные (AD-20).
  await notifyUser(PUSH_EVENTS.QUEST_PUBLISHED, request.draft.id, request.ownerUid);
  // Полный цикл: фонд делится сразу за публикацией; отказ расчёта догонится следующим вызовом.
  await settleReviewFeeCycle(submissionId, "PUBLISHED", Date.now());
  return true;
}

/**
 * Прошедшие уроки с их переводами (4.16): состав берётся из исхода воркера, переводы — из
 * записей review_translations по checkpoint-у конвейера. Урок из исхода, которого нет в заявке,
 * публике не открывается: исход описывает этот submission, чужой идентификатор — мусор.
 */
async function resolvePassingLessonsWithTranslations(submissionId, request, outcome) {
  const lessonsById = new Map(request.lessons.map((lesson) => [lesson.id, lesson]));
  const ids = [];
  const lessons = [];
  const translatedQuestions = [];
  const pipelineSnapshot = await db.doc(`review_pipeline/${submissionId}`).get();
  const pipeline = pipelineSnapshot && pipelineSnapshot.exists ? pipelineSnapshot.data() || {} : {};
  for (const lessonId of outcome.passingLessonIds) {
    const lesson = lessonsById.get(lessonId);
    if (!lesson) continue;
    ids.push(lessonId);
    lessons.push(lesson);
    const verdictId = pipeline.lessons && pipeline.lessons[lessonId] &&
      pipeline.lessons[lessonId].stages && pipeline.lessons[lessonId].stages.TRANSLATE &&
      pipeline.lessons[lessonId].stages.TRANSLATE.verdictId;
    if (!verdictId) continue;
    const translationSnapshot = await db.doc(`review_translations/${verdictId}`).get();
    const translation = translationSnapshot && translationSnapshot.exists
      ? translationSnapshot.data() || {} : {};
    for (const language of ["uk", "en"]) {
      for (const question of Array.isArray(translation.questions && translation.questions[language])
        ? translation.questions[language] : []) {
        if (question && typeof question === "object" && question.id) {
          translatedQuestions.push(normalizeQuestion(question));
        }
      }
    }
  }
  return {ids: new Set(ids), lessonIds: ids, lessons, translatedQuestions};
}

/** Прочитать исход модельной заявки и довести её до авторского статуса (4.16, UX-6). */
async function advanceModelReviewedSubmission(submissionId) {
  const requestSnapshot = await db.collection("quest_review_requests").doc(submissionId).get();
  if (!requestSnapshot.exists) return false;
  const request = normalizeRequest(requestSnapshot.data() || {}, submissionId);
  if (!request.reviewOutcome || typeof request.reviewOutcome !== "object") return false;
  if (request.status !== "UNDER_REVIEW" && request.status !== "PROCESSING") return false;
  return publishModelReviewedSubmission(submissionId, request, requestSnapshot, await readArenaReviewConfig(), Date.now());
}

exports.advanceQuestReviewOutcome = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  await requireProfile(uid);
  const submissionId = stringValue(request.data && request.data.submissionId);
  if (!submissionId) throw new HttpsError("invalid-argument", "submissionId is required");
  // Чужая заявка неотличима от отсутствующей: право проверяет владелец, не существование.
  const snapshot = await db.collection("quest_review_requests").doc(submissionId).get();
  if (!snapshot.exists || snapshot.data().ownerUid !== uid) {
    throw new HttpsError("not-found", "Review submission could not be found");
  }
  const published = await advanceModelReviewedSubmission(submissionId);
  return {published};
});

/**
 * Страница «Решений» для админов (4.17, UX-22): по-вопросные вердикты модели, соединённые
 * с заявкой (названия, тело вопроса) и отметками админов. Право решает requireVerifier —
 * тем же забором, что и прочие админские чтения; не-админ получает отказ, не данные.
 */
/** Именованные отказы модуля «Решений» — в честные коды HTTPS, без сырых внутренних кодов. */
function rethrowDecisionsError(error) {
  const httpsCode = {INVALID_ARGUMENT: "invalid-argument", NOT_FOUND: "not-found", DATA_LOSS: "data-loss"}[
    error && error.code
  ];
  if (httpsCode) throw new HttpsError(httpsCode, error.message);
  throw error;
}

exports.fetchReviewDecisions = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  await requireVerifier(uid);
  try {
    return await readReviewDecisions(db, request.data || {}, {
      documentId: firestoreTypes.FieldPath.documentId(),
    });
  } catch (error) {
    rethrowDecisionsError(error);
  }
});

/**
 * Отметка админа на вопросе вердикта (✓/✗ с необязательным комментарием): живёт отдельно
 * от неизменяемых вердиктов, право — тот же requireVerifier. Повторная отметка перезаписывает.
 */
exports.markReviewDecision = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthUid(request);
  await requireVerifier(uid);
  try {
    return await writeReviewDecisionMark(db, {uid, nowMs: Date.now()}, request.data || {});
  } catch (error) {
    rethrowDecisionsError(error);
  }
});

async function publishSubmissionIfReady(submissionId, config, now) {
  const requestSnapshot = await db.collection("quest_review_requests").doc(submissionId).get();
  if (!requestSnapshot.exists) return false;
  const request = normalizeRequest(requestSnapshot.data() || {}, submissionId);
  // Терминальные заявки фонд делят при каждом обращении: расчёт идемпотентен статусом цикла, и
  // вызов, увидевший уже рассчитанный цикл, стоит двух чтений. Заявка, ставшая терминальной в
  // упавшем перед расчётом инстансе, догоняется первым же следующим действием (AD-9).
  if (request.status === "PUBLISHED") {
    await settleReviewFeeCycle(submissionId, "PUBLISHED", now);
    return false;
  }
  // A rejected submission is finished. The author resubmits to try again, which mints a new
  // submission id — this one never publishes, not even through the nightly reconcile.
  if (request.status === "REJECTED") {
    await settleReviewFeeCycle(submissionId, "REJECTED", now);
    return false;
  }
  // Модельный конвейер (AD-32, история 4.16): исход записал воркер, здесь — публикация
  // прошедших уроков и авторский статус заявки. Человеческий путь ниже не тронут: его уносит
  // 4.19 вместе с ролями ревьюеров.
  if (request.reviewOutcome && typeof request.reviewOutcome === "object") {
    return publishModelReviewedSubmission(submissionId, request, requestSnapshot, config, now);
  }

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
  if (targetTasks.some((task) => task.rejectedAtMs)) {
    await requestSnapshot.ref.set({status: "REJECTED", updatedAtMs: now}, {merge: true});
    await settleReviewFeeCycle(submissionId, "REJECTED", now);
    return false;
  }
  // A stored aggregate is not proof: older clients could submit their own checks, and review
  // documents survive a new submission of the same lesson. Rebuild from this submission's
  // independently accepted records; preserve the old history without laundering it into proof.
  for (const task of targetTasks) {
    const records = (await readReviewRecords(task.lessonId)).filter((record) =>
      record.submissionId === task.submissionId && record.reviewerUid !== task.ownerUid);
    task.checks = rebuildAggregate({...task, checks: reviewToChecks({})}, records, config).checks;
  }
  const allReady = targetTasks.every((task) => lessonPassed(task.checks, task, config));
  if (!allReady) {
    // Автор видит, КАКИЕ стадии не пущены, поимённо и без чьей-либо причины: стадии считаются
    // из оценок, причина — атрибут только отклонения. Рядом лежат и по-вопросные исходы
    // TESTING (CAP-3): какой вопрос оценён, сколько он набрал и прошёл ли порог.
    const failedStages = Array.from(
      new Set(targetTasks.flatMap((task) => failedReviewStages(task.checks, task, config))),
    ).sort();
    const questionOutcomes = Array.from(
      new Map(
        targetTasks
          .flatMap((task) => task.checks.questionOutcomes || [])
          .map((outcome) => [outcome.questionId, outcome]),
      ).values(),
    ).sort((a, b) => (a.questionId < b.questionId ? -1 : a.questionId > b.questionId ? 1 : 0));
    await requestSnapshot.ref.set(
      {status: "UNDER_REVIEW", updatedAtMs: now, failedStages, questionOutcomes},
      {merge: true},
    );
    return false;
  }

  const batch = db.batch();
  const publishable = requestWithPublishedQuestions(request, targetTasks);
  // Версии узлов дерева читаются здесь, до батча: батч писать умеет, а читать нет, и без этого
  // чтения сервер не знает, какая версия у каждого узла сейчас. Первым в списке идёт сам квест —
  // тот же снимок отвечает и на вопрос про полки ниже, второе чтение того же документа не нужно.
  const hierarchySnapshots = await readPublicHierarchySnapshots(publishable);
  const currentVersions = versionsByPath(hierarchySnapshots);
  // Что автор из дерева убрал. Публикация пишет только узлы заявки и только с `merge`, поэтому без
  // этого шага удалённый урок или вопрос остаётся на сервере с `archived: false`, возвращается из
  // `refreshByIds` и живёт на устройстве игрока вечно (AD-26).
  // Судить о составе вопросов сервер вправе только у тех уроков, чьи вопросы этот батч и пишет:
  // урок из заявки, не попавший в эту публикацию, ею не описан, и его вопросы трогать нельзя.
  const prunable = {
    ...publishable,
    lessons: publishable.lessons.filter((lesson) => requestedLessonIds.has(lesson.id)),
  };
  const pruned = planHierarchyPrune({
    request: prunable,
    existing: await readPublishedSubtree(prunable),
    now,
  });
  // Та же половина работы на приватной копии автора. Публичное снятие ей ничего не говорит: автор
  // читает своё дерево из `private/{uid}/…`, и урок, снятый только в каталоге, оставался у него
  // навсегда — приватный документ жил вечным объединением всех прошлых публикаций.
  const privatePruned = planPrivateHierarchyPrune({
    request: prunable,
    existing: await readPrivateSubtree(prunable),
  });
  // A republish overwrites visibleOn with the submission's target shelf, which demotes a quest a
  // curator had placed elsewhere. That reset stays — unreviewed edits must not remain on a curated
  // shelf — but it stops being silent: the audit names the author as the actor.
  const existingQuest = hierarchySnapshots[0];
  if (existingQuest.exists) {
    const existing = existingQuest.data() || {};
    const previousVisibleOn = Array.isArray(existing.visibleOn)
      ? existing.visibleOn.filter((shelf) => typeof shelf === "string")
      : [];
    const nextShelf = request.targetShelf;
    if (previousVisibleOn.length !== 1 || previousVisibleOn[0] !== nextShelf) {
      writeShelfMoveAudit(batch, {
        questId: request.draft.id,
        catalogId: request.draft.catalogId,
        actorUid: request.ownerUid,
        action: "republish",
        previousVisibleOn,
        visibleOn: [nextShelf],
        reason: null,
        now,
      });
    }
  }
  // Версия квеста назначается один раз и здесь: её получает публичный документ, её же получает
  // приватный. Два вызова `nextVersionAtPath` на одну карту дали бы сегодня то же число и разошлись
  // бы в тот день, когда один из них поправят.
  const assignedVersion = nextVersionAtPath(currentVersions, `quests/${request.draft.id}`);
  writePublicHierarchyToBatch(batch, publishable, now, currentVersions, assignedVersion);
  writeHierarchyPruneToBatch(batch, pruned, now);
  writePrivateHierarchyPruneToBatch(batch, db, privatePruned, now);
  logPrivatePruneOutcome(privatePruned);
  writeQuestVersionMirror(batch, {
    ownerUid: request.ownerUid,
    catalogId: request.draft.catalogId,
    questId: request.draft.id,
    assignedVersion,
    now,
  });
  for (const task of targetTasks) {
    writeAdminReviewLessonTasksToBatch(batch, [{...task, status: "PUBLISHED", changedAtMs: now}]);
    const lessonPath = `lessons/${task.lessonId}`;
    const previous = hierarchySnapshots.find((snapshot) => snapshot.ref.path === lessonPath);
    const previousData = previous && previous.exists ? previous.data() || {} : {};
    batch.set(db.doc(lessonPath), {
      authorUid: task.ownerUid,
      reviewSubmissionId: task.submissionId,
      reviewApprovedAtMs: now,
      publishedAtMs: nonNegativeInteger(previousData.publishedAtMs, now),
    }, {merge: true});
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
  // Публикация зафиксирована — автору уходит повод синхронизироваться, не данные (AD-20).
  await notifyUser(PUSH_EVENTS.QUEST_PUBLISHED, request.draft.id, request.ownerUid);
  // Полный цикл: фонд делится сразу за публикацией. Отказ расчёта не отменяет публикацию — его
  // догонит следующий вызов на этой заявке (см. терминальные ветки выше).
  await settleReviewFeeCycle(submissionId, "PUBLISHED", Date.now());
  return true;
}

/**
 * Читает узлы публикуемого дерева пачкой.
 *
 * Чтением по одному это быть не может: дерево — сотня документов, и сотня последовательных чтений
 * растянула бы публикацию настолько, что срабатывал бы таймаут функции. `getAll` берёт их одним
 * обращением, а порции нужны потому, что запрос с неограниченным числом ссылок упирается в предел
 * размера самого запроса.
 */
async function readPublicHierarchySnapshots(request) {
  const refs = publicHierarchyPaths(request).map((path) => db.doc(path));
  const snapshots = [];
  for (let index = 0; index < refs.length; index += MAX_DOCUMENTS_PER_GET) {
    const chunk = refs.slice(index, index + MAX_DOCUMENTS_PER_GET);
    snapshots.push(...(await db.getAll(...chunk)));
  }
  return snapshots;
}

/** Сколько ссылок отдаём в одно `getAll`. */
const MAX_DOCUMENTS_PER_GET = 100;

/** Сколько значений отдаём в одно `in`-условие: предел Firestore. */
const MAX_VALUES_PER_IN = 30;

/**
 * Уже опубликованное поддерево квеста — то, с чем сравнивается заявка.
 *
 * Читается ровно столько, сколько заявка вправе судить. Вопросы — у уроков, которые в заявке есть:
 * их состав она описывает целиком. Секции, темы и уроки — только когда заявка назвала полный состав
 * черновика (`draftTree`); без него они не снимаются, и читать их незачем.
 */
async function readPublishedSubtree(request) {
  const lessonIds = (request.lessons || []).map((lesson) => lesson.id).filter(Boolean);
  const questions = await readDocsWhere("questions", "lessonId", lessonIds);
  if (!request.draftTree) return {sections: [], themes: [], lessons: [], questions};

  const sections = await readDocsWhere("sections", "questId", [request.draft.id]);
  const sectionIds = mergedIds(sections, request.draftTree.sections);
  const themes = await readDocsWhere("themes", "sectionId", sectionIds);
  const themeIds = mergedIds(themes, request.draftTree.themes);
  const lessons = await readDocsWhere("lessons", "themeId", themeIds);
  return {sections, themes, lessons, questions};
}

/** Идентификаторы прочитанных строк плюс объявленные заявкой — чтобы не потерять новых детей. */
function mergedIds(rows, declaredIds) {
  return [...new Set(rows.map((row) => row.id).concat(declaredIds || []).filter(Boolean))];
}

/**
 * Приватная копия дерева у автора — то, с чем сравнивается заявка на его половине.
 *
 * Читается перечислением, а не по вычисленным путям: документ урока лежит там, куда его положила
 * заявка, которая его создала, и заявка сегодняшняя могла перенести урок в другую тему. Перечисление
 * находит его там, где он есть; арифметика по путям сегодняшней заявки — только там, где он был бы.
 *
 * Вопросы читаются лишь у уроков этой публикации: только их состав заявка описывает целиком, и
 * только их она вправе судить. У остальных уроков вопросы не читаются вовсе — их и не трогают.
 *
 * @returns {{sections: Array, themes: Array, lessons: Array, questions: Array}} строки
 *   `{id, path, lessonId?}` — ровно то, что ждёт `planPrivateHierarchyPrune`.
 */
async function readPrivateSubtree(request) {
  const ownerUid = stringValue(request.ownerUid);
  const catalogId = request.draft ? stringValue(request.draft.catalogId) : "";
  const questId = request.draft ? stringValue(request.draft.id) : "";
  const subtree = {sections: [], themes: [], lessons: [], questions: []};
  if (!ownerUid || !catalogId || !questId) return subtree;

  const submittedLessons = new Set((request.lessons || []).map((lesson) => lesson.id).filter(Boolean));
  const questRef = db.doc(privateQuestPath(ownerUid, catalogId, questId));
  const sections = await questRef.collection("sections").get();
  for (const section of sections.docs) {
    subtree.sections.push({id: section.id, path: section.ref.path});
    const themes = await section.ref.collection("themes").get();
    for (const theme of themes.docs) {
      subtree.themes.push({id: theme.id, path: theme.ref.path});
      const lessons = await theme.ref.collection("lessons").get();
      for (const lesson of lessons.docs) {
        subtree.lessons.push({id: lesson.id, path: lesson.ref.path});
        if (!submittedLessons.has(lesson.id)) continue;
        const questions = await lesson.ref.collection("questions").get();
        for (const question of questions.docs) {
          subtree.questions.push({id: question.id, path: question.ref.path, lessonId: lesson.id});
        }
      }
    }
  }
  return subtree;
}

/**
 * Что приватное снятие оставило нетронутым — отдельной строкой от публичной.
 *
 * Заявка старого клиента не несёт `draftTree`, и урок, тема и раздел по ней не снимаются ни в одном
 * из двух деревьев. У автора это заметно иначе, чем у игрока: удалённый урок остаётся у него на
 * устройстве, а не в чужом каталоге, — поэтому строка своя, а не общая с публичной.
 */
function logPrivatePruneOutcome(plan) {
  if (plan.skipped) {
    logger.info("publish: the author's own copy was left untouched", {questId: plan.questId || null, reason: plan.skipped});
    return;
  }
  const undecidable = Object.entries(plan.undecidable || {});
  if (undecidable.length === 0) return;
  logger.info("publish: private nodes outside this submission were left as they are", {
    questId: plan.questId || null,
    counts: Object.fromEntries(undecidable.map(([collection, ids]) => [collection, ids.length])),
  });
}

/** Строки коллекции, у которых поле равно одному из значений: `{id, currentVersion, archived, lessonId}`. */
async function readDocsWhere(collection, field, values) {
  const rows = [];
  for (let index = 0; index < values.length; index += MAX_VALUES_PER_IN) {
    const chunk = values.slice(index, index + MAX_VALUES_PER_IN);
    if (chunk.length === 0) continue;
    const snapshot = await db.collection(collection).where(field, "in", chunk).get();
    for (const doc of snapshot.docs) {
      const data = doc.data() || {};
      rows.push({
        id: doc.id,
        currentVersion: currentEntityVersion(data),
        archived: data.archived === true,
        lessonId: stringValue(data.lessonId) || null,
      });
    }
  }
  return rows;
}

/**
 * Снятие выпавших узлов — тем же батчем, что и сама публикация.
 *
 * Документ и его записи в журналах уходят одним коммитом: снятый узел без записи в журнале клиент
 * не увидит никогда, а запись без снятия отправила бы его перечитывать неизменившийся документ.
 */
function writeHierarchyPruneToBatch(batch, plan, now) {
  for (const [path, data] of Object.entries(plan.documents)) {
    // Версию назначает `entity-version.js` и только он (AD-12): снятие узла — изменение его
    // содержимого, и клиент обязан увидеть его как новую версию, а не как то же число.
    setVersioned(
      batch,
      db.doc(path),
      {
        archived: data.archived,
        lastModifiedAt: firestoreTypes.Timestamp.fromMillis(data.lastModifiedAtMs),
      },
      data.currentVersion,
      {merge: true},
    );
  }
  for (const node of plan.nodes) writeSyncChanges(batch, db, node, now);
  const undecidable = Object.entries(plan.undecidable || {});
  if (undecidable.length > 0) {
    // Опубликованный урок, тема или раздел, которых в этой заявке нет. Удалены они или просто не в
    // этой порции — по заявке не видно, и сервер их не трогает. Строка нужна, чтобы «квест копит
    // мусор» было видно раньше, чем это заметит игрок; она уходит, как только заявка начнёт
    // называть полный состав черновика.
    logger.info("publish: nodes outside this submission were left as they are", {
      questId: plan.questId || null,
      counts: Object.fromEntries(undecidable.map(([collection, ids]) => [collection, ids.length])),
    });
  }
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

/**
 * Документы приватного дерева автора.
 *
 * `now` — серверное время того же батча, что пишет журнал: приватная синхронизация ходит по
 * `changedAtMs`, и документ, датированный часами устройства, разошёлся бы со своей же записью в
 * журнале. Поля про содержание (`createdAtMs`, `updatedAtMs`) остаются авторскими — они говорят,
 * когда автор правил черновик, а не когда сервер это записал.
 *
 * `previousSettings` — настройки приватной ссылки из документа квеста ДО этой заявки (null, когда
 * квест ещё не писалесь или настроек не носит). Правило AD-29: перезаявка обновляет три
 * авторских поля (лимит, видимость результата, срок) и НЕ трогает рантайм — `enabled` и
 * `startedCount` остаются серверными. Повышение лимита допускает новые старты в пределах
 * разницы, понижение не обрывает начатую попытку; закрытая вручную ссылка перезаявкой не
 * открывается заново — reopen решением не назначен (UX-16).
 */
function privateDocuments(request, now, previousSettings = null) {
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
    changedAtMs: now,
  };
  const previous =
    previousSettings && typeof previousSettings === "object" && !Array.isArray(previousSettings) ?
      previousSettings :
      null;
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
    changedAtMs: now,
    review: checksToCallableMap(request.review),
    // Настройки пишет только заявка, чьё тело их несёт (draft.privateQuiz — разбор отрезал
    // рантайм-поля). Перезаявка обычного (неприватного) квеста настроек не пишет вовсе, и
    // merge не трогает то, что в документе уже лежит.
    ...(request.draft.privateQuiz ?
      {
        privateQuizSettings: {
          attemptLimit: request.draft.privateQuiz.attemptLimit,
          showResult: request.draft.privateQuiz.showResult,
          linkExpiresAtMs: request.draft.privateQuiz.linkExpiresAtMs,
          // Рантайм переезжает из документа, а не из тела: новая ссылка рождается включённой
          // с нулём стартов, существующая сохраняет и счётчик, и состояние (включая ручное
          // закрытие) — перезаявкой оно не меняется (AD-29).
          enabled: previous ? previous.enabled !== false : true,
          startedCount: previous ? numberValue(previous.startedCount, 0) : 0,
        },
      } :
      {}),
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
      // Id задания — то, по чему клиент зовёт fetchReviewAssignments после журнала. Раньше урок
      // задания хранился в теле записи журнала; тело теперь три поля (AD-11), и задание ищется
      // по этому полю (`readAdminReviewLessonTasksByIds`).
      assignmentId: task.id,
      submissionId: task.submissionId,
      ownerUid: task.ownerUid,
      catalogId: task.catalogId,
      draftId: task.draftId,
      questId: task.questId,
      title: task.title,
      createdAtMs: task.createdAtMs,
      changedAtMs: task.changedAtMs,
      status: task.status || lessonReviewStatus(task.checks, task, null),
      generation: numberValue(task.generation, 1),
      rejectedAtMs: task.rejectedAtMs || null,
      rejectedByUid: task.rejectedByUid || null,
      // Уровень судьи на момент отклонения: расчёт платного цикла платит за отклонение как за
      // работу (AD-10), и без этого поля в документе юнит отклонения не существует.
      rejectedReviewerLevelAtSubmit: task.rejectedReviewerLevelAtSubmit === undefined
        ? null
        : task.rejectedReviewerLevelAtSubmit,
      rejectionReason: task.rejectionReason || null,
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
  }
  return documents;
}

function writePublicHierarchyToBatch(batch, request, now, currentVersions, assignedVersion) {
  for (const [path, data] of Object.entries(publicDocuments(request, now, currentVersions, assignedVersion))) {
    batch.set(db.doc(path), clean(data), {merge: true});
  }
  for (const node of publicHierarchyNodes(request)) {
    writeSyncChanges(batch, db, node, now);
  }
}

/**
 * Узлы публикуемого дерева для журнала — тот же состав, что и у документов в `publicDocuments`.
 *
 * Квест идёт с автором: публикация зеркалит версию в его приватный документ
 * (`writeQuestVersionMirror`), и автор читает об этом из своего журнала. Вопрос принадлежит двум
 * журналам — каталожному и журналу своего урока; какие именно, решает объявление, не это место.
 */
function publicHierarchyNodes(request) {
  const catalogId = request.draft.catalogId;
  const nodes = [{kind: "quest", id: request.draft.id, catalogId, ownerUid: nullableString(request.ownerUid)}];
  for (const section of request.sections) nodes.push({kind: "section", id: section.id, catalogId});
  for (const theme of request.themes) nodes.push({kind: "theme", id: theme.id, catalogId});
  for (const lesson of request.lessons) nodes.push({kind: "lesson", id: lesson.id, catalogId});
  for (const question of request.questions) {
    nodes.push({kind: "question", id: question.id, catalogId, lessonId: question.lessonId});
  }
  return nodes;
}

/**
 * Пути всех узлов публикуемого дерева — тот же список, по которому потом строятся документы.
 *
 * Существует ради того, чтобы версии читались ровно у тех узлов, которые батч перезапишет: список,
 * собранный отдельно от записи, однажды разъедется с ней, и разъехавшийся узел начнёт получать
 * версию 1 при каждой публикации.
 */
function publicHierarchyPaths(request) {
  const paths = [`quests/${request.draft.id}`];
  for (const section of request.sections) paths.push(`sections/${section.id}`);
  for (const theme of request.themes) paths.push(`themes/${theme.id}`);
  for (const lesson of request.lessons) paths.push(`lessons/${lesson.id}`);
  for (const question of request.questions) paths.push(`questions/${question.id}`);
  return paths;
}

/**
 * Документы публикации.
 *
 * `currentVersions` — карта «путь -> текущая версия», прочитанная до батча: батч читать не умеет, а
 * версию обязан назначить сервер, и притом каждому узлу свою (AD-12). Узел, которого ещё нет, в
 * карте отсутствует и получает первую версию; узел, который уже есть, — свою плюс единицу. Одно
 * число на всё дерево не годится: правка одного урока двигала бы версию соседнего, и клиент
 * перекачивал бы дерево целиком на каждую запятую.
 */
function publicDocuments(request, now, currentVersions, assignedVersion) {
  const versions = currentVersions || {};
  const documents = {};
  const catalogId = request.draft.catalogId;
  const questId = request.draft.id;
  const archivedRoot = request.targetShelf === "archive";
  const questPath = `quests/${questId}`;
  documents[questPath] = {
    id: questId,
    catalogId,
    authorUid: request.ownerUid,
    title: request.draft.title,
    picturePath: null,
    visibleOn: [request.targetShelf],
    averageRating: null,
    averageRatingCount: 0,
    version: assignedVersion,
    lastModifiedAt: firestoreTypes.Timestamp.fromMillis(now),
    archived: archivedRoot,
  };
  for (const section of request.sections) {
    const sectionPath = `sections/${section.id}`;
    documents[sectionPath] = {
      id: section.id,
      questId,
      title: section.title,
      order: section.order,
      version: nextVersionAtPath(versions, sectionPath),
      lastModifiedAt: firestoreTypes.Timestamp.fromMillis(now),
      archived: false,
    };
  }
  for (const theme of request.themes) {
    const themePath = `themes/${theme.id}`;
    documents[themePath] = {
      id: theme.id,
      sectionId: theme.sectionId,
      title: theme.title,
      order: theme.order,
      version: nextVersionAtPath(versions, themePath),
      lastModifiedAt: firestoreTypes.Timestamp.fromMillis(now),
      archived: false,
    };
  }
  for (const lesson of request.lessons) {
    const lessonPath = `lessons/${lesson.id}`;
    documents[lessonPath] = {
      id: lesson.id,
      themeId: lesson.themeId,
      title: lesson.title,
      order: lesson.order,
      version: nextVersionAtPath(versions, lessonPath),
      lastModifiedAt: firestoreTypes.Timestamp.fromMillis(now),
      archived: false,
    };
  }
  for (const question of request.questions) {
    const questionPath = `questions/${question.id}`;
    documents[questionPath] = {
      id: question.id,
      lessonId: question.lessonId,
      text: question.text,
      payload: question.payload,
      language: question.language,
      languageLevel: question.languageLevel,
      order: question.order,
      version: nextVersionAtPath(versions, questionPath),
      lastModifiedAt: firestoreTypes.Timestamp.fromMillis(now),
      archived: false,
    };
  }
  // The other half of every question, gathered into one server-only document per lesson. The
  // payload above is written exactly as before — this only starts producing the key beside it, so
  // that turning redaction on later is a change to one write rather than a migration of two.
  // Per lesson rather than per question because a question already costs four writes in this
  // batch, and a fifth would drop the 500-write ceiling below submissions that publish today.
  //
  // The slice that starts publishing the redacted payload must take both halves from the SAME
  // redact() call — the shuffle is drawn fresh each time, so a second call re-keys every Ordering
  // and FillBlank against a permutation that was never published. Until then every document these
  // produce says so, in publicHalfRedacted.
  const questionKeys = questionKeyDocuments(request.questions);
  Object.assign(documents, questionKeys.documents);
  if (questionKeys.refusals.length > 0) {
    // A refusal means that question's answer is still inside the payload written above, where
    // anyone can read it. Nothing reads question_keys — the rules deny it to every client and
    // there is no admin surface — so this log line is the only place a refusal is actually
    // visible, and the only thing that can be alerted on. The list is capped because a log entry
    // over Cloud Logging's size limit is dropped whole, which would be worse than a sample.
    logger.warn("question-key: published without an answer key", {
      questId,
      catalogId,
      refusalCount: questionKeys.refusals.length,
      refusals: questionKeys.refusals.slice(0, LOGGED_REFUSAL_SAMPLE),
    });
  }
  return documents;
}

/** How many refused questions one publish reports by name before the count has to speak for them. */
const LOGGED_REFUSAL_SAMPLE = 50;

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
      // Поколение заданий: одна ревизия заявки — одно поколение; результат ревью едет с номером
      // поколения, чтобы перегенерация заданий была видна в уже принятых решениях.
      generation: 1,
      createdAtMs,
      changedAtMs: createdAtMs,
      // Author claims about testing/logic/translation never grant publication authority.
      checks: reviewToChecks({}),
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

  // The author's own quest never shows up in their queue — canSubmit refuses it anyway, but an
  // offer that always errors is worse than no offer.
  if (task.ownerUid && profile.uid === task.ownerUid) return new Set();

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
  // Nobody waves their own quest through. The one exemption is above, not here: a developer's
  // blanket access covers their own content by the owner's explicit rule, everyone else's does not.
  if (task.ownerUid && profile.uid === task.ownerUid) return false;
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
  const accepted = records.filter((record) => record.acceptedByServer && record.reviewerUid !== task.ownerUid &&
    (!task.submissionId || record.submissionId === task.submissionId));
  const testing = scoreAggregate(accepted, "TESTING");
  const logic = scoreAggregate(accepted, "LOGIC");
  const questionOutcomes = testingQuestionOutcomes(task, accepted, config);
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
      // Стадия закрыта оценкой урока И, когда задание знает состав вопросов, проходом каждого:
      // задание без вопросного состава (старые записи) судится по оценке урока, как прежде.
      isTested:
        stagePassed(testing.score, config) &&
        (questionOutcomes.length === 0 || questionOutcomes.every((outcome) => outcome.passed)),
      testingScore: testing.score,
      questionOutcomes,
      isLogicReviewed: logic.score !== null,
      logicScore: logic.score,
      isTranslationReviewed: Array.from(required).every((language) => Object.prototype.hasOwnProperty.call(translated, language)),
      translationScore,
      translatedLanguages: translated,
      // Результат привязан к тому, ПОД ЧЕМ он посчитан: ревизия конфига и поколение заданий
      // едут вместе с проверками, чтобы смена порога посреди цикла не переписывала задним
      // числом уже принятые решения.
      appliedConfigRevision: numberValue(config && config.configRevision, 1),
      assignmentGeneration: numberValue(task && task.generation, 1),
    },
    reviewerDeltas: testing.reviewerDeltas.concat(logic.reviewerDeltas),
  };
}

/**
 * По-вопросные исходы TESTING: один независимый ревью (≠ автор) на каждый вопрос ревизии.
 *
 * Оценка вопроса — среднее по принятым записям, отдавшим за него вопросную оценку; вопрос
 * пропущен, когда оценён и его среднее дошло до порога из конфига. Вопрос без единой оценки
 * остаётся неоценённым (testingScore null) — тишина никогда не считается одобрением.
 */
function testingQuestionOutcomes(task, acceptedRecords, config) {
  const threshold = numberValue(config && config.testingPassScore, DEFAULT_TESTING_PASS_SCORE);
  // Ревизию судят по её собственному составу: переведённые варианты (`__<lang>`, влитые
  // mergeQuestions после допуска перевода) — новый языковой контент, а не неоценённые вопросы
  // ревизии; их появление не отзывает уже пройденное тестирование. Вопрос без языка
  // классифицировать нельзя — он остаётся (задания прежних версий языка не несли).
  const sources = new Set(Array.from(task.sourceLanguages || []).map(normalizeLanguage).filter(Boolean));
  const taskQuestions = (Array.isArray(task.questions) ? task.questions : []).filter((question) => {
    const language = normalizeLanguage(question.language);
    return sources.size === 0 || language === "" || sources.has(language);
  });
  const outcomes = taskQuestions.map((question) => {
    const scores = [];
    for (const record of acceptedRecords) {
      if (record.kind !== "TESTING") continue;
      const perQuestion = (record.questionScores || []).find((entry) => entry.questionId === question.id);
      if (perQuestion && perQuestion.score !== null && perQuestion.score !== undefined) {
        scores.push(numberValue(perQuestion.score, null));
      }
    }
    if (scores.length === 0) {
      return {questionId: question.id, testingScore: null, passed: false};
    }
    const score = scores.reduce((sum, value) => sum + value, 0) / scores.length;
    return {questionId: question.id, testingScore: score, passed: score >= threshold};
  });
  // Урок без вопросного списка (старые задания) живёт по записям с вопросными оценками как есть:
  // пустой список вопросов не может превратить isTested в ложь, когда стадия закрыта оценками.
  if (taskQuestions.length === 0) return [];
  return outcomes;
}

function lessonReviewStatus(checks, task, config) {
  // A rejection is terminal until the author resubmits, so it outranks the score arithmetic.
  if (task && task.rejectedAtMs) return "REJECTED";
  return lessonPassed(checks, task, config) ? "READY_FOR_PUBLICATION" : "UNDER_REVIEW";
}

/**
 * Какие стадии не пущены, поимённо — без всякой причины от ревьюера.
 *
 * Причина — атрибут отклонения (rejectReviewSubmission требует её обязательно); проваленная
 * стадия вычисляется из оценок и говорит сама за себя: автор видит, ЧТО не прошло (TESTING /
 * LOGIC / TRANSLATION), и чинит именно это. Ничего не не пущено — пустой список.
 */
/**
 * Стадии, не пройденные свежей заявкой (ещё до первого ревью): тестирование и логика ждут
 * оценок, перевод — требуемых языков. Автор видит это с момента подачи.
 */
function initialFailedStages(request) {
  const tasks = request.lessons.map((lesson) => {
    const lessonQuestions = request.questions.filter((question) => question.lessonId === lesson.id);
    return {
      checks: reviewToChecks({}),
      questions: lessonQuestions,
      sourceLanguages: new Set(lessonQuestions.map((question) => normalizeLanguage(question.language))),
    };
  });
  return Array.from(new Set(tasks.flatMap((task) => failedReviewStages(task.checks, task, null)))).sort();
}

function failedReviewStages(checks, task, config) {
  const failed = [];
  if (!stagePassed(checks.testingScore, config) || !checks.isTested) failed.push("TESTING");
  if (!stagePassed(checks.logicScore, config) || !checks.isLogicReviewed) failed.push("LOGIC");
  const required = requiredLanguages(task, config);
  const translated = checks.translatedLanguages || {};
  const sourceLanguages = new Set(
    Array.from(task.sourceLanguages || []).map(normalizeLanguage).filter(Boolean),
  );
  const translationMissing = Array.from(required).some(
    (language) =>
      !sourceLanguages.has(normalizeLanguage(language)) &&
      numberValue(translated[language], 0) < REVIEW_PASS_LEVEL,
  );
  if (translationMissing) failed.push("TRANSLATION");
  return failed;
}

function lessonPassed(checks, task, config) {
  const required = requiredLanguages(task, config);
  const translated = checks.translatedLanguages || {};
  const sourceLanguages = new Set(
    Array.from(task.sourceLanguages || []).map(normalizeLanguage).filter(Boolean),
  );
  return (
    stagePassed(checks.testingScore, config) &&
    checks.isTested &&
    stagePassed(checks.logicScore, config) &&
    checks.isLogicReviewed &&
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
    // «Незавершённого перевода не существует»: вопрос без текста (и без картинки) — не перевод,
    // и такая запись никогда не засчитывает язык, что бы ни было в счётчике вопросов (CAP-3).
    const completed = languageRecords
      .filter((record) => (record.translatedQuestions || []).length >= sourceQuestionCount)
      .filter((record) => record.translatedQuestions.every(carriesContent))
      .sort((a, b) => b.reviewerLevelAtSubmit - a.reviewerLevelAtSubmit)[0];
    if (completed) result[language] = completed.reviewerLevelAtSubmit;
  }
  return result;
}

function carriesContent(question) {
  return (question.text !== null && question.text !== undefined && String(question.text).trim().length > 0) ||
    (question.imagePath !== null && question.imagePath !== undefined);
}

function requiredLanguages(task, config) {
  const configured = new Set(
    ((config && config.requiredLanguages) || []).map(normalizeLanguage).filter(Boolean),
  );
  return configured.size > 0 ? configured : new Set(Array.from(task.sourceLanguages).map(normalizeLanguage).filter(Boolean));
}

function actionToRecord(action, profile, task, config, existingRecords, now, pinnedReviewId = null) {
  const reviewerLevel = levelFor(profile, action.kind);
  const language = action.language ? normalizeLanguage(action.language) : null;
  // Из очереди id задан ключом мутации и не зависит от часов: повтор строит ту же запись.
  const reviewId = pinnedReviewId ||
    `${action.assignmentId}_${action.kind.toLowerCase()}_${now}_${profile.uid}`
    .replace(/[^A-Za-z0-9_.-]/g, "_");

  if (action.kind === "TESTING" || action.kind === "LOGIC") {
    if (action.score === null || action.score === undefined) {
      throw new HttpsError("invalid-argument", `${action.kind} requires score`);
    }
    // TESTING оценивает КАЖДЫЙ вопрос ревизии (один независимый ревью на вопрос); LOGIC судит
    // урок целиком. Вопросный список задания — единственный источник состава: чужой/лишний id
    // и пропущенный вопрос собираются здесь, на приёмнике, а не доверяются проводу.
    let questionScores = [];
    if (action.kind === "TESTING") {
      questionScores = validatedTestingQuestionScores(action.questionScores, task);
    }
    return {
      id: reviewId,
      lessonId: action.lessonId,
      kind: action.kind,
      reviewerUid: profile.uid,
      reviewerLevelAtSubmit: reviewerLevel,
      score: action.score,
      questionScores,
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

/**
 * Задания по их id.
 *
 * Задание — документ урока под `admin/review/lessons`, а id задания — «заявка × урок». Урок ищется
 * по полю `assignmentId` документа: журнал ревью больше не носит `lessonId` в теле (AD-11: три
 * поля, `id` — ключ для этой самой выборки).
 *
 * Одним запросом по этому полю обойтись нельзя. Задания, записанные прежней версией сервера, поля
 * не имеют вовсе, и такое задание перестало бы находиться навсегда: рецензент получал бы из журнала
 * его id и пустой ответ, а `ReviewAssignmentSync` понимает пустой ответ как «задание удалено» и
 * стирает его с устройства. Поэтому поиск — цепочка из трёх шагов (`review-journal.js`), а найденное
 * окольным путём задание тут же доклеивает себе поле: следующее чтение идёт коротким путём, и
 * доклейка не превращается в постоянный второй запрос.
 */
async function readAdminReviewLessonTasksByIds(ids) {
  if (!ids || ids.size === 0) return [];
  const wanted = [...ids].map((id) => stringValue(id)).filter(Boolean);
  const {resolved, heals} = await resolveReviewAssignments(db, wanted);
  const tasks = [];
  for (const hit of resolved) {
    const lesson = hit.lesson || (await db.doc(adminLessonPath(hit.lessonId)).get());
    if (lesson.exists) tasks.push(await adminLessonSnapshotToTask(lesson));
  }
  await stampAssignmentIds(heals);
  return tasks;
}

/**
 * Доклеивает `assignmentId` заданиям, найденным окольным путём.
 *
 * Отдельным батчем, а не внутри чтения: чтение обязано вернуть задания даже если запись не удалась
 * — рецензент важнее ускорения следующего чтения. Поэтому отказ здесь только логируется.
 */
async function stampAssignmentIds(heals) {
  if (!heals || heals.length === 0) return;
  try {
    const batch = db.batch();
    for (const heal of heals) batch.set(db.doc(heal.path), heal.data, {merge: true});
    await batch.commit();
  } catch (error) {
    logger.warn("review-assignment: could not stamp assignmentId", {
      count: heals.length,
      error: errorMessage(error),
    });
  }
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
    generation: numberValue(data.generation, 1),
    rejectedAtMs: numberValue(data.rejectedAtMs, 0) || null,
    rejectedByUid: nullableString(data.rejectedByUid),
    rejectedReviewerLevelAtSubmit: data.rejectedReviewerLevelAtSubmit === null ||
      data.rejectedReviewerLevelAtSubmit === undefined
      ? null
      : numberValue(data.rejectedReviewerLevelAtSubmit, 0),
    rejectionReason: nullableString(data.rejectionReason),
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

async function readReviewRecords(lessonId, submissionId = null) {
  const snapshot = await db.doc(adminLessonPath(lessonId)).collection("reviews").get();
  return snapshot.docs.map((doc) => normalizeReviewRecord(doc.data() || {}, doc.id, lessonId))
    .filter((record) => !submissionId || record.submissionId === submissionId);
}

async function readArenaReviewConfig() {
  const snapshot = await db.doc("configs/arena_review").get();
  if (!snapshot.exists) return defaultArenaReviewConfig();
  const data = snapshot.data() || {};
  const testingPassScore = numberValue(data.testingPassScore, DEFAULT_TESTING_PASS_SCORE);
  // Порог решает судьбу контента: тихо исправленный неверный порог ловит ошибку слишком поздно —
  // конфиг с числом вне 1..3 громко отказывает всем ревью-путям, пока владелец не починит.
  if (!Number.isInteger(testingPassScore) || testingPassScore < 1 || testingPassScore > 3) {
    throw new HttpsError(
      "failed-precondition",
      `configs/arena_review.testingPassScore must be an integer in 1..3, got ${data.testingPassScore}`,
    );
  }
  return {
    requiredLanguages: stringArray(data.requiredLanguages),
    testingPassScore,
    configRevision: numberValue(data.configRevision, 1),
    updatedAtMs: numberValue(data.updatedAtMs, 0),
  };
}

/** Порог, при котором TESTING-оценка пропускает вопрос/ревизию (1..3: 1 валит, 2 и 3 пропускают). */
const DEFAULT_TESTING_PASS_SCORE = 2;

function defaultArenaReviewConfig() {
  return {requiredLanguages: [], testingPassScore: DEFAULT_TESTING_PASS_SCORE, configRevision: 1, updatedAtMs: 0};
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
    // Loaded at last: until moderation existed, no gate read this field and requireProfile
    // deliberately skipped it.
    moderatorLevel: numberValue(data.moderatorLevel, 0),
    developerLevel: numberValue(data.developerLevel, 0),
    knownLanguages: stringArray(data.knownLanguages),
  };
}

/**
 * Whether the one free self-chosen name has been spent.
 *
 * Filtered in memory rather than with a second where clause: pairing uid with generated would need
 * a composite index, which works in the emulator and fails on the first production call. Nobody
 * holds enough names for the difference to matter.
 */
/**
 * Whether the one free name has been spent.
 *
 * The flag is the answer, except where it is not. Bootstrapping used to re-stamp whichever name was
 * being worn as system-assigned on every launch, so accounts from before that fix carry chosen
 * names flagged as generated, and trusting the flag alone would hand them a free name for ever.
 *
 * A name is also treated as chosen when it is not one this account could have been handed. That
 * repairs the old records where they are read instead of migrating them, and it cannot mislabel a
 * genuine hand-out, because those are exactly the names the generator produces.
 */
async function hasChosenNickname(uid) {
  const claims = await db.collection(NICKNAME_CLAIMS_COLLECTION).where("uid", "==", uid).get();
  const handedOut = new Set(
    generatedNicknameCandidates(uid, "").map((name) => canonicalNickname(name)).filter(Boolean),
  );
  return claims.docs.some((doc) => {
    const data = doc.data() || {};
    if (data.generated !== true) return true;
    return !handedOut.has(stringValue(data.canonical));
  });
}

async function refuseIfPurchaseRequired(uid, nickname) {
  const canonical = canonicalNickname(nickname);
  if (!canonical) return;
  const claim = await db.collection(NICKNAME_CLAIMS_COLLECTION).doc(nicknameClaimId(canonical)).get();
  // Already yours, or already somebody else's — either way nothing is being bought here, and the
  // upsert decides. Only an unclaimed name can turn into a purchase.
  if (claim.exists) return;
  if (await hasChosenNickname(uid)) {
    throw new HttpsError("failed-precondition", "purchase-required");
  }
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

/** Полный состав черновика: три списка идентификаторов, или null, если заявка его не называет. */
function normalizeDraftTree(value) {
  if (!value || typeof value !== "object") return null;
  const sections = stringArray(value.sections);
  const themes = stringArray(value.themes);
  const lessons = stringArray(value.lessons);
  // Урок — единственный обязательный список: без него снимать нечего и не от чего отталкиваться.
  if (lessons.length === 0) return null;
  return {sections, themes, lessons};
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
    // Полный состав черновика, если заявка его называет. Заявка несёт порцию — выбранные уроки, их
    // темы и разделы, — и по ней одной нельзя отличить «урок удалён» от «урок не в этой порции».
    // Пока поля нет, урок, тема и раздел на публикации не снимаются (`hierarchy-prune.js`); клиент,
    // который начнёт его слать, включает снятие, ничего больше не меняя (AD-13).
    draftTree: normalizeDraftTree(data.draftTree),
    status: stringValue(data.status),
    // Исход модельной проверки (4.16) пишет воркер; форму проверяет normalizeReviewOutcome
    // в точке использования — здесь только честный проход, чтобы публикация видела правду.
    reviewOutcome: data.reviewOutcome && typeof data.reviewOutcome === "object" &&
      !Array.isArray(data.reviewOutcome) ? data.reviewOutcome : null,
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
      // Настройки приватной ссылки (AD-29, история 8.3). Едут в теле заявки тем же конвейером
      // SUBMIT_ARENA — параллельного механизма подачи нет. Пусто у всякого неприватного квеста:
      // поле читает только писатель приватного документа (privateDocuments).
      privateQuiz: normalizePrivateQuizSettings(data.draft && data.draft.privateQuiz),
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
 * Настройки приватной ссылки из тела заявки — три авторских поля, нормированные гейтом.
 *
 * Гейт (`normalizeSettings`) — единственный владелец формы и дефолтов AD-29: лимит целый ≥1,
 * срок — момент ДО которого старт разрешён, либо `null` («без срока»). Рантайм-поля
 * (`enabled`, `startedCount`) из тела не читаются никогда: их место — документ квеста, и
 * подставленное в тело значение не имеет силы закрыть или приоткрыть ссылку мимо сервера.
 * Пусто — не ошибка: неприватный квест настроек не носит, а приватный без настроек (старое
 * тело) гейт уже сегодня обслуживает дефолтами AD-29.
 */
function normalizePrivateQuizSettings(raw) {
  if (raw === null || raw === undefined) return null;
  if (typeof raw !== "object" || Array.isArray(raw)) {
    throw new HttpsError("invalid-argument", "draft.privateQuiz must be an object");
  }
  // Рантайм-поля отрезаны разбором: normalizeSettings их нормирует, но писатель документа
  // берёт отсюда только три авторских поля. startedCount здесь всегда 0 и никогда не пишется.
  const settings = normalizeSettings(raw);
  return {
    attemptLimit: settings.attemptLimit,
    showResult: settings.showResult,
    linkExpiresAtMs: settings.linkExpiresAtMs,
  };
}

/*
 * `normalizeLessonAnswers` stood here, beside the intake that was its only caller. Both left when
 * `applyLessonResultEvents` started reading bodies through `attempt-intake.js`, which carries its
 * own verbatim copy. The canonical text a test can pin that copy to now lives in
 * `_legacy-intake-fixture.js` rather than as a declaration here that nothing calls.
 */

/*
 * `normalizeLessonResultAttemptEvent` stood here until `applyLessonResultEvents` started reading
 * bodies through `attempt-intake.js`. Its device-scored path lives on there check for check and
 * message for message; the pre-swap text is kept as the frozen reference in
 * `attempt-intake.test.js`, which still runs both over the same fixtures.
 *
 * `normalizeLessonAnswers` above is deliberately left in place: `attempt-intake.js` copies it
 * verbatim and `attempt-intake.test.js` compares the two source texts, so this declaration is what
 * that copy is pinned to.
 */

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


/**
 * The six activity ratings, as the legacy profile kept them.
 *
 * Three of them have a source in this app — questions put, questions got right, lessons finished.
 * The other three counted time in a quiz, time in chat and sms points, and nothing here measures
 * any of that yet; they stay absent rather than being written as invented zeroes.
 */
function writeActivityRatingsDelta(transaction, uid, ratings, now) {
  transaction.set(
    db.collection("users").doc(uid),
    clean({
      uid,
      updatedAtMs: now,
      ratingCountQuestions: firestoreTypes.FieldValue.increment(ratings.questions),
      ratingCountTrueQuestion: firestoreTypes.FieldValue.increment(ratings.correct),
      ratingQuiz: firestoreTypes.FieldValue.increment(ratings.quizzes),
    }),
    {merge: true},
  );
}

function writeUserProgressDelta(transaction, uid, delta, now) {
  const userRef = db.collection("users").doc(uid);
  transaction.set(
    userRef,
    clean({
      uid,
      updatedAtMs: now,
      pointsSkill: firestoreTypes.FieldValue.increment(delta.skillPoints),
      skillPoints: firestoreTypes.FieldValue.increment(delta.skillPoints),
      pointsNolics: firestoreTypes.FieldValue.increment(delta.nolics),
      nolics: firestoreTypes.FieldValue.increment(delta.nolics),
    }),
    {merge: true},
  );
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
    questionScores: listMaps(data.questionScores).map((item) => ({
      questionId: stringValue(item.questionId),
      score: numberValue(item.score, null),
    })),
    language: nullableString(data.language),
    targetReviewId: nullableString(data.targetReviewId),
    translatedQuestions: listMaps(data.translatedQuestions).map((item) => normalizeQuestion(item)),
    segmentResults: listMaps(data.segmentResults).map(normalizeSegmentDecision),
  };
}

/**
 * Сегментное решение перевода — явное: correct/error; отсутствие решения — unchecked, и оно
 * никогда не хранится как одобрение. Молчаливая approved-булева вида «accepted: true» без
 * решения проводом запрещена: именно она позволяла сегменту пройти, пока ревьюер молчал.
 */
function normalizeSegmentDecision(item) {
  const questionId = stringValue(item.questionId);
  const segmentKey = stringValue(item.segmentKey);
  const raw = item.decision === null || item.decision === undefined ? null : stringValue(item.decision);
  if (raw === null) {
    throw new HttpsError(
      "invalid-argument",
      `segmentResults[${questionId}/${segmentKey}] requires an explicit decision "correct" or "error"`,
    );
  }
  if (raw !== "correct" && raw !== "error") {
    throw new HttpsError("invalid-argument", `segmentResults[${questionId}/${segmentKey}] decision must be "correct" or "error"`);
  }
  return {questionId, segmentKey, decision: raw, accepted: raw === "correct"};
}

/**
 * Вопросные оценки TESTING: покрытие ровно вопросного состава задания, оценки в 1..3.
 *
 * Один независимый ревью на вопрос — это состав, названный заданием: не хватает вопроса — ревью
 * неполно; лишний или чужой id — провод собран мимо задания. Порог здесь не проверяется:
 * низкая оценка — валидное решение, а не ошибка провода.
 */
function validatedTestingQuestionScores(questionScores, task) {
  const expected = new Set((task.questions || []).map((question) => question.id));
  if (expected.size === 0) {
    throw new HttpsError("failed-precondition", "Assignment carries no questions to test");
  }
  const seen = new Set();
  for (const entry of questionScores) {
    if (!entry.questionId) {
      throw new HttpsError("invalid-argument", "questionScores entry requires questionId");
    }
    if (entry.score === null || entry.score === undefined || entry.score < 1 || entry.score > 3 || !Number.isInteger(entry.score)) {
      throw new HttpsError("invalid-argument", `questionScores[${entry.questionId}] score must be an integer in 1..3`);
    }
    if (!expected.has(entry.questionId)) {
      throw new HttpsError("invalid-argument", `questionScores names unknown question ${entry.questionId}`);
    }
    if (seen.has(entry.questionId)) {
      throw new HttpsError("invalid-argument", `questionScores duplicates question ${entry.questionId}`);
    }
    seen.add(entry.questionId);
  }
  const missing = Array.from(expected).filter((id) => !seen.has(id));
  if (missing.length > 0) {
    throw new HttpsError("invalid-argument", `questionScores must cover every question, missing: ${missing.join(", ")}`);
  }
  return questionScores.map((entry) => ({questionId: entry.questionId, score: entry.score}));
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
    submissionId: nullableString(data.submissionId),
    lessonId: stringValue(data.lessonId, fallbackLessonId),
    kind: stringValue(data.kind),
    reviewerUid: stringValue(data.reviewerUid),
    reviewerLevelAtSubmit: numberValue(data.reviewerLevelAtSubmit, 0),
    score: data.score === null || data.score === undefined ? null : numberValue(data.score, null),
    questionScores: listMaps(data.questionScores).map((item) => ({
      questionId: stringValue(item.questionId),
      score: numberValue(item.score, null),
    })),
    language: nullableString(data.language),
    targetReviewId: nullableString(data.targetReviewId),
    createdAtMs: numberValue(data.createdAtMs, 0),
    acceptedByServer: Boolean(data.acceptedByServer),
    segmentResults: listMaps(data.segmentResults).map((item) => ({
      questionId: stringValue(item.questionId),
      segmentKey: stringValue(item.segmentKey),
      decision: stringValue(item.decision, item.accepted ? "correct" : "error"),
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
    questionOutcomes: listMaps(review && review.questionOutcomes).map((item) => ({
      questionId: stringValue(item.questionId),
      testingScore: nullableNumber(item.testingScore),
      passed: Boolean(item.passed),
    })),
    isLogicReviewed: Boolean(review && review.isLogicReviewed),
    logicScore: nullableNumber(review && review.logicScore),
    isTranslationReviewed: Boolean(review && review.isTranslationReviewed),
    translationScore:
      review && review.translationScore !== null && review.translationScore !== undefined
        ? numberValue(review.translationScore, null)
        : null,
    translatedLanguages:
      Object.keys(translated).length > 0 ? translated : (fallbackLanguages || {}),
    appliedConfigRevision: nullableNumber(review && review.appliedConfigRevision),
    assignmentGeneration: nullableNumber(review && review.assignmentGeneration),
  };
}

function checksToCallableMap(checks) {
  return {
    isTested: Boolean(checks.isTested),
    testingScore: checks.testingScore === undefined ? null : checks.testingScore,
    questionOutcomes: (checks.questionOutcomes || []).map((outcome) => ({
      questionId: outcome.questionId,
      testingScore: outcome.testingScore === undefined ? null : outcome.testingScore,
      passed: Boolean(outcome.passed),
    })),
    isLogicReviewed: Boolean(checks.isLogicReviewed),
    logicScore: checks.logicScore === undefined ? null : checks.logicScore,
    isTranslationReviewed: Boolean(checks.isTranslationReviewed),
    translationScore: checks.translationScore === undefined ? null : checks.translationScore,
    translatedLanguages: checks.translatedLanguages || {},
    appliedConfigRevision:
      checks.appliedConfigRevision === undefined ? null : checks.appliedConfigRevision,
    assignmentGeneration:
      checks.assignmentGeneration === undefined ? null : checks.assignmentGeneration,
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
    submissionId: record.submissionId,
    lessonId: record.lessonId,
    kind: record.kind,
    reviewerUid: record.reviewerUid,
    reviewerLevelAtSubmit: record.reviewerLevelAtSubmit,
    score: record.score,
    questionScores: (record.questionScores || []).map((item) => ({
      questionId: item.questionId,
      score: item.score,
    })),
    language: record.language,
    targetReviewId: record.targetReviewId,
    createdAtMs: record.createdAtMs,
    acceptedByServer: record.acceptedByServer,
    segmentResults: (record.segmentResults || []).map((item) => ({
      questionId: item.questionId,
      segmentKey: item.segmentKey,
      decision: item.decision,
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

/**
 * A stage clears when its score reaches the pass mark. Scores run 1..3 and the mark comes from
 * `configs/arena_review.testingPassScore` (default 2), so the lowest score is a refusal rather
 * than an approval, and the owner can raise the bar without a deploy.
 *
 * Until this existed, publication asked only whether a score was PRESENT — a reviewer scoring 1
 * published the quest exactly as a reviewer scoring 3 did, which made the whole review stage
 * decorative. A score below the mark leaves the lesson UNDER_REVIEW indefinitely; raising it
 * publishes without any other action, and the reject action below is the way to send it back.
 */
function stagePassed(score, config) {
  const threshold = numberValue(config && config.testingPassScore, DEFAULT_TESTING_PASS_SCORE);
  return score !== null && score !== undefined && score >= threshold;
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
  const economy = await readEconomyConstantsDocument();
  const userRef = db.collection("users").doc(uid);
  const profileRef = db.collection("profiles").doc(uid);
  return runEconomyTransaction(async (transaction) => {
    const userSnapshot = await transaction.get(userRef);
    const profileSnapshot = await transaction.get(profileRef);
    // Регистрация по ссылке (история 5.3): только при создании аккаунта, чтение пригласившего —
    // до первой записи транзакции. Мёртвая ссылка даёт аккаунт без поля, испорченная — отказ.
    const referralInput = userSnapshot.exists || options.referredByUid === undefined ? null :
      referralRegistration({uid, referredByUid: options.referredByUid});
    const inviterSnapshot = referralInput ?
      await transaction.get(db.collection("users").doc(referralInput)) : null;
    const referral = referralInput ? referralFields({uid, referredByUid: referralInput,
      inviterExists: inviterSnapshot ? inviterSnapshot.exists : false, now: options.now}) : {};
    const existingUser = userSnapshot.exists ? userSnapshot.data() || {} : {};
    const premiumPlan = await advancePremiumInTransaction(transaction, uid, existingUser, economy, options.now);
    if (!premiumPlan.caughtUp) return writePremiumCheckpoint(transaction, uid, premiumPlan);
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
      systemAssigned: !options.allowNicknameUpgrade,
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
    // Offline claims must reconcile against the saved anchor before an online VISIT changes it.
    // Reading/updating a profile is not an extra daily box operation.
    const boxState = readBoxState(existingUser);
    const publicProfile = {
      ...premiumPlan.userPatch,
      // Прямая ссылка приглашающего — только у нового аккаунта; существующий не переписывается.
      ...referral,
      reservedNolics: nonNegativeInteger(existingUser.reservedNolics, 0),
      reservedLifePoints: nonNegativeInteger(existingUser.reservedLifePoints, 0),
      reservedPlasmaPoints: nonNegativeInteger(existingUser.reservedPlasmaPoints, 0),
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
      // Слоты и заряд в них — раздельно, как у обычного заряда: без этих двух полей плазме нечего
      // восстанавливать.
      gold: numberValue(existingUser.gold, 0),
      pointsSkill: numberValue(existingUser.pointsSkill, 0),
      boxCount: boxState.boxCount,
      boxStreakDays: boxState.boxStreakDays,
      nextBoxAtMs: boxState.nextBoxAtMs,
      nextBonusBoxAtMs: nonNegativeInteger(existingUser.nextBonusBoxAtMs, 0),
      trophies: trophyList(existingUser.trophies),
      ownedLogos: stringArray(existingUser.ownedLogos),
      ratingCountQuestions: numberValue(existingUser.ratingCountQuestions, 0),
      ratingCountTrueQuestion: numberValue(existingUser.ratingCountTrueQuestion, 0),
      ratingTimeInQuiz: numberValue(existingUser.ratingTimeInQuiz, 0),
      ratingTimeInChat: numberValue(existingUser.ratingTimeInChat, 0),
      ratingSmsPoints: numberValue(existingUser.ratingSmsPoints, 0),
      ratingQuiz: numberValue(existingUser.ratingQuiz, 0),
      realName: nullableString(existingUser.realName),
      birthday: nullableString(existingUser.birthday),
      city: nullableString(existingUser.city),
      telegram: nullableString(existingUser.telegram),
      verifiedAtMs: numberValue(existingUser.verifiedAtMs, 0),
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
    writePremiumEconomy({db, transaction, uid, plan: premiumPlan});
    transaction.set(
      nicknameClaim.ref,
      {
        uid,
        nickname,
        canonical: nicknameClaim.canonical,
        generated: Boolean(nicknameClaim.generated),
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
    return {blockedWords: [], blockedSymbols: [], ...nicknamePricing(null)};
  }
  const data = snapshot.data() || {};
  return {
    ...nicknamePricing(data),
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
      // Switching away from a name no longer surrenders it: one account holds many names and only
      // one of them is active.
      //
      // "Generated" means the system handed the name over rather than the person choosing it, and
      // that is decided by which path we came in on, not by which candidate won. Every automatic
      // path — first sign-in, opening a box — carries a name along without anybody picking it, so
      // those must not spend the one free choice everybody gets.
      // A name is stamped once, when it is first taken, and never again.
      //
      // Bootstrapping the account re-resolves whichever name is being worn, and it arrives on the
      // system-assigned path — so re-deriving the flag here would relabel a name the player chose
      // as one handed to them. That is not cosmetic: the flag is what decides whether the one free
      // choice has been spent, so a name that keeps being relabelled makes every further name free.
      const alreadyOurs = snapshot.exists && ownerUid === options.uid;
      return {
        ...validated,
        ref,
        snapshot,
        generated: alreadyOurs
          ? Boolean((snapshot.data() || {}).generated)
          : Boolean(options.systemAssigned) || candidate !== options.requestedNickname,
      };
    }

    if (!options.allowGeneratedFallback) {
      throw new HttpsError("already-exists", "Nickname is already taken");
    }
  }

  if (lastPolicyError) throw lastPolicyError;
  throw new HttpsError("already-exists", "Could not allocate a unique nickname");
}


/** The saving path's view of the shared rules: same verdict, raised instead of returned. */
function validateNicknameOrThrow(value, policy) {
  const described = describeNickname(value, policy);
  if (described.ok) return {nickname: described.nickname, canonical: described.canonical};
  throw new HttpsError("invalid-argument", NICKNAME_REASON_MESSAGES[described.reason] ||
    "Nickname is not allowed");
}

const NICKNAME_REASON_MESSAGES = {
  "too-short": "Nickname must contain 3..24 supported characters",
  "too-long": "Nickname must contain 3..24 supported characters",
  "unsupported-characters": "Nickname must contain 3..24 supported characters",
  "blocked-symbol": "Nickname contains a blocked symbol",
  "blocked-word": "Nickname contains a blocked word",
};

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

/**
 * Профиль наружу.
 *
 * Таблица настроек — параметром: бак и темп восстановления в ответе обязаны совпадать с теми, по
 * которым сервер спишет попытку.
 */
function profileResponse(result, economy) {
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
    // upsertUserProfile already committed both clocks and premium lots together. This response
    // must not run a second, timestamp-only regeneration that loses the work remainder.
    ...lifePointsSnapshot(user, Date.now(), economy),
    ...plasmaSnapshot(user, Date.now(), economy),
    boxCount: numberValue(user.boxCount, 0),
    boxStreakDays: numberValue(user.boxStreakDays, 0),
    nextBoxAtMs: numberValue(user.nextBoxAtMs, 0),
    nextBonusBoxAtMs: numberValue(user.nextBonusBoxAtMs, 0),
    premiumUntilMs: numberValue(user.premiumUntilMs, 0),
    hasPremium: numberValue(user.premiumUntilMs, 0) > Date.now(),
    reservedNolics: nonNegativeInteger(user.reservedNolics, 0),
    reservedLifePoints: nonNegativeInteger(user.reservedLifePoints, 0),
    reservedPlasmaPoints: nonNegativeInteger(user.reservedPlasmaPoints, 0),
    trophies: trophyList(user.trophies),
    // What somebody submitted about themselves, readable only by them, plus when they were
    // confirmed. The tick itself is a trophy; this is the date behind it.
    realName: nullableString(user.realName),
    birthday: nullableString(user.birthday),
    city: nullableString(user.city),
    telegram: nullableString(user.telegram),
    verifiedAtMs: numberValue(user.verifiedAtMs, 0),
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
/**
 * Снимок плазмы.
 *
 * Плазма устроена как обычный заряд и хранится так же: `goldHearts` — сколько слотов куплено,
 * `plasmaPoints` — сколько в них накоплено. До сих пор счётчика очков и отметки времени у неё не
 * было вовсе, и «плазма восстанавливается за сутки» существовало только на бумаге: `goldHearts`
 * означал сразу и вместимость, и заряд, а значит восстанавливать было нечего.
 *
 * Аккаунт, у которого поля ещё нет, начинает полным — как и с обычным зарядом: поле появилось
 * позже него, и наказывать за это сутками ожидания не за что.
 */
function plasmaSnapshot(user, nowMs, economy) {
  const rules = economy && economy.plasma ? economy.plasma : readEconomyConstants(null).plasma;
  const slots = Math.min(nonNegativeInteger(user.goldHearts, 0), rules.maxOwned);
  const ceiling = slots * POINTS_PER_CHARGE;
  return {
    plasmaPoints: numberValue(user.plasmaPoints, Math.max(0, ceiling - nonNegativeInteger(user.reservedPlasmaPoints, 0))),
    maxPlasmaPoints: ceiling,
    plasmaPointsUpdatedAtMs: numberValue(user.plasmaPointsUpdatedAtMs, nowMs),
    plasmaRegenRemainderMs: nonNegativeInteger(user.plasmaRegenRemainderMs, 0),
  };
}

/**
 * Снимок бака, каким его показывает профиль.
 *
 * Потолок и темп — из таблицы, как и в списании: иначе профиль рисовал бы одну шкалу, а попытка
 * списывала бы по другой. `lessonAttemptLifeCost` больше не отдаётся: плоской цены попытки нет —
 * она зависит от вида активности, и вид называет сервер (CAP-16). Клиент это поле не читал.
 */
function lifePointsSnapshot(user, nowMs, economy) {
  const rules = economy && economy.standard ? economy.standard : readEconomyConstants(null).standard;
  const ceiling = maxLifePoints(Math.min(nonNegativeInteger(user.standardHearts, MAX_STANDARD_HEARTS), rules.maxOwned));
  return {
    lifePoints: numberValue(user.lifePoints, Math.max(0, ceiling - nonNegativeInteger(user.reservedLifePoints, 0))),
    maxLifePoints: ceiling,
    lifePointsUpdatedAtMs: numberValue(user.lifePointsUpdatedAtMs, nowMs),
    lifeRegenRemainderMs: nonNegativeInteger(user.lifeRegenRemainderMs, 0),
  };
}

/**
 * Баланс, как он лежит в документе.
 *
 * Потолок здесь **не применяется**. Раньше прочитанное зажималось `Math.min` — и это значило, что
 * понижение потолка молча отбирает у игрока лишнее: он купил пять слотов, потолок опустили до трёх,
 * и два исчезли на первом же чтении, без покупки, без объявления и без возврата.
 *
 * Потолок ограничивает пополнение — покупку и восстановление, — а не владение. Аккаунт, у которого
 * больше потолка, просто не растёт, пока не окажется под ним сам. Сегодня это ничего не меняет
 * (ничей баланс потолка не превышает), но таблица настроек затем и делается серверной, чтобы
 * потолки двигались, — и ловушку надо убрать до того, как они начнут двигаться, а не после.
 */
function readEconomyBalance(user) {
  return {
    hasPremium: numberValue(user.premiumUntilMs, 0) > Date.now(),
    streakDays: nonNegativeInteger(user.streakDays, 0),
    stars: nonNegativeInteger(user.stars, 0),
    nolics: nonNegativeInteger(user.pointsNolics, numberValue(user.nolics, 0)),
    standardHearts: nonNegativeInteger(user.standardHearts, MAX_STANDARD_HEARTS),
    goldHearts: nonNegativeInteger(user.goldHearts, 0),
    plasmaPoints: nonNegativeInteger(user.plasmaPoints, nonNegativeInteger(user.goldHearts, 0) * POINTS_PER_CHARGE),
    gold: nonNegativeInteger(user.gold, 0),
    // Part of the balance because the client applies what it is handed back wholesale. Leave the
    // set out and every purchase answers "you own no lessons", which the client then believes.
    lessonUnlocks: readUnlocks(user.lessonUnlocks),
  };
}

function applyShopPurchaseToBalance(balance, itemId, economy) {
  switch (itemId) {
    case SHOP_ITEM_STANDARD_HEART_SLOT:
      return buyStandardHeart(balance, economy.standard);
    case SHOP_ITEM_GOLD_HEART:
      return buyGoldHeart(balance, economy.plasma);
    default:
      throw new HttpsError("invalid-argument", `Unsupported shop item ${itemId}`);
  }
}

/**
 * Слот обычного заряда — по лестнице и потолку из таблицы.
 *
 * Понижённый потолок не отбирает купленное (`readEconomyBalance` не зажимает), но и купить сверх
 * него не даёт — иначе понижение не значило бы ничего.
 */
function buyStandardHeart(balance, rules) {
  if (balance.standardHearts >= rules.maxOwned) {
    throw new HttpsError("failed-precondition", "Standard hearts are already full");
  }
  const price = slotPrice(rules, balance.standardHearts);
  if (balance.nolics < price) {
    throw new HttpsError("failed-precondition", "Not enough nolics");
  }
  return {
    ...balance,
    nolics: balance.nolics - price,
    standardHearts: balance.standardHearts + 1,
  };
}

/**
 * Плазменный заряд — по лестнице золотом `1, 2, 3` из таблицы, а не по плоским десяти.
 *
 * Витрина уже показывала лестницу из таблицы, а сервер списывал десять и запирал на одном:
 * игрок видел «1 золота», а платил десять.
 */
function buyGoldHeart(balance, rules) {
  if (balance.goldHearts >= rules.maxOwned) {
    throw new HttpsError("failed-precondition", "Gold hearts are already full");
  }
  const price = slotPrice(rules, balance.goldHearts);
  if (balance.gold < price) {
    throw new HttpsError("failed-precondition", "Not enough gold");
  }
  // Купленный слот приходит заряженным. Обычный заряд покупается за нолики и ждёт наполнения час;
  // плазма стоит золота и наполняется сутки, и отдать за деньги пустой слот на сутки — это продать
  // ожидание. Ёмкость постоянна, поэтому платят один раз, а ждут только за следующий заряд.
  return {
    ...balance,
    gold: balance.gold - price,
    goldHearts: balance.goldHearts + 1,
    plasmaPoints: nonNegativeInteger(balance.plasmaPoints, 0) + POINTS_PER_CHARGE,
  };
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



function nicknameClaimId(canonicalNicknameValue) {
  return hashHex(canonicalNicknameValue);
}

function privateCatalogPath(ownerUid, catalogId) {
  return `private/${ownerUid}/catalogs/${catalogId}`;
}

function privateQuestPath(ownerUid, catalogId, questId) {
  return `${privateCatalogPath(ownerUid, catalogId)}/quests/${questId}`;
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

const ADMIN_REVIEW_LESSONS = "admin/review/lessons";

function adminLessonPath(lessonId) {
  return `${ADMIN_REVIEW_LESSONS}/${lessonId}`;
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
  const firestore = firestoreTypes;
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
