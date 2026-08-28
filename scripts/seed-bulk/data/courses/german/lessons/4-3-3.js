'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY =====
  sc(0, 'EASY', 'Wie sagt man «аптека» auf Deutsch?', op4('die Apotheke', 'das Krankenhaus', 'die Bibliothek', 'der Bahnhof'), 'a', 'Слово die Apotheke (женский род) означает «аптека».'),
  sc(1, 'EASY', 'Welches Wort bedeutet «таблетка»?', op4('der Hustensaft', 'die Tablette', 'die Salbe', 'das Rezept'), 'b', 'Die Tablette — это «таблетка».'),
  sc(2, 'EASY', 'Ergänze: «Sie ___ dreimal täglich eine Tablette einnehmen.» (совет врача)', op4('können', 'wollen', 'sollen', 'mögen'), 'c', 'Модальный глагол sollen выражает совет или предписание врача.'),
  sc(3, 'EASY', 'Was heißt «das Rezept»?', op4('доза', 'рецепт', 'мазь', 'кашель'), 'b', 'Das Rezept — это «рецепт» (на лекарство).'),
  sc(4, 'EASY', 'Ergänze: «Du ___ die Tabletten nehmen, sonst wirst du nicht gesund.» (необходимость)', op4('musst', 'darfst', 'magst', 'willst'), 'a', 'Глагол müssen (du musst) выражает необходимость, обязанность.'),

  // ===== 5 sc HARD =====
  sc(5, 'HARD', 'Welcher Satz drückt ein ärztliches Verbot korrekt aus?', op4('Du sollst keinen Alkohol trinken dürfen.', 'Du darfst keinen Alkohol trinken.', 'Du musst keinen Alkohol trinken.', 'Du kannst keinen Alkohol nicht trinken.'), 'b', 'Запрет «нельзя» выражается через nicht/kein + dürfen: Du darfst keinen Alkohol trinken.'),
  sc(6, 'HARD', 'Wähle die korrekte Rahmenkonstruktion (Satzklammer).', op4('Sie einnehmen sollen die Tablette nach dem Essen.', 'Sie sollen nach dem Essen die Tablette einnehmen.', 'Sie sollen einnehmen die Tablette nach dem Essen.', 'Einnehmen Sie sollen die Tablette nach dem Essen.'), 'b', 'Спрягаемый модальный глагол стоит на 2-й позиции, а инфинитив einnehmen — в конце.'),
  sc(7, 'HARD', 'Ergänze richtig: «Der Arzt hat mir einen Hustensaft ___.»', op4('verschreiben', 'verschreibt', 'verschrieben', 'verschreibst'), 'c', 'В перфекте нужна форма Partizip II: verschrieben («выписал»).'),
  sc(8, 'HARD', 'Welcher Satz mit trennbarem Verb «einnehmen» ist korrekt?', op4('Nehmen Sie die Tabletten nach dem Essen ein.', 'Einnehmen Sie die Tabletten nach dem Essen.', 'Nehmen ein Sie die Tabletten nach dem Essen.', 'Sie einnehmen die Tabletten nach dem Essen.'), 'a', 'У отделяемого глагола einnehmen приставка ein- уходит в конец предложения.'),
  sc(9, 'HARD', 'Ergänze: «Diese Salbe hat fast keine ___.»', op4('Nebenwirkungen', 'Nebenwirkung', 'Nebenwirkungs', 'Nebenwirken'), 'a', 'Множественное число от die Nebenwirkung — die Nebenwirkungen («побочные действия»).'),

  // ===== 5 mc EASY =====
  mc(10, 'EASY', 'Welche Wörter gehören zur Apotheke und Behandlung? (mehrere richtig)', op5('die Tablette', 'der Hustensaft', 'das Fahrrad', 'die Salbe', 'der Computer'), ['a', 'b', 'd'], 'Tablette, Hustensaft и Salbe относятся к аптеке и лечению.'),
  mc(11, 'EASY', 'Welche Modalverben passen zu Empfehlungen und Vorschriften? (mehrere richtig)', op5('sollen', 'müssen', 'tanzen', 'dürfen', 'singen'), ['a', 'b', 'd'], 'Модальные глаголы sollen, müssen и dürfen дают советы и предписания.'),
  mc(12, 'EASY', 'Welche Aussagen sind sinnvolle Empfehlungen bei Krankheit? (mehrere richtig)', op5('Sie sollen viel trinken.', 'Sie sollen im Bett bleiben.', 'Sie sollen schnell rennen.', 'Sie sollen sich ausruhen.', 'Sie sollen kalt duschen.'), ['a', 'b', 'd'], 'Пить много, оставаться в постели и отдыхать — разумные советы при болезни.'),
  mc(13, 'EASY', 'Welche Zeitangaben passen zur Dosierung? (mehrere richtig)', op5('dreimal täglich', 'vor dem Essen', 'sehr schön', 'nach dem Essen', 'ganz blau'), ['a', 'b', 'd'], 'Dreimal täglich, vor dem Essen и nach dem Essen описывают кратность и время приёма.'),
  mc(14, 'EASY', 'Welche Wörter sind Substantive (groß geschrieben, mit Artikel)? (mehrere richtig)', op5('die Dosis', 'das Medikament', 'einnehmen', 'die Apotheke', 'müssen'), ['a', 'b', 'd'], 'Dosis, Medikament и Apotheke — существительные с артиклем.'),

  // ===== 5 mc HARD =====
  mc(15, 'HARD', 'Welche Sätze sind grammatisch korrekte Empfehlungen? (mehrere richtig)', op6('Sie sollen viel Tee trinken.', 'Du musst dich ausruhen.', 'Du darfst nicht rausgehen.', 'Sie sollen trinken viel Tee.', 'Du ausruhen musst dich.', 'Du darfst rausgehen nicht.'), ['a', 'b', 'c'], 'В первых трёх инфинитив или приставка стоит в конце — рамка соблюдена.'),
  mc(16, 'HARD', 'In welchen Sätzen steht das Modalverb korrekt auf Position 2? (mehrere richtig)', op6('Heute sollst du im Bett bleiben.', 'Nach dem Essen müssen Sie die Tablette einnehmen.', 'Du darfst keinen Sport machen.', 'Im Bett du sollst heute bleiben.', 'Die Tablette nach dem Essen Sie müssen einnehmen.', 'Keinen Sport du darfst machen.'), ['a', 'b', 'c'], 'В немецком главном предложении спрягаемый глагол всегда на 2-й позиции.'),
  mc(17, 'HARD', 'Welche Formen sind korrekte Pluralformen? (mehrere richtig)', op6('die Tabletten', 'die Nebenwirkungen', 'die Rezepte', 'die Tablettes', 'die Nebenwirkungs', 'die Rezepts'), ['a', 'b', 'c'], 'Tabletten, Nebenwirkungen и Rezepte — правильные формы множественного числа.'),
  mc(18, 'HARD', 'Welche Sätze drücken ein Verbot («нельзя») korrekt aus? (mehrere richtig)', op6('Du darfst keinen Alkohol trinken.', 'Sie dürfen hier nicht rauchen.', 'Du darfst nicht rausgehen.', 'Du musst keinen Alkohol trinken.', 'Du sollst Alkohol trinken.', 'Sie dürfen hier rauchen.'), ['a', 'b', 'c'], 'Запрет передаётся через dürfen с отрицанием nicht или kein.'),
  mc(19, 'HARD', 'Welche Imperativsätze (Sie-Form) sind korrekt gebildet? (mehrere richtig)', op6('Trinken Sie viel Tee!', 'Bleiben Sie im Bett!', 'Nehmen Sie die Tabletten ein!', 'Sie trinken viel Tee!', 'Ein nehmen Sie die Tabletten!', 'Bleiben im Bett Sie!'), ['a', 'b', 'c'], 'В вежливом императиве глагол стоит первым, а Sie — сразу после него.'),

  // ===== 5 ord EASY =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Вам следует много пить».', items4('Sie', 'sollen', 'viel', 'trinken'), 'Модальный глагол sollen на 2-й позиции, инфинитив trinken — в конце.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Ты должен отдохнуть».', items4('Du', 'musst', 'dich', 'ausruhen'), 'Глагол müssen стоит на 2-й позиции, инфинитив ausruhen — в конце.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Пейте много чая!».', items4('Trinken', 'Sie', 'viel', 'Tee'), 'В вежливом императиве глагол Trinken стоит на первом месте.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «У меня есть рецепт».', items4('Ich', 'habe', 'ein', 'Rezept'), 'Глагол habe стоит на 2-й позиции в утвердительном предложении.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Оставайтесь в постели!».', items4('Bleiben', 'Sie', 'im', 'Bett'), 'В императиве с Sie глагол Bleiben идёт первым.'),

  // ===== 5 ord HARD =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Вам следует принимать таблетку три раза в день».', items5('Sie', 'sollen', 'dreimal täglich', 'eine Tablette', 'einnehmen'), 'Модальный глагол sollen на 2-й позиции, отделяемый инфинитив einnehmen — в самом конце.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Тебе нельзя пить алкоголь».', items5('Du', 'darfst', 'keinen', 'Alkohol', 'trinken'), 'Запрет с dürfen + kein: модальный глагол на 2-й позиции, инфинитив trinken — в конце.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Принимайте, пожалуйста, таблетки после еды».', items5('Nehmen', 'Sie', 'die Tabletten bitte', 'nach dem Essen', 'ein'), 'Отделяемая приставка ein у глагола einnehmen уходит в самый конец предложения; bitte стоит после дополнения.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Врач выписал мне сироп от кашля».', items5('Der Arzt', 'hat', 'mir', 'einen Hustensaft', 'verschrieben'), 'В перфекте hat стоит на 2-й позиции, а Partizip II verschrieben — в конце.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Тебе нужно сейчас отдохнуть».', items5('Du', 'musst', 'dich', 'jetzt', 'ausruhen'), 'Глагол müssen на 2-й позиции, возвратный инфинитив ausruhen — в конце.'),

  // ===== 5 fb EASY =====
  fb(30, 'EASY', 'Ich kaufe das Medikament in ___ Apotheke.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('dem', 'der', 'den', 'das', 'die'), 'После предлога in (где?) с женским словом Apotheke нужен Dativ: in der Apotheke.'),
  fb(31, 'EASY', 'Sie ___ viel Wasser trinken.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('soll', 'sollst', 'sollen', 'sollt', 'sollen wir'), 'С вежливым Sie глагол sollen имеет форму sollen.'),
  fb(32, 'EASY', 'Du ___ die Tabletten nehmen.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('musst', 'muss', 'müssen', 'müsst', 'müsse'), 'Со 2-м лицом единственного числа du форма müssen — musst.'),
  fb(33, 'EASY', '___ Salbe hilft gegen Schmerzen.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Der', 'Das', 'Den', 'Die', 'Dem'), 'Существительное Salbe женского рода, в Nominativ нужен артикль die.'),
  fb(34, 'EASY', 'Der Arzt gibt mir ___ Rezept.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('der', 'die', 'dem', 'den', 'ein'), 'Das Rezept в Akkusativ среднего рода с неопределённым артиклем — ein Rezept.'),

  // ===== 5 fb HARD =====
  fb(35, 'HARD', 'Du ___ keinen Alkohol trinken. (запрет)', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('darf', 'darfst', 'dürfen', 'dürft', 'darfen'), 'Запрет «нельзя» выражается через dürfen; со 2-м лицом du форма — darfst.'),
  fb(36, 'HARD', 'Nehmen Sie die Tabletten nach dem Essen ___. (einnehmen)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('an', 'auf', 'ein', 'aus', 'mit'), 'Отделяемая приставка глагола einnehmen — ein, она стоит в конце предложения.'),
  fb(37, 'HARD', 'Sie sollen dreimal ___ eine Tablette einnehmen.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('täglich', 'Tag', 'die Tage', 'tagen', 'Tages'), 'Кратность «три раза в день» передаётся словом täglich: dreimal täglich.'),
  fb(38, 'HARD', 'Der Arzt hat mir einen Hustensaft ___.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('verschreibt', 'verschreiben', 'verschreibst', 'verschrieben', 'verschreibe'), 'В перфекте нужна форма Partizip II — verschrieben.'),
  fb(39, 'HARD', 'Diese Salbe hat fast keine ___. (мн. число)', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Nebenwirkungs', 'Nebenwirkunges', 'Nebenwirken', 'Nebenwirkt', 'Nebenwirkungen'), 'Множественное число от die Nebenwirkung — die Nebenwirkungen.'),
];
