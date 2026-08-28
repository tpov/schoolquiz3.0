'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Что значит «rot»?', op4('красный', 'синий', 'зелёный', 'жёлтый'), 'a', 'rot переводится как «красный».'),
  sc(1, 'EASY', 'Что значит «blau»?', op4('чёрный', 'синий', 'белый', 'жёлтый'), 'b', 'blau переводится как «синий, голубой».'),
  sc(2, 'EASY', 'Как сказать «зелёный» по-немецки?', op4('rot', 'gelb', 'grün', 'weiß'), 'c', 'grün значит «зелёный».'),
  sc(3, 'EASY', 'Как сказать «жёлтый» по-немецки?', op4('schwarz', 'blau', 'grün', 'gelb'), 'd', 'gelb значит «жёлтый».'),
  sc(4, 'EASY', 'Что значит слово «die Farbe»?', op4('цвет', 'книга', 'дом', 'стол'), 'a', 'die Farbe переводится как «цвет».'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Как правильно сказать «Книга красная»?', op4('Das Buch ist rot.', 'Das Buch ist rote.', 'Das Buch rot ist.', 'Das rote Buch ist.'), 'a', 'В роли сказуемого цвет не склоняется: Das Buch ist rot.'),
  sc(6, 'HARD', 'Какой вопрос значит «Какой это цвет?»', op4('Wer ist das?', 'Welche Farbe ist das?', 'Wie viel Uhr ist es?', 'Wo ist die Farbe?'), 'b', 'Welche Farbe ist das? — «Какой это цвет?».'),
  sc(7, 'HARD', 'Der Tisch ist ___. (Стол чёрный.)', op4('schwarze', 'schwarzer', 'schwarz', 'schwarzen'), 'c', 'Цвет-сказуемое после ist остаётся без окончания: schwarz.'),
  sc(8, 'HARD', 'Какой артикль нужен: «___ Lampe ist gelb»?', op4('Der', 'Die', 'Das', 'Den'), 'b', 'Lampe — женского рода, поэтому артикль die.'),
  sc(9, 'HARD', 'Как ответить на «Welche Farbe ist das?», если предмет белый?', op4('Das ist weiß.', 'Das ist weiße.', 'Das weiß ist.', 'Ist das weiß.'), 'a', 'Краткий ответ строится как Das ist plus цвет без окончания: Das ist weiß.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Какие слова обозначают цвета? (выберите все верные)', op5('rot', 'blau', 'Tisch', 'Buch', 'Lampe'), ['a', 'b'], 'rot (красный) и blau (синий) — это цвета.'),
  mc(11, 'EASY', 'Какие пары «немецкий цвет → перевод» верны? (выберите все верные)', op5('grün → зелёный', 'gelb → жёлтый', 'rot → синий', 'blau → чёрный', 'weiß → красный'), ['a', 'b'], 'grün — зелёный, gelb — жёлтый.'),
  mc(12, 'EASY', 'Какие слова значат «чёрный» или «белый»? (выберите все верные)', op5('schwarz', 'weiß', 'grün', 'gelb', 'blau'), ['a', 'b'], 'schwarz — «чёрный», weiß — «белый».'),
  mc(13, 'EASY', 'В каких ответах цвет назван верно после «Das ist»? (выберите все верные)', op5('Das ist rot.', 'Das ist grün.', 'Das ist rote.', 'Das rot ist.', 'Ist das grün.'), ['a', 'b'], 'После Das ist цвет идёт без окончания: Das ist rot / grün.'),
  mc(14, 'EASY', 'Какие слова относятся к теме «Farben» (цвета)? (выберите все верные)', op5('gelb', 'schwarz', 'Stuhl', 'Tür', 'Bett'), ['a', 'b'], 'gelb (жёлтый) и schwarz (чёрный) — это названия цветов.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Какие предложения о цвете построены грамматически верно? (выберите все верные)', op5('Das Buch ist rot.', 'Die Lampe ist gelb.', 'Das Buch ist rote.', 'Die Lampe gelb ist.', 'Das rote Buch ist.'), ['a', 'b'], 'Цвет-сказуемое после ist не склоняется и стоит на втором месте глагол: Das Buch ist rot.'),
  mc(16, 'HARD', 'В каких предложениях артикль согласован с родом существительного? (выберите все верные)', op5('Der Tisch ist schwarz.', 'Das Bett ist weiß.', 'Die Tisch ist schwarz.', 'Der Bett ist weiß.', 'Das Lampe ist gelb.'), ['a', 'b'], 'der Tisch (м. р.) и das Bett (ср. р.) — артикли согласованы по роду.'),
  mc(17, 'HARD', 'Какие вопросы о цвете заданы правильно? (выберите все верные)', op5('Welche Farbe ist das?', 'Welche Farbe hat der Stuhl?', 'Welche Farbe das ist?', 'Was Farbe ist das?', 'Wo Farbe ist das?'), ['a', 'b'], 'Welche Farbe ist das? и Welche Farbe hat ...? — корректные вопросы о цвете.'),
  mc(18, 'HARD', 'В каких ответах цвет-сказуемое стоит без лишнего окончания? (выберите все верные)', op5('Die Tür ist grün.', 'Der Stuhl ist blau.', 'Die Tür ist grüne.', 'Der Stuhl ist blauer.', 'Die Tür ist grünen.'), ['a', 'b'], 'Предикативный цвет не склоняется: grün, blau без окончаний.'),
  mc(19, 'HARD', 'Какие пары «предмет → артикль» верны? (выберите все верные)', op5('Stuhl → der', 'Tür → die', 'Buch → der', 'Lampe → das', 'Tisch → die'), ['a', 'b'], 'der Stuhl (м. р.) и die Tür (ж. р.) — артикли названы верно.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Книга красная».', items4('Das', 'Buch', 'ist', 'rot'), 'Сказуемое-цвет без окончания: Das Buch ist rot.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Лампа жёлтая».', items4('Die', 'Lampe', 'ist', 'gelb'), 'die Lampe (ж. р.) plus ist plus цвет: Die Lampe ist gelb.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Стол чёрный».', items4('Der', 'Tisch', 'ist', 'schwarz'), 'der Tisch (м. р.) plus ist plus schwarz: Der Tisch ist schwarz.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Кровать белая».', items4('Das', 'Bett', 'ist', 'weiß'), 'das Bett (ср. р.) plus ist plus weiß: Das Bett ist weiß.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Это синий».', items4('Das', 'ist', 'blau', '.'), 'Краткий ответ о цвете: Das ist blau.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкий вопрос: «Какой это цвет?».', items5('Welche', 'Farbe', 'ist', 'das', '?'), 'Глагол ist на втором месте: Welche Farbe ist das?'),
  ord(26, 'HARD', 'Соберите немецкий вопрос: «Какого цвета стул?».', items5('Welche', 'Farbe', 'hat', 'der', 'Stuhl'), 'Вопрос о цвете предмета: Welche Farbe hat der Stuhl?'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Дверь зелёная».', items4('Die', 'Tür', 'ist', 'grün'), 'die Tür (ж. р.) plus ist plus grün: Die Tür ist grün.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Стул синий».', items4('Der', 'Stuhl', 'ist', 'blau'), 'der Stuhl (м. р.) plus ist plus blau: Der Stuhl ist blau.'),
  ord(29, 'HARD', 'Соберите немецкий ответ: «Это зелёный».', items4('Das', 'ist', 'grün', '.'), 'Краткий ответ о цвете без окончания: Das ist grün.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Das Buch ist ___. (красная)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('rot', 'blau', 'grün', 'gelb', 'weiß'), 'Книга красная — das Buch ist rot.'),
  fb(31, 'EASY', 'Die Lampe ist ___. (жёлтая)', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('rot', 'blau', 'grün', 'gelb', 'weiß'), 'Лампа жёлтая — die Lampe ist gelb.'),
  fb(32, 'EASY', 'Der Tisch ist ___. (чёрный)', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('rot', 'blau', 'grün', 'gelb', 'schwarz'), 'Стол чёрный — der Tisch ist schwarz.'),
  fb(33, 'EASY', 'Das Bett ist ___. (белая)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('rot', 'gelb', 'weiß', 'grün', 'blau'), 'Кровать белая — das Bett ist weiß.'),
  fb(34, 'EASY', 'Das ist ___. (синий)', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('rot', 'blau', 'grün', 'gelb', 'weiß'), 'Это синий — das ist blau.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', '___ Tisch ist schwarz. (артикль)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('Der', 'Die', 'Das', 'Den', 'Dem'), 'der Tisch — мужской род, артикль Der.'),
  fb(36, 'HARD', '___ Lampe ist gelb. (артикль)', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Der', 'Die', 'Das', 'Den', 'Dem'), 'die Lampe — женский род, артикль Die.'),
  fb(37, 'HARD', '___ Buch ist rot. (артикль)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Der', 'Die', 'Das', 'Den', 'Dem'), 'das Buch — средний род, артикль Das.'),
  fb(38, 'HARD', 'Welche ___ ist das? (слово «цвет»)', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Tisch', 'Buch', 'Lampe', 'Farbe', 'Bett'), 'Вопрос о цвете: Welche Farbe ist das?'),
  fb(39, 'HARD', 'Welche Farbe ___ der Stuhl? (глагол)', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('ist', 'sind', 'bin', 'bist', 'hat'), 'Какого цвета стул — Welche Farbe hat der Stuhl?'),
];
