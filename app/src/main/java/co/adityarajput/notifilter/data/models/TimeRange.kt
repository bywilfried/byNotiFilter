package co.adityarajput.notifilter.data.models

import kotlinx.serialization.Serializable

@Serializable
data class TimeRange(
    val start: Int,
    val end: Int,
)
