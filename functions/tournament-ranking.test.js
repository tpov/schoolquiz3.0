"use strict";

const assert = require("assert");
const {calculateTournamentLeaderboard} = require("./tournament-ranking");

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
testOverlappingGroupsBuildOneTable();
testRepeatedGroupsSmoothBadAttempt();
testDisconnectedGraphIsReported();
testNoisySevenRoundSimulationKeepsStrongPlayersNearTop();

console.log("tournament-ranking tests passed");
