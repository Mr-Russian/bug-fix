package dev.heh3spotify;

import java.util.List;

/** Ready-made looks. Your own are saved in the config next to these. */
final class Presets {
    private Presets() {}

    static SpotifyMod.NamedLook mk(String name, String a1, String a2, String a3, String bg, String bg2, String text, String sub,
                                   int radius, int borderW, int borderA, int glass, int glow) {
        var l = new SpotifyMod.Look();
        l.accent = a1; l.accent2 = a2; l.accent3 = a3; l.bg = bg; l.bg2 = bg2; l.text = text; l.sub = sub;
        l.radius = radius; l.borderW = borderW; l.borderA = borderA; l.glass = glass; l.glow = glow;
        return new SpotifyMod.NamedLook(name, l);
    }

    static final List<SpotifyMod.NamedLook> BUILT_IN = List.of(
        mk("Neon", "3DE0FF", "8B5CFF", "FF4DB8", "14102B", "070B1C", "FFFFFF", "B8C4DA", 12, 2, 100, 90, 35),
        mk("Spotify", "1ED760", "1DB954", "1ED760", "181818", "0A0A0A", "FFFFFF", "B3B3B3", 10, 2, 100, 95, 15),
        mk("Sunset", "FFB347", "FF6B6B", "C44DFF", "2B1330", "12081A", "FFF4EC", "E8C9D8", 14, 2, 100, 90, 40),
        mk("Ocean", "22D3EE", "3B82F6", "6366F1", "0B1F33", "050E1B", "F0F9FF", "9CC3E6", 12, 2, 100, 90, 30),
        mk("Candy", "FF8AD8", "FFB86B", "FFE66B", "2A1633", "170B24", "FFFFFF", "E8C9FF", 16, 2, 100, 88, 45),
        mk("Mint", "6EE7B7", "34D399", "22D3EE", "0C1F1C", "061210", "ECFDF5", "A7D7C5", 12, 2, 100, 92, 25),
        mk("Mono", "FFFFFF", "B3B3B3", "FFFFFF", "1C1C1C", "0E0E0E", "FFFFFF", "A0A0A0", 8, 2, 100, 95, 0));
}
