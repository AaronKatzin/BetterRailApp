import { describe, expect, test } from "bun:test"
import { getWidgetFamilyFromURL } from "./widget-helpers"

describe("getWidgetFamilyFromURL", () => {
  test("reads the family param", () => {
    expect(getWidgetFamilyFromURL("widget://route?originId=4600&destinationId=680&family=small")).toBe("small")
  })

  test("maps the Android widget type", () => {
    expect(getWidgetFamilyFromURL("betterrail://modern_widget2x2?originId=4600&destinationId=680")).toBe("compact")
    expect(getWidgetFamilyFromURL("betterrail://modern_widget4x2?originId=4600&destinationId=680")).toBe("wide")
    expect(getWidgetFamilyFromURL("betterrail://modern_widget4x3?originId=4600&destinationId=680")).toBe("large")
    expect(getWidgetFamilyFromURL("aaronrail://modern_widget2x2?originId=4600&destinationId=680")).toBe("compact")
  })

  test("is undefined for links that don't send a family", () => {
    expect(getWidgetFamilyFromURL("widget://route?originId=4600&destinationId=680")).toBeUndefined()
  })
})
