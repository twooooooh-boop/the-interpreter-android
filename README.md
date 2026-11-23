# The Interpreter (Android)

LÖVE 11.5 app, packaged as an Android project and built by GitHub Actions.

- GitHub user: `twooooooh-boop`
- Repo name: **`the-interpreter-android`** → https://github.com/twooooooh-boop/the-interpreter-android
- Package id: `com.twooooooh.interpreter`

All of these names live in `project.env`.

## Get the APK from GitHub
1. Create an **empty** repo `the-interpreter-android` on GitHub (no README/licence).
2. Unzip this project, then in the folder run `bash scripts/push-to-github.sh`
   (or: `git init -b main && git add -A && git commit -m init && git remote add origin https://github.com/twooooooh-boop/the-interpreter-android.git && git push -u origin main`).
3. Open the repo → **Actions** → *Build Android APK* (first build ≈ 15–25 min).
4. Open the finished run → **Artifacts** → `the-interpreter-android-apk` → unzip → install the APK.
   Push a tag like `v1.0.1` and the APK is also attached under **Releases**.

Every build uses the same signing key (`android/debug.keystore`), so new builds install over old ones.

## Android Studio
`bash scripts/prepare-android-project.sh` creates `android-project/` → *File > Open* it
(or `cd android-project && ./gradlew assembleEmbedNoRecordDebug`).
Needs JDK 17, Android SDK 34 and NDK 25.2.9519653.

## Test on desktop
`love game/` (LÖVE 11.5)

## Layout
- `game/` Lua source (main.lua, modules/, fonts/). `modules/keymap.lua` + `modules/settings.lua` are the new Settings tab.
- `android/` icon + debug keystore · `scripts/` project generator · `.github/workflows/build.yml`
- `archive/` your old `8.love`, `safe.love`, `todos.md` (not packaged)

## Settings tab
Pick a language → tap a letter → type a key → **ADD**. A letter can have up to 8 keys (max 6 characters each).
All keys of a letter decode; the **PRIMARY** (first, green) key is used when encoding.
Adding a key that another letter owns moves it. Edits are saved per language; **RESET LANG** restores defaults.
Keys of several characters are matched longest-first when decoding.

## Changes to your original code (main.lua)
- 3rd bottom tab **SETTINGS**; maps now come from `Keymap` (cached instead of rebuilt every frame).
- `decode` does longest-key-first matching (needed for multi-character keys, e.g. Blocky).
- `encodeNormal` falls back to the other letter case (Ridmik defines symbols once per letter pair).
- Debug text ("TEXT:/IND:") only shows on the Interpreter tab.
- Orientation set to portrait (your old APK was landscape); change `ORIENTATION` in `project.env`.
