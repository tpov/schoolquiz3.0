'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Какой артикль во множественном числе?', op4('der', 'die', 'das', 'den'), 'b', 'Во множественном числе артикль всегда die.'),
  sc(1, 'EASY', 'Множественное число: ein Tisch — viele ___', op4('Tische', 'Tischen', 'Tischer', 'Tisch'), 'a', 'Стол образует множественное через -e: der Tisch → die Tische.'),
  sc(2, 'EASY', 'Множественное число: eine Lampe — viele ___', op4('Lampe', 'Lamper', 'Lampen', 'Lämpe'), 'c', 'Лампа во множественном получает -n: die Lampe → die Lampen.'),
  sc(3, 'EASY', 'Множественное число: ein Buch — viele ___', op4('Buchs', 'Buche', 'Buchen', 'Bücher'), 'd', 'Книга меняется с умлаутом и -er: das Buch → die Bücher.'),
  sc(4, 'EASY', 'Множественное число: ein Stuhl — viele ___', op4('Stühle', 'Stuhle', 'Stuhlen', 'Stuhls'), 'a', 'Стул берёт умлаут и -e: der Stuhl → die Stühle.'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Множественное число: ein Fenster — viele ___', op4('Fensters', 'Fenster', 'Fenstern', 'Fenstere'), 'b', 'Окно во множественном не меняется: das Fenster → die Fenster.'),
  sc(6, 'HARD', 'Какое предложение верно?', op4('Die Bücher ist rot.', 'Der Bücher sind rot.', 'Die Bücher sind rot.', 'Das Bücher sind rot.'), 'c', 'Во множественном артикль die и глагол sind, цвет без окончания.'),
  sc(7, 'HARD', 'Множественное число: eine Tür — viele ___', op4('Türe', 'Türer', 'Türen', 'Tühren'), 'c', 'Дверь во множественном получает -en: die Tür → die Türen.'),
  sc(8, 'HARD', 'Как сказать «Это два стула»?', op4('Das ist zwei Stühle.', 'Das sind zwei Stühle.', 'Das sind zwei Stühlen.', 'Die sind zwei Stuhl.'), 'b', 'Для множественного «это» нужно das sind и форма Stühle.'),
  sc(9, 'HARD', 'Множественное число: ein Bett — viele ___', op4('Bette', 'Better', 'Bettn', 'Betten'), 'd', 'Кровать во множественном получает -en: das Bett → die Betten.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Какие формы стоят во множественном числе?', op5('die Tische', 'der Tisch', 'die Bücher', 'das Buch', 'die Stühle'), ['a', 'c', 'e'], 'Множественные формы — die Tische, die Bücher, die Stühle.'),
  mc(11, 'EASY', 'Где артикль множественного числа die?', op5('die Lampen', 'das Fenster', 'die Türen', 'der Stuhl', 'die Betten'), ['a', 'c', 'e'], 'Артикль die стоит у die Lampen, die Türen и die Betten.'),
  mc(12, 'EASY', 'Какие слова означают предметы во множественном числе?', op5('die Fenster', 'eine Lampe', 'die Tische', 'ein Buch', 'die Türen'), ['a', 'c', 'e'], 'Множественные предметы — die Fenster, die Tische и die Türen.'),
  mc(13, 'EASY', 'Какие предложения о множественном верны?', op5('Die Lampen sind weiß.', 'Die Lampe sind weiß.', 'Die Bücher sind blau.', 'Der Tische sind grün.', 'Die Stühle sind rot.'), ['a', 'c', 'e'], 'Верны те, где артикль die, глагол sind и цвет без окончания.'),
  mc(14, 'EASY', 'Какие формы — правильное множественное число?', op5('die Stühle', 'die Stuhle', 'die Bücher', 'die Buche', 'die Lampen'), ['a', 'c', 'e'], 'Правильные формы — die Stühle, die Bücher и die Lampen.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Какие пары единственное → множественное верны?', op6('der Tisch → die Tische', 'der Stuhl → die Stuhle', 'das Buch → die Bücher', 'die Lampe → die Lampen', 'die Tür → die Türe', 'das Fenster → die Fenster'), ['a', 'c', 'd', 'f'], 'Верны Tische, Bücher, Lampen и неизменное Fenster.'),
  mc(16, 'HARD', 'Какие предложения во множественном числе грамматически верны?', op6('Das sind die Türen.', 'Das ist die Türen.', 'Die Fenster sind groß.', 'Die Fenster ist groß.', 'Hier sind drei Betten.', 'Hier ist drei Betten.'), ['a', 'c', 'e'], 'Множественное требует sind, а не ist: das sind, die Fenster sind, hier sind.'),
  mc(17, 'HARD', 'В каких словах множественное число с умлаутом?', op6('die Stühle', 'die Tische', 'die Bücher', 'die Lampen', 'die Türen', 'die Betten'), ['a', 'c'], 'Умлаут есть только в Stühle (ü) и Bücher (ü).'),
  mc(18, 'HARD', 'Какие формы множественного числа написаны без ошибок?', op6('die Tische', 'die Türen', 'die Büchern', 'die Lampen', 'die Stühlen', 'die Betten'), ['a', 'b', 'd', 'f'], 'Büchern и Stühlen ошибочны: верно die Bücher и die Stühle.'),
  mc(19, 'HARD', 'Какие предложения с цветом во множественном числе верны?', op6('Die Tische sind braun.', 'Die Tische sind braune.', 'Die Bücher sind blau.', 'Die Bücher sind blaue.', 'Die Lampen sind weiß.', 'Die Lampen sind weiße.'), ['a', 'c', 'e'], 'Цвет-предикатив во множественном числе стоит без окончания.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Это стулья».', items4('Das', 'sind', 'die', 'Stühle'), 'Das sind die Stühle — указываем на множество стульев.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Книги синие».', items4('Die', 'Bücher', 'sind', 'blau'), 'Die Bücher sind blau — артикль die и глагол sind.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Лампы белые».', items4('Die', 'Lampen', 'sind', 'weiß'), 'Die Lampen sind weiß — цвет без окончания.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Это двери».', items4('Das', 'sind', 'die', 'Türen'), 'Das sind die Türen — указание на множество дверей.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Столы зелёные».', items4('Die', 'Tische', 'sind', 'grün'), 'Die Tische sind grün — множественное с глаголом sind.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Здесь три окна».', items4('Hier', 'sind', 'drei', 'Fenster'), 'Hier sind drei Fenster — глагол sind на втором месте, Fenster не меняется.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Это тоже два стула».', items5('Das', 'sind', 'auch', 'zwei', 'Stühle'), 'Das sind auch zwei Stühle — для множественного нужно das sind.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Кровати тоже коричневые».', items5('Die', 'Betten', 'sind', 'auch', 'braun'), 'Die Betten sind auch braun — auch стоит перед цветом.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Здесь тоже четыре лампы».', items5('Hier', 'sind', 'auch', 'vier', 'Lampen'), 'Hier sind auch vier Lampen — sind на втором месте после Hier, auch перед числом.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Двери тоже белые».', items5('Die', 'Türen', 'sind', 'auch', 'weiß'), 'Die Türen sind auch weiß — auch стоит перед цветом-предикативом.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', '___ Bücher sind blau.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('der', 'die', 'das', 'den', 'dem'), 'Во множественном артикль всегда die.'),
  fb(31, 'EASY', 'Die Lampen ___ weiß.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('bin', 'ist', 'sind', 'bist', 'seid'), 'К множественному подлежащему подходит форма sind.'),
  fb(32, 'EASY', 'Das sind die ___.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('Stühle', 'Stuhl', 'Stuhle', 'Stuhlen', 'Stühl'), 'Правильное множественное число — die Stühle.'),
  fb(33, 'EASY', 'Die ___ sind rot.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Tisch', 'Tischen', 'Tischer', 'Tische', 'Tischs'), 'Множественное число от Tisch — die Tische.'),
  fb(34, 'EASY', 'Die Türen ___ grün.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('ist', 'bin', 'bist', 'seid', 'sind'), 'Множественное подлежащее требует глагол sind.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Das Buch ist rot, die ___ sind auch rot.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Buchs', 'Buche', 'Bücher', 'Buchen', 'Büche'), 'Множественное число с умлаутом — die Bücher.'),
  fb(36, 'HARD', 'Hier sind drei ___.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Fensters', 'Fenster', 'Fenstern', 'Fenstere', 'Fensterer'), 'Окно во множественном не меняется: die Fenster.'),
  fb(37, 'HARD', 'Das sind zwei ___.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Betts', 'Bette', 'Bettn', 'Betten', 'Bett'), 'Множественное число от Bett — die Betten.'),
  fb(38, 'HARD', 'Die ___ sind groß und weiß.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('Türen', 'Türe', 'Türer', 'Tür', 'Tühren'), 'Множественное число от Tür — die Türen.'),
  fb(39, 'HARD', '___ sind vier Stühle.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Die', 'Der', 'Den', 'Dem', 'Das'), 'Для указания «это (мн.)» используем das sind.'),
];
