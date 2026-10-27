"use strict";

/**
 * Реестр скиллов этапов проверки (AD-32): то, что раннер передаёт воркеру.
 *
 * Полный план этапов (WORDS → FACTS) обязателен: полуконфигурированный воркер отказывает
 * (STAGE_MISSING) и очередь не трогает. Сторож полного покрытия живёт здесь же — новая стадия
 * в STAGE_PLAN без скилла ломает реестр тестом, а не боем.
 */

const {STAGE_PLAN} = require("./review-pipeline");
const stageWords = require("./stage-words");
const stageFacts = require("./stage-facts");

const reviewStages = {
  WORDS: stageWords,
  FACTS: stageFacts,
};

function completeReviewStages() {
  for (const stage of STAGE_PLAN) {
    if (!reviewStages[stage]) {
      throw new Error(`review stage ${stage} has no registered skill`);
    }
  }
  return reviewStages;
}

module.exports = {completeReviewStages, reviewStages};
