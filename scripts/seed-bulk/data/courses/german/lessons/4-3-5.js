'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Ich hatte gestern einen ___. (несчастный случай)', op4('Unfall', 'Gips', 'Verband', 'Notarzt'), 'a',
    'Der Unfall — несчастный случай, в винительном падеже после hatte стоит einen Unfall.'),
  sc(1, 'EASY', 'Der Krankenwagen hat ihn ins ___ gebracht.', op4('Operation', 'Krankenhaus', 'Wunde', 'Genesung'), 'b',
    'Das Krankenhaus — больница, среднего рода, после ins (in das) остаётся Krankenhaus.'),
  sc(2, 'EASY', 'Ich bin auf dem Eis ___. (упал)', op4('hingefallen', 'gefahren', 'gegangen', 'gekommen'), 'a',
    'Глагол hinfallen в перфекте даёт причастие hingefallen — «упал».'),
  sc(3, 'EASY', 'Ich ___ mir den Arm gebrochen.', op4('bin', 'habe', 'war', 'wurde'), 'b',
    'Перфект brechen строится с haben: ich habe mir den Arm gebrochen.'),
  sc(4, 'EASY', 'Nach dem Bruch trägt sie einen ___ am Bein. (гипс)', op4('Krankenwagen', 'Gips', 'Notarzt', 'Unfall'), 'b',
    'Der Gips — гипс, мужского рода, в винительном падеже einen Gips; остальные слова по смыслу на ноге не носят.'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Ich ___ gefallen und habe mir das Bein verletzt.', op4('habe', 'bin', 'war', 'hatte'), 'b',
    'Глагол fallen описывает изменение положения, поэтому перфект с sein: ich bin gefallen.'),
  sc(6, 'HARD', 'Ich habe mir ___ Hand verletzt. (себе руку)', op4('der', 'dem', 'die', 'den'), 'c',
    'Часть тела стоит в Akkusativ, die Hand женского рода — артикль die.'),
  sc(7, 'HARD', 'Heute geht es ___ schon viel besser als gestern.', op4('mich', 'ich', 'mir', 'mein'), 'c',
    'Безличное es geht требует лица в Dativ: es geht mir besser.'),
  sc(8, 'HARD', 'Mir geht es heute ___ als gestern. (хуже)', op4('gut', 'besser', 'schlechter', 'schlecht'), 'c',
    'Сравнительная степень schlecht — schlechter, «хуже».'),
  sc(9, 'HARD', 'Der Notarzt kam, ___ ich gestürzt war.', op4('weil', 'damit', 'obwohl', 'sondern'), 'a',
    'Weil вводит причину «потому что», глагол war стоит в конце придаточного.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Какие слова связаны с больницей и помощью?', op5('das Krankenhaus', 'der Notarzt', 'das Eis', 'der Krankenwagen', 'der Sommer'), ['a', 'b', 'd'],
    'Krankenhaus, Notarzt и Krankenwagen относятся к медицинской помощи.'),
  mc(11, 'EASY', 'Какие причастия перфекта образованы верно?', op5('gefallen', 'hingefallen', 'gebrechen', 'gebrochen', 'fallt'), ['a', 'b', 'd'],
    'Верные формы: gefallen, hingefallen и gebrochen.'),
  mc(12, 'EASY', 'Какие глаголы образуют перфект с sein?', op5('fallen', 'hinfallen', 'brechen', 'stürzen', 'verletzen'), ['a', 'b', 'd'],
    'С sein идут глаголы движения и изменения состояния: fallen, hinfallen, stürzen.'),
  mc(13, 'EASY', 'Какие предложения о самочувствии построены верно?', op5('Mir geht es gut.', 'Mir geht es besser.', 'Ich geht es gut.', 'Mir geht es schlecht.', 'Es geht mich gut.'), ['a', 'b', 'd'],
    'Безличное es geht требует Dativ: mir geht es gut/besser/schlecht.'),
  mc(14, 'EASY', 'Какие слова означают повреждение или лечение?', op5('die Wunde', 'der Verband', 'der Sommer', 'die Operation', 'die Schule'), ['a', 'b', 'd'],
    'Wunde (рана), Verband (повязка) и Operation (операция) относятся к лечению.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'В каких предложениях перфект образован с правильным вспомогательным глаголом?', op5('Ich bin gefallen.', 'Ich habe mir den Arm gebrochen.', 'Ich habe gefallen.', 'Er ist gestürzt.', 'Sie hat sich gestürzt.'), ['a', 'b', 'd'],
    'Fallen и stürzen идут с sein, brechen — с haben.'),
  mc(16, 'HARD', 'В каких фразах Dativ лица и Akkusativ части тела стоят верно?', op6('Ich habe mir den Arm gebrochen.', 'Ich habe mir die Hand verletzt.', 'Ich habe mich den Arm gebrochen.', 'Sie hat sich das Bein gebrochen.', 'Er hat ihm der Fuß verletzt.', 'Wir haben uns die Hände verletzt.'), ['a', 'b', 'd', 'f'],
    'Лицо стоит в Dativ (mir, sich, uns), часть тела — в Akkusativ (den Arm, die Hand, das Bein, die Hände).'),
  mc(17, 'HARD', 'Какие сравнительные формы образованы верно?', op5('gut – besser', 'schlecht – schlechter', 'schlimm – schlimmer', 'gut – guter', 'schlecht – schlimmer'), ['a', 'b', 'c'],
    'Верно: gut – besser, schlecht – schlechter, schlimm – schlimmer.'),
  mc(18, 'HARD', 'Какие предложения с порядком слов в рассказе построены верно?', op5('Zuerst bin ich gefallen.', 'Dann hat mich der Notarzt geholt.', 'Deshalb bin ich ins Krankenhaus gekommen.', 'Zuerst ich bin gefallen.', 'Dann der Notarzt hat mich geholt.'), ['a', 'b', 'c'],
    'После zuerst, dann, deshalb сразу идёт спрягаемый глагол (правило V2).'),
  mc(19, 'HARD', 'Какие пары «существ. темы + артикль» верны?', op6('der Unfall', 'das Krankenhaus', 'die Verletzung', 'das Gips', 'der Verband', 'die Genesung'), ['a', 'b', 'c', 'e', 'f'],
    'Верно: der Unfall, das Krankenhaus, die Verletzung, der Verband, die Genesung; Gips — der.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Я упал».', items4('Ich', 'bin', 'hingefallen', '.'), 'Перфект hinfallen идёт с sein: Ich bin hingefallen.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Я сломал себе руку».', items5('Ich', 'habe', 'mir', 'den Arm', 'gebrochen'), 'Перфект с haben, лицо mir в Dativ, рука den Arm в Akkusativ: Ich habe mir den Arm gebrochen.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Скорая отвезла его в больницу».', items5('Der Krankenwagen', 'hat', 'ihn', 'ins Krankenhaus', 'gebracht'), 'Перфект с haben: Der Krankenwagen hat ihn ins Krankenhaus gebracht.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Сегодня мне лучше».', items4('Heute', 'geht', 'es mir', 'besser'), 'Обстоятельство времени heute на первом месте, глагол geht вторым: Heute geht es mir besser.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Она носит гипс на ноге».', items5('Sie', 'trägt', 'einen Gips', 'am', 'Bein'), 'Глагол tragen на втором месте: Sie trägt einen Gips am Bein.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Я упал на льду и сломал себе руку».', items5('Ich bin auf dem Eis hingefallen', 'und', 'habe', 'mir den Arm', 'gebrochen'), 'Два перфекта: bin hingefallen (с sein) и habe gebrochen (с haben).'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Сначала я упал, потом приехала скорая».', items5('Zuerst', 'bin ich gefallen,', 'dann', 'kam', 'der Krankenwagen'), 'После zuerst и dann сразу идёт глагол (V2): Zuerst bin ich gefallen, dann kam der Krankenwagen.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «После операции она теперь носит гипс».', items5('Nach der Operation', 'trägt', 'sie', 'jetzt', 'einen Gips'), 'Обстоятельство Nach der Operation на первом месте, глагол trägt вторым: Nach der Operation trägt sie jetzt einen Gips.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Сегодня мне намного лучше, чем вчера».', items5('Mir geht es heute', 'viel', 'besser', 'als', 'gestern'), 'Сравнение с als: Mir geht es heute viel besser als gestern.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Врач скорой пришёл, потому что я упал».', items5('Der Notarzt kam,', 'weil', 'ich', 'gestürzt', 'bin'), 'В придаточном с weil спрягаемый глагол bin стоит в самом конце.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Ich ___ mir den Arm gebrochen.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('habe', 'bin', 'war', 'wurde', 'hatte'),
    'Перфект brechen образуется с haben: ich habe gebrochen.'),
  fb(31, 'EASY', 'Ich ___ auf dem Eis hingefallen.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('habe', 'bin', 'war', 'hatte', 'wurde'),
    'Перфект hinfallen образуется с sein: ich bin hingefallen.'),
  fb(32, 'EASY', 'Der Krankenwagen hat ihn ins ___ gebracht.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Wunde', 'Operation', 'Krankenhaus', 'Genesung', 'Verband'),
    'Das Krankenhaus — больница, куда отвезла скорая.'),
  fb(33, 'EASY', 'Heute geht es ___ schon besser. (мне)', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('ich', 'mein', 'mich', 'mir', 'meiner'),
    'Безличное es geht требует Dativ: mir.'),
  fb(34, 'EASY', 'Sie trägt einen ___ am Bein. (гипс)', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Krankenwagen', 'Unfall', 'Wunde', 'Notarzt', 'Gips'),
    'Der Gips — гипс, который носят при переломе; остальные слова по смыслу на ноге не носят.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Ich habe mir ___ Hand verletzt.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('die', 'der', 'dem', 'den', 'das'),
    'Часть тела в Akkusativ, die Hand женского рода — артикль die.'),
  fb(36, 'HARD', 'Mir geht es heute ___ als gestern. (хуже)', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('gut', 'schlechter', 'besser', 'gern', 'viel'),
    'Сравнительная форма schlecht — schlechter, «хуже».'),
  fb(37, 'HARD', 'Der Notarzt kam, ___ ich gestürzt war.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('damit', 'obwohl', 'weil', 'sondern', 'denn'),
    'Weil вводит причину, глагол стоит в конце придаточного.'),
  fb(38, 'HARD', 'Ich ___ gefallen und habe mir das Bein gebrochen.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('habe', 'war', 'hatte', 'bin', 'wurde'),
    'Глагол fallen идёт в перфекте с sein: ich bin gefallen.'),
  fb(39, 'HARD', 'Er hat sich gut erholt und wünscht sich eine schnelle ___.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Operation', 'Wunde', 'Verletzung', 'Verband', 'Genesung'),
    'Die Genesung — выздоровление, которого себе желают после болезни.'),
];
