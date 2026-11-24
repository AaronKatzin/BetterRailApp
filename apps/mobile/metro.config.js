const fs = require("fs")
const path = require("path")
const { getDefaultConfig } = require("expo/metro-config")

/** @type {import('expo/metro-config').MetroConfig} */
const config = getDefaultConfig(__dirname)

// Licensed fonts are not in this fork. Substitute Heebo so a leftover require still bundles.
const licensedFonts = path.join(__dirname, "assets/fonts/licensed")
const licensedFontFallback = path.join(__dirname, "assets/fonts/Heebo-Bold.otf")
const resolveRequest = config.resolver.resolveRequest

config.resolver.resolveRequest = (context, moduleName, platform) => {
  const resolve = resolveRequest ?? context.resolveRequest
  if (moduleName.startsWith(".") && /\.(ttf|otf)$/.test(moduleName)) {
    const target = path.resolve(path.dirname(context.originModulePath), moduleName)
    if (target.startsWith(licensedFonts) && !fs.existsSync(target)) {
      return resolve(context, licensedFontFallback, platform)
    }
  }
  return resolve(context, moduleName, platform)
}

module.exports = config
