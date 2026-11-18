package com.novelnest.app.core.lifecycle

import org.junit.Assert.*
import org.junit.Test

class AppLockSessionTasksTest {
    private val tasks = AppLockSessionTasks()

    @Test fun sameTaskReturnsAndConfigurationRecreationDoNotRequireAnotherUnlock() {
        assertFalse(tasks.onTaskStarted(10))
        repeat(3) { assertFalse(tasks.onTaskStarted(10)) }
        assertFalse(tasks.onTasksChanged(setOf(10)))
        assertFalse(tasks.onTaskStarted(10))
    }

    @Test fun removingTheLastAppTaskRevokesTheSession() {
        tasks.onTaskStarted(10)
        assertTrue(tasks.onTasksChanged(emptySet()))
        assertFalse(tasks.onTasksChanged(emptySet()))
    }

    @Test fun newTaskCannotInheritAccessWhenRemovalCallbackWasMissed() {
        tasks.onTaskStarted(10)
        assertTrue(tasks.onTaskStarted(11))
        assertFalse(tasks.onTaskStarted(11))
    }

    @Test fun authenticatedTaskReplacementIsAllowedExactlyForTheSuppliedTransition() {
        tasks.onTaskStarted(10)
        assertFalse(tasks.onTaskStarted(11, authenticatedReplacementTaskId = 11))
        assertFalse(tasks.onTasksChanged(setOf(11)))
        assertTrue(tasks.onTaskStarted(12))
    }

    @Test fun staleTaskReplacementCannotAuthorizeANewTaskAfterRemoval() {
        tasks.onTaskStarted(10)
        assertTrue(tasks.onTaskStarted(11, authenticatedReplacementTaskId = 10))
    }

    @Test fun finishingAChildActivityDoesNotRevokeTheSurvivingTask() {
        tasks.onTaskStarted(10)
        assertFalse(tasks.onTasksChanged(setOf(10)))
    }

    @Test fun invalidTaskIdentityCannotKeepAUserUnlocked() {
        assertTrue(tasks.onTaskStarted(-1))
    }
}
