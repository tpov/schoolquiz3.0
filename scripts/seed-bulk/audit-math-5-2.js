'use strict';
// Уникальный временный скрипт аудита темы math 5-2 (ревизор). НЕ для продакшена.
const path = require('path');
const base = path.join(__dirname, 'data', 'school', 'math', 'lessons');
const L = {};
for (let i = 1; i <= 5; i++) L[i] = require(path.join(base, `5-2-${i}.js`));

let fails = 0;
function eq(label, actual, expected) {
  const a = typeof actual === 'number' ? +actual.toFixed(6) : actual;
  const e = typeof expected === 'number' ? +expected.toFixed(6) : expected;
  if (a !== e) { console.log(`FAIL ${label}: got ${a}, want ${e}`); fails++; }
}
const S3 = (a) => Math.sqrt(a);

// ---------- 5-2-1 (призма) ----------
{
  const q = L[1];
  // sc5..sc9
  eq('1-5 P', 2 * (3 + 4), 14); eq('1-5 Sбок', 14 * 10, 140);
  eq('1-6 P', 4 * 5, 20); eq('1-6 Sполн', 20 * 8 + 2 * 25, 210);
  eq('1-7 Sбок', 3 * 6 * 10, 180);
  eq('1-8 Г', 2 - 12 + 18, 8);
  eq('1-9 Sбок', 12 * 9, 108);
  // mc15..mc19
  eq('1-15 hyp', S3(36 + 64), 10); eq('1-15 P', 24, 24); eq('1-15 Sосн', 0.5 * 48, 24);
  eq('1-15 Sбок', 24 * 10, 240); eq('1-15 Sполн', 240 + 48, 288);
  eq('1-16 Sбок', 16 * 6, 96); eq('1-16 Sполн', 96 + 32, 128); eq('1-16 рёбра', 8 * 4 + 4 * 6, 56);
  eq('1-17 Sбок', 12 * 9, 108); eq('1-17 ошиб.', 12 * 6, 72);
  eq('1-18', `${12},${18},${8}`, `12,18,8`);
  eq('1-19 Sбок', 12 * 5, 60); eq('1-19 Sосн', (Math.sqrt(3) / 4) * 16, 4 * Math.sqrt(3));
  eq('1-19 Sполн', 60 + 2 * 4 * Math.sqrt(3), 60 + 8 * Math.sqrt(3));
  // fb
  eq('1-35', 4 * 6 * 7, 168);
  eq('1-36 В', 2 + 24 - 10, 16);
  eq('1-37', 6 * 3 * 10, 180);
  eq('1-38 h', (260 - 2 * 30) / 20, 10);
  eq('1-39', 7 * 12, 84);
  // keys
  const keyOf = (qq, id) => qq.options.find((o) => o.id === id).text;
  eq('1-5 key', keyOf(q[5], 'c'), '140');
  eq('1-6 key', keyOf(q[6], 'a'), '210');
  eq('1-7 key', keyOf(q[7], 'd'), '180');
  eq('1-8 key', keyOf(q[8], 'b'), '8');
  eq('1-9 key', keyOf(q[9], 'd'), '108');
  const mcHas = (i, texts) => { const t = q[i].options.filter((o) => q[i].correctOptionIds.includes(o.id)).map((o) => o.text); eq(`1-${i} mc`, t.sort().join('|'), texts.sort().join('|')); };
  mcHas(15, ['гипотенуза основания равна 10', 'периметр основания равен 24', 'площадь боковой поверхности равна 240']);
  mcHas(16, ['площадь боковой поверхности равна 96', 'сумма длин всех рёбер равна 56', 'площадь полной поверхности равна 128']);
  mcHas(17, ['площадь боковой поверхности равна 108', 'у наклонной призмы высота меньше бокового ребра', 'боковые грани наклонной призмы — параллелограммы']);
  mcHas(18, ['вершин 12', 'рёбер 18', 'граней 8']);
  mcHas(19, ['S(бок) = 60', 'S(осн) = 4√3', 'S(полн) = 60 + 8√3']);
  const fbVal = (i) => q[i].candidates.find((c) => c.id === q[i].blanks[0].correctCandidateId).text;
  eq('1-35 fb', fbVal(35), '168'); eq('1-36 fb', fbVal(36), '16'); eq('1-37 fb', fbVal(37), '180');
  eq('1-38 fb', fbVal(38), '10'); eq('1-39 fb', fbVal(39), '84');
}

// ---------- 5-2-2 (параллелепипед) ----------
{
  const q = L[2];
  eq('2-5 d', S3(9 + 16 + 144), 13);
  eq('2-6 S', 2 * (12 + 48 + 36), 192);
  eq('2-7 S', 6 * 25, 150);
  eq('2-8 a', 6, 6);
  eq('2-9 d', S3(36 + 36 + 49), 11);
  eq('2-15 d', S3(4 + 9 + 36), 7); eq('2-15 S', 2 * (6 + 18 + 12), 72); eq('2-15 V', 36, 36);
  eq('2-16 d', 4 * Math.sqrt(3), 4 * Math.sqrt(3)); eq('2-16 dnum', + (4 * Math.sqrt(3)).toFixed(2), 6.93); eq('2-16 S', 6 * 16, 96); eq('2-16 V', 64, 64);
  eq('2-17 c', S3(676 - 64 - 36), 24); eq('2-17 dосн', S3(64 + 36), 10); eq('2-17 сеч', 10 * 24, 240);
  eq('2-18 a', S3(54 / 6), 3); eq('2-18 V', 27, 27); eq('2-18 d', +(3 * Math.sqrt(3)).toFixed(2), 5.2);
  eq('2-19 d', S3(1 + 4 + 4), 3); eq('2-19 S', 2 * (2 + 4 + 2), 16); eq('2-19 V', 4, 4);
  eq('2-26 диаг', [S3(1 + 4 + 4), S3(4 + 9 + 36), S3(9 + 16 + 144), S3(64 + 81 + 144)].join(','), '3,7,13,17');
  eq('2-29', `${6 * 36}`, '216');
  const keyOf = (i, id) => q[i].options.find((o) => o.id === id).text;
  eq('2-5 key', keyOf(5, 'a'), '13'); eq('2-6 key', keyOf(6, 'c'), '192');
  eq('2-7 key', keyOf(7, 'd'), '150'); eq('2-8 key', keyOf(8, 'b'), '6'); eq('2-9 key', keyOf(9, 'c'), '11');
  const mcHas = (i, texts) => { const t = q[i].options.filter((o) => q[i].correctOptionIds.includes(o.id)).map((o) => o.text); eq(`2-${i} mc`, t.sort().join('|'), texts.sort().join('|')); };
  mcHas(15, ['диагональ равна 7', 'площадь полной поверхности равна 72', 'объём равен 36']);
  mcHas(16, ['диагональ куба равна 4√3', 'диагональ грани равна 4√2', 'площадь поверхности равна 96', 'объём равен 64']);
  mcHas(17, ['третье измерение равно 24', 'диагональ основания равна 10', 'площадь диагонального сечения равна 240']);
  mcHas(18, ['ребро куба равно 3', 'объём куба равен 27', 'диагональ куба равна 3√3']);
  mcHas(19, ['диагональ равна 3', 'площадь полной поверхности равна 16', 'объём равен 4']);
  const fbVal = (i) => q[i].candidates.find((c) => c.id === q[i].blanks[0].correctCandidateId).text;
  eq('2-35 fb', fbVal(35), '7'); eq('2-36 fb', fbVal(36), '52'); eq('2-37 fb', fbVal(37), '5√2');
  eq('2-38 fb', fbVal(38), '96'); eq('2-39 fb', fbVal(39), '81'); eq('2-39 d2', 16 + 16 + 49, 81);
}

// ---------- 5-2-3 (пирамида) ----------
{
  const q = L[3];
  eq('3-5 Sбок', 0.5 * 24 * 5, 60);
  eq('3-6 Sполн', 60 + 36, 96);
  eq('3-7 h', S3(25 - 9), 4);
  eq('3-8 b', S3(16 + 18), S3(34)); eq('3-8 R2', (6 * S3(2) / 2) ** 2, 18);
  eq('3-9 Sбок', 0.5 * 18 * 4, 36);
  eq('3-15 P', 32, 32); eq('3-15 Sбок', 0.5 * 32 * 5, 80); eq('3-15 h', S3(25 - 16), 3); eq('3-15 Sполн', 80 + 64, 144);
  eq('3-16 l', S3(16 + 9), 5); eq('3-16 Sбок', 0.5 * 24 * 5, 60); eq('3-16 V', 36 * 4 / 3, 48);
  eq('3-17 r', 0.5, 0.5); eq('3-17 R', S3(2) / 2, S3(2) / 2); eq('3-17 l2', 1 + 0.25, 1.25);
  eq('3-19 P', 24, 24); eq('3-19 Sбок', 0.5 * 24 * 6, 72); eq('3-19 Sосн', 1.5 * S3(3) * 16, 24 * S3(3));
  eq('3-23 ряды', [0.5 * 8 * 3, 0.5 * 16 * 3, 0.5 * 16 * 6, 0.5 * 24 * 8].join(','), '12,24,48,96');
  eq('3-27 h(a)', [S3(100 - 64), S3(100 - 36), S3(100 - 25), S3(100 - 4)].map((x) => +x.toFixed(2)).join(','), [6, 8, 8.66, 9.8].join(','));
  eq('3-29 шаги', [3 * 6, 0.5 * 18 * 4, 18 * 4, 0.5 * 18 * 4].join(','), '18,36,72,36');
  const keyOf = (i, id) => q[i].options.find((o) => o.id === id).text;
  eq('3-5 key', keyOf(5, 'a'), '60'); eq('3-6 key', keyOf(6, 'b'), '96'); eq('3-7 key', keyOf(7, 'c'), '4');
  eq('3-8 key', keyOf(8, 'd'), '√34'); eq('3-9 key', keyOf(9, 'c'), '36');
  const mcHas = (i, texts) => { const t = q[i].options.filter((o) => q[i].correctOptionIds.includes(o.id)).map((o) => o.text); eq(`3-${i} mc`, t.sort().join('|'), texts.sort().join('|')); };
  mcHas(15, ['периметр основания равен 32', 'площадь боковой поверхности равна 80', 'высота пирамиды равна 3']);
  mcHas(16, ['апофема равна 5', 'площадь боковой поверхности равна 60', 'объём равен 48']);
  mcHas(17, ['r = a/2', 'R = a√2/2', 'l² = h² + a²/4']);
  mcHas(18, ['когда все боковые рёбра равны', 'когда боковые рёбра образуют равные углы с плоскостью основания', 'когда пирамида правильная']);
  mcHas(19, ['периметр основания равен 24', 'площадь боковой поверхности равна 72', 'площадь основания равна 24√3']);
  const fbVal = (i) => q[i].candidates.find((c) => c.id === q[i].blanks[0].correctCandidateId).text;
  eq('3-35 fb', fbVal(35), '72'); eq('3-36 fb', fbVal(36), '12'); eq('3-37 fb', fbVal(37), 'a√2/2');
  eq('3-38 fb', fbVal(38), '4√3'); eq('3-39 fb', fbVal(39), '120');
}

// ---------- 5-2-4 (сечения) ----------
{
  const q = L[4];
  eq('4-5 S', (S3(3) / 4) * 72, 18 * S3(3));
  eq('4-7 k', (1 / 3) ** 2, 1 / 9);
  eq('4-8 s2', (4 * S3(2) / 2) ** 2, 8); eq('4-8 S', 1.5 * S3(3) * 8, 12 * S3(3));
  eq('4-9 S', 5 * 5 * S3(2), 25 * S3(2));
  eq('4-15 S', (S3(3) / 4) * 72, 18 * S3(3));
  eq('4-16 k2', 4, 4); eq('4-16 k3', 8, 8);
  eq('4-19 сторона', 12 * 0.5, 6); eq('4-19 Sсеч', 36, 36); eq('4-19 Sосн', 144, 144);
  eq('4-23 ряд', [6, +(6 * S3(2)).toFixed(2), +(6 * S3(3)).toFixed(2), 24].join(','), `6,${+(6 * Math.sqrt(2)).toFixed(2)},${+(6 * Math.sqrt(3)).toFixed(2)},24`);
  eq('4-26 ряд', [+((S3(3) / 4) * 32).toFixed(2), 16, +(12 * S3(3)).toFixed(2), +(16 * S3(2)).toFixed(2)].join(','),
    [+(8 * Math.sqrt(3)).toFixed(2), 16, +(12 * Math.sqrt(3)).toFixed(2), +(16 * Math.sqrt(2)).toFixed(2)].join(','));
  eq('4-29 ряд', [144 / 16, 144 / 9, 144 / 4, 144 * 9 / 16].join(','), '9,16,36,81');
  const keyOf = (i, id) => q[i].options.find((o) => o.id === id).text;
  eq('4-5 key', keyOf(5, 'a'), '18√3'); eq('4-7 key', keyOf(7, 'd'), 'в 9 раз');
  eq('4-8 key', keyOf(8, 'c'), '12√3'); eq('4-9 key', keyOf(9, 'b'), 'прямоугольником площадью 25√2');
  const mcHas = (i, texts) => { const t = q[i].options.filter((o) => q[i].correctOptionIds.includes(o.id)).map((o) => o.text); eq(`4-${i} mc`, t.sort().join('|'), texts.sort().join('|')); };
  mcHas(15, ['сечение является треугольником', 'все его стороны равны 6√2', 'его площадь равна 18√3']);
  mcHas(16, ['площадь сечения в 4 раза меньше площади основания', 'объём отсечённой пирамиды в 8 раз меньше объёма исходной', 'сечение подобно основанию']);
  mcHas(17, ['диагональное сечение — прямоугольник со сторонами 4 и 4√2', 'сечение через середины шести рёбер — правильный шестиугольник', 'сечение, параллельное грани, — квадрат площадью 16']);
  mcHas(18, ['след — прямая пересечения секущей плоскости с плоскостью основания', 'для построения следа линии сечения продлевают до плоскости основания', 'след помогает найти точки пересечения секущей плоскости с рёбрами']);
  mcHas(19, ['сторона сечения равна 6', 'площадь сечения равна 36', 'площадь основания равна 144']);
  const fbVal = (i) => q[i].candidates.find((c) => c.id === q[i].blanks[0].correctCandidateId).text;
  eq('4-35 fb', fbVal(35), '60'); eq('4-36 fb', fbVal(36), '(√3/4)·a²');
  eq('4-37 fb', fbVal(37), 'k²'); eq('4-38 fb', fbVal(38), 'следом'); eq('4-39 fb', fbVal(39), 'делённой');
}

// ---------- 5-2-5 (усечённая пирамида) ----------
{
  const q = L[5];
  eq('5-5 Sбок', 0.5 * (32 + 8) * 5, 100);
  eq('5-6 h', S3(25 - 9), 4);
  eq('5-7 Эйлер', 4 - 6 + 4, 2);
  eq('5-8 Sбок', 0.5 * (40 + 16) * 6, 168);
  eq('5-9 V', (1 / 3) * 6 * (27 + S3(324) + 12), 114);
  eq('5-15 Sполн', 100 + 64 + 4, 168); eq('5-15 h', S3(25 - 9), 4);
  eq('5-16 наборы', [5 - 10 + 4, 7 - 9 + 3, 8 - 12 + 6, 4 - 6 + 4, 6 - 12 + 8].join(','), '-1,1,2,2,2');
  eq('5-19 l', S3(16 + 9), 5); eq('5-19 Sбок', 0.5 * (40 + 16) * 5, 140);
  eq('5-23 ряд', [0.5 * 12 * 5, 0.5 * 24 * 5, 0.5 * 32 * 5, 0.5 * 40 * 5].join(','), '30,60,80,100');
  eq('5-27 вершины', '4,6,8,12,20', '4,6,8,12,20');
  eq('5-29 ряд', [4 + 2 + 1, 9 + 6 + 4, 16 + 8 + 4, 25 + 15 + 9].join(','), '7,19,28,49');
  const keyOf = (i, id) => q[i].options.find((o) => o.id === id).text;
  eq('5-5 key', keyOf(5, 'b'), '100'); eq('5-6 key', keyOf(6, 'c'), '4');
  eq('5-7 key', keyOf(7, 'd'), '2'); eq('5-8 key', keyOf(8, 'c'), '168'); eq('5-9 key', keyOf(9, 'a'), '114');
  const mcHas = (i, texts) => { const t = q[i].options.filter((o) => q[i].correctOptionIds.includes(o.id)).map((o) => o.text); eq(`5-${i} mc`, t.sort().join('|'), texts.sort().join('|')); };
  mcHas(15, ['периметр большего основания равен 32', 'площадь боковой поверхности равна 100', 'высота равна 4']);
  mcHas(16, ['куб: 8, 12, 6', 'тетраэдр: 4, 6, 4', 'октаэдр: 6, 12, 8']);
  mcHas(17, ['площади оснований относятся как k²', 'объёмы отсечённой и исходной пирамид относятся как k³', 'периметры оснований относятся как k']);
  mcHas(18, ['берут полусумму сторон вместо полусуммы периметров', 'подставляют высоту вместо апофемы', 'забывают множитель 1/2', 'используют периметр только одного основания']);
  mcHas(19, ['апофема равна 5', 'площадь боковой поверхности равна 140', 'площади оснований равны 100 и 16']);
  const fbVal = (i) => q[i].candidates.find((c) => c.id === q[i].blanks[0].correctCandidateId).text;
  eq('5-35 fb', fbVal(35), 'суммы периметров'); eq('5-36 fb', fbVal(36), '160');
  eq('5-37 fb', fbVal(37), '(r₁ − r₂)²'); eq('5-38 fb', fbVal(38), '2'); eq('5-39 fb', fbVal(39), '78');
}

// ---------- поиск смысловых дублей (жаккар по токенам) ----------
const stop = new Set(['и', 'в', 'на', 'с', 'по', 'для', 'из', 'у', 'что', 'как', 'или', 'равно', 'равна', 'равны']);
function toks(s) {
  return String(s).toLowerCase().replace(/[°√²³()=+—·:,?.]/g, ' ').split(/\s+/)
    .filter((t) => t && !stop.has(t));
}
function jac(a, b) {
  const A = new Set(toks(a)), B = new Set(toks(b));
  let inter = 0; for (const x of A) if (B.has(x)) inter++;
  return inter / (A.size + B.size - inter);
}
const flat = [];
for (let l = 1; l <= 5; l++) L[l].forEach((q, i) => flat.push({ id: `5-2-${l} q${i}`, type: q.type, text: q.text, body: (q.options || q.items || q.candidates || []).map((e) => e.text).join(' | ') }));
const pairs = [];
for (let i = 0; i < flat.length; i++) for (let j = i + 1; j < flat.length; j++) {
  const jt = jac(flat[i].text, flat[j].text);
  const jb = flat[i].body && flat[j].body ? jac(flat[i].body, flat[j].body) : 0;
  if (jt > 0.5 || (jt > 0.35 && jb > 0.35)) pairs.push([jt.toFixed(2), jb.toFixed(2), flat[i].id, flat[j].id, flat[i].type === flat[j].type ? 'same-type' : 'cross-type', flat[i].text.slice(0, 60), '||', flat[j].text.slice(0, 60)]);
}
pairs.sort((a, b) => b[0] - a[0]);
console.log('\n=== ПОХОЖИЕ ПАРЫ (по убыванию) ===');
pairs.slice(0, 60).forEach((p) => console.log(p.join(' ')));

// ---------- проверка ПЕРЕПИСАННЫХ вопросов (после правок ревизора) ----------
{
  const fbVal = (qq, i) => qq[i].candidates.find((c) => c.id === qq[i].blanks[0].correctCandidateId).text;
  const uniq = (qq, i, val) => {
    const others = qq[i].candidates.filter((c) => c.id !== qq[i].blanks[0].correctCandidateId).map((c) => c.text);
    if (others.includes(val)) { console.log(`FAIL 5-2 dup-cand`); fails++; }
  };
  const L1fb = fbVal(L[1], 30); eq('NEW 1-30', L1fb, 'рёбрами'); uniq(L[1], 30, L1fb);
  const L2fb = fbVal(L[2], 31); eq('NEW 2-31', L2fb, 'a²'); uniq(L[2], 31, L1fb);
  const L3 = L[3];
  eq('NEW 3-31', fbVal(L3, 31), 'боковыми рёбрами');
  eq('NEW 3-32', fbVal(L3, 32), 'высоту');
  eq('NEW 3-33', fbVal(L3, 33), 'равнобедренные');
  eq('NEW 3-35', fbVal(L3, 35), '72'); eq('NEW 3-35 calc', 0.5 * 36 * 4, 72);
  eq('NEW 3-36', fbVal(L3, 36), '12'); eq('NEW 3-36 calc', Math.sqrt(169 - 25), 12);
  eq('NEW 3-38', fbVal(L3, 38), '4√3'); eq('NEW 3-38 calc', Math.sqrt(16 + 32), 4 * Math.sqrt(3));
  eq('NEW 3-39', fbVal(L3, 39), '120'); eq('NEW 3-39 calc', 0.5 * 30 * 8, 120);
  const L4 = L[4];
  eq('NEW 4-30', fbVal(L4, 30), 'секущей');
  eq('NEW 4-31', fbVal(L4, 31), '16'); eq('NEW 4-31 calc', 64 * (1 / 2) ** 2, 16);
  eq('NEW 4-32', fbVal(L4, 32), 'рёбрах');
  eq('NEW 4-33', fbVal(L4, 33), 'граней');
  eq('NEW 4-34', fbVal(L4, 34), 'проекцией');
  eq('NEW 4-35', fbVal(L4, 35), '60'); eq('NEW 4-35 calc', 30 / Math.cos(Math.PI / 3), 60);
  eq('NEW 4-25 ord', [Math.sqrt(2) ** 2, (3 * Math.sqrt(3) / 2) * 8].map((x) => +x.toFixed(3)).join(','), '2,20.785');
  eq('NEW 4-25 last', 12 * Math.sqrt(3), (3 * Math.sqrt(3) / 2) * 8);
  const L5 = L[5];
  eq('NEW 5-30', fbVal(L5, 30), 'пятиугольниками');
  eq('NEW 5-31', fbVal(L5, 31), 'двенадцати');
  eq('NEW 5-32', fbVal(L5, 32), 'семи');
  eq('NEW 5-36', fbVal(L5, 36), '160'); eq('NEW 5-36 calc', 0.5 * (48 + 16) * 5, 160);
  eq('NEW 5-39', fbVal(L5, 39), '78'); eq('NEW 5-39 calc', (1 / 3) * 6 * (25 + 10 + 4), 78);
  eq('NEW 5-26 ord V', (1 / 3) * 6 * (36 + 30 + 25), 182); eq('NEW 5-26 prod', 36 * 25, 900);
  const o22 = L[5][22];
  eq('NEW 5-22 рёбра', [3 * 3, 3 * 4, 3 * 5, 3 * 6].join(','), '9,12,15,18');
  const o22t = o22.items.map((it) => it.text).join('|');
  if (!o22t.includes('— 9 рёбер') || !o22t.includes('— 18 рёбер') || o22.text.includes('Эйлера')) { console.log('FAIL NEW 5-22 ord items'); fails++; }
  if (o22.difficulty !== 'EASY' || o22.type !== 'Ordering') { console.log('FAIL NEW 5-22 type/diff'); fails++; }
  // NEW 5-4 q0: дистракторы удлинены, ключ не изменился
  const o40 = L[4][0];
  if (o40.options.find((o) => o.id === o40.correctOptionId).text.length < 60 || o40.options.some((o) => o.text.length < 30)) { console.log('FAIL NEW 4-0 lengths'); fails++; }
  // NEW 4-23: все пункты — отрезки, ряд строго возрастает
  eq('NEW 4-23 ряд', [3 * Math.sqrt(3), 6, 6 * Math.sqrt(2), 6 * Math.sqrt(3)].map((x) => +x.toFixed(2)).join(','), [+(3 * Math.sqrt(3)).toFixed(2), 6, +(6 * Math.sqrt(2)).toFixed(2), +(6 * Math.sqrt(3)).toFixed(2)].join(','));
  if ((+(3 * Math.sqrt(3)).toFixed(2)) >= 6 || L[4][23].items.map((i) => i.text).join(' ').includes('периметр')) { console.log('FAIL NEW 4-23 items'); fails++; }
  // NEW 5-11 info: нет ложного «призма правильной не бывает»
  if (L[5][11].info.includes('не бывает')) { console.log('FAIL NEW 5-11 info'); fails++; }
}

// ---------- позиционные ссылки: СВОЙ кириллический регэксп (\b/\w в JS не работают с русским) ----------
console.log('\n=== ПОЗИЦИОННЫЕ ССЫЛКИ (свой регэксп) ===');
{
  const ORD = '(перв[а-яё]*|втор[а-яё]*|трет[а-яё]*|четвёрт[а-яё]*|пят[а-яё]*|шест[а-яё]*|седьм[а-яё]*|последн[а-яё]*|предпоследн[а-яё]*|остальн[а-яё]*|оставш[а-яё]*|соседн[а-яё]*|предыдущ[а-яё]*)';
  const NOUN = '(вариант|пункт|шаг|ответ|утверждени|запис|строк|формул|случа|элемент|позици|половин|граф|столб|рисунк|строч|уравнени|неравенств|список|порядк|способ|признак|свойств)';
  const reA = new RegExp(ORD + '\\s+' + NOUN, 'gi');                  // «первые три пункта», «последний шаг»
  const reB = new RegExp('(в|во|на|из|со|с)\\s+' + ORD, 'gi'); // «в первом», «во втором», «в последнем»
  const reC = /(два|три|четыре|пять)\s+(последн|перв)[а-яё]*/gi;      // «два последних»
  const reD = /(вариант|пункт)\s*[«"']?\s*[абвгде]\b/gi;              // «вариант б»
  const reE = /перв[а-яё]*\s+(три|два|четыре|пять)|соответственн/gi;  // «первые три», «соответственно»
  let hits = 0;
  const scan = (label, s) => {
    if (!s) return;
    const found = [...(s.match(reA) || []), ...(s.match(reB) || []), ...(s.match(reC) || []), ...(s.match(reD) || []), ...(s.match(reE) || [])];
    if (found.length) { hits++; console.log(`${label}: [${found.join(' | ')}] :: «${String(s).slice(0, 150)}»`); }
  };
  for (let l = 1; l <= 5; l++) L[l].forEach((q, i) => {
    const tag = `5-2-${l} q${i}`;
    scan(`${tag} info`, q.info);
    scan(`${tag} text`, q.text);
    (q.options || []).forEach((o) => scan(`${tag} opt ${o.id}`, o.text));
    (q.items || []).forEach((o) => scan(`${tag} item ${o.id}`, o.text));
    (q.candidates || []).forEach((o) => scan(`${tag} cand ${o.id}`, o.text));
  });
  if (!hits) console.log('(позиционных ссылок не найдено)');
}

// ---------- численная эквивалентность вариантов (второй верный: 0,5 vs 1/2 vs 50 %) ----------
console.log('\n=== ЧИСЛЕННАЯ ЭКВИВАЛЕНТНОСТЬ ВАРИАНТОВ ===');
{
  function parseNum(s) {
    const t = String(s).toLowerCase().replace(/,/g, '.').replace(/\s|≈|~/g, '');
    let m = /^(\d*(?:\.\d+)?)\*?√(\d+)(?:\/(\d+))?$/.exec(t);
    if (m) return (m[1] ? parseFloat(m[1]) : 1) * Math.sqrt(+m[2]) / (m[3] ? +m[3] : 1);
    m = /^(\d+)\/(\d+)$/.exec(t); if (m) return +m[1] / +m[2];
    m = /^(\d+(?:\.\d+)?)%$/.exec(t); if (m) return +m[1] / 100;
    if (/^-?\d+(?:\.\d+)?$/.test(t)) return parseFloat(t);
    return null;
  }
  let hits = 0;
  for (let l = 1; l <= 5; l++) L[l].forEach((q, i) => {
    const opts = (q.options || q.candidates || []).map((e) => e.text);
    const nums = opts.map(parseNum);
    for (let a = 0; a < opts.length; a++) for (let b = a + 1; b < opts.length; b++) {
      if (nums[a] !== null && nums[b] !== null && Math.abs(nums[a] - nums[b]) < 1e-9) {
        hits++; console.log(`5-2-${l} q${i}: «${opts[a]}» == «${opts[b]}» (${nums[a]})`);
      }
    }
    // приближённые совпадения (округления): разница < 0.5%
    for (let a = 0; a < opts.length; a++) for (let b = a + 1; b < opts.length; b++) {
      if (nums[a] !== null && nums[b] !== null && nums[a] !== 0 && Math.abs(nums[a] - nums[b]) / Math.abs(nums[a]) < 0.005 && Math.abs(nums[a] - nums[b]) > 1e-9) {
        hits++; console.log(`5-2-${l} q${i} (близко): «${opts[a]}» ≈ «${opts[b]}» (${nums[a]} vs ${nums[b]})`);
      }
    }
  });
  if (!hits) console.log('(численно эквивалентных пар вариантов нет)');
}

// ---------- оформление: ключ заметно длиннее дистракторов ----------
console.log('\n=== КЛЮЧ ЗАМЕТНО ДЛИННЕЕ ДИСТРАКТОРОВ (sc) ===');
{
  let hits = 0;
  for (let l = 1; l <= 5; l++) L[l].forEach((q, i) => {
    if (q.type !== 'SingleChoice') return;
    const corr = q.options.find((o) => o.id === q.correctOptionId);
    const others = q.options.filter((o) => o.id !== q.correctOptionId);
    const cl = corr.text.length;
    const mx = Math.max(...others.map((o) => o.text.length));
    if (cl > 60 && cl > mx * 1.8) { hits++; console.log(`5-2-${l} q${i}: ключ ${cl} симв, максимум у дистракторов ${mx} :: «${corr.text}»`); }
  });
  if (!hits) console.log('(оформление, выдающее ответ, не найдено)');
}

// ---------- точные дубли (дублирующая проверка гейта) ----------
console.log('\n=== ТОЧНЫЕ ДУБЛИ ===');
{
  const norm = (s) => String(s || '').toLowerCase().replace(/[ё]/g, 'е').replace(/[^a-zа-я0-9]+/g, ' ').trim();
  const seen = new Map(); let d = 0;
  for (let l = 1; l <= 5; l++) L[l].forEach((q, i) => {
    const body = (q.options || q.items || q.candidates || []).map((e) => norm(e.text)).sort().join('|');
    const key = norm(q.text) + '::' + body;
    if (seen.has(key)) { d++; console.log(`ДУБЛЬ: 5-2-${l} q${i} == ${seen.get(key)}`); } else seen.set(key, `5-2-${l} q${i}`);
  });
  if (!d) console.log('(точных дублей нет)');
}

// ---------- ссылки между вариантами / на номера ----------
console.log('\n=== ССЫЛКИ МЕЖДУ ВАРИАНТАМИ ===');
{
  const re = /предыдущ|следующ[а-яё]*\s+(вариант|пункт|шаг)|этих\s+(двух|трёх)|сумма\s+предыдущ|(вариант|пункт|утверждени|столб|граф|строк)[а-яё]*\s*[«']?\s*\d|из\s+них|остальны[ех]|оба\s+варианта|на\s+рисунке|на\s+чертеже/i;
  let hits = 0;
  for (let l = 1; l <= 5; l++) L[l].forEach((q, i) => {
    const f = { text: q.text, info: q.info };
    (q.options || []).forEach((o) => (f['opt ' + o.id] = o.text));
    (q.items || []).forEach((o) => (f['item ' + o.id] = o.text));
    (q.candidates || []).forEach((o) => (f['cand ' + o.id] = o.text));
    for (const [k, v] of Object.entries(f)) {
      const m = String(v || '').match(re);
      if (m) { hits++; console.log(`5-2-${l} q${i} ${k}: [${m.join('|')}] :: ${String(v).slice(0, 110)}`); }
    }
  });
  if (!hits) console.log('(ссылок между вариантами не найдено)');
}

console.log(fails === 0 ? '\nALL NUMERIC CHECKS OK' : `\n${fails} NUMERIC FAILS`);
process.exit(0);
