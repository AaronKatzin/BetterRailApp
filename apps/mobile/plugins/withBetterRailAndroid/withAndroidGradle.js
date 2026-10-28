const { withAppBuildGradle, withFinalizedMod, withProjectBuildGradle } = require("@expo/config-plugins")
const { mergeContents } = require("@expo/config-plugins/build/utils/generateCode")
const fs = require("node:fs")
const path = require("node:path")

// Install id for the sideload preview. Distinct from the namespace (com.betterrail)
// so it can sit beside the Play Store app without moving widget classes or R.
const PREVIEW_APPLICATION_ID = "com.aaronkatzin.rail"

// 2.58 reads Kotlin 2.2 metadata (RN 0.88). 2.59+ only passes its kapt args to AGP's legacy-kapt,
// not the kotlin-kapt this app applies, so Hilt codegen fails there.
const HILT_VERSION = "2.58"
const GOOGLE_SERVICES_VERSION = "4.4.2"

const APP_PLUGINS = `apply plugin: "kotlin-kapt"
apply plugin: "dagger.hilt.android.plugin"`

const DEFAULT_CONFIG = `        missingDimensionStrategy "store", "play"
        def railApiBaseUrl = "https://api.better-rail.co.il/api/v1/rail-api"
        buildConfigField "String", "RAIL_API_BASE_URL", "\\"\${railApiBaseUrl}\\""
        buildConfigField "String", "RAIL_API_TIMETABLE_URL", "\\"\${railApiBaseUrl}/rjpa/api/v1/timetable/\\""`

const DEPENDENCIES = `    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("androidx.work:work-runtime-ktx:2.8.1")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.dagger:hilt-android:${HILT_VERSION}")
    kapt("com.google.dagger:hilt-compiler:${HILT_VERSION}")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    kapt("androidx.room:room-compiler:2.7.0")
    implementation("com.google.android.gms:play-services-auth-api-phone:18.0.2")
    testImplementation("junit:junit:4.13.2")`

const withAppGradle = (config) =>
  withAppBuildGradle(config, (cfg) => {
    let src = cfg.modResults.contents

    // 1) Apply kapt + Hilt gradle plugins (after the existing apply plugin lines).
    src = mergeContents({
      tag: "better-rail-android-plugins",
      src,
      newSrc: APP_PLUGINS,
      anchor: /apply plugin: "com\.facebook\.react"/,
      offset: 1,
      comment: "//",
    }).contents

    // 1b) Apply the Google Services gradle plugin so google-services.json is processed and
    // FirebaseApp auto-inits — required for expo-notifications to obtain an FCM token on Android.
    // Replaces what the removed @react-native-firebase/app config plugin used to do. The
    // google-services.json itself is copied by Expo core from android.googleServicesFile.
    src = mergeContents({
      tag: "better-rail-android-google-services",
      src,
      // Preview google-services.json has no client for com.aaronkatzin.rail. Skip the
      // plugin so the timetable widget still builds; it does not use Firebase.
      newSrc:
        process.env.AARON_RAIL_PREVIEW === "1"
          ? `// AARON_RAIL_PREVIEW: google-services plugin skipped (no Firebase client for this package)`
          : `apply plugin: "com.google.gms.google-services"`,
      anchor: /apply plugin: "com\.facebook\.react"/,
      offset: 1,
      comment: "//",
    }).contents

    // 2) buildConfig fields + product flavor dimension strategy inside defaultConfig {}.
    src = mergeContents({
      tag: "better-rail-android-defaultconfig",
      src,
      newSrc: DEFAULT_CONFIG,
      anchor: /defaultConfig\s*{/,
      offset: 1,
      comment: "//",
    }).contents

    // 3) Ensure buildConfig build feature is enabled (required for buildConfigField).
    if (!/buildFeatures\s*{[^}]*buildConfig\s+true/s.test(src)) {
      src = mergeContents({
        tag: "better-rail-android-buildfeatures",
        src,
        newSrc: `    buildFeatures {\n        buildConfig true\n    }`,
        anchor: /android\s*{/,
        offset: 1,
        comment: "//",
      }).contents
    }

    // 4) Widget dependencies (Hilt, Room, DataStore, WorkManager, OkHttp, Gson, coroutines), plus the SMS User Consent API.
    src = mergeContents({
      tag: "better-rail-android-dependencies",
      src,
      newSrc: DEPENDENCIES,
      anchor: /dependencies\s*{/,
      offset: 1,
      comment: "//",
    }).contents

    cfg.modResults.contents = src
    return cfg
  })

const withProjectGradle = (config) =>
  withProjectBuildGradle(config, (cfg) => {
    let src = cfg.modResults.contents

    src = mergeContents({
      tag: "better-rail-android-hilt-classpath",
      src,
      newSrc: `        classpath("com.google.dagger:hilt-android-gradle-plugin:${HILT_VERSION}")`,
      anchor: /dependencies\s*{/,
      offset: 1,
      comment: "//",
    }).contents

    // Google Services gradle plugin classpath (buildscript) — pairs with the apply plugin in
    // the app build.gradle; required for expo-notifications FCM (replaces @react-native-firebase/app).
    src = mergeContents({
      tag: "better-rail-android-google-services-classpath",
      src,
      newSrc: `        classpath("com.google.gms:google-services:${GOOGLE_SERVICES_VERSION}")`,
      anchor: /dependencies\s*{/,
      offset: 1,
      comment: "//",
    }).contents

    // Gradle 9 ignores rootProject.allprojects{} from subproject build files, so
    // notifee's local AAR repo must be declared at the root level. Resolve the package
    // instead of assuming ../node_modules — in the monorepo it is hoisted to the repo root.
    const notifeeLibsFromAndroidRoot = path
      .relative(
        cfg.modRequest.platformProjectRoot,
        path.join(path.dirname(require.resolve("@notifee/react-native/package.json")), "android", "libs"),
      )
      .split(path.sep)
      .join("/")
    src = mergeContents({
      tag: "better-rail-android-notifee-maven",
      src,
      newSrc: `    maven { url "$rootDir/${notifeeLibsFromAndroidRoot}" }`,
      anchor: /maven \{ url 'https:\/\/www\.jitpack\.io' \}/,
      offset: 1,
      comment: "//",
    }).contents

    cfg.modResults.contents = src
    return cfg
  })

// Expo writes namespace and applicationId together from android.package, and dangerous
// mods run before that write. Finalized runs last: change only applicationId so the
// widget providers (relative .widget.* names) and com.betterrail.R keep resolving.
const withPreviewApplicationId = (config) => {
  if (process.env.AARON_RAIL_PREVIEW !== "1") return config
  return withFinalizedMod(config, [
    "android",
    (cfg) => {
      const gradlePath = path.join(cfg.modRequest.platformProjectRoot, "app", "build.gradle")
      const src = fs.readFileSync(gradlePath, "utf8")
      const next = src.replace(/applicationId\s+['"][^'"]+['"]/, `applicationId '${PREVIEW_APPLICATION_ID}'`)
      if (!next.includes(`applicationId '${PREVIEW_APPLICATION_ID}'`)) {
        throw new Error("AARON_RAIL_PREVIEW: failed to set applicationId in app/build.gradle")
      }
      if (!/namespace\s+['"]com\.betterrail['"]/.test(next)) {
        throw new Error("AARON_RAIL_PREVIEW: namespace must stay com.betterrail")
      }
      fs.writeFileSync(gradlePath, next)

      // expo-dev-client adds exp+<slug>, which the Play Store app also claims.
      const manifestPath = path.join(cfg.modRequest.platformProjectRoot, "app", "src", "main", "AndroidManifest.xml")
      const manifest = fs.readFileSync(manifestPath, "utf8")
      const stripped = manifest.replace(/\s*<data android:scheme="exp\+better-rail"\/>/g, "")
      if (stripped.includes('android:scheme="betterrail"') || stripped.includes("better-rail.co.il") || stripped.includes("exp+better-rail")) {
        throw new Error("AARON_RAIL_PREVIEW: manifest still claims a Better Rail scheme or App Link")
      }
      if (!stripped.includes('android:scheme="aaronrail"')) {
        throw new Error("AARON_RAIL_PREVIEW: manifest is missing the aaronrail scheme")
      }
      fs.writeFileSync(manifestPath, stripped)
      return cfg
    },
  ])
}

const withAndroidGradle = (config) => withPreviewApplicationId(withProjectGradle(withAppGradle(config)))

module.exports = { withAndroidGradle }
