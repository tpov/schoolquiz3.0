'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Ich ___ Student.', op4('bin', 'bist', 'ist', 'sind'), 'a',
    'С местоимением ich глагол sein имеет форму bin.'),
  sc(1, 'EASY', 'Du ___ sehr nett.', op4('bin', 'ist', 'bist', 'seid'), 'c',
    'С местоимением du глагол sein имеет форму bist.'),
  sc(2, 'EASY', 'Er ___ Lehrer.', op4('bist', 'bin', 'sind', 'ist'), 'd',
    'С er/sie/es глагол sein имеет форму ist.'),
  sc(3, 'EASY', 'Wir ___ zu Hause.', op4('seid', 'sind', 'ist', 'bin'), 'b',
    'С местоимением wir глагол sein имеет форму sind.'),
  sc(4, 'EASY', 'Ihr ___ müde.', op4('seid', 'sind', 'bist', 'ist'), 'a',
    'С местоимением ihr глагол sein имеет форму seid.'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Wähle den richtigen Satz: «Я учитель».', op4('Ich bin ein Lehrer.', 'Ich bin Lehrer.', 'Ich ist Lehrer.', 'Ich bin der Lehrer.'), 'b',
    'При названии профессии артикль не ставится: Ich bin Lehrer.'),
  sc(6, 'HARD', 'Er ___ nicht zu Hause.', op4('bin', 'seid', 'ist', 'sind'), 'c',
    'Отрицание ставится словом nicht после формы ist.'),
  sc(7, 'HARD', '___ ihr verheiratet? – Ja, wir sind verheiratet.', op4('Seid', 'Sind', 'Bist', 'Ist'), 'a',
    'В вопросе с ihr глагол seid ставится на первое место.'),
  sc(8, 'HARD', 'Sie (Anna) ___ Studentin und sie ___ glücklich.', op4('ist … sind', 'bist … ist', 'ist … ist', 'sind … ist'), 'c',
    'Для одной женщины sie оба раза используется форма ist.'),
  sc(9, 'HARD', 'Wähle den richtigen Satz: «Вы (вежливо) очень милы».', op4('Sie bist sehr nett.', 'Sie seid sehr nett.', 'Sie ist sehr nett.', 'Sie sind sehr nett.'), 'd',
    'С вежливым Sie глагол sein имеет форму sind.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Sätze sind richtig?', op5('Ich bin müde.', 'Du bin müde.', 'Er ist krank.', 'Wir bin zu Hause.', 'Ihr seid nett.'), ['a', 'c', 'e'],
    'Верны формы bin (ich), ist (er) и seid (ihr).'),
  mc(11, 'EASY', 'Welche Formen von «sein» gibt es?', op5('bin', 'bist', 'bast', 'ist', 'sint'), ['a', 'b', 'd'],
    'Реальные формы sein — bin, bist, ist; bast и sint не существуют.'),
  mc(12, 'EASY', 'Welche Sätze passen zu «du»?', op5('Du bist Student.', 'Du bist müde.', 'Du ist krank.', 'Du sind nett.', 'Du bist glücklich.'), ['a', 'b', 'e'],
    'С du всегда форма bist.'),
  mc(13, 'EASY', 'Welche Sätze sind grammatisch korrekt?', op5('Sie sind verheiratet.', 'Wir sind zu Hause.', 'Ich ist Lehrer.', 'Er ist Student.', 'Du seid müde.'), ['a', 'b', 'd'],
    'Корректны sie sind, wir sind и er ist.'),
  mc(14, 'EASY', 'Welche Pronomen passen zur Form «sind»?', op6('wir', 'sie (они)', 'Sie', 'ich', 'du', 'er'), ['a', 'b', 'c'],
    'Форму sind берут wir, sie (они) и вежливое Sie.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Welche Sätze sind komplett korrekt?', op5('Ich bin Studentin.', 'Wir sind ein Lehrer.', 'Er ist nicht krank.', 'Ihr seid glücklich.', 'Du ist verheiratet.'), ['a', 'c', 'd'],
    'Верны Ich bin Studentin, Er ist nicht krank и Ihr seid glücklich.'),
  mc(16, 'HARD', 'Welche Sätze drücken eine Verneinung korrekt aus?', op5('Er ist nicht müde.', 'Sie sind nicht zu Hause.', 'Ich bin kein müde.', 'Wir nicht sind krank.', 'Ihr seid nicht glücklich.'), ['a', 'b', 'e'],
    'Отрицание прилагательного/наречия после sein делается словом nicht.'),
  mc(17, 'HARD', 'Welche Antworten auf «Seid ihr müde?» sind korrekt?', op5('Ja, wir sind müde.', 'Ja, ich sind müde.', 'Nein, wir sind nicht müde.', 'Ja, ihr seid müde.', 'Nein, wir nicht müde.'), ['a', 'c'],
    'На вопрос про ihr отвечают через wir sind / wir sind nicht.'),
  mc(18, 'HARD', 'Welche Sätze nennen einen Beruf korrekt (ohne Artikel)?', op5('Sie ist Lehrerin.', 'Er ist ein Student.', 'Ich bin Student.', 'Du bist der Lehrer.', 'Wir sind Studenten.'), ['a', 'c', 'e'],
    'Профессию называют без артикля: ist Lehrerin, bin Student, sind Studenten.'),
  mc(19, 'HARD', 'In welchen Sätzen passt die Form zum Pronomen?', op6('ich bin', 'du bist', 'er seid', 'wir sind', 'ihr bist', 'sie (они) sind'), ['a', 'b', 'd', 'f'],
    'Корректны ich bin, du bist, wir sind и sie sind.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Я студент».', items4('Ich', 'bin', 'Student', '.'), 'Порядок: подлежащее ich, глагол bin, профессия Student.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Ты усталый».', items4('Du', 'bist', 'müde', '.'), 'Порядок: du на первом месте, глагол bist на втором.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Он учитель».', items4('Er', 'ist', 'Lehrer', '.'), 'Порядок: er, форма ist, профессия Lehrer без артикля.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Мы дома».', items5('Wir', 'sind', 'zu', 'Hause', '.'), 'Порядок: wir, sind, затем устойчивое выражение zu Hause.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Вы счастливы».', items4('Ihr', 'seid', 'glücklich', '.'), 'Порядок: ihr, форма seid, прилагательное glücklich.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Он не болен».', items5('Er', 'ist', 'nicht', 'krank', '.'), 'Отрицание nicht стоит перед прилагательным krank.'),
  ord(26, 'HARD', 'Соберите немецкий вопрос: «Вы устали?».', items4('Seid', 'ihr', 'müde', '?'), 'В Ja-Nein-вопросе глагол seid стоит на первом месте.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Я студентка и счастлива».', items5('Ich', 'bin', 'Studentin', 'und', 'glücklich'), 'Союз und соединяет профессию Studentin и состояние glücklich.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Мы не дома».', items5('Wir', 'sind', 'nicht', 'zu', 'Hause'), 'Отрицание nicht стоит перед выражением zu Hause.'),
  ord(29, 'HARD', 'Соберите немецкий вопрос: «Ты женат?».', items4('Bist', 'du', 'verheiratet', '?'), 'В вопросе с du глагол bist стоит на первом месте.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Ich ___ müde.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('bin', 'bist', 'ist', 'seid', 'sind'),
    'С местоимением ich используется форма bin.'),
  fb(31, 'EASY', 'Du ___ Student.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('bin', 'bist', 'ist', 'seid', 'sind'),
    'С местоимением du используется форма bist.'),
  fb(32, 'EASY', 'Er ___ krank.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('bin', 'bist', 'ist', 'seid', 'sind'),
    'С местоимением er используется форма ist.'),
  fb(33, 'EASY', 'Ihr ___ glücklich.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('bin', 'bist', 'ist', 'seid', 'sind'),
    'С местоимением ihr используется форма seid.'),
  fb(34, 'EASY', 'Wir ___ zu Hause.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('bin', 'bist', 'ist', 'seid', 'sind'),
    'С местоимением wir используется форма sind.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Sie (Frau Müller) ___ Lehrerin.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('bin', 'bist', 'ist', 'seid', 'sind'),
    'Для одной женщины sie используется форма ist.'),
  fb(36, 'HARD', 'Er ist ___ zu Hause.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('bin', 'kein', 'sind', 'seid', 'nicht'),
    'Состояние/место после sein отрицается словом nicht.'),
  fb(37, 'HARD', '___ ihr verheiratet?', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Bin', 'Bist', 'Ist', 'Seid', 'Sind'),
    'В вопросе с ihr на первом месте стоит форма seid.'),
  fb(38, 'HARD', 'Sie (они) ___ Studenten.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('bin', 'bist', 'ist', 'seid', 'sind'),
    'Для sie (они) используется форма sind.'),
  fb(39, 'HARD', '___ du müde? – Ja, ich ___ müde.', [{ id: 'b1', correctCandidateId: 'c2' }, { id: 'b2', correctCandidateId: 'c1' }], cand5('bin', 'Bist', 'ist', 'seid', 'sind'),
    'В вопросе с du форма Bist, в ответе с ich форма bin.'),
];
