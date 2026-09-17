#!/usr/bin/env python3
"""Печатает опрятную таблицу элементов экрана из дампа uiautomator.

Читает XML со stdin (см. dump.sh) и показывает только то, что пригодно для
автоматизации: подписи, описания, поля ввода, кнопки и переключатели.
Элементы нулевого размера помечаются — по ним нельзя нажать, даже если текст
присутствует в дереве.

  ./dump.sh <serial> | python3 inspect.py [подстрока-фильтр]
"""
import re
import sys

INTERESTING_SUFFIXES = ("EditText", "Button", "CheckBox", "Switch", "RadioButton")


def attr(node: str, name: str) -> str:
    m = re.search(rf'{name}="([^"]*)"', node)
    return m.group(1) if m else ""


def main() -> int:
    needle = (sys.argv[1] if len(sys.argv) > 1 else "").lower()
    raw = sys.stdin.read()
    end = raw.rfind("</hierarchy>")
    if end != -1:
        raw = raw[: end + len("</hierarchy>")]

    rows = []
    for node in re.findall(r"<node[^>]*?/?>", raw):
        text = attr(node, "text")
        desc = attr(node, "content-desc")
        cls = attr(node, "class").split(".")[-1]
        if not (text or desc or cls.endswith(INTERESTING_SUFFIXES)):
            continue
        if needle and needle not in (text + " " + desc + " " + cls).lower():
            continue
        bounds = attr(node, "bounds")
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        flags = []
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            if x2 <= x1 or y2 <= y1:
                flags.append("НЕ РАЗЛОЖЕН")
            center = f"{(x1 + x2) // 2},{(y1 + y2) // 2}"
        else:
            center = "?"
        if attr(node, "clickable") == "true":
            flags.append("клик")
        if attr(node, "focused") == "true":
            flags.append("ФОКУС")
        if attr(node, "checked") == "true":
            flags.append("отмечен")
        if attr(node, "enabled") == "false":
            flags.append("выключен")
        rows.append((text, desc, cls, center, " ".join(flags)))

    if not rows:
        print("(ничего не найдено)")
        return 1

    print(f"{'ТЕКСТ':<26}{'ОПИСАНИЕ':<15}{'КЛАСС':<13}{'ЦЕНТР':<12}ПРИЗНАКИ")
    for text, desc, cls, center, flags in rows:
        print(f"{text[:25]:<26}{desc[:14]:<15}{cls[:12]:<13}{center:<12}{flags}")
    print(f"\nвсего элементов: {len(rows)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
