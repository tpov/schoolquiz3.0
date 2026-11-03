'use strict';
// Аудит-скрипт темы geography 1-1 (уникальное имя, не трогать чужие файлы).
// 1) снимок всех вопросов в snap-geography-1-1.json
// 2) дубли: точные (текст + варианты) и почти (та же нормализованная форма / пересечение вариантов)
// 3) позиционные ссылки своим кириллическим регэкспом (\b/\w в JS мертвы для русского)
// 4) пересчёт всех числовых утверждений темы
const fs = require('fs');
const path = require('path');

const DIR = path.join(__dirname, 'data', 'school', 'geography', 'lessons');
const LESSONS = [1, 2, 3, 4, 5];
const SUBJ = 'geography', THEME = '1-1';

const lessons = LESSONS.map((l) => require(path.join(DIR, `1-1-${l}.js`)));

// --- снимок ---
const snap = [];
lessons.forEach((qs, li) => qs.forEach((q, i) => {
  snap.push({
    lesson: `1-1-${LESSONS[li]}`, idx: i, type: q.type, diff: q.difficulty, text: q.text,
    options: (q.options || q.items || q.candidates || []).map((o) => o.text),
    correct: q.correctOptionId || (q.correctOptionIds || []).join(',') || (q.blanks || []).map((b) => b.correctCandidateId).join(','),
    info: q.info,
  });
}));
fs.writeFileSync(path.join(__dirname, 'snap-geography-1-1.json'), JSON.stringify(snap, null, 1));
console.log(`SNAPSHOT: ${snap.length} questions -> snap-geography-1-1.json`);

// --- дубли ---
const norm = (s) => String(s).toLowerCase().replace(/[^а-яёa-z0-9]+/gi, '');
console.log('\n=== DUP CHECK (exact: text + options) ===');
const seen = new Map();
snap.forEach((q) => {
  const key = norm(q.text) + '|' + q.options.map(norm).join('#');
  if (seen.has(key)) console.log(`DUP: ${q.lesson} q${q.idx} == ${seen.get(key)}`);
  else seen.set(key, `${q.lesson} q${q.idx}`);
});
console.log('--- near-dups: same normalized stem ---');
const stems = new Map();
snap.forEach((q) => {
  const k = norm(q.text);
  if (!stems.has(k)) stems.set(k, []);
  stems.get(k).push(`${q.lesson} q${q.idx}`);
});
stems.forEach((v, k) => { if (v.length > 1) console.log(`SAME STEM: ${k.slice(0, 60)} -> ${v.join(', ')}`); });
console.log('--- near-dups: option-set overlap >= 3 texts with same type ---');
const optKey = (q) => q.options.map(norm).sort().join('#');
const optMap = new Map();
snap.forEach((q) => {
  q.options.forEach((t) => {
    const k = norm(t);
    if (!optMap.has(k)) optMap.set(k, []);
    optMap.get(k).push(`${q.lesson} q${q.idx}:${q.type}`);
  });
});
// попарно: только для вопросов одного типа и не тривиально коротких вариантов
const byType = {};
snap.forEach((q) => { (byType[q.type] = byType[q.type] || []).push(q); });
Object.entries(byType).forEach(([type, qs]) => {
  for (let i = 0; i < qs.length; i++) for (let j = i + 1; j < qs.length; j++) {
    const a = qs[i], b = qs[j];
    if (norm(a.text) === norm(b.text)) continue; // уже отловлено выше
    const sa = new Set(a.options.map(norm)), sb = new Set(b.options.map(norm));
    let inter = 0; sa.forEach((x) => { if (sb.has(x)) inter++; });
    const minLen = Math.min(sa.size, sb.size);
    if (minLen >= 3 && inter >= 3) {
      const ta = a.options.map(norm).join('#').slice(0, 70), tb = b.options.map(norm).join('#').slice(0, 70);
      console.log(`OPT-OVERLAP ${inter}/${minLen}: ${a.lesson} q${a.idx} vs ${b.lesson} q${b.idx}\n   A(${a.text.slice(0, 60)}): ${ta}\n   B(${b.text.slice(0, 60)}): ${tb}`);
    }
  }
});

// --- позиционные ссылки: свой кириллический регэксп ---
console.log('\n=== POSITIONAL REFS (cyrillic regex) ===');
const ORD = '(?:перв[а-яё]*|втор[а-яё]*|трет[а-яё]*|четв[её]рт[а-яё]*|пят[а-яё]*|последн[а-яё]*|предпоследн[а-яё]*|предыдущ[а-яё]*|оставш[а-яё]*|остальн[а-яё]*)';
const NOUN = '(?:вариант[а-яё]*|пункт[а-яё]*|шаг[а-яё]*|утверждени[а-яё]*|запис[а-яё]*|строк[а-яё]*|ответ[а-яё]*|формул[а-яё]*|список[а-яё]*|списке|перечислени[а-яё]*|предложени[а-яё]*|последовательност[а-яё]*|величин[а-яё]*|значени[а-яё]*|промежутк[а-яё]*|даты|дат[ау])';
const NUM2 = '(?:два|две|двух|двум|двумя|три|трёх|трем|четыре|четырёх|четырем|обе|обоих|обеих)';
const pats = [
  [`${ORD}\\s+${NOUN}`, 'ORD+NOUN'],
  [`${NUM2}\\s+(?:первых|последних|предыдущих|оставшихся|остальных)`, 'NUM+FIRSTLAST'],
  [`(?:первых|последних|последние|перв[а-яё]*|предыдущих|оставшихся|остальных)\\s+${NUM2}\\s+(?:пункт|утверждени|вариант|величин|промежутк|шаг|запис)`, 'FIRSTLAST+NUM+NOUN'],
  [`(?:из|к|во|в|на|у)\\s+${ORD}\\b`, 'PREP+ORD'],
  [`в\\s+(?:первом|втором|третьем|четв[её]ртом|последнем|предпоследнем)\\s+(?:варианте|пункте|шаге|утверждении|случае|списке|перечислении|столбце)`, 'IN+ORD+NOUN_LOC'],
  ['соответственн[аоы]', 'SOOTVETSTVENNO'],
  ['\\b(выше|ниже)\\s+(?:вариант|пункт|утверждени|шаг)', 'ABOVEBELOW+NOUN'],
  ['(?:сумма|разность|разница)\\s+(?:этих|предыдущ|двух|трёх)', 'SUMDIFF+REF'],
  ['\\b(?:этих|эти|эти же|те же|всех|всё)\\s+(?:пункт|утверждени|вариант|шаг)', 'DEICTIC+NOUN'],
];
snap.forEach((q) => {
  const fields = [['text', q.text], ['info', q.info], ['opts', q.options.join(' ||| ')]];
  fields.forEach(([fname, val]) => {
    if (!val) return;
    pats.forEach(([p, name]) => {
      const re = new RegExp(p, 'gi');
      let m;
      while ((m = re.exec(val))) {
        const s = Math.max(0, m.index - 40);
        console.log(`POS? [${name}] ${q.lesson} q${q.idx} ${fname}: …${val.slice(s, m.index + m[0].length + 40).replace(/\n/g, ' ')}…`);
      }
    });
  });
});

// --- пересчёт числовых утверждений ---
console.log('\n=== NUMERIC RECHECK ===');
const chk = (label, actual, expected, tol = 0.02) => {
  const ok = Math.abs(actual - expected) <= (Array.isArray(expected) ? tol : Math.abs(expected) * tol + 1e-9);
  console.log(`${ok ? 'OK ' : 'FAIL'} ${label}: ${actual} (ожидалось ~${expected})`);
  return ok;
};
// урок 1-1-1
chk('свет до Земли, с', 149600000 / 300000, 498.7, 0.001);
chk('8,31 мин -> 0,31*60 с', 0.31 * 60, 18.6, 0.01);
chk('свет до Луны, с', 384400 / 300000, 1.28, 0.01);
chk('40 075 000 / 86 164 (м/с, звёздные сутки)', 40075000 / 86164, 465, 0.001);
chk('1670 км/ч в м/с', 1670000 / 3600, 464, 0.001);
chk('поверхность Луны, млн км2', 4 * Math.PI * 1737 * 1737 / 1e6, 38, 0.03);
chk('149+361', 149 + 361, 510, 0);
chk('361/510', 361 / 510, 0.71, 0.01);
chk('2*pi*6378', 2 * 3.14159 * 6378, 40074, 0.0001);
chk('40075/360', 40075 / 360, 111.3, 0.001);
chk('сжатие (6378.14-6356.75)/6378.14 -> 1/x', 1 / ((6378.14 - 6356.75) / 6378.14), 298, 0.001);
chk('40 075 - 40 008', 40075 - 40008, 67, 0);
// урок 1-1-3
chk('40 075 / 24', 40075 / 24, 1670, 0.001);
chk('1670*cos30', 1670 * Math.cos(30 * Math.PI / 180), 1446, 0.001);
chk('1670*cos60', 1670 * Math.cos(60 * Math.PI / 180), 835, 0.001);
chk('360/24', 360 / 24, 15, 0);
chk('66.5+23.5', 66.5 + 23.5, 90, 0);
// урок 1-1-4
chk('2100/4', 2100 / 4, 525, 0);
chk('2100/100', 2100 / 100, 21, 0);
chk('2100/400', 2100 / 400, 5.25, 0.001);
chk('2000/400', 2000 / 400, 5, 0);
let leap = 0; for (let y = 1901; y <= 2100; y++) if ((y % 4 === 0 && y % 100 !== 0) || y % 400 === 0) leap++;
chk('високосных 1901..2100', leap, 49, 0);
chk('орбитальная скорость, км/с', 940000000 / (365.25 * 86400), 29.8, 0.005);
chk('(152-147)/149.6', (152 - 147) / 149.6, 0.033, 0.02);
chk('свет за 10 мин, млн км', 300000 * 600 / 1e6, 180, 0);
chk('длина суток Москвы 22 июня, ч', (2 / 15) * Math.acos(-Math.tan(56 * Math.PI / 180) * Math.tan(23.5 * Math.PI / 180)) * 180 / Math.PI, 17.4, 0.01);
// урок 1-1-5
const h = (phi, dec) => 90 - Math.abs(phi - dec);
chk('h Москва 22 июня', h(56, 23.5), 57.5, 0);
chk('h Москва 22 дек', h(56, -23.5), 10.5, 0);
chk('h Москва равноденствие', h(56, 0), 34, 0);
chk('h тропик 22 июня', h(23.5, 23.5), 90, 0);
chk('h экватор равноденствие', h(0, 0), 90, 0);
chk('h полюс 22 июня', h(90, 23.5), 23.5, 0);
chk('57.5-10.5', 57.5 - 10.5, 47, 0);
chk('90-23.5', 90 - 23.5, 66.5, 0);
// продолжительность полярного дня (для нового q20 1-1-5)
const polarDayDays = (phi) => {
  const d0 = 90 - phi; // минимальное склонение для полярного дня
  if (d0 < 0) return 365; if (d0 > 23.44) return 0;
  const lam = Math.asin(d0 / 23.44) * 180 / Math.PI;
  return 365 * (2 * (90 - lam)) / 360;
};
console.log(`INFO полярный день: 66,5°=${polarDayDays(66.5).toFixed(1)} сут; 70°=${polarDayDays(70).toFixed(0)} сут (около 2 мес?); 80°=${polarDayDays(80).toFixed(0)} сут (более 4 мес?); 90°=${polarDayDays(90).toFixed(0)} сут (полгода)`);

// --- fb: вывести для ручной проверки подстановки ---
console.log('\n=== FB DUMP (подстановка кандидатов) ===');
lessons.forEach((qs, li) => qs.forEach((q, i) => {
  if (q.type !== 'FillBlank') return;
  const cands = q.candidates.map((c) => c.text).join(' | ');
  const corr = q.candidates.find((c) => c.id === q.blanks[0].correctCandidateId);
  console.log(`1-1-${LESSONS[li]} q${i}: ${q.text} => КЛЮЧ: ${corr ? corr.text : '???'} || КАНДИДАТЫ: ${cands}`);
}));
// --- оформление: ключ заметно длиннее дистракторов ---
console.log('\n=== KEY-LENGTH OUTLIERS (sc/mc) ===');
lessons.forEach((qs, li) => qs.forEach((q, i) => {
  if (q.type !== 'SingleChoice' && q.type !== 'MultipleChoice') return;
  const texts = q.options.map((o) => o.text);
  const lens = texts.map((t) => t.length);
  const keyIdx = q.type === 'SingleChoice'
    ? texts.findIndex((t, k) => 'abcdef'[k] === q.correctOptionId)
    : texts.map((t, k) => (q.correctOptionIds.includes('abcdef'[k]) ? t.length : 0));
  const keyLen = q.type === 'SingleChoice' ? lens[q.correctOptionId.charCodeAt(0) - 97] : Math.max(...keyIdx);
  const distr = q.type === 'SingleChoice' ? lens.filter((_, k) => k !== q.correctOptionId.charCodeAt(0) - 97) : lens.filter((t, k) => !q.correctOptionIds.includes('abcdef'[k]));
  const maxD = Math.max(...distr);
  if (keyLen > 55 && keyLen > maxD * 2) {
    console.log(`1-1-${LESSONS[li]} q${i}: keyLen=${keyLen} maxD=${maxD} :: ${texts.map((t, k) => `${'abcdef'[k]}${q.type === 'SingleChoice' && q.correctOptionId === 'abcdef'[k] || (q.correctOptionIds || []).includes('abcdef'[k]) ? '*' : ''}:${t.length}`).join(' ')} :: ${q.text.slice(0, 50)}`);
  }
  // пояснение в скобках только у ключа
  texts.forEach((t, k) => {
    const isKey = q.type === 'SingleChoice' ? q.correctOptionId === 'abcdef'[k] : q.correctOptionIds.includes('abcdef'[k]);
    if (/[;]/.test(t) || (/ \(/.test(t) && t.length > 45 && isKey && maxD < t.length * 0.7)) {
      // только грубые случаи: точка с запятой внутри варианта
      if (/[;]/.test(t)) console.log(`SEMICOLON-OPT 1-1-${LESSONS[li]} q${i} opt ${'abcdef'[k]}: ${t}`);
    }
  });
}));
console.log('\nAUDIT SCRIPT DONE');
