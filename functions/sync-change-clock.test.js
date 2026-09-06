"use strict";

/**
 * Страж часов журнала: время записи ставит сервер, а не устройство.
 *
 * Клиент узнаёт об изменениях единственным способом — вычитывая журнал `sync_changes` по курсору
 * `(changedAtMs, docId)` строго вперёд (AD-31). Курсор монотонен: назад он не возвращается. Значит
 * `changedAtMs` — не свойство события, а положение в общей очереди чтения, и ставить его обязаны
 * одни часы для всех записей. Часы сервера.
 *
 * Что ломается, когда туда попадает время устройства: у автора с убежавшими вперёд часами курсор
 * приватного журнала уезжает в будущее, и всё, что сервер запишет ПОТОМ, — назначенная при
 * публикации версия, перекладка по полкам, рейтинг — оказывается ниже курсора и не читается
 * никогда. `serverRevision` в черновик не приходит, следующая публикация уходит со стухшим
 * `expected_version` и получает вечный конфликт. С отстающими часами автор не видит собственной
 * только что поданной заявки. Документ журнала на узел один и пишется без merge, поэтому неверное
 * время не добавляется к верному, а замещает его; лечится это только «Перечитать всё».
 *
 * Один такой вызов в ветке был (`writePrivateHierarchyToBatch`: `request.requestedAtMs` — число из
 * тела очереди, поставленное устройством автора), и заметить его глазами было нечем: семь соседних
 * вызовов выглядят так же и берут серверное `now`. Отсюда проверка не «этот вызов починен», а
 * «каждый вызов датируется серверными часами».
 *
 * Проверка исходником, а не поведением: `index.js` при загрузке поднимает firebase-admin и требует
 * учётные данные (тот же довод, что в `arena-request-document.test.js`).
 */

const assert = require("assert");
const fs = require("fs");
const path = require("path");

const SOURCE = fs.readFileSync(path.join(__dirname, "index.js"), "utf8");

/**
 * Выражения, которыми серверу позволено датировать запись журнала.
 *
 * Список закрытый и короткий намеренно: всякое новое имя здесь — это обещание, что за ним стоит
 * `Date.now()` сервера, а не число из запроса. `task.changedAtMs` — время, которое `toAdminLessonTasks`
 * проставила из того же серверного `now`.
 */
// lessonChangeAtMs is max(server now, locked server lesson-journal cursor + 1), never request time.
// arena-handler.test drives the callable with forged timestamps and repeated server milliseconds.
const SERVER_CLOCKS = new Set(["now", "options.now", "task.changedAtMs", "lessonChangeAtMs"]);

/** Имена полей, которые приезжают с устройства и часами быть не могут. */
const DEVICE_TIME = /requestedAtMs|clientAtMs|deviceAtMs|createdAtMs|updatedAtMs/;

// ─── разбор исходника ─────────────────────────────────────────────────────────────────────────

/**
 * Комментарии — пробелами, перевод строки на месте: номера строк в сообщении об ошибке обязаны
 * совпадать с настоящим `index.js`, иначе искать будет негде.
 */
function blankOut(text) {
  return text.replace(/[^\n]/g, " ");
}

function withoutComments(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, blankOut)
    .replace(/(^|[^:])(\/\/[^\n]*)/g, (whole, before, comment) => before + blankOut(comment));
}

const CODE = withoutComments(SOURCE);

/** Индекс парной закрывающей скобки к открывающей на позиции `start`. */
function matchingParenthesis(text, start) {
  let depth = 0;
  for (let index = start; index < text.length; index += 1) {
    const character = text[index];
    if (character === "(" || character === "{" || character === "[") depth += 1;
    else if (character === ")" || character === "}" || character === "]") {
      depth -= 1;
      if (depth === 0) return index;
    }
  }
  throw new assert.AssertionError({message: `скобка с позиции ${start} не закрыта`});
}

/** Аргументы вызова верхнего уровня — по запятым вне вложенных скобок и строк. */
function argumentsOf(text) {
  const parts = [];
  let depth = 0;
  let current = "";
  for (let index = 0; index < text.length; index += 1) {
    const character = text[index];
    if (character === "(" || character === "{" || character === "[") depth += 1;
    if (character === ")" || character === "}" || character === "]") depth -= 1;
    if (character === "," && depth === 0) {
      parts.push(current.trim());
      current = "";
      continue;
    }
    current += character;
  }
  if (current.trim() !== "") parts.push(current.trim());
  return parts;
}

/** Все вызовы функции по имени: список списков аргументов. */
function callsTo(name) {
  const calls = [];
  const needle = `${name}(`;
  let at = CODE.indexOf(needle);
  while (at !== -1) {
    const open = at + name.length;
    const close = matchingParenthesis(CODE, open);
    calls.push({
      arguments: argumentsOf(CODE.slice(open + 1, close)),
      line: CODE.slice(0, at).split("\n").length,
    });
    at = CODE.indexOf(needle, close);
  }
  return calls;
}

function bodyOf(name) {
  const header = new RegExp(`function\\s+${name}\\b`).exec(CODE);
  assert.ok(header, `функция ${name} не найдена в index.js`);
  const start = CODE.indexOf("{", header.index);
  return CODE.slice(start, matchingParenthesis(CODE, start) + 1);
}

// ─── проверки ─────────────────────────────────────────────────────────────────────────────────

function testEverySyncChangeIsDatedByTheServer() {
  const calls = callsTo("writeSyncChanges");
  assert.ok(calls.length >= 8, `вызовов writeSyncChanges найдено ${calls.length} — разбор сломался`);

  const offenders = [];
  for (const call of calls) {
    assert.strictEqual(
      call.arguments.length,
      4,
      `writeSyncChanges (строка ~${call.line}) вызван с ${call.arguments.length} аргументами`,
    );
    const clock = call.arguments[3];
    if (!SERVER_CLOCKS.has(clock)) offenders.push(`строка ~${call.line}: changedAtMs = ${clock}`);
  }

  assert.deepStrictEqual(
    offenders,
    [],
    "запись журнала датирована не серверными часами — курсор читателя уедет и назад не вернётся " +
      `(разрешено: ${[...SERVER_CLOCKS].join(", ")}):\n` + offenders.join("\n"),
  );
}

function testThePrivateJournalGetsTheServerClockOfItsOwnBatch() {
  // Именно тот вызов, который был неправ: приватное дерево автора и его журнал.
  const body = bodyOf("writePrivateHierarchyToBatch");
  assert.ok(
    /function writePrivateHierarchyToBatch\(batch, request, now\)/.test(CODE),
    "серверное время обязано приходить аргументом: внутри его взять неоткуда, а брать из заявки нельзя",
  );
  assert.ok(
    !DEVICE_TIME.test(body),
    "в приватном дереве и его журнале не должно быть времени устройства:\n" + body,
  );

  const caller = bodyOf("processArenaRequest");
  assert.ok(
    /const now = Date\.now\(\)/.test(caller),
    "серверное время обработчика заявки считается им самим",
  );
  assert.ok(
    /writePrivateHierarchyToBatch\(batch, request, now\)/.test(caller),
    "посчитанное серверное время обязано доехать до приватного дерева, а не остаться в соседней строке",
  );
}

function testThePrivateDocumentsCarryTheSameClockAsTheirJournalEntry() {
  const body = bodyOf("privateDocuments");
  assert.ok(
    /function privateDocuments\(request, now\)/.test(CODE),
    "приватные документы датируются тем же серверным временем, что и запись журнала о них",
  );
  const stamps = body.match(/changedAtMs:\s*([^,\n]+)/g) || [];
  assert.ok(stamps.length >= 2, `отметок changedAtMs найдено ${stamps.length} — разбор сломался`);
  for (const stamp of stamps) {
    assert.strictEqual(
      stamp.replace(/\s+/g, " ").trim(),
      "changedAtMs: now",
      "приватный документ датирован не серверным временем: клиент ходит по этому полю, и оно " +
        "обязано совпадать с записью журнала того же батча",
    );
  }
  // Время автора остаётся там, где означает «когда автор это сделал».
  assert.ok(
    /updatedAtMs: request\.draft\.updatedAtMs/.test(body),
    "поле про содержание — авторское, его серверными часами подменять нечего",
  );
}

testEverySyncChangeIsDatedByTheServer();
testThePrivateJournalGetsTheServerClockOfItsOwnBatch();
testThePrivateDocumentsCarryTheSameClockAsTheirJournalEntry();

console.log("sync-change-clock.test.js OK");
