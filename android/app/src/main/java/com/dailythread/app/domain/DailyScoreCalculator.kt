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
        val activeFocus = focus.filter { it.deletedAt == null }
        val activeTasks = tasks.filter { it.deletedAt == null }
        val activeActivities = activities.filter { it.deletedAt == null }
        val activeHabits = habits.filter { it.deletedAt == null && it.isActive }
        val activeHabitIds = activeHabits.mapTo(mutableSetOf()) { it.id }
        val activeEntries = entries.filter { it.deletedAt == null && it.habitId in activeHabitIds }

        val focusDone = activeFocus.count { it.status == "COMPLETED" }
        val taskDone = activeTasks.count { it.status == "DONE" }
        val habitDone = activeEntries.count { it.completed }
        val productiveMinutes = activeActivities
            .filter { !it.categoryName.equals("Istirahat", ignoreCase = true) }
            .sumOf { it.durationMinutes.coerceAtLeast(0) }

        val focusScore = if (activeFocus.isEmpty()) 0.0 else 40.0 * focusDone / activeFocus.size
        val taskScore = if (activeTasks.isEmpty()) 0.0 else 20.0 * taskDone / activeTasks.size
        val target = targetFocusMinutes.coerceAtLeast(15)
        val timeScore = 20.0 * (productiveMinutes / target.toDouble()).coerceIn(0.0, 1.0)
        val habitScore = if (activeHabits.isEmpty()) 0.0 else 20.0 * habitDone.coerceAtMost(activeHabits.size) / activeHabits.size

        return (focusScore + taskScore + timeScore + habitScore).toInt().coerceIn(0, 100)
    }
}
