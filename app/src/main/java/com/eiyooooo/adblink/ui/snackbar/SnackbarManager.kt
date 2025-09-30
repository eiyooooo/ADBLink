package com.eiyooooo.adblink.ui.snackbar

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

object SnackbarManager {

    private val hostEntries = mutableStateListOf<HostEntry>()
    private val nextId = AtomicInteger(0)

    private val snackbarJobs = mutableStateListOf<Job>()

    fun register(hostState: SnackbarHostState, scope: CoroutineScope): () -> Unit {
        val entry = HostEntry(hostState, scope, nextId.incrementAndGet())
        hostEntries.add(entry)
        return {
            hostEntries.removeAll { it.id == entry.id }
        }
    }

    fun show(
        message: String,
        actionLabel: String? = null,
        duration: SnackbarDuration? = null,
        dismissCurrent: Boolean = true,
        onAction: (() -> Unit)? = null
    ) {
        val entry = Snapshot.withoutReadObservation { hostEntries.lastOrNull() }
        if (entry == null) {
            Timber.w("No registered SnackbarHostState. Dropping snackbar: %s", message)
            return
        }
        if (dismissCurrent) {
            dismissAll()
        }
        val job = entry.scope.launch {
            val result = if (duration == null) {
                entry.hostState.showSnackbar(message, actionLabel)
            } else {
                entry.hostState.showSnackbar(message, actionLabel, duration = duration)
            }
            if (result == SnackbarResult.ActionPerformed) {
                onAction?.invoke()
            }
        }
        snackbarJobs.add(job)
        job.invokeOnCompletion {
            snackbarJobs.remove(job)
        }
    }

    fun dismissAll() {
        snackbarJobs.forEach {
            it.cancel()
        }
        snackbarJobs.clear()
    }

    private data class HostEntry(
        val hostState: SnackbarHostState,
        val scope: CoroutineScope,
        val id: Int
    )
}
