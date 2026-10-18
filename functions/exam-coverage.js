"use strict";

const {createHash} = require("node:crypto");
const {canonicalQuestionId} = require("./lesson-reward");

/**
 * Подтверждённое покрытие C3 — фундамент допуска к теме.
 *
 * Тема открывается только тому, кто ПОДТВЕРЖДЁННО прошёл каждый требуемый EASY-вопрос каждого
 * урока темы. Не «две звезды», не «идеальные двадцать», не покупка и не квитанция об отправке —
 * союз точных канонических токенов (questionId, revision) по засчитанным обычным попыткам, при
 * барьере материализации.
 *
 * Три правила (C3; протокол §Ordinary progression):
 *
 * - **Манифест — из опубликованного содержимого.** Требуемые токены урока выводятся из его
 *   одобренных EASY-вопросов (Survey исключён: у него нет правильного ответа), хеш манифеста
 *   связывает проекцию покрытия с той редакцией урока, под которую доказательство собрано.
 *   Пустой или неизвестный манифест — недоступность, а не «вакуольно завершено».
 * - **Союз по попыткам, полная зачётность.** Токен подтверждён только терминальной ЗАЧТЁНОЙ
 *   обычной попыткой с серверной квитанцией и полной правильностью слота (цифра 9); повторные
 *   токены считаются один раз, поздние ошибки не стирают заработанное. Попытки без достоверного
 *   перечня показанного (наследие) остаются неизвестным происхождением — ожиданием, а не нулём
 *   и не отказом.
 * - **Барьер материализации.** Подтверждённые токены становятся допуском только документом
 *   материализации, проверенным против ПОЛНОГО текущего манифеста. Загруженное, но не
 *   проглоченное — `ResultsNotSynced`; ничего не заработано — `Locked`; нет манифеста —
 *   `Unavailable`. Обычный офлайн-прогресс это не задевает: материализация — отдельная
 *   проекция, которую двигает экзаменационный worker, а не приёмник обычных прохождений.
 *
 * Чистый модуль: тестируется без firebase-admin.
 */

const COVERAGE_SCHEMA_VERSION = 1;

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

/** Идентичность проекции покрытия пользователя по уроку. */
function coverageDocumentId(uid, lessonId) {
  return digest(["exam-coverage", uid, lessonId]);
}

/** Идентичность состояния допуска/результатов оценки. */
function assessmentStateId(uid, mode, assessmentId) {
  return digest(["exam-assessment", uid, mode, assessmentId]);
}

/**
 * Требуемый манифест EASY-токенов урока из опубликованных строк вопросов.
 *
 * Токен — канонический (questionId, revision) EASY-вопроса без Survey. Пустой урок обязан быть
 * назван публикацией явно: здесь пустой набор — «манифеста нет», недоступность.
 *
 * @param lessonId идентичность урока
 * @param rows опубликованные строки `questions/` урока (см. `exam-bank.parseQuestionRow`)
 * @returns {{lessonId, schemaVersion, requiredTokens, manifestHash}|null} null — манифеста нет
 */
function requiredManifest(lessonId, rows) {
  const list = Array.isArray(rows) ? rows : [];
  const tokens = new Map();
  for (const row of list) {
    if (!row || row.survey) continue;
    if (row.difficulty !== "EASY") continue;
    // Банк называет редакцию `revision`, сырая строка `parseQuestionRow` — `version`: принимаем
    // оба написания, требуем одно значение.
    const revision = Number.isSafeInteger(row.revision) ? row.revision :
      Number.isSafeInteger(row.version) ? row.version : 0;
    if (revision < 1) continue;
    const canonical = canonicalQuestionId(row.questionId);
    const token = {questionId: canonical, revision};
    tokens.set(`${token.questionId}\u0000${token.revision}`, token);
  }
  if (tokens.size === 0) return null;
  const requiredTokens = [...tokens.values()]
    .sort((a, b) => (a.questionId < b.questionId ? -1 : a.questionId > b.questionId ? 1 : a.revision - b.revision));
  return {
    lessonId,
    schemaVersion: COVERAGE_SCHEMA_VERSION,
    requiredTokens,
    manifestHash: digest({lessonId, schemaVersion: COVERAGE_SCHEMA_VERSION, requiredTokens}),
  };
}

/**
 * Подтверждённые токены пользователя по его обычным попыткам.
 *
 * Попытка говорит о покрытии только если: квитанция `SETTLED`, попытка завершена
 * (`completed === true`), и она несёт достоверный перечень показанного (`served` — вопрос,
 * редакция, сложность; цифры `codeAnswer` по слотам). Наследие без перечня — не ноль и не
 * доказательство: оно помечает пользователя ожидающим (`unknownProvenance`), и допуск ждёт
 * восстановимого свидетельства, а не отказывает по нулю.
 *
 * @returns {{confirmedTokens: Map<string, {questionId, revision}>, unknownProvenance: boolean}}
 */
function confirmedCoverage(attempts) {
  const confirmed = new Map();
  let unknownProvenance = false;
  for (const attempt of Array.isArray(attempts) ? attempts : []) {
    if (!attempt || typeof attempt !== "object") continue;
    if (attempt.completed !== true) continue;
    const receipt = attempt.settlementReceipt;
    if (!receipt || receipt.status !== "SETTLED") continue;
    const served = Array.isArray(attempt.served) ? attempt.served : null;
    const codeAnswer = typeof attempt.codeAnswer === "string" ? attempt.codeAnswer : null;
    if (!served || !codeAnswer || served.length !== codeAnswer.length) {
      // Наследие или усечённое свидетельство: не подтверждение и не вина игрока.
      unknownProvenance = true;
      continue;
    }
    served.forEach((slot, index) => {
      if (!slot || typeof slot.questionId !== "string") return;
      if (String(slot.difficulty || "").toUpperCase() !== "EASY") return;
      if (!Number.isSafeInteger(slot.revision) || slot.revision < 1) return;
      if (codeAnswer[index] !== "9") return; // Полная зачётность, как у обычного обучения.
      const canonical = canonicalQuestionId(slot.questionId);
      confirmed.set(`${canonical}\u0000${slot.revision}`, {questionId: canonical, revision: slot.revision});
    });
  }
  return {confirmedTokens: confirmed, unknownProvenance};
}

/**
 * Вердикт покрытия одного урока — чистая функция допуска.
 *
 * @param manifest требуемый манифест ({@link requiredManifest}) или null
 * @param materialization документ материализации или null
 * @param confirmed {confirmedTokens, unknownProvenance} из {@link confirmedCoverage}
 * @returns {{state: "ok"|"Locked"|"ResultsNotSynced"|"Unavailable", missingTokens: number}}
 */
function coverageVerdict(manifest, materialization, confirmed) {
  if (!manifest) return {state: "Unavailable", missingTokens: 0};
  const tokens = confirmed && confirmed.confirmedTokens instanceof Map ?
    confirmed.confirmedTokens : new Map();
  let missing = 0;
  for (const token of manifest.requiredTokens) {
    if (!tokens.has(`${token.questionId}\u0000${token.revision}`)) missing += 1;
  }
  if (missing > 0) {
    // Заработано хоть что-то по этому уроку (или есть неизвестное наследие) — значит, прогресс
    // есть и, возможно, ещё не проглочен: ожидание синхронизации, а не замок.
    const evidence = tokens.size > 0 || (confirmed && confirmed.unknownProvenance === true);
    return {state: evidence ? "ResultsNotSynced" : "Locked", missingTokens: missing};
  }
  const materialized = materialization &&
    materialization.schemaVersion === COVERAGE_SCHEMA_VERSION &&
    materialization.lessonId === manifest.lessonId &&
    materialization.manifestHash === manifest.manifestHash &&
    materialization.complete === true;
  // Токены собраны, но барьер ещё не прошёл — uploads-ack недостаточен по определению.
  return {state: materialized ? "ok" : "ResultsNotSynced", missingTokens: 0};
}

/**
 * План материализации: документ, который worker вправе записать.
 *
 * Материализация обязана видеть ПОЛНЫЙ текущий манифест: частичное покрытие не публикуется.
 * Повтор — та же запись (идентичность выводится из пользователя и урока), смена манифеста —
 * новая запись поверх старой: неизменённые токены сохраняют доказательство, новые требуют
 * свежего.
 */
function materializationPlan({uid, manifest, confirmedTokens, nowMs}) {
  if (!manifest) fail("COVERAGE_UNAVAILABLE");
  const tokens = confirmedTokens instanceof Map ? confirmedTokens : new Map();
  for (const token of manifest.requiredTokens) {
    if (!tokens.has(`${token.questionId}\u0000${token.revision}`)) {
      fail("COVERAGE_INCOMPLETE", `${manifest.lessonId}: missing ${token.questionId}@${token.revision}`);
    }
  }
  return {
    id: coverageDocumentId(uid, manifest.lessonId),
    schemaVersion: COVERAGE_SCHEMA_VERSION,
    uid,
    lessonId: manifest.lessonId,
    manifestHash: manifest.manifestHash,
    requiredCount: manifest.requiredTokens.length,
    complete: true,
    confirmedAtMs: nowMs,
  };
}

/**
 * Реестр пройденных тем курса — ключ (courseId, themeId) стабилен против правок вопросов.
 *
 * Состояние темы несёт агрегат темы: лучший процент, прохождение, поколение агрегата. Архивные
 * темы допуску финала не требуются; новая обязательная тема требует прохода.
 *
 * @param themeStates карты themeId → состояние допуска темы
 * @param requiredThemeIds текущие необязательные (неархивные) темы курса
 */
function themePassRegistry(themeStates, requiredThemeIds) {
  const states = themeStates instanceof Map ? themeStates : new Map(Object.entries(themeStates || {}));
  const required = Array.isArray(requiredThemeIds) ? requiredThemeIds : [];
  const missing = [];
  for (const themeId of required) {
    const state = states.get(themeId);
    if (!state || state.passed !== true) missing.push(themeId);
  }
  return {required, missing, complete: missing.length === 0};
}

module.exports = {
  COVERAGE_SCHEMA_VERSION,
  assessmentStateId,
  confirmedCoverage,
  coverageDocumentId,
  coverageVerdict,
  digest,
  materializationPlan,
  requiredManifest,
  themePassRegistry,
};
