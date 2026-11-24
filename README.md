# Aaron Rail

Personal Android fork of [Better Rail](https://github.com/better-rail/app) (guytepper/better-rail), cut down to the commute home-screen widget.

The widget is native Kotlin. It shows the next train for a two-office weekday schedule, with a manual office toggle, morning/evening reversal at 13:00, per-leg scooter minutes, leave-by times, ETA, and live delays. Tapping it opens this app on that route. Station search and the timetable request live in the widget; the app is the shell that installs, launches, and handles the deep link.

Sideload builds use `AARON_RAIL_PREVIEW=1` (`com.aaronkatzin.rail`, label "Aaron Rail") so the app can sit next to Play Store Better Rail. See `.github/workflows/aaron-rail-apk.yml`.

## Layout

| Path                | What                                                           |
| ------------------- | -------------------------------------------------------------- |
| `apps/mobile`       | Expo app shell plus the Android widget under `android-native/` |
| `packages/stations` | Station list and photos used by the app                        |

The widget calls the existing Better Rail rail API (`https://api.better-rail.co.il`). This repo does not ship that server.

## Setup

- [Bun](https://bun.com/docs/installation)
- Android SDK (compile SDK 37, build-tools 37, NDK 27.1.12297006), same as the preview workflow

```bash
bun install
cd apps/mobile
AARON_RAIL_PREVIEW=1 bunx expo prebuild --platform android --no-install
```

Then assemble from `apps/mobile/android` with `./gradlew :app:assembleRelease`.

## License

Source code is [AGPL-3.0](./LICENSE), © the Better Rail authors. UI, branding, images, and fonts stay outside that license, as in the upstream project.
