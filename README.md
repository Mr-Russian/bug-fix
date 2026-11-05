# Heh3 Spotify HUD (Minecraft 26.3, Fabric)

A neon glass now-playing HUD and pop-out for Spotify. Log in **once** with a single key and it stays logged in.

## One-time setup (about 2 minutes)
1. Go to https://developer.spotify.com/dashboard, log in, press **Create app** (any name, tick *Web API*).
2. In the app settings add this **Redirect URI** and save: `http://127.0.0.1:8888/callback`
3. Copy the app's **Client ID** (the one key). In game press **F8** > **Connect** tab > paste it > **Connect**
   (or type `/spotify key <Client ID>` then `/spotify connect`).
4. A browser tab opens once. Log in and press Agree. Done: the mod keeps a refresh token in `config/heh3-spotify-auth.json`.

Spotify Premium is needed to *control* playback (skip, pause, volume, seek). The HUD itself works on free accounts.
On Windows you can also switch **Source** to *Windows* in the Connect tab: no key or login, controls any player (volume keys instead of a slider).

## Keys
F7 HUD on/off, F8 pop-out. Inside the pop-out: Space play, arrows skip/volume, M mute, S shuffle, R repeat.
Keypad 5 play/pause, keypad 6 next, keypad 4 previous (rebind in Controls).

## Customise (pop-out tabs)
- **Style**: border radius, border width, border opacity, glass opacity, glow, HUD size; colour editor with hex, RGB sliders and variants.
- **Layout**: show/hide art, artist, badge, progress, times, volume; compact mode; position, offsets, width.
- **Presets**: built-in looks, plus save/delete your own.
- **Connect**: key, login, redirect port, source.

Commands: `/spotify`, `hud`, `key`, `connect`, `disconnect`, `source account|windows`, `setup`, `play`, `next`, `prev`, `mute`, `vol up|down`.

Made by Heh3. Discord: https://discord.gg/Y53P35Dbjn (also the "Join the Discord" button in the pop-out).
