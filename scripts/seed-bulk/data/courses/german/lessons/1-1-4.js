'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (0..4) =====
  sc(0, 'EASY', 'Welcher Artikel passt: ___ Mann?', op4('der', 'die', 'das', 'den'), 'a',
    'Mann — мужского рода, поэтому артикль der.'),
  sc(1, 'EASY', 'Welcher Artikel passt: ___ Frau?', op4('das', 'die', 'der', 'dem'), 'b',
    'Frau — женского рода, поэтому артикль die.'),
  sc(2, 'EASY', 'Welcher Artikel passt: ___ Kind?', op4('der', 'die', 'das', 'des'), 'c',
    'Kind — среднего рода, поэтому артикль das.'),
  sc(3, 'EASY', 'Welcher Artikel passt: ___ Tisch?', op4('die', 'der', 'das', 'den'), 'b',
    'Tisch — мужского рода, поэтому артикль der.'),
  sc(4, 'EASY', 'Welcher Artikel passt: ___ Lampe?', op4('der', 'das', 'die', 'dem'), 'c',
    'Lampe — женского рода, поэтому артикль die.'),

  // ===== 5 sc HARD (5..9) =====
  sc(5, 'HARD', 'Welcher Artikel ist richtig: ___ Mädchen?', op4('die', 'der', 'das', 'dem'), 'c',
    'Mädchen — среднего рода (das), хотя по смыслу это девочка.'),
  sc(6, 'HARD', 'Welcher Artikel ist richtig: ___ Sonne?', op4('die', 'der', 'das', 'den'), 'a',
    'Sonne — женского рода, поэтому die Sonne.'),
  sc(7, 'HARD', 'Welcher Artikel ist richtig: ___ Fenster?', op4('der', 'die', 'den', 'das'), 'd',
    'Fenster — среднего рода, поэтому das Fenster.'),
  sc(8, 'HARD', 'Was ist richtig?', op4('die Tisch', 'der Tisch', 'das Tisch', 'den Tisch'), 'b',
    'Tisch мужского рода, поэтому правильно der Tisch.'),
  sc(9, 'HARD', 'Welcher Satz ist korrekt?', op4('Das Frau ist nett.', 'Der Frau ist nett.', 'Die Frau ist nett.', 'Den Frau ist nett.'), 'c',
    'Frau женского рода, поэтому die Frau ist nett.'),

  // ===== 5 mc EASY (10..14) =====
  mc(10, 'EASY', 'Welche Wörter sind maskulin (der)?', op5('der Mann', 'die Frau', 'der Tisch', 'das Kind', 'die Lampe'), ['a', 'c'],
    'Mann и Tisch — мужского рода, оба с артиклем der.'),
  mc(11, 'EASY', 'Welche Wörter sind feminin (die)?', op5('die Frau', 'der Mann', 'die Lampe', 'das Fenster', 'die Sonne'), ['a', 'c', 'e'],
    'Frau, Lampe и Sonne — женского рода, все с артиклем die.'),
  mc(12, 'EASY', 'Welche Wörter sind neutral (das)?', op5('das Kind', 'der Tisch', 'das Fenster', 'die Frau', 'das Mädchen'), ['a', 'c', 'e'],
    'Kind, Fenster и Mädchen — среднего рода, все с артиклем das.'),
  mc(13, 'EASY', 'Welche Verbindungen sind richtig?', op5('der Mann', 'die Tisch', 'das Kind', 'der Lampe', 'die Frau'), ['a', 'c', 'e'],
    'Правильны der Mann, das Kind и die Frau.'),
  mc(14, 'EASY', 'Welche Wörter haben den Artikel die?', op6('die Frau', 'das Kind', 'die Lampe', 'der Tisch', 'die Sonne', 'das Fenster'), ['a', 'c', 'e'],
    'Frau, Lampe и Sonne — женского рода, у всех артикль die.'),

  // ===== 5 mc HARD (15..19) =====
  mc(15, 'HARD', 'Welche Verbindungen sind grammatisch korrekt (Singular)?', op6('das Mädchen', 'der Mädchen', 'die Sonne', 'der Sonne', 'das Fenster', 'der Fenster'), ['a', 'c', 'e'],
    'Верны das Mädchen, die Sonne и das Fenster.'),
  mc(16, 'HARD', 'In welchen Sätzen ist der Artikel richtig?', op5('Der Mann ist hier.', 'Die Mann ist hier.', 'Das Kind spielt.', 'Der Kind spielt.', 'Die Frau ist nett.'), ['a', 'c', 'e'],
    'Верны der Mann, das Kind и die Frau.'),
  mc(17, 'HARD', 'Welche Wörter sind NICHT maskulin?', op5('die Lampe', 'der Mann', 'das Kind', 'der Tisch', 'die Sonne'), ['a', 'c', 'e'],
    'Lampe (die), Kind (das) и Sonne (die) — не мужского рода.'),
  mc(18, 'HARD', 'Welche Artikel-Wort-Paare passen zusammen (Singular)?', op6('der Tisch', 'der Fenster', 'das Fenster', 'das Sonne', 'die Sonne', 'der Mädchen'), ['a', 'c', 'e'],
    'Верны der Tisch, das Fenster и die Sonne.'),
  mc(19, 'HARD', 'Welche Substantive lernt man mit das?', op6('das Kind', 'das Mann', 'das Mädchen', 'das Frau', 'das Fenster', 'das Tisch'), ['a', 'c', 'e'],
    'Kind, Mädchen и Fenster — среднего рода, их учат с артиклем das.'),

  // ===== 5 ord EASY (20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Мужчина здесь».', items4('Der', 'Mann', 'ist', 'hier'), 'der Mann мужского рода; глагол ist стоит на втором месте.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Женщина приятная».', items4('Die', 'Frau', 'ist', 'nett'), 'die Frau женского рода; ist — на втором месте.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Ребёнок играет с удовольствием».', items4('Das', 'Kind', 'spielt', 'gern'), 'das Kind среднего рода; spielt стоит на втором месте, gern (с удовольствием) — в конце.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Лампа новая».', items4('Die', 'Lampe', 'ist', 'neu'), 'die Lampe женского рода; ist — на втором месте.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Окно открыто».', items4('Das', 'Fenster', 'ist', 'offen'), 'das Fenster среднего рода; ist стоит на втором месте.'),

  // ===== 5 ord HARD (25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Девочку зовут Анна».', items4('Das', 'Mädchen', 'heißt', 'Anna'), 'das Mädchen среднего рода; heißt стоит на втором месте.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Солнце сегодня светит тепло».', items5('Die', 'Sonne', 'scheint', 'heute', 'warm'), 'die Sonne женского рода; глагол scheint стоит на втором месте, heute warm — после глагола.'),
  ord(27, 'HARD', 'Соберите немецкую группу подлежащего: «стол и лампа».', items5('Der', 'Tisch', 'und', 'die', 'Lampe'), 'der Tisch мужского рода, die Lampe женского; союз und соединяет два подлежащих.'),
  ord(28, 'HARD', 'Соберите немецкую группу подлежащего: «мужчина и ребёнок».', items5('Der', 'Mann', 'und', 'das', 'Kind'), 'der Mann мужского рода, das Kind среднего; их соединяет союз und.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Женщина сегодня приятная».', items5('Die', 'Frau', 'ist', 'heute', 'nett'), 'die Frau женского рода; ist стоит на втором месте, nett — в конце.'),

  // ===== 5 fb EASY (30..34) =====
  fb(30, 'EASY', '___ Mann ist hier.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('Der', 'Die', 'Das', 'Den', 'Dem'),
    'Mann мужского рода, поэтому Der Mann.'),
  fb(31, 'EASY', '___ Frau ist nett.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Der', 'Die', 'Das', 'Den', 'Dem'),
    'Frau женского рода, поэтому Die Frau.'),
  fb(32, 'EASY', '___ Kind spielt.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Der', 'Die', 'Das', 'Den', 'Dem'),
    'Kind среднего рода, поэтому Das Kind.'),
  fb(33, 'EASY', '___ Tisch ist neu.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('Der', 'Die', 'Das', 'Den', 'Dem'),
    'Tisch мужского рода, поэтому Der Tisch.'),
  fb(34, 'EASY', '___ Lampe ist neu.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Der', 'Die', 'Das', 'Den', 'Dem'),
    'Lampe женского рода, поэтому Die Lampe.'),

  // ===== 5 fb HARD (35..39) =====
  fb(35, 'HARD', '___ Mädchen heißt Anna.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Der', 'Die', 'Das', 'Den', 'Dem'),
    'Mädchen среднего рода, поэтому Das Mädchen, хотя речь о девочке.'),
  fb(36, 'HARD', '___ Sonne ist warm.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Der', 'Die', 'Das', 'Den', 'Dem'),
    'Sonne женского рода, поэтому Die Sonne.'),
  fb(37, 'HARD', '___ Fenster ist offen.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Der', 'Die', 'Das', 'Den', 'Dem'),
    'Fenster среднего рода, поэтому Das Fenster.'),
  fb(38, 'HARD', '___ Frau und ___ Mann sind hier.', [{ id: 'b1', correctCandidateId: 'c2' }, { id: 'b2', correctCandidateId: 'c1' }], cand5('Der', 'Die', 'Das', 'Den', 'Dem'),
    'Frau женского рода (Die), Mann мужского (Der).'),
  fb(39, 'HARD', '___ Kind und ___ Lampe sind neu.', [{ id: 'b1', correctCandidateId: 'c3' }, { id: 'b2', correctCandidateId: 'c2' }], cand5('Der', 'Die', 'Das', 'Den', 'Dem'),
    'Kind среднего рода (Das), Lampe женского (Die).'),
];
