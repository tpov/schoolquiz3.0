'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY =====
  sc(0, 'EASY', 'Wie heißt «отец» auf Deutsch?', op4('der Vater', 'die Mutter', 'der Bruder', 'die Tochter'), 'a', 'Слово «отец» по-немецки — der Vater (мужской род).'),
  sc(1, 'EASY', 'Wie heißt «мать» auf Deutsch?', op4('der Sohn', 'die Mutter', 'der Vater', 'das Kind'), 'b', 'Слово «мать» по-немецки — die Mutter (женский род).'),
  sc(2, 'EASY', 'Wie heißt «сестра» auf Deutsch?', op4('der Bruder', 'der Sohn', 'die Schwester', 'die Tochter'), 'c', 'Слово «сестра» по-немецки — die Schwester (женский род).'),
  sc(3, 'EASY', 'Wie heißt «ребёнок» auf Deutsch?', op4('die Eltern', 'der Vater', 'die Mutter', 'das Kind'), 'd', 'Слово «ребёнок» по-немецки — das Kind (средний род).'),
  sc(4, 'EASY', 'Was bedeutet «die Familie»?', op4('семья', 'брат', 'дочь', 'сын'), 'a', 'Слово die Familie означает «семья».'),

  // ===== 5 sc HARD =====
  sc(5, 'HARD', 'Ergänze: «___ Vater heißt Thomas.» (мой отец)', op4('Meine', 'Mein', 'Deine', 'Meinen'), 'b', 'Der Vater мужского рода, поэтому в Nominativ используется mein без окончания.'),
  sc(6, 'HARD', 'Ergänze: «___ Mutter ist nett.» (моя мать)', op4('Mein', 'Meinen', 'Meine', 'Dein'), 'c', 'Die Mutter женского рода, поэтому притяжательное получает окончание -e: meine.'),
  sc(7, 'HARD', 'Ergänze: «Ist das ___ Bruder?» (твой брат)', op4('deine', 'deinen', 'meine', 'dein'), 'd', 'Der Bruder мужского рода, поэтому в Nominativ это dein без окончания.'),
  sc(8, 'HARD', 'Ergänze: «___ Eltern wohnen in Berlin.» (мои родители)', op4('Meine', 'Mein', 'Deinen', 'Meinen'), 'a', 'Die Eltern — множественное число, поэтому притяжательное всегда с -e: meine.'),
  sc(9, 'HARD', 'Welcher Satz ist korrekt?', op4('Mein Mutter ist nett.', 'Meine Vater heißt Thomas.', 'Meine Schwester ist klein.', 'Mein Tochter ist klein.'), 'c', 'Die Schwester женского рода, поэтому верно meine Schwester.'),

  // ===== 5 mc EASY =====
  mc(10, 'EASY', 'Welche Wörter sind weiblich (die)?', op5('die Mutter', 'der Vater', 'die Schwester', 'der Sohn', 'die Tochter'), ['a', 'c', 'e'], 'К женскому роду относятся die Mutter, die Schwester и die Tochter.'),
  mc(11, 'EASY', 'Welche Wörter sind männlich (der)?', op5('der Vater', 'die Mutter', 'der Bruder', 'der Sohn', 'das Kind'), ['a', 'c', 'd'], 'К мужскому роду относятся der Vater, der Bruder и der Sohn.'),
  mc(12, 'EASY', 'Welche Wörter bedeuten «родственники / семья»?', op5('die Familie', 'das Auto', 'die Eltern', 'der Tisch', 'die Geschwister'), ['a', 'c', 'e'], 'К семье относятся die Familie, die Eltern и die Geschwister.'),
  mc(13, 'EASY', 'Welche deutschen Wörter bedeuten «брат» oder «сестра»?', op5('der Bruder', 'die Schwester', 'die Mutter', 'der Vater', 'das Kind'), ['a', 'b'], 'Der Bruder — это «брат», а die Schwester — это «сестра».'),
  mc(14, 'EASY', 'Welche Formen sind richtig im Nominativ?', op5('mein Vater', 'meine Mutter', 'mein Mutter', 'meine Vater', 'mein Kind'), ['a', 'b', 'e'], 'Верны mein Vater, meine Mutter и mein Kind по роду существительного.'),

  // ===== 5 mc HARD =====
  mc(15, 'HARD', 'Welche Sätze sind grammatisch korrekt?', op6('Mein Bruder ist groß.', 'Meine Schwester ist nett.', 'Mein Mutter ist nett.', 'Deine Tochter ist klein.', 'Meine Vater heißt Tom.', 'Dein Bruder heißt Tom.'), ['a', 'b', 'd', 'f'], 'Правильны формы, где притяжательное согласовано с родом существительного.'),
  mc(16, 'HARD', 'Wo gehört «meine» (mit -e)?', op6('___ Mutter', '___ Schwester', '___ Vater', '___ Eltern', '___ Bruder', '___ Sohn'), ['a', 'b', 'd'], 'Окончание -e ставится перед женским родом и множественным числом: meine Mutter, meine Schwester, meine Eltern.'),
  mc(17, 'HARD', 'Welche Wörter sind Plural (Pl.)?', op5('die Eltern', 'der Vater', 'die Geschwister', 'die Mutter', 'das Kind'), ['a', 'c'], 'Множественное число: die Eltern (родители) и die Geschwister (братья и сёстры).'),
  mc(18, 'HARD', 'Bei welchen Wörtern steht «mein» ohne Endung?', op6('Vater', 'Mutter', 'Bruder', 'Schwester', 'Kind', 'Sohn'), ['a', 'c', 'e', 'f'], 'Mein без окончания стоит перед мужским и средним родом: Vater, Bruder, Kind, Sohn.'),
  mc(19, 'HARD', 'Welche Übersetzungen sind korrekt?', op5('der Sohn — сын', 'die Tochter — дочь', 'das Kind — родители', 'die Eltern — родители', 'der Bruder — мать'), ['a', 'b', 'd'], 'Верны der Sohn — сын, die Tochter — дочь и die Eltern — родители.'),

  // ===== 5 ord EASY =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Это моя семья».', items4('Das', 'ist', 'meine', 'Familie'), 'Получается Das ist meine Familie — «Это моя семья».'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Моя мать приятная».', items4('Meine', 'Mutter', 'ist', 'nett'), 'Получается Meine Mutter ist nett — «Моя мать приятная».'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Мой брат большой».', items4('Mein', 'Bruder', 'ist', 'groß'), 'Получается Mein Bruder ist groß — «Мой брат большой».'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Это твой отец».', items4('Das', 'ist', 'dein', 'Vater'), 'Получается Das ist dein Vater — «Это твой отец».'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Моя сестра маленькая».', items4('Meine', 'Schwester', 'ist', 'klein'), 'Получается Meine Schwester ist klein — «Моя сестра маленькая».'),

  // ===== 5 ord HARD =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Моего отца зовут Томас».', items4('Mein', 'Vater', 'heißt', 'Thomas'), 'Получается Mein Vater heißt Thomas — «Моего отца зовут Томас».'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Мои родители живут в Берлине».', items5('Meine', 'Eltern', 'wohnen', 'in', 'Berlin'), 'Получается Meine Eltern wohnen in Berlin — «Мои родители живут в Берлине».'),
  ord(27, 'HARD', 'Соберите немецкий вопрос: «Это твой брат?».', items4('Ist', 'das', 'dein', 'Bruder'), 'Получается Ist das dein Bruder? — в вопросе глагол стоит на первом месте.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Твоя дочь очень милая».', items5('Deine', 'Tochter', 'ist', 'sehr', 'nett'), 'Получается Deine Tochter ist sehr nett — «Твоя дочь очень милая».'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Мой ребёнок ещё маленький».', items5('Mein', 'Kind', 'ist', 'noch', 'klein'), 'Получается Mein Kind ist noch klein — «Мой ребёнок ещё маленький».'),

  // ===== 5 fb EASY =====
  fb(30, 'EASY', '___ Vater heißt Thomas.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('Mein', 'Meine', 'Deine', 'Meinen', 'Deinen'), 'Der Vater мужского рода, поэтому Mein без окончания.'),
  fb(31, 'EASY', '___ Mutter ist nett.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Mein', 'Meine', 'Dein', 'Meinen', 'Deinen'), 'Die Mutter женского рода, поэтому Meine с окончанием -e.'),
  fb(32, 'EASY', 'Ist das ___ Bruder?', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('deine', 'meine', 'dein', 'deinen', 'meinen'), 'Der Bruder мужского рода, поэтому dein без окончания.'),
  fb(33, 'EASY', 'Das ist ___ Familie.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('mein', 'deinen', 'meinen', 'meine', 'dein'), 'Die Familie женского рода, поэтому meine с окончанием -e.'),
  fb(34, 'EASY', '___ Kind ist klein.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Meine', 'Deine', 'Meinen', 'Deinen', 'Mein'), 'Das Kind среднего рода, поэтому Mein без окончания.'),

  // ===== 5 fb HARD =====
  fb(35, 'HARD', '___ Eltern wohnen in Berlin.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('Meine', 'Mein', 'Deinen', 'Meinen', 'Dein'), 'Die Eltern — множественное число, поэтому всегда Meine с -e.'),
  fb(36, 'HARD', '___ Schwester heißt Anna.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Mein', 'Meine', 'Meinen', 'Dein', 'Deinen'), 'Die Schwester женского рода, поэтому Meine с окончанием -e.'),
  fb(37, 'HARD', 'Ist das ___ Sohn?', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('deine', 'meine', 'dein', 'deinen', 'meinen'), 'Der Sohn мужского рода, поэтому dein без окончания.'),
  fb(38, 'HARD', '___ Geschwister sind nett.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Mein', 'Deinen', 'Meinen', 'Meine', 'Dein'), 'Die Geschwister — множественное число, поэтому Meine с окончанием -e.'),
  fb(39, 'HARD', '___ Tochter ist noch klein.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Mein', 'Meinen', 'Dein', 'Deinen', 'Deine'), 'Die Tochter женского рода, поэтому Deine с окончанием -e.'),
];
