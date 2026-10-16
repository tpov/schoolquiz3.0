"use strict";

const {randomBytes} = require("node:crypto");
const policy = require("./tournament-policy");
const {validateRankingVersion} = require("./tournament-qualification-version");
const {canonicalTournamentJson: canonical, tournamentDigest: digest,
  normalizeTournamentBank, tournamentFormBundle} = require("./tournament-bank");
const {readPremiumEconomy, planPremiumEconomy, writePremiumEconomy, PREMIUM_CATCHUP_PASSES} = require("./premium-economy");
const {ECONOMY_CONSTANTS_DOC, readEconomyConstants} = require("./economy-constants");
const {CURRENCY, REASON, LEDGER_PATH, ledgerEntry} = require("./currency-ledger");

// All these collections are server-only, including redacted forms BEFORE their session starts.
// A callable returns an allowlisted session view, never an entire event/private snapshot.
const TOURNAMENT_PATHS = Object.freeze({events: "course_tournament_events", operations: "course_tournament_operations",
  sessions: "course_tournament_sessions", privateForms: "course_tournament_keys", funding: "course_tournament_funding",
  links: "course_tournament_links", downloads: "course_tournament_downloads"});
const KINDS = Object.freeze(["CREATE", "START_QUALIFICATION", "CLOSE_QUALIFICATION", "PROPOSE", "CONFIRM", "READY", "ADVANCE_MATCH"]);
const MAX_DOCUMENT_JSON_BYTES = 800_000;
const MAX_TRANSACTION_JSON_BYTES = 6_000_000;
const MAX_STAGED_WRITES = 80; // Up to 15 forms + 15 private keys; premium writes have their own bounded budget.

function fail(code, message) { const error = new Error(message); error.code = code; throw error; }
function exact(value, label, min = 0) {
  if (!Number.isSafeInteger(value) || value < min) fail("INVALID_ARGUMENT", `${label} must be an exact integer >= ${min}`);
  return value;
}
function add(a, b) { return exact(a + b, "balance/timestamp"); }
function uidValue(value) {
  if (typeof value !== "string" || !value || value.length > 128 || value !== value.trim() || value.includes("/")) fail("INVALID_ARGUMENT", "Authenticated uid required");
  return value;
}
function idValue(value, label) {
  if (typeof value !== "string" || !/^[A-Za-z0-9_-]{16,128}$/.test(value)) fail("INVALID_ARGUMENT", `${label} must be a persistent 16..128 character id`);
  return value;
}
function nonblank(value, label) {
  if (typeof value !== "string" || !value.trim() || value.length > 512) fail("INVALID_ARGUMENT", `${label} required`);
  return value;
}
function only(object, names) {
  if (!object || typeof object !== "object" || Array.isArray(object) || Object.keys(object).some((key) => !names.includes(key))) {
    fail("INVALID_ARGUMENT", "Unsupported request fields (scores, keys and caller identity are never client inputs)");
  }
}
function normalizeTournamentRequest(uid, payload) {
  uidValue(uid);
  if (!payload || !KINDS.includes(payload.kind)) fail("INVALID_ARGUMENT", "Unknown tournament operation");
  if (payload.ownerUid !== uid) fail("PERMISSION_DENIED", "Operation belongs to another account");
  const common = ["kind", "ownerUid", "operationId", "eventId"];
  const extras = {CREATE: ["source", "startsAtMs", "prizes", "fundedBoxes"],
    // linkToken is the out-of-band organizer-distributed competition link (UX-O4/access);
    // presented only at START, never stored, and never a substitute for the server's records.
    START_QUALIFICATION: ["roundNumber", "attemptId", "linkToken"],
    CLOSE_QUALIFICATION: [], PROPOSE: ["matchId", "slotStartsAtMs"], CONFIRM: ["matchId", "slotStartsAtMs"],
    READY: ["matchId"], ADVANCE_MATCH: ["matchId"]}[payload.kind];
  only(payload, [...common, ...extras]);
  const request = {kind: payload.kind, ownerUid: uid, operationId: idValue(payload.operationId, "operationId"), eventId: idValue(payload.eventId, "eventId")};
  if (request.kind === "CREATE") {
    only(payload.source, ["kind", "id", "snapshotId"]);
    if (!["COURSE", "ARENA_LESSON"].includes(payload.source.kind)) fail("INVALID_ARGUMENT", "Existing reviewed source required");
    request.source = {kind: payload.source.kind, id: nonblank(payload.source.id, "source id"), snapshotId: nonblank(payload.source.snapshotId, "source snapshot")};
    request.startsAtMs = exact(payload.startsAtMs, "startsAtMs");
    const funding = policy.validatePrizeSchedule(payload.prizes, payload.fundedBoxes);
    request.prizes = funding.prizes; request.fundedBoxes = funding.fundedBoxes;
  }
  if (request.kind === "START_QUALIFICATION") {
    request.roundNumber = exact(payload.roundNumber, "roundNumber", 1);
    if (request.roundNumber > 3) fail("INVALID_ARGUMENT", "Only three qualification forms");
    request.attemptId = idValue(payload.attemptId, "attemptId");
    if (payload.linkToken !== undefined &&
        (typeof payload.linkToken !== "string" || !/^[a-f0-9]{48}$/.test(payload.linkToken))) {
      fail("INVALID_ARGUMENT", "Competition link token required");
    }
    if (payload.linkToken !== undefined) request.linkToken = payload.linkToken;
  }
  if (extras.includes("matchId")) request.matchId = nonblank(payload.matchId, "matchId");
  if (extras.includes("slotStartsAtMs")) request.slotStartsAtMs = exact(payload.slotStartsAtMs, "slotStartsAtMs");
  return request;
}
function eventPath(id) { return `${TOURNAMENT_PATHS.events}/${id}`; }
function formPath(eventId, formId) { return `${eventPath(eventId)}/forms/${digest(formId)}`; }
function privatePath(eventId, formId) { return `${TOURNAMENT_PATHS.privateForms}/${eventId}/forms/${digest(formId)}`; }
function qualificationSessionId(attemptId) { return `q_${digest(attemptId)}`; }
function matchSessionId(eventId, matchId, uid) { return `m_${digest([eventId, matchId, uid])}`; }
function sessionPath(id) { return `${TOURNAMENT_PATHS.sessions}/${id}`; }
function sessionView(session) {
  return Object.fromEntries(["schemaVersion", "sessionId", "uid", "eventId", "kind", "roundNumber", "matchId", "formId", "formFingerprint",
    "startedAtMs", "endsAtMs", "status", "questionIndex", "questionStartedAtMs", "questionEndsAtMs"]
    .filter((key) => session[key] !== undefined).map((key) => [key, session[key]]));
}
function eventView(event) {
  return {schemaVersion: 1, eventId: event.eventId, organizerUid: event.organizerUid, status: event.status,
    source: {kind: event.source.kind, id: event.source.id, snapshotId: event.source.snapshotId},
    qualification: event.qualification, prizes: event.prizes, escrow: event.escrow,
    ...(event.progress ? {progress: event.progress} : {}), ...(event.cancelReason ? {cancelReason: event.cancelReason} : {})};
}
function makeSession({sessionId, uid, event, form, kind, startedAtMs, endsAtMs, roundNumber, matchId}) {
  if (!form.questions.length || endsAtMs <= startedAtMs) fail("FAILED_PRECONDITION", "No valid session time remains");
  return {schemaVersion: 1, sessionId, uid, eventId: event.eventId, kind,
    ...(roundNumber ? {roundNumber} : {}), ...(matchId ? {matchId} : {}),
    formId: form.formId, formFingerprint: form.fingerprint, status: "STARTED", startedAtMs, endsAtMs,
    questionIndex: 0, questionStartedAtMs: startedAtMs, questionEndsAtMs: Math.min(add(startedAtMs, form.questions[0].maxDurationMs), endsAtMs)};
}
function assertEscrow(event) {
  const e = event.escrow;
  if (!e || exact(e.reservedBoxes, "reservedBoxes") !== event.fundedBoxes ||
      add(add(exact(e.remainingBoxes, "remainingBoxes"), exact(e.paidBoxes, "paidBoxes")), exact(e.returnedBoxes, "returnedBoxes")) !== event.fundedBoxes) {
    fail("INCONSISTENT_ESCROW", "Reserved, paid and returned boxes do not reconcile");
  }
}
function stageBundle(stage, eventId, form, bank) {
  const bundle = tournamentFormBundle(form, bank);
  stage(formPath(eventId, form.formId), bundle.publicForm, true);
  stage(privatePath(eventId, form.formId), bundle.privateForm, true);
}
function loader(deps, name) {
  if (typeof deps?.[name] !== "function") fail("FAILED_PRECONDITION", `Trusted ${name} is not configured`);
  return deps[name];
}
function rankingGate(snapshot, event) {
  if (!snapshot || snapshot.complete !== true) fail("UNAVAILABLE", "Verified qualification freeze is not complete; retry without cancelling");
  const expected = digest(event.qualificationForms.map(({formId, fingerprint}) => ({formId, fingerprint})));
  let provenance;
  try { provenance = validateRankingVersion(snapshot, event); }
  catch { fail("INVALID_RANKING", "Unbound calculation version"); }
  if (snapshot.eventId !== event.eventId || snapshot.endsAtMs !== event.qualification.endsAtMs || snapshot.formsFingerprint !== expected ||
      !Array.isArray(snapshot.ranking) || snapshot.ranking.length > 16 || !Number.isSafeInteger(snapshot.totalRanked) ||
      snapshot.totalRanked < 0 || snapshot.ranking.length !== Math.min(16, snapshot.totalRanked) ||
      !/^[a-f0-9]{64}$/.test(snapshot.verifiedRunsFingerprint || "")) fail("INVALID_RANKING", "Unbound, truncated or wrong-algorithm ranking freeze");
  nonblank(snapshot.snapshotId, "ranking snapshot id");
  exact(snapshot.verifiedRunCount, "verifiedRunCount");
  const entrants = policy.selectQualifiers(snapshot.ranking);
  entrants.forEach((row, i) => { uidValue(row.uid); if (row.authoritativePlace !== i + 1) fail("INVALID_RANKING", "Top ordinals must be complete and contiguous"); });
  return {snapshotId: snapshot.snapshotId, algorithmVersion: snapshot.algorithmVersion,
    verifiedRunsFingerprint: snapshot.verifiedRunsFingerprint, verifiedRunCount: snapshot.verifiedRunCount, totalRanked: snapshot.totalRanked, ...provenance};
}

/** Read and plan, with writes staged in memory only. Injected loaders must perform transaction
 * reads, never writes or live network calls. uid and now are trusted controller inputs.
 */
async function readPlanTournamentOperation({db, transaction, uid, payload, now, dependencies = {}, serverSeed}) {
  const request = normalizeTournamentRequest(uid, payload);
  exact(now, "server time");
  const operationKey = digest(request.operationId);
  const fingerprint = digest(request);
  const opPath = `${TOURNAMENT_PATHS.operations}/${operationKey}`;
  const reads = new Map(); const writes = new Map(); const effects = [];
  async function read(path) {
    if (!reads.has(path)) { const snap = await transaction.get(db.doc(path)); reads.set(path, snap.exists ? snap.data() : null); }
    return structuredClone(reads.get(path));
  }
  function stage(path, data, create = false) {
    const prior = writes.get(path);
    if (prior?.create && !create) create = true;
    writes.set(path, {path, data: structuredClone(data), create});
  }
  const receipt = await read(opPath);
  if (receipt) {
    if (receipt.uid !== uid) fail("PERMISSION_DENIED", "Operation is owned by another account");
    if (receipt.schemaVersion !== 1 || receipt.operationId !== request.operationId || receipt.fingerprint !== fingerprint || !receipt.response) {
      fail("CONFLICTING_REPLAY", "Operation id is already bound to another request");
    }
    return {caughtUp: true, replayed: true, response: receipt.response, writes: [], accounts: []};
  }
  let event = await read(eventPath(request.eventId));
  let at = event ? Math.max(now, exact(event.lastMutationAtMs, "event clock")) : now;
  let response;
  function effect(owner, delta, sourceId, kind, extra = {}) {
    uidValue(owner);
    if (!Number.isSafeInteger(delta)) fail("INVALID_ARGUMENT", "Box delta overflow");
    // Zero transfers need no user write, but the escrow header still records exact settlement.
    if (delta) effects.push({uid: owner, delta, sourceId, kind, ...extra});
  }
  function finish(placements, status, reason) {
    assertEscrow(event);
    if (event.escrow.remainingBoxes !== event.fundedBoxes || event.escrow.paidBoxes || event.escrow.returnedBoxes) {
      fail("INCONSISTENT_ESCROW", "Only intact escrow can be settled");
    }
    const payout = policy.planPrizePayouts({eventId: event.eventId, prizes: event.prizes, fundedBoxes: event.fundedBoxes, placements});
    for (const row of payout.payouts) effect(row.uid, row.amount, row.payoutId, "PRIZE", {slot: row.slot, rank: row.rank});
    effect(event.organizerUid, payout.unusedReturnBoxes, payout.returnId, "UNUSED_RETURN");
    event.status = status;
    if (reason) event.cancelReason = reason;
    event.escrow = {reservedBoxes: event.fundedBoxes, remainingBoxes: 0, paidBoxes: payout.paidBoxes, returnedBoxes: payout.unusedReturnBoxes};
    event.settledAtMs = at;
  }
  async function trustedBank(sourceRef, owner) {
    const raw = await loader(dependencies, "loadBank")({db, transaction, requesterUid: owner, sourceRef: structuredClone(sourceRef), purpose: request.kind});
    return normalizeTournamentBank({bank: raw, requesterUid: owner, sourceRef});
  }

  if (request.kind === "CREATE") {
    if (event) fail("ALREADY_EXISTS", "Event id is permanent; reuse the original operation id");
    if (request.startsAtMs < at) fail("INVALID_ARGUMENT", "New qualification cannot start in the past");
    if (typeof serverSeed !== "string" || serverSeed.length < 32) fail("FAILED_PRECONDITION", "Server random seed required");
    const bank = await trustedBank(request.source, uid);
    event = {...policy.createEventPlan({eventId: request.eventId, source: bank.source, questions: bank.questions,
      startsAtMs: request.startsAtMs, prizes: request.prizes, fundedBoxes: request.fundedBoxes}),
      schemaVersion: 1, organizerUid: uid, status: "QUALIFICATION", serverSeed, privateBankFingerprint: bank.privateFingerprint,
      timingVersion: bank.timingVersion, createdAtMs: at, lastMutationAtMs: at,
      escrow: {reservedBoxes: request.fundedBoxes, remainingBoxes: request.fundedBoxes, paidBoxes: 0, returnedBoxes: 0}};
    event.qualificationForms = policy.qualificationForms({event, serverSeed});
    event.qualificationForms.forEach((form) => stageBundle(stage, event.eventId, form, bank));
    effect(uid, -event.fundedBoxes, JSON.stringify(["tournament", event.eventId, "reserve"]), "RESERVE");
    // UX-O4/access: the competition link token is minted once, here. Only its digest is stored
    // (on the event's link document); the token itself exists solely in this organizer's CREATE
    // response — receipt replay recovers it for the organizer only, and there is no rotation,
    // revocation or separate expiry: link validity IS the qualification window interval.
    const admissionToken = randomBytes(24).toString("hex");
    stage(`${TOURNAMENT_PATHS.links}/${event.eventId}`,
      {schemaVersion: 1, eventId: event.eventId, digest: digest(admissionToken), createdAtMs: at}, true);
    response = {status: "CREATED", event: eventView(event), admissionLink: {token: admissionToken}};
  } else {
    if (!event || event.schemaVersion !== 1) fail("NOT_FOUND", "Tournament event not found");
    assertEscrow(event);
    if (request.kind === "START_QUALIFICATION") {
      const entryPath = `${eventPath(event.eventId)}/entries/${digest([uid, request.roundNumber])}`;
      const id = qualificationSessionId(request.attemptId);
      const [entry, existingSession] = await Promise.all([read(entryPath), read(sessionPath(id))]);
      if (existingSession && (existingSession.uid !== uid || existingSession.eventId !== event.eventId ||
          existingSession.roundNumber !== request.roundNumber || existingSession.kind !== "QUALIFICATION")) {
        fail("CONFLICTING_REPLAY", "Attempt id belongs to another account, event or form");
      }
      const decision = policy.planQualificationAttempt({event, uid, roundNumber: request.roundNumber,
        attemptId: request.attemptId, nowMs: at, existingAttempts: entry ? [entry] : []});
      if (!decision.allowed) fail("FAILED_PRECONDITION", decision.reason);
      if (decision.replay) {
        if (!existingSession) fail("INCONSISTENT_SESSION", "Entry exists without its permanent session");
        response = {status: "SESSION", session: sessionView(existingSession)};
      } else {
        if (event.status !== "QUALIFICATION") fail("FAILED_PRECONDITION", "Qualification is closed");
        if (existingSession) fail("INCONSISTENT_SESSION", "Session exists without its entry");
        // UX-O4/access gate, evaluated inline in this same transaction (the only entry path):
        // the durable admission document is the receipt of the check. Basis facts come from
        // server-owned records only — the attested download record keyed by
        // (uid, event.source.id) and the event's stored link digest matched against the
        // presented token. Nothing here is readable by CLOSE_QUALIFICATION, ranking or the
        // bracket, so a link can never bypass selection and earned championship rights survive
        // any later link state.
        const admissionPath = `${eventPath(event.eventId)}/admissions/${digest(uid)}`;
        const admitted = await read(admissionPath);
        if (admitted) {
          if (admitted.schemaVersion !== 1 || admitted.eventId !== event.eventId || admitted.uid !== uid ||
              !["COURSE_DOWNLOAD", "LINK"].includes(admitted.basis)) {
            fail("INCONSISTENT_ADMISSION", "Stored admission requires repair");
          }
        } else {
          const downloadRecord = event.source.kind === "COURSE"
            ? await read(`${TOURNAMENT_PATHS.downloads}/${digest([uid, event.source.id])}`) : null;
          let linkTokenDigest = null;
          if (request.linkToken) {
            const presented = digest(request.linkToken);
            const link = await read(`${TOURNAMENT_PATHS.links}/${event.eventId}`);
            if (link && link.schemaVersion === 1 && link.eventId === event.eventId && link.digest === presented) {
              linkTokenDigest = presented;
            }
          }
          const admission = policy.planAdmission({event, uid, nowMs: at, downloadRecord, linkTokenDigest});
          if (!admission.allowed) fail("PERMISSION_DENIED", admission.reason);
          stage(admissionPath, {schemaVersion: 1, eventId: event.eventId, uid, basis: admission.basis, admittedAtMs: at}, true);
        }
        const form = event.qualificationForms[request.roundNumber - 1];
        // Qualification closes at its fixed cutoff, including a late-started timed-out session.
        const session = makeSession({sessionId: id, uid, event, form, kind: "QUALIFICATION", roundNumber: request.roundNumber,
          startedAtMs: at, endsAtMs: Math.min(add(at, form.maxDurationMs), event.qualification.endsAtMs)});
        stage(entryPath, {...decision.attempt, sessionId: id, formId: form.formId, formFingerprint: form.fingerprint}, true);
        stage(sessionPath(id), session, true);
        response = {status: "SESSION", session: sessionView(session)};
      }
    } else if (request.kind === "CLOSE_QUALIFICATION") {
      if (uid !== event.organizerUid) fail("PERMISSION_DENIED", "Only the organizer controller closes qualification");
      if (event.status !== "QUALIFICATION") response = {status: event.status, event: eventView(event)};
      else {
        if (at < event.qualification.endsAtMs) fail("FAILED_PRECONDITION", "Qualification is still open");
        const ranking = await loader(dependencies, "loadQualificationRanking")({db, transaction, eventId: event.eventId,
          forms: structuredClone(event.qualificationForms), endsAtMs: event.qualification.endsAtMs});
        event.qualificationRanking = rankingGate(ranking, event);
        const bracket = policy.buildBracket({event, ranking: ranking.ranking, serverSeed: event.serverSeed});
        event.bracket = bracket;
        event.outcomes = [];
        event.progress = policy.progressBracket({bracket});
        if (bracket.status === "CANCELLED") finish([], "CANCELLED", bracket.reason);
        else {
          const bank = await trustedBank(event.source, event.organizerUid);
          if (bank.poolFingerprint !== event.poolFingerprint || bank.privateFingerprint !== event.privateBankFingerprint) {
            fail("INVALID_BANK", "Immutable bank changed after funding; do not redraw or settle escrow");
          }
          bracket.rounds.flatMap((round) => round.matches).forEach((match) => stageBundle(stage, event.eventId, match.sharedForm, bank));
          event.status = "BRACKET";
        }
        response = {status: event.status, event: eventView(event)};
      }
    } else {
      if (event.status !== "BRACKET") {
        if (["FINISHED", "CANCELLED"].includes(event.status) && request.kind === "ADVANCE_MATCH" && uid === event.organizerUid) {
          response = {status: event.status, event: eventView(event)};
        } else fail("FAILED_PRECONDITION", "No active bracket");
      } else {
        const progress = policy.progressBracket({bracket: event.bracket, outcomes: event.outcomes});
        const pending = progress.pending.find((match) => match.matchId === request.matchId);
        if (!pending) fail("FAILED_PRECONDITION", "Match is terminal, empty or its feeders are not settled");
        const match = event.bracket.rounds.flatMap((round) => round.matches).find((item) => item.matchId === request.matchId);
        const participants = pending.entrants.map((row) => row.uid);
        if (!participants.includes(uid) && !(request.kind === "ADVANCE_MATCH" && uid === event.organizerUid)) {
          fail("PERMISSION_DENIED", "Authenticated user is not this match's participant");
        }
        const context = {...match, participantUids: participants, formMaxDurationMs: match.sharedForm.maxDurationMs};
        const meetingPath = `${eventPath(event.eventId)}/meetings/${digest(match.matchId)}`;
        const meeting = await read(meetingPath) || {schemaVersion: 1, matchId: match.matchId, firstProposals: [], agreement: null, readySignals: []};
        if (meeting.matchId !== match.matchId || meeting.schemaVersion !== 1) fail("INCONSISTENT_SESSION", "Meeting identity mismatch");
        if (request.kind === "PROPOSE") {
          if (meeting.agreement) fail("FAILED_PRECONDITION", "Agreed slot is immutable");
          const proposal = policy.planMeetingProposal({match: context, uid, slotStartsAtMs: request.slotStartsAtMs, nowMs: at});
          const path = `${meetingPath}/offers/${digest([request.slotStartsAtMs, uid])}`;
          const existing = await read(path);
          if (!existing) stage(path, proposal, true);
          if (!meeting.firstProposals.some((row) => row.uid === uid)) meeting.firstProposals.push(proposal);
        }
        if (request.kind === "CONFIRM") {
          if (meeting.agreement) {
            if (meeting.agreement.slotStartsAtMs !== request.slotStartsAtMs) fail("FAILED_PRECONDITION", "Agreed slot is immutable");
          } else {
            const slot = request.slotStartsAtMs;
            // Offers/confirmations live in immutable, directly addressed per-slot documents.
            // This preserves old valid offers without unbounded history inside the event document.
            const offers = (await Promise.all(participants.map((owner) => read(`${meetingPath}/offers/${digest([slot, owner])}`)))).filter(Boolean);
            const confirmations = (await Promise.all(participants.map((owner) => read(`${meetingPath}/confirmations/${digest([slot, owner])}`)))).filter(Boolean);
            if (!confirmations.some((row) => row.uid === uid)) confirmations.push({uid, slotStartsAtMs: slot, acceptedAtMs: at});
            const evaluated = policy.evaluateMeeting({match: context, proposals: offers, confirmations, nowMs: at});
            const ownPath = `${meetingPath}/confirmations/${digest([slot, uid])}`;
            if (!await read(ownPath)) stage(ownPath, confirmations.find((row) => row.uid === uid), true);
            if (evaluated.slotStartsAtMs !== undefined) meeting.agreement = {slotStartsAtMs: slot, proposals: offers, confirmations};
          }
        }
        if (request.kind === "READY") {
          if (!meeting.agreement || at < meeting.agreement.slotStartsAtMs || at >= add(meeting.agreement.slotStartsAtMs, policy.READY_WINDOW_MS)) {
            fail("FAILED_PRECONDITION", "Ready is accepted only inside the agreed one-hour window");
          }
          if (!meeting.readySignals.some((row) => row.uid === uid)) meeting.readySignals.push({uid, acceptedAtMs: at});
        }
        const evaluated = policy.evaluateMeeting({match: context,
          proposals: meeting.agreement ? meeting.agreement.proposals : meeting.firstProposals,
          confirmations: meeting.agreement ? meeting.agreement.confirmations : [], readySignals: meeting.readySignals, nowMs: at});
        let outcome = ["FORFEIT", "DOUBLE_OUT"].includes(evaluated.status) ? evaluated : null;
        let sessions = [];
        if (evaluated.status === "STARTED") {
          sessions = await Promise.all(participants.map(async (owner) => {
            const id = matchSessionId(event.eventId, match.matchId, owner);
            const expected = makeSession({sessionId: id, uid: owner, event, form: match.sharedForm, kind: "MATCH", matchId: match.matchId,
              startedAtMs: evaluated.startedAtMs, endsAtMs: evaluated.endsAtMs});
            const previous = await read(sessionPath(id));
            if (previous && ["sessionId", "uid", "eventId", "kind", "matchId", "formId", "formFingerprint", "startedAtMs", "endsAtMs"]
              .some((key) => previous[key] !== expected[key])) fail("INCONSISTENT_SESSION", "Match session clocks or identity changed");
            if (!previous) stage(sessionPath(id), expected, true);
            return previous || expected;
          }));
          if (request.kind === "ADVANCE_MATCH") {
            const resultSet = await loader(dependencies, "loadSettledMatchResults")({db, transaction, eventId: event.eventId,
              matchId: match.matchId, sessions: sessions.map(sessionView), nowMs: at});
            if (resultSet != null) {
              if (!Array.isArray(resultSet) || resultSet.length !== 2) fail("INVALID_RESULTS", "Both settled server results required");
              const verified = resultSet.map((row) => {
                const session = sessions.find((item) => item.uid === row.uid);
                if (!session || row.status !== "SETTLED" || row.sessionId !== session.sessionId || row.eventId !== event.eventId ||
                    row.matchId !== match.matchId || row.formFingerprint !== session.formFingerprint ||
                    typeof row.verificationVersion !== "string" || !row.verificationVersion ||
                    !Number.isSafeInteger(row.completedAtMs) || row.completedAtMs < session.startedAtMs || row.completedAtMs > Math.min(at, session.endsAtMs) ||
                    !Number.isSafeInteger(row.elapsedMs) || row.elapsedMs !== row.completedAtMs - session.startedAtMs) {
                  fail("INVALID_RESULTS", "Result must be scored from this permanent session and server clock");
                }
                return {uid: row.uid, percentScore: row.percentScore, elapsedMs: row.elapsedMs};
              });
              outcome = policy.resolvePlayedMatch({matchId: match.matchId, entrants: pending.entrants, results: verified});
            }
          }
        }
        if (outcome) {
          event.outcomes.push(outcome);
          event.progress = policy.progressBracket({bracket: event.bracket, outcomes: event.outcomes});
          if (event.progress.status === "FINISHED") finish(event.progress.placements, "FINISHED");
        }
        stage(meetingPath, meeting);
        response = {status: outcome ? outcome.status : evaluated.status === "STARTED" && request.kind === "ADVANCE_MATCH" ? "RESULTS_PENDING" : evaluated.status,
          event: eventView(event), meeting: evaluated,
          ...(sessions.length ? {sessions: sessions.filter((session) => session.uid === uid).map(sessionView)} : {})};
      }
    }
  }
  event.lastMutationAtMs = at;
  assertEscrow(event);
  stage(eventPath(event.eventId), event, request.kind === "CREATE");

  // Financial IDs are permanent and independent of the caller's retry id. If any exists while
  // the event says funds remain, refuse corruption instead of paying the rest a second time.
  const accounts = [];
  if (effects.length) {
    const users = [...new Set(effects.map((row) => row.uid))].sort();
    const config = await read(ECONOMY_CONSTANTS_DOC);
    const constants = readEconomyConstants(config);
    for (const movement of effects) {
      const path = `${TOURNAMENT_PATHS.funding}/${digest(movement.sourceId)}`;
      if (await read(path)) fail("INCONSISTENT_ESCROW", "Funding receipt exists before event settlement");
      stage(path, {schemaVersion: 1, eventId: event.eventId, ...movement, atMs: at}, true);
    }
    for (const owner of users) {
      const user = await read(`users/${owner}`);
      if (!user) fail("FAILED_PRECONDITION", "Tournament account is missing; preserve escrow for repair");
      const snapshot = await readPremiumEconomy({db, transaction, uid: owner, operation: {kind: "ADVANCE"}});
      const premiumPlan = planPremiumEconomy({snapshot, userData: user, constants, now: at, operation: {kind: "ADVANCE"}});
      // Match gift-boxes.readBoxState precedence; recipients need not have opened a box or
      // visited the new profile path yet. A canonical zero MUST override a stale legacy value.
      const boxCount = exact((user.boxCount !== undefined ? user.boxCount : user.countBox) ?? 0, "stored box balance");
      accounts.push({uid: owner, premiumPlan, userPatch: {...premiumPlan.userPatch}, boxCount});
    }
    if (accounts.some((row) => !row.premiumPlan.caughtUp)) {
      return {caughtUp: false, replayed: false, accounts, writes: [], response: null};
    }
    // Premium clock may already be ahead of wall time. Financial effect timestamp must not go
    // backwards across that account cursor; no event/question timer is extended by this clamp.
    at = Math.max(at, ...accounts.map((row) => row.premiumPlan.header.asOfMs));
    for (const account of accounts) {
      const delta = effects.filter((row) => row.uid === account.uid).reduce((sum, row) => sum + BigInt(row.delta), 0n);
      const balance = BigInt(account.boxCount) + delta;
      if (balance < 0n) fail("INSUFFICIENT_BOXES", "Organizer cannot fund this prize escrow");
      if (balance > BigInt(Number.MAX_SAFE_INTEGER)) fail("INVALID_ARGUMENT", "Box balance overflow");
      account.userPatch.boxCount = Number(balance);
    }
    for (const movement of effects) {
      const entry = ledgerEntry({uid: movement.uid, currency: CURRENCY.BOXES, delta: movement.delta, reason: REASON.GIFT_BOX,
        sourceId: `tournament_${digest(movement.sourceId)}`, atMs: at,
        details: {source: "COURSE_TOURNAMENT", eventId: event.eventId, kind: movement.kind, fundingId: movement.sourceId}});
      if (!entry) fail("INVALID_ARGUMENT", "Tournament ledger rejected a nonzero box movement");
      stage(`${LEDGER_PATH}/${entry.id}`, entry);
      stage(`users/${movement.uid}/receipts/tournament_${digest(movement.sourceId)}`,
        {kind: `TOURNAMENT_${movement.kind}`, messageKey: `receipt.tournament_${movement.kind.toLowerCase()}`,
          amount: movement.delta, currency: "BOXES", eventId: event.eventId, operationId: movement.sourceId, createdAtMs: at});
    }
  }
  const result = {schemaVersion: 1, ownerUid: uid, operationId: request.operationId, ...response};
  stage(opPath, {schemaVersion: 1, uid, operationId: request.operationId, eventId: event.eventId,
    fingerprint, createdAtMs: at, response: result}, true);
  const staged = [...writes.values()];
  let bytes = 0;
  for (const write of staged) {
    const length = Buffer.byteLength(canonical(write.data));
    if (length > MAX_DOCUMENT_JSON_BYTES) fail("RESOURCE_EXHAUSTED", "Immutable tournament document exceeds the safe storage budget");
    bytes += length;
  }
  if (staged.length > MAX_STAGED_WRITES || bytes > MAX_TRANSACTION_JSON_BYTES) {
    fail("RESOURCE_EXHAUSTED", "Tournament bundle exceeds the safe transaction budget; no escrow changes committed");
  }
  return {caughtUp: true, replayed: false, accounts, writes: staged, response: result};
}

/** No reads. This adapter owns user merges as well as premium/ledger and tournament records. */
function writeTournamentOperation({db, transaction, plan}) {
  if (plan.replayed) return;
  for (const account of plan.accounts) {
    writePremiumEconomy({db, transaction, uid: account.uid, plan: account.premiumPlan});
    transaction.set(db.doc(`users/${account.uid}`), {uid: account.uid, ...account.userPatch}, {merge: true});
  }
  if (!plan.caughtUp) return;
  for (const write of plan.writes) {
    if (write.create) transaction.create(db.doc(write.path), write.data);
    else transaction.set(db.doc(write.path), write.data);
  }
}

async function applyTournamentOperation({db, uid, payload, now, dependencies = {}, serverSeed = randomBytes(32).toString("hex")}) {
  normalizeTournamentRequest(uid, payload);
  for (let pass = 0; pass < PREMIUM_CATCHUP_PASSES; pass++) {
    const plan = await db.runTransaction(async (transaction) => {
      const planned = await readPlanTournamentOperation({db, transaction, uid, payload, now, dependencies, serverSeed});
      writeTournamentOperation({db, transaction, plan: planned});
      return planned;
    });
    if (plan.caughtUp) return plan.response;
  }
  fail("UNAVAILABLE", "Premium catch-up made durable progress; retry the same tournament operation id");
}

/** Read-only reconnect. Returns the SAME server clocks and saved progress, never restarts them.
 * Root's subsequent answer-submit adapter owns progress/scoring and must share these deadlines.
 */
async function readTournamentSession({db, transaction, uid, sessionId, now}) {
  uidValue(uid); nonblank(sessionId, "sessionId"); exact(now, "server time");
  if (!/^[qm]_[a-f0-9]{64}$/.test(sessionId)) fail("INVALID_ARGUMENT", "Invalid server session id");
  const saved = await transaction.get(db.doc(sessionPath(sessionId)));
  if (!saved.exists) fail("NOT_FOUND", "Tournament session not found");
  const session = saved.data();
  if (session.uid !== uid) fail("PERMISSION_DENIED", "Tournament session belongs to another account");
  if (session.sessionId !== sessionId || now < session.startedAtMs) fail("FAILED_PRECONDITION", "Session has not started");
  const storedForm = await transaction.get(db.doc(formPath(session.eventId, session.formId)));
  const form = storedForm.exists ? storedForm.data() : null;
  if (!form || form.fingerprint !== session.formFingerprint) fail("INCONSISTENT_SESSION", "Immutable form is unavailable");
  return {session: sessionView(session), serverNowMs: now, deadlineReached: now >= session.endsAtMs,
    questionDeadlineReached: now >= session.questionEndsAtMs,
    // Returning only the current redacted question avoids fetching the future answer schedule.
    question: session.status === "STARTED" && now < Math.min(session.endsAtMs, session.questionEndsAtMs) ? form.questions[session.questionIndex] || null : null};
}

module.exports = {TOURNAMENT_PATHS, KINDS, MAX_DOCUMENT_JSON_BYTES, MAX_TRANSACTION_JSON_BYTES,
  normalizeTournamentRequest, qualificationSessionId, matchSessionId, readPlanTournamentOperation,
  writeTournamentOperation, applyTournamentOperation, readTournamentSession};
