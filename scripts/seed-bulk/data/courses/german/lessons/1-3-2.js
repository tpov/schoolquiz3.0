'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ---------- 5 sc EASY ----------
  sc(0, 'EASY', 'Wie heißt die Zahl 13?', op4('dreizehn', 'dreizig', 'zehndrei', 'dreiundzehn'), 'a', 'Числа 13–19 строятся как единица + zehn, поэтому 13 — это dreizehn.'),
  sc(1, 'EASY', 'Wie heißt die Zahl 20?', op4('zweizig', 'zwanzig', 'zweizehn', 'zwozig'), 'b', 'Двадцать по-немецки — zwanzig.'),
  sc(2, 'EASY', 'Welches Wort bedeutet «сто»?', op4('zwanzig', 'fünfzig', 'hundert', 'dreißig'), 'c', 'Сто по-немецки — hundert.'),
  sc(3, 'EASY', 'Wie heißt die Zahl 16?', op4('sechszehn', 'sechsten', 'sechzig', 'sechzehn'), 'd', 'Шестнадцать пишется sechzehn (без -s- перед zehn).'),
  sc(4, 'EASY', 'Wie sagt man «Мне 13 лет»?', op4('Ich bin dreizehn Jahre alt.', 'Ich habe dreizehn Jahre.', 'Ich bin dreizehn alt Jahre.', 'Ich bin Jahre dreizehn alt.'), 'a', 'Возраст выражается через sein: Ich bin … Jahre alt.'),

  // ---------- 5 sc HARD ----------
  sc(5, 'HARD', 'Wie schreibt man die Zahl 21?', op4('zwanzigundein', 'einundzwanzig', 'zwanzigeins', 'einzwanzig'), 'b', 'В числах 21–99 сначала идёт единица, потом und и десяток: einundzwanzig.'),
  sc(6, 'HARD', 'Welche Schreibweise für 30 ist richtig?', op4('dreizig', 'dreiszig', 'dreißig', 'dreizehnzig'), 'c', 'Тридцать — исключение и пишется через ß: dreißig.'),
  sc(7, 'HARD', 'Wie heißt die Zahl 47?', op4('vierzigsieben', 'siebzigvier', 'viersiebzig', 'siebenundvierzig'), 'd', 'Сорок семь — это семь и сорок: siebenundvierzig.'),
  sc(8, 'HARD', 'Welche Zahl ist «einunddreißig»?', op4('31', '13', '41', '30'), 'a', 'einunddreißig — это один и тридцать, то есть 31.'),
  sc(9, 'HARD', 'Wie sagt man «Это стоит 50 евро»?', op4('Das kostet fünfundzig Euro.', 'Das kostet fünfzig Euro.', 'Das kostet fünfzehn Euro.', 'Das kostet fünfzigste Euro.'), 'b', 'Пятьдесят — fünfzig, поэтому правильно: Das kostet fünfzig Euro.'),

  // ---------- 5 mc EASY ----------
  mc(10, 'EASY', 'Welche Wörter sind Zehnerzahlen (десятки)?', op5('zwanzig', 'dreizehn', 'vierzig', 'siebzehn', 'fünfzig'), ['a', 'c', 'e'], 'Десятки оканчиваются на -zig: zwanzig, vierzig, fünfzig.'),
  mc(11, 'EASY', 'Welche Zahlen liegen zwischen 13 und 19?', op5('dreizehn', 'zwanzig', 'sechzehn', 'siebzehn', 'dreißig'), ['a', 'c', 'd'], 'Между 13 и 19 находятся dreizehn, sechzehn и siebzehn.'),
  mc(12, 'EASY', 'Welche Wörter gehören zum Thema Alter und Zahlen?', op6('das Jahr', 'der Tisch', 'alt', 'die Nummer', 'das Fenster', 'hundert'), ['a', 'c', 'd', 'f'], 'К теме возраста и чисел относятся das Jahr, alt, die Nummer и hundert.'),
  mc(13, 'EASY', 'Welche Schreibweisen sind korrekt?', op5('dreizehn', 'zwanzig', 'fünfzig', 'dreizig', 'hundert'), ['a', 'b', 'c', 'e'], 'Верны dreizehn, zwanzig, fünfzig и hundert, а dreizig написано с ошибкой.'),
  mc(14, 'EASY', 'Welche Sätze nennen ein Alter richtig?', op5('Ich bin zwanzig Jahre alt.', 'Ich bin Jahre zwanzig.', 'Sie ist dreizehn Jahre alt.', 'Er habe vierzig Jahre.', 'Wir sind fünfzig Jahre alt.'), ['a', 'c', 'e'], 'Возраст называют через sein + Jahre alt, поэтому верны первый, третий и пятый варианты.'),

  // ---------- 5 mc HARD ----------
  mc(15, 'HARD', 'Welche Zahlwörter sind korrekt geschrieben?', op6('einundzwanzig', 'dreißig', 'fünfunsechzig', 'siebzig', 'neunzig', 'vierzigvier'), ['a', 'b', 'd', 'e'], 'Корректны einundzwanzig, dreißig, siebzig и neunzig.'),
  mc(16, 'HARD', 'Welche Zahlen sind größer als 40?', op5('fünfzig', 'einundzwanzig', 'siebzig', 'dreizehn', 'neunundneunzig'), ['a', 'c', 'e'], 'Больше сорока — fünfzig (50), siebzig (70) и neunundneunzig (99).'),
  mc(17, 'HARD', 'In welchen Zahlen kommt die Einer-Ziffer ZUERST vor (z. B. ein-und-…)?', op5('vierundzwanzig', 'zwanzig', 'einunddreißig', 'hundert', 'sechsundsechzig'), ['a', 'c', 'e'], 'В составных числах 21–99 единица стоит первой: vierundzwanzig, einunddreißig, sechsundsechzig.'),
  mc(18, 'HARD', 'Welche Sätze sind grammatisch korrekt?', op5('Sie ist einunddreißig.', 'Das kostet fünfzig Euro.', 'Ich bin Jahre dreizehn.', 'Meine Nummer ist sieben-acht-neun.', 'Er ist alt zwanzig Jahre.'), ['a', 'b', 'd'], 'Верны первый, второй и четвёртый варианты — у остальных нарушен порядок слов.'),
  mc(19, 'HARD', 'Welche Wörter bedeuten eine Zahl zwischen 60 und 90?', op6('sechzig', 'fünfzig', 'siebzig', 'achtzig', 'vierzig', 'hundert'), ['a', 'c', 'd'], 'Между 60 и 90 находятся sechzig (60), siebzig (70) и achtzig (80).'),

  // ---------- 5 ord EASY ----------
  ord(20, 'EASY', 'Соберите немецкое предложение: «Мне тринадцать лет».', items5('Ich', 'bin', 'dreizehn', 'Jahre', 'alt'), 'Возраст строится как Ich bin … Jahre alt с глаголом sein на втором месте.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Ей двадцать один год».', items5('Sie', 'ist', 'einundzwanzig', 'Jahre', 'alt'), 'В числе einundzwanzig единица стоит перед десятком, а возраст замыкает alt.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Это стоит пятьдесят евро».', items4('Das', 'kostet', 'fünfzig', 'Euro'), 'Глагол kostet занимает второе место в немецком предложении.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Мой номер семь».', items4('Meine', 'Nummer', 'ist', 'sieben'), 'die Nummer женского рода, поэтому притяжательное слово — meine.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Он очень старый».', items4('Er', 'ist', 'sehr', 'alt'), 'В простом предложении глагол sein стоит на втором месте.'),

  // ---------- 5 ord HARD ----------
  ord(25, 'HARD', 'Соберите немецкое предложение: «Мне двадцать четыре года».', items5('Ich', 'bin', 'vierundzwanzig', 'Jahre', 'alt'), 'В vierundzwanzig сначала единица vier, потом und и десяток zwanzig.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Ей тридцать один год».', items5('Sie', 'ist', 'einunddreißig', 'Jahre', 'alt'), 'einunddreißig пишется слитно: ein-und-dreißig.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Книга стоит шестьдесят евро».', items5('Das', 'Buch', 'kostet', 'sechzig', 'Euro'), 'das Buch среднего рода, а sechzig — это 60.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Мой возраст сорок лет».', items5('Mein', 'Alter', 'ist', 'vierzig', 'Jahre'), 'das Alter среднего рода, поэтому притяжательное слово — mein.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Нам девяносто девять лет».', items5('Wir', 'sind', 'neunundneunzig', 'Jahre', 'alt'), 'neunundneunzig — это девять и девяносто, то есть 99.'),

  // ---------- 5 fb EASY ----------
  fb(30, 'EASY', 'Ich bin dreizehn ___ alt.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('Jahre', 'Nummer', 'Euro', 'Alter', 'hundert'), 'После числа в выражении возраста стоит Jahre.'),
  fb(31, 'EASY', 'Die Zahl 20 heißt ___.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('dreizehn', 'zwanzig', 'dreißig', 'vierzig', 'hundert'), 'Двадцать по-немецки — zwanzig.'),
  fb(32, 'EASY', 'Die Zahl 100 heißt ___.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('zwanzig', 'fünfzig', 'hundert', 'dreißig', 'siebzehn'), 'Сто по-немецки — hundert.'),
  fb(33, 'EASY', 'Das kostet fünfzig ___.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Jahre', 'Nummer', 'alt', 'Euro', 'Alter'), 'Цену называют с валютой Euro.'),
  fb(34, 'EASY', 'Meine ___ ist sieben-acht-neun.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Jahre', 'Euro', 'alt', 'hundert', 'Nummer'), 'Номер телефона по-немецки — die Nummer.'),

  // ---------- 5 fb HARD ----------
  fb(35, 'HARD', 'Die Zahl 21 schreibt man ___.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('einundzwanzig', 'zwanzigundein', 'zwanzigeins', 'einzwanzig', 'zwanzigein'), 'В 21 единица стоит перед десятком: einundzwanzig.'),
  fb(36, 'HARD', 'Die Zahl 30 schreibt man ___.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('dreizig', 'dreißig', 'dreiszig', 'dreizehnzig', 'dreisig'), 'Тридцать — исключение и пишется через ß: dreißig.'),
  fb(37, 'HARD', 'Die Zahl 47 schreibt man ___.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('vierzigsieben', 'siebzigvier', 'siebenundvierzig', 'viersiebzig', 'siebenvierzig'), 'Сорок семь — это семь и сорок: siebenundvierzig.'),
  fb(38, 'HARD', 'Sie ist ___ Jahre alt. (Ей 31)', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('dreizehn', 'dreißig', 'dreiunddreißig', 'einunddreißig', 'dreizig'), '31 — это один и тридцать: einunddreißig.'),
  fb(39, 'HARD', 'Die Zahl 99 schreibt man ___.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('neunzig', 'neunzehn', 'neunundneun', 'neunzigneun', 'neunundneunzig'), '99 — это девять и девяносто: neunundneunzig.'),
];
