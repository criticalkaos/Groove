# Groove

A jukebox-style music player for Android.

- **Jukebox view**: swipe through your albums as a 3D cover carousel, with the centred album's tracks listed below (or switch to a grid)
- Proper album pages with big cover art, year, running time and disc-by-disc tracklists
- Artists, Songs, Search, Recently played
- Playlists and Favourites
- Shuffle, repeat, gapless playback and an editable queue
- Lyrics (from `.lrc` files, embedded tags or lrclib.net), synced and scrolling where available
- Sleep timer, playback speed, equalizer and volume levelling
- Lock-screen/notification controls and a home-screen widget
- Plays MP3, FLAC, M4A/AAC, OGG/Opus and WAV from the folders you choose

## Install

Every push to `main` builds the app on GitHub. Open the
[latest release](../../releases/latest) on your phone, tap **Groove.apk**, then open the download
to install (allow "Install unknown apps" for your browser if asked). New versions install over the old one.

## Build yourself

Open in Android Studio, or run `gradle :app:assembleRelease` with JDK 17 and the Android SDK.
