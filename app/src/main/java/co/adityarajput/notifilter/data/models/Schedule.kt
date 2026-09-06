package co.adityarajput.notifilter.data.models

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import co.adityarajput.notifilter.R
import kotlinx.serialization.Serializable
import java.time.ZonedDateTime
import java.util.Calendar

@Serializable
data class Schedule(
    val ranges: Map<Int, List<TimeRange>> =
        (1..7).associateWith { listOf(TimeRange(0, 1440)) },
) {
    val description
        @Composable get() = buildString {
            val activeDays = ranges.filterValues { it.isNotEmpty() }.keys

            when (activeDays) {
                setOf(1, 2, 3, 4, 5, 6, 7) -> append("")
                setOf(2, 3, 4, 5, 6) -> append(stringResource(R.string.on_weekdays))
                setOf(1, 7) -> append(stringResource(R.string.on_weekends))
                else -> append("")
            }
        }

    fun includesNow(calendar: Calendar = Calendar.getInstance()): Boolean {
        val day = calendar.get(Calendar.DAY_OF_WEEK)
        val minuteOfDay =
            calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        return ranges[day].orEmpty().any { range ->
            minuteOfDay >= range.start && minuteOfDay < range.end
        }
    }

    fun endOfActiveRange(now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        val day = now.dayOfWeek.value % 7 + 1
        val minuteOfDay = now.hour * 60 + now.minute

        val range = ranges[day].orEmpty()
            .firstOrNull { minuteOfDay >= it.start && minuteOfDay < it.end }
            ?: return null

        return if (range.end == 1440) {
            now.plusDays(1).toLocalDate().atStartOfDay(now.zone)
        } else {
            now
                .withHour(range.end / 60)
                .withMinute(range.end % 60)
                .withSecond(0)
                .withNano(0)
        }
    }

    fun isRangeValid() =
        ranges.values.flatten().all {
            it.start in 0..1440 &&
                    it.end in 0..1440 &&
                    it.start < it.end
        }
}
