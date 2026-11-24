# The Interpreter

Native Android app (Kotlin + Jetpack Compose) for the Demon / Ridmik / Blocky ciphers.

- GitHub: `twooooooh-boop/the-interpreter-android`
- Package id: `com.twooooooh.interpreter`
- minSdk 24 · compile/target SDK 35 · AGP 8.7.3 · Kotlin 2.0.21 · Gradle 8.9 · JDK 17

## Open in Android Studio
*File ▸ Open* this folder (Android Studio Ladybug or newer). Run the `app` configuration.

## Build on GitHub
Every push to `main` runs **Actions ▸ Build Android APK** and publishes a release
`build-N` with `TheInterpreter-build-N.apk` (source zip / tar.gz are added by GitHub).
Builds are **release** builds (minified, signed). The signing key is persistent, so every new build
installs over the previous one.

### Signing key
`keystore/release.jks` + `keystore/release.properties` are used by default.
**Back both up** (losing the key means users must uninstall to update).
If the repo is public, move the key into GitHub secrets instead: *Settings ▸ Secrets and variables ▸ Actions*,
add `KEYSTORE_BASE64` (`base64 -w0 keystore/release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
(same values as in `release.properties`), then delete the two keystore files from the repo.
The workflow uses the secrets automatically when `KEYSTORE_BASE64` exists.

## Notifications
Every build sends a push notification through ntfy (subscribe to topic `interpreter-eaf0904f7910` in the ntfy app):
success opens the Releases page, failure shows the first error lines and opens the failed run.

## Project layout
```
app/src/main/java/com/twooooooh/interpreter/
  domain/      pure Kotlin, no Android: maps, key rules, decode/encode  (unit tested)
  data/        key map storage (keymap.dat), saved sessions, preferences
  ui/          Compose: theme (fonts/colors), Interpreter, Saves, Settings
app/src/main/res/font/   Inter + JetBrains Mono (SIL OFL, see licenses/)
app/src/test/            parity tests against vectors recorded from the original Lua app
legacy-love/             the previous LÖVE version, kept for reference
```

## What is guaranteed unchanged
`domain/BuiltinMaps.kt` was generated from the Lua `eldaMaps` (same symbols, same order, same
"last duplicate wins" behaviour). `domain/Interpreter.kt` is the same decode / encode logic.
`LuaParityTest` replays ~900 conversions recorded from the Lua code and requires identical output.

## Settings tab
Pick a language, tap a letter, type a key, **Add**. A letter can have up to 8 keys (max 6
characters each). All keys decode; the first (green, **PRIMARY**) is used when encoding.
A key already used by another letter moves to the new one. **Reset** restores a language's built-in keys.

## Export / import
Settings ▸ **Export JSON** lets you tick which maps (Demon / Ridmik / Blocky) go into the file;
**Import JSON** reads a file, shows which maps it contains and applies only the ones you tick
(they replace that language's current keys). Format: `{"format":"the-interpreter-keymaps","version":1,"maps":{"demon":{"a":["Δ"],…}}}`
— first key of a letter = primary.

## Where sessions are saved
Not in app data any more: the first **Save session** asks you to pick a folder
(Android's folder picker). Sessions go into `The Interpreter/` inside it; **Saves ▸ Change folder** switches it.

## Fonts
UI: Inter · cipher text and keys: JetBrains Mono. Symbols a font lacks fall back to the system font.
