package dev.heh3spotify;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Heh3 Spotify HUD: a now-playing HUD plus a pop-out player. Client side only. */
public class SpotifyMod implements ClientModInitializer {
    /** Every colour and shape setting of the HUD and pop-out. Presets are copies of this. */
    public static class Look {
        public String accent = "3DE0FF", accent2 = "8B5CFF", accent3 = "FF4DB8", bg = "14102B", bg2 = "070B1C", text = "FFFFFF", sub = "B8C4DA";
        public int radius = 12;   // corner roundness, GUI pixels
        public int borderW = 2;   // border thickness in half pixels (2 = 1 px)
        public int borderA = 100; // border opacity, percent
        public int glass = 90;    // background opacity, percent
        public int glow = 35;     // soft glow around the HUD, percent

        Look copy() {
            Look l = new Look();
            l.accent = accent; l.accent2 = accent2; l.accent3 = accent3; l.bg = bg; l.bg2 = bg2; l.text = text; l.sub = sub;
            l.radius = radius; l.borderW = borderW; l.borderA = borderA; l.glass = glass; l.glow = glow;
            return l;
        }
    }

    public static class NamedLook {
        public String name = "";
        public Look look = new Look();
        NamedLook() {}
        NamedLook(String n, Look l) { name = n; look = l; }
    }

    /** Saved to config/heh3-spotify.json. Everything is editable in the pop-out (Style, Presets and Connect tabs). */
    public static class Config {
        public boolean hud = true, showArt = true, showArtist = true, showProgress = true, showTimes = true, showVolume = true,
            compact = false, hideWhenPaused = false, hideWhenIdle = true, showBadge = true;
        public String source = Media.WINDOWS ? "windows" : "api"; // "api" = your Spotify account, "windows" = Windows media controls (no login)
        public String clientId = "";   // the one key you paste (Spotify developer app Client ID)
        public int port = 8888;        // login redirect port: http://127.0.0.1:<port>/callback
        public int width = 240;
        public int scale = 100;        // HUD size, percent
        public int offX = 8, offY = 8;
        public int volSteps = 5;       // Windows mode: volume key presses per click (each is about 2 percent)
        public String anchor = "tr";   // tl tc tr ml mr bl bc br
        public Look look = new Look();
        public List<NamedLook> presets = new ArrayList<>();
    }

    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static Config cfg = new Config();
    static Path file;
    static boolean wantOpen;

    @Override public void onInitializeClient() {
        file = FabricLoader.getInstance().getConfigDir().resolve("heh3-spotify.json");
        try { if (Files.exists(file)) cfg = GSON.fromJson(Files.readString(file), Config.class); } catch (Exception ignored) {}
        if (cfg == null) cfg = new Config();
        fix();

        KeyMapping hudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.heh3spotify.hud", InputConstants.KEY_F7, KeyMapping.Category.MISC));
        KeyMapping openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.heh3spotify.open", InputConstants.KEY_F8, KeyMapping.Category.MISC));
        // keypad 5 / 6 / 4 by default (never -1: an "unbound" code can break the key scan on 26.3)
        KeyMapping ppKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.heh3spotify.playpause", 325, KeyMapping.Category.MISC));
        KeyMapping nextKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.heh3spotify.next", 326, KeyMapping.Category.MISC));
        KeyMapping prevKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.heh3spotify.prev", 324, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(c -> { try {
            while (hudKey.consumeClick()) { cfg.hud = !cfg.hud; save(); say("HUD " + (cfg.hud ? "on" : "off")); }
            while (openKey.consumeClick()) wantOpen = true;
            while (ppKey.consumeClick()) Media.playPause();
            while (nextKey.consumeClick()) Media.next();
            while (prevKey.consumeClick()) Media.prev();
            if (wantOpen && c.gui.screen() == null) { wantOpen = false; c.gui.setScreen(new PlayerScreen()); }
        } catch (Throwable t) { fail("tick", t); } });

        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("heh3spotify", "hud"), (g, tick) -> Hud.draw(g));

        ClientCommandRegistrationCallback.EVENT.register((d, reg) -> {
            var root = ClientCommands.literal("spotify");
            root.executes(c -> { wantOpen = true; return 1; });
            root.then(ClientCommands.literal("hud").executes(c -> { cfg.hud = !cfg.hud; save(); say("HUD " + (cfg.hud ? "on" : "off")); return 1; })
                .then(ClientCommands.argument("state", StringArgumentType.word()).executes(c -> {
                    cfg.hud = !StringArgumentType.getString(c, "state").equalsIgnoreCase("off"); save(); say("HUD " + (cfg.hud ? "on" : "off")); return 1; })));
            root.then(ClientCommands.literal("key").then(ClientCommands.argument("clientId", StringArgumentType.greedyString()).executes(c -> {
                cfg.clientId = StringArgumentType.getString(c, "clientId").trim();
                cfg.source = "api"; save();
                say("Saved your key. Now run /spotify connect (a browser tab opens once to log in).");
                return 1; })));
            root.then(ClientCommands.literal("connect").executes(c -> { Api.connect(); return 1; }));
            root.then(ClientCommands.literal("disconnect").executes(c -> { Api.disconnect(); return 1; }));
            root.then(ClientCommands.literal("source")
                .then(ClientCommands.literal("account").executes(c -> { cfg.source = "api"; save(); say("Using your Spotify account."); return 1; }))
                .then(ClientCommands.literal("windows").executes(c -> { if (Media.WINDOWS) { cfg.source = "windows"; save(); say("Using the Windows media controls."); } return 1; })));
            root.then(ClientCommands.literal("setup").executes(c -> { Api.setupHelp(); return 1; }));
            root.then(ClientCommands.literal("play").executes(c -> { Media.playPause(); return 1; }));
            root.then(ClientCommands.literal("next").executes(c -> { Media.next(); return 1; }));
            root.then(ClientCommands.literal("prev").executes(c -> { Media.prev(); return 1; }));
            root.then(ClientCommands.literal("mute").executes(c -> { Media.mute(); return 1; }));
            root.then(ClientCommands.literal("vol")
                .then(ClientCommands.literal("up").executes(c -> { Media.volume(true); return 1; }))
                .then(ClientCommands.literal("down").executes(c -> { Media.volume(false); return 1; })));
            d.register(root);
        });
    }

    /** Keeps a hand-edited config file inside sane limits. */
    static void fix() {
        cfg.width = Math.max(140, Math.min(360, cfg.width));
        cfg.scale = Math.max(60, Math.min(200, cfg.scale));
        cfg.offX = Math.max(0, Math.min(400, cfg.offX));
        cfg.offY = Math.max(0, Math.min(400, cfg.offY));
        cfg.volSteps = Math.max(1, Math.min(25, cfg.volSteps));
        cfg.port = cfg.port < 1024 || cfg.port > 65535 ? 8888 : cfg.port;
        if (cfg.clientId == null) cfg.clientId = "";
        cfg.clientId = cfg.clientId.trim();
        if (cfg.source == null || !(cfg.source.equals("api") || cfg.source.equals("windows"))) cfg.source = Media.WINDOWS ? "windows" : "api";
        if (!Media.WINDOWS) cfg.source = "api";
        if (cfg.anchor == null || !Draw.ANCHORS.contains(cfg.anchor)) cfg.anchor = "tr";
        if (cfg.presets == null) cfg.presets = new ArrayList<>();
        cfg.presets.removeIf(p -> p == null || p.look == null);
        fixLook(cfg.look == null ? (cfg.look = new Look()) : cfg.look);
        for (NamedLook p : cfg.presets) fixLook(p.look);
    }

    static void fixLook(Look l) {
        Look d = new Look();
        if (!Draw.validHex(l.accent)) l.accent = d.accent;
        if (!Draw.validHex(l.accent2)) l.accent2 = d.accent2;
        if (!Draw.validHex(l.accent3)) l.accent3 = d.accent3;
        if (!Draw.validHex(l.bg)) l.bg = d.bg;
        if (!Draw.validHex(l.bg2)) l.bg2 = d.bg2;
        if (!Draw.validHex(l.text)) l.text = d.text;
        if (!Draw.validHex(l.sub)) l.sub = d.sub;
        l.radius = Math.max(0, Math.min(20, l.radius));
        l.borderW = Math.max(0, Math.min(8, l.borderW));
        l.borderA = Math.max(0, Math.min(100, l.borderA));
        l.glass = Math.max(10, Math.min(100, l.glass));
        l.glow = Math.max(0, Math.min(100, l.glow));
    }

    /** True when the Spotify account (Web API) is the active source. */
    static boolean api() { return cfg.source.equals("api"); }

    static void save() {
        try { Files.writeString(file, GSON.toJson(cfg)); } catch (IOException ignored) {}
    }

    /** Writes the stack trace next to the config and turns the HUD off, so a bug never takes the game down. */
    static void fail(String where, Throwable t) {
        try {
            cfg.hud = false;
            java.io.StringWriter sw = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(sw));
            Files.writeString(file.resolveSibling("heh3-spotify-error.txt"), "Error in " + where + "\n" + sw);
            say("Something went wrong (" + where + "), so the HUD was turned off. Details: config/heh3-spotify-error.txt");
        } catch (Throwable ignored) {}
    }

    static void say(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[Spotify] " + s));
    }
}
