"use strict";

const {createHash} = require("node:crypto");

/**
 * Сертификаты — выпуск, подписывание и верификация вне входа.
 *
 * Только успешный ТЕРМИНАЛЬНЫЙ финал разрешает факт выпуска; факт живёт и умирает в одной
 * транзакции с терминальным успехом. Подписание — за пределами любой транзакции, по C4: замок
 * (fence) с истечением, точные замороженные требования, CAS-публикация по паре
 * «идентичность выпуска + хеш требований». Отсутствующий ключ оставляет восстановимое
 * «ожидание подписи» — никогда неподписанную публикацию.
 *
 * Четыре правила (протокол §Certificate issuance and verification; ADR-0007):
 *
 * - **Иммутабельность.** Каждый выпуск — собственный аттестат с собственными датами:
 *   `expiresAt = issuedAt + год`. Перевыпуск не продлевает и не перезаписывает старый; поздняя
 *   коррекция вниз не трогает ни требования, ни даты, ни действительность.
 * - **Выбор профиля — по порядку заработка.** Последний ЗАРАБОТАННЫЙ выпуск отображается, даже
   если его проходной процент ниже предыдущего. Завершение подписи старого факта выбор не
 *   возвращает.
 * - **Ожидание подписи явно.** Пока подписи нет, факт существует с состоянием `PENDING`; ни один
 *   публичный снимок не публикуется без подписи.
 * - **Проверка подписанный-out.** Публичная верификация читает неизменяемый публичный снимок и
 *   политику статуса по СОБСТВЕННЫМ датам и ключам факта — никогда не изменяемые поля профиля,
 *   курса или скорректированный счёт.
 *
 * Конкретный провайдер подписи, алгоритм и секреты — Q-EX-5: фабрика принимает подписанта
 * снаружи (config/secret indirection), сама не связывает ни одного. Чистая половина тестируется
 * без firebase-admin.
 */

const ONE_YEAR_MS = 365 * 24 * 60 * 60 * 1000;
const CLAIMS_VERSION = 1;
const CERTIFICATES_COLLECTION = "exam_certificates";
const CERTIFICATE_PUBLIC_COLLECTION = "exam_certificate_public";
const SIGNING_WORK_COLLECTION = "exam_signing_work";
const KEY_MATERIAL_COLLECTION = "exam_certificate_keys";

const SIGNING_STATE = Object.freeze({
  PENDING: "PENDING",
  SIGNED: "SIGNED",
  FAILED_RECOVERABLE: "FAILED_RECOVERABLE",
});

const VERIFY_STATUS = Object.freeze({
  VALID: "valid",
  EXPIRED: "expired",
  PENDING: "pending_signing",
  UNKNOWN_KEY: "unknown_key",
  NOT_FOUND: "not_found",
});

function fail(code, message = code) {
  const error = new Error(message);
  error.code = code;
  throw error;
}

function canonicalJson(value) {
  if (value === null || typeof value !== "object") return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(",")}]`;
  return `{${Object.keys(value).sort().map((key) =>
    `${JSON.stringify(key)}:${canonicalJson(value[key])}`).join(",")}}`;
}

function digest(value) {
  return createHash("sha256").update(canonicalJson(value)).digest("hex");
}

/**
 * Требования (claims) выпуска — то, что подписывается и что видит проверяющий.
 *
 * Аттестат говорит об УСПЕШНОЙ попытке аккаунта, не о личности человека и не о аккредитации.
 * Скорректированный счёт сюда не попадает никогда: требования заморожены терминальным успехом.
 */
function issuanceClaims({uid, courseId, sittingId, resultVersion, percent, passMark,
  issuedAtMs, expiresAtMs, keyId, bankSnapshot}) {
  return {
    claimsVersion: CLAIMS_VERSION,
    subject: uid,
    courseId,
    sittingId,
    resultVersion,
    percent,
    passMark,
    issuedAtMs,
    expiresAtMs,
    keyId,
    bankSnapshotDigest: digest(bankSnapshot || {}),
  };
}

/**
 * Факт выпуска — создаётся ровно для одного успешного терминального финала.
 *
 * `issuanceId` выводится из владельца, сидинга и версии результата: ретрай терминальной
 * транзакции переигрывает тот же факт, а не второй. Один год действия от выпуска.
 */
function issuanceFact({uid, courseId, sittingId, resultVersion, percent, passMark,
  issuedAtMs, keyId, bankSnapshot}) {
  const issuedAt = Math.floor(Number(issuedAtMs) || 0);
  const expiresAtMs = issuedAt + ONE_YEAR_MS;
  const claims = issuanceClaims({uid, courseId, sittingId, resultVersion, percent, passMark,
    issuedAtMs: issuedAt, expiresAtMs, keyId, bankSnapshot});
  return {
    issuanceId: digest(["exam-issuance", uid, sittingId, resultVersion]),
    schemaVersion: 1,
    uid,
    courseId,
    sittingId,
    earningResultVersion: resultVersion,
    percent,
    passMark,
    issuedAtMs: issuedAt,
    expiresAtMs,
    keyId,
    bankSnapshot: bankSnapshot || {},
    claims,
    claimsDigest: digest(claims),
    signing: {state: SIGNING_STATE.PENDING, artifact: null, attempts: 0, fence: null,
      lastError: null, updatedAtMs: issuedAt},
  };
}

/** Работа подписывания — прочный хвост факта, отдельный документ того же ключа. */
function signingWorkRecord(fact) {
  return {
    issuanceId: fact.issuanceId,
    uid: fact.uid,
    state: SIGNING_STATE.PENDING,
    claimsDigest: fact.claimsDigest,
    keyId: fact.keyId,
    fence: null,
    fenceExpiresAtMs: null,
    attempts: 0,
    createdAtMs: fact.issuedAtMs,
    updatedAtMs: fact.issuedAtMs,
  };
}

/**
 * Выбор профиля: последний заработанный выпуск по ПОРЯДКУ ЗАРАБОТКА.
 *
 * Не по счёту, не по времени подписи и не по времени публикации — по моменту терминального
 * успеха (с версиями результата и идентичностью как тай-брейками). Позднее завершение подписи
 * старого факта порядок не двигает.
 */
function latestEarnedIssuance(issuances) {
  const list = (Array.isArray(issuances) ? issuances : []).filter(Boolean);
  if (list.length === 0) return null;
  return [...list].sort((a, b) =>
    (a.issuedAtMs - b.issuedAtMs) ||
    (a.earningResultVersion - b.earningResultVersion) ||
    (a.issuanceId < b.issuanceId ? -1 : 1)).at(-1);
}

/** Проекция профиля: выбранный выпуск и явное состояние подписи. */
function profileProjection(fact) {
  if (!fact) return {certificate: null};
  return {
    certificate: {
      issuanceId: fact.issuanceId,
      courseId: fact.courseId,
      percent: fact.percent,
      passMark: fact.passMark,
      issuedAtMs: fact.issuedAtMs,
      expiresAtMs: fact.expiresAtMs,
      signingState: fact.signing ? fact.signing.state : SIGNING_STATE.PENDING,
    },
  };
}

/**
 * Политика статуса верификации — по СОБСТВЕННЫМ датам и ключам факта.
 *
 * Нет публичного снимка — ожидание подписи или «нет такого»: ожидание честнее выдуманного
 * отказа. Ключ, чей материал не публиковался и не удерживается, — `unknown_key`, не «неверный».
 * Истёкший аттестат остаётся найденным: статус `expired` при неизменном снимке.
 */
function verifyVerdict({publicSnapshot, keyMaterial, nowMs}) {
  const now = Math.floor(Number(nowMs) || 0);
  if (!publicSnapshot) return {found: false, status: VERIFY_STATUS.NOT_FOUND};
  const knownKeys = keyMaterial && typeof keyMaterial === "object" ? keyMaterial : {};
  if (!(publicSnapshot.keyId in knownKeys)) {
    // Материал ключа ни активен, ни удержан: проверить подпись нечем — отдельный статус, а не
    // «подделка» и не «истёк».
    return {found: true, status: VERIFY_STATUS.UNKNOWN_KEY, snapshot: publicSnapshot};
  }
  if (now > publicSnapshot.expiresAtMs) {
    return {found: true, status: VERIFY_STATUS.EXPIRED, snapshot: publicSnapshot};
  }
  return {found: true, status: VERIFY_STATUS.VALID, snapshot: publicSnapshot};
}

/** Неизменяемый публичный снимок: требования плюс подпись, без приватного и без профиля. */
function publicSnapshot(fact, artifact) {
  return {...fact.claims, signature: artifact.signature, algorithm: artifact.algorithm};
}

/**
 * Фабрика подписывания. `signer` — подключаемый шов Q-EX-5: `{available(keyId), sign(claims,
 * keyId)}`. Материал ключей живёт в `exam_certificate_keys/{keyId}` (публикация ДО применения —
 * правило ротации), секреты приходят извне косвенной подстановкой; фабрика сама ничего не
 * связывает. Отсутствующий подписант или ключ — восстановимое ожидание.
 */
function createCertificateSigning({db, nowMs = () => Date.now(), signer = null}) {
  if (!db || typeof db.runTransaction !== "function") throw new TypeError("certificate signing requires db");
  const now = () => Math.floor(nowMs());

  /** Материал верификации по всем известным ключам: {keyId: true} — активен или удержан. */
  async function keyMaterialMap() {
    const snapshot = await db.collection(KEY_MATERIAL_COLLECTION).get();
    const map = {};
    for (const doc of snapshot.docs) map[doc.id] = (doc.data() || {}).retired !== true;
    return map;
  }

  /** Ротация: материал публикуется ДО того, как ключ подпишет первый новый выпуск. */
  async function publishVerificationMaterial({keyId, material}) {
    if (typeof keyId !== "string" || !keyId) fail("INVALID_ARGUMENT", "keyId is required");
    await db.doc(`${KEY_MATERIAL_COLLECTION}/${keyId}`).set({
      keyId, material: material || null, publishedAtMs: now(), retired: false,
    }, {merge: true});
  }

  /**
   * Захват замка подписывания: работа берётся с истекающим fence, чтобы параллельный
   * fulфиль не подписал тот же факт дважды. Возвращает fence или null (занято/завершено).
   */
  async function claimFence(issuanceId) {
    const fence = digest(["fence", issuanceId, now(), Math.random()]);
    return db.runTransaction(async (tx) => {
      const ref = db.doc(`${SIGNING_WORK_COLLECTION}/${issuanceId}`);
      const work = (await tx.get(ref)).data() || null;
      if (!work || work.state !== SIGNING_STATE.PENDING) return null;
      if (work.fence && Number(work.fenceExpiresAtMs) > now()) return null;
      tx.set(ref, {fence, fenceExpiresAtMs: now() + FENCE_MS, attempts: (work.attempts || 0) + 1,
        updatedAtMs: now()}, {merge: true});
      return fence;
    });
  }

  const FENCE_MS = 10 * 60 * 1000;

  /**
   * Исполнение подписывания: чтение замороженных требований, подпись ВНЕ транзакции,
   * CAS-публикация по (issuanceId, claimsDigest, fence). Первый подтверждённый артефакт
   * побеждает; байт-детерминированность подписи не предполагается.
   */
  async function fulfil({issuanceId, fence}) {
    if (typeof issuanceId !== "string" || !issuanceId || typeof fence !== "string" || !fence) {
      fail("INVALID_ARGUMENT", "issuanceId and fence are required");
    }
    const workRef = db.doc(`${SIGNING_WORK_COLLECTION}/${issuanceId}`);
    const work = (await workRef.get()).data() || null;
    if (!work) return {fulfilled: false, reason: "NO_WORK"};
    if (work.state !== SIGNING_STATE.PENDING) return {fulfilled: true, alreadyDone: true};
    if (work.fence !== fence || Number(work.fenceExpiresAtMs) <= now()) {
      return {fulfilled: false, reason: "FENCE_LOST"};
    }
    const factSnapshot = await db.doc(`${CERTIFICATES_COLLECTION}/${issuanceId}`).get();
    const fact = factSnapshot.data() || null;
    if (!fact) return {fulfilled: false, reason: "NO_FACT"};
    if (fact.claimsDigest !== work.claimsDigest) return {fulfilled: false, reason: "CLAIMS_DRIFT"};
    // Подписант отсутствует или ключ не выдан: восстановимое ожидание, не подпись-заглушка.
    if (!signer || typeof signer.sign !== "function" || !signer.available(fact.keyId)) {
      await workRef.set({state: SIGNING_STATE.PENDING, lastError: "KEY_UNAVAILABLE",
        updatedAtMs: now()}, {merge: true});
      return {fulfilled: false, reason: "KEY_UNAVAILABLE"};
    }
    // --- Подпись вне транзакции: приватный ключ и сеть не входят ни в одну транзакцию. ---
    let artifact;
    try {
      artifact = await signer.sign(fact.claims, fact.keyId);
      if (!artifact || typeof artifact.signature !== "string" || !artifact.signature ||
          typeof artifact.algorithm !== "string") fail("INVALID_ARTIFACT");
    } catch (error) {
      await workRef.set({state: SIGNING_STATE.FAILED_RECOVERABLE,
        lastError: "SIGNING_FAILED", updatedAtMs: now()}, {merge: true});
      return {fulfilled: false, reason: "SIGNING_FAILED"};
    }
    // Верификация перед публикацией — тот же подписант обязан уметь проверять свой артефакт.
    if (typeof signer.verify === "function") {
      const ok = signer.verify(publicSnapshot(fact, artifact), fact.keyId) === true;
      if (!ok) {
        await workRef.set({state: SIGNING_STATE.FAILED_RECOVERABLE,
          lastError: "VERIFY_FAILED", updatedAtMs: now()}, {merge: true});
        return {fulfilled: false, reason: "VERIFY_FAILED"};
      }
    }
    // CAS-публикация: тот же fence, тот же хеш требований; первый коммит побеждает.
    return db.runTransaction(async (tx) => {
      const currentWork = (await tx.get(workRef)).data() || null;
      if (!currentWork || currentWork.fence !== fence ||
          currentWork.claimsDigest !== work.claimsDigest) {
        return {fulfilled: false, reason: "FENCE_LOST"};
      }
      if (currentWork.state === SIGNING_STATE.SIGNED) {
        return {fulfilled: true, alreadyDone: true};
      }
      tx.set(workRef, {state: SIGNING_STATE.SIGNED, fence: null, fenceExpiresAtMs: null,
        lastError: null, updatedAtMs: now()}, {merge: true});
      tx.set(db.doc(`${CERTIFICATES_COLLECTION}/${issuanceId}`), {
        signing: {state: SIGNING_STATE.SIGNED, artifact, attempts: currentWork.attempts || 0,
          fence: null, lastError: null, updatedAtMs: now()},
      }, {merge: true});
      tx.create(db.doc(`${CERTIFICATE_PUBLIC_COLLECTION}/${issuanceId}`),
        publicSnapshot(fact, artifact));
      return {fulfilled: true, alreadyDone: false};
    });
  }

  return {claimFence, fulfil, publishVerificationMaterial, keyMaterialMap,
    FENCE_MS, SIGNING_STATE};
}

module.exports = {
  CERTIFICATE_PUBLIC_COLLECTION,
  CERTIFICATES_COLLECTION,
  CLAIMS_VERSION,
  KEY_MATERIAL_COLLECTION,
  ONE_YEAR_MS,
  SIGNING_STATE,
  SIGNING_WORK_COLLECTION,
  VERIFY_STATUS,
  createCertificateSigning,
  digest,
  issuanceClaims,
  issuanceFact,
  latestEarnedIssuance,
  profileProjection,
  publicSnapshot,
  signingWorkRecord,
  verifyVerdict,
};
