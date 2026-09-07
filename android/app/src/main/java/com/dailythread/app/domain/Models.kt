package com.dailythread.app.domain

enum class FocusStatus { PLANNED, IN_PROGRESS, COMPLETED, CANCELLED }
enum class TaskPriority { LOW, MEDIUM, HIGH }
enum class TaskStatus { TODO, DONE, CANCELLED }
enum class SyncOperation { CREATE, UPDATE, DELETE }
enum class SyncState { PENDING, SYNCING, SYNCED, FAILED, CONFLICT }
