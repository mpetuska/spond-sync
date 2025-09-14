package sportpress.data.calendar

import kotlinx.serialization.Serializable

typealias CalendarId = UInt

@Serializable data class Calendar(val id: CalendarId)
