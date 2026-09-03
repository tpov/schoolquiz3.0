"use strict";

/**
 * Книга движений валют: по строке на каждое изменение баланса, которое сделал сервер.
 *
 * Зачем она нужна. Баланс сегодня меняется приращением — `FieldValue.increment(+12)` — и после
 * записи не остаётся ничего, из чего это «плюс двенадцать» можно вывести заново. Пока такой строки
 * нет, периодическая сверка (CAP-11) не может проверить нолики и очки навыка: ей не с чем
 * сравнивать. Строка и есть то, с чем.
 *
 * Что в ней лежит. Кому, когда, какая валюта, на сколько изменилась, почему (повод) и по какому
 * действию — идентификатор мутации или попытки. Повод и идентификатор обязательны: движение без
 * повода нельзя ни перепроверить, ни объяснить оператору, а без идентификатора нельзя отличить
 * одно движение от его повторной записи.
 *
 * Чего в ней нет. Ни текущего баланса, ни его пересчёта: строка описывает движение, а не итог.
 * Сверять итог с суммой движений — работа периодической функции, и именно потому она возможна.
 *
 * Строки пишет только сервер, читает только оператор: коллекция закрыта правилами целиком, как и
 * записи о перерасходе. Показать игроку, по какой именно арифметике его поймали, — значит
 * объяснить, как обходить её дальше.
 *
 * Чистый модуль: тестируется без firebase-admin.
 */

/** Куда пишутся движения. Закрыто правилами: `match /audit/{document=**}`. */
const LEDGER_PATH = "audit/currency_ledger/entries";

/**
 * Валюты, чьи движения записываются.
 *
 * Подделываемые — нолики, очки навыка, обычные заряды — те, ради которых книга и заведена (CAP-11).
 * Монетарные — золото и плазма — тоже пишутся, но не ради сверки: их сторожат по каждой операции,
 * а строка нужна затем, чтобы спор о деньгах разбирался по записи, а не по памяти.
 */
const CURRENCY = {
  NOLICS: "nolics",
  SKILL_POINTS: "skillPoints",
  STANDARD_CHARGE_POINTS: "standardChargePoints",
  PLASMA_CHARGE_POINTS: "plasmaChargePoints",
  GOLD: "gold",
  BOXES: "boxes",
};

const CURRENCIES = Object.values(CURRENCY);

/**
 * Поводы. Закрытый список: повод, которого здесь нет, — это движение, которое некому объяснить.
 *
 * Читается сверкой как правило вывода: награда выводится из попыток, плата — из прейскуранта,
 * восстановление — из часов сервера, покупка — из лестницы цен.
 */
const REASON = {
  /** Награда за прохождение: выводится из отведённого времени урока и прироста процента. */
  ATTEMPT_REWARD: "attempt_reward",
  /** Плата за попытку по прейскуранту. */
  ATTEMPT_TOLL: "attempt_toll",
  /** Подсказка или пропуск, оплаченные при расчёте заявок. */
  CHARGE_CLAIM: "charge_claim",
  /** Покупка слота заряда за нолики или золото. */
  SLOT_PURCHASE: "slot_purchase",
  /** Разблокировка урока за нолики. */
  LESSON_UNLOCK: "lesson_unlock",
  /** Начисление золота за проверенный чек. */
  GOLD_PACK: "gold_pack",
  /** Открытие коробки и её содержимое. */
  GIFT_BOX: "gift_box",
  /** Покупка или продажа на рынке ников и логотипов. */
  MARKET: "market",
};

const REASONS = Object.values(REASON);

function text(value) {
  return value === null || value === undefined ? "" : String(value);
}

function whole(value) {
  const number = Math.trunc(Number(value));
  return Number.isFinite(number) ? number : 0;
}

/**
 * Одно движение.
 *
 * Возвращает `null`, когда записывать нечего или незачем: нулевое изменение — не движение, а
 * незнакомая валюта или повод означают, что вызывающий ошибся, и молча записать это хуже, чем не
 * записать вовсе — сверка потом считала бы такую строку за настоящую.
 *
 * @param uid чей баланс
 * @param currency одно из [CURRENCY]
 * @param delta на сколько изменился; знак значим
 * @param reason одно из [REASON]
 * @param sourceId идентификатор действия: ключ мутации, попытки, покупки
 * @param atMs время сервера
 */
function ledgerEntry({uid, currency, delta, reason, sourceId, atMs, details}) {
  const owner = text(uid);
  const amount = whole(delta);
  if (!owner || amount === 0) return null;
  if (!CURRENCIES.includes(currency) || !REASONS.includes(reason)) return null;
  const source = text(sourceId);
  if (!source) return null;
  const at = Math.max(0, whole(atMs));
  return {
    // Идентификатор строки выводится, а не выдаётся: та же причина, что и у ключа идемпотентности.
    // Повторная запись того же движения — переигранная транзакция, а не второе движение.
    id: `${owner}_${currency}_${reason}_${source}`,
    uid: owner,
    currency,
    delta: amount,
    reason,
    sourceId: source,
    atMs: at,
    ...(details && typeof details === "object" ? {details} : {}),
  };
}

/**
 * Движения одного действия — сразу несколько строк, пустые отброшены.
 *
 * Одно действие двигает несколько валют: прохождение платит ноликами и очками навыка и берёт плату
 * зарядами. Собирать их по одному значило бы каждый раз повторять `uid`, повод и время.
 */
function ledgerEntries({uid, reason, sourceId, atMs, deltas, details}) {
  const rows = [];
  for (const [currency, delta] of Object.entries(deltas || {})) {
    const entry = ledgerEntry({uid, currency, delta, reason, sourceId, atMs, details});
    if (entry) rows.push(entry);
  }
  return rows;
}

/**
 * Что баланс должен был бы показывать, если сложить все движения этой валюты.
 *
 * Это и есть сверка: сумма движений против записанного баланса. Расхождение — не приговор, а
 * находка: движение могло не записаться из-за старого кода, и оператор смотрит на разницу, а не
 * на факт неравенства.
 *
 * @param entries строки книги по одной валюте, в любом порядке
 */
function derivedBalance(entries) {
  return (Array.isArray(entries) ? entries : [])
    .filter((entry) => entry && CURRENCIES.includes(entry.currency))
    .reduce((total, entry) => total + whole(entry.delta), 0);
}

/**
 * Расхождение между записанным балансом и суммой движений.
 *
 * `entries` — все строки по одной валюте за всю историю аккаунта. Частичная история даёт частичную
 * сумму, поэтому у находки есть `coversFrom`: с какого момента книга вообще велась. Разница до
 * этого момента объясняется тем, что книги не было, а не игроком.
 */
function reconcile({uid, currency, storedBalance, entries, coversFromMs, atMs}) {
  const rows = (Array.isArray(entries) ? entries : []).filter((entry) => entry && entry.currency === currency);
  const derived = derivedBalance(rows);
  const stored = whole(storedBalance);
  const earliest = rows.reduce((min, entry) => Math.min(min, whole(entry.atMs)), Number.POSITIVE_INFINITY);
  return {
    uid: text(uid),
    currency,
    storedBalance: stored,
    derivedBalance: derived,
    gap: stored - derived,
    entryCount: rows.length,
    coversFromMs: Math.max(0, whole(coversFromMs !== undefined && coversFromMs !== null ?
      coversFromMs :
      (Number.isFinite(earliest) ? earliest : 0))),
    checkedAtMs: Math.max(0, whole(atMs)),
  };
}

/** Куда пишутся находки сверки. Та же закрытая ветка, что и записи о перерасходе. */
const RECONCILIATION_PATH = "audit/currency_reconciliation/findings";

/**
 * Валюты, которые сверка проверяет.
 *
 * Только подделываемые (CAP-11): нолики, очки навыка, очки обычных зарядов. Золото и плазму сверка
 * не трогает — их сторожат по каждой операции, и лезть в них расписанием значило бы чинить деньги
 * задним числом там, где неправильное число уже неправильные деньги.
 */
const SWEPT_CURRENCIES = [CURRENCY.NOLICS, CURRENCY.SKILL_POINTS, CURRENCY.STANDARD_CHARGE_POINTS];

/**
 * Стоит ли находка того, чтобы её записать.
 *
 * Ноль — не находка. И расхождение у аккаунта, чья книга началась позже, чем он сам, — тоже: до
 * первой строки движения были, а записи о них нет, и разница объясняется этим, а не игроком.
 * `graceMs` — сколько времени после первой строки книга ещё считается неполной.
 */
function isReportable(finding, {accountCreatedAtMs, graceMs} = {}) {
  if (!finding || finding.gap === 0) return false;
  if (finding.entryCount === 0) return false;
  const created = whole(accountCreatedAtMs);
  const grace = Math.max(0, whole(graceMs));
  // Книга началась позже аккаунта — часть его истории в неё не попала.
  if (created > 0 && finding.coversFromMs > created + grace) return false;
  return true;
}

/**
 * Находка в том виде, в каком её читает оператор.
 *
 * Обоснование внутри: аккаунт, валюта, что записано, что следует из движений, разница, сколько
 * движений сложено и с какого момента книга их видит. Перечитывать книгу, чтобы понять запись, не
 * нужно — это то же правило, что и у записи о перерасходе.
 */
function reconciliationRecord(finding, atMs) {
  const at = Math.max(0, whole(atMs));
  return {
    id: `${finding.uid}_${finding.currency}_${at}`,
    uid: finding.uid,
    currency: finding.currency,
    storedBalance: finding.storedBalance,
    derivedBalance: finding.derivedBalance,
    gap: finding.gap,
    entryCount: finding.entryCount,
    coversFromMs: finding.coversFromMs,
    reason:
      `Баланс ${finding.currency} записан как ${finding.storedBalance}, а из ${finding.entryCount} ` +
      `движений следует ${finding.derivedBalance}; разница ${finding.gap}. ` +
      `Книга видит движения с ${finding.coversFromMs}.`,
    recordedAtMs: at,
  };
}

module.exports = {
  CURRENCY,
  CURRENCIES,
  LEDGER_PATH,
  RECONCILIATION_PATH,
  SWEPT_CURRENCIES,
  REASON,
  REASONS,
  derivedBalance,
  isReportable,
  ledgerEntries,
  ledgerEntry,
  reconcile,
  reconciliationRecord,
};
