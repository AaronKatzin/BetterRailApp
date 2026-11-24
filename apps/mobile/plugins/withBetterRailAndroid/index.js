/**
 * withBetterRailAndroid — the commute widget under CNG:
 *   - withAndroidNativeSources: copies the widget Kotlin and resources.
 *   - withAndroidGradle:        Hilt/Room/DataStore/OkHttp/Gson/coroutines deps,
 *                               kapt + Hilt plugins, buildConfig fields, flavor strategy.
 *   - withAndroidManifestMods:  widget receivers, update receiver, config activities.
 *   - withMainApplicationHilt:  @HiltAndroidApp + widget lifecycle management.
 *   - withMainActivityMods:     RNScreens fragment factory + deep-link onNewIntent.
 *
 * Permissions and the aaronrail:// deep link are declared in app.config.ts.
 */
const { withAndroidNativeSources } = require("./withAndroidNativeSources")
const { withAndroidGradle } = require("./withAndroidGradle")
const { withAndroidGradleProperties } = require("./withAndroidGradleProperties")
const { withAndroidManifestMods } = require("./withAndroidManifestMods")
const { withMainApplicationHilt } = require("./withMainApplicationHilt")
const { withMainActivityMods } = require("./withMainActivityMods")

const withBetterRailAndroid = (config) => {
  config = withAndroidGradle(config)
  config = withAndroidGradleProperties(config)
  config = withAndroidManifestMods(config)
  config = withMainApplicationHilt(config)
  config = withMainActivityMods(config)
  // Source/resource copy runs as a dangerous mod (after the generated project exists).
  config = withAndroidNativeSources(config)
  return config
}

module.exports = withBetterRailAndroid
