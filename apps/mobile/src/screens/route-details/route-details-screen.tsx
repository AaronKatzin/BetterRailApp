/* eslint-disable react-native/no-inline-styles */
import React, { useState } from "react"
import { View } from "react-native"
import { StyleSheet } from "react-native-unistyles"
import { ScrollView } from "react-native-gesture-handler"

import { useShallow } from "zustand/react/shallow"
import { spacing } from "@/theme"
import { RouteDetailsHeader, Screen } from "@/components"
import {
  LongRouteWarning,
  RouteChangeWarnings,
  RouteExchangeDetails,
  RouteLine,
  RouteStationCard,
  RouteStopCard,
} from "./components"

import { useQuery } from "react-query"
import type { RouteItem } from "@/services/api"
import { RouteApi } from "@/services/api/route-api"
import { useNavigationParamsStore } from "@/models/navigation-params/navigation-params"
import { useStations } from "@/data/stations"
import { calculateDelayedTime, formatClockTime, formatDateForAPI, formatTime } from "@/utils/helpers/date-helpers"
import { useSafeAreaInsets } from "react-native-safe-area-context"
import { publishFreshRoutes } from "@/screens/route-list/route-list-query"

const routeApi = new RouteApi()

function getSelectedRide(routes: RouteItem[], trainNumbers: number[]) {
  const key = trainNumbers.join()
  return routes.find((route) => route.trains.map((train) => train.trainNumber).join() === key)
}

export function RouteDetailsScreen({ screenName = "routeDetails" }: { screenName?: "routeDetails" }) {
  const {
    routeItem: paramsRouteItem,
    originId,
    destinationId,
  } = useNavigationParamsStore(
    useShallow((s) => ({ routeItem: s.routeItem, originId: s.originId, destinationId: s.destinationId })),
  )
  const viaStationId = paramsRouteItem.viaStationId
  const allStations = useStations()

  const trainNumbers = paramsRouteItem.trains.map((t) => t.trainNumber)

  // Periodically refetch route data to catch platform changes and delays.
  // Skip when ride is active — the ride polling already handles updates.
  const { data: freshRouteItem } = useQuery(
    ["routeDetails", originId, destinationId, viaStationId, paramsRouteItem.departureTime, ...trainNumbers],
    async () => {
      const [date, time] = formatDateForAPI(paramsRouteItem.departureTime)
      const fromId = paramsRouteItem.trains[0].originStationId.toString()
      const toId = paramsRouteItem.trains[paramsRouteItem.trains.length - 1].destinationStationId.toString()
      const routes = await routeApi.getRoutes(fromId, toId, date, time, { viaStation: viaStationId })
      // A change-station search isn't what the list shows
      if (!viaStationId && originId && destinationId) publishFreshRoutes(originId, destinationId, routes)
      const fresh = getSelectedRide(routes, trainNumbers)
      return fresh ? { ...fresh, viaStationId } : paramsRouteItem
    },
    {
      initialData: paramsRouteItem,
      refetchInterval: 60_000,
    },
  )

  const routeItem = freshRouteItem ?? paramsRouteItem
  const insets = useSafeAreaInsets()
  const [showEntireRoute, setShowEntireRoute] = useState(false)

  return (
    <Screen
      testID="route-details-screen"
      style={styles.root}
      preset="fixed"
      unsafe={true}
      keyboardAvoiding={false}
      statusBar="light-content"
    >
      <View style={{ flex: 1 }}>
        <RouteDetailsHeader
          routeItem={routeItem}
          originId={originId}
          destinationId={destinationId}
          screenName={screenName}
          showEntireRoute={showEntireRoute}
          setShowEntireRoute={setShowEntireRoute}
          style={styles.headerContainer}
        />

        <View style={{ flex: 1 }}>
          <ScrollView
            contentContainerStyle={{ paddingTop: spacing[4], paddingBottom: 80 + insets.bottom }}
            showsVerticalScrollIndicator={false}
          >
            {routeItem.isMuchLonger && screenName === "routeDetails" && <LongRouteWarning />}
            {routeItem.trains.map((train, index) => {
              // When showing the entire route, we need to organize all stations in order
              if (showEntireRoute && train.routeStations && train.routeStations.length > 0) {
                // Get all route stations
                const allRouteStations = train.routeStations
                // Find indices of origin and destination stations
                const originIndex = allRouteStations.findIndex((s) => s.stationId === train.originStationId)
                const destinationIndex = allRouteStations.findIndex((s) => s.stationId === train.destinationStationId)
                // Stations the user actually rides through, between origin and destination
                const betweenStations = allRouteStations.slice(originIndex + 1, destinationIndex)

                return (
                  <View key={train.trainNumber} style={styles.stationContainer}>
                    <RouteChangeWarnings train={train} />

                    {/* Stations before origin */}
                    {allRouteStations.slice(0, originIndex).map((station, idx) => {
                      const isFirstStation = idx === 0
                      return (
                        <View key={`before-${station.stationId}`}>
                          <RouteStopCard
                            stationName={allStations.find((c) => c.id === station.stationId.toString())?.name ?? ""}
                            stopTime={formatClockTime(station.arrivalTime)}
                            delayedTime={calculateDelayedTime(station.arrivalTime, train.delay)}
                            style={{ zIndex: 20 - idx, opacity: 0.7 }}
                            topLineState={isFirstStation ? "hidden" : "idle"}
                            bottomLineState="idle"
                            isOutsideUserJourney={true}
                            isCancelled={station.cancelled}
                          />
                        </View>
                      )
                    })}

                    {/* Origin station */}
                    <RouteStationCard
                      stationName={train.originStationName}
                      stopTime={formatTime(train.departureTime)}
                      platform={train.originPlatform}
                      platformChanged={train.originPlatformChanged}
                      trainNumber={train.trainNumber}
                      lastStop={train.lastStop}
                      lastStopChanged={train.isLastStopChanged}
                      delay={train.delay}
                    />

                    {/* Stations between origin and destination */}
                    {betweenStations.length > 0 ? (
                      betweenStations.map((station, idx) => (
                        <View key={`between-${station.stationId}`}>
                          <RouteStopCard
                            stationName={allStations.find((c) => c.id === station.stationId.toString())?.name ?? ""}
                            stopTime={formatClockTime(station.arrivalTime)}
                            delayedTime={calculateDelayedTime(station.arrivalTime, train.delay)}
                            style={{ zIndex: 20 - idx }}
                            topLineState="idle"
                            bottomLineState="idle"
                            isCancelled={station.cancelled}
                          />
                        </View>
                      )) // if there are no stops, display a separating line between the route station cards
                    ) : (
                      <RouteLine style={{ start: "35.44%", height: 30 }} state="idle" />
                    )}

                    {/* Destination station */}
                    <RouteStationCard
                      stationName={train.destinationStationName}
                      stopTime={formatTime(train.arrivalTime)}
                      delayedTime={calculateDelayedTime(train.arrivalTime, train.delay)}
                      platform={train.destinationPlatform}
                      platformChanged={train.destinationPlatformChanged}
                    />

                    {/* Stations after destination */}
                    {allRouteStations.slice(destinationIndex + 1).map((station, idx, arr) => {
                      const isLastStation = idx === arr.length - 1
                      return (
                        <View key={`after-${station.stationId}`}>
                          <RouteStopCard
                            stationName={allStations.find((c) => c.id === station.stationId.toString())?.name ?? ""}
                            stopTime={formatClockTime(station.arrivalTime)}
                            delayedTime={calculateDelayedTime(station.arrivalTime, train.delay)}
                            style={{ zIndex: 20 - idx, opacity: 0.7 }}
                            topLineState="idle"
                            bottomLineState={isLastStation ? "hidden" : "idle"}
                            isOutsideUserJourney={true}
                            isCancelled={station.cancelled}
                          />
                        </View>
                      )
                    })}

                    {routeItem.isExchange && routeItem.trains.length - 1 !== index && (
                      <RouteExchangeDetails
                        stationName={train.destinationStationName}
                        arrivalPlatform={train.destinationPlatform}
                        departurePlatform={routeItem.trains[index + 1].originPlatform}
                        firstTrain={train}
                        secondTrain={routeItem.trains[index + 1]}
                      />
                    )}
                  </View>
                )
              }

              // Original display logic when not showing entire route
              return (
                <View key={train.trainNumber} style={styles.stationContainer}>
                  <RouteChangeWarnings train={train} />

                  <RouteStationCard
                    stationName={train.originStationName}
                    stopTime={formatTime(train.departureTime)}
                    platform={train.originPlatform}
                    platformChanged={train.originPlatformChanged}
                    trainNumber={train.trainNumber}
                    lastStop={train.lastStop}
                    lastStopChanged={train.isLastStopChanged}
                    delay={train.delay}
                  />

                  {train.stopStations.length > 0
                    ? train.stopStations.map((stop, idx) => (
                        <View key={stop.stationId}>
                          <RouteStopCard
                            stationName={stop.stationName}
                            stopTime={formatTime(stop.departureTime)}
                            delayedTime={calculateDelayedTime(stop.departureTime, train.delay)}
                            style={{ zIndex: 20 - idx }}
                            topLineState="idle"
                            bottomLineState="idle"
                            isCancelled={stop.cancelled}
                          />
                        </View>
                      ))
                    : // if there are no stops, display a separating line between the route station cards
                      train.stopStations.length === 0 && (
                        <RouteLine
                          style={{ start: "35.44%", height: 30 }}
                          // TODO: The line state doesn't work properly
                          state="idle"
                        />
                      )}

                  <RouteStationCard
                    stationName={train.destinationStationName}
                    stopTime={formatTime(train.arrivalTime)}
                    delayedTime={calculateDelayedTime(train.arrivalTime, train.delay)}
                    platform={train.destinationPlatform}
                    platformChanged={train.destinationPlatformChanged}
                  />

                  {routeItem.isExchange && routeItem.trains.length - 1 !== index && (
                    <RouteExchangeDetails
                      stationName={train.destinationStationName}
                      arrivalPlatform={train.destinationPlatform}
                      departurePlatform={routeItem.trains[index + 1].originPlatform}
                      firstTrain={train}
                      secondTrain={routeItem.trains[index + 1]}
                    />
                  )}
                </View>
              )
            })}
          </ScrollView>
        </View>
      </View>
    </Screen>
  )
}

const styles = StyleSheet.create((theme) => ({
  root: {
    flex: 1,
    backgroundColor: theme.colors.background,
  },
  headerContainer: {
    paddingHorizontal: theme.spacing[3],
    marginBottom: theme.spacing[3],
  },
  stationContainer: {
    backgroundColor: theme.colors.background,
  },
}))
