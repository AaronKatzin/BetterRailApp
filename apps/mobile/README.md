# Aaron Rail (mobile)

Android shell for the commute widget. See the [repository README](../../README.md).

Native widget code is in `android-native/` and copied into the Expo prebuild by `plugins/withBetterRailAndroid`. `android/` is generated and gitignored.

Preview installs:

```bash
AARON_RAIL_PREVIEW=1 bunx expo prebuild --platform android --no-install
```

`android.package` stays `com.betterrail` (the widget classes' namespace). The preview application id `com.aaronkatzin.rail` is applied by the Gradle config plugin.
