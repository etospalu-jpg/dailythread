package com.dailythread.app.domain

import com.dailythread.app.data.local.entity.*
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyScoreCalculatorTest {
    private val now = "2026-09-07T00:00:00Z"

    @Test
    fun perfectDayScores100() {
        val focus = listOf(FocusEntity("f1","u","2026-09-07","A",status="COMPLETED",createdAt=now,updatedAt=now))
        val tasks = listOf(TaskEntity("t1","u","2026-09-07","T",status="DONE",createdAt=now,updatedAt=now))
        val activities = listOf(ActivityEntity("a1","u","2026-09-07","Work",categoryName="Kerja",startedAt=now,endedAt="2026-09-07T02:00:00Z",durationMinutes=120,createdAt=now,updatedAt=now))
        val habits = listOf(HabitEntity("h1","u","Habit",createdAt=now,updatedAt=now))
        val entries = listOf(HabitEntryEntity("e1","u","h1","2026-09-07",completed=true,createdAt=now,updatedAt=now))
        assertEquals(100, DailyScoreCalculator.calculate(focus,tasks,activities,habits,entries))
    }

    @Test
    fun emptyDayScores0() {
        assertEquals(0, DailyScoreCalculator.calculate(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
    }

    @Test
    fun restTimeDoesNotIncreaseProductiveScore() {
        val activities = listOf(
            ActivityEntity("a1","u","2026-09-07","Break",categoryName="Istirahat",startedAt=now,endedAt=now,durationMinutes=240,createdAt=now,updatedAt=now)
        )
        assertEquals(0, DailyScoreCalculator.calculate(emptyList(), emptyList(), activities, emptyList(), emptyList()))
    }

    @Test
    fun inactiveHabitDoesNotReduceHabitScore() {
        val habits = listOf(
            HabitEntity("h1","u","Active",isActive=true,createdAt=now,updatedAt=now),
            HabitEntity("h2","u","Paused",isActive=false,createdAt=now,updatedAt=now)
        )
        val entries = listOf(HabitEntryEntity("e1","u","h1","2026-09-07",completed=true,createdAt=now,updatedAt=now))
        assertEquals(20, DailyScoreCalculator.calculate(emptyList(), emptyList(), emptyList(), habits, entries))
    }

    @Test
    fun deletedRowsAreIgnored() {
        val deleted = "2026-09-07T01:00:00Z"
        val focus = listOf(FocusEntity("f1","u","2026-09-07","Deleted",status="PLANNED",createdAt=now,updatedAt=now,deletedAt=deleted))
        val tasks = listOf(TaskEntity("t1","u","2026-09-07","Deleted",status="TODO",createdAt=now,updatedAt=now,deletedAt=deleted))
        val activities = listOf(ActivityEntity("a1","u","2026-09-07","Deleted",categoryName="Kerja",startedAt=now,endedAt=now,durationMinutes=120,createdAt=now,updatedAt=now,deletedAt=deleted))
        val habits = listOf(HabitEntity("h1","u","Deleted",createdAt=now,updatedAt=now,deletedAt=deleted))
        val entries = listOf(HabitEntryEntity("e1","u","h1","2026-09-07",completed=true,createdAt=now,updatedAt=now,deletedAt=deleted))
        assertEquals(0, DailyScoreCalculator.calculate(focus,tasks,activities,habits,entries))
    }

    @Test
    fun productiveTimeIsCappedAtTwentyPoints() {
        val activities = listOf(
            ActivityEntity("a1","u","2026-09-07","Long work",categoryName="Kerja",startedAt=now,endedAt=now,durationMinutes=999,createdAt=now,updatedAt=now)
        )
        assertEquals(20, DailyScoreCalculator.calculate(emptyList(), emptyList(), activities, emptyList(), emptyList(), targetFocusMinutes=120))
    }
}
