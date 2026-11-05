package dev.heh3spotify;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

/** The pop-out: a glass player card plus Style, Presets and Connect tabs. Everything follows your own look. */
public class PlayerScreen extends Screen {
    record Hit(int x, int y, int w, int h, Runnable run) {}
    record Slider(int x, int y, int w, int h, DoubleConsumer set, boolean once) {}

    static final String[] TABS = {"Player", "Style", "Layout", "Presets", "Connect"};
    static final int VW = 420, VH = 368;
    static final String[] TARGETS = {"Acc 1", "Acc 2", "Acc 3", "Back 1", "Back 2", "Text", "Sub"};
    
    final List<Hit> hits = new ArrayList<>();
    final List<Slider> sliders = new ArrayList<>();
    Slider active;
    GuiGraphicsExtractor g;
    int mx, my, tab = 1, target = 0, pscroll = 0;
    float k = 1;
    int ox, oy;
    /** Mouse position in the pop-out's own coordinates. */
    double ux(double v) { return v / k - ox; }
    double uy(double v) { return v / k - oy; }
    String focus = "";                 // which text box gets the keyboard: "hex", "client" or "name"
    String hexBuf = "", nameBuf = "";
    int a1, a2, a3, bg, bg2, text, sub, panel, line;

    public PlayerScreen() {
        super(Component.literal("Heh3 Spotify"));
        Media.ensure();
        if (SpotifyMod.api() && !Api.connected) tab = 4;
    }

    @Override public boolean isPauseScreen() { return false; }

    // ---------------- helpers ----------------
    boolean hover(int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    void t(String s, int x, int y, int c) { g.text(font, s, x, y, c, false); }

    void tc(String s, int cx, int y, int c) { t(s, cx - font.width(s) / 2, y, c); }

    void hit(int x, int y, int w, int h, Runnable run) { hits.add(new Hit(x, y, w, h, run)); }

    /** Pill button. */
    void btn(int x, int y, int w, int h, String label, boolean on, Runnable run) {
        boolean hv = hover(x, y, w, h);
        if (on) Draw.rrg(g, x, y, w, h, h / 2f, a1, a2);
        else Draw.rr(g, x, y, w, h, h / 2f, hv ? Draw.lighten(panel, 22) : panel);
        tc(label, x + w / 2, y + (h - 8) / 2 + 1, on ? 0xFF0B0B14 : hv ? text : sub);
        hit(x, y, w, h, run);
    }

    void iconBase(int x, int y, int w, int h, Runnable run) {
        Draw.rr(g, x, y, w, h, h / 2f, hover(x, y, w, h) ? Draw.lighten(panel, 22) : panel);
        hit(x, y, w, h, run);
    }

    void slider(int x, int y, int w, String label, int value, int min, int max, String suffix, java.util.function.IntConsumer set) {
        t(label, x, y, sub);
        String v = label.startsWith("Border width") ? (value / 2f) + "px" : value + suffix;
        t(v, x + w - font.width(v), y, text);
        int ty = y + 11;
        Draw.rr(g, x, ty, w, 4, 2, Draw.withAlpha(sub, 0x40));
        float f = (value - min) / (float) (max - min);
        int fill = Math.round(w * f);
        if (fill > 1) Draw.bar3(g, x, ty, Math.max(4, fill), 4, a1, a2, a3);
        Draw.circle(g, x + fill - 3.5f, ty - 1.5f, 7, 0xFFFFFFFF, 0xFFE0E0F0);
        sliders.add(new Slider(x - 3, ty - 5, w + 6, 14, d -> { set.accept(min + (int) Math.round(d * (max - min))); }, false));
    }

    void toggle(int x, int y, int w, String label, boolean on, Runnable flip) {
        t(label, x, y + 4, text);
        int tx = x + w - 24;
        if (on) Draw.rrg(g, tx, y + 2, 24, 12, 6, a1, a2); else Draw.rr(g, tx, y + 2, 24, 12, 6, Draw.withAlpha(sub, 0x50));
        Draw.circle(g, on ? tx + 13 : tx + 1.5f, y + 3.5f, 9, 0xFFFFFFFF, 0xFFE6E6F2);
        hit(x, y, w, 16, flip);
    }

    void field(int x, int y, int w, String id, String value, String hint) {
        boolean f = focus.equals(id);
        Draw.rr(g, x, y, w, 16, 8, panel);
        if (f) Draw.strokeH(g, x, y, w, 16, 8, 1, a1, a2, a3);
        String show = value.isEmpty() && !f ? hint : Draw.clip(font, value, w - 14) + (f && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        t(show, x + 7, y + 4, value.isEmpty() && !f ? Draw.withAlpha(sub, 0xA0) : text);
        hit(x, y, w, 16, () -> focus = id);
    }

    // ---------------- frame ----------------
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        g = graphics;
        k = Math.min(1f, Math.min((width - 8f) / VW, (height - 8f) / VH));
        mx = Math.round(mouseX / k); my = Math.round(mouseY / k);
        hits.clear(); sliders.clear();
        Media.ensure();
        var c = SpotifyMod.cfg; var l = c.look;
        a1 = Draw.argb(l.accent); a2 = Draw.argb(l.accent2); a3 = Draw.argb(l.accent3);
        bg = Draw.argb(l.bg); bg2 = Draw.argb(l.bg2); text = Draw.argb(l.text); sub = Draw.argb(l.sub);
        panel = Draw.lerp(bg, 0xFFFFFFFF, 0.08f);
        line = Draw.withAlpha(sub, 0x30);

        ox = Math.round((width / k - VW) / 2f); oy = Math.round((height / k - VH) / 2f);
        g.pose().pushMatrix();
        try {
        g.pose().scale(k, k);
        g.pose().translate(ox, oy);
        mx -= ox; my -= oy;

        // window
        Draw.rrg(g, 0, 0, VW, VH, 16, Draw.withAlpha(Draw.lerp(bg2, 0xFF000000, 0.35f), 0xEE), Draw.withAlpha(Draw.lerp(bg2, 0xFF000000, 0.6f), 0xEE));
        Draw.strokeH(g, 0, 0, VW, VH, 16, 1, Draw.withAlpha(sub, 0x38), Draw.withAlpha(sub, 0x20), Draw.withAlpha(sub, 0x38));

        card(14, 14, VW - 28, 108);

        // tab bar (like the editor in the reference)
        int tw = 0;
        for (String t : TABS) tw += font.width(t) + 18 + 4;
        int tx = (VW - tw + 4) / 2;
        Draw.rr(g, tx - 4, 130, tw + 4, 20, 10, Draw.withAlpha(0xFFFFFFFF, 0x10));
        for (int i = 0; i < TABS.length; i++) {
            int w = font.width(TABS[i]) + 18;
            final int idx = i;
            btn(tx, 132, w, 16, TABS[i], tab == i, () -> { tab = idx; pscroll = 0; focus = ""; });
            tx += w + 4;
        }

        int cx = 16, cw = VW - 32, top = 160;
        switch (tab) {
            case 0 -> playerTab(cx, cw, top);
            case 1 -> styleTab(cx, cw, top);
            case 2 -> layoutTab(cx, cw, top);
            case 3 -> presetsTab(cx, cw, top);
            default -> connectTab(cx, cw, top);
        }
        footer();
        } catch (Throwable t) {
            SpotifyMod.fail("pop-out", t);
            Minecraft.getInstance().gui.setScreen(null);
        } finally {
            g.pose().popMatrix();
        }
    }

    // ---------------- footer: who made it, and a button that opens the discord ----------------
    static final String CREATOR = "Heh3", DISCORD_URL = "https://discord.gg/Y53P35Dbjn";

    void footer() {
        int fy = VH - 24;
        g.fill(16, fy - 4, VW - 16, fy - 3, Draw.withAlpha(0xFFFFFFFF, 0x10));
        t("made by " + CREATOR, 18, fy + 3, sub);
        String join = "Join the Discord";
        int bw = font.width(join) + 18;
        btn(VW - 16 - bw, fy - 1, bw, 16, join, false, this::openDiscord);
    }

    void openDiscord() {
        Api.openBrowser(DISCORD_URL);
        SpotifyMod.say("Discord: " + DISCORD_URL); // also in chat in case no browser opens
    }

    // ---------------- the card (same layout as the reference picture) ----------------
    void card(int x, int y, int w, int h) {
        var c = SpotifyMod.cfg; var l = c.look;
        boolean has = Media.ok && !Media.title.isEmpty();
        float r = Math.min(h / 2f, Math.max(0, l.radius * 2f));
        Hud.frame(g, x, y, w, h, r, l);

        // album art
        Hud.art(g, x + 12, y + 12, 58, Math.max(4, r * 0.42f), a1, a2);

        int tx = x + 82;
        int ctrl = x + Math.round(w * 0.66f);          // controls start here
        String title = has ? Media.title : (SpotifyMod.api() && !Api.connected ? "Not connected" : "Nothing playing");
        String artist = has ? Media.artist : Hud.hint();
        float ts = 1.55f;
        int tmax = ctrl - tx - 14;
        String tt = Draw.clip(font, title, (int) (tmax / ts));
        Draw.bigText(g, font, tt, tx, y + 13, ts, text);
        Draw.bigText(g, font, tt, tx + 0.45f, y + 13, ts, text); // second pass fakes a bold weight
        if (c.showArtist) Draw.bigText(g, font, Draw.clip(font, artist, (int) ((ctrl - tx - 6) / 1.15f)), tx, y + 36, 1.15f, sub);
        if (!Media.activeNotice().isEmpty()) t(Draw.clip(font, Media.activeNotice(), w - 90), tx, y + 51, 0xFFFF7A7A);

        // controls: previous, pause ring, next
        int cy = y + 30;
        float pcx = x + w * 0.775f;
        Hud.skip(g, x + w * 0.675f - 5, cy - 6, 12, false, text);
        hit(Math.round(x + w * 0.675f - 12), cy - 12, 26, 24, Media::prev);
        Draw.circle(g, pcx - 15, cy - 15, 30, a1, a3);
        Draw.circle(g, pcx - 13.5f, cy - 13.5f, 27, Draw.lerp(bg, 0xFFFFFFFF, 0.06f), bg2);
        hit(Math.round(pcx - 15), cy - 15, 30, 30, Media::playPause);
        if (Media.playing()) { Draw.rr(g, pcx - 5.5f, cy - 7, 4, 14, 1.5f, text); Draw.rr(g, pcx + 1.5f, cy - 7, 4, 14, 1.5f, text); }
        else Draw.tri(g, pcx - 4, cy - 7, 14, 0.8f, 1, text);
        Hud.skip(g, x + w * 0.875f - 7, cy - 6, 12, true, text);
        hit(Math.round(x + w * 0.875f - 12), cy - 12, 26, 24, Media::next);

        // progress
        int py = y + 62, pw = x + w - 14 - tx;
        long pos = Media.position();
        Draw.rr(g, tx, py, pw, 5, 2.5f, Draw.withAlpha(a2, 0x38));
        int fill = Media.dur > 0 ? (int) Math.round(pw * Math.min(1.0, pos / (double) Media.dur)) : 0;
        if (fill > 2) Draw.bar(g, tx, py, fill, 5, Draw.lerp(a3, a2, 0.35f), Draw.lighten(a3, 70));
        if (Media.dur > 0) sliders.add(new Slider(tx - 3, py - 5, pw + 6, 15, f -> Media.seekTo((long) (f * Media.dur)), true));
        if (c.showTimes) {
            t(Draw.time(pos), tx, py + 9, text);
            String d = Draw.time(Media.dur);
            t(d, tx + pw - font.width(d), py + 9, text);
        }

        // volume row
        int vy = y + 92;
        g.fill(x + 14, y + 83, x + w - 14, y + 84, Draw.withAlpha(0xFFFFFFFF, 0x10));
        Hud.speaker(g, x + w * 0.14f, vy - 5, 11, text);
        int sx = x + Math.round(w * 0.235f), sw = Math.round(w * 0.545f);
        boolean vol = Media.canSetVolume();
        int v = vol ? Math.max(0, Media.volume) : 0;
        Draw.rr(g, sx, vy - 3, sw, 6, 3, Draw.withAlpha(a2, 0x38));
        int vf = Math.round(sw * v / 100f);
        if (vf > 3) Draw.bar3(g, sx, vy - 3, vf, 6, a3, a2, a1);
        float kn = 13;
        Draw.circle(g, sx + vf - kn / 2 - 1.5f, vy - kn / 2 - 1.5f, kn + 3, Draw.withAlpha(a1, 0x30), Draw.withAlpha(a1, 0x30));
        Draw.circle(g, sx + vf - kn / 2, vy - kn / 2, kn, 0xFFFFFFFF, 0xFFEDEDF8);
        if (vol) sliders.add(new Slider(sx - 6, vy - 9, sw + 12, 18, f -> Media.setVolume((int) Math.round(f * 100)), false));
        else {
            hit(sx - 6, vy - 9, sw / 2, 18, () -> Media.volume(false));
            hit(sx + sw / 2, vy - 9, sw / 2 + 6, 18, () -> Media.volume(true));
        }
        t(vol ? v + "%" : "", sx + sw + 12, vy - 4, text);
    }

    // ---------------- column helpers ----------------
    int LX = 16, RX = 218, CW = 186;

    // ---------------- player tab ----------------
    void playerTab(int x, int w, int y) {
        var c = SpotifyMod.cfg;
        int bw = (w - 18) / 4;
        int[] xs = {x, x + bw + 6, x + 2 * (bw + 6), x + 3 * (bw + 6)};
        btn(xs[0], y, bw, 18, Media.shuffle ? "Shuffle: on" : "Shuffle: off", Media.shuffle, Media::shuffle);
        btn(xs[1], y, bw, 18, Media.repeat == 1 ? "Repeat: song" : Media.repeat == 2 ? "Repeat: all" : "Repeat: off", Media.repeat != 0, Media::repeat);
        btn(xs[2], y, bw, 18, "Back 10s", false, () -> Media.seekBy(-10000));
        btn(xs[3], y, bw, 18, "Forward 10s", false, () -> Media.seekBy(10000));
        y += 26;
        btn(xs[0], y, bw, 18, "Restart", false, () -> Media.seekTo(0));
        btn(xs[1], y, bw, 18, "Mute", false, Media::mute);
        btn(xs[2], y, bw, 18, c.hud ? "HUD: on" : "HUD: off", c.hud, () -> c.hud = !c.hud);
        btn(xs[3], y, bw, 18, "Open Spotify", false, Media::openSpotify);
        y += 30;
        if (!Media.canSetVolume()) {
            t("Volume buttons", x, y + 3, sub);
            btn(x + 100, y, 40, 16, "Vol -", false, () -> Media.volume(false));
            btn(x + 146, y, 40, 16, "Vol +", false, () -> Media.volume(true));
            y += 24;
        }
        if (!Media.device.isEmpty()) { t("Playing on: " + Media.device, x, y, sub); y += 12; }
        t("Keys:  Space play   Left/Right skip   Up/Down volume   M mute   S shuffle   R repeat", x, y, Draw.withAlpha(sub, 0xA0));
        y += 12;
        t("Change the pop-out and HUD in the Style, Layout and Presets tabs.", x, y, Draw.withAlpha(sub, 0xA0));
    }

    // ---------------- style tab (settings left, colors right, like the editor reference) ----------------
    String get(int i) {
        var l = SpotifyMod.cfg.look;
        return switch (i) { case 0 -> l.accent; case 1 -> l.accent2; case 2 -> l.accent3; case 3 -> l.bg; case 4 -> l.bg2; case 5 -> l.text; default -> l.sub; };
    }

    void set(int i, String hex) {
        if (!Draw.validHex(hex)) return;
        hex = hex.toUpperCase();
        var l = SpotifyMod.cfg.look;
        switch (i) { case 0 -> l.accent = hex; case 1 -> l.accent2 = hex; case 2 -> l.accent3 = hex; case 3 -> l.bg = hex; case 4 -> l.bg2 = hex; case 5 -> l.text = hex; default -> l.sub = hex; }
        hexBuf = hex;
    }

    void channel(int ch, int v) {
        int col = Draw.argb(get(target));
        int[] rgb = {(col >> 16) & 255, (col >> 8) & 255, col & 255};
        rgb[ch] = Draw.clamp(v);
        set(target, Draw.hex(rgb[0], rgb[1], rgb[2]));
    }

    void styleTab(int x, int w, int y) {
        var c = SpotifyMod.cfg; var l = c.look;
        int sg = 24;
        t("Settings", LX, y - 1, text);
        int sy = y + 12;
        slider(LX, sy, CW, "Border radius", l.radius, 0, 20, "", v -> l.radius = v); sy += sg;
        slider(LX, sy, CW, "Border width", l.borderW, 0, 8, "", v -> l.borderW = v); sy += sg;
        slider(LX, sy, CW, "Border opacity", l.borderA, 0, 100, "%", v -> l.borderA = v); sy += sg;
        slider(LX, sy, CW, "Glass opacity", l.glass, 10, 100, "%", v -> l.glass = v); sy += sg;
        slider(LX, sy, CW, "Glow", l.glow, 0, 100, "%", v -> l.glow = v); sy += sg;
        slider(LX, sy, CW, "HUD size", c.scale, 60, 200, "%", v -> c.scale = v); sy += sg;

        // colors
        t("Colors", RX, y - 1, text);
        int ry2 = y + 12;
        int tw = (CW - 3 * 4) / 4;
        for (int i = 0; i < TARGETS.length; i++) {
            int col = i % 4, row = i / 4;
            int bx = RX + col * (tw + 4), by = ry2 + row * 18;
            final int idx = i;
            btn(bx, by, tw, 15, TARGETS[i], target == i, () -> { target = idx; hexBuf = get(idx); });
        }
        // little swatch row showing the real colours of each target
        int sw = (CW - 6 * 3) / 7;
        for (int i = 0; i < 7; i++) {
            int bx = RX + i * (sw + 3), by = ry2 + 38;
            Draw.rr(g, bx, by, sw, 5, 2.5f, Draw.argb(get(i)));
        }
        ry2 += 50;
        if (!focus.equals("hex") && !Draw.validHex(hexBuf)) hexBuf = get(target);
        t("#", RX, ry2 + 4, sub);
        field(RX + 8, ry2, 76, "hex", focus.equals("hex") ? hexBuf : get(target), "");
        btn(RX + 90, ry2, 40, 16, "Paste", false, this::pasteHex);
        int col = Draw.argb(get(target)) | 0xFF000000;
        Draw.rr(g, RX + 136, ry2, CW - 136, 16, 8, col);
        ry2 += 22;
        String[] nm = {"R", "G", "B"};
        int[] rgb = {(col >> 16) & 255, (col >> 8) & 255, col & 255};
        for (int q = 0; q < 3; q++) {
            final int ch = q;
            t(nm[q], RX, ry2 - 1, sub);
            int sx = RX + 11, sw2 = CW - 11 - 24;
            Draw.rr(g, sx, ry2 + 1, sw2, 4, 2, Draw.withAlpha(sub, 0x40));
            int fw = Math.round(sw2 * rgb[q] / 255f);
            int tint = q == 0 ? 0xFFFF5A7A : q == 1 ? 0xFF3DE08A : 0xFF4DA3FF;
            if (fw > 1) Draw.rr(g, sx, ry2 + 1, Math.max(4, fw), 4, 2, tint);
            Draw.circle(g, sx + fw - 3.5f, ry2 - 0.5f, 7, 0xFFFFFFFF, 0xFFE0E0F0);
            sliders.add(new Slider(sx - 3, ry2 - 5, sw2 + 6, 14, f -> channel(ch, (int) Math.round(f * 255)), false));
            t(String.valueOf(rgb[q]), RX + CW - font.width(String.valueOf(rgb[q])), ry2 - 1, text);
            ry2 += 14;
        }
        ry2 += 3;
        t("Variants", RX, ry2 - 2, sub);
        ry2 += 9;
        float[] hsb = java.awt.Color.RGBtoHSB(rgb[0], rgb[1], rgb[2], null);
        int n = 8, vw = (CW - (n - 1) * 3) / n;
        for (int row = 0; row < 2; row++) {
            for (int i = 0; i < n; i++) {
                int sh;
                if (row == 0) sh = i < 4 ? Draw.lerp(col, 0xFF000000, (4 - i) * 0.2f) : Draw.lerp(col, 0xFFFFFFFF, (i - 3) * 0.16f);
                else sh = 0xFF000000 | java.awt.Color.HSBtoRGB((hsb[0] + (i - 3.5f) / 14f + 1f) % 1f, hsb[1], hsb[2]);
                String hx = String.format("%06X", sh & 0xFFFFFF);
                int bx = RX + i * (vw + 3), by = ry2 + row * 17;
                Draw.rr(g, bx, by, vw, 14, 6, sh | 0xFF000000);
                hit(bx, by, vw, 14, () -> set(target, hx));
            }
        }
    }

    void pasteHex() {
        String s = clipboard().replace("#", "").trim();
        if (Draw.validHex(s)) set(target, s);
    }

    // ---------------- layout tab ----------------
    void layoutTab(int x, int w, int y) {
        var c = SpotifyMod.cfg;
        int r = 19;
        t("Show", LX, y - 1, text);
        int ly = y + 12;
        toggle(LX, ly, CW, "HUD on screen", c.hud, () -> c.hud = !c.hud); ly += r;
        toggle(LX, ly, CW, "Album art", c.showArt, () -> c.showArt = !c.showArt); ly += r;
        toggle(LX, ly, CW, "Artist", c.showArtist, () -> c.showArtist = !c.showArtist); ly += r;
        toggle(LX, ly, CW, "Progress bar", c.showProgress, () -> c.showProgress = !c.showProgress); ly += r;
        toggle(LX, ly, CW, "Times", c.showTimes, () -> c.showTimes = !c.showTimes); ly += r;
        toggle(LX, ly, CW, "Volume bar", c.showVolume, () -> c.showVolume = !c.showVolume);

        t("Behaviour and position", RX, y - 1, text);
        int ry2 = y + 12;
        toggle(RX, ry2, CW, "Compact (one line)", c.compact, () -> c.compact = !c.compact); ry2 += r;
        toggle(RX, ry2, CW, "Hide when paused", c.hideWhenPaused, () -> c.hideWhenPaused = !c.hideWhenPaused); ry2 += r;
        toggle(RX, ry2, CW, "Hide when idle", c.hideWhenIdle, () -> c.hideWhenIdle = !c.hideWhenIdle); ry2 += r + 2;
        int ai = Math.max(0, Draw.ANCHORS.indexOf(c.anchor));
        stepper(RX, ry2, CW, "Position", Draw.ANCHOR_NAMES[ai], () -> c.anchor = Draw.ANCHORS.get((ai + 7) % 8), () -> c.anchor = Draw.ANCHORS.get((ai + 1) % 8)); ry2 += r;
        stepper(RX, ry2, CW, "Offset X", String.valueOf(c.offX), () -> c.offX -= 4, () -> c.offX += 4); ry2 += r;
        stepper(RX, ry2, CW, "Offset Y", String.valueOf(c.offY), () -> c.offY -= 4, () -> c.offY += 4); ry2 += r;
        stepper(RX, ry2, CW, "HUD width", String.valueOf(c.width), () -> c.width -= 10, () -> c.width += 10);
        if (!Media.canSetVolume() && Media.WINDOWS) { ly += r; stepper(LX, ly, CW, "Volume step", c.volSteps * 2 + "%", () -> c.volSteps--, () -> c.volSteps++); }
    }

    void stepper(int x, int y, int w, String label, String value, Runnable dec, Runnable inc) {
        t(label, x, y + 4, text);
        int bx = x + w - 96;
        btn(bx, y, 14, 14, "<", false, dec);
        tc(Draw.clip(font, value, 56), bx + 48, y + 4, sub);
        btn(bx + 82, y, 14, 14, ">", false, inc);
    }

    // ---------------- presets tab ----------------
    void presetsTab(int x, int w, int y) {
        var c = SpotifyMod.cfg;
        field(x, y, w - 100, "name", nameBuf, "Name your look, then Save...");
        btn(x + w - 94, y, 94, 16, "Save as preset", true, this::savePreset);
        y += 24;
        List<SpotifyMod.NamedLook> all = new ArrayList<>(Presets.BUILT_IN);
        all.addAll(c.presets);
        int rows = (all.size() + 1) / 2, vis = 6;
        pscroll = Math.max(0, Math.min(Math.max(0, rows - vis), pscroll));
        for (int i = pscroll * 2; i < Math.min(all.size(), (pscroll + vis) * 2); i++) {
            int col = i % 2, row = i / 2 - pscroll;
            presetCell(x + col * (CW + 12 - 0), y + row * 22, CW, all.get(i), i >= Presets.BUILT_IN.size());
        }
        if (rows > vis) t("Scroll for more", x + w - 70, y + vis * 22, Draw.withAlpha(sub, 0xA0));
    }

    void presetCell(int x, int y, int w, SpotifyMod.NamedLook p, boolean custom) {
        var l = p.look;
        Draw.rrg(g, x, y, 24, 18, 6, Draw.argb(l.bg), Draw.argb(l.bg2));
        Draw.strokeH(g, x, y, 24, 18, 6, 1, Draw.argb(l.accent), Draw.argb(l.accent2), Draw.argb(l.accent3));
        t(Draw.clip(font, p.name, w - 118), x + 32, y + 5, text);
        btn(x + w - (custom ? 88 : 46), y + 1, 44, 16, "Apply", false, () -> { SpotifyMod.cfg.look = p.look.copy(); hexBuf = get(target); });
        if (custom) btn(x + w - 40, y + 1, 40, 16, "Delete", false, () -> SpotifyMod.cfg.presets.remove(p));
    }

    void savePreset() {
        String n = nameBuf.trim();
        if (n.isEmpty()) n = "My look " + (SpotifyMod.cfg.presets.size() + 1);
        final String fn = n;
        SpotifyMod.cfg.presets.removeIf(p -> p.name.equalsIgnoreCase(fn));
        SpotifyMod.cfg.presets.add(new SpotifyMod.NamedLook(n, SpotifyMod.cfg.look.copy()));
        nameBuf = "";
        focus = "";
    }

    // ---------------- connect tab ----------------
    void connectTab(int x, int w, int y) {
        var c = SpotifyMod.cfg;
        t("Status:", x, y, sub);
        t(Draw.clip(font, Api.status, w - 46), x + 42, y, Api.connected ? 0xFF5CE68A : text);
        y += 15;
        t("1. Create an app at developer.spotify.com/dashboard", x, y, sub); y += 11;
        t("2. Add this Redirect URI and save:", x, y, sub); y += 12;
        Draw.rr(g, x, y - 1, w, 13, 6.5f, panel);
        t(Draw.clip(font, Api.redirectUri(), w - 12), x + 6, y + 1, a1);
        y += 16;
        t("3. Paste its Client ID below (the only key).", x, y, sub); y += 11;
        t("4. Press Connect and log in once in the browser.", x, y, sub); y += 15;
        field(x, y, w - 94, "client", c.clientId, "Paste Client ID here");
        btn(x + w - 88, y, 88, 16, "Paste", false, () -> { String s = clipboard().trim(); if (!s.isEmpty()) { c.clientId = s; focus = ""; } });
        y += 22;
        int bw = (w - 12) / 3;
        btn(x, y, bw, 16, "Connect", true, () -> { c.clientId = c.clientId.trim(); c.source = "api"; SpotifyMod.save(); Api.connect(); });
        btn(x + bw + 6, y, bw, 16, "Disconnect", false, Api::disconnect);
        btn(x + 2 * (bw + 6), y, bw, 16, "Dashboard", false, () -> Api.openBrowser("https://developer.spotify.com/dashboard"));
        y += 24;
        t("Redirect port", x, y + 3, text);
        btn(x + 78, y, 14, 14, "-", false, () -> { if (c.port > 1024) c.port--; });
        tc(String.valueOf(c.port), x + 108, y + 3, text);
        btn(x + 124, y, 14, 14, "+", false, () -> { if (c.port < 65535) c.port++; });
        if (Media.WINDOWS) {
            t("Source", x + w - 190, y + 3, text);
            btn(x + w - 150, y - 1, 74, 15, "Account", SpotifyMod.api(), () -> c.source = "api");
            btn(x + w - 72, y - 1, 72, 15, "Windows", !SpotifyMod.api(), () -> c.source = "windows");
        }
        y += 22;
        t("Account mode needs Spotify Premium to control playback.", x, y, Draw.withAlpha(sub, 0xA0));
    }

    // ---------------- input ----------------
    @Override public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (e.button() == 0) {
            focus = focus.equals("client") || focus.equals("hex") || focus.equals("name") ? "" : focus;
            double mx0 = ux(e.x()), my0 = uy(e.y());
            for (Slider s : sliders) {
                if (mx0 >= s.x() && mx0 < s.x() + s.w() && my0 >= s.y() && my0 < s.y() + s.h()) {
                    active = s;
                    drag(e.x());
                    return true;
                }
            }
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (mx0 >= h.x() && mx0 < h.x() + h.w() && my0 >= h.y() && my0 < h.y() + h.h()) {
                    h.run().run();
                    SpotifyMod.fix();
                    if (focus.equals("hex")) hexBuf = get(target);
                    SpotifyMod.save();
                    return true;
                }
            }
        }
        return super.mouseClicked(e, dbl);
    }

    void drag(double x) {
        if (active == null) return;
        active.set().accept(Math.max(0, Math.min(1, (ux(x) - (active.x() + 3)) / (active.w() - 6))));
        if (!active.once()) SpotifyMod.fix();
    }

    // Not annotated on purpose: these only add behaviour, so a renamed parent method cannot break the build.
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (active == null) return false;
        if (!active.once() && active.set() != null) drag(e.x());
        return true;
    }

    public boolean mouseReleased(MouseButtonEvent e) {
        if (active != null) { SpotifyMod.fix(); SpotifyMod.save(); }
        active = null;
        return false;
    }

    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (tab == 3) { pscroll -= (int) Math.signum(sy); return true; }
        if (sy != 0) Media.volume(sy > 0);
        return true;
    }

    static String clipboard() {
        try {
            Minecraft mc = Minecraft.getInstance();
            for (var f : Minecraft.class.getDeclaredFields()) {
                f.setAccessible(true);
                Object o = f.get(mc);
                if (o == null) continue;
                try {
                    var m = o.getClass().getMethod("getClipboard");
                    if (m.getReturnType() == String.class) return (String) m.invoke(o);
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Throwable ignored) {}
        return "";
    }

    /** Letters, digits, space and minus from a key code (the window has no text-input event wired in). */
    static char typed(int k, boolean shift) {
        if (k >= 65 && k <= 90) return (char) (shift ? k : k + 32);
        if (k >= 48 && k <= 57) return (char) k;
        if (k == 32) return ' ';
        if (k == 45) return '-';
        return 0;
    }

    @Override public boolean keyPressed(KeyEvent e) {
        int kc = e.key();
        if (!focus.isEmpty()) {
            var c = SpotifyMod.cfg;
            boolean ctrl = e.hasControlDown();
            String cur = focus.equals("hex") ? hexBuf : focus.equals("client") ? c.clientId : nameBuf;
            String next = cur;
            if (kc == 256) { focus = ""; return true; }
            if (kc == 257 || kc == 335) { if (focus.equals("name")) savePreset(); focus = ""; return true; }
            if (kc == 259) next = cur.isEmpty() ? cur : cur.substring(0, cur.length() - 1);
            else if (ctrl && kc == 86) next = focus.equals("name") ? cur + clipboard().trim() : clipboard().replace("#", "").trim();
            else if (!ctrl) {
                char ch = typed(kc, e.hasShiftDown());
                if (ch != 0 && (!focus.equals("hex") || Character.digit(ch, 16) >= 0)) next = cur + ch;
            }
            if (focus.equals("hex")) {
                if (next.length() > 6) next = next.substring(0, 6);
                hexBuf = next.toUpperCase();
                if (Draw.validHex(hexBuf)) set(target, hexBuf);
            } else if (focus.equals("client")) { c.clientId = next.length() > 64 ? next.substring(0, 64) : next; SpotifyMod.save(); }
            else nameBuf = next.length() > 24 ? next.substring(0, 24) : next;
            return true;
        }
        {
            if (kc == 32) { Media.playPause(); return true; }
            if (kc == 263) { Media.prev(); return true; }
            if (kc == 262) { Media.next(); return true; }
            if (kc == 265) { Media.volume(true); return true; }
            if (kc == 264) { Media.volume(false); return true; }
            if (kc == 77) { Media.mute(); return true; }
            if (kc == 83) { Media.shuffle(); return true; }
            if (kc == 82) { Media.repeat(); return true; }
        }
        return super.keyPressed(e);
    }
}
