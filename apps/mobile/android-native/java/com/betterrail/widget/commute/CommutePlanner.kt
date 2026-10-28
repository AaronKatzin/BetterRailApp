package com.betterrail.widget.commute

import com.betterrail.widget.data.WidgetData
import com.betterrail.widget.data.WidgetTrainItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Pure commute rules for the home-screen widget.
 *
 * Morning (01:00 until 13:00) rides home station to the office station.
 * Evening (13:00 until 01:00) rides back. Which office is selected from the
 * weekday map unless the user has a same-day manual override.
 *
 * Leave-by is the delayed departure minus the first scooter leg.
 * ETA is the delayed arrival plus the last scooter leg.
 */
enum class CommuteOffice { A, B }

enum class CommuteDirection { TO_OFFICE, TO_HOME }

data class ScooterMinutes(
    val homeToStation: Int = 0,
    val stationToHome: Int = 0,
    val officeAStationToOffice: Int = 0,
    val officeAToStation: Int = 0,
    val officeBStationToOffice: Int = 0,
    val officeBToStation: Int = 0,
)

data class CommuteSettings(
    val homeStationId: String,
    val officeAStationId: String,
    val officeBStationId: String,
    val scooter: ScooterMinutes = ScooterMinutes(),
    /** Monday-first, seven A/B flags. Missing or other characters mean office A. */
    val weekdayOffices: String = "AAAAAAA",
    val officeOverride: CommuteOffice? = null,
    val officeOverrideDate: LocalDate? = null,
    /** Flips the morning/evening direction until the next window, matching the existing auto-reverse override. */
    val directionOverrideActive: Boolean = false,
)

data class ResolvedCommute(
    val office: CommuteOffice,
    val direction: CommuteDirection,
    val originStationId: String,
    val destinationStationId: String,
    val firstScooterMinutes: Int,
    val lastScooterMinutes: Int,
)

data class TrainClock(
    val scheduledDeparture: LocalDateTime,
    val scheduledArrival: LocalDateTime,
    val departureDelayMinutes: Int = 0,
    val arrivalDelayMinutes: Int = 0,
) {
    val actualDeparture: LocalDateTime
        get() = scheduledDeparture.plusMinutes(departureDelayMinutes.toLong())

    val actualArrival: LocalDateTime
        get() = scheduledArrival.plusMinutes(arrivalDelayMinutes.toLong())
}

data class CommuteTrainPlan(
    val leaveBy: LocalDateTime,
    val trainDeparture: LocalDateTime,
    val eta: LocalDateTime,
    /** Whole minutes from the current minute until the leave-by minute. Negative means that minute has passed. */
    val leaveInMinutes: Long,
)

data class PlannedTrain(
    val item: WidgetTrainItem,
    val plan: CommuteTrainPlan,
)

object CommutePlanner {
    const val EVENING_START_HOUR = 13
    const val MORNING_RESUME_HOUR = 1
    /** Trains listed under the main "leave in" line. */
    const val EXTRA_TRAINS = 3
    const val DEFAULT_WEEKDAYS = "AAAAAAA"

    private val ISO_SECONDS: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Same window the widget already uses: evening from 13:00 until 01:00.
     * 12:59 is still the ride to the office. 13:00 is the ride home.
     */
    fun isEvening(time: LocalTime): Boolean {
        val hour = time.hour
        return hour >= EVENING_START_HOUR || hour < MORNING_RESUME_HOUR
    }

    fun directionAt(time: LocalTime, directionOverrideActive: Boolean = false): CommuteDirection {
        val reversed = isEvening(time) xor directionOverrideActive
        return if (reversed) CommuteDirection.TO_HOME else CommuteDirection.TO_OFFICE
    }

    fun selectOffice(
        date: LocalDate,
        weekdayOffices: String,
        override: CommuteOffice?,
        overrideDate: LocalDate?,
    ): CommuteOffice {
        if (override != null && overrideDate == date) return override
        val index = date.dayOfWeek.value - 1 // Monday = 0
        val flag = weekdayOffices.getOrNull(index)
        return if (flag == 'B' || flag == 'b') CommuteOffice.B else CommuteOffice.A
    }

    fun resolve(settings: CommuteSettings, now: ZonedDateTime): ResolvedCommute {
        val office = selectOffice(
            now.toLocalDate(),
            settings.weekdayOffices,
            settings.officeOverride,
            settings.officeOverrideDate,
        )
        val direction = directionAt(now.toLocalTime(), settings.directionOverrideActive)
        val officeStation = when (office) {
            CommuteOffice.A -> settings.officeAStationId
            CommuteOffice.B -> settings.officeBStationId
        }
        val (origin, destination) = when (direction) {
            CommuteDirection.TO_OFFICE -> settings.homeStationId to officeStation
            CommuteDirection.TO_HOME -> officeStation to settings.homeStationId
        }
        val (firstLeg, lastLeg) = scooterLegs(direction, office, settings.scooter)
        return ResolvedCommute(
            office = office,
            direction = direction,
            originStationId = origin,
            destinationStationId = destination,
            firstScooterMinutes = firstLeg,
            lastScooterMinutes = lastLeg,
        )
    }

    fun scooterLegs(
        direction: CommuteDirection,
        office: CommuteOffice,
        scooter: ScooterMinutes,
    ): Pair<Int, Int> {
        return when (direction) {
            CommuteDirection.TO_OFFICE -> {
                val last = if (office == CommuteOffice.A) {
                    scooter.officeAStationToOffice
                } else {
                    scooter.officeBStationToOffice
                }
                scooter.homeToStation to last
            }
            CommuteDirection.TO_HOME -> {
                val first = if (office == CommuteOffice.A) {
                    scooter.officeAToStation
                } else {
                    scooter.officeBToStation
                }
                first to scooter.stationToHome
            }
        }
    }

    fun leaveBy(train: TrainClock, firstScooterMinutes: Int): LocalDateTime {
        return train.actualDeparture.minusMinutes(firstScooterMinutes.toLong())
    }

    fun eta(train: TrainClock, lastScooterMinutes: Int): LocalDateTime {
        return train.actualArrival.plusMinutes(lastScooterMinutes.toLong())
    }

    fun leaveInMinutes(leaveBy: LocalDateTime, now: LocalDateTime): Long {
        val leaveMinute = leaveBy.truncatedTo(ChronoUnit.MINUTES)
        val nowMinute = now.truncatedTo(ChronoUnit.MINUTES)
        return ChronoUnit.MINUTES.between(nowMinute, leaveMinute)
    }

    fun planTrain(train: TrainClock, resolved: ResolvedCommute, now: LocalDateTime): CommuteTrainPlan {
        val leave = leaveBy(train, resolved.firstScooterMinutes)
        return CommuteTrainPlan(
            leaveBy = leave,
            trainDeparture = train.actualDeparture,
            eta = eta(train, resolved.lastScooterMinutes),
            leaveInMinutes = leaveInMinutes(leave, now),
        )
    }

    /**
     * Trains whose leave-by minute is still now or later, soonest first.
     * A leave-by of the current minute stays ("Leave in 0 min"). Earlier minutes are skipped.
     */
    fun visiblePlans(
        items: List<WidgetTrainItem>,
        resolved: ResolvedCommute,
        now: LocalDateTime,
        limit: Int = 1 + EXTRA_TRAINS,
    ): List<PlannedTrain> {
        return items.mapNotNull { item ->
            val clock = clockFrom(item) ?: return@mapNotNull null
            val plan = planTrain(clock, resolved, now)
            if (plan.leaveInMinutes < 0) null else PlannedTrain(item, plan)
        }.sortedBy { it.plan.leaveBy }.take(limit)
    }

    fun clockFrom(item: WidgetTrainItem): TrainClock? {
        val departure = parseDateTime(item.departureTimestamp) ?: return null
        val parsedArrival = parseDateTime(item.arrivalTimestamp)
        val arrival = parsedArrival ?: inferArrival(departure, item.arrivalTime) ?: return null
        val arrivalDelay = if (item.arrivalTimestamp.isNotEmpty()) item.arrivalDelay else item.delay
        return TrainClock(
            scheduledDeparture = departure,
            scheduledArrival = arrival,
            departureDelayMinutes = item.delay,
            arrivalDelayMinutes = arrivalDelay,
        )
    }

    fun formatClock(time: LocalDateTime): String = time.format(CLOCK)

    /** Start of the next morning/evening window. Used to expire a direction override. */
    fun nextDirectionWindowStart(now: ZonedDateTime): ZonedDateTime {
        val hour = now.hour
        return when {
            hour in MORNING_RESUME_HOUR until EVENING_START_HOUR ->
                now.withHour(EVENING_START_HOUR).withMinute(0).withSecond(0).withNano(0)
            hour >= EVENING_START_HOUR ->
                now.plusDays(1).withHour(MORNING_RESUME_HOUR).withMinute(0).withSecond(0).withNano(0)
            else ->
                now.withHour(MORNING_RESUME_HOUR).withMinute(0).withSecond(0).withNano(0)
        }
    }

    fun toggleOffice(current: CommuteOffice): CommuteOffice {
        return if (current == CommuteOffice.A) CommuteOffice.B else CommuteOffice.A
    }

    private fun parseDateTime(value: String): LocalDateTime? {
        if (value.isEmpty()) return null
        val text = value.take(19)
        return try {
            LocalDateTime.parse(text, ISO_SECONDS)
        } catch (_: Exception) {
            null
        }
    }

    private fun inferArrival(departure: LocalDateTime, arrivalClock: String): LocalDateTime? {
        val parts = arrivalClock.split(":")
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        var arrival = departure.toLocalDate().atTime(hour, minute)
        if (arrival.isBefore(departure)) {
            arrival = arrival.plusDays(1)
        }
        return arrival
    }
}

fun WidgetData.isCommuteConfigured(): Boolean {
    return commuteEnabled &&
        homeStationId.isNotEmpty() &&
        officeAStationId.isNotEmpty() &&
        officeBStationId.isNotEmpty()
}

fun WidgetData.toCommuteSettings(now: ZonedDateTime = ZonedDateTime.now()): CommuteSettings {
    val overrideDate = officeOverrideDate
        .takeIf { it.isNotEmpty() }
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val override = when (officeOverride) {
        "A" -> CommuteOffice.A
        "B" -> CommuteOffice.B
        else -> null
    }
    return CommuteSettings(
        homeStationId = homeStationId,
        officeAStationId = officeAStationId,
        officeBStationId = officeBStationId,
        scooter = ScooterMinutes(
            homeToStation = scooterHomeToStation,
            stationToHome = scooterStationToHome,
            officeAStationToOffice = scooterAStationToOffice,
            officeAToStation = scooterAOfficeToStation,
            officeBStationToOffice = scooterBStationToOffice,
            officeBToStation = scooterBOfficeToStation,
        ),
        weekdayOffices = weekdayOffices.ifEmpty { CommutePlanner.DEFAULT_WEEKDAYS },
        officeOverride = override,
        officeOverrideDate = overrideDate,
        directionOverrideActive = manualOverrideUntil > now.toInstant().toEpochMilli(),
    )
}
