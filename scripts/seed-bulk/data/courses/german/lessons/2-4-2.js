'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Welcher Artikel passt? Ich habe ___ Tisch.', op4('einen', 'eine', 'ein', 'der'), 'a', 'Tisch мужского рода в винительном падеже после haben, поэтому неопределённый артикль einen.'),
  sc(1, 'EASY', 'Welcher Artikel passt? Ich habe ___ Lampe.', op4('einen', 'eine', 'ein', 'den'), 'b', 'Lampe женского рода и в винительном падеже не меняется, поэтому артикль eine.'),
  sc(2, 'EASY', 'Welcher Artikel passt? Ich habe ___ Bett.', op4('einen', 'eine', 'ein', 'einem'), 'c', 'Bett среднего рода и в винительном падеже не меняется, поэтому артикль ein.'),
  sc(3, 'EASY', 'Wie heißt «стул» auf Deutsch?', op4('der Tisch', 'der Stuhl', 'das Sofa', 'der Schrank'), 'b', 'Стул по-немецки — der Stuhl.'),
  sc(4, 'EASY', 'Welcher Artikel passt? Wir haben ___ Sofa.', op4('einen', 'eine', 'ein', 'den'), 'c', 'Sofa среднего рода и в винительном падеже не меняется, поэтому артикль ein.'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Welcher Artikel passt? Ich habe ___ Schrank.', op4('ein', 'eine', 'einen', 'einem'), 'c', 'Schrank мужского рода, и в винительном падеже мужской род меняется на einen.'),
  sc(6, 'HARD', 'Welche Negation passt? Ich habe ___ Stuhl.', op4('keine', 'kein', 'keinen', 'nicht'), 'c', 'Stuhl мужского рода в винительном падеже, поэтому отрицание keinen.'),
  sc(7, 'HARD', 'Welche Negation passt? Ich habe ___ Lampe.', op4('keine', 'kein', 'keinen', 'nicht'), 'a', 'Lampe женского рода в винительном падеже не меняется, поэтому отрицание keine.'),
  sc(8, 'HARD', 'Welche Negation passt? Wir haben ___ Bett.', op4('keinen', 'keine', 'kein', 'nicht'), 'c', 'Bett среднего рода в винительном падеже не меняется, поэтому отрицание kein.'),
  sc(9, 'HARD', 'Wie lautet der Plural? der Stuhl → ___', op4('die Stuhle', 'die Stühle', 'die Stühlen', 'die Stuhlen'), 'b', 'Множественное число от der Stuhl — die Stühle с умлаутом и окончанием -e.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Wörter sind Möbel? (mehrere Antworten)', op5('der Tisch', 'das Sofa', 'das Fenster', 'der Schrank', 'die Tür'), ['a', 'b', 'd'], 'Стол, диван и шкаф — это мебель, а окно и дверь — части комнаты.'),
  mc(11, 'EASY', 'Welche Substantive sind maskulin (der)? (mehrere Antworten)', op5('der Tisch', 'der Stuhl', 'das Bett', 'der Schrank', 'die Lampe'), ['a', 'b', 'd'], 'Tisch, Stuhl и Schrank мужского рода и идут с артиклем der.'),
  mc(12, 'EASY', 'Welche Substantive haben den Artikel das? (mehrere Antworten)', op5('das Bett', 'der Tisch', 'das Sofa', 'die Tür', 'das Regal'), ['a', 'c', 'e'], 'Bett, Sofa и Regal среднего рода и идут с артиклем das.'),
  mc(13, 'EASY', 'In welchen Sätzen ist der Akkusativ-Artikel richtig? (mehrere Antworten)', op5('Ich habe einen Tisch.', 'Ich habe eine Lampe.', 'Ich habe ein Bett.', 'Ich habe ein Tisch.', 'Ich habe einen Lampe.'), ['a', 'b', 'c'], 'После haben верно einen Tisch, eine Lampe и ein Bett.'),
  mc(14, 'EASY', 'Welche Sätze mit haben sind richtig konjugiert? (mehrere Antworten)', op5('Ich habe ein Sofa.', 'Du hast einen Stuhl.', 'Wir haben ein Regal.', 'Er hat einen Tisch.', 'Ich hast ein Bett.'), ['a', 'b', 'c', 'd'], 'Формы habe, hast, haben и hat спряжены верно, ошибка только в ich hast.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Welche Akkusativ-Sätze sind grammatisch korrekt? (mehrere Antworten)', op6('Ich habe einen Schrank.', 'Ich habe eine Tür.', 'Ich habe ein Regal.', 'Ich habe ein Schrank.', 'Ich habe einen Tür.', 'Ich habe eine Regal.'), ['a', 'b', 'c'], 'Только мужской род меняется на einen, поэтому верно einen Schrank, eine Tür и ein Regal.'),
  mc(16, 'HARD', 'In welchen Sätzen ist die Negation kein richtig? (mehrere Antworten)', op6('Ich habe keinen Stuhl.', 'Ich habe keine Lampe.', 'Ich habe kein Bett.', 'Ich habe kein Stuhl.', 'Ich habe keinen Lampe.', 'Ich habe keine Bett.'), ['a', 'b', 'c'], 'Верно keinen Stuhl (мужской), keine Lampe (женский) и kein Bett (средний).'),
  mc(17, 'HARD', 'Welche Pluralformen sind korrekt? (mehrere Antworten)', op6('die Stühle', 'die Tische', 'die Betten', 'die Stuhle', 'die Tischen', 'die Bett'), ['a', 'b', 'c'], 'Корректные формы множественного числа — die Stühle, die Tische и die Betten.'),
  mc(18, 'HARD', 'Welche Genus-Zuordnungen stimmen? (mehrere Antworten)', op6('der Teppich', 'das Fenster', 'die Tür', 'die Teppich', 'der Fenster', 'das Tür'), ['a', 'b', 'c'], 'Правильно der Teppich, das Fenster и die Tür.'),
  mc(19, 'HARD', 'In welchen Sätzen ist haben richtig konjugiert? (mehrere Antworten)', op6('Ich habe einen Tisch.', 'Du hast ein Sofa.', 'Sie hat eine Lampe.', 'Wir hat ein Regal.', 'Ihr habt einen Schrank.', 'Du habe ein Bett.'), ['a', 'b', 'c', 'e'], 'Верны habe, hast, hat, habt; ошибочны wir hat и du habe.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «У меня есть стол».', items4('Ich', 'habe', 'einen', 'Tisch'), 'Правильный порядок слов: Ich habe einen Tisch.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «У меня есть лампа».', items4('Ich', 'habe', 'eine', 'Lampe'), 'Правильный порядок слов: Ich habe eine Lampe.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «У нас есть диван».', items4('Wir', 'haben', 'ein', 'Sofa'), 'Правильный порядок слов: Wir haben ein Sofa.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «У тебя есть стул».', items4('Du', 'hast', 'einen', 'Stuhl'), 'Правильный порядок слов: Du hast einen Stuhl.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «У меня нет шкафа».', items4('Ich', 'habe', 'keinen', 'Schrank'), 'Правильный порядок слов: Ich habe keinen Schrank.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «У меня есть стол и четыре стула».', items5('Ich', 'habe', 'einen Tisch', 'und', 'vier Stühle'), 'Правильный порядок слов: Ich habe einen Tisch und vier Stühle.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «У нас есть диван, но нет шкафа».', items5('Wir', 'haben', 'ein Sofa', 'aber', 'keinen Schrank'), 'Правильный порядок слов: Wir haben ein Sofa, aber keinen Schrank.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «В комнате стоит кровать».', items5('Im', 'Zimmer', 'steht', 'ein', 'Bett'), 'Правильный порядок слов: Im Zimmer steht ein Bett.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «У тебя есть полка в комнате?».', items5('Hast', 'du', 'ein', 'Regal', 'im Zimmer'), 'Правильный порядок слов: Hast du ein Regal im Zimmer?'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «У меня, к сожалению, нет ковра».', items5('Ich', 'habe', 'leider', 'keinen', 'Teppich'), 'Правильный порядок слов: Ich habe leider keinen Teppich.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Ich habe ___ Tisch.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('einen', 'eine', 'ein', 'einem', 'einer'), 'Tisch мужского рода в винительном падеже после haben, поэтому einen.'),
  fb(31, 'EASY', 'Ich habe ___ Lampe.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('einen', 'eine', 'ein', 'einer', 'einem'), 'Lampe женского рода в винительном падеже не меняется, поэтому eine.'),
  fb(32, 'EASY', 'Wir haben ___ Bett.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('einen', 'eine', 'ein', 'einem', 'einer'), 'Bett среднего рода в винительном падеже не меняется, поэтому ein.'),
  fb(33, 'EASY', 'Du ___ einen Stuhl.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('habe', 'haben', 'habt', 'hast', 'hat'), 'С местоимением du глагол haben имеет форму hast.'),
  fb(34, 'EASY', 'Ich habe ___ Sofa.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('einen', 'eine', 'einer', 'einem', 'ein'), 'Sofa среднего рода в винительном падеже не меняется, поэтому ein.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Ich habe ___ Schrank.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('ein', 'eine', 'einen', 'einem', 'der'), 'Schrank мужского рода, и в винительном падеже артикль становится einen.'),
  fb(36, 'HARD', 'Ich habe ___ Stuhl.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('keine', 'keinen', 'kein', 'nicht', 'kein Stuhl'), 'Stuhl мужского рода в винительном падеже, поэтому отрицание keinen.'),
  fb(37, 'HARD', 'Wir haben ___ Lampe.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('keine', 'keinen', 'kein', 'nicht', 'keiner'), 'Lampe женского рода в винительном падеже не меняется, поэтому отрицание keine.'),
  fb(38, 'HARD', 'Das Zimmer hat zwei ___ .', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Fenstre', 'Fenstern', 'Fensters', 'Fenster', 'Fensteren'), 'Множественное число от das Fenster не меняется: zwei Fenster.'),
  fb(39, 'HARD', 'Wir haben drei ___ .', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Stuhl', 'Stuhle', 'Stuhlen', 'Stühlen', 'Stühle'), 'Множественное число от der Stuhl — die Stühle, поэтому drei Stühle.'),
];
