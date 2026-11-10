"""Pixel differences between two screenshots, shared by scripts/screenshot-review.py and scripts/aa-gallery.sh.
Needs numpy and ImageMagick."""
import struct, subprocess
from collections import deque
import numpy as np

CELL = 8  # changed pixels closer than about one cell join one box

def size(png):
    """[width, height] from a PNG's header."""
    return list(struct.unpack(">II", png[16:24]))

def pixels(png):
    w, h = size(png)
    raw = subprocess.run(["magick", "png:-", "-depth", "8", "rgba:-"], input=png, capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.uint8).reshape(h, w, 4)

def boxes(mask):
    """Bounding boxes [x, y, w, h] of the changed areas: cells holding a change, joined with their neighbours."""
    h, w = mask.shape
    gh, gw = -(-h // CELL), -(-w // CELL)
    grid = np.zeros((gh * CELL, gw * CELL), bool)
    grid[:h, :w] = mask
    grid = grid.reshape(gh, CELL, gw, CELL).any(axis=(1, 3))
    seen, result = np.zeros_like(grid), []
    for cy, cx in zip(*np.nonzero(grid)):
        if seen[cy, cx]:
            continue
        seen[cy, cx] = True
        queue, cells = deque([(cy, cx)]), []
        while queue:
            y, x = queue.popleft()
            cells.append((y, x))
            for ny in range(max(0, y - 1), min(gh, y + 2)):
                for nx in range(max(0, x - 1), min(gw, x + 2)):
                    if grid[ny, nx] and not seen[ny, nx]:
                        seen[ny, nx] = True
                        queue.append((ny, nx))
        ys, xs = [c[0] for c in cells], [c[1] for c in cells]
        y0, x0 = min(ys) * CELL, min(xs) * CELL
        sub = mask[y0:(max(ys) + 1) * CELL, x0:(max(xs) + 1) * CELL]
        py, px = np.nonzero(sub)
        result.append([int(x0 + px.min()), int(y0 + py.min()), int(px.max() - px.min() + 1), int(py.max() - py.min() + 1)])
    return result

def changed_boxes(before_png, after_png):
    """The changed areas of [after_png] against [before_png]: the whole picture when the sizes differ, [] when the
    pixels are the same."""
    a, b = pixels(before_png), pixels(after_png)
    if a.shape != b.shape:
        return [[0, 0, b.shape[1], b.shape[0]]]
    return boxes((a != b).any(axis=2))
