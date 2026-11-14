package com.onessh.app.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 对话框：纸色卡片、圆角、橙色主按钮，和网页版的 modal 一个调子。 */
public final class Dialogs {

    public interface OnPick {
        void pick(int index);
    }

    public interface OnText {
        void text(String value);
    }

    private Dialogs() {}

    /* ------------------------------------------------------------- 基础 */

    private static Dialog make(Context c) {
        Dialog d = new Dialog(c);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return d;
    }

    private static void place(Dialog d, Context c, boolean bottom) {
        Window w = d.getWindow();
        if (w == null) return;
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setDimAmount(0.42f);
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        if (bottom) {
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        } else {
            w.setGravity(Gravity.CENTER);
            w.setLayout((int) (dm.widthPixels * 0.9f), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static LinearLayout card(Context c) {
        LinearLayout box = UI.column(c);
        box.setBackground(UI.bg(UI.CARD, 16f));
        UI.padding(box, 20, 20, 20, 16);
        return box;
    }

    private static LinearLayout sheetCard(Context c) {
        LinearLayout box = UI.column(c);
        GradientDrawable d = new GradientDrawable();
        d.setColor(UI.CARD);
        float r = UI.dp(18);
        d.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        box.setBackground(d);
        UI.padding(box, 8, 10, 8, 18);
        return box;
    }

    private static void title(Context c, LinearLayout box, String t, String sub) {
        if (t != null && !t.isEmpty()) {
            TextView tv = UI.medium(c, t, 16f, UI.INK);
            box.addView(tv);
        }
        if (sub != null && !sub.isEmpty()) {
            TextView tv = UI.text(c, sub, 12.5f, UI.MUTED);
            tv.setLineSpacing(UI.dp(3), 1f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = UI.dp(8);
            tv.setLayoutParams(lp);
            box.addView(tv);
        }
    }

    private static LinearLayout buttons(Context c) {
        LinearLayout row = UI.row(c);
        row.setGravity(Gravity.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UI.dp(18);
        row.setLayoutParams(lp);
        return row;
    }

    private static TextView action(Context c, String label, int color, boolean filled) {
        TextView t = UI.medium(c, label, 13f, filled ? 0xFFFFFFFF : color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(UI.dp(16), UI.dp(10), UI.dp(16), UI.dp(10));
        if (filled) {
            t.setBackground(UI.ripple(color, 9f));
        } else {
            t.setBackground(UI.ripple(0x00000000, 9f));
        }
        return t;
    }

    /* ------------------------------------------------------------- 提示 */

    public static void alert(Context c, String title, String body) {
        Dialog d = make(c);
        LinearLayout box = card(c);
        title(c, box, title, body);
        LinearLayout btns = buttons(c);
        TextView ok = action(c, "知道了", UI.ACCENT, true);
        ok.setOnClickListener(v -> d.dismiss());
        btns.addView(ok);
        box.addView(btns);
        d.setContentView(box);
        d.show();
        place(d, c, false);
    }

    public static void confirm(Context c, String title, String body, String okLabel,
                               boolean danger, Runnable onOk) {
        Dialog d = make(c);
        LinearLayout box = card(c);
        title(c, box, title, body);
        LinearLayout btns = buttons(c);

        TextView cancel = action(c, "取消", UI.MUTED, false);
        cancel.setOnClickListener(v -> d.dismiss());
        btns.addView(cancel);

        TextView ok = action(c, okLabel, danger ? UI.RED : UI.ACCENT, true);
        ok.setOnClickListener(v -> {
            // 先执行回调再关闭：调用方可能在 dismiss 监听里等结果
            if (onOk != null) onOk.run();
            d.dismiss();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = UI.dp(6);
        ok.setLayoutParams(lp);
        btns.addView(ok);

        box.addView(btns);
        d.setContentView(box);
        d.show();
        place(d, c, false);
    }

    /** 单行输入。secret 为 true 时按密码框显示。 */
    public static void prompt(Context c, String title, String label, String hint,
                              String initial, boolean secret, OnText onOk) {
        Dialog d = make(c);
        LinearLayout box = card(c);
        title(c, box, title, null);

        Widgets.Field f = Widgets.field(c, label, hint,
                secret ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                        : InputType.TYPE_CLASS_TEXT);
        f.setText(initial);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = UI.dp(14);
        f.root.setLayoutParams(flp);
        box.addView(f.root);

        LinearLayout btns = buttons(c);
        TextView cancel = action(c, "取消", UI.MUTED, false);
        cancel.setOnClickListener(v -> d.dismiss());
        btns.addView(cancel);

        TextView ok = action(c, "确定", UI.ACCENT, true);
        ok.setOnClickListener(v -> {
            d.dismiss();
            if (onOk != null) onOk.text(f.text());
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = UI.dp(6);
        ok.setLayoutParams(lp);
        btns.addView(ok);

        box.addView(btns);
        d.setContentView(box);
        d.show();
        place(d, c, false);
        f.input.requestFocus();
    }

    /**
     * 自定义内容对话框。content 由调用方填充，返回值用于关闭。
     */
    public static Dialog custom(Context c, String title, String sub, View content,
                                String okLabel, String cancelLabel, boolean danger, Runnable onOk) {
        Dialog d = make(c);
        LinearLayout box = card(c);
        title(c, box, title, sub);

        if (content != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = UI.dp(14);
            content.setLayoutParams(lp);
            box.addView(content);
        }

        LinearLayout btns = buttons(c);
        if (cancelLabel != null) {
            TextView cancel = action(c, cancelLabel, UI.MUTED, false);
            cancel.setOnClickListener(v -> d.dismiss());
            btns.addView(cancel);
        }
        TextView ok = action(c, okLabel, danger ? UI.RED : UI.ACCENT, true);
        ok.setOnClickListener(v -> {
            // 先执行回调再关闭：调用方可能在 dismiss 监听里等结果
            if (onOk != null) onOk.run();
            d.dismiss();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = UI.dp(6);
        ok.setLayoutParams(lp);
        btns.addView(ok);

        box.addView(btns);
        d.setContentView(box);
        d.show();
        place(d, c, false);
        return d;
    }

    /* --------------------------------------------------------- 底部菜单 */

    public static void sheet(Context c, String title, String[] labels, String[] icons,
                             int dangerIndex, OnPick onPick) {
        Dialog d = make(c);
        LinearLayout box = sheetCard(c);

        View handle = new View(c);
        handle.setBackground(UI.bg(0xFFE2DCD1, 2f));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(UI.dp(36), UI.dp(4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.topMargin = UI.dp(6);
        hp.bottomMargin = UI.dp(8);
        handle.setLayoutParams(hp);
        box.addView(handle);

        if (title != null && !title.isEmpty()) {
            TextView t = UI.medium(c, title, 13f, UI.MUTED);
            t.setPadding(UI.dp(16), UI.dp(8), UI.dp(16), UI.dp(10));
            box.addView(t);
        }

        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            boolean danger = i == dangerIndex;
            LinearLayout row = UI.row(c);
            row.setBackground(UI.ripple(0x00000000, 10f));
            UI.padding(row, 16, 13, 16, 13);

            int fg = danger ? UI.RED : UI.INK;
            if (icons != null && i < icons.length && icons[i] != null) {
                ImageView iv = UI.icon(c, icons[i], danger ? UI.RED : UI.MUTED, 18);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UI.dp(18), UI.dp(18));
                lp.rightMargin = UI.dp(13);
                iv.setLayoutParams(lp);
                row.addView(iv);
            }
            row.addView(UI.text(c, labels[i], 14.5f, fg));
            row.setOnClickListener(v -> {
                d.dismiss();
                if (onPick != null) onPick.pick(idx);
            });
            box.addView(row);
        }

        LinearLayout cancel = UI.row(c);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(UI.ripple(UI.PAPER2, 10f));
        UI.padding(cancel, 16, 13, 16, 13);
        cancel.addView(UI.medium(c, "取消", 14.5f, UI.INK2));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = UI.dp(8);
        clp.leftMargin = UI.dp(8);
        clp.rightMargin = UI.dp(8);
        cancel.setLayoutParams(clp);
        cancel.setOnClickListener(v -> d.dismiss());
        box.addView(cancel);

        d.setContentView(box);
        d.show();
        place(d, c, true);
    }

    /** 单选列表（用于「从凭据库选择」这类场景），带勾选标记 */
    public static void choose(Context c, String title, String[] labels, String[] subs,
                              int selected, OnPick onPick) {
        Dialog d = make(c);
        LinearLayout box = card(c);
        title(c, box, title, null);

        ScrollView sc = new ScrollView(c);
        sc.setVerticalScrollBarEnabled(false);
        LinearLayout list = UI.column(c);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = UI.dp(12);
        list.setLayoutParams(llp);

        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            LinearLayout row = UI.row(c);
            row.setBackground(UI.ripple(UI.PAPER2, 10f));
            UI.padding(row, 12, 11, 12, 11);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = UI.dp(4);
            row.setLayoutParams(rlp);

            LinearLayout col = UI.column(c);
            col.addView(UI.medium(c, labels[i], 13.5f, UI.INK));
            if (subs != null && i < subs.length && subs[i] != null) {
                TextView s = UI.text(c, subs[i], 11.5f, UI.MUTED);
                s.setSingleLine(true);
                s.setEllipsize(android.text.TextUtils.TruncateAt.END);
                col.addView(s);
            }
            row.addView(col);
            UI.weight(col, 1f);

            if (i == selected) row.addView(UI.icon(c, "check", UI.ACCENT, 17));
            row.setOnClickListener(v -> {
                d.dismiss();
                if (onPick != null) onPick.pick(idx);
            });
            list.addView(row);
        }

        sc.addView(list);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        slp.topMargin = UI.dp(12);
        sc.setLayoutParams(slp);
        box.addView(sc);

        LinearLayout btns = buttons(c);
        TextView cancel = action(c, "取消", UI.MUTED, false);
        cancel.setOnClickListener(v -> d.dismiss());
        btns.addView(cancel);
        box.addView(btns);

        d.setContentView(box);
        d.show();
        place(d, c, false);
    }
}
