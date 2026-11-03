#!/usr/bin/env bash
# локальный вариант ui.sh для рига: дамп в файл, тап по тексту/content-desc (подстрока, без регистра)
set -u; S="${1:?serial}"; C="${2:?cmd}"; A="${3:-}"
d(){ adb -s "$S" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb -s "$S" shell cat /sdcard/ui.xml; }
case "$C" in
  tap)
    XY=$(d | S="$A" python3 -c '
import sys, os, re
needle = os.environ["S"].lower(); xml = sys.stdin.read()
ex_t, ex_d, part = [], [], []
for node in re.findall(r"<node[^>]*?>", xml):
    b = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", node)
    if not b: continue
    x1,y1,x2,y2 = map(int,b.groups())
    if x2<=x1 or y2<=y1: continue
    t = (re.search(r"text=\"([^\"]*)\"", node) or [None,""])[1] if re.search(r"text=\"([^\"]*)\"", node) else ""
    de = (re.search(r"content-desc=\"([^\"]*)\"", node) or [None,""])[1] if re.search(r"content-desc=\"([^\"]*)\"", node) else ""
    c = ((x1+x2)//2,(y1+y2)//2)
    if t==needle: ex_t.append(c)
    elif de==needle: ex_d.append(c)
    elif needle in t.lower() or needle in de.lower(): part.append(c)
for b in (ex_t, ex_d, part):
    if b: print(b[0][0], b[0][1]); break
')
    [ -z "$XY" ] && { echo "НЕ найдено: $A"; exit 1; }
    adb -s "$S" shell input tap $XY && echo "нажато: $A ($XY)"
    ;;
  text) d | tr '>' '\n' | grep -o 'text="[^"]*"' | sed 's/text="//;s/"$//' | grep -v '^$' ;;
  shot) adb -s "$S" exec-out screencap -p > "${A:?файл}"; echo "снимок: $A" ;;
esac
