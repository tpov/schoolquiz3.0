"use strict";

/**
 * Страж пакета деплоя: всё, что серверу нужно на загрузке, лежит внутри каталога `functions`.
 *
 * `firebase.json` объявляет `"functions": [{"source": "functions"}]` — в архив деплоя уезжает
 * ровно этот каталог и ничего выше. `require("../что-нибудь")` при этом отлично работает локально
 * (репозиторий на диске целиком) и не работает в облаке: каталога выше там нет. Отказ приходит не
 * поштучно — не загружается `index.js`, а с ним весь кодбейс: приёмник мутаций `submitMutation`
 * (единственный транспорт отложенных операций, AD-6), приём прохождений, публикация, push. Для
 * игрока это «сервера нет» с первого же деплоя, и ни один обычный тест этого не видит, потому что
 * `npm test` запускается из репозитория, где путь наверх существует.
 *
 * Поэтому проверка здесь ровно одна и настоящая: каталог `functions` копируется во временное место
 * БЕЗ соседей и без родителя, и модуль грузится оттуда. Пропал файл из пакета — прогон красный.
 *
 * Загрузить так `index.js` нельзя: он поднимает firebase-admin и требует учётные данные (тот же
 * довод, что в `arena-request-document.test.js`). Поэтому граф `require` разбирается статически.
 */

const assert = require("assert");
const fs = require("fs");
const os = require("os");
const path = require("path");

const PACKAGE_ROOT = __dirname;

/**
 * Модули, которые сегодня читают файл снаружи пакета и потому в облако не уедут.
 *
 * Список — не разрешение, а запись долга: каждая строка означает «деплой этого кодбейса сегодня
 * упадёт». Держится он здесь, а не в отчёте, потому что читать его будет тот, кто соберётся
 * деплоить. Владельцу файла — починить и удалить строку.
 */
// Пусто, и пусть остаётся пустым: список заводился под один известный долг — экономика читала
// объявление из корня, — и долг закрыт переносом файла внутрь пакета. Строка здесь означает
// «этот файл роняет выкат, и мы про это знаем», то есть гейт перестаёт быть гейтом.
const KNOWN_UNPACKAGED = new Map();

// ─── статический разбор ────────────────────────────────────────────────────────────────────────

/**
 * Комментарии прочь: путь, названный в пояснении («так было раньше и так нельзя»), — не require.
 * Достаточно грубо для здешнего стиля: блочные комментарии и строчные `//`.
 */
function withoutComments(source) {
  return source.replace(/\/\*[\s\S]*?\*\//g, " ").replace(/(^|[^:])\/\/[^\n]*/g, "$1");
}

/** Все `require("...")` с относительным путём в файле. */
function relativeRequires(source) {
  const found = [];
  const code = withoutComments(source);
  const pattern = /require\(\s*"(\.[^"]*)"\s*\)/g;
  let match = pattern.exec(code);
  while (match) {
    found.push(match[1]);
    match = pattern.exec(code);
  }
  return found;
}

function runtimeModules() {
  return fs
    .readdirSync(PACKAGE_ROOT)
    .filter((name) => name.endsWith(".js") && !name.endsWith(".test.js"))
    .sort();
}

/** Указывает ли относительный путь наружу каталога пакета. */
function escapesPackage(fromFile, target) {
  const resolved = path.resolve(path.dirname(fromFile), target);
  const relative = path.relative(PACKAGE_ROOT, resolved);
  return relative.startsWith("..") || path.isAbsolute(relative);
}

// ─── копия одного лишь пакета ─────────────────────────────────────────────────────────────────

function copyPackageWithout(names) {
  const skip = new Set(["node_modules", ...names]);
  const destination = fs.mkdtempSync(path.join(os.tmpdir(), "functions-package-"));
  for (const entry of fs.readdirSync(PACKAGE_ROOT)) {
    if (skip.has(entry)) continue;
    fs.cpSync(path.join(PACKAGE_ROOT, entry), path.join(destination, entry), {recursive: true});
  }
  return destination;
}

function loadFromCopy(destination, moduleName) {
  const target = path.join(destination, moduleName);
  delete require.cache[require.resolve(target)];
  return require(target);
}

// ─── проверки ─────────────────────────────────────────────────────────────────────────────────

function testTheQueueLoadsFromThePackageAlone() {
  const copy = copyPackageWithout([]);
  const queue = loadFromCopy(copy, "mutation-queue.js");
  const declared = require("./config/sync-params.json");

  assert.strictEqual(
    queue.MUTATION_KEY_TTL_MS,
    declared.mutationKeyTtlMs,
    "срок хранения ключа в копии пакета разошёлся с объявлением",
  );
  assert.strictEqual(
    queue.QUEUE_RECORD_MAX_AGE_MS,
    declared.queueRecordMaxAgeMs,
    "предельный возраст записи очереди в копии пакета разошёлся с объявлением",
  );
  assert.ok(
    queue.MUTATION_KEY_TTL_MS > queue.QUEUE_RECORD_MAX_AGE_MS,
    "AD-1: срок хранения ключа обязан быть строго больше предельного возраста записи очереди",
  );
}

function testTheDeclarationTravelsWithTheCode() {
  // Объявление пары сроков — не сосед пакета, а его часть: иначе в облаке модуль очереди не
  // соберётся, и падение будет не в очереди, а во всём кодбейсе.
  assert.ok(
    fs.existsSync(path.join(PACKAGE_ROOT, "config", "sync-params.json")),
    "functions/config/sync-params.json пропал: пара сроков снова снаружи пакета деплоя",
  );

  const copy = copyPackageWithout(["config"]);
  assert.throws(
    () => loadFromCopy(copy, "mutation-queue.js"),
    /Cannot find module/,
    "модуль очереди обязан падать без объявления сроков, а не подставлять своё число",
  );
}

function testNoRuntimeModuleReadsAboveThePackage() {
  const offenders = [];
  for (const name of runtimeModules()) {
    const file = path.join(PACKAGE_ROOT, name);
    const source = fs.readFileSync(file, "utf8");
    for (const target of relativeRequires(source)) {
      if (!escapesPackage(file, target)) continue;
      if (KNOWN_UNPACKAGED.has(name)) continue;
      offenders.push(`${name}: require("${target}")`);
    }
  }
  assert.deepStrictEqual(
    offenders,
    [],
    "эти модули читают файл снаружи каталога, который Firebase упаковывает при деплое — в облаке " +
      "не загрузится весь кодбейс:\n" + offenders.join("\n"),
  );

  // Строка долга, которая больше не долг, вводит в заблуждение. Падать из-за неё нельзя — файл
  // чужой и чинится в своей очереди, — но и молчать не о чем.
  for (const [name, why] of KNOWN_UNPACKAGED) {
    const file = path.join(PACKAGE_ROOT, name);
    const stillEscapes =
      fs.existsSync(file) &&
      relativeRequires(fs.readFileSync(file, "utf8")).some((target) => escapesPackage(file, target));
    if (!stillEscapes) {
      console.warn(`deploy-package.test.js: ${name} починен (${why}) — удалите строку из KNOWN_UNPACKAGED`);
    }
  }
}

testTheQueueLoadsFromThePackageAlone();
testTheDeclarationTravelsWithTheCode();
testNoRuntimeModuleReadsAboveThePackage();

console.log("deploy-package.test.js OK");
