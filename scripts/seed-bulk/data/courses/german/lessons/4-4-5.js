'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ---- 5 sc EASY ----
  sc(0, 'EASY', 'Wenn ich Zeit ___, würde ich dir helfen.', op4('hätte', 'habe', 'hatte', 'haben'), 'a',
    'Konjunktiv II от haben — hätte, нужная форма в нереальном условии.'),
  sc(1, 'EASY', 'Wenn das Wetter besser ___, könnten wir spazieren gehen.', op4('ist', 'wäre', 'war', 'sein'), 'b',
    'Konjunktiv II от sein для 3-го лица единственного числа — wäre.'),
  sc(2, 'EASY', 'Wenn ich reich wäre, ___ ich eine Weltreise machen.', op4('werde', 'wird', 'würde', 'wurde'), 'c',
    'В главной части используется würde + Infinitiv для гипотезы.'),
  sc(3, 'EASY', 'Wie sagt man «если бы у меня было время» auf Deutsch?', op4('Wenn ich Zeit habe', 'Als ich Zeit hatte', 'Wenn ich Zeit hätte', 'Weil ich Zeit habe'), 'c',
    'Нереальное условие требует wenn и Konjunktiv II — hätte.'),
  sc(4, 'EASY', 'Wenn du mich ___ würdest, würde ich Ja sagen.', op4('fragen', 'fragst', 'gefragt', 'fragte'), 'a',
    'Конструкция würde + Infinitiv требует инфинитив fragen.'),

  // ---- 5 sc HARD ----
  sc(5, 'HARD', 'Welcher Satz drückt eine irreale Bedingung in der Gegenwart korrekt aus?', op4('Wenn ich Geld habe, kaufe ich ein Auto.', 'Wenn ich Geld hätte, würde ich ein Auto kaufen.', 'Wenn ich Geld hatte, kaufte ich ein Auto.', 'Wenn ich Geld haben würde, kaufte ich Auto.'), 'b',
    'Нереальное условие: в обеих частях Konjunktiv II (hätte … würde kaufen).'),
  sc(6, 'HARD', 'Welche Inversion ersetzt «Wenn ich Zeit hätte, würde ich kommen» ohne wenn korrekt?', op4('Habe ich Zeit, würde ich kommen.', 'Hätte ich Zeit, würde ich kommen.', 'Ich hätte Zeit, würde ich kommen.', 'Wäre ich Zeit, würde ich kommen.'), 'b',
    'Без wenn спрягаемый глагол выходит вперёд: Hätte ich Zeit, …'),
  sc(7, 'HARD', 'Wenn ich du ___, würde ich diese Chance nutzen.', op4('wäre', 'bin', 'wären', 'sei'), 'a',
    'На месте «я» (ich) ставится Konjunktiv II wäre — единственное число.'),
  sc(8, 'HARD', 'Welcher Satz benutzt statt «würde» die korrekte eigene Form des Modalverbs?', op4('Wenn er wollte, würde er könnte kommen.', 'Wenn er Zeit hätte, könnte er kommen.', 'Wenn er Zeit hätte, würde er kann kommen.', 'Wenn er Zeit hätte, würde er kommen können.'), 'b',
    'У модальных и haben/sein своя форма Konjunktiv II — könnte, без würde.'),
  sc(9, 'HARD', 'Warum steht im Satz «Wenn ich reich wäre …» Konjunktiv II und nicht Indikativ?', op4('Weil das Ereignis sicher passiert.', 'Weil es eine Frage ist.', 'Weil die Bedingung irreal oder unwahrscheinlich ist.', 'Weil es Vergangenheit ist.'), 'c',
    'Konjunktiv II здесь показывает нереальность или маловероятность условия.'),

  // ---- 5 mc EASY ----
  mc(10, 'EASY', 'Welche Formen sind Konjunktiv II?', op5('hätte', 'wäre', 'habe', 'würde', 'bin'), ['a', 'b', 'd'],
    'hätte, wäre и würde — формы Konjunktiv II; habe и bin это Indikativ.'),
  mc(11, 'EASY', 'Welche Wörter passen zum Thema «Bedingung»?', op5('wenn', 'dann', 'weil', 'die Bedingung', 'das Buch'), ['a', 'b', 'd'],
    'wenn, dann и die Bedingung относятся к теме условия.'),
  mc(12, 'EASY', 'In welchen Sätzen steht das Verb im wenn-Teil korrekt am Ende?', op5('Wenn ich reich wäre, …', 'Wenn ich hätte Zeit, …', 'Wenn das Wetter besser wäre, …', 'Wenn wäre ich da, …', 'Wenn du kommen würdest, …'), ['a', 'c', 'e'],
    'В придаточном с wenn спрягаемый глагол стоит в конце.'),
  mc(13, 'EASY', 'Welche Sätze sind grammatisch korrekte irreale Bedingungen?', op5('Wenn ich Zeit hätte, würde ich kommen.', 'Wenn ich Zeit hätte, ich komme.', 'Wenn das Wetter besser wäre, könnten wir gehen.', 'Wenn ich reich wäre, würde ich helfen.', 'Wenn ich reich war, helfe ich.'), ['a', 'c', 'd'],
    'Корректны те, где в обеих частях стоит Konjunktiv II.'),
  mc(14, 'EASY', 'Welche Übersetzungen von «если» / «тогда» sind richtig?', op5('wenn — если', 'dann — тогда', 'wenn — потому что', 'dann — но', 'reich — богатый'), ['a', 'b', 'e'],
    'wenn — «если», dann — «тогда», reich — «богатый».'),

  // ---- 5 mc HARD ----
  mc(15, 'HARD', 'Welche Sätze drücken eine irreale Bedingung der Gegenwart korrekt aus?', op6('Wenn ich Zeit hätte, würde ich dir helfen.', 'Wenn du mich fragen würdest, würde ich Ja sagen.', 'Wenn ich Zeit habe, helfe ich dir.', 'Hätte ich deine Nummer, würde ich dich anrufen.', 'Wenn ich Zeit hätte, helfe ich dir.', 'Wenn ich Zeit hatte, half ich dir.'), ['a', 'b', 'd'],
    'Нереальное условие требует Konjunktiv II в обеих частях.'),
  mc(16, 'HARD', 'In welchen Sätzen wird die wenn-Inversion (ohne wenn) korrekt verwendet?', op5('Hätte ich Zeit, würde ich kommen.', 'Wäre das Wetter besser, könnten wir gehen.', 'Habe ich Zeit, komme ich.', 'Hätte ich deine Nummer, würde ich anrufen.', 'Ich hätte Zeit, würde ich kommen.'), ['a', 'b', 'd'],
    'При инверсии спрягаемый глагол в Konjunktiv II стоит первым.'),
  mc(17, 'HARD', 'Welche Verben haben eine eigene Konjunktiv-II-Form (statt würde + Infinitiv)?', op6('haben (hätte)', 'sein (wäre)', 'können (könnte)', 'machen', 'lernen', 'kaufen'), ['a', 'b', 'c'],
    'haben, sein и модальные глаголы имеют собственные формы Konjunktiv II.'),
  mc(18, 'HARD', 'Welche Sätze sind grammatisch FEHLERFREI? (wähle die korrekten)', op5('Wenn ich du wäre, würde ich diese Chance nutzen.', 'Wenn das Wetter besser wäre, könnten wir spazieren gehen.', 'Wenn ich reich wäre, würde ich reich.', 'Wenn ich mehr Geld hätte, würde ich eine Weltreise machen.', 'Wenn ich hätte Zeit, würde ich kommen.'), ['a', 'b', 'd'],
    'Корректны предложения с правильным Konjunktiv II и порядком слов.'),
  mc(19, 'HARD', 'Welche Aussagen über irreale wenn-Sätze sind richtig?', op5('Im wenn-Teil steht das Verb am Ende.', 'Im Hauptteil steht oft würde + Infinitiv.', 'Mit sein/haben nimmt man würde statt der eigenen Form.', 'Konjunktiv II zeigt Irrealität.', 'Die wenn-Inversion braucht immer das Wort wenn.'), ['a', 'b', 'd'],
    'Верны утверждения о позиции глагола, würde и значении нереальности.'),

  // ---- 5 ord EASY ----
  ord(20, 'EASY', 'Соберите немецкое предложение: «Если бы у меня было время».', items4('Wenn', 'ich', 'Zeit', 'hätte'),
    'В придаточном с wenn глагол hätte стоит в конце.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Я бы пришёл».', items4('Ich', 'würde', 'kommen', '.'),
    'В главной части würde занимает вторую позицию, инфинитив — в конце.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Если бы я был богат».', items4('Wenn', 'ich', 'reich', 'wäre'),
    'Спрягаемый глагол wäre стоит в конце wenn-части.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Мы могли бы гулять».', items4('Wir', 'könnten', 'spazieren', 'gehen'),
    'Модальный глагол könnten во второй позиции, основной глагол — в конце.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Тогда я сказал бы да».', items5('Dann', 'würde', 'ich', 'Ja', 'sagen'),
    'После dann сначала идёт würde, затем подлежащее, инфинитив — в конце.'),

  // ---- 5 ord HARD ----
  ord(25, 'HARD', 'Соберите немецкое предложение: «Если бы у меня было время, я бы тебе помог».', items5('Wenn ich Zeit hätte,', 'würde', 'ich', 'dir', 'helfen'),
    'После придаточного würde занимает вторую позицию, инфинитив — в конце.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Будь у меня твой номер, я бы тебе звонил».', items5('Hätte ich deine Nummer,', 'würde', 'ich', 'dich', 'anrufen'),
    'Инверсия без wenn: hätte стоит первым, затем главная часть с würde.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «На твоём месте я бы воспользовался шансом».', items5('Wenn ich du wäre,', 'würde', 'ich', 'die Chance', 'nutzen'),
    'wäre в конце wenn-части, würde + Infinitiv в главной части.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Если бы погода была лучше, мы могли бы гулять».', items5('Wenn das Wetter besser wäre,', 'könnten', 'wir', 'spazieren', 'gehen'),
    'У модального глагола своя форма könnten вместо würde.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Если бы ты меня спросил, я бы сказал да».', items5('Wenn du mich fragen würdest,', 'würde', 'ich', 'Ja', 'sagen'),
    'В обеих частях Konjunktiv II: fragen würdest и würde sagen.'),

  // ---- 5 fb EASY ----
  fb(30, 'EASY', 'Wenn ich Zeit ___, würde ich dir helfen.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('habe', 'hätte', 'hatte', 'haben', 'gehabt'),
    'Konjunktiv II от haben — hätte.'),
  fb(31, 'EASY', 'Wenn ich reich ___, würde ich eine Weltreise machen.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('bin', 'war', 'wäre', 'sein', 'bist'),
    'Konjunktiv II от sein для ich — wäre.'),
  fb(32, 'EASY', 'Wenn ich reich wäre, ___ ich helfen.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('werde', 'wird', 'wurde', 'würde', 'worden'),
    'В главной части используется würde + Infinitiv.'),
  fb(33, 'EASY', '___ ich Zeit hätte, würde ich kommen.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('Wenn', 'Weil', 'Dass', 'Als', 'Ob'),
    'Союз нереального условия — wenn.'),
  fb(34, 'EASY', 'Wenn du kommen ___, wäre ich froh.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('kommst', 'kamst', 'kommen', 'gekommen', 'würdest'),
    'Konjunktiv II для du — würdest + Infinitiv.'),

  // ---- 5 fb HARD ----
  fb(35, 'HARD', '___ ich deine Nummer, würde ich dich öfter anrufen.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Habe', 'Wäre', 'Würde', 'Hätte', 'Hatte'),
    'Инверсия без wenn: спрягаемый Hätte стоит первым.'),
  fb(36, 'HARD', 'Wenn das Wetter besser wäre, ___ wir spazieren gehen.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('könntet', 'können', 'könnten', 'konnten', 'kann'),
    'У модального глагола своя форма Konjunktiv II — könnten (для wir).'),
  fb(37, 'HARD', 'Wenn ich du ___, würde ich diese Chance nutzen.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('bin', 'wäre', 'wären', 'war', 'sei'),
    'Для ich используется Konjunktiv II wäre.'),
  fb(38, 'HARD', 'Wenn du mich fragen ___, würde ich Ja sagen.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('fragst', 'fragtest', 'wirst', 'würde', 'würdest'),
    'Для du форма — würdest + Infinitiv.'),
  fb(39, 'HARD', 'Wenn ich mehr Geld hätte, ___ ich eine Weltreise machen.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('würde', 'werde', 'wurde', 'wird', 'worden'),
    'В главной части würde + Infinitiv для ich — würde.'),
];
