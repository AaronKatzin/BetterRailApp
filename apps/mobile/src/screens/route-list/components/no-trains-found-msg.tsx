import React from "react"
import { Image, ScrollView } from "react-native"
import { StyleSheet } from "react-native-unistyles"
import { Text } from "@/components"

export function NoTrainsFoundMessage() {
  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Image style={styles.searchIcon} source={require("../../../../assets/search.png")} />
      <Text tx="routes.noTrainsFound" style={styles.noTrainsFoundText} />
    </ScrollView>
  )
}

const styles = StyleSheet.create((theme) => ({
  container: {
    alignItems: "center",
    paddingBottom: 250,
    paddingHorizontal: theme.spacing[4],
  },
  searchIcon: {
    width: 57.5,
    height: 57.5,
    marginBottom: theme.spacing[2],
    opacity: 0.7,
    tintColor: theme.colors.text,
  },
  noTrainsFoundText: {
    marginBottom: theme.spacing[2],
    textAlign: "center",
  },
}))
