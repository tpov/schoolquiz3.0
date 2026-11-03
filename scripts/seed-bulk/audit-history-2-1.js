'use strict';
// audit-history-2-1.js — содержательный аудит темы history 2-1 (уникальное имя, параллельно работают другие агенты).
// 1) fb-vs-sc/mc дубли (стем-нормализация русских основ + Jaccard), 2) ord-перенасыщение/подмножества,
// 3) позиционные ссылки (кириллический регэксп), 4) оформление ключа, 5) полные дубли в теме.
const path = require('path');
const L = (n) => require(path.join(__dirname, 'data/school/history/lessons', `${n}.js`));

const THEME = ['2-1-1', '2-1-2', '2-1-3', '2-1-4', '2-1-5'];

const STOP = new Set(['как', 'что', 'какой', 'какая', 'какие', 'каковы', 'где', 'когда', 'кому', 'кого', 'чем', 'кто',
  'это', 'эта', 'эти', 'этот', 'этим', 'этом', 'того', 'том', 'та', 'те', 'тем', 'тому',
  'для', 'при', 'про', 'под', 'над', 'был', 'была', 'были', 'было', 'быть', 'будет',
  'его', 'ее', 'их', 'им', 'него', 'нее', 'ними', 'не', 'ни', 'но', 'а', 'и', 'же', 'ли', 'бы', 'то', 'так', 'такой', 'такие',
  'все', 'весь', 'вся', 'свои', 'свое', 'свой', 'своя', 'один', 'одна', 'одно', 'два', 'три',
  'верн', 'выбер', 'назван', 'относ', 'располож', 'событи', 'порядк', 'хронологическ']);

function norm(s) {
  return String(s).toLowerCase().replace(/ё/g, 'е')
    .replace(/[^a-zа-я0-9\s]/g, ' ').replace(/\s+/g, ' ').trim();
}
function stems(s) {
  return norm(s).split(' ').filter(Boolean)
    .filter((w) => !STOP.has(w))
    .map((w) => (w.length > 5 ? w.slice(0, 5) : w))
    .filter((w) => w.length >= 3);
}
function jac(a, b) {
  const A = new Set(a); const B = new Set(b);
  if (!A.size || !B.size) return 0;
  let inter = 0; A.forEach((x) => { if (B.has(x)) inter += 1; });
  return inter / (A.size + B.size - inter);
}

function qtext(q) { return q.text; }
function qinfo(q) { return q.info || ''; }

// --- позиционные ссылки: \b и \w в JS не работают с кириллицей, свои классы [а-яё] ---
const POSRE = '(^|[^а-яё])(перв|втор|треть|четверт|последн|остальн|предпоследн|предыдущ|следующ|соответственн)';
const POSOBJ = '(пункт|вариант|утверждени|высказыван|сужден|шаг|способ|случа|перечислен|список|запис|строк|дат)';
function posCheck(q, tag) {
  const hits = [];
  const fields = [['text', qtext(q)], ['info', qinfo(q)]];
  (q.options || []).forEach((o) => fields.push(['opt:' + o.id, o.text]));
  (q.items || []).forEach((o) => fields.push(['item:' + o.id, o.text]));
  fields.forEach(([f, v]) => {
    const n = norm(v);
    const sents = n.split(/[.;!?]/).map((x) => x.trim()).filter(Boolean);
    for (const s of sents) {
      const m = s.match(new RegExp(POSRE + '[а-яё]*\\s+(?:[а-яё-]+\\s+){0,2}?' + POSOBJ));
      const variantLetter = /(^|[^а-яё])вариант[ауе]?\s+[абвг]($|[^а-яё])/.test(s);
      const respectively = /(^|[^а-яё])соответственн/.test(s) && /(пункт|вариант|утвержден|шаг)/.test(s);
      if (m || variantLetter || respectively) {
        hits.push(`${tag} [${f}]: «${s.slice(0, 140)}»`);
        break;
      }
    }
  });
  return hits;
}

// --- оформление ключа ---
function keyLenCheck(q, tag) {
  const hits = [];
  if (q.type === 'SingleChoice') {
    const key = q.options.find((o) => o.id === q.correctOptionId);
    const others = q.options.filter((o) => o.id !== q.correctOptionId).map((o) => norm(o.text).length);
    const maxO = Math.max(...others, 1);
    const kLen = norm(key.text).length;
    if (kLen >= 1.5 * maxO && kLen - maxO >= 6) hits.push(`${tag} sc ключ ${kLen} против макс ${maxO}: «${key.text}»`);
    if (/\(/.test(key.text)) {
      const otherParen = q.options.some((o) => o.id !== q.correctOptionId && /\(/.test(o.text));
      if (!otherParen) hits.push(`${tag} sc ключ со скобками, у дистракторов нет: «${key.text}»`);
    }
  }
  if (q.type === 'FillBlank') {
    const cid = q.blanks[0].correctCandidateId;
    const key = q.candidates.find((o) => o.id === cid);
    const others = q.candidates.filter((o) => o.id !== cid).map((o) => norm(o.text).length);
    const maxO = Math.max(...others, 1);
    const kLen = norm(key.text).length;
    if (kLen >= 1.5 * maxO && kLen - maxO >= 6) hits.push(`${tag} fb ключ ${kLen} против макс ${maxO}: «${key.text}»`);
    if (/\(/.test(key.text)) {
      const otherParen = q.candidates.some((o) => o.id !== cid && /\(/.test(o.text));
      if (!otherParen) hits.push(`${tag} fb ключ со скобками, у кандидатов нет: «${key.text}»`);
    }
  }
  return hits;
}

// --- ord overlap ---
function ordKey(item) {
  const nums = (item.match(/\d{1,4}/g) || []).join(',');
  const words = stems(item.replace(/\d+[–\-]?\d*\s*(гг?\.?|век|в\.)/g, ' '));
  return nums + '|' + words.slice(0, 6).join(' ');
}
function ordOverlap(a, b) {
  const A = a.map(ordKey); const B = b.map(ordKey);
  const inter = A.filter((x) => B.includes(x)).length;
  const minN = Math.min(A.length, B.length);
  const uni = new Set([...A, ...B]).size;
  return { inter, minN, minShare: inter / minN, jac: inter / uni, verbatim: A.filter((x) => B.includes(x)).length };
}

// --- хронология ord: годы должны строго расти ---
function ordYears(q) {
  const ys = q.items.map((it) => {
    const m = it.text.match(/(\d{1,4})\s*(?:гг?\.|год[ау]?)?\s*\)?\s*$/);
    const all = it.text.match(/(\d{3,4})/g) || [];
    return { first: all.length ? parseInt(all[0], 10) : null, last: m ? parseInt(m[1], 10) : (all.length ? parseInt(all[all.length - 1], 10) : null) };
  });
  return ys;
}
function ordChronCheck(name, qi, q) {
  const hits = [];
  const ys = ordYears(q);
  const nums = ys.map((y) => y.first);
  if (nums.some((n) => n === null)) { hits.push(`CHRON? ${name} ord#${qi}: недатированный пункт: ${q.items.map((i) => i.text).join(' | ')}`); return hits; }
  for (let k = 1; k < nums.length; k++) {
    if (nums[k] <= nums[k - 1]) hits.push(`CHRON ${name} ord#${qi}: год ${nums[k]} не позже ${nums[k - 1]} (${q.items[k].text})`);
  }
  return hits;
}

function run(names) {
  const data = names.map((n) => ({ name: n, qs: L(n) }));
  const report = [];
  data.forEach(({ name, qs }) => {
    const fbs = qs.map((q, i) => [i, q]).filter(([, q]) => q.type === 'FillBlank');
    const scs = qs.map((q, i) => [i, q]).filter(([, q]) => q.type === 'SingleChoice' || q.type === 'MultipleChoice');
    fbs.forEach(([fi, f]) => {
      const fkey = norm(f.candidates.find((c) => c.id === f.blanks[0].correctCandidateId).text);
      const fst = stems(qtext(f));
      const fopt = f.candidates.map((c) => norm(c.text));
      scs.forEach(([si, s]) => {
        const keys = s.type === 'SingleChoice'
          ? [norm(s.options.find((o) => o.id === s.correctOptionId).text)]
          : s.correctOptionIds.map((id) => norm(s.options.find((o) => o.id === id).text));
        const sameKey = keys.some((k) => k === fkey || (k.length > 5 && fkey.length > 5 && (k.includes(fkey) || fkey.includes(k))));
        if (!sameKey) return;
        const sj = jac(fst, stems(qtext(s)));
        const oo = jac(fopt, s.options.map((o) => norm(o.text)));
        if (sj >= 0.25 || oo >= 0.5) {
          report.push(`DUP? ${name} fb#${fi} ↔ ${s.type === 'SingleChoice' ? 'sc' : 'mc'}#${si} key=«${fkey}» stemJ=${sj.toFixed(2)} optJ=${oo.toFixed(2)}`);
          report.push(`    fb: ${qtext(f)}`);
          report.push(`    ${s.type === 'SingleChoice' ? 'sc' : 'mc'}: ${qtext(s)}`);
        }
      });
    });
    const sms = qs.map((q, i) => [i, q]).filter(([, q]) => q.type === 'SingleChoice' || q.type === 'MultipleChoice');
    for (let x = 0; x < sms.length; x++) for (let y = x + 1; y < sms.length; y++) {
      const [xi, a] = sms[x]; const [yi, b] = sms[y];
      const j = jac(stems(qtext(a)), stems(qtext(b)));
      if (j >= 0.6) {
        const ka = a.type === 'SingleChoice' ? a.correctOptionId : a.correctOptionIds.join(',');
        const kb = b.type === 'SingleChoice' ? b.correctOptionId : b.correctOptionIds.join(',');
        if (ka === kb) { report.push(`DUP? ${name} ${a.type[0]}c#${xi} ↔ ${b.type[0]}c#${yi} stemJ=${j.toFixed(2)} SAMEKEY`); report.push(`    A: ${qtext(a)}`); report.push(`    B: ${qtext(b)}`); }
      }
    }
    for (let x = 0; x < fbs.length; x++) for (let y = x + 1; y < fbs.length; y++) {
      const [xi, a] = fbs[x]; const [yi, b] = fbs[y];
      const j = jac(stems(qtext(a)), stems(qtext(b)));
      if (j >= 0.6 && a.blanks[0].correctCandidateId === b.blanks[0].correctCandidateId) {
        report.push(`DUP? ${name} fb#${xi} ↔ fb#${yi} stemJ=${j.toFixed(2)} sameBlank`);
        report.push(`    A: ${qtext(a)}`); report.push(`    B: ${qtext(b)}`);
      }
    }
  });
  data.forEach(({ name, qs }) => {
    const ords = qs.map((q, i) => [i, q]).filter(([, q]) => q.type === 'Ordering');
    for (let x = 0; x < ords.length; x++) for (let y = x + 1; y < ords.length; y++) {
      const [xi, a] = ords[x]; const [yi, b] = ords[y];
      const o = ordOverlap(a.items.map((i) => i.text), b.items.map((i) => i.text));
      if (o.minShare >= 0.6 || o.jac >= 0.5) {
        report.push(`ORD ${name} ord#${xi} ↔ ord#${yi}: inter=${o.inter}/${o.minN} min=${(o.minShare * 100) | 0}% jac=${o.jac.toFixed(2)} verbatim=${o.verbatim}`);
      }
    }
    ords.forEach(([qi, q]) => ordChronCheck(name, qi, q).forEach((h) => report.push(h)));
  });
  for (let x = 0; x < data.length; x++) for (let y = x; y < data.length; y++) {
    const A = data[x]; const B = data[y];
    const oa = A.qs.map((q, i) => [i, q]).filter(([, q]) => q.type === 'Ordering');
    const ob = B.qs.map((q, i) => [i, q]).filter(([, q]) => q.type === 'Ordering');
    for (const [xi, a] of oa) for (const [yi, b] of ob) {
      if (x === y && xi >= yi) continue;
      const o = ordOverlap(a.items.map((i) => i.text), b.items.map((i) => i.text));
      if (o.jac >= 0.5 || (o.minShare >= 0.75 && o.inter === o.minN)) {
        report.push(`ORDX ${A.name} ord#${xi} ↔ ${B.name} ord#${yi}: inter=${o.inter}/${o.minN} min=${(o.minShare * 100) | 0}% jac=${o.jac.toFixed(2)}`);
      }
    }
    // полные дубли вопросов (текст+варианты) между уроками темы
    for (const [xi, a] of A.qs.map((q, i) => [i, q])) for (const [yi, b] of B.qs.map((q, i) => [i, q])) {
      if (x === y) break;
      const sig = (q) => norm(qtext(q)) + '|' + (q.options || q.candidates || q.items || []).map((o) => norm(o.text)).join('~');
      if (sig(a) === sig(b)) report.push(`FULLDUP ${A.name}#${xi} ↔ ${B.name}#${yi}`);
    }
  }
  data.forEach(({ name, qs }) => qs.forEach((q, i) => {
    posCheck(q, `${name}#${i}`).forEach((h) => report.push('POS ' + h));
    keyLenCheck(q, `${name}#${i}`).forEach((h) => report.push('FMT ' + h));
  }));
  return report;
}

console.log('=== Тема history 2-1 ===');
console.log(run(THEME).join('\n'));
