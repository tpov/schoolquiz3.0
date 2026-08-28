'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Was bedeutet «Was ist das?»', op4('Что это?', 'Кто это?', 'Где это?', 'Сколько это?'), 'a', 'Was ist das? — это вопрос «Что это?».'),
  sc(1, 'EASY', 'Wie sagt man «Это стол» auf Deutsch?', op4('Das ist eine Lampe.', 'Das ist ein Tisch.', 'Das ist ein Buch.', 'Das ist eine Tür.'), 'b', 'Tisch — мужской род (der), поэтому ein Tisch.'),
  sc(2, 'EASY', 'Welches Wort bedeutet «книга»?', op4('der Stuhl', 'die Tür', 'das Buch', 'die Lampe'), 'c', 'Buch — это «книга», средний род (das).'),
  sc(3, 'EASY', 'Welcher unbestimmte Artikel passt: ___ Lampe?', op4('ein', 'eine', 'einen', 'einem'), 'b', 'Lampe — женский род, неопределённый артикль в именительном падеже — eine.'),
  sc(4, 'EASY', 'Wie heißt «окно» auf Deutsch?', op4('das Fenster', 'das Bett', 'der Tisch', 'die Tür'), 'a', 'Fenster — это «окно», средний род (das).'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Was passt: «Ist das ein Stuhl?» — «Ja, das ist ___ Stuhl.»', op4('eine', 'die', 'ein', 'das'), 'c', 'Stuhl — мужской род, поэтому ein Stuhl.'),
  sc(6, 'HARD', 'Welcher Satz ist korrekt?', op4('Das ist eine Buch.', 'Das ein Buch ist.', 'Das Buch ist ein.', 'Das ist ein Buch.'), 'd', 'Buch — средний род (ein), глагол ist стоит на втором месте. «ein» — это артикль перед Buch, а не отдельное слово в конце.'),
  sc(7, 'HARD', 'Welcher Artikel passt: «Das ist ___ Tür.»', op4('ein', 'eine', 'das', 'der'), 'b', 'Tür — женский род, поэтому eine Tür.'),
  sc(8, 'HARD', 'Was antwortet man auf «Was ist das?» (das Bett)?', op4('Das ist eine Bett.', 'Das ist ein Bett.', 'Das ist der Bett.', 'Das ein Bett.'), 'b', 'Bett — средний род, поэтому ein Bett, глагол ist на втором месте.'),
  sc(9, 'HARD', 'Wo steht das Verb «ist» im Satz «Das ist ein Tisch.»?', op4('an erster Stelle', 'an zweiter Stelle', 'am Ende', 'gar nicht'), 'b', 'В простом утверждении глагол ist стоит на втором месте.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Wörter haben den Artikel «das»?', op5('Buch', 'Fenster', 'Tisch', 'Lampe', 'Tür'), ['a', 'b'], 'Среднего рода (das) здесь Buch и Fenster.'),
  mc(11, 'EASY', 'Welche Wörter sind Möbel (мебель)?', op5('Tisch', 'Stuhl', 'Buch', 'Bett', 'Tür'), ['a', 'b', 'd'], 'Мебель — это Tisch, Stuhl и Bett.'),
  mc(12, 'EASY', 'Bei welchen Wörtern passt «eine»?', op5('Lampe', 'Tür', 'Tisch', 'Buch', 'Stuhl'), ['a', 'b'], 'eine ставится перед женским родом: Lampe и Tür.'),
  mc(13, 'EASY', 'Welche Sätze sind korrekt?', op5('Das ist ein Tisch.', 'Das ist eine Lampe.', 'Das ist eine Buch.', 'Das ist ein Tür.', 'Das ist ein Buch.'), ['a', 'b', 'e'], 'Верны: ein Tisch, eine Lampe, ein Buch.'),
  mc(14, 'EASY', 'Welche Wörter bezeichnen einen festen Teil des Zimmers?', op5('die Tür', 'das Fenster', 'das Buch', 'der Tisch', 'die Lampe'), ['a', 'b'], 'Дверь (Tür) и окно (Fenster) — это части комнаты, а Buch, Tisch и Lampe — отдельные предметы.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Bei welchen Wörtern passt der Artikel «ein»?', op6('Tisch', 'Buch', 'Lampe', 'Fenster', 'Tür', 'Stuhl'), ['a', 'b', 'd', 'f'], 'ein ставится перед мужским и средним родом: Tisch, Buch, Fenster, Stuhl.'),
  mc(16, 'HARD', 'Welche Antworten auf «Was ist das?» sind grammatisch korrekt?', op5('Das ist ein Stuhl.', 'Das ist eine Tür.', 'Das ist eine Fenster.', 'Das ein Bett ist.', 'Das ist ein Buch.'), ['a', 'b', 'e'], 'Верны: ein Stuhl, eine Tür, ein Buch.'),
  mc(17, 'HARD', 'Welche Wörter sind feminin (die)?', op6('Tür', 'Lampe', 'Tisch', 'Buch', 'Bett', 'Stuhl'), ['a', 'b'], 'Женского рода (die) здесь Tür и Lampe.'),
  mc(18, 'HARD', 'In welchen Sätzen steht das Verb richtig an zweiter Stelle?', op5('Das ist ein Tisch.', 'Das ein Tisch ist.', 'Das ist eine Lampe.', 'Ist das ein Tisch.', 'Das ist ein Bett.'), ['a', 'c', 'e'], 'В утверждении глагол ist стоит вторым: верны варианты a, c и e.'),
  mc(19, 'HARD', 'Welche Paare «Wort — Artikel» sind richtig?', op6('Stuhl — ein', 'Lampe — eine', 'Buch — eine', 'Tür — eine', 'Fenster — ein', 'Tisch — eine'), ['a', 'b', 'd', 'e'], 'Верно: ein Stuhl, eine Lampe, eine Tür, ein Fenster.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Это стол».', items4('Das', 'ist', 'ein', 'Tisch'), 'Das ist ein Tisch — глагол ist на втором месте.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Это лампа».', items4('Das', 'ist', 'eine', 'Lampe'), 'Lampe — женский род, поэтому eine Lampe.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Это книга».', items4('Das', 'ist', 'ein', 'Buch'), 'Buch — средний род, поэтому ein Buch.'),
  ord(23, 'EASY', 'Соберите немецкий вопрос: «Что это?».', items4('Was', 'ist', 'das', '?'), 'Was ist das? — стандартный вопрос «Что это?».'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Это дверь».', items4('Das', 'ist', 'eine', 'Tür'), 'Tür — женский род, поэтому eine Tür.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкий вопрос: «Это стул?».', items4('Ist', 'das', 'ein', 'Stuhl?'), 'В вопросе глагол ist выходит на первое место: Ist das ein Stuhl?'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Да, это окно».', items5('Ja', ',', 'das', 'ist', 'ein Fenster'), 'Ja, das ist ein Fenster — после Ja идёт обычное утверждение.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Это кровать».', items4('Das', 'ist', 'ein', 'Bett'), 'Bett — средний род, поэтому ein Bett.'),
  ord(28, 'HARD', 'Соберите немецкий вопрос: «Это лампа?».', items4('Ist', 'das', 'eine', 'Lampe?'), 'Ist das eine Lampe? — глагол ist в вопросе стоит первым.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Да, это стол».', items5('Ja', ',', 'das', 'ist', 'ein Tisch'), 'Ja, das ist ein Tisch — после Ja глагол ist на втором месте.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Das ist ___ Tisch.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('ein', 'eine', 'einen', 'die', 'das'), 'Tisch — мужской род, после ist нужен ein Tisch (а не eine/einen/die/das).'),
  fb(31, 'EASY', 'Das ist ___ Lampe.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('ein', 'eine', 'der', 'einer', 'das'), 'Lampe — женский род, после ist нужна eine Lampe (а не ein/der/einer/das).'),
  fb(32, 'EASY', 'Was ___ das?', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('bin', 'ist', 'sind', 'bist', 'seid'), 'В вопросе Was ist das? используется форма ist (3-е лицо).'),
  fb(33, 'EASY', 'Das ist ___ Buch.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('ein', 'eine', 'der', 'die', 'dem'), 'Buch — средний род, после ist нужен ein Buch (а не eine/der/die/dem).'),
  fb(34, 'EASY', 'Das ist ___ Tür.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('ein', 'eine', 'der', 'einer', 'das'), 'Tür — женский род, после ist нужна eine Tür (а не ein/der/einer/das).'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Ist das ein Stuhl? — Ja, das ___ ein Stuhl.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('bin', 'bist', 'sind', 'ist', 'seid'), 'В ответе das ist ein Stuhl нужна форма ist.'),
  fb(36, 'HARD', 'Das ist ___ Fenster.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('eine', 'der', 'ein', 'die', 'dem'), 'Fenster — средний род, поэтому ein Fenster.'),
  fb(37, 'HARD', 'Das ist ___ Stuhl.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('eine', 'die', 'das', 'den', 'ein'), 'Stuhl — мужской род, после ist нужен ein Stuhl (а не eine/die/das/den).'),
  fb(38, 'HARD', '___ ist ein Bett.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Wer', 'Das', 'Wo', 'Wie', 'Wann'), 'Das ist ein Bett — указательное «это» в начале утверждения.'),
  fb(39, 'HARD', 'Das ist ___ Tür.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('ein', 'der', 'das', 'eine', 'dem'), 'Tür — женский род, поэтому eine Tür.'),
];
