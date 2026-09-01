"use strict";

/**
 * Кто назначает `version` публичной сущности.
 *
 * До этого модуля версию назначали четыре разных места тремя разными способами: публикация дерева
 * ставила во все пять типов узлов число, присланное клиентом (`request.localRevision`), перекладка
 * квеста по полкам и снятие с полок делали read-modify-write `+1`, а агрегат рейтинга — атомарный
 * `FieldValue.increment(1)`. Три способа означают три разных ответа на один вопрос «какая версия
 * следующая», и клиент, сверяющий свою версию с серверной, получал бы разный ответ в зависимости от
 * того, какой путь записи сработал последним.
 *
 * AD-12: версию пишет только сервер. Значение, присланное клиентом, не участвует ни в одном узле —
 * иначе два устройства одного автора назначили бы одно и то же число двум разным редакциям, и
 * обнаружить расхождение стало бы нечем.
 *
 * Модуль чистый: ни firebase-admin, ни сети. Всё, что ему нужно от базы, приезжает аргументами —
 * прочитанное значение версии или фабрика атомарного инкремента.
 */

/**
 * Версия сущности, которой ещё нет.
 *
 * Ноль — «документа не было», единица — «первая опубликованная редакция». Ноль здесь — результат
 * чтения, а не значение на проводе: числа `0` клиент прислать не может, его модель требует
 * `serverRevision` либо отсутствующим, либо >= 1, и маппер превращает ноль в отсутствие. Поэтому
 * «я создаю впервые» выражается ОТСУТСТВИЕМ `expected_version`, а не нулём в нём: сверку с этим
 * нулём делает сервер, приравнивая отсутствующее ожидание к [NO_VERSION] (см. `versionVerdict`).
 * Явный ноль на проводе принимается и означает ровно то же самое — но ни один клиент его не шлёт.
 */
const NO_VERSION = 0;

/** Первая версия существующего документа. */
const FIRST_VERSION = 1;

/** Насколько версия растёт за одну запись. Одна запись — один шаг, всегда. */
const VERSION_STEP = 1;

/**
 * Текущая версия по прочитанным данным документа.
 *
 * Отсутствующий документ, отсутствующее поле и мусор в поле дают одно и то же: [NO_VERSION].
 * Отрицательное и дробное значение — мусор: версия монотонна и целочисленна, и молча продолжать
 * ряд от `-3.7` значит навсегда унаследовать чужую ошибку.
 */
function currentVersion(source) {
  const data = documentData(source);
  if (!data) return NO_VERSION;
  const parsed = Number(data.version);
  if (!Number.isFinite(parsed)) return NO_VERSION;
  if (parsed < NO_VERSION) return NO_VERSION;
  return Math.floor(parsed);
}

/**
 * Следующая версия — единственный ответ на этот вопрос во всём сервере.
 *
 * Принимает уже прочитанное текущее значение (число), а не снимок: так функцию можно позвать и из
 * транзакции, и из батча, и из места, где документ прочитан заранее пачкой.
 */
function nextVersion(current) {
  const parsed = Number(current);
  if (!Number.isFinite(parsed) || parsed < NO_VERSION) return FIRST_VERSION;
  return Math.floor(parsed) + VERSION_STEP;
}

/** Следующая версия по снимку документа: `nextVersion(currentVersion(snapshot))`. */
function nextVersionFor(source) {
  return nextVersion(currentVersion(source));
}

/*
 * Слепого инкремента здесь больше нет — намеренно.
 *
 * Он существовал ради одного вызова: агрегат рейтинга дописывал `version: FieldValue.increment(1)`
 * слиянием в квест, не читая её. Записывать версию, не зная текущей, можно только если ты и есть
 * единственный писатель, а рейтинг им не был: он двигал счётчик, который означает «редакция
 * содержимого», у документа, содержимое которого не менял. Оставить готовый помощник для такой
 * записи значит пригласить пятое место сделать то же самое ещё раз, поэтому его нет: версию
 * назначает тот, кто прочитал текущую ([nextVersion], [nextVersionAtPath], [setVersioned]).
 */

/**
 * Версии узлов по путям — то, чем батч публикации заменяет невозможное для себя чтение.
 *
 * Батч писать умеет, читать нет. Поэтому узлы дерева читаются пачкой до батча, а сюда приезжает
 * результат чтения: путь -> текущая версия. Узла, которого не было, в карте просто нет, и
 * [nextVersion] даст ему [FIRST_VERSION].
 *
 * @param {Array<object>} snapshots снимки с `ref.path` (или `path`), `exists` и `data()`.
 */
function versionsByPath(snapshots) {
  const versions = {};
  for (const snapshot of snapshots || []) {
    const path = snapshotPath(snapshot);
    if (!path) continue;
    versions[path] = currentVersion(snapshot);
  }
  return versions;
}

/**
 * Значение поля `version` для узла дерева по карте из [versionsByPath].
 *
 * Своя версия у каждого узла, а не одно число на всё дерево: правка одного урока не обязана
 * двигать версию соседнего, иначе клиент перекачивал бы всё дерево на каждую запятую.
 */
function nextVersionAtPath(versions, path) {
  const known = versions && Object.prototype.hasOwnProperty.call(versions, path) ? versions[path] : NO_VERSION;
  return nextVersion(known);
}

/**
 * Пишет документ с назначенной сервером версией.
 *
 * Один и тот же вызов подходит батчу и транзакции — обоим нужен `set(ref, data, options)`. Поле
 * `version` дописывается после тела: если тело всё-таки принесло своё значение (например, старый
 * клиент прислал `localRevision` под тем же именем), оно будет затёрто, а не пропущено.
 */
function setVersioned(writer, ref, data, current, options) {
  if (!writer || typeof writer.set !== "function") {
    throw new TypeError("setVersioned requires a batch or a transaction");
  }
  const assigned = nextVersion(current);
  const body = {...(data || {}), version: assigned};
  if (options === undefined) {
    writer.set(ref, body);
  } else {
    writer.set(ref, body, options);
  }
  return assigned;
}

function documentData(source) {
  if (source === null || source === undefined) return null;
  if (typeof source.exists === "boolean" && !source.exists) return null;
  if (typeof source.data === "function") return source.data() || null;
  if (typeof source === "object") return source;
  return null;
}

function snapshotPath(snapshot) {
  if (!snapshot) return "";
  if (snapshot.ref && typeof snapshot.ref.path === "string") return snapshot.ref.path;
  if (typeof snapshot.path === "string") return snapshot.path;
  return "";
}

module.exports = {
  FIRST_VERSION,
  NO_VERSION,
  VERSION_STEP,
  currentVersion,
  nextVersion,
  nextVersionAtPath,
  nextVersionFor,
  setVersioned,
  versionsByPath,
};
