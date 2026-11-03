'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Wie heißt «сыр» auf Deutsch?', op4('der Käse', 'der Fisch', 'das Ei', 'der Tee'), 'a', 'Сыр по-немецки — der Käse.'),
  sc(1, 'EASY', 'Wie heißt «яйцо» auf Deutsch?', op4('der Saft', 'das Gemüse', 'das Ei', 'die Butter'), 'c', 'Яйцо по-немецки — das Ei.'),
  sc(2, 'EASY', 'Wie heißt «чай» auf Deutsch?', op4('der Fisch', 'der Tee', 'das Obst', 'der Käse'), 'b', 'Чай по-немецки — der Tee.'),
  sc(3, 'EASY', 'Wie heißt «рыба» auf Deutsch?', op4('das Fleisch', 'das Ei', 'der Saft', 'der Fisch'), 'd', 'Рыба по-немецки — der Fisch.'),
  sc(4, 'EASY', 'Wie heißt «сок» auf Deutsch?', op4('der Saft', 'der Käse', 'das Gemüse', 'der Tee'), 'a', 'Сок по-немецки — der Saft.'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Ich esse ___ Fleisch.', op4('keine', 'kein', 'nicht', 'nein'), 'b', 'Das Fleisch среднего рода, поэтому отрицание — kein.'),
  sc(6, 'HARD', 'Welcher Artikel ist richtig: ___ Butter?', op4('der', 'das', 'die', 'den'), 'c', 'Die Butter женского рода, поэтому артикль die.'),
  sc(7, 'HARD', '___ du gern Tee?', op4('Magst', 'Mag', 'Mögt', 'Mögen'), 'a', 'Со словом du глагол mögen имеет форму magst.'),
  sc(8, 'HARD', '___ esse ich ein Ei. (по утрам)', op4('Abends', 'Mittags', 'Morgens', 'Gern'), 'c', 'По утрам — это наречие morgens.'),
  sc(9, 'HARD', 'Er ___ viel Obst und Gemüse.', op4('isst', 'esse', 'esst', 'essen'), 'a', 'С местоимением er (он) глагол essen имеет форму isst.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Wörter bedeuten «напиток»?', op5('der Tee', 'der Saft', 'der Käse', 'das Ei', 'der Fisch'), ['a', 'b'], 'Der Tee (чай) и der Saft (сок) — это напитки.'),
  mc(11, 'EASY', 'Welche Wörter haben den Artikel «der»?', op5('der Käse', 'das Ei', 'der Fisch', 'die Butter', 'der Saft'), ['a', 'c', 'e'], 'Der Käse, der Fisch и der Saft — мужского рода с артиклем der.'),
  mc(12, 'EASY', 'Welche Wörter bedeuten «еда» (нечто съедобное)?', op5('das Ei', 'der Saft', 'der Käse', 'der Tee', 'das Fleisch'), ['a', 'c', 'e'], 'Das Ei, der Käse и das Fleisch — это еда, а Saft и Tee — напитки.'),
  mc(13, 'EASY', 'Welche Wörter haben den Artikel «das»?', op6('das Ei', 'der Tee', 'das Gemüse', 'das Obst', 'die Butter', 'der Saft'), ['a', 'c', 'd'], 'Das Ei, das Gemüse и das Obst — среднего рода с артиклем das.'),
  mc(14, 'EASY', 'Welche Sätze sind über Frühstück (завтрак)?', op5('Morgens esse ich ein Ei.', 'Abends esse ich Fisch.', 'Morgens trinke ich Tee.', 'Mittags esse ich Reis.', 'Abends trinke ich Saft.'), ['a', 'c'], 'Morgens означает «по утрам», то есть про завтрак.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Welche Formen von «mögen» sind richtig?', op5('ich mag', 'du magst', 'er mögt', 'wir mögen', 'sie magen'), ['a', 'b', 'd'], 'Правильны формы ich mag, du magst и wir mögen.'),
  mc(16, 'HARD', 'In welchen Sätzen ist die Verneinung richtig?', op5('Ich esse kein Fleisch.', 'Ich esse keine Butter.', 'Ich esse keine Käse.', 'Ich esse keine Fisch.', 'Ich esse kein Obst.'), ['a', 'b', 'e'], 'Kein стоит перед мужским/средним родом, keine — перед женским, поэтому верны kein Fleisch (ср.р.), keine Butter (ж.р.), kein Obst (ср.р.).'),
  mc(17, 'HARD', 'Welche Adverbien beschreiben die Zeit (время дня)?', op5('morgens', 'mittags', 'gern', 'abends', 'kein'), ['a', 'b', 'd'], 'Morgens, mittags и abends обозначают время суток, а gern — «охотно».'),
  mc(18, 'HARD', 'Welche Sätze sind grammatisch korrekt?', op5('Wir mögen Fisch.', 'Sie isst Obst.', 'Du magst Käse.', 'Ich mögt Tee.', 'Er essen Fleisch.'), ['a', 'b', 'c'], 'Верны Wir mögen Fisch, Sie isst Obst и Du magst Käse.'),
  mc(19, 'HARD', 'Welche Wörter sind KEINE Lebensmittel (не еда и не напиток)?', op5('morgens', 'der Käse', 'gern', 'abends', 'das Ei'), ['a', 'c', 'd'], 'Morgens, gern и abends — это наречия, а не продукты.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Я люблю сыр».', items4('Ich', 'mag', 'Käse', '.'), 'В простом предложении глагол mag стоит на втором месте.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Я пью чай».', items4('Ich', 'trinke', 'Tee', '.'), 'Глагол trinke стоит на втором месте после подлежащего ich.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Мы любим рыбу».', items4('Wir', 'mögen', 'Fisch', '.'), 'С wir глагол mögen имеет форму mögen.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Я ем яйцо».', items4('Ich', 'esse', 'ein Ei', '.'), 'Яйцо исчисляемо, поэтому используется артикль ein.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Она ест овощи».', items4('Sie', 'isst', 'Gemüse', '.'), 'С sie (она) глагол essen имеет форму isst.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «По утрам я ем хлеб с маслом».', items5('Morgens', 'esse', 'ich', 'Brot', 'mit Butter'), 'После обстоятельства morgens глагол стоит на втором месте, а подлежащее ich — после него.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Я не ем мяса».', items4('Ich', 'esse', 'kein', 'Fleisch'), 'Отрицание kein ставится перед существительным среднего рода Fleisch.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Ты охотно пьёшь чай?».', items5('Trinkst', 'du', 'gern', 'Tee', '?'), 'В вопросе глагол trinkst выходит на первое место.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Она ест много фруктов».', items4('Sie', 'isst', 'viel', 'Obst'), 'Viel (много) стоит перед неисчисляемым словом Obst без артикля.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Я люблю сыр, но я не ем мясо».', items5('Ich mag Käse,', 'aber', 'ich', 'esse', 'kein Fleisch'), 'Союз aber соединяет два предложения, и порядок слов в каждом остаётся прямым.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Ich trinke ___ . (чай)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('Tee', 'Käse', 'Ei', 'Fisch', 'Butter'), 'Чай по-немецки — Tee.'),
  fb(31, 'EASY', 'Ich esse ___ . (рыбу)', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Tee', 'Saft', 'Butter', 'Fisch', 'Obst'), 'Рыба по-немецки — Fisch.'),
  fb(32, 'EASY', '___ Käse ist gut. (артикль)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Die', 'Das', 'Der', 'Den', 'Dem'), 'Der Käse мужского рода, поэтому артикль der.'),
  fb(33, 'EASY', '___ Ei ist frisch. (артикль)', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Der', 'Das', 'Die', 'Den', 'Dem'), 'Das Ei среднего рода, поэтому артикль das.'),
  fb(34, 'EASY', 'Ich ___ Obst gern. (любить)', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('magst', 'mögt', 'mögen', 'isst', 'mag'), 'С ich глагол mögen имеет форму mag.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Ich esse ___ Fleisch. (отрицание)', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('keine', 'kein', 'nicht', 'nein', 'keinen'), 'Das Fleisch среднего рода, поэтому отрицание kein.'),
  fb(36, 'HARD', 'Ich esse ___ Butter. (отрицание)', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('kein', 'nicht', 'nein', 'keine', 'keinen'), 'Die Butter женского рода, поэтому отрицание keine.'),
  fb(37, 'HARD', '___ du gern Saft? (любить)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Mag', 'Mögt', 'Magst', 'Mögen', 'Isst'), 'С du глагол mögen имеет форму magst.'),
  fb(38, 'HARD', '___ esse ich ein Ei. (по утрам)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('Morgens', 'Abends', 'Mittags', 'Gern', 'Viel'), 'По утрам — это наречие morgens.'),
  fb(39, 'HARD', '___ Butter ist auf dem Tisch. (артикль)', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Der', 'Das', 'Den', 'Die', 'Dem'), 'Die Butter женского рода, поэтому артикль die.'),
];
