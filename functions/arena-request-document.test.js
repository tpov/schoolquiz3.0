"use strict";

const assert = require("assert");
const fs = require("fs");
const path = require("path");

/**
 * Страж парности читателя и писателя заявки на арену.
 *
 * `quest_review_requests/{submissionId}` — не отметка об обработке, а тело заявки, которое
 * `publishSubmissionIfReady` перечитывает спустя дни после подачи и по которому строит публичный
 * квест. Пишет туда `requestToDocument`, читает `normalizeRequest`, и списки полей у них обязаны
 * совпадать.
 *
 * Проверка нужна потому, что расхождение здесь не падает и не логируется. Пропущенный `draft`
 * возвращается из `normalizeRequest` пустой строкой, а не `undefined`, — публикация спокойно
 * доходит до `db.collection("quests").doc("")` и пишет по пустому пути. Ровно так публикация с
 * арены и сломалась, когда прямую запись документа клиентом сняли, а серверный обработчик остался
 * дописывать одно `processed: true`.
 *
 * Проверка исходником, а не поведением: `index.js` при загрузке поднимает firebase-admin и
 * требует учётные данные, поэтому вызвать эти функции из обычного node-теста нельзя. Сверяются
 * имена полей — то единственное, что здесь ломается молча.
 */
const SOURCE_PATH = path.join(__dirname, "index.js");

const SUITE = [];
const test = (name, fn) => SUITE.push([name, fn]);

let cachedSource = null;
function source() {
  if (cachedSource) return cachedSource;
  assert.ok(fs.existsSync(SOURCE_PATH), `index.js не найден по пути ${SOURCE_PATH}`);
  cachedSource = fs.readFileSync(SOURCE_PATH, "utf8");
  return cachedSource;
}

// ─── Разбор исходника ──────────────────────────────────────────────────────────────────────────

const CLOSERS = {"{": "}", "[": "]", "(": ")"};

function endOfString(text, start) {
  const quote = text[start];
  let i = start + 1;
  while (i < text.length) {
    if (text[i] === "\\") {
      i += 2;
      continue;
    }
    if (text[i] === quote) return i + 1;
    i += 1;
  }
  return text.length;
}

/** Индекс парной скобки к открывающей на позиции `start`. */
function matchingBracket(text, start) {
  const stack = [CLOSERS[text[start]]];
  let i = start + 1;
  while (i < text.length && stack.length > 0) {
    const ch = text[i];
    if (ch === "'" || ch === '"' || ch === "`") {
      i = endOfString(text, i);
      continue;
    }
    if (ch === "/" && text[i + 1] === "/") {
      const end = text.indexOf("\n", i);
      i = end === -1 ? text.length : end;
      continue;
    }
    if (ch === "/" && text[i + 1] === "*") {
      i = text.indexOf("*/", i) + 2;
      continue;
    }
    if (CLOSERS[ch]) {
      stack.push(CLOSERS[ch]);
      i += 1;
      continue;
    }
    if (ch === stack[stack.length - 1]) {
      stack.pop();
      i += 1;
      continue;
    }
    i += 1;
  }
  assert.strictEqual(stack.length, 0, "не нашёл парную скобку — разбор index.js разошёлся с файлом");
  return i - 1;
}

function functionBody(name) {
  const text = source();
  const start = text.indexOf(`function ${name}(`);
  assert.notStrictEqual(start, -1, `функция ${name} исчезла из index.js`);
  const brace = text.indexOf("{", text.indexOf(")", start));
  return text.slice(brace, matchingBracket(text, brace) + 1);
}

/** Объектный литерал, который функция возвращает. */
function returnedLiteral(name) {
  const body = functionBody(name);
  const marker = body.indexOf("return {");
  assert.notStrictEqual(marker, -1, `${name} больше не возвращает объектный литерал`);
  const brace = body.indexOf("{", marker);
  return body.slice(brace, matchingBracket(body, brace) + 1);
}

/** Имена полей верхнего уровня объектного литерала. */
function topLevelKeys(literal) {
  const inner = literal.slice(1, -1);
  const entries = [];
  let current = "";
  let depth = 0;
  let i = 0;
  while (i < inner.length) {
    const ch = inner[i];
    if (ch === "'" || ch === '"' || ch === "`") {
      const end = endOfString(inner, i);
      current += inner.slice(i, end);
      i = end;
      continue;
    }
    if (ch === "/" && inner[i + 1] === "/") {
      const end = inner.indexOf("\n", i);
      i = end === -1 ? inner.length : end;
      continue;
    }
    if (ch === "/" && inner[i + 1] === "*") {
      i = inner.indexOf("*/", i) + 2;
      continue;
    }
    if (CLOSERS[ch]) depth += 1;
    if (ch === "}" || ch === "]" || ch === ")") depth -= 1;
    if (ch === "," && depth === 0) {
      entries.push(current);
      current = "";
      i += 1;
      continue;
    }
    current += ch;
    i += 1;
  }
  entries.push(current);
  return entries
    .map((entry) => entry.trim())
    .filter((entry) => entry.length > 0)
    .map((entry) => {
      const colon = entry.indexOf(":");
      assert.ok(colon > 0, `не разобрал поле объекта: ${entry}`);
      return entry.slice(0, colon).trim();
    })
    .sort();
}

function fieldsOf(name) {
  return topLevelKeys(returnedLiteral(name));
}

// ─── Проверки ──────────────────────────────────────────────────────────────────────────────────

/**
 * `status` — единственное поле документа, которого нет в теле заявки: его ставит тот, кто пишет
 * (`UNDER_REVIEW` при обработке, `PUBLISHED` при публикации, `REJECTED` при отказе рецензента).
 * Пришло бы оно телом — повторная обработка вернула бы уже отклонённую заявку в проверку.
 */
const WRITER_OMITS = ["status"];

test("документ заявки несёт каждое поле, которое читает normalizeRequest", () => {
  const read = fieldsOf("normalizeRequest");
  const written = fieldsOf("requestToDocument");
  const missing = read.filter((field) => !written.includes(field) && !WRITER_OMITS.includes(field));
  assert.deepStrictEqual(
    missing,
    [],
    "requestToDocument не пишет поля, которые normalizeRequest читает: " + missing.join(", ") +
      ". Публикация перечитывает документ и получит по ним пустые строки — квест уедет в quests/ ",
  );
});

test("документ заявки не несёт полей, которых никто не читает", () => {
  const read = fieldsOf("normalizeRequest");
  const written = fieldsOf("requestToDocument");
  const extra = written.filter((field) => !read.includes(field));
  assert.deepStrictEqual(
    extra,
    [],
    "requestToDocument пишет поля, которых normalizeRequest не знает: " + extra.join(", "),
  );
});

test("вопрос заявки round-trip: questionToDocument против normalizeQuestion", () => {
  assert.deepStrictEqual(fieldsOf("questionToDocument"), fieldsOf("normalizeQuestion"));
});

test("отметки автора round-trip: checksToCallableMap против reviewToChecks", () => {
  assert.deepStrictEqual(fieldsOf("checksToCallableMap"), fieldsOf("reviewToChecks"));
});

test("обработчик заявки пишет тело, а не одну отметку об обработке", () => {
  const body = functionBody("processArenaRequest");
  assert.ok(
    body.includes("...requestToDocument(request)"),
    "processArenaRequest перестал класть тело заявки в quest_review_requests — автор не увидит " +
      "вердикт, а публикация построит квест по пустому идентификатору",
  );
});

test("поля, по которым автор находит свой вердикт, попадают в документ", () => {
  // Выборка вердиктов фильтрует по ownerUid и требует draftId, и правило чтения смотрит на тот
  // же ownerUid. Без них отказ рецензента до автора не доходит, а черновик остаётся заперт.
  const written = fieldsOf("requestToDocument");
  ["ownerUid", "draftId"].forEach((field) => {
    assert.ok(written.includes(field), `документ заявки потерял ${field}`);
  });
});

// ─── Прогон ────────────────────────────────────────────────────────────────────────────────────

let failed = 0;
for (const [name, fn] of SUITE) {
  try {
    fn();
    console.log(`ok — ${name}`);
  } catch (error) {
    failed += 1;
    console.error(`FAIL — ${name}\n  ${error.message}`);
  }
}
console.log(`${SUITE.length - failed} of ${SUITE.length} cases passed`);
if (failed > 0) process.exit(1);
