'use strict';

/**
 * Как называется документ журнала изменений — переходник к единственному источнику.
 *
 * Форма журнала (AD-11) решена в одном модуле, `functions/sync-changes.js`: им пишут сервер, сиды и
 * бэкфилл. Скрипты, которым нужны только идентификаторы и пути, берут их здесь, чтобы у скриптов не
 * было своего мнения на этот счёт; те, что пишут журнал, зовут `writeSyncChanges` оттуда напрямую.
 */

const {
  catalogSyncChangeId,
  publicCatalogSyncChangePath,
  lessonContentSyncChangeId,
  lessonContentSyncChangePath,
} = require('../functions/sync-changes');

module.exports = {
  catalogSyncChangeId,
  catalogSyncChangePath: publicCatalogSyncChangePath,
  lessonContentSyncChangeId,
  lessonContentSyncChangePath,
};
