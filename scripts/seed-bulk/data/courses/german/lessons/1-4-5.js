'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Как сказать «Это не стул»?', op4('Das ist kein Stuhl.', 'Das ist eine Stuhl.', 'Das ist nein Stuhl.', 'Das ist ein Stuhl.'), 'a', 'Существительное мужского рода отрицается через kein.'),
  sc(1, 'EASY', 'Какое слово отрицает существительное (а не служит ответом «нет»)?', op4('ja', 'gut', 'kein', 'und'), 'c', 'Kein отрицает существительное, а nein — это просто ответ «нет».'),
  sc(2, 'EASY', 'Что значит «das Zimmer»?', op4('окно', 'комната', 'дверь', 'стол'), 'b', 'Das Zimmer — это комната.'),
  sc(3, 'EASY', 'Как сказать «Это не лампа»?', op4('Das ist kein Lampe.', 'Das ist nein Lampe.', 'Das ist eine Lampe.', 'Das ist keine Lampe.'), 'd', 'Lampe — женского рода, поэтому отрицание keine.'),
  sc(4, 'EASY', 'Что значит «klein»?', op4('большой', 'зелёный', 'маленький', 'красный'), 'c', 'Klein переводится как «маленький».'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Выберите верный вариант: «Это не книги».', op4('Das sind kein Bücher.', 'Das sind keine Bücher.', 'Das ist keine Bücher.', 'Das sind nein Bücher.'), 'b', 'Во множественном числе используется keine, а глагол — sind.'),
  sc(6, 'HARD', 'Какое предложение правильное?', op4('Das ist kein Fenster.', 'Das ist keine Fenster.', 'Das ist nein Fenster.', 'Das sind kein Fenster.'), 'a', 'Fenster среднего рода в единственном числе отрицается через kein.'),
  sc(7, 'HARD', 'Вставьте верно: «___, das ist kein Tisch.» (как ответ «нет»)', op4('Kein', 'Keine', 'Nein', 'Nicht'), 'c', 'Перед запятой нужен ответ «нет» — nein, а не отрицание существительного kein.'),
  sc(8, 'HARD', 'Какой вариант верен: «Это маленький стол» (Akkusativ-free, просто описание)?', op4('Das ist ein klein Tisch.', 'Das ist ein kleiner Tisch.', 'Das ist eine kleine Tisch.', 'Das ist kein kleiner Tisch.'), 'b', 'После ein у мужского рода прилагательное получает окончание -er: ein kleiner Tisch.'),
  sc(9, 'HARD', 'Как правильно отрицать «Это не двери» (мн. ч.)?', op4('Das ist keine Türen.', 'Das sind kein Türen.', 'Das sind keine Türen.', 'Das sind nein Türen.'), 'c', 'Множественное число требует sind и keine.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Какие предложения отрицают существительное правильно?', op5('Das ist kein Stuhl.', 'Das ist nein Stuhl.', 'Das ist keine Lampe.', 'Das ist eine kein Tisch.', 'Das sind keine Bücher.'), ['a', 'c', 'e'], 'Kein/keine ставится вместо ein/eine, во множественном — keine.'),
  mc(11, 'EASY', 'Выберите слова, которые означают цвет.', op5('grün', 'klein', 'rot', 'hier', 'Zimmer'), ['a', 'c'], 'Grün — зелёный, rot — красный; klein и hier цветами не являются.'),
  mc(12, 'EASY', 'Где правильно стоит keine (для женского рода или мн. ч.)?', op5('Das ist keine Lampe.', 'Das ist keine Tisch.', 'Das sind keine Bücher.', 'Das ist keine Stuhl.', 'Das sind keine Türen.'), ['a', 'c', 'e'], 'Keine идёт с женским родом и множественным числом.'),
  mc(13, 'EASY', 'Какие слова означают предметы комнаты?', op6('der Tisch', 'die Lampe', 'rot', 'das Fenster', 'und', 'auch'), ['a', 'b', 'd'], 'Tisch, Lampe и Fenster — это предметы комнаты.'),
  mc(14, 'EASY', 'Выберите верные пары «слово — перевод».', op5('hier — здесь', 'auch — тоже', 'und — но', 'klein — маленький', 'kein — да'), ['a', 'b', 'd'], 'Und значит «и», а kein — «никакой», а не «да».'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Какие предложения грамматически корректны?', op5('Das ist kein Fenster.', 'Das sind keine Lampen.', 'Das ist keine Tisch.', 'Das ist kein Stuhl.', 'Das sind kein Bücher.'), ['a', 'b', 'd'], 'Род и число должны совпадать: kein для m/n ед. ч., keine для f и мн. ч.'),
  mc(16, 'HARD', 'В каких фразах окончание прилагательного после ein верное?', op5('ein kleiner Tisch', 'eine kleine Lampe', 'ein kleine Fenster', 'ein kleines Fenster', 'eine klein Tür'), ['a', 'b', 'd'], 'После *ein* окончание прилагательного зависит от рода: мужской — *-er* (*ein kleiner Tisch*), женский — *-e* (*eine kleine Lampe*), средний — *-es* (*ein kleines Fenster*).'),
  mc(17, 'HARD', 'Где kein/keine употреблён правильно по роду и числу?', op6('kein Stuhl', 'keine Lampe', 'kein Lampe', 'keine Bücher', 'keine Stuhl', 'kein Tür'), ['a', 'b', 'd'], 'kein — для m/n, keine — для f и мн. ч.'),
  mc(18, 'HARD', 'Какие предложения о комнате полностью правильны?', op5('Die Tür ist grün.', 'Das Fenster ist auch grün.', 'Das ist keine Lampe, das ist ein Fenster.', 'Das ist kein Lampe.', 'Das sind keine Bücher, das sind Lampen.'), ['a', 'b', 'c', 'e'], 'Только «kein Lampe» неверно — Lampe требует keine.'),
  mc(19, 'HARD', 'Отметьте корректные отрицания во множественном числе.', op5('Das sind keine Bücher.', 'Das sind keine Türen.', 'Das ist keine Bücher.', 'Das sind kein Lampen.', 'Das sind keine Stühle.'), ['a', 'b', 'e'], 'Множественное число: глагол sind и отрицание keine.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Это не стул».', items4('Das', 'ist', 'kein', 'Stuhl'), 'Порядок: Das ist kein Stuhl.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Это не лампа».', items4('Das', 'ist', 'keine', 'Lampe'), 'Порядок: Das ist keine Lampe.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Это окно».', items4('Das', 'ist', 'ein', 'Fenster'), 'Порядок: Das ist ein Fenster.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Дверь зелёная».', items4('Die', 'Tür', 'ist', 'grün'), 'Порядок: Die Tür ist grün.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Это не книги».', items4('Das', 'sind', 'keine', 'Bücher'), 'Порядок: Das sind keine Bücher.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Это не лампа, это окно».', items5('Das ist keine Lampe,', 'das', 'ist', 'ein', 'Fenster'), 'Порядок: Das ist keine Lampe, das ist ein Fenster.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Окно тоже зелёное».', items4('Das Fenster', 'ist', 'auch', 'grün'), 'Порядок: Das Fenster ist auch grün.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Это маленький стол».', items4('Das', 'ist', 'ein kleiner', 'Tisch'), 'Порядок: Das ist ein kleiner Tisch.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Это не книги, это лампы».', items5('Das sind', 'keine Bücher,', 'das', 'sind', 'Lampen.'), 'Порядок: Das sind keine Bücher, das sind Lampen.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Дверь зелёная, и окно тоже зелёное».', items5('Die Tür ist grün', 'und', 'das Fenster', 'ist auch', 'grün'), 'Порядок: Die Tür ist grün und das Fenster ist auch grün.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Das ist ___ Stuhl. (отрицание, m)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('kein', 'keine', 'nein', 'ein', 'eine'), 'Мужской род отрицается через kein.'),
  fb(31, 'EASY', 'Das ist ___ Lampe. (отрицание, f)', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('kein', 'keine', 'nein', 'ein', 'eine'), 'Женский род отрицается через keine.'),
  fb(32, 'EASY', 'Das ___ keine Bücher. (глагол, мн. ч.)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('ist', 'bin', 'sind', 'hat', 'und'), 'Во множественном числе глагол sein — это sind.'),
  fb(33, 'EASY', 'Die Tür ist ___. (цвет «зелёный»)', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('klein', 'hier', 'auch', 'grün', 'und'), 'Grün значит «зелёный».'),
  fb(34, 'EASY', 'Das ist ein ___. (предмет «окно»)', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Tisch', 'Stuhl', 'Lampe', 'Tür', 'Fenster'), 'Fenster — это окно.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Das sind ___ Bücher. (отрицание, мн. ч.)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('keine', 'kein', 'nein', 'ein', 'eine'), 'Множественное число отрицается через keine.'),
  fb(36, 'HARD', '___, das ist kein Tisch. (ответ «нет»)', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Kein', 'Nein', 'Keine', 'Ein', 'Und'), 'В начале как ответ нужно «нет» — Nein.'),
  fb(37, 'HARD', 'Das ist ein ___ Tisch. (маленький, m)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('klein', 'kleine', 'kleiner', 'kleines', 'kleinen'), 'После ein у мужского рода окончание -er: kleiner.'),
  fb(38, 'HARD', 'Das ist ein ___ Fenster. (маленький, n)', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('kleiner', 'klein', 'kleine', 'kleines', 'kleinen'), 'После ein у среднего рода окончание -es: kleines.'),
  fb(39, 'HARD', 'Das Fenster ist ___ grün. (слово «тоже»)', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('und', 'hier', 'kein', 'klein', 'auch'), 'Auch значит «тоже».'),
];
