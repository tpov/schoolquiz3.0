'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Welcher Konnektor leitet einen Grund ein und schickt das Verb ans Ende? «Ich bleibe zu Hause, ___ es regnet.»', op4('weil', 'deshalb', 'außerdem', 'trotzdem'), 'a', 'weil вводит причину и отправляет глагол в конец придаточного.'),
  sc(1, 'EASY', 'Wähle die Folge: «Es ist spät, ___ gehe ich jetzt nach Hause.»', op4('weil', 'deshalb', 'denn', 'obwohl'), 'b', 'deshalb выражает следствие («поэтому») и стоит на первом месте с инверсией.'),
  sc(2, 'EASY', 'Welches Wort führt ein Beispiel ein?', op4('außerdem', 'beispielsweise', 'deswegen', 'trotzdem'), 'b', 'beispielsweise («например») вводит пример в аргументации.'),
  sc(3, 'EASY', 'Was bedeutet «der Grund»?', op4('следствие', 'недостаток', 'причина', 'преимущество'), 'c', 'der Grund переводится как «причина».'),
  sc(4, 'EASY', 'Welcher Konnektor steht im Hauptsatz und behält die normale Wortstellung? «Ich bin müde, ___ ich habe schlecht geschlafen.»', op4('weil', 'denn', 'da', 'damit'), 'b', 'denn соединяет два главных предложения с прямым порядком слов.'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Welcher Satz ist grammatisch korrekt?', op4('Es regnet, deshalb wir bleiben zu Hause.', 'Es regnet, deshalb bleiben wir zu Hause.', 'Es regnet, deshalb zu Hause wir bleiben.', 'Es regnet, deshalb bleiben zu Hause wir.'), 'b', 'После deshalb идёт инверсия: глагол на 2-м месте, затем подлежащее.'),
  sc(6, 'HARD', 'Welcher Satz verwendet «nämlich» korrekt?', op4('Nämlich er hat keine Zeit.', 'Er hat keine Zeit nämlich.', 'Er kommt nicht; er hat nämlich keine Zeit.', 'Er nämlich hat keine Zeit.'), 'c', 'nämlich стоит внутри предложения (после глагола), а не в начале.'),
  sc(7, 'HARD', 'Vervollständige korrekt: «Ich kaufe das Auto nicht, ___ es zu teuer ist und oft kaputtgeht.»', op4('weil', 'deshalb', 'denn', 'sondern'), 'a', 'weil вводит придаточное причины с глаголом ist в самом конце.'),
  sc(8, 'HARD', 'Welcher Satz mit «da» ist korrekt?', op4('Da er ist krank, bleibt er im Bett.', 'Da er krank ist, bleibt er im Bett.', 'Da ist er krank, er bleibt im Bett.', 'Er bleibt im Bett, da ist er krank.'), 'b', 'После da глагол ist уходит в конец придаточного: «Da er krank ist».'),
  sc(9, 'HARD', 'Welche Variante drückt das Gegenargument korrekt mit «zwar … aber» aus?', op4('Das Handy ist zwar teuer, aber es ist sehr gut.', 'Das Handy zwar ist teuer, aber gut es ist.', 'Zwar das Handy teuer ist, aber es gut ist.', 'Das Handy ist teuer zwar, aber gut.'), 'a', 'Конструкция zwar …, aber … соединяет уступку и контраргумент с правильным порядком V2.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Wörter leiten eine Folge («поэтому/следовательно») ein?', op5('deshalb', 'weil', 'deswegen', 'beispielsweise', 'daher'), ['a', 'c', 'e'], 'deshalb, deswegen и daher выражают следствие.'),
  mc(11, 'EASY', 'Welche Wörter führen einen Grund («причину») ein?', op5('weil', 'deshalb', 'da', 'denn', 'außerdem'), ['a', 'c', 'd'], 'weil, da и denn вводят причину.'),
  mc(12, 'EASY', 'Welche Wörter strukturieren eine Aufzählung von Argumenten?', op5('erstens', 'weil', 'zweitens', 'außerdem', 'deshalb'), ['a', 'c', 'd'], 'erstens, zweitens и außerdem структурируют перечисление аргументов.'),
  mc(13, 'EASY', 'Welche Ausdrücke leiten ein Beispiel ein?', op5('zum Beispiel', 'trotzdem', 'beispielsweise', 'deshalb', 'und zwar'), ['a', 'c', 'e'], 'zum Beispiel, beispielsweise и und zwar вводят/уточняют пример.'),
  mc(14, 'EASY', 'Welche deutschen Vokabeln gehören zum Thema Argumentation?', op5('das Argument', 'der Tisch', 'der Vorteil', 'der Nachteil', 'das Fenster'), ['a', 'c', 'd'], 'das Argument, der Vorteil и der Nachteil относятся к теме аргументации.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Welche Sätze sind grammatisch korrekt?', op6('Ich komme nicht, weil ich krank bin.', 'Ich komme nicht, weil ich bin krank.', 'Ich bin krank, deshalb komme ich nicht.', 'Ich bin krank, deshalb ich komme nicht.', 'Ich komme nicht, denn ich bin krank.', 'Ich komme nicht, denn bin ich krank.'), ['a', 'c', 'e'], 'Корректны: weil с глаголом в конце, deshalb с инверсией и denn с прямым порядком слов.'),
  mc(16, 'HARD', 'Nach welchen Konnektoren folgt eine Inversion (Verb an Position 2)?', op6('deshalb', 'weil', 'folglich', 'da', 'daher', 'damit'), ['a', 'c', 'e'], 'После deshalb, folglich и daher следует инверсия, потому что они занимают первое место.'),
  mc(17, 'HARD', 'Welche Sätze mit «nicht nur …, sondern auch …» sind korrekt?', op6('Er ist nicht nur klug, sondern auch fleißig.', 'Er ist nicht nur klug, sondern auch ist fleißig.', 'Das Auto ist nicht nur schnell, sondern auch sparsam.', 'Das Auto nicht nur schnell ist, sondern auch sparsam.', 'Sie spricht nicht nur Deutsch, sondern auch Französisch.', 'Sie nicht nur spricht Deutsch, sondern Französisch auch.'), ['a', 'c', 'e'], 'В корректных вариантах после sondern auch стоит только дополнение без повтора глагола.'),
  mc(18, 'HARD', 'Welche Sätze drücken eine korrekte Ursache-Folge-Beziehung aus?', op6('Das Projekt ist gut geplant, daher bin ich optimistisch.', 'Das Projekt ist gut geplant, daher ich bin optimistisch.', 'Da das Projekt gut geplant ist, bin ich optimistisch.', 'Da ist das Projekt gut geplant, ich bin optimistisch.', 'Das Projekt ist gut geplant, folglich vertraue ich dem Team.', 'Das Projekt ist gut geplant, folglich ich vertraue dem Team.'), ['a', 'c', 'e'], 'Корректны конструкции с инверсией после daher/folglich и придаточное с da и глаголом в конце.'),
  mc(19, 'HARD', 'Welche Aussagen über die Konnektoren stimmen?', op6('weil schickt das Verb ans Satzende.', 'denn ändert die Wortstellung im Hauptsatz nicht.', 'nämlich darf am Satzanfang stehen.', 'deshalb verlangt eine Inversion.', 'da kann einen Nebensatz einleiten.', 'denn leitet einen Nebensatz mit Endstellung ein.'), ['a', 'b', 'd', 'e'], 'Верно: weil ставит глагол в конец, denn не меняет порядок слов, deshalb требует инверсии, da вводит придаточное; неверно — nämlich не стоит в начале, а denn вводит главное, а не придаточное.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Я остаюсь дома, потому что идёт дождь».', items5('Ich', 'bleibe', 'zu Hause,', 'weil', 'es regnet.'), 'Порядок: Ich bleibe zu Hause, weil es regnet — глагол regnet в конце придаточного.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Уже поздно, поэтому я иду домой».', items5('Es', 'ist spät,', 'deshalb', 'gehe', 'ich nach Hause.'), 'После deshalb идёт инверсия: gehe ich nach Hause.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Он не приходит, ведь он болен».', items4('Er', 'kommt nicht,', 'denn', 'er ist krank.'), 'После denn сохраняется прямой порядок слов: er ist krank.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «У машины много преимуществ, например, она быстрая».', items5('Das Auto', 'hat viele Vorteile,', 'beispielsweise', 'ist es', 'schnell.'), 'beispielsweise стоит в начале части и вызывает инверсию: ist es schnell.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Во-первых, не хватает денег».', items4('Erstens', 'fehlt', 'das', 'Geld.'), 'erstens на первом месте вызывает инверсию: fehlt das Geld.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Машина не только дорогая, но и ненадёжная».', items5('Das Auto', 'ist', 'nicht nur teuer,', 'sondern auch', 'unzuverlässig.'), 'Конструкция nicht nur …, sondern auch … соединяет два качества.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Так как нет денег, мы откладываем проект».', items5('Da', 'kein Geld da ist,', 'verschieben', 'wir', 'das Projekt.'), 'Придаточное с da стоит впереди, поэтому главное начинается с глагола: verschieben wir.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Я за работу из дома, потому что так экономишь много времени».', items5('Ich bin für das Homeoffice,', 'weil', 'man', 'dadurch viel Zeit', 'spart.'), 'В придаточном с weil спрягаемый глагол spart уходит в самый конец.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Проект хорошо спланирован, следовательно, я доверяю команде».', items5('Das Projekt', 'ist gut geplant,', 'folglich', 'vertraue ich', 'dem Team.'), 'После folglich инверсия: vertraue ich dem Team (Dativ).'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Хотя телефон дорогой, я его покупаю».', items5('Obwohl', 'das Handy teuer ist,', 'kaufe', 'ich', 'es.'), 'Придаточное с obwohl (глагол ist в конце) впереди, потом главное с инверсией: kaufe ich es.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Ich bleibe zu Hause, ___ es regnet.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('weil', 'deshalb', 'außerdem', 'trotzdem', 'sondern'), 'weil вводит причину с глаголом в конце.'),
  fb(31, 'EASY', 'Es ist kalt, ___ ziehe ich eine Jacke an.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('weil', 'deshalb', 'denn', 'obwohl', 'sondern'), 'deshalb выражает следствие и вызывает инверсию: ziehe ich an.'),
  fb(32, 'EASY', 'Das Restaurant ist gut, ___ ist das Essen frisch.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('weil', 'denn', 'beispielsweise', 'sondern', 'obwohl'), 'beispielsweise вводит пример и стоит на первом месте с инверсией.'),
  fb(33, 'EASY', 'Er lernt viel, ___ er will die Prüfung bestehen.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('deshalb', 'weil', 'außerdem', 'denn', 'sondern'), 'denn соединяет два главных предложения с прямым порядком слов.'),
  fb(34, 'EASY', 'Erstens fehlt das Geld, ___ fehlt die Zeit.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('weil', 'obwohl', 'denn', 'sondern', 'zweitens'), 'zweitens продолжает перечисление аргументов («во-вторых») и вызывает инверсию; weil/obwohl/denn/sondern здесь дают неверный порядок слов.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Ich nehme den Job an, ___ er gut bezahlt ist und nah an meiner Wohnung liegt.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('weil', 'deshalb', 'sondern', 'denn', 'trotzdem'), 'weil вводит развёрнутую причину с глаголами в конце придаточного.'),
  fb(36, 'HARD', 'Das Auto ist nicht nur teuer, ___ auch unzuverlässig.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('weil', 'sondern', 'deshalb', 'denn', 'außerdem'), 'В конструкции nicht nur …, sondern auch … нужен союз sondern.'),
  fb(37, 'HARD', 'Das Projekt ist gut geplant, ___ bin ich optimistisch.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('weil', 'denn', 'deshalb', 'sondern', 'obwohl'), 'deshalb выражает следствие и требует инверсии: bin ich.'),
  fb(38, 'HARD', '___ das Wetter schlecht ist, machen wir trotzdem einen Ausflug.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Denn', 'Deshalb', 'Sondern', 'Obwohl', 'Außerdem'), 'Obwohl вводит уступительное придаточное с глаголом ist в конце.'),
  fb(39, 'HARD', 'Es gibt kein Geld, ___ müssen wir das Projekt verschieben.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('weil', 'denn', 'obwohl', 'sondern', 'folglich'), 'folglich выражает вывод/следствие и вызывает инверсию: müssen wir.'),
];
