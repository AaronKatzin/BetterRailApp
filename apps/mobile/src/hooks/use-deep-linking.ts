import { useEffect, useRef } from "react"
import { EmitterSubscription, Linking } from "react-native"
import { router, useNavigationContainerRef } from "expo-router"
import type { NavigationState } from "expo-router/react-navigation"
import { extractURLParams } from "@/utils/helpers/url"
import { useRoutePlanStore } from "@/models/route-plan/route-plan"
import { getStationById } from "@/data/stations"

/**
 * Opens the route list when a commute widget is tapped.
 */
export function useDeepLinking(storeReady: boolean) {
  const pendingURL = useRef<string | null>(null)
  const navigationRef = useNavigationContainerRef()

  function dismissDeepLinkedScreens() {
    let parent = navigationRef.isReady() ? navigationRef.getRootState() : undefined
    while (parent && !parent.routes.some((route) => route.name === "(main)")) {
      parent = parent.routes[parent.index]?.state as NavigationState | undefined
    }
    if (!parent) return

    const mainIndex = parent.routes.findIndex((route) => route.name === "(main)")
    if (parent.index > mainIndex) {
      navigationRef.dispatch({ type: "POP", payload: { count: parent.index - mainIndex }, target: parent.key })
    }

    const mainRoutes = parent.routes[mainIndex].state?.routes ?? []
    const deepLinkIndex = mainRoutes.findIndex((route) => (route.params as { deepLink?: string })?.deepLink === "true")
    if (deepLinkIndex !== -1) router.dismiss(mainRoutes.length - deepLinkIndex)
  }

  function deepLinkWidgetURL(url: string) {
    if (!storeReady) return

    const { originId, destinationId } = extractURLParams(url)
    const routePlan = useRoutePlanStore.getState()

    routePlan.setOrigin(getStationById(originId))
    routePlan.setDestination(getStationById(destinationId))

    dismissDeepLinkedScreens()
    router.push({
      pathname: "/route-list",
      params: {
        originId,
        destinationId,
        time: String(new Date().getTime()),
        enableQuery: "true",
        deepLink: "true",
      },
    })
  }

  function handleDeepLinkURL(url: string | null) {
    if (!url) return
    if (!storeReady) {
      pendingURL.current = url
      return
    }
    if (url.toLowerCase().includes("widget")) {
      deepLinkWidgetURL(url)
    }
  }

  useEffect(() => {
    let linkingListener: EmitterSubscription
    let active = true
    const queuedURL = pendingURL.current
    let receivedURL = !!queuedURL

    if (storeReady && queuedURL) {
      pendingURL.current = null
      handleDeepLinkURL(queuedURL)
    }

    Linking.getInitialURL().then((url) => {
      if (active && !receivedURL) handleDeepLinkURL(url)
    })

    linkingListener = Linking.addEventListener("url", ({ url }) => {
      receivedURL = true
      handleDeepLinkURL(url)
    })

    return () => {
      active = false
      linkingListener?.remove()
    }
  }, [storeReady])
}
