#!/usr/bin/env python3
"""Generate vanilla-compatible Phantom Decor resourcepack for Minecraft 1.21.11.
No Pillow or external downloads are required.
Limitations: pale oak signs AND banner base textures are changed globally.
"""
from pathlib import Path
import json
import struct
import zlib
import zipfile

OUT = Path("build/PhantomDecor-ResourcePack-1.21.11.zip")
COLORS = {
    "ink": (24, 31, 54, 255),
    "cyan": (74, 215, 231, 255),
    "gold": (245, 204, 110, 255),
    "cream": (255, 240, 206, 255),
    "trans": (0, 0, 0, 0),
}

def png(width, height, rgba):
    def chunk(name, data):
        return struct.pack(">I", len(data)) + name + data + struct.pack(">I", zlib.crc32(name + data) & 0xffffffff)
    scanlines = b"".join(b"\x00" + b"".join(bytes(rgba[y * width + x]) for x in range(width)) for y in range(height))
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(scanlines, 9)) + chunk(b"IEND", b"")

def icon(kind):
    img = [COLORS["trans"]] * 256
    def px(x, y, color):
        if 0 <= x < 16 and 0 <= y < 16:
            img[y * 16 + x] = COLORS[color]
    if kind == "invisible_sign":
        for y in range(3, 12):
            for x in range(1, 15):
                px(x, y, "cyan" if x in (1,14) or y in (3,11) else "ink")
        for y in (6,8):
            for x in range(5, 11): px(x, y, "cream")
        for y in range(12, 16): px(8, y, "cyan")
    elif kind == "invisible_banner":
        for y in range(2, 14):
            for x in range(5, 12):
                px(x, y, "gold" if x in (5,11) or y in (2,13) else "cyan")
        for y in range(2, 15): px(4, y, "cream")
        for x in (7,8,9): px(x, 10, "gold")
    else:
        for y in range(2, 14):
            for x in range(2, 14):
                if x in (2,3,12,13) or y in (2,3,12,13): px(x, y, "gold")
        px(7, 7, "cyan")
    return png(16, 16, img)

entries = {
    "pack.mcmeta": json.dumps({"pack": {
        "description": "Phantom Decor 1.1 — invisible decorations (Minecraft 1.21.11)",
        "min_format": [75, 0],
        "max_format": [75, 0],
    }}, ensure_ascii=False, indent=2).encode("utf-8"),
    "assets/minecraft/textures/entity/signs/pale_oak.png":
        png(64, 32, [COLORS["trans"]] * (64 * 32)),
    "assets/minecraft/textures/entity/banner_base.png":
        png(64, 64, [COLORS["trans"]] * (64 * 64)),
    "NOTICE.txt": (
        "Minecraft Java 1.21.11. The pale oak sign texture and banner base texture "
        "are replaced globally, INCLUDING ordinary vanilla pale oak signs and "
        "banners. Other banner rendering can be affected. Text uses native sign "
        "block entity rendering; banners remain native patterned banner blocks. "
        "No client mod is necessary. Install as a vanilla resource pack.\n"
    ).encode(),
}
for name in ("invisible_sign", "invisible_banner", "invisible_frame"):
    entries[f"assets/phantomdecor/textures/item/{name}.png"] = icon(name)
    entries[f"assets/phantomdecor/models/item/{name}.json"] = json.dumps({
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"phantomdecor:item/{name}"}
    }).encode()
    entries[f"assets/phantomdecor/items/{name}.json"] = json.dumps({
        "model": {"type": "minecraft:model", "model": f"phantomdecor:item/{name}"}
    }).encode()
OUT.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(OUT, "w", compression=zipfile.ZIP_DEFLATED) as z:
    for path, data in sorted(entries.items()):
        z.writestr(path, data)
print(f"Created {OUT} ({OUT.stat().st_size} bytes, {len(entries)} files)")
