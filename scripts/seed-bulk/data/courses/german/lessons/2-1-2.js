'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY =====
  sc(0, 'EASY', 'Wir ___ zusammen Deutsch.', op4('lernen', 'lernt', 'lernst', 'lerne'), 'a', 'С wir слабый глагол получает окончание -en.'),
  sc(1, 'EASY', 'Ihr ___ in Hamburg.', op4('wohnen', 'wohnt', 'wohnst', 'wohne'), 'b', 'С ihr глагол получает окончание -t.'),
  sc(2, 'EASY', 'Sie (они) ___ Brot und Milch.', op4('kaufe', 'kaufst', 'kauft', 'kaufen'), 'd', 'Форма sie (они) совпадает с инфинитивом и имеет окончание -en.'),
  sc(3, 'EASY', '___ Sie hier? (вежливо)', op4('Wohnen', 'Wohnt', 'Wohnst', 'Wohne'), 'a', 'Вежливое Sie требует окончания -en, как и инфинитив.'),
  sc(4, 'EASY', 'Die Kinder ___ im Park.', op4('spiele', 'spielst', 'spielt', 'spielen'), 'd', 'Подлежащее во множественном числе (sie) даёт окончание -en.'),

  // ===== 5 sc HARD =====
  sc(5, 'HARD', 'Ihr ___ fleißig im Büro.', op4('arbeiten', 'arbeitet', 'arbeitt', 'arbeitest'), 'b', 'После основы на -t вставляется соединительная -e-: ihr arbeitet.'),
  sc(6, 'HARD', 'Die Leute ___ die Frage.', op4('beantwortet', 'beantwortest', 'beantworten', 'beantworte'), 'c', 'die Leute — это они, поэтому глагол получает окончание -en.'),
  sc(7, 'HARD', 'Wir ___ einen neuen Computer.', op4('brauche', 'brauchst', 'braucht', 'brauchen'), 'd', 'С wir слабый глагол всегда оканчивается на -en.'),
  sc(8, 'HARD', '___ ihr gern Musik?', op4('Hört', 'Hören', 'Hörst', 'Höre'), 'a', 'В вопросе с ihr глагол стоит впереди и имеет окончание -t.'),
  sc(9, 'HARD', 'Meine Freunde ___ in Berlin.', op4('lebe', 'lebst', 'lebt', 'leben'), 'd', 'Подлежащее во множественном числе даёт глаголу окончание -en.'),

  // ===== 5 mc EASY =====
  mc(10, 'EASY', 'Какие формы стоят в правильном множественном числе?', op5('wir machen', 'ihr macht', 'du machst', 'sie machen', 'ich mache'), ['a', 'b', 'd'], 'wir и sie берут -en, ihr берёт -t; du и ich — единственное число.'),
  mc(11, 'EASY', 'Где глагол правильно согласован с подлежащим?', op5('wir kaufen', 'ihr kauft', 'wir kauft', 'sie kaufen', 'ihr kaufen'), ['a', 'b', 'd'], 'wir/sie дают -en, ihr даёт -t.'),
  mc(12, 'EASY', 'Какие формы относятся к ihr?', op5('ihr lernt', 'ihr wohnt', 'ihr spielen', 'ihr fragt', 'ihr hören'), ['a', 'b', 'd'], 'Местоимение ihr всегда требует окончания -t.'),
  mc(13, 'EASY', 'Какие предложения о людях (они) верны?', op5('Die Kinder spielen.', 'Die Leute leben hier.', 'Die Freunde kaufen Brot.', 'Die Kinder spielt.', 'Die Leute lebt hier.'), ['a', 'b', 'c'], 'Множественное подлежащее всегда даёт глаголу окончание -en.'),
  mc(14, 'EASY', 'Какие формы совпадают с инфинитивом по окончанию -en?', op5('wir hören', 'sie (они) hören', 'Sie (вежливо) hören', 'ihr hört', 'du hörst'), ['a', 'b', 'c'], 'wir, sie (они) и вежливое Sie совпадают с инфинитивом на -en.'),

  // ===== 5 mc HARD =====
  mc(15, 'HARD', 'Где соединительная -e- стоит правильно?', op6('ihr arbeitet', 'ihr antwortet', 'ihr wartet', 'ihr arbeitt', 'ihr antwortt', 'ihr wartt'), ['a', 'b', 'c'], 'После основы на -t/-d у ihr вставляется -e- перед окончанием -t.'),
  mc(16, 'HARD', 'Какие вопросы с вежливым Sie написаны верно?', op5('Wohnen Sie hier?', 'Kaufen Sie Brot?', 'Hören Sie Musik?', 'Wohnt Sie hier?', 'Wohnst Sie hier?'), ['a', 'b', 'c'], 'Вежливое Sie всегда требует формы на -en и пишется с большой буквы.'),
  mc(17, 'HARD', 'Где подлежащее и глагол согласованы правильно?', op6('Die Leute fragen', 'Meine Freunde leben', 'Die Kinder hören', 'Die Leute fragt', 'Meine Freunde lebt', 'Die Kinder hört'), ['a', 'b', 'c'], 'Множественное подлежащее всегда требует глагола на -en.'),
  mc(18, 'HARD', 'Какие формы являются правильным множественным числом для wir?', op5('wir brauchen', 'wir antworten', 'wir leben', 'wir braucht', 'wir lebt'), ['a', 'b', 'c'], 'С wir слабый глагол всегда оканчивается на -en.'),
  mc(19, 'HARD', 'Какие предложения с ihr корректны?', op6('Ihr fragt viel.', 'Ihr antwortet schnell.', 'Ihr arbeitet hier.', 'Ihr fragen viel.', 'Ihr antwortt schnell.', 'Ihr arbeitt hier.'), ['a', 'b', 'c'], 'У ihr окончание -t, а после -t/-d добавляется соединительная -e-.'),

  // ===== 5 ord EASY =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Мы вместе учим немецкий.»', items4('Wir', 'lernen', 'zusammen', 'Deutsch'), 'В главном предложении глагол стоит на втором месте.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Они покупают хлеб.»', items4('Sie', 'kaufen', 'das', 'Brot'), 'Форма sie (они) совпадает с инфинитивом на -en.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Дети играют в парке.»', items5('Die', 'Kinder', 'spielen', 'im', 'Park'), 'Множественное подлежащее даёт глаголу окончание -en.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Вы живёте в Гамбурге.»', items4('Ihr', 'wohnt', 'in', 'Hamburg'), 'С ihr глагол получает окончание -t.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Мы слушаем музыку.»', items4('Wir', 'hören', 'die', 'Musik'), 'С wir глагол оканчивается на -en.'),

  // ===== 5 ord HARD =====
  ord(25, 'HARD', 'Соберите немецкий вопрос: «Вы живёте в Вене?» (вежливо)', items4('Wohnen', 'Sie', 'in', 'Wien'), 'В Ja-Nein-вопросе глагол стоит на первом месте; вежливое Sie — с большой буквы.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Люди отвечают на вопрос.»', items5('Die', 'Leute', 'beantworten', 'die', 'Frage'), 'die Leute — это они, поэтому глагол получает окончание -en.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Вы работаете очень прилежно.»', items4('Ihr', 'arbeitet', 'sehr', 'fleißig'), 'После основы на -t у ihr добавляется соединительная -e-: arbeitet.'),
  ord(28, 'HARD', 'Соберите немецкий вопрос: «Вы охотно слушаете музыку?»', items5('Hört', 'ihr', 'gern', 'die', 'Musik'), 'В вопросе с ihr глагол на первом месте и оканчивается на -t.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Мои друзья нуждаются в помощи.»', items4('Meine', 'Freunde', 'brauchen', 'Hilfe'), 'Множественное подлежащее требует глагола на -en.'),

  // ===== 5 fb EASY =====
  fb(30, 'EASY', 'Wir ___ zusammen Deutsch.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('lernen', 'lernt', 'lernst', 'lerne', 'lernet'), 'С wir слабый глагол оканчивается на -en.'),
  fb(31, 'EASY', 'Ihr ___ in Hamburg.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('wohnen', 'wohnt', 'wohne', 'wohnst', 'wohnest'), 'С ihr глагол получает окончание -t.'),
  fb(32, 'EASY', 'Die Kinder ___ im Park.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('spielt', 'spielst', 'spielen', 'spiele', 'spielet'), 'Множественное подлежащее даёт глаголу окончание -en.'),
  fb(33, 'EASY', '___ Sie hier? (вежливо)', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Wohnt', 'Wohnst', 'Wohne', 'Wohnen', 'Wohntet'), 'Вежливое Sie требует формы на -en.'),
  fb(34, 'EASY', 'Sie (они) ___ Brot und Milch.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('kaufe', 'kaufst', 'kauft', 'kaufet', 'kaufen'), 'Форма sie (они) совпадает с инфинитивом на -en.'),

  // ===== 5 fb HARD =====
  fb(35, 'HARD', 'Ihr ___ sehr fleißig.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('arbeitet', 'arbeitt', 'arbeitest', 'arbeiten', 'arbeitst'), 'После основы на -t у ihr добавляется соединительная -e-: arbeitet.'),
  fb(36, 'HARD', 'Die Leute ___ die Frage.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('beantwortet', 'beantworten', 'beantwortest', 'beantworte', 'beantwortt'), 'die Leute — это они, поэтому глагол на -en.'),
  fb(37, 'HARD', 'Wir ___ einen Computer.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('braucht', 'brauchst', 'brauchen', 'brauche', 'brauchet'), 'С wir слабый глагол всегда оканчивается на -en.'),
  fb(38, 'HARD', 'Meine Freunde ___ in Berlin.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('lebt', 'lebst', 'lebe', 'leben', 'lebet'), 'Множественное подлежащее требует глагола на -en.'),
  fb(39, 'HARD', '___ ihr gern Musik?', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Hören', 'Höre', 'Hörst', 'Hört', 'Höret'), 'В вопросе с ihr глагол на первом месте и оканчивается на -t.'),
];
