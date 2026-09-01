"use strict";

const assert = require("assert");
const fs = require("fs");
const path = require("path");

const {
  FIRST_VERSION,
  NO_VERSION,
  VERSION_STEP,
  currentVersion,
  nextVersion,
  nextVersionAtPath,
  nextVersionFor,
  setVersioned,
  versionsByPath,
} = require("./entity-version.js");

// ─── Батч и транзакция, каких хватает для проверки ────────────────────────────────────────────
//
// Обе настоящие сущности умеют ровно одно нужное здесь действие — `set(ref, data, options)`. Всё
// остальное в них от сети, а модуль сети не касается.

function fakeWriter() {
  const writes = [];
  return {
    writes,
    set(ref, data, options) {
      writes.push({path: ref.path, data, options});
    },
  };
}

function snapshot(docPath, data) {
  return {
    ref: {path: docPath},
    exists: data !== null && data !== undefined,
    data: () => data,
  };
}

function testADocumentThatDoesNotExistStartsAtOne() {
  // Ноль и единица различаются намеренно: ноль — «документа не было». На проводе этого нуля не
  // бывает — клиент выражает создание отсутствием `expected_version`, и приравнивает молчание к
  // нулю уже сервер (`versionVerdict`). Здесь ноль — только результат чтения.
  assert.strictEqual(currentVersion(null), NO_VERSION);
  assert.strictEqual(currentVersion(snapshot("quests/q", null)), NO_VERSION);
  assert.strictEqual(nextVersionFor(null), FIRST_VERSION);
  assert.strictEqual(nextVersionFor(snapshot("quests/q", null)), FIRST_VERSION);
}

function testAnExistingDocumentContinuesItsOwnRow() {
  assert.strictEqual(currentVersion(snapshot("quests/q", {version: 5})), 5);
  assert.strictEqual(nextVersionFor(snapshot("quests/q", {version: 5})), 6);
  assert.strictEqual(nextVersion(5), 5 + VERSION_STEP);
}

function testGarbageInTheFieldStartsTheRowOver() {
  // Молча продолжать ряд от строки или от минус трёх значит навсегда унаследовать чужую ошибку:
  // клиент сверяет версию на равенство, и одно нечисловое значение сломало бы сверку навсегда.
  assert.strictEqual(nextVersionFor(snapshot("quests/q", {version: "seven"})), FIRST_VERSION);
  assert.strictEqual(nextVersionFor(snapshot("quests/q", {version: -3})), FIRST_VERSION);
  assert.strictEqual(nextVersionFor(snapshot("quests/q", {})), FIRST_VERSION);
  assert.strictEqual(nextVersion(4.7), 5, "дробную версию округляем вниз, а не тянем дробь дальше");
}

function testTwoPublicationsInARowGiveNPlusOneEachTime() {
  // Ровно тот случай, ради которого версию отобрали у клиента: вторая публикация обязана дать
  // N+1, а не то же число, что и первая.
  const first = nextVersionFor(snapshot("quests/q", null));
  assert.strictEqual(first, 1);
  const second = nextVersionFor(snapshot("quests/q", {version: first}));
  assert.strictEqual(second, 2);
  const third = nextVersionFor(snapshot("quests/q", {version: second}));
  assert.strictEqual(third, 3);
}

function testEachNodeOfAMixedTreeGetsItsOwnVersion() {
  // Дерево смешанного состава: квест и одна секция уже опубликованы, тема и урок создаются
  // впервые. Одно число на всё дерево здесь и ломалось бы: клиент перекачивал бы соседние узлы,
  // которых никто не трогал.
  const versions = versionsByPath([
    snapshot("quests/q-1", {version: 4}),
    snapshot("sections/s-1", {version: 9}),
    snapshot("themes/t-1", null),
    snapshot("lessons/l-1", null),
  ]);

  assert.deepStrictEqual(versions, {
    "quests/q-1": 4,
    "sections/s-1": 9,
    "themes/t-1": 0,
    "lessons/l-1": 0,
  });
  assert.strictEqual(nextVersionAtPath(versions, "quests/q-1"), 5);
  assert.strictEqual(nextVersionAtPath(versions, "sections/s-1"), 10);
  assert.strictEqual(nextVersionAtPath(versions, "themes/t-1"), FIRST_VERSION);
  assert.strictEqual(nextVersionAtPath(versions, "lessons/l-1"), FIRST_VERSION);
  // Узел, которого в карте нет вовсе — новый: карту собирают из тех же путей, что и запись, но
  // отсутствие обязано читаться как «первая версия», а не как NaN.
  assert.strictEqual(nextVersionAtPath(versions, "questions/never-read"), FIRST_VERSION);
  assert.strictEqual(nextVersionAtPath(undefined, "questions/q-1"), FIRST_VERSION);
}

function testTheSecondPublicationOfTheSameTreeMovesEveryNodeByOne() {
  // Две публикации подряд по дереву смешанного состава — целиком, как это делает батч.
  const tree = ["quests/q-1", "sections/s-1", "themes/t-1", "lessons/l-1", "questions/w-1"];
  const stored = {"quests/q-1": 4, "sections/s-1": 9};

  const firstRun = {};
  for (const nodePath of tree) firstRun[nodePath] = nextVersionAtPath(stored, nodePath);
  assert.deepStrictEqual(firstRun, {
    "quests/q-1": 5,
    "sections/s-1": 10,
    "themes/t-1": 1,
    "lessons/l-1": 1,
    "questions/w-1": 1,
  });

  const secondRun = {};
  for (const nodePath of tree) secondRun[nodePath] = nextVersionAtPath(firstRun, nodePath);
  assert.deepStrictEqual(secondRun, {
    "quests/q-1": 6,
    "sections/s-1": 11,
    "themes/t-1": 2,
    "lessons/l-1": 2,
    "questions/w-1": 2,
  });
}

function testABatchWriteCarriesTheVersionTheModuleAssigned() {
  const batch = fakeWriter();

  const assigned = setVersioned(batch, {path: "quests/q-1"}, {title: "Основы"}, 4, {merge: true});

  assert.strictEqual(assigned, 5);
  assert.deepStrictEqual(batch.writes, [
    {path: "quests/q-1", data: {title: "Основы", version: 5}, options: {merge: true}},
  ]);
}

function testATransactionWriteCarriesTheVersionTheModuleAssigned() {
  const transaction = fakeWriter();

  const assigned = setVersioned(transaction, {path: "quests/q-1"}, {visibleOn: []}, 0);

  assert.strictEqual(assigned, FIRST_VERSION);
  assert.deepStrictEqual(transaction.writes, [
    {path: "quests/q-1", data: {visibleOn: [], version: 1}, options: undefined},
  ]);
}

function testABodyThatBringsItsOwnVersionIsOverwrittenNotObeyed() {
  // Старый клиент шлёт localRevision под тем же именем. Значение обязано быть затёрто серверным:
  // ровно это AD-12 и запрещает пропускать.
  const batch = fakeWriter();

  setVersioned(batch, {path: "quests/q-1"}, {version: 999, title: "Основы"}, 4, {merge: true});

  assert.strictEqual(batch.writes[0].data.version, 5);
}

function testAWriterWithoutSetIsRefusedRatherThanSilentlyIgnored() {
  assert.throws(() => setVersioned(null, {path: "quests/q"}, {}, 0), TypeError);
  assert.throws(() => setVersioned({}, {path: "quests/q"}, {}, 0), TypeError);
}

// ─── Гейт: мимо модуля версию не назначают ────────────────────────────────────────────────────
//
// Проверка по исходнику, а не по поведению: пятое место записи, добавленное когда-нибудь потом
// своим способом, поведенческим тестом не ловится — оно просто не вызовет ни одну функцию модуля.

const INDEX_SOURCE = fs.readFileSync(path.join(__dirname, "index.js"), "utf8");

/** Единственные разрешённые источники нового значения version. */
const VERSION_PRODUCERS = /^(nextVersion\(|nextVersionAtPath\(|setVersioned\()/;

function testEveryVersionAssignmentInIndexComesFromThisModule() {
  const assignments = [...INDEX_SOURCE.matchAll(/^\s*version:\s*(.*)$/gm)].map((match) => match[1].trim());
  assert.ok(assignments.length >= 4, "в index.js обязаны остаться места записи version");
  for (const rhs of assignments) {
    assert.ok(
      /nextVersion\(|nextVersionAtPath\(|assignedVersion|options\.assignedVersion/.test(rhs),
      `version назначается мимо entity-version.js: ${rhs}`,
    );
  }
  // Имя-посредник обязано само рождаться в модуле, иначе гейт выше обманывается одной буквой.
  const viaLocalName = [...INDEX_SOURCE.matchAll(/const assignedVersion = (.*)$/gm)].map((m) => m[1].trim());
  assert.ok(viaLocalName.length >= 3, "публикация, перекладка по полкам и снятие с полок назначают версию");
  for (const rhs of viaLocalName) {
    assert.ok(VERSION_PRODUCERS.test(rhs), `assignedVersion собран мимо модуля: ${rhs}`);
  }
}

// ─── Гейт: начисление рейтинга версию не двигает (находки 1 и 2) ──────────────────────────────
//
// Поведенческим тестом это не ловится: писатель агрегата дописывается слиянием в чужой документ, и
// проверить, чего он НЕ пишет, можно только по исходнику. Возврат одной строки `version:` в этот
// патч ломает весь эпик — приватная версия квеста снова начинает расти от чужих звёзд, и автор
// получает конфликт за то, что его квест оценили.

function functionBody(source, name) {
  const start = source.indexOf(`function ${name}(`);
  assert.notStrictEqual(start, -1, `в index.js нет функции ${name}`);
  const open = source.indexOf("{", start);
  let depth = 0;
  for (let i = open; i < source.length; i += 1) {
    if (source[i] === "{") depth += 1;
    if (source[i] === "}") {
      depth -= 1;
      if (depth === 0) return source.slice(open, i + 1);
    }
  }
  throw new Error(`не удалось разобрать тело ${name}`);
}

function testTheRatingAggregateNeverTouchesVersion() {
  const body = functionBody(INDEX_SOURCE, "writeQuestRatingAggregateToWriter");
  assert.ok(
    !/^\s*version:/m.test(body),
    "начисление рейтинга снова пишет version — это гарантированный ложный конфликт у автора",
  );
  assert.ok(
    /ratingVersion:/.test(body),
    "у рейтинга обязан быть свой счётчик: без него обновление оценки не переживёт приёмник снимков",
  );
}

// ─── Гейт: назначенная версия доезжает до приватного документа (находка 2) ────────────────────
//
// Клиент читает serverRevision из приватного документа квеста, а сверяется expected_version с
// версией той же сущности. Пока публикация писала версию только в `quests/{id}`, приватный документ
// её не имел вовсе и сверка не выполнялась ни разу.

function testEveryPlaceThatAssignsAQuestVersionMirrorsItIntoThePrivateDocument() {
  const mirror = functionBody(INDEX_SOURCE, "writeQuestVersionMirror");
  assert.ok(/privateQuestPath\(/.test(mirror), "зеркало пишет не в приватный документ квеста");
  assert.ok(/privateSyncChangePath\(/.test(mirror), "версия без отметки в ленте изменений до автора не доедет");

  const callers = [...INDEX_SOURCE.matchAll(/writeQuestVersionMirror\(/g)].length;
  assert.ok(
    callers >= 4,
    `зеркало обязано звучать в публикации, перекладке и снятии с полок, а не только в объявлении: ${callers}`,
  );
}

function testTheThreeOldWaysOfAssigningVersionAreGone() {
  assert.ok(
    !/version:\s*request\.localRevision/.test(INDEX_SOURCE),
    "присланное клиентом значение больше не попадает в version (AD-12)",
  );
  assert.ok(
    !/numberValue\(\s*quest\.version[^)]*\)\s*\+\s*1/.test(INDEX_SOURCE),
    "самодельный read-modify-write +1 заменён общей функцией",
  );
  assert.ok(
    !/version:\s*admin\.firestore\.FieldValue\.increment/.test(INDEX_SOURCE),
    "version больше не назначается слепым инкрементом: назначает её тот, кто прочитал текущую",
  );
  assert.ok(
    !/versionIncrement/.test(INDEX_SOURCE),
    "помощника для слепого инкремента версии больше нет — он существовал ради записи рейтинга",
  );
}

// ─── Гейт: contentsVersion не пишется ни сервером, ни сидами (история 8.6) ────────────────────

function testTheServerNoLongerWritesContentsVersion() {
  assert.ok(!/contentsVersion/.test(INDEX_SOURCE), "functions/index.js больше не знает contentsVersion");
}

/**
 * Все скрипты, а не только верхний уровень и не только имена вида `seed-*`.
 *
 * Прошлый обход смотрел `readdirSync(scripts)` и брал `^(seed|backfill)-.*\.js$`. Мимо него прошло
 * всё сразу: `scripts/seed-bulk/` — подкаталог, а `retire-quest.js` и `verify-seeded-quest.js` не
 * начинаются ни с `seed`, ни с `backfill`. Именно там `contentsVersion` и дожил до эпика 8. Гейт,
 * который проверяет часть дерева, охраняет ровно ту часть, где нарушения и не было.
 */
function everyScriptSource() {
  const scriptsDir = path.join(__dirname, "..", "scripts");
  const files = [];
  if (!fs.existsSync(scriptsDir)) return files;
  const stack = [scriptsDir];
  while (stack.length > 0) {
    const dir = stack.pop();
    for (const entry of fs.readdirSync(dir, {withFileTypes: true})) {
      if (entry.name === "node_modules" || entry.name.startsWith(".")) continue;
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        stack.push(full);
        continue;
      }
      if (!entry.name.endsWith(".js")) continue;
      files.push({name: path.relative(scriptsDir, full), source: fs.readFileSync(full, "utf8")});
    }
  }
  return files;
}

function testNoScriptWritesContentsVersion() {
  const scripts = everyScriptSource();
  assert.ok(scripts.length > 0, "обход scripts/ ничего не нашёл — гейт охраняет пустоту");
  const offenders = scripts
    .filter(({source}) => /^\s*contentsVersion\s*:/m.test(source))
    .map(({name}) => name);
  assert.deepStrictEqual(offenders, [], "скрипты больше не пишут contentsVersion");
}

/**
 * Сид — единственное место вне сервера, где версию ставит не сервер, и это осознанно (AD-12).
 *
 * Запрет AD-12 адресован клиенту: значение, присланное устройством, не должно попадать в version.
 * Сид клиентом не является — он пишет документы напрямую Admin SDK, и сервера в этом пути нет
 * вообще. Но раз исключение есть, оно обязано быть объяснено там же, где сделано, и брать шаг из
 * того же модуля, что и сервер: иначе через год это будет просто ещё одно место со своим числом.
 *
 * Гейт смотрит на скрипты, которые пишут версию СЛИЯНИЕМ, то есть поверх документов, которые уже
 * могут существовать у клиентов. Им регламент обязателен: приёмник снимков применяет документ,
 * только если версия выросла, и отметка времени (`VERSION = baseMs`) или зашитое число
 * (`VERSION = 4`) этого не дают — первая не версия, второе не растёт. Скрипт, который перед записью
 * сносит свои документы целиком, инкрементом пользоваться не может (счётчик начался бы заново) и
 * сюда не попадает — он и не переписывает чужую историю, он её удаляет.
 */
function testSeedScriptsAssignVersionThroughTheSharedStep() {
  const seeders = everyScriptSource().filter(
    ({source}) => /\bversion:\s*VERSION\b/.test(source) && /merge:\s*true/.test(source),
  );
  assert.ok(seeders.length >= 4, `гейт обязан видеть сиды, пишущие версию слиянием: ${seeders.length}`);
  for (const {name, source} of seeders) {
    assert.ok(
      /VERSION_STEP/.test(source) && /entity-version\.js/.test(source),
      `${name}: версия назначается своим числом, а не шагом из entity-version.js`,
    );
    assert.ok(
      /FieldValue\.increment\(VERSION_STEP\)/.test(source),
      `${name}: версия обязана расти от текущего значения, а не заменять его`,
    );
    assert.ok(
      /AD-12/.test(source),
      `${name}: исключение из AD-12 не объяснено там, где сделано`,
    );
  }
}

testADocumentThatDoesNotExistStartsAtOne();
testAnExistingDocumentContinuesItsOwnRow();
testGarbageInTheFieldStartsTheRowOver();
testTwoPublicationsInARowGiveNPlusOneEachTime();
testEachNodeOfAMixedTreeGetsItsOwnVersion();
testTheSecondPublicationOfTheSameTreeMovesEveryNodeByOne();
testABatchWriteCarriesTheVersionTheModuleAssigned();
testATransactionWriteCarriesTheVersionTheModuleAssigned();
testABodyThatBringsItsOwnVersionIsOverwrittenNotObeyed();
testAWriterWithoutSetIsRefusedRatherThanSilentlyIgnored();
testEveryVersionAssignmentInIndexComesFromThisModule();
testTheRatingAggregateNeverTouchesVersion();
testEveryPlaceThatAssignsAQuestVersionMirrorsItIntoThePrivateDocument();
testTheThreeOldWaysOfAssigningVersionAreGone();
testTheServerNoLongerWritesContentsVersion();
testNoScriptWritesContentsVersion();
testSeedScriptsAssignVersionThroughTheSharedStep();

console.log("entity-version.test.js: all tests passed");
