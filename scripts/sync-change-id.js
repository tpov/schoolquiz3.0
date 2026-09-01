'use strict';

/**
 * Как называется документ журнала изменений.
 *
 * Одна форма на весь проект (AD-11): документ на узел, идентификатор без времени. Сервер пишет
 * именно так — `publicCatalogSyncChangePath` и `lessonContentSyncChangePath` в
 * `functions/index.js`, — а сиды и бэкфиллы годами писали `{время}-{тип}-{id}`.
 *
 * Разница не косметическая. Со временем в идентификаторе каждый повторный прогон сида создаёт
 * новые документы вместо того, чтобы переписать существующие: коллекция журнала растёт
 * неограниченно, а клиент вычитывает одно и то же изменение столько раз, сколько раз запускали сид.
 *
 * Модуль общий, чтобы у скриптов не было своего мнения на этот счёт.
 */

/** Документ журнала каталога: один на узел. */
function catalogSyncChangeId(type, id) {
  return `${type}_${id}`;
}

/**
 * Документ журнала содержимого урока.
 *
 * Здесь только идентификатор вопроса, без типа: в этом журнале других типов не бывает, и сервер
 * пишет так же (`lessonContentSyncChangePath`).
 */
function lessonContentSyncChangeId(questionId) {
  return String(questionId);
}

/** Полный путь к документу журнала каталога. */
function catalogSyncChangePath(catalogId, type, id) {
  return `catalogs/${catalogId}/sync_changes/${catalogSyncChangeId(type, id)}`;
}

/** Полный путь к документу журнала содержимого урока. */
function lessonContentSyncChangePath(lessonId, questionId) {
  return `lesson_content/${lessonId}/sync_changes/${lessonContentSyncChangeId(questionId)}`;
}

module.exports = {
  catalogSyncChangeId,
  catalogSyncChangePath,
  lessonContentSyncChangeId,
  lessonContentSyncChangePath,
};
