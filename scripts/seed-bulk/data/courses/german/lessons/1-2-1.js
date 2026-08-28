'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY =====
  sc(0, 'EASY', 'Как сказать «Привет!» неформально?', op4('Hallo!', 'Tschüss!', 'Danke!', 'Bitte!'), 'a', 'Hallo — это неформальное приветствие при встрече.'),
  sc(1, 'EASY', 'Как поздороваться утром?', op4('Guten Abend!', 'Guten Morgen!', 'Gute Nacht!', 'Guten Tag!'), 'b', 'Guten Morgen говорят утром, до полудня.'),
  sc(2, 'EASY', 'Как пожелать «Спокойной ночи!» перед сном?', op4('Guten Tag!', 'Guten Morgen!', 'Gute Nacht!', 'Hallo!'), 'c', 'Gute Nacht желают на ночь, перед сном.'),
  sc(3, 'EASY', 'Как поздороваться днём?', op4('Gute Nacht!', 'Guten Abend!', 'Guten Morgen!', 'Guten Tag!'), 'd', 'Guten Tag — нейтральное приветствие днём, также «здравствуйте».'),
  sc(4, 'EASY', 'Как поздороваться вечером?', op4('Guten Abend!', 'Guten Morgen!', 'Hallo!', 'Gute Nacht!'), 'a', 'Guten Abend говорят вечером, при встрече.'),

  // ===== 5 sc HARD =====
  sc(5, 'HARD', 'Какое приветствие формальное и подходит для незнакомого человека днём?', op4('Hi!', 'Guten Tag!', 'Hallo!', 'Tschüss!'), 'b', 'Guten Tag — формальная фраза, уместная с «вы» и незнакомцами.'),
  sc(6, 'HARD', 'Какое слово стоит в форме «Gute», а не «Guten»?', op4('Guten Morgen', 'Guten Abend', 'Gute Nacht', 'Guten Tag'), 'c', 'Слово Nacht женского рода (die Nacht), поэтому Gute без -n.'),
  sc(7, 'HARD', 'Что говорят при прощании, а не при встрече?', op4('Tschüss!', 'Guten Tag!', 'Guten Morgen!', 'Hallo!'), 'a', 'Tschüss — неформальное «пока» при расставании.'),
  sc(8, 'HARD', 'Как уважительно поздороваться с госпожой Мюллер днём?', op4('Hallo, Müller!', 'Hi, Frau Müller!', 'Tschüss, Frau Müller!', 'Guten Tag, Frau Müller!'), 'd', 'Формула «Guten Tag, Frau …» — вежливое обращение на «вы».'),
  sc(9, 'HARD', 'В каком приветствии существительное обозначает «вечер»?', op4('Guten Morgen!', 'Guten Abend!', 'Gute Nacht!', 'Guten Tag!'), 'b', 'der Abend значит «вечер», отсюда Guten Abend.'),

  // ===== 5 mc EASY =====
  mc(10, 'EASY', 'Какие фразы являются ПРИВЕТСТВИЯМИ (говорят при встрече)?', op5('Hallo!', 'Tschüss!', 'Guten Tag!', 'Danke!', 'Guten Morgen!'), ['a', 'c', 'e'], 'Hallo, Guten Tag и Guten Morgen произносят при встрече.'),
  mc(11, 'EASY', 'Какие приветствия привязаны ко времени суток?', op5('Hallo!', 'Guten Morgen!', 'Guten Abend!', 'Hi!', 'Gute Nacht!'), ['b', 'c', 'e'], 'Guten Morgen, Guten Abend и Gute Nacht зависят от времени суток.'),
  mc(12, 'EASY', 'Какие фразы НЕФОРМАЛЬНЫЕ (для друзей)?', op5('Hallo!', 'Guten Tag!', 'Hi!', 'Guten Abend!', 'Tschüss!'), ['a', 'c', 'e'], 'Hallo, Hi и Tschüss используют в дружеском, неформальном общении.'),
  mc(13, 'EASY', 'В каких фразах есть слово «Guten»?', op5('Guten Morgen!', 'Gute Nacht!', 'Guten Tag!', 'Hallo!', 'Guten Abend!'), ['a', 'c', 'e'], 'Форма Guten стоит перед Morgen, Tag и Abend (мужской род).'),
  mc(14, 'EASY', 'Какие из этих слов — немецкие существительные времени суток?', op5('Morgen', 'Hallo', 'Abend', 'Danke', 'Nacht'), ['a', 'c', 'e'], 'Morgen, Abend и Nacht обозначают время суток.'),

  // ===== 5 mc HARD =====
  mc(15, 'HARD', 'Какие приветствия можно сказать НЕЗНАКОМЦУ на «вы»?', op6('Guten Tag!', 'Hi!', 'Guten Morgen!', 'Hallo!', 'Guten Abend!', 'Tschüss!'), ['a', 'c', 'e'], 'Формальны Guten Tag, Guten Morgen и Guten Abend; Hi и Hallo неформальны.'),
  mc(16, 'HARD', 'В каких словосочетаниях существительное мужского рода (der)?', op6('der Morgen', 'die Nacht', 'der Tag', 'die Frau', 'der Abend', 'die Stadt'), ['a', 'c', 'e'], 'Morgen, Tag и Abend мужского рода — der; Nacht женского — die.'),
  mc(17, 'HARD', 'Какие фразы НЕ являются приветствием при встрече?', op5('Tschüss!', 'Guten Tag!', 'Gute Nacht!', 'Guten Morgen!', 'Danke!'), ['a', 'c', 'e'], 'Tschüss — прощание, Gute Nacht — на ночь, Danke — «спасибо».'),
  mc(18, 'HARD', 'Какие приветствия уместны ВЕЧЕРОМ или НОЧЬЮ?', op5('Guten Morgen!', 'Guten Abend!', 'Guten Tag!', 'Gute Nacht!', 'Hallo!'), ['b', 'd', 'e'], 'Вечером и ночью подходят Guten Abend, Gute Nacht и нейтральное Hallo.'),
  mc(19, 'HARD', 'В каких фразах прилагательное стоит в правильной форме?', op6('Guten Morgen', 'Gute Morgen', 'Gute Nacht', 'Guten Nacht', 'Guten Abend', 'Gute Abend'), ['a', 'c', 'e'], 'Перед der-словами — Guten, перед die Nacht — Gute.'),

  // ===== 5 ord EASY =====
  ord(20, 'EASY', 'Соберите немецкую фразу: «Доброе утро!».', items4('Guten', 'Morgen', '!', '🌅'), 'Правильный порядок приветствия: Guten Morgen!'),
  ord(21, 'EASY', 'Соберите немецкую фразу: «Добрый день!».', items4('Guten', 'Tag', '!', '☀️'), 'Дневное приветствие: Guten Tag!'),
  ord(22, 'EASY', 'Соберите немецкую фразу: «Добрый вечер!».', items4('Guten', 'Abend', '!', '🌆'), 'Вечернее приветствие: Guten Abend!'),
  ord(23, 'EASY', 'Соберите немецкую фразу: «Спокойной ночи!».', items4('Gute', 'Nacht', '!', '🌙'), 'Пожелание на ночь: Gute Nacht!'),
  ord(24, 'EASY', 'Соберите немецкую фразу: «Привет, пока!».', items4('Hallo', ',', 'tschüss', '!'), 'Hallo — привет, tschüss — пока.'),

  // ===== 5 ord HARD =====
  ord(25, 'HARD', 'Соберите немецкую фразу: «Здравствуйте, госпожа Мюллер!».', items5('Guten', 'Tag', ',', 'Frau Müller', '!'), 'Вежливое обращение: Guten Tag, Frau Müller!'),
  ord(26, 'HARD', 'Соберите немецкую фразу: «Доброе утро, госпожа Мюллер!».', items5('Guten', 'Morgen', ',', 'Frau Müller', '!'), 'Утреннее обращение на «вы»: Guten Morgen, Frau Müller!'),
  ord(27, 'HARD', 'Соберите немецкую фразу: «Добрый вечер, госпожа Мюллер!».', items5('Guten', 'Abend', ',', 'Frau Müller', '!'), 'Вечернее обращение на «вы»: Guten Abend, Frau Müller!'),
  ord(28, 'HARD', 'Соберите немецкую фразу: «Привет, спокойной ночи!».', items5('Hallo', ',', 'Gute', 'Nacht', '!'), 'Hallo — привет, Gute Nacht — спокойной ночи (Nacht — существительное, оба слова формулы с заглавной).'),
  ord(29, 'HARD', 'Соберите немецкую фразу: «Добрый день, пока!».', items5('Guten', 'Tag', ',', 'tschüss', '!'), 'Guten Tag — добрый день, tschüss — пока.'),

  // ===== 5 fb EASY =====
  fb(30, 'EASY', 'Guten ___! (доброе утро)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('Morgen', 'Tag', 'Abend', 'Nacht', 'Hallo'), 'Guten Morgen — доброе утро.'),
  fb(31, 'EASY', 'Guten ___! (добрый день)', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Morgen', 'Tag', 'Abend', 'Nacht', 'Hallo'), 'Guten Tag — добрый день.'),
  fb(32, 'EASY', 'Guten ___! (добрый вечер)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Morgen', 'Tag', 'Abend', 'Nacht', 'Hallo'), 'Guten Abend — добрый вечер.'),
  fb(33, 'EASY', 'Gute ___! (спокойной ночи)', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Morgen', 'Tag', 'Abend', 'Nacht', 'Hallo'), 'Gute Nacht — спокойной ночи.'),
  fb(34, 'EASY', '___! (привет, неформально)', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Morgen', 'Tag', 'Abend', 'Nacht', 'Hallo'), 'Hallo — неформальный «привет».'),

  // ===== 5 fb HARD =====
  fb(35, 'HARD', '___ Morgen! (форма прилагательного перед «Morgen»)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('Guten', 'Gute', 'Gut', 'Guter', 'Gutes'), 'Перед der Morgen — Guten Morgen.'),
  fb(36, 'HARD', '___ Nacht! (форма прилагательного перед «Nacht»)', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Guten', 'Gute', 'Gut', 'Guter', 'Gutes'), 'Перед die Nacht — Gute Nacht (без -n).'),
  fb(37, 'HARD', 'Guten Tag, ___ Müller! (вежливое обращение к женщине)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Herr', 'Mann', 'Frau', 'Kind', 'Tag'), 'Frau Müller — обращение «госпожа Мюллер».'),
  fb(38, 'HARD', 'Zum Abschied sagt man: ___! (при прощании говорят)', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Hallo', 'Guten Tag', 'Guten Morgen', 'Tschüss', 'Guten Abend'), 'Tschüss — неформальное прощание «пока».'),
  fb(39, 'HARD', 'Am Abend grüßt man: Guten ___! (вечером приветствуют)', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Morgen', 'Nacht', 'Tag', 'Hallo', 'Abend'), 'Guten Abend — приветствие вечером.'),
];
