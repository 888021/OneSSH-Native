package com.onessh.app.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * 界面零件。样式对齐网页版：胶囊搜索框、描边输入框、下划线标签页、
 * 白卡片 + 1px 边线、橙色的主按钮。
 */
public final class Widgets {

    public interface OnPick {
        void pick(int index);
    }

    public interface OnClick {
        void click();
    }

    private Widgets() {}

    /* ------------------------------------------------------------- 骨架 */

    /** 纸色底、纵向铺满的根布局 */
    public static LinearLayout screen(Context c) {
        LinearLayout l = UI.column(c);
        l.setBackgroundColor(UI.PAPER);
        return l;
    }

    public static LinearLayout darkScreen(Context c) {
        LinearLayout l = UI.column(c);
        l.setBackgroundColor(UI.TERM_BG);
        return l;
    }

    public static ScrollView scroll(Context c) {
        ScrollView s = new ScrollView(c);
        s.setFillViewport(true);
        s.setVerticalScrollBarEnabled(false);
        s.setClipToPadding(false);
        return s;
    }

    /** 顶栏：返回 + 标题 + 右侧动作 */
    public static LinearLayout bar(Context c, CharSequence title, Runnable onBack, View right) {
        LinearLayout bar = UI.row(c);
        bar.setBackgroundColor(UI.PAPER);
        UI.padding(bar, 8, 6, 12, 6);
        bar.setMinimumHeight(UI.dp(54));

        if (onBack != null) {
            ImageView back = UI.icon(c, "back", UI.INK, 21);
            LinearLayout holder = UI.row(c);
            holder.setGravity(Gravity.CENTER);
            holder.setLayoutParams(new LinearLayout.LayoutParams(UI.dp(44), UI.dp(44)));
            holder.addView(back);
            holder.setBackground(UI.ripple(0x00000000, 22f));
            holder.setOnClickListener(v -> onBack.run());
            bar.addView(holder);
        } else {
            bar.addView(UI.gap(c, 12));
        }

        TextView t = UI.medium(c, title, 16.5f, UI.INK);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = UI.dp(6);
        t.setLayoutParams(tp);
        bar.addView(t);

        if (right != null) bar.addView(right);
        return bar;
    }

    public static LinearLayout darkBar(Context c, CharSequence title, CharSequence sub,
                                       Runnable onBack, View... actions) {
        LinearLayout bar = UI.row(c);
        bar.setBackgroundColor(UI.TERM_BAR);
        UI.padding(bar, 6, 6, 6, 6);
        bar.setMinimumHeight(UI.dp(52));

        if (onBack != null) {
            ImageView back = UI.icon(c, "back", UI.TERM_FG, 20);
            LinearLayout holder = UI.row(c);
            holder.setGravity(Gravity.CENTER);
            holder.setLayoutParams(new LinearLayout.LayoutParams(UI.dp(42), UI.dp(42)));
            holder.setBackground(UI.ripple(0x00000000, 21f));
            holder.addView(back);
            holder.setOnClickListener(v -> onBack.run());
            bar.addView(holder);
        }

        LinearLayout titles = UI.column(c);
        TextView t = UI.medium(c, title, 14f, UI.TERM_FG);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(t);
        if (sub != null && sub.length() > 0) {
            TextView s = UI.text(c, sub, 10f, UI.TERM_MUTED);
            s.setSingleLine(true);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sp.topMargin = UI.dp(2);
            s.setLayoutParams(sp);
            titles.addView(s);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = UI.dp(4);
        titles.setLayoutParams(lp);
        bar.addView(titles);

        for (View a : actions) {
            if (a != null) bar.addView(a);
        }
        return bar;
    }

    /** 顶栏上的图标按钮（44dp 触摸区） */
    public static View barIcon(Context c, String name, int color, Runnable action) {
        ImageView iv = UI.icon(c, name, color, 19);
        LinearLayout holder = UI.row(c);
        holder.setGravity(Gravity.CENTER);
        holder.setLayoutParams(new LinearLayout.LayoutParams(UI.dp(42), UI.dp(42)));
        holder.setBackground(UI.ripple(0x00000000, 21f));
        holder.addView(iv);
        if (action != null) holder.setOnClickListener(v -> action.run());
        return holder;
    }

    /* ------------------------------------------------------------- 文本 */

    public static TextView eyebrow(Context c, CharSequence s) {
        TextView t = UI.eyebrow(c, s, UI.MUTED2);
        t.setLetterSpacing(0.2f);
        return t;
    }

    public static TextView bigTitle(Context c, CharSequence s) {
        TextView t = UI.text(c, s, 27f, UI.INK);
        t.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        t.setLetterSpacing(-0.035f);
        return t;
    }

    public static TextView hint(Context c, CharSequence s) {
        return UI.text(c, s, 12.5f, UI.MUTED);
    }

    public static TextView monoSmall(Context c, CharSequence s, int color) {
        return UI.mono(c, s, 11.5f, color);
    }

    /* ------------------------------------------------------------- 表单 */

    private static Drawable inputBg() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, UI.bg(UI.CARD, 9f, UI.ACCENT, 1.4f));
        s.addState(new int[]{}, UI.bg(UI.CARD, 9f, UI.LINE, 1f));
        return s;
    }

    public static final class Field {
        public final LinearLayout root;
        public final EditText input;

        Field(LinearLayout root, EditText input) {
            this.root = root;
            this.input = input;
        }

        public String text() {
            return input.getText().toString();
        }

        public void setText(String s) {
            input.setText(s == null ? "" : s);
        }
    }

    public static Field field(Context c, String label, String hintText, int inputType) {
        LinearLayout root = UI.column(c);
        if (label != null && !label.isEmpty()) {
            TextView l = UI.medium(c, label, 11f, UI.MUTED);
            l.setLetterSpacing(0.06f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = UI.dp(6);
            l.setLayoutParams(lp);
            root.addView(l);
        }

        EditText e = new EditText(c);
        e.setBackground(inputBg());
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        e.setTypeface(UI.SANS);
        e.setTextColor(UI.INK);
        e.setHintTextColor(UI.MUTED2);
        e.setHint(hintText == null ? "" : hintText);
        e.setInputType(inputType);
        e.setPadding(UI.dp(12), UI.dp(11), UI.dp(12), UI.dp(11));
        e.setIncludeFontPadding(false);
        e.setSingleLine(!isMultiline(inputType));
        if (isMultiline(inputType)) {
            e.setGravity(Gravity.TOP | Gravity.START);
            e.setMinLines(3);
        }
        e.setBackground(inputBg());
        root.addView(e, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return new Field(root, e);
    }

    private static boolean isMultiline(int inputType) {
        int cls = inputType & InputType.TYPE_MASK_CLASS;
        int flags = inputType & InputType.TYPE_MASK_FLAGS;
        return cls == InputType.TYPE_CLASS_TEXT && (flags & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
    }

    /** 选择器行：图标 + 标题/当前值 + 右侧箭头 */
    public static LinearLayout pickRow(Context c, String iconName, String label,
                                       String value, Runnable onClick) {
        LinearLayout row = UI.row(c);
        row.setBackground(UI.ripple(UI.bg(UI.CARD, 9f, UI.LINE, 1f), 9f));
        UI.padding(row, 12, 10, 12, 10);
        row.setMinimumHeight(UI.dp(46));

        if (iconName != null) {
            ImageView iv = UI.icon(c, iconName, UI.MUTED2, 17);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UI.dp(17), UI.dp(17));
            lp.rightMargin = UI.dp(9);
            iv.setLayoutParams(lp);
            row.addView(iv);
        }

        LinearLayout col = UI.column(c);
        if (value != null && !value.isEmpty()) {
            if (label != null) col.addView(UI.text(c, label, 10.5f, UI.MUTED2));
            TextView v = UI.medium(c, value, 13.5f, UI.INK);
            v.setSingleLine(true);
            v.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(v);
        } else {
            TextView v = UI.text(c, label, 13.5f, UI.MUTED2);
            v.setSingleLine(true);
            col.addView(v);
        }
        row.addView(col);
        UI.weight(col, 1f);

        row.addView(UI.icon(c, "chevron", UI.MUTED2, 15));
        if (onClick != null) row.setOnClickListener(v -> onClick.run());
        return row;
    }

    /** 表单里的一段：白色卡片 + 内边距 */
    public static LinearLayout cardBox(Context c) {
        LinearLayout l = UI.column(c);
        l.setBackground(UI.bg(UI.CARD, 14f, UI.LINE, 1f));
        l.setElevation(UI.dp(1f));
        UI.padding(l, 15, 15, 15, 15);
        return l;
    }

    public static TextView cardTitle(Context c, CharSequence s) {
        TextView t = UI.medium(c, s, 12f, UI.MUTED);
        t.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UI.dp(12);
        t.setLayoutParams(lp);
        return t;
    }

    /* ------------------------------------------------------------- 按钮 */

    public static LinearLayout button(Context c, String label, String iconName, boolean primary) {
        LinearLayout b = UI.row(c);
        b.setGravity(Gravity.CENTER);
        if (primary) {
            b.setBackground(UI.ripple(UI.ACCENT, 10f));
            b.setElevation(UI.dp(4f));
        } else {
            b.setBackground(UI.ripple(UI.bg(UI.CARD, 10f, UI.LINE, 1f), 10f));
        }
        UI.padding(b, 16, 10, 16, 10);
        b.setMinimumHeight(UI.dp(42));

        if (iconName != null) {
            ImageView iv = UI.icon(c, iconName, primary ? 0xFFFFFFFF : UI.INK, 15);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UI.dp(15), UI.dp(15));
            lp.rightMargin = UI.dp(7);
            iv.setLayoutParams(lp);
            b.addView(iv);
        }
        TextView t = UI.medium(c, label, 13f, primary ? 0xFFFFFFFF : UI.INK);
        t.setSingleLine(true);
        b.addView(t);
        return b;
    }

    /** 顶栏右侧的文字按钮，例如「保存」 */
    public static TextView textAction(Context c, String label, int color) {
        TextView t = UI.medium(c, label, 14f, color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(UI.dp(14), UI.dp(11), UI.dp(14), UI.dp(11));
        return t;
    }

    /** 小号文字按钮（灰色、带图标） */
    public static LinearLayout link(Context c, String label, String iconName, int color) {
        LinearLayout b = UI.row(c);
        b.setBackground(UI.ripple(0x00000000, 8f));
        UI.padding(b, 8, 6, 8, 6);
        if (iconName != null) {
            ImageView iv = UI.icon(c, iconName, color, 13);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UI.dp(13), UI.dp(13));
            lp.rightMargin = UI.dp(5);
            iv.setLayoutParams(lp);
            b.addView(iv);
        }
        b.addView(UI.text(c, label, 11.5f, color));
        return b;
    }


    /* ------------------------------------------------------------- 开关 */

    /** 自绘开关：44×26 的胶囊 + 白色圆钮，颜色跟着设计系统走 */
    public static final class Toggle extends android.widget.FrameLayout {

        public interface OnChange {
            void on(boolean checked);
        }

        private final View knob;
        private final android.graphics.drawable.GradientDrawable track;
        private boolean checked;
        private OnChange listener;

        public Toggle(Context c, boolean checked) {
            super(c);
            setClickable(true);

            track = new android.graphics.drawable.GradientDrawable();
            track.setCornerRadius(UI.dp(13));
            setBackground(track);

            knob = new View(c);
            android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
            dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dot.setColor(0xFFFFFFFF);
            knob.setBackground(dot);
            knob.setElevation(UI.dp(1.5f));

            LayoutParams kp = new LayoutParams(UI.dp(20), UI.dp(20));
            kp.gravity = Gravity.START | Gravity.TOP;
            kp.leftMargin = UI.dp(3);
            kp.topMargin = UI.dp(3);
            addView(knob, kp);

            setLayoutParams(new LinearLayout.LayoutParams(UI.dp(44), UI.dp(26)));
            setChecked(checked);
            setOnClickListener(v -> {
                setChecked(!this.checked);
                if (listener != null) listener.on(this.checked);
            });
        }

        public boolean isChecked() {
            return checked;
        }

        public void setOnChange(OnChange l) {
            listener = l;
        }

        public void setChecked(boolean on) {
            checked = on;
            track.setColor(on ? UI.ACCENT : 0xFFDDD6C9);
            knob.animate().cancel();
            knob.animate()
                    .translationX(UI.dp(on ? 18 : 0))
                    .setDuration(140)
                    .start();
        }
    }

    /* ------------------------------------------------------------- 头像 */

    public static final int[] PALETTE = {
            0xFFFF4A17, 0xFF1F9D55, 0xFF2F6FB5, 0xFF7C5CFF,
            0xFFC98A12, 0xFF0F9B8E, 0xFFD9402F, 0xFF5B6472,
    };

    public static int autoColor(String seed) {
        String s = seed == null ? "" : seed;
        int h = 0;
        for (int i = 0; i < s.length(); i++) h = (h * 31 + s.charAt(i)) & 0xFFFF;
        return PALETTE[Math.abs(h) % PALETTE.length];
    }

    public static String initials(String name) {
        String s = name == null ? "" : name.trim();
        if (s.isEmpty()) return "?";
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')) {
                return String.valueOf(Character.toUpperCase(ch));
            }
        }
        return s.substring(0, 1);
    }

    /** 主机列表左侧的方块：纯色 + 首字母 */
    public static TextView avatar(Context c, String text, int color, float sizeDp, float textSp) {
        TextView t = UI.medium(c, text, textSp, 0xFFFFFFFF);
        t.setGravity(Gravity.CENTER);
        t.setBackground(UI.bg(color, sizeDp * 0.26f));
        t.setLayoutParams(new LinearLayout.LayoutParams(UI.dp(sizeDp), UI.dp(sizeDp)));
        return t;
    }

    /* ------------------------------------------------------------- 标签页 */

    /** 下划线标签页，风格同网页版的 .filter */
    public static View tabs(Context c, List<String> labels, List<String> counts,
                            int selected, OnPick onPick) {
        LinearLayout outer = UI.column(c);

        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        LinearLayout row = UI.row(c);
        row.setGravity(Gravity.BOTTOM);

        for (int i = 0; i < labels.size(); i++) {
            final int idx = i;
            boolean on = i == selected;

            LinearLayout item = UI.column(c);
            item.setBackground(UI.ripple(0x00000000, 8f));
            item.setGravity(Gravity.START);
            // 右侧留白用 margin 而不是 padding，这样下划线只跟着文字走
            item.setPadding(0, UI.dp(6), 0, UI.dp(10));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ilp.rightMargin = UI.dp(24);
            item.setLayoutParams(ilp);

            LinearLayout line = UI.row(c);
            line.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = UI.text(c, labels.get(i), 13f, on ? UI.INK : UI.MUTED);
            if (on) t.setTypeface(UI.MEDIUM);
            line.addView(t);
            if (counts != null && i < counts.size() && counts.get(i) != null) {
                TextView n = UI.medium(c, counts.get(i), 11.5f, UI.MUTED2);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = UI.dp(5);
                n.setLayoutParams(lp);
                line.addView(n);
            }
            item.addView(line);

            View ind = new View(c);
            ind.setBackgroundColor(on ? UI.ACCENT : 0x00000000);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, UI.dp(2)));
            ip.topMargin = UI.dp(8);
            ind.setLayoutParams(ip);
            item.addView(ind);

            if (onPick != null) item.setOnClickListener(v -> onPick.pick(idx));
            row.addView(item);
        }

        hs.addView(row, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        outer.addView(hs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        outer.addView(UI.hline(c, UI.LINE));
        return outer;
    }

    /* ------------------------------------------------------------- 空状态 */

    public static LinearLayout empty(Context c, String iconName, String title, String body,
                                     String actionLabel, Runnable action) {
        LinearLayout box = UI.column(c);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(0x80FFFFFF);
        d.setCornerRadius(UI.dp(14));
        d.setStroke(Math.max(1, UI.dp(1)), 0xFFDDD5C6, UI.dp(4), UI.dp(4));
        box.setBackground(d);
        UI.padding(box, 24, 38, 24, 34);

        if (iconName != null) {
            ImageView iv = UI.icon(c, iconName, 0xFFC9C0B0, 30);
            box.addView(iv);
        }
        TextView t = UI.medium(c, title, 15f, UI.INK2);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = UI.dp(12);
        t.setLayoutParams(tp);
        box.addView(t);

        if (body != null && !body.isEmpty()) {
            TextView b = UI.text(c, body, 12.5f, UI.MUTED);
            b.setGravity(Gravity.CENTER);
            b.setLineSpacing(UI.dp(3), 1f);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.topMargin = UI.dp(7);
            bp.bottomMargin = UI.dp(16);
            b.setLayoutParams(bp);
            box.addView(b);
        }

        if (actionLabel != null) {
            LinearLayout btn = button(c, actionLabel, "plus", true);
            btn.setOnClickListener(v -> action.run());
            box.addView(btn);
        }
        return box;
    }

    /* ------------------------------------------------------------- 其他 */

    public static void margins(View v, float l, float t, float r, float b) {
        ViewGroup.LayoutParams p = v.getLayoutParams();
        if (p instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) p;
            lp.leftMargin = UI.dp(l);
            lp.topMargin = UI.dp(t);
            lp.rightMargin = UI.dp(r);
            lp.bottomMargin = UI.dp(b);
            v.setLayoutParams(lp);
        }
    }

    public static void top(View v, float t) {
        ViewGroup.LayoutParams p = v.getLayoutParams();
        if (p instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) p).topMargin = UI.dp(t);
            v.setLayoutParams(p);
        }
    }

    public static void bottom(View v, float b) {
        ViewGroup.LayoutParams p = v.getLayoutParams();
        if (p instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) p).bottomMargin = UI.dp(b);
            v.setLayoutParams(p);
        }
    }
}
