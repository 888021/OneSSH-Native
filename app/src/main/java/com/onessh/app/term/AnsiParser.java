package com.onessh.app.term;

/**
 * VT100 / xterm 转义序列解析。
 *
 * 覆盖 shell 日常与 vim/htop 这类全屏程序需要的部分：
 * 光标移动、擦除、插入/删除、滚动区、备用屏、SGR（16/256/真彩）、UTF-8、宽字符。
 * 不做：鼠标上报、sixel、超链接、DEC 字符集（这些在手机 SSH 里用不到）。
 */
public final class AnsiParser {

    public interface Sink {
        /** 需要回写给远端的应答（DSR / DA） */
        void onResponse(String reply);
    }

    private static final int GROUND = 0;
    private static final int ESC = 1;
    private static final int CSI = 2;
    private static final int OSC = 3;
    private static final int SKIP = 4;

    private final TerminalBuffer b;
    private final Sink sink;

    private int state = GROUND;
    private final int[] params = new int[24];
    private int paramCount;
    private int param;
    private boolean hasParam;
    private char privateMarker;
    private int intermediate;
    private boolean oscEsc;

    private int utf8Need;
    private int utf8Cp;

    public AnsiParser(TerminalBuffer buffer, Sink sink) {
        this.b = buffer;
        this.sink = sink;
    }

    public void feed(byte[] data, int len) {
        for (int i = 0; i < len; i++) {
            int c = data[i] & 0xFF;

            if (utf8Need > 0 && state == GROUND) {
                if ((c & 0xC0) == 0x80) {
                    utf8Cp = (utf8Cp << 6) | (c & 0x3F);
                    if (--utf8Need == 0) b.putChar(utf8Cp);
                    continue;
                }
                utf8Need = 0;
            }

            if (c >= 0x80 && state == GROUND) {
                if ((c & 0xE0) == 0xC0) {
                    utf8Cp = c & 0x1F;
                    utf8Need = 1;
                } else if ((c & 0xF0) == 0xE0) {
                    utf8Cp = c & 0x0F;
                    utf8Need = 2;
                } else if ((c & 0xF8) == 0xF0) {
                    utf8Cp = c & 0x07;
                    utf8Need = 3;
                }
                continue;
            }

            feedByte(c);
        }
    }

    private void feedByte(int c) {
        switch (state) {
            case GROUND:
                ground(c);
                break;
            case ESC:
                esc(c);
                break;
            case CSI:
                csi(c);
                break;
            case OSC:
                if (c == 0x07) state = GROUND;
                else if (c == 0x1B) oscEsc = true;
                else if (oscEsc) {
                    oscEsc = false;
                    state = (c == '\\') ? GROUND : ESC;
                }
                break;
            case SKIP:
                state = (c == 0x1B) ? ESC : GROUND;
                break;
            default:
                state = GROUND;
        }
    }

    private void ground(int c) {
        switch (c) {
            case 0x1B:
                state = ESC;
                break;
            case 0x0A:
            case 0x0B:
            case 0x0C:
                b.lineFeed();
                break;
            case 0x0D:
                b.carriageReturn();
                break;
            case 0x08:
                b.backspace();
                break;
            case 0x09:
                b.tab();
                break;
            case 0x07:
                break;
            default:
                if (c >= 0x20 && c < 0x7F) b.putChar(c);
                break;
        }
    }

    private void esc(int c) {
        switch (c) {
            case '[':
                resetParams();
                state = CSI;
                break;
            case ']':
                state = OSC;
                oscEsc = false;
                break;
            case '7':
                b.saveCursor();
                state = GROUND;
                break;
            case '8':
                b.restoreCursor();
                state = GROUND;
                break;
            case 'D':
                b.lineFeed();
                state = GROUND;
                break;
            case 'E':
                b.carriageReturn();
                b.lineFeed();
                state = GROUND;
                break;
            case 'M':
                b.reverseIndex();
                state = GROUND;
                break;
            case 'c':
                b.reset();
                state = GROUND;
                break;
            case '(':
            case ')':
            case '*':
            case '+':
            case '#':
            case '%':
                state = SKIP;
                break;
            case '=':
            case '>':
            case 'H':
            case 'Z':
                state = GROUND;
                break;
            default:
                state = GROUND;
                break;
        }
    }

    private void resetParams() {
        paramCount = 0;
        param = 0;
        hasParam = false;
        privateMarker = 0;
        intermediate = 0;
        java.util.Arrays.fill(params, 0);
    }

    private void pushParam() {
        if (paramCount < params.length) params[paramCount++] = hasParam ? param : 0;
        param = 0;
        hasParam = false;
    }

    private int p(int i, int def) {
        if (i >= paramCount) return def;
        int v = params[i];
        return v == 0 ? def : v;
    }

    private int p0(int i) {
        return i < paramCount ? params[i] : 0;
    }

    private void csi(int c) {
        if (c >= '0' && c <= '9') {
            param = param * 10 + (c - '0');
            hasParam = true;
            return;
        }
        if (c == ';' || c == ':') {
            pushParam();
            return;
        }
        if (c == '?' || c == '>' || c == '<' || c == '=') {
            privateMarker = (char) c;
            return;
        }
        if (c >= 0x20 && c <= 0x2F) {
            intermediate = c;
            return;
        }
        if (c >= 0x40 && c <= 0x7E) {
            if (hasParam || paramCount > 0) pushParam();
            dispatch((char) c);
            state = GROUND;
        }
    }

    private void dispatch(char f) {
        switch (f) {
            case 'A':
                b.moveCursor(-p(0, 1), 0);
                break;
            case 'B':
            case 'e':
                b.moveCursor(p(0, 1), 0);
                break;
            case 'C':
            case 'a':
                b.moveCursor(0, p(0, 1));
                break;
            case 'D':
                b.moveCursor(0, -p(0, 1));
                break;
            case 'E':
                b.moveCursor(p(0, 1), 0);
                b.carriageReturn();
                break;
            case 'F':
                b.moveCursor(-p(0, 1), 0);
                b.carriageReturn();
                break;
            case 'G':
            case '`':
                b.setCursor(b.cursorRow, p(0, 1) - 1);
                break;
            case 'd':
                b.setCursor(p(0, 1) - 1, b.cursorCol);
                break;
            case 'H':
            case 'f':
                b.setCursor(p(0, 1) - 1, p(1, 1) - 1);
                break;
            case 'J':
                b.eraseInDisplay(p0(0));
                break;
            case 'K':
                b.eraseInLine(p0(0));
                break;
            case 'L':
                b.insertLines(p(0, 1));
                break;
            case 'M':
                b.deleteLines(p(0, 1));
                break;
            case 'P':
                b.deleteChars(p(0, 1));
                break;
            case '@':
                b.insertChars(p(0, 1));
                break;
            case 'X':
                b.eraseChars(p(0, 1));
                break;
            case 'S':
                b.scrollUp(p(0, 1));
                break;
            case 'T':
                b.scrollDown(p(0, 1));
                break;
            case 'Z':
                for (int i = 0; i < p(0, 1); i++) b.backTab();
                break;
            case 'm':
                sgr();
                break;
            case 'r':
                b.setScrollRegion(p(0, 1) - 1, p(1, b.rows) - 1);
                break;
            case 's':
                b.saveCursor();
                break;
            case 'u':
                b.restoreCursor();
                break;
            case 'h':
                modes(true);
                break;
            case 'l':
                modes(false);
                break;
            case 'n':
                if (sink != null) {
                    int mode = p0(0);
                    if (mode == 5) sink.onResponse("\u001b[0n");
                    else if (mode == 6) {
                        sink.onResponse("\u001b[" + (b.cursorRow + 1) + ";" + (b.cursorCol + 1) + "R");
                    }
                }
                break;
            case 'c':
                if (sink != null && privateMarker == 0) sink.onResponse("\u001b[?1;2c");
                break;
            default:
                break;
        }
    }

    private void modes(boolean on) {
        for (int i = 0; i < Math.max(1, paramCount); i++) {
            int m = params[i];
            if (privateMarker == '?') {
                switch (m) {
                    case 1:
                        break;
                    case 6:
                        b.originMode = on;
                        b.setCursor(0, 0);
                        break;
                    case 7:
                        b.autowrap = on;
                        break;
                    case 25:
                        b.cursorVisible = on;
                        break;
                    case 47:
                    case 1047:
                    case 1049:
                        if (on) b.enterAltScreen();
                        else b.exitAltScreen();
                        break;
                    default:
                        break;
                }
            } else {
                switch (m) {
                    case 4:
                        break;
                    default:
                        break;
                }
            }
        }
    }

    private void sgr() {
        if (paramCount == 0) {
            b.curFg = TerminalBuffer.DEFAULT_FG;
            b.curBg = TerminalBuffer.DEFAULT_BG;
            b.curAttr = 0;
            return;
        }
        int i = 0;
        while (i < paramCount) {
            int v = params[i];
            if (v == 0) {
                b.curFg = TerminalBuffer.DEFAULT_FG;
                b.curBg = TerminalBuffer.DEFAULT_BG;
                b.curAttr = 0;
                i++;
            } else if (v == 1) {
                b.curAttr |= TerminalBuffer.ATTR_BOLD;
                i++;
            } else if (v == 2) {
                b.curAttr |= TerminalBuffer.ATTR_DIM;
                i++;
            } else if (v == 3) {
                b.curAttr |= TerminalBuffer.ATTR_ITALIC;
                i++;
            } else if (v == 4) {
                b.curAttr |= TerminalBuffer.ATTR_UNDERLINE;
                i++;
            } else if (v == 7) {
                b.curAttr |= TerminalBuffer.ATTR_INVERSE;
                i++;
            } else if (v == 9) {
                b.curAttr |= TerminalBuffer.ATTR_STRIKE;
                i++;
            } else if (v == 21 || v == 22) {
                b.curAttr &= ~(TerminalBuffer.ATTR_BOLD | TerminalBuffer.ATTR_DIM);
                i++;
            } else if (v == 23) {
                b.curAttr &= ~TerminalBuffer.ATTR_ITALIC;
                i++;
            } else if (v == 24) {
                b.curAttr &= ~TerminalBuffer.ATTR_UNDERLINE;
                i++;
            } else if (v == 27) {
                b.curAttr &= ~TerminalBuffer.ATTR_INVERSE;
                i++;
            } else if (v == 29) {
                b.curAttr &= ~TerminalBuffer.ATTR_STRIKE;
                i++;
            } else if (v >= 30 && v <= 37) {
                b.curFg = TermColors.xterm(v - 30);
                i++;
            } else if (v == 39) {
                b.curFg = TerminalBuffer.DEFAULT_FG;
                i++;
            } else if (v >= 40 && v <= 47) {
                b.curBg = TermColors.xterm(v - 40);
                i++;
            } else if (v == 49) {
                b.curBg = TerminalBuffer.DEFAULT_BG;
                i++;
            } else if (v >= 90 && v <= 97) {
                b.curFg = TermColors.xterm(v - 90 + 8);
                i++;
            } else if (v >= 100 && v <= 107) {
                b.curBg = TermColors.xterm(v - 100 + 8);
                i++;
            } else if (v == 38 || v == 48) {
                boolean fg = v == 38;
                if (i + 1 < paramCount) {
                    int kind = params[i + 1];
                    if (kind == 5 && i + 2 < paramCount) {
                        int color = TermColors.xterm(params[i + 2]);
                        if (fg) b.curFg = color;
                        else b.curBg = color;
                        i += 3;
                    } else if (kind == 2 && i + 4 < paramCount) {
                        int color = TermColors.rgb(params[i + 2], params[i + 3], params[i + 4]);
                        if (fg) b.curFg = color;
                        else b.curBg = color;
                        i += 5;
                    } else {
                        i += 2;
                    }
                } else {
                    i++;
                }
            } else {
                i++;
            }
        }
    }
}
