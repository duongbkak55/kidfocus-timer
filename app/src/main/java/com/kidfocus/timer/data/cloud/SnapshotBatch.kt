package com.kidfocus.timer.data.cloud

/** One local scan per Firestore snapshot, even when the initial snapshot contains thousands of rows. */
internal object SnapshotBatch {
    suspend fun <T> apply(
        changes: List<T>,
        scan: suspend () -> Unit,
        unchanged: suspend (T) -> Boolean,
        receive: suspend (T) -> Unit,
    ): Int {
        if (changes.isEmpty()) return 0
        scan()
        var received = 0
        for (change in changes) {
            if (unchanged(change)) continue
            receive(change)
            received++
        }
        return received
    }
}
