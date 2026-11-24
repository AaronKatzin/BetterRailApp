import { useRef, useEffect } from "react"
import { Image, ImageBackground, View, Animated as RNAnimated, Pressable } from "react-native"
import type { ViewStyle } from "react-native"
import { StyleSheet } from "react-native-unistyles"
import { useRouter, useNavigation } from "expo-router"
import { useSafeAreaInsets } from "react-native-safe-area-context"
import LinearGradient from "react-native-linear-gradient"
import { spacing } from "@/theme"
import HapticFeedback from "react-native-haptic-feedback"
import { stationsObject, stationLocale } from "@/data/stations"
import { translate } from "@/i18n"
import { useShallow } from "zustand/react/shallow"
import { useRoutePlanStore } from "@/models"
import type { RouteItem } from "@/services/api"
import { GlassView } from "expo-glass-effect"
import { isLiquidGlassSupported, useGlassTint } from "@/utils/liquid-glass"
import { HeaderBackButton } from "@/components/header-back-button"
import { RouteStationNameButton } from "./route-station-name-button"
import { Text } from "@/components/text/text"

const arrowIcon = require("../../../assets/arrow-left.png")

export interface RouteDetailsHeaderProps {
  originId: string
  destinationId: string
  routeItem?: RouteItem
  screenName?: "routeList" | "routeDetails"
  style?: ViewStyle
  showEntireRoute?: boolean
  setShowEntireRoute?: React.Dispatch<React.SetStateAction<boolean>>
}

export function RouteDetailsHeader(props: RouteDetailsHeaderProps) {
  const glassTint = useGlassTint("secondary")
  const { originId, destinationId, screenName, style, showEntireRoute, setShowEntireRoute } = props
  const {
    origin: routePlanOrigin,
    destination: routePlanDestination,
    switchDirection,
  } = useRoutePlanStore(useShallow((s) => ({ origin: s.origin, destination: s.destination, switchDirection: s.switchDirection })))
  const router = useRouter()
  const navigation = useNavigation()
  const insets = useSafeAreaInsets()
  const routeEditDisabled = screenName !== "routeList"

  const stationCardScale = useRef(new RNAnimated.Value(1)).current

  const originStation = stationsObject[originId]
  const destinationStation = stationsObject[destinationId]
  const originName = originStation?.[stationLocale]
  const destinationName = destinationStation?.[stationLocale]

  const scaleStationCards = () => {
    RNAnimated.sequence([
      RNAnimated.timing(stationCardScale, {
        toValue: 0.94,
        duration: 175,
        useNativeDriver: true,
      }),
      RNAnimated.timing(stationCardScale, {
        toValue: 1,
        duration: 175,
        useNativeDriver: true,
      }),
    ]).start()
  }

  const swapDirection = () => {
    scaleStationCards()
    HapticFeedback.trigger("impactMedium")
    setTimeout(() => {
      switchDirection()
    }, 50)
  }

  const changeOriginStation = () => {
    router.push({ pathname: "/select-station", params: { selectionType: "origin" } })
  }

  const changeDestinationStation = () => {
    router.push({ pathname: "/select-station", params: { selectionType: "destination" } })
  }

  // Only the route list reads its stations from params — route details uses the params store.
  useEffect(() => {
    if (routeEditDisabled) return

    const nextOriginId = routePlanOrigin?.id
    const nextDestinationId = routePlanDestination?.id
    if (!nextOriginId || !nextDestinationId) return
    if (nextOriginId === originId && nextDestinationId === destinationId) return

    navigation.setParams({ originId: nextOriginId, destinationId: nextDestinationId } as never)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [routePlanOrigin?.id, routePlanDestination?.id, originId, destinationId, routeEditDisabled])

  return (
    <>
      <ImageBackground
        source={originStation?.image}
        style={{
          width: "100%",
          height: 200,
          zIndex: 0,
        }}
      >
        <LinearGradient style={styles.gradient} colors={["rgba(0, 0, 0, 0.75)", "rgba(0, 0, 0, 0.05)"]} />

        <View
          style={{
            position: "absolute",
            top: insets.top,
            left: 0,
            right: 0,
            flexDirection: "row",
            justifyContent: "space-between",
            alignItems: "center",
            paddingHorizontal: spacing[4],
            zIndex: 1000,
          }}
          accessibilityRole="header"
          accessibilityLabel={screenName === "routeDetails" ? translate("routes.routeDetails") : translate("plan.title")}
        >
          <HeaderBackButton />

          {screenName === "routeDetails" && setShowEntireRoute && (
            <Pressable onPress={() => setShowEntireRoute((prev) => !prev)} accessibilityRole="button" hitSlop={8}>
              <Text
                style={styles.allStations}
                tx={showEntireRoute ? "routeDetails.hideAllStations" : "routeDetails.showAllStations"}
              />
            </Pressable>
          )}
        </View>
      </ImageBackground>

      <View style={{ top: -20, marginBottom: -30, zIndex: 5 }}>
        <View style={[styles.routeDetailsWrapper, style]}>
          <RouteStationNameButton
            disabled={routeEditDisabled}
            onPress={changeOriginStation}
            buttonScale={stationCardScale}
            style={styles.routeDetailsStation}
            name={originName}
            shortName={originStation?.shortName?.[stationLocale]}
            accessibilityLabel={`${translate("plan.origin")}: ${originName}`}
            accessibilityHint={translate("plan.selectStation")}
          />

          <Pressable
            hitSlop={{
              top: spacing[2],
              bottom: spacing[2],
              left: spacing[2],
              right: spacing[2],
            }}
            onPress={swapDirection}
            style={styles.routeInfoCircleWrapper}
            disabled={routeEditDisabled}
            accessibilityLabel={translate("plan.switchStations")}
            accessibilityHint={translate("plan.switchStationsHint")}
          >
            <GlassView isInteractive={!routeEditDisabled} style={styles.routeInfoCircle} tintColor={glassTint}>
              <Image source={arrowIcon} style={styles.arrowIcon} />
            </GlassView>
          </Pressable>

          <RouteStationNameButton
            disabled={routeEditDisabled}
            onPress={changeDestinationStation}
            buttonScale={stationCardScale}
            style={styles.routeDetailsStation}
            name={destinationName}
            shortName={destinationStation?.shortName?.[stationLocale]}
            accessibilityLabel={`${translate("plan.destination")}: ${destinationName}`}
            accessibilityHint={translate("plan.selectStation")}
          />
        </View>
      </View>
    </>
  )
}

const styles = StyleSheet.create((theme, rt) => ({
  allStations: {
    color: "white",
    fontWeight: "600",
  },
  routeDetailsWrapper: {
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
    gap: theme.spacing[5],
  },
  routeDetailsStation: {
    flex: 1,
    padding: theme.spacing[2],
    backgroundColor: theme.colors.secondaryLighter,
    borderRadius: 25,
    shadowOffset: { width: 0, height: 1 },
    shadowColor: theme.colors.dim,
    shadowRadius: 1,
    shadowOpacity: rt.themeName === "dark" ? 0 : 0.45,
    elevation: 3,
    zIndex: 0,
  },
  routeInfoCircleWrapper: {
    position: "absolute",
    zIndex: 5,
  },
  routeInfoCircle: {
    width: 34,
    height: 34,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: isLiquidGlassSupported ? undefined : theme.colors.secondary,
    borderRadius: 25,
    elevation: 3,
  },
  arrowIcon: {
    width: 15,
    height: 15,
    tintColor: theme.colors.whiteText,
    transform: rt.rtl ? [] : [{ rotate: "180deg" }],
  },
  gradient: {
    height: "100%",
    position: "absolute",
    left: 0,
    right: 0,
    top: 0,
    opacity: 1,
  },
}))
