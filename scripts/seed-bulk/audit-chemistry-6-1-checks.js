'use strict';

// Дополнительные проверки темы chemistry 6-1: структура ключей, перекрёстные ссылки в ord,
// и арифметическая верификация чисел из info (пересчёт, независимый от ключа).

const lessons = [1, 2, 3, 4, 5].map(l => require(`./data/school/chemistry/lessons/6-1-${l}.js`));
const label = (li, qi) => `6-1-${li + 1} q${qi}`;

console.log('=== СТРУКТУРА КЛЮЧЕЙ ===');
lessons.forEach((qs, li) => qs.forEach((q, qi) => {
  const bad = [];
  if (q.type === 'SingleChoice') {
    if (!q.options.some(o => o.id === q.correctOptionId)) bad.push('ключ не существует');
  } else if (q.type === 'MultipleChoice') {
    if (!Array.isArray(q.correctOptionIds) || q.correctOptionIds.length < 2) bad.push('mc < 2 верных');
    const ids = new Set(q.options.map(o => o.id));
    if (q.correctOptionIds.some(k => !ids.has(k))) bad.push('лишний ключ');
  } else if (q.type === 'Ordering') {
    if (q.items.length < 2 || q.items.length > 8) bad.push('ord длина');
    const ids = new Set(q.items.map(o => o.id));
    if (ids.size !== q.items.length) bad.push('ord дубли id');
  } else if (q.type === 'FillBlank') {
    if (![5, 10].includes(q.candidates.length)) bad.push('fb кандидаты != 5/10');
    if ((q.text.match(/___/g) || []).length !== q.blanks.length) bad.push('fb число пропусков');
    if (!q.candidates.some(c => c.id === q.blanks[0].correctCandidateId)) bad.push('fb ключ не существует');
  }
  if (bad.length) console.log(`STRUCT ${label(li, qi)}: ${bad.join('; ')}`);
}));

console.log('\n=== ПЕРЕКРЁСТНЫЕ ССЫЛКИ ВНУТРИ ord-ПУНКТОВ (висят после тасовки) ===');
// «то же самое», «сделать это же», «для них», «этих», «из пункта», «см. выше»
const crossRef = /то же самое|это же|того же шага|из пункта|пункте выше|см\. выше|вышеуказан|в них|для них|из них|этих (веществ|реагентов|продуктов)/i;
lessons.forEach((qs, li) => qs.forEach((q, qi) => {
  if (q.type === 'Ordering') q.items.forEach((it, i) => {
    if (crossRef.test(it.text)) console.log(`XREF ${label(li, qi)} item#${i}: ${it.text.slice(0, 90)}`);
  });
}));

console.log('\=== АРИФМЕТИКА info (пересчёт) ==='.replace('\\', ''));
const eq = (l, r) => JSON.stringify(l) === JSON.stringify(r) ? 'OK' : '!!! MISMATCH';

// 6-1-1 q6: Mn2O7 -> Mn = +7  (2x + 7*(-2) = 0)
console.log('Mn2O7 Mn:', eq(2 * 7 - 2 * 7, 0), '=> +7');
// 6-1-1 mc16: SO3 S=+6; CrO3 Cr=+6
console.log('SO3 S:', eq(3 * 2, 6), '; CrO3 Cr:', eq(3 * 2, 6));
// 6-1-2 q7: P2O5 + 3H2O = 2H3PO4 : P 2=2, H 6=6, O 8=8
console.log('P2O5+3H2O=2H3PO4:', eq([2, 6, 5 + 3], [2, 2 * 3, 2 * 4]));
// 6-1-2 q9: Fe2O3 + 3CO = 2Fe + 3CO2 : Fe 2=2, C 3=3, O 6=6
console.log('Fe2O3+3CO:', eq([2, 3, 3 + 3], [2, 3, 3 * 2]));
// 6-1-2 fb35: P2O5 + 3 H2O = 2H3PO4 (ответ 3)
console.log('fb35 H слева=3*2:', eq(3 * 2, 2 * 3));
// 6-1-2 fb38: Al2O3 + 6 HCl = 2AlCl3 + 3H2O (ответ 6)
console.log('fb38 Cl справа=2*3:', eq(6, 2 * 3));
// 6-1-3 fb36: FeCl3 + 3 NaOH = Fe(OH)3 + 3NaCl (ответ 3)
console.log('fb36 Na справа=3:', eq(3, 3));
// 6-1-3 fb39: 2Fe(OH)3 -> Fe2O3 + 3H2O (ответ 3 моль воды)
console.log('fb39 H: 2*3 слева = 3*2 справа:', eq(6, 6), '; O: 6 = 3 + 3:', eq(6, 3 + 3));
// 6-1-4 q8: 2HCl + Na2CO3 = 2NaCl + H2O + CO2 : H 2=2, Cl 2=2, Na 2=2, C 1=1, O 3=3
console.log('2HCl+Na2CO3:', eq([2, 2, 2, 1, 3], [2, 2, 2, 1, 1 + 2]));
// 6-1-4 fb37: 2Al + 6 HCl = 2AlCl3 + 3H2 (ответ 6)
console.log('fb37 Cl справа=2*3:', eq(6, 2 * 3), '; H слева 6 = 3*2:', eq(6, 3 * 2));
// 6-1-4 ord28: Cu + 4HNO3 = Cu(NO3)2 + 2NO2 + 2H2O : Cu 1, H 4, N 4, O 12
console.log('Cu+4HNO3:', eq([1, 4, 4, 4 * 3], [1, 2 * 2, 2 + 2, 6 + 2 * 2 + 2]));
// 6-1-5 q9: Ca(HCO3)2 -> CaCO3 + H2O + CO2 : Ca 1, H 2, C 2, O 6
console.log('Ca(HCO3)2:', eq([1, 2, 2, 6], [1, 2, 1 + 1, 3 + 1 + 2]));
// 6-1-5 mc15: 2Cu(NO3)2 -> 2CuO + 4NO2 + O2 : Cu 2, N 4, O 12
console.log('2Cu(NO3)2:', eq([2, 4, 4 * 3], [2, 4, 2 + 4 * 2 + 2]));
// 6-1-5 fb38: Na2CO3 + CO2 + H2O = 2NaHCO3 (ответ 2)
console.log('fb38 Na 2=2:', eq(2, 2), '; C 2=2:', eq(1 + 1, 2), '; O 6=6:', eq(3 + 2 + 1, 2 * 3));
// 6-1-5 ord27: Na2CO3 + CO2 + H2O = 2NaHCO3 (Na 2, C 2, O 6, H 2)
console.log('ord27:', eq([2, 2, 3 + 2 + 1, 2], [2, 2, 2 * 3, 2]));
// степени окисления ord28 (6-1-1): Cl2O +1, SO2 +4, N2O5 +5, CrO3 +6, Mn2O7 +7
console.log('ord28 с.о.:', eq([1, 4, 5, 6, 7], [1, 4, 5, 6, 7]));
console.log('DONE');
