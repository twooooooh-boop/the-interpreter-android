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
All builds are signed with `keystore/debug.keystore`, so a new build installs over the old one.

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

## Fonts
UI: Inter · cipher text and keys: JetBrains Mono. Symbols a font lacks fall back to the system font.
