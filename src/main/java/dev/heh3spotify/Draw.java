package dev.heh3spotify;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * Drawing helpers shared by the HUD and the pop-out. Shapes are drawn in quarter-pixels (S per GUI pixel) one row at a
 * time, so rounded corners, circles and gradients come out smooth instead of blocky. No overlapping layers, so
 * transparent colours stay clean.
 */
final class Draw {
    private Draw() {}

    static final int S = 4;
    static final List<String> ANCHORS = List.of("tl", "tc", "tr", "ml", "mr", "bl", "bc", "br");
    static final String[] ANCHOR_NAMES = {"Top left", "Top center", "Top right", "Middle left", "Middle right", "Bottom left", "Bottom center", "Bottom right"};

    // ---- colours ----
    static boolean validHex(String s) { return s != null && s.matches("[0-9A-Fa-f]{6}"); }

    /** "1DB954" -> opaque ARGB. */
    static int argb(String hex) {
        try { return 0xFF000000 | Integer.parseInt(hex, 16); } catch (Exception e) { return 0xFFFFFFFF; }
    }

    static String hex(int r, int g, int b) { return String.format("%02X%02X%02X", clamp(r), clamp(g), clamp(b)); }

    static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    static int withAlpha(int argb, int alpha) { return (clamp(alpha) << 24) | (argb & 0xFFFFFF); }

    /** Blend of two ARGB colours (alpha included), t from 0 to 1. */
    static int lerp(int a, int b, float t) {
        int out = 0;
        for (int sh = 0; sh <= 24; sh += 8) {
            int x = (a >>> sh) & 255, y = (b >>> sh) & 255;
            out |= clamp(Math.round(x + (y - x) * t)) << sh;
        }
        return out;
    }

    static int lighten(int argb, int amt) {
        int r = clamp(((argb >> 16) & 255) + amt), g = clamp(((argb >> 8) & 255) + amt), b = clamp((argb & 255) + amt);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Gradient steps are limited so a gradient is a few wide bands (cheap) that the eye reads as smooth. */
    static float q(float t) { return Math.round(Math.max(0, Math.min(1, t)) * 40f) / 40f; }

    // ---- shapes ----
    static int inset(int i, int H, int R) {
        if (R <= 0) return 0;
        int d = i < R ? R - i : (i >= H - R ? i - (H - R) + 1 : 0);
        if (d <= 0) return 0;
        double dy = d - 0.5;
        return (int) Math.round(R - Math.sqrt(Math.max(0, (double) R * R - dy * dy)));
    }

    /** Rounded rectangle with a vertical gradient (top to bottom). left / right say whether those corners are round. */
    static void rrg(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int top, int bottom, boolean left, boolean right) {
        int X = Math.round(x * S), Y = Math.round(y * S), X2 = Math.round((x + w) * S), Y2 = Math.round((y + h) * S);
        rrSub(g, X, Y, X2 - X, Y2 - Y, Math.round(r * S), top, bottom, left, right);
    }

    static void rrg(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int top, int bottom) { rrg(g, x, y, w, h, r, top, bottom, true, true); }

    static void rr(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int color) { rrg(g, x, y, w, h, r, color, color, true, true); }

    /** Everything in whole quarter-pixels: X, Y, W, H, R are already multiplied by S. */
    static void rrSub(GuiGraphicsExtractor g, int X, int Y, int W, int H, int R, int top, int bottom, boolean left, boolean right) {
        if (W <= 0 || H <= 0) return;
        R = Math.max(0, Math.min(R, Math.min(W, H) / 2));
        g.pose().pushMatrix();
        g.pose().scale(1f / S, 1f / S);
        int start = 0, pl = 0, pr = 0, pc = 0;
        for (int i = 0; i <= H; i++) {
            int il = 0, ir = 0, col = 0;
            boolean end = i == H;
            if (!end) {
                int in = inset(i, H, R);
                il = left ? in : 0;
                ir = right ? in : 0;
                col = top == bottom ? top : lerp(top, bottom, q((i + 0.5f) / H));
            }
            if (i > 0 && (end || il != pl || ir != pr || col != pc)) g.fill(X + pl, Y + start, X + W - pr, Y + i, pc);
            if (i == 0 || (!end && (il != pl || ir != pr || col != pc))) { start = i; pl = il; pr = ir; pc = col; }
        }
        g.pose().popMatrix();
    }

    static void circle(GuiGraphicsExtractor g, float x, float y, float d, int top, int bottom) { rrg(g, x, y, d, d, d / 2f, top, bottom, true, true); }

    /** Horizontal gradient bar with rounded ends (drawn as thin slices that share exact edges). */
    static void bar(GuiGraphicsExtractor g, float x, float y, float w, float h, int c1, int c2) {
        int X = Math.round(x * S), Y = Math.round(y * S), W = Math.round(w * S), H = Math.round(h * S);
        if (W <= 0 || H <= 0) return;
        int R = Math.min(H / 2, W / 2);
        int n = Math.max(1, Math.min(40, W / S));
        for (int k = 0; k < n; k++) {
            int a = X + Math.round(W * (float) k / n), b = X + Math.round(W * (float) (k + 1) / n);
            int c = lerp(c1, c2, q((k + 0.5f) / n));
            rrSubClipped(g, a, Y, b - a, H, R, c, X, W, k == 0, k == n - 1);
        }
    }

    /** One slice of a bar: the slice itself is straight, but the first / last one follows the bar's rounded end. */
    private static void rrSubClipped(GuiGraphicsExtractor g, int a, int Y, int sw, int H, int R, int color, int X, int W, boolean first, boolean last) {
        if (!first && !last) { rrSub(g, a, Y, sw, H, 0, color, color, false, false); return; }
        g.pose().pushMatrix();
        g.pose().scale(1f / S, 1f / S);
        for (int i = 0; i < H; i++) {
            int in = inset(i, H, R);
            int x1 = Math.max(a, X + in), x2 = Math.min(a + sw, X + W - in);
            if (x2 > x1) g.fill(x1, Y + i, x2, Y + i + 1, color);
        }
        g.pose().popMatrix();
    }

    /** Rounded outline (bw thick) with a vertical gradient. */
    static void stroke(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, float bw, int top, int bottom) {
        int X = Math.round(x * S), Y = Math.round(y * S), W = Math.round(w * S), H = Math.round(h * S), B = Math.max(1, Math.round(bw * S));
        if (W <= 0 || H <= 0) return;
        int R = Math.max(B, Math.min(Math.round(r * S), Math.min(W, H) / 2)), Ri = R - B;
        g.pose().pushMatrix();
        g.pose().scale(1f / S, 1f / S);
        for (int i = 0; i < H; i++) {
            int d = Math.min(i, H - 1 - i);
            int io = R - half(R, d);
            int col = top == bottom ? top : lerp(top, bottom, q((i + 0.5f) / H));
            if (d < B || d >= R) {
                if (d < B) { g.fill(X + io, Y + i, X + W - io, Y + i + 1, col); continue; }
                g.fill(X, Y + i, X + B, Y + i + 1, col);
                g.fill(X + W - B, Y + i, X + W, Y + i + 1, col);
                continue;
            }
            int ii = Math.max(io, B + Ri - half(Ri, d - B));
            g.fill(X + io, Y + i, X + ii, Y + i + 1, col);
            g.fill(X + W - ii, Y + i, X + W - io, Y + i + 1, col);
        }
        g.pose().popMatrix();
    }

    /** Three-stop gradient, t from 0 to 1. */
    static int grad3(int c1, int c2, int c3, float t) {
        t = Math.max(0, Math.min(1, t));
        return t < 0.5f ? lerp(c1, c2, q(t * 2)) : lerp(c2, c3, q((t - 0.5f) * 2));
    }

    /** One horizontal run of a row, split into small pieces so the colour changes smoothly left to right. */
    private static void hseg(GuiGraphicsExtractor g, int x1, int x2, int y, int X, int W, int c1, int c2, int c3) {
        if (x2 <= x1) return;
        int step = Math.max(S, W / 40);
        for (int a = x1; a < x2; a += step) {
            int b = Math.min(x2, a + step);
            g.fill(a, y, b, y + 1, grad3(c1, c2, c3, ((a + b) / 2f - X) / Math.max(1, W)));
        }
    }

    /** Rounded outline whose colour runs left to right through three colours (the neon border). */
    static void strokeH(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, float bw, int c1, int c2, int c3) {
        int X = Math.round(x * S), Y = Math.round(y * S), W = Math.round(w * S), H = Math.round(h * S), B = Math.max(1, Math.round(bw * S));
        if (W <= 0 || H <= 0) return;
        int R = Math.max(B, Math.min(Math.round(r * S), Math.min(W, H) / 2)), Ri = R - B;
        g.pose().pushMatrix();
        g.pose().scale(1f / S, 1f / S);
        for (int i = 0; i < R; i++) {
            int io = R - half(R, i);
            int ii = Math.max(io, B + Ri - half(Ri, i - B));
            for (int side = 0; side < 2; side++) {
                int row = side == 0 ? Y + i : Y + H - 1 - i;
                if (i < B) hseg(g, X + io, X + W - io, row, X, W, c1, c2, c3);
                else {
                    hseg(g, X + io, X + ii, row, X, W, c1, c2, c3);
                    hseg(g, X + W - ii, X + W - io, row, X, W, c1, c2, c3);
                }
            }
        }
        if (H - 2 * R > 0) { // straight sides: one tall strip each
            g.fill(X, Y + R, X + B, Y + H - R, c1);
            g.fill(X + W - B, Y + R, X + W, Y + H - R, c3);
        }
        g.pose().popMatrix();
    }

    /** Horizontal gradient bar with rounded ends and three colours. */
    static void bar3(GuiGraphicsExtractor g, float x, float y, float w, float h, int c1, int c2, int c3) {
        int X = Math.round(x * S), Y = Math.round(y * S), W = Math.round(w * S), H = Math.round(h * S);
        if (W <= 0 || H <= 0) return;
        int R = Math.min(H / 2, W / 2);
        g.pose().pushMatrix();
        g.pose().scale(1f / S, 1f / S);
        for (int i = 0; i < H; i++) {
            int in = inset(i, H, R);
            hseg(g, X + in, X + W - in, Y + i, X, W, c1, c2, c3);
        }
        g.pose().popMatrix();
    }

    /** Album art from a small pixel grid, with smooth rounded corners (corner rows are cut per sub-pixel row). */
    static void art(GuiGraphicsExtractor g, int[] px, int n, float x, float y, float size, float radius) {
        int X = Math.round(x * S), Y = Math.round(y * S), Z = Math.round(size * S), R = Math.min(Math.round(radius * S), Z / 2);
        if (Z <= 0 || px == null || px.length < n * n) return;
        g.pose().pushMatrix();
        g.pose().scale(1f / S, 1f / S);
        for (int j = 0; j < n; j++) {
            int y0 = Y + Math.round(Z * (float) j / n), y1 = Y + Math.round(Z * (float) (j + 1) / n);
            boolean corner = y1 - Y <= R || y0 - Y >= Z - R;
            int step = corner ? 2 : y1 - y0;
            for (int yy = y0; yy < y1; yy += step) {
                int ye = Math.min(y1, yy + step);
                int in = inset(((yy + ye) / 2) - Y, Z, R);
                for (int i = 0; i < n; i++) {
                    int x0 = X + Math.round(Z * (float) i / n), x1 = X + Math.round(Z * (float) (i + 1) / n);
                    int a = Math.max(x0, X + in), b = Math.min(x1, X + Z - in);
                    if (b > a) g.fill(a, yy, b, ye, 0xFF000000 | px[j * n + i]);
                }
            }
        }
        g.pose().popMatrix();
    }

    /** Text at a larger size (k times). */
    static void bigText(GuiGraphicsExtractor g, Font f, String s, float x, float y, float k, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(k, k);
        g.text(f, s, 0, 0, color, true);
        g.pose().popMatrix();
    }

    static int half(int R, int i) {
        if (R <= 0) return 0;
        double d = R - i - 0.5;
        return (int) Math.sqrt(Math.max(0, (double) R * R - d * d));
    }

    /** Solid triangle pointing right (dir = 1) or left (dir = -1); smooth edges. */
    static void tri(GuiGraphicsExtractor g, float x, float y, float h, int dir, int color) { tri(g, x, y, h, 0.5f, dir, color); }

    static void tri(GuiGraphicsExtractor g, float x, float y, float h, float wf, int dir, int color) {
        int X = Math.round(x * S), Y = Math.round(y * S), H = Math.round(h * S), Wd = Math.round(h * wf * S);
        g.pose().pushMatrix();
        g.pose().scale(1f / S, 1f / S);
        for (int i = 0; i < H; i++) {
            double t = 1 - Math.abs((i + 0.5) - H / 2.0) / (H / 2.0);
            int len = Math.max(1, (int) Math.round(Wd * t));
            if (dir > 0) g.fill(X, Y + i, X + len, Y + i + 1, color);
            else g.fill(X + Wd - len, Y + i, X + Wd, Y + i + 1, color);
        }
        g.pose().popMatrix();
    }

    // ---- text ----
    static void text(GuiGraphicsExtractor g, Font f, String s, int x, int y, int color, boolean shadow) {
        g.text(f, s, x, y, color, shadow);
    }

    static String clip(Font f, String s, int maxW) {
        if (s == null) return "";
        if (f.width(s) <= maxW) return s;
        while (s.length() > 1 && f.width(s + "...") > maxW) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    static String time(long ms) {
        long s = Math.max(0, ms / 1000);
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }
}
