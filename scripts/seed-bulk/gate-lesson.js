'use strict';
// Гейт урока/темы/предмета для школьных квестов. Используется авторами и ревизорами.
//   node gate-lesson.js <subject> <s>-<t>          — тема (5 уроков)
//   node gate-lesson.js <subject> <s>-<t>-<l>      — один урок
//   node gate-lesson.js <subject> --all            — весь предмет (140 уроков)
// Проверяет: 40 вопросов; строгий порядок блоков 0..39 (5 sc E, 5 sc H, 5 mc E, 5 mc H,
// 5 ord E, 5 ord H, 5 fb E, 5 fb H); инварианты ADR-0003 через validateLesson;
// пустые тексты; fb: blanks/candidates/___; позиционные ссылки в info; дубли текстов
// вопросов внутри темы. Выход: ERR>0 → код 1.
const path = require('path');
const fs = require('fs');
const { validateLesson } = require('./format-for-firebase');

const [subject, target] = process.argv.slice(2);
if (!subject || !target) { console.error('usage: node gate-lesson.js <subject> <s>-<t>[-<l>] | --all'); process.exit(2); }
const base = path.join(__dirname, 'data', 'school', subject);
const { buildLesson } = require(path.join(base, '_helpers'));

const SEQ = ['SingleChoice/EASY','SingleChoice/HARD','MultipleChoice/EASY','MultipleChoice/HARD','Ordering/EASY','Ordering/HARD','FillBlank/EASY','FillBlank/HARD'];
const POSITIONAL = /\b(перв(ый|ая|ое|ые|ых|ом|ую)|втор(ой|ая|ое|ые|ых|ом|ую)|трет(ий|ья|ье|ьи|ьих|ьем|ью)|четвёрт\w*|пят\w*|последн\w*|предпоследн\w*|остальн\w*|оставш\w*)\s+(вариант|пункт|шаг|ответ|утверждени|запис|строк|уравнени|формул|случа|объект|элемент|позици)\w*/i;
const LETTER_REF = /\bвариант\w*\s*[«"']?[абвгдеabcdef][»"']?\b|\bпункт\w*\s*[«"']?[абвгдеabcdef][»"']?\b/i;

function coords(t) {
  const m = /^(\d)-(\d)(?:-(\d))?$/.exec(t);
  if (!m) return null;
  const s = +m[1], th = +m[2];
  return m[3] ? [[s, th, +m[3]]] : [1,2,3,4,5].map((l) => [s, th, l]);
}
let list;
if (target === '--all') { list = []; for (let s = 1; s <= 7; s++) for (let t = 1; t <= 4; t++) for (let l = 1; l <= 5; l++) list.push([s, t, l]); }
else { list = coords(target); if (!list) { console.error('bad target'); process.exit(2); } }

let totalErr = 0; const textsByTheme = new Map();
for (const [s, t, l] of list) {
  const f = path.join(base, 'lessons', `${s}-${t}-${l}.js`);
  const tag = `${subject} ${s}-${t}-${l}`;
  if (!fs.existsSync(f)) { console.log(`${tag}: MISSING`); totalErr++; continue; }
  delete require.cache[require.resolve(f)];
  let a; try { a = require(f); } catch (e) { console.log(`${tag}: LOAD ERROR ${e.message}`); totalErr++; continue; }
  const buckets = {}; let bad = 0, fbbad = 0, seq = 0, pos = 0, dup = 0;
  if (a.length !== 40) console.log(`${tag}: LEN ${a.length}`);
  a.forEach((q, i) => {
    const k = `${q.type}/${q.difficulty}`; buckets[k] = (buckets[k] || 0) + 1;
    if (k !== SEQ[Math.floor(i / 5)]) seq++;
    for (const arr of ['options', 'items', 'candidates']) (q[arr] || []).forEach((e) => { if (!String(e.text || '').trim()) bad++; });
    if (q.type === 'FillBlank') {
      if (!Array.isArray(q.blanks) || q.blanks.length < 1) fbbad++;
      if (!String(q.text).includes('___')) fbbad++;
      (q.blanks || []).forEach((b) => { if (!(q.candidates || []).some((c) => c.id === b.correctCandidateId)) fbbad++; });
      if ((q.text.match(/___/g) || []).length !== (q.blanks || []).length) fbbad++;
    }
    const info = String(q.info || '');
    if (POSITIONAL.test(info) || LETTER_REF.test(info)) { pos++; console.log(`${tag} q${i}: позиционная ссылка в info → «${info.slice(0, 90)}…»`); }
    // Дубль = та же формулировка И тот же набор вариантов/пунктов/кандидатов (порядок не важен).
    const key = `${s}-${t}`; if (!textsByTheme.has(key)) textsByTheme.set(key, new Map());
    const normText = (x) => String(x || '').toLowerCase().replace(/\s+/g, ' ').trim();
    const body = ['options', 'items', 'candidates'].flatMap((arr) => (q[arr] || []).map((e) => normText(e.text))).sort().join('|');
    const norm = normText(q.text) + '::' + body;
    const prev = textsByTheme.get(key).get(norm);
    if (prev) { dup++; console.log(`${tag} q${i}: ДУБЛЬ вопроса (текст + варианты) с ${prev}`); } else textsByTheme.get(key).set(norm, `${s}-${t}-${l} q${i}`);
  });
  const errors = [], warnings = [];
  validateLesson(buildLesson(s, t, l, 'lb', 'T', a), errors, warnings, true);
  const err = errors.length + (a.length !== 40 ? 1 : 0) + bad + fbbad + seq;
  totalErr += err + pos + dup;
  console.log(`${tag}: ${JSON.stringify(buckets)} bad=${bad} fbbad=${fbbad} seq=${seq} pos=${pos} dup=${dup} ERR=${errors.length}${errors.length ? ' ' + errors.slice(0, 3).join(' | ') : ''}${warnings.length ? ` warn=${warnings.length}` : ''}`);
}
console.log(totalErr === 0 ? 'GATE: CLEAN' : `GATE: ${totalErr} problem(s)`);
process.exit(totalErr === 0 ? 0 : 1);
