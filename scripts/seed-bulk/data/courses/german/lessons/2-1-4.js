'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Ich ___ einen Bruder.', op4('habe', 'hast', 'hat', 'haben'), 'a', 'С «ich» глагол haben имеет форму habe: ich habe.'),
  sc(1, 'EASY', 'Du ___ eine Katze.', op4('habe', 'hat', 'hast', 'haben'), 'c', 'С «du» глагол haben имеет форму hast (без -b-): du hast.'),
  sc(2, 'EASY', 'Er ___ ein Auto.', op4('habe', 'habt', 'haben', 'hat'), 'd', 'С «er» глагол haben имеет форму hat (без -b-): er hat.'),
  sc(3, 'EASY', 'Wie heißt «иметь» auf Deutsch?', op4('sein', 'haben', 'heißen', 'kommen'), 'b', 'Глагол «haben» означает «иметь».'),
  sc(4, 'EASY', 'Wir ___ eine Frage.', op4('haben', 'habe', 'hast', 'hat'), 'a', 'С «wir» глагол haben имеет форму haben: wir haben.'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Ich habe ___ Hund.', op4('ein', 'einen', 'eine', 'kein'), 'b', 'После haben мужской род в Akkusativ: ein → einen Hund.'),
  sc(6, 'HARD', 'Sie hat ___ Auto. (у неё нет машины)', op4('kein', 'keine', 'keinen', 'nicht'), 'a', 'Средний род das Auto отрицается формой kein: kein Auto.'),
  sc(7, 'HARD', 'Ich habe ___ Zeit. (нет времени)', op4('kein', 'keinen', 'keine', 'nicht'), 'c', 'Женский род die Zeit отрицается формой keine: keine Zeit.'),
  sc(8, 'HARD', 'Welcher Satz ist korrekt?', op4('Er hat einen Hund.', 'Er habt einen Hund.', 'Er hat ein Hund.', 'Er haben einen Hund.'), 'a', 'С «er» форма hat, а мужской род в Akkusativ — einen Hund.'),
  sc(9, 'HARD', 'Welcher Satz ist grammatisch richtig?', op4('Ich habe keine Auto.', 'Ich habe einen Auto.', 'Ich habe kein Auto.', 'Ich hat kein Auto.'), 'c', 'Средний род das Auto в отрицании — kein Auto, с «ich» форма habe.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Formen gehören zum Verb «haben»?', op5('habe', 'hast', 'kommt', 'wohne', 'hat'), ['a', 'b', 'e'], 'Формы haben здесь — habe (ich), hast (du) и hat (er/sie/es).'),
  mc(11, 'EASY', 'Welche Wörter sind Substantive (существительные)?', op5('Hund', 'haben', 'Katze', 'hat', 'Auto'), ['a', 'c', 'e'], 'Существительные здесь — der Hund, die Katze и das Auto.'),
  mc(12, 'EASY', 'Welche Verbindungen mit «haben» sind sinnvoll?', op5('Ich habe Zeit.', 'Ich habe Auto Zeit.', 'Du hast Geld.', 'Habe ich du.', 'Er hat einen Bruder.'), ['a', 'c', 'e'], 'Корректны «Ich habe Zeit», «Du hast Geld» и «Er hat einen Bruder».'),
  mc(13, 'EASY', 'Welche Negationsformen mit «kein» passen?', op5('kein Auto', 'keine Zeit', 'kein Zeit', 'keine Auto', 'einen Auto'), ['a', 'b'], 'Правильно: kein Auto (n) и keine Zeit (f).'),
  mc(14, 'EASY', 'Welche Wörter sind Tiere (животные)?', op5('Hund', 'Geld', 'Katze', 'Zeit', 'Frage'), ['a', 'c'], 'Животные здесь — der Hund (собака) и die Katze (кошка).'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Welche Sätze sind grammatisch korrekt?', op5('Ich habe einen Bruder.', 'Ich habe ein Bruder.', 'Du hast keine Zeit.', 'Du hast kein Zeit.', 'Er habt ein Auto.'), ['a', 'c'], 'Мужской род — einen Bruder, женский в отрицании — keine Zeit.'),
  mc(16, 'HARD', 'Welche Verbformen sind richtig konjugiert?', op6('ich habe', 'du hast', 'er hat', 'du habst', 'ich hast', 'er habt'), ['a', 'b', 'c'], 'Правильно: ich habe, du hast, er hat (формы du/er без -b-).'),
  mc(17, 'HARD', 'Welche Negationen sind korrekt?', op6('kein Geld', 'keine Katze', 'keinen Hund', 'keine Geld', 'kein Katze', 'keine Hund'), ['a', 'b', 'c'], 'Правильно: kein Geld (n), keine Katze (f), keinen Hund (m в Akkusativ).'),
  mc(18, 'HARD', 'Welche Sätze drücken «не иметь» korrekt aus?', op5('Sie hat kein Auto.', 'Sie hat keine Zeit.', 'Sie hat einen Auto.', 'Sie haben kein Zeit.', 'Sie hat ein keine Auto.'), ['a', 'b'], 'Корректны «Sie hat kein Auto» (n) и «Sie hat keine Zeit» (f).'),
  mc(19, 'HARD', 'Welche Akkusativ-Verbindungen nach «haben» sind richtig?', op6('einen Hund', 'einen Bruder', 'eine Katze', 'ein Hund', 'einen Katze', 'eine Bruder'), ['a', 'b', 'c'], 'В аккузативе артикль меняется только в мужском роде: *ein* → *einen* (*einen Hund/Bruder*), а женский не меняется (*eine Katze*).'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «У меня есть собака».', items4('Ich', 'habe', 'einen', 'Hund'), 'Мужской род в Akkusativ: Ich habe einen Hund.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «У неё есть кошка».', items4('Sie', 'hat', 'eine', 'Katze'), 'Женский род die Katze в Akkusativ: Sie hat eine Katze.'),
  ord(22, 'EASY', 'Соберите немецкий вопрос: «У тебя есть время?».', items4('Hast', 'du', 'Zeit', '?'), 'В вопросе без вопросительного слова глагол на первом месте: Hast du Zeit?'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «У нас есть вопрос».', items4('Wir', 'haben', 'eine', 'Frage'), 'С wir форма haben, женский род — eine Frage: Wir haben eine Frage.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «У него нет машины».', items4('Er', 'hat', 'kein', 'Auto'), 'Средний род das Auto в отрицании: Er hat kein Auto.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «У меня нет времени».', items4('Ich', 'habe', 'keine', 'Zeit'), 'Женский род die Zeit в отрицании — keine Zeit: Ich habe keine Zeit.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «У нас сейчас нет времени».', items5('Wir', 'haben', 'jetzt', 'keine', 'Zeit'), 'С wir форма haben, женский род die Zeit в отрицании — keine Zeit: Wir haben jetzt keine Zeit.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «У тебя сейчас нет собаки».', items5('Du', 'hast', 'jetzt', 'keinen', 'Hund'), 'С du форма hast, мужской род der Hund в Akkusativ-отрицании — keinen Hund: Du hast jetzt keinen Hund.'),
  ord(28, 'HARD', 'Соберите немецкий вопрос: «Разве у тебя нет денег?».', items5('Hast', 'du', 'kein', 'Geld', '?'), 'Глагол на первом месте, средний род das Geld в отрицании: Hast du kein Geld?'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «У неё сейчас нет машины».', items5('Sie', 'hat', 'jetzt', 'kein', 'Auto'), 'С sie форма hat, jetzt — сейчас, отрицание среднего рода — kein Auto: Sie hat jetzt kein Auto.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Ich ___ einen Hund.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('habe', 'hast', 'hat', 'haben', 'habt'), 'С ich глагол haben имеет форму habe: ich habe.'),
  fb(31, 'EASY', 'Du ___ eine Katze.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('habe', 'hast', 'hat', 'haben', 'habt'), 'С du форма hast (без -b-): du hast.'),
  fb(32, 'EASY', 'Er ___ ein Auto.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('habe', 'hast', 'hat', 'haben', 'habt'), 'С er форма hat (без -b-): er hat.'),
  fb(33, 'EASY', 'Wir ___ eine Frage.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('habe', 'hast', 'hat', 'haben', 'habt'), 'С wir форма haben: wir haben.'),
  fb(34, 'EASY', 'Ihr ___ Zeit.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('habe', 'hast', 'hat', 'haben', 'habt'), 'С ihr форма habt: ihr habt.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Ich habe ___ Hund.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('einen', 'eine', 'ein', 'kein', 'keine'), 'Мужской род der Hund в Akkusativ: einen Hund.'),
  fb(36, 'HARD', 'Sie hat ___ Katze.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('einen', 'eine', 'ein', 'keinen', 'kein'), 'Женский род die Katze в Akkusativ: eine Katze.'),
  fb(37, 'HARD', 'Er hat ___ Auto. (нет машины)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('einen', 'eine', 'kein', 'keine', 'keinen'), 'Средний род das Auto в отрицании: kein Auto.'),
  fb(38, 'HARD', 'Ich habe ___ Zeit. (нет времени)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('kein', 'keinen', 'keine', 'eine', 'einen'), 'Женский род die Zeit в отрицании: keine Zeit.'),
  fb(39, 'HARD', 'Ich habe ___ Bruder.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('eine', 'ein', 'kein', 'keine', 'einen'), 'Мужской род der Bruder в Akkusativ: einen Bruder.'),
];
