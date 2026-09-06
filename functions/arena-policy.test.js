"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {
  weightedScore,
  compareScores,
  aggregateLessonRatings,
  assignLessonPrices,
  utcQuarterWindow,
  rankSeasonLessons,
  splitAuthorRoyalty,
  planLessonAdvancement,
  planSeasonAward,
} = require("./arena-policy");

function completed(uid, lessonId = "lesson", suffix = "", overrides = {}) {
  return {attemptId: `attempt:${lessonId}:${uid}:${suffix}`, uid, lessonId,
    status: "SETTLED", completed: true, settledAtMs: 10, ...overrides};
}

function voted(uid, rating = 2, lessonId = "lesson", suffix = "", overrides = {}) {
  return {ratingId: `rating:${lessonId}:${uid}:${suffix}`, uid, lessonId,
    attemptId: `attempt:${lessonId}:${uid}:${suffix}`, rating, acceptedAtMs: 20, ...overrides};
}

function aggregate(ratings, completions, extra = {}) {
  return aggregateLessonRatings({lessonId: "lesson", authorUid: "owner", ratings, completions, ...extra});
}

test("latest eligible vote per player and lesson; retries, owner and incomplete attempts do not count", () => {
  const votes = [
    voted("a", 1, "lesson", "old"),
    voted("a", 3, "lesson", "new", {acceptedAtMs: 30}),
    voted("a", 1, "lesson", "unfinished", {acceptedAtMs: 40}),
    voted("b", 2), voted("b", 2),
    voted("owner", 3),
    voted("pending", 3),
    voted("aborted", 3),
    voted("early", 3, "lesson", "", {acceptedAtMs: 9}),
    voted("foreign", 3),
    voted("other-lesson", 3, "other"),
  ];
  const receipts = [
    completed("a", "lesson", "old"), completed("a", "lesson", "new"),
    completed("a", "lesson", "unfinished", {completed: false}),
    completed("b"), completed("b"), completed("owner"),
    completed("pending", "lesson", "", {status: "PENDING"}),
    completed("aborted", "lesson", "", {status: "ABORTED", completed: false}),
    completed("early"),
    completed("foreign", "lesson", "", {uid: "someone-else"}),
    completed("other-lesson", "other"),
  ];
  const result = aggregate(votes, receipts);
  assert.equal(result.ratingCount, 2);
  assert.equal(result.ratingSum, 5);
  assert.deepEqual(result.weightedScore, {numerator: 45, denominator: 22});
  assert.deepEqual(result.acceptedRatingIds, ["rating:lesson:a:new", "rating:lesson:b:"]);
});

test("votes require matching settled receipt; absent, wrong lesson and premature votes are excluded", () => {
  const result = aggregate([voted("none"), voted("wrong"), voted("late")], [
    completed("wrong", "lesson", "", {lessonId: "other"}),
    completed("late", "lesson", "", {settledAtMs: 21}),
  ]);
  assert.equal(result.ratingCount, 0);
  assert.deepEqual(result.weightedScore, {numerator: 40, denominator: 20});
});

test("same acceptedAt uses stable ratingId, independent of input order", () => {
  const receipts = [completed("a", "lesson", "z"), completed("a", "lesson", "a")];
  const votes = [voted("a", 3, "lesson", "z"), voted("a", 1, "lesson", "a")];
  assert.equal(aggregate(votes, receipts).ratingSum, 1);
  assert.deepEqual(aggregate([...votes].reverse(), receipts), aggregate(votes, receipts));
});

test("conflicting retry bodies are rejected instead of silently replacing trusted facts", () => {
  assert.throws(() => aggregate([voted("a", 1), voted("a", 3)], [completed("a")]), /conflicting duplicate/);
  assert.throws(() => aggregate([voted("a")], [completed("a"), completed("a", "lesson", "", {uid: "b"})]),
    /conflicting duplicate/);
});

test("invalid numeric input fails closed; weighted arithmetic never returns unsafe integers", () => {
  for (const rating of [0, 4, 1.5, NaN, Infinity, "3"]) {
    assert.throws(() => aggregate([voted("a", rating)], [completed("a")]));
  }
  assert.throws(() => weightedScore({ratingSum: 4, ratingCount: 1}), /inconsistent/);
  assert.throws(() => weightedScore({ratingSum: 1, ratingCount: 0}), /inconsistent/);
  assert.throws(() => weightedScore({ratingSum: Number.MAX_SAFE_INTEGER, ratingCount: Number.MAX_SAFE_INTEGER}),
    /safe integer/);
  assert.throws(() => compareScores({numerator: 1, denominator: 0}, {numerator: 1, denominator: 1}));
});

test("exact fractions, not rounded displays, define shared score levels", () => {
  const n = Number.MAX_SAFE_INTEGER - 100;
  const a = weightedScore({ratingSum: n, ratingCount: n});
  const b = weightedScore({ratingSum: n + 1, ratingCount: n + 1});
  assert.equal(a.numerator / a.denominator, b.numerator / b.denominator, "float collision fixture");
  assert.equal(compareScores(a, b), 1);
  assert.equal(compareScores({numerator: 42, denominator: 21}, {numerator: 44, denominator: 22}), 0);
  const prices = assignLessonPrices([
    {lessonId: "larger", ratingSum: n, ratingCount: n},
    {lessonId: "smaller", ratingSum: n + 1, ratingCount: n + 1},
  ]);
  assert.deepEqual(prices.map((row) => row.priceNolics), [100, 1]);
});

test("prices are ordinal across distinct rated levels, not the numerical distance between scores", () => {
  const prices = assignLessonPrices([
    {lessonId: "low", ratingSum: 20, ratingCount: 20},
    {lessonId: "middle", ratingSum: 3, ratingCount: 1},
    {lessonId: "high", ratingSum: 60, ratingCount: 20},
    {lessonId: "unrated", ratingSum: 0, ratingCount: 0},
  ]);
  assert.deepEqual(prices.map((row) => row.priceNolics), [1, 51, 100, 1]);
  assert.equal(prices[3].ratedScoreLevelIndex, null);
  const ties = assignLessonPrices([
    {lessonId: "a", ratingSum: 2, ratingCount: 1},
    {lessonId: "b", ratingSum: 4, ratingCount: 2},
  ]);
  assert.deepEqual(ties.map((row) => [row.priceNolics, row.ratedScoreLevelIndex]), [[1, 0], [1, 0]]);
  assert.deepEqual(assignLessonPrices([]), []);
  assert.throws(() => assignLessonPrices([
    {lessonId: "a", ratingSum: 1, ratingCount: 1}, {lessonId: "a", ratingSum: 2, ratingCount: 1},
  ]), /duplicate lessonId/);
});

test("rounded ordinal prices remain monotonic and exact ties get the same price", () => {
  const rows = Array.from({length: 105}, (_, index) => ({
    lessonId: `lesson-${index}`, ratingSum: index + 1, ratingCount: index + 1,
  }));
  const prices = assignLessonPrices(rows);
  assert.equal(prices[0].priceNolics, 100);
  assert.equal(prices[prices.length - 1].priceNolics, 1);
  for (let i = 1; i < prices.length; i++) assert.ok(prices[i].priceNolics <= prices[i - 1].priceNolics);
});

test("quarters are UTC, half-open, and cross year/leap-day boundaries", () => {
  assert.deepEqual(utcQuarterWindow(Date.parse("2026-01-01T00:00:00Z")), {
    id: "2026-Q1", year: 2026, quarter: 1,
    startMs: Date.parse("2026-01-01T00:00:00Z"), endMs: Date.parse("2026-04-01T00:00:00Z"),
  });
  assert.equal(utcQuarterWindow(Date.parse("2026-04-01T00:00:00Z") - 1).id, "2026-Q1");
  assert.equal(utcQuarterWindow(Date.parse("2026-04-01T00:00:00Z")).id, "2026-Q2");
  assert.equal(utcQuarterWindow(Date.parse("2024-02-29T23:59:59Z")).id, "2024-Q1");
  assert.equal(utcQuarterWindow(Date.parse("2026-12-31T23:59:59Z")).endMs, Date.parse("2027-01-01T00:00:00Z"));
  assert.throws(() => utcQuarterWindow(Number.MAX_SAFE_INTEGER));
});

const Q1 = Date.parse("2026-01-01T00:00:00Z");
const Q2 = Date.parse("2026-04-01T00:00:00Z");

function seasonFixture(lessonId, count, rating = 2, publishedAtMs = 1, questType = "REGULAR") {
  return {
    lesson: {lessonId, authorUid: `owner:${lessonId}`, questType, publishedAtMs},
    ratings: Array.from({length: count}, (_, i) => voted(`u${i}`, rating, lessonId, "", {acceptedAtMs: Q1})),
    completions: Array.from({length: count}, (_, i) => completed(`u${i}`, lessonId)),
  };
}

function season(...fixtures) {
  return rankSeasonLessons({
    lessons: fixtures.map((f) => f.lesson),
    ratings: fixtures.flatMap((f) => f.ratings),
    completions: fixtures.flatMap((f) => f.completions),
    quarterAtMs: Q1,
  });
}

test("season counts distinct eligible players, not retries; 19 cannot win", () => {
  const nineteen = seasonFixture("nineteen", 19, 3);
  nineteen.ratings.push(...nineteen.ratings, ...nineteen.ratings);
  const twenty = seasonFixture("twenty", 20, 1);
  const result = season(nineteen, twenty);
  assert.deepEqual(result.ranked.map((row) => row.lessonId), ["twenty"]);
  assert.equal(result.winner.ratingCount, 20);
  assert.equal(season(nineteen).winner, null);
});

test("season filters acceptance time before latest-vote selection; start included, end excluded", () => {
  const fixture = seasonFixture("eligible", 20, 2);
  fixture.ratings[0].acceptedAtMs = Q2 - 1;
  fixture.ratings.push(voted("u0", 1, "eligible", "later", {acceptedAtMs: Q2}));
  fixture.completions.push(completed("u0", "eligible", "later"));
  assert.equal(season(fixture).winner.ratingSum, 40);
  fixture.ratings[1].acceptedAtMs = Q1 - 1;
  assert.equal(season(fixture).winner, null);
  fixture.ratings[1].acceptedAtMs = Q2;
  assert.equal(season(fixture).winner, null);
});

test("season winner tie-breaks are score, voter count, older publication, stable lesson ID", () => {
  const a = seasonFixture("a", 21, 2, 10);
  const b = seasonFixture("b", 21, 2, 10);
  const later = seasonFixture("later", 21, 2, 11);
  const fewer = seasonFixture("fewer", 20, 2, 1);
  assert.deepEqual(season(b, fewer, later, a).ranked.map((row) => row.lessonId), ["a", "b", "later", "fewer"]);
  const higher = seasonFixture("higher", 20, 3, 100);
  assert.equal(season(a, higher).winner.lessonId, "higher");
});

test("course/survey, not-yet-published and pre-publication ratings do not win the Arena season", () => {
  const course = seasonFixture("course", 30, 3, 1, "COURSE");
  const survey = seasonFixture("survey", 30, 3, 1, "SURVEY");
  const future = seasonFixture("future", 30, 3, Q2);
  const premature = seasonFixture("premature", 30, 3, Q1 + 1);
  const valid = seasonFixture("valid", 20, 1);
  assert.deepEqual(season(course, survey, future, premature, valid).ranked.map((row) => row.lessonId), ["valid"]);
});

test("five one-nolic payments pay author four, sink one, and clear the conserved reserve", () => {
  let state;
  let authorTotal = 0;
  let sinkTotal = 0;
  const authorCredits = [];
  const reserves = [];
  for (let i = 0; i < 5; i++) {
    const openingReserve = state ? state.reservedNolics : 0;
    const result = splitAuthorRoyalty(1, state);
    assert.equal(1 + openingReserve, result.authorCreditNolics + result.sinkNolics + result.next.reservedNolics);
    authorTotal += result.authorCreditNolics;
    sinkTotal += result.sinkNolics;
    state = result.next;
    authorCredits.push(result.authorCreditNolics);
    reserves.push(state.reservedNolics);
  }
  assert.deepEqual(authorCredits, [0, 1, 1, 1, 1]);
  assert.deepEqual(reserves, [1, 1, 1, 1, 0]);
  assert.equal(authorTotal, 4);
  assert.equal(sinkTotal, 1);
  assert.deepEqual(state, {authorCarryFifths: 0, sinkCarryFifths: 0, reservedNolics: 0});
});

test("royalty split is partition-independent and safe at MAX_SAFE_INTEGER", () => {
  let state;
  let author = 0n;
  let sink = 0n;
  let total = 0n;
  for (const payment of [1, 2, 7, 0, 99, Number.MAX_SAFE_INTEGER]) {
    const openingReserve = BigInt(state ? state.reservedNolics : 0);
    const result = splitAuthorRoyalty(payment, state);
    assert.equal(BigInt(payment) + openingReserve,
      BigInt(result.authorCreditNolics) + BigInt(result.sinkNolics) + BigInt(result.next.reservedNolics));
    author += BigInt(result.authorCreditNolics);
    sink += BigInt(result.sinkNolics);
    total += BigInt(payment);
    state = result.next;
  }
  assert.equal(author, total * 4n / 5n);
  assert.equal(sink, total / 5n);
  assert.equal(total, author + sink + BigInt(state.reservedNolics));
  assert.deepEqual(splitAuthorRoyalty(0, state).next, state);
  for (const payment of [-1, 0.5, NaN, Number.MAX_SAFE_INTEGER + 1]) assert.throws(() => splitAuthorRoyalty(payment));
  assert.throws(() => splitAuthorRoyalty(1, {authorCarryFifths: 4, sinkCarryFifths: 1, reservedNolics: 0}), /inconsistent/);
});

test("only curator-gated consecutive REGULAR lesson advancement awards 15 and 20 BOXES", () => {
  const lesson = {lessonId: "lesson", authorUid: "owner", questType: "REGULAR", shelf: "arena"};
  const first = planLessonAdvancement({lesson, targetShelf: "tournament", curatorAuthorized: true});
  assert.equal(first.reward.amount, 15);
  assert.equal(first.reward.currency, "BOXES");
  const again = planLessonAdvancement({lesson: {...lesson, version: 200}, targetShelf: "tournament",
    curatorAuthorized: true, awardedKeys: [first.reward.key]});
  assert.equal(again.reward, null);
  const final = planLessonAdvancement({lesson: {...lesson, shelf: "tournament"}, targetShelf: "home",
    curatorAuthorized: true, awardedKeys: [first.reward.key]});
  assert.equal(final.reward.amount, 20);
  assert.notEqual(final.reward.key, first.reward.key);
  assert.throws(() => planLessonAdvancement({lesson, targetShelf: "home", curatorAuthorized: true}), /next ladder/);
  assert.throws(() => planLessonAdvancement({lesson, targetShelf: "tournament", curatorAuthorized: false}), /curator/);
  assert.throws(() => planLessonAdvancement({lesson: {...lesson, questType: "COURSE"}, targetShelf: "tournament",
    curatorAuthorized: true}), /REGULAR/);
  assert.throws(() => planLessonAdvancement({lesson: {...lesson, shelf: "__proto__"}, targetShelf: "home",
    curatorAuthorized: true}), /next ladder/);
});

test("season ten BOXES uses an independent season key and never advances a shelf", () => {
  const firstSeason = season(seasonFixture("winner", 20, 3));
  const reward = planSeasonAward({season: firstSeason});
  assert.equal(reward.amount, 10);
  assert.equal(reward.currency, "BOXES");
  assert.equal(Object.hasOwn(reward, "targetShelf"), false);
  const promotion = planLessonAdvancement({
    lesson: {lessonId: "winner", authorUid: "owner:winner", questType: "REGULAR", shelf: "arena"},
    targetShelf: "tournament", curatorAuthorized: true, awardedKeys: [reward.key],
  });
  assert.equal(promotion.reward.amount, 15);
  assert.notEqual(reward.key, promotion.reward.key);
  assert.equal(planSeasonAward({season: firstSeason, awardedKeys: [reward.key]}), null);
  // Recomputing the same finalized season with a different winner must not mint a second bonus.
  assert.equal(planSeasonAward({season: season(seasonFixture("other", 20, 3)), awardedKeys: [reward.key]}), null);
  assert.equal(planSeasonAward({season: season(seasonFixture("too-small", 19))}), null);
});

test("helpers do not mutate input and all outputs round-trip through JSON", () => {
  const fixture = seasonFixture("lesson", 20);
  const before = JSON.stringify(fixture);
  const result = season(fixture);
  const output = {
    aggregate: aggregateLessonRatings({lessonId: "lesson", authorUid: fixture.lesson.authorUid,
      ratings: fixture.ratings, completions: fixture.completions}),
    prices: assignLessonPrices(result.ranked), season: result,
    royalty: splitAuthorRoyalty(1), reward: planSeasonAward({season: result}),
  };
  assert.deepEqual(JSON.parse(JSON.stringify(output)), output);
  assert.equal(JSON.stringify(fixture), before);
});
