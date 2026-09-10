#!/usr/bin/env python3
"""
Generates every icon of Bubble Blast and renders a contact sheet so the result
can be reviewed without Android Studio.

Outputs
-------
  app/src/main/res/drawable/ic_*.xml          vector drawables used by the UI
  app/src/main/res/mipmap-*/ic_launcher.png   legacy launcher icons (API 24-25)
  app/src/main/res/mipmap-anydpi-v26/*.xml    adaptive launcher icon
  store/icon_512.png, store/feature_graphic.png
  tools/out/icon_sheet.png                    contact sheet for review

The script only needs numpy (the SVG subset renderer is built in).
Run:  python3 tools/generate_icons.py
"""
import os
import sys

try:
    import numpy as np
except ImportError:  # pragma: no cover
    sys.exit("numpy is required: pip install numpy")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
OUT = os.path.join(ROOT, "tools", "out")

# ----------------------------------------------------------------------
# Tiny SVG-subset renderer (M L H V C Q A Z, absolute + relative)
# ----------------------------------------------------------------------

def _flatten_path(d, curve_steps=24):
    """Returns a list of polygons (each a list of (x, y) tuples)."""
    polygons = []
    current = []
    pos = (0.0, 0.0)
    start = (0.0, 0.0)
    tokens = _tokenize(d)
    i = 0
    cmd = None
    while i < len(tokens):
        token = tokens[i]
        if isinstance(token, str):
            cmd = token
            i += 1
            if cmd in "Zz":
                if current:
                    current.append(start)
                    polygons.append(current)
                    current = []
                pos = start
                continue
        if cmd is None:
            raise ValueError("path data must start with a command: " + d)
        relative = cmd.islower()
        c = cmd.upper()

        def take(n):
            nonlocal i
            values = tokens[i:i + n]
            i += n
            return [float(v) for v in values]

        if c == "M":
            x, y = take(2)
            if relative:
                x, y = pos[0] + x, pos[1] + y
            if current:
                polygons.append(current)
            current = [(x, y)]
            pos = start = (x, y)
            cmd = "l" if relative else "L"
        elif c == "L":
            x, y = take(2)
            if relative:
                x, y = pos[0] + x, pos[1] + y
            current.append((x, y))
            pos = (x, y)
        elif c == "H":
            x = take(1)[0]
            if relative:
                x = pos[0] + x
            current.append((x, pos[1]))
            pos = (x, pos[1])
        elif c == "V":
            y = take(1)[0]
            if relative:
                y = pos[1] + y
            current.append((pos[0], y))
            pos = (pos[0], y)
        elif c == "C":
            x1, y1, x2, y2, x, y = take(6)
            if relative:
                x1, y1, x2, y2, x, y = (pos[0] + x1, pos[1] + y1, pos[0] + x2,
                                        pos[1] + y2, pos[0] + x, pos[1] + y)
            p0 = pos
            for s in range(1, curve_steps + 1):
                t = s / curve_steps
                mt = 1 - t
                bx = (mt ** 3) * p0[0] + 3 * (mt ** 2) * t * x1 + 3 * mt * (t ** 2) * x2 + (t ** 3) * x
                by = (mt ** 3) * p0[1] + 3 * (mt ** 2) * t * y1 + 3 * mt * (t ** 2) * y2 + (t ** 3) * y
                current.append((bx, by))
            pos = (x, y)
        elif c == "Q":
            x1, y1, x, y = take(4)
            if relative:
                x1, y1, x, y = (pos[0] + x1, pos[1] + y1, pos[0] + x, pos[1] + y)
            p0 = pos
            for s in range(1, curve_steps + 1):
                t = s / curve_steps
                mt = 1 - t
                bx = (mt ** 2) * p0[0] + 2 * mt * t * x1 + (t ** 2) * x
                by = (mt ** 2) * p0[1] + 2 * mt * t * y1 + (t ** 2) * y
                current.append((bx, by))
            pos = (x, y)
        elif c == "A":
            rx, ry, rot, large, sweep, x, y = take(7)
            if relative:
                x, y = pos[0] + x, pos[1] + y
            for point in _arc_points(pos, (x, y), rx, ry, large, sweep):
                current.append(point)
            pos = (x, y)
        else:
            raise ValueError("unsupported path command: " + cmd)
    if current:
        polygons.append(current)
    return polygons


def _tokenize(d):
    tokens = []
    number = ""
    for ch in d.replace(",", " "):
        if ch.isalpha():
            if number.strip():
                tokens.append(number.strip())
                number = ""
            tokens.append(ch)
        elif ch in " -+.0123456789eE":
            if ch == "-" and number.strip() and not number.strip().endswith("e"):
                tokens.append(number.strip())
                number = "-"
            elif ch == " ":
                if number.strip():
                    tokens.append(number.strip())
                    number = ""
            else:
                number += ch
    if number.strip():
        tokens.append(number.strip())
    return tokens


def _arc_points(p0, p1, rx, ry, large, sweep, steps=24):
    """Endpoint parameterisation, good enough for icon sized arcs."""
    x0, y0 = p0
    x1, y1 = p1
    rx, ry = abs(rx), abs(ry)
    if rx == 0 or ry == 0:
        return [p1]
    dx, dy = (x0 - x1) / 2.0, (y0 - y1) / 2.0
    phi = 0.0
    x1p, y1p = dx, dy
    lambda_ = (x1p ** 2) / (rx ** 2) + (y1p ** 2) / (ry ** 2)
    if lambda_ > 1:
        rx *= lambda_ ** 0.5
        ry *= lambda_ ** 0.5
    sign = -1 if large == sweep else 1
    num = max(0.0, (rx ** 2) * (ry ** 2) - (rx ** 2) * (y1p ** 2) - (ry ** 2) * (x1p ** 2))
    den = (rx ** 2) * (y1p ** 2) + (ry ** 2) * (x1p ** 2)
    factor = sign * (num / den) ** 0.5 if den else 0.0
    cxp = factor * rx * y1p / ry
    cyp = -factor * ry * x1p / rx
    cx = cxp + (x0 + x1) / 2.0
    cy = cyp + (y0 + y1) / 2.0

    def angle(ux, uy, vx, vy):
        dot = ux * vx + uy * vy
        norm = ((ux ** 2 + uy ** 2) ** 0.5) * ((vx ** 2 + vy ** 2) ** 0.5)
        if norm == 0:
            return 0.0
        a = np.arccos(np.clip(dot / norm, -1.0, 1.0))
        return -a if (ux * vy - uy * vx) < 0 else a

    theta1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dtheta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not sweep and dtheta > 0:
        dtheta -= 2 * np.pi
    elif sweep and dtheta < 0:
        dtheta += 2 * np.pi

    points = []
    for i in range(1, steps + 1):
        t = theta1 + dtheta * i / steps
        points.append((cx + rx * np.cos(t) * np.cos(phi) - ry * np.sin(t) * np.sin(phi),
                       cy + rx * np.cos(t) * np.sin(phi) + ry * np.sin(t) * np.cos(phi)))
    return points


def _rgba(color, alpha=1.0):
    color = color.lstrip("#")
    if len(color) == 6:
        r, g, b = (int(color[i:i + 2], 16) for i in (0, 2, 4))
        a = 255
    else:
        r, g, b, a = (int(color[i:i + 2], 16) for i in (0, 2, 4, 6))
    return (r, g, b, int(a * alpha))


def render_paths(paths, size, background=None, supersample=None):
    """
    Rasterises a list of dicts:
      {"d": str, "fill": "#RRGGBB", "alpha": 1.0,
       "stroke": "#RRGGBB", "stroke_width": 2.0, "stroke_alpha": 1.0}
    Coordinates are in a 24x24 viewport that is mapped onto `size` pixels.
    Banded scanline fill + capsule strokes: fast enough for a contact sheet.
    """
    if supersample is None:
        supersample = 3 if size <= 192 else 2
    big = size * supersample
    scale = big / 24.0
    canvas = np.zeros((big, big, 3), dtype=np.float64)
    alpha = np.zeros((big, big), dtype=np.float64)
    if background is not None:
        canvas[:, :] = _rgba(background)[:3]
        alpha[:, :] = 1.0

    polygons = []
    for path in paths:
        polys = [[(x * scale, y * scale) for x, y in poly] for poly in _flatten_path(path["d"])]
        polygons.append((path, polys))

    for path, polys in polygons:
        if path.get("fill"):
            mask = _fill_mask(polys, big)
            _blend(canvas, alpha, mask, _rgba(path["fill"], path.get("alpha", 1.0)))
        if path.get("stroke"):
            mask = _stroke_mask(polys, big, float(path.get("stroke_width", 1.0)) * scale / 2.0)
            _blend(canvas, alpha, mask, _rgba(path["stroke"], path.get("stroke_alpha", 1.0)))

    # Box filter down to the requested size (clean anti-aliasing).
    rgb = canvas.reshape(size, supersample, size, supersample, 3).mean(axis=(1, 3))
    a = alpha.reshape(size, supersample, size, supersample).mean(axis=(1, 3))
    out = np.zeros((size, size, 4), dtype=np.float64)
    out[:, :, :3] = rgb
    out[:, :, 3] = a * 255.0
    return out


def _fill_mask(polys, big):
    """Winding-number fill, evaluated one vertex band at a time."""
    mask = np.zeros((big, big), dtype=bool)
    center = np.arange(big) + 0.5
    for poly in polys:
        if len(poly) < 3:
            continue
        pts = np.asarray(poly, dtype=np.float64)
        # Close the ring explicitly so the neighbour shift is correct.
        pts = np.vstack([pts, pts[:1]])
        x0 = pts[:-1, 0]
        y0 = pts[:-1, 1]
        x1 = pts[1:, 0]
        y1 = pts[1:, 1]
        keep = y0 != y1
        x0, y0, x1, y1 = x0[keep], y0[keep], x1[keep], y1[keep]
        if len(x0) == 0:
            continue
        ymin = np.minimum(y0, y1)
        ymax = np.maximum(y0, y1)
        bands = np.unique(np.concatenate([ymin, ymax]))
        for k in range(len(bands) - 1):
            lo, hi = bands[k], bands[k + 1]
            if hi <= lo:
                continue
            rows = center[(center >= lo) & (center < hi)]
            rows = rows[(rows >= 0) & (rows < big)]
            if len(rows) == 0:
                continue
            sel = (ymin <= lo) & (ymax >= hi)
            if not sel.any():
                continue
            ex0, ey0, ey1 = x0[sel], y0[sel], y1[sel]
            xs = ex0[None, :] + (rows[:, None] - ey0[None, :]) * \
                (x1[sel] - ex0)[None, :] / (ey1 - ey0)[None, :]
            direction = np.where(y1[sel] > y0[sel], 1, -1)[None, :]
            # Winding: edges whose intersection sits to the right of the pixel.
            winding = np.zeros((len(rows), big), dtype=np.int32)
            for column in range(xs.shape[1]):
                winding += ((xs[:, column][:, None] > center[None, :]) *
                            direction[:, column][:, None]).astype(np.int32)
            top = int(np.floor(rows[0]))
            mask[top:top + len(rows)] |= winding != 0
    return mask


def _stroke_mask(polys, big, radius):
    """Round-capped stroke: every segment becomes a capsule on its own tile."""
    if radius <= 0:
        return np.zeros((big, big), dtype=bool)
    mask = np.zeros((big, big), dtype=bool)
    for poly in polys:
        pts = np.asarray(poly, dtype=np.float64)
        if len(pts) == 1:
            pts = np.vstack([pts, pts])
        for i in range(len(pts) - 1):
            (x0, y0), (x1, y1) = pts[i], pts[i + 1]
            rx0 = max(0, int(np.floor(min(x0, x1) - radius - 1)))
            rx1 = min(big, int(np.ceil(max(x0, x1) + radius + 1)) + 1)
            ry0 = max(0, int(np.floor(min(y0, y1) - radius - 1)))
            ry1 = min(big, int(np.ceil(max(y0, y1) + radius + 1)) + 1)
            if rx1 <= rx0 or ry1 <= ry0:
                continue
            ys = (np.arange(ry0, ry1) + 0.5)[:, None]
            xs = (np.arange(rx0, rx1) + 0.5)[None, :]
            dx, dy = x1 - x0, y1 - y0
            length_sq = dx * dx + dy * dy
            if length_sq <= 1e-9:
                dist = np.sqrt((xs - x0) ** 2 + (ys - y0) ** 2)
            else:
                t = np.clip(((xs - x0) * dx + (ys - y0) * dy) / length_sq, 0.0, 1.0)
                dist = np.sqrt((xs - (x0 + t * dx)) ** 2 + (ys - (y0 + t * dy)) ** 2)
            mask[ry0:ry1, rx0:rx1] |= dist <= radius
    return mask


def _blend(canvas, alpha, mask, color):
    if not mask.any():
        return
    a = color[3] / 255.0
    if a <= 0:
        return
    for channel in range(3):
        canvas[:, :, channel] = np.where(
            mask,
            canvas[:, :, channel] * (1 - a) + color[channel] * a,
            canvas[:, :, channel],
        )
    alpha[:] = np.where(mask, np.maximum(alpha, a), alpha)


def write_png(path, rgba):
    """Minimal PNG writer (RGBA, 8 bit) using zlib."""
    import struct
    import zlib

    height, width = rgba.shape[0], rgba.shape[1]
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            r, g, b, a = rgba[y, x]
            raw += bytes((int(max(0, min(255, r))), int(max(0, min(255, g))),
                          int(max(0, min(255, b))), int(max(0, min(255, a)))))

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + \
        chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(png)


# ----------------------------------------------------------------------
# Icon definitions (24x24 viewport)
# ----------------------------------------------------------------------

WHITE = "#FFFFFF"
GOLD = "#FFC629"
GOLD_DARK = "#D89A00"
CYAN = "#4DE1FF"
GREEN = "#38D96B"
PINK = "#FF4D9D"
BLUE = "#2E9BFF"
PURPLE = "#A85CFF"
ORANGE = "#FF7A2F"
GREY = "#9E93C8"
INK = "#2B1152"


def star_path(cx, cy, outer, inner, points=5, rotation=-90):
    coords = []
    for i in range(points * 2):
        radius = outer if i % 2 == 0 else inner
        angle = np.radians(rotation + i * 180.0 / points)
        coords.append((cx + radius * np.cos(angle), cy + radius * np.sin(angle)))
    d = "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in coords) + " Z"
    return d


def gear_path(cx, cy, outer, teeth=8, tooth_depth=0.22):
    """Outer gear outline: 8 square-ish teeth on a round body."""
    points = []
    step = 2 * np.pi / (teeth * 4)
    for i in range(teeth * 4):
        angle = i * step
        radius = outer if (i % 4) == 0 else outer * (1 - tooth_depth)
        points.append((cx + radius * np.cos(angle), cy + radius * np.sin(angle)))
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in points) + " Z"


def create_icon_definitions():
    """name -> (list of path dicts, fillType evenOdd?)"""
    icons = {}

    icons["ic_star"] = [
        dict(d=star_path(12, 12.6, 9.4, 4.1), fill=GOLD),
        dict(d=star_path(12, 12.6, 9.4, 4.1), fill=None, stroke=GOLD_DARK, stroke_width=0.9, stroke_alpha=0.9),
    ]
    icons["ic_star_empty"] = [
        dict(d=star_path(12, 12.6, 9.4, 4.1), fill="#33FFFFFF"),
        dict(d=star_path(12, 12.6, 9.4, 4.1), fill=None, stroke=GREY, stroke_width=1.4),
    ]
    icons["ic_coin"] = [
        dict(d="M12,2.6 A9.4,9.4 0 1 0 12,21.4 A9.4,9.4 0 1 0 12,2.6 Z", fill=GOLD),
        dict(d="M12,5.6 A6.4,6.4 0 1 0 12,18.4 A6.4,6.4 0 1 0 12,5.6 Z", fill="#FFF0B8"),
        dict(d=star_path(12, 12.2, 5.2, 2.2), fill=GOLD_DARK),
        dict(d=star_path(12, 12.2, 5.2, 2.2), fill=None, stroke="#FFF3C4", stroke_width=0.7),
    ]
    icons["ic_plus_circle"] = [
        dict(d="M12,3.4 A8.6,8.6 0 1 0 12,20.6 A8.6,8.6 0 1 0 12,3.4 Z", fill="#59FFFFFF"),
        dict(d="M12,3.4 A8.6,8.6 0 1 0 12,20.6 A8.6,8.6 0 1 0 12,3.4 Z", fill=None, stroke=WHITE, stroke_width=1.6),
        dict(d="M12,8 v8 M8,12 h8", fill=None, stroke=WHITE, stroke_width=2.4),
    ]
    icons["ic_back"] = [
        dict(d="M15.5,4.5 L8,12 L15.5,19.5", fill=None, stroke=WHITE, stroke_width=2.6),
        dict(d="M9,12 H20", fill=None, stroke=WHITE, stroke_width=2.6),
    ]
    icons["ic_lock"] = [
        dict(d="M8.2,10.6 v-2.4 a3.8,3.8 0 0 1 7.6,0 v2.4", fill=None, stroke=WHITE, stroke_width=1.9),
        dict(d="M6.4,10.6 h11.2 a1.4,1.4 0 0 1 1.4,1.4 v7.6 a1.4,1.4 0 0 1 -1.4,1.4 h-11.2 a1.4,1.4 0 0 1 -1.4,-1.4 v-7.6 a1.4,1.4 0 0 1 1.4,-1.4 Z",
             fill="#E6FFFFFF"),
        dict(d="M12,14.2 a1.5,1.5 0 1 0 0,3 a1.5,1.5 0 1 0 0,-3 Z", fill=INK),
    ]
    icons["ic_pause"] = [
        dict(d="M7.4,5 h3.2 v14 h-3.2 Z", fill=WHITE),
        dict(d="M13.4,5 h3.2 v14 h-3.2 Z", fill=WHITE),
    ]
    icons["ic_gear"] = [
        dict(d=gear_path(12, 12, 9.8), fill=WHITE),
        dict(d="M12,7.4 A4.6,4.6 0 1 0 12,16.6 A4.6,4.6 0 1 0 12,7.4 Z", fill="#66000000", alpha=0.85),
        dict(d="M12,9.2 A2.8,2.8 0 1 0 12,14.8 A2.8,2.8 0 1 0 12,9.2 Z", fill=WHITE),
    ]
    icons["ic_gift"] = [
        dict(d="M4,10.4 h16 v3.2 h-16 Z", fill=PINK),
        dict(d="M5.6,13.6 h12.8 v6.4 a1.2,1.2 0 0 1 -1.2,1.2 h-10.4 a1.2,1.2 0 0 1 -1.2,-1.2 Z", fill="#E64FB08A"),
        dict(d="M10.8,10.4 h2.4 v10.8 h-2.4 Z", fill="#FFFFE08A"),
        dict(d="M12,9.6 C10.4,5.6 5.6,5.6 6.4,8.6 C7,10.4 10.4,10.2 12,9.6 Z", fill="#FFFFE08A"),
        dict(d="M12,9.6 C13.6,5.6 18.4,5.6 17.6,8.6 C17,10.4 13.6,10.2 12,9.6 Z", fill="#FFFFE08A"),
    ]
    icons["ic_aim"] = [
        dict(d="M12,4.2 A7.8,7.8 0 1 0 12,19.8 A7.8,7.8 0 1 0 12,4.2 Z", fill=None, stroke=WHITE, stroke_width=1.8),
        dict(d="M12,2.4 v3.4 M12,18.2 v3.4 M2.4,12 h3.4 M18.2,12 h3.4", fill=None, stroke=WHITE, stroke_width=2),
        dict(d="M12,9.8 a2.2,2.2 0 1 0 0,4.4 a2.2,2.2 0 1 0 0,-4.4 Z", fill=CYAN),
    ]
    icons["ic_color_drop"] = [
        dict(d="M12,2.8 C12,2.8 5.4,10.6 5.4,14.6 a6.6,6.6 0 0 0 13.2,0 C18.6,10.6 12,2.8 12,2.8 Z", fill=BLUE),
        dict(d="M9.4,13.6 C9.4,13.6 8.2,15.2 8.2,16.4 a3.8,3.8 0 0 0 3.8,3.8", fill=None, stroke="#B3FFFFFF", stroke_width=2),
        dict(d="M10.2,8.6 C10.2,8.6 9,10.4 9,11.4", fill=None, stroke="#CCFFFFFF", stroke_width=1.6),
    ]
    icons["ic_bomb"] = [
        dict(d="M9.6,11.4 a5.4,5.4 0 1 0 0,10.8 a5.4,5.4 0 1 0 0,-10.8 Z", fill="#3A3450"),
        dict(d="M9.6,11.4 a5.4,5.4 0 1 0 0,10.8 a5.4,5.4 0 1 0 0,-10.8 Z", fill=None, stroke=WHITE, stroke_width=1.4),
        dict(d="M13.6,11.4 C15.4,9.4 15.8,7.6 14.6,6.2", fill=None, stroke=WHITE, stroke_width=1.6),
        dict(d="M14.6,6.2 a2.1,2.1 0 1 0 0.1,0.1 Z", fill=GOLD),
    ]
    icons["ic_sound_on"] = [
        dict(d="M4,9.4 h3.2 L11.4,6 v12 L7.2,14.6 H4 Z", fill=WHITE),
        dict(d="M14,8.6 a4.6,4.6 0 0 1 0,6.8", fill=None, stroke=WHITE, stroke_width=1.9),
        dict(d="M16.6,6.2 a8,8 0 0 1 0,11.6", fill=None, stroke=WHITE, stroke_width=1.9),
    ]
    icons["ic_sound_off"] = [
        dict(d="M4,9.4 h3.2 L11.4,6 v12 L7.2,14.6 H4 Z", fill=WHITE),
        dict(d="M15,9.6 L20.4,15 M20.4,9.6 L15,15", fill=None, stroke=WHITE, stroke_width=1.9),
    ]
    icons["ic_music_on"] = [
        dict(d="M9.6,16.4 a2.6,2 0 1 0 0,0.1 Z", fill=WHITE),
        dict(d="M17.2,14.4 a2.6,2 0 1 0 0,0.1 Z", fill=WHITE),
        dict(d="M11.4,16.6 V7.2 L19,5.4 V14.6", fill=None, stroke=WHITE, stroke_width=1.8),
    ]
    icons["ic_music_off"] = [
        dict(d="M9.6,16.4 a2.6,2 0 1 0 0,0.1 Z", fill=WHITE),
        dict(d="M11.4,16.6 V7.2 L19,5.4 V14.6", fill=None, stroke=WHITE, stroke_width=1.8),
        dict(d="M3.4,3.4 L20.6,20.6", fill=None, stroke=PINK, stroke_width=2.2),
    ]
    icons["ic_logo"] = logo_paths()
    return icons


def bubble(cx, cy, r, base, highlight, outline="#CCFFFFFF"):
    return [
        dict(d=f"M{cx},{cy - r} A{r},{r} 0 1 0 {cx},{cy + r} A{r},{r} 0 1 0 {cx},{cy - r} Z", fill=base),
        dict(d=f"M{cx - r * 0.32:.2f},{cy - r * 0.36:.2f} a{r * 0.42:.2f},{r * 0.34:.2f} 0 1 0 {r * 0.02:.2f},0.01 Z",
             fill=highlight, alpha=0.85),
        dict(d=f"M{cx},{cy - r} A{r},{r} 0 1 0 {cx},{cy + r} A{r},{r} 0 1 0 {cx},{cy - r} Z",
             fill=None, stroke=outline, stroke_width=r * 0.12),
        dict(d=f"M{cx + r * 0.34:.2f},{cy + r * 0.36:.2f} a{r * 0.2:.2f},{r * 0.16:.2f} 0 1 0 {r * 0.01:.2f},0.01 Z",
             fill="#33000000", alpha=0.6),
    ]


def logo_paths():
    paths = []
    paths += bubble(8.6, 9.4, 6.0, BLUE, "#BFE3FFFF")
    paths += bubble(16.4, 14.2, 5.4, PINK, "#FFD1E9FF")
    paths += bubble(9.4, 17.0, 4.3, GREEN, "#D2F7DFFF")
    paths.append(dict(d=star_path(17.6, 6.4, 3.0, 1.3), fill=GOLD))
    return paths


# ----------------------------------------------------------------------
# Writing Android resources
# ----------------------------------------------------------------------

def vector_xml(paths, size=24, even_odd=False):
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Generated by tools/generate_icons.py - edit that script, not this file. -->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{size}dp"',
        f'    android:height="{size}dp"',
        f'    android:viewportWidth="{size}"',
        f'    android:viewportHeight="{size}">',
    ]
    for path in paths:
        attrs = []
        fill = path.get("fill")
        if fill:
            alpha = path.get("alpha", 1.0)
            if alpha >= 0.999:
                attrs.append(f'android:fillColor="{fill}"')
            else:
                hex_alpha = format(int(alpha * 255), "02X") + fill.lstrip("#")
                attrs.append(f'android:fillColor="#{hex_alpha}"')
        else:
            attrs.append('android:fillColor="#00000000"')
        if path.get("stroke"):
            attrs.append(f'android:strokeColor="{path["stroke"]}"')
            attrs.append(f'android:strokeWidth="{path["stroke_width"]}"')
            attrs.append('android:strokeLineCap="round"')
            attrs.append('android:strokeLineJoin="round"')
        if even_odd and fill:
            attrs.append('android:fillType="evenOdd"')
        attrs.append(f'android:pathData="{path["d"]}"')
        lines.append('    <path ' + "\n        ".join(attrs) + ' />')
    lines.append('</vector>')
    return "\n".join(lines) + "\n"


ADAPTIVE_FOREGROUND = """
<!-- Adaptive launcher icon foreground (API 26+). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <group android:scaleX="0.62" android:scaleY="0.62" android:pivotX="12" android:pivotY="12">
{paths}
    </group>
</vector>
"""


def main():
    icons = create_icon_definitions()
    drawable_dir = os.path.join(RES, "drawable")
    os.makedirs(drawable_dir, exist_ok=True)

    for name, paths in icons.items():
        with open(os.path.join(drawable_dir, name + ".xml"), "w") as handle:
            handle.write(vector_xml(paths))
    print(f"wrote {len(icons)} vector drawables")

    # ---- adaptive launcher icon ----
    adaptive_dir = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(adaptive_dir, exist_ok=True)
    # The foreground/background vectors are plain drawables; only the
    # <adaptive-icon> descriptors live in mipmap-anydpi-v26.
    layer_dir = os.path.join(RES, "drawable")
    os.makedirs(layer_dir, exist_ok=True)
    logo = icons["ic_logo"]
    inner = "\n".join(
        "        " + vector_xml(logo).split("\n", 5)[5].strip().rstrip("</vector>").strip()
        for _ in [0]
    ) if False else None
    # Build the foreground by re-using the logo path elements at group scale.
    foreground_lines = []
    for path in logo:
        attrs = [f'android:fillColor="{path["fill"]}"' if path.get("fill") else 'android:fillColor="#00000000"']
        if path.get("stroke"):
            attrs += [f'android:strokeColor="{path["stroke"]}"', f'android:strokeWidth="{path["stroke_width"]}"',
                      'android:strokeLineCap="round"']
        if path.get("alpha") is not None and path.get("alpha", 1.0) < 0.999:
            attrs[0] = f'android:fillColor="#{format(int(path["alpha"] * 255), "02X")}{path["fill"].lstrip("#")}"'
        attrs.append(f'android:pathData="{path["d"]}"')
        foreground_lines.append("        <path " + "\n            ".join(attrs) + " />")
    with open(os.path.join(layer_dir, "ic_launcher_foreground.xml"), "w") as handle:
        handle.write(ADAPTIVE_FOREGROUND.format(paths="\n".join(foreground_lines)))

    with open(os.path.join(layer_dir, "ic_launcher_background.xml"), "w") as handle:
        handle.write("""<?xml version="1.0" encoding="utf-8"?>
<!-- Adaptive launcher icon background (API 26+). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:pathData="M0,0h108v108h-108z"
        android:fillColor="#3A2470" />
    <path
        android:pathData="M0,0h108v108h-108z"
        android:fillColor="#00000000"
        android:strokeColor="#00000000" />
    <path
        android:pathData="M54,4 a50,50 0 1 0 0.1,0 Z"
        android:fillColor="#4B2C8C" />
    <path
        android:pathData="M54,16 a38,38 0 1 0 0.1,0 Z"
        android:fillColor="#5B34A8" />
</vector>
""")

    with open(os.path.join(adaptive_dir, "ic_launcher.xml"), "w") as handle:
        handle.write("""<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
""")
    with open(os.path.join(adaptive_dir, "ic_launcher_round.xml"), "w") as handle:
        handle.write(open(os.path.join(adaptive_dir, "ic_launcher.xml")).read())
    print("wrote adaptive launcher icon")

    # ---- legacy launcher PNGs ----
    launcher_paths = [
        dict(d="M0,0 h24 v24 h-24 Z", fill="#3A2470"),
        dict(d="M12,2.4 a9.6,9.6 0 1 0 0.1,0 Z", fill="#5B34A8"),
    ] + logo
    for folder, size in (("mipmap-mdpi", 48), ("mipmap-hdpi", 72), ("mipmap-xhdpi", 96),
                         ("mipmap-xxhdpi", 144), ("mipmap-xxxhdpi", 192)):
        image = render_paths(launcher_paths, size, supersample=4)
        target_dir = os.path.join(RES, folder)
        os.makedirs(target_dir, exist_ok=True)
        write_png(os.path.join(target_dir, "ic_launcher.png"), image)
        write_png(os.path.join(target_dir, "ic_launcher_round.png"), image)
    print("wrote launcher mipmaps")

    # ---- store assets ----
    store_dir = os.path.join(ROOT, "store")
    os.makedirs(store_dir, exist_ok=True)
    write_png(os.path.join(store_dir, "icon_512.png"),
              render_paths(launcher_paths, 512, supersample=3))

    # Feature graphic: gradient panel with the key art pasted in the middle.
    width, height = 1024, 500
    graphic = np.zeros((height, width, 4), dtype=np.float64)
    top, bottom = np.array(_rgba("#4B2C8C")[:3]), np.array(_rgba("#22103F")[:3])
    ramp = np.linspace(0.0, 1.0, height)[:, None, None]
    graphic[:, :, :3] = top[None, None, :] * (1 - ramp) + bottom[None, None, :] * ramp
    graphic[:, :, 3] = 255
    art = render_paths(launcher_paths, 512, supersample=2)
    art_size = 360
    step = 512 / art_size
    index = (np.arange(art_size) * step).astype(int)
    art_small = art[np.ix_(index, index)]
    y0 = (height - art_size) // 2
    x0 = (width - art_size) // 2
    region = graphic[y0:y0 + art_size, x0:x0 + art_size]
    a = art_small[:, :, 3:4] / 255.0
    graphic[y0:y0 + art_size, x0:x0 + art_size, :3] = region[:, :, :3] * (1 - a) + art_small[:, :, :3] * a
    write_png(os.path.join(store_dir, "feature_graphic.png"), graphic)
    print("wrote store assets")

    # ---- contact sheet for review ----
    os.makedirs(OUT, exist_ok=True)
    names = sorted(icons.keys())
    cell = 96
    columns = 5
    rows = (len(names) + columns - 1) // columns
    sheet = np.zeros((rows * cell, columns * cell, 4), dtype=np.float64)
    sheet[:, :, :3] = 42
    sheet[:, :, 3] = 255
    for index, name in enumerate(names):
        tile = render_paths(icons[name], cell, supersample=3)
        row, column = divmod(index, columns)
        alpha = tile[:, :, 3:4] / 255.0
        region = sheet[row * cell:(row + 1) * cell, column * cell:(column + 1) * cell, :3]
        sheet[row * cell:(row + 1) * cell, column * cell:(column + 1) * cell, :3] = (
            region * (1 - alpha) + tile[:, :, :3] * alpha
        )
    write_png(os.path.join(OUT, "icon_sheet.png"), sheet)
    print("wrote contact sheet:", os.path.join(OUT, "icon_sheet.png"))
    print("icons:", ", ".join(names))


if __name__ == "__main__":
    main()
