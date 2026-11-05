package dev.heh3spotify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Talks to the Windows media controls (the same ones the volume flyout and media keys use) through one long-running
 * PowerShell process. That works for the Spotify desktop app and for web players in a browser, and needs no login.
 */
final class Media {
    private Media() {}

    static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    // what the player is doing right now (written by the reader thread, read by the HUD and the pop-out)
    static volatile boolean ok;
    static volatile String title = "", artist = "", album = "", app = "", error = "";
    static volatile int status;          // 3 = playing, 4 = paused
    static volatile long pos, dur, stamp; // position and length in ms; stamp = when pos was read
    static volatile boolean shuffle;
    static volatile int repeat;          // 0 off, 1 song, 2 all
    static volatile int volume = -1;     // percent when the Spotify account is the source, -1 = unknown
    static volatile String device = "";
    static volatile String notice = "";  // short message from a command ("needs Premium" ...)
    static volatile long noticeUntil;
    static final int ART_N = 24;         // album art is kept as a 24 x 24 grid (fewer draw calls per frame)
    static volatile int[] artPx;
    private static int lastVolume = 50;

    private static Process proc;
    private static BufferedWriter out;
    private static long nextStart;
    private static int fails;

    static boolean playing() { return ok && status == 3; }

    /** Current position in ms, moving smoothly between the one-second updates. */
    static long position() {
        long p = pos;
        if (status == 3) p += System.currentTimeMillis() - stamp;
        if (dur > 0 && p > dur) p = dur;
        return Math.max(0, p);
    }

    /** Starts the background process if it is not running (safe to call every frame). */
    static synchronized void ensure() {
        if (SpotifyMod.api()) { Api.ensure(); return; }
        if (!WINDOWS) { error = "Windows only"; return; }
        if (proc != null && proc.isAlive()) return;
        if (System.currentTimeMillis() < nextStart) return;
        start();
    }

    private static void start() {
        try {
            Path f = FabricLoader.getInstance().getConfigDir().resolve("heh3-spotify-media.ps1");
            Files.writeString(f, SCRIPT, StandardCharsets.US_ASCII);
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NoLogo", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-File", f.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            proc = p;
            out = new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8));
            Thread t = new Thread(() -> read(p), "heh3-spotify");
            t.setDaemon(true);
            t.start();
        } catch (Exception e) {
            error = "could not start PowerShell: " + e.getMessage();
            fail();
        }
    }

    private static void read(Process p) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) apply(line);
        } catch (Exception ignored) { /* process ended */ }
        ok = false;
        synchronized (Media.class) {
            if (proc == p) { proc = null; fail(); }
        }
    }

    private static void fail() {
        fails++;
        nextStart = System.currentTimeMillis() + Math.min(30000L, 2000L * fails);
    }

    /** One status line from the script (JSON). Anything else it prints is kept as the error text. */
    static void apply(String line) {
        line = line.replace("﻿", "").trim();
        if (line.isEmpty()) return;
        if (line.charAt(0) != '{') { error = line; return; }
        try {
            JsonObject o = JsonParser.parseString(line).getAsJsonObject();
            fails = 0;
            if (!o.has("ok") || !o.get("ok").getAsBoolean()) {
                ok = false;
                error = o.has("err") ? o.get("err").getAsString() : "";
                return;
            }
            title = str(o, "title");
            artist = str(o, "artist");
            album = str(o, "album");
            app = str(o, "app");
            status = o.has("status") ? o.get("status").getAsInt() : 0;
            dur = o.has("dur") ? Math.max(0, o.get("dur").getAsLong()) : 0;
            pos = o.has("pos") ? Math.max(0, o.get("pos").getAsLong()) : 0;
            shuffle = o.has("shuffle") && o.get("shuffle").getAsBoolean();
            repeat = o.has("repeat") ? o.get("repeat").getAsInt() : 0;
            stamp = System.currentTimeMillis();
            error = "";
            ok = true;
        } catch (Exception e) {
            error = "unreadable status line";
        }
    }

    static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    // ---- commands ----
    static synchronized void send(String cmd) {
        ensure();
        try {
            if (out != null && proc != null && proc.isAlive()) { out.write(cmd + "\n"); out.flush(); }
        } catch (Exception ignored) {}
    }

    // ---- state set by the account (Web API) source ----
    static void setIdle(String why) {
        ok = false;
        error = why;
    }

    static void applyApi(String t, String ar, String al, int st, long p, long d, boolean sh, int rep, int vol, String dev) {
        title = t; artist = ar; album = al; status = st; pos = p; dur = d; shuffle = sh; repeat = rep; volume = vol; device = dev;
        stamp = System.currentTimeMillis();
        error = "";
        app = "spotify";
        ok = true;
    }

    static void notice(String s) {
        notice = s;
        noticeUntil = System.currentTimeMillis() + 5000;
    }

    static String activeNotice() { return System.currentTimeMillis() < noticeUntil ? notice : ""; }

    // ---- commands (routed to the account or to the Windows media controls) ----
    static void playPause() {
        boolean was = status == 3;
        long p = position();
        status = was ? 4 : 3;
        pos = p;
        stamp = System.currentTimeMillis();
        if (SpotifyMod.api()) Api.play(!was); else send("playpause");
    }

    static void next() { if (SpotifyMod.api()) Api.next(); else send("next"); }

    static void prev() { if (SpotifyMod.api()) Api.prev(); else send("prev"); }

    static void seekTo(long ms) {
        if (dur <= 0) return;
        ms = Math.max(0, Math.min(dur, ms));
        pos = ms;
        stamp = System.currentTimeMillis();
        if (SpotifyMod.api()) Api.seek(ms); else send("seek " + ms);
    }

    static void seekBy(long ms) { seekTo(position() + ms); }

    static void shuffle() {
        shuffle = !shuffle;
        if (SpotifyMod.api()) Api.shuffle(shuffle); else send("shuffle");
    }

    static void repeat() {
        repeat = repeat == 0 ? 2 : repeat == 2 ? 1 : 0;
        if (SpotifyMod.api()) Api.repeat(repeat == 2 ? "context" : repeat == 1 ? "track" : "off"); else send("repeat");
    }

    /** True when an exact volume can be set (account source and the device reports its volume). */
    static boolean canSetVolume() { return SpotifyMod.api() && volume >= 0; }

    static void setVolume(int pct) {
        pct = Math.max(0, Math.min(100, pct));
        volume = pct;
        if (pct > 0) lastVolume = pct;
        Api.volume(pct);
    }

    static void volume(boolean up) {
        if (canSetVolume()) setVolume(volume + (up ? 1 : -1) * SpotifyMod.cfg.volSteps * 2);
        else if (!SpotifyMod.api()) send((up ? "volup " : "voldown ") + SpotifyMod.cfg.volSteps);
    }

    static void mute() {
        if (canSetVolume()) setVolume(volume > 0 ? 0 : lastVolume);
        else if (!SpotifyMod.api()) send("mute");
    }

    /** Opens the Spotify desktop app (or whatever handles spotify: links). */
    static void openSpotify() {
        if (!WINDOWS) return;
        try { new ProcessBuilder("cmd", "/c", "start", "", "spotify:").start(); } catch (Exception ignored) {}
    }

    /** The PowerShell side. ASCII only (Windows PowerShell 5.1 reads a script without BOM as ANSI). */
    static final String SCRIPT = """
$ErrorActionPreference = 'SilentlyContinue'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$null = [Windows.Media.MediaPlaybackAutoRepeatMode, Windows.Media, ContentType = WindowsRuntime]
$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await($op, [Type]$type) {
  $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
  [void]$t.Wait(5000)
  return $t.Result
}
function Emit($h) {
  if ($h -is [array]) { $h = $h[-1] }
  [Console]::Out.WriteLine(($h | ConvertTo-Json -Compress))
  [Console]::Out.Flush()
}
$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
$wsh = New-Object -ComObject WScript.Shell

function Pick {
  foreach ($s in $mgr.GetSessions()) {
    if ($s.SourceAppUserModelId -match 'spotify') { return $s }
  }
  return $mgr.GetCurrentSession()
}

function Status {
  if ($null -eq $mgr) { return @{ ok = $false; err = 'media controls unavailable' } }
  $s = Pick
  if ($null -eq $s) { return @{ ok = $false; err = 'nothing playing' } }
  $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
  $pb = $s.GetPlaybackInfo()
  $tl = $s.GetTimelineProperties()
  $rep = 0
  if ($null -ne $pb.AutoRepeatMode) { $rep = [int]$pb.AutoRepeatMode }
  return @{
    ok = $true
    app = [string]$s.SourceAppUserModelId
    title = [string]$p.Title
    artist = [string]$p.Artist
    album = [string]$p.AlbumTitle
    status = [int]$pb.PlaybackStatus
    pos = [long]$tl.Position.TotalMilliseconds
    dur = [long]($tl.EndTime.TotalMilliseconds - $tl.StartTime.TotalMilliseconds)
    shuffle = [bool]$pb.IsShuffleActive
    repeat = $rep
  }
}

function Run($line) {
  $parts = $line.Trim().Split(' ')
  $s = Pick
  switch ($parts[0]) {
    'playpause' { if ($s) { [void](Await ($s.TryTogglePlayPauseAsync()) ([bool])) } }
    'next' { if ($s) { [void](Await ($s.TrySkipNextAsync()) ([bool])) } }
    'prev' { if ($s) { [void](Await ($s.TrySkipPreviousAsync()) ([bool])) } }
    'seek' { if ($s) { $ticks = [long]([double]$parts[1] * 10000); [void](Await ($s.TryChangePlaybackPositionAsync($ticks)) ([bool])) } }
    'shuffle' { if ($s) { $cur = [bool]$s.GetPlaybackInfo().IsShuffleActive; [void](Await ($s.TryChangeShuffleActiveAsync(-not $cur)) ([bool])) } }
    'repeat' {
      if ($s) {
        $cur = 0
        if ($null -ne $s.GetPlaybackInfo().AutoRepeatMode) { $cur = [int]$s.GetPlaybackInfo().AutoRepeatMode }
        $next = 0
        if ($cur -eq 0) { $next = 2 } elseif ($cur -eq 2) { $next = 1 }
        [void](Await ($s.TryChangeAutoRepeatModeAsync([Windows.Media.MediaPlaybackAutoRepeatMode]$next)) ([bool]))
      }
    }
    'volup' { for ($i = 0; $i -lt [int]$parts[1]; $i++) { $wsh.SendKeys([char]175) } }
    'voldown' { for ($i = 0; $i -lt [int]$parts[1]; $i++) { $wsh.SendKeys([char]174) } }
    'mute' { $wsh.SendKeys([char]173) }
  }
}

$reader = [Console]::In
$task = $reader.ReadLineAsync()
$last = [DateTime]::MinValue
while ($true) {
  if ($task.IsCompleted) {
    $line = $task.Result
    if ($null -eq $line) { break }
    try { Run $line } catch { }
    $task = $reader.ReadLineAsync()
    $last = [DateTime]::UtcNow.AddMilliseconds(-700)
    continue
  }
  if (([DateTime]::UtcNow - $last).TotalMilliseconds -ge 1000) {
    $last = [DateTime]::UtcNow
    try { Emit (Status) } catch { Emit @{ ok = $false; err = 'status failed' } }
  }
  Start-Sleep -Milliseconds 100
}
""";
}
