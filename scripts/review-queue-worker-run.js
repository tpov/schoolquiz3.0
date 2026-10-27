"use strict";

/**
 * Раннер воркера очереди проверки на VPS (AD-32, история 4.12).
 *
 * Лёгкий процесс: только чтение Firestore через admin SDK (ADC окружения VPS) и вызовы модели.
 * Ни Gradle, ни Telegram, ни торговли. Ключ и адрес модели — из окружения; без них процесс
 * громко отказывается стартовать, а не «просто спит»: тихий простой очереди выглядит как
 * зависшая проверка.
 *
 * Пока скиллы этапов не зарегистрированы (истории 4.13/4.14), свип возвращает STAGE_MISSING
 * и ничего не трогает — очередь ждёт развёртки, никакого мусора в базе не появляется.
 *
 * Запуск: `node scripts/review-queue-worker-run.js [--once]`
 * Окружение: GCLOUD_PROJECT (проект Firestore), REVIEW_MODEL_API_URL, REVIEW_MODEL_API_KEY,
 * REVIEW_MODEL_NAME; опционально REVIEW_WORKER_POLL_MS (по умолчанию 30 000).
 */

const {
  createReviewModelClient,
  readModelConfig,
} = require("../functions/review-model-client");
const {createReviewQueueWorker} = require("../functions/review-queue-worker");

const USAGE = "node scripts/review-queue-worker-run.js [--once]";
const DEFAULT_POLL_MS = 30_000;

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function parseCli(argv) {
  return {once: argv.includes("--once")};
}

async function main(argv = process.argv.slice(2), env = process.env) {
  const options = parseCli(argv);
  const projectId = env.GCLOUD_PROJECT || "";
  if (!projectId) {
    console.error(JSON.stringify({status: "FAIL", code: "GCLOUD_PROJECT_MISSING"}));
    return null;
  }
  const config = readModelConfig(env);
  if (!config.ok) {
    console.error(JSON.stringify({status: "FAIL", code: config.reason}));
    return null;
  }
  const pollMs = Number(env.REVIEW_WORKER_POLL_MS) || DEFAULT_POLL_MS;

  const admin = require("../functions/node_modules/firebase-admin");
  admin.initializeApp({projectId});
  const db = admin.firestore();
  // Реестр скиллов этапов: «Слова» + «Факты» + «Перевод» (4.13–4.15); полный план проверяет реестр.
  const {completeReviewStages} = require("../functions/review-stages");
  const worker = createReviewQueueWorker({
    db,
    stages: completeReviewStages(),
    modelClient: createReviewModelClient({config: config.config}),
    log: (record) => console.warn(JSON.stringify(record)),
  });

  for (;;) {
    let result;
    try {
      result = await worker.sweep();
    } catch (error) {
      result = {status: "RUNNER_CRASHED"};
    }
    console.log(JSON.stringify({component: "review-queue-worker-run", status: result.status,
      attempted: result.attempted || 0, stageCalls: result.stageCalls || 0}));
    if (options.once) return result;
    await sleep(pollMs);
  }
}

if (require.main === module) {
  main().catch((error) => {
    console.error(JSON.stringify({status: "FAIL", code: "UNEXPECTED", message: String(error && error.message || error)}));
    process.exitCode = 1;
  });
}

module.exports = {USAGE, main};
