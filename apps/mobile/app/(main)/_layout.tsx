import { Stack } from "expo-router/stack"
import { useUnistyles } from "react-native-unistyles"
import { typography } from "@/theme"

export default function MainLayout() {
  const { theme: appTheme } = useUnistyles()

  return (
    <Stack
      screenOptions={{
        headerBackButtonDisplayMode: "minimal",
        headerBackButtonMenuEnabled: false,
        headerTitleStyle: {
          fontSize: 20,
          fontFamily: typography.primary,
        },
        headerBackTitleStyle: { fontFamily: typography.primary },
        headerTintColor: appTheme.colors.primary as unknown as string,
        headerTitle: "",
      }}
    >
      <Stack.Screen name="index" options={{ headerShown: false }} />
      <Stack.Screen
        name="select-station"
        options={{
          headerShown: false,
          presentation: "fullScreenModal",
        }}
      />
      <Stack.Screen name="route-list" options={{ headerShown: false }} />
      <Stack.Screen name="route-details" options={{ headerShown: false }} />
    </Stack>
  )
}
