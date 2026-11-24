package io.github.beddeak.uno.art;

import java.util.Map;

final class Glyphs {

    static final String[][] BIG_DIGITS = {
            {".###.", "##.##", "##.##", "##.##", "##.##", "##.##", ".###."},
            {"..##.", ".###.", "..##.", "..##.", "..##.", "..##.", ".####"},
            {".###.", "##.##", "...##", "..##.", ".##..", "##...", "#####"},
            {"####.", "...##", "...##", ".###.", "...##", "...##", "####."},
            {"...##", "..###", ".#.##", "#..##", "#####", "...##", "...##"},
            {"#####", "##...", "####.", "...##", "...##", "##.##", ".###."},
            {".###.", "##...", "##...", "####.", "##.##", "##.##", ".###."},
            {"#####", "...##", "..##.", "..##.", ".##..", ".##..", ".##.."},
            {".###.", "##.##", "##.##", ".###.", "##.##", "##.##", ".###."},
            {".###.", "##.##", "##.##", ".####", "...##", "...##", ".###."},
    };

    static final String[] BIG_PLUS = {"..##..", "..##..", "######", "######", "..##..", "..##.."};

    static final String[] BANG = {"###", "###", "###", "###", "###", "...", "###"};

    static final String[] SMALL_SKIP = {".###.", "#..##", "#.#.#", "##..#", ".###."};

    static final String[] SMALL_REVERSE = {"...#.", "#####", "...#.", ".....", ".#...", "#####", ".#..."};

    static final Map<Character, String[]> SMALL = Map.ofEntries(
            Map.entry('U', new String[]{"#.#", "#.#", "#.#", "#.#", "###"}),
            Map.entry('N', new String[]{"#..#", "##.#", "#.##", "#..#", "#..#"}),
            Map.entry('O', new String[]{"###", "#.#", "#.#", "#.#", "###"}),
            Map.entry('0', new String[]{"###", "#.#", "#.#", "#.#", "###"}),
            Map.entry('1', new String[]{".#.", "##.", ".#.", ".#.", "###"}),
            Map.entry('2', new String[]{"###", "..#", "###", "#..", "###"}),
            Map.entry('3', new String[]{"###", "..#", "###", "..#", "###"}),
            Map.entry('4', new String[]{"#.#", "#.#", "###", "..#", "..#"}),
            Map.entry('5', new String[]{"###", "#..", "###", "..#", "###"}),
            Map.entry('6', new String[]{"###", "#..", "###", "#.#", "###"}),
            Map.entry('7', new String[]{"###", "..#", "..#", ".#.", ".#."}),
            Map.entry('8', new String[]{"###", "#.#", "###", "#.#", "###"}),
            Map.entry('9', new String[]{"###", "#.#", "###", "..#", "###"}),
            Map.entry('+', new String[]{"...", ".#.", "###", ".#.", "..."}),
            Map.entry('A', new String[]{".#.", "#.#", "###", "#.#", "#.#"}),
            Map.entry('C', new String[]{"###", "#..", "#..", "#..", "###"}),
            Map.entry('D', new String[]{"##.", "#.#", "#.#", "#.#", "##."}),
            Map.entry('L', new String[]{"#..", "#..", "#..", "#..", "###"}),
            Map.entry('R', new String[]{"##.", "#.#", "##.", "#.#", "#.#"}),
            Map.entry('S', new String[]{"###", "#..", "###", "..#", "###"}),
            Map.entry('T', new String[]{"###", ".#.", ".#.", ".#.", ".#."})
    );

    private Glyphs() {
    }

    static Mask text(Mask mask, String text, int left, int top) {
        int x = left;
        for (char c : text.toCharArray()) {
            String[] glyph = SMALL.get(c);
            if (glyph == null) {
                throw new IllegalArgumentException("no glyph for " + c);
            }
            mask.glyph(glyph, x, top, 1);
            x += glyph[0].length() + 1;
        }
        return mask;
    }
}
