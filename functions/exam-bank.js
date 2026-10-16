"use strict";

const {createHash} = require("node:crypto");
const {canonicalQuestionId, questionAllocatedSeconds} = require("./lesson-reward");
const {buildWorthBasis} = require("./exam-reward");
const {examAnswerSeconds, THEME_DRAW_EASY, THEME_DRAW_HARD, FINAL_DRAW_HARD} = require("./exam-scoring");

/**
 * Экзаменационный банк — одобренный пул вопросов для набора сидинга.
 *
 * Тема: все подходящие вопросы ниже темы (5 EASY + 15 HARD, смешанный набор). Финал: все
 * подходящие вопросы текущего курса (20 HARD, последний одобренный курс) плюс подтверждение
 * сертификационной пригодности курса. Здесь живёт единственное место, где жребий встречается с
 * деньгами: набор выбирается случайно, а СТОИМОСТЬ попытки — нет.
 *
 * Четыре правила, ради которых файл написан именно так (протокол §Admission/draw, C1):
 *
 * - **Каноническое схлопывание.** Переводы (`q1__ru`, `q1__en`) — один канонический вопрос
 *   (`canonicalQuestionId`). Архивное, карантинное и неодобренное в пул не попадает; Survey
 *   исключён из наборов assessment всегда.
 * - **Основа выплаты — обычное время.** Стоимости считаются по ОБЫЧНОМУ отведённому времени
 *   каждого вопроса (`questionAllocatedSeconds`: EASY 0.36, HARD 0.24) по всему пулу, среднее ×
 *   размер набора, поэтому жребий цену не двигает. Ответное время сидинга считается отдельно и
 *   по all-HARD правилу (`examAnswerSeconds`) — база выплаты от таймера не зависит.
 * - **Источник стоимости один на канонический вопрос.** Из доступных редакций канонического
 *   вопроса публикация выбирает одну: детерминированно, минимальным идентификатором документа.
 *   Выбор замораживается манифестом сидинга; язык игрока, повтор и порядок выдачи на цену не
 *   влияют. Двусмысленная основа — отказ ДО списания, а не нулевая цена.
 * - **Сертификация пересчитывается, а не инкрементируется.** Пригодность курса к финалу —
 *   «≥12 тем с ≥100 EASY + ≥100 HARD доступными вопросами» — каждый раз выводится из
 *   одобренных банков заново: повторная публикация неспособна накрутить счётчик.
 *
 * Чистый модуль: Firestore подводит фабрика (`db`, `loadLessonContext` — каноническое
 * опубликованное+одобренное доказательство урока из `index.js`), всё остальное — функции.
 */

/** Ограничители чтения: курс или тема крупнее этих чисел недоступны экзамену, а не бесконечны. */
const MAX_SECTIONS_PER_COURSE = 64;
const MAX_THEMES_PER_COURSE = 64;
const MAX_LESSONS_PER_THEME = 64;
const MAX_QUESTIONS_PER_LESSON = 200;

/** Границы набора протокола. */
const THEME_POOL_MIN_EASY = 5;
const THEME_POOL_MIN_HARD = 15;
const FINAL_POOL_MIN_HARD = 20;

/** Предел Firestore на значения одного `in`: крупнее — батчами, объединёнными в результат. */
const MAX_VALUES_PER_IN = 30;

/** Сертификационные пороги курса: тем, у каждой ≥100 EASY + ≥100 HARD. */
const CERTIFICATION_MIN_THEMES = 12;
const CERTIFICATION_MIN_EASY_PER_THEME = 100;
const CERTIFICATION_MIN_HARD_PER_THEME = 100;

const CONTENT_TYPE_SURVEY = "Survey";

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

/** Строка вопроса `questions/`: что банк читает, до всякой оценки. */
function parseQuestionRow(doc) {
  const row = doc.data() || {};
  if (row.archived === true) return null;
  let content;
  try {
    content = typeof row.payload === "string" ? JSON.parse(row.payload) : row.payload;
  } catch (_) {
    return null;
  }
  if (!content || typeof content !== "object" || Array.isArray(content)) return null;
  const difficulty = String(row.difficulty || content.difficulty || "EASY").toUpperCase();
  return {
    questionId: doc.id,
    lessonId: row.lessonId || "",
    version: Number.isSafeInteger(row.version) ? row.version : 0,
    language: typeof row.language === "string" ? row.language : "",
    difficulty,
    content,
    survey: content.type === CONTENT_TYPE_SURVEY,
  };
}

/**
 * Канонический пул одной сложности.
 *
 * Переводы схлопываются, Survey и архив уходят; источник стоимости — минимальный идентификатор
 * документа среди доступных редакций канонического вопроса. Выход отсортирован по каноническому
 * идентификатору, чтобы манифест и его хеш не зависели от порядка чтения.
 */
function collectDifficultyPool(rows, difficulty) {
  const byCanonical = new Map();
  for (const row of rows) {
    if (!row || row.survey) continue;
    if (row.difficulty !== difficulty) continue;
    const canonical = canonicalQuestionId(row.questionId);
    const existing = byCanonical.get(canonical);
    if (!existing || row.questionId < existing.questionId) byCanonical.set(canonical, row);
  }
  return [...byCanonical.keys()].sort().map((canonical) => {
    const row = byCanonical.get(canonical);
    return {
      canonicalId: canonical,
      questionId: row.questionId,
      lessonId: row.lessonId,
      revision: row.version,
      difficulty,
      // Обычное время — база выплаты; all-HARD ответное время — заморозка сидинга. Разные правила
      // посказанно рядом: пять EASY-позиций темы стоят EASY и отвечают по HARD.
      worthSeconds: questionAllocatedSeconds(row.content, difficulty === "HARD"),
      answerSeconds: examAnswerSeconds(row.content, difficulty === "HARD"),
      content: row.content,
    };
  });
}

/** Канонический пул темы: {easy: [...], hard: [...]} с обеими сложностями. */
function collectPool(rows) {
  return {
    easy: collectDifficultyPool(rows, "EASY"),
    hard: collectDifficultyPool(rows, "HARD"),
  };
}

/**
 * Проходит ли пул границе набора своей оценки.
 *
 * Неизвестное — не проход: тема без 5 EASY или 15 HARD и финал без 20 HARD отказывают ДО
 * списания (`PoolTooSmall`), а не рисуют укороченный набор.
 */
function poolGate(mode, pool) {
  if (mode === "ThemeTest") {
    return pool.easy.length >= THEME_POOL_MIN_EASY && pool.hard.length >= THEME_POOL_MIN_HARD;
  }
  if (mode === "FinalExam") return pool.hard.length >= FINAL_POOL_MIN_HARD;
  return false;
}

/**
 * Стоимостная основа оценки по каноническому пулу.
 *
 * Суммы времени — по ВСЕМУ пулу каждой сложности, среднее × размер набора: жребий не двигает
 * цену. Основа обязана пройти обыкновенные минимумы вопроса (C1: тема TE>=25/TH>=75, финал
 * TE=0/TH>=100) — иначе двусмысленность отказывает до списания.
 */
function worthBasisFor(mode, pool) {
  return buildWorthBasis({
    mode,
    poolEasySeconds: pool.easy.map((entry) => entry.worthSeconds),
    poolHardSeconds: pool.hard.map((entry) => entry.worthSeconds),
  });
}

/**
 * Смешанный набор темы: 5 EASY + 15 HARD, случайно по `rank`, канонические идентичности без
 * повторов. `rank(key)` — серверная энтропия вызова (как у арены): один вызов — один жребий.
 */
function drawThemeSet(pool, rank) {
  if (!poolGate("ThemeTest", pool)) fail("POOL_TOO_SMALL");
  return {
    easy: drawFrom(pool.easy, THEME_DRAW_EASY, rank),
    hard: drawFrom(pool.hard, THEME_DRAW_HARD, rank),
  };
}

/** Набор финала: 20 HARD. */
function drawFinalSet(pool, rank) {
  if (!poolGate("FinalExam", pool)) fail("POOL_TOO_SMALL");
  return {easy: [], hard: drawFrom(pool.hard, FINAL_DRAW_HARD, rank)};
}

function drawFrom(entries, count, rank) {
  return [...entries].sort((a, b) => (rank(a.canonicalId) < rank(b.canonicalId) ? -1 : 1))
    .slice(0, count);
}

/**
 * Манифест сидинга: слоты в порядке выдачи (пять EASY, затем пятнадцать HARD / двадцать HARD),
 * каждый с точной редакцией и обеими секундами. Порядок фиксируется манифестом — свидетельство
 * коррекции адресуется индексами исходных слотов.
 */
function buildSittingManifest({mode, drawn}) {
  const ordered = mode === "ThemeTest" ?
    [...drawn.easy.map((entry) => ({...entry, shownDifficulty: "EASY"})),
      ...drawn.hard.map((entry) => ({...entry, shownDifficulty: "HARD"}))] :
    drawn.hard.map((entry) => ({...entry, shownDifficulty: "HARD"}));
  const slots = ordered.map((entry, index) => ({
    index,
    canonicalId: entry.canonicalId,
    questionId: entry.questionId,
    lessonId: entry.lessonId,
    revision: entry.revision,
    difficulty: entry.difficulty,
    shownDifficulty: entry.shownDifficulty,
    worthSeconds: entry.worthSeconds,
    answerSeconds: entry.answerSeconds,
  }));
  return {
    mode,
    slots,
    // Приватный отпечаток банка: приватные содержимые в хеш не входят, только идентичности и
    // числа. Публичных данных манифест не несёт — ключи и канонические ответы живут в сидинге.
    manifestHash: digest({mode, slots}),
  };
}

/** Итог сертификации курса: тем с полным пулом достаточно? Пересчитано, не накоплено. */
function courseCertification(themeSummaries) {
  const qualified = (Array.isArray(themeSummaries) ? themeSummaries : [])
    .filter((summary) => summary.easyDistinct >= CERTIFICATION_MIN_EASY_PER_THEME &&
      summary.hardDistinct >= CERTIFICATION_MIN_HARD_PER_THEME);
  return {
    qualifiedThemes: qualified.length,
    requiredThemes: CERTIFICATION_MIN_THEMES,
    eligible: qualified.length >= CERTIFICATION_MIN_THEMES,
    themes: qualified.map((summary) => summary.themeId),
  };
}

/**
 * Читатели банков. `loadLessonContext(transaction, lessonId)` — тот же канонический
 * опубликованный+одобренный доказатель, что кормит арену; строки вопросов читаются по
 * `lessonId`. Всё чтение — в транзакции старта, поэтому набор замораживается против
 * публикации-соперника.
 */
function createExamBank({db, loadLessonContext}) {
  if (!db || typeof db.runTransaction !== "function" || typeof loadLessonContext !== "function") {
    throw new TypeError("exam bank requires db and loadLessonContext");
  }
  async function readQuestions(tx, lessonIds) {
    const rows = [];
    for (const lessonId of lessonIds) {
      const snapshot = await tx.get(db.collection("questions").where("lessonId", "==", lessonId)
        .limit(MAX_QUESTIONS_PER_LESSON + 1));
      if (snapshot.docs.length > MAX_QUESTIONS_PER_LESSON) fail("BANK_TOO_LARGE", lessonId);
      for (const doc of snapshot.docs) {
        const row = parseQuestionRow(doc);
        if (row) rows.push(row);
      }
    }
    return rows;
  }

  /** Уроки темы с aprovленным доказательством; неодобренные и архивные уроки не попадают. */
  async function readThemeLessons(tx, themeId) {
    const snapshot = await tx.get(db.collection("lessons").where("themeId", "==", themeId)
      .limit(MAX_LESSONS_PER_THEME + 1));
    if (snapshot.docs.length > MAX_LESSONS_PER_THEME) fail("BANK_TOO_LARGE", themeId);
    const contexts = [];
    for (const doc of snapshot.docs) {
      const context = await loadLessonContext(tx, doc.id);
      if (context && context.published && context.reviewApproved) contexts.push(context);
    }
    return contexts;
  }

  /** Пул темы целиком; сырые строки возвращаются рядом — курс собирает свой пул из них же. */
  async function themePool(tx, themeId) {
    const contexts = await readThemeLessons(tx, themeId);
    const rawRows = await readQuestions(tx, contexts.map((context) => context.lessonId));
    return {themeId, lessons: contexts, pool: collectPool(rawRows), rawRows};
  }

  /**
   * Пул курса: последний одобренный курс (у финала активный сидинг держит заморозку; новый
   * старт всегда читает текущее дерево), темы курса с их пулами и сертификационная сводка.
   */
  /** Темы по списку секций: `in` батчами по ≤30 идентификаторов, результаты слиты. */
  async function readThemesBySections(tx, sectionIds) {
    const docs = [];
    for (let start = 0; start < sectionIds.length; start += MAX_VALUES_PER_IN) {
      const chunk = sectionIds.slice(start, start + MAX_VALUES_PER_IN);
      const snapshot = await tx.get(db.collection("themes").where("sectionId", "in", chunk)
        .limit(MAX_THEMES_PER_COURSE + 1 - docs.length));
      docs.push(...snapshot.docs);
      if (docs.length > MAX_THEMES_PER_COURSE) break;
    }
    // Дубликаты невозможны (секция не повторяется в списке), порядок — по батчам чтения.
    return {docs};
  }

  async function coursePool(tx, courseId) {
    const questSnapshot = await tx.get(db.doc(`quests/${courseId}`));
    // Вышедший из архива курс: не найден для целей экзамена — платный финал не рисуется.
    if (!questSnapshot.exists) fail("COURSE_NOT_FOUND");
    const quest = questSnapshot.data() || {};
    if (quest.archived === true) fail("COURSE_NOT_FOUND");
    const sectionsSnapshot = await tx.get(db.collection("sections").where("questId", "==", courseId)
      .limit(MAX_SECTIONS_PER_COURSE + 1));
    if (sectionsSnapshot.docs.length > MAX_SECTIONS_PER_COURSE) fail("BANK_TOO_LARGE", courseId);
    const themesSnapshot = sectionsSnapshot.docs.length === 0 ? {docs: []} :
      await readThemesBySections(tx, sectionsSnapshot.docs.map((doc) => doc.id));
    if (themesSnapshot.docs.length > MAX_THEMES_PER_COURSE) fail("BANK_TOO_LARGE", courseId);
    const archivedThemes = new Set(themesSnapshot.docs
      .filter((doc) => (doc.data() || {}).archived === true).map((doc) => doc.id));
    const themeSummaries = [];
    const rawRows = [];
    for (const themeDoc of themesSnapshot.docs) {
      if (archivedThemes.has(themeDoc.id)) continue;
      const pool = await themePool(tx, themeDoc.id);
      themeSummaries.push({
        themeId: themeDoc.id,
        easyDistinct: pool.pool.easy.length,
        hardDistinct: pool.pool.hard.length,
      });
      rawRows.push(...pool.rawRows);
    }
    return {
      courseId,
      quest,
      themeIds: themesSnapshot.docs.map((doc) => doc.id).filter((id) => !archivedThemes.has(id)),
      themeSummaries,
      certification: courseCertification(themeSummaries),
      pool: {easy: [], hard: collectDifficultyPool(rawRows, "HARD")},
    };
  }

  return {themePool, coursePool, readQuestions, readThemeLessons};
}

module.exports = {
  CERTIFICATION_MIN_EASY_PER_THEME,
  MAX_VALUES_PER_IN,
  CERTIFICATION_MIN_HARD_PER_THEME,
  CERTIFICATION_MIN_THEMES,
  CONTENT_TYPE_SURVEY,
  FINAL_DRAW_HARD,
  FINAL_POOL_MIN_HARD,
  MAX_LESSONS_PER_THEME,
  MAX_QUESTIONS_PER_LESSON,
  MAX_SECTIONS_PER_COURSE,
  MAX_THEMES_PER_COURSE,
  THEME_DRAW_EASY,
  THEME_DRAW_HARD,
  THEME_POOL_MIN_EASY,
  THEME_POOL_MIN_HARD,
  buildSittingManifest,
  collectDifficultyPool,
  collectPool,
  courseCertification,
  createExamBank,
  digest,
  drawFinalSet,
  drawThemeSet,
  parseQuestionRow,
  poolGate,
  worthBasisFor,
};
