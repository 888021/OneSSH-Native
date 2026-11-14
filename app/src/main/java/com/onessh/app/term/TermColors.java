package com.onessh.app.term;

/** 终端配色，直接取自网页版 term.js 里的 THEME。 */
public final class TermColors {

    public static final int BG = 0xFF070910;
    public static final int FG = 0xFFD6DBE4;
    public static final int CURSOR = 0xFFFF7A4D;
    public static final int CURSOR_TEXT = 0xFF070910;
    public static final int SELECTION = 0x44FF7A4D;

    private static final int[] BASE = {
            0xFF171B22, // black
            0xFFFF7A70, // red
            0xFF5DDB8B, // green
            0xFFE8C07D, // yellow
            0xFF7FB2FF, // blue
            0xFFC9A0F0, // magenta
            0xFF5FD8E0, // cyan
            0xFFC8CFDB, // white
            0xFF5C6679, // bright black
            0xFFFFA39A, // bright red
            0xFF8DF0B3, // bright green
            0xFFF6D79C, // bright yellow
            0xFFA9CAFF, // bright blue
            0xFFDFC2F8, // bright magenta
            0xFF96EAF0, // bright cyan
            0xFFF4F7FB, // bright white
    };

    private TermColors() {}

    /** 256 色调色板：前 16 个来自主题，16-231 是 6×6×6 立方，232-255 是灰阶 */
    public static int xterm(int idx) {
        if (idx < 16) return BASE[idx];
        if (idx < 232) {
            int i = idx - 16;
            int r = i / 36;
            int g = (i % 36) / 6;
            int b = i % 6;
            return 0xFF000000 | (level(r) << 16) | (level(g) << 8) | level(b);
        }
        int v = 8 + (idx - 232) * 10;
        return 0xFF000000 | (v << 16) | (v << 8) | v;
    }

    private static int level(int v) {
        return v == 0 ? 0 : 55 + v * 40;
    }

    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
}
