package com.betterrail.widget.commute

import com.betterrail.widget.data.WidgetTrainItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class CommutePlannerTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private val home = "3500" // Herzliya
    private val officeA = "4600" // Tel Aviv HaShalom
    private val officeB = "680" // Jerusalem Navon

    private val scooter = ScooterMinutes(
        homeToStation = 8,
        stationToHome = 6,
        officeAStationToOffice = 4,
        officeAToStation = 5,
        officeBStationToOffice = 12,
        officeBToStation = 11,
    )

    private fun settings(
        weekdays: String = "AAAAABB",
        override: CommuteOffice? = null,
        overrideDate: LocalDate? = null,
        directionOverride: Boolean = false,
    ) = CommuteSettings(
        homeStationId = home,
        officeAStationId = officeA,
        officeBStationId = officeB,
        scooter = scooter,
        weekdayOffices = weekdays,
        officeOverride = override,
        officeOverrideDate = overrideDate,
        directionOverrideActive = directionOverride,
    )

    private fun at(date: String, time: String): ZonedDateTime {
        return ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone)
    }

    private fun clock(
        departure: String,
        arrival: String,
        departureDelay: Int = 0,
        arrivalDelay: Int = departureDelay,
    ) = TrainClock(
        scheduledDeparture = LocalDateTime.parse(departure),
        scheduledArrival = LocalDateTime.parse(arrival),
        departureDelayMinutes = departureDelay,
        arrivalDelayMinutes = arrivalDelay,
    )

    @Test
    fun leaveBySubtractsFirstScooterLeg() {
        val train = clock("2026-10-28T08:00:00", "2026-10-28T08:32:00")
        val leave = CommutePlanner.leaveBy(train, firstScooterMinutes = 8)
        assertEquals(LocalDateTime.parse("2026-10-28T07:52:00"), leave)
    }

    @Test
    fun leaveByIncludesLiveDepartureDelay() {
        val train = clock("2026-10-28T08:00:00", "2026-10-28T08:32:00", departureDelay = 7)
        val leave = CommutePlanner.leaveBy(train, firstScooterMinutes = 8)
        assertEquals(LocalDateTime.parse("2026-10-28T07:59:00"), leave)
    }

    @Test
    fun earlyTrainMovesLeaveByEarlier() {
        val train = clock("2026-10-28T08:00:00", "2026-10-28T08:32:00", departureDelay = -3)
        val leave = CommutePlanner.leaveBy(train, firstScooterMinutes = 8)
        assertEquals(LocalDateTime.parse("2026-10-28T07:49:00"), leave)
    }

    @Test
    fun etaAddsLastScooterLegAndArrivalDelay() {
        val train = clock(
            "2026-10-28T08:00:00",
            "2026-10-28T08:32:00",
            departureDelay = 7,
            arrivalDelay = 9,
        )
        val eta = CommutePlanner.eta(train, lastScooterMinutes = 4)
        assertEquals(LocalDateTime.parse("2026-10-28T08:45:00"), eta)
    }

    @Test
    fun morningOfficeAUsesHomeAndOfficeScooterLegs() {
        val resolved = CommutePlanner.resolve(settings(), at("2026-10-28", "08:10"))
        assertEquals(CommuteDirection.TO_OFFICE, resolved.direction)
        assertEquals(CommuteOffice.A, resolved.office)
        assertEquals(home, resolved.originStationId)
        assertEquals(officeA, resolved.destinationStationId)
        assertEquals(8, resolved.firstScooterMinutes)
        assertEquals(4, resolved.lastScooterMinutes)
    }

    @Test
    fun eveningOfficeBUsesReturnScooterLegs() {
        // Saturday 2026-10-31 is office B in "AAAAABB", and 18:00 is the ride home.
        val resolved = CommutePlanner.resolve(settings(), at("2026-10-31", "18:00"))
        assertEquals(CommuteDirection.TO_HOME, resolved.direction)
        assertEquals(CommuteOffice.B, resolved.office)
        assertEquals(officeB, resolved.originStationId)
        assertEquals(home, resolved.destinationStationId)
        assertEquals(11, resolved.firstScooterMinutes)
        assertEquals(6, resolved.lastScooterMinutes)
    }

    @Test
    fun directionFlipsAtThirteenHundred() {
        assertEquals(CommuteDirection.TO_OFFICE, CommutePlanner.directionAt(LocalTime.of(12, 59)))
        assertEquals(CommuteDirection.TO_HOME, CommutePlanner.directionAt(LocalTime.of(13, 0)))
        assertEquals(CommuteDirection.TO_HOME, CommutePlanner.directionAt(LocalTime.of(13, 1)))
    }

    @Test
    fun lateNightStaysEveningUntilOneAm() {
        assertEquals(true, CommutePlanner.isEvening(LocalTime.of(0, 30)))
        assertEquals(CommuteDirection.TO_HOME, CommutePlanner.directionAt(LocalTime.of(0, 30)))
        assertEquals(false, CommutePlanner.isEvening(LocalTime.of(1, 0)))
        assertEquals(CommuteDirection.TO_OFFICE, CommutePlanner.directionAt(LocalTime.of(1, 0)))
    }

    @Test
    fun directionOverrideFlipsTheCurrentWindow() {
        assertEquals(
            CommuteDirection.TO_HOME,
            CommutePlanner.directionAt(LocalTime.of(9, 0), directionOverrideActive = true),
        )
        assertEquals(
            CommuteDirection.TO_OFFICE,
            CommutePlanner.directionAt(LocalTime.of(18, 0), directionOverrideActive = true),
        )
    }

    @Test
    fun nextDirectionWindowMatchesExistingBoundaries() {
        val morning = at("2026-10-28", "09:40")
        assertEquals(at("2026-10-28", "13:00"), CommutePlanner.nextDirectionWindowStart(morning))

        val evening = at("2026-10-28", "13:00")
        assertEquals(at("2026-10-29", "01:00"), CommutePlanner.nextDirectionWindowStart(evening))

        val afterMidnight = at("2026-10-28", "00:20")
        assertEquals(at("2026-10-28", "01:00"), CommutePlanner.nextDirectionWindowStart(afterMidnight))
    }

    @Test
    fun weekdayMapSelectsTheOffice() {
        val week = "ABABABA" // Mon A, Tue B, ...
        assertEquals(
            CommuteOffice.A,
            CommutePlanner.selectOffice(LocalDate.parse("2026-10-26"), week, null, null),
        )
        assertEquals(
            CommuteOffice.B,
            CommutePlanner.selectOffice(LocalDate.parse("2026-10-27"), week, null, null),
        )
        assertEquals(
            CommuteOffice.A,
            CommutePlanner.selectOffice(LocalDate.parse("2026-10-28"), week, null, null),
        )
        assertEquals(
            CommuteOffice.A,
            CommutePlanner.selectOffice(LocalDate.parse("2026-11-01"), week, null, null),
        )
    }

    @Test
    fun manualOfficeOverrideBeatsTheWeekdayAndExpiresTheNextDay() {
        val today = LocalDate.parse("2026-10-28") // Wednesday, mapped to A
        val week = "AAAAAAA"
        assertEquals(
            CommuteOffice.B,
            CommutePlanner.selectOffice(today, week, CommuteOffice.B, today),
        )
        assertEquals(
            CommuteOffice.A,
            CommutePlanner.selectOffice(today, week, CommuteOffice.B, today.minusDays(1)),
        )
    }

    @Test
    fun toggleSwitchesBetweenOffices() {
        assertEquals(CommuteOffice.B, CommutePlanner.toggleOffice(CommuteOffice.A))
        assertEquals(CommuteOffice.A, CommutePlanner.toggleOffice(CommuteOffice.B))
    }

    @Test
    fun passedLeaveByTrainsAreSkippedAndTheCurrentMinuteStays() {
        val resolved = CommutePlanner.resolve(settings(), at("2026-10-28", "08:00"))
        val now = LocalDateTime.parse("2026-10-28T08:00:30")
        // Scooter home->station is 8 min, so leave-by = departure - 8.
        // 08:07 departs -> leave-by 07:59 (passed). 08:08 -> 08:00 (this minute). 08:20 -> 08:12.
        val trains = listOf(
            train("2026-10-28T08:07:00", "2026-10-28T08:40:00"),
            train("2026-10-28T08:08:00", "2026-10-28T08:41:00", delay = 0),
            train("2026-10-28T08:20:00", "2026-10-28T08:55:00", delay = 5, arrivalDelay = 6),
            train("2026-10-28T08:40:00", "2026-10-28T09:10:00"),
            train("2026-10-28T09:00:00", "2026-10-28T09:30:00"),
        )

        val visible = CommutePlanner.visiblePlans(trains, resolved, now, limit = 4)
        assertEquals(4, visible.size)
        assertEquals(0L, visible[0].plan.leaveInMinutes)
        assertEquals(LocalDateTime.parse("2026-10-28T08:00:00"), visible[0].plan.leaveBy)
        // 08:20 + 5 delay - 8 scooter = 08:17, ETA 08:55 + 6 + 4 = 09:05
        assertEquals(LocalDateTime.parse("2026-10-28T08:17:00"), visible[1].plan.leaveBy)
        assertEquals(LocalDateTime.parse("2026-10-28T08:25:00"), visible[1].plan.trainDeparture)
        assertEquals(LocalDateTime.parse("2026-10-28T09:05:00"), visible[1].plan.eta)
        assertTrue(visible.all { it.plan.leaveInMinutes >= 0 })
    }

    @Test
    fun visiblePlansKeepsOnlyTheMainTrainPlusThreeMore() {
        val resolved = CommutePlanner.resolve(settings(), at("2026-10-28", "07:00"))
        val now = LocalDateTime.parse("2026-10-28T07:00:00")
        val trains = (0 until 6).map { index ->
            val depart = LocalDateTime.parse("2026-10-28T08:00:00").plusMinutes(index * 15L)
            train(depart.toString(), depart.plusMinutes(30).toString())
        }
        val visible = CommutePlanner.visiblePlans(trains, resolved, now)
        assertEquals(1 + CommutePlanner.EXTRA_TRAINS, visible.size)
        assertEquals(LocalDateTime.parse("2026-10-28T07:52:00"), visible.first().plan.leaveBy)
    }

    @Test
    fun clockFromFallsBackToDepartureDelayWhenArrivalTimestampIsMissing() {
        val item = train("2026-10-28T23:50:00", "00:20", delay = 4)
        val clock = CommutePlanner.clockFrom(item)!!
        assertEquals(LocalDateTime.parse("2026-10-29T00:20:00"), clock.scheduledArrival)
        assertEquals(4, clock.arrivalDelayMinutes)
        val eta = CommutePlanner.eta(clock, lastScooterMinutes = 6)
        assertEquals(LocalDateTime.parse("2026-10-29T00:30:00"), eta)
    }

    @Test
    fun widgetDataOverrideIsActiveOnlyForItsCalendarDay() {
        val stored = com.betterrail.widget.data.WidgetData(
            originId = home,
            destinationId = officeA,
            commuteEnabled = true,
            homeStationId = home,
            officeAStationId = officeA,
            officeBStationId = officeB,
            weekdayOffices = "AAAAAAA",
            officeOverride = "B",
            officeOverrideDate = "2026-10-28",
            manualOverrideUntil = at("2026-10-28", "13:00").toInstant().toEpochMilli(),
        )
        val morning = stored.toCommuteSettings(at("2026-10-28", "09:00"))
        val resolved = CommutePlanner.resolve(morning, at("2026-10-28", "09:00"))
        assertEquals(CommuteOffice.B, resolved.office)
        assertEquals(CommuteDirection.TO_HOME, resolved.direction)

        val nextDay = CommutePlanner.resolve(
            stored.toCommuteSettings(at("2026-10-29", "09:00")),
            at("2026-10-29", "09:00"),
        )
        assertEquals(CommuteOffice.A, nextDay.office)
        assertEquals(CommuteDirection.TO_OFFICE, nextDay.direction)
    }

    private fun train(
        departureIso: String,
        arrival: String,
        delay: Int = 0,
        arrivalDelay: Int = delay,
    ): WidgetTrainItem {
        val departureClock = departureIso.substringAfter("T").take(5)
        val arrivalIso = if (arrival.contains("T")) arrival else ""
        val arrivalClock = if (arrivalIso.isNotEmpty()) arrivalIso.substringAfter("T").take(5) else arrival
        return WidgetTrainItem(
            departureTime = departureClock,
            arrivalTime = arrivalClock,
            platform = "1",
            delay = delay,
            departureTimestamp = if (departureIso.length == 16) departureIso + ":00" else departureIso,
            arrivalTimestamp = if (arrivalIso.length == 16) arrivalIso + ":00" else arrivalIso,
            arrivalDelay = arrivalDelay,
        )
    }
}
