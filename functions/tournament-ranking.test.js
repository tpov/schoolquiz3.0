"use strict";

const assert = require("assert");
const {
  calculateTournamentLeaderboard,
  tournamentGroupForAttempt,
  tournamentResultForAttempt,
} = require("./tournament-ranking");

function entry(result, userId) {
  const found = result.leaderboard.find((item) => item.userId === userId);
  assert.ok(found, `Missing leaderboard entry for ${userId}`);
  return found;
}

function assertAlmostEqual(actual, expected, epsilon = 0.0001) {
  assert.ok(
    Math.abs(actual - expected) <= epsilon,
    `Expected ${actual} to be within ${epsilon} of ${expected}`,
  );
}

function testSingleGroupKeepsPercentDiff() {
  const result = calculateTournamentLeaderboard([
    {
      groupId: "g1",
      results: [
        {userId: "A", percent: 90},
        {userId: "B", percent: 80},
      ],
    },
  ]);

  assert.strictEqual(result.metadata.isFullyConnected, true);
  assertAlmostEqual(entry(result, "A").ratingPercent - entry(result, "B").ratingPercent, 10);
  assert.strictEqual(result.leaderboard[0].userId, "A");
}

function testStoredResultsWithoutWeightStillCompare() {
  const result = calculateTournamentLeaderboard([
    {
      groupId: "stored-results",
      results: [
        {userId: "A", percent: 95, weight: null},
        {userId: "B", percent: 75, weight: null},
        {userId: "C", percent: 55, weight: null},
      ],
    },
  ]);

  assert.strictEqual(result.metadata.comparisonCount, 3);
  assert.strictEqual(entry(result, "A").comparisons, 2);
  assert.ok(entry(result, "A").ratingPercent > entry(result, "B").ratingPercent);
  assert.ok(entry(result, "B").ratingPercent > entry(result, "C").ratingPercent);
}

function testOverlappingGroupsBuildOneTable() {
  const result = calculateTournamentLeaderboard([
    {
      groupId: "g1",
      results: [
        {userId: "A", percent: 90},
        {userId: "B", percent: 80},
        {userId: "C", percent: 70},
      ],
    },
    {
      groupId: "g2",
      results: [
        {userId: "C", percent: 80},
        {userId: "D", percent: 70},
        {userId: "E", percent: 60},
      ],
    },
    {
      groupId: "g3",
      results: [
        {userId: "E", percent: 80},
        {userId: "F", percent: 70},
      ],
    },
  ]);

  assert.strictEqual(result.metadata.isFullyConnected, true);
  assert.deepStrictEqual(
    result.leaderboard.map((item) => item.userId),
    ["A", "B", "C", "D", "E", "F"],
  );
}

function testRepeatedGroupsSmoothBadAttempt() {
  const result = calculateTournamentLeaderboard([
    {
      groupId: "bad-bridge",
      results: [
        {userId: "C", percent: 40},
        {userId: "D", percent: 75},
      ],
    },
    {
      groupId: "normal-1",
      results: [
        {userId: "C", percent: 90},
        {userId: "D", percent: 70},
      ],
    },
    {
      groupId: "normal-2",
      results: [
        {userId: "C", percent: 92},
        {userId: "D", percent: 72},
      ],
    },
  ]);

  assert.ok(entry(result, "C").ratingPercent > entry(result, "D").ratingPercent);
}

function testDisconnectedGraphIsReported() {
  const result = calculateTournamentLeaderboard([
    {
      groupId: "g1",
      results: [
        {userId: "A", percent: 90},
        {userId: "B", percent: 80},
      ],
    },
    {
      groupId: "g2",
      results: [
        {userId: "C", percent: 90},
        {userId: "D", percent: 80},
      ],
    },
  ]);

  assert.strictEqual(result.metadata.isFullyConnected, false);
  assert.strictEqual(result.metadata.components.length, 2);
}

function testNoisySevenRoundSimulationKeepsStrongPlayersNearTop() {
  const players = Array.from({length: 32}, (_, index) => `P${String(index).padStart(2, "0")}`);
  const groups = [];
  for (let round = 0; round < 7; round++) {
    const shuffled = seededShuffle(players, 100 + round);
    for (let index = 0; index < shuffled.length; index += 6) {
      const groupPlayers = shuffled.slice(index, index + 6);
      groups.push({
        groupId: `round-${round}-group-${index / 6}`,
        results: groupPlayers.map((userId) => {
          const skill = Number(userId.slice(1));
          const noise = seededNoise(`${round}:${userId}`);
          return {
            userId,
            percent: Math.max(0, Math.min(100, 24 + skill * 2.1 + noise)),
          };
        }),
      });
    }
  }

  const result = calculateTournamentLeaderboard(groups);
  const trueTopEight = new Set(players.slice(-8));
  const rankedTopEight = result.leaderboard.slice(0, 8).filter((item) => trueTopEight.has(item.userId));

  assert.strictEqual(result.metadata.isFullyConnected, true);
  assert.ok(
    rankedTopEight.length >= 6,
    `Expected at least 6 true top players in top 8, got ${rankedTopEight.map((item) => item.userId).join(", ")}`,
  );
}

function testTournamentAttemptUsesHourlyLessonGroup() {
  const baseAttempt = {
    sourceShelf: "tournament",
    lessonId: "lesson-1",
    difficulty: "EASY",
    completedAtMs: Date.parse("2026-05-07T10:15:00.000Z"),
  };

  const first = tournamentGroupForAttempt(baseAttempt);
  const sameHour = tournamentGroupForAttempt({
    ...baseAttempt,
    completedAtMs: Date.parse("2026-05-07T10:45:00.000Z"),
  });
  const nextHour = tournamentGroupForAttempt({
    ...baseAttempt,
    completedAtMs: Date.parse("2026-05-07T11:00:00.000Z"),
  });

  assert.strictEqual(first.tournamentId, "tournament");
  assert.strictEqual(first.groupId, sameHour.groupId);
  assert.notStrictEqual(first.groupId, nextHour.groupId);
}

function testTournamentAttemptIgnoresNonTournamentShelf() {
  assert.strictEqual(
    tournamentGroupForAttempt({
      sourceShelf: "arena",
      lessonId: "lesson-1",
      completedAtMs: Date.parse("2026-05-07T10:15:00.000Z"),
    }),
    null,
  );
}

function testTournamentResultKeepsPercentAndAttemptIdentity() {
  const result = tournamentResultForAttempt({
    userId: "user-1",
    attemptId: "attempt-1",
    percentScore: 87,
    completedAtMs: 1234,
    lessonId: "lesson-1",
    questId: "quest-1",
    catalogId: "school",
    difficulty: "hard",
    answers: [{durationMs: 4000}, {durationMs: 2500}, {durationMs: 0}],
  });

  assert.deepStrictEqual(result, {
    userId: "user-1",
    attemptId: "attempt-1",
    percent: 87,
    completedAtMs: 1234,
    // Время отвечания, сложенное из самих ответов; нулевая длительность — «не мерили», а не ноль.
    elapsedMs: 6500,
    lessonId: "lesson-1",
    questId: "quest-1",
    catalogId: "school",
    difficulty: "HARD",
  });
}

function testTimeBreaksAPercentTieAndNothingMore() {
  // Ровно случай из CAP-10: два стопроцентных прогона по одному уроку, быстрый впереди.
  const group = (groupId, results) => ({groupId, weight: 1, results});
  // Имена подобраны против алфавита: пройди время мимо, и запасной порядок по userId дал бы
  // ["a-slow", "z-fast"], то есть тест зеленел бы, ничего не проверяя.
  const table = calculateTournamentLeaderboard([
    group("g1", [
      {userId: "a-slow", percent: 100, elapsedMs: 90_000},
      {userId: "z-fast", percent: 100, elapsedMs: 30_000},
    ]),
  ]).leaderboard.map((entry) => entry.userId);
  assert.deepStrictEqual(table, ["z-fast", "a-slow"]);

  // И только ничьей: тот, кто ответил лучше, остаётся выше, как бы медленно он ни отвечал.
  const byPercent = calculateTournamentLeaderboard([
    group("g1", [
      {userId: "slowButRight", percent: 100, elapsedMs: 600_000},
      {userId: "quickButWrong", percent: 40, elapsedMs: 1_000},
    ]),
  ]).leaderboard.map((entry) => entry.userId);
  assert.deepStrictEqual(byPercent, ["slowButRight", "quickButWrong"]);
}

function testAnUnmeasuredRunRanksBehindMeasuredOnes() {
  // Без замера время неизвестно, а не равно нулю: иначе старая попытка объявлялась бы мгновенной.
  const table = calculateTournamentLeaderboard([
    {groupId: "g1", weight: 1, results: [
      {userId: "z-measured", percent: 100, elapsedMs: 120_000},
      {userId: "a-unmeasured", percent: 100},
    ]},
  ]).leaderboard;
  assert.deepStrictEqual(table.map((entry) => entry.userId), ["z-measured", "a-unmeasured"]);
  assert.strictEqual(table[1].averageElapsedMs, Number.POSITIVE_INFINITY);
}

function seededShuffle(values, seed) {
  const result = values.slice();
  let state = seed;
  for (let index = result.length - 1; index > 0; index--) {
    state = (state * 1664525 + 1013904223) % 4294967296;
    const swapIndex = state % (index + 1);
    const tmp = result[index];
    result[index] = result[swapIndex];
    result[swapIndex] = tmp;
  }
  return result;
}

function seededNoise(seedText) {
  let hash = 2166136261;
  for (const char of seedText) {
    hash ^= char.charCodeAt(0);
    hash = Math.imul(hash, 16777619);
  }
  return (Math.abs(hash) % 700) / 100 - 3.5;
}

testSingleGroupKeepsPercentDiff();
testStoredResultsWithoutWeightStillCompare();
testOverlappingGroupsBuildOneTable();
testRepeatedGroupsSmoothBadAttempt();
testDisconnectedGraphIsReported();
testNoisySevenRoundSimulationKeepsStrongPlayersNearTop();
testTournamentAttemptUsesHourlyLessonGroup();
testTournamentAttemptIgnoresNonTournamentShelf();
testTournamentResultKeepsPercentAndAttemptIdentity();
testTimeBreaksAPercentTieAndNothingMore();
testAnUnmeasuredRunRanksBehindMeasuredOnes();

console.log("tournament-ranking tests passed");
