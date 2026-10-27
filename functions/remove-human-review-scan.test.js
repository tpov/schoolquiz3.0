"use strict";

/**
 * 4.19 remove-human-review (UX-17): мир живых проверяющих удалён — этот скан не даёт ему
 * вернуться. Отрицательное утверждение обо всём дереве (testing.md «Тесты по исходникам»):
 * поведение здесь не проверить — удалённые имена обязаны не встречаться нигде в исходниках
 * сервера и клиента. Тест считает просмотренные файлы и падает, если их подозрительно мало:
 * пустой результат должен значить «не нашли», а не «не искали». Из скана исключён сам скан.
 */

const assert = require("node:assert/strict");
const test = require("node:test");
const fs = require("node:fs");
const path = require("node:path");

const ROOT = path.join(__dirname, "..");

// Каталоги-источники: сервер и весь клиентский код (общие модули, платформа, презентация, app).
const DIRS = [
  "functions",
  "shared/core",
  "shared/feature",
  "android/feature",
  "apps/android-next/src",
  "platform/firebase/src",
];
const EXT = /\.(js|kt|kts|java|xml)$/;
// Служебное и генерируемое: метаданные SMB, сборки, схемы Room, бейслайны detekt (их починка —
// вопрос запуска detekt, красного toolchain-wide по решению владельца), чужой untracked-скрипт.
const SKIP = /(^|\/)(\._[^/]+|build|node_modules|schemas|\.git)(\/|$)|detekt-baseline\.xml$|generate-arena-client-fixtures\.js$/;
// Легитимные имена: миграция 18→19 и её тест обязаны называть дропнутые таблицы по имени
// (DROP TABLE и INSERT в фикстуре старой схемы).
const ALLOWED_PATH = /migrations\/Migration18to19\.kt$|AppDatabaseMigrationsTest\.kt$/;

/** Удалённые общие имена: ни сервер, ни клиент не вправе держать их ни в одном исходнике. */
const FORBIDDEN = [
  // callable и операция очереди (обе стороны обязаны молчать согласованно)
  "submitReviewAction",
  "fetchReviewAssignments",
  "fetchReviewAssignmentChanges",
  "rejectReviewSubmission",
  "reconcileQuestReviewDaily",
  "quest_authoring.SUBMIT_REVIEW",
  // звёзды, порог и посегментное ревью перевода
  "testingPassScore",
  "TRANSLATION_REVIEW",
  "segmentResults",
  "SubmitReviewActionCommand",
  // назначение проверяющих и их награды
  "buildReviewUnits",
  "review_assignment",
  "ReviewAssignment",
  "reviewerLevelAtSubmit",
  // экран проверяющего: имена классов, секции шторки и её корень
  // (слово ReviewQueue само по себе не ловим: модельный воркер 4.12 зовётся createReviewQueueWorker,
  // а статус черновика автора — REVIEW_QUEUED; ловим имена поверхности)
  "ReviewQueueComponent",
  "ReviewQueueScreen",
  "ReviewQueueView",
  "ReviewQueueUiState",
  "LocalSection.ReviewQueue",
  "ReviewQueueRoot",
  "reviewQueueFactory",
];

/** Допустимые упоминания: чужие роли и слова, совпадающие по подстроке случайно. */
const ALLOWED = [
  // код-ревью и ревью спек — не живые проверяющие
  "code-reviewer",
];

function listFiles(dir, acc = []) {
  for (const entry of fs.readdirSync(dir, {withFileTypes: true})) {
    const full = path.join(dir, entry.name);
    if (SKIP.test(full + "/") || SKIP.test(full)) continue;
    if (entry.isDirectory()) listFiles(full, acc);
    else if (EXT.test(entry.name) && entry.name !== path.basename(__filename)) acc.push(full);
  }
  return acc;
}

test("the removed human-review world is gone from every source file, and the scan proved it searched", () => {
  const files = DIRS.flatMap((dir) => {
    const full = path.join(ROOT, dir);
    return fs.existsSync(full) ? listFiles(full) : [];
  });
  // «Не искали» не должно притворяться «не нашли»: под скан попадают сотни исходников.
  assert.ok(files.length > 500, `scan covered only ${files.length} files — the tree shrank?`);

  const offenders = [];
  for (const file of files) {
    if (ALLOWED_PATH.test(file)) continue;
    const text = fs.readFileSync(file, "utf8");
    for (const name of FORBIDDEN) {
      if (!text.includes(name)) continue;
      if (ALLOWED.some((allowed) => text.includes(allowed) && text.includes(name))) {
        // Допустимое упоминание найдено рядом; строка ниже скажет, что именно нашлось.
      }
      const line = text.split("\n").findIndex((row) => row.includes(name)) + 1;
      offenders.push(`${path.relative(ROOT, file)}:${line}: ${name}`);
    }
  }
  assert.deepEqual(offenders, [], "human-review world resurfaced:\n" + offenders.join("\n"));
});
