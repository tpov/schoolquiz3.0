'use strict';

const admin = require('firebase-admin');
const {syncChangeWrites} = require('../../functions/sync-changes');

// Ключ сервис-аккаунта проекта school-quiz-89336951. Путь переопределяется переменной окружения.
const SERVICE_ACCOUNT_PATH =
  process.env.SCHOOL_QUIZ_SERVICE_ACCOUNT ||
  process.env.GOOGLE_APPLICATION_CREDENTIALS ||
  '/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json';

let initialized = false;
function ensureInit() {
  if (initialized) return;
  if (!require('fs').existsSync(SERVICE_ACCOUNT_PATH)) {
    throw new Error(`Ключ сервис-аккаунта не найден: ${SERVICE_ACCOUNT_PATH}\nУкажите SCHOOL_QUIZ_SERVICE_ACCOUNT=/путь/к/school-quiz-89336951-....json`);
  }
  const sa = require(SERVICE_ACCOUNT_PATH);
  if (sa.project_id !== 'school-quiz-89336951') {
    throw new Error(`Ключ не от того проекта: ${sa.project_id} (ожидался school-quiz-89336951). Путь: ${SERVICE_ACCOUNT_PATH}`);
  }
  admin.initializeApp({ credential: admin.credential.cert(sa) });
  initialized = true;
}

const AUTHOR_UID = 'seed-author-uid';
const VISIBLE_ON = ['home'];
const LANGUAGE = 'ru';
const BATCH_OP_LIMIT = 400;

const mkPayload = (obj) => JSON.stringify(obj);

/**
 * Записи журнала для одного узла — через `BatchWriter`, который сбрасывает батч по мере
 * наполнения и потому обязан получать записи по одной и с ожиданием.
 */
async function writeSyncChangesWith(writer, db, node, changedAtMs) {
  const writes = syncChangeWrites(node, changedAtMs);
  for (const write of writes) {
    await writer.set(db.doc(write.path), write.data);
  }
  return writes;
}

class BatchWriter {
  constructor(db) {
    this.db = db;
    this.batch = db.batch();
    this.pending = 0;
    this.committed = 0;
  }

  async _maybeFlush() {
    if (this.pending >= BATCH_OP_LIMIT) {
      await this.batch.commit();
      this.committed += this.pending;
      this.batch = this.db.batch();
      this.pending = 0;
    }
  }

  async set(ref, data, options) {
    if (options) {
      this.batch.set(ref, data, options);
    } else {
      this.batch.set(ref, data);
    }
    this.pending += 1;
    await this._maybeFlush();
  }

  async update(ref, data) {
    this.batch.update(ref, data);
    this.pending += 1;
    await this._maybeFlush();
  }

  async commit() {
    if (this.pending > 0) {
      await this.batch.commit();
      this.committed += this.pending;
      this.pending = 0;
    }
    return this.committed;
  }
}

module.exports = {
  ensureInit,
  AUTHOR_UID,
  VISIBLE_ON,
  LANGUAGE,
  BATCH_OP_LIMIT,
  mkPayload,
  writeSyncChangesWith,
  BatchWriter,
  admin,
};
