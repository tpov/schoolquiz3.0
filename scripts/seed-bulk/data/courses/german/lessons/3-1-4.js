'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ---------- 5 sc EASY (idx 0..4) ----------
  sc(0, 'EASY', 'Welche Präposition braucht den Akkusativ?', op4('für', 'mit', 'bei', 'von'), 'a',
    'Только «für» из этого списка всегда требует Akkusativ, остальные — Dativ.'),
  sc(1, 'EASY', 'Welche Präposition braucht den Dativ?', op4('durch', 'gegen', 'mit', 'ohne'), 'c',
    '«mit» всегда идёт с Dativ, а durch/gegen/ohne — с Akkusativ.'),
  sc(2, 'EASY', 'Das Geschenk ist ___ dich.', op4('für', 'mit', 'bei', 'zu'), 'a',
    '«für» означает «для» и требует Akkusativ, поэтому «für dich».'),
  sc(3, 'EASY', 'Wir fahren ___ dem Bus.', op4('für', 'durch', 'mit', 'ohne'), 'c',
    '«mit» (на, с) требует Dativ, поэтому «mit dem Bus».'),
  sc(4, 'EASY', 'Ich gehe ___ Arzt.', op4('zum', 'für', 'durch', 'ohne'), 'a',
    '«zu + dem» сливается в «zum», и «zum Arzt» значит «к врачу».'),

  // ---------- 5 sc HARD (idx 5..9) ----------
  sc(5, 'HARD', 'Welche Form ist nach «ohne» korrekt?', op4('ohne mir', 'ohne mich', 'ohne ich', 'ohne mein'), 'b',
    '«ohne» требует Akkusativ, поэтому личное местоимение — «mich».'),
  sc(6, 'HARD', 'Wähle die richtige Verschmelzung: «zu + der Schule».', op4('zum Schule', 'zur Schule', 'zus Schule', 'zo Schule'), 'b',
    '«zu + der» (Dativ женского рода) сливается в «zur».'),
  sc(7, 'HARD', 'Sie wohnt ___ ihren Eltern.', op4('für', 'durch', 'bei', 'gegen'), 'c',
    '«bei» (у) требует Dativ, поэтому «bei ihren Eltern».'),
  sc(8, 'HARD', 'Welcher Satz ist grammatisch korrekt?', op4('Ich fahre nach den Berlin.', 'Ich fahre nach Berlin.', 'Ich fahre zu Berlin.', 'Ich fahre für Berlin.'), 'b',
    'С названием города «nach» употребляется без артикля: «nach Berlin».'),
  sc(9, 'HARD', 'Welche Form passt: «durch + das Fenster» als Verschmelzung?', op4('durchs Fenster', 'durchm Fenster', 'durchn Fenster', 'durchr Fenster'), 'a',
    '«durch + das» сливается в «durchs», ведь «durch» требует Akkusativ.'),

  // ---------- 5 mc EASY (idx 10..14) ----------
  mc(10, 'EASY', 'Welche Präpositionen stehen immer mit Akkusativ?', op5('für', 'mit', 'durch', 'bei', 'von'), ['a', 'c'],
    '«für» и «durch» всегда требуют Akkusativ, а mit/bei/von — Dativ.'),
  mc(11, 'EASY', 'Welche Präpositionen stehen immer mit Dativ?', op5('mit', 'für', 'bei', 'durch', 'von'), ['a', 'c', 'e'],
    'mit, bei и von всегда идут с Dativ, тогда как für и durch — с Akkusativ.'),
  mc(12, 'EASY', 'Welche Wörter gehören zur DOGFU-Gruppe (Akkusativ)?', op5('durch', 'gegen', 'mit', 'ohne', 'bei'), ['a', 'b', 'd'],
    'durch, gegen и ohne входят в группу предлогов с Akkusativ (DOGFU).'),
  mc(13, 'EASY', 'Welche Verschmelzungen sind korrekt?', op5('zum', 'zur', 'mitm', 'vom', 'fürr'), ['a', 'b', 'd'],
    'zum, zur и vom — правильные слияния предлога с артиклем в Dativ.'),
  mc(14, 'EASY', 'In welchen Sätzen ist die Präposition richtig benutzt?', op5('für dich', 'mit dir', 'ohne du', 'bei mir', 'gegen ich'), ['a', 'b', 'd'],
    '«für dich», «mit dir» и «bei mir» используют правильные падежные формы.'),

  // ---------- 5 mc HARD (idx 15..19) ----------
  mc(15, 'HARD', 'Welche Sätze sind grammatisch korrekt?', op6('Ich gehe ohne meinen Bruder.', 'Wir fahren mit dem Auto.', 'Er kommt mit der Bus.', 'Das ist für meinen Vater.', 'Sie geht für die Arzt.', 'Ich bin gegen meinen Freund.'), ['a', 'b', 'd', 'f'],
    'Верны варианты с правильным падежом: ohne+Akk., mit+Dat., für+Akk., gegen+Akk.'),
  mc(16, 'HARD', 'Welche Präposition-Pronomen-Verbindungen sind korrekt?', op6('für mich', 'mit mir', 'ohne ihm', 'bei ihr', 'gegen er', 'von dir'), ['a', 'b', 'd', 'f'],
    'für mich (Akk.), mit mir, bei ihr и von dir (Dativ) — все формы верны.'),
  mc(17, 'HARD', 'Welche Verschmelzungen sind sprachlich richtig?', op6('zum', 'beim', 'vom', 'fürs', 'mitm', 'vonm'), ['a', 'b', 'c', 'd'],
    'zum, beim, vom и fürs — стандартные слияния предлога с артиклем.'),
  mc(18, 'HARD', 'In welchen Sätzen passt der Kasus nach der Präposition?', op6('Ich danke dir für das Buch.', 'Wir laufen durch den Park.', 'Sie wohnt bei den Eltern.', 'Er fährt mit den Zug.', 'Das ist ohne der Mann.', 'Ich komme von dem Markt.'), ['a', 'b', 'c', 'f'],
    'Верны: für+Akk., durch+Akk., bei+Dat. и von+Dat.; mit и ohne здесь с ошибкой.'),
  mc(19, 'HARD', 'Welche Aussagen über feste Präpositionen stimmen?', op6('«für» verlangt Akkusativ.', '«mit» verlangt Dativ.', '«ohne» verlangt Dativ.', '«bei» verlangt Dativ.', '«gegen» verlangt Dativ.', '«zu» verlangt Dativ.'), ['a', 'b', 'd', 'f'],
    'für+Akk., mit+Dat., bei+Dat. и zu+Dat. — верные утверждения; ohne и gegen идут с Akkusativ.'),

  // ---------- 5 ord EASY (idx 20..24) ----------
  ord(20, 'EASY', 'Соберите немецкое предложение: «Этот подарок для тебя».', items4('Das', 'Geschenk', 'ist', 'für dich'), 'Порядок: подлежащее, глагол на 2-м месте, затем «für dich» (Akkusativ).'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Мы едем на автобусе».', items4('Wir', 'fahren', 'mit', 'dem Bus'), '«mit dem Bus» в Dativ; глагол «fahren» стоит на втором месте.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Я иду к врачу».', items4('Ich', 'gehe', 'zum', 'Arzt'), '«zum Arzt» — слияние zu+dem (Dativ), означает «к врачу».'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Она живёт у своих родителей».', items5('Sie', 'wohnt', 'bei', 'ihren', 'Eltern'), '«bei ihren Eltern» в Dativ — «bei» всегда требует Dativ.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Я иду без своего брата».', items5('Ich', 'gehe', 'ohne', 'meinen', 'Bruder'), '«ohne meinen Bruder» в Akkusativ — «ohne» всегда требует Akkusativ.'),

  // ---------- 5 ord HARD (idx 25..29) ----------
  ord(25, 'HARD', 'Соберите немецкое предложение: «После работы я иду домой».', items5('Nach der Arbeit', 'gehe', 'ich', 'nach', 'Hause'), 'Обстоятельство времени в начале, поэтому глагол «gehe» стоит на 2-м месте (инверсия).'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Мы бежим через парк».', items5('Wir', 'laufen', 'durch', 'den', 'Park'), '«durch den Park» в Akkusativ; глагол «laufen» на втором месте.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Я благодарю тебя за подарок».', items5('Ich', 'danke', 'dir', 'für das', 'Geschenk'), '«für das Geschenk» в Akkusativ; «danke dir» в Dativ — это управление глагола.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Он приходит с большого рынка».', items5('Er', 'kommt', 'vom', 'großen', 'Markt'), '«vom großen Markt» — слияние von+dem (Dativ) с окончанием прилагательного -en.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Без тебя я не еду».', items5('Ohne dich', 'fahre', 'ich', 'nicht', 'mit'), 'Начало с «ohne dich» (Akk.) даёт инверсию, поэтому «fahre ich»; «mit» — отделяемая приставка глагола mitfahren.'),

  // ---------- 5 fb EASY (idx 30..34) ----------
  fb(30, 'EASY', 'Das Geschenk ist ___ dich.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('für', 'mit', 'bei', 'von', 'zu'),
    '«für» требует Akkusativ, поэтому «für dich».'),
  fb(31, 'EASY', 'Wir fahren ___ dem Bus.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('für', 'mit', 'durch', 'ohne', 'gegen'),
    '«mit» требует Dativ, поэтому «mit dem Bus».'),
  fb(32, 'EASY', 'Ich gehe ___ Arzt.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('für', 'mit', 'zum', 'durch', 'ohne'),
    '«zu + dem» сливается в «zum», «zum Arzt» — «к врачу».'),
  fb(33, 'EASY', 'Sie wohnt ___ ihren Eltern.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('für', 'durch', 'gegen', 'bei', 'ohne'),
    '«bei» требует Dativ и означает «у»: «bei ihren Eltern».'),
  fb(34, 'EASY', 'Ich gehe ohne ___ Bruder.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('mein', 'meinem', 'meines', 'meiner', 'meinen'),
    '«ohne» требует Akkusativ, поэтому «meinen Bruder».'),

  // ---------- 5 fb HARD (idx 35..39) ----------
  fb(35, 'HARD', 'Wir laufen durch ___ Park.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('dem', 'den', 'der', 'des', 'das'),
    '«durch» требует Akkusativ, а «der Park» в Akkusativ — «den Park».'),
  fb(36, 'HARD', 'Ich komme ___ Markt zurück.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('für', 'durch', 'vom', 'fürs', 'ohne'),
    '«von + dem» сливается в «vom» (Dativ): «vom Markt».'),
  fb(37, 'HARD', 'Das Buch ist ein Geschenk für ___ Vater.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('der', 'dem', 'des', 'meinen', 'mein'),
    '«für» требует Akkusativ, поэтому «für meinen Vater».'),
  fb(38, 'HARD', 'Sie fährt ___ Stadt.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('zur', 'zum', 'fürs', 'durchs', 'ums'),
    '«zu + der» сливается в «zur» (Dativ, женский род): «zur Stadt».'),
  fb(39, 'HARD', 'Ich danke dir ___ das Geschenk.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('mit', 'bei', 'von', 'zu', 'für'),
    '«für» требует Akkusativ: «danke dir für das Geschenk».'),
];
