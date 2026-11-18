package com.novelnest.app.core.lifecycle

/** Main-thread-only task identities. Permissions remain process memory only in AppLockManager. */
class AppLockSessionTasks {
    private val known = mutableSetOf<Int>()

    /** A new task after removal cannot inherit an old task's unlocked session. */
    fun onTaskStarted(taskId: Int, authenticatedReplacementTaskId: Int? = null): Boolean {
        if (taskId < 0) return true
        val authenticatedReplacement = taskId == authenticatedReplacementTaskId
        val needsLock = known.isNotEmpty() && taskId !in known && !authenticatedReplacement
        if (needsLock || authenticatedReplacement) known.clear()
        known.add(taskId)
        return needsLock
    }

    /** Called after Activity finish/removal; configuration recreation keeps the same task. */
    fun onTasksChanged(remaining: Set<Int>): Boolean {
        val hadTask = known.isNotEmpty()
        known.retainAll(remaining)
        return hadTask && known.isEmpty()
    }
}
