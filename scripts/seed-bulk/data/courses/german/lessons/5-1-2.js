'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY =====
  sc(0, 'EASY', 'Das Haus ___ 1990 gebaut. (Passiv Präteritum)', op4('wurde', 'wird', 'ist', 'war'), 'a', 'В пассиве прошедшего времени (Präteritum) используется wurde + Partizip II.'),
  sc(1, 'EASY', 'Die Brücke wird nächstes Jahr renoviert ___. (Passiv Futur I)', op4('werden', 'worden', 'wird', 'sein'), 'a', 'В пассиве будущего времени (Futur I) на конце стоит инфинитив werden.'),
  sc(2, 'EASY', 'Amerika wurde 1492 ___ Kolumbus entdeckt.', op4('durch', 'von', 'mit', 'für'), 'b', 'Действующее лицо в пассиве вводится предлогом von + Dativ.'),
  sc(3, 'EASY', 'Das Dach wurde ___ einen Sturm zerstört.', op4('von', 'durch', 'mit', 'bei'), 'b', 'Средство или причину действия вводит предлог durch + Akkusativ.'),
  sc(4, 'EASY', 'Das Haus ist gebaut ___. (Passiv Perfekt)', op4('geworden', 'worden', 'werden', 'gewesen'), 'b', 'В пассиве Perfekt используется особая форма worden без приставки ge-.'),

  // ===== 5 sc HARD =====
  sc(5, 'HARD', 'Die Kirche ___ im 15. Jahrhundert von Mönchen gegründet worden.', op4('ist', 'hat', 'wurde', 'werden'), 'a', 'Пассив Perfekt строится со вспомогательным sein: ist ... worden.'),
  sc(6, 'HARD', 'Das Problem ___ gestern durch ein Update gelöst.', op4('wird', 'wurde', 'ist', 'hat'), 'b', 'Маркер gestern требует прошедшего: пассив Präteritum — wurde + Partizip II.'),
  sc(7, 'HARD', 'Die Stadt wurde durch ein Erdbeben ___.', op4('zerstört', 'zerstörte', 'zerstören', 'gezerstört'), 'a', 'После wurde ставится Partizip II, у zerstören это zerstört без ge-.'),
  sc(8, 'HARD', 'Welcher Satz steht korrekt im Passiv Futur I?', op4('Das Haus wird gebaut werden.', 'Das Haus wird gebaut worden.', 'Das Haus wurde gebaut werden.', 'Das Haus ist gebaut werden.'), 'a', 'Futur I Passiv: wird + Partizip II + werden на конце.'),
  sc(9, 'HARD', 'Die Lampe wurde ___ einen Kurzschluss beschädigt.', op4('von', 'durch', 'mit', 'aus'), 'b', 'Неодушевлённая причина (короткое замыкание) требует durch + Akkusativ (einen).'),

  // ===== 5 mc EASY =====
  mc(10, 'EASY', 'Какие предложения стоят в пассиве Präteritum?', op5('Das Haus wurde gebaut.', 'Die Brücke wurde renoviert.', 'Das Haus wird gebaut.', 'Die Stadt wurde zerstört.', 'Das Haus ist gebaut worden.'), ['a', 'b', 'd'], 'Пассив Präteritum распознаётся по форме wurde + Partizip II.'),
  mc(11, 'EASY', 'Где правильно стоит von перед действующим лицом?', op5('von dem Architekten', 'von Kolumbus', 'von den Mönchen', 'durch dem Architekten', 'von ein Sturm'), ['a', 'b', 'c'], 'von обозначает деятеля и требует Dativ (von dem, von den).'),
  mc(12, 'EASY', 'Где durch вводит средство или причину?', op5('durch einen Sturm', 'durch das Internet', 'durch ein Update', 'durch dem Architekten', 'durch der Sturm'), ['a', 'b', 'c'], 'durch вводит причину/средство и требует Akkusativ (durch einen, durch das).'),
  mc(13, 'EASY', 'Какие предложения о пассиве Perfekt корректны?', op5('Das Haus ist gebaut worden.', 'Die Kirche ist gegründet worden.', 'Das Dach ist zerstört worden.', 'Das Haus ist gebaut geworden.', 'Das Haus hat gebaut worden.'), ['a', 'b', 'c'], 'Пассив Perfekt: sein + Partizip II + worden (не geworden).'),
  mc(14, 'EASY', 'В каких предложениях форма werden использована верно?', op5('Das Haus wurde gebaut.', 'Die Brücke wird renoviert werden.', 'Das Haus ist gebaut worden.', 'Das Haus wird gebaut geworden.', 'Das Haus wurde gebaut werden.'), ['a', 'b', 'c'], 'Präteritum — wurde, Futur — werden, Perfekt — worden; geworden в пассиве не используется.'),

  // ===== 5 mc HARD =====
  mc(15, 'HARD', 'Какие предложения в пассиве полностью корректны?', op6('Das Haus wurde gebaut.', 'Das Haus ist gebaut worden.', 'Das Haus wird gebaut werden.', 'Das Haus ist gebaut geworden.', 'Das Haus hat gebaut worden.', 'Das Haus wurde gebaut werden.'), ['a', 'b', 'c'], 'Корректны Präteritum (wurde), Perfekt (ist ... worden) и Futur I (wird ... werden).'),
  mc(16, 'HARD', 'Где правильно выбран предлог von или durch?', op6('Amerika wurde von Kolumbus entdeckt.', 'Das Dach wurde durch einen Sturm zerstört.', 'Das Problem wurde durch ein Update gelöst.', 'Amerika wurde durch Kolumbus entdeckt.', 'Das Dach wurde von einem Sturm zerstört.', 'Das Problem wurde von einem Update gelöst.'), ['a', 'b', 'c'], 'Деятель идёт с von + Dativ, а средство/причина — с durch + Akkusativ.'),
  mc(17, 'HARD', 'Какие предложения в пассиве Perfekt написаны без ошибки?', op6('Die Stadt ist zerstört worden.', 'Das Auto ist repariert worden.', 'Die Brücke ist gebaut worden.', 'Die Stadt ist zerstört geworden.', 'Das Auto hat repariert worden.', 'Die Brücke wurde gebaut worden.'), ['a', 'b', 'c'], 'Пассив Perfekt всегда: ist/sind + Partizip II + worden.'),
  mc(18, 'HARD', 'Где Partizip II образован верно?', op5('gebaut (bauen)', 'zerstört (zerstören)', 'entdeckt (entdecken)', 'gebaute (bauen)', 'gezerstört (zerstören)'), ['a', 'b', 'c'], 'Глаголы с приставкой be-/zer-/ent- образуют Partizip II без ge-.'),
  mc(19, 'HARD', 'Какие предложения в пассиве Futur I корректны?', op6('Die Brücke wird renoviert werden.', 'Das Haus wird gebaut werden.', 'Die Stadt wird wieder aufgebaut werden.', 'Die Brücke wird renoviert worden.', 'Das Haus wird gebaut geworden.', 'Die Stadt wurde aufgebaut werden.'), ['a', 'b', 'c'], 'Futur I Passiv: wird + Partizip II + werden на конце предложения.'),

  // ===== 5 ord EASY =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Дом был построен в 1990 году.»', items5('Das', 'Haus', 'wurde', '1990', 'gebaut'), 'Пассив Präteritum: wurde + Partizip II в конце.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Америка была открыта Колумбом.»', items5('Amerika', 'wurde', 'von', 'Kolumbus', 'entdeckt'), 'Деятель вводится предлогом von + Dativ.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Берлин был разрушен бомбами.»', items5('Berlin', 'wurde', 'durch', 'Bomben', 'zerstört'), 'Средство/причину вводит предлог durch + Akkusativ (durch Bomben — мн. ч. без артикля).'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Америка была открыта.» (Perfekt)', items4('Amerika', 'ist', 'entdeckt', 'worden'), 'Пассив Perfekt: sein + Partizip II + worden без ge-.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Мост будет отремонтирован.»', items5('Die', 'Brücke', 'wird', 'renoviert', 'werden'), 'Пассив Futur I: wird + Partizip II + werden.'),

  // ===== 5 ord HARD =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Церковь была построена в 1450 году.»', items5('Die', 'Kirche', 'wurde', '1450', 'gebaut'), 'Пассив Präteritum: wurde стоит на втором месте, Partizip II — в конце.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Проблема была быстро решена.»', items5('Das', 'Problem', 'wurde', 'schnell', 'gelöst'), 'Пассив Präteritum: wurde на втором месте, Partizip II gelöst — в конце.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Дом был построен.» (Perfekt)', items5('Das', 'Haus', 'ist', 'gebaut', 'worden'), 'Пассив Perfekt: ist + Partizip II + worden в самом конце.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Город будет отстроен.»', items5('Die', 'Stadt', 'wird', 'aufgebaut', 'werden'), 'Пассив Futur I: wird ... werden охватывают рамку предложения.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Гамбург был разрушен пожаром.»', items5('Hamburg', 'wurde', 'durch', 'Feuer', 'zerstört'), 'Неодушевлённую причину (огонь/пожар) вводит предлог durch (durch Feuer — без артикля идиоматично).'),

  // ===== 5 fb EASY =====
  fb(30, 'EASY', 'Das Haus ___ 1990 gebaut.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('wurde', 'wird', 'ist', 'war', 'worden'), 'Пассив Präteritum использует wurde + Partizip II.'),
  fb(31, 'EASY', 'Amerika wurde 1492 ___ Kolumbus entdeckt.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('durch', 'von', 'mit', 'für', 'bei'), 'Деятеля вводит предлог von + Dativ.'),
  fb(32, 'EASY', 'Das Dach wurde ___ einen Sturm zerstört.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('von', 'mit', 'durch', 'bei', 'aus'), 'Причину/средство вводит durch + Akkusativ.'),
  fb(33, 'EASY', 'Das Haus ist gebaut ___.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('geworden', 'werden', 'wurde', 'worden', 'gewesen'), 'Пассив Perfekt: sein + Partizip II + worden.'),
  fb(34, 'EASY', 'Die Brücke wird renoviert ___.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('worden', 'wurde', 'wird', 'ist', 'werden'), 'Пассив Futur I заканчивается инфинитивом werden.'),

  // ===== 5 fb HARD =====
  fb(35, 'HARD', 'Die Kirche ___ 1450 von Mönchen gegründet worden.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('ist', 'hat', 'wurde', 'werden', 'wird'), 'Пассив Perfekt требует вспомогательного sein: ist ... worden.'),
  fb(36, 'HARD', 'Das Problem wurde ___ ein Update gelöst.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('von', 'durch', 'mit', 'bei', 'aus'), 'Средство решения (обновление) вводится durch + Akkusativ.'),
  fb(37, 'HARD', 'Das Buch wurde ___ einem berühmten Autor geschrieben.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('durch', 'mit', 'von', 'bei', 'aus'), 'Автор — действующее лицо, поэтому von + Dativ.'),
  fb(38, 'HARD', 'Die Stadt wird nächstes Jahr wieder aufgebaut ___.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('worden', 'wurde', 'geworden', 'werden', 'ist'), 'Пассив Futur I: wird ... aufgebaut werden.'),
  fb(39, 'HARD', 'Das alte Schloss ist durch ein Feuer zerstört ___.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('werden', 'wurde', 'geworden', 'wird', 'worden'), 'Пассив Perfekt всегда заканчивается на worden, а не geworden.'),
];
