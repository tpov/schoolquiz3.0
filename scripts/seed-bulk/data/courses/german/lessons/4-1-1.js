'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Wähle die richtige Präteritum-Form: «Gestern ___ ich sehr müde.»', op4('war', 'bin', 'bist', 'waren'), 'a', 'Präteritum от sein в 1-м лице ед. ч. — war.'),
  sc(1, 'EASY', 'Wähle die richtige Form: «Als Kind ___ er einen Hund.»', op4('hat', 'habe', 'hatte', 'hatten'), 'c', 'Präteritum от haben в 3-м лице ед. ч. — hatte.'),
  sc(2, 'EASY', 'Wähle die richtige Präteritum-Form: «Wir ___ leider nicht kommen.»', op4('konnte', 'konnten', 'konntet', 'kannten'), 'b', 'Модальный können в Präteritum теряет умляут, для wir — konnten.'),
  sc(3, 'EASY', 'Wähle die richtige Form: «Das Kind ___ krank.»', op4('war', 'waren', 'warst', 'wart'), 'a', 'Präteritum от sein в 3-м лице ед. ч. — war.'),
  sc(4, 'EASY', 'Wähle die richtige Form: «Früher ___ ich Pilot werden.»', op4('will', 'wollte', 'wolltest', 'wollten'), 'b', 'Модальный wollen в Präteritum в 1-м лице ед. ч. — wollte.'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Wähle die richtige Form: «Du ___ gestern arbeiten, oder?»', op4('musste', 'musstest', 'musstet', 'mussten'), 'b', 'Модальный müssen в Präteritum во 2-м лице ед. ч. — du musstest.'),
  sc(6, 'HARD', 'Wähle die richtige Form: «Ihr ___ damals noch sehr jung.»', op4('wart', 'wart ihr', 'waren', 'warst'), 'a', 'Präteritum от sein во 2-м лице мн. ч. — ihr wart.'),
  sc(7, 'HARD', 'Wähle die richtige Form: «Wir ___ als Kinder einen großen Garten.»', op4('hattet', 'hatten', 'hatte', 'hattest'), 'b', 'Präteritum от haben в 1-м лице мн. ч. — wir hatten.'),
  sc(8, 'HARD', 'Wähle die richtige Form: «Die Eltern ___ den Termin leider nicht absagen.»', op4('durftest', 'durftet', 'durfte', 'durften'), 'd', 'Подлежащее die Eltern — 3-е лицо мн. ч., dürfen в Präteritum → durften.'),
  sc(9, 'HARD', 'Wähle die richtige Form: «Ich ___ als Kind keine Pilze.»', op4('mochte', 'mochtest', 'mochten', 'mag'), 'a', 'Модальный mögen в Präteritum в 1-м лице ед. ч. — mochte.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Formen sind korrektes Präteritum von «sein»?', op5('war', 'bin', 'waren', 'sind', 'warst'), ['a', 'c', 'e'], 'Präteritum от sein: war, warst, war, waren, wart, waren.'),
  mc(11, 'EASY', 'Welche Sätze stehen korrekt im Präteritum?', op5('Gestern war ich krank.', 'Gestern bin ich krank.', 'Er hatte Fieber.', 'Er hat Fieber gestern.', 'Wir konnten kommen.'), ['a', 'c', 'e'], 'Präteritum образуется одним глаголом: war, hatte, konnten.'),
  mc(12, 'EASY', 'Welche Formen sind korrektes Präteritum von «haben»?', op5('hatte', 'habe', 'hatten', 'hast', 'hattest'), ['a', 'c', 'e'], 'Präteritum от haben: hatte, hattest, hatte, hatten, hattet, hatten.'),
  mc(13, 'EASY', 'Welche Präteritum-Formen von Modalverben verlieren den Umlaut korrekt?', op5('konnte', 'könnte', 'musste', 'müsste', 'durfte'), ['a', 'c', 'e'], 'В Präteritum модальные теряют умлаут: konnte, musste, durfte.'),
  mc(14, 'EASY', 'Welche Wörter passen zum Thema «früher/Vergangenheit»?', op5('gestern', 'morgen', 'früher', 'übermorgen', 'damals'), ['a', 'c', 'e'], 'О прошлом говорят: gestern, früher, damals.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Welche Sätze sind grammatisch korrekt im Präteritum?', op6('Wir waren müde.', 'Wir warst müde.', 'Ihr hattet Zeit.', 'Ihr hatten Zeit.', 'Sie mussten gehen.', 'Sie musste gehen, sie alle.'), ['a', 'c', 'e'], 'Корректны: wir waren, ihr hattet, sie mussten.'),
  mc(16, 'HARD', 'In welchen Sätzen ist die Modalverb-Form im Präteritum richtig?', op6('Ich wollte schlafen.', 'Ich willte schlafen.', 'Du solltest warten.', 'Du sollst gestern warten.', 'Er mochte sie.', 'Er mögte sie.'), ['a', 'c', 'e'], 'Корректные формы Präteritum: wollte, solltest, mochte.'),
  mc(17, 'HARD', 'Welche Endungen passen zu «du» im Präteritum?', op6('du warst', 'du war', 'du hattest', 'du hatte', 'du konntest', 'du konnte'), ['a', 'c', 'e'], 'Для du в Präteritum окончание -st: warst, hattest, konntest.'),
  mc(18, 'HARD', 'Welche Sätze verwenden korrekt das Präteritum von sein/haben/Modalverben?', op6('Sie waren glücklich.', 'Sie waret glücklich.', 'Wir hatten Glück.', 'Wir hattem Glück.', 'Ich durfte gehen.', 'Ich durftete gehen.'), ['a', 'c', 'e'], 'Корректно: sie waren, wir hatten, ich durfte.'),
  mc(19, 'HARD', 'Welche Formen sind korrektes Präteritum (1./3. Person Singular ohne Endung)?', op6('ich war', 'ich ware', 'er konnte', 'er konntet', 'sie hatte', 'sie hattte'), ['a', 'c', 'e'], 'В 1-м и 3-м лице ед. ч. Präteritum без окончания: war, konnte, hatte.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Вчера я был усталым».', items4('Gestern', 'war', 'ich', 'müde'), 'В главном предложении глагол war стоит на втором месте (V2).'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Ребёнок был болен».', items4('Das', 'Kind', 'war', 'krank'), 'Подлежащее das Kind, затем глагол war на втором месте.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «У меня была собака».', items4('Ich', 'hatte', 'einen', 'Hund'), 'Präteritum hatte на втором месте, Akkusativ einen Hund.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Мы, к сожалению, не смогли прийти».', items5('Wir', 'konnten', 'leider', 'nicht', 'kommen'), 'Модальный konnten на втором месте, инфинитив kommen в конце.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Раньше я хотел стать пилотом».', items5('Früher', 'wollte', 'ich', 'Pilot', 'werden'), 'wollte на втором месте, инфинитив werden в конце рамки.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Тебе вчера, к сожалению, пришлось работать».', items5('Du', 'musstest', 'gestern', 'leider', 'arbeiten'), 'musstest на втором месте, наречие leider в середине, инфинитив arbeiten в конце.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «В детстве у меня была собака».', items5('Als', 'Kind', 'hatte', 'ich', 'einen Hund'), 'Обстоятельство als Kind на первом месте, hatte на втором (V2).'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Ребёнок был болен и у него была температура».', items5('Das Kind', 'war', 'krank', 'und', 'hatte Fieber'), 'Два главных предложения соединены союзом und.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Я был усталым, потому что я работал».', items5('Ich', 'war', 'müde,', 'weil', 'ich arbeitete'), 'После weil идёт придаточное, где глагол стоит в конце.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Тогда мы не могли остаться».', items5('Damals', 'konnten', 'wir', 'nicht', 'bleiben'), 'Damals на первом месте, konnten на втором, bleiben в конце.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Gestern ___ ich sehr müde.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('war', 'bin', 'hatte', 'waren', 'bist'), 'Präteritum от sein в 1-м лице ед. ч. — war.'),
  fb(31, 'EASY', 'Als Kind ___ er einen Hund.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('habe', 'hatte', 'hat', 'hatten', 'hattest'), 'Präteritum от haben в 3-м лице ед. ч. — hatte.'),
  fb(32, 'EASY', 'Wir ___ leider nicht kommen.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('konntest', 'konntet', 'konnten', 'kannten', 'konnte'), 'Модальный können в Präteritum для wir — konnten.'),
  fb(33, 'EASY', 'Du ___ gestern arbeiten, oder?', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('musste', 'mussten', 'musstet', 'musstest', 'musst'), 'Модальный müssen во 2-м лице ед. ч. — musstest.'),
  fb(34, 'EASY', 'Früher ___ ich Pilot werden.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('will', 'wolltest', 'wollten', 'wolltet', 'wollte'), 'Модальный wollen в 1-м лице ед. ч. — wollte.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Ihr ___ damals noch sehr jung.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('waren', 'wart', 'warst', 'war', 'wars'), 'Präteritum от sein во 2-м лице мн. ч. — ihr wart.'),
  fb(36, 'HARD', 'Wir ___ als Kinder einen großen Garten.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('hatte', 'hattet', 'hatten', 'hattest', 'haben'), 'Präteritum от haben в 1-м лице мн. ч. — hatten.'),
  fb(37, 'HARD', 'Die Kinder ___ den Termin leider nicht absagen.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('durfte', 'durftest', 'durftet', 'durften', 'durftete'), 'Подлежащее die Kinder — 3-е лицо мн. ч., dürfen в Präteritum → durften.'),
  fb(38, 'HARD', 'Ich ___ als Kind keine Pilze.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('mochte', 'mochtest', 'mochten', 'mag', 'mochtet'), 'Модальный mögen в Präteritum в 1-м лице ед. ч. — mochte.'),
  fb(39, 'HARD', 'Das Kind war krank und ___ Fieber.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('habe', 'haben', 'hattest', 'hattet', 'hatte'), 'Präteritum от haben в 3-м лице ед. ч. — hatte.'),
];
