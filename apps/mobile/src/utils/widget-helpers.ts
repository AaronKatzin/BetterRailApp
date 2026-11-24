import { extractURLParams } from "@/utils/helpers/url"

const ANDROID_WIDGET_TYPE_FAMILIES: Record<string, string> = {
  modern_widget2x2: "compact",
  modern_widget4x2: "wide",
  modern_widget4x3: "large",
}

export function getWidgetFamilyFromURL(url: string): string | undefined {
  const { family } = extractURLParams(url)
  if (family) return family
  const host = url.match(/^(?:betterrail|aaronrail):\/\/(\w+)/i)?.[1].toLowerCase()
  return host ? ANDROID_WIDGET_TYPE_FAMILIES[host] : undefined
}
