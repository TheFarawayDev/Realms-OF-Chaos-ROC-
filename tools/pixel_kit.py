"""A small kit for drawing pixel art that holds together: colour ramps that shift hue as they darken and lighten,
shaded spheres, faceted crystals, clusters of nuggets, templates, and outlines in a darker shade of what they edge."""
import colorsys
import math
from PIL import Image


def ramp(base, n=6, warm=0.11, cool=0.62):
    """A ramp from outline to highlight, n tones: shadows lean toward cool blue-violet and gain saturation;
    highlights lean toward warm yellow and lose it. Index 0 is the outline, n-1 the highlight."""
    r, g, b = [v / 255 for v in base]
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    out = []
    mid = 3
    for i in range(n):
        t = (i - mid) / mid                          # -1 (darkest) .. +0.67 (lightest)
        if t < 0:
            target = cool
            k = -t * 0.18
            dv = 0.34 + 0.66 * (1 + t)
            ss = min(1.0, s * (1 + -t * 0.35) + 0.04)
        else:
            target = warm
            k = t * 0.22
            dv = 1.0 + t * 0.5
            ss = max(0.0, s * (1 - t * 0.55))
        dh = ((target - h + 0.5) % 1.0) - 0.5
        hh = (h + dh * k) % 1.0
        vv = min(1.0, v * dv + (0.08 * t if t > 0 else 0))
        rr, gg, bb = colorsys.hsv_to_rgb(hh, ss, vv)
        out.append((int(rr * 255), int(gg * 255), int(bb * 255)))
    if v < 0.3:                                       # (a dark base still wants a visible highlight)
        out[-1] = tuple(min(255, c + 70) for c in out[-1])
        out[-2] = tuple(min(255, c + 35) for c in out[-2])
    return out


LIGHT = (-0.55, -0.62, 0.56)


def _norm(v):
    m = math.sqrt(sum(c * c for c in v))
    return tuple(c / m for c in v)


L = _norm(LIGHT)


def sphere(cells, cx, cy, r, tones, depth=None, z0=0.0, spec=True):
    """Shades a sphere into cells {(x, y): colour}, lit from the top left; tones dark..light (no outline)."""
    for y in range(int(cy - r - 1), int(cy + r + 2)):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            dx, dy = (x + 0.5 - cx) / r, (y + 0.5 - cy) / r
            d2 = dx * dx + dy * dy
            if d2 > 1.0 or not (0 <= x < 16 and 0 <= y < 16):
                continue
            nz = math.sqrt(1 - d2)
            z = z0 + nz * r
            if depth is not None:
                if depth.get((x, y), -1e9) > z:
                    continue
                depth[(x, y)] = z
            lam = max(0.0, dx * L[0] + dy * L[1] + nz * L[2])
            i = min(len(tones) - 1, int(lam * len(tones) * 0.999 + 0.25))
            c = tones[i]
            if spec and lam > 0.96:
                c = tuple(min(255, v + 60) for v in tones[-1])
            cells[(x, y)] = c
    return cells


def crystal(cells, x0, y0, angle, length, width, tones, depth=None, z=0.0):
    """A pointed hexagonal crystal from (x0, y0) along an angle (degrees, 0 = up): three facets, left lit."""
    a = math.radians(angle)
    ux, uy = math.sin(a), -math.cos(a)           # along
    vx, vy = -uy, ux                              # across (to the right of along)
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5 - x0, y + 0.5 - y0
            u = px * ux + py * uy
            v = px * vx + py * vy
            if u < 0 or u > length:
                continue
            half = width if u < length - width * 1.2 else (length - u) / 1.2
            if abs(v) > half:
                continue
            if depth is not None:
                if depth.get((x, y), -1e9) > z:
                    continue
                depth[(x, y)] = z
            third = half / 3.0
            if v < -third: c = tones[-2]
            elif v > third: c = tones[1]
            else: c = tones[-3]
            if abs(v + third) < 0.5 and u < length - 1: c = tones[-1]       # the bright edge between facets
            cells[(x, y)] = c
    return cells


def outline(cells, colour, img=None):
    """An image of the cells, outlined (four-way) in a colour wherever a cell meets empty space."""
    img = img or Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for (x, y), c in cells.items():
        img.putpixel((x, y), tuple(c[:3]) + (255,))
    for (x, y) in list(cells):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in cells and img.getpixel((nx, ny))[3] == 0:
                img.putpixel((nx, ny), tuple(colour[:3]) + (255,))
    return img


def template(rows, pal, size=16):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == size, (y, len(row), row)
        for x, ch in enumerate(row):
            if ch in pal and pal[ch] is not None:
                img.putpixel((x, y), tuple(pal[ch][:3]) + (255,))
    return img


def tones_pal(t, extra=None):
    """A palette for templates from a six-tone ramp: O outline, S shadow, D dark, M mid, L light, W highlight."""
    p = {"O": t[0], "S": t[1], "D": t[2], "M": t[3], "L": t[4], "W": t[5]}
    if extra: p.update(extra)
    return p
