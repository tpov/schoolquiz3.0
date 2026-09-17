#!/usr/bin/env bash
#
# Тыкать по тексту, а не по координатам.
#
# У пяти разных телефонов разные экраны, и координаты с одного не годятся для
# другого. Здесь всё ищется по подписи в дереве элементов, поэтому один и тот же
# вызов работает на планшете и на телефоне одинаково.
#
#   ui.sh <serial> find   <текст>        — есть ли такой элемент (код 0/1)
#   ui.sh <serial> tap    <текст>        — нажать по центру элемента
#   ui.sh <serial> longtap <текст>       — долгое нажатие
#   ui.sh <serial> type   <текст>        — ввести текст в текущее поле
#   ui.sh <serial> text                  — весь видимый текст экрана
#   ui.sh <serial> shot   <файл.png>     — снимок
#   ui.sh <serial> back                  — назад
#
# Поиск — по подстроке, регистр не важен. Если совпадений несколько, берётся
# первое сверху.
set -u

ADB="${ADB:-/opt/homebrew/bin/adb}"

SERIAL="${1:?нужен serial устройства}"
CMD="${2:?нужна команда}"
ARG="${3:-}"
PKG="${PKG:-com.tpov.schoolquiz}"

dump() {
  "$(dirname "$0")/dump.sh" "$SERIAL" "${DUMP_TIMEOUT:-20}"
}

# Печатает "x y" центра первого элемента, чей text или content-desc содержит ARG.
# Показана ли экранная клавиатура. Нужна, потому что открытая клавиатура
# прокручивает форму вверх и закрывает нижние поля.
kb_shown() {
  "$ADB" -s "$SERIAL" shell dumpsys input_method 2>/dev/null \
    | grep -m1 -oE "mInputShown=(true|false)" | cut -d= -f2 | tr -d '\r'
}

# Закрыть клавиатуру и дать форме доехать назад.
# "Назад" отправляется ТОЛЬКО когда клавиатура показана, иначе уйдём с экрана.
hide_kb() {
  if [ "$(kb_shown)" = "true" ]; then
    "$ADB" -s "$SERIAL" shell input keyevent 4 >/dev/null 2>&1
    sleep "${SETTLE:-2}"
  fi
}

# Печатает "x y" центра подходящего элемента.
# Порядок предпочтения: точное совпадение text -> точное content-desc ->
# подстрока. Внутри одного разряда берётся первый сверху. Это важно: подписи
# вроде "Квест" и "Квесты" различаются только точным совпадением.
locate() {
  dump | ARG="$1" python3 -c '
import sys, os, re

needle = os.environ["ARG"]
low = needle.lower()
xml = sys.stdin.read()
exact_text, exact_desc, partial = [], [], []

for node in re.findall(r"<node[^>]*?/?>", xml):
    m = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", node)
    if not m:
        continue
    x1, y1, x2, y2 = map(int, m.groups())
    if x2 <= x1 or y2 <= y1:
        continue          # элемент не разложен (нулевой размер) — нажать нельзя
    t = (re.search(r"text=\"([^\"]*)\"", node) or [None, ""])[1] if re.search(r"text=\"([^\"]*)\"", node) else ""
    d = (re.search(r"content-desc=\"([^\"]*)\"", node) or [None, ""])[1] if re.search(r"content-desc=\"([^\"]*)\"", node) else ""
    center = ((x1 + x2) // 2, (y1 + y2) // 2)
    if t == needle:
        exact_text.append(center)
    elif d == needle:
        exact_desc.append(center)
    elif low and (low in t.lower() or low in d.lower()):
        partial.append(center)

for bucket in (exact_text, exact_desc, partial):
    if bucket:
        print(bucket[0][0], bucket[0][1])
        break
'
}

case "$CMD" in
  find)
    coords=$(locate "$ARG")
    if [ -n "$coords" ]; then echo "найдено: $ARG ($coords)"; exit 0; fi
    echo "НЕ найдено: $ARG"; exit 1
    ;;
  tap|longtap)
    coords=$(locate "$ARG")
    if [ -z "$coords" ]; then echo "НЕ найдено: $ARG"; exit 1; fi
    set -- $coords
    if [ "$CMD" = "tap" ]; then
      "$ADB" -s "$SERIAL" shell input tap "$1" "$2"
    else
      "$ADB" -s "$SERIAL" shell input swipe "$1" "$2" "$1" "$2" 900
    fi
    echo "нажато: $ARG ($1 $2)"
    ;;
  type)
    # Пробелы в adb input text — через %s, иначе текст обрежется.
    # Только латиница: input text не передаёт кириллицу (нужен Unicode-IME).
    "$ADB" -s "$SERIAL" shell input text "$(printf '%s' "$ARG" | sed 's/ /%s/g')"
    echo "введено: $ARG"
    ;;
  hidekb)
    hide_kb
    echo "клавиатура: $(kb_shown)"
    ;;
  fill)
    # fill <подпись> <значение> — нажать по подписи поля, ввести значение,
    # закрыть клавиатуру. Собирает в себя три грабли, найденные на прогоне:
    #   1) форма прокручивается при появлении клавиатуры, поэтому перед
    #      нажатием клавиатура закрывается и ждётся конец прокрутки;
    #   2) координаты берутся из свежего дампа уже после того, как всё встало;
    #   3) поле сначала очищается, иначе ввод дописывается к прежнему значению.
    VALUE="${4:?нужно значение}"
    hide_kb
    coords=$(locate "$ARG")
    if [ -z "$coords" ]; then echo "НЕ найдено поле: $ARG"; exit 1; fi
    set -- $coords
    "$ADB" -s "$SERIAL" shell input tap "$1" "$2"
    sleep 1
    for _ in $(seq 1 40); do
      "$ADB" -s "$SERIAL" shell input keyevent 67 >/dev/null 2>&1
    done
    "$ADB" -s "$SERIAL" shell input text "$(printf '%s' "$VALUE" | sed 's/ /%s/g')"
    sleep 1
    hide_kb
    echo "заполнено: $ARG = $VALUE"
    ;;
  text)
    dump | tr '>' '\n' | grep -o 'text="[^"]*"' | sed 's/text="//;s/"$//' | grep -v '^$'
    ;;
  shot)
    "$ADB" -s "$SERIAL" exec-out screencap -p > "${ARG:?нужен путь к файлу}"
    echo "снимок: $ARG"
    ;;
  back)
    "$ADB" -s "$SERIAL" shell input keyevent KEYCODE_BACK
    echo "назад"
    ;;
  *)
    echo "неизвестная команда: $CMD"; exit 2
    ;;
esac
