'use strict';
// Ревизия темы chemistry 4-4: почти-дупы, позиционные ссылки (кириллица), fb-подстановки.
// Уникальные имена: audit-chemistry-4-4.js / snap-chemistry-4-4.json
const fs = require('fs');
const path = require('path');

const base = path.join(__dirname, 'data', 'school', 'chemistry');
const lessons = [1, 2, 3, 4, 5].map((l) => {
  delete require.cache[require.resolve(path.join(base, 'lessons', `4-4-${l}.js`))];
  return require(path.join(base, 'lessons', `4-4-${l}.js`));
});

const norm = (x) => String(x || '').toLowerCase().replace(/\s+/g, ' ').replace(/[«»]/g, '').trim();
const tokens = (x) => norm(x).split(/[^a-zа-яё0-9⁺⁻²³⁴⁷₃₄₇₂]+/i).filter(Boolean);
const opts = (q) => ['options', 'items', 'candidates'].flatMap((a) => (q[a] || []).map((e) => e.text));

// ---- 1. Точные и почти-дупы ----
const flat = [];
lessons.forEach((a, li) => a.forEach((q, i) => flat.push({ l: li + 1, i, q, text: q.text, body: opts(q) })));

const exact = new Map();
const jaccard = (a, b) => { const A = new Set(a), B = new Set(b); const inter = [...A].filter((x) => B.has(x)).length; return inter / (A.size + B.size - inter); };

console.log('=== ТОЧНЫЕ ДУПИ (текст + все варианты, как гейт) ===');
for (let i = 0; i < flat.length; i++) {
  const key = norm(flat[i].text) + '::' + flat[i].body.map(norm).sort().join('|');
  if (exact.has(key)) console.log(`4-4-${flat[i].l} q${flat[i].i} == 4-4-${exact.get(key).l} q${exact.get(key).i}`);
  else exact.set(key, flat[i]);
}

console.log('\n=== ПОЧТИ-ДУПИ (текст ≥0.6 по жаккару ИЛИ одинаковые стемы; варианты ≥0.8 как множество) ===');
for (let i = 0; i < flat.length; i++) for (let j = i + 1; j < flat.length; j++) {
  const A = flat[i], B = flat[j];
  const tt = jaccard(tokens(A.text), tokens(B.text));
  const bo = jaccard(A.body.map(norm), B.body.map(norm));
  if (tt >= 0.6 && bo >= 0.8) console.log(`HIGH 4-4-${A.l} q${A.i} ~ 4-4-${B.l} q${B.i}  text=${tt.toFixed(2)} opts=${bo.toFixed(2)}\n   A: ${A.text}\n   B: ${B.text}`);
  else if (tt >= 0.72 || (tt >= 0.55 && bo >= 0.65)) console.log(`MED  4-4-${A.l} q${A.i} ~ 4-4-${B.l} q${B.i}  text=${tt.toFixed(2)} opts=${bo.toFixed(2)}\n   A: ${A.text}\n   B: ${B.text}`);
}

// ---- 2. Позиционные ссылки (кириллический регэксп, не гейтовский) ----
const ORD = '(перв|втор|трет|четвёрт|четверт|пят|шест|седьм|последн|предпоследн|предыдущ|следующ|остальн|оставш)';
const NOUN = '(вариант|пункт|шаг|ответ|утверждени|запис|строк|уравнени|формул|случа|объект|элемент|позици|половин|часть|схем|полуреакци|пункт)';
const NUMWORD = '(два|две|три|четыре|пять|оба|обе)';
const rePos1 = new RegExp('[а-яё]*' + ORD + '[а-яё]*\\s+' + NOUN, 'i');
const rePos2 = new RegExp(NUMWORD + '\\s+' + ORD + '[а-яё]*\\s+' + NOUN, 'i');
const rePos3 = new RegExp(ORD + '[а-яё]*\\s+' + NUMWORD, 'i'); // «первые три», «последние два»
const reLetter = /[а-яё]*вариант[а-яё]*\s*[«"']?[абвгдеabcdef][»"']?|[а-яё]*пункт[а-яё]*\s*[«"']?\d/ig;
console.log('\n=== ПОЗИЦИОННЫЕ ССЫЛКИ (свой регэксп: info + варианты + пункты) ===');
for (const { l, i, q } of flat) {
  const fields = [['info', q.info], ...opts(q).map((t, k) => [`opt${k}`, t])];
  for (const [fname, val] of fields) {
    const s = String(val || '');
    for (const [re, tag] of [[rePos1, 'P1'], [rePos2, 'P2'], [rePos3, 'P3'], [reLetter, 'LET']]) {
      const m = s.match(re);
      if (m) console.log(`4-4-${l} q${i} [${fname} ${tag}] «${m[0]}»  ⟶ ${s.slice(0, 110)}`);
    }
  }
}

// ---- 3. fb: подстановка каждого кандидата ----
console.log('\n=== FB: кандидаты (ровно 5/10, единственный корректный id) ===');
const fbSeen = new Map();
for (const { l, i, q } of flat) {
  if (q.type !== 'FillBlank') continue;
  const cand = q.candidates || [];
  const blanks = (String(q.text).match(/___/g) || []).length;
  if (cand.length !== 5 && cand.length !== 10) console.log(`4-4-${l} q${i}: кандидатов ${cand.length}!`);
  if (blanks !== (q.blanks || []).length) console.log(`4-4-${l} q${i}: ___=${blanks} vs blanks=${q.blanks.length}`);
  const ids = new Set(cand.map((c) => c.id));
  if (ids.size !== cand.length) console.log(`4-4-${l} q${i}: дубли id кандидатов`);
  for (const b of q.blanks || []) if (!ids.has(b.correctCandidateId)) console.log(`4-4-${l} q${i}: correctCandidateId ${b.correctCandidateId} не найден`);
  // эквивалентные кандидаты численно
  const canon = (t) => norm(t).replace(/,/g, '.').replace(/^\+/, '').replace(/г$|моль$|л$|Кл\/моль$/, '').trim();
  const seen = new Map();
  for (const c of cand) { const k = canon(c.text); if (seen.has(k)) console.log(`4-4-${l} q${i}: кандидаты «${seen.get(k)}» и «${c.text}» эквивалентны после нормировки`); else seen.set(k, c.text); }
  // одинаковый набор кандидатов + одинаковый верный ответ у двух fb → кандидат в дупы
  const ckey = cand.map((c) => canon(c.text)).sort().join('|') + '#' + norm(cand.find((c) => c.id === (q.blanks[0] || {}).correctCandidateId)?.text || '');
  if (fbSeen.has(ckey)) console.log(`FB-DUP? 4-4-${l} q${i} ~ 4-4-${fbSeen.get(ckey).l} q${fbSeen.get(ckey).i} — тот же набор кандидатов и тот же верный ответ\n   A: ${q.text}\n   B: ${fbSeen.get(ckey).q.text}`);
  else fbSeen.set(ckey, { l, i, q });
}

// ---- 4. Пересчёт ключевых чисел ----
console.log('\n=== ПЕРЕСЧЁТ ЧИСЕЛ ===');
const F = 96500;
console.log('Cu 5A 965s n=2:', 64 * 5 * 965 / (2 * F), '(заявлено 1,6)');
console.log('Ag 9650 Кл n=1:', 9650 / F * 108, '(заявлено 10,8)');
console.log('Cu 2A 1800s n=2:', 64 * 2 * 1800 / (2 * F), '(заявлено ~1,19)');
console.log('H2 9650 Кл n=2:', 22.4 * 9650 / (2 * F), '(заявлено 1,12 л)');
console.log('Ag 1.5A 1200s n=1:', 108 * 1.5 * 1200 / (1 * F), '(заявлено ~2,01)');
// балансы уравнений (O/H/N подсчёт)
const cnt = (eq) => { throw new Error('unused'); };
function check(name, left, right) { console.log(name, JSON.stringify(left) === JSON.stringify(right) ? 'OK' : `FAIL ${JSON.stringify(left)} vs ${JSON.stringify(right)}`); }
const tally = (side) => {
  const t = {};
  const add = (el, n) => { t[el] = (t[el] || 0) + n; };
  const scan = (formula, mult) => {
    let mm2; const re2 = /([A-Z][a-z]?)(\d*)|[(]([^()]*)[)](\d*)/g;
    while ((mm2 = re2.exec(formula))) {
      if (mm2[1]) add(mm2[1], mult * (+(mm2[2] || 1)));
      else if (mm2[3] !== undefined) {
        let mm3; const re3 = /([A-Z][a-z]?)(\d*)/g;
        while ((mm3 = re3.exec(mm2[3]))) add(mm3[1], mult * (+(mm2[4] || 1)) * (+(mm3[2] || 1)));
      }
    }
  };
  for (const term0 of side.split('+')) {
    const term = term0.trim().replace(/[↑↓]/g, '').replace(/\(.*?\)$/, (m) => m); // аннотации среды в скобках в конце — уже убраны ниже
    const clean = term.replace(/\((?:конц|разб|оч\. разб|расплав|электролиз|гор|хол|t°|криолит)\)/gi, '').trim();
    const mm = /^(\d*)\s*(.+)$/.exec(clean);
    scan(mm[2].replace(/\s+/g, ''), +(mm[1] || 1));
  }
  return t;
};
const canonT = (t) => Object.keys(t).sort().map((k) => k + t[k]).join(',');
const eq = (s) => s.split('→').map((x) => x.trim());
const run = (name, s) => { const [L, R] = eq(s); check(name, canonT(tally(L)), canonT(tally(R))); };
run('Al+O2', '4Al + 3O2 → 2Al2O3');
run('Cu+HNO3 разб', '3Cu + 8HNO3 → 3Cu(NO3)2 + 2NO + 4H2O');
run('KMnO4+HCl', '2KMnO4 + 16HCl → 2KCl + 2MnCl2 + 5Cl2 + 8H2O');
run('KClO3', '2KClO3 → 2KCl + 3O2');
run('Cu+HNO3 конц', 'Cu + 4HNO3 → Cu(NO3)2 + 2NO2 + 2H2O');
run('Zn оч.разб', '4Zn + 10HNO3 → 4Zn(NO3)2 + NH4NO3 + 3H2O');
run('Cu+H2SO4 конц', 'Cu + 2H2SO4 → CuSO4 + SO2 + 2H2O');
run('H2O2+KMnO4', '5H2O2 + 2KMnO4 + 3H2SO4 → 2MnSO4 + K2SO4 + 5O2 + 8H2O');
run('Cl2+KOH гор', '3Cl2 + 6KOH → 5KCl + KClO3 + 3H2O');
run('Cl2+NaOH хол', 'Cl2 + 2NaOH → NaCl + NaClO + H2O');
run('P+HNO3', '3P + 5HNO3 + 2H2O → 3H3PO4 + 5NO');
run('NH3+O2', '4NH3 + 3O2 → 2N2 + 6H2O');
run('SO3^2- кислая', '2KMnO4 + 5Na2SO3 + 3H2SO4 → 2MnSO4 + 5Na2SO4 + K2SO4 + 3H2O');
run('SO3^2- нейтр', '2KMnO4 + 3Na2SO3 + H2O → 2MnO2 + 3Na2SO4 + 2KOH');
run('SO3^2- щел', '2KMnO4 + Na2SO3 + 2KOH → 2K2MnO4 + Na2SO4 + H2O');
run('Mg+O2', '2Mg + O2 → 2MgO');
run('NaCl раствор', '2NaCl + 2H2O → H2 + Cl2 + 2NaOH');
run('CuSO4 электр', '2CuSO4 + 2H2O → 2Cu + O2 + 2H2SO4');
run('H2S+SO2', '2H2S + SO2 → 3S + 2H2O');
run('Fe2O3+CO', 'Fe2O3 + 3CO → 2Fe + 3CO2');
run('Al2O3', '2Al2O3 → 4Al + 3O2');
run('CuCl2', 'CuCl2 → Cu + Cl2');
console.log('snap: записи уроков:', flat.length);
fs.writeFileSync(path.join(__dirname, 'snap-chemistry-4-4.json'), JSON.stringify(flat.map(({ l, i, q, text, body }) => ({ l, i, type: q.type, diff: q.difficulty, text, body, info: q.info })), null, 1));
console.log('snap-chemistry-4-4.json записан');
