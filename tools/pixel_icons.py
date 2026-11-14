"""Pixel style icons: 2dp cells in a 24dp square, written as vector drawables of rectangles.

    python3 tools/pixel_icons.py app/src/main/res/drawable

Each icon is a picture in text, # for a filled cell. At most 12 by 12 cells; smaller pictures are centred.
"""
import sys, os
ICONS = {
 "shift": [".....#.....", "....#.#....", "...#...#...", "..#.....#..", ".###...###.", "...#...#...", "...#...#...", "...#...#...", "...#####..."],
 "shifted": [".....#.....", "....###....", "...#####...", "..#######..", ".#########.", "...#####...", "...#####...", "...#####...", "...#####..."],
 "shift_lock": [".....#.....", "....###....", "...#####...", "..#######..", ".#########.", "...#####...", "...#####...", "...#####...", "...........", "...#####...", "...#####..."],
 "delete": ["....########", "...#.......#", "..#..#...#.#", ".#....#.#..#", "#......#...#", ".#....#.#..#", "..#..#...#.#", "...#.......#", "....########"],
 "return": [".........##", ".........##", "..#......##", ".##......##", "###########", "###########", ".##........", "..#........"],
 "language_switch": ["...#####...", "..#..#..#..", ".#...#...#.", "#....#....#", "#....#....#", "###########", "#....#....#", "#....#....#", ".#...#...#.", "..#..#..#..", "...#####..."],
 "space": ["#.........#", "#.........#", "###########"],
 "search": ["..####....", ".#....#...", "#......#..", "#......#..", "#......#..", "#......#..", ".#....#...", "..####.#..", ".......##.", "........##"],
 "next": [".......#...", ".......##..", "##########.", "###########", "##########.", ".......##..", ".......#..."],
 "previous": ["...#.......", "..##.......", ".##########", "###########", ".##########", "..##.......", "...#......."],
 "done": ["..........#", ".........##", "........##.", "#......##..", "##....##...", ".##..##....", "..####.....", "...##......"],
 "tab": [".....#....#", ".....##...#", "########..#", "#########.#", "########..#", ".....##...#", ".....#....#"],
 "smiley": ["..#####..", ".#.....#.", "#..#.#..#", "#..#.#..#", "#.......#", "#.#...#.#", "#..###..#", ".#.....#.", "..#####.."],
 "toolbar": ["##....", ".##...", "..##..", "...##.", "..##..", ".##...", "##...."],
}

def vector(rows):
    n = 12
    w, h = len(rows[0]), len(rows)
    assert w <= n and h <= n and all(len(r) == w for r in rows), rows
    ox, oy = (n - w) // 2, (n - h) // 2
    parts = []
    for j, r in enumerate(rows):
        i = 0
        while i < w:
            if r[i] == "#":
                k = i
                while k < w and r[k] == "#": k += 1
                parts.append(f"M{2 * (ox + i)},{2 * (oy + j)}h{2 * (k - i)}v2h-{2 * (k - i)}z")
                i = k
            else:
                i += 1
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Pixel style icon: 2dp cells. Written by tools/pixel_icons.py of this fork. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp"\n    android:height="24dp"\n    android:viewportWidth="24"\n    android:viewportHeight="24">\n'
            f'    <path android:fillColor="#FFF"\n        android:pathData="{"".join(parts)}"/>\n</vector>\n')

if __name__ == "__main__":
    out = sys.argv[1]
    for name, rows in ICONS.items():
        open(os.path.join(out, f"sym_keyboard_{name}_pixel.xml"), "w").write(vector(rows))
    print(len(ICONS), "icons")
