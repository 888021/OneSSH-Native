package com.onessh.app.term;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** AnsiParser 纯 Java 测试：不需要 Android / 真机。 */
public class AnsiParserTest {

    private static final class Fixture {
        final TerminalBuffer buffer = new TerminalBuffer(20, 5);
        final List<String> responses = new ArrayList<>();
        final AnsiParser parser = new AnsiParser(buffer, responses::add);

        void feed(String s) {
            byte[] data = s.getBytes(StandardCharsets.UTF_8);
            parser.feed(data, data.length);
        }

        String firstLine() {
            String line = buffer.lineTextAt(0);
            return line == null ? "" : line.trim();
        }
    }

    @Test
    public void plainTextIsStored() {
        Fixture f = new Fixture();
        f.feed("hello");
        assertEquals("hello", f.firstLine());
        assertEquals(5, f.buffer.cursorCol);
    }

    @Test
    public void utf8WideCharIsStored() {
        Fixture f = new Fixture();
        f.feed("中");
        assertEquals("中", f.firstLine());
        assertEquals(2, f.buffer.cursorCol);
    }

    @Test
    public void cursorPositionSequenceMovesCursor() {
        Fixture f = new Fixture();
        f.feed("\u001b[2;3H" + "X");
        assertEquals(1, f.buffer.cursorRow);
        assertEquals(3, f.buffer.cursorCol);
        assertEquals("X", f.buffer.lineTextAt(1).trim());
    }

    @Test
    public void sgrBoldIsSetAndReset() {
        Fixture f = new Fixture();
        f.feed("\u001b[1m");
        assertTrue((f.buffer.curAttr & TerminalBuffer.ATTR_BOLD) != 0);
        f.feed("\u001b[0m");
        assertEquals(0, f.buffer.curAttr);
    }

    @Test
    public void eraseDisplayClearsText() {
        Fixture f = new Fixture();
        f.feed("hello");
        f.feed("\u001b[2J");
        assertEquals("", f.firstLine());
    }

    @Test
    public void deviceStatusReportAnswersCursorPosition() {
        Fixture f = new Fixture();
        f.feed("\u001b[6n");
        assertEquals(1, f.responses.size());
        assertEquals("\u001b[1;1R", f.responses.get(0));
    }

    @Test
    public void altScreenModeSwitches() {
        Fixture f = new Fixture();
        f.feed("main");
        f.feed("\u001b[?1049h");
        assertTrue(f.buffer.isAltScreen());
        assertEquals("", f.firstLine());
        f.feed("\u001b[?1049l");
        assertTrue(!f.buffer.isAltScreen());
        assertEquals("main", f.firstLine());
    }
}
