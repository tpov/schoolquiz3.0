'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Что значит «die Stellenanzeige»?',
    op4('резюме', 'объявление о вакансии', 'испытательный срок', 'сопроводительное письмо'),
    'b', 'die Stellenanzeige — это объявление о вакансии.'),

  sc(1, 'EASY', 'Что значит «der Lebenslauf»?',
    op4('резюме', 'заявление о приёме', 'требование', 'знания'),
    'a', 'der Lebenslauf — это резюме с историей образования и работы.'),

  sc(2, 'EASY', 'Welcher Satz ist richtig? «Я хотел бы откликнуться на вакансию.»',
    op4('Ich möchte mich um die Stelle bewerben.', 'Ich möchte um die Stelle mich bewerben.', 'Ich möchte mich um die Stelle bewerbe.', 'Ich bewerben möchte mich um die Stelle.'),
    'a', 'Модальный möchte стоит на 2-м месте, а инфинитив bewerben уходит в конец.'),

  sc(3, 'EASY', 'Welches Wort fehlt? «___ Sie gute Deutschkenntnisse haben.» (требование: вы должны)',
    op4('Können', 'Müssen', 'Wollen', 'Möchten'),
    'b', 'Требование «вы должны» выражает модальный глагол müssen.'),

  sc(4, 'EASY', 'Welcher Satz ist richtig? «Есть свободная вакансия.»',
    op4('Es gibt ein freie Stelle.', 'Es gibt einen freie Stelle.', 'Es gibt eine freie Stelle.', 'Es gibt eine frei Stelle.'),
    'c', 'После es gibt идёт Akkusativ: eine freie Stelle (die Stelle, женский род).'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Welcher Satz ist korrekt? «Вы должны хорошо говорить по-английски и уметь работать на компьютере.»',
    op4('Sie müssen gut Englisch sprechen und am Computer arbeiten können.', 'Sie müssen gut Englisch sprechen und am Computer können arbeiten.', 'Sie müssen gut Englisch sprechen und können am Computer arbeiten zu.', 'Sie müssen sprechen gut Englisch und am Computer arbeiten können.'),
    'a', 'Оба инфинитива (sprechen и arbeiten können) стоят в конце рамки после müssen.'),

  sc(6, 'HARD', 'Welcher Satz ist korrekt? «Я подаю заявление на должность проектного менеджера.»',
    op4('Ich bewerbe mich für die Stelle als Projektmanager.', 'Ich bewerbe mich um die Stelle als Projektmanager.', 'Ich bewerbe mich an die Stelle als Projektmanager.', 'Ich bewerbe mich auf der Stelle als Projektmanager.'),
    'b', 'Глагол sich bewerben управляет предлогом um + Akkusativ: sich um die Stelle bewerben.'),

  sc(7, 'HARD', 'Welcher Satz ist korrekt? «У неё есть опыт в маркетинге.»',
    op4('Sie hat Erfahrung in den Marketing.', 'Sie hat Erfahrung in das Marketing.', 'Sie hat Erfahrung im Marketing.', 'Sie hat Erfahrung an Marketing.'),
    'c', 'Erfahrung haben in управляет Dativ: in dem Marketing → im Marketing (das Marketing).'),

  sc(8, 'HARD', 'Welcher Satz ist korrekt? «Кандидаты должны быть гибкими и уметь работать в команде.»',
    op4('Die Bewerber sollten flexibel sein und im Team können arbeiten.', 'Die Bewerber sollten flexibel sein und im Team arbeiten können.', 'Die Bewerber sollten sein flexibel und im Team arbeiten können.', 'Die Bewerber sollten flexibel sein und im Team arbeiten zu können.'),
    'b', 'Однородные инфинитивы sein и arbeiten können стоят в конце рамки после sollten.'),

  sc(9, 'HARD', 'Что значит «die Probezeit»?',
    op4('полная занятость', 'частичная занятость', 'испытательный срок', 'отпуск'),
    'c', 'die Probezeit — это испытательный срок в начале новой работы.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Wörter passen zum Thema «Bewerbung»? (документы и понятия отклика)',
    op5('der Lebenslauf', 'das Anschreiben', 'die Banane', 'die Stellenanzeige', 'der Tisch'),
    ['a', 'b', 'd'], 'Lebenslauf, Anschreiben и Stellenanzeige относятся к теме поиска работы.'),

  mc(11, 'EASY', 'Welche Modalverben drücken eine Voraussetzung oder Fähigkeit aus?',
    op5('müssen', 'essen', 'können', 'spielen', 'sollen'),
    ['a', 'c', 'e'], 'müssen, können и sollen выражают требование, умение или рекомендацию.'),

  mc(12, 'EASY', 'Welche Sätze sind grammatisch korrekt? (рамка: инфинитив в конце)',
    op6('Sie müssen Deutsch sprechen.', 'Sie müssen sprechen Deutsch.', 'Sie können am Computer arbeiten.', 'Sie können arbeiten am Computer.', 'Sie sollten flexibel sein.', 'Sie sollten sein flexibel.'),
    ['a', 'c', 'e'], 'Модальный глагол стоит на 2-м месте, а инфинитив (sprechen/arbeiten/sein) — в конце.'),

  mc(13, 'EASY', 'Welche Übersetzungen sind richtig?',
    op5('die Vollzeit — полная занятость', 'die Teilzeit — частичная занятость', 'die Vollzeit — отпуск', 'die Bewerbung — заявление, отклик', 'die Teilzeit — резюме'),
    ['a', 'b', 'd'], 'Vollzeit — полная, Teilzeit — частичная занятость, Bewerbung — отклик.'),

  mc(14, 'EASY', 'Welche Sätze mit «es gibt» sind korrekt? (Akkusativ nach es gibt)',
    op5('Es gibt eine freie Stelle.', 'Es gibt einen Job in Berlin.', 'Es gibt eine freier Stelle.', 'Es gibt ein Praktikum im Büro.', 'Es gibt einem Job in Berlin.'),
    ['a', 'b', 'd'], 'После es gibt существительное стоит в Akkusativ: eine Stelle, einen Job, ein Praktikum.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Welche Sätze beschreiben Voraussetzungen korrekt?',
    op6('Sie müssen gute Englischkenntnisse haben.', 'Sie müssen haben gute Englischkenntnisse.', 'Sie können am Computer arbeiten.', 'Sie können am Computer zu arbeiten.', 'Sie sollten im Team arbeiten können.', 'Sie sollten können im Team arbeiten.'),
    ['a', 'c', 'e'], 'Инфинитив стоит в конце без zu, а при двух глаголах — порядок «… arbeiten können».'),

  mc(16, 'HARD', 'Welche Sätze mit «sich bewerben um» sind korrekt?',
    op6('Ich bewerbe mich um die Stelle.', 'Ich bewerbe mich für die Stelle.', 'Ich möchte mich um die Stelle bewerben.', 'Ich möchte mich um die Stelle bewerbe.', 'Sie bewirbt sich um den Job.', 'Sie bewirbt sich um dem Job.'),
    ['a', 'c', 'e'], 'sich bewerben управляет um + Akkusativ (die Stelle, den Job), а инфинитив — в конце.'),

  mc(17, 'HARD', 'Welche Sätze mit «Erfahrung haben in» (+ Dativ) sind korrekt?',
    op6('Er hat Erfahrung im Verkauf.', 'Er hat Erfahrung in den Verkauf.', 'Sie hat Erfahrung in der Buchhaltung.', 'Sie hat Erfahrung in die Buchhaltung.', 'Wir haben Erfahrung im Marketing.', 'Wir haben Erfahrung in das Marketing.'),
    ['a', 'c', 'e'], 'Erfahrung haben in требует Dativ: im Verkauf, in der Buchhaltung, im Marketing.'),

  mc(18, 'HARD', 'Welche Sätze sind als Anforderung in einer Stellenanzeige korrekt?',
    op6('Sie sollten flexibel sein und im Team arbeiten können.', 'Sie sollten flexibel sein und im Team können arbeiten.', 'Sie müssen Deutsch und Englisch sprechen.', 'Sie müssen sprechen Deutsch und Englisch.', 'Sie können selbstständig arbeiten.', 'Sie können selbstständig zu arbeiten.'),
    ['a', 'c', 'e'], 'Инфинитивы стоят в конце рамки без zu; при двух глаголах порядок «arbeiten können».'),

  mc(19, 'HARD', 'Welche Aussagen über eine Bewerbung sind korrekt?',
    op6('Der Lebenslauf zeigt die Berufserfahrung.', 'Das Anschreiben ist ein kurzer Brief an die Firma.', 'Die Probezeit ist der erste Lohn.', 'Die Voraussetzung ist eine Bedingung für die Stelle.', 'Die Teilzeit bedeutet Vollzeit.', 'Die Stellenanzeige ist ein Lebenslauf.'),
    ['a', 'b', 'd'], 'Lebenslauf показывает опыт, Anschreiben — письмо, Voraussetzung — условие для вакансии.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Вы должны говорить по-немецки.»',
    items4('Sie', 'müssen', 'Deutsch', 'sprechen'),
    'Модальный müssen на 2-м месте, инфинитив sprechen — в конце рамки.'),

  ord(21, 'EASY', 'Соберите немецкое предложение: «Есть свободная вакансия.»',
    items5('Es', 'gibt', 'eine', 'freie', 'Stelle'),
    'После es gibt идёт Akkusativ: eine freie Stelle (die Stelle).'),

  ord(22, 'EASY', 'Соберите немецкое предложение: «Вы можете работать на компьютере.»',
    items5('Sie', 'können', 'am', 'Computer', 'arbeiten'),
    'Модальный können на 2-м месте, инфинитив arbeiten — в конце.'),

  ord(23, 'EASY', 'Соберите немецкое предложение: «Я отправляю своё резюме.»',
    items4('Ich', 'schicke', 'meinen', 'Lebenslauf'),
    'der Lebenslauf в Akkusativ — meinen Lebenslauf после schicke.'),

  ord(24, 'EASY', 'Соберите немецкое предложение: «Мы ищем сотрудницу.»',
    items4('Wir', 'suchen', 'eine', 'Mitarbeiterin'),
    'die Mitarbeiterin в Akkusativ после suchen — eine Mitarbeiterin.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Я хотел бы откликнуться на эту вакансию.»',
    items5('Ich', 'möchte', 'mich', 'um die Stelle', 'bewerben'),
    'sich um die Stelle bewerben: möchte на 2-м месте, инфинитив bewerben в конце.'),

  ord(26, 'HARD', 'Соберите немецкое предложение: «Вы должны иметь хорошее знание английского.»',
    items5('Sie', 'müssen', 'gute', 'Englischkenntnisse', 'haben'),
    'Инфинитив haben стоит в конце рамки после müssen.'),

  ord(27, 'HARD', 'Соберите немецкое предложение: «У неё есть опыт в продажах.»',
    items4('Sie', 'hat', 'Erfahrung', 'im Verkauf'),
    'Оборот *Erfahrung haben in* («иметь опыт в») требует датива, а *in dem* сливается в *im*: *in dem Verkauf* → *im Verkauf*.'),

  ord(28, 'HARD', 'Соберите немецкое предложение: «Вы должны быть гибкими и уметь работать в команде.»',
    items5('Sie', 'sollten', 'flexibel sein', 'und im Team', 'arbeiten können'),
    'Однородные инфинитивы sein и arbeiten können стоят в конце после sollten.'),

  ord(29, 'HARD', 'Соберите немецкое предложение: «Пожалуйста, пришлите нам своё резюме.»',
    items5('Bitte', 'schicken', 'Sie', 'uns', 'Ihren Lebenslauf'),
    'der Lebenslauf в Akkusativ — Ihren Lebenslauf; в просьбе глагол schicken на 1-м месте.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Sie ___ gute Deutschkenntnisse haben.',
    [{ id: 'b1', correctCandidateId: 'c1' }],
    cand5('müssen', 'muss', 'könnt', 'will', 'magst'),
    'Для Sie (вы) форма müssen — это «müssen», требование к кандидату.'),

  fb(31, 'EASY', 'Es gibt ___ freie Stelle im Büro.',
    [{ id: 'b1', correctCandidateId: 'c2' }],
    cand5('ein', 'eine', 'einen', 'einem', 'einer'),
    'После es gibt идёт Akkusativ женского рода: eine freie Stelle (die Stelle).'),

  fb(32, 'EASY', 'Bitte schicken Sie uns Ihren ___.',
    [{ id: 'b1', correctCandidateId: 'c3' }],
    cand5('Bewerbung', 'Probezeit', 'Lebenslauf', 'Teilzeit', 'Voraussetzung'),
    'der Lebenslauf (резюме) — мужского рода, поэтому Ihren Lebenslauf.'),

  fb(33, 'EASY', 'Sie können am Computer ___.',
    [{ id: 'b1', correctCandidateId: 'c4' }],
    cand5('arbeitet', 'gearbeitet', 'zu arbeiten', 'arbeiten', 'arbeite'),
    'После модального können инфинитив arbeiten стоит в конце без zu.'),

  fb(34, 'EASY', 'Ich möchte mich um die Stelle ___.',
    [{ id: 'b1', correctCandidateId: 'c5' }],
    cand5('beworben', 'bewirbt', 'bewerbe', 'bewirbst', 'bewerben'),
    'После möchte инфинитив bewerben стоит в конце рамки.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Ich bewerbe mich ___ die Stelle als Projektmanager.',
    [{ id: 'b1', correctCandidateId: 'c1' }],
    cand5('um', 'für', 'an', 'mit', 'in'),
    'Глагол sich bewerben управляет предлогом um + Akkusativ.'),

  fb(36, 'HARD', 'Sie hat viel Erfahrung ___ Marketing.',
    [{ id: 'b1', correctCandidateId: 'c2' }],
    cand5('in das', 'im', 'in den', 'an', 'um'),
    'Оборот *Erfahrung haben in* («иметь опыт в») требует датива, а *in dem* сливается в *im*: *in dem Marketing* → *im Marketing* (*das Marketing*).'),

  fb(37, 'HARD', 'Sie müssen gut Englisch sprechen und am Computer ___ können.',
    [{ id: 'b1', correctCandidateId: 'c3' }],
    cand5('arbeitet', 'gearbeitet', 'arbeiten', 'zu arbeiten', 'arbeite'),
    'При двух глаголах в конце стоит порядок «arbeiten können»: инфинитив перед können.'),

  fb(38, 'HARD', 'Die Bewerber ___ flexibel sein und im Team arbeiten können.',
    [{ id: 'b1', correctCandidateId: 'c4' }],
    cand5('könnt', 'muss', 'magst', 'sollten', 'will'),
    'Рекомендацию для нескольких кандидатов выражает sollten (форма множественного числа).'),

  fb(39, 'HARD', 'Es gibt ___ Job in Vollzeit für unser Büro.',
    [{ id: 'b1', correctCandidateId: 'c5' }],
    cand5('ein', 'eine', 'einem', 'einer', 'einen'),
    'После es gibt идёт Akkusativ мужского рода: einen Job (der Job).'),
];
