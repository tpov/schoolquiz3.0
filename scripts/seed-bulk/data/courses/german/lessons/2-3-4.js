'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Was sagen Sie höflich im Geschäft: «Я хотел бы килограмм помидоров»?',
    op4('Ich hätte gern ein Kilo Tomaten.', 'Ich nehme Kilo Tomaten gestern.', 'Du hast Kilo Tomaten.', 'Wir Tomaten Kilo gern.'),
    'a', 'Устойчивая формула вежливой просьбы «Ich hätte gern …» (я хотел бы).'),

  sc(1, 'EASY', 'Wie fragt die Verkäuferin höflich nach mehr: «Что-нибудь ещё?»?',
    op4('Wer noch etwas?', 'Sonst noch etwas?', 'Wo noch etwas?', 'Wie noch etwas?'),
    'b', 'Стандартная реплика продавца «Sonst noch etwas?» означает «Что-нибудь ещё?».'),

  sc(2, 'EASY', 'Wie antworten Sie: «Нет, спасибо, это всё»?',
    op4('Ja, bitte, das ist viel.', 'Nein, danke, das ist klein.', 'Nein, danke, das ist alles.', 'Ja, danke, das ist nichts.'),
    'c', 'Завершающая реплика покупателя «Nein, danke, das ist alles» = «Нет, спасибо, это всё».'),

  sc(3, 'EASY', 'Welches Wort bedeutet «пожалуйста»?',
    op4('danke', 'tschüss', 'hallo', 'bitte'),
    'd', 'Слово «bitte» переводится как «пожалуйста».'),

  sc(4, 'EASY', 'Wie fragen Sie nach dem Preis: «Сколько с меня?»?',
    op4('Was macht das?', 'Wer macht das?', 'Wann macht das?', 'Warum macht das?'),
    'a', 'Вопрос о сумме оплаты звучит как «Was macht das?».'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Welcher Satz ist grammatisch korrekt und höflich?',
    op4('Ich möchte ein Brot, bitte.', 'Ich möchte einen Brot, bitte.', 'Ich möchten ein Brot, bitte.', 'Ich möchte eine Brot, bitte.'),
    'a', '«das Brot» среднего рода, поэтому в Akkusativ артикль «ein», а möchten с ich даёт «möchte».'),

  sc(6, 'HARD', 'Die Verkäuferin fragt höflich auf «Sie». Welcher Satz ist richtig?',
    op4('Zahlst du bar oder mit Karte?', 'Zahlen Sie bar oder mit Karte?', 'Zahlt ihr bar oder mit Karte?', 'Zahle ich bar oder mit Karte?'),
    'b', 'При вежливом обращении на «Sie» глагол стоит в форме «zahlen Sie».'),

  sc(7, 'HARD', 'Wie sagt man korrekt: «Восемь евро пятьдесят»?',
    op4('Das macht acht Euro fünfzig.', 'Das machen acht Euro fünfzig.', 'Das macht achten Euro fünfzig.', 'Das macht acht Euros fünfzig.'),
    'a', 'Сумму называют «Das macht acht Euro fünfzig», где Euro в этой конструкции не меняется.'),

  sc(8, 'HARD', 'Welche höfliche Bitte um eine Tüte ist korrekt?',
    op4('Brauche Sie eine Tüte?', 'Brauchen Sie ein Tüte?', 'Brauchen Sie eine Tüte?', 'Brauchen Sie einen Tüte?'),
    'c', '«die Tüte» женского рода, поэтому в Akkusativ артикль «eine», а с «Sie» глагол «brauchen».'),

  sc(9, 'HARD', 'An der Kasse möchten Sie bezahlen. Welcher Satz ist korrekt und höflich?',
    op4('Zahlen, bitte!', 'Bezahlen ich, bitte.', 'Ich zahlst, bitte.', 'Zahle Sie, bitte.'),
    'a', 'Устойчивая формула у кассы «Zahlen, bitte!» означает «Рассчитайте, пожалуйста».'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Wörter sind höfliche Wörter?',
    op5('bitte', 'danke', 'Entschuldigung', 'Tomate', 'Kasse'),
    ['a', 'b', 'c'], 'Вежливые слова: «bitte», «danke» и «Entschuldigung».'),

  mc(11, 'EASY', 'Welche Sätze passen zum Bezahlen an der Kasse?',
    op5('Zahlen, bitte.', 'Was macht das?', 'Ich heiße Anna.', 'Das macht 5 Euro.', 'Heute ist Montag.'),
    ['a', 'b', 'd'], 'К оплате на кассе относятся «Zahlen, bitte», «Was macht das?» и «Das macht 5 Euro».'),

  mc(12, 'EASY', 'Welche Wörter bezeichnen Personen oder Orte beim Einkaufen?',
    op5('der Supermarkt', 'die Verkäuferin', 'der Kunde', 'schlafen', 'gestern'),
    ['a', 'b', 'c'], 'Это супермаркет, продавщица и покупатель — места и люди при покупке.'),

  mc(13, 'EASY', 'Wie kann man höflich um etwas bitten?',
    op6('Ich hätte gern …', 'Ich möchte bitte …', 'Gib mir sofort …', 'Ich nehme …, bitte.', 'Du musst …', 'Schnell, los!'),
    ['a', 'b', 'd'], 'Вежливо просят формулами «Ich hätte gern …», «Ich möchte bitte …» и «Ich nehme …, bitte».'),

  mc(14, 'EASY', 'Welche Antworten passen auf «Sonst noch etwas?»?',
    op5('Nein, danke, das ist alles.', 'Ja, bitte, noch ein Brot.', 'Guten Morgen!', 'Ja, ich hätte gern Milch.', 'Tschüss!'),
    ['a', 'b', 'd'], 'На «Sonst noch etwas?» отвечают «нет, это всё» или называют ещё один товар.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Welche Sätze sind grammatisch korrekt und höflich (Sie-Form)?',
    op5('Möchten Sie eine Tüte?', 'Haben Sie Tomaten?', 'Möchtest Sie eine Tüte?', 'Was nehmen Sie?', 'Haben du Tomaten?'),
    ['a', 'b', 'd'], 'При обращении на «Sie» глагол в форме «möchten/haben/nehmen Sie».'),

  mc(16, 'HARD', 'Welche Sätze enthalten den richtigen Artikel im Akkusativ?',
    op6('Ich möchte eine Tüte.', 'Ich nehme den Käse.', 'Ich möchte einen Tüte.', 'Ich nehme das Brot.', 'Ich nehme die Käse.', 'Ich möchte ein Tüte.'),
    ['a', 'b', 'd'], '«die Tüte» → «eine», «der Käse» → «den», «das Brot» → «das» в Akkusativ.'),

  mc(17, 'HARD', 'Welche Wörter haben das richtige Geschlecht (Artikel)?',
    op5('die Kasse', 'die Tüte', 'das Kasse', 'der Markt', 'die Markt'),
    ['a', 'b', 'd'], 'Правильно: «die Kasse», «die Tüte» и «der Markt».'),

  mc(18, 'HARD', 'Welche Fragen kann die Verkäuferin höflich stellen?',
    op6('Brauchen Sie eine Tüte?', 'Zahlen Sie bar oder mit Karte?', 'Brauchst du Tüte?', 'Sonst noch etwas?', 'Zahlst du bar?', 'Was nehmst Sie?'),
    ['a', 'b', 'd'], 'Корректные вежливые вопросы продавца — с глаголом во форме на «Sie».'),

  mc(19, 'HARD', 'Welche Aussagen über das Bezahlen sind korrekt formuliert?',
    op5('Ich zahle bar.', 'Ich zahle mit Karte.', 'Ich zahle mit bar.', 'Ich bezahle bar.', 'Ich zahle die Karte.'),
    ['a', 'b', 'd'], 'Говорят «bar» без предлога, «mit Karte» с предлогом; «zahlen» и «bezahlen» оба верны.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Я хотел бы хлеб».',
    items4('Ich', 'hätte', 'gern', 'Brot'),
    'Порядок: «Ich hätte gern Brot» — устойчивая вежливая просьба.'),

  ord(21, 'EASY', 'Соберите немецкое предложение: «Что-нибудь ещё?».',
    items4('Sonst', 'noch', 'etwas', '?'),
    'Реплика продавца «Sonst noch etwas?» — «Что-нибудь ещё?».'),

  ord(22, 'EASY', 'Соберите немецкое предложение: «Сколько с меня?».',
    items4('Was', 'macht', 'das', '?'),
    'Вопрос о сумме «Was macht das?» — «Сколько с меня?».'),

  ord(23, 'EASY', 'Соберите немецкое предложение: «Нет, спасибо, это всё».',
    items5('Nein', 'danke', 'das', 'ist', 'alles'),
    'Завершающая фраза «Nein, danke, das ist alles» — «Нет, спасибо, это всё».'),

  ord(24, 'EASY', 'Соберите немецкое предложение: «Платите, пожалуйста».',
    items4('Zahlen', 'Sie', 'bitte', '!'),
    'Вежливая просьба у кассы «Zahlen Sie bitte!».'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Вы платите наличными или картой?».',
    items5('Zahlen', 'Sie', 'bar', 'oder', 'mit Karte?'),
    'V2-вопрос на «Sie»: «Zahlen Sie bar oder mit Karte?».'),

  ord(26, 'HARD', 'Соберите немецкое предложение: «Вам нужен пакет?».',
    items4('Brauchen', 'Sie', 'eine', 'Tüte?'),
    'Вопрос продавца «Brauchen Sie eine Tüte?» с глаголом на первом месте.'),

  ord(27, 'HARD', 'Соберите немецкое предложение: «Я хотел бы килограмм помидоров».',
    items5('Ich', 'hätte', 'gern', 'ein Kilo', 'Tomaten'),
    'Вежливая просьба «Ich hätte gern ein Kilo Tomaten».'),

  ord(28, 'HARD', 'Соберите немецкое предложение: «Это стоит восемь евро пятьдесят».',
    items5('Das', 'macht', 'acht', 'Euro', 'fünfzig'),
    'Называние суммы «Das macht acht Euro fünfzig».'),

  ord(29, 'HARD', 'Соберите немецкое предложение: «Извините, где касса?».',
    items5('Entschuldigung', 'wo', 'ist', 'die', 'Kasse?'),
    'Вежливый вопрос «Entschuldigung, wo ist die Kasse?» с глаголом на втором месте.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Ich hätte ___ ein Kilo Tomaten.',
    [{ id: 'b1', correctCandidateId: 'c1' }],
    cand5('gern', 'gestern', 'gegen', 'genug', 'gleich'),
    'В формуле «Ich hätte gern …» нужно слово «gern».'),

  fb(31, 'EASY', '___ noch etwas?',
    [{ id: 'b1', correctCandidateId: 'c2' }],
    cand5('Wer', 'Sonst', 'Wo', 'Wie', 'Wann'),
    'Реплика продавца начинается со слова «Sonst».'),

  fb(32, 'EASY', 'Nein, danke, das ist ___.',
    [{ id: 'b1', correctCandidateId: 'c3' }],
    cand5('viel', 'klein', 'alles', 'gut', 'neu'),
    'Завершающая фраза «das ist alles» — «это всё».'),

  fb(33, 'EASY', 'Brauchen ___ eine Tüte?',
    [{ id: 'b1', correctCandidateId: 'c4' }],
    cand5('ich', 'du', 'er', 'Sie', 'es'),
    'Вежливый вопрос продавца — на «Sie»: только с «Sie» глагол имеет форму «brauchen».'),

  fb(34, 'EASY', 'Was macht ___?',
    [{ id: 'b1', correctCandidateId: 'c5' }],
    cand5('du', 'wir', 'ich', 'Sie', 'das'),
    'Вопрос о цене звучит «Was macht das?».'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Ich möchte bitte ___ Brot.',
    [{ id: 'b1', correctCandidateId: 'c1' }],
    cand5('ein', 'eine', 'einen', 'einem', 'der'),
    '«das Brot» среднего рода, в Akkusativ артикль «ein».'),

  fb(36, 'HARD', 'Ich nehme ___ Käse.',
    [{ id: 'b1', correctCandidateId: 'c2' }],
    cand5('das', 'den', 'die', 'dem', 'des'),
    '«der Käse» мужского рода, в Akkusativ становится «den».'),

  fb(37, 'HARD', 'Zahlen Sie bar ___ mit Karte?',
    [{ id: 'b1', correctCandidateId: 'c3' }],
    cand5('und', 'aber', 'oder', 'denn', 'sondern'),
    'Выбор между двумя вариантами выражает союз «oder».'),

  fb(38, 'HARD', 'Ich möchte ___ Tüte, bitte.',
    [{ id: 'b1', correctCandidateId: 'c4' }],
    cand5('ein', 'einen', 'einem', 'eine', 'der'),
    '«die Tüte» женского рода, в Akkusativ артикль «eine».'),

  fb(39, 'HARD', '___ Sie, wo ist die Kasse?',
    [{ id: 'b1', correctCandidateId: 'c5' }],
    cand5('Danke', 'Bitte', 'Tschüss', 'Hallo', 'Entschuldigen'),
    'Вежливое обращение к продавцу «Entschuldigen Sie, …».'),
];
