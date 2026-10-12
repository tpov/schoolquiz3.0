"use strict";

/**
 * Сезон экономики закрывается снимком (история 5.1).
 *
 * Сезон — календарный квартал UTC. Когда квартал созрел, плановая работа создаёт документ сезона
 * ровно один раз: в нём лежит снимок топ-100 таблицы лидеров и дайджест снимка. Существование
 * документа — ключ повтора закрытия: повторный прогон читает существующий, сверяет дайджест и
 * ничего не пишет, так что выплата (5.2, 5.3) всегда считается от одной и той же картины.
 *
 * Накопленные счётчики открытий приглашённых снимаются порциями в подколлекцию сезона — по
 * документу на игрока; курсор обхода лежит в самом документе сезона и двигается только вперёд.
 * Сам счётчик ведётся на документе игрока с первого открытия и никогда не обнуляется: сезонная
 * разность считается по снимкам, а не по сбросам.
 *
 * Чистый модуль: тестируется без firebase-admin, Firestore приходит параметром.
 */

const {createHash} = require("node:crypto");
const {canonicalJson} = require("./canonical-json");
const {CURRENCY, LEDGER_PATH, REASON, ledgerEntry} = require("./currency-ledger");
const {TROPHY_SEASON_CHAMPION, trophyList} = require("./trophies");
const {readEconomyConstants} = require("./economy-constants");

/** Куда ложится сезон; коллекция закрыта правилами, читает её только сервер. */
const ECONOMY_SEASONS_COLLECTION = "economy_seasons";

/** Статусы сезона. CLOSED — снимок записан; SETTLED — обе выплаты закрыты, сезон мёртв. */
const ECONOMY_SEASON_CLOSED = "CLOSED";
const ECONOMY_SEASON_SETTLED = "SETTLED";

/** Куда ложится разовая награда «за шестерых»; коллекция закрыта правилами. */
const REFERRAL_AWARDS_COLLECTION = "referral_awards";

const SCHEMA_VERSION = 1;

/**
 * Вместимость транзакции порции счётчиков: порция пишет документ снимка на игрока и курсор на
 * сезоне — сто игроков дают ~ сто записей против пятисот допустимых. Граница модуля, не таблицы
 * экономики.
 */
const SEASON_REFERRAL_BATCH = 100;

/**
 * Сколько созревших кварталов назад догоняющий проход закрывает: простой планировщика длиннее
 * года — повод для операторского взгляда, а не для безграничной ходьбы назад.
 */
const SEASON_CLOSE_LOOKBACK_QUARTERS = 4;

/**
 * Документ-граница: в каком квартале сезонная экономика вообще встала на учёт.
 *
 * Кварталы старше установки сезоном не жили — игроки не знали, что соревнуются, и платить за них
 * нечем и незачем; закрыть их значило бы раздать призы за соревнование, которого не было. Первый
 * прогон ставит маркер с кварталом установки, и догоняющий проход не заходит старше него.
 */
const INSTALL_MARKER_ID = "_installed";
const INSTALL_MARKER_KIND = "install";

/** Сколько порций счётчиков снимает один прогон: расписание догонит остальное следующим тиком. */
const SEASON_SWEEP_PORTIONS = 10;

/**
 * Вместимость транзакции порции выплат (история 5.2): порция пишет документ выплаты, баланс,
 * строку книги и квитанцию на каждое место — пятьдесят мест дают ~двести записей против пятисот
 * допустимых. Граница модуля, не таблицы экономики.
 */
const SEASON_PAYOUT_BATCH = 50;

/** Место, которому сезон платит трофей. Продуктовый факт спеки, не ручка таблицы. */
const SEASON_CHAMPION_RANK = 1;

/**
 * Граница чтения приглашённых одного пригласившего за одну транзакцию: процент считает всех
 * прямых приглашённых одним floor'ом, поэтому чтение цельное, а за границей — громкий отказ
 * оператору, не тихая потеря денег. Граница модуля, не таблицы экономики.
 */
const REFERRAL_INVITEE_READ_LIMIT = 200;

const SEASON_ID_PATTERN = /^\d{4}-Q[1-4]$/;

function fail(code, message) { const error = new Error(message); error.code = code; throw error; }

function exactNonNegative(value, label) {
  if (!Number.isSafeInteger(value) || value < 0) fail("INVALID_ARGUMENT", `${label} must be an exact nonnegative integer`);
  return value;
}

/**
 * Календарный квартал, содержащий atMs: [startMs, endMs), всегда UTC.
 *
 * Семантика — та же, что у арены (utcQuarterWindow), но копия своя: у сезонов экономики свой
 * домен, и связывать их идентификаторы между собой нечего.
 */
function quarterWindow(atMs) {
  exactNonNegative(atMs, "server time");
  const date = new Date(atMs);
  if (!Number.isFinite(date.getTime())) fail("INVALID_ARGUMENT", "server time is outside the Date range");
  const year = date.getUTCFullYear();
  const quarter = Math.floor(date.getUTCMonth() / 3) + 1;
  return quarterFromParts(year, quarter);
}

function quarterFromParts(year, quarterNumber) {
  if (!Number.isSafeInteger(year) || year < 0 || ![1, 2, 3, 4].includes(quarterNumber)) {
    fail("INVALID_ARGUMENT", "quarter parts are invalid");
  }
  const startMs = Date.UTC(year, (quarterNumber - 1) * 3, 1);
  const endMs = Date.UTC(year, quarterNumber * 3, 1);
  exactNonNegative(startMs, "quarter.startMs");
  exactNonNegative(endMs, "quarter.endMs");
  return {id: `${year}-Q${quarterNumber}`, year, quarter: quarterNumber, startMs, endMs};
}

/** Квартал из его же идентификатора: границы выводятся из календаря, не из часов запуска. */
function quarterFromId(seasonId) {
  if (typeof seasonId !== "string" || !SEASON_ID_PATTERN.test(seasonId)) {
    fail("INVALID_ARGUMENT", "season id is not a quarter key");
  }
  const [year, quarter] = seasonId.split("-Q").map(Number);
  return quarterFromParts(year, quarter);
}

/** Предыдущий квартал: тот, чьё окончание — начало данного. */
function previousQuarter(quarter) {
  const year = quarter.quarter === 1 ? quarter.year - 1 : quarter.year;
  const quarterNumber = quarter.quarter === 1 ? 4 : quarter.quarter - 1;
  return quarterFromParts(year, quarterNumber);
}

function digest(snapshot) {
  return createHash("sha256").update(canonicalJson(snapshot)).digest("hex");
}

/**
 * Топ-100 на момент закрытия: больше очков выше, при равенстве — меньший uid.
 *
 * Состав выборки фиксирует запрос (orderBy + limit), порядок внутри — код; дайджест подписывает
 * уже упорядоченный массив, поэтому сверка воспроизводима.
 */
function rankTopPlayers(docs) {
  const ranked = docs.map((doc) => {
    const user = doc.data() || {};
    if (typeof user.uid !== "string" || !user.uid) fail("INVALID_ARGUMENT", "stored player uid is corrupted");
    if (doc.id !== user.uid) fail("INVALID_ARGUMENT", "player document id disagrees with its uid");
    return {uid: user.uid, skillPoints: exactNonNegative(user.skillPoints || 0, "player skillPoints")};
  });
  ranked.sort((left, right) =>
    (right.skillPoints - left.skillPoints) || (left.uid < right.uid ? -1 : left.uid > right.uid ? 1 : 0));
  return ranked.slice(0, 100).map((row, index) => ({rank: index + 1, uid: row.uid, skillPoints: row.skillPoints}));
}

function validateSeasonSnapshot(season) {
  if (!Array.isArray(season.snapshot?.top)) fail("INVALID_SOURCE", "season snapshot top is corrupted");
  // Топ по определению сотня: самосогласованная подделка длиннее должна быть громким отказом,
  // а не тихой выплатой нулевых мест за пределами таблицы.
  if (season.snapshot.top.length > 100) fail("INVALID_SOURCE", "season snapshot top is longer than a hundred");
  for (const [index, row] of season.snapshot.top.entries()) {
    if (row.rank !== index + 1 || typeof row.uid !== "string" || !row.uid ||
        !Number.isSafeInteger(row.skillPoints) || row.skillPoints < 0) {
      fail("INVALID_SOURCE", "season snapshot top row is corrupted");
    }
  }
  if (season.snapshotDigest !== digest(season.snapshot)) {
    fail("INVALID_SOURCE", "season snapshot digest disagrees with its snapshot");
  }
}

function validateSeason(raw, seasonId) {
  if (!raw || raw.schemaVersion !== SCHEMA_VERSION || raw.seasonId !== seasonId ||
      ![ECONOMY_SEASON_CLOSED, ECONOMY_SEASON_SETTLED].includes(raw.status) ||
      !raw.quarter || raw.quarter.id !== seasonId) {
    fail("INVALID_SOURCE", "economy season document is corrupted");
  }
  const quarter = quarterFromId(seasonId);
  if (raw.quarter.startMs !== quarter.startMs || raw.quarter.endMs !== quarter.endMs) {
    fail("INVALID_SOURCE", "season quarter boundaries disagree with the calendar");
  }
  exactNonNegative(raw.createdAtMs, "season.createdAtMs");
  validateSeasonSnapshot(raw);
  if (raw.referralWalkCursorUid !== null && raw.referralWalkCursorUid !== undefined &&
      (typeof raw.referralWalkCursorUid !== "string" || !raw.referralWalkCursorUid)) {
    fail("INVALID_SOURCE", "season referral walk cursor is corrupted");
  }
  if (raw.leaderboardCursorRank !== null && raw.leaderboardCursorRank !== undefined &&
      !Number.isSafeInteger(raw.leaderboardCursorRank)) {
    fail("INVALID_SOURCE", "season leaderboard payout cursor is corrupted");
  }
  return raw;
}

/**
 * Полоса места: сколько коробок платит таблица этому месту, ноль — вне таблицы.
 *
 * Таблица проверена читателем экономики (1..100 без дыр и перекрытий), так что каждое место
 * попадает ровно в одну полосу; линейный проход честен — полос восемь.
 */
function boxesForRank(bands, rank) {
  if (!Array.isArray(bands) || !Number.isSafeInteger(rank) || rank < 1) {
    fail("INVALID_ARGUMENT", "leaderboard bands or rank are invalid");
  }
  for (const [from, to, boxes] of bands) {
    if (rank >= from && rank <= to) return exactNonNegative(boxes, "band boxes");
  }
  return 0;
}

/**
 * Закрытие одного сезона: guard по отсутствию документа, снимок, дайджест.
 *
 * Транзакция пишет только при отсутствии документа сезона: повторный прогон читает существующий,
 * сверяет дайджест и уходит, ничего не записав. Снимок топ-100 — сама таблица лидеров
 * (`skillPoints`, та же метрика, на которой стоит fetchLeagueStanding), замороженная на момент
 * закрытия.
 *
 * @returns {"closed" | "already-closed" | "not-due"} — что сделал этот прогон.
 */
async function closeEconomySeason({db, seasonId, now}) {
  exactNonNegative(now, "server time");
  const quarter = quarterFromId(seasonId);
  return db.runTransaction(async (transaction) => {
    const seasonRef = db.collection(ECONOMY_SEASONS_COLLECTION).doc(seasonId);
    const existing = await transaction.get(seasonRef);
    if (existing.exists) {
      const season = validateSeason(existing.data(), seasonId);
      if (now < season.quarter.endMs) return "not-due";
      return "already-closed";
    }
    if (now < quarter.endMs) return "not-due";
    // Все чтения — до первой записи: порядок транзакции Firestore.
    const topSnapshot = await transaction.get(
      db.collection("users").orderBy("skillPoints", "desc").limit(100));
    const snapshot = {top: rankTopPlayers(topSnapshot.docs)};
    transaction.create(seasonRef, {
      schemaVersion: SCHEMA_VERSION,
      seasonId,
      quarter,
      status: ECONOMY_SEASON_CLOSED,
      snapshot,
      snapshotDigest: digest(snapshot),
      referralWalkCursorUid: null,
      createdAtMs: now,
    });
    return "closed";
  });
}

/**
 * Снимок накопленных счётчиков открытий приглашённых: порциями, каждая — своя транзакция.
 *
 * Порция читает игроков с `referred == true` (5.3 заведёт поле; до того запрос корректно
 * возвращает пустое множество) и создаёт документ снимка каждому, у кого его ещё нет: документ —
 * и есть ключ повтора порции, двойной снимок требует дважды создать один документ — невозможно.
 * Обход идёт курсором по имени документа: курсор лежит на сезоне и двигается только вперёд, так
 * что прогон, упавший между порциями, продолжает с места — и никогда не стартует заново с первых
 * ста приглашённых. Снимок сезона при этом неприкосновенен: поля снимка и дайджест курсор не
 * трогает.
 *
 * @returns {{captured: number, done: boolean}} сколько снято и закончен ли обход.
 */
async function snapshotReferralCounters({db, seasonId, now, batchSize = SEASON_REFERRAL_BATCH}) {
  exactNonNegative(now, "server time");
  quarterFromId(seasonId);
  if (!Number.isSafeInteger(batchSize) || batchSize <= 0) fail("INVALID_ARGUMENT", "batch size must be a positive integer");
  const seasonRef = db.collection(ECONOMY_SEASONS_COLLECTION).doc(seasonId);
  const referralsCollection = seasonRef.collection("referrals");
  let captured = 0;
  for (let portion = 0; portion < SEASON_SWEEP_PORTIONS; portion++) {
    const outcome = await db.runTransaction(async (transaction) => {
      const seasonSnapshot = await transaction.get(seasonRef);
      if (!seasonSnapshot.exists) return {written: 0, done: true};
      const season = validateSeason(seasonSnapshot.data(), seasonId);
      if (now < season.quarter.endMs) return {written: 0, done: true};
      const cursor = season.referralWalkCursorUid || null;
      // Все чтения — до первой записи: порядок транзакции Firestore.
      let query = db.collection("users").where("referred", "==", true).orderBy("__name__").limit(batchSize);
      if (cursor !== null) query = query.startAfter(cursor);
      const players = await transaction.get(query);
      if (players.docs.length === 0) {
        if (season.referralWalkDone !== true) transaction.set(seasonRef, {referralWalkDone: true}, {merge: true});
        return {written: 0, done: true};
      }
      const reads = {};
      for (const doc of players.docs) {
        reads[doc.id] = await transaction.get(referralsCollection.doc(doc.id));
      }
      let written = 0;
      for (const doc of players.docs) {
        const user = doc.data() || {};
        if (typeof user.referredBy !== "string" || !user.referredBy) {
          fail("INVALID_ARGUMENT", "referred player is missing its referrer");
        }
        if (user.referredBy === doc.id) fail("INVALID_ARGUMENT", "a player cannot be its own referrer");
        if (reads[doc.id].exists) continue;
        transaction.create(referralsCollection.doc(doc.id), {
          uid: doc.id,
          referredBy: user.referredBy,
          openedBoxes: exactNonNegative(user.openedBoxesTotal || 0, "player openedBoxesTotal"),
          capturedAtMs: now,
        });
        written += 1;
      }
      const done = players.docs.length < batchSize;
      transaction.set(seasonRef, {
        referralWalkCursorUid: players.docs.at(-1).id,
        ...(done ? {referralWalkDone: true} : {}),
      }, {merge: true});
      return {written, done};
    });
    captured += outcome.written;
    if (outcome.done) return {captured, done: true};
  }
  return {captured, done: false};
}

/**
 * Выплата лидерборда по снимку сезона (история 5.2): порциями, по возрастанию места.
 *
 * Порция читает сезон (тем же validateSeason: снимок под дайджестом), берёт первые неоплаченные
 * места из снимка и платит каждое своей записью в той же транзакции: документ выплаты — create,
 * и двойное начисление требует дважды создать один документ — невозможно; коробки приходят к
 * балансу абсолютом, строка книги называет сезон и место, квитанция объясняет выплату ключом с
 * параметрами. Курсор выплат на сезоне двигается только вперёд, поэтому прогон, упавший между
 * порциями, продолжает с места, а оплаченные места — всегда префикс.
 *
 * @returns {{paid: number, boxes: number, done: boolean}} сколько мест оплачено и закончен ли расчёт.
 */
async function payLeaderboard({db, seasonId, now, constants, batchSize = SEASON_PAYOUT_BATCH}) {
  exactNonNegative(now, "server time");
  quarterFromId(seasonId);
  const bands = constants && constants.seasons ? constants.seasons.leaderboardBands : null;
  if (!Array.isArray(bands) || bands.length === 0) fail("INVALID_ARGUMENT", "leaderboard bands are required");
  if (!Number.isSafeInteger(batchSize) || batchSize <= 0) fail("INVALID_ARGUMENT", "batch size must be a positive integer");
  const seasonRef = db.collection(ECONOMY_SEASONS_COLLECTION).doc(seasonId);
  const payoutsCollection = seasonRef.collection("payouts");
  let paid = 0;
  let boxes = 0;
  for (let portion = 0; portion < SEASON_SWEEP_PORTIONS; portion++) {
    const outcome = await db.runTransaction(async (transaction) => {
      const seasonSnapshot = await transaction.get(seasonRef);
      if (!seasonSnapshot.exists) return {paid: 0, done: true};
      const season = validateSeason(seasonSnapshot.data(), seasonId);
      if (season.leaderboardPaid === true) return {paid: 0, done: true};
      const top = season.snapshot.top;
      const cursor = season.leaderboardCursorRank || 0;
      // Все чтения — до первой записи: порядок транзакции Firestore.
      const slice = top.filter((row) => row.rank > cursor).slice(0, batchSize);
      if (slice.length === 0) {
        if (season.leaderboardPaid !== true) transaction.set(seasonRef, {leaderboardPaid: true}, {merge: true});
        return {paid: 0, done: true};
      }
      const reads = {};
      for (const row of slice) {
        reads[row.uid] = {
          payout: await transaction.get(payoutsCollection.doc(row.uid)),
          user: await transaction.get(db.collection("users").doc(row.uid)),
        };
      }
      let portionPaid = 0;
      let portionBoxes = 0;
      let lastRank = cursor;
      for (const row of slice) {
        const trophy = row.rank === SEASON_CHAMPION_RANK;
        const boxes = boxesForRank(bands, row.rank);
        if (reads[row.uid].payout.exists) continue;
        const user = reads[row.uid].user.exists ? reads[row.uid].user.data() || {} : {};
        const boxCount = exactNonNegative(user.boxCount || 0, "player boxCount");
        transaction.create(payoutsCollection.doc(row.uid), {
          uid: row.uid, seasonId, kind: "LEADERBOARD", rank: row.rank, boxes, trophy, paidAtMs: now,
        });
        transaction.set(db.collection("users").doc(row.uid), {
          uid: row.uid,
          boxCount: boxCount + boxes,
          ...(trophy ? {trophies: trophyList([...trophyList(user.trophies), TROPHY_SEASON_CHAMPION])} : {}),
          updatedAtMs: now,
        }, {merge: true});
        const ledger = ledgerEntry({
          uid: row.uid,
          currency: CURRENCY.BOXES,
          delta: boxes,
          reason: REASON.SEASON_LEADERBOARD,
          sourceId: `${seasonId}_leaderboard`,
          atMs: now,
          details: {seasonId, rank: row.rank, trophy},
        });
        if (ledger) transaction.set(db.doc(`${LEDGER_PATH}/${ledger.id}`), ledger, {merge: true});
        transaction.set(db.collection("users").doc(row.uid).collection("receipts")
          .doc(`season_leaderboard_${seasonId}`), {
          kind: "SEASON_LEADERBOARD",
          messageKey: "receipt.season_leaderboard",
          params: {seasonId, rank: row.rank, boxes, trophy},
          amount: boxes,
          currency: "BOXES",
          createdAtMs: now,
        });
        portionPaid += 1;
        portionBoxes += boxes;
        lastRank = row.rank;
      }
      const done = slice.length < batchSize || lastRank >= top.at(-1).rank;
      transaction.set(seasonRef, {
        leaderboardCursorRank: lastRank,
        ...(done ? {leaderboardPaid: true} : {}),
      }, {merge: true});
      return {paid: portionPaid, boxes: portionBoxes, done};
    });
    paid += outcome.paid;
    boxes += outcome.boxes || 0;
    if (outcome.done) return {paid, boxes, done: true};
  }
  return {paid, boxes, done: false};
}

/**
 * Расчёт одного пригласившего: процент сезона и, если пора, разовая награда — одна транзакция.
 *
 * Процент — один floor от суммы разностей прямых приглашённых: счётчик игрока минус его самый
 * свежий снимок прошлых сезонов (в глубину догоняющего прохода; ни разу не снятому — ноль).
 * Кумулятивный счётчик делает пропавший снимок middle-сезона безболезненным: разность берётся
 * от более старой базы — деньги не теряются и не платятся дважды. Документ выплаты — create,
 * ключ повтора и самого процента, и засчитанного нуля. Награда «за шестерых» — отдельная
 * коллекция и отдельный ключ: она не сезонная, «ровно один раз за всё время».
 */
async function settleReferral({db, seasonId, referrerUid, now, constants}) {
  exactNonNegative(now, "server time");
  quarterFromId(seasonId);
  if (typeof referrerUid !== "string" || !referrerUid) fail("INVALID_ARGUMENT", "referrer uid is invalid");
  const referral = constants && constants.seasons ? constants.seasons : null;
  if (!referral) fail("INVALID_ARGUMENT", "referral constants are required");
  const percent = exactNonNegative(referral.referralPercent, "referralPercent");
  const target = exactNonNegative(referral.referralInviteTarget, "referralInviteTarget");
  const each = exactNonNegative(referral.referralInviteBoxes, "referralInviteBoxes");
  const bonus = exactNonNegative(referral.referralBonusBoxes, "referralBonusBoxes");
  return db.runTransaction(async (transaction) => {
    // Все чтения — до первой записи: порядок транзакции Firestore.
    const payoutRef = db.collection(ECONOMY_SEASONS_COLLECTION).doc(seasonId).collection("payouts")
      .doc(`referral_${referrerUid}`);
    if ((await transaction.get(payoutRef)).exists) return {settled: false, boxes: 0, credited: 0, awarded: false};
    const awardRef = db.collection(REFERRAL_AWARDS_COLLECTION).doc(referrerUid);
    const awardSnapshot = await transaction.get(awardRef);
    const invitees = await transaction.get(db.collection("users")
      .where("referredBy", "==", referrerUid).orderBy("__name__").limit(REFERRAL_INVITEE_READ_LIMIT + 1));
    if (invitees.docs.length > REFERRAL_INVITEE_READ_LIMIT) {
      fail("INVALID_SOURCE", `referrer ${referrerUid} has more than ${REFERRAL_INVITEE_READ_LIMIT} invitees`);
    }
    const priorSeasons = [];
    let cursor = quarterFromId(seasonId);
    for (let back = 0; back < SEASON_CLOSE_LOOKBACK_QUARTERS; back++) {
      cursor = previousQuarter(cursor);
      priorSeasons.push(cursor.id);
    }
    let openings = 0;
    let qualifying = 0;
    for (const doc of invitees.docs) {
      const user = doc.data() || {};
      if (user.referredBy !== referrerUid) fail("INVALID_SOURCE", "referral link disagrees with its query");
      const counter = exactNonNegative(user.openedBoxesTotal || 0, "invitee openedBoxesTotal");
      let base = 0;
      for (const sid of priorSeasons) {
        const prior = await transaction.get(
          db.collection(`${ECONOMY_SEASONS_COLLECTION}/${sid}/referrals`).doc(doc.id));
        if (prior.exists) {
          base = exactNonNegative((prior.data() || {}).openedBoxes || 0, "prior snapshot openedBoxes");
          break;
        }
      }
      openings += Math.max(0, counter - base);
      if (counter >= each) qualifying += 1;
    }
    const userSnapshot = await transaction.get(db.collection("users").doc(referrerUid));
    const user = userSnapshot.exists ? userSnapshot.data() || {} : {};
    const boxCount = exactNonNegative(user.boxCount || 0, "player boxCount");
    const boxes = Math.floor((openings * percent) / 100);
    const awardDue = !awardSnapshot.exists && qualifying >= target && bonus > 0;
    const credited = boxes + (awardDue ? bonus : 0);
    transaction.create(payoutRef, {
      uid: referrerUid, seasonId, kind: "REFERRAL", boxes, openings,
      invitees: invitees.docs.length, paidAtMs: now,
    });
    if (credited > 0) {
      transaction.set(db.collection("users").doc(referrerUid), {
        uid: referrerUid, boxCount: boxCount + credited, updatedAtMs: now,
      }, {merge: true});
    }
    if (boxes > 0) {
      const ledger = ledgerEntry({
        uid: referrerUid, currency: CURRENCY.BOXES, delta: boxes,
        reason: REASON.SEASON_REFERRAL, sourceId: `${seasonId}_referral`, atMs: now,
        details: {seasonId, openings, percent, invitees: invitees.docs.length},
      });
      if (ledger) transaction.set(db.doc(`${LEDGER_PATH}/${ledger.id}`), ledger, {merge: true});
      transaction.set(db.collection("users").doc(referrerUid).collection("receipts")
        .doc(`season_referral_${seasonId}`), {
        kind: "SEASON_REFERRAL", messageKey: "receipt.season_referral_percent",
        params: {seasonId, openings, boxes, percent, invitees: invitees.docs.length},
        amount: boxes, currency: "BOXES", createdAtMs: now,
      });
    }
    if (awardDue) {
      transaction.create(awardRef, {
        uid: referrerUid, boxes: bonus, invitees: qualifying, each, awardedAtMs: now,
      });
      const ledger = ledgerEntry({
        uid: referrerUid, currency: CURRENCY.BOXES, delta: bonus,
        reason: REASON.REFERRAL_BONUS, sourceId: "referral_bonus", atMs: now,
        details: {invitees: qualifying, each},
      });
      if (ledger) transaction.set(db.doc(`${LEDGER_PATH}/${ledger.id}`), ledger, {merge: true});
      transaction.set(db.collection("users").doc(referrerUid).collection("receipts")
        .doc("referral_bonus"), {
        kind: "REFERRAL_BONUS", messageKey: "receipt.referral_bonus",
        params: {boxes: bonus, invitees: qualifying, each},
        amount: bonus, currency: "BOXES", createdAtMs: now,
      });
    }
    return {settled: true, boxes, credited, awarded: awardDue};
  });
}

/**
 * Рефералка сезона (история 5.3): обход игроков с `referred == true` курсором на сезоне, для
 * каждого встреченного непогашенного пригласившего — своя транзакция расчёта. Курсор двигается
 * только после успешных транзакций расчёта: прогон, упавший между ними, перечитывает ту же
 * страницу и пропускает погашенных по документам выплат. Обход закончен — сезон помечен
 * `referralPaid`; обе выплаты закрыты — сезон SETTLED.
 *
 * @returns {{settled: number, boxes: number, awarded: number, done: boolean}}.
 */
async function payReferral({db, seasonId, now, constants, batchSize = SEASON_REFERRAL_BATCH}) {
  exactNonNegative(now, "server time");
  quarterFromId(seasonId);
  if (!Number.isSafeInteger(batchSize) || batchSize <= 0) fail("INVALID_ARGUMENT", "batch size must be a positive integer");
  const seasonRef = db.collection(ECONOMY_SEASONS_COLLECTION).doc(seasonId);
  let settled = 0;
  let boxes = 0;
  let awarded = 0;
  for (let portion = 0; portion < SEASON_SWEEP_PORTIONS; portion++) {
    // Чтение страницы без записи: курсор подвинется отдельной транзакцией после расчётов.
    const page = await db.runTransaction(async (transaction) => {
      const seasonSnapshot = await transaction.get(seasonRef);
      if (!seasonSnapshot.exists) return {referrers: [], lastId: null, done: true};
      const season = validateSeason(seasonSnapshot.data(), seasonId);
      if (season.referralPaid === true || season.status === ECONOMY_SEASON_SETTLED) {
        return {referrers: [], lastId: null, done: true};
      }
      const cursor = season.referralPayoutCursor || null;
      let query = db.collection("users").where("referred", "==", true).orderBy("__name__").limit(batchSize);
      if (cursor !== null) query = query.startAfter(cursor);
      const players = await transaction.get(query);
      const referrers = [];
      for (const doc of players.docs) {
        const user = doc.data() || {};
        if (typeof user.referredBy !== "string" || !user.referredBy) {
          fail("INVALID_SOURCE", "referred player is missing its referrer");
        }
        if (user.referredBy === doc.id) fail("INVALID_SOURCE", "a player cannot be its own referrer");
        if (!referrers.includes(user.referredBy)) {
          const payout = await transaction.get(
            seasonRef.collection("payouts").doc(`referral_${user.referredBy}`));
          if (!payout.exists) referrers.push(user.referredBy);
        }
      }
      return {
        referrers,
        lastId: players.docs.length ? players.docs.at(-1).id : null,
        done: players.docs.length < batchSize,
      };
    });
    for (const referrerUid of page.referrers) {
      const outcome = await settleReferral({db, seasonId, referrerUid, now, constants});
      if (outcome.settled) {
        settled += 1;
        boxes += outcome.credited;
        if (outcome.awarded) awarded += 1;
      }
    }
    // Курсор и флаги — после успешных расчётов: падение между ними перечитывает страницу.
    const finished = await db.runTransaction(async (transaction) => {
      const seasonSnapshot = await transaction.get(seasonRef);
      if (!seasonSnapshot.exists) return {done: true};
      const season = validateSeason(seasonSnapshot.data(), seasonId);
      if (season.referralPaid === true || season.status === ECONOMY_SEASON_SETTLED) return {done: true};
      const done = page.done && page.referrers.length === 0;
      const bothPaid = done && season.leaderboardPaid === true;
      transaction.set(seasonRef, {
        ...(page.lastId ? {referralPayoutCursor: page.lastId} : {}),
        ...(done ? {referralPaid: true} : {}),
        ...(bothPaid ? {status: ECONOMY_SEASON_SETTLED} : {}),
      }, {merge: true});
      return {done};
    });
    if (finished.done) return {settled, boxes, awarded, done: true};
  }
  return {settled, boxes, awarded, done: false};
}

/**
 * Граница установки: квартал первого прогона, с которого сезонная история и начинается.
 *
 * Ставится один раз, транзакцией — два параллельных прогона спорят о ней ровно до первого
 * коммита. Испорченный маркер — громкий отказ: на нём стоит вся граница «за что платим».
 */
async function ensureInstallMarker({db, now}) {
  exactNonNegative(now, "server time");
  const markerRef = db.collection(ECONOMY_SEASONS_COLLECTION).doc(INSTALL_MARKER_ID);
  const existing = await markerRef.get();
  if (existing.exists) {
    const marker = existing.data() || {};
    const quarter = marker.quarter && typeof marker.quarter.id === "string" ?
      quarterFromId(marker.quarter.id) : null;
    // Границы сверяются с календарём, как у самого сезона: маркер решает, за что платим.
    if (marker.marker !== INSTALL_MARKER_KIND || !quarter ||
        marker.quarter.startMs !== quarter.startMs || marker.quarter.endMs !== quarter.endMs) {
      fail("INVALID_SOURCE", "season install marker is corrupted");
    }
    return {...marker, quarter};
  }
  const quarter = quarterWindow(now);
  await db.runTransaction(async (transaction) => {
    const check = await transaction.get(markerRef);
    if (check.exists) return;
    transaction.create(markerRef, {schemaVersion: SCHEMA_VERSION, marker: INSTALL_MARKER_KIND, quarter, createdAtMs: now});
  });
  return {schemaVersion: SCHEMA_VERSION, marker: INSTALL_MARKER_KIND, quarter, createdAtMs: now};
}

/**
 * Весь прогон: закрыть созревшие сезоны и докончить обход счётчиков по закрытым, ничего не делать
 * лишний раз. Выплаты (5.2, 5.3) лягут поверх закрытых сезонов своими историями.
 *
 * @param {object} deps
 * @param {object} deps.db Firestore.
 * @param {number} deps.now серверное время.
 * @returns {Promise<{closed: string[], captured: number, skipped: number}>}
 */
async function sweepEconomySeasons({db, now, constants}) {
  exactNonNegative(now, "server time");
  const install = await ensureInstallMarker({db, now});
  const bands = constants && constants.seasons ? constants : readEconomyConstants(null);
  const closed = [];
  let captured = 0;
  let paid = 0;
  let paidBoxes = 0;
  let referralSettled = 0;
  let referralBoxes = 0;
  let referralAwarded = 0;
  let skipped = 0;
  let cursor = quarterWindow(now);
  for (let back = 0; back < SEASON_CLOSE_LOOKBACK_QUARTERS; back++) {
    cursor = previousQuarter(cursor);
    // Старше установки сезон не жил: маркер и есть граница «за что вообще платим».
    if (cursor.endMs <= install.quarter.startMs) break;
    const outcome = await closeEconomySeason({db, seasonId: cursor.id, now});
    if (outcome === "closed") closed.push(cursor.id);
    else if (outcome === "not-due") break;
    else skipped += 1;
    const seasonRef = db.collection(ECONOMY_SEASONS_COLLECTION).doc(cursor.id);
    const seasonSnapshot = await seasonRef.get();
    if (!seasonSnapshot.exists) continue;
    const season = validateSeason(seasonSnapshot.data(), cursor.id);
    if (season.referralWalkDone !== true) {
      const walk = await snapshotReferralCounters({db, seasonId: cursor.id, now});
      captured += walk.captured;
    }
    if (season.leaderboardPaid !== true) {
      const payout = await payLeaderboard({db, seasonId: cursor.id, now, constants: bands});
      paid += payout.paid;
      paidBoxes += payout.boxes;
    }
    if (season.referralPaid !== true && season.status !== ECONOMY_SEASON_SETTLED) {
      const referral = await payReferral({db, seasonId: cursor.id, now, constants: bands});
      referralSettled += referral.settled;
      referralBoxes += referral.boxes;
      referralAwarded += referral.awarded;
    }
  }
  return {closed, captured, paid, paidBoxes, referralSettled, referralBoxes, referralAwarded, skipped};
}

module.exports = {
  ECONOMY_SEASONS_COLLECTION,
  ECONOMY_SEASON_CLOSED,
  ECONOMY_SEASON_SETTLED,
  REFERRAL_AWARDS_COLLECTION,
  INSTALL_MARKER_ID,
  SCHEMA_VERSION,
  SEASON_REFERRAL_BATCH,
  SEASON_PAYOUT_BATCH,
  SEASON_CLOSE_LOOKBACK_QUARTERS,
  SEASON_CHAMPION_RANK,
  REFERRAL_INVITEE_READ_LIMIT,
  boxesForRank,
  closeEconomySeason,
  digest,
  ensureInstallMarker,
  payLeaderboard,
  payReferral,
  previousQuarter,
  quarterFromId,
  quarterWindow,
  rankTopPlayers,
  snapshotReferralCounters,
  sweepEconomySeasons,
  validateSeason,
};
