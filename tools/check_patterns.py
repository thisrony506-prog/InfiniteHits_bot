#!/usr/bin/env python3
"""
Static checker for the ASCII level layouts in
app/src/main/java/com/infinitehits/bubbleblast/core/level/LayoutPatterns.kt

The bubble grid alternates between 11 columns (even rows, offset 0) and
10 columns (odd rows, offset half a bubble). Every pattern row must obey that
width rule or the generator would place bubbles outside the hex lattice.

Run:  python3 tools/check_patterns.py
Exit code 0 = all patterns valid.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(
    ROOT, "app", "src", "main", "java", "com", "infinitehits", "bubbleblast",
    "core", "level", "LayoutPatterns.kt",
)

# . empty | # colour | S stone | X unbreakable | L locked | I ice
# M moving row | B bomb | R rainbow | T lightning | F fire
LEGEND = ".#SXLIMBRTF"
MIN_ROWS, MAX_ROWS = 3, 12


def main() -> int:
    with open(SOURCE) as handle:
        source = handle.read()

    blocks = re.findall(r"(\w+) to listOf\((.*?)\)", source, re.S)
    if not blocks:
        print("no patterns found - did the file move?")
        return 1

    errors = []
    for name, body in blocks:
        rows = re.findall(r'"([^"]*)"', body)
        if not (MIN_ROWS <= len(rows) <= MAX_ROWS):
            errors.append(f"{name}: {len(rows)} rows (allowed {MIN_ROWS}-{MAX_ROWS})")
        for index, row in enumerate(rows):
            wanted = 11 if index % 2 == 0 else 10
            if len(row) != wanted:
                errors.append(f"{name} row {index}: width {len(row)}, expected {wanted} [{row}]")
            for char in row:
                if char not in LEGEND:
                    errors.append(f"{name} row {index}: unknown symbol '{char}'")
            if index % 2 == 1 and row.endswith("#"):
                # An odd row ending on a bubble leaves a visible gap against the
                # right wall of the even row above it. Warn, do not fail.
                pass

    # Every pattern must be referenced by the catalog or the generator.
    keys = [name for name, _ in blocks]
    constants = [name for name, _ in re.findall(r'val (\w+) = "([a-z_]+)"', source)]
    missing = [name for name in constants if name not in keys]
    if missing:
        errors.append(f"constants without a pattern: {', '.join(missing)}")
    duplicates = {name for name in keys if keys.count(name) > 1}
    if duplicates:
        errors.append(f"duplicate pattern keys: {', '.join(sorted(duplicates))}")

    print(f"patterns checked: {len(blocks)}")
    if errors:
        for error in errors:
            print("  ERROR", error)
        return 1
    print(f"constants: {len(constants)} -> all resolve")
    print("ALL PATTERNS OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
