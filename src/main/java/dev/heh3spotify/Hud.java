package dev.heh3spotify;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The always-on now-playing HUD: a smooth glass card with a neon border, album art, progress and volume. */
final class Hud {
    private Hud() {}

    static boolean hasVolume(SpotifyMod.Config c, boolean has) { return c.showVolume && has && Media.volume >= 0 && SpotifyMod.api(); }

    static int height(SpotifyMod.Config c, boolean has) {
        if (c.compact) return 28;
        return 52 + (hasVolume(c, has) ? 13 : 0);
    }

    /** Top-left corner of a w x h box for the chosen anchor and offsets. */
    static int[] place(SpotifyMod.Config c, int sw, int sh, int w, int h) {
        String a = c.anchor;
        int x = a.charAt(1) == 'l' ? c.offX : a.charAt(1) == 'c' ? (sw - w) / 2 : sw - w - c.offX;
        int y = a.charAt(0) == 't' ? c.offY : a.charAt(0) == 'm' ? (sh - h) / 2 : sh - h - c.offY;
        return new int[]{Math.max(0, x), Math.max(0, y)};
    }

    static void draw(GuiGraphicsExtractor g) {
        var c = SpotifyMod.cfg;
        if (!c.hud) return;
        Media.ensure();
        boolean has = Media.ok && !Media.title.isEmpty();
        if (!has && c.hideWhenIdle) return;
        if (has && !Media.playing() && c.hideWhenPaused) return;
        Minecraft mc = Minecraft.getInstance();
        float k = c.scale / 100f;
        int w = c.width, h = height(c, has);
        int sw = Math.round(mc.getWindow().getGuiScaledWidth() / k), sh = Math.round(mc.getWindow().getGuiScaledHeight() / k);
        int[] p = place(c, sw, sh, w, h);
        g.pose().pushMatrix();
        try {
            g.pose().scale(k, k);
            render(g, mc.font, p[0], p[1], w, has, c);
        } catch (Throwable t) {
            SpotifyMod.fail("HUD", t);
        } finally {
            g.pose().popMatrix();
        }
    }

    /** Draws the HUD at x, y (the pop-out also uses this for its live preview). */
    static void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, boolean has, SpotifyMod.Config c) {
        SpotifyMod.Look l = c.look;
        int a1 = Draw.argb(l.accent), a2 = Draw.argb(l.accent2), a3 = Draw.argb(l.accent3);
        int bg = Draw.argb(l.bg), bg2 = Draw.argb(l.bg2), text = Draw.argb(l.text), sub = Draw.argb(l.sub);
        int h = height(c, has);
        float r = Math.min(l.radius, h / 2f);

        frame(g, x, y, w, h, r, l);

        String t1 = has ? Media.title : "Nothing playing";
        String t2 = has ? Media.artist : hint();
        boolean playing = has && Media.playing();

        if (c.compact) {
            float ar = Math.max(3, r * 0.6f);
            art(g, x + 5, y + 5, 18, ar, a1, a2);
            int tx = x + 28;
            String line = t1 + (c.showArtist && !t2.isEmpty() ? "  -  " + t2 : "");
            Draw.text(g, font, Draw.clip(font, line, w - 28 - 10), tx, y + 7, text, true);
            if (c.showProgress && has && Media.dur > 0) progress(g, x + 28, y + h - 6, w - 28 - 10, 2, a1, a2, a3, sub, false);
            return;
        }

        int tx = x + (c.showArt ? 55 : 12), tw = w - (tx - x) - 10;
        if (c.showArt) art(g, x + 7, y + 6, 40, Math.max(3, r * 0.6f), a1, a2);
        if (has) {
            tw = Math.max(20, tw);
        }
        int titleW = tw;
        if (c.showArtist && !t2.isEmpty()) {
            Draw.text(g, font, Draw.clip(font, t1, titleW), tx, y + 8, text, true);
            Draw.text(g, font, Draw.clip(font, t2, tw), tx, y + 19, sub, true);
        } else {
            Draw.text(g, font, Draw.clip(font, t1, titleW), tx, y + 13, text, true);
        }
        if (c.showProgress && has && Media.dur > 0) {
            progress(g, tx, y + 33, tw, 4, a1, a2, a3, sub, true);
            if (c.showTimes) {
                Draw.text(g, font, Draw.time(Media.position()), tx, y + 41, sub, true);
                String d = Draw.time(Media.dur);
                Draw.text(g, font, d, tx + tw - font.width(d), y + 41, sub, true);
            }
        }
        if (hasVolume(c, has)) {
            int vy = y + 52;
            String v = Media.volume + "%";
            int vw = tw - font.width(v) - 6;
            Draw.rr(g, tx, vy + 2, vw, 3, 1.5f, Draw.withAlpha(sub, 0x40));
            int fill = Math.round(vw * Media.volume / 100f);
            if (fill > 1) Draw.bar3(g, tx, vy + 2, fill, 3, a1, a2, a3);
            Draw.text(g, font, v, tx + tw - font.width(v), vy, sub, true);
        }
    }

    static String hint() {
        if (SpotifyMod.api() && !Api.connected) return "Open the player (F8) > Connect";
        return Media.WINDOWS || SpotifyMod.api() ? "Open Spotify and press play" : "Windows only";
    }

    /** Progress track with a gradient fill and a small knob. */
    static void progress(GuiGraphicsExtractor g, int x, int y, int w, int h, int a1, int a2, int a3, int sub, boolean knob) {
        Draw.rr(g, x, y, w, h, h / 2f, Draw.withAlpha(sub, 0x40));
        float f = Media.dur > 0 ? Math.min(1f, Media.position() / (float) Media.dur) : 0;
        int fill = Math.round(w * f);
        if (fill > 1) Draw.bar3(g, x, y, Math.max(h, fill), h, a1, a2, a3);
        if (knob && fill > 1) Draw.circle(g, x + fill - h * 0.75f, y - h * 0.25f, h * 1.5f, 0xFFFFFFFF, 0xFFE6E6F5);
    }

    /** Album art, or a gradient square until the picture arrives. */
    static void art(GuiGraphicsExtractor g, float x, float y, float size, float radius, int a1, int a2) {
        int[] px = Media.artPx;
        if (px != null && SpotifyMod.api()) Draw.art(g, px, Media.ART_N, x, y, size, radius);
        else Draw.rrg(g, x, y, size, size, radius, a1, a2);
    }

    /** Glow, glass background and neon border shared by the HUD and the pop-out card. */
    static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, float r, SpotifyMod.Look l) {
        int a1 = Draw.argb(l.accent), a2 = Draw.argb(l.accent2), a3 = Draw.argb(l.accent3);
        int bg = Draw.argb(l.bg), bg2 = Draw.argb(l.bg2);
        int glass = l.glass * 255 / 100, ba = l.borderA * 255 / 100;
        // soft glow: a few one-pixel rings that fade out (they never overlap each other or the card)
        for (int k = 1; k <= 3 && l.glow > 0; k++) {
            int al = l.glow * (k == 1 ? 70 : k == 2 ? 36 : 16) / 100 * 255 / 100;
            Draw.strokeH(g, x - k, y - k, w + 2 * k, h + 2 * k, r + k, 1f, Draw.withAlpha(a1, al), Draw.withAlpha(a2, al), Draw.withAlpha(a3, al));
        }
        Draw.rrg(g, x, y, w, h, r, Draw.withAlpha(bg, glass), Draw.withAlpha(bg2, glass));
        if (l.borderW > 0) Draw.strokeH(g, x, y, w, h, r, l.borderW / 2f, Draw.withAlpha(a1, ba), Draw.withAlpha(a2, ba), Draw.withAlpha(a3, ba));
    }

    /** A thick line drawn as overlapping opaque dots (use opaque colours only). */
    static void line(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, float th, int color) {
        float len = (float) Math.hypot(x2 - x1, y2 - y1);
        int n = Math.max(1, (int) Math.ceil(len / (th * 0.4f)));
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            Draw.circle(g, x1 + (x2 - x1) * t - th / 2, y1 + (y2 - y1) * t - th / 2, th, color, color);
        }
    }

    /** The little green Spotify mark: a circle with three curved bars. */
    static void logo(GuiGraphicsExtractor g, float x, float y, float d) {
        Draw.circle(g, x, y, d, 0xFF1ED760, 0xFF1DB954);
        int dark = 0xFF0B1A10;
        float th = Math.max(1f, d * 0.11f), sag = d * 0.07f;
        float[] hw = {0.30f, 0.25f, 0.19f}, yy = {0.36f, 0.52f, 0.67f};
        for (int k = 0; k < 3; k++) {
            float w = hw[k] * d, cx = x + d / 2, cy = y + yy[k] * d;
            int n = 6;
            float px = 0, py = 0;
            for (int i = 0; i <= n; i++) {
                float t = -1 + 2f * i / n;
                float qx = cx + t * w, qy = cy - sag * (1 - t * t);
                if (i > 0) line(g, px, py, qx, qy, th, dark);
                px = qx; py = qy;
            }
        }
    }

    /** Blue verified badge with a white check. */
    static void badge(GuiGraphicsExtractor g, float x, float y, float d) {
        Draw.circle(g, x, y, d, 0xFF38B6FF, 0xFF1D9BF0);
        float th = Math.max(0.9f, d * 0.13f);
        line(g, x + d * 0.27f, y + d * 0.52f, x + d * 0.44f, y + d * 0.69f, th, 0xFFFFFFFF);
        line(g, x + d * 0.44f, y + d * 0.69f, x + d * 0.75f, y + d * 0.32f, th, 0xFFFFFFFF);
    }

    /** Skip icons: a bar and a triangle, both opaque so the edges stay clean. */
    static void skip(GuiGraphicsExtractor g, float x, float y, float h, boolean next, int color) {
        float tw = h * 0.78f, bw = Math.max(1.5f, h * 0.16f);
        if (next) { Draw.tri(g, x, y, h, 0.78f, 1, color); Draw.rr(g, x + tw + 0.5f, y, bw, h, bw / 2, color); }
        else { Draw.rr(g, x, y, bw, h, bw / 2, color); Draw.tri(g, x + bw + 0.5f, y, h, 0.78f, -1, color); }
    }

    /** Speaker with two sound waves. */
    static void speaker(GuiGraphicsExtractor g, float x, float y, float h, int color) {
        Draw.rr(g, x, y + h * 0.33f, h * 0.30f, h * 0.34f, 0.6f, color);
        Draw.tri(g, x + h * 0.22f, y + h * 0.10f, h * 0.80f, 0.5f, -1, color);
        float cx = x + h * 0.62f, cy = y + h * 0.5f, th = Math.max(0.9f, h * 0.09f);
        for (int k = 1; k <= 2; k++) {
            float r = h * (0.20f + 0.17f * k);
            float px = 0, py = 0;
            for (int i = 0; i <= 6; i++) {
                double a = Math.toRadians(-48 + 96 * i / 6.0);
                float qx = cx + (float) Math.cos(a) * r, qy = cy + (float) Math.sin(a) * r;
                if (i > 0) line(g, px, py, qx, qy, th, color);
                px = qx; py = qy;
            }
        }
    }
}
