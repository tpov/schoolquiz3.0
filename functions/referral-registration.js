"use strict";

/**
 * Регистрация по ссылке приглашающего (история 5.3).
 *
 * Ровно один честный момент атрибуции — создание аккаунта: существующий игрок, пришедший по
 * ссылке, переводил бы пригласившему свой год открытий, а установка сама по себе ничего не
 * платит (Play запрещает стимулированные установки; платятся открытия, а не приход). Поэтому
 * поля пишутся только на новом документе, и только на один уровень: процент читает
 * `referredBy` приглашённого и никогда не ходит по цепочке.
 *
 * Чистый модуль: без firebase-admin.
 */

function fail(code, message) { const error = new Error(message); error.code = code; throw error; }

/** Допустимый идентификатор документа: непустая строка без «/», не точка-имя, в разумной длине. */
function documentUid(value) {
  if (typeof value !== "string" || !value || value === "." || value === ".." ||
      value.includes("/") || value.length > 128) {
    fail("INVALID_ARGUMENT", "referredByUid must be a document id");
  }
  return value;
}

/**
 * Валидация названного пригласившего: absent — null (поле необязательное), испорченное или
 * самоприглашение — громкий отказ. Существование пригласившего проверяет транзакция.
 */
function referralRegistration({uid, referredByUid}) {
  if (referredByUid === undefined || referredByUid === null || referredByUid === "") return null;
  const inviter = documentUid(referredByUid);
  if (inviter === uid) fail("INVALID_ARGUMENT", "a player cannot be their own referrer");
  return inviter;
}

/**
 * Поля документа нового игрока: мёртвая ссылка — тихое отсутствие поля (застрявший код не
 * должен запирать новому игроку вход), живая — прямой уровень без цепочки.
 */
function referralFields({uid, referredByUid, inviterExists, now}) {
  const inviter = referralRegistration({uid, referredByUid});
  if (!inviter || inviterExists !== true) return {};
  if (!Number.isSafeInteger(now) || now < 0) fail("INVALID_ARGUMENT", "server time is invalid");
  return {referred: true, referredBy: inviter, referredAtMs: now};
}

module.exports = {referralFields, referralRegistration};
