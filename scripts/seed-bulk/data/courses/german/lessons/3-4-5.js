'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Ich hätte gern ein Zimmer ___ Frühstück, ich frühstücke gern im Hotel.', op4('mit', 'ohne', 'für', 'aus'), 'a', 'Вежливая просьба о номере с завтраком: предлог mit + Dativ; вторая часть «я люблю завтракать» подсказывает mit, а не ohne.'),
  sc(1, 'EASY', 'Wie sagt man höflich an der Rezeption: «Не могли бы вы мне помочь?»', op4('Könnten Sie mir bitte helfen?', 'Du musst mir helfen!', 'Hilf mir sofort!', 'Ich helfe Ihnen.'), 'a', 'Вежливый вопрос-просьба строится с Könnten Sie ... bitte ... ?'),
  sc(2, 'EASY', 'An der ___ bekommt man den Schlüssel für das Zimmer.', op4('Quittung', 'Rezeption', 'Rechnung', 'Stadtführung'), 'b', 'Ключ выдают на ресепшене — die Rezeption.'),
  sc(3, 'EASY', '___ es hier in der Nähe ein gutes Restaurant?', op4('Hat', 'Ist', 'Gibt', 'Sind'), 'c', 'Конструкция наличия — es gibt, поэтому в вопросе Gibt es ... ?'),
  sc(4, 'EASY', 'Ich nehme ein Taxi, ___ mein Koffer zu schwer ist.', op4('weil', 'aber', 'und', 'oder'), 'a', 'Причину вводит союз weil — «потому что».'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Welcher Satz ist im Perfekt korrekt: «Мы поехали в Мюнхен»?', op4('Wir haben nach München gefahren.', 'Wir sind nach München gefahren.', 'Wir sind nach München gefahrt.', 'Wir haben nach München gefahrt.'), 'b', 'Глагол fahren образует Perfekt с sein и формой gefahren.'),
  sc(6, 'HARD', 'Ergänzen Sie höflich: «Könnten Sie mir bitte eine gute Stadtführung ___ ?»', op4('empfehlen', 'empfiehlt', 'empfohlen', 'empfehle'), 'a', 'После модального Könnten стоит инфинитив — empfehlen.'),
  sc(7, 'HARD', 'Welcher Nebensatz mit weil ist korrekt?', op4('Ich bleibe hier, weil es regnet draußen.', 'Ich bleibe hier, weil draußen regnet es.', 'Ich bleibe hier, weil es draußen regnet.', 'Ich bleibe hier, weil regnet es draußen.'), 'c', 'В придаточном с weil спрягаемый глагол стоит в самом конце.'),
  sc(8, 'HARD', 'Wir sind drei Tage in Wien ___ und haben viele Sehenswürdigkeiten gesehen.', op4('bleiben', 'geblieben', 'gebleibt', 'blieben'), 'b', 'Глагол bleiben в Perfekt даёт причастие geblieben (с sein).'),
  sc(9, 'HARD', 'Welche Bitte ist am höflichsten?', op4('Geben Sie mir den Schlüssel!', 'Ich will den Schlüssel.', 'Ich hätte gern den Schlüssel für Zimmer 204.', 'Schlüssel für Zimmer 204!'), 'c', 'Оборот Ich hätte gern — самая вежливая форма просьбы.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Wörter passen zum Thema «Hotel»? (Mehrere richtig)', op5('die Rezeption', 'der Schlüssel', 'der Apfel', 'das Frühstück', 'die Katze'), ['a', 'b', 'd'], 'К теме отеля относятся ресепшен, ключ и завтрак; яблоко и кошка — нет.'),
  mc(11, 'EASY', 'Welche Sätze sind höfliche Bitten? (Mehrere richtig)', op5('Ich hätte gern einen Kaffee.', 'Gib mir Kaffee!', 'Könnten Sie mir bitte helfen?', 'Ich will Kaffee.', 'Hätten Sie ein Zimmer frei?'), ['a', 'c', 'e'], 'Вежливые формы — Ich hätte gern, Könnten Sie и Hätten Sie.'),
  mc(12, 'EASY', 'Was kann man an der Rezeption sagen? (Mehrere richtig)', op5('Ich möchte einchecken.', 'Ich möchte auschecken.', 'Ich möchte schlafen gehen.', 'Ich hätte gern die Rechnung.', 'Ich male ein Bild.'), ['a', 'b', 'd'], 'На ресепшене говорят о заселении, выселении и счёте.'),
  mc(13, 'EASY', 'Welche Wörter bedeuten etwas zum Bezahlen? (Mehrere richtig)', op5('die Rechnung', 'die Quittung', 'der Schlüssel', 'das Trinkgeld', 'die Sehenswürdigkeit'), ['a', 'b', 'd'], 'К оплате относятся счёт, квитанция и чаевые.'),
  mc(14, 'EASY', 'In welchen Sätzen steht «es gibt» richtig? (Mehrere richtig)', op5('Gibt es hier WLAN?', 'Es gibt ein Frühstück um acht.', 'Es gibt hier WLAN?', 'Hier gibt es ein Taxi.', 'Es hat hier WLAN.'), ['a', 'b', 'd'], 'Конструкция наличия — es gibt + Akkusativ, в вопросе Gibt es ... ?'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Welche Perfekt-Sätze über die Reise sind korrekt? (Mehrere richtig)', op6('Wir sind nach Wien gefahren.', 'Wir haben viele Sehenswürdigkeiten gesehen.', 'Wir haben nach Wien gefahren.', 'Wir sind drei Tage geblieben.', 'Wir haben drei Tage gebleibt.', 'Wir sind viele Sehenswürdigkeiten gesehen.'), ['a', 'b', 'd'], 'Глаголы движения и bleiben берут sein, а sehen — haben.'),
  mc(16, 'HARD', 'Welche Nebensätze mit weil sind grammatisch korrekt? (Mehrere richtig)', op5('Ich nehme ein Taxi, weil mein Koffer zu schwer ist.', 'Ich nehme ein Taxi, weil ist mein Koffer schwer.', 'Wir bleiben hier, weil es spät ist.', 'Wir bleiben hier, weil es spät ist sehr.', 'Sie wartet, weil der Bus zu spät kommt.'), ['a', 'c', 'e'], 'В придаточном с weil спрягаемый глагол всегда в конце.'),
  mc(17, 'HARD', 'Welche höflichen Bitten sind korrekt formuliert? (Mehrere richtig)', op5('Könnten Sie mir bitte die Rechnung bringen?', 'Könnten Sie mir bitte die Rechnung gebracht?', 'Ich hätte gern eine Quittung.', 'Ich hätte gern eine Quittung bekommen.', 'Hätten Sie ein Zimmer mit Frühstück?'), ['a', 'c', 'e'], 'После Könnten/hätten gern идёт инфинитив или существительное, без причастия.'),
  mc(18, 'HARD', 'Welche Sätze passen zur Situation «Etwas ist verloren»? (Mehrere richtig)', op5('Ich habe meinen Schlüssel verloren.', 'Mein Koffer ist unterwegs verloren gegangen.', 'Ich habe meinen Schlüssel gefunden.', 'Könnten Sie mir bitte helfen, ihn zu finden?', 'Der Schlüssel ist sehr schön.'), ['a', 'b', 'd'], 'О потере говорят verloren, verloren gegangen и просят о помощи.'),
  mc(19, 'HARD', 'Welche Fragen kann ein Tourist an der Rezeption stellen? (Mehrere richtig)', op6('Gibt es hier WLAN?', 'Könnten Sie mir eine Stadtführung empfehlen?', 'Wann gibt es Frühstück?', 'Wie heißt du?', 'Ich male gern Bilder.', 'Wo kann ich auschecken?'), ['a', 'b', 'c', 'f'], 'Турист на ресепшене вежливо спрашивает про WLAN, экскурсию, завтрак и где выселиться; «Wie heißt du?» (ты) и «Ich male gern Bilder» сюда не подходят.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «У меня есть ключ».', items4('Ich', 'habe', 'den', 'Schlüssel'), 'Прямой порядок: подлежащее, глагол, Akkusativ с артиклем den.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Здесь есть WLAN?».', items4('Gibt', 'es', 'hier', 'WLAN'), 'В вопросе es gibt глагол выходит на первое место: Gibt es ... ?'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Я хотел бы завтрак».', items4('Ich', 'hätte', 'gern', 'Frühstück'), 'Вежливый оборот: Ich hätte gern + желаемое.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Где ресепшен?».', items4('Wo', 'ist', 'die', 'Rezeption'), 'Вопрос с Wo: вопросительное слово, глагол, подлежащее.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Я беру такси».', items4('Ich', 'nehme', 'ein', 'Taxi'), 'Прямой порядок с Akkusativ: ein Taxi.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Не могли бы вы мне помочь?».', items5('Könnten', 'Sie', 'mir', 'bitte', 'helfen'), 'Вежливый вопрос: модальный глагол на первом месте, helfen — в конце.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Мы поехали в Вену».', items5('Wir', 'sind', 'nach', 'Wien', 'gefahren'), 'Perfekt с sein: вспомогательный sind на втором месте, gefahren в конце.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Я беру такси, потому что уже поздно».', items5('Ich', 'nehme', 'ein', 'Taxi,', 'weil es spät ist'), 'Главное предложение V2, а в придаточном с weil глагол ist в конце.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Я хотел бы ключ от номера 204».', items5('Ich', 'hätte', 'gern', 'den Schlüssel', 'für Zimmer 204'), 'Оборот Ich hätte gern + Akkusativ den Schlüssel и уточнение с für.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Не могли бы вы порекомендовать экскурсию?».', items5('Könnten', 'Sie', 'bitte', 'eine Stadtführung', 'empfehlen'), 'Könnten Sie bitte ... empfehlen: bitte после Sie, инфинитив empfehlen в самом конце.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Ich hätte gern ___ Schlüssel für mein Zimmer.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('den', 'die', 'das', 'dem', 'ein'), 'der Schlüssel в Akkusativ принимает артикль den.'),
  fb(31, 'EASY', 'Gibt ___ hier in der Nähe ein Taxi?', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('das', 'es', 'er', 'sie', 'man'), 'В конструкции наличия используется безличное es: Gibt es ... ?'),
  fb(32, 'EASY', 'Ich nehme ein Taxi, ___ es spät ist.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('aber', 'und', 'weil', 'oder', 'denn'), 'Причину с глаголом в конце вводит союз weil.'),
  fb(33, 'EASY', 'Könnten Sie mir bitte ___ ?', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('hilft', 'geholfen', 'hilfst', 'helfen', 'helfe'), 'После Könnten Sie ставится инфинитив helfen.'),
  fb(34, 'EASY', 'Wir sind nach Berlin ___ .', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('fahren', 'fährt', 'gefahrt', 'fuhr', 'gefahren'), 'Perfekt глагола fahren — причастие gefahren.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Ich möchte ___ , bitte geben Sie mir die Rechnung.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('auschecken', 'ausgecheckt', 'auscheckt', 'auszuchecken', 'checkst'), 'После möchte стоит инфинитив без zu — auschecken (выселяться).'),
  fb(36, 'HARD', 'Wir sind drei Tage in Wien ___ .', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('bleiben', 'geblieben', 'blieb', 'gebleibt', 'bleibt'), 'Perfekt глагола bleiben (с sein) — причастие geblieben.'),
  fb(37, 'HARD', 'Wir fahren ___ dem Taxi zum Bahnhof.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('für', 'ohne', 'mit', 'durch', 'gegen'), 'Только mit управляет Dativ (dem Taxi); für/ohne/durch/gegen требуют Akkusativ.'),
  fb(38, 'HARD', 'Könnten Sie mir bitte eine gute Stadtführung ___ ?', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('empfiehlt', 'empfohlen', 'empfehle', 'empfehlen', 'empfiehlst'), 'После Könnten Sie стоит инфинитив empfehlen.'),
  fb(39, 'HARD', 'Ich habe meinen Schlüssel ___ .', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('verliere', 'verliert', 'verlierst', 'verlor', 'verloren'), 'Perfekt глагола verlieren — причастие verloren.'),
];
