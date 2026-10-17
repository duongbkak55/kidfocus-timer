package com.kidfocus.timer.data.cloud

/** Shared wire contract with web/app/src/migration-lease.mjs; only meta is leased, items use LWW. */
internal object MigrationLeasePolicy {
    const val LEASE_MILLIS = 120_000L
    sealed interface Decision {
        data class Ready(val migratedAtMillis: Long) : Decision
        data class Wait(val untilMillis: Long) : Decision
        data object Claim : Decision
    }

    fun decide(meta: Map<String, Any?>?, now: Long, deviceId: String): Decision {
        if ((meta?.get("schemaVersion") as? Number)?.toInt() == 2)
            return Decision.Ready((meta["migratedAtMillis"] as? Number)?.toLong() ?: 0L)
        val until = (meta?.get("leaseUntil") as? Number)?.toLong() ?: 0L
        if (meta?.get("state") == "migrating" && meta["by"] != deviceId && until > now)
            return Decision.Wait(until)
        return Decision.Claim
    }

    fun claim(now: Long, deviceId: String): Map<String, Any> =
        mapOf("state" to "migrating", "by" to deviceId, "leaseUntil" to now + LEASE_MILLIS)
}
