package com.kidfocus.timer.data.cloud

/** Room holds one account's syncable rows at a time. Decide ownership before any scan or upload. */
internal class RoomOwnerGate(
    private val currentOwner: suspend () -> String?,
    private val saveOwner: (String) -> Unit,
    private val clearLocal: suspend (oldOwner: String?, newOwner: String) -> Unit,
    private val hasGuestData: suspend () -> Boolean,
    private val askGuestMerge: suspend () -> Boolean,
) {
    /** True when local tables were cleared and must be populated only from this account. */
    suspend fun prepare(uid: String): Boolean {
        require(uid.isNotBlank())
        val owner = currentOwner()
        if (owner == uid) return false
        if (owner == null && !hasGuestData()) {
            saveOwner(uid)
            return false
        }
        if (owner != null || !askGuestMerge()) {
            clearLocal(owner, uid)
            saveOwner(uid)
            return true
        }
        saveOwner(uid)
        return false
    }
}
