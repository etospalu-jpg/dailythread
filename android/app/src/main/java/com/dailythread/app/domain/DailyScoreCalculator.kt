package com.dailythread.app.domain

import com.dailythread.app.data.local.entity.*

object DailyScoreCalculator {
    fun calculate(
        focus: List<FocusEntity>,
        tasks: List<TaskEntity>,
        activities: List<ActivityEntity>,
        habits: List<HabitEntity>,
        entries: List<HabitEntryEntity>,
        targetFocusMinutes: Int = 120
    ): Int {
        val focusDone = focus.count { it.status == "COMPLETED" }
        val taskDone = tasks.count { it.status == "DONE" }
        val habitDone = entries.count { it.completed && habits.any { h -> h.id == it.habitId } }
        val productiveMinutes = activities
            .filter { it.categoryName != "Istirahat" }
            .sumOf { it.durationMinutes }

        val focusScore = if (focus.isEmpty()) 0.0 else 40.0 * focusDone / focus.size
        val taskScore = if (tasks.isEmpty()) 0.0 else 20.0 * taskDone / tasks.size
        val target = targetFocusMinutes.coerceAtLeast(15)
        val timeScore = 20.0 * (productiveMinutes / target.toDouble()).coerceAtMost(1.0)
        val habitScore = if (habits.isEmpty()) 0.0 else 20.0 * habitDone / habits.size

        return (focusScore + taskScore + timeScore + habitScore).toInt().coerceIn(0, 100)
    }
}
