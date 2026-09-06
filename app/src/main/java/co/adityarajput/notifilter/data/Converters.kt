package co.adityarajput.notifilter.data

import androidx.room.TypeConverter
import co.adityarajput.notifilter.data.models.Action
import co.adityarajput.notifilter.data.models.Schedule
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json

    @TypeConverter
    fun fromAction(action: Action) = action.toString()

    @TypeConverter
    fun toAction(value: String) = Action.fromString(value)

    @TypeConverter
    fun fromDays(days: Set<Int>) = days.joinToString(",")

    @TypeConverter
    fun toDays(value: String) = value.split(",").map { it.toInt() }.toSet()

    @TypeConverter
    fun fromSchedule(schedule: Schedule): String =
        json.encodeToString(schedule)

    @TypeConverter
    fun toSchedule(value: String): Schedule =
        json.decodeFromString(value)
}
