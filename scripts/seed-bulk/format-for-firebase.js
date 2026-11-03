'use strict';

// ===========================================================================
// format-for-firebase.js
//
// Deterministic gate for authored quest modules before they are seeded to
// Firestore. Two jobs:
//
//   1. CODE-SYNTAX HANDLING  — parse the author markup ( *protected* /
//      **blank** / ***protected-blank*** ) exactly like the live app
//      (android .../logic/FillBlankAuthoring.kt :: parseAuthorMarkup) and pull
//      protected spans (code / terminology) into `protectedTextSegments` so a
//      later localization pass never sends them to the translator.
//
//   2. INVARIANT VALIDATION  — enforce ADR-0003 / QuestionContent.kt invariants
//      and the locked content standard (40 Q per lesson = 10 sc / 10 mc /
//      10 ord / 10 fb, 20 EASY + 20 HARD) so a bad lesson fails fast.
//
// Localization (translate `info`/instructions to other languages) is a
// separate, later stage; the masking helpers it needs live here too
// (maskProtected mirrors legacy translateQuestion.js :: maskItalicWords).
//
// CLI:
//   node format-for-firebase.js <questModule.js> [--no-balance] [--selftest]
// Exit code 0 = clean, 1 = errors found.
// ===========================================================================

// --- 1. Author markup parser (mirror of FillBlankAuthoring.kt) --------------
// token rules: '*'   -> protected, not a blank
//              '**'  -> blank, not protected
//              '***' -> blank AND protected
function parseAuthorMarkup(text) {
  const source = (text || '').trim();
  if (!source) return { segments: [{ text: '', isBlank: false, isProtected: false }], hasMarkup: false };

  const segments = [];
  let plain = '';
  let index = 0;
  let hasMarkup = false;
  const flushPlain = () => {
    if (plain) { segments.push({ text: plain, isBlank: false, isProtected: false }); plain = ''; }
  };

  while (index < source.length) {
    const token = source.startsWith('***', index) ? '***'
      : source.startsWith('**', index) ? '**'
        : source.startsWith('*', index) ? '*'
          : null;
    if (token === null) { plain += source[index]; index += 1; continue; }

    const contentStart = index + token.length;
    const contentEnd = source.indexOf(token, contentStart);
    if (contentEnd <= contentStart) { plain += token; index += token.length; continue; }

    const content = source.substring(contentStart, contentEnd).trim();
    if (!content) { plain += source.substring(index, contentEnd + token.length); index = contentEnd + token.length; continue; }

    flushPlain();
    hasMarkup = true;
    segments.push({ text: content, isBlank: token.length >= 2, isProtected: token.length === 1 || token.length === 3 });
    index = contentEnd + token.length;
  }
  flushPlain();
  return {
    segments: segments.length ? segments : [{ text: source, isBlank: false, isProtected: false }],
    hasMarkup,
  };
}

// All protected spans in a piece of author text (code / terms not to translate).
function extractProtectedSegments(text) {
  const seen = new Set();
  const out = [];
  for (const seg of parseAuthorMarkup(text).segments) {
    if (!seg.isProtected) continue;
    const t = seg.text.trim();
    if (!t) continue;
    const k = t.toLowerCase();
    if (seen.has(k)) continue;
    seen.add(k);
    out.push(t);
  }
  return out;
}

// --- 2. Localization masking (mirror of translateQuestion.js) ---------------
// Replace *protected* spans with {{N}} placeholders before sending to a
// translator; restore them afterwards. Single-star only, by design.
function maskProtected(text) {
  const masked = [];
  const cleaned = String(text).replace(/\*(.*?)\*/g, (_, word) => { masked.push(word); return `{{${masked.length - 1}}}`; });
  return { cleaned, masked };
}
function unmaskProtected(text, masked) {
  return String(text).replace(/\{\{(\d+)\}\}/g, (_, i) => `*${masked[Number(i)]}*`);
}

// --- 3. ADR-0003 invariant validation ---------------------------------------
function validatePayload(p, id, errors) {
  const where = id || '(no id)';
  if (!id || !String(id).trim()) errors.push(`${where}: id must not be blank`);
  if (!p || typeof p !== 'object') { errors.push(`${where}: payload missing`); return; }
  if (!p.text || !String(p.text).trim()) errors.push(`${where}: text must not be blank`);

  // No option / ordering item / candidate may have blank text.
  for (const arrName of ['options', 'items', 'candidates']) {
    if (Array.isArray(p[arrName])) {
      p[arrName].forEach((el) => {
        if (!el || el.text == null || !String(el.text).trim()) {
          errors.push(`${where}: ${p.type}.${arrName} has an element with blank text`);
        }
      });
    }
  }

  switch (p.type) {
    case 'SingleChoice': {
      const opts = p.options || [];
      if (!(opts.length >= 2 && opts.length <= 8)) errors.push(`${where}: SingleChoice.options.size must be 2..8, got ${opts.length}`);
      const ids = opts.map((o) => o.id);
      if (new Set(ids).size !== ids.length) errors.push(`${where}: SingleChoice options must have unique ids`);
      if (!ids.includes(p.correctOptionId)) errors.push(`${where}: SingleChoice.correctOptionId '${p.correctOptionId}' not in options`);
      break;
    }
    case 'MultipleChoice': {
      const opts = p.options || [];
      if (!(opts.length >= 2 && opts.length <= 8)) errors.push(`${where}: MultipleChoice.options.size must be 2..8, got ${opts.length}`);
      const ids = opts.map((o) => o.id);
      if (new Set(ids).size !== ids.length) errors.push(`${where}: MultipleChoice options must have unique ids`);
      const correct = p.correctOptionIds || [];
      if (correct.length < 2) errors.push(`${where}: MultipleChoice.correctOptionIds.size must be >= 2, got ${correct.length}`);
      const missing = correct.filter((c) => !ids.includes(c));
      if (missing.length) errors.push(`${where}: MultipleChoice.correctOptionIds not in options: ${missing.join(',')}`);
      break;
    }
    case 'Ordering': {
      const items = p.items || [];
      if (!(items.length >= 2 && items.length <= 8)) errors.push(`${where}: Ordering.items.size must be 2..8, got ${items.length}`);
      break;
    }
    case 'FillBlank': {
      const blanks = p.blanks || [];
      const cands = p.candidates || [];
      if (!(blanks.length >= 1 && blanks.length <= 3)) errors.push(`${where}: FillBlank.blanks.size must be 1..3, got ${blanks.length}`);
      if (!(cands.length === 5 || cands.length === 10)) errors.push(`${where}: FillBlank.candidates.size must be 5 or 10, got ${cands.length}`);
      const cids = cands.map((c) => c.id);
      if (new Set(cids).size !== cids.length) errors.push(`${where}: FillBlank.candidates must have unique ids`);
      const bids = blanks.map((b) => b.id);
      if (new Set(bids).size !== bids.length) errors.push(`${where}: FillBlank.blanks must have unique ids`);
      for (const b of blanks) if (!cids.includes(b.correctCandidateId)) errors.push(`${where}: FillBlank blank '${b.id}' correctCandidateId '${b.correctCandidateId}' not in candidates`);
      for (const s of (p.protectedTextSegments || [])) if (!String(s).trim()) errors.push(`${where}: FillBlank.protectedTextSegments must not contain blank values`);
      break;
    }
    default:
      errors.push(`${where}: unknown question type '${p.type}'`);
  }
}

// --- 4. Locked content standard (balance per lesson) ------------------------
const STANDARD = {
  totalPerLesson: 40,
  perType: { SingleChoice: 10, MultipleChoice: 10, Ordering: 10, FillBlank: 10 },
  easy: 20,
  hard: 20,
  perTypeDifficulty: 5,
};

function validateLesson(lesson, errors, warnings, checkBalance) {
  const id = lesson.id || '(lesson no id)';
  const qs = lesson.questions || [];
  for (const q of qs) {
    validatePayload(q.payload, q.id, errors);
    if (q.payload && (q.payload.info == null || !String(q.payload.info).trim())) warnings.push(`${q.id}: missing info (explanation)`);
  }
  if (!checkBalance) return;
  if (qs.length !== STANDARD.totalPerLesson) errors.push(`${id}: lesson must have ${STANDARD.totalPerLesson} questions, got ${qs.length}`);
  const byType = {}; const byDiff = {}; const byTD = {};
  for (const q of qs) {
    const t = q.payload && q.payload.type; const d = q.payload && q.payload.difficulty;
    byType[t] = (byType[t] || 0) + 1; byDiff[d] = (byDiff[d] || 0) + 1; byTD[`${t}/${d}`] = (byTD[`${t}/${d}`] || 0) + 1;
  }
  for (const [t, n] of Object.entries(STANDARD.perType)) if ((byType[t] || 0) !== n) errors.push(`${id}: expected ${n} ${t}, got ${byType[t] || 0}`);
  if ((byDiff.EASY || 0) !== STANDARD.easy) errors.push(`${id}: expected ${STANDARD.easy} EASY, got ${byDiff.EASY || 0}`);
  if ((byDiff.HARD || 0) !== STANDARD.hard) errors.push(`${id}: expected ${STANDARD.hard} HARD, got ${byDiff.HARD || 0}`);
  for (const t of Object.keys(STANDARD.perType)) for (const d of ['EASY', 'HARD']) {
    if ((byTD[`${t}/${d}`] || 0) !== STANDARD.perTypeDifficulty) errors.push(`${id}: expected ${STANDARD.perTypeDifficulty} ${t}/${d}, got ${byTD[`${t}/${d}`] || 0}`);
  }
}

function validateQuest(quest, { checkBalance = true } = {}) {
  const errors = []; const warnings = [];
  let lessons = 0; let questions = 0;
  for (const s of (quest.sections || [])) for (const t of (s.themes || [])) for (const l of (t.lessons || [])) {
    lessons += 1; questions += (l.questions || []).length;
    validateLesson(l, errors, warnings, checkBalance);
  }
  return { errors, warnings, stats: { sections: (quest.sections || []).length, lessons, questions } };
}

module.exports = {
  parseAuthorMarkup, extractProtectedSegments, maskProtected, unmaskProtected,
  validatePayload, validateLesson, validateQuest, STANDARD,
};

// --- CLI --------------------------------------------------------------------
function selftest() {
  const cases = [
    ['plain text', { hasMarkup: false, protected: [] }],
    ['Run `*git commit*` to save', { hasMarkup: true, protected: ['git commit'] }],
    ['The verb **is** goes here', { hasMarkup: true, protected: [] }],
    ['Type ***README*** exactly', { hasMarkup: true, protected: ['README'] }],
  ];
  let ok = true;
  for (const [text, exp] of cases) {
    const m = parseAuthorMarkup(text);
    const prot = extractProtectedSegments(text);
    const pass = m.hasMarkup === exp.hasMarkup && JSON.stringify(prot) === JSON.stringify(exp.protected);
    if (!pass) { ok = false; console.log(`SELFTEST FAIL: "${text}" -> hasMarkup=${m.hasMarkup} protected=${JSON.stringify(prot)}`); }
  }
  const { cleaned, masked } = maskProtected('use *Kotlin* not *Java*');
  if (cleaned !== 'use {{0}} not {{1}}' || unmaskProtected(cleaned, masked) !== 'use *Kotlin* not *Java*') { ok = false; console.log('SELFTEST FAIL: mask/unmask roundtrip'); }
  console.log(ok ? 'SELFTEST OK' : 'SELFTEST FAILED');
  return ok;
}

if (require.main === module) {
  const args = process.argv.slice(2);
  if (args.includes('--selftest')) { process.exit(selftest() ? 0 : 1); }
  const file = args.find((a) => !a.startsWith('--'));
  if (!file) { console.error('usage: node format-for-firebase.js <questModule.js> [--no-balance] [--selftest]'); process.exit(2); }
  const path = require('path');
  const quest = require(path.resolve(file));
  const { errors, warnings, stats } = validateQuest(quest, { checkBalance: !args.includes('--no-balance') });
  console.log(`quest: ${quest.id}`);
  console.log(`stats: ${stats.sections} sections, ${stats.lessons} lessons, ${stats.questions} questions`);
  if (warnings.length) console.log(`\nWARNINGS (${warnings.length}):\n  ` + warnings.slice(0, 20).join('\n  ') + (warnings.length > 20 ? `\n  ...+${warnings.length - 20} more` : ''));
  if (errors.length) { console.log(`\nERRORS (${errors.length}):\n  ` + errors.join('\n  ')); process.exit(1); }
  console.log('\nALL INVARIANTS OK');
}
