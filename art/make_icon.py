"""Draws the BlizzardRiptide icon as 64x64 pixel art, then scales it to 512x512.

Run:  python art/make_icon.py   ->  art/icon.png (512) and art/icon-64.png (1:1 sprite)
"""
import math
import random
from pathlib import Path
from PIL import Image

N = 64
OUT = Path(__file__).parent
random.seed(7)

img = Image.new("RGB", (N, N))
px = img.load()


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def put(x, y, c):
    x, y = int(round(x)), int(round(y))
    if 0 <= x < N and 0 <= y < N:
        px[x, y] = c


def blend(c1, c2, a):
    return tuple(round(c1[i] + (c2[i] - c1[i]) * a) for i in range(3))


# ---------------- background: dithered night-blizzard gradient ----------------
BG = [hexc(h) for h in ("#070d22", "#0a1733", "#0c2443", "#0e3352", "#10445f", "#13566b")]
BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]
for y in range(N):
    for x in range(N):
        # diagonal gradient: dark top-left -> teal bottom-right
        t = (x * 0.35 + y * 0.65) / (N - 1) * (len(BG) - 1)
        i = int(t)
        frac = t - i
        if i >= len(BG) - 1:
            i, frac = len(BG) - 2, 1.0
        px[x, y] = BG[i + 1] if frac * 16 > BAYER[y % 4][x % 4] + 0.5 else BG[i]

# ---------------- wind streaks (behind everything) ----------------
STREAK = hexc("#2b6f8a")
for _ in range(9):
    sx, sy = random.randint(-10, 60), random.randint(2, 62)
    length = random.randint(6, 14)
    for k in range(length):
        a = 1 - abs(k - length / 2) / (length / 2)
        put(sx + k, sy + k * 0.25, blend(px[min(max(int(sx + k), 0), N - 1), min(max(int(sy + k * 0.25), 0), N - 1)], STREAK, 0.6 * a))

# ---------------- geometry ----------------
TAIL = (11.0, 53.0)
TIP = (55.0, 9.0)
dx, dy = TIP[0] - TAIL[0], TIP[1] - TAIL[1]
L = math.hypot(dx, dy)
D = (dx / L, dy / L)            # along the trident
NRM = (-D[1], D[0])             # perpendicular


def along(t, off=0.0):
    """Point at fraction t along the shaft, offset `off` px sideways."""
    return (TAIL[0] + dx * t + NRM[0] * off, TAIL[1] + dy * t + NRM[1] * off)


def disc(cx, cy, r, c):
    for yy in range(int(cy - r - 1), int(cy + r + 2)):
        for xx in range(int(cx - r - 1), int(cx + r + 2)):
            if (xx - cx) ** 2 + (yy - cy) ** 2 <= r * r:
                put(xx, yy, c)


def seg(p, q, r, c):
    steps = int(math.hypot(q[0] - p[0], q[1] - p[1]) * 3) + 1
    for s in range(steps + 1):
        f = s / steps
        disc(p[0] + (q[0] - p[0]) * f, p[1] + (q[1] - p[1]) * f, r, c)


# ---------------- riptide vortex (helix around the shaft) ----------------
# Points are split into behind/in-front of the trident by the helix depth.
VORTEX_BACK = hexc("#3fa7c9")
VORTEX_MID = hexc("#8fe3f5")
VORTEX_FRONT = hexc("#f2fdff")
helix_back, helix_front = [], []
# Minecraft's Riptide effect: spinning rings around the user, wider toward the
# tail. Each ring is an ellipse: wide across the shaft, squashed along it.
RINGS = [(0.14, 9.5), (0.32, 7.2), (0.50, 5.0)]
for t, radius in RINGS:
    for s in range(720):
        theta = s / 720 * 2 * math.pi
        off = radius * math.cos(theta)
        skew = radius * 0.38 * math.sin(theta)
        x, y = along(t, off)
        x += D[0] * skew
        y += D[1] * skew
        depth = math.sin(theta)
        (helix_front if depth > 0 else helix_back).append((x, y, depth, t))
for x, y, depth, t in helix_back:
    disc(x, y, 0.55, blend(VORTEX_BACK, px[int(min(max(x, 0), N - 1)), int(min(max(y, 0), N - 1))], 0.35))

# ---------------- trident ----------------
OUTLINE = hexc("#0a1f26")
SHAFT = hexc("#2f7f86")
SHAFT_HI = hexc("#5fb9b8")
METAL = hexc("#9ff0e0")
METAL_HI = hexc("#e9fffb")
METAL_SH = hexc("#4aa7a3")
GLINT = hexc("#c9a8ff")

CROSS = 0.70
SIDE = 5.2

parts = []  # (p, q, radius, fill, highlight)
parts.append((along(0.0), along(CROSS), 1.3, SHAFT, SHAFT_HI))                  # shaft
parts.append((along(CROSS - 0.02, -SIDE), along(CROSS - 0.02, SIDE), 1.2, METAL_SH, METAL))   # crossbar
parts.append((along(CROSS), along(0.97), 1.2, METAL, METAL_HI))
# centre spearhead
parts.append((along(1.0), along(0.92, -2.0), 0.8, METAL, METAL_HI))
parts.append((along(1.0), along(0.92, 2.0), 0.8, METAL, METAL_HI))                  # centre prong
for side in (-SIDE, SIDE):
    parts.append((along(CROSS - 0.02, side), along(0.90, side), 1.1, METAL, METAL_HI))  # side prongs
    sgn = 1 if side > 0 else -1
    parts.append((along(0.90, side), along(0.85, side + sgn * 2.0), 0.7, METAL, METAL_HI))  # outward barb

# outlines first, then fills, then a 1px highlight line
for p, q, r, _, _ in parts:
    seg(p, q, r + 1.0, OUTLINE)
# pommel
disc(*along(0.0), 2.4, OUTLINE)
for p, q, r, fill, _ in parts:
    seg(p, q, r, fill)
disc(*along(0.0), 1.4, METAL_SH)
for p, q, r, _, hi in parts:
    # highlight on the upper-left edge of each part
    o = (-NRM[0] * 0.6, -NRM[1] * 0.6)
    if r >= 1.0:
        seg((p[0] + o[0], p[1] + o[1]), (q[0] + o[0], q[1] + o[1]), 0.35, hi)


# enchantment glint pixels
for t in (0.18, 0.41, 0.63, 0.86):
    x, y = along(t, 0.4)
    put(x, y, GLINT)

# ---------------- front half of the vortex ----------------
for x, y, depth, t in helix_front:
    c = blend(VORTEX_MID, VORTEX_FRONT, depth)
    disc(x, y, 0.55 + 0.45 * depth, c)   # ribbon is thickest where it's closest to us

# ---------------- snowflakes ----------------
SNOW = hexc("#ffffff")
SNOW_DIM = hexc("#a9d8ec")


def flake(x, y, big):
    if big:
        for ox, oy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            put(x + ox, y + oy, SNOW if (ox, oy) == (0, 0) else SNOW_DIM)
        for ox, oy in ((2, 0), (-2, 0), (0, 2), (0, -2)):
            put(x + ox, y + oy, blend(SNOW_DIM, px[min(max(x + ox, 0), N - 1), min(max(y + oy, 0), N - 1)], 0.5))
    else:
        put(x, y, SNOW if random.random() < 0.5 else SNOW_DIM)


def clear_of_trident(x, y, pad):
    # keep flakes off the trident silhouette
    rx, ry = x - TAIL[0], y - TAIL[1]
    t = (rx * D[0] + ry * D[1]) / L
    off = abs(rx * NRM[0] + ry * NRM[1])
    return not (-0.05 < t < 1.05 and off < (SIDE + 2 + pad if t > CROSS - 0.03 else 3 + pad))


big = [(8, 9), (29, 6), (50, 34), (40, 52), (18, 30), (57, 52), (5, 44)]
for x, y in big:
    if clear_of_trident(x, y, 2):
        flake(x, y, True)
placed = 0
while placed < 46:
    x, y = random.randint(1, 62), random.randint(1, 62)
    if clear_of_trident(x, y, 1):
        flake(x, y, False)
        placed += 1

img.save(OUT / "icon-64.png")
img.resize((512, 512), Image.NEAREST).save(OUT / "icon.png")
print("wrote", OUT / "icon.png")
