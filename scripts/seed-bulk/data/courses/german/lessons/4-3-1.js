'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Mir tut der ___ weh. (голова)', op4('Kopf', 'Bein', 'Hand', 'Bauch'), 'a',
    'Der Kopf — голова, мужского рода, поэтому артикль der.'),
  sc(1, 'EASY', 'Какой артикль у слова Hand?', op4('der', 'das', 'die', 'den'), 'c',
    'Die Hand — кисть руки, женского рода, артикль die.'),
  sc(2, 'EASY', 'Ich ___ Kopfschmerzen.', op4('habe', 'bin', 'tut', 'gehe'), 'a',
    'Для жалоб на боли используется глагол haben: ich habe Kopfschmerzen.'),
  sc(3, 'EASY', 'Mir tut der Rücken ___.', op4('gut', 'weh', 'gern', 'sehr'), 'b',
    'Конструкция wehtun требует слова weh в конце: mir tut der Rücken weh.'),
  sc(4, 'EASY', 'Какое слово означает «зуб»?', op4('der Hals', 'der Zahn', 'der Arm', 'der Bauch'), 'b',
    'Der Zahn — зуб, мужского рода.'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', '___ tut der Kopf weh. (у неё болит)', op4('Sie', 'Ihr', 'Ihre', 'Ihrem'), 'b',
    'Лицо, испытывающее боль, стоит в дательном падеже: ihr (ей) tut der Kopf weh.'),
  sc(6, 'HARD', 'Mir ___ die Füße weh.', op4('tut', 'tun', 'tue', 'tust'), 'b',
    'При множественном подлежащем die Füße глагол tun стоит во множественном числе.'),
  sc(7, 'HARD', 'Ich habe seit gestern starke ___.', op4('Halsschmerz', 'Halsschmerzen', 'Halsschmerze', 'Halsweh'), 'b',
    'Слово die Schmerzen существует только во множественном числе: Halsschmerzen.'),
  sc(8, 'HARD', 'Mein Rücken tut weh, ___ ich lange sitze. (каждый раз, когда)', op4('als', 'denn', 'wenn', 'dass'), 'c',
    'Для повторяющегося «каждый раз когда» в настоящем нужен wenn; als — только для однократного действия в прошлом, denn требует прямого порядка слов, dass («что») не подходит по смыслу.'),
  sc(9, 'HARD', 'Ich bleibe zu Hause, ___ ich Fieber habe.', op4('weil', 'denn', 'und', 'oder'), 'a',
    'Глагол стоит в конце (ich Fieber habe), поэтому нужен подчинительный союз weil; denn/und/oder требуют обычного порядка слов.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Какие слова обозначают части тела?', op5('der Kopf', 'das Bein', 'das Fieber', 'die Hand', 'der Husten'), ['a', 'b', 'd'],
    'Kopf, Bein и Hand — части тела; Fieber и Husten — это симптомы.'),
  mc(11, 'EASY', 'У каких существительных артикль der?', op5('Kopf', 'Hals', 'Hand', 'Bauch', 'Bein'), ['a', 'b', 'd'],
    'Der Kopf, der Hals, der Bauch — мужского рода; die Hand и das Bein — нет.'),
  mc(12, 'EASY', 'Какие жалобы строятся с глаголом haben?', op5('Ich habe Husten', 'Ich habe Fieber', 'Mir habe weh', 'Ich habe Schnupfen', 'Mir habe Kopf'), ['a', 'b', 'd'],
    'С haben идут симптомы Husten, Fieber, Schnupfen без артикля.'),
  mc(13, 'EASY', 'Какие предложения про боль построены правильно?', op5('Mir tut der Arm weh.', 'Mein Kopf tut weh.', 'Ich tut weh Hand.', 'Mir tun die Beine weh.', 'Der weh tut Bauch.'), ['a', 'b', 'd'],
    'Правильны модели mir tut ... weh, mein Kopf tut weh и mir tun die Beine weh.'),
  mc(14, 'EASY', 'Какие слова означают симптомы (недомогания)?', op5('der Husten', 'das Fieber', 'der Arm', 'der Schnupfen', 'die Hand'), ['a', 'b', 'd'],
    'Husten (кашель), Fieber (температура) и Schnupfen (насморк) — симптомы.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Какие формы дательного падежа лица верны для wehtun?', op6('mir', 'dir', 'ihm', 'mich', 'uns', 'euch'), ['a', 'b', 'c', 'e', 'f'],
    'Болящее лицо стоит в Dativ: mir, dir, ihm, uns, euch; mich — это Akkusativ.'),
  mc(16, 'HARD', 'Какие сложные слова про боль образованы верно?', op5('Kopfschmerzen', 'Zahnschmerzen', 'Schmerzenkopf', 'Bauchschmerzen', 'Wehbauch'), ['a', 'b', 'd'],
    'Часть тела стоит первой: Kopfschmerzen, Zahnschmerzen, Bauchschmerzen.'),
  mc(17, 'HARD', 'В каких предложениях глагол tun согласован верно?', op5('Mir tun die Füße weh.', 'Mir tut der Zahn weh.', 'Mir tut die Beine weh.', 'Mir tun die Hände weh.', 'Mir tun der Kopf weh.'), ['a', 'b', 'd'],
    'Tun стоит во мн.ч. при die Füße/die Hände и в ед.ч. tut при der Zahn.'),
  mc(18, 'HARD', 'Какие предложения с придаточным построены верно?', op5('Mein Rücken tut weh, wenn ich sitze.', 'Mir tun die Füße weh, weil ich gelaufen bin.', 'Mir tut weh, weil ich bin müde.', 'Ich bleibe zu Hause, weil ich Fieber habe.', 'Ich habe Husten, weil habe ich Schnupfen.'), ['a', 'b', 'd'],
    'В придаточных с wenn/weil глагол стоит в самом конце.'),
  mc(19, 'HARD', 'Какие пары «часть тела + артикль» верны?', op6('der Kopf', 'die Hand', 'das Bein', 'das Hals', 'der Arm', 'die Bauch'), ['a', 'b', 'c', 'e'],
    'Верно: der Kopf, die Hand, das Bein, der Arm; Hals — der, Bauch — der.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «У меня болит голова».', items4('Mir', 'tut', 'der Kopf', 'weh'), 'Порядок: Mir tut der Kopf weh — дательное лицо, глагол, подлежащее, weh.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «У меня кашель».', items4('Ich', 'habe', 'Husten', '.'), 'Утвердительное предложение: подлежащее, глагол habe, затем симптом Husten.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «У меня болит спина».', items4('Mein', 'Rücken', 'tut', 'weh'), 'С притяжательным: Mein Rücken tut weh — подлежащее Mein Rücken, глагол tut, weh.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «У него болит зуб».', items4('Ihm', 'tut', 'der Zahn', 'weh'), 'Ему — ihm в дательном падеже: Ihm tut der Zahn weh.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «У меня температура».', items4('Ich', 'habe', 'Fieber', '.'), 'Симптом Fieber идёт с глаголом haben без артикля.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «У меня болят ноги».', items4('Mir', 'tun', 'die Füße', 'weh'), 'При мн.ч. die Füße глагол tun: Mir tun die Füße weh.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «У меня со вчерашнего дня болит горло».', items5('Ich', 'habe', 'seit gestern', 'Halsschmerzen', '.'), 'Обстоятельство времени seit gestern стоит после глагола: Ich habe seit gestern Halsschmerzen.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «У меня болит спина, когда я долго сижу».', items5('Mein Rücken tut weh,', 'wenn', 'ich', 'lange', 'sitze'), 'В придаточном с wenn глагол sitze стоит в самом конце.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «У тебя тоже температура и кашель?».', items5('Hast', 'du', 'auch', 'Fieber', 'und Husten?'), 'В вопросе глагол hast на первом месте: Hast du auch Fieber und Husten?'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «У меня болят ноги, потому что я весь день ходил».', items5('Mir tun die Füße weh,', 'weil ich', 'den ganzen Tag', 'gelaufen', 'bin'), 'В придаточном с weil спрягаемый глагол bin стоит в конце: …, weil ich den ganzen Tag gelaufen bin.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Mir tut ___ Kopf weh.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('die', 'das', 'der', 'den', 'dem'),
    'Der Kopf — мужского рода в именительном падеже, поэтому der.'),
  fb(31, 'EASY', 'Ich ___ Husten.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('habe', 'bin', 'tut', 'tue', 'gehe'),
    'Симптом Husten идёт с глаголом haben: ich habe.'),
  fb(32, 'EASY', 'Mir tut der Rücken ___.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('gut', 'weh', 'sehr', 'gern', 'viel'),
    'Конструкция wehtun завершается словом weh.'),
  fb(33, 'EASY', '___ tut der Zahn weh. (мне)', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Ich', 'Mein', 'Du', 'Mir', 'Mich'),
    'Болящее лицо стоит в дательном падеже: mir.'),
  fb(34, 'EASY', 'Mir tut ___ Bauch weh.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('die', 'das', 'der', 'den', 'dem'),
    'Der Bauch — мужского рода в именительном падеже, поэтому только der.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Mir ___ die Füße weh.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('tut', 'tun', 'tue', 'tust', 'tat'),
    'При мн.ч. die Füße глагол tun стоит во множественном числе.'),
  fb(36, 'HARD', 'Ich habe seit gestern starke ___.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Halsschmerz', 'Halsweh', 'Halsschmerzen', 'Schmerzhals', 'Hals'),
    'Слово die Schmerzen — только мн.ч.: Halsschmerzen.'),
  fb(37, 'HARD', 'Mir tun die Füße weh, ___ ich lange gelaufen bin.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('weil', 'denn', 'und', 'oder', 'aber'),
    'Глагол в конце (ich lange gelaufen bin) требует подчинительного союза weil; denn/und/oder/aber нуждаются в прямом порядке слов.'),
  fb(38, 'HARD', '___ tut der Hals weh. (ему)', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Er', 'Sein', 'Ihn', 'Ihm', 'Du'),
    'Ему — ihm в дательном падеже для wehtun; Er/Du — именительный, Ihn — винительный, Sein — притяжательный.'),
  fb(39, 'HARD', 'Mir tun die ___ weh.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Kopf', 'Bauch', 'Rücken', 'Zahn', 'Hände'),
    'Подлежащее во мн.ч. требует tun; die Hände — множественное число руки.'),
];
