'use strict';

const path = require('path');
const admin = require('firebase-admin');

const SERVICE_ACCOUNT_PATH = '/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json';

let initialized = false;
function ensureInit() {
  if (initialized) return;
  const sa = require(SERVICE_ACCOUNT_PATH);
  admin.initializeApp({ credential: admin.credential.cert(sa) });
  initialized = true;
}

const AUTHOR_UID = 'seed-author-uid';
const VISIBLE_ON = ['home'];
const LANGUAGE = 'ru';
const BATCH_OP_LIMIT = 400;

const mkPayload = (obj) => JSON.stringify(obj);

const syncChange = (type, id, changedAtMs) => ({ type, id, changedAtMs });

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
  syncChange,
  BatchWriter,
  admin,
};
