import * as storage from "@/utils/storage"
import { Appearance } from "react-native"
import { UnistylesRuntime } from "react-native-unistyles"
import { applyAccentTheme } from "@/theme/apply-accent-theme"
import { isAccentThemeSupported } from "@/utils/accent-theme-support"
import {
  useRoutePlanStore,
  getRoutePlanSnapshot,
  hydrateRoutePlanStore,
  resetRoutePlanStore,
} from "@/models/route-plan/route-plan"
import { getTrainRoutesSnapshot, hydrateTrainRoutesStore, resetTrainRoutesStore } from "@/models/train-routes/train-routes"
import {
  useRecentSearchesStore,
  getRecentSearchesSnapshot,
  hydrateRecentSearchesStore,
  resetRecentSearchesStore,
} from "@/models/recent-searches/recent-searches"
import {
  useSettingsStore,
  getSettingsSnapshot,
  hydrateSettingsStore,
  resetSettingsStore,
  type ColorSchemePreference,
} from "@/models/settings/settings"

const ROOT_STATE_STORAGE_KEY = "root"

function getSnapshot() {
  return {
    routePlan: getRoutePlanSnapshot(useRoutePlanStore.getState()),
    trainRoutes: getTrainRoutesSnapshot(),
    recentSearches: getRecentSearchesSnapshot(useRecentSearchesStore.getState()),
    settings: getSettingsSnapshot(useSettingsStore.getState()),
  }
}

export async function setupRootStore() {
  let data: any

  try {
    data = (await storage.load(ROOT_STATE_STORAGE_KEY)) || {}
  } catch (e) {
    data = {}
  }

  const hydrators = [
    () => hydrateRoutePlanStore(data.routePlan),
    () => hydrateTrainRoutesStore(data.trainRoutes),
    () => hydrateRecentSearchesStore(data.recentSearches),
    () => hydrateSettingsStore(data.settings),
  ]

  for (const hydrate of hydrators) {
    try {
      hydrate()
    } catch (e) {
      if (__DEV__) {
        console.warn("Store hydration failed:", e)
      }
    }
  }

  const applyColorScheme = (colorScheme: ColorSchemePreference) => {
    Appearance.setColorScheme(colorScheme === "automatic" ? "unspecified" : colorScheme)
    if (colorScheme === "automatic") {
      UnistylesRuntime.setAdaptiveThemes(true)
    } else {
      UnistylesRuntime.setAdaptiveThemes(false)
      UnistylesRuntime.setTheme(colorScheme)
    }
  }
  applyColorScheme(useSettingsStore.getState().colorScheme)

  const applyTheme = () => {
    const { accentTheme, useAlternateTimeFont } = useSettingsStore.getState()
    if (isAccentThemeSupported()) applyAccentTheme(accentTheme, useAlternateTimeFont)
    else applyAccentTheme("default")
  }
  applyTheme()

  await storage.save(ROOT_STATE_STORAGE_KEY, getSnapshot())

  const persist = () => storage.save(ROOT_STATE_STORAGE_KEY, getSnapshot())

  useRoutePlanStore.subscribe(persist)
  useRecentSearchesStore.subscribe(persist)
  useSettingsStore.subscribe((state, previousState) => {
    if (state.colorScheme !== previousState.colorScheme) applyColorScheme(state.colorScheme)
    if (state.accentTheme !== previousState.accentTheme || state.useAlternateTimeFont !== previousState.useAlternateTimeFont) {
      applyTheme()
    }
    persist()
  })
}

export function clearAllData() {
  resetRoutePlanStore()
  resetTrainRoutesStore()
  resetRecentSearchesStore()
  resetSettingsStore()
}
