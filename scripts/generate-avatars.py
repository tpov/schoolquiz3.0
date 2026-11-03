#!/usr/bin/env python3
"""Generates the 8 NOIR avatar images as PNGs (pure stdlib), then a base64 JS module for functions."""

import base64
import struct
import zlib
import os
import sys

SIZE = 256
BG = (10, 10, 12)  # #0A0A0C — NoirBgDeep-ish

def blank():
    return [[BG for _ in range(SIZE)] for _ in range(SIZE)]

def put(px, x, y, c):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        px[y][x] = c

def fill_poly(px, pts, c):
    ys = [p[1] for p in pts]
    y0, y1 = max(0, min(ys)), min(SIZE - 1, max(ys))
    for y in range(y0, y1 + 1):
        xs = []
        for i in range(len(pts)):
            x1, y1p = pts[i]
            x2, y2p = pts[(i + 1) % len(pts)]
            if (y1p <= y < y2p) or (y2p <= y < y1p):
                xs.append(x1 + (y - y1p) * (x2 - x1) / (y2p - y1p))
        xs.sort()
        for k in range(0, len(xs) - 1, 2):
            xa, xb = int(xs[k]), int(xs[k + 1])
            for x in range(xa, xb + 1):
                put(px, x, y, c)

def fill_ellipse(px, cx, cy, rx, ry, c, outline=None, ow=0):
    for y in range(cy - ry, cy + ry + 1):
        for x in range(cx - rx, cx + rx + 1):
            if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1:
                put(px, x, y, c)
    if outline is not None:
        for y in range(cy - ry, cy + ry + 1):
            for x in range(cx - rx, cx + rx + 1):
                if abs(((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 - 1) <= 0.14:
                    put(px, x, y, outline)

def fill_rect(px, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(px, x, y, c)

def line(px, x0, y0, x1, y1, c, w=1):
    steps = max(abs(x1 - x0), abs(y1 - y0))
    for i in range(steps + 1):
        t = i / steps
        for dx in range(-w // 2, w // 2 + 1):
            for dy in range(-w // 2, w // 2 + 1):
                put(px, int(x0 + (x1 - x0) * t) + dx, int(y0 + (y1 - y0) * t) + dy, c)

def encode(px):
    rows = b""
    for y in range(SIZE):
        rows += b"\x00" + bytes(v for pixel in px[y] for v in pixel)
    return zlib.compress(rows, 9)

def png_bytes(px):
    ihdr = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 2, 0, 0, 0)
    return (
        b"\x89PNG\r\n\x1a\n"
        + struct.pack(">I", 13) + b"IHDR" + ihdr + struct.pack(">I", zlib.crc32(b"IHDR" + ihdr))
        + struct.pack(">I", len(encode(px))) + b"IDAT" + encode(px) + struct.pack(">I", zlib.crc32(b"IDAT" + encode(px)))
        + struct.pack(">I", 0) + b"IEND" + struct.pack(">I", zlib.crc32(b"IEND"))
    )

def cx(x): return SIZE // 2 + x
def cy(y): return SIZE // 2 + y

# --- icons ---------------------------------------------------------------

def crown(px):
    gold = (255, 215, 0)
    fill_rect(px, cx(-92), cy(38), cx(92), cy(66), gold)
    fill_poly(px, [(cx(-92), cy(38)), (cx(-108), cy(-42)), (cx(-52), cy(10)), (cx(-8), cy(-66)), (cx(28), cy(-28))], gold)
    fill_poly(px, [(cx(-52), cy(10)), (cx(-28), cy(-34)), (cx(8), cy(-10))], gold)
    fill_poly(px, [(cx(28), cy(-28)), (cx(64), cy(-48)), (cx(92), cy(-38)), (cx(92), cy(38))], gold)
    fill_rect(px, cx(-24), cy(66), cx(-8), cy(84), gold)
    fill_rect(px, cx(28), cy(66), cx(44), cy(84), gold)

def star(px):
    gold = (255, 215, 0)
    fill_poly(px, [
        (cx(0), cy(-96)), (cx(26), cy(-34)), (cx(92), cy(-34)), (cx(38), cy(4)),
        (cx(58), cy(66),), (cx(0), cy(28)), (cx(-58), cy(66)), (cx(-38), cy(4)),
        (cx(-92), cy(-34)), (cx(-26), cy(-34)),
    ], gold)

def wings(px):
    orange = (255, 122, 69)
    # left wing: three feathers
    fill_poly(px, [(cx(-8), cy(-20)), (cx(-110), cy(-84)), (cx(-104), cy(-34)), (cx(-156), cy(-40)), (cx(-120), cy(6)), (cx(-70), cy(4))], orange)
    fill_poly(px, [(cx(-70), cy(4)), (cx(-96), cy(66)), (cx(-58), cy(52)), (cx(-34), cy(40))], orange)
    # right wing mirrored
    fill_poly(px, [(cx(8), cy(-20)), (cx(110), cy(-84)), (cx(104), cy(-34)), (cx(156), cy(-40)), (cx(120), cy(6)), (cx(70), cy(4))], orange)
    fill_poly(px, [(cx(70), cy(4)), (cx(96), cy(66)), (cx(58), cy(52)), (cx(34), cy(40))], orange)
    fill_ellipse(px, cx(0), cy(10), 34, 44, (255, 210, 120))

def scales(px):
    green = (92, 201, 122)
    dark = (48, 130, 74)
    rows_y = [-56, -8, 40, 88]
    for r, y0 in enumerate(rows_y):
        step = 44
        start = cx(-92) + (22 if r % 2 else 0)
        for x0 in range(start, cx(92), step):
            fill_poly(px, [(x0, y0), (x0 + 22, y0 - 26), (x0 + 44, y0), (x0 + 44, y0 + 26), (x0, y0 + 26)], green if (x0 - start) % (2 * step) == 0 else dark)

def orb(px):
    cyan = (77, 209, 232)
    fill_ellipse(px, cx(0), cy(0), 78, 86, cyan)
    fill_ellipse(px, cx(0), cy(0), 66, 72, (20, 120, 140))
    fill_ellipse(px, cx(-22), cy(-30), 18, 26, (200, 245, 252))
    line(px, cx(-52), cy(-58), cx(0), cy(0), cyan, 6)
    line(px, cx(0), cy(0), cx(48), cy(56), cyan, 6)
    line(px, cx(-34), cy(58), cx(30), cy(-44), cyan, 4)

def bolt(px):
    yellow = (255, 215, 0)
    fill_poly(px, [(cx(20), cy(-96)), (cx(-44), cy(20)), (cx(-2), cy(20)), (cx(-20), cy(96)), (cx(52), cy(-28)), (cx(6), cy(-28))], yellow)

def eye(px):
    violet = (150, 128, 242)
    light = (196, 180, 250)
    fill_ellipse(px, cx(0), cy(0), 92, 58, (30, 26, 52), outline=violet, ow=3)
    fill_ellipse(px, cx(0), cy(0), 46, 46, (60, 48, 120))
    fill_ellipse(px, cx(0), cy(0), 22, 22, (16, 14, 30))
    fill_ellipse(px, cx(-30), cy(-16), 12, 8, light)
    # lashes
    for (x0, y0, x1, y1) in [(-70, -40, -96, -62), (-30, -52, -36, -84), (14, -54, 22, -88), (58, -42, 86, -56)]:
        line(px, cx(x0), cy(y0), cx(x1), cy(y1), violet, 5)

def shield(px):
    azure = (5, 153, 239)
    inner = (90, 200, 250)
    pts = [
        (cx(-84), cy(-70)), (cx(84), cy(-70)), (cx(84), cy(0)),
        (cx(60), cy(52)), (cx(0), cy(92)), (cx(-60), cy(52)), (cx(-84), cy(0)),
    ]
    fill_poly(px, pts, azure)
    pts2 = [
        (cx(-64), cy(-52)), (cx(64), cy(-52)), (cx(64), cy(0)),
        (cx(44), cy(38)), (cx(0), cy(68)), (cx(-44), cy(38)), (cx(-64), cy(0)),
    ]
    fill_poly(px, pts2, (8, 60, 110))
    fill_poly(px, [(cx(-18), cy(10)), (cx(0), cy(-34)), (cx(18), cy(10)), (cx(0), cy(44))], inner)

ICONS = {
    "Golden Crown Logo": crown,
    "Diamond Star Logo": star,
    "Phoenix Wings Logo": wings,
    "Dragon Scale Logo": scales,
    "Crystal Orb Logo": orb,
    "Thunder Bolt Logo": bolt,
    "Mystic Eye Logo": eye,
    "Royal Shield Logo": shield,
}

def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else "/tmp/noir-avatars"
    os.makedirs(out_dir, exist_ok=True)
    module = []
    module.append("// Generated by scripts/generate-avatars.py — the NOIR avatar pool.")
    module.append("// Served by the logoImage HTTP function; do not edit by hand.")
    module.append('"use strict";')
    module.append("const LOGO_IMAGES = {")
    for name, draw in ICONS.items():
        px = blank()
        draw(px)
        raw = png_bytes(px)
        safe = name.replace(" ", "_")
        path = os.path.join(out_dir, f"{safe}.png")
        with open(path, "wb") as f:
            f.write(raw)
        module.append(f'  "{name}": Buffer.from("{base64.b64encode(raw).decode()}", "base64"),')
        print(f"{name}: {len(raw)} bytes")
    module.append("};")
    module.append("module.exports = { LOGO_IMAGES };")
    with open(os.path.join(out_dir, "logo-images.js"), "w") as f:
        f.write("\n".join(module))
    print("total:", sum(os.path.getsize(os.path.join(out_dir, f"{n.replace(' ', '_')}.png")) for n in ICONS), "bytes PNG + module written")

if __name__ == "__main__":
    main()
