'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ---- 5 sc EASY (idx 0..4) ----
  sc(0, 'EASY', 'Wie heißt «отпуск» auf Deutsch?', op4('der Urlaub', 'die Reise', 'das Hotel', 'der Koffer'), 'a', 'Слово «der Urlaub» означает отпуск.'),
  sc(1, 'EASY', 'Welches Wort bedeutet «чемодан»?', op4('der Pass', 'das Gepäck', 'der Koffer', 'der Flug'), 'c', 'Слово «der Koffer» означает чемодан.'),
  sc(2, 'EASY', 'Was passt? Ich ___ ein Zimmer im Hotel.', op4('buche', 'buchst', 'bucht', 'buchen'), 'a', 'С местоимением «ich» глагол buchen имеет окончание -e.'),
  sc(3, 'EASY', 'Was passt? ___ Sommer fahren wir ans Meer.', op4('Am', 'Im', 'Um', 'An'), 'b', 'С названиями времён года используется предлог «im».'),
  sc(4, 'EASY', 'Wie heißt «самолёт» auf Deutsch?', op4('der Flug', 'das Flugzeug', 'die Grenze', 'die Aussicht'), 'b', 'Слово «das Flugzeug» означает самолёт.'),

  // ---- 5 sc HARD (idx 5..9) ----
  sc(5, 'HARD', 'Was ist korrekt? Nächste Woche ___ ich nach Italien.', op4('ich fahre', 'fahre', 'fahren', 'fährt'), 'b', 'После обстоятельства времени в начале предложения идёт глагол, потом подлежащее — поэтому «fahre».'),
  sc(6, 'HARD', 'Wähle die richtige Form: Ich freue ___ auf den Urlaub.', op4('mir', 'mich', 'sich', 'dich'), 'b', 'Возвратный глагол sich freuen с «ich» требует местоимения «mich».'),
  sc(7, 'HARD', 'Was ist korrekt? Ich ___ im Sommer verreisen.', op4('werde', 'wird', 'werdet', 'wirst'), 'a', 'Будущее с werden и местоимением «ich» образуется формой «werde».'),
  sc(8, 'HARD', 'Welcher Satz ist korrekt?', op4('Im August wir fliegen nach Spanien.', 'Im August fliegen wir nach Spanien.', 'Im August fliegen nach Spanien wir.', 'Im August wir nach Spanien fliegen.'), 'b', 'При выносе обстоятельства времени вперёд глагол стоит на втором месте, а подлежащее — после него.'),
  sc(9, 'HARD', 'Was passt? Vergiss deinen Pass nicht ___ der Grenze!', op4('an', 'in', 'auf', 'um'), 'a', 'С существительным Grenze для обозначения места используется предлог «an».'),

  // ---- 5 mc EASY (idx 10..14) ----
  mc(10, 'EASY', 'Welche Wörter gehören zum Thema «Reisen»?', op5('die Reise', 'der Urlaub', 'die Schule', 'das Brot', 'der Koffer'), ['a', 'b', 'e'], 'К теме путешествий относятся die Reise, der Urlaub и der Koffer.'),
  mc(11, 'EASY', 'Welche Sätze sprechen über die Zukunft?', op5('Nächste Woche fahre ich weg.', 'Gestern war ich zu Hause.', 'Im Sommer fliegen wir ans Meer.', 'Ich werde morgen verreisen.', 'Letztes Jahr war es kalt.'), ['a', 'c', 'd'], 'О будущем говорят предложения с «nächste Woche», «im Sommer» и «werde ... morgen».'),
  mc(12, 'EASY', 'Welche Wörter haben den Artikel «das»?', op5('Hotel', 'Zimmer', 'Koffer', 'Gepäck', 'Reise'), ['a', 'b', 'd'], 'Артикль «das» имеют слова Hotel, Zimmer и Gepäck.'),
  mc(13, 'EASY', 'Welche Verben passen zum Reisen?', op6('buchen', 'verreisen', 'kochen', 'fliegen', 'singen', 'fahren'), ['a', 'b', 'd', 'f'], 'К путешествиям подходят глаголы buchen, verreisen, fliegen и fahren.'),
  mc(14, 'EASY', 'Welche Wörter bedeuten «жильё/размещение» oder «номер»?', op5('die Unterkunft', 'das Zimmer', 'die Grenze', 'der Flug', 'der Pass'), ['a', 'b'], 'Слова die Unterkunft (жильё) и das Zimmer (номер) обозначают место для проживания.'),

  // ---- 5 mc HARD (idx 15..19) ----
  mc(15, 'HARD', 'Welche Sätze sind grammatisch korrekt?', op5('Ich freue mich auf den Urlaub.', 'Im Juli fahren wir nach Italien.', 'Wir werden buchen ein Hotel.', 'Nächsten Freitag fliegen wir nach Spanien.', 'Ich werde mich freuen den Urlaub.'), ['a', 'b', 'd'], 'Верны предложения с правильным порядком слов и возвратным freuen sich auf + Akkusativ.'),
  mc(16, 'HARD', 'In welchen Sätzen ist die Zeitangabe richtig?', op6('am Montag', 'im Juli', 'um 8 Uhr', 'in Montag', 'am Juli', 'um Juli'), ['a', 'b', 'c'], 'Правильно: am Montag (день), im Juli (месяц), um 8 Uhr (час).'),
  mc(17, 'HARD', 'Welche Formen von «werden» sind richtig zugeordnet?', op5('ich werde', 'du wirst', 'wir werden', 'er werden', 'ihr wird'), ['a', 'b', 'c'], 'Правильно спряжены: ich werde, du wirst, wir werden.'),
  mc(18, 'HARD', 'Welche Sätze drücken einen Reiseplan aus?', op5('Ich werde im Sommer verreisen.', 'Nächste Woche fahre ich nach Berlin.', 'Ich habe gestern gepackt.', 'Im August fliegen wir nach Rom.', 'Letzte Woche war ich am Meer.'), ['a', 'b', 'd'], 'О планах поездки говорят предложения с werden и с обстоятельством будущего времени.'),
  mc(19, 'HARD', 'Welche Wörter passen nach «Ich freue mich auf ___»?', op5('den Urlaub', 'die Reise', 'dem Hotel', 'den Flug', 'der Grenze'), ['a', 'b', 'd'], 'После «sich freuen auf» нужен Akkusativ: den Urlaub, die Reise, den Flug.'),

  // ---- 5 ord EASY (idx 20..24) ----
  ord(20, 'EASY', 'Соберите немецкое предложение: «Я бронирую номер».', items4('Ich', 'buche', 'ein', 'Zimmer'), 'Глагол buche стоит на втором месте после подлежащего Ich.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Летом мы едем к морю».', items5('Im', 'Sommer', 'fahren', 'wir', 'ans Meer'), 'После обстоятельства времени «Im Sommer» глагол стоит на втором месте.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Я лечу в Испанию».', items4('Ich', 'fliege', 'nach', 'Spanien'), 'С названиями стран направление выражается предлогом nach.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Мы ждём отпуск».', items5('Wir', 'freuen', 'uns', 'auf den', 'Urlaub'), 'Возвратное местоимение uns стоит сразу после глагола freuen.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Мой паспорт лежит в чемодане».', items5('Mein', 'Pass', 'liegt', 'im', 'Koffer'), 'Глагол liegt стоит на втором месте после подлежащего Mein Pass.'),

  // ---- 5 ord HARD (idx 25..29) ----
  ord(25, 'HARD', 'Соберите немецкое предложение: «В следующую пятницу мы летим в Испанию».', items5('Nächsten Freitag', 'fliegen', 'wir', 'nach', 'Spanien'), 'После обстоятельства времени глагол fliegen занимает второе место, а подлежащее идёт после него.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Летом я снова уеду в путешествие».', items5('Im Sommer', 'werde', 'ich', 'wieder', 'verreisen'), 'В будущем с werden спрягаемая форма стоит на втором месте, а инфинитив verreisen — в конце.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Я забронировал двухместный номер».', items5('Ich', 'habe', 'ein', 'Doppelzimmer', 'gebucht'), 'В Perfekt вспомогательный глагол habe стоит на втором месте, а причастие gebucht — в конце.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «В июле мы бронируем отель».', items5('Im Juli', 'buchen', 'wir', 'ein', 'Hotel'), 'С названием месяца используется предлог im, а глагол buchen стоит на втором месте.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Я очень жду отпуск».', items5('Ich', 'freue mich', 'sehr', 'auf den', 'Urlaub'), 'Конструкция sich freuen auf требует Akkusativ — auf den Urlaub.'),

  // ---- 5 fb EASY (idx 30..34) ----
  fb(30, 'EASY', 'Ich buche ein ___ im Hotel.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('Zimmer', 'Koffer', 'Grenze', 'Pass', 'Flug'), 'В отеле бронируют das Zimmer (номер).'),
  fb(31, 'EASY', 'Wir fahren ___ Sommer ans Meer.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('am', 'im', 'um', 'auf', 'an'), 'С названиями времён года используется предлог im.'),
  fb(32, 'EASY', 'Ich freue mich auf ___ Urlaub.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('die', 'dem', 'den', 'das', 'der'), 'После «freuen sich auf» нужен Akkusativ — den Urlaub.'),
  fb(33, 'EASY', 'Nächste Woche ___ ich nach Italien.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('fährt', 'fahren', 'fahrt', 'fahre', 'fuhr'), 'С местоимением ich глагол fahren имеет форму fahre.'),
  fb(34, 'EASY', 'Ich packe meinen ___ für die Reise.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Hotel', 'Grenze', 'Aussicht', 'Unterkunft', 'Koffer'), 'Для путешествия упаковывают den Koffer (чемодан).'),

  // ---- 5 fb HARD (idx 35..39) ----
  fb(35, 'HARD', 'Ich ___ im Sommer verreisen.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('werde', 'wirst', 'wird', 'werdet', 'werden'), 'Будущее с werden и ich образует форму werde.'),
  fb(36, 'HARD', '___ Montag fliegen wir nach Wien.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Im', 'Am', 'Um', 'In', 'Auf'), 'С днями недели используется предлог am.'),
  fb(37, 'HARD', 'Vergiss deinen Pass nicht an ___ Grenze!', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('die', 'den', 'der', 'das', 'dem'), 'Предлог an с Dativ перед существительным женского рода даёт der Grenze.'),
  fb(38, 'HARD', 'Im August ___ wir nach Rom.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('fliege', 'fliegt', 'fliegst', 'fliegen', 'flog'), 'С подлежащим wir глагол fliegen имеет окончание -en.'),
  fb(39, 'HARD', 'Wir haben ein Doppelzimmer ___ Hotel gebucht.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('am', 'um', 'an', 'auf', 'im'), 'Сочетание in + dem перед Hotel даёт слитную форму im.'),
];
