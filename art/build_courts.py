"""Builds the court-card illustrations bundled with the app.

Source: Dmitry Fomin's English pattern playing cards (Wikimedia Commons, CC0 1.0),
renders saved in art/fomin/English_pattern_<rank>_of_<suit>.png (960 px wide).

For each card: find the picture frame, crop the picture to the app's art-area aspect,
recolour the flat 5-colour palette into the app's palette (anti-aliasing preserved by
inverse-distance weighting between palette colours) and save a WebP into
app/src/main/res/drawable-nodpi/court_<r><s>.webp.

Run: python art/build_courts.py
"""
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "fomin"
OUT = ROOT.parent / "app" / "src" / "main" / "res" / "drawable-nodpi"

# Width / height of PlayingCard's art rect: 0.70w by 0.87h with h = 1.4w.
ASPECT = 0.70 / (0.87 * 1.4)
OUT_WIDTH = 360

SOURCE_PALETTE = np.array([
    (255, 255, 255),  # paper
    (0, 0, 0),        # ink
    (255, 85, 85),    # red
    (255, 255, 85),   # yellow
    (85, 85, 170),    # blue
], dtype=np.float32)

TARGET_PALETTE = np.array([
    (251, 247, 238),  # cream paper (matches CardPaper)
    (27, 27, 31),     # ink
    (198, 40, 46),    # crimson
    (220, 176, 86),   # antique gold
    (35, 64, 142),    # royal blue
], dtype=np.float32)

RANKS = {"jack": "j", "queen": "q", "king": "k"}
SUITS = {"hearts": "h", "diamonds": "d", "clubs": "c", "spades": "s"}
FRAME_BLUE = np.array([85, 85, 170])


def flatten(path: Path) -> np.ndarray:
    im = Image.open(path).convert("RGBA")
    bg = Image.new("RGBA", im.size, (255, 255, 255, 255))
    return np.asarray(Image.alpha_composite(bg, im).convert("RGB")).astype(np.int16)


def is_frame(px) -> bool:
    return np.abs(px - FRAME_BLUE).sum() < 60


# The picture frame is the same on every card of this template (960 x 1440 renders);
# detection on the middle row fails when the drawing overlaps the frame line.
FRAME = (84, 84, 875, 1355)


def find_frame(img: np.ndarray):
    h, w, _ = img.shape
    assert (w, h) == (960, 1440), f"unexpected render size {w}x{h}"
    assert is_frame(img[h // 2, FRAME[0] - 6]) or is_frame(img[FRAME[1] - 6, w // 2]), "frame not where expected"
    return FRAME


def recolour(img: np.ndarray) -> np.ndarray:
    px = img.reshape(-1, 1, 3).astype(np.float32)
    d2 = ((px - SOURCE_PALETTE[None, :, :]) ** 2).sum(axis=2)
    weights = 1.0 / (d2 ** 2 + 1e-3)
    weights /= weights.sum(axis=1, keepdims=True)
    out = weights @ TARGET_PALETTE
    return np.clip(out, 0, 255).reshape(img.shape).astype(np.uint8)


def build(rank: str, suit: str) -> Path | None:
    src = SRC / f"English_pattern_{rank}_of_{suit}.png"
    if not src.exists() or src.stat().st_size < 10_000:
        print("missing", src.name)
        return None
    img = flatten(src)
    l, t, r, b = find_frame(img)
    pic = img[t:b, l:r]
    ph, pw = pic.shape[:2]
    want_w = int(round(ph * ASPECT))
    if want_w < pw:
        cut = (pw - want_w) // 2
        pic = pic[:, cut:cut + want_w]
    else:
        want_h = int(round(pw / ASPECT))
        cut = (ph - want_h) // 2
        pic = pic[cut:cut + want_h, :]
    pic = recolour(pic)
    im = Image.fromarray(pic)
    im = im.resize((OUT_WIDTH, int(round(OUT_WIDTH / ASPECT))), Image.LANCZOS)
    OUT.mkdir(parents=True, exist_ok=True)
    dst = OUT / f"court_{RANKS[rank]}{SUITS[suit]}.webp"
    im.save(dst, "WEBP", quality=84, method=6)
    print(f"{dst.name}: frame=({l},{t},{r},{b}) -> {im.size} {dst.stat().st_size // 1024} KB")
    return dst


if __name__ == "__main__":
    for rank in RANKS:
        for suit in SUITS:
            build(rank, suit)
