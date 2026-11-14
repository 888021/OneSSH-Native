package com.onessh.app.ui;

import android.graphics.Path;
import android.graphics.RectF;

/**
 * 极简 SVG path 解析器，只为把网页版那套 24 单位的图标路径原样搬到 Canvas 上。
 *
 * 支持 M L H V C S Q T A Z（大小写）。图标里的圆弧全部是 x 轴旋转 0 的椭圆弧，
 * 所以直接用 Path.arcTo 精确绘制，不做贝塞尔近似。
 */
public final class SvgPath {

    private SvgPath() {}

    public static Path parse(String d) {
        Path path = new Path();
        if (d == null) return path;
        Cursor c = new Cursor(d);

        float cx = 0, cy = 0;
        float sx = 0, sy = 0;
        float lastCx = 0, lastCy = 0;
        char prevCmd = 0;
        char cmd = 0;

        while (true) {
            c.skipSeparators();
            if (c.eof()) break;

            char ch = c.peek();
            if (isCommand(ch)) {
                cmd = ch;
                c.pos++;
            } else if (cmd == 0) {
                break;
            } else if (cmd == 'M') {
                cmd = 'L';
            } else if (cmd == 'm') {
                cmd = 'l';
            }

            boolean rel = Character.isLowerCase(cmd);
            switch (Character.toUpperCase(cmd)) {
                case 'M': {
                    float x = c.num(), y = c.num();
                    if (rel) { x += cx; y += cy; }
                    path.moveTo(x, y);
                    cx = sx = x;
                    cy = sy = y;
                    break;
                }
                case 'L': {
                    float x = c.num(), y = c.num();
                    if (rel) { x += cx; y += cy; }
                    path.lineTo(x, y);
                    cx = x;
                    cy = y;
                    break;
                }
                case 'H': {
                    float x = c.num();
                    if (rel) x += cx;
                    path.lineTo(x, cy);
                    cx = x;
                    break;
                }
                case 'V': {
                    float y = c.num();
                    if (rel) y += cy;
                    path.lineTo(cx, y);
                    cy = y;
                    break;
                }
                case 'C': {
                    float x1 = c.num(), y1 = c.num(), x2 = c.num(), y2 = c.num(), x = c.num(), y = c.num();
                    if (rel) { x1 += cx; y1 += cy; x2 += cx; y2 += cy; x += cx; y += cy; }
                    path.cubicTo(x1, y1, x2, y2, x, y);
                    lastCx = x2;
                    lastCy = y2;
                    cx = x;
                    cy = y;
                    break;
                }
                case 'S': {
                    float x2 = c.num(), y2 = c.num(), x = c.num(), y = c.num();
                    if (rel) { x2 += cx; y2 += cy; x += cx; y += cy; }
                    float x1 = cx, y1 = cy;
                    if (prevCmd == 'C' || prevCmd == 'c' || prevCmd == 'S' || prevCmd == 's') {
                        x1 = 2 * cx - lastCx;
                        y1 = 2 * cy - lastCy;
                    }
                    path.cubicTo(x1, y1, x2, y2, x, y);
                    lastCx = x2;
                    lastCy = y2;
                    cx = x;
                    cy = y;
                    break;
                }
                case 'Q': {
                    float x1 = c.num(), y1 = c.num(), x = c.num(), y = c.num();
                    if (rel) { x1 += cx; y1 += cy; x += cx; y += cy; }
                    path.quadTo(x1, y1, x, y);
                    lastCx = x1;
                    lastCy = y1;
                    cx = x;
                    cy = y;
                    break;
                }
                case 'T': {
                    float x = c.num(), y = c.num();
                    if (rel) { x += cx; y += cy; }
                    float x1 = cx, y1 = cy;
                    if (prevCmd == 'Q' || prevCmd == 'q' || prevCmd == 'T' || prevCmd == 't') {
                        x1 = 2 * cx - lastCx;
                        y1 = 2 * cy - lastCy;
                    }
                    path.quadTo(x1, y1, x, y);
                    lastCx = x1;
                    lastCy = y1;
                    cx = x;
                    cy = y;
                    break;
                }
                case 'A': {
                    float rx = c.num(), ry = c.num(), rot = c.num();
                    boolean large = c.flag(), sweep = c.flag();
                    float x = c.num(), y = c.num();
                    if (rel) { x += cx; y += cy; }
                    arcTo(path, cx, cy, rx, ry, rot, large, sweep, x, y);
                    cx = x;
                    cy = y;
                    break;
                }
                case 'Z': {
                    path.close();
                    cx = sx;
                    cy = sy;
                    break;
                }
                default:
                    // 未知命令：跳过这个字母，避免死循环
                    c.pos++;
                    break;
            }
            prevCmd = cmd;
        }
        return path;
    }

    /** SVG 端点式圆弧 → Path.arcTo（旋转角为 0 的精确解） */
    private static void arcTo(Path path, float x0, float y0, float rx, float ry, float rotDeg,
                              boolean large, boolean sweep, float x1, float y1) {
        rx = Math.abs(rx);
        ry = Math.abs(ry);
        if (rx == 0 || ry == 0 || (x0 == x1 && y0 == y1)) {
            path.lineTo(x1, y1);
            return;
        }

        double rot = Math.toRadians(rotDeg % 360);
        double cosR = Math.cos(rot), sinR = Math.sin(rot);

        double dx2 = (x0 - x1) / 2.0, dy2 = (y0 - y1) / 2.0;
        double x1p = cosR * dx2 + sinR * dy2;
        double y1p = -sinR * dx2 + cosR * dy2;

        double lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry);
        if (lambda > 1) {
            double s = Math.sqrt(lambda);
            rx = (float) (rx * s);
            ry = (float) (ry * s);
        }

        double sign = (large != sweep) ? 1 : -1;
        double num = (double) rx * rx * ry * ry - (double) rx * rx * y1p * y1p - (double) ry * ry * x1p * x1p;
        double den = (double) rx * rx * y1p * y1p + (double) ry * ry * x1p * x1p;
        double co = den == 0 ? 0 : sign * Math.sqrt(Math.max(0, num / den));

        double cxp = co * rx * y1p / ry;
        double cyp = -co * ry * x1p / rx;

        double cx = cosR * cxp - sinR * cyp + (x0 + x1) / 2.0;
        double cy = sinR * cxp + cosR * cyp + (y0 + y1) / 2.0;

        double ux = (x1p - cxp) / rx, uy = (y1p - cyp) / ry;
        double vx = (-x1p - cxp) / rx, vy = (-y1p - cyp) / ry;

        double theta1 = Math.atan2(uy, ux);
        double dTheta = Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy);
        if (!sweep && dTheta > 0) dTheta -= 2 * Math.PI;
        if (sweep && dTheta < 0) dTheta += 2 * Math.PI;

        if (Math.abs(rotDeg) < 0.01) {
            RectF oval = new RectF((float) (cx - rx), (float) (cy - ry), (float) (cx + rx), (float) (cy + ry));
            path.arcTo(oval, (float) Math.toDegrees(theta1), (float) Math.toDegrees(dTheta), false);
        } else {
            // 图标里没有旋转椭圆；退化成直线，至少不会画歪
            path.lineTo(x1, y1);
        }
    }

    private static boolean isCommand(char c) {
        return "MmLlHhVvCcSsQqTtAaZz".indexOf(c) >= 0;
    }

    private static final class Cursor {
        final String s;
        int pos;

        Cursor(String s) {
            this.s = s;
        }

        boolean eof() {
            return pos >= s.length();
        }

        char peek() {
            return s.charAt(pos);
        }

        void skipSeparators() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == ',' || c == '\n' || c == '\r' || c == '\t') pos++;
                else break;
            }
        }

        /** 圆弧的 large-arc / sweep 标志位：可能连写，如 "01" */
        boolean flag() {
            skipSeparators();
            char c = s.charAt(pos);
            pos++;
            return c == '1';
        }

        float num() {
            skipSeparators();
            int start = pos;
            if (pos < s.length() && (s.charAt(pos) == '-' || s.charAt(pos) == '+')) pos++;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            if (pos < s.length() && s.charAt(pos) == '.') {
                pos++;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            }
            if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                int save = pos;
                pos++;
                if (pos < s.length() && (s.charAt(pos) == '-' || s.charAt(pos) == '+')) pos++;
                if (pos < s.length() && Character.isDigit(s.charAt(pos))) {
                    while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                } else {
                    pos = save;
                }
            }
            if (start == pos) {
                pos++;
                return 0f;
            }
            try {
                return Float.parseFloat(s.substring(start, pos));
            } catch (NumberFormatException e) {
                return 0f;
            }
        }
    }
}
