'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Das Auto ___ in der Werkstatt repariert.', op4('wird', 'werden', 'wirst', 'werde'), 'a',
    'В пассиве при подлежащем "das Auto" (3-е лицо ед. ч.) werden принимает форму "wird".'),
  sc(1, 'EASY', 'Hier ___ moderne Maschinen hergestellt.', op4('wird', 'werden', 'werdet', 'werde'), 'b',
    'Подлежащее "Maschinen" во множественном числе, поэтому нужна форма "werden".'),
  sc(2, 'EASY', 'Welcher Satz ist korrektes Passiv?', op4('Das Buch liest.', 'Das Buch wird gelesen.', 'Das Buch wird lesen.', 'Das Buch ist lesen.'), 'b',
    'Vorgangspassiv строится как werden + Partizip II ("gelesen") в конце.'),
  sc(3, 'EASY', 'Was ist das Partizip II von "machen"?', op4('machte', 'gemacht', 'machen', 'gemachen'), 'b',
    'Слабый глагол образует Partizip II по схеме ge- + основа + -t, то есть "gemacht".'),
  sc(4, 'EASY', 'Die Rechnung ___ sofort bezahlt.', op4('werde', 'wirst', 'wird', 'werdet'), 'c',
    'Подлежащее "die Rechnung" — 3-е лицо ед. ч., поэтому werden = "wird".'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Wie heißt das Passiv von "Der Mechaniker repariert das Auto"?', op4('Das Auto repariert den Mechaniker.', 'Das Auto wird vom Mechaniker repariert.', 'Das Auto wird von dem Mechaniker reparieren.', 'Der Mechaniker wird das Auto repariert.'), 'b',
    'Прямое дополнение становится подлежащим, деятель вводится через von + Dativ ("vom Mechaniker").'),
  sc(6, 'HARD', 'Wähle die korrekte Form: "Diese Software ___ von Millionen Menschen benutzt."', op4('werden', 'wird', 'wirst', 'benutzt wird'), 'b',
    'Подлежащее "die Software" — единственное число, поэтому werden = "wird", а Partizip II стоит в конце.'),
  sc(7, 'HARD', 'Welches Partizip II ist richtig: "Der Brief wird ___."', op4('schreibt', 'geschreibt', 'geschrieben', 'schreiben'), 'c',
    'Сильный глагол "schreiben" образует Partizip II с изменением корня: "geschrieben".'),
  sc(8, 'HARD', 'Welcher Satz bildet das Partizip II ohne ge-?', op4('Das Auto wird gereparieren.', 'Das Auto wird repariert.', 'Das Auto wird gerepariert.', 'Das Auto wird reparieren.'), 'b',
    'Глаголы на -ieren образуют Partizip II без приставки ge-, то есть "repariert".'),
  sc(9, 'HARD', 'Wie wird der Agens (Täter) im Passiv eingeführt?', op4('mit + Akkusativ', 'für + Akkusativ', 'von + Dativ', 'zu + Dativ'), 'c',
    'Деятель в пассиве вводится предлогом von, который требует Dativ.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Formen von "werden" passen zu einem Passivsatz im Präsens?', op5('wird', 'werden', 'wurde', 'worden', 'geworden'), ['a', 'b'],
    'В Präsens-пассиве используются спрягаемые формы "wird" (ед.) и "werden" (мн.); остальные — прошедшее или Partizip.'),
  mc(11, 'EASY', 'Welche Sätze sind grammatisch korrektes Vorgangspassiv?', op5('Das Haus wird gebaut.', 'Die Türen werden geöffnet.', 'Das Haus baut.', 'Das Haus wird bauen.', 'Die Türen öffnen wird.'), ['a', 'b'],
    'Корректный пассив — это werden + Partizip II в конце, согласованный с числом подлежащего.'),
  mc(12, 'EASY', 'Welche Wörter sind ein korrektes Partizip II?', op5('gemacht', 'verkauft', 'machen', 'verkaufen', 'macht'), ['a', 'b'],
    'Partizip II здесь — "gemacht" (слабый с ge-) и "verkauft" (неотделяемая приставка ver- без ge-).'),
  mc(13, 'EASY', 'In welchen Sätzen steht "werden" richtig konjugiert?', op5('Die Briefe werden gelesen.', 'Der Brief wird gelesen.', 'Der Brief werden gelesen.', 'Die Briefe wird gelesen.', 'Der Brief werdet gelesen.'), ['a', 'b'],
    'werden согласуется с числом: "wird" для единственного, "werden" для множественного подлежащего.'),
  mc(14, 'EASY', 'Welche Partizipien II werden ohne "ge-" gebildet?', op5('repariert', 'verkauft', 'gemacht', 'gespielt', 'gekauft'), ['a', 'b'],
    'Глаголы на -ieren ("repariert") и с неотделяемой приставкой ("verkauft") образуют Partizip II без ge-.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Welche Sätze sind korrekt aus dem Aktiv ins Passiv überführt?', op5('Aktiv: Man baut das Haus. → Das Haus wird gebaut.', 'Aktiv: Sie verkaufen die Autos. → Die Autos werden verkauft.', 'Aktiv: Er liest das Buch. → Das Buch wird lesen.', 'Aktiv: Wir machen die Arbeit. → Die Arbeit werden gemacht.', 'Aktiv: Er repariert das Rad. → Das Rad repariert.'), ['a', 'b'],
    'При переходе Akkusativ-дополнение становится подлежащим в Nominativ, а werden согласуется с ним по числу.'),
  mc(16, 'HARD', 'Welche Sätze führen den Agens korrekt mit "von + Dativ" ein?', op6('Das Auto wird von dem Mechaniker repariert.', 'Die Software wird von Millionen Menschen benutzt.', 'Das Auto wird von den Mechaniker repariert.', 'Die Software wird von Millionen Menschen benutzen.', 'Das Auto wird mit dem Mechaniker repariert.', 'Das Auto wird für den Mechaniker repariert.'), ['a', 'b'],
    'von требует Dativ ("dem Mechaniker", "Millionen Menschen"), а Partizip II стоит в конце.'),
  mc(17, 'HARD', 'Welche Partizip-II-Formen sind richtig gebildet?', op5('geschrieben', 'genommen', 'geschreibt', 'genehmt', 'nimmt'), ['a', 'b'],
    'Сильные глаголы образуют Partizip II с изменением корня: "geschrieben" и "genommen".'),
  mc(18, 'HARD', 'Welche Sätze sind korrektes Präsens-Passiv im Plural?', op5('In Deutschland werden viele Autos exportiert.', 'Hier werden moderne Maschinen hergestellt.', 'In Deutschland wird viele Autos exportiert.', 'Hier werdet moderne Maschinen hergestellt.', 'In Deutschland werden viele Autos exportieren.'), ['a', 'b'],
    'При множественном подлежащем нужна форма "werden", а отделяемая приставка соединяется в Partizip II ("hergestellt").'),
  mc(19, 'HARD', 'Welche Aussagen über das Vorgangspassiv sind richtig?', op5('Das Vollverb steht als Partizip II am Satzende.', 'Der Agens kann mit "von + Dativ" genannt werden.', 'Das Hilfsverb im Präsens-Passiv ist "sein".', 'Das Akkusativobjekt bleibt im Passiv unverändert.', 'Partizip II von -ieren-Verben hat immer "ge-".'), ['a', 'b'],
    'В пассиве смысловой глагол стоит как Partizip II в конце, а деятель при необходимости вводится через von + Dativ.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Машину ремонтируют».', items4('Das', 'Auto', 'wird', 'repariert'),
    'Порядок Vorgangspassiv: подлежащее, спрягаемое "wird", затем Partizip II в конце.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Счёт оплачивается».', items4('Die', 'Rechnung', 'wird', 'bezahlt'),
    'V2-порядок: подлежащее на первом месте, "wird" на втором, Partizip II "bezahlt" в конце.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Дом строится».', items4('Das', 'Haus', 'wird', 'gebaut'),
    'Partizip II "gebaut" стоит в конце рамочной конструкции после "wird".'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Книгу читают».', items4('Das', 'Buch', 'wird', 'gelesen'),
    'Сильный глагол даёт Partizip II "gelesen", который замыкает предложение.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Двери открываются».', items4('Die', 'Türen', 'werden', 'geöffnet'),
    'Множественное подлежащее "die Türen" требует формы "werden", а Partizip II стоит в конце.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Машину ремонтирует механик (пассив)».', items5('Das', 'Auto', 'wird', 'vom Mechaniker', 'repariert'),
    'Деятель "vom Mechaniker" (von + Dativ) стоит перед Partizip II, который замыкает рамку.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Здесь производят современные станки».', items5('Hier', 'werden', 'moderne', 'Maschinen', 'hergestellt'),
    'Обстоятельство "hier" занимает первое место, "werden" — второе, Partizip II "hergestellt" — конец.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «В Германии экспортируется много автомобилей».', items5('In Deutschland', 'werden', 'viele', 'Autos', 'exportiert'),
    'При выносе обстоятельства вперёд "werden" остаётся на втором месте (V2), а Partizip II — в конце.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Этой программой пользуются миллионы людей».', items5('Die Software', 'wird', 'von Millionen', 'Menschen', 'benutzt'),
    'Агенс "von Millionen Menschen" вводится через von + Dativ, а Partizip II "benutzt" стоит в конце.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Письмо пишется сейчас».', items5('Der', 'Brief', 'wird', 'jetzt', 'geschrieben'),
    'Наречие времени стоит в середине, а сильный Partizip II "geschrieben" замыкает предложение.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Das Auto ___ repariert.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('wird', 'werden', 'werdet', 'werde', 'wirst'),
    'Подлежащее "das Auto" — 3-е лицо ед. ч., поэтому нужна форма "wird".'),
  fb(31, 'EASY', 'Die Maschinen ___ hergestellt.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('wird', 'werden', 'werdet', 'werde', 'wirst'),
    'Множественное подлежащее "die Maschinen" требует формы "werden".'),
  fb(32, 'EASY', 'Das Buch wird ___.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('liest', 'lesen', 'gelesen', 'gelest', 'last'),
    'В пассиве в конце стоит Partizip II сильного глагола "gelesen".'),
  fb(33, 'EASY', 'Die Arbeit wird ___.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('macht', 'machen', 'machte', 'gemacht', 'gemachen'),
    'Слабый глагол образует Partizip II "gemacht" по схеме ge- + основа + -t.'),
  fb(34, 'EASY', 'Die Rechnung ___ bezahlt.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('werden', 'werdet', 'werde', 'wirst', 'wird'),
    'Подлежащее "die Rechnung" — единственное число, поэтому werden = "wird".'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Der Brief wird ___.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('geschrieben', 'geschreibt', 'schreibt', 'schreiben', 'schrieb'),
    'Сильный глагол "schreiben" даёт Partizip II "geschrieben" с изменением корня.'),
  fb(36, 'HARD', 'Das Auto wird von ___ Mechaniker repariert.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('der', 'dem', 'den', 'des', 'die'),
    'Предлог von требует Dativ, поэтому артикль мужского рода — "dem".'),
  fb(37, 'HARD', 'Hier ___ moderne Maschinen hergestellt.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('wird', 'werdet', 'werden', 'werde', 'wirst'),
    'Подлежащее "Maschinen" во множественном числе, поэтому нужна форма "werden".'),
  fb(38, 'HARD', 'Das Rad wird ___ (reparieren).', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('gereparieren', 'gerepariert', 'reparieren', 'repariert', 'reparierte'),
    'Глагол на -ieren образует Partizip II без ge-: "repariert".'),
  fb(39, 'HARD', 'Die Software wird von Millionen Menschen ___.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('benutzen', 'benutzt wird', 'gebenutzt', 'benutzen wird', 'benutzt'),
    'Неотделяемая приставка be- даёт Partizip II без ge-: "benutzt", стоящий в конце.'),
];
