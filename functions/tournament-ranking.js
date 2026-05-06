"use strict";

const ALGORITHM_VERSION = "pairwise-percent-least-squares-v1";
const DEFAULT_RATING_BASE = 1000;
const DEFAULT_RATING_SCALE = 10;
const DEFAULT_MIN_GROUPS_FOR_STABLE_RANK = 7;
const EPSILON = 1e-9;

function calculateTournamentLeaderboard(groups, options = {}) {
  const normalizedGroups = normalizeTournamentGroups(groups);
  const ratingBase = finiteNumber(options.ratingBase, DEFAULT_RATING_BASE);
  const ratingScale = finiteNumber(options.ratingScale, DEFAULT_RATING_SCALE);
  const minGroupsForStableRank = Math.max(
    1,
    Math.trunc(finiteNumber(options.minGroupsForStableRank, DEFAULT_MIN_GROUPS_FOR_STABLE_RANK)),
  );
  const players = new Map();
  const comparisons = [];

  for (const group of normalizedGroups) {
    if (group.results.length < 2) continue;
    for (const result of group.results) {
      const stats = playerStats(players, result.userId);
      stats.groupsPlayed += 1;
      stats.percentSum += result.percent;
      stats.groupIds.add(group.groupId);
    }
    for (let i = 0; i < group.results.length; i++) {
      for (let j = i + 1; j < group.results.length; j++) {
        const left = group.results[i];
        const right = group.results[j];
        const weight = comparisonWeight(group, left, right);
        if (weight <= 0) continue;
        const diff = left.percent - right.percent;
        comparisons.push({
          groupId: group.groupId,
          leftUserId: left.userId,
          rightUserId: right.userId,
          percentDiff: diff,
          weight,
        });
        playerStats(players, left.userId).comparisons += 1;
        playerStats(players, right.userId).comparisons += 1;
        playerStats(players, left.userId).opponents.add(right.userId);
        playerStats(players, right.userId).opponents.add(left.userId);
      }
    }
  }

  const allUserIds = Array.from(players.keys()).sort();
  const components = connectedComponents(allUserIds, comparisons);
  const ratings = solveComponentRatings(components, comparisons);
  const leaderboard = allUserIds.map((userId) => {
    const stats = playerStats(players, userId);
    const ratingPercent = ratings.get(userId) || 0;
    const component = components.find((item) => item.users.includes(userId));
    const componentId = component ? component.componentId : 0;
    const componentSize = component ? component.users.length : 1;
    return {
      userId,
      ratingPercent: round(ratingPercent, 4),
      ratingPoints: Math.round(ratingBase + ratingPercent * ratingScale),
      averagePercent: round(stats.percentSum / Math.max(1, stats.groupsPlayed), 4),
      groupsPlayed: stats.groupsPlayed,
      comparisons: stats.comparisons,
      uniqueOpponents: stats.opponents.size,
      componentId,
      componentSize,
      confidence: confidenceScore(stats, allUserIds.length, componentSize, minGroupsForStableRank),
    };
  }).sort(compareLeaderboardEntries);

  leaderboard.forEach((entry, index) => {
    entry.place = index + 1;
  });

  return {
    leaderboard,
    metadata: {
      algorithmVersion: ALGORITHM_VERSION,
      playersCount: allUserIds.length,
      groupsCount: normalizedGroups.length,
      countedGroups: normalizedGroups.filter((group) => group.results.length >= 2).length,
      comparisonCount: comparisons.length,
      isFullyConnected: components.length <= 1,
      components: components.map((component) => ({
        componentId: component.componentId,
        size: component.users.length,
        users: component.users,
      })),
    },
  };
}

function normalizeTournamentGroups(groups) {
  if (!Array.isArray(groups)) return [];
  return groups.map((group, index) => normalizeTournamentGroup(group, index)).filter(Boolean);
}

function normalizeTournamentGroup(group, index) {
  if (!group || typeof group !== "object") return null;
  const groupId = stringValue(group.groupId || group.id || `group-${index + 1}`);
  const rawResults = Array.isArray(group.results) ? group.results : [];
  const resultsByUser = new Map();
  for (const rawResult of rawResults) {
    const result = normalizeTournamentResult(rawResult);
    if (!result) continue;
    const previous = resultsByUser.get(result.userId);
    if (!previous || result.completedAtMs > previous.completedAtMs) {
      resultsByUser.set(result.userId, result);
    }
  }
  return {
    groupId,
    weight: Math.max(0, finiteNumber(group.weight, 1)),
    results: Array.from(resultsByUser.values()).sort((a, b) => stringValue(a.userId).localeCompare(b.userId)),
  };
}

function normalizeTournamentResult(result) {
  if (!result || typeof result !== "object") return null;
  const userId = stringValue(result.userId || result.uid || result.playerId);
  if (!userId) return null;
  const percent = resultPercent(result);
  if (percent === null) return null;
  return {
    userId,
    percent,
    weight: Math.max(0, finiteNumber(result.weight, 1)),
    completedAtMs: finiteNumber(result.completedAtMs || result.finishedAtMs || result.updatedAtMs, 0),
  };
}

function resultPercent(result) {
  const explicit = firstFiniteNumber(
    result.percent,
    result.percentScore,
    result.completionPercent,
    result.scorePercent,
  );
  if (explicit !== null) return clamp(explicit, 0, 100);
  const correct = firstFiniteNumber(result.correctCount, result.correctAnswers);
  const total = firstFiniteNumber(result.questionCount, result.totalQuestions, result.answeredCount);
  if (correct === null || total === null || total <= 0) return null;
  return clamp((correct / total) * 100, 0, 100);
}

function comparisonWeight(group, left, right) {
  return group.weight * Math.sqrt(left.weight * right.weight);
}

function playerStats(players, userId) {
  if (!players.has(userId)) {
    players.set(userId, {
      groupsPlayed: 0,
      percentSum: 0,
      comparisons: 0,
      opponents: new Set(),
      groupIds: new Set(),
    });
  }
  return players.get(userId);
}

function connectedComponents(userIds, comparisons) {
  const graph = new Map(userIds.map((userId) => [userId, new Set()]));
  for (const comparison of comparisons) {
    graph.get(comparison.leftUserId).add(comparison.rightUserId);
    graph.get(comparison.rightUserId).add(comparison.leftUserId);
  }
  const seen = new Set();
  const components = [];
  for (const userId of userIds) {
    if (seen.has(userId)) continue;
    const users = [];
    const stack = [userId];
    seen.add(userId);
    while (stack.length > 0) {
      const current = stack.pop();
      users.push(current);
      for (const next of graph.get(current) || []) {
        if (seen.has(next)) continue;
        seen.add(next);
        stack.push(next);
      }
    }
    users.sort();
    components.push({componentId: components.length + 1, users});
  }
  return components.sort((a, b) => b.users.length - a.users.length || stringValue(a.users[0]).localeCompare(b.users[0]))
    .map((component, index) => ({...component, componentId: index + 1}));
}

function solveComponentRatings(components, comparisons) {
  const ratings = new Map();
  for (const component of components) {
    const users = component.users;
    if (users.length === 1) {
      ratings.set(users[0], 0);
      continue;
    }
    const indexByUser = new Map(users.map((userId, index) => [userId, index]));
    const matrix = Array.from({length: users.length}, () => Array(users.length).fill(0));
    const vector = Array(users.length).fill(0);
    for (const comparison of comparisons) {
      const leftIndex = indexByUser.get(comparison.leftUserId);
      const rightIndex = indexByUser.get(comparison.rightUserId);
      if (leftIndex === undefined || rightIndex === undefined) continue;
      const weight = comparison.weight;
      const diff = comparison.percentDiff;
      matrix[leftIndex][leftIndex] += weight;
      matrix[rightIndex][rightIndex] += weight;
      matrix[leftIndex][rightIndex] -= weight;
      matrix[rightIndex][leftIndex] -= weight;
      vector[leftIndex] += weight * diff;
      vector[rightIndex] -= weight * diff;
    }
    matrix[users.length - 1] = Array(users.length).fill(1);
    vector[users.length - 1] = 0;
    const solution = solveLinearSystem(matrix, vector) || Array(users.length).fill(0);
    users.forEach((userId, index) => {
      ratings.set(userId, solution[index]);
    });
  }
  return ratings;
}

function solveLinearSystem(matrix, vector) {
  const size = vector.length;
  const rows = matrix.map((row, index) => row.slice().concat(vector[index]));
  for (let column = 0; column < size; column++) {
    let pivot = column;
    for (let row = column + 1; row < size; row++) {
      if (Math.abs(rows[row][column]) > Math.abs(rows[pivot][column])) pivot = row;
    }
    if (Math.abs(rows[pivot][column]) < EPSILON) return null;
    if (pivot !== column) {
      const tmp = rows[column];
      rows[column] = rows[pivot];
      rows[pivot] = tmp;
    }
    const pivotValue = rows[column][column];
    for (let col = column; col <= size; col++) rows[column][col] /= pivotValue;
    for (let row = 0; row < size; row++) {
      if (row === column) continue;
      const factor = rows[row][column];
      if (Math.abs(factor) < EPSILON) continue;
      for (let col = column; col <= size; col++) rows[row][col] -= factor * rows[column][col];
    }
  }
  return rows.map((row) => row[size]);
}

function confidenceScore(stats, totalPlayers, componentSize, minGroupsForStableRank) {
  const groupsFactor = clamp(stats.groupsPlayed / minGroupsForStableRank, 0, 1);
  const opponentTarget = Math.max(1, totalPlayers - 1);
  const opponentsFactor = clamp(stats.opponents.size / opponentTarget, 0, 1);
  const componentFactor = totalPlayers <= 1 ? 1 : clamp((componentSize - 1) / (totalPlayers - 1), 0, 1);
  const comparisonTarget = Math.max(1, minGroupsForStableRank * Math.min(4, opponentTarget));
  const comparisonsFactor = clamp(stats.comparisons / comparisonTarget, 0, 1);
  return round(
    groupsFactor * 0.35 +
      opponentsFactor * 0.35 +
      componentFactor * 0.2 +
      comparisonsFactor * 0.1,
    4,
  );
}

function compareLeaderboardEntries(left, right) {
  return right.ratingPercent - left.ratingPercent ||
    right.averagePercent - left.averagePercent ||
    right.groupsPlayed - left.groupsPlayed ||
    right.uniqueOpponents - left.uniqueOpponents ||
    left.userId.localeCompare(right.userId);
}

function firstFiniteNumber(...values) {
  for (const value of values) {
    const number = finiteNumber(value, null);
    if (number !== null) return number;
  }
  return null;
}

function finiteNumber(value, fallback) {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (value && typeof value.toNumber === "function") return value.toNumber();
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function round(value, digits) {
  const factor = 10 ** digits;
  return Math.round(value * factor) / factor;
}

function stringValue(value, fallback = "") {
  if (value === null || value === undefined) return fallback;
  const text = String(value);
  return text.length > 0 ? text : fallback;
}

module.exports = {
  ALGORITHM_VERSION,
  calculateTournamentLeaderboard,
};
