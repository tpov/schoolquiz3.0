"use strict";

/**
 * Сентинел общих имён предложений (история 4.21, testing.md «Общие имена клиента и сервера»).
 *
 * Словарь предложений — контракт двух миров: виды (REPORT/COPY_EDIT/NEW_LESSON), статусы машины,
 * имя мутации в реестрах обеих сторон, имя операции в клиентском OutboxOperations. Расхождение
 * любого из них — запись, которую приёмник отвергнет окончательно или прочитает мимо поля. Ловится
 * это чтением обеих сторон текстом: сервер — из его же исходников, клиент — из Kotlin-файла.
 *
 * Скан считанный (testing.md «Отрицательное утверждение о всём дереве»): он называет, сколько
 * файлов прошёл, и падает, если прошёл подозрительно мало — пропавший каталог не выглядит зелёным.
 */

const assert = require("node:assert/strict");
const test = require("node:test");
const fs = require("node:fs");
const path = require("node:path");

const ROOT = __dirname;
const CLIENT_OUTBOX = path.join(ROOT,
  "../shared/core/outbox/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/outbox/OutboxOperations.kt");

const {
  PROPOSALS_COLLECTION,
  PROPOSAL_KINDS,
  PROPOSAL_STATUS,
  PROPOSAL_TARIFFS_DOC,
} = require("./lesson-proposals");

/** Собранные файлы скана: ._-компаньоны SMB и build/ не считаются. */
function collectSources(root, extension, skipSelf) {
  const files = [];
  const walk = (directory) => {
    for (const entry of fs.readdirSync(directory, {withFileTypes: true}).sort((a, b) => a.name.localeCompare(b.name))) {
      if (entry.name.startsWith("._") || entry.name === "build" || entry.name === "node_modules") continue;
      const full = path.join(directory, entry.name);
      if (entry.isDirectory()) {
        walk(full);
      } else if (entry.name.endsWith(extension) && (!skipSelf || full !== skipSelf)) {
        files.push(full);
      }
    }
  };
  walk(root);
  return files;
}

test("the proposal vocabulary is the contract it claims to be", () => {
  assert.deepEqual(PROPOSAL_KINDS, ["REPORT", "COPY_EDIT", "NEW_LESSON"], "AD-33 names three kinds");
  assert.deepEqual(Object.values(PROPOSAL_STATUS).sort(),
    ["APPROVED", "CHECK_DELAYED", "CHECK_FAILED", "CHECK_PASSED", "QUEUED", "REJECTED"].sort());
  assert.equal(PROPOSALS_COLLECTION, "lesson_proposals");
  assert.equal(PROPOSAL_TARIFFS_DOC, "configs/proposal_fee");
});

test("the client declares the proposal operation under the name the server registers", () => {
  const source = fs.readFileSync(CLIENT_OUTBOX, "utf8");
  assert.ok(source.includes('const val SUBMIT_PROPOSAL: String = "quest_authoring.SUBMIT_PROPOSAL"'),
    "OutboxOperations.SUBMIT_PROPOSAL must carry the exact operation name");
});

test("the server registers the proposal mutation under the same name", () => {
  const index = fs.readFileSync(path.join(ROOT, "index.js"), "utf8");
  const registry = index.match(/const MUTATION_HANDLERS = \{([\s\S]*?)\n};/);
  assert.ok(registry, "MUTATION_HANDLERS registry must exist");
  assert.ok(registry[1].includes('"quest_authoring.SUBMIT_PROPOSAL":'),
    "the proposal receiver is registered under the client operation name");
});

test("the counted scan really walked the functions tree", () => {
  const files = collectSources(ROOT, ".js", path.join(__filename));
  assert.ok(files.length >= 100, `scan floor: walked ${files.length} files, expected the whole suite`);
  const index = files.map((file) => fs.readFileSync(file, "utf8")).join("\n");
  // Вид предложения не называется нигде вне словаря и приёмника: строка "COPY_EDIT" живёт в
  // lesson-proposals.js и index.js (плата по виду), но не разбредается по чужим модулям.
  const kindMentions = files.filter((file) => {
    const text = fs.readFileSync(file, "utf8");
    return text.includes('"COPY_EDIT"');
  }).map((file) => path.relative(ROOT, file));
  assert.ok(kindMentions.every((file) => file === "lesson-proposals.js" || file === "index.js" ||
    file === "lesson-proposals.test.js" || file === "proposal-names-sentinel.test.js"),
    `the proposal kind leaked outside its module: ${kindMentions.join(", ")}`);
  assert.ok(index.includes("PROPOSAL_FEE"), "the ledger reason is part of the server surface");
});
