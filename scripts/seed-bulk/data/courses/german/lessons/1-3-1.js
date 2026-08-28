'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY =====
  sc(0, 'EASY', 'Welche Zahl ist das: 3?', op4('drei', 'zwei', 'vier', 'fünf'), 'a', 'Цифра 3 по-немецки — drei.'),
  sc(1, 'EASY', 'Welche Zahl ist das: 7?', op4('fünf', 'acht', 'sieben', 'sechs'), 'c', 'Цифра 7 по-немецки — sieben.'),
  sc(2, 'EASY', 'Welche Zahl ist das: 0?', op4('null', 'eins', 'neun', 'zehn'), 'a', 'Цифра 0 по-немецки — null.'),
  sc(3, 'EASY', 'Welche Zahl ist das: 10?', op4('elf', 'zwölf', 'neun', 'zehn'), 'd', 'Цифра 10 по-немецки — zehn.'),
  sc(4, 'EASY', 'Welche Zahl ist das: 5?', op4('vier', 'fünf', 'sechs', 'drei'), 'b', 'Цифра 5 по-немецки — fünf.'),

  // ===== 5 sc HARD =====
  sc(5, 'HARD', 'Wie schreibt man die Zahl 12 korrekt?', op4('zwoelf', 'zwölf', 'zwölv', 'zwelf'), 'b', 'Двенадцать пишется через умляут — zwölf.'),
  sc(6, 'HARD', 'Welches Wort steht für die Zahl 8?', op4('acht', 'echt', 'auch', 'aber'), 'a', 'Восемь по-немецки — acht.'),
  sc(7, 'HARD', 'Was ist die richtige Schreibweise für 5?', op4('funf', 'fuenf', 'fünf', 'fünff'), 'c', 'Пять пишется с умляутом — fünf.'),
  sc(8, 'HARD', 'Ergänze: «Eins, zwei, ___» — was kommt jetzt?', op4('vier', 'null', 'drei', 'sechs'), 'c', 'После eins, zwei идёт drei.'),
  sc(9, 'HARD', 'Welche Form ist richtig vor einem Wort: «___ Buch»?', op4('eins', 'ein', 'eine', 'einer'), 'b', 'Перед существительным eins превращается в ein.'),

  // ===== 5 mc EASY =====
  mc(10, 'EASY', 'Welche Wörter sind Zahlen?', op5('drei', 'Hund', 'sieben', 'rot', 'neun'), ['a', 'c', 'e'], 'Числа здесь — drei, sieben и neun.'),
  mc(11, 'EASY', 'Welche Wörter bedeuten eine Zahl?', op5('zwei', 'vier', 'gut', 'acht', 'Tag'), ['a', 'b', 'd'], 'Zwei, vier и acht — это числа.'),
  mc(12, 'EASY', 'Welche Zahlen sind kleiner als 5?', op5('eins', 'zwei', 'zehn', 'drei', 'acht'), ['a', 'b', 'd'], 'Меньше пяти — eins, zwei и drei.'),
  mc(13, 'EASY', 'Welche Wörter sind richtig geschriebene Zahlen?', op5('fünf', 'sechs', 'siben', 'elf', 'zwöf'), ['a', 'b', 'd'], 'Верно написаны fünf, sechs и elf.'),
  mc(14, 'EASY', 'Welche Zahlen liegen zwischen 6 und 12?', op5('sieben', 'drei', 'neun', 'elf', 'zwei'), ['a', 'c', 'd'], 'Между 6 и 12 — sieben, neun и elf.'),

  // ===== 5 mc HARD =====
  mc(15, 'HARD', 'Welche Wörter enthalten einen Umlaut?', op5('zwölf', 'fünf', 'sieben', 'vier', 'acht'), ['a', 'b'], 'Умляут есть только в zwölf и fünf.'),
  mc(16, 'HARD', 'Welche Zahlen sind gerade (2, 4, 6 ...)?', op6('zwei', 'drei', 'vier', 'fünf', 'sechs', 'sieben'), ['a', 'c', 'e'], 'Чётные числа здесь — zwei, vier и sechs.'),
  mc(17, 'HARD', 'In welchen Zahlwörtern steht der Buchstabe «e»?', op5('eins', 'zehn', 'acht', 'zwei', 'null'), ['a', 'b', 'd'], 'Буква e есть в eins, zehn и zwei; в acht и null её нет.'),
  mc(18, 'HARD', 'Welche Antworten sind korrekt für 11?', op5('elf', 'elv', 'ölf', 'die Elf', 'eilf'), ['a', 'd'], 'Одиннадцать — это elf, а число как существительное — die Elf.'),
  mc(19, 'HARD', 'Welche Zahlen kommen nach 9?', op5('zehn', 'acht', 'elf', 'sieben', 'zwölf'), ['a', 'c', 'e'], 'После 9 идут zehn, elf и zwölf.'),

  // ===== 5 ord EASY =====
  ord(20, 'EASY', 'Соберите по порядку: «Один, два, три, четыре».', items4('eins,', 'zwei,', 'drei,', 'vier'), 'Порядок счёта: eins, zwei, drei, vier.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Это число ноль.».', items5('Das', 'ist', 'die', 'Zahl', 'null'), 'Das ist die Zahl null — называем число: артикль die + само число.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Это семёрка.».', items4('Das', 'ist', 'die', 'Sieben'), 'Das ist die Sieben — называем число как существительное с артиклем die.'),
  ord(23, 'EASY', 'Соберите по порядку: «Четыре, пять, шесть, семь».', items4('vier,', 'fünf,', 'sechs,', 'sieben'), 'Числа по порядку: vier, fünf, sechs, sieben.'),
  ord(24, 'EASY', 'Соберите по порядку: «Девять, десять, одиннадцать, двенадцать».', items4('neun,', 'zehn,', 'elf,', 'zwölf'), 'Числа по порядку: neun, zehn, elf, zwölf.'),

  // ===== 5 ord HARD =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Это число восемь.».', items5('Das', 'ist', 'die', 'Zahl', 'acht'), 'V2-порядок: глагол ist на втором месте.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Я умею считать до двенадцати.».', items5('Ich', 'kann', 'bis', 'zwölf', 'zählen'), 'Рамка: kann на 2-м месте (V2), инфинитив zählen — в конце.'),
  ord(27, 'HARD', 'Соберите немецкий вопрос: «Сколько это книг?».', items5('Wie', 'viele', 'Bücher', 'sind', 'das?'), 'Вопрос Wie viele + мн.ч. Bücher; глагол sind на 2-й позиции.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Ноль и один — маленькие.».', items5('Null', 'und', 'eins', 'sind', 'klein'), 'Подлежащее во мн.ч. → глагол sind; klein как сказуемое без окончания.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Здесь только одна книга.».', items5('Hier', 'ist', 'nur', 'ein', 'Buch'), 'V2: ist на 2-м месте; перед существительным ein (не eins).'),

  // ===== 5 fb EASY =====
  fb(30, 'EASY', 'Das ist die Zahl ___ (0).', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('null', 'eins', 'zwei', 'drei', 'vier'), 'Ноль по-немецки — null.'),
  fb(31, 'EASY', 'Eins, zwei, ___ (3).', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('vier', 'drei', 'fünf', 'sechs', 'acht'), 'После eins, zwei идёт drei.'),
  fb(32, 'EASY', 'Das ist die Zahl ___ (5).', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('zwei', 'vier', 'fünf', 'sechs', 'sieben'), 'Пять по-немецки — fünf.'),
  fb(33, 'EASY', 'Acht, neun, ___ (10).', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('elf', 'sieben', 'zwölf', 'zehn', 'null'), 'После acht, neun идёт zehn.'),
  fb(34, 'EASY', 'Das ist die Zahl ___ (7).', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('drei', 'fünf', 'neun', 'zwei', 'sieben'), 'Семь по-немецки — sieben.'),

  // ===== 5 fb HARD =====
  fb(35, 'HARD', 'Zehn, ___ (11), zwölf.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('acht', 'elf', 'neun', 'sieben', 'sechs'), 'Между zehn и zwölf стоит elf.'),
  fb(36, 'HARD', 'Wie viele? — ___ (12).', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('zehn', 'elf', 'zwölf', 'zwei', 'null'), 'Двенадцать с умляутом — zwölf.'),
  fb(37, 'HARD', 'Das ist ___ (1) Buch.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('eins', 'eine', 'einer', 'ein', 'einen'), 'Перед существительным Buch используется ein.'),
  fb(38, 'HARD', 'Vier, fünf, ___ (6).', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('drei', 'acht', 'zwei', 'sieben', 'sechs'), 'После vier, fünf идёт sechs.'),
  fb(39, 'HARD', 'Ich zähle bis ___ (9).', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('neun', 'zehn', 'elf', 'acht', 'sieben'), 'Девять по-немецки — neun.'),
];
