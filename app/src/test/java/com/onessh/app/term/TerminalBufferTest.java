package com.onessh.app.term;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** TerminalBuffer 纯 Java 测试：不需要 Android / 真机。 */
public class TerminalBufferTest {

    @Test
    public void putCharAdvancesCursorAndStoresText() {
        TerminalBuffer b = new TerminalBuffer(10, 4);
        b.putChar('h');
        b.putChar('i');
        assertEquals(0, b.cursorRow);
        assertEquals(2, b.cursorCol);
        assertEquals("hi", b.lineTextAt(0).trim());
    }

    @Test
    public void wideCharTakesTwoCells() {
        assertEquals(1, TerminalBuffer.width('A'));
        assertEquals(2, TerminalBuffer.width('中'));
        assertEquals(0, TerminalBuffer.width(0x0301)); // 组合重音，不占格
        TerminalBuffer b = new TerminalBuffer(10, 4);
        b.putChar('中');
        assertEquals(2, b.cursorCol);
        assertEquals("中", b.lineTextAt(0).trim());
    }

    @Test
    public void lineFeedAtBottomScrollsIntoScrollback() {
        TerminalBuffer b = new TerminalBuffer(5, 2);
        b.putChar('a');
        b.lineFeed();
        b.putChar('b');
        b.lineFeed(); // 到底，滚一行
        assertEquals(1, b.scrollbackSize());
        assertEquals("a", b.lineTextAt(0).trim());
        assertEquals("b", b.lineTextAt(1).trim());
    }

    @Test
    public void eraseInDisplayClearsScreen() {
        TerminalBuffer b = new TerminalBuffer(10, 2);
        b.putChar('x');
        b.eraseInDisplay(2);
        assertEquals("", b.lineTextAt(0).trim());
        assertEquals(1, b.cursorCol); // erase 只清内容，不动光标
    }

    @Test
    public void altScreenRestoresMainScreen() {
        TerminalBuffer b = new TerminalBuffer(10, 2);
        b.putChar('m');
        b.enterAltScreen();
        assertTrue(b.isAltScreen());
        assertEquals("", b.lineTextAt(0).trim());
        b.putChar('a');
        b.exitAltScreen();
        assertFalse(b.isAltScreen());
        assertEquals("m", b.lineTextAt(0).trim());
    }

    @Test
    public void resizeKeepsExistingText() {
        TerminalBuffer b = new TerminalBuffer(5, 2);
        b.putChar('h');
        b.putChar('i');
        b.resize(10, 4);
        assertEquals(10, b.cols);
        assertEquals(4, b.rows);
        assertEquals("hi", b.lineTextAt(0).trim());
    }

    @Test
    public void lineTextAtOutOfRangeReturnsNull() {
        TerminalBuffer b = new TerminalBuffer(5, 2);
        assertNull(b.lineTextAt(-1));
        assertNull(b.lineTextAt(99));
    }

    @Test
    public void saveAndRestoreCursor() {
        TerminalBuffer b = new TerminalBuffer(10, 4);
        b.setCursor(1, 3);
        b.saveCursor();
        b.setCursor(0, 0);
        b.restoreCursor();
        assertEquals(1, b.cursorRow);
        assertEquals(3, b.cursorCol);
    }
}
