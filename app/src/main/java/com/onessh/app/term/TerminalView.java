package com.onessh.app.term;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.OverScroller;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 终端视图：把 TerminalBuffer 画出来，把触摸/键盘/输入法喂回去。
 *
 * 没有用隐藏的 EditText：直接实现 InputConnection，输入法的候选词在 commitText
 * 那一刻才发给远端，中文输入法也能正常用。
 */
public class TerminalView extends View {

    public interface Listener {
        void onInput(byte[] data);

        void onResize(int cols, int rows);

        /** Ctrl 锁存状态变化，用来给按键上高亮 */
        default void onCtrlLatch(boolean on) {
        }

        /** 长按终端：y 是按下时的触摸纵坐标，Activity 据此取行并弹复制菜单 */
        default void onLongPress(float y) {
        }
    }

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint();
    private final Paint decoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private TerminalBuffer buffer;
    private AnsiParser parser;
    private Listener listener;

    private float cellW = 8f;
    private float cellH = 16f;
    private float baseline = 13f;

    private int fontSizeSp = 13;

    private final OverScroller scroller;
    private float scrollPx;
    private VelocityTracker tracker;
    private float lastY;
    private boolean dragging;

    /* 长按复制：按住不动够久就高亮那一行并回调 Activity 弹菜单 */
    private final Handler longPressHandler = new Handler(Looper.getMainLooper());
    private Runnable longPressTask;
    private float downX;
    private float downY;
    private boolean longPressed;
    /** 正在高亮的行（回滚区 + 当前屏的统一行号），-1 表示没有 */
    private int selIndex = -1;

    private boolean cursorOn = true;
    private boolean attached;

    /** Ctrl 锁存：点一下 Ctrl 等于按住它，下一个字符会转成控制字符 */
    private boolean ctrlLatch;

    private final java.util.HashMap<Integer, Float> advCache = new java.util.HashMap<>();

    private final ConcurrentLinkedQueue<byte[]> pending = new ConcurrentLinkedQueue<>();
    private volatile boolean scheduled;

    private final Runnable blink = new Runnable() {
        @Override
        public void run() {
            cursorOn = !cursorOn;
            if (scrollPx <= 0.5f) invalidate();
            postDelayed(this, 530);
        }
    };

    public TerminalView(Context context) {
        this(context, null);
    }

    public TerminalView(Context context, AttributeSet attrs) {
        super(context, attrs);
        scroller = new OverScroller(context);
        setBackgroundColor(TermColors.BG);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);

        textPaint.setTypeface(Typeface.MONOSPACE);
        textPaint.setAntiAlias(true);
        decoPaint.setStyle(Paint.Style.FILL);

        applyTextSize();
        buffer = new TerminalBuffer(80, 24);
        parser = new AnsiParser(buffer, reply -> {
            Listener l = listener;
            if (l != null) l.onInput(reply.getBytes(StandardCharsets.UTF_8));
        });
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public TerminalBuffer buffer() {
        return buffer;
    }

    public void setFontSizeSp(int sp) {
        fontSizeSp = Math.max(9, Math.min(24, sp));
        applyTextSize();
        requestLayout();
        if (getWidth() > 0) computeGrid();
        invalidate();
    }

    public int fontSizeSp() {
        return fontSizeSp;
    }

    private void applyTextSize() {
        float px = fontSizeSp * getResources().getDisplayMetrics().scaledDensity;
        textPaint.setTextSize(px);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        cellH = (fm.descent - fm.ascent) * 1.16f;
        baseline = (cellH - (fm.descent - fm.ascent)) / 2f - fm.ascent;
        cellW = Math.max(1f, textPaint.measureText("M"));
        advCache.clear();
    }

    /* ------------------------------------------------------------- 数据 */

    public void feed(byte[] data) {
        if (data == null || data.length == 0) return;
        pending.add(data);
        if (!scheduled) {
            scheduled = true;
            post(this::drain);
        }
    }

    private void drain() {
        scheduled = false;
        int before = buffer.scrollbackSize();
        byte[] d;
        int guard = 0;
        while ((d = pending.poll()) != null && guard++ < 512) {
            parser.feed(d, d.length);
        }
        int added = buffer.scrollbackSize() - before;
        // 用户正在往回翻的时候，别让新输出把视野顶走
        if (scrollPx > 0.5f && added > 0) {
            scrollPx = Math.min(maxScrollPx(), scrollPx + added * cellH);
        }
        invalidate();
    }

    private float maxScrollPx() {
        return buffer.scrollbackSize() * cellH;
    }

    /* ------------------------------------------------------------- 布局 */

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        computeGrid();
    }

    private void computeGrid() {
        int cols = Math.max(2, (int) Math.floor(getWidth() / cellW));
        int rows = Math.max(2, (int) Math.floor(getHeight() / cellH));
        if (cols == buffer.cols && rows == buffer.rows) return;
        buffer.resize(cols, rows);
        scrollPx = 0;
        if (listener != null) listener.onResize(cols, rows);
        invalidate();
    }

    /* ------------------------------------------------------------- 绘制 */

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(TermColors.BG);
        int sbSize = buffer.scrollbackSize();
        int topIndex = sbSize - Math.round(scrollPx / cellH);

        for (int row = 0; row < buffer.rows; row++) {
            TerminalBuffer.Line line = lineAt(topIndex + row);
            if (line == null) continue;
            float top = row * cellH;
            if (topIndex + row == selIndex) {
                fillPaint.setColor(TermColors.SELECTION);
                canvas.drawRect(0, top, getWidth(), top + cellH, fillPaint);
            }
            drawBackgrounds(canvas, line, top);
            drawText(canvas, line, top);
        }

        // 光标只在贴底时显示
        if (scrollPx < 0.5f && buffer.cursorVisible && cursorOn && !buffer.isAltScreen()) {
            float x = buffer.cursorCol * cellW;
            float y = buffer.cursorRow * cellH;
            fillPaint.setColor(TermColors.CURSOR);
            float barW = Math.max(2f, cellW * 0.14f);
            canvas.drawRect(x, y + cellH * 0.12f, x + barW, y + cellH * 0.88f, fillPaint);
        } else if (scrollPx < 0.5f && buffer.cursorVisible && cursorOn && buffer.isAltScreen()) {
            float x = buffer.cursorCol * cellW;
            float y = buffer.cursorRow * cellH;
            fillPaint.setColor(TermColors.CURSOR);
            float barW = Math.max(2f, cellW * 0.14f);
            canvas.drawRect(x, y + cellH * 0.12f, x + barW, y + cellH * 0.88f, fillPaint);
        }

        if (scrollPx > 0.5f) drawScrollbar(canvas);
    }

    private TerminalBuffer.Line lineAt(int index) {
        int sb = buffer.scrollbackSize();
        if (index < 0) return null;
        if (index < sb) return buffer.scrollbackAt(index);
        int r = index - sb;
        if (r < 0 || r >= buffer.rows) return null;
        return buffer.line(r);
    }

    private void drawScrollbar(Canvas canvas) {
        float max = maxScrollPx();
        if (max <= 0) return;
        float h = getHeight();
        float thumbH = Math.max(h * 0.08f, h * h / (h + max));
        float progress = 1f - Math.min(1f, scrollPx / max);
        float top = progress * (h - thumbH);
        fillPaint.setColor(0x33FFFFFF);
        float w = Math.max(2f, getResources().getDisplayMetrics().density * 2.5f);
        float x = getWidth() - w - getResources().getDisplayMetrics().density * 2;
        canvas.drawRoundRect(x, top, x + w, top + thumbH, w / 2f, w / 2f, fillPaint);
    }

    private int resolveFg(TerminalBuffer.Line l, int i) {
        int at = l.at[i];
        int f = l.fg[i] == TerminalBuffer.DEFAULT_FG ? TermColors.FG : l.fg[i];
        int bg = l.bg[i] == TerminalBuffer.DEFAULT_BG ? TermColors.BG : l.bg[i];
        if ((at & TerminalBuffer.ATTR_INVERSE) != 0) {
            f = bg;
        }
        if ((at & TerminalBuffer.ATTR_DIM) != 0) f = dim(f);
        return f;
    }

    private int resolveBg(TerminalBuffer.Line l, int i) {
        int at = l.at[i];
        int f = l.fg[i] == TerminalBuffer.DEFAULT_FG ? TermColors.FG : l.fg[i];
        int bg = l.bg[i] == TerminalBuffer.DEFAULT_BG ? TermColors.BG : l.bg[i];
        if ((at & TerminalBuffer.ATTR_INVERSE) != 0) return f;
        return bg;
    }

    private static int dim(int color) {
        int a = (color >>> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * 0.62f);
        int g = (int) (((color >> 8) & 0xFF) * 0.62f);
        int b = (int) ((color & 0xFF) * 0.62f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void drawBackgrounds(Canvas canvas, TerminalBuffer.Line l, float top) {
        int i = 0;
        while (i < buffer.cols) {
            int color = resolveBg(l, i);
            int start = i;
            i++;
            while (i < buffer.cols && resolveBg(l, i) == color) i++;
            if (color != TermColors.BG) {
                fillPaint.setColor(color);
                canvas.drawRect(start * cellW, top, i * cellW, top + cellH, fillPaint);
            }
        }
    }

    private void drawText(Canvas canvas, TerminalBuffer.Line l, float top) {
        float y = top + baseline;
        int i = 0;
        while (i < buffer.cols) {
            int at = l.at[i];
            int cp = l.cp[i];

            if ((at & TerminalBuffer.ATTR_WIDE_TAIL) != 0 || cp == 0) {
                i++;
                continue;
            }

            int w = TerminalBuffer.width(cp);
            int fg = resolveFg(l, i);
            boolean bold = (at & TerminalBuffer.ATTR_BOLD) != 0;

            if (w == 1 && cp >= 0x20 && cp <= 0x7E) {
                // ASCII 的可打印字符：等宽字体前进宽度可靠，合并成一次 drawText
                int start = i;
                StringBuilder sb = new StringBuilder();
                while (i < buffer.cols) {
                    int a = l.at[i];
                    int c = l.cp[i];
                    if ((a & TerminalBuffer.ATTR_WIDE_TAIL) != 0) break;
                    if (c < 0x20 || c > 0x7E) break;
                    if (resolveFg(l, i) != fg) break;
                    if (((a & TerminalBuffer.ATTR_BOLD) != 0) != bold) break;
                    if ((a & (TerminalBuffer.ATTR_UNDERLINE | TerminalBuffer.ATTR_STRIKE))
                            != (at & (TerminalBuffer.ATTR_UNDERLINE | TerminalBuffer.ATTR_STRIKE))) break;
                    sb.append((char) c);
                    i++;
                }
                if (sb.length() == 0) {
                    i = start + 1;
                    continue;
                }
                textPaint.setColor(fg);
                textPaint.setFakeBoldText(bold);
                canvas.drawText(sb.toString(), start * cellW, y, textPaint);
                decorate(canvas, l, start, i, top, fg);
            } else {
                drawGlyph(canvas, cp, i, top, fg, bold, w);
                decorate(canvas, l, i, i + w, top, fg);
                i += w;
            }
        }
    }

    /**
     * 画一个非 ASCII 字符。
     *
     * 制表符（┌─┐）、箭头这类符号不在等宽字体里，会回退到中日韩字体，
     * 而中日韩字体里它们是全角宽度 —— 直接画会把后面的字符整体挤歪。
     * 这里按格宽做一次水平缩放，保证每个字符正好落在自己的格子里。
     */
    private void drawGlyph(Canvas canvas, int cp, int cell, float top,
                           int fg, boolean bold, int cells) {
        String s = new String(Character.toChars(cp));
        textPaint.setColor(fg);
        textPaint.setFakeBoldText(bold);

        float x = cell * cellW;
        float target = cells * cellW;
        float advance = advance(cp, s);

        if (advance > target + 0.01f) {
            canvas.save();
            canvas.scale(target / advance, 1f, x, top);
            canvas.drawText(s, x, top + baseline, textPaint);
            canvas.restore();
        } else {
            canvas.drawText(s, x, top + baseline, textPaint);
        }
    }

    private void decorate(Canvas canvas, TerminalBuffer.Line l, int from, int to, float top, int fg) {
        if (from >= to) return;
        int at = l.at[from];
        if ((at & TerminalBuffer.ATTR_UNDERLINE) != 0) {
            decoPaint.setColor(fg);
            float uy = top + cellH * 0.88f;
            canvas.drawRect(from * cellW, uy, to * cellW, uy + Math.max(1f, cellH * 0.05f), decoPaint);
        }
        if ((at & TerminalBuffer.ATTR_STRIKE) != 0) {
            decoPaint.setColor(fg);
            float sy = top + cellH * 0.55f;
            canvas.drawRect(from * cellW, sy, to * cellW, sy + Math.max(1f, cellH * 0.05f), decoPaint);
        }
    }

    private float advance(int cp, String s) {
        Float cached = advCache.get(cp);
        if (cached != null) return cached;
        float a = textPaint.measureText(s);
        if (advCache.size() > 4096) advCache.clear();
        advCache.put(cp, a);
        return a;
    }

    /* ------------------------------------------------------------- 交互 */

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (tracker != null) tracker.recycle();
                tracker = VelocityTracker.obtain();
                tracker.addMovement(ev);
                scroller.forceFinished(true);
                lastY = ev.getY();
                dragging = false;
                longPressed = false;
                downX = ev.getX();
                downY = ev.getY();
                if (selIndex >= 0) {
                    selIndex = -1;
                    invalidate();
                }
                requestFocus();
                cancelPendingLongPress();
                longPressTask = () -> {
                    longPressed = true;
                    selIndex = indexAtY(downY);
                    invalidate();
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    if (listener != null) listener.onLongPress(downY);
                };
                longPressHandler.postDelayed(longPressTask,
                        ViewConfiguration.getLongPressTimeout());
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (tracker != null) tracker.addMovement(ev);
                float dy = lastY - ev.getY();
                int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (Math.abs(ev.getX() - downX) > slop
                        || Math.abs(ev.getY() - downY) > slop) {
                    cancelPendingLongPress();
                }
                if (Math.abs(dy) > 1) {
                    dragging = true;
                    scrollPx = Math.max(0, Math.min(maxScrollPx(), scrollPx + dy));
                    lastY = ev.getY();
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                cancelPendingLongPress();
                if (tracker != null) {
                    tracker.addMovement(ev);
                    tracker.computeCurrentVelocity(1000);
                    float vy = tracker.getYVelocity();
                    tracker.recycle();
                    tracker = null;
                    if (dragging && Math.abs(vy) > 400) {
                        scroller.fling(0, (int) scrollPx, 0, (int) -vy, 0, 0, 0, (int) maxScrollPx());
                        postInvalidateOnAnimation();
                    } else if (!dragging && !longPressed) {
                        showIme();
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                cancelPendingLongPress();
                if (tracker != null) {
                    tracker.recycle();
                    tracker = null;
                }
                return true;
            }
            default:
                return super.onTouchEvent(ev);
        }
    }

    private void cancelPendingLongPress() {
        if (longPressTask != null) {
            longPressHandler.removeCallbacks(longPressTask);
            longPressTask = null;
        }
    }

    /** 触摸纵坐标换算成“回滚区 + 当前屏”的统一行号，跟 onDraw 用的算法一致 */
    private int indexAtY(float y) {
        int topIndex = buffer.scrollbackSize() - Math.round(scrollPx / cellH);
        return topIndex + (int) (y / cellH);
    }

    /** 取触摸点那一行的文本（去掉了行尾空格），点到空白处返回空串 */
    public String lineTextAt(float y) {
        String t = buffer.lineTextAt(indexAtY(y));
        return t == null ? "" : t;
    }

    /** 清掉长按高亮 */
    public void clearSelection() {
        if (selIndex >= 0) {
            selIndex = -1;
            invalidate();
        }
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollPx = Math.max(0, Math.min(maxScrollPx(), scroller.getCurrY()));
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        postDelayed(blink, 530);
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        removeCallbacks(blink);
        super.onDetachedFromWindow();
    }

    /* ------------------------------------------------------------- 输入 */

    public void sendText(String s) {
        if (s == null || s.isEmpty()) return;
        scrollPx = 0;

        if (ctrlLatch) {
            ctrlLatch = false;
            if (listener != null) listener.onCtrlLatch(false);
            if (s.length() == 1) {
                int code = controlCode(s.charAt(0));
                if (code > 0) s = String.valueOf((char) code);
            }
        }

        if (listener != null) listener.onInput(s.getBytes(StandardCharsets.UTF_8));
        invalidate();
    }

    /** a-z / A-Z / [ \\ ] ^ _ ? 对应的控制字符，其他返回 -1 */
    private static int controlCode(char ch) {
        char c = Character.toUpperCase(ch);
        if (c >= 'A' && c <= 'Z') return c - 'A' + 1;
        switch (ch) {
            case '[':
            case '3':
                return 27;
            case '\\':
            case '4':
                return 28;
            case ']':
            case '5':
                return 29;
            case '^':
            case '6':
                return 30;
            case '_':
            case '7':
            case '/':
                return 31;
            case '?':
            case '8':
                return 127;
            default:
                return -1;
        }
    }

    /** 点一下 Ctrl = 按住 Ctrl；再点一下取消 */
    public boolean toggleCtrlLatch() {
        ctrlLatch = !ctrlLatch;
        if (listener != null) listener.onCtrlLatch(ctrlLatch);
        return ctrlLatch;
    }

    public boolean isCtrlLatched() {
        return ctrlLatch;
    }

    public void clearCtrlLatch() {
        if (!ctrlLatch) return;
        ctrlLatch = false;
        if (listener != null) listener.onCtrlLatch(false);
    }

    public void scrollToBottom() {
        scrollPx = 0;
        invalidate();
    }

    public void clearScreen() {
        buffer.eraseInDisplay(3);
        scrollPx = 0;
        invalidate();
    }

    public String allText() {
        return buffer.allText();
    }

    public void showIme() {
        requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
    }

    public void hideIme() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(getWindowToken(), 0);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo out) {
        // 终端不是密码框：不要用 TYPE_TEXT_VARIATION_VISIBLE_PASSWORD。
        // 之前用它是想禁联想，但鸿蒙/华为会把带密码变体的输入框当密码框，
        // 连接后一弹键盘就强制拉起安全键盘（无联想、无剪贴板、布局受限）。
        // 改回普通文本输入，TYPE_TEXT_FLAG_NO_SUGGESTIONS 继续禁联想/学习，
        // 中文拼音照常走 commitText。
        out.inputType = InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        out.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        out.initialSelStart = 0;
        out.initialSelEnd = 0;
        return new BaseInputConnection(this, false) {
            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                sendText(text.toString());
                return true;
            }

            @Override
            public boolean setComposingText(CharSequence text, int newCursorPosition) {
                // 组合中（拼音还没选字）不发给远端
                return true;
            }

            @Override
            public boolean finishComposingText() {
                return true;
            }

            @Override
            public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < beforeLength; i++) sb.append('\u007f');
                sendText(sb.toString());
                return true;
            }

            @Override
            public boolean sendKeyEvent(KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (handleKey(event)) return true;
                }
                return true;
            }
        };
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (handleKey(event)) return true;
        return super.onKeyDown(keyCode, event);
    }

    private boolean handleKey(KeyEvent e) {
        int kc = e.getKeyCode();
        boolean ctrl = e.isCtrlPressed() || ctrlLatch;
        boolean alt = e.isAltPressed();

        switch (kc) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                sendText(ctrl ? "\n" : "\r");
                return true;
            case KeyEvent.KEYCODE_DEL:
                sendText("\u007f");
                return true;
            case KeyEvent.KEYCODE_FORWARD_DEL:
                sendText("\u001b[3~");
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                sendText("\u001b[A");
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                sendText("\u001b[B");
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                sendText("\u001b[C");
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                sendText("\u001b[D");
                return true;
            case KeyEvent.KEYCODE_TAB:
                sendText("\t");
                return true;
            case KeyEvent.KEYCODE_ESCAPE:
                sendText("\u001b");
                return true;
            case KeyEvent.KEYCODE_MOVE_HOME:
                sendText("\u001b[H");
                return true;
            case KeyEvent.KEYCODE_MOVE_END:
                sendText("\u001b[F");
                return true;
            case KeyEvent.KEYCODE_PAGE_UP:
                sendText("\u001b[5~");
                return true;
            case KeyEvent.KEYCODE_PAGE_DOWN:
                sendText("\u001b[6~");
                return true;
            case KeyEvent.KEYCODE_F1:
                sendText("\u001bOP");
                return true;
            case KeyEvent.KEYCODE_F2:
                sendText("\u001bOQ");
                return true;
            case KeyEvent.KEYCODE_F3:
                sendText("\u001bOR");
                return true;
            case KeyEvent.KEYCODE_F4:
                sendText("\u001bOS");
                return true;
            case KeyEvent.KEYCODE_F5:
                sendText("\u001b[15~");
                return true;
            case KeyEvent.KEYCODE_F6:
                sendText("\u001b[17~");
                return true;
            case KeyEvent.KEYCODE_F7:
                sendText("\u001b[18~");
                return true;
            case KeyEvent.KEYCODE_F8:
                sendText("\u001b[19~");
                return true;
            case KeyEvent.KEYCODE_F9:
                sendText("\u001b[20~");
                return true;
            case KeyEvent.KEYCODE_F10:
                sendText("\u001b[21~");
                return true;
            case KeyEvent.KEYCODE_F11:
                sendText("\u001b[23~");
                return true;
            case KeyEvent.KEYCODE_F12:
                sendText("\u001b[24~");
                return true;
            case KeyEvent.KEYCODE_BACK:
                return false;
            default:
                break;
        }

        if (ctrl && kc >= KeyEvent.KEYCODE_A && kc <= KeyEvent.KEYCODE_Z) {
            sendText(String.valueOf((char) (kc - KeyEvent.KEYCODE_A + 1)));
            return true;
        }
        if (ctrl && (kc == KeyEvent.KEYCODE_LEFT_BRACKET || kc == KeyEvent.KEYCODE_RIGHT_BRACKET)) {
            sendText(kc == KeyEvent.KEYCODE_LEFT_BRACKET ? "\u001b" : "\u001d");
            return true;
        }

        int unicode = e.getUnicodeChar();
        if (!ctrl && !alt && unicode != 0) {
            sendText(new String(Character.toChars(unicode)));
            return true;
        }
        return false;
    }

    /** 硬件键盘/手柄的按键也能进来 */
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0
                && event.getAction() == MotionEvent.ACTION_SCROLL) {
            float v = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            scrollPx = Math.max(0, Math.min(maxScrollPx(), scrollPx + v * cellH * 3));
            invalidate();
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    public boolean isAttached() {
        return attached;
    }
}
