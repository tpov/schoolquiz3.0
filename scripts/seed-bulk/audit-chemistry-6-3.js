'use strict';
// Ревизорский аудит темы chemistry 6-3: почти-дубли, позиционные ссылки (кириллица),
// длина ключа vs дистракторов, баланс химических уравнений. Уникальное имя файла.
const path = require('path');
const base = path.join(__dirname, 'data', 'school', 'chemistry');
const lessons = [1, 2, 3, 4, 5].map((l) => {
  delete require.cache[require.resolve(path.join(base, 'lessons', `6-3-${l}.js`))];
  return { l, arr: require(path.join(base, 'lessons', `6-3-${l}.js`)) };
});

const norm = (x) => String(x || '').toLowerCase().replace(/\s+/g, ' ').replace(/[«»"]/g, '').trim();
const optTexts = (q) => ['options', 'items', 'candidates'].flatMap((k) => (q[k] || []).map((e) => e.text));
const tag = (l, i) => `6-3-${l} q${i}`;
const typeShort = { SingleChoice: 'sc', MultipleChoice: 'mc', Ordering: 'ord', FillBlank: 'fb' };

// ---------- 1. ПОЗИЦИОННЫЕ ССЫЛКИ (свой регэксп, кириллица, без \b/\w) ----------
// Числительные и маркеры порядка + рядом существительное из списка.
const CYR = 'А-Яа-яЁё';
const NUM = `(?:${['перв', 'втор', 'трет', 'четв', 'пят', 'шест', 'седьм', 'последн', 'предпоследн', 'остальн', 'оставш'].join('|')})[${CYR}]*`;
const NOUN = `(?:${['вариант', 'пункт', 'шаг', 'ответ', 'утверждени', 'запис', 'строк', 'уравнени', 'формул', 'случа', 'объект', 'элемент', 'позици', 'пробирк', 'столб', 'порци', 'слов', 'предложени', 'ряд', 'колонк', 'граф', 'рисунк'].join('|')})[${CYR}]*`;
const LETTER = `(?:вариант|пункт|шаг|ответ|столб|колонк)[${CYR}]*\\s*[«"']?[абвгдеa-f1-8][»"']?`;
const posRe1 = new RegExp(`[^${CYR}](${NUM})\\s+(${NOUN})`, 'gi');
const posRe2 = new RegExp(`[^${CYR}](${LETTER})`, 'gi');
const posRe3 = new RegExp(`[^${CYR}]((?:первые|последние|два|три|четыре|двух|трёх|в двух|в трёх)\\s+${NUM}\\s+${NOUN})`, 'gi');
console.log('=== ПОЗИЦИОННЫЕ ССЫЛКИ (кириллический регэксп) ===');
let posCount = 0;
for (const { l, arr } of lessons) arr.forEach((q, i) => {
  const spots = [];
  const scan = (txt, where) => {
    if (!txt) return;
    for (const re of [posRe1, posRe2, posRe3]) {
      re.lastIndex = 0;
      let m;
      while ((m = re.exec(txt))) spots.push(`info/опции ${where}: «…${txt.slice(Math.max(0, m.index - 40), m.index + 60).replace(/\n/g, ' ')}…»`);
    }
  };
  scan(String(q.info || ''), 'info');
  optTexts(q).forEach((t) => scan(String(t), 'option'));
  if (spots.length) { posCount++; spots.forEach((s) => console.log(`${tag(l, i)}: ${s}`)); }
});
if (!posCount) console.log('(совпадений нет)');

// ---------- 2. ДУБЛИ И ПОЧТИ-ДУБЛИ ----------
console.log('\n=== ТОЧНЫЕ И ПОЧТИ-ДУБЛИ ===');
const all = [];
for (const { l, arr } of lessons) arr.forEach((q, i) => all.push({ l, i, q, text: norm(q.text), opts: optTexts(q).map(norm).sort() }));
for (let a = 0; a < all.length; a++) for (let b = a + 1; b < all.length; b++) {
  const A = all[a], B = all[b];
  const setA = new Set(A.opts), inter = B.opts.filter((x) => setA.has(x)).length;
  const denom = Math.min(A.opts.length, B.opts.length) || 1;
  const optSim = inter / denom;
  const wordsA = new Set(A.text.split(' ')), wordsB = B.text.split(' ');
  const commonWords = wordsB.filter((w) => wordsA.has(w) && w.length > 3).length;
  const stemSim = commonWords / Math.max(wordsA.size, new Set(wordsB).size);
  if (A.text === B.text && optSim === 1) console.log(`ТОЧНЫЙ ДУБЛЬ: ${tag(A.l, A.i)} == ${tag(B.l, B.i)}`);
  else if (A.text === B.text || (optSim >= 0.7 && stemSim >= 0.5) || (optSim >= 0.5 && stemSim >= 0.75)) {
    console.log(`ПОЧТИ-ДУБЛЬ: ${tag(A.l, A.i)} vs ${tag(B.l, B.i)} | optSim=${optSim.toFixed(2)} stemSim=${stemSim.toFixed(2)} | «${A.text.slice(0, 70)}» vs «${B.text.slice(0, 70)}»`);
  }
}

// ---------- 3. ДЛИНА КЛЮЧА vs ДИСТРАКТОРОВ ----------
console.log('\n=== ОФОРМЛЕНИЕ: ключ длиннее дистракторов ===');
const optOf = (q, k) => (q.options || []).find((o) => o.id === k);
for (const { l, arr } of lessons) arr.forEach((q, i) => {
  const t = typeShort[q.type];
  if (t === 'sc') {
    const key = optOf(q, q.correctOptionId); if (!key) return;
    const wrong = (q.options || []).filter((o) => o.id !== q.correctOptionId);
    const maxW = Math.max(...wrong.map((o) => o.text.length));
    if (key.text.length > 1.5 * maxW && key.text.length - maxW > 20) {
      console.log(`${tag(l, i)} sc: ключ ${key.text.length} симв vs max дистрактор ${maxW} | «${key.text.slice(0, 80)}»`);
    }
  }
  if (t === 'mc') {
    const keys = (q.options || []).filter((o) => q.correctOptionIds.includes(o.id));
    const wrong = (q.options || []).filter((o) => !q.correctOptionIds.includes(o.id));
    const avgK = keys.reduce((s, o) => s + o.text.length, 0) / keys.length;
    const avgW = wrong.reduce((s, o) => s + o.text.length, 0) / wrong.length;
    if (avgK > 1.6 * avgW && avgK - avgW > 25) {
      console.log(`${tag(l, i)} mc: ср.ключ ${avgK.toFixed(0)} vs ср.дистр ${avgW.toFixed(0)}`);
    }
  }
  if (t === 'fb') {
    const correctIds = new Set((q.blanks || []).map((b) => b.correctCandidateId));
    const keys = (q.candidates || []).filter((c) => correctIds.has(c.id));
    const wrong = (q.candidates || []).filter((c) => !correctIds.has(c.id));
    const maxK = Math.max(...keys.map((c) => c.text.length));
    const maxW = Math.max(...wrong.map((c) => c.text.length));
    if (maxK > 1.9 * maxW && maxK - maxW > 15) {
      console.log(`${tag(l, i)} fb: ключ «${keys.find((c) => c.text.length === maxK).text}» (${maxK}) vs max дистр ${maxW}`);
    }
  }
});

// ---------- 4. БАЛАНС УРАВНЕНИЙ ----------
console.log('\n=== БАЛАНС УРАВНЕНИЙ (пересчёт) ===');
const SUB = { '₀': '0', '₁': '1', '₂': '2', '₃': '3', '₄': '4', '₅': '5', '₆': '6', '₇': '7', '₈': '8', '₉': '9' };
const fixSub = (s) => s.replace(/[₀-₉]/g, (c) => SUB[c]);

function parseFormula(f) {
  // возвращает карту элементов или null, если формула не атомарная (ионы, электроны, заряды)
  if (/[⁺⁻ē]|^\s*e\s*$/.test(f)) return null;
  f = f.replace(/[↑↓]/g, '').replace(/\((?:конц|разб|тв|гор|насыщ|р-р|s|g|l|sol)\)/gi, '').trim();
  if (!f) return null;
  if (f.includes('·')) { // гидраты/стекло: Na2O·CaO·6SiO2 = сумма частей с коэффициентами
    const total = {};
    for (const part of f.split('·')) {
      const mm = /^(\d*)(.*)$/.exec(part.trim());
      const sub = parseFormula(mm[2]);
      if (!sub) return null;
      const k = mm[1] ? parseInt(mm[1], 10) : 1;
      for (const [el, n] of Object.entries(sub)) total[el] = (total[el] || 0) + n * k;
    }
    return total;
  }
  const stack = [{}];
  let i = 0;
  const push = (el, n) => { const top = stack[stack.length - 1]; top[el] = (top[el] || 0) + n; };
  while (i < f.length) {
    const c = f[i];
    if (c === '(' || c === '[') { stack.push({}); i++; continue; }
    if (c === ')' || c === ']') {
      i++; let num = '';
      while (i < f.length && /\d/.test(f[i])) { num += f[i]; i++; }
      const grp = stack.pop(); if (!grp) return null;
      const mult = num ? parseInt(num, 10) : 1;
      const top = stack[stack.length - 1];
      for (const [el, n] of Object.entries(grp)) top[el] = (top[el] || 0) + n * mult;
      continue;
    }
    if (/[A-Z]/.test(c)) {
      let el = c; i++;
      while (i < f.length && /[a-z]/.test(f[i])) { el += f[i]; i++; }
      let num = '';
      while (i < f.length && /\d/.test(f[i])) { num += f[i]; i++; }
      push(el, num ? parseInt(num, 10) : 1);
      continue;
    }
    if (c === '·' || c === '*') { // гидраты/стекло: интерпретируем как сумму частей
      i++; let num = ''; const start = i;
      while (i < f.length && /\d/.test(f[i])) { num += f[i]; i++; }
      if (!num) { i = start; } // '·' без числа — дальше просто формула
      continue;
    }
    return null; // незнакомый символ
  }
  if (stack.length !== 1) return null;
  return stack[0];
}

function balanceCheck(eq) {
  // вырезаем условия стрелок и аннотации
  let s = eq.replace(/\((?:t[^)]*|hν|электролиз|свет|t)[^)]*\)/gi, ''); // →(t), ⇄(t, V₂O₅)
  s = s.replace(/\((?:конц|разб|тв|гор|насыщ|электролиз)[^)]*\)/gi, '');
  s = s.replace(/[→⇄⇌=]+/g, '=').replace(/−|–/g, '-');
  const sides = s.split('=').map((x) => x.trim());
  if (sides.length !== 2) return { skip: 'не два члена' };
  const parseSide = (side) => {
    const map = {};
    for (let tok of side.split('+')) {
      tok = tok.trim().replace(/^[↑↓]/, '');
      if (!tok) continue;
      if (/ē|[⁺⁻]/.test(tok)) return null; // полуреакция/заряды — пропускаем целиком
      const m = /^(\d*)\s*(.+)$/.exec(tok);
      if (!m) return null;
      const coef = m[1] ? parseInt(m[1], 10) : 1;
      const fm = parseFormula(fixSub(m[2]));
      if (!fm) return null;
      for (const [el, n] of Object.entries(fm)) map[el] = (map[el] || 0) + n * coef;
    }
    return map;
  };
  const L = parseSide(sides[0]), R = parseSide(sides[1]);
  if (!L || !R) return { skip: 'не парсится' };
  const els = new Set([...Object.keys(L), ...Object.keys(R)]);
  const diff = [];
  for (const el of els) if ((L[el] || 0) !== (R[el] || 0)) diff.push(`${el}:${L[el] || 0}≠${R[el] || 0}`);
  return diff.length ? { bad: diff.join(' ') } : { ok: true };
}

const EQ = /=|→|⇄/;
const T = String.raw`\d*\s*[A-Z][a-z]?\d*(?:\([A-Za-z0-9.,]+\)\d*|\([А-Яа-яЁё.]+\)\d*|·\d*[A-Z][a-z]?\d*|\d*[A-Z][a-z]?\d*)*`;
const EQRE = new RegExp(`${T}(?:\\s*\\+\\s*${T})+\\s*[=→⇄⇌]\\s*${T}(?:\\s*\\+\\s*${T})*`, 'g');
let badCount = 0, checked = 0;
for (const { l, arr } of lessons) arr.forEach((q, i) => {
  const texts = [q.text, q.info, ...optTexts(q)].map(String);
  for (const t of texts) {
    let clean = fixSub(t.replace(/[↑↓]/g, '').replace(/\((?:t[^)]*|hν|электролиз|свет)[^)]*\)/gi, ''));
    let m;
    EQRE.lastIndex = 0;
    while ((m = EQRE.exec(clean))) {
      const eq = m[0].replace(/\((?:конц|разб|тв|гор|насыщ)[^)]*\)/gi, '');
      if (eq.length < 5) continue;
      checked++;
      const res = balanceCheck(eq);
      if (res.skip || res.ok) continue;
      badCount++;
      console.log(`${tag(l, i)} НЕСБАЛАНС: ${eq.trim().slice(0, 120)} | ${res.bad}`);
    }
  }
});
console.log(`(уравнений проверено: ${checked}, несбалансированных: ${badCount})`);
