'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Ich bin der Meinung, dass dieser Film langweilig ___.', op4('ist', 'sein', 'wird sein', 'bist'), 'a',
    'В придаточном с dass спрягаемый глагол уходит в самый конец: ... dass der Film langweilig ist.'),
  sc(1, 'EASY', 'Welches Wort bedeutet «мнение»?', op4('die Meinung', 'der Eindruck', 'der Standpunkt', 'die Ansicht'), 'a',
    'Die Meinung — это «мнение»; остальные слова обозначают впечатление, точку зрения и взгляд.'),
  sc(2, 'EASY', '___ Meinung nach ist das Buch zu lang.', op4('Meiner', 'Meine', 'Mein', 'Meinem'), 'a',
    'В обороте meiner Meinung nach слово Meinung стоит в дательном падеже, поэтому meiner.'),
  sc(3, 'EASY', 'Ich finde, das Essen ___ wirklich gut.', op4('ist', 'sind', 'seid', 'bin'), 'a',
    'Без союза dass порядок слов прямой, а к подлежащему das Essen подходит форма ist.'),
  sc(4, 'EASY', 'Welches Verb passt: «Я думаю, что …» — Ich ___, dass …', op4('denke', 'denkst', 'denkt', 'denken'), 'a',
    'К местоимению ich глагол denken спрягается как denke.'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Welcher Satz ist korrekt?', op4('Meiner Meinung nach das Gesetz ist sinnvoll.', 'Meiner Meinung nach ist das Gesetz sinnvoll.', 'Meiner Meinung nach das Gesetz sinnvoll ist.', 'Meiner Meinung nach das ist Gesetz sinnvoll.'), 'b',
    'После meiner Meinung nach действует инверсия: глагол стоит на втором месте — ist das Gesetz sinnvoll.'),
  sc(6, 'HARD', 'Ich bin der Ansicht, dass diese Entscheidung ein Fehler ___.', op4('war', 'wäre sein', 'gewesen', 'sein war'), 'a',
    'Спрягаемая форма war уходит в конец придаточного с dass: ... dass diese Entscheidung ein Fehler war.'),
  sc(7, 'HARD', 'Er ist fest davon ___, dass er recht hat.', op4('überzeugt', 'überzeugen', 'überzeugst', 'Überzeugung'), 'a',
    'Устойчивая конструкция überzeugt sein von требует причастия überzeugt: er ist überzeugt.'),
  sc(8, 'HARD', 'Welcher Satz ohne dass ist korrekt gebaut?', op4('Ich glaube, dass das ist falsch.', 'Ich glaube, das ist falsch.', 'Ich glaube, das falsch ist.', 'Ich glaube das, ist falsch.'), 'b',
    'После Ich glaube можно опустить dass, и тогда порядок слов прямой: das ist falsch.'),
  sc(9, 'HARD', 'Aus meiner Sicht ist der Vorschlag ___ sinnvoll. (вполне)', op4('durchaus', 'kaum', 'gar nicht', 'selten'), 'a',
    'Наречие durchaus усиливает положительную оценку и значит «вполне, безусловно».'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Wendungen leiten eine Meinung ein?', op5('Ich finde, dass …', 'Meiner Meinung nach …', 'Ich bin der Ansicht, dass …', 'Guten Appetit!', 'Wie spät ist es?'), ['a', 'b', 'c'],
    'Ich finde, Meiner Meinung nach и Ich bin der Ansicht — вводные обороты мнения, остальные фразы к нему не относятся.'),
  mc(11, 'EASY', 'Welche Wörter bezeichnen eine Meinung oder Sichtweise?', op5('die Meinung', 'der Standpunkt', 'die Ansicht', 'das Frühstück', 'der Bahnhof'), ['a', 'b', 'c'],
    'Meinung, Standpunkt и Ansicht относятся к выражению взгляда; Frühstück и Bahnhof — нет.'),
  mc(12, 'EASY', 'In welchen Sätzen geht das Verb ans Ende?', op5('Ich denke, dass er recht hat.', 'Ich glaube, dass es zu teuer ist.', 'Ich finde es gut.', 'Sie sagt, dass sie kommt.', 'Er ist müde.'), ['a', 'b', 'd'],
    'Глагол уходит в конец только в придаточном с dass; в простых предложениях он остаётся на втором месте.'),
  mc(13, 'EASY', 'Welche Adverbien können eine Bewertung verstärken oder abschwächen?', op5('durchaus', 'eindeutig', 'eher', 'der Tisch', 'gehen'), ['a', 'b', 'c'],
    'Durchaus, eindeutig и eher — наречия оценки, а Tisch и gehen ими не являются.'),
  mc(14, 'EASY', 'Welche Sätze sind korrektes Deutsch?', op5('Ich bin der Meinung, dass das stimmt.', 'Meiner Meinung nach ist das richtig.', 'Ich finde, das ist gut.', 'Meiner Meinung das ist gut.', 'Ich denke dass das gut.'), ['a', 'b', 'c'],
    'Первые три предложения построены верно; в остальных нарушены оборот nach и союз dass.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Welche Sätze mit dass sind grammatisch korrekt?', op5('Ich glaube, dass er morgen kommt.', 'Ich finde, dass das Buch zu lang ist.', 'Ich denke, dass er recht hat.', 'Ich glaube, dass kommt er morgen.', 'Ich finde, dass ist das falsch.'), ['a', 'b', 'c'],
    'В правильных вариантах спрягаемый глагол стоит в конце придаточного с dass.'),
  mc(16, 'HARD', 'Welche Sätze drücken eine starke Überzeugung aus?', op5('Ich bin fest davon überzeugt, dass …', 'Ich betone, dass …', 'Ich behaupte ganz klar, dass …', 'Vielleicht, ich weiß nicht.', 'Das ist mir egal.'), ['a', 'b', 'c'],
    'Überzeugt sein, betonen и behaupten выражают твёрдую позицию; две последние фразы — нет.'),
  mc(17, 'HARD', 'Welche Sätze nutzen meiner Meinung nach mit korrekter Inversion?', op5('Meiner Meinung nach ist das ein Fehler.', 'Meiner Meinung nach sollte man warten.', 'Meiner Meinung nach das ist ein Fehler.', 'Meiner Meinung nach kann man das ändern.', 'Meiner Meinung nach man wartet.'), ['a', 'b', 'd'],
    'После meiner Meinung nach глагол обязан стоять на втором месте — ist, sollte, kann.'),
  mc(18, 'HARD', 'Welche Verben passen zu «утверждать / подчёркивать / быть убеждённым»?', op5('behaupten', 'betonen', 'überzeugt sein', 'frühstücken', 'spazieren'), ['a', 'b', 'c'],
    'Behaupten, betonen и überzeugt sein относятся к аргументации, а frühstücken и spazieren — нет.'),
  mc(19, 'HARD', 'Welche Adverbien passen sinnvoll vor ein Adjektiv der Bewertung?', op5('ziemlich gut', 'durchaus richtig', 'eindeutig falsch', 'gehen schnell', 'der wichtig'), ['a', 'b', 'c'],
    'Ziemlich, durchaus и eindeutig стоят перед прилагательным; остальные сочетания грамматически неверны.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Я думаю, что он прав».', items4('Ich', 'denke,', 'dass', 'er recht hat'),
    'В придаточном с dass глагол hat уходит в конец: Ich denke, dass er recht hat.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «По моему мнению, это правильно».', items4('Meiner', 'Meinung nach', 'ist', 'das richtig'),
    'После meiner Meinung nach действует инверсия: глагол ist стоит на втором месте.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Я считаю, что это хорошо».', items4('Ich', 'finde,', 'das', 'ist gut'),
    'Без союза dass порядок слов прямой: Ich finde, das ist gut.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Я придерживаюсь мнения».', items4('Ich', 'bin', 'der', 'Meinung'),
    'Устойчивый оборот Ich bin der Meinung вводит личное мнение.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Я убеждён в этом».', items4('Ich', 'bin', 'davon', 'überzeugt'),
    'Конструкция überzeugt sein von даёт порядок Ich bin davon überzeugt.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Я придерживаюсь мнения, что это решение было ошибкой».', items5('Ich bin der Ansicht,', 'dass', 'diese Entscheidung', 'ein Fehler', 'war'),
    'Спрягаемый глагол war уходит в конец придаточного с dass.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «По моему мнению, нужно проводить больше времени с семьёй».', items5('Meiner Meinung nach', 'sollte', 'man', 'mehr Zeit', 'mit der Familie verbringen'),
    'После оборота nach стоит модальный глагол sollte, а смысловой verbringen — в конце рамки.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Я твёрдо убеждён, что он прав».', items5('Ich bin', 'fest davon', 'überzeugt,', 'dass', 'er recht hat'),
    'После überzeugt идёт придаточное с dass, где hat завершает предложение.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «С моей точки зрения, предложение вполне разумно».', items5('Aus meiner Sicht', 'ist', 'der Vorschlag', 'durchaus', 'sinnvoll'),
    'После Aus meiner Sicht — инверсия (ist), а durchaus усиливает оценку sinnvoll.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Я считаю, что книга однозначно слишком длинная».', items5('Ich finde,', 'dass', 'das Buch', 'eindeutig', 'zu lang ist'),
    'В придаточном с dass глагол ist стоит в самом конце, а eindeutig усиливает оценку.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Ich bin der ___, dass das stimmt.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('Meinung', 'Standpunkt', 'Eindruck', 'Bahnhof', 'Tisch'),
    'Устойчивый оборот звучит как Ich bin der Meinung, dass …; остальные существительные в эту рамку не подходят.'),
  fb(31, 'EASY', '___ Meinung nach ist das Wetter heute schön.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Mein', 'Meiner', 'Meine', 'Meinem', 'Meines'),
    'В обороте meiner Meinung nach используется дательная форма meiner.'),
  fb(32, 'EASY', 'Ich finde, das ___ wirklich interessant.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('bin', 'sind', 'ist', 'seid', 'bist'),
    'К подлежащему das подходит форма 3-го лица единственного числа ist.'),
  fb(33, 'EASY', 'Ich ___, dass er bald kommt.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('glaubst', 'glaubt', 'glauben', 'glaube', 'geglaubt'),
    'К местоимению ich глагол glauben спрягается как glaube.'),
  fb(34, 'EASY', 'Aus meiner Sicht ist der Plan ___ gut. (вполне)', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('kaum', 'selten', 'nie', 'gar nicht', 'durchaus'),
    'Наречие durchaus значит «вполне» и усиливает положительную оценку.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Ich bin der Ansicht, dass diese Lösung falsch ___.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('ist', 'sein', 'sind', 'bist', 'wird sein'),
    'Спрягаемый глагол ist уходит в конец придаточного с dass.'),
  fb(36, 'HARD', 'Meiner Meinung nach ___ das Gesetz wirklich sinnvoll.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('sein', 'ist', 'seid', 'bin', 'wäre gewesen'),
    'После meiner Meinung nach инверсия ставит глагол ist на второе место.'),
  fb(37, 'HARD', 'Er ist fest davon ___, dass er gewinnt.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('überzeugen', 'überzeugst', 'überzeugt', 'Überzeugung', 'überzeuge'),
    'Конструкция überzeugt sein требует причастия überzeugt.'),
  fb(38, 'HARD', 'Ich ___, dass dieser Vorschlag ein großer Fehler war.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('behauptest', 'behauptet', 'behaupten', 'behaupte', 'behauptend'),
    'К местоимению ich глагол behaupten в настоящем времени спрягается как behaupte.'),
  fb(39, 'HARD', 'Ich finde das Buch ___ zu lang. (однозначно)', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('kaum', 'vielleicht', 'selten', 'manchmal', 'eindeutig'),
    'Наречие eindeutig значит «однозначно» и усиливает оценку zu lang.'),
];
