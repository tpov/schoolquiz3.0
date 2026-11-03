'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Entschuldigung, wo ist die nächste ___?', op4('Haltestelle', 'Gehen', 'Weit', 'Links'), 'a', 'Существительное «остановка» — die Haltestelle, остальное не существительные.'),
  sc(1, 'EASY', 'Wie sagt man «прямо» auf Deutsch?', op4('rechts', 'links', 'geradeaus', 'weit'), 'c', '«Прямо» по-немецки — geradeaus.'),
  sc(2, 'EASY', 'Welches Wort bedeutet «светофор»?', op4('die Brücke', 'die Ampel', 'die Ecke', 'der Weg'), 'b', '«Светофор» по-немецки — die Ampel.'),
  sc(3, 'EASY', 'Gehen Sie ___ und biegen Sie dann links ab.', op4('geradeaus', 'die Ampel', 'rechts ist', 'der Weg'), 'a', 'Здесь нужно наречие geradeaus («прямо»).'),
  sc(4, 'EASY', 'Was ist das Gegenteil von «links»?', op4('weit', 'nah', 'rechts', 'geradeaus'), 'c', 'Противоположность links («налево») — rechts («направо»).'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Wählen Sie die höfliche Frage: ___ Sie mir sagen, wo der Bahnhof ist?', op4('Kann', 'Könnt', 'Können', 'Konnte'), 'c', 'К вежливому Sie глагол können спрягается как können: Können Sie ...?'),
  sc(6, 'HARD', 'Biegen Sie ___ Ampel rechts ab.', op4('an die', 'an der', 'am', 'auf der'), 'b', 'После an в значении места — Dativ: an der Ampel (die Ampel → der).'),
  sc(7, 'HARD', 'Gehen Sie bis ___ Kreuzung und dann geradeaus.', op4('zur', 'zum', 'zum der', 'zu die'), 'a', 'Конструкция *bis zu* требует датива, а *zu* + *der* сливаются в *zur*: *die Kreuzung* → *bis zur Kreuzung*.'),
  sc(8, 'HARD', 'Nehmen Sie die ___ Straße links.', op4('zwei', 'zweiten', 'zweite', 'zweier'), 'c', 'Порядковое числительное после die в Akkusativ ж.р. — die zweite Straße.'),
  sc(9, 'HARD', 'Welcher Imperativ (Sie-Form) ist korrekt?', op4('Geht geradeaus!', 'Gehen Sie geradeaus!', 'Du gehst geradeaus!', 'Gehe geradeaus!'), 'b', 'Вежливый императив образуется как Gehen Sie ...!'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Wörter zeigen eine Richtung?', op5('links', 'rechts', 'die Ampel', 'geradeaus', 'die Brücke'), ['a', 'b', 'd'], 'Направления — links, rechts и geradeaus; остальное — существительные.'),
  mc(11, 'EASY', 'Welche Wörter sind Substantive (mit Artikel)?', op5('die Kreuzung', 'gehen', 'die Haltestelle', 'nah', 'der Weg'), ['a', 'c', 'e'], 'Существительные с артиклем — die Kreuzung, die Haltestelle, der Weg.'),
  mc(12, 'EASY', 'Welche Sätze beginnen höflich nach dem Weg zu fragen?', op5('Entschuldigung, wo ist der Park?', 'Geh weg!', 'Entschuldigung, wie komme ich zum Bahnhof?', 'Halt den Mund!', 'Wo ist bitte die Apotheke?'), ['a', 'c', 'e'], 'Вежливые вопросы о дороге начинаются с Entschuldigung или содержат bitte.'),
  mc(13, 'EASY', 'Welche Wörter bedeuten Orte in der Stadt?', op6('die Brücke', 'die Haltestelle', 'links', 'die Kreuzung', 'geradeaus', 'weit'), ['a', 'b', 'd'], 'Места в городе — die Brücke, die Haltestelle, die Kreuzung.'),
  mc(14, 'EASY', 'Welche Verben passen zu Wegbeschreibungen?', op5('gehen', 'schlafen', 'abbiegen', 'überqueren', 'kochen'), ['a', 'c', 'd'], 'Глаголы для описания пути — gehen, abbiegen, überqueren.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Welche Sätze sind grammatisch korrekt?', op5('Gehen Sie geradeaus.', 'Biegen Sie links ab.', 'Sie gehen geradeaus ab biegen.', 'Überqueren Sie die Straße.', 'Sie geradeaus gehen.'), ['a', 'b', 'd'], 'В вежливом императиве глагол стоит первым, отделяемая приставка — в конце.'),
  mc(16, 'HARD', 'In welchen Sätzen steht der Dativ korrekt nach der Präposition?', op5('an der Ampel', 'bis zur Brücke', 'an die Kreuzung vorbei', 'am Park vorbei', 'bis zu der Bahnhof'), ['a', 'b', 'd'], 'an + Dativ и bis zu + Dativ: an der Ampel, bis zur Brücke, am Park vorbei.'),
  mc(17, 'HARD', 'Welche Ordinalzahlen sind in «die ___ Straße» korrekt?', op6('erste', 'zweite', 'zwei', 'dritte', 'drei', 'erstens'), ['a', 'b', 'd'], 'Порядковые после die — erste, zweite, dritte (с окончанием -e).'),
  mc(18, 'HARD', 'Welche höflichen Bitten sind korrekt formuliert?', op5('Können Sie mir helfen?', 'Können Sie mir sagen, wo das Rathaus ist?', 'Du sagst mir den Weg!', 'Entschuldigung, wo ist die Bank?', 'Sag mir sofort!'), ['a', 'b', 'd'], 'Вежливые формы используют Sie, Können Sie ...? или Entschuldigung.'),
  mc(19, 'HARD', 'Welche Sätze haben die richtige Wortstellung mit «weil»?', op5('Ich nehme den Bus, weil es weit ist.', 'Ich gehe zu Fuß, weil es nah ist.', 'Ich nehme den Bus, weil ist es weit.', 'Wir warten hier, weil die Ampel rot ist.', 'Wir gehen, weil die Brücke ist gesperrt.'), ['a', 'b', 'd'], 'После weil глагол стоит в конце придаточного предложения.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Идите прямо».', items4('Gehen', 'Sie', 'bitte', 'geradeaus'), 'Вежливый императив: Gehen Sie bitte geradeaus.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Где остановка?».', items4('Wo', 'ist', 'die', 'Haltestelle'), 'Вопрос о месте: Wo ist die Haltestelle?'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Поверните направо».', items4('Biegen', 'Sie', 'rechts', 'ab'), 'Отделяемая приставка ab уходит в конец: Biegen Sie rechts ab.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Извините, где банк?».', items5('Entschuldigung,', 'wo', 'ist', 'die', 'Bank?'), 'Вежливый вопрос: Entschuldigung, wo ist die Bank?'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Перейдите улицу».', items4('Überqueren', 'Sie', 'die', 'Straße'), 'Императив с überqueren: Überqueren Sie die Straße.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Поверните на светофоре налево».', items5('Biegen', 'Sie', 'an der Ampel', 'links', 'ab'), 'an der Ampel — Dativ, приставка ab в конце: Biegen Sie an der Ampel links ab.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Сверните на вторую улицу направо».', items5('Nehmen', 'Sie', 'die zweite', 'Straße', 'rechts'), 'Порядковое die zweite Straße: Nehmen Sie die zweite Straße rechts.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Можете сказать мне, где вокзал?».', items5('Können', 'Sie', 'mir sagen,', 'wo der Bahnhof', 'ist?'), 'В придаточном с wo глагол ist в конце: Können Sie mir sagen, wo der Bahnhof ist?'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Идите прямо до моста».', items5('Gehen', 'Sie', 'geradeaus', 'bis zur', 'Brücke'), 'Конструкция *bis zu* требует датива, *zu der* сливается в *zur*: *bis zur Brücke* — *Gehen Sie geradeaus bis zur Brücke.*'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Идите мимо парка, затем налево».', items5('Gehen', 'Sie', 'am Park vorbei,', 'dann', 'links'), 'an ... vorbei с Dativ: Gehen Sie am Park vorbei, dann links.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Entschuldigung, wo ist ___ Haltestelle?', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('die', 'der', 'das', 'den', 'dem'), 'die Haltestelle женского рода — артикль die.'),
  fb(31, 'EASY', 'Überqueren Sie ___ Straße und gehen Sie dann geradeaus.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('der', 'die', 'das', 'dem', 'den'), 'die Straße — Akkusativ женского рода после überqueren: артикль die.'),
  fb(32, 'EASY', 'Die Bank ist gleich um ___ Ecke.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('der', 'dem', 'die', 'den', 'das'), 'um + Akkusativ, die Ecke женского рода → um die Ecke («за углом»).'),
  fb(33, 'EASY', '___ Sie geradeaus, bitte!', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Geht', 'Gehe', 'Du gehst', 'Gehen', 'Ging'), 'Вежливый императив: Gehen Sie ...!'),
  fb(34, 'EASY', 'Wo ist ___ Bahnhof?', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('die', 'das', 'den', 'dem', 'der'), 'der Bahnhof мужского рода в Nominativ — артикль der.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Biegen Sie an ___ Ampel links ab.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('der', 'die', 'das', 'den', 'dem'), 'Предлог *an* в значении места требует датива: *die Ampel* → *an der Ampel*.'),
  fb(36, 'HARD', 'Gehen Sie bis ___ Kreuzung geradeaus.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('zum', 'zur', 'zu', 'zen', 'der'), 'Конструкция *bis zu* требует датива, *zu der* сливается в *zur*: *die Kreuzung* → *bis zur Kreuzung*.'),
  fb(37, 'HARD', 'Nehmen Sie die ___ Straße rechts.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('zwei', 'zweier', 'zweite', 'zweiten', 'zweimal'), 'Порядковое в Akkusativ ж.р. после die — die zweite Straße.'),
  fb(38, 'HARD', '___ Sie mir sagen, wo das Rathaus ist?', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Kann', 'Könnt', 'Konnte', 'Können', 'Könnte'), 'Вежливая форма к Sie — Können Sie ...?'),
  fb(39, 'HARD', 'Gehen Sie ___ Park vorbei, dann sehen Sie die Brücke.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('an die', 'an der', 'zum', 'auf den', 'am'), 'Оборот *an ... vorbei* («мимо») требует датива, а *an dem* сливается в *am*: *an dem Park* = *am Park vorbei*.'),
];
