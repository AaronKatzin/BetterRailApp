// Defer widget navigation to useDeepLinking until app state loads.
const MANUAL_DEEP_LINK = /^(?:betterrail|aaronrail):\/\/(?:modern_)?widget\w*(?:[/?#]|$)/i

export function redirectSystemPath({ path }: { path: string; initial: boolean }): string | null {
  try {
    if (MANUAL_DEEP_LINK.test(path.toLowerCase())) {
      return null
    }
  } catch {
    // Never throw from here — it can crash the app on launch.
  }

  return path || null
}
