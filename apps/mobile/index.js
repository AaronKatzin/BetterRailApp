// App entry point — runs before the Expo Router root component mounts.
//
// Configure Unistyles before any route module is evaluated. Importing it in
// `app/_layout` is too late — expo-router resolves routes first.
import "./src/theme/unistyles"

const defaultErrorHandler = global.ErrorUtils && global.ErrorUtils.getGlobalHandler()
let withinStartupWindow = true
setTimeout(() => {
  withinStartupWindow = false
}, 8000)

if (global.ErrorUtils) {
  global.ErrorUtils.setGlobalHandler((error, isFatal) => {
    try {
      // eslint-disable-next-line no-console
      console.error("[AaronRail] startup error", { isFatal, message: error && error.message }, error)
    } catch (_) {}

    if (isFatal && withinStartupWindow) return
    if (defaultErrorHandler) defaultErrorHandler(error, isFatal)
  })
}

require("expo-router/entry")
