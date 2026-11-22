package ru.nto.guestmanagement.mobile.ui.refresh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RefreshGate {
    private var currentJob: Job? = null

    fun launch(scope: CoroutineScope, block: suspend () -> Unit): Job? {
        if (currentJob?.isActive == true) {
            return null
        }
        val job = scope.launch {
            block()
        }
        currentJob = job
        job.invokeOnCompletion {
            if (currentJob === job) {
                currentJob = null
            }
        }
        return job
    }

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
    }
}
