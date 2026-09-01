"use strict";

/**
 * Какого вида была попытка — и значит, сколько она стоит.
 *
 * Решает **сервер**, по квесту, который попытка называет (CAP-16). До сих пор единственным
 * именем того, что сыграно, был `sourceShelf`, и его вычисляло устройство: сервер брал строку из
 * payload как есть. Пока каждая попытка стоила одни и те же 33 очка, это было безобидно. Под
 * прейскурантом это вся игра: клиент, объявивший `home` для турнирного забега, платил бы 33 очка
 * вместо 500. Если вид называет клиент, а сервер по названному списывает, то самый дешёвый вид —
 * это тот, который назовёт любой клиент.
 *
 * Поэтому вид выводится из полок квеста, а полки — из документа, который клиент писать не может
 * (`quests/{id}`: create и visibleOn закрыты правилами). Объявление клиента остаётся в событии как
 * диагностика; убери его совсем — списание не изменится.
 *
 * Чистый модуль: тестируется без firebase-admin. Имена видов совпадают с ключами прейскуранта в
 * `economy-constants.js` — это один и тот же словарь, а не два похожих.
 */

const ORDINARY_LESSON = "ORDINARY_LESSON";
const ARENA = "ARENA";
const THEME_TEST = "THEME_TEST";
const FINAL_EXAM = "FINAL_EXAM";
const TOURNAMENT = "TOURNAMENT";

const PRIVATE_SCOPE = "private";

/**
 * Полки, от дорогой к дешёвой.
 *
 * Квест может стоять на нескольких полках сразу, и тогда считается по самой дорогой из них: это
 * единственное направление ошибки, которое не открывает дыру. Финал турнира выше отбора, потому
 * что финал — тот же турнир, только его нельзя перепутать с обычной полкой.
 */
const SHELF_PRECEDENCE = ["tournamentFinal", "tournament", "arena", "home", "archive"];

const KIND_BY_SHELF = {
  tournamentFinal: TOURNAMENT,
  tournament: TOURNAMENT,
  arena: ARENA,
  home: ORDINARY_LESSON,
  archive: ORDINARY_LESSON,
};

function normalizeShelf(value) {
  const raw = typeof value === "string" ? value.trim() : "";
  const lower = raw.toLowerCase();
  if (lower === "tournamentfinal") return "tournamentFinal";
  return SHELF_PRECEDENCE.includes(lower) ? lower : null;
}

/** Полки квеста в каноническом написании, без мусора и повторов. */
function shelvesOf(quest) {
  const list = quest && Array.isArray(quest.visibleOn) ? quest.visibleOn : [];
  const seen = new Set();
  for (const entry of list) {
    const shelf = normalizeShelf(entry);
    if (shelf) seen.add(shelf);
  }
  return SHELF_PRECEDENCE.filter((shelf) => seen.has(shelf));
}

/**
 * Полка, по которой попытка идёт дальше — в турнирную группу, в грязные оценки.
 *
 * Это замена клиентскому `sourceShelf` для всего, что от него зависело. Приватный квест полок не
 * имеет и остаётся `private`, как и раньше; опубликованный без полок — снятый с продажи — отдаёт
 * пустую строку, и турнирная маршрутизация его не берёт.
 *
 * @param quest документ квеста, каким его прочитал сервер, или `null`, если документа нет
 * @param scope `private` или `public` — из события, оно уже сверено с uid
 */
function sourceShelfForQuest(quest, scope) {
  if (scope === PRIVATE_SCOPE) return PRIVATE_SCOPE;
  const shelves = shelvesOf(quest);
  return shelves.length > 0 ? shelves[0] : "";
}

/**
 * Вид попытки по квесту.
 *
 * Возвращает `null`, если квеста нет: документ удалён, а попытка на него ссылается. Это не «обычный
 * урок» и не пропуск оплаты — `activityPrice` в `economy-constants.js` считает неизвестный вид по
 * самой дорогой известной ставке. Иначе выдуманный `questId` при настоящем `lessonId` получал бы
 * награду за урок по цене, которую никто не проверял.
 *
 * Контрольная и экзамен здесь **не выводятся никогда**: они не приходят этим путём. Обе — серверные
 * сессии со своими вызовами (`startThemeExam` … `finishExam` в `spec-theme-exams`), и их вид
 * известен в момент открытия сессии, а не по полке квеста. Ряды прейскуранта для них уже есть;
 * этот модуль — то место, куда их разрешение встанет, когда сессии появятся.
 */
function activityKindForQuest(quest, scope) {
  if (scope === PRIVATE_SCOPE) return ORDINARY_LESSON;
  if (!quest || typeof quest !== "object") return null;
  const shelves = shelvesOf(quest);
  if (shelves.length === 0) return ORDINARY_LESSON;
  return KIND_BY_SHELF[shelves[0]];
}

module.exports = {
  ORDINARY_LESSON,
  ARENA,
  THEME_TEST,
  FINAL_EXAM,
  TOURNAMENT,
  SHELF_PRECEDENCE,
  activityKindForQuest,
  shelvesOf,
  sourceShelfForQuest,
};
