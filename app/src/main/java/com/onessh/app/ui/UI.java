package com.onessh.app.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 设计系统。数值全部来自网页版的 CSS 变量（style.css :root），
 * 这样原生界面和原版看起来是同一套东西。
 */
public final class UI {

    public static final int PAPER = 0xFFFAF8F4;
    public static final int PAPER2 = 0xFFF5F1EA;
    public static final int CARD = 0xFFFFFFFF;
    public static final int INK = 0xFF16151A;
    public static final int INK2 = 0xFF3D3A34;
    public static final int MUTED = 0xFF8B857A;
    public static final int MUTED2 = 0xFFA8A196;
    public static final int LINE = 0xFFE8E2D7;
    public static final int LINE2 = 0xFFF0EBE2;
    public static final int ACCENT = 0xFFFF4A17;
    public static final int ACCENT_DARK = 0xFFE63C0C;
    public static final int ACCENT_SOFT = 0xFFFFECE4;
    public static final int GREEN = 0xFF1F9D55;
    public static final int AMBER = 0xFFC98A12;
    public static final int RED = 0xFFD9402F;

    public static final int TERM_BG = 0xFF070910;
    public static final int TERM_BAR = 0xFF0B0E13;
    public static final int TERM_LINE = 0xFF171C24;
    public static final int TERM_FG = 0xFFD6DBE4;
    public static final int TERM_MUTED = 0xFF5C6679;

    public static final Typeface SANS = Typeface.create("sans-serif", Typeface.NORMAL);
    public static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    public static final Typeface MONO = Typeface.MONOSPACE;

    private UI() {}

    /* ------------------------------------------------------------- 尺寸 */

    public static float density() {
        return android.content.res.Resources.getSystem().getDisplayMetrics().density;
    }

    public static int dp(float v) {
        return Math.round(v * density());
    }

    public static int screenWidth(Context c) {
        return c.getResources().getDisplayMetrics().widthPixels;
    }

    public static int statusBarHeight(Context c) {
        int id = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? c.getResources().getDimensionPixelSize(id) : dp(24);
    }

    /* ------------------------------------------------------------- 文本 */

    public static TextView text(Context c, CharSequence s, float sizeSp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(color);
        t.setTypeface(SANS);
        t.setIncludeFontPadding(false);
        return t;
    }

    public static TextView medium(Context c, CharSequence s, float sizeSp, int color) {
        TextView t = text(c, s, sizeSp, color);
        t.setTypeface(MEDIUM);
        return t;
    }

    public static TextView mono(Context c, CharSequence s, float sizeSp, int color) {
        TextView t = text(c, s, sizeSp, color);
        t.setTypeface(MONO);
        return t;
    }

    public static TextView eyebrow(Context c, CharSequence s, int color) {
        TextView t = medium(c, s, 10f, color);
        t.setLetterSpacing(0.14f);
        t.setAllCaps(true);
        return t;
    }

    /* ------------------------------------------------------------- 形状 */

    public static GradientDrawable bg(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    public static GradientDrawable bg(int color, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable d = bg(color, radiusDp);
        d.setStroke(Math.max(1, dp(strokeDp)), strokeColor);
        return d;
    }

    public static GradientDrawable stroke(int strokeColor, float strokeDp, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x00000000);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(Math.max(1, dp(strokeDp)), strokeColor);
        return d;
    }

    /** 卡片：白底 + 圆角 + 极轻的投影（对应 --shadow-sm） */
    public static View card(Context c, float radiusDp) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackground(bg(CARD, radiusDp));
        v.setElevation(dp(1.5f));
        return v;
    }

    public static RippleDrawable ripple(int contentColor, float radiusDp) {
        GradientDrawable content = bg(contentColor, radiusDp);
        GradientDrawable mask = bg(0xFFFFFFFF, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(0x1A000000), content, mask);
    }

    public static RippleDrawable ripple(Drawable content, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x1A000000), content, bg(0xFFFFFFFF, radiusDp));
    }

    public static void touchable(View v) {
        TypedValue out = new TypedValue();
        v.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, out, true);
        if (out.resourceId != 0) v.setBackgroundResource(out.resourceId);
    }

    /* ------------------------------------------------------------- 图标 */

    public static ImageView icon(Context c, String name, int color, float sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(Icons.drawable(name, color, dp(sizeDp)));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        iv.setLayoutParams(lp);
        return iv;
    }

    /** 带浅色底的方形图标块（主机列表左侧那个 tile） */
    public static View tile(Context c, String iconName, int bgColor, int fgColor, float sizeDp) {
        LinearLayout box = new LinearLayout(c);
        box.setGravity(Gravity.CENTER);
        box.setBackground(bg(bgColor, 12f));
        box.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        box.addView(icon(c, iconName, fgColor, sizeDp * 0.5f));
        return box;
    }

    /* ------------------------------------------------------------- 布局 */

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static View hline(Context c, int color) {
        View v = new View(c);
        v.setBackgroundColor(color);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.8f))));
        return v;
    }

    public static View gap(Context c, float hDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(hDp)));
        return v;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lpm(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static void padding(View v, float l, float t, float r, float b) {
        v.setPadding(dp(l), dp(t), dp(r), dp(b));
    }

    public static void weight(View v, float w) {
        ViewGroup.LayoutParams p = v.getLayoutParams();
        if (p instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) p).weight = w;
            ((LinearLayout.LayoutParams) p).width = 0;
        }
    }

    public static void toast(Context c, CharSequence msg) {
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }
}
