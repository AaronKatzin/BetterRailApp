package com.betterrail.widget.database.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.betterrail.widget.data.WidgetTrainItem

/**
 * Type converter for storing list of WidgetTrainItem in Room database
 */
class TrainItemListConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromTrainItemList(trainItems: List<WidgetTrainItem>): String {
        return gson.toJson(trainItems)
    }

    @TypeConverter
    fun toTrainItemList(trainItemsString: String): List<WidgetTrainItem> {
        return try {
            val array = JsonParser.parseString(trainItemsString).asJsonArray
            array.map { element ->
                val obj = element.asJsonObject
                if (!obj.has("arrivalTimestamp") || obj.get("arrivalTimestamp").isJsonNull) {
                    obj.addProperty("arrivalTimestamp", "")
                }
                if (!obj.has("arrivalDelay") || obj.get("arrivalDelay").isJsonNull) {
                    obj.addProperty("arrivalDelay", 0)
                }
                gson.fromJson(obj, WidgetTrainItem::class.java)
            }
        } catch (_: Exception) {
            val type = object : TypeToken<List<WidgetTrainItem>>() {}.type
            gson.fromJson(trainItemsString, type) ?: emptyList()
        }
    }
}