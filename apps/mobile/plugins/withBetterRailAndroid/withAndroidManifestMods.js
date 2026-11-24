const { withAndroidManifest } = require("@expo/config-plugins")

/**
 * Adds the commute widget components to AndroidManifest: the AppWidget providers,
 * the alarm/boot update receiver, and the configuration activities.
 * Permissions and the aaronrail:// deep link are declared in app.config.ts.
 */
const APPWIDGET_UPDATE = "android.appwidget.action.APPWIDGET_UPDATE"

const widgetReceiver = (name, refresh, update, info) => ({
  $: { "android:name": name, "android:exported": "true" },
  "intent-filter": [
    {
      action: [
        { $: { "android:name": APPWIDGET_UPDATE } },
        { $: { "android:name": refresh } },
        { $: { "android:name": update } },
      ],
    },
  ],
  "meta-data": [{ $: { "android:name": "android.appwidget.provider", "android:resource": info } }],
})

const configActivity = (name) => ({
  $: {
    "android:name": name,
    "android:label": "@string/widget_configure_title",
    "android:exported": "false",
  },
  "intent-filter": [{ action: [{ $: { "android:name": "android.appwidget.action.APPWIDGET_CONFIGURE" } }] }],
})

const withAndroidManifestMods = (config) =>
  withAndroidManifest(config, (cfg) => {
    const manifest = cfg.modResults.manifest
    const app = manifest.application[0]

    app.activity = app.activity || []
    app.receiver = app.receiver || []

    app.activity.push(
      configActivity(".widget.CompactWidget2x2ConfigActivity"),
      configActivity(".widget.CompactWidget4x2ConfigActivity"),
      configActivity(".widget.CompactWidget4x3ConfigActivity"),
    )

    app.receiver.push(
      widgetReceiver(
        ".widget.ModernCompactWidget2x2Provider",
        "com.betterrail.widget.modern.compact.ACTION_REFRESH",
        "com.betterrail.widget.modern.compact.ACTION_WIDGET_UPDATE",
        "@xml/compact_widget_2x2_info",
      ),
      widgetReceiver(
        ".widget.ModernCompactWidget4x2Provider",
        "com.betterrail.widget.modern.compact4x2.ACTION_REFRESH",
        "com.betterrail.widget.modern.compact4x2.ACTION_WIDGET_UPDATE",
        "@xml/compact_widget_4x2_info",
      ),
      widgetReceiver(
        ".widget.ModernCompactWidget4x3Provider",
        "com.betterrail.widget.modern.compact4x3.ACTION_REFRESH",
        "com.betterrail.widget.modern.compact4x3.ACTION_WIDGET_UPDATE",
        "@xml/compact_widget_4x3_info",
      ),
      { $: { "android:name": ".widget.WidgetPinReceiver", "android:exported": "false" } },
      {
        $: { "android:name": ".widget.scheduler.WidgetUpdateReceiver", "android:exported": "false" },
        "intent-filter": [
          { action: [{ $: { "android:name": "com.betterrail.widget.UPDATE_ALL_WIDGETS" } }] },
          {
            action: [{ $: { "android:name": "android.intent.action.BOOT_COMPLETED" } }],
            category: [{ $: { "android:name": "android.intent.category.DEFAULT" } }],
          },
          {
            action: [{ $: { "android:name": "android.intent.action.MY_PACKAGE_REPLACED" } }],
            category: [{ $: { "android:name": "android.intent.category.DEFAULT" } }],
            data: [{ $: { "android:scheme": "package" } }],
          },
          {
            action: [{ $: { "android:name": "android.intent.action.PACKAGE_REPLACED" } }],
            category: [{ $: { "android:name": "android.intent.category.DEFAULT" } }],
            data: [{ $: { "android:scheme": "package" } }],
          },
        ],
      },
    )

    return cfg
  })

module.exports = { withAndroidManifestMods }
