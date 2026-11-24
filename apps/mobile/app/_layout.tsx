import "@/i18n"
import "@/utils/ignore-warnings"
import React, { useState, useEffect } from "react"
import { AppState, I18nManager, useColorScheme } from "react-native"
import { useDeepLinking } from "@/hooks/use-deep-linking"
import { Stack } from "expo-router/stack"
import { enableLegacyHeaderAppearance } from "expo-router"
import { ThemeProvider, DarkTheme, DefaultTheme } from "expo-router/react-navigation"
import { useUnistyles } from "react-native-unistyles"
import { QueryClient, QueryClientProvider, focusManager } from "react-query"
import { SafeAreaProvider, initialWindowMetrics } from "react-native-safe-area-context"
import { enableScreens } from "react-native-screens"
import { GestureHandlerRootView } from "react-native-gesture-handler"

import { initFonts } from "@/theme/fonts"
import * as storage from "@/utils/storage"
import { setupRootStore } from "@/models"
import {
  changeUserLanguage,
  getLanguageChangedInIOSSettings,
  isRTLLanguage,
  setInitialLanguage,
  setUserLanguage,
} from "@/i18n/i18n"

enableScreens()
enableLegacyHeaderAppearance()

export const queryClient = new QueryClient()

// React Native has no window focus events: treat returning to the app as focus,
// so live delays refetch right away instead of on the next interval.
focusManager.setEventListener((handleFocus) => {
  const subscription = AppState.addEventListener("change", (state) => handleFocus(state === "active"))
  return () => subscription.remove()
})

function AppStack() {
  const colorScheme = useColorScheme()
  const { theme } = useUnistyles()

  const baseNavigationTheme = colorScheme === "dark" ? DarkTheme : DefaultTheme
  const { background, text, separator } = theme.colors
  const navigationTheme =
    typeof background === "string" && typeof text === "string" && typeof separator === "string"
      ? {
          ...baseNavigationTheme,
          colors: { ...baseNavigationTheme.colors, background, card: background, text, border: separator },
        }
      : baseNavigationTheme

  return (
    <ThemeProvider value={navigationTheme}>
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="(main)" options={{ headerShown: false }} />
      </Stack>
    </ThemeProvider>
  )
}

function RootLayout() {
  const [storeReady, setStoreReady] = useState(false)
  const [localeReady, setLocaleReady] = useState(false)

  useDeepLinking(storeReady)

  useEffect(() => {
    ;(async () => {
      await initFonts()
      setupRootStore().then(() => setStoreReady(true))
    })()
  }, [])

  useEffect(() => {
    storage.load("appLanguage").then(async (storedLanguageCode) => {
      const iosSettingsLanguage = getLanguageChangedInIOSSettings()
      const languageCode = iosSettingsLanguage ?? storedLanguageCode

      if (iosSettingsLanguage && isRTLLanguage(iosSettingsLanguage) !== I18nManager.isRTL) {
        changeUserLanguage(iosSettingsLanguage)
      } else if (languageCode) {
        if (iosSettingsLanguage) await storage.save("appLanguage", iosSettingsLanguage)
        setUserLanguage(languageCode)
        setLocaleReady(true)
      } else {
        setInitialLanguage()
      }
    })
  }, [])

  if (!storeReady || !localeReady) return null

  return (
    <QueryClientProvider client={queryClient}>
      <GestureHandlerRootView style={{ flex: 1 }}>
        <SafeAreaProvider initialMetrics={initialWindowMetrics}>
          <AppStack />
        </SafeAreaProvider>
      </GestureHandlerRootView>
    </QueryClientProvider>
  )
}

export default RootLayout
