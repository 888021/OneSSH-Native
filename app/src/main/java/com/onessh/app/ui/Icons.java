package com.onessh.app.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.Map;

/**
 * 图标：直接沿用网页版 pages.js 里的 24 单位路径，描边宽度 1.6，圆头圆角，
 * 所以原生界面上的图标和网页版是同一套形状。
 */
public final class Icons {

    private static final float VIEW = 24f;
    private static final float STROKE = 1.6f;

    private static final Map<String, String> PATHS = new HashMap<>();
    private static final Map<String, Path> CACHE = new HashMap<>();

    static {
        PATHS.put("home", "M4 10.6 12 4l8 6.6V19a1 1 0 0 1-1 1h-4.5v-6h-5v6H5a1 1 0 0 1-1-1z");
        PATHS.put("server",
                "M5.5 4.5h13a2 2 0 0 1 2 2v2a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2v-2a2 2 0 0 1 2-2z"
                        + "M7 7.5h.01"
                        + "M5.5 13.5h13a2 2 0 0 1 2 2v2a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2v-2a2 2 0 0 1 2-2z"
                        + "M7 16.5h.01");
        PATHS.put("code", "m8.5 8.5-4 3.5 4 3.5M15.5 8.5l4 3.5-4 3.5");
        PATHS.put("terminal", "m5 8 4 4-4 4M12.5 16h6");
        PATHS.put("gear",
                "M19.4 14.5a7.7 7.7 0 0 0 0-5l1.6-1.2-1.8-3.1-1.9.8a7.6 7.6 0 0 0-4.3-2.5L12.6 1h-1.2"
                        + "l-.4 2.5a7.6 7.6 0 0 0-4.3 2.5l-1.9-.8L3 8.3l1.6 1.2a7.7 7.7 0 0 0 0 5L3 15.7l1.8 3.1"
                        + " 1.9-.8a7.6 7.6 0 0 0 4.3 2.5l.4 2.5h1.2l.4-2.5a7.6 7.6 0 0 0 4.3-2.5l1.9.8 1.8-3.1z"
                        + "M15.2 12a3.2 3.2 0 1 1-6.4 0 3.2 3.2 0 0 1 6.4 0z");
        PATHS.put("logout", "M14 5.5h4a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1h-4M10 8.5 6 12l4 3.5M6 12h9");
        PATHS.put("plus", "M12 5v14M5 12h14");
        PATHS.put("refresh", "M20 11a8 8 0 1 0-2.3 6.3M20 5v6h-6");
        PATHS.put("copy", "M11 9h7a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-7a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z"
                + "M15 6.5V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v9a1 1 0 0 0 1 1h1.5");
        PATHS.put("back", "M10 6.5 4.5 12l5.5 5.5M4.5 12H20");
        PATHS.put("power", "M12 4v7M7.5 6.8a7 7 0 1 0 9 0");
        PATHS.put("expand", "M9 4H4v5M15 4h5v5M15 20h5v-5M9 20H4v-5");
        PATHS.put("bolt", "M13.5 3 5 13.5h6L10.5 21 19 10.5h-6z");
        PATHS.put("key", "M11.5 12a3.5 3.5 0 1 1-7 0 3.5 3.5 0 0 1 7 0zM11.5 12H21M17.5 12v3M20 12v2.5");
        PATHS.put("close", "m6 6 12 12M18 6 6 18");
        PATHS.put("edit", "m4 16.5-.8 4 4-.8L19 8a2.1 2.1 0 0 0-3-3z");
        PATHS.put("log", "M6 3.5h7l5 5v12H6zM13 3.5v5h5M9 13h6M9 16.5h4");
        PATHS.put("folder", "M3.5 6.5h6l1.8 2h9.2v10.5a1 1 0 0 1-1 1h-15a1 1 0 0 1-1-1z");
        PATHS.put("lock", "M8 10V7a4 4 0 0 1 8 0v3M6 10h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z");
        PATHS.put("search", "M16.5 10.5a6 6 0 1 1-12 0 6 6 0 0 1 12 0zM15 15l4.5 4.5");
        PATHS.put("download", "M12 4v11M8 11.5l4 4 4-4M5 20h14");
        PATHS.put("upload", "M12 20V9M8 12.5l4-4 4 4M5 4h14");
        PATHS.put("trash", "M5 7h14M9.5 7V4.5h5V7M6.5 7l.9 12.5h9.2L17.5 7");
        PATHS.put("file", "M7 3.5h6.5L18 8v12.5H7zM13.5 3.5V8H18");
        PATHS.put("up", "M12 19.5V5M6.5 10.5 12 5l5.5 5.5");
        PATHS.put("chevron", "m9.5 6 6 6-6 6");
        PATHS.put("more", "M12 5.2a1.3 1.3 0 1 1 0 2.6 1.3 1.3 0 0 1 0-2.6z"
                + "M12 10.7a1.3 1.3 0 1 1 0 2.6 1.3 1.3 0 0 1 0-2.6z"
                + "M12 16.2a1.3 1.3 0 1 1 0 2.6 1.3 1.3 0 0 1 0-2.6z");
        PATHS.put("check", "m5 12.5 4.5 4.5L19 7.5");
        PATHS.put("clock", "M12 4a8 8 0 1 1 0 16 8 8 0 0 1 0-16zM12 8v4.5l3 1.8");
        PATHS.put("shield", "M12 3.5 5 6v6c0 4 3 7 7 8.5 4-1.5 7-4.5 7-8.5V6z");
        PATHS.put("minimize", "M5 12h14");
    }

    private Icons() {}

    public static Path path(String name) {
        Path p = CACHE.get(name);
        if (p != null) return p;
        String d = PATHS.get(name);
        p = SvgPath.parse(d == null ? "" : d);
        CACHE.put(name, p);
        return p;
    }

    public static Drawable drawable(String name, int color, int sizePx) {
        IconDrawable d = new IconDrawable(path(name), color);
        d.setBounds(0, 0, sizePx, sizePx);
        return d;
    }

    private static final class IconDrawable extends Drawable {
        private final Path src;
        private final Paint paint;
        private final Matrix matrix = new Matrix();
        private final Path scaled = new Path();

        IconDrawable(Path src, int color) {
            this.src = src;
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(color);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            float scale = Math.min(b.width(), b.height()) / VIEW;
            matrix.reset();
            matrix.setScale(scale, scale);
            matrix.postTranslate(b.left + (b.width() - VIEW * scale) / 2f,
                    b.top + (b.height() - VIEW * scale) / 2f);
            scaled.reset();
            src.transform(matrix, scaled);
            paint.setStrokeWidth(STROKE * scale);
            canvas.drawPath(scaled, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
